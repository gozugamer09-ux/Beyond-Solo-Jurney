package com.beyondsolo.association;

import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The gates the association has claimed, kept with the overworld's data: an exam gate (only its examinee may go in)
 * and a gate whose clearing rights a hunter took with a permit (only they and their party may go in). A claim ends
 * with its gate, or when the exam fails.
 */
public final class AssociationData extends SavedData {
    private static final String NAME = "beyondsolo_association";

    public enum Kind { EXAM, PERMIT }

    /** A gate claimed for one hunter. */
    public record Claim(Kind kind, UUID holder, String holderName, PlayerRank rank, long since) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("kind", kind.name());
            t.putUUID("holder", holder);
            t.putString("name", holderName);
            t.putString("rank", rank.name());
            t.putLong("since", since);
            return t;
        }

        @Nullable
        static Claim load(CompoundTag t) {
            if (!t.hasUUID("holder")) return null;
            Kind kind = "EXAM".equals(t.getString("kind")) ? Kind.EXAM : Kind.PERMIT;
            PlayerRank rank = PlayerRank.byName(t.getString("rank"));
            return new Claim(kind, t.getUUID("holder"), t.getString("name"), rank == null ? PlayerRank.E : rank, t.getLong("since"));
        }
    }

    private final Map<UUID, Claim> claims = new LinkedHashMap<>();

    public static AssociationData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(AssociationData::new, AssociationData::load, null), NAME);
    }

    @Nullable
    public Claim claim(UUID gate) {
        return claims.get(gate);
    }

    public Map<UUID, Claim> claims() {
        return claims;
    }

    public void put(UUID gate, Claim claim) {
        claims.put(gate, claim);
        setDirty();
    }

    @Nullable
    public Claim remove(UUID gate) {
        Claim c = claims.remove(gate);
        if (c != null) setDirty();
        return c;
    }

    /** The gate a hunter's exam is in, or null. */
    @Nullable
    public UUID examOf(UUID player) {
        for (var e : claims.entrySet()) if (e.getValue().kind() == Kind.EXAM && e.getValue().holder().equals(player)) return e.getKey();
        return null;
    }

    /** Drops every claim of a hunter's (their record was wiped). */
    public void forget(UUID player) {
        if (claims.values().removeIf(c -> c.holder().equals(player))) setDirty();
    }

    /** Drops the claims on gates no longer around. */
    public void keepOnly(Collection<UUID> gates) {
        if (claims.keySet().removeIf(id -> !gates.contains(id))) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (var e : claims.entrySet()) {
            CompoundTag t = e.getValue().save();
            t.putUUID("gate", e.getKey());
            list.add(t);
        }
        tag.put("claims", list);
        return tag;
    }

    private static AssociationData load(CompoundTag tag, HolderLookup.Provider provider) {
        AssociationData d = new AssociationData();
        for (Tag e : tag.getList("claims", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            Claim c = Claim.load(t);
            if (c != null && t.hasUUID("gate")) d.claims.put(t.getUUID("gate"), c);
        }
        return d;
    }
}
