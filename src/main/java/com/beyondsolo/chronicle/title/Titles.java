package com.beyondsolo.chronicle.title;

import com.beyondsolo.chronicle.journal.CreatureFamily;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.beyondsolo.chronicle.title.TitleCategory.CITADEL;
import static com.beyondsolo.chronicle.title.TitleCategory.GATES;
import static com.beyondsolo.chronicle.title.TitleCategory.HUNT;
import static com.beyondsolo.chronicle.title.TitleCategory.MASTERY;
import static com.beyondsolo.chronicle.title.TitleCategory.SYSTEM;
import static com.beyondsolo.chronicle.title.TitleTier.COMMON;
import static com.beyondsolo.chronicle.title.TitleTier.EPIC;
import static com.beyondsolo.chronicle.title.TitleTier.LEGENDARY;
import static com.beyondsolo.chronicle.title.TitleTier.RARE;

/**
 * Every title the System gives, in the order the TITLES page lists them. The four titles that existed before the
 * chronicle (the Arbiter's, the Trial of the Depths', the Citadel's two) keep their keys, so hunters who earned them keep
 * them. Perk values are first guesses for the balancing pass.
 *
 * <p>Another feature can give a title of its own with {@code SystemAPI.setTitle(player, "title.beyondsolo.x")}: an
 * unregistered key is still earned, announced and wearable (with no perk); {@link #register} gives it a page entry.</p>
 */
public final class Titles {
    private static final Map<String, TitleDef> BY_KEY = new LinkedHashMap<>();

    // the ones other features give (they also have a feat, so a hunter who earned them before still gets them)
    public static final String ARBITER_SLAYER = TitleDef.PREFIX + "arbiter_slayer";
    public static final String RELENTLESS = TitleDef.PREFIX + "relentless";
    public static final String CITADEL_CONQUEROR = TitleDef.PREFIX + "citadel_conqueror";
    public static final String RUSH_SOVEREIGN = TitleDef.PREFIX + "rush_sovereign";

