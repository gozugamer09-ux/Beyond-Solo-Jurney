package com.beyondsolo.news;

import net.minecraft.nbt.CompoundTag;

/**
 * One headline of the hunter news: when it ran (the world's time and day), what it is about ({@code kind}, for its
 * mark: {@link HunterNews#RANK}, ...), its text (a component as JSON, put in the reader's own language on their
 * screen), its colour, and whether it broke in as breaking news.
 */
public record Headline(long id, long time, long day, String kind, String text, int accent, boolean breaking) {
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("id", id);
        t.putLong("time", time);
        t.putLong("day", day);
        t.putString("kind", kind);
        t.putString("text", text);
        t.putInt("accent", accent);
        t.putBoolean("breaking", breaking);
        return t;
    }

    public static Headline load(CompoundTag t) {
        return new Headline(t.getLong("id"), t.getLong("time"), t.getLong("day"), t.getString("kind"), t.getString("text"),
                t.getInt("accent"), t.getBoolean("breaking"));
    }
}
