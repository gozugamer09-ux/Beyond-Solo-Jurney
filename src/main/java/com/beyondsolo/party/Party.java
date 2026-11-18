package com.beyondsolo.party;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A party of hunters: its leader and its members in the order they joined (the leader among them), with the name each
 * went by when last seen, so members who are away still show by name. Kept in {@link PartyData}; changed only through
 * {@link Parties}.
 */
public final class Party {
    private final UUID id;
    private UUID leader;
    private final Map<UUID, String> members = new LinkedHashMap<>();

    Party(UUID id, UUID leader, String leaderName) {
        this.id = id;
        this.leader = leader;
        members.put(leader, leaderName);
    }

    public UUID id() {
        return id;
    }

    public UUID leader() {
        return leader;
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    /** Members in the order they joined. */
    public List<UUID> members() {
        return new ArrayList<>(members.keySet());
    }

    public int size() {
        return members.size();
    }

    public boolean has(UUID player) {
        return members.containsKey(player);
    }

    /** The name a member went by when last seen. */
    public String name(UUID player) {
        return members.getOrDefault(player, "?");
    }

    void add(UUID player, String name) {
        members.put(player, name);
    }

    void remove(UUID player) {
        members.remove(player);
    }

    void rename(UUID player, String name) {
        if (members.containsKey(player)) members.put(player, name);
    }

    void setLeader(UUID player) {
        if (members.containsKey(player)) leader = player;
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putUUID("leader", leader);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, String> e : members.entrySet()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("id", e.getKey());
            m.putString("name", e.getValue());
            list.add(m);
        }
        t.put("members", list);
        return t;
    }

    static Party load(CompoundTag t) {
        UUID leader = t.getUUID("leader");
        Party p = new Party(t.getUUID("id"), leader, "?");
        p.members.clear();
        for (Tag tag : t.getList("members", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) tag;
            p.members.put(m.getUUID("id"), m.getString("name"));
        }
        if (!p.members.containsKey(leader) && !p.members.isEmpty()) p.leader = p.members.keySet().iterator().next();
        return p;
    }
}
