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

    /** The rank of a gate made by world generation: deeper ranks further from the world's centre. */
    public static PlayerRank worldgenRank(BlockPos pos, RandomSource r) {
        double d = Math.sqrt((double) pos.getX() * pos.getX() + (double) pos.getZ() * pos.getZ());
        int base = (int) (d / 1400);
        int roll = r.nextInt(10);
        int tier = base + (roll < 3 ? -1 : roll < 8 ? 0 : 1) + (r.nextInt(25) == 0 ? 2 : 0);
        return PlayerRank.values()[Mth.clamp(tier, 0, PlayerRank.values().length - 1)];
    }

    /** False (and the gate starts closing) once the gate's cave has been cleared and left. */
    public static boolean stillOpen(ServerLevel level, GateBlockEntity gate) {
        if (gate.gateId() == null) return true;
        GateInstance g = GateData.get(level.getServer()).get(gate.gateId());
        if (g == null) return true;
        if (g.state() == GateInstance.State.CLOSED) {
            gate.startClosing(level.getGameTime());
            level.playSound(null, gate.getBlockPos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5f, 0.7f);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ noticing gates

    /** Players who come close to a gate for the first time are told about it (and warned if it is above them). */
    public static void noticeNearby(ServerLevel level, GateBlockEntity gate) {
        if (gate.gateId() == null) return;
        GateTheme theme = GateTheme.of(gate.rank());
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(gate.getBlockPos()).inflate(40))) {
            Set<UUID> seen = NOTICED.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
            if (!seen.add(gate.gateId())) continue;
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.gate.detected.title", gate.rank().displayName()),
                    Component.translatable("notify.beyondsolo.gate.detected.body", theme.dungeonName(), gate.rank().displayName())));
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
        if (!SystemAPI.isAwakened(player)) {
            if (COOLDOWN.getOrDefault(player.getUUID(), 0L) <= level.getGameTime()) {
                player.displayClientMessage(Component.translatable("message.beyondsolo.gate.unevaluated"), true);
                COOLDOWN.put(player.getUUID(), level.getGameTime() + 60);
            }
            Vec3 away = player.position().subtract(gate.center()).multiply(1, 0, 1).normalize().scale(0.6);
            player.push(away.x, 0.25, away.z);
            player.hurtMarked = true;
            return;
        }
        GateInstance g = GateData.get(level.getServer()).get(gate.gateId());
        if (g == null || g.state() != GateInstance.State.OPEN) return;
        if (level.getServer().getLevel(DIMENSION) == null) {
            BeyondSolo.LOGGER.error("The gate dimension {} is missing; is the mod's datapack loaded?", DIMENSION.location());
            return;
        }
        startBuilding(level.getServer(), g);
        TRANSITS.put(player.getUUID(), new Transit(g.id(), false, level.getGameTime()));
        freeze(player, true);
        level.playSound(null, gate.getBlockPos(), SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.35f, 1.4f);
        level.playSound(null, gate.getBlockPos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2f, 0.6f);
        PacketDistributor.sendToPlayer(player, new GatePayload(GatePayload.ENTER, g.rank().ordinal(), -1, 0,
                g.theme().dungeonName(), Component.translatable("gate.beyondsolo.rank_gate", g.rank().displayName())));
    }

    public static void beginExit(ServerPlayer player, GateBlockEntity gate) {
        if (busy(player)) return;
        GateInstance g = GateData.get(player.server).bySlotPos(player.blockPosition());
        if (g == null) return;
        TRANSITS.put(player.getUUID(), new Transit(g.id(), true, player.level().getGameTime()));
        freeze(player, true);
        player.level().playSound(null, gate.getBlockPos(), SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.3f, 1.8f);
        PacketDistributor.sendToPlayer(player, new GatePayload(GatePayload.EXIT, g.rank().ordinal(), -1, 0,
                Component.translatable("gate.beyondsolo.leaving"), g.theme().dungeonName()));
    }

    private static boolean busy(ServerPlayer player) {
        if (TRANSITS.containsKey(player.getUUID())) return true;
        return COOLDOWN.getOrDefault(player.getUUID(), 0L) > player.level().getGameTime();
    }

    public static boolean inTransit(Entity player) {
        return TRANSITS.containsKey(player.getUUID());
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
            DungeonBuilder b = BUILDERS.computeIfAbsent(g.id(), k -> builder(player.server, g));
            b.finish(dungeon);
            finishBuilding(dungeon, g);
        }
        BlockPos at = g.entrance();
        DungeonLayout.Room next = g.layout().rooms().size() > 1 ? g.layout().rooms().get(1) : g.layout().entrance();
        BlockPos look = g.world(next.center());
        float yaw = (float) (Mth.atan2(look.getZ() - at.getZ(), look.getX() - at.getX()) * Mth.RAD_TO_DEG) - 90f;
        player.teleportTo(dungeon, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        // fake players (tests) ignore same-level teleports
        if (player.distanceToSqr(Vec3.atBottomCenterOf(at)) > 4) player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        player.setDeltaMovement(Vec3.ZERO);
        COOLDOWN.put(player.getUUID(), dungeon.getGameTime() + 40);
        dungeon.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.6f);
        PacketDistributor.sendToPlayer(player, new GatePayload(GatePayload.ARRIVE, g.rank().ordinal(), -1, 0,
                g.theme().dungeonName(), Component.translatable("gate.beyondsolo.rank_gate", g.rank().displayName())));
        giveQuest(player, g);
        return true;
    }

    /** Takes a player back out to where the gate stands. */
    public static void leave(ServerPlayer player, GateInstance g) {
        ServerLevel level = player.server.getLevel(g.gateDimension());
        if (level == null) level = player.server.overworld();
        Vec3 to;
        float yaw = player.getYRot();
        if (level.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate) {
            to = gate.front();
            yaw = gate.facing().toYRot();
        } else {
            to = Vec3.atBottomCenterOf(g.gatePos().relative(Direction.NORTH, 2));
        }
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(to));
        double y = Math.abs(ground.getY() - to.y) < 4 ? ground.getY() : to.y;
        player.teleportTo(level, to.x, y, to.z, yaw, 0f);
        if (player.distanceToSqr(to.x, y, to.z) > 4) player.moveTo(to.x, y, to.z, yaw, 0f);
        player.setDeltaMovement(Vec3.ZERO);
        COOLDOWN.put(player.getUUID(), level.getGameTime() + 80);
        PacketDistributor.sendToPlayer(player, new GatePayload(GatePayload.RETURN, g.rank().ordinal(), -1, 0,
                Component.translatable(g.bossDefeated() ? "gate.beyondsolo.cleared" : "gate.beyondsolo.returned"), g.theme().dungeonName()));
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
        GateBossEntity boss = ModEntities.GATE_BOSS.get().create(dungeon);
        if (boss == null) return;
        BlockPos c = g.bossRoomCenter();
        BlockPos entrance = g.world(g.layout().rooms().get(g.layout().rooms().size() - 1).center());
        DungeonLayout.Tunnel in = null;
        for (DungeonLayout.Tunnel t : g.layout().tunnels()) if (g.layout().rooms().get(t.to()).kind() == DungeonLayout.Kind.BOSS) in = t;
        BlockPos from = in != null ? g.world(in.start()) : entrance;
        float yaw = (float) (Mth.atan2(from.getZ() - c.getZ(), from.getX() - c.getX()) * Mth.RAD_TO_DEG) - 90f;
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
    private static void watch(ServerPlayer player) {
        ServerLevel dungeon = player.serverLevel();
        GateData data = GateData.get(player.server);
        GateInstance g = data.bySlotPos(player.blockPosition());
        if (g == null || g.state() == GateInstance.State.CLOSED) {
            if (!inTransit(player)) sendHome(player);
            return;
        }
        if (!g.built()) return;
        BlockPos at = player.blockPosition();
        for (DungeonLayout.Room room : g.layout().rooms()) {
            BlockPos c = g.world(room.center());
            double dx = at.getX() - c.getX(), dz = at.getZ() - c.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(at.getY() - c.getY()) > room.ry() + 8) continue;
            if (room.kind() == DungeonLayout.Kind.BOSS) {
                if (!g.bossAwake() && !g.bossDefeated() && dist < room.reach() - 4) wakeBoss(dungeon, g);
            } else if (room.kind() != DungeonLayout.Kind.ENTRANCE && !g.roomTriggered(room.index()) && dist < room.reach() + 7) {
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
        int floor = c.getY() - (room.center().getY() - room.floorY());
        int count = theme.packSize + r.nextInt(2) + (room.kind() == DungeonLayout.Kind.SIDE ? 1 : 0);
        for (int i = 0; i < count; i++) {
            float a = r.nextFloat() * Mth.TWO_PI;
            float d = 0.2f + r.nextFloat() * 0.45f;
            BlockPos p = new BlockPos(c.getX() + Math.round(Mth.cos(a) * d * room.rx()), floor, c.getZ() + Math.round(Mth.sin(a) * d * room.rz()));
            for (int dy = 0; dy < 5 && !dungeon.getBlockState(p).isAir(); dy++) p = p.above();
            EntityType<?> type = theme.monsters.get(r.nextInt(theme.monsters.size()));
            spawnMonster(dungeon, g, type, p);
        }
        dungeon.playSound(null, c, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.6f, 1.4f);
    }

    /** One of a gate's monsters: tougher by the gate's rank, carrying its element, rising out of the ground. */
    @Nullable
    public static Mob spawnMonster(ServerLevel level, GateInstance g, EntityType<?> type, BlockPos p) {
        if (!(type.create(level) instanceof Mob mob)) return null;
        GateTheme theme = g.theme();
        mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        mob.getPersistentData().putInt(MOB_TAG, theme.rank.ordinal());
        var hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && theme.mobHealthBonus() > 0) {
            hp.addPermanentModifier(new AttributeModifier(BeyondSolo.id("gate_health"), theme.mobHealthBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            mob.setHealth(mob.getMaxHealth());
        }
        var dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null && theme.mobDamageBonus() > 0) {
            dmg.addPermanentModifier(new AttributeModifier(BeyondSolo.id("gate_damage"), theme.mobDamageBonus(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.3, mob.getZ(), 12, 0.3, 0.1, 0.3, 0.02);
        level.sendParticles(ParticleTypes.SCULK_SOUL, mob.getX(), mob.getY() + 0.5, mob.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
        return mob;
    }

    private static void wakeBoss(ServerLevel dungeon, GateInstance g) {
        if (g.boss() == null || !(dungeon.getEntity(g.boss()) instanceof GateBossEntity boss)) return;
        g.setBossAwake(true);
        GateData.get(dungeon.getServer()).setDirty();
        boss.wake();
        for (ServerPlayer p : playersIn(dungeon, g)) {
            PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.BOSS_INTRO, g.rank().ordinal(), boss.getId(), 0,
                    g.theme().bossName(), Component.translatable("gate.beyondsolo.boss_subtitle", g.rank().displayName())));
        }
    }

    /** Wakes a gate's boss now (commands). */
    public static void wakeBossNow(ServerLevel dungeon, GateInstance g) {
        if (!g.bossAwake()) wakeBoss(dungeon, g);
    }

    static void bossEnraged(ServerLevel level, GateBossEntity boss) {
        GateInstance g = boss.gateId() == null ? null : GateData.get(level.getServer()).get(boss.gateId());
        if (g == null) return;
        for (int i = 0; i < 2; i++) {
            float a = level.random.nextFloat() * Mth.TWO_PI;
            BlockPos p = BlockPos.containing(boss.getX() + Mth.cos(a) * 4, boss.getY(), boss.getZ() + Mth.sin(a) * 4);
            spawnMonster(level, g, g.theme().monsters.get(level.random.nextInt(g.theme().monsters.size())), p);
        }
        for (ServerPlayer p : playersIn(level, g)) {
            PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.ENRAGED, g.rank().ordinal(), boss.getId(), 0, g.theme().bossName(), Component.empty()));
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
        for (ServerPlayer p : players) {
            PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.BOSS_DEFEATED, g.rank().ordinal(), boss.getId(), 0,
                    g.theme().bossName(), Component.translatable("gate.beyondsolo.boss_defeated")));
            QuestEngine.progress(p, BOSS_KEY, 1);
        }
        BlockPos center = boss.blockPosition();
        Vec3 toEntrance = Vec3.atCenterOf(g.world(g.layout().entrance().center())).subtract(boss.position()).multiply(1, 0, 1).normalize();
        Direction facing = Direction.getNearest(toEntrance.x, 0, toEntrance.z);

        // the reward cache comes down once the death animation is over, then the way out opens
        com.beyondsolo.util.ServerScheduler.schedule(GateBossEntity.DEATH_TICKS - 6, () -> {
            BlockPos cachePos = ground(level, center.relative(facing, 3));
            level.setBlock(cachePos, ModBlocks.REWARD_CACHE.get().defaultBlockState().setValue(RewardCacheBlock.FACING, facing), 3);
            if (level.getBlockEntity(cachePos) instanceof RewardCacheBlockEntity cache) cache.setup(g.rank(), level.getGameTime());
            long xp = g.theme().clearXp();
            for (ServerPlayer p : playersIn(level, g)) {
                SystemAPI.grantXp(p, xp, BeyondSolo.id("gate_clear"));
                QuestEngine.progress(p, CLEAR_KEY, 1);
                PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.CLEARED, g.rank().ordinal(), -1, xp,
                        g.theme().dungeonName(), Component.translatable("gate.beyondsolo.rank_gate", g.rank().displayName())));
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

    private static BlockPos ground(ServerLevel level, BlockPos p) {
        BlockPos q = p;
        for (int i = 0; i < 6 && !level.getBlockState(q).isAir(); i++) q = q.above();
        for (int i = 0; i < 8 && level.getBlockState(q.below()).isAir(); i++) q = q.below();
        return q;
    }

    // ------------------------------------------------------------------ quests

    private static void giveQuest(ServerPlayer player, GateInstance g) {
        for (Quest q : QuestEngine.data(player).active()) {
            if (QUEST_TEMPLATE.equals(q.template()) && g.id().toString().equals(q.extra().getString("gate"))) return;
        }
        if (g.bossDefeated()) return;
        GateTheme theme = g.theme();
        int mobs = Math.min(4 + 2 * theme.rank.tier(), (theme.rooms - 1) * theme.packSize);
        CompoundTag extra = new CompoundTag();
        extra.putString("gate", g.id().toString());
        Quest q = QuestBuilder.of(QUEST_TEMPLATE, QuestCategory.GATE, player)
                .title(Component.translatable("quest.beyondsolo.gate_clear", g.rank().displayName()))
                .description(Component.translatable("quest.beyondsolo.gate_clear.desc", theme.dungeonName(), theme.bossName()))
                .custom(MOB_KEY, mobs, Component.translatable("quest_objective.beyondsolo.gate_mobs"))
                .custom(BOSS_KEY, 1, Component.translatable("quest_objective.beyondsolo.gate_boss", theme.bossName()))
                .fixed().xp(150L * (1 + theme.rank.tier())).statPoints(theme.rank.tier() >= 3 ? 2 : 1)
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
            data.setDirty();
            ServerLevel world = server.getLevel(g.gateDimension());
            if (world != null && world.isLoaded(g.gatePos()) && world.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate) {
                stillOpen(world, gate);
            }
        }
    }

    private static void sendHome(ServerPlayer player) {
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
        GateTheme theme = GateTheme.values()[Mth.clamp(attacker.getPersistentData().getInt(MOB_TAG), 0, 5)];
        Statuses.apply(event.getEntity(), theme.element, theme.mobBuildup(), attacker);
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
            }
            return;
        }
        if (victim.getPersistentData().contains(MOB_TAG) && event.getSource().getEntity() instanceof ServerPlayer killer) {
            QuestEngine.progress(killer, MOB_KEY, 1);
        }
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

    static boolean naturalSpawnsOn() {
        return Config.GATE_SPAWN_CHANCE.get() > 0;
    }

    private GateService() {}
}
