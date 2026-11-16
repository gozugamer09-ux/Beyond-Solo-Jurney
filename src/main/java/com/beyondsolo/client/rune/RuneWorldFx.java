package com.beyondsolo.client.rune;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.combat.CombatWorldFx;
import com.beyondsolo.client.combat.GradeVisuals;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.network.RuneFxPayload;
import com.beyondsolo.rune.Runes;
import com.beyondsolo.system.PlayerRank;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Rune effects in the world: a rune stone being absorbed in any player's hands (runic letters and light drawn into
 * the stone, the stone cracking), the shatter (shards, a flash, light pouring into the player), engravings, the glow
 * of a rune lying on the ground, and the looks of the rune arts. Light is drawn with the mod's light render type;
 * vanilla particles do sparks, dust, shards and debris. Runs on game time.
 *
 * <p>The local player's own absorb in first person is drawn on the screen instead ({@link RuneOverlay}).</p>
 */
public final class RuneWorldFx {
    static final ResourceLocation GLOW = CombatWorldFx.GLOW;
    static final ResourceLocation RING = CombatWorldFx.RING;
    static final ResourceLocation SIGIL = CombatWorldFx.SIGIL;
    static final ResourceLocation BURST = CombatWorldFx.BURST;
    static final ResourceLocation STREAK = BeyondSolo.id("textures/effect/streak.png");
    static final ResourceLocation SLASH = BeyondSolo.id("textures/effect/slash.png");
    static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    static final ResourceLocation BEAM = GradeVisuals.BEAM;
    static final ResourceLocation WEB = BeyondSolo.id("textures/effect/rune_web.png");
    static final ResourceLocation WARD = BeyondSolo.id("textures/effect/rune_ward.png");
    /** The glyph carved on each kind of stone (the ten elements, then the seal). */
    static final ResourceLocation[] GLYPHS = new ResourceLocation[Runes.GLYPHS];
    private static final List<ResourceLocation> ALL = new ArrayList<>(List.of(GLOW, RING, SIGIL, BURST, STREAK, SLASH, CIRCLE, BEAM, WEB, WARD));

    static {
        for (int i = 0; i < GLYPHS.length; i++) {
            GLYPHS[i] = BeyondSolo.id("textures/effect/rune_glyph_" + i + ".png");
            ALL.add(GLYPHS[i]);
        }
    }

    private static final class Fx {
        final RuneFxPayload p;
        final float start;
        final Vec3 origin;
        final float yaw;
        final long seed;
        /** For one-off moments inside an effect (a burst, a cut). */
        int stage;

        Fx(RuneFxPayload p, float start, Vec3 origin, float yaw, long seed) {
            this.p = p;
            this.start = start;
            this.origin = origin;
            this.yaw = yaw;
            this.seed = seed;
        }

        Vec3 point() {
            return new Vec3(p.x(), p.y(), p.z());
        }

        Vec3 dir() {
            Vec3 d = new Vec3(p.dx(), p.dy(), p.dz());
            return d.lengthSqr() < 1.0e-6 ? Vec3.directionFromRotation(0, yaw) : d.normalize();
        }

        float lasting() {
            return p.duration() / 20f;
        }

        float life() {
            return switch (p.kind()) {
                case SHATTER -> 1.6f;
                case FIZZLE -> 0.6f;
                case ENGRAVED -> 1.6f;
                case ENGRAVE_REFUSED -> 0.5f;
                case HOWL -> 1.2f;
                case SWARM -> 1.0f;
                case SHARDS -> 0.5f;
                case LANCE -> 0.9f;
                case WALL -> p.duration() == 0 ? 0.4f : lasting() + 0.25f;
                case WISP -> lasting() + 0.8f;
                case IMPACT -> 1.1f;
                case WEB -> lasting() + 0.3f;
                case DREAD -> 1.3f;
                case TERROR -> Math.max(0.5f, lasting());
                case FLURRY -> lasting() + 0.4f;
                case SIGIL -> lasting() + 0.6f;
                case BREATH -> lasting() + 0.6f;
                case DROPPED -> 1.8f;
            };
        }
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    /** Players absorbing a stone: entity id -> how many crack bursts have played. */
    private static final Map<Integer, Integer> CRACKS = new HashMap<>();

    private static float now(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    static void play(RuneFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = p.entityId() >= 0 ? mc.level.getEntity(p.entityId()) : null;
        Vec3 origin = e != null ? e.position() : new Vec3(p.x(), p.y(), p.z());
        float yaw = e != null ? e.getYRot() : 0;
        if (p.kind() == RuneFxPayload.Kind.LANCE && e != null) origin = e.getEyePosition().add(0, -0.3, 0);
        if (p.kind() == RuneFxPayload.Kind.WISP && e != null) origin = e.getEyePosition().add(e.getLookAngle().scale(0.4)).add(0, -0.25, 0);
        if (p.kind() == RuneFxPayload.Kind.WALL && p.duration() > 0) {
            // a fresh bulwark replaces the old one
            ACTIVE.removeIf(f -> f.p.kind() == RuneFxPayload.Kind.WALL && f.p.duration() > 0 && f.p.entityId() == p.entityId());
        }
        Fx fx = new Fx(p, now(0), origin, yaw, RANDOM.nextLong());
        ACTIVE.add(fx);
        if (ACTIVE.size() > 96) ACTIVE.remove(0);
        burst(mc.level, fx, e);
    }

    // ================================================================ particles

    private static Vector3f rgb(int color, float towardsWhite) {
        return new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f)
                .lerp(new Vector3f(1, 1, 1), towardsWhite);
    }

    private static double rnd(double s) {
        return (RANDOM.nextDouble() - 0.5) * 2 * s;
    }

    /** True for the local player seen from their own eyes: particles at their head would only be blots on the view. */
    private static boolean selfView(@Nullable Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return e != null && e == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    /** Where a player holds a stone they are absorbing (in the world, for others and third person). */
    static Vec3 stoneAt(Entity e, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Camera cam = mc.gameRenderer.getMainCamera();
            Vector3f look = cam.getLookVector();
            Vector3f up = cam.getUpVector();
            return cam.getPosition().add(look.x * 0.8, look.y * 0.8, look.z * 0.8).add(up.x * -0.22, up.y * -0.22, up.z * -0.22);
        }
        float bodyYaw = e instanceof Player pl ? Mth.rotLerp(partial, pl.yBodyRotO, pl.yBodyRot) : e.getYRot();
        Vec3 fwd = Vec3.directionFromRotation(0, bodyYaw);
        return e.getPosition(partial).add(0, e.getBbHeight() * 0.62, 0).add(fwd.scale(0.48));
    }

    private static ItemStack shardStack(RuneFxPayload p) {
        int grade = (p.extra() >> 4) & 0xF;
        return Runes.sealed(PlayerRank.values()[Mth.clamp(grade, 0, PlayerRank.values().length - 1)]);
    }

