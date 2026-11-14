package com.beyondsolo.client.quest;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.ClientEvents;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.hud.StatusHud;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestObjective;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The quest tracker under the status capsule: the most urgent active quests with their objectives, bars that
 * fill as you progress (and flash when they move), a clock for timed quests, and the newest open offer with
 * its accept key.
 */
public final class QuestTracker {
    public static final float WIDTH = 156;
    private static final int MAX_CARDS = 3;
    private static final float X = 8;

    private static float anchorX = X + WIDTH / 2f;
    private static float anchorY = 70;
    /** Per quest: how far it has slid in. */
    private static final Map<UUID, Float> APPEAR = new HashMap<>();
    /** Per quest objective ("id#index"): the bar as drawn, the progress last seen, and when it last moved. */
    private static final Map<String, Float> BARS = new HashMap<>();
    private static final Map<String, Integer> SEEN = new HashMap<>();
    private static final Map<String, Double> FLASH = new HashMap<>();
    private static final Map<String, Double> DONE_AT = new HashMap<>();
    private static float offerShown;

    /** Where a quest flying into the tracker should land. */
    public static float anchorX() { return anchorX; }
    public static float anchorY() { return anchorY; }

    static void onSync(QuestData data) {
        double now = Anim.now();
        for (Quest q : data.active()) {
            for (int i = 0; i < q.objectives().size(); i++) {
                String key = q.id() + "#" + i;
                int p = q.objectives().get(i).progress();
                Integer old = SEEN.put(key, p);
                if (old != null && p > old) FLASH.put(key, now);
            }
        }
    }

    private static final Map<UUID, Double> READY_AT = new HashMap<>();

    static void ready(UUID quest) {
        READY_AT.put(quest, Anim.now());
    }

