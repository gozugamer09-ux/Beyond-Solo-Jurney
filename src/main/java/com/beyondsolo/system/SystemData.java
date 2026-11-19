package com.beyondsolo.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
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
    /** The player has the System at all (it binds to them in the awakening trial; evaluation comes after). */
    private boolean hasSystem;
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
    /** Where the objective leads (the Evaluation Zone the System marked for a newcomer), or null. */
    @Nullable
    private GlobalPos objectiveTarget;
    private String title = "";

    public SystemData() {
        resetStats();
    }

    // ---- reads ----

    public boolean isAwakened() { return awakened; }
    /** True once the System is bound to the player. Everyone who has been evaluated has it. */
    public boolean hasSystem() { return hasSystem || awakened; }
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
    /** Where the objective leads: drawn in the world, on the objective chip and on Xaero's map. Null: nowhere marked. */
    @Nullable
    public GlobalPos objectiveTarget() { return objectiveTarget; }
    /** Optional title shown on the status window (e.g. earned from quests). Empty = none. */
    public String title() { return title; }

    // ---- writes (package-private or via SystemAPI) ----

    void setAwakened(boolean awakened) { this.awakened = awakened; }
    void setHasSystem(boolean has) { this.hasSystem = has; }
    void setRank(PlayerRank rank) { this.rank = rank; }
    void setPlayerClass(PlayerClass playerClass) { this.playerClass = playerClass; }
    void setLevel(int level) { this.level = Math.max(1, Math.min(Progression.MAX_LEVEL, level)); }
    void setXp(long xp) { this.xp = Math.max(0, xp); }
    void setStatPoints(int points) { this.statPoints = Math.max(0, points); }
    void setStat(Stat stat, int value) { stats[stat.ordinal()] = Math.max(Stat.BASE, value); }
    void setMana(float mana) { this.mana = Mth.clamp(mana, 0, maxMana()); }
    void setManaBonus(float bonus) { this.manaBonus = Math.max(0, bonus); }
    void setExhaustion(float exhaustion) { this.exhaustion = Mth.clamp(exhaustion, 0, 100); }
    void setCollapseTicks(int ticks) { this.collapseTicks = Math.max(0, ticks); }
    void setFirstJoinHandled(boolean handled) { this.firstJoinHandled = handled; }
    void setObjective(String objective) { this.objective = objective == null ? Objectives.NONE : objective; }
    void setObjectiveTarget(@Nullable GlobalPos target) { this.objectiveTarget = target; }
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
        tag.putBoolean("hasSystem", hasSystem);
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
        if (objectiveTarget != null) {
            tag.putString("objectiveTargetDim", objectiveTarget.dimension().location().toString());
            tag.putLong("objectiveTarget", objectiveTarget.pos().asLong());
        }
        tag.putString("title", title);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        awakened = tag.getBoolean("awakened");
        // saves from before the awakening trial: whoever was evaluated already has the System
        hasSystem = tag.contains("hasSystem") ? tag.getBoolean("hasSystem") : awakened;
        PlayerRank r = PlayerRank.byName(tag.getString("rank"));
        rank = r == null ? PlayerRank.E : r;
        PlayerClass c = PlayerClass.byName(tag.getString("class"));
        playerClass = c == null ? PlayerClass.WARRIOR : c;
        // saves from before the level cap came down to 100 keep the cap
        level = Math.max(1, Math.min(Progression.MAX_LEVEL, tag.getInt("level")));
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
        ResourceLocation targetDim = tag.contains("objectiveTarget") ? ResourceLocation.tryParse(tag.getString("objectiveTargetDim")) : null;
        objectiveTarget = targetDim == null ? null
                : GlobalPos.of(ResourceKey.create(Registries.DIMENSION, targetDim), BlockPos.of(tag.getLong("objectiveTarget")));
        title = tag.getString("title");
    }
}
