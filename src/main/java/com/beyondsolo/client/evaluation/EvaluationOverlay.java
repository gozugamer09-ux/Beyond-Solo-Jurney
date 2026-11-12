package com.beyondsolo.client.evaluation;

import com.beyondsolo.client.ClientEvents;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.hud.StatusHud;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.evaluation.EvaluationTimeline;
import com.beyondsolo.network.EvaluationPayload;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The on-screen part of the evaluation.
 *
 * <p>While the crystal scans, a small read-out shows the progress. Then the reveal takes over the screen:
 * a roulette of rank letters slows down and the rank drops in, the class emblem draws itself and the class
 * drops in, and a summary shows the starting stats. The server plays light and sound in the world at the
 * same moments (see {@link EvaluationTimeline}).</p>
 *
 * <p>The script runs on game ticks, not on the frame clock, so it stays in step with the server's effects
 * and stops when the game is paused.</p>
 */
public final class EvaluationOverlay {
    private enum Stage { NONE, SCAN, INTERRUPTED, REVEAL }

    // The reveal's script, in seconds from its start.
    private static final float LOCK = EvaluationTimeline.RANK_LOCK / 20f;
    private static final float CLASS = EvaluationTimeline.CLASS_START / 20f;
    private static final float CLASS_LOCK = EvaluationTimeline.CLASS_LOCK / 20f;
    private static final float STATS = EvaluationTimeline.STATS_START / 20f;
    private static final float FINISH = EvaluationTimeline.FINISH / 20f;
    /** The rank emblem leaves the middle to make room for the class. */
    private static final float RANK_OUT = CLASS - 0.5f;
    /** The class emblem leaves the middle to make room for the summary. */
    private static final float CLASS_OUT = STATS - 0.5f;
    /** "The System is now active". */
    private static final float READY = STATS + 1.7f;
    private static final float FADE_OUT = 0.6f;
    /** A revealed emblem drops towards the screen this long before its lock and lands exactly on it. */
    private static final float STAMP = 0.18f;

    // Layout, in units. One unit is one GUI pixel at the smallest size; the whole composition is scaled up on bigger screens.
    private static final float RANK_Y = -6;
    private static final float CLASS_Y = -4;
    private static final float TOP_Y = -92;
    private static final float HEADER_Y = -62;
    private static final float HEADER_SIZE = 17;
    private static final float RANK_RADIUS = 34;
    private static final float CLASS_RADIUS = 30;
    private static final float SMALL_RADIUS = 12;
    /** Icon size that makes the hexagon plate as wide as a circle of radius 1. */
    private static final float HEX = 2.46f;

    private static final int RANK_STEPS = 18;
    private static final int CLASS_STEPS = 10;
    /** When each roulette entry appears. The last entry is the real result. */
    private static final float[] RANK_STEP_AT = new float[RANK_STEPS];
    private static final float[] CLASS_STEP_AT = new float[CLASS_STEPS];

    static {
        schedule(RANK_STEP_AT, 0.45f, LOCK - STAMP, 1.13, 1.5);
        schedule(CLASS_STEP_AT, CLASS + 0.2f, CLASS_LOCK - STAMP, 1.2, 1.3);
    }

    /** Spreads roulette steps between two moments: each gap is {@code ratio} longer than the one before, and the last one is held. */
    private static void schedule(float[] at, float start, float end, double ratio, double hold) {
        int gaps = at.length - 1;
        double sum = 0;
        for (int k = 0; k < gaps; k++) sum += Math.pow(ratio, k) * (k == gaps - 1 ? hold : 1);
        double base = (end - start) / sum;
        double time = start;
        for (int k = 0; k < at.length; k++) {
            at[k] = (float) time;
            if (k < gaps) time += base * Math.pow(ratio, k) * (k == gaps - 1 ? hold : 1);
        }
        at[at.length - 1] = end;
    }

    private static final class Spark {
        float x, y, vx, vy, age, life, size;
        int color;
    }

    private static Stage stage = Stage.NONE;
    /** Game ticks since the current stage began. */
    private static int ticks;
    /** Seconds added by skipping. */
    private static float offset;
    private static float time;
    private static float lastTime;
    private static boolean skipped;

    private static float scanSeconds = EvaluationTimeline.SCAN_TICKS / 20f;
    private static float scanProgress;
    private static float scanShown;
    private static boolean scanFailed;

    private static PlayerRank rank = PlayerRank.E;
    private static PlayerClass playerClass = PlayerClass.WARRIOR;
    private static final int[] STATS_SHOWN = new int[Stat.values().length];
    private static final PlayerRank[] RANK_ROLL = new PlayerRank[RANK_STEPS];
    private static final PlayerClass[] CLASS_ROLL = new PlayerClass[CLASS_STEPS];
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final Random RANDOM = new Random();

    // ---------------------------------------------------------------- state

    public static void handle(EvaluationPayload payload) {
        switch (payload.phase()) {
            case EvaluationPayload.START -> {
                scanSeconds = Math.max(1, payload.scanTicks()) / 20f;
                scanProgress = 0;
                scanFailed = false;
                begin(Stage.SCAN);
            }
            case EvaluationPayload.CANCEL -> {
                if (stage == Stage.SCAN) {
                    scanFailed = true;
                    begin(Stage.INTERRUPTED);
                }
            }
            case EvaluationPayload.REVEAL -> {
                rank = PlayerRank.values()[Mth.clamp(payload.rank(), 0, PlayerRank.values().length - 1)];
                playerClass = PlayerClass.values()[Mth.clamp(payload.playerClass(), 0, PlayerClass.values().length - 1)];
                for (int i = 0; i < STATS_SHOWN.length; i++) {
                    STATS_SHOWN[i] = i < payload.stats().size() ? payload.stats().get(i) : Stat.BASE;
                }
                roll(RANK_ROLL, PlayerRank.values(), rank);
                roll(CLASS_ROLL, PlayerClass.values(), playerClass);
                SPARKS.clear();
                begin(Stage.REVEAL);
            }
            default -> {}
        }
    }

