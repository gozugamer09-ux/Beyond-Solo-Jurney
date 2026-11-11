package com.beyondsolo.client.notify;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.notify.SystemNotification;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Client queue of System pop-ups plus a history log for the System window. */
public final class NotificationManager {
    public static final int MAX_VISIBLE = 4;
    public static final int HISTORY_SIZE = 50;
    /** Ticks for the open/close animation. */
    public static final int FADE_TICKS = 8;

    public static final class Active {
        public final SystemNotification notification;
        public final long startTick;

        Active(SystemNotification notification, long startTick) {
            this.notification = notification;
            this.startTick = startTick;
        }

        public long endTick() {
            return startTick + notification.durationTicks();
        }

        /** 0..1 open amount, for fade/slide animations. */
        public float openAmount(long now, float partialTick) {
            float t = (now - startTick) + partialTick;
            float end = notification.durationTicks() - t;
            return Math.max(0, Math.min(1, Math.min(t, end) / FADE_TICKS));
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final Deque<SystemNotification> WAITING = new ArrayDeque<>();
    private static final Deque<SystemNotification> HISTORY = new ArrayDeque<>();
    private static int unread;

    public static void push(SystemNotification notification) {
        HISTORY.addFirst(notification);
        while (HISTORY.size() > HISTORY_SIZE) HISTORY.removeLast();
        if (ClientConfig.NOTIFICATION_MODE.get() == ClientConfig.NotificationMode.HIDDEN) {
            unread++;
        } else {
            WAITING.addLast(notification);
        }
        if (ClientConfig.NOTIFICATION_SOUNDS.get()) {
            Minecraft mc = Minecraft.getInstance();
            if (notification.kind() == SystemNotification.Kind.ALERT) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 0.6f, 0.8f));
            } else {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 1.0f));
            }
        }
    }

    /** Called every client tick. */
    public static void tick(long gameTick) {
        ACTIVE.removeIf(a -> gameTick >= a.endTick() || gameTick < a.startTick);
        while (ACTIVE.size() < MAX_VISIBLE && !WAITING.isEmpty()) {
            ACTIVE.add(new Active(WAITING.pollFirst(), gameTick));
        }
    }

    public static List<Active> active() {
        return ACTIVE;
    }

    public static List<SystemNotification> history() {
        return List.copyOf(HISTORY);
    }

    public static int unread() {
        return unread;
    }

    public static void markAllRead() {
        unread = 0;
    }

    /** Close everything on screen (keeps the log). */
    public static void dismissAll() {
        ACTIVE.clear();
        WAITING.clear();
    }

    public static void clearAll() {
        dismissAll();
        HISTORY.clear();
        unread = 0;
    }

    private NotificationManager() {}
}
