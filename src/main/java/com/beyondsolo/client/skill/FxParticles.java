package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.render.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The skill effects' own particles: smoke that billows and thins, flames that lick up and burn out, sparks that
 * streak, fall and skip off the ground, embers, motes, petals, leaves, feathers, drops, shards and shock rings that
 * run out over the ground. Vanilla's particles are small squares that read as blocks next to the light the effects
 * draw; these are smooth textures (tools/gen_skill_fx_textures.py) sized in blocks. Fire, sparks and motes are added
 * to the scene as light; smoke and dust are blended over it and darkened where the world is dark. Runs on game time
 * like the rest of the skill effects, so it pauses with the game.
 *
 * <p>Make one with {@link #add} and tune it with the chained setters, or use one of the ready-made bursts below
 * ({@link #smoke}, {@link #fire}, {@link #sparks} and the rest). Counts follow the player's particle setting.</p>
 */
public final class FxParticles {
    public enum Tex {
        SOFT("soft", 1), HOT("hot", 1), SPARK("spark", 1), EMBER("ember", 1), FLARE("flare", 1), RAYS("rays", 1),
        WISP("wisp", 1), DROP("drop", 1), SHARD("shard", 1), PETAL("petal", 1), LEAF("leaf", 1), FEATHER("feather", 1),
        FLAME("flame_sheet", 4), SMOKE("smoke_sheet", 4), SHOCK("shock", 1), RIM("rim", 1);

        final ResourceLocation id;
        /** Frames per side of a flipbook sheet (1 for a single picture). */
        final int grid;

        Tex(String name, int grid) {
            this.id = BeyondSolo.id("textures/effect/fx/" + name + ".png");
            this.grid = grid;
        }
    }

    /** One particle. Made by {@link #add}; the setters tune it before it first moves. Sizes are half widths in blocks, times in ticks. */
    public static final class P {
        final Tex tex;
        double x, y, z, ox, oy, oz, vx, vy, vz;
        float age, life = 20;
        float size0 = 0.2f, size1 = 0.2f;
        int color0 = 0xFFFFFF, color1 = -1;
        float alpha = 1, fadeIn = 0.08f, fadeOut = 0.45f;
        float rot, spin;
        float gravity, drag = 0.96f;
        float stretch, flicker, swirl;
        float bounce = -1;
        boolean blend, flat, dead;
        int frame = -1;
        float shade = 1;
        final float seed;

        P(Tex tex, double x, double y, double z, double vx, double vy, double vz) {
            this.tex = tex;
            this.x = this.ox = x;
            this.y = this.oy = y;
            this.z = this.oz = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.seed = RANDOM.nextFloat();
            this.rot = RANDOM.nextFloat() * 360;
        }

        public P life(float ticks) {
            life = Math.max(1, ticks);
            return this;
        }

        public P size(float half) {
            size0 = size1 = half;
            return this;
        }

        /** Grows (or shrinks) from {@code from} to {@code to} over its life, quickly at first. */
        public P size(float from, float to) {
            size0 = from;
            size1 = to;
            return this;
        }

        public P color(int rgb) {
            color0 = rgb;
            color1 = -1;
            return this;
        }

        /** Shifts from one colour to the other over its life (white-hot to red, say). */
        public P color(int from, int to) {
            color0 = from;
            color1 = to;
            return this;
        }

        public P alpha(float a) {
            alpha = a;
            return this;
        }

        /** Fades in over the first {@code in} of its life and out from {@code outFrom} (both 0..1). */
        public P fade(float in, float outFrom) {
            fadeIn = in;
            fadeOut = outFrom;
            return this;
        }

        /** Turns {@code degrees} a tick. */
        public P spin(float degrees) {
            spin = degrees;
            return this;
        }

        public P rot(float degrees) {
            rot = degrees;
            return this;
        }

        /** Blocks per tick per tick, pulling down (negative lifts). */
        public P gravity(float g) {
            gravity = g;
            return this;
        }

        /** What is left of its speed after a tick (1 keeps it all). */
        public P drag(float d) {
            drag = d;
            return this;
        }

        /** Drawn along the way it flies, longer the faster it goes (sparks, drops). */
        public P stretch(float k) {
            stretch = k;
            return this;
        }

        /** Its light wavers (embers). */
        public P flicker(float f) {
            flicker = f;
            return this;
        }

        /** It wanders a little as it drifts (smoke, petals). */
        public P swirl(float s) {
            swirl = s;
            return this;
        }

        /** Stops at the ground and skips off it, keeping {@code bounce} of its fall; 0 dies there. */
        public P collide(float bounce) {
            this.bounce = bounce;
            return this;
        }

        /** Blended over the scene instead of added as light: smoke, dust. */
        public P blend() {
            blend = true;
            return this;
        }

        /** Lies flat on the ground instead of facing the camera: shock rings, scorch glows. */
        public P flat() {
            flat = true;
            return this;
        }

        /** Waits {@code ticks} before it shows and starts to move. */
        public P delay(float ticks) {
            age = -ticks;
            return this;
        }

        /** Holds one frame of a sheet instead of playing it through. */
        public P frame(int f) {
            frame = f;
            return this;
        }
    }

    private static final List<P> ALIVE = new ArrayList<>();
    private static final int MAX = 2400;
    static final RandomSource RANDOM = RandomSource.create();

    /** A new particle at (x, y, z) moving by (vx, vy, vz) blocks a tick. Null when the player has turned particles off. */
    public static P add(Tex tex, double x, double y, double z, double vx, double vy, double vz) {
        P p = new P(tex, x, y, z, vx, vy, vz);
        ALIVE.add(p);
        if (ALIVE.size() > MAX) ALIVE.remove(0);
        return p;
    }

    /** How many of {@code n} to make under the player's particle setting. */
    public static int count(int n) {
        ParticleStatus s = Minecraft.getInstance().options.particles().get();
        if (s == ParticleStatus.MINIMAL) return Math.max(n > 0 ? 1 : 0, n / 4);
        if (s == ParticleStatus.DECREASED) return Math.max(n > 0 ? 1 : 0, n / 2);
        return n;
    }

    static double rnd(double s) {
        return (RANDOM.nextDouble() - 0.5) * 2 * s;
    }

    static float rf(float from, float to) {
        return from + RANDOM.nextFloat() * (to - from);
    }

    // ================================================================ ready-made bursts

    /** Billowing smoke around (x, y, z): puffs that swell, rise by {@code rise} a tick and thin away. */
    public static void smoke(double x, double y, double z, double spread, int n, int rgb, float size, float rise) {
        for (int i = 0, k = count(n); i < k; i++) {
            add(Tex.SMOKE, x + rnd(spread), y + rnd(spread * 0.4), z + rnd(spread), rnd(0.03), rise * rf(0.6f, 1.2f), rnd(0.03))
                    .frame(RANDOM.nextInt(16)).blend().life(rf(28, 52)).size(size * rf(0.6f, 0.9f), size * rf(1.6f, 2.4f))
                    .color(rgb).alpha(rf(0.35f, 0.5f)).fade(0.12f, 0.35f).drag(0.92f).swirl(0.006f).spin(rf(-0.8f, 0.8f));
        }
    }

    /** Smoke thrown out flat from (x, y, z) over the ground, as from a blow that struck it. */
    public static void smokeRing(double x, double y, double z, int n, int rgb, float size, float speed) {
        for (int i = 0, k = count(n); i < k; i++) {
            double a = i * Math.PI * 2 / k + rnd(0.2);
            float sp = speed * rf(0.6f, 1.1f);
            add(Tex.SMOKE, x + Math.cos(a) * 0.4, y + 0.15 + RANDOM.nextDouble() * 0.3, z + Math.sin(a) * 0.4, Math.cos(a) * sp, rf(0.005f, 0.03f), Math.sin(a) * sp)
                    .frame(RANDOM.nextInt(16)).blend().life(rf(26, 44)).size(size * 0.6f, size * rf(1.6f, 2.2f))
                    .color(rgb).alpha(rf(0.4f, 0.6f)).fade(0.08f, 0.3f).drag(0.86f).swirl(0.004f).spin(rf(-1, 1));
        }
    }

    /** Dust of the ground under (x, y, z) kicked out over it to about {@code radius}. */
    public static void dust(ClientLevel level, double x, double y, double z, float radius, int n, float size) {
        int rgb = groundColor(level, x, y, z);
        for (int i = 0, k = count(n); i < k; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(RANDOM.nextDouble()) * radius * 0.5;
            float sp = (float) (radius * 0.05 * rf(0.5f, 1.2f));
            add(Tex.SMOKE, x + Math.cos(a) * r, y + 0.2 + RANDOM.nextDouble() * 0.4, z + Math.sin(a) * r, Math.cos(a) * sp, rf(0.01f, 0.05f), Math.sin(a) * sp)
                    .frame(RANDOM.nextInt(16)).blend().life(rf(30, 55)).size(size * 0.7f, size * rf(1.7f, 2.5f))
                    .color(rgb).alpha(rf(0.45f, 0.7f)).fade(0.06f, 0.3f).drag(0.88f).swirl(0.004f).spin(rf(-0.8f, 0.8f));
        }
    }

    /** A fire: flames licking up from around (x, y, z), {@code rgb} at the hot heart shading to a deeper tint as they burn out. */
    public static void fire(double x, double y, double z, double spread, int n, float size, float rise, int hot, int cool) {
        for (int i = 0, k = count(n); i < k; i++) {
            add(Tex.FLAME, x + rnd(spread), y + rnd(spread * 0.3), z + rnd(spread), rnd(0.02), rise * rf(0.7f, 1.3f), rnd(0.02))
                    .life(rf(10, 18)).size(size * rf(0.8f, 1.1f), size * rf(0.9f, 1.3f)).color(hot, cool).alpha(rf(0.8f, 1f))
                    .fade(0.05f, 0.6f).drag(0.93f).swirl(0.004f).rot(0);
        }
    }

    /** Flames thrown out flat from (x, y, z) like a ring of fire running over the ground. */
    public static void fireRing(double x, double y, double z, double from, int n, float size, float speed, int hot, int cool) {
        for (int i = 0, k = count(n); i < k; i++) {
            double a = i * Math.PI * 2 / k + rnd(0.15);
            float sp = speed * rf(0.85f, 1.1f);
            add(Tex.FLAME, x + Math.cos(a) * from, y + 0.1, z + Math.sin(a) * from, Math.cos(a) * sp, rf(0.02f, 0.05f), Math.sin(a) * sp)
                    .life(rf(11, 17)).size(size * rf(0.7f, 1f), size * rf(1.1f, 1.5f)).color(hot, cool).alpha(rf(0.8f, 1f))
                    .fade(0.05f, 0.55f).drag(0.9f).rot(rf(-10, 10));
        }
    }

    /** Sparks flung from (x, y, z): bright streaks that arc down and skip off the ground. */
    public static void sparks(double x, double y, double z, int n, float speed, int rgb, float gravity) {
        for (int i = 0, k = count(n); i < k; i++) {
            Vec3 d = new Vec3(rnd(1), rnd(1) * 0.8 + 0.25, rnd(1)).normalize().scale(speed * rf(0.5f, 1.1f));
            add(Tex.SPARK, x, y, z, d.x, d.y, d.z).life(rf(7, 15)).size(rf(0.035f, 0.06f)).color(0xFFFFFF, rgb).alpha(1)
                    .fade(0, 0.5f).gravity(gravity).drag(0.9f).stretch(2.2f).collide(0.35f);
        }
    }

    /** Sparks flung from (x, y, z) mostly along {@code dir}, in a cone {@code spread} wide. */
    public static void sparksAlong(double x, double y, double z, Vec3 dir, double spread, int n, float speed, int rgb, float gravity) {
        Vec3 d0 = dir.normalize();
        for (int i = 0, k = count(n); i < k; i++) {
            Vec3 d = d0.add(rnd(spread), rnd(spread), rnd(spread)).normalize().scale(speed * rf(0.5f, 1.1f));
            add(Tex.SPARK, x, y, z, d.x, d.y, d.z).life(rf(6, 13)).size(rf(0.035f, 0.06f)).color(0xFFFFFF, rgb).alpha(1)
                    .fade(0, 0.5f).gravity(gravity).drag(0.88f).stretch(2.2f).collide(0.35f);
        }
    }

    /** Embers drifting up from around (x, y, z), wavering as they go. */
    public static void embers(double x, double y, double z, double spread, int n, int rgb) {
        for (int i = 0, k = count(n); i < k; i++) {
            add(Tex.EMBER, x + rnd(spread), y + rnd(spread * 0.5), z + rnd(spread), rnd(0.03), rf(0.02f, 0.07f), rnd(0.03))
                    .life(rf(26, 60)).size(rf(0.04f, 0.08f)).color(0xFFF0C0, rgb).alpha(1).fade(0.1f, 0.6f)
                    .drag(0.95f).gravity(-0.002f).swirl(0.01f).flicker(0.5f);
        }
    }

    /** Soft motes of light rising around (x, y, z) (holy light, mana). */
    public static void motes(double x, double y, double z, double spread, int n, int rgb, float rise) {
        for (int i = 0, k = count(n); i < k; i++) {
            add(Tex.HOT, x + rnd(spread), y + rnd(spread * 0.6), z + rnd(spread), rnd(0.01), rise * rf(0.6f, 1.3f), rnd(0.01))
                    .life(rf(20, 44)).size(rf(0.05f, 0.11f), 0.02f).color(0xFFFFFF, rgb).alpha(rf(0.7f, 1f)).fade(0.15f, 0.5f)
                    .drag(0.96f).swirl(0.006f).flicker(0.25f);
        }
    }

    /** Motes thrown outward from (x, y, z) in every direction. */
    public static void motesBurst(double x, double y, double z, int n, float speed, int rgb) {
        for (int i = 0, k = count(n); i < k; i++) {
            Vec3 d = new Vec3(rnd(1), rnd(1), rnd(1)).normalize().scale(speed * rf(0.4f, 1f));
            add(Tex.HOT, x, y, z, d.x, d.y, d.z).life(rf(12, 26)).size(rf(0.06f, 0.12f), 0.02f).color(0xFFFFFF, rgb)
                    .alpha(1).fade(0.05f, 0.4f).drag(0.88f).gravity(-0.003f);
        }
    }

    /** A flash at (x, y, z): a star of light that pops and is gone, and the glow behind it. */
    public static void flash(double x, double y, double z, float size, int rgb) {
        add(Tex.FLARE, x, y, z, 0, 0, 0).life(5).size(size * 0.5f, size * 1.25f).color(0xFFFFFF, rgb).alpha(1).fade(0, 0.2f).rot(rf(0, 45));
        add(Tex.HOT, x, y, z, 0, 0, 0).life(7).size(size * 0.9f, size * 1.6f).color(rgb).alpha(0.75f).fade(0, 0.15f);
    }

    /** Holy rays bursting from (x, y, z). */
    public static void rays(double x, double y, double z, float size, int rgb, float life) {
        add(Tex.RAYS, x, y, z, 0, 0, 0).life(life).size(size * 0.6f, size * 1.3f).color(0xFFFFFF, rgb).alpha(0.9f).fade(0.05f, 0.3f).spin(rf(-1.5f, 1.5f));
    }

    /** A shock ring running out over the ground from (x, y, z) to {@code radius}. */
    public static void shockwave(double x, double y, double z, float radius, float life, int rgb, float alpha) {
        add(Tex.SHOCK, x, y + 0.06, z, 0, 0, 0).flat().life(life).size(radius * 0.15f, radius).color(rgb).alpha(alpha).fade(0, 0.25f);
    }

    /** A soft glow lying on the ground at (x, y, z) that fades (a scorch, the light of a landing). */
    public static void groundGlow(double x, double y, double z, float radius, float life, int rgb, float alpha) {
        add(Tex.SOFT, x, y + 0.05, z, 0, 0, 0).flat().life(life).size(radius * 0.8f, radius).color(rgb).alpha(alpha).fade(0, 0.2f);
    }

    /** Shards (ice, crystal, bone) flung from (x, y, z), tumbling as they fall. */
    public static void shards(double x, double y, double z, int n, float speed, int rgb, float size) {
        for (int i = 0, k = count(n); i < k; i++) {
            Vec3 d = new Vec3(rnd(1), rnd(1) * 0.6 + 0.5, rnd(1)).normalize().scale(speed * rf(0.5f, 1.1f));
            add(Tex.SHARD, x + rnd(0.2), y + rnd(0.2), z + rnd(0.2), d.x, d.y, d.z).life(rf(16, 30)).size(size * rf(0.7f, 1.2f))
                    .color(0xFFFFFF, rgb).alpha(1).fade(0, 0.6f).gravity(0.04f).drag(0.94f).spin(rf(-25, 25)).collide(0.25f);
        }
    }

    /** Petals, leaves or feathers ({@code tex}) thrown from (x, y, z) that flutter down. */
    public static void flutter(Tex tex, double x, double y, double z, double spread, int n, float speed, int rgb, float size) {
        for (int i = 0, k = count(n); i < k; i++) {
            double a = RANDOM.nextDouble() * Math.PI * 2;
            float sp = speed * rf(0.3f, 1f);
            add(tex, x + rnd(spread), y + rnd(spread * 0.5), z + rnd(spread), Math.cos(a) * sp, rf(0.02f, 0.12f), Math.sin(a) * sp)
                    .life(rf(30, 60)).size(size * rf(0.7f, 1.2f)).color(rgb).alpha(1).fade(0.05f, 0.7f).gravity(0.006f)
                    .drag(0.9f).swirl(0.02f).spin(rf(-12, 12)).collide(0);
        }
    }

    /** Drops of water flung from (x, y, z) that fall and are gone where they land. */
    public static void drops(double x, double y, double z, int n, float speed, int rgb) {
        for (int i = 0, k = count(n); i < k; i++) {
            Vec3 d = new Vec3(rnd(1), rnd(1) * 0.5 + 0.7, rnd(1)).normalize().scale(speed * rf(0.4f, 1.1f));
            add(Tex.DROP, x + rnd(0.2), y, z + rnd(0.2), d.x, d.y, d.z).life(rf(14, 26)).size(rf(0.04f, 0.07f)).color(0xFFFFFF, rgb)
                    .alpha(0.95f).fade(0, 0.7f).gravity(0.05f).drag(0.97f).stretch(1.4f).collide(0);
        }
    }

    /** Curls of shadow or soul smoke rising and turning around (x, y, z). */
    public static void wisps(double x, double y, double z, double spread, int n, int rgb, float size) {
        for (int i = 0, k = count(n); i < k; i++) {
            add(Tex.WISP, x + rnd(spread), y + rnd(spread * 0.5), z + rnd(spread), rnd(0.02), rf(0.02f, 0.06f), rnd(0.02))
                    .life(rf(18, 34)).size(size * 0.6f, size * rf(1.1f, 1.5f)).color(rgb).alpha(rf(0.6f, 0.9f)).fade(0.1f, 0.4f)
                    .drag(0.94f).swirl(0.01f).spin(rf(-8, 8));
        }
    }

    /** A puff of light motes and a soft glow (a step of light, a blessing landing). */
    public static void glow(double x, double y, double z, float size, int rgb, float life, float alpha) {
        add(Tex.HOT, x, y, z, 0, 0, 0).life(life).size(size * 0.7f, size).color(rgb).alpha(alpha).fade(0.05f, 0.25f);
    }

    /** The colour of the ground under (x, y, z), for dust: its map colour, a little paled, or a grey dust over air. */
    public static int groundColor(ClientLevel level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y - 0.3, z);
        BlockState ground = level.getBlockState(pos);
        if (ground.isAir()) ground = level.getBlockState(pos.below());
        MapColor map = ground.getMapColor(level, pos);
        int c = map == null || map == MapColor.NONE ? 0x8A8278 : map.col;
        return SkillWorldFx.mix(c, 0xB8B0A4, 0.35f);
    }

    // ================================================================ tick and draw

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        int dark = level.getSkyDarken();
        Iterator<P> it = ALIVE.iterator();
        while (it.hasNext()) {
            P p = it.next();
            p.ox = p.x;
            p.oy = p.y;
            p.oz = p.z;
            p.age++;
            if (p.dead || p.age >= p.life) {
                it.remove();
                continue;
            }
            if (p.age <= 0) continue;
            p.vy -= p.gravity;
            if (p.swirl > 0) {
                p.vx += Mth.sin(p.age * 0.37f + p.seed * 40) * p.swirl;
                p.vz += Mth.cos(p.age * 0.29f + p.seed * 70) * p.swirl;
            }
            p.vx *= p.drag;
            p.vy *= p.drag;
            p.vz *= p.drag;
            double nx = p.x + p.vx, ny = p.y + p.vy, nz = p.z + p.vz;
            if (p.bounce >= 0 && p.vy < 0) {
                BlockPos at = BlockPos.containing(nx, ny, nz);
                BlockState state = level.getBlockState(at);
                if (!state.isAir()) {
                    VoxelShape shape = state.getCollisionShape(level, at);
                    if (!shape.isEmpty()) {
                        double top = at.getY() + shape.max(Direction.Axis.Y);
                        if (ny < top && p.y >= top - 0.05) {
                            if (p.bounce <= 0) {
                                it.remove();
                                continue;
                            }
                            ny = top + 0.01;
                            p.vy = -p.vy * p.bounce;
                            p.vx *= 0.6;
                            p.vz *= 0.6;
                            if (p.vy < 0.02) p.vy = 0;
                        }
                    }
                }
            }
            p.x = nx;
            p.y = ny;
            p.z = nz;
            p.rot += p.spin;
            if (p.blend && ((int) p.age & 3) == 1) {
                p.shade = 0.18f + 0.82f * level.getRawBrightness(BlockPos.containing(p.x, p.y, p.z), dark) / 15f;
            }
        }
    }

    /** Draws every particle. Called once a frame after the skill effects' light. */
    public static void render(PoseStack pose, Camera camera, float partial) {
        if (ALIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        Matrix4f m = pose.last().pose();

        // smoke and dust, back to front so the near puffs cover the far ones
        List<P> blended = new ArrayList<>();
        for (P p : ALIVE) if (p.blend) blended.add(p);
        if (!blended.isEmpty()) {
            blended.sort((a, b) -> Double.compare(dist2(b, cam, partial), dist2(a, cam, partial)));
            Tex last = null;
            VertexConsumer out = null;
            for (P p : blended) {
                if (p.tex != last) {
                    if (last != null) buffers.endBatch(ModRenderTypes.softTexture(last.id));
                    last = p.tex;
                    out = buffers.getBuffer(ModRenderTypes.softTexture(p.tex.id));
                }
                draw(out, m, p, cam, left, up, partial);
            }
            buffers.endBatch(ModRenderTypes.softTexture(last.id));
        }
        // light, a texture at a time
        for (Tex tex : Tex.values()) {
            VertexConsumer out = null;
            for (P p : ALIVE) {
                if (p.blend || p.tex != tex) continue;
                if (out == null) out = buffers.getBuffer(ModRenderTypes.lightTexture(tex.id));
                draw(out, m, p, cam, left, up, partial);
            }
            if (out != null) buffers.endBatch(ModRenderTypes.lightTexture(tex.id));
        }
    }

    private static double dist2(P p, Vec3 cam, float partial) {
        double dx = Mth.lerp(partial, p.ox, p.x) - cam.x, dy = Mth.lerp(partial, p.oy, p.y) - cam.y, dz = Mth.lerp(partial, p.oz, p.z) - cam.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void draw(VertexConsumer out, Matrix4f m, P p, Vec3 cam, Vector3f left, Vector3f up, float partial) {
        if (p.age + partial < 0) return;
        float t = Mth.clamp((p.age + partial) / p.life, 0, 1);
        float x = (float) (Mth.lerp(partial, p.ox, p.x) - cam.x);
        float y = (float) (Mth.lerp(partial, p.oy, p.y) - cam.y);
        float z = (float) (Mth.lerp(partial, p.oz, p.z) - cam.z);
        float grow = 1 - (1 - t) * (1 - t);
        float half = Mth.lerp(grow, p.size0, p.size1);
        float a = p.alpha;
        if (p.fadeIn > 0) a *= Mth.clamp(t / p.fadeIn, 0, 1);
        if (t > p.fadeOut) a *= Mth.clamp(1 - (t - p.fadeOut) / Math.max(0.001f, 1 - p.fadeOut), 0, 1);
        if (p.flicker > 0) a *= 1 - p.flicker * (0.5f + 0.5f * Mth.sin((p.age + partial) * 0.9f + p.seed * 50));
        // nothing swells over the eye: what drifts right up to the camera fades away
        float d = Mth.sqrt(x * x + y * y + z * z);
        if (!p.flat && d < 1.4f + half) a *= Mth.clamp((d - 0.3f - half * 0.5f) / 1.1f, 0, 1);
        if (a <= 0.004f || half <= 0.001f) return;
        int rgb = p.color1 < 0 ? p.color0 : SkillWorldFx.mix(p.color0, p.color1, t);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        if (p.blend) {
            r = Math.round(r * p.shade);
            g = Math.round(g * p.shade);
            b = Math.round(b * p.shade);
        }
        int al = Mth.clamp(Math.round(Math.min(1, a) * 255), 0, 255);

        // the frame of a sheet
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (p.tex.grid > 1) {
            int n = p.tex.grid * p.tex.grid;
            int f = p.frame >= 0 ? p.frame % n : Math.min(n - 1, (int) (t * n));
            float cell = 1f / p.tex.grid;
            u0 = (f % p.tex.grid) * cell;
            v0 = (f / p.tex.grid) * cell;
            u1 = u0 + cell;
            v1 = v0 + cell;
        }

        if (p.flat) {
            float c = Mth.cos(p.rot * Mth.DEG_TO_RAD) * half, s = Mth.sin(p.rot * Mth.DEG_TO_RAD) * half;
            out.addVertex(m, x - c - s, y, z - s + c).setUv(u0, v1).setColor(r, g, b, al);
            out.addVertex(m, x + c - s, y, z + s + c).setUv(u1, v1).setColor(r, g, b, al);
            out.addVertex(m, x + c + s, y, z + s - c).setUv(u1, v0).setColor(r, g, b, al);
            out.addVertex(m, x - c + s, y, z - s - c).setUv(u0, v0).setColor(r, g, b, al);
            return;
        }
        if (p.stretch > 0) {
            // along the way it moves: the spark's head (right of its picture) or the drop's round end (bottom) leads
            double vx = p.x - p.ox, vy = p.y - p.oy, vz = p.z - p.oz;
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (speed > 1.0e-3) {
                float len = half * 2 + (float) speed * p.stretch;
                float dx = (float) (vx / speed), dy = (float) (vy / speed), dz = (float) (vz / speed);
                // side = dir x view
                float sx = dy * -z - dz * -y, sy = dz * -x - dx * -z, sz = dx * -y - dy * -x;
                float sl = Mth.sqrt(sx * sx + sy * sy + sz * sz);
                if (sl > 1.0e-4f) {
                    sx = sx / sl * half;
                    sy = sy / sl * half;
                    sz = sz / sl * half;
                    float tx = x - dx * len, ty = y - dy * len, tz = z - dz * len;
                    if (p.tex == Tex.DROP) {
                        out.addVertex(m, tx - sx, ty - sy, tz - sz).setUv(0, 0).setColor(r, g, b, al);
                        out.addVertex(m, x - sx, y - sy, z - sz).setUv(0, 1).setColor(r, g, b, al);
                        out.addVertex(m, x + sx, y + sy, z + sz).setUv(1, 1).setColor(r, g, b, al);
                        out.addVertex(m, tx + sx, ty + sy, tz + sz).setUv(1, 0).setColor(r, g, b, al);
                    } else {
                        out.addVertex(m, tx - sx, ty - sy, tz - sz).setUv(0, 1).setColor(r, g, b, al);
                        out.addVertex(m, x - sx, y - sy, z - sz).setUv(1, 1).setColor(r, g, b, al);
                        out.addVertex(m, x + sx, y + sy, z + sz).setUv(1, 0).setColor(r, g, b, al);
                        out.addVertex(m, tx + sx, ty + sy, tz + sz).setUv(0, 0).setColor(r, g, b, al);
                    }
                    return;
                }
            }
        }
        // facing the camera, turned by its own roll
        float c = Mth.cos(p.rot * Mth.DEG_TO_RAD), s = Mth.sin(p.rot * Mth.DEG_TO_RAD);
        float lx = (left.x() * c + up.x() * s) * half, ly = (left.y() * c + up.y() * s) * half, lz = (left.z() * c + up.z() * s) * half;
        float ux = (up.x() * c - left.x() * s) * half, uy = (up.y() * c - left.y() * s) * half, uz = (up.z() * c - left.z() * s) * half;
        // flames stand taller than they are wide, and stay upright
        if (p.tex == Tex.FLAME) {
            lx = left.x() * half;
            ly = left.y() * half;
            lz = left.z() * half;
            ux = up.x() * half * 1.5f;
            uy = up.y() * half * 1.5f;
            uz = up.z() * half * 1.5f;
            x += ux * 0.6f;
            y += uy * 0.6f;
            z += uz * 0.6f;
        }
        out.addVertex(m, x + lx - ux, y + ly - uy, z + lz - uz).setUv(u0, v1).setColor(r, g, b, al);
        out.addVertex(m, x - lx - ux, y - ly - uy, z - lz - uz).setUv(u1, v1).setColor(r, g, b, al);
        out.addVertex(m, x - lx + ux, y - ly + uy, z - lz + uz).setUv(u1, v0).setColor(r, g, b, al);
        out.addVertex(m, x + lx + ux, y + ly + uy, z + lz + uz).setUv(u0, v0).setColor(r, g, b, al);
    }

    public static void clear() {
        ALIVE.clear();
    }

    private FxParticles() {}
}