    static {
        // ---- the hunt
        feat("first_hunt", HUNT, COMMON, Feat.KILLS, 1, p(TitlePerk.XP, 0.02f));
        feat("gate_hunter", HUNT, COMMON, Feat.KILLS, 100, p(TitlePerk.CREATURE_DAMAGE, 0.03f));
        feat("reaper", HUNT, EPIC, Feat.KILLS, 1000, p(TitlePerk.CREATURE_DAMAGE, 0.06f), p(TitlePerk.SWIFTNESS, 0.03f));
        feat("fangbreaker", HUNT, RARE, Feat.BEASTS, 150, family(CreatureFamily.BEAST, 0.10f));
        feat("hivebane", HUNT, RARE, Feat.ARTHROPODS, 150, family(CreatureFamily.ARTHROPOD, 0.10f));
        feat("gravewarden", HUNT, RARE, Feat.UNDEAD, 100, family(CreatureFamily.UNDEAD, 0.10f));
        feat("warband_breaker", HUNT, RARE, Feat.HUMANOIDS, 150, family(CreatureFamily.HUMANOID, 0.10f));
        feat("spellbreaker", HUNT, RARE, Feat.ARCANE, 100, family(CreatureFamily.ARCANE, 0.10f));
        feat("boss_hunter", HUNT, RARE, Feat.BOSSES, 10, p(TitlePerk.BOSS_DAMAGE, 0.05f));
        feat("lordsbane", HUNT, EPIC, Feat.BOSSES, 50, p(TitlePerk.BOSS_DAMAGE, 0.10f));
        feat("ascended_bane", HUNT, EPIC, Feat.ASCENDED, 5, p(TitlePerk.BOSS_DAMAGE, 0.08f), p(TitlePerk.VITALITY, 0.04f));
        secret("giant_slayer", HUNT, EPIC, Feat.GIANTS, 1, p(TitlePerk.GIANT_DAMAGE, 0.10f));
        secret("one_who_endured", HUNT, LEGENDARY, Feat.CLUTCH, 1, p(TitlePerk.LAST_STAND, 0.15f));
        feat("rises_again", HUNT, COMMON, Feat.DEATHS, 10, p(TitlePerk.VITALITY, 0.04f));

        // ---- gates
        feat("gate_walker", GATES, COMMON, Feat.GATES, 1, p(TitlePerk.GOLD, 0.03f));
        feat("seasoned_raider", GATES, RARE, Feat.GATES, 25, p(TitlePerk.GOLD, 0.05f));
        feat("hundred_gates", GATES, EPIC, Feat.GATES, 100, p(TitlePerk.GOLD, 0.08f), p(TitlePerk.XP, 0.03f));
        feat("abyss_walker", GATES, EPIC, Feat.S_GATES, 1, p(TitlePerk.BOSS_DAMAGE, 0.06f), p(TitlePerk.CREATURE_DAMAGE, 0.03f));
        feat("breakwater", GATES, RARE, Feat.BREAKS, 1, p(TitlePerk.GUARD, 0.05f));
        feat("shield_of_the_city", GATES, EPIC, Feat.BREAKS, 10, p(TitlePerk.GUARD, 0.08f), p(TitlePerk.ARMOR, 2));
        feat("red_gate_survivor", GATES, RARE, Feat.RED_GATES, 1, p(TitlePerk.VITALITY, 0.05f));
        feat("nightwalker", GATES, RARE, Feat.RED_NIGHTS, 10, p(TitlePerk.STAMINA, 0.10f));
        feat("redgate_breaker", GATES, EPIC, Feat.RED_MASTERS, 5, p(TitlePerk.BOSS_DAMAGE, 0.08f));
        feat("keybearer", GATES, RARE, Feat.KEY_RUNS, 10, p(TitlePerk.GOLD, 0.05f));
        feat("chainrunner", GATES, RARE, Feat.KEY_CHAIN, 5, p(TitlePerk.SWIFTNESS, 0.04f));
        feat("seeker", GATES, RARE, Feat.HIDDEN_ROOMS, 5, p(TitlePerk.LUCK, 1));
        feat("trapbreaker", GATES, RARE, Feat.TRAP_ROOMS, 10, p(TitlePerk.ARMOR, 2));
        feat("keen_eye", GATES, RARE, Feat.WEAKNESSES, 20, p(TitlePerk.ELEMENTAL, 0.10f));

        // ---- the Crimson Citadel
        feat("citadel_climber", CITADEL, COMMON, Feat.TOWER_FLOOR, 10, p(TitlePerk.XP, 0.03f));
        feat("halfway_to_the_throne", CITADEL, RARE, Feat.TOWER_FLOOR, 50, p(TitlePerk.VITALITY, 0.05f));
        feat("citadel_conqueror", CITADEL, LEGENDARY, Feat.LORD, 1, p(TitlePerk.MIGHT, 0.06f), p(TitlePerk.VITALITY, 0.06f));
        feat("spire_walker", CITADEL, EPIC, Feat.SPIRE, 25, p(TitlePerk.HASTE, 0.06f));
        feat("the_endless", CITADEL, LEGENDARY, Feat.SPIRE, 100, p(TitlePerk.BOSS_DAMAGE, 0.10f), p(TitlePerk.HASTE, 0.05f));
        feat("colosseum_challenger", CITADEL, RARE, Feat.RUSHES, 1, p(TitlePerk.BOSS_DAMAGE, 0.05f));
        feat("rush_sovereign", CITADEL, LEGENDARY, Feat.SOVEREIGN_RUSH, 1, p(TitlePerk.BOSS_DAMAGE, 0.12f));

        // ---- the System
        feat("rising_hunter", SYSTEM, COMMON, Feat.LEVEL, 10, p(TitlePerk.VITALITY, 0.02f));
        feat("proven_hunter", SYSTEM, RARE, Feat.LEVEL, 40, p(TitlePerk.MIGHT, 0.04f));
        feat("peerless", SYSTEM, EPIC, Feat.LEVEL, 80, p(TitlePerk.MIGHT, 0.05f), p(TitlePerk.VITALITY, 0.05f));
        feat("diligent", SYSTEM, COMMON, Feat.DAILIES, 7, p(TitlePerk.XP, 0.03f));
        feat("iron_discipline", SYSTEM, RARE, Feat.DAILIES, 30, p(TitlePerk.XP, 0.05f), p(TitlePerk.STAMINA, 0.05f));
        feat("wastes_survivor", SYSTEM, RARE, Feat.PENALTIES, 1, p(TitlePerk.ARMOR, 2));
        feat("questbound", SYSTEM, RARE, Feat.QUESTS, 50, p(TitlePerk.GOLD, 0.04f));
        feat("ready_for_anything", SYSTEM, RARE, Feat.SURPRISES, 5, p(TitlePerk.SWIFTNESS, 0.04f));
        given("relentless", SYSTEM, RARE, p(TitlePerk.MIGHT, 0.04f));
        given("arbiter_slayer", SYSTEM, EPIC, p(TitlePerk.XP, 0.05f), p(TitlePerk.MIGHT, 0.03f));
        feat("goldhand", SYSTEM, RARE, Feat.GOLD, 100_000, p(TitlePerk.GOLD, 0.06f));

        // ---- mastery
        feat("catalyst", MASTERY, RARE, Feat.REACTIONS, 50, p(TitlePerk.ELEMENTAL, 0.10f));
        feat("flowing_blade", MASTERY, RARE, Feat.COMBO, 6, p(TitlePerk.HASTE, 0.05f));
        feat("gravecaller", MASTERY, RARE, Feat.SHADOWS, 10, p(TitlePerk.FOCUS, 0.05f));
        feat("herald_of_the_legion", MASTERY, EPIC, Feat.SHADOWS, 40, p(TitlePerk.MIGHT, 0.05f), p(TitlePerk.FOCUS, 0.05f));
        feat("archivist", MASTERY, RARE, Feat.JOURNAL, 40, p(TitlePerk.LUCK, 1));
        feat("keeper_of_lore", MASTERY, LEGENDARY, Feat.JOURNAL, 100, p(TitlePerk.XP, 0.05f), p(TitlePerk.LUCK, 2));
    }

