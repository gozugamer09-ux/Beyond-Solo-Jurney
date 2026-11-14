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
 * Every skill in the game. Each class has two actives and a passive that unlock with levels, plus one advanced
 * skill for the job change; everyone shares two actives and two passives.
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

    // ---------------------------------------------------------------- assassin
    public static final Skill SHADOWSTEP = active("shadowstep", PlayerClass.ASSASSIN, PlayerRank.E, 1, 0xA77BFF, 12, 160, 80,
            SkillActions::shadowstep, (l, t) -> a(f(SkillActions.shadowstepBonus(l))));
    public static final Skill VENOM_EDGE = active("venom_edge", PlayerClass.ASSASSIN, PlayerRank.C, 6, 0x8FE04A, 15, 500, 300,
            SkillActions::venomEdge, (l, t) -> a(f(10 + l), f(14 + 2 * l)));
    public static final Skill KEEN_INSTINCT = passive("keen_instinct", PlayerClass.ASSASSIN, PlayerRank.C, 10, 0xC0A0FF,
            (l, t) -> a(f(2 * l)));
    public static final Skill VEIL_OF_NIGHT = advanced("veil_of_night", PlayerClass.ASSASSIN, PlayerRank.A, 0x6A4FD0, 30, 800, 500,
            SkillActions::veilOfNight, (l, t) -> a(f(SkillActions.veilSeconds(l)), f(60 + 5 * l)));

    // ---------------------------------------------------------------- mage
    public static final Skill ARC_BOLT = active("arc_bolt", PlayerClass.MAGE, PlayerRank.E, 1, 0x66CCFF, 10, 50, 30,
            SkillActions::arcBolt, (l, t) -> a(f(7 * mult(l, t))));
    public static final Skill EMBER_BURST = active("ember_burst", PlayerClass.MAGE, PlayerRank.C, 6, 0xFF8A2E, 22, 280, 160,
            SkillActions::emberBurst, (l, t) -> a(f(5 * mult(l, t)), f(30 + 4 * l)));
    public static final Skill ARCANE_FONT = passive("arcane_font", PlayerClass.MAGE, PlayerRank.C, 10, 0x7FA8FF,
            (l, t) -> a(f(8 * l)));
    public static final Skill STARFALL = advanced("starfall", PlayerClass.MAGE, PlayerRank.A, 0xFFB45A, 40, 700, 420,
            SkillActions::starfall, (l, t) -> a(f(SkillActions.meteors(l)), f(8 * mult(l, t))));

    // ---------------------------------------------------------------- guardian
    public static final Skill BULWARK = active("bulwark", PlayerClass.GUARDIAN, PlayerRank.E, 1, 0xFFD36A, 12, 300, 180,
            SkillActions::bulwark, (l, t) -> a(f(SkillActions.bulwarkShield(l))));
    public static final Skill PROVOKE = active("provoke", PlayerClass.GUARDIAN, PlayerRank.C, 6, 0xF0B040, 15, 400, 240,
            SkillActions::provoke, (l, t) -> a(f(10)));
    public static final Skill STONESKIN = passive("stoneskin", PlayerClass.GUARDIAN, PlayerRank.C, 10, 0xC9A35A,
            (l, t) -> a(f(0.5f * l), f(4 * l)));
    public static final Skill AEGIS_DOME = advanced("aegis_dome", PlayerClass.GUARDIAN, PlayerRank.A, 0xFFE08A, 35, 900, 600,
            SkillActions::aegisDome, (l, t) -> a(f(SkillActions.aegisSeconds(l))));

    // ---------------------------------------------------------------- healer
    public static final Skill MENDING_LIGHT = active("mending_light", PlayerClass.HEALER, PlayerRank.E, 1, 0x8CFFB0, 14, 160, 100,
            SkillActions::mendingLight, (l, t) -> a(f(5 * mult(l, t))));
    public static final Skill PURIFY = active("purify", PlayerClass.HEALER, PlayerRank.C, 6, 0xE8FFF4, 16, 400, 240,
            SkillActions::purify, (l, t) -> a());
    public static final Skill LIFE_BLOOM = passive("life_bloom", PlayerClass.HEALER, PlayerRank.C, 10, 0x7CE09B,
            (l, t) -> a(f(SkillActions.lifeBloom(l))));
    public static final Skill SANCTUARY = advanced("sanctuary", PlayerClass.HEALER, PlayerRank.A, 0xC8FF9A, 40, 1000, 700,
            SkillActions::sanctuary, (l, t) -> a(f(SkillActions.sanctuaryHeal(l))));

    // ---------------------------------------------------------------- ranger
    public static final Skill PIERCING_SHOT = active("piercing_shot", PlayerClass.RANGER, PlayerRank.E, 1, 0xC6F06A, 10, 80, 40,
            SkillActions::piercingShot, (l, t) -> a(f(6 * mult(l, t))));
    public static final Skill HUNTERS_MARK = active("hunters_mark", PlayerClass.RANGER, PlayerRank.C, 6, 0xFF6A6A, 14, 400, 240,
            SkillActions::huntersMark, (l, t) -> a(f(SkillActions.markBonus(l))));
    public static final Skill EAGLE_EYE = passive("eagle_eye", PlayerClass.RANGER, PlayerRank.C, 10, 0x9BD06A,
            (l, t) -> a(f(4 * l)));
    public static final Skill ARROW_RAIN = advanced("arrow_rain", PlayerClass.RANGER, PlayerRank.A, 0xB8F07A, 38, 800, 500,
            SkillActions::arrowRain, (l, t) -> a(f(SkillActions.rainArrows(l))));


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
    }

    // ---------------------------------------------------------------- lookups

    public static @Nullable Skill get(String path) {
        return ALL.get(path);
    }

    public static List<Skill> all() {
        return Collections.unmodifiableList(new ArrayList<>(ALL.values()));
    }

    /** Skills a class can ever hold: the shared ones and its own, advanced last. */
    public static List<Skill> forClass(PlayerClass c) {
        List<Skill> out = new ArrayList<>();
        for (Skill s : ALL.values()) if (s.fits(c) && !s.advanced()) out.add(s);
        for (Skill s : ALL.values()) if (s.fits(c) && s.advanced() && s.subclass() == null) out.add(s);
        return out;
    }

    /** The base class's advanced skills (not the subclass ones). */
    public static List<Skill> advancedFor(PlayerClass c) {
        List<Skill> out = new ArrayList<>();
        for (Skill s : ALL.values()) if (s.owner() == c && s.advanced() && s.subclass() == null) out.add(s);
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
    }

    private Skills() {}
}
