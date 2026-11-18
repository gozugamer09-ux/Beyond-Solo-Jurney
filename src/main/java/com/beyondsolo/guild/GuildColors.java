package com.beyondsolo.guild;

/**
 * The colours and crests a guild can be founded with. The colour tints its tag in chat and over its members' heads,
 * its crest and its page; the crest is one of {@link #CRESTS} emblems (drawn on the client by
 * {@code client.guild.GuildArt}).
 */
public final class GuildColors {
    /** Azure, crimson, gold, emerald, violet, ember, frost, rose, jade, silver. */
    public static final int[] PALETTE = {0x4FA8FF, 0xE04A5A, 0xE0B04A, 0x4AD08A, 0x9B6BFF, 0xFF8A3D, 0x8FE3FF, 0xFF7AC8, 0x3FD9C0, 0xC8D0E0};
    /** How many crests there are to choose from. */
    public static final int CRESTS = 12;

    public static int color(int index) {
        return PALETTE[Math.floorMod(index, PALETTE.length)];
    }

    public static int clampColor(int index) {
        return Math.floorMod(index, PALETTE.length);
    }

    public static int clampCrest(int index) {
        return Math.floorMod(index, CRESTS);
    }

    private GuildColors() {}
}