    /** The extra line under a quest's objectives: where to hand it in, or how far the marked spot is. Null if none. */
    private static String guideLine(Quest q) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        if (q.status() == Quest.Status.READY && q.giver() != null) {
            double d = Math.sqrt(q.giver().pos().distToCenterSqr(mc.player.position()));
            return Component.translatable("quest.beyondsolo.tracker.turn_in", q.giver().name(), Math.round(d)).getString();
        }
        net.minecraft.world.phys.Vec3 at = com.beyondsolo.client.npc.NpcMarkers.waypoint(q);
        if (at == null) return null;
        double dx = at.x - mc.player.getX(), dz = at.z - mc.player.getZ();
        return Component.translatable("quest.beyondsolo.tracker.waypoint", Math.round(Math.sqrt(dx * dx + dz * dz))).getString();
    }

    static void objectiveDone(UUID quest, int index) {
        DONE_AT.put(quest + "#" + index, Anim.now());
    }

    public static void reset() {
        APPEAR.clear();
        BARS.clear();
        SEEN.clear();
        FLASH.clear();
        DONE_AT.clear();
        READY_AT.clear();
        offerShown = 0;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientConfig.SHOW_HUD.get() || mc.getDebugOverlay().showDebugScreen()) return;
        float y = StatusHud.bottom();
        anchorX = X + WIDTH / 2f;
        anchorY = y + 14;
        List<Quest> active = ClientQuests.active();
        int cards = 0;
        for (Quest q : active) {
            if (cards >= MAX_CARDS) break;
            if (QuestOverlay.isIncoming(q.id())) continue;
            float k = APPEAR.getOrDefault(q.id(), 0f);
            k = Anim.approach(k, 1, 7f);
            APPEAR.put(q.id(), k);
            y += drawCard(g, q, X, y, k) + 5;
            cards++;
        }
        int hiddenCount = 0;
        for (Quest q : active) if (!QuestOverlay.isIncoming(q.id())) hiddenCount++;
        hiddenCount -= cards;
        if (hiddenCount > 0) {
            SysFont.text(g, Component.translatable("quest.beyondsolo.tracker.more", hiddenCount).getString(), X + 4, y, 6.5f, SystemTheme.TEXT_DIM);
            y += 10;
        }

        // the newest offer: accept key and a ring that runs down
        List<Quest> offers = ClientQuests.offers();
        offerShown = Anim.approach(offerShown, offers.isEmpty() ? 0 : 1, 9f);
        if (offerShown > 0.02f && !offers.isEmpty()) drawOffer(g, offers.get(offers.size() - 1), X, y);
    }

    private static float drawCard(GuiGraphics g, Quest q, float x, float y, float appear) {
        int color = q.category().color();
        List<QuestObjective> objectives = q.objectives();
        float rowH = 12.5f;
        String guide = guideLine(q);
        boolean ready = q.status() == Quest.Status.READY;
        float h = 17 + objectives.size() * rowH + 3 + (guide != null ? 11 : 0);
        float k = Anim.outCubic(appear);
        float old = Gfx.fade(k);
        x -= (1 - k) * 24;

        long left = ClientQuests.ticksLeft(q);
        boolean urgent = left >= 0 && left < 20 * 30;
        float urgentPulse = urgent ? Anim.pulse(0.6f) : 0;
        if (ready) {
            // waiting to be handed in: gold, breathing
            color = SystemTheme.GOLD;
            float flash = READY_AT.containsKey(q.id()) ? 1 - Anim.outCubic(Anim.progress(READY_AT.get(q.id()), 1.4f)) : 0;
            Shapes.box(x - 1, y - 1, WIDTH + 2, h + 2).radius(8).fill(0x00000000).glow(6 + 10 * flash, Gfx.alpha(color, 0.18f + 0.15f * Anim.pulse(1.4f) + 0.5f * flash)).draw(g);
        }
        Shapes.box(x + 1, y + 2, WIDTH - 2, h - 2).radius(7).fill(0x00000000).glow(7, 0x48000000).draw(g);
        Shapes.box(x, y, WIDTH, h).radius(7).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.84f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.84f))
                .border(0.75f, Gfx.alpha(urgent ? SystemTheme.DANGER : color, 0.65f + 0.3f * urgentPulse), Gfx.alpha(color, 0.18f))
                .glow(4 + 5 * urgentPulse, Gfx.alpha(urgent ? SystemTheme.DANGER : color, 0.12f + 0.3f * urgentPulse)).draw(g);
        Shapes.pill(x + 3, y + 5, 1.6f, h - 10).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.4f), color).glow(3, Gfx.alpha(color, 0.5f)).draw(g);

        // header: category icon, title, clock
        Shapes.icon(QuestText.icon(q.category()), x + 12, y + 8.5f, 8.5f).fill(Gfx.mix(color, 0xFFFFFFFF, 0.3f)).glow(2, Gfx.alpha(color, 0.6f)).draw(g);
        String clock = left >= 0 ? QuestText.clock(left) : "";
        float clockW = clock.isEmpty() ? 0 : SysFont.width(clock, 7f, true) + 6;
        SysFont.title(g, SysFont.fit(q.title().getString(), 8f, true, WIDTH - 28 - clockW), x + 19, SysFont.centerY(y + 2, 13, 8f), 8f, SystemTheme.TEXT);
        if (!clock.isEmpty()) {
            int cc = urgent ? Gfx.mix(SystemTheme.DANGER, 0xFFFFFFFF, 0.4f * urgentPulse) : SystemTheme.TEXT_SOFT;
            SysFont.draw(g, clock, x + WIDTH - 7, SysFont.centerY(y + 2, 13, 7f), 7f, cc, true, 1f);
        }

        // objectives
        float oy = y + 17;
        double now = Anim.now();
        for (int i = 0; i < objectives.size(); i++) {
            QuestObjective o = objectives.get(i);
            String key = q.id() + "#" + i;
            float target = o.fraction();
            float bar = BARS.containsKey(key) ? Anim.approach(BARS.get(key), target, 6f) : target;
            BARS.put(key, bar);
            float flash = FLASH.containsKey(key) ? 1 - Anim.outCubic(Anim.progress(FLASH.get(key), 0.9f)) : 0;
            float doneFx = DONE_AT.containsKey(key) ? 1 - Anim.outCubic(Anim.progress(DONE_AT.get(key), 1.2f)) : 0;
            if (flash > 0.01f || doneFx > 0.01f) {
                int fc = o.done() ? SystemTheme.GOOD : SystemTheme.ACCENT;
                Shapes.box(x + 7, oy - 1.5f, WIDTH - 12, rowH - 0.5f).radius(4).fill(Gfx.alpha(fc, 0.16f * Math.max(flash, doneFx)))
                        .glow(4, Gfx.alpha(fc, 0.3f * Math.max(flash, doneFx))).draw(g);
            }
            float ix = x + 12;
            if (o.done()) {
                float pop = DONE_AT.containsKey(key) ? Anim.outBackStrong(Anim.progress(DONE_AT.get(key), 0.35f)) : 1;
                Shapes.icon(Icons.CHECK, ix, oy + 3.5f, 7.5f * pop).fill(SystemTheme.GOOD).glow(2 + 4 * doneFx, Gfx.alpha(SystemTheme.GOOD, 0.6f)).draw(g);
            } else {
                Shapes.icon(QuestText.icon(o.type()), ix, oy + 3.5f, 7f).fill(Gfx.alpha(SystemTheme.TEXT_SOFT, 0.85f)).draw(g);
            }
            String count = o.progress() + "/" + o.required();
            float cw = SysFont.width(count, 6.5f, true);
            SysFont.text(g, SysFont.fit(o.label().getString(), 7f, false, WIDTH - 32 - cw), x + 19, oy, 7f, o.done() ? Gfx.alpha(SystemTheme.GOOD, 0.85f) : SystemTheme.TEXT_SOFT);
            SysFont.draw(g, count, x + WIDTH - 7, oy + 0.4f, 6.5f, o.done() ? SystemTheme.GOOD : Gfx.mix(SystemTheme.TEXT_SOFT, 0xFFFFFFFF, flash), true, 1f);
            if (!o.done()) {
                int a = q.isForced() ? SystemTheme.HP_A : SystemTheme.MP_A;
                int b = q.isForced() ? SystemTheme.HP_B : SystemTheme.MP_B;
                Ui.bar(g, x + 19, oy + 8, WIDTH - 26, 1.8f, bar, a, Gfx.mix(b, 0xFFFFFFFF, flash * 0.6f));
            }
            oy += rowH;
        }
        if (guide != null) {
            int gc = ready ? SystemTheme.GOLD : SystemTheme.ACCENT_BRIGHT;
            Shapes.icon(ready ? Icons.HANDSHAKE : Icons.PIN, x + 12, oy + 3.5f, 7.5f).fill(gc).glow(ready ? 2 + 2 * Anim.pulse(1.2f) : 1, Gfx.alpha(gc, 0.6f)).draw(g);
            SysFont.text(g, SysFont.fit(guide, 6.8f, true, WIDTH - 26), x + 19, oy, 6.8f, Gfx.mix(gc, 0xFFFFFFFF, 0.25f));
        }
        Gfx.setAlpha(old);
        return h * k + (1 - k) * 0;
    }

    private static void drawOffer(GuiGraphics g, Quest q, float x, float y) {
        float k = Anim.outCubic(offerShown);
        float old = Gfx.fade(k);
        x -= (1 - k) * 20;
        float h = 22;
        float pulse = Anim.pulse(1.1f);
        int gold = SystemTheme.GOLD;
        Shapes.box(x, y, WIDTH, h).radius(h / 2f).fillV(Gfx.mix(SystemTheme.GLASS_TOP, Gfx.alpha(gold, 0.9f), 0.12f), SystemTheme.GLASS_BOTTOM)
                .border(0.9f, Gfx.alpha(gold, 0.75f + 0.25f * pulse), Gfx.alpha(gold, 0.3f)).glow(4 + 5 * pulse, Gfx.alpha(gold, 0.18f + 0.22f * pulse)).draw(g);
        // countdown ring around the icon
        long left = ClientQuests.ticksLeft(q);
        float frac = left < 0 ? 1 : Math.min(1f, left / (float) com.beyondsolo.quest.QuestEngine.OFFER_TICKS);
        float cx = x + 11;
        float cy = y + h / 2f;
        Shapes.ring(cx, cy, 7.5f, 1.2f).fill(0x30FFFFFF).draw(g);
        Shapes.ring(cx, cy, 7.5f, 1.2f).fill(gold).progress(frac).glow(2, Gfx.alpha(gold, 0.5f)).draw(g);
        Shapes.icon(QuestText.icon(q.category()), cx, cy, 8).fill(0xFFFFF0C8).draw(g);
        String key = ClientEvents.ACCEPT_QUEST.getTranslatedKeyMessage().getString();
        String accept = Component.translatable("quest.beyondsolo.tracker.accept").getString();
        float kw = key.length() <= 6 ? Ui.keycapWidth(10, key) : 0;
        float aw = SysFont.width(accept, 6.5f, true);
        SysFont.spaced(g, Component.translatable("quest.beyondsolo.tracker.offer").getString(), x + 22, y + 3.5f, 5.8f, gold, true, 1.2f, 0f);
        SysFont.title(g, SysFont.fit(q.title().getString(), 7.5f, true, WIDTH - 34 - kw - aw), x + 22, y + 11, 7.5f, SystemTheme.TEXT);
        if (kw > 0) {
            float kx = x + WIDTH - 8 - aw - 3 - kw;
            Ui.keycap(g, kx, y + 6, 10, key, gold);
            SysFont.draw(g, accept, kx + kw + 3, SysFont.centerY(y + 6, 10, 6.5f), 6.5f, Gfx.mix(gold, 0xFFFFFFFF, 0.3f), true, 0f);
        }
        Gfx.setAlpha(old);
    }

    private QuestTracker() {}
}
