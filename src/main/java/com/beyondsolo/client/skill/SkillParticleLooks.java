package com.beyondsolo.client.skill;

import com.beyondsolo.client.skill.FxParticles.Tex;
import com.beyondsolo.network.SkillParticlesPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the particles a skill's server code throws ({@link SkillParticlesPayload}) with the soft particles: the same
 * spread and speed vanilla would have used, in the look that stands for the vanilla particle (flames that lick up,
 * blasts of smoke with a flash, motes, sparks, drops of blood...).
 */
public final class SkillParticleLooks {
    private static final RandomSource RANDOM = RandomSource.create();

    public static void play(SkillParticlesPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        SkillParticlesPayload.Look look = p.look();
        // a blast is one event, however many puffs it was asked for
        switch (look) {
            case EXPLOSION -> {
                FxParticles.flash(p.x(), p.y(), p.z(), 1.2f, 0xFFE8C8);
                FxParticles.sparks(p.x(), p.y(), p.z(), 5, 0.3f, 0xFFB060, 0.04f);
            }
            case BIG_EXPLOSION -> {
                FxParticles.flash(p.x(), p.y(), p.z(), 3.2f, 0xFFE0B0);
                FxParticles.shockwave(p.x(), p.y() - 0.5, p.z(), 6, 14, 0xFFE8D0, 0.8f);
                FxParticles.sparks(p.x(), p.y(), p.z(), 18, 0.6f, 0xFFA040, 0.05f);
                SkillShake.hit(new Vec3(p.x(), p.y(), p.z()), 0.5f, 32, SkillShake.BLAST);
            }
            case FLASH -> {
                FxParticles.flash(p.x(), p.y(), p.z(), 1.6f, 0xFFFFFF);
                return;
            }
            default -> {}
        }
        // vanilla's own reading of count, spread and speed: none means one, moving by the spread times the speed
        int n = p.count() <= 0 ? 1 : p.count();
        // the soft ones are larger than vanilla's specks, so fewer of them make the same cloud
        float keep = switch (look) {
            case FIRE, SOUL_FIRE, POOF, SMOKE, INK, SNEEZE -> 0.55f;
            case EXPLOSION, BIG_EXPLOSION -> 0.45f;
            default -> 1f;
        };
        n = FxParticles.count(Math.max(1, Math.round(n * keep)));
        for (int i = 0; i < n; i++) {
            double x = p.x(), y = p.y(), z = p.z(), vx, vy, vz;
            if (p.count() <= 0) {
                vx = p.dx() * p.speed();
                vy = p.dy() * p.speed();
                vz = p.dz() * p.speed();
            } else {
                x += RANDOM.nextGaussian() * p.dx();
                y += RANDOM.nextGaussian() * p.dy();
                z += RANDOM.nextGaussian() * p.dz();
                vx = RANDOM.nextGaussian() * p.speed();
                vy = RANDOM.nextGaussian() * p.speed();
                vz = RANDOM.nextGaussian() * p.speed();
            }
            one(look, p.color(), x, y, z, vx, vy, vz);
        }
    }

