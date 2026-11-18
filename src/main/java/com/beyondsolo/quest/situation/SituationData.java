package com.beyondsolo.quest.situation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player's situations leave behind for later (attachment {@code situations}, kept through death): things that
 * will come back for them ({@link FollowUp}s: a creature they fled from, one that killed them), and a few counts.
 */
public final class SituationData implements INBTSerializable<CompoundTag> {
    /** Something that comes back: its kind (see {@link FollowUps}), when, and what it needs to know. */
    public record FollowUp(String kind, long due, CompoundTag data) {}

    private final List<FollowUp> followUps = new ArrayList<>();
    /** The creature that last killed the player, while it is worth coming back for (see {@code Revenge}). */
    @Nullable private CompoundTag lastKiller;
    private int nemesesSlain;
    private int secretsFound;

    public List<FollowUp> followUps() { return followUps; }
    @Nullable public CompoundTag lastKiller() { return lastKiller; }
    public void setLastKiller(@Nullable CompoundTag killer) { lastKiller = killer; }
    public int nemesesSlain() { return nemesesSlain; }
    public void countNemesis() { nemesesSlain++; }
    public int secretsFound() { return secretsFound; }
    public void countSecret() { secretsFound++; }

    public void clear() {
        followUps.clear();
        lastKiller = null;
        nemesesSlain = 0;
        secretsFound = 0;
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (FollowUp f : followUps) {
            CompoundTag t = new CompoundTag();
            t.putString("kind", f.kind());
            t.putLong("due", f.due());
            t.put("data", f.data().copy());
            list.add(t);
        }
        tag.put("followUps", list);
        if (lastKiller != null) tag.put("lastKiller", lastKiller.copy());
        tag.putInt("nemeses", nemesesSlain);
        tag.putInt("secrets", secretsFound);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        clear();
        for (Tag t : tag.getList("followUps", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            followUps.add(new FollowUp(c.getString("kind"), c.getLong("due"), c.getCompound("data")));
        }
        lastKiller = tag.contains("lastKiller") ? tag.getCompound("lastKiller") : null;
        nemesesSlain = tag.getInt("nemeses");
        secretsFound = tag.getInt("secrets");
    }
}
