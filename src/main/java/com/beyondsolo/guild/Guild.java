package com.beyondsolo.guild;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A guild: its name, tag, crest and colours, its master and members (each with their role, the day they joined and
 * what they have given it), its level and XP, its treasury, the hunters it has hired and its recent history. Kept in
 * {@link GuildData}; changed only through {@link Guilds}.
 */
public final class Guild {
    /** How many lines of history a guild keeps. */
    public static final int LOG_SIZE = 14;

    /** One member: the name they went by when last seen, their role, when they joined, what they gave. */
    public static final class Member {
        String name;
        GuildRole role;
        final long joinedDay;
        /** Guild XP earned for the guild (clears, raids) plus gold paid in, a hundredth of it. */
        long contribution;

        Member(String name, GuildRole role, long joinedDay) {
            this.name = name;
            this.role = role;
            this.joinedDay = joinedDay;
        }

        public String name() { return name; }
        public GuildRole role() { return role; }
        public long joinedDay() { return joinedDay; }
        public long contribution() { return contribution; }
    }

    /** A line of the guild's history: the day, its lang key's last part, and its arguments as plain words. */
    public record LogLine(long day, String key, List<String> args) {}

    private final UUID id;
    private String name;
    private String tag;
    private int crest;
    private int color;
    private UUID master;
    private final Map<UUID, Member> members = new LinkedHashMap<>();
    private int level = 1;
    private long xp;
    private long treasury;
    private final long foundedDay;
    private int raidsCleared;
    private int gatesCleared;
    private final List<GuildHunter> hunters = new ArrayList<>();
    /** The day the recruit pool was drawn, and who is in it (those hired are taken out). */
    long recruitDay = -1;
    final List<GuildHunter> recruits = new ArrayList<>();
    private final List<LogLine> log = new ArrayList<>();

    Guild(UUID id, String name, String tag, int crest, int color, UUID master, String masterName, long day) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.crest = crest;
        this.color = color;
        this.master = master;
        this.foundedDay = day;
        members.put(master, new Member(masterName, GuildRole.MASTER, day));
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String tag() { return tag; }
    public int crest() { return crest; }
    public int color() { return color; }
    public UUID master() { return master; }
    public int level() { return level; }
    public long xp() { return xp; }
    public long treasury() { return treasury; }
    public long foundedDay() { return foundedDay; }
    public int raidsCleared() { return raidsCleared; }
    public int gatesCleared() { return gatesCleared; }

    public boolean isMaster(UUID player) {
        return master.equals(player);
    }

    /** Members in the order they joined (the founder first). */
    public List<UUID> members() {
        return new ArrayList<>(members.keySet());
    }

    @Nullable
    public Member member(UUID player) {
        return members.get(player);
    }

    public int size() {
        return members.size();
    }

    public boolean has(UUID player) {
        return members.containsKey(player);
    }

    public String name(UUID player) {
        Member m = members.get(player);
        return m == null ? "?" : m.name;
    }

    public GuildRole role(UUID player) {
        Member m = members.get(player);
        return m == null ? GuildRole.MEMBER : m.role;
    }

    /** The hunters the guild has hired, in the order they were hired. */
    public List<GuildHunter> hunters() {
        return hunters;
    }

    @Nullable
    public GuildHunter hunter(UUID id) {
        for (GuildHunter h : hunters) if (h.id().equals(id)) return h;
        return null;
    }

    public List<LogLine> log() {
        return log;
    }

    // ------------------------------------------------------------------ changes (through GuildData, which saves)

    void add(UUID player, String name, long day) {
        members.put(player, new Member(name, GuildRole.MEMBER, day));
    }

    void remove(UUID player) {
        members.remove(player);
    }

    void rename(UUID player, String name) {
        Member m = members.get(player);
        if (m != null) m.name = name;
    }

    void setRole(UUID player, GuildRole role) {
        Member m = members.get(player);
        if (m != null) m.role = role;
    }

    /** Makes {@code player} the master; the old master becomes an officer. */
    void setMaster(UUID player) {
        if (!members.containsKey(player)) return;
        Member old = members.get(master);
        if (old != null && !master.equals(player)) old.role = GuildRole.OFFICER;
        master = player;
        members.get(player).role = GuildRole.MASTER;
    }

    void contribute(UUID player, long amount) {
        Member m = members.get(player);
        if (m != null) m.contribution += Math.max(0, amount);
    }

    void setLevel(int level) {
        this.level = level;
    }

    void setXp(long xp) {
        this.xp = Math.max(0, xp);
    }

