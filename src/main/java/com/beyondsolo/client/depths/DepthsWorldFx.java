package com.beyondsolo.client.depths;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.combat.CombatWorldFx;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.depths.GateWeather;
import com.beyondsolo.depths.HiddenSealBlock;
import com.beyondsolo.depths.TrapCoreBlock;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.DepthsFxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The depths' moments in the world, drawn as light (no activation circles; only what the moment needs):
 * <ul>
 *   <li>a knock on a hidden wall: violet cracks spread on the stone where it was struck, with a ring of the hollow
 *       echo;</li>
 *   <li>Pulse Scan: a wave runs out over the floor, then hidden walls glow through the rock as violet frames on their
 *       outer faces, and trap cores as red stars, for as long as the scan lasts;</li>
 *   <li>a hidden wall coming down: light pours out of the opening into the cavern in long rays;</li>
 *   <li>a storm bolt: a jagged bolt from the roof, white-hot at its core, flickering, and a flash in the fog;</li>
 *   <li>a trap core struck (a ring and a spark), and shattered (a burst of red shards);</li>
 *   <li>the shrine's blessing: a column of pale light comes down on the one it blesses, and motes rise around them.</li>
 * </ul>
 * Vanilla particles stay out of the first-person camera's way (nothing starts or flies within two blocks of it).
 */
public final class DepthsWorldFx {
    /** How near (blocks, from a first-person camera) a vanilla particle may come, at the nearest it can get. */
    private static final double CLOSE = 2.0;
    /** How far block bits get from where they start: the particle throws them about itself, whatever speed they are given. */
    private static final double BLOCK_BITS = 0.8;
    static final ResourceLocation GLOW = CombatWorldFx.GLOW;
    static final ResourceLocation RING = CombatWorldFx.RING;
    static final ResourceLocation BURST = CombatWorldFx.BURST;
    static final ResourceLocation STREAK = BeyondSolo.id("textures/effect/streak.png");
    static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/beam.png");
    static final ResourceLocation FRAME = BeyondSolo.id("textures/effect/depths_frame.png");
    static final ResourceLocation[] CRACKS = {
            BeyondSolo.id("textures/effect/key_crack_1.png"),
            BeyondSolo.id("textures/effect/key_crack_2.png"),
            BeyondSolo.id("textures/effect/key_crack_3.png")};
    private static final List<ResourceLocation> ALL = List.of(GLOW, RING, BURST, STREAK, BEAM, FRAME, CRACKS[0], CRACKS[1], CRACKS[2]);
    private static final int MAX_REVEALED = 900;
    private static final RandomSource RANDOM = RandomSource.create();

    /** One moment: what it is, where, its numbers, its colour, when it began (game time) and how long it lasts. */
    private static final class Moment {
        final int kind;
        final Vec3 at;
        final float a, b;
        final int color;
        final float start;
        final float life;
        /** A bolt's path, from the roof down. */
        Vec3[] path;
        /** A reveal's lit faces: the block, and which face. */
        List<long[]> faces;
        List<BlockPos> cores;

        Moment(int kind, Vec3 at, float a, float b, int color, float start, float life) {
            this.kind = kind;
            this.at = at;
            this.a = a;
            this.b = b;
            this.color = color;
            this.start = start;
            this.life = life;
        }
    }

    private static final List<Moment> MOMENTS = new ArrayList<>();