    /** Fills a roulette: random entries, never the same one twice in a row, ending on the real result. */
    private static <T> void roll(T[] into, T[] all, T result) {
        into[into.length - 1] = result;
        for (int k = into.length - 2; k >= 0; k--) {
            T pick;
            do {
                pick = all[RANDOM.nextInt(all.length)];
            } while (pick == into[k + 1]);
            into[k] = pick;
        }
    }

    private static void begin(Stage next) {
        stage = next;
        ticks = 0;
        offset = 0;
        time = 0;
        lastTime = 0;
        skipped = false;
        applyFlags();
    }

    private static void end() {
        stage = Stage.NONE;
        SPARKS.clear();
        applyFlags();
    }

    /** Keeps the rest of the HUD out of the way: the small status capsule would give the rank away early. */
    private static void applyFlags() {
        StatusHud.setHidden(stage == Stage.SCAN || stage == Stage.REVEAL);
        NotificationManager.setSuppressed(stage == Stage.REVEAL);
    }

    public static boolean active() {
        return stage != Stage.NONE;
    }

    /** The hide key during the reveal jumps to its end. Returns true if the key press was used up. */
    public static boolean skip() {
        if (stage != Stage.REVEAL) return false;
        if (time > 0.6f && time < FINISH - FADE_OUT - 0.1f) {
            offset += FINISH - FADE_OUT - time;
            skipped = true;
            SPARKS.clear();
        }
        return true;
    }

    public static void reset() {
        stage = Stage.NONE;
        scanShown = 0;
        SPARKS.clear();
    }

    /** Once per game tick. */
    public static void tick() {
        if (stage != Stage.NONE && !Minecraft.getInstance().isPaused()) ticks++;
    }

    /** Once per rendered frame: advances the script and fires its one-off moments. */
    public static void frame(DeltaTracker delta) {
        boolean scanVisible = stage == Stage.SCAN || (stage == Stage.INTERRUPTED && time < 0.45f);
        scanShown = Anim.approach(scanShown, scanVisible ? 1f : 0f, 10f);
        if (stage == Stage.NONE) return;

        lastTime = time;
        time = Math.max(lastTime, (ticks + delta.getGameTimeDeltaPartialTick(true)) / 20f + offset);
        switch (stage) {
            case SCAN -> {
                scanProgress = Mth.clamp(time / scanSeconds, 0f, 1f);
                // The server should have answered long ago; do not sit on the screen forever.
                if (time > scanSeconds + 5f) end();
            }
            case INTERRUPTED -> {
                if (time > 0.8f) end();
            }
            case REVEAL -> {
                if (skipped) skipped = false;
                else revealMoments();
                updateSparks(time - lastTime);
                if (time >= FINISH) end();
            }
            default -> {}
        }
    }

    private static boolean crossed(float moment) {
        return lastTime < moment && time >= moment;
    }

