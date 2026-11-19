package com.beyondsolo.client.awakening;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.network.AwakeningPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The awakening trial on screen. Before the System binds, nothing here looks like the System: the moments are bare
 * white words over the world, in the pale gold of the sealed gate.
 * <ul>
 *   <li><b>Called</b>: the sealed gate tears open; the camera turns to it, the edges of the screen pale.</li>
 *   <li><b>Tether</b>: walking too far: a flash of gold, broken rings snapping in, the pull.</li>
 *   <li><b>The trial's threshold</b> and <b>getting up again</b> after a fall on the way down.</li>
 *   <li><b>Slain</b>: the Arbiter killed: white, gold cracks from the middle of the screen, IMPOSSIBLE.</li>
 *   <li><b>The awakening</b>: struck down, the world tips over and closes into black on a slowing heartbeat. A few
 *   words. Then a line of blue light cuts the dark and opens into the first System window there has ever been for
 *   this player; it finds them, binds (a bar fills), and collapses into the System's sigil, which bursts into rings
 *   and rays under THE SYSTEM HAS AWAKENED. The light fades and they are outside, and the HUD is there.</li>
 *   <li><b>The awakening in the light</b> (sworn to the light in the hidden chapel, {@code light.Covenant}): no fall.
 *   Gold light closes in instead of the dark, a bell instead of a heartbeat; the words are the light's, and the window,
 *   its bar and the sigil burn gold. The System is the same System: only the light it binds in is not.</li>
 * </ul>
 */
public final class AwakeningOverlay {
    private static final int PALE = 0xFFF2D58A;
    /** The light's gold, for an awakening in the light. */
    private static final int DAWN = 0xFFFFD27A;
    private static final int DAWN_BRIGHT = 0xFFFFF0C4;
    private static final float AWAKEN_LENGTH = 14.6f;

    private static int kind = -1;
    private static double startedAt;
    private static boolean slain;
    /** Sworn to the light: the System binds in the light. */
    private static boolean light;
    private static Vec3 focus;
    private static int cue;

    // ------------------------------------------------------------------ events

    public static void handle(AwakeningPayload p) {
        switch (p.kind()) {
            case AwakeningPayload.MARK -> AwakeningWorldFx.mark(p.x(), p.y(), p.z(), p.value());
            case AwakeningPayload.WAVE -> AwakeningWorldFx.wave(p.x(), p.y(), p.z(), p.value());
            case AwakeningPayload.TETHER -> {
                // don't cut the gate's own opening short
                if (kind == AwakeningPayload.CALLED || kind == AwakeningPayload.AWAKEN) return;
                start(p);
                GateOverlay.addTrauma(0.45f);
            }
            case AwakeningPayload.AWAKEN -> {
                slain = p.value() > 0.5f && p.value() < 1.5f;
                light = p.value() > 1.5f;
                start(p);
                NotificationManager.setHeld(true);
            }
            default -> {
                if (kind == AwakeningPayload.AWAKEN) return;
                start(p);
            }
        }
    }

    private static void start(AwakeningPayload p) {
        kind = p.kind();
        startedAt = Anim.now();
        focus = new Vec3(p.x(), p.y(), p.z());
        cue = 0;
        switch (kind) {
            case AwakeningPayload.CALLED -> {
                GateOverlay.addTrauma(0.7f);
                sound(SoundEvents.WARDEN_SONIC_CHARGE, 0.5f, 0.7f);
            }
            case AwakeningPayload.SLAIN -> {
                GateOverlay.addTrauma(1f);
                sound(SoundEvents.TRIDENT_THUNDER.value(), 0.6f, 0.8f);
            }
            case AwakeningPayload.REVIVE -> sound(SoundEvents.WARDEN_HEARTBEAT, 0.8f, 1f);
            default -> {}
        }
    }

    public static boolean active() {
        return kind >= 0;
    }

    /** True while the awakening itself plays (the screen is the System's; nothing else should draw over it). */
    public static boolean awakening() {
        return kind == AwakeningPayload.AWAKEN;
    }

    public static void reset() {
        if (kind == AwakeningPayload.AWAKEN) NotificationManager.setHeld(false);
        kind = -1;
        AwakeningWorldFx.clear();
    }

