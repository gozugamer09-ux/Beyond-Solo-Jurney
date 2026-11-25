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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.beyondsolo.client.skill.ArtFx.out;
import static com.beyondsolo.client.skill.ArtFx.rnd;
import static com.beyondsolo.client.skill.ArtFx.win;

/**
 * The legendary weapons' arts: each its own picture, smaller than a divine weapon's but never a plain swing. A ring of
 * lightning blades and the chain between the cut; six golden blows and the uppercut; three hounds of fire; a
 * constellation and its light; a dawn behind the healer; a gale driven through a line; a step through the dark; a wave
 * of the wielder's own blood; a sword of judgement out of the sky; a pyre; a fan of dark glass; a ring of blood thorns.
 */
final class ArtLegendary {
    static void register(Map<Signature, Art> art) {
        art.put(Signature.STORMBREAK, new Stormbreak());
        art.put(Signature.TITANS_BARRAGE, new TitansBarrage());
        art.put(Signature.THREE_HEADED_MAW, new ThreeHeadedMaw());
        art.put(Signature.CONSTELLATION, new Constellation());
        art.put(Signature.SAINTS_DAWN, new SaintsDawn());
        art.put(Signature.GALE_LANCE, new GaleLance());
        art.put(Signature.VEILSTEP, new Veilstep());
        art.put(Signature.BLOOD_PRICE, new BloodPrice());
        art.put(Signature.SUNDERING_VERDICT, new SunderingVerdict());
        art.put(Signature.PYRE_OF_OATHS, new PyreOfOaths());
        art.put(Signature.NIGHTGLASS_FAN, new NightglassFan());
        art.put(Signature.REQUIEM_OF_THORNS, new RequiemOfThorns());
    }

    // ------------------------------------------------------------------ helpers

    /** A ring standing square to {@code axis} round {@code centre}: a punch's burst of air, a gale's ring. */
    static void ringAcross(Ctx c, Vec3 centre, Vec3 axis, float radius, float width, ResourceLocation tex, int rgb, float alpha, float turn) {
        if (alpha <= 0.01f || radius <= 0.01f || axis.lengthSqr() < 1.0e-6) return;
        Vec3 d = axis.normalize();
        float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (Math.asin(Mth.clamp(-d.y, -1, 1)) * Mth.RAD_TO_DEG);
        PoseStack pose = c.pose;
        pose.pushPose();
        Vec3 r = c.rel(centre);
        pose.translate(r.x, r.y, r.z);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        Skins.ring(pose, c.buffers, ModRenderTypes.energy(tex), Vec3.ZERO, radius, width, rgb, alpha, turn, Math.max(2, radius * 1.5f));
        pose.popPose();
    }

    /** A crescent standing square to the way {@code yaw} faces at {@code centre}, rolled {@code roll} degrees: a wave thrown ahead. */
    static void wave(Ctx c, Vec3 centre, float yaw, float roll, float radius, float width, int body, int edge, float alpha, float scroll) {
        if (alpha <= 0.01f) return;
        PoseStack pose = c.pose;
        pose.pushPose();
        Vec3 r = c.rel(centre);
        pose.translate(r.x, r.y, r.z);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        Skins.crescent(pose, c.buffers, ModRenderTypes.lightTexture(SkillWorldFx.ARC), radius * 1.04f, width * 1.4f, -100, 100, edge, alpha * 0.35f, 0, 0, 1);
        Skins.crescent(pose, c.buffers, ModRenderTypes.energy(Skins.PLASMA), radius, width, -100, 100, body, alpha, 0, scroll, 1.5f);
        Skins.crescent(pose, c.buffers, ModRenderTypes.energy(Skins.BLADE), radius, width * 0.7f, -100, 100, edge, alpha, 0, 0, 1);
        pose.popPose();
    }

    static Vec3 chest(Fx fx) {
        return fx.at.add(0, 1.2, 0);
    }

    // ------------------------------------------------------------------ Stormlord's Longsword

    /** Stormbreak: one turn of the body throws a ring of lightning blades out round the wielder; then the lightning jumps from foe to foe. */
    static final class Stormbreak extends Art {
        static final int C = 0x9CC8FF, CORE = 0xF2F6FF;
        static final float OUT = 0.22f, REACH = 6.5f, CHAIN = 4 / 20f;

