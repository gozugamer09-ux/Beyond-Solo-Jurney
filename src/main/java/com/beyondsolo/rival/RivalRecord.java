package com.beyondsolo.rival;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A hunter's dealings with the world's other hunters ({@code beyondsolo:rivalry}, kept through death): the races for
 * gates they won and lost, the gates they forced their way into, and the hands they have hired (a porter's pack goes
 * with it).
 */
public final class RivalRecord implements INBTSerializable<CompoundTag> {
    int raceWins;
    int raceLosses;
    int trespasses;
    /** Hands hired now. */
    final List<Hire> hires = new ArrayList<>();
    /** The day of {@link #taken} (the offers hired from an office that day, so they are not offered again). */
    long takenDay = -1;
    final Set<UUID> taken = new LinkedHashSet<>();

    public int raceWins() { return raceWins; }
    public int raceLosses() { return raceLosses; }
    public int trespasses() { return trespasses; }
    public List<Hire> hires() { return hires; }

    /** The hired porter, or null. */
    @Nullable
    public Hire porter() {
        for (Hire h : hires) if (h.porter()) return h;
        return null;
    }

    /** The hired hunters. */
    public int hunters() {
        int n = 0;
        for (Hire h : hires) if (!h.porter()) n++;
        return n;
    }

    @Nullable
    public Hire hire(UUID id) {
        for (Hire h : hires) if (h.id.equals(id)) return h;
        return null;
    }

    /** True if the offer was hired today. */
    boolean taken(long day, UUID offer) {
        return takenDay == day && taken.contains(offer);
    }

    void take(long day, UUID offer) {
        if (takenDay != day) {
            takenDay = day;
            taken.clear();
        }
        taken.add(offer);
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putInt("raceWins", raceWins);
        t.putInt("raceLosses", raceLosses);
        t.putInt("trespasses", trespasses);
        ListTag list = new ListTag();
        for (Hire h : hires) list.add(h.save(provider));
        t.put("hires", list);
        t.putLong("takenDay", takenDay);
        ListTag tk = new ListTag();
        for (UUID u : taken) tk.add(NbtUtils.createUUID(u));
        t.put("taken", tk);
        return t;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        raceWins = t.getInt("raceWins");
        raceLosses = t.getInt("raceLosses");
        trespasses = t.getInt("trespasses");
        hires.clear();
        for (Tag e : t.getList("hires", Tag.TAG_COMPOUND)) {
            Hire h = Hire.load((CompoundTag) e, provider);
            if (h != null) hires.add(h);
        }
        takenDay = t.contains("takenDay") ? t.getLong("takenDay") : -1;
        taken.clear();
        for (Tag e : t.getList("taken", Tag.TAG_INT_ARRAY)) taken.add(NbtUtils.loadUUID(e));
    }
}
