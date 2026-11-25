package com.beyondsolo.client.news;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.hud.HudSpace;
import com.beyondsolo.client.notify.NotificationRenderer;
import com.beyondsolo.news.Headline;
import com.beyondsolo.news.HunterNews;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

/**
 * The news ticker: a headline as it comes in runs in a strip at the top of the screen, under the bars there, like a
 * broadcast's: a tag on the left (HUNTER NEWS, or a red BREAKING with a chime) and the headline beside it, crawling
 * along when it is longer than the strip. One at a time; the rest wait their turn. Everything that ran stays on the
 * Chronicle's NEWS page.
 */
public final class NewsTicker {
    /** The broadcast's red (breaking news). */
    public static final int RED = 0xFFFF3B4A;
    private static final float H = 14;
    /** Crawl speed (GUI pixels a second) and the pauses before and after the crawl. */
    private static final float SPEED = 34, HOLD = 1.3f;
    private static final float IN = 0.35f, OUT = 0.35f;

    private static Headline showing;
    private static double shownAt;
    private static float duration;
    /** The room the ticker took last frame (it keeps clear of the other bars at the top, not of itself). */
    private static float[] claim;

    /** The mark of a kind of headline. */
    public static ResourceLocation icon(String kind) {
        return switch (kind) {
            case HunterNews.RANK -> Icons.MEDAL;
            case HunterNews.GUILD -> Icons.HANDSHAKE;
            case HunterNews.RACE -> Icons.RACE;
            case HunterNews.BREAK -> Icons.SKULL;
            case HunterNews.BOSS -> Icons.CROWN;
            case HunterNews.DUEL -> Icons.DUEL;
            default -> Icons.CITY;
        };
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ClientSystemData.get().hasSystem()) return;
        boolean hidden = mc.options.hideGui || !ClientConfig.SHOW_HUD.get();
        if (showing == null || Anim.since(shownAt) > duration) {
            Headline next = NewsClient.nextForTicker();
            if (next != null) {
                showing = next;
                shownAt = Anim.now();
                duration = -1;
                if (!hidden) mc.getSoundManager().play(SimpleSoundInstance.forUI(next.breaking() ? SoundEvents.NOTE_BLOCK_BELL.value()
                        : SoundEvents.NOTE_BLOCK_CHIME.value(), next.breaking() ? 0.85f : 1.6f, next.breaking() ? 0.7f : 0.3f));
            } else {
                showing = null;
            }
        }
        if (showing == null || hidden) return;
        draw(g, mc, g.guiWidth(), showing, Anim.since(shownAt));
    }

    private static void draw(GuiGraphics g, Minecraft mc, int w, Headline hl, float since) {
        boolean breaking = hl.breaking();
        int c = breaking ? RED : hl.accent() | 0xFF000000;
        String tag = Component.translatable(breaking ? "news.beyondsolo.ticker.breaking" : "news.beyondsolo.ticker.tag").getString().toUpperCase(Locale.ROOT);
        String text = NewsClient.text(hl).getString();
        float tagSize = 5f, textSize = 5.6f;
        float tagW = SysFont.spacedWidth(tag, tagSize, true, 1.2f) + 20;
        float textW = SysFont.width(text, textSize, false);
        float maxW = Math.min(w - 24, Math.max(260, w * 0.5f));
        float boxW = Math.min(maxW, tagW + textW + 18);
        float x = Math.round((w - boxW) / 2f);
        float y = Math.max(4, HudSpace.topClaimedLately(x, x + boxW, claim) + 4);
        // pushed down beside the pop-ups floating at the right side: narrower, in the room between them and our left
        // column (on a screen too small for that, it runs over them for its few seconds)
        float popLeft = NotificationRenderer.sideLeft() - 6, band = popLeft - HudSpace.leftColumn();
        if (NotificationRenderer.sideBottom() > 0 && y < NotificationRenderer.sideBottom() && y + H > NotificationRenderer.sideTop()
                && x + boxW > popLeft && band >= 150) {
            boxW = Math.min(boxW, band);
            x = Math.round(Math.min(x, popLeft - boxW));
            y = Math.max(4, HudSpace.topClaimedLately(x, x + boxW, claim) + 4);
        }
        float room = boxW - tagW - 12;
        float crawl = Math.max(0, textW - room);
        float needs = IN + (crawl > 0 ? HOLD * 2 + crawl / SPEED : breaking ? 8f : 6.5f) + OUT;
        // (a strip made narrower while it runs crawls further: it stays up until it is done)
        if (duration < 0 || needs > duration) duration = needs;
        float in = Anim.outCubic(Anim.clamp01(since / IN));
        float out = Anim.clamp01((duration - since) / OUT);
        float k = Math.min(in, out);
        if (k <= 0.01f) return;

        y -= (1 - k) * 8;
        claim = HudSpace.claimTop(x, y, boxW, H);
        float old = Gfx.fade(k);
        float pulse = Anim.pulse(breaking ? 0.8f : 2.4f);
        float flash = 1 - Anim.clamp01(since / 1.2f);
        Shapes.box(x, y, boxW, H).radius(4).fillV(0xE60B1220, 0xE6060A12)
                .border(0.8f, Gfx.alpha(c, 0.55f + 0.3f * pulse * (breaking ? 1 : 0.4f) + 0.15f * flash), Gfx.alpha(c, 0.2f))
                .glow(3 + 6 * flash + (breaking ? 2 * pulse : 0), Gfx.alpha(c, 0.2f + 0.3f * flash)).draw(g);
        // the tag: a solid block of colour, the kind's mark and the word
        Shapes.box(x, y, tagW, H).radius(4).fillV(Gfx.alpha(c, breaking ? 0.95f : 0.55f), Gfx.alpha(Gfx.shade(c, -0.35f), breaking ? 0.95f : 0.45f)).draw(g);
        Shapes.icon(icon(hl.kind()), x + 8, y + H / 2f, 7).fill(0xFFFFFFFF).draw(g);
        SysFont.spaced(g, tag, x + 14, SysFont.centerY(y, H, tagSize), tagSize, 0xFFFFFFFF, true, 1.2f, 0f);
        if (breaking) {
            // the live dot
            Shapes.circle(x + tagW - 4, y + 3.2f, 1.3f).fill(Gfx.alpha(0xFFFFFFFF, 0.4f + 0.6f * pulse)).draw(g);
        }
        // the headline, crawling when it runs long
        float tx = x + tagW + 6;
        float shift = 0;
        if (crawl > 0) shift = Math.min(crawl, Math.max(0, since - IN - HOLD) * SPEED);
        Shapes.flush(g);
        g.enableScissor((int) Math.floor(tx), (int) Math.floor(y), (int) Math.ceil(tx + room + 2), (int) Math.ceil(y + H));
        SysFont.draw(g, text, tx - shift, SysFont.centerY(y, H, textSize), textSize, SystemTheme.TEXT, false, 0f);
        Shapes.flush(g);
        g.disableScissor();
        // the fades at the strip's ends while it crawls
        if (crawl > 0 && shift > 0) Shapes.box(tx, y + 1, 6, H - 2).fillH(0xE60B1220, 0x000B1220).draw(g);
        if (crawl > 0 && shift < crawl) Shapes.box(tx + room - 4, y + 1, 6, H - 2).fillH(0x000B1220, 0xE60B1220).draw(g);
        Gfx.setAlpha(old);
    }

    /** Forgets what is showing (logout). */
    public static void reset() {
        showing = null;
        duration = 0;
        claim = null;
    }

    private NewsTicker() {}
}