    private static PerkLine p(TitlePerk perk, float value) {
        return new PerkLine(perk, value);
    }

    private static PerkLine family(CreatureFamily family, float value) {
        return new PerkLine(TitlePerk.FAMILY_DAMAGE, value, family);
    }

    private static void feat(String id, TitleCategory category, TitleTier tier, Feat feat, long target, PerkLine... perks) {
        register(new TitleDef(id, category, tier, feat, target, List.of(perks), false));
    }

    private static void secret(String id, TitleCategory category, TitleTier tier, Feat feat, long target, PerkLine... perks) {
        register(new TitleDef(id, category, tier, feat, target, List.of(perks), true));
    }

    private static void given(String id, TitleCategory category, TitleTier tier, PerkLine... perks) {
        register(new TitleDef(id, category, tier, null, 0, List.of(perks), false));
    }

    /** Adds (or replaces) a title. Other features call this from their setup for titles they give themselves. */
    public static synchronized void register(TitleDef def) {
        BY_KEY.put(def.key(), def);
    }

    public static @Nullable TitleDef byKey(String key) {
        return key == null ? null : BY_KEY.get(key);
    }

    public static @Nullable TitleDef byId(String id) {
        return byKey(TitleDef.PREFIX + id);
    }

    public static Collection<TitleDef> all() {
        return Collections.unmodifiableCollection(BY_KEY.values());
    }

    public static List<TitleDef> earnedBy(Feat feat) {
        List<TitleDef> out = new ArrayList<>();
        for (TitleDef d : BY_KEY.values()) if (d.feat() == feat) out.add(d);
        return out;
    }

    /** The tier of a title key (unregistered ones count as rare). */
    public static TitleTier tierOf(String key) {
        TitleDef d = byKey(key);
        return d == null ? RARE : d.tier();
    }

    private Titles() {}
}
