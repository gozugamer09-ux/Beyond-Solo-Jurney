package com.beyondsolo.chronicle.journal;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** The journal's sections. */
public enum JournalCategory {
    CREATURES, BOSSES, REALMS, ARSENAL;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("chronicle.beyondsolo.journal." + id());
    }
}
