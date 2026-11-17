package com.beyondsolo.client.skill;

import com.beyondsolo.network.SkillFxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import static com.beyondsolo.client.skill.SkillWorldFx.disc;
import static com.beyondsolo.client.skill.SkillWorldFx.dot;
import static com.beyondsolo.client.skill.SkillWorldFx.mix;
import static com.beyondsolo.client.skill.SkillWorldFx.streak;

/**
 * The Wildcaller's beasts: figures of starlight, drawn the way a constellation is, with a star at every joint and
 * lines of light between them. They are built in three dimensions (legs and wings stand out to both sides), so a
 * beast reads as a beast from wherever it is seen. Nothing here is a creature in the world: the server times the
 * blow, and this only shows what struck.
 *
 * <p>Joints are {forward, up, left} in blocks for a beast of scale 1.</p>
 */
final class StarBeasts {
    private static final int HAWK = SkillFxPayload.BEAST_HAWK, WOLF = SkillFxPayload.BEAST_WOLF, BOAR = SkillFxPayload.BEAST_BOAR,
            BEAR = SkillFxPayload.BEAST_BEAR;

    private static final int[][] WOLF_BONES = {
            // head and throat
            {0, 1}, {1, 2}, {1, 3}, {0, 4}, {4, 6}, {1, 5},
            // spine and tail, breast and belly
            {5, 7}, {7, 8}, {8, 10}, {10, 11}, {6, 9},
            // shoulders and haunches stand out to both sides, with a flank between them
            {5, 12}, {5, 13}, {12, 6}, {13, 6}, {8, 14}, {8, 15}, {9, 14}, {9, 15}, {12, 14}, {13, 15},
            // legs
            {12, 16}, {16, 17}, {13, 18}, {18, 19}, {14, 20}, {20, 21}, {15, 22}, {22, 23}};
    private static final int[][] BOAR_BONES = {
            // head, tusks, hump and back
            {0, 1}, {1, 2}, {1, 3}, {0, 4}, {4, 13}, {5, 6}, {7, 8}, {1, 9}, {9, 10}, {10, 11}, {11, 12}, {13, 14},
            // shoulders and haunches stand out to both sides, with a flank between them
            {9, 15}, {9, 16}, {15, 13}, {16, 13}, {11, 17}, {11, 18}, {14, 17}, {14, 18}, {15, 17}, {16, 18},
            // legs
            {15, 19}, {19, 20}, {16, 21}, {21, 22}, {17, 23}, {23, 24}, {18, 25}, {25, 26}};
    private static final int[][] HAWK_BONES = {{0, 1}, {1, 2}, {1, 3}, {2, 4}, {3, 4}, {4, 5}, {4, 6}, {5, 6}, {3, 7}, {7, 8}, {8, 9}, {9, 4},
            {3, 10}, {10, 11}, {11, 12}, {12, 4}, {2, 13}, {2, 14}};
    private static final int[][] BEAR_BONES = {{0, 1}, {1, 2}, {1, 3}, {0, 4}, {4, 6}, {1, 5}, {5, 7}, {7, 15}, {15, 16}, {6, 8}, {8, 16},
            {5, 9}, {5, 12}, {9, 10}, {10, 11}, {12, 13}, {13, 14}, {6, 9}, {6, 12}, {15, 17}, {17, 18}, {15, 19}, {19, 20}};
    private static final int[][] SITTING_BONES = {{0, 1}, {1, 2}, {1, 3}, {0, 4}, {4, 6}, {1, 5}, {5, 6}, {5, 7}, {7, 8}, {6, 9}, {6, 10},
            {8, 11}, {11, 12}, {8, 13}, {13, 14}, {8, 15}, {15, 16}, {6, 8}};

    /** Seconds a beast is seen: the time the server gave it, and a little after to leave in. */
    static float life(int species, float seconds) {
        return switch (species) {
            case HAWK -> seconds + 0.7f;
            case WOLF, BOAR -> seconds + 0.2f;
            case BEAR -> seconds + 0.6f;
            default -> Math.max(0.6f, seconds);
        };
    }

