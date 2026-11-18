package com.beyondsolo.guild;

import com.beyondsolo.raid.RaidRole;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * A hunter a guild has hired (or one waiting in its recruit pool): an invented name, a class, a level (their rank
 * follows it, as a hunter's gates do: {@link Balance#rankFor}), a look, and their record with the guild. They come along
 * on the guild's raids ({@link GuildHunterEntity} is the hunter in the flesh, only while a raid lasts); one who falls in
 * a raid is hurt and sits out the next ones for a while ({@code guild.hunterInjuryMinutes}).
 */
public final class GuildHunter {
    /** Minecraft's own wide player skins: a hunter's look is one of them under their class's armor. */
    public static final String[] SKINS = {"alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"};

    private final UUID id;
    private final String name;
    private final PlayerClass cls;
    private int level;
    private final int skin;
    /** Overworld game time until which they are hurt (0: fit). */
    private long injuredUntil;
    private int raids;
    private int kills;
    /** Gold they cost (asked, or paid when hired). */
    private long cost;

    public GuildHunter(UUID id, String name, PlayerClass cls, int level, int skin, long cost) {
        this.id = id;
        this.name = name;
        this.cls = cls;
        this.level = Math.max(1, Math.min(Balance.MAX_LEVEL, level));
        this.skin = Math.floorMod(skin, SKINS.length);
        this.cost = cost;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public PlayerClass playerClass() { return cls; }
    public int level() { return level; }
    public int skin() { return skin; }
    public long cost() { return cost; }
    public int raids() { return raids; }
    public int kills() { return kills; }
    public long injuredUntil() { return injuredUntil; }

    /** Their rank, from their level. */
    public PlayerRank rank() {
        return Balance.rankFor(level);
    }

    public RaidRole role() {
        return RaidRole.forClass(cls);
    }

    /** True while they are still hurt from a raid. */
    public boolean injured(long now) {
        return injuredUntil > now;
    }

    void injure(long until) {
        injuredUntil = Math.max(injuredUntil, until);
    }

    void heal() {
        injuredUntil = 0;
    }

    /** Levels gained (capped at the level cap). Returns how many they actually gained. */
    int gainLevels(int levels) {
        int before = level;
        level = Math.min(Balance.MAX_LEVEL, level + Math.max(0, levels));
        return level - before;
    }

    void setLevel(int level) {
        this.level = Math.max(1, Math.min(Balance.MAX_LEVEL, level));
    }

    void countRaid() {
        raids++;
    }

    void countKills(int n) {
        kills += Math.max(0, n);
    }

    void setCost(long cost) {
        this.cost = cost;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("class", cls.name());
        t.putInt("level", level);
        t.putInt("skin", skin);
        t.putLong("injured", injuredUntil);
        t.putInt("raids", raids);
        t.putInt("kills", kills);
        t.putLong("cost", cost);
        return t;
    }

    public static GuildHunter load(CompoundTag t) {
        PlayerClass cls;
        try {
            cls = PlayerClass.valueOf(t.getString("class"));
        } catch (IllegalArgumentException e) {
            cls = PlayerClass.WARRIOR;
        }
        GuildHunter h = new GuildHunter(t.getUUID("id"), t.getString("name"), cls, t.getInt("level"), t.getInt("skin"), t.getLong("cost"));
        h.injuredUntil = t.getLong("injured");
        h.raids = t.getInt("raids");
        h.kills = t.getInt("kills");
        return h;
    }
}