    private static void revealMoments() {
        int tier = rank.tier();
        float tickPitch = -1;
        for (int k = 0; k < RANK_STEPS; k++) {
            if (crossed(RANK_STEP_AT[k])) tickPitch = 0.75f + 1.1f * k / (RANK_STEPS - 1f);
        }
        for (int k = 0; k < CLASS_STEPS; k++) {
            if (crossed(CLASS_STEP_AT[k])) tickPitch = 0.95f + 0.9f * k / (CLASS_STEPS - 1f);
        }
        if (tickPitch > 0) play(SoundEvents.NOTE_BLOCK_HAT.value(), tickPitch, 0.55f);

        if (crossed(LOCK)) {
            burst(0, RANK_Y, SystemTheme.rank(rank), 26 + tier * 9, 125 + tier * 20);
            play(SoundEvents.END_PORTAL_FRAME_FILL, 0.75f, 1f);
            play(SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.9f + tier * 0.06f, 0.8f);
        }
        // S rank: two aftershocks, in step with the lightning the server calls down
        if (tier >= 5 && (crossed(LOCK + 0.28f) || crossed(LOCK + 0.55f))) {
            burst(0, RANK_Y, 0xFFFFFFFF, 14, 210);
        }
        if (crossed(CLASS_LOCK)) {
            burst(0, CLASS_Y, SystemTheme.playerClass(playerClass), 30, 135);
            play(SoundEvents.END_PORTAL_FRAME_FILL, 1.15f, 0.9f);
        }
        for (int i = 0; i < STATS_SHOWN.length; i++) {
            if (crossed(chipAt(i))) play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f + i * 0.12f, 0.3f);
        }
        if (crossed(READY)) play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.5f);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ---------------------------------------------------------------- sparks

    private static void burst(float x, float y, int color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            Spark s = new Spark();
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            float v = speed * (0.35f + RANDOM.nextFloat() * 0.85f);
            s.x = x + (float) Math.cos(angle) * 24;
            s.y = y + (float) Math.sin(angle) * 24;
            s.vx = (float) Math.cos(angle) * v;
            s.vy = (float) Math.sin(angle) * v - 18;
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

    // ---------------------------------------------------------------- drawing

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (stage != Stage.REVEAL && scanShown <= 0.01f) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float s = Mth.clamp(Math.min(w / 460f, h / 250f), 1f, 2.2f);
        if (stage == Stage.REVEAL) drawReveal(g, w, h, s);
        if (scanShown > 0.01f) drawScan(g, w, h, s);
    }

    // ---- scan read-out

    private static void drawScan(GuiGraphics g, int w, int h, float s) {
        boolean bad = scanFailed;
        boolean done = !bad && (stage != Stage.SCAN || scanProgress >= 1f);
        int accent = bad ? SystemTheme.DANGER : SystemTheme.ACCENT;
        float sinceFail = stage == Stage.INTERRUPTED ? time : 1f;
        float progress = bad ? scanProgress * (1 - Anim.outCubic(Anim.window(sinceFail, 0f, 0.45f))) : done ? 1f : scanProgress;
        float old = Gfx.fade(Anim.clamp01(scanShown));

        if (stage == Stage.SCAN || bad) {
            Shapes.vignette(0, 0, w, h, 0.5f, 1.3f).fill(Gfx.alpha(bad ? 0xFF8A0A18 : SystemTheme.ACCENT_DEEP, 0.32f)).draw(g);
        }
        if (stage == Stage.SCAN) {
            // a band of light sweeping down the screen
            float sweep = (time % 1.9f) / 1.9f;
            float y = -30 + (h + 60) * sweep;
            float edge = (float) Math.sin(sweep * Math.PI);
            Shapes.box(0, y - 52, w, 52).fillV(Gfx.alpha(SystemTheme.ACCENT, 0f), Gfx.alpha(SystemTheme.ACCENT, 0.13f * edge)).additive().draw(g);
            Shapes.box(0, y - 0.6f, w, 1.2f).fill(Gfx.alpha(SystemTheme.ACCENT_BRIGHT, 0.5f * edge))
                    .glow(5, Gfx.alpha(SystemTheme.ACCENT, 0.4f * edge)).additive().draw(g);
        }

        float pop = Anim.lerp(0.88f, 1f, Anim.outCubic(scanShown));
        float shakeX = bad ? (float) Math.sin(sinceFail * 60f) * 3f * (1 - Anim.window(sinceFail, 0f, 0.35f)) : 0;
        g.pose().pushPose();
        g.pose().translate(w / 2f + shakeX * s, h * 0.72f, 0);
        g.pose().scale(s * pop, s * pop, 1f);
        float oldScale = SysFont.setContentScale(s);

        float pw = 226;
        float ph = 48;
        float x0 = -pw / 2f;
        float y0 = -ph / 2f;
        Shapes.box(x0 + 1, y0 + 3, pw - 2, ph - 2).radius(ph / 2f).fill(0x00000000).glow(12, 0x66000000).draw(g);
        Shapes.pill(x0, y0, pw, ph).fillV(SystemTheme.GLASS_TOP, SystemTheme.GLASS_BOTTOM)
                .border(1f, Gfx.fade(accent, 0.95f), Gfx.fade(accent, 0.28f)).glow(10, Gfx.alpha(accent, 0.24f)).draw(g);

        // progress ring with the percentage inside
        float rx = x0 + 26;
        Shapes.ring(rx, 0, 15, 2.6f).fill(0x26FFFFFF).draw(g);
        if (progress > 0.004f) {
            Shapes.ring(rx, 0, 15, 2.6f).fillAround(Gfx.mix(accent, 0xFFFFFFFF, 0.6f), accent).progress(progress)
                    .glow(4, Gfx.alpha(accent, 0.6f)).draw(g);
        }
        Shapes.ring(rx, 0, 20.5f, 0.75f).fill(Gfx.alpha(accent, 0.5f)).dashes(24, 0.45f).rotation(Anim.loop(7f)).draw(g);
        SysFont.draw(g, Math.round(progress * 100) + "%", rx, SysFont.centerY(-6, 12, 8.5f), 8.5f, SystemTheme.TEXT, true, 0.5f);

        // what the crystal is doing right now, typed out
        float tx = x0 + 54;
        SysFont.spaced(g, tr(bad ? "evaluation.beyondsolo.scan.interrupted" : "evaluation.beyondsolo.scan.title"),
                tx, y0 + 9.5f, 8.5f, Gfx.mix(accent, 0xFFFFFFFF, 0.25f), true, 2.4f, 0f);
        String status;
        float typedFor = 10;
        if (bad) {
            status = tr("evaluation.beyondsolo.scan.lost");
        } else if (done) {
            status = tr("evaluation.beyondsolo.scan.done");
        } else {
            int step = Math.min(3, (int) (progress * 4));
            status = tr("evaluation.beyondsolo.scan.step" + (step + 1));
            typedFor = time - step * scanSeconds / 4f;
        }
        SysFont.Line line = SysFont.line(status, 8.2f, false);
        int chars = (int) Math.min(SysFont.length(line), Math.max(0, typedFor) * 48f);
        SysFont.draw(g, SysFont.truncate(line, chars), tx, y0 + 22f, SystemTheme.TEXT, 0f);
        SysFont.text(g, tr("evaluation.beyondsolo.scan.hint"), tx, y0 + 34.5f, 6.6f, SystemTheme.TEXT_DIM);

        // a little signal meter on the right
        for (int i = 0; i < 5; i++) {
            float level = bad || done ? 0.15f : 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(time * (5.2f + i * 1.3f) + i * 1.7f));
            float bh = 3 + 13 * level;
            Shapes.pill(x0 + pw - 40 + i * 4.6f, 8 - bh, 2.4f, bh).fill(Gfx.alpha(accent, 0.5f + 0.45f * level)).draw(g);
        }

        SysFont.setContentScale(oldScale);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    // ---- reveal

    private static void drawReveal(GuiGraphics g, int w, int h, float s) {
        float t = time;
        int tier = rank.tier();
        int rankColor = SystemTheme.rank(rank);
        int classColor = SystemTheme.playerClass(playerClass);
        float old = Gfx.fade(1 - Anim.window(t, FINISH - FADE_OUT, FINISH));

        // The world dims so the reveal owns the screen, and takes on the colour of what was just decided.
        float dim = Anim.outCubic(Anim.window(t, 0f, 0.5f));
        Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFF02060E, 0.70f * dim)).draw(g);
        Shapes.vignette(0, 0, w, h, 0.30f, 1.25f).fill(Gfx.alpha(0xFF000208, 0.80f * dim)).draw(g);
        float moodAmount = Anim.window(t, LOCK, LOCK + 0.4f);
        if (moodAmount > 0) {
            int mood = Gfx.mix(Gfx.mix(rankColor, classColor, Anim.window(t, CLASS_LOCK, CLASS_LOCK + 0.4f)),
                    SystemTheme.ACCENT, Anim.window(t, STATS, STATS + 0.6f));
            Shapes.vignette(0, 0, w, h, 0.5f, 1.4f).fill(Gfx.alpha(mood, (0.16f + 0.025f * tier) * moodAmount)).additive().draw(g);
        }

        // Everything else is laid out around the middle of the screen and scaled to fit it.
        float shake = shake(t, LOCK, 2.2f + tier * 1.1f) + shake(t, CLASS_LOCK, 2.2f);
        g.pose().pushPose();
        g.pose().translate(w / 2f + (float) Math.sin(t * 71f) * shake * s, h / 2f + (float) Math.cos(t * 57f) * shake * s, 0);
        g.pose().scale(s, s, 1f);
        float oldScale = SysFont.setContentScale(s);

        String rankTitle = Component.translatable("evaluation.beyondsolo.rank.title", rank.displayName()).getString().toUpperCase(Locale.ROOT);
        String classTitle = playerClass.displayName().getString().toUpperCase(Locale.ROOT);
        float rankW = SysFont.width(rankTitle, HEADER_SIZE, true);
        float headerW = rankW + 7 + SysFont.width(classTitle, HEADER_SIZE, true);

        // The rank emblem starts big in the middle, steps up to make room for the class,
        // and both end as small emblems at either end of the summary's headline.
        float rankUp = Anim.inOutCubic(Anim.window(t, RANK_OUT, CLASS));
        float toHeader = Anim.inOutCubic(Anim.window(t, CLASS_OUT, STATS));
        float rx = Anim.lerp(0, -(headerW / 2f + 21), toHeader);
        float ry = Anim.lerp(Anim.lerp(RANK_Y, TOP_Y, rankUp), HEADER_Y, toHeader);
        float rr = Anim.lerp(RANK_RADIUS, SMALL_RADIUS, rankUp);
        float cx = Anim.lerp(0, headerW / 2f + 21, toHeader);
        float cy = Anim.lerp(CLASS_Y, HEADER_Y, toHeader);
        float cr = Anim.lerp(CLASS_RADIUS, SMALL_RADIUS, toHeader);

        drawRank(g, t, rx, ry, rr, 1 - Anim.window(t, RANK_OUT, RANK_OUT + 0.3f), rankTitle);
        if (t >= CLASS) drawClass(g, t, cx, cy, cr, 1 - Anim.window(t, CLASS_OUT, CLASS_OUT + 0.3f), classTitle);
        if (t >= STATS - 0.1f) drawSummary(g, t, rankTitle, classTitle, rankW, headerW);
        drawSparks(g);

        SysFont.setContentScale(oldScale);
        g.pose().popPose();

        // a flash of light on each lock
        float flash = flash(t, LOCK, 0.30f + 0.07f * tier) + flash(t, CLASS_LOCK, 0.22f);
        if (tier >= 5) flash += flash(t, LOCK + 0.28f, 0.22f) + flash(t, LOCK + 0.55f, 0.16f);
        if (flash > 0.004f) Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFFFFFFFF, Math.min(0.85f, flash))).additive().draw(g);

        drawSkipHint(g, w, h, t);
        Gfx.setAlpha(old);
    }

    private static void drawRank(GuiGraphics g, float t, float x, float y, float r, float big, String rankTitle) {
        float appear = Anim.window(t, 0.15f, 0.5f);
        if (appear <= 0) return;
        int tier = rank.tier();
        int rankColor = SystemTheme.rank(rank);
        boolean decided = t >= LOCK - STAMP;
        int step = step(RANK_STEP_AT, t);
        PlayerRank shown = decided ? rank : step < 0 ? null : RANK_ROLL[step];
        int color = shown == null ? SystemTheme.ACCENT : SystemTheme.rank(shown);
        float build = Anim.window(t, RANK_STEP_AT[0], LOCK - STAMP);
        float old = Gfx.fade(Anim.outCubic(appear));

        // light behind the emblem once the rank is in
        if (t >= LOCK && big > 0) {
            float oldBig = Gfx.fade(big);
            float glow = Anim.outCubic(Anim.window(t, LOCK, LOCK + 0.35f));
            float strength = (0.34f + 0.08f * tier) * glow;
            Shapes.rays(x, y, 118 + tier * 14, 8 + tier).fillV(Gfx.alpha(Gfx.mix(rankColor, 0xFFFFFFFF, 0.55f), strength), Gfx.alpha(rankColor, strength))
                    .rotation(t * 0.018f).additive().draw(g);
            if (tier >= 3) {
                Shapes.rays(x, y, 80 + tier * 10, 5 + tier).fillV(Gfx.alpha(0xFFFFFFFF, 0.22f * glow), Gfx.alpha(rankColor, 0.2f * glow))
                        .rotation(-t * 0.03f + 0.2f).additive().draw(g);
            }
            float pulse = 0.85f + 0.15f * Anim.pulse(1.6f);
            Shapes.circle(x, y, r * 0.9f).fill(0x00000000).glow(r * (1.5f + 0.22f * tier) * pulse, Gfx.alpha(rankColor, (0.34f + 0.05f * tier) * glow))
                    .additive().draw(g);
            shockwaves(g, x, y, t, LOCK, r + 10, rankColor, tier >= 3 ? 3 : 2, 110 + tier * 16);
            Gfx.setAlpha(oldBig);
        }

        // energy streaming in while the rank is being decided
        if (!decided && build > 0) streaks(g, x, y, t, build, color);

        float jolt = t >= LOCK ? 0.14f * sq(1 - Anim.window(t, LOCK, LOCK + 0.3f)) : 0;
        float er = r * Anim.lerp(0.55f, 1f, Anim.outBack(appear)) * (1 + jolt);

        // the dial around the emblem
        if (big > 0) {
            float oldBig = Gfx.fade(big);
            float turn = dialTurn(t);
            Shapes.ring(x, y, er + 7.5f, 0.9f).fill(Gfx.alpha(color, 0.75f)).dashes(30, 0.5f).rotation(turn).draw(g);
            Shapes.ring(x, y, er + 11.5f, 0.75f).fill(Gfx.alpha(color, 0.45f)).dashes(10, 0.28f).rotation(-turn * 0.6f).draw(g);
            // a meter fills until the rank locks, then is thrown off as a ring of light
            float gone = Anim.window(t, LOCK, LOCK + 0.3f);
            if (gone < 1) {
                float oldMeter = Gfx.fade(1 - gone);
                float mr = er + 15.5f + 26 * Anim.outCubic(gone);
                Shapes.ring(x, y, mr, 1.6f).fill(0x1FFFFFFF).draw(g);
                float filled = decided ? 1f : build;
                if (filled > 0.004f) {
                    Shapes.ring(x, y, mr, 1.6f).fillAround(SystemTheme.ACCENT_BRIGHT, color).progress(filled).glow(3.5f, Gfx.alpha(color, 0.55f)).draw(g);
                }
                Gfx.setAlpha(oldMeter);
            }
            Gfx.setAlpha(oldBig);
        }

        // the emblem itself
        float energy = t >= LOCK ? 0.45f + 0.55f * (1 - Anim.window(t, LOCK, LOCK + 1.2f)) : 0.15f + 0.25f * build;
        Shapes.circle(x, y, er).fillV(0xF0142238, 0xF0070D1A).border(Math.max(0.75f, er * 0.07f), color, Gfx.fade(color, 0.45f))
                .glow(er * (0.5f + 0.7f * energy), Gfx.alpha(color, 0.28f + 0.42f * energy)).draw(g);
        Shapes.ring(x, y, er * 0.82f, Math.max(0.5f, er * 0.022f)).fill(Gfx.alpha(color, 0.3f)).draw(g);
        if (big < 1) {
            Shapes.ring(x, y, er * 1.24f, Math.max(0.6f, er * 0.06f)).fill(Gfx.alpha(color, 0.75f * (1 - big))).dashes(18, 0.45f)
                    .rotation(Anim.loop(18f)).draw(g);
        }
        if (shown != null) {
            float scale = 1f;
            float alpha = 1f;
            if (!decided) {
                scale = 1 + 0.24f * (1 - Anim.window(t, RANK_STEP_AT[step], RANK_STEP_AT[step] + 0.09f));
            } else if (t < LOCK) {
                float u = Anim.window(t, LOCK - STAMP, LOCK);
                scale = Anim.lerp(2.7f, 1f, Anim.inCubic(u));
                alpha = Anim.lerp(0.2f, 1f, u);
            }
            float oldLetter = Gfx.fade(alpha);
            Shapes.icon(Icons.of(shown), x, y + er * 0.02f, er * 1.5f * scale).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.8f), Gfx.mix(color, 0xFFFFFFFF, 0.25f))
                    .glow(er * 0.3f, Gfx.alpha(color, 0.75f)).draw(g);
            Gfx.setAlpha(oldLetter);
        }

        // words
        if (big > 0) {
            float oldBig = Gfx.fade(big);
            float swap = Anim.window(t, LOCK, LOCK + 0.25f);
            float labelY = y - RANK_RADIUS - 30;
            label(g, tr("evaluation.beyondsolo.rank.pending"), labelY, SystemTheme.ACCENT, 1 - swap);
            label(g, tr("evaluation.beyondsolo.rank.assigned"), labelY, Gfx.mix(rankColor, 0xFFFFFFFF, 0.3f), swap);
            if (t >= LOCK) {
                float in = Anim.outCubic(Anim.window(t, LOCK + 0.08f, LOCK + 0.45f));
                title(g, rankTitle, y + RANK_RADIUS + 24, 26, rankColor, in);
                typed(g, tr("evaluation.beyondsolo.rank." + rank.name().toLowerCase(Locale.ROOT)), y + RANK_RADIUS + 55, t - (LOCK + 0.45f));
            }
            Gfx.setAlpha(oldBig);
        }
        Gfx.setAlpha(old);
    }

    private static void drawClass(GuiGraphics g, float t, float x, float y, float r, float big, String classTitle) {
        float appear = Anim.window(t, CLASS, CLASS + 0.35f);
        if (appear <= 0) return;
        int classColor = SystemTheme.playerClass(playerClass);
        boolean decided = t >= CLASS_LOCK - STAMP;
        int step = step(CLASS_STEP_AT, t);
        PlayerClass shown = decided ? playerClass : step < 0 ? null : CLASS_ROLL[step];
        int color = shown == null ? SystemTheme.ACCENT : SystemTheme.playerClass(shown);
        float build = Anim.window(t, CLASS, CLASS_LOCK - STAMP);
        float old = Gfx.fade(Anim.outCubic(appear));

        if (t >= CLASS_LOCK && big > 0) {
            float oldBig = Gfx.fade(big);
            float glow = Anim.outCubic(Anim.window(t, CLASS_LOCK, CLASS_LOCK + 0.35f));
            Shapes.rays(x, y, 124, 10).fillV(Gfx.alpha(Gfx.mix(classColor, 0xFFFFFFFF, 0.5f), 0.42f * glow), Gfx.alpha(classColor, 0.42f * glow))
                    .rotation(-t * 0.02f).additive().draw(g);
            Shapes.circle(x, y, r * 0.9f).fill(0x00000000).glow(r * 1.7f * (0.85f + 0.15f * Anim.pulse(1.6f)), Gfx.alpha(classColor, 0.36f * glow))
                    .additive().draw(g);
            shockwaves(g, x, y, t, CLASS_LOCK, r + 10, classColor, 2, 110);
            Gfx.setAlpha(oldBig);
        }

        float jolt = t >= CLASS_LOCK ? 0.14f * sq(1 - Anim.window(t, CLASS_LOCK, CLASS_LOCK + 0.3f)) : 0;
        float er = r * Anim.lerp(0.55f, 1f, Anim.outBack(appear)) * (1 + jolt);

        // the ring that draws itself around the emblem
        if (big > 0) {
            float oldBig = Gfx.fade(big);
            float ring = er * 1.5f;
            Shapes.ring(x, y, ring, 1.6f).fill(0x1FFFFFFF).draw(g);
            float filled = decided ? 1f : build;
            if (filled > 0.004f) {
                Shapes.ring(x, y, ring, 1.6f).fillAround(SystemTheme.ACCENT_BRIGHT, color).progress(filled).glow(3.5f, Gfx.alpha(color, 0.55f)).draw(g);
            }
            Shapes.ring(x, y, ring + 5f, 0.75f).fill(Gfx.alpha(color, 0.5f)).dashes(36, 0.4f).rotation(-Anim.loop(22f)).draw(g);
            Gfx.setAlpha(oldBig);
        }

        // the hexagon plate and the class icon on it
        float energy = t >= CLASS_LOCK ? 0.45f + 0.55f * (1 - Anim.window(t, CLASS_LOCK, CLASS_LOCK + 1.2f)) : 0.15f + 0.25f * build;
        Shapes.icon(Icons.HEXAGON, x, y, er * HEX).fillV(0xF0142238, 0xF0070D1A).border(Math.max(0.75f, er * 0.08f), color, Gfx.fade(color, 0.5f))
                .glow(er * (0.4f + 0.5f * energy), Gfx.alpha(color, 0.3f + 0.4f * energy)).draw(g);
        if (shown != null) {
            float scale = 1f;
            float alpha = 1f;
            if (!decided) {
                scale = 1 + 0.22f * (1 - Anim.window(t, CLASS_STEP_AT[step], CLASS_STEP_AT[step] + 0.09f));
                alpha = 0.8f;
            } else if (t < CLASS_LOCK) {
                float u = Anim.window(t, CLASS_LOCK - STAMP, CLASS_LOCK);
                scale = Anim.lerp(2.6f, 1f, Anim.inCubic(u));
                alpha = Anim.lerp(0.2f, 1f, u);
            }
            float oldIcon = Gfx.fade(alpha);
            Shapes.icon(Icons.of(shown), x, y, er * 1.12f * scale).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.8f), Gfx.mix(color, 0xFFFFFFFF, 0.3f))
                    .glow(er * 0.25f, Gfx.alpha(color, 0.7f)).draw(g);
            Gfx.setAlpha(oldIcon);
        }

        if (big > 0) {
            float oldBig = Gfx.fade(big);
            float swap = Anim.window(t, CLASS_LOCK, CLASS_LOCK + 0.25f);
            float labelY = y - CLASS_RADIUS * 1.5f - 20;
            label(g, tr("evaluation.beyondsolo.class.pending"), labelY, SystemTheme.ACCENT, 1 - swap);
            label(g, tr("evaluation.beyondsolo.class.assigned"), labelY, Gfx.mix(classColor, 0xFFFFFFFF, 0.3f), swap);
            if (t >= CLASS_LOCK) {
                float in = Anim.outCubic(Anim.window(t, CLASS_LOCK + 0.08f, CLASS_LOCK + 0.45f));
                title(g, classTitle, y + CLASS_RADIUS * 1.5f + 14, 24, classColor, in);
                typed(g, tr("evaluation.beyondsolo.class." + playerClass.name().toLowerCase(Locale.ROOT)), y + CLASS_RADIUS * 1.5f + 43, t - (CLASS_LOCK + 0.45f));
            }
            Gfx.setAlpha(oldBig);
        }
        Gfx.setAlpha(old);
    }

    private static float chipAt(int index) {
        return STATS + 0.3f + index * 0.13f;
    }

    private static void drawSummary(GuiGraphics g, float t, String rankTitle, String classTitle, float rankW, float headerW) {
        int rankColor = SystemTheme.rank(rank);
        int classColor = SystemTheme.playerClass(playerClass);

        // headline between the two emblems: "A-RANK ASSASSIN"
        float in = Anim.outCubic(Anim.window(t, STATS - 0.1f, STATS + 0.3f));
        float old = Gfx.fade(in);
        float hy = HEADER_Y - HEADER_SIZE * SysFont.CAP / 2f + (1 - in) * 6;
        SysFont.draw(g, rankTitle, -headerW / 2f, hy, HEADER_SIZE, Gfx.mix(rankColor, 0xFFFFFFFF, 0.35f), true, 0f);
        SysFont.draw(g, classTitle, -headerW / 2f + rankW + 7, hy, HEADER_SIZE, Gfx.mix(classColor, 0xFFFFFFFF, 0.35f), true, 0f);
        Ui.divider(g, -150, HEADER_Y + 21, 300, Gfx.alpha(SystemTheme.ACCENT, 0.5f));
        SysFont.spaced(g, tr("evaluation.beyondsolo.summary.attributes"), 0, HEADER_Y + 29, 6.5f, SystemTheme.TEXT_DIM, true, 2.2f, 0.5f);
        Gfx.setAlpha(old);

        // the five stats pop in one after another and count up from the base value
        int best = 0;
        for (int value : STATS_SHOWN) best = Math.max(best, value - Stat.BASE);
        float chipW = 58;
        float chipH = 50;
        float gap = 7;
        float left = -(Stat.values().length * chipW + (Stat.values().length - 1) * gap) / 2f;
        float chipY = HEADER_Y + 41;
        for (Stat stat : Stat.values()) {
            int i = stat.ordinal();
            float pop = Anim.window(t, chipAt(i), chipAt(i) + 0.32f);
            if (pop <= 0) continue;
            int bonus = STATS_SHOWN[i] - Stat.BASE;
            float count = Anim.outCubic(Anim.window(t, chipAt(i) + 0.15f, chipAt(i) + 0.95f));
            int value = Stat.BASE + Math.round(bonus * count);
            boolean main = bonus > 0 && bonus == best;
            int tint = main ? classColor : SystemTheme.ACCENT;
            float scale = Anim.lerp(0.7f, 1f, Anim.outBack(pop));

            g.pose().pushPose();
            g.pose().translate(left + i * (chipW + gap) + chipW / 2f, chipY + chipH / 2f, 0);
            g.pose().scale(scale, scale, 1f);
            float oldChip = Gfx.fade(Anim.outCubic(pop));
            float landed = 1 - Anim.window(t, chipAt(i), chipAt(i) + 0.6f);
            Shapes.box(-chipW / 2f, -chipH / 2f, chipW, chipH).radius(9)
                    .fillV(Gfx.mix(SystemTheme.GLASS_TOP, Gfx.alpha(tint, 0.95f), main ? 0.22f : 0.08f), SystemTheme.GLASS_BOTTOM)
                    .border(0.9f, Gfx.alpha(tint, main ? 0.95f : 0.6f), Gfx.alpha(tint, main ? 0.4f : 0.2f))
                    .glow(6 + 6 * landed, Gfx.alpha(tint, (main ? 0.3f : 0.12f) + 0.3f * landed)).draw(g);
            Ui.iconBadge(g, Icons.of(stat), 0, -chipH / 2f + 13, 7.5f, tint);
            SysFont.draw(g, String.valueOf(value), 0, 1f, 15, SystemTheme.TEXT, true, 0.5f);
            SysFont.draw(g, stat.displayName().getString(), -chipW / 2f + 8, chipH / 2f - 10.5f, 6.5f, SystemTheme.TEXT_SOFT, true, 0f);
            if (bonus > 0) {
                float oldBonus = Gfx.fade(count);
                SysFont.draw(g, "+" + bonus, chipW / 2f - 8, chipH / 2f - 10.5f, 6.5f, main ? Gfx.mix(classColor, 0xFFFFFFFF, 0.3f) : SystemTheme.GOOD, true, 1f);
                Gfx.setAlpha(oldBonus);
            }
            Gfx.setAlpha(oldChip);
            g.pose().popPose();
        }

        // "the System is now active", and how to open it
        float ready = Anim.window(t, READY, READY + 0.5f);
        if (ready > 0) {
            float eased = Anim.outCubic(ready);
            float oldReady = Gfx.fade(eased);
            float ry = chipY + chipH + 16;
            String text = tr("evaluation.beyondsolo.summary.active");
            float tw = SysFont.spacedWidth(text, 10.5f, true, 3.2f);
            Shapes.pill(-tw / 2f * eased, ry + 13f, tw * eased, 1.2f).fill(Gfx.alpha(SystemTheme.ACCENT, 0.35f)).glow(14, Gfx.alpha(SystemTheme.ACCENT, 0.22f))
                    .additive().draw(g);
            SysFont.spaced(g, text, 0, ry, 10.5f, Gfx.mix(SystemTheme.ACCENT_BRIGHT, 0xFFFFFFFF, 0.35f), true, 3.2f + (1 - eased) * 2.5f, 0.5f);
            Gfx.setAlpha(oldReady);

            float hint = Anim.window(t, READY + 0.45f, READY + 0.85f);
            String key = ClientEvents.OPEN_SYSTEM.getTranslatedKeyMessage().getString();
            if (hint > 0 && key.length() <= 6) {
                float oldHint = Gfx.fade(hint * (0.7f + 0.3f * Anim.pulse(1.4f)));
                String open = tr("evaluation.beyondsolo.summary.open");
                float kw = Ui.keycapWidth(11, key);
                float total = kw + 5 + SysFont.width(open, 7.5f, false);
                float hx = -total / 2f;
                Ui.keycap(g, hx, ry + 19, 11, key, SystemTheme.ACCENT);
                SysFont.draw(g, open, hx + kw + 5, SysFont.centerY(ry + 19, 11, 7.5f), 7.5f, SystemTheme.TEXT_SOFT, false, 0f);
                Gfx.setAlpha(oldHint);
            }
        }
    }

    private static void drawSkipHint(GuiGraphics g, int w, int h, float t) {
        float a = Anim.window(t, 1.0f, 1.5f) * (1 - Anim.window(t, FINISH - 1.8f, FINISH - 1.3f)) * 0.6f;
        String key = ClientEvents.DISMISS.getTranslatedKeyMessage().getString();
        if (a <= 0.02f || key.length() > 6) return;
        String label = tr("evaluation.beyondsolo.skip");
        float old = Gfx.fade(a);
        float kw = Ui.keycapWidth(10, key);
        float x = w - 12 - SysFont.width(label, 6.5f, true) - 4 - kw;
        float y = h - 22;
        Ui.keycap(g, x, y, 10, key, SystemTheme.TEXT_SOFT);
        SysFont.draw(g, label, x + kw + 4, SysFont.centerY(y, 10, 6.5f), 6.5f, SystemTheme.TEXT_SOFT, true, 0f);
        Gfx.setAlpha(old);
    }

    // ---------------------------------------------------------------- pieces

    /** A small spaced caption with a fading line on each side, centred on x = 0. */
    private static void label(GuiGraphics g, String text, float y, int color, float alpha) {
        if (alpha <= 0.02f) return;
        float old = Gfx.fade(alpha);
        float size = 7.5f;
        float tracking = 3.2f;
        float tw = SysFont.spacedWidth(text, size, true, tracking);
        SysFont.spaced(g, text, 0, y, size, color, true, tracking, 0.5f);
        float ly = y + size * SysFont.CAP / 2f - 0.4f;
        Shapes.box(-tw / 2f - 48, ly, 36, 0.8f).fillH(Gfx.fade(color, 0f), Gfx.fade(color, 0.8f)).draw(g);
        Shapes.box(tw / 2f + 12, ly, 36, 0.8f).fillH(Gfx.fade(color, 0.8f), Gfx.fade(color, 0f)).draw(g);
        Shapes.circle(-tw / 2f - 9, ly + 0.4f, 1.1f).fill(color).draw(g);
        Shapes.circle(tw / 2f + 9, ly + 0.4f, 1.1f).fill(color).draw(g);
        Gfx.setAlpha(old);
    }

    /** A big headline that rises into place, with a soft light under it. */
    private static void title(GuiGraphics g, String text, float y, float size, int color, float in) {
        if (in <= 0.02f) return;
        float old = Gfx.fade(in);
        float ty = y + (1 - in) * 9;
        float tw = SysFont.width(text, size, true);
        Shapes.pill(-tw / 2f, ty + size * SysFont.CAP / 2f, tw, 1.5f).fill(Gfx.alpha(color, 0.3f)).glow(size * 0.7f, Gfx.alpha(color, 0.2f)).additive().draw(g);
        SysFont.draw(g, text, 0, ty, size, Gfx.mix(color, 0xFFFFFFFF, 0.4f), true, 0.5f);
        Gfx.setAlpha(old);
    }

    /** One line that types itself out, centred on x = 0. */
    private static void typed(GuiGraphics g, String text, float y, float elapsed) {
        if (elapsed <= 0) return;
        SysFont.Line line = SysFont.line(text, 9f, false);
        int total = SysFont.length(line);
        int chars = Math.min(total, (int) (elapsed * 55f));
        SysFont.draw(g, chars >= total ? line : SysFont.truncate(line, chars), 0, y, SystemTheme.TEXT_SOFT, 0.5f);
    }

    /** Rings of light thrown outwards from an impact. */
    private static void shockwaves(GuiGraphics g, float x, float y, float t, float at, float from, int color, int count, float reach) {
        for (int i = 0; i < count; i++) {
            float start = at + i * 0.09f;
            float u = Anim.window(t, start, start + 0.75f);
            if (u <= 0 || u >= 1) continue;
            float e = Anim.outCubic(u);
            Shapes.ring(x, y, from + reach * e, Anim.lerp(3.2f, 0.6f, e)).fill(Gfx.alpha(Gfx.mix(0xFFFFFFFF, color, e), sq(1 - u) * 0.9f))
                    .glow(6, Gfx.alpha(color, 0.5f * (1 - u))).additive().draw(g);
        }
    }

    /** Streaks of light racing in towards the emblem; more and brighter as the decision gets closer. */
    private static void streaks(GuiGraphics g, float x, float y, float t, float build, int color) {
        int count = 14;
        float period = 0.62f;
        for (int i = 0; i < count; i++) {
            float phase = t / period + i / (float) count;
            int cycle = (int) Math.floor(phase);
            float u = phase - cycle;
            float alpha = (float) Math.sin(u * Math.PI) * (0.2f + 0.65f * build);
            if (alpha <= 0.02f) continue;
            // each streak comes from a new direction every time around
            float angle = hash(i * 31 + cycle * 17) * Mth.TWO_PI;
            float distance = Anim.lerp(178f, 48f, u * u);
            float length = 10 + 28 * u;
            g.pose().pushPose();
            g.pose().translate(x + Mth.cos(angle) * distance, y + Mth.sin(angle) * distance, 0);
            g.pose().mulPose(Axis.ZP.rotation(angle));
            Shapes.pill(0, -0.8f, length, 1.6f).fillH(Gfx.alpha(Gfx.mix(color, 0xFFFFFFFF, 0.7f), alpha), Gfx.alpha(color, 0f)).additive().draw(g);
            g.pose().popPose();
        }
    }

    /** How far the dial has turned (in turns): fast at first, slowing to a crawl by the time the rank locks. */
    private static float dialTurn(float t) {
        float fast = 1.5f;
        float slow = 0.04f;
        float u = Math.min(t, LOCK);
        float turned = slow * u + (fast - slow) * (u - u * u / (2 * LOCK));
        return t > LOCK ? turned + slow * (t - LOCK) : turned;
    }

    /** Index of the roulette step showing at {@code t}, or -1 before the first. */
    private static int step(float[] at, float t) {
        int step = -1;
        for (int k = 0; k < at.length; k++) {
            if (t >= at[k]) step = k;
        }
        return step;
    }

    private static float shake(float t, float at, float amount) {
        return t < at ? 0 : amount * sq(1 - Anim.window(t, at, at + 0.45f));
    }

    private static float flash(float t, float at, float amount) {
        return t < at ? 0 : amount * (1 - Anim.outCubic(Anim.window(t, at, at + 0.5f)));
    }

    private static float sq(float v) {
        return v * v;
    }

    private static float hash(int n) {
        n = (n << 13) ^ n;
        n = n * (n * n * 15731 + 789221) + 1376312589;
        return (n & 0x7fffffff) / (float) 0x7fffffff;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private EvaluationOverlay() {}
}
