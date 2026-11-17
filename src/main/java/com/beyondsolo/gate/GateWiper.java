package com.beyondsolo.gate;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Puts the rock back where a closed gate's cave was, then hands its slot of the gate dimension to the next gate
 * ({@link GateData#freeSlot}). Without it every gate ever run would leave a cave behind, and dungeon keys, which open
 * as many gates as a hunter has keys, would make the save grow with every run.
 *
 * <p>A cave is wiped once it has been closed for {@link #GRACE_TICKS} and nobody is in its slot: the box of every
 * room, passage and open space (with a margin for the lights and veins set into the walls) goes back to the
 * dimension's own rock, a slice at a time ({@link #BUDGET} blocks a tick, one cave at a time), then whatever was left lying in it
 * (items, monsters that were never killed) is removed. Monsters saved in chunks that were not loaded are turned away
 * when they load ({@link #onJoin}). The gate's record is then forgotten (an awakening trial's is kept, the awakening
 * still reads it).</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class GateWiper {
    /** Blocks looked at per tick while wiping. */
    public static final int BUDGET = 16_000;
    /** How long a closed cave is left alone before it is wiped. */
    public static final int GRACE_TICKS = 20 * 20;
    /** Persistent data on gate monsters: the id of the gate they belong to. */
    public static final String GATE_ID_TAG = "beyondsolo_gate_id";
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private static final Map<UUID, Long> CLOSED_SINCE = new HashMap<>();
    @Nullable private static Job job;

    /** Called every tick from {@link GateService}. */
    static void tick(MinecraftServer server, ServerLevel dungeon, GateData data) {
        if (job != null) {
            if (job.tick(dungeon)) finish(dungeon, data, job.gate);
            return;
        }
        if (server.getTickCount() % 40 != 0) return;
        long now = dungeon.getGameTime();
        CLOSED_SINCE.keySet().removeIf(id -> data.get(id) == null);
        for (GateInstance g : data.all()) {
            if (g.state() != GateInstance.State.CLOSED || g.slotFreed()) continue;
            long since = CLOSED_SINCE.computeIfAbsent(g.id(), k -> now);
            if (now - since < GRACE_TICKS) continue;
            if (!start(dungeon, data, g)) continue;
            return;
        }
    }

    /** Starts wiping a closed gate's cave (or frees its slot at once if nothing was ever carved). */
    private static boolean start(ServerLevel dungeon, GateData data, GateInstance g) {
        if (GateService.isBuilding(g.id()) || GateService.inTransitTo(g.id()) || occupied(dungeon, g)) return false;
        if (!g.built()) {
            finish(dungeon, data, g);
            return true;
        }
        job = new Job(g, dungeon);
        return true;
    }

    /** Wipes a closed cave right away (tests, commands). Returns false if it is still open or someone is in it. */
    public static boolean wipeNow(MinecraftServer server, GateInstance g) {
        ServerLevel dungeon = GateService.dungeon(server);
        GateData data = GateData.get(server);
        if (dungeon == null || g.state() != GateInstance.State.CLOSED || g.slotFreed()) return false;
        if (job != null && job.gate == g) job = null;
        if (GateService.isBuilding(g.id()) || occupied(dungeon, g)) return false;
        if (g.built()) {
            Job j = new Job(g, dungeon);
            while (!j.tick(dungeon)) {
                // keep going
            }
        }
        // another cave being wiped in the background carries on where it was
        Job running = job;
        finish(dungeon, data, g);
        job = running;
        return true;
    }

    /** True while a cave is being wiped (tests). */
    public static boolean busy() {
        return job != null;
    }

    private static void finish(ServerLevel dungeon, GateData data, GateInstance g) {
        job = null;
        CLOSED_SINCE.remove(g.id());
        g.setSlotFreed(true);
        data.freeSlot(g.slot());
        // the awakening still looks its trial up; every other gate is done with
        if (!g.isTrial()) data.remove(g.id());
        data.setDirty();
    }

    /** Someone is standing in the slot (a player who has not been sent home yet). */
    private static boolean occupied(ServerLevel dungeon, GateInstance g) {
        for (ServerPlayer p : dungeon.players()) if (GateData.slotAt(p.blockPosition()) == g.slot()) return true;
        return false;
    }

    /** The rock the gate dimension is made of at a height (the game-test fallback carves into plain stone). */
    static BlockState rock(ServerLevel level, int y) {
        if (!level.dimension().equals(GateService.DIMENSION)) return Blocks.STONE.defaultBlockState();
        if (y <= 0) return Blocks.BEDROCK.defaultBlockState();
        if (y <= 40) return Blocks.DEEPSLATE.defaultBlockState();
        if (y <= 127) return Blocks.STONE.defaultBlockState();
        return Blocks.AIR.defaultBlockState();
    }

    private static void put(ServerLevel level, BlockPos.MutableBlockPos p, BlockState rock) {
        BlockState here = level.getBlockState(p);
        if (here == rock) return;
        // take block entities away first, so a chest nobody opened doesn't spill its loot into the rock
        if (here.hasBlockEntity()) level.removeBlockEntity(p);
        level.setBlock(p, rock, FLAGS);
    }

    // ------------------------------------------------------------------ the work

    private interface Step {
        /** Does some work; returns the number of blocks looked at, or -1 when finished. */
        int run(ServerLevel level);
    }

    private static final class Job {
        final GateInstance gate;
        final Deque<Step> steps = new ArrayDeque<>();

        Job(GateInstance gate, ServerLevel level) {
            this.gate = gate;
            BlockPos o = gate.origin();
            for (int[] b : gate.layout().boxes(3)) steps.add(new BoxWipe(o, b));
            steps.add(lvl -> {
                sweep(lvl, gate);
                return -1;
            });
        }

        boolean tick(ServerLevel level) {
            int spent = 0;
            while (!steps.isEmpty() && spent < BUDGET) {
                int n = steps.peekFirst().run(level);
                if (n < 0) {
                    steps.pollFirst();
                    spent += 200;
                } else {
                    spent += n;
                }
            }
            return steps.isEmpty();
        }
    }

    /**
     * One box of the dungeon (a room, a passage, a space: {@link DungeonLayout#boxes}, grown by a margin for the
     * lights and veins set into the walls), a slice of x at a time.
     */
    private static final class BoxWipe implements Step {
        private final int minY, maxY, minZ, maxZ, maxX;
        private int x;

        BoxWipe(BlockPos o, int[] b) {
            this.x = o.getX() + b[0];
            this.maxX = o.getX() + b[3];
            this.minY = o.getY() + b[1];
            this.maxY = o.getY() + b[4];
            this.minZ = o.getZ() + b[2];
            this.maxZ = o.getZ() + b[5];
        }

        @Override
        public int run(ServerLevel level) {
            if (x > maxX) return -1;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int y0 = Math.max(level.getMinBuildHeight() + 1, minY), y1 = Math.min(level.getMaxBuildHeight() - 1, maxY);
            int n = 0;
            for (int y = y0; y <= y1; y++) {
                BlockState rock = rock(level, y);
                for (int z = minZ; z <= maxZ; z++) {
                    n++;
                    p.set(x, y, z);
                    put(level, p, rock);
                }
            }
            x++;
            return Math.max(1, n);
        }
    }

    /** Whatever was left lying in the cave goes with it. */
    private static void sweep(ServerLevel level, GateInstance g) {
        int[] b = g.layout().bounds();
        BlockPos o = g.origin();
        AABB box = new AABB(o.getX() + b[0] - 8, level.getMinBuildHeight(), o.getZ() + b[1] - 8,
                o.getX() + b[2] + 8, level.getMaxBuildHeight(), o.getZ() + b[3] + 8);
        for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) e.discard();
    }

    /**
     * A monster or item saved in a chunk that was not loaded when its cave was wiped turns up when the chunk loads
     * again: it is turned away if its slot is closed, empty, or belongs to another gate now.
     */
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(GateService.DIMENSION)) return;
        Entity e = event.getEntity();
        if (e instanceof Player) return;
        BlockPos pos = e.blockPosition();
        if (GateData.slotAt(pos) < 1) return;
        GateInstance g = GateData.get(level.getServer()).bySlotPos(pos);
        UUID from = e instanceof GateBossEntity boss ? boss.gateId()
                : e.getPersistentData().hasUUID(GATE_ID_TAG) ? e.getPersistentData().getUUID(GATE_ID_TAG) : null;
        if (g == null || g.state() == GateInstance.State.CLOSED || (from != null && !from.equals(g.id()))) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        job = null;
        CLOSED_SINCE.clear();
    }

    private GateWiper() {}
}
