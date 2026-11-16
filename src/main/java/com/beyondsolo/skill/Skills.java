package com.beyondsolo.skill;

import com.beyondsolo.job.Subclass;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.beyondsolo.skill.SkillActions.f;
import static com.beyondsolo.skill.SkillActions.mult;

/**
 * Every skill in the game. Each class has five actives and two passives that unlock with levels, plus one
 * advanced skill for the job change; everyone shares four actives and two passives.
 *
 * <p>Numbers live in {@link SkillActions} so the description and the effect never disagree.</p>
 */
public final class Skills {
    private static final Map<String, Skill> ALL = new LinkedHashMap<>();

    // ---------------------------------------------------------------- shared
    public static final Skill SURGE_STEP = active("surge_step", null, PlayerRank.E, 1, 0x7FD8FF, 8, 100, 50,
            SkillActions::surgeStep, (l, t) -> a(f(SkillActions.surgeDistance(l))));
    public static final Skill PULSE_SCAN = active("pulse_scan", null, PlayerRank.E, 3, 0x4FC3F7, 12, 400, 200,
            SkillActions::pulseScan, (l, t) -> a(f(SkillActions.scanRadius(l)), f(SkillActions.scanSeconds(l))));
    public static final Skill APPRAISING_GAZE = active("appraising_gaze", null, PlayerRank.D, 4, 0xFFE27A, 10, 600, 300,
            SkillActions::appraisingGaze, (l, t) -> a(f(SkillActions.gazeSeconds(l)), f(SkillActions.weakSpotBonus(l))));
    public static final Skill PHANTOM_SHROUD = active("phantom_shroud", null, PlayerRank.C, 12, 0x8A9BC8, 20, 900, 500,
            SkillActions::phantomShroud, (l, t) -> a(f(SkillActions.shroudSeconds(l))));
    public static final Skill UNBROKEN_WILL = passive("unbroken_will", null, PlayerRank.D, 5, 0xD9B76A,
            (l, t) -> a(f(l * 3)));
    public static final Skill MANA_FLOW = passive("mana_flow", null, PlayerRank.D, 8, 0x6FA8FF,
            (l, t) -> a(f(SkillActions.manaFlow(l))));

    // ---------------------------------------------------------------- warrior
    public static final Skill RENDING_ARC = active("rending_arc", PlayerClass.WARRIOR, PlayerRank.E, 1, 0xFF7A5C, 10, 120, 60,
            SkillActions::rendingArc, (l, t) -> a(f(6 * mult(l, t))));
    public static final Skill WAR_CRY = active("war_cry", PlayerClass.WARRIOR, PlayerRank.C, 6, 0xFF5340, 18, 600, 360,
            SkillActions::warCry, (l, t) -> a(l >= 6 ? "II" : "I", f(8 + l)));
    public static final Skill BATTLE_HARDENED = passive("battle_hardened", PlayerClass.WARRIOR, PlayerRank.C, 10, 0xE0896A,
            (l, t) -> a(f(0.4f * l), f(0.3f * l)));
    public static final Skill EARTHSPLITTER = advanced("earthsplitter", PlayerClass.WARRIOR, PlayerRank.A, 0xFF8A3D, 35, 500, 300,
            SkillActions::earthsplitter, (l, t) -> a(f(12 * mult(l, t))));
    public static final Skill BULL_RUSH = active("bull_rush", PlayerClass.WARRIOR, PlayerRank.E, 3, 0xFFB08A, 12, 200, 120,
            SkillActions::bullRush, (l, t) -> a(f(SkillActions.rushDistance(l)), f(5 * mult(l, t))));
    public static final Skill FLAME_WAVE = active("flame_wave", PlayerClass.WARRIOR, PlayerRank.C, 14, 0xFF7A2E, 20, 260, 160,
            SkillActions::flameWave, (l, t) -> a(f(6 * mult(l, t)), f(25 + 3 * l)));
    public static final Skill BLOODLUST = passive("bloodlust", PlayerClass.WARRIOR, PlayerRank.C, 16, 0xD04040,
            (l, t) -> a(f(0.5f * l), f(l)));
    public static final Skill WHIRLWIND = active("whirlwind", PlayerClass.WARRIOR, PlayerRank.B, 20, 0xA8F0E0, 24, 300, 180,
            SkillActions::whirlwind, (l, t) -> a(f(2.5f * mult(l, t))));

