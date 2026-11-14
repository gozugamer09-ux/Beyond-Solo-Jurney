package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.notify.SystemNotification;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: show a System pop-up. */
public record NotificationPayload(SystemNotification notification) implements CustomPacketPayload {
    public static final Type<NotificationPayload> TYPE = new Type<>(BeyondSolo.id("notification"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NotificationPayload> STREAM_CODEC =
            SystemNotification.STREAM_CODEC.map(NotificationPayload::new, NotificationPayload::notification);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
