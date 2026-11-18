package com.beyondsolo.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.beyondsolo.skill.SkillActions.canHit;

/**
 * The shared toolkit of the subclass skill sets (one file per base class: {@link WarriorPath}, ...): the builders
 * for skills that unlock with levels after the job change, attribute passives, and the building blocks most
 * skills are made of (cones, dashes, leaps, zones, pushes and pulls).
 */
final class SubclassKit {
    // ================================================================ builders

    /** A subclass active that unlocks at a player level once the player has the subclass. */
    static Skill active(String path, Subclass sub, PlayerRank grade, int unlock, int color, float mana, int cooldown, int cooldownAtMax,
                        Skill.Action action, Skill.Args args) {
        Skill s = new Skill(path, Skill.Kind.ACTIVE, sub.base(), grade, unlock, false, color, mana, cooldown, cooldownAtMax, action, args);
        return Skills.add(s.element(sub.element()).subclass(sub));
    }

    static Skill passive(String path, Subclass sub, PlayerRank grade, int unlock, int color, Skill.Args args) {
        Skill s = new Skill(path, Skill.Kind.PASSIVE, sub.base(), grade, unlock, false, color, 0, 0, 0, null, args);
        return Skills.add(s.element(sub.element()).subclass(sub));
    }

    static Object[] a(Object... args) {
        return args;
    }

    // ================================================================ attribute passives

    private record Bonus(Skill skill, Holder<Attribute> attribute, float perLevel, AttributeModifier.Operation op, String key) {
        ResourceLocation id() {
            return BeyondSolo.id("skill/" + skill.path() + "/" + key);
        }
    }

    static final AttributeModifier.Operation ADD = AttributeModifier.Operation.ADD_VALUE;
    static final AttributeModifier.Operation MUL = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
    /** Max health passives multiply the whole pool, so they keep their share of it at every level. */
    static final AttributeModifier.Operation TOTAL = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
    private static final List<Bonus> BONUSES = new ArrayList<>();

    /** A passive that adds to an attribute with every skill level. */
    static void bonus(Skill skill, Holder<Attribute> attribute, float perLevel, AttributeModifier.Operation op, String key) {
        BONUSES.add(new Bonus(skill, attribute, perLevel, op, key));
    }

    static void applyBonuses(ServerPlayer player, SkillData data) {
        for (Bonus b : BONUSES) {
            AttributeInstance inst = player.getAttribute(b.attribute);
            if (inst == null) continue;
            int level = data.level(b.skill.path());
            if (level <= 0) inst.removeModifier(b.id());
            else inst.addOrUpdateTransientModifier(new AttributeModifier(b.id(), b.perLevel * level, b.op));
        }
    }

    // ================================================================ numbers

    static int tier(ServerPlayer p) {
        return SystemAPI.get(p).rank().tier();
    }

    static float m(int l, int t) {
        return SkillActions.mult(l, t);
    }

    /** What a stat adds to a hit: per point above the starting value. */
    static float stat(ServerPlayer p, Stat stat, float perPoint) {
        return Math.max(0, SystemAPI.get(p).stat(stat) - Stat.BASE) * perPoint;
    }

    // ================================================================ sight and sound

    static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void fx(Entity on, SkillFxPayload.Kind kind, int color, Vec3 at, float size, int duration) {
        SkillFx.play(on, kind, color, at, size, duration);
    }

    static void fxAt(ServerLevel level, SkillFxPayload.Kind kind, int color, Vec3 at, float size, int duration) {
        SubclassActions.fxAt(level, kind, color, at, size, duration);
    }

    /**
     * A tile of a block's texture standing on {@code at} for four seconds (a block marker particle): walls, pillars,
     * flowers and trees that are seen but never built, so nothing is left behind in the world.
     */
    static void marker(ServerLevel level, BlockState state, Vec3 at) {
        SkillParticles.send(level, new BlockParticleOption(ParticleTypes.BLOCK_MARKER, state), at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
    }

    // ================================================================ space

    static Vec3 flat(Vec3 v, Entity e) {
        Vec3 f = new Vec3(v.x, 0, v.z);
        return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, e.getYRot()) : f.normalize();
    }

    static Vec3 facing(ServerPlayer p) {
        return flat(p.getLookAngle(), p);
    }

