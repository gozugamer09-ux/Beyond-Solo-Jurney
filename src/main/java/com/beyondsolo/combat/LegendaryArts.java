package com.beyondsolo.combat;

import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.network.SignatureFxPayload;
import com.beyondsolo.system.Balance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The legendary weapons' arts: one heavy blow each, every one its own shape. The eight arsenal legendaries and the
 * awakening trial's four each have one: a ring of lightning blades, a barrage of fists, three hounds of fire, a
 * constellation, a dawn, a gale, a step through the dark, a wave of the wielder's own blood, a sword of judgement out
 * of the sky, a pyre, a fan of glass and a ring of thorns.
 */
final class LegendaryArts {
    /** Ticks from the Stormbreak ring to the chain of bolts between the foes it cut. */
    static final int CHAIN_DELAY = 4;
    /** Ticks between Titan's Barrage punches, and how many there are before the uppercut. */
    static final int PUNCH_GAP = 2, PUNCHES = 6;
    static final int UPPERCUT = PUNCH_GAP * PUNCHES + 2;
    /** Ticks from the constellation's stars to their light falling. */
    static final int STARLIGHT_DELAY = 14;
    /** The Ashen Oath's pyre burns this long, biting every {@link #PYRE_TICK} ticks. */
    static final int PYRE_TICKS = 60, PYRE_TICK = 10;

    // ---------------------------------------------------------------- Stormlord's Longsword

