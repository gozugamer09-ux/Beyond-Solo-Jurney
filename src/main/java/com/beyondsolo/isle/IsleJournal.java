package com.beyondsolo.isle;

import com.beyondsolo.chronicle.journal.CreatureFamily;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The Halcyon raids in the hunter's chronicle. The queen's and the king's journal pages come with the great threats'
 * (every {@link com.beyondsolo.worldboss.WorldBossKind} has one); these are the island's titles. Perk values are first
 * guesses for the balancing pass.
 */
public final class IsleJournal {
    /** Landed with a raid that retook the island. */
    public static final String HIVE_BREAKER = TitleDef.PREFIX + "hive_breaker";
    /** Did the most to the Hive King as he fell (secret until earned). */
    public static final String KINGSLAYER = TitleDef.PREFIX + "kingslayer";
    /** Was at the burning of {@link #NESTS} nests, over every raid. */
    public static final String NESTBURNER = TitleDef.PREFIX + "nestburner";
    /** Nests for the Nestburner. */
    public static final int NESTS = 12;

    static void register() {
        Titles.register(new TitleDef("hive_breaker", TitleCategory.HUNT, TitleTier.LEGENDARY, null, 0,
                List.of(new PerkLine(TitlePerk.CREATURE_DAMAGE, 0.10f), new PerkLine(TitlePerk.VITALITY, 0.05f)), false));
        Titles.register(new TitleDef("kingslayer", TitleCategory.HUNT, TitleTier.MYTHIC, null, 0,
                List.of(new PerkLine(TitlePerk.BOSS_DAMAGE, 0.10f), new PerkLine(TitlePerk.MIGHT, 0.06f), new PerkLine(TitlePerk.SWIFTNESS, 0.05f)),
                true));
        Titles.register(new TitleDef("nestburner", TitleCategory.HUNT, TitleTier.EPIC, null, 0,
                List.of(new PerkLine(TitlePerk.FAMILY_DAMAGE, 0.08f, CreatureFamily.ARTHROPOD), new PerkLine(TitlePerk.GUARD, 0.04f)), false));
    }

    /** A hunter was there when a nest burned. */
    static void nestBurned(ServerPlayer p, IsleRecord record) {
        if (record.nests() >= NESTS && SystemAPI.hasSystem(p)) SystemAPI.setTitle(p, NESTBURNER);
    }

    private IsleJournal() {}
}
