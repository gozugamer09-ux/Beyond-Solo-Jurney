package com.beyondsolo.ruler;

import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The rulers in the hunter's chronicle. Their journal pages come with the great threats' (every
 * {@link WorldBossKind} has one); these are the titles the light gives and takes. Perk values are first guesses for the
 * balancing pass.
 */
public final class RulerJournal {
    /** Had a share in felling a ruler of light. */
    public static final String RULERBREAKER = TitleDef.PREFIX + "rulerbreaker";
    /** Did the most to a ruler that fell (secret until earned). */
    public static final String ECLIPSE = TitleDef.PREFIX + "eclipse";
    /** Brought a monarch down at a ruler's side, and was blessed for it. */
    public static final String ALLY_OF_THE_DAWN = TitleDef.PREFIX + "ally_of_the_dawn";
    /** Cut down {@link #HERALDS} heralds of light. */
    public static final String HERALDS_BANE = TitleDef.PREFIX + "heralds_bane";
    /** Felled the ruler that came to judge them (secret until earned). */
    public static final String UNSENTENCED = TitleDef.PREFIX + "unsentenced";
    /** Heralds for the Herald's Bane. */
    public static final int HERALDS = 25;

    static void register() {
        Titles.register(new TitleDef("rulerbreaker", TitleCategory.HUNT, TitleTier.MYTHIC, null, 0,
                List.of(new PerkLine(TitlePerk.MIGHT, 0.08f), new PerkLine(TitlePerk.VITALITY, 0.08f), new PerkLine(TitlePerk.BOSS_DAMAGE, 0.10f)),
                false));
        Titles.register(new TitleDef("eclipse", TitleCategory.HUNT, TitleTier.MYTHIC, null, 0,
                List.of(new PerkLine(TitlePerk.BOSS_DAMAGE, 0.12f), new PerkLine(TitlePerk.MIGHT, 0.10f)), true));
        Titles.register(new TitleDef("ally_of_the_dawn", TitleCategory.HUNT, TitleTier.LEGENDARY, null, 0,
                List.of(new PerkLine(TitlePerk.GIANT_DAMAGE, 0.10f), new PerkLine(TitlePerk.GUARD, 0.05f)), false));
        Titles.register(new TitleDef("heralds_bane", TitleCategory.HUNT, TitleTier.EPIC, null, 0,
                List.of(new PerkLine(TitlePerk.CREATURE_DAMAGE, 0.08f), new PerkLine(TitlePerk.SWIFTNESS, 0.04f)), false));
        Titles.register(new TitleDef("unsentenced", TitleCategory.HUNT, TitleTier.MYTHIC, null, 0,
                List.of(new PerkLine(TitlePerk.LAST_STAND, 0.12f), new PerkLine(TitlePerk.VITALITY, 0.10f)), true));
    }

    /** A hunter was paid for a ruler's fall. */
    static void earned(ServerPlayer p, WorldBossKind kind, boolean top) {
        if (!SystemAPI.hasSystem(p)) return;
        SystemAPI.setTitle(p, RULERBREAKER);
        if (top) SystemAPI.setTitle(p, ECLIPSE);
    }

    /** A hunter felled the ruler that came to judge them. */
    static void overturned(ServerPlayer p) {
        if (SystemAPI.hasSystem(p)) SystemAPI.setTitle(p, UNSENTENCED);
    }

    /** A hunter helped bring a monarch down at a ruler's side. */
    static void allied(ServerPlayer p) {
        if (SystemAPI.hasSystem(p)) SystemAPI.setTitle(p, ALLY_OF_THE_DAWN);
    }

    /** A hunter cut down a herald. */
    static void heraldSlain(ServerPlayer p, RulerRecord record) {
        if (record.heralds() >= HERALDS && SystemAPI.hasSystem(p)) SystemAPI.setTitle(p, HERALDS_BANE);
    }

    private RulerJournal() {}
}
