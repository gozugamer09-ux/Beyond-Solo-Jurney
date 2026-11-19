package com.beyondsolo.home;

import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** The residence's titles in the hunter's chronicle. Perk values are first guesses for the balancing pass. */
public final class HomeJournal {
    /** Bought a home of their own. */
    public static final String HOMEOWNER = TitleDef.PREFIX + "a_place_to_return";
    /** Owns the Penthouse. */
    public static final String SKYLINE = TitleDef.PREFIX + "skyline_sovereign";

    static void register() {
        Titles.register(new TitleDef("a_place_to_return", TitleCategory.SYSTEM, TitleTier.RARE, null, 0,
                List.of(new PerkLine(TitlePerk.STAMINA, 0.04f), new PerkLine(TitlePerk.VITALITY, 0.02f)), false));
        Titles.register(new TitleDef("skyline_sovereign", TitleCategory.SYSTEM, TitleTier.LEGENDARY, null, 0,
                List.of(new PerkLine(TitlePerk.GOLD, 0.05f), new PerkLine(TitlePerk.XP, 0.03f), new PerkLine(TitlePerk.FOCUS, 0.03f)), false));
    }

    /** The hunter's home is {@code tier} now: the titles it earns. */
    static void owned(ServerPlayer p, HomeTier tier) {
        if (!SystemAPI.hasSystem(p)) return;
        if (tier.atLeast(HomeTier.STUDIO)) SystemAPI.setTitle(p, HOMEOWNER);
        if (tier.atLeast(HomeTier.PENTHOUSE)) SystemAPI.setTitle(p, SKYLINE);
    }

    private HomeJournal() {}
}
