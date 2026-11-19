package com.beyondsolo.reward;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Supplier;

/** Random boxes (config/beyondsolo-boxes.toml). Numbers are first guesses for the balancing pass. */
public final class RewardConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DAILY_BOX = B
            .comment("The daily training pays a Random Box of the hunter's rank on top of its XP and stat point.")
            .define("boxes.dailyBox", true);
    public static final ModConfigSpec.DoubleValue BOSS_CHANCE = B
            .comment("Chance a gate boss's reward cache holds a Random Box of the gate's rank.")
            .defineInRange("boxes.bossChance", 0.25, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue ASCENDED_CHANCE = B
            .comment("Chance an ascended boss's cache holds a Blessed Random Box (instead of the plain chance).")
            .defineInRange("boxes.ascendedBlessedChance", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue HIDDEN_CHANCE = B
            .comment("Chance a hidden chamber's cache holds a Blessed Random Box (otherwise the plain chance, doubled).")
            .defineInRange("boxes.hiddenBlessedChance", 0.3, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue RED_CHANCE = B
            .comment("Chance a red gate's cache holds a Cursed Random Box.")
            .defineInRange("boxes.redCursedChance", 0.6, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue SECRET_BOX = B
            .comment("Secret quests (brave or unusual deeds) pay a Blessed Random Box too.")
            .define("boxes.secretBlessed", true);
    public static final ModConfigSpec.DoubleValue GREED_SHARE = B
            .comment("Curse of Greed: the share of the hunter's gold a cursed box swallows.")
            .defineInRange("boxes.greedShare", 0.1, 0.0, 1.0);
    public static final ModConfigSpec.IntValue FRAILTY_MINUTES = B
            .comment("Curse of Frailty: how long the hex lasts.")
            .defineInRange("boxes.frailtyMinutes", 10, 1, 120);
    public static final ModConfigSpec.IntValue BACKLASH = B
            .comment("Curse of Backlash: how much mana poisoning (of 100) the box's mana leaves in the hunter.")
            .defineInRange("boxes.backlash", 45, 0, 100);
    public static final ModConfigSpec.IntValue WARDEN_SECONDS = B
            .comment("Curse of the Wardens: seconds to put the box's wardens down before what it holds crumbles.")
            .defineInRange("boxes.wardenSeconds", 120, 20, 1200);

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

    public static boolean dailyBox() { return get(DAILY_BOX::get, true); }
    public static double bossChance() { return get(BOSS_CHANCE::get, 0.25); }
    public static double ascendedChance() { return get(ASCENDED_CHANCE::get, 0.35); }
    public static double hiddenChance() { return get(HIDDEN_CHANCE::get, 0.3); }
    public static double redChance() { return get(RED_CHANCE::get, 0.6); }
    public static boolean secretBox() { return get(SECRET_BOX::get, true); }
    public static double greedShare() { return get(GREED_SHARE::get, 0.1); }
    public static int frailtyTicks() { return get(FRAILTY_MINUTES::get, 10) * 1200; }
    public static int backlash() { return get(BACKLASH::get, 45); }
    public static int wardenTicks() { return get(WARDEN_SECONDS::get, 120) * 20; }

    private RewardConfig() {}
}
