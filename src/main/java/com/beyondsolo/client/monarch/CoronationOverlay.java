package com.beyondsolo.client.monarch;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.hud.StatusHud;
import com.beyondsolo.client.job.CutsceneCamera;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.client.skill.SkillEmblem;
import com.beyondsolo.monarch.CoronationTimeline;
import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.network.MonarchActionPayload;
import com.beyondsolo.skill.MonarchPowers;
import com.beyondsolo.skill.Skill;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 * The coronation on screen, while the camera circles the hunter on the throne.
 *
 * <p>Light streams in from the edges of sight and gathers in the middle while the hall's braziers flare one by one;
 * everything draws in for a breath; then the crown takes shape: a flash, shock rings, the crown's emblem bursting in
 * and "MONARCH OF NIGHT" stamped under it while the screen shakes. Then the card: what the crown rules, and the four
 * powers on the crown keys. The hide key skips to the card, then to the end.</p>
 *
 * <p>Runs on game ticks so it stays in step with the light show in the hall ({@link CoronationTimeline}).</p>
 */
public final class CoronationOverlay {
    private static final float GATHER = CoronationTimeline.GATHER / 20f;
    private static final float LOCK = CoronationTimeline.LOCK / 20f;
    private static final float CARD = CoronationTimeline.CARD / 20f;
    private static final float FINISH = CoronationTimeline.FINISH / 20f;

    private static final class Spark {
        float x, y, vx, vy, age, life, size;
        int color;
        boolean inward;
    }

    private static boolean active;
    /** When the last coronation ended on screen (ms). */
    private static long endedAt = -100_000;
    private static int ticks;
    private static float offset, time, lastTime;
    private static boolean skippedToEnd, sawCamera;
    private static Dominion crown = Dominion.NIGHT;
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static float spawn;

    // ================================================================ state

    static void start(Dominion d) {
        crown = d;
        active = true;
        ticks = 0;
        offset = 0;
        time = lastTime = 0;
        skippedToEnd = false;
        sawCamera = false;
        spawn = 0;
        SPARKS.clear();
        flags();
    }

    static void reset() {
        if (active) endedAt = net.minecraft.Util.getMillis();
        active = false;
        SPARKS.clear();
        flags();
    }

    public static boolean active() {
        return active;
    }

    /**
     * The coronation is on screen or has just ended. Its card already showed the crown's powers, so their own Skill
     * Acquired moments are left out then (they still go in the log).
     */
    public static boolean justCrowned() {
        return active || net.minecraft.Util.getMillis() - endedAt < 10_000;
    }

    private static void flags() {
        StatusHud.setHidden(active);
        NotificationManager.setSuppressed(active);
    }

    /** The hide key: to the card, then to the end (the hall lets the hunter go soon after). Returns true if used. */
    public static boolean skip() {
        if (!active) return false;
        if (time < CARD - 0.2f) {
            offset += CARD - time;
            SPARKS.clear();
        } else if (!skippedToEnd && time < FINISH - 1f) {
            offset += FINISH - 0.75f - time;
            skippedToEnd = true;
            PacketDistributor.sendToServer(new MonarchActionPayload(MonarchActionPayload.SKIP, 0));
        }
        return true;
    }

    static void tick() {
        if (active && !Minecraft.getInstance().isPaused()) ticks++;
    }

    static void frame(DeltaTracker delta) {
        if (!active) return;
        lastTime = time;
        time = Math.max(lastTime, (ticks + delta.getGameTimeDeltaPartialTick(true)) / 20f + offset);
        // the hall let the hunter go early (skipped, or the trial was ended): end with it
        if (CutsceneCamera.active()) sawCamera = true;
        else if (sawCamera && time > 1f && time < FINISH - 0.75f) {
            offset += FINISH - 0.75f - time;
            time = FINISH - 0.75f;
        }
        moments();
        updateSparks(time - lastTime);
        if (time >= FINISH + 0.3f) reset();
    }

    private static boolean crossed(float at) {
        return lastTime < at && time >= at;
    }

