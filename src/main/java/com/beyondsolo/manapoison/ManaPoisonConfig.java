package com.beyondsolo.manapoison;

import com.beyondsolo.system.PlayerRank;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.function.Supplier;

/** Mana poisoning (config/beyondsolo-mana.toml). Numbers are first guesses for the balancing pass. */
public final class ManaPoisonConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("High-rank gates are thick with mana, and it poisons hunters who stay in them too long.")
            .define("mana.enabled", true);
    public static final ModConfigSpec.EnumValue<PlayerRank> MIN_GATE_RANK = B
            .comment("The lowest gate rank whose mana is thick enough to poison.")
            .defineEnum("mana.minGateRank", PlayerRank.A);
    public static final ModConfigSpec.DoubleValue MATCHED_RATE = B
            .comment("Poisoning a second (of 100) in a gate of the hunter's own rank.")
            .defineInRange("mana.matchedRate", 0.05, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue ABOVE_RATE = B
            .comment("Poisoning a second in a gate one rank above the hunter.")
            .defineInRange("mana.aboveRate", 0.25, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue FAR_ABOVE_RATE = B
            .comment("Poisoning a second in a gate two or more ranks above the hunter.")
            .defineInRange("mana.farAboveRate", 0.6, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue S_GATE_MULTIPLIER = B
            .comment("S-rank gates' mana is thicker still: their rates are multiplied by this.")
            .defineInRange("mana.sGateMultiplier", 1.3, 0.0, 10.0);
    public static final ModConfigSpec.IntValue INT_PER_PERCENT = B
            .comment("Intelligence above the base that shields 1% of the poisoning (a mind that knows mana holds it off).")
            .defineInRange("mana.intPerPercent", 4, 1, 100);
    public static final ModConfigSpec.DoubleValue INT_CAP = B
            .comment("The most intelligence can shield.")
            .defineInRange("mana.intCap", 0.4, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DECAY = B
            .comment("Poisoning that leaves a second outside thick mana (and once a gate's boss is down).")
            .defineInRange("mana.decay", 1.0, 0.0, 100.0);
    public static final ModConfigSpec.DoubleValue CRYSTAL_PURIFY = B
            .comment("Poisoning a purifying crystal draws out a second from a hunter resting by it.")
            .defineInRange("mana.crystalPurify", 6.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue CRYSTAL_RADIUS = B
            .comment("How close a hunter must stand to a purifying crystal, in blocks.")
            .defineInRange("mana.crystalRadius", 4, 1, 16);
    public static final ModConfigSpec.IntValue CRYSTALS_A = B
            .comment("Purifying crystals grown into an A-rank gate's caves.")
            .defineInRange("mana.crystalsA", 2, 0, 8);
    public static final ModConfigSpec.IntValue CRYSTALS_S = B
            .comment("Purifying crystals grown into an S-rank gate's caves.")
            .defineInRange("mana.crystalsS", 3, 0, 8);
    public static final ModConfigSpec.DoubleValue DRAIN_POISONED = B
            .comment("Mana Poisoning (60+): the share of max health it eats every 3 seconds.")
            .defineInRange("mana.drainPoisoned", 0.02, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue DRAIN_COLLAPSE = B
            .comment("Mana Collapse (90+): the share of max health it eats every 3 seconds.")
            .defineInRange("mana.drainCollapse", 0.04, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BURST = B
            .comment("Mana Burst (100): the share of max health it tears away before the poisoning falls back to 75.")
            .defineInRange("mana.burst", 0.2, 0.0, 1.0);
    public static final ModConfigSpec.IntValue DRAUGHT = B
            .comment("Poisoning a purifying draught washes out.")
            .defineInRange("mana.draught", 50, 0, 100);
    public static final ModConfigSpec.IntValue CLEAR_BREATH_SECONDS = B
            .comment("How long a purifying draught keeps the mana out (Clear Breath).")
            .defineInRange("mana.clearBreathSeconds", 120, 0, 3600);

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
    public static PlayerRank minGateRank() { return get(MIN_GATE_RANK::get, PlayerRank.A); }
    public static double matchedRate() { return get(MATCHED_RATE::get, 0.05); }
    public static double aboveRate() { return get(ABOVE_RATE::get, 0.25); }
    public static double farAboveRate() { return get(FAR_ABOVE_RATE::get, 0.6); }
    public static double sGateMultiplier() { return get(S_GATE_MULTIPLIER::get, 1.3); }
    public static int intPerPercent() { return get(INT_PER_PERCENT::get, 4); }
    public static double intCap() { return get(INT_CAP::get, 0.4); }
    public static double decay() { return get(DECAY::get, 1.0); }
    public static double crystalPurify() { return get(CRYSTAL_PURIFY::get, 6.0); }
    public static int crystalRadius() { return get(CRYSTAL_RADIUS::get, 4); }
    public static int crystals(PlayerRank gate) { return gate == PlayerRank.S ? get(CRYSTALS_S::get, 3) : get(CRYSTALS_A::get, 2); }
    public static double drainPoisoned() { return get(DRAIN_POISONED::get, 0.02); }
    public static double drainCollapse() { return get(DRAIN_COLLAPSE::get, 0.04); }
    public static double burst() { return get(BURST::get, 0.2); }
    public static int draught() { return get(DRAUGHT::get, 50); }
    public static int clearBreathTicks() { return get(CLEAR_BREATH_SECONDS::get, 120) * 20; }

    private ManaPoisonConfig() {}
}
