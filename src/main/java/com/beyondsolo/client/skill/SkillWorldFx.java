package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.combat.CombatWorldFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.network.SkillFxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Skill effects in the world. Like the combat effects, light is drawn with the mod's light render type (magic
 * circles, rings, glows, streaks, crescents) and vanilla particles are used for sparks, smoke, debris and embers.
 * Runs on game time, so it pauses with the game and follows /tick rate.
 *
 * <p>The caster's own effects near the face (slash crescent, aura glow) are skipped in first person; ground
 * effects, bolts and zones still show, since the player sees those in front of them.</p>
 */
public final class SkillWorldFx {
    static final ResourceLocation GLOW = CombatWorldFx.GLOW;
    static final ResourceLocation RING = CombatWorldFx.RING;
    static final ResourceLocation SIGIL = CombatWorldFx.SIGIL;
    static final ResourceLocation BURST = CombatWorldFx.BURST;
    static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    static final ResourceLocation STREAK = BeyondSolo.id("textures/effect/streak.png");
    static final ResourceLocation SLASH = BeyondSolo.id("textures/effect/slash.png");
    private static final ResourceLocation[] ALL = {GLOW, RING, SIGIL, BURST, CIRCLE, STREAK, SLASH};

    private static final class Fx {
        final SkillFxPayload p;
        final float start;
        final Vec3 origin;
        final float yaw;
        final long seed;

        Fx(SkillFxPayload p, float start, Vec3 origin, float yaw, long seed) {
            this.p = p;
            this.start = start;
            this.origin = origin;
            this.yaw = yaw;
            this.seed = seed;
        }

        Vec3 target() {
            return new Vec3(p.x(), p.y(), p.z());
        }

        /** Seconds the effect lives. */
        float life() {
            float lasting = p.duration() / 20f;
            return switch (p.kind()) {
                case CAST -> 0.8f;
                case SLASH -> 0.45f;
                case DASH -> 0.6f;
                case PULSE -> 1.4f;
                case ROAR -> 1.0f;
                case SLAM -> 1.2f;
                case BLINK -> 0.7f;
                case BOLT -> 0.4f;
                case NOVA -> 0.9f;
                case METEOR -> lasting + 0.6f;
                case HEAL -> 1.2f;
                case SHOT -> 0.45f;
                case AURA, DOME, ZONE, MARK, VEIL, BIND, ELEMENT -> Math.max(0.6f, lasting);
                case WAVE -> 0.8f;
                case SPIN -> lasting + 0.25f;
                case FAN -> 0.4f;
                case SPLASH -> 0.7f;
                case VORTEX, RAIN -> lasting + 0.4f;
                case PILLAR -> lasting + 0.7f;
                case BLOSSOM -> 0.8f;
                case REACTION -> 0.9f;
                case CLEAVE -> lasting + 0.9f;
                case BANNER, BLADES, QUAKE -> Math.max(0.6f, lasting);
                case CRACK -> 1.7f;
                case GEYSER -> 1.0f;
                case BEAM -> lasting + 0.2f;
                case ORB -> Math.max(0.4f, lasting);
                case THUNDER -> 0.55f;
                case SHIELD, CHAIN -> Math.max(0.3f, lasting);
                case HAMMER, DISCUS -> Math.max(0.1f, lasting) + 0.05f;
                case HALO, WINGS, BUBBLE, ORBIT -> Math.max(0.4f, lasting);
                case STREAM -> Math.max(0.3f, lasting) + 0.1f;
            };
        }
    }

    private static final List<Fx> ACTIVE = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private static float now(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() + partial;
    }

    public static void play(SkillFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(p.entityId());
        Vec3 origin = e != null ? e.position() : new Vec3(p.x(), p.y(), p.z());
        float yaw = e != null ? e.getYRot() : 0;
        if (p.kind() == SkillFxPayload.Kind.ELEMENT || p.kind() == SkillFxPayload.Kind.REACTION) {
            // a creature carries one element at a time, and a reaction uses it up
            ACTIVE.removeIf(f -> f.p.kind() == SkillFxPayload.Kind.ELEMENT && f.p.entityId() == p.entityId());
        }
        if (p.kind() == SkillFxPayload.Kind.ORBIT) {
            // one set of lights of a colour per creature: a new count replaces the old one, and none takes them away
            ACTIVE.removeIf(f -> f.p.kind() == SkillFxPayload.Kind.ORBIT && f.p.entityId() == p.entityId() && f.p.color() == p.color());
            if (p.size() < 0.5f) return;
        }
        if (p.kind() == SkillFxPayload.Kind.HALO || p.kind() == SkillFxPayload.Kind.WINGS || p.kind() == SkillFxPayload.Kind.BUBBLE) {
            // cast again before it ran out: the new one takes the old one's place instead of doubling it
            ACTIVE.removeIf(f -> f.p.kind() == p.kind() && f.p.entityId() == p.entityId() && f.p.size() == p.size());
        }
        if (p.kind() == SkillFxPayload.Kind.REACTION) {
            com.beyondsolo.skill.SkillReaction[] all = com.beyondsolo.skill.SkillReaction.values();
            ComboHud.reaction(all[Math.floorMod(p.duration(), all.length)], new Vec3(p.x(), p.y(), p.z()));
        }
        Fx fx = new Fx(p, now(0), origin, yaw, RANDOM.nextLong());
        ACTIVE.add(fx);
        if (ACTIVE.size() > 96) ACTIVE.remove(0);
        burstParticles(mc.level, fx, e);
    }

