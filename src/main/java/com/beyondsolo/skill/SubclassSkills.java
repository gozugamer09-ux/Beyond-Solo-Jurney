package com.beyondsolo.skill;

import com.beyondsolo.job.Subclass;
import com.beyondsolo.system.PlayerRank;

import static com.beyondsolo.skill.SkillActions.f;
import static com.beyondsolo.skill.SubclassActions.*;
import static com.beyondsolo.skill.SubclassPassives.*;

/**
 * The starter skills of the 30 subclasses, learned at the job change: one signature active and one passive each,
 * and a second active (an ultimate) for the five rare subclasses. Later skill batches add more per subclass.
 *
 * <p>Every skill takes its subclass's element. Rare subclass skills are S grade and hit several times harder.</p>
 */
public final class SubclassSkills {
    // ================================================================ warrior
    public static final Skill BLOOD_FRENZY = active("blood_frenzy", Subclass.BLOODRAGER, PlayerRank.A, 18, 700, 420,
            SubclassActions::bloodFrenzy, (l, t) -> a(f(frenzySeconds(l)), l >= 6 ? "II" : "I", f(frenzyLifesteal(l))));
    public static final Skill RAGE_ENGINE = passive("rage_engine", Subclass.BLOODRAGER, PlayerRank.A, (l, t) -> a(f(rageBonus(l))));

    public static final Skill BANNER_OF_CONQUEST = active("banner_of_conquest", Subclass.WARLORD, PlayerRank.A, 24, 900, 600,
            SubclassActions::bannerOfConquest, (l, t) -> a(f(bannerSeconds(l))));
    public static final Skill COMMANDING_PRESENCE = passive("commanding_presence", Subclass.WARLORD, PlayerRank.A, (l, t) -> a(f(l), f(0.3f * l)));

    public static final Skill THOUSAND_CUTS = active("thousand_cuts", Subclass.BLADEMASTER, PlayerRank.A, 20, 240, 120,
            SubclassActions::thousandCuts, (l, t) -> a(f(cutDamage(l, t))));
    public static final Skill FLOWING_BLADE = passive("flowing_blade", Subclass.BLADEMASTER, PlayerRank.A, (l, t) -> a(f(3 * l)));

    public static final Skill UNSTOPPABLE_CHARGE = active("unstoppable_charge", Subclass.JUGGERNAUT, PlayerRank.A, 18, 280, 160,
            SubclassActions::unstoppableCharge, (l, t) -> a(f(chargeDistance(l)), f(chargeDamage(l, t))));
    public static final Skill IRON_HIDE = passive("iron_hide", Subclass.JUGGERNAUT, PlayerRank.A, (l, t) -> a(f(0.8f * l), f(4 * l)));

    public static final Skill CATACLYSM_FIST = active("cataclysm_fist", Subclass.WORLDBREAKER, PlayerRank.S, 45, 500, 300,
            SubclassActions::cataclysmFist, (l, t) -> a(f(cataclysmDamage(l, t))));
    public static final Skill FAULT_LINE = active("fault_line", Subclass.WORLDBREAKER, PlayerRank.S, 35, 300, 180,
            SubclassActions::faultLine, (l, t) -> a(f(faultDamage(l, t))));
    public static final Skill TITAN_BLOOD = passive("titan_blood", Subclass.WORLDBREAKER, PlayerRank.S, (l, t) -> a(f(titanBonus(l)), f(1.5f * l)));

    // ================================================================ assassin
    public static final Skill UMBRAL_EXECUTION = active("umbral_execution", Subclass.NIGHTBLADE, PlayerRank.A, 20, 240, 140,
            SubclassActions::umbralExecution, (l, t) -> a(f(executeDamage(l, t))));
    public static final Skill NIGHT_HUNTER = passive("night_hunter", Subclass.NIGHTBLADE, PlayerRank.A, (l, t) -> a(f(nightBonus(l))));

    public static final Skill MIASMA_CLOUD = active("miasma_cloud", Subclass.TOXIN_WEAVER, PlayerRank.A, 22, 400, 240,
            SubclassActions::miasmaCloud, (l, t) -> a(f(miasmaSeconds(l)), f(miasmaDamage(l, t))));
    public static final Skill VIRULENCE = passive("virulence", Subclass.TOXIN_WEAVER, PlayerRank.A, (l, t) -> a(f(virulence(l))));

