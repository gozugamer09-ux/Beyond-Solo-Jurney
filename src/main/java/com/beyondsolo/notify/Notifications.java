package com.beyondsolo.notify;

import com.beyondsolo.network.NotificationPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side helper for sending System pop-ups. */
public final class Notifications {
    public static void send(ServerPlayer player, SystemNotification notification) {
        PacketDistributor.sendToPlayer(player, new NotificationPayload(notification));
    }

    private Notifications() {}
}
