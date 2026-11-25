package com.beyondsolo.client.notify;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.notify.SystemNotification;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** Client state of the System pop-ups: what is on screen, what is waiting, and the history for the Log page. */
public final class NotificationManager {
    public static final int MAX_VISIBLE = 4;
    public static final int HISTORY_SIZE = 50;
    /** How many messages the hide key brings back when nothing is on screen. */
    public static final int RECALL_COUNT = 3;
    public static final float APPEAR_SECONDS = 0.38f;
    public static final float EXIT_SECONDS = 0.24f;

    /** A message in the Log. */
    public record Entry(SystemNotification notification, long receivedAt) {}

    /** A pop-up on screen. */
    public static final class Popup {
        /** What it says: the latest of the same message, if it came again while showing ({@link #repeats}). */
        public SystemNotification notification;
        public final double shownAt;
        /** True if it was brought back from the Log with the hide key. */
        public final boolean recalled;
        /** Stack position, animated by the renderer. */
        public float y = Float.NaN;
        private double hideAt = -1;
        /** How many times the same message came while this pop-up showed it (1: once). */
        public int repeats = 1;
        /** When it last came again (its time on screen starts over; the panel flashes once more). */
        public double repeatedAt;

        Popup(SystemNotification notification, boolean recalled) {
            this.notification = notification;
            this.shownAt = Anim.now();
            this.repeatedAt = shownAt;
            this.recalled = recalled;
        }

        public float duration() {
            float seconds = notification.durationTicks() / 20f;
            return recalled ? Math.max(seconds, 6f) : seconds;
        }

        /** How long it has been showing what it says now (since it last came again). */
        public float age() {
            return Anim.since(repeatedAt);
        }

        /** 0..1 while the pop-up animates in. */
        public float appear() {
            return Anim.progress(shownAt, APPEAR_SECONDS);
        }

        /** 0 while shown, rises to 1 while the pop-up animates out. */
        public float exit() {
            return hideAt < 0 ? 0 : Anim.progress(hideAt, EXIT_SECONDS);
        }

        public boolean hiding() {
            return hideAt >= 0;
        }

        /** 1 when just shown, 0 when its time is up. */
        public float remaining() {
            return 1f - Math.min(1f, age() / duration());
        }

        void hide() {
            if (hideAt < 0) hideAt = Anim.now();
        }
    }

    private static final List<Popup> ACTIVE = new ArrayList<>();
    private static final Deque<SystemNotification> WAITING = new ArrayDeque<>();
    private static final Deque<Entry> HISTORY = new ArrayDeque<>();
    private static int unread;
    private static boolean suppressed;
    /** While true (a quest moment in the middle of the screen), new pop-ups wait; the ones showing stay. */
    private static boolean held;

    public static void setHeld(boolean value) {
        held = value;
    }

    /** Sends off the pop-ups showing now (a moment that needs the whole screen, like a red gate's warning). */
    public static void hideShowing() {
        for (Popup p : ACTIVE) if (!p.hiding()) p.hide();
    }

    public static void push(SystemNotification notification) {
        // the same message again a moment later (a key held down, a thing tried twice) is one message, not a stack:
        // one line in the Log, and on screen one pop-up that counts the repeats
        Entry latest = HISTORY.peekFirst();
        boolean again = latest != null && sameWords(latest.notification(), notification)
                && System.currentTimeMillis() - latest.receivedAt() < REPEAT_WINDOW_MS;
        if (again) HISTORY.pollFirst();
        HISTORY.addFirst(new Entry(notification, System.currentTimeMillis()));
        while (HISTORY.size() > HISTORY_SIZE) HISTORY.removeLast();
        if (ClientConfig.NOTIFICATION_MODE.get() == ClientConfig.NotificationMode.HIDDEN) {
            if (!again) {
                unread++;
                playSound(notification, 0.5f);
            }
            return;
        }
        for (Popup p : ACTIVE) {
            if (!p.hiding() && !p.recalled && sameAs(p.notification, notification)) {
                p.notification = notification;
                p.repeats++;
                p.repeatedAt = Anim.now();
                return;
            }
        }
        for (Iterator<SystemNotification> it = WAITING.iterator(); it.hasNext(); ) {
            if (sameAs(it.next(), notification)) it.remove();
        }
        WAITING.addLast(notification);
    }

    /** How soon (ms) the same message again counts as a repeat of the last one in the Log. */
    private static final long REPEAT_WINDOW_MS = 10_000;

    /**
     * Whether two messages are the same one: the same kind and words; a warning, by its title alone (what is wrong,
     * however its details changed, like the seconds left).
     */
    static boolean sameAs(SystemNotification a, SystemNotification b) {
        if (a.kind() != b.kind() || !a.title().getString().equals(b.title().getString())) return false;
        return a.kind() == SystemNotification.Kind.ALERT || a.body().getString().equals(b.body().getString());
    }

