package com.beyondsolo.light;

import com.beyondsolo.enemy.Affix;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.monarch.ThroneTrial;
import com.beyondsolo.network.LightFxPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.situation.Foes;
import com.beyondsolo.skill.RulerPowers;
import com.beyondsolo.skill.SkillParticles;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.worldboss.WorldBossFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
 * The Investiture: the vigil a hunter sworn to the light keeps to become a ruler of light. Called by the light
 * ({@link LightAPI#call}) once they have changed jobs and reached {@code light.minLevel}, they raise the Envoy's feather
 * where they stand ({@link EnvoyFeatherItem}): a ring of light opens round them and the Envoy comes down over it.
 * <ol>
 *   <li><b>The light gathers</b> for a few seconds.</li>
 *   <li><b>The dark comes for them</b>: {@link #WAVES} waves of the gates' creatures of their own rank rise round the
 *   ring, each bigger than the last, the last led by a champion. They hold the ring: whoever leaves it for long, logs
 *   out or falls has failed, and may keep it again after {@code light.retryMinutes}.</li>
 *   <li><b>The mantle</b>: the Envoy comes down to them and lays the four mantles before them
 *   ({@code client.light.MantleScreen}); if they don't choose in {@code light.choiceSeconds}, the light takes the one
 *   their class leans to ({@link Mantle#leaning}). Then the Investiture ({@link LightAPI#invest}).</li>
 * </ol>
 * Other hunters may fight beside them; only the one keeping the vigil must hold the ring. Nothing here is saved: a
 * vigil the server stops in the middle of is simply not kept.
 */
public final class Vigil {
    /** The ring's radius. */
    public static final int RADIUS = 11;
    public static final int WAVES = 3;
    /** Ticks the light gathers before the first wave. */
    static final int GATHER_TICKS = 100;
    /** Ticks between waves. */
    static final int BETWEEN_TICKS = 80;
    /** Ticks a hunter may stand outside the ring before the vigil fails. */
    static final int OUTSIDE_TICKS = 100;
    /** Ticks after the mantle is taken before the Envoy goes. */
    static final int FAREWELL_TICKS = 100;
    static final String FOE_TAG = "beyondsolo_vigil";

    enum Stage { GATHER, FIGHT, BETWEEN, CHOOSE, DONE }

    /** A vigil being kept. */
    static final class Watch {
        final UUID player;
        final ServerLevel level;
        final Vec3 center;
        final Mantle leaning;
        Stage stage = Stage.GATHER;
        long stageAt;
        int wave;
        int outside;
        @Nullable UUID envoy;
        final Set<UUID> foes = new HashSet<>();

        Watch(ServerPlayer p, Vec3 center, long now) {
            this.player = p.getUUID();
            this.level = p.serverLevel();
            this.center = center;
            this.leaning = Mantle.leaning(SystemAPI.get(p).playerClass());
            this.stageAt = now;
        }

        long since() {
            return level.getGameTime() - stageAt;
        }

        void stage(Stage s) {
            stage = s;
            stageAt = level.getGameTime();
        }
    }

    private static final Map<UUID, Watch> WATCHES = new HashMap<>();

    // ================================================================ beginning it

    /** The feather raised: the vigil begins where they stand, if the light will have them now. Returns true if it did. */
    public static boolean begin(ServerPlayer p) {
        LightData data = LightAPI.get(p);
        if (!data.sworn()) {
            LightAPI.info(p, "light_feather_cold");
            return false;
        }
        if (data.invested()) {
            LightAPI.info(p, "light_feather_invested", data.mantle().title());
            return false;
        }
        if (WATCHES.containsKey(p.getUUID())) return false;
        if (!LightAPI.eligible(p)) {
            LightAPI.info(p, "light_vigil_waits", LightAPI.minLevel());
            return false;
        }
        long now = p.server.overworld().getGameTime();
        if (data.retryAt() > now) {
            LightAPI.info(p, "light_vigil_retry", Math.max(1, (data.retryAt() - now + 1199) / 1200));
            return false;
        }
        if (p.level().dimension() != Level.OVERWORLD) {
            LightAPI.info(p, "light_vigil_place");
            return false;
        }
        if (!data.called()) LightAPI.call(p);
        LightAPI.vigilTried(p);
        Watch w = new Watch(p, p.position(), p.level().getGameTime());
        WATCHES.put(p.getUUID(), w);
        // the Envoy comes down over the ring
        EnvoyEntity envoy = LightModule.ENVOY.get().create(w.level);
        if (envoy != null) {
            envoy.moveTo(w.center.x, w.center.y + 7, w.center.z, p.getYRot() + 180, 0);
            envoy.setMode(EnvoyEntity.VIGIL);
            envoy.setWard(p.getUUID());
            w.level.addFreshEntity(envoy);
            w.envoy = envoy.getUUID();
            envoy.bless();
        }
        int c = RulerPowers.DAWN_GOLD;
        WorldBossFx.circle(w.level, w.center, RADIUS, GATHER_TICKS, c);
        WorldBossFx.ring(w.level, w.center, 0.5f, RADIUS, 16, c);
        SkillParticles.send(w.level, ParticleTypes.END_ROD, w.center.x, w.center.y + 1, w.center.z, 80, 1, 3, 1, 0.1);
        w.level.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2f, 0.7f);
        w.level.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2f, 1.2f);
        send(p, 0, GATHER_TICKS);
        return true;
    }

    // ================================================================ keeping it

    static void tick(MinecraftServer server) {
        if (WATCHES.isEmpty()) return;
        Iterator<Map.Entry<UUID, Watch>> it = WATCHES.entrySet().iterator();
        List<Runnable> after = new ArrayList<>();
        while (it.hasNext()) {
            Watch w = it.next().getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(w.player);
            if (p == null || !p.isAlive() || p.level() != w.level) {
                it.remove();
                after.add(() -> failed(w, p, "gone"));
                continue;
            }
            if (w.stage == Stage.DONE) {
                if (w.since() >= FAREWELL_TICKS) {
                    it.remove();
                    after.add(() -> envoyLeaves(w));
                }
                continue;
            }
            // they must hold the ring (not while the Envoy lays the mantles before them)
            if (w.stage != Stage.CHOOSE && horizontal(p.position(), w.center) > RADIUS + 1.5) {
                if (w.outside++ == 0) send(p, -2, OUTSIDE_TICKS);
                if (w.outside > OUTSIDE_TICKS) {
                    it.remove();
                    after.add(() -> failed(w, p, "left"));
                    continue;
                }
            } else if (w.outside > 0) {
                w.outside = 0;
                send(p, Math.max(0, w.wave), foesLeft(w));
            }
            if (w.level.getGameTime() % 10 == 0) ring(w);
            switch (w.stage) {
                case GATHER -> {
                    if (w.since() >= GATHER_TICKS) wave(w, p, 1);
                }
                case FIGHT -> {
                    if (w.level.getGameTime() % 10 == 0) {
                        int left = foesLeft(w);
                        if (left == 0) {
                            if (w.wave >= WAVES) investiture(w, p);
                            else {
                                w.stage(Stage.BETWEEN);
                                send(p, w.wave, 0);
                                w.level.playSound(null, BlockPos.containing(w.center), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5f, 1.2f);
                            }
                        } else if (w.level.getGameTime() % 40 == 0) {
                            send(p, w.wave, left);
                            hunt(w, p);
                        }
                    }
                }
                case BETWEEN -> {
                    if (w.since() >= BETWEEN_TICKS) wave(w, p, w.wave + 1);
                }
                case CHOOSE -> {
                    if (w.since() >= LightConfig.choiceSeconds() * 20L) after.add(() -> choose(p, w.leaning));
                }
                default -> {
                }
            }
        }
        after.forEach(Runnable::run);
    }

    /** A wave of the dark rises round the ring: the gates' creatures of the hunter's rank, more each time, the last led by a champion. */
    static void wave(Watch w, ServerPlayer p, int n) {
        w.wave = n;
        w.stage(Stage.FIGHT);
        RandomSource r = w.level.random;
        PlayerRank rank = SystemAPI.get(p).rank();
        EnemyRoster.Mix mix = EnemyRoster.random(rank, r);
        int count = 3 + 2 * n + Math.min(3, w.level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(RADIUS * 2),
                o -> o != p && o.isAlive() && !o.isSpectator()).size());
        BlockPos at = BlockPos.containing(w.center);
        for (int i = 0; i < count; i++) {
            BlockPos spot = Foes.spotAround(w.level, at, RADIUS - 3, RADIUS + 3);
            if (spot == null) continue;
            EntityType<?> type = mix.pick(r);
            boolean champion = n == WAVES && i == 0;
            EnemyTraits traits;
            if (champion) {
                Variant v = Variant.CHAMPION;
                traits = new EnemyTraits(mix.affinityFor(type, r, true), v, Affix.mask(Affix.roll(v.affixes, r)));
            } else {
                traits = Enemies.roll(rank, mix, type, r, i == 0, n > 1);
            }
            Mob m = Enemies.spawn(w.level, rank, type, spot, traits, -1);
            if (m == null) continue;
            m.getPersistentData().putUUID(FOE_TAG, w.player);
            m.setTarget(p);
            w.foes.add(m.getUUID());
            SkillParticles.send(w.level, ParticleTypes.SCULK_SOUL, m.getX(), m.getY() + 0.5, m.getZ(), 10, 0.4, 0.6, 0.4, 0.03);
        }
        w.level.playSound(null, at, SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.4f, 0.6f);
        w.level.playSound(null, at, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.6f, 0.6f);
        if (n == WAVES) w.level.playSound(null, at, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 0.8f);
        send(p, n, w.foes.size());
    }

    /** The creatures of the wave still standing (the ones gone from the world, or lost far away, no longer count). */
    static int foesLeft(Watch w) {
        w.foes.removeIf(id -> {
            Entity e = w.level.getEntity(id);
            return !(e instanceof Mob m) || !m.isAlive() || horizontal(m.position(), w.center) > RADIUS * 4;
        });
        return w.foes.size();
    }

    /** The wave keeps coming for the one keeping the vigil. */
    private static void hunt(Watch w, ServerPlayer p) {
        for (UUID id : w.foes) {
            if (w.level.getEntity(id) instanceof Mob m && m.isAlive() && (m.getTarget() == null || !m.getTarget().isAlive())) m.setTarget(p);
        }
    }

    /** The ring of light on the ground, and the pillar where the Envoy's light comes down. */
    private static void ring(Watch w) {
        int c = RulerPowers.DAWN_GOLD;
        int n = 28;
        long t = w.level.getGameTime();
        for (int i = 0; i < n; i++) {
            double a = i * Mth.TWO_PI / n + t * 0.01;
            SkillParticles.send(w.level, ThroneTrial.dust(c, 1.3f), w.center.x + Math.cos(a) * RADIUS, w.center.y + 0.2,
                    w.center.z + Math.sin(a) * RADIUS, 1, 0, 0.02, 0, 0);
        }
        SkillParticles.send(w.level, ParticleTypes.END_ROD, w.center.x, w.center.y + 3, w.center.z, 3, 0.15, 2.5, 0.15, 0.01);
    }

    // ================================================================ the Investiture

    /** The last wave is down: the Envoy comes down to them and lays the four mantles before them. */
    static void investiture(Watch w, ServerPlayer p) {
        w.stage(Stage.CHOOSE);
        EnvoyEntity envoy = envoy(w);
        if (envoy != null) {
            Vec3 to = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(3)).add(0, 0.8, 0);
            envoy.teleportTo(to.x, to.y, to.z);
            float yaw = (float) (Mth.atan2(p.getZ() - to.z, p.getX() - to.x) * Mth.RAD_TO_DEG) - 90f;
            envoy.setYRot(yaw);
            envoy.setYHeadRot(yaw);
            envoy.setYBodyRot(yaw);
            envoy.bless();
        }
        WorldBossFx.burst(w.level, w.center.add(0, 1, 0), 5f, RulerPowers.DAWN_GOLD);
        w.level.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2f, 0.6f);
        w.level.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 0.8f);
        send(p, WAVES + 1, 0);
        PacketDistributor.sendToPlayer(p, new LightFxPayload(LightFxPayload.CHOOSE, envoy == null ? -1 : envoy.getId(), RulerPowers.DAWN_GOLD,
                w.leaning.ordinal(), LightConfig.choiceSeconds() * 20));
    }

    /** The mantle taken up ({@code client.light.MantleScreen}, or the light's pick when time ran out): the Investiture. */
    public static void choose(ServerPlayer p, Mantle m) {
        Watch w = WATCHES.get(p.getUUID());
        if (w == null || w.stage != Stage.CHOOSE) return;
        w.stage(Stage.DONE);
        LightAPI.invest(p, m);
        int c = m.color();
        EnvoyEntity envoy = envoy(w);
        if (envoy != null) envoy.bless();
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        com.beyondsolo.skill.SkillFx.play(p, com.beyondsolo.network.SkillFxPayload.Kind.PILLAR, c, at, 2.6f, 0);
        com.beyondsolo.skill.SkillFx.play(p, com.beyondsolo.network.SkillFxPayload.Kind.WINGS, c, at, 1.6f, 120);
        com.beyondsolo.skill.SkillFx.play(p, com.beyondsolo.network.SkillFxPayload.Kind.HALO, c, at, 1.4f, 160);
        WorldBossFx.burst(level, at.add(0, 1, 0), 7f, c);
        WorldBossFx.quake(level, at, RADIUS, c);
        WorldBossFx.ring(level, at, 0.5f, RADIUS + 4, 16, c);
        SkillParticles.send(level, ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 120, 1.2, 2.5, 1.2, 0.2);
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2f, 0.6f);
        level.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2f, 0.5f);
        level.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1f, 1.2f);
        PacketDistributor.sendToPlayer(p, new LightFxPayload(LightFxPayload.INVESTITURE, p.getId(), c, m.ordinal(), 0));
        // everyone near sees a ruler of light rise
        for (ServerPlayer o : level.getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(64), o -> o != p)) {
            if (!SystemAPI.hasSystem(o)) continue;
            Notifications.send(o, SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable("notify.beyondsolo.light_risen.title"),
                    Component.translatable("notify.beyondsolo.light_risen.body", p.getDisplayName(), m.title())));
        }
    }

    /** The Investiture skipped on their screen: nothing changes here (the mantle is already theirs). */
    public static void skipInvestiture(ServerPlayer p) {}

    /** The vigil failed: the dark fades, the Envoy goes, and the light waits a while before it calls again. */
    static void failed(Watch w, @Nullable ServerPlayer p, String why) {
        for (UUID id : w.foes) {
            if (w.level.getEntity(id) instanceof Mob m && m.isAlive()) {
                SkillParticles.send(w.level, ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + 0.5, m.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                m.discard();
            }
        }
        envoyLeaves(w);
        if (p == null) return;
        LightAPI.vigilFailed(p);
        send(p, -1, 0);
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT, Component.translatable("notify.beyondsolo.light_vigil_failed.title"),
                Component.translatable("notify.beyondsolo.light_vigil_failed." + why, LightConfig.retryMinutes())));
    }

    private static void envoyLeaves(Watch w) {
        EnvoyEntity envoy = envoy(w);
        if (envoy != null) envoy.leave();
    }

    /** The hunter fell (from {@code LightHooks}): their vigil fails. */
    static void died(ServerPlayer p) {
        Watch w = WATCHES.remove(p.getUUID());
        if (w != null && w.stage != Stage.DONE) failed(w, p, "fell");
        else if (w != null) envoyLeaves(w);
    }

    /** Ends a vigil at once, quietly (the light taken back, a command, logging out). */
    public static void cancel(ServerPlayer p) {
        Watch w = WATCHES.remove(p.getUUID());
        if (w == null) return;
        if (w.stage == Stage.DONE) {
            envoyLeaves(w);
            return;
        }
        failed(w, null, "gone");
    }

    // ================================================================ reading it

    public static boolean keeping(ServerPlayer p) {
        return WATCHES.containsKey(p.getUUID());
    }

    /** The wave they are on (0 while the light gathers), or -1 if they keep no vigil. Tests. */
    public static int wave(ServerPlayer p) {
        Watch w = WATCHES.get(p.getUUID());
        return w == null ? -1 : w.wave;
    }

    /** Whether they are choosing a mantle now. Tests. */
    public static boolean choosing(ServerPlayer p) {
        Watch w = WATCHES.get(p.getUUID());
        return w != null && w.stage == Stage.CHOOSE;
    }

    /** The creatures of the wave they face now. Tests. */
    public static List<Mob> foes(ServerPlayer p) {
        Watch w = WATCHES.get(p.getUUID());
        List<Mob> out = new ArrayList<>();
        if (w == null) return out;
        for (UUID id : w.foes) if (w.level.getEntity(id) instanceof Mob m && m.isAlive()) out.add(m);
        return out;
    }

    /** Skips to the next stage (tests, commands): the wave cleared, or the light done gathering. */
    public static void advance(ServerPlayer p) {
        Watch w = WATCHES.get(p.getUUID());
        if (w == null) return;
        switch (w.stage) {
            case GATHER, BETWEEN -> wave(w, p, w.wave + 1);
            case FIGHT -> {
                for (Mob m : foes(p)) m.discard();
                w.foes.clear();
                if (w.wave >= WAVES) investiture(w, p);
                else w.stage(Stage.BETWEEN);
            }
            default -> {
            }
        }
    }

    /** Whether an Envoy at a vigil still has one to watch over. */
    static boolean watching(ServerLevel level, EnvoyEntity envoy) {
        if (envoy.ward() == null) return false;
        Watch w = WATCHES.get(envoy.ward());
        return w != null && envoy.getUUID().equals(w.envoy);
    }

    /** The hunter spoke to the Envoy at their vigil: the mantles again if they are choosing, a word otherwise. */
    static void spokenTo(ServerPlayer p, EnvoyEntity envoy) {
        Watch w = WATCHES.get(p.getUUID());
        envoy.speak();
        if (w != null && w.stage == Stage.CHOOSE) {
            long left = LightConfig.choiceSeconds() * 20L - w.since();
            PacketDistributor.sendToPlayer(p, new LightFxPayload(LightFxPayload.CHOOSE, envoy.getId(), RulerPowers.DAWN_GOLD,
                    w.leaning.ordinal(), (int) Math.max(20, left)));
        } else {
            p.displayClientMessage(Component.translatable("light.beyondsolo.envoy.vigil").withStyle(net.minecraft.ChatFormatting.GOLD), true);
        }
    }

    private static @Nullable EnvoyEntity envoy(Watch w) {
        return w.envoy != null && w.level.getEntity(w.envoy) instanceof EnvoyEntity e && e.isAlive() ? e : null;
    }

    private static void send(ServerPlayer p, int wave, int left) {
        PacketDistributor.sendToPlayer(p, new LightFxPayload(LightFxPayload.VIGIL, p.getId(), RulerPowers.DAWN_GOLD, wave, Math.max(0, left)));
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    static void forget(UUID player) {
        WATCHES.remove(player);
    }

    static void clear() {
        WATCHES.clear();
    }

    private Vigil() {}
}
