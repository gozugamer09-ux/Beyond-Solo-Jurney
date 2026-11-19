package com.beyondsolo.rival;

import com.beyondsolo.gate.GateInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Hooks for commands and game tests that drive rival parties by hand. Not for gameplay code. */
public final class RivalTestAccess {
    /** The party's hunters never show themselves, whoever stands near (so they never walk into another test's plot). */
    public static void unseen(RivalParty p) {
        RivalParties.UNSEEN.add(p.id);
    }

    /** One second of the party's run, now. */
    public static void advance(MinecraftServer server, RivalParty p) {
        RivalParties.advance(server, RivalData.get(server), p, RivalParties.now(server));
    }

    /**
     * A hunter walks into the party's gate, with the roll that decides whether a party still on its way follows them in
     * anyway (0: it does if it ever would; 1: it never does).
     */
    public static void arrived(ServerPlayer player, GateInstance g, float roll) {
        RivalParties.arrived(player, g, roll);
    }

    /** The race's tally so far: what the party and what the hunters dealt the gate's boss. */
    public static void tally(RivalParty p, float rivals, float hunters) {
        p.rivalDamage = rivals;
        p.hunterDamage = hunters;
    }

    /** How far through its gate the party is (0 to 1), and whether it will get to the end of it. */
    public static void progress(RivalParty p, float progress, boolean succeeds) {
        p.progress = progress;
        p.succeeds = succeeds;
    }

    /** The party is forgotten at once, its hunters gone (the end of a test). */
    public static void drop(MinecraftServer server, RivalParty p) {
        RivalParties.removeBodies(server, p);
        RivalData.get(server).remove(p.id);
        RivalParties.UNSEEN.remove(p.id);
    }

    private RivalTestAccess() {}
}
