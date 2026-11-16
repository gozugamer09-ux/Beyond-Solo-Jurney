package com.beyondsolo.client.key;

import com.beyondsolo.client.ClientEvents;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.key.DungeonKeys;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The keyed run on screen. At the top, under the boss bar when there is one: the run clock, with the run's rank and
 * its place in a chain; once the boss falls it stops and turns gold, with the best time for the rank or NEW BEST.
 * Above the hotbar, in a cleared cave when the player carries a key of its rank: the re-run prompt, which turns that
 * key for the next run right where they stand (the accept key, J by default).
 */
public final class RunHud {
    private static float clockIn;
    private static float promptIn;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        clockIn = Anim.approach(clockIn, KeyClient.inRun() ? 1f : 0f, 4f);
        if (clockIn > 0.01f) clock(g, w, delta.getGameTimeDeltaPartialTick(false));
        // the prompt comes with the cleared banner and waits for it to finish
        boolean prompt = KeyClient.rerunShowing() && KeyClient.sinceRerun() > 0.6f && !GateOverlay.active();
        promptIn = Anim.approach(promptIn, prompt ? 1f : 0f, prompt ? 3f : 6f);
        if (promptIn > 0.01f) prompt(g, w, h);
    }

    /** The run clock at the top of the screen. */
    private static void clock(GuiGraphics g, int w, float partial) {
        PlayerRank rank = KeyClient.runRank();
        boolean done = KeyClient.cleared();
        int rc = SystemTheme.rank(rank);
        int c = done ? SystemTheme.GOLD : rc;
        float in = Anim.outCubic(clockIn);
        float y = 6 + 34 * Anim.outCubic(GateOverlay.bossBarIn()) - 16 * (1 - in);

        String label = Component.translatable("key.beyondsolo.hud.run", rank.displayName()).getString();
        String time = KeyClient.time(KeyClient.runTicks(partial));
        String chain = KeyClient.runChain() > 1 ? Component.translatable("key.beyondsolo.hud.chain", KeyClient.runChain()).getString() : null;
        String tail = null;
        if (done) {
            tail = KeyClient.newBest() ? Component.translatable("key.beyondsolo.hud.new_best").getString()
                    : KeyClient.best() >= 0 ? Component.translatable("key.beyondsolo.hud.best", KeyClient.time(KeyClient.best())).getString() : null;
        }
        float labelW = SysFont.spacedWidth(label, 5.6f, true, 1.2f);
        float timeW = SysFont.width("0:00.0".length() < time.length() ? time : "0:00.0", 9f, true);
        float chainW = chain == null ? 0 : SysFont.width(chain, 5.4f, true) + 9;
        float tailW = tail == null ? 0 : SysFont.width(tail, 5.4f, true) + 9;
        float bw = 22 + Math.max(labelW, chainW + tailW + (chainW > 0 && tailW > 0 ? 3 : 0)) + 10 + timeW + 10;
        float bh = 22;
        float x = (w - bw) / 2f;
        float old = Gfx.fade(in);
        float flash = done ? 1 - Anim.clamp01(KeyClient.sinceCleared() / 1.2f) : 0;
        Shapes.box(x, y, bw, bh).radius(bh / 2f).fillV(0xC00A1222, 0xC0050912)
                .border(0.75f, Gfx.alpha(c, 0.75f + 0.25f * flash), Gfx.alpha(c, 0.3f))
                .glow(5 + 8 * flash, Gfx.alpha(c, 0.22f + 0.45f * flash)).draw(g);
        // the key's emblem: a keyhole in a ring of the rank's colour
        float ix = x + 11, iy = y + bh / 2f;
        Shapes.circle(ix, iy, 7).fillV(Gfx.alpha(rc, 0.3f), Gfx.alpha(rc, 0.1f)).border(0.6f, Gfx.alpha(rc, 0.8f)).draw(g);
        Shapes.ring(ix, iy, 8.6f, 0.5f).fill(Gfx.alpha(rc, 0.6f)).dashes(10, 0.5f).rotation(done ? 0 : Anim.loop(6f)).draw(g);
        Shapes.icon(Icons.KEY, ix, iy, 9).fill(Gfx.mix(rc, 0xFFFFFFFF, 0.55f)).glow(2, Gfx.alpha(rc, 0.6f)).draw(g);
        // label, chain and the best line
        float tx = x + 22;
        SysFont.spaced(g, label, tx, y + 4, 5.6f, Gfx.mix(rc, 0xFFFFFFFF, 0.45f), true, 1.2f, 0f);
        float cx = tx;
        if (chain != null) {
            cx += Ui.chip(g, cx, y + 11.5f, 7.5f, chain, 5.0f, SystemTheme.ACCENT, true) + 3;
        }
        if (tail != null) {
            int tc = KeyClient.newBest() ? SystemTheme.GOLD : SystemTheme.TEXT_SOFT;
            float pop = KeyClient.newBest() ? Anim.outBack(Anim.clamp01((KeyClient.sinceCleared() - 0.3f) / 0.4f)) : 1;
            if (pop > 0.01f) {
                g.pose().pushPose();
                g.pose().translate(cx, y + 15.25f, 0);
                g.pose().scale(pop, pop, 1);
                float pulse = KeyClient.newBest() ? 0.5f + 0.5f * Anim.pulse(1.4f) : 0;
                float tw = Ui.chip(g, 0, -3.75f, 7.5f, tail, 5.0f, tc, KeyClient.newBest());
                if (pulse > 0) Shapes.pill(0, -3.75f, tw, 7.5f).fill(0).glow(3 + 3 * pulse, Gfx.alpha(SystemTheme.GOLD, 0.35f * pulse)).draw(g);
                g.pose().popPose();
            }
        }
        if (chain == null && tail == null) {
            SysFont.draw(g, SysFont.fit(KeyClient.dungeon().getString(), 5.2f, false, labelW + 10), tx, y + 12.5f, 5.2f, SystemTheme.TEXT_DIM, false, 0f);
        }
        // the time
        float timeX = x + bw - 10;
        Shapes.box(timeX - timeW - 6, y + 4, 0.75f, bh - 8).fill(Gfx.alpha(c, 0.35f)).draw(g);
        int timeColor = done ? 0xFFFFE6A8 : SystemTheme.TEXT;
        SysFont.draw(g, time, timeX, SysFont.centerY(y, bh, 9f), 9f, timeColor, true, 1f);
        Gfx.setAlpha(old);
    }

    /** The re-run prompt above the hotbar. */
    private static void prompt(GuiGraphics g, int w, int h) {
        PlayerRank rank = KeyClient.rerunRank();
        int style = KeyClient.rerunStyle();
        int c = 0xFF000000 | DungeonKeys.color(rank, style);
        float in = Anim.outBack(promptIn);
        String keyName = ClientEvents.ACCEPT_QUEST.getTranslatedKeyMessage().getString().toUpperCase();
        String head = Component.translatable("key.beyondsolo.hud.rerun").getString();
        String line = Component.translatable("key.beyondsolo.hud.rerun_key", KeyClient.rerunKey()).getString();
        int chain = KeyClient.rerunChain();
        String bonus = chain > 1 ? Component.translatable("key.beyondsolo.hud.rerun_chain", chain,
                Math.round(DungeonKeys.bonusFor(chain) * 100 - 100)).getString() : null;
        float capW = Ui.keycapWidth(12, keyName);
        float headW = SysFont.spacedWidth(head, 6.4f, true, 1.6f);
        float lineW = SysFont.width(line, 6f, false);
        float bonusW = bonus == null ? 0 : SysFont.width(bonus, 5.2f, true) + 9;
        float bw = 10 + capW + 8 + 20 + Math.max(headW + (bonus == null ? 0 : bonusW + 6), lineW) + 12;
        float bh = 27;
        float x = (w - bw) / 2f;
        float y = h - 104 + 10 * (1 - Anim.outCubic(promptIn));
        float old = Gfx.fade(Math.min(1f, promptIn));
        g.pose().pushPose();
        g.pose().translate(w / 2f, y + bh / 2f, 0);
        g.pose().scale(0.9f + 0.1f * in, 0.9f + 0.1f * in, 1);
        g.pose().translate(-w / 2f, -(y + bh / 2f), 0);
        float pulse = Anim.pulse(1.8f);
        Shapes.box(x, y, bw, bh).radius(9).fillV(0xD00C1A33, 0xD0060E1E)
                .border(0.8f, Gfx.alpha(c, 0.7f + 0.3f * pulse), Gfx.alpha(c, 0.3f))
                .glow(5 + 3 * pulse, Gfx.alpha(c, 0.22f + 0.15f * pulse)).sheen(Anim.loop(3.4f) * 1.6f - 0.3f, 0.12f).draw(g);
        float cx = x + 10;
        Ui.keycap(g, cx, y + (bh - 12) / 2f, 12, keyName, c);
        cx += capW + 8;
        // the key itself
        ItemStack icon = style == 2 ? DungeonKeys.sealed(rank, null, true) : style == 1 ? DungeonKeys.sealed(rank, BossKind.values()[0], false) : DungeonKeys.key(rank);
        Shapes.circle(cx + 8, y + bh / 2f, 9).fill(0).glow(6, Gfx.alpha(c, 0.35f + 0.2f * pulse)).draw(g);
        g.renderItem(icon, (int) cx, (int) (y + bh / 2f - 8));
        cx += 20;
        SysFont.spaced(g, head, cx, y + 5, 6.4f, Gfx.mix(c, 0xFFFFFFFF, 0.55f), true, 1.6f, 0f);
        if (bonus != null) Ui.chip(g, cx + headW + 6, y + 3.5f, 8, bonus, 5.2f, SystemTheme.GOOD, true);
        SysFont.draw(g, line, cx, y + 15.5f, 6f, SystemTheme.TEXT_SOFT, false, 0f);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    /** True once the re-run prompt is up on screen (the accept key turns its key then). */
    static boolean promptShown() {
        return promptIn > 0.3f;
    }

    static void reset() {
        clockIn = 0;
        promptIn = 0;
    }

    private RunHud() {}
}
