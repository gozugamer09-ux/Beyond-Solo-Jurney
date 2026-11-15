package com.beyondsolo.skill;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.network.SkillFxPayload.Kind;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static com.beyondsolo.skill.SkillActions.aim;
import static com.beyondsolo.skill.SkillActions.canHit;
import static com.beyondsolo.skill.SkillActions.f;
import static com.beyondsolo.skill.SubclassActions.stun;
import static com.beyondsolo.skill.SubclassKit.*;

/**
 * The Mage subclasses' own skill sets, on top of the starters in {@link SubclassSkills}: ten more each for the
 * Pyromancer, Stormcaller, Tidecaller, Necromancer and the rare Starborn Archon. Same shape as
 * {@link WarriorPath}. Raising shadows is not here: that is the shadow army's own system.
 */
public final class MagePath {
    public static final String BUFF_AEGIS = "astral_aegis";

    /** Curse of Frailty: creature -> {end game time, extra damage percent}. Server thread only. */
    static final Map<LivingEntity, long[]> FRAIL = new WeakHashMap<>();

    private static final Subclass PY = Subclass.PYROMANCER, SC = Subclass.STORMCALLER, TC = Subclass.TIDECALLER,
            NC = Subclass.NECROMANCER, SA = Subclass.STARBORN_ARCHON;
    private static final PlayerRank A = PlayerRank.A, S = PlayerRank.S;
    private static final int BONE = 0xE8E0D0;

    private static float wis(ServerPlayer p, float perPoint) {
        return stat(p, Stat.INT, perPoint);
    }

    private static Vec3 mid(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    /** The line a spell travels: from the caster's hands to {@code range} along their gaze. */
    private static Vec3[] ray(ServerPlayer p, double range) {
        Vec3 from = p.getEyePosition().add(0, -0.3, 0);
        return new Vec3[]{from, from.add(p.getLookAngle().scale(range))};
    }

    static boolean burning(LivingEntity e) {
        return e.isOnFire() || e.hasEffect(ModEffects.DEEP_BURN);
    }

    private static void ignite(ServerPlayer p, LivingEntity e, float amount) {
        Statuses.apply(e, DamageElement.FIRE, amount, p);
        e.igniteForSeconds(4);
    }

    // ================================================================ pyromancer

    static float emberMana(int l) { return 2 + 0.5f * l; }

    public static final Skill FIRE_LANCE = active("fire_lance", PY, A, 22, 0xFF8A3A, 14, 120, 70,
            MagePath::fireLance, (l, t) -> a(f(10 * m(l, t))));
    public static final Skill FLAME_WALL = active("flame_wall", PY, A, 24, 0xFF6A20, 18, 360, 220,
            MagePath::flameWall, (l, t) -> a(f(3 * m(l, t))));
    public static final Skill PYROMANIA = passive("pyromania", PY, A, 26, 0xFFA040, (l, t) -> a(f(2 * l)));
    public static final Skill COMBUSTION = active("combustion", PY, A, 29, 0xFF5A1A, 18, 200, 120,
            MagePath::combustion, (l, t) -> a(f(12 * m(l, t))));
    public static final Skill PHOENIX_DIVE = active("phoenix_dive", PY, A, 32, 0xFFB050, 22, 320, 200,
            MagePath::phoenixDive, (l, t) -> a(f(13 * m(l, t))));
    public static final Skill MOLTEN_SKIN = passive("molten_skin", PY, A, 35, 0xE06030, (l, t) -> a(f(4 * l)));
    public static final Skill METEOR_SHOWER = active("meteor_shower", PY, A, 38, 0xFF7030, 28, 480, 300,
            MagePath::meteorShower, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill LIVING_BOMB = active("living_bomb", PY, A, 42, 0xFF4010, 24, 400, 260,
            MagePath::livingBomb, (l, t) -> a(f(24 * m(l, t)), f(18 * m(l, t))));
    public static final Skill EMBER_HEART = passive("ember_heart", PY, A, 46, 0xFFC070, (l, t) -> a(f(emberMana(l))));
    public static final Skill SUN_FALL = active("sun_fall", PY, S, 50, 0xFFB030, 45, 1400, 900,
            MagePath::sunFall, (l, t) -> a(f(60 * m(l, t))));

    static boolean fireLance(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3[] ray = ray(p, 18);
        float dmg = 10 * m(l, tier(p)) + wis(p, 0.25f);
        for (LivingEntity e : along(p, ray[0], ray[1], 0.9)) {
            magic(p, s, e, dmg);
            ignite(p, e, 30 + 4 * l);
        }
        for (int i = 2; i < 18; i += 2) {
            Vec3 pt = ray[0].lerp(ray[1], i / 18.0);
            level.sendParticles(ParticleTypes.FLAME, pt.x, pt.y, pt.z, 4, 0.15, 0.15, 0.15, 0.02);
        }
        sound(p, SoundEvents.BLAZE_SHOOT, 1f, 0.8f);
        fx(p, Kind.BEAM, s.color(), ray[1], 0.45f, 6);
        return true;
    }

    static boolean flameWall(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 dir = facing(p);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 center = ground(level, p.position().add(dir.scale(4)));
        Vec3 a = center.add(side.scale(-3.5)), b = center.add(side.scale(3.5));
        sound(p, SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
        for (int i = 0; i <= 4; i++) {
            Vec3 pt = a.lerp(b, i / 4.0);
            repeat(p, 100, 20, () -> fxAt(level, Kind.GEYSER, s.color(), pt, 2.6f, 0));
        }
        repeat(p, 100, 5, () -> {
            for (int i = 0; i <= 7; i++) {
                Vec3 pt = a.lerp(b, i / 7.0);
                level.sendParticles(ParticleTypes.FLAME, pt.x, pt.y + 0.8, pt.z, 3, 0.25, 0.7, 0.25, 0.01);
            }
        });
        repeat(p, 100, 10, () -> {
            for (LivingEntity e : along(p, a.add(0, 1, 0), b.add(0, 1, 0), 1.2)) {
                magic(p, s, e, 3 * m(l, tier(p)) + wis(p, 0.06f));
                ignite(p, e, 20 + 2 * l);
            }
        });
        return true;
    }

    static boolean combustion(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 24, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        float dmg = 12 * m(l, tier(p)) + wis(p, 0.3f);
        boolean lit = burning(target);
        Vec3 at = target.position();
        magic(p, s, target, lit ? dmg * 1.6f : dmg);
        ignite(p, target, 40 + 4 * l);
        if (lit) {
            for (LivingEntity e : foes(p, at, 4)) {
                if (e == target) continue;
                magic(p, s, e, dmg * 0.6f);
                ignite(p, e, 30);
            }
        }
        sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, lit ? 0.8f : 1.3f);
        fxAt(level, Kind.NOVA, s.color(), at, lit ? 4.5f : 2.4f, 0);
        fxAt(level, Kind.GEYSER, s.color(), at, lit ? 4 : 2.4f, 0);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 1, at.z, lit ? 16 : 6, 0.5, 0.5, 0.5, 0);
        return true;
    }

    static boolean phoenixDive(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 to = aimGround(p, 14);
        sound(p, SoundEvents.BLAZE_SHOOT, 1f, 0.5f);
        fx(p, Kind.GEYSER, s.color(), p.position(), 3, 0);
        fx(p, Kind.AURA, s.color(), p.position(), 1.3f, 24);
        repeat(p, 20, 2, () -> level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.8, p.getZ(), 8, 0.4, 0.5, 0.4, 0.03));
        leap(p, to, 0.85, land -> {
            for (LivingEntity e : foes(p, land, 5)) {
                magic(p, s, e, 13 * m(l, tier(p)) + wis(p, 0.3f));
                ignite(p, e, 40 + 4 * l);
                push(e, land, 0.9, 0.45);
            }
            sound(level, land, SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.9f);
            fxAt(level, Kind.NOVA, s.color(), land, 5, 0);
            fxAt(level, Kind.CRACK, s.color(), land, 5, 0);
            fxAt(level, Kind.GEYSER, 0xFFE090, land, 4, 0);
            level.sendParticles(ParticleTypes.FLAME, land.x, land.y + 0.4, land.z, 60, 1.8, 0.3, 1.8, 0.06);
        });
        return true;
    }

