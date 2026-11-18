package com.beyondsolo.quest.situation;

import com.beyondsolo.quest.Quest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A running situation, as kept on its quest ({@link Quest#extra()}): which situation and stage it is, who it is about
 * (its target), when it started and what it has seen so far. Saved with the quest, so nothing is lost on logout.
 */
public final class Run {
    public static final String SITUATION = "sit";
    public static final String STAGE = "stage";
    public static final String TARGET = "target";
    /** The target's network id, for the marker the client draws over it. Refreshed while it is loaded. */
    public static final String TARGET_ID = "targetId";
    public static final String STARTED = "started";
    public static final String LOOPS = "loops";
    /** Lowest share of health the player fell to during the run (0..1). */
    public static final String LOW = "low";
    /** A marked spot shown as a waypoint: dimension and position. */
    public static final String WAYPOINT_DIM = "wpDim";
    public static final String WAYPOINT = "wp";
    /** The target's name as the player read it, kept for when the target itself is out of reach. */
    public static final String NAME = "name";
    /** Set when the target is someone to keep alive rather than something to bring down (the marker is drawn as a ward). */
    public static final String WARD = "ward";

    private final Quest quest;
    private final CompoundTag tag;

    private Run(Quest quest) {
        this.quest = quest;
        this.tag = quest.extra();
    }

    /** The run a quest belongs to, or null if no situation made it. */
    @Nullable
    public static Run of(Quest quest) {
        return quest.extra().contains(SITUATION) ? new Run(quest) : null;
    }

    /** Fresh run state for the first quest of a situation. */
    public static CompoundTag start(Situation situation, String stage, long now) {
        CompoundTag t = new CompoundTag();
        t.putString(SITUATION, situation.id());
        t.putString(STAGE, stage);
        t.putLong(STARTED, now);
        t.putFloat(LOW, 1f);
        return t;
    }

    /** The run state carried into the next stage's quest (target, counters and all), with the stage changed. */
    public CompoundTag next(String stage) {
        CompoundTag t = tag.copy();
        t.putString(STAGE, stage);
        return t;
    }

    public Quest quest() { return quest; }
    public CompoundTag tag() { return tag; }
    public String situation() { return tag.getString(SITUATION); }
    public String stage() { return tag.getString(STAGE); }
    public boolean is(String stage) { return stage.equals(stage()); }
    public long started() { return tag.getLong(STARTED); }
    public int loops() { return tag.getInt(LOOPS); }
    public float low() { return tag.contains(LOW) ? tag.getFloat(LOW) : 1f; }

    public void noteLow(float share) {
        if (share < low()) tag.putFloat(LOW, share);
    }

    @Nullable
    public UUID target() {
        return tag.hasUUID(TARGET) ? tag.getUUID(TARGET) : null;
    }

    public int targetId() {
        return tag.contains(TARGET_ID) ? tag.getInt(TARGET_ID) : -1;
    }

    public static void setTarget(CompoundTag t, Entity e) {
        t.putUUID(TARGET, e.getUUID());
        t.putInt(TARGET_ID, e.getId());
        Situations.markTarget(e);
    }

    /** Marks the target as someone to keep alive (true) or something to fight (false). */
    public static void setWard(CompoundTag t, boolean ward) {
        if (ward) t.putBoolean(WARD, true);
        else t.remove(WARD);
    }

    /** Remembers the target's name (see {@link #name}). */
    public static void setName(CompoundTag t, Component name, HolderLookup.Provider provider) {
        t.putString(NAME, Component.Serializer.toJson(name, provider));
    }

    /** The target's name as it was when the run noticed it, or "?" if it never had one. */
    public Component name(HolderLookup.Provider provider) {
        return name(tag, provider);
    }

    /** The name kept in run state (or any tag with a {@link #NAME}), or "?". */
    public static Component name(CompoundTag tag, HolderLookup.Provider provider) {
        String json = tag.getString(NAME);
        if (!json.isEmpty()) {
            try {
                Component c = Component.Serializer.fromJson(json, provider);
                if (c != null) return c;
            } catch (RuntimeException ignored) {
                // an unreadable name is only a name
            }
        }
        return Component.literal("?");
    }

    /** Keeps the target's network id current (it changes when the target is unloaded and loaded again). True if it changed. */
    public boolean refreshTargetId(Entity e) {
        if (targetId() == e.getId()) return false;
        tag.putInt(TARGET_ID, e.getId());
        return true;
    }

    /** A counter kept on the run. */
    public int count(String key) {
        return tag.getInt(key);
    }

    public void setCount(String key, int value) {
        tag.putInt(key, value);
    }

    /** A list of creatures the run follows (a pack, a villager's attackers). */
    public List<UUID> list(String key) {
        List<UUID> out = new ArrayList<>();
        for (Tag t : tag.getList(key, Tag.TAG_INT_ARRAY)) out.add(NbtUtils.loadUUID(t));
        return out;
    }

    public static void putList(CompoundTag t, String key, List<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) list.add(NbtUtils.createUUID(id));
        t.put(key, list);
    }

    public void putList(String key, List<UUID> ids) {
        putList(tag, key, ids);
    }

    public static void setWaypoint(CompoundTag t, ResourceKey<Level> dim, BlockPos pos) {
        t.putString(WAYPOINT_DIM, dim.location().toString());
        t.putLong(WAYPOINT, pos.asLong());
    }

    @Nullable
    public static BlockPos waypoint(Quest q, ResourceKey<Level> dim) {
        CompoundTag t = q.extra();
        if (!t.contains(WAYPOINT) || !dim.location().toString().equals(t.getString(WAYPOINT_DIM))) return null;
        return BlockPos.of(t.getLong(WAYPOINT));
    }

    @Nullable
    public static ResourceKey<Level> waypointDimension(Quest q) {
        ResourceLocation id = ResourceLocation.tryParse(q.extra().getString(WAYPOINT_DIM));
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }
}
