package com.beyondsolo.duel;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * A hunter's duels with the national-level hunters ({@code beyondsolo:duels}, kept through death): how each one went,
 * the challenge standing now (one at a time), and what has brought one already (a test of the new S-rank, the places
 * climbed past them on the rankings).
 */
public final class DuelRecord implements INBTSerializable<CompoundTag> {
    /** What the last duel with one of them came to. */
    public static final int NONE = 0, WON = 1, LOST = 2, DRAWN = 3;

    /** Why a challenge was made. */
    public enum Reason {
        /** The hunter reached rank S: one of them comes to see if it is real. */
        TEST,
        /** The hunter climbed past them on the national rankings: they come for their place. */
        PASSED,
        /** The hunter walked out on their guild's contract: they come to settle it. */
        GRUDGE,
        /** The hunter asked for it at the association. */
        ASKED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Reason byIndex(int i) {
            Reason[] all = values();
            return all[Math.floorMod(i, all.length)];
        }
    }

    /** A challenge standing: who, why, when they set out for the hunter, and when it lapses. */
    public static final class Challenge {
        public final UUID id;
        public final NationalHunter who;
        public final Reason why;
        /** Game time they set out (or come back) for the hunter. */
        public long openAt;
        /** Game time it lapses (0 until it opens). */
        public long lapseAt;
        /** The hunter has been told of it; the challenger has gone to them this time. */
        public boolean told, visited;
        /** Times the hunter has put it off ("Not now"). */
        public int later;

        public Challenge(UUID id, NationalHunter who, Reason why, long openAt) {
            this.id = id;
            this.who = who;
            this.why = why;
            this.openAt = openAt;
        }

        public boolean open(long now) {
            return now >= openAt;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putInt("who", who.ordinal());
            t.putInt("why", why.ordinal());
            t.putLong("openAt", openAt);
            t.putLong("lapseAt", lapseAt);
            t.putBoolean("told", told);
            t.putBoolean("visited", visited);
            t.putInt("later", later);
            return t;
        }

        static Challenge load(CompoundTag t) {
            Challenge c = new Challenge(t.hasUUID("id") ? t.getUUID("id") : UUID.randomUUID(), NationalHunter.byIndex(t.getInt("who")),
                    Reason.byIndex(t.getInt("why")), t.getLong("openAt"));
            c.lapseAt = t.getLong("lapseAt");
            c.told = t.getBoolean("told");
            c.visited = t.getBoolean("visited");
            c.later = t.getInt("later");
            return c;
        }
    }

    private static final int N = NationalHunter.values().length;

    final int[] wins = new int[N], losses = new int[N], draws = new int[N], last = new int[N];
    final long[] lastDay = new long[N];
    int declined, forfeits;
    /** The national-level hunters the hunter has climbed past on the rankings (each comes for their place once). */
    int passed;
    /** A national-level hunter has come to test the hunter as a new S-rank. */
    boolean tested;
    /** Game time the hunter reached rank S (0: not yet, or before this record kept it). */
    long rankedSAt;
    /** The association has recognized them as a national-level hunter (they beat all four). */
    boolean national;
    @Nullable Challenge challenge;

    public DuelRecord() {
        Arrays.fill(lastDay, -1000);
    }

    public int wins(NationalHunter h) { return wins[h.ordinal()]; }
    public int losses(NationalHunter h) { return losses[h.ordinal()]; }
    public int draws(NationalHunter h) { return draws[h.ordinal()]; }
    /** What the last duel with them came to ({@link #WON}, {@link #LOST}, {@link #DRAWN}, or {@link #NONE}). */
    public int last(NationalHunter h) { return last[h.ordinal()]; }
    public long lastDay(NationalHunter h) { return lastDay[h.ordinal()]; }
    public boolean passed(NationalHunter h) { return (passed & (1 << h.ordinal())) != 0; }
    public boolean tested() { return tested; }
    public boolean national() { return national; }
    public int declined() { return declined; }
    public int forfeits() { return forfeits; }
    @Nullable public Challenge challenge() { return challenge; }

    public int totalWins() {
        int n = 0;
        for (int w : wins) n += w;
        return n;
    }

    public int totalLosses() {
        int n = 0;
        for (int l : losses) n += l;
        return n;
    }

    public int totalDraws() {
        int n = 0;
        for (int d : draws) n += d;
        return n;
    }

    /** True if they have beaten every one of them at least once. */
    public boolean beatenAll() {
        for (int w : wins) if (w <= 0) return false;
        return true;
    }

    /** The ids of the national-level hunters whose last duel with the hunter the hunter won (they rank above them). */
    public java.util.List<String> standingOver() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (NationalHunter h : NationalHunter.values()) if (last[h.ordinal()] == WON) out.add(h.id);
        return out;
    }

    void markPassed(NationalHunter h) {
        passed |= 1 << h.ordinal();
    }

    void count(NationalHunter h, int result, long day) {
        int i = h.ordinal();
        switch (result) {
            case WON -> wins[i]++;
            case LOST -> losses[i]++;
            case DRAWN -> draws[i]++;
            default -> {
                return;
            }
        }
        last[i] = result;
        lastDay[i] = day;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putIntArray("wins", wins);
        t.putIntArray("losses", losses);
        t.putIntArray("draws", draws);
        t.putIntArray("last", last);
        t.putLongArray("lastDay", lastDay);
        t.putInt("declined", declined);
        t.putInt("forfeits", forfeits);
        t.putInt("passed", passed);
        t.putBoolean("tested", tested);
        t.putLong("rankedSAt", rankedSAt);
        t.putBoolean("national", national);
        if (challenge != null) t.put("challenge", challenge.save());
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        copy(t.getIntArray("wins"), wins);
        copy(t.getIntArray("losses"), losses);
        copy(t.getIntArray("draws"), draws);
        copy(t.getIntArray("last"), last);
        long[] days = t.getLongArray("lastDay");
        for (int i = 0; i < N; i++) lastDay[i] = i < days.length ? days[i] : -1000;
        declined = t.getInt("declined");
        forfeits = t.getInt("forfeits");
        passed = t.getInt("passed");
        tested = t.getBoolean("tested");
        rankedSAt = t.getLong("rankedSAt");
        national = t.getBoolean("national");
        challenge = t.contains("challenge") ? Challenge.load(t.getCompound("challenge")) : null;
    }

    private static void copy(int[] from, int[] to) {
        for (int i = 0; i < to.length; i++) to[i] = i < from.length ? from[i] : 0;
    }
}
