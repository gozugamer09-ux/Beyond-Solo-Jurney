package com.beyondsolo.client.quest;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.evaluation.EvaluationOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The big quest moments in the middle of the screen, one at a time:
 * <ul>
 *   <li><b>Accepted</b>: a line of light opens into the quest window, the emblem pops, the title types itself,
 *   the objectives slide in, then the window folds up and flies into the quest tracker. Emergencies flash red.</li>
 *   <li><b>Completed</b>: the window opens on rays and shockwaves, a check seal stamps down with a burst of sparks,
 *   the objectives tick off and the rewards pop in with their numbers counting up.</li>
 *   <li><b>Failed</b>: the screen flashes red, the window snaps open and shakes, the header glitches, the title is
 *   struck through and the penalty types out; then it drops away.</li>
 * </ul>
 * The hide key skips to the end of the current one.
 */
public final class QuestOverlay {
    public enum Moment { ACCEPTED, COMPLETED, FAILED }

    private record Entry(Moment moment, Quest quest, int detail) {}

    private static final Deque<Entry> QUEUE = new ArrayDeque<>();
    private static final float WIDTH = 236;
    private static final float EXIT = 0.55f;

    private static Entry current;
    private static double startedAt;
    private static boolean stampPlayed;

    public static void push(Moment moment, Quest quest, int detail) {
        QUEUE.addLast(new Entry(moment, quest, detail));
    }

    public static boolean active() {
        return current != null;
    }

    /** True while a quest's "accepted" moment is waiting or playing, so the tracker can let it land first. */
    public static boolean isIncoming(java.util.UUID id) {
        if (current != null && current.moment == Moment.ACCEPTED && current.quest.id().equals(id)
                && Anim.since(startedAt) < duration(current) - EXIT * 0.4f) return true;
        for (Entry e : QUEUE) if (e.moment == Moment.ACCEPTED && e.quest.id().equals(id)) return true;
        return false;
    }

    private static float duration(Entry e) {
        return switch (e.moment) {
            case ACCEPTED -> 3.6f;
            case COMPLETED -> 4.2f;
            case FAILED -> 3.6f;
        };
    }