    private static float now(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    public static void play(DepthsFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        int color = p.color() & 0xFFFFFF;
        float t = now(0);
        switch (p.kind()) {
            case DepthsFxPayload.KNOCK -> {
                MOMENTS.add(new Moment(p.kind(), at, p.a(), p.b(), color, t, 34));
                for (int i = 0; i < 5 + 3 * (int) p.a(); i++) {
                    Vec3 o = at.add(rnd(0.55), rnd(0.55), rnd(0.55));
                    particle(mc.level, dust(color, 0.4f, 0.7f), o, rnd(0.03), 0.01, rnd(0.03));
                }
                // chips of the rock it passes for
                BlockState wall = mc.level.getBlockState(BlockPos.containing(at));
                if (wall.getBlock() instanceof HiddenSealBlock) {
                    BlockParticleOption chips = new BlockParticleOption(ParticleTypes.BLOCK, wall.getValue(HiddenSealBlock.LOOK).mimic.defaultBlockState());
                    for (int i = 0; i < 8; i++) particle(mc.level, chips, at.add(rnd(0.6), rnd(0.6), rnd(0.6)), rnd(0.05), 0.05, rnd(0.05));
                }
            }
            case DepthsFxPayload.REVEAL -> {
                Moment m = new Moment(p.kind(), at, p.a(), p.b(), color, t, Math.max(20, p.b() * 20));
                scan(mc.level, m);
                MOMENTS.add(m);
            }
            case DepthsFxPayload.OPEN -> {
                MOMENTS.add(new Moment(p.kind(), at, p.a(), p.b(), color, t, 80));
                GateOverlay.addTrauma(0.18f);
                Vec3 out = new Vec3(-Math.cos(p.a()), 0, -Math.sin(p.a()));
                for (int i = 0; i < 40; i++) {
                    Vec3 o = at.add(rnd(p.b()), rnd(p.b()), rnd(p.b()));
                    Vec3 v = out.scale(0.05 + 0.1 * RANDOM.nextDouble()).add(rnd(0.02), 0.01 + rnd(0.02), rnd(0.02));
                    particle(mc.level, dust(color, 0.3f + 0.4f * RANDOM.nextFloat(), 0.8f + RANDOM.nextFloat()), o, v.x, v.y, v.z);
                }
                for (int i = 0; i < 14; i++) {
                    Vec3 o = at.add(rnd(p.b() * 0.8), rnd(p.b() * 0.8), rnd(p.b() * 0.8));
                    Vec3 v = out.scale(0.08 + 0.08 * RANDOM.nextDouble());
                    particle(mc.level, ParticleTypes.END_ROD, o, v.x, v.y + 0.02, v.z);
                }
            }
            case DepthsFxPayload.BOLT -> {
                Moment m = new Moment(p.kind(), at, p.a(), p.b(), color, t, 16);
                m.path = boltPath(mc.level, at);
                MOMENTS.add(m);
                WeatherFx.flash(at, 1f);
                for (int i = 0; i < 18; i++) {
                    double ang = RANDOM.nextDouble() * Math.PI * 2;
                    Vec3 v = new Vec3(Math.cos(ang), 0.6 + RANDOM.nextDouble(), Math.sin(ang)).scale(0.15 + 0.15 * RANDOM.nextDouble());
                    particle(mc.level, ParticleTypes.ELECTRIC_SPARK, at.add(0, 0.2, 0), v.x, v.y, v.z);
                }
                if (mc.player != null && mc.player.position().distanceTo(at) < 7) GateOverlay.addTrauma(0.15f);
            }
            case DepthsFxPayload.SHATTER -> {
                MOMENTS.add(new Moment(p.kind(), at, p.a(), p.b(), color, t, 22));
                for (int i = 0; i < 30; i++) {
                    Vec3 v = new Vec3(rnd(1), rnd(1) + 0.4, rnd(1)).normalize().scale(0.12 + 0.18 * RANDOM.nextDouble());
                    particle(mc.level, dust(color, 0.2f + 0.5f * RANDOM.nextFloat(), 0.7f + RANDOM.nextFloat() * 0.8f), at, v.x, v.y, v.z);
                }
                for (int i = 0; i < 10; i++) {
                    Vec3 v = new Vec3(rnd(1), rnd(1), rnd(1)).normalize().scale(0.18);
                    particle(mc.level, ParticleTypes.END_ROD, at, v.x, v.y, v.z);
                }
            }
            case DepthsFxPayload.CORE_HIT -> {
                MOMENTS.add(new Moment(p.kind(), at, p.a(), p.b(), color, t, 12));
                for (int i = 0; i < 6 + 2 * (int) p.a(); i++) {
                    Vec3 v = new Vec3(rnd(1), rnd(1) + 0.3, rnd(1)).normalize().scale(0.1);
                    particle(mc.level, ParticleTypes.ELECTRIC_SPARK, at, v.x, v.y, v.z);
                }
            }
            case DepthsFxPayload.BLESS -> MOMENTS.add(new Moment(p.kind(), at, p.a(), p.b(), color, t, 60));
            case DepthsFxPayload.ROCK_WARN -> {
                BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, Block.stateById(p.color()));
                for (int i = 0; i < 10; i++) particle(mc.level, dust, at.add(rnd(0.5), 0, rnd(0.5)), 0, 0, 0);
            }
            case DepthsFxPayload.ROCK -> {
                BlockState stone = Block.stateById(p.color());
                MOMENTS.add(new Moment(p.kind(), at, p.a(), p.b(), GateWeather.TREMORS.color, t, 18));
                BlockParticleOption chips = new BlockParticleOption(ParticleTypes.BLOCK, stone);
                for (int i = 0; i < 34; i++) {
                    Vec3 v = new Vec3(rnd(1), 0.5 + RANDOM.nextDouble(), rnd(1)).scale(0.12 + 0.1 * RANDOM.nextDouble());
                    particle(mc.level, chips, at.add(rnd(p.a() * 0.4), 0.3, rnd(p.a() * 0.4)), v.x, v.y, v.z);
                }
                for (int i = 0; i < 8; i++) {
                    particle(mc.level, ParticleTypes.POOF, at.add(rnd(p.a() * 0.5), 0.2, rnd(p.a() * 0.5)), rnd(0.04), 0.02, rnd(0.04));
                }
                if (mc.player != null && mc.player.position().distanceTo(at) < 5) GateOverlay.addTrauma(0.12f);
            }
            default -> {
            }
        }
        if (MOMENTS.size() > 48) MOMENTS.remove(0);
    }

