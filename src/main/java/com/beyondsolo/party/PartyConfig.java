package com.beyondsolo.party;

import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Party settings (config/beyondsolo-party.toml). Numbers are first guesses for the balancing pass. */
public final class PartyConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_SIZE = B
            .comment("How many hunters a party can hold, its leader included.")
            .defineInRange("party.maxSize", 6, 2, 16);
    public static final ModConfigSpec.IntValue INVITE_SECONDS = B
            .comment("Seconds an invitation to a party stays open.")
            .defineInRange("party.inviteSeconds", 60, 10, 600);
    public static final ModConfigSpec.IntValue SHARE_RANGE = B
            .comment("Party members within this many blocks of a creature when it falls share the kill (in a gate's cave,",
                    "anywhere in the same cave counts): its quest progress in full, and a part of its XP and gold.")
            .defineInRange("party.shareRange", 48, 8, 256);
    public static final ModConfigSpec.DoubleValue KILL_SHARE = B
            .comment("The part of a kill's XP and gold each party member who shares it gets. Whoever struck the blow",
                    "always gets all of theirs. 0 shares only the quest progress.")
            .defineInRange("party.killShare", 0.5, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue FRIENDLY_FIRE = B
            .comment("Whether party members (and their shadows and pets) can hurt each other.")
            .define("party.friendlyFire", false);
    public static final ModConfigSpec.IntValue FOLLOW_SECONDS = B
            .comment("A keyed gate or a red gate that is about to seal holds open up to this many seconds longer while a",
                    "party member of someone inside is still on the way (within 48 blocks of it). 0 turns that off.")
            .defineInRange("party.followSeconds", 45, 0, 600);
    public static final ModConfigSpec.IntValue REGROUP_COOLDOWN_MINUTES = B
            .comment("Minutes before a hunter can regroup with their party again (travel to a party member's side).",
                    "0: no wait. -1 turns regrouping off.")
            .defineInRange("party.regroupCooldownMinutes", 10, -1, 1440);
    public static final ModConfigSpec.IntValue REGROUP_MIN_DISTANCE = B
            .comment("Regrouping is for party members farther apart than this many blocks (or in another dimension).")
            .defineInRange("party.regroupMinDistance", 64, 0, 100000);

    public static final ModConfigSpec SPEC = B.build();

    /** Ticks a sealing gate may hold open for the party. */
    public static int followTicks() {
        return FOLLOW_SECONDS.get() * 20;
    }

    /** Blocks around a gate within which a party member counts as on the way to it. */
    public static final int FOLLOW_REACH = 48;

    /** Ticks between two regroups (-1 when regrouping is off). */
    public static long regroupCooldownTicks() {
        int m = REGROUP_COOLDOWN_MINUTES.get();
        return m < 0 ? -1 : m * 60L * 20L;
    }

    /** True for the dimensions a party can regroup in: the overworld, the Nether and the End (not gates and trials). */
    public static boolean openWorld(Level level) {
        return level.dimension() == Level.OVERWORLD || level.dimension() == Level.NETHER || level.dimension() == Level.END;
    }

    private PartyConfig() {}
}
