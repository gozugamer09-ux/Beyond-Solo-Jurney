package com.beyondsolo.association;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A hunter's file at the association ({@code beyondsolo:association}, kept through death): their merit and the
 * standing it has earned, the contracts, clears, break suppressions and exams on record, today's permits and the
 * broker's tips they have bought, and the gates the broker scouted for them.
 */
public final class AssociationRecord implements INBTSerializable<CompoundTag> {
    /** Scouting reports kept at most (the oldest go first). */
    public static final int SCOUTED_KEPT = 48;

    long merit;
    /** The highest standing reached (its pop-up is said once). */
    int standing;
    int contracts;
    int contractsFailed;
    int clears;
    int suppressions;
    int exams;
    int examsFailed;
    /** The in-game day from which the next exam may be taken. */
    long examFrom;
    long permitDay = -1;
    int permitsUsed;
    long tipDay = -1;
    int tipsBought;
    long bounties;
    final Set<UUID> scouted = new LinkedHashSet<>();

    public long merit() { return merit; }
    public Standing standing() { return Standing.of(merit); }
    public int contracts() { return contracts; }
    public int contractsFailed() { return contractsFailed; }
    public int clears() { return clears; }
    public int suppressions() { return suppressions; }
    public int exams() { return exams; }
    public int examsFailed() { return examsFailed; }
    public long examFrom() { return examFrom; }
    public long bounties() { return bounties; }

    /** Permits taken on {@code day}. */
    public int permitsUsed(long day) {
        return permitDay == day ? permitsUsed : 0;
    }

    void usePermit(long day) {
        if (permitDay != day) {
            permitDay = day;
            permitsUsed = 0;
        }
        permitsUsed++;
    }

    /** Tips bought on {@code day}. */
    public int tipsBought(long day) {
        return tipDay == day ? tipsBought : 0;
    }

    void buyTip(long day) {
        if (tipDay != day) {
            tipDay = day;
            tipsBought = 0;
        }
        tipsBought++;
    }

    public boolean scouted(UUID gate) {
        return scouted.contains(gate);
    }

    void scout(UUID gate) {
        scouted.remove(gate);
        scouted.add(gate);
        while (scouted.size() > SCOUTED_KEPT) scouted.remove(scouted.iterator().next());
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putLong("merit", merit);
        t.putInt("standing", standing);
        t.putInt("contracts", contracts);
        t.putInt("contractsFailed", contractsFailed);
        t.putInt("clears", clears);
        t.putInt("suppressions", suppressions);
        t.putInt("exams", exams);
        t.putInt("examsFailed", examsFailed);
        t.putLong("examFrom", examFrom);
        t.putLong("permitDay", permitDay);
        t.putInt("permitsUsed", permitsUsed);
        t.putLong("tipDay", tipDay);
        t.putInt("tipsBought", tipsBought);
        t.putLong("bounties", bounties);
        ListTag list = new ListTag();
        for (UUID id : scouted) list.add(NbtUtils.createUUID(id));
        t.put("scouted", list);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        merit = t.getLong("merit");
        standing = t.getInt("standing");
        contracts = t.getInt("contracts");
        contractsFailed = t.getInt("contractsFailed");
        clears = t.getInt("clears");
        suppressions = t.getInt("suppressions");
        exams = t.getInt("exams");
        examsFailed = t.getInt("examsFailed");
        examFrom = t.getLong("examFrom");
        permitDay = t.contains("permitDay") ? t.getLong("permitDay") : -1;
        permitsUsed = t.getInt("permitsUsed");
        tipDay = t.contains("tipDay") ? t.getLong("tipDay") : -1;
        tipsBought = t.getInt("tipsBought");
        bounties = t.getLong("bounties");
        scouted.clear();
        for (Tag e : t.getList("scouted", Tag.TAG_INT_ARRAY)) scouted.add(NbtUtils.loadUUID(e));
    }
}
