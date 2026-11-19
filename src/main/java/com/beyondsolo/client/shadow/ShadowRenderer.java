package com.beyondsolo.client.shadow;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.fx.Aura;
import com.beyondsolo.client.fx.Outline;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.shadow.ShadowRank;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a shadow the way the series draws one: the creature it was, poured in blue-black ink ({@link ShadowTint}),
 * whose silhouette burns at the edge in its rank's light, black flame standing on it and black wisps tearing off its
 * head and shoulders like ink in water ({@link Aura}, read off the model's own outline, so it hugs any creature from
 * any side). Two white-hot eyes trail a comet's tail when it turns or charges. It climbs out of a pool of
 * ink with a thin hot rim when called and sinks back into one when dismissed or fallen: never a circle of light, which
 * belongs to the other side.
 *
 * <p>The ranks show in the burning: a husk barely holds an edge, soldiers burn steady, a knight's seams glow, a captain
 * and up wear a crest of taller flame, a commander (a marshal, a regent) a crown of it, standing in tall spikes; a
 * marshal burns gold; a regent burns crimson with a second, violet edge round it and stands in its own pool, as a
 * boss's shadow does. From a captain on a shadow is also drawn a little larger each rank ({@link #tierScale}).</p>
 */
public class ShadowRenderer extends EntityRenderer<ShadowEntity> {
    static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");

    /** Each rank's burning, built once. */
    private static final Aura.Style[] LOOKS = new Aura.Style[ShadowRank.values().length];
    /** A boss's shadow burns harder, whatever its rank. */
    private static final Aura.Style[] BOSS_LOOKS = new Aura.Style[ShadowRank.values().length];

    static {
        for (ShadowRank r : ShadowRank.values()) {
            LOOKS[r.ordinal()] = look(r);
            BOSS_LOOKS[r.ordinal()] = look(r).rim(0.8f, 0.09f).seams(0.35f).flame(Math.max(0.65f, look(r).flameHeight() * 1.3f)).crest(1.8f)
                    .glow(0.7f).wisps(2.2f, 0.9f);
        }
    }

    private static Aura.Style look(ShadowRank rank) {
        int c = rank.color();
        return switch (rank) {
            case HUSK -> new Aura.Style().hue(c).rim(0.4f, 0.05f).flame(0f).glow(0f).wisps(0.35f, 0.5f);
            case SOLDIER -> new Aura.Style().hue(c).rim(0.5f, 0.06f).flame(0.45f).glow(0.45f).wisps(0.7f, 0.6f);
            case VANGUARD -> new Aura.Style().hue(c).rim(0.55f, 0.065f).seams(0.15f).flame(0.5f).glow(0.5f).wisps(0.9f, 0.65f);
            case KNIGHT -> new Aura.Style().hue(c).rim(0.6f, 0.07f).seams(0.25f).flame(0.56f).crest(1.3f).glow(0.55f).wisps(1.1f, 0.7f);
            case CAPTAIN -> new Aura.Style().hue(c).rim(0.65f, 0.075f).seams(0.3f).flame(0.62f).crest(1.5f).glow(0.6f).wisps(1.3f, 0.75f);
            case COMMANDER -> new Aura.Style().hue(c).rim(0.7f, 0.08f).seams(0.32f).flame(0.68f).crest(1.6f).crown(0.6f).glow(0.62f)
                    .wisps(1.5f, 0.8f);
            case MARSHAL -> new Aura.Style().hue(c).rim(0.75f, 0.085f).seams(0.35f).flame(0.74f).crest(1.75f).crown(0.8f).glow(0.65f)
                    .wisps(1.7f, 0.85f);
            case REGENT -> new Aura.Style().hue(c).second(0x9B5BFF).rim(0.8f, 0.09f).seams(0.38f).flame(0.82f).crest(1.9f).crown(1f).glow(0.7f)
                    .wisps(2.1f, 0.9f);
        };
    }

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
        ShadowRank rank = e.rank();
        int color = rank.color();

