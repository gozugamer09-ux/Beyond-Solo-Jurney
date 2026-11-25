package com.beyondsolo.client.skill;

import com.beyondsolo.client.fx.Aura;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.client.skill.ArtFx.Art;
import com.beyondsolo.client.skill.ArtFx.Ctx;
import com.beyondsolo.client.skill.ArtFx.Fx;
import com.beyondsolo.item.arsenal.Signature;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

import static com.beyondsolo.client.skill.ArtFx.hump;
import static com.beyondsolo.client.skill.ArtFx.out;
import static com.beyondsolo.client.skill.ArtFx.rnd;
import static com.beyondsolo.client.skill.ArtFx.win;

/**
 * The mythic arts' looks. Hellsovereign's Fang: hellfire rises off the wielder, three black-red cuts tear the air and the
 * blade keeps burning; every burning hit leaves a cut of fire on what it struck. Winterborne Requiem: frost creeps out
 * from the wielder's feet, the lance goes in and the ground freezes in a running ring, ice bursts up in three rings and
 * locks the foes inside, then the requiem shatters it all. Stormcaller's Edict: a storm gathers over the raised hammer,
 * bolts fall on every foe, and the verdict comes down on the strongest as a pillar of thunder.
 */
final class ArtMythic {
    static void register(Map<Signature, Art> art) {
        art.put(Signature.INFERNAL_REND, new InfernalRend());
        art.put(Signature.ABSOLUTE_ZERO, new AbsoluteZero());
        art.put(Signature.HEAVENS_VERDICT, new HeavensVerdict());
    }

    /**
     * A cut of a blade through the air in front of the wielder at chest height, facing {@code yaw}, rolled {@code roll}
     * degrees (0 a level sweep, +-50 a diagonal), {@code dir} 1 from the right, -1 back. Through the wielder's own eyes
     * it is a sweep across the view, a little below it, as the skills' cuts are.
     */
    static void cut(Ctx c, Vec3 at, float yaw, float roll, float radius, float age, float life, int dir, int color, Skins.Look look, float strength) {
        if (age < 0 || age > life) return;
        PoseStack pose = c.pose;
        pose.pushPose();
        if (c.self && c.e != null) {
            Vec3 eye = c.rel(c.e.getEyePosition(c.partial));
            pose.translate(eye.x, eye.y, eye.z);
            pose.mulPose(Axis.YP.rotationDegrees(-c.e.getViewYRot(c.partial)));
            pose.mulPose(Axis.XP.rotationDegrees(c.e.getViewXRot(c.partial)));
            pose.translate(0, -0.42f, 0.6f);
            pose.mulPose(Axis.ZP.rotationDegrees(roll * 0.6f + (dir > 0 ? -18 : 18)));
            pose.mulPose(Axis.XP.rotationDegrees(-38));
            SkillWorldFx.blade(pose, c.buffers, c.camera, Math.min(radius, 1.35f), age, life, dir, color, strength * 0.85f, look, c.time);
        } else {
            Vec3 r = c.rel(at);
            pose.translate(r.x, r.y, r.z);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            pose.mulPose(Axis.ZP.rotationDegrees(roll));
            pose.mulPose(Axis.XP.rotationDegrees(-12));
            SkillWorldFx.blade(pose, c.buffers, c.camera, radius, age, life, dir, color, strength, look, c.time);
        }
        pose.popPose();
    }

    /** Two points of light where a figure's eyes are, burning with the art (the series' "eyes carry power"). */
    static void eyes(Ctx c, int hue, float alpha, float size) {
        if (c.e == null || c.self || alpha <= 0.01f) return;
        Vec3 eye = c.rel(c.e.getEyePosition(c.partial));
        Vec3 f = ArtFx.forward(c.e.getViewYRot(c.partial));
        Vec3 side = ArtFx.rightOf(f).scale(0.12);
        Vec3 front = f.scale(0.27);
        for (int s = -1; s <= 1; s += 2) {
            Vec3 p = eye.add(front).add(side.scale(s));
            Aura.glint(c.pose, c.buffers, (float) p.x, (float) p.y + 0.02f, (float) p.z, size, hue, alpha, c.time * 0.05f + s);
        }
    }

