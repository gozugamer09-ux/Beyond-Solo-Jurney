package com.beyondsolo.system;

import net.minecraft.network.chat.Component;

/** The five core stats. Every stat starts at {@link #BASE} and only goes up. */
public enum Stat {
    STR("str"),
    AGI("agi"),
    VIT("vit"),
    INT("int"),
    SENSE("sense");

    public static final int BASE = 10;

    private final String key;

    Stat(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public Component displayName() {
        return Component.translatable("stat.beyondsolo." + key);
    }

    public Component description() {
        return Component.translatable("stat.beyondsolo." + key + ".desc");
    }

    public static Stat byIndex(int i) {
        Stat[] all = values();
        return i >= 0 && i < all.length ? all[i] : null;
    }
}
