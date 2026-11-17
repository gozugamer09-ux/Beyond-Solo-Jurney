package com.beyondsolo.client.breaks;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.combat.CombatWorldFx;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.render.CameraParticles;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.network.GateSensePayload;
import com.beyondsolo.system.PlayerRank;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Dungeon breaks in the world, drawn as light (vanilla particles stay out of a first-person camera's way):
 * <ul>
 *   <li>a gate coming apart leaks red motes out of its rim, more as it nears the break, with sparks; once broken it
 *       spills red and smoke without stopping ({@link #leak});</li>
 *   <li>the break: a white-red flash at the gate, a pillar of red light thrown up out of it, and a shock ring running
 *       out over the ground as far as its monsters may roam;</li>
 *   <li>a wave coming out: a red pulse through the gate and a smaller ring over the ground, smoke rolling out;</li>
 *   <li>a monster crumbling as the break ends: a pale column of ash light where it stood, sinking, ash falling and
 *       souls rising;</li>
 *   <li>the gate-sensing pulse: every gate it sensed stands as a pillar of its rank's light, seen through the ground
 *       and lit as the pulse reaches it (the nearest first), with a beat of light where it stands. One coming apart
 *       pulses red, a broken one burns red. Gates past the fog are drawn nearer along the same line, at the same size
 *       on screen, so the light does not fade out with the fog.</li>
 * </ul>
 */
public final class BreakWorldFx {
    static final int RED = 0xFF2A22;
    private static final int DARK_RED = 0x7A0A0A;
    private static final ResourceLocation GLOW = CombatWorldFx.GLOW;
    private static final ResourceLocation RING = CombatWorldFx.RING;
    private static final ResourceLocation BURST = CombatWorldFx.BURST;
    private static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/beam.png");
    private static final List<ResourceLocation> ALL = List.of(GLOW, RING, BURST, BEAM);
    private static final RandomSource RANDOM = RandomSource.create();

    private static final int BROKE = 0, SPILL = 1, CRUMBLE = 2;

    /** One moment: what, where, a number for it, when it began (game time) and how long it lasts (ticks). */
    private record Moment(int kind, Vec3 at, float a, int color, float start, float life) {}

    private static final List<Moment> MOMENTS = new ArrayList<>();

    private static float now(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    static Vec3 center(BlockPos gate) {
        return new Vec3(gate.getX() + 0.5, gate.getY() + GateBlockEntity.CENTER_Y, gate.getZ() + 0.5);
    }

    // ================================================================ moments

    /** The gate broke: the flash, the pillar and the shock ring, and red thrown out every way. */
    static void broke(BlockPos gate, PlayerRank rank) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 at = center(gate);
        add(new Moment(BROKE, at, rank.tier(), RED, now(0), 80));
        for (int i = 0; i < 70; i++) {
            Vec3 d = new Vec3(rnd(1), rnd(0.8), rnd(1)).normalize();
            Vec3 o = at.add(d.scale(1 + 3.5 * RANDOM.nextDouble()));
            CameraParticles.add(level, dust(RED, 0.2f * RANDOM.nextFloat(), 1.4f + RANDOM.nextFloat()), o.x, o.y, o.z, d.x, d.y, d.z);
        }
        for (int i = 0; i < 30; i++) {
            double ang = RANDOM.nextDouble() * Math.PI * 2;
            Vec3 v = new Vec3(Math.cos(ang), 0.01, Math.sin(ang)).scale(0.3 + 0.25 * RANDOM.nextDouble());
            CameraParticles.add(level, ParticleTypes.LARGE_SMOKE, at.x + v.x * 3, gate.getY() + 0.4, at.z + v.z * 3, v.x, v.y, v.z);
        }
        for (int i = 0; i < 26; i++) {
            Vec3 v = new Vec3(rnd(1), rnd(1) + 0.3, rnd(1)).normalize().scale(0.5 + 0.4 * RANDOM.nextDouble());
            CameraParticles.add(level, ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, v.x, v.y, v.z);
        }
    }

    /** A wave came out: a pulse through the gate, a ring over the ground and smoke rolling out of it. */
    static void spill(BlockPos gate, PlayerRank rank, int count) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Vec3 at = center(gate);
        add(new Moment(SPILL, at, Math.max(1, count), RED, now(0), 36));
        Direction facing = level.getBlockEntity(gate) instanceof GateBlockEntity be ? be.facing() : Direction.NORTH;
        for (int i = 0; i < 10 + 3 * count; i++) {
            Direction out = RANDOM.nextBoolean() ? facing : facing.getOpposite();
            Vec3 v = new Vec3(out.getStepX(), 0, out.getStepZ()).scale(0.12 + 0.12 * RANDOM.nextDouble()).add(rnd(0.05), 0.02, rnd(0.05));
            Vec3 o = at.add(rnd(1.2), rnd(1.6), rnd(1.2));
            CameraParticles.add(level, ParticleTypes.LARGE_SMOKE, o.x, o.y, o.z, v.x, v.y, v.z);
            if (i % 2 == 0) CameraParticles.add(level, dust(RED, 0.1f, 1.3f), o.x, o.y, o.z, v.x, v.y, v.z);
        }
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.position().distanceTo(at) < 14) GateOverlay.addTrauma(0.12f);
    }

    /** One of the break's monsters turns to ash where it stands. */
    static void crumble(int entityId, BlockPos pos, float height) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity e = level.getEntity(entityId);
        Vec3 at = e != null ? e.position() : Vec3.atBottomCenterOf(pos);
        float h = Math.max(0.6f, e != null ? e.getBbHeight() : height);
        float w = e != null ? Math.max(0.4f, e.getBbWidth()) : 0.8f;
        add(new Moment(CRUMBLE, at, h, 0xD8CFC4, now(0), 44));
        for (int i = 0; i < 14 + (int) (h * 6); i++) {
            double y = at.y + RANDOM.nextDouble() * h;
            CameraParticles.add(level, ParticleTypes.ASH, at.x + rnd(w * 0.6), y, at.z + rnd(w * 0.6), rnd(0.02), -0.02, rnd(0.02));
            if (i % 3 == 0) CameraParticles.add(level, ParticleTypes.WHITE_ASH, at.x + rnd(w * 0.5), y, at.z + rnd(w * 0.5), 0, 0, 0);
        }
        for (int i = 0; i < 3 + (int) h; i++) {
            CameraParticles.add(level, ParticleTypes.SOUL, at.x + rnd(w * 0.4), at.y + RANDOM.nextDouble() * h, at.z + rnd(w * 0.4), 0, 0.04, 0);
        }
    }

    private static void add(Moment m) {
        MOMENTS.add(m);
        if (MOMENTS.size() > 64) MOMENTS.remove(0);
    }

    /**
     * Every client tick for a gate coming apart: red motes thrown out of its rim instead of drawn in, more as it nears
     * the break, sparks now and then, and once broken a steady spill and smoke rising off it.
     */
    public static void leak(GateBlockEntity gate) {
        if (!(gate.getLevel() instanceof ClientLevel level)) return;
        RandomSource r = level.random;
        float k = gate.instability(0);
        float chance = gate.isBroken() ? 0.8f : 0.12f + 0.55f * k;
        if (r.nextFloat() > chance) return;
        Vec3 c = gate.center();
        boolean alongX = gate.facing().getAxis() == Direction.Axis.Z;
        double a = r.nextDouble() * Math.PI * 2;
        double ox = Math.cos(a) * GateBlockEntity.HALF_WIDTH * 0.95, oy = Math.sin(a) * GateBlockEntity.HALF_HEIGHT * 0.95;
        double x = c.x + (alongX ? ox : (r.nextDouble() - 0.5) * 0.4), y = c.y + oy, z = c.z + (alongX ? (r.nextDouble() - 0.5) * 0.4 : ox);
        double sp = 0.05 + 0.08 * k + (gate.isBroken() ? 0.08 : 0);
        double vx = (alongX ? Math.cos(a) : (r.nextDouble() - 0.5)) * sp, vz = (alongX ? (r.nextDouble() - 0.5) : Math.cos(a)) * sp;
        CameraParticles.add(level, dust(RED, 0.25f * (1 - k), 1.1f + 0.6f * k), x, y, z, vx, Math.sin(a) * sp, vz);
        if (r.nextFloat() < 0.25f + 0.4f * k) CameraParticles.add(level, ParticleTypes.ELECTRIC_SPARK, x, y, z, vx * 2, Math.sin(a) * sp * 2, vz * 2);
        if (gate.isBroken() && r.nextFloat() < 0.4f) {
            CameraParticles.add(level, ParticleTypes.LARGE_SMOKE, c.x + (r.nextDouble() - 0.5) * 2, c.y + GateBlockEntity.HALF_HEIGHT,
                    c.z + (r.nextDouble() - 0.5) * 2, 0, 0.06, 0);
        }
    }

    // ================================================================ every tick

    static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        float t = now(0);
        for (Moment m : MOMENTS) {
            float age = t - m.start;
            // after a break, red keeps rising out of the gate for a while
            if (m.kind == BROKE && age < 50 && RANDOM.nextInt(2) == 0) {
                Vec3 o = m.at.add(rnd(1.3), rnd(1.8), rnd(1.3));
                CameraParticles.add(level, dust(RED, 0.1f, 1.2f + RANDOM.nextFloat()), o.x, o.y, o.z, 0, 1, 0);
            }
        }
    }

    // ================================================================ drawing

    static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BreakClient.Sensed sensed = BreakClient.sensed();
        if (MOMENTS.isEmpty() && sensed == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float t = now(partial);
        Iterator<Moment> it = MOMENTS.iterator();
        while (it.hasNext()) {
            Moment m = it.next();
            float age = t - m.start;
            if (age > m.life || age < -40) {
                it.remove();
                continue;
            }
            float k = age / m.life;
            pose.pushPose();
            pose.translate(m.at.x - cam.x, m.at.y - cam.y, m.at.z - cam.z);
            switch (m.kind) {
                case BROKE -> broke(pose, buffers, camera, m, age);
                case SPILL -> spill(pose, buffers, camera, m, age, k);
                case CRUMBLE -> crumble(pose, buffers, camera, m, age, k);
                default -> {
                }
            }
            pose.popPose();
        }
        for (ResourceLocation tex : ALL) buffers.endBatch(ModRenderTypes.lightTexture(tex));
        if (sensed != null && !sensed.gates().isEmpty()) {
            pillars(pose, buffers, camera, sensed, t);
            buffers.endBatch(ModRenderTypes.lightThroughWalls(BEAM));
            buffers.endBatch(ModRenderTypes.lightThroughWalls(GLOW));
            buffers.endBatch(ModRenderTypes.lightThroughWalls(RING));
        }
    }

    /** The break: a flash, a pillar thrown up out of the gate, and the shock ring out to its monsters' leash. */
    private static void broke(PoseStack pose, MultiBufferSource buffers, Camera camera, Moment m, float age) {
        float flash = Mth.clamp(1 - age / 16f, 0, 1);
        float hold = Mth.clamp(1 - age / 80f, 0, 1);
        dot(pose, buffers, camera, GLOW, Vec3.ZERO, 3f + 6f * (1 - flash), 0xFFFFFF, 0.95f * flash, 0);
        dot(pose, buffers, camera, GLOW, Vec3.ZERO, 6f + 3f * (1 - hold), RED, 0.8f * hold * hold, 0);
        dot(pose, buffers, camera, BURST, Vec3.ZERO, 3f + 9f * Mth.clamp(age / 14f, 0, 1), GateFx.mix(RED, 0xFFFFFF, 0.4f), 0.85f * flash, m.start % 360);
        // the pillar, thrown up fast and thinning away
        float up = 1 - (1 - Mth.clamp(age / 10f, 0, 1)) * (1 - Mth.clamp(age / 10f, 0, 1));
        float height = 60 * up;
        VertexConsumer beam = buffers.getBuffer(ModRenderTypes.lightTexture(BEAM));
        pose.pushPose();
        pose.translate(0, -GateBlockEntity.CENTER_Y, 0);
        GateFx.column(beam, pose, camera, m.at.x, m.at.z, 2.6f * (1.2f - 0.6f * hold), height, RED, 0.7f * hold);
        GateFx.column(buffers.getBuffer(ModRenderTypes.lightTexture(BEAM)), pose, camera, m.at.x, m.at.z, 0.8f, height, 0xFFD8D0, 0.8f * hold * hold);
        // the shock ring over the ground, out to the leash, and a second one behind it
        for (int i = 0; i < 2; i++) {
            float rk = Mth.clamp((age - i * 6) / (34f + i * 10), 0, 1);
            if (rk <= 0 || rk >= 1) continue;
            float r = 2 + 40 * (1 - (1 - rk) * (1 - rk) * (1 - rk));
            GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.15f, r, 0, i == 0 ? GateFx.mix(RED, 0xFFFFFF, 0.35f) : RED, 0.9f * (1 - rk));
        }
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.1f, 9f, 0, RED, 0.6f * hold);
        pose.popPose();
    }

    /** A wave: a red pulse through the gate and a ring over the ground. */
    private static void spill(PoseStack pose, MultiBufferSource buffers, Camera camera, Moment m, float age, float k) {
        float out = 1 - (1 - k) * (1 - k);
        float strength = Mth.clamp(m.a / 5f, 0.5f, 1.2f);
        dot(pose, buffers, camera, GLOW, Vec3.ZERO, (3f + 3f * out) * strength, RED, 0.75f * (1 - k), 0);
        dot(pose, buffers, camera, RING, Vec3.ZERO, 1.5f + 4f * out, GateFx.mix(RED, 0xFFFFFF, 0.3f), 0.8f * (1 - k), 0);
        pose.pushPose();
        pose.translate(0, -GateBlockEntity.CENTER_Y, 0);
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.12f, 2 + 8 * out, 0, RED, 0.75f * (1 - k));
        pose.popPose();
    }

    /** A column of ash light where a monster stood, sinking as it crumbles. */
    private static void crumble(PoseStack pose, MultiBufferSource buffers, Camera camera, Moment m, float age, float k) {
        float h = m.a * (1 - k);
        float fade = 1 - k * k;
        VertexConsumer beam = buffers.getBuffer(ModRenderTypes.lightTexture(BEAM));
        GateFx.column(beam, pose, camera, m.at.x, m.at.z, 0.55f, h + 0.3f, m.color, 0.55f * fade);
        dot(pose, buffers, camera, GLOW, new Vec3(0, h * 0.5, 0), 0.8f + 0.6f * m.a * (1 - k), DARK_RED, 0.5f * fade, 0);
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose, 0.05f, 0.4f + 1.2f * k, 0, m.color, 0.6f * (1 - k));
    }

    /**
     * The gates the pulse sensed: a pillar each, in through the ground as the pulse reaches it, out over the last two
     * seconds. Past the fog a pillar is drawn nearer, on the same line and at the same size on screen.
     */
    private static void pillars(PoseStack pose, MultiBufferSource buffers, Camera camera, BreakClient.Sensed s, float t) {
        Vec3 cam = camera.getPosition();
        float age = s.age();
        float out = Mth.clamp((s.seconds() - age) / 2f, 0, 1);
        if (out <= 0.01f) return;
        double far = Math.max(48, RenderSystem.getShaderFogEnd() * 0.7);
        for (GateSensePayload.Entry e : s.gates()) {
            Vec3 base = Vec3.atBottomCenterOf(e.pos());
            Vec3 rel = base.subtract(cam);
            double d = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
            float in = Mth.clamp((age - delay(d)) / 0.35f, 0, 1);
            if (in <= 0) continue;
            float alpha = in * out;
            // past the fog: nearer along the same line, everything scaled down with it
            float scale = (float) (rel.length() > far ? far / rel.length() : 1);
            rel = rel.scale(scale);
            int color = color(e, t);
            float width = (float) Math.max(0.5, d * 0.012) * scale;
            float height = (float) Math.max(40, d * 0.35) * scale;
            float beat = 0.75f + 0.25f * Mth.sin(t * (e.state() == GateSensePayload.IMMINENT ? 0.6f : 0.2f));
            pose.pushPose();
            pose.translate(rel.x, rel.y, rel.z);
            // drawn at the real spot for the camera-facing turn, scaled to keep its size on screen
            GateFx.column(buffers.getBuffer(ModRenderTypes.lightThroughWalls(BEAM)), pose, camera, base.x, base.z, width * 2.2f, height, color, 0.32f * alpha * beat);
            GateFx.column(buffers.getBuffer(ModRenderTypes.lightThroughWalls(BEAM)), pose, camera, base.x, base.z, width * 0.7f, height * 0.85f, GateFx.mix(color, 0xFFFFFF, 0.5f), 0.55f * alpha);
            float mid = GateBlockEntity.CENTER_Y * scale;
            float glow = (float) Math.max(1.6, d * 0.03) * scale;
            dotThrough(pose, buffers, camera, GLOW, new Vec3(0, mid, 0), glow * (0.9f + 0.2f * beat), color, 0.85f * alpha);
            dotThrough(pose, buffers, camera, GLOW, new Vec3(0, mid, 0), glow * 0.35f, 0xFFFFFF, 0.8f * alpha);
            // the beat where it stands, and the ping as the pulse reaches it
            float ring = ((age - delay(d)) % 1.6f) / 1.6f;
            dotThrough(pose, buffers, camera, RING, new Vec3(0, mid, 0), glow * (0.6f + 1.4f * ring), color, 0.7f * alpha * (1 - ring));
            float ping = Mth.clamp(1 - (age - delay(d)) / 0.6f, 0, 1);
            if (ping > 0) dotThrough(pose, buffers, camera, GLOW, new Vec3(0, mid, 0), glow * (1.5f + 2f * (1 - ping)), 0xFFFFFF, 0.7f * ping);
            pose.popPose();
        }
    }

    /** Seconds after the pulse that it reaches a gate this far off. */
    static float delay(double distance) {
        return (float) Math.min(1.6, distance / 220.0);
    }

    /** A sensed gate's light: its rank's colour, red-pulsing when it is coming apart, red when broken. */
    static int color(GateSensePayload.Entry e, float ticks) {
        int rank = PlayerRank.values()[Mth.clamp(e.rank(), 0, PlayerRank.values().length - 1)].color();
        return switch (e.state()) {
            case GateSensePayload.UNSTABLE -> GateFx.mix(rank, RED, 0.35f + 0.25f * (0.5f + 0.5f * Mth.sin(ticks * 0.2f)));
            case GateSensePayload.IMMINENT -> GateFx.mix(rank, RED, 0.6f + 0.4f * (0.5f + 0.5f * Mth.sin(ticks * 0.6f)));
            case GateSensePayload.BROKEN -> RED;
            case GateSensePayload.SEALED -> 0xF2D58A;
            case GateSensePayload.CLEARED -> GateFx.mix(rank, 0x8A9AAA, 0.6f);
            default -> rank;
        };
    }

    // ================================================================ helpers

    private static double rnd(double s) {
        return (RANDOM.nextDouble() - 0.5) * 2 * s;
    }

    private static DustParticleOptions dust(int color, float towardsWhite, float size) {
        Vector3f c = new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f)
                .lerp(new Vector3f(1, 1, 1), towardsWhite);
        return new DustParticleOptions(c, size);
    }

    private static void dot(PoseStack pose, MultiBufferSource buffers, Camera camera, ResourceLocation tex, Vec3 at, float half, int rgb, float alpha, float roll) {
        if (alpha <= 0.01f || half <= 0.005f) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camera.rotation());
        if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        GateFx.quad(buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose.last().pose(), half, half, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    private static void dotThrough(PoseStack pose, MultiBufferSource buffers, Camera camera, ResourceLocation tex, Vec3 at, float half, int rgb, float alpha) {
        if (alpha <= 0.01f || half <= 0.005f) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camera.rotation());
        GateFx.quad(buffers.getBuffer(ModRenderTypes.lightThroughWalls(tex)), pose.last().pose(), half, half, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    static void clear() {
        MOMENTS.clear();
    }

    private BreakWorldFx() {}
}
