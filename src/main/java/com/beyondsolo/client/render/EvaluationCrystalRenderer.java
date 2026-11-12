package com.beyondsolo.client.render;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.evaluation.EvaluationCrystalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the Evaluation Crystal: a faceted shard that floats and turns over its dais, small shards circling it,
 * a magic circle on the floor and a thin beam of light. Everything speeds up and brightens while a player is
 * being evaluated, and takes the colour of their rank when it is decided
 * (the state comes from {@link EvaluationCrystalBlockEntity}).
 */
public class EvaluationCrystalRenderer implements BlockEntityRenderer<EvaluationCrystalBlockEntity> {
    private static final ResourceLocation MAGIC_CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");

    // Shapes are rings of {radius, height} from the top tip to the bottom tip, turned around the vertical axis.
    private static final float[][] GEM = {{0f, 0.44f}, {0.225f, 0.14f}, {0.225f, -0.11f}, {0f, -0.50f}};
    private static final float[][] SHARD = {{0f, 0.085f}, {0.042f, 0f}, {0f, -0.085f}};
    private static final int SHARDS = 3;
    private static final int MOTES = 7;
    private static final Vector3f LIGHT = new Vector3f(0.35f, 0.85f, 0.4f).normalize();
    private static final float CIRCLE_Y = 0.09f;

    public EvaluationCrystalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EvaluationCrystalBlockEntity crystal, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = crystal.getLevel();
        if (level == null) return;
        float time = (level.getGameTime() % 24000L) + partialTick;
        float activity = crystal.activity(partialTick);
        float flash = crystal.flash(partialTick);
        int rgb = crystal.color(partialTick);
        float spin = crystal.spin(partialTick) * Mth.DEG_TO_RAD;
        float energy = Math.min(1f, 0.45f * activity + flash);
        float bob = Mth.sin(time * 0.07f) * 0.035f * (1 - 0.6f * activity);
        float gemY = EvaluationCrystalBlockEntity.GEM_HEIGHT + bob;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        BlockPos pos = crystal.getBlockPos();
        Vector3f toCamera = new Vector3f((float) (eye.x - (pos.getX() + 0.5)), (float) (eye.y - (pos.getY() + gemY)), (float) (eye.z - (pos.getZ() + 0.5)));
        if (toCamera.lengthSquared() < 1e-6f) toCamera.set(0, 0, 1);
        toCamera.normalize();

        // the circle on the floor: faint while idle, bright and layered during an evaluation
        int pale = mix(rgb, 0xFFFFFF, 0.35f);
        circle(pose, buffers, CIRCLE_Y, 1.45f + 0.1f * activity, time * 0.35f + spin * 3f, pale, 0.42f + 0.45f * activity + 0.3f * flash);
        circle(pose, buffers, CIRCLE_Y + 0.004f, 0.78f, -time * 0.9f, pale, 0.75f * activity);

