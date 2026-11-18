package com.beyondsolo.redgate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.breaks.BreakState;
import com.beyondsolo.breaks.DungeonBreaks;
import com.beyondsolo.economy.EconomyHooks;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBlock;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateLoot;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateTheme;
import com.beyondsolo.gate.RewardCacheBlock;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.network.GatePayload;
import com.beyondsolo.network.RedGatePayload;
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
import com.beyondsolo.util.ParticleGuard;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Red gates. Now and then a gate is a red gate in waiting ({@link #latent}): the first awakened hunter through it turns
 * it red. For a few seconds the rest of a party can still follow, then it seals, and nothing gets in or out until the
 * master of the land behind it falls (or, with {@link RedGateConfig#RELEASE_DAY} set, until the dawn of that day).
 *
 * <p>Behind it is not a cave but the Red Winter ({@link RedWorld}), and time runs faster there than outside
 * ({@link RedGateConfig#TIME_RATE}): days go by, its creatures (a rank or two above the gate's own) come out of the
 * snow at night, blizzards blow in, and the cold bites anyone out in the open ({@link #cold}). Its camps can be taken for
 * what they hoard; its master waits in a keep about a hundred blocks from where hunters arrive. Its fall opens the way
 * out and drops a red reward cache (twice the loot, often a key); everyone pays twice what a gate of its inside rank
 * pays. If everyone it took falls, the red fades from the gate in the world and it starts to come apart (see
 * {@code breaks.DungeonBreaks}).</p>
 *
 * <p>The client shows it all ({@code client.redgate}): the System's red warning, the inside clock, the cold, and the
 * way to its master.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class RedGates {
    /** Persistent data on the red gate's creatures (and its master): the id of the gate they belong to. */
    public static final String MOB_TAG = "beyondsolo_red_gate";
    public static final String BOSS_TAG = "beyondsolo_red_master";
    /** Persistent data on a hunter inside: the id of the gate. */
    public static final String IN_RED_TAG = "beyondsolo_in_red_gate";
    public static final String QUEST_TEMPLATE = BeyondSolo.id("redgate/master").toString();
    public static final String MASTER_KEY = BeyondSolo.id("red_gate_master").toString();
    public static final String CAMP_KEY = BeyondSolo.id("red_gate_camp").toString();
    public static final int RED = 0xC8102E;

    /** Chill: 0 is warm, this is frozen through (vanilla's freezing takes over from there). */
    public static final int MAX_CHILL = 100;
    /** From this chill on, vanilla's frost creeps in round the screen, and at {@link #MAX_CHILL} it bites. */
    static final int FROST_RIM = 70;
    /** How close hunters come to a camp, the keep, and its master before they stir. */
    static final int CAMP_REACH = 30;
    static final int KEEP_REACH = 46;
    static final int WAKE_REACH = 22;
    /** How close to the red gate in the world someone has to be to see how it stands. */
    static final int WATCH_REACH = 40;
    /** Real ticks before the first wave of a night, and the range of the waits between waves. */
    static final int FIRST_WAVE = 20 * 6;
    static final int WAVE_MIN = 20 * 16;
    static final int WAVE_SPREAD = 20 * 10;

    private static final ResourceLocation FREEZE = BeyondSolo.id("red_gate_transit");
    private static final ResourceLocation FURY_HEALTH = BeyondSolo.id("red_gate_fury_health");
    private static final ResourceLocation FURY_DAMAGE = BeyondSolo.id("red_gate_fury_damage");
    private static final ResourceLocation HUNT = BeyondSolo.id("red_gate_hunt");
    private static final DustParticleOptions RED_DUST = new DustParticleOptions(new Vector3f(0.8f, 0.05f, 0.1f), 2.5f);
    private static final DustParticleOptions BEACON_DUST = new DustParticleOptions(new Vector3f(0.9f, 0.06f, 0.12f), 3f);

    private record Transit(UUID gate, boolean exit, long started) {}

    private static final Map<UUID, Transit> TRANSITS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Map<UUID, RedWorldBuilder> BUILDERS = new HashMap<>();
    private static final Map<UUID, Integer> CHILL = new HashMap<>();
    private static final Set<UUID> WARM = new HashSet<>();
    private static final Set<UUID> FREEZING = new HashSet<>();
    private static final Map<UUID, Long> EDGE_WARNED = new HashMap<>();
    /** Players outside who were last shown a red gate near them (hidden again once they walk off). */
    private static final Set<UUID> WATCHING = new HashSet<>();
    /** Players by id, for those who are not in the player list (game-test players). */
    private static final Map<UUID, ServerPlayer> KNOWN = new HashMap<>();
    private static final Map<PlayerRank, EnemyRoster.Mix> MIXES = new EnumMap<>(PlayerRank.class);
    /** Set while this class moves a hunter between worlds itself; any other move out of a sealed red gate is refused. */
    private static boolean moving;

    // ================================================================ turning red

    /**
     * True if this gate turns red when an awakened hunter is the first through it: the same answer every time for the
     * same gate. Awakening trials and keyed gates never do; a gate someone without the System went through first never
     * will ({@link RedGateData#defuse}); a command can make any gate one ({@link RedGateData#force}).
     */
    public static boolean latent(MinecraftServer server, GateInstance g) {
        RedGateData data = RedGateData.get(server);
        if (data.forced(g.id())) return true;
        if (g.isTrial() || g.isKeyed() || data.defused(g.id())) return false;
        double chance = RedGateConfig.chance();
        return chance > 0 && RandomSource.create(g.seed() * 41L + 0x12ED_6A7EL).nextDouble() < chance;
    }

    /**
     * From {@link GateService#beginEnter}, once the gate has let the player by: true if a red gate takes it from here
     * (it turns red, the player is let into one that is still open, or turned away from one that has sealed).
     */
    public static boolean enter(ServerPlayer player, GateBlockEntity gate, GateInstance g) {
        MinecraftServer server = player.server;
        RedGateData data = RedGateData.get(server);
        RedGate run = data.get(g.id());
        if (run != null) {
            // its red winter is over (everyone in it fell): an ordinary gate again
            if (run.state == RedGate.State.ENDED) return false;
            if (run.state == RedGate.State.OPEN) join(player, run, gate);
            else refuse(player, gate, "message.beyondsolo.redgate.sealed_outside");
            return true;
        }
        if (!latent(server, g) || g.built() || GateService.isBuilding(g.id()) || GateService.inTransitTo(g.id())) return false;
        if (!SystemAPI.isAwakened(player)) {
            // someone without the System went through first: it will never turn red
            data.defuse(g.id());
            return false;
        }
        if (RedWorld.level(server) == null) {
            BeyondSolo.LOGGER.error("The red gate dimension {} is missing; is the mod's datapack loaded?", RedWorld.DIMENSION.location());
            return false;
        }
        run = start(server, gate, g, player.getUUID());
        join(player, run, gate);
        return true;
    }

    /**
     * The gate turns red: its run begins, the patch behind it starts being shaped, and everyone near sees it happen
     * (but {@code first}, the hunter it turned for: the System's warning tells them once they are through).
     */
    static RedGate start(MinecraftServer server, GateBlockEntity gate, GateInstance g, @Nullable UUID first) {
        RedGateData data = RedGateData.get(server);
        ServerLevel world = (ServerLevel) gate.getLevel();
        long now = world.getGameTime();
        RandomSource r = RandomSource.create(g.seed() ^ 0x1D5EEDL);
        PlayerRank inside = insideRank(g.rank(), r);
        RedGate run = new RedGate(g.id(), g.rank(), inside, g.seed() * 31L + 0x2EDL, data.takeSlot(),
                GateService.home(server, g, gate.facing().toYRot()), masterKind(inside, r));
        run.sealAt = now + RedGateConfig.openTicks();
        data.add(run);
        BUILDERS.put(run.gate, new RedWorldBuilder(run));
        gate.setRed(now);
        BlockPos pos = gate.getBlockPos();
        Vec3 c = gate.center();
        world.playSound(null, pos, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.BLOCKS, 3f, 0.5f);
        world.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.5f, 0.4f);
        world.playSound(null, pos, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.5f, 0.5f);
        ParticleGuard.send(world, RED_DUST, c.x, c.y, c.z, 60, 1.6, 2.2, 1.6, 0.02);
        for (ServerPlayer p : world.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(48))) {
            if (p.getUUID().equals(first)) continue;
            PacketDistributor.sendToPlayer(p, outside(run, gate, RedGatePayload.EV_TURNED));
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.redgate.turned.title"),
                    Component.translatable("notify.beyondsolo.redgate.turned.body", g.rank().displayName())));
            WATCHING.add(p.getUUID());
        }
        return run;
    }

    /** What waits behind a red gate: a rank above the gate's own, now and then two. */
    static PlayerRank insideRank(PlayerRank rank, RandomSource r) {
        int up = 1 + (r.nextFloat() < 0.25f ? 1 : 0);
        return PlayerRank.values()[Math.min(PlayerRank.S.ordinal(), rank.ordinal() + up)];
    }

    /** The master of the land: most often a frost alpha, else a knight, a magus or a brute (those that live at its rank). */
    static BossKind masterKind(PlayerRank inside, RandomSource r) {
        BossKind[] pool = {BossKind.ALPHA, BossKind.ALPHA, BossKind.ALPHA, BossKind.KNIGHT, BossKind.KNIGHT, BossKind.MAGUS, BossKind.MAGUS, BossKind.BRUTE};
        List<BossKind> fit = new ArrayList<>();
        for (BossKind k : pool) if (k.livesAt(inside)) fit.add(k);
        return fit.isEmpty() ? BossKind.BRUTE : fit.get(r.nextInt(fit.size()));
    }

    /** The creatures of the Red Winter at a rank: its frost-touched tribes, hounds and dead. */
    static EnemyRoster.Mix mix(PlayerRank inside) {
        return MIXES.computeIfAbsent(inside, RedGates::makeMix);
    }

    private static EnemyRoster.Mix makeMix(PlayerRank rank) {
        List<EnemyRoster.Entry> entries = switch (rank) {
            case E, D -> List.of(own(MonsterKind.GLOOMFANG, 4), own(MonsterKind.BONE_ARCHER, 3), own(MonsterKind.GRAVE_THRALL, 2),
                    own(MonsterKind.SHARDBACK, 2), own(MonsterKind.RIME_STALKER, 2));
            case C -> List.of(own(MonsterKind.RIME_STALKER, 4), own(MonsterKind.GLOOMFANG, 3), own(MonsterKind.TUSK_SHAMAN, 2),
                    own(MonsterKind.TUSKBRUTE, 2), own(MonsterKind.BONE_ARCHER, 2), own(MonsterKind.SHARDBACK, 1));
            case B -> List.of(own(MonsterKind.RIME_STALKER, 4), own(MonsterKind.RIMECLAW, 2), own(MonsterKind.WAILING_WRAITH, 2),
                    own(MonsterKind.TUSK_SHAMAN, 2), own(MonsterKind.TUSKBRUTE, 2), own(MonsterKind.GLOOMFANG, 2));
            default -> List.of(own(MonsterKind.RIME_STALKER, 4), own(MonsterKind.RIMECLAW, 3), own(MonsterKind.WAILING_WRAITH, 3),
                    own(MonsterKind.TUSK_SHAMAN, 2), own(MonsterKind.GLOOMFANG, 2), own(MonsterKind.RUNE_COLOSSUS, 1));
        };
        return new EnemyRoster.Mix(entries, Affinity.FROST);
    }

    private static EnemyRoster.Entry own(MonsterKind kind, int weight) {
        return new EnemyRoster.Entry(() -> EnemyModule.type(kind), weight, true);
    }

    // ================================================================ going through

    /** Starts the way in (the same moment as any gate's, in red). */
    private static void join(ServerPlayer player, RedGate run, GateBlockEntity gate) {
        if (busy(player)) return;
        ServerLevel level = player.serverLevel();
        run.party.add(player.getUUID());
        RedGateData.get(player.server).setDirty();
        KNOWN.put(player.getUUID(), player);
        WATCHING.remove(player.getUUID());
        TRANSITS.put(player.getUUID(), new Transit(run.gate, false, level.getGameTime()));
        freeze(player, true);
        level.playSound(null, gate.getBlockPos(), SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.35f, 0.9f);
        level.playSound(null, gate.getBlockPos(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2f, 0.45f);
        PacketDistributor.sendToPlayer(player, new GatePayload(GatePayload.ENTER, GatePayload.RED + run.inside.ordinal(), -1, 0,
                dungeonName(), label(run)));
    }

    /**
     * From {@link GateService#beginExit}: true if this is a red gate's way out (it lets the hunter go only once its
     * hold is broken).
     */
    public static boolean exit(ServerPlayer player, GateBlockEntity gate) {
        if (gate.gateId() == null) return false;
        RedGate run = RedGateData.get(player.server).get(gate.gateId());
        if (run == null) return false;
        if (busy(player)) return true;
        if (run.state != RedGate.State.RELEASED) {
            int release = RedGateConfig.releaseDay();
            refuse(player, gate, release > 0 ? "message.beyondsolo.redgate.exit_sealed_day" : "message.beyondsolo.redgate.exit_sealed", release);
            return true;
        }
        TRANSITS.put(player.getUUID(), new Transit(run.gate, true, player.level().getGameTime()));
        freeze(player, true);
        player.level().playSound(null, gate.getBlockPos(), SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.3f, 1.8f);
        PacketDistributor.sendToPlayer(player, new GatePayload(GatePayload.EXIT, GatePayload.RED + run.inside.ordinal(), -1, 0,
                Component.translatable("gate.beyondsolo.leaving"), dungeonName()));
        return true;
    }

    private static void refuse(ServerPlayer player, GateBlockEntity gate, String key, Object... args) {
        long now = player.level().getGameTime();
        if (COOLDOWN.getOrDefault(player.getUUID(), 0L) <= now) {
            player.displayClientMessage(Component.translatable(key, args).withColor(RED), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2f, 0.5f);
            COOLDOWN.put(player.getUUID(), now + 60);
        }
        Vec3 away = player.position().subtract(gate.center()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, gate.facing().toYRot()) : away.normalize();
        player.push(away.x * 0.6, 0.25, away.z * 0.6);
        player.hurtMarked = true;
    }

    private static boolean busy(ServerPlayer player) {
        if (TRANSITS.containsKey(player.getUUID()) || GateService.inTransit(player)) return true;
        return COOLDOWN.getOrDefault(player.getUUID(), 0L) > player.level().getGameTime();
    }

    private static void freeze(ServerPlayer player, boolean on) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(FREEZE);
        if (on) speed.addTransientModifier(new AttributeModifier(FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void tickTransits(MinecraftServer server) {
        if (TRANSITS.isEmpty()) return;
        RedGateData data = RedGateData.get(server);
        Iterator<Map.Entry<UUID, Transit>> it = TRANSITS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Transit> e = it.next();
            Transit t = e.getValue();
            ServerPlayer p = player(server, e.getKey());
            RedGate run = data.get(t.gate());
            ServerLevel level = RedWorld.level(server);
            if (p == null || run == null || level == null || !p.isAlive() || run.state == RedGate.State.ENDED) {
                if (p != null) freeze(p, false);
                it.remove();
                continue;
            }
            p.setDeltaMovement(p.getDeltaMovement().multiply(0, 1, 0));
            long elapsed = p.level().getGameTime() - t.started();
            if (!t.exit()) {
                if (elapsed < GateService.ENTER_TICKS) continue;
                // the breach has to stand before anyone can arrive in it; after twenty seconds it is made at once
                if (!ensureArrival(level, run, elapsed > 20 * 20)) continue;
                it.remove();
                freeze(p, false);
                arrive(p, run);
            } else if (elapsed >= GateService.EXIT_TICKS) {
                it.remove();
                freeze(p, false);
                leave(p, run);
            }
        }
    }

    /** True once the breach (where hunters arrive) stands; with {@code now}, makes it stand. */
    private static boolean ensureArrival(ServerLevel level, RedGate run, boolean now) {
        if (run.exit != null) return true;
        RedWorldBuilder b = BUILDERS.computeIfAbsent(run.gate, k -> new RedWorldBuilder(run));
        if (now) b.finishArrival(level);
        return run.exit != null;
    }

    /** Puts a hunter down in the breach, by the sealed way out, facing the far red light of the keep. */
    static boolean arrive(ServerPlayer p, RedGate run) {
        MinecraftServer server = p.server;
        ServerLevel level = RedWorld.level(server);
        if (level == null || !ensureArrival(level, run, true)) return false;
        Direction front = RedWorldBuilder.front(run);
        Vec3 at = level.getBlockEntity(run.exit) instanceof GateBlockEntity exit ? exit.front() : Vec3.atBottomCenterOf(run.exit.relative(front, 2));
        // the party side by side rather than on top of each other
        int n = run.insideNow.size() % 3;
        Direction side = front.getClockWise();
        at = at.add(side.getStepX() * (n == 1 ? 1.6 : n == 2 ? -1.6 : 0), 0, side.getStepZ() * (n == 1 ? 1.6 : n == 2 ? -1.6 : 0));
        float yaw = front.toYRot();
        moving = true;
        try {
            p.teleportTo(level, at.x, at.y, at.z, yaw, 0f);
            // fake players (tests) ignore same-level teleports
            if (p.distanceToSqr(at) > 4) p.moveTo(at.x, at.y, at.z, yaw, 0f);
        } finally {
            moving = false;
        }
        p.setDeltaMovement(Vec3.ZERO);
        UUID id = p.getUUID();
        COOLDOWN.put(id, level.getGameTime() + 60);
        KNOWN.put(id, p);
        run.party.add(id);
        run.insideNow.add(id);
        RedGateData.get(server).setDirty();
        p.getPersistentData().putUUID(IN_RED_TAG, run.gate);
        CHILL.put(id, 0);
        BlockPos b = BlockPos.containing(at);
        level.playSound(null, b, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.4f);
        level.playSound(null, b, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2f, 0.6f);
        // the warning first: the quest's own window waits until it has had its say
        PacketDistributor.sendToPlayer(p, inside(server, run, p, RedGatePayload.EV_WARNING));
        giveQuest(p, run);
        return true;
    }

    /** Back out in front of the gate in the world: cleared (its master fell) or escaped (they outlasted its hold). */
    static void leave(ServerPlayer p, RedGate run) {
        MinecraftServer server = p.server;
        UUID id = p.getUUID();
        boolean cleared = run.cleared;
        long xp;
        if (cleared) {
            xp = clearXp(run);
        } else {
            // they outlasted it: the System pays for that too, if not what its master was worth
            xp = GateTheme.of(run.rank).clearXp();
            SystemAPI.grantXp(p, xp, BeyondSolo.id("red_gate_escape"));
            p.getData(RedGateModule.RECORD).addEscaped(run.day());
            failQuest(p, run, QuestEvent.FailReason.FLED);
        }
        run.insideNow.remove(id);
        forget(p);
        moving = true;
        try {
            GateService.place(p, run.home);
        } finally {
            moving = false;
        }
        COOLDOWN.put(id, p.level().getGameTime() + 80);
        PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.RETURN, GatePayload.RED + run.inside.ordinal(), -1, 0,
                Component.translatable(cleared ? "redgate.beyondsolo.overlay.cleared" : "redgate.beyondsolo.escaped"), dungeonName()));
        PacketDistributor.sendToPlayer(p, summary(run, cleared ? RedGatePayload.EV_SUMMARY_CLEARED : RedGatePayload.EV_SUMMARY_ESCAPED, xp));
        RedGateData.get(server).setDirty();
        if (run.insideNow.isEmpty() && !transitTo(run.gate)) end(server, run);
    }

    /** A hunter is no longer in a red gate: their cold and their mark go. */
    private static void forget(ServerPlayer p) {
        UUID id = p.getUUID();
        p.getPersistentData().remove(IN_RED_TAG);
        CHILL.remove(id);
        WARM.remove(id);
        FREEZING.remove(id);
        EDGE_WARNED.remove(id);
        p.setTicksFrozen(0);
    }

    /** Sends a hunter home at once: in front of the gate in the world, or (no gate) to where they respawn. */
    static void sendHome(ServerPlayer p, @Nullable RedGate run) {
        forget(p);
        moving = true;
        try {
            if (run != null) {
                GateService.place(p, run.home);
            } else {
                ServerLevel world = p.server.overworld();
                BlockPos spawn = p.getRespawnPosition() != null && p.getRespawnDimension().equals(Level.OVERWORLD) ? p.getRespawnPosition() : world.getSharedSpawnPos();
                BlockPos top = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
                p.teleportTo(world, top.getX() + 0.5, top.getY(), top.getZ() + 0.5, p.getYRot(), 0f);
            }
        } finally {
            moving = false;
        }
        PacketDistributor.sendToPlayer(p, RedGatePayload.none(RedGatePayload.EV_NONE));
    }

    // ================================================================ the run

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        tickTransits(server);
        RedGateData data = RedGateData.get(server);
        ServerLevel level = RedWorld.level(server);
        long now = server.overworld().getGameTime();
        if (level != null) {
            for (RedGate run : List.copyOf(data.all())) {
                if (run.state == RedGate.State.ENDED) continue;
                try {
                    tickRun(server, level, data, run, now);
                } catch (RuntimeException e) {
                    // a run that can't go on lets its hunters go rather than holding them in a broken world
                    BeyondSolo.LOGGER.error("Red gate {} failed; sending its hunters home", run.gate, e);
                    abort(server, run);
                }
            }
        }
        if (server.getTickCount() % 20 == 0) watchers(server, data);
        if (server.getTickCount() % 40 == 0 && level != null) strays(level);
        if (server.getTickCount() % 6000 == 0) data.forgetGone(id -> GateData.get(server).get(id) == null);
    }

    /** A red gate about to seal holds a little longer while a party member of someone inside is still on the way. */
    private static boolean holdsForParty(MinecraftServer server, RedGate run, long now) {
        GateInstance g = GateData.get(server).get(run.gate);
        return g != null && com.beyondsolo.party.Parties.holdFor(server, run.gate, g.gateDimension(), g.gatePos(), run.insideNow, now - run.sealAt);
    }

    private static void tickRun(MinecraftServer server, ServerLevel level, RedGateData data, RedGate run, long now) {
        RedWorldBuilder b = BUILDERS.get(run.gate);
        if (b == null && !run.built) BUILDERS.put(run.gate, b = new RedWorldBuilder(run));
        if (b != null) {
            if (b.tick(level)) BUILDERS.remove(run.gate);
            data.setDirty();
        }

        List<ServerPlayer> inside = insidePlayers(server, level, run);
        if (run.state == RedGate.State.OPEN && now >= run.sealAt && !holdsForParty(server, run, now)) {
            if (run.insideNow.isEmpty() && !transitTo(run.gate)) {
                fizzle(server, run);
                return;
            }
            seal(server, level, run, inside);
        }
        if (inside.isEmpty()) {
            // hunters still in it who are away: a sealed one waits for them; one that has let go is over
            if (run.state == RedGate.State.RELEASED && !transitTo(run.gate)) end(server, run);
            return;
        }

        // the inside clock
        long before = run.insideTime;
        run.insideTime += RedGateConfig.timeRate();
        int day = run.day();
        int t = run.timeOfDay();
        if (t >= RedWorld.NIGHT_START && t < RedWorld.NIGHT_END && run.nightAnnounced < day) {
            run.nightAnnounced = day;
            run.raidIn = FIRST_WAVE;
            for (ServerPlayer p : inside) {
                event(p, run, RedGatePayload.EV_NIGHT);
                p.playNotifySound(SoundEvents.WOLF_HOWL, SoundSource.HOSTILE, 0.9f, 0.55f);
            }
        }
        if (t >= RedWorld.NIGHT_END && run.dawnAnnounced <= day) {
            run.dawnAnnounced = day + 1;
            dawn(server, level, run, inside);
        }
        if (run.weatherRolled < day) {
            run.weatherRolled = day;
            rollWeather(level.random, run, day);
        }
        if (before < run.blizzardFrom && run.insideTime >= run.blizzardFrom && run.blizzardUntil > run.insideTime) {
            for (ServerPlayer p : inside) event(p, run, RedGatePayload.EV_BLIZZARD);
        } else if (before < run.blizzardUntil && run.insideTime >= run.blizzardUntil && run.blizzardFrom <= before) {
            for (ServerPlayer p : inside) event(p, run, RedGatePayload.EV_CALM);
        }

        // the night's waves
        if (run.state == RedGate.State.SEALED && run.night() && --run.raidIn <= 0) {
            run.raidIn = WAVE_MIN + level.random.nextInt(WAVE_SPREAD);
            raid(level, run, inside);
        }

        // the cold, every tick (vanilla thaws a little every tick): vanilla's frost rim (and its freezing) only comes in
        // the last stretch of the chill, before that the System's own frost at the edges shows it
        for (ServerPlayer p : inside) {
            int chill = CHILL.getOrDefault(p.getUUID(), 0);
            p.setTicksFrozen(chill < FROST_RIM ? 0
                    : Math.round((chill - FROST_RIM) / (float) (MAX_CHILL - FROST_RIM) * p.getTicksRequiredToFreeze()) + 2);
        }

        if ((now + run.slot) % 10 == 0) {
            for (ServerPlayer p : inside) {
                cold(level, run, p);
                edge(level, run, p, now);
            }
            camps(level, run, inside);
            keep(level, run, inside);
            master(level, run, inside);
            if (run.state == RedGate.State.RELEASED && run.exit != null && level.isLoaded(run.exit)
                    && level.getBlockEntity(run.exit) instanceof GateBlockEntity exit && exit.isRed()) {
                exit.fadeRed(now);
            }
            beacon(level, run, inside);
            for (ServerPlayer p : inside) event(p, run, RedGatePayload.EV_NONE);
        }
        data.setDirty();
    }

    /** Nobody it took is in it any more, alive and out: it closes (the gate in the world with it). */
    static void end(MinecraftServer server, RedGate run) {
        if (run.state == RedGate.State.ENDED) return;
        long now = server.overworld().getGameTime();
        run.state = RedGate.State.ENDED;
        run.endedAt = now;
        BUILDERS.remove(run.gate);
        GateData gates = GateData.get(server);
        GateInstance g = gates.get(run.gate);
        if (g != null && g.state() != GateInstance.State.CLOSED) {
            g.setState(GateInstance.State.CLOSED);
            gates.setDirty();
        }
        GateBlockEntity gate = worldGate(server, run);
        if (gate != null) {
            gate.fadeRed(now);
            GateService.close((ServerLevel) gate.getLevel(), gate);
        }
        clearPatch(server, run);
        RedGateData.get(server).setDirty();
    }

    /**
     * Everyone it took has fallen in it: the red fades from the gate in the world and it is an ordinary gate again,
     * one at its last minute before it breaks (when gates break at all: see {@code breaks.DungeonBreaks}).
     */
    static void failed(MinecraftServer server, RedGate run) {
        if (run.state == RedGate.State.ENDED) return;
        long now = server.overworld().getGameTime();
        run.state = RedGate.State.ENDED;
        run.endedAt = now;
        BUILDERS.remove(run.gate);
        RedGateData data = RedGateData.get(server);
        data.defuse(run.gate);
        GateBlockEntity gate = worldGate(server, run);
        if (gate != null) gate.fadeRed(now);
        GateData gates = GateData.get(server);
        GateInstance g = gates.get(run.gate);
        if (g != null && Config.GATE_BREAK_MINUTES.get() > 0 && DungeonBreaks.canBreak(g)) {
            BreakState b = g.breakState();
            if (!b.clockStarted()) b.start(now, DungeonBreaks.IMMINENT_TICKS);
            else b.setBreakAt(Math.min(b.breakAt(), now + DungeonBreaks.IMMINENT_TICKS));
            gates.setDirty();
        }
        clearPatch(server, run);
        data.setDirty();
    }

    /** Nobody made it in before it sealed (they all turned back on the way): it was never a red gate after all. */
    static void fizzle(MinecraftServer server, RedGate run) {
        long now = server.overworld().getGameTime();
        run.state = RedGate.State.ENDED;
        run.endedAt = now;
        BUILDERS.remove(run.gate);
        RedGateData data = RedGateData.get(server);
        data.defuse(run.gate);
        GateBlockEntity gate = worldGate(server, run);
        if (gate != null) gate.fadeRed(now);
        data.setDirty();
    }

    /** Everyone out now, nothing paid (a run that broke, or a command). */
    static void abort(MinecraftServer server, RedGate run) {
        for (UUID id : List.copyOf(run.insideNow)) {
            ServerPlayer p = player(server, id);
            if (p == null) continue;
            failQuest(p, run, QuestEvent.FailReason.ABANDONED);
            sendHome(p, run);
        }
        run.insideNow.clear();
        end(server, run);
    }

    /**
     * What is left of its creatures goes with it, and whatever lies in the snow (the belongings of those who fell)
     * comes back out in front of the gate.
     */
    private static void clearPatch(MinecraftServer server, RedGate run) {
        ServerLevel level = RedWorld.level(server);
        if (level == null || run.center == null) return;
        AABB box = new AABB(run.center).inflate(RedWorld.EDGE + 48, 192, RedWorld.EDGE + 48);
        for (Entity e : level.getEntities((Entity) null, box, e -> belongsTo(e, run.gate))) e.discard();
        ServerLevel home = server.getLevel(run.home.dimension());
        if (home == null) return;
        Vec3 to = run.home.pos();
        int n = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (n++ >= 256) break;
            ItemEntity copy = new ItemEntity(home, to.x, to.y + 0.5, to.z, item.getItem().copy());
            copy.setDefaultPickUpDelay();
            home.addFreshEntity(copy);
            item.discard();
        }
    }

    private static boolean belongsTo(Entity e, UUID gate) {
        CompoundTag tag = e.getPersistentData();
        return (tag.hasUUID(MOB_TAG) && gate.equals(tag.getUUID(MOB_TAG))) || (tag.hasUUID(BOSS_TAG) && gate.equals(tag.getUUID(BOSS_TAG)));
    }

    /** The gate shuts behind the party. */
    private static void seal(MinecraftServer server, ServerLevel level, RedGate run, List<ServerPlayer> inside) {
        run.state = RedGate.State.SEALED;
        run.raidIn = FIRST_WAVE;
        RedGateData.get(server).setDirty();
        GateBlockEntity gate = worldGate(server, run);
        if (gate != null) {
            ServerLevel world = (ServerLevel) gate.getLevel();
            world.playSound(null, gate.getBlockPos(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.4f);
            world.playSound(null, gate.getBlockPos(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 2f, 0.5f);
        }
        if (run.exit != null && level.isLoaded(run.exit)) level.playSound(null, run.exit, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 2f, 0.5f);
        for (ServerPlayer p : inside) {
            event(p, run, RedGatePayload.EV_SEALED);
            p.playNotifySound(SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1f, 0.4f);
        }
    }

    /** Its hold breaks: the way out opens though its master lives (the release day). */
    static void release(MinecraftServer server, ServerLevel level, RedGate run, List<ServerPlayer> inside) {
        if (run.state != RedGate.State.OPEN && run.state != RedGate.State.SEALED) return;
        run.state = RedGate.State.RELEASED;
        RedGateData.get(server).setDirty();
        if (run.exit != null && level.isLoaded(run.exit)) {
            if (level.getBlockEntity(run.exit) instanceof GateBlockEntity exit) exit.fadeRed(level.getGameTime());
            level.playSound(null, run.exit, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2f, 0.6f);
        }
        for (ServerPlayer p : inside) {
            event(p, run, RedGatePayload.EV_RELEASED);
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.redgate.released.title"),
                    Component.translatable("notify.beyondsolo.redgate.released.body")));
        }
    }

    /** Dawn inside: the night's raiders go back into the snow; on the release day the gate's hold breaks. */
    private static void dawn(MinecraftServer server, ServerLevel level, RedGate run, List<ServerPlayer> inside) {
        for (UUID id : run.raiders) {
            if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
                ParticleGuard.send(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
                        m.getX(), m.getY() + 0.6, m.getZ(), 24, 0.4, 0.6, 0.4, 0.08);
                m.discard();
            }
        }
        run.raiders.clear();
        int release = RedGateConfig.releaseDay();
        boolean freed = release > 0 && run.dawnAnnounced >= release && run.state == RedGate.State.SEALED;
        for (ServerPlayer p : inside) {
            p.getData(RedGateModule.RECORD).addNight();
            if (!freed) event(p, run, RedGatePayload.EV_DAWN);
        }
        if (freed) release(server, level, run, inside);
    }

    /** Whether today brings a blizzard, and when: more likely the longer the hunters have been in. */
    private static void rollWeather(RandomSource r, RedGate run, int day) {
        float chance = Math.min(0.7f, 0.15f + 0.1f * (day - 1));
        if (r.nextFloat() >= chance) return;
        long dayStart = (day - 1) * 24000L;
        long from = Math.max(run.insideTime + 200, dayStart + r.nextInt(20000));
        run.blizzardFrom = from;
        run.blizzardUntil = from + 3000 + r.nextInt(4000);
    }

    // ================================================================ creatures

    /** One of the Red Winter's creatures: its inside rank, frost-touched, angrier than its kind outside. */
    @Nullable
    static Mob creature(ServerLevel level, RedGate run, BlockPos at, boolean leader, boolean late) {
        EnemyRoster.Mix mix = mix(run.inside);
        RandomSource r = level.random;
        EntityType<?> type = mix.pick(r);
        Mob mob = Enemies.spawn(level, run.inside, type, at, Enemies.roll(run.inside, mix, type, r, leader, late), -1);
        if (mob == null) return null;
        mob.getPersistentData().putUUID(MOB_TAG, run.gate);
        fury(mob);
        com.beyondsolo.enemy.HiddenLevel.refresh(mob);
        return mob;
    }

    private static void fury(Mob mob) {
        var hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null && hp.getModifier(FURY_HEALTH) == null) {
            hp.addPermanentModifier(new AttributeModifier(FURY_HEALTH, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            mob.setHealth(mob.getMaxHealth());
        }
        var dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null && dmg.getModifier(FURY_DAMAGE) == null) {
            dmg.addPermanentModifier(new AttributeModifier(FURY_DAMAGE, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** A raider: it knows who it came for, and doesn't lose them in the dark. */
    private static void hunt(Mob mob, LivingEntity target) {
        var range = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null && range.getModifier(HUNT) == null) range.addPermanentModifier(new AttributeModifier(HUNT, 40, AttributeModifier.Operation.ADD_VALUE));
        mob.setTarget(target);
    }

    /** A wave of the night: out of the dark, twenty-odd blocks from one of the hunters. */
    static int raid(ServerLevel level, RedGate run, List<ServerPlayer> inside) {
        if (inside.isEmpty()) return 0;
        run.raiders.removeIf(id -> !(level.getEntity(id) instanceof Mob m) || !m.isAlive());
        int cap = 6 + 2 * inside.size();
        int size = Math.min(Math.min(6, 2 + (run.day() - 1) / 2 + run.inside.tier() / 3), cap - run.raiders.size());
        if (size <= 0) return 0;
        RandomSource r = level.random;
        ServerPlayer target = inside.get(r.nextInt(inside.size()));
        double a = r.nextDouble() * Math.PI * 2;
        int made = 0;
        for (int i = 0; i < size * 3 && made < size; i++) {
            double ang = a + (r.nextDouble() - 0.5) * 1.2;
            double d = 20 + r.nextDouble() * 8;
            int x = Mth.floor(target.getX() + Math.cos(ang) * d), z = Mth.floor(target.getZ() + Math.sin(ang) * d);
            if (!level.isLoaded(new BlockPos(x, target.getBlockY(), z))) continue;
            BlockPos at = stand(level, new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z));
            if (!level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty()) continue;
            Mob m = creature(level, run, at, made == 0, run.day() >= 3);
            if (m == null) continue;
            hunt(m, target);
            run.raiders.add(m.getUUID());
            made++;
            ParticleGuard.send(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
                    m.getX(), m.getY() + 0.5, m.getZ(), 30, 0.5, 0.6, 0.5, 0.1);
        }
        if (made > 0) {
            for (ServerPlayer p : inside) {
                event(p, run, RedGatePayload.EV_RAID);
                p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.7f, 0.7f);
            }
        }
        return made;
    }

    /** The camps: a pack rises round the fire as hunters come near; once it is dead its hoard is theirs. */
    private static void camps(ServerLevel level, RedGate run, List<ServerPlayer> inside) {
        if (!run.built) return;
        for (RedGate.Camp c : run.camps) {
            if (c.cleared) continue;
            double near = nearest(inside, c.pos);
            if (!c.triggered) {
                if (near > CAMP_REACH) continue;
                c.triggered = true;
                int n = 3 + run.inside.tier() / 2 + level.random.nextInt(2);
                ServerPlayer closest = closest(inside, c.pos);
                for (int k = 0; k < n; k++) {
                    double a = level.random.nextDouble() * Math.PI * 2, d = 3 + level.random.nextDouble() * 4;
                    BlockPos at = stand(level, c.pos.offset((int) Math.round(Math.cos(a) * d), 0, (int) Math.round(Math.sin(a) * d)));
                    Mob m = creature(level, run, at, k == 0, run.day() >= 3);
                    if (m == null) continue;
                    c.mobs.add(m.getUUID());
                    m.restrictTo(c.pos, 18);
                    if (closest != null) m.setTarget(closest);
                }
                level.playSound(null, c.pos, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 3f, 0.8f);
                continue;
            }
            // taken once all of its pack is dead (looked at only while someone is near enough for them to be there)
            if (near > 64 || !level.isPositionEntityTicking(c.pos)) continue;
            boolean standing = false;
            for (UUID id : c.mobs) {
                Entity e = level.getEntity(id);
                if (e != null && e.isAlive()) standing = true;
            }
            if (!standing) campTaken(level, run, c, inside);
        }
    }

    private static void campTaken(ServerLevel level, RedGate run, RedGate.Camp c, List<ServerPlayer> inside) {
        c.cleared = true;
        c.mobs.clear();
        BlockPos stash = stand(level, RedWorldBuilder.stashSpot(c.pos));
        level.setBlock(stash, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH), 3);
        if (level.getBlockEntity(stash) instanceof ChestBlockEntity chest) {
            int slot = 0;
            for (ItemStack s : GateLoot.roll(run.inside, level.random)) {
                if (slot >= chest.getContainerSize()) break;
                chest.setItem(slot, s);
                slot += 2;
            }
        }
        ParticleGuard.send(level, RED_DUST, stash.getX() + 0.5, stash.getY() + 1, stash.getZ() + 0.5, 20, 0.4, 0.5, 0.4, 0.02);
        level.playSound(null, stash, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1f, 0.8f);
        level.playSound(null, stash, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 0.6f);
        for (ServerPlayer p : inside) {
            QuestEngine.progress(p, CAMP_KEY, 1);
            event(p, run, RedGatePayload.EV_CAMP);
        }
    }

    /** The keep's guard turns out as hunters come up to its walls. */
    private static void keep(ServerLevel level, RedGate run, List<ServerPlayer> inside) {
        if (!run.built || run.keepTriggered || run.cleared || run.keep == null || nearest(inside, run.keep) > KEEP_REACH) return;
        run.keepTriggered = true;
        int i = 0;
        for (BlockPos spot : RedWorldBuilder.guardSpots(run)) {
            Mob m = creature(level, run, stand(level, spot), i++ % 3 == 0, true);
            if (m == null) continue;
            run.keepGuards.add(m.getUUID());
            m.restrictTo(run.keep, 22);
        }
        level.playSound(null, run.keep, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4f, 0.6f);
    }

    /** Its master: stands in its keep once the keep is built, wakes as hunters come near, calls for help when enraged. */
    private static void master(ServerLevel level, RedGate run, List<ServerPlayer> inside) {
        if (!run.built || run.cleared || run.keep == null) return;
        BlockPos spot = RedWorldBuilder.bossSpot(run);
        if (!run.bossSpawned) {
            if (level.isLoaded(spot)) spawnMaster(level, run, spot);
            return;
        }
        GateBossEntity boss = run.boss != null && level.getEntity(run.boss) instanceof GateBossEntity b ? b : null;
        if (boss == null || !boss.isAlive()) return;
        if (!run.bossAwake && nearest(inside, boss.blockPosition()) <= WAKE_REACH) {
            run.bossAwake = true;
            boss.wake();
            for (ServerPlayer p : inside) {
                PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.BOSS_INTRO, GatePayload.RED + run.inside.ordinal(), boss.getId(), 0,
                        bossName(run), Component.translatable("redgate.beyondsolo.master_subtitle", run.inside.displayName())));
            }
        }
        if (boss.isEnraged() && !run.bossEnrageAnswered) {
            run.bossEnrageAnswered = true;
            for (int i = 0; i < 2; i++) {
                float a = level.random.nextFloat() * Mth.TWO_PI;
                BlockPos p = stand(level, BlockPos.containing(boss.getX() + Mth.cos(a) * (boss.getBbWidth() / 2 + 3), boss.getY(),
                        boss.getZ() + Mth.sin(a) * (boss.getBbWidth() / 2 + 3)));
                Mob add = creature(level, run, p, false, true);
                if (add != null && boss.getTarget() != null) add.setTarget(boss.getTarget());
            }
            for (ServerPlayer p : inside) {
                PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.ENRAGED, GatePayload.RED + run.inside.ordinal(), boss.getId(), 0,
                        bossName(run), Component.empty()));
            }
        }
    }

    @Nullable
    static GateBossEntity spawnMaster(ServerLevel level, RedGate run, BlockPos spot) {
        GateBossEntity boss = ModEntities.GATE_BOSS.get().create(level);
        if (boss == null) return null;
        BlockPos at = stand(level, spot);
        float yaw = RedWorldBuilder.keepFront(run).toYRot();
        boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
        boss.configure(run.inside, run.bossKind, true);
        boss.setCustomName(bossName(run));
        boss.setCustomNameVisible(false);
        boss.getPersistentData().putUUID(BOSS_TAG, run.gate);
        level.addFreshEntity(boss);
        run.boss = boss.getUUID();
        run.bossSpawned = true;
        return boss;
    }

    /** Its master fell: the way out opens, the raiders scatter, and the red cache comes down where it stood. */
    static void masterFell(ServerLevel level, RedGate run, GateBossEntity boss) {
        if (run.cleared || run.state == RedGate.State.ENDED) return;
        MinecraftServer server = level.getServer();
        run.cleared = true;
        run.state = RedGate.State.RELEASED;
        RedGateData.get(server).setDirty();
        long now = level.getGameTime();
        if (run.exit != null && level.isLoaded(run.exit) && level.getBlockEntity(run.exit) instanceof GateBlockEntity exit) exit.fadeRed(now);
        for (UUID id : run.raiders) {
            if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
                ParticleGuard.send(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState()),
                        m.getX(), m.getY() + 0.6, m.getZ(), 24, 0.4, 0.6, 0.4, 0.08);
                m.discard();
            }
        }
        run.raiders.clear();
        for (ServerPlayer p : insidePlayers(server, level, run)) {
            PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.BOSS_DEFEATED, GatePayload.RED + run.inside.ordinal(), boss.getId(), 0,
                    bossName(run), Component.translatable("redgate.beyondsolo.master_fell")));
            // the master is the clear: whatever camps were left no longer count against it
            QuestEngine.progress(p, CAMP_KEY, Short.MAX_VALUE);
            QuestEngine.progress(p, MASTER_KEY, 1);
        }
        BlockPos center = boss.blockPosition();
        Direction facing = run.keep != null ? RedWorldBuilder.keepFront(run) : Direction.NORTH;
        ServerScheduler.schedule(GateBossEntity.DEATH_TICKS - 6, () -> {
            if (run.state == RedGate.State.ENDED) return;
            BlockPos cachePos = stand(level, center.relative(facing, 3));
            level.setBlock(cachePos, ModBlocks.REWARD_CACHE.get().defaultBlockState().setValue(RewardCacheBlock.FACING, facing), 3);
            if (level.getBlockEntity(cachePos) instanceof RewardCacheBlockEntity cache) {
                cache.setup(run.inside, run.bossKind, true, level.getGameTime());
                cache.setRed(true);
            }
            double mult = RedGateConfig.rewardMultiplier();
            long xp = clearXp(run);
            for (ServerPlayer p : insidePlayers(server, level, run)) {
                p.getData(RedGateModule.RECORD).addCleared(run.day());
                SystemAPI.grantXp(p, xp, BeyondSolo.id("red_gate_clear"));
                QuestEngine.progress(p, GateService.CLEAR_KEY, 1);
                EconomyHooks.gateCleared(p, run.inside, (float) mult);
                event(p, run, RedGatePayload.EV_CLEARED);
                // the cleared banner is the System talking; without it there's just the quiet
                if (!SystemAPI.hasSystem(p)) continue;
                PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.CLEARED, GatePayload.RED + run.inside.ordinal(), -1, xp,
                        dungeonName(), label(run)));
            }
        });
        ServerScheduler.schedule(GateBossEntity.DEATH_TICKS + 50, () -> {
            if (run.state != RedGate.State.ENDED) openKeepExit(level, run, center, facing);
        });
    }

    /** A way out opens behind where its master fell (the breach's is open too now). */
    private static void openKeepExit(ServerLevel level, RedGate run, BlockPos bossPos, Direction facing) {
        BlockPos pos = stand(level, bossPos.relative(facing.getOpposite(), 4));
        level.setBlock(pos, ModBlocks.GATE.get().defaultBlockState().setValue(GateBlock.FACING, facing), 3);
        if (level.getBlockEntity(pos) instanceof GateBlockEntity exit) exit.setup(run.gate, run.inside, true, level.getGameTime());
        for (int y = 1; y <= 5; y++) {
            for (int s = -2; s <= 2; s++) {
                BlockPos q = pos.above(y).relative(facing.getClockWise(), s);
                if (!level.getBlockState(q).isAir()) level.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        run.keepExit = pos;
        RedGateData.get(level.getServer()).setDirty();
        level.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.5f, 1.8f);
        for (ServerPlayer p : insidePlayers(level.getServer(), level, run)) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.redgate.exit.title"), Component.translatable("notify.beyondsolo.redgate.exit.body")));
        }
    }

    // ================================================================ the cold and the edge

    /**
     * Every half second: out in the open at night a hunter chills, in a blizzard fast; under a roof, in leather, or
     * wet the cold bites less or more; by a fire it goes. Frozen through, vanilla's freezing takes over.
     */
    static void cold(ServerLevel level, RedGate run, ServerPlayer p) {
        UUID id = p.getUUID();
        int chill = CHILL.getOrDefault(id, 0);
        if (!RedGateConfig.cold() || p.isCreative() || p.isSpectator()) {
            CHILL.put(id, 0);
            WARM.remove(id);
            return;
        }
        boolean warm = p.isOnFire() || warmth(level, p.blockPosition());
        if (warm) {
            WARM.add(id);
            chill = Math.max(0, chill - 12);
        } else {
            WARM.remove(id);
            float exposure = (run.night() ? 1f : 0f) + (run.blizzard() ? 1.6f : 0f);
            if (p.isInWaterOrBubble() || p.isInPowderSnow) exposure += 1.5f;
            if (!level.canSeeSky(p.blockPosition().above())) exposure *= 0.25f;
            exposure *= 1f - 0.2f * leather(p);
            chill = exposure <= 0.05f ? Math.max(0, chill - 1) : Math.min(MAX_CHILL, chill + Math.max(1, Math.round(exposure * 2.5f)));
        }
        CHILL.put(id, chill);
        if (chill >= 70 && FREEZING.add(id)) event(p, run, RedGatePayload.EV_FREEZING);
        else if (chill < 40) FREEZING.remove(id);
    }

    /** A fire close by: a campfire burning, flames, lava or magma, a lit furnace. */
    private static boolean warmth(ServerLevel level, BlockPos at) {
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-4, -2, -4), at.offset(4, 2, 4))) {
            BlockState s = level.getBlockState(q);
            if (s.is(Blocks.CAMPFIRE) && s.getValue(CampfireBlock.LIT)) return true;
            if (s.is(Blocks.FIRE) || s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK)) return true;
            if ((s.is(Blocks.FURNACE) || s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.SMOKER)) && s.getValue(AbstractFurnaceBlock.LIT)) return true;
        }
        return false;
    }

    private static int leather(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getArmorSlots()) if (s.is(ItemTags.FREEZE_IMMUNE_WEARABLES)) n++;
        return n;
    }

    /** Past the edge of the patch the blizzard is a wall: it drives them back, and beyond it throws them back. */
    private static void edge(ServerLevel level, RedGate run, ServerPlayer p, long now) {
        if (run.center == null) return;
        double dx = p.getX() - (run.center.getX() + 0.5), dz = p.getZ() - (run.center.getZ() + 0.5);
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < RedWorld.EDGE) return;
        double ix = -dx / d, iz = -dz / d;
        if (d > RedWorld.EDGE + 24) {
            int x = Mth.floor(run.center.getX() + 0.5 - ix * (RedWorld.EDGE - 8)), z = Mth.floor(run.center.getZ() + 0.5 - iz * (RedWorld.EDGE - 8));
            level.getChunk(x >> 4, z >> 4);
            BlockPos to = stand(level, new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z));
            p.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        } else {
            p.push(ix * 0.35, 0.05, iz * 0.35);
            p.hurtMarked = true;
        }
        if (RedGateConfig.cold()) CHILL.merge(p.getUUID(), 4, (a, b) -> Math.min(MAX_CHILL, a + b));
        if (EDGE_WARNED.getOrDefault(p.getUUID(), 0L) <= now) {
            EDGE_WARNED.put(p.getUUID(), now + 60);
            event(p, run, RedGatePayload.EV_EDGE);
        }
    }

    /** The red light in the spire's crown throws a thin red line up into the sky while its master lives. */
    private static void beacon(ServerLevel level, RedGate run, List<ServerPlayer> inside) {
        if (!run.built || run.cleared || run.keep == null) return;
        BlockPos top = RedWorldBuilder.spireTop(run);
        for (ServerPlayer p : inside) {
            double d = p.blockPosition().distSqr(top);
            if (d > 240 * 240 || d < 20 * 20) continue;
            for (int k = 0; k < 4; k++) {
                level.sendParticles(p, BEACON_DUST, true, top.getX() + 0.5, top.getY() + 2 + k * 4 + level.random.nextDouble() * 4, top.getZ() + 0.5,
                        1, 0.08, 0.6, 0.08, 0);
            }
        }
    }

    // ================================================================ watching from outside, strays

    /** Once a second: players near a red gate in the world see how it stands (sealed, how many inside, the day in there). */
    private static void watchers(MinecraftServer server, RedGateData data) {
        Set<UUID> seen = new HashSet<>();
        for (RedGate run : data.all()) {
            if (run.state == RedGate.State.ENDED) continue;
            GateBlockEntity gate = worldGate(server, run);
            if (gate == null) continue;
            ServerLevel world = (ServerLevel) gate.getLevel();
            for (ServerPlayer p : world.getEntitiesOfClass(ServerPlayer.class, new AABB(gate.getBlockPos()).inflate(WATCH_REACH))) {
                if (TRANSITS.containsKey(p.getUUID()) || run.insideNow.contains(p.getUUID())) continue;
                seen.add(p.getUUID());
                PacketDistributor.sendToPlayer(p, outside(run, gate, RedGatePayload.EV_NONE));
            }
        }
        for (UUID id : List.copyOf(WATCHING)) {
            if (seen.contains(id)) continue;
            WATCHING.remove(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && !occupied(p)) PacketDistributor.sendToPlayer(p, RedGatePayload.none(RedGatePayload.EV_NONE));
        }
        WATCHING.addAll(seen);
    }

    /** Anyone in the Red Winter who isn't in one of its red gates (a teleport, a gate that ended while away) goes home. */
    private static void strays(ServerLevel level) {
        // the game-test fallback shares the overworld with everything else
        if (!RedWorld.isRedWorld(level)) return;
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (TRANSITS.containsKey(p.getUUID()) || p.isCreative() || p.isSpectator()) continue;
            RedGate run = runOf(p.server, p);
            if (run == null || run.state == RedGate.State.ENDED || !run.insideNow.contains(p.getUUID())) {
                if (run != null) run.insideNow.remove(p.getUUID());
                sendHome(p, run);
            }
        }
    }

    // ================================================================ the quest

    private static void giveQuest(ServerPlayer p, RedGate run) {
        if (!SystemAPI.isAwakened(p) || run.cleared) return;
        for (Quest q : QuestEngine.data(p).active()) {
            if (QUEST_TEMPLATE.equals(q.template()) && run.gate.toString().equals(q.extra().getString("gate"))) return;
        }
        int tier = run.inside.tier();
        CompoundTag extra = new CompoundTag();
        extra.putString("gate", run.gate.toString());
        Quest q = QuestBuilder.of(QUEST_TEMPLATE, QuestCategory.EMERGENCY, tier, SystemAPI.get(p).level())
                .title(Component.translatable("quest.beyondsolo.red_gate", run.rank.displayName()))
                .description(Component.translatable("quest.beyondsolo.red_gate.desc", bossName(run)))
                .custom(CAMP_KEY, Math.max(1, Math.min(3, run.camps.size())), Component.translatable("quest_objective.beyondsolo.red_gate_camps"))
                .custom(MASTER_KEY, 1, Component.translatable("quest_objective.beyondsolo.red_gate_master", bossName(run)))
                .fixed().xp(300L * (1 + tier)).statPoints(tier >= 3 ? 3 : 2)
                .extra(extra)
                .build();
        QuestEngine.start(p, q);
    }

    private static void failQuest(ServerPlayer p, RedGate run, QuestEvent.FailReason reason) {
        for (Quest q : List.copyOf(QuestEngine.data(p).active())) {
            if (QUEST_TEMPLATE.equals(q.template()) && run.gate.toString().equals(q.extra().getString("gate"))) QuestEngine.fail(p, q, reason);
        }
    }

    // ================================================================ events

    /** Nobody gets hurt halfway through a gate. */
    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && TRANSITS.containsKey(p.getUUID())) event.setCanceled(true);
    }

    /** The Red Winter's creatures hit cold. */
    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getNewDamage() <= 0 || !RedGateConfig.cold()) return;
        Entity src = event.getSource().getEntity();
        if (src == null || !(src.getPersistentData().hasUUID(MOB_TAG) || src.getPersistentData().hasUUID(BOSS_TAG))) return;
        if (CHILL.containsKey(p.getUUID())) CHILL.merge(p.getUUID(), 6, (a, b) -> Math.min(MAX_CHILL, a + b));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        if (victim instanceof ServerPlayer p) {
            playerFell(p);
            return;
        }
        CompoundTag tag = victim.getPersistentData();
        RedGateData data = RedGateData.get(level.getServer());
        if (tag.hasUUID(BOSS_TAG)) {
            RedGate run = data.get(tag.getUUID(BOSS_TAG));
            if (run != null && victim instanceof GateBossEntity boss && boss.getUUID().equals(run.boss)) masterFell(level, run, boss);
        } else if (tag.hasUUID(MOB_TAG)) {
            RedGate run = data.get(tag.getUUID(MOB_TAG));
            if (run != null && run.state != RedGate.State.ENDED) {
                run.kills++;
                data.setDirty();
            }
        }
    }

    /** A hunter fell in a red gate: out of it (they wake where they respawn); the last of them, and it has failed. */
    static void playerFell(ServerPlayer p) {
        RedGate run = runOf(p.server, p);
        if (run == null || run.state == RedGate.State.ENDED || !run.insideNow.contains(p.getUUID())) return;
        MinecraftServer server = p.server;
        run.insideNow.remove(p.getUUID());
        forget(p);
        failQuest(p, run, QuestEvent.FailReason.DIED);
        p.getData(RedGateModule.RECORD).addFallen();
        PacketDistributor.sendToPlayer(p, summary(run, RedGatePayload.EV_SUMMARY_FAILED, 0));
        RedGateData.get(server).setDirty();
        if (run.insideNow.isEmpty() && !transitTo(run.gate)) {
            // a moment on, once what they carried has fallen into the snow
            ServerScheduler.schedule(2, () -> failed(server, run));
        }
    }

    /** Someone who logged out in a red gate comes back to it as it is now: still holding them, or over (then they go home). */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        RedGate run = runOf(p.server, p);
        boolean there = RedWorld.isRedWorld(p.level());
        if (run == null && !there) return;
        if (run != null && run.state != RedGate.State.ENDED && run.insideNow.contains(p.getUUID()) && there) {
            KNOWN.put(p.getUUID(), p);
            CHILL.putIfAbsent(p.getUUID(), 0);
            PacketDistributor.sendToPlayer(p, inside(p.server, run, p, RedGatePayload.EV_NONE));
            return;
        }
        if (run == null && (p.isCreative() || p.isSpectator())) return;
        if (run != null) {
            run.insideNow.remove(p.getUUID());
            RedGateData.get(p.server).setDirty();
        }
        if (there || run != null) sendHome(p, run);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        if (TRANSITS.remove(id) != null && event.getEntity() instanceof ServerPlayer p) freeze(p, false);
        COOLDOWN.remove(id);
        KNOWN.remove(id);
        WATCHING.remove(id);
        WARM.remove(id);
        FREEZING.remove(id);
        EDGE_WARNED.remove(id);
    }

    /** Sealed means sealed: no other way out of a red gate (portals, commands of other mods) until it lets go. */
    @SubscribeEvent
    static void onTravel(EntityTravelToDimensionEvent event) {
        if (moving || !(event.getEntity() instanceof ServerPlayer p) || p.isCreative() || p.isSpectator()) return;
        RedGate run = runOf(p.server, p);
        if (run == null || (run.state != RedGate.State.OPEN && run.state != RedGate.State.SEALED) || !run.insideNow.contains(p.getUUID())) return;
        event.setCanceled(true);
        p.displayClientMessage(Component.translatable("message.beyondsolo.redgate.no_way_out").withColor(RED), true);
    }

    @SubscribeEvent
    static void onPortal(BlockEvent.PortalSpawnEvent event) {
        if (event.getLevel() instanceof Level l && RedWorld.isRedWorld(l)) event.setCanceled(true);
    }

    /** Nobody sleeps in the Red Winter. */
    @SubscribeEvent
    static void onSleep(CanPlayerSleepEvent event) {
        if (RedWorld.isRedWorld(event.getLevel())) event.setProblem(Player.BedSleepingProblem.NOT_SAFE);
    }

    @SubscribeEvent
    static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (RedWorld.DIMENSION.equals(event.getSpawnLevel())) event.setCanceled(true);
    }

    /** A red gate's creature loaded back in after its red gate ended (or its master, replaced) is turned away. */
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = event.getEntity().getPersistentData();
        UUID gate = tag.hasUUID(MOB_TAG) ? tag.getUUID(MOB_TAG) : tag.hasUUID(BOSS_TAG) ? tag.getUUID(BOSS_TAG) : null;
        if (gate == null) return;
        RedGate run = RedGateData.get(level.getServer()).get(gate);
        boolean replaced = tag.hasUUID(BOSS_TAG) && run != null && !event.getEntity().getUUID().equals(run.boss);
        if (run == null || run.state == RedGate.State.ENDED || replaced) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        TRANSITS.clear();
        COOLDOWN.clear();
        BUILDERS.clear();
        CHILL.clear();
        WARM.clear();
        FREEZING.clear();
        EDGE_WARNED.clear();
        WATCHING.clear();
        KNOWN.clear();
        moving = false;
    }

    // ================================================================ what the client is told

    /** A hunter inside: the clock, the cold, the way to its master (or out). */
    static RedGatePayload inside(MinecraftServer server, RedGate run, ServerPlayer p, int event) {
        int[] v = new int[RedGatePayload.SIZE];
        v[RedGatePayload.I_MODE] = RedGatePayload.INSIDE;
        v[RedGatePayload.I_PHASE] = run.state.ordinal();
        v[RedGatePayload.I_RANK] = run.rank.ordinal();
        v[RedGatePayload.I_INSIDE_RANK] = run.inside.ordinal();
        v[RedGatePayload.I_DAY] = run.day();
        v[RedGatePayload.I_TIME] = run.timeOfDay();
        v[RedGatePayload.I_RATE] = RedGateConfig.timeRate();
        v[RedGatePayload.I_BLIZZARD] = run.blizzard() ? 1 : 0;
        v[RedGatePayload.I_CHILL] = CHILL.getOrDefault(p.getUUID(), 0);
        v[RedGatePayload.I_PARTY] = run.insideNow.size();
        v[RedGatePayload.I_KILLS] = run.kills;
        v[RedGatePayload.I_RELEASE_DAY] = RedGateConfig.releaseDay();
        BlockPos target = target(server, run, p);
        if (target != null) {
            v[RedGatePayload.I_TARGET_X] = target.getX();
            v[RedGatePayload.I_TARGET_Z] = target.getZ();
        }
        int flags = 0;
        if (!run.cleared) flags |= RedGatePayload.F_BOSS_ALIVE;
        if (run.cleared) flags |= RedGatePayload.F_CLEARED;
        if (run.bossAwake) flags |= RedGatePayload.F_BOSS_AWAKE;
        if (run.night()) flags |= RedGatePayload.F_NIGHT;
        if (WARM.contains(p.getUUID())) flags |= RedGatePayload.F_WARM;
        if (target == null) flags |= RedGatePayload.F_NO_TARGET;
        v[RedGatePayload.I_FLAGS] = flags;
        if (run.center != null) {
            v[RedGatePayload.I_CX] = run.center.getX();
            v[RedGatePayload.I_CZ] = run.center.getZ();
        }
        v[RedGatePayload.I_RADIUS] = RedWorld.EDGE;
        v[RedGatePayload.I_EVENT] = event;
        v[RedGatePayload.I_CAMPS] = run.campsCleared();
        v[RedGatePayload.I_CAMPS_TOTAL] = run.camps.size();
        v[RedGatePayload.I_SEAL_LEFT] = run.state == RedGate.State.OPEN ? (int) Math.max(0, run.sealAt - p.level().getGameTime()) : 0;
        return new RedGatePayload(v, bossName(run));
    }

    /** Someone near the red gate in the world. */
    static RedGatePayload outside(RedGate run, GateBlockEntity gate, int event) {
        int[] v = new int[RedGatePayload.SIZE];
        v[RedGatePayload.I_MODE] = RedGatePayload.OUTSIDE;
        v[RedGatePayload.I_PHASE] = run.state.ordinal();
        v[RedGatePayload.I_RANK] = run.rank.ordinal();
        v[RedGatePayload.I_INSIDE_RANK] = run.inside.ordinal();
        v[RedGatePayload.I_DAY] = run.day();
        v[RedGatePayload.I_TIME] = run.timeOfDay();
        v[RedGatePayload.I_RATE] = RedGateConfig.timeRate();
        v[RedGatePayload.I_PARTY] = run.insideNow.size();
        v[RedGatePayload.I_RELEASE_DAY] = RedGateConfig.releaseDay();
        v[RedGatePayload.I_CX] = gate.getBlockPos().getX();
        v[RedGatePayload.I_CZ] = gate.getBlockPos().getZ();
        v[RedGatePayload.I_EVENT] = event;
        v[RedGatePayload.I_SEAL_LEFT] = run.state == RedGate.State.OPEN && gate.getLevel() != null
                ? (int) Math.max(0, run.sealAt - gate.getLevel().getGameTime()) : 0;
        return new RedGatePayload(v, bossName(run));
    }

    /** Back home (or fallen): the card that sums it up. Hides the HUD. */
    static RedGatePayload summary(RedGate run, int event, long xp) {
        int[] v = new int[RedGatePayload.SIZE];
        v[RedGatePayload.I_MODE] = RedGatePayload.NONE;
        v[RedGatePayload.I_EVENT] = event;
        v[RedGatePayload.I_RANK] = run.rank.ordinal();
        v[RedGatePayload.I_INSIDE_RANK] = run.inside.ordinal();
        v[RedGatePayload.I_DAY] = run.day();
        v[RedGatePayload.I_TIME] = run.timeOfDay();
        v[RedGatePayload.I_KILLS] = run.kills;
        v[RedGatePayload.I_CAMPS] = run.campsCleared();
        v[RedGatePayload.I_CAMPS_TOTAL] = run.camps.size();
        v[RedGatePayload.I_XP] = (int) Math.min(Integer.MAX_VALUE, xp);
        return new RedGatePayload(v, bossName(run));
    }

    /** Where the HUD's marker points: its master (or its keep), or once the way out is open, the nearest way out. */
    @Nullable
    private static BlockPos target(MinecraftServer server, RedGate run, ServerPlayer p) {
        if (run.state == RedGate.State.RELEASED) {
            BlockPos best = run.exit;
            if (run.keepExit != null && (best == null || run.keepExit.distSqr(p.blockPosition()) < best.distSqr(p.blockPosition()))) best = run.keepExit;
            return best;
        }
        ServerLevel level = RedWorld.level(server);
        if (level != null && run.boss != null && level.getEntity(run.boss) instanceof GateBossEntity boss && boss.isAlive()) return boss.blockPosition();
        if (run.keep != null) return RedWorldBuilder.bossSpot(run);
        return null;
    }

    private static void event(ServerPlayer p, RedGate run, int event) {
        PacketDistributor.sendToPlayer(p, inside(p.server, run, p, event));
    }

    // ================================================================ helpers

    public static Component dungeonName() {
        return Component.translatable("redgate.beyondsolo.world");
    }

    static Component label(RedGate run) {
        return Component.translatable("redgate.beyondsolo.label", run.rank.displayName());
    }

    static Component bossName(RedGate run) {
        return Component.translatable("redgate.beyondsolo.boss", run.bossKind.name(run.inside));
    }

    /** XP for clearing it: a gate of its inside rank's, times the red gate multiplier. */
    static long clearXp(RedGate run) {
        return Math.round(GateTheme.of(run.inside).clearXp() * RedGateConfig.rewardMultiplier());
    }

    /** The red gate a player is in (or was last in), if any. */
    @Nullable
    static RedGate runOf(MinecraftServer server, Player p) {
        CompoundTag tag = p.getPersistentData();
        return tag.hasUUID(IN_RED_TAG) ? RedGateData.get(server).get(tag.getUUID(IN_RED_TAG)) : null;
    }

    @Nullable
    static ServerPlayer player(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        return p != null ? p : KNOWN.get(id);
    }

    /** The hunters of a red gate who are in its patch now, alive and online. */
    static List<ServerPlayer> insidePlayers(MinecraftServer server, ServerLevel level, RedGate run) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : run.insideNow) {
            ServerPlayer p = player(server, id);
            if (p == null || !p.isAlive() || p.serverLevel() != level || RedWorld.slotAt(level, p.blockPosition()) != run.slot) continue;
            out.add(p);
        }
        return out;
    }

    private static boolean transitTo(UUID gate) {
        for (Transit t : TRANSITS.values()) if (t.gate().equals(gate)) return true;
        return false;
    }

    /** The red gate standing in the world, if its chunk is loaded. */
    @Nullable
    static GateBlockEntity worldGate(MinecraftServer server, RedGate run) {
        GateInstance g = GateData.get(server).get(run.gate);
        if (g == null) return null;
        ServerLevel world = server.getLevel(g.gateDimension());
        if (world == null || !world.isLoaded(g.gatePos())) return null;
        return world.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate ? gate : null;
    }

    private static double nearest(List<ServerPlayer> players, BlockPos pos) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            double dx = p.getX() - (pos.getX() + 0.5), dz = p.getZ() - (pos.getZ() + 0.5);
            best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
        }
        return best;
    }

    @Nullable
    private static ServerPlayer closest(List<ServerPlayer> players, BlockPos pos) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            double d = p.distanceToSqr(Vec3.atCenterOf(pos));
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** A spot to stand at or near {@code p}: up out of anything solid, then down onto the ground. */
    static BlockPos stand(ServerLevel level, BlockPos p) {
        BlockPos q = p;
        for (int i = 0; i < 12 && !(open(level, q) && open(level, q.above())); i++) q = q.above();
        for (int i = 0; i < 12 && open(level, q.below()); i++) q = q.below();
        return q;
    }

    private static boolean open(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty();
    }

    // ================================================================ for other systems

    /** True while a red gate holds this gate (its hunters are in it, or it is letting them go): its break clock waits. */
    public static boolean holding(GateInstance g) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return false;
        RedGate run = RedGateData.get(server).get(g.id());
        return run != null && run.holding();
    }

    /** True while a player is in a red gate or on their way in or out (no penalty zone, trials or surprise quests then). */
    public static boolean occupied(Player p) {
        if (TRANSITS.containsKey(p.getUUID())) return true;
        if (!(p instanceof ServerPlayer sp)) return false;
        RedGate run = runOf(sp.server, sp);
        return run != null && run.state != RedGate.State.ENDED && run.insideNow.contains(p.getUUID());
    }

    /** A player's chill (0..{@link #MAX_CHILL}). */
    public static int chill(Player p) {
        return CHILL.getOrDefault(p.getUUID(), 0);
    }

    // ================================================================ commands and tests

    /** Builds the whole patch now (commands, tests). */
    static void buildNow(MinecraftServer server, RedGate run) {
        ServerLevel level = RedWorld.level(server);
        if (level == null || run.built) return;
        BUILDERS.computeIfAbsent(run.gate, k -> new RedWorldBuilder(run)).finish(level);
        BUILDERS.remove(run.gate);
        RedGateData.get(server).setDirty();
    }

    /** Turns a gate red now with {@code p} the first through it, straight into the breach (commands, tests). */
    @Nullable
    static RedGate startNow(ServerPlayer p, GateBlockEntity gate, GateInstance g) {
        RedGateData data = RedGateData.get(p.server);
        if (data.get(g.id()) != null || RedWorld.level(p.server) == null) return null;
        data.force(g.id(), true);
        RedGate run = start(p.server, gate, g, p.getUUID());
        run.party.add(p.getUUID());
        return arrive(p, run) ? run : null;
    }

    /** Lets a player out of the red gate they are in at once, nothing paid (commands). */
    public static boolean leaveNow(ServerPlayer p) {
        RedGate run = runOf(p.server, p);
        if (run == null || !run.insideNow.contains(p.getUUID())) return false;
        run.insideNow.remove(p.getUUID());
        failQuest(p, run, QuestEvent.FailReason.ABANDONED);
        sendHome(p, run);
        RedGateData.get(p.server).setDirty();
        if (run.insideNow.isEmpty() && !transitTo(run.gate) && run.state != RedGate.State.ENDED) end(p.server, run);
        return true;
    }

    static void setChill(Player p, int chill) {
        CHILL.put(p.getUUID(), Mth.clamp(chill, 0, MAX_CHILL));
    }

    /** Moves a red gate on: night, dawn, a day, a raid, the weather, the seal, its release, its master's fall, or a place. */
    static boolean jump(ServerPlayer p, String to, int n) {
        RedGate run = runOf(p.server, p);
        ServerLevel level = RedWorld.level(p.server);
        if (run == null || run.state == RedGate.State.ENDED || level == null) return false;
        long dayStart = (run.day() - 1) * 24000L;
        int rate = RedGateConfig.timeRate();
        switch (to) {
            case "night" -> run.insideTime = (run.timeOfDay() >= RedWorld.NIGHT_START ? dayStart + 24000L : dayStart) + RedWorld.NIGHT_START - rate;
            case "dawn" -> run.insideTime = (run.timeOfDay() >= RedWorld.NIGHT_END ? dayStart + 24000L : dayStart) + RedWorld.NIGHT_END - rate;
            case "day" -> {
                int day = Math.max(1, n);
                run.insideTime = (day - 1) * 24000L + RedWorld.START_TIME;
                run.nightAnnounced = day - 1;
                run.dawnAnnounced = day;
                run.weatherRolled = day;
            }
            case "time" -> run.insideTime = dayStart + Mth.clamp(n, 0, 23999);
            case "raid" -> {
                if (raid(level, run, insidePlayers(p.server, level, run)) == 0) return false;
            }
            case "blizzard" -> {
                run.blizzardFrom = run.insideTime;
                run.blizzardUntil = run.insideTime + (n > 0 ? n * 20L * rate : 6000);
                event(p, run, RedGatePayload.EV_BLIZZARD);
            }
            case "calm" -> {
                run.blizzardUntil = run.insideTime;
                event(p, run, RedGatePayload.EV_CALM);
            }
            case "seal" -> run.sealAt = level.getGameTime();
            case "release" -> release(p.server, level, run, insidePlayers(p.server, level, run));
            case "build" -> buildNow(p.server, run);
            case "clear" -> {
                buildNow(p.server, run);
                BlockPos spot = RedWorldBuilder.bossSpot(run);
                level.getChunk(spot);
                // (a master spawned in a chunk nobody is near can't be looked up, but it can still fall)
                GateBossEntity boss = run.boss != null && level.getEntity(run.boss) instanceof GateBossEntity b ? b : null;
                if (boss == null && !run.bossSpawned) boss = spawnMaster(level, run, spot);
                if (boss == null) return false;
                boss.kill();
            }
            case "chill" -> setChill(p, n);
            case "breach", "keep", "camp", "boss" -> {
                buildNow(p.server, run);
                BlockPos at = switch (to) {
                    case "keep" -> run.keep.relative(RedWorldBuilder.keepFront(run), 30);
                    case "boss" -> RedWorldBuilder.bossSpot(run).relative(RedWorldBuilder.keepFront(run), 12);
                    case "camp" -> {
                        BlockPos best = null;
                        for (RedGate.Camp c : run.camps) if (!c.cleared && (best == null || c.pos.distSqr(p.blockPosition()) < best.distSqr(p.blockPosition()))) best = c.pos;
                        yield best == null ? null : best.relative(RedWorldBuilder.front(run).getOpposite(), 14);
                    }
                    default -> run.center;
                };
                if (at == null) return false;
                level.getChunk(at);
                BlockPos ground = stand(level, new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ()));
                BlockPos look = to.equals("breach") ? run.keep : to.equals("camp") ? at.relative(RedWorldBuilder.front(run), 14) : run.keep;
                float yaw = (float) (Mth.atan2(look.getZ() - ground.getZ(), look.getX() - ground.getX()) * Mth.RAD_TO_DEG) - 90f;
                p.teleportTo(level, ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, yaw, 0f);
                if (p.distanceToSqr(Vec3.atBottomCenterOf(ground)) > 4) p.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, yaw, 0f);
            }
            default -> {
                return false;
            }
        }
        RedGateData.get(p.server).setDirty();
        event(p, run, RedGatePayload.EV_NONE);
        return true;
    }

    /** One line about a player's red gate (the status command). */
    static String status(MinecraftServer server, RedGate run) {
        return "Red gate " + run.gate.toString().substring(0, 8) + ": " + run.state.name().toLowerCase() + ", " + run.rank.name() + "-rank (inside "
                + run.inside.name() + "), master " + run.bossKind.id + (run.cleared ? " (fallen)" : run.bossAwake ? " (awake)" : "")
                + ", day " + run.day() + " " + run.timeOfDay() + (run.night() ? " night" : "") + (run.blizzard() ? " blizzard" : "")
                + ", built " + run.built + ", camps " + run.campsCleared() + "/" + run.camps.size() + ", kills " + run.kills
                + ", inside " + run.insideNow.size() + "/" + run.party.size() + ", slot " + run.slot;
    }

    private RedGates() {}
}
