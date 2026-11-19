package com.beyondsolo.reward;

import net.minecraft.network.chat.Component;

/**
 * The three random boxes. A plain box holds one thing, picked at random. A blessed box offers three and lets the
 * hunter take the one they need. A cursed box holds something better than either, and a curse for whoever opens it.
 */
public enum BoxKind {
    PLAIN("random_box", 0xFF5FB8FF),
    BLESSED("blessed_random_box", 0xFFFFD27A),
    CURSED("cursed_random_box", 0xFFE0405A);

    private final String id;
    private final int color;

    BoxKind(String id, int color) {
        this.id = id;
        this.color = color;
    }

    /** The item's registry path. */
    public String id() {
        return id;
    }

    /** The box's light, on screen and in the world. */
    public int color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("item.beyondsolo." + id);
    }

    public static BoxKind byIndex(int i) {
        BoxKind[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }
}
