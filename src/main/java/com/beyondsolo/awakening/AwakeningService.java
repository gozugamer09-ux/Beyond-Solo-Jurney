package com.beyondsolo.awakening;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.awakening.AwakeningData.Stage;
import com.beyondsolo.combat.Buildups;
import com.beyondsolo.depths.DepthsBuild;
import com.beyondsolo.depths.GateDepths;
import com.beyondsolo.depths.HiddenKind;
import com.beyondsolo.depths.HiddenRooms;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateFeature;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.RewardCacheBlock;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.network.AwakeningPayload;
import com.beyondsolo.network.GatePayload;
import com.beyondsolo.registry.ModAttachments;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.spawn.FirstSpawnHandler;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How a player gets the System.
 *
 * <p>Everyone starts without it: no System window, no pop-ups, no quests ({@link SystemAPI#hasSystem}). Gates can
 * still be walked into. The first gate of rank D or higher that a player without the System walks into hides the
 * sealed door ({@link #enteringGate}, {@link HiddenKind#SEALED}): a chamber past the boss's hall, walled off like any
 * hidden chamber. When the boss falls with them inside, the wall breaks by itself and a sealed gate opens in the
 * chamber, their door ({@link #revealDoors}). The cave's way out won't take them while it stands ({@link #heldBack}).</p>
 *
 * <p>Behind it is the trial ({@link com.beyondsolo.gate.GateTheme#TRIAL}): since 0.1.1 a temple, a short way down
 * past the Arbiter's tenets to the hall of the {@link ArbiterEntity Hollow Arbiter}, and nothing else in there. The
 * door closes behind them. Nobody dies in the trial. A fall on the way puts the player back on their feet at the last
 * room they reached; falling to the Arbiter is what the trial is for: the death is refused, the System binds to them
 * (the awakening plays on screen), and they wake outside the gate they first went into, with everything they carried.
 * Then the usual road starts: find a village, get evaluated.</p>
 *
 * <p>The hidden way: the Arbiter can be killed. Then its cache holds one random legendary weapon, and the System
 * binds all the same.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class AwakeningService {
    /** Ticks of the awakening on screen (see {@code client.awakening.AwakeningOverlay}). */
    public static final int TELEPORT_TICK = 76;
    public static final int GRANT_TICK = 268;
    public static final int END_TICK = 290;
    public static final String SLAYER_TITLE = "title.beyondsolo.arbiter_slayer";

    private static final ResourceLocation FREEZE = BeyondSolo.id("awakening_freeze");
    private static final String LEGENDARY_TAG = "beyondsolo_legendary_for";

    private record Sequence(long started, boolean slain) {}

    private static final Map<UUID, Sequence> SEQUENCES = new HashMap<>();
    private static final Map<UUID, Long> SHIELDED = new HashMap<>();
    private static final Map<UUID, Long> TETHER_FX = new HashMap<>();

    public static AwakeningData data(Player player) {
        return player.getData(ModAttachments.AWAKENING);
    }

    public static Stage stage(Player player) {
        return data(player).stage();
    }

    public static boolean inSequence(Player player) {
        return SEQUENCES.containsKey(player.getUUID());
    }

    // ------------------------------------------------------------------ the call

    /** Ticks from the boss's fall to the wall breaking: once it has crumbled, before the way out opens. */
    public static final int REVEAL_DELAY = com.beyondsolo.gate.GateBossEntity.DEATH_TICKS + 20;

    /**
     * A player without the System is about to go into a gate. If it is of rank D or higher and nobody has been in yet,
     * the sealed door is hidden in it ({@link HiddenKind#SEALED}, in place of whatever chamber its seed hid): a chamber
     * past the boss's hall, behind a wall that breaks once the boss falls. Caves carved before hidden chambers existed,
     * and the rare cave with no room for it, are passed over: the next gate does it.
     */
    public static void enteringGate(ServerPlayer player, GateInstance g) {
        if (!Config.AWAKENING_TRIAL.get() || g.isTrial() || g.rank().tier() < 1 || g.built() || GateService.isBuilding(g.id())) return;
        // someone the door is still waiting for: the player, or a party member of theirs right there with them
        if (!waitsForDoor(player) && !partyWaitsForDoor(player)) return;
        GateDepths depths = g.depths();
        if (!depths.enabled() || depths.hidden(g) == HiddenKind.SEALED) return;
        // the door hides behind the boss: a dungeon with no room for it there is laid out as caverns instead
        com.beyondsolo.gate.DungeonShape shape = DungeonLayout.shapeForDoor(g.seed(), g.theme(), g.realm(), g.shape());
        if (shape == null) return;
        if (shape != g.shape()) g.setDungeon(g.realm(), shape);
        depths.setHidden(HiddenKind.SEALED);
        g.replan();
        GateData.get(player.server).setDirty();
    }

    private static boolean waitsForDoor(ServerPlayer p) {
        return !SystemAPI.hasSystem(p) && !p.isSpectator() && stage(p) == Stage.DORMANT;
    }

    /** A party member of theirs within 48 blocks who still waits for their door (they will go in together). */
    private static boolean partyWaitsForDoor(ServerPlayer player) {
        for (ServerPlayer m : com.beyondsolo.party.Parties.mates(player)) {
            if (m.level() == player.level() && m.distanceToSqr(player) <= 48 * 48 && waitsForDoor(m)) return true;
        }
        return false;
    }

    /** True if the sealed door is hidden in this cave. */
    public static boolean holdsTheDoor(GateInstance g) {
        return Config.AWAKENING_TRIAL.get() && !g.isTrial() && g.rank().tier() >= 1
                && g.depths().hidden(g) == HiddenKind.SEALED && g.layout().hidden() != null;
    }

    /**
     * A gate was cleared: players without the System who were in it are called, if their door was hidden in it, and a
     * moment later (the boss has crumbled) the wall breaks and their doors open ({@link #revealDoors}).
     */
    public static void gateCleared(ServerLevel level, GateInstance g, List<ServerPlayer> players) {
        if (!holdsTheDoor(g)) return;
        boolean called = false;
        for (ServerPlayer p : players) {
            if (SystemAPI.hasSystem(p) || p.isSpectator()) continue;
            AwakeningData d = data(p);
            if (d.stage() == Stage.DORMANT) {
                d.setStage(Stage.CALLED);
                called = true;
            }
        }
        if (!called) return;
        ServerScheduler.schedule(REVEAL_DELAY, () -> {
            List<ServerPlayer> waiting = new ArrayList<>();
            for (ServerPlayer p : GateService.playersIn(level, g)) {
                if (!SystemAPI.hasSystem(p) && stage(p) == Stage.CALLED && data(p).sealedPos() == null) waiting.add(p);
            }
            revealDoors(level, g, waiting);
        });
    }

    /**
     * The wall past the boss's hall breaks (no System moment, no XP: this one is not the System's to find), and a
     * sealed gate opens in the chamber behind it for each player called here: the door to their trial.
     */
    public static void revealDoors(ServerLevel level, GateInstance cave, List<ServerPlayer> called) {
        if (called.isEmpty() || !holdsTheDoor(cave) || !cave.built()) return;
        if (!cave.depths().hiddenOpen()) HiddenRooms.open(level, cave, null, null);
        // one door for each party called here (they go through the trial together), one for each hunter on their own
        Map<UUID, List<ServerPlayer>> groups = new java.util.LinkedHashMap<>();
        for (ServerPlayer p : called) {
            if (SystemAPI.hasSystem(p) || stage(p) != Stage.CALLED) continue;
            com.beyondsolo.party.Party party = com.beyondsolo.party.Parties.of(p);
            groups.computeIfAbsent(party == null ? p.getUUID() : party.id(), k -> new ArrayList<>()).add(p);
        }
        int index = 0;
        for (List<ServerPlayer> group : groups.values()) openDoor(level, cave, group, index++);
    }

    /**
     * One door, side by side with any others in the chamber, facing the way in: a player's own, or one for a party,
     * opened for its leader if they are among them (else whoever came first) and letting the others through as well.
     */
    private static void openDoor(ServerLevel level, GateInstance cave, List<ServerPlayer> group, int index) {
        ServerPlayer player = group.get(0);
        com.beyondsolo.party.Party party = com.beyondsolo.party.Parties.of(player);
        if (party != null) for (ServerPlayer p : group) if (party.isLeader(p.getUUID())) player = p;
        // their old doors close (unless someone else, outside this group, is still bound to one)
        for (ServerPlayer p : group) {
            AwakeningData old = data(p);
            if (!sharedDoor(p, old, group)) closeSealedGate(p.server, old);
        }
        DepthsBuild.Door spot = DepthsBuild.sealedDoor(level, cave, index);
        BlockPos at = spot.pos();
        Direction side = spot.facing().getClockWise();
        // room for the door: nothing in the way of its arch
        for (int y = 0; y <= 5; y++) {
            for (int s = -2; s <= 2; s++) {
                BlockPos q = at.above(y).relative(side, s);
                if (!level.getBlockState(q).isAir()) level.setBlock(q, net.minecraft.world.level.block.Blocks.CAVE_AIR.defaultBlockState(), 3);
            }
        }
        GateBlockEntity door = GateService.spawnSealedGate(level, at, spot.facing(), player);
        GateInstance trial = GateData.get(player.server).get(door.gateId());
        // when it is over they wake where they first went in: in front of this cave's gate
        if (trial != null) trial.setTrialHome(GateService.home(player.server, cave, player.getYRot()));
        GateData.get(player.server).setDirty();
        for (ServerPlayer p : group) {
            data(p).setSealedGate(door.gateId(), level.dimension(), at);
            PacketDistributor.sendToPlayer(p, new AwakeningPayload(AwakeningPayload.CALLED, 0, at.getX() + 0.5, at.getY() + GateBlockEntity.CENTER_Y, at.getZ() + 0.5));
        }
    }

    /**
     * True if a player may go through a sealed gate: it opened for them, or for the party they were called with (they
     * are bound to it and have not gone through yet, or are in the trial already).
     */
    public static boolean mayEnter(ServerPlayer player, GateInstance g) {
        if (!g.isTrial() || player.getUUID().equals(g.owner())) return true;
        AwakeningData d = data(player);
        return g.id().equals(d.trial()) && (d.stage() == Stage.CALLED || d.stage() == Stage.TRIAL);
    }

    /** True if someone else on the server is still bound to this player's door (a party called together). */
    private static boolean sharedDoor(ServerPlayer player, AwakeningData d) {
        return sharedDoor(player, d, List.of());
    }

    private static boolean sharedDoor(ServerPlayer player, AwakeningData d, List<ServerPlayer> besides) {
        if (d.trial() == null) return false;
        for (ServerPlayer p : com.beyondsolo.util.OnlinePlayers.all(player.server)) {
            if (p == player || besides.contains(p)) continue;
            AwakeningData o = data(p);
            if (d.trial().equals(o.trial()) && (o.stage() == Stage.CALLED || o.stage() == Stage.TRIAL || o.stage() == Stage.AWAKENING)) return true;
        }
        return false;
    }

    /** Those still in the cave a door opened for who have not gone through it yet (not {@code except}). */
    private static List<ServerPlayer> stillToCome(MinecraftServer server, GateInstance trial, @Nullable ServerPlayer except) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : com.beyondsolo.util.OnlinePlayers.all(server)) {
            if (p == except || !p.isAlive()) continue;
            AwakeningData o = data(p);
            if (!trial.id().equals(o.trial()) || o.stage() != Stage.CALLED || o.sealedPos() == null) continue;
            if (!GateService.isDungeon(p.level()) || GateData.slotAt(p.blockPosition()) != GateData.slotAt(o.sealedPos())) continue;
            out.add(p);
        }
        return out;
    }

    /**
     * The players in a trial (alive, there in person) who are still facing the Arbiter: bound to it, without the
     * System, not already falling into their awakening. Not {@code except}.
     */
    static List<ServerPlayer> challengers(MinecraftServer server, GateInstance trial, @Nullable ServerPlayer except) {
        List<ServerPlayer> out = new ArrayList<>();
        int slot = GateData.slotAt(trial.entrance());
        for (ServerPlayer p : com.beyondsolo.util.OnlinePlayers.all(server)) {
            if (p == except || !p.isAlive() || p.isSpectator() || inSequence(p) || SystemAPI.hasSystem(p)) continue;
            if (!GateService.isDungeon(p.level()) || GateData.slotAt(p.blockPosition()) != slot) continue;
            if (!trial.id().equals(data(p).trial()) && !p.getUUID().equals(trial.owner())) continue;
            out.add(p);
        }
        return out;
    }

    /** Anyone still in a trial's hall or on the way to it, falling or standing (not {@code except}). */
    private static boolean anyoneLeftIn(MinecraftServer server, GateInstance trial, ServerPlayer except) {
        int slot = GateData.slotAt(trial.entrance());
        for (ServerPlayer p : com.beyondsolo.util.OnlinePlayers.all(server)) {
            if (p != except && GateService.isDungeon(p.level()) && GateData.slotAt(p.blockPosition()) == slot) return true;
        }
        return false;
    }

    /**
     * True while a called player stands in the cave where their door is open: its way out won't take them
     * ({@code GateService.beginExit}). The door is the only way on.
     */
    public static boolean heldBack(ServerPlayer player) {
        AwakeningData d = data(player);
        if (d.stage() != Stage.CALLED || d.sealedPos() == null || !player.level().dimension().equals(d.sealedDim())) return false;
        if (!GateService.isDungeon(player.level()) || GateData.slotAt(player.blockPosition()) != GateData.slotAt(d.sealedPos())) return false;
        GateInstance trial = d.trial() == null ? null : GateData.get(player.server).get(d.trial());
        return trial != null && trial.state() == GateInstance.State.OPEN
                && player.level().getBlockEntity(d.sealedPos()) instanceof GateBlockEntity door && door.isSealed() && !door.isClosing();
    }

    /** A player stepped back out of a gate: a call whose door they never went through fades. */
    public static void leftGate(ServerPlayer player, GateInstance g) {
        AwakeningData d = data(player);
        if (d.stage() == Stage.CALLED && !g.isTrial()) lapse(player, d);
    }

    /** The call fades: their door, if it still stands, closes; their next gate of rank D or higher hides it again. */
    private static void lapse(ServerPlayer player, AwakeningData d) {
        if (!sharedDoor(player, d)) closeSealedGate(player.server, d);
        d.setStage(Stage.DORMANT);
        d.setSealedGate(null, null, null);
    }

    /**
     * Calls a player now, whatever they have or haven't cleared (commands, tests): standing in a cave that hides the
     * door, its wall breaks and the door opens as if the boss had just fallen; anywhere else a sealed gate opens in
     * front of them.
     */
    public static boolean call(ServerPlayer player) {
        AwakeningData d = data(player);
        if (SystemAPI.hasSystem(player) || d.stage() == Stage.TRIAL || d.stage() == Stage.AWAKENING) return false;
        GateInstance cave = GateService.isDungeon(player.level()) ? GateData.get(player.server).bySlotPos(player.blockPosition()) : null;
        if (cave != null && cave.built() && holdsTheDoor(cave)) {
            d.setStage(Stage.CALLED);
            revealDoors(player.serverLevel(), cave, List.of(player));
            return d.sealedPos() != null;
        }
        d.setStage(Stage.CALLED);
        return openSealedGate(player) != null;
    }

    /** Tears the sealed gate open in front of the player (out in the world: the trial command). */
    @Nullable
    public static GateBlockEntity openSealedGate(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (GateService.isDungeon(level) && level.dimension().equals(GateService.DIMENSION)) return null;
        AwakeningData d = data(player);
        closeSealedGate(player.server, d);
        Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
        BlockPos best = null;
        for (int attempt = 0; attempt < 24 && best == null; attempt++) {
            // straight ahead first, then fanning out and closer
            float turn = (attempt % 2 == 0 ? 1 : -1) * (attempt / 2) * 0.28f;
            double dist = 8 - (attempt / 8) * 1.5;
            Vec3 at = player.position().add(look.yRot(turn).scale(dist));
            BlockPos column = BlockPos.containing(at);
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos p = column.atY(player.blockPosition().getY() + dy);
                if (GateFeature.goodSpot(level, p)) {
                    best = p;
                    break;
                }
            }
        }
        if (best == null) {
            // nowhere good (a cave, the sea): stand it right where they look and make room
            best = BlockPos.containing(player.position().add(look.scale(6))).atY(player.blockPosition().getY());
            for (int y = 0; y < 5; y++) {
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) level.destroyBlock(best.offset(x, y, z), false);
            }
            if (level.getBlockState(best.below()).canBeReplaced()) level.setBlock(best.below(), net.minecraft.world.level.block.Blocks.CALCITE.defaultBlockState(), 3);
        }
        Direction facing = Direction.getNearest(player.getX() - best.getX(), 0, player.getZ() - best.getZ());
        if (facing.getAxis().isVertical()) facing = Direction.NORTH;
        GateBlockEntity gate = GateService.spawnSealedGate(level, best, facing, player);
        d.setSealedGate(gate.gateId(), level.dimension(), best);
        PacketDistributor.sendToPlayer(player, new AwakeningPayload(AwakeningPayload.CALLED, 0, best.getX() + 0.5, best.getY() + GateBlockEntity.CENTER_Y, best.getZ() + 0.5));
        return gate;
    }

    private static void closeSealedGate(MinecraftServer server, AwakeningData d) {
        if (d.sealedPos() == null || d.sealedDim() == null) return;
        ServerLevel level = server.getLevel(d.sealedDim());
        if (level != null && level.isLoaded(d.sealedPos()) && level.getBlockEntity(d.sealedPos()) instanceof GateBlockEntity gate && gate.isSealed()) {
            gate.startClosing(level.getGameTime());
            level.playSound(null, d.sealedPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5f, 0.5f);
        }
        if (d.trial() != null) {
            GateInstance g = GateData.get(server).get(d.trial());
            if (g != null && g.state() == GateInstance.State.OPEN && !g.built()) {
                g.setState(GateInstance.State.CLOSED);
                GateData.get(server).setDirty();
            }
        }
    }

    /**
     * Once a player is called, their door holds them. In a cave it is the way out that won't take them
     * ({@link #heldBack}); a door out in the world (the trial command, or an old save's) pulls them back past
     * {@code awakening.tetherRadius}. A door they can no longer reach (they fell and woke at their bed, logged out and
     * the cave closed, got far away) lets them go: the call fades ({@link #lapse}), and the next gate hides it again.
     */
    private static void tether(ServerPlayer player, AwakeningData d) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (GateService.inTransit(player) || !player.isAlive() || player.isSpectator()) return;
        if (GateService.isDungeon(level) && level.dimension().equals(GateService.DIMENSION)) return;
        boolean here = d.sealedPos() != null && level.dimension().equals(d.sealedDim());
        double dist = here ? Math.sqrt(d.sealedPos().distToCenterSqr(player.position())) : Double.MAX_VALUE;
        int radius = Config.TETHER_RADIUS.get();
        boolean gone = here && level.isLoaded(d.sealedPos()) && !(level.getBlockEntity(d.sealedPos()) instanceof GateBlockEntity);
        if (!here || gone || dist > radius + 40) {
            lapse(player, d);
            return;
        }
        if (dist <= radius) return;
        if (player.isPassenger()) player.stopRiding();
        Vec3 to = Vec3.atCenterOf(d.sealedPos()).subtract(player.position());
        double pull = Math.min(1.4, 0.45 + (dist - radius) * 0.12);
        Vec3 v = to.multiply(1, 0, 1).normalize().scale(pull);
        player.setDeltaMovement(v.x, Math.max(player.getDeltaMovement().y, 0.28), v.z);
        player.hurtMarked = true;
        player.fallDistance = 0;
        if (TETHER_FX.getOrDefault(player.getUUID(), 0L) <= now) {
            TETHER_FX.put(player.getUUID(), now + 16);
            level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.4f, 0.5f);
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6f, 0.7f);
            PacketDistributor.sendToPlayer(player, new AwakeningPayload(AwakeningPayload.TETHER, 0,
                    d.sealedPos().getX() + 0.5, d.sealedPos().getY() + GateBlockEntity.CENTER_Y, d.sealedPos().getZ() + 0.5));
        }
    }

    // ------------------------------------------------------------------ the trial

    /**
     * Someone it opened for stepped through the sealed gate. A door found in a cave closes behind the last of them (a
     * party called together goes through one by one): the way back is gone.
     */
    public static void arrived(ServerPlayer player, GateInstance g) {
        if (!mayEnter(player, g)) return;
        AwakeningData d = data(player);
        if (d.stage() == Stage.DORMANT || d.stage() == Stage.CALLED) d.setStage(Stage.TRIAL);
        if (d.trial() == null) d.setSealedGate(g.id(), g.gateDimension(), g.gatePos());
        if (g.trialHome() != null && stillToCome(player.server, g, player).isEmpty()) shutDoor(player.server, d);
        PacketDistributor.sendToPlayer(player, AwakeningPayload.of(AwakeningPayload.TRIAL_ARRIVE));
    }

    /** The door a player came through closes (the trial behind it stays theirs). */
    private static void shutDoor(MinecraftServer server, AwakeningData d) {
        if (d.sealedPos() == null || d.sealedDim() == null) return;
        ServerLevel level = server.getLevel(d.sealedDim());
        if (level != null && level.isLoaded(d.sealedPos()) && level.getBlockEntity(d.sealedPos()) instanceof GateBlockEntity gate
                && gate.isSealed() && !gate.isClosing()) {
            gate.startClosing(level.getGameTime());
            level.playSound(null, d.sealedPos(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.6f, 0.4f);
            level.playSound(null, d.sealedPos(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.6f, 0.5f);
        }
    }

    public static void spawnArbiter(ServerLevel dungeon, GateInstance g) {
        ArbiterEntity arbiter = ModEntities.ARBITER.get().create(dungeon);
        if (arbiter == null) return;
        BlockPos c = g.bossRoomCenter();
        BlockPos from = g.entrance();
        for (DungeonLayout.Tunnel t : g.layout().tunnels()) {
            if (g.layout().rooms().get(t.to()).kind() == DungeonLayout.Kind.BOSS) from = g.world(t.start());
        }
        float yaw = (float) (Mth.atan2(from.getZ() - c.getZ(), from.getX() - c.getX()) * Mth.RAD_TO_DEG) - 90f;
        arbiter.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, yaw, 0f);
        arbiter.setYHeadRot(yaw);
        arbiter.setYBodyRot(yaw);
        arbiter.setGate(g.id());
        dungeon.addFreshEntity(arbiter);
        g.setBoss(arbiter.getUUID());
        g.setBossSpawned(true);
    }

    public static void wakeArbiter(ServerLevel dungeon, GateInstance g, ArbiterEntity arbiter) {
        arbiter.wake();
        for (ServerPlayer p : GateService.playersIn(dungeon, g)) {
            PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.BOSS_INTRO, g.theme().ordinal(), arbiter.getId(), 0,
                    arbiter.getDisplayName(), Component.translatable("gate.beyondsolo.arbiter_subtitle")));
        }
    }

    @Nullable
    public static ArbiterEntity arbiter(ServerLevel dungeon, GateInstance g) {
        return g.boss() != null && dungeon.getEntity(g.boss()) instanceof ArbiterEntity a ? a : null;
    }

    /** The trial a player is standing in, if any. */
    @Nullable
    public static GateInstance trialAt(ServerPlayer player) {
        if (!GateService.isDungeon(player.level())) return null;
        GateInstance g = GateData.get(player.server).bySlotPos(player.blockPosition());
        return g != null && g.isTrial() ? g : null;
    }

    private static boolean inArbiterHall(ServerPlayer player, GateInstance g) {
        DungeonLayout.Room room = g.layout().boss();
        BlockPos c = g.world(room.center());
        double dx = player.getX() - c.getX(), dz = player.getZ() - c.getZ();
        return Math.sqrt(dx * dx + dz * dz) < room.reach() + 6 && Math.abs(player.getY() - c.getY()) < room.ry() + 8;
    }

    /** Nobody dies in the trial: the death is refused, and what happens instead depends on where they fell. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        GateInstance g = trialAt(player);
        if (g == null) return;
        event.setCanceled(true);
        player.setHealth(1f);
        if (inSequence(player)) return;
        if (g.bossDefeated()) {
            begin(player, g, true);
        } else if (g.bossAwake() && inArbiterHall(player, g)) {
            begin(player, g, false);
        } else {
            revive(player, g);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (inSequence(player) || SHIELDED.getOrDefault(player.getUUID(), 0L) > player.level().getGameTime()) event.setCanceled(true);
    }

    /** Fell on the way down: back on their feet at the last cavern they reached. */
    public static void revive(ServerPlayer player, GateInstance g) {
        ServerLevel dungeon = player.serverLevel();
        List<DungeonLayout.Room> rooms = g.layout().rooms();
        DungeonLayout.Room room = rooms.get(Mth.clamp(g.checkpoint(), 0, rooms.size() - 1));
        if (room.kind() == DungeonLayout.Kind.BOSS) room = rooms.get(Math.max(0, room.index() - 1));
        BlockPos at = g.world(new BlockPos(room.center().getX(), room.floorY(), room.center().getZ()));
        restore(player);
        player.teleportTo(dungeon, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0f);
        if (player.distanceToSqr(Vec3.atBottomCenterOf(at)) > 4) player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0f);
        player.setDeltaMovement(Vec3.ZERO);
        SHIELDED.put(player.getUUID(), dungeon.getGameTime() + 60);
        dungeon.playSound(null, at, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
        PacketDistributor.sendToPlayer(player, AwakeningPayload.of(AwakeningPayload.REVIVE));
    }

    private static void restore(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(Math.max(player.getFoodData().getFoodLevel(), 14));
        player.clearFire();
        player.setTicksFrozen(0);
        player.removeAllEffects();
        for (var type : Buildups.all()) Buildups.clear(player, type);
        player.fallDistance = 0;
    }

    // ------------------------------------------------------------------ the awakening

    /** Starts the awakening for a player in the trial: they fell to the Arbiter, or killed it. */
    public static void begin(ServerPlayer player, GateInstance g, boolean slain) {
        if (inSequence(player) || SystemAPI.hasSystem(player) && stage(player) == Stage.DONE) return;
        ServerLevel level = player.serverLevel();
        AwakeningData d = data(player);
        d.setStage(Stage.AWAKENING);
        d.setArbiterSlain(slain);
        if (d.trial() == null) d.setSealedGate(g.id(), g.gateDimension(), g.gatePos());
        SEQUENCES.put(player.getUUID(), new Sequence(level.getGameTime(), slain));
        freeze(player, true);
        player.setHealth(Math.max(1f, player.getHealth()));
        player.setDeltaMovement(Vec3.ZERO);
        if (!slain) {
            // it stands over the fallen only once nobody is left to face it (a party falls one by one)
            ArbiterEntity arbiter = arbiter(level, g);
            if (arbiter != null && challengers(player.server, g, player).isEmpty()) arbiter.passVerdict();
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2f, 0.6f);
        PacketDistributor.sendToPlayer(player, AwakeningPayload.of(AwakeningPayload.AWAKEN, slain ? 1 : 0));
    }

    private static void freeze(ServerPlayer player, boolean on) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(FREEZE);
            if (on) speed.addTransientModifier(new AttributeModifier(FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        var jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(FREEZE);
            if (on) jump.addTransientModifier(new AttributeModifier(FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void tickSequence(ServerPlayer player, Sequence seq) {
        long t = player.level().getGameTime() - seq.started;
        player.setDeltaMovement(player.getDeltaMovement().multiply(0, 1, 0));
        if (t == TELEPORT_TICK) wakeOutside(player);
        if (t == GRANT_TICK) bind(player, seq.slain);
        if (t >= END_TICK) {
            SEQUENCES.remove(player.getUUID());
            freeze(player, false);
            SHIELDED.put(player.getUUID(), player.level().getGameTime() + 100);
            FirstSpawnHandler.systemIntro(player, 30);
        }
    }

    /** While the screen is black: out of the trial, whole again, with everything they carried. */
    private static void wakeOutside(ServerPlayer player) {
        AwakeningData d = data(player);
        MinecraftServer server = player.server;
        collectLegendary(player);
        GateInstance g = d.trial() == null ? null : GateData.get(server).get(d.trial());
        if (g != null && g.state() == GateInstance.State.OPEN && !anyoneLeftIn(server, g, player)) {
            g.setState(GateInstance.State.CLEARED);
            g.setClearedAt(player.level().getGameTime());
            GateData.get(server).setDirty();
        }
        restore(player);
        // a door found in a cave: they wake where they first went in, in front of that cave's gate (the cave is gone)
        if (g != null && g.trialHome() != null) {
            GateService.place(player, g.trialHome());
            closeSealedGate(server, d);
            return;
        }
        if (!GateService.isDungeon(player.level()) || !player.level().dimension().equals(GateService.DIMENSION)) {
            if (player.server instanceof net.minecraft.gametest.framework.GameTestServer) closeSealedGate(server, d);
            return;
        }
        ServerLevel level = d.sealedDim() == null ? null : server.getLevel(d.sealedDim());
        Vec3 to = null;
        float yaw = player.getYRot();
        if (level != null && d.sealedPos() != null) {
            if (level.getBlockEntity(d.sealedPos()) instanceof GateBlockEntity gate) {
                to = gate.front();
                // their back to where the gate was: the world, not the hole, is the first thing they see
                yaw = gate.facing().toYRot();
            } else {
                to = Vec3.atBottomCenterOf(d.sealedPos());
            }
        }
        if (to == null) {
            level = server.overworld();
            BlockPos spawn = player.getRespawnPosition() != null && Level.OVERWORLD.equals(player.getRespawnDimension()) ? player.getRespawnPosition() : level.getSharedSpawnPos();
            to = Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn));
        }
        player.teleportTo(level, to.x, to.y, to.z, yaw, 0f);
        player.setDeltaMovement(Vec3.ZERO);
        closeSealedGate(server, d);
    }

    private static void bind(ServerPlayer player, boolean slain) {
        AwakeningData d = data(player);
        d.setStage(Stage.DONE);
        d.setSealedGate(d.trial(), null, null);
        SystemAPI.grantSystem(player);
        if (slain) SystemAPI.setTitle(player, SLAYER_TITLE);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.4f);
    }

    /** Finishes an awakening at once (the player logged out halfway, the server stopped). */
    public static void finishNow(ServerPlayer player) {
        AwakeningData d = data(player);
        SEQUENCES.remove(player.getUUID());
        freeze(player, false);
        wakeOutside(player);
        bind(player, d.arbiterSlain());
    }

    // ------------------------------------------------------------------ the hidden way

    public static ItemStack rollLegendary(RandomSource r) {
        return com.beyondsolo.gear.GearRolls.roll(new ItemStack(ModItems.LEGENDARIES.get(r.nextInt(ModItems.LEGENDARIES.size())).get()), r);
    }

    static void arbiterSlain(ServerLevel level, ArbiterEntity arbiter, DamageSource source) {
        GateData gates = GateData.get(level.getServer());
        GateInstance g = arbiter.gateId() == null ? null : gates.get(arbiter.gateId());
        if (g == null || g.bossDefeated()) return;
        g.setBossDefeated(true);
        gates.setDirty();
        for (ServerPlayer p : GateService.playersIn(level, g)) {
            PacketDistributor.sendToPlayer(p, AwakeningPayload.of(AwakeningPayload.SLAIN));
        }
        // everyone still standing against it earns the hidden way (a party that brings it down: a cache each)
        List<UUID> victors = new ArrayList<>();
        for (ServerPlayer p : challengers(level.getServer(), g, null)) victors.add(p.getUUID());
        if (victors.isEmpty() && g.owner() != null) victors.add(g.owner());
        for (UUID id : victors) {
            ServerPlayer p = com.beyondsolo.util.OnlinePlayers.get(level.getServer(), id);
            if (p == null) continue;
            data(p).setArbiterSlain(true);
            SHIELDED.put(p.getUUID(), level.getGameTime() + ArbiterEntity.DEATH_TICKS + 20 * 30);
        }
        BlockPos center = arbiter.blockPosition();
        // once it has crumbled, its caches come down where it stood, side by side
        ServerScheduler.schedule(ArbiterEntity.DEATH_TICKS + 10, () -> {
            for (int i = 0; i < victors.size(); i++) {
                UUID ownerId = victors.get(i);
                BlockPos at = center.offset((int) Math.round((i - (victors.size() - 1) / 2.0) * 2), 0, 0);
                for (int k = 0; k < 6 && !level.getBlockState(at).isAir(); k++) at = at.above();
                for (int k = 0; k < 8 && level.getBlockState(at.below()).isAir(); k++) at = at.below();
                ServerPlayer p = com.beyondsolo.util.OnlinePlayers.get(level.getServer(), ownerId);
                Direction facing = p == null ? Direction.NORTH : Direction.getNearest(p.getX() - at.getX(), 0, p.getZ() - at.getZ());
                if (facing.getAxis().isVertical()) facing = Direction.NORTH;
                level.setBlock(at, ModBlocks.REWARD_CACHE.get().defaultBlockState().setValue(RewardCacheBlock.FACING, facing), 3);
                if (level.getBlockEntity(at) instanceof RewardCacheBlockEntity cache) cache.setupLegendary(ownerId, level.getGameTime());
                BlockPos cachePos = at;
                // it opens by itself if nobody dares touch it
                ServerScheduler.schedule(20 * 5, () -> {
                    if (level.getBlockEntity(cachePos) instanceof RewardCacheBlockEntity cache && !cache.isOpen()) cache.open(null);
                });
            }
        });
    }

    /** The legendary cache burst: the weapon is out, and the awakening follows. */
    public static void legendaryOut(ServerLevel level, @Nullable UUID ownerId, ItemEntity item) {
        if (ownerId == null) return;
        item.getPersistentData().putUUID(LEGENDARY_TAG, ownerId);
        item.setTarget(ownerId);
        item.setUnlimitedLifetime();
        ServerScheduler.schedule(90, () -> {
            ServerPlayer p = com.beyondsolo.util.OnlinePlayers.get(level.getServer(), ownerId);
            if (p == null) return;
            GateInstance g = trialAt(p);
            if (g != null) begin(p, g, true);
        });
    }

    /** Whatever of the legendary is still lying in the hall goes with them. */
    private static void collectLegendary(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(80),
                e -> e.getPersistentData().hasUUID(LEGENDARY_TAG) && e.getPersistentData().getUUID(LEGENDARY_TAG).equals(player.getUUID()))) {
            ItemStack stack = item.getItem().copy();
            item.discard();
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Sequence seq = SEQUENCES.get(player.getUUID());
        if (seq != null) {
            tickSequence(player, seq);
            return;
        }
        if (player.tickCount % 5 != 0) return;
        AwakeningData d = data(player);
        switch (d.stage()) {
            case CALLED -> tether(player, d);
            case TRIAL -> {
                // out of the trial some other way (a command, the cave closing): the gate takes them back
                if (player.tickCount % 40 == 0 && trialAt(player) == null && !GateService.inTransit(player) && !SystemAPI.hasSystem(player)) {
                    d.setStage(Stage.CALLED);
                    GateInstance g = d.trial() == null ? null : GateData.get(player.server).get(d.trial());
                    if (g == null || g.state() != GateInstance.State.OPEN) d.setSealedGate(null, null, null);
                }
            }
            case DORMANT -> {
                if (SystemAPI.hasSystem(player)) d.setStage(Stage.DONE);
            }
            default -> {}
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AwakeningData d = data(player);
        if (d.stage() == Stage.AWAKENING) {
            finishNow(player);
            FirstSpawnHandler.systemIntro(player, 80);
        }
        if (!Config.AWAKENING_TRIAL.get() && !SystemAPI.hasSystem(player) && d.stage() == Stage.DORMANT) {
            d.setStage(Stage.DONE);
            SystemAPI.grantSystem(player);
        }
        if (!SystemAPI.hasSystem(player) && !d.hinted()) {
            d.setHinted(true);
            ServerScheduler.schedule(100, () -> {
                if (!player.hasDisconnected()) player.sendSystemMessage(Component.translatable("message.beyondsolo.presystem.hint").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            });
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (SEQUENCES.containsKey(player.getUUID())) finishNow(player);
        SHIELDED.remove(player.getUUID());
        TETHER_FX.remove(player.getUUID());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        SEQUENCES.clear();
        SHIELDED.clear();
        TETHER_FX.clear();
    }

    /** Back to the very start (commands, tests): no System, not called. */
    public static void reset(ServerPlayer player) {
        AwakeningData d = data(player);
        boolean shared = sharedDoor(player, d);
        if (!shared) closeSealedGate(player.server, d);
        // the trial behind it goes too, built or not, so the next call starts a new one (unless others still share it)
        GateInstance old = d.trial() == null || shared ? null : GateData.get(player.server).get(d.trial());
        if (old != null && old.state() == GateInstance.State.OPEN) {
            old.setState(GateInstance.State.CLOSED);
            GateData.get(player.server).setDirty();
        }
        SEQUENCES.remove(player.getUUID());
        freeze(player, false);
        d.setStage(Stage.DORMANT);
        d.setSealedGate(null, null, null);
        d.setArbiterSlain(false);
    }

    private AwakeningService() {}
}
