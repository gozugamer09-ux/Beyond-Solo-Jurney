package com.beyondsolo.story;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.UUID;

/**
 * What the System's own story keeps for a hunter ({@code beyondsolo:main_story}, kept through death): the chapter they
 * are on ({@link MainStory}'s chapters), the scene a finished chapter still owes them, the monarch the story called for
 * them and when it may call the next, how much of the Last War is won, and their father's part in it.
 */
public class MainRecord implements INBTSerializable<CompoundTag> {
    /** The chapter the hunter is on ({@link MainStory#UNTOLD} to {@link MainStory#DONE}). */
    int chapter;
    /** When the chapter's quest was first given (game time): a quest done at once was done before the story came. */
    long given;
    /** A finished chapter whose closing scene is still owed (0: none), and the time it can play from. */
    int owed;
    long owedAt;
    /** The great threat the story called (or took up) for the hunter's fight, if one is on. */
    @Nullable UUID fight;
    /** The earliest the story calls the next monarch (game time). */
    long nextFight;
    /** The story's monarchs that have fallen (bit 0: Veyrith, bit 1: Thyrax, bit 2: Khorvane; {@link MainFights#bit}). */
    int war;
    /** Their father: met in the story's first monarch fight, gone for good at the end of the Last War. */
    boolean fatherMet, fatherGone;
    /** Which of their father's looks (-1: not chosen yet). */
    int look = -1;
    /** The in-game day the story ended ({@link KinRecord#NOT_YET}: not yet). */
    long endedDay = KinRecord.NOT_YET;

    public int chapter() { return chapter; }
    public int owed() { return owed; }
    public @Nullable UUID fight() { return fight; }
    public long nextFight() { return nextFight; }
    public int war() { return war; }
    public boolean fatherMet() { return fatherMet; }
    public boolean fatherGone() { return fatherGone; }
    public int look() { return look; }
    public long endedDay() { return endedDay; }

    /** Whether the story's monarch {@code bit} (0 Veyrith, 1 Thyrax, 2 Khorvane) has fallen. */
    public boolean warWon(int bit) {
        return (war & (1 << bit)) != 0;
    }

    void reset() {
        chapter = 0;
        given = 0;
        owed = 0;
        owedAt = 0;
        fight = null;
        nextFight = 0;
        war = 0;
        fatherMet = false;
        fatherGone = false;
        endedDay = KinRecord.NOT_YET;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putInt("Chapter", chapter);
        t.putLong("Given", given);
        t.putInt("Owed", owed);
        t.putLong("OwedAt", owedAt);
        if (fight != null) t.putUUID("Fight", fight);
        t.putLong("NextFight", nextFight);
        t.putInt("War", war);
        t.putBoolean("FatherMet", fatherMet);
        t.putBoolean("FatherGone", fatherGone);
        t.putInt("Look", look);
        t.putLong("Ended", endedDay);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        chapter = t.getInt("Chapter");
        given = t.getLong("Given");
        owed = t.getInt("Owed");
        owedAt = t.getLong("OwedAt");
        fight = t.hasUUID("Fight") ? t.getUUID("Fight") : null;
        nextFight = t.getLong("NextFight");
        war = t.getInt("War");
        fatherMet = t.getBoolean("FatherMet");
        fatherGone = t.getBoolean("FatherGone");
        look = t.contains("Look") ? t.getInt("Look") : -1;
        endedDay = t.contains("Ended") ? t.getLong("Ended") : KinRecord.NOT_YET;
    }
}
