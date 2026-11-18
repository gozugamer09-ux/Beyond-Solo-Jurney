package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.combat.CombatWorldFx;
import com.beyondsolo.client.render.CameraParticles;
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
import org.joml.Quaternionf;
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
    /** A band with a bright cutting edge along its top: a blade's trail of light. */
    static final ResourceLocation ARC = BeyondSolo.id("textures/effect/fx/arc.png");
    /** A four-pointed star of light: a glint, the flash of a hit. */
    static final ResourceLocation FLARE = BeyondSolo.id("textures/effect/fx/flare.png");
    /** A ring with a bright leading edge and a soft wake inside it: a shock running out. */
    static final ResourceLocation SHOCK = BeyondSolo.id("textures/effect/fx/shock.png");
    private static final ResourceLocation[] ALL = {GLOW, RING, SIGIL, BURST, CIRCLE, STREAK, SLASH, ARC, FLARE, SHOCK};

    private static final class Fx {
        final SkillFxPayload p;
        final float start;
        final Vec3 origin;
        final float yaw;
        final long seed;
        /** The skin the skill's element gives it (fire burns, shadow is dark with bright edges...). */
        final Skins.Look look;
        /** Took the place of an older one of its kind: it is simply there, without fading in again. */
        boolean settled;

        Fx(SkillFxPayload p, float start, Vec3 origin, float yaw, long seed) {
            this.p = p;
            this.start = start;
            this.origin = origin;
            this.yaw = yaw;
            this.seed = seed;
            this.look = Skins.Look.of(p.element(), p.color());
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
        int c = p.color();
        int pale = mix(c, 0xFFFFFF, 0.5f);
        boolean self = e == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        switch (p.kind()) {
            case CAST -> {
                // motes rise off the rim of the circle
                for (int i = 0, n = FxParticles.count(16); i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    FxParticles.add(FxParticles.Tex.HOT, o.x + Math.cos(a) * 0.95, o.y + 0.1, o.z + Math.sin(a) * 0.95, 0, FxParticles.rf(0.03f, 0.07f), 0)
                            .life(FxParticles.rf(14, 24)).size(0.07f, 0.02f).color(0xFFFFFF, c).alpha(0.9f).fade(0.1f, 0.4f).drag(0.94f);
                }
            }
            case SLASH -> {
                if (e == null) break;
                // the edge throws a few sparks off as it passes, flying on the way it swung
                Vec3 look = Vec3.directionFromRotation(0, fx.yaw);
                Vec3 side = new Vec3(-look.z, 0, look.x);
                boolean back = p.duration() == 1;
                for (int i = -2; i <= 2; i++) {
                    Vec3 at = o.add(look.scale(p.size() * (0.85 - Math.abs(i) * 0.12))).add(side.scale(i * p.size() * 0.32)).add(0, 1.1 - i * 0.12, 0);
                    Vec3 along = side.scale(back ? 1 : -1).add(look.scale(0.4));
                    FxParticles.sparksAlong(at.x, at.y, at.z, along, 0.35, 2, 0.32f, c, 0.03f);
                }
            }
            case DASH -> {
                // dust kicked back where they set off, and where they arrive
                Vec3 start = fx.target();
                FxParticles.dust(level, start.x, start.y, start.z, 1.6f, 7, 0.32f);
                FxParticles.dust(level, o.x, o.y, o.z, 1.2f, 4, 0.28f);
                SkillShake.hit(o, 0.12f, 12, SkillShake.SWEEP);
            }
            case PULSE -> {
                FxParticles.shockwave(o.x, o.y, o.z, p.size(), 18, pale, p.duration() == 1 ? 0.35f : 0.75f);
                // duration 1 is a quiet ripple, for waves that keep coming: the ring alone, no motes
                if (p.duration() == 1) break;
                for (int i = 0, n = FxParticles.count(30); i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    double sp = 0.3 + FxParticles.RANDOM.nextDouble() * 0.15;
                    FxParticles.add(FxParticles.Tex.HOT, o.x + Math.cos(a) * 1.0, o.y + 0.25, o.z + Math.sin(a) * 1.0, Math.cos(a) * sp, 0.01, Math.sin(a) * sp)
                            .life(FxParticles.rf(14, 22)).size(0.09f, 0.03f).color(0xFFFFFF, c).alpha(1).fade(0.05f, 0.45f).drag(0.9f);
                }
            }
            case ROAR -> {
                // the shout throws up the dust around them and the air shudders
                FxParticles.smokeRing(o.x, o.y, o.z, 14, FxParticles.groundColor(level, o.x, o.y, o.z), 0.45f, 0.32f);
                FxParticles.shockwave(o.x, o.y, o.z, p.size() * 1.1f, 14, pale, 0.6f);
                SkillShake.hit(o, 0.22f, 16, SkillShake.BLAST);
            }
            case SLAM -> {
                Vec3 at = fx.target();
                slamBurst(level, at, p.size(), c, 1f);
                SkillShake.hit(at, 0.5f, 22, SkillShake.SLAM);
            }
            case BLINK -> {
                for (Vec3 at : new Vec3[]{fx.target(), o}) {
                    if (p.duration() == 1) {
                        // a step of light leaves motes behind, not smoke
                        FxParticles.motes(at.x, at.y + 0.9, at.z, 0.35, 12, c, 0.03f);
                        FxParticles.flash(at.x, at.y + 1, at.z, 0.9f, c);
                        continue;
                    }
                    int shadow = mix(c, 0x0A0612, 0.75f);
                    FxParticles.smoke(at.x, at.y + 0.8, at.z, 0.35, 9, shadow, 0.4f, 0.015f);
                    FxParticles.wisps(at.x, at.y + 0.8, at.z, 0.35, 8, c, 0.22f);
                }
            }
            case BOLT -> {
                Vec3 at = fx.target();
                FxParticles.sparks(at.x, at.y, at.z, 12, 0.35f, c, 0.04f);
                FxParticles.flash(at.x, at.y, at.z, 0.8f, c);
            }
            case THUNDER -> {
                Vec3 at = fx.target();
                FxParticles.flash(at.x, at.y + 0.6, at.z, 3f, c);
                FxParticles.sparks(at.x, at.y + 0.2, at.z, 26, 0.6f, c, 0.05f);
                FxParticles.shockwave(at.x, at.y, at.z, Math.max(2.5f, p.size() * 1.2f), 12, pale, 0.9f);
                FxParticles.groundGlow(at.x, at.y, at.z, Math.max(1.5f, p.size()), 30, c, 0.6f);
                FxParticles.smoke(at.x, at.y + 0.5, at.z, 0.6, 6, 0x34343C, 0.55f, 0.03f);
                SkillShake.hit(at, 0.4f, 30, SkillShake.BLAST);
            }
            case NOVA -> {
                // what the ring throws: flames (duration 0), sparks (1), spray (2), motes of light (3) or soul fire (4)
                // seen through the caster's own eyes the ring starts a few steps out, where it reads as a ring and not
                // as a wall of flame at the feet
                double from = self ? 2.4 : 0.7;
                float speed = Math.max(0.25f, p.size() * 0.07f);
                switch (p.duration()) {
                    case 1 -> {
                        for (int i = 0, n = FxParticles.count(36); i < n; i++) {
                            double a = i * Math.PI * 2 / n;
                            Vec3 dir = new Vec3(Math.cos(a), 0.15, Math.sin(a));
                            FxParticles.sparksAlong(o.x + dir.x * from, o.y + 0.5, o.z + dir.z * from, dir, 0.2, 1, speed * 2.2f, c, 0.02f);
                        }
                    }
                    case 2 -> {
                        FxParticles.smokeRing(o.x, o.y, o.z, 18, 0xE8F4FF, 0.5f, speed * 1.3f);
                        FxParticles.drops(o.x, o.y + 0.5, o.z, 24, speed * 1.5f, c);
                    }
                    case 3 -> {
                        for (int i = 0, n = FxParticles.count(40); i < n; i++) {
                            double a = i * Math.PI * 2 / n;
                            double sp = speed * (1.2 + FxParticles.RANDOM.nextDouble() * 0.4);
                            FxParticles.add(FxParticles.Tex.HOT, o.x + Math.cos(a) * from, o.y + 0.4, o.z + Math.sin(a) * from, Math.cos(a) * sp, 0.02, Math.sin(a) * sp)
                                    .life(FxParticles.rf(14, 24)).size(0.1f, 0.03f).color(0xFFFFFF, c).alpha(1).fade(0.05f, 0.4f).drag(0.9f);
                        }
                    }
                    case 4 -> {
                        FxParticles.fireRing(o.x, o.y, o.z, from, 30, 0.38f, speed, 0xD8FFFF, mix(c, 0x1050A0, 0.4f));
                        FxParticles.wisps(o.x, o.y + 0.6, o.z, 1.2, 10, c, 0.3f);
                    }
                    default -> {
                        FxParticles.fireRing(o.x, o.y, o.z, from, 34, 0.42f, speed, 0xFFD27A, mix(c, 0xC02000, 0.3f));
                        FxParticles.smokeRing(o.x, o.y, o.z, 10, 0x4A4440, 0.5f, speed * 0.9f);
                        if (!self) FxParticles.embers(o.x, o.y + 0.6, o.z, 1.0, 14, c);
                    }
                }
                SkillShake.hit(o, 0.2f, 14, SkillShake.BLAST);
            }
            case HEAL -> {
                FxParticles.motes(o.x, o.y + 0.8, o.z, 0.5, 18, c, 0.05f);
                FxParticles.flutter(FxParticles.Tex.PETAL, o.x, o.y + 1.4, o.z, 0.5, 5, 0.04f, pale, 0.09f);
                FxParticles.groundGlow(o.x, o.y, o.z, 1.3f, 24, c, 0.5f);
            }
            case SHOT -> {
                Vec3 from = e != null ? e.getEyePosition().add(0, -0.2, 0) : o;
                Vec3 to = fx.target();
                Vec3 d = to.subtract(from);
                // motes left hanging along the line, and the hit
                int n = (int) Math.min(18, d.length() * 0.8);
                for (int i = 1; i < n; i++) {
                    Vec3 at = from.add(d.scale(i / (double) n));
                    FxParticles.add(FxParticles.Tex.HOT, at.x, at.y, at.z, FxParticles.rnd(0.01), 0.005, FxParticles.rnd(0.01))
                            .life(FxParticles.rf(8, 16)).size(0.06f, 0.02f).color(0xFFFFFF, c).alpha(0.8f).fade(0, 0.3f).delay(i * 0.1f);
                }
                FxParticles.sparksAlong(to.x, to.y, to.z, d.scale(-1), 0.7, 8, 0.3f, c, 0.03f);
                FxParticles.flash(to.x, to.y, to.z, 0.6f, c);
            }
            case VEIL -> {
                int shadow = mix(c, 0x08060E, 0.8f);
                FxParticles.smoke(o.x, o.y + 0.9, o.z, 0.45, 18, shadow, 0.5f, 0.012f);
                FxParticles.wisps(o.x, o.y + 0.9, o.z, 0.45, 10, c, 0.25f);
            }
            case WAVE -> {
                // a blade of wind (duration 3) flies at chest height and tears nothing out of the ground
                if (p.duration() == 3) break;
                Vec3 to = fx.target();
                Vec3 d = to.subtract(o);
                BlockState ground = level.getBlockState(BlockPos.containing(o.x, o.y - 0.5, o.z));
                ParticleOptions debris = ground.isAir() ? null : new BlockParticleOption(ParticleTypes.BLOCK, ground);
                int dustColor = FxParticles.groundColor(level, o.x, o.y, o.z);
                int n = (int) (d.length() * 1.6);
                for (int i = 0; i < n; i++) {
                    float along = i / (float) n;
                    Vec3 at = o.add(d.scale(along));
                    double sx = FxParticles.rnd(p.size()), sz = FxParticles.rnd(p.size());
                    // each puff is thrown up as the wave passes it, not all at once
                    float wait = along * 6;
                    if (p.duration() == 0) {
                        FxParticles.add(FxParticles.Tex.FLAME, at.x + sx, at.y + 0.1, at.z + sz, d.x * 0.004, FxParticles.rf(0.04f, 0.09f), d.z * 0.004)
                                .life(FxParticles.rf(12, 20)).size(FxParticles.rf(0.3f, 0.45f), FxParticles.rf(0.4f, 0.6f)).color(0xFFD27A, mix(c, 0xB01800, 0.3f))
                                .alpha(0.95f).fade(0.05f, 0.55f).drag(0.93f).delay(wait);
                        if (i % 3 == 0) FxParticles.embers(at.x, at.y + 0.4, at.z, p.size(), 1, c);
                        if (i % 4 == 0) FxParticles.add(FxParticles.Tex.SMOKE, at.x + sx, at.y + 0.9, at.z + sz, 0, 0.03, 0).frame(FxParticles.RANDOM.nextInt(16))
                                .blend().life(36).size(0.4f, 0.9f).color(0x302824).alpha(0.45f).fade(0.15f, 0.35f).drag(0.94f).delay(wait + 3);
                    } else {
                        if (debris != null && i % 2 == 0) level.addParticle(debris, at.x + sx, at.y + 0.1, at.z + sz, 0, 0.4 + FxParticles.RANDOM.nextDouble() * 0.4, 0);
                        if (i % 2 == 0) FxParticles.add(FxParticles.Tex.SMOKE, at.x + sx, at.y + 0.3, at.z + sz, FxParticles.rnd(0.03), 0.03, FxParticles.rnd(0.03))
                                .frame(FxParticles.RANDOM.nextInt(16)).blend().life(FxParticles.rf(28, 40)).size(0.35f, 0.8f).color(dustColor)
                                .alpha(0.55f).fade(0.08f, 0.3f).drag(0.9f).delay(wait);
                    }
                }
                if (p.duration() == 1) SkillShake.hit(o, 0.25f, 18, SkillShake.SLAM);
            }
            case FAN -> {
                Vec3 d = fx.target().subtract(o).normalize();
                FxParticles.sparksAlong(o.x + d.x, o.y + 1.2, o.z + d.z, d, 0.55, 12, 0.6f, c, 0.02f);
            }
            case SPLASH -> {
                Vec3 at = fx.target();
                if (p.duration() == 0) {
                    FxParticles.drops(at.x, at.y + 0.3, at.z, 26, 0.32f, c);
                    FxParticles.smokeRing(at.x, at.y, at.z, 14, 0xEEF6FF, 0.4f, 0.22f);
                    FxParticles.shockwave(at.x, at.y, at.z, p.size() * 1.2f, 14, pale, 0.7f);
                } else {
                    FxParticles.flash(at.x, at.y, at.z, 1.1f, c);
                    FxParticles.sparks(at.x, at.y, at.z, 12, 0.35f, c, 0.04f);
                    FxParticles.motesBurst(at.x, at.y, at.z, 8, 0.15f, c);
                }
            }
            case PILLAR -> {
                if (p.duration() == 0) {
                    FxParticles.flash(p.x(), p.y() + 1, p.z(), 3f, c);
                    FxParticles.sparks(p.x(), p.y() + 0.2, p.z(), 20, 0.5f, c, 0.05f);
                    FxParticles.shockwave(p.x(), p.y(), p.z(), p.size() * 1.6f, 12, pale, 0.9f);
                    SkillShake.hit(new Vec3(p.x(), p.y(), p.z()), 0.35f, 28, SkillShake.BLAST);
                }
            }
            case BLOSSOM -> {
                for (int i = 0, n = FxParticles.count(44); i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    double sp = 0.18 + FxParticles.RANDOM.nextDouble() * 0.2;
                    FxParticles.add(FxParticles.Tex.PETAL, o.x + Math.cos(a) * 0.6, o.y + 0.7 + FxParticles.RANDOM.nextDouble() * 0.8, o.z + Math.sin(a) * 0.6,
                                    Math.cos(a) * sp, 0.05, Math.sin(a) * sp)
                            .life(FxParticles.rf(30, 55)).size(FxParticles.rf(0.08f, 0.13f)).color(i % 3 == 0 ? pale : c).alpha(1).fade(0.03f, 0.7f)
                            .gravity(0.005f).drag(0.9f).swirl(0.015f).spin(FxParticles.rf(-14, 14)).collide(0);
                }
                FxParticles.motes(o.x, o.y + 1, o.z, 0.6, 10, c, 0.03f);
            }
            case REACTION -> {
                FxParticles.flash(p.x(), p.y(), p.z(), 1.4f, c);
                FxParticles.motesBurst(p.x(), p.y(), p.z(), 18, 0.25f, c);
                FxParticles.sparks(p.x(), p.y(), p.z(), 8, 0.35f, c, 0.04f);
            }
            case MARK -> {
                if (e != null) FxParticles.motes(e.getX(), e.getY() + e.getBbHeight() + 0.5, e.getZ(), 0.35, 8, c, -0.02f);
            }
            case CRACK -> {
                BlockState ground = level.getBlockState(BlockPos.containing(p.x(), p.y() - 0.5, p.z()));
                if (!ground.isAir()) {
                    ParticleOptions debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
                    for (int i = 0; i < 24; i++) {
                        double a = FxParticles.RANDOM.nextDouble() * Math.PI * 2;
                        double r = FxParticles.RANDOM.nextDouble() * p.size();
                        level.addParticle(debris, p.x() + Math.cos(a) * r, p.y() + 0.1, p.z() + Math.sin(a) * r, 0, 0.25 + FxParticles.RANDOM.nextDouble() * 0.3, 0);
                    }
                }
                FxParticles.dust(level, p.x(), p.y(), p.z(), p.size() * 1.2f, 10, 0.4f);
                FxParticles.groundGlow(p.x(), p.y(), p.z(), p.size() * 0.8f, 30, c, 0.45f);
                SkillShake.hit(new Vec3(p.x(), p.y(), p.z()), 0.3f, 20, SkillShake.SLAM);
            }
            case GEYSER -> geyserBurst(level, p, c, pale);
            case BANNER -> {
                FxParticles.shockwave(p.x(), p.y(), p.z(), p.size() * 0.6f, 14, pale, 0.7f);
                FxParticles.motes(p.x(), p.y() + 0.5, p.z(), 0.6, 14, c, 0.05f);
                SkillShake.hit(new Vec3(p.x(), p.y(), p.z()), 0.2f, 16, SkillShake.SLAM);
            }
            case TRAP -> {
                // iron on iron as the jaws meet
                if (p.duration() == 0) {
                    FxParticles.sparks(p.x(), p.y() + 0.3, p.z(), 8, 0.25f, 0xFFD890, 0.04f);
                    FxParticles.flash(p.x(), p.y() + 0.3, p.z(), 0.5f, 0xFFE8C0);
                }
            }
            default -> {}
        }
    }

    /**
     * A blow landing on the ground at {@code at}: the flash of it, a shock ring and the dust running out over the
     * ground, rock thrown up, sparks, and the light of it on the ground. {@code power} scales it all.
     */
    static void slamBurst(ClientLevel level, Vec3 at, float size, int c, float power) {
        int pale = mix(c, 0xFFFFFF, 0.5f);
        FxParticles.flash(at.x, at.y + 0.4, at.z, 0.8f * power + size * 0.12f, pale);
        FxParticles.shockwave(at.x, at.y, at.z, size * 1.35f, 14, pale, 0.8f);
        FxParticles.add(FxParticles.Tex.SHOCK, at.x, at.y + 0.08, at.z, 0, 0, 0).flat().life(18).size(size * 0.1f, size * 0.9f).color(c).alpha(0.55f).fade(0, 0.3f).delay(2);
        FxParticles.groundGlow(at.x, at.y, at.z, size * 0.7f, 26, c, 0.3f);
        FxParticles.smokeRing(at.x, at.y, at.z, Math.round(16 * power), FxParticles.groundColor(level, at.x, at.y, at.z), 0.5f + size * 0.05f, 0.18f + size * 0.04f);
        FxParticles.sparks(at.x, at.y + 0.2, at.z, Math.round(12 * power), 0.45f, c, 0.05f);
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (!ground.isAir()) {
            ParticleOptions debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0, n = Math.round(30 * power); i < n; i++) {
                double a = FxParticles.RANDOM.nextDouble() * Math.PI * 2;
                double r = FxParticles.RANDOM.nextDouble() * size * 0.8;
                level.addParticle(debris, at.x + Math.cos(a) * r, at.y + 0.1, at.z + Math.sin(a) * r, Math.cos(a) * 0.2, 0.35 + FxParticles.RANDOM.nextDouble() * 0.4, Math.sin(a) * 0.2);
            }
        }
    }

    /** What a fountain throws up: embers (duration 0), the ground itself (1), motes of light (2), water (3) or souls (4). */
    private static void geyserBurst(ClientLevel level, SkillFxPayload p, int c, int pale) {
        double x = p.x(), y = p.y(), z = p.z();
        float h = p.size();
        switch (p.duration()) {
            case 1 -> {
                BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.5, z));
                if (!ground.isAir()) {
                    ParticleOptions debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
                    for (int i = 0; i < 30; i++) level.addParticle(debris, x + FxParticles.rnd(0.4), y + 0.2, z + FxParticles.rnd(0.4),
                            FxParticles.rnd(0.12), 0.4 + FxParticles.RANDOM.nextDouble() * 0.4 * h / 3, FxParticles.rnd(0.12));
                }
                FxParticles.dust(level, x, y, z, 2f, 10, 0.4f);
                SkillShake.hit(new Vec3(x, y, z), 0.25f, 16, SkillShake.SLAM);
            }
            case 2 -> {
                for (int i = 0, n = FxParticles.count(30); i < n; i++) {
                    FxParticles.add(FxParticles.Tex.HOT, x + FxParticles.rnd(0.3), y + 0.2, z + FxParticles.rnd(0.3), FxParticles.rnd(0.05), 0.2 + FxParticles.RANDOM.nextDouble() * 0.12 * h, FxParticles.rnd(0.05))
                            .life(FxParticles.rf(14, 26)).size(0.1f, 0.03f).color(0xFFFFFF, c).alpha(1).fade(0.05f, 0.5f).drag(0.88f);
                }
                FxParticles.rays(x, y + 0.6, z, 1.6f, c, 12);
            }
            case 3 -> {
                FxParticles.drops(x, y + 0.3, z, 30, 0.25f + h * 0.08f, c);
                FxParticles.smokeRing(x, y, z, 12, 0xEEF6FF, 0.4f, 0.2f);
                for (int i = 0, n = FxParticles.count(8); i < n; i++) {
                    FxParticles.add(FxParticles.Tex.SMOKE, x + FxParticles.rnd(0.3), y + 0.3, z + FxParticles.rnd(0.3), 0, 0.12 + 0.05 * h, 0).frame(FxParticles.RANDOM.nextInt(16))
                            .blend().life(26).size(0.35f, 0.8f).color(0xF0F8FF).alpha(0.5f).fade(0.05f, 0.3f).drag(0.88f);
                }
            }
            case 4 -> {
                for (int i = 0, n = FxParticles.count(22); i < n; i++) {
                    FxParticles.add(FxParticles.Tex.FLAME, x + FxParticles.rnd(0.35), y + 0.1, z + FxParticles.rnd(0.35), FxParticles.rnd(0.03), 0.15 + FxParticles.RANDOM.nextDouble() * 0.06 * h, FxParticles.rnd(0.03))
                            .life(FxParticles.rf(12, 20)).size(0.3f, 0.5f).color(0xD8FFFF, mix(c, 0x1050A0, 0.4f)).alpha(0.95f).fade(0.05f, 0.55f).drag(0.9f);
                }
                FxParticles.wisps(x, y + h * 0.4, z, 0.5, 10, c, 0.3f);
            }
            default -> {
                for (int i = 0, n = FxParticles.count(16); i < n; i++) {
                    FxParticles.add(FxParticles.Tex.FLAME, x + FxParticles.rnd(0.35), y + 0.1, z + FxParticles.rnd(0.35), FxParticles.rnd(0.03), 0.15 + FxParticles.RANDOM.nextDouble() * 0.06 * h, FxParticles.rnd(0.03))
                            .life(FxParticles.rf(12, 20)).size(0.26f, 0.42f).color(0xFFD27A, mix(c, 0xB01800, 0.3f)).alpha(0.9f).fade(0.05f, 0.5f).drag(0.9f);
                }
                FxParticles.embers(x, y + h * 0.5, z, 0.6, 16, c);
                FxParticles.smoke(x, y + h * 0.35, z, 0.4, 5, 0x4A4440, 0.45f, 0.02f);
            }
        }
        FxParticles.shockwave(x, y, z, 1.6f, 12, pale, 0.6f);
    }

    /** True on the one tick at which something {@code at} seconds into an effect happens (a landing, a strike). */
    private static boolean moment(float age, float at) {
        return age >= at && age < at + 0.051f;
    }

    /** Lasting effects drop a few particles every tick, and the ones that land later burst when they do. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        ClientLevel level = mc.level;
        float time = now(0);
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (Fx fx : ACTIVE) {
            float age = (time - fx.start) / 20f;
            if (age > fx.life()) continue;
            SkillFxPayload p = fx.p;
            Entity e = level.getEntity(p.entityId());
            boolean self = e == mc.player && firstPerson;
            int c = p.color();
            int pale = mix(c, 0xFFFFFF, 0.5f);
            Vec3 target = fx.target();
            switch (p.kind()) {
                case AURA -> {
                    if (e == null || self) break;
                    // motes and curls of the colour rise off the body: no ring on the ground
                    if (RANDOM.nextFloat() < 0.7f) {
                        FxParticles.add(FxParticles.Tex.HOT, e.getX() + rnd(0.4), e.getY() + RANDOM.nextDouble() * e.getBbHeight(), e.getZ() + rnd(0.4), 0, 0.035, 0)
                                .life(FxParticles.rf(14, 24)).size(0.07f, 0.02f).color(0xFFFFFF, c).alpha(0.9f).fade(0.15f, 0.5f).drag(0.95f);
                    }
                    if (RANDOM.nextFloat() < 0.2f) FxParticles.wisps(e.getX(), e.getY() + e.getBbHeight() * 0.4, e.getZ(), 0.35, 1, c, 0.18f);
                }
                case VEIL -> {
                    if (e == null || RANDOM.nextFloat() > 0.45f) break;
                    FxParticles.add(FxParticles.Tex.SMOKE, e.getX() + rnd(0.35), e.getY() + 0.15, e.getZ() + rnd(0.35), 0, 0.012, 0)
                            .frame(RANDOM.nextInt(16)).blend().life(FxParticles.rf(24, 36)).size(0.22f, 0.5f).color(mix(c, 0x08060E, 0.8f))
                            .alpha(0.5f).fade(0.15f, 0.4f).drag(0.94f).swirl(0.004f);
                }
                case ZONE -> {
                    double r = p.size();
                    for (int i = 0; i < 2; i++) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        double d = Math.sqrt(RANDOM.nextDouble()) * r;
                        FxParticles.add(FxParticles.Tex.HOT, p.x() + Math.cos(a) * d, p.y() + 0.1, p.z() + Math.sin(a) * d, 0, 0.03 + RANDOM.nextDouble() * 0.04, 0)
                                .life(FxParticles.rf(18, 34)).size(0.07f, 0.02f).color(0xFFFFFF, c).alpha(0.85f).fade(0.15f, 0.5f).drag(0.97f).swirl(0.003f);
                    }
                }
                case DOME -> {
                    Vec3 cc = domeCenter(fx, e, 0);
                    double r = p.size();
                    if (RANDOM.nextFloat() < 0.6f) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        double b = RANDOM.nextDouble() * Math.PI / 2;
                        FxParticles.add(FxParticles.Tex.HOT, cc.x + Math.cos(a) * Math.cos(b) * r, cc.y + (r < 3 ? 0.9 : 0) + Math.sin(b) * r, cc.z + Math.sin(a) * Math.cos(b) * r, 0, 0.005, 0)
                                .life(FxParticles.rf(10, 18)).size(0.06f, 0.02f).color(0xFFFFFF, c).alpha(0.8f).fade(0.2f, 0.4f);
                    }
                }
                case METEOR -> {
                    float fall = p.duration() / 20f;
                    float k = Mth.clamp(age / fall, 0, 1);
                    if (k < 1) {
                        // a burning tail: flames, embers and smoke left hanging where it passed
                        Vec3 at = meteorAt(fx, k);
                        FxParticles.fire(at.x, at.y, at.z, 0.35 * p.size() / 3 + 0.2, 3, 0.45f + p.size() * 0.06f, 0.02f, 0xFFD27A, mix(c, 0xB01800, 0.3f));
                        FxParticles.embers(at.x, at.y, at.z, 0.4, 1, c);
                        FxParticles.add(FxParticles.Tex.SMOKE, at.x + rnd(0.3), at.y + 0.3, at.z + rnd(0.3), 0, 0.02, 0).frame(RANDOM.nextInt(16)).blend()
                                .life(FxParticles.rf(30, 45)).size(0.5f, 1.3f).color(0x2C2622).alpha(0.5f).fade(0.1f, 0.35f).drag(0.94f).swirl(0.004f);
                    }
                    if (moment(age, fall)) {
                        slamBurst(level, target, p.size(), c, 1.3f);
                        FxParticles.fireRing(target.x, target.y, target.z, 0.6, 26, 0.5f, 0.28f + p.size() * 0.03f, 0xFFD27A, mix(c, 0xB01800, 0.3f));
                        FxParticles.embers(target.x, target.y + 0.6, target.z, p.size() * 0.5, 22, c);
                        FxParticles.smoke(target.x, target.y + 1.2, target.z, p.size() * 0.4, 10, 0x2C2622, 0.8f, 0.04f);
                        SkillShake.hit(target, 0.7f, 36, SkillShake.SLAM);
                    }
                }
                case PILLAR -> {
                    float warn = p.duration() / 20f;
                    if (p.duration() > 0 && moment(age, warn)) {
                        FxParticles.flash(target.x, target.y + 1, target.z, 2.6f, c);
                        FxParticles.rays(target.x, target.y + 1.2, target.z, 2.4f, c, 14);
                        FxParticles.sparks(target.x, target.y + 0.2, target.z, 16, 0.45f, c, 0.05f);
                        FxParticles.shockwave(target.x, target.y, target.z, p.size() * 1.7f, 14, pale, 0.9f);
                        FxParticles.groundGlow(target.x, target.y, target.z, p.size(), 30, c, 0.6f);
                        for (int i = 0, n = FxParticles.count(18); i < n; i++) {
                            FxParticles.add(FxParticles.Tex.HOT, target.x + rnd(p.size() * 0.5), target.y + RANDOM.nextDouble() * 4, target.z + rnd(p.size() * 0.5), 0, 0.08 + RANDOM.nextDouble() * 0.1, 0)
                                    .life(FxParticles.rf(14, 26)).size(0.09f, 0.02f).color(0xFFFFFF, c).alpha(1).fade(0.05f, 0.5f).drag(0.96f);
                        }
                        SkillShake.hit(target, 0.35f, 28, SkillShake.SLAM);
                    }
                }
                case SPIN -> {
                    if (e == null) break;
                    // the turning blade drags the dust round at the feet and throws sparks off its edge
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    double rr = p.size() * 0.7;
                    FxParticles.add(FxParticles.Tex.SMOKE, e.getX() + Math.cos(a) * rr, e.getY() + 0.2, e.getZ() + Math.sin(a) * rr, -Math.sin(a) * 0.22, 0.02, Math.cos(a) * 0.22)
                            .frame(RANDOM.nextInt(16)).blend().life(FxParticles.rf(16, 26)).size(0.25f, 0.6f).color(FxParticles.groundColor(level, e.getX(), e.getY(), e.getZ()))
                            .alpha(0.45f).fade(0.1f, 0.3f).drag(0.88f);
                    if (!self && RANDOM.nextFloat() < 0.6f) {
                        double b = RANDOM.nextDouble() * Math.PI * 2;
                        Vec3 at = e.position().add(Math.cos(b) * p.size() * 0.85, 1.0 + rnd(0.3), Math.sin(b) * p.size() * 0.85);
                        FxParticles.sparksAlong(at.x, at.y, at.z, new Vec3(-Math.sin(b), 0.1, Math.cos(b)), 0.3, 1, 0.35f, c, 0.03f);
                    }
                }
                case VORTEX -> {
                    for (int i = 0; i < 3; i++) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        double h = RANDOM.nextDouble() * 4.5;
                        double r = p.size() * (0.2 + 0.13 * h);
                        if (i == 0) {
                            FxParticles.add(FxParticles.Tex.SMOKE, p.x() + Math.cos(a) * r, p.y() + h, p.z() + Math.sin(a) * r, -Math.sin(a) * 0.4, 0.06, Math.cos(a) * 0.4)
                                    .frame(RANDOM.nextInt(16)).blend().life(FxParticles.rf(14, 22)).size(0.3f, 0.7f).color(mix(c, 0xE8EEF4, 0.6f))
                                    .alpha(0.35f).fade(0.15f, 0.4f).drag(0.9f);
                        } else {
                            FxParticles.add(FxParticles.Tex.HOT, p.x() + Math.cos(a) * r, p.y() + h, p.z() + Math.sin(a) * r, -Math.sin(a) * 0.45, 0.06, Math.cos(a) * 0.45)
                                    .life(FxParticles.rf(10, 18)).size(0.06f, 0.02f).color(0xFFFFFF, c).alpha(0.9f).fade(0.1f, 0.4f).drag(0.92f);
                        }
                    }
                }
                case BIND -> {
                    if (RANDOM.nextFloat() > 0.45f) break;
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    double r = Math.sqrt(RANDOM.nextDouble()) * p.size();
                    boolean small = p.size() < 3;
                    Vec3 cc = small && e != null ? e.position() : target;
                    if (small) FxParticles.wisps(cc.x + Math.cos(a) * r, cc.y + 0.2, cc.z + Math.sin(a) * r, 0.1, 1, c, 0.16f);
                    else FxParticles.flutter(FxParticles.Tex.LEAF, cc.x + Math.cos(a) * r, cc.y + 0.9, cc.z + Math.sin(a) * r, 0.2, 1, 0.02f, c, 0.09f);
                }
                case RAIN -> {
                    if (age > p.duration() / 20f) break;
                    for (int i = 0; i < 2; i++) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        double r = Math.sqrt(RANDOM.nextDouble()) * p.size();
                        FxParticles.sparks(p.x() + Math.cos(a) * r, p.y() + 0.1, p.z() + Math.sin(a) * r, 1, 0.25f, c, 0.04f);
                    }
                }
                case CLEAVE -> {
                    // the moment the blade lands
                    if (moment(age, p.duration() / 20f)) {
                        slamBurst(level, target, p.size() * 1.1f, c, 1.4f);
                        SkillShake.hit(target, 0.6f, 32, SkillShake.SLAM);
                    }
                }
                case BANNER -> {
                    if (RANDOM.nextFloat() < 0.5f) {
                        FxParticles.add(FxParticles.Tex.HOT, p.x() + 0.2 + RANDOM.nextDouble() * 1.4, p.y() + 2.6 + RANDOM.nextDouble() * 1.6, p.z() + rnd(0.3), 0, 0.03, 0)
                                .life(FxParticles.rf(16, 28)).size(0.07f, 0.02f).color(0xFFFFFF, c).alpha(0.9f).fade(0.15f, 0.5f).drag(0.96f);
                    }
                    if (RANDOM.nextFloat() < 0.3f) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        FxParticles.add(FxParticles.Tex.HOT, p.x() + Math.cos(a) * p.size(), p.y() + 0.1, p.z() + Math.sin(a) * p.size(), 0, 0.06, 0)
                                .life(FxParticles.rf(16, 28)).size(0.08f, 0.02f).color(0xFFFFFF, c).alpha(0.9f).fade(0.1f, 0.5f).drag(0.96f);
                    }
                }
                case BLADES -> {
                    if (e == null || self || RANDOM.nextFloat() > 0.5f) break;
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    Vec3 at = e.position().add(Math.cos(a) * p.size(), 1.05 + rnd(0.2), Math.sin(a) * p.size());
                    FxParticles.sparksAlong(at.x, at.y, at.z, new Vec3(-Math.sin(a), 0, Math.cos(a)), 0.2, 1, 0.3f, c, 0.02f);
                }
                case QUAKE -> {
                    BlockState ground = level.getBlockState(BlockPos.containing(p.x(), p.y() - 0.5, p.z()));
                    ParticleOptions debris = ground.isAir() ? null : new BlockParticleOption(ParticleTypes.BLOCK, ground);
                    for (int i = 0; i < 2; i++) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        double d = Math.sqrt(RANDOM.nextDouble()) * p.size();
                        if (debris != null) level.addParticle(debris, p.x() + Math.cos(a) * d, p.y() + 0.1, p.z() + Math.sin(a) * d, 0, 0.2 + RANDOM.nextDouble() * 0.3, 0);
                    }
                    if (RANDOM.nextFloat() < 0.5f) {
                        double a = RANDOM.nextDouble() * Math.PI * 2;
                        double d = Math.sqrt(RANDOM.nextDouble()) * p.size();
                        FxParticles.dust(level, p.x() + Math.cos(a) * d, p.y(), p.z() + Math.sin(a) * d, 0.8f, 1, 0.35f);
                    }
                    SkillShake.hit(target, 0.05f, p.size() + 12, SkillShake.SLAM);
                }
                case ELEMENT -> {
                    if (e != null && RANDOM.nextFloat() < 0.3f) {
                        FxParticles.add(FxParticles.Tex.HOT, e.getX() + rnd(0.4), e.getY() + RANDOM.nextDouble() * e.getBbHeight(), e.getZ() + rnd(0.4), 0, 0.025, 0)
                                .life(FxParticles.rf(12, 20)).size(0.06f, 0.02f).color(0xFFFFFF, c).alpha(0.8f).fade(0.15f, 0.5f);
                    }
                }
                case MARK -> {
                    if (e != null && RANDOM.nextFloat() < 0.25f) {
                        FxParticles.add(FxParticles.Tex.HOT, e.getX() + rnd(0.3), e.getY() + e.getBbHeight() + 0.4, e.getZ() + rnd(0.3), 0, -0.02, 0)
                                .life(FxParticles.rf(14, 22)).size(0.06f, 0.02f).color(0xFFFFFF, c).alpha(0.85f).fade(0.15f, 0.5f);
                    }
                }
                case WINGS -> {
                    // a feather of light drifts down from them now and then
                    if (e == null || self || RANDOM.nextFloat() > 0.14f) break;
                    double reach = 1.3 * p.size();
                    FxParticles.flutter(FxParticles.Tex.FEATHER, e.getX() + rnd(reach), e.getY() + e.getBbHeight() * (0.6 + RANDOM.nextDouble() * 0.5), e.getZ() + rnd(reach),
                            0.05, 1, 0.01f, pale, 0.11f);
                }
                case BUBBLE -> {
                    if (e == null || RANDOM.nextFloat() > 0.12f) break;
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    FxParticles.add(FxParticles.Tex.HOT, e.getX() + Math.cos(a) * p.size(), e.getY() + RANDOM.nextDouble() * e.getBbHeight(), e.getZ() + Math.sin(a) * p.size(), 0, 0.02, 0)
                            .life(FxParticles.rf(10, 16)).size(0.05f, 0.02f).color(0xFFFFFF, c).alpha(0.8f).fade(0.2f, 0.5f);
                }
                case HAMMER -> {
                    float flight = Math.max(0.1f, p.duration() / 20f);
                    if (moment(age, flight)) {
                        slamBurst(level, target, 1.2f + p.size(), c, 0.4f + 0.3f * p.size());
                        SkillShake.hit(target, 0.12f + 0.15f * p.size(), 20, SkillShake.SLAM);
                    }
                }
                case DISCUS -> {
                    if (moment(age, Math.max(0.1f, p.duration() / 20f))) {
                        FxParticles.flash(target.x, target.y, target.z, 0.9f, c);
                        FxParticles.sparks(target.x, target.y, target.z, 10, 0.35f, c, 0.04f);
                    }
                }
                case ARROW -> {
                    // the moment it strikes
                    if (moment(age, fx.arrowFlight())) {
                        Vec3 back = fx.origin.subtract(target);
                        FxParticles.sparksAlong(target.x, target.y, target.z, back, 0.8, 6, 0.28f, c, 0.03f);
                        FxParticles.flash(target.x, target.y, target.z, 0.55f, c);
                    }
                }
                case SEEKER -> {
                    if (moment(age, Math.max(0.1f, p.duration() / 20f))) {
                        FxParticles.flash(target.x, target.y, target.z, 0.7f, c);
                        FxParticles.sparks(target.x, target.y, target.z, 8, 0.3f, c, 0.03f);
                    }
                }
                case SKYSHOT -> {
                    // the moment it lands: the ground jumps
                    if (moment(age, Math.max(0.05f, p.duration() / 20f))) {
                        slamBurst(level, target, 1.2f + p.size(), c, 0.45f * p.size());
                        SkillShake.hit(target, 0.15f * p.size(), 24, SkillShake.SLAM);
                    }
                }
                case BALL -> {
                    if (RANDOM.nextFloat() > 0.5f) break;
                    Vec3 pos = fx.origin.add(0, 1.2, 0).lerp(target, Mth.clamp(age / Math.max(0.1f, p.duration() / 20f), 0, 1));
                    FxParticles.sparks(pos.x, pos.y, pos.z, 1, 0.25f, c, 0.01f);
                }
                case STATIC -> {
                    if (RANDOM.nextFloat() > 0.15f || self) break;
                    Vec3 cc = e != null ? e.position() : target;
                    FxParticles.sparks(cc.x + rnd(0.4), cc.y + RANDOM.nextDouble() * (e != null ? e.getBbHeight() : 1), cc.z + rnd(0.4), 1, 0.15f, c, 0.01f);
                }
                case UPDRAFT -> {
                    if (RANDOM.nextFloat() > 0.5f) break;
                    double a = RANDOM.nextDouble() * Math.PI * 2;
                    FxParticles.add(FxParticles.Tex.SMOKE, fx.origin.x + Math.cos(a) * p.size(), fx.origin.y + 0.1, fx.origin.z + Math.sin(a) * p.size(),
                                    -Math.sin(a) * 0.08, 0.22, Math.cos(a) * 0.08)
                            .frame(RANDOM.nextInt(16)).blend().life(FxParticles.rf(14, 22)).size(0.25f, 0.6f).color(mix(c, 0xEEF2F6, 0.7f))
                            .alpha(0.35f).fade(0.15f, 0.35f).drag(0.92f);
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
                    // it stays on the ground where the spell was cast, if the caster leaps or is thrown from it
                    Vec3 g = fx.origin.subtract(at);
                    pose.translate(g.x, g.y, g.z);
                    float k = Mth.clamp(age / 0.25f, 0, 1);
                    disc(pose, buffers, CIRCLE, 0.04f, p.size() * (0.5f + 0.5f * k), time * 4, c, fade * 1.1f);
                    disc(pose, buffers, RING, 0.05f, p.size() * (0.6f + 0.9f * out), 0, light, fade * 0.8f);
                }
                case SLASH -> {
                    // duration 1 is the cut back the other way
                    boolean back = p.duration() == 1;
                    pose.pushPose();
                    float r;
                    if (self && e != null) {
                        // through the wielder's own eyes the cut is a sweep across the view, a little below it and
                        // tipped up to face them; seen from beside, the same band would be a thin line
                        pose.translate(0, e.getEyeHeight(), 0);
                        pose.mulPose(Axis.YP.rotationDegrees(-e.getViewYRot(partial)));
                        pose.mulPose(Axis.XP.rotationDegrees(e.getViewXRot(partial)));
                        pose.translate(0, -0.42f, 0.55f);
                        pose.mulPose(Axis.ZP.rotationDegrees(back ? 24 : -24));
                        pose.mulPose(Axis.XP.rotationDegrees(-38));
                        r = 1.25f;
                    } else {
                        pose.translate(0, 1.1f, 0);
                        pose.mulPose(Axis.YP.rotationDegrees(-fx.yaw));
                        // cut on a slant, its far side a little raised, so it is seen from behind and not edge-on
                        pose.mulPose(Axis.ZP.rotationDegrees(back ? 24 : -24));
                        pose.mulPose(Axis.XP.rotationDegrees(-14));
                        r = p.size();
                    }
                    blade(pose, buffers, camera, r, age, life, back ? -1 : 1, c, self ? 0.8f : 1f, fx.look, time);
                    pose.popPose();
                }
                case DASH -> {
                    // afterimages left along the way, and ribbons of wind streaming past at the body's heights
                    Vec3 start = fx.target().subtract(at);
                    Vec3 run = start.scale(-1);
                    if (run.lengthSqr() < 0.01) break;
                    Vec3 side = new Vec3(-run.z, 0, run.x).normalize();
                    for (int i = 0; i < 6; i++) {
                        float k = i / 6f;
                        Vec3 g = start.scale(1 - k);
                        float ghost = fade * (0.15f + 0.45f * k) * (self ? 0.3f : 1f);
                        dot(pose, buffers, camera, (float) g.x, (float) g.y + 1.25f, (float) g.z, 0.32f, light, ghost * 0.8f);
                        streak(pose, buffers, camera, at, g.add(0, 0.25, 0), g.add(0, 1.55, 0), 0.75f, c, ghost * 0.7f);
                    }
                    RandomSource lines = RandomSource.create(fx.seed);
                    for (int i = 0; i < 7; i++) {
                        double h = 0.3 + lines.nextDouble() * 1.4;
                        double off = (lines.nextDouble() - 0.5) * 1.3;
                        float k0 = Mth.clamp(out * 1.3f - 0.3f - lines.nextFloat() * 0.2f, 0, 1);
                        Vec3 a0 = start.lerp(Vec3.ZERO, k0 * 0.8).add(side.scale(off)).add(0, h, 0);
                        Vec3 b0 = start.lerp(Vec3.ZERO, Math.min(1, k0 * 0.8 + 0.35)).add(side.scale(off)).add(0, h, 0);
                        Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(Skins.WIND), a0, b0, 4, 0.34f, k2 -> Mth.sin(k2 * Mth.PI),
                                fade * (self ? 0.4f : 0.9f), Skins.FLAT, i % 2 == 0 ? 0xFFFFFF : fx.look.light(c), -time * 0.3f, 0.5f);
                    }
                }
                case PULSE -> {
                    // a wave running out over the ground (a scan, a blessing, a cold snap), a softer one after it
                    int glow = fx.look.light(c);
                    SkinFx.shock(pose, buffers, camera, at, Vec3.ZERO, 0.3f + p.size() * out, 0.55f + p.size() * 0.035f, fade, glow, fx.seed);
                    float k2 = Mth.clamp((age - 0.25f) / (life - 0.25f), 0, 1);
                    if (k2 > 0) SkinFx.shock(pose, buffers, camera, at, Vec3.ZERO, p.size() * 0.7f * SkinFx.easeOut(k2), 0.3f, (1 - k2) * 0.7f, c, fx.seed + 1);
                    if (!self && age < 0.2f) Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), new Vec3(0, 1, 0), 0.9f, glow, 1 - age / 0.2f, 0);
                }
                case ROAR -> {
                    // a shout made visible: shells of air running out of the chest one after another, a shock over the
                    // ground, power flaring off the body for a moment. A war cry, not a spell: nothing drawn under it
                    float chest = e != null ? e.getBbHeight() * 0.72f : 1.3f;
                    SkinFx.shells(pose, buffers, camera, at, new Vec3(0, chest, 0), p.size() * 0.75f, age, 3, 0.13f, fx.look, c, self ? 0.25f : 0.55f);
                    SkinFx.shock(pose, buffers, camera, at, Vec3.ZERO, 0.6f + p.size() * 0.85f * out, 0.3f, fade * 0.9f, fx.look.light(c), fx.seed);
                    if (!self && e != null && age < 0.7f) {
                        SkinFx.flames(pose, buffers, camera, at, Vec3.ZERO, e.getBbHeight() * 1.25f, Math.max(0.4f, e.getBbWidth() * 0.7f),
                                Mth.clamp(age / 0.06f, 0, 1) * SkinFx.fadeOut(age, 0.3f, 0.4f), fx.look, c, fx.seed, time);
                    }
                }
                case SLAM -> {
                    // no sigil: a blow, not a spell. The ground splits from where it landed, a shockwave runs out and dust rises
                    Vec3 g = fx.target().subtract(at);
                    float glow = SkinFx.fadeOut(age, 0.35f, life - 0.35f);
                    if (SkinFx.scorches(fx.look)) SkinFx.scorch(pose, buffers, g, p.size() * 0.45f, glow, fx.seed);
                    SkinFx.cracks(pose, buffers, camera, at, g, fx.seed, p.size() * 0.95f, 7, SkinFx.easeOut(age / 0.12f), glow, 0.45f * glow,
                            fx.look, c, time);
                    SkinFx.shock(pose, buffers, camera, at, g, p.size() * (0.25f + 0.95f * SkinFx.easeOut(age / 0.4f)), 0.45f + p.size() * 0.06f,
                            SkinFx.fadeOut(age, 0.1f, 0.45f), fx.look.light(c), fx.seed);
                    if (age < 0.12f) {
                        float f = 1 - age / 0.12f;
                        Skins.spikes(pose, buffers, camera, g.add(0, 0.3, 0), fx.seed, 10, 1.2f + p.size() * 0.35f, 0.35f, fx.look.light(c), f);
                        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), g.add(0, 0.3, 0), 0.8f + p.size() * 0.2f, 0xFFFFFF, f, 0);
                    }
                    SkinFx.smoke(pose, buffers, camera, g.add(0, 0.2, 0), p.size() * 0.55f, age, fx.seed, 0x9A8C78, 0.8f * Mth.clamp((life - age) / 0.25f, 0, 1));
                }
                case BLINK -> {
                    // where they left, a burst of the element folds in on itself; where they came out one opens, and a
                    // streak of it is left between the two
                    Vec3 from = fx.target().subtract(at).add(0, 1, 0);
                    Vec3 here = new Vec3(0, 1, 0);
                    int body = fx.look.body(c), glow = fx.look.light(c);
                    float k = Mth.clamp(age / 0.35f, 0, 1);
                    Skins.sphere(pose, buffers, camera, at, ModRenderTypes.energy(fx.look.skin()), from, 0.05f + 1.1f * (1 - SkinFx.easeOut(k)), 8, 14,
                            1 - k, body, time * 0.02f, 2, 0, 1, 0.8f);
                    if (age < 0.12f) Skins.spikes(pose, buffers, camera, from, fx.seed, 9, 1.6f, 0.3f, glow, 1 - age / 0.12f);
                    if (!self) {
                        Skins.sphere(pose, buffers, camera, at, ModRenderTypes.energy(fx.look.skin()), here, 0.3f + 1.0f * SkinFx.easeOut(age / 0.3f), 8, 14,
                                fade, body, -time * 0.02f, 2, 0, 1, 0.9f);
                        if (age < 0.15f) Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), here, 1.1f, glow, 1 - age / 0.15f, 0);
                    }
                    Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(fx.look.skin()), from, here, 10, 0.75f * fade,
                            k2 -> 0.35f + 0.65f * Mth.sin(k2 * Mth.PI), fade * (self ? 0.5f : 0.85f), Skins.FLAT, body, -time * 0.3f, 0.5f);
                    Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), from, here, 6, 0.2f * fade, Skins.FLAT,
                            fade * (self ? 0.4f : 0.8f), Skins.FLAT, glow, -time * 0.4f, 0.5f);
                }
                case AURA -> {
                    // a buff shows on the body: power burning off it, not a ring on the ground
                    if (self) break;
                    float tall = e != null ? e.getBbHeight() : 1.8f;
                    float wide = e != null ? Math.max(0.38f, e.getBbWidth() * 0.65f) : 0.4f;
                    if (age < 0.45f) {
                        float k = age / 0.45f;
                        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), new Vec3(0, tall * 0.55f, 0), 1.2f + 0.6f * k,
                                fx.look.light(c), (1 - k) * 0.8f, 0);
                        SkinFx.flames(pose, buffers, camera, at, Vec3.ZERO, tall * (1.3f + 0.4f * k), wide * 1.15f, (1 - k) * 0.6f, fx.look, c, fx.seed + 1, time);
                    }
                    SkinFx.flames(pose, buffers, camera, at, Vec3.ZERO, tall * (0.95f + 0.2f * p.size()), wide, hold * 0.85f, fx.look, c, fx.seed, time);
                }
                case BOLT -> {
                    Vec3 from = (e != null ? e.getEyePosition(partial).add(e.getViewVector(partial).scale(self ? 1.0 : 0.6)).add(0, -0.35, 0) : fx.origin).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    if (fx.look == Skins.Look.LIGHTNING) {
                        // a forked bolt from the hand, re-struck every couple of ticks while it lasts
                        float flick = age < 0.1f ? 1 : fade * (0.75f + 0.25f * Mth.sin(time * 3.1f));
                        Skins.lightning(pose, buffers, camera, at, from, to, fx.seed + (long) (time / 2), 0.2f, c, flick * 1.1f, 0.22f, 0.3f);
                        impact(pose, buffers, camera, at, to, age, fx, 0.9f, 1f);
                        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), from, 0.35f, fx.look.light(c), fade, time * 7);
                        break;
                    }
                    // a spear of the element thrown from the hand, the trail it leaves, and the blow where it lands
                    Vec3 path = to.subtract(from);
                    double dist = path.length();
                    if (dist < 0.05) break;
                    float flight = Mth.clamp((float) dist / 70f, 0.05f, 0.15f);
                    float k = Mth.clamp(age / flight, 0, 1);
                    Vec3 dir = path.scale(1 / dist);
                    float scroll = -time * 0.3f;
                    if (age < flight) {
                        Vec3 head = from.add(path.scale(k));
                        Skins.lance(pose, buffers, camera, at, head, dir, 1.4f, 0.45f, (float) Math.min(dist * k, 5), fx.look, c, 1f, scroll);
                    } else {
                        // the wake it left hangs a moment and fades
                        float wake = Mth.clamp(1 - (age - flight) / 0.25f, 0, 1);
                        Vec3 back = to.subtract(dir.scale(Math.min(dist, 6)));
                        Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(fx.look.skin()), back, to, 8, 0.4f * wake, k2 -> 0.3f + 0.7f * k2,
                                wake * 0.6f, k2 -> k2 * k2, fx.look.body(c), scroll, 0.6f);
                        impact(pose, buffers, camera, at, to, age - flight, fx, 1f, 1f);
                    }
                    Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), from, 0.3f * (1 - k * 0.5f), fx.look.light(c), Mth.clamp(1 - age / 0.2f, 0, 1), time * 7);
                }
                case NOVA -> {
                    // a blast of the element round the caster: a dome of force swelling out of the chest, a shockwave
                    // over the ground, the ground scorched where it burned
                    SkinFx.blast(pose, buffers, camera, at, new Vec3(0, 1.0, 0), Vec3.ZERO, p.size() * 0.9f, age, life, 0.2f, fx.look, c, fx.seed,
                            self ? 0.6f : 1f);
                }
                case METEOR -> {
                    Vec3 land = fx.target().subtract(at);
                    float fall = p.duration() / 20f;
                    if (age < fall) {
                        float k = age / fall;
                        // where it will land is marked on the ground (a spell: it may have its ring)
                        pose.pushPose();
                        pose.translate(land.x, land.y, land.z);
                        disc(pose, buffers, RING, 0.06f, p.size() * (1.2f - 0.6f * k), -time * 4, c, 0.5f + 0.5f * k);
                        disc(pose, buffers, GLOW, 0.05f, p.size() * 0.8f, 0, c, 0.3f + 0.4f * k);
                        pose.popPose();
                        // the rock: a ball of the element, white-hot in front, dragging a tail of fire
                        Vec3 rock = meteorAt(fx, k).subtract(at);
                        Vec3 tail = meteorAt(fx, Math.max(0, k - 0.3f)).subtract(at);
                        Vec3 dir = rock.subtract(meteorAt(fx, Math.max(0, k - 0.05f)).subtract(at));
                        dir = dir.lengthSqr() < 1.0e-6 ? new Vec3(0, -1, 0) : dir.normalize();
                        float rr = 0.45f + p.size() * 0.16f;
                        Skins.line(pose, buffers, camera, at, ModRenderTypes.lightTexture(STREAK), tail, rock, 6, rr * 4.2f, k2 -> 0.3f + 0.7f * k2, 0.55f,
                                k2 -> k2, fx.look.light(c), 0, 0);
                        Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(fx.look.skin()), tail, rock, 10, rr * 2.4f, k2 -> 0.15f + 0.85f * k2, 0.95f,
                                k2 -> k2, fx.look.body(c), -time * 0.3f, 0.3f);
                        Skins.sphere(pose, buffers, camera, at, ModRenderTypes.energy(fx.look.skin()), rock, rr, 8, 14, 1f, fx.look.body(c), time * 0.02f, 2,
                                -time * 0.03f, 1, -0.3f);
                        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.ORB), rock, rr * 1.3f, fx.look.light(c), 0.9f, time * 3);
                        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), rock.add(dir.scale(rr * 0.5)), rr * 1.7f, 0xFFFFFF, 0.8f, time * 5);
                    } else {
                        float since = age - fall;
                        SkinFx.blast(pose, buffers, camera, at, land.add(0, 0.6, 0), land, p.size(), since, life - fall, 0.85f, fx.look, c, fx.seed, 1f);
                        SkinFx.cracks(pose, buffers, camera, at, land, fx.seed, p.size() * 1.1f, 7, SkinFx.easeOut(since / 0.15f),
                                SkinFx.fadeOut(since, 0.25f, life - fall - 0.25f), 0.5f, fx.look, c, time);
                    }
                }
                case DOME -> {
                    Vec3 cc = domeCenter(fx, e, partial).subtract(at);
                    float r = p.size() * (0.7f + 0.3f * SkinFx.easeOut(age / 0.3f));
                    boolean big = p.size() >= 3;
                    float a = hold * (self && !big ? 0.2f : 1f);
                    Vec3 centre = big ? cc : cc.add(0, 0.9, 0);
                    SkinFx.barrier(pose, buffers, camera, at, centre, r, a, null, fx.look, c, time);
                    // it goes up with a ripple of light over it
                    if (age < 0.3f) {
                        Skins.sphere(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), centre, r * (1.0f + 0.08f * age / 0.3f), 10, 18,
                                (1 - age / 0.3f) * a, fx.look.light(c), 0, 2, age * 2, 1, 1f);
                    }
                }
                case HEAL -> {
                    // a column of soft light round the healed with motes rising in it, a ring of light lifting off the ground
                    int glow = fx.look.light(c);
                    float a = Mth.clamp(age / 0.1f, 0, 1) * fade;
                    float wide = 0.55f + 0.2f * p.size();
                    Skins.wall(pose, buffers, camera, at, Vec3.ZERO, wide, 2.4f + 0.3f * p.size(), a * (self ? 0.35f : 0.8f), glow, time * 0.01f, 3,
                            k -> 1 - k * 0.3f);
                    RandomSource motes = RandomSource.create(fx.seed);
                    for (int i = 0; i < 12; i++) {
                        float ang = motes.nextFloat() * Mth.TWO_PI + age * 2.5f;
                        float rr = wide * (0.3f + 0.6f * motes.nextFloat());
                        float y = (motes.nextFloat() * 0.4f + age * (1.6f + motes.nextFloat())) % 2.8f;
                        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), new Vec3(Mth.cos(ang) * rr, y, Mth.sin(ang) * rr),
                                0.12f + 0.1f * motes.nextFloat(), i % 3 == 0 ? 0xFFFFFF : glow, a * (self ? 0.5f : 1f) * (1 - y / 2.8f), i * 31);
                    }
                    float rise = (float) Math.min(1, age / 0.9f);
                    disc(pose, buffers, RING, 0.05f + 1.9f * rise, 0.8f - 0.3f * rise, 0, light, fade);
                    disc(pose, buffers, CIRCLE, 0.04f, 0.9f + 0.3f * out, time * 3, c, fade * 0.9f);
                }
                case ZONE -> {
                    Vec3 cc = fx.target().subtract(at);
                    float grow = SkinFx.easeOut(age / 0.5f);
                    float r = p.size() * grow;
                    int glow = fx.look.light(c);
                    pose.pushPose();
                    pose.translate(cc.x, cc.y, cc.z);
                    disc(pose, buffers, CIRCLE, 0.05f, r, time * 1.2f, c, hold * 0.95f);
                    disc(pose, buffers, GLOW, 0.03f, r * 1.1f, 0, c, hold * 0.35f);
                    pose.popPose();
                    // its edge: a low wall of the element's light; motes rising inside it
                    Skins.wall(pose, buffers, camera, at, cc.add(0, 0.03, 0), r, 0.55f + 0.05f * p.size(), hold * 0.7f, glow, time * 0.004f,
                            Math.max(3, Math.round(p.size() * 1.3f)), Skins.FLAT);
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), cc.add(0, 0.05, 0), r, 0.18f + p.size() * 0.02f, glow, hold,
                            time * 0.01f, Math.max(2, p.size() * 1.5f));
                    RandomSource motes = RandomSource.create(fx.seed);
                    int n = Mth.clamp((int) (p.size() * 3), 6, 30);
                    for (int i = 0; i < n; i++) {
                        double ang = motes.nextDouble() * Math.PI * 2;
                        double rr = Math.sqrt(motes.nextDouble()) * r * 0.95;
                        float y = (motes.nextFloat() * 1.5f + age * (0.6f + motes.nextFloat() * 0.6f)) % 1.8f;
                        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), cc.add(Math.cos(ang) * rr, y + 0.1, Math.sin(ang) * rr),
                                0.1f + 0.08f * motes.nextFloat(), glow, hold * Math.min(1, (1.8f - y) * 1.5f) * Math.min(1, y * 4), i * 23);
                    }
                }
                case MARK -> {
                    if (e == null) break;
                    float h = e.getBbHeight() + 0.75f;
                    float pulse = 0.8f + 0.2f * Mth.sin(time * 0.35f);
                    float pop = SkinFx.easeOut(age / 0.2f);
                    int glow = fx.look.light(c);
                    // a sigil turning over the head, a point of light stabbing down at it, a line of the mark's colour
                    // round the feet
                    pose.pushPose();
                    pose.translate(0, h, 0);
                    pose.mulPose(camera.rotation());
                    pose.mulPose(Axis.ZP.rotationDegrees(time * 4));
                    sprite(buffers.getBuffer(ModRenderTypes.lightTexture(SIGIL)), pose.last().pose(), 0.6f * pulse * pop, c, hold);
                    pose.popPose();
                    Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), new Vec3(0, h, 0), 0.5f * pop, glow, hold * 0.7f, time * 2);
                    float bob = 0.12f * Mth.sin(time * 0.3f);
                    Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, h + 1.0 + bob, 0), new Vec3(0, h + 0.45 + bob, 0), 4,
                            0.36f * pop, k -> 1 - k * 0.92f, hold, Skins.FLAT, fx.look.body(c), 0, 0);
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.05, 0), Math.max(0.6f, e.getBbWidth()) * (1.1f + 0.1f * pulse),
                            0.14f, glow, hold * 0.9f, -time * 0.02f, 3);
                }
                case SHOT -> {
                    Vec3 from = (e == null ? fx.origin : self ? e.getEyePosition(partial).add(e.getViewVector(partial).scale(1.0)).add(0, -0.18, 0)
                            : e.getEyePosition(partial).add(0, -0.2, 0)).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    Vec3 path = to.subtract(from);
                    double dist = path.length();
                    if (dist < 0.05) break;
                    Vec3 dir = path.scale(1 / dist);
                    float flight = Mth.clamp((float) dist / 120f, 0.04f, 0.1f);
                    float k = Mth.clamp(age / flight, 0, 1);
                    Vec3 head = from.add(path.scale(k));
                    float scroll = -time * 0.4f;
                    // the line it leaves: a tracer of its colour that thins and fades from the shooter's end first
                    float wake = age < flight ? 1 : Mth.clamp(1 - (age - flight) / (life - flight), 0, 1);
                    Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), from, head, 12, 0.24f * (0.4f + 0.6f * wake), k2 -> 0.35f + 0.65f * k2,
                            wake, k2 -> 0.2f + 0.8f * k2, fx.look.body(c), scroll, 0.5f);
                    Skins.line(pose, buffers, camera, at, ModRenderTypes.lightTexture(STREAK), from, head, 6, 0.7f * wake, k2 -> 0.3f + 0.7f * k2,
                            wake * 0.5f, Skins.FLAT, c, 0, 0);
                    if (age < flight) Skins.lance(pose, buffers, camera, at, head, dir, 1.1f, 0.32f, 0, fx.look, c, 1f, scroll);
                    else impact(pose, buffers, camera, at, to, age - flight, fx, 0.7f, 0.8f);
                    // the loosing flashes at the bow (a star of light, not a ring: it is a weapon's shot, not a spell)
                    if (!self && age < 0.15f) {
                        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), from, 0.3f + 0.25f * out, fx.look.light(c), 1 - age / 0.15f, fx.seed % 90);
                    }
                }
                case WAVE -> {
                    int body = fx.look.body(c), glow = fx.look.light(c);
                    if (p.duration() == 3) {
                        // a blade of wind: a crescent flying flat at chest height, its horns swept back, ghosts of it behind
                        Vec3 a0 = fx.origin.subtract(at).add(0, 1.0, 0), b0 = fx.target().subtract(at).add(0, 1.0, 0);
                        Vec3 fly = b0.subtract(a0);
                        if (fly.lengthSqr() < 1.0e-4) break;
                        fly = fly.normalize();
                        float yaw = (float) Math.toDegrees(Math.atan2(fly.x, fly.z));
                        float flown = Mth.clamp(age / 0.32f, 0, 1);
                        float seen = age < 0.32f ? 1 : Mth.clamp(1 - (age - 0.32f) / 0.3f, 0, 1);
                        float r = p.size();
                        for (int ghost = 2; ghost >= 0; ghost--) {
                            Vec3 mid = a0.lerp(b0, Math.max(0, flown - ghost * 0.06f));
                            float ga = seen * (ghost == 0 ? 1f : ghost == 1 ? 0.4f : 0.2f);
                            pose.pushPose();
                            pose.translate(mid.x - fly.x * r * 0.7, mid.y, mid.z - fly.z * r * 0.7);
                            pose.mulPose(Axis.YP.rotationDegrees(yaw));
                            Skins.crescent(pose, buffers, ModRenderTypes.energy(Skins.WIND), r * 1.03f, r * 0.42f, -70, 70, glow, ga * 0.7f, 0, -time * 0.1f, 2);
                            Skins.crescent(pose, buffers, ModRenderTypes.energy(Skins.BLADE), r, r * 0.26f, -64, 64, body, ga, 0, 0, 1);
                            pose.popPose();
                        }
                        break;
                    }
                    Vec3 a = fx.origin.subtract(at);
                    Vec3 b = fx.target().subtract(at);
                    Vec3 dir = b.subtract(a);
                    if (dir.lengthSqr() < 1.0e-4) break;
                    dir = dir.normalize();
                    Vec3 side = new Vec3(-dir.z, 0, dir.x);
                    float k = Mth.clamp(age / 0.3f, 0, 1);
                    Vec3 head = a.lerp(b, k);
                    float half = p.size() * 0.8f;
                    if (p.duration() == 1) {
                        // spikes (rock, thorns, ice) bursting out of the ground one after another along the way
                        RandomSource r = RandomSource.create(fx.seed);
                        int n = 11;
                        float sink = SkinFx.fadeOut(age, life - 0.3f, 0.3f);
                        List<Vec3> line = new ArrayList<>();
                        for (int i = 0; i <= 8; i++) line.add(a.lerp(b, i / 8f).add(0, 0.04, 0));
                        SkinFx.fault(pose, buffers, line, k, 0.35f + half * 0.2f, fade, 0.25f, fx.look, c, time);
                        for (int i = 0; i < n; i++) {
                            float along = (i + 1f) / n;
                            Vec3 foot = a.lerp(b, along).add(side.scale((r.nextDouble() - 0.5) * 2 * half * 0.7));
                            float tall = 1.2f + r.nextFloat() * 1.2f;
                            float thick = 0.26f + 0.14f * r.nextFloat();
                            Vec3 lean = new Vec3((r.nextDouble() - 0.5) * 0.7, 1, (r.nextDouble() - 0.5) * 0.7).normalize();
                            float rise = SkinFx.easeOut((age - along * 0.3f) / 0.09f);
                            if (rise <= 0) continue;
                            Vec3 tip = foot.add(lean.scale(tall * rise * sink));
                            Skins.tube(pose, buffers, camera, at, ModRenderTypes.energy(Skins.PLASMA), foot.add(0, -0.25, 0), tip, 6, 3,
                                    k2 -> thick * (1 - k2 * 0.92f), Skins.FLAT, Math.min(1, fade * 2), body, i * 0.17f, 1, 0.1f, i * 0.3f, 0.4f, -0.5f);
                            Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), foot, tip, 3, thick * 0.6f, k2 -> 1 - k2 * 0.8f,
                                    fade * 0.6f, Skins.FLAT, glow, 0, 0);
                        }
                    } else if (p.duration() == 0) {
                        // a wall of the element rolling forward over the ground, the ground burning behind it
                        int m = 8;
                        Vec3[] top = new Vec3[m + 1], foot = new Vec3[m + 1];
                        float[] u = new float[m + 1], al = new float[m + 1];
                        float tall = 1.2f + p.size() * 0.35f;
                        for (int i = 0; i <= m; i++) {
                            float s = i / (float) m * 2 - 1;
                            float h = tall * (1 - 0.45f * s * s) * (0.85f + 0.15f * Mth.sin(time * 0.6f + i));
                            Vec3 f = head.add(side.scale(s * half)).subtract(dir.scale(half * 0.6 * s * s));
                            foot[i] = f;
                            top[i] = f.add(0, h, 0).add(dir.scale(0.25));
                            u[i] = i / (float) m * 1.5f;
                            al[i] = fade * (1 - 0.6f * s * s);
                        }
                        Skins.strip(pose, buffers, ModRenderTypes.energy(fx.look.skin()), top, foot, u, al, body, -time * 0.06f, -time * 0.06f + 1.1f);
                        Skins.strip(pose, buffers, ModRenderTypes.energy(Skins.WALL), top, foot, u, al, glow, 0.015f, 0.985f);
                        // tongues of it licking up along the wall, so that it is a wall of fire from the side too
                        for (int i = 0; i <= m; i += 2) {
                            float h = (top[i].y - foot[i].y > 0 ? (float) (top[i].y - foot[i].y) : 1) * 1.15f;
                            Vec3[] pts = new Vec3[5];
                            float[] w = new float[5], al2 = new float[5];
                            for (int j = 0; j < 5; j++) {
                                float k2 = j / 4f;
                                pts[j] = foot[i].add(dir.scale(0.15 * Mth.sin(time * 0.4f + i + k2 * 3))).add(0, h * k2, 0);
                                w[j] = 0.9f * (1 - k2) + 0.05f;
                                al2[j] = al[i] * Mth.clamp(k2 / 0.15f, 0, 1) * (1 - k2 * 0.6f);
                            }
                            Skins.ribbon(pose, buffers, camera, at, ModRenderTypes.energy(Skins.AURA), pts, w, al2, i % 4 == 0 ? glow : body, -time * 0.06f + i, 0.7f);
                        }
                        List<Vec3> trail = new ArrayList<>();
                        for (int i = 0; i <= 8; i++) trail.add(a.lerp(head, i / 8f).add(0, 0.04, 0));
                        SkinFx.fault(pose, buffers, trail, 1, half * 1.2f, fade * 0.8f, 0.6f, fx.look, c, time);
                    } else {
                        // a cut running along the ground: a crescent of the element standing up and racing forward, the
                        // ground split in a line behind it
                        List<Vec3> trail = new ArrayList<>();
                        for (int i = 0; i <= 8; i++) trail.add(a.lerp(head, i / 8f).add(0, 0.04, 0));
                        SkinFx.fault(pose, buffers, trail, 1, 0.3f + half * 0.25f, fade, 0.35f, fx.look, c, time);
                        float r = 1.1f + p.size() * 0.75f;
                        for (int ghost = 2; ghost >= 0; ghost--) {
                            Vec3 at2 = a.lerp(b, Math.max(0, k - ghost * 0.07f));
                            float ga = (age < 0.3f ? 1 : fade) * (ghost == 0 ? 1f : ghost == 1 ? 0.45f : 0.2f);
                            int m = 12;
                            Vec3[] pts = new Vec3[m + 1];
                            float[] w = new float[m + 1], al = new float[m + 1];
                            for (int i = 0; i <= m; i++) {
                                float th = (i / (float) m * 2 - 1) * 1.45f;
                                pts[i] = at2.add(side.scale(Mth.sin(th) * r * 0.75f)).add(0, Mth.cos(th) * r, 0)
                                        .subtract(dir.scale(r * 0.55f * (1 - Mth.cos(th))));
                                float mid = Mth.cos(th);
                                w[i] = (0.12f + 0.55f * mid * mid) * (0.6f + 0.25f * p.size());
                                al[i] = ga * (0.25f + 0.75f * mid);
                            }
                            Skins.ribbon(pose, buffers, camera, at, ModRenderTypes.energy(fx.look.skin()), pts, w, al, body, -time * 0.2f, 0.5f);
                            float[] core = new float[m + 1];
                            for (int i = 0; i <= m; i++) core[i] = w[i] * 0.4f;
                            Skins.ribbon(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), pts, core, al, glow, -time * 0.3f, 0.6f);
                        }
                    }
                }
                case SPIN -> {
                    // the blade's path round the body: cuts of the element at three heights, each chasing its own edge.
                    // A weapon's turn, so nothing is drawn on the ground
                    float spin = age * 1100;
                    float a = Mth.clamp(age / 0.1f, 0, 1) * Mth.clamp((life - age) / 0.25f, 0, 1) * (self ? 0.35f : 1f);
                    for (int i = 0; i < 3; i++) {
                        pose.pushPose();
                        pose.translate(0, 0.72f + i * 0.34f, 0);
                        pose.mulPose(Axis.XP.rotationDegrees((i - 1) * 10));
                        float head = spin + i * 120;
                        float r = p.size() * (0.8f + 0.1f * i);
                        Skins.crescent(pose, buffers, ModRenderTypes.lightTexture(ARC), r * 1.03f, r * 0.42f, head - 200, head, fx.look.light(c), a * 0.35f, 1.2f, 0, 1);
                        Skins.crescent(pose, buffers, ModRenderTypes.energy(Skins.BLADE), r, r * 0.22f, head - 170, head, fx.look.body(c), a, 0.8f, 0, 1);
                        pose.popPose();
                    }
                }
                case FAN -> {
                    // blades thrown out in a fan: spears of the element flying apart
                    Vec3 a = fx.origin.subtract(at).add(0, 1.2, 0);
                    Vec3 d = fx.target().subtract(fx.origin);
                    float len = (float) d.length();
                    float base = (float) Math.atan2(d.z, d.x);
                    float k = Mth.clamp(age / 0.13f, 0, 1);
                    float seen = k < 1 ? 1 : fade;
                    for (int i = -3; i <= 3; i++) {
                        float ang = base + i * 0.2f;
                        Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                        Vec3 head = a.add(dir.scale(Math.max(0.6, len * k)));
                        Skins.lance(pose, buffers, camera, at, head, dir, 0.75f, 0.24f, Math.min(len * k, 2.5f), fx.look, c, seen, -time * 0.3f);
                    }
                }
                case SPLASH -> {
                    Vec3 cc = fx.target().subtract(at);
                    float since = age;
                    if (p.duration() == 0 && e != null) {
                        // thrown from the hand first
                        float flight = 0.12f;
                        Vec3 hand = e.getEyePosition(partial).subtract(at).add(0, -0.3, 0);
                        if (age < flight) {
                            Vec3 head = hand.lerp(cc, age / flight);
                            Skins.lance(pose, buffers, camera, at, head, cc.subtract(hand), 0.9f, 0.4f, (float) head.distanceTo(hand) * 0.7f, fx.look, c, 1f,
                                    -time * 0.3f);
                            break;
                        }
                        since = age - flight;
                    }
                    // where the blow lands: a burst of the element, no ring of light
                    SkinFx.blast(pose, buffers, camera, at, cc, null, p.size() * 0.75f, since, life, 0.6f, fx.look, c, fx.seed, 1f);
                }
                case VORTEX -> {
                    Vec3 cc = fx.target().subtract(at);
                    float grow = SkinFx.easeOut(age / 0.4f);
                    pose.pushPose();
                    pose.translate(cc.x, cc.y, cc.z);
                    disc(pose, buffers, CIRCLE, 0.05f, p.size() * grow, -time * 6, c, hold * 0.8f);
                    pose.popPose();
                    SkinFx.tornado(pose, buffers, camera, at, cc, p.size() * 0.6f * grow, 4.5f + p.size() * 0.4f, hold, fx.look, c, fx.seed, time);
                }
                case PILLAR -> {
                    Vec3 cc = fx.target().subtract(at);
                    float warn = p.duration() / 20f;
                    int glow = fx.look.light(c);
                    if (age < warn) {
                        float k = age / warn;
                        pose.pushPose();
                        pose.translate(cc.x, cc.y, cc.z);
                        disc(pose, buffers, RING, 0.06f, p.size() * (1.5f - 0.5f * k), time * 5, c, 0.4f + 0.6f * k);
                        disc(pose, buffers, CIRCLE, 0.05f, p.size() * k, -time * 3, light, 0.7f * k);
                        pose.popPose();
                        // a thread of light already marks where it will come down
                        Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), cc, cc.add(0, 16, 0), 8, 0.12f + 0.18f * k, Skins.FLAT,
                                0.5f * k, k2 -> 1 - k2 * 0.8f, glow, -time * 0.3f, 0.3f);
                    } else {
                        float since = age - warn;
                        float k = Mth.clamp(since / 0.7f, 0, 1);
                        float a = (1 - k) * Mth.clamp(since / 0.04f, 0, 1);
                        if (p.duration() == 0) {
                            // a bolt out of the sky instead of a beam
                            Skins.lightning(pose, buffers, camera, at, cc.add(1.5, 18, -1), cc, fx.seed + (long) (time / 2), 0.5f, c, a * 1.2f, 0.18f, 0.3f);
                        } else {
                            float r = p.size() * 0.5f * (1 - 0.5f * k) * (0.5f + 0.5f * SkinFx.easeOut(since / 0.08f));
                            SkinFx.column(pose, buffers, camera, at, cc, 20, r, a, fx.look, c, time);
                            pose.pushPose();
                            pose.translate(cc.x, cc.y, cc.z);
                            disc(pose, buffers, CIRCLE, 0.05f, p.size(), time * 4, light, a);
                            pose.popPose();
                        }
                        SkinFx.blast(pose, buffers, camera, at, cc.add(0, 0.4, 0), cc, p.size() * 0.9f, since, life - warn, 0.6f, fx.look, c, fx.seed, 1f);
                    }
                }
                case BLOSSOM -> {
                    // blades thrown out like opening petals, turning as they go; the cut runs out over the ground
                    SkinFx.shock(pose, buffers, camera, at, Vec3.ZERO, p.size() * out, 0.4f, fade, fx.look.light(c), fx.seed);
                    float r = 0.5f + p.size() * out;
                    for (int i = 0; i < 10; i++) {
                        float ang = i * 36 + age * 172;
                        pose.pushPose();
                        pose.translate(0, 0.9f + 0.15f * Mth.sin(i * 1.7f), 0);
                        Skins.crescent(pose, buffers, ModRenderTypes.energy(Skins.BLADE), r, 0.35f + r * 0.12f, ang - 14, ang + 14, fx.look.body(c),
                                fade * (self ? 0.5f : 1f), 0.6f, 0, 1);
                        pose.popPose();
                    }
                    if (!self && age < 0.2f) Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), new Vec3(0, 1, 0), 1.4f, fx.look.light(c), 1 - age / 0.2f, 0);
                }
                case BIND -> {
                    boolean small = p.size() < 3;
                    Vec3 cc = small && e != null ? Vec3.ZERO : fx.target().subtract(at);
                    float grow = SkinFx.easeOut(age / 0.25f);
                    int body = fx.look.body(c), glow = fx.look.light(c);
                    if (small) Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), cc.add(0, 0.05, 0), p.size() * 1.05f * grow, 0.16f, glow, hold * 0.8f,
                            time * 0.02f, 3);
                    RandomSource r = RandomSource.create(fx.seed);
                    int n = small ? 6 : 16;
                    for (int i = 0; i < n; i++) {
                        double ang = i * Math.PI * 2 / n + (small ? 0 : r.nextDouble());
                        double rad = small ? p.size() * 0.85 : p.size() * (0.25 + 0.7 * r.nextDouble());
                        double tall = 0.7 + r.nextDouble() * 0.8;
                        Vec3 out2 = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                        Vec3 foot = cc.add(out2.scale(rad));
                        // chains rise out of the ground and close over a held creature; brambles stand and sway
                        Vec3 tip = small ? cc.add(out2.scale(rad * 0.15)).add(0, 1.5 * grow, 0)
                                : foot.add(0.15 * Mth.sin(time * 0.2f + i), tall * grow, 0.15 * Mth.cos(time * 0.17f + i));
                        Vec3 bend = foot.lerp(tip, 0.5).add(out2.scale(small ? 0.35 : 0.12)).add(0, small ? 0.35 : 0.1, 0);
                        int m = 8;
                        Vec3[] pts = new Vec3[m + 1];
                        float[] w = new float[m + 1], core = new float[m + 1], al = new float[m + 1];
                        for (int j = 0; j <= m; j++) {
                            float k = j / (float) m;
                            pts[j] = StarBeasts.bezier(foot, bend, tip, k);
                            w[j] = (small ? 0.22f : 0.3f) * (1 - 0.7f * k);
                            core[j] = w[j] * 0.35f;
                            al[j] = hold * (small ? 1f : 0.85f);
                        }
                        Skins.ribbon(pose, buffers, camera, at, ModRenderTypes.energy(fx.look.skin()), pts, w, al, body, -time * 0.1f + i, 0.8f);
                        Skins.ribbon(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), pts, core, al, glow, -time * 0.2f, 0.8f);
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
                    int glow = fx.look.light(c);
                    // the element it carries: a faint flame of it over the body and two motes chasing round
                    SkinFx.flames(pose, buffers, camera, at, Vec3.ZERO, h * 0.85f, rad * 0.6f, hold * 0.3f, fx.look, c, fx.seed, time);
                    for (int i = 0; i < 2; i++) {
                        float ang = time * 0.22f + i * Mth.PI;
                        float y = h * (0.5f + 0.3f * Mth.sin(time * 0.1f + i * 2));
                        Vec3 mote = new Vec3(Mth.cos(ang) * rad, y, Mth.sin(ang) * rad);
                        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), mote, 0.24f, glow, hold, time * 4);
                        dot(pose, buffers, camera, (float) mote.x, (float) mote.y, (float) mote.z, 0.42f, c, hold * 0.5f);
                    }
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.05, 0), rad * 1.1f, 0.12f, glow, hold * 0.6f, time * 0.02f, 3);
                }
                case REACTION -> {
                    Vec3 cc = fx.target().subtract(at);
                    // two elements meeting: a blast of them, its shock running out over the ground below
                    SkinFx.blast(pose, buffers, camera, at, cc, null, p.size() * 0.85f, age, life, 0.7f, fx.look, c, fx.seed, 1f);
                    pose.pushPose();
                    pose.translate(cc.x, cc.y, cc.z);
                    disc(pose, buffers, SHOCK, (float) -cc.y + 0.06f, p.size() * 1.5f * out, 0, light, fade * 0.7f);
                    pose.popPose();
                }
                case CLEAVE -> {
                    Vec3 cc = fx.target().subtract(at);
                    float fall = Math.max(0.05f, p.duration() / 20f);
                    float len = p.size() * 1.7f;
                    float wide = p.size() * 0.42f;
                    Vec3 across = new Vec3(Mth.cos(fx.seed % 7), 0, Mth.sin(fx.seed % 7));
                    if (age < fall) {
                        float k = age / fall;
                        float h = 16 * (1 - k * k);
                        pose.pushPose();
                        pose.translate(cc.x, cc.y, cc.z);
                        disc(pose, buffers, RING, 0.06f, p.size() * (1.6f - 0.8f * k), time * 6, c, 0.3f + 0.7f * k);
                        disc(pose, buffers, SIGIL, 0.05f, p.size() * 0.7f * k, -time * 4, light, 0.8f * k);
                        pose.popPose();
                        // a sword of light coming down point first, a streak of its fall above it
                        SkinFx.sword(pose, buffers, camera, at, cc.add(0, h, 0), len, wide, across, 0.5f + 0.5f * k, fx.look, c, time);
                        Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), cc.add(0, h + len, 0), cc.add(0, h + len + 7, 0), 6,
                                wide * 0.8f, k2 -> 1 - k2, 0.6f * k, k2 -> 1 - k2, fx.look.light(c), -time * 0.4f, 0.3f);
                    } else {
                        float since = age - fall;
                        float k = Mth.clamp(since / 0.9f, 0, 1);
                        // the blade stands in the ground and fades; the blow splits the ground round it
                        SkinFx.sword(pose, buffers, camera, at, cc.add(0, -0.4, 0), len, wide * (1 - 0.3f * k), across, 1 - k, fx.look, c, time);
                        SkinFx.blast(pose, buffers, camera, at, cc.add(0, 0.5, 0), cc, p.size() * 0.9f, since, life - fall, 0.5f, fx.look, c, fx.seed, 1f);
                        SkinFx.cracks(pose, buffers, camera, at, cc, fx.seed, p.size() * 1.3f, 8, SkinFx.easeOut(since / 0.15f), 1 - k, 0.4f * (1 - k),
                                fx.look, c, time);
                        pose.pushPose();
                        pose.translate(cc.x, cc.y, cc.z);
                        disc(pose, buffers, SIGIL, 0.05f, p.size() * 0.9f, time * 2, c, 1 - k);
                        pose.popPose();
                    }
                }
                case BANNER -> {
                    Vec3 cc = fx.target().subtract(at);
                    pose.translate(cc.x, cc.y, cc.z);
                    Vec3 base = fx.target();
                    float rise = 1 - (float) Math.pow(1 - Mth.clamp(age / 0.35f, 0, 1), 3);
                    float top = 4.6f * rise;
                    int body = fx.look.body(c);
                    // the pole: a shaft of solid light with a white-hot line through it, a star on its top
                    Skins.tube(pose, buffers, camera, base, ModRenderTypes.energy(Skins.PLASMA), Vec3.ZERO, new Vec3(0, top, 0), 6, 1, kk -> 0.08f, Skins.FLAT,
                            hold, body, time * 0.02f, 1, 0, 0, 0.5f, -0.4f);
                    streak(pose, buffers, camera, base, new Vec3(0, 0, 0), new Vec3(0, top, 0), 0.07f, 0xFFFFFF, hold);
                    Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), new Vec3(0, top + 0.15f, 0), 0.55f, light, hold, time * 2);
                    // the cloth: a sheet of the element's energy rippling away from the pole, its edges burning
                    if (top > 1.2f) {
                        int cols = 8, rows = 4;
                        Vec3[][] g = new Vec3[rows + 1][cols + 1];
                        for (int row = 0; row <= rows; row++) {
                            for (int col = 0; col <= cols; col++) {
                                float x = col / (float) cols;
                                float y = top - 0.1f - row * 0.46f * rise - (row == rows ? 0.35f * x : 0);
                                float wave = 0.26f * x * Mth.sin(time * 0.35f - x * 7.2f + row * 0.4f);
                                g[row][col] = new Vec3(x * 1.7f, y + wave * 0.4f, wave);
                            }
                        }
                        float[] u = new float[cols + 1], al = new float[cols + 1], ew = new float[cols + 1];
                        for (int col = 0; col <= cols; col++) {
                            u[col] = col / (float) cols * 1.4f - time * 0.01f;
                            al[col] = hold * 0.9f;
                            ew[col] = 0.12f;
                        }
                        for (int row = 0; row < rows; row++) {
                            Skins.strip(pose, buffers, ModRenderTypes.energy(fx.look.skin()), g[row], g[row + 1], u, al, body, row / (float) rows, (row + 1f) / rows);
                        }
                        Vec3[] outer = new Vec3[rows + 1];
                        float[] ow = new float[rows + 1], oa = new float[rows + 1];
                        for (int row = 0; row <= rows; row++) {
                            outer[row] = g[row][cols];
                            ow[row] = 0.12f;
                            oa[row] = hold;
                        }
                        Skins.ribbon(pose, buffers, camera, base, ModRenderTypes.energy(Skins.BEAM), g[0], ew, al, light, 0, 0);
                        Skins.ribbon(pose, buffers, camera, base, ModRenderTypes.energy(Skins.BEAM), g[rows], ew, al, light, 0, 0);
                        Skins.ribbon(pose, buffers, camera, base, ModRenderTypes.energy(Skins.BEAM), outer, ow, oa, light, 0, 0);
                    }
                    // the ground it holds
                    disc(pose, buffers, CIRCLE, 0.05f, p.size() * rise, time * 0.8f, c, hold * 0.8f);
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.07, 0), p.size() * rise, 0.16f, light, hold * 0.8f, 0, 1);
                    float beat = (age % 1.2f) / 1.2f;
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.08, 0), p.size() * beat, 0.2f, light, hold * (1 - beat) * 0.9f, 0, 1);
                    disc(pose, buffers, GLOW, 0.03f, p.size() * 1.05f, 0, c, hold * 0.3f);
                }
                case BLADES -> {
                    int n = 6;
                    // their owner sees them faintly in first person: they pass right in front of the eyes
                    float seen = self ? hold * 0.3f : hold;
                    for (int i = 0; i < n; i++) {
                        float ang = time * 0.42f + i * Mth.TWO_PI / n;
                        float bob = 0.2f * Mth.sin(time * 0.2f + i * 1.3f);
                        Vec3 mid = new Vec3(Mth.cos(ang) * p.size(), 1.05f + bob, Mth.sin(ang) * p.size());
                        // each a blade of light flying point first along its path, tip a little down, a wake behind it
                        Vec3 dir = new Vec3(-Mth.sin(ang), -0.35f, Mth.cos(ang)).normalize();
                        int m = 6;
                        Vec3[] pts = new Vec3[m + 1];
                        float[] w = new float[m + 1], al = new float[m + 1];
                        for (int j = 0; j <= m; j++) {
                            float k = j / (float) m;
                            float a2 = ang - 0.75f + 0.62f * k;
                            pts[j] = new Vec3(Mth.cos(a2) * p.size(), 1.05f + bob + 0.25f * (1 - k), Mth.sin(a2) * p.size());
                            w[j] = 0.3f * k;
                            al[j] = seen * 0.5f * k;
                        }
                        Skins.ribbon(pose, buffers, camera, at, ModRenderTypes.energy(fx.look.skin()), pts, w, al, fx.look.body(c), -time * 0.2f, 0.6f);
                        Skins.lance(pose, buffers, camera, at, mid.add(dir.scale(0.75)), dir, 1.5f, 0.3f, 0, fx.look, c, seen, -time * 0.2f);
                    }
                }
                case CRACK -> {
                    Vec3 cc = fx.target().subtract(at);
                    float grow = SkinFx.easeOut(age / 0.2f);
                    float glow = age < 0.6f ? 1 : SkinFx.fadeOut(age, 0.6f, life - 0.6f);
                    if (SkinFx.scorches(fx.look)) SkinFx.scorch(pose, buffers, cc, p.size() * 0.35f, glow, fx.seed);
                    SkinFx.cracks(pose, buffers, camera, at, cc, fx.seed, p.size(), 7, grow, glow, 0.15f + 0.45f * SkinFx.fadeOut(age, 0.1f, 0.8f),
                            fx.look, c, time);
                    SkinFx.shock(pose, buffers, camera, at, cc, p.size() * (0.2f + 1.0f * SkinFx.easeOut(age / 0.5f)), 0.3f + p.size() * 0.05f,
                            SkinFx.fadeOut(age, 0.15f, 0.45f), fx.look.light(c), fx.seed);
                }
                case BEAM -> {
                    // from the caster's right hand, so the beam is not hidden behind them or seen dead end-on
                    Vec3 hand = fx.origin;
                    if (e != null) {
                        Vec3 view = e.getViewVector(partial);
                        Vec3 right = view.cross(new Vec3(0, 1, 0));
                        if (right.lengthSqr() > 1.0e-4) right = right.normalize().scale(0.45);
                        // through the caster's own eyes it starts further out, past the arm and what it holds
                        hand = self ? e.getEyePosition(partial).add(view.scale(1.3)).add(right.scale(0.7)).add(0, -0.3, 0)
                                : e.getEyePosition(partial).add(view.scale(0.6)).add(right).add(0, -0.4, 0);
                    }
                    beam(pose, buffers, camera, at, hand.subtract(at), fx.target().subtract(at), p.size(), age, hold, time, fx);
                }
                case ORB -> {
                    Vec3 cc = fx.target().subtract(at);
                    float grow = Mth.clamp(age / 0.2f, 0, 1);
                    float pulse = 1 + 0.08f * Mth.sin(time * 0.9f);
                    orb(pose, buffers, camera, at, cc, p.size() * grow * pulse, hold, time, fx, c);
                }
                case THUNDER -> {
                    Vec3 cc = fx.target().subtract(at);
                    Vec3 sky = cc.add(fx.seed % 7 - 3, 26, fx.seed / 7 % 7 - 3);
                    float flick = age < 0.12f ? 1 : fade;
                    Skins.lightning(pose, buffers, camera, at, sky, cc, fx.seed + (long) (time / 2), 0.5f + p.size() * 0.06f, c, flick * 1.1f, 0.16f, 0.35f);
                    SkinFx.blast(pose, buffers, camera, at, cc.add(0, 0.5, 0), cc, p.size() * 0.7f, age, life, 0.7f, fx.look, c, fx.seed, 1f);
                    SkinFx.cracks(pose, buffers, camera, at, cc, fx.seed, p.size() * 0.9f, 6, SkinFx.easeOut(age / 0.08f), fade, 0.2f, fx.look, c, time);
                }
                case SHIELD -> {
                    float yaw = e != null ? e.getViewYRot(partial) : fx.yaw;
                    float pop = SkinFx.easeOut(age / 0.1f);
                    // up at once, so that even a half-second shield is seen; a very short one is a bash, thrust forward
                    float up = Mth.clamp(age / 0.1f, 0, 1) * Mth.clamp((life - age) / Math.min(0.5f, life * 0.5f), 0, 1);
                    boolean bash = p.duration() < 10;
                    // its owner looks through it: in first person only a faint rim is left, so the fight stays in view
                    if (self) up *= 0.3f;
                    Vec3 fwd = Vec3.directionFromRotation(0, yaw);
                    Vec3 front = fwd.scale(bash ? 0.95 + 1.1 * out : 0.95).add(0, 1.0, 0);
                    int glow = fx.look.light(c);
                    if (bash) {
                        // a bash is a blow, not a barrier: the air in front of the shield is shoved forward, no rings
                        Skins.cap(pose, buffers, camera, at, ModRenderTypes.energy(Skins.WIND), front, fwd, p.size() * (0.35f + 0.6f * out), 55, up * 0.8f, glow,
                                2, time * 0.05f, 0, 0.7f);
                        Skins.cap(pose, buffers, camera, at, ModRenderTypes.energy(Skins.PLASMA), front.subtract(fwd.scale(0.1)), fwd, p.size() * (0.3f + 0.5f * out),
                                50, up * 0.45f, fx.look.body(c), 1, time * 0.02f, 0, 0.6f);
                    } else {
                        // a shield of the element's light: hexes over a curved plate, bright at its rim
                        Skins.cap(pose, buffers, camera, at, ModRenderTypes.energy(Skins.PLASMA), front.subtract(fwd.scale(0.02)), fwd, p.size() * pop, 40,
                                up * 0.4f, fx.look.body(c), 1, time * 0.01f, 0, 0.5f);
                        Skins.cap(pose, buffers, camera, at, ModRenderTypes.energy(Skins.HEX), front, fwd, p.size() * pop, 40, up, glow, 3,
                                time * 0.003f, 0, 0.6f);
                        if (!self) Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), front.add(fwd.scale(0.1)), p.size() * 0.5f * pop, glow,
                                up * (0.5f + 0.2f * Mth.sin(time * 0.4f)), 45);
                    }
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
                    int body = fx.look.body(c);
                    // the way it came, burning out behind it
                    Vec3[] trail = new Vec3[7];
                    float[] tw = new float[7], ta = new float[7];
                    for (int i = 0; i < 7; i++) {
                        float kb = Math.max(0, k - i * 0.035f);
                        trail[i] = from.lerp(to, kb).add(0, Mth.sin(kb * Mth.PI) * arc, 0);
                        tw[i] = 0.6f * big * (1 - i / 6f);
                        ta[i] = a * 0.55f * (1 - i / 6f);
                    }
                    Skins.ribbon(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), trail, tw, ta, c, 0, 0);
                    dot(pose, buffers, camera, (float) head.x, (float) head.y, (float) head.z, 0.9f * big, c, a * 0.45f);
                    // a hammer of solid light: the haft with a white-hot line through it, and across its end a heavy drum
                    // of the element's energy whose faces burn white
                    Skins.tube(pose, buffers, camera, at, ModRenderTypes.energy(Skins.PLASMA), butt, head, 6, 1, kk -> 0.075f * big, Skins.FLAT, a, body,
                            time * 0.02f, 1, 0, 0, 1, -0.4f);
                    streak(pose, buffers, camera, at, butt, head, 0.06f * big, 0xFFFFFF, a);
                    Vec3 h0 = head.subtract(cross.scale(0.45 * big)), h1 = head.add(cross.scale(0.45 * big));
                    Skins.tube(pose, buffers, camera, at, ModRenderTypes.energy(fx.look.skin()), h0, h1, 12, 1, kk -> 0.27f * big, Skins.FLAT, a,
                            body, time * 0.03f, 1, 0, 0, 1.2f, -0.6f);
                    for (int f = 0; f < 2; f++) {
                        Vec3 outward = f == 0 ? cross.scale(-1) : cross;
                        Vec3 hf = f == 0 ? h0 : h1;
                        pose.pushPose();
                        pose.translate(hf.x, hf.y, hf.z);
                        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), new Vector3f((float) outward.x, (float) outward.y, (float) outward.z)));
                        Skins.disk(pose, buffers, ModRenderTypes.energy(fx.look.skin()), 0.27f * big, body, a, 1, 1, time * 0.01f, 0);
                        pose.mulPose(Axis.XP.rotationDegrees(90));
                        Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), Vec3.ZERO, 0.25f * big, 0.1f * big, 0xFFFFFF, a, 0, 1);
                        pose.popPose();
                    }
                    Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), h0, 0.32f * big, 0xFFFFFF, a * 0.85f, time * 3);
                    Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), h1, 0.32f * big, 0xFFFFFF, a * 0.85f, -time * 3);
                }
                case DISCUS -> {
                    // edge-on a flat disc would vanish, so it is drawn facing the camera and spun
                    Vec3 from = fx.origin.add(0, e != null ? e.getBbHeight() * 0.6 : 1, 0).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    float flight = Math.max(0.1f, p.duration() / 20f);
                    float k = Mth.clamp(age / flight, 0, 1);
                    Vec3 pos = from.lerp(to, k);
                    float a = age <= flight ? 1 : Mth.clamp(1 - (age - flight) / 0.05f, 0, 1);
                    float r = 0.5f * p.size();
                    Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), from.lerp(to, Math.max(0, k - 0.35f)), pos, 4, 0.36f * p.size(),
                            kk -> kk, a * 0.7f, kk -> kk, c, 0, 0);
                    dot(pose, buffers, camera, (float) pos.x, (float) pos.y, (float) pos.z, r * 1.6f, c, a * 0.4f);
                    // a wheel of solid light: a plate of the element's energy, a white-hot rim and an inner ring
                    pose.pushPose();
                    pose.translate(pos.x, pos.y, pos.z);
                    pose.mulPose(camera.rotation());
                    pose.mulPose(Axis.ZP.rotationDegrees(time * 30));
                    Skins.disk(pose, buffers, ModRenderTypes.energy(fx.look.skin()), r, fx.look.body(c), a * 0.85f, 0.6f, 1.2f, 0, 0);
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), Vec3.ZERO, r, r * 0.3f, light, a, 0, 1);
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), Vec3.ZERO, r * 0.5f, r * 0.16f, 0xFFFFFF, a * 0.8f, 0, 1);
                    pose.popPose();
                    Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), pos, r * 0.75f, 0xFFFFFF, a * 0.9f, time * 30);
                }
                case CHAIN -> {
                    Vec3 from = (e != null ? e.getPosition(partial).add(0, e.getBbHeight() * 0.6, 0) : fx.origin).subtract(at);
                    Vec3 to = fx.target().subtract(at);
                    double len = to.subtract(from).length();
                    if (len < 0.2) break;
                    float reach = Mth.clamp(age / 0.15f, 0, 1);
                    // the chain hangs a little in the middle
                    double sag = Math.min(0.6, len * 0.05);
                    float size = p.size();
                    int body = fx.look.body(c);
                    // a glow along it
                    Vec3[] glow = new Vec3[11];
                    float[] gw = new float[11], ga = new float[11];
                    for (int i = 0; i <= 10; i++) {
                        glow[i] = chainAt(from, to, reach * i / 10.0, sag);
                        gw[i] = 0.6f * size;
                        ga[i] = hold * 0.35f;
                    }
                    Skins.ribbon(pose, buffers, camera, at, ModRenderTypes.lightTexture(STREAK), glow, gw, ga, c, 0, 0);
                    Vec3 dir = to.subtract(from).scale(1 / len);
                    Vec3 face = dir.cross(camera.getPosition().subtract(at.add(from.lerp(to, 0.5))));
                    if (face.lengthSqr() < 1.0e-6) face = dir.cross(new Vec3(0, 1, 0));
                    if (face.lengthSqr() < 1.0e-6) face = new Vec3(1, 0, 0);
                    face = face.normalize();
                    if (fx.look == Skins.Look.NATURE) {
                        // a vine: two green strands twisting round each other out to the mark
                        Vec3 up2 = dir.cross(face);
                        int segs = Math.max(8, (int) (len * 4));
                        int shown = Math.max(2, Math.round(segs * reach) + 1);
                        for (int s = 0; s < 2; s++) {
                            Vec3[] pts = new Vec3[shown];
                            float[] w = new float[shown], al = new float[shown], core = new float[shown];
                            for (int i = 0; i < shown; i++) {
                                double kk = i / (double) segs;
                                double turn = kk * len * 2.4 + s * Math.PI;
                                pts[i] = chainAt(from, to, kk, sag).add(face.scale(Math.cos(turn) * 0.13 * size)).add(up2.scale(Math.sin(turn) * 0.13 * size));
                                w[i] = 0.17f * size;
                                core[i] = 0.06f * size;
                                al[i] = hold;
                            }
                            Skins.ribbon(pose, buffers, camera, at, ModRenderTypes.energy(Skins.PLASMA), pts, w, al, body, time * 0.01f + s * 0.5f, 1.2f);
                            Skins.ribbon(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), pts, core, al, light, 0, 0);
                        }
                    } else {
                        // links of solid light, each through the last, turned alternately face on and half away
                        float pitch = 0.4f * size;
                        int links = Math.max(2, (int) (len / pitch));
                        for (int i = 0; i <= links * reach; i++) {
                            double kk = i / (double) links;
                            Vec3 g = chainAt(from, to, kk, sag);
                            Vec3 along = chainAt(from, to, Math.min(1, kk + 0.01), sag).subtract(chainAt(from, to, Math.max(0, kk - 0.01), sag)).normalize();
                            Vec3 flat = along.cross(camera.getPosition().subtract(at.add(g)));
                            if (flat.lengthSqr() < 1.0e-6) flat = along.cross(new Vec3(0, 1, 0));
                            if (flat.lengthSqr() < 1.0e-6) continue;
                            flat = flat.normalize();
                            Vec3 across = i % 2 == 0 ? flat : flat.scale(0.35).add(along.cross(flat).scale(0.94));
                            link(pose, buffers, g, along, across, pitch * 1.5f, pitch * 0.85f, 0.1f * size, i % 2 == 0 ? light : body, hold);
                        }
                    }
                    Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), to, size * 0.6f, light, hold * reach, time * 5);
                }
                case GEYSER -> {
                    Vec3 cc = fx.target().subtract(at);
                    SkinFx.geyser(pose, buffers, camera, at, cc, p.size(), 0.28f + p.size() * 0.07f, age, life, fx.look, c, fx.seed, time);
                }
                case QUAKE -> {
                    Vec3 cc = fx.target().subtract(at);
                    for (int i = 0; i < 3; i++) {
                        float k = ((age + i * 0.3f) % 0.9f) / 0.9f;
                        SkinFx.shock(pose, buffers, camera, at, cc, p.size() * k, 0.25f + 0.35f * (1 - k), hold * (1 - k), i == 1 ? fx.look.light(c) : c, fx.seed + i);
                    }
                    // the ground keeps splitting: cracks opening in new places every few ticks
                    long wave = (long) (time / 6);
                    float k = (time / 6) % 1f;
                    SkinFx.cracks(pose, buffers, camera, at, cc, fx.seed + wave * 31, p.size() * 0.9f, 5, SkinFx.easeOut(k / 0.3f), hold * (1 - k),
                            0.3f * (1 - k), fx.look, c, time);
                }
                case HALO -> {
                    if (e == null || self) break;
                    float r = 0.34f * p.size();
                    float h = e.getBbHeight() + 0.3f + 0.03f * Mth.sin(time * 0.15f);
                    float appear = 1 - (1 - Mth.clamp(age / 0.25f, 0, 1)) * (1 - Mth.clamp(age / 0.25f, 0, 1));
                    // it leans towards whoever looks at it, so that it is a ring from every side and never just a line
                    Vec3 toEye = camera.getPosition().subtract(at.add(0, h, 0));
                    float face = (float) Mth.atan2(toEye.x, toEye.z);
                    pose.pushPose();
                    pose.translate(0, h, 0);
                    pose.mulPose(Axis.YP.rotation(face));
                    pose.mulPose(Axis.XP.rotationDegrees(28));
                    // a hoop of solid light: a band of the element's energy with a white-hot line round it, a thin ring
                    // inside it, and a soft glow under both
                    disc(pose, buffers, GLOW, -0.01f, r * 1.5f, 0, c, hold * 0.35f);
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.PLASMA), Vec3.ZERO, r * appear, r * 0.34f, fx.look.body(c), hold * 0.8f,
                            time * 0.01f, 3);
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), Vec3.ZERO, r * appear, r * 0.24f, light, hold, 0, 1);
                    Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), Vec3.ZERO, r * 0.74f * appear, r * 0.07f, 0xFFFFFF, hold * 0.8f, 0, 1);
                    pose.popPose();
                    // and two glints running round it (the same turns as the pose above, worked out by hand so they face the camera)
                    for (int i = 0; i < 2; i++) {
                        float th = time * 0.09f + i * Mth.PI;
                        Vec3 g = new Vec3(Mth.sin(th) * r * appear, 0, Mth.cos(th) * r * appear).xRot(-28 * Mth.DEG_TO_RAD).yRot(face).add(0, h, 0);
                        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), g, r * 0.45f, 0xFFFFFF, hold * 0.9f, time * 4);
                    }
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
                    int feathers = 7;
                    Vec3[] roots = new Vec3[feathers * 2], tips = new Vec3[feathers * 2], bones = new Vec3[6];
                    for (int w = 0; w < 2; w++) {
                        Vec3 outward = side.scale(w * 2 - 1);
                        Vec3 shoulder = fwd.scale(-0.28).add(outward.scale(0.12)).add(0, tall * 0.74, 0);
                        // the arm of the wing goes up and out, then out; the feathers hang from it
                        Vec3 elbow = shoulder.add(wingDir(outward, fwd, 0.95f + flap).scale(0.7 * span * open));
                        Vec3 wrist = elbow.add(wingDir(outward, fwd, 0.2f + flap * 0.6f).scale(0.75 * span * open));
                        bones[w * 3] = shoulder;
                        bones[w * 3 + 1] = elbow;
                        bones[w * 3 + 2] = wrist;
                        for (int i = 0; i < feathers; i++) {
                            float u = (i + 1f) / feathers;
                            Vec3 root = u < 0.45f ? shoulder.lerp(elbow, u / 0.45f) : elbow.lerp(wrist, (u - 0.45f) / 0.55f);
                            // the inner feathers hang down, the outer ones reach out
                            roots[w * feathers + i] = root;
                            tips[w * feathers + i] = root.add(wingDir(outward, fwd, -1.35f + u + flap * 0.5f).scale(span * (0.5f + 0.6f * u) * open));
                        }
                    }
                    int body = fx.look.body(c);
                    dot(pose, buffers, camera, (float) (-fwd.x * 0.28), tall * 0.74f, (float) (-fwd.z * 0.28), 0.6f * span, c, hold * 0.35f);
                    // every feather a long leaf of the element's energy with a white-hot quill down it, the bones of the
                    // wing over them
                    for (int i = 0; i < roots.length; i++) {
                        Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(fx.look.skin()), roots[i], tips[i], 5, 0.38f * span, SkillWorldFx::feather,
                                hold * 0.6f, Skins.FLAT, body, time * 0.008f + i * 0.37f, 0);
                    }
                    for (int i = 0; i < roots.length; i++) {
                        Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), roots[i], tips[i], 5, 0.17f * span, SkillWorldFx::feather,
                                hold, Skins.FLAT, light, 0, 0);
                    }
                    for (int w = 0; w < 2; w++) {
                        streak(pose, buffers, camera, at, bones[w * 3], bones[w * 3 + 1], 0.24f * span, light, hold);
                        streak(pose, buffers, camera, at, bones[w * 3 + 1], bones[w * 3 + 2], 0.2f * span, light, hold);
                    }
                }
                case BUBBLE -> {
                    if (e == null) break;
                    float r = p.size() * (1 + 0.035f * Mth.sin(time * 0.2f)) * (0.6f + 0.4f * Mth.clamp(age / 0.2f, 0, 1));
                    float mid = e.getBbHeight() * 0.5f;
                    if (self) {
                        // from the inside only its rim on the ground is seen
                        Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.05, 0), r, 0.14f, light, hold * 0.45f, 0, 1);
                        break;
                    }
                    Vec3 m = new Vec3(0, mid, 0);
                    // a skin of water round them, clear in the middle and thick at its edges, its surface running
                    Skins.sphere(pose, buffers, camera, at, ModRenderTypes.energy(Skins.WATER), m, r, 12, 18, hold * 0.8f, fx.look.body(c),
                            time * 0.004f, 2, time * 0.006f, 1, 1f);
                    dot(pose, buffers, camera, 0, mid, 0, r * 1.05f, c, hold * 0.12f);
                    // the glints that make it a bubble: one up on the left, a small one down on the right
                    Vector3f cl = camera.getLeftVector(), cu = camera.getUpVector();
                    Vec3 gl = new Vec3(cl.x() * 0.42f + cu.x() * 0.48f, cl.y() * 0.42f + cu.y() * 0.48f, cl.z() * 0.42f + cu.z() * 0.48f).scale(r);
                    Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), m.add(gl), r * 0.28f, 0xFFFFFF, hold * 0.9f, 20);
                    Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), m.subtract(gl.scale(0.9)), r * 0.13f, 0xFFFFFF, hold * 0.7f, 60);
                }
                case STREAM -> {
                    if (e == null) break;
                    Vec3 src = fx.target().subtract(at);
                    Vec3 dst = new Vec3(0, e.getBbHeight() * 0.6, 0);
                    double dist = dst.subtract(src).length();
                    if (dist < 0.3) break;
                    float a = Mth.clamp(age / 0.08f, 0, 1) * Mth.clamp((life - age) / 0.15f, 0, 1);
                    double arch = Math.min(0.9, dist * 0.14);
                    // a stream of the stuff itself flowing along the arch from the one to the other: thick in the middle of
                    // its run, thin where it leaves and where it pours in, a bright line down it
                    int segs = 14;
                    Vec3[] pts = new Vec3[segs + 1];
                    float[] w = new float[segs + 1], core = new float[segs + 1], al = new float[segs + 1];
                    for (int i = 0; i <= segs; i++) {
                        float u = i / (float) segs;
                        pts[i] = src.lerp(dst, u).add(0, Math.sin(u * Math.PI) * arch, 0);
                        float swell = 0.55f + 0.45f * Mth.sin(u * Mth.PI) + 0.15f * Mth.sin(u * 9 - age * 14);
                        w[i] = 0.34f * p.size() * swell;
                        core[i] = w[i] * 0.3f;
                        al[i] = a * Math.min(1, Math.min(u, 1 - u) * 8 + 0.3f);
                    }
                    Skins.ribbon(pose, buffers, camera, at, ModRenderTypes.energy(Skins.WATER), pts, w, al, fx.look.body(c), -age * 2.2f, 0.9f);
                    Skins.ribbon(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), pts, core, al, light, 0, 0);
                    // drops of it running down
                    int n = Mth.clamp((int) (dist * 1.2), 4, 9);
                    Vec3[] drops = new Vec3[n];
                    for (int i = 0; i < n; i++) {
                        float k = (i / (float) n + age * 1.6f) % 1f;
                        drops[i] = src.lerp(dst, k).add(0, Math.sin(k * Math.PI) * arch, 0);
                    }
                    motes(pose, buffers, camera, drops, 0.15f * p.size(), light, a, time * 4);
                    Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), dst, 0.45f * p.size(), light, a * 0.6f, time * 2);
                }
                case ORBIT -> {
                    if (e == null) break;
                    int n = Math.max(1, Math.round(p.size()));
                    float rad = Math.max(0.7f, e.getBbWidth() / 2 + 0.45f);
                    // their owner sees them faintly in first person: they pass right in front of the eyes
                    float seen = self ? hold * 0.5f : hold;
                    float phase = (c & 0xFF) * 0.07f;
                    Vec3[] beads = new Vec3[n];
                    for (int i = 0; i < n; i++) {
                        float ang = time * 0.16f + phase + i * Mth.TWO_PI / n;
                        // the wake each draws round its owner: a ribbon of its light along the way it came, thinning out
                        Vec3[] pts = new Vec3[6];
                        float[] w = new float[6], al = new float[6];
                        for (int j = 0; j < 6; j++) {
                            float back = ang - j * 0.12f;
                            float y = e.getBbHeight() * 0.62f + 0.12f * Mth.sin((time - j * 0.75f) * 0.13f + i * 1.9f);
                            pts[j] = new Vec3(Mth.cos(back) * rad, y, Mth.sin(back) * rad);
                            w[j] = 0.2f * (1 - j / 5f);
                            al[j] = seen * 0.8f * (1 - j / 5f);
                        }
                        beads[i] = pts[0];
                        Skins.ribbon(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), pts, w, al, c, 0, 0);
                    }
                    motes(pose, buffers, camera, beads, 0.14f, c, seen, time * 3);
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
                        // soft puffs that hide what is behind them, the way fog does, with a little light in some
                        Skins.billboard(pose, buffers, camera, ModRenderTypes.softTexture(Skins.SMOKE), new Vec3(x, y, z), half * 1.15f, i % 3 == 0 ? light : pale,
                                Math.min(1f, hold * thick * clear * 2.4f), i * 47 + time * 0.25f * (i % 2 == 0 ? 1 : -1));
                        if (i % 4 == 0) dot(pose, buffers, camera, x, y, z, half, pale, hold * thick * clear * 0.6f);
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
                        // the light it leaves behind it: a wake of its element, thinning back to the bow
                        Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(fx.look.skin()), from.add(dir.scale(Math.max(0, flown - 5 * scale))), head, 8,
                                0.34f * scale, k2 -> 0.2f + 0.8f * k2, 0.8f, k2 -> k2, fx.look.body(c), -time * 0.3f, 0.5f);
                        streak(pose, buffers, camera, at, from.add(dir.scale(Math.max(0, flown - 3 * scale))), head, 0.1f * scale, fx.look.light(c), 0.6f);
                        arrow(pose, buffers, camera, at, head, dir, scale, c, 1f);
                        break;
                    }
                    float since = age - flight;
                    float stuck = p.duration() / 20f;
                    // where it struck
                    impact(pose, buffers, camera, at, to, since, fx, 0.65f * scale, 1f);
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
                        disc(pose, buffers, SHOCK, 0.04f, (0.2f + 0.9f * since / 0.4f) * scale, 0, light, 1 - since / 0.4f);
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
                        // the ground knows first: a ring of light closing on the spot, and a thread of it down from the sky
                        disc(pose, buffers, GLOW, 0.05f, scale * (1.8f - k), 0, c, 0.3f * k);
                        Skins.ring(pose, buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.06, 0), scale * (1.7f - 1.0f * k), 0.14f + 0.1f * k, light,
                                0.3f + 0.6f * k, 0, 1);
                        streak(pose, buffers, camera, base, Vec3.ZERO, sky, 0.06f + 0.06f * k, light, 0.4f * k);
                        float d = (age - (fall - drop)) / drop;
                        if (d > 0) {
                            Vec3 head = sky.scale(1 - d);
                            Vec3 tail = sky.scale(Math.min(1, 1 - d + 0.4f));
                            // it comes down in a shaft of the element's own light
                            Skins.tube(pose, buffers, camera, base, ModRenderTypes.energy(fx.look.skin()), head, tail, 10, 4, kk -> 0.3f * scale * (1 - 0.6f * kk),
                                    kk -> 1 - kk, 0.9f, fx.look.body(c), time * 0.02f, 1, 0, -time * 0.05f, 0.2f, 0.4f);
                            streak(pose, buffers, camera, base, tail, head, 0.18f * scale, 0xFFFFFF, 0.9f);
                            arrow(pose, buffers, camera, base, head, dir, scale * 1.4f, c, 1f);
                        }
                    } else {
                        float since = age - fall;
                        float k = Mth.clamp(since / 0.9f, 0, 1);
                        float a = 1 - k;
                        // the arrow stands in the ground in the light it came down in, and the ground goes up round it
                        arrow(pose, buffers, camera, base, dir.scale(0.2 * scale), dir, scale * 1.4f, c, Mth.clamp(a * 1.6f, 0, 1));
                        SkinFx.column(pose, buffers, camera, base, Vec3.ZERO, 30, 0.45f * scale * (1 - k), a * 0.8f, fx.look, c, time);
                        SkinFx.blast(pose, buffers, camera, base, new Vec3(0, 0.5, 0), Vec3.ZERO, scale * 1.5f, since, life - fall, 0.5f, fx.look, c, fx.seed, 1f);
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
                        // through the archer's own eyes the first rings would open around their head: they start further out
                        if (rk <= 0 || rk >= 1 || (self && along < 4)) continue;
                        Vec3 g = from.add(dir.scale(along));
                        pose.pushPose();
                        pose.translate(g.x, g.y, g.z);
                        pose.mulPose(Axis.YP.rotation(turn));
                        pose.mulPose(Axis.XP.rotation(tilt));
                        pose.mulPose(Axis.ZP.rotationDegrees(time * 20 + i * 40));
                        sprite(buffers.getBuffer(ModRenderTypes.lightTexture(SHOCK)), pose.last().pose(), 0.25f + p.size() * (1 - (1 - rk) * (1 - rk)), i % 2 == 0 ? light : c, (1 - rk) * 0.8f);
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
                        disc(pose, buffers, SHOCK, 0.06f, p.size() * (0.5f + 2.2f * k), 0, light, (1 - k) * (1 - k) * 0.7f);
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
                    int body = fx.look.body(c);
                    for (int i = 0; i < 3; i++) {
                        float k = Mth.clamp((age - i * 0.03f) / 0.07f, 0, 1);
                        if (k <= 0) continue;
                        float reach = (i == 1 ? 0.62f : 0.5f) * len;
                        Vec3 top = cc.add(upward.scale(reach)).add(left.scale((i - 1) * 0.3 * len + 0.28 * len));
                        Vec3 bottom = cc.add(upward.scale(-reach)).add(left.scale((i - 1) * 0.3 * len - 0.28 * len));
                        Vec3 end = top.lerp(bottom, k);
                        // each rake a blade of light, pointed at both ends and widest past its middle, a white-hot line down it
                        Skins.line(pose, buffers, camera, at, ModRenderTypes.lightTexture(STREAK), top, end, 6, 0.55f * len, SkillWorldFx::rake, a * 0.35f,
                                Skins.FLAT, c, 0, 0);
                        Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), top, end, 6, 0.26f * len, SkillWorldFx::rake, a, Skins.FLAT,
                                body, 0, 0);
                        Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), top, end, 6, 0.08f * len, SkillWorldFx::rake, a, Skins.FLAT,
                                0xFFFFFF, 0, 0);
                    }
                    if (age < 0.12f) Skins.spikes(pose, buffers, camera, cc, fx.seed, 8, len * 0.9f, len * 0.15f, light, (1 - age / 0.12f) * 0.8f);
                }
                case SWARM -> {
                    if (e == null) break;
                    float mid = e.getBbHeight() * 0.6f;
                    Vec3[] bugs = new Vec3[22];
                    for (int i = 0; i < 22; i++) {
                        long h = (fx.seed + i) * 0x9E3779B97F4A7C15L;
                        float u = ((h >>> 12) & 0xFFFF) / 65535f, v = ((h >>> 28) & 0xFFFF) / 65535f, w = ((h >>> 44) & 0xFFFF) / 65535f;
                        // each on its own crooked little orbit, darting in and out
                        float speed = 0.5f + 0.6f * u;
                        bugs[i] = hornet(time, i, speed, v, w, p.size(), mid);
                        Skins.line(pose, buffers, camera, at, ModRenderTypes.energy(Skins.BEAM), hornet(time - 0.9f, i, speed, v, w, p.size(), mid), bugs[i], 3,
                                0.1f, kk -> 0.3f + 0.7f * kk, hold * 0.8f, kk -> kk, c, 0, 0);
                    }
                    motes(pose, buffers, camera, bugs, 0.1f, c, hold, time * 5);
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
                        // a storm cloud: dark, soft and heavy, hiding the sky behind it
                        Skins.billboard(pose, buffers, camera, ModRenderTypes.softTexture(Skins.SMOKE), new Vec3(x, y, z), 1.6f + 1.4f * w, dark,
                                Math.min(1f, a * 0.6f * thin), i * 53 + time * 0.1f * (i % 2 == 0 ? 1 : -1));
                        if (lit) dot(pose, buffers, camera, x, y, z, 1.6f + 1.3f * w, mix(c, 0xFFFFFF, 0.6f), a * 0.55f * thin);
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
                    orb(pose, buffers, camera, at, pos, r * pulse, a, time, fx, c);
                    // the arcs that reach out of it and are gone again
                    RandomSource arcs = RandomSource.create(fx.seed + (long) (time / 2));
                    for (int i = 0; i < 4; i++) {
                        Vec3 reach = new Vec3(arcs.nextDouble() - 0.5, arcs.nextDouble() - 0.5, arcs.nextDouble() - 0.5).normalize().scale(r * (1.6 + arcs.nextDouble() * 1.6));
                        Skins.lightning(pose, buffers, camera, at, pos, pos.add(reach), arcs.nextLong(), 0.07f, c, a, 0.3f, 0);
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
                    // the shadow itself: dark smoke rolling off where they stood, and a dark breath running out over the ground
                    Vec3 o = fx.origin.subtract(at);
                    SkinFx.smoke(pose, buffers, camera, o.add(0, 0.8, 0), 1.8f, age, fx.seed, 0x1A1424, Mth.clamp((life - age) / 0.3f, 0, 1));
                    if (age < 0.6f) {
                        pose.pushPose();
                        pose.translate(o.x, o.y, o.z);
                        disc(pose, buffers, SHOCK, 0.06f, 0.4f + 2.2f * (age / 0.6f), 0, mix(c, 0x000000, 0.4f), (1 - age / 0.6f) * 0.6f);
                        pose.popPose();
                    }
                }
            }
            pose.popPose();
        }
        Skins.flush(buffers);
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

    /**
     * A blade's cut in the pose's XZ plane, round its origin at radius {@code r}: a crescent of the skill's colour with
     * a white-hot cutting edge sweeps 160 degrees in a tenth of a second ({@code dir} 1 from the right across to the
     * left, -1 back), its tail catching up after it and the whole of it burning away; a glint on its point while it
     * moves. The element dresses it: flames trail inside a fire cut, water runs in a water one, a shadow cut is dark
     * with a bright edge, lightning crawls along a charged one.
     */
    static void blade(PoseStack pose, MultiBufferSource buffers, Camera camera, float r, float age, float life, int dir, int c, float strength,
                      Skins.Look look, float time) {
        float swing = 0.1f;
        float k = Mth.clamp(age / swing, 0, 1);
        float head = 1 - (1 - k) * (1 - k) * (1 - k);
        // the tail holds a moment once the edge has passed, then gathers up after it
        float tk = Mth.clamp((age - 0.05f) / Math.max(0.05f, life - 0.05f), 0, 1);
        float tail = Math.min(head, tk * tk * 0.85f);
        float span = 160;
        float from = -dir * span / 2;
        float aHead = from + dir * span * head;
        float aTail = from + dir * span * tail;
        float gone = Mth.clamp((age - swing - 0.04f) / Math.max(0.05f, life - swing - 0.04f), 0, 1);
        float a = strength * (1 - gone * gone);
        int body = look.body(c), light = look.light(c);
        // a soft wake of light behind the blade, then the blade itself
        float band = Mth.clamp(0.2f + 0.45f / r, 0.24f, 0.36f);
        Skins.crescent(pose, buffers, ModRenderTypes.lightTexture(ARC), r * 1.03f, r * band * 1.5f, aTail, aHead, light, a * 0.3f, 1.3f, 0, 1);
        Skins.crescent(pose, buffers, ModRenderTypes.energy(Skins.BLADE), r, r * band, aTail, aHead, body, a, 0.6f, 0, 1);
        switch (look) {
            case FIRE, WATER, AIR -> Skins.crescent(pose, buffers, ModRenderTypes.energy(look.skin()), r * 0.985f, r * band * 1.15f, aTail, aHead,
                    body, a * 0.55f, 1.2f, -time * 0.06f, 1.4f);
            case SHADOW -> Skins.crescent(pose, buffers, ModRenderTypes.energy(Skins.PLASMA), r * 0.99f, r * band * 1.25f, aTail, aHead,
                    look.body(c), a * 0.7f, 1f, -time * 0.04f, 1.2f);
            case LIGHTNING -> {
                // a few short arcs crawling along the edge, struck anew every couple of ticks
                RandomSource arcs = RandomSource.create((long) (time / 2) * 31 + dir);
                for (int i = 0; i < 3; i++) {
                    float at = Mth.lerp(0.35f + 0.6f * arcs.nextFloat(), aTail, aHead) * Mth.DEG_TO_RAD;
                    float at2 = at + dir * (0.12f + 0.15f * arcs.nextFloat());
                    Vec3 p1 = new Vec3(Mth.sin(at) * r, 0, Mth.cos(at) * r);
                    Vec3 p2 = new Vec3(Mth.sin(at2) * r * 0.85f, (arcs.nextFloat() - 0.5f) * 0.25f, Mth.cos(at2) * r * 0.85f);
                    Vector3f w1 = pose.last().pose().transformPosition((float) p1.x, (float) p1.y, (float) p1.z, new Vector3f());
                    Vector3f w2 = pose.last().pose().transformPosition((float) p2.x, (float) p2.y, (float) p2.z, new Vector3f());
                    Skins.lightning(new PoseStack(), buffers, camera, camera.getPosition(), new Vec3(w1.x(), w1.y(), w1.z()), new Vec3(w2.x(), w2.y(), w2.z()),
                            arcs.nextLong(), 0.06f, light, a, 0.3f, 0);
                }
            }
            default -> {}
        }
        if (k < 1) {
            float rad = aHead * Mth.DEG_TO_RAD;
            Vector3f tip = pose.last().pose().transformPosition(Mth.sin(rad) * r, 0, Mth.cos(rad) * r, new Vector3f());
            Skins.billboard(new PoseStack(), buffers, camera, ModRenderTypes.energy(Skins.FLARE), new Vec3(tip.x(), tip.y(), tip.z()),
                    0.3f + 0.1f * r, light, strength * (1 - k * 0.5f), 45 * k);
        }
    }

    /**
     * A beam from {@code from} to {@code to} (pose-relative; the pose origin is at {@code origin}): a tube of the
     * element's energy running out of the hand, a hot core, two coils turning round it, a glow about it, the light
     * gathered at the hand and the blow where it lands. A lightning beam is a sheaf of bolts instead of a tube.
     */
    private static void beam(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 from, Vec3 to, float size,
                             float age, float hold, float time, Fx fx) {
        int c = fx.p.color();
        Skins.Look look = fx.look;
        int body = look.body(c), light = look.light(c);
        float grow = Mth.clamp(age / 0.12f, 0, 1);
        Vec3 end = from.lerp(to, grow);
        Vec3 axis = end.subtract(from);
        double len = axis.length();
        if (len < 0.05) return;
        float pulse = 0.88f + 0.12f * Mth.sin(time * 1.7f);
        int n = Mth.clamp((int) (len * 1.5), 4, 48);
        Skins.Profile ends = k -> Math.min(1, k / 0.04f);
        // the glow about it
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.lightTexture(STREAK), from, end, n, size * 3.4f * pulse, ends, hold * 0.6f, Skins.FLAT, c, 0, 0);
        if (look == Skins.Look.LIGHTNING) {
            for (int i = 0; i < 3; i++) {
                Skins.lightning(pose, buffers, camera, origin, from, end, fx.seed + i * 7919L + (long) (time / 2), size * 0.45f, c, hold, 0.12f, 0.15f);
            }
        } else {
            // the body: the element's energy pouring out of the hand, turning as it goes
            Skins.tube(pose, buffers, camera, origin, ModRenderTypes.energy(look.skin()), from, end, 12, n, k -> size * 0.95f * pulse * ends.at(k),
                    Skins.FLAT, hold, body, time * 0.01f, 2, (float) len * 0.12f, -time * 0.09f, 0.3f, 0.65f);
        }
        // the core, white-hot
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.BEAM), from, end, n, size * 1.25f * pulse, ends, hold, Skins.FLAT, light, -time * 0.22f, 0.35f);
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.BEAM), from, end, n, size * 0.45f, ends, hold, Skins.FLAT, 0xFFFFFF, -time * 0.35f, 0.5f);
        // two coils winding round it
        Vec3 d = axis.scale(1 / len);
        Vec3 e1 = d.cross(Math.abs(d.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 e2 = d.cross(e1);
        int m = Mth.clamp((int) (len * 6), 8, 160);
        for (int s = 0; s < 2; s++) {
            Vec3[] pts = new Vec3[m + 1];
            float[] w = new float[m + 1], al = new float[m + 1];
            for (int i = 0; i <= m; i++) {
                float k = i / (float) m;
                double ang = k * len * 2.2 - time * 0.55 + s * Math.PI;
                double rad = size * (1.2 + 0.12 * Math.sin(k * len * 0.7 + time * 0.2));
                pts[i] = from.add(axis.scale(k)).add(e1.scale(Math.cos(ang) * rad)).add(e2.scale(Math.sin(ang) * rad));
                w[i] = size * 0.2f;
                al[i] = hold * 0.85f * ends.at(k) * Math.min(1, (1 - k) / 0.05f + 0.3f);
            }
            Skins.ribbon(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.BEAM), pts, w, al, light, -time * 0.3f, 0.6f);
        }
        // the light gathered at the hand, and the blow where it lands
        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), from, size * 1.6f * pulse, light, hold, time * 6);
        dot(pose, buffers, camera, (float) from.x, (float) from.y, (float) from.z, size * 1.4f, c, hold * 0.7f);
        if (grow >= 1) {
            Skins.spikes(pose, buffers, camera, end, fx.seed + (long) (time / 2), 9, 0.9f + size * 2.4f, 0.2f + size * 0.3f, light, hold);
            Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), end, 0.6f + size * 1.6f * pulse, light, hold, -time * 9);
            dot(pose, buffers, camera, (float) end.x, (float) end.y, (float) end.z, 0.8f + size * 2.2f, c, hold * 0.6f);
        }
    }

    /**
     * A ball of the element's energy at {@code at}: a churning shell bright at its edges, a hot heart, a glow about
     * it. Shadow is a dark ball with a burning rim.
     */
    private static void orb(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 at, float radius, float alpha, float time, Fx fx, int c) {
        if (radius <= 0.01f || alpha <= 0.01f) return;
        Skins.Look look = fx.look;
        dot(pose, buffers, camera, (float) at.x, (float) at.y, (float) at.z, radius * 2.6f, c, alpha * 0.45f);
        Skins.sphere(pose, buffers, camera, origin, ModRenderTypes.energy(look.skin()), at, radius, 10, 16, alpha, look.body(c),
                time * 0.006f, 2, -time * 0.009f, 1, 0.7f);
        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.ORB), at, radius * 1.02f, look.light(c), alpha * (look.dark() ? 0.6f : 0.9f), time * 2.5f);
        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), at, radius * 0.9f, 0xFFFFFF, alpha * 0.8f, -time * 4);
    }

    /**
     * The blow where something lands at {@code at} (pose-relative), {@code since} seconds ago: hard spikes of light
     * and a star at once, a shell of the element's energy that runs out and burns away, a glow. {@code size} scales it.
     */
    private static void impact(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 at, float since, Fx fx, float size, float alpha) {
        if (since < 0) return;
        int c = fx.p.color();
        Skins.Look look = fx.look;
        int light = look.light(c);
        float k = Mth.clamp(since / 0.3f, 0, 1);
        float o = 1 - (1 - k) * (1 - k);
        // lightning lands as a crack and a flash more than a blast: its shell stays small
        float shell = look == Skins.Look.LIGHTNING ? 0.55f : 1f;
        if (k < 1) {
            Skins.sphere(pose, buffers, camera, origin, ModRenderTypes.energy(look.skin()), at, size * shell * (0.2f + 0.7f * o), 8, 12,
                    alpha * 0.7f * (1 - k) * (1 - k), look.body(c), (fx.seed & 7) * 0.13f, 1, since * 0.6f, 1, 0.9f);
        }
        if (since < 0.12f) {
            float f = 1 - since / 0.12f;
            Skins.spikes(pose, buffers, camera, at, fx.seed, 10, size * (1.2f + 0.8f * (1 - f)), size * 0.3f, light, alpha * f);
            Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), at, size * (0.9f + 0.4f * (1 - f)), 0xFFFFFF, alpha * f, fx.seed % 90);
        }
        if (k < 1) {
            dot(pose, buffers, camera, (float) at.x, (float) at.y, (float) at.z, size * (1.2f + 1.4f * o) * shell, c, alpha * (1 - k) * 0.7f);
        }
    }

    /**
     * A sweep of light round the pose origin in its XZ plane: the band between {@code radius - width} and
     * {@code radius}, from angle {@code from} (its tail, thin and faded) to {@code to} (its head, full), in degrees
     * from +z towards +x. The bright edge of the arc picture runs along the outside, as a blade's edge would.
     */
    static void arc(PoseStack pose, MultiBufferSource buffers, float radius, float width, float from, float to, int rgb, float alpha) {
        if (alpha <= 0.01f || Math.abs(to - from) < 0.5f || width <= 0.005f) return;
        int n = Mth.clamp((int) (Math.abs(to - from) / 5), 4, 48);
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(ARC));
        Matrix4f m = pose.last().pose();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float a = Math.min(1f, alpha) * 255;
        for (int i = 0; i < n; i++) {
            float k0 = i / (float) n, k1 = (i + 1) / (float) n;
            float a0 = Mth.lerp(k0, from, to) * Mth.DEG_TO_RAD, a1 = Mth.lerp(k1, from, to) * Mth.DEG_TO_RAD;
            float in0 = radius - width * (0.2f + 0.8f * k0), in1 = radius - width * (0.2f + 0.8f * k1);
            int al0 = Mth.clamp(Math.round(a * (float) Math.pow(k0, 1.3)), 0, 255), al1 = Mth.clamp(Math.round(a * (float) Math.pow(k1, 1.3)), 0, 255);
            float s0 = Mth.sin(a0), c0 = Mth.cos(a0), s1 = Mth.sin(a1), c1 = Mth.cos(a1);
            out.addVertex(m, s0 * in0, 0, c0 * in0).setUv(k0, 1).setColor(r, g, b, al0);
            out.addVertex(m, s1 * in1, 0, c1 * in1).setUv(k1, 1).setColor(r, g, b, al1);
            out.addVertex(m, s1 * radius, 0, c1 * radius).setUv(k1, 0).setColor(r, g, b, al1);
            out.addVertex(m, s0 * radius, 0, c0 * radius).setUv(k0, 0).setColor(r, g, b, al0);
        }
    }

    /**
     * Beads of light at {@code at}: small orbs of the colour with a white-hot heart, solid against a bright sky. All the
     * orbs first, then all the hearts, so a swarm of them is two draws.
     */
    private static void motes(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3[] at, float half, int rgb, float alpha, float roll) {
        if (alpha <= 0.01f) return;
        for (Vec3 q : at) dot(pose, buffers, camera, (float) q.x, (float) q.y, (float) q.z, half * 2.6f, rgb, alpha * 0.55f);
        for (int i = 0; i < at.length; i++) {
            Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.ORB), at[i], half, rgb, Math.min(1f, alpha), roll + i * 37);
        }
        for (int i = 0; i < at.length; i++) {
            Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), at[i], half * 0.8f, 0xFFFFFF, Math.min(1f, alpha * 0.7f), -roll - i * 23);
        }
    }

    /** A feather along its length (k 0 at its root): broad soon after the root, tapering to a point at its tip. */
    private static float feather(float k) {
        return (float) Math.pow(Math.max(0, Mth.sin(Mth.PI * (0.12f + 0.88f * k))), 0.6);
    }

    /** A claw's rake along its length: pointed where it starts, widest past its middle, pointed where it ends. */
    private static float rake(float k) {
        return (float) Math.pow(Math.max(0, Mth.sin(k * Mth.PI)), 0.6) * (0.6f + 0.4f * k);
    }

    /** A point {@code k} (0..1) of the way along a chain from {@code from} to {@code to}, hanging {@code sag} in the middle. */
    private static Vec3 chainAt(Vec3 from, Vec3 to, double k, double sag) {
        return from.lerp(to, k).add(0, -Math.sin(k * Math.PI) * sag, 0);
    }

    /**
     * A link of a chain at {@code g}: an oval hoop of light {@code length} long along {@code along} and {@code width}
     * wide along {@code across}, its band {@code thick}.
     */
    private static void link(PoseStack pose, MultiBufferSource buffers, Vec3 g, Vec3 along, Vec3 across, float length, float width, float thick,
                             int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        int n = 16;
        Vec3[] in = new Vec3[n + 1], out = new Vec3[n + 1];
        float[] u = new float[n + 1], al = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            double th = Math.PI * 2 * i / n;
            double cs = Math.cos(th), sn = Math.sin(th);
            in[i] = g.add(along.scale(cs * (length - thick) / 2)).add(across.scale(sn * Math.max(0.01, width - thick) / 2));
            out[i] = g.add(along.scale(cs * (length + thick) / 2)).add(across.scale(sn * (width + thick) / 2));
            u[i] = i / (float) n;
            al[i] = alpha;
        }
        Skins.strip(pose, buffers, ModRenderTypes.energy(Skins.BEAM), in, out, u, al, rgb);
    }

    /**
     * A streak of light from a to b (relative to the current pose origin, which sits at {@code origin} in the
     * world), turned to face the camera.
     */
    static void streak(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 origin, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        if (alpha <= 0.01f || width <= 0.005f) return;
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0e-4) return;
        // a streak running through the eye (a fountain at your own feet, seen in first person) would be turned edge-on
        // to nothing and smear across the view: it fades out as the eye comes close to the middle of it. Near an end
        // (a beam leaving your own hand) it stays as it is.
        Vec3 eye = camera.getPosition().subtract(origin);
        double along = Mth.clamp(eye.subtract(a).dot(dir) / dir.lengthSqr(), 0, 1);
        if (along > 0.02 && along < 0.98) {
            double off = eye.distanceTo(a.add(dir.scale(along)));
            alpha *= (float) Mth.clamp((off - 0.2) / (0.6 + width), 0, 1);
            if (alpha <= 0.01f) return;
        }
        Vec3 mid = origin.add(a.add(b).scale(0.5));
        Vec3 view = camera.getPosition().subtract(mid);
        Vec3 side = dir.cross(view);
        if (side.lengthSqr() < 1.0e-6) side = dir.cross(new Vec3(0, 1, 0));
        side = side.normalize().scale(width / 2);
        // a line of energy with a hot core: its colour as a body, so it reads against a bright sky too
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.energy(Skins.BEAM));
        Matrix4f m = pose.last().pose();
        int al = Mth.clamp(Math.round(Math.min(1f, alpha) * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, bl = rgb & 0xFF;
        out.addVertex(m, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z)).setUv(0, 1).setColor(r, g, bl, al);
        out.addVertex(m, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z)).setUv(1, 1).setColor(r, g, bl, al);
        out.addVertex(m, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z)).setUv(1, 0).setColor(r, g, bl, al);
        out.addVertex(m, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z)).setUv(0, 0).setColor(r, g, bl, al);
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
        // a shaft of light with a white-hot core, a leaf-shaped head, swept vanes and a glint on its point
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.lightTexture(STREAK), tail, head, 4, 0.55f * scale, k -> 0.4f + 0.6f * k, alpha * 0.45f,
                Skins.FLAT, rgb, 0, 0);
        streak(pose, buffers, camera, origin, tail, head, 0.2f * scale, rgb, alpha * 0.9f);
        streak(pose, buffers, camera, origin, tail, head, 0.07f * scale, 0xFFFFFF, alpha);
        Skins.line(pose, buffers, camera, origin, ModRenderTypes.energy(Skins.BEAM), head.subtract(dir.scale(0.4 * scale)), head, 4, 0.36f * scale,
                k -> k < 0.4f ? 0.35f + 0.65f * k / 0.4f : (1 - k) / 0.6f, alpha, Skins.FLAT, pale, 0, 0);
        for (int s = -1; s <= 1; s += 2) {
            streak(pose, buffers, camera, origin, head.subtract(dir.scale(0.3 * scale)).add(flat.scale(0.13 * scale * s)), head, 0.09f * scale, pale, alpha);
            for (int i = 0; i < 2; i++) {
                Vec3 root = tail.add(dir.scale((0.08 + 0.13 * i) * scale));
                streak(pose, buffers, camera, origin, root.subtract(dir.scale(0.16 * scale)).add(flat.scale(0.12 * scale * s)), root, 0.07f * scale, pale, alpha * 0.9f);
            }
        }
        dot(pose, buffers, camera, (float) head.x, (float) head.y, (float) head.z, 0.22f * scale, pale, alpha * 0.8f);
        Skins.billboard(pose, buffers, camera, ModRenderTypes.energy(Skins.FLARE), head, 0.22f * scale, pale, alpha * 0.9f, 45);
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