    public static final Skill MIRAGE_FLURRY = active("mirage_flurry", Subclass.MIRAGE_DANCER, PlayerRank.A, 24, 320, 180,
            SubclassActions::mirageFlurry, (l, t) -> a(f(flurryTargets(l)), f(flurryDamage(l, t))));
    public static final Skill EVASIVE_STEP = passive("evasive_step", Subclass.MIRAGE_DANCER, PlayerRank.A, (l, t) -> a(f(evasion(l))));

    public static final Skill DEATH_SENTENCE = active("death_sentence", Subclass.BOUNTY_REAPER, PlayerRank.A, 16, 400, 240,
            SubclassActions::deathSentence, (l, t) -> a(f(sentenceBonus(l))));
    public static final Skill OPENING_STRIKE = passive("opening_strike", Subclass.BOUNTY_REAPER, PlayerRank.A, (l, t) -> a(f(openingBonus(l))));

    public static final Skill TOTAL_ECLIPSE = active("total_eclipse", Subclass.ECLIPSE_SOVEREIGN, PlayerRank.S, 50, 1200, 800,
            SubclassActions::totalEclipse, (l, t) -> a(f(eclipseDamage(l, t))));
    public static final Skill VOID_REND = active("void_rend", Subclass.ECLIPSE_SOVEREIGN, PlayerRank.S, 40, 500, 300,
            SubclassActions::voidRend, (l, t) -> a(f(voidRendDamage(l, t))));
    public static final Skill SOVEREIGN_OF_DUSK = passive("sovereign_of_dusk", Subclass.ECLIPSE_SOVEREIGN, PlayerRank.S, (l, t) -> a(f(duskBonus(l)), f(2 * l)));

    // ================================================================ mage
    public static final Skill INFERNO_PILLAR = active("inferno_pillar", Subclass.PYROMANCER, PlayerRank.A, 26, 200, 110,
            SubclassActions::infernoPillar, (l, t) -> a(f(pillarDamage(l, t))));
    public static final Skill KINDLED_SOUL = passive("kindled_soul", Subclass.PYROMANCER, PlayerRank.A, (l, t) -> a(f(kindle(l))));

    public static final Skill CHAIN_LIGHTNING = active("chain_lightning", Subclass.STORMCALLER, PlayerRank.A, 24, 160, 90,
            SubclassActions::chainLightning, (l, t) -> a(f(chainDamage(l, t)), f(chainJumps(l))));
    public static final Skill STATIC_CHARGE = passive("static_charge", Subclass.STORMCALLER, PlayerRank.A, (l, t) -> a(f(staticRegen(l))));

    public static final Skill TIDAL_CRASH = active("tidal_crash", Subclass.TIDECALLER, PlayerRank.A, 22, 220, 120,
            SubclassActions::tidalCrash, (l, t) -> a(f(tideDamage(l, t))));
    public static final Skill DEEP_CURRENT = passive("deep_current", Subclass.TIDECALLER, PlayerRank.A, (l, t) -> a(f(deepMana(l))));

    public static final Skill SOUL_HARVEST = active("soul_harvest", Subclass.NECROMANCER, PlayerRank.A, 24, 300, 180,
            SubclassActions::soulHarvest, (l, t) -> a(f(harvestDamage(l, t)), f(harvestHeal(l))));
    public static final Skill GRAVE_PACT = passive("grave_pact", Subclass.NECROMANCER, PlayerRank.A, (l, t) -> a(f(pactHeal(l)), f(pactMana(l))));

    public static final Skill ASTRAL_CATACLYSM = active("astral_cataclysm", Subclass.STARBORN_ARCHON, PlayerRank.S, 60, 900, 540,
            SubclassActions::astralCataclysm, (l, t) -> a(f(astralMeteors(l)), f(astralDamage(l, t))));
    public static final Skill SINGULARITY = active("singularity", Subclass.STARBORN_ARCHON, PlayerRank.S, 45, 600, 360,
            SubclassActions::singularity, (l, t) -> a(f(singularityDamage(l, t))));
    public static final Skill CELESTIAL_CORE = passive("celestial_core", Subclass.STARBORN_ARCHON, PlayerRank.S, (l, t) -> a(f(celestialMana(l)), f(celestialBonus(l))));