    private static void moments() {
        int c = Gfx.opaque(crown.color());
        if (crossed(0.1f)) play(SoundEvents.BEACON_POWER_SELECT, 0.5f, 0.8f);
        for (int i = 0; i < 6; i++) if (crossed(0.6f + i * 0.45f)) play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f + i * 0.12f, 0.7f);
        if (crossed(GATHER)) play(SoundEvents.WARDEN_HEARTBEAT, 0.5f, 1f);
        if (crossed(LOCK)) {
            burst(Gfx.mix(c, 0xFFFFFFFF, 0.3f), 70, 240);
            burst(c, 50, 150);
            MonarchHud.shake(1.6f, 1.1f);
            play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
            play(SoundEvents.TOTEM_USE, 0.6f, 0.5f);
            play(SoundEvents.END_PORTAL_SPAWN, 1f, 0.6f);
        }
        if (crossed(CARD)) play(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f, 0.9f);
        for (int i = 0; i < 4; i++) if (crossed(CARD + 0.8f + i * 0.22f)) play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f + i * 0.15f, 0.35f);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ================================================================ drawing

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float s = Mth.clamp(Math.min(w / 460f, h / 250f), 0.6f, 2.2f);
        float t = time;
        float old = Gfx.fade(1 - Anim.window(t, FINISH - 0.7f, FINISH));
        int c = Gfx.opaque(crown.color());
        int light = Gfx.mix(c, 0xFFFFFFFF, 0.55f);

        // the edges darken; at the lock the crown's light floods in
        float dim = Anim.outCubic(Anim.window(t, 0, 0.8f));
        float inhale = Anim.window(t, GATHER, LOCK) * (t < LOCK ? 1 : 0);
        Shapes.vignette(0, 0, w, h, 0.25f - 0.12f * inhale, 1.2f).fill(Gfx.alpha(0xFF020108, (0.7f + 0.2f * inhale) * dim)).draw(g);
        if (t >= LOCK) {
            float mood = Anim.window(t, LOCK, LOCK + 0.5f);
            Shapes.vignette(0, 0, w, h, 0.45f, 1.35f).fill(Gfx.alpha(c, 0.24f * mood)).additive().draw(g);
        }

        spawnInward(w, h, s, c, t);
        g.pose().pushPose();
        g.pose().translate(w / 2f, h / 2f, 0);
        g.pose().scale(s, s, 1);
        float oldScale = SysFont.setContentScale(s);

        float toCard = Anim.inOutCubic(Anim.window(t, CARD - 0.5f, CARD + 0.2f));
        header(g, t, toCard, light);
        if (t < LOCK) gather(g, t, c, light);
        else crowned(g, t, c, light, toCard);
        if (t >= CARD - 0.3f) card(g, t, c, light);
        drawSparks(g, s, w, h);

        SysFont.setContentScale(oldScale);
        g.pose().popPose();

        float flash = flash(t, LOCK, 0.85f);
        if (flash > 0.004f) Shapes.box(0, 0, w, h).fill(Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.6f), Math.min(0.9f, flash))).additive().draw(g);
        skipHint(g, w, h, t);
        Gfx.setAlpha(old);
    }

    /** "THE THRONE ACCEPTS YOU", typed out; "THE THRONE IS NO LONGER EMPTY" once the crown is placed. */
    private static void header(GuiGraphics g, float t, float toCard, int light) {
        float out = toCard;
        if (t < LOCK) {
            float in = Anim.window(t, 0.2f, 0.7f) * (1 - Anim.window(t, GATHER, LOCK - 0.1f));
            if (in <= 0) return;
            float o = Gfx.fade(in);
            String head = Component.translatable("ui.beyondsolo.monarch.coronation.header").getString().toUpperCase(Locale.ROOT);
            int chars = (int) Math.min(head.length(), (t - 0.2f) * 28);
            if (chars > 0) SysFont.spaced(g, head.substring(0, chars), 0, -98, 12, 0xFFFFFFFF, true, 3.6f, 0.5f);
            Gfx.setAlpha(o);
        } else {
            float in = Anim.outCubic(Anim.window(t, LOCK + 0.5f, LOCK + 1.1f)) * (1 - out);
            if (in <= 0) return;
            float o = Gfx.fade(in);
            String head = Component.translatable("ui.beyondsolo.monarch.coronation.crowned").getString().toUpperCase(Locale.ROOT);
            SysFont.spaced(g, head, 0, -100 + 6 * (1 - in), 8, light, true, 3f, 0.5f);
            float hw = SysFont.spacedWidth(head, 8, true, 3f);
            float line = 60 * in;
            Ui.divider(g, -hw / 2f - 8 - line, -96.5f, line, Gfx.alpha(light, 0.9f));
            Ui.divider(g, hw / 2f + 8, -96.5f, line, Gfx.alpha(light, 0.9f));
            Gfx.setAlpha(o);
        }
    }

    /** Light gathering in the middle: rings turning faster and faster, a core growing brighter, then drawn in. */
    private static void gather(GuiGraphics g, float t, int c, int light) {
        float grow = Anim.window(t, 0.4f, GATHER);
        float squeeze = Anim.inCubic(Anim.window(t, GATHER, LOCK));
        float spin = t * t * 0.02f;
        float r = (34 + 18 * grow) * (1 - 0.75f * squeeze);
        Shapes.circle(0, -10, r * 1.6f).fill(0x00000000).glow(r * (0.5f + 0.8f * grow), Gfx.alpha(c, 0.15f + 0.45f * grow)).additive().draw(g);
        Shapes.ring(0, -10, r, 0.9f).fill(Gfx.alpha(c, 0.25f + 0.55f * grow)).dashes(24, 0.5f).rotation(spin).draw(g);
        Shapes.ring(0, -10, r * 1.35f, 0.6f).fill(Gfx.alpha(light, 0.15f + 0.4f * grow)).dashes(10, 0.3f).rotation(-spin * 1.4f).draw(g);
        Shapes.ring(0, -10, r * 0.62f, 1.2f).fill(Gfx.alpha(light, 0.2f + 0.6f * grow)).dashes(6, 0.6f).rotation(spin * 2.2f).draw(g);
        float core = 3 + 9 * grow * (1 - 0.6f * squeeze);
        Shapes.circle(0, -10, core).fill(Gfx.alpha(0xFFFFFFFF, 0.5f + 0.5f * grow)).glow(core * 2.5f, Gfx.alpha(light, 0.8f)).additive().draw(g);
        // the crown's emblem shows through, faint, as the light gathers
        float ghost = 0.35f * grow * (1 - squeeze);
        if (ghost > 0.01f) {
            float o = Gfx.fade(ghost);
            Shapes.icon(crownIcon(crown), 0, -10, 30).fill(light).glow(4, Gfx.alpha(c, 0.6f)).additive().draw(g);
            Gfx.setAlpha(o);
        }
    }

    /** The crown placed: rays, shock rings, the emblem, and the title stamped under it. */
    private static void crowned(GuiGraphics g, float t, int c, int light, float toCard) {
        float pop = Anim.outBackStrong(Anim.window(t, LOCK, LOCK + 0.45f));
        float ex = 0, ey = Anim.lerp(-14, -66, toCard);
        float r = Anim.lerp(30, 17, toCard) * pop;
        float rays = Anim.window(t, LOCK, LOCK + 0.5f) * (1 - 0.6f * toCard);
        if (rays > 0) {
            Shapes.rays(ex, ey, 200 * (1 - 0.4f * toCard), 18).fillV(Gfx.alpha(light, 0.3f * rays), Gfx.alpha(c, 0.25f * rays))
                    .rotation(t * 0.02f).additive().draw(g);
        }
        shock(g, ex, ey, t, LOCK, r + 6, light);
        shock(g, ex, ey, t, LOCK + 0.18f, r + 6, c);
        shock(g, ex, ey, t, LOCK + 0.4f, r + 6, 0xFFFFFFFF);
        if (r > 0.5f) emblem(g, ex, ey, r, c, light, 1 - 0.6f * Anim.window(t, LOCK, LOCK + 1.5f));
        // "MONARCH OF NIGHT", stamped big then settling; it rides up with the emblem to the card
        float stamp = Anim.window(t, LOCK + 0.2f, LOCK + 0.5f);
        if (stamp <= 0) return;
        float scale = Anim.lerp(2.4f, 1f, Anim.inCubic(stamp)) * (1 - 0.35f * toCard);
        float o = Gfx.fade(Math.min(1, stamp * 1.6f));
        String title = crown.title().getString().toUpperCase(Locale.ROOT);
        float ty = Anim.lerp(48, -36, toCard);
        g.pose().pushPose();
        g.pose().translate(0, ty, 0);
        g.pose().scale(scale, scale, 1);
        float size = Math.min(19, 400 / Math.max(1, SysFont.spacedWidth(title, 19, true, 2.8f)) * 19);
        SysFont.spaced(g, title, 0, -size * 0.5f, size, 0xFFFFFFFF, true, 2.8f, 0.5f);
        g.pose().popPose();
        Gfx.setAlpha(o);
    }

    /** What the crown rules and the powers it brings, on the crown keys. */
    private static void card(GuiGraphics g, float t, int c, int light) {
        float in = Anim.outCubic(Anim.window(t, CARD - 0.1f, CARD + 0.5f));
        if (in <= 0) return;
        float o = Gfx.fade(in);
        // sized to sit inside the letterbox bars at every GUI scale
        float cw = 320, ch = 124;
        float x0 = -cw / 2f, y0 = -28 + 12 * (1 - in);
        Shapes.box(x0, y0, cw, ch).radius(12).fillV(0xF2100A20, 0xF2050310)
                .border(1f, Gfx.fade(c, 0.95f), Gfx.fade(c, 0.25f)).glow(14, Gfx.alpha(c, 0.28f)).draw(g);
        float y = y0 + 10;
        // chips: the crown and its element
        String[] chips = {crown.displayName().getString().toUpperCase(Locale.ROOT), crown.element().displayName().getString()};
        float total = 0;
        for (String ch1 : chips) total += SysFont.width(ch1, 6f, true) + 9 + 4;
        float cx = -total / 2f;
        cx += Ui.chip(g, cx, y, 10, chips[0], 6f, c, true) + 4;
        Ui.chip(g, cx, y, 10, chips[1], 6f, Gfx.opaque(crown.element().color()), false);
        y += 15;
        // the domain
        float d = Anim.window(t, CARD + 0.3f, CARD + 0.7f);
        float od = Gfx.fade(d);
        String label = Component.translatable("ui.beyondsolo.monarch.throne.domain").getString().toUpperCase(Locale.ROOT);
        SysFont.spaced(g, label, 0, y, 5.2f, SystemTheme.TEXT_DIM, true, 2f, 0.5f);
        y += 8;
        for (SysFont.Line line : SysFont.wrap(crown.domainText(), 6.4f, false, cw - 40)) {
            SysFont.draw(g, line, 0, y, SystemTheme.TEXT, 0.5f);
            y += 6.4f * SysFont.LINE;
        }
        Gfx.setAlpha(od);
        y = Math.max(y + 3, y0 + 52);
        Ui.divider(g, x0 + 16, y, cw - 32, Gfx.alpha(c, 0.5f));
        y += 5;
        SysFont.spaced(g, Component.translatable("ui.beyondsolo.monarch.coronation.powers").getString().toUpperCase(Locale.ROOT), 0, y, 5.2f,
                SystemTheme.TEXT_DIM, true, 2f, 0.5f);
        y += 10;
        // the four powers, each with its crown key
        float slot = 76;
        float sx = -1.5f * slot;
        for (int i = 0; i < 4; i++) {
            Skill s = MonarchPowers.onKey(crown, i);
            if (s == null) continue;
            float sIn = Anim.outBack(Anim.window(t, CARD + 0.8f + i * 0.22f, CARD + 1.2f + i * 0.22f));
            if (sIn <= 0) continue;
            float px = sx + i * slot;
            SkillEmblem.draw(g, s, px, y + 9, 9 * sIn, i == 3 ? 0.9f : 0.5f, false);
            float os = Gfx.fade(Math.min(1, sIn));
            SysFont.draw(g, SysFont.fit(s.displayName().getString(), 6f, true, slot - 6), px, y + 22, 6f, i == 3 ? light : 0xFFFFFFFF, true, 0.5f);
            String key = CrownBar.keyLabel(MonarchClientEvents.CROWN_KEYS[i], true);
            float kw = Ui.keycapWidth(9, key);
            Ui.keycap(g, px - kw / 2f, y + 30, 9, key, i == 3 ? c : SystemTheme.TEXT_SOFT);
            Gfx.setAlpha(os);
        }
        float f = Anim.window(t, CARD + 1.9f, CARD + 2.4f);
        float of = Gfx.fade(f);
        SysFont.draw(g, SysFont.fit(Component.translatable("ui.beyondsolo.monarch.coronation.footer").getString(), 5.6f, false, cw - 30), 0,
                y0 + ch - 11, 5.6f, Gfx.mix(c, 0xFFFFFFFF, 0.5f), false, 0.5f);
        Gfx.setAlpha(of);
        Gfx.setAlpha(o);
    }

    /** The crown's emblem: a dark disc edged in its colour, two turning rings, its sign. */
    private static void emblem(GuiGraphics g, float x, float y, float r, int c, int light, float energy) {
        Shapes.circle(x, y, r * 2f).fill(0x00000000).glow(r * (0.8f + 0.8f * energy), Gfx.alpha(c, 0.3f + 0.4f * energy)).additive().draw(g);
        Shapes.circle(x, y, r).fillV(0xF0181030, 0xF0080512).border(Math.max(1f, r * 0.07f), c, Gfx.fade(c, 0.4f)).draw(g);
        Shapes.ring(x, y, r * 1.2f, Math.max(0.6f, r * 0.05f)).fill(Gfx.alpha(c, 0.75f)).dashes(18, 0.45f).rotation(Anim.loop(12f)).draw(g);
        Shapes.ring(x, y, r * 1.42f, Math.max(0.5f, r * 0.03f)).fill(Gfx.alpha(light, 0.45f)).dashes(8, 0.25f).rotation(-Anim.loop(18f)).draw(g);
        Shapes.icon(crownIcon(crown), x, y, r * 1.3f).fillV(Gfx.mix(c, 0xFFFFFFFF, 0.85f), light)
                .glow(r * 0.2f + r * 0.3f * energy, Gfx.alpha(c, 0.7f)).draw(g);
        Shapes.icon(com.beyondsolo.client.gfx.Icons.CROWN, x, y - r * 1.25f, r * 0.75f).fillV(0xFFFFFFFF, light)
                .glow(r * 0.15f, Gfx.alpha(c, 0.8f)).draw(g);
    }

    private static void shock(GuiGraphics g, float x, float y, float t, float at, float r0, int color) {
        float k = Anim.window(t, at, at + 0.75f);
        if (k <= 0 || k >= 1) return;
        float r = r0 + 140 * Anim.outCubic(k);
        Shapes.ring(x, y, r, 2.4f * (1 - k) + 0.4f).fill(Gfx.alpha(color, 0.8f * (1 - k))).glow(5, Gfx.alpha(color, 0.5f * (1 - k))).additive().draw(g);
    }

    private static float flash(float t, float at, float strength) {
        if (t < at) return 0;
        float k = (t - at) / 0.4f;
        return k >= 1 ? 0 : strength * (1 - k) * (1 - k);
    }

    private static void skipHint(GuiGraphics g, int w, int h, float t) {
        float hint = Anim.window(t, 1.2f, 1.8f) * (1 - Anim.window(t, FINISH - 1.2f, FINISH - 0.8f));
        if (hint <= 0) return;
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

    private static ResourceLocation crownIcon(Dominion d) {
        return BeyondSolo.id("textures/gui/icon/monarch_" + d.id() + ".png");
    }

    // ================================================================ sparks

    /** While the light gathers: motes streaming in from the edges of sight, more and more of them. */
    private static void spawnInward(int w, int h, float s, int c, float t) {
        if (t >= GATHER + 0.4f || time - lastTime <= 0) return;
        spawn += (time - lastTime) * (20 + 90 * Anim.window(t, 0.3f, GATHER));
        while (spawn >= 1) {
            spawn--;
            Spark k = new Spark();
            double a = RANDOM.nextDouble() * Math.PI * 2;
            float dist = 170 + RANDOM.nextFloat() * 90;
            k.x = (float) Math.cos(a) * dist;
            k.y = (float) Math.sin(a) * dist * 0.75f - 10;
            float v = 160 + RANDOM.nextFloat() * 120;
            k.vx = (float) -Math.cos(a) * v;
            k.vy = (float) -Math.sin(a) * v * 0.75f;
            k.life = dist / v * 0.95f;
            k.size = 0.7f + RANDOM.nextFloat() * 1.1f;
            k.color = RANDOM.nextFloat() < 0.3f ? 0xFFFFFFFF : c;
            k.inward = true;
            SPARKS.add(k);
        }
    }

    private static void burst(int color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            Spark s = new Spark();
            double a = RANDOM.nextDouble() * Math.PI * 2;
            float v = speed * (0.35f + RANDOM.nextFloat() * 0.85f);
            s.x = (float) Math.cos(a) * 20;
            s.y = (float) Math.sin(a) * 20 - 14;
            s.vx = (float) Math.cos(a) * v;
            s.vy = (float) Math.sin(a) * v - 18;
            s.life = 0.7f + RANDOM.nextFloat() * 1.0f;
            s.size = 0.9f + RANDOM.nextFloat() * 1.6f;
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
            if (!s.inward) {
                s.vx *= drag;
                s.vy = s.vy * drag + 70f * dt;
            }
            s.x += s.vx * dt;
            s.y += s.vy * dt;
        }
    }

    private static void drawSparks(GuiGraphics g, float scale, int w, int h) {
        for (Spark s : SPARKS) {
            float left = 1 - s.age / s.life;
            float r = s.size * (0.4f + 0.6f * (s.inward ? 1 - left * 0.5f : left));
            float a = s.inward ? Math.min(1f, (1 - left) * 3f) * Math.min(1, left * 4) : Math.min(1f, left * 1.6f);
            Shapes.circle(s.x, s.y, r).fill(Gfx.alpha(s.color, a)).glow(r * 3f, Gfx.alpha(s.color, 0.55f * a)).additive().draw(g);
        }
    }

    private CoronationOverlay() {}
}
