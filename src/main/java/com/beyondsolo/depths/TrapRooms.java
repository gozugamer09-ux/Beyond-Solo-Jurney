package com.beyondsolo.depths;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyFx;
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
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trap rooms ({@link TrapKind}). A trap springs when someone steps into its room: the System calls it out and it goes
 * to work on whoever is inside, every few seconds, each strike marked on the floor first:
 * <ul>
 *   <li>spikes burst up under the party's feet (and somewhere else);</li>
 *   <li>vents set in the floor blast the gate's element up in columns (burn, venom or crimson buildup);</li>
 *   <li>the roof comes down in marked spots;</li>
 *   <li>a lockdown seals every way out with walls of red light and sends two waves of the gate's creatures, then lets
 *       the party go (and leaves a little treasure).</li>
 * </ul>
 * The first three die with their trap core ({@link TrapCoreBlock}): {@link #CORE_HITS} strikes shatter it. A core can
 * be broken before its trap is sprung, by someone who found it first (Pulse Scan shows cores). Strikes hurt the cave's
 * creatures too.
 */
public final class TrapRooms {
    public static final int CORE_HITS = 4;
    public static final int STRIKE_WARNING = 22;
    /** A lockdown with nobody left inside stands down after this long and springs again on the next step in. */
    private static final int LOCKDOWN_GRACE = 20 * 20;
    private static final Map<Long, Integer> CORE_DAMAGE = new HashMap<>();
    private static final Map<UUID, Long> HIT_COOLDOWN = new HashMap<>();
    /** Lockdowns: when the next wave comes (game time), and when the room was last seen empty. */
    private static final Map<String, Long> NEXT_WAVE = new HashMap<>();
    private static final Map<String, Long> EMPTY_SINCE = new HashMap<>();

    private static String key(GateInstance g, int room) {
        return g.id() + "#" + room;
    }

    /** Every half second for a cave with a party in it. */
    static void tick(ServerLevel level, GateInstance g, List<ServerPlayer> players, long tick) {
        GateDepths d = g.depths();
        int beat = (int) (tick / 10) + g.slot() * 3;
        for (var e : d.traps(g).entrySet()) {
            int index = e.getKey();
            TrapKind kind = e.getValue();
            if (d.trapDone(index)) continue;
            DungeonLayout.Room room = g.layout().rooms().get(index);
            List<ServerPlayer> in = new ArrayList<>();
            for (ServerPlayer p : players) if (!exempt(p) && HiddenRooms.inside(g, room, p.position(), 1.5f)) in.add(p);
            if (!d.trapTriggered(index)) {
                if (!in.isEmpty()) spring(level, g, room, kind, in);
                continue;
            }
            switch (kind) {
                case LOCKDOWN -> lockdown(level, g, room, in);
                case SPIKES -> {
                    if (beat % 4 != 0) continue;
                    for (ServerPlayer p : in) {
                        spike(level, g, WeatherService.spotNear(level, p.position(), 1.2));
                        spike(level, g, WeatherService.spotNear(level, Vec3.atCenterOf(g.world(room.center())), room.reach() * 0.6));
                    }
                }
                case VENTS -> {
                    if (beat % 5 != 0 || in.isEmpty()) continue;
                    int floor = g.world(new BlockPos(0, room.floorY(), 0)).getY();
                    for (int[] v : DepthsBuild.ventSpots(g, room)) {
                        BlockPos at = DepthsBuild.floorAt(level, v[0], v[1], floor);
                        boolean near = false;
                        for (ServerPlayer p : in) near |= p.position().distanceTo(Vec3.atBottomCenterOf(at)) < 9;
                        if (near) vent(level, g, Vec3.atBottomCenterOf(at));
                    }
                }
                case ROCKFALL -> {
                    if (beat % 3 != 0) continue;
                    int tier = g.rank().tier();
                    for (ServerPlayer p : in) {
                        Vec3 at = WeatherService.spotNear(level, p.position(), 1.5);
                        if (at != null) WeatherService.rock(level, g, at, 1.6f, 4f + 2f * tier);
                    }
                }
            }
        }
    }

    private static boolean exempt(Player p) {
        return p.isCreative() || p.isSpectator();
    }

    /** Tells a player's screen about the nearest trap already going off in their cave (they came in, or back, after it sprang). */
    static void resync(ServerPlayer p, GateInstance g) {
        GateDepths d = g.depths();
        DungeonLayout.Room nearest = null;
        TrapKind kind = null;
        double best = Double.MAX_VALUE;
        for (Map.Entry<Integer, TrapKind> e : d.traps(g).entrySet()) {
            if (!d.trapActive(e.getKey())) continue;
            DungeonLayout.Room room = g.layout().rooms().get(e.getKey());
            double dist = p.position().distanceToSqr(Vec3.atCenterOf(g.world(room.center())));
            if (dist < best) {
                best = dist;
                nearest = room;
                kind = e.getValue();
            }
        }
        if (nearest == null) return;
        BlockPos c = g.world(nearest.center());
        int wave = kind == TrapKind.LOCKDOWN ? d.wave(nearest.index()) : 0;
        PacketDistributor.sendToPlayer(p, new DepthsPayload(DepthsPayload.TRAP_ACTIVE, kind.ordinal() + 16 * wave, nearest.reach(),
                c.getX() + 0.5f, c.getY(), c.getZ() + 0.5f, Component.empty(), Component.empty()));
    }

    /** Someone stepped in: the trap springs. */
    static void spring(ServerLevel level, GateInstance g, DungeonLayout.Room room, TrapKind kind, List<ServerPlayer> in) {
        GateDepths d = g.depths();
        d.triggerTrap(room.index());
        GateData.get(level.getServer()).setDirty();
        BlockPos c = g.world(room.center());
        level.playSound(null, c, SoundEvents.TRIAL_SPAWNER_DETECT_PLAYER, SoundSource.HOSTILE, 1.5f, 0.6f);
        level.playSound(null, c, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.2f, 0.5f);
        for (ServerPlayer p : GateService.playersIn(level, g)) {
            PacketDistributor.sendToPlayer(p, new DepthsPayload(DepthsPayload.TRAP, kind.ordinal(), room.reach(),
                    c.getX() + 0.5f, c.getY(), c.getZ() + 0.5f, kind.title(), kind.hint()));
        }
        if (kind == TrapKind.LOCKDOWN) {
            seal(level, g, room);
            d.setWave(room.index(), 1);
            wave(level, g, room, 1);
        }
    }

    // ------------------------------------------------------------------ strikes

    /** Spikes: marked, then up they come. */
    static void spike(ServerLevel level, GateInstance g, @Nullable Vec3 at) {
        if (at == null) return;
        float damage = 3f + 2f * g.rank().tier();
        EnemyFx.telegraphCircle(level, at, 1.3f, STRIKE_WARNING, TrapKind.SPIKES.color);
        level.playSound(null, BlockPos.containing(at), SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA, SoundSource.HOSTILE, 1f, 0.6f);
        ServerScheduler.schedule(STRIKE_WARNING, () -> {
            EnemyFx.pillar(level, at, TrapKind.SPIKES.color, 1.1f, 1.8f);
            NearFx.particles(level, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.POINTED_DRIPSTONE.defaultBlockState()),
                    at.x, at.y + 0.6, at.z, 20, 0.4, 0.5, 0.4, 0.1);
            level.playSound(null, BlockPos.containing(at), SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.HOSTILE, 1.4f, 0.7f);
            level.playSound(null, BlockPos.containing(at), SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 1f, 0.6f);
            for (LivingEntity e : caught(level, at, 1.3f)) {
                e.hurt(level.damageSources().stalagmite(), damage);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.45, 0));
                e.hurtMarked = true;
            }
        });
    }

    /** A vent: it hisses, then blasts the gate's element up in a column. */
    static void vent(ServerLevel level, GateInstance g, Vec3 at) {
        DamageElement element = g.element();
        int color = DamageElement.FIRE == element ? 0xFF8A3A : DamageElement.POISON == element ? 0x8CE850 : 0xE0303C;
        float damage = 2f + 1.5f * g.rank().tier();
        float buildup = 14f + 3f * g.rank().tier();
        EnemyFx.telegraphCircle(level, at, 1.4f, STRIKE_WARNING, color);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8f, 0.6f);
        NearFx.particles(level, ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.2, at.z, 6, 0.2, 0.1, 0.2, 0.02);
        ServerScheduler.schedule(STRIKE_WARNING, () -> {
            EnemyFx.pillar(level, at, color, 1.4f, 4.5f);
            level.playSound(null, BlockPos.containing(at), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.1f, 0.5f);
            NearFx.particles(level, element == DamageElement.FIRE ? ParticleTypes.FLAME : ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 1, at.z, 24, 0.3, 1.2, 0.3, 0.04);
            for (LivingEntity e : caught(level, at, 1.4f)) {
                e.hurt(level.damageSources().magic(), damage);
                Statuses.apply(e, element, buildup, null);
            }
        });
    }

    private static List<LivingEntity> caught(ServerLevel level, Vec3 at, float radius) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1, 2.5, radius + 1),
                e -> e.isAlive() && !(e instanceof Player p && exempt(p)) && WeatherService.horizontal(e.position(), at) <= radius + e.getBbWidth() / 2
                        && Math.abs(e.getY() - at.y) < 2);
    }

    // ------------------------------------------------------------------ the lockdown

    /** Walls of light across every way out of the room. */
    static void seal(ServerLevel level, GateInstance g, DungeonLayout.Room room) {
        for (DungeonLayout.Tunnel t : g.layout().tunnelsOf(room.index())) {
            DepthsBuild.Mouth m = DepthsBuild.mouth(g, t, room);
            DepthsBuild.fillSlab(level, m, m.radius() + 1.1f, 0.5f, at -> DepthsModule.TRAP_WARD.get().defaultBlockState(),
                    s -> s.isAir() || s.is(Blocks.LIGHT));
            level.playSound(null, BlockPos.containing(m.center()), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.2f, 0.7f);
        }
    }

    /** The walls of light fall away. */
    static void unseal(ServerLevel level, GateInstance g, DungeonLayout.Room room) {
        for (DungeonLayout.Tunnel t : g.layout().tunnelsOf(room.index())) {
            DepthsBuild.Mouth m = DepthsBuild.mouth(g, t, room);
            int reach = Mth.ceil(m.radius() + 3);
            BlockPos c = BlockPos.containing(m.center());
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-reach, -reach, -reach), c.offset(reach, reach, reach))) {
                if (level.getBlockState(p).is(DepthsModule.TRAP_WARD.get())) {
                    level.setBlock(p, Blocks.CAVE_AIR.defaultBlockState(), 3);
                    if (level.random.nextInt(3) == 0) NearFx.particles(level, ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0.02);
                }
            }
            level.playSound(null, c, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.2f, 1.1f);
        }
    }

    /** A wave of the gate's creatures out of the floor around the room's middle. */
    static void wave(ServerLevel level, GateInstance g, DungeonLayout.Room room, int wave) {
        EnemyRoster.Mix mix = g.mix();
        BlockPos c = DepthsBuild.floorCenter(level, g, room);
        int count = g.theme().packSize + wave;
        for (int i = 0; i < count; i++) {
            float a = i * Mth.TWO_PI / count + level.random.nextFloat() * 0.5f;
            float k = 0.3f + level.random.nextFloat() * 0.3f;
            BlockPos at = DepthsBuild.floorAt(level, c.getX() + Math.round(Mth.cos(a) * room.rx() * k), c.getZ() + Math.round(Mth.sin(a) * room.rz() * k), c.getY());
            EntityType<?> type = mix.pick(level.random);
            Mob m = Enemies.spawn(level, g.rank(), type, at, Enemies.roll(g.rank(), mix, type, level.random, i == 0 && wave == 2, true), g.theme().ordinal());
            GateService.claim(m, g);
        }
        level.playSound(null, c, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.9f, 1.3f);
        for (ServerPlayer p : GateService.playersIn(level, g)) {
            PacketDistributor.sendToPlayer(p, new DepthsPayload(DepthsPayload.WAVE, wave, 2, c.getX() + 0.5f, c.getY(), c.getZ() + 0.5f,
                    Component.translatable("depths.beyondsolo.trap.wave", wave, 2), Component.empty()));
        }
    }

    private static void lockdown(ServerLevel level, GateInstance g, DungeonLayout.Room room, List<ServerPlayer> in) {
        GateDepths d = g.depths();
        String key = key(g, room.index());
        long now = level.getGameTime();
        if (in.isEmpty()) {
            // everyone fell or got out somehow: after a while the room stands down, and springs again on the next step in
            long since = EMPTY_SINCE.computeIfAbsent(key, k -> now);
            if (now - since > LOCKDOWN_GRACE) {
                unseal(level, g, room);
                d.untrigger(room.index());
                d.setWave(room.index(), 0);
                NEXT_WAVE.remove(key);
                EMPTY_SINCE.remove(key);
                GateData.get(level.getServer()).setDirty();
            }
            return;
        }
        EMPTY_SINCE.remove(key);
        if (HiddenRooms.guardians(level, g, room) > 0) return;
        Long next = NEXT_WAVE.get(key);
        if (d.wave(room.index()) < 2) {
            if (next == null) {
                NEXT_WAVE.put(key, now + 40);
            } else if (now >= next) {
                NEXT_WAVE.remove(key);
                d.setWave(room.index(), 2);
                wave(level, g, room, 2);
            }
            return;
        }
        finish(level, g, room.index(), null);
    }

    // ------------------------------------------------------------------ the core

    /** A strike on a trap core. */
    public static void hitCore(ServerLevel level, BlockPos pos, ServerPlayer p) {
        long now = level.getGameTime();
        if (HIT_COOLDOWN.getOrDefault(p.getUUID(), -100L) > now) return;
        HIT_COOLDOWN.put(p.getUUID(), now + 5);
        int hits = CORE_DAMAGE.merge(pos.asLong(), 1, Integer::sum);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.4f, 0.6f + 0.2f * hits);
        level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8f, 0.7f + 0.1f * hits);
        DepthsFxPayload fx = new DepthsFxPayload(DepthsFxPayload.CORE_HIT, pos.getX() + 0.5f, pos.getY() + 0.6f, pos.getZ() + 0.5f, hits, CORE_HITS, TrapKind.LOCKDOWN.color);
        for (ServerPlayer near : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(32))) PacketDistributor.sendToPlayer(near, fx);
        if (hits >= CORE_HITS) coreBroken(level, pos, p, true);
    }

    /** A core is broken (struck to pieces, or broken outright in creative). */
    public static void coreBroken(ServerLevel level, BlockPos pos, @Nullable ServerPlayer by) {
        coreBroken(level, pos, by, false);
    }

    private static void coreBroken(ServerLevel level, BlockPos pos, @Nullable ServerPlayer by, boolean remove) {
        CORE_DAMAGE.remove(pos.asLong());
        if (remove) level.setBlock(pos, Blocks.CAVE_AIR.defaultBlockState(), 3);
        DepthsFxPayload fx = new DepthsFxPayload(DepthsFxPayload.SHATTER, pos.getX() + 0.5f, pos.getY() + 0.6f, pos.getZ() + 0.5f, 0, 0, TrapKind.LOCKDOWN.color);
        for (ServerPlayer near : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(48))) PacketDistributor.sendToPlayer(near, fx);
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.4f, 0.6f);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.6f, 0.5f);
        GateInstance g = HiddenRooms.gateAt(level, pos);
        if (g == null) return;
        Integer room = roomOf(g, pos);
        if (room != null) finish(level, g, room, by);
    }

    /** The trap room a core belongs to (the trap room it stands in). */
    @Nullable
    static Integer roomOf(GateInstance g, BlockPos pos) {
        Integer best = null;
        double bestD = Double.MAX_VALUE;
        for (var e : g.depths().traps(g).entrySet()) {
            DungeonLayout.Room room = g.layout().rooms().get(e.getKey());
            double dist = g.world(room.center()).distSqr(pos);
            if (dist < bestD && dist < (room.reach() + 6) * (room.reach() + 6)) {
                best = e.getKey();
                bestD = dist;
            }
        }
        return best;
    }

    /** The trap is beaten: everyone in the cave hears, the party gets its XP, a lockdown lets go and leaves treasure. */
    static void finish(ServerLevel level, GateInstance g, int index, @Nullable ServerPlayer by) {
        GateDepths d = g.depths();
        if (d.trapDone(index)) return;
        TrapKind kind = d.trap(g, index);
        if (kind == null) return;
        boolean sprung = d.trapTriggered(index);
        d.triggerTrap(index);
        d.finishTrap(index);
        GateData.get(level.getServer()).setDirty();
        DungeonLayout.Room room = g.layout().rooms().get(index);
        BlockPos c = DepthsBuild.floorCenter(level, g, room);
        if (kind == TrapKind.LOCKDOWN) {
            unseal(level, g, room);
            int tier = g.rank().tier();
            ItemEntity loot = new ItemEntity(level, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, new ItemStack(Items.EMERALD, 3 + 2 * tier));
            loot.setDeltaMovement(0, 0.3, 0);
            level.addFreshEntity(loot);
            if (level.random.nextFloat() < 0.35f) {
                ItemEntity extra = new ItemEntity(level, c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, com.beyondsolo.gate.GateLoot.weapon(tier, level.random));
                extra.setDeltaMovement(0.1, 0.35, 0);
                extra.setGlowingTag(true);
                level.addFreshEntity(extra);
            }
        }
        level.playSound(null, c, SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER, SoundSource.BLOCKS, 1.4f, 0.8f);
        long xp = (kind == TrapKind.LOCKDOWN ? 70L : 40L) * (1 + g.rank().tier());
        String line = sprung ? "depths.beyondsolo.trap.done" : "depths.beyondsolo.trap.defused";
        for (ServerPlayer p : GateService.playersIn(level, g)) {
            PacketDistributor.sendToPlayer(p, new DepthsPayload(DepthsPayload.TRAP_DONE, kind.ordinal(), index, c.getX() + 0.5f, c.getY(), c.getZ() + 0.5f,
                    Component.translatable(line), kind.title()));
            if (!SystemAPI.hasSystem(p)) continue;
            SystemAPI.grantXp(p, xp, BeyondSolo.id("trap_cleared"));
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD, Component.translatable(line + ".title", kind.title()),
                    Component.translatable("depths.beyondsolo.trap.reward", xp)));
        }
    }

    static void forget(UUID player) {
        HIT_COOLDOWN.remove(player);
    }

    static void clear() {
        CORE_DAMAGE.clear();
        HIT_COOLDOWN.clear();
        NEXT_WAVE.clear();
        EMPTY_SINCE.clear();
    }

    private TrapRooms() {}
}
