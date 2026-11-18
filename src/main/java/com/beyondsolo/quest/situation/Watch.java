package com.beyondsolo.quest.situation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * What the System has noticed about one player lately, kept in memory only (it is about the last minutes of play):
 * when they were last hurt, how many kills they have made without a scratch, how fast they have been killing, whether
 * they have been hit in the gate they are running.
 */
public final class Watch {
    /** Game time the player last took damage, and last took it from a creature. */
    public long lastHurt = Long.MIN_VALUE / 2;
    public long lastHurtByCreature = Long.MIN_VALUE / 2;
    /** Game time the player last hurt something. */
    public long lastHit = Long.MIN_VALUE / 2;
    /** Kills since the player last took a hit, and when the last of them was. */
    public int cleanKills;
    public long lastCleanKill = Long.MIN_VALUE / 2;
    /** Game times of the player's recent kills, newest last. */
    public final Deque<Long> recentKills = new ArrayDeque<>();
    /** The gate the player is running, whether they have been hurt in it, and whether its boss has fallen. */
    public UUID gate;
    public boolean hurtInGate;
    public boolean gateCleared;
    /** Game time structures were last looked up around the player. */
    public long structureCheck = Long.MIN_VALUE / 2;

    /** Forgets kills older than {@code window} ticks. */
    public int killsWithin(long now, long window) {
        while (!recentKills.isEmpty() && now - recentKills.peekFirst() > window) recentKills.pollFirst();
        return recentKills.size();
    }
}
