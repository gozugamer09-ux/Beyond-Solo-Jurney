package com.beyondsolo.client.job;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.hud.StatusHud;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.client.skill.SkillEmblem;
import com.beyondsolo.job.JobRevealTimeline;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.network.JobRevealPayload;
import com.beyondsolo.network.SkipJobRevealPayload;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.PlayerClass;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The job change reveal on screen.
 *
 * <p>The class emblem appears in the middle and its five subclasses fly out around it (the rare one sealed and
 * gold, a question mark on it). The ring turns while a light jumps from emblem to emblem, slower and slower,
 * until it stops: the chosen subclass flies to the middle and bursts. A rare result shakes the screen, cracks it
 * with lightning and stamps "EXTREMELY RARE". Then the name card: name, what it does, and the skills it brings.</p>
 *
 * <p>Runs on game ticks like the evaluation reveal so it stays in step with the server's light show
 * ({@link JobRevealTimeline}); the camera circles the player meanwhile ({@link CutsceneCamera}).</p>
 */
public final class JobRevealOverlay {
    private static final float RING = JobRevealTimeline.RING / 20f;
    private static final float LOCK = JobRevealTimeline.LOCK / 20f;
    private static final int GOLD = 0xFFFFD166;
    private static final int STEPS = 20;
    private static final float[] STEP_AT = new float[STEPS];

    static {
        // each gap a bit longer than the last; the last one held
        double ratio = 1.16, sum = 0;
        for (int k = 0; k < STEPS - 1; k++) sum += Math.pow(ratio, k) * (k == STEPS - 2 ? 1.6 : 1);
        double base = (LOCK - 0.2 - (RING + 0.2)) / sum, t = RING + 0.2;
        for (int k = 0; k < STEPS; k++) {
            STEP_AT[k] = (float) t;
            if (k < STEPS - 1) t += base * Math.pow(ratio, k) * (k == STEPS - 2 ? 1.6 : 1);
        }
        STEP_AT[STEPS - 1] = LOCK - 0.2f;
    }

    private static final class Spark {
        float x, y, vx, vy, age, life, size;
        int color;
    }

    private static boolean active;
    private static int ticks;
    private static float offset, time, lastTime;
    private static boolean skippedToEnd;
    private static Subclass result = Subclass.BLOODRAGER;
    private static boolean flawless;
    private static List<Subclass> ring = List.of();
    /** Which ring index the light sits on at each roulette step (the last one is the result). */
    private static final int[] ROLL = new int[STEPS];
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    /** Jagged lightning for a rare result, rebuilt a few times a second. */
    private static final List<float[]> BOLTS = new ArrayList<>();
    private static float boltsAt = -1;

    // ---------------------------------------------------------------- state

    public static void start(JobRevealPayload p) {
        Subclass[] all = Subclass.values();
        result = all[Mth.clamp(p.subclass(), 0, all.length - 1)];
        flawless = p.flawless();
        ring = Subclass.of(result.base());
        int target = ring.indexOf(result);
        ROLL[STEPS - 1] = target;
        for (int k = STEPS - 2; k >= 0; k--) {
            int pick;
            do {
                pick = RANDOM.nextInt(ring.size());
            } while (pick == ROLL[k + 1]);
            ROLL[k] = pick;
        }
        // tease: the light lands on the sealed rare emblem a couple of times on the way
        ROLL[STEPS - 4] = ring.size() - 1 == ROLL[STEPS - 3] ? ROLL[STEPS - 4] : ring.size() - 1;
        active = true;
        ticks = 0;
        offset = 0;
        time = lastTime = 0;
        skippedToEnd = false;
        SPARKS.clear();
        BOLTS.clear();
        flags();
    }

    public static void reset() {
        active = false;
        SPARKS.clear();
        flags();
    }

    public static boolean active() {
        return active;
    }

    private static void flags() {
        StatusHud.setHidden(active);
        NotificationManager.setSuppressed(active);
    }

    private static float card() {
        return JobRevealTimeline.card(result.rare()) / 20f;
    }

    private static float finish() {
        return JobRevealTimeline.finish(result.rare()) / 20f;
    }