    // ---------------------------------------------------------------- assassin
    public static final Skill SHADOWSTEP = active("shadowstep", PlayerClass.ASSASSIN, PlayerRank.E, 1, 0xA77BFF, 12, 160, 80,
            SkillActions::shadowstep, (l, t) -> a(f(SkillActions.shadowstepBonus(l))));
    public static final Skill VENOM_EDGE = active("venom_edge", PlayerClass.ASSASSIN, PlayerRank.C, 6, 0x8FE04A, 15, 500, 300,
            SkillActions::venomEdge, (l, t) -> a(f(10 + l), f(14 + 2 * l)));
    public static final Skill KEEN_INSTINCT = passive("keen_instinct", PlayerClass.ASSASSIN, PlayerRank.C, 10, 0xC0A0FF,
            (l, t) -> a(f(2 * l)));
    public static final Skill VEIL_OF_NIGHT = advanced("veil_of_night", PlayerClass.ASSASSIN, PlayerRank.A, 0x6A4FD0, 30, 800, 500,
            SkillActions::veilOfNight, (l, t) -> a(f(SkillActions.veilSeconds(l)), f(60 + 5 * l)));
    public static final Skill FAN_OF_BLADES = active("fan_of_blades", PlayerClass.ASSASSIN, PlayerRank.E, 3, 0xC8E8E0, 10, 140, 80,
            SkillActions::fanOfBlades, (l, t) -> a(f(4.5f * mult(l, t))));
    public static final Skill SHADOW_BIND = active("shadow_bind", PlayerClass.ASSASSIN, PlayerRank.C, 14, 0x8060D0, 18, 360, 220,
            SkillActions::shadowBind, (l, t) -> a(f(SkillActions.bindSeconds(l)), f(4 * mult(l, t))));
    public static final Skill CRUEL_PRECISION = passive("cruel_precision", PlayerClass.ASSASSIN, PlayerRank.C, 16, 0xE06A8A,
            (l, t) -> a(f(4 * l)));
    public static final Skill DEATH_BLOSSOM = active("death_blossom", PlayerClass.ASSASSIN, PlayerRank.B, 20, 0x9AE04A, 24, 320, 200,
            SkillActions::deathBlossom, (l, t) -> a(f(5 * mult(l, t)), f(20 + 3 * l)));

    // ---------------------------------------------------------------- mage
    public static final Skill ARC_BOLT = active("arc_bolt", PlayerClass.MAGE, PlayerRank.E, 1, 0x66CCFF, 10, 50, 30,
            SkillActions::arcBolt, (l, t) -> a(f(7 * mult(l, t))));
    public static final Skill EMBER_BURST = active("ember_burst", PlayerClass.MAGE, PlayerRank.C, 6, 0xFF8A2E, 22, 280, 160,
            SkillActions::emberBurst, (l, t) -> a(f(5 * mult(l, t)), f(30 + 4 * l)));
    public static final Skill ARCANE_FONT = passive("arcane_font", PlayerClass.MAGE, PlayerRank.C, 10, 0x7FA8FF,
            (l, t) -> a(f(8 * l)));
    public static final Skill STARFALL = advanced("starfall", PlayerClass.MAGE, PlayerRank.A, 0xFFB45A, 40, 700, 420,
            SkillActions::starfall, (l, t) -> a(f(SkillActions.meteors(l)), f(8 * mult(l, t))));
    public static final Skill TIDAL_ORB = active("tidal_orb", PlayerClass.MAGE, PlayerRank.E, 3, 0x4FA8FF, 12, 120, 70,
            SkillActions::tidalOrb, (l, t) -> a(f(5.5f * mult(l, t))));
    public static final Skill CHAIN_LIGHTNING = active("chain_lightning", PlayerClass.MAGE, PlayerRank.C, 14, 0xB8E2FF, 22, 240, 140,
            SkillActions::chainLightning, (l, t) -> a(f(6 * mult(l, t)), f(SkillActions.chainJumps(l))));
    public static final Skill ELEMENTAL_ATTUNEMENT = passive("elemental_attunement", PlayerClass.MAGE, PlayerRank.C, 16, 0xC0A8FF,
            (l, t) -> a(f(5 * l)));
    public static final Skill CYCLONE = active("cyclone", PlayerClass.MAGE, PlayerRank.B, 20, 0xA8F0E0, 28, 400, 260,
            SkillActions::cyclone, (l, t) -> a(f(1.5f * mult(l, t))));