    /** The ground under (or just above) a point. */
    static Vec3 ground(ServerLevel level, Vec3 at) {
        var hit = level.clip(new ClipContext(at.add(0, 1.5, 0), at.add(0, -16, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
    }

    /** Where the player is aiming on the ground, at most {@code range} away: under the creature in their sights if there is one. */
    static Vec3 aimGround(ServerPlayer p, double range) {
        LivingEntity target = SkillActions.aim(p, range, x -> canHit(p, x));
        if (target != null) return ground(p.serverLevel(), target.position());
        return ground(p.serverLevel(), SkillActions.aimPoint(p, range));
    }

    static List<LivingEntity> around(ServerPlayer p, Vec3 at, double radius, Predicate<LivingEntity> filter) {
        return SubclassActions.around(p, at, radius, filter);
    }

    /** Foes around a point. */
    static List<LivingEntity> foes(ServerPlayer p, Vec3 at, double radius) {
        return around(p, at, radius, x -> canHit(p, x));
    }

    /** Foes in front of the player: within {@code range} and inside the arc ({@code minDot} 0.3 is a wide swing). */
    static List<LivingEntity> cone(ServerPlayer p, double range, double minDot) {
        Vec3 look = facing(p);
        return around(p, p.position(), range, x -> {
            if (!canHit(p, x)) return false;
            Vec3 to = x.position().subtract(p.position()).multiply(1, 0, 1);
            return to.lengthSqr() < 1.0e-3 || to.normalize().dot(look) >= minDot;
        });
    }

    /** Foes within {@code halfWidth} of the segment from a to b. */
    static List<LivingEntity> along(ServerPlayer p, Vec3 a, Vec3 b, double halfWidth) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(halfWidth + 1), x -> canHit(p, x))) {
            AABB box = e.getBoundingBox().inflate(halfWidth);
            if (box.clip(a, b).isPresent() || box.contains(a)) out.add(e);
        }
        return out;
    }