    // ================================================================ guardian
    public static final Skill HOLY_JUDGMENT = active("holy_judgment", Subclass.PALADIN, PlayerRank.A, 20, 200, 120,
            SubclassActions::holyJudgment, (l, t) -> a(f(judgmentDamage(l, t))));
    public static final Skill DEVOTION = passive("devotion", Subclass.PALADIN, PlayerRank.A, (l, t) -> a(f(0.4f * l), f(devotionRegen(l))));

    public static final Skill FORTRESS_STANCE = active("fortress_stance", Subclass.IRONWALL_SENTINEL, PlayerRank.A, 18, 600, 360,
            SubclassActions::fortressStance, (l, t) -> a(f(fortressSeconds(l))));
    public static final Skill UNYIELDING = passive("unyielding", Subclass.IRONWALL_SENTINEL, PlayerRank.A, (l, t) -> a(f(0.5f * l), f(0.3f * l)));

    public static final Skill MANA_BARRIER = active("mana_barrier", Subclass.SPELLWARD_WARDEN, PlayerRank.A, 22, 600, 400,
            SubclassActions::manaBarrier, (l, t) -> a(f(barrierHearts(l))));
    public static final Skill ARCANE_WARD = passive("arcane_ward", Subclass.SPELLWARD_WARDEN, PlayerRank.A, (l, t) -> a(f(wardReduction(l))));

    public static final Skill RETRIBUTION = active("retribution", Subclass.VENGEANCE_KNIGHT, PlayerRank.A, 22, 500, 300,
            SubclassActions::retribution, (l, t) -> a(f(retributionBonus(l, t))));
    public static final Skill BURNING_SPITE = passive("burning_spite", Subclass.VENGEANCE_KNIGHT, PlayerRank.A, (l, t) -> a(f(spite(l))));

    public static final Skill COLOSSUS_FORM = active("colossus_form", Subclass.EVERLASTING_COLOSSUS, PlayerRank.S, 50, 1800, 1200,
            SubclassActions::colossusForm, (l, t) -> a(f(colossusSeconds(l)), f(20 + 2 * l)));
    public static final Skill EARTHEN_DOMAIN = active("earthen_domain", Subclass.EVERLASTING_COLOSSUS, PlayerRank.S, 40, 600, 360,
            SubclassActions::earthenDomain, (l, t) -> a(f(domainDamage(l, t))));
    public static final Skill UNDYING = passive("undying", Subclass.EVERLASTING_COLOSSUS, PlayerRank.S, (l, t) -> a(f(undyingCooldown(l)), f(2 * l)));

    // ================================================================ healer
    public static final Skill RADIANT_BURST = active("radiant_burst", Subclass.LIGHTBINDER, PlayerRank.A, 24, 240, 140,
            SubclassActions::radiantBurst, (l, t) -> a(f(radiantAmount(l, t))));
    public static final Skill BLESSED_AURA = passive("blessed_aura", Subclass.LIGHTBINDER, PlayerRank.A, (l, t) -> a(f(blessedRegen(l))));

    public static final Skill VERDANT_BLOOM = active("verdant_bloom", Subclass.GROVEKEEPER, PlayerRank.A, 26, 500, 300,
            SubclassActions::verdantBloom, (l, t) -> a(f(bloomSeconds(l)), f(bloomHeal(l))));
    public static final Skill PHOTOSYNTHESIS = passive("photosynthesis", Subclass.GROVEKEEPER, PlayerRank.A, (l, t) -> a(f(sunRegen(l))));

    public static final Skill CLEANSING_TIDE = active("cleansing_tide", Subclass.SPRING_ORACLE, PlayerRank.A, 22, 360, 220,
            SubclassActions::cleansingTide, (l, t) -> a(f(tideHeal(l, t))));
    public static final Skill FORESIGHT = passive("foresight", Subclass.SPRING_ORACLE, PlayerRank.A, (l, t) -> a(f(foresightMana(l)), f(foresightDodge(l))));

    public static final Skill BLOOD_SIPHON = active("blood_siphon", Subclass.HEMOMANCER, PlayerRank.A, 18, 160, 90,
            SubclassActions::bloodSiphon, (l, t) -> a(f(siphonDamage(l, t))));
    public static final Skill CRIMSON_VIGOR = passive("crimson_vigor", Subclass.HEMOMANCER, PlayerRank.A, (l, t) -> a(f(l), f(vigorBlood(l))));