    // ---------------------------------------------------------------- guardian
    public static final Skill BULWARK = active("bulwark", PlayerClass.GUARDIAN, PlayerRank.E, 1, 0xFFD36A, 12, 300, 180,
            SkillActions::bulwark, (l, t) -> a(f(SkillActions.bulwarkShield(l))));
    public static final Skill PROVOKE = active("provoke", PlayerClass.GUARDIAN, PlayerRank.C, 6, 0xF0B040, 15, 400, 240,
            SkillActions::provoke, (l, t) -> a(f(10)));
    public static final Skill STONESKIN = passive("stoneskin", PlayerClass.GUARDIAN, PlayerRank.C, 10, 0xC9A35A,
            (l, t) -> a(f(0.5f * l), f(4 * l)));
    public static final Skill AEGIS_DOME = advanced("aegis_dome", PlayerClass.GUARDIAN, PlayerRank.A, 0xFFE08A, 35, 900, 600,
            SkillActions::aegisDome, (l, t) -> a(f(SkillActions.aegisSeconds(l))));
    public static final Skill SHIELD_BASH = active("shield_bash", PlayerClass.GUARDIAN, PlayerRank.E, 3, 0xE8D8B0, 10, 160, 100,
            SkillActions::shieldBash, (l, t) -> a(f(5 * mult(l, t)), f(SkillActions.bashSeconds(l))));
    public static final Skill STONE_SPIKES = active("stone_spikes", PlayerClass.GUARDIAN, PlayerRank.C, 14, 0xC9A35A, 20, 260, 160,
            SkillActions::stoneSpikes, (l, t) -> a(f(6 * mult(l, t))));
    public static final Skill THORNED_GUARD = passive("thorned_guard", PlayerClass.GUARDIAN, PlayerRank.C, 16, 0xB0C070,
            (l, t) -> a(f(0.5f * l)));
    public static final Skill SUNLANCE = active("sunlance", PlayerClass.GUARDIAN, PlayerRank.B, 20, 0xFFF0A8, 28, 360, 220,
            SkillActions::sunlance, (l, t) -> a(f(9 * mult(l, t))));

    // ---------------------------------------------------------------- healer
    public static final Skill MENDING_LIGHT = active("mending_light", PlayerClass.HEALER, PlayerRank.E, 1, 0x8CFFB0, 14, 160, 100,
            SkillActions::mendingLight, (l, t) -> a(f(5 * mult(l, t))));
    public static final Skill PURIFY = active("purify", PlayerClass.HEALER, PlayerRank.C, 6, 0xE8FFF4, 16, 400, 240,
            SkillActions::purify, (l, t) -> a());
    public static final Skill LIFE_BLOOM = passive("life_bloom", PlayerClass.HEALER, PlayerRank.C, 10, 0x7CE09B,
            (l, t) -> a(f(SkillActions.lifeBloom(l))));
    public static final Skill SANCTUARY = advanced("sanctuary", PlayerClass.HEALER, PlayerRank.A, 0xC8FF9A, 40, 1000, 700,
            SkillActions::sanctuary, (l, t) -> a(f(SkillActions.sanctuaryHeal(l))));
    public static final Skill RADIANT_LANCE = active("radiant_lance", PlayerClass.HEALER, PlayerRank.E, 3, 0xFFF0A8, 10, 70, 40,
            SkillActions::radiantLance, (l, t) -> a(f(5 * mult(l, t))));
    public static final Skill TIDEWELL = active("tidewell", PlayerClass.HEALER, PlayerRank.C, 14, 0x5AB8FF, 22, 400, 260,
            SkillActions::tidewell, (l, t) -> a(f(SkillActions.tidewellSeconds(l)), f(2 * mult(l, t))));
    public static final Skill BENEDICTION = passive("benediction", PlayerClass.HEALER, PlayerRank.C, 16, 0xFFD8F0,
            (l, t) -> a(f(4 * l)));
    public static final Skill THORNBIND = active("thornbind", PlayerClass.HEALER, PlayerRank.B, 20, 0x7CC84A, 26, 440, 280,
            SkillActions::thornbind, (l, t) -> a(f(1.5f * mult(l, t))));

