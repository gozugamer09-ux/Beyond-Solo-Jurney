package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.skill.Skill;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The world a great skill makes while it lasts. An ultimate that takes the light covers the sun with a black disc
 * (Total Eclipse) or puts out the world beyond arm's reach (World Without Light); another lets night fall under a
 * huge moon, rolls in a storm that lights the clouds, burns the sky orange under a falling sun, opens the stars,
 * fills the air with spores, ash or petals. Everyone near the caster sees it: the sky, the fog and how far one can see
 * all change.
 *
 * <p>It comes with the wind-up: the world dims while the power gathers, takes its full colour on the blow, holds for
 * the skill's time and then lifts slowly. Purely a look; each ultimate that has one is listed below.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class SkillDomains {
    /** What hangs in a domain's sky. */
    enum Body { NONE, ECLIPSE, MOON, SUN, VOID }

    /** What drifts through a domain's air, round whoever is looking. */
    enum Air { NONE, ASH, MOTES, PETALS, DUST, SPORES, EMBERS }

    /** How a domain looks. */
    static final class Look {
        /** The sky overhead. */
        final int sky;
        /** The fog: the horizon and everything far off. */
        final int fog;
        /** How much of the usual sky it covers, 0..1. */
        final float cover;
        /** How far one sees inside it, in blocks. */
        final float far;
        /** How long it holds after the blow. */
        final float seconds;
        Body body = Body.NONE;
        int bodyColor;
        boolean stars;
        boolean storm;
        /** How high its body hangs over the horizon, in degrees. */
        float high = 15;
        /** The fog starts at once: the dark is right round you, not only far off. */
        boolean close;
        Air air = Air.NONE;
        int airColor;

        Look(int sky, int fog, float cover, float far, float seconds) {
            this.sky = sky;
            this.fog = fog;
            this.cover = cover;
            this.far = far;
            this.seconds = seconds;
        }

        Look body(Body b, int color) {
            body = b;
            bodyColor = color;
            return this;
        }

        /** The body sits just over the horizon: a sun rising. */
        Look rise() {
            high = 4;
            return this;
        }

        Look stars() {
            stars = true;
            return this;
        }

        Look storm() {
            storm = true;
            return this;
        }

        Look close() {
            close = true;
            return this;
        }

        Look air(Air a, int color) {
            air = a;
            airColor = color;
            return this;
        }
    }

    private static final Map<String, Look> LOOKS = new HashMap<>();

    private static void put(String skill, Look look) {
        LOOKS.put(skill, look);
    }

    static {
        // ---- the assassin's: the light taken
        put("total_eclipse", new Look(0x0E0820, 0x24133E, 0.92f, 46, 8).body(Body.ECLIPSE, 0xB474FF).stars());
        put("world_without_light", new Look(0x010103, 0x030206, 0.97f, 15, 4.5f).close().air(Air.MOTES, 0x9A6CFF));
        put("endless_night", new Look(0x070F2A, 0x0E1834, 0.92f, 40, 4).body(Body.MOON, 0xE4ECFF).stars());
        put("pandemic", new Look(0x1A2410, 0x37521E, 0.78f, 30, 7).close().air(Air.SPORES, 0x9CFF5E));
        put("tempest_waltz", new Look(0x3A4A58, 0x7890A0, 0.62f, 52, 3.5f).air(Air.DUST, 0xD8E8F0));
        // ---- the mage's
        put("sun_fall", new Look(0x3A0C04, 0xB8441A, 0.85f, 60, 5).body(Body.SUN, 0xFF9A3A).air(Air.EMBERS, 0xFF7A2A));
        put("judgment_of_thunder", new Look(0x0B0F1C, 0x262F42, 0.9f, 46, 5).storm());
        put("abyssal_deluge", new Look(0x021226, 0x08304A, 0.88f, 22, 6).close().air(Air.MOTES, 0x60C8FF));
        put("astral_cataclysm", new Look(0x04020C, 0x170E32, 0.94f, 60, 6).body(Body.VOID, 0x9C84FF).stars());
        put("genesis_ray", new Look(0x090716, 0x262040, 0.88f, 64, 4).body(Body.SUN, 0xFFF0C8).stars());
        put("singularity", new Look(0x020107, 0x110820, 0.95f, 44, 4.5f).body(Body.VOID, 0xD0B8FF).stars());
        put("requiem", new Look(0x031414, 0x0C3434, 0.88f, 34, 6).close().air(Air.MOTES, 0x5CFFE0));
        // ---- the warrior's
        put("end_of_ages", new Look(0x1C0302, 0x521006, 0.9f, 40, 6).body(Body.ECLIPSE, 0xFF5A20).air(Air.ASH, 0xFF6A2A));
        put("last_stand", new Look(0x300606, 0x6E1810, 0.55f, 60, 4).air(Air.EMBERS, 0xFF4030));
        put("worldshaker_roar", new Look(0x48331A, 0x957044, 0.45f, 56, 2.5f).air(Air.DUST, 0xC8A070));
        // ---- the healer's
        put("blood_tempest", new Look(0x1C0207, 0x520913, 0.88f, 38, 6).body(Body.MOON, 0xFF4646).air(Air.ASH, 0xC0202A));
        // a dawn is a deep sky over a burning horizon with the sun just rising, never a washed-out white
        put("avatar_of_dawn", new Look(0x1C2A64, 0xF4A468, 0.82f, 80, 8).body(Body.SUN, 0xFFE6B0).rise().air(Air.MOTES, 0xFFE8A0));
        put("miracle_of_dawn", new Look(0x22306E, 0xF6B27A, 0.78f, 90, 5).body(Body.SUN, 0xFFEFC8).rise().air(Air.MOTES, 0xFFE8A0));
        put("solstice", new Look(0x103C8C, 0xFFD48C, 0.72f, 90, 5).body(Body.SUN, 0xFFFFFF));
        put("eternal_spring", new Look(0x3C8CD0, 0xF6D6E6, 0.55f, 70, 6).air(Air.PETALS, 0xFFC4E0));
        put("elderwood", new Look(0x243A16, 0x587A36, 0.62f, 40, 6).air(Air.SPORES, 0xC8FF80));
        // ---- the guardian's
        put("day_of_reckoning", new Look(0x280A04, 0x66200E, 0.78f, 48, 5).body(Body.SUN, 0xFF7444).air(Air.EMBERS, 0xFF8A50));
        put("dawnbreaker", new Look(0x241E5C, 0xFF9A64, 0.8f, 90, 3.5f).body(Body.SUN, 0xFFE8C0).rise());
        put("earthen_domain", new Look(0x56401E, 0x9E7A46, 0.72f, 36, 6).air(Air.DUST, 0xC8A070));
        put("pillars_of_creation", new Look(0x382A1A, 0x886842, 0.72f, 44, 5).air(Air.DUST, 0xC8A070));
        put("colossus_form", new Look(0x463216, 0x967242, 0.55f, 56, 4).air(Air.DUST, 0xC8A070));
        // ---- the ranger's
        put("storm_of_a_thousand", new Look(0x121824, 0x323E58, 0.82f, 56, 4.5f).storm());
        put("stormfall", new Look(0x0E1422, 0x2A344E, 0.88f, 50, 5).storm());
        // the heavens open over the whole rain of arrows, till the last one lands
        put("fall_of_heaven", new Look(0x08163E, 0xC8D8FF, 0.82f, 96, 8).body(Body.SUN, 0xFFFFFF).stars().air(Air.MOTES, 0xFFF4C0));
        put("wild_hunt", new Look(0x180E06, 0x3E2618, 0.86f, 44, 9).body(Body.MOON, 0xFFB466));
        put("call_of_the_pack", new Look(0x0A1024, 0x18223E, 0.86f, 44, 4).body(Body.MOON, 0xEAF2FF).stars());
    }

    /** True when the skill makes a domain (for tests and tools). */
    public static boolean has(String skill) {
        return LOOKS.containsKey(skill);
    }

    // ================================================================ domains coming, held, going

    private static final int RISE = 5, LIFT = 34;
    /** How far from its caster a domain is still seen. */
    private static final double REACH = 72;

    private static final class Domain {
        final Look look;
        final AbstractClientPlayer caster;
        final String skill;
        /** Where its body hangs: the way the caster faced. */
        final float yaw;
        final int windup;
        int waited;
        /** Ticks since its blow, or -1 while the caster is still winding up. */
        int age = -1;
        float from;
        float k, kWas;
        float flash, flashWas;

        Domain(Look look, AbstractClientPlayer caster, String skill, int windup) {
            this.look = look;
            this.caster = caster;
            this.skill = skill;
            this.yaw = caster.getYRot();
            this.windup = Math.max(1, windup);
        }

        float k(float partial) {
            return Mth.lerp(partial, kWas, k);
        }

        float flash(float partial) {
            return Mth.lerp(partial, flashWas, flash);
        }

        int life() {
            return Math.round(look.seconds * 20);
        }
    }

    private static final List<Domain> DOMAINS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static long ticks;

    /** A hunter winds up for {@code skill}, its blow {@code ticks} away: the world starts to dim. */
    static void windup(AbstractClientPlayer caster, Skill skill, int ticks) {
        Look look = LOOKS.get(skill.path());
        if (look == null || !near(caster)) return;
        DOMAINS.removeIf(d -> d.caster == caster && d.skill.equals(skill.path()) && d.age < 0);
        DOMAINS.add(new Domain(look, caster, skill.path(), ticks));
    }

    /** {@code skill} went off (or landed its first blow): its domain takes the world. */
    static void cast(AbstractClientPlayer caster, Skill skill) {
        Look look = LOOKS.get(skill.path());
        if (look == null || !near(caster)) return;
        for (Domain d : DOMAINS) {
            if (d.caster != caster || !d.skill.equals(skill.path())) continue;
            if (d.age < 0) {
                d.age = 0;
                d.from = d.k;
            }
            // a later blow of the same cast, or a recast: it holds on
            else if (d.age < d.life()) return;
            else {
                d.age = 0;
                d.from = d.k;
            }
            return;
        }
        Domain d = new Domain(look, caster, skill.path(), 1);
        d.age = 0;
        DOMAINS.add(d);
    }

    private static boolean near(AbstractClientPlayer caster) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        return camera.getPosition().distanceToSqr(caster.position()) < REACH * REACH;
    }

    /** The strongest domain round the camera now, or null. */
    private static Domain dominant() {
        Domain best = null;
        for (Domain d : DOMAINS) if (best == null || d.k + d.kWas > best.k + best.kWas) best = d;
        return best != null && Math.max(best.k, best.kWas) > 0.001f ? best : null;
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        ticks++;
        if (DOMAINS.isEmpty()) return;
        var it = DOMAINS.iterator();
        while (it.hasNext()) {
            Domain d = it.next();
            d.kWas = d.k;
            d.flashWas = d.flash;
            if (d.caster.isRemoved() && d.age < 0 || d.caster.level() != mc.level) {
                it.remove();
                continue;
            }
            if (d.age < 0) {
                // the world holds its breath while the power gathers
                d.waited++;
                float t = Math.min(1f, d.waited / (float) d.windup);
                d.k = 0.55f * t * t;
                if (d.waited > d.windup + 40) it.remove();
                continue;
            }
            d.age++;
            int life = d.life();
            if (d.age <= RISE) d.k = Mth.lerp(d.age / (float) RISE, d.from, 1f);
            else if (d.age <= life) d.k = 1f;
            else d.k = 1f - (d.age - life) / (float) LIFT;
            if (d.k <= 0) {
                it.remove();
                continue;
            }
            // lightning lights the clouds from inside now and then
            d.flash *= 0.55f;
            if (d.look.storm && d.k > 0.6f && RANDOM.nextInt(26) == 0) d.flash = 0.7f + RANDOM.nextFloat() * 0.3f;
        }
        Domain d = dominant();
        if (d != null && d.k > 0.3f && mc.player != null) breathe(d, mc);
    }

    /** What drifts through the domain's air round the one looking. */
    private static void breathe(Domain d, Minecraft mc) {
        if (d.look.air == Air.NONE) return;
        Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
        int n = FxParticles.count(d.look.air == Air.DUST ? 3 : 2);
        for (int i = 0; i < n; i++) {
            if (RANDOM.nextFloat() > d.k) continue;
            // never right at the eye, where a soft speck is drawn as a big blur
            double a = RANDOM.nextDouble() * Mth.TWO_PI, r = 3.5 + RANDOM.nextDouble() * 11;
            double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r, y = c.y - 2 + RANDOM.nextDouble() * 7;
            int col = d.look.airColor;
            switch (d.look.air) {
                case ASH -> FxParticles.add(FxParticles.Tex.EMBER, x, y + 3, z, FxParticles.rnd(0.01), -0.012, FxParticles.rnd(0.01))
                        .life(FxParticles.rf(60, 100)).size(FxParticles.rf(0.03f, 0.06f)).color(0x302824, col).alpha(0.9f)
                        .fade(0.1f, 0.7f).swirl(0.008f).flicker(0.3f);
                case EMBERS -> FxParticles.embers(x, y - 1, z, 0.5, 1, col);
                case MOTES -> FxParticles.motes(x, y, z, 0.5, 1, col, 0.01f);
                case SPORES -> FxParticles.add(FxParticles.Tex.HOT, x, y, z, FxParticles.rnd(0.006), 0.004, FxParticles.rnd(0.006))
                        .life(FxParticles.rf(50, 90)).size(FxParticles.rf(0.03f, 0.06f), 0.02f).color(0xFFFFFF, col).alpha(0.8f)
                        .fade(0.2f, 0.6f).swirl(0.01f).flicker(0.2f);
                case PETALS -> FxParticles.flutter(FxParticles.Tex.PETAL, x, y + 3, z, 0.5, 1, 0.02f, col, 0.09f);
                case DUST -> FxParticles.add(FxParticles.Tex.SOFT, x, y - 1, z, 0.05 + FxParticles.rnd(0.02), 0.002, FxParticles.rnd(0.02))
                        .blend().life(FxParticles.rf(40, 70)).size(FxParticles.rf(0.3f, 0.6f), FxParticles.rf(0.8f, 1.3f)).color(col)
                        .alpha(FxParticles.rf(0.12f, 0.22f)).fade(0.25f, 0.5f).drag(0.98f);
                default -> {}
            }
        }
    }

    // ================================================================ the fog: its colour and how far one sees

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void fogColor(ViewportEvent.ComputeFogColor event) {
        Domain d = dominant();
        if (d == null) return;
        float partial = (float) event.getPartialTick();
        float k = smooth(d.k(partial));
        int c = lerpColor(d.look.fog, 0xC8D6FF, d.flash(partial) * 0.6f);
        event.setRed(Mth.lerp(k, event.getRed(), ((c >> 16) & 255) / 255f));
        event.setGreen(Mth.lerp(k, event.getGreen(), ((c >> 8) & 255) / 255f));
        event.setBlue(Mth.lerp(k, event.getBlue(), (c & 255) / 255f));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    static void fog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        Domain d = dominant();
        if (d == null) return;
        float k = smooth(d.k((float) event.getPartialTick()));
        float far = Mth.lerp(k, event.getFarPlaneDistance(), Math.min(event.getFarPlaneDistance(), d.look.far));
        float near = Mth.lerp(k, event.getNearPlaneDistance(), d.look.close ? 0f : far * 0.12f);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(near, far * 0.9f));
        event.setCanceled(true);
    }

    // ================================================================ the sky

    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    private static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");
    private static final ResourceLocation RAYS = BeyondSolo.id("textures/effect/fx/rays.png");

    /** Flat colour laid over the sky: no depth, so it never hides the land, which is drawn after it. */
    private static final RenderType SHADE = RenderType.create("beyondsolo_domain_shade",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    @SubscribeEvent
    static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Domain d = dominant();
        if (d == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float k = smooth(d.k(partial));
        if (k < 0.01f) return;
        float time = ticks + partial;
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        float flash = d.flash(partial);
        int top = lerpColor(d.look.sky, 0xB8C8F0, flash * 0.5f), low = lerpColor(d.look.fog, 0xC8D6FF, flash * 0.6f);
        dome(buffers.getBuffer(SHADE), pose.last().pose(), top, low, d.look.cover * k);
        buffers.endBatch(SHADE);
        if (d.look.stars) stars(buffers, pose.last().pose(), k, time);
        if (d.look.body != Body.NONE) body(buffers, pose, d, k, time);
    }

    /** A sphere round the eye, the domain's sky colour overhead fading to its fog at the horizon and below. */
    private static void dome(VertexConsumer out, Matrix4f m, int top, int low, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2) return;
        int seg = 24;
        float[] bands = {-90, -20, 0, 18, 45, 90};
        float r = 12;
        for (int b = 0; b < bands.length - 1; b++) {
            float p0 = bands[b] * Mth.DEG_TO_RAD, p1 = bands[b + 1] * Mth.DEG_TO_RAD;
            int c0 = band(bands[b], top, low), c1 = band(bands[b + 1], top, low);
            for (int i = 0; i < seg; i++) {
                float y0 = Mth.TWO_PI * i / seg, y1 = Mth.TWO_PI * (i + 1) / seg;
                float[] v00 = dir(y0, p0, r), v10 = dir(y1, p0, r), v01 = dir(y0, p1, r), v11 = dir(y1, p1, r);
                vertex(out, m, v00, c0, a);
                vertex(out, m, v10, c0, a);
                vertex(out, m, v11, c1, a);
                vertex(out, m, v00, c0, a);
                vertex(out, m, v11, c1, a);
                vertex(out, m, v01, c1, a);
            }
        }
    }

    private static int band(float pitch, int top, int low) {
        if (pitch <= 0) return low;
        return lerpColor(low, top, Mth.clamp(pitch / 50f, 0, 1));
    }

    private static float[] dir(float yaw, float pitch, float r) {
        float c = Mth.cos(pitch);
        return new float[]{Mth.cos(yaw) * c * r, Mth.sin(pitch) * r, Mth.sin(yaw) * c * r};
    }

    private static void vertex(VertexConsumer out, Matrix4f m, float[] v, int rgb, int a) {
        out.addVertex(m, v[0], v[1], v[2]).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a);
    }

    /** Stars come out over the domain, the same ones each time, twinkling. */
    private static void stars(MultiBufferSource.BufferSource buffers, Matrix4f view, float k, float time) {
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
        RandomSource r = RandomSource.create(7);
        PoseStack pose = new PoseStack();
        pose.mulPose(view);
        for (int i = 0; i < 110; i++) {
            float yaw = r.nextFloat() * Mth.TWO_PI, pitch = (float) Math.asin(0.08 + r.nextFloat() * 0.92);
            float size = 0.03f + r.nextFloat() * r.nextFloat() * 0.09f;
            float tw = 0.6f + 0.4f * Mth.sin(time * (0.05f + r.nextFloat() * 0.1f) + i);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotation(-yaw));
            pose.mulPose(Axis.XP.rotation(-pitch));
            pose.translate(0, 0, 11);
            GateFx.quad(out, pose.last().pose(), size, size, i % 9 == 0 ? 0xCFDBFF : 0xFFFFFF, k * tw);
            pose.popPose();
        }
        buffers.endBatch(ModRenderTypes.lightTexture(GLOW));
    }

    /** The eclipse, the moon, the sun or the void, hung high where the caster faced. */
    private static void body(MultiBufferSource.BufferSource buffers, PoseStack pose, Domain d, float k, float time) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-d.yaw));
        // low over the horizon ahead, where a fighter's eyes are, not overhead
        pose.mulPose(Axis.XP.rotationDegrees(-d.look.high));
        pose.translate(0, 0, 10.5);
        Matrix4f m = pose.last().pose();
        int c = d.look.bodyColor;
        float breathe = 1 + 0.03f * Mth.sin(time * 0.05f);
        switch (d.look.body) {
            case ECLIPSE -> {
                glow(buffers, m, 9f * breathe, c, 0.55f * k);
                glow(buffers, m, 3.6f * breathe, lerpColor(c, 0xFFFFFF, 0.35f), 0.85f * k);
                spin(buffers, pose, RING, 2.35f, time * 0.15f, lerpColor(c, 0xFFFFFF, 0.5f), 0.9f * k);
                disc(buffers, m, 0, 0, 1.6f, 0x050205, 0x0E0610, k);
                ring(buffers, m, 1.72f, 0xFFF0FF, 0.55f * k);
            }
            case MOON -> {
                glow(buffers, m, 5.5f * breathe, c, 0.45f * k);
                disc(buffers, m, 0, 0, 1.35f, lerpColor(c, 0xFFFFFF, 0.3f), c, k);
                // the shadow that makes it a crescent is the sky's own colour
                disc(buffers, m, 0.62f, 0.3f, 1.22f, d.look.sky, d.look.sky, k);
                ring(buffers, m, 1.5f, c, 0.35f * k);
            }
            case SUN -> {
                glow(buffers, m, 13f * breathe, c, 0.5f * k);
                spin(buffers, pose, RAYS, 6.5f, time * 0.08f, c, 0.55f * k);
                spin(buffers, pose, RAYS, 5f, -time * 0.11f, lerpColor(c, 0xFFFFFF, 0.5f), 0.45f * k);
                glow(buffers, m, 3.2f, 0xFFFFFF, 0.95f * k);
                glow(buffers, m, 1.8f, 0xFFFFFF, k);
            }
            case VOID -> {
                glow(buffers, m, 8f * breathe, c, 0.4f * k);
                // the ring of what falls in, seen almost edge on, turning
                pose.pushPose();
                pose.mulPose(Axis.XP.rotationDegrees(68));
                spin(buffers, pose, RING, 3.6f, time * 0.9f, lerpColor(c, 0xFFFFFF, 0.4f), 0.95f * k);
                spin(buffers, pose, RING, 2.7f, -time * 1.3f, c, 0.8f * k);
                pose.popPose();
                disc(buffers, m, 0, 0, 1.15f, 0x000000, 0x020104, k);
                ring(buffers, m, 1.28f, 0xFFFFFF, 0.8f * k);
            }
            default -> {}
        }
        pose.popPose();
    }

    private static void glow(MultiBufferSource.BufferSource buffers, Matrix4f m, float size, int rgb, float alpha) {
        GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), m, size, size, rgb, alpha);
        buffers.endBatch(ModRenderTypes.lightTexture(GLOW));
    }

    private static void ring(MultiBufferSource.BufferSource buffers, Matrix4f m, float size, int rgb, float alpha) {
        GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), m, size, size, rgb, alpha);
        buffers.endBatch(ModRenderTypes.lightTexture(RING));
    }

    private static void spin(MultiBufferSource.BufferSource buffers, PoseStack pose, ResourceLocation tex, float size, float degrees, int rgb, float alpha) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(degrees));
        GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose.last().pose(), size, size, rgb, alpha);
        buffers.endBatch(ModRenderTypes.lightTexture(tex));
        pose.popPose();
    }

    /** A flat disc of colour, {@code mid} at its centre and {@code edge} at its rim. */
    private static void disc(MultiBufferSource.BufferSource buffers, Matrix4f m, float cx, float cy, float r, int mid, int edge, float alpha) {
        VertexConsumer out = buffers.getBuffer(SHADE);
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        int n = 40;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            out.addVertex(m, cx, cy, -0.01f).setColor((mid >> 16) & 255, (mid >> 8) & 255, mid & 255, a);
            out.addVertex(m, cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, -0.01f).setColor((edge >> 16) & 255, (edge >> 8) & 255, edge & 255, a);
            out.addVertex(m, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, -0.01f).setColor((edge >> 16) & 255, (edge >> 8) & 255, edge & 255, a);
        }
        buffers.endBatch(SHADE);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = Math.round(Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255));
        int bl = Math.round(Mth.lerp(t, a & 255, b & 255));
        return (r << 16) | (g << 8) | bl;
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        DOMAINS.clear();
    }

    private SkillDomains() {}
}
