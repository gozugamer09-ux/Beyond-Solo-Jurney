package com.beyondsolo.chronicle.title;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Where a title comes from, for the filters of the TITLES page. */
public enum TitleCategory {
    HUNT, GATES, CITADEL, SYSTEM, MASTERY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("chronicle.beyondsolo.category." + id());
    }
}
