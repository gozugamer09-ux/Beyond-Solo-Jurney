package com.beyondsolo.client.skill;

import com.beyondsolo.client.fx.Aura;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.client.skill.ArtFx.Art;
import com.beyondsolo.client.skill.ArtFx.Ctx;
import com.beyondsolo.client.skill.ArtFx.Fx;
import com.beyondsolo.item.arsenal.Signature;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.beyondsolo.client.skill.ArtFx.in;
import static com.beyondsolo.client.skill.ArtFx.out;
import static com.beyondsolo.client.skill.ArtFx.rnd;
import static com.beyondsolo.client.skill.ArtFx.win;

/**
 * The divine arts' looks, the biggest things a weapon does. Ruinbringer: a cut of fire from the sky to the ground and
 * the earth split open twelve blocks ahead, erupting. Calamity Fang: a step too fast to see, a black wake of it, and
 * then every wound along it opens at once. Eye of the Insatiable: a black star fed at the staff's head, thrown, a
 * singularity that eats the light round it, then collapses and bursts. Colossus Aegis: a hammer of light the size of
 * a tower comes down, a bulwark stands round the guardian, and later breaks outward. Branch of the World Tree: roots of
 * light, then the World Tree itself rises with the healer at its heart. Starfall Longbow: a star gathers on the arrow,
 * the arrow becomes a comet, and the sky answers where it lands: seven stars, then the great one.
 */
final class ArtDivine {
    static void register(Map<Signature, Art> art) {
        art.put(Signature.CATACLYSM_CLEAVE, new CataclysmCleave());
        art.put(Signature.DRAGONS_WRATH, new DragonsWrath());
        art.put(Signature.DEVOURING_STAR, new DevouringStar());
        art.put(Signature.COLOSSUS_STANCE, new ColossusStance());
        art.put(Signature.VERDANT_BLESSING, new VerdantBlessing());
        art.put(Signature.STARFALL, new Starfall());
    }

    // ------------------------------------------------------------------ shared helpers

