package com.beyondsolo.skill;

import com.beyondsolo.combat.Buildups;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import static com.beyondsolo.skill.SkillActions.aim;
import static com.beyondsolo.skill.SkillActions.aimPoint;
import static com.beyondsolo.skill.SkillActions.canHit;
import static com.beyondsolo.skill.SkillActions.isFoe;

/**
 * What every subclass skill does, and the numbers its description shows. Same rules as {@link SkillActions}: power
 * grows 10% per skill level and 12% per rank tier, stats add on top at cast time. Rare subclass skills hit several
 * times harder than the others.
 */
public final class SubclassActions {
    // ---- buff keys (SkillData buffs)
    public static final String BUFF_FRENZY = "blood_frenzy";
    public static final String BUFF_FORTRESS = "fortress_stance";
    public static final String BUFF_MANA_BARRIER = "mana_barrier";
    public static final String BUFF_RETRIBUTION = "retribution";
    public static final String BUFF_COLOSSUS = "colossus_form";
    public static final String BUFF_ECLIPSE = "total_eclipse";
    /** Tag on summoned wolves and storm arrows. */
    public static final String SUMMON_TAG = "beyondsolo_summon";
    public static final String STORM_ARROW_TAG = "beyondsolo_storm_arrow";
    static final String SUMMON_EXPIRES = "beyondsolo_expires";

    /** Death Sentence marks: creature -> {end game time, bonus percent, caster entity id}. Server thread only. */
    static final Map<LivingEntity, long[]> SENTENCES = new WeakHashMap<>();
    /** Retribution: player -> damage stored so far. */
    static final Map<ServerPlayer, float[]> STORED = new WeakHashMap<>();

    // ================================================================ numbers (shared with descriptions)

    static float m(int l, int t) { return SkillActions.mult(l, t); }

    public static float frenzySeconds(int l) { return 8 + l / 2f; }
    public static float frenzyLifesteal(int l) { return 8 + l; }
    public static float bannerSeconds(int l) { return 10 + l / 2f; }
    public static float cutDamage(int l, int t) { return 3.2f * m(l, t); }
    public static float chargeDamage(int l, int t) { return 8 * m(l, t); }
    public static float chargeDistance(int l) { return 8 + l / 2f; }
    public static float cataclysmDamage(int l, int t) { return 26 * m(l, t); }
    public static float faultDamage(int l, int t) { return 14 * m(l, t); }

    public static float executeDamage(int l, int t) { return 12 * m(l, t); }
    public static float miasmaSeconds(int l) { return 6 + l / 2f; }
    public static float miasmaDamage(int l, int t) { return 1.5f * m(l, t); }
    public static int flurryTargets(int l) { return 3 + l / 4; }
    public static float flurryDamage(int l, int t) { return 7 * m(l, t); }
    public static float sentenceBonus(int l) { return 20 + 3 * l; }
    public static float eclipseDamage(int l, int t) { return 6 * m(l, t); }
    public static float voidRendDamage(int l, int t) { return 16 * m(l, t); }

    public static float pillarDamage(int l, int t) { return 11 * m(l, t); }
    public static int chainJumps(int l) { return 3 + l / 3; }
    public static float chainDamage(int l, int t) { return 9 * m(l, t); }
    public static float tideDamage(int l, int t) { return 8 * m(l, t); }
    public static float harvestDamage(int l, int t) { return 7 * m(l, t); }
    public static float harvestHeal(int l) { return 1.5f + 0.15f * l; }
    public static int astralMeteors(int l) { return 12 + l / 2; }
    public static float astralDamage(int l, int t) { return 14 * m(l, t); }
    public static float singularityDamage(int l, int t) { return 30 * m(l, t); }

    public static float judgmentDamage(int l, int t) { return 10 * m(l, t); }
    public static float fortressSeconds(int l) { return 6 + l / 2f; }
    public static float barrierHearts(int l) { return 4 + 2 * (l / 3); }
    public static float retributionBonus(int l, int t) { return 5 * m(l, t); }
    public static float colossusSeconds(int l) { return 15 + l; }
    public static float domainDamage(int l, int t) { return 18 * m(l, t); }

    public static float radiantAmount(int l, int t) { return 6 * m(l, t); }
    public static float bloomSeconds(int l) { return 8 + l / 2f; }
    public static float bloomHeal(int l) { return 1 + 0.2f * l; }
    public static float tideHeal(int l, int t) { return 4 * m(l, t); }
    public static float siphonDamage(int l, int t) { return 9 * m(l, t); }
    public static float dawnDamage(int l, int t) { return 20 * m(l, t); }
    public static int dawnPillars(int l) { return 10 + l / 2; }
    public static float dawnPillarDamage(int l, int t) { return 14 * m(l, t); }

    public static float deadeyeDamage(int l, int t) { return 16 * m(l, t); }
    public static int packSize(int l) { return 2 + (l >= 5 ? 1 : 0) + (l >= 10 ? 1 : 0); }
    public static float snareDamage(int l, int t) { return 1.5f * m(l, t); }
    public static float volleyDamage(int l, int t) { return 6 * m(l, t); }
    public static float heavensDamage(int l, int t) { return 34 * m(l, t); }
    public static int stormArrows(int l) { return 42 + 4 * l; }

    private static float m(ServerPlayer p, int l) {
        return SkillActions.mult(l, SystemAPI.get(p).rank().tier());
    }

    private static int tier(ServerPlayer p) {
        return SystemAPI.get(p).rank().tier();
    }

    private static float stat(ServerPlayer p, Stat stat, float perPoint) {
        return Math.max(0, SystemAPI.get(p).stat(stat) - Stat.BASE) * perPoint;
    }

    // ================================================================ warrior

