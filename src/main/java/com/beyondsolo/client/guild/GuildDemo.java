package com.beyondsolo.client.guild;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateRealm;
import com.beyondsolo.guild.GuildConfig;
import com.beyondsolo.guild.GuildHunter;
import com.beyondsolo.guild.GuildPerks;
import com.beyondsolo.guild.GuildRole;
import com.beyondsolo.guild.Recruits;
import com.beyondsolo.network.RaidEventPayload;
import com.beyondsolo.network.RaidSyncPayload;
import com.beyondsolo.raid.RaidConfig;
import com.beyondsolo.raid.RaidRole;
import com.beyondsolo.raid.Raids;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.UUID;

/**
 * Made-up guild and raid states for the UI test driver, shaped as {@code guild.Guilds.syncTag} and
 * {@code raid.Raids.syncTag} build them, so the GUILD page, the raid briefing and the raid HUD can be looked at with
 * one player on the server. The numbers a guild's level gives come from {@link GuildPerks}, as on a real server.
 */
public final class GuildDemo {
    private static final long SEED = 0x6A11D;
    private static final PlayerRank RAID_RANK = PlayerRank.B;

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("demo:" + name).getBytes());
    }

    private static UUID me() {
        var p = Minecraft.getInstance().player;
        return p == null ? new UUID(0, 1) : p.getUUID();
    }

    private static String myName() {
        var p = Minecraft.getInstance().player;
        return p == null ? "Dev" : p.getGameProfile().getName();
    }

    // ------------------------------------------------------------------ the guild

    /**
     * A guild state: "" a level 6 guild the player leads, "member" the same guild with the player a plain member,
     * "raid" with one of its raids mustering, "none" no guild and two invitations waiting.
     */
    public static CompoundTag guild(String variant) {
        CompoundTag t = new CompoundTag();
        long day = 37;
        t.putLong("foundCost", GuildConfig.FOUNDING_COST.get());
        t.putLong("day", day);
        t.putInt("baseMembers", GuildPerks.memberCap(1));
        ListTag invites = new ListTag();
        if (variant.equals("none")) {
            invites.add(invite("Crimson Wardens", "CRWD", 4, 1, 7, 9, "Mira", 20 * 214));
            invites.add(invite("Silver Tide", "TIDE", 9, 6, 3, 4, "Kael", 20 * 75));
            t.put("invites", invites);
            t.putBoolean("in", false);
            return t;
        }
        t.put("invites", invites);
        t.putBoolean("in", true);

        int level = 6;
        boolean member = variant.equals("member");
        UUID master = member ? id("kael") : me();
        t.putUUID("id", id("guild"));
        t.putString("name", "Nightfall Lanterns");
        t.putString("tag", "NFL");
        t.putInt("crest", 5);
        t.putInt("color", 4);
        t.putInt("level", level);
        long next = GuildPerks.xpToNext(level);
        t.putLong("xp", next * 62 / 100);
        t.putLong("xpNext", next);
        t.putLong("treasury", 48_250);
        t.putLong("founded", 11);
        t.putInt("raids", 3);
        t.putInt("gates", 41);
        t.putUUID("master", master);
        t.putString("role", (member ? GuildRole.MEMBER : GuildRole.MASTER).id());
        t.putInt("memberCap", GuildPerks.memberCap(level));
        t.putInt("rosterCap", GuildPerks.rosterCap(level));
        t.putDouble("goldBonus", GuildPerks.goldBonus(level));
        t.putDouble("xpBonus", GuildPerks.xpBonus(level));
        t.putString("permitRank", GuildPerks.permitRank(level).name());
        t.putDouble("discount", GuildPerks.permitDiscount(level));
        ListTag permits = new ListTag();
        for (PlayerRank r : PlayerRank.values()) {
            CompoundTag pr = new CompoundTag();
            pr.putString("rank", r.name());
            pr.putLong("price", GuildPerks.permitPrice(level, r));
            pr.putInt("needs", GuildPerks.permitLevel(r));
            permits.add(pr);
        }
        t.put("permits", permits);

        // the members: the player first, as the founder (or as one of them)
        var sys = ClientSystemData.get();
        ListTag members = new ListTag();
        members.add(member(me(), myName(), member ? GuildRole.MEMBER : GuildRole.MASTER, member ? 2_400 : 31_500, member ? 19 : 11, true,
                sys.isAwakened() && sys.rank() != null ? sys.rank().ordinal() : -1,
                sys.isAwakened() && sys.playerClass() != null ? sys.playerClass().ordinal() : -1, sys.hasSystem() ? sys.level() : 0));
        members.add(member(id("kael"), "Kael", member ? GuildRole.MASTER : GuildRole.OFFICER, 18_200, 11, true, PlayerRank.B.ordinal(), PlayerClass.MAGE.ordinal(), 47));
        members.add(member(id("mira"), "Mira", GuildRole.OFFICER, 9_750, 13, true, PlayerRank.C.ordinal(), PlayerClass.HEALER.ordinal(), 33));
        members.add(member(id("sora"), "Sora", GuildRole.MEMBER, 4_100, 20, true, PlayerRank.C.ordinal(), PlayerClass.ASSASSIN.ordinal(), 29));
        members.add(member(id("toru"), "Toru", GuildRole.MEMBER, 2_600, 24, false, -1, -1, 0));
        members.add(member(id("juno"), "Juno", GuildRole.MEMBER, 800, 31, true, PlayerRank.D.ordinal(), PlayerClass.GUARDIAN.ordinal(), 18));
        members.add(member(id("rin"), "Rin", GuildRole.MEMBER, 0, 35, false, -1, -1, 0));
        t.put("members", members);

        // the hired hunters: one hurt in the last raid, one out on a raid when one is underway
        boolean raid = variant.equals("raid");
        List<GuildHunter> hired = List.of(
                Recruits.of(RandomSource.create(SEED), PlayerRank.C, PlayerClass.GUARDIAN),
                Recruits.of(RandomSource.create(SEED + 1), PlayerRank.C, PlayerClass.RANGER),
                Recruits.of(RandomSource.create(SEED + 2), PlayerRank.B, PlayerClass.MAGE),
                Recruits.of(RandomSource.create(SEED + 3), PlayerRank.D, PlayerClass.HEALER));
        int[][] records = {{3, 41}, {2, 27}, {3, 58}, {1, 6}};
        ListTag hunters = new ListTag();
        for (int i = 0; i < hired.size(); i++) {
            CompoundTag ht = hired.get(i).save();
            ht.putInt("raids", records[i][0]);
            ht.putInt("kills", records[i][1]);
            ht.putLong("injured", i == 3 ? 20 * 60 * 7 + 20 * 24 : 0);
            ht.putBoolean("busy", raid && i < 2);
            hunters.add(ht);
        }
        t.put("hunters", hunters);
        RandomSource r = RandomSource.create(SEED + 10);
        ListTag pool = new ListTag();
        for (int i = 0; i < 4; i++) pool.add(Recruits.roll(r, level).save());
        t.put("recruits", pool);

        String first = hired.get(0).name(), second = hired.get(2).name(), founder = member ? "Kael" : myName();
        ListTag log = new ListTag();
        log.add(log(11, "founded", founder, "Nightfall Lanterns"));
        log.add(log(11, "joined", member ? myName() : "Kael"));
        log.add(log(13, "joined", "Mira"));
        log.add(log(14, "promoted", "Mira"));
        log.add(log(16, "deposit", founder, "20000"));
        log.add(log(17, "hired", first, founder));
        log.add(log(22, "permit", "Kael", PlayerRank.C.name()));
        log.add(log(22, "raid", PlayerRank.C.name()));
        log.add(log(26, "hired", second, "Mira"));
        log.add(log(31, "joined", "Juno"));
        log.add(log(33, "raid", PlayerRank.B.name()));
        log.add(log(33, "level", Integer.toString(level)));
        log.add(log(36, "deposit", "Sora", "1500"));
        t.put("log", log);

        if (raid) {
            var p = Minecraft.getInstance().player;
            CompoundTag rt = new CompoundTag();
            rt.putUUID("gate", id("gate"));
            rt.putInt("rank", RAID_RANK.ordinal());
            rt.putString("phase", "MUSTER");
            rt.putString("leader", "Kael");
            rt.putInt("raiders", 3);
            rt.putInt("hunters", 2);
            rt.putInt("x", p == null ? 140 : (int) p.getX() + 140);
            rt.putInt("z", p == null ? -60 : (int) p.getZ() - 60);
            rt.putBoolean("permit", true);
            rt.putInt("lieutenants", 3);
            t.put("raid", rt);
        }
        return t;
    }

    private static CompoundTag invite(String name, String tag, int crest, int color, int level, int size, String from, int ticks) {
        CompoundTag inv = new CompoundTag();
        inv.putUUID("id", id("guild:" + name));
        inv.putString("name", name);
        inv.putString("tag", tag);
        inv.putInt("crest", crest);
        inv.putInt("color", color);
        inv.putInt("level", level);
        inv.putInt("size", size);
        inv.putString("from", from);
        inv.putInt("ticks", ticks);
        return inv;
    }

    private static CompoundTag member(UUID id, String name, GuildRole role, long given, long joined, boolean online, int rank, int cls, int lvl) {
        CompoundTag mt = new CompoundTag();
        mt.putUUID("id", id);
        mt.putString("name", name);
        mt.putString("role", role.id());
        mt.putLong("given", given);
        mt.putLong("joined", joined);
        mt.putBoolean("online", online);
        mt.putInt("rank", rank);
        mt.putInt("cls", cls);
        mt.putInt("lvl", lvl);
        return mt;
    }

    private static CompoundTag log(long day, String key, String... args) {
        CompoundTag lt = new CompoundTag();
        lt.putLong("day", day);
        lt.putString("key", key);
        ListTag a = new ListTag();
        for (String s : args) a.add(StringTag.valueOf(s));
        lt.put("args", a);
        return lt;
    }

    // ------------------------------------------------------------------ the raid

    /**
     * A raid state: "" or "muster" the player leading a mustering B-Rank raid, "countdown" the same counting down,
     * "lieutenants" inside with a lieutenant still standing, "barrier" the boss behind its barrier, "boss" the boss
     * low and a cataclysm on its way, "none" no raid. Inside, the HUD only shows in a gate's cave.
     */
    public static RaidSyncPayload raid(String variant) {
        if (variant.equals("none")) return RaidSyncPayload.none();
        String phase = switch (variant) {
            case "", "muster" -> "MUSTER";
            case "countdown" -> "COUNTDOWN";
            default -> "RUNNING";
        };
        boolean inside = phase.equals("RUNNING");
        GateRealm realm = GateRealm.forSeed(RAID_RANK, SEED);
        BossKind boss = BossKind.forSeed(RAID_RANK, SEED, realm);
        var sys = ClientSystemData.get();
        PlayerClass myClass = sys.isAwakened() && sys.playerClass() != null ? sys.playerClass() : PlayerClass.WARRIOR;

        CompoundTag t = new CompoundTag();
        t.putBoolean("active", true);
        t.putUUID("gate", id("gate"));
        t.putString("phase", phase);
        t.putInt("rank", RAID_RANK.ordinal());
        t.putInt("realm", realm.ordinal());
        t.putUUID("leader", me());
        t.putString("leaderName", myName());
        t.putString("role", RaidRole.forClass(myClass).id());
        t.putString("status", inside ? "INSIDE" : "WAITING");
        t.putInt("countdown", phase.equals("COUNTDOWN") ? 20 * 17 : -1);
        t.putInt("maxSize", 8);
        t.putBoolean("bring", true);
        t.putInt("lieutenants", 3);
        t.putInt("lieutenantsLeft", switch (variant) {
            case "lieutenants" -> 1;
            case "barrier", "boss" -> 0;
            default -> 3;
        });
        t.putBoolean("barrier", variant.equals("barrier"));
        t.putBoolean("barrierDone", variant.equals("boss"));
        t.putInt("pylons", 4);
        t.putInt("pylonsLeft", variant.equals("barrier") ? 3 : variant.equals("boss") ? 0 : 4);
        t.putLong("elapsed", switch (variant) {
            case "lieutenants" -> 20 * 152;
            case "barrier" -> 20 * 318;
            case "boss" -> 20 * 447;
            default -> 0;
        });
        t.putFloat("reward", 1.45f);
        t.putLong("guildXp", GuildPerks.raidXp(RAID_RANK));
        var p = Minecraft.getInstance().player;
        t.putInt("x", p == null ? 140 : (int) p.getX() + 6);
        t.putInt("z", p == null ? -60 : (int) p.getZ() + 4);
        t.putInt("guildHunters", 3);

        // the guild's hunters coming along, and the raiders
        ListTag hunters = new ListTag();
        hunters.add(hunter(Recruits.of(RandomSource.create(SEED), PlayerRank.C, PlayerClass.GUARDIAN), inside ? 0.74f : 1f, false));
        hunters.add(hunter(Recruits.of(RandomSource.create(SEED + 1), PlayerRank.C, PlayerClass.RANGER), inside ? 0.41f : 1f, false));
        hunters.add(hunter(Recruits.of(RandomSource.create(SEED + 2), PlayerRank.B, PlayerClass.MAGE), variant.equals("boss") ? 0f : inside ? 0.88f : 1f,
                variant.equals("boss")));
        t.put("hunters", hunters);
        ListTag raiders = new ListTag();
        raiders.add(raider(me(), myName(), RaidRole.forClass(myClass), inside ? "INSIDE" : "WAITING", true, myClass.ordinal(),
                sys.hasSystem() ? sys.level() : 30, sys.isAwakened() && sys.rank() != null ? sys.rank().ordinal() : -1, inside ? 0.83f : 1f));
        raiders.add(raider(id("kael"), "Kael", RaidRole.STRIKER, inside ? "INSIDE" : "WAITING", true, PlayerClass.MAGE.ordinal(), 47, PlayerRank.B.ordinal(),
                inside ? 0.57f : 1f));
        raiders.add(raider(id("mira"), "Mira", RaidRole.SUPPORT, inside ? "INSIDE" : "WAITING", true, PlayerClass.HEALER.ordinal(), 33, PlayerRank.C.ordinal(),
                inside ? 0.95f : 1f));
        raiders.add(raider(id("juno"), "Juno", RaidRole.VANGUARD, variant.equals("boss") ? "FALLEN" : inside ? "INSIDE" : "WAITING", true,
                PlayerClass.GUARDIAN.ordinal(), 18, PlayerRank.D.ordinal(), variant.equals("boss") ? 0f : inside ? 0.36f : 1f));
        t.put("raiders", raiders);
        boolean awake = variant.equals("barrier") || variant.equals("boss");
        t.putBoolean("bossAwake", awake);
        t.putFloat("bossHp", switch (variant) {
            case "barrier" -> 0.7f;
            case "boss" -> 0.22f;
            default -> 1f;
        });
        t.putInt("cataclysm", variant.equals("boss") ? 20 * 4 : -1);
        return new RaidSyncPayload(t, realm.displayName(), boss.name(RAID_RANK));
    }

    private static CompoundTag hunter(GuildHunter h, float hp, boolean fallen) {
        CompoundTag ht = new CompoundTag();
        ht.putUUID("id", h.id());
        ht.putString("name", h.name());
        ht.putInt("cls", h.playerClass().ordinal());
        ht.putInt("level", h.level());
        ht.putInt("skin", h.skin());
        ht.putString("role", h.role().id());
        ht.putBoolean("fallen", fallen);
        ht.putFloat("hp", hp);
        return ht;
    }

    private static CompoundTag raider(UUID id, String name, RaidRole role, String status, boolean online, int cls, int level, int prank, float hp) {
        CompoundTag rt = new CompoundTag();
        rt.putUUID("id", id);
        rt.putString("name", name);
        rt.putString("role", role.id());
        rt.putString("status", status);
        rt.putBoolean("online", online);
        rt.putInt("cls", cls);
        rt.putInt("level", level);
        rt.putInt("prank", prank);
        rt.putFloat("hp", hp);
        return rt;
    }

    /** The results board of the demo raid: cleared (with its best hunter) or failed. */
    public static CompoundTag results(boolean cleared) {
        CompoundTag results = new CompoundTag();
        results.putBoolean("cleared", cleared);
        results.putInt("rank", RAID_RANK.ordinal());
        results.putLong("ticks", cleared ? 20 * 512 : 20 * 391);
        var sys = ClientSystemData.get();
        PlayerClass myClass = sys.isAwakened() && sys.playerClass() != null ? sys.playerClass() : PlayerClass.WARRIOR;
        ListTag lines = new ListTag();
        lines.add(line("Kael", false, RaidRole.STRIKER, 18_420, PlayerClass.MAGE, -1, false));
        lines.add(line(myName(), false, RaidRole.forClass(myClass), 15_960, myClass, -1, false));
        GuildHunter mage = Recruits.of(RandomSource.create(SEED + 2), PlayerRank.B, PlayerClass.MAGE);
        lines.add(line(mage.name(), true, RaidRole.STRIKER, 9_310, PlayerClass.MAGE, cleared ? 0 : -1, true));
        GuildHunter archer = Recruits.of(RandomSource.create(SEED + 1), PlayerRank.C, PlayerClass.RANGER);
        lines.add(line(archer.name(), true, RaidRole.STRIKER, 7_045, PlayerClass.RANGER, cleared ? 2 : -1, false));
        lines.add(line("Juno", false, RaidRole.VANGUARD, 3_880, PlayerClass.GUARDIAN, -1, true));
        GuildHunter tank = Recruits.of(RandomSource.create(SEED), PlayerRank.C, PlayerClass.GUARDIAN);
        lines.add(line(tank.name(), true, RaidRole.VANGUARD, 2_515, PlayerClass.GUARDIAN, cleared ? 3 : -1, false));
        lines.add(line("Mira", false, RaidRole.SUPPORT, 1_240, PlayerClass.HEALER, -1, false));
        results.put("lines", lines);
        if (cleared) {
            results.putString("mvp", "Kael");
            results.putFloat("scale", 1.45f);
            results.putLong("guildXp", GuildPerks.raidXp(RAID_RANK));
            results.putInt("caches", 3);
        }
        return results;
    }

    private static CompoundTag line(String name, boolean hunter, RaidRole role, double dealt, PlayerClass cls, int levels, boolean fallen) {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putBoolean("hunter", hunter);
        t.putString("role", role.id());
        t.putDouble("dealt", dealt);
        t.putInt("cls", cls.ordinal());
        if (levels >= 0) t.putInt("levels", levels);
        t.putBoolean("fallen", fallen);
        return t;
    }

    /** A raid moment as the server sends it: "inside", "countdown", "lieutenant", "unsealed", "barrier", "broken" or "cataclysm". */
    public static RaidEventPayload moment(String kind) {
        String k = Raids.KEY + "banner.";
        CompoundTag t = new CompoundTag();
        if (kind.equals("cataclysm")) {
            t.putInt("ticks", Raids.CHANNEL_TICKS);
            return new RaidEventPayload(RaidEventPayload.CATACLYSM, t, Component.translatable(k + "cataclysm"), Component.translatable(k + "cataclysm.sub"));
        }
        BossKind boss = BossKind.forSeed(RAID_RANK, SEED, GateRealm.forSeed(RAID_RANK, SEED));
        Object[] row = switch (kind) {
            case "inside" -> new Object[]{Component.translatable(k + "inside", RAID_RANK.displayName()), Component.translatable(k + "inside.sub", 3), Raids.GOLD, "crown"};
            case "countdown" -> new Object[]{Component.translatable(k + "countdown"), Component.translatable(k + "countdown.sub", RaidConfig.COUNTDOWN_SECONDS.get()), Raids.GOLD, "flag"};
            case "lieutenant" -> new Object[]{Component.translatable(k + "lieutenant"), Component.translatable(k + "lieutenant.sub", 2), Raids.GOLD, "skull"};
            case "unsealed" -> new Object[]{Component.translatable(k + "unsealed"), Component.translatable(k + "unsealed.sub", boss.name(RAID_RANK)), Raids.GOLD, "unlock"};
            case "broken" -> new Object[]{Component.translatable(k + "broken"),
                    Component.translatable(k + "broken.sub", Math.round(RaidConfig.STAGGER_DAMAGE.get() * 100)), 0xFFFFFF, "lightning"};
            default -> new Object[]{Component.translatable(k + "barrier"), Component.translatable(k + "barrier.sub", 4), Raids.BARRIER, "shield"};
        };
        t.putInt("color", (Integer) row[2]);
        t.putString("icon", (String) row[3]);
        return new RaidEventPayload(RaidEventPayload.BANNER, t, (Component) row[0], (Component) row[1]);
    }

    /** The demo raid's cave name, for the results board. */
    public static Component dungeon() {
        return GateRealm.forSeed(RAID_RANK, SEED).displayName();
    }

    private GuildDemo() {}
}
