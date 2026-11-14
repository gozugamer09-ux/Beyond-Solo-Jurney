package com.beyondsolo.system;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

/**
 * Everything the System knows about one player. Stored as a data attachment on the player,
 * kept through death, and mirrored to that player's client.
 *
 * <p>Change it through {@link SystemAPI} so events fire and the client stays in sync.
 * Other modules (quests, gates, combat) should register their own attachments instead of
 * adding fields here.
 */
public final class SystemData implements INBTSerializable<CompoundTag> {
    private boolean awakened;
    private PlayerRank rank = PlayerRank.E;
    private PlayerClass playerClass = PlayerClass.WARRIOR;
    private int level = 1;
    private long xp;
    private int statPoints;
    private final int[] stats = new int[Stat.values().length];
    private float mana;
    /** Extra max mana from skills (Arcane Font). Not saved: skills put it back on login. */
    private float manaBonus;
    private float exhaustion;
    private int collapseTicks;
    private boolean firstJoinHandled;
    private String objective = Objectives.NONE;
    private String title = "";

    public SystemData() {
        resetStats();
    }

    // ---- reads ----

    public boolean isAwakened() { return awakened; }
    public PlayerRank rank() { return rank; }
    public PlayerClass playerClass() { return playerClass; }
    public int level() { return level; }
    public long xp() { return xp; }
    public long xpToNextLevel() { return Progression.xpToNext(level); }
    public int statPoints() { return statPoints; }
    public int stat(Stat stat) { return stats[stat.ordinal()]; }
    public float mana() { return mana; }
    public float maxMana() { return 50 + (stat(Stat.INT) - Stat.BASE) * 5f + manaBonus; }
    public float manaBonus() { return manaBonus; }
    public float exhaustion() { return exhaustion; }
    public boolean isCollapsed() { return collapseTicks > 0; }
    public int collapseTicks() { return collapseTicks; }
    public boolean firstJoinHandled() { return firstJoinHandled; }
    public String objective() { return objective; }
    /** Optional title shown on the status window (e.g. earned from quests). Empty = none. */
    public String title() { return title; }

    // ---- writes (package-private or via SystemAPI) ----

    void setAwakened(boolean awakened) { this.awakened = awakened; }
    void setRank(PlayerRank rank) { this.rank = rank; }
    void setPlayerClass(PlayerClass playerClass) { this.playerClass = playerClass; }
    void setLevel(int level) { this.level = Math.max(1, level); }
    void setXp(long xp) { this.xp = Math.max(0, xp); }
    void setStatPoints(int points) { this.statPoints = Math.max(0, points); }
    void setStat(Stat stat, int value) { stats[stat.ordinal()] = Math.max(Stat.BASE, value); }
    void setMana(float mana) { this.mana = Mth.clamp(mana, 0, maxMana()); }
    void setManaBonus(float bonus) { this.manaBonus = Math.max(0, bonus); }
    void setExhaustion(float exhaustion) { this.exhaustion = Mth.clamp(exhaustion, 0, 100); }
    void setCollapseTicks(int ticks) { this.collapseTicks = Math.max(0, ticks); }
    void setFirstJoinHandled(boolean handled) { this.firstJoinHandled = handled; }
    void setObjective(String objective) { this.objective = objective == null ? Objectives.NONE : objective; }
    void setTitle(String title) { this.title = title == null ? "" : title; }

    void resetStats() {
        for (Stat s : Stat.values()) stats[s.ordinal()] = Stat.BASE;
    }

    /** Wipes progress but keeps the "first join already handled" flag. */
    void reset() {
        boolean joined = firstJoinHandled;
        CompoundTag fresh = new SystemData().serializeNBT(null);
        deserializeNBT(null, fresh);
        firstJoinHandled = joined;
    }

    // ---- save / sync ----

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("awakened", awakened);
        tag.putString("rank", rank.name());
        tag.putString("class", playerClass.name());
        tag.putInt("level", level);
        tag.putLong("xp", xp);
        tag.putInt("statPoints", statPoints);
        CompoundTag statTag = new CompoundTag();
        for (Stat s : Stat.values()) statTag.putInt(s.key(), stats[s.ordinal()]);
        tag.put("stats", statTag);
        tag.putFloat("mana", mana);
        tag.putFloat("manaBonus", manaBonus);
        tag.putFloat("exhaustion", exhaustion);
        tag.putInt("collapseTicks", collapseTicks);
        tag.putBoolean("firstJoinHandled", firstJoinHandled);
        tag.putString("objective", objective);
        tag.putString("title", title);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        awakened = tag.getBoolean("awakened");
        PlayerRank r = PlayerRank.byName(tag.getString("rank"));
        rank = r == null ? PlayerRank.E : r;
        PlayerClass c = PlayerClass.byName(tag.getString("class"));
        playerClass = c == null ? PlayerClass.WARRIOR : c;
        level = Math.max(1, tag.getInt("level"));
        xp = tag.getLong("xp");
        statPoints = tag.getInt("statPoints");
        CompoundTag statTag = tag.getCompound("stats");
        for (Stat s : Stat.values()) {
            stats[s.ordinal()] = statTag.contains(s.key()) ? Math.max(Stat.BASE, statTag.getInt(s.key())) : Stat.BASE;
        }
        manaBonus = tag.getFloat("manaBonus");
        mana = tag.getFloat("mana");
        exhaustion = tag.getFloat("exhaustion");
        collapseTicks = tag.getInt("collapseTicks");
        firstJoinHandled = tag.getBoolean("firstJoinHandled");
        objective = tag.getString("objective");
        title = tag.getString("title");
    }
}
