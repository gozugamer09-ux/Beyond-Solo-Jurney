package com.beyondsolo.system;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * Class from the evaluation. The weights decide where the rank's bonus stat points go
 * (order: STR, AGI, VIT, INT, SENSE). Skills per class come in a later update.
 */
public enum PlayerClass {
    WARRIOR(0xE0675A, 4, 2, 3, 0, 1),
    ASSASSIN(0x9B7BE0, 2, 5, 1, 0, 2),
    MAGE(0x5AB0E0, 0, 1, 1, 6, 2),
    GUARDIAN(0xC9A35A, 2, 0, 6, 0, 2),
    HEALER(0x7CE09B, 0, 1, 3, 4, 2),
    RANGER(0x9BD06A, 1, 3, 1, 1, 4);

    private final int color;
    private final int[] statWeights;

    PlayerClass(int color, int... statWeights) {
        this.color = color;
        this.statWeights = statWeights;
    }

    public int color() {
        return color;
    }

    public int statWeight(Stat stat) {
        return statWeights[stat.ordinal()];
    }

    public Component displayName() {
        return Component.translatable("class.beyondsolo." + name().toLowerCase());
    }

    public static PlayerClass roll(RandomSource random) {
        PlayerClass[] all = values();
        return all[random.nextInt(all.length)];
    }

    public static PlayerClass byName(String name) {
        for (PlayerClass c : values()) if (c.name().equalsIgnoreCase(name)) return c;
        return null;
    }
}