    /** Whether two messages say exactly the same (the Log keeps every message that says something new). */
    static boolean sameWords(SystemNotification a, SystemNotification b) {
        return a.kind() == b.kind() && a.title().getString().equals(b.title().getString()) && a.body().getString().equals(b.body().getString());
    }

    /** Adds a message to the Log without a pop-up (for moments another overlay already shows, like quest results). */
    public static void log(SystemNotification notification) {
        HISTORY.addFirst(new Entry(notification, System.currentTimeMillis()));
        while (HISTORY.size() > HISTORY_SIZE) HISTORY.removeLast();
    }

    /** Runs once per rendered frame: retires old pop-ups and brings in waiting ones. */
    public static void frame() {
        boolean keep = ClientConfig.KEEP_POPUPS.get();
        int shown = 0;
        for (Iterator<Popup> it = ACTIVE.iterator(); it.hasNext(); ) {
            Popup p = it.next();
            if (!p.hiding() && (suppressed || (!keep && p.age() >= p.duration()))) p.hide();
            if (p.exit() >= 1f) {
                it.remove();
            } else if (!p.hiding()) {
                shown++;
            }
        }
        if (suppressed) return;
        // new pop-ups wait while a quest moment holds the middle of the screen, while a dungeon break's, a great
        // threat's, a monarch's authority's or the Halcyon raid's banner is up (they run across the top, where they
        // would cover it), or while the Citadel or a raid has the screen (its cards; a raid's banners, cataclysm and
        // results board)
        if (held || com.beyondsolo.client.breaks.BreakClient.bannerPending() || com.beyondsolo.client.worldboss.WorldBossClient.bannerPending()
                || com.beyondsolo.client.monarch.MonarchHud.bannerUp() || com.beyondsolo.client.isle.IsleClient.bannerPending()
                || com.beyondsolo.client.tower.TowerClient.holdsScreen() || com.beyondsolo.client.raid.RaidClient.holdsScreen()
                || com.beyondsolo.client.duel.DuelHud.holdsScreen()) return;
        // With "stay open" on, a full stack makes room by retiring its oldest pop-up.
        if (keep && !WAITING.isEmpty() && shown >= MAX_VISIBLE) {
            for (Popup p : ACTIVE) {
                if (!p.hiding()) {
                    p.hide();
                    shown--;
                    break;
                }
            }
        }
        while (shown < MAX_VISIBLE && !WAITING.isEmpty()) {
            SystemNotification next = WAITING.pollFirst();
            ACTIVE.add(new Popup(next, false));
            playSound(next, 1f);
            shown++;
        }
    }

    private static void playSound(SystemNotification notification, float volume) {
        if (!ClientConfig.NOTIFICATION_SOUNDS.get()) return;
        Minecraft mc = Minecraft.getInstance();
        switch (notification.kind()) {
            case ALERT -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 0.6f, 0.8f * volume));
            case QUEST -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, volume));
            case REWARD, LEVEL_UP -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.8f, volume));
            default -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, volume));
        }
    }

    /**
     * The hide key. If pop-ups are on screen (or waiting), hides them all. If none are, brings the latest
     * few back from the Log. Returns true if pop-ups are showing afterwards.
     */
    public static boolean toggleHidden() {
        boolean anyShown = !WAITING.isEmpty();
        for (Popup p : ACTIVE) anyShown |= !p.hiding();
        Minecraft mc = Minecraft.getInstance();
        if (anyShown) {
            for (Popup p : ACTIVE) p.hide();
            WAITING.clear();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, 0.25f));
            return false;
        }
        if (HISTORY.isEmpty() || suppressed) return false;
        List<SystemNotification> latest = new ArrayList<>();
        for (Entry e : HISTORY) {
            if (latest.size() >= RECALL_COUNT) break;
            latest.add(0, e.notification());
        }
        for (SystemNotification n : latest) ACTIVE.add(new Popup(n, true));
        unread = 0;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.25f));
        return true;
    }

    /** While true (the evaluation reveal), pop-ups wait instead of covering the screen. */
    public static void setSuppressed(boolean value) {
        suppressed = value;
    }

    public static List<Popup> active() {
        return ACTIVE;
    }

    public static List<Entry> history() {
        return List.copyOf(HISTORY);
    }

    public static int unread() {
        return unread;
    }

    public static void markAllRead() {
        unread = 0;
    }

    public static void clearAll() {
        ACTIVE.clear();
        WAITING.clear();
        HISTORY.clear();
        unread = 0;
        suppressed = false;
    }

    private NotificationManager() {}
}
