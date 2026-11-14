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
        public final SystemNotification notification;
        public final double shownAt;
        /** True if it was brought back from the Log with the hide key. */
        public final boolean recalled;
        /** Stack position, animated by the renderer. */
        public float y = Float.NaN;
        private double hideAt = -1;

        Popup(SystemNotification notification, boolean recalled) {
            this.notification = notification;
            this.shownAt = Anim.now();
            this.recalled = recalled;
        }

        public float duration() {
            float seconds = notification.durationTicks() / 20f;
            return recalled ? Math.max(seconds, 6f) : seconds;
        }

        public float age() {
            return Anim.since(shownAt);
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

    public static void push(SystemNotification notification) {
        HISTORY.addFirst(new Entry(notification, System.currentTimeMillis()));
        while (HISTORY.size() > HISTORY_SIZE) HISTORY.removeLast();
        if (ClientConfig.NOTIFICATION_MODE.get() == ClientConfig.NotificationMode.HIDDEN) {
            unread++;
            playSound(notification, 0.5f);
        } else {
            WAITING.addLast(notification);
        }
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
