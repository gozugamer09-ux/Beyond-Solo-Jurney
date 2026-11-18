package com.beyondsolo.client.chronicle;

import com.beyondsolo.chronicle.journal.JournalEntry;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.client.ClientEvents;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.notify.SystemNotification;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * The chronicle's moments on screen.
 * <ul>
 *   <li><b>Title acquired</b>: the world dims and takes the title's colour, a crest bursts in with rays and rings, a
 *   "[ TITLE ACQUIRED ]" header draws its lines out, the name types itself on a band of light, then its tier, what
 *   earned it and what it does, and whether it is now worn. Several at once: the first plays and the rest are listed
 *   under it. Waits for the other big moments, holds new pop-ups while it plays; the hide key skips it.</li>
 *   <li><b>New journal page</b>: a small card slides in at the right, above the hotbar.</li>
 * </ul>
 */
public final class TitleOverlay {
    private record Earned(String key, boolean worn) {}

    private static final Deque<Earned> EARNED = new ArrayDeque<>();
    private static final Deque<JournalEntry> PAGES = new ArrayDeque<>();
    private static final float EXIT = 0.5f;
    private static final float PAGE_TIME = 2.8f;

    private static Earned current;
    private static List<Earned> also = List.of();
    private static double startedAt;
    private static float time = 4.4f;

    private static JournalEntry page;
    private static int pageMore;
    private static double pageAt;

    public static void earned(String key, boolean worn) {
        EARNED.addLast(new Earned(key, worn));
        NotificationManager.log(SystemNotification.of(SystemNotification.Kind.REWARD,
                Component.translatable("chronicle.beyondsolo.acquired"), Component.translatable(key)));
    }

    public static void page(JournalEntry entry) {
        PAGES.addLast(entry);
    }

    public static boolean active() {
        return current != null;
    }