    // ------------------------------------------------------------------ Hellsovereign's Fang

    static final class InfernalRend extends Art {
        static final int C = 0xFF5A1E;
        /** The hellfire's body: near black, burning red at its edges. */
        static final int DARK = 0x4A0A04;

        @Override
        float life(Fx fx) {
            return fx.passive ? 0.6f : 6.8f;
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (fx.passive || e == null) return;
            boolean self = e == net.minecraft.client.Minecraft.getInstance().player
                    && net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson();
            if (self) return;
            Vec3 p = e.position();
            if (k < 0) {
                FxParticles.embers(p.x, p.y + 0.3, p.z, 0.6, 2, C);
            } else if (k < 6.3f && ArtFx.RANDOM.nextFloat() < 0.6f) {
                FxParticles.embers(p.x, p.y + 0.6 + ArtFx.RANDOM.nextDouble() * 1.2, p.z, 0.4, 1, C);
            }
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            Vec3 c = fx.at.add(fx.look.scale(1.6)).add(0, 1.1, 0);
            FxParticles.fire(c.x, c.y, c.z, 0.8, FxParticles.count(14), 0.35f, 0.04f, 0xFFB050, 0x7A1A08);
            FxParticles.smoke(c.x, c.y, c.z, 0.8, FxParticles.count(5), 0x120604, 0.6f, 0.03f);
            ArtFx.shake(fx.at, 0.35f, 16, SkillShake.SWEEP);
        }

        @Override
        void passive(Fx fx, ClientLevel level) {
            Vec3 p = fx.points.get(0);
            FxParticles.fire(p.x, p.y, p.z, 0.3, FxParticles.count(7), 0.22f, 0.05f, 0xFFB050, 0x7A1A08);
            FxParticles.embers(p.x, p.y, p.z, 0.3, FxParticles.count(5), C);
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            if (fx.passive) {
                // a burning hit: a short black-red cut of fire across whatever was struck
                Vec3 p = fx.points.get(0);
                float roll = (fx.seed & 1) == 0 ? 40 : -40;
                PoseStack pose = c.pose;
                pose.pushPose();
                Vec3 r = c.rel(p);
                pose.translate(r.x, r.y, r.z);
                pose.mulPose(Axis.YP.rotationDegrees(-fx.yaw));
                pose.mulPose(Axis.ZP.rotationDegrees(roll));
                SkillWorldFx.blade(pose, c.buffers, c.camera, 0.9f, c.k, 0.32f, (fx.seed & 2) == 0 ? 1 : -1, DARK, 1f, Skins.Look.FIRE, c.time);
                pose.popPose();
                if (c.k < 0.12f) Skins.spikes(c.pose, c.buffers, c.camera, r, fx.seed, 8, 0.9f, 0.08f, 0xFFB060, 1 - c.k / 0.12f);
                return;
            }
            if (c.k < 0) {
                // hellfire rising off the wielder, the eyes lit
                float a = win(c.t, 0, 0.3f);
                if (!c.self) SkinFx.flames(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, 2.0f, 0.55f, a * 0.85f, Skins.Look.FIRE, DARK, c.fx.seed, c.time);
                eyes(c, C, a, 0.35f);
                return;
            }
            // three cuts: a diagonal, the other diagonal, then a wide level sweep
            Vec3 chest = fx.at.add(0, 1.1, 0);
            float gap = 3 / 20f;
            cut(c, chest, fx.yaw, -48, 2.7f, c.k, 0.34f, 1, DARK, Skins.Look.FIRE, 1);
            cut(c, chest, fx.yaw, 48, 2.7f, c.k - gap, 0.34f, -1, DARK, Skins.Look.FIRE, 1);
            cut(c, chest, fx.yaw, 0, 3.3f, c.k - 2 * gap, 0.4f, 1, DARK, Skins.Look.FIRE, 1.1f);
            for (int i = 0; i < 3; i++) {
                float a = c.k - i * gap;
                if (a >= 0 && a < 0.1f && !c.self) {
                    Vec3 tip = c.rel(chest.add(fx.look.scale(2.2)));
                    Skins.spikes(c.pose, c.buffers, c.camera, tip, fx.seed + i, 10, 1.6f, 0.1f, 0xFFC070, (1 - a / 0.1f) * 0.9f);
                }
            }
            // the mantle: the blade (and the wielder) keep burning for six seconds
            float mantle = win(c.k, 0.3f, 0.6f) * (1 - win(c.k, 6.1f, 6.6f));
            if (mantle > 0 && !c.self && c.e != null) {
                Vec3 feet = c.rel(c.live);
                SkinFx.flames(c.pose, c.buffers, c.camera, c.origin, feet, 1.9f, 0.5f, mantle * 0.45f, Skins.Look.FIRE, DARK, fx.seed, c.time);
                eyes(c, C, mantle * 0.8f, 0.28f);
            }
        }
    }

