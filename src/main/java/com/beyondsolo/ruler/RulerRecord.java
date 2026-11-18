package com.beyondsolo.ruler;

import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * A hunter's record with the rulers of light ({@code beyondsolo:rulers}, kept through death): the rulers they helped
 * fell (by kind) and how often they did the most, the wars in which a monarch fell to them at a ruler's side, the
 * sentences passed on them and the ones they overturned, the heralds they cut down and the blessings they were given.
 * Titles read it; so will the path of light.
 */
public final class RulerRecord implements INBTSerializable<CompoundTag> {
    private final int[] slain = new int[WorldBossKind.values().length];
    private int top;
    private int alliedWars;
    private int sentences;
    private int overturned;
    private int heralds;
    private int blessings;

    public int slain(WorldBossKind kind) {
        return slain[kind.ordinal()];
    }

    /** Rulers felled, all kinds. */
    public int rulers() {
        int n = 0;
        for (WorldBossKind k : WorldBossKind.rulers()) n += slain[k.ordinal()];
        return n;
    }

    /** Times this hunter did the most to a ruler that fell. */
    public int top() {
        return top;
    }

    /** Monarchs brought down at a ruler's side (blessed for it). */
    public int alliedWars() {
        return alliedWars;
    }

    /** Sentences passed on this hunter. */
    public int sentences() {
        return sentences;
    }

    /** Rulers come to judge this hunter that fell instead. */
    public int overturned() {
        return overturned;
    }

    /** Heralds of light cut down. */
    public int heralds() {
        return heralds;
    }

    /** Blessings of Light received from a ruler. */
    public int blessings() {
        return blessings;
    }

    void addSlain(WorldBossKind kind, boolean top) {
        slain[kind.ordinal()]++;
        if (top) this.top++;
    }

    void addAlliedWar() {
        alliedWars++;
    }

    void addSentence() {
        sentences++;
    }

    void addOverturned() {
        overturned++;
    }

    void addHerald() {
        heralds++;
    }

    void addBlessing() {
        blessings++;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        CompoundTag kills = new CompoundTag();
        for (WorldBossKind k : WorldBossKind.values()) if (slain[k.ordinal()] > 0) kills.putInt(k.id, slain[k.ordinal()]);
        tag.put("slain", kills);
        tag.putInt("top", top);
        tag.putInt("wars", alliedWars);
        tag.putInt("sentences", sentences);
        tag.putInt("overturned", overturned);
        tag.putInt("heralds", heralds);
        tag.putInt("blessings", blessings);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        CompoundTag kills = tag.getCompound("slain");
        for (WorldBossKind k : WorldBossKind.values()) slain[k.ordinal()] = kills.getInt(k.id);
        top = tag.getInt("top");
        alliedWars = tag.getInt("wars");
        sentences = tag.getInt("sentences");
        overturned = tag.getInt("overturned");
        heralds = tag.getInt("heralds");
        blessings = tag.getInt("blessings");
    }
}
