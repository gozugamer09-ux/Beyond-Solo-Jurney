package com.beyondsolo.gate;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The shape of the whole dungeon behind a gate, before its realm dresses it: how its rooms are laid out and joined.
 * A gate's realm says which shapes suit it ({@link GateRealm#shapes}) and the gate's seed picks one, so the same
 * frost cavern can be a winding chain of caves one time and a field of ice bridges over a chasm the next.
 * {@link DungeonPlanner} turns a shape into a {@link DungeonLayout}.
 */
public enum DungeonShape {
    /** The classic: a winding chain of caverns, a side cave or two, the boss at the far end. */
    CAVERNS,
    /** A branching maze on a grid: dead ends, loops, caches in the corners, the boss beyond the far edge. */
    LABYRINTH,
    /** A run of great halls, one after another through gatehouses, each a fight of its own. */
    GAUNTLET,
    /** A tower climbed floor by floor up winding stairs, the boss at the top. */
    SPIRE,
    /** A deep shaft walked down on a ramp round its wall, ledge caves off it, the boss at the bottom. */
    DESCENT,
    /** Islands of rock standing in a chasm, joined by bridges, a sea of water or lava far below. */
    CHASM,
    /** One vast open cavern with its own lake and pillars, the boss's lair beyond it. */
    HOLLOW,
    /** A cluster of round cells grown off each other in every direction, the queen's chamber deepest in. */
    HIVE;

    public String id() {
        return name().toLowerCase();
    }

    public Component displayName() {
        return Component.translatable("gate.beyondsolo.shape." + id());
    }

    @Nullable
    public static DungeonShape byId(String id) {
        for (DungeonShape s : values()) if (s.name().equalsIgnoreCase(id)) return s;
        return null;
    }

    public static DungeonShape byIndex(int i) {
        DungeonShape[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }
}
