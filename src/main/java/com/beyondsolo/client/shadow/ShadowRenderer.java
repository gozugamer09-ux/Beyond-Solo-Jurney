package com.beyondsolo.client.shadow;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.shadow.ShadowRank;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

/**
 * Draws a shadow: the creature it was, poured in ink ({@link ShadowTint}), with two burning eyes, a faint aura and
 * a slow ring at its feet (a magic circle for boss shadows). When called it climbs out of the ground through a
 * circle of light; when dismissed or fallen it sinks back in.
 *
 * <p>The ranks its master promotes it to show: from a knight on its rank's light flows over it ({@link ShadowShell}),
 * brighter each rank; from a captain it is drawn a little larger each rank ({@link #tierScale}); a commander wears a
 * crown of flame and stands on a circle of runes in its rank's colour; a regent's shell burns in two colours.</p>
 */
public class ShadowRenderer extends EntityRenderer<ShadowEntity> {
    static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");
    static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");

    public ShadowRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.6f;
        this.shadowStrength = 1f;
    }

    @Override
    public void render(ShadowEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float time = e.tickCount + partial;
        float h = e.getBbHeight();
        float w = e.getBbWidth();
        int color = e.rank().color();

        // rising out of the ground, sinking back into it, or falling apart
        float rise = e.preview ? 1f : Mth.clamp(time / e.riseTicks(), 0f, 1f);
        float riseEase = 1 - (1 - rise) * (1 - rise) * (1 - rise);
        float sink = e.isSinking() ? Mth.clamp((e.sinkTicks() + partial) / ShadowEntity.SINK_TICKS, 0f, 1f) : 0f;
        float dead = e.deathTime > 0 ? Mth.clamp((e.deathTime + partial) / ShadowEntity.DEATH_TICKS, 0f, 1f) : 0f;
        float down = (1 - riseEase) * h * 1.05f + sink * sink * h * 1.05f + dead * dead * h * 0.8f;
        float presence = Math.min(riseEase, 1 - Math.max(sink, dead));

        // the ground under a shadow coming or going: a circle of violet light
        float gate = e.preview ? 0f : Math.max(1 - rise, Math.max(sink, dead)) ;
        if (gate > 0.01f) {
            float k = Mth.sin(Mth.clamp(gate, 0, 1) * Mth.PI * 0.5f);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.04f, (w * 0.9f + 0.6f) * (0.6f + 0.4f * k), time * 6, color, 0.9f * k);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.05f, (w * 0.9f + 0.6f) * (1.4f - 0.4f * k), 0, 0xFFFFFF, 0.5f * k);
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.06f, w + 0.8f, 0, color, 0.5f * k);
        }

        LivingEntity look = e.look();
        if (look != null) {
            if (e.preview) {
                // a screen preview is never ticked: give the stand-in this frame's pose
                look.yBodyRot = look.yBodyRotO = e.yBodyRot;
                look.setYRot(e.getYRot());
                look.yRotO = e.getYRot();
                look.setXRot(e.getXRot());
                look.yHeadRot = look.yHeadRotO = e.yHeadRot;
                look.tickCount = e.tickCount;
            }
            pose.pushPose();
            pose.translate(0, -down, 0);
            float s = e.sizeScale() * tierScale(e.rank());
            pose.scale(s, s, s);
            try {
                @SuppressWarnings("unchecked")
                EntityRenderer<LivingEntity> r = (EntityRenderer<LivingEntity>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(look);
                float brightness = e.isBoss() ? 1.25f : 1f;
                r.render(look, yaw, partial, pose, ShadowTint.ink(buffers, brightness), light);
                if (presence > 0.02f) shell(r, look, e.rank(), yaw, partial, pose, buffers, light, time, presence);
            } catch (RuntimeException ignored) {
                // a creature whose renderer can't draw a stand-in: the aura alone shows where it is
            }
            pose.popPose();
        }

        if (presence > 0.02f && e.isAlive()) aura(e, pose, buffers, partial, time, w, h, down, color, presence);
        if (presence > 0.02f && e.isAlive() && e.rank().ordinal() >= ShadowRank.COMMANDER.ordinal()) {
            crown(pose, buffers, e, time, w, h * tierScale(e.rank()), down, presence);
        }
        if (!e.preview) super.render(e, yaw, partial, pose, buffers, light);
    }

    /** How much bigger than its size a shadow of a rank its master promoted it to is drawn (its hitbox stays). */
    public static float tierScale(ShadowRank rank) {
        return 1f + 0.04f * Math.max(0, rank.ordinal() - ShadowRank.KNIGHT.ordinal());
    }

    /** The light that flows over a knight or anything higher: the creature again, a little larger, in its rank's wisps. */
    private static void shell(EntityRenderer<LivingEntity> r, LivingEntity look, ShadowRank rank, float yaw, float partial, PoseStack pose,
                              MultiBufferSource buffers, int light, float time, float presence) {
        if (rank.ordinal() < ShadowRank.KNIGHT.ordinal()) return;
        int step = rank.ordinal() - ShadowRank.KNIGHT.ordinal();
        float strength = Math.min(0.8f, 0.42f + 0.09f * step) * presence;
        float grow = 1.03f + 0.006f * step;
        float detail = ShadowShell.detail(r.getTextureLocation(look));
        drawShell(r, look, yaw, partial, pose, buffers, light, new ShadowShell(buffers, time, 1f + 0.15f * step, rank.color(), strength, detail), grow);
        // a regent's light burns in two colours: the crimson of its rank, and under it, faint, the violet of the dark it came from
        if (rank == ShadowRank.REGENT) {
            drawShell(r, look, yaw, partial, pose, buffers, light, new ShadowShell(buffers, time + 300, -1.3f, 0x9B5BFF, strength * 0.35f,
                    detail * 1.6f), grow + 0.02f);
        }
    }

    private static void drawShell(EntityRenderer<LivingEntity> r, LivingEntity look, float yaw, float partial, PoseStack pose,
                                  MultiBufferSource buffers, int light, ShadowShell shell, float grow) {
        float mid = look.getBbHeight() * 0.5f;
        pose.pushPose();
        pose.translate(0, mid, 0);
        pose.scale(grow, grow, grow);
        pose.translate(0, -mid, 0);
        r.render(look, yaw, partial, pose, shell, light);
        pose.popPose();
    }

    /**
     * A commander's crown (and a marshal's, a regent's): tongues of its rank's flame standing in a ring over its head,
     * each leaning out and licking up on its own beat, on a thin band of light. Drawn as real quads round the head, so
     * it reads the same from any side and in the System window's portrait.
     */
    private static void crown(PoseStack pose, MultiBufferSource buffers, ShadowEntity e, float time, float w, float h, float down, float k) {
        ShadowRank rank = e.rank();
        int step = rank.ordinal() - ShadowRank.COMMANDER.ordinal();
        int c = rank.color();
        int hot = GateFx.mix(c, 0xFFFFFF, 0.45f);
        float radius = Mth.clamp(w * 0.32f, 0.16f, 0.6f);
        float base = h + 0.04f - down;
        float tall = (0.26f + 0.06f * step) * Mth.clamp(w * 0.9f + 0.4f, 0.8f, 1.6f);
        int n = 7 + 2 * step;
        float spin = time * 0.6f;
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < n; i++) {
            float a = (i * 360f / n + spin) * Mth.DEG_TO_RAD;
            float beat = 0.75f + 0.25f * Mth.sin(time * 0.35f + i * 1.7f + e.getId());
            float cx = Mth.cos(a) * radius, cz = Mth.sin(a) * radius;
            float tx = -Mth.sin(a), tz = Mth.cos(a);
            float half = radius * 0.42f;
            float lean = 0.12f * tall;
            float top = base + tall * beat;
            int al = Mth.clamp(Math.round(255 * k * 0.85f), 0, 255);
            int r = (i % 3 == 0 ? hot : c) >> 16 & 0xFF, g = (i % 3 == 0 ? hot : c) >> 8 & 0xFF, b = (i % 3 == 0 ? hot : c) & 0xFF;
            out.addVertex(m, cx - tx * half, base - tall * 0.2f, cz - tz * half).setUv(0, 1).setColor(r, g, b, al);
            out.addVertex(m, cx + tx * half, base - tall * 0.2f, cz + tz * half).setUv(1, 1).setColor(r, g, b, al);
            out.addVertex(m, cx + Mth.cos(a) * lean + tx * half * 0.6f, top, cz + Mth.sin(a) * lean + tz * half * 0.6f).setUv(1, 0).setColor(r, g, b, al);
            out.addVertex(m, cx + Mth.cos(a) * lean - tx * half * 0.6f, top, cz + Mth.sin(a) * lean - tz * half * 0.6f).setUv(0, 0).setColor(r, g, b, al);
        }
        // the band they stand on
        int segs = 16;
        int al = Mth.clamp(Math.round(255 * k * 0.6f), 0, 255);
        int r = hot >> 16 & 0xFF, g = hot >> 8 & 0xFF, b = hot & 0xFF;
        float bh = 0.05f + 0.01f * step;
        for (int i = 0; i < segs; i++) {
            float a0 = i * Mth.TWO_PI / segs, a1 = (i + 1) * Mth.TWO_PI / segs;
            float x0 = Mth.cos(a0) * radius, z0 = Mth.sin(a0) * radius, x1 = Mth.cos(a1) * radius, z1 = Mth.sin(a1) * radius;
            out.addVertex(m, x0, base - bh, z0).setUv(0, 0.75f).setColor(r, g, b, al);
            out.addVertex(m, x1, base - bh, z1).setUv(1, 0.75f).setColor(r, g, b, al);
            out.addVertex(m, x1, base + bh, z1).setUv(1, 0.25f).setColor(r, g, b, al);
            out.addVertex(m, x0, base + bh, z0).setUv(0, 0.25f).setColor(r, g, b, al);
        }
    }

    private void aura(ShadowEntity e, PoseStack pose, MultiBufferSource buffers, float partial, float time, float w, float h, float down, int color, float k) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        boolean boss = e.isBoss();
        // eyes: two burning points in front of its face
        float headYaw = Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(headYaw), fz = Mth.cos(headYaw);
        float sx = -fz, sz = fx;
        float eyeY = e.getEyeHeight() * 0.98f - down;
        float fwd = Math.min(1.6f, w * 0.5f + 0.03f);
        float gap = Mth.clamp(w * 0.14f, 0.06f, 0.35f);
        float flicker = 0.85f + 0.15f * Mth.sin(time * 0.9f + e.getId());
        int eye = GateFx.mix(color, 0xFFFFFF, 0.35f);
        float es = (boss ? 0.13f : 0.07f) * (0.9f + 0.1f * flicker) * Math.max(1f, w * 0.6f);
        for (int side = -1; side <= 1; side += 2) {
            float x = fx * fwd + sx * gap * side;
            float z = fz * fwd + sz * gap * side;
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, x, eyeY, z, es, eye, k * flicker);
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, x, eyeY, z, es * 2.6f, color, 0.45f * k * flicker);
        }
        // a faint aura around its body and a slow ring at its feet
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, h * 0.5f - down, 0, Math.max(w, h) * 0.85f, color, 0.13f * k);
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.03f, w * 0.8f + 0.25f, 0, color, 0.28f * k);
        if (boss) {
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.035f, w + 0.9f, time * 1.5f, color, 0.45f * k);
            GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, h * 0.55f - down, 0, Math.max(w, h) * 1.2f, 0xFF4A6A, 0.08f * k);
        } else if (e.rank().ordinal() >= ShadowRank.COMMANDER.ordinal()) {
            // a commander (and higher) stands on a slow circle of runes in its rank's colour
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(CIRCLE)), pose, 0.035f, w * 0.75f + 0.7f, -time * 1.1f, color, 0.38f * k);
        }
    }

    @Override
    protected boolean shouldShowName(ShadowEntity e) {
        return e.hasCustomName() && e.isCustomNameVisible() && !e.isSinking() && e.deathTime == 0;
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowEntity e) {
        return GLOW;
    }
}