    static List<Player> allies(ServerPlayer p, double radius) {
        List<Player> list = new ArrayList<>(p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(radius), Player::isAlive));
        if (!list.contains(p)) list.add(p);
        return list;
    }

    // ================================================================ force

    static void hit(ServerPlayer p, Skill s, LivingEntity e, float damage) {
        SkillActions.hit(p, s, e, damage, false);
    }

    /** A hit that ignores armour (spells, poison, shadow). */
    static void magic(ServerPlayer p, Skill s, LivingEntity e, float damage) {
        SkillActions.hit(p, s, e, damage, true);
    }

    /**
     * Moves the player to a spot next to a creature (behind it when {@code behind}, otherwise on the player's
     * side of it), facing it. Falls back to the near side when the spot is blocked.
     */
    static void blinkTo(ServerPlayer p, LivingEntity target, boolean behind) {
        Vec3 from = p.position();
        double gap = target.getBbWidth() / 2 + 0.9;
        Vec3 near = target.position().add(flat(from.subtract(target.position()), p).scale(gap));
        Vec3 dest = behind ? target.position().add(flat(target.getLookAngle(), target).scale(-gap)) : near;
        if (!p.level().noCollision(p, p.getBoundingBox().move(dest.subtract(from)))) dest = near;
        SubclassActions.teleport(p, dest, target);
    }

    /** Moves the player up to {@code distance} the way they face, stopping at walls. Returns where they were. */
    static Vec3 blinkForward(ServerPlayer p, double distance) {
        Vec3 from = p.position();
        Vec3 dir = facing(p);
        Vec3 dest = from;
        for (double d = distance; d >= 1; d -= 0.5) {
            Vec3 to = from.add(dir.scale(d));
            if (p.level().noCollision(p, p.getBoundingBox().move(to.subtract(from)))) {
                dest = to;
                break;
            }
        }
        p.teleportTo(p.serverLevel(), dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
        if (p.position().distanceToSqr(dest) > 0.01) p.moveTo(dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        return from;
    }

    static boolean poisoned(LivingEntity e) {
        return e.hasEffect(net.minecraft.world.effect.MobEffects.POISON) || e.hasEffect(com.beyondsolo.combat.ModEffects.VENOM)
                || e.hasEffect(com.beyondsolo.combat.ModEffects.HEAVY_VENOM);
    }

    static void launch(LivingEntity e, double up) {
        e.setDeltaMovement(e.getDeltaMovement().x * 0.4, up, e.getDeltaMovement().z * 0.4);
        e.hurtMarked = true;
    }

    /** Throws a creature away from a point. */
    static void push(LivingEntity e, Vec3 from, double strength, double up) {
        Vec3 away = flat(e.position().subtract(from), e);
        e.setDeltaMovement(away.x * strength, up, away.z * strength);
        e.hurtMarked = true;
    }

    /** Drags a creature towards a point. */
    static void pull(LivingEntity e, Vec3 to, double strength) {
        Vec3 d = to.subtract(e.position());
        double len = d.length();
        if (len < 1.2) return;
        // a creature thrown from where it stands travels about four and a half times its first tick, and one that
        // was already off the ground nearly seven times, so this lands it on the point; a weaker pull than that
        // only brings it part of the way
        Vec3 v = d.normalize().scale(Math.min(strength, len * (e.onGround() ? 0.22 : 0.145)));
        e.setDeltaMovement(v.x, Math.max(0.25, v.y + 0.2), v.z);
        e.hurtMarked = true;
    }

    static void move(ServerPlayer p, Vec3 velocity) {
        p.setDeltaMovement(velocity);
        p.hurtMarked = true;
        p.fallDistance = 0;
    }

    // ================================================================ time

    /** Runs later, if the caster is still alive and in the same world. */
    static void later(ServerPlayer p, int ticks, Runnable action) {
        ServerLevel level = p.serverLevel();
        ServerScheduler.schedule(ticks, () -> {
            if (p.isAlive() && !p.hasDisconnected() && p.level() == level) action.run();
        });
    }

    /** Runs {@code each} every {@code period} ticks for {@code ticks}, starting now. */
    static void repeat(ServerPlayer p, int ticks, int period, Runnable each) {
        for (int t = 0; t < ticks; t += period) later(p, t, each);
    }

    /**
     * Carries the player forward for {@code ticks}, calling {@code onTouch} once for each foe they pass through
     * and {@code onEnd} when they stop.
     */
    static void dash(ServerPlayer p, Vec3 dir, double distance, int ticks, double reach, Consumer<LivingEntity> onTouch, Runnable onEnd) {
        double speed = distance / ticks * 1.35;
        Set<Integer> struck = new HashSet<>();
        for (int t = 0; t < ticks; t++) {
            final boolean last = t == ticks - 1;
            later(p, t, () -> {
                p.setDeltaMovement(dir.x * speed, Math.max(p.getDeltaMovement().y, -0.05), dir.z * speed);
                p.hurtMarked = true;
                p.fallDistance = 0;
                for (LivingEntity e : foes(p, p.position(), reach)) {
                    if (struck.add(e.getId())) onTouch.accept(e);
                }
                if (last && onEnd != null) onEnd.run();
            });
        }
    }

    /** Throws the player into the air towards a point and calls {@code onLand} where they come down. */
    static void leap(ServerPlayer p, Vec3 target, double up, Consumer<Vec3> onLand) {
        Vec3 d = target.subtract(p.position()).multiply(1, 0, 1);
        double len = Math.min(d.length(), 14);
        Vec3 dir = len < 0.3 ? Vec3.ZERO : d.normalize();
        // time in the air is about 2 * up / 0.08 ticks with drag; aim to cover the distance in that time
        double airTicks = Math.max(8, up * 22);
        move(p, new Vec3(dir.x * len / airTicks * 1.25, up, dir.z * len / airTicks * 1.25));
        landWatch(p, onLand, 0, (int) (airTicks * 0.55));
    }

    private static void landWatch(ServerPlayer p, Consumer<Vec3> onLand, int ticks, int dive) {
        later(p, 1, () -> {
            p.fallDistance = 0;
            if (ticks == dive) move(p, new Vec3(p.getDeltaMovement().x, Math.min(p.getDeltaMovement().y, -1.6), p.getDeltaMovement().z));
            if ((ticks > 3 && p.onGround()) || ticks > 60) {
                onLand.accept(p.position());
                return;
            }
            landWatch(p, onLand, ticks + 1, dive);
        });
    }

    private SubclassKit() {}
}
