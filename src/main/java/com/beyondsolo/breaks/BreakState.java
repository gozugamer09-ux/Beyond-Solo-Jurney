package com.beyondsolo.breaks;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Where a gate stands on its way to breaking, saved with its {@link com.beyondsolo.gate.GateInstance}: when its clock
 * started and when it breaks, the phase it has reached, and once it has broken, how far the spill has got and the boss
 * that came out with it. {@link DungeonBreaks} moves it along.
 */
public final class BreakState {
    /** A time that has not come yet. */
    public static final long NEVER = Long.MIN_VALUE;

    public enum Phase {
        /** Holding. No clock has started yet if {@link #clockStarted()} is false. */
        STABLE,
        /** Coming apart: the last third of its clock. Players near it see the collapse timer. */
        UNSTABLE,
        /** About to break: the last minute, counted down in front of someone. */
        IMMINENT,
        /** Broken: its monsters are out in the world, and its boss with them. */
        BROKEN,
        /** Its boss fell outside: the break is over and the gate has collapsed. */
        SUPPRESSED;

        public static Phase byIndex(int i) {
            Phase[] all = values();
            return all[Math.floorMod(i, all.length)];
        }
    }

    private Phase phase = Phase.STABLE;
    /** Game time the clock started, {@link #NEVER} until then. */
    private long startedAt = NEVER;
    /** The clock's full length (ticks), for the share of it that shows as unstable. */
    private long length;
    /** Game time it breaks (pushed back while someone is inside its cave or, once imminent, while nobody watches). */
    private long breakAt = NEVER;
    /** Game time it started to come apart (its collapse timer showed), {@link #NEVER} until then. */
    private long unstableAt = NEVER;
    /** Game time it broke, {@link #NEVER} until then. */
    private long brokeAt = NEVER;
    /** Waves of monsters out so far (the first comes with the break). */
    private int waves;
    /** Game time of the last wave. */
    private long lastWave = NEVER;
    /** The boss out in the world, once it has come through. */
    @Nullable private UUID boss;
    /** Monsters of the break killed so far. */
    private int killed;

    public static BreakState fresh() {
        return new BreakState();
    }

    public Phase phase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }
    public boolean clockStarted() { return startedAt != NEVER; }
    public long startedAt() { return startedAt; }
    public long length() { return length; }
    public long breakAt() { return breakAt; }
    public void setBreakAt(long time) { this.breakAt = time; }
    public long brokeAt() { return brokeAt; }
    public long unstableAt() { return unstableAt; }

    /** It started to come apart now (kept from the first time). */
    public void markUnstable(long now) {
        if (unstableAt == NEVER) unstableAt = now;
    }
    public int waves() { return waves; }
    public long lastWave() { return lastWave; }
    @Nullable public UUID boss() { return boss; }
    public void setBoss(@Nullable UUID boss) { this.boss = boss; }
    public boolean bossOut() { return boss != null; }
    public int killed() { return killed; }

    /** True while its monsters are out (broken and not yet suppressed). */
    public boolean broken() {
        return phase == Phase.BROKEN;
    }

    /** True once it has broken, suppressed or not. */
    public boolean hasBroken() {
        return phase == Phase.BROKEN || phase == Phase.SUPPRESSED;
    }

    /** Starts the clock: the gate breaks {@code ticks} from {@code now}. */
    public void start(long now, long ticks) {
        startedAt = now;
        length = Math.max(1, ticks);
        breakAt = now + length;
        unstableAt = NEVER;
        phase = Phase.STABLE;
    }

    /** Ticks left before it breaks (0 once due). */
    public long left(long now) {
        return breakAt == NEVER ? Long.MAX_VALUE : Math.max(0, breakAt - now);
    }

    public void broke(long now) {
        phase = Phase.BROKEN;
        brokeAt = now;
    }

    public void wave(long now) {
        waves++;
        lastWave = now;
    }

    /** Tests: the last wave came {@code ticks} earlier than it did. */
    void backdateWave(long ticks) {
        lastWave -= ticks;
    }

    public void countKill() {
        killed++;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("phase", phase.name());
        if (startedAt != NEVER) t.putLong("started", startedAt);
        t.putLong("length", length);
        if (breakAt != NEVER) t.putLong("breakAt", breakAt);
        if (unstableAt != NEVER) t.putLong("unstableAt", unstableAt);
        if (brokeAt != NEVER) t.putLong("brokeAt", brokeAt);
        t.putInt("waves", waves);
        if (lastWave != NEVER) t.putLong("lastWave", lastWave);
        if (boss != null) t.putUUID("boss", boss);
        t.putInt("killed", killed);
        return t;
    }

    public static BreakState load(CompoundTag t) {
        BreakState b = new BreakState();
        try {
            b.phase = Phase.valueOf(t.getString("phase"));
        } catch (IllegalArgumentException e) {
            b.phase = Phase.STABLE;
        }
        b.startedAt = t.contains("started") ? t.getLong("started") : NEVER;
        b.length = t.getLong("length");
        b.breakAt = t.contains("breakAt") ? t.getLong("breakAt") : NEVER;
        b.unstableAt = t.contains("unstableAt") ? t.getLong("unstableAt") : NEVER;
        b.brokeAt = t.contains("brokeAt") ? t.getLong("brokeAt") : NEVER;
        b.waves = t.getInt("waves");
        b.lastWave = t.contains("lastWave") ? t.getLong("lastWave") : NEVER;
        if (t.hasUUID("boss")) b.boss = t.getUUID("boss");
        b.killed = t.getInt("killed");
        return b;
    }
}
