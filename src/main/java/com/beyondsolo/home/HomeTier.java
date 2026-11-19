package com.beyondsolo.home;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * How much of the residence a hunter owns. Each tier opens more of the floor at the top of their tower: the Studio (the
 * flat itself: living room, bed, kitchenette and store), the Family Home (the west wing: kitchen and dining room, a room
 * for their mother, the study with the Association's desk), the Training Hall (the east wing: a tall hall with training
 * dummies) and the Penthouse (the floor above: the trophy hall of the great threats they have slain, and the roof
 * terrace). What each asks for is in {@link HomeConfig}.
 */
public enum HomeTier {
    NONE,
    STUDIO,
    FAMILY,
    HALL,
    PENTHOUSE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("home.beyondsolo.tier." + id());
    }

    /** The next tier up, or null at the top. */
    @Nullable
    public HomeTier next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }

    public boolean atLeast(HomeTier t) {
        return ordinal() >= t.ordinal();
    }

    public static HomeTier of(int i) {
        HomeTier[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    public static HomeTier byId(String id) {
        for (HomeTier t : values()) if (t.id().equalsIgnoreCase(id)) return t;
        return NONE;
    }
}
