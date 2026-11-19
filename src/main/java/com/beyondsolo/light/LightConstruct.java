package com.beyondsolo.light;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * One of the light's greater shapes, made real for a moment by a ruler's power ({@link com.beyondsolo.skill.RulerPowers}):
 * a lance of judgement, the Blade of Heaven, the Hand of Heaven, Daybreak's sun, a feather of the choir, the choir's
 * wings. Only something to see: what it does is done by the power that made it, on the server's clock, so it never
 * touches anything, never saves and goes when its time is up.
 *
 * <p>Where it is drawn is worked out from the world's clock ({@link #age}), so it comes down the same for a hunter who
 * only now came near: {@link #fall} ticks dropping out of the sky onto where it stands (or rising, for the sun), then
 * standing until {@link #life} runs out, fading over its last {@link #FADE} ticks. One that follows someone (the choir)
 * goes where they go. Models and animations ({@code animation.beyondsolo.light_<form>.}) come from
 * {@code tools/gen_light_assets.py}.</p>
 */
public class LightConstruct extends Entity implements GeoEntity {
    /** What shape the light takes. */
    public enum Form {
        /** Judgement's lance, onto each judged foe. */
        SPEAR(18f),
        /** The Verdict's Blade of Heaven, as tall as a tower. */
        BLADE(46f),
        /** The Unseen Hand's Hand of Heaven: it comes down open, closes and slams. */
        HAND(26f),
        /** Dawn's Daybreak: a sun that rises over the fight. */
        SUN(0f),
        /** A feather of the choir, falling like a spear. */
        FEATHER(14f),
        /** The choir of wings behind a ruler of Wings. */
        CHOIR(0f);

        /** How high over its spot it starts when it falls. */
        public final float drop;

        Form(float drop) {
            this.drop = drop;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public static Form byIndex(int i) {
            Form[] all = values();
            return all[Mth.clamp(i, 0, all.length - 1)];
        }
    }

    /** Ticks it fades out over at the end of its life. */
    public static final int FADE = 14;

    private static final EntityDataAccessor<Byte> FORM = SynchedEntityData.defineId(LightConstruct.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(LightConstruct.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(LightConstruct.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> FALL = SynchedEntityData.defineId(LightConstruct.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(LightConstruct.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> BORN = SynchedEntityData.defineId(LightConstruct.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> FOLLOW = SynchedEntityData.defineId(LightConstruct.class, EntityDataSerializers.INT);

    private static final String BASE = "animation.beyondsolo.light_";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LightConstruct(EntityType<? extends LightConstruct> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    // ================================================================ making one

    /** A shape that falls out of the sky onto {@code at} over {@code fall} ticks and is gone after {@code life}. */
    public static @Nullable LightConstruct drop(ServerLevel level, Form form, Vec3 at, int color, float scale, int fall, int life) {
        return spawn(level, form, at, color, scale, fall, life, null);
    }

    /** A shape that rises at {@code at} (the sun: up over it) and stays for {@code life} ticks. */
    public static @Nullable LightConstruct hold(ServerLevel level, Form form, Vec3 at, int color, float scale, int life) {
        return spawn(level, form, at, color, scale, 0, life, null);
    }

    /** A shape that goes where {@code owner} goes for {@code life} ticks (the choir of wings). */
    public static @Nullable LightConstruct follow(ServerLevel level, Form form, Entity owner, int color, float scale, int life) {
        return spawn(level, form, owner.position(), color, scale, 0, life, owner);
    }

    private static @Nullable LightConstruct spawn(ServerLevel level, Form form, Vec3 at, int color, float scale, int fall, int life, @Nullable Entity owner) {
        LightConstruct c = LightModule.CONSTRUCT.get().create(level);
        if (c == null) return null;
        c.moveTo(at.x, at.y, at.z, owner != null ? owner.getYRot() : level.random.nextFloat() * 360f, 0);
        c.entityData.set(FORM, (byte) form.ordinal());
        c.entityData.set(COLOR, color & 0xFFFFFF);
        c.entityData.set(SCALE, scale);
        c.entityData.set(FALL, Math.max(0, fall));
        c.entityData.set(LIFE, Math.max(fall + 1, life));
        c.entityData.set(BORN, level.getGameTime());
        c.entityData.set(FOLLOW, owner == null ? -1 : owner.getId());
        level.addFreshEntity(c);
        return c;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FORM, (byte) 0);
        builder.define(COLOR, 0xFFE9A8);
        builder.define(SCALE, 1f);
        builder.define(FALL, 0);
        builder.define(LIFE, 40);
        builder.define(BORN, 0L);
        builder.define(FOLLOW, -1);
    }

    // ================================================================ reading it

    public Form form() { return Form.byIndex(entityData.get(FORM)); }
    public int color() { return entityData.get(COLOR); }
    public float scale() { return entityData.get(SCALE); }
    public int fall() { return entityData.get(FALL); }
    public int life() { return entityData.get(LIFE); }

    /** Ticks since it was made, by the world's clock (smooth with {@code partial}). */
    public float age(float partial) {
        return Math.max(0, level().getGameTime() - entityData.get(BORN)) + partial;
    }

    /** 0 before it lands, rising to 1 as it does. */
    public float landing(float partial) {
        int f = fall();
        return f <= 0 ? 1 : Mth.clamp(age(partial) / f, 0, 1);
    }

    /** How strongly it shows: fading in over its first ticks and out over its last {@link #FADE}. */
    public float alpha(float partial) {
        float a = age(partial);
        float in = Mth.clamp(a / 4f, 0, 1);
        float out = Mth.clamp((life() - a) / FADE, 0, 1);
        return in * out;
    }

    /** The one it follows, if any. */
    public @Nullable Entity owner() {
        int id = entityData.get(FOLLOW);
        return id < 0 ? null : level().getEntity(id);
    }

    // ================================================================ its time

    @Override
    public void tick() {
        super.tick();
        Entity owner = owner();
        if (owner != null) {
            setPos(owner.getX(), owner.getY(), owner.getZ());
            setYRot(owner.getYRot());
        }
        if (!level().isClientSide && (age(0) >= life() || entityData.get(FOLLOW) >= 0 && (owner == null || !owner.isAlive()))) discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    // ================================================================ its animations

    /** Each form's animations, made once: [form][0] while it comes down, [form][1] once it has landed. */
    private static final RawAnimation[][] ANIMS = new RawAnimation[Form.values().length][2];

    static {
        for (Form f : Form.values()) {
            String name = BASE + f.id() + ".";
            ANIMS[f.ordinal()] = switch (f) {
                // the hand comes down open, closes on what is under it, then holds it
                case HAND -> new RawAnimation[]{RawAnimation.begin().thenLoop(name + "open"),
                        RawAnimation.begin().thenPlay(name + "close").thenLoop(name + "fist")};
                case SUN, CHOIR -> new RawAnimation[]{RawAnimation.begin().thenLoop(name + "idle"), RawAnimation.begin().thenLoop(name + "idle")};
                default -> new RawAnimation[]{RawAnimation.begin().thenLoop(name + "fall"),
                        RawAnimation.begin().thenPlay(name + "land").thenLoop(name + "stand")};
            };
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0,
                state -> state.setAndContinue(ANIMS[form().ordinal()][landing(0) < 1 ? 0 : 1])));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