    private static Vector3f rgb(int color, float towardsWhite) {
        return new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f)
                .lerp(new Vector3f(1, 1, 1), towardsWhite);
    }

    private static double rnd(double s) {
        return (RANDOM.nextDouble() - 0.5) * 2 * s;
    }

    private static void burstParticles(ClientLevel level, Fx fx, Entity e) {
        SkillFxPayload p = fx.p;
        Vec3 o = fx.origin;
        DustParticleOptions dust = new DustParticleOptions(rgb(p.color(), 0.2f), 1.1f);
        boolean self = e == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        switch (p.kind()) {
            case CAST -> {
                for (int i = 0; i < 14; i++) {
                    double a = i / 14.0 * Math.PI * 2;
                    level.addParticle(dust, o.x + Math.cos(a) * 0.9, o.y + 0.1, o.z + Math.sin(a) * 0.9, 0, 0.06, 0);
                }
            }
            case SLASH -> {
                if (e == null) break;
                Vec3 look = Vec3.directionFromRotation(0, fx.yaw);
                Vec3 side = new Vec3(-look.z, 0, look.x);
                for (int i = -2; i <= 2; i++) {
                    Vec3 at = o.add(look.scale(2.0 - Math.abs(i) * 0.25)).add(side.scale(i * 0.8)).add(0, 1.1, 0);
                    level.addParticle(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 0, 0, 0);
                    level.addParticle(dust, at.x, at.y, at.z, look.x * 0.2, 0.02, look.z * 0.2);
                }
            }
            case DASH -> {
                for (int i = 0; i < 10; i++) level.addParticle(ParticleTypes.CLOUD, p.x() + rnd(0.3), p.y() + 0.1, p.z() + rnd(0.3), rnd(0.05), 0.02, rnd(0.05));
            }
            case PULSE -> {
                // duration 1 is a quiet ripple, for waves that keep coming: the rings alone, no motes
                if (p.duration() == 1) break;
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0 * Math.PI * 2;
                    level.addParticle(ParticleTypes.END_ROD, o.x + Math.cos(a) * 1.2, o.y + 0.2, o.z + Math.sin(a) * 1.2, Math.cos(a) * 0.5, 0.01, Math.sin(a) * 0.5);
                }
            }
            case ROAR -> {
                for (int i = 0; i < 24; i++) {
                    double a = i / 24.0 * Math.PI * 2;
                    level.addParticle(dust, o.x + Math.cos(a) * 0.6, o.y + 1.2, o.z + Math.sin(a) * 0.6, Math.cos(a) * 0.6, 0.02, Math.sin(a) * 0.6);
                }
                if (!self) level.addParticle(ParticleTypes.FLASH, o.x, o.y + 1.2, o.z, 0, 0, 0);
            }
            case SLAM -> {
                Vec3 at = fx.target();
                BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
                ParticleOptions debris = ground.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, ground);
                for (int i = 0; i < 70; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    double r = RANDOM.nextDouble() * p.size();
                    level.addParticle(debris, at.x + Math.cos(a) * r, at.y + 0.1, at.z + Math.sin(a) * r, Math.cos(a) * 0.2, 0.4 + RANDOM.nextDouble() * 0.4, Math.sin(a) * 0.2);
                }
                for (int i = 0; i < 16; i++) level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x + rnd(2), at.y + 0.2, at.z + rnd(2), 0, 0.03, 0);
            }
            case BLINK -> {
                for (Vec3 at : new Vec3[]{fx.target(), o}) {
                    if (p.duration() == 1) {
                        // a step of light leaves motes behind, not smoke
                        for (int i = 0; i < 10; i++) level.addParticle(ParticleTypes.END_ROD, at.x + rnd(0.3), at.y + RANDOM.nextDouble() * 1.8, at.z + rnd(0.3),
                                rnd(0.05), 0.04 + RANDOM.nextDouble() * 0.06, rnd(0.05));
                        continue;
                    }
                    for (int i = 0; i < 24; i++) level.addParticle(ParticleTypes.PORTAL, at.x + rnd(0.4), at.y + RANDOM.nextDouble() * 1.8, at.z + rnd(0.4), rnd(0.6), rnd(0.3), rnd(0.6));
                    for (int i = 0; i < 8; i++) level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + rnd(0.3), at.y + RANDOM.nextDouble() * 1.6, at.z + rnd(0.3), 0, 0.02, 0);
                }
            }
            case BOLT -> {
                Vec3 at = fx.target();
                for (int i = 0; i < 16; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, rnd(0.4), rnd(0.4), rnd(0.4));
            }
            case THUNDER -> {
                Vec3 at = fx.target();
                for (int i = 0; i < 30; i++) level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x + rnd(0.5), at.y + 0.2, at.z + rnd(0.5), rnd(0.8), RANDOM.nextDouble() * 0.6, rnd(0.8));
                for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + rnd(0.6), at.y + 0.2, at.z + rnd(0.6), 0, 0.04, 0);
            }
            case NOVA -> {
                // what the ring throws: flames (duration 0), sparks (1), spray (2), motes of light (3) or soul fire (4)
                int n = p.duration() == 0 ? 60 : 36;
                for (int i = 0; i < n; i++) {
                    double a = i / (double) n * Math.PI * 2;
                    double sp = 0.25 + RANDOM.nextDouble() * 0.15;
                    ParticleOptions thrown = switch (p.duration()) {
                        case 1 -> ParticleTypes.ELECTRIC_SPARK;
                        case 2 -> i % 3 == 0 ? ParticleTypes.BUBBLE_POP : ParticleTypes.SPLASH;
                        case 3 -> i % 3 == 0 ? ParticleTypes.END_ROD : dust;
                        case 4 -> i % 3 == 0 ? ParticleTypes.SOUL : ParticleTypes.SOUL_FIRE_FLAME;
                        default -> i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME;
                    };
                    // a splash only flies sideways when it is given no lift of its own
                    level.addParticle(thrown, o.x + Math.cos(a) * 0.6, o.y + 0.3, o.z + Math.sin(a) * 0.6,
                            Math.cos(a) * sp, p.duration() == 2 ? 0 : 0.02, Math.sin(a) * sp);
                }
                if (p.duration() == 0) for (int i = 0; i < 8; i++) level.addParticle(ParticleTypes.LAVA, o.x, o.y + 0.5, o.z, 0, 0, 0);
            }
            case HEAL -> {
                for (int i = 0; i < 18; i++) {
                    level.addParticle(i % 3 == 0 ? ParticleTypes.HAPPY_VILLAGER : dust, o.x + rnd(0.5), o.y + RANDOM.nextDouble() * 2, o.z + rnd(0.5), 0, 0.08, 0);
                }
                for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.END_ROD, o.x + rnd(0.4), o.y + 0.2, o.z + rnd(0.4), 0, 0.12, 0);
            }
            case SHOT -> {
                Vec3 from = e != null ? e.getEyePosition() : o;
                Vec3 to = fx.target();
                Vec3 d = to.subtract(from);
                int n = (int) Math.min(40, d.length() * 1.5);
                for (int i = 0; i < n; i++) {
                    Vec3 at = from.add(d.scale(i / (double) n));
                    level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, rnd(0.05), rnd(0.05), rnd(0.05));
                }
            }
            case VEIL -> {
                for (int i = 0; i < 40; i++) level.addParticle(i % 2 == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.LARGE_SMOKE,
                        o.x + rnd(0.5), o.y + RANDOM.nextDouble() * 1.9, o.z + rnd(0.5), rnd(0.08), 0.02, rnd(0.08));
            }
            case WAVE -> {
                Vec3 to = fx.target();
                Vec3 d = to.subtract(o);
                BlockState ground = level.getBlockState(BlockPos.containing(o.x, o.y - 0.5, o.z));
                ParticleOptions debris = ground.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, ground);
                int n = (int) (d.length() * 4);
                for (int i = 0; i < n; i++) {
                    Vec3 at = o.add(d.scale(i / (double) n));
                    double sx = rnd(p.size()), sz = rnd(p.size());
                    if (p.duration() == 0) {
                        level.addParticle(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, at.x + sx, at.y + 0.1, at.z + sz, d.x * 0.02, 0.06 + RANDOM.nextDouble() * 0.12, d.z * 0.02);
                        if (i % 6 == 0) level.addParticle(ParticleTypes.LAVA, at.x, at.y + 0.2, at.z, 0, 0, 0);
                    } else {
                        level.addParticle(debris, at.x + sx, at.y + 0.1, at.z + sz, 0, 0.5 + RANDOM.nextDouble() * 0.5, 0);
                        if (i % 4 == 0) level.addParticle(ParticleTypes.POOF, at.x + sx, at.y + 0.2, at.z + sz, 0, 0.04, 0);
                    }
                }
            }
            case FAN -> {
                Vec3 d = fx.target().subtract(o).normalize();
                for (int i = 0; i < 16; i++) level.addParticle(ParticleTypes.CRIT, o.x + d.x, o.y + 1.2, o.z + d.z, d.x * 1.2 + rnd(0.7), rnd(0.1), d.z * 1.2 + rnd(0.7));
            }
            case SPLASH -> {
                Vec3 at = fx.target();
                if (p.duration() == 0) {
                    for (int i = 0; i < 30; i++) {
                        double a = i / 30.0 * Math.PI * 2;
                        level.addParticle(ParticleTypes.FALLING_WATER, at.x + Math.cos(a) * p.size() * 0.6, at.y + 0.8 + RANDOM.nextDouble(), at.z + Math.sin(a) * p.size() * 0.6, 0, 0, 0);
                        level.addParticle(dust, at.x + Math.cos(a) * 0.4, at.y, at.z + Math.sin(a) * 0.4, Math.cos(a) * 0.4, 0.1, Math.sin(a) * 0.4);
                    }
                } else {
                    for (int i = 0; i < 20; i++) level.addParticle(dust, at.x, at.y, at.z, rnd(0.6), rnd(0.4), rnd(0.6));
                    level.addParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0, 0, 0);
                }
            }
            case PILLAR -> {
                if (p.duration() == 0) level.addParticle(ParticleTypes.FLASH, p.x(), p.y() + 1, p.z(), 0, 0, 0);
            }
            case BLOSSOM -> {
                for (int i = 0; i < 70; i++) {
                    double a = i / 70.0 * Math.PI * 2;
                    double sp = 0.2 + RANDOM.nextDouble() * 0.25;
                    level.addParticle(i % 3 == 0 ? ParticleTypes.CHERRY_LEAVES : i % 3 == 1 ? ParticleTypes.SPORE_BLOSSOM_AIR : dust,
                            o.x + Math.cos(a) * 0.7, o.y + 0.6 + RANDOM.nextDouble() * 0.9, o.z + Math.sin(a) * 0.7, Math.cos(a) * sp, 0.03, Math.sin(a) * sp);
                }
            }
            case REACTION -> {
                for (int i = 0; i < 26; i++) level.addParticle(dust, p.x(), p.y(), p.z(), rnd(0.9), rnd(0.7), rnd(0.9));
                level.addParticle(ParticleTypes.FLASH, p.x(), p.y(), p.z(), 0, 0, 0);
            }
            case MARK -> {
                if (e != null) for (int i = 0; i < 12; i++) level.addParticle(dust, e.getX() + rnd(0.4), e.getY() + e.getBbHeight() + 0.5, e.getZ() + rnd(0.4), 0, 0.05, 0);
            }
            case CRACK -> {
                BlockState ground = level.getBlockState(BlockPos.containing(p.x(), p.y() - 0.5, p.z()));
                ParticleOptions debris = ground.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, ground);
                for (int i = 0; i < 40; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    double r = RANDOM.nextDouble() * p.size();
                    level.addParticle(i % 4 == 0 ? dust : debris, p.x() + Math.cos(a) * r, p.y() + 0.1, p.z() + Math.sin(a) * r, 0, 0.25 + RANDOM.nextDouble() * 0.3, 0);
                }
            }
            case GEYSER -> {
                // what flies up with the jets: embers (duration 0), the ground itself (1), motes of light (2), water (3) or souls (4)
                if (p.duration() == 3) {
                    // drops fall back from the top of the jet and it splashes at the foot: no dust in a fountain
                    for (int i = 0; i < 12; i++) level.addParticle(ParticleTypes.FALLING_WATER, p.x() + rnd(0.9), p.y() + p.size() * (0.5 + RANDOM.nextDouble() * 0.5), p.z() + rnd(0.9), 0, 0, 0);
                    for (int i = 0; i < 8; i++) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        level.addParticle(ParticleTypes.SPLASH, p.x() + rnd(0.4), p.y() + 0.1, p.z() + rnd(0.4), Math.cos(a) * 0.25, 0, Math.sin(a) * 0.25);
                    }
                    break;
                }
                BlockState ground = level.getBlockState(BlockPos.containing(p.x(), p.y() - 0.5, p.z()));
                ParticleOptions chunk = p.duration() == 2 ? ParticleTypes.END_ROD
                        : p.duration() == 4 ? ParticleTypes.SOUL
                        : p.duration() == 1 ? (ground.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, ground))
                        : ParticleTypes.LAVA;
                for (int i = 0; i < 46; i++) {
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    double sp = RANDOM.nextDouble() * 0.16;
                    level.addParticle(i % 5 == 0 ? chunk : dust, p.x() + rnd(0.3), p.y() + 0.2 + RANDOM.nextDouble() * p.size() * 0.5, p.z() + rnd(0.3),
                            Math.cos(a) * sp, 0.25 + RANDOM.nextDouble() * 0.35, Math.sin(a) * sp);
                }
            }
            case BANNER -> {
                for (int i = 0; i < 24; i++) {
                    double a = i / 24.0 * Math.PI * 2;
                    level.addParticle(dust, p.x() + Math.cos(a) * 0.5, p.y() + 0.2, p.z() + Math.sin(a) * 0.5, Math.cos(a) * 0.35, 0.02, Math.sin(a) * 0.35);
                }
            }
            default -> {}
        }
    }

    /** Lasting effects drop a few particles every tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        float time = now(0);
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Fx fx : ACTIVE) {
            float age = (time - fx.start) / 20f;
            if (age > fx.life()) continue;
            Entity e = mc.level.getEntity(fx.p.entityId());
            DustParticleOptions dust = new DustParticleOptions(rgb(fx.p.color(), 0.25f), 0.9f);
            switch (fx.p.kind()) {
                case AURA -> {
                    if (e == null || (e == mc.player && firstPerson)) break;
                    if (RANDOM.nextFloat() < 0.6f) mc.level.addParticle(dust, e.getX() + rnd(0.45), e.getY() + RANDOM.nextDouble() * e.getBbHeight(), e.getZ() + rnd(0.45), 0, 0.04, 0);
                }
                case VEIL -> {
                    if (e == null || RANDOM.nextFloat() > 0.35f) break;
                    mc.level.addParticle(ParticleTypes.SMOKE, e.getX() + rnd(0.35), e.getY() + 0.1, e.getZ() + rnd(0.35), 0, 0.01, 0);
                }
                case ZONE -> {
                    double r = fx.p.size();
                    for (int i = 0; i < 2; i++) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        double d = Math.sqrt(RANDOM.nextDouble()) * r;
                        mc.level.addParticle(RANDOM.nextFloat() < 0.5f ? ParticleTypes.END_ROD : dust,
                                fx.p.x() + Math.cos(a) * d, fx.p.y() + 0.1, fx.p.z() + Math.sin(a) * d, 0, 0.05 + RANDOM.nextDouble() * 0.05, 0);
                    }
                }
                case DOME -> {
                    Vec3 c = domeCenter(fx, e, 0);
                    double r = fx.p.size();
                    if (RANDOM.nextFloat() < 0.5f) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        double b = RANDOM.nextDouble() * Math.PI / 2;
                        mc.level.addParticle(dust, c.x + Math.cos(a) * Math.cos(b) * r, c.y + Math.sin(b) * r, c.z + Math.sin(a) * Math.cos(b) * r, 0, 0, 0);
                    }
                }
                case METEOR -> {
                    float k = Mth.clamp(age / (fx.p.duration() / 20f), 0, 1);
                    if (k < 1) {
                        Vec3 at = meteorAt(fx, k);
                        mc.level.addParticle(ParticleTypes.FLAME, at.x + rnd(0.2), at.y + rnd(0.2), at.z + rnd(0.2), 0, 0.05, 0);
                        mc.level.addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 0, 0.02, 0);
                    }
                }
                case SPIN -> {
                    if (e == null) break;
                    for (int i = 0; i < 2; i++) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        mc.level.addParticle(RANDOM.nextBoolean() ? ParticleTypes.SWEEP_ATTACK : ParticleTypes.CLOUD,
                                e.getX() + Math.cos(a) * fx.p.size() * 0.7, e.getY() + 0.8 + RANDOM.nextDouble() * 0.6, e.getZ() + Math.sin(a) * fx.p.size() * 0.7, -Math.sin(a) * 0.3, 0, Math.cos(a) * 0.3);
                    }
                }
                case VORTEX -> {
                    for (int i = 0; i < 3; i++) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        double h = RANDOM.nextDouble() * 4.5;
                        double r = fx.p.size() * (0.2 + 0.13 * h);
                        mc.level.addParticle(i == 0 ? ParticleTypes.CLOUD : dust, fx.p.x() + Math.cos(a) * r, fx.p.y() + h, fx.p.z() + Math.sin(a) * r,
                                -Math.sin(a) * 0.45, 0.06, Math.cos(a) * 0.45);
                    }
                }
                case BIND -> {
                    if (RANDOM.nextFloat() > 0.5f) break;
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    double r = Math.sqrt(RANDOM.nextDouble()) * fx.p.size();
                    Vec3 c = fx.p.size() < 3 && e != null ? e.position() : fx.target();
                    mc.level.addParticle(fx.p.size() < 3 ? ParticleTypes.SMOKE : dust, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 0, 0.04, 0);
                }
                case RAIN -> {
                    if (age > fx.p.duration() / 20f) break;
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    double r = Math.sqrt(RANDOM.nextDouble()) * fx.p.size();
                    mc.level.addParticle(ParticleTypes.CRIT, fx.p.x() + Math.cos(a) * r, fx.p.y() + 0.2, fx.p.z() + Math.sin(a) * r, rnd(0.2), 0.3, rnd(0.2));
                }
                case CLEAVE -> {
                    // the moment the blade lands
                    float land = fx.p.duration() / 20f;
                    if (age >= land && age < land + 0.051f) {
                        BlockState ground = mc.level.getBlockState(BlockPos.containing(fx.p.x(), fx.p.y() - 0.5, fx.p.z()));
                        ParticleOptions debris = ground.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, ground);
                        for (int i = 0; i < 36; i++) {
                            double a = RANDOM.nextDouble() * Math.PI * 2;
                            mc.level.addParticle(i % 3 == 0 ? dust : debris, fx.p.x() + Math.cos(a) * 0.4, fx.p.y() + 0.1, fx.p.z() + Math.sin(a) * 0.4,
                                    Math.cos(a) * 0.3, 0.3 + RANDOM.nextDouble() * 0.4, Math.sin(a) * 0.3);
                        }
                        mc.level.addParticle(ParticleTypes.FLASH, fx.p.x(), fx.p.y() + 0.6, fx.p.z(), 0, 0, 0);
                    }
                }
                case BANNER -> {
                    if (RANDOM.nextFloat() < 0.5f) mc.level.addParticle(dust, fx.p.x() + rnd(0.6), fx.p.y() + 2.6 + RANDOM.nextDouble() * 1.6, fx.p.z() + rnd(0.6), 0, 0.03, 0);
                    if (RANDOM.nextFloat() < 0.3f) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        mc.level.addParticle(ParticleTypes.END_ROD, fx.p.x() + Math.cos(a) * fx.p.size(), fx.p.y() + 0.1, fx.p.z() + Math.sin(a) * fx.p.size(), 0, 0.06, 0);
                    }
                }
                case BLADES -> {
                    if (e == null || RANDOM.nextFloat() > 0.5f) break;
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    mc.level.addParticle(ParticleTypes.CRIT, e.getX() + Math.cos(a) * fx.p.size(), e.getY() + 0.9 + rnd(0.4), e.getZ() + Math.sin(a) * fx.p.size(), -Math.sin(a) * 0.3, 0, Math.cos(a) * 0.3);
                }
                case QUAKE -> {
                    BlockState ground = mc.level.getBlockState(BlockPos.containing(fx.p.x(), fx.p.y() - 0.5, fx.p.z()));
                    ParticleOptions debris = ground.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, ground);
                    for (int i = 0; i < 3; i++) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        double d = Math.sqrt(RANDOM.nextDouble()) * fx.p.size();
                        mc.level.addParticle(debris, fx.p.x() + Math.cos(a) * d, fx.p.y() + 0.1, fx.p.z() + Math.sin(a) * d, 0, 0.2 + RANDOM.nextDouble() * 0.3, 0);
                    }
                }
                case ELEMENT -> {
                    if (e != null && RANDOM.nextFloat() < 0.3f) mc.level.addParticle(dust, e.getX() + rnd(0.4), e.getY() + RANDOM.nextDouble() * e.getBbHeight(), e.getZ() + rnd(0.4), 0, 0.03, 0);
                }
                case MARK -> {
                    if (e != null && RANDOM.nextFloat() < 0.25f) mc.level.addParticle(dust, e.getX() + rnd(0.3), e.getY() + e.getBbHeight() + 0.4, e.getZ() + rnd(0.3), 0, -0.02, 0);
                }
                case WINGS -> {
                    // a mote of light drifts down from the feathers now and then
                    if (e == null || (e == mc.player && firstPerson) || RANDOM.nextFloat() > 0.12f) break;
                    double reach = 1.3 * fx.p.size();
                    mc.level.addParticle(ParticleTypes.END_ROD, e.getX() + rnd(reach), e.getY() + e.getBbHeight() * (0.5 + RANDOM.nextDouble() * 0.6), e.getZ() + rnd(reach), 0, -0.02, 0);
                }
                case BUBBLE -> {
                    if (e == null || RANDOM.nextFloat() > 0.1f) break;
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    mc.level.addParticle(ParticleTypes.BUBBLE_POP, e.getX() + Math.cos(a) * fx.p.size(), e.getY() + RANDOM.nextDouble() * e.getBbHeight(), e.getZ() + Math.sin(a) * fx.p.size(), 0, 0.02, 0);
                }
                default -> {}
            }
        }
    }

    private static Vec3 domeCenter(Fx fx, Entity e, float partial) {
        // a small dome follows its caster (Bulwark), a big one stays where it was raised (Aegis Dome)
        if (fx.p.size() < 3 && e != null) return e.getPosition(partial);
        return fx.target();
    }

    /** The way a creature's body faces: a player's body turns after their head. */
    private static float bodyYaw(Entity e, float partial) {
        return e instanceof LivingEntity living ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : e.getViewYRot(partial);
    }

    /** A direction in the plane of a wing: out to the side at {@code angle} above the horizon, swept back a little. */
    private static Vec3 wingDir(Vec3 outward, Vec3 fwd, float angle) {
        return outward.scale(Mth.cos(angle)).add(0, Mth.sin(angle), 0).add(fwd.scale(-0.22)).normalize();
    }

    private static Vec3 meteorAt(Fx fx, float k) {
        Vec3 land = fx.target();
        Vec3 from = land.add(4, 22, 2.5);
        return from.lerp(land, k * k);
    }

    /** Draws the light part of every effect. Called once per frame after particles. */
    public static void render(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = now(partial);
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        Iterator<Fx> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            float age = (time - fx.start) / 20f;
            float life = fx.life();
            if (age >= life + 0.05f || age < -1) {
                it.remove();
                continue;
            }
            SkillFxPayload p = fx.p;
            Entity e = mc.level.getEntity(p.entityId());
            boolean self = e == mc.player && firstPerson;
            Vec3 at = e != null ? e.getPosition(partial) : fx.origin;
            float t = Mth.clamp(age / life, 0, 1);
            float out = 1 - (1 - t) * (1 - t) * (1 - t);
            float fade = 1 - t;
            // lasting effects fade in and out at the ends instead of over their whole life
            float hold = Mth.clamp(age / 0.3f, 0, 1) * Mth.clamp((life - age) / 0.5f, 0, 1);
            int c = p.color();
            int light = mix(c, 0xFFFFFF, 0.5f);

            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            switch (p.kind()) {
                case CAST -> {
                    float k = Mth.clamp(age / 0.25f, 0, 1);
                    disc(pose, buffers, CIRCLE, 0.04f, p.size() * (0.5f + 0.5f * k), time * 4, c, fade * 1.1f);
                    disc(pose, buffers, RING, 0.05f, p.size() * (0.6f + 0.9f * out), 0, light, fade * 0.8f);
                }
                case SLASH -> {
                    if (self) break;
                    // duration 1 is the cut back the other way
                    boolean back = p.duration() == 1;
                    float k = Mth.clamp(age / 0.14f, 0, 1);
                    float sweep = back ? Mth.lerp(k, 60, -60) : Mth.lerp(k, -60, 60);
                    float lag = back ? 14 : -14;
                    slash(pose, buffers, fx.yaw, sweep, 1.1f, p.size(), c, fade * 1.3f);
                    slash(pose, buffers, fx.yaw, sweep + lag * 0.5f, 1.1f, p.size() * 0.9f, 0xFFFFFF, fade);
                    slash(pose, buffers, fx.yaw, sweep + lag, 1.15f, p.size() * 1.08f, light, fade * 0.7f);
                    slash(pose, buffers, fx.yaw, sweep + lag * 2.2f, 1.1f, p.size() * 1.02f, c, fade * 0.45f);
                    Vec3 front = Vec3.directionFromRotation(0, fx.yaw).scale(p.size() * 0.55f);
                    dot(pose, buffers, camera, (float) front.x, 1.1f, (float) front.z, p.size() * 0.5f * (1 - t * 0.4f), c, fade * 0.45f);
                    disc(pose, buffers, RING, 0.06f, p.size() * (0.5f + 0.6f * out), 0, light, fade * 0.6f);
                }
                case DASH -> {
                    Vec3 start = fx.target().subtract(at);
                    for (int i = 0; i < 7; i++) {
                        float k = i / 7f;
                        Vec3 g = start.scale(1 - k);
                        dot(pose, buffers, camera, (float) g.x, (float) g.y + 1f, (float) g.z, 0.55f + 0.25f * k, c, fade * (0.3f + 0.5f * k));
                    }
                    pose.pushPose();
                    pose.translate(start.x, start.y, start.z);
                    disc(pose, buffers, RING, 0.06f, 0.6f + 1.6f * out, 0, light, fade);
                    pose.popPose();
                }
                case PULSE -> {
                    disc(pose, buffers, RING, 0.1f, p.size() * out, 0, light, fade * 1.2f);
                    float k2 = Mth.clamp((age - 0.25f) / (life - 0.25f), 0, 1);
                    if (k2 > 0) disc(pose, buffers, RING, 0.1f, p.size() * 0.7f * (1 - (1 - k2) * (1 - k2)), 0, c, (1 - k2) * 0.9f);
                    disc(pose, buffers, GLOW, 0.05f, 2.5f * (1 - t), 0, c, fade * 0.6f);
                }
                case ROAR -> {
                    for (int i = 0; i < 3; i++) {
                        float k = Mth.clamp((age - i * 0.12f) / 0.7f, 0, 1);
                        if (k <= 0 || k >= 1) continue;
                        float o = 1 - (1 - k) * (1 - k);
                        disc(pose, buffers, RING, 1.2f + i * 0.15f, 0.6f + p.size() * 0.75f * o, 0, i == 1 ? light : c, (1 - k) * 1.1f);
                    }
                    disc(pose, buffers, RING, 0.06f, p.size() * out, 0, c, fade * 0.8f);
                    if (!self) dot(pose, buffers, camera, 0, 1.3f, 0, 1.4f * (1 - t * 0.5f), c, fade * 0.6f);
                }
                case SLAM -> {
                    Vec3 g = fx.target().subtract(at);
                    pose.translate(g.x, g.y, g.z);
                    disc(pose, buffers, SIGIL, 0.05f, p.size() * (0.4f + 0.6f * out), time * 2, c, fade * 1.3f);
                    disc(pose, buffers, RING, 0.08f, p.size() * 1.3f * out, 0, light, fade * 1.2f);
                    disc(pose, buffers, GLOW, 0.04f, p.size() * 0.8f, 0, c, fade * 0.7f);
                    dotTex(pose, buffers, camera, BURST, 0, 0.5f, 0, 1.2f + 2f * out, light, fade, fx.seed % 360);
                }
                case BLINK -> {
                    Vec3 from = fx.target().subtract(at);
                    dot(pose, buffers, camera, (float) from.x, (float) from.y + 1f, (float) from.z, 1.2f * (1 - out * 0.6f), c, fade);
                    pose.pushPose();
                    pose.translate(from.x, from.y, from.z);
                    disc(pose, buffers, RING, 0.05f, 0.4f + 1.4f * out, 0, c, fade);
                    pose.popPose();
                    if (!self) dot(pose, buffers, camera, 0, 1f, 0, 1.3f * (1 - out), light, fade);
                    disc(pose, buffers, RING, 0.05f, 0.4f + 1.4f * out, 0, light, fade);
                    streak(pose, buffers, camera, at, from.add(0, 1, 0), new Vec3(0, 1, 0), 0.25f * fade, c, fade * 0.7f);
                }
                case AURA -> {
                    if (self) {
                        disc(pose, buffers, RING, 0.05f, 0.9f + 0.08f * Mth.sin(time * 0.3f), time * 3, c, hold * 0.6f);
                        break;
                    }
                    disc(pose, buffers, RING, 0.05f, 0.9f + 0.08f * Mth.sin(time * 0.3f), time * 3, c, hold * 0.8f);
                    disc(pose, buffers, GLOW, 0.04f, 1.2f, 0, c, hold * 0.4f);
                    dot(pose, buffers, camera, 0, 1f, 0, 1.0f, c, hold * (0.18f + 0.08f * Mth.sin(time * 0.25f)));
                }
                case BOLT -> {
                    Vec3 from = (e != null ? e.getEyePosition(partial).add(e.getViewVector(partial).scale(0.6)).add(0, -0.35, 0) : fx.origin).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    float flick = 0.75f + 0.25f * Mth.sin(time * 3.1f);
                    zigzag(pose, buffers, camera, at, from, to, fx.seed + (long) (time / 2), 0.32f * flick, c, fade * 1.3f);
                    zigzag(pose, buffers, camera, at, from, to, fx.seed + (long) (time / 2), 0.12f, 0xFFFFFF, fade);
                    dotTex(pose, buffers, camera, BURST, (float) to.x, (float) to.y, (float) to.z, 0.6f + 0.9f * out, light, fade, fx.seed % 360);
                    dot(pose, buffers, camera, (float) from.x, (float) from.y, (float) from.z, 0.35f, light, fade);
                }
                case NOVA -> {
                    disc(pose, buffers, RING, 0.15f, p.size() * out, 0, c, fade * 1.3f);
                    disc(pose, buffers, RING, 0.6f, p.size() * 0.85f * out, 0, p.duration() == 0 ? mix(c, 0xFFE0A0, 0.4f) : light, fade * 0.9f);
                    disc(pose, buffers, GLOW, 0.1f, p.size() * 0.9f * out, 0, c, fade * 0.6f);
                    dot(pose, buffers, camera, 0, 0.9f, 0, 1.6f * (1 - t), mix(c, 0xFFFFFF, 0.3f), fade * 0.8f);
                }
                case METEOR -> {
                    Vec3 land = fx.target().subtract(at);
                    float fall = p.duration() / 20f;
                    pose.pushPose();
                    pose.translate(land.x, land.y, land.z);
                    if (age < fall) {
                        float k = age / fall;
                        disc(pose, buffers, RING, 0.06f, p.size() * (1.2f - 0.6f * k), -time * 4, c, 0.5f + 0.5f * k);
                        disc(pose, buffers, GLOW, 0.05f, p.size() * 0.8f, 0, c, 0.3f + 0.4f * k);
                    } else {
                        float k = Mth.clamp((age - fall) / 0.6f, 0, 1);
                        float o = 1 - (1 - k) * (1 - k);
                        disc(pose, buffers, RING, 0.08f, p.size() * (0.5f + 1.3f * o), 0, light, 1 - k);
                        dotTex(pose, buffers, camera, BURST, 0, 0.6f, 0, 1f + 2f * o, mix(c, 0xFFFFFF, 0.3f), (1 - k) * 1.2f, fx.seed % 360);
                    }
                    pose.popPose();
                    if (age < fall) {
                        float k = age / fall;
                        Vec3 rock = meteorAt(fx, k).subtract(at);
                        Vec3 tail = meteorAt(fx, Math.max(0, k - 0.18f)).subtract(at);
                        streak(pose, buffers, camera, at, tail, rock, 0.7f, c, 0.9f);
                        dot(pose, buffers, camera, (float) rock.x, (float) rock.y, (float) rock.z, 0.9f, mix(c, 0xFFFFFF, 0.4f), 1.2f);
                        dot(pose, buffers, camera, (float) rock.x, (float) rock.y, (float) rock.z, 1.8f, c, 0.6f);
                    }
                }
                case DOME -> {
                    Vec3 cc = domeCenter(fx, e, partial).subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    float r = p.size() * (0.7f + 0.3f * Mth.clamp(age / 0.3f, 0, 1));
                    boolean big = p.size() >= 3;
                    float a = hold * (self && !big ? 0.45f : 1f);
                    for (int i = 0; i < 3; i++) {
                        pose.pushPose();
                        pose.translate(0, big ? 0 : 0.9f, 0);
                        pose.mulPose(Axis.YP.rotationDegrees(time * 2 + i * 60));
                        pose.mulPose(Axis.ZP.rotationDegrees(90));
                        disc(pose, buffers, RING, 0, r * 1.05f, 0, i == 0 ? light : c, a * 0.55f);
                        pose.popPose();
                    }
                    disc(pose, buffers, RING, 0.06f, r, -time * 2, light, a * 0.9f);
                    if (big) disc(pose, buffers, CIRCLE, 0.04f, r * 0.95f, time * 1.5f, c, a * 0.7f);
                    if (!self || big) dot(pose, buffers, camera, 0, big ? r * 0.35f : 0.9f, 0, r * 1.15f, c, a * 0.22f);
                }
                case HEAL -> {
                    if (!self) streak(pose, buffers, camera, at, new Vec3(0, -0.1, 0), new Vec3(0, 3.2, 0), 0.9f * (1 - t * 0.5f), c, fade * 0.9f);
                    float rise = (float) Math.min(1, age / 0.9f);
                    disc(pose, buffers, RING, 0.05f + 1.9f * rise, 0.8f - 0.3f * rise, 0, light, fade);
                    disc(pose, buffers, CIRCLE, 0.04f, 0.9f + 0.3f * out, time * 3, c, fade * 0.9f);
                }
                case ZONE -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    float grow = 1 - (float) Math.pow(1 - Mth.clamp(age / 0.5f, 0, 1), 3);
                    disc(pose, buffers, CIRCLE, 0.05f, p.size() * grow, time * 1.2f, c, hold * 0.95f);
                    disc(pose, buffers, RING, 0.07f, p.size() * grow * (1.02f + 0.03f * Mth.sin(time * 0.3f)), 0, light, hold * 0.7f);
                    disc(pose, buffers, GLOW, 0.03f, p.size() * 1.1f * grow, 0, c, hold * 0.35f);
                }
                case MARK -> {
                    if (e == null) break;
                    float h = e.getBbHeight() + 0.75f;
                    float pulse = 0.8f + 0.2f * Mth.sin(time * 0.35f);
                    pose.pushPose();
                    pose.translate(0, h, 0);
                    pose.mulPose(camera.rotation());
                    pose.mulPose(Axis.ZP.rotationDegrees(time * 4));
                    sprite(buffers.getBuffer(ModRenderTypes.lightTexture(SIGIL)), pose.last().pose(), 0.45f * pulse, c, hold);
                    pose.popPose();
                    disc(pose, buffers, RING, 0.05f, Math.max(0.6f, e.getBbWidth()) * (1.1f + 0.1f * pulse), -time * 3, c, hold * 0.8f);
                }
                case SHOT -> {
                    Vec3 from = (e != null ? e.getEyePosition(partial).add(0, -0.2, 0) : fx.origin).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    float k = Mth.clamp(age / 0.08f, 0, 1);
                    Vec3 head = from.lerp(to, k);
                    streak(pose, buffers, camera, at, from, head, 0.28f * fade, c, fade * 1.2f);
                    streak(pose, buffers, camera, at, from, head, 0.1f, 0xFFFFFF, fade);
                    dot(pose, buffers, camera, (float) head.x, (float) head.y, (float) head.z, 0.45f, light, fade);
                    if (!self) {
                        pose.pushPose();
                        pose.translate(from.x, from.y, from.z);
                        Vec3 d = to.subtract(from).normalize();
                        pose.mulPose(Axis.YP.rotation((float) Math.atan2(d.x, d.z)));
                        pose.mulPose(Axis.XP.rotation((float) -Math.asin(d.y)));
                        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose.last().pose(), 0.3f + 0.9f * out, light, fade);
                        pose.popPose();
                    }
                }
                case WAVE -> {
                    Vec3 a = fx.origin.subtract(at);
                    Vec3 b = fx.target().subtract(at);
                    float k = Mth.clamp(age / 0.3f, 0, 1);
                    Vec3 head = a.lerp(b, k);
                    Vec3 up = new Vec3(0, 0.12, 0);
                    Vec3 dir = b.subtract(a).normalize();
                    Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(p.size() * 0.8);
                    streak(pose, buffers, camera, at, a.add(up).add(side), head.add(up).add(side), 0.5f, c, fade);
                    streak(pose, buffers, camera, at, a.add(up).subtract(side), head.add(up).subtract(side), 0.5f, c, fade);
                    if (p.duration() != 2) streak(pose, buffers, camera, at, a.add(up), head.add(up), p.size() * 1.6f, light, fade * 0.8f);
                    dot(pose, buffers, camera, (float) head.x, (float) head.y + 0.6f, (float) head.z, p.size() * 1.3f, light, fade);
                    RandomSource r = RandomSource.create(fx.seed);
                    int n = 8;
                    for (int i = 0; i < n && p.duration() != 2; i++) {
                        float along = (i + 1f) / n;
                        Vec3 at2 = a.lerp(b, along).add(side.scale((r.nextDouble() - 0.5) * 1.4));
                        float rise = Mth.clamp((age - along * 0.3f) / 0.12f, 0, 1);
                        if (rise <= 0) continue;
                        if (p.duration() == 1) {
                            // rock spikes: tall thin streaks leaning a little
                            Vec3 tip = at2.add((r.nextDouble() - 0.5) * 0.5, 2.1 * rise, (r.nextDouble() - 0.5) * 0.5);
                            streak(pose, buffers, camera, at, at2, tip, 0.55f, c, fade * 1.2f);
                            streak(pose, buffers, camera, at, at2, tip, 0.18f, 0xFFFFFF, fade);
                        } else {
                            float h = (float) (1.0 + r.nextDouble() * 0.9);
                            dot(pose, buffers, camera, (float) at2.x, (float) at2.y + 0.5f * h, (float) at2.z, 0.9f * h * rise, c, fade * 0.8f);
                            dot(pose, buffers, camera, (float) at2.x, (float) at2.y + 0.4f, (float) at2.z, 0.45f, 0xFFF0C0, fade * 0.8f);
                        }
                    }
                }
                case SPIN -> {
                    float spin = age * 1100;
                    float a = Mth.clamp(age / 0.1f, 0, 1) * Mth.clamp((life - age) / 0.25f, 0, 1);
                    if (!self) {
                        for (int i = 0; i < 3; i++) {
                            slash(pose, buffers, 0, spin + i * 120, 0.8f + i * 0.3f, p.size() * (0.85f + 0.08f * i), i == 1 ? light : c, a * 1.1f);
                            slash(pose, buffers, 0, spin + i * 120 - 16, 0.8f + i * 0.3f, p.size() * 0.8f, 0xFFFFFF, a * 0.6f);
                        }
                    }
                    disc(pose, buffers, RING, 0.06f, p.size() * (0.9f + 0.06f * Mth.sin(time)), -spin * 0.3f, c, a * 0.8f);
                    disc(pose, buffers, SIGIL, 0.05f, p.size() * 0.6f, spin * 0.4f, light, a * 0.6f);
                }
                case FAN -> {
                    Vec3 a = fx.origin.subtract(at).add(0, 1.2, 0);
                    Vec3 d = fx.target().subtract(fx.origin);
                    float len = (float) d.length();
                    float base = (float) Math.atan2(d.z, d.x);
                    float k = Mth.clamp(age / 0.13f, 0, 1);
                    for (int i = -3; i <= 3; i++) {
                        float ang = base + i * 0.2f;
                        Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                        Vec3 head = a.add(dir.scale(len * k));
                        Vec3 tail = a.add(dir.scale(len * Math.max(0, k - 0.45f)));
                        streak(pose, buffers, camera, at, tail, head, 0.3f, c, fade * 1.2f);
                        streak(pose, buffers, camera, at, tail, head, 0.1f, 0xFFFFFF, fade);
                        dot(pose, buffers, camera, (float) head.x, (float) head.y, (float) head.z, 0.3f, light, fade);
                    }
                }
                case SPLASH -> {
                    Vec3 cc = fx.target().subtract(at);
                    if (p.duration() == 0 && e != null && age < 0.12f) {
                        Vec3 hand = e.getEyePosition(partial).subtract(at).add(0, -0.3, 0);
                        Vec3 orb = hand.lerp(cc, age / 0.12f);
                        dot(pose, buffers, camera, (float) orb.x, (float) orb.y, (float) orb.z, 0.6f, light, 1f);
                        streak(pose, buffers, camera, at, hand.lerp(cc, Math.max(0, age / 0.12f - 0.4f)), orb, 0.35f, c, 0.9f);
                    }
                    pose.translate(cc.x, cc.y, cc.z);
                    disc(pose, buffers, RING, 0.0f, p.size() * out, 0, light, fade * 1.2f);
                    disc(pose, buffers, RING, 0.3f, p.size() * 0.7f * out, 0, c, fade);
                    disc(pose, buffers, GLOW, -0.1f, p.size() * 0.9f, 0, c, fade * 0.5f);
                    dotTex(pose, buffers, camera, BURST, 0, 0.1f, 0, p.size() * (0.4f + 0.6f * out), light, fade * 1.1f, fx.seed % 360);
                    dot(pose, buffers, camera, 0, 0.1f, 0, p.size() * 0.7f * (1 - t), c, fade * 0.7f);
                }
                case VORTEX -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    float grow = Mth.clamp(age / 0.4f, 0, 1);
                    for (int i = 0; i < 7; i++) {
                        float h = 0.25f + i * 0.7f;
                        float r = p.size() * (0.2f + 0.13f * h) * grow;
                        float wob = 0.25f * Mth.sin(time * 0.5f + i);
                        pose.pushPose();
                        pose.translate(wob * 0.4f * i * 0.3f, 0, wob * 0.3f * i * 0.3f);
                        disc(pose, buffers, RING, h, r, time * (22 - i * 2) + i * 50, i % 2 == 0 ? c : light, hold * (0.85f - i * 0.07f));
                        pose.popPose();
                    }
                    disc(pose, buffers, CIRCLE, 0.05f, p.size() * grow, -time * 6, c, hold * 0.8f);
                    disc(pose, buffers, GLOW, 0.04f, p.size() * 1.1f, 0, c, hold * 0.3f);
                    streak(pose, buffers, camera, fx.target(), new Vec3(0, 0, 0), new Vec3(0, 5, 0), 1.4f, light, hold * 0.35f);
                }
                case PILLAR -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    float warn = p.duration() / 20f;
                    Vec3 base = fx.target();
                    if (age < warn) {
                        float k = age / warn;
                        disc(pose, buffers, RING, 0.06f, p.size() * (1.5f - 0.5f * k), time * 5, c, 0.4f + 0.6f * k);
                        disc(pose, buffers, CIRCLE, 0.05f, p.size() * k, -time * 3, light, 0.7f * k);
                        streak(pose, buffers, camera, base, new Vec3(0, 0, 0), new Vec3(0, 16, 0), 0.12f + 0.1f * k, light, 0.5f * k);
                    } else {
                        float k = Mth.clamp((age - warn) / 0.7f, 0, 1);
                        float o = 1 - (1 - k) * (1 - k);
                        float a = 1 - k;
                        if (p.duration() == 0) {
                            long reroll = fx.seed + (long) (time / 2);
                            zigzag(pose, buffers, camera, base, new Vec3(1.5, 16, -1), new Vec3(0, 0, 0), reroll, 0.5f, c, a * 1.3f);
                            zigzag(pose, buffers, camera, base, new Vec3(1.5, 16, -1), new Vec3(0, 0, 0), reroll, 0.16f, 0xFFFFFF, a);
                        } else {
                            streak(pose, buffers, camera, base, new Vec3(0, -0.2, 0), new Vec3(0, 18, 0), p.size() * 1.5f * (1 - 0.6f * k), c, a * 1.2f);
                            streak(pose, buffers, camera, base, new Vec3(0, -0.2, 0), new Vec3(0, 18, 0), p.size() * 0.6f * (1 - 0.6f * k), 0xFFFFFF, a);
                            disc(pose, buffers, CIRCLE, 0.05f, p.size(), time * 4, light, a);
                        }
                        disc(pose, buffers, RING, 0.08f, p.size() * (0.4f + 1.2f * o), 0, light, a * 1.2f);
                        dotTex(pose, buffers, camera, BURST, 0, 0.5f, 0, p.size() * (0.5f + 0.7f * o), light, a * 1.2f, fx.seed % 360);
                        dot(pose, buffers, camera, 0, 0.6f, 0, p.size() * 1.2f, c, a * 0.6f);
                    }
                }
                case BLOSSOM -> {
                    disc(pose, buffers, SIGIL, 0.06f, p.size() * (0.3f + 0.7f * out), time * 9, c, fade * 1.2f);
                    disc(pose, buffers, RING, 0.5f, p.size() * out, 0, light, fade);
                    disc(pose, buffers, RING, 1.1f, p.size() * 0.8f * out, 0, c, fade * 0.8f);
                    for (int i = 0; i < 10; i++) {
                        float ang = i * Mth.TWO_PI / 10 + age * 3;
                        Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                        Vec3 head = dir.scale(p.size() * out).add(0, 0.9, 0);
                        Vec3 tail = dir.scale(p.size() * Math.max(0, out - 0.3f)).add(0, 0.9, 0);
                        streak(pose, buffers, camera, at, tail, head, 0.3f, i % 2 == 0 ? c : light, fade * 1.1f);
                    }
                    if (!self) dot(pose, buffers, camera, 0, 1f, 0, 1.6f * (1 - t), light, fade * 0.6f);
                }
                case BIND -> {
                    boolean small = p.size() < 3;
                    Vec3 cc = small && e != null ? Vec3.ZERO : fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    Vec3 base = at.add(cc);
                    float grow = Mth.clamp(age / 0.25f, 0, 1);
                    if (small) {
                        disc(pose, buffers, SIGIL, 0.05f, p.size() * grow, -time * 3, c, hold);
                        disc(pose, buffers, RING, 0.06f, p.size() * 1.05f * grow, time * 2, mix(c, 0x000000, 0.3f), hold * 0.9f);
                    }
                    RandomSource r = RandomSource.create(fx.seed);
                    int n = small ? 6 : 16;
                    for (int i = 0; i < n; i++) {
                        double ang = i * Math.PI * 2 / n + (small ? 0 : r.nextDouble());
                        double rad = small ? p.size() * 0.85 : p.size() * (0.25 + 0.7 * r.nextDouble());
                        Vec3 foot = new Vec3(Math.cos(ang) * rad, 0, Math.sin(ang) * rad);
                        // chains lean in over a held creature; brambles stand and sway
                        Vec3 tip = small ? new Vec3(foot.x * 0.15, 1.5 * grow, foot.z * 0.15)
                                : foot.add(0.15 * Mth.sin(time * 0.2f + i), (0.7 + r.nextDouble() * 0.8) * grow, 0.15 * Mth.cos(time * 0.17f + i));
                        streak(pose, buffers, camera, base, foot, tip, small ? 0.16f : 0.3f, c, hold * (small ? 1f : 0.85f));
                        if (small) streak(pose, buffers, camera, base, foot, tip, 0.05f, 0xFFFFFF, hold * 0.6f);
                    }
                }
                case RAIN -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    Vec3 base = fx.target();
                    float lasting = p.duration() / 20f;
                    float a = Mth.clamp(age / 0.2f, 0, 1) * Mth.clamp((lasting + 0.3f - age) / 0.3f, 0, 1);
                    for (int i = 0; i < 22; i++) {
                        RandomSource r = RandomSource.create(fx.seed + i * 31L);
                        float period = 0.28f + r.nextFloat() * 0.2f;
                        float phase = (age + r.nextFloat() * period) / period;
                        long cycle = (long) phase;
                        float k = phase - cycle;
                        RandomSource c2 = RandomSource.create(fx.seed + i * 31L + cycle * 977L);
                        double ang = c2.nextDouble() * Math.PI * 2;
                        double rad = Math.sqrt(c2.nextDouble()) * p.size();
                        Vec3 land = new Vec3(Math.cos(ang) * rad, 0, Math.sin(ang) * rad);
                        Vec3 head = land.add(0.6 * (1 - k), 14 * (1 - k), 0.3 * (1 - k));
                        Vec3 tail = head.add(0.13, 3.0, 0.065);
                        streak(pose, buffers, camera, base, tail, head, 0.16f, c, a * 0.95f);
                        streak(pose, buffers, camera, base, tail, head, 0.05f, 0xFFFFFF, a * 0.8f);
                        if (k > 0.85f) {
                            pose.pushPose();
                            pose.translate(land.x, 0, land.z);
                            disc(pose, buffers, RING, 0.07f, 0.3f + 1.2f * (k - 0.85f) / 0.15f, 0, light, a * (1 - (k - 0.85f) / 0.15f));
                            pose.popPose();
                        }
                    }
                    streak(pose, buffers, camera, base, new Vec3(0, 0, 0), new Vec3(0, 14, 0), p.size() * 1.6f, c, a * 0.12f);
                }
                case ELEMENT -> {
                    if (e == null) break;
                    float h = e.getBbHeight();
                    float rad = Math.max(0.5f, e.getBbWidth() * 0.9f);
                    for (int i = 0; i < 2; i++) {
                        float ang = time * 0.22f + i * Mth.PI;
                        float y = h * (0.5f + 0.3f * Mth.sin(time * 0.1f + i * 2));
                        dot(pose, buffers, camera, Mth.cos(ang) * rad, y, Mth.sin(ang) * rad, 0.2f, light, hold);
                        dot(pose, buffers, camera, Mth.cos(ang) * rad, y, Mth.sin(ang) * rad, 0.42f, c, hold * 0.6f);
                    }
                    disc(pose, buffers, RING, 0.05f, rad * 1.1f, time * 4, c, hold * 0.55f);
                }
                case REACTION -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    dotTex(pose, buffers, camera, BURST, 0, 0, 0, p.size() * (0.5f + 1.1f * out), light, fade * 1.3f, fx.seed % 360);
                    dotTex(pose, buffers, camera, RING, 0, 0, 0, p.size() * 1.3f * out, c, fade * 1.2f, 0);
                    dotTex(pose, buffers, camera, RING, 0, 0, 0, p.size() * 0.8f * out, 0xFFFFFF, fade * 0.8f, 0);
                    dot(pose, buffers, camera, 0, 0, 0, p.size() * (1.4f - t), c, fade * 0.8f);
                    disc(pose, buffers, RING, (float) -cc.y + 0.06f, p.size() * 1.5f * out, 0, light, fade);
                }
                case CLEAVE -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    Vec3 base = fx.target();
                    float fall = Math.max(0.05f, p.duration() / 20f);
                    float len = p.size() * 1.7f;
                    if (age < fall) {
                        float k = age / fall;
                        float h = 16 * (1 - k * k);
                        streak(pose, buffers, camera, base, new Vec3(0, h, 0), new Vec3(0, h + len, 0), p.size() * 0.55f, c, 0.4f + 0.6f * k);
                        streak(pose, buffers, camera, base, new Vec3(0, h, 0), new Vec3(0, h + len, 0), p.size() * 0.2f, 0xFFFFFF, 0.5f + 0.5f * k);
                        streak(pose, buffers, camera, base, new Vec3(0, h + len, 0), new Vec3(0, h + len + 7, 0), p.size() * 0.3f, c, 0.5f * k);
                        disc(pose, buffers, RING, 0.06f, p.size() * (1.6f - 0.8f * k), time * 6, c, 0.3f + 0.7f * k);
                        disc(pose, buffers, SIGIL, 0.05f, p.size() * 0.7f * k, -time * 4, light, 0.8f * k);
                    } else {
                        float k = Mth.clamp((age - fall) / 0.9f, 0, 1);
                        float o = 1 - (1 - k) * (1 - k) * (1 - k);
                        float a = 1 - k;
                        // the blade stands in the ground and fades, cross-guard near the top
                        streak(pose, buffers, camera, base, new Vec3(0, -0.3, 0), new Vec3(0, len, 0), p.size() * 0.55f * (1 - 0.5f * k), c, a * 1.2f);
                        streak(pose, buffers, camera, base, new Vec3(0, -0.3, 0), new Vec3(0, len, 0), p.size() * 0.2f * (1 - 0.5f * k), 0xFFFFFF, a);
                        Vec3 side = new Vec3(Mth.cos(fx.seed % 7), 0, Mth.sin(fx.seed % 7)).scale(p.size() * 0.45f);
                        streak(pose, buffers, camera, base, side.add(0, len * 0.78f, 0), side.scale(-1).add(0, len * 0.78f, 0), p.size() * 0.22f, light, a);
                        disc(pose, buffers, RING, 0.08f, p.size() * (0.5f + 1.3f * o), 0, light, a * 1.2f);
                        disc(pose, buffers, RING, 0.5f, p.size() * 1.1f * o, 0, c, a * 0.8f);
                        disc(pose, buffers, SIGIL, 0.05f, p.size() * 0.9f, time * 2, c, a);
                        dotTex(pose, buffers, camera, BURST, 0, 0.6f, 0, p.size() * (0.6f + 0.9f * o), light, a * 1.2f, fx.seed % 360);
                    }
                }
                case BANNER -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    Vec3 base = fx.target();
                    float rise = 1 - (float) Math.pow(1 - Mth.clamp(age / 0.35f, 0, 1), 3);
                    float top = 4.6f * rise;
                    streak(pose, buffers, camera, base, new Vec3(0, 0, 0), new Vec3(0, top, 0), 0.22f, light, hold);
                    streak(pose, buffers, camera, base, new Vec3(0, 0, 0), new Vec3(0, top, 0), 0.08f, 0xFFFFFF, hold);
                    dot(pose, buffers, camera, 0, top + 0.15f, 0, 0.45f, light, hold);
                    // the cloth: rows of light that ripple away from the pole
                    for (int row = 0; row < 5; row++) {
                        float y = top - 0.25f - row * 0.38f;
                        if (y < 1) break;
                        Vec3 prev = new Vec3(0, y, 0);
                        for (int seg = 1; seg <= 4; seg++) {
                            float wave = 0.22f * seg / 4f * Mth.sin(time * 0.35f - seg * 0.9f + row * 0.4f);
                            Vec3 next = new Vec3(seg * 0.42f, y + wave * 0.5f - (row == 4 ? 0.12f * Math.abs(seg - 2.5f) : 0), wave);
                            streak(pose, buffers, camera, base, prev, next, 0.42f, row % 2 == 0 ? c : mix(c, 0xFFFFFF, 0.25f), hold * 0.95f);
                            prev = next;
                        }
                    }
                    disc(pose, buffers, CIRCLE, 0.05f, p.size() * rise, time * 0.8f, c, hold * 0.8f);
                    disc(pose, buffers, RING, 0.07f, p.size() * rise, 0, light, hold * 0.7f);
                    float beat = (age % 1.2f) / 1.2f;
                    disc(pose, buffers, RING, 0.08f, p.size() * beat, 0, light, hold * (1 - beat) * 0.9f);
                    disc(pose, buffers, GLOW, 0.03f, p.size() * 1.05f, 0, c, hold * 0.3f);
                }
                case BLADES -> {
                    int n = 6;
                    Vec3 here = at;
                    // their owner sees them faintly in first person: they pass right in front of the eyes
                    float seen = self ? hold * 0.3f : hold;
                    for (int i = 0; i < n; i++) {
                        float ang = time * 0.42f + i * Mth.TWO_PI / n;
                        float bob = 0.2f * Mth.sin(time * 0.2f + i * 1.3f);
                        Vec3 mid = new Vec3(Mth.cos(ang) * p.size(), 1.05f + bob, Mth.sin(ang) * p.size());
                        // each blade points along its path, tip a little down
                        Vec3 dir = new Vec3(-Mth.sin(ang), -0.35f, Mth.cos(ang)).normalize().scale(0.75f);
                        streak(pose, buffers, camera, here, mid.subtract(dir), mid.add(dir), 0.3f, c, seen * 1.1f);
                        streak(pose, buffers, camera, here, mid.subtract(dir), mid.add(dir), 0.1f, 0xFFFFFF, seen);
                        Vec3 trail = new Vec3(Mth.cos(ang - 0.5f) * p.size(), 1.05f + bob, Mth.sin(ang - 0.5f) * p.size());
                        streak(pose, buffers, camera, here, trail, mid.subtract(dir), 0.16f, c, seen * 0.4f);
                    }
                    disc(pose, buffers, RING, 0.06f, p.size() * 1.05f, -time * 6, c, hold * 0.6f);
                    if (!self) disc(pose, buffers, SIGIL, 0.05f, p.size() * 0.6f, time * 5, light, hold * 0.45f);
                }
                case CRACK -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    Vec3 base = fx.target();
                    float grow = Mth.clamp(age / 0.18f, 0, 1);
                    float glow = age < 0.5f ? 1 : Mth.clamp(1 - (age - 0.5f) / (life - 0.5f), 0, 1);
                    RandomSource r = RandomSource.create(fx.seed);
                    int arms = 7;
                    for (int i = 0; i < arms; i++) {
                        double ang = i * Math.PI * 2 / arms + r.nextDouble() * 0.5;
                        int segs = 5;
                        Vec3 prev = new Vec3(0, 0.09, 0);
                        for (int sgm = 1; sgm <= segs; sgm++) {
                            ang += (r.nextDouble() - 0.5) * 0.9;
                            double step = p.size() / segs * (0.8 + r.nextDouble() * 0.4);
                            Vec3 next = prev.add(Math.cos(ang) * step, 0, Math.sin(ang) * step);
                            if (sgm / (float) segs <= grow + 0.01f) {
                                float thin = 1 - 0.6f * sgm / segs;
                                streak(pose, buffers, camera, base, prev, next, 0.5f * thin, c, glow * 1.2f);
                                streak(pose, buffers, camera, base, prev, next, 0.16f * thin, 0xFFFFFF, glow);
                                // a branch now and then
                                if (r.nextFloat() < 0.4f) {
                                    double b2 = ang + (r.nextBoolean() ? 0.9 : -0.9);
                                    streak(pose, buffers, camera, base, next, next.add(Math.cos(b2) * step * 0.7, 0, Math.sin(b2) * step * 0.7), 0.28f * thin, c, glow);
                                }
                            } else {
                                r.nextFloat();
                            }
                            prev = next;
                        }
                    }
                    disc(pose, buffers, GLOW, 0.04f, p.size() * 0.9f, 0, c, glow * 0.5f);
                    if (age < 0.5f) disc(pose, buffers, RING, 0.08f, p.size() * 1.1f * (age / 0.5f), 0, light, 1 - age / 0.5f);
                }
                case BEAM -> {
                    // from the caster's right hand, so the beam is not hidden behind them or seen dead end-on
                    Vec3 hand = fx.origin;
                    if (e != null) {
                        Vec3 view = e.getViewVector(partial);
                        Vec3 right = view.cross(new Vec3(0, 1, 0));
                        if (right.lengthSqr() > 1.0e-4) right = right.normalize().scale(0.45);
                        hand = e.getEyePosition(partial).add(view.scale(0.6)).add(right).add(0, -0.4, 0);
                    }
                    Vec3 from = hand.subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    float pulse = 0.85f + 0.15f * Mth.sin(time * 1.7f);
                    float grow = Mth.clamp(age / 0.12f, 0, 1);
                    streak(pose, buffers, camera, at, from, to, p.size() * 2.4f * pulse * grow, c, hold * 0.7f);
                    streak(pose, buffers, camera, at, from, to, p.size() * pulse * grow, light, hold * 1.1f);
                    streak(pose, buffers, camera, at, from, to, p.size() * 0.4f * grow, 0xFFFFFF, hold);
                    dotTex(pose, buffers, camera, BURST, (float) to.x, (float) to.y, (float) to.z, 0.8f + p.size() * 1.5f * pulse, light, hold, time * 9);
                    dot(pose, buffers, camera, (float) from.x, (float) from.y, (float) from.z, p.size() * 0.9f, c, hold);
                }
                case ORB -> {
                    Vec3 cc = fx.target().subtract(at);
                    float pulse = 1 + 0.1f * Mth.sin(time * 0.9f);
                    float grow = Mth.clamp(age / 0.2f, 0, 1);
                    dot(pose, buffers, camera, (float) cc.x, (float) cc.y, (float) cc.z, p.size() * 1.7f * pulse * grow, c, hold * 0.6f);
                    dot(pose, buffers, camera, (float) cc.x, (float) cc.y, (float) cc.z, p.size() * pulse * grow, light, hold);
                    dot(pose, buffers, camera, (float) cc.x, (float) cc.y, (float) cc.z, p.size() * 0.45f * grow, 0xFFFFFF, hold);
                    dotTex(pose, buffers, camera, BURST, (float) cc.x, (float) cc.y, (float) cc.z, p.size() * 1.3f * grow, c, hold * 0.7f, time * 6);
                }
                case THUNDER -> {
                    Vec3 cc = fx.target().subtract(at);
                    Vec3 sky = cc.add(fx.seed % 7 - 3, 26, fx.seed / 7 % 7 - 3);
                    float flick = age < 0.12f ? 1 : fade;
                    zigzag(pose, buffers, camera, at, sky, cc, fx.seed + (long) (time / 2), 0.9f, c, flick * 1.3f);
                    zigzag(pose, buffers, camera, at, sky, cc, fx.seed + (long) (time / 2), 0.35f, 0xFFFFFF, flick);
                    pose.translate(cc.x, cc.y, cc.z);
                    disc(pose, buffers, RING, 0.07f, p.size() * out, 0, light, fade * 1.2f);
                    disc(pose, buffers, GLOW, 0.05f, p.size(), 0, c, fade * 0.8f);
                    dotTex(pose, buffers, camera, BURST, 0, 0.4f, 0, 1f + 2f * out, light, fade, fx.seed % 360);
                }
                case SHIELD -> {
                    float yaw = e != null ? e.getViewYRot(partial) : fx.yaw;
                    float pop = 1 - (1 - Mth.clamp(age / 0.1f, 0, 1)) * (1 - Mth.clamp(age / 0.1f, 0, 1));
                    // up at once, so that even a half-second shield is seen; a very short one is a bash, thrust forward
                    float up = Mth.clamp(age / 0.1f, 0, 1) * Mth.clamp((life - age) / Math.min(0.5f, life * 0.5f), 0, 1);
                    boolean bash = p.duration() < 10;
                    // its owner looks through it: in first person only a faint rim is left, so the fight stays in view
                    if (self) up *= 0.3f;
                    Vec3 front = Vec3.directionFromRotation(0, yaw).scale(bash ? 0.95 + 1.1 * out : 0.95);
                    pose.pushPose();
                    pose.translate(front.x, 1.0, front.z);
                    pose.mulPose(Axis.YP.rotationDegrees(-yaw));
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    if (!self) disc(pose, buffers, GLOW, 0, p.size() * 0.95f * pop, 0, c, up * 0.55f);
                    disc(pose, buffers, RING, 0.01f, p.size() * pop, 0, light, up * 1.1f);
                    disc(pose, buffers, RING, 0.02f, p.size() * 0.62f * pop, 0, c, up * 0.9f);
                    if (!self) disc(pose, buffers, BURST, 0.03f, p.size() * 0.5f * pop, 45, light, up * (0.5f + 0.2f * Mth.sin(time * 0.4f)));
                    pose.popPose();
                }
                case HAMMER -> {
                    // thrown from where the caster stood, tumbling head over haft; a big one is lobbed high and comes down hard
                    Vec3 look = Vec3.directionFromRotation(0, fx.yaw);
                    Vec3 from = fx.origin.add(-look.z * 0.4, 1.35, look.x * 0.4).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    float flight = Math.max(0.1f, p.duration() / 20f);
                    float k = Mth.clamp(age / flight, 0, 1);
                    float big = p.size();
                    float arc = 0.3f + 1.1f * big;
                    Vec3 pos = from.lerp(to, k).add(0, Mth.sin(k * Mth.PI) * arc, 0);
                    // it tumbles in the plane of the screen: thrown straight away from the viewer, a true tumble would be
                    // seen edge-on, as a line. It ends head down, on the mark.
                    Vector3f cl = camera.getLeftVector(), cu = camera.getUpVector();
                    Vec3 left = new Vec3(cl.x(), cl.y(), cl.z()), upward = new Vec3(cu.x(), cu.y(), cu.z());
                    float spin = -Mth.HALF_PI + (1 - k) * Mth.TWO_PI * (big >= 2.5f ? 1 : 2);
                    Vec3 axis = left.scale(Mth.cos(spin)).add(upward.scale(Mth.sin(spin)));
                    Vec3 cross = left.scale(-Mth.sin(spin)).add(upward.scale(Mth.cos(spin)));
                    Vec3 butt = pos.subtract(axis.scale(0.6 * big)), head = pos.add(axis.scale(0.6 * big));
                    float a = age <= flight ? 1 : Mth.clamp(1 - (age - flight) / 0.05f, 0, 1);
                    float kb = Math.max(0, k - 0.15f);
                    streak(pose, buffers, camera, at, from.lerp(to, kb).add(0, Mth.sin(kb * Mth.PI) * arc, 0), pos, 0.25f * big, c, a * 0.45f);
                    streak(pose, buffers, camera, at, butt, head, 0.2f * big, light, a);
                    streak(pose, buffers, camera, at, head.subtract(cross.scale(0.45 * big)), head.add(cross.scale(0.45 * big)), 0.6f * big, c, a * 1.2f);
                    streak(pose, buffers, camera, at, head.subtract(cross.scale(0.38 * big)), head.add(cross.scale(0.38 * big)), 0.3f * big, 0xFFFFFF, a);
                    dot(pose, buffers, camera, (float) head.x, (float) head.y, (float) head.z, 0.8f * big, c, a * 0.6f);
                }
                case DISCUS -> {
                    // edge-on a flat disc would vanish, so it is drawn facing the camera and spun
                    Vec3 from = fx.origin.add(0, e != null ? e.getBbHeight() * 0.6 : 1, 0).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    float flight = Math.max(0.1f, p.duration() / 20f);
                    float k = Mth.clamp(age / flight, 0, 1);
                    Vec3 pos = from.lerp(to, k);
                    float a = age <= flight ? 1 : Mth.clamp(1 - (age - flight) / 0.05f, 0, 1);
                    streak(pose, buffers, camera, at, from.lerp(to, Math.max(0, k - 0.35f)), pos, 0.3f * p.size(), c, a * 0.5f);
                    dot(pose, buffers, camera, (float) pos.x, (float) pos.y, (float) pos.z, 0.6f * p.size(), c, a * 0.5f);
                    dotTex(pose, buffers, camera, RING, (float) pos.x, (float) pos.y, (float) pos.z, 0.55f * p.size(), light, a * 1.2f, 0);
                    dotTex(pose, buffers, camera, RING, (float) pos.x, (float) pos.y, (float) pos.z, 0.32f * p.size(), c, a, 0);
                    dotTex(pose, buffers, camera, BURST, (float) pos.x, (float) pos.y, (float) pos.z, 0.34f * p.size(), 0xFFFFFF, a * 0.8f, time * 30);
                }
                case CHAIN -> {
                    Vec3 from = (e != null ? e.getPosition(partial).add(0, e.getBbHeight() * 0.6, 0) : fx.origin).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    double len = to.subtract(from).length();
                    int links = Math.max(2, (int) (len / 0.55));
                    float reach = Mth.clamp(age / 0.15f, 0, 1);
                    streak(pose, buffers, camera, at, from, from.lerp(to, reach), 0.07f * p.size(), c, hold * 0.8f);
                    for (int i = 0; i <= links * reach; i++) {
                        double k = i / (double) links;
                        // the chain hangs a little in the middle
                        Vec3 g = from.lerp(to, k).add(0, -Math.sin(k * Math.PI) * Math.min(0.6, len * 0.05), 0);
                        dot(pose, buffers, camera, (float) g.x, (float) g.y, (float) g.z, p.size() * (i % 2 == 0 ? 0.16f : 0.1f), i % 2 == 0 ? light : c, hold);
                    }
                    dotTex(pose, buffers, camera, BURST, (float) to.x, (float) to.y, (float) to.z, p.size() * 0.6f, light, hold * reach, time * 5);
                }
                case GEYSER -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    Vec3 base = fx.target();
                    float up = 1 - (1 - Mth.clamp(age / 0.22f, 0, 1)) * (1 - Mth.clamp(age / 0.22f, 0, 1));
                    float a = age < 0.4f ? 1 : Mth.clamp(1 - (age - 0.4f) / (life - 0.4f), 0, 1);
                    RandomSource r = RandomSource.create(fx.seed);
                    streak(pose, buffers, camera, base, new Vec3(0, -0.2, 0), new Vec3(0, p.size() * up, 0), 1.3f * (1 - 0.4f * t), c, a * 1.2f);
                    streak(pose, buffers, camera, base, new Vec3(0, -0.2, 0), new Vec3(0, p.size() * up * 0.9f, 0), 0.45f, 0xFFFFFF, a * 0.9f);
                    for (int i = 0; i < 9; i++) {
                        double ang = i * Math.PI * 2 / 9 + r.nextDouble();
                        double lean = 0.5 + r.nextDouble() * 0.9;
                        float h = p.size() * (0.55f + r.nextFloat() * 0.45f) * up;
                        // each jet arcs outwards and droops as it ages
                        Vec3 tip = new Vec3(Math.cos(ang) * lean * (0.4 + t), h * (1 - 0.5f * t * t), Math.sin(ang) * lean * (0.4 + t));
                        streak(pose, buffers, camera, base, new Vec3(0, 0.1, 0), tip, 0.4f, i % 2 == 0 ? c : light, a);
                        dot(pose, buffers, camera, (float) tip.x, (float) tip.y, (float) tip.z, 0.3f, light, a);
                    }
                    disc(pose, buffers, RING, 0.07f, 0.5f + p.size() * 0.55f * out, 0, light, fade * 1.1f);
                    disc(pose, buffers, GLOW, 0.04f, p.size() * 0.5f, 0, c, a * 0.7f);
                }
                case QUAKE -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    Vec3 base = fx.target();
                    for (int i = 0; i < 3; i++) {
                        float k = ((age + i * 0.3f) % 0.9f) / 0.9f;
                        disc(pose, buffers, RING, 0.06f + 0.01f * i, p.size() * k, 0, i == 1 ? light : c, hold * (1 - k) * 1.1f);
                    }
                    // short cracks that flicker in new places every few ticks
                    RandomSource r = RandomSource.create(fx.seed + (long) (time / 4));
                    for (int i = 0; i < 8; i++) {
                        double ang = r.nextDouble() * Math.PI * 2;
                        double rad = Math.sqrt(r.nextDouble()) * p.size() * 0.9;
                        Vec3 a1 = new Vec3(Math.cos(ang) * rad, 0.08, Math.sin(ang) * rad);
                        double a2 = r.nextDouble() * Math.PI * 2;
                        Vec3 b1 = a1.add(Math.cos(a2) * 1.3, 0, Math.sin(a2) * 1.3);
                        streak(pose, buffers, camera, base, a1, b1, 0.3f, c, hold * 0.9f);
                        streak(pose, buffers, camera, base, a1, b1, 0.1f, 0xFFFFFF, hold * 0.6f);
                    }
                    disc(pose, buffers, GLOW, 0.03f, p.size(), 0, c, hold * 0.35f);
                }
                case HALO -> {
                    if (e == null || self) break;
                    float r = 0.34f * p.size();
                    float h = e.getBbHeight() + 0.3f + 0.03f * Mth.sin(time * 0.15f);
                    float appear = 1 - (1 - Mth.clamp(age / 0.25f, 0, 1)) * (1 - Mth.clamp(age / 0.25f, 0, 1));
                    pose.pushPose();
                    pose.translate(0, h, 0);
                    pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw(e, partial)));
                    // tipped back a little, so that it is a ring from every side and never just a line
                    pose.mulPose(Axis.XP.rotationDegrees(-14));
                    disc(pose, buffers, RING, 0, r * appear, time * 2, light, hold * 1.1f);
                    disc(pose, buffers, RING, 0.01f, r * 0.8f * appear, -time * 2, 0xFFFFFF, hold * 0.7f);
                    disc(pose, buffers, GLOW, -0.01f, r * 1.5f, 0, c, hold * 0.4f);
                    pose.popPose();
                    dot(pose, buffers, camera, 0, h, 0, r * 1.7f, c, hold * 0.25f);
                }
                case WINGS -> {
                    // behind their owner's back, so in first person there is nothing of them to see
                    if (e == null || self) break;
                    Vec3 fwd = Vec3.directionFromRotation(0, bodyYaw(e, partial));
                    Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
                    float span = p.size();
                    float open = 1 - (float) Math.pow(1 - Mth.clamp(age / 0.4f, 0, 1), 3);
                    float flap = 0.16f * Mth.sin(time * 0.22f);
                    float tall = e.getBbHeight();
                    for (int w = -1; w <= 1; w += 2) {
                        Vec3 outward = side.scale(w);
                        Vec3 shoulder = fwd.scale(-0.28).add(outward.scale(0.12)).add(0, tall * 0.74, 0);
                        // the arm of the wing goes up and out, then out; the feathers hang from it
                        Vec3 elbow = shoulder.add(wingDir(outward, fwd, 0.95f + flap).scale(0.7 * span * open));
                        Vec3 wrist = elbow.add(wingDir(outward, fwd, 0.2f + flap * 0.6f).scale(0.75 * span * open));
                        streak(pose, buffers, camera, at, shoulder, elbow, 0.2f * span, light, hold);
                        streak(pose, buffers, camera, at, elbow, wrist, 0.16f * span, light, hold);
                        int feathers = 7;
                        for (int i = 0; i < feathers; i++) {
                            float u = (i + 1f) / feathers;
                            Vec3 root = u < 0.45f ? shoulder.lerp(elbow, u / 0.45f) : elbow.lerp(wrist, (u - 0.45f) / 0.55f);
                            // the inner feathers hang down, the outer ones reach out
                            Vec3 tip = root.add(wingDir(outward, fwd, -1.35f + u + flap * 0.5f).scale(span * (0.5f + 0.6f * u) * open));
                            streak(pose, buffers, camera, at, root, tip, 0.34f * span, c, hold * 0.8f);
                            streak(pose, buffers, camera, at, root, tip, 0.11f * span, 0xFFFFFF, hold * 0.75f);
                        }
                    }
                    dot(pose, buffers, camera, (float) (-fwd.x * 0.28), tall * 0.74f, (float) (-fwd.z * 0.28), 0.55f * span, c, hold * 0.35f);
                }
                case BUBBLE -> {
                    if (e == null) break;
                    float r = p.size() * (1 + 0.035f * Mth.sin(time * 0.2f)) * (0.6f + 0.4f * Mth.clamp(age / 0.2f, 0, 1));
                    float mid = e.getBbHeight() * 0.5f;
                    if (self) {
                        // from the inside only its rim on the ground is seen
                        disc(pose, buffers, RING, 0.05f, r, time, light, hold * 0.45f);
                        break;
                    }
                    dotTex(pose, buffers, camera, RING, 0, mid, 0, r, light, hold * 0.95f, 0);
                    dotTex(pose, buffers, camera, RING, 0, mid, 0, r * 0.93f, c, hold * 0.5f, 0);
                    dot(pose, buffers, camera, 0, mid, 0, r * 1.05f, c, hold * 0.16f);
                    // the glints that make it a bubble: one up on the left, a small one down on the right
                    Vector3f cl = camera.getLeftVector(), cu = camera.getUpVector();
                    float gx = (cl.x() * 0.42f + cu.x() * 0.48f) * r, gy = (cl.y() * 0.42f + cu.y() * 0.48f) * r, gz = (cl.z() * 0.42f + cu.z() * 0.48f) * r;
                    dot(pose, buffers, camera, gx, mid + gy, gz, r * 0.16f, 0xFFFFFF, hold * 0.85f);
                    dot(pose, buffers, camera, -gx * 0.9f, mid - gy * 0.9f, -gz * 0.9f, r * 0.07f, 0xFFFFFF, hold * 0.6f);
                }
                case STREAM -> {
                    if (e == null) break;
                    Vec3 src = fx.target().subtract(at);
                    Vec3 dst = new Vec3(0, e.getBbHeight() * 0.6, 0);
                    double dist = dst.subtract(src).length();
                    if (dist < 0.3) break;
                    float a = Mth.clamp(age / 0.08f, 0, 1) * Mth.clamp((life - age) / 0.15f, 0, 1);
                    double arch = Math.min(0.9, dist * 0.14);
                    int n = Mth.clamp((int) (dist * 1.2), 4, 9);
                    for (int i = 0; i < n; i++) {
                        float k = (i / (float) n + age * 1.6f) % 1f;
                        float k0 = Math.max(0, k - 0.09f);
                        Vec3 pos = src.lerp(dst, k).add(0, Math.sin(k * Math.PI) * arch, 0);
                        Vec3 tail = src.lerp(dst, k0).add(0, Math.sin(k0 * Math.PI) * arch, 0);
                        float swell = 0.6f + 0.4f * Mth.sin(k * Mth.PI);
                        streak(pose, buffers, camera, at, tail, pos, 0.2f * p.size() * swell, c, a * 0.8f);
                        dot(pose, buffers, camera, (float) pos.x, (float) pos.y, (float) pos.z, 0.2f * p.size() * swell, light, a);
                    }
                    dot(pose, buffers, camera, (float) dst.x, (float) dst.y, (float) dst.z, 0.5f * p.size(), c, a * 0.45f);
                }
                case ORBIT -> {
                    if (e == null) break;
                    int n = Math.max(1, Math.round(p.size()));
                    float rad = Math.max(0.7f, e.getBbWidth() / 2 + 0.45f);
                    // their owner sees them faintly in first person: they pass right in front of the eyes
                    float seen = self ? hold * 0.5f : hold;
                    float phase = (c & 0xFF) * 0.07f;
                    for (int i = 0; i < n; i++) {
                        float ang = time * 0.16f + phase + i * Mth.TWO_PI / n;
                        float y = e.getBbHeight() * 0.62f + 0.12f * Mth.sin(time * 0.13f + i * 1.9f);
                        Vec3 pos = new Vec3(Mth.cos(ang) * rad, y, Mth.sin(ang) * rad);
                        Vec3 tail = new Vec3(Mth.cos(ang - 0.55f) * rad, y, Mth.sin(ang - 0.55f) * rad);
                        streak(pose, buffers, camera, at, tail, pos, 0.12f, c, seen * 0.55f);
                        dot(pose, buffers, camera, (float) pos.x, (float) pos.y, (float) pos.z, 0.28f, c, seen * 0.6f);
                        dot(pose, buffers, camera, (float) pos.x, (float) pos.y, (float) pos.z, 0.13f, light, seen);
                    }
                }
                case VEIL -> {
                    disc(pose, buffers, SIGIL, 0.04f, 1.1f, -time * 2, c, hold * 0.5f);
                    if (age < 0.6f) disc(pose, buffers, RING, 0.06f, 0.4f + 2.2f * (age / 0.6f), 0, c, 1 - age / 0.6f);
                }
            }
            pose.popPose();
        }
        for (ResourceLocation tex : ALL) buffers.endBatch(ModRenderTypes.lightTexture(tex));
    }

    // ---- drawing helpers (each fetches its buffer right before drawing; see the gates' "Not building!" note) ----

    private static int mix(int a, int b, float t) {
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
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camera.rotation());
        if (roll != 0) pose.mulPose(Axis.ZP.rotationDegrees(roll));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose.last().pose(), half, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    /** The crescent, flat in front of the caster, swept around them and slightly tilted. */
    private static void slash(PoseStack pose, MultiBufferSource buffers, float yaw, float sweep, float height, float radius, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(0, height, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.ZP.rotationDegrees(-18));
        pose.mulPose(Axis.YP.rotationDegrees(sweep));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(SLASH)), pose.last().pose(), radius, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    /**
     * A streak of light from a to b (relative to the current pose origin, which sits at {@code origin} in the
     * world), turned to face the camera.
     */
    private static void streak(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        if (alpha <= 0.01f || width <= 0.005f) return;
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0e-4) return;
        Vec3 mid = origin.add(a.add(b).scale(0.5));
        Vec3 view = camera.getPosition().subtract(mid);
        Vec3 side = dir.cross(view);
        if (side.lengthSqr() < 1.0e-6) side = dir.cross(new Vec3(0, 1, 0));
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

    /** A jagged bolt: the line broken into segments that jump sideways, re-rolled every couple of ticks. */
    private static void zigzag(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 a, Vec3 b, long seed, float width, int rgb, float alpha) {
        RandomSource r = RandomSource.create(seed);
        int n = Math.max(3, (int) (b.subtract(a).length() / 1.6));
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            Vec3 next = a.lerp(b, i / (double) n);
            if (i < n) next = next.add((r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.6);
            streak(pose, buffers, camera, origin, prev, next, width, rgb, alpha);
            prev = next;
        }
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

    public static void clear() {
        ACTIVE.clear();
    }

    private SkillWorldFx() {}
}
