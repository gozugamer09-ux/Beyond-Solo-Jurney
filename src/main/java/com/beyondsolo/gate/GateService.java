package com.beyondsolo.gate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.network.GatePayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs gates: registering them, building their caves, taking players through (with the enter and exit moments),
 * waking packs of monsters as players reach each cavern, waking the boss, and what happens when it falls (the
 * reward cache, the exit gate, the clear rewards, the gate in the world closing).
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class GateService {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, BeyondSolo.id("gate_depths"));
    public static final String QUEST_TEMPLATE = BeyondSolo.id("gate/clear").toString();
    /** Custom quest objectives other quests can use: a gate cleared, a gate monster killed, a gate boss killed. */
    public static final String CLEAR_KEY = BeyondSolo.id("gate_clear").toString();
    public static final String MOB_KEY = BeyondSolo.id("gate_mob").toString();
    public static final String BOSS_KEY = BeyondSolo.id("gate_boss").toString();
    /** Persistent data on monsters that belong to a gate: the gate's rank. */
    public static final String MOB_TAG = "beyondsolo_gate_rank";
    /** Persistent-data key: the element (ordinal) a gate creature's hits carry, its realm's (see {@link GateInstance#element}). */
    public static final String ELEMENT_TAG = "beyondsolo_gate_element";
    /**
     * Persistent data on a player inside a cave: the id of its gate. A cave's slot is handed to a new gate once it has
     * been wiped ({@link GateWiper}), so someone who logged out in a cave is checked against it when they come back.
     */
    public static final String IN_GATE_TAG = "beyondsolo_in_gate";

    /** Ticks from stepping into a gate to arriving on the other side (the overlay's swirl lasts as long). */
    public static final int ENTER_TICKS = 44;
    public static final int EXIT_TICKS = 40;
    private static final ResourceLocation FREEZE = BeyondSolo.id("gate_transit");

    private record Transit(UUID gate, boolean exit, long started) {}

    private static final Map<UUID, Transit> TRANSITS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Map<UUID, DungeonBuilder> BUILDERS = new HashMap<>();
    private static final Map<UUID, Set<UUID>> NOTICED = new HashMap<>();

    /**
     * The level gate caves are carved into. The game-test server does not load datapack dimensions, so there (and only
     * there) caves go into the overworld, far from everything, and are walled in by the builder.
     */
    @Nullable
    public static ServerLevel dungeon(MinecraftServer server) {
        ServerLevel level = server.getLevel(DIMENSION);
        if (level == null && server instanceof net.minecraft.gametest.framework.GameTestServer) return server.overworld();
        return level;
    }

    /** True if {@code level} is where gate caves are. */
    public static boolean isDungeon(Level level) {
        return level instanceof ServerLevel server ? dungeon(server.getServer()) == server : level.dimension().equals(DIMENSION);
    }

    // ------------------------------------------------------------------ registering and spawning

    /** Gives a freshly placed gate (world-gen, a command) its instance. */
    public static void register(ServerLevel level, GateBlockEntity gate) {
        UUID id = UUID.randomUUID();
        GateData.get(level.getServer()).create(id, gate.rank(), level.random.nextLong(), level.dimension(), gate.getBlockPos());
        gate.setGateId(id);
    }

    /** Places a new gate. {@code opening} plays the gate opening (it tears open where it stands). */
    public static GateBlockEntity spawnGate(ServerLevel level, BlockPos pos, PlayerRank rank, Direction facing, boolean opening) {
        level.setBlock(pos, ModBlocks.GATE.get().defaultBlockState().setValue(GateBlock.FACING, facing), 3);
        GateBlockEntity gate = (GateBlockEntity) level.getBlockEntity(pos);
        gate.setup(null, rank, false, opening ? level.getGameTime() : -1);
        register(level, gate);
        if (opening) {
            level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6f, 1.6f);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 0.6f);
        }
        return gate;
    }

    /** Opens the sealed gate of {@code owner}'s awakening trial (see {@code awakening.AwakeningService}). */
    public static GateBlockEntity spawnSealedGate(ServerLevel level, BlockPos pos, Direction facing, ServerPlayer owner) {
        level.setBlock(pos, ModBlocks.GATE.get().defaultBlockState().setValue(GateBlock.FACING, facing), 3);
        GateBlockEntity gate = (GateBlockEntity) level.getBlockEntity(pos);
        UUID id = UUID.randomUUID();
        GateInstance g = GateData.get(level.getServer()).create(id, PlayerRank.D, level.random.nextLong(), level.dimension(), pos);
        g.setOwner(owner.getUUID());
        g.setTemple(true);
        gate.setup(id, PlayerRank.D, false, level.getGameTime());
        gate.setSealed(true);
        level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8f, 0.5f);
        level.playSound(null, pos, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.BLOCKS, 1.5f, 0.5f);
        return gate;
    }

    /**
     * A new gate's rank, rolled at random: any gate can be any rank, wherever it opens and whoever is near (gates pay
     * no mind to the hunters around them). Low ranks are the common ones; the weights are {@code gates.rankWeights}.
     */
    public static PlayerRank randomRank(RandomSource r) {
        return randomRank(r, Config.GATE_RANK_WEIGHTS.get());
    }

    /** As above, with the weights given (E to S; a missing one counts as 0, and all 0 makes every rank as likely). */
    public static PlayerRank randomRank(RandomSource r, List<? extends Integer> weights) {
        PlayerRank[] ranks = PlayerRank.values();
        int total = 0;
        for (int i = 0; i < ranks.length; i++) total += weight(weights, i);
        if (total <= 0) return ranks[r.nextInt(ranks.length)];
        int roll = r.nextInt(total);
        for (int i = 0; i < ranks.length; i++) {
            roll -= weight(weights, i);
            if (roll < 0) return ranks[i];
        }
        return ranks[0];
    }

    private static int weight(List<? extends Integer> weights, int i) {
        return i < weights.size() && weights.get(i) != null ? Math.max(0, weights.get(i)) : 0;
    }

    /** Opens the gate of a dungeon key ({@code key.DungeonKeys}): a door into a fresh cave of the key's rank. */
    public static GateBlockEntity spawnKeyedGate(ServerLevel level, BlockPos pos, Direction facing, PlayerRank rank, GateInstance.Keyed keyed) {
        level.setBlock(pos, ModBlocks.GATE.get().defaultBlockState().setValue(GateBlock.FACING, facing), 3);
        GateBlockEntity gate = (GateBlockEntity) level.getBlockEntity(pos);
        UUID id = UUID.randomUUID();
        GateInstance g = GateData.get(level.getServer()).create(id, rank, level.random.nextLong(), level.dimension(), pos);
        g.setKeyed(keyed);
        // a sealed key holds its boss to its word (and an ascendant one, its crown)
        if (keyed.sigil != null && keyed.sigil.livesAt(rank)) g.setBossKind(keyed.sigil);
        if (keyed.ascendant) g.setBossAscended(true);
        gate.setup(id, rank, false, level.getGameTime());
        gate.setKeyed(true);
        level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.5f, 1.3f);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.2f, 0.5f);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2f, 0.8f);
        return gate;
    }

    /**
     * False (and the gate starts closing) once the gate's cave has been cleared and left, once a keyed gate's door has
     * shut, or once the gate's record is gone (its cave was wiped and forgotten while the gate was out of sight).
     */
    public static boolean stillOpen(ServerLevel level, GateBlockEntity gate) {
        if (gate.gateId() == null) return true;
        GateInstance g = GateData.get(level.getServer()).get(gate.gateId());
        if (g == null || g.state() == GateInstance.State.CLOSED || (g.isKeyed() && g.keyed().doorClosed)) {
            close(level, gate);
            return false;
        }
        return true;
    }

    /** The gate in the world closes, with its sound. */
    public static void close(ServerLevel level, GateBlockEntity gate) {
        if (gate.isClosing()) return;
        gate.startClosing(level.getGameTime());
        level.playSound(null, gate.getBlockPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5f, 0.7f);
        if (gate.isKeyed()) level.playSound(null, gate.getBlockPos(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.4f, 0.5f);
    }

    // ------------------------------------------------------------------ noticing gates

    /** True if the player has come within 40 blocks of this gate since the server started ({@link #noticeNearby}). */
    public static boolean noticed(ServerPlayer player, UUID gate) {
        Set<UUID> seen = NOTICED.get(player.getUUID());
        return seen != null && seen.contains(gate);
    }

    /** Players who come close to a gate for the first time are told about it (and warned if it is above them). */
    public static void noticeNearby(ServerLevel level, GateBlockEntity gate) {
        // a keyed gate was opened on purpose, by someone standing right there; a sealed one is nobody's business
        if (gate.gateId() == null || gate.isKeyed() || gate.isSealed()) return;
        GateInstance g = GateData.get(level.getServer()).get(gate.gateId());
        Component name = g != null ? g.dungeonName() : GateTheme.of(gate.rank()).dungeonName();
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(gate.getBlockPos()).inflate(40))) {
            Set<UUID> seen = NOTICED.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
            if (!seen.add(gate.gateId())) continue;
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.gate.detected.title", gate.rank().displayName()),
                    Component.translatable("notify.beyondsolo.gate.detected.body", name, gate.rank().displayName())));
            if (SystemAPI.isAwakened(p) && gate.rank().tier() > SystemAPI.get(p).rank().tier() + 1) {
                Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                        Component.translatable("notify.beyondsolo.gate.danger.title"),
                        Component.translatable("notify.beyondsolo.gate.danger.body", gate.rank().displayName())));
            }
        }
    }

    // ------------------------------------------------------------------ going through

    public static void beginEnter(ServerPlayer player, GateBlockEntity gate) {
        if (busy(player) || gate.gateId() == null) return;
        ServerLevel level = player.serverLevel();
        GateInstance g = GateData.get(level.getServer()).get(gate.gateId());
        if (g == null || g.state() != GateInstance.State.OPEN || (g.isKeyed() && g.keyed().doorClosed)) return;
        // a sealed gate is for the ones it opened for (a party called in the same cave shares one); someone the System
        // has bound must be evaluated first. Players without the System walk into gates freely: that is how they earn
        // it. A gate that has broken has spilled its cave out here: its boss is the way to close it now.
        String refusal = g.isTrial() && !com.beyondsolo.awakening.AwakeningService.mayEnter(player, g) ? "message.beyondsolo.gate.sealed_other"
                : g.breakState().broken() ? "message.beyondsolo.gate.broken"
                : SystemAPI.hasSystem(player) && !SystemAPI.isAwakened(player) ? "message.beyondsolo.gate.unevaluated" : null;
        if (refusal != null) {
            if (COOLDOWN.getOrDefault(player.getUUID(), 0L) <= level.getGameTime()) {
                player.displayClientMessage(Component.translatable(refusal), true);
                COOLDOWN.put(player.getUUID(), level.getGameTime() + 60);
            }
            Vec3 away = player.position().subtract(gate.center()).multiply(1, 0, 1).normalize().scale(0.6);
            player.push(away.x, 0.25, away.z);
            player.hurtMarked = true;
            return;
        }
        // a red gate in waiting turns red for an awakened hunter; one that has turned lets its party follow, then seals
        if (com.beyondsolo.redgate.RedGates.enter(player, gate, g)) return;
        if (level.getServer().getLevel(DIMENSION) == null) {
            BeyondSolo.LOGGER.error("The gate dimension {} is missing; is the mod's datapack loaded?", DIMENSION.location());
            return;
        }
        // the first gate of rank D or higher that a player without the System walks into hides the sealed door
        com.beyondsolo.awakening.AwakeningService.enteringGate(player, g);
        startBuilding(level.getServer(), g);
        // a keyed gate's door shuts a little after the first one through (see key.KeyedRuns)
        if (g.isKeyed() && !g.keyed().entered()) {
            g.keyed().firstEntryAt = level.getGameTime();
            GateData.get(level.getServer()).setDirty();
        }
        TRANSITS.put(player.getUUID(), new Transit(g.id(), false, level.getGameTime()));
        freeze(player, true);
        level.playSound(null, gate.getBlockPos(), SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.35f, 1.4f);
        level.playSound(null, gate.getBlockPos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2f, 0.6f);
        PacketDistributor.sendToPlayer(player, new GatePayload(GatePayload.ENTER, g.theme().ordinal(), -1, 0,
                g.dungeonName(), gateLabel(g)));
    }

    /** "D-Rank Gate", or "Sealed Gate" for the trial. */
    public static Component gateLabel(GateInstance g) {
        return g.isTrial() ? Component.translatable("gate.beyondsolo.sealed_gate") : Component.translatable("gate.beyondsolo.rank_gate", g.rank().displayName());
    }

    /** "D-Rank Gate · Labyrinth": the gate and the shape of what lies behind it (the trial just its seal). */
    public static Component arrivalLabel(GateInstance g) {
        return g.isTrial() ? gateLabel(g) : Component.translatable("gate.beyondsolo.rank_gate_shape", gateLabel(g), g.shape().displayName());
    }

    /** Tells a player's client which realm they are in (its fog and the motes in its air), or none (-1). */
    public static void sendRealm(ServerPlayer player, @Nullable GateInstance g) {
        PacketDistributor.sendToPlayer(player, new GatePayload(GatePayload.REALM, g == null ? -1 : g.realm().ordinal(), -1,
                g == null ? 0 : g.shape().ordinal(), Component.empty(), Component.empty()));
    }

    public static void beginExit(ServerPlayer player, GateBlockEntity gate) {
        // a red gate's way out lets its hunters go only once its hold is broken
        if (com.beyondsolo.redgate.RedGates.exit(player, gate)) return;
        if (busy(player)) return;
        GateInstance g = GateData.get(player.server).bySlotPos(player.blockPosition());
        if (g == null) return;
        // a player whose sealed door has opened in this cave is not let out: the door is the only way on
        if (com.beyondsolo.awakening.AwakeningService.heldBack(player)) {
            if (COOLDOWN.getOrDefault(player.getUUID(), 0L) <= player.level().getGameTime()) {
                player.displayClientMessage(Component.translatable("message.beyondsolo.gate.held_back"), true);
                player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2f, 0.5f);
                COOLDOWN.put(player.getUUID(), player.level().getGameTime() + 60);
            }
            Vec3 away = player.position().subtract(gate.center()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, gate.facing().toYRot()) : away.normalize();
            player.push(away.x * 0.6, 0.25, away.z * 0.6);
            player.hurtMarked = true;
            return;
        }
        TRANSITS.put(player.getUUID(), new Transit(g.id(), true, player.level().getGameTime()));
        freeze(player, true);
        player.level().playSound(null, gate.getBlockPos(), SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.3f, 1.8f);
        PacketDistributor.sendToPlayer(player, new GatePayload(GatePayload.EXIT, g.theme().ordinal(), -1, 0,
                Component.translatable("gate.beyondsolo.leaving"), g.dungeonName()));
    }

    private static boolean busy(ServerPlayer player) {
        if (TRANSITS.containsKey(player.getUUID())) return true;
        return COOLDOWN.getOrDefault(player.getUUID(), 0L) > player.level().getGameTime();
    }

    public static boolean inTransit(Entity player) {
        return TRANSITS.containsKey(player.getUUID());
    }

    /** True while someone is on their way into or out of this gate's cave. */
    public static boolean inTransitTo(UUID gate) {
        for (Transit t : TRANSITS.values()) if (t.gate().equals(gate)) return true;
        return false;
    }

    /** True while this gate's cave is still being carved. */
    public static boolean isBuilding(UUID gate) {
        return BUILDERS.containsKey(gate);
    }

    private static void freeze(ServerPlayer player, boolean on) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(FREEZE);
        if (on) speed.addTransientModifier(new AttributeModifier(FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Takes a player into a gate's cave right away (the end of the enter moment; tests and commands use it directly). */
    public static boolean arrive(ServerPlayer player, GateInstance g) {
        ServerLevel dungeon = dungeon(player.server);
        if (dungeon == null) return false;
        if (!g.built()) {
            com.beyondsolo.awakening.AwakeningService.enteringGate(player, g);
            DungeonBuilder b = BUILDERS.computeIfAbsent(g.id(), k -> builder(player.server, g));
            b.finish(dungeon);
            finishBuilding(dungeon, g);
        }
        BlockPos at = g.entrance();
        float yaw = arrivalYaw(g);
        player.teleportTo(dungeon, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        // fake players (tests) ignore same-level teleports
        if (player.distanceToSqr(Vec3.atBottomCenterOf(at)) > 4) player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        player.setDeltaMovement(Vec3.ZERO);
        COOLDOWN.put(player.getUUID(), dungeon.getGameTime() + 40);
        dungeon.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.6f);
        sendRealm(player, g);
        PacketDistributor.sendToPlayer(player, new GatePayload(GatePayload.ARRIVE, g.theme().ordinal(), -1, 0,
                g.dungeonName(), arrivalLabel(g)));
        player.getPersistentData().putUUID(IN_GATE_TAG, g.id());
        if (g.isKeyed()) com.beyondsolo.key.KeyedRuns.arrived(player, g);
        if (g.isTrial()) com.beyondsolo.awakening.AwakeningService.arrived(player, g);
        else giveQuest(player, g);
        com.beyondsolo.party.Parties.wentIn(player, g);
        return true;
    }

    /**
     * Which way a player arriving faces: toward the way on, where the first passage leaves the entrance (a tower's
     * stairs, a bridge, a corridor's door), or else toward the next room.
     */
    static float arrivalYaw(GateInstance g) {
        DungeonLayout layout = g.layout();
        DungeonLayout.Room first = layout.entrance();
        Vec3 look = null;
        for (DungeonLayout.Tunnel t : layout.tunnels()) {
            if (t.from() != 0 && t.to() != 0) continue;
            for (int i = 0; i <= 48 && look == null; i++) {
                double[] p = DungeonLayout.along(t, t.from() == 0 ? i / 48.0 : 1 - i / 48.0);
                if (!first.contains(p[0], first.floorY() + 1, p[2], 0.85)) look = new Vec3(p[0], p[1], p[2]);
            }
            if (look != null) break;
        }
        if (look == null && layout.rooms().size() > 1) look = Vec3.atCenterOf(layout.rooms().get(1).center());
        if (look == null) return 0f;
        double dx = look.x - (first.center().getX() + 0.5), dz = look.z - (first.center().getZ() + 0.5);
        if (dx * dx + dz * dz < 1) return 0f;
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
    }

    /**
     * Takes a player back out: to where the gate stands, or, from a keyed run, to where its key was turned (the
     * first key of a chain).
     */
    public static void leave(ServerPlayer player, GateInstance g) {
        player.getPersistentData().remove(IN_GATE_TAG);
        if (g.isKeyed()) com.beyondsolo.key.KeyedRuns.left(player, g);
        place(player, home(player.server, g, player.getYRot()));
        COOLDOWN.put(player.getUUID(), player.level().getGameTime() + 80);
        sendRealm(player, null);
        PacketDistributor.sendToPlayer(player, new GatePayload(GatePayload.RETURN, g.theme().ordinal(), -1, 0,
                Component.translatable(g.bossDefeated() ? "gate.beyondsolo.cleared" : "gate.beyondsolo.returned"), g.dungeonName()));
        com.beyondsolo.awakening.AwakeningService.leftGate(player, g);
    }

    /** Where someone leaving a cave comes out. */
    public record Home(ResourceKey<Level> dimension, Vec3 pos, float yaw) {}

    /** Puts a player down at a home: on the ground there if the ground is close above or below. */
    public static void place(ServerPlayer player, Home home) {
        ServerLevel level = player.server.getLevel(home.dimension());
        if (level == null) level = player.server.overworld();
        Vec3 to = home.pos();
        float yaw = home.yaw();
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(to));
        double y = Math.abs(ground.getY() - to.y) < 4 ? ground.getY() : to.y;
        player.teleportTo(level, to.x, y, to.z, yaw, 0f);
        // fake players (tests) ignore same-level teleports
        if (player.distanceToSqr(to.x, y, to.z) > 4) player.moveTo(to.x, y, to.z, yaw, 0f);
        player.setDeltaMovement(Vec3.ZERO);
    }

    /** In front of the gate in the world, or for a keyed run, where its key (the chain's first) was turned. */
    public static Home home(MinecraftServer server, GateInstance g, float yaw) {
        if (g.isKeyed()) {
            GateInstance.Keyed k = g.keyed();
            return new Home(k.returnDim, k.returnPos, k.returnYaw);
        }
        ServerLevel level = server.getLevel(g.gateDimension());
        if (level == null) level = server.overworld();
        if (level.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate) return new Home(level.dimension(), gate.front(), gate.facing().toYRot());
        return new Home(level.dimension(), Vec3.atBottomCenterOf(g.gatePos().relative(Direction.NORTH, 2)), yaw);
    }

    // ------------------------------------------------------------------ building caves

    private static DungeonBuilder builder(MinecraftServer server, GateInstance g) {
        DungeonBuilder b = new DungeonBuilder(g);
        return server.getLevel(DIMENSION) == null ? b.walledIn() : b;
    }

    public static void startBuilding(MinecraftServer server, GateInstance g) {
        if (g.built() || BUILDERS.containsKey(g.id())) return;
        BUILDERS.put(g.id(), builder(server, g));
    }

    /** Builds a gate's whole cave now (tests, commands). */
    public static void buildNow(MinecraftServer server, GateInstance g) {
        ServerLevel dungeon = dungeon(server);
        if (dungeon == null || g.built()) return;
        BUILDERS.computeIfAbsent(g.id(), k -> builder(server, g)).finish(dungeon);
        finishBuilding(dungeon, g);
    }

    private static void finishBuilding(ServerLevel dungeon, GateInstance g) {
        BUILDERS.remove(g.id());
        if (g.built()) return;
        g.setBuilt(true);
        if (!g.bossSpawned()) spawnBoss(dungeon, g);
        GateData.get(dungeon.getServer()).setDirty();
    }

    private static void spawnBoss(ServerLevel dungeon, GateInstance g) {
        if (g.isTrial()) {
            com.beyondsolo.awakening.AwakeningService.spawnArbiter(dungeon, g);
            return;
        }
        GateBossEntity boss = ModEntities.GATE_BOSS.get().create(dungeon);
        if (boss == null) return;
        BlockPos c = g.bossRoomCenter();
        Vec3 door = doorward(g, Vec3.atBottomCenterOf(c));
        float yaw = (float) (Mth.atan2(door.z, door.x) * Mth.RAD_TO_DEG) - 90f;
        boss.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, yaw, 0f);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
        boss.configure(g);
        dungeon.addFreshEntity(boss);
        g.setBoss(boss.getUUID());
        g.setBossSpawned(true);
    }

    // ------------------------------------------------------------------ inside a cave

    /** Every half second, for each player in the gate dimension: wake the packs and the boss as they come near. */
    static void watch(ServerPlayer player) {
        ServerLevel dungeon = player.serverLevel();
        GateData data = GateData.get(player.server);
        GateInstance g = data.bySlotPos(player.blockPosition());
        // a cave whose gate has broken has spilled out into the world: nobody is left standing in it
        if (g == null || g.state() == GateInstance.State.CLOSED || g.breakState().hasBroken()) {
            // the job change trial also takes place in this dimension, in its own halls
            if (!inTransit(player) && !com.beyondsolo.job.JobTrial.inTrial(player)) sendHome(player);
            return;
        }
        if (!g.built()) return;
        BlockPos at = player.blockPosition();
        for (DungeonLayout.Room room : g.layout().rooms()) {
            BlockPos c = g.world(room.center());
            double dx = at.getX() - c.getX(), dz = at.getZ() - c.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            // only from the room's own height: towers and shafts stack rooms over each other
            if (at.getY() < room.floorY() - 6 || at.getY() > room.topY() + 4) continue;
            if (g.isTrial() && room.kind() != DungeonLayout.Kind.SIDE && room.index() > g.checkpoint() && dist < room.reach()) {
                g.setCheckpoint(room.index());
                data.setDirty();
            }
            if (room.kind() == DungeonLayout.Kind.BOSS) {
                if (!g.bossAwake() && !g.bossDefeated() && dist < room.reach() - (g.isTrial() ? 8 : 4)) wakeBoss(dungeon, g);
            } else if (room.kind().hasPack() && !g.roomTriggered(room.index()) && dist < room.reach() + 7
                    // the awakening trial holds nothing but its Arbiter
                    && !g.isTrial()) {
                g.triggerRoom(room.index());
                data.setDirty();
                spawnPack(dungeon, g, room);
            }
        }
    }

    private static void spawnPack(ServerLevel dungeon, GateInstance g, DungeonLayout.Room room) {
        GateTheme theme = g.theme();
        RandomSource r = dungeon.random;
        BlockPos c = g.world(room.center());
        int floor = room.floorY();
        int count = theme.packSize + r.nextInt(2) + (room.kind() == DungeonLayout.Kind.SIDE ? 1 : 0) + (room.reach() >= 12 ? 1 : 0);
        com.beyondsolo.enemy.EnemyRoster.Mix mix = g.isTrial() ? null : g.mix();
        // later caverns are more likely to be led by an elite or a champion
        boolean late = room.index() > g.layout().rooms().size() / 2;
        for (int i = 0; i < count; i++) {
            BlockPos p = packSpot(dungeon, c, floor, room, r);
            if (mix == null) {
                spawnMonster(dungeon, g, com.beyondsolo.enemy.EnemyRoster.trial().pick(r), p);
                continue;
            }
            EntityType<?> type = mix.pick(r);
            belongs(com.beyondsolo.enemy.Enemies.spawn(dungeon, g.rank(), type, p,
                    com.beyondsolo.enemy.Enemies.roll(g.rank(), mix, type, r, i == 0, late), theme.ordinal()), g);
        }
        dungeon.playSound(null, c, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.6f, 1.4f);
    }

    /**
     * Somewhere in a room for one of its pack to rise: on the floor, with room to stand, not inside whatever the room
     * is dressed with (pillars, stalagmites, coffins). The middle of the floor if nowhere else will do.
     */
    private static BlockPos packSpot(ServerLevel level, BlockPos c, int floor, DungeonLayout.Room room, RandomSource r) {
        for (int attempt = 0; attempt < 10; attempt++) {
            float a = r.nextFloat() * Mth.TWO_PI;
            float d = 0.15f + r.nextFloat() * 0.5f;
            int x = c.getX() + Math.round(Mth.cos(a) * d * room.rx()), z = c.getZ() + Math.round(Mth.sin(a) * d * room.rz());
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos p = new BlockPos(x, floor + dy, z);
                if (roomy(level, p)) return p;
            }
        }
        for (int dy = 0; dy < 6; dy++) {
            BlockPos p = new BlockPos(c.getX(), floor + dy, c.getZ());
            if (roomy(level, p)) return p;
        }
        return new BlockPos(c.getX(), floor, c.getZ());
    }

    /** Two blocks of open air over firm ground (no liquid). */
    private static boolean roomy(ServerLevel level, BlockPos p) {
        BlockState below = level.getBlockState(p.below());
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p).getFluidState().isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && below.getFluidState().isEmpty() && below.isFaceSturdy(level, p.below(), Direction.UP);
    }

    /**
     * One of a gate's monsters, a plain one (no affinity, no variant): tougher by the gate's rank, carrying its
     * element, rising out of the ground. Packs use {@link com.beyondsolo.enemy.Enemies#spawn} with the gate's mix.
     */
    @Nullable
    public static Mob spawnMonster(ServerLevel level, GateInstance g, EntityType<?> type, BlockPos p) {
        return belongs(com.beyondsolo.enemy.Enemies.spawn(level, g.rank(), type, p, com.beyondsolo.enemy.EnemyTraits.NONE, g.theme().ordinal()), g);
    }

    /**
     * Marks a monster as one of this gate's (it is turned away if its cave is gone when it is loaded again) and gives
     * its hits the gate's element. Everything a gate spawns goes through here.
     */
    @Nullable
    public static Mob claim(@Nullable Mob mob, GateInstance g) {
        return belongs(mob, g);
    }

    @Nullable
    private static Mob belongs(@Nullable Mob mob, GateInstance g) {
        if (mob != null) {
            mob.getPersistentData().putUUID(GateWiper.GATE_ID_TAG, g.id());
            mob.getPersistentData().putString(ELEMENT_TAG, g.element().id().toString());
        }
        return mob;
    }

    /** The element a gate creature's hits carry: its realm's if it was told one, else its rank's. */
    public static com.beyondsolo.combat.DamageElement elementOf(Entity mob) {
        CompoundTag data = mob.getPersistentData();
        if (data.contains(ELEMENT_TAG)) {
            ResourceLocation id = ResourceLocation.tryParse(data.getString(ELEMENT_TAG));
            com.beyondsolo.combat.DamageElement e = id == null ? null : com.beyondsolo.combat.DamageElement.get(id);
            if (e != null) return e;
        }
        return GateTheme.byIndex(data.getInt(MOB_TAG)).element;
    }

    private static void wakeBoss(ServerLevel dungeon, GateInstance g) {
        if (g.boss() == null) return;
        Entity e = dungeon.getEntity(g.boss());
        if (g.isTrial() && e instanceof com.beyondsolo.awakening.ArbiterEntity arbiter) {
            g.setBossAwake(true);
            GateData.get(dungeon.getServer()).setDirty();
            com.beyondsolo.awakening.AwakeningService.wakeArbiter(dungeon, g, arbiter);
            return;
        }
        if (!(e instanceof GateBossEntity boss)) return;
        g.setBossAwake(true);
        GateData.get(dungeon.getServer()).setDirty();
        boss.wake();
        for (ServerPlayer p : playersIn(dungeon, g)) {
            PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.BOSS_INTRO, g.theme().ordinal(), boss.getId(), 0,
                    g.bossName(), Component.translatable("gate.beyondsolo.boss_subtitle", g.rank().displayName())));
        }
    }

    /** Wakes a gate's boss now (commands). */
    public static void wakeBossNow(ServerLevel dungeon, GateInstance g) {
        if (!g.bossAwake()) wakeBoss(dungeon, g);
    }

    static void bossEnraged(ServerLevel level, GateBossEntity boss) {
        GateInstance g = boss.gateId() == null ? null : GateData.get(level.getServer()).get(boss.gateId());
        if (g == null) return;
        com.beyondsolo.enemy.EnemyRoster.Mix mix = g.mix();
        for (int i = 0; i < 2; i++) {
            float a = level.random.nextFloat() * Mth.TWO_PI;
            BlockPos p = BlockPos.containing(boss.getX() + Mth.cos(a) * (boss.getBbWidth() / 2 + 3), boss.getY(), boss.getZ() + Mth.sin(a) * (boss.getBbWidth() / 2 + 3));
            for (int dy = 0; dy < 4 && !level.getBlockState(p).isAir(); dy++) p = p.above();
            EntityType<?> type = mix.pick(level.random);
            Mob add = belongs(com.beyondsolo.enemy.Enemies.spawn(level, g.rank(), type, p, com.beyondsolo.enemy.Enemies.roll(g.rank(), mix, type, level.random, false, true), g.theme().ordinal()), g);
            com.beyondsolo.breaks.DungeonBreaks.adopt(boss, add);
            if (add != null && boss.getTarget() != null) add.setTarget(boss.getTarget());
        }
        for (ServerPlayer p : playersIn(level, g)) {
            PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.ENRAGED, g.rank().ordinal(), boss.getId(), 0, g.bossName(), Component.empty()));
        }
    }

    /** Shakes the camera of players near {@code at}, weaker further away. */
    public static void shake(ServerLevel level, Entity at, float strength, double radius) {
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, at.getBoundingBox().inflate(radius))) {
            float k = (float) (1 - p.distanceTo(at) / radius);
            if (k > 0.05f) PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.SHAKE, 0, -1, strength * k, Component.empty(), Component.empty()));
        }
    }

    public static List<ServerPlayer> playersIn(ServerLevel dungeon, GateInstance g) {
        List<ServerPlayer> out = new ArrayList<>();
        if (!isDungeon(dungeon)) return out;
        for (ServerPlayer p : dungeon.players()) {
            GateInstance at = GateData.get(dungeon.getServer()).bySlotPos(p.blockPosition());
            if (at != null && at.id().equals(g.id())) out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------ the boss falls

    static void bossDefeated(ServerLevel level, GateBossEntity boss, DamageSource source) {
        GateData data = GateData.get(level.getServer());
        GateInstance g = boss.gateId() == null ? null : data.get(boss.gateId());
        if (g == null || g.bossDefeated()) return;
        g.setBossDefeated(true);
        g.setState(GateInstance.State.CLEARED);
        g.setClearedAt(level.getGameTime());
        data.setDirty();

        List<ServerPlayer> players = playersIn(level, g);
        com.beyondsolo.awakening.AwakeningService.gateCleared(level, g, players);
        if (g.isKeyed()) com.beyondsolo.key.KeyedRuns.cleared(level, g, players);
        for (ServerPlayer p : players) {
            PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.BOSS_DEFEATED, g.rank().ordinal(), boss.getId(), 0,
                    g.bossName(), Component.translatable("gate.beyondsolo.boss_defeated")));
            // the boss is the clear: whatever monsters were left behind no longer count against it
            QuestEngine.progress(p, MOB_KEY, Short.MAX_VALUE);
            QuestEngine.progress(p, BOSS_KEY, 1);
        }
        BlockPos center = boss.blockPosition();
        Vec3 toDoor = doorward(g, boss.position());
        Direction facing = Direction.getNearest(toDoor.x, 0, toDoor.z);

        // the reward cache comes down once the death animation is over, then the way out opens
        com.beyondsolo.util.ServerScheduler.schedule(GateBossEntity.DEATH_TICKS - 6, () -> {
            BlockPos cachePos = ground(level, center.relative(facing, 3));
            level.setBlock(cachePos, ModBlocks.REWARD_CACHE.get().defaultBlockState().setValue(RewardCacheBlock.FACING, facing), 3);
            if (level.getBlockEntity(cachePos) instanceof RewardCacheBlockEntity cache) cache.setup(g.rank(), g.bossKind(), g.bossAscended(), level.getGameTime());
            // a chain of keyed runs pays a little more for each run in it, and so does a cave with weather
            float bonus = com.beyondsolo.key.KeyedRuns.bonus(g) * com.beyondsolo.depths.WeatherService.rewardBonus(g);
            long xp = Math.round(g.theme().clearXp() * (double) bonus);
            for (ServerPlayer p : playersIn(level, g)) {
                SystemAPI.grantXp(p, xp, BeyondSolo.id("gate_clear"));
                QuestEngine.progress(p, CLEAR_KEY, 1);
                com.beyondsolo.economy.EconomyHooks.gateCleared(p, g.rank(), bonus);
                // the cleared banner is the System talking; without it there's just the quiet
                if (!SystemAPI.hasSystem(p)) continue;
                PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.CLEARED, g.theme().ordinal(), -1, xp,
                        g.dungeonName(), gateLabel(g)));
                // the run's time under the banner, and a key to turn for the next one
                com.beyondsolo.key.KeyedRuns.afterClear(p, g);
            }
        });
        com.beyondsolo.util.ServerScheduler.schedule(GateBossEntity.DEATH_TICKS + 50, () -> openExit(level, g, center, facing));
    }

    /** Opens the way out behind where the boss fell. */
    public static void openExit(ServerLevel level, GateInstance g, BlockPos bossPos, Direction facing) {
        BlockPos pos = ground(level, bossPos.relative(facing.getOpposite(), 4));
        level.setBlock(pos, ModBlocks.GATE.get().defaultBlockState().setValue(GateBlock.FACING, facing), 3);
        if (level.getBlockEntity(pos) instanceof GateBlockEntity exit) exit.setup(g.id(), g.rank(), true, level.getGameTime());
        // clear head room for the portal
        for (int y = 1; y <= 5; y++) {
            for (int s = -2; s <= 2; s++) {
                BlockPos p = pos.above(y).relative(facing.getClockWise(), s);
                if (!level.getBlockState(p).isAir()) level.setBlock(p, net.minecraft.world.level.block.Blocks.CAVE_AIR.defaultBlockState(), 3);
            }
        }
        g.setExitPos(pos);
        GateData.get(level.getServer()).setDirty();
        level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.5f, 1.8f);
        for (ServerPlayer p : playersIn(level, g)) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.gate.exit.title"), Component.translatable("notify.beyondsolo.gate.exit.body")));
        }
    }

    /**
     * Which way the way into the boss's room lies from a point in it (flat, unit length): toward the door of the
     * passage that leads in, or toward the entrance if that is no help (straight above or below, in a tower).
     */
    public static Vec3 doorward(GateInstance g, Vec3 from) {
        DungeonLayout.Tunnel in = g.layout().bossTunnel();
        DungeonLayout.Room boss = g.layout().boss();
        Vec3 to = null;
        if (in != null) {
            BlockPos door = in.end();
            if (Math.abs(door.getX() - boss.center().getX()) + Math.abs(door.getZ() - boss.center().getZ()) < 3) door = in.start();
            to = Vec3.atCenterOf(g.world(door)).subtract(from).multiply(1, 0, 1);
        }
        if (to == null || to.lengthSqr() < 4) to = Vec3.atCenterOf(g.world(g.layout().entrance().center())).subtract(from).multiply(1, 0, 1);
        if (to.lengthSqr() < 1e-4) to = new Vec3(0, 0, 1);
        return to.normalize();
    }

    private static BlockPos ground(ServerLevel level, BlockPos p) {
        BlockPos q = p;
        for (int i = 0; i < 6 && !level.getBlockState(q).isAir(); i++) q = q.above();
        for (int i = 0; i < 8 && level.getBlockState(q.below()).isAir(); i++) q = q.below();
        return q;
    }

    // ------------------------------------------------------------------ quests

    private static void giveQuest(ServerPlayer player, GateInstance g) {
        if (!SystemAPI.isAwakened(player)) return;
        for (Quest q : QuestEngine.data(player).active()) {
            if (QUEST_TEMPLATE.equals(q.template()) && g.id().toString().equals(q.extra().getString("gate"))) return;
        }
        if (g.bossDefeated()) return;
        GateTheme theme = g.theme();
        int mobs = Math.min(4 + 2 * theme.rank.tier(), (theme.rooms - 1) * theme.packSize);
        CompoundTag extra = new CompoundTag();
        extra.putString("gate", g.id().toString());
        Quest q = QuestBuilder.of(QUEST_TEMPLATE, QuestCategory.GATE, g.rank().tier(), SystemAPI.get(player).level())
                .title(Component.translatable("quest.beyondsolo.gate_clear", g.rank().displayName()))
                .description(Component.translatable("quest.beyondsolo.gate_clear.desc", g.dungeonName(), g.bossName()))
                .custom(MOB_KEY, mobs, Component.translatable("quest_objective.beyondsolo.gate_mobs"))
                .custom(BOSS_KEY, 1, Component.translatable("quest_objective.beyondsolo.gate_boss", g.bossName()))
                // a keyed run can be bought and crafted, so it pays its XP but no stat points
                .fixed().xp(150L * (1 + theme.rank.tier())).statPoints(g.isKeyed() ? 0 : theme.rank.tier() >= 3 ? 2 : 1)
                .extra(extra)
                .build();
        QuestEngine.start(player, q);
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel dungeon = dungeon(server);
        if (dungeon != null && !BUILDERS.isEmpty()) {
            for (DungeonBuilder b : List.copyOf(BUILDERS.values())) {
                if (b.tick(dungeon)) finishBuilding(dungeon, b.gate());
            }
        }

        GateData data = GateData.get(server);
        Iterator<Map.Entry<UUID, Transit>> it = TRANSITS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Transit> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Transit t = e.getValue();
            GateInstance g = data.get(t.gate());
            if (p == null || g == null || !p.isAlive()) {
                if (p != null) freeze(p, false);
                it.remove();
                continue;
            }
            p.setDeltaMovement(p.getDeltaMovement().multiply(0, 1, 0));
            long elapsed = p.level().getGameTime() - t.started();
            if (!t.exit && elapsed >= ENTER_TICKS && (g.built() || elapsed > 20 * 12)) {
                it.remove();
                freeze(p, false);
                arrive(p, g);
            } else if (t.exit && elapsed >= EXIT_TICKS) {
                it.remove();
                freeze(p, false);
                leave(p, g);
            }
        }

        if (server.getTickCount() % 10 == 0 && dungeon != null) {
            for (ServerPlayer p : List.copyOf(dungeon.players())) watch(p);
        }
        if (server.getTickCount() % 40 == 0 && dungeon != null) closeEmptyClearedGates(server, dungeon, data);
        if (dungeon != null) GateWiper.tick(server, dungeon, data);
        if (server.getTickCount() % 1200 == 600) GateSpawner.tick(server);
    }

    /** A cleared cave with nobody left in it is closed for good, and the gate in the world closes. */
    private static void closeEmptyClearedGates(MinecraftServer server, ServerLevel dungeon, GateData data) {
        for (GateInstance g : data.all()) {
            if (g.state() != GateInstance.State.CLEARED) continue;
            if (dungeon.getGameTime() - g.clearedAt() < 20 * 15) continue;
            if (!playersIn(dungeon, g).isEmpty()) continue;
            boolean inTransit = false;
            for (Transit t : TRANSITS.values()) if (t.gate().equals(g.id())) inTransit = true;
            if (inTransit) continue;
            g.setState(GateInstance.State.CLOSED);
            if (g.isKeyed()) g.keyed().inside.clear();
            data.setDirty();
            ServerLevel world = server.getLevel(g.gateDimension());
            if (world != null && world.isLoaded(g.gatePos()) && world.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate) {
                stillOpen(world, gate);
            }
        }
    }

    /**
     * Someone left in a cave that is closed (or no longer theirs) goes home: from a keyed run, back to where its key
     * was turned; otherwise to their respawn point.
     */
    private static void sendHome(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();
        GateInstance was = tag.hasUUID(IN_GATE_TAG) ? GateData.get(player.server).get(tag.getUUID(IN_GATE_TAG)) : null;
        tag.remove(IN_GATE_TAG);
        if (was != null && was.isKeyed()) {
            leave(player, was);
            return;
        }
        ServerLevel world = player.server.overworld();
        BlockPos spawn = player.getRespawnPosition() != null && player.getRespawnDimension().equals(Level.OVERWORLD)
                ? player.getRespawnPosition() : world.getSharedSpawnPos();
        BlockPos top = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
        player.teleportTo(world, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, player.getYRot(), 0f);
    }

    /** Nobody gets hurt halfway through a gate. */
    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && inTransit(p)) event.setCanceled(true);
    }

    /** Gate monsters hit with the gate's element. */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        Entity src = event.getSource().getEntity();
        if (!(src instanceof LivingEntity attacker) || src instanceof GateBossEntity) return;
        if (!attacker.getPersistentData().contains(MOB_TAG) || event.getNewDamage() <= 0) return;
        // a status's own tick or burst, or a storm spark, is not a fresh hit: no buildup off it
        if (com.beyondsolo.combat.ElementalHits.isProc(event.getSource()) || com.beyondsolo.enemy.Affinity.inSpark()) return;
        GateTheme theme = GateTheme.byIndex(attacker.getPersistentData().getInt(MOB_TAG));
        Statuses.apply(event.getEntity(), elementOf(attacker), theme.mobBuildup(), attacker);
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (victim instanceof ServerPlayer player && isDungeon(player.level())) {
            GateInstance g = GateData.get(player.server).bySlotPos(player.blockPosition());
            if (g != null) {
                for (Quest q : List.copyOf(QuestEngine.data(player).active())) {
                    if (QUEST_TEMPLATE.equals(q.template()) && g.id().toString().equals(q.extra().getString("gate"))) {
                        QuestEngine.fail(player, q, QuestEvent.FailReason.DIED);
                    }
                }
                // a keyed run's door is shut behind the party: whoever falls is out of it
                if (g.isKeyed()) com.beyondsolo.key.KeyedRuns.left(player, g);
            }
            player.getPersistentData().remove(IN_GATE_TAG);
            return;
        }
        if (victim.getPersistentData().contains(MOB_TAG) && event.getSource().getEntity() instanceof ServerPlayer killer) {
            QuestEngine.progress(killer, MOB_KEY, 1);
            for (ServerPlayer mate : com.beyondsolo.party.Parties.sharers(killer, victim)) QuestEngine.progress(mate, MOB_KEY, 1);
        }
    }

    /**
     * Someone who logged out in a cave comes back to find it as it is now: still theirs (a keyed run picks its clock
     * back up), or closed, or wiped and handed to another gate, in which case they are taken home at once rather than
     * left standing in rock or in a stranger's cave.
     */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.level().dimension().equals(DIMENSION)) return;
        if (GateData.slotAt(p.blockPosition()) < 1) return;
        GateInstance g = GateData.get(p.server).bySlotPos(p.blockPosition());
        CompoundTag tag = p.getPersistentData();
        UUID was = tag.hasUUID(IN_GATE_TAG) ? tag.getUUID(IN_GATE_TAG) : null;
        if (g != null && g.state() != GateInstance.State.CLOSED && !g.breakState().hasBroken() && (was == null || was.equals(g.id()))) {
            if (g.isKeyed()) com.beyondsolo.key.KeyedRuns.rejoined(p, g);
            sendRealm(p, g);
            return;
        }
        sendHome(p);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        if (TRANSITS.remove(id) != null && event.getEntity() instanceof ServerPlayer p) freeze(p, false);
        COOLDOWN.remove(id);
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        TRANSITS.clear();
        COOLDOWN.clear();
        BUILDERS.clear();
        NOTICED.clear();
    }

    /** The gate nearest to the player within {@code range}, if any. */
    @Nullable
    public static GateBlockEntity nearestGate(ServerPlayer player, int range) {
        GateBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        ServerLevel level = player.serverLevel();
        for (GateInstance g : GateData.get(player.server).all()) {
            if (!g.gateDimension().equals(level.dimension())) continue;
            double d = g.gatePos().distSqr(player.blockPosition());
            if (d > range * range || d >= bestD) continue;
            if (level.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate) {
                best = gate;
                bestD = d;
            }
        }
        return best;
    }

    private GateService() {}
}
