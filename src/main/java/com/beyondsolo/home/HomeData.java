package com.beyondsolo.home;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The homes of the world: which plot (tower) is whose, and how much of each has been built (the city and the tower
 * round a plot once, by {@link HomeBuilder#VERSION}; its floor up to the tier its owner has). A plot stays its owner's
 * for good, even if their home is taken away, so it is built where it was if they buy it again.
 */
public final class HomeData extends SavedData {
    private static final String NAME = "beyondsolo_home";

    /** The owner of each plot, by its index. */
    final List<UUID> owners = new ArrayList<>();
    /** The builder version the city and tower round each plot were built by (0: not yet). */
    final List<Integer> cityBuilt = new ArrayList<>();
    /** The tier each plot's floor is built up to. */
    final List<Integer> floorBuilt = new ArrayList<>();
    private final Map<UUID, Integer> byOwner = new HashMap<>();

    public static HomeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(HomeData::new, HomeData::load, null), NAME);
    }

    /** A hunter's plot, or -1 if they have none yet. */
    public int plot(UUID owner) {
        Integer i = byOwner.get(owner);
        return i == null ? -1 : i;
    }

    /** Whose plot {@code plot} is, or null. */
    @Nullable
    public UUID owner(int plot) {
        return plot >= 0 && plot < owners.size() ? owners.get(plot) : null;
    }

    public int plots() {
        return owners.size();
    }

    /** A hunter's plot, given to them now if they had none. */
    int claim(UUID owner) {
        int i = plot(owner);
        if (i >= 0) return i;
        owners.add(owner);
        cityBuilt.add(0);
        floorBuilt.add(0);
        i = owners.size() - 1;
        byOwner.put(owner, i);
        setDirty();
        return i;
    }

    int cityBuilt(int plot) {
        return plot >= 0 && plot < cityBuilt.size() ? cityBuilt.get(plot) : 0;
    }

    void setCityBuilt(int plot, int version) {
        if (plot < 0 || plot >= cityBuilt.size()) return;
        cityBuilt.set(plot, version);
        setDirty();
    }

    HomeTier floorBuilt(int plot) {
        return HomeTier.of(plot >= 0 && plot < floorBuilt.size() ? floorBuilt.get(plot) : 0);
    }

    void setFloorBuilt(int plot, HomeTier tier) {
        if (plot < 0 || plot >= floorBuilt.size()) return;
        floorBuilt.set(plot, tier.ordinal());
        setDirty();
    }

    private static HomeData load(CompoundTag t, HolderLookup.Provider provider) {
        HomeData d = new HomeData();
        ListTag list = t.getList("plots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            UUID owner = c.hasUUID("owner") ? c.getUUID("owner") : new UUID(0, i);
            d.owners.add(owner);
            d.cityBuilt.add(c.getInt("city"));
            d.floorBuilt.add(c.getInt("floor"));
            d.byOwner.put(owner, i);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag t, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (int i = 0; i < owners.size(); i++) {
            CompoundTag c = new CompoundTag();
            c.putUUID("owner", owners.get(i));
            c.putInt("city", cityBuilt.get(i));
            c.putInt("floor", floorBuilt.get(i));
            list.add(c);
        }
        t.put("plots", list);
        return t;
    }
}
