package com.beyondsolo.skill;

import com.beyondsolo.combat.Buildups;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
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
 * The Warrior subclasses' own skill sets, on top of the starters in {@link SubclassSkills}: ten more each for the
 * Bloodrager, Warlord, Blademaster, Juggernaut and the rare Worldbreaker. They unlock with player levels after the
 * job change, the last one (an ultimate) at level 50.
 *
 * <p>Numbers sit next to the skill they belong to, and both the description and the effect read them.</p>
 */
public final class WarriorPath {
    public static final String BUFF_LAST_STAND = "last_stand";
    public static final String BUFF_STONE_SKIN = "stone_skin";

    /** Execution Order marks: creature -> {end game time, bonus percent}. Everyone's hits count. Server thread only. */
    static final Map<LivingEntity, long[]> ORDERS = new WeakHashMap<>();
    /** Aftershock: player -> game time of the last splash. */
    private static final Map<ServerPlayer, Long> SPLASHED = new WeakHashMap<>();
    private static boolean splashing;

    private static final Subclass BR = Subclass.BLOODRAGER, WL = Subclass.WARLORD, BM = Subclass.BLADEMASTER,
            JG = Subclass.JUGGERNAUT, WB = Subclass.WORLDBREAKER;
    private static final PlayerRank A = PlayerRank.A, S = PlayerRank.S;

    // ================================================================ bloodrager

    static float lastStandSeconds(int l) { return 5 + l / 2f; }

    public static final Skill CRIMSON_CLEAVE = active("crimson_cleave", BR, A, 22, 0xE0393E, 14, 140, 80,
            WarriorPath::crimsonCleave, (l, t) -> a(f(9 * m(l, t))));
    public static final Skill GORE_RUSH = active("gore_rush", BR, A, 24, 0xF05050, 14, 200, 120,
            WarriorPath::goreRush, (l, t) -> a(f(7 * m(l, t))));
    public static final Skill THICK_BLOOD = passive("thick_blood", BR, A, 26, 0xC02838, (l, t) -> a(f(2.5f * l), f(0.25f * l)));
    public static final Skill BLOOD_GEYSER = active("blood_geyser", BR, A, 29, 0xD01828, 18, 220, 130,
            WarriorPath::bloodGeyser, (l, t) -> a(f(10 * m(l, t))));
    public static final Skill HEMORRHAGE = active("hemorrhage", BR, A, 32, 0xB01030, 16, 240, 140,
            WarriorPath::hemorrhage, (l, t) -> a(f(8 * m(l, t)), f(2 * m(l, t))));
    public static final Skill BLOODSCENT = passive("bloodscent", BR, A, 35, 0xE06070, (l, t) -> a(f(2 * l)));
    public static final Skill RECKLESS_SLAM = active("reckless_slam", BR, A, 38, 0xF04830, 22, 280, 170,
            WarriorPath::recklessSlam, (l, t) -> a(f(15 * m(l, t))));
    public static final Skill BLOOD_NOVA = active("blood_nova", BR, A, 42, 0xFF3048, 26, 400, 260,
            WarriorPath::bloodNova, (l, t) -> a(f(12 * m(l, t))));
    public static final Skill PAIN_TOLERANCE = passive("pain_tolerance", BR, A, 46, 0xA04050, (l, t) -> a(f(1.5f * l)));
    public static final Skill LAST_STAND = active("last_stand", BR, S, 50, 0xFF2020, 40, 1800, 1200,
            WarriorPath::lastStand, (l, t) -> a(f(lastStandSeconds(l))));

