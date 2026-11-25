package com.beyondsolo.duel;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * A duel being fought: a hunter and a national-level hunter in a ring the association marks out where the challenge
 * was taken. Kept in memory only: a server that stops mid-duel calls it off (the challenger is never saved either).
 */
public final class Duel {
    /** Where it is at: the count of three, the fight, and the moment after it (the result on everyone's screen). */
    public enum Phase { COUNTDOWN, FIGHT, OVER }

    /** How it ended. */
    public enum Result {
        /** The challenger yielded. */
        WON,
        /** The hunter yielded (a blow that would have killed them). */
        LOST,
        /** The time ran out. */
        DRAWN,
        /** The hunter left the ring, or the world they were fighting in. */
        FORFEIT,
        /** Nobody won: the hunter left the game, the challenger was taken away, a command. */
        CALLED_OFF
    }

    public final UUID id = UUID.randomUUID();
    public final UUID hunter;
    public final NationalHunter who;
    public final DuelRecord.Reason why;
    public final ResourceKey<Level> dimension;
    public final Vec3 center;
    public final double radius;
    /** The level they fight at (their own, or the hunter's when they only came to test them). */
    public final float level;
    /** The challenger's entity. */
    public UUID duelist;
    public int duelistId;
    public Phase phase = Phase.COUNTDOWN;
    /** Game times: the countdown began, the fight began (or will), the time runs out, it ended. */
    public long startedAt, fightAt, endsAt, overAt;
    /** Ticks the hunter has stood outside the ring in a row. */
    public int outside;
    public Result result;
    /** The end of it has been settled (the record, the rewards): until then nothing more lands on the hunter. */
    public boolean settled;
    /** The hunter's name, for those who watch. */
    public String hunterName = "";
    /** What a win paid; whether it made them a national-level hunter. */
    public long purse, xp;
    public int merit;
    public boolean national;

    Duel(UUID hunter, NationalHunter who, DuelRecord.Reason why, ResourceKey<Level> dimension, Vec3 center, double radius, float level) {
        this.hunter = hunter;
        this.who = who;
        this.why = why;
        this.dimension = dimension;
        this.center = center;
        this.radius = radius;
        this.level = level;
    }

    public boolean fighting() {
        return phase == Phase.FIGHT;
    }

    /** True if (x, z) is inside the ring. */
    public boolean inside(Vec3 at, double margin) {
        double dx = at.x - center.x, dz = at.z - center.z;
        return dx * dx + dz * dz <= (radius + margin) * (radius + margin);
    }
}
