package com.beyondsolo.story;

import com.beyondsolo.system.PlayerRank;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Supplier;

/** The hunter's own story (config/beyondsolo-story.toml). Numbers are first guesses for the balancing pass. */
public final class StoryConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("The Long Sleep: the hunter's mother sleeps in a village bed, and the System's story quests lead to her cure.")
            .define("story.longSleep", true);
    public static final ModConfigSpec.IntValue VISIT_LEVEL = B
            .comment("The System level at which the hunter is sent to visit her (the chain's first quest).")
            .defineInRange("story.visitLevel", 5, 1, 100);
    public static final ModConfigSpec.IntValue CURE_LEVEL = B
            .comment("The System level at which the System finds the cure's recipe (the second quest).")
            .defineInRange("story.cureLevel", 25, 1, 100);
    public static final ModConfigSpec.IntValue SOULS = B
            .comment("Souls the cure needs, gathered from the creatures of the Crimson Citadel.")
            .defineInRange("story.souls", 1000, 1, 100000);
    public static final ModConfigSpec.IntValue KEEPER_SOULS = B
            .comment("Souls a gatekeeper (or any boss of the Citadel) is worth; every other creature is worth one.")
            .defineInRange("story.keeperSouls", 10, 1, 1000);
    public static final ModConfigSpec.IntValue LORD_SOULS = B
            .comment("Souls the Citadel's lord is worth.")
            .defineInRange("story.lordSouls", 50, 1, 10000);
    public static final ModConfigSpec.ConfigValue<String> DEW_RANK = B
            .comment("The lowest gate rank whose hidden chamber can hold Elder Root Dew (E, D, C, B, A or S).")
            .define("story.dewRank", "B");
    public static final ModConfigSpec.IntValue BED_REACH = B
            .comment("How far (blocks) from the village's Evaluation Zone a bed for her is looked for before one is set up.")
            .defineInRange("story.bedReach", 48, 8, 128);

    public static final ModConfigSpec SPEC = B.build();

    // safe reads: the config is not loaded in every context (early game tests)

    private static <T> T get(Supplier<T> value, T fallback) {
        try {
            T v = value.get();
            return v == null ? fallback : v;
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static boolean enabled() { return get(ENABLED::get, true); }
    public static int visitLevel() { return get(VISIT_LEVEL::get, 5); }
    public static int cureLevel() { return get(CURE_LEVEL::get, 25); }
    public static int souls() { return get(SOULS::get, 1000); }
    public static int keeperSouls() { return get(KEEPER_SOULS::get, 10); }
    public static int lordSouls() { return get(LORD_SOULS::get, 50); }
    public static int bedReach() { return get(BED_REACH::get, 48); }

    public static PlayerRank dewRank() {
        String r = get(DEW_RANK::get, "B");
        for (PlayerRank rank : PlayerRank.values()) if (rank.name().equalsIgnoreCase(r)) return rank;
        return PlayerRank.B;
    }

    private StoryConfig() {}
}