    /**
     * Draws a beast at its moment.
     *
     * @param at      where the pose's origin is in the world
     * @param origin  where the caster stood when they called it
     * @param yaw     the way the caster faced then
     * @param target  the point the server named
     * @param seconds the time the server gave it to get there (or to stay)
     */
    static void render(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 at, Vec3 origin, float yaw, Vec3 target,
                       int species, float seconds, float scale, int color, float age, float time) {
        Vec3 look = Vec3.directionFromRotation(0, yaw);
        Vec3 right = new Vec3(-look.z, 0, look.x);
        switch (species) {
            case HAWK -> {
                // it stoops from high behind the caster with its wings folded, strikes, and climbs away
                Vec3 start = origin.add(look.scale(-6)).add(0, 10, 0);
                Vec3 bend = target.add(look.scale(-5)).add(0, 3.2, 0);
                Vec3 pos, before;
                float tuck, flap, strike, alpha;
                if (age < seconds) {
                    float k = age / seconds;
                    float kk = (float) Math.pow(k, 1.5);
                    pos = bezier(start, bend, target, kk);
                    before = bezier(start, bend, target, Math.max(0, kk - 0.03f));
                    strike = Mth.clamp((k - 0.8f) / 0.2f, 0, 1);
                    tuck = Mth.clamp((k - 0.1f) / 0.3f, 0, 1) * (1 - strike);
                    flap = Mth.sin(time * 0.9f) * (1 - tuck);
                    alpha = Mth.clamp(age / 0.12f, 0, 1);
                    Vec3 wake = bezier(start, bend, target, Math.max(0, kk - 0.14f));
                    streak(pose, buffers, camera, at, wake.subtract(at), pos.subtract(at), 0.3f * scale, color, alpha * 0.35f);
                } else {
                    float j = Mth.clamp((age - seconds) / 0.7f, 0, 1);
                    float j0 = j - 0.03f;
                    pos = target.add(look.scale(7 * j)).add(0, 5.5 * j * j, 0);
                    before = target.add(look.scale(7 * j0)).add(0, 5.5 * j0 * Math.abs(j0), 0);
                    strike = 1 - j;
                    tuck = 0;
                    flap = Mth.sin(time * 0.9f);
                    alpha = 1 - j;
                }
                Vec3 fwd = pos.subtract(before);
                fwd = fwd.lengthSqr() < 1.0e-6 ? look : fwd.normalize();
                draw(pose, buffers, camera, at, pos.subtract(at), fwd, scale, hawk(flap, tuck, strike), HAWK_BONES, color, alpha, time);
            }
            case WOLF, BOAR -> {
                // it runs the way the caster faces, from beside them to the point, and on a little while it fades
                double run = Math.max(1.5, target.subtract(origin).dot(look));
                Vec3 from = target.subtract(look.scale(run - 0.8));
                from = new Vec3(from.x, origin.y, from.z);
                Vec3 pos = from.lerp(target, age / seconds);
                float alpha = Mth.clamp(age / 0.1f, 0, 1) * Mth.clamp((seconds + 0.2f - age) / 0.2f, 0, 1);
                float stride = age * (species == WOLF ? 19 : 22);
                Vec3 here = pos.subtract(at);
                draw(pose, buffers, camera, at, here, look, scale, species == WOLF ? wolf(stride) : boar(stride),
                        species == WOLF ? WOLF_BONES : BOAR_BONES, color, alpha, time);
                // the light it leaves behind it
                Vec3 back = here.add(0, 0.7 * scale, 0);
                streak(pose, buffers, camera, at, back.subtract(look.scale(2.6 * scale)), back.subtract(look.scale(0.7 * scale)), 0.55f * scale, color, alpha * 0.22f);
            }
            case BEAR -> {
                // it rears over the spot, and then it comes down on it with both paws
                Vec3 base = target.subtract(look.scale(0.56 * scale)).subtract(at);
                float k = Mth.clamp(age / seconds, 0, 1);
                float rear;
                if (k < 0.4f) rear = 1 - (1 - k / 0.4f) * (1 - k / 0.4f);
                else if (k < 0.8f) rear = 1;
                else rear = 1 - (k - 0.8f) / 0.2f * ((k - 0.8f) / 0.2f);
                float alpha = Mth.clamp(age / 0.12f, 0, 1) * Mth.clamp((seconds + 0.6f - age) / 0.45f, 0, 1);
                draw(pose, buffers, camera, at, base, look, scale, bear(rear), BEAR_BONES, color, alpha, time);
            }
            default -> {
                // a wolf that sits by the caster, lifts its head and howls
                Vec3 base = origin.add(right.scale(1.2)).add(look.scale(0.3)).subtract(at);
                float lift = 1 - (1 - Mth.clamp(age / 0.3f, 0, 1)) * (1 - Mth.clamp(age / 0.3f, 0, 1));
                float alpha = Mth.clamp(age / 0.15f, 0, 1) * Mth.clamp((seconds - age) / 0.35f, 0, 1);
                float[][] joints = sitting(lift);
                draw(pose, buffers, camera, at, base, look, scale, joints, SITTING_BONES, color, alpha, time);
                // the howl goes up in rings
                Vec3 muzzle = base.add(look.scale(joints[0][0] * scale)).add(0, joints[0][1] * scale, 0);
                int light = mix(color, 0xFFFFFF, 0.5f);
                for (int i = 0; i < 3 && age > 0.25f; i++) {
                    float k = (age * 1.3f + i / 3f) % 1f;
                    pose.pushPose();
                    pose.translate(muzzle.x + look.x * 0.3 * k, muzzle.y + 0.15 + 2.4 * k, muzzle.z + look.z * 0.3 * k);
                    disc(pose, buffers, SkillWorldFx.RING, 0, 0.12f + 1.2f * k, 0, i == 1 ? color : light, alpha * (1 - k) * 0.9f);
                    pose.popPose();
                }
            }
        }
    }