    static boolean crimsonCleave(ServerPlayer p, Skill s, int l) {
        for (int i = 0; i < 2; i++) {
            final int k = i;
            later(p, i * 6, () -> {
                float dmg = 9 * m(l, tier(p)) + stat(p, Stat.STR, 0.2f);
                for (LivingEntity e : cone(p, 5, 0.15)) {
                    hit(p, s, e, dmg);
                    Buildups.add(e, Buildups.BLOOD, 10 + 2 * l, p);
                }
                sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.7f + 0.2f * k);
                sound(p, SoundEvents.HONEY_BLOCK_BREAK, 0.8f, 0.6f);
                fx(p, Kind.SLASH, s.color(), p.position(), 4.6f + 0.6f * k, k);
            });
        }
        return true;
    }

    static boolean goreRush(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 start = p.position();
        sound(p, SoundEvents.RAVAGER_ATTACK, 0.8f, 1.3f);
        fx(p, Kind.DASH, s.color(), start, 1.2f, 10);
        dash(p, facing(p), 7, 8, 2.0, e -> {
            hit(p, s, e, 7 * m(l, tier(p)) + stat(p, Stat.STR, 0.15f));
            com.beyondsolo.system.Balance.healRaw(p, p.getMaxHealth() * 0.03f);
            fxAt(level, Kind.GEYSER, s.color(), e.position(), 2.4f, 0);
            sound(e, SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 0.8f);
        }, () -> fx(p, Kind.SLASH, s.color(), p.position(), 3.2f, 0));
        return true;
    }

    static boolean bloodGeyser(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 16);
        fxAt(level, Kind.PILLAR, s.color(), at, 2.2f, 8);
        sound(level, at, SoundEvents.LAVA_AMBIENT, 1.5f, 0.6f);
        later(p, 8, () -> {
            fxAt(level, Kind.GEYSER, s.color(), at, 6.5f, 0);
            sound(level, at, SoundEvents.GENERIC_SPLASH, 1.2f, 0.5f);
            sound(level, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, 0.6f, 1.4f);
            for (LivingEntity e : foes(p, at, 3.5)) {
                hit(p, s, e, 10 * m(l, tier(p)) + stat(p, Stat.STR, 0.2f));
                launch(e, 0.95);
                Buildups.add(e, Buildups.BLOOD, 14 + 2 * l, p);
            }
        });
        return true;
    }

    static boolean hemorrhage(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 5.5, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        hit(p, s, target, 8 * m(l, tier(p)) + stat(p, Stat.STR, 0.2f));
        sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.6f);
        sound(p, SoundEvents.SLIME_SQUISH, 0.9f, 0.5f);
        fx(p, Kind.SLASH, s.color(), p.position(), 2.8f, 1);
        fx(target, Kind.MARK, s.color(), target.position(), target.getBbHeight(), 110);
        for (int i = 1; i <= 5; i++) {
            later(p, i * 20, () -> {
                if (!target.isAlive()) return;
                hit(p, s, target, 2 * m(l, tier(p)) + stat(p, Stat.STR, 0.04f));
                fxAt(level, Kind.GEYSER, s.color(), target.position(), 1.5f, 0);
            });
        }
        return true;
    }

    static boolean recklessSlam(ServerPlayer p, Skill s, int l) {
        if (!p.isCreative()) p.setHealth(Math.max(1, p.getHealth() * 0.92f));
        Vec3 at = p.position();
        ServerLevel level = p.serverLevel();
        for (LivingEntity e : foes(p, at, 5.5)) {
            hit(p, s, e, 15 * m(l, tier(p)) + stat(p, Stat.STR, 0.3f));
            push(e, at, 0.8, 0.55);
        }
        sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 0.7f);
        sound(p, SoundEvents.RAVAGER_ROAR, 0.6f, 1.3f);
        fx(p, Kind.SLAM, s.color(), at, 5.5f, 0);
        fxAt(level, Kind.CRACK, s.color(), at, 5.5f, 0);
        fxAt(level, Kind.GEYSER, s.color(), at, 3.5f, 0);
        return true;
    }

    static boolean bloodNova(ServerPlayer p, Skill s, int l) {
        float missing = 1 - p.getHealth() / p.getMaxHealth();
        if (!p.isCreative()) p.setHealth(Math.max(1, p.getHealth() * 0.85f));
        Vec3 at = p.position();
        ServerLevel level = p.serverLevel();
        float dmg = (12 * m(l, tier(p)) + stat(p, Stat.STR, 0.25f)) * (1 + missing);
        for (LivingEntity e : foes(p, at, 7)) {
            hit(p, s, e, dmg);
            Statuses.apply(e, DamageElement.FIRE, 12 + 2 * l, p);
            push(e, at, 0.6, 0.4);
        }
        sound(p, SoundEvents.WITHER_BREAK_BLOCK, 0.6f, 1.5f);
        sound(p, SoundEvents.GENERIC_SPLASH, 1f, 0.4f);
        fx(p, Kind.NOVA, s.color(), at, 7, 0);
        fx(p, Kind.ROAR, s.color(), at, 7, 0);
        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI / 3;
            Vec3 pt = ground(level, at.add(Math.cos(ang) * 4.2, 0, Math.sin(ang) * 4.2));
            later(p, 2 + i, () -> fxAt(level, Kind.GEYSER, s.color(), pt, 4.5f, 0));
        }
        return true;
    }

    static boolean lastStand(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(lastStandSeconds(l) * 20);
        SkillAPI.setBuff(p, BUFF_LAST_STAND, ticks);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 1));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
        sound(p, SoundEvents.WITHER_SPAWN, 0.35f, 1.6f);
        sound(p, SoundEvents.RAVAGER_ROAR, 1f, 0.8f);
        fx(p, Kind.ROAR, s.color(), p.position(), 8, 0);
        fx(p, Kind.DOME, s.color(), p.position(), 1.5f, ticks);
        fx(p, Kind.AURA, s.color(), p.position(), 1.4f, ticks);
        fxAt(p.serverLevel(), Kind.CRACK, s.color(), p.position(), 4, 0);
        return true;
    }

    // ================================================================ warlord

    static float ruinSeconds(int l) { return 6 + l / 2f; }
    static float wallSeconds(int l) { return 6 + l / 2f; }
    static float orderBonus(int l) { return 15 + 2 * l; }
    static float spoilsMana(int l) { return 2 + 0.5f * l; }

    public static final Skill RALLYING_CRY = active("rallying_cry", WL, A, 22, 0xFFC860, 18, 400, 260,
            WarriorPath::rallyingCry, (l, t) -> a(f(4 * m(l, t))));
    public static final Skill SPEAR_OF_COMMAND = active("spear_of_command", WL, A, 24, 0xFFB040, 16, 160, 90,
            WarriorPath::spearOfCommand, (l, t) -> a(f(10 * m(l, t))));
    public static final Skill IRON_DISCIPLINE = passive("iron_discipline", WL, A, 26, 0xC0C8D8, (l, t) -> a(f(0.5f * l), f(3 * l)));
    public static final Skill VANGUARD_CHARGE = active("vanguard_charge", WL, A, 29, 0xF09030, 16, 240, 140,
            WarriorPath::vanguardCharge, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill BANNER_OF_RUIN = active("banner_of_ruin", WL, A, 32, 0xE0502A, 24, 500, 320,
            WarriorPath::bannerOfRuin, (l, t) -> a(f(ruinSeconds(l)), f(2.5f * m(l, t))));
    public static final Skill WAR_DRUMS = passive("war_drums", WL, A, 35, 0xE8A860, (l, t) -> a(l >= 6 ? "II" : "I"));
    public static final Skill SHIELD_WALL = active("shield_wall", WL, A, 38, 0xFFE0A0, 22, 600, 400,
            WarriorPath::shieldWall, (l, t) -> a(f(wallSeconds(l)), f(2 + 2 * (l / 4))));
    public static final Skill EXECUTION_ORDER = active("execution_order", WL, A, 42, 0xFF7030, 18, 400, 260,
            WarriorPath::executionOrder, (l, t) -> a(f(6 * m(l, t)), f(orderBonus(l))));
    public static final Skill SPOILS_OF_WAR = passive("spoils_of_war", WL, A, 46, 0xF0D060, (l, t) -> a(f(spoilsMana(l)), f(0.5f + 0.1f * l)));
    public static final Skill CONQUERORS_VERDICT = active("conquerors_verdict", WL, S, 50, 0xFFA020, 40, 1000, 640,
            WarriorPath::conquerorsVerdict, (l, t) -> a(f(22 * m(l, t))));

    static boolean rallyingCry(ServerPlayer p, Skill s, int l) {
        float heal = 4 * m(l, tier(p)) + stat(p, Stat.VIT, 0.1f);
        for (Player ally : allies(p, 10)) {
            ally.heal(heal);
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0));
            fx(ally, Kind.HEAL, s.color(), ally.position(), 1, 0);
        }
        sound(p, SoundEvents.RAID_HORN.value(), 0.8f, 1.2f);
        fx(p, Kind.ROAR, s.color(), p.position(), 10, 0);
        fx(p, Kind.PULSE, s.color(), p.position(), 10, 0);
        return true;
    }

    static boolean spearOfCommand(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 from = p.getEyePosition().add(0, -0.3, 0);
        Vec3 to = SkillActions.aimPoint(p, 18);
        for (LivingEntity e : along(p, from, to, 0.9)) {
            hit(p, s, e, 10 * m(l, tier(p)) + stat(p, Stat.STR, 0.2f));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
            SkillParticles.send(level, ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 12, 0.2, 0.2, 0.2, 0.3);
        }
        sound(p, SoundEvents.TRIDENT_THROW.value(), 1f, 0.8f);
        sound(level, to, SoundEvents.TRIDENT_HIT_GROUND, 1f, 0.8f);
        fx(p, Kind.SHOT, s.color(), to, 1, 0);
        fxAt(level, Kind.CLEAVE, s.color(), ground(level, to), 1.3f, 2);
        return true;
    }

    static boolean vanguardCharge(ServerPlayer p, Skill s, int l) {
        Vec3 start = p.position();
        for (Player ally : allies(p, 8)) ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
        sound(p, SoundEvents.RAID_HORN.value(), 0.5f, 1.6f);
        sound(p, SoundEvents.HORSE_GALLOP, 1f, 0.8f);
        fx(p, Kind.ROAR, s.color(), start, 8, 0);
        fx(p, Kind.DASH, s.color(), start, 1.3f, 12);
        dash(p, facing(p), 9, 10, 2.2, e -> {
            hit(p, s, e, 8 * m(l, tier(p)) + stat(p, Stat.STR, 0.15f));
            push(e, p.position(), 0.9, 0.45);
        }, () -> {
            sound(p, SoundEvents.ANVIL_LAND, 0.4f, 0.8f);
            fx(p, Kind.SLAM, s.color(), p.position(), 3, 0);
        });
        return true;
    }

    static boolean bannerOfRuin(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 18);
        int ticks = Math.round(ruinSeconds(l) * 20);
        sound(level, at, SoundEvents.RAID_HORN.value(), 0.6f, 0.7f);
        sound(level, at, SoundEvents.BLAZE_SHOOT, 1f, 0.6f);
        fxAt(level, Kind.CLEAVE, s.color(), at, 1.4f, 3);
        fxAt(level, Kind.BANNER, s.color(), at, 6, ticks);
        repeat(p, ticks, 20, () -> {
            for (LivingEntity e : foes(p, at, 6)) {
                SkillActions.hit(p, s, e, 2.5f * m(l, tier(p)) + stat(p, Stat.STR, 0.05f), true);
                Statuses.apply(e, DamageElement.FIRE, 8 + l, p);
            }
            SkillParticles.send(level, ParticleTypes.FLAME, at.x, at.y + 0.2, at.z, 30, 3, 0.1, 3, 0.02);
        });
        return true;
    }

    static boolean shieldWall(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(wallSeconds(l) * 20);
        for (Player ally : allies(p, 8)) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1));
            ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, l / 4));
            fx(ally, Kind.DOME, s.color(), ally.position(), 1.3f, ticks);
        }
        sound(p, SoundEvents.SHIELD_BLOCK, 1f, 0.6f);
        sound(p, SoundEvents.ANVIL_PLACE, 0.5f, 1.4f);
        fx(p, Kind.PULSE, s.color(), p.position(), 8, 0);
        return true;
    }

    static boolean executionOrder(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 24, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        int ticks = 240;
        ORDERS.put(target, new long[]{level.getGameTime() + ticks, Math.round(orderBonus(l))});
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
        sound(p, SoundEvents.BELL_RESONATE, 1f, 1.4f);
        fx(target, Kind.MARK, s.color(), target.position(), target.getBbHeight(), ticks);
        fxAt(level, Kind.CLEAVE, s.color(), target.position(), 2f, 7);
        later(p, 7, () -> {
            if (!target.isAlive()) return;
            hit(p, s, target, 6 * m(l, tier(p)) + stat(p, Stat.STR, 0.1f));
            sound(target, SoundEvents.TRIDENT_HIT, 1f, 0.6f);
        });
        return true;
    }

    static boolean conquerorsVerdict(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 20);
        sound(p, SoundEvents.RAID_HORN.value(), 1f, 0.6f);
        sound(level, at, SoundEvents.BEACON_ACTIVATE, 1.5f, 0.6f);
        fxAt(level, Kind.CLEAVE, s.color(), at, 5.5f, 16);
        later(p, 16, () -> {
            for (LivingEntity e : foes(p, at, 5.5)) {
                hit(p, s, e, 22 * m(l, tier(p)) + stat(p, Stat.STR, 0.35f));
                stun(e, 50);
                launch(e, 0.5);
            }
            sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.6f);
            sound(level, at, SoundEvents.TRIDENT_THUNDER.value(), 0.8f, 0.7f);
            fxAt(level, Kind.SLAM, s.color(), at, 5.5f, 0);
            fxAt(level, Kind.CRACK, s.color(), at, 6.5f, 0);
        });
        return true;
    }

    // ================================================================ blademaster

    static float wardSeconds(int l) { return 5 + l / 2f; }
    static int rainSwords(int l) { return 6 + l / 2; }
    static float riposteChance(int l) { return 2 * l; }

    public static final Skill IAIDO = active("iaido", BM, A, 22, 0xFFE0D0, 16, 180, 100,
            WarriorPath::iaido, (l, t) -> a(f(11 * m(l, t))));
    public static final Skill CRESCENT_WAVE = active("crescent_wave", BM, A, 24, 0xC8F0FF, 14, 120, 70,
            WarriorPath::crescentWave, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill KEEN_EDGE = passive("keen_edge", BM, A, 26, 0xE8E8F0, (l, t) -> a(f(0.4f * l)));
    public static final Skill RISING_MOON = active("rising_moon", BM, A, 29, 0xFFF0C0, 14, 200, 120,
            WarriorPath::risingMoon, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill BLADE_WARD = active("blade_ward", BM, A, 32, 0xB8E0FF, 22, 440, 280,
            WarriorPath::bladeWard, (l, t) -> a(f(wardSeconds(l)), f(3 * m(l, t))));
    public static final Skill RIPOSTE = passive("riposte", BM, A, 35, 0xFFB0A0, (l, t) -> a(f(riposteChance(l)), f(3 + l)));
    public static final Skill FALLING_PETAL = active("falling_petal", BM, A, 38, 0xFFB8C8, 20, 260, 160,
            WarriorPath::fallingPetal, (l, t) -> a(f(12 * m(l, t))));
    public static final Skill SWORD_RAIN = active("sword_rain", BM, A, 42, 0xA8D8FF, 26, 400, 250,
            WarriorPath::swordRain, (l, t) -> a(f(rainSwords(l)), f(6 * m(l, t))));
    public static final Skill WIND_STEP = passive("wind_step", BM, A, 46, 0xC0FFF0, (l, t) -> a(f(1.5f * l)));
    public static final Skill SEVERING_LINE = active("severing_line", BM, S, 50, 0xFFFFFF, 40, 900, 560,
            WarriorPath::severingLine, (l, t) -> a(f(20 * m(l, t))));

    static boolean iaido(ServerPlayer p, Skill s, int l) {
        Vec3 start = p.position();
        List<LivingEntity> cut = new ArrayList<>();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 16, 2, false, false, false));
        sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1f, 1.8f);
        fx(p, Kind.DASH, s.color(), start, 1, 8);
        dash(p, facing(p), 7.5, 5, 2.2, cut::add, () -> later(p, 7, () -> {
            // the blade is already sheathed when the cuts open
            sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.7f);
            sound(p, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.5f);
            int k = 0;
            for (LivingEntity e : cut) {
                if (!e.isAlive()) continue;
                hit(p, s, e, 11 * m(l, tier(p)) + stat(p, Stat.AGI, 0.2f) + stat(p, Stat.STR, 0.1f));
                fx(e, Kind.SLASH, s.color(), e.position(), 2.4f, k++ % 2);
            }
        }));
        return true;
    }

    static boolean crescentWave(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 dir = facing(p);
        Vec3 start = p.position().add(0, 1, 0);
        Set<Integer> struck = new HashSet<>();
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.3f);
        sound(p, SoundEvents.BREEZE_SHOOT, 0.8f, 1.2f);
        fx(p, Kind.SLASH, s.color(), p.position(), 3.4f, 0);
        fx(p, Kind.WAVE, s.color(), p.position().add(dir.scale(14)), 1.5f, 2);
        for (int i = 1; i <= 7; i++) {
            Vec3 a = start.add(dir.scale((i - 1) * 2)), b = start.add(dir.scale(i * 2));
            later(p, i, () -> {
                for (LivingEntity e : along(p, a, b, 1.5)) {
                    if (!struck.add(e.getId())) continue;
                    hit(p, s, e, 8 * m(l, tier(p)) + stat(p, Stat.AGI, 0.15f) + stat(p, Stat.STR, 0.1f));
                    e.knockback(0.5, -dir.x, -dir.z);
                    fxAt(level, Kind.SPLASH, s.color(), e.position().add(0, 1, 0), 1.3f, 1);
                }
                SkillParticles.send(level, ParticleTypes.SWEEP_ATTACK, b.x, b.y, b.z, 2, 0.6, 0.1, 0.6, 0);
            });
        }
        return true;
    }

    static boolean risingMoon(ServerPlayer p, Skill s, int l) {
        Vec3 front = p.position().add(facing(p).scale(2));
        for (LivingEntity e : cone(p, 4.5, 0.3)) {
            hit(p, s, e, 8 * m(l, tier(p)) + stat(p, Stat.STR, 0.15f) + stat(p, Stat.AGI, 0.1f));
            launch(e, 0.95);
        }
        move(p, new Vec3(p.getDeltaMovement().x, 0.7, p.getDeltaMovement().z));
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.9f);
        sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 0.8f, 1.3f);
        fx(p, Kind.SLASH, s.color(), p.position(), 3.8f, 1);
        fx(p, Kind.SPIN, s.color(), p.position(), 2.4f, 6);
        fxAt(p.serverLevel(), Kind.GEYSER, s.color(), front, 4.5f, 0);
        return true;
    }

    static boolean bladeWard(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(wardSeconds(l) * 20);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0));
        sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1f, 1.4f);
        sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        fx(p, Kind.BLADES, s.color(), p.position(), 2.6f, ticks);
        repeat(p, ticks, 10, () -> {
            for (LivingEntity e : foes(p, p.position(), 3.2)) {
                hit(p, s, e, 3 * m(l, tier(p)) + stat(p, Stat.AGI, 0.06f));
                sound(e, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.8f);
            }
        });
        return true;
    }

    static boolean fallingPetal(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.BREEZE_JUMP, 1f, 1.2f);
        leap(p, aimGround(p, 12), 0.85, land -> {
            for (LivingEntity e : foes(p, land, 4.5)) hit(p, s, e, 12 * m(l, tier(p)) + stat(p, Stat.AGI, 0.25f));
            sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
            sound(p, SoundEvents.CHERRY_LEAVES_BREAK, 1.5f, 0.8f);
            fx(p, Kind.BLOSSOM, s.color(), land, 4.5f, 0);
            fx(p, Kind.SLASH, s.color(), land, 4.2f, 0);
            fx(p, Kind.SLASH, 0xFFFFFF, land, 4.2f, 1);
            fxAt(level, Kind.CRACK, s.color(), land, 3, 0);
        });
        return true;
    }

    static boolean swordRain(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = aimGround(p, 18);
        int n = rainSwords(l);
        sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 1.4f);
        fxAt(level, Kind.ZONE, s.color(), center, 5, n * 3 + 14);
        for (int i = 0; i < n; i++) {
            double ang = p.getRandom().nextDouble() * Math.PI * 2;
            double rad = i == 0 ? 0 : Math.sqrt(p.getRandom().nextDouble()) * 4.5;
            Vec3 pt = ground(level, center.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad));
            later(p, i * 3, () -> fxAt(level, Kind.CLEAVE, s.color(), pt, 1.5f, 8));
            later(p, i * 3 + 8, () -> {
                for (LivingEntity e : foes(p, pt, 2)) hit(p, s, e, 6 * m(l, tier(p)) + stat(p, Stat.AGI, 0.1f));
                sound(level, pt, SoundEvents.TRIDENT_HIT_GROUND, 0.8f, 1.4f);
            });
        }
        return true;
    }

    static boolean severingLine(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 4, false, false, false));
        sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1f, 0.6f);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.8f);
        later(p, 12, () -> {
            Vec3 dir = facing(p), from = p.position();
            Vec3 a = from.add(0, 1, 0), b = a.add(dir.scale(18));
            List<LivingEntity> cut = new ArrayList<>(along(p, a, b, 1.8));
            for (LivingEntity e : cut) {
                float dmg = 20 * m(l, tier(p)) + stat(p, Stat.AGI, 0.3f) + stat(p, Stat.STR, 0.2f);
                hit(p, s, e, e.getHealth() < e.getMaxHealth() * 0.25f ? dmg * 2 : dmg);
                fx(e, Kind.SLASH, s.color(), e.position(), 2.6f, 0);
            }
            // a breath later the cut catches up: the ground parts along the line and what stood on it is cut again
            later(p, 10, () -> {
                for (int i = 0; i < 4; i++) fxAt(level, Kind.CRACK, s.color(), from.add(dir.scale(2.5 + i * 4.5)), 2.8f, 0);
                for (LivingEntity e : cut) {
                    if (!e.isAlive()) continue;
                    fx(e, Kind.SLASH, 0xFFFFFF, e.position(), 2.2f, 1);
                    fxAt(level, Kind.SPLASH, s.color(), e.position().add(0, e.getBbHeight() * 0.5, 0), 1.8f, 1);
                }
                sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.4f);
            });
            sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
            sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.5f, 1.8f);
            fx(p, Kind.SLASH, s.color(), p.position(), 6, 0);
            fx(p, Kind.WAVE, s.color(), p.position().add(dir.scale(18)), 1.8f, 2);
            for (int i = 1; i <= 6; i++) {
                Vec3 pt = p.position().add(dir.scale(i * 3)).add(0, 1, 0);
                later(p, i, () -> fxAt(level, Kind.SPLASH, s.color(), pt, 2.2f, 1));
            }
        });
        return true;
    }

    // ================================================================ juggernaut

    static float skinSeconds(int l) { return 6 + l / 2f; }
    static float windRegen(int l) { return 0.2f * l; }

    public static final Skill SEISMIC_STOMP = active("seismic_stomp", JG, A, 22, 0xC89060, 16, 200, 120,
            WarriorPath::seismicStomp, (l, t) -> a(f(9 * m(l, t))));
    public static final Skill BOULDER_TOSS = active("boulder_toss", JG, A, 24, 0xA08060, 16, 180, 100,
            WarriorPath::boulderToss, (l, t) -> a(f(11 * m(l, t))));
    public static final Skill IMMOVABLE = passive("immovable", JG, A, 26, 0x9098A8, (l, t) -> a(f(0.6f * l), f(0.3f * l)));
    public static final Skill STONE_SKIN = active("stone_skin", JG, A, 29, 0xB0A898, 20, 600, 400,
            WarriorPath::stoneSkin, (l, t) -> a(f(skinSeconds(l)), f(2 + 2 * (l / 3))));
    public static final Skill GRAPPLE_SLAM = active("grapple_slam", JG, A, 32, 0xD0A070, 18, 240, 150,
            WarriorPath::grappleSlam, (l, t) -> a(f(13 * m(l, t)), f(5 * m(l, t))));
    public static final Skill MOMENTUM = passive("momentum", JG, A, 35, 0xE0B080, (l, t) -> a(f(2 * l)));
    public static final Skill LIVING_AVALANCHE = active("living_avalanche", JG, A, 38, 0xB89878, 24, 400, 260,
            WarriorPath::livingAvalanche, (l, t) -> a(f(4 * m(l, t))));
    public static final Skill BULWARK_TAUNT = active("bulwark_taunt", JG, A, 42, 0xE0C8A0, 18, 500, 320,
            WarriorPath::bulwarkTaunt, (l, t) -> a(f(2 + 2 * (l / 3))));
    public static final Skill SECOND_WIND = passive("second_wind", JG, A, 46, 0xA8D090, (l, t) -> a(f(windRegen(l))));
    public static final Skill MOUNTAINFALL = active("mountainfall", JG, S, 50, 0xD08840, 40, 1000, 640,
            WarriorPath::mountainfall, (l, t) -> a(f(20 * m(l, t)), f(3 * m(l, t))));

    static boolean seismicStomp(ServerPlayer p, Skill s, int l) {
        Vec3 at = p.position();
        for (LivingEntity e : foes(p, at, 6)) {
            hit(p, s, e, 9 * m(l, tier(p)) + stat(p, Stat.STR, 0.15f) + stat(p, Stat.VIT, 0.1f));
            launch(e, 0.6);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
        }
        sound(p, SoundEvents.ANVIL_LAND, 0.6f, 0.5f);
        sound(p, SoundEvents.WARDEN_STEP, 2f, 0.6f);
        fx(p, Kind.SLAM, s.color(), at, 6, 0);
        fxAt(p.serverLevel(), Kind.CRACK, s.color(), at, 6, 0);
        return true;
    }

    static boolean boulderToss(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 20);
        sound(p, SoundEvents.ROOTED_DIRT_BREAK, 1.5f, 0.5f);
        sound(p, SoundEvents.TRIDENT_THROW.value(), 1f, 0.5f);
        fx(p, Kind.SLAM, s.color(), p.position(), 1.6f, 0);
        fxAt(level, Kind.METEOR, s.color(), at, 3.2f, 13);
        later(p, 13, () -> {
            for (LivingEntity e : foes(p, at, 3.5)) {
                hit(p, s, e, 11 * m(l, tier(p)) + stat(p, Stat.STR, 0.2f));
                stun(e, 30);
            }
            sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 0.9f);
            sound(level, at, SoundEvents.STONE_BREAK, 2f, 0.5f);
            fxAt(level, Kind.SLAM, s.color(), at, 3.5f, 0);
            fxAt(level, Kind.CRACK, s.color(), at, 3.2f, 0);
        });
        return true;
    }

    static boolean stoneSkin(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(skinSeconds(l) * 20);
        SkillAPI.setBuff(p, BUFF_STONE_SKIN, ticks);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, l / 3));
        sound(p, SoundEvents.STONE_PLACE, 1.5f, 0.5f);
        sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1f, 0.6f);
        fx(p, Kind.DOME, s.color(), p.position(), 1.3f, ticks);
        fx(p, Kind.AURA, s.color(), p.position(), 1.2f, ticks);
        fxAt(p.serverLevel(), Kind.CRACK, s.color(), p.position(), 2.5f, 0);
        return true;
    }

    static boolean grappleSlam(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 8, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        Vec3 hold = p.position().add(facing(p).scale(1.6 + target.getBbWidth() / 2));
        sound(p, SoundEvents.CHAIN_PLACE, 1.5f, 0.6f);
        sound(p, SoundEvents.FISHING_BOBBER_RETRIEVE, 1f, 0.5f);
        fx(p, Kind.BOLT, s.color(), target.position().add(0, target.getBbHeight() / 2, 0), 0.3f, 0);
        target.teleportTo(hold.x, hold.y, hold.z);
        launch(target, 1.0);
        later(p, 9, () -> {
            if (!target.isAlive()) return;
            target.setDeltaMovement(0, -2.2, 0);
            target.hurtMarked = true;
        });
        later(p, 13, () -> {
            Vec3 at = ground(level, target.position());
            if (target.isAlive()) {
                hit(p, s, target, 13 * m(l, tier(p)) + stat(p, Stat.STR, 0.3f));
                stun(target, 30);
            }
            for (LivingEntity e : foes(p, at, 3.2)) {
                if (e == target) continue;
                hit(p, s, e, 5 * m(l, tier(p)) + stat(p, Stat.STR, 0.1f));
                push(e, at, 0.6, 0.4);
            }
            sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1f);
            fxAt(level, Kind.SLAM, s.color(), at, 3.2f, 0);
            fxAt(level, Kind.CRACK, s.color(), at, 3.6f, 0);
        });
        return true;
    }

    static boolean livingAvalanche(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        int ticks = 60;
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 2, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1, false, false, true));
        sound(p, SoundEvents.RAVAGER_ROAR, 0.7f, 0.6f);
        fx(p, Kind.SPIN, s.color(), p.position(), 2.4f, ticks);
        fx(p, Kind.AURA, s.color(), p.position(), 1.3f, ticks);
        repeat(p, ticks, 5, () -> {
            for (LivingEntity e : foes(p, p.position(), 2.6)) {
                hit(p, s, e, 4 * m(l, tier(p)) + stat(p, Stat.VIT, 0.08f));
                push(e, p.position(), 0.7, 0.35);
            }
        });
        repeat(p, ticks, 10, () -> {
            fxAt(level, Kind.CRACK, s.color(), p.position(), 2.2f, 0);
            sound(p, SoundEvents.STONE_BREAK, 1.2f, 0.5f);
        });
        return true;
    }

    static boolean bulwarkTaunt(ServerPlayer p, Skill s, int l) {
        for (LivingEntity e : foes(p, p.position(), 10)) {
            if (e instanceof Mob mob) mob.setTarget(p);
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false));
        }
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, l / 3));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 0));
        sound(p, SoundEvents.SHIELD_BLOCK, 1f, 0.5f);
        sound(p, SoundEvents.RAVAGER_ROAR, 1f, 1f);
        fx(p, Kind.ROAR, s.color(), p.position(), 10, 0);
        fx(p, Kind.DOME, s.color(), p.position(), 1.5f, 120);
        return true;
    }

    static boolean mountainfall(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 1.2f);
        fx(p, Kind.SLAM, s.color(), p.position(), 2.5f, 0);
        leap(p, aimGround(p, 14), 1.15, land -> {
            for (LivingEntity e : foes(p, land, 7)) {
                hit(p, s, e, 20 * m(l, tier(p)) + stat(p, Stat.STR, 0.3f) + stat(p, Stat.VIT, 0.2f));
                launch(e, 0.9);
                stun(e, 40);
            }
            sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.5f);
            sound(p, SoundEvents.ANVIL_LAND, 0.8f, 0.5f);
            SkillParticles.send(level, ParticleTypes.EXPLOSION, land.x, land.y + 0.3, land.z, 5, 2.5, 0.3, 2.5, 0);
            fx(p, Kind.SLAM, s.color(), land, 7, 0);
            fxAt(level, Kind.CRACK, s.color(), land, 7.5f, 0);
            fxAt(level, Kind.QUAKE, s.color(), land, 7, 80);
            for (int t = 20; t <= 80; t += 20) {
                later(p, t, () -> {
                    for (LivingEntity e : foes(p, land, 7)) {
                        hit(p, s, e, 3 * m(l, tier(p)) + stat(p, Stat.VIT, 0.05f));
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
                    }
                    sound(level, land, SoundEvents.STONE_BREAK, 1.5f, 0.5f);
                });
            }
        });
        return true;
    }

    // ================================================================ worldbreaker (rare)

    static float strideSeconds(int l) { return 8 + l / 2f; }
    static float aftershockDamage(int l) { return 2 + l; }

    public static final Skill TECTONIC_UPPERCUT = active("tectonic_uppercut", WB, S, 22, 0xFF7A30, 22, 200, 120,
            WarriorPath::tectonicUppercut, (l, t) -> a(f(18 * m(l, t))));
    public static final Skill METEOR_HURL = active("meteor_hurl", WB, S, 24, 0xFF5A1E, 28, 260, 160,
            WarriorPath::meteorHurl, (l, t) -> a(f(26 * m(l, t))));
    public static final Skill EARTHEN_MIGHT = passive("earthen_might", WB, S, 26, 0xD09050, (l, t) -> a(f(0.8f * l)));
    public static final Skill GRAVITY_WELL = active("gravity_well", WB, S, 29, 0xC060FF, 26, 320, 200,
            WarriorPath::gravityWell, (l, t) -> a(f(14 * m(l, t))));
    public static final Skill TITAN_STRIDE = active("titan_stride", WB, S, 32, 0xFFA040, 30, 700, 460,
            WarriorPath::titanStride, (l, t) -> a(f(strideSeconds(l)), f(8 * m(l, t))));
    public static final Skill AFTERSHOCK = passive("aftershock", WB, S, 35, 0xFFB060, (l, t) -> a(f(aftershockDamage(l))));
    public static final Skill CONTINENTAL_SPLIT = active("continental_split", WB, S, 38, 0xFF8020, 34, 360, 220,
            WarriorPath::continentalSplit, (l, t) -> a(f(16 * m(l, t))));
    public static final Skill WORLDSHAKER_ROAR = active("worldshaker_roar", WB, S, 42, 0xFFC070, 30, 600, 380,
            WarriorPath::worldshakerRoar, (l, t) -> a(f(10 * m(l, t))));
    public static final Skill UNBROKEN = passive("unbroken", WB, S, 46, 0xB0B0C0, (l, t) -> a(f(2 * l)));
    public static final Skill END_OF_AGES = active("end_of_ages", WB, S, 50, 0xFF4010, 45, 1800, 1200,
            WarriorPath::endOfAges, (l, t) -> a(f(12 * m(l, t)), f(40 * m(l, t))));

    static boolean tectonicUppercut(ServerPlayer p, Skill s, int l) {
        Vec3 dir = facing(p);
        ServerLevel level = p.serverLevel();
        for (LivingEntity e : cone(p, 6, 0.2)) {
            hit(p, s, e, 18 * m(l, tier(p)) + stat(p, Stat.STR, 0.35f));
            launch(e, 1.3);
        }
        sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.1f);
        sound(p, SoundEvents.DRIPSTONE_BLOCK_BREAK, 2f, 0.5f);
        fx(p, Kind.SLASH, s.color(), p.position(), 5, 1);
        fx(p, Kind.WAVE, s.color(), p.position().add(dir.scale(6)), 2f, 1);
        fxAt(level, Kind.GEYSER, s.color(), ground(level, p.position().add(dir.scale(3))), 7, 0);
        fxAt(level, Kind.CRACK, s.color(), p.position(), 4.5f, 0);
        return true;
    }

    static boolean meteorHurl(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 24);
        sound(p, SoundEvents.ROOTED_DIRT_BREAK, 2f, 0.4f);
        sound(p, SoundEvents.BLAZE_SHOOT, 1f, 0.5f);
        fx(p, Kind.SLAM, s.color(), p.position(), 2.2f, 0);
        fxAt(level, Kind.METEOR, s.color(), at, 5.5f, 16);
        later(p, 16, () -> {
            for (LivingEntity e : foes(p, at, 5.5)) {
                hit(p, s, e, 26 * m(l, tier(p)) + stat(p, Stat.STR, 0.4f));
                Statuses.apply(e, DamageElement.FIRE, 30, p);
                push(e, at, 0.9, 0.6);
            }
            sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.6f);
            SkillParticles.send(level, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
            fxAt(level, Kind.SLAM, s.color(), at, 5.5f, 0);
            fxAt(level, Kind.NOVA, s.color(), at, 5.5f, 0);
            fxAt(level, Kind.CRACK, s.color(), at, 6.5f, 0);
        });
        return true;
    }

    static boolean gravityWell(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        List<LivingEntity> caught = foes(p, at, 10);
        if (caught.isEmpty()) return false;
        for (LivingEntity e : caught) pull(e, at, 1.7);
        sound(p, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1f, 0.5f);
        sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 0.7f, 1.4f);
        fx(p, Kind.VORTEX, s.color(), at, 4.5f, 14);
        fx(p, Kind.PULSE, s.color(), at, 10, 0);
        later(p, 12, () -> {
            Vec3 here = p.position();
            for (LivingEntity e : foes(p, here, 5)) {
                hit(p, s, e, 14 * m(l, tier(p)) + stat(p, Stat.STR, 0.3f));
                launch(e, 0.7);
                stun(e, 30);
            }
            sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.7f);
            fx(p, Kind.SLAM, 0xFF7A30, here, 5, 0);
            fxAt(level, Kind.CRACK, 0xFF7A30, here, 5.5f, 0);
        });
        return true;
    }

    static boolean titanStride(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        int ticks = Math.round(strideSeconds(l) * 20);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0));
        sound(p, SoundEvents.WARDEN_ROAR, 0.6f, 1.2f);
        fx(p, Kind.ROAR, s.color(), p.position(), 6, 0);
        fx(p, Kind.AURA, s.color(), p.position(), 1.6f, ticks);
        repeat(p, ticks, 20, () -> {
            Vec3 at = p.position();
            for (LivingEntity e : foes(p, at, 4)) {
                hit(p, s, e, 8 * m(l, tier(p)) + stat(p, Stat.STR, 0.1f));
                push(e, at, 0.5, 0.4);
            }
            sound(p, SoundEvents.WARDEN_STEP, 2f, 0.5f);
            fx(p, Kind.SLAM, s.color(), at, 4, 0);
            fxAt(level, Kind.CRACK, s.color(), at, 3.4f, 0);
        });
        return true;
    }

    static boolean continentalSplit(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 start = p.position();
        Set<Integer> struck = new HashSet<>();
        sound(p, SoundEvents.WARDEN_DIG, 1f, 0.6f);
        sound(p, SoundEvents.ANVIL_LAND, 0.6f, 0.5f);
        fx(p, Kind.SLAM, s.color(), start, 3, 0);
        for (int line = -1; line <= 1; line++) {
            Vec3 dir = facing(p).yRot((float) Math.toRadians(line * 26));
            fx(p, Kind.WAVE, s.color(), start.add(dir.scale(16)), 1.4f, 1);
            for (int i = 1; i <= 8; i++) {
                Vec3 at = ground(level, start.add(dir.scale(i * 2.0)));
                later(p, i * 2, () -> {
                    fxAt(level, Kind.CRACK, s.color(), at, 2.4f, 0);
                    SkillParticles.send(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
                    sound(level, at, SoundEvents.DRIPSTONE_BLOCK_BREAK, 1.2f, 0.5f);
                    for (LivingEntity e : foes(p, at, 2.4)) {
                        if (!struck.add(e.getId())) continue;
                        hit(p, s, e, 16 * m(l, tier(p)) + stat(p, Stat.STR, 0.3f));
                        launch(e, 1.0);
                    }
                });
            }
        }
        return true;
    }

    static boolean worldshakerRoar(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        for (LivingEntity e : foes(p, at, 12)) {
            hit(p, s, e, 10 * m(l, tier(p)) + stat(p, Stat.STR, 0.2f));
            stun(e, 60);
            push(e, at, 0.5, 0.3);
        }
        sound(p, SoundEvents.WARDEN_ROAR, 1.2f, 0.7f);
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 0.6f);
        fx(p, Kind.ROAR, s.color(), at, 12, 0);
        later(p, 5, () -> fx(p, Kind.ROAR, 0xFFFFFF, at, 12, 0));
        fxAt(level, Kind.CRACK, s.color(), at, 10, 0);
        fxAt(level, Kind.QUAKE, s.color(), at, 12, 40);
        return true;
    }

    static boolean endOfAges(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 3, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 3, false, false, false));
        sound(p, SoundEvents.WARDEN_EMERGE, 1f, 0.6f);
        sound(p, SoundEvents.END_PORTAL_SPAWN, 0.4f, 0.6f);
        fxAt(level, Kind.ZONE, s.color(), center, 12, 52);
        fxAt(level, Kind.QUAKE, s.color(), center, 12, 52);
        fx(p, Kind.AURA, s.color(), center, 1.8f, 52);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + 0.3;
            double rad = i % 2 == 0 ? 7.5 : 4.5;
            Vec3 pt = ground(level, center.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad));
            later(p, 4 + i * 4, () -> fxAt(level, Kind.PILLAR, s.color(), pt, 2.4f, 8));
            later(p, 12 + i * 4, () -> {
                for (LivingEntity e : foes(p, pt, 3)) {
                    hit(p, s, e, 12 * m(l, tier(p)) + stat(p, Stat.STR, 0.2f));
                    launch(e, 0.7);
                }
                sound(level, pt, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 0.9f);
                fxAt(level, Kind.CRACK, s.color(), pt, 3, 0);
            });
        }
        later(p, 52, () -> {
            for (LivingEntity e : foes(p, center, 12)) {
                hit(p, s, e, 40 * m(l, tier(p)) + stat(p, Stat.STR, 0.5f));
                launch(e, 1.2);
                stun(e, 60);
            }
            sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.4f);
            sound(level, center, SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.5f);
            sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 0.6f);
            SkillParticles.send(level, ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 10, 5, 0.5, 5, 0);
            fxAt(level, Kind.SLAM, s.color(), center, 12, 0);
            fxAt(level, Kind.CRACK, s.color(), center, 12, 0);
            fxAt(level, Kind.GEYSER, s.color(), center, 12, 0);
            fxAt(level, Kind.ROAR, 0xFFFFFF, center, 12, 0);
            for (int i = 0; i < 6; i++) {
                double ang = i * Math.PI / 3;
                Vec3 pt = ground(level, center.add(Math.cos(ang) * 8, 0, Math.sin(ang) * 8));
                later(p, 3 + i, () -> fxAt(level, Kind.GEYSER, s.color(), pt, 7, 0));
            }
        });
        return true;
    }

    // ================================================================ passives

    static {
        bonus(THICK_BLOOD, Attributes.MAX_HEALTH, 0.025f, TOTAL, "health");
        bonus(THICK_BLOOD, Attributes.ATTACK_DAMAGE, 0.25f, ADD, "attack");
        bonus(IRON_DISCIPLINE, Attributes.ARMOR, 0.5f, ADD, "armor");
        bonus(IRON_DISCIPLINE, Attributes.KNOCKBACK_RESISTANCE, 0.03f, ADD, "knockback");
        bonus(KEEN_EDGE, Attributes.ATTACK_DAMAGE, 0.4f, ADD, "attack");
        bonus(WIND_STEP, Attributes.MOVEMENT_SPEED, 0.015f, MUL, "speed");
        bonus(IMMOVABLE, Attributes.ARMOR, 0.6f, ADD, "armor");
        bonus(IMMOVABLE, Attributes.ARMOR_TOUGHNESS, 0.3f, ADD, "toughness");
        bonus(EARTHEN_MIGHT, Attributes.ATTACK_DAMAGE, 0.8f, ADD, "attack");
        bonus(EARTHEN_MIGHT, Attributes.ATTACK_KNOCKBACK, 0.1f, ADD, "knockback");
    }

    /** Extra damage, in percent, when a warrior subclass player strikes. */
    static float strikeBonus(ServerPlayer p, SkillData data, LivingEntity target, long now) {
        float bonus = 0;
        int scent = data.level(BLOODSCENT.path());
        if (scent > 0 && target.getHealth() < target.getMaxHealth() * 0.5f) bonus += 2 * scent;
        int momentum = data.level(MOMENTUM.path());
        if (momentum > 0 && p.isSprinting()) bonus += 2 * momentum;
        return bonus;
    }

    /** Execution Order: extra damage, in percent, from anyone who strikes the marked creature. */
    static float orderBonus(LivingEntity target, long now) {
        if (ORDERS.isEmpty()) return 0;
        long[] order = ORDERS.get(target);
        if (order == null) return 0;
        if (order[0] <= now) {
            ORDERS.remove(target);
            return 0;
        }
        return order[1];
    }

    /** Aftershock: a melee hit splashes onto the creatures next to the target, at most twice a second. */
    static void afterStrike(ServerPlayer p, SkillData data, LivingEntity target, boolean melee, long now) {
        int shock = data.level(AFTERSHOCK.path());
        if (shock <= 0 || !melee || splashing || now - SPLASHED.getOrDefault(p, 0L) < 10) return;
        SPLASHED.put(p, now);
        splashing = true;
        try {
            for (LivingEntity e : foes(p, target.position(), 2.8)) {
                if (e != target) e.hurt(p.damageSources().indirectMagic(p, p), com.beyondsolo.system.Balance.flatDamage(p, aftershockDamage(shock)));
            }
        } finally {
            splashing = false;
        }
        fxAt(p.serverLevel(), Kind.CRACK, AFTERSHOCK.color(), target.position(), 2.8f, 0);
    }

    /**
     * A warrior subclass player is struck. Returns the damage to take, or a negative number when the blow is
     * turned aside entirely (Riposte).
     */
    static float whenStruck(ServerPlayer p, SkillData data, LivingEntity attacker, boolean melee, DamageSource source, float amount) {
        int riposte = data.level(RIPOSTE.path());
        if (riposte > 0 && melee && attacker != null && attacker.isAlive()
                && com.beyondsolo.system.BalanceHooks.avoids(p, source, riposteChance(riposte) / 100f)) {
            attacker.hurt(p.damageSources().playerAttack(p), com.beyondsolo.system.Balance.flatDamage(p, 3 + riposte));
            sound(p, SoundEvents.ANVIL_PLACE, 0.5f, 1.9f);
            fx(p, Kind.SLASH, RIPOSTE.color(), p.position(), 2.6f, 1);
            return -1;
        }
        int pain = data.level(PAIN_TOLERANCE.path());
        if (pain > 0 && p.getHealth() < p.getMaxHealth() * 0.5f) amount *= 1 - 0.015f * pain;
        int unbroken = data.level(UNBROKEN.path());
        if (unbroken > 0) amount *= 1 - 0.02f * unbroken;
        return amount;
    }

    static void tickSecond(ServerPlayer p, SkillData data) {
        int drums = data.level(WAR_DRUMS.path());
        if (drums > 0) {
            for (Player ally : allies(p, 8)) ally.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 40, drums >= 6 ? 1 : 0, true, false, true));
        }
        int wind = data.level(SECOND_WIND.path());
        if (wind > 0 && p.getHealth() < p.getMaxHealth() * 0.3f) com.beyondsolo.system.Balance.regenFlat(p, windRegen(wind));
    }

    static void onKill(ServerPlayer p, SkillData data, LivingEntity dead) {
        ORDERS.remove(dead);
        int spoils = data.level(SPOILS_OF_WAR.path());
        if (spoils > 0) {
            SystemAPI.restoreMana(p, spoilsMana(spoils));
            p.heal(0.5f + 0.1f * spoils);
        }
    }

    /** Last Stand refuses one death a cast: the first blow that would kill leaves one point, and the stand is spent. */
    static boolean refusesDeath(ServerPlayer p, SkillData data) {
        if (!data.hasBuff(BUFF_LAST_STAND, p.level().getGameTime())) return false;
        SkillAPI.clearBuff(p, BUFF_LAST_STAND);
        p.setHealth(1);
        sound(p, SoundEvents.TOTEM_USE, 0.4f, 1.6f);
        fx(p, Kind.ROAR, LAST_STAND.color(), p.position(), 3, 0);
        return true;
    }

    /** Touch the class so its skills are registered. */
    static void init() {}

    private WarriorPath() {}
}