    // ---------------------------------------------------------------- ranger
    public static final Skill PIERCING_SHOT = active("piercing_shot", PlayerClass.RANGER, PlayerRank.E, 1, 0xC6F06A, 10, 80, 40,
            SkillActions::piercingShot, (l, t) -> a(f(6 * mult(l, t))));
    public static final Skill HUNTERS_MARK = active("hunters_mark", PlayerClass.RANGER, PlayerRank.C, 6, 0xFF6A6A, 14, 400, 240,
            SkillActions::huntersMark, (l, t) -> a(f(SkillActions.markBonus(l))));
    public static final Skill EAGLE_EYE = passive("eagle_eye", PlayerClass.RANGER, PlayerRank.C, 10, 0x9BD06A,
            (l, t) -> a(f(4 * l)));
    public static final Skill ARROW_RAIN = advanced("arrow_rain", PlayerClass.RANGER, PlayerRank.A, 0xB8F07A, 38, 800, 500,
            SkillActions::arrowRain, (l, t) -> a(f(SkillActions.rainArrows(l))));
    public static final Skill EMBER_ARROW = active("ember_arrow", PlayerClass.RANGER, PlayerRank.E, 3, 0xFF8A3D, 12, 140, 80,
            SkillActions::emberArrow, (l, t) -> a(f(6 * mult(l, t)), f(20 + 3 * l)));
    public static final Skill VAULT_SHOT = active("vault_shot", PlayerClass.RANGER, PlayerRank.C, 14, 0xA8F0E0, 14, 200, 120,
            SkillActions::vaultShot, (l, t) -> a(f(4.5f * mult(l, t))));
    public static final Skill STEADY_AIM = passive("steady_aim", PlayerClass.RANGER, PlayerRank.C, 16, 0xD8E0A0,
            (l, t) -> a(f(SkillActions.STEADY_RANGE), f(3 * l)));
    public static final Skill THUNDER_VOLLEY = active("thunder_volley", PlayerClass.RANGER, PlayerRank.B, 20, 0xB8E2FF, 26, 340, 200,
            SkillActions::thunderVolley, (l, t) -> a(f(SkillActions.volleyBolts(l)), f(5 * mult(l, t))));


    static {
        SURGE_STEP.element(SkillElement.AIR);
        PULSE_SCAN.element(SkillElement.ARCANE);
        APPRAISING_GAZE.element(SkillElement.LIGHT);
        PHANTOM_SHROUD.element(SkillElement.SHADOW);
        UNBROKEN_WILL.element(SkillElement.EARTH);
        MANA_FLOW.element(SkillElement.WATER);
        RENDING_ARC.element(SkillElement.PHYSICAL);
        WAR_CRY.element(SkillElement.FIRE);
        BATTLE_HARDENED.element(SkillElement.PHYSICAL);
        EARTHSPLITTER.element(SkillElement.EARTH);
        SHADOWSTEP.element(SkillElement.SHADOW);
        VENOM_EDGE.element(SkillElement.NATURE);
        KEEN_INSTINCT.element(SkillElement.AIR);
        VEIL_OF_NIGHT.element(SkillElement.SHADOW);
        ARC_BOLT.element(SkillElement.LIGHTNING);
        EMBER_BURST.element(SkillElement.FIRE);
        ARCANE_FONT.element(SkillElement.ARCANE);
        STARFALL.element(SkillElement.FIRE);
        BULWARK.element(SkillElement.EARTH);
        PROVOKE.element(SkillElement.PHYSICAL);
        STONESKIN.element(SkillElement.EARTH);
        AEGIS_DOME.element(SkillElement.LIGHT);
        MENDING_LIGHT.element(SkillElement.LIGHT);
        PURIFY.element(SkillElement.WATER);
        LIFE_BLOOM.element(SkillElement.NATURE);
        SANCTUARY.element(SkillElement.LIGHT);
        PIERCING_SHOT.element(SkillElement.AIR);
        HUNTERS_MARK.element(SkillElement.PHYSICAL);
        EAGLE_EYE.element(SkillElement.AIR);
        ARROW_RAIN.element(SkillElement.AIR);
        BULL_RUSH.element(SkillElement.EARTH);
        FLAME_WAVE.element(SkillElement.FIRE);
        BLOODLUST.element(SkillElement.PHYSICAL);
        WHIRLWIND.element(SkillElement.AIR);
        FAN_OF_BLADES.element(SkillElement.AIR);
        SHADOW_BIND.element(SkillElement.SHADOW);
        CRUEL_PRECISION.element(SkillElement.SHADOW);
        DEATH_BLOSSOM.element(SkillElement.NATURE);
        TIDAL_ORB.element(SkillElement.WATER);
        CHAIN_LIGHTNING.element(SkillElement.LIGHTNING);
        ELEMENTAL_ATTUNEMENT.element(SkillElement.ARCANE);
        CYCLONE.element(SkillElement.AIR);
        SHIELD_BASH.element(SkillElement.PHYSICAL);
        STONE_SPIKES.element(SkillElement.EARTH);
        THORNED_GUARD.element(SkillElement.NATURE);
        SUNLANCE.element(SkillElement.LIGHT);
        RADIANT_LANCE.element(SkillElement.LIGHT);
        TIDEWELL.element(SkillElement.WATER);
        BENEDICTION.element(SkillElement.LIGHT);
        THORNBIND.element(SkillElement.NATURE);
        EMBER_ARROW.element(SkillElement.FIRE);
        VAULT_SHOT.element(SkillElement.AIR);
        STEADY_AIM.element(SkillElement.PHYSICAL);
        THUNDER_VOLLEY.element(SkillElement.LIGHTNING);
    }

