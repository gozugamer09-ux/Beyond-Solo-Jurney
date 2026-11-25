package com.beyondsolo.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.network.SignatureFxPayload;
import com.beyondsolo.system.Balance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The divine weapons' arts, one per class: whole scenes in beats. Each opens with a wind-up the world can read (the
 * ground cracks, a star is born, a giant hammer of light rises), lands its first blow on the strike frame and answers
 * it with a second beat: the fissure that erupts after the cleave, the wounds that open after the dash, the star that
 * collapses after it has fed, the bulwark that throws back what it took.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
final class DivineArts {
    /** Ticks from the cleave to the fissure erupting. */
    static final int FISSURE_DELAY = 8;
    static final double FISSURE_LENGTH = 12;
    /** Ticks from the dash to the wounds opening. */
    static final int WOUND_DELAY = 12;
    /** Ticks the devouring star feeds (pulls foes in) before it collapses. */
    static final int FEED_TICKS = 16;
    /** How long the Colossus stance holds before its retribution. */
    static final int STANCE_TICKS = 160;
    /** Ticks after the star arrow lands that each small star falls, and the great one. */
    static final int STAR_FIRST = 4, STAR_GAP = 3, STARS = 7, GREAT_STAR = 28;

    private static final String STAR_TAG = "beyondsolo.star_arrow";

    private static final class Bastion {
        final long until;
        float taken;

        Bastion(long until) {
            this.until = until;
        }
    }

    private static final Map<Player, Bastion> BASTIONS = new WeakHashMap<>();

    // ---------------------------------------------------------------- Ruinbringer (warrior)