    /** The particles an effect starts with. */
    private static void burst(ClientLevel level, Fx fx, Entity e) {
        RuneFxPayload p = fx.p;
        int c = p.color();
        Vec3 at = fx.origin;
        switch (p.kind()) {
            case SHATTER -> {
                if (e == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson()) break; // on the screen
                Vec3 stone = e != null ? stoneAt(e, 1) : at.add(0, 1.2, 0);
                ItemParticleOption shard = new ItemParticleOption(ParticleTypes.ITEM, shardStack(p));
                for (int i = 0; i < 26; i++) {
                    level.addParticle(shard, stone.x, stone.y, stone.z, rnd(0.22), 0.05 + RANDOM.nextDouble() * 0.22, rnd(0.22));
                }
                DustParticleOptions dust = new DustParticleOptions(rgb(c, 0.35f), 1.1f);
                for (int i = 0; i < 14; i++) level.addParticle(dust, stone.x + rnd(0.3), stone.y + rnd(0.3), stone.z + rnd(0.3), 0, 0, 0);
                for (int i = 0; i < 8; i++) level.addParticle(ParticleTypes.FIREWORK, stone.x, stone.y, stone.z, rnd(0.15), rnd(0.15) + 0.04, rnd(0.15));
            }
            case FIZZLE, ENGRAVE_REFUSED -> {
                Vec3 stone = e != null ? stoneAt(e, 1) : at.add(0, 1.2, 0);
                for (int i = 0; i < 7; i++) level.addParticle(ParticleTypes.SMOKE, stone.x + rnd(0.15), stone.y + rnd(0.1), stone.z + rnd(0.15), rnd(0.01), 0.03, rnd(0.01));
                DustParticleOptions grey = new DustParticleOptions(new Vector3f(0.45f, 0.45f, 0.5f), 0.9f);
                for (int i = 0; i < 6; i++) level.addParticle(grey, stone.x + rnd(0.2), stone.y + rnd(0.2), stone.z + rnd(0.2), 0, 0, 0);
            }
            case ENGRAVED -> {
                Vec3 chest = at.add(0, 1.1, 0);
                for (int i = 0; i < 34; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2, r = 1.2 + RANDOM.nextDouble() * 1.2;
                    level.addParticle(ParticleTypes.ENCHANT, chest.x, chest.y + 1.2, chest.z, Math.cos(a) * r, rnd(0.6) - 1.0, Math.sin(a) * r);
                }
                DustParticleOptions dust = new DustParticleOptions(rgb(c, 0.2f), 0.8f);
                for (int i = 0; i < 12; i++) level.addParticle(dust, chest.x + rnd(0.5), chest.y + rnd(0.6), chest.z + rnd(0.5), 0, 0.02, 0);
            }
            case HOWL -> {
                // seen through the howler's own eyes the gust would fill the view: it starts further out
                boolean self = selfView(e);
                Vec3 mouth = at.add(0, 1.55, 0).add(fx.dir().multiply(1, 0, 1).scale(self ? 2.2 : 0.9));
                if (!self) level.addParticle(ParticleTypes.GUST, mouth.x, mouth.y, mouth.z, 0, 0, 0);
                for (int i = 0; i < 10; i++) {
                    Vec3 v = fx.dir().scale(0.35 + RANDOM.nextDouble() * 0.3);
                    level.addParticle(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, v.x + rnd(0.08), rnd(0.04), v.z + rnd(0.08));
                }
            }
            case SHARDS -> {
                Vec3 to = fx.point();
                BlockParticleOption crystal = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_CLUSTER.defaultBlockState());
                for (int i = 0; i < 10; i++) level.addParticle(crystal, to.x + rnd(0.3), to.y + rnd(0.3), to.z + rnd(0.3), 0, 0, 0);
            }
            case LANCE -> {
                Vec3 to = fx.point();
                Vec3 d = to.subtract(at);
                for (int i = 0; i < 24; i++) {
                    Vec3 q = at.add(d.scale(RANDOM.nextDouble()));
                    level.addParticle(ParticleTypes.REVERSE_PORTAL, q.x + rnd(0.2), q.y + rnd(0.2), q.z + rnd(0.2), 0, 0, 0);
                }
            }
            case WALL -> {
                Vec3 fwd = Vec3.directionFromRotation(0, e != null ? e.getYRot() : fx.yaw);
                Vec3 base = (e != null ? e.position() : at).add(fwd.scale(0.85));
                int n = p.duration() == 0 ? 6 : 16;
                for (int i = 0; i < n; i++) {
                    level.addParticle(ParticleTypes.WHITE_ASH, base.x + rnd(0.7), base.y + RANDOM.nextDouble() * 2.2, base.z + rnd(0.7), 0, 0.02, 0);
                }
                // flames this close to the bearer's own eyes would be drawn huge
                if (p.duration() == 0 && !selfView(e)) {
                    for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.SMALL_FLAME, base.x + rnd(0.5), base.y + 0.6 + RANDOM.nextDouble() * 1.2, base.z + rnd(0.5), 0, 0.02, 0);
                }
            }
            case IMPACT -> {
                Vec3 to = fx.point();
                BlockState ground = level.getBlockState(BlockPos.containing(to.x, to.y - 0.5, to.z));
                if (ground.isAir()) ground = Blocks.STONE.defaultBlockState();
                BlockParticleOption rock = new BlockParticleOption(ParticleTypes.BLOCK, ground);
                for (int i = 0; i < 44; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2, r = RANDOM.nextDouble() * p.size();
                    level.addParticle(rock, to.x + Math.cos(a) * r, to.y + 0.1, to.z + Math.sin(a) * r, Math.cos(a) * 0.2, 0.25 + RANDOM.nextDouble() * 0.35, Math.sin(a) * 0.2);
                }
                for (int i = 0; i < 14; i++) {
                    double a = i / 14.0 * Math.PI * 2;
                    level.addParticle(ParticleTypes.POOF, to.x + Math.cos(a) * 1.2, to.y + 0.2, to.z + Math.sin(a) * 1.2, Math.cos(a) * 0.18, 0.02, Math.sin(a) * 0.18);
                }
                if (!selfView(e)) level.addParticle(ParticleTypes.EXPLOSION, to.x, to.y + 0.5, to.z, 0, 0, 0);
            }
            case WEB -> {
                Vec3 to = fx.point();
                for (int i = 0; i < 12; i++) level.addParticle(ParticleTypes.WHITE_ASH, to.x + rnd(p.size()), to.y + 0.3, to.z + rnd(p.size()), 0, 0, 0);
            }
            case DREAD -> {
                DustParticleOptions blood = new DustParticleOptions(rgb(c, 0f), 1.6f);
                for (int i = 0; i < 26; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2, r = (selfView(e) ? 1.8 : 0.8) + RANDOM.nextDouble() * 1.4;
                    level.addParticle(blood, at.x + Math.cos(a) * r, at.y + 0.2 + RANDOM.nextDouble() * 1.6, at.z + Math.sin(a) * r, 0, 0, 0);
                }
                if (!selfView(e)) {
                    for (int i = 0; i < 12; i++) level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + rnd(1), at.y + 0.1, at.z + rnd(1), rnd(0.08), 0.01, rnd(0.08));
                }
            }
            case BREATH -> {
                if (selfView(e)) break; // a puff at the mouth would fill the roarer's own view
                Vec3 mouth = at.add(0, e != null ? e.getEyeHeight() : 1.6, 0);
                for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.LARGE_SMOKE, mouth.x + rnd(0.3), mouth.y + rnd(0.2), mouth.z + rnd(0.3), 0, 0.05, 0);
            }
            case DROPPED -> {
                Vec3 to = fx.point();
                for (int i = 0; i < 10; i++) level.addParticle(ParticleTypes.END_ROD, to.x + rnd(0.3), to.y + rnd(0.4), to.z + rnd(0.3), rnd(0.02), 0.04 + RANDOM.nextDouble() * 0.05, rnd(0.02));
                for (int i = 0; i < 22; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2, r = 0.8 + RANDOM.nextDouble();
                    level.addParticle(ParticleTypes.ENCHANT, to.x, to.y + 1.2, to.z, Math.cos(a) * r, rnd(0.5) - 1.0, Math.sin(a) * r);
                }
            }
            default -> {
            }
        }
    }

    /** Particles over time: the absorb of every player in view, and the moments inside effects. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        float time = now(0);

        // runes being absorbed: runic letters drawn into the stone, a crack burst at each third
        for (AbstractClientPlayer pl : level.players()) {
            boolean using = pl.isUsingItem() && Runes.isRune(pl.getUseItem());
            if (!using) {
                CRACKS.remove(pl.getId());
                continue;
            }
            if (pl.distanceToSqr(mc.player) > 64 * 64) continue;
            ItemStack stack = pl.getUseItem();
            int c = Runes.colorOf(stack);
            float progress = Mth.clamp(pl.getTicksUsingItem() / (float) Runes.USE_TICKS, 0, 1);
            // the local player's own stone in first person is on the screen (RuneOverlay): particles this close
            // to the eye would only be big blots
            if (pl == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            Vec3 stone = stoneAt(pl, 1);
            int letters = 2 + Math.round(progress * 2);
            for (int i = 0; i < letters; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2, r = 1.1 + RANDOM.nextDouble() * 0.7;
                double h = rnd(0.7);
                level.addParticle(ParticleTypes.ENCHANT, stone.x, stone.y + 1.2, stone.z, Math.cos(a) * r, h - 1.2, Math.sin(a) * r);
            }
            if (RANDOM.nextFloat() < 0.25f + 0.5f * progress) {
                DustParticleOptions dust = new DustParticleOptions(rgb(c, 0.3f), 0.5f + 0.4f * progress);
                level.addParticle(dust, stone.x + rnd(0.18), stone.y + rnd(0.18), stone.z + rnd(0.18), 0, 0.01, 0);
            }
            int cracks = progress >= 2 / 3f ? 2 : progress >= 1 / 3f ? 1 : 0;
            int done = CRACKS.getOrDefault(pl.getId(), 0);
            if (cracks > done) {
                CRACKS.put(pl.getId(), cracks);
                // chips of the stone itself
                ItemParticleOption chips = new ItemParticleOption(ParticleTypes.ITEM, Runes.sealed(Runes.gradeOf(stack)));
                for (int i = 0; i < 6; i++) level.addParticle(chips, stone.x, stone.y, stone.z, rnd(0.1), 0.06 + RANDOM.nextDouble() * 0.08, rnd(0.1));
                DustParticleOptions spark = new DustParticleOptions(rgb(c, 0.5f), 0.7f);
                for (int i = 0; i < 6; i++) level.addParticle(spark, stone.x + rnd(0.12), stone.y + rnd(0.12), stone.z + rnd(0.12), 0, 0, 0);
            }
        }

        // moments inside effects
        for (Fx fx : ACTIVE) {
            RuneFxPayload p = fx.p;
            float age = (time - fx.start) / 20f;
            Entity e = p.entityId() >= 0 ? level.getEntity(p.entityId()) : null;
            switch (p.kind()) {
                case WALL -> {
                    if (p.duration() > 0 && e != null && age < fx.lasting() && RANDOM.nextInt(3) == 0) {
                        Vec3 base = e.position().add(Vec3.directionFromRotation(0, e.getYRot()).scale(0.85));
                        level.addParticle(selfView(e) || RANDOM.nextBoolean() ? ParticleTypes.WHITE_ASH : ParticleTypes.SMALL_FLAME,
                                base.x + rnd(0.65), base.y + 0.1 + RANDOM.nextDouble() * 2.1, base.z + rnd(0.65), 0, 0.015, 0);
                    }
                }
                case WISP -> {
                    float flight = Math.max(0.05f, fx.lasting());
                    if (age < flight) {
                        Vec3 q = wispAt(fx, age / flight);
                        level.addParticle(ParticleTypes.FLAME, q.x + rnd(0.08), q.y + rnd(0.08), q.z + rnd(0.08), 0, 0.01, 0);
                        if (RANDOM.nextBoolean()) level.addParticle(ParticleTypes.SMOKE, q.x, q.y, q.z, 0, 0.02, 0);
                    } else if (fx.stage == 0) {
                        fx.stage = 1;
                        Vec3 q = fx.point();
                        for (int i = 0; i < 26; i++) {
                            double a = RANDOM.nextDouble() * Math.PI * 2, s = 0.1 + RANDOM.nextDouble() * 0.18;
                            level.addParticle(ParticleTypes.FLAME, q.x, q.y + 0.3, q.z, Math.cos(a) * s, 0.04 + RANDOM.nextDouble() * 0.15, Math.sin(a) * s);
                        }
                        for (int i = 0; i < 5; i++) level.addParticle(ParticleTypes.LAVA, q.x, q.y + 0.3, q.z, 0, 0, 0);
                        for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.LARGE_SMOKE, q.x + rnd(1), q.y + 0.4, q.z + rnd(1), 0, 0.04, 0);
                    }
                }
                case FLURRY -> {
                    int cuts = Math.max(1, p.extra());
                    while (fx.stage < cuts && age >= (2 + 2 * fx.stage) / 20f) {
                        if (e != null) {
                            level.addParticle(ParticleTypes.SWEEP_ATTACK, e.getX() + rnd(0.3), e.getY() + e.getBbHeight() * (0.3 + RANDOM.nextDouble() * 0.5), e.getZ() + rnd(0.3), 0, 0, 0);
                            // sparks stay by the creature, not in the cutter's eyes
                            level.addParticle(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), rnd(0.15), rnd(0.12), rnd(0.15));
                        }
                        fx.stage++;
                    }
                }
                case SIGIL -> {
                    if (fx.stage == 0 && age >= fx.lasting()) {
                        fx.stage = 1;
                        Vec3 q = e != null ? e.position() : fx.point();
                        for (int i = 0; i < 14; i++) level.addParticle(ParticleTypes.END_ROD, q.x + rnd(0.6), q.y + 0.1, q.z + rnd(0.6), rnd(0.03), 0.12 + RANDOM.nextDouble() * 0.12, rnd(0.03));
                    }
                }
                case BREATH -> {
                    if (age < fx.lasting() && e != null) {
                        // flame sprites close to the roarer's own eyes would be drawn huge: from there they start further out
                        boolean self = selfView(e);
                        Vec3 mouth = e.getEyePosition().add(fx.dir().scale(self ? 2.6 : 0.6));
                        Vec3 d = fx.dir();
                        for (int i = 0, n = self ? 4 : 7; i < n; i++) {
                            double s = 0.45 + RANDOM.nextDouble() * 0.45;
                            level.addParticle(RANDOM.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME, mouth.x, mouth.y, mouth.z,
                                    d.x * s + rnd(0.12), d.y * s + rnd(0.1), d.z * s + rnd(0.12));
                        }
                        if (RANDOM.nextBoolean()) {
                            Vec3 q = mouth.add(d.scale((self ? 4 : 2) + RANDOM.nextDouble() * p.size() * 0.6));
                            level.addParticle(ParticleTypes.LARGE_SMOKE, q.x + rnd(0.8), q.y + rnd(0.5), q.z + rnd(0.8), 0, 0.03, 0);
                        }
                    }
                }
                case TERROR -> {
                    if (e != null && age < fx.lasting() && RANDOM.nextInt(5) == 0) {
                        level.addParticle(ParticleTypes.SMOKE, e.getX() + rnd(0.3), e.getY() + e.getBbHeight() + 0.1, e.getZ() + rnd(0.3), 0, 0.02, 0);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static Vec3 wispAt(Fx fx, float k) {
        Vec3 from = fx.origin;
        Vec3 to = fx.point().add(0, 0.3, 0);
        double arc = 1.4 + from.distanceTo(to) * 0.12;
        return from.lerp(to, k).add(0, arc * 4 * k * (1 - k), 0);
    }

    // ================================================================ light

    static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = now(partial);
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        boolean any = false;

        // stones being absorbed (not the local player's in first person: that one is on the screen)
        for (AbstractClientPlayer pl : mc.level.players()) {
            if (!pl.isUsingItem() || !Runes.isRune(pl.getUseItem())) continue;
            if (pl == mc.player && firstPerson) continue;
            if (pl.distanceToSqr(cam) > 64 * 64) continue;
            any = true;
            channel(pose, buffers, camera, pl, partial, time);
        }

        // runes lying on the ground
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity item) || !Runes.isRune(item.getItem()) || e.distanceToSqr(cam) > 48 * 48) continue;
            any = true;
            dropped(pose, buffers, camera, item, partial, time);
        }

        Iterator<Fx> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            float age = (time - fx.start) / 20f;
            float life = fx.life();
            if (age >= life + 0.05f || age < -1) {
                it.remove();
                continue;
            }
            any = true;
            RuneFxPayload p = fx.p;
            Entity e = p.entityId() >= 0 ? mc.level.getEntity(p.entityId()) : null;
            boolean self = e == mc.player && firstPerson;
            Vec3 at = e != null ? e.getPosition(partial) : fx.origin;
            float t = Mth.clamp(age / life, 0, 1);
            float out = 1 - (1 - t) * (1 - t) * (1 - t);
            float fade = 1 - t;
            float hold = Mth.clamp(age / 0.25f, 0, 1) * Mth.clamp((life - age) / 0.4f, 0, 1);
            int c = p.color();
            int light = mix(c, 0xFFFFFF, 0.5f);

            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            switch (p.kind()) {
                case SHATTER -> {
                    if (self) break; // on the screen
                    Vec3 stone = (e != null ? stoneAt(e, partial) : fx.origin.add(0, 1.2, 0)).subtract(at);
                    float flash = Mth.clamp(1 - age / 0.2f, 0, 1);
                    dot(pose, buffers, camera, (float) stone.x, (float) stone.y, (float) stone.z, 0.5f + 1.1f * flash, 0xFFFFFF, flash);
                    dotTex(pose, buffers, camera, BURST, (float) stone.x, (float) stone.y, (float) stone.z, 0.5f + 1.3f * Mth.clamp(age / 0.35f, 0, 1),
                            c, Mth.clamp(1 - age / 0.45f, 0, 1), fx.seed % 360);
                    float g = Mth.clamp(age / 0.6f, 0, 1);
                    int glyph = Mth.clamp(p.extra() & 0xF, 0, GLYPHS.length - 1);
                    dotTex(pose, buffers, camera, GLYPHS[glyph], (float) stone.x, (float) stone.y + 0.1f, (float) stone.z, 0.3f + 0.9f * g, light, (1 - g) * 1.2f, 0);
                    // the light pours in from all around
                    pours(pose, buffers, camera, at, fx.seed, age, 0, 1.05f, c, light);
                    float rise = Mth.clamp((age - 0.15f) / 0.8f, 0, 1);
                    if (rise > 0 && rise < 1) disc(pose, buffers, RING, 0.1f + 1.9f * rise, 0.95f - 0.45f * rise, 0, light, (1 - rise) * 0.9f);
                    float glow = Mth.clamp((age - 0.6f) / 0.25f, 0, 1) * Mth.clamp((life - age) / 0.6f, 0, 1);
                    dot(pose, buffers, camera, 0, 1.05f, 0, 1.0f, c, glow * 0.55f);
                }
                case FIZZLE, ENGRAVE_REFUSED -> {
                    if (self) break;
                    Vec3 stone = (e != null ? stoneAt(e, partial) : fx.origin.add(0, 1.2, 0)).subtract(at);
                    float flick = fade * (0.5f + 0.5f * Mth.sin(age * 60));
                    dot(pose, buffers, camera, (float) stone.x, (float) stone.y, (float) stone.z, 0.35f, c, flick * 0.6f);
                }
                case ENGRAVED -> {
                    float k = Mth.clamp(age / 0.7f, 0, 1);
                    float ko = 1 - (1 - k) * (1 - k);
                    disc(pose, buffers, RING, 1.1f, 1.6f - 1.1f * ko, time * 2, c, (1 - k) * 1.1f);
                    disc(pose, buffers, RING, 0.06f, 0.5f + 1.4f * out, 0, light, fade * 0.7f);
                    if (!self) pours(pose, buffers, camera, at, fx.seed, age, 0.1f, 1.05f, c, light);
                    float pop = Mth.clamp((age - 0.65f) / 0.5f, 0, 1);
                    if (!self && pop > 0 && pop < 1) dotTex(pose, buffers, camera, BURST, 0, 1.1f, 0, 0.4f + 0.8f * pop, light, 1 - pop, fx.seed % 360);
                }
                case HOWL -> {
                    Vec3 d = fx.dir();
                    float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
                    for (int i = 0; i < 3; i++) {
                        float k = Mth.clamp((age - i * 0.13f) / 0.75f, 0, 1);
                        if (k <= 0 || k >= 1) continue;
                        float o = 1 - (1 - k) * (1 - k);
                        wave(pose, buffers, yaw, 1.45f, 0.7f + 4.6f * o, 0.45f + 2.5f * o, i == 1 ? light : c, (1 - k) * 0.95f);
                    }
                    disc(pose, buffers, RING, 0.06f, p.size() * out, 0, light, fade * 0.7f);
                    disc(pose, buffers, GLOW, 0.04f, 1.8f * (1 - t), 0, c, fade * 0.5f);
                }
                case SWARM -> swarm(pose, buffers, camera, fx, at, age, c, light);
                case SHARDS -> {
                    Vec3 from = new Vec3(0, (e != null ? e.getBbHeight() : 1.8f) * 0.55f, 0);
                    Vec3 to = fx.point().subtract(at);
                    RandomSource r = RandomSource.create(fx.seed);
                    float k = Mth.clamp(age / 0.18f, 0, 1);
                    for (int i = 0; i < 5; i++) {
                        Vec3 end = to.add((r.nextDouble() - 0.5) * 0.8, (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.8);
                        Vec3 head = from.lerp(end, k);
                        Vec3 tail = from.lerp(end, Math.max(0, k - 0.35f));
                        streak(pose, buffers, camera, at, tail, head, 0.13f, c, (1 - t) * 1.2f);
                        streak(pose, buffers, camera, at, tail, head, 0.05f, 0xFFFFFF, (1 - t));
                    }
                    if (age > 0.16f) {
                        float b = Mth.clamp((age - 0.16f) / 0.3f, 0, 1);
                        dotTex(pose, buffers, camera, BURST, (float) to.x, (float) to.y, (float) to.z, 0.3f + 0.6f * b, light, 1 - b, fx.seed % 360);
                    }
                }
                case LANCE -> {
                    Vec3 from = fx.origin.subtract(at);
                    Vec3 to = fx.point().subtract(at);
                    float grow = Mth.clamp(age / 0.1f, 0, 1);
                    Vec3 head = from.lerp(to, grow);
                    float beam = (float) Math.pow(1 - t, 1.6);
                    streak(pose, buffers, camera, at, from, head, 0.7f * beam + 0.05f, c, beam * 1.1f);
                    streak(pose, buffers, camera, at, from, head, 0.2f * beam + 0.02f, 0xFFFFFF, beam);
                    // hollow rings along the lance
                    double len = from.distanceTo(to);
                    Vec3 d = to.subtract(from).normalize();
                    for (int i = 1; i * 3 < len; i++) {
                        float k = Mth.clamp((age - i * 0.025f) / 0.45f, 0, 1);
                        if (k <= 0 || k >= 1) continue;
                        Vec3 q = from.add(d.scale(i * 3));
                        ringAlong(pose, buffers, q, d, 0.3f + 0.6f * k, c, (1 - k) * 0.8f);
                    }
                    // the hollowness flows back to the caster: a wisp for every foe the lance passed
                    int wisps = Math.max(1, p.extra()) * 3;
                    RandomSource r = RandomSource.create(fx.seed);
                    for (int i = 0; i < wisps; i++) {
                        float delay = 0.15f + r.nextFloat() * 0.25f;
                        float k = Mth.clamp((age - delay) / 0.5f, 0, 1);
                        if (k <= 0 || k >= 1) continue;
                        Vec3 start = from.lerp(to, 0.4 + r.nextDouble() * 0.6);
                        Vec3 q = start.lerp(from.add(0, 0.2, 0), k * k).add(0, Math.sin(k * Math.PI) * 0.6, 0);
                        dot(pose, buffers, camera, (float) q.x, (float) q.y, (float) q.z, 0.14f, light, (1 - k) * 0.9f);
                    }
                    if (grow >= 1) dotTex(pose, buffers, camera, BURST, (float) to.x, (float) to.y, (float) to.z, 0.5f + 0.9f * out, c, fade, fx.seed % 360);
                }
                case WALL -> {
                    float yaw = e != null ? Mth.rotLerp(partial, e.yRotO, e.getYRot()) : fx.yaw;
                    float a = self ? 0.3f : 1f;
                    if (p.duration() == 0) {
                        float k = Mth.clamp(age / 0.4f, 0, 1);
                        ward(pose, buffers, yaw, 1.0f, light, (1 - k) * 1.1f * a);
                        waveAt(pose, buffers, yaw, 0.85f, 1.05f, 0.4f + 1.4f * k, light, (1 - k) * a);
                    } else {
                        float rise = Mth.clamp(age / 0.22f, 0, 1);
                        float shimmer = 0.6f + 0.1f * Mth.sin(time * 0.25f);
                        ward(pose, buffers, yaw, 1 - (1 - rise) * (1 - rise), c, hold * shimmer * a);
                        disc(pose, buffers, GLOW, 0.05f, 1.1f, 0, c, hold * 0.3f * a);
                    }
                }
                case WISP -> {
                    float flight = Math.max(0.05f, fx.lasting());
                    if (age < flight) {
                        float k = age / flight;
                        for (int i = 6; i >= 0; i--) {
                            float kk = k - i * 0.035f;
                            if (kk < 0) continue;
                            Vec3 q = wispAt(fx, kk).subtract(at);
                            float s = i == 0 ? 0.42f : 0.32f - i * 0.03f;
                            dot(pose, buffers, camera, (float) q.x, (float) q.y, (float) q.z, s, i == 0 ? c : mix(c, 0xFF2A00, i / 7f), 1f - i * 0.12f);
                        }
                        Vec3 q = wispAt(fx, k).subtract(at);
                        dot(pose, buffers, camera, (float) q.x, (float) q.y, (float) q.z, 0.17f + 0.03f * Mth.sin(time * 1.3f), 0xFFF2C0, 1f);
                    } else {
                        float k = Mth.clamp((age - flight) / 0.8f, 0, 1);
                        float o = 1 - (1 - k) * (1 - k) * (1 - k);
                        Vec3 q = fx.point().subtract(at);
                        pose.pushPose();
                        pose.translate(q.x, q.y, q.z);
                        dotTex(pose, buffers, camera, BURST, 0, 0.5f, 0, 0.8f + 1.6f * o, light, (1 - k) * 1.2f, fx.seed % 360);
                        dot(pose, buffers, camera, 0, 0.5f, 0, 1.6f * (1 - k * 0.5f), c, (1 - k) * 0.8f);
                        disc(pose, buffers, RING, 0.08f, p.size() * o, 0, c, (1 - k) * 1.1f);
                        disc(pose, buffers, GLOW, 0.05f, p.size() * 0.9f, 0, c, (1 - k) * 0.6f);
                        pose.popPose();
                    }
                }
                case IMPACT -> {
                    Vec3 q = fx.point().subtract(at);
                    pose.translate(q.x, q.y, q.z);
                    disc(pose, buffers, RING, 0.08f, p.size() * 1.25f * out, 0, light, fade * 1.2f);
                    float k2 = Mth.clamp((age - 0.12f) / 0.7f, 0, 1);
                    if (k2 > 0 && k2 < 1) disc(pose, buffers, RING, 0.1f, p.size() * 0.8f * (1 - (1 - k2) * (1 - k2)), 0, c, (1 - k2));
                    disc(pose, buffers, GLOW, 0.05f, p.size() * 0.75f, 0, c, fade * 0.7f);
                    dotTex(pose, buffers, camera, BURST, 0, 0.4f, 0, 1f + 2f * out, light, fade, fx.seed % 360);
                }
                case WEB -> {
                    Vec3 q = fx.point().subtract(at);
                    pose.translate(q.x, q.y, q.z);
                    float k = Mth.clamp(age / 0.3f, 0, 1);
                    float spread = 0.3f + 0.7f * outBack(k);
                    float shimmer = 0.85f + 0.15f * Mth.sin(time * 0.4f);
                    disc(pose, buffers, WEB, 0.06f, p.size() * spread, (fx.seed % 360), c, hold * shimmer);
                    disc(pose, buffers, GLOW, 0.04f, p.size() * 0.9f, 0, c, hold * 0.22f);
                }
                case DREAD -> {
                    for (int i = 0; i < 3; i++) {
                        float k = Mth.clamp((age - i * 0.09f) / 0.85f, 0, 1);
                        if (k <= 0 || k >= 1) continue;
                        float o = 1 - (1 - k) * (1 - k);
                        disc(pose, buffers, RING, 0.08f + i * 0.85f, 0.8f + p.size() * o * (1 - i * 0.12f), 0, i == 0 ? light : c, (1 - k) * 1.1f);
                    }
                    disc(pose, buffers, GLOW, 0.04f, p.size() * 0.6f * out, 0, c, fade * 0.5f);
                    if (!self) dot(pose, buffers, camera, 0, 1.1f, 0, 1.3f * (1 - t * 0.5f), c, fade * 0.6f);
                }
                case TERROR -> {
                    if (e == null) break;
                    float h = e.getBbHeight() + 0.4f;
                    float shake = 0.04f * Mth.sin(time * 2.7f);
                    dotTex(pose, buffers, camera, SIGIL, shake, h, 0, 0.28f, c, hold * 0.95f, time * 9);
                    dot(pose, buffers, camera, shake, h, 0, 0.32f, c, hold * 0.5f);
                    disc(pose, buffers, RING, e.getBbHeight() * 0.5f, Math.max(0.5f, e.getBbWidth()) * (0.9f + 0.1f * Mth.sin(time * 0.6f)), time * 4, c, hold * 0.6f);
                }
                case FLURRY -> {
                    if (e == null) break;
                    // the blink: an afterimage from where the cutter stood
                    float trail = Mth.clamp(1 - age / 0.35f, 0, 1);
                    if (trail > 0) {
                        Vec3 from = fx.point().subtract(at).add(0, 1, 0);
                        streak(pose, buffers, camera, at, from, new Vec3(0, 1, 0), 0.5f * trail, light, trail * 0.8f);
                    }
                    int cuts = Math.max(1, p.extra());
                    RandomSource r = RandomSource.create(fx.seed);
                    float size = Math.max(1.1f, e.getBbHeight() * 0.85f + e.getBbWidth() * 0.3f);
                    for (int i = 0; i < cuts; i++) {
                        float yaw = r.nextFloat() * 360, tilt = (r.nextFloat() - 0.5f) * 130;
                        float ti = (2 + 2 * i) / 20f;
                        float k = (age - ti) / 0.24f;
                        if (k < 0 || k > 1) continue;
                        boolean last = i == cuts - 1;
                        cut(pose, buffers, e.getBbHeight() * 0.55f, yaw, tilt, size * (last ? 1.4f : 1f) * (0.8f + 0.2f * k), i % 2 == 0 ? c : light, (1 - k) * 1.3f);
                        if (last) dotTex(pose, buffers, camera, BURST, 0, e.getBbHeight() * 0.55f, 0, 0.6f + 1.4f * k, light, 1 - k, fx.seed % 360);
                    }
                }
                case SIGIL -> {
                    float delay = Math.max(0.05f, fx.lasting());
                    int i = p.extra();
                    if (age < delay) {
                        float k = Mth.clamp(age / 0.25f, 0, 1);
                        float charge = age / delay;
                        float r = p.size() * (0.4f + 0.6f * outBack(k));
                        disc(pose, buffers, CIRCLE, 0.05f, r, time * 3 * (i % 2 == 0 ? 1 : -1), c, 0.5f + 0.6f * charge);
                        disc(pose, buffers, RING, 0.06f, r * (1.15f - 0.25f * charge), 0, light, 0.4f + 0.6f * charge);
                        disc(pose, buffers, GLOW, 0.04f, r * 0.8f, 0, c, 0.2f + 0.5f * charge);
                    } else {
                        float k = Mth.clamp((age - delay) / 0.6f, 0, 1);
                        float o = 1 - (1 - k) * (1 - k) * (1 - k);
                        column(pose, buffers, camera, 0.35f + 0.25f * (1 - k), 3.2f, light, (1 - k) * 1.1f);
                        dotTex(pose, buffers, camera, BURST, 0, 0.6f, 0, 0.8f + 1.8f * o, light, (1 - k) * 1.2f, fx.seed % 360);
                        disc(pose, buffers, RING, 0.08f, p.size() * 1.4f * o, 0, c, (1 - k) * 1.1f);
                        disc(pose, buffers, CIRCLE, 0.05f, p.size() * (1 + 0.4f * o), time * 3, c, (1 - k) * 0.8f);
                    }
                }
                case BREATH -> breath(pose, buffers, camera, fx, e, at, partial, age, c);
                case DROPPED -> {
                    Vec3 q = fx.point().subtract(at);
                    pose.translate(q.x, q.y, q.z);
                    float k = Mth.clamp((age - 0.3f) / 1.5f, 0, 1);
                    float o = Mth.clamp(age / 0.15f, 0, 1);
                    column(pose, buffers, camera, 0.3f, 3.6f, c, o * (1 - k) * 0.9f);
                    column(pose, buffers, camera, 0.1f, 3.6f, 0xFFFFFF, o * (1 - k) * 0.7f);
                    int glyph = Mth.clamp(p.extra() & 0xF, 0, GLYPHS.length - 1);
                    float rise = Mth.clamp(age / 1.2f, 0, 1);
                    dotTex(pose, buffers, camera, GLYPHS[glyph], 0, 0.2f + 0.8f * rise, 0, 0.35f + 0.25f * rise, light, (1 - rise) * 1.2f, 0);
                    disc(pose, buffers, RING, -p.size() * 0.45f + 0.05f, 0.4f + 1.6f * out, 0, c, fade);
                    float flash = Mth.clamp(1 - age / 0.3f, 0, 1);
                    dotTex(pose, buffers, camera, BURST, 0, 0, 0, 0.5f + 0.8f * (1 - flash), light, flash, fx.seed % 360);
                }
            }
            pose.popPose();
        }
        if (any) for (ResourceLocation tex : ALL) buffers.endBatch(ModRenderTypes.lightTexture(tex));
    }

    /** A stone in a player's hands, drinking in light (third person and other players). */
    private static void channel(PoseStack pose, MultiBufferSource buffers, Camera camera, AbstractClientPlayer pl, float partial, float time) {
        ItemStack stack = pl.getUseItem();
        int c = Runes.colorOf(stack);
        int light = mix(c, 0xFFFFFF, 0.55f);
        float progress = Mth.clamp((pl.getTicksUsingItem() + partial) / Runes.USE_TICKS, 0, 1);
        Vec3 cam = camera.getPosition();
        Vec3 stone = stoneAt(pl, partial);
        pose.pushPose();
        pose.translate(stone.x - cam.x, stone.y - cam.y, stone.z - cam.z);
        float flicker = 0.85f + 0.15f * Mth.sin(time * 1.7f + pl.getId());
        dot(pose, buffers, camera, 0, 0, 0, 0.32f + 0.35f * progress, c, (0.45f + 0.45f * progress) * flicker);
        dot(pose, buffers, camera, 0, 0, 0, 0.12f + 0.08f * progress, 0xFFFFFF, 0.5f + 0.5f * progress);
        // the stone's glyph rises out of it, brighter as it fills
        int glyph = Runes.glyphOf(stack);
        dotTex(pose, buffers, camera, GLYPHS[glyph], 0, 0.38f + 0.06f * Mth.sin(time * 0.2f), 0, 0.2f + 0.08f * progress, light, Mth.clamp(progress * 1.6f, 0, 1), 0);
        // a ring of light closing on the stone
        dotTex(pose, buffers, camera, RING, 0, 0, 0, 0.75f - 0.45f * progress, light, 0.25f + 0.55f * progress, time * 5);
        // motes spiralling in
        for (int i = 0; i < 10; i++) {
            float ph = (time * 0.04f + i / 10f) % 1f;
            float r = 1.15f * (1 - ph);
            float a = i * 36 + ph * 420;
            float y = (1 - ph) * 0.45f * Mth.sin(i * 1.7f);
            float x = Mth.cos(a * Mth.DEG_TO_RAD) * r, z = Mth.sin(a * Mth.DEG_TO_RAD) * r;
            dot(pose, buffers, camera, x, y, z, 0.06f + 0.03f * ph, light, ph * (0.35f + 0.65f * progress));
        }
        pose.popPose();
    }

    /** A rune lying on the ground: its glyph turning slowly beneath it, a soft halo, a column for the high grades. */
    private static void dropped(PoseStack pose, MultiBufferSource buffers, Camera camera, ItemEntity item, float partial, float time) {
        ItemStack stack = item.getItem();
        int c = Runes.colorOf(stack);
        int light = mix(c, 0xFFFFFF, 0.5f);
        Vec3 cam = camera.getPosition();
        Vec3 at = item.getPosition(partial);
        float pulse = 0.75f + 0.25f * Mth.sin(time * 0.15f + item.getId());
        float bob = Mth.sin((item.getAge() + partial) / 10f + item.bobOffs) * 0.1f + 0.1f;
        pose.pushPose();
        pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        disc(pose, buffers, GLYPHS[Runes.glyphOf(stack)], 0.03f, 0.42f, time * 0.8f, c, 0.75f * pulse);
        disc(pose, buffers, GLOW, 0.02f, 0.65f, 0, c, 0.4f * pulse);
        dot(pose, buffers, camera, 0, bob + 0.25f, 0, 0.32f, c, 0.55f * pulse);
        dot(pose, buffers, camera, 0, bob + 0.25f, 0, 0.12f, light, 0.45f * pulse);
        if (Runes.gradeOf(stack).tier() >= PlayerRank.B.tier()) column(pose, buffers, camera, 0.12f, 1.8f, c, 0.35f * pulse);
        pose.popPose();
    }

    /** Light pouring into a body's chest from all around (relative to the current pose at the body's feet). */
    private static void pours(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, long seed, float age, float delay, float chest,
                              int c, int light) {
        RandomSource r = RandomSource.create(seed);
        Vec3 heart = new Vec3(0, chest, 0);
        for (int i = 0; i < 14; i++) {
            double yaw = r.nextDouble() * Math.PI * 2, pitch = (r.nextDouble() - 0.35) * 1.4, dist = 2.0 + r.nextDouble() * 1.2;
            Vec3 from = heart.add(Math.cos(yaw) * Math.cos(pitch) * dist, Math.sin(pitch) * dist, Math.sin(yaw) * Math.cos(pitch) * dist);
            float start = delay + 0.05f + r.nextFloat() * 0.35f;
            float k = Mth.clamp((age - start) / 0.4f, 0, 1);
            if (k <= 0 || k >= 1) continue;
            float kk = k * k;
            Vec3 head = from.lerp(heart, kk);
            Vec3 tail = from.lerp(heart, Math.max(0, kk - 0.3f));
            streak(pose, buffers, camera, origin, tail, head, 0.09f, i % 3 == 0 ? light : c, 0.35f + 0.65f * k);
        }
    }

    /** The brood: glowing spiderlings skittering over the ground from the caster to the end of the cone. */
    private static void swarm(PoseStack pose, MultiBufferSource buffers, Camera camera, Fx fx, Vec3 at, float age, int c, int light) {
        Vec3 from = fx.origin.subtract(at);
        Vec3 to = fx.point().subtract(at);
        Vec3 d = to.subtract(from);
        Vec3 side = new Vec3(-d.z, 0, d.x);
        if (side.lengthSqr() > 1.0e-6) side = side.normalize();
        RandomSource r = RandomSource.create(fx.seed);
        for (int i = 0; i < 28; i++) {
            float lane = (r.nextFloat() - 0.5f) * 2;
            float delay = r.nextFloat() * 0.25f;
            float speed = 0.5f + r.nextFloat() * 0.25f;
            float k = Mth.clamp((age - delay) / speed, 0, 1);
            if (k <= 0 || k >= 1) continue;
            Vec3 start = from.add(side.scale(lane * 0.4));
            Vec3 end = to.add(side.scale(lane * fx.p.size()));
            Vec3 q = start.lerp(end, k).add(side.scale(Mth.sin(k * 22 + i) * 0.14)).add(0, 0.12 + Math.abs(Mth.sin(k * 31 + i)) * 0.1, 0);
            Vec3 back = start.lerp(end, Math.max(0, k - 0.05f)).add(0, 0.12, 0);
            float a = 1 - k * k * k * k;
            streak(pose, buffers, camera, at, back, q, 0.07f, c, a * 0.7f);
            dot(pose, buffers, camera, (float) q.x, (float) q.y, (float) q.z, 0.11f, c, a);
            dot(pose, buffers, camera, (float) q.x, (float) q.y, (float) q.z, 0.045f, light, a);
        }
        float k = Mth.clamp(age / 0.4f, 0, 1);
        disc(pose, buffers, GLOW, 0.04f, 1.2f, 0, c, (1 - k) * 0.5f);
    }

    /** Wyrmfire: a cone of flame rolling out of the caster's mouth. */
    private static void breath(PoseStack pose, MultiBufferSource buffers, Camera camera, Fx fx, Entity e, Vec3 at, float partial, float age, int c) {
        Vec3 d = fx.dir();
        // through the roarer's own eyes the fire starts a little further out, so it doesn't swallow the view
        boolean self = selfView(e);
        Vec3 mouth = (e != null ? e.getEyePosition(partial) : fx.origin.add(0, 1.6, 0)).subtract(at).add(d.scale(self ? 1.4 : 0.6));
        Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = d.cross(up).normalize();
        Vec3 s2 = d.cross(s1).normalize();
        float breath = Math.max(0.05f, fx.lasting());
        float len = fx.p.size();
        RandomSource r = RandomSource.create(fx.seed);
        int n = 64;
        for (int i = 0; i < n; i++) {
            float born = i / (float) n * breath;
            float travel = 0.5f + r.nextFloat() * 0.2f;
            float k = (age - born) / travel;
            double a = r.nextDouble() * Math.PI * 2;
            double wide = 0.35 + r.nextDouble() * 0.65;
            if (k <= 0 || k >= 1) continue;
            float o = 1 - (1 - k) * (1 - k);
            double spread = o * len * 0.42 * wide;
            Vec3 q = mouth.add(d.scale(len * o)).add(s1.scale(Math.cos(a) * spread)).add(s2.scale(Math.sin(a) * spread * 0.7));
            int col = k < 0.25f ? mix(0xFFF2B0, c, k / 0.25f) : mix(c, 0x6A0E00, (k - 0.25f) / 0.75f);
            dot(pose, buffers, camera, (float) q.x, (float) q.y, (float) q.z, 0.35f + 1.5f * k, col, (float) Math.pow(1 - k, 1.3) * 0.85f);
        }
        if (age < breath) {
            float flick = 0.8f + 0.2f * Mth.sin(age * 70);
            dotTex(pose, buffers, camera, BURST, (float) mouth.x, (float) mouth.y, (float) mouth.z, (self ? 0.3f : 0.7f) * flick, 0xFFE0A0, self ? 0.6f : 0.9f, age * 400);
        }
        // the roar's ring
        float k = Mth.clamp(age / 0.6f, 0, 1);
        if (k < 1) {
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            pose.pushPose();
            pose.translate(mouth.x, mouth.y - 1.45f, mouth.z);
            wave(pose, buffers, yaw, 1.45f, 0.4f + 2.5f * k, 0.5f + 2.2f * k, mix(c, 0xFFFFFF, 0.4f), (1 - k) * 0.8f);
            pose.popPose();
        }
    }

    // ================================================================ drawing helpers (each fetches its buffer right before drawing)

    private static float outBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    private static void disc(PoseStack pose, MultiBufferSource buffers, ResourceLocation tex, float y, float radius, float degrees, int rgb, float alpha) {
        if (alpha <= 0.01f || radius <= 0.01f) return;
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose.last().pose(), radius, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    private static void dot(PoseStack pose, MultiBufferSource buffers, Camera camera, float x, float y, float z, float half, int rgb, float alpha) {
        dotTex(pose, buffers, camera, GLOW, x, y, z, half, rgb, alpha, 0);
    }

    private static void dotTex(PoseStack pose, MultiBufferSource buffers, Camera camera, ResourceLocation tex,
                               float x, float y, float z, float half, int rgb, float alpha, float roll) {
        if (alpha <= 0.01f || half <= 0.005f) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camera.rotation());
        if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose.last().pose(), half, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    /** A ring standing up, facing along {@code yaw}, {@code dist} in front at {@code height}: a wave of sound or force. */
    private static void wave(PoseStack pose, MultiBufferSource buffers, float yaw, float height, float dist, float radius, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(0, height, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0, 0, dist);
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose.last().pose(), radius, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    private static void waveAt(PoseStack pose, MultiBufferSource buffers, float yaw, float dist, float height, float radius, int rgb, float alpha) {
        wave(pose, buffers, yaw, height, dist, radius, rgb, alpha);
    }

    /** A ring around a line (the lance): standing across {@code dir} at {@code at}. */
    private static void ringAlong(PoseStack pose, MultiBufferSource buffers, Vec3 at, Vec3 dir, float radius, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(-dir.y, -1, 1)));
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose.last().pose(), radius, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    /** The ashen tower shield, standing in front of the body, {@code rise} of it grown out of the ground. */
    private static void ward(PoseStack pose, MultiBufferSource buffers, float yaw, float rise, int rgb, float alpha) {
        if (alpha <= 0.01f || rise <= 0.01f) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0, 0, 0.85f);
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(WARD));
        Matrix4f m = pose.last().pose();
        float w = 0.78f, h = 2.3f * rise;
        int a = Mth.clamp(Math.round(Math.min(1f, alpha) * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        out.addVertex(m, -w, 0, 0).setUv(0, 1).setColor(r, g, b, a);
        out.addVertex(m, w, 0, 0).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, w, h, 0).setUv(1, 1 - rise).setColor(r, g, b, a);
        out.addVertex(m, -w, h, 0).setUv(0, 1 - rise).setColor(r, g, b, a);
        pose.popPose();
    }

    /** One cut of the flurry: a crescent through the body at a slant. */
    private static void cut(PoseStack pose, MultiBufferSource buffers, float height, float yaw, float tilt, float radius, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(0, height, 0);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.ZP.rotationDegrees(tilt));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(SLASH)), pose.last().pose(), radius, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    /** A column of light standing on the current pose origin, turned to the camera. */
    private static void column(PoseStack pose, MultiBufferSource buffers, Camera camera, float half, float height, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(BEAM));
        Matrix4f m = pose.last().pose();
        int a = Mth.clamp(Math.round(Math.min(1f, alpha) * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        out.addVertex(m, -half, -0.1f, 0).setUv(0, 1).setColor(r, g, b, a);
        out.addVertex(m, half, -0.1f, 0).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, half, height, 0).setUv(1, 0).setColor(r, g, b, a);
        out.addVertex(m, -half, height, 0).setUv(0, 0).setColor(r, g, b, a);
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
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        out.addVertex(m, -half, -half, 0).setUv(0, 1).setColor(red, green, blue, a);
        out.addVertex(m, half, -half, 0).setUv(1, 1).setColor(red, green, blue, a);
        out.addVertex(m, half, half, 0).setUv(1, 0).setColor(red, green, blue, a);
        out.addVertex(m, -half, half, 0).setUv(0, 0).setColor(red, green, blue, a);
    }

    static void clear() {
        ACTIVE.clear();
        CRACKS.clear();
    }

    private RuneWorldFx() {}
}