    // ================================================================ particles

    private static double rnd(double s) {
        return (RANDOM.nextDouble() - 0.5) * 2 * s;
    }

    private static DustParticleOptions dust(int color, float towardsWhite, float size) {
        Vector3f c = new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f)
                .lerp(new Vector3f(1, 1, 1), towardsWhite);
        return new DustParticleOptions(c, size);
    }

    /** A vanilla particle, unless it would start or fly right in front of a first-person camera. */
    private static void particle(ClientLevel level, ParticleOptions type, Vec3 at, double vx, double vy, double vz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType().isFirstPerson()) {
            Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
            // the nearest it comes on its way (it drifts about 25 ticks' worth of its speed); block bits get room all round
            Vec3 v = new Vec3(vx, vy, vz);
            double way = v.length() * 25;
            Vec3 nearest = at;
            if (way > 1.0e-4) {
                Vec3 dir = v.normalize();
                nearest = at.add(dir.scale(Mth.clamp(cam.subtract(at).dot(dir), 0, way)));
            }
            double room = CLOSE + (type instanceof BlockParticleOption ? BLOCK_BITS : 0);
            if (nearest.distanceToSqr(cam) < room * room) return;
        }
        level.addParticle(type, at.x, at.y, at.z, vx, vy, vz);
    }

    // ================================================================ the reveal

    /** Finds the hidden walls and trap cores within the scan, once, and which faces of the walls to light. */
    private static void scan(ClientLevel level, Moment m) {
        int r = Mth.clamp(Mth.ceil(m.a), 4, 40);
        BlockPos c = BlockPos.containing(m.at);
        Set<Long> seals = new HashSet<>();
        List<BlockPos> cores = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r) continue;
                for (int y = -Math.min(r, 20); y <= Math.min(r, 20); y++) {
                    p.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                    BlockState s = level.getBlockState(p);
                    if (s.getBlock() instanceof HiddenSealBlock) {
                        if (seals.size() < MAX_REVEALED) seals.add(p.asLong());
                    } else if (s.getBlock() instanceof TrapCoreBlock) {
                        cores.add(p.immutable());
                    }
                }
            }
        }
        List<long[]> faces = new ArrayList<>();
        for (long l : seals) {
            BlockPos b = BlockPos.of(l);
            for (Direction d : Direction.values()) {
                if (!seals.contains(b.relative(d).asLong())) faces.add(new long[]{l, d.ordinal()});
            }
        }
        m.faces = faces;
        m.cores = cores;
    }

    // ================================================================ the bolt

    private static Vec3[] boltPath(ClientLevel level, Vec3 ground) {
        // up to the roof (or twelve blocks)
        double top = ground.y + 12;
        for (int i = 2; i < 16; i++) {
            if (!level.getBlockState(BlockPos.containing(ground.x, ground.y + i, ground.z)).isAir()) {
                top = ground.y + i - 0.2;
                break;
            }
        }
        int n = 9;
        Vec3[] path = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double k = i / (double) n;
            double jitter = i == 0 || i == n ? 0 : 0.55 * Math.sin(k * Math.PI);
            path[i] = new Vec3(ground.x + rnd(jitter), Mth.lerp(k, top, ground.y + 0.05), ground.z + rnd(jitter));
        }
        return path;
    }

    // ================================================================ drawing

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!mc.level.dimension().equals(GateService.DIMENSION)) {
            // every moment here happens in a cave: none of them follows the player out
            MOMENTS.clear();
            return;
        }
        float t = now(0);
        for (Moment m : MOMENTS) {
            float age = t - m.start;
            if (m.kind == DepthsFxPayload.BLESS && age < 40 && RANDOM.nextInt(2) == 0) {
                double ang = RANDOM.nextDouble() * Math.PI * 2;
                Vec3 o = m.at.add(Math.cos(ang) * 0.9, 0.1 + RANDOM.nextDouble() * 0.4, Math.sin(ang) * 0.9);
                particle(mc.level, dust(m.color, 0.5f, 0.6f), o, 0, 0.05, 0);
            }
            if (m.kind == DepthsFxPayload.OPEN && age < 50 && RANDOM.nextInt(3) == 0) {
                Vec3 out = new Vec3(-Math.cos(m.a), 0, -Math.sin(m.a));
                Vec3 o = m.at.add(rnd(m.b), rnd(m.b), rnd(m.b));
                particle(mc.level, dust(m.color, 0.6f, 0.5f), o, out.x * 0.04, 0.01, out.z * 0.04);
            }
        }
    }

    static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || MOMENTS.isEmpty()) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float t = now(partial);
        boolean through = false;
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
                case DepthsFxPayload.KNOCK -> knock(pose, buffers, camera, m, age, k);
                case DepthsFxPayload.REVEAL -> through |= reveal(pose, buffers, camera, m, age, k);
                case DepthsFxPayload.OPEN -> open(pose, buffers, camera, m, age, k);
                case DepthsFxPayload.BOLT -> bolt(pose, buffers, camera, m, age);
                case DepthsFxPayload.SHATTER -> shatter(pose, buffers, camera, m, k);
                case DepthsFxPayload.CORE_HIT -> coreHit(pose, buffers, camera, m, k);
                case DepthsFxPayload.BLESS -> bless(pose, buffers, camera, m, age, k);
                case DepthsFxPayload.ROCK -> rock(pose, buffers, camera, m, k);
                default -> {
                }
            }
            pose.popPose();
        }
        for (ResourceLocation tex : ALL) buffers.endBatch(ModRenderTypes.lightTexture(tex));
        if (through) {
            buffers.endBatch(ModRenderTypes.lightThroughWalls(FRAME));
            buffers.endBatch(ModRenderTypes.lightThroughWalls(GLOW));
            buffers.endBatch(ModRenderTypes.lightThroughWalls(RING));
        }
    }

    /** Violet cracks where the wall was struck, a hollow ring and a soft glow. */
    private static void knock(PoseStack pose, MultiBufferSource buffers, Camera camera, Moment m, float age, float k) {
        int stage = Mth.clamp((int) m.a, 1, 3);
        Vec3 toCam = camera.getPosition().subtract(m.at).normalize();
        // on the face towards the viewer, just off the stone
        Vec3 face = toCam.scale(0.53);
        float fade = 1 - k * k;
        float pop = Mth.clamp(age / 3f, 0, 1);
        dotAt(pose, buffers, camera, CRACKS[stage - 1], face, 0.55f + 0.05f * stage, mix(m.color, 0xFFFFFF, 0.3f), fade * pop, stage * 40);
        dotAt(pose, buffers, camera, GLOW, face, 0.9f, m.color, 0.45f * fade, 0);
        for (int i = 0; i < 2; i++) {
            float rk = Mth.clamp((age - i * 4) / 12f, 0, 1);
            if (rk > 0 && rk < 1) dotAt(pose, buffers, camera, RING, face, 0.3f + 1.1f * rk, mix(m.color, 0xFFFFFF, 0.2f), 0.7f * (1 - rk), 0);
        }
    }

    /** The scan wave over the floor, then the hidden things glowing through the rock. Returns true if it drew through walls. */
    private static boolean reveal(PoseStack pose, MultiBufferSource buffers, Camera camera, Moment m, float age, float k) {
        float wave = Mth.clamp(age / 24f, 0, 1);
        if (wave < 1) {
            float r = m.a * (1 - (1 - wave) * (1 - wave));
            flat(pose, buffers.getBuffer(ModRenderTypes.lightTexture(RING)), -0.95f, r, 0, m.color, 0.65f * (1 - wave));
        }
        if (m.faces == null || (m.faces.isEmpty() && m.cores.isEmpty())) return false;
        // in after the wave reaches them, out over the last two seconds
        float in = Mth.clamp((age - 8) / 10f, 0, 1);
        float out = Mth.clamp((m.life - age) / 40f, 0, 1);
        float pulse = 0.75f + 0.25f * Mth.sin(age * 0.18f);
        float alpha = in * out * pulse;
        if (alpha <= 0.01f) return false;
        if (!m.faces.isEmpty()) {
            VertexConsumer v = buffers.getBuffer(ModRenderTypes.lightThroughWalls(FRAME));
            int c = mix(m.color, 0xFFFFFF, 0.15f);
            for (long[] f : m.faces) {
                BlockPos b = BlockPos.of(f[0]);
                face(pose, v, b, Direction.values()[(int) f[1]], m.at, c, 0.55f * alpha);
            }
            // a soft glow at the middle of the wall
            Vec3 mid = Vec3.ZERO;
            for (long[] f : m.faces) mid = mid.add(Vec3.atCenterOf(BlockPos.of(f[0])));
            mid = mid.scale(1.0 / m.faces.size());
            VertexConsumer gl = buffers.getBuffer(ModRenderTypes.lightThroughWalls(GLOW));
            dotRaw(pose, gl, camera, mid.subtract(m.at), 3.2f, m.color, 0.35f * alpha);
        }
        if (!m.cores.isEmpty()) {
            VertexConsumer gl = buffers.getBuffer(ModRenderTypes.lightThroughWalls(GLOW));
            for (BlockPos b : m.cores) {
                Vec3 o = Vec3.atCenterOf(b).subtract(m.at);
                dotRaw(pose, gl, camera, o, 0.9f, 0xFF4A4A, 0.9f * alpha);
                dotRaw(pose, gl, camera, o, 0.3f, 0xFFFFFF, 0.9f * alpha);
            }
            VertexConsumer rg = buffers.getBuffer(ModRenderTypes.lightThroughWalls(RING));
            for (BlockPos b : m.cores) {
                float rk = (age % 30) / 30f;
                dotRaw(pose, rg, camera, Vec3.atCenterOf(b).subtract(m.at), 0.4f + 1.4f * rk, 0xFF6A5A, 0.8f * alpha * (1 - rk));
            }
        }
        return true;
    }

    /** Light pours out of the opening into the cavern. */
    private static void open(PoseStack pose, MultiBufferSource buffers, Camera camera, Moment m, float age, float k) {
        float flash = Mth.clamp(1 - age / 14f, 0, 1);
        float hold = Mth.clamp(age / 6f, 0, 1) * (1 - k * k);
        int pale = mix(m.color, 0xFFFFFF, 0.55f);
        dotAt(pose, buffers, camera, GLOW, Vec3.ZERO, m.b * 2.6f, 0xFFFFFF, 0.9f * flash, 0);
        dotAt(pose, buffers, camera, GLOW, Vec3.ZERO, m.b * 2.2f + 1.5f * k, m.color, 0.7f * hold, 0);
        dotAt(pose, buffers, camera, BURST, Vec3.ZERO, m.b * (1.2f + 2f * Mth.clamp(age / 10f, 0, 1)), pale, 0.8f * flash, m.start % 360);
        // rays out of the mouth, into the cavern, fanned a little and swaying
        Vec3 out = new Vec3(-Math.cos(m.a), 0, -Math.sin(m.a));
        Vec3 side = new Vec3(-out.z, 0, out.x);
        for (int i = 0; i < 7; i++) {
            float u = (i - 3) / 3f;
            float sway = 0.15f * Mth.sin(age * 0.07f + i * 1.7f);
            Vec3 start = side.scale(u * m.b * 0.6).add(0, (i % 3 - 1) * m.b * 0.35, 0);
            Vec3 dir = out.add(side.scale(u * 0.35 + sway)).add(0, -0.12 + 0.08 * (i % 2), 0).normalize();
            float len = (6 + 2.5f * ((i * 37) % 5)) * Mth.clamp(age / 10f, 0, 1);
            float w = m.b * (0.5f + 0.25f * ((i * 13) % 3));
            streak(pose, buffers, camera, m.at, start, start.add(dir.scale(len)), w, i % 2 == 0 ? pale : m.color, 0.32f * hold);
        }
    }

    /** The bolt: flickers three times, each a little dimmer, then a fading glow where it struck. */
    private static void bolt(PoseStack pose, MultiBufferSource buffers, Camera camera, Moment m, float age) {
        float on = age < 2 ? 1 : age < 4 ? 0.25f : age < 6 ? 0.9f : age < 7 ? 0.2f : Mth.clamp(1 - (age - 7) / 6f, 0, 1) * 0.7f;
        int pale = mix(m.color, 0xFFFFFF, 0.6f);
        if (m.path != null && age < 10) {
            for (int i = 0; i < m.path.length - 1; i++) {
                Vec3 a = m.path[i].subtract(m.at), b = m.path[i + 1].subtract(m.at);
                streak(pose, buffers, camera, m.at, a, b, 0.9f, m.color, 0.55f * on);
                streak(pose, buffers, camera, m.at, a, b, 0.28f, 0xFFFFFF, 0.95f * on);
            }
        }
        float ground = Mth.clamp(1 - age / 16f, 0, 1);
        dotAt(pose, buffers, camera, GLOW, new Vec3(0, 0.3, 0), m.a * 1.6f, pale, 0.8f * ground, 0);
        flat(pose, buffers.getBuffer(ModRenderTypes.lightTexture(RING)), 0.05f, m.a * (0.6f + 0.8f * (1 - ground)), 0, pale, 0.8f * ground);
        flat(pose, buffers.getBuffer(ModRenderTypes.lightTexture(BURST)), 0.06f, m.a * 1.2f, m.start % 360, m.color, 0.7f * ground);
    }

    private static void shatter(PoseStack pose, MultiBufferSource buffers, Camera camera, Moment m, float k) {
        float out = 1 - (1 - k) * (1 - k);
        int pale = mix(m.color, 0xFFFFFF, 0.5f);
        dotAt(pose, buffers, camera, GLOW, Vec3.ZERO, 0.8f + 2.2f * out, 0xFFFFFF, Mth.clamp(1 - k * 3, 0, 1), 0);
        dotAt(pose, buffers, camera, GLOW, Vec3.ZERO, 1.5f + 2f * out, m.color, 0.8f * (1 - k), 0);
        dotAt(pose, buffers, camera, BURST, Vec3.ZERO, 0.8f + 2.4f * out, pale, 0.9f * (1 - k), m.start % 360);
        dotAt(pose, buffers, camera, RING, Vec3.ZERO, 0.4f + 3f * out, pale, 0.8f * (1 - k), 0);
    }

    private static void coreHit(PoseStack pose, MultiBufferSource buffers, Camera camera, Moment m, float k) {
        float out = 1 - (1 - k) * (1 - k);
        float strength = Mth.clamp(m.a / Math.max(1, m.b), 0.25f, 1f);
        dotAt(pose, buffers, camera, RING, Vec3.ZERO, 0.3f + 1.4f * out, mix(m.color, 0xFFFFFF, 0.4f), (0.5f + 0.5f * strength) * (1 - k), 0);
        dotAt(pose, buffers, camera, GLOW, Vec3.ZERO, 0.6f + 0.6f * strength, m.color, 0.7f * (1 - k), 0);
    }

    /** A falling rock lands: a ring of dust thrown out over the marked spot. */
    private static void rock(PoseStack pose, MultiBufferSource buffers, Camera camera, Moment m, float k) {
        float out = 1 - (1 - k) * (1 - k);
        int pale = mix(m.color, 0xFFFFFF, 0.35f);
        flat(pose, buffers.getBuffer(ModRenderTypes.lightTexture(RING)), 0.05f, m.a * (0.5f + 0.9f * out), 0, pale, 0.7f * (1 - k));
        dotAt(pose, buffers, camera, GLOW, new Vec3(0, 0.3, 0), m.a * (0.7f + 0.5f * out), m.color, 0.45f * (1 - k), 0);
    }

    /** A column of light comes down on the one blessed and thins away. */
    private static void bless(PoseStack pose, MultiBufferSource buffers, Camera camera, Moment m, float age, float k) {
        float down = Mth.clamp(age / 8f, 0, 1);
        float fade = 1 - k * k;
        int pale = mix(m.color, 0xFFFFFF, 0.6f);
        VertexConsumer b = buffers.getBuffer(ModRenderTypes.lightTexture(BEAM));
        float height = 14 * down;
        pose.pushPose();
        pose.translate(0, 14 - height, 0);
        column(b, pose, camera, m.at, 1.1f * (1.2f - 0.4f * k), height, m.color, 0.55f * fade);
        column(b, pose, camera, m.at, 0.4f, height, pale, 0.8f * fade);
        pose.popPose();
        float land = Mth.clamp((age - 6) / 14f, 0, 1);
        if (land > 0 && land < 1) flat(pose, buffers.getBuffer(ModRenderTypes.lightTexture(RING)), 0.05f, 0.5f + 2.5f * land, 0, pale, 0.9f * (1 - land));
        dotAt(pose, buffers, camera, GLOW, new Vec3(0, 1, 0), 1.6f, m.color, 0.45f * fade * down, 0);
    }

    // ================================================================ drawing helpers

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    /** One face of a block, a hair outside it, relative to {@code origin} (where the pose sits). */
    private static void face(PoseStack pose, VertexConsumer out, BlockPos b, Direction d, Vec3 origin, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2) return;
        float x = (float) (b.getX() - origin.x), y = (float) (b.getY() - origin.y), z = (float) (b.getZ() - origin.z);
        float e = 0.01f;
        Matrix4f m = pose.last().pose();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, bl = rgb & 0xFF;
        float[][] q = switch (d) {
            case DOWN -> new float[][]{{0, -e, 0}, {1, -e, 0}, {1, -e, 1}, {0, -e, 1}};
            case UP -> new float[][]{{0, 1 + e, 0}, {0, 1 + e, 1}, {1, 1 + e, 1}, {1, 1 + e, 0}};
            case NORTH -> new float[][]{{0, 0, -e}, {0, 1, -e}, {1, 1, -e}, {1, 0, -e}};
            case SOUTH -> new float[][]{{0, 0, 1 + e}, {1, 0, 1 + e}, {1, 1, 1 + e}, {0, 1, 1 + e}};
            case WEST -> new float[][]{{-e, 0, 0}, {-e, 0, 1}, {-e, 1, 1}, {-e, 1, 0}};
            case EAST -> new float[][]{{1 + e, 0, 0}, {1 + e, 1, 0}, {1 + e, 1, 1}, {1 + e, 0, 1}};
        };
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int i = 0; i < 4; i++) {
            out.addVertex(m, x + q[i][0], y + q[i][1], z + q[i][2]).setUv(uv[i][0], uv[i][1]).setColor(r, g, bl, a);
        }
    }

    private static void dotAt(PoseStack pose, MultiBufferSource buffers, Camera camera, ResourceLocation tex, Vec3 at, float half, int rgb, float alpha, float roll) {
        if (alpha <= 0.01f || half <= 0.005f) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camera.rotation());
        if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose.last().pose(), half, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    private static void dotRaw(PoseStack pose, VertexConsumer out, Camera camera, Vec3 at, float half, int rgb, float alpha) {
        if (alpha <= 0.01f || half <= 0.005f) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camera.rotation());
        sprite(out, pose.last().pose(), half, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    /** A texture lying flat at height {@code y}, turned {@code degrees}. */
    private static void flat(PoseStack pose, VertexConsumer out, float y, float radius, float degrees, int rgb, float alpha) {
        if (alpha <= 0.01f || radius <= 0.01f) return;
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        sprite(out, pose.last().pose(), radius, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    /** A camera-facing column from the pose origin up {@code height}. */
    private static void column(VertexConsumer out, PoseStack pose, Camera camera, Vec3 at, float hx, float height, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 2 || height <= 0.01f) return;
        pose.pushPose();
        float yaw = (float) Math.atan2(camera.getPosition().x - at.x, camera.getPosition().z - at.z);
        pose.mulPose(Axis.YP.rotation(yaw));
        Matrix4f m = pose.last().pose();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        out.addVertex(m, -hx, 0, 0).setUv(0, 1).setColor(r, g, b, a);
        out.addVertex(m, hx, 0, 0).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, hx, height, 0).setUv(1, 0).setColor(r, g, b, a);
        out.addVertex(m, -hx, height, 0).setUv(0, 0).setColor(r, g, b, a);
        pose.popPose();
    }

    /** A streak of light from a to b (relative to the pose origin, which sits at {@code origin}), turned to the camera. */
    private static void streak(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        if (alpha <= 0.01f || width <= 0.005f) return;
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0e-4) return;
        Vec3 mid = origin.add(a.add(b).scale(0.5));
        Vec3 view = camera.getPosition().subtract(mid);
        Vec3 side = dir.cross(view);
        if (side.lengthSqr() < 1.0e-6) side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0e-6) return;
        side = side.normalize().scale(width / 2);
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(STREAK));
        Matrix4f m = pose.last().pose();
        int al = Mth.clamp(Math.round(Math.min(1f, alpha) * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, bl = rgb & 0xFF;
        out.addVertex(m, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setUv(0, 1).setColor(r, g, bl, al);
        out.addVertex(m, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).setUv(1, 1).setColor(r, g, bl, al);
        out.addVertex(m, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).setUv(1, 0).setColor(r, g, bl, al);
        out.addVertex(m, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setUv(0, 0).setColor(r, g, bl, al);
    }

    private static void sprite(VertexConsumer out, Matrix4f m, float half, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        if (a <= 1) return;
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        out.addVertex(m, -half, -half, 0).setUv(0, 1).setColor(red, green, blue, a);
        out.addVertex(m, half, -half, 0).setUv(1, 1).setColor(red, green, blue, a);
        out.addVertex(m, half, half, 0).setUv(1, 0).setColor(red, green, blue, a);
        out.addVertex(m, -half, half, 0).setUv(0, 0).setColor(red, green, blue, a);
    }

    static void clear() {
        MOMENTS.clear();
    }

    private DepthsWorldFx() {}
}