    /** Nothing else holds the middle of the screen. */
    private static boolean free() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return false;
        return !com.beyondsolo.client.evaluation.EvaluationOverlay.active()
                && !com.beyondsolo.client.awakening.AwakeningOverlay.active()
                && !com.beyondsolo.client.quest.QuestOverlay.active()
                && !com.beyondsolo.client.gate.GateOverlay.active()
                && !com.beyondsolo.client.skill.SkillOverlay.active()
                && !com.beyondsolo.client.job.JobRevealOverlay.active()
                && !com.beyondsolo.client.job.CutsceneCamera.active()
                && !com.beyondsolo.client.rune.RuneClient.absorbing()
                && !com.beyondsolo.client.breaks.BreakClient.bannerPending()
                && !com.beyondsolo.client.tower.TowerClient.holdsScreen();
    }

    public static void frame() {
        if (current != null && Anim.since(startedAt) >= time) {
            current = null;
            also = List.of();
            NotificationManager.setHeld(false);
        }
        if (current == null && !EARNED.isEmpty() && free()) {
            current = EARNED.pollFirst();
            also = List.of();
            if (EARNED.size() >= 2) {
                // many at once (a hunter's past feats on their first day with the chronicle): one moment for all,
                // led by the rarest
                List<Earned> all = new ArrayList<>(EARNED);
                all.add(0, current);
                EARNED.clear();
                all.sort(Comparator.comparingInt((Earned e) -> Titles.tierOf(e.key()).ordinal()).reversed());
                current = all.remove(0);
                also = all;
            }
            startedAt = Anim.now();
            time = !also.isEmpty() ? 5.6f : EARNED.isEmpty() ? 4.4f : 3.2f;
            NotificationManager.setHeld(true);
            TitleTier tier = Titles.tierOf(current.key());
            sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 0.8f);
            sound(SoundEvents.BEACON_POWER_SELECT, tier.ordinal() >= TitleTier.LEGENDARY.ordinal() ? 0.9f : 1.25f, 0.55f);
            if (tier.ordinal() >= TitleTier.EPIC.ordinal()) sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.1f, 0.35f);
        }
        if (page != null && Anim.since(pageAt) >= PAGE_TIME) page = null;
        if (page == null && !PAGES.isEmpty() && current == null && Minecraft.getInstance().player != null) {
            page = PAGES.pollFirst();
            pageMore = PAGES.size();
            PAGES.clear();
            pageAt = Anim.now();
            sound(SoundEvents.BOOK_PAGE_TURN, 1.1f, 0.9f);
        }
    }

    /** The hide key: jumps to the end of the moment. */
    public static boolean skip() {
        if (current == null) return false;
        float t = Anim.since(startedAt);
        float end = time - EXIT;
        if (t < end) startedAt -= end - t;
        return true;
    }

    public static void reset() {
        EARNED.clear();
        PAGES.clear();
        if (current != null) NotificationManager.setHeld(false);
        current = null;
        also = List.of();
        page = null;
    }

    private static void sound(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ------------------------------------------------------------------ drawing

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (page != null) drawPage(g, Anim.since(pageAt));
        if (current != null) drawEarned(g, current, Anim.since(startedAt));
    }

    private static void drawEarned(GuiGraphics g, Earned m, float t) {
        TitleDef def = Titles.byKey(m.key());
        TitleTier tier = Titles.tierOf(m.key());
        int color = tier.color();
        int light = Gfx.mix(color, 0xFFFFFFFF, 0.55f);
        float w = g.guiWidth();
        float h = g.guiHeight();
        float cx = w / 2f;
        float cy = h / 2f - (also.isEmpty() ? 26 : 40);
        float in = Anim.clamp01(t / 0.35f);
        float out = Anim.clamp01((t - (time - EXIT)) / EXIT);
        float live = in * (1 - out);
        float old = Gfx.fade(live);

        // the world dims and takes the title's colour at the edges
        Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFF02060E, 0.4f)).draw(g);
        float flash = 1 - Anim.clamp01((t - 0.2f) / 0.7f);
        Shapes.vignette(0, 0, w, h, 0.2f, 1.35f).fill(Gfx.alpha(color, 0.16f + 0.3f * flash)).draw(g);

        // the crest bursts in
        float ey = cy - 20;

        float pop = Anim.outBackStrong(Anim.window(t, 0.1f, 0.6f));
        float rays = Anim.window(t, 0.15f, 0.7f) * (1 - out);
        if (rays > 0) {
            Shapes.rays(cx, ey, 120, 20).fillV(Gfx.alpha(light, 0.26f * rays), Gfx.alpha(color, 0.1f * rays)).rotation(t * 0.025f).additive().draw(g);
        }
        for (int i = 0; i < 2; i++) {
            float k = Anim.window(t, 0.25f + i * 0.15f, 1.05f + i * 0.15f);
            if (k > 0 && k < 1) Shapes.ring(cx, ey, 16 + 80 * Anim.outCubic(k), 2f * (1 - k)).fill(Gfx.alpha(light, 1 - k)).draw(g);
        }
        float r = 15 * pop;
        if (r > 0.5f) {
            Shapes.circle(cx, ey, r * 1.9f).fill(0).glow(r * 1.2f, Gfx.alpha(color, 0.3f + 0.4f * flash)).additive().draw(g);
            Shapes.ring(cx, ey, r * 1.32f, 0.7f).fill(Gfx.alpha(color, 0.7f)).dashes(20, 0.5f).rotation(Anim.loop(10f)).draw(g);
            Shapes.circle(cx, ey, r).fillV(Gfx.alpha(Gfx.mix(color, 0xFF0A1428, 0.35f), 0.95f), 0xF0070D1A)
                    .border(1f, light, Gfx.alpha(color, 0.5f)).glow(5, Gfx.alpha(color, 0.5f)).draw(g);
            Shapes.icon(def != null ? ChronicleIcons.of(def.category()) : Icons.CROWN, cx, ey, r * 1.15f)
                    .fillV(0xFFFFFFFF, light).glow(r * 0.3f, Gfx.alpha(color, 0.8f)).draw(g);
        }

        // the header draws its lines out
        float head = Anim.outCubic(Anim.window(t, 0.4f, 0.8f));
        float hy = ey - 36 - (1 - head) * 8;
        String header = Component.translatable("chronicle.beyondsolo.acquired.header").getString();
        float oldH = Gfx.fade(head);
        SysFont.spaced(g, header, cx, hy, 8.5f, light, true, 3.2f, 0.5f);
        float hw = SysFont.spacedWidth(header, 8.5f, true, 3.2f);
        float line = 64 * head;
        Ui.divider(g, cx - hw / 2f - 8 - line, hy + 3.2f, line, Gfx.alpha(color, 0.9f));
        Ui.divider(g, cx + hw / 2f + 8, hy + 3.2f, line, Gfx.alpha(color, 0.9f));
        Gfx.setAlpha(oldH);

        // the name types itself on a band of light
        float ny = ey + 26;
        SysFont.Line name = SysFont.line(Component.translatable(m.key()).getString(), 17, true);
        List<SysFont.Line> descLines = def == null ? List.of() : limit(SysFont.wrap(def.description(), 7f, false, 250), 2);

        // a soft shade behind the words, so they read on snow and sand too
        float shade = Anim.outCubic(Anim.window(t, 0.5f, 1.0f));
        if (shade > 0) {
            float bodyH = 39 + descLines.size() * 7f * SysFont.LINE + 3 + (def == null ? 0 : def.perks().size() * (7.5f * SysFont.LINE + 1)) + 20;
            if (!also.isEmpty()) bodyH += 10 + Math.min(5, also.size()) * 7.5f * SysFont.LINE + (also.size() > 5 ? 8 : 0);
            float top = ny - 9;
            float sh = bodyH + 9;
            float sw = Math.max(name.width() + 60, 270);
            Shapes.box(cx - sw / 2f, top, sw, sh).radius(sh / 2f)
                    .fill(Gfx.alpha(0xFF02060E, 0.24f * shade)).glow(26, Gfx.alpha(0xFF02060E, 0.24f * shade)).draw(g);
        }
        float band = Anim.outCubic(Anim.window(t, 0.55f, 0.95f));
        if (band > 0) {
            float bw = (name.width() + 70) * band;
            Shapes.box(cx - bw / 2f, ny - 5, bw / 2f, 22).fillH(Gfx.alpha(color, 0f), Gfx.alpha(color, 0.22f)).draw(g);
            Shapes.box(cx, ny - 5, bw / 2f, 22).fillH(Gfx.alpha(color, 0.22f), Gfx.alpha(color, 0f)).draw(g);
            Ui.divider(g, cx - bw / 2f, ny - 5.5f, bw, Gfx.alpha(light, 0.8f));
            Ui.divider(g, cx - bw / 2f, ny + 16.5f, bw, Gfx.alpha(light, 0.8f));
        }
        int total = SysFont.length(name);
        int shown = Math.round(total * Anim.clamp01((t - 0.7f) / 0.45f));
        if (shown > 0) {
            float nx = cx - name.width() / 2f;
            int nameColor = Gfx.mix(color, 0xFFFFFFFF, 0.55f);
            SysFont.draw(g, shown >= total ? name : SysFont.truncate(name, shown), nx, ny, nameColor, 0f);
            if (shown < total) {
                float cw = SysFont.truncate(name, shown).width();
                Shapes.box(nx + cw + 1, ny, 1.5f, 13).fill(Gfx.alpha(light, Anim.pulse(0.25f))).draw(g);
            }
        }

        // tier, what earned it, what it does
        float d = Anim.outCubic(Anim.window(t, 1.1f, 1.45f));
        float dy = ny + 24 + (1 - d) * 5;
        if (d > 0) {
            float oldD = Gfx.fade(d);
            String tierText = tier.displayName().getString().toUpperCase();
            String cat = def != null ? def.category().displayName().getString() : Component.translatable("chronicle.beyondsolo.category.special").getString();
            float cw = SysFont.width(tierText, 6f, true) + 9 + 4 + SysFont.width(cat, 6f, true) + 9;
            float chipX = cx - cw / 2f;
            chipX += Ui.chip(g, chipX, dy, 10, tierText, 6f, color, true) + 4;
            Ui.chip(g, chipX, dy, 10, cat, 6f, SystemTheme.TEXT_SOFT, false);
            dy += 15;
            if (def != null) {
                for (SysFont.Line l : descLines) {
                    SysFont.draw(g, l, cx - l.width() / 2f, dy, SystemTheme.TEXT_SOFT, 0f);
                    dy += 7f * SysFont.LINE;
                }
                dy += 3;
                for (PerkLine p : def.perks()) {
                    String s = p.describe().getString();
                    float pw = SysFont.width(s, 7.5f, true) + 13;
                    float px = cx - pw / 2f;
                    Shapes.icon(ChronicleIcons.of(p.perk()), px + 4, dy + 3.4f, 7.5f).fill(light).glow(2, Gfx.alpha(color, 0.6f)).draw(g);
                    SysFont.draw(g, s, px + 12, dy, 7.5f, SystemTheme.GOOD, true, 0f);
                    dy += 7.5f * SysFont.LINE + 1;
                }
            }
            Gfx.setAlpha(oldD);
        }

        // worn now, or where to wear it
        float e = Anim.outCubic(Anim.window(t, 1.6f, 1.9f));
        if (e > 0) {
            float oldE = Gfx.fade(e);
            float fy = dy + 4;
            if (m.worn()) {
                String worn = Component.translatable("chronicle.beyondsolo.acquired.worn").getString();
                float ww = SysFont.width(worn, 6.2f, true) + 9;
                Ui.chip(g, cx - ww / 2f, fy, 11, worn, 6.2f, SystemTheme.ACCENT, true);
            } else {
                String hint = Component.translatable("chronicle.beyondsolo.acquired.wear").getString();
                String key = ClientEvents.OPEN_SYSTEM.getTranslatedKeyMessage().getString();
                float tw = SysFont.width(hint, 7f, false);
                float kw = Ui.keycapWidth(10, key);
                float hx = cx - (tw + 5 + kw) / 2f;
                SysFont.text(g, hint, hx, fy + 2, 7f, SystemTheme.TEXT_SOFT);
                Ui.keycap(g, hx + tw + 5, fy, 10, key, color);
            }
            fy += 16;
            if (!also.isEmpty()) {
                String more = Component.translatable("chronicle.beyondsolo.acquired.also", also.size()).getString();
                SysFont.spaced(g, more, cx, fy, 6f, SystemTheme.TEXT_DIM, true, 1.4f, 0.5f);
                fy += 10;
                int shownNames = Math.min(5, also.size());
                for (int i = 0; i < shownNames; i++) {
                    float k = Anim.outCubic(Anim.window(t, 1.9f + i * 0.12f, 2.2f + i * 0.12f));
                    if (k <= 0) continue;
                    Earned a = also.get(i);
                    float oldK = Gfx.fade(k);
                    String n = Component.translatable(a.key()).getString();
                    SysFont.draw(g, n, cx, fy + (1 - k) * 4, 7.5f, Gfx.mix(Titles.tierOf(a.key()).color(), 0xFFFFFFFF, 0.4f), true, 0.5f);
                    Gfx.setAlpha(oldK);
                    fy += 7.5f * SysFont.LINE;
                }
                if (also.size() > shownNames) {
                    String rest = Component.translatable("chronicle.beyondsolo.acquired.rest", also.size() - shownNames).getString();
                    SysFont.draw(g, rest, cx, fy, 6.2f, SystemTheme.TEXT_DIM, false, 0.5f);
                }
            }
            Gfx.setAlpha(oldE);
        }
        Gfx.setAlpha(old);
    }

    private static List<SysFont.Line> limit(List<SysFont.Line> lines, int n) {
        return lines.size() <= n ? lines : lines.subList(0, n);
    }

    /** The little card for a new journal page. */
    private static void drawPage(GuiGraphics g, float t) {
        JournalEntry e = page;
        float in = Anim.outBack(Anim.clamp01(t / 0.35f));
        float out = Anim.clamp01((t - (PAGE_TIME - 0.35f)) / 0.35f);
        String head = Component.translatable("chronicle.beyondsolo.page.header").getString();
        String name = pageMore > 0 ? Component.translatable("chronicle.beyondsolo.page.more", e.displayName(), pageMore).getString()
                : e.displayName().getString();
        float w = Math.max(SysFont.spacedWidth(head, 5.6f, true, 1.5f), SysFont.width(name, 7.5f, true)) + 34;
        float h = 24;
        float x = g.guiWidth() - w - 8 + (1 - in) * (w + 12) + out * 16;
        float y = g.guiHeight() - 74;
        float old = Gfx.fade(Anim.clamp01(t / 0.2f) * (1 - out));
        int color = SystemTheme.GOLD;
        Ui.panel(g, x, y, w, h, 8, color, 0.6f);
        float pulse = Anim.window(t, 0.2f, 0.8f);
        Shapes.circle(x + 13, y + h / 2f, 7.5f).fillV(Gfx.alpha(color, 0.3f), Gfx.alpha(color, 0.08f)).border(0.7f, Gfx.alpha(color, 0.8f)).draw(g);
        if (pulse > 0 && pulse < 1) Shapes.ring(x + 13, y + h / 2f, 7.5f + 10 * Anim.outCubic(pulse), 1.2f * (1 - pulse)).fill(Gfx.alpha(color, 1 - pulse)).draw(g);
        Shapes.icon(ChronicleIcons.JOURNAL, x + 13, y + h / 2f, 9).fill(Gfx.mix(color, 0xFFFFFFFF, 0.4f)).draw(g);
        SysFont.spaced(g, head, x + 25, y + 4.5f, 5.6f, Gfx.mix(color, 0xFFFFFFFF, 0.2f), true, 1.5f, 0f);
        SysFont.draw(g, name, x + 25, y + 12.5f, 7.5f, SystemTheme.TEXT, true, 0f);
        Gfx.setAlpha(old);
    }

    private TitleOverlay() {}
}
