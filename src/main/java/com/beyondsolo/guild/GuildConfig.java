package com.beyondsolo.guild;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Guild settings (config/beyondsolo-guild.toml). Every number is a first guess for the balancing pass. */
public final class GuildConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.LongValue FOUNDING_COST = B
            .comment("Gold it costs to found a guild.")
            .defineInRange("guild.foundingCost", 5000L, 0L, 100_000_000L);
    public static final ModConfigSpec.IntValue INVITE_SECONDS = B
            .comment("Seconds an invitation into a guild stays open.")
            .defineInRange("guild.inviteSeconds", 120, 10, 3600);
    public static final ModConfigSpec.IntValue BASE_MEMBERS = B
            .comment("How many hunters a new guild can hold (its master included).")
            .defineInRange("guild.baseMembers", 8, 2, 200);
    public static final ModConfigSpec.IntValue MEMBERS_PER_LEVEL = B
            .comment("More members for every guild level after the first.")
            .defineInRange("guild.membersPerLevel", 2, 0, 50);
    public static final ModConfigSpec.IntValue BASE_ROSTER = B
            .comment("How many hunters a new guild can hire; one more every second level.")
            .defineInRange("guild.baseRoster", 2, 0, 20);
    public static final ModConfigSpec.DoubleValue GOLD_PER_LEVEL = B
            .comment("More gold from kills, quests, gates, raids and the Citadel for members, per guild level after the first.")
            .defineInRange("guild.goldPerLevel", 0.02, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue XP_PER_LEVEL = B
            .comment("More System XP for members, per guild level after the first.")
            .defineInRange("guild.xpPerLevel", 0.02, 0.0, 1.0);
    public static final ModConfigSpec.IntValue GATE_XP = B
            .comment("Guild XP when a member clears an E-rank gate; a gate of rank tier t gives this times (t+1)^2.")
            .defineInRange("guild.gateXp", 10, 0, 100000);
    public static final ModConfigSpec.IntValue RAID_XP_MULTIPLIER = B
            .comment("A raid cleared gives this many times the guild XP of a gate of its rank, for each member in it.")
            .defineInRange("guild.raidXpMultiplier", 10, 0, 1000);
    public static final ModConfigSpec.IntValue GOLD_PER_XP = B
            .comment("Gold paid into the treasury that counts as one guild XP. 0: paying in gives no XP.")
            .defineInRange("guild.goldPerXp", 100, 0, 1_000_000);
    public static final ModConfigSpec.IntValue LEVEL_XP = B
            .comment("Guild XP from level 1 to 2; level n to n+1 takes this times n^1.7.")
            .defineInRange("guild.levelXp", 600, 1, 10_000_000);
    public static final ModConfigSpec.IntValue PERMIT_PRICE = B
            .comment("A raid permit costs this many times the gold of clearing a gate of its rank, from the treasury.")
            .defineInRange("guild.permitPrice", 8, 0, 1000);
    public static final ModConfigSpec.DoubleValue PERMIT_DISCOUNT = B
            .comment("Permits cost this much less per guild level after the first (0.04 = 4%).")
            .defineInRange("guild.permitDiscountPerLevel", 0.04, 0.0, 0.1);
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> HIRE_COST = B
            .comment("Gold to hire a hunter, by rank E D C B A S (a few percent more for each level over the rank's start).")
            .defineList("hunters.hireCost", List.of(600, 1500, 3500, 8000, 16000, 32000), () -> 1000, o -> o instanceof Integer i && i >= 0);
    public static final ModConfigSpec.IntValue RECRUITS = B
            .comment("Hunters looking for a guild each day (the recruit pool).")
            .defineInRange("hunters.recruits", 4, 0, 12);
    public static final ModConfigSpec.IntValue INJURY_MINUTES = B
            .comment("Minutes a hunter who fell in a raid sits out the next ones.")
            .defineInRange("hunters.injuryMinutes", 20, 0, 1440);
    public static final ModConfigSpec.DoubleValue HUNTER_HEALTH = B
            .comment("Scales every hired hunter's health (1 = as strong as the creatures of their level hit).")
            .defineInRange("hunters.healthScale", 1.0, 0.05, 20.0);
    public static final ModConfigSpec.DoubleValue HUNTER_DAMAGE = B
            .comment("Scales every hired hunter's blows.")
            .defineInRange("hunters.damageScale", 1.0, 0.05, 20.0);

    public static final ModConfigSpec SPEC = B.build();

    /** The highest guild level. */
    public static final int MAX_LEVEL = 10;

    /** The hire price of a hunter of this rank (tier 0..5). */
    public static long hireCost(int tier) {
        List<? extends Integer> list = HIRE_COST.get();
        if (list.isEmpty()) return 1000;
        Integer v = list.get(Math.max(0, Math.min(tier, list.size() - 1)));
        return v == null ? 1000 : v;
    }

    private GuildConfig() {}
}