    private static float length() {
        return switch (kind) {
            case AwakeningPayload.CALLED -> 6.5f;
            case AwakeningPayload.TETHER -> 1.6f;
            case AwakeningPayload.TRIAL_ARRIVE -> 7.5f;
            case AwakeningPayload.REVIVE -> 3.6f;
            case AwakeningPayload.SLAIN -> 5.2f;
            case AwakeningPayload.AWAKEN -> AWAKEN_LENGTH;
            default -> 0f;
        };
    }

    public static void frame() {
        if (kind < 0) return;
        float t = Anim.since(startedAt);
        if (t >= length()) {
            if (kind == AwakeningPayload.AWAKEN) NotificationManager.setHeld(false);
            kind = -1;
            return;
        }
        if (kind == AwakeningPayload.CALLED && t < 1.6f) lookAt(focus, Anim.dt() * 4.5f);
        if (kind == AwakeningPayload.AWAKEN) cues(t);
    }

    /** Sounds of the awakening, each once as its moment passes. */
    private static void cues(float t) {
        float[] at = {0.0f, 0.9f, 1.9f, 5.6f, 6.3f, 8.3f, 9.9f, 10.25f, 10.9f, 12.5f};
        while (cue < at.length && t >= at[cue]) {
            switch (cue) {
                case 0, 1, 2 -> {
                    if (light) sound(SoundEvents.BELL_RESONATE, 1.1f - 0.15f * cue, 0.7f);
                    else sound(SoundEvents.WARDEN_HEARTBEAT, 0.8f - 0.12f * cue, 1f);
                }
                case 3 -> sound(SoundEvents.BEACON_POWER_SELECT, 1.8f, 0.5f);
                case 4 -> sound(SoundEvents.BEACON_ACTIVATE, 1.5f, 0.6f);
                case 5 -> sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 1f);
                case 6 -> sound(SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 1.3f, 0.8f);
                case 7 -> {
                    sound(SoundEvents.TOTEM_USE, 1.1f, 0.55f);
                    sound(SoundEvents.TRIDENT_THUNDER.value(), 1.4f, 0.35f);
                }
                case 8 -> sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 0.7f);
                default -> sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1f);
            }
            cue++;
        }
    }

    private static void sound(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    private static void lookAt(Vec3 target, float k) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || target == null) return;
        Vec3 to = target.subtract(mc.player.getEyePosition());
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        if (flat < 0.5) return;
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(to.y, flat) * Mth.RAD_TO_DEG);
        k = Math.min(1f, k);
        mc.player.setYRot(mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()) * k);
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + (pitch - mc.player.getXRot()) * k, -90f, 90f));
        mc.player.yRotO = mc.player.getYRot();
        mc.player.xRotO = mc.player.getXRot();
    }

    /** Extra camera roll and pitch (degrees) while the player goes down. */
    public static float[] cameraFall() {
        if (kind != AwakeningPayload.AWAKEN || light) return null;
        float t = Anim.since(startedAt);
        if (t > 4.2f) return null;
        float k = Anim.inOutCubic(Anim.window(t, 0.15f, 2.3f));
        return new float[]{38f * k, 24f * k};
    }

    // ------------------------------------------------------------------ drawing

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (kind < 0 || Minecraft.getInstance().player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float t = Anim.since(startedAt);
        switch (kind) {
            case AwakeningPayload.CALLED -> called(g, w, h, t);
            case AwakeningPayload.TETHER -> tether(g, w, h, t);
            case AwakeningPayload.TRIAL_ARRIVE -> threshold(g, w, h, t);
            case AwakeningPayload.REVIVE -> revive(g, w, h, t);
            case AwakeningPayload.SLAIN -> slainMoment(g, w, h, t);
            case AwakeningPayload.AWAKEN -> awaken(g, w, h, t);
            default -> {}
        }
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("awakening.beyondsolo." + key, args).getString();
    }

    /** Two bare lines over the world: a spaced headline and a quiet line under it, on a soft dark band. */
    private static void words(GuiGraphics g, int w, float y, String head, String sub, float in, float out, int color) {
        float a = in * (1 - out);
        if (a <= 0.01f) return;
        float cx = w / 2f;
        float old = Gfx.fade(a);
        float bw = Math.min(w * 0.86f, Math.max(SysFont.spacedWidth(head, 11f, true, 3f), SysFont.width(sub, 7.5f, false)) + 70);
        Shapes.pill(cx - bw / 2f, y - 10, bw, 46).fill(Gfx.alpha(0xFF05060A, 0.5f)).glow(26, Gfx.alpha(0xFF05060A, 0.4f)).draw(g);
        SysFont.spaced(g, head, cx, y - 2 * (1 - in), 11f, 0xFFFFFFFF, true, 3f + 2f * (1 - in), 0.5f);
        float lw = Math.min(bw - 40, 190) * Anim.outQuint(in);
        Shapes.pill(cx - lw / 2f, y + 14.5f, lw, 0.9f).fill(Gfx.alpha(color, 0.9f)).glow(5, Gfx.alpha(color, 0.6f)).draw(g);
        if (!sub.isEmpty()) SysFont.draw(g, sub, cx, y + 20, 7.5f, Gfx.mix(color, 0xFFFFFFFF, 0.55f), false, 0.5f);
        Gfx.setAlpha(old);
    }

    private static void called(GuiGraphics g, int w, int h, float t) {
        float live = 1 - Anim.window(t, 5.6f, 6.5f);
        float flash = 1 - Anim.outCubic(Anim.window(t, 0f, 0.9f));
        Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFFFFF6DC, 0.5f * flash)).draw(g);
        float pulse = 0.5f + 0.5f * Mth.sin(t * 3.2f);
        Shapes.vignette(0, 0, w, h, 0.35f, 1.25f).fill(Gfx.alpha(PALE, (0.2f + 0.12f * pulse) * live)).draw(g);
        Shapes.vignette(0, 0, w, h, 0.6f, 1.5f).fill(Gfx.alpha(0xFF000000, 0.45f * live)).draw(g);
        words(g, w, h * 0.68f, tr("called.title"), tr("called.body"), Anim.outCubic(Anim.window(t, 1.5f, 2.3f)), Anim.window(t, 5.4f, 6.2f), PALE);
    }

    private static void tether(GuiGraphics g, int w, int h, float t) {
        float k = 1 - Anim.outCubic(Anim.window(t, 0f, 1.1f));
        float cx = w / 2f, cy = h / 2f;
        float diag = Mth.sqrt(w * w + h * h) / 2f;
        Shapes.vignette(0, 0, w, h, 0.25f, 1.1f).fill(Gfx.alpha(PALE, 0.45f * k)).draw(g);
        for (int i = 0; i < 3; i++) {
            float u = Anim.window(t, i * 0.08f, 0.55f + i * 0.08f);
            if (u <= 0 || u >= 1) continue;
            // broken rings snapping in towards the middle, like links pulled tight
            Shapes.ring(cx, cy, diag * (1.05f - 0.75f * Anim.outCubic(u)), 3.5f * (1 - u) + 1f).fill(Gfx.alpha(PALE, 0.8f * (1 - u)))
                    .dashes(14 + i * 4, 0.6f).rotation(0.05f * i + 0.1f * u).draw(g);
        }
        words(g, w, h * 0.7f, tr("tether.title"), "", Anim.outCubic(Anim.window(t, 0.05f, 0.35f)), Anim.window(t, 1.1f, 1.6f), PALE);
    }

    private static void threshold(GuiGraphics g, int w, int h, float t) {
        float live = 1 - Anim.window(t, 6.6f, 7.5f);
        Shapes.vignette(0, 0, w, h, 0.4f, 1.3f).fill(Gfx.alpha(PALE, 0.14f * live * Anim.window(t, 1f, 2f))).draw(g);
        words(g, w, h * 0.3f, tr("threshold.title"), tr("threshold.body"), Anim.outCubic(Anim.window(t, 1.6f, 2.5f)), Anim.window(t, 6.2f, 7.2f), PALE);
    }

    private static void revive(GuiGraphics g, int w, int h, float t) {
        float black = 1 - Anim.inOutCubic(Anim.window(t, 0.5f, 2.0f));
        Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFF000000, black)).draw(g);
        Shapes.vignette(0, 0, w, h, 0.2f, 1.1f).fill(Gfx.alpha(0xFF7A0A14, 0.5f * (1 - Anim.window(t, 0.8f, 3.2f)))).draw(g);
        words(g, w, h * 0.46f, tr("revive.title"), tr("revive.body"), Anim.outCubic(Anim.window(t, 0.15f, 0.6f)), Anim.window(t, 2.6f, 3.4f), 0xFFFF7080);
    }

    private static void slainMoment(GuiGraphics g, int w, int h, float t) {
        float cx = w / 2f, cy = h * 0.4f;
        float flash = 1 - Anim.outCubic(Anim.window(t, 0f, 1.2f));
        float live = 1 - Anim.window(t, 4.5f, 5.2f);
        Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFFFFFFFF, 0.85f * flash)).draw(g);
        Shapes.vignette(0, 0, w, h, 0.3f, 1.3f).fill(Gfx.alpha(SystemTheme.GOLD, 0.3f * live)).draw(g);
        float rays = Anim.outCubic(Anim.window(t, 0.3f, 1.3f));
        Shapes.rays(cx, cy, Math.max(w, h) * 0.9f * rays, 26).fill(Gfx.alpha(SystemTheme.GOLD, 0.22f * live)).rotation(t * 0.015f).additive().draw(g);
        for (int i = 0; i < 3; i++) {
            float u = Anim.window(t, 0.9f + i * 0.14f, 2.1f + i * 0.14f);
            if (u > 0 && u < 1) Shapes.ring(cx, cy, 24 + 220 * Anim.outCubic(u), 3f * (1 - u)).fill(Gfx.alpha(SystemTheme.GOLD, 1 - u)).draw(g);
        }
        float stamp = Anim.window(t, 0.7f, 1.0f);
        if (stamp > 0) {
            float s = Mth.lerp(Anim.outCubic(stamp), 3.2f, 1f);
            float old = Gfx.fade(Math.min(1f, stamp * 1.6f) * live);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().scale(s, s, 1);
            String word = tr("slain.title");
            float ww = SysFont.spacedWidth(word, 24f, true, 4f) + 34;
            Shapes.box(-ww / 2f, -18, ww, 36).radius(5).fill(Gfx.alpha(0xFF1A0E02, 0.6f)).border(1.5f, SystemTheme.GOLD).glow(18, 0x90FFD166).draw(g);
            SysFont.spaced(g, word, 0, -9, 24f, 0xFFFFE6A8, true, 4f, 0.5f);
            g.pose().popPose();
            Gfx.setAlpha(old);
            if (stamp >= 1f && t < 1.1f) GateOverlay.addTrauma(0.06f);
        }
        float sub = Anim.outCubic(Anim.window(t, 1.7f, 2.4f)) * live;
        if (sub > 0.01f) {
            float old = Gfx.fade(sub);
            SysFont.draw(g, tr("slain.body"), cx, cy + 30, 8.5f, 0xFFFFF2D0, false, 0.5f);
            SysFont.draw(g, tr("slain.hint"), cx, cy + 44, 7f, Gfx.alpha(SystemTheme.GOLD, Anim.window(t, 2.6f, 3.2f)), false, 0.5f);
            Gfx.setAlpha(old);
        }
    }

    // ------------------------------------------------------------------ the awakening

    private static void awaken(GuiGraphics g, int w, int h, float t) {
        float cx = w / 2f, cy = h / 2f;
        int blue = light ? DAWN : SystemTheme.ACCENT;
        int bright = light ? DAWN_BRIGHT : SystemTheme.ACCENT_BRIGHT;

        // in the light: no fall. Gold closes in from the edges, slowly, like a hand laid over the eyes
        if (light && t < 3.2f) {
            float close = Anim.inOutCubic(Anim.window(t, 0.2f, 2.6f));
            float shimmer = 0.5f + 0.5f * Mth.sin(t * 2.4f);
            Shapes.vignette(0, 0, w, h, 0.75f - 0.75f * close, 1.25f - 0.9f * close).fill(Gfx.alpha(DAWN, 0.45f + 0.25f * shimmer)).additive().draw(g);
            Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFFFFF4D8, 0.35f * close)).draw(g);
            Shapes.rays(cx, cy, Math.max(w, h) * 0.8f, 20).fill(Gfx.alpha(DAWN, 0.12f * (1 - close * 0.5f))).rotation(t * 0.02f).additive().draw(g);
        }
        // going down: a heartbeat in red, closing in
        if (!light && t < 3.2f) {
            float close = Anim.inOutCubic(Anim.window(t, 0.2f, 2.6f));
            float beat = (float) Math.pow(Math.max(0, Mth.sin(t * (5.2f - 1.1f * t))), 6);
            Shapes.vignette(0, 0, w, h, 0.75f - 0.75f * close, 1.25f - 0.9f * close).fill(Gfx.alpha(0xFF6E0812, 0.55f + 0.35f * beat)).draw(g);
            Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFF300408, 0.25f * beat + 0.2f * close)).draw(g);
        }
        // the dark
        float dark = Anim.inCubic(Anim.window(t, 1.5f, 2.7f)) * (1 - Anim.window(t, 12.5f, 12.9f));
        if (dark > 0.001f) Shapes.box(0, 0, w, h).fill(Gfx.alpha(light ? 0xFF0B0803 : 0xFF000000, dark)).draw(g);

        // a few words in the dark, nobody's voice yet
        line(g, cx, cy - 4, tr(light ? "dark.light1" : slain ? "dark.slain1" : "dark.1"), t, 3.0f, 4.3f, light ? 0xFFE8D8B0 : 0xFFB8BCC4);
        line(g, cx, cy - 4, tr(light ? "dark.light2" : slain ? "dark.slain2" : "dark.2"), t, 4.3f, 5.6f, 0xFFFFFFFF);

        // the line of light, and the first window
        float cut = Anim.outQuint(Anim.window(t, 5.6f, 6.2f));
        float open = Anim.outBack(Anim.window(t, 6.3f, 6.9f));
        float collapse = Anim.inCubic(Anim.window(t, 9.95f, 10.25f));
        float pw = Math.min(w * 0.8f, 300), ph = 104;
        if (cut > 0 && collapse < 1f) {
            float lineW = Math.max(w, 1) * cut * (1 - Anim.window(t, 6.3f, 6.8f)) + pw * Anim.window(t, 6.3f, 6.8f);
            float glitch = t < 6.3f ? (Mth.sin(t * 90f) > 0.6f ? 3f : 0f) : 0f;
            if (open <= 0.02f) {
                Shapes.pill(cx - lineW / 2f, cy - 0.75f + glitch, lineW, 1.5f).fill(Gfx.mix(blue, 0xFFFFFFFF, 0.6f)).glow(10, Gfx.alpha(blue, 0.9f)).draw(g);
                Shapes.pill(cx - lineW * 0.2f, cy - 1.25f, lineW * 0.4f, 2.5f).fill(0xFFFFFFFF).glow(16, Gfx.alpha(blue, 0.7f)).draw(g);
            } else {
                float sy = Math.max(0.02f, open) * (1 - collapse);
                float sx = 1 - collapse * 0.97f;
                g.pose().pushPose();
                g.pose().translate(cx, cy, 0);
                g.pose().scale(sx, sy, 1);
                Shapes.rays(0, 0, 190, 22).fill(Gfx.alpha(blue, 0.12f)).rotation(t * 0.02f).additive().draw(g);
                Ui.panel(g, -pw / 2f, -ph / 2f, pw, ph, 10, blue, 1.4f);
                g.pose().popPose();
                if (open > 0.97f && collapse <= 0) window(g, cx, cy, pw, ph, t, blue);
            }
        }

        // the sigil
        float born = Anim.outBackStrong(Anim.window(t, 10.2f, 10.8f));
        float gone = Anim.window(t, 12.5f, 13.9f);
        if (born > 0 && gone < 1) {
            float sy = cy - 16;
            if (light) {
                // the light's own halo round the sigil, and its rays standing out
                Shapes.rays(cx, sy, 120 * born, 16).fill(Gfx.alpha(DAWN_BRIGHT, 0.18f * (1 - gone))).rotation(-t * 0.02f).additive().draw(g);
            }
            float a = 1 - Anim.inCubic(gone);
            float old = Gfx.fade(a);
            float burst = Anim.window(t, 10.2f, 10.5f);
            Shapes.box(0, 0, w, h).fill(Gfx.alpha(Gfx.mix(blue, 0xFFFFFFFF, 0.7f), 0.75f * (1 - Anim.outCubic(Anim.window(t, 10.2f, 11.1f))) * (burst > 0 ? 1 : 0))).draw(g);
            Shapes.rays(cx, sy, Math.max(w, h) * 0.75f * Anim.outCubic(Anim.window(t, 10.2f, 11.2f)), 24).fill(Gfx.alpha(blue, 0.2f)).rotation(t * 0.03f).additive().draw(g);
            Shapes.rays(cx, sy, 150, 12).fill(Gfx.alpha(0xFFFFFFFF, 0.12f)).rotation(-t * 0.05f).additive().draw(g);
            for (int i = 0; i < 3; i++) {
                float u = Anim.window(t, 10.25f + i * 0.16f, 11.5f + i * 0.16f);
                if (u > 0 && u < 1) Shapes.ring(cx, sy, 30 + 260 * Anim.outCubic(u), 3.5f * (1 - u)).fill(Gfx.alpha(Gfx.mix(blue, 0xFFFFFFFF, 0.4f), 1 - u)).draw(g);
            }
            float r = 30 * born;
            Shapes.circle(cx, sy, r * 1.15f).fill(Gfx.alpha(0xFF061226, 0.85f)).glow(40, Gfx.alpha(blue, 0.55f)).draw(g);
            Shapes.ring(cx, sy, r * 1.5f, 1.2f).fill(Gfx.alpha(blue, 0.8f)).dashes(36, 0.5f).rotation(t * 0.06f).draw(g);
            Shapes.ring(cx, sy, r * 1.28f, 2f).fill(Gfx.mix(blue, 0xFFFFFFFF, 0.4f)).dashes(6, 0.82f).rotation(-t * 0.1f).draw(g);
            Shapes.ring(cx, sy, r * 1.78f, 0.8f).fill(Gfx.alpha(bright, 0.6f)).dashes(3, 0.7f).rotation(t * 0.16f).draw(g);
            Shapes.icon(Icons.HEXAGON, cx, sy, r * 1.9f).fill(Gfx.mix(blue, 0xFFFFFFFF, 0.25f)).glow(10, Gfx.alpha(blue, 0.9f)).draw(g);
            Shapes.icon(Icons.SPARKLE, cx, sy, r * 0.95f).fill(0xFFFFFFFF).glow(8, Gfx.alpha(blue, 0.9f)).draw(g);

            float head = Anim.outCubic(Anim.window(t, 10.9f, 11.5f));
            if (head > 0.01f) {
                float o2 = Gfx.fade(head);
                SysFont.spaced(g, tr("awakened.title"), cx, sy + 62, 13f, 0xFFFFFFFF, true, 3f + 5f * (1 - head), 0.5f);
                float lw = 210 * Anim.outQuint(head);
                Shapes.pill(cx - lw / 2f, sy + 80, lw, 1f).fill(Gfx.mix(blue, 0xFFFFFFFF, 0.5f)).glow(6, Gfx.alpha(blue, 0.8f)).draw(g);
                Gfx.setAlpha(o2);
            }
            float sub = Anim.outCubic(Anim.window(t, 11.5f, 12.1f));
            if (sub > 0.01f) {
                float o3 = Gfx.fade(sub);
                Minecraft mc = Minecraft.getInstance();
                String name = mc.player == null ? "" : mc.player.getGameProfile().getName();
                SysFont.draw(g, tr(light ? "awakened.body_light" : slain ? "awakened.body_slain" : "awakened.body", name), cx, sy + 87, 8f, bright, false, 0.5f);
                Gfx.setAlpha(o3);
            }
            Gfx.setAlpha(old);
        }
        // out into the world through light
        float white = Anim.window(t, 12.3f, 12.8f) * (1 - Anim.outCubic(Anim.window(t, 12.8f, 14.4f)));
        if (white > 0.001f) Shapes.box(0, 0, w, h).fill(Gfx.alpha(light ? 0xFFFFF6E2 : 0xFFEAF6FF, white)).draw(g);
    }

    /** What the first window says. */
    private static void window(GuiGraphics g, float cx, float cy, float pw, float ph, float t, int blue) {
        float top = cy - ph / 2f;
        float headIn = Anim.outCubic(Anim.window(t, 6.9f, 7.3f));
        float old = Gfx.fade(headIn);
        int bright = light ? DAWN_BRIGHT : SystemTheme.ACCENT_BRIGHT;
        SysFont.spaced(g, tr("window.header"), cx, top + 9, 7f, bright, true, 3.2f, 0.5f);
        Ui.divider(g, cx - pw / 2f + 18, top + 22, pw - 36, Gfx.alpha(blue, 0.6f));
        Gfx.setAlpha(old);
        typed(g, tr("window.found"), cx, top + 30, 8f, SystemTheme.TEXT, Anim.window(t, 7.2f, 8.1f));
        float condIn = Anim.window(t, 8.1f, 8.3f);
        if (condIn > 0) {
            float o2 = Gfx.fade(condIn);
            String label = tr(slain || light ? "window.hidden_condition" : "window.condition");
            String cond = tr(light ? "window.cond_light" : slain ? "window.cond_slain" : "window.cond_fallen");
            int c = light ? DAWN_BRIGHT : slain ? SystemTheme.GOLD : SystemTheme.ACCENT_BRIGHT;
            SysFont.draw(g, label, cx, top + 46, 6.5f, SystemTheme.TEXT_SOFT, false, 0.5f);
            Gfx.setAlpha(o2);
            typed(g, "[ " + cond + " ]", cx, top + 56, 9.5f, c, Anim.window(t, 8.3f, 8.9f));
        }
        float bar = Anim.window(t, 8.6f, 9.8f);
        if (bar > 0) {
            float bw = pw - 60, bx = cx - bw / 2f, by = top + ph - 22;
            float k = Anim.inOutCubic(bar);
            boolean done = t > 9.85f;
            SysFont.spaced(g, done ? tr("window.bound") : tr(light ? "window.binding_light" : "window.binding"), bx, by - 9, 6f,
                    done ? 0xFFFFFFFF : SystemTheme.TEXT_SOFT, true, 1.8f, 0f);
            SysFont.draw(g, Math.round(k * 100) + "%", bx + bw, by - 9, 6f, bright, true, 1f);
            if (light) Ui.bar(g, bx, by, bw, 5, k, 0xFFB07A1E, DAWN_BRIGHT);
            else Ui.bar(g, bx, by, bw, 5, k, SystemTheme.MP_A, SystemTheme.MP_B);
            if (done) Shapes.box(cx - pw / 2f, top, pw, ph).radius(10).fill(Gfx.alpha(0xFFFFFFFF, 0.5f * (1 - Anim.window(t, 9.85f, 10f)))).draw(g);
        }
    }

    /** One line in the dark that fades in, holds and fades out. */
    private static void line(GuiGraphics g, float cx, float y, String s, float t, float from, float to, int color) {
        float a = Anim.outCubic(Anim.window(t, from, from + 0.45f)) * (1 - Anim.window(t, to - 0.4f, to));
        if (a <= 0.01f) return;
        float old = Gfx.fade(a);
        SysFont.draw(g, s, cx, y, 9f, color, false, 0.5f);
        Gfx.setAlpha(old);
    }

    private static void typed(GuiGraphics g, String text, float cx, float y, float size, int color, float k) {
        if (k <= 0) return;
        SysFont.Line line = SysFont.line(text, size, true);
        int n = SysFont.length(line);
        int shown = Math.min(n, Math.round(n * k));
        float full = line.width();
        SysFont.draw(g, SysFont.truncate(line, shown), cx - full / 2f, y, color, 0f);
        if (k < 1f) {
            float caret = cx - full / 2f + SysFont.truncate(line, shown).width() + 1;
            Shapes.box(caret, y, 1.2f, size * 0.8f).fill(Gfx.alpha(light ? DAWN_BRIGHT : SystemTheme.ACCENT_BRIGHT, 0.9f))
                    .glow(4, light ? DAWN : SystemTheme.ACCENT).draw(g);
        }
    }

    private AwakeningOverlay() {}
}
