package com.beyondsolo;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side tuning values. Lives in config/beyondsolo-common.toml. */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    // --- First spawn ---
    public static final ModConfigSpec.BooleanValue RANDOM_SPAWN = B
            .comment("Send first-time players to a random spot in the world.")
            .define("spawn.randomSpawn", true);
    public static final ModConfigSpec.IntValue RANDOM_SPAWN_MIN = B
            .comment("Minimum distance from world spawn for the random first spawn.")
            .defineInRange("spawn.minDistance", 800, 0, 1_000_000);
    public static final ModConfigSpec.IntValue RANDOM_SPAWN_MAX = B
            .comment("Maximum distance from world spawn for the random first spawn.")
            .defineInRange("spawn.maxDistance", 3000, 16, 1_000_000);

    // --- Evaluation zone ---
    public static final ModConfigSpec.IntValue EVALUATION_ZONE_WEIGHT = B
            .comment("How often the Evaluation Zone is picked when a village places a building. Vanilla houses use 1-10. In testing, 3 gives most villages one or two zones.")
            .defineInRange("world.evaluationZoneWeight", 3, 0, 200);
    public static final ModConfigSpec.BooleanValue ANNOUNCE_AWAKENINGS = B
            .comment("Tell everyone on the server in chat when a player finishes their evaluation, with the rank and class they got.")
            .define("evaluation.announceInChat", true);

    // --- Progression ---
    public static final ModConfigSpec.IntValue STAT_POINTS_PER_LEVEL = B
            .comment("Free stat points granted on each level up.")
            .defineInRange("progression.statPointsPerLevel", 3, 0, 100);
    public static final ModConfigSpec.DoubleValue KILL_XP_MULTIPLIER = B
            .comment("System XP from a kill = victim max health * this.")
            .defineInRange("progression.killXpMultiplier", 0.5, 0.0, 100.0);

    // --- Exhaustion ---
    public static final ModConfigSpec.BooleanValue EXHAUSTION_ENABLED = B
            .comment("Enable the exhaustion meter. At 100% the player collapses and cannot act until it wears off, they sleep, or they die.")
            .define("exhaustion.enabled", true);
    public static final ModConfigSpec.DoubleValue EXHAUSTION_PER_SPRINT_SECOND = B
            .defineInRange("exhaustion.perSprintSecond", 0.25, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue EXHAUSTION_PER_ATTACK = B
            .defineInRange("exhaustion.perAttack", 0.4, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue EXHAUSTION_RECOVERY_PER_SECOND = B
            .comment("Recovered per second while not sprinting or fighting (scaled up by VIT).")
            .defineInRange("exhaustion.recoveryPerSecond", 0.5, 0.0, 100.0);
    public static final ModConfigSpec.IntValue COLLAPSE_MIN_SECONDS = B
            .defineInRange("exhaustion.collapseMinSeconds", 30, 1, 3600);
    public static final ModConfigSpec.IntValue COLLAPSE_MAX_SECONDS = B
            .defineInRange("exhaustion.collapseMaxSeconds", 45, 1, 3600);


    // --- Combat ---
    public static final ModConfigSpec.DoubleValue MOB_FIRE_BUILDUP = B
            .comment("Burn buildup per hit from mobs in the entity type tag beyondsolo:element_attackers/fire (100 = deep burn).")
            .defineInRange("combat.mobFireBuildup", 20.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue MOB_POISON_BUILDUP = B
            .comment("Poison buildup per hit from beyondsolo:element_attackers/poison mobs (35 = one venom stack).")
            .defineInRange("combat.mobPoisonBuildup", 18.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue MOB_CRIMSON_BUILDUP = B
            .comment("Crimson buildup per hit from beyondsolo:element_attackers/crimson mobs (100 = crimson burst).")
            .defineInRange("combat.mobCrimsonBuildup", 20.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue MOB_BLOOD_BUILDUP = B
            .comment("Blood buildup per hit from beyondsolo:element_attackers/blood mobs (100 = blood pop: 10% of max health, 5% for bosses and elites).")
            .defineInRange("combat.mobBloodBuildup", 20.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue BURNING_BUILDUP = B
            .comment("Burn buildup each time an entity takes fire damage from burning or fire blocks (lava counts triple).")
            .defineInRange("combat.burningBuildup", 7.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue EXHAUSTION_PER_ROLL = B
            .comment("Exhaustion from each Combat Roll dodge.")
            .defineInRange("exhaustion.perRoll", 2.5, 0.0, 100.0);

    // --- Gates ---
    public static final ModConfigSpec.DoubleValue GATE_SPAWN_CHANCE = B
            .comment("Chance, checked once a minute for each evaluated player in the overworld, that a gate opens 40-80 blocks away (never near another open gate). 0 turns it off; world generation still places gates.")
            .defineInRange("gates.openChancePerMinute", 0.08, 0.0, 1.0);
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends Integer>> GATE_RANK_WEIGHTS = B
            .comment("How often a new gate is each rank, E to S (six weights). A gate's rank is rolled at random wherever it opens and whoever is near: it never follows the player's rank or the distance from spawn. The default makes E-rank gates the most common and S-rank gates rare (4 in 100).")
            .defineList("gates.rankWeights", java.util.List.of(30, 25, 20, 13, 8, 4), () -> 10, o -> o instanceof Integer i && i >= 0);
    public static final ModConfigSpec.IntValue GATE_BREAK_MINUTES = B
            .comment("Minutes (real time, give or take a fifth) an uncleared gate holds before it breaks and its monsters pour out into the world (a dungeon break). Its collapse timer shows for the last third. The clock stops while anyone is inside its cave, and a gate only breaks with someone near to see it (one left alone breaks a minute after someone comes back). 0 turns dungeon breaks off.")
            .defineInRange("gates.breakMinutes", 40, 0, 100_000);

    // --- Awakening ---
    public static final ModConfigSpec.BooleanValue AWAKENING_TRIAL = B
            .comment("New players start without the System and must earn it: their first D-rank (or higher) gate clear opens a sealed gate to the awakening trial. false = everyone has the System from the start (the old onboarding).")
            .define("awakening.required", true);
    public static final ModConfigSpec.IntValue TETHER_RADIUS = B
            .comment("How far (blocks) the sealed gate lets its player walk away before pulling them back.")
            .defineInRange("awakening.tetherRadius", 18, 6, 64);
    public static final ModConfigSpec.DoubleValue PRE_SYSTEM_GATE_CHANCE = B
            .comment("Chance, checked once a minute for each player without the System, that a D-rank gate opens near them (if no gate is open close by). Keeps the first gate findable.")
            .defineInRange("awakening.gateChancePerMinute", 0.12, 0.0, 1.0);

    // --- Re-evaluation ---
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends Integer>> RANK_THRESHOLDS = B
            .comment("Hidden rank thresholds: the total of a player's five stats needed to count as rank D, C, B, A and S. Crossing one is never shown; the player only learns it by being re-evaluated at a crystal.")
            .defineList("reevaluation.rankThresholds", java.util.List.of(90, 140, 210, 300, 420), () -> 100, o -> o instanceof Integer i && i > 0);
    public static final ModConfigSpec.IntValue REEVALUATION_MILESTONE = B
            .comment("Levels per milestone. A player can be re-evaluated once per milestone: at level 10, 20, 30 ... with the default.")
            .defineInRange("reevaluation.milestoneLevels", 10, 1, 999);
    public static final ModConfigSpec.BooleanValue REEVALUATION_HINT = B
            .comment("Give a vague pop-up (it never names the rank) when a player's stats cross a hidden rank threshold.")
            .define("reevaluation.hintOnThreshold", true);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
