package com.beyondsolo.shadow;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The creature a shadow wears, made on the client to be drawn in ink ({@code client.shadow.ShadowRenderer}): any
 * creature at all, ours, vanilla's or another mod's, built from the look its shadow keeps ({@link ShadowForm#look}) so
 * it holds its bow, wears its armour, keeps its colours and its kind. Every tick it takes its shadow's place and pose
 * and runs its own tick, so what it animates by itself goes on (a bat's wings, a camel's sway, a slime's squish), with
 * its particles held back: the shadow is ink, not the creature.
 *
 * <p>Nothing a creature does here can bring the game down. A kind that can't be made on the client, or whose drawing
 * fails ({@link #broke}, from {@code client.shadow.ShadowDraw}), is drawn from then on as a plain stand-in of its size
 * and way of moving: a zombie or a spider, a phantom or a vex, a dolphin. One whose tick fails is only posed.</p>
 */
public final class ShadowLook {
    /** Kinds that failed to be made or drawn: drawn as a stand-in from now on (for the rest of the session). */
    private static final Set<EntityType<?>> BROKEN = ConcurrentHashMap.newKeySet();
    /** Kinds whose own tick failed: posed only. */
    private static final Set<EntityType<?>> STILL = ConcurrentHashMap.newKeySet();

    /** The creature's kind (null: one the game no longer knows). */
    private final @Nullable EntityType<?> kind;
    private final LivingEntity body;
    /** How much larger than itself the body is drawn to fill its shadow (before the shadow's own fit and rank). */
    private final float scale;
    private final boolean standIn;
    private final boolean swims;

    private ShadowLook(@Nullable EntityType<?> kind, LivingEntity body, float scale, boolean standIn, boolean swims) {
        this.kind = kind;
        this.body = body;
        this.scale = scale;
        this.standIn = standIn;
        this.swims = swims;
        // a fish out of water lies on its side: a swimmer's shadow is drawn as if in water wherever it swims
        if (swims) body.wasTouchingWater = true;
    }

    /** The creature to draw. */
    public LivingEntity body() { return body; }

    /** How much larger than itself it is drawn (1, but for a stand-in). */
    public float scale() { return scale; }

    /** A stand-in, not the creature itself. */
    public boolean standIn() { return standIn; }

    /** Its kind failed to draw since this was made: a stand-in should take its place. */
    public boolean stale() {
        return !standIn && kind != null && BROKEN.contains(kind);
    }

    /**
     * Makes what a shadow of {@code source} (an entity type id) is drawn as: the creature, from {@code form}'s look,
     * or a stand-in. {@code bossRank} gives a boss without a look (raised before looks were kept) its rank's look.
     */
    public static ShadowLook make(Level level, String source, @Nullable ShadowForm form, int bossRank) {
        EntityType<?> kind = EntityType.byString(source).orElse(null);
        boolean swims = form != null && form.move() == ShadowForm.Movement.SWIM;
        if (kind != null && kind != ShadowModule.SHADOW.get() && !BROKEN.contains(kind)) {
            LivingEntity body = build(level, kind, form, bossRank);
            if (body != null) return new ShadowLook(kind, body, 1f, false, swims);
            broke(kind, null);
        }
        return standIn(level, kind, form);
    }

    private static @Nullable LivingEntity build(Level level, EntityType<?> kind, @Nullable ShadowForm form, int bossRank) {
        Entity made = null;
        try {
            made = kind.create(level);
            if (!(made instanceof LivingEntity body)) {
                if (made != null) made.discard();
                return null;
            }
            CompoundTag look = form == null ? new CompoundTag() : form.look();
            if (!look.isEmpty()) {
                try {
                    body.load(look.copy());
                } catch (RuntimeException | LinkageError e) {
                    // a look it can't read back: the kind's own
                    BeyondSolo.LOGGER.debug("Shadow look of {} could not be read: {}", kind, e.toString());
                }
            }
            if (body instanceof GateBossEntity boss) {
                // awake, in its rank's look (a boss raised before looks were kept: the rank it was raised at)
                PlayerRank rank = look.isEmpty() && bossRank >= 0
                        ? PlayerRank.values()[Mth.clamp(bossRank, 0, PlayerRank.values().length - 1)] : boss.rank();
                boss.displayAs(rank, boss.kind());
            }
            AttributeInstance size = body.getAttribute(Attributes.SCALE);
            if (size != null && form != null) {
                size.removeModifiers();
                size.setBaseValue(form.scale());
            }
            settle(body);
            return body;
        } catch (RuntimeException | LinkageError e) {
            BeyondSolo.LOGGER.warn("A shadow of {} can't be drawn as itself, using a stand-in: {}", kind, e.toString());
            if (made != null) made.discard();
            return null;
        }
    }

    /** What every body it wears is: silent, mindless, pushes nothing, burns with nothing. */
    private static void settle(LivingEntity body) {
        body.setSilent(true);
        body.noPhysics = true;
        body.clearFire();
        if (body instanceof Mob m) m.setNoAi(true);
    }

    /**
     * A plain creature of the shadow's size and way of moving, for a kind that can't be drawn as itself: a zombie, or a
     * spider for a creature wider than it is tall; a phantom, or a vex for a tall flier; a dolphin for a swimmer.
     */
    private static ShadowLook standIn(Level level, @Nullable EntityType<?> kind, @Nullable ShadowForm form) {
        ShadowForm f = form != null ? form : ShadowForm.UNKNOWN;
        boolean wide = f.width() > f.height();
        EntityType<? extends LivingEntity> as = switch (f.move()) {
            case FLY -> wide ? EntityType.PHANTOM : EntityType.VEX;
            case SWIM -> EntityType.DOLPHIN;
            case WALK -> wide ? EntityType.SPIDER : EntityType.ZOMBIE;
        };
        LivingEntity body = as.create(level);
        if (body == null) body = EntityType.ZOMBIE.create(level);
        settle(body);
        EntityDimensions d = body.getType().getDimensions();
        float scale = Math.min(f.width() / Math.max(0.1f, d.width()), f.height() / Math.max(0.1f, d.height()));
        return new ShadowLook(kind, body, Mth.clamp(scale, 0.1f, 20f), true, f.move() == ShadowForm.Movement.SWIM);
    }

    /**
     * A kind failed to be made or drawn (once is enough): told in the log once, then drawn as a stand-in. {@code why}
     * null: it was told already.
     */
    public static void broke(@Nullable EntityType<?> kind, @Nullable Throwable why) {
        if (kind == null || !BROKEN.add(kind) || why == null) return;
        BeyondSolo.LOGGER.warn("Shadows of {} are drawn as a stand-in: its own drawing failed", EntityType.getKey(kind), why);
    }

    // ================================================================ each tick

    /** Takes its shadow's place and pose and runs its own tick (client side, each tick of its shadow). */
    public void follow(LivingEntity shadow) {
        LivingEntity l = body;
        float flap = l instanceof EnderDragon d ? d.flapTime : 0;
        l.setOldPosAndRot();
        l.setPos(shadow.getX(), shadow.getY(), shadow.getZ());
        l.tickCount = shadow.tickCount;
        copyPose(shadow, l);
        boolean ticked = false;
        // a swimmer out of water would flop and turn on its side; a dragon without a mind stops its wings and its
        // neck: those are posed by hand below
        if (!swims && !(l instanceof EnderDragon) && (kind == null || !STILL.contains(kind))) ticked = tick(l);
        if (!ticked) l.calculateEntityAnimation(l instanceof net.minecraft.world.entity.animal.FlyingAnimal);
        copyPose(shadow, l);
        l.setOnGround(shadow.onGround());
        if (l instanceof Mob m && shadow instanceof Mob s) m.setAggressive(s.isAggressive());
        if (swims) l.wasTouchingWater = true;
        if (l instanceof EnderDragon d) dragon(d, flap);
    }

    /** Runs the body's own tick with its particles held back; a kind whose tick fails is only posed from then on. */
    private boolean tick(LivingEntity l) {
        try {
            // (client only: this class is used on the client alone)
            com.beyondsolo.client.shadow.ShadowDraw.quietly(l::tick);
            return true;
        } catch (RuntimeException | LinkageError e) {
            if (kind != null && STILL.add(kind)) {
                BeyondSolo.LOGGER.warn("Shadows of {} are posed only: its own tick failed", EntityType.getKey(kind), e);
            }
            return false;
        } finally {
            // something in its tick took it out of the world it was never in: it is still drawn
            if (l.isRemoved()) l.revive();
        }
    }

    private static void copyPose(LivingEntity from, LivingEntity to) {
        to.setYRot(from.getYRot());
        to.yRotO = from.yRotO;
        to.setXRot(from.getXRot());
        to.xRotO = from.xRotO;
        to.yBodyRot = from.yBodyRot;
        to.yBodyRotO = from.yBodyRotO;
        to.yHeadRot = from.yHeadRot;
        to.yHeadRotO = from.yHeadRotO;
        to.attackAnim = from.attackAnim;
        to.oAttackAnim = from.oAttackAnim;
        to.swinging = from.swinging;
        to.swingTime = from.swingTime;
        to.swingingArm = from.swingingArm;
        to.hurtTime = from.hurtTime;
        to.hurtDuration = from.hurtDuration;
    }

    /**
     * A dragon's wings beat and its neck and tail follow where it has been: Minecraft keeps both only for a dragon with
     * a mind of its own, so a dragon's shadow keeps them here, the same way.
     */
    private static void dragon(EnderDragon d, float flap) {
        double dx = d.getX() - d.xo, dy = d.getY() - d.yo, dz = d.getZ() - d.zo;
        float beat = 0.2f / ((float) Math.sqrt(dx * dx + dz * dz) * 10f + 1f) * (float) Math.pow(2.0, Mth.clamp(dy, -1, 1));
        d.oFlapTime = flap;
        d.flapTime = flap + Math.max(0.05f, beat);
        if (d.posPointer < 0) {
            for (double[] p : d.positions) {
                p[0] = d.getYRot();
                p[1] = d.getY();
            }
        }
        if (++d.posPointer == d.positions.length) d.posPointer = 0;
        d.positions[d.posPointer][0] = d.getYRot();
        d.positions[d.posPointer][1] = d.getY();
    }
}
