package com.beyondsolo.chronicle.ranking;

import com.beyondsolo.economy.Economy;
import com.beyondsolo.enemy.HiddenLevel;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.tower.TowerAPI;
import com.beyondsolo.tower.TowerData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The players of this world as the rankings last saw them, so a hunter who is offline still has their place. Updated
 * when they log in or out, every minute while they play, and when they open the rankings. Kept with the overworld.
 */
public final class RankingData extends SavedData {
    private static final String NAME = "beyondsolo_rankings";

    /** One player's standing. */
    public static final class Hunter {
        public final UUID id;
        public String name = "";
        public int rank;
        public int playerClass;
        public String title = "";
        public float level;
        public int floor;
        public int spire;
        public final long[] rush = new long[TowerData.TIERS];
        public int gates;
        /** Their best place among hunters so far (0 for none yet): climbing past it is announced. */
        public int bestPlace;

        Hunter(UUID id) {
            this.id = id;
            Arrays.fill(rush, -1);
        }
    }

    private final Map<UUID, Hunter> hunters = new LinkedHashMap<>();

    public static RankingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(RankingData::new, RankingData::load, null), NAME);
    }

    public Collection<Hunter> all() {
        return Collections.unmodifiableCollection(hunters.values());
    }

    public Hunter get(UUID id) {
        return hunters.get(id);
    }

    /** Takes the player's standing now. Players who have not been evaluated are not ranked. */
    public Hunter update(ServerPlayer p) {
        SystemData d = SystemAPI.get(p);
        if (!d.isAwakened()) return null;
        Hunter h = hunters.computeIfAbsent(p.getUUID(), Hunter::new);
        h.name = p.getGameProfile().getName();
        h.rank = d.rank().ordinal();
        h.playerClass = d.playerClass().ordinal();
        h.title = d.title();
        h.level = HiddenLevel.hunterLevel(d);
        TowerData t = TowerAPI.data(p);
        h.floor = t.highest;
        h.spire = t.spireBest;
        System.arraycopy(t.rushBest, 0, h.rush, 0, Math.min(h.rush.length, t.rushBest.length));
        h.gates = Economy.data(p).totalClears();
        setDirty();
        return h;
    }

    public void forget(UUID id) {
        if (hunters.remove(id) != null) setDirty();
    }

    void setBestPlace(Hunter h, int place) {
        h.bestPlace = place;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Hunter h : hunters.values()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("id", h.id);
            e.putString("name", h.name);
            e.putInt("rank", h.rank);
            e.putInt("class", h.playerClass);
            e.putString("title", h.title);
            e.putFloat("level", h.level);
            e.putInt("floor", h.floor);
            e.putInt("spire", h.spire);
            e.putLongArray("rush", h.rush);
            e.putInt("gates", h.gates);
            e.putInt("best", h.bestPlace);
            list.add(e);
        }
        tag.put("hunters", list);
        return tag;
    }

    private static RankingData load(CompoundTag tag, HolderLookup.Provider provider) {
        RankingData data = new RankingData();
        ListTag list = tag.getList("hunters", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (!e.hasUUID("id")) continue;
            Hunter h = new Hunter(e.getUUID("id"));
            h.name = e.getString("name");
            h.rank = e.getInt("rank");
            h.playerClass = e.getInt("class");
            h.title = e.getString("title");
            h.level = e.getFloat("level");
            h.floor = e.getInt("floor");
            h.spire = e.getInt("spire");
            long[] rush = e.getLongArray("rush");
            for (int t = 0; t < h.rush.length; t++) h.rush[t] = t < rush.length ? rush[t] : -1;
            h.gates = e.getInt("gates");
            h.bestPlace = e.getInt("best");
            data.hunters.put(h.id, h);
        }
        return data;
    }
}