    /**
     * A cut opening in the air at {@code centre}, square to the eye from wherever it is seen: a straight slash
     * {@code len} long at {@code angle} degrees across the screen, drawn across in a few frames, a dark body with a
     * bright edge, burning away by {@code life}.
     */
    static void slit(Ctx c, Vec3 centre, float angle, float len, float age, float life, int body, int edge, float strength) {
        if (age < 0 || age > life || strength <= 0.01f) return;
        Vector3f l = c.camera.getLeftVector(), u = c.camera.getUpVector();
        float a = angle * Mth.DEG_TO_RAD, cs = Mth.cos(a), sn = Mth.sin(a);
        Vec3 dir = new Vec3(l.x() * cs + u.x() * sn, l.y() * cs + u.y() * sn, l.z() * cs + u.z() * sn);
        float draw = out(age / 0.06f);
        float fade = 1 - win(age, life * 0.35f, life);
        Vec3 at = c.rel(centre);
        Vec3 from = at.subtract(dir.scale(len / 2)), to = from.add(dir.scale(len * draw));
        Skins.Profile point = k -> (float) Math.pow(Math.max(0, Mth.sin(Mth.PI * k)), 0.6);
        Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.PLASMA), from, to, 8, 0.5f * (0.6f + 0.4f * fade), point,
                strength * fade, Skins.FLAT, body, c.time * 0.02f, 0.4f);
        Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), from, to, 8, 0.13f, point, strength * fade,
                Skins.FLAT, edge, 0, 0);
        if (age < 0.09f) {
            Skins.spikes(c.pose, c.buffers, c.camera, at, (long) (centre.x * 977 + centre.z * 31 + angle), 8, len * 0.6f, 0.07f, edge,
                    strength * (1 - age / 0.09f));
        }
    }

    /** A ring square to the eye at {@code at} (the edge of a black star, a lens of bent light). */
    static void facingRing(Ctx c, Vec3 at, float radius, float width, int rgb, float alpha, float turn) {
        if (alpha <= 0.01f || radius <= 0.01f) return;
        PoseStack pose = c.pose;
        pose.pushPose();
        Vec3 r = c.rel(at);
        pose.translate(r.x, r.y, r.z);
        pose.mulPose(c.camera.rotation());
        pose.mulPose(Axis.XP.rotationDegrees(90));
        Skins.ring(pose, c.buffers, ModRenderTypes.energy(Skins.BEAM), Vec3.ZERO, radius, width, rgb, alpha, turn, Math.max(2, radius * 1.5f));
        pose.popPose();
    }

    /** A flat thing of {@link Aura}'s drawn at a world point (its pose's origin moved there first). */
    static void at(Ctx c, Vec3 world, Runnable draw) {
        PoseStack pose = c.pose;
        pose.pushPose();
        Vec3 r = c.rel(world);
        pose.translate(r.x, r.y, r.z);
        draw.run();
        pose.popPose();
    }

    /** The point {@code f} (0..1) of the way along a path of points (by their count, which is near enough for a crack). */
    static Vec3 along(List<Vec3> path, float f) {
        if (path.size() == 1) return path.get(0);
        float x = Mth.clamp(f, 0, 1) * (path.size() - 1);
        int i = Math.min(path.size() - 2, (int) x);
        return path.get(i).lerp(path.get(i + 1), x - i);
    }

    static List<Vec3> rel(Ctx c, List<Vec3> world) {
        List<Vec3> out = new ArrayList<>(world.size());
        for (Vec3 p : world) out.add(c.rel(p));
        return out;
    }

    /** The same frame as {@code c} but drawn as the world sees it, even through the wielder's own eyes (after-images, a cut landing on a foe). */
    static Ctx world(Ctx c) {
        return c.self ? new Ctx(c.pose, c.buffers, c.camera, c.origin, c.time, c.t, c.k, false, c.e, c.partial, c.live, c.fx) : c;
    }

    static boolean firstPerson(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        return e == mc.player && mc.options.getCameraType().isFirstPerson();
    }

    // ------------------------------------------------------------------ Ruinbringer

    /**
     * Cataclysm Cleave: fire rises off the wielder and cracks the ground under them as the blade goes up; one cut of
     * fire comes down from the sky into the ground and splits it open twelve blocks ahead, fire spitting from the
     * fissure as it runs; a moment later the whole fissure erupts.
     */
    static final class CataclysmCleave extends Art {
        static final int C = 0xFF7A2E, HOT = 0xFFD080, COOL = 0x7A1A08, DARK = 0x3A0C04, SOOT = 0x1E1714;
        /** The fissure erupts this long after the cut (the server's beat 1 comes then). */
        static final float ERUPT = 8 / 20f;

        @Override
        float life(Fx fx) {
            return 4f;
        }

        @SuppressWarnings("unchecked")
        static List<Vec3> path(Fx fx) {
            return fx.data instanceof List<?> l ? (List<Vec3>) l : null;
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0) {
                if (e == null || firstPerson(e)) return;
                Vec3 p = e.position();
                FxParticles.embers(p.x, p.y + 0.4, p.z, 0.7, 2, C);
                if (ArtFx.RANDOM.nextFloat() < 0.5f) FxParticles.fire(p.x + rnd(0.5), p.y + 0.1, p.z + rnd(0.5), 0.2, 1, 0.25f, 0.06f, HOT, COOL);
                return;
            }
            List<Vec3> path = path(fx);
            if (path == null) return;
            // fire spits from the fissure's front as it runs, embers rise off it while it glows
            float grow = out(win(k, 0.05f, ERUPT));
            if (grow > 0 && grow < 1) {
                Vec3 front = along(path, grow);
                FxParticles.fire(front.x, front.y + 0.2, front.z, 0.3, 3, 0.35f, 0.08f, HOT, COOL);
                FxParticles.sparks(front.x, front.y + 0.2, front.z, 3, 0.35f, C, 0.05f);
            }
            if (k > ERUPT && k < 3f) {
                Vec3 p = along(path, ArtFx.RANDOM.nextFloat());
                FxParticles.embers(p.x, p.y + 0.1, p.z, 0.3, 1, C);
            }
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            if (fx.points.size() >= 2) {
                List<Vec3> grounded = new ArrayList<>();
                for (Vec3 p : ArtShapes.jagged(fx.points.get(0), fx.points.get(1), fx.seed, 14, 0.35)) {
                    grounded.add(ArtFx.ground(level, p.add(0, 0.6, 0)).add(0, 0.02, 0));
                }
                fx.data = grounded;
            }
            Vec3 bite = fx.at.add(fx.look.scale(2.4));
            FxParticles.fire(bite.x, bite.y + 0.6, bite.z, 0.8, FxParticles.count(18), 0.45f, 0.05f, HOT, COOL);
            FxParticles.sparksAlong(bite.x, bite.y + 0.2, bite.z, fx.look, 0.6, FxParticles.count(24), 0.6f, C, 0.05f);
            FxParticles.dust(level, bite.x, bite.y, bite.z, 3.5f, FxParticles.count(10), 0.7f);
            FxParticles.smokeRing(bite.x, bite.y + 0.1, bite.z, FxParticles.count(14), SOOT, 0.9f, 0.32f);
            ArtFx.shake(fx.at, 0.9f, 22, SkillShake.SLAM);
        }

        @Override
        void beat(Fx fx, int n, Entity e, ClientLevel level) {
            List<Vec3> path = path(fx);
            if (path != null) {
                for (int i = 0; i < 6; i++) {
                    Vec3 p = along(path, (i + 0.5f) / 6);
                    FxParticles.fire(p.x, p.y + 0.4, p.z, 0.5, FxParticles.count(10), 0.5f, 0.12f, HOT, COOL);
                    FxParticles.smoke(p.x, p.y + 0.5, p.z, 0.6, FxParticles.count(3), SOOT, 0.8f, 0.05f);
                }
                Vec3 end = path.get(path.size() - 1);
                FxParticles.fireRing(end.x, end.y, end.z, 0.5, FxParticles.count(20), 0.5f, 0.35f, HOT, COOL);
                FxParticles.sparks(end.x, end.y + 0.5, end.z, FxParticles.count(30), 0.7f, C, 0.05f);
                FxParticles.dust(level, end.x, end.y, end.z, 4f, FxParticles.count(10), 0.8f);
                ArtFx.shake(path.get(path.size() / 2), 1.2f, 26, SkillShake.SWEEP);
            }
            List<Vec3> pts = fx.beatPoints[1];
            for (int i = 2; i < pts.size(); i++) {
                Vec3 p = pts.get(i);
                FxParticles.fire(p.x, p.y, p.z, 0.4, FxParticles.count(8), 0.4f, 0.1f, HOT, COOL);
            }
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            Skins.Look look = Skins.Look.FIRE;
            if (c.k < 0) {
                // the blade goes up: fire rises off the wielder and the ground cracks under them
                float a = win(c.t, 0, 0.3f);
                SkinFx.cracks(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, fx.seed, 2.4f, 6, out(win(c.t, 0.05f, 0.6f)), 0.9f * a, 0.25f,
                        look, C, c.time);
                if (!c.self) {
                    SkinFx.flames(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, 2.1f, 0.55f, a * 0.8f, look, DARK, fx.seed, c.time);
                    ArtMythic.eyes(c, C, a, 0.35f);
                    // heat gathering on the raised blade, over the head
                    if (c.e != null && c.t > 0.3f) {
                        Vec3 tip = c.rel(c.live).add(0, 3.1, 0).add(ArtFx.forward(c.e.getViewYRot(c.partial)).scale(-0.4));
                        float g = win(c.t, 0.3f, 0.65f);
                        Aura.glint(c.pose, c.buffers, (float) tip.x, (float) tip.y, (float) tip.z, 0.6f + 0.8f * g, HOT, g, c.time * 0.06f);
                        Skins.spikes(c.pose, c.buffers, c.camera, tip, fx.seed + (long) (c.time / 2), 8, 0.6f + 0.6f * g, 0.06f, HOT, g * 0.8f);
                    }
                }
                return;
            }
            float k = c.k;
            // the cleave: one huge cut of fire, top to bottom, into the ground
            Vec3 chest = fx.at.add(0, 1.4, 0).add(fx.look.scale(0.5));
            ArtMythic.cut(c, chest, fx.yaw, 90, 4.4f, k, 0.5f, -1, DARK, look, 1.25f);
            Vec3 bite = fx.at.add(fx.look.scale(2.4));
            if (k < 0.14f) Skins.spikes(c.pose, c.buffers, c.camera, c.rel(bite).add(0, 0.4, 0), fx.seed, 14, 2.6f, 0.16f, HOT, 1 - k / 0.14f);
            SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, c.rel(bite).add(0, 0.5, 0), c.rel(bite), 2.6f, k - 0.04f, 1.6f, 0.8f, look, C,
                    fx.seed, 1);
            // the fissure runs ahead, fire bursting out of it as it opens
            List<Vec3> path = path(fx);
            if (path == null) return;
            float erupt = c.beat(1);
            float grow = out(win(k, 0.05f, ERUPT));
            float glow = 1 - win(k, 2.8f, 3.6f);
            float rise = 0.6f + (erupt >= 0 ? 1.6f * (1 - win(erupt, 0.1f, 0.7f)) : 0);
            SkinFx.fault(c.pose, c.buffers, rel(c, path), grow, 0.9f, glow, rise, look, C, c.time);
            for (int i = 0; i < 6; i++) {
                float f = (i + 0.5f) / 6;
                float age = k - (0.05f + (ERUPT - 0.05f) * f);
                if (age < 0 || age > 0.9f) continue;
                Vec3 base = c.rel(along(path, f));
                SkinFx.geyser(c.pose, c.buffers, c.camera, c.origin, base, 2.4f + (i % 2) * 1.0f, 0.32f, age, 0.9f, look, C, fx.seed + i, c.time);
            }
            if (erupt < 0) return;
            // the eruption: the fissure bursts end to end, the far end hardest
            for (int i = 0; i < 4; i++) {
                Vec3 g = c.rel(along(path, 0.2f + i * 0.25f));
                SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, g.add(0, 0.8, 0), g, 2.4f, erupt - i * 0.04f, 1.6f, 1, look, C, fx.seed + 11 + i, 1);
                float age = erupt - i * 0.04f;
                if (age >= 0 && age < 1.1f) {
                    SkinFx.geyser(c.pose, c.buffers, c.camera, c.origin, g, 3.6f + (i % 2) * 0.8f, 0.45f, age, 1.1f, look, C, fx.seed + 40 + i, c.time);
                }
            }
            Vec3 end = c.rel(path.get(path.size() - 1));
            SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, end.add(0, 1.0, 0), end, 4f, erupt - 0.16f, 2.2f, 1, look, C, fx.seed + 99, 1.2f);
        }
    }

    // ------------------------------------------------------------------ Calamity Fang

    /**
     * Dragon's Wrath: an ink pool under the assassin and crimson eyes trailing light; then they are simply somewhere
     * else, a black wake with a burning red edge where they passed and slits in the air on everything they went
     * through; a breath later every wound opens at once in crossed cuts.
     */
    static final class DragonsWrath extends Art {
        static final int C = 0xE0306E, EDGE = 0xFF5A8A, INK = 0x14030A;
        static final float WOUNDS = 12 / 20f;

        @Override
        float life(Fx fx) {
            return fx.beat[1] >= 0 ? (fx.beat[1] - fx.impact) / 20f + 1.4f : 2.4f;
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0 && e != null && !firstPerson(e)) {
                Vec3 p = e.position();
                FxParticles.wisps(p.x, p.y + 0.2, p.z, 0.5, 1, 0x0C0B18, 0.35f);
            }
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            if (fx.points.size() < 2) return;
            Vec3 from = fx.points.get(0), to = fx.points.get(1);
            FxParticles.smoke(from.x, from.y + 0.8, from.z, 0.5, FxParticles.count(6), 0x0C0B18, 0.7f, 0.03f);
            FxParticles.wisps(from.x, from.y + 0.6, from.z, 0.6, FxParticles.count(6), 0x0C0B18, 0.5f);
            Vec3 d = to.subtract(from);
            for (int i = 1; i <= 5; i++) {
                Vec3 p = from.add(d.scale(i / 5.0));
                FxParticles.sparksAlong(p.x, p.y + 1, p.z, d, 0.4, 3, 0.5f, C, 0.03f);
            }
            FxParticles.dust(level, to.x, to.y, to.z, 2f, FxParticles.count(6), 0.5f);
            for (int i = 2; i < fx.points.size(); i++) {
                Vec3 p = fx.points.get(i);
                FxParticles.sparks(p.x, p.y, p.z, FxParticles.count(6), 0.35f, EDGE, 0.03f);
            }
            ArtFx.shake(to, 0.5f, 18, SkillShake.SWEEP);
        }

        @Override
        void beat(Fx fx, int n, Entity e, ClientLevel level) {
            List<Vec3> wounds = fx.beatPoints[1];
            for (int i = 1; i < wounds.size(); i++) {
                Vec3 p = wounds.get(i);
                FxParticles.drops(p.x, p.y, p.z, FxParticles.count(10), 0.25f, 0x9A0A2A);
                FxParticles.sparks(p.x, p.y, p.z, FxParticles.count(10), 0.45f, EDGE, 0.03f);
                FxParticles.wisps(p.x, p.y, p.z, 0.3, FxParticles.count(3), 0x0C0B18, 0.4f);
            }
            if (wounds.size() > 1) ArtFx.shake(wounds.get(1), 0.7f, 20, SkillShake.BLAST);
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            if (c.k < 0) {
                float a = win(c.t, 0, 0.2f);
                Aura.pool(c.pose, c.buffers, 0.03f, 1.4f * out(c.t / 0.25f), C, a * 0.9f, c.time, 0.5f);
                if (!c.self && c.e != null) {
                    SkinFx.flames(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, 1.2f, 0.45f, a * 0.6f, Skins.Look.SHADOW, C, fx.seed, c.time);
                    ArtMythic.eyes(c, EDGE, a, 0.42f);
                    // the eyes trail light behind them, the way a killer's do before the step
                    Vec3 eye = c.rel(c.e.getEyePosition(c.partial));
                    Vec3 f = ArtFx.forward(c.e.getViewYRot(c.partial));
                    Vec3 side = ArtFx.rightOf(f).scale(0.12);
                    for (int s = -1; s <= 1; s += 2) {
                        Vec3 p = eye.add(f.scale(0.27)).add(side.scale(s)).add(0, 0.02, 0);
                        Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), p, p.subtract(f.scale(0.9)).add(0, 0.06, 0), 4,
                                0.07f, k -> 1 - 0.7f * k, a * 0.9f, k -> 1 - k, EDGE, 0, 0);
                    }
                }
                return;
            }
            if (fx.points.size() < 2) return;
            float k = c.k;
            Vec3 from = fx.points.get(0).add(0, 1.0, 0), to = fx.points.get(1).add(0, 1.0, 0);
            Vec3 a = c.rel(from), b = c.rel(to);
            // the wake: black where they passed, a red-hot edge, a white flash the first frames
            float wake = 1 - win(k, 0.12f, 0.75f);
            Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.PLASMA), a, b, 16, 1.7f, s -> 0.35f + 0.65f * s, wake * 0.9f,
                    s -> s, Skins.Look.SHADOW.body(C), c.time * 0.05f, 0.3f);
            Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), a, b, 16, 0.24f, Skins.FLAT, wake, s -> s, EDGE,
                    -c.time * 0.2f, 0.4f);
            if (k < 0.12f) SkillWorldFx.streak(c.pose, c.buffers, c.camera, c.origin, a, b, 0.55f, 0xFFFFFF, 1 - k / 0.12f);
            // after-images: dark crescents standing along the path, as if it were cut by several of them
            for (int i = 0; i < 4; i++) {
                float f = 0.2f + i * 0.2f;
                Vec3 p = from.lerp(to, f);
                ArtMythic.cut(world(c), p, fx.yaw + (i % 2 == 0 ? 20 : -20), i % 2 == 0 ? 70 : -70, 1.1f, k - f * 0.06f, 0.4f,
                        i % 2 == 0 ? 1 : -1, INK, Skins.Look.SHADOW, 0.9f);
            }
            // ink thrown out where they left from, a thinner splash where they stopped
            at(c, fx.points.get(0), () -> Aura.splash(c.pose, c.buffers, 0.03f, 0.6f + 1.8f * out(k / 0.3f), C, 1 - win(k, 0.2f, 0.8f), fx.seed % 360));
            at(c, fx.points.get(1), () -> Aura.splash(c.pose, c.buffers, 0.03f, 0.4f + 1.2f * out(k / 0.25f), C, 0.8f * (1 - win(k, 0.15f, 0.6f)),
                    (fx.seed >> 8) % 360));
            // a slit opening on everything it went through
            for (int i = 2; i < fx.points.size(); i++) {
                slit(c, fx.points.get(i), 30 + i * 37, 1.2f, k - 0.02f * i, 0.3f, INK, EDGE, 0.9f);
            }
            // the wounds open, all at once: crossed cuts and a burst of the crimson
            float w = c.beat(1);
            if (w < 0) return;
            List<Vec3> wounds = c.beatPoints(1);
            for (int i = 1; i < wounds.size(); i++) {
                Vec3 p = wounds.get(i);
                slit(c, p, 45, 2.0f, w, 0.45f, INK, EDGE, 1.1f);
                slit(c, p, -45, 2.0f, w - 0.05f, 0.45f, INK, EDGE, 1.1f);
                SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, c.rel(p), null, 1.6f, w - 0.03f, 0.9f, 0.4f, Skins.Look.SHADOW, C, fx.seed + i, 1);
            }
        }
    }

    // ------------------------------------------------------------------ Eye of the Insatiable

    /**
     * Devouring Star: light streams into a black star growing at the staff's head; it flies as a comet and stops to
     * become a singularity, a black core rimmed with bent light and a ring of matter turning into it, everything round
     * drawn in; then it collapses to a point and bursts.
     */
    static final class DevouringStar extends Art {
        static final int C = 0x9B7BFF, CORE = 0xF0E8FF, INK = 0x0A0614, DEEP = 0x3A2280;
        static final float FLIGHT = 0.18f;

        @Override
        float life(Fx fx) {
            return fx.beat[1] >= 0 ? (fx.beat[1] - fx.impact) / 20f + 2.4f : 3.4f;
        }

        static Vec3 hit(Fx fx) {
            return fx.points.size() >= 2 ? fx.points.get(1) : fx.at.add(fx.look.scale(8)).add(0, 1.4, 0);
        }

        static Vec3 muzzle(Fx fx) {
            return fx.points.isEmpty() ? fx.at.add(0, 1.4, 0) : fx.points.get(0);
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0) {
                if (e == null) return;
                // light streams in to the staff's head
                Vec3 tip = ArtFx.staffTip(e, 0);
                int n = firstPerson(e) ? 1 : 3;
                for (int i = 0; i < n; i++) {
                    Vec3 d = new Vec3(rnd(1), rnd(1), rnd(1)).normalize().scale(1.6 + ArtFx.RANDOM.nextDouble());
                    Vec3 p = tip.add(d);
                    FxParticles.add(FxParticles.Tex.SPARK, p.x, p.y, p.z, -d.x * 0.11, -d.y * 0.11, -d.z * 0.11).life(10).size(0.05f, 0.01f)
                            .color(CORE, C).drag(1f).stretch(1.6f);
                }
                return;
            }
            if (k < FLIGHT || fx.beat[1] >= 0) return;
            // everything round the singularity is drawn in: dust, motes, bits of light
            Vec3 at = hit(fx);
            for (int i = 0; i < 3; i++) {
                double ang = ArtFx.RANDOM.nextDouble() * Math.PI * 2, r = 3 + ArtFx.RANDOM.nextDouble() * 3;
                Vec3 p = at.add(Math.cos(ang) * r, rnd(1.5), Math.sin(ang) * r);
                Vec3 in = at.subtract(p);
                // a little sideways too, so it spirals
                Vec3 side = new Vec3(-in.z, 0, in.x).scale(0.35);
                Vec3 v = in.add(side).scale(0.07);
                FxParticles.add(i == 0 ? FxParticles.Tex.SOFT : FxParticles.Tex.SPARK, p.x, p.y, p.z, v.x, v.y, v.z).life(14)
                        .size(i == 0 ? 0.25f : 0.05f, 0.02f).color(i == 0 ? INK : CORE, C).alpha(i == 0 ? 0.6f : 1f).drag(1.02f).stretch(i == 0 ? 0 : 1.4f);
            }
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            Vec3 m = muzzle(fx);
            FxParticles.flash(m.x, m.y, m.z, 1.2f, C);
            FxParticles.sparksAlong(m.x, m.y, m.z, hit(fx).subtract(m), 0.3, FxParticles.count(10), 0.6f, C, 0f);
        }

        @Override
        void beat(Fx fx, int n, Entity e, ClientLevel level) {
            Vec3 at = hit(fx);
            Vec3 g = ArtFx.ground(level, at);
            fx.data = Math.abs(g.y - at.y) < 3.5 ? g : null;
            FxParticles.smoke(at.x, at.y, at.z, 1.4, FxParticles.count(10), INK, 1.2f, 0.04f);
            FxParticles.motesBurst(at.x, at.y, at.z, FxParticles.count(26), 0.7f, C);
            FxParticles.sparks(at.x, at.y, at.z, FxParticles.count(30), 0.8f, CORE, 0.02f);
            if (fx.data instanceof Vec3 ground) FxParticles.smokeRing(ground.x, ground.y + 0.1, ground.z, FxParticles.count(18), INK, 1.1f, 0.4f);
            ArtFx.shake(at, 1.4f, 30, SkillShake.BLAST);
        }

        /** A black star: a dark core, a rim of light round its edge, a hot point in its heart. */
        static void star(Ctx c, Vec3 at, float r, float alpha) {
            if (r <= 0.01f || alpha <= 0.01f) return;
            Skins.sphere(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.PLASMA), at, r, 8, 14, alpha, INK, c.time * 0.03f, 2,
                    -c.time * 0.02f, 1, 0.9f);
            Vec3 world = at.add(c.origin);
            facingRing(c, world, r * 1.12f, Math.max(0.03f, r * 0.12f), C, alpha, c.time * 0.05f);
            facingRing(c, world, r * 1.02f, Math.max(0.02f, r * 0.05f), CORE, alpha * 0.9f, -c.time * 0.07f);
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            if (c.k < 0) {
                if (c.e == null) return;
                Vec3 tip = c.rel(ArtFx.staffTip(c.e, c.partial));
                float grow = out(win(c.t, 0, 0.55f));
                float r = (c.self ? 0.13f : 0.32f) * grow * (1 + 0.06f * Mth.sin(c.time * 0.9f));
                star(c, tip, r, 1);
                // light running into it along three coils
                if (!c.self) {
                    RandomSource rs = RandomSource.create(fx.seed);
                    for (int j = 0; j < 3; j++) {
                        Vec3 d = new Vec3(rs.nextFloat() - 0.5f, rs.nextFloat() - 0.3f, rs.nextFloat() - 0.5f).normalize().scale(2.2);
                        Vec3[] coil = ArtShapes.helix(tip.add(d), tip, 0.35, s -> 1 - s, 1.5, c.time * 0.25 + j * 2.1, 16);
                        ArtShapes.bent(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), coil, grow, 0.09f, 0.02f, 0.8f * grow, 0,
                                C, -c.time * 0.25f, 0.6f);
                    }
                    ArtMythic.eyes(c, C, grow, 0.3f);
                }
                return;
            }
            float k = c.k;
            Vec3 m = c.rel(muzzle(fx)), at = c.rel(hit(fx));
            // the comet
            if (k < FLIGHT + 0.03f) {
                float fly = out(win(k, 0, FLIGHT));
                Vec3 head = m.lerp(at, fly);
                Vec3 dir = at.subtract(m);
                Skins.lance(c.pose, c.buffers, c.camera, c.origin, head, dir, 1.6f, 0.55f, (float) Math.min(dir.length() * fly, 6), Skins.Look.ARCANE, C,
                        1, c.time * 0.1f);
                star(c, head, 0.3f, 1);
            }
            if (k < FLIGHT) return;
            float b = c.beat(1);
            float form = out(win(k, FLIGHT, FLIGHT + 0.12f));
            float collapse = b >= 0 ? 1 - in(win(b, 0, 0.08f)) : 1;
            float s = form * collapse * (1 + 0.04f * Mth.sin(c.time * 1.3f));
            if (s > 0.01f) {
                // the singularity: a black core, the lens of light round it, matter turning in a ring into it
                star(c, at, 0.9f * s, 1);
                facingRing(c, hit(fx), 1.35f * s, 0.07f, CORE, 0.8f * s, c.time * 0.04f);
                PoseStack pose = c.pose;
                Vector3f tmp = new Vector3f();
                for (int ring = 0; ring < 2; ring++) {
                    Quaternionf tilt = Axis.YP.rotationDegrees(ring == 0 ? 20 : 110).mul(Axis.XP.rotationDegrees(ring == 0 ? 68 : -52));
                    pose.pushPose();
                    pose.translate(at.x, at.y, at.z);
                    pose.mulPose(tilt);
                    float rr = (ring == 0 ? 1.7f : 2.2f) * s;
                    Skins.ring(pose, c.buffers, ModRenderTypes.energy(Skins.PLASMA), Vec3.ZERO, rr, 0.7f * s, ring == 0 ? C : DEEP, 0.7f,
                            -c.time * (ring == 0 ? 0.06f : 0.04f), 3);
                    Skins.ring(pose, c.buffers, ModRenderTypes.energy(Skins.BEAM), Vec3.ZERO, rr * 0.92f, 0.12f, CORE, 0.85f, c.time * 0.08f, 3);
                    pose.popPose();
                    // arms of light spiralling in along the ring's plane (ribbons face the eye, so they are laid out unturned)
                    for (int arm = 0; arm < 3; arm++) {
                        int n = 14;
                        Vec3[] pts = new Vec3[n + 1];
                        float[] w = new float[n + 1], al = new float[n + 1];
                        for (int i = 0; i <= n; i++) {
                            float f = i / (float) n;
                            double ang = arm * Mth.TWO_PI / 3 + f * 2.6 - c.time * 0.12 * (ring == 0 ? 1 : -0.8);
                            double rad = (4.6 - 3.4 * f) * s;
                            tilt.transform((float) (Math.cos(ang) * rad), 0, (float) (Math.sin(ang) * rad), tmp);
                            pts[i] = at.add(tmp.x(), tmp.y(), tmp.z());
                            w[i] = 0.05f + 0.16f * f;
                            al[i] = 0.55f * f * s;
                        }
                        Skins.ribbon(pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), pts, w, al, ring == 0 ? C : CORE,
                                -c.time * 0.2f, 0.5f);
                    }
                }
            }
            if (b < 0.06f) return;
            // the burst
            float age = b - 0.06f;
            Vec3 ground = fx.data instanceof Vec3 g ? c.rel(g) : null;
            SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, at, ground, 5.5f, age, 2.2f, 0.4f, Skins.Look.ARCANE, C, fx.seed, 1.3f);
            if (age < 0.15f) Skins.spikes(c.pose, c.buffers, c.camera, at, fx.seed + 5, 18, 7f, 0.22f, 0xFFFFFF, 1 - age / 0.15f);
            if (age < 0.6f) facingRing(c, hit(fx), 0.5f + 8 * out(age / 0.5f), 0.35f, CORE, 1 - win(age, 0.1f, 0.6f), 0);
            if (ground != null && age < 0.8f) {
                Skins.ring(c.pose, c.buffers, ModRenderTypes.energy(Skins.BEAM), ground.add(0, 0.05, 0), 0.5f + 9 * out(age / 0.6f), 0.45f, C,
                        1 - win(age, 0.2f, 0.8f), 0, 12);
            }
        }
    }

    // ------------------------------------------------------------------ Colossus Aegis

    /**
     * Colossus Stance: a hammer of light the size of a tower takes shape over the guardian's shoulder and comes down
     * with their own swing, breaking the ground ahead; a bulwark of hexes stands round them for eight seconds, then
     * breaks outward with all it took.
     */
    static final class ColossusStance extends Art {
        static final int C = 0xFFC85A, CORE = 0xFFF6DC, BODY = 0xE0A040;
        static final float SWING = 0.1f, HANDLE = 5f, STANCE = 160 / 20f;

        @Override
        float life(Fx fx) {
            return fx.beat[1] >= 0 ? (fx.beat[1] - fx.impact) / 20f + 1.6f : STANCE + 1.6f;
        }

        /** The hammer's lean from upright (degrees): cocked back in the wind-up, past level at the blow. */
        static float theta(float t, float k) {
            if (k < 0) return Mth.lerp(out(t / 0.6f), -20, -45);
            return Mth.lerp(in(k / SWING), -45, 100);
        }

        static Vec3 contact(Fx fx) {
            return fx.data instanceof Vec3 v ? v : fx.at.add(fx.look.scale(4.9));
        }

        /** The hammer: a shaft of light from {@code pivot}, its head a block of light {@code HANDLE} out. */
        static void hammer(Ctx c, Vec3 pivot, Vec3 fwd, float theta, float alpha) {
            if (alpha <= 0.01f) return;
            float th = theta * Mth.DEG_TO_RAD;
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 dir = fwd.scale(Mth.sin(th)).add(up.scale(Mth.cos(th)));
            Vec3 tangent = fwd.scale(Mth.cos(th)).subtract(up.scale(Mth.sin(th)));
            Vec3 right = ArtFx.rightOf(fwd);
            Vec3 head = pivot.add(dir.scale(HANDLE));
            Vec3 butt = pivot.subtract(dir.scale(0.7));
            Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), butt, head, 10, 0.34f, Skins.FLAT, alpha, Skins.FLAT,
                    C, -c.time * 0.1f, 0.3f);
            Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), butt, head, 10, 0.12f, Skins.FLAT, alpha, Skins.FLAT,
                    CORE, -c.time * 0.2f, 0.4f);
            ArtShapes.box(c.pose, c.buffers, ModRenderTypes.energy(Skins.PLASMA), head, right.scale(0.75), dir.scale(0.7), tangent.scale(1.2), BODY,
                    alpha * 0.8f);
            ArtShapes.box(c.pose, c.buffers, ModRenderTypes.energy(Skins.HEX), head, right.scale(0.8), dir.scale(0.75), tangent.scale(1.26), C,
                    alpha);
            // its striking faces burn white
            for (int s = -1; s <= 1; s += 2) {
                Vec3 face = head.add(tangent.scale(1.27 * s));
                Skins.billboard(c.pose, c.buffers, c.camera, ModRenderTypes.energy(Skins.FLARE), face, 0.55f, CORE, alpha * 0.6f, c.time * 2 + s * 40);
            }
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0) {
                if (e == null || firstPerson(e)) return;
                Vec3 p = e.position();
                FxParticles.motes(p.x, p.y + 2.5, p.z, 1.2, 2, C, 0.02f);
                return;
            }
            if (k >= SWING && ArtFx.once(fx, 0)) {
                Vec3 g = contact(fx);
                FxParticles.dust(level, g.x, g.y, g.z, 6f, FxParticles.count(16), 1.0f);
                FxParticles.sparks(g.x, g.y + 0.5, g.z, FxParticles.count(30), 0.8f, C, 0.05f);
                FxParticles.shards(g.x, g.y + 0.4, g.z, FxParticles.count(18), 0.55f, C, 0.12f);
                FxParticles.motesBurst(g.x, g.y + 1, g.z, FxParticles.count(20), 0.5f, C);
                ArtFx.shake(g, 1.6f, 30, SkillShake.SLAM);
            }
            if (fx.beat[1] < 0 && k > 0.3f && k < STANCE && e != null && ArtFx.RANDOM.nextFloat() < 0.3f && !firstPerson(e)) {
                Vec3 p = e.position();
                double ang = ArtFx.RANDOM.nextDouble() * Math.PI * 2;
                FxParticles.motes(p.x + Math.cos(ang) * 1.5, p.y + 0.3, p.z + Math.sin(ang) * 1.5, 0.1, 1, C, 0.03f);
            }
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            fx.data = ArtFx.ground(level, fx.at.add(fx.look.scale(4.9)).add(0, 0.8, 0));
        }

        @Override
        void beat(Fx fx, int n, Entity e, ClientLevel level) {
            List<Vec3> pts = fx.beatPoints[1];
            Vec3 c = pts.isEmpty() ? (e != null ? e.position() : fx.at) : pts.get(0);
            FxParticles.shards(c.x, c.y + 1.2, c.z, FxParticles.count(40), 0.75f, C, 0.13f);
            FxParticles.motesBurst(c.x, c.y + 1, c.z, FxParticles.count(24), 0.6f, CORE);
            FxParticles.dust(level, c.x, c.y, c.z, 5f, FxParticles.count(10), 0.8f);
            ArtFx.shake(c, 1.0f, 22, SkillShake.BLAST);
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            Skins.Look look = Skins.Look.LIGHT;
            if (c.k < 0) {
                if (c.e == null) return;
                Vec3 fwd = ArtFx.forward(c.e.getViewYRot(c.partial));
                Vec3 pivot = c.rel(c.live).add(0, 1.9, 0).add(ArtFx.rightOf(fwd).scale(0.3));
                hammer(c, pivot, fwd, theta(c.t, -1), win(c.t, 0, 0.3f));
                if (!c.self) ArtMythic.eyes(c, CORE, win(c.t, 0.2f, 0.5f), 0.3f);
                return;
            }
            float k = c.k;
            Vec3 pivot = new Vec3(0, 1.9, 0).add(ArtFx.rightOf(fx.look).scale(0.3));
            float fade = 1 - win(k, 0.35f, 0.7f);
            float th = theta(c.t, k);
            hammer(c, pivot, fx.look, th, fade);
            if (k < SWING + 0.04f) {
                // the smear of the swing, and two ghosts of it
                hammer(c, pivot, fx.look, theta(c.t, Math.max(0, k - 0.03f)), 0.35f);
                hammer(c, pivot, fx.look, theta(c.t, Math.max(0, k - 0.06f)), 0.18f);
                PoseStack pose = c.pose;
                pose.pushPose();
                pose.translate(pivot.x, pivot.y, pivot.z);
                pose.mulPose(Axis.YP.rotationDegrees(-fx.yaw));
                pose.mulPose(Axis.ZP.rotationDegrees(90));
                float head = 90 - th, tail = 90 - theta(c.t, Math.max(0, k - 0.07f));
                Skins.crescent(pose, c.buffers, ModRenderTypes.energy(Skins.BLADE), HANDLE + 1.3f, 2.2f, tail, head, C, 0.9f, 1, 0, 1);
                Skins.crescent(pose, c.buffers, ModRenderTypes.energy(Skins.BEAM), HANDLE + 1.25f, 0.5f, tail, head, CORE, 0.9f, 1, 0, 1);
                pose.popPose();
            }
            // the blow lands: the ground breaks and rings
            float ca = k - SWING;
            if (ca >= 0) {
                Vec3 g = c.rel(contact(fx));
                SkinFx.cracks(c.pose, c.buffers, c.camera, c.origin, g, fx.seed, 6.5f, 9, out(win(ca, 0, 0.22f)), 1 - win(ca, 1.6f, 2.6f), 0.45f,
                        Skins.Look.EARTH, C, c.time);
                SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, g.add(0, 0.8, 0), g, 4.2f, ca, 2.4f, 0.5f, Skins.Look.EARTH, C, fx.seed, 1.2f);
                SkinFx.shells(c.pose, c.buffers, c.camera, c.origin, g.add(0, 1.0, 0), 7.5f, ca, 3, 0.08f, look, C, 0.9f);
                if (ca < 0.12f) Skins.spikes(c.pose, c.buffers, c.camera, g.add(0, 0.9, 0), fx.seed + 3, 14, 4.5f, 0.2f, CORE, 1 - ca / 0.12f);
                // shards of golden light thrust up out of the broken ground round the blow
                if (ca < 1.5f) {
                    ArtShapes.Crystals shards = new ArtShapes.Crystals();
                    RandomSource r = RandomSource.create(fx.seed);
                    float rise = out(win(ca, 0.02f, 0.12f)), gone = win(ca, 0.9f, 1.5f);
                    for (int i = 0; i < 11; i++) {
                        double ang = i * Mth.TWO_PI / 11 + r.nextFloat() * 0.4;
                        double rad = 2.0 + r.nextFloat() * 1.4;
                        float h = 0.8f + r.nextFloat() * 1.0f;
                        Vec3 base = g.add(Math.cos(ang) * rad, -0.05, Math.sin(ang) * rad);
                        Vec3 tip = base.add(Math.cos(ang) * h * 0.5, h * rise * (1 - gone), Math.sin(ang) * h * 0.5);
                        shards.add(base, tip, 0.22f, BODY, 0xFFE6A0, CORE, C, 0.85f * (1 - gone), (float) ang);
                    }
                    shards.draw(c.pose, c.buffers);
                }
            }
            // the bulwark round the guardian until it breaks
            float ret = c.beat(1);
            float hold = win(k, 0.15f, 0.4f) * (ret >= 0 ? 0 : 1 - win(k, STANCE - 0.2f, STANCE + 0.3f));
            if (hold > 0.01f) {
                Vec3 feet = c.rel(c.live);
                float pulse = 0.85f + 0.15f * Mth.sin(c.time * 0.25f);
                if (c.self) {
                    Skins.ring(c.pose, c.buffers, ModRenderTypes.energy(Skins.BEAM), feet.add(0, 0.05, 0), 1.6f, 0.22f, C, hold * 0.8f * pulse,
                            c.time * 0.01f, 3);
                } else {
                    SkinFx.barrier(c.pose, c.buffers, c.camera, c.origin, feet.add(0, 0.95, 0), 1.6f, hold * 0.5f * pulse, feet, look, C, c.time);
                }
            }
            // retribution: the bulwark breaks outward with all it took
            if (ret >= 0) {
                List<Vec3> pts = c.beatPoints(1);
                Vec3 centre = c.rel(pts.isEmpty() ? c.live : pts.get(0));
                SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, centre.add(0, 1, 0), centre, 5f, ret, 1.6f, 0.3f, look, C, fx.seed + 7, 1.2f);
                SkinFx.shells(c.pose, c.buffers, c.camera, c.origin, centre.add(0, 1, 0), 7f, ret, 2, 0.1f, look, C, 1);
                if (ret < 0.12f && !c.self) Skins.spikes(c.pose, c.buffers, c.camera, centre.add(0, 1.1, 0), fx.seed + 9, 16, 4f, 0.2f, CORE, 1 - ret / 0.12f);
                for (int i = 1; i < pts.size(); i++) {
                    if (ret < 0.1f) Skins.spikes(c.pose, c.buffers, c.camera, c.rel(pts.get(i)), fx.seed + i, 8, 1.4f, 0.09f, C, 1 - ret / 0.1f);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Branch of the World Tree

    /**
     * Verdant Blessing: roots of light run out over the ground under a seal; a pillar of light goes up and the World
     * Tree grows in it, the healer at its heart, branches spreading, leaves of light, petals falling; halos settle over
     * the party and roots coil up round every foe close by.
     */
    static final class VerdantBlessing extends Art {
        static final int C = 0x8CE05A, CORE = 0xF4FFE0, GOLD = 0xFFF2A0, ROOT = 0x2E5A18, PETAL = 0xFFD8E8;
        static final float H = 7.5f;

        /** The tree, made once for each cast: where its branches and twigs go. */
        static final class Tree {
            final List<Vec3[]> branches = new ArrayList<>();
            final List<Vec3[]> twigs = new ArrayList<>();
            final List<Vec3> leaves = new ArrayList<>();
        }

        static Tree tree(Fx fx) {
            if (fx.data instanceof Tree t) return t;
            Tree t = new Tree();
            RandomSource r = RandomSource.create(fx.seed);
            float turn = r.nextFloat() * Mth.TWO_PI;
            for (int i = 0; i < 7; i++) {
                double ang = turn + i * Mth.TWO_PI / 7 + (r.nextFloat() - 0.5f) * 0.5f;
                double hb = 3.2 + r.nextFloat() * 3.4;
                double reach = 2.6 + r.nextFloat() * 2.2, lift = 0.8 + r.nextFloat() * 1.8;
                Vec3 base = new Vec3(0, hb, 0);
                Vec3 tip = new Vec3(Math.cos(ang) * reach, hb + lift, Math.sin(ang) * reach);
                Vec3 ctrl = new Vec3(Math.cos(ang) * reach * 0.45, hb + lift * 1.15 + 0.5, Math.sin(ang) * reach * 0.45);
                Vec3[] pts = ArtShapes.curve(base, ctrl, tip, 10);
                t.branches.add(pts);
                for (int j = 0; j < 2; j++) {
                    Vec3 from = pts[5 + j * 2];
                    double ta = ang + (j == 0 ? 0.7 : -0.7) + (r.nextFloat() - 0.5f) * 0.4;
                    Vec3 end = from.add(Math.cos(ta) * 1.3, 0.6 + r.nextFloat() * 0.6, Math.sin(ta) * 1.3);
                    t.twigs.add(ArtShapes.curve(from, from.lerp(end, 0.5).add(0, 0.35, 0), end, 6));
                }
            }
            for (int i = 0; i < 48; i++) {
                double ang = r.nextFloat() * Mth.TWO_PI, rad = Math.sqrt(r.nextFloat()) * 4.8;
                t.leaves.add(new Vec3(Math.cos(ang) * rad, H - 0.4 + (r.nextFloat() - 0.4f) * 2.4 * (1 - rad / 6.0), Math.sin(ang) * rad));
            }
            fx.data = t;
            return t;
        }

        @Override
        float life(Fx fx) {
            return 6.2f;
        }

        static int allies(Fx fx) {
            return fx.points.isEmpty() ? 0 : Mth.clamp((int) Math.round(fx.points.get(0).x), 0, fx.points.size() - 1);
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            Vec3 p = k < 0 && e != null ? e.position() : fx.at;
            if (k < 0) {
                if (e != null && !firstPerson(e)) FxParticles.motes(p.x, p.y + 0.2, p.z, 2.5, 2, C, 0.03f);
                return;
            }
            if (k < 4.5f) {
                // petals and leaves falling out of the crown
                double a = ArtFx.RANDOM.nextDouble() * Math.PI * 2, r = ArtFx.RANDOM.nextDouble() * 4.5;
                FxParticles.flutter(ArtFx.RANDOM.nextBoolean() ? FxParticles.Tex.PETAL : FxParticles.Tex.LEAF, p.x + Math.cos(a) * r, p.y + H - 0.5,
                        p.z + Math.sin(a) * r, 0.4, 1, 0.03f, ArtFx.RANDOM.nextBoolean() ? PETAL : C, 0.09f);
                if (ArtFx.RANDOM.nextFloat() < 0.5f) FxParticles.motes(p.x + rnd(3), p.y + 0.4, p.z + rnd(3), 0.3, 1, GOLD, 0.04f);
            }
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            Vec3 p = fx.at;
            FxParticles.motesBurst(p.x, p.y + 1.5, p.z, FxParticles.count(24), 0.45f, C);
            FxParticles.flutter(FxParticles.Tex.LEAF, p.x, p.y + 1, p.z, 1.2, FxParticles.count(12), 0.12f, C, 0.1f);
            int n = allies(fx);
            for (int i = 1; i <= n; i++) {
                Vec3 a = fx.points.get(i);
                FxParticles.motes(a.x, a.y + 1, a.z, 0.5, FxParticles.count(10), GOLD, 0.05f);
            }
            for (int i = n + 1; i < fx.points.size(); i++) {
                Vec3 f = fx.points.get(i);
                FxParticles.dust(level, f.x, f.y, f.z, 1.6f, FxParticles.count(5), 0.4f);
            }
            ArtFx.shake(p, 0.4f, 16, SkillShake.BLAST);
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            Skins.Look look = Skins.Look.NATURE;
            if (c.k < 0) {
                // roots of light run out over the ground under a turning seal
                float grow = out(win(c.t, 0, 0.75f));
                SkinFx.cracks(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, fx.seed, 8f, 9, grow, 0.9f * win(c.t, 0, 0.2f), 0.3f, look, C, c.time);
                Aura.seal(c.pose, c.buffers, 0.03f, 2.2f * out(c.t / 0.4f), c.time * 0.6f, C, 0.85f * win(c.t, 0, 0.25f));
                if (!c.self) ArtMythic.eyes(c, CORE, win(c.t, 0.3f, 0.7f), 0.28f);
                return;
            }
            float k = c.k;
            float fade = 1 - win(k, 5f, 6f);
            Tree tree = tree(fx);
            // the roots and the seal stay under it
            SkinFx.cracks(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, fx.seed, 8f, 9, 1, 0.9f * (1 - win(k, 4.4f, 5.4f)), 0.3f, look, C, c.time);
            Aura.seal(c.pose, c.buffers, 0.03f, 2.2f + 0.4f * out(k / 0.3f), c.time * 0.6f, C, 0.85f * fade);
            // the pillar of light it grows in
            if (!c.self && k < 0.8f) {
                SkinFx.column(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, H + 3, 0.7f, 1 - win(k, 0.25f, 0.8f), look, C, c.time);
            }
            // the trunk, with the healer at its heart
            float rise = out(win(k, 0, 0.45f));
            if (!c.self && rise > 0.01f) {
                float top = H * rise;
                Skins.tube(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.PLASMA), new Vec3(0, -0.2, 0), new Vec3(0, top, 0), 12, 10,
                        s -> 0.78f * (1 - 0.6f * s) + 0.08f, s -> 1 - 0.3f * s, 0.5f * fade, C, c.time * 0.004f, 2, 0.3f, -c.time * 0.02f, 0.25f, 0.7f);
                Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), Vec3.ZERO, new Vec3(0, top, 0), 10, 0.5f,
                        s -> 1 - 0.5f * s, 0.8f * fade, Skins.FLAT, CORE, -c.time * 0.3f, 0.3f);
            }
            // branches and twigs spread
            for (int i = 0; i < tree.branches.size(); i++) {
                float g = out(win(k, 0.18f + 0.04f * i, 0.6f + 0.04f * i));
                Vec3[] pts = tree.branches.get(i);
                ArtShapes.bent(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.PLASMA), pts, g, 0.62f, 0.1f, 0.45f * fade, 0.4f, C,
                        -c.time * 0.01f, 0.5f);
                ArtShapes.bent(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), pts, g, 0.3f, 0.05f, 0.9f * fade, 0.5f,
                        SkillWorldFx.mix(C, CORE, 0.35f), -c.time * 0.02f, 0.5f);
            }
            for (int i = 0; i < tree.twigs.size(); i++) {
                float g = out(win(k, 0.4f + 0.03f * i, 0.75f + 0.03f * i));
                ArtShapes.bent(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), tree.twigs.get(i), g, 0.14f, 0.03f, 0.8f * fade,
                        0.6f, SkillWorldFx.mix(C, CORE, 0.35f), 0, 0.6f);
            }
            // the crown: leaves of light, twinkling
            float bloom = win(k, 0.5f, 0.95f) * fade;
            if (bloom > 0.01f) {
                for (int i = 0; i < tree.leaves.size(); i++) {
                    Vec3 l = tree.leaves.get(i);
                    float tw = 0.55f + 0.45f * Mth.sin(c.time * 0.2f + i * 1.7f);
                    int hue = i % 3 == 0 ? GOLD : i % 3 == 1 ? C : CORE;
                    Aura.glint(c.pose, c.buffers, (float) l.x, (float) l.y + 0.08f * Mth.sin(c.time * 0.05f + i), (float) l.z, 0.28f + 0.18f * (i % 4) / 3f,
                            hue, bloom * tw, c.time * 0.02f + i);
                }
            }
            // halos settle over the party
            int n = allies(fx);
            float halo = win(k, 0.2f, 0.5f) * (1 - win(k, 2.6f, 3.3f));
            float camYaw = c.camera.getYRot();
            for (int i = 1; i <= n; i++) {
                Vec3 a = c.rel(fx.points.get(i));
                Aura.halo(c.pose, c.buffers, (float) a.x, (float) a.y + 2.3f, (float) a.z, camYaw, 0.45f, c.time * 2, GOLD, halo);
                if (k < 0.9f) SkinFx.column(c.pose, c.buffers, c.camera, c.origin, a, 3f, 0.35f, 1 - win(k, 0.2f, 0.9f), look, C, c.time);
            }
            if (!c.self && c.e != null) {
                Vec3 me = c.rel(c.live);
                Aura.halo(c.pose, c.buffers, (float) me.x, (float) me.y + 2.3f, (float) me.z, camYaw, 0.45f, c.time * 2, GOLD, halo);
            }
            // roots coil up round every foe the blessing binds
            float bind = out(win(k, 0.1f, 0.4f)) * (1 - win(k, 3f, 3.5f));
            if (bind > 0.01f) {
                for (int i = n + 1; i < fx.points.size(); i++) {
                    Vec3 base = c.rel(fx.points.get(i));
                    for (int j = 0; j < 3; j++) {
                        Vec3[] coil = ArtShapes.helix(base, base.add(0, 1.9, 0), 0.6, s -> 1 - 0.45f * s, 1.25, j * 2.09 + (fx.seed & 7), 18);
                        float g = out(win(k, 0.1f + 0.04f * j, 0.4f + 0.04f * j));
                        ArtShapes.bent(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.PLASMA), coil, g, 0.24f, 0.06f, bind * 0.9f, 0.3f,
                                ROOT, 0, 0.6f);
                        ArtShapes.bent(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), coil, g, 0.08f, 0.02f, bind, 0.3f, C,
                                -c.time * 0.1f, 0.6f);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Starfall Longbow

    /**
     * Starfall: a star gathers on the arrow as the bow is drawn; loosed, the arrow becomes a comet; where it lands a
     * seal of light opens, seven stars fall round it one after another, and then the great star falls on the mark.
     * A crit of the bow outside the art calls one small star.
     */
    static final class Starfall extends Art {
        static final int C = 0xFFF0A8, CORE = 0xFFFFFF, BODY = 0xE8C870;
        /** When each small star lands after the arrow does, and when the great one does (the server's timings). */
        static final float FIRST = 4 / 20f, GAP = 3 / 20f, GREAT = 28 / 20f, FALL = 0.25f;

        @Override
        float life(Fx fx) {
            if (fx.passive) return 1.4f;
            return fx.beat[1] >= 0 ? (fx.beat[1] - fx.impact) / 20f + GREAT + 2f : 8f;
        }

        static Entity arrow(Fx fx, Entity e, ClientLevel level) {
            if (fx.arrowId >= 0) return level.getEntity(fx.arrowId);
            if (fx.points.isEmpty()) return null;
            Vec3 from = fx.points.get(0);
            AbstractArrow best = null;
            for (AbstractArrow a : level.getEntitiesOfClass(AbstractArrow.class, new AABB(from, from).inflate(4))) {
                if (e != null && a.getOwner() != null && a.getOwner() != e) continue;
                if (best == null || a.distanceToSqr(from) < best.distanceToSqr(from)) best = a;
            }
            if (best != null) fx.arrowId = best.getId();
            return best;
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (fx.passive || k < 0) return;
            if (fx.beat[1] < 0) {
                Entity a = arrow(fx, e, level);
                if (a != null && a.isAlive()) {
                    Vec3 p = a.position();
                    FxParticles.motes(p.x, p.y, p.z, 0.15, 2, C, 0f);
                    FxParticles.add(FxParticles.Tex.SPARK, p.x, p.y, p.z, rnd(0.05), rnd(0.05), rnd(0.05)).life(8).size(0.05f, 0.01f).color(CORE, C);
                }
                return;
            }
            float b = (ArtFx.now(0) - fx.beat[1]) / 20f;
            List<Vec3> pts = fx.beatPoints[1];
            for (int i = 1; i < pts.size(); i++) {
                if (b >= FIRST + (i - 1) * GAP && ArtFx.once(fx, i)) {
                    Vec3 p = pts.get(i);
                    FxParticles.sparks(p.x, p.y + 0.3, p.z, FxParticles.count(14), 0.45f, C, 0.04f);
                    FxParticles.shards(p.x, p.y + 0.3, p.z, FxParticles.count(6), 0.35f, CORE, 0.08f);
                    FxParticles.motesBurst(p.x, p.y + 0.5, p.z, FxParticles.count(8), 0.3f, C);
                    ArtFx.shake(p, 0.35f, 20, SkillShake.BLAST);
                }
            }
        }

        @Override
        void beat(Fx fx, int n, Entity e, ClientLevel level) {
            if (n != 2) return;
            Vec3 c = fx.beatPoints[2].isEmpty() ? (fx.beatPoints[1].isEmpty() ? fx.at : fx.beatPoints[1].get(0)) : fx.beatPoints[2].get(0);
            FxParticles.shards(c.x, c.y + 0.5, c.z, FxParticles.count(36), 0.7f, CORE, 0.12f);
            FxParticles.motesBurst(c.x, c.y + 1, c.z, FxParticles.count(30), 0.7f, C);
            FxParticles.sparks(c.x, c.y + 0.5, c.z, FxParticles.count(36), 0.9f, C, 0.05f);
            FxParticles.smokeRing(c.x, c.y + 0.1, c.z, FxParticles.count(16), 0xD8D0C0, 1f, 0.4f);
            FxParticles.rays(c.x, c.y + 1.5, c.z, 6f, C, 8);
            ArtFx.shake(c, 1.3f, 28, SkillShake.SLAM);
        }

        @Override
        void passive(Fx fx, ClientLevel level) {
            Vec3 p = fx.points.get(0);
            FxParticles.motes(p.x, p.y + 0.5, p.z, 0.6, FxParticles.count(6), C, 0.03f);
        }

        /** A star falling from {@code sky} to {@code ground}, {@code f} (0..1) of the way. */
        static void falling(Ctx c, Vec3 sky, Vec3 ground, float f, float length, float width, float trail) {
            if (f < 0 || f > 1) return;
            float e = f * f;
            Vec3 head = sky.lerp(ground, e);
            Vec3 dir = ground.subtract(sky);
            Skins.lance(c.pose, c.buffers, c.camera, c.origin, head, dir, length, width, trail * Math.min(1, f * 3), Skins.Look.LIGHT, C, 1, c.time * 0.1f);
            Aura.glint(c.pose, c.buffers, (float) head.x, (float) head.y, (float) head.z, width * 3.2f, CORE, 1, c.time * 0.07f);
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            Skins.Look look = Skins.Look.LIGHT;
            if (fx.passive) {
                Vec3 at = c.rel(fx.points.get(0));
                float f = win(c.k, 0, 0.14f);
                if (c.k < 0.14f) falling(c, at.add(0.8, 9, 0.4), at, f, 1.6f, 0.35f, 3);
                SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, at.add(0, 0.4, 0), at, 1.7f, c.k - 0.14f, 1f, 0.5f, look, C, fx.seed, 1);
                return;
            }
            if (c.k < 0) {
                // charged and held: hard rays round the star on the arrow
                if (c.self || !(c.e instanceof Player p) || ArtFx.gathering(p) < 1) return;
                Vec3 tip = c.rel(arrowTip(p, c.partial));
                float pulse = 0.8f + 0.2f * Mth.sin(c.time * 0.6f);
                Skins.spikes(c.pose, c.buffers, c.camera, tip, fx.seed + (long) (c.time / 3), 8, 1.1f * pulse, 0.07f, C, 0.85f);
                return;
            }
            float b1 = c.beat(1);
            if (b1 < 0) {
                // the comet: the arrow itself, burning
                ClientLevel level = Minecraft.getInstance().level;
                Entity a = level == null ? null : arrow(fx, c.e, level);
                if (a == null || !a.isAlive()) return;
                Vec3 head = c.rel(a.getPosition(c.partial));
                Vec3 v = a.getDeltaMovement();
                if (v.lengthSqr() < 1.0e-4) return;
                Skins.lance(c.pose, c.buffers, c.camera, c.origin, head, v, 1.8f, 0.42f, 7, look, C, 1, c.time * 0.1f);
                Aura.glint(c.pose, c.buffers, (float) head.x, (float) head.y, (float) head.z, 1.2f, CORE, 1, c.time * 0.08f);
                Skins.spikes(c.pose, c.buffers, c.camera, head, fx.seed + (long) (c.time / 2), 4, 1f, 0.1f, C, 0.8f);
                return;
            }
            List<Vec3> pts = c.beatPoints(1);
            if (pts.isEmpty()) return;
            Vec3 centre = c.rel(pts.get(0));
            float b2 = c.beat(2);
            // the mark: a seal of light opens where the arrow struck, a thread of light up into the sky
            float seal = 0.85f * (1 - win(b2 >= 0 ? b2 : b1 - GREAT, 0.3f, 0.9f));
            at(c, pts.get(0), () -> Aura.seal(c.pose, c.buffers, 0.05f, 5 * out(b1 / 0.3f), c.time * 0.8f, C, seal));
            Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), centre, centre.add(0, 30, 0), 8, 0.28f, Skins.FLAT,
                    seal * 0.7f, s -> 1 - s, C, -c.time * 0.2f, 0.2f);
            // seven stars fall round it, one after another
            RandomSource r = RandomSource.create(fx.seed);
            ArtShapes.Crystals crystals = new ArtShapes.Crystals();
            for (int i = 1; i < pts.size(); i++) {
                Vec3 ground = c.rel(pts.get(i));
                float land = FIRST + (i - 1) * GAP;
                Vec3 out = ground.subtract(centre).multiply(1, 0, 1);
                out = out.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : out.normalize();
                Vec3 sky = ground.add(out.scale(4)).add(0, 20, 0);
                falling(c, sky, ground, (b1 - (land - FALL)) / FALL, 3.2f, 0.55f, 6);
                float age = b1 - land;
                if (age >= 0) {
                    SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, ground.add(0, 0.5, 0), ground, 2.3f, age, 1.2f, 0.6f, look, C, fx.seed + i, 1);
                    float rise = out(win(age, 0, 0.08f)), gone = win(age, 0.9f, 1.5f);
                    for (int j = 0; j < 5; j++) {
                        double ang = j * Mth.TWO_PI / 5 + r.nextFloat();
                        float h = 0.5f + r.nextFloat() * 0.7f;
                        Vec3 base = ground.add(Math.cos(ang) * 0.35, 0, Math.sin(ang) * 0.35);
                        Vec3 tip = base.add(Math.cos(ang) * h * 0.6, h * rise * (1 - gone), Math.sin(ang) * h * 0.6);
                        crystals.add(base, tip, 0.12f, BODY, 0xFFF4C8, CORE, C, 0.85f * (1 - gone), (float) ang);
                    }
                }
            }
            // the great star: first a point high above, then it falls on the mark
            if (b2 < 0) {
                float show = win(b1, 0.5f, 1.0f);
                Vec3 high = centre.add(0, 34, 0);
                if (b1 < GREAT - 0.3f) {
                    Aura.glint(c.pose, c.buffers, (float) high.x, (float) high.y, (float) high.z, 3f + 2f * show, CORE, show, c.time * 0.03f);
                }
                falling(c, high, centre, (b1 - (GREAT - 0.3f)) / 0.3f, 7f, 1.4f, 12);
            } else {
                SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, centre.add(0, 1, 0), centre, 6f, b2, 2.6f, 0.7f, look, C, fx.seed + 77, 1.3f);
                SkinFx.shells(c.pose, c.buffers, c.camera, c.origin, centre.add(0, 1, 0), 8f, b2, 3, 0.09f, look, C, 1);
                if (b2 < 0.15f) Skins.spikes(c.pose, c.buffers, c.camera, centre.add(0, 1.2, 0), fx.seed + 13, 16, 6f, 0.24f, CORE, 1 - b2 / 0.15f);
                if (b2 < 0.6f) SkinFx.column(c.pose, c.buffers, c.camera, c.origin, centre, 30, 1.3f, 1 - win(b2, 0.08f, 0.6f), look, C, c.time);
                if (b2 < 0.9f) {
                    Skins.ring(c.pose, c.buffers, ModRenderTypes.energy(Skins.BEAM), centre.add(0, 0.06, 0), 1 + 9 * out(b2 / 0.7f), 0.5f, CORE,
                            1 - win(b2, 0.2f, 0.9f), 0, 14);
                }
                // a crater of crystal light round the mark
                float rise = out(win(b2, 0, 0.1f)), gone = win(b2, 1.3f, 2.2f);
                for (int j = 0; j < 14; j++) {
                    double ang = j * Mth.TWO_PI / 14 + r.nextFloat() * 0.3;
                    double rad = 2.4 + r.nextFloat() * 0.8;
                    float h = 1.2f + r.nextFloat() * 1.3f;
                    Vec3 base = centre.add(Math.cos(ang) * rad, -0.05, Math.sin(ang) * rad);
                    Vec3 tip = base.add(Math.cos(ang) * h * 0.55, h * rise * (1 - gone), Math.sin(ang) * h * 0.55);
                    crystals.add(base, tip, 0.26f, BODY, 0xFFF4C8, CORE, C, 0.9f * (1 - gone), (float) ang);
                }
            }
            crystals.draw(c.pose, c.buffers);
        }
    }

    // ------------------------------------------------------------------ the drawn arts gathering on a bow

    /** Where a drawing archer's arrow head is, near enough: before the eyes, a little low. */
    static Vec3 arrowTip(Player p, float partial) {
        Vec3 look = p.getViewVector(partial);
        Vec3 right = ArtFx.rightOf(ArtFx.forward(p.getViewYRot(partial)));
        return p.getEyePosition(partial).add(look.scale(0.95)).add(0, -0.16, 0).add(right.scale(-0.04));
    }

    /** Particles of a drawn art gathering on a bow, {@code g} (0..1) of the way charged. */
    static void gatherTick(Player p, Signature sig, float g, ClientLevel level, boolean selfFirstPerson) {
        if (selfFirstPerson) return;
        Vec3 tip = arrowTip(p, 0);
        if (sig == Signature.STARFALL) {
            for (int i = 0; i < 2; i++) {
                Vec3 d = new Vec3(rnd(1), rnd(1), rnd(1)).normalize().scale(1 + ArtFx.RANDOM.nextDouble() * 0.8);
                Vec3 at = tip.add(d);
                FxParticles.add(FxParticles.Tex.SPARK, at.x, at.y, at.z, -d.x * 0.1, -d.y * 0.1, -d.z * 0.1).life(10).size(0.04f, 0.01f)
                        .color(Starfall.CORE, Starfall.C).drag(1f).stretch(1.4f);
            }
        } else if (sig == Signature.GALE_LANCE) {
            Vec3 look = p.getViewVector(0);
            Vec3 side = ArtFx.rightOf(ArtFx.forward(p.getYRot()));
            double ang = ArtFx.RANDOM.nextDouble() * Math.PI * 2;
            Vec3 ring = side.scale(Math.cos(ang) * 0.7).add(0, Math.sin(ang) * 0.7, 0);
            Vec3 at = tip.add(ring).subtract(look.scale(0.6));
            Vec3 v = look.scale(0.08).add(side.scale(-Math.sin(ang) * 0.05)).add(0, Math.cos(ang) * 0.05, 0);
            FxParticles.add(FxParticles.Tex.WISP, at.x, at.y, at.z, v.x, v.y, v.z).life(12).size(0.12f, 0.22f).color(0xE8FFF8).alpha(0.5f * g)
                    .drag(0.95f).spin(8);
        }
    }

    /** A drawn art gathering on a bow, as others see it: the pose's origin is at {@code live}, the archer's feet. */
    static void drawGathering(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, Vec3 live, Player p, Signature sig, float g,
                              float time, float partial) {
        Vec3 tip = arrowTip(p, partial).subtract(live);
        Vec3 look = p.getViewVector(partial);
        if (sig == Signature.STARFALL) {
            // a star gathering on the arrow head, the arrow's shaft lit
            Aura.glint(pose, buffers, (float) tip.x, (float) tip.y, (float) tip.z, 0.3f + 1.0f * g, Starfall.C, 0.5f + 0.5f * g, time * 0.05f);
            Aura.glint(pose, buffers, (float) tip.x, (float) tip.y, (float) tip.z, 0.2f + 0.5f * g, Starfall.CORE, g, -time * 0.08f);
            Skins.line(pose, buffers, camera, live, ModRenderTypes.energy(Skins.BEAM), tip, tip.subtract(look.scale(0.9)), 4, 0.08f, Skins.FLAT,
                    0.8f * g, s -> 1 - s, Starfall.C, 0, 0);
        } else if (sig == Signature.GALE_LANCE) {
            // wind coiling round the arrow, tighter and faster as the draw fills
            Vec3 back = tip.subtract(look.scale(1.1)), front = tip.add(look.scale(0.6));
            for (int j = 0; j < 3; j++) {
                Vec3[] coil = ArtShapes.helix(back, front, 0.45 - 0.15 * g, s -> 0.6f + 0.4f * Mth.sin(Mth.PI * s), 1.5 + g, time * (0.2 + 0.3 * g) + j * 2.09,
                        20);
                ArtShapes.bent(pose, buffers, camera, live, ModRenderTypes.energy(Skins.WIND), coil, 1, 0.16f, 0.06f, 0.55f * g, 0.2f, 0xA8F0E0,
                        -time * 0.1f, 0.5f);
            }
            Aura.glint(pose, buffers, (float) tip.x, (float) tip.y, (float) tip.z, 0.3f + 0.4f * g, 0xE8FFF8, g, time * 0.1f);
        }
    }

    private ArtDivine() {}
}
