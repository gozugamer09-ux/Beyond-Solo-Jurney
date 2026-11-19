package com.beyondsolo.rival;

import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A hand a hunter hired at the association office ({@link Hires}): a porter who carries what falls, or a freelance
 * hunter who fights beside them. Paid up front; on the job until their hunter walks out of a gate they cleared together,
 * or until the job's time is up ({@code hire.minutes}). Kept in the hunter's {@link RivalRecord}, porter's pack and all.
 */
public final class Hire {
    public enum Kind {
        /** Carries: picks up what falls around their hunter and keeps it in their pack. Never fights. */
        PORTER,
        /** Fights beside their hunter, as a guild's hunter does in a raid. */
        HUNTER;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public final UUID id;
    public final Kind kind;
    public final String name;
    public final PlayerClass cls;
    public final int level;
    public final int skin;
    public final long fee;
    /** Overworld game time they were hired, and the time the job is over at the latest. */
    public final long hiredAt, until;
    /** A gate was cleared with them along: they go once their hunter is out of it. */
    boolean done;
    int kills;
    /** A porter's pack ({@code hire.porterSlots} stacks); empty for a hunter. */
    final NonNullList<ItemStack> pack;
    /** Their body in the world (not saved: it steps out beside their hunter again). */
    @Nullable transient UUID body;

    public Hire(UUID id, Kind kind, String name, PlayerClass cls, int level, int skin, long fee, long hiredAt, long until, int slots) {
        this.id = id;
        this.kind = kind;
        this.name = name;
        this.cls = cls;
        this.level = Math.max(1, Math.min(Balance.MAX_LEVEL, level));
        this.skin = skin;
        this.fee = fee;
        this.hiredAt = hiredAt;
        this.until = until;
        this.pack = NonNullList.withSize(kind == Kind.PORTER ? Math.max(9, slots) : 0, ItemStack.EMPTY);
    }

    public PlayerRank rank() {
        return Balance.rankFor(level);
    }

    public boolean porter() {
        return kind == Kind.PORTER;
    }

    public boolean done() {
        return done;
    }

    public int kills() {
        return kills;
    }

    public NonNullList<ItemStack> pack() {
        return pack;
    }

    /** Stacks in the pack. */
    public int carried() {
        int n = 0;
        for (ItemStack s : pack) if (!s.isEmpty()) n++;
        return n;
    }

    /** True once the job's time is up. */
    public boolean expired(long now) {
        return now >= until;
    }

    CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("kind", kind.name());
        t.putString("name", name);
        t.putString("class", cls.name());
        t.putInt("level", level);
        t.putInt("skin", skin);
        t.putLong("fee", fee);
        t.putLong("hired", hiredAt);
        t.putLong("until", until);
        if (done) t.putBoolean("done", true);
        t.putInt("kills", kills);
        t.putInt("slots", pack.size());
        if (!pack.isEmpty()) ContainerHelper.saveAllItems(t, pack, provider);
        return t;
    }

    @Nullable
    static Hire load(CompoundTag t, HolderLookup.Provider provider) {
        if (!t.hasUUID("id")) return null;
        Kind kind = "PORTER".equals(t.getString("kind")) ? Kind.PORTER : Kind.HUNTER;
        PlayerClass cls;
        try {
            cls = PlayerClass.valueOf(t.getString("class"));
        } catch (IllegalArgumentException e) {
            cls = PlayerClass.WARRIOR;
        }
        Hire h = new Hire(t.getUUID("id"), kind, t.getString("name"), cls, t.getInt("level"), t.getInt("skin"), t.getLong("fee"),
                t.getLong("hired"), t.getLong("until"), t.getInt("slots"));
        h.done = t.getBoolean("done");
        h.kills = t.getInt("kills");
        if (!h.pack.isEmpty()) ContainerHelper.loadAllItems(t, h.pack, provider);
        return h;
    }
}
