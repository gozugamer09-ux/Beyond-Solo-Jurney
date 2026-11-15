package com.beyondsolo.skill;

import com.beyondsolo.combat.DamageElement;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
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
 * The Assassin subclasses' own skill sets, on top of the starters in {@link SubclassSkills}: ten more each for the
 * Nightblade, Toxin Weaver, Mirage Dancer, Bounty Reaper and the rare Eclipse Sovereign. Same shape as
 * {@link WarriorPath}.
 */
public final class AssassinPath {
    public static final String BUFF_SHROUD = "dusk_shroud";
    public static final String BUFF_MIRROR = "mirror_image";
    public static final String BUFF_MOONLESS = "moonless_veil";

    /** Final Contract marks: creature -> {end game time, bonus percent, caster entity id}. Server thread only. */
    static final Map<LivingEntity, long[]> CONTRACTS = new WeakHashMap<>();

    private static final Subclass NB = Subclass.NIGHTBLADE, TW = Subclass.TOXIN_WEAVER, MD = Subclass.MIRAGE_DANCER,
            BR = Subclass.BOUNTY_REAPER, ES = Subclass.ECLIPSE_SOVEREIGN;
    private static final PlayerRank A = PlayerRank.A, S = PlayerRank.S;

    private static boolean unaware(ServerPlayer p, LivingEntity e) {
        return !(e instanceof Mob mob) || mob.getTarget() != p;
    }

    // ================================================================ nightblade

    static float shroudSeconds(int l) { return 4 + l / 2f; }
    static float shroudBonus(int l) { return 40 + 6 * l; }

    public static final Skill SHADOW_FANG = active("shadow_fang", NB, A, 22, 0x8060E0, 14, 160, 90,
            AssassinPath::shadowFang, (l, t) -> a(f(9 * m(l, t))));
    public static final Skill DUSK_SHROUD = active("dusk_shroud", NB, A, 24, 0x5040A0, 16, 400, 260,
            AssassinPath::duskShroud, (l, t) -> a(f(shroudSeconds(l)), f(shroudBonus(l))));
    public static final Skill KILLER_INSTINCT = passive("killer_instinct", NB, A, 26, 0xA070D0, (l, t) -> a(f(2 * l)));
    public static final Skill THROAT_SLIT = active("throat_slit", NB, A, 29, 0xC04070, 16, 220, 130,
            AssassinPath::throatSlit, (l, t) -> a(f(11 * m(l, t))));
    public static final Skill UMBRAL_DECOY = active("umbral_decoy", NB, A, 32, 0x7050C0, 20, 440, 280,
            AssassinPath::umbralDecoy, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill NIGHTFALL_STEP = passive("nightfall_step", NB, A, 35, 0x9080E0, (l, t) -> a(f(1.5f * l), f(2 * l)));
    public static final Skill BLACK_LOTUS = active("black_lotus", NB, A, 38, 0x6030B0, 22, 300, 180,
            AssassinPath::blackLotus, (l, t) -> a(f(5 * m(l, t))));
    public static final Skill NIGHT_TERROR = active("night_terror", NB, A, 42, 0x402080, 22, 480, 300,
            AssassinPath::nightTerror, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill FINISHER = passive("finisher", NB, A, 46, 0xD05080, (l, t) -> a(f(3 * l)));
    public static final Skill ENDLESS_NIGHT = active("endless_night", NB, S, 50, 0x7040FF, 40, 1000, 640,
            AssassinPath::endlessNight, (l, t) -> a(f(6 * m(l, t)), f(20 * m(l, t))));

    static boolean shadowFang(ServerPlayer p, Skill s, int l) {
        Vec3 start = p.position();
        Vec3 dir = facing(p);
        sound(p, SoundEvents.PHANTOM_BITE, 1f, 1.4f);
        fx(p, Kind.DASH, s.color(), start, 1, 8);
        dash(p, dir, 6.5, 5, 2.0, e -> {
            float dmg = 9 * m(l, tier(p)) + stat(p, Stat.AGI, 0.2f);
            boolean behind = flat(e.getLookAngle(), e).dot(dir) > 0.3;
            hit(p, s, e, behind || unaware(p, e) ? dmg * 1.5f : dmg);
            fx(e, Kind.SLASH, s.color(), e.position(), 2.2f, behind ? 1 : 0);
            sound(e, SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 1.3f);
        }, null);
        return true;
    }

    static boolean duskShroud(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(shroudSeconds(l) * 20);
        SkillAPI.setBuff(p, BUFF_SHROUD, ticks);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0, false, false, true));
        for (LivingEntity e : foes(p, p.position(), 16)) if (e instanceof Mob mob && mob.getTarget() == p) mob.setTarget(null);
        sound(p, SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 0.8f, 1.3f);
        fx(p, Kind.VEIL, s.color(), p.position(), 1.2f, ticks);
        fx(p, Kind.PULSE, s.color(), p.position(), 4, 0);
        return true;
    }