    /** A star at every joint and a line of light along every bone. */
    private static void draw(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 base, Vec3 fwd, float scale,
                             float[][] joints, int[][] bones, int color, float alpha, float time) {
        if (alpha <= 0.01f) return;
        Vec3 side = fwd.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(fwd).normalize();
        Vec3[] at = new Vec3[joints.length];
        for (int i = 0; i < joints.length; i++) {
            float[] j = joints[i];
            at[i] = base.add(fwd.scale(j[0] * scale)).add(up.scale(j[1] * scale)).add(side.scale(-j[2] * scale));
        }
        for (int[] bone : bones) {
            streak(pose, buffers, camera, origin, at[bone[0]], at[bone[1]], 0.12f * scale, color, alpha * 0.9f);
            streak(pose, buffers, camera, origin, at[bone[0]], at[bone[1]], 0.045f * scale, 0xFFFFFF, alpha * 0.8f);
        }
        for (int i = 0; i < at.length; i++) {
            float twinkle = 0.8f + 0.3f * Mth.sin(time * 0.6f + i * 2.4f);
            dot(pose, buffers, camera, (float) at[i].x, (float) at[i].y, (float) at[i].z, 0.2f * scale * twinkle, color, alpha * 0.5f);
            dot(pose, buffers, camera, (float) at[i].x, (float) at[i].y, (float) at[i].z, 0.085f * scale * twinkle, 0xFFFFFF, alpha);
        }
    }