    /**
     * Stormbreak: one turn of the whole body throws a ring of lightning blades out round the wielder, and then the
     * lightning leaps from each foe it cut to the next. Points: the wielder, then the cut foes in turn round them.
     */
    static List<Vec3> stormbreak(ServerLevel level, Player p, float power) {
        Vec3 c = p.position();
        List<LivingEntity> cut = new ArrayList<>(ArsenalHits.foesAround(p, c, 6.5));
        cut.sort(Comparator.comparingDouble(e -> Math.atan2(e.getZ() - c.z, e.getX() - c.x)));
        List<Vec3> pts = new ArrayList<>(List.of(c));
        for (LivingEntity e : cut) {
            ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 5f) + power * 0.5f);
            if (pts.size() < 24) pts.add(ArsenalAbilities.chest(e));
        }
        ArsenalAbilities.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.8f);
        ArsenalAbilities.sound(level, p, SoundEvents.TRIDENT_THUNDER.value(), 0.9f, 1.5f);
        if (!cut.isEmpty()) {
            ArsenalAbilities.later(p, level, CHAIN_DELAY, () -> {
                for (LivingEntity e : cut) if (e.isAlive()) ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 2f));
                ArsenalAbilities.sound(level, p, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.1f, 1.4f);
            });
        }
        return pts;
    }

    // ---------------------------------------------------------------- Titan's Knuckles

    /**
     * Titan's Barrage: six blows of golden fists into everything just ahead, then an uppercut that throws it into the
     * air; the guardian stands firmer while it lasts. Beat 1's points: the wielder, then whoever the uppercut threw.
     */
    static List<Vec3> titansBarrage(ServerLevel level, Player p, float power) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, false, true, true));
        for (int i = 0; i < PUNCHES; i++) {
            float pitch = 0.8f + i * 0.08f;
            ArsenalAbilities.later(p, level, i * PUNCH_GAP, () -> {
                Vec3 look = ArsenalAbilities.flatLook(p);
                for (LivingEntity e : ArsenalAbilities.cone(p, p.position(), look, 3.5, 50)) {
                    ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 1.4f) + power * 0.12f);
                    ArsenalAbilities.knock(e, look.x * 0.12, 0.02, look.z * 0.12);
                }
                ArsenalAbilities.sound(level, p, SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, pitch);
            });
        }
        ArsenalAbilities.later(p, level, UPPERCUT, () -> {
            Vec3 look = ArsenalAbilities.flatLook(p);
            List<Vec3> pts = new ArrayList<>(List.of(p.position()));
            for (LivingEntity e : ArsenalAbilities.cone(p, p.position(), look, 3.8, 55)) {
                ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 4f) + power * 0.4f);
                ArsenalAbilities.knock(e, look.x * 0.3, 1.0, look.z * 0.3);
                if (pts.size() < 24) pts.add(ArsenalAbilities.chest(e));
            }
            ArsenalAbilities.sound(level, p, SoundEvents.ANVIL_LAND, 0.7f, 1.3f);
            ArsenalAbilities.sound(level, p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.7f);
            ArsenalAbilities.send(p, Signature.TITANS_BARRAGE, SignatureFxPayload.beat(1), pts);
        });
        return List.of(p.position());
    }

    // ---------------------------------------------------------------- Hellhound Gatekeeper's Halberd

    /**
     * Three-Headed Maw: the halberd's sweep lets loose three hounds' heads of fire down three lanes ahead; each bites
     * the first foe in its lane, harder still if that foe is nearly dead. Points: the mouth of the sweep, then where
     * each head stopped.
     */
    static List<Vec3> threeHeadedMaw(ServerLevel level, Player p, float power) {
        Vec3 look = ArsenalAbilities.flatLook(p);
        Vec3 origin = p.position().add(0, 1.0, 0);
        List<Vec3> pts = new ArrayList<>(List.of(origin));
        for (float lane : new float[]{-25, 0, 25}) {
            Vec3 dir = look.yRot(lane * Mth.DEG_TO_RAD);
            Vec3 end = ArsenalAbilities.reach(p, origin, origin.add(dir.scale(9)));
            LivingEntity bitten = ArsenalAbilities.line(p, origin, end, 1.1).stream()
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(origin))).orElse(null);
            if (bitten != null) {
                float dmg = Balance.flatDamage(p, 5f) + power * 0.45f;
                if (bitten.getHealth() < bitten.getMaxHealth() * 0.3f) dmg *= 1.4f;
                ArsenalHits.magicHit(p, bitten, dmg);
                Statuses.apply(bitten, DamageElement.FIRE, 40, p);
                ArsenalAbilities.knock(bitten, dir.x * 0.5, 0.25, dir.z * 0.5);
                end = ArsenalAbilities.chest(bitten);
                ArsenalAbilities.soundAt(level, end, SoundEvents.EVOKER_FANGS_ATTACK, 1.2f, 0.7f);
            }
            pts.add(end);
        }
        ArsenalAbilities.sound(level, p, SoundEvents.WOLF_GROWL, 1.3f, 0.45f);
        ArsenalAbilities.sound(level, p, SoundEvents.BLAZE_SHOOT, 1f, 0.6f);
        ArsenalAbilities.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        return pts;
    }

    // ---------------------------------------------------------------- Starseer's Staff

    /**
     * Constellation: up to five foes within 16 blocks are marked with stars, joined into a constellation, and then the
     * stars' light falls on each of them, scaled by spell power. Points: the marked foes.
     */
    static List<Vec3> constellation(ServerLevel level, Player p) {
        List<LivingEntity> marked = ArsenalHits.foesAround(p, p.position(), 16).stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).limit(5).toList();
        float spell = 1f + (float) p.getAttributeValue(ModAttributes.SPELL_POWER);
        List<Vec3> pts = new ArrayList<>();
        for (LivingEntity e : marked) pts.add(e.position().add(0, e.getBbHeight() + 0.7, 0));
        if (pts.isEmpty()) pts.add(p.position().add(ArsenalAbilities.flatLook(p).scale(5)).add(0, 2.5, 0));
        ArsenalAbilities.sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 1.5f);
        ArsenalAbilities.sound(level, p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.8f);
        ArsenalAbilities.later(p, level, STARLIGHT_DELAY, () -> {
            List<Vec3> fell = new ArrayList<>();
            for (LivingEntity e : marked) {
                if (!e.isAlive()) continue;
                ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 6f) * spell);
                fell.add(e.position());
                ArsenalAbilities.soundAt(level, e.position(), SoundEvents.AMETHYST_BLOCK_BREAK, 1.2f, 0.9f);
            }
            if (!fell.isEmpty()) ArsenalAbilities.sound(level, p, SoundEvents.TRIDENT_THUNDER.value(), 0.8f, 1.7f);
            ArsenalAbilities.send(p, Signature.CONSTELLATION, SignatureFxPayload.beat(1), fell);
        });
        return pts;
    }

    // ---------------------------------------------------------------- Saint's Sunstaff

    /**
     * Saint's Dawn: a sun rises behind the healer and its light falls for eight blocks round: the party heals a quarter
     * of its health and is shielded a while; foes are burnt by it and dazzled. Points: the first holds the number of
     * allies (its x), then the allies, then the foes.
     */
    static List<Vec3> saintsDawn(ServerLevel level, Player p) {
        List<Vec3> allies = new ArrayList<>();
        for (Player ally : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(8), Player::isAlive)) {
            Balance.healRaw(ally, ally.getMaxHealth() * 0.25f);
            ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120, 0, false, true, true));
            if (ally != p && allies.size() < 12) allies.add(ally.position());
        }
        List<Vec3> foes = new ArrayList<>();
        for (LivingEntity e : ArsenalHits.foesAround(p, p.position(), 8)) {
            ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 4f));
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, true, true), p);
            if (foes.size() < 12) foes.add(e.position());
        }
        ArsenalAbilities.sound(level, p, SoundEvents.BEACON_POWER_SELECT, 1.2f, 1.6f);
        ArsenalAbilities.sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 1.2f);
        List<Vec3> pts = new ArrayList<>();
        pts.add(new Vec3(allies.size(), 0, 0));
        pts.addAll(allies);
        pts.addAll(foes);
        return pts;
    }

    // ---------------------------------------------------------------- Galebreaker Warbow (drawn)

    /**
     * Gale Lance: a charged draw looses a gale with the arrow that drives through everything in a line 30 blocks long,
     * hurling it back. Points: the bow, where the gale stopped, then the foes it struck.
     */
    static void galeLance(ServerLevel level, Player p, AbstractArrow arrow) {
        Vec3 eye = p.getEyePosition();
        Vec3 dir = arrow.getDeltaMovement().lengthSqr() > 1.0e-4 ? arrow.getDeltaMovement().normalize() : p.getLookAngle();
        Vec3 end = ArsenalAbilities.reach(p, eye, eye.add(dir.scale(30)));
        List<Vec3> pts = new ArrayList<>(List.of(eye, end));
        for (LivingEntity e : ArsenalAbilities.line(p, eye, end, 1.3)) {
            ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 6f));
            ArsenalAbilities.knock(e, dir.x * 1.3, 0.25, dir.z * 1.3);
            if (pts.size() < 24) pts.add(ArsenalAbilities.chest(e));
        }
        ArsenalAbilities.sound(level, p, SoundEvents.BREEZE_SHOOT, 1.4f, 0.7f);
        ArsenalAbilities.sound(level, p, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1f, 1.2f);
        ArsenalAbilities.send(p, Signature.GALE_LANCE, SignatureFxPayload.IMPACT, pts);
    }

    // ---------------------------------------------------------------- Nightveil Kris

    /**
     * Veilstep: the assassin sinks into the dark and steps out of it behind the nearest foe within 10 blocks, striking
     * its back (half again as hard if it never saw them coming), and stays hidden for two seconds. With no foe near it
     * is a short step through the dark ahead. Points: from, to, and the foe struck.
     */
    static List<Vec3> veilstep(ServerLevel level, Player p, float power) {
        Vec3 from = p.position();
        Vec3 look = ArsenalAbilities.flatLook(p);
        List<LivingEntity> near = ArsenalHits.foesAround(p, from, 10);
        LivingEntity target = ArsenalAbilities.cone(p, from, look, 10, 75).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(p)))
                .orElse(near.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null));
        ArsenalAbilities.soundAt(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.9f, 0.7f);
        if (target == null) {
            Vec3 to = ArsenalAbilities.reach(p, from.add(0, 0.6, 0), from.add(look.scale(6)).add(0, 0.6, 0)).subtract(look.scale(0.5)).add(0, -0.6, 0);
            p.teleportTo(to.x, to.y, to.z);
            p.resetFallDistance();
            return List.of(from, to);
        }
        Vec3 dir = target.position().subtract(from).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0e-4 ? look : dir.normalize();
        Vec3 to = target.position().add(dir.scale(target.getBbWidth() / 2 + 0.9));
        if (!level.noCollision(p, p.getBoundingBox().move(to.subtract(p.position())))) to = target.position().subtract(dir.scale(target.getBbWidth() / 2 + 0.9));
        if (!level.noCollision(p, p.getBoundingBox().move(to.subtract(p.position())))) to = from;
        boolean unseen = !(target instanceof Mob m && m.getTarget() == p);
        float yaw = (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG) + 180;
        if (p instanceof ServerPlayer sp) sp.teleportTo(level, to.x, to.y, to.z, yaw, sp.getXRot());
        else p.teleportTo(to.x, to.y, to.z);
        p.resetFallDistance();
        float dmg = (Balance.flatDamage(p, 6f) + power * 0.7f) * (unseen ? 1.5f : 1f);
        ArsenalHits.magicHit(p, target, dmg);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, true));
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12), m -> m.getTarget() == p && m != target)) {
            m.setTarget(null);
        }
        ArsenalAbilities.sound(level, p, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.6f);
        ArsenalAbilities.sound(level, p, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.4f);
        return List.of(from, to, ArsenalAbilities.chest(target));
    }

    // ---------------------------------------------------------------- Ironblood Claymore

    /**
     * Blood Price: the wielder pays with their own blood (8% of their health) and the claymore throws it as a great
     * crescent wave twelve blocks ahead; a quarter of what it takes from each foe flows back to them. Points: from, to.
     */
    static List<Vec3> bloodPrice(ServerLevel level, Player p, float power) {
        float cost = p.getHealth() * 0.08f;
        if (p.getHealth() - cost >= 1) p.setHealth(p.getHealth() - cost);
        Vec3 look = ArsenalAbilities.flatLook(p);
        Vec3 from = p.position().add(0, 1.0, 0);
        Vec3 to = ArsenalAbilities.reach(p, from, from.add(look.scale(12)));
        for (LivingEntity e : ArsenalAbilities.line(p, from, to, 1.6)) {
            int delay = (int) Math.min(6, e.position().distanceTo(p.position()) / 2);
            ArsenalAbilities.later(p, level, delay, () -> {
                if (!e.isAlive()) return;
                float took = ArsenalAbilities.hitAndMeasure(p, e, Balance.flatDamage(p, 8f) + power * 0.8f);
                Statuses.apply(e, DamageElement.BLOOD, 40, p);
                if (took > 0) Balance.healRaw(p, took * 0.25f);
                ArsenalAbilities.soundAt(level, e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.6f);
            });
        }
        ArsenalAbilities.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
        ArsenalAbilities.sound(level, p, SoundEvents.WARDEN_ATTACK_IMPACT, 0.8f, 0.8f);
        return List.of(from, to);
    }

    // ---------------------------------------------------------------- Sunderveil (the trial's greatsword)

    /**
     * Sundering Verdict: a sword of light the size of a tree falls point first six blocks ahead, splitting the ground
     * and throwing back everything within four blocks of it. Points: where it fell.
     */
    static List<Vec3> sunderingVerdict(ServerLevel level, Player p, float power) {
        Vec3 look = ArsenalAbilities.flatLook(p);
        Vec3 from = p.position().add(0, 0.8, 0);
        Vec3 stop = ArsenalAbilities.reach(p, from, from.add(look.scale(6)));
        Vec3 at = ArsenalAbilities.ground(level, stop.subtract(look.scale(stop.distanceTo(from) < 5.9 ? 0.8 : 0)).add(0, -0.8, 0));
        for (LivingEntity e : ArsenalHits.foesAround(p, at, 4)) {
            ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 9f) + power * 0.8f);
            Statuses.apply(e, DamageElement.CRIMSON, 40, p);
            Vec3 out = e.position().subtract(at).multiply(1, 0, 1);
            out = out.lengthSqr() < 1.0e-4 ? look : out.normalize();
            ArsenalAbilities.knock(e, out.x * 0.8, 0.4, out.z * 0.8);
        }
        ArsenalAbilities.soundAt(level, at, SoundEvents.ANVIL_LAND, 1.2f, 0.5f);
        ArsenalAbilities.soundAt(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.1f, 0.8f);
        ArsenalAbilities.soundAt(level, at, SoundEvents.BEACON_DEACTIVATE, 1f, 0.6f);
        return List.of(at);
    }

    // ---------------------------------------------------------------- Ashen Oath (the trial's sword)

    /**
     * Pyre of Oaths: the sword is planted and six pillars of fire rise round the wielder; everything within five blocks
     * burns, and the pyre keeps burning there for three seconds. Points: the pyre's middle.
     */
    static List<Vec3> pyreOfOaths(ServerLevel level, Player p, float power) {
        Vec3 c = p.position();
        for (LivingEntity e : ArsenalHits.foesAround(p, c, 5)) {
            ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 5f) + power * 0.4f);
            Statuses.apply(e, DamageElement.FIRE, 60, p);
        }
        for (int t = PYRE_TICK; t <= PYRE_TICKS; t += PYRE_TICK) {
            ArsenalAbilities.later(p, level, t, () -> {
                for (LivingEntity e : ArsenalHits.foesAround(p, c, 5)) {
                    ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 1.5f));
                    Statuses.apply(e, DamageElement.FIRE, 10, p);
                }
            });
        }
        ArsenalAbilities.sound(level, p, SoundEvents.BLAZE_SHOOT, 1.3f, 0.5f);
        ArsenalAbilities.sound(level, p, SoundEvents.FIRECHARGE_USE, 1.2f, 0.5f);
        ArsenalAbilities.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 0.8f);
        return List.of(c);
    }

    // ---------------------------------------------------------------- Nightglass Fang (the trial's dagger)

    /**
     * Nightglass Fan: seven shards of dark glass thrown in a fan ahead; each one stabs the first foe in its way and
     * breaks into venom. Points: the hand, then where each shard stopped.
     */
    static List<Vec3> nightglassFan(ServerLevel level, Player p, float power) {
        Vec3 eye = p.getEyePosition().add(0, -0.25, 0);
        Vec3 look = p.getLookAngle();
        List<Vec3> pts = new ArrayList<>(List.of(eye));
        for (int i = 0; i < 7; i++) {
            float ang = (-35 + i * 70f / 6) * Mth.DEG_TO_RAD;
            Vec3 dir = look.yRot(ang);
            Vec3 end = ArsenalAbilities.reach(p, eye, eye.add(dir.scale(12)));
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, p, eye, end, new AABB(eye, end).inflate(1),
                    e -> e instanceof LivingEntity l && ArsenalHits.isFoe(p, l), 0.35f);
            if (hit != null && hit.getEntity() instanceof LivingEntity e) {
                end = hit.getLocation();
                int delay = (int) Math.min(5, end.distanceTo(eye) / 3);
                ArsenalAbilities.later(p, level, delay, () -> {
                    if (!e.isAlive()) return;
                    ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 3f) + power * 0.25f);
                    Statuses.apply(e, DamageElement.POISON, 45, p);
                    ArsenalAbilities.soundAt(level, e.position(), SoundEvents.GLASS_BREAK, 0.8f, 1.4f);
                });
            }
            pts.add(end);
        }
        ArsenalAbilities.sound(level, p, SoundEvents.TRIDENT_THROW.value(), 1f, 1.5f);
        ArsenalAbilities.sound(level, p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 1.4f);
        return pts;
    }

    // ---------------------------------------------------------------- Thorn Requiem (the trial's scythe)

    /**
     * Requiem of Thorns: the scythe's turn wakes a ring of blood thorns that bursts out of the ground round the
     * wielder, running outward six blocks; each foe it impales is slowed, and each one gives the wielder back a little
     * life (up to five). Points: the wielder, then the impaled foes.
     */
    static List<Vec3> requiemOfThorns(ServerLevel level, Player p, float power) {
        Vec3 c = p.position();
        List<Vec3> pts = new ArrayList<>(List.of(c));
        int fed = 0;
        for (LivingEntity e : ArsenalHits.foesAround(p, c, 6)) {
            int delay = (int) Math.min(6, e.position().distanceTo(c) * 0.9);
            ArsenalAbilities.later(p, level, delay, () -> {
                if (!e.isAlive()) return;
                ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 7f) + power * 0.6f);
                Statuses.apply(e, DamageElement.BLOOD, 50, p);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true, true), p);
                ArsenalAbilities.soundAt(level, e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.9f, 0.5f);
            });
            if (fed < 5) fed++;
            if (pts.size() < 24) pts.add(e.position());
        }
        if (fed > 0) Balance.healRaw(p, p.getMaxHealth() * 0.03f * fed);
        ArsenalAbilities.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.45f);
        ArsenalAbilities.sound(level, p, SoundEvents.ROOTED_DIRT_BREAK, 1.4f, 0.5f);
        ArsenalAbilities.sound(level, p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 0.5f);
        return pts;
    }

    private LegendaryArts() {}
}
