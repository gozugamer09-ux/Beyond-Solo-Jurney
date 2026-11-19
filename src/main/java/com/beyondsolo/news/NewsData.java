package com.beyondsolo.news;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The hunter news, kept with the overworld's data: the latest headlines (oldest first) and when the next word of the world comes. */
public final class NewsData extends SavedData {
    private static final String NAME = "beyondsolo_news";

    private final List<Headline> headlines = new ArrayList<>();
    private long nextId = 1;
    /** The world's time of the next headline about the world's other hunters (0: not planned yet). */
    long nextAmbient;

    public static NewsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(NewsData::new, NewsData::load, null), NAME);
    }

    /** The headlines, oldest first. */
    public List<Headline> headlines() {
        return Collections.unmodifiableList(headlines);
    }

    Headline add(long time, long day, String kind, String text, int accent, boolean breaking, int keep) {
        Headline h = new Headline(nextId++, time, day, kind, text, accent, breaking);
        headlines.add(h);
        while (headlines.size() > Math.max(1, keep)) headlines.remove(0);
        setDirty();
        return h;
    }

    /** Forgets every headline (commands). */
    public void clear() {
        headlines.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Headline h : headlines) list.add(h.save());
        tag.put("headlines", list);
        tag.putLong("nextId", nextId);
        tag.putLong("nextAmbient", nextAmbient);
        return tag;
    }

    private static NewsData load(CompoundTag tag, HolderLookup.Provider provider) {
        NewsData d = new NewsData();
        for (Tag e : tag.getList("headlines", Tag.TAG_COMPOUND)) d.headlines.add(Headline.load((CompoundTag) e));
        d.nextId = Math.max(1, tag.getLong("nextId"));
        for (Headline h : d.headlines) d.nextId = Math.max(d.nextId, h.id() + 1);
        d.nextAmbient = tag.getLong("nextAmbient");
        return d;
    }
}
