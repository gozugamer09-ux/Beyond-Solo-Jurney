package com.beyondsolo.guild;

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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Every guild on the server, kept with the overworld's data. A hunter is in one guild at most; names and tags are
 * unique (whatever their case). A guild lasts until its master disbands it or its last member leaves.
 */
public final class GuildData extends SavedData {
    private static final String NAME = "beyondsolo_guilds";

    private final Map<UUID, Guild> guilds = new LinkedHashMap<>();
    private final Map<UUID, UUID> byMember = new HashMap<>();

    public static GuildData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(GuildData::new, GuildData::load, null), NAME);
    }

    /** The guild a hunter is in, or null. */
    @Nullable
    public Guild of(UUID player) {
        UUID id = byMember.get(player);
        return id == null ? null : guilds.get(id);
    }

    @Nullable
    public Guild get(UUID guild) {
        return guilds.get(guild);
    }

    public Collection<Guild> all() {
        return guilds.values();
    }

    /** The guild going by this name or tag (any case), or null. */
    @Nullable
    public Guild named(String nameOrTag) {
        String k = nameOrTag.trim().toLowerCase(Locale.ROOT);
        for (Guild g : guilds.values()) {
            if (g.name().toLowerCase(Locale.ROOT).equals(k) || g.tag().toLowerCase(Locale.ROOT).equals(k)) return g;
        }
        return null;
    }

    public boolean nameTaken(String name, @Nullable Guild except) {
        String k = name.trim().toLowerCase(Locale.ROOT);
        for (Guild g : guilds.values()) if (g != except && g.name().toLowerCase(Locale.ROOT).equals(k)) return true;
        return false;
    }

    public boolean tagTaken(String tag, @Nullable Guild except) {
        String k = tag.trim().toLowerCase(Locale.ROOT);
        for (Guild g : guilds.values()) if (g != except && g.tag().toLowerCase(Locale.ROOT).equals(k)) return true;
        return false;
    }

    Guild create(String name, String tag, int crest, int color, UUID master, String masterName, long day) {
        Guild old = of(master);
        if (old != null) remove(old, master);
        Guild g = new Guild(UUID.randomUUID(), name, tag, crest, color, master, masterName, day);
        guilds.put(g.id(), g);
        byMember.put(master, g.id());
        setDirty();
        return g;
    }

    void add(Guild guild, UUID player, String name, long day) {
        Guild old = of(player);
        if (old != null && old != guild) remove(old, player);
        guild.add(player, name, day);
        byMember.put(player, guild.id());
        setDirty();
    }

    void remove(Guild guild, UUID player) {
        guild.remove(player);
        if (guild.id().equals(byMember.get(player))) byMember.remove(player);
        setDirty();
    }

    void disband(Guild guild) {
        for (UUID m : guild.members()) {
            if (guild.id().equals(byMember.get(m))) byMember.remove(m);
        }
        guilds.remove(guild.id());
        setDirty();
    }

    void rename(Guild guild, UUID player, String name) {
        if (!name.equals(guild.name(player))) {
            guild.rename(player, name);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Guild g : guilds.values()) list.add(g.save());
        tag.put("guilds", list);
        return tag;
    }

    public static GuildData load(CompoundTag tag, HolderLookup.Provider registries) {
        GuildData d = new GuildData();
        List<Guild> loaded = new ArrayList<>();
        for (Tag t : tag.getList("guilds", Tag.TAG_COMPOUND)) loaded.add(Guild.load((CompoundTag) t));
        for (Guild g : loaded) {
            // a hunter found in two guilds stays in the first; a guild with nobody left is gone
            for (UUID m : g.members()) if (d.byMember.containsKey(m)) g.remove(m);
            if (g.size() == 0) continue;
            if (!g.has(g.master())) g.setMaster(g.members().get(0));
            d.guilds.put(g.id(), g);
            for (UUID m : g.members()) d.byMember.put(m, g.id());
        }
        return d;
    }
}
