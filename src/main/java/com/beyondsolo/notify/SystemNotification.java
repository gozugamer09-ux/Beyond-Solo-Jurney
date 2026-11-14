package com.beyondsolo.notify;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A System pop-up window. Quests, gates and combat send these with {@link Notifications#send}.
 *
 * @param kind          picks the header text, colour and sound
 * @param title         big line, e.g. "Level Up!" or "[Emergency Quest]"
 * @param body          detail text, wraps automatically
 * @param durationTicks how long it stays on screen (20 ticks = 1 second)
 */
public record SystemNotification(Kind kind, Component title, Component body, int durationTicks) {
    public static final int DEFAULT_DURATION = 120;

    public enum Kind {
        INFO(0xFF4FC3F7),
        QUEST(0xFFFFD54F),
        ALERT(0xFFEF5350),
        LEVEL_UP(0xFF81D4FA),
        REWARD(0xFF9CCC65);

        private final int color;

        Kind(int color) {
            this.color = color;
        }

        public int color() {
            return color;
        }

        public Component header() {
            return Component.translatable("notify.beyondsolo.kind." + name().toLowerCase());
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SystemNotification> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Kind.values()[Math.floorMod(i, Kind.values().length)], Kind::ordinal),
            SystemNotification::kind,
            ComponentSerialization.STREAM_CODEC, SystemNotification::title,
            ComponentSerialization.STREAM_CODEC, SystemNotification::body,
            ByteBufCodecs.VAR_INT, SystemNotification::durationTicks,
            SystemNotification::new);

    public static SystemNotification of(Kind kind, Component title, Component body) {
        return new SystemNotification(kind, title, body, DEFAULT_DURATION);
    }
}
