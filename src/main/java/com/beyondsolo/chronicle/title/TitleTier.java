package com.beyondsolo.chronicle.title;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** How rare a title is: the colour it is shown in, and which one is worn when the hunter has not picked one. */
public enum TitleTier {
    COMMON(0xFFC9D8EA),
    RARE(0xFF5FB4FF),
    EPIC(0xFFB57BFF),
    LEGENDARY(0xFFFFC14D),
    MYTHIC(0xFFFF5468);

    private final int color;

    TitleTier(int color) {
        this.color = color;
    }

    /** ARGB. */
    public int color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("chronicle.beyondsolo.tier." + name().toLowerCase(Locale.ROOT));
    }
}