    // ---------------------------------------------------------------- lookups

    public static @Nullable Skill get(String path) {
        return ALL.get(path);
    }

    public static List<Skill> all() {
        return Collections.unmodifiableList(new ArrayList<>(ALL.values()));
    }

    /** Skills a class can ever hold: the shared ones and its own, advanced last. Rune arts are not listed (see {@link #runeArts}). */
    public static List<Skill> forClass(PlayerClass c) {
        List<Skill> out = new ArrayList<>();
        for (Skill s : ALL.values()) if (s.fits(c) && !s.advanced() && s.subclass() == null && !s.rune()) out.add(s);
        for (Skill s : ALL.values()) if (s.fits(c) && s.advanced() && s.subclass() == null && !s.rune()) out.add(s);
        return out;
    }

    /** The rune arts: skills only rune stones teach, open to every class. */
    public static List<Skill> runeArts() {
        List<Skill> out = new ArrayList<>();
        for (Skill s : ALL.values()) if (s.rune()) out.add(s);
        return out;
    }

    /** The base class's advanced skills (not the subclass ones). */
    public static List<Skill> advancedFor(PlayerClass c) {
        List<Skill> out = new ArrayList<>();
        for (Skill s : ALL.values()) if (s.owner() == c && s.advanced() && s.subclass() == null) out.add(s);
        return out;
    }

    /** The skills a subclass learns at the job change itself (the rest unlock with levels afterwards). */
    public static List<Skill> startersFor(Subclass sub) {
        List<Skill> out = new ArrayList<>();
        for (Skill s : forSubclass(sub)) if (s.advanced()) out.add(s);
        return out;
    }

    /** A subclass's own skills, actives first. */
    public static List<Skill> forSubclass(Subclass sub) {
        List<Skill> out = new ArrayList<>();
        for (Skill s : ALL.values()) if (s.subclass() == sub && s.isActive()) out.add(s);
        for (Skill s : ALL.values()) if (s.subclass() == sub && !s.isActive()) out.add(s);
        return out;
    }

    /** The skills in a player's data, in learned order (unknown ids are skipped). */
    public static List<Skill> known(SkillData data) {
        List<Skill> out = new ArrayList<>();
        for (String path : data.known().keySet()) {
            Skill s = ALL.get(path);
            if (s != null) out.add(s);
        }
        return out;
    }

    // ---------------------------------------------------------------- builders

    private static Object[] a(Object... args) {
        return args;
    }

    private static Skill active(String path, @Nullable PlayerClass owner, PlayerRank grade, int unlock, int color,
                                float mana, int cooldown, int cooldownAtMax, Skill.Action action, Skill.Args args) {
        return add(new Skill(path, Skill.Kind.ACTIVE, owner, grade, unlock, false, color, mana, cooldown, cooldownAtMax, action, args));
    }

    private static Skill advanced(String path, PlayerClass owner, PlayerRank grade, int color,
                                  float mana, int cooldown, int cooldownAtMax, Skill.Action action, Skill.Args args) {
        return add(new Skill(path, Skill.Kind.ACTIVE, owner, grade, -1, true, color, mana, cooldown, cooldownAtMax, action, args));
    }

    private static Skill passive(String path, @Nullable PlayerClass owner, PlayerRank grade, int unlock, int color, Skill.Args args) {
        return add(new Skill(path, Skill.Kind.PASSIVE, owner, grade, unlock, false, color, 0, 0, 0, null, args));
    }

    static Skill add(Skill s) {
        ALL.put(s.path(), s);
        return s;
    }

    /** Touch the class so every skill is registered. */
    public static void init() {
        SubclassSkills.init();
        RuneArts.init();
    }

    private Skills() {}
}