    static boolean meteorShower(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = aimGround(p, 24);
        sound(p, SoundEvents.EVOKER_PREPARE_ATTACK, 1f, 0.6f);
        fxAt(level, Kind.ZONE, s.color(), center, 6, 60);
        for (int i = 0; i < 7; i++) {
            final int k = i;
            later(p, i * 6, () -> {
                double ang = p.getRandom().nextDouble() * Math.PI * 2, d = k == 0 ? 0 : Math.sqrt(p.getRandom().nextDouble()) * 5;
                Vec3 at = ground(level, center.add(Math.cos(ang) * d, 0, Math.sin(ang) * d));
                fx(p, Kind.METEOR, s.color(), at, 2.4f, 10);
                later(p, 10, () -> {
                    for (LivingEntity e : foes(p, at, 2.8)) {
                        magic(p, s, e, 8 * m(l, tier(p)) + wis(p, 0.15f));
                        ignite(p, e, 25 + 3 * l);
                    }
                    sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.1f + 0.05f * k);
                    fxAt(level, Kind.NOVA, s.color(), at, 2.8f, 0);
                    level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 6, 0.6, 0.1, 0.6, 0);
                });
            });
        }
        return true;
    }

    static boolean livingBomb(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 24, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.TNT_PRIMED, 1f, 1.4f);
        fx(target, Kind.MARK, s.color(), target.position(), target.getBbHeight(), 60);
        fx(target, Kind.AURA, s.color(), target.position(), 1.1f, 60);
        repeat(p, 60, 20, () -> {
            if (target.isAlive()) sound(target, SoundEvents.NOTE_BLOCK_HAT.value(), 1f, 1.6f);
        });
        later(p, 60, () -> {
            Vec3 at = target.position();
            if (target.isAlive()) magic(p, s, target, 24 * m(l, tier(p)) + wis(p, 0.4f));
            for (LivingEntity e : foes(p, at, 5)) {
                if (e != target) magic(p, s, e, 18 * m(l, tier(p)) + wis(p, 0.3f));
                ignite(p, e, 40 + 4 * l);
                push(e, at, 0.8, 0.4);
            }
            sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.8f);
            fxAt(level, Kind.NOVA, s.color(), at, 5, 0);
            fxAt(level, Kind.GEYSER, s.color(), at, 4, 0);
            fxAt(level, Kind.ROAR, 0xFFE090, at, 5, 0);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1, at.z, 3, 0.8, 0.5, 0.8, 0);
        });
        return true;
    }

    static boolean sunFall(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = aimGround(p, 24);
        Vec3 sun = center.add(0, 14, 0);
        sound(p, SoundEvents.BEACON_ACTIVATE, 1.2f, 0.5f);
        sound(level, center, SoundEvents.FIRE_AMBIENT, 2f, 0.5f);
        fx(p, Kind.AURA, s.color(), p.position(), 1.4f, 44);
        fxAt(level, Kind.ORB, s.color(), sun, 3.5f, 44);
        fxAt(level, Kind.ZONE, s.color(), center, 10, 48);
        fxAt(level, Kind.QUAKE, s.color(), center, 10, 48);
        repeat(p, 40, 4, () -> level.sendParticles(ParticleTypes.FLAME, sun.x, sun.y, sun.z, 12, 1.5, 1.5, 1.5, 0.02));
        later(p, 40, () -> fx(p, Kind.METEOR, s.color(), center, 9, 8));
        later(p, 48, () -> {
            for (LivingEntity e : foes(p, center, 10)) {
                magic(p, s, e, 60 * m(l, tier(p)) + wis(p, 0.8f));
                ignite(p, e, 100);
                launch(e, 0.9);
            }
            sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.5f);
            sound(level, center, SoundEvents.BLAZE_SHOOT, 1.4f, 0.4f);
            fxAt(level, Kind.NOVA, s.color(), center, 10, 0);
            fxAt(level, Kind.ROAR, 0xFFF0C0, center, 10, 0);
            fxAt(level, Kind.CRACK, s.color(), center, 10, 0);
            fxAt(level, Kind.GEYSER, 0xFFE090, center, 9, 0);
            level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 1, center.z, 4, 2, 0.6, 2, 0);
            level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.5, center.z, 30, 3, 0.3, 3, 0);
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4;
                Vec3 pt = ground(level, center.add(Math.cos(ang) * 6.5, 0, Math.sin(ang) * 6.5));
                later(p, 2 + i, () -> fxAt(level, Kind.GEYSER, s.color(), pt, 4, 0));
            }
            // the ground keeps burning
            fxAt(level, Kind.ZONE, 0xFF5A1A, center, 8, 80);
            repeat(p, 80, 10, () -> {
                level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.3, center.z, 40, 3.5, 0.2, 3.5, 0.02);
                for (LivingEntity e : foes(p, center, 8)) {
                    magic(p, s, e, 5 * m(l, tier(p)));
                    ignite(p, e, 20);
                }
            });
        });
        return true;
    }

    // ================================================================ stormcaller

    static float overchargeChance(int l) { return 4 + 2 * l; }
    static float surgeMana(int l) { return 0.15f * l; }

    public static final Skill THUNDER_SPEAR = active("thunder_spear", SC, A, 22, 0xA8E0FF, 14, 110, 60,
            MagePath::thunderSpear, (l, t) -> a(f(10 * m(l, t))));
    public static final Skill BALL_LIGHTNING = active("ball_lightning", SC, A, 24, 0x80C8FF, 20, 360, 220,
            MagePath::ballLightning, (l, t) -> a(f(3 * m(l, t))));
    public static final Skill OVERCHARGE = passive("overcharge", SC, A, 26, 0xC0F0FF, (l, t) -> a(f(overchargeChance(l))));
    public static final Skill THUNDERCLAP = active("thunderclap", SC, A, 29, 0x90D0FF, 18, 240, 140,
            MagePath::thunderclap, (l, t) -> a(f(9 * m(l, t))));
    public static final Skill LIGHTNING_DASH = active("lightning_dash", SC, A, 32, 0xB0E8FF, 16, 200, 120,
            MagePath::lightningDash, (l, t) -> a(f(10 * m(l, t))));
    public static final Skill CONDUCTIVITY = passive("conductivity", SC, A, 35, 0x70B8F0, (l, t) -> a(f(2 * l)));
    public static final Skill STORM_CAGE = active("storm_cage", SC, A, 38, 0x60A8FF, 26, 480, 300,
            MagePath::stormCage, (l, t) -> a(f(3.5f * m(l, t))));
    public static final Skill TEMPEST_CALL = active("tempest_call", SC, A, 42, 0x9CD8FF, 28, 520, 320,
            MagePath::tempestCall, (l, t) -> a(f(10 * m(l, t))));
    public static final Skill STORM_SURGE = passive("storm_surge", SC, A, 46, 0xD0F4FF, (l, t) -> a(f(surgeMana(l))));
    public static final Skill JUDGMENT_OF_THUNDER = active("judgment_of_thunder", SC, S, 50, 0xB8E8FF, 45, 1400, 900,
            MagePath::judgmentOfThunder, (l, t) -> a(f(55 * m(l, t)), f(20 * m(l, t))));

    static boolean thunderSpear(ServerPlayer p, Skill s, int l) {
        Vec3[] ray = ray(p, 20);
        for (LivingEntity e : along(p, ray[0], ray[1], 0.8)) {
            magic(p, s, e, 10 * m(l, tier(p)) + wis(p, 0.25f));
            stun(e, 10);
            fx(e, Kind.SPLASH, s.color(), mid(e), 1.4f, 0);
        }
        sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.6f, 1.6f);
        fx(p, Kind.BEAM, s.color(), ray[1], 0.25f, 4);
        fx(p, Kind.BOLT, s.color(), ray[1], 0.3f, 0);
        return true;
    }

    static boolean ballLightning(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 start = p.getEyePosition().add(0, -0.2, 0).add(facing(p).scale(1.2));
        Vec3 dir = facing(p);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 1f, 1.8f);
        for (int t = 0; t < 60; t += 5) {
            final int tt = t;
            later(p, t, () -> {
                Vec3 at = start.add(dir.scale(tt * 0.3));
                fxAt(level, Kind.ORB, s.color(), at, 0.7f, 7);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.5, 0.5, 0.5, 0.2);
                for (LivingEntity e : foes(p, at, 3.5)) {
                    magic(p, s, e, 3 * m(l, tier(p)) + wis(p, 0.06f));
                    fx(e, Kind.BOLT, s.color(), at, 0.3f, 0);
                }
                if (tt % 10 == 0) sound(level, at, SoundEvents.BEE_LOOP_AGGRESSIVE, 0.5f, 1.8f);
            });
        }
        return true;
    }

    static boolean thunderclap(ServerPlayer p, Skill s, int l) {
        Vec3 at = p.position();
        for (LivingEntity e : foes(p, at, 6)) {
            magic(p, s, e, 9 * m(l, tier(p)) + wis(p, 0.2f));
            stun(e, 30);
            push(e, at, 1.0, 0.4);
        }
        sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 1.5f);
        fx(p, Kind.ROAR, s.color(), at, 6, 0);
        fx(p, Kind.NOVA, s.color(), at, 6, 0);
        fxAt(p.serverLevel(), Kind.THUNDER, s.color(), at, 4, 0);
        return true;
    }

    static boolean lightningDash(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 from = blinkForward(p, 9);
        Vec3 to = p.position();
        for (LivingEntity e : along(p, from.add(0, 1, 0), to.add(0, 1, 0), 1.3)) {
            magic(p, s, e, 10 * m(l, tier(p)) + wis(p, 0.25f));
            stun(e, 15);
            fx(e, Kind.SPLASH, s.color(), mid(e), 1.4f, 0);
        }
        sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.6f, 1.9f);
        fx(p, Kind.BOLT, s.color(), from.add(0, 1, 0), 0.3f, 0);
        fx(p, Kind.BLINK, s.color(), from, 1, 0);
        fxAt(level, Kind.THUNDER, s.color(), to, 2.5f, 0);
        for (int i = 1; i < 9; i++) {
            Vec3 pt = from.lerp(to, i / 9.0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pt.x, pt.y + 1, pt.z, 5, 0.2, 0.4, 0.2, 0.1);
        }
        return true;
    }

    static boolean stormCage(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = aimGround(p, 22);
        sound(p, SoundEvents.EVOKER_PREPARE_ATTACK, 1f, 1.5f);
        fxAt(level, Kind.ZONE, s.color(), center, 5, 80);
        fxAt(level, Kind.DOME, s.color(), center.add(0, 2.5, 0), 5, 80);
        repeat(p, 80, 10, () -> {
            for (LivingEntity e : foes(p, center, 5.5)) {
                magic(p, s, e, 3.5f * m(l, tier(p)) + wis(p, 0.06f));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
                if (e.position().distanceTo(center) > 3.5) pull(e, center, 0.7);
            }
        });
        repeat(p, 80, 16, () -> {
            double ang = p.getRandom().nextDouble() * Math.PI * 2, d = p.getRandom().nextDouble() * 4;
            Vec3 at = ground(level, center.add(Math.cos(ang) * d, 0, Math.sin(ang) * d));
            fxAt(level, Kind.THUNDER, s.color(), at, 2, 0);
            sound(level, at, SoundEvents.TRIDENT_THUNDER.value(), 0.35f, 1.8f);
        });
        return true;
    }

    static boolean tempestCall(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7f, 0.8f);
        fx(p, Kind.AURA, s.color(), p.position(), 1.3f, 54);
        fx(p, Kind.PULSE, s.color(), p.position(), 12, 0);
        for (int i = 0; i < 9; i++) {
            later(p, i * 6, () -> {
                List<LivingEntity> near = foes(p, p.position(), 12);
                Vec3 at;
                if (near.isEmpty()) {
                    double ang = p.getRandom().nextDouble() * Math.PI * 2, d = 3 + p.getRandom().nextDouble() * 7;
                    at = ground(level, p.position().add(Math.cos(ang) * d, 0, Math.sin(ang) * d));
                } else {
                    at = near.get(p.getRandom().nextInt(near.size())).position();
                }
                for (LivingEntity e : foes(p, at, 3)) {
                    magic(p, s, e, 10 * m(l, tier(p)) + wis(p, 0.2f));
                    stun(e, 15);
                }
                fxAt(level, Kind.THUNDER, s.color(), at, 3, 0);
                sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.2f);
            });
        }
        return true;
    }

    static boolean judgmentOfThunder(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = aimGround(p, 26);
        sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 1f, 1.4f);
        fx(p, Kind.AURA, s.color(), p.position(), 1.4f, 34);
        fxAt(level, Kind.ZONE, s.color(), center, 8, 34);
        fxAt(level, Kind.ORB, s.color(), center.add(0, 16, 0), 2.5f, 32);
        for (int i = 0; i < 6; i++) {
            final int k = i;
            later(p, i * 5, () -> {
                double ang = k * Math.PI / 3;
                Vec3 at = ground(level, center.add(Math.cos(ang) * 7, 0, Math.sin(ang) * 7));
                fxAt(level, Kind.THUNDER, s.color(), at, 2, 0);
                sound(level, at, SoundEvents.TRIDENT_THUNDER.value(), 0.4f, 1.6f);
            });
        }
        later(p, 32, () -> {
            Set<Integer> struck = new HashSet<>();
            for (LivingEntity e : foes(p, center, 8)) {
                struck.add(e.getId());
                magic(p, s, e, 55 * m(l, tier(p)) + wis(p, 0.8f));
                stun(e, 60);
            }
            // what the bolt does not reach, its arcs do
            for (LivingEntity e : foes(p, center, 16)) {
                if (!struck.add(e.getId())) continue;
                magic(p, s, e, 20 * m(l, tier(p)) + wis(p, 0.3f));
                stun(e, 20);
                fx(e, Kind.BOLT, s.color(), center.add(0, 1, 0), 0.3f, 0);
            }
            sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.6f, 0.6f);
            sound(level, center, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4f, 0.7f);
            for (int i = 0; i < 3; i++) later(p, i * 2, () -> fxAt(level, Kind.THUNDER, s.color(), center, 8, 0));
            fxAt(level, Kind.NOVA, s.color(), center, 8, 0);
            fxAt(level, Kind.ROAR, 0xFFFFFF, center, 10, 0);
            fxAt(level, Kind.CRACK, s.color(), center, 8, 0);
            fxAt(level, Kind.QUAKE, s.color(), center, 10, 20);
        });
        return true;
    }

    // ================================================================ tidecaller

    static float undertowSeconds(int l) { return 1 + 0.2f * l; }
    static float tideMana(int l) { return 4 * l; }
    static float rainHeal(int l) { return 1 + 0.4f * l; }

    public static final Skill WATER_WHIP = active("water_whip", TC, A, 22, 0x5AB8FF, 12, 110, 60,
            MagePath::waterWhip, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill BUBBLE_PRISON = active("bubble_prison", TC, A, 24, 0x80D0FF, 18, 360, 220,
            MagePath::bubblePrison, (l, t) -> a(f(bubbleTicks(l) / 20f), f(6 * m(l, t))));
    public static final Skill UNDERTOW = passive("undertow", TC, A, 26, 0x3A90E0, (l, t) -> a(f(undertowSeconds(l))));
    public static final Skill RIPTIDE = active("riptide", TC, A, 29, 0x4FA8FF, 16, 220, 130,
            MagePath::riptide, (l, t) -> a(f(9 * m(l, t))));
    public static final Skill HEALING_RAIN = active("healing_rain", TC, A, 32, 0x70E0E8, 24, 600, 400,
            MagePath::healingRain, (l, t) -> a(f(rainHeal(l))));
    public static final Skill HIGH_TIDE = passive("high_tide", TC, A, 35, 0x60B0FF, (l, t) -> a(f(tideMana(l))));
    public static final Skill MAELSTROM = active("maelstrom", TC, A, 38, 0x3080E0, 26, 480, 300,
            MagePath::maelstrom, (l, t) -> a(f(3.5f * m(l, t))));
    public static final Skill TSUNAMI = active("tsunami", TC, A, 42, 0x48A0F0, 28, 520, 320,
            MagePath::tsunami, (l, t) -> a(f(14 * m(l, t))));
    public static final Skill EBB_AND_FLOW = passive("ebb_and_flow", TC, A, 46, 0x90D8F0, (l, t) -> a(f(2 * l)));
    public static final Skill ABYSSAL_DELUGE = active("abyssal_deluge", TC, S, 50, 0x2870E0, 45, 1400, 900,
            MagePath::abyssalDeluge, (l, t) -> a(f(50 * m(l, t)), f(8 + 2 * l)));

    static int bubbleTicks(int l) { return 40 + 4 * l; }

    private static void drench(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, count, spread, 0.4, spread, 0.2);
        level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 0.8, at.z, count / 2, spread, 0.6, spread, 0.02);
    }

    static boolean waterWhip(ServerPlayer p, Skill s, int l) {
        Vec3 dir = facing(p);
        for (LivingEntity e : cone(p, 8, 0.5)) {
            magic(p, s, e, 8 * m(l, tier(p)) + wis(p, 0.2f));
            pull(e, p.position().add(dir.scale(1.5)), 1.0);
            fx(e, Kind.SPLASH, s.color(), mid(e), 1.6f, 0);
            drench(p.serverLevel(), e.position(), 16, 0.4);
        }
        sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.8f, 1.4f);
        fx(p, Kind.WAVE, s.color(), p.position().add(dir.scale(7)), 1.4f, 2);
        fx(p, Kind.SLASH, s.color(), p.position(), 3.4f, 0);
        return true;
    }

    static boolean bubblePrison(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 20, x -> canHit(p, x));
        if (target == null) return false;
        int ticks = bubbleTicks(l);
        magic(p, s, target, 6 * m(l, tier(p)) + wis(p, 0.15f));
        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, ticks, 0, false, false));
        stun(target, ticks);
        sound(target, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 1.2f, 1.2f);
        fx(target, Kind.DOME, s.color(), target.position(), Math.max(1.2f, target.getBbHeight() * 0.7f), ticks);
        fx(target, Kind.BIND, s.color(), target.position(), 1.2f, ticks);
        later(p, ticks, () -> {
            if (!target.isAlive()) return;
            magic(p, s, target, 6 * m(l, tier(p)) + wis(p, 0.15f));
            sound(target, SoundEvents.GENERIC_SPLASH, 1f, 1.3f);
            fx(target, Kind.SPLASH, s.color(), mid(target), 2.4f, 0);
            drench(p.serverLevel(), target.position(), 30, 0.6);
        });
        return true;
    }

    static boolean riptide(ServerPlayer p, Skill s, int l) {
        Vec3 start = p.position();
        Vec3 dir = facing(p);
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1f, 1f);
        fx(p, Kind.DASH, s.color(), start, 1, 8);
        fx(p, Kind.WAVE, s.color(), start.add(dir.scale(9)), 1.2f, 2);
        repeat(p, 7, 1, () -> drench(level, p.position(), 10, 0.4));
        dash(p, dir, 9, 7, 2.2, e -> {
            magic(p, s, e, 9 * m(l, tier(p)) + wis(p, 0.2f));
            push(e, p.position().subtract(dir), 1.0, 0.5);
            fx(e, Kind.SPLASH, s.color(), mid(e), 1.8f, 0);
        }, () -> fx(p, Kind.SPLASH, s.color(), p.position().add(0, 1, 0), 2.6f, 0));
        return true;
    }

    static boolean healingRain(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        sound(p, SoundEvents.WEATHER_RAIN, 1.2f, 1.2f);
        fxAt(level, Kind.RAIN, s.color(), center, 5, 120);
        fxAt(level, Kind.ZONE, s.color(), center, 5, 120);
        repeat(p, 120, 20, () -> {
            for (Player ally : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(center, center).inflate(5), Player::isAlive)) {
                ally.heal(rainHeal(l) + wis(p, 0.03f));
                ally.clearFire();
                fx(ally, Kind.HEAL, s.color(), ally.position(), 0.8f, 0);
            }
            for (LivingEntity e : foes(p, center, 5)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
        });
        return true;
    }

    static boolean maelstrom(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = aimGround(p, 22);
        sound(level, center, SoundEvents.ELDER_GUARDIAN_CURSE, 0.5f, 1.6f);
        fxAt(level, Kind.VORTEX, s.color(), center, 6, 80);
        fxAt(level, Kind.ZONE, s.color(), center, 6, 80);
        repeat(p, 80, 5, () -> {
            for (LivingEntity e : foes(p, center, 7)) pull(e, center, 0.6);
            drench(level, center, 14, 2.5);
        });
        repeat(p, 80, 10, () -> {
            for (LivingEntity e : foes(p, center, 7)) {
                magic(p, s, e, 3.5f * m(l, tier(p)) + wis(p, 0.06f));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1));
            }
        });
        return true;
    }

    static boolean tsunami(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 start = p.position();
        Vec3 dir = facing(p);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Set<Integer> struck = new HashSet<>();
        sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.4f, 0.6f);
        fx(p, Kind.ROAR, s.color(), start, 5, 0);
        for (int i = 0; i < 12; i++) {
            final int k = i;
            later(p, i * 2, () -> {
                Vec3 at = ground(level, start.add(dir.scale(1.5 * k + 2)));
                for (LivingEntity e : along(p, at.add(side.scale(-4)).add(0, 1, 0), at.add(side.scale(4)).add(0, 1, 0), 1.6)) {
                    if (!struck.add(e.getId())) continue;
                    magic(p, s, e, 14 * m(l, tier(p)) + wis(p, 0.3f));
                    e.setDeltaMovement(dir.x * 1.6, 0.5, dir.z * 1.6);
                    e.hurtMarked = true;
                    fx(e, Kind.SPLASH, s.color(), mid(e), 2, 0);
                }
                for (int j = -1; j <= 1; j++) fxAt(level, Kind.GEYSER, j == 0 ? 0xD0F0FF : s.color(), at.add(side.scale(j * 2.8)), 3.4f, 0);
                drench(level, at, 30, 3);
                if (k % 3 == 0) sound(level, at, SoundEvents.GENERIC_SPLASH, 1f, 0.7f);
            });
        }
        return true;
    }

    static boolean abyssalDeluge(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = aimGround(p, 24);
        sound(level, center, SoundEvents.ELDER_GUARDIAN_CURSE, 0.8f, 0.8f);
        sound(p, SoundEvents.CONDUIT_ACTIVATE, 1.2f, 0.7f);
        fx(p, Kind.AURA, s.color(), p.position(), 1.4f, 60);
        fxAt(level, Kind.VORTEX, s.color(), center, 10, 60);
        fxAt(level, Kind.ZONE, s.color(), center, 10, 60);
        fxAt(level, Kind.QUAKE, s.color(), center, 10, 60);
        repeat(p, 60, 4, () -> {
            for (LivingEntity e : foes(p, center, 12)) pull(e, center, 0.8);
            drench(level, center, 24, 4);
        });
        repeat(p, 60, 10, () -> {
            for (LivingEntity e : foes(p, center, 12)) magic(p, s, e, 4 * m(l, tier(p)));
        });
        later(p, 60, () -> {
            for (LivingEntity e : foes(p, center, 10)) {
                magic(p, s, e, 50 * m(l, tier(p)) + wis(p, 0.8f));
                launch(e, 1.3);
            }
            for (Player ally : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(center, center).inflate(10), Player::isAlive)) {
                ally.heal(8 + 2 * l);
                fx(ally, Kind.HEAL, s.color(), ally.position(), 1, 0);
            }
            if (p.position().distanceTo(center) > 10) p.heal(8 + 2 * l);
            sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.6f);
            sound(level, center, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.6f, 0.5f);
            fxAt(level, Kind.GEYSER, 0xD0F0FF, center, 12, 0);
            fxAt(level, Kind.NOVA, s.color(), center, 10, 0);
            fxAt(level, Kind.ROAR, 0xFFFFFF, center, 10, 0);
            drench(level, center, 120, 5);
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4;
                Vec3 pt = ground(level, center.add(Math.cos(ang) * 6, 0, Math.sin(ang) * 6));
                later(p, 1 + i, () -> fxAt(level, Kind.GEYSER, s.color(), pt, 6, 0));
            }
        });
        return true;
    }

    // ================================================================ necromancer

    static float mightSeconds(int l) { return 2 + 0.3f * l; }
    static float frailBonus(int l) { return 10 + 2 * l; }

    public static final Skill BONE_SPEAR = active("bone_spear", NC, A, 22, 0xD8D0E8, 14, 110, 60,
            MagePath::boneSpear, (l, t) -> a(f(10 * m(l, t))));
    public static final Skill WITHERING_CURSE = active("withering_curse", NC, A, 24, 0x6A4AB0, 16, 260, 160,
            MagePath::witheringCurse, (l, t) -> a(f(curseTicks(l) / 20f), f(5 * m(l, t))));
    public static final Skill NECROTIC_MIGHT = passive("necrotic_might", NC, A, 26, 0x8060C0, (l, t) -> a(f(mightSeconds(l))));
    public static final Skill GRAVE_BURST = active("grave_burst", NC, A, 29, 0xB0A8C8, 18, 240, 140,
            MagePath::graveBurst, (l, t) -> a(f(11 * m(l, t))));
    public static final Skill LIFE_DRAIN = active("life_drain", NC, A, 32, 0x9A50D0, 20, 360, 220,
            MagePath::lifeDrain, (l, t) -> a(f(3 * m(l, t)), f(drainHeal(l))));
    public static final Skill BONE_ARMOR = passive("bone_armor", NC, A, 35, 0xE0D8F0, (l, t) -> a(f(0.6f * l), f(0.2f * l)));
    public static final Skill SPIRIT_SWARM = active("spirit_swarm", NC, A, 38, 0x90F0D0, 24, 400, 260,
            MagePath::spiritSwarm, (l, t) -> a(f(6 * m(l, t))));
    public static final Skill CURSE_OF_FRAILTY = active("curse_of_frailty", NC, A, 42, 0x5A3AA0, 24, 600, 400,
            MagePath::curseOfFrailty, (l, t) -> a(f(frailBonus(l))));
    public static final Skill DEATHS_EMBRACE = passive("deaths_embrace", NC, A, 46, 0x7040B0, (l, t) -> a(f(3 * l)));
    public static final Skill REQUIEM = active("requiem", NC, S, 50, 0x7B5CC8, 45, 1400, 900,
            MagePath::requiem, (l, t) -> a(f(50 * m(l, t))));

    static int curseTicks(int l) { return 100 + 10 * l; }
    static float drainHeal(int l) { return 1 + 0.2f * l; }

    static boolean boneSpear(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3[] ray = ray(p, 18);
        for (LivingEntity e : along(p, ray[0], ray[1], 0.8)) {
            hit(p, s, e, 10 * m(l, tier(p)) + wis(p, 0.25f));
            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
            fx(e, Kind.SPLASH, BONE, mid(e), 1.4f, 0);
        }
        for (int i = 2; i < 18; i += 2) {
            Vec3 pt = ray[0].lerp(ray[1], i / 18.0);
            level.sendParticles(ParticleTypes.WHITE_ASH, pt.x, pt.y, pt.z, 6, 0.15, 0.15, 0.15, 0.01);
        }
        sound(p, SoundEvents.SKELETON_HURT, 1f, 0.6f);
        sound(p, SoundEvents.TRIDENT_THROW.value(), 1f, 0.7f);
        fx(p, Kind.BEAM, BONE, ray[1], 0.22f, 4);
        fx(p, Kind.SHOT, s.color(), ray[1], 1, 0);
        return true;
    }

    static boolean witheringCurse(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 22, x -> canHit(p, x));
        if (target == null) return false;
        int ticks = curseTicks(l);
        magic(p, s, target, 5 * m(l, tier(p)) + wis(p, 0.15f));
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks, 1));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1));
        sound(target, SoundEvents.WITHER_SHOOT, 0.6f, 0.6f);
        fx(p, Kind.BOLT, s.color(), mid(target), 0.3f, 0);
        fx(target, Kind.MARK, s.color(), target.position(), target.getBbHeight(), ticks);
        fx(target, Kind.VEIL, s.color(), target.position(), 1.1f, Math.min(ticks, 60));
        return true;
    }

    static boolean graveBurst(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 22);
        sound(level, at, SoundEvents.SKELETON_AMBIENT, 1f, 0.5f);
        fxAt(level, Kind.CRACK, s.color(), at, 4, 0);
        fxAt(level, Kind.ZONE, s.color(), at, 4, 8);
        later(p, 6, () -> {
            for (LivingEntity e : foes(p, at, 4)) {
                hit(p, s, e, 11 * m(l, tier(p)) + wis(p, 0.25f));
                launch(e, 0.7);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            }
            sound(level, at, SoundEvents.SKELETON_DEATH, 1f, 0.6f);
            sound(level, at, SoundEvents.WITHER_BREAK_BLOCK, 0.5f, 1.4f);
            fxAt(level, Kind.GEYSER, BONE, at, 4, 0);
            for (int i = 0; i < 5; i++) {
                double ang = i * Math.PI * 2 / 5;
                fxAt(level, Kind.GEYSER, i % 2 == 0 ? BONE : s.color(), at.add(Math.cos(ang) * 2.4, 0, Math.sin(ang) * 2.4), 2.6f, 0);
            }
            level.sendParticles(ParticleTypes.WHITE_ASH, at.x, at.y + 1, at.z, 60, 1.5, 1, 1.5, 0.02);
        });
        return true;
    }

    static boolean lifeDrain(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 16, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 0.6f);
        fx(p, Kind.AURA, s.color(), p.position(), 1.1f, 40);
        repeat(p, 40, 5, () -> {
            if (!target.isAlive() || target.distanceTo(p) > 20) return;
            magic(p, s, target, 3 * m(l, tier(p)) + wis(p, 0.06f));
            p.heal(drainHeal(l));
            fx(p, Kind.BEAM, s.color(), mid(target), 0.3f, 6);
            Vec3 pt = mid(target).lerp(p.getEyePosition(), p.getRandom().nextDouble());
            level.sendParticles(ParticleTypes.SOUL, pt.x, pt.y, pt.z, 3, 0.15, 0.15, 0.15, 0.01);
            sound(p, SoundEvents.SOUL_ESCAPE.value(), 0.6f, 1.2f);
        });
        later(p, 40, () -> fx(p, Kind.HEAL, s.color(), p.position(), 1, 0));
        return true;
    }

    static boolean spiritSwarm(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        if (foes(p, p.position(), 14).isEmpty()) return false;
        sound(p, SoundEvents.VEX_CHARGE, 1f, 0.7f);
        fx(p, Kind.BLADES, s.color(), p.position(), 1.6f, 36);
        fx(p, Kind.PULSE, s.color(), p.position(), 6, 0);
        for (int i = 0; i < 6; i++) {
            later(p, 6 + i * 5, () -> {
                List<LivingEntity> near = foes(p, p.position(), 14);
                if (near.isEmpty()) return;
                LivingEntity e = near.get(p.getRandom().nextInt(near.size()));
                magic(p, s, e, 6 * m(l, tier(p)) + wis(p, 0.12f));
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
                fx(p, Kind.SHOT, s.color(), mid(e), 1, 0);
                fxAt(level, Kind.ORB, s.color(), mid(e), 0.6f, 6);
                level.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + 1, e.getZ(), 6, 0.3, 0.5, 0.3, 0.03);
                sound(e, SoundEvents.VEX_HURT, 0.7f, 0.8f);
            });
        }
        return true;
    }

    static boolean curseOfFrailty(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 20);
        List<LivingEntity> cursed = foes(p, at, 6);
        if (cursed.isEmpty()) return false;
        long end = level.getGameTime() + 160;
        for (LivingEntity e : cursed) {
            FRAIL.put(e, new long[]{end, Math.round(frailBonus(l))});
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 0));
            fx(e, Kind.MARK, s.color(), e.position(), e.getBbHeight(), 160);
            fx(e, Kind.BIND, s.color(), e.position(), 1.1f, 30);
        }
        sound(level, at, SoundEvents.ELDER_GUARDIAN_CURSE, 0.5f, 1.3f);
        fxAt(level, Kind.ZONE, s.color(), at, 6, 40);
        fxAt(level, Kind.PULSE, s.color(), at, 6, 0);
        return true;
    }

    static boolean requiem(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 44, 3, false, false, false));
        sound(p, SoundEvents.WITHER_SPAWN, 0.5f, 1.4f);
        fx(p, Kind.VEIL, s.color(), center, 1.6f, 40);
        fxAt(level, Kind.ZONE, s.color(), center, 12, 44);
        fxAt(level, Kind.QUAKE, s.color(), center, 12, 40);
        fxAt(level, Kind.ORB, s.color(), center.add(0, 4.5, 0), 1.6f, 40);
        repeat(p, 40, 5, () -> {
            for (LivingEntity e : foes(p, center, 12)) {
                Vec3 pt = mid(e);
                level.sendParticles(ParticleTypes.SOUL, pt.x, pt.y, pt.z, 3, 0.3, 0.4, 0.3, 0.04);
            }
            sound(level, center, SoundEvents.SOUL_ESCAPE.value(), 1f, 0.6f);
        });
        later(p, 40, () -> {
            int reaped = 0;
            for (LivingEntity e : foes(p, center, 12)) {
                float dmg = 50 * m(l, tier(p)) + wis(p, 0.8f);
                // those already at death's door do not get to stay there
                if (e.getHealth() < e.getMaxHealth() * 0.2f) dmg *= 2;
                magic(p, s, e, dmg);
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                fxAt(level, Kind.GEYSER, s.color(), e.position(), 3.5f, 0);
                fx(p, Kind.BOLT, s.color(), mid(e), 0.3f, 0);
                reaped++;
            }
            if (reaped > 0) {
                p.heal(2 * reaped);
                SystemAPI.restoreMana(p, 3 * reaped);
                fx(p, Kind.HEAL, s.color(), p.position(), 1.4f, 0);
            }
            sound(level, center, SoundEvents.WITHER_DEATH, 0.5f, 1.3f);
            sound(level, center, SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.6f);
            fxAt(level, Kind.NOVA, s.color(), center, 12, 0);
            fxAt(level, Kind.ROAR, BONE, center, 12, 0);
            fxAt(level, Kind.CRACK, s.color(), center, 12, 0);
            level.sendParticles(ParticleTypes.SOUL, center.x, center.y + 1, center.z, 40, 5, 1, 5, 0.05);
        });
        return true;
    }

    // ================================================================ starborn archon

    static float mindMana(int l) { return 5 * l; }
    static float flowMana(int l) { return 0.2f * l; }
    static float aegisGuard(int l) { return 40 + 3 * l; }
    static int aegisTicks(int l) { return 120 + 10 * l; }

    public static final Skill STAR_BOLT = active("star_bolt", SA, S, 22, 0x9CB4FF, 16, 90, 50,
            MagePath::starBolt, (l, t) -> a(f(5 * m(l, t))));
    public static final Skill COMET = active("comet", SA, S, 24, 0x80A0FF, 22, 240, 140,
            MagePath::comet, (l, t) -> a(f(16 * m(l, t))));
    public static final Skill STELLAR_MIND = passive("stellar_mind", SA, S, 26, 0xB0C4FF, (l, t) -> a(f(mindMana(l))));
    public static final Skill EVENT_HORIZON = active("event_horizon", SA, S, 29, 0x6A5CE0, 22, 300, 180,
            MagePath::eventHorizon, (l, t) -> a(f(14 * m(l, t))));
    public static final Skill CONSTELLATION = active("constellation", SA, S, 32, 0xC8D8FF, 24, 360, 220,
            MagePath::constellation, (l, t) -> a(f(8 * m(l, t)), f(2 * m(l, t))));
    public static final Skill COSMIC_FLOW = passive("cosmic_flow", SA, S, 35, 0x90A8F0, (l, t) -> a(f(flowMana(l))));
    public static final Skill SUPERNOVA = active("supernova", SA, S, 38, 0xE0E8FF, 30, 520, 320,
            MagePath::supernova, (l, t) -> a(f(20 * m(l, t))));
    public static final Skill ASTRAL_AEGIS = active("astral_aegis", SA, S, 42, 0x88A8FF, 26, 700, 460,
            MagePath::astralAegis, (l, t) -> a(f(aegisTicks(l) / 20f), f(aegisGuard(l)), f(3 + l)));
    public static final Skill ZENITH = passive("zenith", SA, S, 46, 0xD8E0FF, (l, t) -> a(f(3 * l)));
    public static final Skill GENESIS_RAY = active("genesis_ray", SA, S, 50, 0xA0B8FF, 45, 1500, 1000,
            MagePath::genesisRay, (l, t) -> a(f(9 * m(l, t)), f(30 * m(l, t))));

    private static void stars(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, count, spread, spread, spread, 0.03);
    }

    static boolean starBolt(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 26, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < 3; i++) {
            final int k = i;
            later(p, i * 3, () -> {
                if (!target.isAlive()) return;
                magic(p, s, target, 5 * m(l, tier(p)) + wis(p, 0.12f));
                fx(p, Kind.SHOT, s.color(), mid(target), 1, 0);
                fxAt(level, Kind.ORB, s.color(), mid(target), 0.5f, 4);
                stars(level, mid(target), 6, 0.2);
                sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 1.2f + 0.2f * k);
            });
        }
        return true;
    }

    static boolean comet(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 26);
        sound(p, SoundEvents.EVOKER_CAST_SPELL, 1f, 1.4f);
        fx(p, Kind.METEOR, s.color(), at, 3.5f, 10);
        later(p, 10, () -> {
            for (LivingEntity e : foes(p, at, 4)) {
                magic(p, s, e, 16 * m(l, tier(p)) + wis(p, 0.35f));
                launch(e, 0.6);
            }
            sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.3f);
            sound(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 1.4f, 0.6f);
            fxAt(level, Kind.NOVA, s.color(), at, 4, 0);
            fxAt(level, Kind.CRACK, s.color(), at, 4, 0);
            stars(level, at.add(0, 1, 0), 40, 1.2);
        });
        return true;
    }

    static boolean eventHorizon(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 origin = blinkForward(p, 10);
        sound(level, origin, SoundEvents.ENDERMAN_TELEPORT, 1f, 0.6f);
        fx(p, Kind.BLINK, s.color(), origin, 1, 0);
        fxAt(level, Kind.ORB, 0x201840, origin.add(0, 1.2, 0), 1.3f, 20);
        fxAt(level, Kind.VORTEX, s.color(), origin, 5, 20);
        repeat(p, 20, 4, () -> {
            for (LivingEntity e : foes(p, origin, 7)) pull(e, origin, 0.9);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, origin.x, origin.y + 1.2, origin.z, 20, 1.5, 1, 1.5, 0.02);
        });
        later(p, 20, () -> {
            for (LivingEntity e : foes(p, origin, 4)) magic(p, s, e, 14 * m(l, tier(p)) + wis(p, 0.3f));
            sound(level, origin, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 1.5f);
            fxAt(level, Kind.NOVA, s.color(), origin, 4.5f, 0);
            fxAt(level, Kind.ROAR, 0xFFFFFF, origin, 4.5f, 0);
            stars(level, origin.add(0, 1, 0), 40, 1);
        });
        return true;
    }

    static boolean constellation(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        List<LivingEntity> near = new ArrayList<>(foes(p, p.position(), 16));
        if (near.isEmpty()) return false;
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        List<LivingEntity> stars = near.subList(0, Math.min(6, near.size()));
        int n = stars.size();
        sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, 0.8f);
        for (int i = 0; i < n; i++) {
            LivingEntity e = stars.get(i);
            fx(e, Kind.MARK, s.color(), e.position(), e.getBbHeight(), 20);
            fxAt(level, Kind.ORB, s.color(), e.position().add(0, e.getBbHeight() + 1.2, 0), 0.5f, 22);
            if (i + 1 < n) fx(e, Kind.BEAM, s.color(), mid(stars.get(i + 1)), 0.12f, 20);
        }
        later(p, 20, () -> {
            for (LivingEntity e : stars) {
                if (!e.isAlive()) continue;
                magic(p, s, e, (8 + 2 * n) * m(l, tier(p)) + wis(p, 0.25f));
                fxAt(level, Kind.NOVA, s.color(), e.position(), 2.2f, 0);
                fxAt(level, Kind.GEYSER, s.color(), e.position(), 3, 0);
                stars(level, mid(e), 12, 0.4);
            }
            sound(p, SoundEvents.AMETHYST_CLUSTER_BREAK, 2f, 0.6f);
        });
        return true;
    }

    static boolean supernova(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
        fx(p, Kind.AURA, s.color(), p.position(), 1.4f, 16);
        fxAt(level, Kind.ORB, s.color(), p.position().add(0, 1.1, 0), 1.6f, 16);
        repeat(p, 16, 4, () -> level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 10, 3, 1.5, 3, -0.2));
        later(p, 16, () -> {
            Vec3 at = p.position();
            for (LivingEntity e : foes(p, at, 9)) {
                magic(p, s, e, 20 * m(l, tier(p)) + wis(p, 0.4f));
                push(e, at, 1.4, 0.5);
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            }
            sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.4f);
            fx(p, Kind.NOVA, s.color(), at, 9, 0);
            fx(p, Kind.ROAR, 0xFFFFFF, at, 9, 0);
            fx(p, Kind.PULSE, s.color(), at, 12, 0);
            stars(level, at.add(0, 1, 0), 80, 2.5);
        });
        return true;
    }

    static boolean astralAegis(ServerPlayer p, Skill s, int l) {
        int ticks = aegisTicks(l);
        SkillAPI.setBuff(p, BUFF_AEGIS, ticks);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 1f, 1.3f);
        fx(p, Kind.DOME, s.color(), p.position(), 1.4f, ticks);
        fx(p, Kind.BLADES, s.color(), p.position(), 1.5f, ticks);
        fx(p, Kind.PULSE, s.color(), p.position(), 4, 0);
        return true;
    }

    static boolean genesisRay(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 54, 2, false, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2, false, false, false));
        sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 1f, 1.2f);
        fx(p, Kind.AURA, s.color(), p.position(), 1.5f, 54);
        fx(p, Kind.PULSE, s.color(), p.position(), 6, 0);
        repeat(p, 50, 2, () -> {
            Vec3[] ray = ray(p, 24);
            fx(p, Kind.BEAM, s.color(), ray[1], 1.1f, 3);
            stars(level, ray[0].lerp(ray[1], p.getRandom().nextDouble()), 4, 0.4);
        });
        // knots of light strung along the beam, so it reads from any side
        repeat(p, 50, 4, () -> {
            Vec3[] ray = ray(p, 24);
            for (int i = 1; i <= 5; i++) fxAt(level, Kind.ORB, i % 2 == 0 ? 0xFFFFFF : s.color(), ray[0].lerp(ray[1], i / 5.0), 0.9f, 5);
            fxAt(level, Kind.NOVA, s.color(), ground(level, ray[1]), 2.5f, 0);
        });
        repeat(p, 50, 5, () -> {
            Vec3[] ray = ray(p, 24);
            for (LivingEntity e : along(p, ray[0], ray[1], 1.6)) {
                magic(p, s, e, 9 * m(l, tier(p)) + wis(p, 0.15f));
                fx(e, Kind.SPLASH, s.color(), mid(e), 1.6f, 0);
            }
            sound(p, SoundEvents.BEACON_AMBIENT, 1.6f, 1.8f);
        });
        later(p, 50, () -> {
            Vec3 end = ground(level, ray(p, 24)[1]);
            for (LivingEntity e : foes(p, end, 6)) magic(p, s, e, 30 * m(l, tier(p)) + wis(p, 0.5f));
            sound(level, end, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.8f);
            sound(level, end, SoundEvents.WARDEN_SONIC_BOOM, 1f, 1.2f);
            fxAt(level, Kind.NOVA, s.color(), end, 6, 0);
            fxAt(level, Kind.ROAR, 0xFFFFFF, end, 6, 0);
            fxAt(level, Kind.CRACK, s.color(), end, 6, 0);
            fxAt(level, Kind.GEYSER, s.color(), end, 6, 0);
            stars(level, end.add(0, 1, 0), 80, 2);
        });
        return true;
    }

    // ================================================================ passives

    static {
        bonus(BONE_ARMOR, Attributes.ARMOR, 0.6f, ADD, "armor");
        bonus(BONE_ARMOR, Attributes.ARMOR_TOUGHNESS, 0.2f, ADD, "toughness");
    }

    /** More mana from the mage subclasses' passives. */
    static float manaBonus(SkillData data) {
        return tideMana(data.level(HIGH_TIDE.path())) + mindMana(data.level(STELLAR_MIND.path()));
    }

    /** Extra damage, in percent, when a mage subclass player strikes. */
    static float strikeBonus(ServerPlayer p, SkillData data, LivingEntity target) {
        float bonus = 0;
        int mania = data.level(PYROMANIA.path());
        if (mania > 0 && burning(target)) bonus += 2 * mania;
        int conduct = data.level(CONDUCTIVITY.path());
        if (conduct > 0 && (target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || target.isInWaterOrRain())) bonus += 2 * conduct;
        int embrace = data.level(DEATHS_EMBRACE.path());
        if (embrace > 0 && p.getHealth() < p.getMaxHealth() * 0.5f) bonus += 3 * embrace;
        int zenith = data.level(ZENITH.path());
        if (zenith > 0) bonus += 3 * zenith;
        return bonus;
    }

    /** Curse of Frailty: extra damage, in percent, a cursed creature takes from anyone. */
    static float frailty(LivingEntity target, long now) {
        if (FRAIL.isEmpty()) return 0;
        long[] curse = FRAIL.get(target);
        if (curse == null) return 0;
        if (curse[0] <= now) {
            FRAIL.remove(target);
            return 0;
        }
        return curse[1];
    }

    static void afterStrike(ServerPlayer p, SkillData data, LivingEntity target) {
        int over = data.level(OVERCHARGE.path());
        if (over > 0 && p.getRandom().nextFloat() * 100 < overchargeChance(over)) {
            stun(target, 20);
            fx(target, Kind.SPLASH, OVERCHARGE.color(), mid(target), 1.2f, 0);
        }
        int tow = data.level(UNDERTOW.path());
        if (tow > 0) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Math.round(undertowSeconds(tow) * 20), 1));
        int might = data.level(NECROTIC_MIGHT.path());
        if (might > 0) target.addEffect(new MobEffectInstance(MobEffects.WITHER, Math.round(mightSeconds(might) * 20), 0));
    }

    /** A mage subclass player is struck. Returns the damage to take. */
    static float whenStruck(ServerPlayer p, SkillData data, LivingEntity attacker, boolean melee, DamageSource source, float amount, long now) {
        int molten = data.level(MOLTEN_SKIN.path());
        if (molten > 0) {
            if (source.is(DamageTypeTags.IS_FIRE)) amount *= 1 - 0.04f * molten;
            if (melee && attacker != null && attacker.isAlive()) ignite(p, attacker, 10 + 3 * molten);
        }
        int ebb = data.level(EBB_AND_FLOW.path());
        if (ebb > 0) amount *= 1 - 0.02f * ebb;
        if (data.hasBuff(BUFF_AEGIS, now) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            int aegis = Math.max(1, data.level(ASTRAL_AEGIS.path()));
            amount *= 1 - aegisGuard(aegis) / 100f;
            if (attacker != null && attacker.isAlive() && attacker != p) {
                later(p, 1, () -> {
                    if (!attacker.isAlive()) return;
                    attacker.hurt(p.damageSources().indirectMagic(p, p), 3 + aegis);
                    fx(p, Kind.BOLT, ASTRAL_AEGIS.color(), mid(attacker), 0.3f, 0);
                });
            }
        }
        return amount;
    }

    static void tickSecond(ServerPlayer p, SkillData data) {
        float mana = surgeMana(data.level(STORM_SURGE.path())) + flowMana(data.level(COSMIC_FLOW.path()));
        if (mana > 0) SystemAPI.restoreMana(p, mana);
    }

    static void onKill(ServerPlayer p, SkillData data, LivingEntity dead) {
        int ember = data.level(EMBER_HEART.path());
        if (ember > 0) SystemAPI.restoreMana(p, emberMana(ember));
        FRAIL.remove(dead);
    }

    /** Touch the class so its skills are registered. */
    static void init() {}

    private MagePath() {}
}