    // ------------------------------------------------------------------ Winterborne Requiem

    static final class AbsoluteZero extends Art {
        static final int C = 0x8FD8FF, CORE = 0xF0FCFF, DEEP = 0x2B6F9E, DARK = 0x0B1A2A;
        static final int[] RINGS = {10, 14, 18};
        static final float[] RADII = {2.4f, 4.4f, 6.6f};
        static final float REQUIEM = 30 / 20f;

        @Override
        float life(Fx fx) {
            return 3.4f;
        }

        @Override
        void started(Fx fx, Entity e, ClientLevel level) {
            Vec3 p = e.position();
            FxParticles.smoke(p.x, p.y + 0.2, p.z, 0.8, FxParticles.count(4), 0xDDEEFF, 0.5f, 0.01f);
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            Vec3 p = e != null && k < 0 ? e.position() : fx.at;
            if (k < 0) {
                // the air freezes: motes drawn in to the lance
                for (int i = 0; i < 3; i++) {
                    double a = ArtFx.RANDOM.nextDouble() * Math.PI * 2, r = 2.5 + ArtFx.RANDOM.nextDouble() * 2;
                    double x = p.x + Math.cos(a) * r, z = p.z + Math.sin(a) * r, y = p.y + 0.3 + ArtFx.RANDOM.nextDouble() * 2;
                    FxParticles.add(FxParticles.Tex.SPARK, x, y, z, (p.x - x) * 0.07, (p.y + 1.6 - y) * 0.07, (p.z - z) * 0.07)
                            .life(14).size(0.05f, 0.01f).color(CORE, C).drag(0.98f);
                }
            } else if (k < REQUIEM && ArtFx.RANDOM.nextFloat() < 0.7f) {
                double a = ArtFx.RANDOM.nextDouble() * Math.PI * 2, r = ArtFx.RANDOM.nextDouble() * 7.5;
                FxParticles.add(FxParticles.Tex.SOFT, p.x + Math.cos(a) * r, p.y + 0.15, p.z + Math.sin(a) * r, 0, 0.012, 0)
                        .life(30).size(0.25f, 0.6f).color(0xE8F6FF).alpha(0.35f).blend();
            }
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            Vec3 p = fx.at;
            FxParticles.shards(p.x, p.y + 0.4, p.z, FxParticles.count(26), 0.45f, CORE, 0.12f);
            FxParticles.smokeRing(p.x, p.y + 0.15, p.z, FxParticles.count(18), 0xE6F4FF, 0.8f, 0.35f);
            for (Vec3 f : fx.points) FxParticles.shards(f.x, f.y + 1, f.z, FxParticles.count(8), 0.25f, CORE, 0.08f);
            ArtFx.shake(p, 0.8f, 20, SkillShake.SLAM);
        }