    /** The hide key: jumps to the name card, then to the end. Returns true if the press was used. */
    public static boolean skip() {
        if (!active) return false;
        if (time < card() - 0.2f) {
            offset += card() - time;
            SPARKS.clear();
        } else if (!skippedToEnd && time < finish() - 1f) {
            offset += finish() - 0.75f - time;
            skippedToEnd = true;
            PacketDistributor.sendToServer(SkipJobRevealPayload.INSTANCE);
        }
        return true;
    }

    public static void tick() {
        if (active && !Minecraft.getInstance().isPaused()) ticks++;
    }

    public static void frame(DeltaTracker delta) {
        if (!active) return;
        lastTime = time;
        time = Math.max(lastTime, (ticks + delta.getGameTimeDeltaPartialTick(true)) / 20f + offset);
        moments();
        updateSparks(time - lastTime);
        if (time >= finish() + 0.5f) reset();
    }

    private static boolean crossed(float at) {
        return lastTime < at && time >= at;
    }

    private static void moments() {
        if (crossed(0.1f)) play(SoundEvents.BEACON_POWER_SELECT, 0.7f, 0.6f);
        for (int i = 0; i < 5; i++) if (crossed(0.6f + i * 0.14f)) play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f + i * 0.12f, 0.8f);
        for (int k = 0; k < STEPS; k++) {
            if (crossed(STEP_AT[k])) play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.8f + 1.0f * k / (STEPS - 1f), 0.6f);
        }
        if (crossed(LOCK)) {
            burst(0, 0, Gfx.opaque(result.color()), 46, 170);
            play(SoundEvents.END_PORTAL_FRAME_FILL, 0.8f, 1f);
            play(SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 1.1f, 0.8f);
            if (result.rare()) {
                burst(0, 0, GOLD, 70, 260);
                play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
                play(SoundEvents.TOTEM_USE, 1.2f, 0.5f);
            }
        }
        if (result.rare()) {
            for (float at : new float[]{LOCK + 0.5f, LOCK + 1.1f, LOCK + 1.7f}) {
                if (crossed(at)) {
                    burst(0, 0, 0xFFFFFFFF, 22, 220);
                    play(SoundEvents.TRIDENT_THUNDER.value(), 1.3f, 0.6f);
                }
            }
        }
        if (crossed(card())) play(SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 0.8f);
        List<Skill> skills = Skills.forSubclass(result);
        for (int i = 0; i < skills.size(); i++) if (crossed(card() + 0.9f + i * 0.2f)) play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f + i * 0.15f, 0.3f);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ---------------------------------------------------------------- drawing

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        renderLetterbox(g);
        if (!active || mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float s = Mth.clamp(Math.min(w / 460f, h / 250f), 0.6f, 2.2f);
        float t = time;
        float old = Gfx.fade(1 - Anim.window(t, finish() - 0.7f, finish()));
        int color = Gfx.opaque(result.color());
        int classColor = SystemTheme.playerClass(result.base());

        float dim = Anim.outCubic(Anim.window(t, 0, 0.6f));
        Shapes.vignette(0, 0, w, h, 0.25f, 1.2f).fill(Gfx.alpha(0xFF02060E, 0.75f * dim)).draw(g);
        if (t >= LOCK) {
            float mood = Anim.window(t, LOCK, LOCK + 0.4f);
            Shapes.vignette(0, 0, w, h, 0.45f, 1.35f).fill(Gfx.alpha(result.rare() ? GOLD : color, 0.2f * mood)).additive().draw(g);
        }

        float shake = 0;
        if (result.rare()) shake = 4.5f * (1 - Anim.window(t, LOCK, LOCK + 2.2f)) * (t >= LOCK ? 1 : 0);
        else if (t >= LOCK) shake = 2f * (1 - Anim.window(t, LOCK, LOCK + 0.4f));

        g.pose().pushPose();
        g.pose().translate(w / 2f + (float) Math.sin(t * 73) * shake * s, h / 2f + (float) Math.cos(t * 61) * shake * s, 0);
        g.pose().scale(s, s, 1);
        float oldScale = SysFont.setContentScale(s);

        float toCard = Anim.inOutCubic(Anim.window(t, card() - 0.5f, card() + 0.2f));
        // header
        float headIn = Anim.window(t, 0.1f, 0.6f) * (1 - toCard);
        if (headIn > 0) {
            float o = Gfx.fade(headIn);
            String head = Component.translatable("job.beyondsolo.reveal.title").getString();
            SysFont.Line line = SysFont.line(head, 15, true);
            int chars = (int) Math.min(SysFont.length(line), t * 30);
            SysFont.spaced(g, head.substring(0, Math.min(head.length(), chars)), 0, -98, 15, 0xFFFFFFFF, true, 4f, 0.5f);
            String sub = Component.translatable("job.beyondsolo.reveal.sub", result.base().displayName()).getString();
            SysFont.spaced(g, sub, 0, -78, 7, Gfx.mix(classColor, 0xFFFFFFFF, 0.3f), false, 2f, 0.5f);
            Gfx.setAlpha(o);
        }

        // the wheel: the class in the middle, its five subclasses around it
        float wheelOut = 1 - Anim.window(t, card() - 0.6f, card() - 0.1f);
        if (wheelOut > 0) {
            float o = Gfx.fade(wheelOut);
            drawWheel(g, t, classColor);
            Gfx.setAlpha(o);
        }
        // the result: from its place on the ring to the middle, then up to the card
        if (t >= LOCK - 0.2f) drawResult(g, t, color, toCard);
        if (t >= card() - 0.3f) drawCard(g, t, color, classColor);
        drawSparks(g);
        if (result.rare() && t >= LOCK && t < card() + 0.3f) drawRareStamp(g, t);

        SysFont.setContentScale(oldScale);
        g.pose().popPose();

        if (result.rare() && t >= LOCK && t < LOCK + 2.4f) drawLightning(g, w, h, t);
        float flash = flash(t, LOCK, result.rare() ? 0.9f : 0.35f);
        if (result.rare()) flash += flash(t, LOCK + 0.5f, 0.35f) + flash(t, LOCK + 1.1f, 0.3f) + flash(t, LOCK + 1.7f, 0.25f);
        if (flash > 0.004f) Shapes.box(0, 0, w, h).fill(Gfx.alpha(result.rare() ? 0xFFFFF4D0 : 0xFFFFFFFF, Math.min(0.9f, flash))).additive().draw(g);

        // skip hint
        float hint = Anim.window(t, 1.2f, 1.8f) * (1 - Anim.window(t, finish() - 1.2f, finish() - 0.8f));
        if (hint > 0) {
            float o = Gfx.fade(hint * 0.8f);
            String label = Component.translatable("job.beyondsolo.reveal.skip").getString();
            String key = com.beyondsolo.client.ClientEvents.DISMISS.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
            float kw = Ui.keycapWidth(10, key);
            float lw = SysFont.width(label, 6.5f, false);
            float x0 = w - 12 - lw - 4 - kw;
            Ui.keycap(g, x0, h - 34, 10, key, SystemTheme.TEXT_SOFT);
            SysFont.text(g, label, x0 + kw + 4, h - 31.5f, 6.5f, SystemTheme.TEXT_SOFT);
            Gfx.setAlpha(o);
        }
        Gfx.setAlpha(old);
    }

    /** Black bars top and bottom while a cutscene runs. */
    public static void renderLetterbox(GuiGraphics g) {
        float b = Anim.inOutCubic(CutsceneCamera.bars());
        if (b <= 0.002f) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float bh = h * 0.11f * b;
        Shapes.box(0, 0, w, bh).fill(0xFF000000).draw(g);
        Shapes.box(0, h - bh, w, bh).fill(0xFF000000).draw(g);
        Shapes.box(0, bh, w, 1).fill(Gfx.alpha(0xFF6A3FD0, 0.35f * b)).draw(g);
        Shapes.box(0, h - bh - 1, w, 1).fill(Gfx.alpha(0xFF6A3FD0, 0.35f * b)).draw(g);
    }

    private static float ringAngle(int i, float t) {
        return (float) (-Math.PI / 2 + i * Math.PI * 2 / ring.size() + t * 0.25f);
    }

    private static void drawWheel(GuiGraphics g, float t, int classColor) {
        float appear = Anim.outBack(Anim.window(t, 0.2f, 0.7f));
        float radius = 58;
        // rings and the class emblem
        Shapes.ring(0, 0, radius, 0.8f).fill(Gfx.alpha(classColor, 0.35f * appear)).dashes(40, 0.5f).rotation(t * 0.02f).draw(g);
        Shapes.ring(0, 0, radius + 18, 0.6f).fill(Gfx.alpha(classColor, 0.2f * appear)).dashes(12, 0.3f).rotation(-t * 0.03f).draw(g);
        float cr = 20 * appear;
        if (cr > 0.5f) {
            Shapes.circle(0, 0, cr).fillV(0xF0142238, 0xF0070D1A).border(1.2f, classColor).glow(cr * 0.7f, Gfx.alpha(classColor, 0.35f)).draw(g);
            Shapes.icon(Icons.of(result.base()), 0, 0, cr * 1.15f).fill(Gfx.mix(classColor, 0xFFFFFFFF, 0.5f)).glow(3, Gfx.alpha(classColor, 0.6f)).draw(g);
        }
        int step = -1;
        for (int k = 0; k < STEPS; k++) if (t >= STEP_AT[k]) step = k;
        int lit = step < 0 ? -1 : ROLL[step];
        for (int i = 0; i < ring.size(); i++) {
            Subclass sub = ring.get(i);
            float fly = Anim.outCubic(Anim.window(t, 0.6f + i * 0.14f, 1.2f + i * 0.14f));
            if (fly <= 0) continue;
            float a = ringAngle(i, t);
            float x = (float) Math.cos(a) * radius * fly;
            float y = (float) Math.sin(a) * radius * fly;
            boolean isResult = sub == result;
            if (isResult && t >= LOCK - 0.2f) continue; // drawn by drawResult
            boolean on = i == lit && t < LOCK;
            float since = step >= 0 ? t - STEP_AT[step] : 1;
            float pop = on ? 1 + 0.22f * (1 - Anim.window(since, 0, 0.12f)) : 1;
            float fade = t >= LOCK ? 1 - Anim.window(t, LOCK, LOCK + 0.35f) : 1;
            float o = Gfx.fade(fade);
            float r = 13 * pop * (t >= LOCK ? 1 - 0.4f * Anim.window(t, LOCK, LOCK + 0.35f) : 1);
            emblem(g, sub, x, y, r, on ? 1f : 0.15f, sub.rare() && !(isResult && t >= LOCK));
            if (on) Shapes.ring(x, y, r + 4, 1).fill(Gfx.alpha(sub.rare() ? GOLD : Gfx.opaque(sub.color()), 0.9f)).glow(4, Gfx.alpha(sub.rare() ? GOLD : Gfx.opaque(sub.color()), 0.6f)).draw(g);
            // names under the emblems once the roulette runs
            float label = Anim.window(t, RING, RING + 0.5f) * (1 - Anim.window(t, LOCK - 0.3f, LOCK));
            if (label > 0) {
                float lo = Gfx.fade(label);
                String name = sub.rare() ? "? ? ?" : sub.displayName().getString();
                SysFont.draw(g, name, x, y + r + 4, 5.8f, on ? 0xFFFFFFFF : SystemTheme.TEXT_SOFT, on, 0.5f);
                Gfx.setAlpha(lo);
            }
            Gfx.setAlpha(o);
        }
    }

    private static void drawResult(GuiGraphics g, float t, int color, float toCard) {
        int i = ring.indexOf(result);
        float a = ringAngle(i, Math.min(t, LOCK));
        float home = Anim.outBack(Anim.window(t, LOCK - 0.2f, LOCK + 0.25f));
        float x = Anim.lerp((float) Math.cos(a) * 58, 0, home);
        float y = Anim.lerp((float) Math.sin(a) * 58, 0, home);
        float r = Anim.lerp(13, 30, home);
        // up to the card's top
        x = Anim.lerp(x, 0, toCard);
        y = Anim.lerp(y, -62, toCard);
        r = Anim.lerp(r, 22, toCard);
        int glowColor = result.rare() ? GOLD : color;
        if (t >= LOCK) {
            float glow = Anim.outCubic(Anim.window(t, LOCK, LOCK + 0.4f));
            Shapes.rays(x, y, (result.rare() ? 170 : 110) * (1 - 0.4f * toCard), result.rare() ? 16 : 10)
                    .fillV(Gfx.alpha(Gfx.mix(glowColor, 0xFFFFFFFF, 0.5f), 0.32f * glow), Gfx.alpha(glowColor, 0.3f * glow))
                    .rotation(t * 0.02f).additive().draw(g);
            shock(g, x, y, t, LOCK, r + 6, glowColor);
            if (result.rare()) {
                shock(g, x, y, t, LOCK + 0.5f, r + 6, 0xFFFFFFFF);
                shock(g, x, y, t, LOCK + 1.1f, r + 6, GOLD);
            }
        }
        float energy = t >= LOCK ? 1 - 0.6f * Anim.window(t, LOCK, LOCK + 1.5f) : 0.6f;
        emblem(g, result, x, y, r, energy, false);
    }

    /** A subclass emblem: a hex plate with its icon. {@code sealed} draws the rare one hidden: gold, with a question mark. */
    private static void emblem(GuiGraphics g, Subclass sub, float x, float y, float r, float energy, boolean sealed) {
        int color = sealed ? GOLD : Gfx.opaque(sub.color());
        boolean rare = sub.rare();
        Shapes.icon(Icons.HEXAGON, x, y, r * 2.46f).fillV(0xF0162640, 0xF0060C18)
                .border(Math.max(0.8f, r * 0.07f), color).glow(r * (0.4f + 0.6f * energy), Gfx.alpha(color, 0.3f + 0.45f * energy)).draw(g);
        if (rare) {
            Shapes.ring(x, y, r * 1.22f, Math.max(0.5f, r * 0.05f)).fill(Gfx.alpha(GOLD, 0.6f)).dashes(16, 0.5f).rotation(Anim.loop(9f)).draw(g);
        }
        if (sealed) {
            Shapes.icon(Icons.QUESTION, x, y, r * 1.2f).fill(Gfx.mix(GOLD, 0xFFFFFFFF, 0.4f)).glow(r * 0.25f, Gfx.alpha(GOLD, 0.7f)).draw(g);
        } else {
            Shapes.icon(sub.icon(), x, y, r * 1.25f).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.8f), Gfx.mix(color, 0xFFFFFFFF, 0.4f))
                    .glow(r * 0.2f + r * 0.25f * energy, Gfx.alpha(color, 0.6f)).draw(g);
        }
    }

    private static void drawRareStamp(GuiGraphics g, float t) {
        float in = Anim.window(t, LOCK + 0.15f, LOCK + 0.45f);
        if (in <= 0) return;
        float scale = Anim.lerp(2.6f, 1f, Anim.inCubic(in));
        float out = 1 - Anim.window(t, card() - 0.4f, card());
        float o = Gfx.fade(Math.min(1, in * 1.5f) * out);
        g.pose().pushPose();
        g.pose().translate(0, 52, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-6));
        g.pose().scale(scale, scale, 1);
        String text = Component.translatable("job.beyondsolo.reveal.rare").getString();
        float tw = SysFont.spacedWidth(text, 13, true, 3f) + 24;
        Shapes.box(-tw / 2f, -11, tw, 22).radius(4).fill(Gfx.alpha(0xFF2A1A00, 0.85f)).border(1.4f, GOLD).glow(10, Gfx.alpha(GOLD, 0.55f))
                .sheen(Anim.loop(1.6f) * 1.6f - 0.3f, 0.35f).draw(g);
        SysFont.spaced(g, text, 0, -4.5f, 13, Gfx.mix(GOLD, 0xFFFFFFFF, 0.35f), true, 3f, 0.5f);
        g.pose().popPose();
        Gfx.setAlpha(o);
    }

    private static void drawCard(GuiGraphics g, float t, int color, int classColor) {
        float in = Anim.outCubic(Anim.window(t, card() - 0.1f, card() + 0.5f));
        if (in <= 0) return;
        float o = Gfx.fade(in);
        List<Skill> skills = Skills.forSubclass(result);
        float cw = 300, ch = 150 + (result.canRaiseShadows() ? 12 : 0);
        float x0 = -cw / 2f, y0 = -40 + 12 * (1 - in);
        int edge = result.rare() ? GOLD : color;
        Shapes.box(x0, y0, cw, ch).radius(12).fillV(SystemTheme.GLASS_TOP, SystemTheme.GLASS_BOTTOM)
                .border(1f, Gfx.fade(edge, 0.95f), Gfx.fade(edge, 0.25f)).glow(14, Gfx.alpha(edge, 0.28f)).draw(g);
        float y = y0 + 12;
        // "WARRIOR  >  BLOODRAGER"
        String from = result.base().displayName().getString().toUpperCase(Locale.ROOT);
        float fw = SysFont.spacedWidth(from, 6.5f, true, 2f);
        String arrow = "  ›  ";
        SysFont.spaced(g, from + arrow + Component.translatable("job.beyondsolo.reveal.subclass").getString().toUpperCase(Locale.ROOT),
                0, y, 6.5f, Gfx.mix(classColor, 0xFFFFFFFF, 0.3f), true, 2f, 0.5f);
        y += 11;
        String name = result.displayName().getString().toUpperCase(Locale.ROOT);
        float nameIn = Anim.outBack(Anim.window(t, card() + 0.1f, card() + 0.5f));
        g.pose().pushPose();
        g.pose().translate(0, y + 9, 0);
        g.pose().scale(nameIn, nameIn, 1);
        SysFont.spaced(g, name, 0, -9, 20, 0xFFFFFFFF, true, 2.4f, 0.5f);
        g.pose().popPose();
        y += 26;
        // chips: element, rare, flawless
        List<String> chips = new ArrayList<>();
        chips.add(result.element().displayName().getString());
        if (result.rare()) chips.add(Component.translatable("job.beyondsolo.reveal.rare_chip").getString());
        if (flawless) chips.add(Component.translatable("job.beyondsolo.reveal.flawless").getString());
        float total = 0;
        for (String c : chips) total += SysFont.width(c, 6f, true) + 12 + 4;
        float cx = -total / 2f;
        for (int i = 0; i < chips.size(); i++) {
            int cc = i == 0 ? Gfx.opaque(result.element().color()) : GOLD;
            cx += Ui.chip(g, cx, y, 10, chips.get(i), 6f, cc, false) + 4;
        }
        y += 15;
        float descIn = Anim.window(t, card() + 0.4f, card() + 0.8f);
        float od = Gfx.fade(descIn);
        for (SysFont.Line line : SysFont.wrap(result.description(), 7f, false, cw - 30)) {
            SysFont.draw(g, line, 0, y, SystemTheme.TEXT, 0.5f);
            y += 9.5f;
        }
        Gfx.setAlpha(od);
        y = Math.max(y + 4, y0 + 86);
        Ui.divider(g, x0 + 16, y, cw - 32, Gfx.alpha(edge, 0.5f));
        y += 6;
        SysFont.spaced(g, Component.translatable("job.beyondsolo.reveal.skills").getString().toUpperCase(Locale.ROOT), 0, y, 5.8f, SystemTheme.TEXT_DIM, true, 2f, 0.5f);
        y += 10;
        float slot = 84;
        float sx = -(skills.size() - 1) * slot / 2f;
        for (int i = 0; i < skills.size(); i++) {
            Skill s = skills.get(i);
            float sIn = Anim.outBack(Anim.window(t, card() + 0.9f + i * 0.2f, card() + 1.3f + i * 0.2f));
            if (sIn <= 0) continue;
            float px = sx + i * slot;
            SkillEmblem.draw(g, s, px, y + 9, 9 * sIn, 0.5f, false);
            float os = Gfx.fade(Math.min(1, sIn));
            SysFont.draw(g, s.displayName().getString(), px, y + 22, 6.2f, 0xFFFFFFFF, true, 0.5f);
            String kind = Component.translatable(s.isActive() ? "job.beyondsolo.reveal.active" : "job.beyondsolo.reveal.passive").getString();
            SysFont.draw(g, kind, px, y + 30, 5.2f, SystemTheme.TEXT_DIM, false, 0.5f);
            Gfx.setAlpha(os);
        }
        if (result.canRaiseShadows()) {
            float on = Anim.window(t, card() + 1.6f, card() + 2.1f);
            float oo = Gfx.fade(on);
            SysFont.draw(g, Component.translatable("job.beyondsolo.reveal.shadows").getString(), 0, y0 + ch - 12, 6.2f,
                    Gfx.mix(color, 0xFFFFFFFF, 0.4f), false, 0.5f);
            Gfx.setAlpha(oo);
        }
        Gfx.setAlpha(o);
    }

    private static void shock(GuiGraphics g, float x, float y, float t, float at, float r0, int color) {
        float k = Anim.window(t, at, at + 0.7f);
        if (k <= 0 || k >= 1) return;
        float r = r0 + 120 * Anim.outCubic(k);
        Shapes.ring(x, y, r, 2.2f * (1 - k) + 0.4f).fill(Gfx.alpha(color, 0.8f * (1 - k))).glow(5, Gfx.alpha(color, 0.5f * (1 - k))).additive().draw(g);
    }

    private static float flash(float t, float at, float strength) {
        if (t < at) return 0;
        float k = (t - at) / 0.35f;
        return k >= 1 ? 0 : strength * (1 - k) * (1 - k);
    }

    /** Jagged bolts across the whole screen for a rare result. */
    private static void drawLightning(GuiGraphics g, int w, int h, float t) {
        if (BOLTS.isEmpty() || t - boltsAt > 0.12f) {
            boltsAt = t;
            BOLTS.clear();
            int n = 2 + RANDOM.nextInt(3);
            for (int b = 0; b < n; b++) {
                float x = RANDOM.nextFloat() * w, y = -10;
                float tx = w / 2f + (RANDOM.nextFloat() - 0.5f) * w * 0.4f;
                float ty = h / 2f + (RANDOM.nextFloat() - 0.5f) * h * 0.2f;
                int segs = 9;
                for (int i = 0; i < segs; i++) {
                    float k = (i + 1f) / segs;
                    float nx = Mth.lerp(k, x, tx) + (RANDOM.nextFloat() - 0.5f) * 40 * (1 - k);
                    float ny = Mth.lerp(k, -10, ty) + (RANDOM.nextFloat() - 0.5f) * 14;
                    BOLTS.add(new float[]{x, y, nx, ny});
                    x = nx;
                    y = ny;
                }
            }
        }
        float strength = (1 - Anim.window(t, LOCK, LOCK + 2.4f)) * (0.55f + 0.45f * (float) Math.abs(Math.sin(t * 40)));
        for (float[] s : BOLTS) {
            segment(g, s[0], s[1], s[2], s[3], 3.2f, Gfx.alpha(GOLD, 0.35f * strength));
            segment(g, s[0], s[1], s[2], s[3], 1.1f, Gfx.alpha(0xFFFFFFFF, 0.95f * strength));
        }
    }

    private static void segment(GuiGraphics g, float x1, float y1, float x2, float y2, float width, int color) {
        float len = (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
        g.pose().pushPose();
        g.pose().translate(x1, y1, 0);
        g.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(y2 - y1, x2 - x1)));
        Shapes.box(0, -width / 2f, len, width).fill(color).glow(width * 2, Gfx.alpha(color, 0.5f)).additive().draw(g);
        g.pose().popPose();
    }

    // ---------------------------------------------------------------- sparks

    private static void burst(float x, float y, int color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            Spark s = new Spark();
            double a = RANDOM.nextDouble() * Math.PI * 2;
            float v = speed * (0.35f + RANDOM.nextFloat() * 0.85f);
            s.x = x + (float) Math.cos(a) * 20;
            s.y = y + (float) Math.sin(a) * 20;
            s.vx = (float) Math.cos(a) * v;
            s.vy = (float) Math.sin(a) * v - 18;
            s.life = 0.7f + RANDOM.nextFloat() * 0.9f;
            s.size = 0.9f + RANDOM.nextFloat() * 1.5f;
            s.color = RANDOM.nextFloat() < 0.35f ? Gfx.mix(color, 0xFFFFFFFF, 0.7f) : color;
            SPARKS.add(s);
        }
    }

    private static void updateSparks(float dt) {
        if (SPARKS.isEmpty() || dt <= 0) return;
        float drag = (float) Math.exp(-2.6f * dt);
        for (Iterator<Spark> it = SPARKS.iterator(); it.hasNext(); ) {
            Spark s = it.next();
            s.age += dt;
            if (s.age >= s.life) {
                it.remove();
                continue;
            }
            s.vx *= drag;
            s.vy = s.vy * drag + 70f * dt;
            s.x += s.vx * dt;
            s.y += s.vy * dt;
        }
    }

    private static void drawSparks(GuiGraphics g) {
        for (Spark s : SPARKS) {
            float left = 1 - s.age / s.life;
            float r = s.size * (0.4f + 0.6f * left);
            Shapes.circle(s.x, s.y, r).fill(Gfx.alpha(s.color, Math.min(1f, left * 1.6f)))
                    .glow(r * 3f, Gfx.alpha(s.color, 0.55f * left)).additive().draw(g);
        }
    }

    private JobRevealOverlay() {}
}
