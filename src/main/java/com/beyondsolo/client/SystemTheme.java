package com.beyondsolo.client;

import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;

/** The System colour palette (deep navy glass with cyan light). All values are ARGB. */
public final class SystemTheme {
    // window glass
    public static final int GLASS_TOP = 0xF20C1A33;
    public static final int GLASS_BOTTOM = 0xF2060E1E;
    public static final int CARD_TOP = 0x70183052;
    public static final int CARD_BOTTOM = 0x700D1C36;
    public static final int CARD_BORDER = 0x405FC8FF;
    public static final int TRACK = 0x8C050B18;
    public static final int TRACK_BORDER = 0x30A8DCFF;

    // light
    public static final int ACCENT = 0xFF5FD0FF;
    public static final int ACCENT_BRIGHT = 0xFFB9EDFF;
    public static final int ACCENT_DEEP = 0xFF2F7BFF;

    // text
    public static final int TEXT = 0xFFEAF6FF;
    public static final int TEXT_SOFT = 0xFFA9BED2;
    public static final int TEXT_DIM = 0xFF6F859C;

    // meaning
    public static final int GOLD = 0xFFFFD166;
    public static final int DANGER = 0xFFFF5D6C;
    public static final int GOOD = 0xFF8BE28B;

    // bars (start, end of the gradient)
    public static final int HP_A = 0xFFE23D50;
    public static final int HP_B = 0xFFFF7E6B;
    public static final int MP_A = 0xFF2F7BFF;
    public static final int MP_B = 0xFF62D4FF;
    public static final int XP_A = 0xFF18B8A0;
    public static final int XP_B = 0xFF86F2C6;
    public static final int FATIGUE_A = 0xFFF08A24;
    public static final int FATIGUE_B = 0xFFFFC857;

    public static int rank(PlayerRank rank) {
        return 0xFF000000 | rank.color();
    }

    public static int playerClass(PlayerClass playerClass) {
        return 0xFF000000 | playerClass.color();
    }

    private SystemTheme() {}
}
