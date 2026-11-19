package com.beyondsolo.story;

import net.minecraft.server.level.ServerPlayer;

/** Hooks for game tests that reach past the normal flow of the Long Sleep. Not for gameplay code. */
public final class StoryTestAccess {
    /** The once-a-second check her hunter's tick runs: her in her bed, and the next part of the story when it is time. */
    public static void tick(ServerPlayer p) {
        Kin.tick(p);
        LongSleep.tick(p);
    }

    /** Only the story's part of that check (her own part may choose a bed: a test without one of its own skips it). */
    public static void storyTick(ServerPlayer p) {
        LongSleep.tick(p);
    }

    /** The hunter right-clicks her. */
    public static void talk(ServerPlayer p, KinEntity k) {
        Kin.interact(p, k);
    }

    /** The day she woke (her day: sitting up in bed that day, up and about after). */
    public static void setWokeDay(ServerPlayer p, long day) {
        Kin.record(p).wokeDay = day;
    }

    /** The day she last cooked for them. */
    public static void setMealDay(ServerPlayer p, long day) {
        Kin.record(p).mealDay = day;
    }

    private StoryTestAccess() {}
}