    void setTreasury(long gold) {
        this.treasury = Math.max(0, gold);
    }

    void countRaid() {
        raidsCleared++;
    }

    void countGate() {
        gatesCleared++;
    }

    void addHunter(GuildHunter h) {
        hunters.add(h);
    }

    boolean removeHunter(UUID id) {
        return hunters.removeIf(h -> h.id().equals(id));
    }

    void restyle(String name, String tag, int crest, int color) {
        this.name = name;
        this.tag = tag;
        this.crest = crest;
        this.color = color;
    }

    void log(long day, String key, String... args) {
        log.add(new LogLine(day, key, List.of(args)));
        while (log.size() > LOG_SIZE) log.remove(0);
    }

    // ------------------------------------------------------------------ saving

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("tag", tag);
        t.putInt("crest", crest);
        t.putInt("color", color);
        t.putUUID("master", master);
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Member> e : members.entrySet()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("id", e.getKey());
            m.putString("name", e.getValue().name);
            m.putString("role", e.getValue().role.id());
            m.putLong("joined", e.getValue().joinedDay);
            m.putLong("given", e.getValue().contribution);
            list.add(m);
        }
        t.put("members", list);
        t.putInt("level", level);
        t.putLong("xp", xp);
        t.putLong("treasury", treasury);
        t.putLong("founded", foundedDay);
        t.putInt("raids", raidsCleared);
        t.putInt("gates", gatesCleared);
        ListTag hired = new ListTag();
        for (GuildHunter h : hunters) hired.add(h.save());
        t.put("hunters", hired);
        t.putLong("recruitDay", recruitDay);
        ListTag pool = new ListTag();
        for (GuildHunter h : recruits) pool.add(h.save());
        t.put("recruits", pool);
        ListTag history = new ListTag();
        for (LogLine l : log) {
            CompoundTag line = new CompoundTag();
            line.putLong("day", l.day());
            line.putString("key", l.key());
            ListTag args = new ListTag();
            for (String a : l.args()) args.add(StringTag.valueOf(a));
            line.put("args", args);
            history.add(line);
        }
        t.put("log", history);
        return t;
    }

    static Guild load(CompoundTag t) {
        UUID master = t.getUUID("master");
        Guild g = new Guild(t.getUUID("id"), t.getString("name"), t.getString("tag"), t.getInt("crest"), t.getInt("color"), master, "?",
                t.getLong("founded"));
        g.members.clear();
        for (Tag tag : t.getList("members", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) tag;
            Member member = new Member(m.getString("name"), GuildRole.byId(m.getString("role")), m.getLong("joined"));
            member.contribution = m.getLong("given");
            g.members.put(m.getUUID("id"), member);
        }
        // a guild always has exactly one master: the saved one if still a member, else its first officer or member
        if (!g.members.containsKey(master) && !g.members.isEmpty()) {
            UUID next = null;
            for (Map.Entry<UUID, Member> e : g.members.entrySet()) {
                if (e.getValue().role == GuildRole.OFFICER) {
                    next = e.getKey();
                    break;
                }
            }
            g.master = next != null ? next : g.members.keySet().iterator().next();
        }
        for (Map.Entry<UUID, Member> e : g.members.entrySet()) {
            if (e.getKey().equals(g.master)) e.getValue().role = GuildRole.MASTER;
            else if (e.getValue().role == GuildRole.MASTER) e.getValue().role = GuildRole.OFFICER;
        }
        g.level = Math.max(1, t.getInt("level"));
        g.xp = t.getLong("xp");
        g.treasury = t.getLong("treasury");
        g.raidsCleared = t.getInt("raids");
        g.gatesCleared = t.getInt("gates");
        for (Tag tag : t.getList("hunters", Tag.TAG_COMPOUND)) g.hunters.add(GuildHunter.load((CompoundTag) tag));
        g.recruitDay = t.contains("recruitDay") ? t.getLong("recruitDay") : -1;
        for (Tag tag : t.getList("recruits", Tag.TAG_COMPOUND)) g.recruits.add(GuildHunter.load((CompoundTag) tag));
        for (Tag tag : t.getList("log", Tag.TAG_COMPOUND)) {
            CompoundTag line = (CompoundTag) tag;
            List<String> args = new ArrayList<>();
            for (Tag a : line.getList("args", Tag.TAG_STRING)) args.add(a.getAsString());
            g.log.add(new LogLine(line.getLong("day"), line.getString("key"), args));
        }
        return g;
    }
}
