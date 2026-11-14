package com.beyondsolo.notify;

import com.beyondsolo.network.NotificationPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side helper for sending System pop-ups. */
public final class Notifications {
    /** Sends a pop-up. Players the System has not bound to yet get nothing (they can't see the System). */
    public static void send(ServerPlayer player, SystemNotification notification) {
        if (!com.beyondsolo.system.SystemAPI.hasSystem(player)) return;
        PacketDistributor.sendToPlayer(player, new NotificationPayload(notification));
    }

    private Notifications() {}
}
