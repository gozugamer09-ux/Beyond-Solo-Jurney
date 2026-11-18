package com.beyondsolo.monarch;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The monarch path's settings (config/beyondsolo-monarch.toml). First guesses for the final balancing pass: a monarch
 * should feel far above every other hunter and still be something another monarch can beat.
 */
public final class MonarchConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MIN_LEVEL = B
            .comment("Player level at which the Sigil of Dominion calls its holder to the throne.")
            .defineInRange("monarch.minLevel", 80, 1, 999);
    public static final ModConfigSpec.IntValue RETRY_MINUTES = B
            .comment("Real minutes before a failed throne trial can be tried again.")
            .defineInRange("monarch.retryMinutes", 20, 0, 10_000);
    public static final ModConfigSpec.IntValue CHOICE_SECONDS = B
            .comment("Seconds the throne waits for the hunter to pick a crown before the System picks the one their class leans to.")
            .defineInRange("monarch.choiceSeconds", 90, 5, 3600);
    public static final ModConfigSpec.DoubleValue HEALTH_BONUS = B
            .comment("A monarch's extra max health, as a fraction of their whole pool.")
            .defineInRange("monarch.healthBonus", 0.25, 0, 10);
    public static final ModConfigSpec.DoubleValue DAMAGE_BONUS = B
            .comment("A monarch's extra attack damage, as a fraction.")
            .defineInRange("monarch.damageBonus", 0.12, 0, 10);
    public static final ModConfigSpec.DoubleValue MANA_BONUS = B
            .comment("A monarch's extra mana.")
            .defineInRange("monarch.manaBonus", 150.0, 0, 100_000);
    public static final ModConfigSpec.DoubleValue VERSUS_MONARCH = B
            .comment("Damage multiplier a monarch's blows get against the monarchs from beyond the gates (one crown knows another).")
            .defineInRange("monarch.versusMonarch", 1.2, 1, 10);
    public static final ModConfigSpec.IntValue DOMAIN_RADIUS = B
            .comment("Radius of a monarch's domain.")
            .defineInRange("monarch.domainRadius", 16, 4, 64);

    public static final ModConfigSpec SPEC = B.build();

    private MonarchConfig() {}
}