    public static final Skill MIRACLE_OF_DAWN = active("miracle_of_dawn", Subclass.DAWN_SAINT, PlayerRank.S, 60, 2400, 1600,
            SubclassActions::miracleOfDawn, (l, t) -> a(f(dawnDamage(l, t))));
    public static final Skill JUDGMENT_OF_DAWN = active("judgment_of_dawn", Subclass.DAWN_SAINT, PlayerRank.S, 45, 500, 300,
            SubclassActions::judgmentOfDawn, (l, t) -> a(f(dawnPillars(l)), f(dawnPillarDamage(l, t))));
    public static final Skill ETERNAL_GRACE = passive("eternal_grace", Subclass.DAWN_SAINT, PlayerRank.S, (l, t) -> a(f(graceRegen(l)), f(undyingCooldown(l))));

    // ================================================================ ranger
    public static final Skill DEADEYE_SHOT = active("deadeye_shot", Subclass.SHARPSHOOTER, PlayerRank.A, 20, 200, 110,
            SubclassActions::deadeyeShot, (l, t) -> a(f(deadeyeDamage(l, t))));
    public static final Skill STEADY_AIM = passive("steady_aim", Subclass.SHARPSHOOTER, PlayerRank.A, (l, t) -> a(f(steadyAim(l))));

    public static final Skill CALL_OF_THE_PACK = active("call_of_the_pack", Subclass.WILDCALLER, PlayerRank.A, 30, 1200, 800,
            SubclassActions::callOfThePack, (l, t) -> a(f(packSize(l))));
    public static final Skill BEAST_BOND = passive("beast_bond", Subclass.WILDCALLER, PlayerRank.A, (l, t) -> a(f(1.5f * l)));

    public static final Skill SNARE_FIELD = active("snare_field", Subclass.SNARE_ARTISAN, PlayerRank.A, 20, 400, 240,
            SubclassActions::snareField, (l, t) -> a(f(snareDamage(l, t))));
    public static final Skill FIELD_CRAFT = passive("field_craft", Subclass.SNARE_ARTISAN, PlayerRank.A, (l, t) -> a(f(fieldCraft(l))));

    public static final Skill THUNDER_VOLLEY = active("thunder_volley", Subclass.STORM_ARCHER, PlayerRank.A, 24, 220, 130,
            SubclassActions::thunderVolley, (l, t) -> a(f(volleyDamage(l, t))));
    public static final Skill CHARGED_QUIVER = passive("charged_quiver", Subclass.STORM_ARCHER, PlayerRank.A, (l, t) -> a(f(quiverBonus(l)), f(quiverSpark(l))));

    public static final Skill HEAVENS_ARROW = active("heavens_arrow", Subclass.HEAVENPIERCER, PlayerRank.S, 45, 400, 240,
            SubclassActions::heavensArrow, (l, t) -> a(f(heavensDamage(l, t))));
    public static final Skill STORM_OF_A_THOUSAND = active("storm_of_a_thousand", Subclass.HEAVENPIERCER, PlayerRank.S, 55, 900, 600,
            SubclassActions::stormOfAThousand, (l, t) -> a(f(stormArrows(l))));
    public static final Skill EAGLE_SOVEREIGN = passive("eagle_sovereign", Subclass.HEAVENPIERCER, PlayerRank.S, (l, t) -> a(f(eagleBonus(l)), f(2 * l)));

    // ================================================================ builders

    private static Object[] a(Object... args) {
        return args;
    }

    private static Skill active(String path, Subclass sub, PlayerRank grade, float mana, int cooldown, int cooldownAtMax,
                                Skill.Action action, Skill.Args args) {
        Skill s = new Skill(path, Skill.Kind.ACTIVE, sub.base(), grade, -1, true, sub.color(), mana, cooldown, cooldownAtMax, action, args);
        return Skills.add(s.element(sub.element()).subclass(sub));
    }

    private static Skill passive(String path, Subclass sub, PlayerRank grade, Skill.Args args) {
        Skill s = new Skill(path, Skill.Kind.PASSIVE, sub.base(), grade, -1, true, sub.color(), 0, 0, 0, null, args);
        return Skills.add(s.element(sub.element()).subclass(sub));
    }

    /** Touch the class so every subclass skill is registered (called from {@link Skills#init()}). */
    static void init() {}

    private SubclassSkills() {}
}