        @Override
        void beat(Fx fx, int n, Entity e, ClientLevel level) {
            // the requiem: every spike and every block of ice shatters
            RandomSource r = RandomSource.create(fx.seed);
            for (int ring = 0; ring < RINGS.length; ring++) {
                for (int i = 0; i < RINGS[ring]; i++) {
                    if (ArtFx.RANDOM.nextFloat() < 0.5f) continue;
                    double a = (i + r.nextFloat() * 0.6) / RINGS[ring] * Math.PI * 2;
                    double x = fx.at.x + Math.cos(a) * RADII[ring], z = fx.at.z + Math.sin(a) * RADII[ring];
                    FxParticles.shards(x, fx.at.y + 0.6, z, FxParticles.count(3), 0.3f, CORE, 0.09f);
                }
            }
            for (Vec3 f : fx.beatPoints[1]) {
                FxParticles.shards(f.x, f.y + 1, f.z, FxParticles.count(18), 0.5f, CORE, 0.13f);
                FxParticles.flash(f.x, f.y + 1, f.z, 1.6f, CORE);
            }
            ArtFx.shake(fx.at, 0.6f, 20, SkillShake.BLAST);
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            Skins.Look look = Skins.Look.ARCANE;
            if (c.k < 0) {
                // frost creeps out over the ground from the wielder's feet
                float grow = out(win(c.t, 0.05f, 0.55f));
                SkinFx.cracks(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, fx.seed, 2.6f, 7, grow, 0.8f * grow, 0.2f, look, C, c.time);
                if (!c.self) eyes(c, CORE, win(c.t, 0.2f, 0.5f), 0.3f);
                return;
            }
            float k = c.k;
            float requiem = c.beat(1) >= 0 ? c.beat(1) : (k > REQUIEM + 0.3f ? k - REQUIEM : -1);
            float fade = 1 - win(k, 2.6f, 3.3f);
            // the ground freezes in a running ring, cracks of frost out to eight blocks
            float run = out(win(k, 0, 0.35f));
            SkinFx.cracks(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, fx.seed, 8.2f, 12, run, fade * (1 - 0.5f * win(k, 0.4f, 1.2f)), 0.35f, look, C, c.time);
            if (k < 0.45f) {
                float a = 1 - win(k, 0.1f, 0.45f);
                Skins.ring(c.pose, c.buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.05, 0), 0.5f + 7.8f * run, 0.4f, CORE, a, 0, 12);
                SkinFx.shock(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, 0.5f + 7.8f * run, 0.6f, a * 0.8f, C, fx.seed);
            }
            // ice bursts up in three rings, then the requiem breaks it
            ArtShapes.Crystals ice = new ArtShapes.Crystals();
            RandomSource r = RandomSource.create(fx.seed);
            for (int ring = 0; ring < RINGS.length; ring++) {
                float rise = out(win(k, 0.04f + ring * 0.07f, 0.12f + ring * 0.07f));
                float gone = requiem >= 0 ? win(requiem, 0, 0.06f) : win(k, 2.6f, 3.2f);
                for (int i = 0; i < RINGS[ring]; i++) {
                    double a = (i + r.nextFloat() * 0.6) / RINGS[ring] * Math.PI * 2;
                    float h = (0.7f + r.nextFloat() * 1.1f) * (1.15f - ring * 0.15f);
                    float lean = 0.25f + r.nextFloat() * 0.45f;
                    if (rise <= 0 || gone >= 1) continue;
                    Vec3 base = new Vec3(Math.cos(a) * RADII[ring], 0.02, Math.sin(a) * RADII[ring]);
                    Vec3 tip = base.add(Math.cos(a) * lean * h, h * rise * (1 - gone), Math.sin(a) * lean * h);
                    ice.add(base, tip, 0.16f + h * 0.08f, 0x6FB4E0, 0xBFE8FF, CORE, C, 0.85f * (1 - gone), (float) a + r.nextFloat());
                }
            }
            // the frozen foes locked in clusters of ice
            for (Vec3 f : fx.points) {
                float rise = out(win(k, 0.05f, 0.15f));
                float gone = requiem >= 0 ? win(requiem, 0, 0.05f) : win(k, 2.6f, 3.2f);
                if (rise <= 0 || gone >= 1) continue;
                Vec3 at = c.rel(f);
                RandomSource fr = RandomSource.create(fx.seed ^ (long) (f.x * 31 + f.z * 17));
                for (int i = 0; i < 6; i++) {
                    double a = i / 6.0 * Math.PI * 2 + fr.nextFloat();
                    float h = 1.4f + fr.nextFloat() * 1.0f;
                    Vec3 base = at.add(Math.cos(a) * 0.45, 0, Math.sin(a) * 0.45);
                    Vec3 tip = base.add(Math.cos(a) * 0.35, h * rise * (1 - gone), Math.sin(a) * 0.35);
                    ice.add(base, tip, 0.3f, 0x7FC0E8, 0xCFEFFF, CORE, C, 0.75f * (1 - gone), (float) a);
                }
            }
            ice.draw(c.pose, c.buffers);
            // the requiem: a white flash ring and the shatter on every foe
            if (requiem >= 0 && requiem < 0.6f) {
                float a = 1 - win(requiem, 0.05f, 0.6f);
                Skins.ring(c.pose, c.buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.3, 0), 1 + 8 * out(requiem / 0.4f), 0.5f, CORE, a, 0, 16);
                for (Vec3 f : c.beatPoints(1)) {
                    Vec3 at = c.rel(f).add(0, 1, 0);
                    SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, at, null, 1.8f, requiem, 0.6f, 0.4f, look, C, fx.seed, 1);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Stormcaller's Edict

