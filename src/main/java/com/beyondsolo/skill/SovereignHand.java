package com.beyondsolo.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.AssociationNpc;
import com.beyondsolo.guild.GuildHunterEntity;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.quest.situation.Foes;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.story.KinEntity;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sovereign Hand: the hunter's will as a hand nobody can see (the series' telekinesis). Pressed while looking at a
 * creature, the hand takes it: it is torn off the ground and held in the air in front of the hunter, kicking and
 * unable to strike, wherever they turn. Pressed again while it holds, the hand hurls it where the hunter looks: it
 * crashes into the first wall, ground or creature in its way (a wall hurts most, and whatever it strikes is hurt too).
 * Let go of, the hand slams it into the ground. A boss or anything too big is not lifted: the hand pins it down
 * instead, slowed to a crawl. Pressed at nothing, the hand shoves everything in front of the hunter away, turns arrows
 * and spells back the way the hunter looks, and draws the loot lying around to them.
 *
 * <p>The second press is the skill's recast ({@link Skill#recast()}): it costs nothing and does not wait for the
 * cooldown, which started with the grab. Server thread only.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class SovereignHand {
    /** The hold: while it lasts the skill's next press hurls what the hand holds. */
    public static final String BUFF_HOLD = "sovereign_hold";

    // ================================================================ numbers (first guesses for the balancing pass)

    /** How far away the hand can take a creature. */
    public static float reach(int l) { return 10 + 0.6f * (l - 1); }
    /** How long the hand holds before it slams what it holds down. */
    public static float holdSeconds(int l) { return 3 + 0.2f * (l - 1); }
    /** What a hurled creature takes when it crashes. */
    public static float hurlDamage(int l, int rankTier) { return 7 * SkillActions.mult(l, rankTier); }
    /** What the shove does to each creature in front. */
    public static float pushDamage(int l, int rankTier) { return 2.5f * SkillActions.mult(l, rankTier); }
    /** How long a boss stays pinned. */
    public static float pinSeconds(int l) { return 1.5f + 0.1f * (l - 1); }
    /** How far the shove reaches. */
    public static float pushRange(int l) { return 7 + 0.3f * (l - 1); }
    /** Blocks a tick a hurled creature leaves the hand with. */
    static float hurlSpeed(int l) { return 1.9f + 0.06f * (l - 1); }

    /** A wall takes a hurled creature harder than open ground. */
    static final float WALL = 1.3f;
    /** What a creature struck by a hurled one takes, of the hurl's damage. */
    static final float STRUCK = 0.8f;
    /** A hold that ran out slams its creature down for this much of the hurl's damage. */
    static final float SLAM = 0.7f;
    /** A pinned boss takes this much of the hurl's damage. */
    static final float PIN = 0.5f;
    /** How far in front of the eyes a held creature hangs (from its middle), at most and at least. */
    static final double HOLD_FAR = 5.0, HOLD_NEAR = 2.4;
    /** A hurled creature flies this long at most before the hand lets it fall where it is. */
    static final int FLIGHT_TICKS = 40;

    private static final ResourceLocation HELD_SLOW = BeyondSolo.id("sovereign_hold");
    private static final ResourceLocation PIN_SLOW = BeyondSolo.id("sovereign_pin");

    // ================================================================ what the hand is doing

    /** A creature in the hand. */
    private static final class Hold {
        final ServerPlayer holder;
        final LivingEntity body;
        final Skill skill;
        final int level;
        final long until;
        final boolean noGravity;
        int age;

        Hold(ServerPlayer holder, LivingEntity body, Skill skill, int level, long until) {
            this.holder = holder;
            this.body = body;
            this.skill = skill;
            this.level = level;
            this.until = until;
            this.noGravity = body.isNoGravity();
        }
    }

    /** A creature thrown by the hand, on its way to whatever it crashes into. */
    private static final class Flight {
        final ServerPlayer thrower;
        final LivingEntity body;
        final Skill skill;
        final int level;
        /** Of the hurl's damage: 1 thrown, {@link #SLAM} slammed down. */
        final float share;
        final java.util.Set<Integer> struck = new java.util.HashSet<>();
        Vec3 last;
        int ticks;

        Flight(ServerPlayer thrower, LivingEntity body, Skill skill, int level, float share) {
            this.thrower = thrower;
            this.body = body;
            this.skill = skill;
            this.level = level;
            this.share = share;
            this.last = body.position();
        }
    }

    /** holder -> what their hand holds */
    private static final Map<UUID, Hold> HOLDS = new HashMap<>();
    /** creature -> its flight */
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    /** pinned creature -> game time the pin ends */
    private static final Map<LivingEntity, Long> PINS = new java.util.WeakHashMap<>();

    /** What this hunter's hand holds right now, or null. */
    public static @Nullable LivingEntity held(ServerPlayer p) {
        Hold h = HOLDS.get(p.getUUID());
        return h == null ? null : h.body;
    }

    /** True while a creature is in someone's hand. */
    public static boolean isHeld(Entity e) {
        for (Hold h : HOLDS.values()) if (h.body == e) return true;
        return false;
    }

    /** True while a creature is flying from the hand. */
    public static boolean isFlying(Entity e) {
        return FLIGHTS.containsKey(e.getUUID());
    }

    /** True while a creature is pinned down. */
    public static boolean isPinned(LivingEntity e) {
        Long until = PINS.get(e);
        return until != null && until > e.level().getGameTime();
    }

    // ================================================================ the presses

    /** The first press: take the creature looked at, pin a boss, or shove what is in front. */
    public static boolean cast(ServerPlayer p, Skill s, int l) {
        let(p);
        LivingEntity target = SkillActions.aim(p, reach(l), e -> liftable(p, e));
        if (target == null) return shove(p, s, l);
        if (heavy(target)) return pin(p, s, l, target);
        grab(p, s, l, target);
        return true;
    }

    /** The second press while the hand holds: hurl it. False when the hand holds nothing (an ordinary cast then). */
    public static boolean hurl(ServerPlayer p, Skill s, int l) {
        Hold h = HOLDS.get(p.getUUID());
        if (h == null) return false;
        HOLDS.remove(p.getUUID());
        LivingEntity e = h.body;
        free(h);
        SkillAPI.clearBuff(p, BUFF_HOLD);
        if (!e.isAlive() || e.level() != p.level()) return true;
        // where the hunter looks: the point under the crosshair if there is one close enough, else straight ahead
        Vec3 from = e.position().add(0, e.getBbHeight() * 0.5, 0);
        Vec3 aim = SkillActions.aimPoint(p, 40);
        Vec3 dir = aim.subtract(from);
        if (dir.lengthSqr() < 1.0) dir = p.getLookAngle();
        dir = dir.normalize();
        float speed = hurlSpeed(l);
        e.setDeltaMovement(dir.x * speed, dir.y * speed + 0.12, dir.z * speed);
        e.hurtMarked = true;
        e.hasImpulse = true;
        e.fallDistance = 0;
        slow(e, HELD_SLOW, true);
        FLIGHTS.put(e.getUUID(), new Flight(p, e, s, l, 1));
        // the throw first: the first effect of a press is the one the hunter's arm acts out
        fx(e, SkillFxPayload.Kind.HURL, from.add(dir.scale(speed * 6)), Math.max(0.5f, e.getBbWidth()), FLIGHT_TICKS);
        fx(e, SkillFxPayload.Kind.GRASP, holderOf(p), 0, 0);
        sound(p.serverLevel(), from, SoundEvents.WIND_CHARGE_THROW, 1f, 0.6f);
        sound(p.serverLevel(), from, SoundEvents.TRIDENT_RIPTIDE_2.value(), 0.8f, 0.8f);
        return true;
    }

    private static void grab(ServerPlayer p, Skill s, int l, LivingEntity e) {
        e.stopRiding();
        e.ejectPassengers();
        int ticks = Math.round(holdSeconds(l) * 20);
        Hold h = new Hold(p, e, s, l, p.level().getGameTime() + ticks);
        HOLDS.put(p.getUUID(), h);
        FLIGHTS.remove(e.getUUID());
        e.setNoGravity(true);
        slow(e, HELD_SLOW, true);
        if (e instanceof Mob m) {
            m.getNavigation().stop();
            m.setTarget(null);
        }
        e.setDeltaMovement(0, 0.35, 0);
        e.hurtMarked = true;
        SkillAPI.setBuff(p, BUFF_HOLD, ticks);
        fx(e, SkillFxPayload.Kind.GRASP, holderOf(p), grip(e), ticks);
        sound(p.serverLevel(), e.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.9f, 1.5f);
        sound(p.serverLevel(), e.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.7f, 1.3f);
    }

    /** A boss or a creature too big to lift: pressed down where it stands, slowed to a crawl, hurt a little. */
    private static boolean pin(ServerPlayer p, Skill s, int l, LivingEntity e) {
        int ticks = Math.round(pinSeconds(l) * 20);
        SkillActions.hit(p, s, e, hurlDamage(l, SystemAPI.get(p).rank().tier()) * PIN, false);
        slow(e, PIN_SLOW, true);
        PINS.put(e, p.level().getGameTime() + ticks);
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x * 0.2, Math.min(v.y, -0.6), v.z * 0.2);
        e.hurtMarked = true;
        fx(e, SkillFxPayload.Kind.GRASP, holderOf(p), -grip(e), ticks);
        sound(p.serverLevel(), e.position(), SoundEvents.MACE_SMASH_GROUND_HEAVY, 1f, 0.6f);
        sound(p.serverLevel(), e.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.9f, 0.8f);
        return true;
    }

    /** The hand pressed at nothing: a wave of force in front of the hunter. */
    private static boolean shove(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        double range = pushRange(l);
        Vec3 look = p.getLookAngle();
        Vec3 flat = SubclassKit.facing(p);
        float dmg = pushDamage(l, SystemAPI.get(p).rank().tier());
        for (LivingEntity e : SubclassKit.cone(p, range, 0.45)) {
            if (!liftable(p, e) && !heavy(e)) continue;
            SkillActions.hit(p, s, e, dmg, false);
            boolean big = heavy(e);
            SubclassKit.push(e, p.position(), big ? 0.35 : 1.6, big ? 0.05 : 0.45);
        }
        // arrows and spells coming at the hunter turn round and fly the way they look
        Vec3 eye = p.getEyePosition();
        for (Projectile pr : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(range), pr -> pr.getOwner() != p)) {
            Vec3 v = pr.getDeltaMovement();
            if (v.lengthSqr() < 0.01) continue; // lying in the ground
            Vec3 to = pr.position().subtract(eye);
            if (to.lengthSqr() > 1.0e-3 && to.normalize().dot(look) < 0.35) continue;
            double speed = Math.max(1.2, v.length());
            if (pr.deflect(ProjectileDeflection.AIM_DEFLECT, p, p, true)) {
                pr.setDeltaMovement(look.scale(speed));
                pr.hurtMarked = true;
            }
        }
        // loot lying about drifts to the hunter
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(range + 4), ItemEntity::isAlive)) {
            Vec3 d = p.position().add(0, 0.6, 0).subtract(item.position());
            double len = d.length();
            if (len < 1.5) continue;
            Vec3 v = d.normalize().scale(Math.min(0.9, 0.15 + len * 0.09));
            item.setDeltaMovement(v.x, v.y + 0.25, v.z);
            item.hurtMarked = true;
        }
        fx(p, SkillFxPayload.Kind.FORCE, eye.add(flat.scale(range)), (float) range, 0);
        sound(level, p.position(), SoundEvents.WIND_CHARGE_BURST.value(), 1f, 0.7f);
        sound(level, p.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.35f, 1.7f);
        return true;
    }

    // ================================================================ every tick

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (!HOLDS.isEmpty()) tickHolds();
        if (!FLIGHTS.isEmpty()) tickFlights();
        if (!PINS.isEmpty()) tickPins();
    }

    private static void tickHolds() {
        List<Hold> slam = new ArrayList<>();
        Iterator<Hold> it = HOLDS.values().iterator();
        while (it.hasNext()) {
            Hold h = it.next();
            ServerPlayer p = h.holder;
            LivingEntity e = h.body;
            boolean gone = !e.isAlive() || e.isRemoved() || !p.isAlive() || p.isRemoved() || p.hasDisconnected() || e.level() != p.level()
                    || e.distanceTo(p) > reach(h.level) + 8;
            if (gone) {
                it.remove();
                free(h);
                SkillAPI.clearBuff(p, BUFF_HOLD);
                fx(e, SkillFxPayload.Kind.GRASP, holderOf(p), 0, 0);
                continue;
            }
            if (p.level().getGameTime() >= h.until) {
                it.remove();
                slam.add(h);
                continue;
            }
            h.age++;
            // the hand keeps it hanging in front of the hunter's eyes, wherever they look
            Vec3 eye = p.getEyePosition();
            double half = e.getBbWidth() * 0.5;
            double d = Mth.clamp(eye.distanceTo(e.position().add(0, e.getBbHeight() * 0.5, 0)), HOLD_NEAR + half, HOLD_FAR + half);
            Vec3 point = eye.add(p.getLookAngle().scale(d)).subtract(0, e.getBbHeight() * 0.5, 0);
            // never pressed into the ground at the hunter's feet
            point = new Vec3(point.x, Math.max(point.y, p.getY() + 0.4), point.z);
            point = point.add(0, Math.sin((h.age + e.getId()) * 0.25) * 0.06, 0);
            Vec3 v = point.subtract(e.position()).scale(h.age < 6 ? 0.2 : 0.38);
            if (v.length() > 1.3) v = v.normalize().scale(1.3);
            e.setDeltaMovement(v);
            e.hurtMarked = true;
            e.fallDistance = 0;
            if (e instanceof Mob m) {
                m.getNavigation().stop();
                if (m.getTarget() != null) m.setTarget(null);
            }
        }
        for (Hold h : slam) slamDown(h);
    }

    /** The hold ran out: the hand slams what it held into the ground. */
    private static void slamDown(Hold h) {
        LivingEntity e = h.body;
        free(h);
        SkillAPI.clearBuff(h.holder, BUFF_HOLD);
        fx(e, SkillFxPayload.Kind.GRASP, holderOf(h.holder), 0, 0);
        if (!e.isAlive()) return;
        e.setDeltaMovement(0, -1.9, 0);
        e.hurtMarked = true;
        slow(e, HELD_SLOW, true);
        FLIGHTS.put(e.getUUID(), new Flight(h.holder, e, h.skill, h.level, SLAM));
        sound(h.holder.serverLevel(), e.position(), SoundEvents.MACE_SMASH_AIR, 0.9f, 0.7f);
    }

    private static void tickFlights() {
        Iterator<Flight> it = FLIGHTS.values().iterator();
        List<Runnable> impacts = new ArrayList<>();
        while (it.hasNext()) {
            Flight f = it.next();
            LivingEntity e = f.body;
            ServerPlayer p = f.thrower;
            f.ticks++;
            if (!e.isAlive() || e.isRemoved() || p.isRemoved() || e.level() != p.level()) {
                it.remove();
                slow(e, HELD_SLOW, false);
                continue;
            }
            e.fallDistance = 0;
            Vec3 now = e.position();
            Vec3 moved = now.subtract(f.last);
            // whatever it passed through on the way (a fast body can cross a creature between two ticks)
            AABB swept = e.getBoundingBox().minmax(e.getBoundingBox().move(moved.scale(-1))).inflate(0.3);
            LivingEntity struck = null;
            for (LivingEntity o : e.level().getEntitiesOfClass(LivingEntity.class, swept,
                    o -> o != e && o != p && !f.struck.contains(o.getId()) && SkillActions.canHit(p, o) && !(o instanceof ShadowEntity))) {
                struck = o;
                break;
            }
            f.last = now;
            boolean wall = e.horizontalCollision;
            boolean ground = f.ticks > 1 && (e.onGround() || e.verticalCollisionBelow);
            boolean spent = f.ticks > FLIGHT_TICKS || f.ticks > 4 && moved.lengthSqr() < 0.01;
            if (struck == null && !wall && !ground && !spent) continue;
            it.remove();
            final LivingEntity hitBody = struck;
            final Vec3 dir = moved.lengthSqr() > 1.0e-4 ? moved.normalize() : new Vec3(0, -1, 0);
            impacts.add(() -> crash(f, hitBody, wall, ground, spent && hitBody == null && !wall && !ground, dir));
        }
        impacts.forEach(Runnable::run);
    }

    /** A hurled or slammed creature meets a wall, the ground or another creature. */
    private static void crash(Flight f, @Nullable LivingEntity struck, boolean wall, boolean ground, boolean nothing, Vec3 dir) {
        LivingEntity e = f.body;
        ServerPlayer p = f.thrower;
        slow(e, HELD_SLOW, false);
        if (nothing) {
            fx(e, SkillFxPayload.Kind.HURL, e.position(), 0, 0);
            return;
        }
        int tier = SystemAPI.get(p).rank().tier();
        float dmg = hurlDamage(f.level, tier) * f.share * (wall ? WALL : 1);
        Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
        Vec3 v = e.getDeltaMovement();
        SkillActions.hit(p, f.skill, e, dmg, false);
        // it stops dead against what it hit, a little bounce back off a wall
        e.setDeltaMovement(wall ? dir.x * -0.25 : v.x * 0.2, Math.max(0.1, v.y * 0.1), wall ? dir.z * -0.25 : v.z * 0.2);
        e.hurtMarked = true;
        e.fallDistance = 0;
        if (struck != null) {
            float hit = hurlDamage(f.level, tier) * f.share * STRUCK;
            SkillActions.hit(p, f.skill, struck, hit, false);
            boolean big = heavy(struck);
            struck.setDeltaMovement(dir.x * (big ? 0.3 : 1.1), big ? 0.05 : 0.4, dir.z * (big ? 0.3 : 1.1));
            struck.hurtMarked = true;
        }
        ServerLevel level = p.serverLevel();
        fx(e, SkillFxPayload.Kind.HURL, at, Math.max(0.6f, e.getBbWidth()) * (wall ? 1.3f : 1f), 0);
        SkillParticles.send(level, net.minecraft.core.particles.ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        SoundEvent thud = wall || f.share < 1 ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND;
        sound(level, at, thud, 1f, wall ? 0.75f : 0.9f);
        if (ground || f.share < 1) {
            SkillFx.play(e, SkillFxPayload.Kind.CRACK, 0x9CB8FF, e.position(), 1.6f + e.getBbWidth(), 0);
        }
    }

    private static void tickPins() {
        Iterator<Map.Entry<LivingEntity, Long>> it = PINS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Long> en = it.next();
            LivingEntity e = en.getKey();
            if (!e.isAlive() || e.isRemoved() || e.level().getGameTime() >= en.getValue()) {
                it.remove();
                slow(e, PIN_SLOW, false);
                continue;
            }
            // pressed down: no jumping out from under the hand
            Vec3 v = e.getDeltaMovement();
            if (v.y > 0) {
                e.setDeltaMovement(v.x, 0, v.z);
                e.hurtMarked = true;
            }
        }
    }

    // ================================================================ held creatures can't strike

    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        Entity by = event.getSource().getEntity();
        if (by != null && !HOLDS.isEmpty() && isHeld(by)) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onExplosion(ExplosionEvent.Start event) {
        Entity by = event.getExplosion().getDirectSourceEntity();
        if (by != null && !HOLDS.isEmpty() && isHeld(by)) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        HOLDS.clear();
        FLIGHTS.clear();
        PINS.clear();
    }

    // ================================================================ helpers

    /** Lets go of whatever this hunter's hand holds (another cast, a test), without slamming it. */
    public static void let(ServerPlayer p) {
        Hold h = HOLDS.remove(p.getUUID());
        if (h == null) return;
        free(h);
        SkillAPI.clearBuff(p, BUFF_HOLD);
        fx(h.body, SkillFxPayload.Kind.GRASP, holderOf(p), 0, 0);
    }

    /** Gives a held creature its weight and its legs back. */
    private static void free(Hold h) {
        h.body.setNoGravity(h.noGravity);
        slow(h.body, HELD_SLOW, false);
        h.body.fallDistance = 0;
    }

    /** What the hand may take or shove: whatever a skill may strike, but never shadows, hired hunters or people. */
    static boolean liftable(ServerPlayer p, LivingEntity e) {
        return SkillActions.canHit(p, e) && !(e instanceof ShadowEntity) && !(e instanceof KinEntity) && !(e instanceof GuildHunterEntity)
                && !(e instanceof AssociationNpc) && !isHeld(e);
    }

    /** Too big or too strong to lift: bosses, and anything wider than two blocks or taller than three. */
    static boolean heavy(LivingEntity e) {
        return Foes.isBoss(e) || e.getBbWidth() > 2.0f || e.getBbHeight() > 3.2f;
    }

    /** How wide the hand's grip is drawn round a creature. */
    private static float grip(LivingEntity e) {
        return Math.max(0.45f, Math.max(e.getBbWidth() * 0.75f, e.getBbHeight() * 0.45f));
    }

    /** Puts a creature's legs (and wings) out of use, or gives them back. */
    private static void slow(LivingEntity e, ResourceLocation id, boolean on) {
        for (var attr : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
            AttributeInstance inst = e.getAttribute(attr);
            if (inst == null) continue;
            inst.removeModifier(id);
            if (on) inst.addTransientModifier(new AttributeModifier(id, id == PIN_SLOW ? -0.85 : -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** An effect of the hand, skinned as raw arcane force whoever is near. */
    private static void fx(Entity on, SkillFxPayload.Kind kind, Vec3 at, float size, int duration) {
        SkillFx.note(kind, duration);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(on, new SkillFxPayload(on.getId(), kind, Skills.SOVEREIGN_HAND.color() & 0xFFFFFF,
                (float) at.x, (float) at.y, (float) at.z, size, duration, SkillElement.ARCANE.ordinal()));
    }

    /** A {@link SkillFxPayload.Kind#GRASP}'s point: the holder's entity id, split so a float carries it exactly. */
    private static Vec3 holderOf(ServerPlayer p) {
        return new Vec3(p.getId() >>> 12, p.getId() & 0xFFF, 0);
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private SovereignHand() {}
}
