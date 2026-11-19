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

    /** The longest name a master can give a shadow, and the shortest. */
    public static final int MAX_GIVEN = 16;
    public static final int MIN_GIVEN = 2;

    /**
     * A name as a master typed it, made fit to give: letters (any language's), spaces, apostrophes and hyphens only,
     * no runs of spaces, trimmed, at most {@link #MAX_GIVEN} long. Never holds the ':', '|' or '#' stored names
     * rely on ({@link ShadowRecord#wireName}). Both sides use it: the page as it is typed, the server again.
     */
    public static String clean(String typed) {
        if (typed == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (c == '\u2019') c = '\'';
            boolean ok = Character.isLetter(c) || c == '\'' || c == '-' || Character.getType(c) == Character.NON_SPACING_MARK;
            if (Character.isWhitespace(c)) {
                if (b.length() > 0 && b.charAt(b.length() - 1) != ' ') b.append(' ');
            } else if (ok) {
                b.append(c);
            }
            if (b.length() >= MAX_GIVEN) break;
        }
        return b.toString().stripLeading();
    }

    /** True if a cleaned name can be given: long enough, with at least two letters in it. */
    public static boolean fit(String cleaned) {
        String t = cleaned.strip();
        if (t.length() < MIN_GIVEN || t.length() > MAX_GIVEN) return false;
        int letters = 0;
        for (int i = 0; i < t.length(); i++) if (Character.isLetter(t.charAt(i))) letters++;
        return letters >= 2;
    }

    /** The name a stored one is known by, to tell two names apart (a boss's without its epithet). */
    public static String base(String stored) {
        int bar = stored.indexOf('|');
        return (bar > 0 ? stored.substring(0, bar) : stored).strip().toLowerCase(java.util.Locale.ROOT);
    }

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
