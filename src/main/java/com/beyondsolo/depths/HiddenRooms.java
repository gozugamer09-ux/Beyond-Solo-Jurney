package com.beyondsolo.depths;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateWiper;
import com.beyondsolo.network.DepthsFxPayload;
import com.beyondsolo.network.DepthsPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hidden chambers. Some gates hide a chamber off one of their later caverns, behind a plug of rock that passes for the
 * cave's own wall ({@link HiddenSealBlock}). Ways to find it: a faint mote of violet light seeping out of the wall now
 * and then; Pulse Scan, which shows it through the rock ({@link #scan}); and knocking (hitting the wall), which sounds
 * hollow there: the {@link #KNOCKS}th knock brings it down ({@link #open}), with the System's HIDDEN DUNGEON moment.
 *
 * <p>Inside ({@link HiddenKind}): a vault's cache, a rank above the gate; a shrine's blessing for whoever steps in
 * (strength, regeneration and resistance for five minutes) and its cache; or a lair whose guardians, a rank above the
 * gate, wake when someone steps in, and whose cache comes down from above once the last of them falls.</p>
 */
public final class HiddenRooms {
    public static final int KNOCKS = 3;
    public static final int BLESSING_TICKS = 20 * 60 * 5;
    public static final int VIOLET = 0xB58CFF;
    private static final Map<UUID, Long> KNOCK_COOLDOWN = new HashMap<>();
    /** Who the shrine has blessed, by gate. */
    private static final Map<UUID, Set<UUID>> BLESSED = new HashMap<>();

    @Nullable
    static GateInstance gateAt(ServerLevel level, BlockPos pos) {
        if (!GateService.isDungeon(level)) return null;
        return GateData.get(level.getServer()).bySlotPos(pos);
    }

    // ------------------------------------------------------------------ finding it

    /** A knock on a hidden wall. */
    public static void knock(ServerPlayer p, BlockPos pos) {
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        if (KNOCK_COOLDOWN.getOrDefault(p.getUUID(), -100L) > now) return;
        KNOCK_COOLDOWN.put(p.getUUID(), now + 6);
        GateInstance g = gateAt(level, pos);
        BlockState state = level.getBlockState(pos);
        int knocks;
        if (g == null) {
            // a hidden wall outside any cave (placed by hand): it gives at once
            openAt(level, pos, p);
            return;
        }
        GateDepths d = g.depths();
        if (d.hiddenOpen()) {
            crumble(level, connected(level, pos), pos);
            return;
        }
        knocks = ++d.knocks;
        float pitch = 0.55f + 0.12f * knocks;
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 1.4f, pitch);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 1.1f, 0.6f + 0.1f * knocks);
        if (state.getBlock() instanceof HiddenSealBlock) {
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                    state.getValue(HiddenSealBlock.LOOK).mimic.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
        }
        DepthsFxPayload fx = new DepthsFxPayload(DepthsFxPayload.KNOCK, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, knocks, KNOCKS, VIOLET);
        for (ServerPlayer near : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(32))) PacketDistributor.sendToPlayer(near, fx);
        if (knocks >= KNOCKS) {
            open(level, g, p, pos);
        } else if (knocks == 1) {
            p.displayClientMessage(Component.translatable("depths.beyondsolo.hidden.hollow"), true);
        }
    }

    /** Opens the hidden wall {@code pos} belongs to: its cave's, or (outside any cave) just that wall. */
    public static void openAt(ServerLevel level, BlockPos pos, @Nullable ServerPlayer by) {
        GateInstance g = gateAt(level, pos);
        if (g != null && g.depths().hidden(g) != null) open(level, g, by, pos);
        else crumble(level, connected(level, pos), pos);
    }

    /**
     * The wall comes down: block by block from where it was struck, light pours out of the opening, and everyone in
     * the cave gets the System's moment. A small reward of XP for the find.
     */
    public static void open(ServerLevel level, GateInstance g, @Nullable ServerPlayer by, @Nullable BlockPos struck) {
        GateDepths d = g.depths();
        HiddenKind kind = d.hidden(g);
        if (kind == null || d.hiddenOpen()) return;
        d.setHiddenOpen(true);
        GateData.get(level.getServer()).setDirty();
        DepthsBuild.Mouth m = DepthsBuild.sealMouth(g);
        BlockPos from = struck != null ? struck : m != null ? BlockPos.containing(m.center()) : null;
        List<BlockPos> wall = new ArrayList<>();
        if (m != null) {
            // every block of the plug (it may not all touch where it was struck)
            int reach = Mth.ceil(m.radius() + 3);
            BlockPos c = BlockPos.containing(m.center());
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-reach, -reach, -reach), c.offset(reach, reach, reach))) {
                if (level.getBlockState(p).getBlock() instanceof HiddenSealBlock) wall.add(p.immutable());
            }
        }
        if (struck != null) for (BlockPos p : connected(level, struck)) if (!wall.contains(p)) wall.add(p);
        if (from != null) crumble(level, wall, from);
        Vec3 at = m != null ? m.center() : from != null ? Vec3.atCenterOf(from) : Vec3.ZERO;
        Vec3 dir = m != null ? m.dir() : Vec3.ZERO;
        DepthsFxPayload fx = new DepthsFxPayload(DepthsFxPayload.OPEN, (float) at.x, (float) at.y, (float) at.z,
                (float) Math.atan2(dir.z, dir.x), m != null ? m.radius() : 2.5f, kind.color);
        List<ServerPlayer> party = GateService.playersIn(level, g);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(BlockPos.containing(at)).inflate(64))) {
            if (!party.contains(p)) party.add(p);
        }
        level.playSound(null, BlockPos.containing(at), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.2f, 0.6f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2f, 0.5f);
        long xp = 60L * (1 + g.rank().tier());
        for (ServerPlayer p : party) {
            PacketDistributor.sendToPlayer(p, fx);
            PacketDistributor.sendToPlayer(p, new DepthsPayload(DepthsPayload.HIDDEN, kind.ordinal(), g.rank().ordinal(),
                    (float) at.x, (float) at.y, (float) at.z,
                    Component.translatable("depths.beyondsolo.hidden.found"), kind.title()));
            if (!SystemAPI.hasSystem(p)) continue;
            SystemAPI.grantXp(p, xp, BeyondSolo.id("hidden_found"));
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.QUEST,
                    Component.translatable("depths.beyondsolo.hidden.found.title", kind.title()), kind.description()));
        }
        d.knocks = 0;
    }

    /** Takes a wall down a block at a time, nearest {@code from} first. */
    static void crumble(ServerLevel level, List<BlockPos> wall, BlockPos from) {
        if (wall.isEmpty()) return;
        List<BlockPos> order = new ArrayList<>(wall);
        order.sort(Comparator.comparingDouble(p -> p.distSqr(from)));
        int per = Math.max(1, order.size() / 10);
        for (int i = 0; i < order.size(); i++) {
            BlockPos p = order.get(i);
            int delay = 1 + i / per;
            ServerScheduler.schedule(delay, () -> {
                BlockState s = level.getBlockState(p);
                if (!(s.getBlock() instanceof HiddenSealBlock)) return;
                level.levelEvent(2001, p, Block.getId(s.getValue(HiddenSealBlock.LOOK).mimic.defaultBlockState()));
                level.setBlock(p, net.minecraft.world.level.block.Blocks.CAVE_AIR.defaultBlockState(), 3);
            });
        }
    }

    /** The hidden-wall blocks joined to {@code start} (at most 400). */
    static List<BlockPos> connected(ServerLevel level, BlockPos start) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> todo = new ArrayDeque<>();
        todo.add(start);
        while (!todo.isEmpty() && out.size() < 400) {
            BlockPos p = todo.poll();
            if (!seen.add(p) || !(level.getBlockState(p).getBlock() instanceof HiddenSealBlock)) continue;
            out.add(p);
            for (Direction d : Direction.values()) todo.add(p.relative(d));
        }
        return out;
    }

    /**
     * Pulse Scan: what is hidden within its reach shows through the rock for a while (the client draws the outlines),
     * and the System says so if anything is there.
     */
    public static void scan(ServerPlayer p, float radius) {
        ServerLevel level = p.serverLevel();
        GateInstance g = gateAt(level, p.blockPosition());
        if (g == null) return;
        GateDepths d = g.depths();
        int found = 0;
        DepthsBuild.Mouth m = d.hidden(g) != null && !d.hiddenOpen() ? DepthsBuild.sealMouth(g) : null;
        if (m != null && m.center().distanceTo(p.position()) <= radius + 6) found++;
        for (var e : d.traps(g).entrySet()) {
            if (!e.getValue().hasCore() || d.trapDone(e.getKey())) continue;
            DungeonLayout.Room room = g.layout().rooms().get(e.getKey());
            if (Vec3.atCenterOf(g.world(room.center())).distanceTo(p.position()) <= radius + room.reach()) found++;
        }
        PacketDistributor.sendToPlayer(p, new DepthsFxPayload(DepthsFxPayload.REVEAL, (float) p.getX(), (float) p.getY() + 1, (float) p.getZ(),
                radius + 6, 14, VIOLET));
        if (found > 0) {
            p.displayClientMessage(Component.translatable("depths.beyondsolo.scan.found"), false);
            level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 0.7f);
        }
    }

    // ------------------------------------------------------------------ inside

    /** True if {@code p} stands inside the room. */
    static boolean inside(GateInstance g, DungeonLayout.Room room, Vec3 p, float margin) {
        BlockPos c = g.world(room.center());
        double dx = p.x - (c.getX() + 0.5), dz = p.z - (c.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz) < room.reach() - margin && Math.abs(p.y - c.getY()) < room.ry() + 3;
    }

    /** Every half second for a cave with a party in it: stepping into the chamber, the shrine's blessing, the lair. */
    static void tick(ServerLevel level, GateInstance g, List<ServerPlayer> players) {
        GateDepths d = g.depths();
        HiddenKind kind = d.hidden(g);
        DungeonLayout.Room room = g.layout().hidden();
        if (kind == null || room == null || !d.hiddenOpen()) return;
        List<ServerPlayer> in = new ArrayList<>();
        for (ServerPlayer p : players) if (inside(g, room, p.position(), 1)) in.add(p);
        if (!d.hiddenEntered() && !in.isEmpty()) enter(level, g, room, kind, in);
        if (kind == HiddenKind.SHRINE) for (ServerPlayer p : in) bless(level, g, p);
        if (kind == HiddenKind.LAIR && d.hiddenEntered() && !d.hiddenDone() && guardians(level, g, room) == 0) lairCleared(level, g, room);
    }

    /** The first step into the chamber. */
    static void enter(ServerLevel level, GateInstance g, DungeonLayout.Room room, HiddenKind kind, List<ServerPlayer> in) {
        GateDepths d = g.depths();
        d.setHiddenEntered(true);
        GateData.get(level.getServer()).setDirty();
        BlockPos c = DepthsBuild.floorCenter(level, g, room);
        String line = switch (kind) {
            case VAULT -> "depths.beyondsolo.hidden.enter.vault";
            case SHRINE -> "depths.beyondsolo.hidden.enter.shrine";
            case LAIR -> "depths.beyondsolo.hidden.enter.lair";
        };
        for (ServerPlayer p : GateService.playersIn(level, g)) {
            PacketDistributor.sendToPlayer(p, new DepthsPayload(DepthsPayload.HIDDEN_ENTER, kind.ordinal(), g.rank().ordinal(),
                    c.getX() + 0.5f, c.getY(), c.getZ() + 0.5f, kind.title(), Component.translatable(line)));
        }
        if (kind == HiddenKind.LAIR) {
            spawnGuardians(level, g, room);
            level.playSound(null, c, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.2f, 1.1f);
        } else {
            level.playSound(null, c, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5f, 1.2f);
        }
    }

    /** The lair's guardians: the gate's creatures, a rank above it, led by an elite or a champion. */
    static void spawnGuardians(ServerLevel level, GateInstance g, DungeonLayout.Room room) {
        PlayerRank rank = DepthsBuild.cacheRank(g, HiddenKind.LAIR);
        EnemyRoster.Mix mix = g.mix();
        BlockPos c = DepthsBuild.floorCenter(level, g, room);
        int count = g.theme().packSize + 2;
        for (int i = 0; i < count; i++) {
            float a = i * Mth.TWO_PI / count + level.random.nextFloat() * 0.4f;
            float k = 0.25f + level.random.nextFloat() * 0.35f;
            BlockPos at = DepthsBuild.floorAt(level, c.getX() + Math.round(Mth.cos(a) * room.rx() * k), c.getZ() + Math.round(Mth.sin(a) * room.rz() * k), c.getY());
            EntityType<?> type = mix.pick(level.random);
            Mob m = Enemies.spawn(level, rank, type, at, Enemies.roll(rank, mix, type, level.random, i == 0, true), g.theme().ordinal());
            if (m != null) m.getPersistentData().putUUID(GateWiper.GATE_ID_TAG, g.id());
        }
    }

    /** The gate's creatures still standing in the chamber. */
    static int guardians(ServerLevel level, GateInstance g, DungeonLayout.Room room) {
        BlockPos c = g.world(room.center());
        AABB box = new AABB(c).inflate(room.rx() + 2, room.ry() + 3, room.rz() + 2);
        return level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.getPersistentData().hasUUID(GateWiper.GATE_ID_TAG)
                && g.id().equals(m.getPersistentData().getUUID(GateWiper.GATE_ID_TAG))).size();
    }

    /** The last guardian is down: the lair's cache comes down from above. */
    static void lairCleared(ServerLevel level, GateInstance g, DungeonLayout.Room room) {
        GateDepths d = g.depths();
        d.setHiddenDone(true);
        GateData.get(level.getServer()).setDirty();
        BlockPos at = DepthsBuild.floorCenter(level, g, room);
        DepthsBuild.placeCache(level, at, DepthsBuild.cacheRank(g, HiddenKind.LAIR), level.getGameTime());
        level.playSound(null, at, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.4f, 1.2f);
        for (ServerPlayer p : GateService.playersIn(level, g)) {
            if (!SystemAPI.hasSystem(p)) continue;
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD,
                    Component.translatable("depths.beyondsolo.hidden.lair_cleared.title"),
                    Component.translatable("depths.beyondsolo.hidden.lair_cleared.body")));
        }
    }

    /** The shrine's blessing, once a gate for each who steps in. */
    static void bless(ServerLevel level, GateInstance g, ServerPlayer p) {
        Set<UUID> blessed = BLESSED.computeIfAbsent(g.id(), k -> new HashSet<>());
        if (!blessed.add(p.getUUID())) return;
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BLESSING_TICKS, 0, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BLESSING_TICKS, 0, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, BLESSING_TICKS, 0, false, true, true));
        DepthsFxPayload fx = new DepthsFxPayload(DepthsFxPayload.BLESS, (float) p.getX(), (float) p.getY(), (float) p.getZ(), 1, 0, HiddenKind.SHRINE.color);
        for (ServerPlayer near : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(48))) PacketDistributor.sendToPlayer(near, fx);
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 1.4f);
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 0.9f);
        if (SystemAPI.hasSystem(p)) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD,
                    Component.translatable("depths.beyondsolo.hidden.blessing.title"), Component.translatable("depths.beyondsolo.hidden.blessing.body")));
        }
        g.depths().setHiddenDone(true);
    }

    /** True if the shrine of this gate has blessed {@code p}. */
    public static boolean blessed(GateInstance g, ServerPlayer p) {
        Set<UUID> s = BLESSED.get(g.id());
        return s != null && s.contains(p.getUUID());
    }

    static void forget(UUID player) {
        KNOCK_COOLDOWN.remove(player);
    }

    static void clear() {
        KNOCK_COOLDOWN.clear();
        BLESSED.clear();
    }

    private HiddenRooms() {}
}