    static final class HeavensVerdict extends Art {
        static final int C = 0xB8E2FF, CORE = 0xF2EEFF, DEEP = 0x3A2FA0, CLOUD = 0x141726;
        static final float SKY = 11;

        @Override
        float life(Fx fx) {
            return 2.8f;
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0 && e != null && ArtFx.RANDOM.nextFloat() < 0.5f) {
                Vec3 p = e.position();
                FxParticles.sparks(p.x + rnd(0.4), p.y + 2.2 + ArtFx.RANDOM.nextDouble() * 0.6, p.z + rnd(0.4), 2, 0.12f, C, 0.02f);
            }
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            for (Vec3 p : fx.points) {
                FxParticles.sparks(p.x, p.y + 0.3, p.z, FxParticles.count(18), 0.4f, C, 0.04f);
                FxParticles.flash(p.x, p.y + 1, p.z, 2.4f, CORE);
                FxParticles.smoke(p.x, p.y + 0.2, p.z, 0.4, FxParticles.count(3), 0x10121C, 0.6f, 0.03f);
            }
            ArtFx.shake(fx.at, 1.1f, 26, SkillShake.BLAST);
        }

        @Override
        void beat(Fx fx, int n, Entity e, ClientLevel level) {
            for (Vec3 p : fx.beatPoints[1]) {
                FxParticles.sparks(p.x, p.y + 0.5, p.z, FxParticles.count(30), 0.6f, C, 0.04f);
                FxParticles.rays(p.x, p.y + 1.2, p.z, 4f, CORE, 6);
                FxParticles.smokeRing(p.x, p.y + 0.2, p.z, FxParticles.count(14), 0x161A28, 0.8f, 0.3f);
                ArtFx.shake(p, 1.3f, 30, SkillShake.SLAM);
            }
        }

