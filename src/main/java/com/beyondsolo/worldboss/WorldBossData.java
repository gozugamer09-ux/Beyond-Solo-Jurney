package com.beyondsolo.worldboss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The world's great threats, kept with the overworld's data: the one abroad now (at most one at a time), when the next
 * comes, and how many have been slain (monarchs only come once world bosses have fallen). Beside it, the ruler abroad
 * ({@link #ruler()}, at most one): rulers come to make war on a monarch abroad or to pass sentence on a crowned hunter,
 * so one can be abroad at the same time as the great threat ({@code com.beyondsolo.ruler.Rulers}).
 */
public final class WorldBossData extends SavedData {
    private static final String NAME = "beyondsolo_world_bosses";

    /** The great threat abroad now: a world boss that rose, or a monarch coming down (or landed). */
    public static final class Event {
        public final UUID id;
        public final WorldBossKind kind;
        /** Where it rose or lands, and the land it roams. */
        public final BlockPos home;
        public final long started;
        /** A monarch's landing (game time); 0 once it has landed, and for world bosses. */
        long landAt;
        /** The boss, once it is in the world. */
        @Nullable UUID entity;
        /** Where it was last seen (for the compass while its land is out of reach). */
        BlockPos last;
        /** When a hunter last came near or hurt it (game time). */
        long engaged;
        /** Called by a seal or a command: it doesn't move the schedule. */
        final boolean called;
        /** A ruler's: why it came ({@code Rulers}' reasons), and whom for (the monarch it makes war on, the hunter it judges). */
        int reason;
        @Nullable UUID quarry;

        public Event(UUID id, WorldBossKind kind, BlockPos home, long started, long landAt, boolean called) {
            this.id = id;
            this.kind = kind;
            this.home = home;
            this.started = started;
            this.landAt = landAt;
            this.last = home;
            this.engaged = started;
            this.called = called;
        }

        public long landAt() {
            return landAt;
        }

        public boolean descending() {
            return landAt > 0;
        }

        @Nullable
        public UUID entity() {
            return entity;
        }

        public BlockPos last() {
            return last;
        }

        public long engaged() {
            return engaged;
        }

        public boolean called() {
            return called;
        }

        public int reason() {
            return reason;
        }

        @Nullable
        public UUID quarry() {
            return quarry;
        }

        public void setReason(int reason, @Nullable UUID quarry) {
            this.reason = reason;
            this.quarry = quarry;
        }

        public void setLandAt(long landAt) {
            this.landAt = landAt;
        }

        public void setEntity(@Nullable UUID entity) {
            this.entity = entity;
        }

        public void setLast(BlockPos last) {
            this.last = last;
        }

        public void setEngaged(long engaged) {
            this.engaged = engaged;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", id);
            t.putString("kind", kind.id);
            t.putLong("home", home.asLong());
            t.putLong("started", started);
            t.putLong("landAt", landAt);
            if (entity != null) t.putUUID("entity", entity);
            t.putLong("last", last.asLong());
            t.putLong("engaged", engaged);
            t.putBoolean("called", called);
            if (reason != 0) t.putInt("reason", reason);
            if (quarry != null) t.putUUID("quarry", quarry);
            return t;
        }

        @Nullable
        static Event load(CompoundTag t) {
            WorldBossKind kind = WorldBossKind.byId(t.getString("kind"));
            if (kind == null || !t.hasUUID("id")) return null;
            Event e = new Event(t.getUUID("id"), kind, BlockPos.of(t.getLong("home")), t.getLong("started"), t.getLong("landAt"),
                    t.getBoolean("called"));
            e.entity = t.hasUUID("entity") ? t.getUUID("entity") : null;
            e.last = BlockPos.of(t.getLong("last"));
            e.engaged = t.getLong("engaged");
            e.reason = t.getInt("reason");
            e.quarry = t.hasUUID("quarry") ? t.getUUID("quarry") : null;
            return e;
        }
    }

    @Nullable private Event current;
    /** The ruler abroad, beside the great threat. */
    @Nullable private Event ruler;
    private int rulerKills;
    /** When the next ruler may come to judge a crowned hunter (game time); -1 until one is crowned. */
    private long nextSentenceAt = -1;
    /** When the next great threat comes on its own (game time); -1 until a hunter is strong enough to face one. */
    private long nextAt = -1;
    private int worldKills;
    private int monarchKills;
    @Nullable private WorldBossKind lastKind;

    public static WorldBossData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(WorldBossData::new, WorldBossData::load, null), NAME);
    }

    @Nullable
    public Event current() {
        return current;
    }

    void setCurrent(@Nullable Event e) {
        current = e;
        if (e != null) lastKind = e.kind;
        setDirty();
    }

    @Nullable
    public Event ruler() {
        return ruler;
    }

    public void setRuler(@Nullable Event e) {
        ruler = e;
        setDirty();
    }

    public int rulerKills() {
        return rulerKills;
    }

    public long nextSentenceAt() {
        return nextSentenceAt;
    }

    public void setNextSentenceAt(long t) {
        nextSentenceAt = t;
        setDirty();
    }

    public long nextAt() {
        return nextAt;
    }

    void setNextAt(long t) {
        nextAt = t;
        setDirty();
    }

    public int worldKills() {
        return worldKills;
    }

    public int monarchKills() {
        return monarchKills;
    }

    public void countKill(WorldBossKind kind) {
        if (kind.ruler()) rulerKills++;
        else if (kind.monarch()) monarchKills++;
        else worldKills++;
        setDirty();
    }

    @Nullable
    WorldBossKind lastKind() {
        return lastKind;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (current != null) tag.put("current", current.save());
        tag.putLong("nextAt", nextAt);
        tag.putInt("worldKills", worldKills);
        tag.putInt("monarchKills", monarchKills);
        if (lastKind != null) tag.putString("lastKind", lastKind.id);
        if (ruler != null) tag.put("ruler", ruler.save());
        tag.putInt("rulerKills", rulerKills);
        tag.putLong("nextSentenceAt", nextSentenceAt);
        return tag;
    }

    private static WorldBossData load(CompoundTag tag, HolderLookup.Provider provider) {
        WorldBossData d = new WorldBossData();
        if (tag.contains("current")) d.current = Event.load(tag.getCompound("current"));
        d.nextAt = tag.contains("nextAt") ? tag.getLong("nextAt") : -1;
        d.worldKills = tag.getInt("worldKills");
        d.monarchKills = tag.getInt("monarchKills");
        d.lastKind = WorldBossKind.byId(tag.getString("lastKind"));
        if (tag.contains("ruler")) d.ruler = Event.load(tag.getCompound("ruler"));
        d.rulerKills = tag.getInt("rulerKills");
        d.nextSentenceAt = tag.contains("nextSentenceAt") ? tag.getLong("nextSentenceAt") : -1;
        return d;
    }
}
