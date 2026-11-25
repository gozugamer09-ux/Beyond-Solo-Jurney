package com.beyondsolo.breaks;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateSpawner;
import com.beyondsolo.gate.RewardCacheBlock;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.network.BreakPayload;
import com.beyondsolo.network.GatePayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.ParticleGuard;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dungeon breaks. An uncleared gate does not hold forever: its clock starts as soon as it stands in the world
 * ({@code gates.breakMinutes}, give or take a fifth), and for the last third of it the gate comes apart in plain
 * sight: cracks, a red leak, and the collapse timer on the HUD of anyone near. Its last minute is counted down only in
 * front of someone, with the System warning everyone around; then it breaks. The ground shakes, its monsters pour out
 * in waves (the gate's own creatures, {@link GateInstance#mix}), and with the third wave its boss comes through. A
 * break stays by its gate (its monsters are leashed to it, {@link #LEASH} blocks) and lasts until its boss falls: then
 * the gate collapses, what is left of its monsters crumbles to ash, and the boss leaves a reward cache, XP and gold for
 * everyone who stood against it, and the quest the System gave them at the break is done.
 *
 * <p>Fair to the player: the clock waits while anyone is inside the gate's cave (or on their way in), a gate never
 * breaks with nobody near to see it (left alone it holds at its last minute, and breaks a minute after someone comes
 * back), nothing breaks on peaceful, and trials, keyed gates and cleared gates never break. A broken gate lets nobody
 * in: its cave has spilled out.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class DungeonBreaks {
    /** How often (ticks) the clocks are looked at. */
    public static final int CHECK = 20;
    /** How near (blocks, across the ground) a player must be for a gate to count as watched, and to hear of it. */
    public static final int WATCH = 160;
    /** The last minute before a break, counted down only while someone watches. */
    public static final int IMMINENT_TICKS = 20 * 60;
    /** How far (blocks) a break's monsters may roam from their gate before they are pulled back. */
    public static final int LEASH = 40;
    /** Ticks between waves. */
    public static final int WAVE_TICKS = 20 * 20;
    /** The wave the boss comes through with. */
    public static final int BOSS_WAVE = 3;
    /** How near (blocks) players share a suppressed break's reward. */
    public static final int SHARE = 64;
    /** A break pays a quarter more than clearing the gate would have. */
    public static final float REWARD = 1.25f;
    /** Persistent data on a break's monsters (and its boss): the id of the gate they came out of. */
    public static final String BREAK_TAG = "beyondsolo_break";

    public static final String QUEST_TEMPLATE = BeyondSolo.id("break/suppress").toString();
    public static final String MOB_KEY = BeyondSolo.id("break_mob").toString();
    public static final String BOSS_KEY = BeyondSolo.id("break_boss").toString();

    /** Checks in a row a break's boss has been nowhere to be found (it comes through again after 30). */
    private static final Map<UUID, Integer> MISSING = new HashMap<>();

    // ================================================================ the clock

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK != 11) return;
        tick(server);
    }

    static void tick(MinecraftServer server) {
        GateData data = GateData.get(server);
        ServerLevel dungeon = GateService.dungeon(server);
        boolean on = Config.GATE_BREAK_MINUTES.get() > 0;
        for (GateInstance g : List.copyOf(data.all())) {
            BreakState b = g.breakState();
            ServerLevel world = server.getLevel(g.gateDimension());
            if (world == null) continue;
            if (b.broken()) {
                brokenTick(world, data, g, watched(world, g.gatePos()));
                continue;
            }
            if (!on || !canBreak(g)) continue;
            // a gate taken out of the world by a command (players can't break one) has nothing left to break
            if (world.isLoaded(g.gatePos()) && !(world.getBlockEntity(g.gatePos()) instanceof GateBlockEntity)) continue;
            // a red gate holds its clock too, for as long as it holds its hunters
            boolean inside = (dungeon != null && (!GateService.playersIn(dungeon, g).isEmpty() || GateService.inTransitTo(g.id())))
                    || com.beyondsolo.redgate.RedGates.holding(g);
            step(world, data, g, world.getGameTime(), inside, watched(world, g.gatePos()));
            status(world, g, 0);
        }
    }

    /** True for a gate that can still break: open, uncleared, not a trial or a keyed door, and not broken yet. */
    public static boolean canBreak(GateInstance g) {
        return g.state() == GateInstance.State.OPEN && !g.isTrial() && !g.isKeyed() && !g.slotFreed()
                && !g.gateDimension().equals(GateService.DIMENSION) && !g.breakState().hasBroken();
    }

    /** How long a new clock runs (ticks): the configured minutes, give or take a fifth. */
    public static long clockTicks(RandomSource r) {
        return Math.max(CHECK, Math.round(Config.GATE_BREAK_MINUTES.get() * 1200.0 * (0.8 + 0.4 * r.nextDouble())));
    }

    /**
     * One look at a gate's clock. {@code inside}: someone is in its cave (or on their way in), so it waits;
     * {@code watched}: someone is near enough to see it break (see {@link #watched}).
     */
    public static void step(ServerLevel world, GateData data, GateInstance g, long now, boolean inside, boolean watched) {
        BreakState b = g.breakState();
        if (!b.clockStarted()) b.start(now, clockTicks(world.random));
        if (inside) {
            // the hunters inside hold it together: its clock waits for them
            b.setBreakAt(b.breakAt() + CHECK);
            data.setDirty();
            sync(world, g);
            return;
        }
        long left = b.breakAt() - now;
        if (b.phase() == BreakState.Phase.IMMINENT) {
            if (!watched) {
                // nobody to see it: its last minute waits for someone
                b.setBreakAt(b.breakAt() + CHECK);
            } else if (left <= 0) {
                breakOpen(world, data, g, now);
                return;
            }
        } else {
            if (!watched && left < IMMINENT_TICKS) {
                // left alone it holds at its last minute
                b.setBreakAt(now + IMMINENT_TICKS);
                left = IMMINENT_TICKS;
            }
            if (watched && left <= IMMINENT_TICKS) {
                b.markUnstable(now);
                b.setPhase(BreakState.Phase.IMMINENT);
                announce(world, g, BreakState.Phase.IMMINENT, now);
            } else if (b.phase() == BreakState.Phase.STABLE && left <= b.length() / 3) {
                b.markUnstable(now);
                b.setPhase(BreakState.Phase.UNSTABLE);
                announce(world, g, BreakState.Phase.UNSTABLE, now);
            }
        }
        data.setDirty();
        sync(world, g);
    }

    /** True if someone is near enough to see the gate break, in a part of the world that is running. */
    public static boolean watched(ServerLevel world, BlockPos pos) {
        if (world.getDifficulty() == Difficulty.PEACEFUL || !world.isLoaded(pos) || !world.isPositionEntityTicking(pos)) return false;
        return !near(world, pos, WATCH).isEmpty();
    }

    /** Living players within {@code range} blocks across the ground of a spot (spectators left out). */
    public static List<ServerPlayer> near(ServerLevel world, BlockPos pos, int range) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : world.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (flat(p.position(), pos) <= range) out.add(p);
        }
        return out;
    }

    /** Distance across the ground from a point to a block's middle. */
    public static double flat(Vec3 from, BlockPos to) {
        double dx = from.x - (to.getX() + 0.5), dz = from.z - (to.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Tells the gate block what its clients should see of the break. */
    static void sync(ServerLevel world, GateInstance g) {
        if (!world.isLoaded(g.gatePos()) || !(world.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate)) return;
        BreakState b = g.breakState();
        int phase = switch (b.phase()) {
            case UNSTABLE -> GateBlockEntity.UNSTABLE;
            case IMMINENT -> GateBlockEntity.IMMINENT;
            case BROKEN, SUPPRESSED -> GateBlockEntity.BROKEN;
            default -> GateBlockEntity.HOLDING;
        };
        gate.setBreak(phase, time(b.unstableAt()), time(b.breakAt()), time(b.brokeAt()));
    }

    private static long time(long t) {
        return t == BreakState.NEVER ? -1 : t;
    }

    /** The collapse timer (or the break's count) for everyone near a gate that is coming apart or broken. */
    static void status(ServerLevel world, GateInstance g, int monstersOut) {
        BreakState b = g.breakState();
        int phase = switch (b.phase()) {
            case UNSTABLE -> 1;
            case IMMINENT -> 2;
            case BROKEN -> 3;
            default -> 0;
        };
        if (phase == 0) return;
        int a = phase == 3 ? monstersOut : (int) Math.min(Integer.MAX_VALUE, b.left(world.getGameTime()));
        BreakPayload msg = BreakPayload.of(BreakPayload.STATUS, g.rank().ordinal(), phase, g.gatePos(), a, b.bossOut() ? 1 : 0);
        for (ServerPlayer p : near(world, g.gatePos(), WATCH)) PacketDistributor.sendToPlayer(p, msg);
    }

    /** The System's warning to everyone near: the gate is coming apart, or about to break. */
    private static void announce(ServerLevel world, GateInstance g, BreakState.Phase phase, long now) {
        BlockPos pos = g.gatePos();
        long left = g.breakState().left(now);
        for (ServerPlayer p : near(world, pos, WATCH)) {
            long d = Math.round(flat(p.position(), pos));
            Component dir = GateSpawner.direction(p.position(), pos);
            if (phase == BreakState.Phase.UNSTABLE) {
                long minutes = Math.max(1, Math.round(left / 1200.0));
                Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                        Component.translatable("notify.beyondsolo.break.unstable.title"),
                        Component.translatable("notify.beyondsolo.break.unstable.body", g.rank().displayName(), d, dir, minutes)));
            } else {
                long seconds = Math.max(1, (left + 19) / 20);
                Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                        Component.translatable("notify.beyondsolo.break.imminent.title"),
                        Component.translatable("notify.beyondsolo.break.imminent.body", g.rank().displayName(), d, dir, seconds)));
            }
        }
        if (phase == BreakState.Phase.UNSTABLE) {
            world.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 2.5f, 0.5f);
        } else {
            world.playSound(null, pos, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 3f, 0.6f);
            world.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.5f, 0.5f);
        }
    }

    // ================================================================ the break

    /** The gate breaks now: the shock, the banner, the quest, and the first wave. */
    public static void breakOpen(ServerLevel world, GateData data, GateInstance g, long now) {
        BreakState b = g.breakState();
        if (b.hasBroken()) return;
        if (!b.clockStarted()) b.start(now, 1);
        b.markUnstable(now);
        b.broke(now);
        data.setDirty();
        sync(world, g);
        BlockPos pos = g.gatePos();
        world.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4f, 0.45f);
        world.playSound(null, pos, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 4f, 0.6f);
        world.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 2f, 0.5f);
        Component title = Component.translatable("break.beyondsolo.banner.break");
        Component sub = Component.translatable("break.beyondsolo.banner.break_sub", g.rank().displayName(), g.dungeonName());
        int reach = WATCH + 40;
        for (ServerPlayer p : near(world, pos, reach)) {
            double d = flat(p.position(), pos);
            int felt = (int) Math.round(100 * Mth.clamp(1 - d / reach, 0.15, 1));
            PacketDistributor.sendToPlayer(p, new BreakPayload(BreakPayload.BREAK, g.rank().ordinal(), 3, pos, felt, 0, title, sub));
            if (d <= 96) giveQuest(p, g);
        }
        spill(world, data, g, now, waveSize(g), false);
        com.beyondsolo.news.HunterNews.breakOpened(world.getServer(), g);
    }

    /** Monsters in one wave. */
    public static int waveSize(GateInstance g) {
        return 3 + (g.rank().tier() + 1) / 2;
    }

    /** Waves a break sends out in all (the boss's among them). */
    public static int waves(GateInstance g) {
        return 3 + g.rank().tier();
    }

    /** The most of its monsters a break keeps out at once (its boss not counted). */
    public static int cap(GateInstance g) {
        return 6 + 2 * g.rank().tier();
    }

    /** Every second of a break, while someone is near: its monsters kept close, the next wave, its boss. */
    static void brokenTick(ServerLevel world, GateData data, GateInstance g, boolean watched) {
        BreakState b = g.breakState();
        List<Mob> out = monsters(world, g);
        if (watched) {
            long now = world.getGameTime();
            leash(world, g, out);
            Entity boss = b.boss() == null ? null : world.getEntity(b.boss());
            int crowd = 0;
            for (Mob m : out) if (!(m instanceof GateBossEntity)) crowd++;
            boolean due = now - b.lastWave() >= WAVE_TICKS;
            boolean bossDue = !b.bossOut() && b.waves() >= BOSS_WAVE - 1;
            if (due && (bossDue || (b.waves() < waves(g) && crowd < cap(g)))) {
                spill(world, data, g, now, Math.min(waveSize(g), Math.max(0, cap(g) - crowd)), bossDue);
            } else if (b.bossOut() && boss == null && entitiesLoaded(world, g.gatePos(), LEASH * 2)) {
                // lost without falling (it never died, so the break never ended): it comes through again
                int n = MISSING.merge(g.id(), 1, Integer::sum);
                if (n >= 30) {
                    MISSING.remove(g.id());
                    bossOut(world, data, g);
                }
            } else {
                MISSING.remove(g.id());
            }
        }
        status(world, g, out.size());
    }

    /** A wave comes out of the gate: {@code count} of its creatures, and its boss with them if {@code withBoss}. */
    public static void spill(ServerLevel world, GateData data, GateInstance g, long now, int count, boolean withBoss) {
        BreakState b = g.breakState();
        RandomSource r = world.random;
        EnemyRoster.Mix mix = g.mix();
        boolean late = b.waves() >= 2;
        int made = 0;
        for (int i = 0; i < count; i++) {
            BlockPos at = spot(world, g, r);
            EntityType<?> type = mix.pick(r);
            Mob m = Enemies.spawn(world, g.rank(), type, at, Enemies.roll(g.rank(), mix, type, r, i == 0 && b.waves() == 0, late), g.theme().ordinal());
            if (m == null) continue;
            mark(m, g);
            made++;
        }
        if (withBoss) bossOut(world, data, g);
        b.wave(now);
        data.setDirty();
        BreakPayload msg = BreakPayload.of(BreakPayload.SPILL, g.rank().ordinal(), 3, g.gatePos(), made, 0);
        for (ServerPlayer p : near(world, g.gatePos(), WATCH)) PacketDistributor.sendToPlayer(p, msg);
        world.playSound(null, g.gatePos(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.6f, 1.1f);
    }

    /** The boss comes through: up close, its own entrance (as in its cave); further off, a banner. */
    @Nullable
    public static GateBossEntity bossOut(ServerLevel world, GateData data, GateInstance g) {
        GateBossEntity boss = ModEntities.GATE_BOSS.get().create(world);
        if (boss == null) return null;
        BlockPos at = spot(world, g, world.random);
        float yaw = (float) (Mth.atan2(at.getZ() + 0.5 - (g.gatePos().getZ() + 0.5), at.getX() + 0.5 - (g.gatePos().getX() + 0.5)) * Mth.RAD_TO_DEG) - 90f;
        boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        boss.setYHeadRot(yaw);
        boss.setYBodyRot(yaw);
        boss.configure(g.rank(), g.bossKind(), g.bossAscended());
        boss.setCustomName(g.bossName());
        boss.setCustomNameVisible(false);
        mark(boss, g);
        world.addFreshEntity(boss);
        boss.wake();
        g.breakState().setBoss(boss.getUUID());
        data.setDirty();
        Component subtitle = Component.translatable("break.beyondsolo.boss_subtitle", g.rank().displayName());
        for (ServerPlayer p : near(world, g.gatePos(), WATCH)) {
            if (p.distanceTo(boss) < 40) {
                PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.BOSS_INTRO, g.theme().ordinal(), boss.getId(), 0, g.bossName(), subtitle));
            } else {
                PacketDistributor.sendToPlayer(p, new BreakPayload(BreakPayload.BOSS_OUT, g.rank().ordinal(), 3, g.gatePos(), boss.getId(), 0,
                        g.bossName(), Component.translatable("break.beyondsolo.banner.boss_out", g.rank().displayName())));
            }
        }
        return boss;
    }

    /** Marks a creature as one of this gate's break, and keeps it to the gate. */
    static void mark(Mob m, GateInstance g) {
        m.getPersistentData().putUUID(BREAK_TAG, g.id());
        m.restrictTo(g.gatePos(), LEASH);
    }

    /**
     * A creature something of a break brought into the world (its boss's adds, a splitting creature's halves) belongs to
     * the break too: kept to the gate, counted, and gone with the rest when the break ends.
     */
    public static void adopt(Entity parent, @Nullable Mob child) {
        if (child == null || !parent.getPersistentData().hasUUID(BREAK_TAG) || !(child.level() instanceof ServerLevel level)) return;
        GateInstance g = GateData.get(level.getServer()).get(parent.getPersistentData().getUUID(BREAK_TAG));
        if (g != null && g.breakState().broken()) mark(child, g);
    }

    /** True if {@code e} came out of a dungeon break, any gate's. */
    public static boolean fromBreak(Entity e) {
        return e.getPersistentData().hasUUID(BREAK_TAG);
    }

    /** True if {@code e} came out of the break of {@code gate}. */
    public static boolean isOf(Entity e, UUID gate) {
        CompoundTag t = e.getPersistentData();
        return t.hasUUID(BREAK_TAG) && t.getUUID(BREAK_TAG).equals(gate);
    }

    /** A break's creatures (its boss among them) alive in the loaded world around its gate. */
    public static List<Mob> monsters(ServerLevel world, GateInstance g) {
        AABB box = new AABB(g.gatePos()).inflate(LEASH * 2 + 16, 64, LEASH * 2 + 16);
        return world.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && isOf(m, g.id()));
    }

    /** Where one of its creatures steps out: the ground a few steps from either face of the gate. */
    static BlockPos spot(ServerLevel world, GateInstance g, RandomSource r) {
        BlockPos gate = g.gatePos();
        Direction facing = world.getBlockEntity(gate) instanceof GateBlockEntity be ? be.facing() : Direction.NORTH;
        if (r.nextBoolean()) facing = facing.getOpposite();
        Direction side = facing.getClockWise();
        for (int attempt = 0; attempt < 8; attempt++) {
            double ahead = 2.5 + r.nextDouble() * 4, across = (r.nextDouble() - 0.5) * 7;
            int x = Mth.floor(gate.getX() + 0.5 + facing.getStepX() * ahead + side.getStepX() * across);
            int z = Mth.floor(gate.getZ() + 0.5 + facing.getStepZ() * ahead + side.getStepZ() * across);
            BlockPos ground = standable(world, new BlockPos(x, gate.getY() + 4, z), 10);
            if (ground != null) return ground;
        }
        return gate.relative(facing, 2);
    }

    /** Going down from {@code from}, the first spot with solid ground under it and room to stand. */
    @Nullable
    private static BlockPos standable(ServerLevel world, BlockPos from, int depth) {
        BlockPos p = from;
        for (int i = 0; i < depth; i++, p = p.below()) {
            BlockPos below = p.below();
            if (world.getBlockState(below).isFaceSturdy(world, below, Direction.UP) && free(world, p) && free(world, p.above())
                    && world.getFluidState(p).isEmpty()) return p;
        }
        return null;
    }

    private static boolean free(ServerLevel world, BlockPos p) {
        return world.getBlockState(p).getCollisionShape(world, p).isEmpty();
    }

    /** Creatures that strayed too far are walked back to the gate, or, far out, pulled back through the rift. */
    static void leash(ServerLevel world, GateInstance g, List<Mob> out) {
        Vec3 home = Vec3.atBottomCenterOf(g.gatePos());
        for (Mob m : out) {
            if (!m.hasRestriction()) m.restrictTo(g.gatePos(), LEASH);
            double d = flat(m.position(), g.gatePos());
            if (d > LEASH * 1.6) {
                ParticleGuard.send(world, ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + m.getBbHeight() * 0.5, m.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
                BlockPos at = spot(world, g, world.random);
                m.setTarget(null);
                m.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                m.getNavigation().stop();
            } else if (d > LEASH) {
                LivingEntity t = m.getTarget();
                if (t == null || flat(t.position(), g.gatePos()) > LEASH + 8) {
                    m.setTarget(null);
                    m.getNavigation().moveTo(home.x, home.y, home.z, 1.15);
                }
            }
        }
    }

    /** True if every chunk within {@code r} blocks of a spot has its creatures loaded. */
    private static boolean entitiesLoaded(ServerLevel world, BlockPos c, int r) {
        for (int x = (c.getX() - r) >> 4; x <= (c.getX() + r) >> 4; x++) {
            for (int z = (c.getZ() - r) >> 4; z <= (c.getZ() + r) >> 4; z++) {
                if (!world.areEntitiesLoaded(ChunkPos.asLong(x, z))) return false;
            }
        }
        return true;
    }

    // ================================================================ the end of a break

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (!(e.level() instanceof ServerLevel level) || !e.getPersistentData().hasUUID(BREAK_TAG)) return;
        GateData data = GateData.get(level.getServer());
        GateInstance g = data.get(e.getPersistentData().getUUID(BREAK_TAG));
        if (g == null || !g.breakState().broken()) return;
        if (e instanceof GateBossEntity boss) {
            if (boss.getUUID().equals(g.breakState().boss())) suppress(level, data, g, boss);
            return;
        }
        g.breakState().countKill();
        data.setDirty();
        if (event.getSource().getEntity() instanceof ServerPlayer killer) QuestEngine.progress(killer, MOB_KEY, 1);
    }

    /**
     * Its boss fell: the break is over. The gate collapses (its cave is wiped like any closed gate's), what is left of
     * its monsters crumbles to ash, nearest the boss first, and everyone near is paid: a reward cache where the boss
     * fell, XP, gold, and the break's quest done.
     */
    public static void suppress(ServerLevel level, GateData data, GateInstance g, @Nullable LivingEntity boss) {
        BreakState b = g.breakState();
        if (!b.broken()) return;
        long now = level.getGameTime();
        b.setPhase(BreakState.Phase.SUPPRESSED);
        b.setBoss(null);
        g.setState(GateInstance.State.CLOSED);
        g.setBossDefeated(true);
        g.setClearedAt(now);
        data.setDirty();
        MISSING.remove(g.id());
        sync(level, g);
        if (level.isLoaded(g.gatePos()) && level.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate) GateService.close(level, gate);
        level.playSound(null, g.gatePos(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 3f, 0.4f);

        Vec3 at = boss != null ? boss.position() : Vec3.atBottomCenterOf(g.gatePos());
        List<Mob> left = monsters(level, g);
        left.removeIf(m -> m == boss || m instanceof GateBossEntity);
        left.sort(Comparator.comparingDouble(m -> m.distanceToSqr(at)));
        for (int i = 0; i < left.size(); i++) {
            Mob m = left.get(i);
            ServerScheduler.schedule(12 + i * 3, () -> crumble(level, m));
        }

        BlockPos fell = BlockPos.containing(at);
        Direction facing = Direction.getNearest(g.gatePos().getX() - at.x, 0, g.gatePos().getZ() - at.z);
        if (facing.getAxis().isVertical()) facing = Direction.NORTH;
        Direction toGate = facing;
        ServerScheduler.schedule(boss != null ? GateBossEntity.DEATH_TICKS - 6 : 10, () -> dropCache(level, g, fell, toGate));

        long xp = Math.round(g.theme().clearXp() * (double) REWARD);
        Component title = Component.translatable("break.beyondsolo.banner.suppressed");
        Component sub = Component.translatable("break.beyondsolo.banner.suppressed_sub", g.rank().displayName());
        Set<ServerPlayer> paid = new LinkedHashSet<>(near(level, fell, SHARE));
        paid.addAll(near(level, g.gatePos(), SHARE));
        for (ServerPlayer p : paid) reward(p, g, xp, title, sub);
        // merit with the association, and its contracts on the break done
        com.beyondsolo.association.Association.breakSuppressed(level, g, paid);
        com.beyondsolo.news.HunterNews.breakSuppressed(level.getServer(), g, paid);
    }

    /** One hunter's share of a suppressed break. */
    static void reward(ServerPlayer p, GateInstance g, long xp, Component title, Component sub) {
        // the boss is the end of it: whatever monsters were left no longer count against the quest
        QuestEngine.progress(p, MOB_KEY, Short.MAX_VALUE);
        QuestEngine.progress(p, BOSS_KEY, 1);
        QuestEngine.progress(p, GateService.CLEAR_KEY, 1);
        boolean system = SystemAPI.isAwakened(p);
        if (system) SystemAPI.grantXp(p, xp, BeyondSolo.id("dungeon_break"));
        com.beyondsolo.economy.EconomyHooks.gateCleared(p, g.rank(), REWARD);
        PacketDistributor.sendToPlayer(p, new BreakPayload(BreakPayload.SUPPRESSED, g.rank().ordinal(), 4, g.gatePos(), system ? (int) Math.min(Integer.MAX_VALUE, xp) : 0, 0, title, sub));
    }

    /** One of a break's monsters turns to ash (no loot, no XP: the break is over). */
    static void crumble(ServerLevel level, Mob m) {
        if (m.isRemoved() || !m.isAlive()) return;
        BreakPayload msg = BreakPayload.of(BreakPayload.CRUMBLE, 0, 4, m.blockPosition(), m.getId(), Math.round(m.getBbHeight() * 10));
        for (ServerPlayer p : near(level, m.blockPosition(), 96)) PacketDistributor.sendToPlayer(p, msg);
        double y = m.getY() + m.getBbHeight() * 0.5;
        ParticleGuard.send(level, ParticleTypes.LARGE_SMOKE, m.getX(), y, m.getZ(), 8, m.getBbWidth() * 0.3, m.getBbHeight() * 0.3, m.getBbWidth() * 0.3, 0.01);
        ParticleGuard.send(level, ParticleTypes.SOUL, m.getX(), y, m.getZ(), 4, m.getBbWidth() * 0.3, m.getBbHeight() * 0.3, m.getBbWidth() * 0.3, 0.02);
        level.playSound(null, m.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.2f, 0.7f);
        level.playSound(null, m.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1f, 0.6f);
        m.discard();
    }

    /** The boss's reward cache, on the ground where it fell (never in place of a block that is there). */
    private static void dropCache(ServerLevel level, GateInstance g, BlockPos fell, Direction facing) {
        BlockPos p = null;
        for (int dy = 0; dy < 8 && p == null; dy++) {
            BlockPos up = fell.above(dy);
            BlockState here = level.getBlockState(up);
            if ((here.isAir() || here.canBeReplaced()) && here.getFluidState().isEmpty() && !level.getBlockState(up.below()).isAir()) p = up;
        }
        if (p == null) p = standable(level, fell.above(4), 12);
        if (p == null) return;
        level.setBlock(p, ModBlocks.REWARD_CACHE.get().defaultBlockState().setValue(RewardCacheBlock.FACING, facing), 3);
        if (level.getBlockEntity(p) instanceof RewardCacheBlockEntity cache) cache.setup(g.rank(), g.bossKind(), g.bossAscended(), level.getGameTime());
    }

    // ================================================================ the quest

    /**
     * The System's quest at the break, for a hunter near it: forced (an emergency: fleeing or dying fails it) when the
     * break is no more than a rank above them, offered otherwise.
     */
    static void giveQuest(ServerPlayer p, GateInstance g) {
        if (!SystemAPI.isAwakened(p)) return;
        for (Quest q : QuestEngine.data(p).active()) {
            if (QUEST_TEMPLATE.equals(q.template()) && g.id().toString().equals(q.extra().getString("gate"))) return;
        }
        int tier = g.rank().tier();
        boolean forced = tier <= SystemAPI.get(p).rank().tier() + 1;
        CompoundTag extra = new CompoundTag();
        extra.putString("gate", g.id().toString());
        QuestBuilder b = QuestBuilder.of(QUEST_TEMPLATE, forced ? QuestCategory.EMERGENCY : QuestCategory.SURPRISE, tier, SystemAPI.get(p).level())
                .title(Component.translatable("quest.beyondsolo.dungeon_break", g.rank().displayName()))
                .description(Component.translatable("quest.beyondsolo.dungeon_break.desc", g.rank().displayName(), g.bossName()))
                .custom(MOB_KEY, Math.min(12, waveSize(g) * 2), Component.translatable("quest_objective.beyondsolo.break_mobs"))
                .custom(BOSS_KEY, 1, Component.translatable("quest_objective.beyondsolo.break_boss", g.bossName()))
                .timeLimit(20 * 60 * 30)
                .fixed().xp(200L * (1 + tier)).statPoints(tier >= 3 ? 2 : 1)
                .extra(extra);
        if (forced) b.area(p, g.gatePos(), 96, 160);
        Quest q = b.build();
        if (forced) QuestEngine.start(p, q);
        else QuestEngine.offer(p, q, QuestEngine.OFFER_TICKS);
    }

    // ================================================================ loading and stopping

    /** A break's creature loaded back in after its break ended out of sight crumbled with the rest. */
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)) return;
        Entity e = event.getEntity();
        if (!e.getPersistentData().hasUUID(BREAK_TAG)) return;
        GateInstance g = GateData.get(level.getServer()).get(e.getPersistentData().getUUID(BREAK_TAG));
        boolean replaced = e instanceof GateBossEntity && g != null && !e.getUUID().equals(g.breakState().boss());
        if (g == null || !g.breakState().broken() || replaced) event.setCanceled(true);
        else if (e instanceof Mob m) m.restrictTo(g.gatePos(), LEASH);
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        MISSING.clear();
    }

    private DungeonBreaks() {}
}
