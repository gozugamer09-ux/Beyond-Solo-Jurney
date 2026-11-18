package com.beyondsolo.party;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every party on the server, kept with the overworld's data: a party lasts until it is disbanded or only one member is
 * left, whoever logs out in between. A hunter is in one party at most.
 */
public final class PartyData extends SavedData {
    private static final String NAME = "beyondsolo_parties";

    private final Map<UUID, Party> parties = new LinkedHashMap<>();
    private final Map<UUID, UUID> byMember = new HashMap<>();

    public static PartyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(PartyData::new, PartyData::load, null), NAME);
    }

    /** The party a hunter is in, or null. */
    @Nullable
    public Party of(UUID player) {
        UUID id = byMember.get(player);
        return id == null ? null : parties.get(id);
    }

    @Nullable
    public Party get(UUID party) {
        return parties.get(party);
    }

    public Collection<Party> all() {
        return parties.values();
    }

    Party create(UUID leader, String leaderName) {
        Party old = of(leader);
        if (old != null) remove(old, leader);
        Party p = new Party(UUID.randomUUID(), leader, leaderName);
        parties.put(p.id(), p);
        byMember.put(leader, p.id());
        setDirty();
        return p;
    }

    void add(Party party, UUID player, String name) {
        Party old = of(player);
        if (old != null && old != party) remove(old, player);
        party.add(player, name);
        byMember.put(player, party.id());
        setDirty();
    }

    void remove(Party party, UUID player) {
        party.remove(player);
        if (party.id().equals(byMember.get(player))) byMember.remove(player);
        setDirty();
    }

    void disband(Party party) {
        for (UUID m : party.members()) {
            if (party.id().equals(byMember.get(m))) byMember.remove(m);
        }
        parties.remove(party.id());
        setDirty();
    }

    void setLeader(Party party, UUID player) {
        party.setLeader(player);
        setDirty();
    }

    void rename(Party party, UUID player, String name) {
        if (!name.equals(party.name(player))) {
            party.rename(player, name);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Party p : parties.values()) list.add(p.save());
        tag.put("parties", list);
        return tag;
    }

    public static PartyData load(CompoundTag tag, HolderLookup.Provider registries) {
        PartyData d = new PartyData();
        List<Party> loaded = new ArrayList<>();
        for (Tag t : tag.getList("parties", Tag.TAG_COMPOUND)) loaded.add(Party.load((CompoundTag) t));
        for (Party p : loaded) {
            // a party of one is no party; a hunter found in two keeps the first
            List<UUID> members = new ArrayList<>();
            for (UUID m : p.members()) {
                if (d.byMember.containsKey(m)) p.remove(m);
                else members.add(m);
            }
            if (members.size() < 2) continue;
            if (!p.has(p.leader())) p.setLeader(members.get(0));
            d.parties.put(p.id(), p);
            for (UUID m : members) d.byMember.put(m, p.id());
        }
        return d;
    }
}
