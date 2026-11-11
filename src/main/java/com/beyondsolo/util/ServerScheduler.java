package com.beyondsolo.util;

import com.beyondsolo.BeyondSolo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Runs code a number of server ticks from now. Tasks are dropped when the server stops. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ServerScheduler {
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    private static final class Task {
        int ticksLeft;
        final Runnable action;

        Task(int ticksLeft, Runnable action) {
            this.ticksLeft = ticksLeft;
            this.action = action;
        }
    }

    public static void schedule(int ticks, Runnable action) {
        synchronized (PENDING) {
            PENDING.add(new Task(Math.max(0, ticks), action));
        }
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        synchronized (PENDING) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (--t.ticksLeft <= 0) {
                it.remove();
                try {
                    t.action.run();
                } catch (Exception e) {
                    BeyondSolo.LOGGER.error("Scheduled task failed", e);
                }
            }
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        TASKS.clear();
        synchronized (PENDING) {
            PENDING.clear();
        }
    }

    private ServerScheduler() {}
}