    /**
     * Cataclysm Cleave: a cleave of fire brought down through everything ahead; a moment later the ground it split erupts in
     * a line of fire twelve blocks long. Points: the fissure's start and end.
     */
    static List<Vec3> cataclysmCleave(ServerLevel level, Player p, float power) {
        Vec3 look = ArsenalAbilities.flatLook(p);
        Vec3 origin = p.position();
        for (LivingEntity e : ArsenalAbilities.cone(p, origin, look, 6, 55)) {
            ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 7f) + power * 0.6f);
            Statuses.apply(e, DamageElement.FIRE, 60, p);
            ArsenalAbilities.knock(e, look.x * 0.4, 0.4, look.z * 0.4);
        }
        Vec3 start = origin.add(look.scale(1.5));
        BlockHitResult wall = level.clip(new ClipContext(start.add(0, 0.5, 0), start.add(look.scale(FISSURE_LENGTH)).add(0, 0.5, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = wall.getType() == HitResult.Type.MISS ? start.add(look.scale(FISSURE_LENGTH)) : wall.getLocation().add(0, -0.5, 0);
        ArsenalAbilities.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 1.1f, 0.7f);
        ArsenalAbilities.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
        ArsenalAbilities.sound(level, p, SoundEvents.BLAZE_SHOOT, 1f, 0.5f);
        ArsenalAbilities.later(p, level, FISSURE_DELAY, () -> {
            List<Vec3> pts = new ArrayList<>(List.of(start, end));
            for (LivingEntity e : ArsenalAbilities.line(p, start.add(0, 0.5, 0), end.add(0, 0.5, 0), 1.8)) {
                ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 5f) + power * 0.45f);
                Statuses.apply(e, DamageElement.FIRE, 40, p);
                ArsenalAbilities.knock(e, 0, 0.8, 0);
                if (pts.size() < 24) pts.add(ArsenalAbilities.chest(e));
            }
            Vec3 mid = start.lerp(end, 0.5);
            ArsenalAbilities.soundAt(level, mid, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.55f);
            ArsenalAbilities.soundAt(level, end, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.7f);
            ArsenalAbilities.soundAt(level, mid, SoundEvents.BLAZE_SHOOT, 1.4f, 0.4f);
            ArsenalAbilities.send(p, Signature.CATACLYSM_CLEAVE, SignatureFxPayload.beat(1), pts);
        });
        return List.of(start, end);
    }

    // ---------------------------------------------------------------- Calamity Fang (assassin)

    /**
     * Dragon's Wrath: a dash of up to ten blocks through everything in a line, cutting as it passes; a moment after
     * the wielder stops, the wounds open on everything it passed. Points: from, to, then the cut foes.
     */
    static List<Vec3> dragonsWrath(ServerLevel level, Player p, float power) {
        Vec3 from = p.position();
        Vec3 look = ArsenalAbilities.flatLook(p);
        Vec3 want = from.add(look.scale(10));
        BlockHitResult wall = level.clip(new ClipContext(from.add(0, 0.6, 0), want.add(0, 0.6, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 to = wall.getType() == HitResult.Type.MISS ? want : wall.getLocation().subtract(look.scale(0.6)).add(0, -0.6, 0);
        List<Vec3> points = new ArrayList<>(List.of(from, to));
        List<LivingEntity> cut = ArsenalAbilities.line(p, from.add(0, 1, 0), to.add(0, 1, 0), 1.4);
        for (LivingEntity e : cut) {
            ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 4f) + power * 0.4f);
            if (points.size() < 24) points.add(ArsenalAbilities.chest(e));
        }
        p.teleportTo(to.x, to.y, to.z);
        p.resetFallDistance();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 4, false, false, false));
        ArsenalAbilities.sound(level, p, SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 1.4f);
        ArsenalAbilities.sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.3f);
        ArsenalAbilities.soundAt(level, from, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.6f);
        ArsenalAbilities.later(p, level, WOUND_DELAY, () -> {
            List<Vec3> wounds = new ArrayList<>();
            wounds.add(p.position());
            for (LivingEntity e : cut) {
                if (!e.isAlive()) continue;
                ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 6f) + power * 0.5f);
                Statuses.apply(e, DamageElement.CRIMSON, 50, p);
                if (wounds.size() < 24) wounds.add(ArsenalAbilities.chest(e));
            }
            ArsenalAbilities.sound(level, p, SoundEvents.PLAYER_ATTACK_CRIT, 1.4f, 0.6f);
            ArsenalAbilities.sound(level, p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1f, 2f);
            for (int i = 1; i < wounds.size(); i++) ArsenalAbilities.soundAt(level, wounds.get(i), SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 1.5f);
            ArsenalAbilities.send(p, Signature.DRAGONS_WRATH, SignatureFxPayload.beat(1), wounds);
        });
        return points;
    }

    // ---------------------------------------------------------------- Eye of the Insatiable (mage)

    /**
     * Devouring Star: a black star flies to where the mage looks (up to 28 blocks), feeds there, dragging everything
     * near into itself, then collapses and bursts, scaled by spell power. Points: muzzle, where it fed.
     */
    static List<Vec3> devouringStar(ServerLevel level, Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(28));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, p, eye, stop, new AABB(eye, stop).inflate(1),
                e -> e instanceof LivingEntity l && ArsenalHits.isFoe(p, l), 0.3f);
        Vec3 at = entity != null ? entity.getLocation() : stop.subtract(p.getLookAngle().scale(0.6));
        float spell = 1f + (float) p.getAttributeValue(ModAttributes.SPELL_POWER);
        ArsenalAbilities.sound(level, p, SoundEvents.EVOKER_CAST_SPELL, 1.2f, 0.7f);
        ArsenalAbilities.soundAt(level, at, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.4f, 0.5f);
        ArsenalAbilities.soundAt(level, at, SoundEvents.PORTAL_AMBIENT, 1.2f, 0.6f);
        for (int t = 2; t < FEED_TICKS; t += 2) {
            ArsenalAbilities.later(p, level, t, () -> {
                for (LivingEntity e : ArsenalHits.foesAround(p, at, 6.5)) {
                    Vec3 in = at.subtract(e.position().add(0, e.getBbHeight() * 0.4, 0));
                    double d = in.length();
                    if (d < 0.6) continue;
                    double pull = 0.16 * Math.min(1, d / 2) * (1 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * 0.7);
                    Vec3 v = in.normalize().scale(pull);
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(v.x, v.y * 0.5 + 0.02, v.z));
                    e.hurtMarked = true;
                }
            });
        }
        ArsenalAbilities.later(p, level, FEED_TICKS, () -> {
            for (LivingEntity e : ArsenalHits.foesAround(p, at, 4.5)) {
                ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 12f) * spell);
                Vec3 out = e.position().subtract(at).multiply(1, 0, 1);
                if (out.lengthSqr() > 1.0e-4) out = out.normalize();
                ArsenalAbilities.knock(e, out.x * 0.6, 0.45, out.z * 0.6);
            }
            ArsenalAbilities.soundAt(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 1.2f);
            ArsenalAbilities.soundAt(level, at, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.4f, 0.5f);
            ArsenalAbilities.soundAt(level, at, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.6f);
            ArsenalAbilities.send(p, Signature.DEVOURING_STAR, SignatureFxPayload.beat(1), List.of(at));
        });
        return List.of(eye.add(p.getLookAngle().scale(1.2)), at);
    }

    // ---------------------------------------------------------------- Colossus Aegis (guardian)

    /**
     * Colossus Stance: a hammer of light the size of a tower comes down with the wielder's own, throwing back
     * whatever stands close and drawing every foe's eye. For eight seconds the guardian shrugs off blows behind a
     * bulwark of light, then the bulwark throws back a share of everything it took. Points: the knocked-back foes.
     */
    static List<Vec3> colossusStance(ServerLevel level, Player p, float power) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, STANCE_TICKS, 1, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, STANCE_TICKS, 2, false, true, true));
        List<Vec3> knocked = new ArrayList<>();
        for (LivingEntity e : ArsenalHits.foesAround(p, p.position(), 14)) {
            if (e instanceof Mob m) m.setTarget(p);
            if (e.distanceTo(p) < 5) {
                Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0e-4 ? ArsenalAbilities.flatLook(p) : away.normalize();
                ArsenalAbilities.knock(e, away.x * 1.1, 0.35, away.z * 1.1);
                ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 6f) + power * 0.5f);
                if (knocked.size() < 24) knocked.add(e.position());
            }
        }
        BASTIONS.put(p, new Bastion(level.getGameTime() + STANCE_TICKS));
        ArsenalAbilities.sound(level, p, SoundEvents.ANVIL_LAND, 1f, 0.5f);
        ArsenalAbilities.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 0.6f);
        ArsenalAbilities.sound(level, p, SoundEvents.IRON_GOLEM_REPAIR, 1.2f, 0.6f);
        ArsenalAbilities.later(p, level, STANCE_TICKS, () -> retribution(level, p, power));
        return knocked;
    }

    /** The bulwark breaks outward: a flat blow plus a share of what it took, never more than a heavy art's worth. */
    static void retribution(ServerLevel level, Player p, float power) {
        Bastion b = BASTIONS.remove(p);
        if (b == null) return;
        float amount = Balance.flatDamage(p, 4f) + Math.min(b.taken * 0.6f, Balance.flatDamage(p, 12f));
        List<Vec3> pts = new ArrayList<>(List.of(p.position()));
        for (LivingEntity e : ArsenalHits.foesAround(p, p.position(), 6)) {
            ArsenalHits.magicHit(p, e, amount);
            Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0e-4 ? ArsenalAbilities.flatLook(p) : away.normalize();
            ArsenalAbilities.knock(e, away.x * 0.9, 0.3, away.z * 0.9);
            if (pts.size() < 24) pts.add(ArsenalAbilities.chest(e));
        }
        ArsenalAbilities.sound(level, p, SoundEvents.GLASS_BREAK, 1.4f, 0.5f);
        ArsenalAbilities.sound(level, p, SoundEvents.ANVIL_LAND, 0.8f, 0.8f);
        ArsenalAbilities.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.9f);
        ArsenalAbilities.send(p, Signature.COLOSSUS_STANCE, SignatureFxPayload.beat(1), pts);
    }

    /**
     * What the guardian takes while the bulwark stands feeds its retribution: each blow as it got past the armour, the
     * share the bulwark's own resistance and shield of light kept off included (counting only the health lost would
     * count nothing until the shield was gone).
     */
    @SubscribeEvent
    static void bastionTakes(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player p) || p.level().isClientSide) return;
        Bastion b = BASTIONS.get(p);
        if (b == null) return;
        if (p.level().getGameTime() > b.until + 5) {
            BASTIONS.remove(p);
            return;
        }
        b.taken += event.getNewDamage() + event.getReduction(DamageContainer.Reduction.MOB_EFFECTS)
                + event.getReduction(DamageContainer.Reduction.ABSORPTION);
    }

    /** Damage the bulwark has taken so far, or -1 when none stands (tests). */
    static float bastionTaken(Player p) {
        Bastion b = BASTIONS.get(p);
        return b == null ? -1 : b.taken;
    }

    // ---------------------------------------------------------------- Branch of the World Tree (healer)

    /**
     * Verdant Blessing: roots of light run out over the ground and the World Tree rises out of it. The party heals 40%
     * of its health, most ailments wash away and regeneration follows; the roots bind every foe within 8 blocks.
     * Points: the first holds the number of allies (its x), then the allies, then the bound foes.
     */
    static List<Vec3> verdantBlessing(ServerLevel level, Player p) {
        List<Vec3> allies = new ArrayList<>();
        for (Player ally : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(10), Player::isAlive)) {
            Balance.healRaw(ally, ally.getMaxHealth() * 0.4f);
            ally.removeEffect(ModEffects.VENOM);
            ally.removeEffect(ModEffects.HEAVY_VENOM);
            ally.removeEffect(ModEffects.CRIMSON_BRAND);
            ally.removeEffect(ModEffects.FROSTBITE);
            ally.removeEffect(ModEffects.FROZEN);
            ally.removeEffect(MobEffects.POISON);
            ally.removeEffect(MobEffects.WITHER);
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 8 * 20, 1, false, true, true));
            if (ally != p && allies.size() < 12) allies.add(ally.position());
        }
        List<Vec3> bound = new ArrayList<>();
        for (LivingEntity e : ArsenalHits.foesAround(p, p.position(), 8)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3, false, true, true), p);
            ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 4f));
            if (bound.size() < 16) bound.add(e.position());
        }
        ArsenalAbilities.sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.8f);
        ArsenalAbilities.sound(level, p, SoundEvents.BEACON_POWER_SELECT, 1f, 1.4f);
        ArsenalAbilities.sound(level, p, SoundEvents.CHERRY_LEAVES_BREAK, 1.2f, 0.7f);
        ArsenalAbilities.sound(level, p, SoundEvents.ROOTED_DIRT_PLACE, 1.2f, 0.5f);
        List<Vec3> pts = new ArrayList<>();
        pts.add(new Vec3(allies.size(), 0, 0));
        pts.addAll(allies);
        pts.addAll(bound);
        return pts;
    }

    // ---------------------------------------------------------------- Starfall Longbow (ranger)

    /** A charged draw was loosed: the arrow carries a star, and the sky answers wherever it lands. */
    static void starArrow(ServerLevel level, Player p, AbstractArrow arrow) {
        arrow.getPersistentData().putBoolean(STAR_TAG, true);
        arrow.setCritArrow(true);
        ArsenalAbilities.sound(level, p, SoundEvents.TRIDENT_THROW.value(), 1.2f, 1.4f);
        ArsenalAbilities.sound(level, p, SoundEvents.AMETHYST_BLOCK_BREAK, 1f, 1.6f);
        ArsenalAbilities.send(p, Signature.STARFALL, SignatureFxPayload.IMPACT, List.of(arrow.position(), arrow.getDeltaMovement()));
    }

    @SubscribeEvent
    static void starLands(ProjectileImpactEvent event) {
        Projectile arrow = event.getProjectile();
        if (arrow.level().isClientSide || !arrow.getPersistentData().getBoolean(STAR_TAG)) return;
        arrow.getPersistentData().remove(STAR_TAG);
        if (!(arrow.getOwner() instanceof Player p) || !(arrow.level() instanceof ServerLevel level)) return;
        HitResult hit = event.getRayTraceResult();
        Vec3 at = hit instanceof EntityHitResult eh ? eh.getEntity().position() : hit.getLocation();
        starfall(level, p, at);
    }

    /**
     * Starfall: seven stars fall round where the arrow landed, one after another, and then the great star on the
     * mark itself. Beat 1's points: the mark, then where each small star falls.
     */
    static void starfall(ServerLevel level, Player p, Vec3 centre) {
        RandomSource r = level.random;
        List<Vec3> stars = new ArrayList<>();
        stars.add(centre);
        float turn = r.nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < STARS; i++) {
            float a = turn + i * Mth.TWO_PI / STARS + (r.nextFloat() - 0.5f) * 0.5f;
            double d = 1.6 + r.nextDouble() * 3.4;
            Vec3 at = centre.add(Math.cos(a) * d, 0, Math.sin(a) * d);
            stars.add(ArsenalAbilities.ground(level, at));
        }
        ArsenalAbilities.send(p, Signature.STARFALL, SignatureFxPayload.beat(1), stars);
        for (int i = 0; i < STARS; i++) {
            Vec3 at = stars.get(i + 1);
            ArsenalAbilities.later(p, level, STAR_FIRST + i * STAR_GAP, () -> {
                for (LivingEntity e : ArsenalHits.foesAround(p, at, 2.2)) ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 4f));
                ArsenalAbilities.soundAt(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 1.1f, 0.6f + level.random.nextFloat() * 0.4f);
                ArsenalAbilities.soundAt(level, at, SoundEvents.FIREWORK_ROCKET_BLAST, 0.9f, 0.8f);
            });
        }
        ArsenalAbilities.later(p, level, GREAT_STAR, () -> {
            for (LivingEntity e : ArsenalHits.foesAround(p, centre, 4)) {
                ArsenalHits.magicHit(p, e, Balance.flatDamage(p, 10f));
                Vec3 out = e.position().subtract(centre).multiply(1, 0, 1);
                if (out.lengthSqr() > 1.0e-4) out = out.normalize();
                ArsenalAbilities.knock(e, out.x * 0.7, 0.5, out.z * 0.7);
            }
            ArsenalAbilities.soundAt(level, centre, SoundEvents.TRIDENT_THUNDER.value(), 1.6f, 1.2f);
            ArsenalAbilities.soundAt(level, centre, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.1f);
            ArsenalAbilities.soundAt(level, centre, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.5f);
            ArsenalAbilities.send(p, Signature.STARFALL, SignatureFxPayload.beat(2), List.of(centre));
        });
    }

    private DivineArts() {}
}
