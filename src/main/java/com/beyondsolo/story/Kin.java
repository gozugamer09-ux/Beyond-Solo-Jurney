package com.beyondsolo.story;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The hunter's mother in the world ({@link KinEntity}): the village they call home (the one they were evaluated in),
 * the bed of it she lies in, putting her there and back again if she is ever gone, and how she spends her days once she
 * is awake (sitting up in bed the day she wakes, up and about beside it after). Her story is {@link LongSleep}'s.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Kin {
    /** She is looked after this often (ticks) while her hunter is near home. */
    public static final int CHECK = 20;
    /** Her hunter must be this near (blocks) home for her to be put in her bed. */
    public static final int NEAR = 64;
    /** ...and this near her bed before a missing mother is put back (her chunk has long been loaded by then). */
    public static final int CLOSE = 40;
    /** Checks in a row she must be missing before she is put back (entities load after their chunks). */
    public static final int MISSES = 3;

    private static final Map<UUID, Integer> MISSING = new HashMap<>();

    public static KinRecord record(Player p) {
        return p.getData(StoryModule.RECORD);
    }

    // ------------------------------------------------------------------ home

    /** A hunter was evaluated at a village's crystal for the first time: that village is home. */
    public static void evaluatedAt(ServerPlayer p, BlockPos crystal) {
        KinRecord r = record(p);
        r.anchorDim = p.level().dimension();
        r.anchor = crystal.immutable();
    }

    /**
     * From the Evaluation Crystal's server ticker: a hunter evaluated before the story came to the game, near a
     * crystal, takes its village for home.
     */
    public static void crystalTick(Level world, BlockPos pos) {
        if (!(world instanceof ServerLevel level) || (level.getGameTime() + Math.floorMod(pos.asLong() * 31, 100)) % 100 != 0) return;
        if (!level.dimension().equals(Level.OVERWORLD) || !StoryConfig.enabled()) return;
        if (level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer) return;
        Vec3 c = Vec3.atCenterOf(pos);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !SystemAPI.isAwakened(p) || p.distanceToSqr(c) > 48 * 48) continue;
            KinRecord r = record(p);
            if (r.anchor == null) {
                r.anchorDim = level.dimension();
                r.anchor = pos.immutable();
            }
        }
    }

    /** Home moved: her old bed is not hers any more (a new one is chosen the next time the hunter is near). */
    public static void forgetBed(ServerPlayer p) {
        KinRecord r = record(p);
        r.bed = null;
        r.bedDim = null;
        r.kin = null;
    }

    // ------------------------------------------------------------------ looking after her

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || (p.tickCount + p.getId()) % CHECK != 0) return;
        if (!StoryConfig.enabled() || !SystemAPI.isAwakened(p)) return;
        tick(p);
        LongSleep.tick(p);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MISSING.remove(event.getEntity().getUUID());
    }

    /** Once a second: near home, she is in her bed (a new one if hers is gone), and as her day says. */
    public static void tick(ServerPlayer p) {
        KinRecord r = record(p);
        if (r.anchor == null || r.anchorDim == null) return;
        ServerLevel level = p.serverLevel();
        boolean hasBed = r.bed != null && r.bedDim != null;
        ResourceKey<Level> homeDim = hasBed ? r.bedDim : r.anchorDim;
        BlockPos home = hasBed ? r.bed : r.anchor;
        if (!level.dimension().equals(homeDim) || p.blockPosition().distSqr(home) > (double) NEAR * NEAR) {
            MISSING.remove(p.getUUID());
            return;
        }
        if (!level.isLoaded(home)) return;
        KinEntity k = find(level, r);
        // her bed: the one she was given while it stands, else the nearest free one of the village, else a new one
        if (r.bed == null || !isBed(level, r.bed)) {
            if (!r.anchorDim.equals(level.dimension()) || !level.isLoaded(r.anchor) || !chooseBed(p, level, r, k)) return;
        }
        if (k == null) {
            if (p.blockPosition().distSqr(r.bed) > (double) CLOSE * CLOSE) return;
            if (MISSING.merge(p.getUUID(), 1, Integer::sum) < MISSES) return;
            MISSING.remove(p.getUUID());
            spawn(p, level, r);
            return;
        }
        MISSING.remove(p.getUUID());
        // a scene telling her story moves her itself
        if (StoryScenes.playing(p)) return;
        byte want = stateFor(p, r);
        if (k.state() != want) k.setState(want);
    }

    /** A bed for her near home (the nearest free one, else a new one); she moves to it. False if there is none. */
    private static boolean chooseBed(ServerPlayer p, ServerLevel level, KinRecord r, @Nullable KinEntity k) {
        BlockPos bed = findBed(level, r.anchor, StoryConfig.bedReach());
        if (bed == null) bed = makeBed(level, r.anchor);
        if (bed == null) return false;
        r.bed = bed;
        r.bedDim = level.dimension();
        if (k != null) k.moveToBed(bed);
        LongSleep.pointHome(p);
        return true;
    }

    /**
     * Puts her in her bed now, wherever her hunter is (operator commands, tests): a bed chosen near home if she has none
     * standing, and her in it as her day says. Null if there is no home yet, or no bed to be had (the village unloaded).
     */
    @Nullable
    public static KinEntity bringHome(ServerPlayer p) {
        KinRecord r = record(p);
        if (r.anchor == null || r.anchorDim == null) return null;
        ServerLevel home = p.server.getLevel(r.anchorDim);
        if (home == null) return null;
        ServerLevel level = r.bed != null && r.bedDim != null ? p.server.getLevel(r.bedDim) : home;
        if (level == null) level = home;
        KinEntity k = find(level, r);
        if (r.bed == null || r.bedDim == null || !isBed(level, r.bed)) {
            level = home;
            if (!chooseBed(p, home, r, k != null && k.level() == home ? k : null)) return null;
        }
        if (k == null || k.level() != level) return spawn(p, level, r);
        k.moveToBed(r.bed);
        k.setState(stateFor(p, r));
        return k;
    }

    /** Where her day finds her: asleep until the elixir, sitting up in bed the day she wakes, up and about after. */
    public static byte stateFor(ServerPlayer p, KinRecord r) {
        if (!r.awake()) return KinEntity.SLEEPING;
        return QuestEngine.day(p) > r.wokeDay ? KinEntity.STANDING : KinEntity.SITTING;
    }

    /** Her, if she is in the world and loaded. */
    @Nullable
    public static KinEntity find(ServerLevel level, KinRecord r) {
        if (r.kin == null) return null;
        return level.getEntity(r.kin) instanceof KinEntity k && k.isAlive() ? k : null;
    }

    /** Her, wherever she is loaded on the server. */
    @Nullable
    public static KinEntity find(ServerPlayer p) {
        KinRecord r = record(p);
        if (r.kin == null || r.bedDim == null) return null;
        ServerLevel level = p.server.getLevel(r.bedDim);
        return level == null ? null : find(level, r);
    }

    /** Puts her in her bed now (her look chosen the first time). */
    @Nullable
    public static KinEntity spawn(ServerPlayer owner, ServerLevel level, KinRecord r) {
        if (r.bed == null) return null;
        KinEntity old = find(level, r);
        if (old != null) old.discard();
        KinEntity k = StoryModule.MOTHER.get().create(level);
        if (k == null) return null;
        if (r.look < 0) r.look = owner.getRandom().nextInt(KinEntity.LOOKS);
        Vec3 at = Vec3.atBottomCenterOf(r.bed);
        k.moveTo(at.x, at.y, at.z, 0, 0);
        k.setup(owner, r.look, r.bed);
        r.kin = k.getUUID();
        level.addFreshEntity(k);
        k.setState(stateFor(owner, r));
        return k;
    }

    // ------------------------------------------------------------------ her bed

    static boolean isBed(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    /**
     * The nearest bed of the village (its head) within {@code reach} of its crystal that nobody uses: not slept in, not
     * a hunter's respawn point, not another hunter's mother's. Only loaded chunks are looked at.
     */
    @Nullable
    public static BlockPos findBed(ServerLevel level, BlockPos anchor, int reach) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int cx = (anchor.getX() - reach) >> 4; cx <= (anchor.getX() + reach) >> 4; cx++) {
            for (int cz = (anchor.getZ() - reach) >> 4; cz <= (anchor.getZ() + reach) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof BedBlockEntity)) continue;
                    BlockState s = be.getBlockState();
                    if (s.getValue(BedBlock.PART) != BedPart.HEAD || s.getValue(BedBlock.OCCUPIED)) continue;
                    BlockPos pos = be.getBlockPos();
                    double d = pos.distSqr(anchor);
                    if (d > (double) reach * reach || d >= bestD || taken(level, pos)) continue;
                    best = pos.immutable();
                    bestD = d;
                }
            }
        }
        return best;
    }

    private static boolean taken(ServerLevel level, BlockPos head) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (head.equals(p.getRespawnPosition()) && level.dimension().equals(p.getRespawnDimension())) return true;
        }
        return !level.getEntitiesOfClass(KinEntity.class, new AABB(head).inflate(4), k -> head.equals(k.bed())).isEmpty();
    }

    /**
     * No bed free in the whole village: one is made for her a few steps from the crystal, on open ground with room
     * beside it. Null if no such ground is near.
     */
    @Nullable
    public static BlockPos makeBed(ServerLevel level, BlockPos anchor) {
        for (int ring = 3; ring <= 12; ring++) {
            for (int i = 0; i < ring * 8; i++) {
                double a = i * Math.PI * 2 / (ring * 8);
                int x = anchor.getX() + (int) Math.round(Math.cos(a) * ring);
                int z = anchor.getZ() + (int) Math.round(Math.sin(a) * ring);
                BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (Math.abs(ground.getY() - anchor.getY()) > 4) continue;
                for (Direction f : Direction.Plane.HORIZONTAL) {
                    BlockPos head = ground, foot = ground.relative(f.getOpposite());
                    if (!free(level, head) || !free(level, foot) || !free(level, head.relative(f.getClockWise()))) continue;
                    BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, f);
                    level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
                    level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
                    return head.immutable();
                }
            }
        }
        return null;
    }

    private static boolean free(Level level, BlockPos p) {
        return level.getBlockState(p).canBeReplaced() && level.getBlockState(p).getFluidState().isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
    }

    /** Where she stands when she is up: beside the pillow, else beside the foot, else past it. Null if all are walled. */
    @Nullable
    public static BlockPos besideBed(Level level, BlockPos head) {
        BlockState s = level.getBlockState(head);
        if (!(s.getBlock() instanceof BedBlock)) return null;
        Direction f = s.getValue(BedBlock.FACING);
        BlockPos foot = head.relative(f.getOpposite());
        for (BlockPos p : List.of(head.relative(f.getClockWise()), head.relative(f.getCounterClockWise()),
                foot.relative(f.getClockWise()), foot.relative(f.getCounterClockWise()), foot.relative(f.getOpposite()))) {
            if (standable(level, p)) return p;
        }
        return null;
    }

    private static boolean standable(Level level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p).getFluidState().isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
    }

    // ------------------------------------------------------------------ talking to her

    /**
     * Her bed is hers: right-clicking it reaches for her (asleep she is only a speck to click, the vanilla sleeper's
     * box, and the bed would only say it is occupied).
     */
    @SubscribeEvent
    static void onUseBed(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        BlockState s = level.getBlockState(event.getPos());
        if (!(s.getBlock() instanceof BedBlock)) return;
        BlockPos head = s.getValue(BedBlock.PART) == BedPart.HEAD ? event.getPos() : event.getPos().relative(s.getValue(BedBlock.FACING));
        List<KinEntity> her = level.getEntitiesOfClass(KinEntity.class, new AABB(head).inflate(3), k -> head.equals(k.bed()) && k.isAlive());
        if (her.isEmpty()) return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer p) {
            interact(p, her.get(0));
        }
    }

    /** Someone right-clicked her. */
    static void interact(ServerPlayer p, KinEntity k) {
        if (!k.isOwnedBy(p)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    k.state() == KinEntity.SLEEPING ? "story.beyondsolo.kin.other.asleep" : "story.beyondsolo.kin.other.awake",
                    k.ownerName()), true);
            return;
        }
        if (StoryScenes.playing(p)) return;
        LongSleep.talk(p, k);
    }

    private Kin() {}
}