    private static void one(SkillParticlesPayload.Look look, int color, double x, double y, double z, double vx, double vy, double vz) {
        switch (look) {
            // a vanilla flame is a small lick of fire: kept small, so a line of them reads as fire and not as candles
            case FIRE -> flame(x, y, z, vx, vy, vz, 0xFFD27A, 0xFF5418);
            case SOUL_FIRE -> flame(x, y, z, vx, vy, vz, 0xD8FFFF, 0x2E8CD8);
            case EXPLOSION, BIG_EXPLOSION -> {
                float big = look == SkillParticlesPayload.Look.BIG_EXPLOSION ? 1.6f : 1f;
                FxParticles.add(Tex.SMOKE, x, y, z, vx * 0.3, vy * 0.3 + 0.02, vz * 0.3).frame(RANDOM.nextInt(16)).blend()
                        .life(FxParticles.rf(22, 36)).size(0.45f * big, FxParticles.rf(1.0f, 1.4f) * big).color(0x4E4743)
                        .alpha(0.55f).fade(0.04f, 0.3f).drag(0.88f).spin(FxParticles.rf(-1, 1));
                FxParticles.add(Tex.HOT, x, y, z, 0, 0, 0).life(6).size(0.6f * big, 0.9f * big).color(0xFFC890).alpha(0.5f).fade(0, 0.2f);
            }
            case MOTE -> FxParticles.add(Tex.HOT, x, y, z, vx, vy, vz).life(FxParticles.rf(16, 30)).size(FxParticles.rf(0.06f, 0.1f), 0.02f)
                    .color(0xFFFFFF, 0xFFF4D8).alpha(0.95f).fade(0.08f, 0.5f).drag(0.94f);
            case CRIT -> FxParticles.add(Tex.SPARK, x, y, z, vx + FxParticles.rnd(0.15), vy + FxParticles.rnd(0.15), vz + FxParticles.rnd(0.15))
                    .life(FxParticles.rf(6, 11)).size(FxParticles.rf(0.03f, 0.05f)).color(0xFFFFFF, 0xFFE070).alpha(1).fade(0, 0.5f)
                    .drag(0.88f).gravity(0.02f).stretch(2f);
            case LAVA -> {
                double a = RANDOM.nextDouble() * Math.PI * 2;
                FxParticles.add(Tex.EMBER, x, y, z, Math.cos(a) * 0.08, 0.2 + RANDOM.nextDouble() * 0.15, Math.sin(a) * 0.08)
                        .life(FxParticles.rf(16, 28)).size(FxParticles.rf(0.06f, 0.1f), 0.03f).color(0xFFE8A0, 0xE03C10).alpha(1).fade(0, 0.6f)
                        .gravity(0.035f).drag(0.97f).collide(0.3f).flicker(0.3f);
            }
            case SPARK -> FxParticles.add(Tex.SPARK, x, y, z, vx + FxParticles.rnd(0.12), vy + FxParticles.rnd(0.12), vz + FxParticles.rnd(0.12))
                    .life(FxParticles.rf(4, 9)).size(FxParticles.rf(0.025f, 0.045f)).color(0xFFFFFF, 0x7CC8FF).alpha(1).fade(0, 0.4f)
                    .drag(0.85f).stretch(2.4f);
            case SOUL -> FxParticles.add(Tex.WISP, x, y, z, vx * 0.5, vy * 0.5 + 0.03, vz * 0.5).life(FxParticles.rf(18, 30))
                    .size(0.12f, 0.22f).color(0xB8FFFF).alpha(0.8f).fade(0.1f, 0.45f).drag(0.94f).swirl(0.008f).spin(FxParticles.rf(-8, 8));
            case SNEEZE -> FxParticles.add(Tex.SMOKE, x, y, z, vx * 0.5, vy * 0.5, vz * 0.5).frame(RANDOM.nextInt(16)).blend()
                    .life(FxParticles.rf(18, 30)).size(0.15f, 0.4f).color(0x9CC860).alpha(0.5f).fade(0.1f, 0.35f).drag(0.9f);
            case HAPPY -> FxParticles.add(Tex.HOT, x, y, z, vx * 0.5, vy * 0.5 + 0.01, vz * 0.5).life(FxParticles.rf(14, 26))
                    .size(0.07f, 0.02f).color(0xFFFFFF, 0x78F078).alpha(0.9f).fade(0.1f, 0.5f).drag(0.94f);
            case POOF -> FxParticles.add(Tex.SMOKE, x, y, z, vx * 0.6, vy * 0.6, vz * 0.6).frame(RANDOM.nextInt(16)).blend()
                    .life(FxParticles.rf(14, 24)).size(0.2f, 0.5f).color(0xE6E2DC).alpha(0.5f).fade(0.05f, 0.3f).drag(0.88f);
            case SMOKE -> FxParticles.add(Tex.SMOKE, x, y, z, vx * 0.5, vy * 0.5 + 0.01, vz * 0.5).frame(RANDOM.nextInt(16)).blend()
                    .life(FxParticles.rf(22, 36)).size(0.2f, 0.55f).color(0x2C2826).alpha(0.5f).fade(0.1f, 0.35f).drag(0.92f).swirl(0.004f);
            case INK -> FxParticles.add(Tex.SMOKE, x, y, z, vx * 0.5, vy * 0.5, vz * 0.5).frame(RANDOM.nextInt(16)).blend()
                    .life(FxParticles.rf(20, 32)).size(0.25f, 0.6f).color(0x101016).alpha(0.65f).fade(0.05f, 0.35f).drag(0.9f);
            case SPLASH -> FxParticles.add(Tex.DROP, x, y, z, vx + FxParticles.rnd(0.08), Math.abs(vy) + 0.12 + RANDOM.nextDouble() * 0.1, vz + FxParticles.rnd(0.08))
                    .life(FxParticles.rf(12, 22)).size(FxParticles.rf(0.035f, 0.06f)).color(0xFFFFFF, 0x8CC8FF).alpha(0.9f).fade(0, 0.7f)
                    .gravity(0.045f).drag(0.97f).stretch(1.4f).collide(0);
            case PORTAL -> FxParticles.add(Tex.WISP, x, y, z, vx * 0.5, vy * 0.5, vz * 0.5).life(FxParticles.rf(14, 26))
                    .size(0.1f, 0.2f).color(0xC890FF).alpha(0.8f).fade(0.1f, 0.4f).drag(0.92f).spin(FxParticles.rf(-10, 10));
            case SNOW -> FxParticles.add(Tex.SHARD, x, y, z, vx, vy, vz).life(FxParticles.rf(16, 28)).size(FxParticles.rf(0.05f, 0.09f))
                    .color(0xFFFFFF, 0xBDE8FF).alpha(0.95f).fade(0, 0.6f).gravity(0.01f).drag(0.94f).spin(FxParticles.rf(-15, 15)).collide(0);
            case GLOW -> FxParticles.add(Tex.HOT, x, y, z, vx * 0.5, vy * 0.5, vz * 0.5).life(FxParticles.rf(16, 28)).size(0.08f, 0.02f)
                    .color(0xFFFFFF, 0x60FFE0).alpha(0.9f).fade(0.1f, 0.5f).drag(0.94f);
            case DUST -> {
                int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
                if (Math.max(r, Math.max(g, b)) > 120) {
                    FxParticles.add(Tex.HOT, x, y, z, vx, vy, vz).life(FxParticles.rf(14, 26)).size(FxParticles.rf(0.06f, 0.1f), 0.02f)
                            .color(SkillWorldFx.mix(color, 0xFFFFFF, 0.3f), color).alpha(0.9f).fade(0.08f, 0.5f).drag(0.94f);
                } else {
                    FxParticles.add(Tex.SMOKE, x, y, z, vx, vy, vz).frame(RANDOM.nextInt(16)).blend().life(FxParticles.rf(16, 28))
                            .size(0.12f, 0.3f).color(color).alpha(0.6f).fade(0.08f, 0.4f).drag(0.92f);
                }
            }
            case BLOOD -> FxParticles.add(Tex.DROP, x, y, z, vx + FxParticles.rnd(0.06), Math.abs(vy) * 0.5 + 0.06 + RANDOM.nextDouble() * 0.08, vz + FxParticles.rnd(0.06))
                    .life(FxParticles.rf(14, 24)).size(FxParticles.rf(0.04f, 0.07f)).color(0xC8202A, 0x6E0810).alpha(0.95f).fade(0, 0.75f)
                    .gravity(0.04f).drag(0.97f).stretch(1.2f).collide(0);
            case LEAF -> FxParticles.add(Tex.LEAF, x, y, z, vx, Math.abs(vy) * 0.5 + 0.04, vz).life(FxParticles.rf(30, 50))
                    .size(FxParticles.rf(0.07f, 0.11f)).color(0x6FB048).alpha(1).fade(0.05f, 0.75f).gravity(0.006f).drag(0.9f)
                    .swirl(0.02f).spin(FxParticles.rf(-12, 12)).collide(0);
            case FLASH -> {}
        }
    }

    private static void flame(double x, double y, double z, double vx, double vy, double vz, int hot, int cool) {
        FxParticles.add(Tex.FLAME, x, y, z, vx * 0.6, vy * 0.6 + 0.02, vz * 0.6).life(FxParticles.rf(8, 13))
                .size(FxParticles.rf(0.08f, 0.13f), FxParticles.rf(0.1f, 0.16f)).color(hot, cool).alpha(0.95f).fade(0.05f, 0.55f).drag(0.9f);
        if (RANDOM.nextFloat() < 0.4f) {
            FxParticles.add(Tex.HOT, x, y, z, vx * 0.4 + FxParticles.rnd(0.02), vy * 0.4 + 0.03, vz * 0.4 + FxParticles.rnd(0.02))
                    .life(FxParticles.rf(12, 22)).size(0.05f, 0.015f).color(hot, cool).alpha(0.9f).fade(0.05f, 0.5f).drag(0.94f);
        }
    }

    private SkillParticleLooks() {}
}
