package com.beyondsolo.client.skill;

import com.beyondsolo.client.combat.BodyAnimator;
import com.beyondsolo.client.fx.Aura;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.item.SystemBowItem;
import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.network.SignatureFxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The weapon arts as the world sees them. Each art ({@link Art}) has its own wind-up the world can read, its own
 * strike and its own later beats, drawn with the skill skins ({@link Skins}, {@link SkinFx}), the series' light and ink
 * ({@link Aura}) and the soft particles ({@link FxParticles}); the camera jolts with {@link SkillShake}. The screen side
 * (bars, banners, impact frames) is {@code client.combat.SignatureFx}, which also feeds the payloads in here.
 *
 * <p>The art looks are in {@link ArtDivine}, {@link ArtMythic} and {@link ArtLegendary}; shapes of their own (crystal
 * spikes, a hammer of light, hounds' jaws, a tree) in {@link ArtShapes}.</p>
 */
public final class ArtFx {
    static final RandomSource RANDOM = RandomSource.create();

    /** One playing art. Times are game ticks (with the partial tick); -1 = not yet. */
    static final class Fx {
        final int entityId;
        final Signature sig;
        final Art art;
        final long seed = RANDOM.nextLong();
        final float start;
        float impact = -1;
        final float[] beat = {-1, -1, -1, -1};
        List<Vec3> points = List.of();
        @SuppressWarnings("unchecked")
        final List<Vec3>[] beatPoints = new List[]{List.of(), List.of(), List.of(), List.of()};
        /** Where the wielder stood at the strike, which way they faced, and their flat look. */
        Vec3 at;
        float yaw;
        Vec3 look;
        boolean passive;
        /** A drawn art's arrow, once found. */
        int arrowId = -1;
        /** Anything an art wants to keep between frames. */
        float memo;
        /** What an art worked out once and keeps (a fissure's path, a tree). */
        Object data;
        /** Which of an art's one-off moments have happened (bits, see {@link #once}). */
        int fired;

        Fx(int entityId, Signature sig, float start, Vec3 at, float yaw) {
            this.entityId = entityId;
            this.sig = sig;
            this.art = ART.get(sig);
            this.start = start;
            this.at = at;
            this.yaw = yaw;
            this.look = forward(yaw);
        }
    }

    /** What an art's drawing needs this frame. */
    static final class Ctx {
        final PoseStack pose;
        final MultiBufferSource.BufferSource buffers;
        final Camera camera;
        /** The world position the pose's origin sits at. */
        final Vec3 origin;
        /** Game time in ticks. */
        final float time;
        /** Seconds since the wind-up began, and since the strike (-1 before it). */
        final float t, k;
        /** The local player's own art seen through their own eyes. */
        final boolean self;
        final Entity e;
        final float partial;
        /** Where the wielder is now (or was last seen). */
        final Vec3 live;
        final Fx fx;

        Ctx(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, Vec3 origin, float time, float t, float k, boolean self,
            Entity e, float partial, Vec3 live, Fx fx) {
            this.pose = pose;
            this.buffers = buffers;
            this.camera = camera;
            this.origin = origin;
            this.time = time;
            this.t = t;
            this.k = k;
            this.self = self;
            this.e = e;
            this.partial = partial;
            this.live = live;
            this.fx = fx;
        }

        Vec3 rel(Vec3 world) {
            return world.subtract(origin);
        }

        /** Seconds since beat n (1..3), or -1 before it. */
        float beat(int n) {
            return fx.beat[n] < 0 ? -1 : (time - fx.beat[n]) / 20f;
        }

        List<Vec3> beatPoints(int n) {
            return fx.beatPoints[n];
        }
    }

    /** An art's look: its wind-up, strike, beats, the particles it throws and how long it plays. */
    abstract static class Art {
        /** Seconds after the strike until it is all gone. */
        abstract float life(Fx fx);

        /** The wind-up began (particles, the first sounds of the world). */
        void started(Fx fx, Entity e, ClientLevel level) {}

        /** Every tick while it plays: k = -1 during the wind-up. */
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {}

        /** The strike frame. */
        void impact(Fx fx, Entity e, ClientLevel level) {}

        /** Beat n arrived from the server. */
        void beat(Fx fx, int n, Entity e, ClientLevel level) {}

        /** A small touch outside the cast (a burning hit, a crit's star). */
        void passive(Fx fx, ClientLevel level) {}

        abstract void draw(Ctx c);
    }

    private static final Map<Signature, Art> ART = new EnumMap<>(Signature.class);

    static {
        ArtMythic.register(ART);
        ArtDivine.register(ART);
        ArtLegendary.register(ART);
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    /** The local player's drawn arts: the game time each is ready again. */
    private static final Map<Signature, Long> READY = new EnumMap<>(Signature.class);

    static float now(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    // ------------------------------------------------------------------ events

    public static void play(SignatureFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (p.phase() == SignatureFxPayload.READY) {
            READY.remove(p.signature());
            return;
        }
        Art art = ART.get(p.signature());
        if (art == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        float now = now(0);
        int phase = p.phase();
        if (phase == SignatureFxPayload.WINDUP) {
            if (e == null) return;
            Fx fx = new Fx(p.entityId(), p.signature(), now, e.position(), e.getYRot());
            ACTIVE.add(fx);
            if (ACTIVE.size() > 48) ACTIVE.remove(0);
            if (p.signature().active() && e instanceof AbstractClientPlayer player) BodyAnimator.play(player, p.signature().key());
            guard(fx, () -> art.started(fx, e, mc.level));
            return;
        }
        if (phase == SignatureFxPayload.PASSIVE) {
            if (p.points().isEmpty()) return;
            Fx fx = new Fx(p.entityId(), p.signature(), now, p.points().get(0), e != null ? e.getYRot() : 0);
            fx.passive = true;
            fx.impact = now;
            fx.points = p.points();
            if (e != null) fx.look = p.points().get(0).subtract(e.position()).multiply(1, 0, 1).normalize();
            ACTIVE.add(fx);
            if (ACTIVE.size() > 48) ACTIVE.remove(0);
            guard(fx, () -> art.passive(fx, mc.level));
            return;
        }
        int n = SignatureFxPayload.beatOf(phase);
        if (n == 0) {
            Fx fx = latest(p.entityId(), p.signature(), f -> f.impact < 0);
            if (fx == null) {
                // no wind-up seen (the wielder just came into view, or a drawn art loosed before it was charged here)
                Vec3 at = e != null ? e.position() : p.points().isEmpty() ? null : p.points().get(0);
                if (at == null) return;
                fx = new Fx(p.entityId(), p.signature(), now - p.signature().impactTicks(), at, e != null ? e.getYRot() : 0);
                ACTIVE.add(fx);
            }
            fx.impact = now;
            fx.points = p.points();
            if (e != null) {
                fx.at = e.position();
                fx.yaw = e.getYRot();
                fx.look = forward(fx.yaw);
            }
            if (e == mc.player && p.signature().drawn()) READY.put(p.signature(), mc.level.getGameTime() + p.signature().cooldownSeconds() * 20L);
            // the body hangs on the strike frame for a beat: the heavier the grade, the longer
            if (p.signature().active() && e instanceof AbstractClientPlayer player) BodyAnimator.hold(player, strikeHold(p.signature()));
            Fx struck = fx;
            guard(struck, () -> art.impact(struck, e, mc.level));
            return;
        }
        if (n > 3) return;
        Fx fx = latest(p.entityId(), p.signature(), f -> f.impact >= 0 && f.beat[n] < 0 && !f.passive);
        if (fx == null) {
            Vec3 at = !p.points().isEmpty() ? p.points().get(0) : e != null ? e.position() : null;
            if (at == null) return;
            fx = new Fx(p.entityId(), p.signature(), now, at, e != null ? e.getYRot() : 0);
            fx.impact = now - 1;
            ACTIVE.add(fx);
        }
        fx.beat[n] = now;
        fx.beatPoints[n] = p.points();
        Fx beaten = fx;
        guard(beaten, () -> art.beat(beaten, n, e, mc.level));
    }

    /** Seconds the wielder's body hangs on an art's strike frame. */
    static float strikeHold(Signature sig) {
        return switch (sig.grade()) {
            case DIVINE -> 0.13f;
            case MYTHIC -> 0.09f;
            default -> 0.06f;
        };
    }

    private static final java.util.Set<Signature> BROKEN = java.util.EnumSet.noneOf(Signature.class);

    /**
     * Runs one of an art's steps; if it throws, the art is stopped and the error logged once, so a fault in one look
     * costs that look, never the game.
     */
    private static boolean guard(Fx fx, Runnable step) {
        try {
            step.run();
            return true;
        } catch (RuntimeException ex) {
            ACTIVE.remove(fx);
            if (BROKEN.add(fx.sig)) com.beyondsolo.BeyondSolo.LOGGER.error("Signature art {} failed and was stopped", fx.sig, ex);
            return false;
        }
    }

    private static Fx latest(int entityId, Signature sig, java.util.function.Predicate<Fx> want) {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Fx f = ACTIVE.get(i);
            if (f.entityId == entityId && f.sig == sig && !f.passive && want.test(f)) return f;
        }
        return null;
    }

    /** Whether the local player's drawn art is ready (as far as this client knows). */
    public static boolean drawReady(Signature sig) {
        ClientLevel level = Minecraft.getInstance().level;
        Long at = READY.get(sig);
        return level == null || at == null || level.getGameTime() >= at;
    }

    /** Seconds the local player's drawn art still has to wait (0 when ready). */
    public static int drawWait(Signature sig) {
        ClientLevel level = Minecraft.getInstance().level;
        Long at = READY.get(sig);
        if (level == null || at == null) return 0;
        long left = at - level.getGameTime();
        return left <= 0 ? 0 : (int) ((left + 19) / 20);
    }

    /** How far a player drawing a bow with a drawn art has gathered it (0..1), or -1 if they aren't. */
    public static float gathering(Player p) {
        if (!p.isUsingItem() || !(p.getUseItem().getItem() instanceof SystemBowItem bow)) return -1;
        Signature sig = bow.signature();
        if (sig == null || !sig.drawn()) return -1;
        if (p == Minecraft.getInstance().player && !drawReady(sig)) return -1;
        return Mth.clamp(p.getTicksUsingItem() / (float) sig.drawTicks(), 0, 1);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        float time = now(0);
        for (Fx fx : List.copyOf(ACTIVE)) {
            Entity e = mc.level.getEntity(fx.entityId);
            float t = (time - fx.start) / 20f;
            float k = fx.impact < 0 ? -1 : (time - fx.impact) / 20f;
            guard(fx, () -> fx.art.tick(fx, e, mc.level, t, k));
        }
        // a drawn art gathering on a bow (the wind-up payload only comes once it is charged)
        for (Player p : mc.level.players()) {
            float g = gathering(p);
            if (g <= 0 || g >= 1 || p.distanceToSqr(mc.player) > 48 * 48) continue;
            if (p.getUseItem().getItem() instanceof SystemBowItem bow && bow.signature() != null) {
                ArtDivine.gatherTick(p, bow.signature(), g, mc.level, p == mc.player && mc.options.getCameraType().isFirstPerson());
            }
        }
    }

    public static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = now(partial);
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        boolean any = false;
        Iterator<Fx> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            float t = (time - fx.start) / 20f;
            float k = fx.impact < 0 ? -1 : (time - fx.impact) / 20f;
            float windup = fx.sig.drawn() ? 60 : fx.sig.impactTicks() / 20f + 2f;
            if ((k < 0 && t > windup) || k > fx.art.life(fx) || t < -2) {
                it.remove();
                continue;
            }
            Entity e = mc.level.getEntity(fx.entityId);
            boolean self = e == mc.player && firstPerson;
            Vec3 live = e != null ? e.getPosition(partial) : fx.at;
            Vec3 origin = k < 0 ? live : fx.at;
            pose.pushPose();
            try {
                pose.translate(origin.x - cam.x, origin.y - cam.y, origin.z - cam.z);
                fx.art.draw(new Ctx(pose, buffers, camera, origin, time, t, k, self, e, partial, live, fx));
            } catch (RuntimeException ex) {
                it.remove();
                if (BROKEN.add(fx.sig)) com.beyondsolo.BeyondSolo.LOGGER.error("Signature art {} failed to draw and was stopped", fx.sig, ex);
            } finally {
                pose.popPose();
            }
            any = true;
        }
        // drawn arts gathering on bows
        for (Player p : mc.level.players()) {
            float g = gathering(p);
            if (g <= 0 || p.distanceToSqr(cam.x, cam.y, cam.z) > 64 * 64) continue;
            if (!(p.getUseItem().getItem() instanceof SystemBowItem bow) || bow.signature() == null) continue;
            boolean self = p == mc.player && firstPerson;
            if (self) continue; // the archer's own eyes see it on the screen
            Vec3 live = p.getPosition(partial);
            pose.pushPose();
            pose.translate(live.x - cam.x, live.y - cam.y, live.z - cam.z);
            ArtDivine.drawGathering(pose, buffers, camera, live, p, bow.signature(), g, time, partial);
            pose.popPose();
            any = true;
        }
        if (!any) return;
        flush(buffers);
    }

    /** Draws everything still open, in order: crystal faces, then the skins, then the light. */
    static void flush(MultiBufferSource.BufferSource buffers) {
        buffers.endBatch(ModRenderTypes.CRYSTAL);
        buffers.endBatch(ModRenderTypes.CRYSTAL_GLOW);
        Skins.flush(buffers);
        for (ResourceLocation tex : new ResourceLocation[]{Aura.POOL, Aura.SPLASH, Aura.HALO, Aura.SPARK, Aura.FEATHER, Aura.SHAFT, Aura.TRAIL, Aura.EDGE}) {
            buffers.endBatch(ModRenderTypes.energy(tex));
        }
        for (ResourceLocation tex : new ResourceLocation[]{SkillWorldFx.ARC, SkillWorldFx.SHOCK, SkillWorldFx.FLARE, SkillWorldFx.STREAK,
                SkillWorldFx.GLOW, SkillWorldFx.SLASH}) {
            buffers.endBatch(ModRenderTypes.lightTexture(tex));
        }
    }

    public static void clear() {
        ACTIVE.clear();
        READY.clear();
    }

    // ------------------------------------------------------------------ helpers shared by the art looks

    /** True the first time it is asked for moment {@code bit} of this art (a landing, a contact), false after. */
    static boolean once(Fx fx, int bit) {
        if ((fx.fired & (1 << bit)) != 0) return false;
        fx.fired |= 1 << bit;
        return true;
    }

    static Vec3 forward(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    static Vec3 rightOf(Vec3 fwd) {
        return new Vec3(-fwd.z, 0, fwd.x);
    }

    /** 0 before a, 1 after b, straight between. */
    static float win(float x, float a, float b) {
        return Mth.clamp((x - a) / (b - a), 0, 1);
    }

    static float out(float x) {
        x = Mth.clamp(x, 0, 1);
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    static float in(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * x;
    }

    /** Rises from 0 at a to 1 at peak, falls back to 0 at b. */
    static float hump(float x, float a, float peak, float b) {
        if (x <= a || x >= b) return 0;
        return x < peak ? (x - a) / (peak - a) : 1 - (x - peak) / (b - peak);
    }

    static double rnd(double s) {
        return (RANDOM.nextDouble() - 0.5) * 2 * s;
    }

    /** The ground under (or just over) a point. */
    static Vec3 ground(ClientLevel level, Vec3 at) {
        BlockHitResult down = level.clip(new ClipContext(at.add(0, 2.5, 0), at.add(0, -4, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, CollisionContext.empty()));
        return down.getType() == HitResult.Type.MISS ? at : down.getLocation();
    }

    /** Where a wielder's weapon hand is, roughly: before the chest, on the right. */
    static Vec3 hand(Entity e, float partial) {
        Vec3 f = forward(e.getViewYRot(partial));
        return e.getPosition(partial).add(f.scale(0.35)).add(rightOf(f).scale(0.38)).add(0, 1.15, 0);
    }

    /** Where a staff's head is while aiming: in front of the eyes, a little low and to the right. */
    static Vec3 staffTip(Entity e, float partial) {
        Vec3 look = e.getViewVector(partial);
        return e.getEyePosition(partial).add(look.scale(1.1)).add(0, -0.3, 0).add(rightOf(forward(e.getViewYRot(partial))).scale(0.18));
    }

    /** How hard the camera jolts at the local player for a blow of {@code strength} at {@code at}. */
    static void shake(Vec3 at, float strength, float reach, int kind) {
        SkillShake.hit(at, strength, reach, kind);
    }

    private ArtFx() {}
}