    static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, float t) {
        float u = 1 - t;
        return a.scale(u * u).add(b.scale(2 * u * t)).add(c.scale(t * t));
    }

    private static float up(float stride) {
        return Math.max(0, Mth.cos(stride));
    }

    /** A wolf at a gallop: front legs reach while the hind legs push, and the other way about. */
    private static float[][] wolf(float s) {
        float bob = 0.07f * Mth.sin(2 * s + 0.6f);
        float fl = Mth.sin(s), fr = Mth.sin(s - 0.5f), hl = Mth.sin(s + Mth.PI), hr = Mth.sin(s + Mth.PI - 0.5f);
        float wag = 0.08f * Mth.sin(s * 0.7f);
        return new float[][]{
                {1.04f, 0.90f + bob, 0},                                    // 0 nose
                {0.74f, 1.12f + bob, 0},                                    // 1 brow
                {0.58f, 1.38f + bob, 0.14f},                                // 2 ear
                {0.58f, 1.38f + bob, -0.14f},                               // 3 ear
                {0.78f, 0.78f + bob, 0},                                    // 4 jaw
                {0.36f, 0.98f + bob, 0},                                    // 5 withers
                {0.44f, 0.52f + bob, 0},                                    // 6 breast
                {-0.12f, 0.88f + bob + 0.05f * Mth.cos(s), 0},              // 7 back
                {-0.58f, 0.86f + bob, 0},                                   // 8 hip
                {-0.20f, 0.56f + bob, 0},                                   // 9 belly
                {-0.84f, 0.84f + bob, 0},                                   // 10 tail
                {-1.22f, 1.06f + bob + wag, 0},                             // 11 tail tip
                {0.36f, 0.74f + bob, 0.20f}, {0.36f, 0.74f + bob, -0.20f},  // 12, 13 shoulders
                {-0.50f, 0.68f + bob, 0.22f}, {-0.50f, 0.68f + bob, -0.22f}, // 14, 15 haunches
                {0.40f + 0.22f * fl, 0.34f, 0.19f}, {0.42f + 0.55f * fl, 0.03f + 0.22f * up(s), 0.18f},
                {0.40f + 0.22f * fr, 0.34f, -0.19f}, {0.42f + 0.55f * fr, 0.03f + 0.22f * up(s - 0.5f), -0.18f},
                {-0.50f + 0.20f * hl, 0.36f, 0.21f}, {-0.62f + 0.50f * hl, 0.03f + 0.22f * up(s + Mth.PI), 0.20f},
                {-0.50f + 0.20f * hr, 0.36f, -0.21f}, {-0.62f + 0.50f * hr, 0.03f + 0.22f * up(s + Mth.PI - 0.5f), -0.20f}};
    }

    /** A boar: a hump, a low head, tusks, and short legs that drum. */
    private static float[][] boar(float s) {
        float bob = 0.05f * Mth.sin(2 * s);
        float fl = Mth.sin(s), fr = Mth.sin(s - 0.5f), hl = Mth.sin(s + Mth.PI), hr = Mth.sin(s + Mth.PI - 0.5f);
        return new float[][]{
                {1.00f, 0.48f + bob, 0},                                    // 0 snout
                {0.70f, 0.92f + bob, 0},                                    // 1 forehead
                {0.55f, 1.10f + bob, 0.19f}, {0.55f, 1.10f + bob, -0.19f},  // 2, 3 ears
                {0.80f, 0.36f + bob, 0},                                    // 4 jaw
                {0.84f, 0.42f + bob, 0.13f}, {1.02f, 0.76f + bob, 0.22f},   // 5, 6 tusk
                {0.84f, 0.42f + bob, -0.13f}, {1.02f, 0.76f + bob, -0.22f}, // 7, 8 tusk
                {0.28f, 1.24f + bob, 0},                                    // 9 hump
                {-0.25f, 1.06f + bob, 0},                                   // 10 back
                {-0.72f, 0.84f + bob, 0},                                   // 11 rump
                {-0.92f, 1.02f + bob, 0},                                   // 12 tail
                {0.48f, 0.40f + bob, 0},                                    // 13 breast
                {-0.12f, 0.34f + bob, 0},                                   // 14 belly
                {0.40f, 0.80f + bob, 0.27f}, {0.40f, 0.80f + bob, -0.27f},  // 15, 16 shoulders
                {-0.56f, 0.68f + bob, 0.27f}, {-0.56f, 0.68f + bob, -0.27f}, // 17, 18 haunches
                {0.46f + 0.16f * fl, 0.30f, 0.25f}, {0.48f + 0.38f * fl, 0.02f + 0.14f * up(s), 0.24f},
                {0.46f + 0.16f * fr, 0.30f, -0.25f}, {0.48f + 0.38f * fr, 0.02f + 0.14f * up(s - 0.5f), -0.24f},
                {-0.58f + 0.14f * hl, 0.30f, 0.25f}, {-0.62f + 0.34f * hl, 0.02f + 0.14f * up(s + Mth.PI), 0.24f},
                {-0.58f + 0.14f * hr, 0.30f, -0.25f}, {-0.62f + 0.34f * hr, 0.02f + 0.14f * up(s + Mth.PI - 0.5f), -0.24f}};
    }

    /** A hawk: wings beating ({@code flap} -1..1) or folded for the stoop ({@code tuck} 1), talons out to {@code strike}. */
    private static float[][] hawk(float flap, float tuck, float strike) {
        float[][] j = new float[15][];
        j[0] = new float[]{0.55f, 0, 0};                                    // beak
        j[1] = new float[]{0.36f, 0.07f, 0};                                // head
        j[2] = new float[]{0.05f, -0.05f, 0};                               // breast
        j[3] = new float[]{-0.10f, 0.06f, 0};                               // back
        j[4] = new float[]{-0.45f, 0, 0};                                   // tail root
        j[5] = new float[]{-0.82f, 0, 0.2f};
        j[6] = new float[]{-0.82f, 0, -0.2f};
        for (int w = 0; w < 2; w++) {
            float z = w == 0 ? 1 : -1;
            // wrist, tip, and the middle of the trailing edge: spread, and folded
            j[7 + w * 3] = new float[]{Mth.lerp(tuck, 0.22f, 0.05f), Mth.lerp(tuck, 0.10f + 0.25f * flap, 0.14f), Mth.lerp(tuck, 0.62f, 0.26f) * z};
            j[8 + w * 3] = new float[]{Mth.lerp(tuck, -0.05f, -0.65f), Mth.lerp(tuck, 0.05f + 0.6f * flap, 0.10f), Mth.lerp(tuck, 1.35f, 0.36f) * z};
            j[9 + w * 3] = new float[]{Mth.lerp(tuck, -0.28f, -0.35f), Mth.lerp(tuck, 0.02f + 0.2f * flap, 0.05f), Mth.lerp(tuck, 0.62f, 0.22f) * z};
            j[13 + w] = new float[]{0.15f + 0.1f * strike, -0.1f - 0.25f * strike, 0.1f * z};
        }
        return j;
    }

    /** A bear: on all fours at {@code rear} 0, upright with its paws over its head at 1. */
    private static float[][] bear(float rear) {
        float turn = rear * 68 * Mth.DEG_TO_RAD;
        float[][] j = new float[21][];
        float[][] upper = {{1.02f, 0.78f, 0}, {0.78f, 1.08f, 0}, {0.70f, 1.22f, 0.17f}, {0.70f, 1.22f, -0.17f}, {0.82f, 0.62f, 0},
                {0.38f, 1.22f, 0}, {0.50f, 0.52f, 0}, {-0.15f, 1.10f, 0}, {-0.10f, 0.45f, 0}};
        for (int i = 0; i < upper.length; i++) j[i] = aboutHip(upper[i], turn);
        float[][] down = {{0.42f, 0.85f, 0.28f}, {0.48f, 0.42f, 0.30f}, {0.56f, 0.02f, 0.30f}};
        float[][] raised = {{0.42f, 0.95f, 0.30f}, {0.80f, 0.80f, 0.44f}, {1.10f, 1.12f, 0.52f}};
        for (int w = 0; w < 2; w++) {
            float z = w == 0 ? 1 : -1;
            for (int i = 0; i < 3; i++) {
                j[9 + w * 3 + i] = aboutHip(new float[]{Mth.lerp(rear, down[i][0], raised[i][0]), Mth.lerp(rear, down[i][1], raised[i][1]),
                        Mth.lerp(rear, down[i][2], raised[i][2]) * z}, turn);
            }
        }
        j[15] = new float[]{-0.62f, 0.86f, 0};                              // hip
        j[16] = new float[]{-0.85f, 0.62f, 0};                              // rump
        j[17] = new float[]{-0.50f, 0.40f, 0.28f};
        j[18] = new float[]{-0.62f, 0.02f, 0.30f};
        j[19] = new float[]{-0.50f, 0.40f, -0.28f};
        j[20] = new float[]{-0.62f, 0.02f, -0.30f};
        return j;
    }

    /** Everything above a bear's hips turns about them when it rears. */
    private static float[] aboutHip(float[] p, float turn) {
        float px = -0.62f, py = 0.86f;
        float x = p[0] - px, y = p[1] - py;
        return new float[]{px + x * Mth.cos(turn) - y * Mth.sin(turn), py + x * Mth.sin(turn) + y * Mth.cos(turn), p[2]};
    }

    /** A wolf sitting, its head level at {@code lift} 0 and thrown back to howl at 1. */
    private static float[][] sitting(float lift) {
        return new float[][]{
                {Mth.lerp(lift, 0.62f, 0.42f), Mth.lerp(lift, 1.25f, 1.74f), 0},        // 0 nose
                {Mth.lerp(lift, 0.36f, 0.22f), Mth.lerp(lift, 1.42f, 1.50f), 0},        // 1 brow
                {Mth.lerp(lift, 0.22f, 0.08f), Mth.lerp(lift, 1.56f, 1.52f), 0.1f},     // 2 ear
                {Mth.lerp(lift, 0.22f, 0.08f), Mth.lerp(lift, 1.56f, 1.52f), -0.1f},    // 3 ear
                {Mth.lerp(lift, 0.48f, 0.30f), Mth.lerp(lift, 1.16f, 1.42f), 0},        // 4 jaw
                {0.12f, 1.02f, 0},                                                      // 5 neck
                {0.30f, 0.72f, 0},                                                      // 6 chest
                {-0.15f, 0.72f, 0},                                                     // 7 back
                {-0.38f, 0.32f, 0},                                                     // 8 hip
                {0.36f, 0.02f, 0.13f}, {0.36f, 0.02f, -0.13f},                          // 9, 10 front paws
                {-0.10f, 0.34f, 0.2f}, {0.0f, 0.02f, 0.22f},                            // 11, 12 hind leg
                {-0.10f, 0.34f, -0.2f}, {0.0f, 0.02f, -0.22f},                          // 13, 14 hind leg
                {-0.70f, 0.12f, 0.05f}, {-0.95f, 0.20f, 0.1f}};                         // 15, 16 tail
    }

    private StarBeasts() {}
}
