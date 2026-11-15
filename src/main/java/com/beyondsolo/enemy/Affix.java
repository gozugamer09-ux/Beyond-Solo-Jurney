package com.beyondsolo.enemy;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.EnumSet;

/**
 * Extra traits elites and champions carry. What each one does lives in {@link EnemyHooks} (combat and death) and
 * {@link Enemies#apply} (attributes).
 */
public enum Affix {
    /** Faster on its feet. */
    SWIFT,
    /** More armour and hard to knock back. */
    ARMORED,
    /** Heals by a quarter of the damage it deals. */
    VAMPIRIC,
    /** Below half health it goes into a frenzy: faster and stronger. */
    FRENZIED,
    /** Bursts a moment after it dies: get clear. */
    VOLATILE,
    /** Splits into two smaller copies when it dies. */
    SPLITTING,
    /** Skills and element damage do a third less to it. */
    WARDED,
    /** Hurts whoever strikes it in melee. */
    THORNED;

    public Component title() {
        return Component.translatable("enemy.beyondsolo.affix." + name().toLowerCase());
    }

    public static EnumSet<Affix> roll(int count, RandomSource r) {
        EnumSet<Affix> out = EnumSet.noneOf(Affix.class);
        Affix[] all = values();
        for (int guard = 0; out.size() < count && guard < 20; guard++) out.add(all[r.nextInt(all.length)]);
        return out;
    }

    public static int mask(EnumSet<Affix> set) {
        int m = 0;
        for (Affix a : set) m |= 1 << a.ordinal();
        return m;
    }

    public static EnumSet<Affix> fromMask(int mask) {
        EnumSet<Affix> out = EnumSet.noneOf(Affix.class);
        for (Affix a : values()) if ((mask & (1 << a.ordinal())) != 0) out.add(a);
        return out;
    }
}
