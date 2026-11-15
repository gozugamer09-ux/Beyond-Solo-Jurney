package com.beyondsolo.client.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.network.SignatureFxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The signature arts as the world sees them. Every art has its own body animation ({@link BodyAnimator}), its own
 * wind-up, its own strike (drawn with light, crystal faces and particles), its own camera shake and its own screen
 * effect for the wielder. Nothing is shared between arts beyond the drawing helpers at the bottom. Magic circles
 * and auras are kept for the spells (the storm sigil, the healer's circle) and the guardian's dome; weapon arts
 * are told by the body and the strike alone, with light particle counts.
 *
 * <ul>
 *   <li>Infernal Rend: the arms fling up and a fan of flame tongues flares from the blade.</li>
 *   <li>Absolute Zero: the lance strikes the ground and rings of ice spikes burst out of it.</li>
 *   <li>Heaven's Verdict: a storm sigil gathers overhead, then columns of light fall on every target.</li>
 *   <li>Cataclysm Cleave: a crescent of fire sweeps ahead and a fissure erupts in flame columns.</li>
 *   <li>Dragon's Wrath: a crimson afterimage tears along the dash and X-shaped wounds open behind it.</li>
 *   <li>Devouring Star: a star is gathered at the staff, fired as a beam, collapses inward and bursts.</li>
 *   <li>Colossus Stance: the ground cracks under a slam, shockwaves roll out and a golden dome rises.</li>
 *   <li>Verdant Blessing: motes rise to the branch and a giant flower of light unfolds on the ground.</li>
 *   <li>Starfall: a star streaks down out of the sky onto the arrow's mark.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class SignatureFx {
    static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");
    static final ResourceLocation BURST = BeyondSolo.id("textures/effect/burst.png");
    static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    static final ResourceLocation SIGIL = BeyondSolo.id("textures/effect/brand_sigil.png");
    static final ResourceLocation STREAK = BeyondSolo.id("textures/effect/streak.png");
    static final ResourceLocation SLASH = BeyondSolo.id("textures/effect/slash.png");
    static final ResourceLocation VORTEX = BeyondSolo.id("textures/effect/vortex.png");
    private static final ResourceLocation[] TEXTURES = {GLOW, RING, BURST, CIRCLE, SIGIL, STREAK, SLASH, VORTEX};

    private static final class Fx {
        final int entityId;
        final Signature sig;
        final float start;
        final long seed;
        /** Game time of the strike, or -1 while winding up. */
        float impact = -1;
        List<Vec3> points = List.of();
        /** Where the wielder stood and faced at the strike. */
        Vec3 at;
        float yaw;

        Fx(int entityId, Signature sig, float start, Vec3 at, float yaw) {
            this.entityId = entityId;
            this.sig = sig;
            this.start = start;
            this.at = at;
            this.yaw = yaw;
            this.seed = RANDOM.nextLong();
        }
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    // camera shake and the wielder's screen effect (real time, so they also play while the game lags)
    private static double shakeAt = -10;
    private static float shakeStrength, shakeLength;
    private static int shakeStyle;
    private static double screenAt = -10;
    private static Signature screenSig;
    private static double chargeAt = -10;
    private static Signature chargeSig;
    private static float raysTurn;

    private static float now(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    // ------------------------------------------------------------------ events

    public static void play(SignatureFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        boolean self = e != null && e == mc.player;
        if (p.phase() == SignatureFxPayload.WINDUP) {
            if (e == null) return;
            ACTIVE.add(new Fx(p.entityId(), p.signature(), now(0), e.position(), e.getYRot()));
            if (ACTIVE.size() > 32) ACTIVE.remove(0);
            if (e instanceof AbstractClientPlayer player) BodyAnimator.play(player, p.signature().key());
            if (self) {
                chargeAt = Anim.now();
                chargeSig = p.signature();
            }
            return;
        }
        Fx fx = null;
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Fx f = ACTIVE.get(i);
            if (f.entityId == p.entityId() && f.sig == p.signature() && f.impact < 0) {
                fx = f;
                break;
            }
        }
        if (fx == null) {
            // no wind-up seen (the bow's passive art, or the wielder just came into view)
            Vec3 at = e != null ? e.position() : p.points().isEmpty() ? null : p.points().get(0);
            if (at == null) return;
            fx = new Fx(p.entityId(), p.signature(), now(0), at, e != null ? e.getYRot() : 0);
            ACTIVE.add(fx);
        }
        fx.impact = now(0);
        fx.points = p.points();
        if (e != null) {
            fx.at = e.position();
            fx.yaw = e.getYRot();
        }
        if (fx.sig == Signature.DRAGONS_WRATH && fx.points.size() >= 2) fx.at = fx.points.get(1);
        impactParticles(mc.level, fx, self && mc.options.getCameraType().isFirstPerson());
        shakeFor(mc, fx, self);
        if (self) {
            screenAt = Anim.now();
            screenSig = fx.sig;
            chargeAt = -10;
        }
    }

    /** How hard the camera shakes: full for the wielder, fading with distance for everyone else. */
    private static void shakeFor(Minecraft mc, Fx fx, boolean self) {
        if (mc.player == null) return;
        float strength = switch (fx.sig) {
            case INFERNAL_REND -> 0.5f;
            case ABSOLUTE_ZERO -> 0.9f;
            case HEAVENS_VERDICT -> 1.4f;
            case CATACLYSM_CLEAVE -> 1.3f;
            case DRAGONS_WRATH -> 0.7f;
            case DEVOURING_STAR -> 1.1f;
            case COLOSSUS_STANCE -> 1.8f;
            case VERDANT_BLESSING -> 0f;
            case STARFALL -> 0.6f;
        };
        Vec3 source = fx.sig == Signature.DEVOURING_STAR && fx.points.size() >= 2 ? fx.points.get(1)
                : fx.sig == Signature.STARFALL && !fx.points.isEmpty() ? fx.points.get(0) : fx.at;
        float distance = (float) mc.player.position().distanceTo(source);
        float falloff = self && fx.sig != Signature.DEVOURING_STAR && fx.sig != Signature.STARFALL ? 1f : Mth.clamp(1 - distance / 24f, 0, 1);
        float s = strength * falloff;
        if (s <= 0.05f || s < shakeLeft()) return;
        shakeAt = Anim.now();
        shakeStrength = s;
        shakeLength = switch (fx.sig) {
            case COLOSSUS_STANCE, HEAVENS_VERDICT -> 0.7f;
            case DRAGONS_WRATH -> 0.3f;
            default -> 0.5f;
        };
        shakeStyle = switch (fx.sig) {
            case COLOSSUS_STANCE, ABSOLUTE_ZERO -> 1; // a slam: mostly up and down
            case CATACLYSM_CLEAVE, DRAGONS_WRATH -> 2; // a sweep: mostly side to side
            default -> 0;
        };
    }

    private static float shakeLeft() {
        float p = Anim.progress(shakeAt, shakeLength);
        return p >= 1 ? 0 : shakeStrength * (1 - p) * (1 - p);
    }

    @SubscribeEvent
    static void camera(ViewportEvent.ComputeCameraAngles event) {
        float s = shakeLeft();
        if (s <= 0.001f || !ClientConfig.SCREEN_EFFECTS.get()) return;
        float t = Anim.since(shakeAt) * 40f;
        float a = Mth.sin(t * 1.0f + 0.3f) * 0.6f + Mth.sin(t * 1.9f + 2.1f) * 0.4f;
        float b = Mth.sin(t * 1.3f + 4.2f) * 0.6f + Mth.sin(t * 2.3f + 0.7f) * 0.4f;
        float yaw = 1.6f, pitch = 1.6f, roll = 1.2f;
        if (shakeStyle == 1) {
            yaw = 0.6f;
            pitch = 3f;
        } else if (shakeStyle == 2) {
            yaw = 3f;
            pitch = 0.6f;
            roll = 2f;
        }
        event.setYaw(event.getYaw() + a * yaw * s);
        event.setPitch(event.getPitch() + b * pitch * s);
        event.setRoll(event.getRoll() + (a - b) * 0.5f * roll * s);
    }

    // ------------------------------------------------------------------ particles

    private static double rnd(double s) {
        return (RANDOM.nextDouble() - 0.5) * 2 * s;
    }

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
    }

    private static Vec3 forward(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    /** The one-off burst of particles on the strike frame. */
    private static void impactParticles(ClientLevel level, Fx fx, boolean firstPersonSelf) {
        Vec3 at = fx.at;
        switch (fx.sig) {
            case INFERNAL_REND -> {
                if (!firstPersonSelf) for (int i = 0; i < 4; i++) level.addParticle(ParticleTypes.LAVA, at.x + rnd(0.4), at.y + 1.6, at.z + rnd(0.4), 0, 0, 0);
            }
            case ABSOLUTE_ZERO -> {
                for (int i = 0; i < 70; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2, r = 1 + RANDOM.nextDouble() * 6;
                    level.addParticle(ParticleTypes.SNOWFLAKE, at.x + Math.cos(a) * r, at.y + 0.2 + RANDOM.nextDouble() * 1.4, at.z + Math.sin(a) * r, Math.cos(a) * 0.05, 0.05, Math.sin(a) * 0.05);
                }
                for (Vec3 p : fx.points) {
                    for (int i = 0; i < 14; i++) level.addParticle(ParticleTypes.ITEM_SNOWBALL, p.x + rnd(0.4), p.y + 1 + rnd(0.6), p.z + rnd(0.4), rnd(0.15), 0.1, rnd(0.15));
                }
            }
            case HEAVENS_VERDICT -> {
                for (Vec3 p : fx.points) {
                    for (int i = 0; i < 24; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x + rnd(0.3), p.y + 0.2 + RANDOM.nextDouble() * 2, p.z + rnd(0.3), rnd(0.4), RANDOM.nextDouble() * 0.3, rnd(0.4));
                    for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.FIREWORK, p.x, p.y + 0.3, p.z, rnd(0.25), 0.2, rnd(0.25));
                    level.addParticle(ParticleTypes.FLASH, p.x, p.y + 1, p.z, 0, 0, 0);
                }
            }
            case CATACLYSM_CLEAVE -> {
                Vec3 o = fx.points.size() >= 2 ? fx.points.get(0) : at;
                Vec3 tip = fx.points.size() >= 2 ? fx.points.get(1) : at.add(forward(fx.yaw).scale(7.5));
                Vec3 dir = tip.subtract(o);
                for (int i = 0; i < 16; i++) {
                    Vec3 q = o.add(dir.scale(0.15 + RANDOM.nextDouble() * 0.85));
                    level.addParticle(ParticleTypes.FLAME, q.x + rnd(0.6), q.y + 0.2, q.z + rnd(0.6), 0, 0.12 + RANDOM.nextDouble() * 0.15, 0);
                }
                for (int i = 0; i < 5; i++) {
                    Vec3 q = o.add(dir.scale(0.2 + RANDOM.nextDouble() * 0.8));
                    level.addParticle(ParticleTypes.LAVA, q.x, q.y + 0.3, q.z, 0, 0, 0);
                }
            }
            case DRAGONS_WRATH -> {
                if (fx.points.size() >= 2) {
                    Vec3 from = fx.points.get(0), to = fx.points.get(1);
                    for (int i = 0; i <= 14; i++) {
                        Vec3 q = from.lerp(to, i / 14.0);
                        level.addParticle(dust(0xE0306E, 1.4f), q.x + rnd(0.25), q.y + 0.4 + RANDOM.nextDouble() * 1.2, q.z + rnd(0.25), 0, 0, 0);
                        if (i % 2 == 0) level.addParticle(ParticleTypes.CRIMSON_SPORE, q.x, q.y + 0.9, q.z, rnd(0.05), 0.02, rnd(0.05));
                    }
                    for (int i = 0; i < 3; i++) level.addParticle(ParticleTypes.LARGE_SMOKE, from.x + rnd(0.3), from.y + 0.6 + rnd(0.5), from.z + rnd(0.3), 0, 0.02, 0);
                }
            }
            case DEVOURING_STAR -> {
                if (fx.points.size() >= 2) {
                    Vec3 hit = fx.points.get(1);
                    for (int i = 0; i < 60; i++) level.addParticle(ParticleTypes.REVERSE_PORTAL, hit.x + rnd(2), hit.y + rnd(2), hit.z + rnd(2), 0, 0, 0);
                    level.addParticle(ParticleTypes.FLASH, hit.x, hit.y, hit.z, 0, 0, 0);
                }
            }
            case COLOSSUS_STANCE -> {
                for (int i = 0; i < 12; i++) {
                    double a = i / 12.0 * Math.PI * 2;
                    level.addParticle(ParticleTypes.POOF, at.x + Math.cos(a) * 0.8, at.y + 0.1, at.z + Math.sin(a) * 0.8, Math.cos(a) * 0.35, 0.02, Math.sin(a) * 0.35);
                }
                for (int i = 0; i < 8; i++) level.addParticle(dust(0xFFC85A, 1.6f), at.x + rnd(2.5), at.y + 0.2, at.z + rnd(2.5), 0, 0.15, 0);
            }
            case VERDANT_BLESSING -> {
                for (int i = 0; i < 50; i++) level.addParticle(ParticleTypes.CHERRY_LEAVES, at.x + rnd(3.5), at.y + 2.5 + RANDOM.nextDouble() * 1.5, at.z + rnd(3.5), 0, 0, 0);
                for (Vec3 p : fx.points) for (int i = 0; i < 12; i++) level.addParticle(ParticleTypes.HAPPY_VILLAGER, p.x + rnd(0.5), p.y + RANDOM.nextDouble() * 2, p.z + rnd(0.5), 0, 0, 0);
            }
            case STARFALL -> {}
        }
    }

    /** Ongoing particles: gathering during the wind-up, lingering after the strike. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        float time = now(0);
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Fx fx : ACTIVE) {
            Entity e = mc.level.getEntity(fx.entityId);
            boolean hideSelf = e == mc.player && firstPerson;
            float t = (time - fx.start) / 20f;
            float k = fx.impact < 0 ? -1 : (time - fx.impact) / 20f;
            Vec3 pos = e != null ? e.position() : fx.at;
            ClientLevel level = mc.level;
            switch (fx.sig) {
                case INFERNAL_REND -> {
                    if (k >= 0 && k < 0.5f && !hideSelf && RANDOM.nextBoolean()) {
                        level.addParticle(ParticleTypes.SMALL_FLAME, pos.x + rnd(0.6), pos.y + 2 + RANDOM.nextDouble() * 0.6, pos.z + rnd(0.6), 0, 0.05, 0);
                    }
                }
                case ABSOLUTE_ZERO -> {
                    if (k < 0) {
                        for (int i = 0; i < 4; i++) {
                            double a = RANDOM.nextDouble() * Math.PI * 2, r = 0.5 + RANDOM.nextDouble() * 2.5;
                            level.addParticle(ParticleTypes.SNOWFLAKE, pos.x + Math.cos(a) * r, pos.y + 2.4, pos.z + Math.sin(a) * r, 0, -0.08, 0);
                        }
                    } else if (k < 1.8f && RANDOM.nextFloat() < 0.6f) {
                        double a = RANDOM.nextDouble() * Math.PI * 2, r = RANDOM.nextDouble() * 7;
                        level.addParticle(ParticleTypes.WHITE_ASH, fx.at.x + Math.cos(a) * r, fx.at.y + 0.1, fx.at.z + Math.sin(a) * r, 0, 0.03, 0);
                    }
                }
                case HEAVENS_VERDICT -> {
                    if (k < 0 && e != null && !hideSelf) {
                        // sparks crawl up the raised blade
                        Vec3 hand = pos.add(forward(e.getYRot()).scale(0.25)).add(rightOf(e.getYRot()).scale(-0.35));
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, hand.x + rnd(0.1), pos.y + 1.6 + Math.min(1.2, t * 2) + RANDOM.nextDouble() * 0.6, hand.z + rnd(0.1), 0, 0.1, 0);
                    }
                    if (k < 0 && t > 0.2f) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x + Math.cos(a) * 1.8, pos.y + 6, pos.z + Math.sin(a) * 1.8, 0, 0, 0);
                    }
                }
                case CATACLYSM_CLEAVE -> {
                    if (k < 0 && e != null) {
                        // fire coils along the blade's wind-up arc, behind the wielder's right shoulder
                        float yaw = e.getYRot();
                        for (int i = 0; i < 3; i++) {
                            double a = -0.4 - RANDOM.nextDouble() * 1.6;
                            Vec3 f = forward(yaw), r = rightOf(yaw);
                            Vec3 q = pos.add(f.scale(Math.cos(a) * 1.4)).add(r.scale(-Math.sin(a) * 1.4));
                            level.addParticle(ParticleTypes.FLAME, q.x, pos.y + 1.1 + rnd(0.2), q.z, 0, 0.02, 0);
                        }
                    } else if (k >= 0 && k < 1.2f && fx.points.size() >= 2) {
                        Vec3 q = fx.points.get(0).lerp(fx.points.get(1), 0.15 + RANDOM.nextDouble() * 0.85);
                        level.addParticle(RANDOM.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMOKE, q.x + rnd(0.5), q.y + 0.2, q.z + rnd(0.5), 0, 0.1, 0);
                    }
                }
                case DRAGONS_WRATH -> {
                    if (k > 0.12f && k < 0.4f) {
                        for (int i = 2; i < fx.points.size(); i++) {
                            Vec3 p = fx.points.get(i);
                            level.addParticle(dust(0xC2185B, 1.3f), p.x + rnd(0.3), p.y + 0.6 + RANDOM.nextDouble() * 1.2, p.z + rnd(0.3), rnd(0.15), 0.05, rnd(0.15));
                        }
                    }
                }
                case DEVOURING_STAR -> {
                    if (k < 0 && e != null) {
                        Vec3 tip = staffTip(e, 1);
                        for (int i = 0; i < 4; i++) {
                            Vec3 from = tip.add(rnd(1.4), rnd(1.4), rnd(1.4));
                            Vec3 v = tip.subtract(from).scale(0.12);
                            level.addParticle(dust(0xB9A4FF, 0.8f), from.x, from.y, from.z, v.x, v.y, v.z);
                        }
                    } else if (k >= 0.25f && k < 0.5f && fx.points.size() >= 2) {
                        Vec3 hit = fx.points.get(1);
                        for (int i = 0; i < 10; i++) level.addParticle(ParticleTypes.DRAGON_BREATH, hit.x, hit.y, hit.z, rnd(0.35), rnd(0.25), rnd(0.35));
                    }
                }
                case COLOSSUS_STANCE -> {
                    // nothing extra: the slam, the cracks and the dome carry it
                }
                case VERDANT_BLESSING -> {
                    if (k < 0) {
                        double a = t * 9 + RANDOM.nextDouble(), r = 1.6 - Math.min(1.2, t * 1.5);
                        level.addParticle(ParticleTypes.END_ROD, pos.x + Math.cos(a) * r, pos.y + t * 3, pos.z + Math.sin(a) * r, 0, 0.04, 0);
                        level.addParticle(dust(0x8CE05A, 1f), pos.x + rnd(2), pos.y + 0.1, pos.z + rnd(2), 0, 0.1, 0);
                    } else if (k < 2f && RANDOM.nextBoolean()) {
                        double a = RANDOM.nextDouble() * Math.PI * 2, r = RANDOM.nextDouble() * 3.2;
                        level.addParticle(ParticleTypes.END_ROD, fx.at.x + Math.cos(a) * r, fx.at.y + 0.2, fx.at.z + Math.sin(a) * r, 0, 0.05 + RANDOM.nextDouble() * 0.05, 0);
                    }
                }
                case STARFALL -> {
                    if (k > 0.12f && k < 0.5f && !fx.points.isEmpty()) {
                        Vec3 p = fx.points.get(0);
                        level.addParticle(ParticleTypes.FIREWORK, p.x + rnd(0.3), p.y + 0.3, p.z + rnd(0.3), rnd(0.2), 0.15, rnd(0.2));
                    }
                }
            }
        }
    }

    private static Vec3 rightOf(float yaw) {
        Vec3 f = forward(yaw);
        return new Vec3(-f.z, 0, f.x);
    }

    /** Where the staff's head is while aiming: in front of the eyes, a little low and to the right. */
    private static Vec3 staffTip(Entity e, float partial) {
        Vec3 look = e.getViewVector(partial);
        return e.getEyePosition(partial).add(look.scale(1.1)).add(0, -0.35, 0).add(rightOf(e.getViewYRot(partial)).scale(-0.15));
    }

    // ------------------------------------------------------------------ world drawing

    private static float life(Signature sig) {
        return switch (sig) {
            case INFERNAL_REND, HEAVENS_VERDICT, STARFALL -> 1.3f;
            case ABSOLUTE_ZERO -> 2.4f;
            case CATACLYSM_CLEAVE -> 1.7f;
            case DRAGONS_WRATH -> 1.1f;
            case DEVOURING_STAR -> 1.5f;
            case COLOSSUS_STANCE -> 2.8f;
            case VERDANT_BLESSING -> 2.6f;
        };
    }

    public static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = now(partial);
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        Iterator<Fx> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            float t = (time - fx.start) / 20f;
            float k = fx.impact < 0 ? -1 : (time - fx.impact) / 20f;
            if ((k < 0 && t > 3) || k > life(fx.sig) || t < -1) {
                it.remove();
                continue;
            }
            Entity e = mc.level.getEntity(fx.entityId);
            boolean hideSelf = e == mc.player && firstPerson;
            Vec3 live = e != null ? e.getPosition(partial) : fx.at;
            Vec3 origin = k < 0 ? live : fx.at;
            pose.pushPose();
            pose.translate(origin.x - cam.x, origin.y - cam.y, origin.z - cam.z);
            Ctx c = new Ctx(pose, buffers, camera, origin, time, t, k, hideSelf, e, partial, live);
            switch (fx.sig) {
                case INFERNAL_REND -> infernalRend(c, fx);
                case ABSOLUTE_ZERO -> absoluteZero(c, fx);
                case HEAVENS_VERDICT -> heavensVerdict(c, fx);
                case CATACLYSM_CLEAVE -> cataclysmCleave(c, fx);
                case DRAGONS_WRATH -> dragonsWrath(c, fx);
                case DEVOURING_STAR -> devouringStar(c, fx);
                case COLOSSUS_STANCE -> colossusStance(c, fx);
                case VERDANT_BLESSING -> verdantBlessing(c, fx);
                case STARFALL -> starfall(c, fx);
            }
            pose.popPose();
        }
        buffers.endBatch(ModRenderTypes.CRYSTAL);
        buffers.endBatch(ModRenderTypes.CRYSTAL_GLOW);
        for (ResourceLocation tex : TEXTURES) buffers.endBatch(ModRenderTypes.lightTexture(tex));
    }

    /** What every art's drawing needs: t = seconds since the wind-up began, k = seconds since the strike (or -1). */
    private record Ctx(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, float time, float t, float k,
                       boolean hideSelf, Entity entity, float partial, Vec3 live) {}

    private static float out(float x) {
        x = Mth.clamp(x, 0, 1);
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    private static float window(float x, float from, float to) {
        return Mth.clamp((x - from) / (to - from), 0, 1);
    }

    /** Fades in over {@code in} seconds and out over the last {@code outLen} seconds of {@code length}. */
    private static float envelope(float x, float in, float length, float outLen) {
        if (x < 0 || x > length) return 0;
        return Math.min(in <= 0 ? 1 : x / in, Math.min(1, (length - x) / outLen));
    }

    // ---- Infernal Rend

    private static void infernalRend(Ctx c, Fx fx) {
        int core = 0xFF5A1E, hot = 0xFFB050;
        if (c.k < 0 || c.hideSelf) return;
        float k = c.k;
        // the arms fling up and the fire answers: tongues of flame flare up and out in a fan, then gutter
        float flare = out(k / 0.2f);
        float fade = 1 - window(k, 0.25f, 0.8f);
        float yaw = fx.yaw;
        Vec3 f = forward(yaw), r = rightOf(yaw);
        for (int i = 0; i < 7; i++) {
            float spread = (i - 3) / 3f;
            Vec3 dir = r.scale(spread * 0.9).add(0, 1.4 - Math.abs(spread) * 0.4, 0).add(f.scale(0.15)).normalize();
            Vec3 base = new Vec3(0, 1.5, 0).add(r.scale(spread * 0.25));
            float len = (1.4f - Math.abs(spread) * 0.4f) * flare;
            streak(c, base, base.add(dir.scale(len)), 0.32f, core, 1.1f * fade);
            streak(c, base, base.add(dir.scale(len * 0.75f)), 0.12f, hot, 1.2f * fade);
        }
    }

    // ---- Absolute Zero

    private static void absoluteZero(Ctx c, Fx fx) {
        int ice = 0x9ED8FF, pale = 0xE6F7FF;
        if (c.k < 0) {
            return;
        }
        float k = c.k;
        float fade = 1 - window(k, 1.8f, 2.4f);
        // the frost floor races out, then lingers
        float floor = out(k / 0.35f);
        disc(c, GLOW, 0.03f, 7.5f * floor, 0, 0x6FB8FF, 0.4f * fade);
        disc(c, RING, 0.06f, 1 + 7 * out(k / 0.3f), 0, pale, 1.2f * (1 - window(k, 0.15f, 0.5f)));
        // ice spikes: three rings, each erupting a beat after the last, then sinking away
        RandomSource r = RandomSource.create(fx.seed);
        for (int ring = 0; ring < 3; ring++) {
            float radius = 2f + ring * 2f;
            int count = 7 + ring * 4;
            float delay = ring * 0.07f;
            for (int i = 0; i < count; i++) {
                float a = (i + r.nextFloat() * 0.5f) / count * Mth.TWO_PI;
                float rr = radius + (r.nextFloat() - 0.5f) * 0.8f;
                float h = (1.1f - ring * 0.2f) * (0.7f + r.nextFloat() * 0.6f);
                float grow = Anim.outBack(window(k, delay, delay + 0.18f));
                float sink = window(k, 1.5f + ring * 0.1f, 2.3f);
                spike(c, Mth.cos(a) * rr, Mth.sin(a) * rr, h * grow * (1 - sink), 0.16f + 0.1f * r.nextFloat(),
                        a, 0.35f * r.nextFloat(), ice, 0.75f * (1 - sink));
            }
        }
        // a cluster of larger spikes encases each frozen foe
        for (Vec3 p : fx.points) {
            Vec3 rel = p.subtract(c.origin);
            for (int i = 0; i < 5; i++) {
                float a = i / 5f * Mth.TWO_PI + r.nextFloat();
                float grow = Anim.outBack(window(k, 0.1f, 0.3f)) * (1 - window(k, 1.6f, 2.3f));
                spike(c, (float) rel.x + Mth.cos(a) * 0.45f, (float) rel.z + Mth.sin(a) * 0.45f, (1.4f + r.nextFloat() * 0.6f) * grow,
                        0.22f, a, 0.4f, pale, 0.8f * fade);
            }
        }
    }

    // ---- Heaven's Verdict

    private static void heavensVerdict(Ctx c, Fx fx) {
        int sky = 0xB8E2FF, white = 0xF2FAFF;
        if (c.k < 0) {
            float g = out(c.t / 0.7f);
            // the storm sigil gathers high above and spins up
            disc(c, CIRCLE, 6f, 0.8f + 2.6f * g, c.time * (2 + 10 * g), sky, 0.9f * g);
            disc(c, GLOW, 5.9f, 3.5f * g, 0, 0x6FA8FF, 0.5f * g);
            if (c.t > 0.35f) {
                long seed = fx.seed + (long) (c.time / 2);
                zigzag(c, new Vec3(0, 2.3, 0), new Vec3(0, 6, 0), seed, 0.18f, white, 0.8f * window(c.t, 0.35f, 0.6f));
            }
            return;
        }
        float k = c.k;
        float fade = 1 - window(k, 0.5f, 1.3f);
        disc(c, CIRCLE, 6f, 3.4f + 0.8f * out(k), c.time * 12, sky, 0.9f * fade);
        for (int i = 0; i < fx.points.size(); i++) {
            Vec3 rel = fx.points.get(i).subtract(c.origin);
            float delay = i * 0.05f;
            float kk = k - delay;
            if (kk < 0) continue;
            float strike = 1 - window(kk, 0.05f, 0.6f);
            // a column of light, thick at the strike then thinning
            Vec3 top = rel.add(0, 14, 0);
            streak(c, top, rel, 1.6f * strike + 0.2f, sky, strike * 1.2f);
            streak(c, top, rel, 0.5f * strike + 0.08f, white, strike * 1.4f);
            if (kk < 0.4f) zigzag(c, top, rel, fx.seed + i * 31 + (long) (c.time / 2), 0.14f, white, 1 - kk / 0.4f);
            float roll = fx.seed % 360 + i * 40;
            pushed(c, rel, () -> {
                disc(c, RING, 0.05f, 0.4f + 2.6f * out(kk / 0.35f), 0, sky, 1.2f * (1 - window(kk, 0.1f, 0.5f)));
                dot(c, BURST, 0, 1f, 0, 0.8f + 1.5f * out(kk / 0.25f), white, 1.2f * (1 - window(kk, 0, 0.3f)), roll);
            });
        }
    }

    // ---- Cataclysm Cleave

    private static void cataclysmCleave(Ctx c, Fx fx) {
        int fire = 0xFF7A2E, hot = 0xFFD07A;
        if (c.k < 0) {
            float g = out(c.t / 0.5f);
            if (!c.hideSelf && c.entity != null) {
                // heat gathers along the coiled blade
                float yaw = c.entity.getViewYRot(c.partial);
                Vec3 f = forward(yaw), r = rightOf(yaw);
                for (int i = 0; i < 6; i++) {
                    double a = -0.3 - i * 0.3;
                    Vec3 q = f.scale(Math.cos(a) * 1.3).add(r.scale(-Math.sin(a) * 1.3));
                    dot(c, GLOW, (float) q.x, 1.15f, (float) q.z, 0.22f * g, i % 2 == 0 ? hot : fire, 0.7f * g, 0);
                }
            }
            return;
        }
        float k = c.k;
        Vec3 o = fx.points.size() >= 2 ? fx.points.get(0).subtract(c.origin) : Vec3.ZERO;
        Vec3 tip = fx.points.size() >= 2 ? fx.points.get(1).subtract(c.origin) : forward(fx.yaw).scale(7.5);
        Vec3 dir = tip.subtract(o);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        // a great crescent of fire sweeps across the front, right to left
        float sweep = out(k / 0.18f);
        float slashFade = 1 - window(k, 0.15f, 0.55f);
        slashAt(c, yaw, -70 + 110 * sweep, 1.0f, 3.2f + 1.4f * sweep, fire, 1.3f * slashFade);
        slashAt(c, yaw, -80 + 110 * sweep, 1.0f, 2.6f + 1.2f * sweep, hot, 1.1f * slashFade);
        // the fissure: a burning line torn along the ground, then columns of flame erupting along it
        float tear = out(window(k, 0.05f, 0.35f));
        float fade = 1 - window(k, 1.0f, 1.7f);
        flatStreak(c, o.add(dir.scale(0.1)), o.add(dir.scale(0.1 + 0.9 * tear)), 0.9f, fire, 1.2f * fade);
        flatStreak(c, o.add(dir.scale(0.1)), o.add(dir.scale(0.1 + 0.9 * tear)), 0.3f, hot, 1.4f * fade);
        for (int d = 1; d <= 7; d++) {
            float delay = 0.05f + d * 0.045f;
            float kk = k - delay;
            if (kk < 0) continue;
            Vec3 at = o.add(dir.scale(d / 7.5));
            float rise = out(kk / 0.15f);
            float flame = 1 - window(kk, 0.2f, 0.9f);
            float h = (1.6f + d * 0.25f) * rise;
            streak(c, at, at.add(0, h, 0), 0.9f + 0.1f * d, fire, flame);
            streak(c, at, at.add(0, h * 0.8f, 0), 0.35f + 0.05f * d, hot, flame * 1.2f);
        }
    }

    // ---- Dragon's Wrath

    private static void dragonsWrath(Ctx c, Fx fx) {
        int crimson = 0xE0306E, dark = 0x9A0F45, pale = 0xFFB0CC;
        if (c.k < 0) {
            return;
        }
        if (fx.points.size() < 2) return;
        float k = c.k;
        Vec3 from = fx.points.get(0).subtract(c.origin), to = fx.points.get(1).subtract(c.origin);
        // the afterimage: a ribbon that tears from start to end, then thins from the start forward
        float draw = out(k / 0.08f);
        float tail = window(k, 0.15f, 0.8f);
        Vec3 a = from.lerp(to, tail), b = from.lerp(to, draw);
        for (float y : new float[]{0.5f, 1.0f, 1.5f}) {
            streak(c, a.add(0, y, 0), b.add(0, y, 0), y == 1.0f ? 1.1f : 0.5f, y == 1.0f ? crimson : dark, 1.1f * (1 - tail));
        }
        streak(c, a.add(0, 1, 0), b.add(0, 1, 0), 0.2f, pale, 1.3f * (1 - tail));
        // ghosts of the wielder along the path
        for (int i = 1; i <= 4; i++) {
            float u = i / 5f;
            if (u > draw) continue;
            Vec3 q = from.lerp(to, u);
            float g = (1 - window(k, 0.05f + u * 0.15f, 0.45f + u * 0.2f)) * 0.7f;
            dot(c, GLOW, (float) q.x, (float) q.y + 0.9f, (float) q.z, 0.55f, crimson, g, 0);
            streak(c, q.add(0, 0.1, 0), q.add(0, 1.8, 0), 0.7f, dark, g);
        }
        // the wounds open a beat after the wielder has passed: an X of light on everything cut
        for (int i = 2; i < fx.points.size(); i++) {
            Vec3 p = fx.points.get(i).subtract(c.origin).add(0, 1, 0);
            float kk = k - 0.12f;
            if (kk < 0) continue;
            float open = out(kk / 0.08f);
            float f = 1 - window(kk, 0.2f, 0.6f);
            Vec3 view = c.camera.getPosition().subtract(c.origin.add(p)).normalize();
            Vec3 side = view.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 up = side.cross(view).normalize();
            Vec3 d1 = side.add(up).scale(0.9 * open), d2 = side.subtract(up).scale(0.9 * open);
            streak(c, p.subtract(d1), p.add(d1), 0.35f, crimson, 1.4f * f);
            streak(c, p.subtract(d2), p.add(d2), 0.35f, crimson, 1.4f * f);
            streak(c, p.subtract(d1), p.add(d1), 0.1f, pale, 1.4f * f);
            streak(c, p.subtract(d2), p.add(d2), 0.1f, pale, 1.4f * f);
        }
    }

    // ---- Devouring Star

    private static void devouringStar(Ctx c, Fx fx) {
        int violet = 0x9B7BFF, pale = 0xE4DBFF;
        if (c.k < 0) {
            if (c.entity == null) return;
            float g = out(c.t / 0.45f);
            Vec3 tip = staffTip(c.entity, c.partial).subtract(c.origin);
            float s = c.hideSelf ? 0.45f : 1f;
            dot(c, VORTEX, (float) tip.x, (float) tip.y, (float) tip.z, (0.3f + 0.9f * g) * s, violet, 0.9f * g, -c.time * 25);
            dot(c, GLOW, (float) tip.x, (float) tip.y, (float) tip.z, (0.15f + 0.35f * g) * s, pale, g, 0);
            return;
        }
        if (fx.points.size() < 2) return;
        float k = c.k;
        Vec3 muzzle = fx.points.get(0).subtract(c.origin), hit = fx.points.get(1).subtract(c.origin);
        // the beam, gone almost as soon as it is seen
        float beam = 1 - window(k, 0.05f, 0.35f);
        streak(c, muzzle, hit, 0.9f * beam, violet, beam * 1.2f);
        streak(c, muzzle, hit, 0.3f * beam, pale, beam * 1.5f);
        float hx = (float) hit.x, hy = (float) hit.y, hz = (float) hit.z;
        // collapse: everything around the impact is drawn inward
        if (k < 0.3f) {
            float pull = window(k, 0, 0.28f);
            dot(c, VORTEX, hx, hy, hz, 3.2f * (1 - pull) + 0.3f, violet, 1.1f, c.time * 40);
            dot(c, GLOW, hx, hy, hz, 0.4f + 0.4f * pull, pale, 1, 0);
        }
        // then it bursts
        float bk = k - 0.28f;
        if (bk >= 0) {
            float burst = out(bk / 0.35f);
            float f = 1 - window(bk, 0.2f, 1.1f);
            dot(c, BURST, hx, hy, hz, 0.8f + 3.5f * burst, pale, 1.3f * f, fx.seed % 360);
            dot(c, GLOW, hx, hy, hz, 1 + 3.5f * burst, violet, 0.9f * f, 0);
            pushed(c, new Vec3(hx, hy, hz), () -> {
                disc(c, RING, 0, 0.5f + 4.5f * burst, 0, violet, 1.2f * f);
                c.pose.pushPose();
                c.pose.mulPose(Axis.XP.rotationDegrees(90));
                disc(c, RING, 0, 0.5f + 4f * burst, 0, pale, 0.9f * f);
                c.pose.popPose();
            });
        }
    }

    // ---- Colossus Stance

    private static void colossusStance(Ctx c, Fx fx) {
        int gold = 0xFFC85A, pale = 0xFFF1C8;
        if (c.k < 0) {
            float g = out(c.t / 0.45f);
            if (!c.hideSelf) dot(c, GLOW, 0, 2.9f, 0, 0.2f + 0.6f * g, pale, 0.9f * g, 0);
            return;
        }
        float k = c.k;
        // cracks shoot out from the hammer and glow, then cool
        RandomSource r = RandomSource.create(fx.seed);
        float crack = out(k / 0.15f);
        float cool = 1 - window(k, 0.8f, 2.4f);
        for (int i = 0; i < 9; i++) {
            float a = i / 9f * Mth.TWO_PI + r.nextFloat() * 0.4f;
            float len = (2.5f + r.nextFloat() * 2.5f) * crack;
            Vec3 mid = new Vec3(Mth.cos(a + 0.2f) * len * 0.5f, 0, Mth.sin(a + 0.2f) * len * 0.5f);
            Vec3 end = new Vec3(Mth.cos(a) * len, 0, Mth.sin(a) * len);
            flatStreak(c, new Vec3(0, 0, 0), mid, 0.22f, gold, 1.3f * cool);
            flatStreak(c, mid, end, 0.16f, gold, 1.1f * cool);
        }
        // three shockwaves roll out
        for (int i = 0; i < 3; i++) {
            float kk = k - i * 0.12f;
            if (kk < 0) continue;
            disc(c, RING, 0.06f + i * 0.02f, 0.6f + 5f * out(kk / 0.5f), 0, i == 0 ? pale : gold, 1.2f * (1 - window(kk, 0.15f, 0.55f)));
        }
        // the golden dome: rises, shimmers, then fades leaving a ring that marks the stance
        float rise = Anim.outBack(window(k, 0.05f, 0.35f));
        float dome = (1 - window(k, 1.8f, 2.8f));
        if (!c.hideSelf) dome(c, 1.55f * rise, gold, (0.25f + 0.15f * Mth.sin(c.time * 0.5f)) * dome);
    }

    // ---- Verdant Blessing

    private static void verdantBlessing(Ctx c, Fx fx) {
        int leaf = 0x8CE05A, blossom = 0xFFB7D5, pale = 0xF0FFE0;
        if (c.k < 0) {
            float g = out(c.t / 0.6f);
            disc(c, CIRCLE, 0.04f, 0.6f + 1.6f * g, -c.time * 1.5f, leaf, 0.75f * g);
            if (!c.hideSelf) dot(c, GLOW, 0, 2.6f, 0, 0.15f + 0.4f * g, pale, 0.9f * g, 0);
            return;
        }
        float k = c.k;
        float fade = 1 - window(k, 1.8f, 2.6f);
        // a giant flower unfolds: closed petals open flat, slowly turning
        float open = Anim.outBack(window(k, 0, 0.6f));
        for (int layer = 0; layer < 2; layer++) {
            int count = 8;
            float len = layer == 0 ? 3.4f : 2.4f;
            for (int i = 0; i < count; i++) {
                float a = (i + layer * 0.5f) / count * 360f + c.time * (layer == 0 ? 0.6f : -0.9f);
                float tilt = 80 * (1 - open) + 6;
                petal(c, a, tilt, len * (0.4f + 0.6f * open), len * 0.32f, layer == 0 ? leaf : blossom, 0.75f * fade);
            }
        }
        disc(c, GLOW, 0.05f, 1.2f + 0.6f * open, 0, pale, 0.9f * fade);
        disc(c, RING, 0.06f, 0.5f + 4f * out(k / 0.6f), 0, leaf, 1 - window(k, 0.3f, 0.8f));
        // a soft column of light on each ally the blessing reached (and the wielder)
        List<Vec3> lit = new ArrayList<>(fx.points);
        lit.add(c.origin);
        for (Vec3 p : lit) {
            Vec3 rel = p.subtract(c.origin);
            if (rel.lengthSqr() < 0.01 && c.hideSelf) continue;
            float rise = out(window(k, 0.1f, 0.6f));
            float f = 1 - window(k, 1.2f, 2.2f);
            streak(c, rel, rel.add(0, 3.2 * rise, 0), 1.1f, leaf, 0.6f * f);
            streak(c, rel, rel.add(0, 2.6 * rise, 0), 0.35f, pale, 0.9f * f);
        }
    }

    // ---- Starfall

    private static void starfall(Ctx c, Fx fx) {
        if (fx.points.isEmpty() || c.k < 0) return;
        int star = 0xFFF0A8, white = 0xFFFFFF;
        float k = c.k;
        Vec3 hit = fx.points.get(0).subtract(c.origin);
        Vec3 sky = hit.add(-6, 22, 4);
        float fall = out(k / 0.14f);
        Vec3 head = sky.lerp(hit, fall);
        Vec3 tail = sky.lerp(hit, Math.max(0, fall - 0.35f + window(k, 0.14f, 0.4f) * 0.35f));
        float trail = 1 - window(k, 0.14f, 0.5f);
        streak(c, tail, head, 0.7f, star, trail * 1.2f);
        streak(c, tail, head, 0.22f, white, trail * 1.4f);
        float hx = (float) head.x, hy = (float) head.y, hz = (float) head.z;
        dot(c, GLOW, hx, hy, hz, 0.6f, white, trail, 0);
        float bk = k - 0.14f;
        if (bk >= 0) {
            float f = 1 - window(bk, 0.15f, 1.1f);
            float x = (float) hit.x, y = (float) hit.y, z = (float) hit.z;
            dot(c, BURST, x, y + 0.6f, z, 0.6f + 2.6f * out(bk / 0.3f), star, 1.3f * f, c.time * 2);
            pushed(c, hit, () -> {
                disc(c, RING, 0.05f, 0.5f + 3f * out(bk / 0.4f), 0, star, 1.2f * f);
            });
        }
    }

    // ------------------------------------------------------------------ the wielder's screen

    /** Drawn in the combat HUD layer: the wind-up charge and the strike's own tint for the wielder. */
    public static void renderHud(GuiGraphics g) {
        if (!ClientConfig.SCREEN_EFFECTS.get()) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float c = Anim.since(chargeAt);
        if (chargeSig != null && c < 1.2f) {
            float a = Anim.clamp01(c / 0.4f) * 0.35f;
            int color = 0xFF000000 | chargeSig.color();
            Shapes.vignette(0, 0, w, h, 0.6f, 1.3f).fill(Gfx.alpha(color, a)).draw(g);
        }
        if (screenSig == null) return;
        float s = Anim.since(screenAt);
        int color = 0xFF000000 | screenSig.color();
        switch (screenSig) {
            case INFERNAL_REND -> {
                float f = 1 - Anim.clamp01(s / 1.2f);
                if (f <= 0) return;
                float flicker = 0.8f + 0.2f * Mth.sin(s * 37f);
                Shapes.vignette(0, 0, w, h, 0.35f, 1.15f).fill(Gfx.alpha(0xFFFF4A10, 0.75f * f * flicker)).draw(g);
                Shapes.box(0, 0, w, h).fillV(0x00000000, Gfx.alpha(0xFFFF8A2A, 0.3f * f)).draw(g);
            }
            case ABSOLUTE_ZERO -> {
                // frost creeps in from the edges and lingers
                float in = Anim.outCubic(Anim.clamp01(s / 0.3f));
                float f = 1 - Anim.clamp01((s - 1.2f) / 1f);
                if (f <= 0) return;
                Shapes.vignette(0, 0, w, h, 0.75f - 0.35f * in, 1.1f).fill(Gfx.alpha(0xFFE6F7FF, 0.7f * f)).draw(g);
                Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFF9ED8FF, 0.12f * f)).draw(g);
            }
            case HEAVENS_VERDICT -> {
                float flash = 1 - Anim.clamp01(s / 0.25f);
                if (flash > 0) Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFFFFFFFF, 0.6f * flash)).draw(g);
                float f = 1 - Anim.clamp01(s / 1f);
                if (f > 0) Shapes.vignette(0, 0, w, h, 0.4f, 1.2f).fill(Gfx.alpha(0xFF6FA8FF, 0.55f * f)).draw(g);
            }
            case CATACLYSM_CLEAVE -> {
                float f = 1 - Anim.clamp01(s / 0.8f);
                if (f <= 0) return;
                Shapes.vignette(0, 0, w, h, 0.3f, 1.1f).fill(Gfx.alpha(color, 0.8f * f)).draw(g);
                // a hot band sweeps across the screen with the blade
                float x = Anim.outCubic(Anim.clamp01(s / 0.25f));
                float bandW = w * 0.5f;
                Shapes.box(w - x * (w + bandW), 0, bandW, h).fillH(0x00000000, Gfx.alpha(0xFFFFB050, 0.35f * f)).additive().draw(g);
            }
            case DRAGONS_WRATH -> {
                float f = 1 - Anim.clamp01(s / 0.45f);
                if (f <= 0) return;
                if (s < 0.02f) raysTurn = RANDOM.nextFloat();
                Shapes.rays(w / 2f, h / 2f, Math.max(w, h) * 0.75f, 40).rotation(raysTurn).fill(Gfx.alpha(0xFFFF6A9A, 0.55f * f)).additive().draw(g);
                Shapes.vignette(0, 0, w, h, 0.45f, 1.1f).fill(Gfx.alpha(0xFF6A0A2A, 0.7f * f)).draw(g);
            }
            case DEVOURING_STAR -> {
                float f = 1 - Anim.clamp01(s / 0.9f);
                if (f <= 0) return;
                Shapes.vignette(0, 0, w, h, 0.25f + 0.5f * (1 - f), 1.2f).fill(Gfx.alpha(0xFF3A1A80, 0.75f * f)).draw(g);
                float e = Anim.outCubic(Anim.clamp01(s / 0.6f));
                Shapes.ring(w / 2f, h / 2f, 10 + e * Math.max(w, h) * 0.4f, 2 + 5 * (1 - e)).fill(Gfx.alpha(color, 0.7f * f))
                        .glow(10, Gfx.alpha(color, 0.5f * f)).additive().draw(g);
            }
            case COLOSSUS_STANCE -> {
                float f = 1 - Anim.clamp01(s / 1.4f);
                if (f <= 0) return;
                Shapes.vignette(0, 0, w, h, 0.5f, 1.2f).fill(Gfx.alpha(color, 0.6f * f)).draw(g);
                Shapes.box(0, h * 0.6f, w, h * 0.4f).fillV(0x00000000, Gfx.alpha(0xFFFFE0A0, 0.35f * f)).additive().draw(g);
            }
            case VERDANT_BLESSING -> {
                float in = Anim.clamp01(s / 0.4f);
                float f = Math.min(in, 1 - Anim.clamp01((s - 0.6f) / 1.6f));
                if (f <= 0) return;
                Shapes.box(0, h * 0.4f, w, h * 0.6f).fillV(0x00000000, Gfx.alpha(0xFFB8F08A, 0.4f * f)).additive().draw(g);
                Shapes.vignette(0, 0, w, h, 0.55f, 1.3f).fill(Gfx.alpha(0xFF8CE05A, 0.45f * f)).draw(g);
            }
            case STARFALL -> {}
        }
    }

    // ------------------------------------------------------------------ drawing helpers

    private static void pushed(Ctx c, Vec3 at, Runnable draw) {
        c.pose.pushPose();
        c.pose.translate(at.x, at.y, at.z);
        draw.run();
        c.pose.popPose();
    }

    private static int argb(int rgb, float alpha) {
        return (Mth.clamp(Math.round(Math.min(1f, alpha) * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static void disc(Ctx c, ResourceLocation tex, float y, float radius, float degrees, int rgb, float alpha) {
        if (alpha <= 0.01f || radius <= 0.01f) return;
        PoseStack pose = c.pose;
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        quad(c.buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose.last().pose(), -radius, -radius, radius, radius, rgb, alpha);
        pose.popPose();
    }

    private static void dot(Ctx c, ResourceLocation tex, float x, float y, float z, float half, int rgb, float alpha, float roll) {
        if (alpha <= 0.01f || half <= 0.005f) return;
        PoseStack pose = c.pose;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(c.camera.rotation());
        if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll % 360));
        quad(c.buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose.last().pose(), -half, -half, half, half, rgb, alpha);
        pose.popPose();
    }

    /** The crescent texture laid flat around the origin at {@code height}, facing {@code yaw}, turned by {@code sweep}. */
    private static void slashAt(Ctx c, float yaw, float sweep, float height, float radius, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        PoseStack pose = c.pose;
        pose.pushPose();
        pose.translate(0, height, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw + 180));
        pose.mulPose(Axis.ZP.rotationDegrees(-8));
        pose.mulPose(Axis.YP.rotationDegrees(sweep));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        quad(c.buffers.getBuffer(ModRenderTypes.lightTexture(SLASH)), pose.last().pose(), -radius, -radius, radius, radius, rgb, alpha);
        pose.popPose();
    }

    /** A petal of light: a stretched glow from the centre outwards at {@code degrees}, tilted up by {@code tilt}. */
    private static void petal(Ctx c, float degrees, float tilt, float length, float width, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        PoseStack pose = c.pose;
        pose.pushPose();
        pose.translate(0, 0.06f, 0);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.mulPose(Axis.XP.rotationDegrees(90 - tilt));
        quad(c.buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose.last().pose(), -width, -length * 0.08f, width, length, rgb, alpha);
        pose.popPose();
    }

    /** A camera-facing streak of light from a to b (relative to the effect's origin). */
    private static void streak(Ctx c, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        if (alpha <= 0.01f || width <= 0.005f) return;
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0e-4) return;
        Vec3 mid = c.origin.add(a.add(b).scale(0.5));
        Vec3 view = c.camera.getPosition().subtract(mid);
        Vec3 side = dir.cross(view);
        if (side.lengthSqr() < 1.0e-6) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0e-6) side = new Vec3(1, 0, 0);
        band(c, STREAK, a, b, side.normalize().scale(width / 2), rgb, alpha);
    }

    /** A streak lying on the ground from a to b. */
    private static void flatStreak(Ctx c, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0e-4) return;
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize().scale(width / 2);
        band(c, STREAK, a.add(0, 0.05, 0), b.add(0, 0.05, 0), side, rgb, alpha);
    }

    private static void band(Ctx c, ResourceLocation tex, Vec3 a, Vec3 b, Vec3 side, int rgb, float alpha) {
        VertexConsumer out = c.buffers.getBuffer(ModRenderTypes.lightTexture(tex));
        Matrix4f m = c.pose.last().pose();
        int col = argb(rgb, alpha);
        out.addVertex(m, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setUv(0, 1).setColor(col);
        out.addVertex(m, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).setUv(1, 1).setColor(col);
        out.addVertex(m, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).setUv(1, 0).setColor(col);
        out.addVertex(m, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setUv(0, 0).setColor(col);
    }

    /** A jagged bolt from a to b, re-rolled whenever the seed changes. */
    private static void zigzag(Ctx c, Vec3 a, Vec3 b, long seed, float width, int rgb, float alpha) {
        RandomSource r = RandomSource.create(seed);
        int n = Math.max(3, (int) (b.subtract(a).length() / 1.4));
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            Vec3 next = a.lerp(b, i / (double) n);
            if (i < n) next = next.add((r.nextDouble() - 0.5) * 0.7, (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.7);
            streak(c, prev, next, width, rgb, alpha);
            prev = next;
        }
    }

    /**
     * An ice spike: a four-sided crystal pyramid standing at (x, z), leaning outward by {@code lean} radians
     * towards {@code angle}. Faces are shaded so it reads as solid, with a faint glow shell.
     */
    private static void spike(Ctx c, float x, float z, float height, float half, float angle, float lean, int rgb, float alpha) {
        if (height <= 0.02f || alpha <= 0.01f) return;
        float tx = x + Mth.cos(angle) * Mth.sin(lean) * height;
        float tz = z + Mth.sin(angle) * Mth.sin(lean) * height;
        float ty = Mth.cos(lean) * height;
        float[][] base = new float[4][];
        for (int i = 0; i < 4; i++) {
            float a = angle + i * Mth.HALF_PI + Mth.PI / 4;
            base[i] = new float[]{x + Mth.cos(a) * half, 0.02f, z + Mth.sin(a) * half};
        }
        Matrix4f m = c.pose.last().pose();
        // one buffer at a time: asking the buffer source for another render type ends the one being filled
        VertexConsumer faces = c.buffers.getBuffer(ModRenderTypes.CRYSTAL);
        int tip = argb(0xF4FCFF, alpha * 0.85f);
        for (int i = 0; i < 4; i++) {
            float[] p0 = base[i], p1 = base[(i + 1) % 4];
            float shade = 0.65f + 0.35f * (i % 2 == 0 ? 1 : 0.55f);
            int face = argb(Gfx.mix(0xFF000000 | rgb, 0xFFFFFFFF, 0.25f * shade) & 0xFFFFFF, alpha * 0.65f * shade);
            faces.addVertex(m, p0[0], p0[1], p0[2]).setColor(face);
            faces.addVertex(m, p1[0], p1[1], p1[2]).setColor(face);
            faces.addVertex(m, tx, ty, tz).setColor(tip);
        }
        VertexConsumer glow = c.buffers.getBuffer(ModRenderTypes.CRYSTAL_GLOW);
        int g = argb(rgb, alpha * 0.25f), gt = argb(0xFFFFFF, alpha * 0.3f);
        for (int i = 0; i < 4; i++) {
            float[] p0 = base[i], p1 = base[(i + 1) % 4];
            glow.addVertex(m, p0[0], p0[1], p0[2]).setColor(g);
            glow.addVertex(m, p1[0], p1[1], p1[2]).setColor(g);
            glow.addVertex(m, tx, ty + 0.05f, tz).setColor(gt);
        }
    }

    /** A low dome of light around the origin: six sides, three bands, added to the scene as glow. */
    private static void dome(Ctx c, float radius, int rgb, float alpha) {
        if (radius <= 0.05f || alpha <= 0.01f) return;
        Matrix4f m = c.pose.last().pose();
        VertexConsumer out = c.buffers.getBuffer(ModRenderTypes.CRYSTAL_GLOW);
        int sides = 6, bands = 3;
        for (int b = 0; b < bands; b++) {
            float e0 = b / (float) bands * Mth.HALF_PI, e1 = (b + 1) / (float) bands * Mth.HALF_PI;
            float r0 = Mth.cos(e0) * radius, r1 = Mth.cos(e1) * radius;
            float y0 = Mth.sin(e0) * radius * 1.2f, y1 = Mth.sin(e1) * radius * 1.2f;
            int c0 = argb(rgb, alpha * (1 - b * 0.25f)), c1 = argb(rgb, alpha * (0.75f - b * 0.25f));
            for (int i = 0; i < sides; i++) {
                float a0 = i / (float) sides * Mth.TWO_PI, a1 = (i + 1) / (float) sides * Mth.TWO_PI;
                float x00 = Mth.cos(a0) * r0, z00 = Mth.sin(a0) * r0, x01 = Mth.cos(a1) * r0, z01 = Mth.sin(a1) * r0;
                float x10 = Mth.cos(a0) * r1, z10 = Mth.sin(a0) * r1, x11 = Mth.cos(a1) * r1, z11 = Mth.sin(a1) * r1;
                out.addVertex(m, x00, y0, z00).setColor(c0);
                out.addVertex(m, x01, y0, z01).setColor(c0);
                out.addVertex(m, x11, y1, z11).setColor(c1);
                if (b < bands - 1) {
                    out.addVertex(m, x00, y0, z00).setColor(c0);
                    out.addVertex(m, x11, y1, z11).setColor(c1);
                    out.addVertex(m, x10, y1, z10).setColor(c1);
                }
            }
        }
    }

    private static void quad(VertexConsumer out, Matrix4f m, float x0, float y0, float x1, float y1, int rgb, float alpha) {
        int col = argb(rgb, alpha);
        out.addVertex(m, x0, y0, 0).setUv(0, 1).setColor(col);
        out.addVertex(m, x1, y0, 0).setUv(1, 1).setColor(col);
        out.addVertex(m, x1, y1, 0).setUv(1, 0).setColor(col);
        out.addVertex(m, x0, y1, 0).setUv(0, 0).setColor(col);
    }

    public static void clear() {
        ACTIVE.clear();
        screenSig = null;
        chargeSig = null;
        shakeAt = screenAt = chargeAt = -10;
    }

    private SignatureFx() {}
}