        // the beam: a thread of light while idle, a pillar during an evaluation
        pose.pushPose();
        pose.translate(0f, 0.3f, 0f);
        BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partialTick, 1f, level.getGameTime(), 1, BeaconRenderer.MAX_RENDER_Y,
                0xFF000000 | mix(rgb, 0xFFFFFF, 0.2f), 0.025f + 0.075f * activity + 0.06f * flash, 0.045f + 0.105f * activity + 0.10f * flash);
        pose.popPose();

        pose.pushPose();
        pose.translate(0.5f, gemY, 0.5f);
        Matrix4f m = pose.last().pose();

        // the crystal and the shards around it: far faces first so the near ones blend over them
        float[] gem = triangles(GEM, 6, spin, 1f + 0.08f * flash);
        float[][] shards = new float[SHARDS][];
        float[][] shardAt = new float[SHARDS][3];
        for (int i = 0; i < SHARDS; i++) {
            float angle = time * (0.045f + 0.11f * activity) + i * Mth.TWO_PI / SHARDS + spin * 0.2f;
            float radius = 0.47f - 0.07f * activity + 0.03f * Mth.sin(time * 0.05f + i * 2.1f);
            shardAt[i][0] = Mth.cos(angle) * radius;
            shardAt[i][1] = 0.06f * Mth.sin(time * 0.06f + i * 1.9f) + (i - 1) * 0.14f;
            shardAt[i][2] = Mth.sin(angle) * radius;
            shards[i] = triangles(SHARD, 4, time * 0.12f + i * 1.3f, 1f);
        }
        VertexConsumer faces = buffers.getBuffer(ModRenderTypes.CRYSTAL);
        for (int pass = 0; pass < 2; pass++) {
            boolean front = pass == 1;
            faces(faces, m, gem, 0, 0, 0, toCamera, front, rgb, energy, GEM[GEM.length - 1][1], GEM[0][1]);
            for (int i = 0; i < SHARDS; i++) {
                faces(faces, m, shards[i], shardAt[i][0], shardAt[i][1], shardAt[i][2], toCamera, front, rgb, energy, -0.085f, 0.085f);
            }
        }

        // light, drawn with a soft dot that always faces the viewer: a halo behind the crystal, a glow at its
        // heart (placed just in front of it, so it shows through the faces) and motes drifting up around it
        VertexConsumer light = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
        float halo = 0.66f + 0.22f * activity + 0.9f * flash + 0.03f * Mth.sin(time * 0.11f);
        dot(light, pose, camera, 0, 0, 0, halo, pale, Math.min(1f, 0.34f + 0.3f * activity + 0.6f * flash));
        dot(light, pose, camera, toCamera.x * 0.27f, toCamera.y * 0.27f, toCamera.z * 0.27f, 0.21f + 0.08f * activity + 0.3f * flash,
                mix(rgb, 0xFFFFFF, 0.7f), Math.min(1f, 0.42f + 0.4f * energy));
        for (int i = 0; i < MOTES; i++) {
            float life = 90 + i * 13;
            float u = ((time + i * 37.7f) % life) / life;
            float angle = i * 2.4f + u * (1.5f + 3f * activity);
            float radius = 0.36f + 0.12f * Mth.sin(i * 1.7f);
            dot(light, pose, camera, Mth.cos(angle) * radius, -0.5f + 1.3f * u, Mth.sin(angle) * radius,
                    0.035f + 0.012f * (i % 3), mix(rgb, 0xFFFFFF, 0.6f), Mth.sin(u * Mth.PI) * (0.75f + 0.25f * activity));
        }
        shell(buffers.getBuffer(ModRenderTypes.CRYSTAL_GLOW), m, triangles(GEM, 6, spin, 1.12f + 0.1f * flash), toCamera, rgb, 0.22f + 0.3f * activity + 0.5f * flash);

        pose.popPose();
    }

    /** Builds the triangles of a shape (9 numbers each: three corners), centred on the origin. */
    private static float[] triangles(float[][] profile, int sides, float spin, float scale) {
        int last = profile.length - 1;
        float[] out = new float[sides * 2 * (last - 1) * 9];
        int n = 0;
        for (int i = 0; i < sides; i++) {
            float a0 = spin + i * Mth.TWO_PI / sides;
            float a1 = spin + (i + 1) * Mth.TWO_PI / sides;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            for (int j = 0; j < last; j++) {
                float r0 = profile[j][0] * scale, y0 = profile[j][1] * scale;
                float r1 = profile[j + 1][0] * scale, y1 = profile[j + 1][1] * scale;
                // a band between two rings is two triangles; at a tip one of them collapses and is left out
                if (r0 > 0) {
                    n = put(out, n, c0 * r0, y0, s0 * r0);
                    n = put(out, n, c0 * r1, y1, s0 * r1);
                    n = put(out, n, c1 * r0, y0, s1 * r0);
                }
                if (r1 > 0) {
                    n = put(out, n, c1 * r0, y0, s1 * r0);
                    n = put(out, n, c0 * r1, y1, s0 * r1);
                    n = put(out, n, c1 * r1, y1, s1 * r1);
                }
            }
        }
        return out;
    }

    private static int put(float[] out, int n, float x, float y, float z) {
        out[n] = x;
        out[n + 1] = y;
        out[n + 2] = z;
        return n + 3;
    }

    /**
     * Draws the faces of a shape that point towards the viewer ({@code front}) or away from them, lit from above,
     * with a glint when a face catches the light and a paler rim.
     */
    private static void faces(VertexConsumer out, Matrix4f m, float[] tris, float ox, float oy, float oz, Vector3f toCamera,
                              boolean front, int rgb, float energy, float yMin, float yMax) {
        float red = ((rgb >> 16) & 0xFF) / 255f;
        float green = ((rgb >> 8) & 0xFF) / 255f;
        float blue = (rgb & 0xFF) / 255f;
        float hx = LIGHT.x + toCamera.x, hy = LIGHT.y + toCamera.y, hz = LIGHT.z + toCamera.z;
        float hl = Mth.sqrt(hx * hx + hy * hy + hz * hz);
        if (hl > 1e-4f) {
            hx /= hl;
            hy /= hl;
            hz /= hl;
        }
        for (int t = 0; t < tris.length; t += 9) {
            float nx = normal(tris, t, 0), ny = normal(tris, t, 1), nz = normal(tris, t, 2);
            float nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-7f) continue;
            nx /= nl;
            ny /= nl;
            nz /= nl;
            // point the normal away from the middle of the shape
            if (nx * (tris[t] + tris[t + 3] + tris[t + 6]) + ny * (tris[t + 1] + tris[t + 4] + tris[t + 7]) + nz * (tris[t + 2] + tris[t + 5] + tris[t + 8]) < 0) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            float facing = nx * toCamera.x + ny * toCamera.y + nz * toCamera.z;
            if ((facing >= 0) != front) continue;
            float diffuse = Math.max(0f, nx * LIGHT.x + ny * LIGHT.y + nz * LIGHT.z);
            float glint = (float) Math.pow(Math.max(0f, nx * hx + ny * hy + nz * hz), 28);
            float rim = (1 - Math.abs(facing)) * (1 - Math.abs(facing));
            float shade = front ? 0.42f + 0.58f * diffuse + 0.25f * rim + 0.35f * energy : 0.26f + 0.20f * diffuse + 0.25f * energy;
            float white = front ? Math.min(1f, 0.06f + 0.9f * glint + 0.22f * rim + 0.25f * energy) : 0.03f;
            float alpha = front ? 0.9f : 0.92f;
            for (int v = 0; v < 9; v += 3) {
                float y = tris[t + v + 1];
                float lift = shade * (0.86f + 0.28f * (y - yMin) / (yMax - yMin));
                out.addVertex(m, ox + tris[t + v], oy + y, oz + tris[t + v + 2]).setColor(
                        Mth.clamp(red * lift * (1 - white) + white, 0f, 1f),
                        Mth.clamp(green * lift * (1 - white) + white, 0f, 1f),
                        Mth.clamp(blue * lift * (1 - white) + white, 0f, 1f),
                        alpha);
            }
        }
    }

    /** One component (0 = x, 1 = y, 2 = z) of a triangle's unnormalised normal. */
    private static float normal(float[] tris, int t, int axis) {
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        float ua = tris[t + 3 + a] - tris[t + a], ub = tris[t + 3 + b] - tris[t + b];
        float va = tris[t + 6 + a] - tris[t + a], vb = tris[t + 6 + b] - tris[t + b];
        return ua * vb - ub * va;
    }

    /** A slightly larger copy of the shape drawn as light, strongest where the surface turns away from the viewer. */
    private static void shell(VertexConsumer out, Matrix4f m, float[] tris, Vector3f toCamera, int rgb, float strength) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        for (int t = 0; t < tris.length; t += 9) {
            float nx = normal(tris, t, 0), ny = normal(tris, t, 1), nz = normal(tris, t, 2);
            float nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-7f) continue;
            float facing = Math.abs(nx * toCamera.x + ny * toCamera.y + nz * toCamera.z) / nl;
            int alpha = Mth.clamp(Math.round(255 * strength * (0.25f + 0.75f * (1 - facing) * (1 - facing))), 0, 255);
            for (int v = 0; v < 9; v += 3) {
                out.addVertex(m, tris[t + v], tris[t + v + 1], tris[t + v + 2]).setColor(red, green, blue, alpha);
            }
        }
    }

    /** A soft dot of light at an offset from the crystal, turned to face the viewer. */
    private static void dot(VertexConsumer out, PoseStack pose, Camera camera, float x, float y, float z, float half, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camera.rotation());
        sprite(out, pose.last().pose(), half, rgb, alpha);
        pose.popPose();
    }

    /** A square of the given half size around the origin of {@code m}, in its XY plane. */
    private static void sprite(VertexConsumer out, Matrix4f m, float half, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        out.addVertex(m, -half, -half, 0).setUv(0, 1).setColor(red, green, blue, a);
        out.addVertex(m, half, -half, 0).setUv(1, 1).setColor(red, green, blue, a);
        out.addVertex(m, half, half, 0).setUv(1, 0).setColor(red, green, blue, a);
        out.addVertex(m, -half, half, 0).setUv(0, 0).setColor(red, green, blue, a);
    }

    private static void circle(PoseStack pose, MultiBufferSource buffers, float y, float radius, float degrees, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(0.5f, y, 0.5f);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(MAGIC_CIRCLE)), pose.last().pose(), radius, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    private static int mix(int a, int b, float t) {
        int red = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int green = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int blue = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (red << 16) | (green << 8) | blue;
    }

    /** The beam and the circle reach well outside the block, so keep drawing when the block itself is off screen. */
    @Override
    public boolean shouldRenderOffScreen(EvaluationCrystalBlockEntity crystal) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(EvaluationCrystalBlockEntity crystal) {
        BlockPos pos = crystal.getBlockPos();
        return new AABB(pos.getX() - 2, pos.getY(), pos.getZ() - 2, pos.getX() + 3, BeaconRenderer.MAX_RENDER_Y, pos.getZ() + 3);
    }
}
