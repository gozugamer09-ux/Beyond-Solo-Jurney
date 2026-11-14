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
            .comment("Blood buildup per hit from beyondsolo:element_attackers/blood mobs (100 = blood pop, 10% max health).")
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

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