        // rising out of the ground, sinking back into it, or falling apart
        float rise = e.preview ? 1f : Mth.clamp(time / e.riseTicks(), 0f, 1f);
        float riseEase = 1 - (1 - rise) * (1 - rise) * (1 - rise);
        float sink = e.isSinking() ? Mth.clamp((e.sinkTicks() + partial) / ShadowEntity.SINK_TICKS, 0f, 1f) : 0f;
        float dead = e.deathTime > 0 ? Mth.clamp((e.deathTime + partial) / ShadowEntity.DEATH_TICKS, 0f, 1f) : 0f;
        float down = (1 - riseEase) * h * 1.05f + sink * sink * h * 1.05f + dead * dead * h * 0.8f;
        float presence = Math.min(riseEase, 1 - Math.max(sink, dead));
        float grow = e.sizeScale() * tierScale(rank);

        // the ground under a shadow coming or going: a pool of ink with a thin rim of its light. A regent and a boss's
        // shadow stand in a pool of their own.
        if (!e.preview) {
            float gate = Math.max(1 - rise, Math.max(sink, dead));
            float open = gate > 0.01f ? Mth.sin(Mth.clamp(gate * 1.6f, 0, 1) * Mth.PI * 0.5f) : 0f;
            float own = (e.isBoss() || rank == ShadowRank.REGENT) && e.isAlive() ? 0.75f * presence : 0f;
            float k = Math.max(open, own);
            if (k > 0.01f) {
                float radius = (w * 0.9f + 0.7f) * grow * (0.6f + 0.4f * k);
                Aura.pool(pose, buffers, 0.03f, radius, color, Math.min(1f, k * 1.2f), time, gate > 0.01f ? 1f : 0.4f);
            }
        }

        LivingEntity look = e.look();
        Outline outline = null;
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
            pose.scale(grow, grow, grow);
            try {
                @SuppressWarnings("unchecked")
                EntityRenderer<LivingEntity> r = (EntityRenderer<LivingEntity>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(look);
                float brightness = e.isBoss() ? 1.25f : 1f;
                outline = Outline.begin();
                r.render(look, yaw, partial, pose, outline.tap(ShadowTint.ink(buffers, brightness, Aura.mix(color, 0xFFFFFF, 0.25f))), light);
            } catch (RuntimeException ignored) {
                // a creature whose renderer can't draw a stand-in: its eyes alone show where it is
                outline = null;
            }
            pose.popPose();
        }