        /** The storm over the wielder: a ring of dark cloud lying level, turning, lit from inside now and then. */
        static void cloud(Ctx c, Vec3 centre, float size, float alpha) {
            if (alpha <= 0.01f) return;
            RandomSource r = RandomSource.create(c.fx.seed);
            for (int i = 0; i < 9; i++) {
                double a = i / 9.0 * Math.PI * 2 + c.time * 0.004;
                double d = (i % 3 == 0 ? 0.2 : 0.55 + r.nextFloat() * 0.35) * size;
                Vec3 p = centre.add(Math.cos(a) * d, r.nextFloat() * 0.6, Math.sin(a) * d);
                Skins.decal(c.pose, c.buffers, ModRenderTypes.softTexture(Skins.SMOKE), p, size * (0.45f + r.nextFloat() * 0.3f), CLOUD,
                        alpha * 0.85f, i * 40 + c.time * 0.6f);
            }
            // lightning running through it
            long tick = (long) (c.time / 2);
            RandomSource fl = RandomSource.create(c.fx.seed + tick * 13);
            if (fl.nextFloat() < 0.55f * alpha) {
                double a1 = fl.nextDouble() * Math.PI * 2, a2 = a1 + 1 + fl.nextDouble() * 2;
                Vec3 p1 = centre.add(Math.cos(a1) * size * 0.7, -0.2, Math.sin(a1) * size * 0.7);
                Vec3 p2 = centre.add(Math.cos(a2) * size * 0.6, -0.4, Math.sin(a2) * size * 0.6);
                Skins.lightning(c.pose, c.buffers, c.camera, c.origin, p1, p2, fl.nextLong(), 0.12f, C, alpha * 0.9f, 0.35f, 0.3f);
            }
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            Skins.Look look = Skins.Look.LIGHTNING;
            Vec3 sky = new Vec3(0, SKY, 0);
            if (c.k < 0) {
                float grow = out(win(c.t, 0, 0.65f));
                cloud(c, sky, 3 + 5 * grow, grow);
                // arcs crawl from the raised hammer up into the storm
                if (c.t > 0.3f && c.e != null) {
                    Vec3 head = c.rel(c.e.getPosition(c.partial)).add(0, 2.5, 0);
                    long seed = fx.seed + (long) (c.time / 2) * 7;
                    Skins.lightning(c.pose, c.buffers, c.camera, c.origin, head, sky.add(rnd(0.5), -0.5, rnd(0.5)), seed, 0.08f, C,
                            0.7f * win(c.t, 0.3f, 0.6f), 0.3f, 0.25f);
                }
                if (!c.self) eyes(c, CORE, win(c.t, 0.2f, 0.6f), 0.3f);
                return;
            }
            float k = c.k;
            float verdict = c.beat(1);
            float hold = 1 - win(verdict >= 0 ? verdict : k - 0.5f, 0.6f, 1.2f);
            cloud(c, sky, 8, hold);
            // the first bolt answers the hammer, then the storm falls on every foe
            if (k < 0.25f) {
                float a = 1 - win(k, 0.12f, 0.25f);
                Skins.lightning(c.pose, c.buffers, c.camera, c.origin, sky, new Vec3(0, 2.4, 0), fx.seed + (long) (c.time), 0.5f, C, a, 0.25f, 0.2f);
            }
            int i = 0;
            for (Vec3 p : fx.points) {
                float d = k - 0.03f * i;
                Vec3 at = c.rel(p);
                if (d >= 0 && d < 0.3f) {
                    float a = 1 - win(d, 0.15f, 0.3f);
                    Vec3 top = sky.add((at.x) * 0.35, 0, (at.z) * 0.35);
                    Skins.lightning(c.pose, c.buffers, c.camera, c.origin, top, at, fx.seed + i * 31L + (long) (c.time / 2), 0.36f, C, a, 0.3f, 0.35f);
                }
                if (d >= 0) {
                    SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, at.add(0, 0.4, 0), at, 2.3f, d, 1.6f, 0.6f, look, C, fx.seed + i, 1);
                }
                i++;
            }
            // the verdict: a pillar of thunder on the strongest
            if (verdict >= 0) {
                for (Vec3 p : c.beatPoints(1)) {
                    Vec3 at = c.rel(p);
                    float a = 1 - win(verdict, 0.45f, 0.8f);
                    if (a > 0) {
                        for (int b = 0; b < 4; b++) {
                            Vec3 off = new Vec3(Math.cos(b * 1.57 + c.time * 0.3) * 0.35, 0, Math.sin(b * 1.57 + c.time * 0.3) * 0.35);
                            Skins.lightning(c.pose, c.buffers, c.camera, c.origin, sky.add(at.x * 0.5, 0, at.z * 0.5).add(off), at.add(off),
                                    fx.seed + b * 97L + (long) c.time, 0.42f, C, a, 0.22f, 0.2f);
                        }
                        SkinFx.column(c.pose, c.buffers, c.camera, c.origin, at, SKY, 1.1f * (0.6f + 0.4f * out(verdict / 0.1f)), a * 0.8f, look, C, c.time);
                    }
                    SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, at.add(0, 0.6, 0), at, 3.6f, verdict, 1.8f, 0.8f, look, C, fx.seed + 77, 1.2f);
                    SkinFx.shells(c.pose, c.buffers, c.camera, c.origin, at, 5f, verdict, 2, 0.12f, look, CORE, 0.8f);
                }
            }
        }
    }

    private ArtMythic() {}
}
