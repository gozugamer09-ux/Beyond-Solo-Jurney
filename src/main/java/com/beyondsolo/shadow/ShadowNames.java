package com.beyondsolo.shadow;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * Names for elite shadows raised from bosses: an invented name and an epithet. The name is kept as
 * {@code "Vhalgrim|unbroken"} so every player reads the epithet in their own language
 * ({@code shadow.beyondsolo.epithet.unbroken} = "%s, the Unbroken"). Names saved before that spelled the
 * English epithet out ("Vhalgrim, the Unbroken"); {@link #display} still reads those.
 */
public final class ShadowNames {
    private static final String[] HEAD = {"Vhal", "Kor", "Ash", "Mor", "Zeph", "Gral", "Nyx", "Thra", "Sol", "Drae", "Ul", "Bryn",
            "Kael", "Ves", "Ruun", "Ost", "Iz", "Hael", "Tor", "Sev"};
    private static final String[] TAIL = {"grim", "vex", "oth", "ra", "mund", "dris", "gar", "eth", "iel", "kor", "thas", "ven",
            "ric", "wyn", "ash", "dun"};
    /** Epithet ids: {@code shadow.beyondsolo.epithet.<id>} in the language files. */
    static final String[] EPITHET = {"unbroken", "ashen", "walks_twice", "hollow_crown", "last_ember", "gatebreaker", "deep_stair",
            "silent_oath", "grave_sworn", "black_water", "ninth_bell", "unburied"};
    /** The same epithets as older saves spelled them, in the same order. */
    private static final String[] LEGACY = {"the Unbroken", "the Ashen", "Who Walks Twice", "of the Hollow Crown", "the Last Ember",
            "the Gatebreaker", "of the Deep Stair", "the Silent Oath", "the Grave-Sworn", "of Black Water", "the Ninth Bell",
            "the Unburied"};
    /** The stored name of a necromancer's first steed: shown as {@code shadow.beyondsolo.starter_steed}. */
    public static final String STARTER_STEED = "#starter_steed";
    private static final String LEGACY_STEED = "Duskmane";

    public static String boss(RandomSource r) {
        return HEAD[r.nextInt(HEAD.length)] + TAIL[r.nextInt(TAIL.length)] + "|" + EPITHET[r.nextInt(EPITHET.length)];
    }

    /** What a stored shadow name reads as, in the reader's language. */
    public static Component display(String stored) {
        if (stored.equals(STARTER_STEED) || stored.equals(LEGACY_STEED)) return Component.translatable("shadow.beyondsolo.starter_steed");
        int bar = stored.indexOf('|');
        if (bar > 0) return Component.translatable("shadow.beyondsolo.epithet." + stored.substring(bar + 1), stored.substring(0, bar));
        for (int i = 0; i < LEGACY.length; i++) {
            String tail = ", " + LEGACY[i];
            if (stored.endsWith(tail) && stored.length() > tail.length()) {
                return Component.translatable("shadow.beyondsolo.epithet." + EPITHET[i], stored.substring(0, stored.length() - tail.length()));
            }
        }
        return Component.literal(stored);
    }

    private ShadowNames() {}
}
