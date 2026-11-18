package com.beyondsolo.raid;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Raid settings (config/beyondsolo-raid.toml). Every number is a first guess for the balancing pass. */
public final class RaidConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> NATURAL_CHANCE = B
            .comment("Percent chance a gate that opens on its own is a raid gate, by rank E D C B A S.")
            .defineList("raid.naturalChance", List.of(0, 0, 0, 4, 7, 10), () -> 0, o -> o instanceof Integer i && i >= 0 && i <= 100);
    public static final ModConfigSpec.IntValue MAX_SIZE = B
            .comment("Most raiders in one raid, hired hunters included.")
            .defineInRange("raid.maxSize", 12, 1, 40);
    public static final ModConfigSpec.IntValue MUSTER_RANGE = B
            .comment("How far from its gate a raider may wait for the start (blocks); further, they drop out of the muster.")
            .defineInRange("raid.musterRange", 32, 8, 128);
    public static final ModConfigSpec.IntValue COUNTDOWN_SECONDS = B
            .comment("Seconds from the leader's start to the raiders being taken through together.")
            .defineInRange("raid.countdownSeconds", 10, 3, 60);
    public static final ModConfigSpec.DoubleValue PACK_SCALE = B
            .comment("A raid's packs are this much bigger than a gate's.")
            .defineInRange("raid.packScale", 1.5, 1.0, 4.0);
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH = B
            .comment("The raid boss's health: a gate boss's times this, plus bossHealthPerRaider for each raider past the first.")
            .defineInRange("raid.bossHealth", 4.0, 0.5, 100.0);
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH_PER_RAIDER = B
            .comment("More raid boss health for each raider past the first (a hired hunter counts half).")
            .defineInRange("raid.bossHealthPerRaider", 0.75, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue BOSS_DAMAGE = B
            .comment("The raid boss's blows: a gate boss's times this.")
            .defineInRange("raid.bossDamage", 1.25, 0.1, 20.0);
    public static final ModConfigSpec.DoubleValue LIEUTENANT_HEALTH = B
            .comment("A lieutenant's health: a gate boss's times this, plus lieutenantHealthPerRaider for each raider past the first.")
            .defineInRange("raid.lieutenantHealth", 0.8, 0.1, 50.0);
    public static final ModConfigSpec.DoubleValue LIEUTENANT_HEALTH_PER_RAIDER = B
            .comment("More lieutenant health for each raider past the first (a hired hunter counts half).")
            .defineInRange("raid.lieutenantHealthPerRaider", 0.35, 0.0, 10.0);
    public static final ModConfigSpec.IntValue PYLONS = B
            .comment("Pylons that hold the boss's barrier up (one more for every 4 raiders past the first 4).")
            .defineInRange("raid.pylons", 3, 1, 8);
    public static final ModConfigSpec.IntValue PYLON_HITS = B
            .comment("Hits a pylon takes before it breaks (one more for every 2 raiders past the first).")
            .defineInRange("raid.pylonHits", 4, 1, 100);
    public static final ModConfigSpec.IntValue STAGGER_SECONDS = B
            .comment("Seconds the boss reels once its barrier breaks (it takes more damage meanwhile).")
            .defineInRange("raid.staggerSeconds", 6, 0, 60);
    public static final ModConfigSpec.DoubleValue STAGGER_DAMAGE = B
            .comment("How much more damage a reeling boss takes (0.5 = +50%).")
            .defineInRange("raid.staggerDamage", 0.5, 0.0, 5.0);
    public static final ModConfigSpec.IntValue CATACLYSM_SECONDS = B
            .comment("Seconds between the boss's cataclysms, once it is below a quarter of its health.")
            .defineInRange("raid.cataclysmSeconds", 22, 8, 600);
    public static final ModConfigSpec.DoubleValue CATACLYSM_DAMAGE = B
            .comment("A cataclysm takes this share of the max health of every raider outside the light.")
            .defineInRange("raid.cataclysmDamage", 0.55, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue VANGUARD_GUARD = B
            .comment("A vanguard takes this much less damage in a raid (0.2 = 20% less).")
            .defineInRange("roles.vanguardGuard", 0.2, 0.0, 0.9);
    public static final ModConfigSpec.DoubleValue VANGUARD_THREAT = B
            .comment("How much more the boss minds a vanguard's blows than anyone else's.")
            .defineInRange("roles.vanguardThreat", 3.0, 1.0, 20.0);
    public static final ModConfigSpec.DoubleValue STRIKER_DAMAGE = B
            .comment("A striker deals this much more damage in a raid (0.12 = 12% more).")
            .defineInRange("roles.strikerDamage", 0.12, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue SUPPORT_PULSE = B
            .comment("Every 10 seconds a support heals the raiders near them by this share of their max health.")
            .defineInRange("roles.supportPulse", 0.03, 0.0, 0.5);
    public static final ModConfigSpec.DoubleValue REWARD_SCALE = B
            .comment("A raid pays this many times a gate's clear (System XP and gold).")
            .defineInRange("rewards.scale", 2.5, 1.0, 50.0);
    public static final ModConfigSpec.IntValue CACHES = B
            .comment("Most reward caches a cleared raid drops (one for each raider, up to this).")
            .defineInRange("rewards.caches", 4, 1, 12);

    public static final ModConfigSpec SPEC = B.build();

    /** Percent chance a natural gate of this rank (tier 0..5) is a raid gate. */
    public static int naturalChance(int tier) {
        List<? extends Integer> list = NATURAL_CHANCE.get();
        if (tier < 0 || tier >= list.size() || list.get(tier) == null) return 0;
        return Math.max(0, Math.min(100, list.get(tier)));
    }

    private RaidConfig() {}
}
