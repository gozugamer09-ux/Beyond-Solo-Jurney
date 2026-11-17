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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
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
        /** Took the place of an older one of its kind: it is simply there, without fading in again. */
        boolean settled;

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

        /** Seconds an arrow is in the air on its way from the shooter to the point. */
        float arrowFlight() {
            return Mth.clamp((float) target().distanceTo(origin) / 60f, 0.08f, 0.6f);
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
                case MIST -> Math.max(1f, lasting);
                case STREAM -> Math.max(0.3f, lasting) + 0.1f;
                case ARROW -> arrowFlight() + lasting + 0.3f;
                case SKYSHOT -> lasting + 0.9f;
                case SEEKER -> Math.max(0.1f, lasting) + 0.25f;
                case SIGHT -> Math.max(0.15f, lasting);
                case GALE -> 0.9f;
                case UPDRAFT -> Math.max(0.5f, lasting);
                case QUIVER, SWARM, WIRE -> Math.max(0.3f, lasting);
                case WINDWALL, STATIC -> Math.max(0.4f, lasting);
                case BEAST -> StarBeasts.life(p.duration() % 10, p.duration() / 10 / 20f);
                case CLAW -> 0.5f;
                case TRAP -> p.duration() > 0 ? lasting : 0.75f;
                case NET -> 0.25f + lasting + 0.2f;
                case ROPE -> Math.max(0.4f, lasting);
                case CLOUD -> Math.max(1f, lasting);
                case BALL -> Math.max(0.1f, lasting) + 0.15f;
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
        if (p.kind() == SkillFxPayload.Kind.SIGHT && p.size() < 0.5f) {
            // one line of sight follows a hunter's gaze at a time, and a negative size only puts it out
            ACTIVE.removeIf(f -> f.p.kind() == SkillFxPayload.Kind.SIGHT && f.p.entityId() == p.entityId() && f.p.size() < 0.5f);
            if (p.size() < -0.5f) return;
        }
        boolean settled = false;
        if (p.kind() == SkillFxPayload.Kind.QUIVER) {
            // one fan of arrows per hunter: a new count replaces the old one, and none takes it away
            settled = ACTIVE.removeIf(f -> f.p.kind() == SkillFxPayload.Kind.QUIVER && f.p.entityId() == p.entityId());
            if (p.size() < 0.5f) return;
        }
        if (p.kind() == SkillFxPayload.Kind.TRAP && p.duration() == 0 || p.kind() == SkillFxPayload.Kind.WIRE && p.size() < 0.5f) {
            // sprung, snapped or taken up: what lay at that spot is gone
            Vec3 spot = new Vec3(p.x(), p.y(), p.z());
            ACTIVE.removeIf(f -> f.p.kind() == p.kind() && f.p.duration() > 0 && f.target().distanceToSqr(spot) < 0.25);
            if (p.size() < 0.05f) return;
        }
        Fx fx = new Fx(p, now(0), origin, yaw, RANDOM.nextLong());
        fx.settled = settled;
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
                // a blade of wind (duration 3) flies at chest height and tears nothing out of the ground
                if (p.duration() == 3) break;
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
            case TRAP -> {
                // iron on iron as the jaws meet
                if (p.duration() == 0) for (int i = 0; i < 6; i++) level.addParticle(ParticleTypes.CRIT, p.x(), p.y() + 0.3, p.z(), rnd(0.3), 0.15 + RANDOM.nextDouble() * 0.2, rnd(0.3));
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
                case ARROW -> {
                    // the moment it strikes
                    float land = fx.arrowFlight();
                    if (age >= land && age < land + 0.051f) {
                        for (int i = 0; i < 4; i++) mc.level.addParticle(ParticleTypes.CRIT, fx.p.x(), fx.p.y(), fx.p.z(), rnd(0.25), rnd(0.25), rnd(0.25));
                    }
                }
                case SKYSHOT -> {
                    // the moment it lands: the ground jumps
                    float land = fx.p.duration() / 20f;
                    if (age >= land && age < land + 0.051f) {
                        BlockState ground = mc.level.getBlockState(BlockPos.containing(fx.p.x(), fx.p.y() - 0.5, fx.p.z()));
                        ParticleOptions debris = ground.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, ground);
                        int n = Math.round(14 * fx.p.size());
                        for (int i = 0; i < n; i++) {
                            double a = RANDOM.nextDouble() * Math.PI * 2;
                            mc.level.addParticle(i % 3 == 0 ? dust : debris, fx.p.x() + Math.cos(a) * 0.4, fx.p.y() + 0.1, fx.p.z() + Math.sin(a) * 0.4,
                                    Math.cos(a) * 0.3, 0.3 + RANDOM.nextDouble() * 0.4, Math.sin(a) * 0.3);
                        }
                        mc.level.addParticle(ParticleTypes.FLASH, fx.p.x(), fx.p.y() + 0.6, fx.p.z(), 0, 0, 0);
                    }
                }
                case BALL -> {
                    if (RANDOM.nextFloat() > 0.4f) break;
                    Vec3 pos = fx.origin.add(0, 1.2, 0).lerp(fx.target(), Mth.clamp(age / Math.max(0.1f, fx.p.duration() / 20f), 0, 1));
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, rnd(0.3), rnd(0.3), rnd(0.3));
                }
                case STATIC -> {
                    if (RANDOM.nextFloat() > 0.12f || (e == mc.player && firstPerson)) break;
                    Vec3 c = e != null ? e.position() : fx.target();
                    mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x + rnd(0.4), c.y + RANDOM.nextDouble() * (e != null ? e.getBbHeight() : 1), c.z + rnd(0.4), rnd(0.1), 0.05, rnd(0.1));
                }
                case UPDRAFT -> {
                    if (RANDOM.nextFloat() > 0.5f) break;
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    mc.level.addParticle(ParticleTypes.CLOUD, fx.origin.x + Math.cos(a) * fx.p.size(), fx.origin.y + 0.1, fx.origin.z + Math.sin(a) * fx.p.size(),
                            -Math.sin(a) * 0.1, 0.25, Math.cos(a) * 0.1);
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
                    if (p.duration() == 3) {
                        // a blade of wind: a crescent flying flat at chest height, its horns swept back, and the cut it leaves
                        Vec3 a0 = fx.origin.subtract(at).add(0, 1.0, 0), b0 = fx.target().subtract(at).add(0, 1.0, 0);
                        float flown = Mth.clamp(age / 0.32f, 0, 1);
                        Vec3 fly = b0.subtract(a0).normalize();
                        Vec3 wide = new Vec3(-fly.z, 0, fly.x);
                        float seen = age < 0.32f ? 1 : Mth.clamp(1 - (age - 0.32f) / 0.3f, 0, 1);
                        float sweep = p.size() * 0.55f;
                        for (int ghost = 0; ghost < 3; ghost++) {
                            Vec3 mid = a0.lerp(b0, Math.max(0, flown - ghost * 0.07f));
                            float ga = seen * (ghost == 0 ? 1f : ghost == 1 ? 0.45f : 0.22f);
                            Vec3 last = null;
                            for (int i = -5; i <= 5; i++) {
                                float u = i / 5f;
                                Vec3 pt = mid.add(wide.scale(u * p.size())).add(fly.scale(-sweep * u * u)).add(0, 0.12 * u, 0);
                                if (last != null) {
                                    float thick = 1 - 0.75f * Math.abs(u - 0.1f);
                                    streak(pose, buffers, camera, at, last, pt, 0.5f * thick + 0.08f, c, ga);
                                    if (ghost == 0) streak(pose, buffers, camera, at, last, pt, 0.16f * thick + 0.03f, 0xFFFFFF, ga);
                                }
                                last = pt;
                            }
                        }
                        Vec3 head = a0.lerp(b0, flown);
                        for (int sgn = -1; sgn <= 1; sgn += 2) {
                            Vec3 horn = head.add(wide.scale(sgn * p.size())).add(fly.scale(-sweep));
                            Vec3 back = a0.lerp(b0, Math.max(0, flown - 0.4f)).add(wide.scale(sgn * p.size())).add(fly.scale(-sweep));
                            streak(pose, buffers, camera, at, back, horn, 0.12f, light, seen * 0.5f);
                        }
                        break;
                    }
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
                    // a faint thread along the whole arch, so the drops read as one stream
                    Vec3 last = src;
                    for (int i = 1; i <= 8; i++) {
                        float u = i / 8f;
                        Vec3 next = src.lerp(dst, u).add(0, Math.sin(u * Math.PI) * arch, 0);
                        streak(pose, buffers, camera, at, last, next, 0.14f * p.size(), c, a * 0.3f);
                        last = next;
                    }
                    int n = Mth.clamp((int) (dist * 1.2), 4, 9);
                    for (int i = 0; i < n; i++) {
                        float k = (i / (float) n + age * 1.6f) % 1f;
                        float k0 = Math.max(0, k - 0.12f);
                        Vec3 pos = src.lerp(dst, k).add(0, Math.sin(k * Math.PI) * arch, 0);
                        Vec3 tail = src.lerp(dst, k0).add(0, Math.sin(k0 * Math.PI) * arch, 0);
                        float swell = 0.6f + 0.4f * Mth.sin(k * Mth.PI);
                        streak(pose, buffers, camera, at, tail, pos, 0.3f * p.size() * swell, c, a * 0.9f);
                        dot(pose, buffers, camera, (float) pos.x, (float) pos.y, (float) pos.z, 0.3f * p.size() * swell, light, a);
                    }
                    dot(pose, buffers, camera, (float) dst.x, (float) dst.y, (float) dst.z, 0.6f * p.size(), c, a * 0.5f);
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
                case MIST -> {
                    // banks of fog that lie low, drift slowly round the middle and breathe; thickest at the centre
                    float r = p.size();
                    int pale = mix(c, 0xFFFFFF, 0.4f);
                    float ox = (float) (at.x - cam.x), oy = (float) (at.y - cam.y), oz = (float) (at.z - cam.z);
                    for (int i = 0; i < 56; i++) {
                        long h = (fx.seed + i) * 0x9E3779B97F4A7C15L;
                        float u = ((h >>> 12) & 0xFFFF) / 65535f, v = ((h >>> 28) & 0xFFFF) / 65535f, w = ((h >>> 44) & 0xFFFF) / 65535f;
                        float dist = (float) Math.sqrt(u) * r * 0.9f;
                        float ang = v * Mth.TWO_PI + time * 0.004f * (i % 2 == 0 ? 1 : -1) * (0.5f + w);
                        float half = (0.7f + 0.6f * w) * (0.88f + 0.12f * Mth.sin(time * 0.045f + i * 1.7f));
                        // it lies low: what stands in it is seen from the chest up
                        float x = Mth.cos(ang) * dist, y = 0.3f + 0.8f * w + 0.07f * Mth.sin(time * 0.05f + i), z = Mth.sin(ang) * dist;
                        // thinner towards its edge and towards its top
                        float thick = (0.13f + 0.09f * (1 - dist / r)) * (1 - 0.45f * w);
                        // it thins around whoever stands in it, so nobody is blinded by fog, least of all their own
                        float away = Mth.sqrt((ox + x) * (ox + x) + (oy + y) * (oy + y) + (oz + z) * (oz + z));
                        float clear = Mth.clamp((away - half * 0.6f) / 4f, 0, 1);
                        dot(pose, buffers, camera, x, y, z, half, i % 3 == 0 ? light : pale, hold * thick * clear);
                    }
                    // where it ends, on the ground
                    disc(pose, buffers, RING, 0.04f, r * (0.97f + 0.03f * Mth.sin(time * 0.06f)), time * 0.2f, pale, hold * 0.22f);
                }
                case ARROW -> {
                    Vec3 from = shotFrom(fx, e).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    Vec3 path = to.subtract(from);
                    double dist = path.length();
                    if (dist < 0.05) break;
                    Vec3 dir = path.scale(1 / dist);
                    float flight = fx.arrowFlight();
                    float scale = p.size();
                    if (age < flight) {
                        double flown = dist * age / flight;
                        Vec3 head = from.add(dir.scale(flown));
                        // the light it leaves behind it
                        streak(pose, buffers, camera, at, from.add(dir.scale(Math.max(0, flown - 5 * scale))), head, 0.18f * scale, c, 0.45f);
                        arrow(pose, buffers, camera, at, head, dir, scale, c, 1f);
                        break;
                    }
                    float since = age - flight;
                    float stuck = p.duration() / 20f;
                    if (since < 0.25f) {
                        // where it struck
                        float k = since / 0.25f;
                        dotTex(pose, buffers, camera, BURST, (float) to.x, (float) to.y, (float) to.z, (0.3f + 0.8f * k) * scale, light, (1 - k) * 1.1f, fx.seed % 360);
                        dot(pose, buffers, camera, (float) to.x, (float) to.y, (float) to.z, 0.5f * scale * (1 - k), c, (1 - k) * 0.7f);
                    }
                    if (stuck <= 0) break;
                    // it stands where it landed and shakes itself still. A shaft that came in flat is shown steeper,
                    // so that it is seen standing in the ground and not lying on it
                    Vec3 level = new Vec3(dir.x, 0, dir.z);
                    Vec3 lean = dir.y > -0.66 ? (level.lengthSqr() < 1.0e-6 ? new Vec3(0, -1, 0) : level.normalize().scale(0.75).add(0, -0.66, 0).normalize()) : dir;
                    float shake = since < 0.5f ? 0.1f * (1 - since / 0.5f) * Mth.sin(since * 70) : 0;
                    Vec3 d2 = lean.add(new Vec3(-lean.z, 0, lean.x).scale(shake)).normalize();
                    float a = Mth.clamp((stuck + 0.3f - since) / 0.3f, 0, 1);
                    arrow(pose, buffers, camera, at, to.add(d2.scale(0.14 * scale)), d2, scale, c, a);
                    if (since < 0.4f) {
                        pose.pushPose();
                        pose.translate(to.x, to.y, to.z);
                        disc(pose, buffers, RING, 0.04f, (0.2f + 0.9f * since / 0.4f) * scale, 0, light, 1 - since / 0.4f);
                        pose.popPose();
                    }
                }
                case SKYSHOT -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    Vec3 base = fx.target();
                    float fall = Math.max(0.05f, p.duration() / 20f);
                    float scale = p.size();
                    // it comes down a little aslant, never quite the same way twice
                    Vec3 sky = new Vec3((Math.floorMod(fx.seed, 5) - 2) * 1.5, 30, (Math.floorMod(fx.seed / 5, 5) - 2) * 1.5);
                    Vec3 dir = sky.scale(-1).normalize();
                    float drop = Math.min(0.2f, fall);
                    if (age < fall) {
                        float k = age / fall;
                        // the ground knows first: a ring closing on the spot, and a thread of light down from the sky
                        disc(pose, buffers, RING, 0.06f, scale * (1.7f - 1.0f * k), time * 5, c, 0.3f + 0.6f * k);
                        streak(pose, buffers, camera, base, Vec3.ZERO, sky, 0.06f + 0.06f * k, light, 0.4f * k);
                        float d = (age - (fall - drop)) / drop;
                        if (d > 0) {
                            Vec3 head = sky.scale(1 - d);
                            Vec3 tail = sky.scale(Math.min(1, 1 - d + 0.4f));
                            streak(pose, buffers, camera, base, tail, head, 0.55f * scale, c, 0.8f);
                            streak(pose, buffers, camera, base, tail, head, 0.18f * scale, 0xFFFFFF, 0.9f);
                            arrow(pose, buffers, camera, base, head, dir, scale * 1.4f, c, 1f);
                        }
                    } else {
                        float k = Mth.clamp((age - fall) / 0.9f, 0, 1);
                        float o = 1 - (1 - k) * (1 - k) * (1 - k);
                        float a = 1 - k;
                        // the arrow stands in the ground, and the light it came down in hangs over it for a moment
                        arrow(pose, buffers, camera, base, dir.scale(0.2 * scale), dir, scale * 1.4f, c, Mth.clamp(a * 1.6f, 0, 1));
                        streak(pose, buffers, camera, base, Vec3.ZERO, sky, 0.7f * scale * (1 - k), c, a * 0.7f);
                        streak(pose, buffers, camera, base, Vec3.ZERO, sky, 0.2f * scale * (1 - k), 0xFFFFFF, a * 0.6f);
                        disc(pose, buffers, RING, 0.08f, scale * (0.5f + 2.1f * o), 0, light, a * 1.2f);
                        disc(pose, buffers, RING, 0.4f, scale * 1.4f * o, 0, c, a * 0.8f);
                        disc(pose, buffers, GLOW, 0.05f, scale * 1.6f, 0, c, a * 0.6f);
                        dotTex(pose, buffers, camera, BURST, 0, 0.5f, 0, scale * (0.6f + 1.2f * o), light, a * 1.2f, fx.seed % 360);
                    }
                }
                case SEEKER -> {
                    Vec3 look = Vec3.directionFromRotation(0, fx.yaw);
                    Vec3 right = new Vec3(-look.z, 0, look.x);
                    float turn = (fx.seed & 1) == 0 ? 1 : -1;
                    float wide = 0.35f + ((fx.seed >>> 3) & 7) / 7f * 0.5f;
                    // loosed from over the shoulder, out to the side and up before it bends in on its mark
                    Vec3 from = fx.origin.add(0, (e != null ? e.getEyeHeight() : 1.5) + 0.3, 0).add(look.scale(-0.3)).add(right.scale(turn * 0.45)).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    double dist = to.subtract(from).length();
                    if (dist < 0.05) break;
                    Vec3 bend = from.lerp(to, 0.35).add(right.scale(turn * wide * dist * 0.45)).add(0, dist * (0.16 + 0.12 * wide), 0);
                    float flight = Math.max(0.1f, p.duration() / 20f);
                    if (age < flight) {
                        float k = age / flight;
                        Vec3 head = StarBeasts.bezier(from, bend, to, k);
                        Vec3 dir = head.subtract(StarBeasts.bezier(from, bend, to, Math.max(0, k - 0.04f)));
                        dir = dir.lengthSqr() < 1.0e-8 ? bend.subtract(from).normalize() : dir.normalize();
                        Vec3 last = head;
                        for (int i = 1; i <= 6; i++) {
                            float kk = k - i * 0.05f;
                            if (kk < 0) break;
                            Vec3 next = StarBeasts.bezier(from, bend, to, kk);
                            streak(pose, buffers, camera, at, next, last, 0.16f * p.size() * (1 - i / 7f), c, 0.6f * (1 - i / 7f));
                            last = next;
                        }
                        arrow(pose, buffers, camera, at, head, dir, 0.75f * p.size(), c, 1f);
                    } else {
                        float k = Mth.clamp((age - flight) / 0.25f, 0, 1);
                        dotTex(pose, buffers, camera, BURST, (float) to.x, (float) to.y, (float) to.z, (0.3f + 0.7f * k) * p.size(), light, (1 - k) * 1.1f, fx.seed % 360);
                        dot(pose, buffers, camera, (float) to.x, (float) to.y, (float) to.z, 0.45f * p.size() * (1 - k), c, (1 - k) * 0.7f);
                    }
                }
                case SIGHT -> {
                    if (e == null) break;
                    Vec3 view = e.getViewVector(partial);
                    Vec3 eye = e.getEyePosition(partial);
                    Vec3 right = view.cross(new Vec3(0, 1, 0));
                    right = right.lengthSqr() > 1.0e-4 ? right.normalize() : Vec3.ZERO;
                    boolean gaze = p.size() < 0.5f;
                    // from beside the eye, so that the one who aims sees their own line
                    Vec3 a = eye.add(right.scale(0.22)).add(0, -0.16, 0).add(view.scale(0.3)).subtract(at);
                    Vec3 b = (gaze ? sightEnd(e, eye, view) : fx.target()).subtract(at);
                    float seen = gaze ? Mth.clamp(age / 0.15f, 0, 1) * Mth.clamp((life - age) / 0.2f, 0, 1) : Mth.clamp(age / 0.05f, 0, 1);
                    float blink = 0.8f + 0.2f * Mth.sin(time * 0.9f);
                    ray(pose, buffers, camera, at, a, b, c, seen * 0.8f * blink);
                    float far = (float) camera.getPosition().distanceTo(at.add(b));
                    float ring = (gaze ? 0.16f + 0.03f * Mth.sin(time * 0.5f) : 0.6f - 0.42f * t) * Math.max(1, far / 9f);
                    dot(pose, buffers, camera, (float) b.x, (float) b.y, (float) b.z, 0.09f * Math.max(1, far / 9f), 0xFFFFFF, seen);
                    dotTex(pose, buffers, camera, RING, (float) b.x, (float) b.y, (float) b.z, ring, c, seen, time * 3);
                }
                case GALE -> {
                    Vec3 from = shotFrom(fx, e).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    Vec3 path = to.subtract(from);
                    double dist = path.length();
                    if (dist < 0.5) break;
                    Vec3 dir = path.scale(1 / dist);
                    float flight = (float) dist / 80f;
                    float k = Mth.clamp(age / flight, 0, 1);
                    Vec3 head = from.add(dir.scale(dist * k));
                    if (age < flight + 0.1f) {
                        float a = age < flight ? 1 : 1 - (age - flight) / 0.1f;
                        streak(pose, buffers, camera, at, from.add(dir.scale(Math.max(0, dist * k - 6))), head, 0.3f, c, a * 0.8f);
                        arrow(pose, buffers, camera, at, head, dir, 1.2f, c, a);
                    }
                    // the wind it drags along: rings that open one after another down the line, and a wake between them
                    streak(pose, buffers, camera, at, from, head, p.size() * 1.1f, c, fade * 0.2f);
                    float turn = (float) Math.atan2(dir.x, dir.z), tilt = (float) -Math.asin(dir.y);
                    int rings = Math.max(2, (int) (dist / 3));
                    for (int i = 0; i < rings; i++) {
                        double along = 1.5 + i * (dist - 2) / rings;
                        float rk = (age - (float) along / 80f) / 0.45f;
                        if (rk <= 0 || rk >= 1) continue;
                        Vec3 g = from.add(dir.scale(along));
                        pose.pushPose();
                        pose.translate(g.x, g.y, g.z);
                        pose.mulPose(Axis.YP.rotation(turn));
                        pose.mulPose(Axis.XP.rotation(tilt));
                        pose.mulPose(Axis.ZP.rotationDegrees(time * 20 + i * 40));
                        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(RING)), pose.last().pose(), 0.25f + p.size() * (1 - (1 - rk) * (1 - rk)), i % 2 == 0 ? light : c, (1 - rk) * 0.9f);
                        pose.popPose();
                    }
                }
                case UPDRAFT -> {
                    // it turns around its owner's eyes too: in first person only a breath of it is drawn
                    float seen = self ? hold * 0.3f : hold;
                    for (int i = 0; i < 7; i++) {
                        float phase = (age * 1.5f + i / 7f) % 1f;
                        Vec3 last = null;
                        for (int sgm = 0; sgm <= 3; sgm++) {
                            float ph = phase - sgm * 0.035f;
                            float ang = i * 0.9f + ph * 7.5f;
                            float rad = p.size() * (1.05f - 0.45f * ph);
                            Vec3 pt = new Vec3(Mth.cos(ang) * rad, -0.3 + ph * 3.6, Mth.sin(ang) * rad);
                            if (last != null) streak(pose, buffers, camera, at, pt, last, 0.18f * (1 - sgm * 0.2f), i % 2 == 0 ? light : c, Mth.sin(phase * Mth.PI) * seen * (1 - sgm * 0.25f));
                            last = pt;
                        }
                    }
                    // and the dust it lifts where it started: thrown outwards once and gone, not a circle to stand in
                    float k = age / 0.45f;
                    if (k < 1) {
                        Vec3 foot = fx.origin.subtract(at);
                        pose.translate(foot.x, foot.y, foot.z);
                        disc(pose, buffers, RING, 0.06f, p.size() * (0.5f + 2.2f * k), 0, light, (1 - k) * (1 - k) * 0.7f);
                    }
                }
                case QUIVER -> {
                    // behind their owner's back, so in first person there is nothing of them to see
                    if (e == null || self) break;
                    int n = Math.max(1, Math.round(p.size()));
                    Vec3 fwd = Vec3.directionFromRotation(0, bodyYaw(e, partial));
                    Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
                    float seen = (fx.settled ? 1 : Mth.clamp(age / 0.3f, 0, 1)) * Mth.clamp((life - age) / 0.4f, 0, 1);
                    Vec3 hub = fwd.scale(-0.42).add(0, e.getBbHeight() * 0.72, 0);
                    for (int i = 0; i < n; i++) {
                        // a fan: the middle arrow straight up, the outer ones leaning out to the sides
                        float u = n == 1 ? 0 : i / (float) (n - 1) * 2 - 1;
                        float ang = u * Math.min(1.25f, 0.2f * n);
                        Vec3 dir = side.scale(Mth.sin(ang)).add(0, Mth.cos(ang), 0).add(fwd.scale(-0.12)).normalize();
                        float bob = 0.05f * Mth.sin(time * 0.14f + i * 1.7f);
                        arrow(pose, buffers, camera, at, hub.add(dir.scale(1.45 + bob)), dir, 0.8f, c, seen);
                    }
                    dot(pose, buffers, camera, (float) hub.x, (float) hub.y + 0.5f, (float) hub.z, 0.9f, c, seen * 0.2f);
                }
                case WINDWALL -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    Vec3 base = fx.target();
                    Vec3 look = Vec3.directionFromRotation(0, fx.yaw);
                    Vec3 side = new Vec3(-look.z, 0, look.x);
                    float half = p.size() / 2 * (1 - (1 - Mth.clamp(age / 0.25f, 0, 1)) * (1 - Mth.clamp(age / 0.25f, 0, 1)));
                    float tall = 3.4f;
                    int n = Math.round(p.size() * 2.4f);
                    for (int i = 0; i < n; i++) {
                        long h = (fx.seed + i) * 0x9E3779B97F4A7C15L;
                        float u = ((h >>> 12) & 0xFFFF) / 65535f, v = ((h >>> 28) & 0xFFFF) / 65535f, w = ((h >>> 44) & 0xFFFF) / 65535f;
                        float period = 0.55f + 0.4f * v;
                        float phase = ((age + w * period) / period) % 1f;
                        float x = (u * 2 - 1) * half;
                        // each gust climbs the wall in a slow S, the way smoke does in a draught: straight lines would be rain
                        Vec3 last = null;
                        for (int sgm = 0; sgm <= 4; sgm++) {
                            float ph = phase - sgm * 0.07f;
                            if (ph < 0) break;
                            float sway = 0.5f * Mth.sin(ph * 7.5f + i * 2.3f);
                            float bow = 0.22f * Mth.sin(ph * Mth.TWO_PI + i);
                            Vec3 pt = side.scale(Mth.clamp(x + sway, -half, half)).add(look.scale(bow)).add(0, ph * tall, 0);
                            if (last != null) streak(pose, buffers, camera, base, pt, last, (0.17f + 0.1f * w) * (1 - sgm * 0.15f), i % 3 == 0 ? light : c,
                                    Mth.sin(phase * Mth.PI) * hold * 0.8f * (1 - sgm * 0.2f));
                            last = pt;
                        }
                    }
                    // the wall itself: a sheet of pale air between the line it stands on and the line it ends at
                    for (int row = 0; row < 3; row++) {
                        float y = 0.6f + row * 1.1f + 0.1f * Mth.sin(time * 0.2f + row);
                        streak(pose, buffers, camera, base, side.scale(-half).add(0, y, 0), side.scale(half).add(0, y, 0), 1.2f, c, hold * 0.16f);
                    }
                    streak(pose, buffers, camera, base, side.scale(-half).add(0, 0.08, 0), side.scale(half).add(0, 0.08, 0), 0.22f, light, hold * 0.7f);
                    streak(pose, buffers, camera, base, side.scale(-half).add(0, tall, 0), side.scale(half).add(0, tall, 0), 0.1f, c, hold * 0.35f);
                }
                case BEAST -> StarBeasts.render(pose, buffers, camera, at, fx.origin, fx.yaw, fx.target(), p.duration() % 10, p.duration() / 10 / 20f, p.size(), c, age, time);
                case CLAW -> {
                    // torn across whatever is looked at: drawn in the plane of the screen, so it is never seen edge-on
                    Vec3 cc = fx.target().subtract(at);
                    Vector3f cl = camera.getLeftVector(), cu = camera.getUpVector();
                    Vec3 left = new Vec3(cl.x(), cl.y(), cl.z()), upward = new Vec3(cu.x(), cu.y(), cu.z());
                    float len = p.size();
                    float a = Mth.clamp(1 - (age - 0.14f) / (life - 0.14f), 0, 1);
                    for (int i = 0; i < 3; i++) {
                        float k = Mth.clamp((age - i * 0.03f) / 0.07f, 0, 1);
                        if (k <= 0) continue;
                        float reach = (i == 1 ? 0.62f : 0.5f) * len;
                        Vec3 top = cc.add(upward.scale(reach)).add(left.scale((i - 1) * 0.3 * len + 0.28 * len));
                        Vec3 bottom = cc.add(upward.scale(-reach)).add(left.scale((i - 1) * 0.3 * len - 0.28 * len));
                        Vec3 end = top.lerp(bottom, k);
                        streak(pose, buffers, camera, at, top, end, 0.22f * len, c, a);
                        streak(pose, buffers, camera, at, top, end, 0.07f * len, 0xFFFFFF, a);
                    }
                    dot(pose, buffers, camera, (float) cc.x, (float) cc.y, (float) cc.z, len * 0.9f * (1 - t), c, fade * 0.3f);
                }
                case SWARM -> {
                    if (e == null) break;
                    float mid = e.getBbHeight() * 0.6f;
                    for (int i = 0; i < 22; i++) {
                        long h = (fx.seed + i) * 0x9E3779B97F4A7C15L;
                        float u = ((h >>> 12) & 0xFFFF) / 65535f, v = ((h >>> 28) & 0xFFFF) / 65535f, w = ((h >>> 44) & 0xFFFF) / 65535f;
                        // each on its own crooked little orbit, darting in and out
                        float speed = 0.5f + 0.6f * u;
                        Vec3 pos = hornet(time, i, speed, v, w, p.size(), mid);
                        streak(pose, buffers, camera, at, hornet(time - 0.9f, i, speed, v, w, p.size(), mid), pos, 0.07f, c, hold * 0.7f);
                        dot(pose, buffers, camera, (float) pos.x, (float) pos.y, (float) pos.z, 0.13f, c, hold * 0.6f);
                        dot(pose, buffers, camera, (float) pos.x, (float) pos.y, (float) pos.z, 0.06f, 0xFFFFFF, hold);
                    }
                }
                case TRAP -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    Vec3 base = fx.target();
                    // the same spot always lies the same way round, set or sprung
                    float turn = (float) ((Math.abs(base.x) * 12.9898 + Math.abs(base.z) * 78.233) % Math.PI);
                    if (p.duration() > 0) {
                        // set: it opens as it is laid down, and then it waits. A glint runs round the iron now and then
                        float open = 1 - (1 - Mth.clamp(age / 0.35f, 0, 1)) * (1 - Mth.clamp(age / 0.35f, 0, 1));
                        float a = Mth.clamp(age / 0.1f, 0, 1) * Mth.clamp((life - age) / 0.5f, 0, 1);
                        jaws(pose, buffers, camera, base, p.size(), 1 - open, turn, c, a * 0.9f);
                        disc(pose, buffers, RING, 0.05f, p.size() * 0.32f, 0, c, a * 0.6f);
                        float glint = (age % 2.6f) / 0.5f;
                        if (glint < 1) {
                            float ga = glint * Mth.TWO_PI + turn;
                            dot(pose, buffers, camera, Mth.cos(ga) * p.size(), 0.1f, Mth.sin(ga) * p.size(), 0.16f, 0xFFFFFF, a * Mth.sin(glint * Mth.PI));
                        }
                    } else {
                        // sprung: shut in a blink, and gone a moment later
                        float a = Mth.clamp(1 - (age - 0.35f) / 0.4f, 0, 1);
                        jaws(pose, buffers, camera, base, p.size(), Mth.clamp(age / 0.06f, 0, 1), turn, c, a);
                        if (age < 0.3f) {
                            float k = age / 0.3f;
                            disc(pose, buffers, RING, 0.07f, p.size() * (0.6f + 1.6f * k), 0, light, 1 - k);
                            dot(pose, buffers, camera, 0, 0.5f, 0, 0.25f + 0.5f * (1 - k), 0xFFFFFF, (1 - k) * 0.9f);
                        }
                    }
                }
                case WIRE -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    Vec3 base = fx.target();
                    Vec3 look = Vec3.directionFromRotation(0, fx.yaw);
                    Vec3 right = new Vec3(-look.z, 0, look.x).scale(p.size() / 2);
                    Vec3 left = right.scale(-1);
                    float a = Mth.clamp((life - age) / 0.5f, 0, 1);
                    for (Vec3 stake : new Vec3[]{left, right}) {
                        streak(pose, buffers, camera, base, stake.add(0, -0.05, 0), stake.add(0, 0.6, 0), 0.16f, c, a);
                        streak(pose, buffers, camera, base, stake.add(0, -0.05, 0), stake.add(0, 0.6, 0), 0.05f, light, a * 0.8f);
                        dot(pose, buffers, camera, (float) stake.x, 0.62f, (float) stake.z, 0.11f, light, a);
                    }
                    // the wire runs out from one stake to the other, and then it is hard to see: a thread, and a glint that runs along it
                    float strung = Mth.clamp(age / 0.25f, 0, 1);
                    streak(pose, buffers, camera, base, left.add(0, 0.35, 0), left.lerp(right, strung).add(0, 0.35, 0), 0.06f, light, a * 0.6f);
                    float glint = (age % 2.2f) / 0.7f;
                    if (glint < 1 && strung >= 1) {
                        Vec3 g = left.lerp(right, glint).add(0, 0.35, 0);
                        dot(pose, buffers, camera, (float) g.x, (float) g.y, (float) g.z, 0.13f, 0xFFFFFF, a * Mth.sin(glint * Mth.PI));
                    }
                }
                case NET -> {
                    Vec3 hand = shotFrom(fx, e).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    float flight = 0.25f;
                    float k = Mth.clamp(age / flight, 0, 1);
                    // flying it is a spinning disc that opens; landed it hangs over whatever is under it
                    float r = p.size() * (0.12f + 0.88f * k * k);
                    Vec3 centre = hand.lerp(to, k).add(0, Mth.sin(k * Mth.PI) * 1.6, 0);
                    float a = Mth.clamp((life - age) / 0.5f, 0, 1);
                    float spin = Math.min(age, flight) * 9;
                    float crown = age < flight ? 0.3f : 1.9f - 0.25f * Mth.clamp((age - flight) / 0.3f, 0, 1);
                    Vec3 top = centre.add(0, crown, 0);
                    int n = 10;
                    Vec3 lastRim = null, lastIn = null;
                    for (int i = 0; i <= n; i++) {
                        float ang = spin + i * Mth.TWO_PI / n;
                        Vec3 rim = centre.add(Mth.cos(ang) * r, 0.05, Mth.sin(ang) * r);
                        Vec3 in = centre.add(Mth.cos(ang) * r * 0.5, crown * 0.8, Mth.sin(ang) * r * 0.5);
                        if (lastRim != null) {
                            streak(pose, buffers, camera, at, lastRim, rim, 0.09f, c, a * 0.9f);
                            streak(pose, buffers, camera, at, lastIn, in, 0.07f, c, a * 0.75f);
                        }
                        if (i < n) {
                            // the cords from the middle out to the rim, and a weight where each one ends
                            streak(pose, buffers, camera, at, top, in, 0.06f, light, a * 0.7f);
                            streak(pose, buffers, camera, at, in, rim, 0.06f, light, a * 0.7f);
                            dot(pose, buffers, camera, (float) rim.x, (float) rim.y, (float) rim.z, 0.12f, light, a);
                        }
                        lastRim = rim;
                        lastIn = in;
                    }
                }
                case ROPE -> {
                    if (e == null) break;
                    float tall = e.getBbHeight();
                    // a pole that was bent to the ground whips upright beside the creature and takes it up by the rope
                    Vec3 foot = fx.target();
                    float sprung = 1 - (1 - Mth.clamp(age / 0.3f, 0, 1)) * (1 - Mth.clamp(age / 0.3f, 0, 1));
                    Vec3 tip = fx.origin.add(0, 0.3, 0).lerp(fx.origin.add(0, tall + 5.6, 0), sprung);
                    Vec3 knee = foot.add(0, 2.2, 0).lerp(new Vec3(foot.x, fx.origin.y + tall + 6.2, foot.z), sprung);
                    float a = Mth.clamp((life - age) / 0.4f, 0, 1);
                    Vec3 last = foot.subtract(at);
                    for (int i = 1; i <= 8; i++) {
                        Vec3 next = StarBeasts.bezier(foot, knee, tip, i / 8f).subtract(at);
                        streak(pose, buffers, camera, at, last, next, 0.2f * (1 - i * 0.07f), c, a);
                        streak(pose, buffers, camera, at, last, next, 0.06f, light, a * 0.7f);
                        last = next;
                    }
                    Vec3 knot = new Vec3(0, tall + 0.05, 0);
                    streak(pose, buffers, camera, at, tip.subtract(at), knot, 0.06f, light, a * 0.9f);
                    dot(pose, buffers, camera, 0, tall + 0.05f, 0, 0.11f, 0xFFFFFF, a);
                    // the noose
                    disc(pose, buffers, RING, tall * 0.85f, Math.max(0.35f, e.getBbWidth() * 0.75f), time * 2, light, a * 0.9f);
                }
                case CLOUD -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    Vec3 base = fx.target();
                    float r = p.size();
                    float gather = 1 - (1 - Mth.clamp(age / 0.6f, 0, 1)) * (1 - Mth.clamp(age / 0.6f, 0, 1));
                    float a = Mth.clamp(age / 0.4f, 0, 1) * Mth.clamp((life - age) / 0.8f, 0, 1);
                    int dark = mix(c, 0x3A475E, 0.7f);
                    int n = Mth.clamp(Math.round(r * r * 0.9f), 30, 110);
                    // from the ground a wide cloud is looked through edge-on, many puffs deep: the wider, the fainter each one
                    float thin = Math.min(1f, (float) Math.sqrt(6 / r));
                    // lightning somewhere inside it lights the cloud around it, a few times a second
                    long beat = (long) (time / 3);
                    for (int i = 0; i < n; i++) {
                        long h = (fx.seed + i) * 0x9E3779B97F4A7C15L;
                        float u = ((h >>> 12) & 0xFFFF) / 65535f, v = ((h >>> 28) & 0xFFFF) / 65535f, w = ((h >>> 44) & 0xFFFF) / 65535f;
                        float dist = (float) Math.sqrt(u) * r * 0.95f * gather;
                        float ang = v * Mth.TWO_PI + time * 0.006f * (0.5f + w);
                        float x = Mth.cos(ang) * dist, y = 7f + 1.1f * (w - 0.5f) + 0.12f * Mth.sin(time * 0.04f + i), z = Mth.sin(ang) * dist;
                        boolean lit = Math.floorMod((fx.seed ^ beat) * 0xC2B2AE3D27D4EB4FL + i * 0x165667B19E3779F9L >>> 20, 11) == 0;
                        dot(pose, buffers, camera, x, y, z, 1.5f + 1.3f * w, lit ? mix(c, 0xFFFFFF, 0.6f) : dark, a * (lit ? 0.5f : 0.26f) * thin);
                    }
                    // thin rain under it
                    for (int i = 0; i < 18; i++) {
                        long h = (fx.seed + 500 + i) * 0x9E3779B97F4A7C15L;
                        float u = ((h >>> 12) & 0xFFFF) / 65535f, v = ((h >>> 28) & 0xFFFF) / 65535f, w = ((h >>> 44) & 0xFFFF) / 65535f;
                        float k = (age / (0.45f + 0.25f * w) + v) % 1f;
                        float dist = (float) Math.sqrt(u) * r * 0.9f;
                        float ang = (v + w) * Mth.TWO_PI;
                        Vec3 head = new Vec3(Mth.cos(ang) * dist, 6.6 * (1 - k), Mth.sin(ang) * dist);
                        streak(pose, buffers, camera, base, head.add(0.05, 1.1, 0), head, 0.05f, light, a * 0.32f);
                    }
                    disc(pose, buffers, RING, 0.05f, r, time * 0.3f, c, a * 0.3f);
                }
                case BALL -> {
                    Vec3 from = fx.origin.add(0, 1.2, 0).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    float flight = Math.max(0.1f, p.duration() / 20f);
                    Vec3 pos = from.lerp(to, Mth.clamp(age / flight, 0, 1)).add(0, 0.12 * Mth.sin(age * 9), 0);
                    float a = Mth.clamp(age / 0.12f, 0, 1) * (age <= flight ? 1 : Mth.clamp(1 - (age - flight) / 0.12f, 0, 1));
                    float r = p.size();
                    float pulse = 1 + 0.12f * Mth.sin(time * 1.3f);
                    float px = (float) pos.x, py = (float) pos.y, pz = (float) pos.z;
                    dot(pose, buffers, camera, px, py, pz, r * 2.4f * pulse, c, a * 0.5f);
                    dot(pose, buffers, camera, px, py, pz, r * 1.3f * pulse, light, a * 0.9f);
                    dot(pose, buffers, camera, px, py, pz, r * 0.6f, 0xFFFFFF, a);
                    dotTex(pose, buffers, camera, BURST, px, py, pz, r * 1.6f, light, a * 0.7f, time * 11);
                    // the arcs that reach out of it and are gone again
                    RandomSource arcs = RandomSource.create(fx.seed + (long) (time / 2));
                    for (int i = 0; i < 4; i++) {
                        Vec3 reach = new Vec3(arcs.nextDouble() - 0.5, arcs.nextDouble() - 0.5, arcs.nextDouble() - 0.5).normalize().scale(r * (1.6 + arcs.nextDouble() * 1.6));
                        spark(pose, buffers, camera, at, pos, pos.add(reach), arcs.nextLong(), 0.1f, light, a);
                    }
                }
                case STATIC -> {
                    // it crawls over its owner's eyes too: in first person only a little of it is drawn
                    float seen = self ? hold * 0.3f : hold;
                    float tall = e != null ? e.getBbHeight() : 1.1f;
                    float rad = e != null ? Math.max(0.35f, e.getBbWidth() * 0.5f + 0.12f) * p.size() / 0.9f : p.size();
                    RandomSource arcs = RandomSource.create(fx.seed + (long) (time / 3));
                    for (int i = 0; i < 3; i++) {
                        double a1 = arcs.nextDouble() * Math.PI * 2, y1 = arcs.nextDouble() * tall;
                        Vec3 p1, p2;
                        if (e != null) {
                            // over the body: from one place on it to another near by
                            double a2 = a1 + (arcs.nextDouble() - 0.5) * 1.8, y2 = Mth.clamp(y1 + (arcs.nextDouble() - 0.5) * 1.1, 0, tall);
                            p1 = new Vec3(Math.cos(a1) * rad, y1, Math.sin(a1) * rad);
                            p2 = new Vec3(Math.cos(a2) * rad, y2, Math.sin(a2) * rad);
                        } else {
                            // from the thing that hums to the ground around it
                            double out2 = rad * (0.5 + arcs.nextDouble() * 0.5);
                            p1 = new Vec3(0, 0.2 + y1 * 0.7, 0);
                            p2 = new Vec3(Math.cos(a1) * out2, 0.05, Math.sin(a1) * out2);
                        }
                        spark(pose, buffers, camera, at, p1, p2, arcs.nextLong(), 0.09f, c, seen);
                    }
                    dot(pose, buffers, camera, 0, tall * 0.5f, 0, rad * 1.6f, c, seen * (0.1f + 0.05f * Mth.sin(time * 0.7f)));
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

    static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    static void disc(PoseStack pose, MultiBufferSource buffers, ResourceLocation tex, float y, float radius, float degrees, int rgb, float alpha) {
        if (alpha <= 0.01f || radius <= 0.01f) return;
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(tex)), pose.last().pose(), radius, rgb, Math.min(1f, alpha));
        pose.popPose();
    }

    static void dot(PoseStack pose, MultiBufferSource buffers, Camera camera, float x, float y, float z, float half, int rgb, float alpha) {
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
    static void streak(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
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

    /**
     * Where a shot leaves its shooter: beside the eyes on the bow-hand side for a player, so that their own shots are
     * seen leaving in first person, and the middle of a creature (an arrow glancing off it).
     */
    private static Vec3 shotFrom(Fx fx, Entity e) {
        if (e == null) return fx.origin;
        if (!(e instanceof Player)) return fx.origin.add(0, e.getBbHeight() * 0.55, 0);
        Vec3 look = Vec3.directionFromRotation(0, fx.yaw);
        return fx.origin.add(-look.z * 0.3, e.getEyeHeight() - 0.25, look.x * 0.3);
    }

    /** An arrow of light with its head at {@code head}, flying along {@code dir}: shaft, barbs and feathers. */
    private static void arrow(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 head, Vec3 dir, float scale, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        Vec3 tail = head.subtract(dir.scale(1.15 * scale));
        // the barbs and the feathers lie in the plane that faces the camera
        Vec3 flat = dir.cross(camera.getPosition().subtract(origin.add(head)));
        if (flat.lengthSqr() < 1.0e-6) flat = dir.cross(new Vec3(0, 1, 0));
        if (flat.lengthSqr() < 1.0e-6) flat = new Vec3(1, 0, 0);
        flat = flat.normalize();
        int pale = mix(rgb, 0xFFFFFF, 0.55f);
        streak(pose, buffers, camera, origin, tail, head, 0.2f * scale, rgb, alpha * 0.9f);
        streak(pose, buffers, camera, origin, tail, head, 0.07f * scale, 0xFFFFFF, alpha);
        for (int s = -1; s <= 1; s += 2) {
            streak(pose, buffers, camera, origin, head.subtract(dir.scale(0.3 * scale)).add(flat.scale(0.13 * scale * s)), head, 0.09f * scale, pale, alpha);
            for (int i = 0; i < 2; i++) {
                Vec3 root = tail.add(dir.scale((0.08 + 0.13 * i) * scale));
                streak(pose, buffers, camera, origin, root.subtract(dir.scale(0.16 * scale)).add(flat.scale(0.12 * scale * s)), root, 0.07f * scale, pale, alpha * 0.9f);
            }
        }
        dot(pose, buffers, camera, (float) head.x, (float) head.y, (float) head.z, 0.22f * scale, pale, alpha * 0.8f);
    }

    /** What a hunter's gaze rests on: the first block or creature along it. */
    private static Vec3 sightEnd(Entity e, Vec3 eye, Vec3 view) {
        Vec3 far = eye.add(view.scale(48));
        Vec3 end = e.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(e, eye, end, e.getBoundingBox().expandTowards(view.scale(48)).inflate(1),
                x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator(), eye.distanceToSqr(end));
        return hit != null ? hit.getLocation() : end;
    }

    /**
     * A thin line of light over a long way. One long streak would thin to nothing with distance, so it is drawn in
     * pieces that widen the further they are from the eye.
     */
    private static void ray(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 a, Vec3 b, int rgb, float alpha) {
        int n = Mth.clamp((int) (b.subtract(a).length() / 4), 1, 10);
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            Vec3 next = a.lerp(b, i / (double) n);
            float far = (float) camera.getPosition().distanceTo(origin.add(prev.add(next).scale(0.5)));
            streak(pose, buffers, camera, origin, prev, next, 0.05f + 0.012f * far, rgb, alpha);
            streak(pose, buffers, camera, origin, prev, next, 0.018f + 0.004f * far, 0xFFFFFF, alpha * 0.8f);
            prev = next;
        }
    }

    /** A small crooked arc of electricity from a to b. */
    private static void spark(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 a, Vec3 b, long seed, float width, int rgb, float alpha) {
        RandomSource r = RandomSource.create(seed);
        double jitter = b.subtract(a).length() * 0.22;
        Vec3 prev = a;
        for (int i = 1; i <= 4; i++) {
            Vec3 next = a.lerp(b, i / 4.0);
            if (i < 4) next = next.add((r.nextDouble() - 0.5) * jitter, (r.nextDouble() - 0.5) * jitter, (r.nextDouble() - 0.5) * jitter);
            streak(pose, buffers, camera, origin, prev, next, width, rgb, alpha);
            streak(pose, buffers, camera, origin, prev, next, width * 0.4f, 0xFFFFFF, alpha * 0.8f);
            prev = next;
        }
    }

    /**
     * Iron jaws on the ground: two toothed half rings on a hinge. {@code shut} 0 is lying open, 1 is snapped shut.
     * Drawn line by line, so that they are seen from the side as well as from above.
     */
    private static void jaws(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, float radius, float shut, float turn, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        int pale = mix(rgb, 0xFFFFFF, 0.6f);
        float lift = shut * 1.4f;
        Vec3 hinge = new Vec3(Mth.cos(turn), 0, Mth.sin(turn));
        Vec3 across = new Vec3(-Mth.sin(turn), 0, Mth.cos(turn));
        for (int s = -1; s <= 1; s += 2) {
            // each half lies out along the ground when open and stands up when shut
            Vec3 outward = across.scale(s * Mth.cos(lift)).add(0, Mth.sin(lift), 0);
            // its teeth stand up out of an open jaw, and point at the other half when it has shut
            Vec3 tooth = across.scale(-s * Mth.sin(lift)).add(0, Mth.cos(lift), 0);
            Vec3 last = null;
            int n = 6;
            for (int i = 0; i <= n; i++) {
                float a = i * Mth.PI / n;
                Vec3 pt = hinge.scale(Mth.cos(a) * radius).add(outward.scale(Mth.sin(a) * radius)).add(0, 0.06, 0);
                if (last != null) {
                    streak(pose, buffers, camera, origin, last, pt, 0.13f, rgb, alpha);
                    streak(pose, buffers, camera, origin, last, pt, 0.045f, pale, alpha * 0.8f);
                }
                if (i > 0 && i < n) streak(pose, buffers, camera, origin, pt, pt.add(tooth.scale(0.34 * radius)), 0.08f, pale, alpha);
                last = pt;
            }
        }
        streak(pose, buffers, camera, origin, hinge.scale(-radius).add(0, 0.06, 0), hinge.scale(radius).add(0, 0.06, 0), 0.09f, rgb, alpha * 0.8f);
    }

    /** Where one light of a swarm is at a moment: its own crooked little orbit around the creature it harries. */
    private static Vec3 hornet(float time, int i, float speed, float v, float w, float radius, float mid) {
        float a = time * speed + v * 40, b = time * speed * 1.31f + w * 40, c = time * speed * 0.77f + i;
        float rad = radius * (0.55f + 0.45f * Mth.sin(c * 1.7f));
        return new Vec3(Mth.cos(a) * rad, mid + Mth.sin(b) * radius * 0.55f, Mth.sin(a) * rad * (0.75f + 0.25f * Mth.cos(c)));
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