    /** Once per frame: retires the finished moment and starts the next one. */
    public static void frame() {
        if (current != null && Anim.since(startedAt) >= duration(current)) {
            current = null;
            NotificationManager.setHeld(false);
        }
        if (current == null && !QUEUE.isEmpty() && !EvaluationOverlay.active() && !com.beyondsolo.client.gate.GateOverlay.active() && !com.beyondsolo.client.npc.NpcBoardScreen.isOpen()) {
            current = QUEUE.pollFirst();
            NotificationManager.setHeld(true);
            startedAt = Anim.now();
            stampPlayed = false;
            switch (current.moment) {
                case ACCEPTED -> sound(current.quest.isForced() ? SoundEvents.NOTE_BLOCK_BIT.value() : SoundEvents.AMETHYST_BLOCK_RESONATE,
                        current.quest.isForced() ? 0.7f : 1.3f, 0.6f);
                case COMPLETED -> sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.6f);
                case FAILED -> sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 0.8f);
            }
        }
    }

    /** The hide key: jumps to the end of the moment on screen. Returns true if it did. */
    public static boolean skip() {
        if (current == null) return false;
        float t = Anim.since(startedAt);
        float end = duration(current) - EXIT;
        if (t < end) startedAt -= end - t;
        return true;
    }

    public static void reset() {
        QUEUE.clear();
        current = null;
        NotificationManager.setHeld(false);
    }

    private static void sound(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ------------------------------------------------------------------ drawing

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || mc.player == null || mc.options.hideGui) return;
        Entry e = current;
        float t = Anim.since(startedAt);
        float dur = duration(e);
        int guiW = g.guiWidth();
        int guiH = g.guiHeight();
        Quest q = e.quest;
        int accent = switch (e.moment) {
            case ACCEPTED -> q.category() == QuestCategory.SET ? SystemTheme.ACCENT : q.category().color();
            case COMPLETED -> SystemTheme.GOLD;
            case FAILED -> SystemTheme.DANGER;
        };

        // ---- screen tint
        float exitK = Anim.window(t, dur - EXIT, dur);
        float live = 1 - Anim.outCubic(exitK);
        switch (e.moment) {
            case COMPLETED -> {
                float flash = 1 - Anim.outCubic(Anim.window(t, 0.3f, 1.4f));
                Shapes.vignette(0, 0, guiW, guiH, 0.2f, 1.4f).fill(Gfx.alpha(SystemTheme.GOLD, (0.12f + 0.3f * flash) * live)).draw(g);
            }
            case FAILED -> {
                float flash = 1 - Anim.outCubic(Anim.window(t, 0f, 1.1f));
                Shapes.box(0, 0, guiW, guiH).fill(Gfx.alpha(0xFF3A0008, 0.35f * flash * live)).draw(g);
                Shapes.vignette(0, 0, guiW, guiH, 0.15f, 1.3f).fill(Gfx.alpha(0xFF9A0F20, (0.25f + 0.5f * flash + 0.08f * Anim.pulse(0.9f)) * live)).draw(g);
            }
            case ACCEPTED -> {
                if (q.isForced()) {
                    float pulse = Anim.pulse(0.55f);
                    Shapes.vignette(0, 0, guiW, guiH, 0.2f, 1.35f).fill(Gfx.alpha(0xFFB01226, (0.18f + 0.22f * pulse) * live)).draw(g);
                } else {
                    float flash = 1 - Anim.outCubic(Anim.window(t, 0f, 1.2f));
                    Shapes.vignette(0, 0, guiW, guiH, 0.25f, 1.45f).fill(Gfx.alpha(accent, 0.16f * flash * live)).draw(g);
                }
            }
        }

        // ---- layout
        List<QuestObjective> objectives = q.objectives();
        List<QuestText.Reward> rewards = QuestText.rewards(q);
        List<SysFont.Line> desc = e.moment == Moment.ACCEPTED ? SysFont.wrap(q.description(), 7.5f, false, WIDTH - 36) : List.of();
        if (desc.size() > 3) desc = desc.subList(0, 3);
        float rowH = 13;
        float h = 52 + desc.size() * 7.5f * SysFont.LINE + (desc.isEmpty() ? 0 : 5) + objectives.size() * rowH + 8;
        if (e.moment == Moment.COMPLETED && !rewards.isEmpty()) h += 30;
        if (e.moment == Moment.FAILED) h += 36;
        if (e.moment == Moment.ACCEPTED) h += 16;
        float x = (guiW - WIDTH) / 2f;
        float y = Math.max(24, guiH * 0.22f);
        float cx = guiW / 2f;

        // ---- exit motion
        g.pose().pushPose();
        float oldAlpha = Gfx.alpha();
        if (exitK > 0) {
            switch (e.moment) {
                case ACCEPTED -> {
                    // fold up and fly into the tracker
                    float k = Anim.inOutCubic(exitK);
                    float tx = QuestTracker.anchorX();
                    float ty = QuestTracker.anchorY();
                    float px = Anim.lerp(cx, tx, k);
                    float py = Anim.lerp(y + h / 2f, ty, k);
                    float s = Anim.lerp(1f, 0.18f, k);
                    g.pose().translate(px, py, 0);
                    g.pose().scale(s, s, 1);
                    g.pose().translate(-cx, -(y + h / 2f), 0);
                    Gfx.fade(1 - Anim.inCubic(exitK));
                }
                case COMPLETED -> {
                    g.pose().translate(0, -22 * Anim.inCubic(exitK), 0);
                    Gfx.fade(1 - Anim.outCubic(exitK));
                }
                case FAILED -> {
                    g.pose().translate(0, 34 * Anim.inCubic(exitK), 0);
                    Gfx.fade(1 - Anim.outCubic(exitK));
                }
            }
        }

        // ---- shake
        float shake = 0;
        if (e.moment == Moment.FAILED) shake = (float) Math.sin(t * 58) * 6f * (float) Math.exp(-t * 4.5f) * Anim.window(t, 0.18f, 0.2f);
        if (e.moment == Moment.ACCEPTED && q.isForced()) shake = (float) Math.sin(t * 50) * 3f * (float) Math.exp(-t * 5f);
        x += shake;
        cx += shake;

        // ---- light behind (completed): rays and glow
        float stampT = 0.42f;
        if (e.moment == Moment.COMPLETED) {
            float rays = Anim.outCubic(Anim.window(t, stampT - 0.05f, stampT + 0.5f)) * live;
            Shapes.rays(cx, y, 150, 14).fillV(Gfx.alpha(0xFFFFF4D0, 0.32f * rays), Gfx.alpha(SystemTheme.GOLD, 0.16f * rays)).rotation(t * 0.025f).additive().draw(g);
            Shapes.circle(cx, y, 30).fill(0x00000000).glow(60, Gfx.alpha(SystemTheme.GOLD, 0.28f * rays)).additive().draw(g);
        }

        // ---- the line of light that opens into the window
        float line = Anim.outCubic(Anim.window(t, 0f, 0.24f));
        float open = e.moment == Moment.FAILED ? Anim.outCubic(Anim.window(t, 0.14f, 0.3f)) : Anim.outBack(Anim.window(t, 0.16f, 0.52f));
        float midY = y + h / 2f;
        if (open < 0.98f && line > 0) {
            float lw = WIDTH * line;
            float fade = 1 - Anim.window(t, 0.35f, 0.55f);
            Shapes.pill(cx - lw / 2f, midY - 0.9f, lw, 1.8f).fill(Gfx.alpha(Gfx.mix(accent, 0xFFFFFFFF, 0.6f), fade))
                    .glow(7, Gfx.alpha(accent, 0.8f * fade)).additive().draw(g);
        }

        if (open > 0.01f) {
            float shown = h * Math.min(open, 1.06f);
            int top = Math.round(midY - shown / 2f) - 14;
            int bottom = Math.round(midY + shown / 2f) + 14;
            g.flush();
            g.enableScissor(0, Math.max(0, top), guiW, Math.min(guiH, bottom));
            drawWindow(g, e, t, x, y, h, accent, desc, rowH, rewards, stampT);
            g.flush();
            g.disableScissor();
        }

        // ---- emblem hanging on the top edge (drawn outside the scissor so it can overlap)
        drawEmblem(g, e, t, cx, y, accent, stampT, live);

        Gfx.setAlpha(oldAlpha);
        g.pose().popPose();
    }

    private static void drawWindow(GuiGraphics g, Entry e, float t, float x, float y, float h, int accent,
                                   List<SysFont.Line> desc, float rowH, List<QuestText.Reward> rewards, float stampT) {
        Quest q = e.quest;
        float flash = 1 - Anim.outCubic(Anim.window(t, 0.3f, 1.1f));
        Shapes.box(x + 2, y + 4, WIDTH - 4, h - 2).radius(11).fill(0x00000000).glow(16, 0x80000000).draw(g);
        Shapes.box(x, y, WIDTH, h).radius(11)
                .fillV(Gfx.mix(SystemTheme.GLASS_TOP, Gfx.alpha(accent, 0.95f), 0.08f + 0.14f * flash), SystemTheme.GLASS_BOTTOM)
                .border(1.2f, Gfx.mix(accent, 0xFFFFFFFF, 0.2f + 0.5f * flash), Gfx.fade(accent, 0.3f))
                .glow(12 + 10 * flash, Gfx.alpha(accent, 0.25f + 0.35f * flash))
                .sheen(Anim.window(t, 0.35f, 1.15f) * 1.6f - 0.3f, t < 1.2f ? 0.18f : 0f)
                .draw(g);
        // corner brackets, the "System window" look
        float c = 10;
        int bracket = Gfx.alpha(Gfx.mix(accent, 0xFFFFFFFF, 0.3f), 0.85f);
        Shapes.box(x + 5, y + 5, c, 1.2f).fill(bracket).draw(g);
        Shapes.box(x + 5, y + 5, 1.2f, c).fill(bracket).draw(g);
        Shapes.box(x + WIDTH - 5 - c, y + h - 6.2f, c, 1.2f).fill(bracket).draw(g);
        Shapes.box(x + WIDTH - 6.2f, y + h - 5 - c, 1.2f, c).fill(bracket).draw(g);

        float cx = x + WIDTH / 2f;

        // ---- header label: letters close in
        String header = Component.translatable(switch (e.moment) {
            case ACCEPTED -> q.isForced() ? "quest.beyondsolo.moment.emergency" : "quest.beyondsolo.moment.accepted";
            case COMPLETED -> "quest.beyondsolo.moment.completed";
            case FAILED -> "quest.beyondsolo.moment.failed";
        }).getString();
        float hk = Anim.outCubic(Anim.window(t, 0.32f, 0.85f));
        float tracking = Anim.lerp(5f, 2.2f, hk);
        float hy = y + 20;
        float oldH = Gfx.fade(hk);
        int headerColor = Gfx.mix(accent, 0xFFFFFFFF, 0.3f);
        if (e.moment == Moment.FAILED) {
            boolean glitch = t < 0.9f || (t * 6.3f) % 1f < 0.09f;
            if (glitch) {
                float j = (float) Math.sin(t * 91) * 2.2f;
                SysFont.spaced(g, header, cx + j + 1.2f, hy, 9.5f, Gfx.alpha(0xFFFF2040, 0.65f), true, tracking, 0.5f);
                SysFont.spaced(g, header, cx - j - 1.2f, hy + 0.5f, 9.5f, Gfx.alpha(0xFF40E0FF, 0.5f), true, tracking, 0.5f);
            }
        }
        SysFont.spaced(g, header, cx, hy, 9.5f, headerColor, true, tracking, 0.5f);
        Gfx.setAlpha(oldH);

        // ---- category and rank chips
        float chipK = Anim.outCubic(Anim.window(t, 0.45f, 0.8f));
        if (chipK > 0) {
            float oldC = Gfx.fade(chipK);
            String cat = q.category().displayName().getString();
            String rank = Component.translatable("quest.beyondsolo.rank_chip", "EDCBAS".charAt(Mth.clamp(q.rankTier(), 0, 5)) + "").getString();
            float cw = SysFont.width(cat, 6f, true) + 10 * 0.9f;
            float rw = SysFont.width(rank, 6f, true) + 10 * 0.9f;
            float chipX = cx - (cw + rw + 4) / 2f;
            Ui.chip(g, chipX, y + 32, 10, cat, 6f, q.category().color(), true);
            Ui.chip(g, chipX + cw + 4, y + 32, 10, rank, 6f, SystemTheme.TEXT_SOFT, false);
            Gfx.setAlpha(oldC);
        }

        // ---- title: types itself out
        SysFont.Line title = SysFont.line(q.title(), 12.5f, true);
        int total = SysFont.length(title);
        int visible = Math.round(total * Anim.window(t, 0.5f, 0.5f + Math.min(0.6f, total * 0.03f)));
        float ty = y + 46;
        if (visible > 0) {
            // anchored on the left of where the full title will sit, so it doesn't slide while it types
            float left = cx - title.width() / 2f;
            SysFont.draw(g, visible >= total ? title : SysFont.truncate(title, visible), left, ty, SystemTheme.TEXT, 0f);
            if (visible < total && (int) (t * 12) % 2 == 0) {
                float shownW = SysFont.truncate(title, visible).width();
                Shapes.box(left + shownW + 1, ty, 1.2f, 10).fill(Gfx.alpha(accent, 0.9f)).draw(g);
            }
        }
        if (e.moment == Moment.FAILED) {
            // struck through
            float sk = Anim.inOutCubic(Anim.window(t, 0.95f, 1.35f));
            if (sk > 0) {
                float sw = title.width() + 10;
                Shapes.pill(cx - sw / 2f, ty + 4.2f, sw * sk, 1.6f).fill(SystemTheme.DANGER).glow(4, Gfx.alpha(SystemTheme.DANGER, 0.7f)).draw(g);
            }
        }
        float rowY = ty + 16;

        // ---- description (accepted only)
        if (!desc.isEmpty()) {
            float dk = Anim.outCubic(Anim.window(t, 0.75f, 1.15f));
            float oldD = Gfx.fade(dk);
            for (SysFont.Line l : desc) {
                SysFont.draw(g, l, cx, rowY, SystemTheme.TEXT_SOFT, 0.5f);
                rowY += 7.5f * SysFont.LINE;
            }
            Gfx.setAlpha(oldD);
            rowY += 5;
        }
        Ui.divider(g, x + 16, rowY - 2, WIDTH - 32, Gfx.alpha(accent, 0.45f));
        rowY += 4;

        // ---- objectives slide in one after another
        List<QuestObjective> objectives = q.objectives();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective o = objectives.get(i);
            float start = 0.8f + i * 0.12f;
            float k = Anim.outCubic(Anim.window(t, start, start + 0.35f));
            if (k <= 0) continue;
            float oldO = Gfx.fade(k);
            float ox = x + 18 - (1 - k) * 18;
            float oy = rowY + i * rowH;
            boolean done = e.moment == Moment.COMPLETED || o.done();
            int rowColor = done ? SystemTheme.GOOD : e.moment == Moment.FAILED ? SystemTheme.DANGER : SystemTheme.ACCENT;
            if (done && e.moment == Moment.COMPLETED) {
                float pop = Anim.outBackStrong(Anim.window(t, start + 0.1f, start + 0.4f));
                Shapes.circle(ox + 4, oy + 4.5f, 4.6f * pop).fillV(Gfx.alpha(SystemTheme.GOOD, 0.4f), Gfx.alpha(SystemTheme.GOOD, 0.15f)).border(0.75f, SystemTheme.GOOD).draw(g);
                Shapes.icon(Icons.CHECK, ox + 4, oy + 4.5f, 7 * pop).fill(0xFFE8FFE8).glow(2, Gfx.alpha(SystemTheme.GOOD, 0.7f)).draw(g);
            } else {
                Shapes.icon(QuestText.icon(o.type()), ox + 4, oy + 4.5f, 8).fill(Gfx.mix(rowColor, 0xFFFFFFFF, 0.3f)).glow(2, Gfx.alpha(rowColor, 0.4f)).draw(g);
            }
            String count = (e.moment == Moment.COMPLETED ? o.required() : o.progress()) + "/" + o.required();
            float countW = SysFont.width(count, 7.5f, true);
            String label = SysFont.fit(o.label().getString(), 8f, false, WIDTH - 36 - countW - 22);
            SysFont.text(g, label, ox + 12, oy + 1, 8f, done ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT);
            SysFont.draw(g, count, x + WIDTH - 18, oy + 1.5f, 7.5f, Gfx.mix(rowColor, 0xFFFFFFFF, 0.25f), true, 1f);
            Gfx.setAlpha(oldO);
        }
        rowY += objectives.size() * rowH + 4;

        switch (e.moment) {
            case ACCEPTED -> {
                // time limit and what it pays
                float k = Anim.outCubic(Anim.window(t, 1.1f, 1.5f));
                float oldA = Gfx.fade(k);
                String limit = q.timeLimit() > 0 ? Component.translatable("quest.beyondsolo.time_limit", QuestText.clock(q.timeLimit())).getString()
                        : q.category() == QuestCategory.DAILY ? Component.translatable("quest.beyondsolo.until_dawn").getString() : "";
                String pay = QuestText.rewardsLine(q).getString();
                String text = limit.isEmpty() ? pay : limit + "   ·   " + pay;
                SysFont.draw(g, SysFont.fit(text, 7f, true, WIDTH - 30), x + WIDTH / 2f, rowY, 7f,
                        q.timeLimit() > 0 && q.isForced() ? Gfx.mix(SystemTheme.DANGER, 0xFFFFFFFF, 0.3f) : SystemTheme.GOLD, true, 0.5f);
                Gfx.setAlpha(oldA);
            }
            case COMPLETED -> drawRewards(g, t, x, rowY, rewards);
            case FAILED -> {
                QuestEvent.FailReason reason = QuestEvent.FailReason.values()[Math.floorMod(e.detail, QuestEvent.FailReason.values().length)];
                float k = Anim.outCubic(Anim.window(t, 1.2f, 1.5f));
                float oldF = Gfx.fade(k);
                SysFont.draw(g, reason.describe(), x + WIDTH / 2f, rowY, 8f, SystemTheme.TEXT, true, 0.5f);
                Gfx.setAlpha(oldF);
                // the penalty types out
                SysFont.Line pen = SysFont.line(QuestText.penaltyLine(reason == QuestEvent.FailReason.ABANDONED ? q.penalty().scaled(0.5f) : q.penalty()), 7.5f, false);
                int pt = SysFont.length(pen);
                int pv = Math.round(pt * Anim.window(t, 1.45f, 1.45f + Math.min(0.8f, pt * 0.02f)));
                if (pv > 0) SysFont.draw(g, pv >= pt ? pen : SysFont.truncate(pen, pv), x + WIDTH / 2f, rowY + 12, Gfx.mix(SystemTheme.DANGER, 0xFFFFFFFF, 0.35f), 0.5f);
            }
        }
    }

    /** Reward chips popping in one by one, numbers counting up. */
    private static void drawRewards(GuiGraphics g, float t, float x, float y, List<QuestText.Reward> rewards) {
        if (rewards.isEmpty()) return;
        float lk = Anim.outCubic(Anim.window(t, 1.15f, 1.4f));
        float oldL = Gfx.fade(lk);
        SysFont.spaced(g, Component.translatable("quest.beyondsolo.rewards").getString(), x + WIDTH / 2f, y, 6.2f, SystemTheme.GOLD, true, 1.6f, 0.5f);
        Gfx.setAlpha(oldL);
        float chipH = 13;
        float[] widths = new float[rewards.size()];
        float total = 0;
        for (int i = 0; i < rewards.size(); i++) {
            QuestText.Reward r = rewards.get(i);
            widths[i] = SysFont.width(QuestText.rewardLabel(r, 1f), 7f, true) + 20;
            total += widths[i] + (i > 0 ? 4 : 0);
        }
        float scale = Math.min(1f, (WIDTH - 20) / total);
        float cx = x + WIDTH / 2f;
        float rx = cx - total * scale / 2f;
        float ry = y + 10;
        for (int i = 0; i < rewards.size(); i++) {
            QuestText.Reward r = rewards.get(i);
            float start = 1.3f + i * 0.14f;
            float k = Anim.window(t, start, start + 0.32f);
            float w = widths[i] * scale;
            if (k > 0) {
                float pop = Anim.outBack(k);
                float oldR = Gfx.fade(Anim.outCubic(k));
                g.pose().pushPose();
                g.pose().translate(rx + w / 2f, ry + chipH / 2f, 0);
                g.pose().scale(pop * scale, pop * scale, 1);
                g.pose().translate(-widths[i] / 2f, -chipH / 2f, 0);
                float fresh = 1 - Anim.outCubic(Anim.window(t, start, start + 0.8f));
                Shapes.pill(0, 0, widths[i], chipH).fillV(Gfx.alpha(r.color(), 0.28f + 0.2f * fresh), Gfx.alpha(r.color(), 0.1f))
                        .border(0.75f, Gfx.alpha(r.color(), 0.8f)).glow(3 + 6 * fresh, Gfx.alpha(r.color(), 0.25f + 0.3f * fresh)).draw(g);
                Shapes.icon(r.icon(), 7.5f, chipH / 2f, 8).fill(Gfx.mix(r.color(), 0xFFFFFFFF, 0.4f)).glow(2, Gfx.alpha(r.color(), 0.6f)).draw(g);
                float count = Anim.outCubic(Anim.window(t, start + 0.05f, start + 0.75f));
                SysFont.title(g, QuestText.rewardLabel(r, count), 14, SysFont.centerY(0, chipH, 7f), 7f, SystemTheme.TEXT);
                g.pose().popPose();
                Gfx.setAlpha(oldR);
            }
            rx += w + 4 * scale;
        }
    }

    private static void drawEmblem(GuiGraphics g, Entry e, float t, float cx, float cy, int accent, float stampT, float live) {
        Quest q = e.quest;
        float r = 15;
        float k;
        ResourceLocation icon;
        switch (e.moment) {
            case COMPLETED -> {
                // the seal stamps down from big
                float u = Anim.window(t, stampT - 0.2f, stampT);
                k = u;
                float s = Anim.lerp(2.6f, 1f, Anim.inCubic(u));
                if (u >= 1 && !stampPlayed) {
                    stampPlayed = true;
                    sound(SoundEvents.PLAYER_LEVELUP, 1.6f, 0.5f);
                }
                if (u <= 0) return;
                shockwaves(g, cx, cy, t, stampT, r + 2, SystemTheme.GOLD, 2, 80);
                sparks(g, cx, cy, t - stampT, SystemTheme.GOLD);
                r *= s;
                icon = Icons.CHECK;
            }
            case FAILED -> {
                k = Anim.outCubic(Anim.window(t, 0.15f, 0.3f));
                icon = QuestText.icon(QuestEvent.FailReason.values()[Math.floorMod(e.detail, QuestEvent.FailReason.values().length)]);
                if (k > 0.9f && !stampPlayed) {
                    stampPlayed = true;
                    sound(SoundEvents.ANVIL_LAND, 0.6f, 0.25f);
                }
            }
            default -> {
                k = Anim.outBackStrong(Anim.window(t, 0.3f, 0.62f));
                icon = QuestText.icon(q.category());
                if (k > 0.5f && !stampPlayed) {
                    stampPlayed = true;
                    sound(SoundEvents.NOTE_BLOCK_CHIME.value(), q.isForced() ? 0.8f : 1.6f, 0.45f);
                }
            }
        }
        if (k <= 0.01f) return;
        float rr = e.moment == Moment.COMPLETED ? r : r * k;
        float alpha = Math.min(1, k * 1.5f);
        float oldE = Gfx.fade(alpha);
        float pulse = Anim.pulse(1.4f);
        Shapes.circle(cx, cy, rr).fillV(0xF5142238, 0xF5070D1A).border(1.4f, Gfx.mix(accent, 0xFFFFFFFF, 0.3f), Gfx.fade(accent, 0.5f))
                .glow(rr * (0.7f + 0.3f * pulse * live), Gfx.alpha(accent, 0.5f)).draw(g);
        Shapes.ring(cx, cy, rr * 1.3f, 0.9f).fill(Gfx.alpha(accent, 0.8f)).dashes(16, 0.45f).rotation(Anim.loop(10f) * (e.moment == Moment.FAILED ? -1 : 1)).draw(g);
        if (e.moment != Moment.FAILED) {
            Shapes.ring(cx, cy, rr * 1.55f, 0.6f).fill(Gfx.alpha(accent, 0.35f)).dashes(30, 0.3f).rotation(-Anim.loop(16f)).draw(g);
        }
        Shapes.icon(icon, cx, cy, rr * 1.15f).fillV(Gfx.mix(accent, 0xFFFFFFFF, 0.8f), Gfx.mix(accent, 0xFFFFFFFF, 0.3f))
                .glow(rr * 0.25f, Gfx.alpha(accent, 0.8f)).draw(g);
        if (e.moment == Moment.FAILED) {
            // a crack across the emblem
            float ck = Anim.outCubic(Anim.window(t, 0.3f, 0.5f));
            Shapes.box(cx - rr * 1.1f, cy - 0.6f, rr * 2.2f * ck, 1.2f).fill(Gfx.alpha(0xFFFFE0E0, 0.85f)).glow(3, Gfx.alpha(SystemTheme.DANGER, 0.8f)).draw(g);
        }
        Gfx.setAlpha(oldE);
    }

    private static void shockwaves(GuiGraphics g, float x, float y, float t, float at, float from, int color, int count, float reach) {
        for (int i = 0; i < count; i++) {
            float start = at + i * 0.1f;
            float u = Anim.window(t, start, start + 0.7f);
            if (u <= 0 || u >= 1) continue;
            float e = Anim.outCubic(u);
            Shapes.ring(x, y, from + reach * e, Anim.lerp(3f, 0.6f, e)).fill(Gfx.alpha(Gfx.mix(0xFFFFFFFF, color, e), (1 - u) * (1 - u) * 0.9f))
                    .glow(6, Gfx.alpha(color, 0.5f * (1 - u))).additive().draw(g);
        }
    }

    /** A burst of glowing sparks thrown out of the seal. {@code t} counts from the stamp. */
    private static void sparks(GuiGraphics g, float x, float y, float t, int color) {
        if (t <= 0 || t > 1.2f) return;
        int count = 22;
        for (int i = 0; i < count; i++) {
            float angle = i * 2.39996f;
            float speed = 40 + (i * 37 % 23) * 2.2f;
            float life = 0.7f + (i * 13 % 7) * 0.07f;
            float u = t / life;
            if (u >= 1) continue;
            float d = speed * Anim.outCubic(u);
            float px = x + (float) Math.cos(angle) * d;
            float py = y + (float) Math.sin(angle) * d * 0.75f + 14 * u * u;
            float a = (1 - u);
            Shapes.circle(px, py, 1.1f + (i % 3) * 0.35f).fill(Gfx.alpha(0xFFFFF6DA, a)).glow(3.5f, Gfx.alpha(color, 0.8f * a)).additive().draw(g);
        }
    }

    private QuestOverlay() {}
}
