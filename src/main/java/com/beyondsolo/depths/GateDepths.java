package com.beyondsolo.depths;

import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What lies off the beaten path of one gate's cave, and how far the party has got with it: its weather, the hidden
 * chamber (if it has one), its trap rooms, and their progress. Everything is decided by the gate's seed unless a
 * command (or a test) forces it. Kept on the {@link GateInstance} and saved with it.
 *
 * <p>Caves made before hidden chambers and traps existed load as {@link #legacy()}: their rock was carved without
 * them, so they get weather only.</p>
 */
public final class GateDepths {
    private static final int FROM_SEED = -2;
    private static final int NONE = -1;

    private final boolean enabled;
    private int weatherOverride = NONE;
    private int hiddenOverride = FROM_SEED;
    /** Forced trap rooms (room index to kind); null: from the seed. */
    @Nullable private Map<Integer, TrapKind> trapOverride;

    private boolean hiddenOpen;
    private boolean hiddenEntered;
    private boolean hiddenDone;
    private long trapsTriggered;
    private long trapsDone;
    /** Lockdown rooms: the wave reached (1 or 2). */
    private final Map<Integer, Integer> waves = new HashMap<>();

    private transient Map<Integer, TrapKind> traps;
    /** Knocks on the hidden wall so far (anyone's): the third brings it down. */
    transient int knocks;

    private GateDepths(boolean enabled) {
        this.enabled = enabled;
    }

    /** A new gate's: hidden chambers and traps from its seed. */
    public static GateDepths fresh() {
        return new GateDepths(true);
    }

    /** A cave carved before hidden chambers and traps: weather only. */
    public static GateDepths legacy() {
        return new GateDepths(false);
    }

    public boolean enabled() {
        return enabled;
    }

    // ------------------------------------------------------------------ what this cave has

    public GateWeather weather(GateInstance g) {
        if (g.isTrial()) return GateWeather.CLEAR;
        if (weatherOverride >= 0) return GateWeather.byIndex(weatherOverride);
        return GateWeather.roll(g.seed(), g.rank(), g.theme());
    }

    public void setWeather(@Nullable GateWeather weather) {
        weatherOverride = weather == null ? NONE : weather.ordinal();
    }

    /** The hidden chamber, or null. Changing it after the cave is built does nothing to the rock. */
    @Nullable
    public HiddenKind hidden(GateInstance g) {
        if (!enabled || g.isTrial() || hiddenOverride == NONE) return null;
        if (hiddenOverride >= 0) return HiddenKind.byIndex(hiddenOverride);
        return HiddenKind.roll(g.seed(), g.rank());
    }

    /** Forces the hidden chamber (null: none). Call {@link GateInstance#replan()} after, before the cave is built. */
    public void setHidden(@Nullable HiddenKind kind) {
        hiddenOverride = kind == null ? NONE : kind.ordinal();
    }

    /** The trap rooms, by room index. */
    public Map<Integer, TrapKind> traps(GateInstance g) {
        if (!enabled || g.isTrial()) return Collections.emptyMap();
        if (trapOverride != null) return trapOverride;
        if (traps != null) return traps;
        Map<Integer, TrapKind> out = new LinkedHashMap<>();
        int tier = g.rank().tier();
        int max = 1 + tier / 3;
        float chance = 0.12f + 0.03f * tier;
        RandomSource r = new LegacyRandomSource(g.seed() ^ 0x5452415053215452L);
        for (DungeonLayout.Room room : g.layout().rooms()) {
            // never the first cavern: the run gets going before the cave starts playing tricks
            if (room.kind() != DungeonLayout.Kind.CAVERN || room.index() < 2) continue;
            boolean hit = r.nextFloat() < chance;
            int kind = r.nextInt(100);
            if (hit && out.size() < max) out.put(room.index(), TrapKind.pick(kind));
        }
        traps = out;
        return out;
    }

    @Nullable
    public TrapKind trap(GateInstance g, int room) {
        return traps(g).get(room);
    }

    /** Forces a room's trap (null takes it away). The first forced room drops every trap the seed gave. */
    public void setTrap(int room, @Nullable TrapKind kind) {
        if (trapOverride == null) trapOverride = new LinkedHashMap<>();
        if (kind == null) trapOverride.remove(room);
        else trapOverride.put(room, kind);
        traps = null;
    }

    /** No trap rooms at all (tests that want a quiet cave). */
    public void clearTraps() {
        trapOverride = new LinkedHashMap<>();
        traps = null;
    }

    // ------------------------------------------------------------------ progress

    public boolean hiddenOpen() { return hiddenOpen; }
    void setHiddenOpen(boolean open) { this.hiddenOpen = open; }
    public boolean hiddenEntered() { return hiddenEntered; }
    void setHiddenEntered(boolean entered) { this.hiddenEntered = entered; }
    /** The lair's guardians have fallen (or the shrine has given its blessing). */
    public boolean hiddenDone() { return hiddenDone; }
    void setHiddenDone(boolean done) { this.hiddenDone = done; }

    public boolean trapTriggered(int room) { return (trapsTriggered & (1L << room)) != 0; }
    void triggerTrap(int room) { trapsTriggered |= 1L << room; }
    /** A lockdown that stood down: it springs again on the next step in. */
    void untrigger(int room) { trapsTriggered &= ~(1L << room); }
    /** The trap is beaten: its core broken, or the lockdown's last wave down. */
    public boolean trapDone(int room) { return (trapsDone & (1L << room)) != 0; }
    void finishTrap(int room) { trapsDone |= 1L << room; }

    /** True while a trap has been sprung and is still working. */
    public boolean trapActive(int room) {
        return trapTriggered(room) && !trapDone(room);
    }

    int wave(int room) { return waves.getOrDefault(room, 0); }
    void setWave(int room, int wave) { waves.put(room, wave); }

    // ------------------------------------------------------------------ saving

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("enabled", enabled);
        t.putInt("weather", weatherOverride);
        t.putInt("hidden", hiddenOverride);
        if (trapOverride != null) {
            CompoundTag traps = new CompoundTag();
            trapOverride.forEach((room, kind) -> traps.putString(String.valueOf(room), kind.id()));
            t.put("traps", traps);
        }
        t.putBoolean("hiddenOpen", hiddenOpen);
        t.putBoolean("hiddenEntered", hiddenEntered);
        t.putBoolean("hiddenDone", hiddenDone);
        t.putLong("trapsTriggered", trapsTriggered);
        t.putLong("trapsDone", trapsDone);
        CompoundTag w = new CompoundTag();
        waves.forEach((room, wave) -> w.putInt(String.valueOf(room), wave));
        t.put("waves", w);
        return t;
    }

    public static GateDepths load(CompoundTag t) {
        GateDepths d = new GateDepths(t.getBoolean("enabled"));
        d.weatherOverride = t.contains("weather") ? t.getInt("weather") : NONE;
        d.hiddenOverride = t.contains("hidden") ? t.getInt("hidden") : FROM_SEED;
        if (t.contains("traps")) {
            CompoundTag traps = t.getCompound("traps");
            d.trapOverride = new LinkedHashMap<>();
            for (String key : traps.getAllKeys()) {
                TrapKind kind = TrapKind.byId(traps.getString(key));
                try {
                    if (kind != null) d.trapOverride.put(Integer.parseInt(key), kind);
                } catch (NumberFormatException ignored) {
                    // a broken entry: skip it
                }
            }
        }
        d.hiddenOpen = t.getBoolean("hiddenOpen");
        d.hiddenEntered = t.getBoolean("hiddenEntered");
        d.hiddenDone = t.getBoolean("hiddenDone");
        d.trapsTriggered = t.getLong("trapsTriggered");
        d.trapsDone = t.getLong("trapsDone");
        CompoundTag w = t.getCompound("waves");
        for (String key : w.getAllKeys()) {
            try {
                d.waves.put(Integer.parseInt(key), w.getInt(key));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return d;
    }
}
