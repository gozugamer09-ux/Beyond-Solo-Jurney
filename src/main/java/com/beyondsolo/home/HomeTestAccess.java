package com.beyondsolo.home;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/** What the game tests reach into ({@code test.HomeGameTests}): the plan of any plot, a night's rest, a hunter's tick. */
public final class HomeTestAccess {
    /** Plot {@code plot}'s plan: the floor alone ({@code small}, as in tests) or the full home in its world. */
    public static HomeWorld.Layout plan(boolean small, int plot) {
        return HomeWorld.plan(small, plot);
    }

    /** The plan of the hunter's own home in the level homes are in, or null if they have no plot. */
    @Nullable
    public static HomeWorld.Layout layout(ServerPlayer p) {
        ServerLevel level = HomeWorld.level(p.server);
        int plot = HomeData.get(p.server).plot(p.getUUID());
        return level == null || plot < 0 ? null : HomeWorld.plan(level, plot);
    }

    /** A night in their own bed, as if they had slept the night through. */
    public static void rest(ServerPlayer p) {
        Homes.rest(p);
    }

    /**
     * One of the hunter's ticks in a home on which they are looked over (fake players don't tick): their fall caught,
     * a guest's welcome checked, the dummies and pedestals put right.
     */
    public static void tick(ServerPlayer p) {
        p.tickCount = Math.floorMod(-p.getId(), Homes.CHECK);
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
    }

    private HomeTestAccess() {}
}
