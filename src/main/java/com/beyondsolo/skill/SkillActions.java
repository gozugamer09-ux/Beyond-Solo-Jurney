package com.beyondsolo.skill;

import com.beyondsolo.combat.Buildups;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.npc.TravelerEntity;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * What every active skill does, and the numbers shared with the skill descriptions.
 *
 * <p>Power grows 10% per skill level and 12% per rank tier ({@link #mult}); the player's stats add on top at cast
 * time (STR for strikes, INT for spells, AGI for shots). Effects are drawn on the clients from {@link SkillFx}
 * events; the server only spawns vanilla particles for impacts that everyone should see.</p>
 */
public final class SkillActions {
    /** Buff keys stored in {@link SkillData}. */
    public static final String BUFF_SHADOWSTEP = "shadowstep";
    public static final String BUFF_VENOM_EDGE = "venom_edge";
    public static final String BUFF_VEIL = "veil";
    public static final String BUFF_SHROUD = "shroud";
    public static final String BUFF_GAZE = "gaze";
    /** Tag on arrows from Arrow Rain, so they never hurt the caster. */
    public static final String RAIN_TAG = "beyondsolo_arrow_rain";

    /** Hunter's Mark: creature -> {end game time, bonus percent}. Server thread only. */
    static final Map<LivingEntity, long[]> MARKS = new WeakHashMap<>();

    // ================================================================ numbers

    public static float mult(int level, int rankTier) {
        return (1f + 0.1f * (level - 1)) * (1f + 0.12f * rankTier);
    }

    private static float mult(ServerPlayer p, int level) {
        return mult(level, SystemAPI.get(p).rank().tier());
    }

    private static float statBonus(ServerPlayer p, Stat stat, float perPoint) {
        return Math.max(0, SystemAPI.get(p).stat(stat) - Stat.BASE) * perPoint;
    }

    public static float surgeDistance(int l) { return 6 + 0.4f * (l - 1); }
    public static float scanRadius(int l) { return 24 + 3 * (l - 1); }
    public static float scanSeconds(int l) { return 8 + l; }
    public static float gazeSeconds(int l) { return 15 + 2 * l; }
    public static float weakSpotBonus(int l) { return 8 + 2 * l; }
    public static float shroudSeconds(int l) { return 3 + l / 2f; }
    public static float manaFlow(int l) { return 0.2f * l; }
    public static float shadowstepBonus(int l) { return 30 + 5 * (l - 1); }
    public static float veilSeconds(int l) { return 6 + l / 2f; }
    public static int meteors(int l) { return 5 + l / 3; }
    public static float bulwarkShield(int l) { return 4 * ((l - 1) / 3 + 1); }
    public static float aegisSeconds(int l) { return 8 + l / 2f; }
    public static float lifeBloom(int l) { return 0.15f * l; }
    public static float sanctuaryHeal(int l) { return 1 + 0.2f * l; }
    public static float markBonus(int l) { return 15 + 2 * l; }
    public static int rainArrows(int l) { return 14 + 2 * l; }
    public static float rushDistance(int l) { return 7 + 0.3f * (l - 1); }
    public static float bindSeconds(int l) { return 2.5f + 0.25f * l; }
    public static int chainJumps(int l) { return 2 + l / 3; }
    public static float bashSeconds(int l) { return 1.5f + 0.1f * l; }
    public static float tidewellSeconds(int l) { return 6 + l / 2f; }
    public static int volleyBolts(int l) { return 3 + l / 3; }
    /** Steady Aim counts hits from further than this. */
    public static final float STEADY_RANGE = 10;

    /** A number for a description: no ".0", one decimal otherwise. */
    public static String f(float v) {
        return Math.abs(v - Math.round(v)) < 0.05f ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    // ================================================================ shared

    public static boolean surgeStep(ServerPlayer p, Skill s, int l) {
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1.0e-4) dir = Vec3.directionFromRotation(0, p.getYRot());
        dir = dir.normalize();
        Vec3 start = p.position();
        float speed = surgeDistance(l) * 0.3f;
        p.setDeltaMovement(dir.x * speed, p.onGround() ? 0.2 : Math.max(0.1, p.getDeltaMovement().y), dir.z * speed);
        p.hurtMarked = true;
        p.fallDistance = 0;
        p.invulnerableTime = Math.max(p.invulnerableTime, 8);
        sound(p, SoundEvents.BREEZE_JUMP, 0.9f, 1.3f);
        SkillFx.play(p, SkillFxPayload.Kind.DASH, s.color(), start, 1, 10);
        return true;
    }

    public static boolean pulseScan(ServerPlayer p, Skill s, int l) {
        float r = scanRadius(l);
        int found = 0;
        for (LivingEntity e : around(p, p.position(), r, x -> isFoe(p, x))) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.round(scanSeconds(l) * 20), 0, false, false));
            found++;
        }
        sound(p, SoundEvents.BEACON_ACTIVATE, 0.7f, 1.8f);
        sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
        // the ring reaches much further for gates: their rank, which way they lie, and how close they are to breaking
        int gates = com.beyondsolo.breaks.GateSense.pulse(p, l, Math.round(scanSeconds(l)));
        p.displayClientMessage(gates > 0 ? Component.translatable("skill.beyondsolo.pulse_scan.found_gates", found, gates)
                : Component.translatable("skill.beyondsolo.pulse_scan.found", found), true);
        SkillFx.play(p, SkillFxPayload.Kind.PULSE, s.color(), p.position(), r, 0);
        // in a gate, hidden walls and trap cores within reach show through the rock
        com.beyondsolo.depths.HiddenRooms.scan(p, r);
        return true;
    }

    /** Reads every creature nearby: their level, health and weak spot show over them; hits on a weak spot deal more. */
    public static boolean appraisingGaze(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(gazeSeconds(l) * 20);
        SkillAPI.setBuff(p, BUFF_GAZE, ticks);
        sound(p, SoundEvents.SPYGLASS_USE, 1f, 0.8f);
        sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.7f);
        SkillFx.play(p, SkillFxPayload.Kind.PULSE, s.color(), p.position(), 20, 0);
        return true;
    }

    /** Wraps the player in shadow: hidden from monsters for a few seconds; attacking ends it. */
    public static boolean phantomShroud(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(shroudSeconds(l) * 20);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
        SkillAPI.setBuff(p, BUFF_SHROUD, ticks);
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p)) m.setTarget(null);
        sound(p, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.8f, 1.3f);
        SkillFx.play(p, SkillFxPayload.Kind.VEIL, s.color(), p.position(), 1.2f, ticks);
        return true;
    }

    // ================================================================ warrior

    /** Two cuts: a wide sweep, then a faster one back the other way for a little less. */
    public static boolean rendingArc(ServerPlayer p, Skill s, int l) {
        float dmg = 6 * mult(p, l) + statBonus(p, Stat.STR, 0.15f);
        arcCut(p, s, dmg * 0.6f, 0);
        ServerScheduler.schedule(5, () -> {
            if (p.isAlive() && !p.hasDisconnected()) arcCut(p, s, dmg * 0.4f, 1);
        });
        return true;
    }

    private static void arcCut(ServerPlayer p, Skill s, float dmg, int back) {
        Vec3 look = flat(p.getLookAngle(), p);
        for (LivingEntity e : inCone(p, look, 4.2, 0.42)) { // ~130 degrees
            hit(p, s, e, dmg, false);
            e.knockback(back == 0 ? 0.25 : 0.6, -look.x, -look.z);
        }
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, back == 0 ? 0.75f : 0.95f);
        sound(p, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.5f, back == 0 ? 1.6f : 1.9f);
        SkillFx.play(p, SkillFxPayload.Kind.SLASH, s.color(), p.position(), 4.0f, back);
    }

    /** Charges ahead, throwing aside everything in the way. */
    public static boolean bullRush(ServerPlayer p, Skill s, int l) {
        Vec3 look = flat(p.getLookAngle(), p);
        Vec3 start = p.position();
        float speed = rushDistance(l) * 0.26f;
        p.setDeltaMovement(look.x * speed, p.onGround() ? 0.12 : p.getDeltaMovement().y, look.z * speed);
        p.hurtMarked = true;
        p.invulnerableTime = Math.max(p.invulnerableTime, 10);
        float dmg = 5 * mult(p, l) + statBonus(p, Stat.STR, 0.15f);
        java.util.Set<LivingEntity> struck = new java.util.HashSet<>();
        for (int t = 0; t <= 8; t++) {
            ServerScheduler.schedule(t, () -> {
                if (!p.isAlive() || p.hasDisconnected()) return;
                for (LivingEntity e : around(p, p.position(), 1.9, x -> canHit(p, x))) {
                    if (!struck.add(e)) continue;
                    hit(p, s, e, dmg, false);
                    e.setDeltaMovement(look.x * 0.9, 0.42, look.z * 0.9);
                    e.hurtMarked = true;
                    SkillParticles.send(p.serverLevel(), ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.3, 0.3, 0.4);
                    sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
                }
            });
        }
        sound(p, SoundEvents.RAVAGER_STEP, 1f, 0.8f);
        sound(p, SoundEvents.BREEZE_JUMP, 0.7f, 0.8f);
        SkillFx.play(p, SkillFxPayload.Kind.DASH, s.color(), start, 1, 10);
        SkillFx.play(p, SkillFxPayload.Kind.WAVE, s.color(), start.add(look.scale(rushDistance(l))), 1.0f, 2);
        return true;
    }

    /** A wall of fire rolls out along the ground. */
    public static boolean flameWave(ServerPlayer p, Skill s, int l) {
        Vec3 look = flat(p.getLookAngle(), p);
        float dmg = 6 * mult(p, l) + statBonus(p, Stat.STR, 0.15f);
        for (LivingEntity e : inLine(p, look, 9, 1.5)) {
            hit(p, s, e, dmg, true);
            Statuses.apply(e, DamageElement.FIRE, 25 + 3 * l, p);
            e.igniteForSeconds(3);
        }
        sound(p, SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.6f);
        SkillFx.play(p, SkillFxPayload.Kind.WAVE, s.color(), p.position().add(look.scale(9)), 1.5f, 0);
        return true;
    }

    /** Spins for a second: four cuts all around, each drawing foes in. */
    public static boolean whirlwind(ServerPlayer p, Skill s, int l) {
        float dmg = 2.5f * mult(p, l) + statBonus(p, Stat.STR, 0.08f);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 25, 0, false, false));
        for (int i = 0; i < 4; i++) {
            ServerScheduler.schedule(i * 5, () -> {
                if (!p.isAlive() || p.hasDisconnected()) return;
                for (LivingEntity e : around(p, p.position(), 3.8, x -> canHit(p, x))) {
                    hit(p, s, e, dmg, false);
                    Vec3 in = p.position().subtract(e.position()).multiply(1, 0, 1);
                    if (in.lengthSqr() > 2.2) {
                        in = in.normalize();
                        e.setDeltaMovement(in.x * 0.3, 0.1, in.z * 0.3);
                        e.hurtMarked = true;
                    }
                }
                sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.1f);
            });
        }
        sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.7f, 1.2f);
        SkillFx.play(p, SkillFxPayload.Kind.SPIN, s.color(), p.position(), 3.8f, 20);
        return true;
    }

    public static boolean warCry(ServerPlayer p, Skill s, int l) {
        int ticks = (8 + l) * 20;
        for (Player ally : allies(p, 8)) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, l >= 6 ? 1 : 0));
        }
        for (LivingEntity e : around(p, p.position(), 8, x -> isFoe(p, x))) {
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
        }
        sound(p, SoundEvents.RAVAGER_ROAR, 0.8f, 1.35f);
        SkillFx.play(p, SkillFxPayload.Kind.ROAR, s.color(), p.position(), 8, 0);
        SkillFx.play(p, SkillFxPayload.Kind.AURA, s.color(), p.position(), 1, ticks);
        return true;
    }

    public static boolean earthsplitter(ServerPlayer p, Skill s, int l) {
        Vec3 look = flat(p.getLookAngle(), p);
        p.setDeltaMovement(look.x * 0.45, 0.95, look.z * 0.45);
        p.hurtMarked = true;
        sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 0.8f, 0.7f);
        SkillFx.play(p, SkillFxPayload.Kind.DASH, s.color(), p.position(), 1, 10);
        slamWhenLanded(p, s, l, 0);
        return true;
    }

    private static void slamWhenLanded(ServerPlayer p, Skill s, int l, int ticks) {
        ServerScheduler.schedule(1, () -> {
            if (!p.isAlive() || p.hasDisconnected()) return;
            p.fallDistance = 0;
            if (ticks == 9) {
                // drive down hard after the top of the leap
                p.setDeltaMovement(p.getDeltaMovement().x * 0.5, -1.6, p.getDeltaMovement().z * 0.5);
                p.hurtMarked = true;
            }
            if ((ticks > 4 && p.onGround()) || ticks > 50) {
                Vec3 at = p.position();
                float dmg = 12 * mult(p, l) + statBonus(p, Stat.STR, 0.2f);
                for (LivingEntity e : around(p, at, 5, x -> canHit(p, x))) {
                    hit(p, s, e, dmg * (1f - 0.35f * (float) (e.distanceTo(p) / 5f)), false);
                    Vec3 away = e.position().subtract(at).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.x * 0.5, 0.65, away.z * 0.5);
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                }
                sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 0.8f);
                sound(p, SoundEvents.ANVIL_LAND, 0.5f, 0.5f);
                SkillParticles.send(p.serverLevel(), ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 3, 1.2, 0.1, 1.2, 0);
                SkillFx.play(p, SkillFxPayload.Kind.SLAM, s.color(), at, 5, 0);
                return;
            }
            slamWhenLanded(p, s, l, ticks + 1);
        });
    }

    // ================================================================ assassin

    public static boolean shadowstep(ServerPlayer p, Skill s, int l) {
        Vec3 from = p.position();
        LivingEntity target = aim(p, 14, x -> canHit(p, x));
        Vec3 dest = null;
        if (target != null) {
            Vec3 back = flat(target.getLookAngle(), target).scale(-(target.getBbWidth() / 2 + 0.9));
            for (Vec3 c : new Vec3[]{target.position().add(back), target.position().add(from.subtract(target.position()).multiply(1, 0, 1).normalize().scale(target.getBbWidth() / 2 + 0.9))}) {
                if (p.level().noCollision(p, p.getBoundingBox().move(c.subtract(from)))) {
                    dest = c;
                    break;
                }
            }
        } else {
            Vec3 look = flat(p.getLookAngle(), p);
            for (double d = 8; d >= 2; d -= 0.5) {
                Vec3 c = from.add(look.scale(d));
                if (p.level().noCollision(p, p.getBoundingBox().move(c.subtract(from)))
                        && p.level().clip(new ClipContext(p.getEyePosition(), c.add(0, p.getEyeHeight(), 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS) {
                    dest = c;
                    break;
                }
            }
        }
        if (dest == null) return false;
        float yaw = p.getYRot();
        float pitch = p.getXRot();
        if (target != null) {
            Vec3 d = target.getEyePosition().subtract(dest.add(0, p.getEyeHeight(), 0));
            yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
            pitch = (float) (-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
        }
        sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.5f);
        p.teleportTo(p.serverLevel(), dest.x, dest.y, dest.z, yaw, pitch);
        if (p.position().distanceToSqr(dest) > 0.01) p.moveTo(dest.x, dest.y, dest.z, yaw, pitch); // players without a connection
        p.fallDistance = 0;
        sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8f, 1.2f);
        SkillAPI.setBuff(p, BUFF_SHADOWSTEP, 60);
        SkillFx.play(p, SkillFxPayload.Kind.BLINK, s.color(), from, 1, 0);
        return true;
    }

    public static boolean venomEdge(ServerPlayer p, Skill s, int l) {
        int ticks = (10 + l) * 20;
        SkillAPI.setBuff(p, BUFF_VENOM_EDGE, ticks);
        sound(p, SoundEvents.BREWING_STAND_BREW, 1f, 1.4f);
        SkillFx.play(p, SkillFxPayload.Kind.AURA, s.color(), p.position(), 1, ticks);
        return true;
    }

    public static boolean veilOfNight(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(veilSeconds(l) * 20);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
        SkillAPI.setBuff(p, BUFF_VEIL, ticks);
        for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), m -> m.getTarget() == p)) m.setTarget(null);
        sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 0.9f, 0.8f);
        SkillFx.play(p, SkillFxPayload.Kind.VEIL, s.color(), p.position(), 1.5f, ticks);
        return true;
    }

    // ================================================================ mage

    public static boolean arcBolt(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 28, x -> canHit(p, x));
        Vec3 end;
        if (target != null) {
            end = target.position().add(0, target.getBbHeight() * 0.55, 0);
            hit(p, s, target, 7 * mult(p, l) + statBonus(p, Stat.INT, 0.25f), true);
            SkillParticles.send(p.serverLevel(), ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 14, 0.25, 0.25, 0.25, 0.25);
        } else {
            end = aimPoint(p, 28);
        }
        sound(p, SoundEvents.BLAZE_SHOOT, 0.6f, 1.7f);
        sound(p, SoundEvents.AMETHYST_BLOCK_HIT, 1f, 0.8f);
        SkillFx.play(p, SkillFxPayload.Kind.BOLT, s.color(), end, 0.3f, 0);
        return true;
    }

    public static boolean emberBurst(ServerPlayer p, Skill s, int l) {
        float dmg = 5 * mult(p, l) + statBonus(p, Stat.INT, 0.2f);
        for (LivingEntity e : around(p, p.position(), 5, x -> canHit(p, x))) {
            hit(p, s, e, dmg, true);
            Statuses.apply(e, DamageElement.FIRE, 30 + 4 * l, p);
            Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1).normalize();
            e.knockback(0.7, -away.x, -away.z);
        }
        sound(p, SoundEvents.FIRECHARGE_USE, 1f, 0.8f);
        sound(p, SoundEvents.BLAZE_SHOOT, 0.8f, 0.6f);
        SkillFx.play(p, SkillFxPayload.Kind.NOVA, s.color(), p.position(), 5, 0);
        return true;
    }

    public static boolean starfall(ServerPlayer p, Skill s, int l) {
        Vec3 center = aimPoint(p, 30);
        int count = meteors(l);
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.EVOKER_PREPARE_SUMMON, 1f, 0.7f);
        SkillFx.play(p, SkillFxPayload.Kind.ZONE, s.color(), center, 4, 10 + 6 * count + 10);
        for (int i = 0; i < count; i++) {
            double ang = p.getRandom().nextDouble() * Math.PI * 2;
            double rad = i == 0 ? 0 : 1 + p.getRandom().nextDouble() * 2.6;
            Vec3 at = ground(level, center.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad));
            int delay = 6 * i;
            ServerScheduler.schedule(delay, () -> {
                if (p.level() == level) SkillFx.play(p, SkillFxPayload.Kind.METEOR, s.color(), at, 2.5f, 12);
            });
            ServerScheduler.schedule(delay + 12, () -> {
                if (p.level() != level) return;
                float dmg = 8 * mult(p, l) + statBonus(p, Stat.INT, 0.2f);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(2.6), x -> canHit(p, x))) {
                    hit(p, s, e, dmg, true);
                    Statuses.apply(e, DamageElement.FIRE, 20, p);
                }
                SkillParticles.send(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 1, 0, 0, 0, 0);
                SkillParticles.send(level, ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 8, 0.6, 0.2, 0.6, 0);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.1f);
            });
        }
        return true;
    }

    // ================================================================ guardian

    public static boolean bulwark(ServerPlayer p, Skill s, int l) {
        int amp = (l - 1) / 3;
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, amp));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 0));
        sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1f, 0.8f);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.6f);
        SkillFx.play(p, SkillFxPayload.Kind.DOME, s.color(), p.position(), 1.6f, 120);
        return true;
    }

    public static boolean provoke(ServerPlayer p, Skill s, int l) {
        for (LivingEntity e : around(p, p.position(), 10, x -> isFoe(p, x))) {
            if (e instanceof Mob m) m.setTarget(p);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
        }
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 0));
        sound(p, SoundEvents.ANVIL_LAND, 0.5f, 0.7f);
        sound(p, SoundEvents.RAVAGER_STUNNED, 0.6f, 1.3f);
        SkillFx.play(p, SkillFxPayload.Kind.ROAR, s.color(), p.position(), 10, 0);
        return true;
    }

    public static boolean aegisDome(ServerPlayer p, Skill s, int l) {
        Vec3 center = p.position();
        int ticks = Math.round(aegisSeconds(l) * 20);
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.BEACON_ACTIVATE, 1f, 0.9f);
        SkillFx.play(p, SkillFxPayload.Kind.DOME, s.color(), center, 5, ticks);
        for (int t = 0; t < ticks; t += 10) {
            ServerScheduler.schedule(t, () -> {
                if (p.level() != level) return;
                AABB box = new AABB(center, center).inflate(5);
                for (Player ally : level.getEntitiesOfClass(Player.class, box, x -> x.position().distanceTo(center) <= 5)) {
                    ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 25, 1, true, false, true));
                    ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 30, 0, true, false, true));
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> isFoe(p, x) && x.position().distanceTo(center) < 5)) {
                    Vec3 away = e.position().subtract(center).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.x * 0.6, 0.25, away.z * 0.6);
                    e.hurtMarked = true;
                }
            });
        }
        return true;
    }

    // ================================================================ healer

    public static boolean mendingLight(ServerPlayer p, Skill s, int l) {
        float heal = 5 * mult(p, l) + statBonus(p, Stat.INT, 0.1f);
        for (Player ally : allies(p, 7)) {
            ally.heal(healing(p, heal));
            SkillFx.play(ally, SkillFxPayload.Kind.HEAL, s.color(), ally.position(), 1, 0);
        }
        sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 1.6f);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 0.5f, 1.9f);
        return true;
    }

    private static final List<Holder<MobEffect>> PURIFIED = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.WEAKNESS,
            MobEffects.MOVEMENT_SLOWDOWN, MobEffects.BLINDNESS, MobEffects.DARKNESS, MobEffects.HUNGER, MobEffects.CONFUSION,
            MobEffects.DIG_SLOWDOWN);

    public static boolean purify(ServerPlayer p, Skill s, int l) {
        for (Player ally : allies(p, 7)) {
            for (Holder<MobEffect> e : PURIFIED) ally.removeEffect(e);
            // ordinary venom and the brand, not heavy venom or a deep burn: those still need their remedies
            ally.removeEffect(ModEffects.VENOM);
            ally.removeEffect(ModEffects.CRIMSON_BRAND);
            Buildups.clear(ally, Buildups.BLOOD);
            Buildups.clear(ally, Statuses.VENOM);
            Buildups.clear(ally, Statuses.CRIMSON);
            Buildups.clear(ally, Statuses.BURN);
            SkillFx.play(ally, SkillFxPayload.Kind.HEAL, s.color(), ally.position(), 1.3f, 0);
        }
        sound(p, SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.8f);
        sound(p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 1.5f);
        return true;
    }

    public static boolean sanctuary(ServerPlayer p, Skill s, int l) {
        Vec3 center = p.position();
        int ticks = 160;
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.BEACON_ACTIVATE, 1f, 1.4f);
        SkillFx.play(p, SkillFxPayload.Kind.ZONE, s.color(), center, 6, ticks);
        for (int t = 0; t < ticks; t += 20) {
            ServerScheduler.schedule(t, () -> {
                if (p.level() != level) return;
                AABB box = new AABB(center, center).inflate(6);
                for (Player ally : level.getEntitiesOfClass(Player.class, box, x -> x.position().distanceTo(center) <= 6)) {
                    ally.heal(healing(p, sanctuaryHeal(l)));
                }
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> isFoe(p, x) && x.position().distanceTo(center) <= 6)) {
                    hit(p, s, e, sanctuaryHeal(l), true);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0));
                }
            });
        }
        return true;
    }

    // ================================================================ ranger

    public static boolean piercingShot(ServerPlayer p, Skill s, int l) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = aimPoint(p, 32);
        float dmg = 6 * mult(p, l) + statBonus(p, Stat.AGI, 0.15f);
        AABB sweep = new AABB(eye, end).inflate(1);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, sweep, x -> canHit(p, x))) {
            Optional<Vec3> hitAt = e.getBoundingBox().inflate(0.35).clip(eye, end);
            if (hitAt.isEmpty()) continue;
            hit(p, s, e, dmg, false);
            Vec3 dir = end.subtract(eye).normalize();
            e.knockback(0.3, -dir.x, -dir.z);
            SkillParticles.send(p.serverLevel(), ParticleTypes.CRIT, hitAt.get().x, hitAt.get().y, hitAt.get().z, 8, 0.1, 0.1, 0.1, 0.3);
        }
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.7f);
        sound(p, SoundEvents.ARROW_SHOOT, 0.8f, 1.6f);
        SkillFx.play(p, SkillFxPayload.Kind.SHOT, s.color(), end, 0.4f, 0);
        return true;
    }

    public static boolean huntersMark(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 32, x -> canHit(p, x));
        if (target == null) return false;
        int ticks = 240;
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
        MARKS.put(target, new long[]{p.level().getGameTime() + ticks, Math.round(markBonus(l))});
        sound(p, SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1.6f);
        sound(p, SoundEvents.ARROW_HIT_PLAYER, 0.6f, 0.6f);
        SkillFx.play(target, SkillFxPayload.Kind.MARK, s.color(), target.position(), target.getBbHeight(), ticks);
        return true;
    }

    public static boolean arrowRain(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = ground(level, aimPoint(p, 30));
        int count = rainArrows(l);
        double damage = (2.5 + 0.2 * l) * (1 + 0.12 * SystemAPI.get(p).rank().tier());
        sound(p, SoundEvents.ARROW_SHOOT, 1f, 0.6f);
        SkillFx.play(p, SkillFxPayload.Kind.ZONE, s.color(), center, 4, 56);
        SkillFx.play(p, SkillFxPayload.Kind.RAIN, s.color(), center, 4, 50);
        SkillFx.play(p, SkillFxPayload.Kind.SHOT, s.color(), center.add(0, 16, 0), 0.5f, 0);
        for (int i = 0; i < count; i++) {
            int delay = 6 + i * 40 / count;
            ServerScheduler.schedule(delay, () -> {
                if (p.level() != level) return;
                double ang = level.random.nextDouble() * Math.PI * 2;
                double rad = Math.sqrt(level.random.nextDouble()) * 4;
                Arrow arrow = new Arrow(level, center.x + Math.cos(ang) * rad, center.y + 14, center.z + Math.sin(ang) * rad,
                        new ItemStack(Items.ARROW), null);
                arrow.setOwner(p);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.setBaseDamage(damage);
                arrow.setCritArrow(true);
                arrow.addTag(RAIN_TAG);
                arrow.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, -2.4, (level.random.nextDouble() - 0.5) * 0.1);
                level.addFreshEntity(arrow);
            });
        }
        return true;
    }

    // ================================================================ second batch

    /** Blades thrown in a wide fan. */
    public static boolean fanOfBlades(ServerPlayer p, Skill s, int l) {
        Vec3 look = flat(p.getLookAngle(), p);
        float dmg = 4.5f * mult(p, l) + statBonus(p, Stat.AGI, 0.15f);
        for (LivingEntity e : inCone(p, look, 9, 0.64)) {
            if (!p.hasLineOfSight(e)) continue;
            hit(p, s, e, dmg, false);
            SkillParticles.send(p.serverLevel(), ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.2, 0.2, 0.2, 0.3);
        }
        sound(p, SoundEvents.TRIDENT_THROW.value(), 0.9f, 1.6f);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.5f);
        SkillFx.play(p, SkillFxPayload.Kind.FAN, s.color(), p.position().add(look.scale(9)), 9, 0);
        return true;
    }

    /** Pins the creature the player is looking at to its own shadow. */
    public static boolean shadowBind(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 16, x -> canHit(p, x));
        if (target == null) return false;
        int ticks = Math.round(bindSeconds(l) * 20);
        hit(p, s, target, 4 * mult(p, l) + statBonus(p, Stat.AGI, 0.1f), true);
        SkillCombat.stun(target, ticks);
        sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.4f, 1.6f);
        sound(p, SoundEvents.CHAIN_PLACE, 1f, 0.6f);
        SkillFx.play(target, SkillFxPayload.Kind.BIND, s.color(), target.position(), Math.max(1.1f, target.getBbWidth() * 1.4f), ticks);
        return true;
    }

    /** A spin that throws poisoned petals at everything around. */
    public static boolean deathBlossom(ServerPlayer p, Skill s, int l) {
        float dmg = 5 * mult(p, l) + statBonus(p, Stat.AGI, 0.15f);
        for (LivingEntity e : around(p, p.position(), 5, x -> canHit(p, x))) {
            hit(p, s, e, dmg, false);
            Statuses.apply(e, DamageElement.POISON, 20 + 3 * l, p);
        }
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.3f);
        sound(p, SoundEvents.SPORE_BLOSSOM_BREAK, 1f, 0.7f);
        SkillFx.play(p, SkillFxPayload.Kind.BLOSSOM, s.color(), p.position(), 5, 0);
        return true;
    }

    /** A ball of water that bursts where it lands, soaking and slowing. */
    public static boolean tidalOrb(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 24, x -> canHit(p, x));
        Vec3 at = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : aimPoint(p, 24);
        float dmg = 5.5f * mult(p, l) + statBonus(p, Stat.INT, 0.2f);
        for (LivingEntity e : around(p, at, 3.2, x -> canHit(p, x))) {
            hit(p, s, e, dmg, true);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
            e.clearFire();
        }
        SkillParticles.send(p.serverLevel(), ParticleTypes.SPLASH, at.x, at.y, at.z, 60, 1.4, 0.6, 1.4, 0.3);
        SkillParticles.send(p.serverLevel(), ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, 20, 1.2, 0.6, 1.2, 0.05);
        sound(p, SoundEvents.BUCKET_EMPTY, 0.8f, 1.4f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1f, 0.8f);
        SkillFx.play(p, SkillFxPayload.Kind.SPLASH, s.color(), at, 3.2f, 0);
        return true;
    }

    /** Lightning that jumps from creature to creature, a little weaker each time. */
    public static boolean chainLightning(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 24, x -> canHit(p, x));
        if (target == null) return false;
        float dmg = 6 * mult(p, l) + statBonus(p, Stat.INT, 0.2f);
        java.util.Set<LivingEntity> struck = new java.util.HashSet<>();
        Entity from = p;
        LivingEntity next = target;
        for (int jump = 0; jump <= chainJumps(l) && next != null; jump++) {
            struck.add(next);
            Vec3 mid = next.position().add(0, next.getBbHeight() * 0.55, 0);
            SkillFx.play(from, SkillFxPayload.Kind.BOLT, s.color(), mid, 0.3f, 0);
            hit(p, s, next, dmg, true);
            SkillParticles.send(p.serverLevel(), ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, 10, 0.25, 0.25, 0.25, 0.25);
            dmg *= 0.8f;
            from = next;
            LivingEntity last = next;
            next = around(p, last.position(), 6, x -> canHit(p, x) && !struck.contains(x)).stream()
                    .min(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(last))).orElse(null);
        }
        sound(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 1.7f);
        sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.3f, 1.8f);
        return true;
    }

    /** A whirlwind where the player is looking: drags creatures in and cuts at them for four seconds. */
    public static boolean cyclone(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = ground(level, aimPoint(p, 20));
        int ticks = 80;
        float dmg = 1.5f * mult(p, l) + statBonus(p, Stat.INT, 0.05f);
        sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 0.6f);
        SkillFx.play(p, SkillFxPayload.Kind.VORTEX, s.color(), center, 4.5f, ticks);
        for (int t = 0; t < ticks; t += 10) {
            ServerScheduler.schedule(t, () -> {
                if (p.level() != level) return;
                for (LivingEntity e : around(p, center, 4.5, x -> canHit(p, x))) {
                    hit(p, s, e, dmg, true);
                    Vec3 in = center.subtract(e.position()).multiply(1, 0, 1);
                    if (in.lengthSqr() > 0.5) {
                        in = in.normalize();
                        e.setDeltaMovement(in.x * 0.35 - in.z * 0.15, 0.12, in.z * 0.35 + in.x * 0.15);
                        e.hurtMarked = true;
                    }
                }
                level.playSound(null, center.x, center.y, center.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.25f, 1.6f);
            });
        }
        return true;
    }

    /** Slams the shield arm into the creature in front: stunned and thrown back. */
    public static boolean shieldBash(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 5, x -> canHit(p, x));
        if (target == null) return false;
        Vec3 look = flat(p.getLookAngle(), p);
        hit(p, s, target, 5 * mult(p, l) + statBonus(p, Stat.VIT, 0.15f), false);
        target.knockback(1.0, -look.x, -look.z);
        SkillCombat.stun(target, Math.round(bashSeconds(l) * 20));
        Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
        SkillParticles.send(p.serverLevel(), ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.5);
        sound(p, SoundEvents.SHIELD_BLOCK, 1f, 0.7f);
        sound(p, SoundEvents.ANVIL_LAND, 0.4f, 1.2f);
        SkillFx.play(p, SkillFxPayload.Kind.SPLASH, s.color(), at, 1.6f, 1);
        return true;
    }

    /** Spikes of rock erupt in a line, throwing creatures into the air. */
    public static boolean stoneSpikes(ServerPlayer p, Skill s, int l) {
        Vec3 look = flat(p.getLookAngle(), p);
        float dmg = 6 * mult(p, l) + statBonus(p, Stat.VIT, 0.15f);
        for (LivingEntity e : inLine(p, look, 8, 1.3)) {
            hit(p, s, e, dmg, false);
            e.setDeltaMovement(e.getDeltaMovement().x * 0.3, 0.75, e.getDeltaMovement().z * 0.3);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        }
        sound(p, SoundEvents.DRIPSTONE_BLOCK_BREAK, 1f, 0.6f);
        sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.3f);
        SkillFx.play(p, SkillFxPayload.Kind.WAVE, s.color(), p.position().add(look.scale(8)), 1.3f, 1);
        return true;
    }

    /** A lance of sunlight from the sky: burns foes where it lands and mends allies standing in it. */
    public static boolean sunlance(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        LivingEntity target = aim(p, 20, x -> canHit(p, x));
        Vec3 center = ground(level, target != null ? target.position() : aimPoint(p, 20));
        sound(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.8f);
        SkillFx.play(p, SkillFxPayload.Kind.PILLAR, s.color(), center, 3, 8);
        ServerScheduler.schedule(8, () -> {
            if (p.level() != level) return;
            float dmg = 9 * mult(p, l) + statBonus(p, Stat.VIT, 0.2f);
            for (LivingEntity e : around(p, center, 3, x -> canHit(p, x))) hit(p, s, e, undead(e) ? dmg * 1.3f : dmg, true);
            for (Player ally : level.getEntitiesOfClass(Player.class, new AABB(center, center).inflate(3), Player::isAlive)) ally.heal(healing(p, 3));
            SkillParticles.send(level, ParticleTypes.END_ROD, center.x, center.y + 1, center.z, 40, 1.2, 1.5, 1.2, 0.1);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.7f);
            level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.4f, 1.6f);
        });
        return true;
    }

    /** The healer's strike: a beam of light through everything in its path, harder on the undead. */
    public static boolean radiantLance(ServerPlayer p, Skill s, int l) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = aimPoint(p, 24);
        float dmg = 5 * mult(p, l) + statBonus(p, Stat.INT, 0.2f);
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1), x -> canHit(p, x))) {
            Optional<Vec3> hitAt = e.getBoundingBox().inflate(0.4).clip(eye, end);
            if (hitAt.isEmpty()) continue;
            hit(p, s, e, undead(e) ? dmg * 1.5f : dmg, true);
            SkillParticles.send(p.serverLevel(), ParticleTypes.END_ROD, hitAt.get().x, hitAt.get().y, hitAt.get().z, 8, 0.1, 0.1, 0.1, 0.08);
        }
        sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.9f);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 0.5f, 2f);
        SkillFx.play(p, SkillFxPayload.Kind.SHOT, s.color(), end, 0.4f, 0);
        return true;
    }

    /** A wave of healing water: mends allies over time, soaks and slows foes. */
    public static boolean tidewell(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(tidewellSeconds(l) * 20);
        for (Player ally : allies(p, 7)) {
            ally.heal(healing(p, 2));
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, 1));
            ally.clearFire();
            SkillFx.play(ally, SkillFxPayload.Kind.HEAL, s.color(), ally.position(), 1, 0);
        }
        float dmg = 2 * mult(p, l) + statBonus(p, Stat.INT, 0.05f);
        for (LivingEntity e : around(p, p.position(), 7, x -> isFoe(p, x))) {
            hit(p, s, e, dmg, true);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
        }
        sound(p, SoundEvents.BUCKET_EMPTY, 1f, 0.8f);
        sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.2f);
        SkillFx.play(p, SkillFxPayload.Kind.PULSE, s.color(), p.position(), 7, 0);
        return true;
    }

    /** Brambles grow where the player is looking: creatures inside are held and poisoned. */
    public static boolean thornbind(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = ground(level, aimPoint(p, 20));
        int ticks = 100;
        float dmg = 1.5f * mult(p, l) + statBonus(p, Stat.INT, 0.05f);
        sound(p, SoundEvents.SWEET_BERRY_BUSH_PLACE, 1f, 0.6f);
        sound(p, SoundEvents.BIG_DRIPLEAF_TILT_UP, 1f, 0.5f);
        SkillFx.play(p, SkillFxPayload.Kind.ZONE, s.color(), center, 4, ticks);
        SkillFx.play(p, SkillFxPayload.Kind.BIND, s.color(), center, 4, ticks);
        for (int t = 0; t < ticks; t += 20) {
            ServerScheduler.schedule(t, () -> {
                if (p.level() != level) return;
                for (LivingEntity e : around(p, center, 4, x -> canHit(p, x))) {
                    hit(p, s, e, dmg, true);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 4));
                    Statuses.apply(e, DamageElement.POISON, 8, p);
                }
            });
        }
        return true;
    }

    /** An arrow that bursts into flame where it lands. */
    public static boolean emberArrow(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 32, x -> canHit(p, x));
        Vec3 at = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : aimPoint(p, 32);
        float dmg = 6 * mult(p, l) + statBonus(p, Stat.AGI, 0.15f);
        for (LivingEntity e : around(p, at, 3, x -> canHit(p, x))) {
            hit(p, s, e, dmg, false);
            Statuses.apply(e, DamageElement.FIRE, 20 + 3 * l, p);
            e.igniteForSeconds(3);
        }
        SkillParticles.send(p.serverLevel(), ParticleTypes.FLAME, at.x, at.y, at.z, 50, 0.9, 0.6, 0.9, 0.08);
        SkillParticles.send(p.serverLevel(), ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.9f);
        p.level().playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.3f);
        SkillFx.play(p, SkillFxPayload.Kind.SHOT, s.color(), at, 0.4f, 0);
        SkillFx.play(p, SkillFxPayload.Kind.SPLASH, s.color(), at, 3, 1);
        return true;
    }

    /** Looses a spread at close range and leaps backwards out of reach. */
    public static boolean vaultShot(ServerPlayer p, Skill s, int l) {
        Vec3 look = flat(p.getLookAngle(), p);
        Vec3 start = p.position();
        float dmg = 4.5f * mult(p, l) + statBonus(p, Stat.AGI, 0.15f);
        for (LivingEntity e : inCone(p, look, 6, 0.5)) {
            hit(p, s, e, dmg, false);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        }
        p.setDeltaMovement(-look.x * 1.15, 0.5, -look.z * 1.15);
        p.hurtMarked = true;
        p.fallDistance = 0;
        sound(p, SoundEvents.ARROW_SHOOT, 1f, 1.3f);
        sound(p, SoundEvents.BREEZE_JUMP, 0.8f, 1.4f);
        SkillFx.play(p, SkillFxPayload.Kind.FAN, s.color(), start.add(look.scale(6)), 6, 0);
        SkillFx.play(p, SkillFxPayload.Kind.DASH, s.color(), start, 1, 10);
        return true;
    }

    /** Arrows that call lightning: bolts strike creatures around the spot, one after another. */
    public static boolean thunderVolley(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        LivingEntity target = aim(p, 30, x -> canHit(p, x));
        Vec3 center = ground(level, target != null ? target.position() : aimPoint(p, 30));
        float dmg = 5 * mult(p, l) + statBonus(p, Stat.AGI, 0.12f);
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.6f);
        SkillFx.play(p, SkillFxPayload.Kind.SHOT, s.color(), center.add(0, 12, 0), 0.4f, 0);
        for (int i = 0; i < volleyBolts(l); i++) {
            ServerScheduler.schedule(6 + i * 4, () -> {
                if (p.level() != level) return;
                List<LivingEntity> foes = around(p, center, 5, x -> canHit(p, x));
                Vec3 at;
                if (foes.isEmpty()) {
                    at = ground(level, center.add((level.random.nextDouble() - 0.5) * 8, 0, (level.random.nextDouble() - 0.5) * 8));
                } else {
                    LivingEntity e = foes.get(level.random.nextInt(foes.size()));
                    at = e.position();
                    hit(p, s, e, dmg, true);
                }
                SkillFx.play(p, SkillFxPayload.Kind.PILLAR, s.color(), at, 1.2f, 0);
                SkillParticles.send(level, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1, at.z, 20, 0.3, 0.8, 0.3, 0.3);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6f, 1.5f);
            });
        }
        return true;
    }

    // ================================================================ helpers

    /** Hostile creatures, or anything already fighting the player. Used by area skills and buffs/debuffs. */
    static boolean isFoe(ServerPlayer p, LivingEntity e) {
        if (!canHit(p, e)) return false;
        return e instanceof Enemy || (e instanceof Mob m && m.getTarget() == p);
    }

    /** Anything a strike may land on: not the caster, other players, villagers, escorts, golems, pets or stands. */
    public static boolean canHit(ServerPlayer p, LivingEntity e) {
        if (e == p || !e.isAlive() || e.isInvulnerable() || e instanceof Player || e instanceof ArmorStand) return false;
        if (e instanceof AbstractVillager || e instanceof TravelerEntity || e instanceof AbstractGolem) return false;
        if (e instanceof TamableAnimal t && t.isTame()) return false;
        // a ruler of light and its heralds, unless the hunter stands against them
        if (com.beyondsolo.ruler.Rulers.spares(p.getUUID(), e)) return false;
        return true;
    }

    /** The caster and the players around them. */
    private static List<Player> allies(ServerPlayer p, double radius) {
        List<Player> list = new java.util.ArrayList<>(p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(radius), Player::isAlive));
        if (!list.contains(p)) list.add(p);
        return list;
    }

    private static List<LivingEntity> around(ServerPlayer p, Vec3 at, double radius, Predicate<LivingEntity> filter) {
        return p.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> filter.test(e) && e.position().distanceTo(at) <= radius + e.getBbWidth() / 2);
    }

    /**
     * A skill's hit: the hunter's power ({@link com.beyondsolo.system.Balance#power}: level and the stats their class
     * leans on), the combo chain and the passives scale it, the skill's element is left on the creature or reacts
     * with the one already there ({@link SkillCombat#strike}), then the damage lands.
     */
    static void hit(ServerPlayer p, Skill s, LivingEntity e, float damage, boolean magic) {
        damage *= (float) com.beyondsolo.system.Balance.power(p);
        damage *= SkillCombat.comboMultiplier(p) * com.beyondsolo.rune.Runes.damageMultiplier(p, s);
        int steady = SkillAPI.level(p, Skills.STEADY_AIM);
        if (steady > 0 && e.distanceTo(p) > STEADY_RANGE) damage *= 1 + 0.03f * steady;
        damage = SkillCombat.strike(p, e, s.element(), damage);
        e.invulnerableTime = 0;
        e.hurt(magic ? p.damageSources().indirectMagic(p, p) : p.damageSources().playerAttack(p), damage);
    }

    /** Creatures in a strip in front of the player: {@code length} long, {@code halfWidth} to each side. */
    private static List<LivingEntity> inLine(ServerPlayer p, Vec3 look, double length, double halfWidth) {
        Vec3 from = p.position();
        return around(p, from.add(look.scale(length / 2)), length / 2 + 1, e -> {
            if (!canHit(p, e)) return false;
            Vec3 to = e.position().subtract(from);
            double along = to.x * look.x + to.z * look.z;
            if (along < -0.3 || along > length + 0.5 || Math.abs(to.y) > 3) return false;
            double side = Math.abs(to.x * -look.z + to.z * look.x);
            return side <= halfWidth + e.getBbWidth() / 2;
        });
    }

    /** Creatures in a cone in front of the player ({@code minDot} is the cosine of half its angle). */
    private static List<LivingEntity> inCone(ServerPlayer p, Vec3 look, double range, double minDot) {
        return around(p, p.position(), range, e -> {
            if (!canHit(p, e)) return false;
            Vec3 to = e.position().subtract(p.position());
            Vec3 toFlat = new Vec3(to.x, 0, to.z);
            return Math.abs(to.y) <= 3 && (toFlat.lengthSqr() < 0.6 || toFlat.normalize().dot(look) >= minDot);
        });
    }

    /** Healing done by a skill, raised by Benediction and by what the healer's subclass adds to it. */
    private static float healing(ServerPlayer p, float amount) {
        return HealerPath.healing(p, amount);
    }

    private static boolean undead(LivingEntity e) {
        return e.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD);
    }

    /** The creature the player is looking at: the closest one within a narrow cone, in plain sight. */
    static @Nullable LivingEntity aim(ServerPlayer p, double range, Predicate<LivingEntity> filter) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), filter)) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.1) continue;
            // looking at its feet, middle or head all count
            double dot = -1;
            for (double part : new double[]{0.15, 0.5, 0.9}) {
                dot = Math.max(dot, e.position().add(0, e.getBbHeight() * part, 0).subtract(eye).normalize().dot(look));
            }
            // the cone widens a little for big creatures and close ones
            double allowed = Math.cos(Math.toRadians(6 + Math.toDegrees(Math.atan2(e.getBbWidth() * 0.6, dist))));
            if (dot < allowed || !p.hasLineOfSight(e)) continue;
            double score = dist * (2 - dot);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    /** Where the player's line of sight ends: the first block, or the full range. */
    static Vec3 aimPoint(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    /** The ground under a point (up to 16 blocks down). */
    private static Vec3 ground(ServerLevel level, Vec3 at) {
        var hit = level.clip(new ClipContext(at.add(0, 1.5, 0), at.add(0, -16, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
    }

    private static Vec3 flat(Vec3 v, Entity e) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, e.getYRot()) : f.normalize();
    }

    private static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private SkillActions() {}
}
