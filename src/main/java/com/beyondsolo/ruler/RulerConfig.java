package com.beyondsolo.ruler;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The rulers of light (config/beyondsolo-rulers.toml). Numbers are first guesses for the balancing pass. */
public final class RulerConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("Rulers come on their own: to make war on a monarch that lands, and to pass sentence on crowned hunters.",
                    "Off: they only come when called (commands).")
            .define("rulers.enabled", true);
    public static final ModConfigSpec.DoubleValue WAR_CHANCE = B
            .comment("The chance a ruler comes down to make war on a monarch once it has landed.")
            .defineInRange("rulers.warChance", 0.4, 0.0, 1.0);
    public static final ModConfigSpec.IntValue DESCENT_SECONDS = B
            .comment("Seconds between a rift of light opening in the sky and its ruler coming down through it.")
            .defineInRange("rulers.descentSeconds", 30, 5, 600);
    public static final ModConfigSpec.BooleanValue SENTENCES = B
            .comment("Rulers come to pass sentence on hunters who took a monarch's crown.")
            .define("rulers.sentences", true);
    public static final ModConfigSpec.IntValue FIRST_SENTENCE_MINUTES = B
            .comment("Minutes after a crowned hunter is first in the overworld before the first sentence.")
            .defineInRange("rulers.firstSentenceMinutes", 30, 1, 10000);
    public static final ModConfigSpec.IntValue SENTENCE_MINUTES = B
            .comment("Minutes between one sentence ending and the next (give or take a quarter).")
            .defineInRange("rulers.sentenceMinutes", 120, 5, 10000);
    public static final ModConfigSpec.IntValue ABANDON_MINUTES = B
            .comment("A ruler no hunter comes near or fights for this long rises back into the sky.")
            .defineInRange("rulers.abandonMinutes", 8, 1, 1000);
    public static final ModConfigSpec.IntValue BLESSING_MINUTES = B
            .comment("How long the Blessing of Light lasts: given by a ruler to the hunters who fought a monarch at its side.")
            .defineInRange("rulers.blessingMinutes", 20, 1, 1000);
    public static final ModConfigSpec.DoubleValue BLESSING_MIGHT = B
            .comment("How much harder a blessed hunter's blows land on monarchs and their armies (0.15: 15% harder).")
            .defineInRange("rulers.blessingMight", 0.15, 0.0, 10.0);

    public static final ModConfigSpec SPEC = B.build();

    // safe reads: the config is not loaded in every context (early game tests)

    static boolean enabled() {
        try {
            return ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    static double warChance() {
        try {
            return WAR_CHANCE.get();
        } catch (IllegalStateException e) {
            return 0.4;
        }
    }

    public static int descentTicks() {
        try {
            return DESCENT_SECONDS.get() * 20;
        } catch (IllegalStateException e) {
            return 30 * 20;
        }
    }

    static boolean sentences() {
        try {
            return SENTENCES.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    static int firstSentenceTicks() {
        try {
            return FIRST_SENTENCE_MINUTES.get() * 1200;
        } catch (IllegalStateException e) {
            return 30 * 1200;
        }
    }

    static int sentenceTicks() {
        try {
            return SENTENCE_MINUTES.get() * 1200;
        } catch (IllegalStateException e) {
            return 120 * 1200;
        }
    }

    static int abandonTicks() {
        try {
            return ABANDON_MINUTES.get() * 1200;
        } catch (IllegalStateException e) {
            return 8 * 1200;
        }
    }

    public static int blessingTicks() {
        try {
            return BLESSING_MINUTES.get() * 1200;
        } catch (IllegalStateException e) {
            return 20 * 1200;
        }
    }

    public static float blessingMight() {
        try {
            return BLESSING_MIGHT.get().floatValue();
        } catch (IllegalStateException e) {
            return 0.15f;
        }
    }

    private RulerConfig() {}
}
