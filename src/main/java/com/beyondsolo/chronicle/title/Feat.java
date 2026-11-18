package com.beyondsolo.chronicle.title;

import com.beyondsolo.chronicle.ChronicleData;
import com.beyondsolo.chronicle.journal.CreatureFamily;
import com.beyondsolo.chronicle.journal.Journal;
import com.beyondsolo.depths.WeaknessLog;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.key.DungeonKeys;
import com.beyondsolo.penalty.PenaltyZone;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.redgate.RedGateModule;
import com.beyondsolo.redgate.RedRecord;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.tower.RushTier;
import com.beyondsolo.tower.TowerAPI;
import com.beyondsolo.tower.TowerData;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.function.BiFunction;

/**
 * What a title is earned for: a number that only goes up. Some the chronicle counts itself from events (the
 * {@link #counter} ones, kept in {@link ChronicleData}); the rest are read from the records the other features already
 * keep (gate clears, the Citadel, red gates, quests...), so nothing there had to change for titles.
 */
public enum Feat {
    KILLS(counter()),
    BOSSES(counter()),
    ASCENDED(counter()),
    /** A boss slain while the hunter was below 15% of their health. */
    CLUTCH(counter()),
    /** A creature slain whose name burned red. */
    GIANTS(counter()),
    DEATHS(counter()),
    BEASTS(counter()),
    ARTHROPODS(counter()),
    UNDEAD(counter()),
    HUMANOIDS(counter()),
    ARCANE(counter()),
    GATES((p, c) -> (long) Economy.data(p).totalClears()),
    S_GATES((p, c) -> (long) Economy.data(p).clears(PlayerRank.S)),
    BREAKS(counter()),
    RED_GATES((p, c) -> {
        RedRecord r = p.getData(RedGateModule.RECORD);
        return (long) r.cleared() + r.escaped();
    }),
    RED_MASTERS((p, c) -> (long) p.getData(RedGateModule.RECORD).cleared()),
    RED_NIGHTS((p, c) -> (long) p.getData(RedGateModule.RECORD).nights()),
    KEY_RUNS((p, c) -> (long) DungeonKeys.data(p).totalCleared()),
    KEY_CHAIN((p, c) -> (long) DungeonKeys.data(p).longestChain),
    HIDDEN_ROOMS(counter()),
    TRAP_ROOMS(counter()),
    WEAKNESSES((p, c) -> (long) WeaknessLog.of(p).size()),
    TOWER_FLOOR((p, c) -> (long) TowerAPI.data(p).highest),
    LORD((p, c) -> (long) TowerAPI.data(p).lordKills),
    SPIRE((p, c) -> (long) TowerAPI.data(p).spireBest),
    RUSHES((p, c) -> {
        TowerData d = TowerAPI.data(p);
        long n = 0;
        for (int clears : d.rushClears) n += clears;
        return n;
    }),
    SOVEREIGN_RUSH((p, c) -> (long) TowerAPI.data(p).rushClears[RushTier.SOVEREIGN.ordinal()]),
    QUESTS((p, c) -> (long) QuestEngine.data(p).completedCount()),
    DAILIES(counter()),
    SURPRISES(counter()),
    PENALTIES((p, c) -> (long) PenaltyZone.data(p).served()),
    LEVEL((p, c) -> (long) SystemAPI.get(p).level()),
    GOLD((p, c) -> Economy.data(p).earned()),
    REACTIONS(counter()),
    COMBO(counter()),
    SHADOWS((p, c) -> (long) ShadowService.army(p).size()),
    JOURNAL((p, c) -> (long) c.pages());

    private final BiFunction<ServerPlayer, ChronicleData, Long> reader;

    Feat(BiFunction<ServerPlayer, ChronicleData, Long> reader) {
        this.reader = reader;
    }

    /** Marker for the feats the chronicle counts itself (read back by name). */
    private static BiFunction<ServerPlayer, ChronicleData, Long> counter() {
        return null;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Counted by the chronicle itself (kept in {@link ChronicleData#count}). */
    public boolean counted() {
        return reader == null;
    }

    public long value(ServerPlayer p, ChronicleData c) {
        if (reader == null) return c.count(id());
        try {
            return reader.apply(p, c);
        } catch (RuntimeException e) {
            // a record that isn't there (a feature turned off): no progress rather than no titles at all
            return 0;
        }
    }

    /** The feat counting kills of one family. */
    public static Feat of(CreatureFamily family) {
        return switch (family) {
            case BEAST -> BEASTS;
            case ARTHROPOD -> ARTHROPODS;
            case UNDEAD -> UNDEAD;
            case HUMANOID -> HUMANOIDS;
            case ARCANE -> ARCANE;
        };
    }

    /** How far the hunter has to go for a title's target: the journal's goal is every page there is. */
    public long target(long declared) {
        return this == JOURNAL && declared <= 0 ? Journal.size() : declared;
    }

    public static Feat byId(String id) {
        for (Feat f : values()) if (f.id().equals(id)) return f;
        return null;
    }
}
