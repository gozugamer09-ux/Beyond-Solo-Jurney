package com.beyondsolo.worldboss;

import com.beyondsolo.chronicle.journal.Journal;
import com.beyondsolo.chronicle.journal.JournalCategory;
import com.beyondsolo.chronicle.journal.JournalEntry;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The great threats in the hunter's chronicle: a journal page for each (filled in once one is seen, its lore opened by
 * the first kill; the chronicle maps the entity to its page) and the titles for felling them, given to every hunter
 * with a share of the kill. Perk values are first guesses for the balancing pass.
 */
public final class WorldBossJournal {
    /** Had a share in felling a world boss. */
    public static final String COLOSSUS_BREAKER = TitleDef.PREFIX + "colossus_breaker";
    /** Had a share in slaying a monarch. */
    public static final String CROWNBREAKER = TitleDef.PREFIX + "crownbreaker";
    /** Did the most to a monarch that fell (secret until earned). */
    public static final String THRONE_TAKER = TitleDef.PREFIX + "throne_taker";

    static void register() {
        for (WorldBossKind k : WorldBossKind.values()) Journal.register(JournalEntry.boss(k.asset()));
        Titles.register(new TitleDef("colossus_breaker", TitleCategory.HUNT, TitleTier.LEGENDARY, null, 0,
                List.of(new PerkLine(TitlePerk.BOSS_DAMAGE, 0.08f), new PerkLine(TitlePerk.VITALITY, 0.05f)), false));
        Titles.register(new TitleDef("crownbreaker", TitleCategory.HUNT, TitleTier.MYTHIC, null, 0,
                List.of(new PerkLine(TitlePerk.MIGHT, 0.08f), new PerkLine(TitlePerk.VITALITY, 0.08f), new PerkLine(TitlePerk.BOSS_DAMAGE, 0.10f)),
                false));
        Titles.register(new TitleDef("throne_taker", TitleCategory.HUNT, TitleTier.MYTHIC, null, 0,
                List.of(new PerkLine(TitlePerk.GIANT_DAMAGE, 0.15f), new PerkLine(TitlePerk.MIGHT, 0.10f)), true));
    }

    /** The great threat a journal page is about, or null. */
    public static @Nullable WorldBossKind kindOf(JournalEntry e) {
        if (e.category() != JournalCategory.BOSSES || e.boss() != null) return null;
        for (WorldBossKind k : WorldBossKind.values()) if (k.asset().equals(e.name())) return k;
        return null;
    }

    /** A hunter was paid for a kill: the titles it earns (each only once). */
    public static void earned(ServerPlayer p, WorldBossKind kind, boolean top) {
        if (!SystemAPI.hasSystem(p)) return;
        if (kind.monarch()) {
            SystemAPI.setTitle(p, CROWNBREAKER);
            if (top) SystemAPI.setTitle(p, THRONE_TAKER);
            // crown against crown: a monarch who had a share in it
            com.beyondsolo.monarch.MonarchAPI.monarchSlain(p);
            // the light against the crowns: a hunter sworn to it who had a share in it
            com.beyondsolo.light.LightAPI.monarchSlain(p);
        } else {
            SystemAPI.setTitle(p, COLOSSUS_BREAKER);
        }
    }

    private WorldBossJournal() {}
}