    public static boolean bloodFrenzy(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(frenzySeconds(l) * 20);
        // the rage costs blood
        if (!p.isCreative()) p.setHealth(Math.max(1, p.getHealth() - p.getHealth() * 0.1f));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, l >= 6 ? 1 : 0));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
        SkillAPI.setBuff(p, BUFF_FRENZY, ticks);
        sound(p, SoundEvents.RAVAGER_ROAR, 0.7f, 1.6f);
        sound(p, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.6f);
        SkillFx.play(p, SkillFxPayload.Kind.ROAR, s.color(), p.position(), 5, 0);
        SkillFx.play(p, SkillFxPayload.Kind.AURA, s.color(), p.position(), 1.2f, ticks);
        return true;
    }

    public static boolean bannerOfConquest(ServerPlayer p, Skill s, int l) {
        Vec3 center = p.position();
        int ticks = Math.round(bannerSeconds(l) * 20);
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.RAID_HORN.value(), 0.6f, 1.4f);
        sound(p, SoundEvents.ARMOR_EQUIP_IRON.value(), 1f, 0.8f);
        SkillFx.play(p, SkillFxPayload.Kind.ZONE, s.color(), center, 7, ticks);
        SkillFx.play(p, SkillFxPayload.Kind.ROAR, s.color(), center, 7, 0);
        for (int t = 0; t < ticks; t += 20) {
            ServerScheduler.schedule(t, () -> {
                if (p.level() != level) return;
                for (Player ally : level.getEntitiesOfClass(Player.class, new AABB(center, center).inflate(7), x -> x.position().distanceTo(center) <= 7)) {
                    ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 30, 0, true, false, true));
                    ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, true, false, true));
                }
                for (LivingEntity e : around(p, center, 7, x -> isFoe(p, x))) {
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0));
                }
            });
        }
        return true;
    }

    public static boolean thousandCuts(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < 5; i++) {
            final int k = i;
            ServerScheduler.schedule(i * 4, () -> {
                if (!p.isAlive() || p.level() != level) return;
                Vec3 look = flat(p.getLookAngle(), p);
                float dmg = cutDamage(l, tier(p)) + stat(p, Stat.STR, 0.06f) + stat(p, Stat.AGI, 0.06f);
                for (LivingEntity e : around(p, p.position(), 4.5, x -> canHit(p, x))) {
                    Vec3 to = e.position().subtract(p.position()).multiply(1, 0, 1);
                    if (to.lengthSqr() > 1.0e-3 && to.normalize().dot(look) < 0.3) continue;
                    hit(p, e, dmg, false);
                }
                sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1.1f + k * 0.12f);
                SkillFx.play(p, SkillFxPayload.Kind.SLASH, s.color(), p.position(), 3.6f + k * 0.2f, 0);
            });
        }
        return true;
    }

    public static boolean unstoppableCharge(ServerPlayer p, Skill s, int l) {
        Vec3 dir = flat(p.getLookAngle(), p);
        Vec3 start = p.position();
        float speed = chargeDistance(l) * 0.16f;
        Set<Integer> struck = new HashSet<>();
        sound(p, SoundEvents.RAVAGER_STEP, 1f, 0.7f);
        sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 0.7f, 0.6f);
        SkillFx.play(p, SkillFxPayload.Kind.DASH, s.color(), start, 1.3f, 12);
        for (int t = 0; t < 12; t++) {
            final int k = t;
            ServerScheduler.schedule(t, () -> {
                if (!p.isAlive()) return;
                p.setDeltaMovement(dir.x * speed, p.getDeltaMovement().y, dir.z * speed);
                p.hurtMarked = true;
                p.fallDistance = 0;
                for (LivingEntity e : around(p, p.position(), 2.2, x -> canHit(p, x) && !struck.contains(x.getId()))) {
                    struck.add(e.getId());
                    hit(p, e, chargeDamage(l, tier(p)) + stat(p, Stat.STR, 0.15f) + stat(p, Stat.VIT, 0.1f), false);
                    e.setDeltaMovement(dir.x * 0.8, 0.7, dir.z * 0.8);
                    e.hurtMarked = true;
                }
                if (k == 11) {
                    sound(p, SoundEvents.ANVIL_LAND, 0.5f, 0.6f);
                    SkillFx.play(p, SkillFxPayload.Kind.SLAM, s.color(), p.position(), 3, 0);
                }
            });
        }
        return true;
    }

    public static boolean cataclysmFist(ServerPlayer p, Skill s, int l) {
        Vec3 look = flat(p.getLookAngle(), p);
        p.setDeltaMovement(look.x * 0.3, 1.15, look.z * 0.3);
        p.hurtMarked = true;
        sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 1f, 0.8f);
        SkillFx.play(p, SkillFxPayload.Kind.AURA, s.color(), p.position(), 1.6f, 30);
        crashWhenLanded(p, s, l, 0);
        return true;
    }

    private static void crashWhenLanded(ServerPlayer p, Skill s, int l, int ticks) {
        ServerScheduler.schedule(1, () -> {
            if (!p.isAlive() || p.hasDisconnected()) return;
            p.fallDistance = 0;
            if (ticks == 10) {
                p.setDeltaMovement(p.getDeltaMovement().x * 0.3, -2.4, p.getDeltaMovement().z * 0.3);
                p.hurtMarked = true;
            }
            if ((ticks > 4 && p.onGround()) || ticks > 60) {
                Vec3 at = p.position();
                ServerLevel level = p.serverLevel();
                float dmg = cataclysmDamage(l, tier(p)) + stat(p, Stat.STR, 0.35f);
                for (LivingEntity e : around(p, at, 9, x -> canHit(p, x))) {
                    hit(p, e, dmg * (1f - 0.4f * (float) (e.distanceTo(p) / 9f)), false);
                    Vec3 away = e.position().subtract(at).multiply(1, 0, 1).normalize();
                    e.setDeltaMovement(away.x * 0.9, 1.0, away.z * 0.9);
                    e.hurtMarked = true;
                    stun(e, 60);
                }
                sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.5f);
                sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 0.6f);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
                SkillFx.play(p, SkillFxPayload.Kind.SLAM, s.color(), at, 9, 0);
                SkillFx.play(p, SkillFxPayload.Kind.ROAR, s.color(), at, 9, 0);
                // aftershocks
                for (int k = 1; k <= 2; k++) {
                    final int ring = k;
                    ServerScheduler.schedule(6 * k, () -> {
                        if (p.level() != level) return;
                        fxAt(level, SkillFxPayload.Kind.SLAM, s.color(), at, 9 + 3 * ring, 0);
                        for (LivingEntity e : around(p, at, 9 + 3 * ring, x -> canHit(p, x))) hit(p, e, dmg * 0.25f, false);
                        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 0.7f);
                    });
                }
                return;
            }
            crashWhenLanded(p, s, l, ticks + 1);
        });
    }

    public static boolean faultLine(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 dir = flat(p.getLookAngle(), p);
        Vec3 start = p.position();
        float dmg = faultDamage(l, tier(p)) + stat(p, Stat.STR, 0.25f);
        Set<Integer> struck = new HashSet<>();
        sound(p, SoundEvents.WARDEN_DIG, 1f, 0.8f);
        for (int i = 1; i <= 9; i++) {
            Vec3 at = ground(level, start.add(dir.scale(i * 2.0)));
            ServerScheduler.schedule(i * 2, () -> {
                if (p.level() != level) return;
                fxAt(level, SkillFxPayload.Kind.SLAM, s.color(), at, 2.6f, 0);
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
                for (LivingEntity e : around(p, at, 2.6, x -> canHit(p, x) && !struck.contains(x.getId()))) {
                    struck.add(e.getId());
                    hit(p, e, dmg, false);
                    e.setDeltaMovement(e.getDeltaMovement().x, 1.1, e.getDeltaMovement().z);
                    e.hurtMarked = true;
                }
            });
        }
        return true;
    }

    // ================================================================ assassin

    public static boolean umbralExecution(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 16, x -> canHit(p, x));
        if (target == null) return false;
        Vec3 from = p.position();
        Vec3 back = flat(target.getLookAngle(), target).scale(-(target.getBbWidth() / 2 + 0.9));
        Vec3 dest = target.position().add(back);
        if (!p.level().noCollision(p, p.getBoundingBox().move(dest.subtract(from)))) dest = target.position().add(from.subtract(target.position()).multiply(1, 0, 1).normalize());
        teleport(p, dest, target);
        float dmg = executeDamage(l, tier(p)) + stat(p, Stat.AGI, 0.3f);
        boolean execute = target.getHealth() < target.getMaxHealth() * 0.3f;
        hit(p, target, execute ? dmg * 2.5f : dmg, false);
        sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.7f);
        sound(p, execute ? SoundEvents.WITHER_BREAK_BLOCK : SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 1.4f);
        SkillFx.play(p, SkillFxPayload.Kind.BLINK, s.color(), from, 1, 0);
        SkillFx.play(p, SkillFxPayload.Kind.SLASH, s.color(), p.position(), execute ? 4 : 3, 0);
        return true;
    }

    public static boolean miasmaCloud(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = ground(level, aimPoint(p, 20));
        int ticks = Math.round(miasmaSeconds(l) * 20);
        sound(p, SoundEvents.BREWING_STAND_BREW, 1f, 0.6f);
        sound(p, SoundEvents.SPLASH_POTION_BREAK, 1f, 0.7f);
        SkillFx.play(p, SkillFxPayload.Kind.ZONE, s.color(), center, 4.5f, ticks);
        for (int t = 0; t < ticks; t += 20) {
            ServerScheduler.schedule(t, () -> {
                if (p.level() != level) return;
                level.sendParticles(ParticleTypes.SNEEZE, center.x, center.y + 0.6, center.z, 20, 2, 0.5, 2, 0.01);
                for (LivingEntity e : around(p, center, 4.5, x -> canHit(p, x))) {
                    hit(p, e, miasmaDamage(l, tier(p)) + stat(p, Stat.INT, 0.05f), true);
                    Statuses.apply(e, DamageElement.POISON, 18 + 2 * l, p);
                }
            });
        }
        return true;
    }

    public static boolean mirageFlurry(ServerPlayer p, Skill s, int l) {
        List<LivingEntity> targets = new ArrayList<>(around(p, p.position(), 10, x -> canHit(p, x) && p.hasLineOfSight(x)));
        if (targets.isEmpty()) return false;
        targets.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        int n = Math.min(flurryTargets(l), targets.size());
        ServerLevel level = p.serverLevel();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, n * 4 + 10, 2, false, false, false));
        for (int i = 0; i < n; i++) {
            LivingEntity target = targets.get(i);
            ServerScheduler.schedule(i * 4, () -> {
                if (!p.isAlive() || p.level() != level || !target.isAlive()) return;
                Vec3 from = p.position();
                Vec3 side = flat(target.position().subtract(from), p).scale(target.getBbWidth() / 2 + 0.8);
                teleport(p, target.position().add(side), target);
                hit(p, target, flurryDamage(l, tier(p)) + stat(p, Stat.AGI, 0.2f), false);
                sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7f, 1.5f);
                SkillFx.play(p, SkillFxPayload.Kind.DASH, s.color(), from, 1, 8);
                SkillFx.play(p, SkillFxPayload.Kind.SLASH, s.color(), p.position(), 2.8f, 0);
            });
        }
        return true;
    }

    public static boolean deathSentence(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 28, x -> canHit(p, x));
        if (target == null) return false;
        int ticks = 200;
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
        SENTENCES.put(target, new long[]{p.level().getGameTime() + ticks, Math.round(sentenceBonus(l)), p.getId()});
        sound(p, SoundEvents.BELL_BLOCK, 1f, 0.6f);
        sound(p, SoundEvents.WITHER_SKELETON_AMBIENT, 0.5f, 1.4f);
        SkillFx.play(target, SkillFxPayload.Kind.MARK, s.color(), target.position(), target.getBbHeight(), ticks);
        return true;
    }

    public static boolean totalEclipse(ServerPlayer p, Skill s, int l) {
        Vec3 center = p.position();
        ServerLevel level = p.serverLevel();
        int ticks = 160;
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
        SkillAPI.setBuff(p, BUFF_ECLIPSE, ticks);
        sound(p, SoundEvents.WARDEN_EMERGE, 0.8f, 1.3f);
        sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 0.5f);
        SkillFx.play(p, SkillFxPayload.Kind.ZONE, s.color(), center, 10, ticks);
        SkillFx.play(p, SkillFxPayload.Kind.VEIL, s.color(), center, 2.5f, ticks);
        for (int t = 0; t < ticks; t += 20) {
            ServerScheduler.schedule(t, () -> {
                if (p.level() != level) return;
                for (LivingEntity e : around(p, center, 10, x -> canHit(p, x))) {
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                    if (e instanceof Mob m && m.getTarget() == p) m.setTarget(null);
                    hit(p, e, eclipseDamage(l, tier(p)) + stat(p, Stat.AGI, 0.12f), true);
                    SkillFx.play(e, SkillFxPayload.Kind.SLASH, s.color(), e.position(), 1.6f, 0);
                }
            });
        }
        return true;
    }

    public static boolean voidRend(ServerPlayer p, Skill s, int l) {
        List<LivingEntity> targets = new ArrayList<>(around(p, p.position(), 14, x -> canHit(p, x)));
        if (targets.isEmpty()) return false;
        targets.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
        int n = Math.min(8, targets.size());
        Vec3 home = p.position();
        float yaw = p.getYRot();
        ServerLevel level = p.serverLevel();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, n * 3 + 10, 4, false, false, false));
        sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 1.6f);
        for (int i = 0; i <= n; i++) {
            final int k = i;
            ServerScheduler.schedule(i * 3, () -> {
                if (!p.isAlive() || p.level() != level) return;
                Vec3 from = p.position();
                if (k == n) {
                    p.teleportTo(level, home.x, home.y, home.z, yaw, p.getXRot());
                    SkillFx.play(p, SkillFxPayload.Kind.BLINK, s.color(), from, 1.4f, 0);
                    SkillFx.play(p, SkillFxPayload.Kind.ROAR, s.color(), home, 6, 0);
                    sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.4f);
                    return;
                }
                LivingEntity target = targets.get(k);
                if (!target.isAlive()) return;
                Vec3 side = flat(target.position().subtract(from), p).scale(target.getBbWidth() / 2 + 0.8);
                teleport(p, target.position().add(side), target);
                hit(p, target, voidRendDamage(l, tier(p)) + stat(p, Stat.AGI, 0.35f), false);
                sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.6f);
                SkillFx.play(p, SkillFxPayload.Kind.BLINK, s.color(), from, 1, 0);
                SkillFx.play(p, SkillFxPayload.Kind.SLASH, s.color(), p.position(), 3.4f, 0);
            });
        }
        return true;
    }

    // ================================================================ mage

    public static boolean infernoPillar(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = ground(level, aimPoint(p, 28));
        float dmg = pillarDamage(l, tier(p)) + stat(p, Stat.INT, 0.25f);
        sound(p, SoundEvents.BLAZE_SHOOT, 1f, 0.5f);
        SkillFx.play(p, SkillFxPayload.Kind.METEOR, s.color(), at, 3, 8);
        ServerScheduler.schedule(8, () -> {
            if (p.level() != level) return;
            fxAt(level, SkillFxPayload.Kind.NOVA, s.color(), at, 3.5f, 0);
            fxAt(level, SkillFxPayload.Kind.HEAL, s.color(), at, 2, 0);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1.5, at.z, 60, 0.6, 1.6, 0.6, 0.05);
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 10, 0.8, 0.2, 0.8, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2f, 0.6f);
            for (LivingEntity e : around(p, at, 3.2, x -> canHit(p, x))) {
                hit(p, e, dmg, true);
                Statuses.apply(e, DamageElement.FIRE, 40 + 5 * l, p);
                e.setDeltaMovement(e.getDeltaMovement().x, 0.9, e.getDeltaMovement().z);
                e.hurtMarked = true;
            }
        });
        return true;
    }

    public static boolean chainLightning(ServerPlayer p, Skill s, int l) {
        LivingEntity first = aim(p, 28, x -> canHit(p, x));
        if (first == null) return false;
        Set<Integer> struck = new HashSet<>();
        float dmg = chainDamage(l, tier(p)) + stat(p, Stat.INT, 0.25f);
        Entity from = p;
        LivingEntity current = first;
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.4f, 1.8f);
        for (int jump = 0; jump < chainJumps(l) && current != null; jump++) {
            struck.add(current.getId());
            Vec3 end = current.position().add(0, current.getBbHeight() * 0.55, 0);
            SkillFx.play(from, SkillFxPayload.Kind.BOLT, s.color(), end, 0.3f, 0);
            hit(p, current, dmg * (float) Math.pow(0.85, jump), true);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 12, 0.25, 0.25, 0.25, 0.25);
            from = current;
            LivingEntity last = current;
            current = null;
            double best = 8 * 8;
            for (LivingEntity e : around(p, last.position(), 8, x -> canHit(p, x) && !struck.contains(x.getId()))) {
                double d = e.distanceToSqr(last);
                if (d < best) {
                    best = d;
                    current = e;
                }
            }
        }
        sound(p, SoundEvents.AMETHYST_BLOCK_HIT, 1f, 1.4f);
        return true;
    }

    public static boolean tidalCrash(ServerPlayer p, Skill s, int l) {
        Vec3 look = flat(p.getLookAngle(), p);
        float dmg = tideDamage(l, tier(p)) + stat(p, Stat.INT, 0.2f);
        for (LivingEntity e : around(p, p.position(), 7, x -> canHit(p, x))) {
            Vec3 to = e.position().subtract(p.position()).multiply(1, 0, 1);
            if (to.lengthSqr() > 1.0e-3 && to.normalize().dot(look) < 0.35) continue;
            hit(p, e, dmg, true);
            e.setDeltaMovement(look.x * 1.4, 0.45, look.z * 1.4);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            e.clearFire();
        }
        p.heal(2 + 0.3f * l);
        sound(p, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1f, 0.8f);
        sound(p, SoundEvents.TRIDENT_RIPTIDE_3.value(), 0.8f, 1f);
        SkillFx.play(p, SkillFxPayload.Kind.SLASH, s.color(), p.position(), 6, 0);
        SkillFx.play(p, SkillFxPayload.Kind.PULSE, s.color(), p.position(), 7, 0);
        return true;
    }

    public static boolean soulHarvest(ServerPlayer p, Skill s, int l) {
        float dmg = harvestDamage(l, tier(p)) + stat(p, Stat.INT, 0.2f);
        int n = 0;
        for (LivingEntity e : around(p, p.position(), 6, x -> canHit(p, x))) {
            hit(p, e, dmg, true);
            SkillFx.play(e, SkillFxPayload.Kind.BOLT, s.color(), p.position().add(0, 1, 0), 0.25f, 0);
            n++;
        }
        if (n > 0) {
            p.heal(harvestHeal(l) * n);
            SystemAPI.restoreMana(p, 3f * n);
        }
        sound(p, SoundEvents.SOUL_ESCAPE.value(), 1f, 0.7f);
        sound(p, SoundEvents.WITHER_AMBIENT, 0.4f, 1.6f);
        SkillFx.play(p, SkillFxPayload.Kind.NOVA, s.color(), p.position(), 6, 0);
        SkillFx.play(p, SkillFxPayload.Kind.HEAL, s.color(), p.position(), 1, 0);
        return true;
    }

    public static boolean astralCataclysm(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = ground(level, aimPoint(p, 32));
        int count = astralMeteors(l);
        sound(p, SoundEvents.EVOKER_PREPARE_SUMMON, 1f, 0.5f);
        sound(p, SoundEvents.BEACON_ACTIVATE, 1f, 0.6f);
        SkillFx.play(p, SkillFxPayload.Kind.ZONE, s.color(), center, 6, 10 + 4 * count + 12);
        for (int i = 0; i < count; i++) {
            double ang = p.getRandom().nextDouble() * Math.PI * 2;
            double rad = i == 0 ? 0 : 1 + p.getRandom().nextDouble() * 4.5;
            Vec3 at = ground(level, center.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad));
            int delay = 4 * i;
            ServerScheduler.schedule(delay, () -> {
                if (p.level() == level) SkillFx.play(p, SkillFxPayload.Kind.METEOR, s.color(), at, 3f, 12);
            });
            ServerScheduler.schedule(delay + 12, () -> {
                if (p.level() != level) return;
                float dmg = astralDamage(l, tier(p)) + stat(p, Stat.INT, 0.3f);
                for (LivingEntity e : around(p, at, 3, x -> canHit(p, x))) hit(p, e, dmg, true);
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.3, at.z, 10, 0.6, 0.2, 0.6, 0.1);
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.3f);
            });
        }
        return true;
    }

    public static boolean singularity(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = aimPoint(p, 24).add(0, 1.2, 0);
        int ticks = 80;
        sound(p, SoundEvents.PORTAL_TRIGGER, 0.7f, 1.6f);
        SkillFx.play(p, SkillFxPayload.Kind.ZONE, s.color(), center.add(0, -1.2, 0), 9, ticks);
        fxAt(level, SkillFxPayload.Kind.DOME, s.color(), center.add(0, -1, 0), 1.4f, ticks);
        for (int t = 0; t <= ticks; t += 2) {
            final int k = t;
            ServerScheduler.schedule(t, () -> {
                if (p.level() != level) return;
                if (k < ticks) {
                    for (LivingEntity e : around(p, center, 9, x -> canHit(p, x))) {
                        Vec3 pull = center.subtract(e.position()).normalize().scale(0.28);
                        e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(pull));
                        e.hurtMarked = true;
                    }
                    if (k % 10 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 30, 2.5, 2.5, 2.5, 0.1);
                    return;
                }
                float dmg = singularityDamage(l, tier(p)) + stat(p, Stat.INT, 0.4f);
                for (LivingEntity e : around(p, center, 5.5, x -> canHit(p, x))) {
                    hit(p, e, dmg, true);
                    Vec3 away = e.position().subtract(center).normalize();
                    e.setDeltaMovement(away.x * 1.2, 0.8, away.z * 1.2);
                    e.hurtMarked = true;
                }
                fxAt(level, SkillFxPayload.Kind.NOVA, s.color(), center.add(0, -1, 0), 6, 0);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
                level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 0.6f);
            });
        }
        return true;
    }

    // ================================================================ guardian

    public static boolean holyJudgment(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 24, x -> canHit(p, x));
        if (target == null) return false;
        float dmg = judgmentDamage(l, tier(p)) + stat(p, Stat.VIT, 0.15f) + stat(p, Stat.INT, 0.1f);
        if (undead(target)) dmg *= 1.6f;
        hit(p, target, dmg, true);
        p.heal(3 + 0.4f * l);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 1f, 1.3f);
        sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.3f, 1.8f);
        SkillFx.play(target, SkillFxPayload.Kind.HEAL, s.color(), target.position(), 1.6f, 0);
        SkillFx.play(p, SkillFxPayload.Kind.BOLT, s.color(), target.position().add(0, target.getBbHeight() * 0.6, 0), 0.4f, 0);
        return true;
    }

    public static boolean fortressStance(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(fortressSeconds(l) * 20);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 2));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1));
        SkillAPI.setBuff(p, BUFF_FORTRESS, ticks);
        sound(p, SoundEvents.ANVIL_PLACE, 0.8f, 0.6f);
        sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1f, 0.6f);
        SkillFx.play(p, SkillFxPayload.Kind.SLAM, s.color(), p.position(), 3, 0);
        SkillFx.play(p, SkillFxPayload.Kind.DOME, s.color(), p.position(), 1.5f, ticks);
        return true;
    }

    public static boolean manaBarrier(ServerPlayer p, Skill s, int l) {
        int ticks = 240;
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, Math.round(barrierHearts(l) / 2) - 1));
        SkillAPI.setBuff(p, BUFF_MANA_BARRIER, ticks);
        sound(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.6f);
        sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.9f);
        SkillFx.play(p, SkillFxPayload.Kind.DOME, s.color(), p.position(), 1.7f, ticks);
        return true;
    }

    public static boolean retribution(ServerPlayer p, Skill s, int l) {
        int ticks = 120;
        STORED.put(p, new float[]{0});
        SkillAPI.setBuff(p, BUFF_RETRIBUTION, ticks);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0));
        sound(p, SoundEvents.BLAZE_AMBIENT, 1f, 0.6f);
        SkillFx.play(p, SkillFxPayload.Kind.AURA, s.color(), p.position(), 1.3f, ticks);
        ServerLevel level = p.serverLevel();
        ServerScheduler.schedule(ticks, () -> {
            if (!p.isAlive() || p.level() != level) return;
            float[] stored = STORED.remove(p);
            float dmg = (stored == null ? 0 : stored[0] * 2) + retributionBonus(l, tier(p));
            for (LivingEntity e : around(p, p.position(), 5, x -> canHit(p, x))) {
                hit(p, e, dmg, true);
                Statuses.apply(e, DamageElement.FIRE, 30 + 3 * l, p);
            }
            sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.9f);
            sound(p, SoundEvents.BLAZE_SHOOT, 1f, 0.5f);
            SkillFx.play(p, SkillFxPayload.Kind.NOVA, s.color(), p.position(), 5, 0);
        });
        return true;
    }

    private static final ResourceLocation COLOSSUS_SCALE = com.beyondsolo.BeyondSolo.id("skill/colossus_form/scale");
    private static final ResourceLocation COLOSSUS_HEALTH = com.beyondsolo.BeyondSolo.id("skill/colossus_form/health");
    private static final ResourceLocation COLOSSUS_ATTACK = com.beyondsolo.BeyondSolo.id("skill/colossus_form/attack");
    private static final ResourceLocation COLOSSUS_KNOCKBACK = com.beyondsolo.BeyondSolo.id("skill/colossus_form/knockback");

    public static boolean colossusForm(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(colossusSeconds(l) * 20);
        setModifier(p, Attributes.SCALE, COLOSSUS_SCALE, 0.8);
        setModifier(p, Attributes.MAX_HEALTH, COLOSSUS_HEALTH, 20 + 2 * l);
        setModifier(p, Attributes.ATTACK_DAMAGE, COLOSSUS_ATTACK, 6 + l * 0.6);
        setModifier(p, Attributes.KNOCKBACK_RESISTANCE, COLOSSUS_KNOCKBACK, 1);
        p.heal(20 + 2 * l);
        SkillAPI.setBuff(p, BUFF_COLOSSUS, ticks);
        sound(p, SoundEvents.WARDEN_EMERGE, 1f, 0.7f);
        sound(p, SoundEvents.ANVIL_LAND, 0.6f, 0.4f);
        SkillFx.play(p, SkillFxPayload.Kind.SLAM, s.color(), p.position(), 6, 0);
        SkillFx.play(p, SkillFxPayload.Kind.AURA, s.color(), p.position(), 2.2f, ticks);
        ServerScheduler.schedule(ticks, () -> endColossus(p));
        return true;
    }

    /** Takes the colossus form's modifiers off (also on login, since they are transient anyway). */
    public static void endColossus(ServerPlayer p) {
        for (var id : new ResourceLocation[]{COLOSSUS_SCALE, COLOSSUS_HEALTH, COLOSSUS_ATTACK, COLOSSUS_KNOCKBACK}) {
            for (var attr : List.of(Attributes.SCALE, Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.KNOCKBACK_RESISTANCE)) {
                AttributeInstance inst = p.getAttribute(attr);
                if (inst != null) inst.removeModifier(id);
            }
        }
        if (p.getHealth() > p.getMaxHealth()) p.setHealth(p.getMaxHealth());
    }

    public static boolean earthenDomain(ServerPlayer p, Skill s, int l) {
        Vec3 at = p.position();
        float dmg = domainDamage(l, tier(p)) + stat(p, Stat.VIT, 0.3f) + stat(p, Stat.STR, 0.2f);
        for (LivingEntity e : around(p, at, 12, x -> canHit(p, x))) {
            hit(p, e, dmg, false);
            stun(e, 60);
            if (e instanceof Mob m) m.setTarget(p);
            e.setDeltaMovement(e.getDeltaMovement().x, 0.6, e.getDeltaMovement().z);
            e.hurtMarked = true;
        }
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1));
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1f, 0.5f);
        sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.5f);
        p.serverLevel().sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
        SkillFx.play(p, SkillFxPayload.Kind.SLAM, s.color(), at, 12, 0);
        SkillFx.play(p, SkillFxPayload.Kind.ROAR, s.color(), at, 12, 0);
        return true;
    }

    // ================================================================ healer

    public static boolean radiantBurst(ServerPlayer p, Skill s, int l) {
        float amount = radiantAmount(l, tier(p)) + stat(p, Stat.INT, 0.15f);
        for (Player ally : allies(p, 7)) {
            ally.heal(amount);
            SkillFx.play(ally, SkillFxPayload.Kind.HEAL, s.color(), ally.position(), 1, 0);
        }
        for (LivingEntity e : around(p, p.position(), 7, x -> isFoe(p, x))) hit(p, e, undead(e) ? amount * 1.6f : amount, true);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 1f, 1.5f);
        sound(p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 1.2f);
        SkillFx.play(p, SkillFxPayload.Kind.NOVA, s.color(), p.position(), 7, 0);
        return true;
    }

    public static boolean verdantBloom(ServerPlayer p, Skill s, int l) {
        Vec3 center = p.position();
        int ticks = Math.round(bloomSeconds(l) * 20);
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.AZALEA_LEAVES_PLACE, 1f, 0.7f);
        sound(p, SoundEvents.BIG_DRIPLEAF_TILT_UP, 1f, 0.8f);
        SkillFx.play(p, SkillFxPayload.Kind.ZONE, s.color(), center, 6, ticks);
        for (int t = 0; t < ticks; t += 20) {
            ServerScheduler.schedule(t, () -> {
                if (p.level() != level) return;
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 0.5, center.z, 12, 3, 0.4, 3, 0);
                for (Player ally : level.getEntitiesOfClass(Player.class, new AABB(center, center).inflate(6), x -> x.position().distanceTo(center) <= 6)) {
                    ally.heal(bloomHeal(l));
                }
                for (LivingEntity e : around(p, center, 6, x -> isFoe(p, x))) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3));
                }
            });
        }
        return true;
    }

    private static final List<Holder<MobEffect>> CLEANSED = List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.WEAKNESS,
            MobEffects.MOVEMENT_SLOWDOWN, MobEffects.BLINDNESS, MobEffects.DARKNESS, MobEffects.HUNGER, MobEffects.CONFUSION,
            MobEffects.DIG_SLOWDOWN);

    public static boolean cleansingTide(ServerPlayer p, Skill s, int l) {
        float heal = tideHeal(l, tier(p)) + stat(p, Stat.INT, 0.1f);
        for (Player ally : allies(p, 7)) {
            cleanse(ally);
            ally.heal(heal);
            ally.clearFire();
            SkillFx.play(ally, SkillFxPayload.Kind.HEAL, s.color(), ally.position(), 1.2f, 0);
        }
        for (LivingEntity e : around(p, p.position(), 7, x -> isFoe(p, x))) {
            Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1).normalize();
            e.setDeltaMovement(away.x * 1.1, 0.4, away.z * 1.1);
            e.hurtMarked = true;
        }
        sound(p, SoundEvents.PLAYER_SPLASH, 1f, 0.9f);
        sound(p, SoundEvents.CONDUIT_ACTIVATE, 0.8f, 1.4f);
        SkillFx.play(p, SkillFxPayload.Kind.PULSE, s.color(), p.position(), 7, 0);
        return true;
    }

    public static boolean bloodSiphon(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 20, x -> canHit(p, x));
        if (target == null) return false;
        float dmg = siphonDamage(l, tier(p)) + stat(p, Stat.INT, 0.2f);
        float before = target.getHealth();
        hit(p, target, dmg, true);
        Buildups.add(target, Buildups.BLOOD, 30 + 3 * l, p);
        p.heal(Math.max(1, (before - target.getHealth()) * 0.5f));
        sound(p, SoundEvents.WARDEN_HEARTBEAT, 1f, 1.3f);
        sound(p, SoundEvents.HONEY_DRINK, 1f, 0.6f);
        SkillFx.play(target, SkillFxPayload.Kind.BOLT, s.color(), p.position().add(0, 1, 0), 0.35f, 0);
        SkillFx.play(p, SkillFxPayload.Kind.HEAL, s.color(), p.position(), 1, 0);
        return true;
    }

    public static boolean miracleOfDawn(ServerPlayer p, Skill s, int l) {
        for (Player ally : allies(p, 16)) {
            cleanse(ally);
            ally.removeEffect(ModEffects.HEAVY_VENOM);
            ally.removeEffect(ModEffects.DEEP_BURN);
            ally.setHealth(ally.getMaxHealth());
            ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 2));
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
            SkillFx.play(ally, SkillFxPayload.Kind.HEAL, s.color(), ally.position(), 2, 0);
        }
        float dmg = dawnDamage(l, tier(p)) + stat(p, Stat.INT, 0.3f);
        for (LivingEntity e : around(p, p.position(), 16, x -> isFoe(p, x) && undead(x))) {
            hit(p, e, dmg, true);
            SkillFx.play(e, SkillFxPayload.Kind.HEAL, s.color(), e.position(), 1.4f, 0);
        }
        sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
        sound(p, SoundEvents.BEACON_ACTIVATE, 1f, 1.5f);
        SkillFx.play(p, SkillFxPayload.Kind.ROAR, s.color(), p.position(), 16, 0);
        SkillFx.play(p, SkillFxPayload.Kind.DOME, s.color(), p.position(), 3, 40);
        return true;
    }

    public static boolean judgmentOfDawn(ServerPlayer p, Skill s, int l) {
        List<LivingEntity> foes = new ArrayList<>(around(p, p.position(), 16, x -> canHit(p, x)));
        if (foes.isEmpty()) return false;
        ServerLevel level = p.serverLevel();
        int n = dawnPillars(l);
        sound(p, SoundEvents.BEACON_ACTIVATE, 1f, 1.8f);
        for (int i = 0; i < n; i++) {
            LivingEntity target = foes.get(i % foes.size());
            ServerScheduler.schedule(3 * i, () -> {
                if (p.level() != level || !target.isAlive()) return;
                float dmg = dawnPillarDamage(l, tier(p)) + stat(p, Stat.INT, 0.2f);
                hit(p, target, undead(target) ? dmg * 1.5f : dmg, true);
                SkillFx.play(target, SkillFxPayload.Kind.HEAL, s.color(), target.position(), 1.6f, 0);
                fxAt(level, SkillFxPayload.Kind.NOVA, s.color(), target.position(), 2, 0);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.35f, 1.9f);
            });
        }
        return true;
    }

    // ================================================================ ranger

    public static boolean deadeyeShot(ServerPlayer p, Skill s, int l) {
        float dmg = deadeyeDamage(l, tier(p)) + stat(p, Stat.AGI, 0.3f) + stat(p, Stat.SENSE, 0.2f);
        Vec3 end = line(p, 48, 0.4, dmg, 0.6, SkillFxPayload.Kind.SHOT, s.color());
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.5f);
        sound(p, SoundEvents.TRIDENT_THROW.value(), 1f, 1.3f);
        SkillFx.play(p, SkillFxPayload.Kind.SHOT, s.color(), end, 0.6f, 0);
        return true;
    }

    public static boolean callOfThePack(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        int n = packSize(l);
        long expires = level.getGameTime() + 600;
        for (int i = 0; i < n; i++) {
            Wolf wolf = EntityType.WOLF.create(level);
            if (wolf == null) continue;
            double ang = Math.PI * 2 * i / n;
            wolf.moveTo(p.getX() + Math.cos(ang) * 1.6, p.getY(), p.getZ() + Math.sin(ang) * 1.6, p.getYRot(), 0);
            wolf.tame(p);
            wolf.getAttribute(Attributes.MAX_HEALTH).setBaseValue(30 + 3 * l);
            wolf.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4 + 0.6 * l * (1 + 0.12 * tier(p)));
            wolf.setHealth(wolf.getMaxHealth());
            wolf.addTag(SUMMON_TAG);
            wolf.getPersistentData().putLong(SUMMON_EXPIRES, expires);
            level.addFreshEntity(wolf);
            SkillFx.play(wolf, SkillFxPayload.Kind.HEAL, s.color(), wolf.position(), 1, 0);
        }
        sound(p, SoundEvents.WOLF_HOWL, 1f, 1f);
        SkillFx.play(p, SkillFxPayload.Kind.PULSE, s.color(), p.position(), 5, 0);
        return true;
    }

    public static boolean snareField(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = ground(level, aimPoint(p, 24));
        int ticks = 140;
        sound(p, SoundEvents.TRIPWIRE_ATTACH, 1f, 0.6f);
        sound(p, SoundEvents.CHAIN_PLACE, 1f, 0.7f);
        SkillFx.play(p, SkillFxPayload.Kind.ZONE, s.color(), center, 4, ticks);
        for (int t = 0; t < ticks; t += 20) {
            ServerScheduler.schedule(t, () -> {
                if (p.level() != level) return;
                for (LivingEntity e : around(p, center, 4, x -> canHit(p, x))) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 5));
                    hit(p, e, snareDamage(l, tier(p)) + stat(p, Stat.SENSE, 0.05f), false);
                }
            });
        }
        return true;
    }

    public static boolean thunderVolley(ServerPlayer p, Skill s, int l) {
        float dmg = volleyDamage(l, tier(p)) + stat(p, Stat.AGI, 0.15f);
        float yaw = p.getYRot();
        for (int i = -2; i <= 2; i++) {
            Vec3 dir = Vec3.directionFromRotation(p.getXRot(), yaw + i * 9f);
            Vec3 eye = p.getEyePosition();
            Vec3 end = clip(p, eye, eye.add(dir.scale(28)));
            for (LivingEntity e : struckAlong(p, eye, end, 0.4)) {
                hit(p, e, dmg, false);
                LivingEntity arc = null;
                for (LivingEntity o : around(p, e.position(), 5, x -> canHit(p, x) && x != e)) {
                    arc = o;
                    break;
                }
                if (arc != null) {
                    hit(p, arc, dmg * 0.5f, true);
                    SkillFx.play(e, SkillFxPayload.Kind.BOLT, s.color(), arc.position().add(0, arc.getBbHeight() * 0.5, 0), 0.25f, 0);
                }
            }
            SkillFx.play(p, SkillFxPayload.Kind.SHOT, s.color(), end, 0.35f, 0);
        }
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 1.1f);
        sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.3f, 2f);
        return true;
    }

    public static boolean heavensArrow(ServerPlayer p, Skill s, int l) {
        float dmg = heavensDamage(l, tier(p)) + stat(p, Stat.AGI, 0.5f) + stat(p, Stat.SENSE, 0.3f);
        Vec3 end = line(p, 80, 1.1, dmg, 1.4, SkillFxPayload.Kind.SHOT, s.color());
        sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.9f, 1.5f);
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.4f);
        SkillFx.play(p, SkillFxPayload.Kind.SHOT, s.color(), end, 1.4f, 0);
        SkillFx.play(p, SkillFxPayload.Kind.ROAR, s.color(), p.position(), 4, 0);
        return true;
    }

    public static boolean stormOfAThousand(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = ground(level, aimPoint(p, 36));
        int count = stormArrows(l);
        double damage = (4.5 + 0.35 * l) * (1 + 0.12 * tier(p));
        sound(p, SoundEvents.ARROW_SHOOT, 1f, 0.4f);
        SkillFx.play(p, SkillFxPayload.Kind.ZONE, s.color(), center, 7, 70);
        for (int i = 0; i < count; i++) {
            int delay = 6 + i * 60 / count;
            ServerScheduler.schedule(delay, () -> {
                if (p.level() != level) return;
                double ang = level.random.nextDouble() * Math.PI * 2;
                double rad = Math.sqrt(level.random.nextDouble()) * 7;
                Arrow arrow = new Arrow(level, center.x + Math.cos(ang) * rad, center.y + 18, center.z + Math.sin(ang) * rad,
                        new ItemStack(Items.ARROW), null);
                arrow.setOwner(p);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.setBaseDamage(damage);
                arrow.setCritArrow(true);
                arrow.addTag(SkillActions.RAIN_TAG);
                arrow.addTag(STORM_ARROW_TAG);
                arrow.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, -2.8, (level.random.nextDouble() - 0.5) * 0.1);
                level.addFreshEntity(arrow);
            });
        }
        return true;
    }

    // ================================================================ helpers

    static boolean undead(LivingEntity e) {
        return e.getType().is(EntityTypeTags.UNDEAD);
    }

    /** Stuns: almost frozen and too weak to hit back for a moment. */
    static void stun(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 5));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2));
    }

    static void cleanse(Player ally) {
        for (Holder<MobEffect> e : CLEANSED) ally.removeEffect(e);
        ally.removeEffect(ModEffects.VENOM);
        ally.removeEffect(ModEffects.CRIMSON_BRAND);
        Buildups.clear(ally, Buildups.BLOOD);
        Buildups.clear(ally, Statuses.VENOM);
        Buildups.clear(ally, Statuses.CRIMSON);
        Buildups.clear(ally, Statuses.BURN);
    }

    private static void setModifier(ServerPlayer p, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id, double value) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst != null) inst.addOrUpdateTransientModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
    }

    /** A piercing line from the eyes: hits everything along it once. Returns where it ends. */
    private static Vec3 line(ServerPlayer p, double range, double width, float dmg, double push, SkillFxPayload.Kind kind, int color) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = clip(p, eye, eye.add(p.getLookAngle().scale(range)));
        Vec3 dir = end.subtract(eye).normalize();
        for (LivingEntity e : struckAlong(p, eye, end, width)) {
            hit(p, e, dmg, false);
            e.knockback(push, -dir.x, -dir.z);
            p.serverLevel().sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 10, 0.15, 0.15, 0.15, 0.3);
        }
        return end;
    }

    private static List<LivingEntity> struckAlong(ServerPlayer p, Vec3 from, Vec3 to, double width) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(width + 1), x -> canHit(p, x))) {
            Optional<Vec3> at = e.getBoundingBox().inflate(width).clip(from, to);
            if (at.isPresent()) out.add(e);
        }
        return out;
    }

    private static Vec3 clip(ServerPlayer p, Vec3 from, Vec3 to) {
        var hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    private static void teleport(ServerPlayer p, Vec3 dest, LivingEntity faceTo) {
        Vec3 d = faceTo.getEyePosition().subtract(dest.add(0, p.getEyeHeight(), 0));
        float yaw = (float) (Math.atan2(d.z, d.x) * (180 / Math.PI)) - 90f;
        float pitch = (float) (-(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * (180 / Math.PI)));
        p.teleportTo(p.serverLevel(), dest.x, dest.y, dest.z, yaw, pitch);
        if (p.position().distanceToSqr(dest) > 0.01) p.moveTo(dest.x, dest.y, dest.z, yaw, pitch);
        p.fallDistance = 0;
    }

    /** Draws an effect at a point instead of on an entity. */
    static void fxAt(ServerLevel level, SkillFxPayload.Kind kind, int color, Vec3 at, float size, int duration) {
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 96,
                new SkillFxPayload(-1, kind, color & 0xFFFFFF, (float) at.x, (float) at.y, (float) at.z, size, duration));
    }

    private static List<Player> allies(ServerPlayer p, double radius) {
        List<Player> list = new ArrayList<>(p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(radius), Player::isAlive));
        if (!list.contains(p)) list.add(p);
        return list;
    }

    static List<LivingEntity> around(ServerPlayer p, Vec3 at, double radius, Predicate<LivingEntity> filter) {
        return p.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> filter.test(e) && e.position().distanceTo(at) <= radius + e.getBbWidth() / 2);
    }

    static void hit(ServerPlayer p, LivingEntity e, float damage, boolean magic) {
        e.invulnerableTime = 0;
        e.hurt(magic ? p.damageSources().indirectMagic(p, p) : p.damageSources().playerAttack(p), damage);
    }

    private static Vec3 ground(ServerLevel level, Vec3 at) {
        var hit = level.clip(new ClipContext(at.add(0, 1.5, 0), at.add(0, -16, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
    }

    private static Vec3 flat(Vec3 v, Entity e) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, e.getYRot()) : f.normalize();
    }

    private static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private SubclassActions() {}
}