        if (presence > 0.02f) {
            if (outline != null) burn(e, outline, pose, buffers, partial, time, h, presence, light);
            if (e.isAlive()) eyes(e, pose, buffers, partial, time, w, down, color, presence);
        }
        if (!e.preview) super.render(e, yaw, partial, pose, buffers, light);
    }

    /** How much bigger than its size a shadow of a rank its master promoted it to is drawn (its hitbox stays). */
    public static float tierScale(ShadowRank rank) {
        return 1f + 0.04f * Math.max(0, rank.ordinal() - ShadowRank.KNIGHT.ordinal());
    }

    /** The burning edge, the black flame and (in the world) the ink wisps, all off the outline just drawn. */
    private static void burn(ShadowEntity e, Outline outline, PoseStack pose, MultiBufferSource buffers, float partial, float time, float h,
                             float presence, int light) {
        Aura.Style style = (e.isBoss() ? BOSS_LOOKS : LOOKS)[e.rank().ordinal()];
        float size = Mth.clamp(h * tierScale(e.rank()) / 1.9f, 0.6f, 2.6f);
        if (e.preview) {
            // a portrait on a screen: seen from far away straight on, a block is the pose's scale
            Matrix4f m = pose.last().pose();
            Vector3f up = m.transformDirection(new Vector3f(0, 1, 0));
            float unit = up.length();
            if (unit < 1.0e-4f) return;
            up.div(unit);
            outline.solveScreen();
            Aura.edge(outline, buffers, style, time, presence, unit, new Vec3(up.x(), up.y(), up.z()), Vec3.ZERO, 1f, size, e.getId(), 0f);
            return;
        }
        outline.solveWorld();
        double dist = Math.sqrt(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(e.getPosition(partial)));
        float detail = (float) Mth.clamp(1 - (dist - 24) / 40, 0, 1);
        if (dist > 72) return;
        // blown back by its own motion
        Vec3 drift = new Vec3(e.xo - e.getX(), 0, e.zo - e.getZ());
        if (drift.lengthSqr() > 0.25) drift = drift.normalize().scale(0.5);
        Aura.edge(outline, buffers, style, time, presence, 1f, new Vec3(0, 1, 0), drift, Math.max(detail, 0.15f), size, e.getId(),
                Aura.daylight(light, partial));
        if (detail > 0.1f) Aura.wisps(Aura.state(e.getId()), outline, buffers, style, time, presence * detail, drift, size, e.getId());
    }

    /**
     * Two white-hot eyes in front of its face with a halo of its rank's light, each trailing a comet's tail: where the
     * eye was a moment ago, risen and blown back.
     */
    private static void eyes(ShadowEntity e, PoseStack pose, MultiBufferSource buffers, float partial, float time, float w, float down,
                             int color, float k) {
        boolean boss = e.isBoss();
        float headYaw = Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) * Mth.DEG_TO_RAD;
        float fx = -Mth.sin(headYaw), fz = Mth.cos(headYaw);
        float sx = -fz, sz = fx;
        float grow = e.sizeScale() * tierScale(e.rank());
        float eyeY = e.getEyeHeight() * 0.98f * tierScale(e.rank()) - down;
        float fwd = Math.min(1.6f, w * 0.5f + 0.03f) * tierScale(e.rank());
        float gap = Mth.clamp(w * 0.14f, 0.06f, 0.35f) * grow;
        float flicker = 0.85f + 0.15f * Mth.sin(time * 0.9f + e.getId());
        float es = (boss ? 0.12f : 0.065f) * (0.9f + 0.1f * flicker) * Math.max(1f, w * 0.6f);
        int hue = boss ? Aura.mix(color, 0xFF3355, 0.35f) : color;
        Matrix4f m = pose.last().pose();
        Vector3f l = m.transformPosition(new Vector3f(fx * fwd - sx * gap, eyeY, fz * fwd - sz * gap));
        Vector3f r = m.transformPosition(new Vector3f(fx * fwd + sx * gap, eyeY, fz * fwd + sz * gap));
        if (e.preview) {
            // on a screen: two sparks facing the viewer, no trail
            VertexConsumer vc = buffers.getBuffer(ModRenderTypes.energy(Aura.SPARK));
            float unit = m.transformDirection(new Vector3f(0, 1, 0)).length();
            int al = Mth.clamp(Math.round(k * flicker * 255), 0, 255);
            int cr = hue >> 16 & 0xFF, cg = hue >> 8 & 0xFF, cb = hue & 0xFF;
            for (Vector3f at : new Vector3f[]{l, r}) {
                float half = es * 1.1f * unit;
                vc.addVertex(at.x() - half, at.y() - half, at.z() + unit).setUv(0, 1).setColor(cr, cg, cb, al);
                vc.addVertex(at.x() + half, at.y() - half, at.z() + unit).setUv(1, 1).setColor(cr, cg, cb, al);
                vc.addVertex(at.x() + half, at.y() + half, at.z() + unit).setUv(1, 0).setColor(cr, cg, cb, al);
                vc.addVertex(at.x() - half, at.y() + half, at.z() + unit).setUv(0, 0).setColor(cr, cg, cb, al);
            }
            return;
        }
        float tail = (boss ? 0.55f : 0.32f) * Math.max(1f, w * 0.5f);
        Aura.eyes(Aura.state(e.getId()), buffers, new Vec3(l.x(), l.y(), l.z()), new Vec3(r.x(), r.y(), r.z()), new Vec3(-fx, 0, -fz),
                hue, es, k * flicker, tail, time);
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
