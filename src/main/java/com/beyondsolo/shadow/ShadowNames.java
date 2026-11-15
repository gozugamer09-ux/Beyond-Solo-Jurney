package com.beyondsolo.shadow;

import net.minecraft.util.RandomSource;

/** Names for elite shadows raised from bosses: an invented name and an epithet. */
public final class ShadowNames {
    private static final String[] HEAD = {"Vhal", "Kor", "Ash", "Mor", "Zeph", "Gral", "Nyx", "Thra", "Sol", "Drae", "Ul", "Bryn",
            "Kael", "Ves", "Ruun", "Ost", "Iz", "Hael", "Tor", "Sev"};
    private static final String[] TAIL = {"grim", "vex", "oth", "ra", "mund", "dris", "gar", "eth", "iel", "kor", "thas", "ven",
            "ric", "wyn", "ash", "dun"};
    private static final String[] EPITHET = {"the Unbroken", "the Ashen", "Who Walks Twice", "of the Hollow Crown", "the Last Ember",
            "the Gatebreaker", "of the Deep Stair", "the Silent Oath", "the Grave-Sworn", "of Black Water", "the Ninth Bell",
            "the Unburied"};

    public static String boss(RandomSource r) {
        return HEAD[r.nextInt(HEAD.length)] + TAIL[r.nextInt(TAIL.length)] + ", " + EPITHET[r.nextInt(EPITHET.length)];
    }

    private ShadowNames() {}
}
