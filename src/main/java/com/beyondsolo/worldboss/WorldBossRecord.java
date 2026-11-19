package com.beyondsolo.worldboss;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * A hunter's record against the world's great threats: world bosses and monarchs they helped slay (by kind), how many
 * times they dealt the most damage, and the monarchs fought to the end since their last piece of monarch armor (the
 * pity count). Kept through death; titles and the leaderboard can read it.
 */
public final class WorldBossRecord implements INBTSerializable<CompoundTag> {
    private final int[] slain = new int[WorldBossKind.values().length];
    private int topHunter;
    private int dryMonarchs;
    private int armorPieces;

    public int slain(WorldBossKind kind) {
        return slain[kind.ordinal()];
    }

    /** World bosses slain, all kinds. */
    public int worldBosses() {
        int n = 0;
        for (WorldBossKind k : WorldBossKind.worldBosses()) n += slain[k.ordinal()];
        return n;
    }

    /** Monarchs slain, all kinds. */
    public int monarchs() {
        int n = 0;
        for (WorldBossKind k : WorldBossKind.monarchs()) n += slain[k.ordinal()];
        return n;
    }

    /** Times this hunter dealt the most damage to a great threat that fell. */
    public int topHunter() {
        return topHunter;
    }

    /** Monarchs fought to the end since the last piece of monarch armor. */
    public int dryMonarchs() {
        return dryMonarchs;
    }

    /** Pieces of monarch armor won, all told. */
    public int armorPieces() {
        return armorPieces;
    }

    /** Sets how many of a kind the hunter has slain (operator commands: {@code /solo home trophies}). */
    public void setSlain(WorldBossKind kind, int count) {
        slain[kind.ordinal()] = Math.max(0, count);
    }

    void addSlain(WorldBossKind kind, boolean top) {
        slain[kind.ordinal()]++;
        if (top) topHunter++;
    }

    void addDry() {
        dryMonarchs++;
    }

    void addArmor() {
        armorPieces++;
        dryMonarchs = 0;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        CompoundTag kills = new CompoundTag();
        for (WorldBossKind k : WorldBossKind.values()) if (slain[k.ordinal()] > 0) kills.putInt(k.id, slain[k.ordinal()]);
        tag.put("slain", kills);
        tag.putInt("top", topHunter);
        tag.putInt("dry", dryMonarchs);
        tag.putInt("armor", armorPieces);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        CompoundTag kills = tag.getCompound("slain");
        for (WorldBossKind k : WorldBossKind.values()) slain[k.ordinal()] = kills.getInt(k.id);
        topHunter = tag.getInt("top");
        dryMonarchs = tag.getInt("dry");
        armorPieces = tag.getInt("armor");
    }
}
