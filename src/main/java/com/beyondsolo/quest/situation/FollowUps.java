package com.beyondsolo.quest.situation;

import com.beyondsolo.quest.QuestEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consequences that come back later. A situation schedules one ({@link #schedule}) when it ends a certain way (the player
 * fled from a creature: it will hunt them down), and when it is due its handler tries to make it happen. A handler that
 * can't yet (the player is inside a gate, or already in an emergency) is asked again a minute later, for a while.
 */
public final class FollowUps {
    /** Makes a follow-up happen. Returns false when it can't happen right now and should be tried again later. */
    @FunctionalInterface
    public interface Handler {
        boolean fire(ServerPlayer player, CompoundTag data);
    }

    private static final Map<String, Handler> HANDLERS = new HashMap<>();
    private static final long RETRY = 20 * 60;
    private static final int MAX_TRIES = 40;

    public static void register(String kind, Handler handler) {
        HANDLERS.put(kind, handler);
    }

    public static SituationData data(ServerPlayer player) {
        return player.getData(com.beyondsolo.registry.ModAttachments.SITUATIONS);
    }

    /** Schedules a follow-up of {@code kind} in {@code delay} ticks. */
    public static void schedule(ServerPlayer player, String kind, long delay, CompoundTag data) {
        if (!SituationConfig.followUps()) return;
        data(player).followUps().add(new SituationData.FollowUp(kind, QuestEngine.gameTime(player) + Math.max(0, delay), data));
    }

    /** Brings every waiting follow-up of {@code kind} forward to now (testing and filming). Returns how many. */
    public static int hurry(ServerPlayer player, String kind) {
        List<SituationData.FollowUp> list = data(player).followUps();
        int n = 0;
        for (int i = 0; i < list.size(); i++) {
            SituationData.FollowUp f = list.get(i);
            if (kind != null && !f.kind().equals(kind)) continue;
            list.set(i, new SituationData.FollowUp(f.kind(), 0, f.data()));
            n++;
        }
        return n;
    }

    /** Once a second: whatever is due tries to happen. */
    static void tick(ServerPlayer player, long now) {
        List<SituationData.FollowUp> list = data(player).followUps();
        if (list.isEmpty()) return;
        for (SituationData.FollowUp f : new ArrayList<>(list)) {
            if (f.due() > now) continue;
            Handler h = HANDLERS.get(f.kind());
            list.remove(f);
            if (h == null) continue;
            if (h.fire(player, f.data())) return; // one at a time
            int tries = f.data().getInt("tries") + 1;
            if (tries >= MAX_TRIES) continue;
            f.data().putInt("tries", tries);
            list.add(new SituationData.FollowUp(f.kind(), now + RETRY, f.data()));
        }
    }

    private FollowUps() {}
}
