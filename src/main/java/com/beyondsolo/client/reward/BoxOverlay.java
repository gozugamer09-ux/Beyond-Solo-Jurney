package com.beyondsolo.client.reward;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.reward.BoxCurse;
import com.beyondsolo.reward.BoxKind;
import com.beyondsolo.reward.BoxReward;
import com.beyondsolo.reward.RewardBoxItem;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * A random box opening, as a moment in the middle of the screen: the box shakes with the light leaking out of it,
 * bursts open, and what it held rises out of the light (its name types itself, then its kind, the box's rank and the
 * rest of what was inside). A cursed box's curse comes down after it: the light turns red and the curse is named,
 * with what it does. Waits for the other big moments and holds new pop-ups while it plays; the hide key skips it.
 */
public final class BoxOverlay {
    record Opened(BoxKind kind, PlayerRank rank, BoxReward reward, @Nullable BoxCurse curse, long detail) {}

    private static final Deque<Opened> QUEUE = new ArrayDeque<>();
    private static final float EXIT = 0.5f;
    /** When the lid gives. */
    private static final float BURST = 0.6f;
    /** When a curse comes down. */
    private static final float CURSE = 2.1f;
    private static final int CURSE_RED = 0xFFE0405A;

    @Nullable private static Opened current;
    private static double startedAt;
    private static float time;
    private static boolean burstPlayed, cursePlayed;

    static void opened(Opened o) {
        QUEUE.addLast(o);
    }

    public static boolean active() {
        return current != null;
    }

    /** A box moment is queued and will start as soon as the screen is free (other moments let it go first). */
    public static boolean waiting() {
        return current == null && !QUEUE.isEmpty();
    }

