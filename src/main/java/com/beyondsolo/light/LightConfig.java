package com.beyondsolo.light;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The light path's settings (config/beyondsolo-light.toml). First guesses for the final balancing pass: a ruler of
 * light should stand where a monarch stands, far above every other hunter and still someone a monarch can beat.
 */
public final class LightConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CHAPEL = B
            .comment("Whether new awakening temples hide the chapel of the Envoy of Dawn (the secret way to wake sworn to the light).")
            .define("light.chapel", true);
    public static final ModConfigSpec.IntValue JUDGEMENT_LEVEL = B
            .comment("Player level at which a hunter sworn to the light learns Judgement (1: as they wake).")
            .defineInRange("light.judgementLevel", 1, 1, 999);
    public static final ModConfigSpec.IntValue SERAPH_LEVEL = B
            .comment("Player level at which a hunter sworn to the light learns Seraph Form.")
            .defineInRange("light.seraphLevel", 45, 1, 999);
    public static final ModConfigSpec.IntValue MIN_LEVEL = B
            .comment("Player level at which the light calls a hunter sworn to it (who has changed jobs) to the Investiture.")
            .defineInRange("light.minLevel", 80, 1, 999);
    public static final ModConfigSpec.IntValue RETRY_MINUTES = B
            .comment("Real minutes before a failed vigil can be kept again.")
            .defineInRange("light.retryMinutes", 20, 0, 10_000);
    public static final ModConfigSpec.IntValue CHOICE_SECONDS = B
            .comment("Seconds the Envoy waits for the hunter to take up a mantle before the light picks the one their class leans to.")
            .defineInRange("light.choiceSeconds", 90, 5, 3600);
    public static final ModConfigSpec.DoubleValue HEALTH_BONUS = B
            .comment("A ruler of light's extra max health once invested, as a fraction of their whole pool.")
            .defineInRange("light.healthBonus", 0.25, 0, 10);
    public static final ModConfigSpec.DoubleValue DAMAGE_BONUS = B
            .comment("A ruler of light's extra attack damage once invested, as a fraction.")
            .defineInRange("light.damageBonus", 0.12, 0, 10);
    public static final ModConfigSpec.DoubleValue MANA_BONUS = B
            .comment("A ruler of light's extra mana once invested (a hunter only sworn gets a third of it).")
            .defineInRange("light.manaBonus", 150.0, 0, 100_000);
    public static final ModConfigSpec.DoubleValue VERSUS_MONARCH = B
            .comment("Damage multiplier the blows of a hunter sworn to the light get against the monarchs from beyond the gates.")
            .defineInRange("light.versusMonarch", 1.2, 1, 10);
    public static final ModConfigSpec.IntValue SANCTUM_RADIUS = B
            .comment("Radius of a ruler of light's sanctum.")
            .defineInRange("light.sanctumRadius", 14, 4, 64);

    public static final ModConfigSpec SPEC = B.build();

    public static boolean chapel() { return get(CHAPEL); }
    public static int judgementLevel() { return get(JUDGEMENT_LEVEL); }
    public static int seraphLevel() { return get(SERAPH_LEVEL); }
    public static int minLevel() { return get(MIN_LEVEL); }
    public static int retryMinutes() { return get(RETRY_MINUTES); }
    public static int choiceSeconds() { return get(CHOICE_SECONDS); }
    public static double healthBonus() { return get(HEALTH_BONUS); }
    public static double damageBonus() { return get(DAMAGE_BONUS); }
    public static double manaBonus() { return get(MANA_BONUS); }
    public static double versusMonarch() { return get(VERSUS_MONARCH); }
    public static int sanctumRadius() { return get(SANCTUM_RADIUS); }

    /** A setting's value, or its default before the settings are read (a fresh client, a test before load). */
    static int get(ModConfigSpec.IntValue v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }

    static double get(ModConfigSpec.DoubleValue v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }

    static boolean get(ModConfigSpec.BooleanValue v) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return v.getDefault();
        }
    }

    private LightConfig() {}
}