        @Override
        float life(Fx fx) {
            return 1.4f;
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0 && e != null && !ArtDivine.firstPerson(e) && ArtFx.RANDOM.nextFloat() < 0.6f) {
                Vec3 p = e.position();
                FxParticles.sparks(p.x + rnd(0.5), p.y + 1 + rnd(0.5), p.z + rnd(0.5), 2, 0.15f, C, 0.02f);
            }
            if (k >= CHAIN && fx.points.size() > 1 && ArtFx.once(fx, 0)) {
                for (int i = 1; i < fx.points.size(); i++) {
                    Vec3 p = fx.points.get(i);
                    FxParticles.sparks(p.x, p.y, p.z, FxParticles.count(10), 0.4f, C, 0.03f);
                }
                ArtFx.shake(fx.at, 0.4f, 18, SkillShake.BLAST);
            }
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            Vec3 p = fx.at;
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                FxParticles.sparksAlong(p.x + Math.cos(a), p.y + 1, p.z + Math.sin(a), new Vec3(Math.cos(a), 0.1, Math.sin(a)), 0.3, 2, 0.7f, C, 0.02f);
            }
            FxParticles.dust(level, p.x, p.y, p.z, 4f, FxParticles.count(8), 0.6f);
            ArtFx.shake(p, 0.5f, 18, SkillShake.SWEEP);
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            Skins.Look look = Skins.Look.LIGHTNING;
            if (c.k < 0) {
                float a = win(c.t, 0, 0.25f);
                if (!c.self && c.e != null) {
                    // arcs crawl round the wielder as the turn begins
                    Vec3 chest = c.rel(c.live).add(0, 1.1, 0);
                    long seed = fx.seed + (long) (c.time / 2) * 7;
                    RandomSource r = RandomSource.create(seed);
                    for (int i = 0; i < 2; i++) {
                        Vec3 p1 = chest.add(jitter(r, 0.7), jitter(r, 0.8), jitter(r, 0.7)), p2 = chest.add(jitter(r, 0.9), jitter(r, 0.9), jitter(r, 0.9));
                        Skins.lightning(c.pose, c.buffers, c.camera, c.origin, p1, p2, r.nextLong(), 0.06f, C, a * 0.9f, 0.35f, 0.2f);
                    }
                    ArtMythic.eyes(c, CORE, a, 0.3f);
                }
                return;
            }
            float k = c.k;
            float grow = out(k / OUT);
            float radius = 1 + (REACH - 1) * grow;
            float alpha = 1 - win(k, 0.16f, 0.42f);
            // the ring of blades, turning as it flies out, and a short wall of light under it
            PoseStack pose = c.pose;
            float turn = 140 * grow + (fx.seed & 63);
            for (int band = 0; band < 2; band++) {
                pose.pushPose();
                pose.translate(0, band == 0 ? 0.85 : 1.35, 0);
                for (int j = 0; j < 4; j++) {
                    float mid = turn + j * 90 + band * 45;
                    Skins.crescent(pose, c.buffers, ModRenderTypes.lightTexture(SkillWorldFx.ARC), radius * 1.03f, 1.3f, mid - 38, mid + 38, look.light(C),
                            alpha * 0.4f, 1.2f, 0, 1);
                    Skins.crescent(pose, c.buffers, ModRenderTypes.energy(Skins.BLADE), radius, 0.85f, mid - 38, mid + 38, look.body(C), alpha, 0.7f, 0, 1);
                }
                pose.popPose();
            }
            SkinFx.shock(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, radius, 0.9f, alpha * 0.8f, C, fx.seed);
            // lightning running along the blades
            long tick = (long) (c.time / 2);
            RandomSource r = RandomSource.create(fx.seed + tick * 31);
            for (int j = 0; j < 4 && alpha > 0.05f; j++) {
                float a1 = (turn + j * 90 - 30 + r.nextFloat() * 40) * Mth.DEG_TO_RAD, a2 = a1 + 0.3f + r.nextFloat() * 0.3f;
                Vec3 p1 = new Vec3(Mth.sin(a1) * radius, 1.1, Mth.cos(a1) * radius), p2 = new Vec3(Mth.sin(a2) * radius * 0.92, 0.6 + r.nextFloat(), Mth.cos(a2) * radius * 0.92);
                Skins.lightning(c.pose, c.buffers, c.camera, c.origin, p1, p2, r.nextLong(), 0.09f, C, alpha, 0.35f, 0.25f);
            }
            // the cut foes flash, then the chain jumps between them
            List<Vec3> pts = fx.points;
            for (int i = 1; i < pts.size(); i++) {
                if (k < 0.12f) Skins.spikes(c.pose, c.buffers, c.camera, c.rel(pts.get(i)), fx.seed + i, 8, 1.4f, 0.09f, CORE, 1 - k / 0.12f);
            }
            float chain = k - CHAIN;
            if (chain >= 0 && chain < 0.35f && pts.size() > 1) {
                float a = 1 - win(chain, 0.15f, 0.35f);
                Vec3 prev = c.rel(fx.at.add(0, 1.2, 0));
                for (int i = 1; i < pts.size(); i++) {
                    Vec3 next = c.rel(pts.get(i));
                    Skins.lightning(c.pose, c.buffers, c.camera, c.origin, prev, next, fx.seed + i * 977L + tick, 0.14f, C, a, 0.35f, 0.25f);
                    prev = next;
                }
            }
        }

        static double jitter(RandomSource r, double s) {
            return (r.nextDouble() - 0.5) * 2 * s;
        }
    }

    // ------------------------------------------------------------------ Titan's Knuckles

    /** Titan's Barrage: six golden blows burst the air ahead one after another, then the uppercut throws everything up. */
    static final class TitansBarrage extends Art {
        static final int C = 0xF2D27A, CORE = 0xFFF8E0;
        static final float GAP = 2 / 20f, UPPERCUT = 14 / 20f;
        static final int PUNCHES = 6;

        @Override
        float life(Fx fx) {
            return fx.beat[1] >= 0 ? (fx.beat[1] - fx.impact) / 20f + 1.1f : 1.9f;
        }

        static Vec3 target(Fx fx, int i) {
            Vec3 side = ArtFx.rightOf(fx.look).scale(i % 2 == 0 ? 0.25 : -0.25);
            return chest(fx).add(fx.look.scale(2.3)).add(side).add(0, (i % 3 - 1) * 0.2, 0);
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0) return;
            for (int i = 0; i < PUNCHES; i++) {
                if (k >= i * GAP && ArtFx.once(fx, i)) {
                    Vec3 p = target(fx, i);
                    FxParticles.sparksAlong(p.x, p.y, p.z, fx.look, 0.7, FxParticles.count(6), 0.45f, C, 0.03f);
                    FxParticles.add(FxParticles.Tex.SHOCK, p.x, p.y, p.z, 0, 0, 0).life(5).size(0.2f, 1.1f).color(CORE).alpha(0.7f).fade(0, 0.3f);
                    ArtFx.shake(fx.at, 0.22f, 12, SkillShake.BLAST);
                }
            }
        }

        @Override
        void beat(Fx fx, int n, Entity e, ClientLevel level) {
            List<Vec3> pts = fx.beatPoints[1];
            for (int i = 1; i < pts.size(); i++) {
                Vec3 p = pts.get(i);
                FxParticles.sparks(p.x, p.y, p.z, FxParticles.count(14), 0.6f, C, 0.03f);
                FxParticles.dust(level, p.x, p.y - 1, p.z, 1.6f, FxParticles.count(5), 0.5f);
            }
            Vec3 c = chest(fx).add(fx.look.scale(1.6));
            FxParticles.motesBurst(c.x, c.y, c.z, FxParticles.count(14), 0.5f, C);
            ArtFx.shake(fx.at, 0.9f, 20, SkillShake.SLAM);
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            Skins.Look look = Skins.Look.PHYSICAL;
            if (c.k < 0) {
                if (c.e == null || c.self) return;
                float a = win(c.t, 0, 0.15f);
                Vec3 f = ArtFx.forward(c.e.getViewYRot(c.partial));
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 h = c.rel(c.live).add(0, 1.15, 0).add(f.scale(0.3)).add(ArtFx.rightOf(f).scale(0.38 * s));
                    Aura.glint(c.pose, c.buffers, (float) h.x, (float) h.y, (float) h.z, 0.7f, C, a, c.time * 0.1f * s);
                }
                ArtMythic.eyes(c, CORE, a, 0.3f);
                return;
            }
            float k = c.k;
            Vec3 chest = chest(fx);
            for (int i = 0; i < PUNCHES; i++) {
                float age = k - i * GAP;
                if (age < 0 || age > 0.22f) continue;
                Vec3 side = ArtFx.rightOf(fx.look).scale(i % 2 == 0 ? 0.38 : -0.38);
                Vec3 hand = chest.add(side).add(fx.look.scale(0.3));
                Vec3 hit = target(fx, i);
                // the blow: a golden fist of force driven out, the air bursting round where it lands
                Vec3 head = c.rel(hand.lerp(hit, out(age / 0.05f)));
                Skins.lance(c.pose, c.buffers, c.camera, c.origin, head, fx.look, 1.3f, 0.55f, 1.0f, look, C, 1 - win(age, 0.05f, 0.14f), c.time * 0.2f);
                float ring = out(age / 0.14f);
                ringAcross(c, hit, fx.look, 0.3f + 1.2f * ring, 0.22f, Skins.BEAM, CORE, 1 - win(age, 0.03f, 0.16f), age * 3);
                ringAcross(c, hit.add(fx.look.scale(0.5)), fx.look, 0.2f + 0.9f * ring, 0.14f, Skins.BEAM, C, 0.8f * (1 - win(age, 0.05f, 0.18f)), age * 3);
                if (age < 0.08f) Skins.spikes(c.pose, c.buffers, c.camera, c.rel(hit), fx.seed + i, 9, 1.4f, 0.09f, CORE, 1 - age / 0.08f);
            }
            // the uppercut
            float b = c.beat(1);
            if (b < 0) return;
            ArtMythic.cut(c, chest.add(fx.look.scale(0.8)), fx.yaw, 90, 2.3f, b, 0.4f, 1, C, look, 1.2f);
            Vec3 up = c.rel(chest.add(fx.look.scale(1.6)).add(0, 0.5, 0));
            if (b < 0.12f) Skins.spikes(c.pose, c.buffers, c.camera, up, fx.seed + 50, 14, 2.6f, 0.15f, CORE, 1 - b / 0.12f);
            SkinFx.shells(c.pose, c.buffers, c.camera, c.origin, up, 4.5f, b, 2, 0.08f, Skins.Look.LIGHT, C, 0.9f);
            List<Vec3> pts = c.beatPoints(1);
            for (int i = 1; i < pts.size(); i++) {
                Vec3 p = c.rel(pts.get(i));
                float a = 1 - win(b, 0.1f, 0.45f);
                Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), p.add(0, -0.6, 0), p.add(0, 2.6 * out(b / 0.15f), 0), 6,
                        0.35f, s -> 1 - 0.7f * s, a, s -> 1 - s, C, 0, 0);
            }
        }
    }

    // ------------------------------------------------------------------ Hellhound Gatekeeper's Halberd

    /** Three-Headed Maw: hounds' eyes open in the air, then three heads of fire run down three lanes and bite. */
    static final class ThreeHeadedMaw extends Art {
        static final int C = 0xFF6A2A, HOT = 0xFFC060, COOL = 0x7A1A08, DARK = 0x3A0A04, EYE = 0xFF3010;
        static final float RUN = 0.3f;
        static final float[] LANES = {-25, 0, 25};

        @Override
        float life(Fx fx) {
            return 1.6f;
        }

        Vec3 origin(Fx fx) {
            return fx.points.isEmpty() ? chest(fx) : fx.points.get(0);
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0) {
                if (e != null && !ArtDivine.firstPerson(e) && ArtFx.RANDOM.nextFloat() < 0.6f) {
                    Vec3 p = e.position();
                    FxParticles.embers(p.x, p.y + 0.5, p.z, 0.6, 1, C);
                }
                return;
            }
            Vec3 o = origin(fx);
            for (int i = 1; i < fx.points.size() && i <= 3; i++) {
                Vec3 end = fx.points.get(i);
                float f = out(Math.min(1, k / RUN));
                if (k < RUN + 0.05f) {
                    Vec3 h = o.lerp(end, f);
                    FxParticles.fire(h.x, h.y, h.z, 0.3, 2, 0.35f, 0.04f, HOT, COOL);
                }
                if (k >= RUN && ArtFx.once(fx, i)) {
                    FxParticles.fireRing(end.x, end.y, end.z, 0.2, FxParticles.count(12), 0.35f, 0.25f, HOT, COOL);
                    FxParticles.sparks(end.x, end.y, end.z, FxParticles.count(12), 0.5f, C, 0.04f);
                    FxParticles.smoke(end.x, end.y, end.z, 0.3, FxParticles.count(3), 0x1E1714, 0.6f, 0.04f);
                }
            }
            if (k >= RUN && ArtFx.once(fx, 0)) ArtFx.shake(fx.at, 0.5f, 18, SkillShake.BLAST);
        }

        /** A hound's head of fire at {@code head} running along {@code dir}: jaws snapping, eyes burning. */
        static void hound(Ctx c, Vec3 head, Vec3 dir, float open, float alpha) {
            if (alpha <= 0.01f) return;
            Vec3 d = dir.normalize();
            float yaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
            PoseStack pose = c.pose;
            pose.pushPose();
            pose.translate(head.x, head.y, head.z);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            pose.mulPose(Axis.ZP.rotationDegrees(90));
            // upper and lower jaw: two crescents of fire opening and closing in the upright plane
            Skins.crescent(pose, c.buffers, ModRenderTypes.energy(Skins.FIRE), 0.95f, 0.42f, 105, 18 + open, Skins.Look.FIRE.body(C), alpha, 0, c.time * 0.05f, 1);
            Skins.crescent(pose, c.buffers, ModRenderTypes.energy(Skins.FIRE), 0.95f, 0.42f, -105, -18 - open, Skins.Look.FIRE.body(C), alpha, 0, c.time * 0.05f, 1);
            Skins.crescent(pose, c.buffers, ModRenderTypes.energy(Skins.BLADE), 0.97f, 0.16f, 100, 18 + open, HOT, alpha, 0, 0, 1);
            Skins.crescent(pose, c.buffers, ModRenderTypes.energy(Skins.BLADE), 0.97f, 0.16f, -100, -18 - open, HOT, alpha, 0, 0, 1);
            pose.popPose();
            // eyes over the upper jaw
            Vec3 side = ArtFx.rightOf(new Vec3(d.x, 0, d.z).normalize()).scale(0.28);
            Vec3 eyes = head.subtract(d.scale(0.35)).add(0, 0.5, 0);
            for (int s = -1; s <= 1; s += 2) {
                Vec3 p = eyes.add(side.scale(s));
                Aura.glint(c.pose, c.buffers, (float) p.x, (float) p.y, (float) p.z, 0.45f, EYE, alpha, c.time * 0.1f);
            }
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            Skins.Look look = Skins.Look.FIRE;
            if (c.k < 0) {
                if (c.e == null) return;
                float a = win(c.t, 0.05f, 0.35f);
                if (!c.self) SkinFx.flames(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, 1.9f, 0.55f, a * 0.7f, look, DARK, fx.seed, c.time);
                // three pairs of eyes open in the air ahead
                Vec3 f = ArtFx.forward(c.e.getViewYRot(c.partial));
                Vec3 base = c.rel(c.live).add(0, 1.5, 0);
                for (int i = 0; i < 3; i++) {
                    Vec3 lane = f.yRot(LANES[i] * Mth.DEG_TO_RAD);
                    Vec3 at = base.add(lane.scale(1.8)).add(0, i == 1 ? 0.35 : 0, 0);
                    Vec3 side = ArtFx.rightOf(lane).scale(0.22);
                    float ai = a * win(c.t, 0.1f + i * 0.08f, 0.25f + i * 0.08f);
                    for (int s = -1; s <= 1; s += 2) {
                        Vec3 p = at.add(side.scale(s));
                        Aura.glint(c.pose, c.buffers, (float) p.x, (float) p.y, (float) p.z, 0.4f, EYE, ai, c.time * 0.07f);
                    }
                }
                return;
            }
            float k = c.k;
            Vec3 o = origin(fx);
            float f = out(Math.min(1, k / RUN));
            float fade = 1 - win(k, RUN + 0.25f, RUN + 0.6f);
            for (int i = 1; i < fx.points.size() && i <= 3; i++) {
                Vec3 end = fx.points.get(i);
                Vec3 dir = end.subtract(o);
                if (dir.lengthSqr() < 0.01) continue;
                Vec3 head = c.rel(o.lerp(end, f));
                Skins.lance(c.pose, c.buffers, c.camera, c.origin, head, dir, 2.2f, 0.9f, (float) (dir.length() * f), look, C, fade, c.time * 0.1f);
                float open = k < RUN ? 22 + 14 * Mth.sin(c.time * 1.6f + i) : 22 * (1 - out((k - RUN) / 0.06f));
                hound(c, head, dir, Math.max(0, open), fade);
                float bite = k - RUN;
                if (bite >= 0) {
                    SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, head, null, 1.7f, bite, 1.0f, 1, look, C, fx.seed + i, 1);
                    if (bite < 0.1f) Skins.spikes(c.pose, c.buffers, c.camera, head, fx.seed + i * 7, 10, 1.6f, 0.1f, HOT, 1 - bite / 0.1f);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Starseer's Staff

    /** Constellation: stars wake over every marked foe and are joined one to the next; then their light falls on each. */
    static final class Constellation extends Art {
        static final int C = 0x9FC4FF, CORE = 0xFFFFFF, DEEP = 0x5A78E0;
        /** Seconds between one star waking and the next (the light falls about a second after the first). */
        static final float LINK = 0.09f;

        @Override
        float life(Fx fx) {
            return fx.beat[1] >= 0 ? (fx.beat[1] - fx.impact) / 20f + 1.2f : 2f;
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0 && e != null && !ArtDivine.firstPerson(e)) {
                Vec3 p = e.position();
                FxParticles.motes(p.x, p.y + 1.6, p.z, 1.2, 1, C, 0.01f);
            }
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            for (Vec3 p : fx.points) FxParticles.motesBurst(p.x, p.y, p.z, FxParticles.count(8), 0.2f, C);
        }

        @Override
        void beat(Fx fx, int n, Entity e, ClientLevel level) {
            for (Vec3 p : fx.beatPoints[1]) {
                FxParticles.shards(p.x, p.y + 0.6, p.z, FxParticles.count(10), 0.45f, CORE, 0.08f);
                FxParticles.motesBurst(p.x, p.y + 1, p.z, FxParticles.count(10), 0.4f, C);
            }
            if (!fx.beatPoints[1].isEmpty()) ArtFx.shake(fx.beatPoints[1].get(0), 0.6f, 24, SkillShake.BLAST);
        }

        /** A star: a hot point, four long rays and a slow turn. */
        static void star(Ctx c, Vec3 at, float size, float alpha, long seed) {
            Aura.glint(c.pose, c.buffers, (float) at.x, (float) at.y, (float) at.z, size, C, alpha, c.time * 0.02f + seed);
            Aura.glint(c.pose, c.buffers, (float) at.x, (float) at.y, (float) at.z, size * 0.55f, CORE, alpha, -c.time * 0.03f);
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            if (c.k < 0) {
                if (c.e == null) return;
                float a = win(c.t, 0, 0.3f);
                Vec3 tip = c.rel(ArtFx.staffTip(c.e, c.partial));
                star(c, tip, (c.self ? 0.4f : 0.8f) * (0.4f + 0.6f * out(c.t / 0.5f)), a, fx.seed);
                if (!c.self) {
                    // small stars turning round the wielder
                    Vec3 me = c.rel(c.live);
                    for (int i = 0; i < 5; i++) {
                        double ang = i * Math.PI * 2 / 5 + c.time * 0.06;
                        Vec3 p = me.add(Math.cos(ang) * 1.3, 1.2 + 0.4 * Math.sin(c.time * 0.1 + i), Math.sin(ang) * 1.3);
                        star(c, p, 0.35f, a * 0.8f, fx.seed + i);
                    }
                }
                return;
            }
            float k = c.k;
            float b = c.beat(1);
            float fade = b >= 0 ? 1 - win(b, 0.05f, 0.4f) : 1 - win(k, 1.5f, 2f);
            List<Vec3> pts = fx.points;
            // the stars wake one by one over the marked
            for (int i = 0; i < pts.size(); i++) {
                float a = win(k, i * LINK, i * LINK + 0.08f) * fade;
                Vec3 p = c.rel(pts.get(i));
                star(c, p, 1.5f + 0.2f * Mth.sin(c.time * 0.4f + i), a, fx.seed + i);
                if (k - i * LINK < 0.18f && k >= i * LINK) {
                    Skins.spikes(c.pose, c.buffers, c.camera, p, fx.seed + i, 8, 2.4f, 0.08f, CORE, 1 - (k - i * LINK) / 0.18f);
                }
            }
            // and are joined into the figure, the staff to the first, each to the next
            Vec3 from = c.e != null ? c.rel(ArtFx.staffTip(c.e, c.partial)) : c.rel(fx.at.add(0, 1.6, 0));
            for (int i = 0; i < pts.size(); i++) {
                float g = out(win(k, i * LINK, i * LINK + 0.1f));
                Vec3 to = c.rel(pts.get(i));
                if (g > 0) {
                    Vec3 end = from.lerp(to, g);
                    float a = (i == 0 ? 0.5f : 0.85f) * fade;
                    Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), from, end, 8, 0.16f, Skins.FLAT, a, Skins.FLAT,
                            CORE, -c.time * 0.1f, 0.5f);
                    Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), from, end, 8, 0.6f, Skins.FLAT, a * 0.5f,
                            Skins.FLAT, DEEP, -c.time * 0.05f, 0.3f);
                }
                from = to;
            }
            // the light falls: a lance of starlight from each star onto its foe
            if (b < 0) return;
            for (int i = 0; i < c.beatPoints(1).size(); i++) {
                Vec3 foot = c.rel(c.beatPoints(1).get(i));
                Vec3 star = foot.add(0, 3.2, 0);
                for (Vec3 s : pts) {
                    Vec3 rs = c.rel(s);
                    if (Math.abs(rs.x - foot.x) < 1.5 && Math.abs(rs.z - foot.z) < 1.5) {
                        star = rs;
                        break;
                    }
                }
                float f = out(win(b, 0, 0.08f));
                Vec3 head = star.lerp(foot.add(0, 0.2, 0), f);
                if (b < 0.14f) {
                    Skins.lance(c.pose, c.buffers, c.camera, c.origin, head, foot.subtract(star), 1.8f, 0.45f, (float) star.distanceTo(head), Skins.Look.LIGHT,
                            C, 1 - win(b, 0.08f, 0.14f), c.time * 0.1f);
                }
                if (b >= 0.08f) {
                    SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, foot.add(0, 0.8, 0), foot, 1.9f, b - 0.08f, 1.0f, 0.6f, Skins.Look.LIGHT, C, fx.seed + i, 1);
                    if (b < 0.5f) SkinFx.column(c.pose, c.buffers, c.camera, c.origin, foot, 3.4f, 0.35f, 1 - win(b, 0.1f, 0.5f), Skins.Look.LIGHT, C, c.time);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Saint's Sunstaff

    /** Saint's Dawn: a halo rises behind the healer and becomes a sun; its light falls on the party and burns the foes. No dark in it. */
    static final class SaintsDawn extends Art {
        static final int C = 0xFFE7A0, CORE = 0xFFFDF0, WARM = 0xFFC870;

        @Override
        float life(Fx fx) {
            return 2.3f;
        }

        static int allies(Fx fx) {
            return fx.points.isEmpty() ? 0 : Mth.clamp((int) Math.round(fx.points.get(0).x), 0, fx.points.size() - 1);
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0 && e != null && !ArtDivine.firstPerson(e)) {
                Vec3 p = e.position();
                FxParticles.motes(p.x, p.y + 0.4, p.z, 1.0, 2, C, 0.04f);
            }
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            Vec3 p = fx.at;
            FxParticles.rays(p.x, p.y + 3, p.z, 5f, C, 10);
            FxParticles.motesBurst(p.x, p.y + 1.5, p.z, FxParticles.count(20), 0.5f, C);
            int n = allies(fx);
            for (int i = 1; i < fx.points.size(); i++) {
                Vec3 a = fx.points.get(i);
                if (i <= n) FxParticles.motes(a.x, a.y + 1, a.z, 0.5, FxParticles.count(10), C, 0.05f);
                else FxParticles.sparks(a.x, a.y + 1, a.z, FxParticles.count(10), 0.4f, WARM, 0.03f);
            }
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            Skins.Look look = Skins.Look.LIGHT;
            float yaw = c.e != null ? c.e.getViewYRot(c.partial) : fx.yaw;
            Vec3 back = ArtFx.forward(yaw).scale(-0.45);
            if (c.k < 0) {
                float g = out(c.t / 0.6f);
                if (!c.self) {
                    Vec3 h = c.rel(c.live).add(0, 1.8 + 0.25 * g, 0).add(back);
                    Aura.halo(c.pose, c.buffers, (float) h.x, (float) h.y, (float) h.z, yaw, 0.35f + 0.7f * g, c.time * 1.5f, C, win(c.t, 0, 0.2f));
                }
                Skins.ring(c.pose, c.buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.05, 0), 1.3f, 0.16f, C, 0.8f * g, c.time * 0.02f, 3);
                return;
            }
            float k = c.k;
            float fade = 1 - win(k, 1.4f, 2.2f);
            // the sun: the halo rises behind the healer and spreads
            float rise = out(k / 0.3f);
            if (!c.self) {
                Vec3 sun = c.rel(c.live).add(0, 2.05 + 1.1 * rise, 0).add(back);
                Aura.halo(c.pose, c.buffers, (float) sun.x, (float) sun.y, (float) sun.z, yaw, 1.05f + 1.4f * rise, c.time * 1.2f, C, fade);
                Aura.halo(c.pose, c.buffers, (float) sun.x, (float) sun.y, (float) sun.z, yaw, 0.7f + 0.8f * rise, -c.time * 2f, CORE, fade * 0.9f);
                Aura.glint(c.pose, c.buffers, (float) sun.x, (float) sun.y, (float) sun.z, 1.6f + 1.2f * rise, CORE, fade * 0.8f, c.time * 0.02f);
            }
            // its light running out over the ground
            if (k < 0.9f) {
                float r = 0.5f + 7.5f * out(k / 0.6f);
                Skins.ring(c.pose, c.buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.06, 0), r, 0.4f, C, 1 - win(k, 0.3f, 0.9f), 0, 10);
                SkinFx.shock(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, r, 0.6f, 0.6f * (1 - win(k, 0.2f, 0.8f)), C, fx.seed);
            }
            // the party bathed in it, the foes burnt and dazzled
            int n = allies(fx);
            float camYaw = c.camera.getYRot();
            for (int i = 1; i < fx.points.size(); i++) {
                Vec3 p = c.rel(fx.points.get(i));
                if (i <= n) {
                    if (k < 0.8f) SkinFx.column(c.pose, c.buffers, c.camera, c.origin, p, 3.2f, 0.4f, 1 - win(k, 0.2f, 0.8f), look, C, c.time);
                    Aura.halo(c.pose, c.buffers, (float) p.x, (float) p.y + 2.25f, (float) p.z, camYaw, 0.42f, c.time * 2, C, fade * win(k, 0.1f, 0.3f));
                } else {
                    float f = out(win(k, 0.04f, 0.12f));
                    Vec3 top = p.add(0, 7, 0);
                    if (k < 0.18f) {
                        Skins.lance(c.pose, c.buffers, c.camera, c.origin, top.lerp(p.add(0, 0.3, 0), f), p.subtract(top), 1.6f, 0.4f, 4, look, C,
                                1 - win(k, 0.12f, 0.18f), c.time * 0.1f);
                    }
                    SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, p.add(0, 1, 0), p, 1.5f, k - 0.12f, 0.9f, 0.5f, look, C, fx.seed + i, 1);
                }
            }
        }
    }

    // ------------------------------------------------------------------ Galebreaker Warbow (drawn)

    /** Gale Lance: a full draw loosed drives a gale with the arrow through everything in a line: a tunnel of wind, rings of air bursting along it. */
    static final class GaleLance extends Art {
        static final int C = 0xA8F0E0, CORE = 0xF0FFFA;

        @Override
        float life(Fx fx) {
            return 1.3f;
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            if (fx.points.size() < 2) return;
            Vec3 a = fx.points.get(0), b = fx.points.get(1);
            Vec3 d = b.subtract(a);
            for (int i = 1; i <= 8; i++) {
                Vec3 p = a.add(d.scale(i / 8.0));
                FxParticles.flutter(FxParticles.Tex.LEAF, p.x, p.y, p.z, 0.6, 1, 0.12f, 0x9ACD70, 0.08f);
                FxParticles.add(FxParticles.Tex.WISP, p.x + rnd(0.6), p.y + rnd(0.6), p.z + rnd(0.6), d.x * 0.02, d.y * 0.02, d.z * 0.02).life(14)
                        .size(0.2f, 0.5f).color(0xE8FFF8).alpha(0.45f).drag(0.92f).spin(12);
            }
            for (int i = 2; i < fx.points.size(); i++) {
                Vec3 p = fx.points.get(i);
                FxParticles.dust(level, p.x, p.y - 1, p.z, 1.6f, FxParticles.count(5), 0.5f);
            }
            ArtFx.shake(a, 0.5f, 16, SkillShake.BLAST);
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            Skins.Look look = Skins.Look.AIR;
            if (c.k < 0) {
                // charged and held: a ring of wind turning round the arrow head
                if (c.self || !(c.e instanceof Player p) || ArtFx.gathering(p) < 1) return;
                Vec3 tip = ArtDivine.arrowTip(p, c.partial);
                ringAcross(c, tip, p.getViewVector(c.partial), 0.55f + 0.06f * Mth.sin(c.time * 0.8f), 0.2f, Skins.WIND, C, 0.8f, c.time * 0.15f);
                return;
            }
            if (fx.points.size() < 2) return;
            float k = c.k;
            Vec3 eye = fx.points.get(0), end = fx.points.get(1);
            Vec3 dir = end.subtract(eye);
            double len = dir.length();
            if (len < 1) return;
            Vec3 d = dir.scale(1 / len);
            Vec3 a = c.rel(eye.add(d.scale(Math.min(1.5, len * 0.3)))), b = c.rel(end);
            float run = out(win(k, 0, 0.12f));
            Vec3 front = a.lerp(b, run);
            float fade = 1 - win(k, 0.35f, 1.0f);
            // the tunnel of wind, the coils turning in it, the white line of the shot
            Skins.tube(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.WIND), a, front, 14, 12, s -> 0.4f + 0.8f * s * (0.6f + 0.4f * fade),
                    s -> Math.min(1, (1 - s) * 6 + 0.2f) * Math.min(1, s * 8), 0.75f * fade, C, -c.time * 0.08f, 3, 1.5f, -c.time * 0.2f, 0.3f, 0.85f);
            for (int j = 0; j < 3; j++) {
                Vec3[] coil = ArtShapes.helix(a, b, 0.75, s -> 0.6f + 0.6f * s, len / 4, c.time * 0.5 + j * 2.09, 40);
                ArtShapes.bent(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), coil, run, 0.1f, 0.06f, 0.8f * fade, 0.3f, CORE,
                        -c.time * 0.3f, 0.5f);
            }
            if (k < 0.14f) SkillWorldFx.streak(c.pose, c.buffers, c.camera, c.origin, a, front, 0.45f, 0xFFFFFF, 1 - k / 0.14f);
            if (k < 0.16f) Skins.lance(c.pose, c.buffers, c.camera, c.origin, front, d, 2.4f, 0.6f, 3, look, C, 1 - win(k, 0.1f, 0.16f), c.time * 0.2f);
            // rings of air burst along it as the front passes
            for (int i = 0; i < 6; i++) {
                float f = 0.12f + i * 0.16f;
                float age = k - f * 0.12f;
                if (age < 0 || age > 0.45f) continue;
                Vec3 p = eye.add(d.scale(Math.min(1.5, len * 0.3))).lerp(end, f);
                ringAcross(c, p, d, 0.4f + 1.6f * out(age / 0.35f), 0.2f, Skins.BEAM, CORE, 1 - win(age, 0.08f, 0.45f), age * 2);
            }
            for (int i = 2; i < fx.points.size(); i++) {
                Vec3 p = c.rel(fx.points.get(i));
                SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, p, null, 1.3f, k - 0.06f, 0.7f, 0.3f, look, C, fx.seed + i, 0.9f);
            }
        }
    }

    // ------------------------------------------------------------------ Nightveil Kris

    /** Veilstep: the assassin sinks into ink and comes out of it behind the foe; a dark cut on its back, ink smoke where they were. */
    static final class Veilstep extends Art {
        static final int C = 0x8A6CFF, EDGE = 0xB89CFF, INK = 0x0A0614;

        @Override
        float life(Fx fx) {
            return 1.3f;
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0 && e != null) {
                Vec3 p = e.position();
                FxParticles.wisps(p.x, p.y + 0.3, p.z, 0.5, 1, 0x0C0B18, 0.4f);
            }
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            if (fx.points.size() < 2) return;
            Vec3 from = fx.points.get(0), to = fx.points.get(1);
            FxParticles.smoke(from.x, from.y + 0.8, from.z, 0.5, FxParticles.count(8), 0x0C0B18, 0.7f, 0.03f);
            FxParticles.wisps(to.x, to.y + 0.5, to.z, 0.5, FxParticles.count(6), 0x0C0B18, 0.45f);
            if (fx.points.size() > 2) {
                Vec3 t = fx.points.get(2);
                FxParticles.sparks(t.x, t.y, t.z, FxParticles.count(12), 0.45f, EDGE, 0.03f);
                FxParticles.smoke(t.x, t.y, t.z, 0.3, FxParticles.count(4), 0x0C0B18, 0.5f, 0.03f);
                ArtFx.shake(t, 0.45f, 16, SkillShake.SWEEP);
            }
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            if (c.k < 0) {
                float g = out(c.t / 0.22f);
                Aura.pool(c.pose, c.buffers, 0.03f, 1.3f * g, C, 0.95f, c.time, 0.6f);
                if (!c.self) {
                    SkinFx.flames(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, 1.0f + 0.8f * g, 0.45f, 0.7f * g, Skins.Look.SHADOW, C, fx.seed, c.time);
                    ArtMythic.eyes(c, EDGE, g, 0.35f);
                }
                return;
            }
            if (fx.points.size() < 2) return;
            float k = c.k;
            Vec3 from = fx.points.get(0), to = fx.points.get(1);
            // ink thrown out where they sank, a pool where they rise
            ArtDivine.at(c, from, () -> Aura.splash(c.pose, c.buffers, 0.03f, 0.5f + 1.6f * out(k / 0.25f), C, 1 - win(k, 0.2f, 0.8f), fx.seed % 360));
            ArtDivine.at(c, to, () -> Aura.pool(c.pose, c.buffers, 0.03f, 1.2f * (1 - win(k, 0.3f, 0.7f)), C, 0.9f, c.time, 0.3f));
            // the step itself: a black seam through the air
            Vec3 a = c.rel(from.add(0, 1, 0)), b = c.rel(to.add(0, 1, 0));
            float seam = 1 - win(k, 0.05f, 0.35f);
            Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.PLASMA), a, b, 10, 0.9f, s -> 0.4f + 0.6f * s, seam, Skins.FLAT,
                    Skins.Look.SHADOW.body(C), c.time * 0.05f, 0.4f);
            Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), a, b, 10, 0.12f, Skins.FLAT, seam, Skins.FLAT, EDGE, 0, 0);
            // the cut on the foe's back
            if (fx.points.size() > 2) {
                Vec3 t = fx.points.get(2);
                Vec3 face = t.subtract(to).multiply(1, 0, 1);
                float yaw = face.lengthSqr() < 1.0e-4 ? fx.yaw : (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG);
                Vec3 at = to.add(0, 1.15, 0);
                ArtMythic.cut(ArtDivine.world(c), at, yaw, -35, 1.6f, k - 0.02f, 0.35f, 1, Skins.Look.SHADOW.body(C), Skins.Look.SHADOW, 1.1f);
                ArtDivine.slit(c, t, 60, 1.6f, k - 0.04f, 0.35f, INK, EDGE, 1);
                SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, c.rel(t), null, 1.5f, k - 0.03f, 0.9f, 0.4f, Skins.Look.SHADOW, C, fx.seed, 1);
            }
        }
    }

    // ------------------------------------------------------------------ Ironblood Claymore

    /** Blood Price: blood drawn out of the wielder onto the blade, then thrown as a great crescent wave of it. */
    static final class BloodPrice extends Art {
        static final int C = 0xD0244E, EDGE = 0xFF4A64, DARK = 0x2A0208, BLOOD = 0x8A0A1E;
        /** Blocks a second: the server's {@code LegendaryArts.BLOOD_WAVE_SPEED} a tick. */
        static final float SPEED = 24;

        @Override
        float life(Fx fx) {
            return 1.5f;
        }

        static float travel(Fx fx) {
            return fx.points.size() < 2 ? 0.3f : (float) fx.points.get(0).distanceTo(fx.points.get(1)) / SPEED;
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0) {
                if (e != null && !ArtDivine.firstPerson(e)) {
                    Vec3 p = e.position();
                    FxParticles.drops(p.x + rnd(0.3), p.y + 1.1, p.z + rnd(0.3), 1, 0.08f, BLOOD);
                }
                return;
            }
            if (fx.points.size() < 2) return;
            float tr = travel(fx);
            if (k <= tr + 0.05f) {
                Vec3 p = fx.points.get(0).lerp(fx.points.get(1), Math.min(1, k / tr));
                FxParticles.drops(p.x, p.y, p.z, 3, 0.2f, BLOOD);
                FxParticles.add(FxParticles.Tex.SOFT, p.x + rnd(0.8), p.y + rnd(0.6), p.z + rnd(0.8), 0, 0.01, 0).life(20).size(0.3f, 0.7f).color(0x5A0412)
                        .alpha(0.4f).blend();
            }
            if (k >= tr && ArtFx.once(fx, 0)) {
                Vec3 p = fx.points.get(1);
                FxParticles.drops(p.x, p.y, p.z, FxParticles.count(18), 0.4f, BLOOD);
                FxParticles.sparks(p.x, p.y, p.z, FxParticles.count(10), 0.4f, EDGE, 0.03f);
            }
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            Vec3 c = chest(fx);
            FxParticles.drops(c.x, c.y, c.z, FxParticles.count(12), 0.3f, BLOOD);
            ArtFx.shake(fx.at, 0.6f, 18, SkillShake.SWEEP);
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            if (c.k < 0) {
                float a = win(c.t, 0, 0.3f);
                if (!c.self) {
                    SkinFx.flames(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, 1.8f, 0.5f, a * 0.6f, Skins.Look.SHADOW, C, fx.seed, c.time);
                    ArtMythic.eyes(c, EDGE, a, 0.32f);
                    if (c.e != null) {
                        Vec3 h = c.rel(ArtFx.hand(c.e, c.partial));
                        Aura.glint(c.pose, c.buffers, (float) h.x, (float) h.y, (float) h.z, 0.5f + 0.6f * a, EDGE, a, c.time * 0.1f);
                    }
                }
                return;
            }
            if (fx.points.size() < 2) return;
            float k = c.k;
            Vec3 from = fx.points.get(0), to = fx.points.get(1);
            float tr = travel(fx);
            float f = Math.min(1, k / Math.max(0.05f, tr));
            float fade = 1 - win(k, tr, tr + 0.25f);
            // the swing that throws it
            ArtMythic.cut(c, chest(fx), fx.yaw, -8, 2.6f, k, 0.3f, 1, DARK, Skins.Look.SHADOW, 1);
            // the wave of blood running out
            Vec3 at = from.lerp(to, f).add(0, 0.3, 0);
            float grow = 2.0f + 1.4f * out(f * 2);
            wave(c, at, fx.yaw, 25, grow, 0.95f, DARK, EDGE, fade, -c.time * 0.05f);
            // the trail it leaves
            Vec3 a = c.rel(from), b = c.rel(at);
            Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.PLASMA), a, b, 10, 1.8f, s -> 0.2f + 0.8f * s,
                    0.6f * (1 - win(k, tr * 0.6f, tr + 0.4f)), s -> s * s, DARK, c.time * 0.05f, 0.3f);
            if (k >= tr) {
                Vec3 end = c.rel(to);
                SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, end, null, 1.9f, k - tr, 1.0f, 0.6f, Skins.Look.SHADOW, C, fx.seed, 1);
            }
        }
    }

    // ------------------------------------------------------------------ Sunderveil

    /** Sundering Verdict: a sword of light the size of a tree hangs over the ground ahead, then falls point first and splits it. */
    static final class SunderingVerdict extends Art {
        static final int C = 0xF4E2B0, CORE = 0xFFFFFF, CRIMSON = 0xE0405A;
        static final float LEN = 6.5f, HANG = 9f;

        @Override
        float life(Fx fx) {
            return 1.9f;
        }

        /** Where it falls: what the server said, or (in the wind-up) six blocks ahead of the wielder. */
        Vec3 mark(Fx fx, Entity e, float partial) {
            if (fx.impact >= 0 && !fx.points.isEmpty()) return fx.points.get(0);
            if (fx.data instanceof Vec3 v) return v;
            return (e != null ? e.getPosition(partial) : fx.at).add(fx.look.scale(6));
        }

        @Override
        void started(Fx fx, Entity e, ClientLevel level) {
            fx.data = ArtFx.ground(level, e.position().add(ArtFx.forward(e.getYRot()).scale(6)).add(0, 0.8, 0));
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            Vec3 p = mark(fx, e, 0);
            FxParticles.dust(level, p.x, p.y, p.z, 4.5f, FxParticles.count(12), 0.8f);
            FxParticles.sparks(p.x, p.y + 0.4, p.z, FxParticles.count(24), 0.7f, C, 0.05f);
            FxParticles.rays(p.x, p.y + 1.5, p.z, 5f, C, 7);
            ArtFx.shake(p, 0.9f, 22, SkillShake.SLAM);
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k >= 1.0f && ArtFx.once(fx, 0)) {
                Vec3 p = mark(fx, e, 0);
                FxParticles.shards(p.x, p.y + 2, p.z, FxParticles.count(26), 0.5f, CORE, 0.12f);
                FxParticles.motesBurst(p.x, p.y + 2, p.z, FxParticles.count(14), 0.35f, C);
            }
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            Skins.Look look = Skins.Look.LIGHT;
            Vec3 mark = c.rel(mark(fx, c.e, c.partial));
            Vec3 across = ArtFx.rightOf(fx.look);
            if (c.k < 0) {
                // the sword takes shape over the mark and hangs, trembling; the mark is drawn on the ground
                float a = win(c.t, 0, 0.35f);
                float hang = HANG + 0.12f * Mth.sin(c.time * 1.7f);
                SkinFx.sword(c.pose, c.buffers, c.camera, c.origin, mark.add(0, hang, 0), LEN, 0.75f, across, a, look, C, c.time);
                Skins.ring(c.pose, c.buffers, ModRenderTypes.energy(Skins.BEAM), mark.add(0, 0.06, 0), 1.2f + 0.6f * out(c.t / 0.5f), 0.18f, CRIMSON, a * 0.9f,
                        c.time * 0.02f, 4);
                Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), mark, mark.add(0, hang, 0), 8, 0.05f, Skins.FLAT,
                        a * 0.5f, s -> 1 - s * 0.5f, C, 0, 0);
                return;
            }
            float k = c.k;
            // it falls point first and buries itself, stands a moment, then shatters
            float fall = out(win(k, 0, 0.08f));
            float shatter = win(k, 1.0f, 1.08f);
            Vec3 tip = mark.add(0, Mth.lerp(fall, HANG, -1.4f), 0);
            SkinFx.sword(c.pose, c.buffers, c.camera, c.origin, tip, LEN, 0.75f, across, 1 - shatter, look, C, c.time);
            if (k < 0.12f) {
                Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.lightTexture(SkillWorldFx.STREAK), mark.add(0, HANG + LEN, 0), tip.add(0, LEN, 0),
                        6, 1.6f, Skins.FLAT, 1 - k / 0.12f, s -> s, C, 0, 0);
            }
            float land = k - 0.08f;
            if (land >= 0) {
                SkinFx.cracks(c.pose, c.buffers, c.camera, c.origin, mark, fx.seed, 4.8f, 8, out(land / 0.2f), 1 - win(land, 1.0f, 1.6f), 0.5f, look, C, c.time);
                SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, mark.add(0, 0.8, 0), mark, 4f, land, 1.6f, 0.5f, look, C, fx.seed, 1.1f);
                if (land < 0.12f) Skins.spikes(c.pose, c.buffers, c.camera, mark.add(0, 1, 0), fx.seed + 3, 14, 4f, 0.2f, CORE, 1 - land / 0.12f);
                Skins.ring(c.pose, c.buffers, ModRenderTypes.energy(Skins.BEAM), mark.add(0, 0.06, 0), 1.8f, 0.2f, CRIMSON, 0.8f * (1 - win(land, 0.8f, 1.4f)),
                        c.time * 0.02f, 4);
            }
            if (k >= 1.0f && k < 1.25f) {
                float s = k - 1.0f;
                Skins.spikes(c.pose, c.buffers, c.camera, mark.add(0, 2.5, 0), fx.seed + 9, 12, 3f, 0.14f, CORE, 1 - s / 0.25f);
            }
        }
    }

    // ------------------------------------------------------------------ Ashen Oath

    /** Pyre of Oaths: the sword is planted, six pillars of fire stand up round the wielder and the ground between them burns. */
    static final class PyreOfOaths extends Art {
        static final int C = 0xFF8A3A, HOT = 0xFFC870, COOL = 0x7A1A08, SOOT = 0x1E1714;
        static final float BURN = 3f;

        @Override
        float life(Fx fx) {
            return BURN + 0.6f;
        }

        @SuppressWarnings("unchecked")
        static List<Vec3> pillars(Fx fx) {
            return fx.data instanceof List<?> l ? (List<Vec3>) l : List.of();
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            List<Vec3> p = new ArrayList<>();
            float turn = (fx.seed & 63) / 63f * Mth.TWO_PI;
            for (int i = 0; i < 6; i++) {
                double a = turn + i * Math.PI / 3;
                p.add(ArtFx.ground(level, fx.at.add(Math.cos(a) * 3.5, 0.8, Math.sin(a) * 3.5)));
            }
            fx.data = p;
            FxParticles.fireRing(fx.at.x, fx.at.y, fx.at.z, 0.5, FxParticles.count(24), 0.5f, 0.3f, HOT, COOL);
            ArtFx.shake(fx.at, 0.6f, 18, SkillShake.SLAM);
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0) {
                if (e != null && !ArtDivine.firstPerson(e)) {
                    Vec3 p = e.position();
                    FxParticles.embers(p.x, p.y + 0.3, p.z, 1.2, 2, C);
                }
                return;
            }
            if (k > BURN) return;
            for (Vec3 p : pillars(fx)) {
                if (ArtFx.RANDOM.nextFloat() < 0.5f) FxParticles.fire(p.x, p.y + 0.2, p.z, 0.3, 1, 0.35f, 0.1f, HOT, COOL);
                if (ArtFx.RANDOM.nextFloat() < 0.15f) FxParticles.smoke(p.x, p.y + 4, p.z, 0.3, 1, SOOT, 0.7f, 0.06f);
            }
            double a = ArtFx.RANDOM.nextDouble() * Math.PI * 2, r = ArtFx.RANDOM.nextDouble() * 4.5;
            FxParticles.embers(fx.at.x + Math.cos(a) * r, fx.at.y + 0.2, fx.at.z + Math.sin(a) * r, 0.2, 1, C);
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            Skins.Look look = Skins.Look.FIRE;
            if (c.k < 0) {
                float g = out(c.t / 0.5f);
                SkinFx.cracks(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, fx.seed, 3.5f * g, 6, g, 0.8f, 0.2f, look, C, c.time);
                if (!c.self) ArtMythic.eyes(c, HOT, g, 0.3f);
                return;
            }
            float k = c.k;
            float fade = 1 - win(k, BURN, BURN + 0.5f);
            SkinFx.blast(c.pose, c.buffers, c.camera, c.origin, new Vec3(0, 0.8, 0), Vec3.ZERO, 3.5f, k, 1.4f, 0.7f, look, C, fx.seed, 1);
            SkinFx.cracks(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, fx.seed, 5f, 8, out(k / 0.25f), 0.9f * fade, 0.3f, look, C, c.time);
            Skins.ring(c.pose, c.buffers, ModRenderTypes.energy(Skins.FIRE), new Vec3(0, 0.08, 0), 3.5f, 1.0f, look.body(C), 0.8f * fade * out(k / 0.2f),
                    -c.time * 0.02f, 6);
            int i = 0;
            for (Vec3 p : pillars(fx)) {
                float rise = out(win(k, i * 0.03f, i * 0.03f + 0.18f));
                float flick = 0.85f + 0.15f * Mth.sin(c.time * 0.9f + i * 2);
                Vec3 base = c.rel(p);
                SkinFx.column(c.pose, c.buffers, c.camera, c.origin, base, 4.4f * rise * flick, 0.5f, fade * rise, look, C, c.time + i * 9);
                SkinFx.flames(c.pose, c.buffers, c.camera, c.origin, base, 2.4f * rise, 0.45f, fade * 0.7f, look, SkillWorldFx.mix(C, 0x3A0C04, 0.5f),
                        fx.seed + i, c.time);
                i++;
            }
        }
    }

    // ------------------------------------------------------------------ Nightglass Fang

    /** Nightglass Fan: seven shards of dark glass form in a fan before the hand and fly, each breaking into venom where it stops. */
    static final class NightglassFan extends Art {
        static final int C = 0x7CFF6A, GLASS = 0x0E1412, LIT = 0x2C3A34, VENOM = 0x3A8A2A;
        /** Blocks a second (the server's {@code LegendaryArts.SHARD_SPEED} a tick) and the fan's spread in degrees. */
        static final float SPEED = 30, FAN = 70;

        @Override
        float life(Fx fx) {
            return 1.2f;
        }

        static float arrive(Fx fx, int i) {
            return (float) fx.points.get(0).distanceTo(fx.points.get(i)) / SPEED;
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0 || fx.points.size() < 2) return;
            for (int i = 1; i < fx.points.size(); i++) {
                if (k >= arrive(fx, i) && ArtFx.once(fx, i)) {
                    Vec3 p = fx.points.get(i);
                    FxParticles.drops(p.x, p.y, p.z, FxParticles.count(6), 0.2f, VENOM);
                    FxParticles.shards(p.x, p.y, p.z, FxParticles.count(4), 0.25f, 0x2A3A30, 0.06f);
                    FxParticles.wisps(p.x, p.y, p.z, 0.2, FxParticles.count(2), C, 0.25f);
                }
            }
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            ArtShapes.Crystals glass = new ArtShapes.Crystals();
            if (c.k < 0) {
                if (c.e == null) return;
                // the shards form in a fan before the hand
                Vec3 look = c.e.getViewVector(c.partial);
                Vec3 hand = c.rel(c.e.getEyePosition(c.partial)).add(0, -0.25, 0).add(look.scale(c.self ? 0.9 : 0.6));
                for (int i = 0; i < 7; i++) {
                    float a = win(c.t, i * 0.03f, i * 0.03f + 0.1f);
                    Vec3 dir = look.yRot((-FAN / 2 + i * FAN / 6) * Mth.DEG_TO_RAD);
                    Vec3 base = hand.add(dir.scale(0.15));
                    glass.add(base, base.add(dir.scale(0.7)), 0.09f, GLASS, LIT, C, C, a, i);
                }
                glass.draw(c.pose, c.buffers);
                return;
            }
            if (fx.points.size() < 2) return;
            float k = c.k;
            Vec3 hand = fx.points.get(0);
            for (int i = 1; i < fx.points.size(); i++) {
                Vec3 end = fx.points.get(i);
                Vec3 dir = end.subtract(hand);
                if (dir.lengthSqr() < 0.01) continue;
                float t = arrive(fx, i);
                float f = Math.min(1, k / Math.max(0.02f, t));
                Vec3 head = c.rel(hand.lerp(end, f));
                Vec3 d = dir.normalize();
                float gone = win(k, t + 0.05f, t + 0.25f);
                glass.add(head.subtract(d.scale(1.25)), head, 0.15f, GLASS, LIT, C, C, 1 - gone, i);
                if (f < 1) {
                    Skins.line(c.pose, c.buffers, c.camera, c.origin, ModRenderTypes.energy(Skins.BEAM), c.rel(hand).lerp(head, 0.2), head, 4, 0.11f,
                            Skins.FLAT, 0.75f, s -> s, C, 0, 0);
                } else {
                    float age = k - t;
                    if (age < 0.16f) Skins.spikes(c.pose, c.buffers, c.camera, head, fx.seed + i, 7, 1.4f, 0.08f, C, 1 - age / 0.16f);
                }
            }
            glass.draw(c.pose, c.buffers);
        }
    }

    // ------------------------------------------------------------------ Thorn Requiem

    /** Requiem of Thorns: the scythe's turn wakes rings of blood thorns that burst out of the ground, running outward; a great thorn through every foe. */
    static final class RequiemOfThorns extends Art {
        static final int C = 0xC2185B, BODY = 0x3A0614, LIT = 0x8A1236, POINT = 0xFF7A9A;
        static final int RINGS = 5;

        @Override
        float life(Fx fx) {
            return 1.9f;
        }

        /** The thorns, worked out once: x, ground y, z, height, lean angle and the moment each bursts. */
        @SuppressWarnings("unchecked")
        static List<float[]> thorns(Fx fx) {
            return fx.data instanceof List<?> l ? (List<float[]>) l : List.of();
        }

        @Override
        void impact(Fx fx, Entity e, ClientLevel level) {
            List<float[]> out = new ArrayList<>();
            RandomSource r = RandomSource.create(fx.seed);
            for (int ring = 0; ring < RINGS; ring++) {
                double rad = 1.5 + ring * 1.1;
                int n = 8 + ring * 3;
                for (int i = 0; i < n; i++) {
                    double a = (i + r.nextFloat() * 0.7) / n * Math.PI * 2;
                    Vec3 g = ArtFx.ground(level, fx.at.add(Math.cos(a) * rad, 0.8, Math.sin(a) * rad));
                    out.add(new float[]{(float) g.x, (float) g.y, (float) g.z, 0.6f + r.nextFloat() * 0.8f, (float) a, ring * 0.06f});
                }
            }
            fx.data = out;
            for (int i = 1; i < fx.points.size(); i++) {
                Vec3 p = fx.points.get(i);
                FxParticles.drops(p.x, p.y + 1, p.z, FxParticles.count(14), 0.35f, 0x8A0A1E);
            }
            FxParticles.add(FxParticles.Tex.SHOCK, fx.at.x, fx.at.y + 0.1, fx.at.z, 0, 0, 0).flat().life(12).size(1f, 6.5f).color(C).alpha(0.6f).fade(0, 0.3f);
            ArtFx.shake(fx.at, 0.6f, 18, SkillShake.SLAM);
        }

        @Override
        void tick(Fx fx, Entity e, ClientLevel level, float t, float k) {
            if (k < 0) {
                if (e != null && !ArtDivine.firstPerson(e)) {
                    Vec3 p = e.position();
                    double a = ArtFx.RANDOM.nextDouble() * Math.PI * 2;
                    FxParticles.add(FxParticles.Tex.SOFT, p.x + Math.cos(a) * 1.4, p.y + 0.4, p.z + Math.sin(a) * 1.4, -Math.sin(a) * 0.06, 0.01, Math.cos(a) * 0.06)
                            .life(20).size(0.25f, 0.5f).color(0x5A0412).alpha(0.45f).blend();
                }
            }
        }

        @Override
        void draw(Ctx c) {
            Fx fx = c.fx;
            if (c.k < 0) {
                float g = out(c.t / 0.5f);
                Skins.ring(c.pose, c.buffers, ModRenderTypes.energy(Skins.PLASMA), new Vec3(0, 0.05, 0), 1.2f + 0.4f * g, 0.5f, BODY, 0.85f * g, c.time * 0.02f, 4);
                Skins.ring(c.pose, c.buffers, ModRenderTypes.energy(Skins.BEAM), new Vec3(0, 0.06, 0), 1.45f + 0.4f * g, 0.08f, POINT, 0.8f * g, 0, 4);
                if (!c.self) ArtMythic.eyes(c, POINT, g, 0.3f);
                return;
            }
            float k = c.k;
            ArtShapes.Crystals thorns = new ArtShapes.Crystals();
            float sink = win(k, 1.2f, 1.6f);
            for (float[] th : thorns(fx)) {
                float rise = out(win(k, th[5], th[5] + 0.07f));
                if (rise <= 0) continue;
                Vec3 base = c.rel(new Vec3(th[0], th[1] - 0.05, th[2]));
                float h = th[3] * rise * (1 - sink);
                Vec3 tip = base.add(Mth.cos(th[4]) * h * 0.45, h, Mth.sin(th[4]) * h * 0.45);
                thorns.add(base, tip, 0.14f + th[3] * 0.06f, BODY, LIT, POINT, C, 0.92f * (1 - sink * 0.6f), th[4]);
            }
            // a great thorn through every foe it caught
            for (int i = 1; i < fx.points.size(); i++) {
                Vec3 p = fx.points.get(i);
                float when = (float) Math.min(6, p.distanceTo(fx.at) * 0.9) / 20f;
                float rise = out(win(k, when, when + 0.06f));
                if (rise <= 0) continue;
                Vec3 base = c.rel(p).add(0, -0.1, 0);
                float h = 2.6f * rise * (1 - sink);
                thorns.add(base, base.add(0.25, h, 0.1), 0.32f, BODY, LIT, POINT, C, 0.95f, i);
                float age = k - when;
                if (age < 0.1f) Skins.spikes(c.pose, c.buffers, c.camera, base.add(0, 1.1, 0), fx.seed + i, 8, 1.4f, 0.08f, POINT, 1 - age / 0.1f);
            }
            thorns.draw(c.pose, c.buffers);
            SkinFx.shock(c.pose, c.buffers, c.camera, c.origin, Vec3.ZERO, 1 + 5.5f * out(k / 0.32f), 0.5f, 0.7f * (1 - win(k, 0.2f, 0.5f)), C, fx.seed);
            // the scythe's turn
            ArtMythic.cut(c, fx.at.add(0, 0.9, 0), fx.yaw, -6, 2.8f, k, 0.4f, 1, BODY, Skins.Look.SHADOW, 1);
        }
    }

    private ArtLegendary() {}
}