    static boolean throatSlit(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 6, x -> canHit(p, x));
        if (target == null) return false;
        float dmg = 11 * m(l, tier(p)) + stat(p, Stat.AGI, 0.25f);
        boolean silent = unaware(p, target);
        hit(p, s, target, silent ? dmg * 2 : dmg);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
        sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.7f);
        sound(p, SoundEvents.WITHER_SKELETON_HURT, 0.5f, 1.6f);
        fx(p, Kind.SLASH, s.color(), p.position(), 2.6f, 1);
        fxAt(p.serverLevel(), Kind.GEYSER, s.color(), target.position().add(0, target.getBbHeight() * 0.6, 0), silent ? 2.6f : 1.6f, 0);
        return true;
    }

    static boolean umbralDecoy(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        for (LivingEntity e : foes(p, at, 12)) if (e instanceof Mob mob && mob.getTarget() == p) mob.setTarget(null);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, true));
        sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 0.8f);
        fxAt(level, Kind.VEIL, s.color(), at, 1.2f, 40);
        fxAt(level, Kind.ZONE, s.color(), at, 4, 40);
        blinkForward(p, 5);
        fx(p, Kind.BLINK, s.color(), at, 1, 0);
        later(p, 40, () -> {
            for (LivingEntity e : foes(p, at, 4)) {
                magic(p, s, e, 8 * m(l, tier(p)) + stat(p, Stat.AGI, 0.15f));
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            }
            sound(level, at, SoundEvents.WITHER_BREAK_BLOCK, 0.5f, 1.6f);
            fxAt(level, Kind.NOVA, s.color(), at, 4, 0);
            fxAt(level, Kind.SPLASH, s.color(), at.add(0, 1, 0), 3, 1);
        });
        return true;
    }

    static boolean blackLotus(ServerPlayer p, Skill s, int l) {
        sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 0.8f, 1.5f);
        fx(p, Kind.BLOSSOM, s.color(), p.position(), 5, 0);
        fx(p, Kind.SPIN, s.color(), p.position(), 4.5f, 16);
        for (int i = 0; i < 3; i++) {
            later(p, i * 6, () -> {
                for (LivingEntity e : foes(p, p.position(), 5)) {
                    hit(p, s, e, 5 * m(l, tier(p)) + stat(p, Stat.AGI, 0.1f));
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                }
                sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.5f);
            });
        }
        later(p, 14, () -> fx(p, Kind.BLOSSOM, 0xE0D0FF, p.position(), 5, 0));
        return true;
    }

    static boolean nightTerror(ServerPlayer p, Skill s, int l) {
        Vec3 at = p.position();
        for (LivingEntity e : foes(p, at, 8)) {
            magic(p, s, e, 8 * m(l, tier(p)) + stat(p, Stat.AGI, 0.1f));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
            e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
            if (e instanceof Mob mob) mob.setTarget(null);
            push(e, at, 1.1, 0.4);
        }
        sound(p, SoundEvents.WARDEN_ROAR, 0.7f, 1.6f);
        sound(p, SoundEvents.PHANTOM_AMBIENT, 1f, 0.5f);
        fx(p, Kind.ROAR, s.color(), at, 8, 0);
        fx(p, Kind.VEIL, s.color(), at, 1.5f, 30);
        fxAt(p.serverLevel(), Kind.QUAKE, s.color(), at, 8, 30);
        return true;
    }

    static boolean endlessNight(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 20, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        Vec3 home = p.position();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 44, 4, false, false, false));
        sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 0.7f, 1.7f);
        fxAt(level, Kind.ZONE, s.color(), target.position(), 4, 40);
        fx(target, Kind.MARK, s.color(), target.position(), target.getBbHeight(), 44);
        for (int i = 0; i < 7; i++) {
            final int k = i;
            later(p, i * 4, () -> {
                if (!target.isAlive()) return;
                Vec3 from = p.position();
                double ang = k * 2.4;
                double gap = target.getBbWidth() / 2 + 1.2;
                Vec3 spot = target.position().add(Math.cos(ang) * gap, 0, Math.sin(ang) * gap);
                if (!level.noCollision(p, p.getBoundingBox().move(spot.subtract(from)))) spot = from;
                SubclassActions.teleport(p, spot, target);
                hit(p, s, target, 6 * m(l, tier(p)) + stat(p, Stat.AGI, 0.12f));
                sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.4f + 0.08f * k);
                fx(p, Kind.BLINK, s.color(), from, 1, 0);
                fx(p, Kind.SLASH, s.color(), p.position(), 3, k % 2);
            });
        }
        later(p, 32, () -> {
            Vec3 from = p.position();
            SubclassActions.teleport(p, home, target);
            fx(p, Kind.BLINK, s.color(), from, 1, 0);
            if (!target.isAlive()) return;
            fxAt(level, Kind.CLEAVE, s.color(), target.position(), 3, 6);
        });
        later(p, 38, () -> {
            if (!target.isAlive()) return;
            hit(p, s, target, 20 * m(l, tier(p)) + stat(p, Stat.AGI, 0.4f));
            for (LivingEntity e : foes(p, target.position(), 3.5)) if (e != target) hit(p, s, e, 8 * m(l, tier(p)));
            sound(target, SoundEvents.WITHER_BREAK_BLOCK, 0.8f, 1.2f);
            fxAt(level, Kind.CRACK, s.color(), target.position(), 4, 0);
            fxAt(level, Kind.NOVA, s.color(), target.position(), 4, 0);
        });
        return true;
    }

    // ================================================================ toxin weaver

    static float dartSeconds(int l) { return 2 + l / 5f; }
    static float thornPoison(int l) { return 5 + 2 * l; }
    static float pandemicSeconds(int l) { return 6 + l / 2f; }

    public static final Skill VENOM_SPIT = active("venom_spit", TW, A, 22, 0x8FE04A, 12, 100, 60,
            AssassinPath::venomSpit, (l, t) -> a(f(7 * m(l, t))));
    public static final Skill CAUSTIC_SLASH = active("caustic_slash", TW, A, 24, 0xB0F060, 14, 160, 90,
            AssassinPath::causticSlash, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill TOXIC_BLOOD = passive("toxic_blood", TW, A, 26, 0x70C040, (l, t) -> a(f(thornPoison(l))));
    public static final Skill SPORE_BURST = active("spore_burst", TW, A, 29, 0xC0E070, 18, 240, 150,
            AssassinPath::sporeBurst, (l, t) -> a(f(6 * m(l, t))));
    public static final Skill PARALYTIC_DART = active("paralytic_dart", TW, A, 32, 0xE0E060, 16, 320, 200,
            AssassinPath::paralyticDart, (l, t) -> a(f(5 * m(l, t)), f(dartSeconds(l))));
    public static final Skill POTENCY = passive("potency", TW, A, 35, 0xA0FF60, (l, t) -> a(f(2 * l)));
    public static final Skill PLAGUE_TRAIL = active("plague_trail", TW, A, 38, 0x60B030, 22, 400, 260,
            AssassinPath::plagueTrail, (l, t) -> a(f(2.5f * m(l, t))));
    public static final Skill NEUROTOXIN = active("neurotoxin", TW, A, 42, 0xD0FF40, 22, 360, 220,
            AssassinPath::neurotoxin, (l, t) -> a(f(14 * m(l, t))));
    public static final Skill ANTIBODIES = passive("antibodies", TW, A, 46, 0x90D0A0, (l, t) -> a(f(3 * l)));
    public static final Skill PANDEMIC = active("pandemic", TW, S, 50, 0x70FF30, 40, 1100, 700,
            AssassinPath::pandemic, (l, t) -> a(f(pandemicSeconds(l)), f(5 * m(l, t))));

    static boolean venomSpit(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 20, x -> canHit(p, x));
        if (target == null) return false;
        Vec3 at = target.position().add(0, target.getBbHeight() / 2, 0);
        magic(p, s, target, 7 * m(l, tier(p)) + stat(p, Stat.AGI, 0.1f) + stat(p, Stat.INT, 0.1f));
        Statuses.apply(target, DamageElement.POISON, 25 + 3 * l, p);
        sound(p, SoundEvents.LLAMA_SPIT, 1f, 0.8f);
        sound(target, SoundEvents.SLIME_SQUISH, 1f, 1.2f);
        fx(p, Kind.SHOT, s.color(), at, 1, 0);
        fxAt(p.serverLevel(), Kind.SPLASH, s.color(), at, 1.8f, 0);
        return true;
    }

    static boolean causticSlash(ServerPlayer p, Skill s, int l) {
        for (LivingEntity e : cone(p, 4.5, 0.25)) {
            hit(p, s, e, 8 * m(l, tier(p)) + stat(p, Stat.AGI, 0.2f));
            Statuses.addVenomStack(e, p);
        }
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.2f);
        sound(p, SoundEvents.BREWING_STAND_BREW, 0.8f, 1.6f);
        fx(p, Kind.SLASH, s.color(), p.position(), 3.8f, 0);
        fx(p, Kind.FAN, s.color(), p.position().add(facing(p).scale(4.5)), 1, 0);
        return true;
    }

    static boolean sporeBurst(ServerPlayer p, Skill s, int l) {
        Vec3 at = p.position();
        ServerLevel level = p.serverLevel();
        for (LivingEntity e : foes(p, at, 5)) {
            magic(p, s, e, 6 * m(l, tier(p)) + stat(p, Stat.INT, 0.1f) + stat(p, Stat.AGI, 0.05f));
            Statuses.apply(e, DamageElement.POISON, 22 + 2 * l, p);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
        }
        sound(p, SoundEvents.PUFFER_FISH_BLOW_UP, 1.2f, 0.7f);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y + 1, at.z, 80, 2.5, 0.8, 2.5, 0.02);
        level.sendParticles(ParticleTypes.SNEEZE, at.x, at.y + 0.8, at.z, 40, 2, 0.5, 2, 0.05);
        fx(p, Kind.BLOSSOM, s.color(), at, 5, 0);
        fx(p, Kind.PULSE, s.color(), at, 5, 0);
        return true;
    }

    static boolean paralyticDart(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 24, x -> canHit(p, x));
        if (target == null) return false;
        int ticks = Math.round(dartSeconds(l) * 20);
        hit(p, s, target, 5 * m(l, tier(p)) + stat(p, Stat.AGI, 0.1f));
        stun(target, ticks);
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 1.6f);
        fx(p, Kind.SHOT, s.color(), target.position().add(0, target.getBbHeight() * 0.7, 0), 1, 0);
        fx(target, Kind.BIND, s.color(), target.position(), 1.2f, ticks);
        return true;
    }

    static boolean plagueTrail(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, false, true));
        sound(p, SoundEvents.BREWING_STAND_BREW, 1f, 0.6f);
        fx(p, Kind.AURA, s.color(), p.position(), 1.2f, 100);
        repeat(p, 100, 10, () -> {
            Vec3 at = p.position();
            fxAt(level, Kind.ZONE, s.color(), at, 2.5f, 60);
            level.sendParticles(ParticleTypes.SNEEZE, at.x, at.y + 0.3, at.z, 8, 1, 0.2, 1, 0.01);
            for (int t = 0; t < 60; t += 20) {
                later(p, t, () -> {
                    for (LivingEntity e : foes(p, at, 2.5)) {
                        magic(p, s, e, 2.5f * m(l, tier(p)) + stat(p, Stat.INT, 0.04f));
                        Statuses.apply(e, DamageElement.POISON, 10 + l, p);
                    }
                });
            }
        });
        return true;
    }

    static boolean neurotoxin(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 16, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        Statuses.addVenomStack(target, p);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
        sound(p, SoundEvents.WITCH_THROW, 1f, 0.8f);
        fx(p, Kind.SHOT, s.color(), target.position().add(0, target.getBbHeight() / 2, 0), 1, 0);
        fx(target, Kind.MARK, s.color(), target.position(), target.getBbHeight(), 60);
        later(p, 60, () -> {
            Vec3 at = target.position();
            if (target.isAlive()) {
                float dmg = 14 * m(l, tier(p)) + stat(p, Stat.AGI, 0.15f) + stat(p, Stat.INT, 0.15f);
                magic(p, s, target, poisoned(target) ? dmg * 2 : dmg);
            }
            for (LivingEntity e : foes(p, at, 4)) Statuses.apply(e, DamageElement.POISON, 30 + 3 * l, p);
            sound(level, at, SoundEvents.SLIME_BLOCK_BREAK, 1.5f, 0.6f);
            fxAt(level, Kind.GEYSER, s.color(), at, 4, 0);
            fxAt(level, Kind.SPLASH, s.color(), at.add(0, 0.2, 0), 4, 0);
        });
        return true;
    }

    static boolean pandemic(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 20);
        int ticks = Math.round(pandemicSeconds(l) * 20);
        sound(p, SoundEvents.WITCH_CELEBRATE, 1f, 0.6f);
        sound(level, at, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 0.6f);
        fxAt(level, Kind.ZONE, s.color(), at, 9, ticks);
        fxAt(level, Kind.QUAKE, 0x406020, at, 9, ticks);
        repeat(p, ticks, 20, () -> {
            for (LivingEntity e : foes(p, at, 9)) {
                magic(p, s, e, 5 * m(l, tier(p)) + stat(p, Stat.INT, 0.1f) + stat(p, Stat.AGI, 0.05f));
                Statuses.addVenomStack(e, p);
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
            }
            level.sendParticles(ParticleTypes.SNEEZE, at.x, at.y + 0.6, at.z, 60, 4.5, 0.5, 4.5, 0.01);
            double ang = p.getRandom().nextDouble() * Math.PI * 2;
            double rad = p.getRandom().nextDouble() * 7;
            fxAt(level, Kind.GEYSER, s.color(), ground(level, at.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad)), 4, 0);
            sound(level, at, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1.5f, 0.6f);
        });
        return true;
    }

    // ================================================================ mirage dancer

    static float mirrorSeconds(int l) { return 5 + l / 2f; }
    static float mirrorDodge(int l) { return 35 + 3 * l; }

    public static final Skill PHANTOM_STEP = active("phantom_step", MD, A, 22, 0xB8A0FF, 12, 140, 80,
            AssassinPath::phantomStep, (l, t) -> a(f(7 * m(l, t))));
    public static final Skill GALE_KICK = active("gale_kick", MD, A, 24, 0xC8F0FF, 12, 160, 90,
            AssassinPath::galeKick, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill FEATHERFALL = passive("featherfall", MD, A, 26, 0xE0E8FF, (l, t) -> a(f(2 * l), f(l)));
    public static final Skill MIRROR_IMAGE = active("mirror_image", MD, A, 29, 0xD0C0FF, 18, 500, 320,
            AssassinPath::mirrorImage, (l, t) -> a(f(mirrorSeconds(l)), f(mirrorDodge(l))));
    public static final Skill CYCLONE_DANCE = active("cyclone_dance", MD, A, 32, 0xA8E8F0, 20, 300, 180,
            AssassinPath::cycloneDance, (l, t) -> a(f(3.5f * m(l, t))));
    public static final Skill TAILWIND = passive("tailwind", MD, A, 35, 0xB0F0E0, (l, t) -> a(f(3 * l)));
    public static final Skill UPDRAFT = active("updraft", MD, A, 38, 0xD8FFF8, 18, 280, 170,
            AssassinPath::updraft, (l, t) -> a(f(7 * m(l, t))));
    public static final Skill AFTERIMAGE_BARRAGE = active("afterimage_barrage", MD, A, 42, 0xC0B0FF, 22, 320, 200,
            AssassinPath::afterimageBarrage, (l, t) -> a(f(5 * m(l, t))));
    public static final Skill SLIPSTREAM = passive("slipstream", MD, A, 46, 0xA0D0FF, (l, t) -> a(f(2 * l)));
    public static final Skill TEMPEST_WALTZ = active("tempest_waltz", MD, S, 50, 0xE0D0FF, 40, 1000, 640,
            AssassinPath::tempestWaltz, (l, t) -> a(f(7 * m(l, t)), f(16 * m(l, t))));

    static boolean phantomStep(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 from = blinkForward(p, 8);
        sound(level, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.4f);
        fx(p, Kind.BLINK, s.color(), from, 1, 0);
        fxAt(level, Kind.VEIL, s.color(), from, 1, 8);
        later(p, 6, () -> {
            for (LivingEntity e : foes(p, from, 3.2)) hit(p, s, e, 7 * m(l, tier(p)) + stat(p, Stat.AGI, 0.2f));
            sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.5f);
            fxAt(level, Kind.SLASH, s.color(), from, 3.2f, 0);
            fxAt(level, Kind.SPLASH, s.color(), from.add(0, 1, 0), 2.6f, 1);
        });
        return true;
    }

    static boolean galeKick(ServerPlayer p, Skill s, int l) {
        Vec3 dir = facing(p);
        for (LivingEntity e : cone(p, 4, 0.3)) {
            hit(p, s, e, 8 * m(l, tier(p)) + stat(p, Stat.AGI, 0.2f));
            e.setDeltaMovement(dir.x * 1.5, 0.45, dir.z * 1.5);
            e.hurtMarked = true;
        }
        sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 1f, 1.2f);
        fx(p, Kind.FAN, s.color(), p.position().add(dir.scale(5)), 1, 0);
        fx(p, Kind.WAVE, s.color(), p.position().add(dir.scale(7)), 1.4f, 2);
        return true;
    }

    static boolean mirrorImage(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(mirrorSeconds(l) * 20);
        SkillAPI.setBuff(p, BUFF_MIRROR, ticks);
        sound(p, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1f, 1.2f);
        fx(p, Kind.BLADES, s.color(), p.position(), 1.4f, ticks);
        fx(p, Kind.AURA, s.color(), p.position(), 1.1f, ticks);
        return true;
    }

    static boolean cycloneDance(ServerPlayer p, Skill s, int l) {
        sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 0.9f);
        fx(p, Kind.SPIN, s.color(), p.position(), 3.5f, 30);
        fx(p, Kind.VORTEX, s.color(), p.position(), 3.2f, 30);
        repeat(p, 30, 6, () -> {
            for (LivingEntity e : foes(p, p.position(), 3.8)) hit(p, s, e, 3.5f * m(l, tier(p)) + stat(p, Stat.AGI, 0.08f));
            for (LivingEntity e : foes(p, p.position(), 7)) pull(e, p.position(), 0.5);
            sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.6f);
        });
        return true;
    }

    static boolean updraft(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        List<LivingEntity> caught = foes(p, at, 5);
        for (LivingEntity e : caught) {
            hit(p, s, e, 7 * m(l, tier(p)) + stat(p, Stat.AGI, 0.15f));
            launch(e, 1.15);
            e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false));
            fxAt(level, Kind.GEYSER, s.color(), e.position(), 4, 0);
        }
        move(p, new Vec3(p.getDeltaMovement().x, 0.9, p.getDeltaMovement().z));
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
        sound(p, SoundEvents.BREEZE_JUMP, 1f, 0.8f);
        sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 1f, 0.7f);
        fx(p, Kind.PULSE, s.color(), at, 5, 0);
        fxAt(level, Kind.VORTEX, s.color(), at, 4, 14);
        return true;
    }

    static boolean afterimageBarrage(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 dir = facing(p);
        Vec3 start = p.position();
        sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.6f);
        for (int i = 1; i <= 5; i++) {
            Vec3 at = ground(level, start.add(dir.scale(i * 2.2)));
            final int k = i;
            later(p, i * 3, () -> {
                for (LivingEntity e : foes(p, at, 2.4)) hit(p, s, e, 5 * m(l, tier(p)) + stat(p, Stat.AGI, 0.1f));
                sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.2f + 0.1f * k);
                fxAt(level, Kind.BLINK, s.color(), at, 1, 0);
                fxAt(level, Kind.SLASH, s.color(), at, 2.6f, k % 2);
                fxAt(level, Kind.SPLASH, s.color(), at.add(0, 1, 0), 2f, 1);
            });
        }
        return true;
    }

    static boolean tempestWaltz(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 home = p.position();
        if (foes(p, home, 12).isEmpty()) return false;
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false, false));
        sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 0.6f);
        fxAt(level, Kind.VORTEX, s.color(), home, 6, 50);
        fxAt(level, Kind.ZONE, s.color(), home, 12, 54);
        for (int i = 0; i < 12; i++) {
            final int k = i;
            later(p, i * 4, () -> {
                List<LivingEntity> near = foes(p, home, 12);
                if (near.isEmpty()) return;
                LivingEntity target = near.get(p.getRandom().nextInt(near.size()));
                Vec3 from = p.position();
                blinkTo(p, target, k % 2 == 0);
                hit(p, s, target, 7 * m(l, tier(p)) + stat(p, Stat.AGI, 0.12f));
                sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.3f + 0.05f * k);
                fx(p, Kind.DASH, s.color(), from, 1, 6);
                fx(p, Kind.SLASH, s.color(), p.position(), 3, k % 2);
            });
        }
        later(p, 50, () -> {
            Vec3 from = p.position();
            p.teleportTo(level, home.x, home.y, home.z, p.getYRot(), p.getXRot());
            for (LivingEntity e : foes(p, home, 8)) {
                hit(p, s, e, 16 * m(l, tier(p)) + stat(p, Stat.AGI, 0.3f));
                push(e, home, 1.0, 0.6);
            }
            sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 1.4f, 0.5f);
            sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.5f, 1.6f);
            fx(p, Kind.BLINK, s.color(), from, 1, 0);
            fx(p, Kind.BLOSSOM, s.color(), home, 8, 0);
            fx(p, Kind.PULSE, 0xFFFFFF, home, 8, 0);
            fx(p, Kind.SPIN, s.color(), home, 6, 8);
        });
        return true;
    }

    // ================================================================ bounty reaper

    static float ledgerSeconds(int l) { return 3 + 0.3f * l; }
    static float contractBonus(int l) { return 30 + 3 * l; }

    public static final Skill CHAIN_HOOK = active("chain_hook", BR, A, 22, 0xC0C8D0, 12, 200, 120,
            AssassinPath::chainHook, (l, t) -> a(f(6 * m(l, t))));
    public static final Skill REAPING_ARC = active("reaping_arc", BR, A, 24, 0xD04060, 14, 160, 90,
            AssassinPath::reapingArc, (l, t) -> a(f(9 * m(l, t))));
    public static final Skill BOUNTY_LEDGER = passive("bounty_ledger", BR, A, 26, 0xE0C060, (l, t) -> a(f(ledgerSeconds(l))));
    public static final Skill CRIPPLING_SHOT = active("crippling_shot", BR, A, 29, 0xE08090, 12, 200, 120,
            AssassinPath::cripplingShot, (l, t) -> a(f(7 * m(l, t))));
    public static final Skill PURSUIT = active("pursuit", BR, A, 32, 0xF05070, 14, 240, 140,
            AssassinPath::pursuit, (l, t) -> a(f(6 * m(l, t))));
    public static final Skill APPRAISE_WEAKNESS = passive("appraise_weakness", BR, A, 35, 0xFFD080, (l, t) -> a(f(2 * l)));
    public static final Skill BOLA_SNARE = active("bola_snare", BR, A, 38, 0xB0A090, 18, 360, 220,
            AssassinPath::bolaSnare, (l, t) -> a(f(5 * m(l, t))));
    public static final Skill COLLECT_DUES = active("collect_dues", BR, A, 42, 0xFF6080, 24, 400, 260,
            AssassinPath::collectDues, (l, t) -> a(f(9 * m(l, t))));
    public static final Skill DEAD_OR_ALIVE = passive("dead_or_alive", BR, A, 46, 0xC09090, (l, t) -> a(f(0.3f * l), f(0.3f * l)));
    public static final Skill FINAL_CONTRACT = active("final_contract", BR, S, 50, 0xFF2040, 36, 1200, 800,
            AssassinPath::finalContract, (l, t) -> a(f(15 * m(l, t)), f(contractBonus(l))));

    private static boolean marked(LivingEntity e) {
        return e.hasEffect(MobEffects.GLOWING);
    }

    static boolean chainHook(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 16, x -> canHit(p, x));
        if (target == null) return false;
        hit(p, s, target, 6 * m(l, tier(p)) + stat(p, Stat.AGI, 0.1f) + stat(p, Stat.STR, 0.1f));
        Vec3 to = p.position().add(facing(p).scale(1.6));
        Vec3 d = to.subtract(target.position());
        target.setDeltaMovement(d.x * 0.28, 0.35 + Math.max(0, d.y) * 0.2, d.z * 0.28);
        target.hurtMarked = true;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
        sound(p, SoundEvents.CHAIN_BREAK, 1.5f, 0.7f);
        sound(p, SoundEvents.FISHING_BOBBER_RETRIEVE, 1f, 0.6f);
        fx(p, Kind.BOLT, s.color(), target.position().add(0, target.getBbHeight() / 2, 0), 0.3f, 0);
        fx(target, Kind.BIND, s.color(), target.position(), 1.2f, 20);
        return true;
    }

    static boolean reapingArc(ServerPlayer p, Skill s, int l) {
        for (int i = 0; i < 2; i++) {
            final int k = i;
            later(p, i * 5, () -> {
                for (LivingEntity e : cone(p, 5.5, 0)) {
                    float dmg = (k == 0 ? 9 : 4.5f) * m(l, tier(p)) + stat(p, Stat.AGI, 0.12f) + stat(p, Stat.STR, 0.08f);
                    hit(p, s, e, marked(e) ? dmg * 1.5f : dmg);
                }
                sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.6f + 0.3f * k);
                fx(p, Kind.SLASH, k == 0 ? s.color() : 0xFFFFFF, p.position(), 5.5f, k);
            });
        }
        return true;
    }

    static boolean cripplingShot(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 24, x -> canHit(p, x));
        if (target == null) return false;
        hit(p, s, target, 7 * m(l, tier(p)) + stat(p, Stat.AGI, 0.15f));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false));
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.3, 0);
        sound(p, SoundEvents.TRIDENT_THROW.value(), 1f, 1.5f);
        fx(p, Kind.SHOT, s.color(), at, 1, 0);
        fxAt(p.serverLevel(), Kind.SPLASH, s.color(), at, 1.4f, 1);
        return true;
    }

    static boolean pursuit(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 24, x -> canHit(p, x));
        if (target == null) return false;
        Vec3 from = p.position();
        blinkTo(p, target, true);
        hit(p, s, target, 6 * m(l, tier(p)) + stat(p, Stat.AGI, 0.15f));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1));
        sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.4f);
        sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1f, 1f);
        fx(p, Kind.DASH, s.color(), from, 1, 8);
        fx(p, Kind.SLASH, s.color(), p.position(), 2.6f, 0);
        return true;
    }

    static boolean bolaSnare(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 18);
        sound(p, SoundEvents.FISHING_BOBBER_THROW, 1f, 0.6f);
        fx(p, Kind.SHOT, s.color(), at.add(0, 0.5, 0), 1, 0);
        later(p, 3, () -> {
            for (LivingEntity e : foes(p, at, 4)) {
                hit(p, s, e, 5 * m(l, tier(p)) + stat(p, Stat.AGI, 0.1f));
                stun(e, 60);
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
            }
            sound(level, at, SoundEvents.CHAIN_PLACE, 2f, 0.6f);
            fxAt(level, Kind.BIND, s.color(), at, 4, 60);
        });
        return true;
    }

    static boolean collectDues(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        List<LivingEntity> near = foes(p, p.position(), 14);
        if (near.isEmpty()) return false;
        sound(p, SoundEvents.BELL_BLOCK, 1f, 0.5f);
        fx(p, Kind.PULSE, s.color(), p.position(), 14, 0);
        for (LivingEntity e : near) {
            boolean due = marked(e) || SubclassActions.SENTENCES.containsKey(e) || CONTRACTS.containsKey(e);
            fxAt(level, Kind.CLEAVE, s.color(), e.position(), due ? 2.2f : 1.4f, 8);
            later(p, 8, () -> {
                if (!e.isAlive()) return;
                float dmg = 9 * m(l, tier(p)) + stat(p, Stat.AGI, 0.15f);
                hit(p, s, e, due ? dmg * 2 : dmg);
                sound(e, SoundEvents.TRIDENT_HIT, 0.8f, 0.7f);
            });
        }
        return true;
    }

    static boolean finalContract(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 30, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        int ticks = 300;
        CONTRACTS.put(target, new long[]{level.getGameTime() + ticks, Math.round(contractBonus(l)), p.getId()});
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
        sound(p, SoundEvents.BELL_RESONATE, 1f, 0.6f);
        sound(p, SoundEvents.WITHER_SKELETON_AMBIENT, 0.6f, 0.6f);
        fx(target, Kind.MARK, s.color(), target.position(), target.getBbHeight(), ticks);
        fx(target, Kind.BIND, s.color(), target.position(), 1.4f, 40);
        fxAt(level, Kind.CLEAVE, s.color(), target.position(), 3, 10);
        later(p, 10, () -> {
            if (!target.isAlive()) return;
            hit(p, s, target, 15 * m(l, tier(p)) + stat(p, Stat.AGI, 0.3f));
            stun(target, 40);
            sound(target, SoundEvents.ANVIL_LAND, 0.6f, 0.6f);
            fxAt(level, Kind.CRACK, s.color(), target.position(), 3.5f, 0);
        });
        return true;
    }

    // ================================================================ eclipse sovereign (rare)

    static float moonlessSeconds(int l) { return 5 + l / 2f; }
    static float penumbraChance(int l) { return 5 + 2 * l; }

    public static final Skill UMBRAL_LANCE = active("umbral_lance", ES, S, 22, 0x7A4FF0, 20, 140, 80,
            AssassinPath::umbralLance, (l, t) -> a(f(18 * m(l, t))));
    public static final Skill NIGHTFALL = active("nightfall", ES, S, 24, 0x5A2FD0, 24, 240, 140,
            AssassinPath::nightfall, (l, t) -> a(f(16 * m(l, t))));
    public static final Skill VOID_TOUCHED = passive("void_touched", ES, S, 26, 0x9070E0, (l, t) -> a(f(0.7f * l), f(l)));
    public static final Skill BLACK_SUN = active("black_sun", ES, S, 29, 0x3A1890, 30, 440, 280,
            AssassinPath::blackSun, (l, t) -> a(f(6 * m(l, t)), f(18 * m(l, t))));
    public static final Skill MOONLESS_VEIL = active("moonless_veil", ES, S, 32, 0x4830A0, 26, 600, 400,
            AssassinPath::moonlessVeil, (l, t) -> a(f(moonlessSeconds(l))));
    public static final Skill PENUMBRA = passive("penumbra", ES, S, 35, 0x8060C0, (l, t) -> a(f(penumbraChance(l))));
    public static final Skill CRESCENT_REAVER = active("crescent_reaver", ES, S, 38, 0xA080FF, 28, 260, 160,
            AssassinPath::crescentReaver, (l, t) -> a(f(14 * m(l, t))));
    public static final Skill STARLESS_PRISON = active("starless_prison", ES, S, 42, 0x6040C0, 30, 560, 360,
            AssassinPath::starlessPrison, (l, t) -> a(f(12 * m(l, t))));
    public static final Skill LORD_OF_NIGHT = passive("lord_of_night", ES, S, 46, 0x503090, (l, t) -> a(f(2 * l)));
    public static final Skill WORLD_WITHOUT_LIGHT = active("world_without_light", ES, S, 50, 0x6A3FFF, 45, 1800, 1200,
            AssassinPath::worldWithoutLight, (l, t) -> a(f(12 * m(l, t)), f(36 * m(l, t))));

    static boolean umbralLance(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 from = p.getEyePosition().add(0, -0.3, 0);
        Vec3 to = SkillActions.aimPoint(p, 20);
        for (LivingEntity e : along(p, from, to, 1.0)) {
            magic(p, s, e, 18 * m(l, tier(p)) + stat(p, Stat.AGI, 0.3f));
            fxAt(level, Kind.SPLASH, s.color(), e.position().add(0, e.getBbHeight() / 2, 0), 1.8f, 1);
        }
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.8f);
        sound(p, SoundEvents.TRIDENT_THROW.value(), 1f, 0.6f);
        fx(p, Kind.SHOT, s.color(), to, 1, 0);
        fx(p, Kind.WAVE, s.color(), ground(level, to), 0.8f, 2);
        return true;
    }

    static boolean nightfall(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 from = p.position();
        Vec3 dest = aimGround(p, 14);
        Vec3 spot = dest.add(flat(from.subtract(dest), p).scale(1.2));
        if (!level.noCollision(p, p.getBoundingBox().move(spot.subtract(from)))) spot = from;
        p.teleportTo(level, spot.x, spot.y, spot.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        Vec3 at = p.position();
        for (LivingEntity e : foes(p, at, 5)) {
            magic(p, s, e, 16 * m(l, tier(p)) + stat(p, Stat.AGI, 0.3f));
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
            push(e, at, 0.6, 0.4);
        }
        sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.5f);
        sound(p, SoundEvents.WITHER_BREAK_BLOCK, 0.6f, 1.4f);
        fx(p, Kind.BLINK, s.color(), from, 1, 0);
        fx(p, Kind.NOVA, s.color(), at, 5, 0);
        fxAt(level, Kind.CRACK, s.color(), at, 5, 0);
        fxAt(level, Kind.CLEAVE, s.color(), at, 2.5f, 1);
        return true;
    }

    static boolean blackSun(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 18);
        int ticks = 80;
        sound(level, at, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.5f, 0.5f);
        sound(level, at, SoundEvents.PORTAL_AMBIENT, 0.8f, 0.6f);
        fxAt(level, Kind.VORTEX, s.color(), at, 5, ticks);
        fxAt(level, Kind.DOME, s.color(), at.add(0, 2.5, 0), 3, ticks);
        fxAt(level, Kind.ZONE, s.color(), at, 8, ticks);
        repeat(p, ticks, 10, () -> {
            for (LivingEntity e : foes(p, at, 8)) pull(e, at, 0.7);
        });
        repeat(p, ticks, 20, () -> {
            for (LivingEntity e : foes(p, at, 8)) magic(p, s, e, 6 * m(l, tier(p)) + stat(p, Stat.AGI, 0.08f));
        });
        later(p, ticks, () -> {
            for (LivingEntity e : foes(p, at, 8)) {
                magic(p, s, e, 18 * m(l, tier(p)) + stat(p, Stat.AGI, 0.3f));
                push(e, at, 1.0, 0.7);
            }
            sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.5f);
            sound(level, at, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.5f, 0.6f);
            fxAt(level, Kind.NOVA, s.color(), at, 8, 0);
            fxAt(level, Kind.ROAR, s.color(), at, 8, 0);
            fxAt(level, Kind.CRACK, s.color(), at, 8, 0);
        });
        return true;
    }

    static boolean moonlessVeil(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(moonlessSeconds(l) * 20);
        SkillAPI.setBuff(p, BUFF_MOONLESS, ticks);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, false, true));
        for (LivingEntity e : foes(p, p.position(), 20)) if (e instanceof Mob mob && mob.getTarget() == p) mob.setTarget(null);
        sound(p, SoundEvents.WARDEN_EMERGE, 0.5f, 1.8f);
        fx(p, Kind.VEIL, s.color(), p.position(), 1.6f, ticks);
        fx(p, Kind.ROAR, s.color(), p.position(), 6, 0);
        fx(p, Kind.DOME, s.color(), p.position(), 1.4f, ticks);
        return true;
    }

    static boolean crescentReaver(ServerPlayer p, Skill s, int l) {
        for (int i = 0; i < 2; i++) {
            final int k = i;
            later(p, i * 6, () -> {
                for (LivingEntity e : cone(p, 7, 0.1)) hit(p, s, e, 14 * m(l, tier(p)) + stat(p, Stat.AGI, 0.25f));
                sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.5f + 0.2f * k);
                sound(p, SoundEvents.WITHER_SHOOT, 0.4f, 1.6f);
                fx(p, Kind.SLASH, s.color(), p.position(), 7, k);
                fx(p, Kind.WAVE, s.color(), p.position().add(facing(p).scale(7)), 2.2f, 2);
            });
        }
        return true;
    }

    static boolean starlessPrison(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        List<LivingEntity> caught = foes(p, at, 9);
        if (caught.isEmpty()) return false;
        for (LivingEntity e : caught) {
            magic(p, s, e, 12 * m(l, tier(p)) + stat(p, Stat.AGI, 0.2f));
            stun(e, 60);
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
            fx(e, Kind.BIND, s.color(), e.position(), 1.3f, 60);
        }
        sound(p, SoundEvents.CHAIN_PLACE, 2f, 0.5f);
        sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5f, 1.4f);
        fxAt(level, Kind.ZONE, s.color(), at, 9, 60);
        fx(p, Kind.PULSE, s.color(), at, 9, 0);
        return true;
    }

    static boolean worldWithoutLight(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 70, 3, false, false, true));
        sound(p, SoundEvents.WARDEN_EMERGE, 1f, 0.5f);
        sound(p, SoundEvents.END_PORTAL_SPAWN, 0.4f, 0.5f);
        fxAt(level, Kind.ZONE, s.color(), center, 14, 62);
        fxAt(level, Kind.QUAKE, 0x201040, center, 14, 62);
        fxAt(level, Kind.DOME, s.color(), center, 14, 62);
        fx(p, Kind.VEIL, s.color(), center, 2, 62);
        repeat(p, 60, 20, () -> {
            for (LivingEntity e : foes(p, center, 14)) {
                e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
            }
        });
        for (int i = 0; i < 6; i++) {
            later(p, 8 + i * 7, () -> {
                List<LivingEntity> near = foes(p, center, 14);
                if (near.isEmpty()) return;
                LivingEntity target = near.get(p.getRandom().nextInt(near.size()));
                Vec3 pt = target.position();
                fxAt(level, Kind.CLEAVE, s.color(), pt, 2.4f, 6);
                later(p, 6, () -> {
                    for (LivingEntity e : foes(p, pt, 2.6)) magic(p, s, e, 12 * m(l, tier(p)) + stat(p, Stat.AGI, 0.15f));
                    sound(level, pt, SoundEvents.TRIDENT_HIT_GROUND, 1f, 0.5f);
                });
            });
        }
        later(p, 62, () -> {
            for (LivingEntity e : foes(p, center, 14)) {
                magic(p, s, e, 36 * m(l, tier(p)) + stat(p, Stat.AGI, 0.5f));
                stun(e, 60);
            }
            sound(level, center, SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.5f);
            sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.4f);
            fxAt(level, Kind.NOVA, s.color(), center, 14, 0);
            fxAt(level, Kind.ROAR, 0xFFFFFF, center, 14, 0);
            fxAt(level, Kind.CRACK, s.color(), center, 14, 0);
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4;
                Vec3 pt = ground(level, center.add(Math.cos(ang) * 9, 0, Math.sin(ang) * 9));
                later(p, 2 + i, () -> fxAt(level, Kind.CLEAVE, s.color(), pt, 3, 4));
            }
        });
        return true;
    }

    // ================================================================ passives

    static {
        bonus(NIGHTFALL_STEP, Attributes.MOVEMENT_SPEED, 0.015f, MUL, "speed");
        bonus(NIGHTFALL_STEP, Attributes.ATTACK_SPEED, 0.02f, MUL, "attack_speed");
        bonus(FEATHERFALL, Attributes.MOVEMENT_SPEED, 0.02f, MUL, "speed");
        bonus(FEATHERFALL, Attributes.SAFE_FALL_DISTANCE, 1f, ADD, "fall");
        bonus(TAILWIND, Attributes.ATTACK_SPEED, 0.03f, MUL, "attack_speed");
        bonus(DEAD_OR_ALIVE, Attributes.ATTACK_DAMAGE, 0.3f, ADD, "attack");
        bonus(DEAD_OR_ALIVE, Attributes.ARMOR, 0.3f, ADD, "armor");
        bonus(VOID_TOUCHED, Attributes.ATTACK_DAMAGE, 0.7f, ADD, "attack");
        bonus(VOID_TOUCHED, Attributes.MOVEMENT_SPEED, 0.01f, MUL, "speed");
    }

    /** Extra damage, in percent, when an assassin subclass player strikes. */
    static float strikeBonus(ServerPlayer p, SkillData data, LivingEntity target, long now) {
        float bonus = 0;
        int instinct = data.level(KILLER_INSTINCT.path());
        if (instinct > 0 && unaware(p, target)) bonus += 2 * instinct;
        int finisher = data.level(FINISHER.path());
        if (finisher > 0 && target.getHealth() < target.getMaxHealth() * 0.3f) bonus += 3 * finisher;
        int potency = data.level(POTENCY.path());
        if (potency > 0 && poisoned(target)) bonus += 2 * potency;
        int appraise = data.level(APPRAISE_WEAKNESS.path());
        if (appraise > 0 && marked(target)) bonus += 2 * appraise;
        if (data.hasBuff(BUFF_SHROUD, now)) {
            // the first strike out of the shroud
            bonus += shroudBonus(Math.max(1, data.level(DUSK_SHROUD.path())));
            data.clearBuff(BUFF_SHROUD);
            p.removeEffect(MobEffects.INVISIBILITY);
        }
        if (!CONTRACTS.isEmpty()) {
            long[] contract = CONTRACTS.get(target);
            if (contract != null) {
                if (contract[0] <= now) CONTRACTS.remove(target);
                else if (contract[2] == p.getId()) bonus += contract[1];
            }
        }
        return bonus;
    }

    /** Penumbra: a chance to stun on any hit. */
    static void afterStrike(ServerPlayer p, SkillData data, LivingEntity target) {
        int penumbra = data.level(PENUMBRA.path());
        if (penumbra > 0 && p.getRandom().nextFloat() * 100 < penumbraChance(penumbra)) {
            stun(target, 20);
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
        }
    }

    /** An assassin subclass player is struck. Returns the damage to take, or a negative number for a dodge. */
    static float whenStruck(ServerPlayer p, SkillData data, LivingEntity attacker, boolean melee, DamageSource source, float amount, long now) {
        if (attacker != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float dodge = 0;
            if (data.hasBuff(BUFF_MIRROR, now)) dodge = mirrorDodge(Math.max(1, data.level(MIRROR_IMAGE.path())));
            if (data.hasBuff(BUFF_MOONLESS, now)) dodge = Math.max(dodge, 50);
            if (dodge > 0 && p.getRandom().nextFloat() * 100 < dodge) {
                sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7f, 1.6f);
                fx(p, Kind.BLINK, MIRROR_IMAGE.color(), p.position().add(p.getRandom().nextDouble() - 0.5, 0, p.getRandom().nextDouble() - 0.5), 1, 0);
                return -1;
            }
        }
        int toxic = data.level(TOXIC_BLOOD.path());
        if (toxic > 0 && melee && attacker != null && attacker.isAlive()) Statuses.apply(attacker, DamageElement.POISON, thornPoison(toxic), p);
        int anti = data.level(ANTIBODIES.path());
        if (anti > 0 && (source.is(DamageTypeTags.WITCH_RESISTANT_TO) || source.is(DamageTypeTags.BYPASSES_ARMOR))) amount *= 1 - 0.03f * anti;
        int slip = data.level(SLIPSTREAM.path());
        if (slip > 0 && p.isSprinting()) amount *= 1 - 0.02f * slip;
        int lord = data.level(LORD_OF_NIGHT.path());
        if (lord > 0) amount *= 1 - 0.02f * lord;
        return amount;
    }

    static void tickSecond(ServerPlayer p, SkillData data) {
        if (data.level(ANTIBODIES.path()) > 0 && p.hasEffect(MobEffects.POISON)) p.removeEffect(MobEffects.POISON);
    }

    static void onKill(ServerPlayer p, SkillData data, LivingEntity dead) {
        int ledger = data.level(BOUNTY_LEDGER.path());
        if (ledger > 0) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Math.round(ledgerSeconds(ledger) * 20), 1));
        long[] contract = CONTRACTS.remove(dead);
        if (contract != null && contract[0] > p.level().getGameTime() && contract[2] == p.getId()) {
            // the contract is fulfilled: paid in full
            p.heal(p.getMaxHealth());
            SystemAPI.restoreMana(p, 40);
            SkillAPI.resetCooldowns(p);
            sound(p, SoundEvents.PLAYER_LEVELUP, 0.8f, 0.6f);
            fx(p, Kind.HEAL, FINAL_CONTRACT.color(), p.position(), 1.5f, 0);
        }
    }

    /** Touch the class so its skills are registered. */
    static void init() {}

    private AssassinPath() {}
}
