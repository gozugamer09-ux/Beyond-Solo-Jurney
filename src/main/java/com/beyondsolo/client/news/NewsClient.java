package com.beyondsolo.client.news;

import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.news.Headline;
import com.beyondsolo.network.NewsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The client's copy of the hunter news: every headline kept (the Chronicle's NEWS page reads them) and the ones still
 * to run across the ticker.
 */
public final class NewsClient {
    private static final List<Headline> HEADLINES = new ArrayList<>();
    private static final Deque<Headline> TICKER = new ArrayDeque<>();
    private static final Map<Long, Component> TEXTS = new HashMap<>();
    /** The newest headline the hunter has seen on the NEWS page. */
    private static long seen = -1;
    private static double lastAt = -100;

    public static void onNews(NewsPayload p) {
        CompoundTag t = p.data();
        if (t.getBoolean("reset")) {
            HEADLINES.clear();
            TICKER.clear();
            TEXTS.clear();
        }
        boolean flash = t.getBoolean("flash");
        for (Tag e : t.getList("headlines", Tag.TAG_COMPOUND)) {
            Headline h = Headline.load((CompoundTag) e);
            HEADLINES.removeIf(x -> x.id() == h.id());
            HEADLINES.add(h);
            if (flash) {
                TICKER.addLast(h);
                lastAt = Anim.now();
            }
        }
        while (HEADLINES.size() > 500) HEADLINES.remove(0);
        // the first sync after joining: what was there before counts as seen
        if (t.getBoolean("reset") && seen < 0) seen = newest();
    }

    /** The headlines, oldest first. */
    public static List<Headline> headlines() {
        return Collections.unmodifiableList(HEADLINES);
    }

    /** The next headline for the ticker, taken off its queue (null: none waiting). */
    public static Headline nextForTicker() {
        return TICKER.pollFirst();
    }

    public static boolean tickerWaiting() {
        return !TICKER.isEmpty();
    }

    /** A headline's text in the reader's language. */
    public static Component text(Headline h) {
        return TEXTS.computeIfAbsent(h.id(), id -> {
            Minecraft mc = Minecraft.getInstance();
            try {
                Component c = mc.level == null ? null : Component.Serializer.fromJson(h.text(), mc.level.registryAccess());
                return c == null ? Component.literal(h.text()) : c;
            } catch (Exception e) {
                return Component.literal(h.text());
            }
        });
    }

    public static long newest() {
        return HEADLINES.isEmpty() ? 0 : HEADLINES.get(HEADLINES.size() - 1).id();
    }

    /** Headlines the hunter has not yet seen on the page. */
    public static int unread() {
        int n = 0;
        for (Headline h : HEADLINES) if (h.id() > seen) n++;
        return n;
    }

    public static void markSeen() {
        seen = Math.max(seen, newest());
    }

    /** Seconds since the last headline came in. */
    public static float sinceLast() {
        return Anim.since(lastAt);
    }

    public static void reset() {
        HEADLINES.clear();
        TICKER.clear();
        TEXTS.clear();
        seen = -1;
        lastAt = -100;
    }

    private NewsClient() {}
}
