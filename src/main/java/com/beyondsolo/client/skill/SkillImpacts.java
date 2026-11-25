package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.network.SkillHitPayload;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.skill.SkillFeel;
import com.beyondsolo.skill.SkillSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Where a skill's blows land, on everyone's screen ({@link SkillHitPayload}, weighed by {@link SkillFeel}). The struck
 * creature flashes white and hangs for a beat (the hitstop), shudders along the line of the blow, and sparks of the
 * skill's element fly off the side that was hit, with anime impact lines round the heavy ones; the hit is heard in
 * the element's own sound, with a thump under a heavy blow and a crack when it fells. The hunter's body hangs on the
 * blow with it, and the hunter's own view punches in and jolts. Heavy blows ripple the screen ({@link SkillScreen}).
 *
 * <p>The hold and the flash are drawn by hooks in the entity renderers and the client tick
 * ({@code com.beyondsolo.mixin}): a held creature skips its client ticks and is drawn at the moment it was struck.
 * The {@code combat.hitFeedback} option turns the creature's flash, hold and shudder off; the view's punch and the
 * screen's ripples go with {@code combat.screenEffects}.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class SkillImpacts {
    // ---- by weight: LIGHT, MEDIUM, HEAVY, MASSIVE ----
    /** Seconds of white. */
    private static final float[] FLASH = {0.07f, 0.1f, 0.13f, 0.17f};
    /** Seconds the struck creature hangs. */
    private static final float[] HOLD = {0f, 0.06f, 0.1f, 0.16f};
    /** How far it shudders, in blocks. */
    private static final float[] JOLT = {0.03f, 0.055f, 0.085f, 0.13f};
    private static final int[] SPARKS = {5, 9, 14, 22};
    private static final float[] FLARE = {0.45f, 0.7f, 1.05f, 1.5f};
    /** How far the hunter's view punches in (a share of the field of view). */
    private static final float[] PUNCH = {0f, 0.018f, 0.04f, 0.075f};
    /** How hard the hunter's view jolts. */
    private static final float[] KICK = {0.05f, 0.12f, 0.26f, 0.5f};
    private static final float[] VOLUME = {0.55f, 0.7f, 0.85f, 1f};

    /** After a hold the creature is not held again for this long, so a flurry or a storm never pins it in place. */
    private static final double REST = 0.25;
    /** How long a shudder lasts beyond the hold. */
    private static final float SHUDDER = 0.12f;

    private static final class Struck {
        double start;
        float flash, hold, jolt;
        double dx, dz;
        float pinned = -1;

        float left(double now) {
            return (float) (start + Math.max(flash, hold + SHUDDER) - now);
        }
    }

    private static final Map<Integer, Struck> STRUCK = new HashMap<>();
    private static final Map<Integer, Double> RESTED = new HashMap<>();

    private static float punch;
    private static double punchAt = -10;

    // ================================================================ a blow lands

    public static void onHit(SkillHitPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Entity caster = level.getEntity(p.casterId());
        boolean self = mc.player != null && p.casterId() == mc.player.getId();
        SkillElement element = SkillElement.values()[Mth.clamp(p.element(), 0, SkillElement.values().length - 1)];
        double now = Anim.now();
        boolean feedback = ClientConfig.HIT_FEEDBACK.get();
        int heaviest = -1;
        boolean killed = false;
        boolean repeats = true;
        Vec3 heaviestAt = null;
        int voiced = 0;
        int burst = 0;
        for (int i = 0; i < p.targets().length; i++) {
            if (!(level.getEntity(p.targets()[i]) instanceof LivingEntity e)) continue;
            int w = Mth.clamp(p.weights()[i], 0, 3);
            boolean kill = (p.flags()[i] & SkillFeel.KILL) != 0;
            boolean repeat = (p.flags()[i] & SkillFeel.REPEAT) != 0;
            Vec3 dir = direction(caster, e);
            Vec3 at = contact(e, dir);
            if (feedback) strike(e, w, kill, repeat, dir, now);
            // what it leaves behind: a mark on the ground under a heavy blow, a last breath off what it felled
            if (burst < 6 && (!repeat || w >= SkillFeel.HEAVY)) SkillAftermath.blow(level, e, element, element.color(), w, kill, p.ultimate() && !repeat);
            // a crowd struck at once: the first few get the whole burst, the rest a spark and a flash
            burst(level, element, at, dir, burst++ < 6 ? w : -1, kill, repeat, e);
            // the heaviest three are heard (a storm's ticks: the first)
            if (voiced < (repeat ? 1 : 3)) voice(level, element, at, w, kill, repeat, voiced++);
            if (w > heaviest) {
                heaviest = w;
                heaviestAt = at;
            }
            killed |= kill;
            repeats &= repeat;
        }
        if (heaviestAt == null) return;
        if (feedback && caster instanceof AbstractClientPlayer player && heaviest >= SkillFeel.MEDIUM && !repeats) {
            // the hunter's body hangs on the blow with the creature
            SkillGestures.hold(player, HOLD[heaviest] * 0.9f + (killed ? 0.04f : 0));
        }
        if (ClientConfig.SCREEN_EFFECTS.get()) {
            if (self && (!repeats || heaviest >= SkillFeel.HEAVY)) {
                punch = Math.max(punchNow(now), PUNCH[heaviest] + (killed ? 0.012f : 0));
                punchAt = now;
                SkillShake.hit(mc.player.position(), KICK[heaviest] * (repeats ? 0.5f : 1) + (killed ? 0.08f : 0), 8,
                        heaviest >= SkillFeel.HEAVY ? SkillShake.BLAST : SkillShake.SWEEP);
            } else if (!self && heaviest >= SkillFeel.HEAVY && !repeats) {
                SkillShake.hit(heaviestAt, heaviest == SkillFeel.MASSIVE ? 0.35f : 0.15f, 18, SkillShake.BLAST);
            }
        }
        if (!repeats || heaviest >= SkillFeel.HEAVY) SkillScreen.impact(heaviestAt, heaviest, p.ultimate() && !repeats, killed, element, self);
    }

    /** The line of the blow: from the hunter to the creature, flat. */
    private static Vec3 direction(Entity caster, Entity e) {
        Vec3 d = caster == null ? Vec3.ZERO : e.position().subtract(caster.position());
        d = new Vec3(d.x, 0, d.z);
        if (d.lengthSqr() < 1.0e-3) d = Vec3.directionFromRotation(0, e.getYRot()).scale(-1);
        return d.normalize();
    }

    /** Where it was struck: mid-body, on the side that faced the hunter. */
    private static Vec3 contact(Entity e, Vec3 dir) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0).subtract(dir.scale(e.getBbWidth() * 0.5));
    }

    private static void strike(LivingEntity e, int w, boolean kill, boolean repeat, Vec3 dir, double now) {
        Struck s = STRUCK.computeIfAbsent(e.getId(), k -> new Struck());
        float hold = repeat || e instanceof Player ? 0 : HOLD[w] + (kill && w >= SkillFeel.MEDIUM ? 0.05f : 0);
        Double rested = RESTED.get(e.getId());
        if (rested != null && now < rested) hold = 0;
        boolean live = s.left(now) > 0;
        float flash = (repeat ? 0.05f : FLASH[w]) + (kill ? 0.04f : 0);
        if (live && now < s.start + s.hold && hold <= 0) {
            // held already: the new blow only adds its flash
            s.flash = Math.max(s.flash, (float) (now - s.start) + flash);
            return;
        }
        s.start = now;
        s.flash = flash;
        s.hold = hold;
        s.jolt = (repeat ? 0.5f : 1) * JOLT[w];
        s.dx = dir.x;
        s.dz = dir.z;
        s.pinned = -1;
        if (hold > 0) RESTED.put(e.getId(), now + hold + REST);
    }

    // ================================================================ what the renderers ask

    /** True while any creature is flashing, held or shuddering: the render hooks do nothing otherwise. */
    public static boolean active() {
        return !STRUCK.isEmpty();
    }

    /** True while the creature hangs on a blow: its client tick is skipped. */
    public static boolean held(Entity e) {
        if (STRUCK.isEmpty()) return false;
        Struck s = STRUCK.get(e.getId());
        return s != null && s.hold > 0 && Anim.now() < s.start + s.hold;
    }

    /** The partial tick to draw the creature at: while it hangs, the one it was struck at. */
    public static float partial(Entity e, float partial) {
        if (STRUCK.isEmpty()) return partial;
        Struck s = STRUCK.get(e.getId());
        if (s == null || s.hold <= 0 || Anim.now() >= s.start + s.hold) return partial;
        if (s.pinned < 0) s.pinned = partial;
        return s.pinned;
    }

    /** How far the creature is pushed along the blow right now (blocks; zero when still). */
    public static Vec3 shudder(Entity e) {
        if (STRUCK.isEmpty()) return Vec3.ZERO;
        Struck s = STRUCK.get(e.getId());
        if (s == null || s.jolt <= 0) return Vec3.ZERO;
        float t = (float) (Anim.now() - s.start);
        float span = s.hold + SHUDDER;
        if (t < 0 || t >= span) return Vec3.ZERO;
        float fade = 1 - t / span;
        // knocked back along the blow at once, then shaking about it, dying away
        float along = s.jolt * fade * fade * (0.55f + 0.45f * Mth.cos(t * 2 * Mth.PI * 19f));
        float side = s.jolt * 0.45f * fade * fade * Mth.sin(t * 2 * Mth.PI * 27f + 1.3f);
        return new Vec3(s.dx * along - s.dz * side, 0, s.dz * along + s.dx * side);
    }

    /** The overlay a struck creature is drawn with: white while it flashes, or {@code normal}. */
    public static int overlay(Entity e, int normal) {
        if (STRUCK.isEmpty() || !(e instanceof LivingEntity)) return normal;
        Struck s = STRUCK.get(e.getId());
        if (s == null) return normal;
        float t = (float) (Anim.now() - s.start);
        if (t < 0 || t >= s.flash) return normal;
        // full white (as white as the overlay goes) for most of it, then a quick fade into the hurt red
        float k = t < s.flash * 0.6f ? 1 : 1 - (t - s.flash * 0.6f) / (s.flash * 0.4f);
        return OverlayTexture.pack(Math.round(15 * k), 10);
    }

    // ================================================================ the look of a blow

    private static void burst(ClientLevel level, SkillElement element, Vec3 at, Vec3 dir, int w, boolean kill, boolean repeat, LivingEntity e) {
        int c = element.color();
        double x = at.x, y = at.y, z = at.z;
        // sparks spray back toward the hunter and up off the side that was struck
        Vec3 back = dir.scale(-1).add(0, 0.35, 0);
        if (w < 0) {
            FxParticles.flash(x, y, z, 0.35f, c);
            FxParticles.sparksAlong(x, y, z, back, 0.8, 2, 0.25f, c, 0.03f);
            return;
        }
        float size = FLARE[w] * (repeat ? 0.6f : 1);
        int n = repeat ? Math.max(2, SPARKS[w] / 3) : SPARKS[w];
        FxParticles.flash(x, y, z, size, c);
        switch (element) {
            case PHYSICAL -> {
                FxParticles.sparksAlong(x, y, z, back, 0.75, n, 0.32f, 0xFFE8B0, 0.035f);
                if (!repeat) FxParticles.smoke(x, y - 0.2, z, 0.2, 1 + w, 0xC8C2B8, 0.25f, 0.01f);
            }
            case FIRE -> {
                FxParticles.sparksAlong(x, y, z, back, 0.8, n, 0.28f, 0xFF7A2E, 0.02f);
                FxParticles.embers(x, y, z, 0.25, n / 2 + 1, 0xFF5A1E);
                FxParticles.fire(x, y, z, 0.15, 2 + w, 0.22f + 0.06f * w, 0.035f, 0xFFE0A0, 0xFF4A10);
            }
            case WATER -> {
                FxParticles.drops(x, y, z, n, 0.22f + 0.04f * w, 0x4FA8FF);
                if (!repeat) FxParticles.add(FxParticles.Tex.SHOCK, x, y, z, 0, 0, 0).life(7).size(0.1f, 0.45f + 0.25f * w)
                        .color(0xFFFFFF, 0x4FA8FF).alpha(0.75f).fade(0, 0.3f);
            }
            case LIGHTNING -> {
                FxParticles.sparksAlong(x, y, z, back, 1.1, n + 3, 0.5f, 0xB8E2FF, 0.005f);
                FxParticles.flash(x, y, z, size * 0.7f, 0xE8F6FF);
                FxParticles.motesBurst(x, y, z, 3 + w, 0.2f, 0x9CD8FF);
            }
            case AIR -> {
                FxParticles.wisps(x, y, z, 0.25, n / 2 + 1, 0xA8F0E0, 0.2f + 0.05f * w);
                FxParticles.sparksAlong(x, y, z, dir, 0.6, n / 2 + 1, 0.4f, 0xDFFFF6, 0.0f);
            }
            case EARTH -> {
                FxParticles.shards(x, y, z, n / 2 + 2, 0.22f + 0.04f * w, 0xC9A35A, 0.08f + 0.02f * w);
                FxParticles.dust(level, e.getX(), e.getY(), e.getZ(), 1.2f + 0.6f * w, 2 + 2 * w, 0.3f);
                FxParticles.sparksAlong(x, y, z, back, 0.7, n / 2, 0.25f, 0xFFD890, 0.04f);
            }
            case SHADOW -> {
                // ink: dark smoke that bursts and curls, purple sparks through it
                for (int i = 0, k = FxParticles.count(3 + 2 * w); i < k; i++) {
                    Vec3 d = back.add(FxParticles.rnd(0.9), FxParticles.rnd(0.6), FxParticles.rnd(0.9)).normalize().scale(FxParticles.rf(0.05f, 0.14f));
                    FxParticles.add(FxParticles.Tex.SMOKE, x, y, z, d.x, d.y, d.z).frame(FxParticles.RANDOM.nextInt(16)).blend()
                            .life(FxParticles.rf(12, 22)).size(0.12f, 0.35f + 0.08f * w).color(0x14081F).alpha(0.85f)
                            .fade(0.02f, 0.4f).drag(0.84f).spin(FxParticles.rf(-4, 4));
                }
                FxParticles.sparksAlong(x, y, z, back, 0.8, n, 0.3f, 0x9B7BE0, 0.02f);
            }
            case LIGHT -> {
                FxParticles.rays(x, y, z, size * 0.9f, 0xFFF0A8, 7 + w);
                FxParticles.motesBurst(x, y, z, n, 0.16f, 0xFFE27A);
            }
            case NATURE -> {
                FxParticles.flutter(FxParticles.Tex.LEAF, x, y, z, 0.2, n / 2 + 1, 0.16f, 0x8CE05A, 0.1f);
                FxParticles.motesBurst(x, y, z, n / 2 + 1, 0.14f, 0xB8FF7A);
                FxParticles.sparksAlong(x, y, z, back, 0.7, n / 3 + 1, 0.25f, 0xD8FF9A, 0.03f);
            }
            case ARCANE -> {
                FxParticles.motesBurst(x, y, z, n, 0.2f, 0x7F9CFF);
                FxParticles.add(FxParticles.Tex.RIM, x, y, z, 0, 0, 0).life(6).size(0.1f, 0.35f + 0.2f * w)
                        .color(0xFFFFFF, 0x7F9CFF).alpha(0.8f).fade(0, 0.3f).spin(12);
            }
        }
        if (repeat) return;
        if (w >= SkillFeel.HEAVY) {
            // the anime answer to a heavy blow: lines bursting out from it across the view, and a ring of force
            impactLines(x, y, z, w == SkillFeel.MASSIVE ? 14 : 9, w == SkillFeel.MASSIVE ? 0.75f : 0.5f, c);
            FxParticles.add(FxParticles.Tex.SHOCK, x, y, z, 0, 0, 0).life(w == SkillFeel.MASSIVE ? 9 : 7)
                    .size(0.2f, w == SkillFeel.MASSIVE ? 2.6f : 1.5f).color(0xFFFFFF, c).alpha(0.9f).fade(0, 0.25f);
            FxParticles.sparksAlong(x, y, z, dir.add(0, 0.2, 0), 0.5, n / 2, 0.45f, 0xFFFFFF, 0.03f);
        }
        if (w == SkillFeel.MASSIVE) {
            FxParticles.shockwave(e.getX(), e.getY(), e.getZ(), 4.5f, 12, c, 0.7f);
            FxParticles.dust(level, e.getX(), e.getY(), e.getZ(), 3.5f, 8, 0.45f);
        }
        if (kill) {
            // felled: a last burst through it and the breath going out of it
            FxParticles.sparksAlong(x, y, z, dir.add(0, 0.5, 0), 0.9, 6 + 2 * w, 0.4f, c, 0.04f);
            FxParticles.motesBurst(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6 + 2 * w, 0.12f, c);
            FxParticles.smoke(e.getX(), e.getY() + e.getBbHeight() * 0.4, e.getZ(), e.getBbWidth() * 0.4, 3, 0x2A2630, 0.4f, 0.025f);
        }
    }

    /** Streaks bursting outward from (x, y, z) across the view, like the lines round a blow in a manhwa panel. */
    private static void impactLines(double x, double y, double z, int n, float speed, int rgb) {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = cam.getLeftVector();
        Vector3f up = cam.getUpVector();
        for (int i = 0, k = FxParticles.count(n); i < k; i++) {
            double a = (i + FxParticles.RANDOM.nextDouble() * 0.6) * Math.PI * 2 / k;
            double cx = Math.cos(a), sy = Math.sin(a);
            double dx = left.x() * cx + up.x() * sy, dy = left.y() * cx + up.y() * sy, dz = left.z() * cx + up.z() * sy;
            float sp = speed * FxParticles.rf(0.7f, 1.2f);
            FxParticles.add(FxParticles.Tex.SPARK, x + dx * 0.2, y + dy * 0.2, z + dz * 0.2, dx * sp, dy * sp, dz * sp)
                    .life(FxParticles.rf(4, 6)).size(FxParticles.rf(0.04f, 0.07f)).color(0xFFFFFF, rgb).alpha(1)
                    .fade(0, 0.35f).drag(0.7f).stretch(5f);
        }
    }

    // ================================================================ the sound of a blow

    private static void voice(ClientLevel level, SkillElement element, Vec3 at, int w, boolean kill, boolean repeat, int index) {
        float vol = VOLUME[w] * (repeat ? 0.45f : index == 0 ? 1f : 0.6f);
        // the heavier the blow, the lower it sounds
        float pitch = (1.08f - 0.06f * w) * FxParticles.rf(0.94f, 1.06f);
        play(level, at, SkillSounds.impact(element), vol, pitch);
        if (repeat || index > 0) return;
        if (w >= SkillFeel.HEAVY) play(level, at, SkillSounds.HEAVY.get(), w == SkillFeel.MASSIVE ? 1f : 0.75f, FxParticles.rf(0.92f, 1.05f));
        if (w == SkillFeel.MASSIVE) play(level, at, SkillSounds.MASSIVE.get(), 1f, FxParticles.rf(0.95f, 1.05f));
        if (kill) play(level, at, SkillSounds.KILL.get(), 0.8f, FxParticles.rf(0.95f, 1.1f));
    }

    private static void play(ClientLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    // ================================================================ a cast goes off

    /**
     * A hunter winds up for a skill ({@code SkillGestures}): a heavy skill's power is heard gathering; an ultimate
     * calls out now, the screen darkens round the hunter with lines rushing in, and the cut-in names it, gone as the
     * blow comes ({@link SkillScreen}, {@link SkillCutIn}).
     */
    static void windup(AbstractClientPlayer player, Skill skill, String gesture, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean self = player == mc.player;
        Vec3 at = player.position().add(0, player.getBbHeight() * 0.6, 0);
        if (SkillFeel.ultimate(skill)) {
            play(mc.level, at, SkillSounds.ULTIMATE.get(), 1f, 1f);
            play(mc.level, at, SkillSounds.CHARGE.get(), 0.9f, Mth.clamp(1.3f / Math.max(0.4f, ticks / 20f + 0.2f), 0.9f, 1.6f));
            SkillScreen.ultimate(player, skill, self);
            if (self && !skill.sovereign()) SkillCutIn.show(skill, ticks / 20f);
        } else if (SkillFeel.stature(skill) >= SkillFeel.HEAVY) {
            play(mc.level, at, SkillSounds.CHARGE.get(), 0.55f, 1.5f);
        }
    }

    /** A blade starts its swing after a wind-up: the air it cuts is heard just before the blow. */
    static void swing(AbstractClientPlayer player, String gesture) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean blade = switch (gesture) {
            case "slash", "slash_back", "chop", "claw", "spin", "sweep", "lunge", "throw", "throw_side" -> true;
            default -> false;
        };
        if (!blade) return;
        Vec3 at = player.position().add(0, player.getBbHeight() * 0.6, 0);
        play(mc.level, at, SkillSounds.SWING.get(), player == mc.player ? 0.45f : 0.35f, FxParticles.rf(1.05f, 1.2f));
    }

    /**
     * A skill went off ({@code SkillGestures}): the sound of its element leaving the hunter. One cast without a
     * wind-up ({@code wound} false: a command, a crown key) still makes an ultimate's call and the screen's answer here.
     */
    static void cast(AbstractClientPlayer player, Skill skill, String gesture, boolean wound) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean self = player == mc.player;
        Vec3 at = player.position().add(0, player.getBbHeight() * 0.6, 0);
        play(mc.level, at, SkillSounds.cast(skill.element()), self ? 0.75f : 0.6f, FxParticles.rf(0.94f, 1.06f));
        if (wound || !SkillFeel.ultimate(skill)) return;
        play(mc.level, at, SkillSounds.ULTIMATE.get(), 1f, 1f);
        SkillScreen.ultimate(player, skill, self);
        // a sovereign's power names itself already (the authority banner)
        if (self && !skill.sovereign()) SkillCutIn.show(skill, 1.1f);
    }

    // ================================================================ the hunter's view

    private static float punchNow(double now) {
        float t = (float) (now - punchAt) / 0.22f;
        if (t >= 1 || t < 0) return 0;
        float k = 1 - t;
        return punch * k * k;
    }

    @SubscribeEvent
    static void fov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) return;
        float p = punchNow(Anim.now());
        if (p > 0 && ClientConfig.SCREEN_EFFECTS.get()) event.setFOV(event.getFOV() * (1 - p));
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        if (STRUCK.isEmpty() && RESTED.isEmpty()) return;
        double now = Anim.now();
        Iterator<Map.Entry<Integer, Struck>> it = STRUCK.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue().left(now) <= 0) it.remove();
        RESTED.values().removeIf(until -> until < now);
    }

    @SubscribeEvent
    static void leave(ClientPlayerNetworkEvent.LoggingOut event) {
        STRUCK.clear();
        RESTED.clear();
        punch = 0;
    }

    private SkillImpacts() {}
}
