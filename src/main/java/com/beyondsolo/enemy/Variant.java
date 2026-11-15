package com.beyondsolo.enemy;

import com.beyondsolo.system.PlayerRank;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * How strong one creature is next to the rest of its kind. Most are {@link #NORMAL}; veterans are a little
 * tougher; elites are bigger and carry one affix; a champion leads a pack: bigger again, two affixes, its name over
 * its head. Higher gates hold more of the strong ones.
 */
public enum Variant {
    NORMAL(1f, 1f, 1f, 0, 1f),
    VETERAN(1.35f, 1.15f, 1.06f, 0, 1.4f),
    ELITE(2.2f, 1.35f, 1.18f, 1, 2.5f),
    CHAMPION(3.6f, 1.6f, 1.32f, 2, 4.5f);

    public final float health;
    public final float damage;
    /** Size (the generic.scale attribute). */
    public final float scale;
    public final int affixes;
    /** Kill XP and drops multiplier. */
    public final float reward;

    Variant(float health, float damage, float scale, int affixes, float reward) {
        this.health = health;
        this.damage = damage;
        this.scale = scale;
        this.affixes = affixes;
        this.reward = reward;
    }

    public static Variant byIndex(int i) {
        Variant[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    public boolean special() {
        return this == ELITE || this == CHAMPION;
    }

    public Component title() {
        return Component.translatable("enemy.beyondsolo.variant." + name().toLowerCase());
    }

    public int color() {
        return switch (this) {
            case NORMAL -> 0xCDD2E1;
            case VETERAN -> 0x9FD8A0;
            case ELITE -> 0x6FB8FF;
            case CHAMPION -> 0xFFC74A;
        };
    }

    /** One member of a pack (not its leader): mostly normal, some veterans, a few elites in the high gates. */
    public static Variant rollMember(PlayerRank rank, RandomSource r) {
        int t = rank.tier();
        float roll = r.nextFloat();
        float elite = 0.02f + 0.025f * t;
        float veteran = 0.18f + 0.04f * t;
        if (roll < elite) return ELITE;
        if (roll < elite + veteran) return VETERAN;
        return NORMAL;
    }

    /** A pack's leader, if it has one: an elite or (rarer, more often deeper in high gates) a champion. */
    public static Variant rollLeader(PlayerRank rank, RandomSource r, boolean lateRoom) {
        int t = rank.tier();
        float lead = 0.3f + 0.07f * t + (lateRoom ? 0.15f : 0f);
        if (r.nextFloat() >= lead) return NORMAL;
        float champion = 0.18f + 0.05f * t + (lateRoom ? 0.12f : 0f);
        return r.nextFloat() < champion ? CHAMPION : ELITE;
    }
}