    /** Nothing else holds the middle of the screen. */
    private static boolean free() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return false;
        return !com.beyondsolo.client.evaluation.EvaluationOverlay.active()
                && !com.beyondsolo.client.awakening.AwakeningOverlay.active()
                && !com.beyondsolo.client.quest.QuestOverlay.active()
                && !com.beyondsolo.client.chronicle.TitleOverlay.active()
                && !com.beyondsolo.client.gate.GateOverlay.active()
                && !com.beyondsolo.client.skill.SkillOverlay.active()
                && !com.beyondsolo.client.job.JobRevealOverlay.active()
                && !com.beyondsolo.client.job.CutsceneCamera.active()
                && !com.beyondsolo.client.rune.RuneClient.absorbing()
                && !com.beyondsolo.client.monarch.MonarchHud.bannerUp()
                && !com.beyondsolo.client.tower.TowerClient.holdsScreen()
                && !com.beyondsolo.client.raid.RaidClient.holdsScreen();
    }

    public static void frame() {
        if (current != null && Anim.since(startedAt) >= time) {
            current = null;
            NotificationManager.setHeld(false);
        }
        if (current == null && !QUEUE.isEmpty() && free()) {
            current = QUEUE.pollFirst();
            startedAt = Anim.now();
            time = current.curse() != null ? 5.8f : QUEUE.isEmpty() ? 4.0f : 2.9f;
            burstPlayed = cursePlayed = false;
            NotificationManager.setHeld(true);
            sound(SoundEvents.CHEST_LOCKED, 0.7f, 0.5f);
            sound(SoundEvents.AMETHYST_BLOCK_RESONATE, current.kind() == BoxKind.CURSED ? 0.6f : 1.1f, 0.5f);
        }
        if (current != null) {
            float t = Anim.since(startedAt);
            if (!burstPlayed && t >= BURST) {
                burstPlayed = true;
                sound(SoundEvents.CHEST_OPEN, 1.2f, 0.7f);
                sound(SoundEvents.PLAYER_LEVELUP, current.kind() == BoxKind.CURSED ? 0.6f : 1.4f, 0.45f);
                if (current.kind() == BoxKind.BLESSED) sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.3f, 0.3f);
            }
            if (current.curse() != null && !cursePlayed && t >= CURSE) {
                cursePlayed = true;
                sound(SoundEvents.WITHER_SPAWN, 1.6f, 0.35f);
                sound(SoundEvents.BELL_RESONATE, 0.5f, 0.7f);
            }
        }
    }

    /** The hide key: jumps to the end of the moment. */
    public static boolean skip() {
        if (current == null) return false;
        float t = Anim.since(startedAt);
        float end = time - EXIT;
        if (t < end) startedAt -= end - t;
        burstPlayed = cursePlayed = true;
        return true;
    }

    public static void reset() {
        QUEUE.clear();
        if (current != null) NotificationManager.setHeld(false);
        current = null;
    }

    private static void sound(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ------------------------------------------------------------------ drawing

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (current == null || Minecraft.getInstance().player == null) return;
        draw(g, current, Anim.since(startedAt));
    }

    private static void draw(GuiGraphics g, Opened m, float t) {
        int color = m.kind().color();
        int light = Gfx.mix(color, 0xFFFFFFFF, 0.55f);
        float w = g.guiWidth();
        float h = g.guiHeight();
        float cx = w / 2f;
        float cy = h / 2f - (m.curse() != null ? 34 : 22);
        float in = Anim.clamp01(t / 0.3f);
        float out = Anim.clamp01((t - (time - EXIT)) / EXIT);
        float live = in * (1 - out);
        float old = Gfx.fade(live);
        boolean cursed = m.curse() != null && t >= CURSE;
        float curseIn = m.curse() == null ? 0 : Anim.outCubic(Anim.window(t, CURSE, CURSE + 0.4f));

        // the world dims and takes the box's light at the edges (a curse's red, once it comes down)
        Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFF02060E, 0.38f)).draw(g);
        float flash = t < BURST ? 0 : 1 - Anim.clamp01((t - BURST) / 0.8f);
        int edge = cursed ? Gfx.mix(color, CURSE_RED, curseIn) : color;
        Shapes.vignette(0, 0, w, h, 0.2f, 1.35f).fill(Gfx.alpha(edge, 0.14f + 0.3f * flash + 0.25f * curseIn * (1 - Anim.clamp01((t - CURSE - 0.5f) / 1.2f)))).draw(g);

        float iy = cy - 6;
        if (t < BURST) {
            // the box shakes, light leaking out of its seams
            float k = t / BURST;
            float shake = k * k * 2.2f;
            float sx = Mth.sin(t * 61f) * shake, sy = Mth.cos(t * 47f) * shake * 0.6f;
            Shapes.circle(cx, iy, 26 + 10 * k).fill(0).glow(16 + 14 * k, Gfx.alpha(color, 0.2f + 0.45f * k)).additive().draw(g);
            Shapes.rays(cx, iy, 40 + 50 * k, 12).fillV(Gfx.alpha(light, 0.22f * k), Gfx.alpha(color, 0f)).rotation(t * 0.4f).additive().draw(g);
            // (items are deaf to the fade: they grow in and shrink out instead)
            if (in > 0.05f) item(g, RewardBoxItem.of(m.kind(), m.rank(), 1), cx + sx, iy + sy, (3.4f + 0.3f * k) * in);
        } else {
            // it bursts, and what it held rises out of the light
            float b = Anim.clamp01((t - BURST) / 0.7f);
            for (int i = 0; i < 3; i++) {
                float k = Anim.window(t, BURST + i * 0.1f, BURST + 0.75f + i * 0.12f);
                if (k > 0 && k < 1) Shapes.ring(cx, iy, 18 + 95 * Anim.outCubic(k), 2.4f * (1 - k)).fill(Gfx.alpha(i == 1 ? color : light, 1 - k)).draw(g);
            }
            float rays = (1 - out) * (0.35f + 0.65f * (1 - b));
            Shapes.rays(cx, iy, 130, 22).fillV(Gfx.alpha(light, 0.24f * rays), Gfx.alpha(color, 0.08f * rays)).rotation(t * 0.03f).additive().draw(g);
            if (flash > 0) Shapes.circle(cx, iy, 40 * (0.6f + flash)).fill(0).glow(30, Gfx.alpha(0xFFFFFFFF, 0.5f * flash)).additive().draw(g);
            float pop = Anim.outBackStrong(Anim.window(t, BURST + 0.05f, BURST + 0.55f));
            if (pop > 0.02f) {
                Shapes.circle(cx, iy, 24 * pop).fill(0).glow(18, Gfx.alpha(color, 0.45f)).additive().draw(g);
                Shapes.ring(cx, iy, 27 * pop, 0.7f).fill(Gfx.alpha(color, 0.7f)).dashes(18, 0.5f).rotation(Anim.loop(9f)).draw(g);
                lead(g, m.reward(), cx, iy, 3f * pop * (1 - out), color);
            }
        }

        // the header draws its lines out
        float head = Anim.outCubic(Anim.window(t, 0.1f, 0.5f));
        float hy = iy - 46 - (1 - head) * 6;
        String header = Component.translatable(t < BURST ? "box.beyondsolo.header.opening" : "box.beyondsolo.header." + m.kind().name().toLowerCase(Locale.ROOT)).getString();
        float oldH = Gfx.fade(head);
        SysFont.spaced(g, header, cx, hy, 8f, light, true, 3f, 0.5f);
        float hw = SysFont.spacedWidth(header, 8f, true, 3f);
        float line = 56 * head;
        Ui.divider(g, cx - hw / 2f - 8 - line, hy + 3f, line, Gfx.alpha(color, 0.9f));
        Ui.divider(g, cx + hw / 2f + 8, hy + 3f, line, Gfx.alpha(color, 0.9f));
        Gfx.setAlpha(oldH);

        // the name of what came out types itself on a band of light
        float ny = iy + 30;
        String name = name(m.reward());
        SysFont.Line nameLine = SysFont.line(name, 14, true);
        float band = Anim.outCubic(Anim.window(t, BURST + 0.25f, BURST + 0.6f));
        if (band > 0) {
            float bw = (nameLine.width() + 70) * band;
            // a soft shade so the words read on snow and sand too
            Shapes.box(cx - bw / 2f - 30, ny - 8, bw + 60, 70).radius(35).fill(Gfx.alpha(0xFF02060E, 0.22f * band)).glow(24, Gfx.alpha(0xFF02060E, 0.2f * band)).draw(g);
            Shapes.box(cx - bw / 2f, ny - 4, bw / 2f, 19).fillH(Gfx.alpha(color, 0f), Gfx.alpha(color, 0.22f)).draw(g);
            Shapes.box(cx, ny - 4, bw / 2f, 19).fillH(Gfx.alpha(color, 0.22f), Gfx.alpha(color, 0f)).draw(g);
            Ui.divider(g, cx - bw / 2f, ny - 4.5f, bw, Gfx.alpha(light, 0.8f));
            Ui.divider(g, cx - bw / 2f, ny + 14.5f, bw, Gfx.alpha(light, 0.8f));
        }
        int total = SysFont.length(nameLine);
        int shown = Math.round(total * Anim.clamp01((t - BURST - 0.35f) / 0.4f));
        if (shown > 0) {
            float nx = cx - nameLine.width() / 2f;
            SysFont.draw(g, shown >= total ? nameLine : SysFont.truncate(nameLine, shown), nx, ny, nameColor(m.reward()), 0f);
        }

        // what kind of thing it is, the box's rank, and the rest of what was inside
        float d = Anim.outCubic(Anim.window(t, BURST + 0.7f, BURST + 1.05f));
        float dy = ny + 21 + (1 - d) * 4;
        if (d > 0) {
            float oldD = Gfx.fade(d);
            String rank = Component.translatable("box.beyondsolo.rank", m.rank().displayName()).getString().toUpperCase(Locale.ROOT);
            String cat = m.reward().category().displayName().getString().toUpperCase(Locale.ROOT);
            float cw = SysFont.width(rank, 6f, true) + 9 + 4 + SysFont.width(cat, 6f, true) + 9;
            float chipX = cx - cw / 2f;
            chipX += Ui.chip(g, chipX, dy, 10, rank, 6f, color, true) + 4;
            Ui.chip(g, chipX, dy, 10, cat, 6f, SystemTheme.TEXT_SOFT, false);
            dy += 15;
            List<ItemStack> items = m.reward().items();
            for (int i = 1; i < items.size() && i <= 4; i++) {
                ItemStack s = items.get(i);
                String txt = s.getCount() + "× " + s.getHoverName().getString();
                float tw = SysFont.width(txt, 7f, false) + 13;
                float tx = cx - tw / 2f;
                if (out < 0.7f) item(g, s, tx + 4, dy + 3.5f, 0.6f * (1 - out));
                SysFont.draw(g, txt, tx + 13, dy, 7f, SystemTheme.TEXT_SOFT, false, 0f);
                dy += 7f * SysFont.LINE + 2;
            }
            if (!items.isEmpty() && m.reward().gold() > 0) {
                SysFont.draw(g, "+" + gold(m.reward().gold()), cx, dy, 7f, SystemTheme.GOLD, true, 0.5f);
                dy += 7f * SysFont.LINE + 2;
            }
            if (m.curse() != null && m.curse() == BoxCurse.WARDENS) {
                // what the wardens hold is only theirs once they are down
                SysFont.draw(g, Component.translatable("box.beyondsolo.sealed").getString(), cx, dy + 1, 6.5f, Gfx.mix(CURSE_RED, 0xFFFFFFFF, 0.35f), false, 0.5f);
                dy += 6.5f * SysFont.LINE + 2;
            }
            Gfx.setAlpha(oldD);
        }

        // a curse comes down
        if (m.curse() != null && curseIn > 0) {
            float oldC = Gfx.fade(curseIn);
            float by = dy + 8 + (1 - curseIn) * 6;
            String title = Component.translatable("box.beyondsolo.curse.header", m.curse().displayName()).getString().toUpperCase(Locale.ROOT);
            String body = Component.translatable("box.beyondsolo.curse." + m.curse().id() + ".desc", detail(m)).getString();
            List<SysFont.Line> lines = SysFont.wrap(Component.literal(body), 7f, false, 260);
            float bh = 18 + lines.size() * 7f * SysFont.LINE + 6;
            float bw = 290;
            float bx = cx - bw / 2f;
            float shake = (1 - Anim.clamp01((t - CURSE) / 0.35f)) * 2.5f;
            bx += Mth.sin(t * 70) * shake;
            Shapes.box(bx, by, bw, bh).radius(6).fillV(0xE0240810, 0xE0120408).border(0.9f, Gfx.alpha(CURSE_RED, 0.9f), Gfx.alpha(CURSE_RED, 0.3f))
                    .glow(6 + 4 * Anim.pulse(1.1f), Gfx.alpha(CURSE_RED, 0.45f)).draw(g);
            Shapes.icon(Icons.SKULL, bx + 13, by + 9.5f, 9).fill(Gfx.mix(CURSE_RED, 0xFFFFFFFF, 0.35f)).glow(3, Gfx.alpha(CURSE_RED, 0.6f)).draw(g);
            SysFont.spaced(g, title, bx + 23, by + 6, 6.5f, Gfx.mix(CURSE_RED, 0xFFFFFFFF, 0.45f), true, 1.6f, 0f);
            float ly = by + 18;
            for (SysFont.Line l : lines) {
                SysFont.draw(g, l, bx + 12, ly, 0xFFF2D6DA, 0f);
                ly += 7f * SysFont.LINE;
            }
            Gfx.setAlpha(oldC);
        }
        Gfx.setAlpha(old);
    }

    /** What the curse's line says in numbers: wardens, minutes, poisoning, gold. */
    private static Object detail(Opened m) {
        return m.curse() == BoxCurse.GREED ? gold(m.detail()) : String.valueOf(m.detail());
    }

    /** The reward drawn big: its first item, or coins for gold, a star for stat points. */
    private static void lead(GuiGraphics g, BoxReward r, float cx, float cy, float scale, int color) {
        ItemStack lead = r.lead();
        if (scale < 0.05f) return;
        if (!lead.isEmpty()) {
            item(g, lead, cx, cy, scale);
            return;
        }
        ResourceLocation icon = r.statPoints() > 0 ? Icons.STAR : Icons.COINS;
        int c = r.statPoints() > 0 ? SystemTheme.ACCENT_BRIGHT : SystemTheme.GOLD;
        Shapes.icon(icon, cx, cy, 16 * scale / 1.6f).fillV(0xFFFFFFFF, c).glow(5, Gfx.alpha(c, 0.7f)).draw(g);
    }

    /** What the name line says. */
    static String name(BoxReward r) {
        if (!r.lead().isEmpty()) {
            ItemStack lead = r.lead();
            return (lead.getCount() > 1 ? lead.getCount() + "× " : "") + lead.getHoverName().getString();
        }
        if (r.statPoints() > 0) return Component.translatable("box.beyondsolo.stats", r.statPoints()).getString();
        return "+" + gold(r.gold());
    }

    static int nameColor(BoxReward r) {
        if (r.lead().isEmpty()) return r.statPoints() > 0 ? SystemTheme.ACCENT_BRIGHT : SystemTheme.GOLD;
        return itemColor(r.lead());
    }

    /** An item's name colour: its grade's or rarity's, lightened a little to read on the dark. */
    static int itemColor(ItemStack s) {
        TextColor c = s.getHoverName().getStyle().getColor();
        if (c == null) {
            Integer rc = s.getRarity().color().getColor();
            c = rc == null ? null : TextColor.fromRgb(rc);
        }
        int rgb = c == null ? 0xFFFFFF : c.getValue();
        return Gfx.mix(0xFF000000 | rgb, 0xFFFFFFFF, 0.2f);
    }

    static String gold(long amount) {
        return Component.translatable("box.beyondsolo.gold", NumberFormat.getIntegerInstance(Locale.ROOT).format(amount)).getString();
    }

    static void item(GuiGraphics g, ItemStack stack, float cx, float cy, float scale) {
        Shapes.flush(g);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 150);
        g.pose().scale(scale, scale, 1);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
    }

    private BoxOverlay() {}
}
