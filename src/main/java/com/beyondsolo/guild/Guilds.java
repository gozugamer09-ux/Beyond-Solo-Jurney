package com.beyondsolo.guild;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.network.GuildActionPayload;
import com.beyondsolo.network.GuildEventPayload;
import com.beyondsolo.network.GuildSyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.system.SystemEvent;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Guilds: hunters who band together for good, under a name and a crest. A master founds one (it costs gold,
 * {@code guild.foundingCost}) and leads it; officers help run it; members fight under its tag. Unlike a party, a guild
 * lasts: it keeps a treasury members pay into, grows in level as its members clear gates and raids (more members,
 * hired hunters, a little more gold and XP for everyone, raid permits of higher ranks: {@link GuildPerks}), hires
 * hunters who come along on its raids ({@link GuildHunter}, {@link Recruits}) and requests raid gates from the System
 * ({@code raid.Raids}).
 *
 * <p>Every change goes through here and is saved in {@link GuildData}; the client hears of it through
 * {@link GuildSyncPayload}. Invitations are kept in memory for {@code guild.inviteSeconds}. A member's tag shows in
 * front of their name in chat, over their head and in the player list.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Guilds {
    /** Lang key prefix of every answer. */
    public static final String KEY = "guild.beyondsolo.";
    /** Title for founding a guild. */
    public static final String FOUNDER_TITLE = "title.beyondsolo.guild_founder";

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N} '&.\\-]*");
    private static final Pattern TAG = Pattern.compile("[A-Z0-9]{2,4}");
    public static final int NAME_MIN = 3, NAME_MAX = 24;

    /** What an action came to: done, or why not (a chat line, or the GUILD page's toast). */
    public record Outcome(boolean ok, Component message) {
        public static Outcome ok(String key, Object... args) {
            return new Outcome(true, Component.translatable(KEY + key, args));
        }

        public static Outcome no(String key, Object... args) {
            return new Outcome(false, Component.translatable(KEY + key, args));
        }
    }

    private record Invite(UUID guild, String from, long expires) {}

    /** Open invitations by invited hunter, by guild, oldest first. */
    private static final Map<UUID, LinkedHashMap<UUID, Invite>> INVITES = new HashMap<>();
    /** Hunters whose page shows an invitation that ran out: synced once more. */
    private static final Set<UUID> DIRTY = new HashSet<>();

    // ------------------------------------------------------------------ reads

    public static GuildData data(MinecraftServer server) {
        return GuildData.get(server);
    }

    @Nullable
    public static Guild of(MinecraftServer server, UUID player) {
        return data(server).of(player);
    }

    @Nullable
    public static Guild of(Player player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : of(server, player.getUUID());
    }

    /** True if two different hunters are in the same guild. */
    public static boolean together(MinecraftServer server, @Nullable UUID a, @Nullable UUID b) {
        if (a == null || b == null || a.equals(b)) return false;
        Guild g = of(server, a);
        return g != null && g.has(b);
    }

    public static List<ServerPlayer> online(MinecraftServer server, Guild g) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID m : g.members()) {
            ServerPlayer p = OnlinePlayers.get(server, m);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** The day of the world (the overworld's), as the guild's history counts it. */
    public static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    /** Today's recruit pool of a guild. */
    public static List<GuildHunter> recruits(MinecraftServer server, Guild g) {
        long day = day(server);
        boolean fresh = g.recruitDay != day;
        List<GuildHunter> pool = Recruits.pool(g, day);
        if (fresh) data(server).setDirty();
        return pool;
    }

    /** The guild's hunters fit to fight now (not hurt). */
    public static List<GuildHunter> fit(MinecraftServer server, Guild g) {
        long now = server.overworld().getGameTime();
        List<GuildHunter> out = new ArrayList<>();
        for (GuildHunter h : g.hunters()) if (!h.injured(now)) out.add(h);
        return out;
    }

    /** Guild ids this hunter has open invitations from, oldest first. */
    public static List<UUID> invitesFor(ServerPlayer player) {
        Map<UUID, Invite> map = INVITES.get(player.getUUID());
        if (map == null) return List.of();
        long now = player.server.getTickCount();
        List<UUID> out = new ArrayList<>();
        for (Invite i : map.values()) if (i.expires > now) out.add(i.guild);
        return out;
    }

    // ------------------------------------------------------------------ founding

    /** The name as it will be kept: trimmed, single spaces. */
    public static String cleanName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

    public static String cleanTag(String tag) {
        return tag == null ? "" : tag.trim().toUpperCase(Locale.ROOT);
    }

    /** Why a name won't do (a lang key's last part), or null. */
    @Nullable
    public static String nameProblem(String name) {
        if (name.length() < NAME_MIN || name.length() > NAME_MAX) return "found.name_length";
        if (!NAME.matcher(name).matches()) return "found.name_chars";
        return null;
    }

    @Nullable
    public static String tagProblem(String tag) {
        return TAG.matcher(tag).matches() ? null : "found.tag_bad";
    }

    /** Founds a guild with {@code player} as its master, for {@code guild.foundingCost} gold. */
    public static Outcome found(ServerPlayer player, String rawName, String rawTag, int crest, int color) {
        MinecraftServer server = player.server;
        GuildData data = data(server);
        if (!SystemAPI.isAwakened(player)) return Outcome.no("found.unevaluated");
        if (data.of(player.getUUID()) != null) return Outcome.no("found.already");
        String name = cleanName(rawName), tag = cleanTag(rawTag);
        String problem = nameProblem(name);
        if (problem == null) problem = tagProblem(tag);
        if (problem != null) return Outcome.no(problem, NAME_MIN, NAME_MAX);
        if (data.nameTaken(name, null)) return Outcome.no("found.name_taken", name);
        if (data.tagTaken(tag, null)) return Outcome.no("found.tag_taken", tag);
        long cost = GuildConfig.FOUNDING_COST.get();
        if (!Economy.spend(player, cost, Economy.Reason.GUILD)) return Outcome.no("found.gold", cost);
        Guild g = data.create(name, tag, GuildColors.clampCrest(crest), GuildColors.clampColor(color), player.getUUID(),
                player.getGameProfile().getName(), day(server));
        g.log(day(server), "founded", player.getGameProfile().getName(), name);
        data.setDirty();
        INVITES.remove(player.getUUID());
        refreshName(player);
        SystemAPI.setTitle(player, FOUNDER_TITLE);
        PacketDistributor.sendToPlayer(player, new GuildEventPayload(GuildEventPayload.FOUNDED, g.crest(), Component.literal(name)));
        // word gets round: everyone else hears of a new guild
        Component line = tag().append(Component.translatable(KEY + "founded.world", Component.literal(player.getGameProfile().getName())
                .withStyle(ChatFormatting.AQUA), styled(g)).withStyle(ChatFormatting.GRAY));
        for (ServerPlayer p : OnlinePlayers.all(server)) if (p != player) p.sendSystemMessage(line);
        syncGuild(server, g);
        return Outcome.ok("founded", name, tag);
    }

    // ------------------------------------------------------------------ membership

    /** {@code from} (the master or an officer) invites {@code to} into the guild. */
    public static Outcome invite(ServerPlayer from, ServerPlayer to) {
        MinecraftServer server = from.server;
        Guild g = of(from);
        if (g == null) return Outcome.no("none");
        if (!g.role(from.getUUID()).manages()) return Outcome.no("not_officer");
        if (to == from) return Outcome.no("invite.self");
        if (g.has(to.getUUID())) return Outcome.no("invite.member", to.getDisplayName());
        int cap = GuildPerks.memberCap(g.level());
        if (g.size() >= cap) return Outcome.no("full", cap);
        long expires = server.getTickCount() + GuildConfig.INVITE_SECONDS.get() * 20L;
        LinkedHashMap<UUID, Invite> map = INVITES.computeIfAbsent(to.getUUID(), k -> new LinkedHashMap<>());
        boolean again = map.remove(g.id()) != null;
        map.put(g.id(), new Invite(g.id(), from.getGameProfile().getName(), expires));

        MutableComponent line = Component.translatable(KEY + "invited", Component.literal(from.getGameProfile().getName())
                .withStyle(ChatFormatting.AQUA), styled(g), g.size(), cap).withStyle(ChatFormatting.GRAY);
        Guild theirs = of(to);
        if (theirs != null) line.append(" ").append(Component.translatable(KEY + "invited.leaves", styled(theirs)).withStyle(ChatFormatting.DARK_GRAY));
        line.append(" ").append(button("accept", ChatFormatting.GREEN, "/guild accept " + g.tag()))
                .append(" ").append(button("decline", ChatFormatting.RED, "/guild decline " + g.tag()));
        to.sendSystemMessage(tag().append(line));
        Notifications.send(to, SystemNotification.of(SystemNotification.Kind.QUEST,
                Component.translatable("notify.beyondsolo.guild.invite.title"),
                Component.translatable("notify.beyondsolo.guild.invite.body", from.getGameProfile().getName(), g.name())));
        sync(to);
        return Outcome.ok(again ? "invite.again" : "invite.sent", to.getDisplayName());
    }

    /** Accepts the invitation of a guild (null: the newest). */
    public static Outcome accept(ServerPlayer player, @Nullable UUID guild) {
        MinecraftServer server = player.server;
        GuildData data = data(server);
        Invite inv = take(player, guild);
        if (inv == null) return Outcome.no(guild == null ? "accept.none" : "accept.expired");
        Guild g = data.get(inv.guild);
        if (g == null) {
            sync(player);
            return Outcome.no("accept.gone");
        }
        if (g.has(player.getUUID())) {
            sync(player);
            return Outcome.no("accept.already");
        }
        int cap = GuildPerks.memberCap(g.level());
        if (g.size() >= cap) {
            sync(player);
            return Outcome.no("accept.full", cap);
        }
        Guild old = data.of(player.getUUID());
        if (old != null) {
            if (old.isMaster(player.getUUID()) && old.size() > 1) {
                // they can't just walk away from a guild they lead
                INVITES.computeIfAbsent(player.getUUID(), k -> new LinkedHashMap<>()).put(inv.guild, inv);
                return Outcome.no("accept.master", old.name());
            }
            leave(player);
        }
        data.add(g, player.getUUID(), player.getGameProfile().getName(), day(server));
        g.log(day(server), "joined", player.getGameProfile().getName());
        INVITES.remove(player.getUUID());
        for (ServerPlayer m : online(server, g)) {
            if (m != player) inform(m, Component.translatable(KEY + "joined.other", name(player), g.name()), false);
        }
        refreshName(player);
        syncGuild(server, g);
        return Outcome.ok("joined", g.name());
    }

    /** Turns down a guild's invitation (null: the newest). */
    public static Outcome decline(ServerPlayer player, @Nullable UUID guild) {
        Invite inv = take(player, guild);
        if (inv == null) return Outcome.no(guild == null ? "accept.none" : "accept.expired");
        Guild g = data(player.server).get(inv.guild);
        if (g != null) {
            for (ServerPlayer m : online(player.server, g)) {
                if (g.role(m.getUUID()).manages()) inform(m, Component.translatable(KEY + "declined.other", name(player)), false);
            }
        }
        sync(player);
        return Outcome.ok("declined", g == null ? "?" : g.name());
    }

    /**
     * Leaves the guild. A master who leaves hands it to their first officer (else the member who has been in longest);
     * the last one out ends it.
     */
    public static Outcome leave(ServerPlayer player) {
        MinecraftServer server = player.server;
        GuildData data = data(server);
        Guild g = data.of(player.getUUID());
        if (g == null) return Outcome.no("none");
        if (g.size() <= 1) {
            dissolve(server, g);
            return Outcome.ok("left.ended", g.name());
        }
        boolean led = g.isMaster(player.getUUID());
        if (led) g.setMaster(successor(g, player.getUUID()));
        data.remove(g, player.getUUID());
        g.log(day(server), "left", player.getGameProfile().getName());
        data.setDirty();
        refreshName(player);
        sync(player);
        for (ServerPlayer m : online(server, g)) inform(m, Component.translatable(KEY + "left.other", name(player)), false);
        if (led) announceMaster(server, g);
        syncGuild(server, g);
        return Outcome.ok("left", g.name());
    }

    /** The master (anyone) or an officer (plain members only) removes a member. */
    public static Outcome kick(ServerPlayer by, UUID member) {
        MinecraftServer server = by.server;
        GuildData data = data(server);
        Guild g = data.of(by.getUUID());
        if (g == null) return Outcome.no("none");
        GuildRole mine = g.role(by.getUUID());
        if (!mine.manages()) return Outcome.no("not_officer");
        if (member.equals(by.getUUID())) return Outcome.no("kick.self");
        if (!g.has(member)) return Outcome.no("not_member");
        if (mine != GuildRole.MASTER && g.role(member) != GuildRole.MEMBER) return Outcome.no("kick.rank");
        String name = g.name(member);
        data.remove(g, member);
        g.log(day(server), "kicked", name, by.getGameProfile().getName());
        data.setDirty();
        ServerPlayer gone = OnlinePlayers.get(server, member);
        if (gone != null) {
            inform(gone, Component.translatable(KEY + "kicked.you", g.name()), true);
            refreshName(gone);
            sync(gone);
        }
        for (ServerPlayer m : online(server, g)) {
            if (m != by) inform(m, Component.translatable(KEY + "kicked.other", name), false);
        }
        syncGuild(server, g);
        return Outcome.ok("kicked", name);
    }

    /** The master makes a member an officer. */
    public static Outcome promote(ServerPlayer by, UUID member) {
        return setRole(by, member, GuildRole.OFFICER);
    }

    /** The master makes an officer a member again. */
    public static Outcome demote(ServerPlayer by, UUID member) {
        return setRole(by, member, GuildRole.MEMBER);
    }

    private static Outcome setRole(ServerPlayer by, UUID member, GuildRole role) {
        MinecraftServer server = by.server;
        Guild g = of(by);
        if (g == null) return Outcome.no("none");
        if (!g.isMaster(by.getUUID())) return Outcome.no("not_master", g.name(g.master()));
        if (!g.has(member)) return Outcome.no("not_member");
        if (member.equals(by.getUUID())) return Outcome.no("role.self");
        if (g.role(member) == role) return Outcome.no(role == GuildRole.OFFICER ? "promote.already" : "demote.already", g.name(member));
        g.setRole(member, role);
        String key = role == GuildRole.OFFICER ? "promoted" : "demoted";
        g.log(day(server), key, g.name(member));
        data(server).setDirty();
        ServerPlayer them = OnlinePlayers.get(server, member);
        if (them != null) inform(them, Component.translatable(KEY + key + ".you", g.name()), false);
        syncGuild(server, g);
        return Outcome.ok(key, g.name(member));
    }

    /** The master hands the guild to another member (and becomes an officer). */
    public static Outcome transfer(ServerPlayer by, UUID member) {
        MinecraftServer server = by.server;
        Guild g = of(by);
        if (g == null) return Outcome.no("none");
        if (!g.isMaster(by.getUUID())) return Outcome.no("not_master", g.name(g.master()));
        if (!g.has(member)) return Outcome.no("not_member");
        if (member.equals(by.getUUID())) return Outcome.no("transfer.self");
        g.setMaster(member);
        g.log(day(server), "master", g.name(member));
        data(server).setDirty();
        announceMaster(server, g);
        syncGuild(server, g);
        return Outcome.ok("transferred", g.name(member));
    }

    /** The master ends the guild: its members go their own way, its hunters too; its treasury goes to the master. */
    public static Outcome disband(ServerPlayer by) {
        MinecraftServer server = by.server;
        Guild g = of(by);
        if (g == null) return Outcome.no("none");
        if (!g.isMaster(by.getUUID())) return Outcome.no("not_master", g.name(g.master()));
        for (ServerPlayer m : online(server, g)) {
            if (m != by) inform(m, Component.translatable(KEY + "disbanded.other", name(by), g.name()), true);
        }
        long left = g.treasury();
        dissolve(server, g);
        if (left > 0) Economy.add(by, left, Economy.Reason.GUILD);
        return Outcome.ok("disbanded", g.name());
    }

    // ------------------------------------------------------------------ the treasury

    /** A member pays gold into the treasury (a hundredth of it counts as guild XP, {@code guild.goldPerXp}). */
    public static Outcome deposit(ServerPlayer player, long amount) {
        MinecraftServer server = player.server;
        Guild g = of(player);
        if (g == null) return Outcome.no("none");
        if (amount <= 0) return Outcome.no("deposit.amount");
        if (!Economy.spend(player, amount, Economy.Reason.GUILD)) return Outcome.no("deposit.gold", amount);
        g.setTreasury(g.treasury() + amount);
        g.log(day(server), "deposit", player.getGameProfile().getName(), Long.toString(amount));
        int per = GuildConfig.GOLD_PER_XP.get();
        data(server).setDirty();
        if (per > 0 && amount / per > 0) addXp(server, g, amount / per, player.getUUID());
        else syncGuild(server, g);
        return Outcome.ok("deposited", amount);
    }

    /** The master takes gold out of the treasury. */
    public static Outcome withdraw(ServerPlayer player, long amount) {
        MinecraftServer server = player.server;
        Guild g = of(player);
        if (g == null) return Outcome.no("none");
        if (!g.isMaster(player.getUUID())) return Outcome.no("not_master", g.name(g.master()));
        if (amount <= 0) return Outcome.no("deposit.amount");
        if (g.treasury() < amount) return Outcome.no("withdraw.short", g.treasury());
        g.setTreasury(g.treasury() - amount);
        Economy.add(player, amount, Economy.Reason.GUILD);
        g.log(day(server), "withdraw", player.getGameProfile().getName(), Long.toString(amount));
        data(server).setDirty();
        syncGuild(server, g);
        return Outcome.ok("withdrew", amount);
    }

    /** Takes gold from the treasury for something the guild buys. False (nothing taken) if it is short. */
    public static boolean spend(MinecraftServer server, Guild g, long amount) {
        if (amount < 0 || g.treasury() < amount) return false;
        g.setTreasury(g.treasury() - amount);
        data(server).setDirty();
        return true;
    }

    // ------------------------------------------------------------------ hunters

    /** The master or an officer hires a hunter from today's pool, paid from the treasury. */
    public static Outcome hire(ServerPlayer player, UUID recruit) {
        MinecraftServer server = player.server;
        Guild g = of(player);
        if (g == null) return Outcome.no("none");
        if (!g.role(player.getUUID()).manages()) return Outcome.no("not_officer");
        GuildHunter h = null;
        for (GuildHunter r : recruits(server, g)) if (r.id().equals(recruit)) h = r;
        if (h == null) return Outcome.no("hire.gone");
        int cap = GuildPerks.rosterCap(g.level());
        if (g.hunters().size() >= cap) return Outcome.no("hire.full", cap);
        if (!spend(server, g, h.cost())) return Outcome.no("hire.treasury", h.cost(), g.treasury());
        g.recruits.remove(h);
        g.addHunter(h);
        g.log(day(server), "hired", h.name(), player.getGameProfile().getName());
        data(server).setDirty();
        for (ServerPlayer m : online(server, g)) {
            if (m != player) inform(m, Component.translatable(KEY + "hired.other", name(player), h.name()), false);
        }
        syncGuild(server, g);
        return Outcome.ok("hired", h.name(), h.cost());
    }

    /** The master or an officer lets a hunter go. */
    public static Outcome dismiss(ServerPlayer player, UUID hunter) {
        MinecraftServer server = player.server;
        Guild g = of(player);
        if (g == null) return Outcome.no("none");
        if (!g.role(player.getUUID()).manages()) return Outcome.no("not_officer");
        GuildHunter h = g.hunter(hunter);
        if (h == null) return Outcome.no("dismiss.gone");
        if (com.beyondsolo.raid.Raids.hunterBusy(server, hunter)) return Outcome.no("dismiss.raid", h.name());
        g.removeHunter(hunter);
        g.log(day(server), "dismissed", h.name(), player.getGameProfile().getName());
        data(server).setDirty();
        syncGuild(server, g);
        return Outcome.ok("dismissed", h.name());
    }

    /** A hunter fell in a raid: hurt for {@code hunters.injuryMinutes}. */
    public static void hunterFell(MinecraftServer server, UUID guild, UUID hunter) {
        Guild g = data(server).get(guild);
        GuildHunter h = g == null ? null : g.hunter(hunter);
        if (h == null) return;
        h.injure(server.overworld().getGameTime() + GuildConfig.INJURY_MINUTES.get() * 1200L);
        data(server).setDirty();
        syncGuild(server, g);
    }

    /** A hunter came through a raid: counted, its kills, and the levels it learned there. Returns levels gained. */
    public static int hunterRaided(MinecraftServer server, UUID guild, UUID hunter, int levels, int kills) {
        Guild g = data(server).get(guild);
        GuildHunter h = g == null ? null : g.hunter(hunter);
        if (h == null) return 0;
        h.countRaid();
        h.countKills(kills);
        int gained = h.gainLevels(levels);
        data(server).setDirty();
        return gained;
    }

    /** Ends a guild outright (commands): its members go their own way, its treasury with it. */
    public static void dissolveNow(MinecraftServer server, Guild g) {
        for (ServerPlayer m : online(server, g)) inform(m, Component.translatable(KEY + "disbanded.admin", g.name()), true);
        dissolve(server, g);
    }

    /** Heals every hunter of a guild (commands). */
    public static void healHunters(MinecraftServer server, Guild g) {
        for (GuildHunter h : g.hunters()) h.heal();
        data(server).setDirty();
        syncGuild(server, g);
    }

    /** Adds a hunter to a guild's roster outright (commands, tests): no price, no cap. */
    public static void enlist(MinecraftServer server, Guild g, GuildHunter h) {
        g.addHunter(h);
        data(server).setDirty();
        syncGuild(server, g);
    }

    // ------------------------------------------------------------------ raids

    /**
     * The master or an officer requests a raid gate of {@code rank} from the System: it opens near them, paid from the
     * treasury ({@link GuildPerks#permitPrice}); the guild's level must allow the rank.
     */
    public static Outcome permit(ServerPlayer player, PlayerRank rank) {
        MinecraftServer server = player.server;
        Guild g = of(player);
        if (g == null) return Outcome.no("none");
        if (!g.role(player.getUUID()).manages()) return Outcome.no("not_officer");
        if (rank.tier() > GuildPerks.permitRank(g.level()).tier()) return Outcome.no("permit.level", rank.displayName(), GuildPerks.permitLevel(rank));
        long price = GuildPerks.permitPrice(g.level(), rank);
        if (g.treasury() < price) return Outcome.no("permit.treasury", price, g.treasury());
        String refusal = com.beyondsolo.raid.Raids.permitRefusal(player, g);
        if (refusal != null) return Outcome.no(refusal);
        if (!com.beyondsolo.raid.Raids.openPermitGate(player, g, rank)) return Outcome.no("permit.room");
        spend(server, g, price);
        g.log(day(server), "permit", player.getGameProfile().getName(), rank.name());
        data(server).setDirty();
        syncGuild(server, g);
        return Outcome.ok("permit.opened", rank.displayName(), price);
    }

    /** A raid the guild was in was cleared: counted, and its guild XP shared among those who were there. */
    public static void raidCleared(MinecraftServer server, Guild g, PlayerRank rank, List<UUID> members) {
        g.countRaid();
        g.log(day(server), "raid", rank.name());
        data(server).setDirty();
        long each = GuildPerks.raidXp(rank);
        if (members.isEmpty()) {
            syncGuild(server, g);
            return;
        }
        long total = 0;
        for (UUID m : members) {
            g.contribute(m, each);
            total += each;
        }
        addXp(server, g, total, null);
    }

    // ------------------------------------------------------------------ guild XP and levels

    /** Guild XP for the guild (and to {@code by}'s contribution); levels it up as far as it goes. */
    public static void addXp(MinecraftServer server, Guild g, long amount, @Nullable UUID by) {
        if (amount <= 0) return;
        if (by != null) g.contribute(by, amount);
        int before = g.level();
        long xp = g.xp() + amount;
        int level = g.level();
        while (level < GuildConfig.MAX_LEVEL && xp >= GuildPerks.xpToNext(level)) {
            xp -= GuildPerks.xpToNext(level);
            level++;
        }
        if (level >= GuildConfig.MAX_LEVEL) xp = Math.min(xp, 0);
        g.setLevel(level);
        g.setXp(xp);
        if (level > before) {
            g.log(day(server), "level", Integer.toString(level));
            for (ServerPlayer m : online(server, g)) {
                if (SystemAPI.hasSystem(m)) {
                    Notifications.send(m, SystemNotification.of(SystemNotification.Kind.LEVEL_UP,
                            Component.translatable("notify.beyondsolo.guild.level.title", level),
                            Component.translatable("notify.beyondsolo.guild.level.body", g.name(), level)));
                } else {
                    m.sendSystemMessage(tag().append(Component.translatable(KEY + "level", g.name(), level).withStyle(ChatFormatting.GOLD)));
                }
                PacketDistributor.sendToPlayer(m, new GuildEventPayload(GuildEventPayload.LEVEL_UP, level, Component.literal(g.name())));
            }
        }
        data(server).setDirty();
        syncGuild(server, g);
    }

    /** A member cleared a gate: the guild counts it and earns XP by its rank. */
    public static void gateCleared(ServerPlayer player, GateInstance gate) {
        Guild g = of(player);
        if (g == null || gate.isTrial()) return;
        g.countGate();
        addXp(player.server, g, GuildPerks.gateXp(gate.rank()), player.getUUID());
    }

    /** Sets a guild's level outright (commands, tests). */
    public static void setLevel(MinecraftServer server, Guild g, int level) {
        g.setLevel(Math.max(1, Math.min(GuildConfig.MAX_LEVEL, level)));
        g.setXp(0);
        data(server).setDirty();
        syncGuild(server, g);
    }

    /** Sets a guild's treasury outright (commands, tests). */
    public static void setTreasury(MinecraftServer server, Guild g, long gold) {
        g.setTreasury(gold);
        data(server).setDirty();
        syncGuild(server, g);
    }

    /** The guild's XP perk on System XP its members earn. */
    @SubscribeEvent
    static void onXp(SystemEvent.XpGain event) {
        Guild g = of(event.getPlayer());
        if (g == null) return;
        double bonus = GuildPerks.xpBonus(g.level());
        if (bonus > 0) event.setAmount(Math.round(event.getAmount() * (1.0 + bonus)));
    }

    // ------------------------------------------------------------------ chat

    /** A line to every member online: "[TAG] name: message". */
    public static Outcome chat(ServerPlayer player, String message) {
        Guild g = of(player);
        if (g == null) return Outcome.no("none");
        String text = message.trim();
        if (text.isEmpty()) return Outcome.no("chat.empty");
        Component line = Component.translatable(KEY + "chat", styled(g),
                Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.AQUA),
                Component.literal(text).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY);
        for (ServerPlayer m : online(player.server, g)) m.sendSystemMessage(line);
        return new Outcome(true, Component.empty());
    }

    // ------------------------------------------------------------------ the page's buttons

    /** A button on the GUILD page: does it and tells the client how it went. */
    public static void handle(ServerPlayer player, GuildActionPayload p) {
        MinecraftServer server = player.server;
        CompoundTag a = p.args();
        UUID target = a.hasUUID("target") ? a.getUUID("target") : new UUID(0, 0);
        Outcome out = switch (p.action()) {
            case GuildActionPayload.FOUND -> found(player, a.getString("name"), a.getString("tag"), a.getInt("crest"), a.getInt("color"));
            case GuildActionPayload.INVITE -> {
                ServerPlayer to = OnlinePlayers.get(server, target);
                yield to == null ? Outcome.no("invite.offline") : invite(player, to);
            }
            case GuildActionPayload.ACCEPT -> accept(player, a.hasUUID("target") ? target : null);
            case GuildActionPayload.DECLINE -> decline(player, a.hasUUID("target") ? target : null);
            case GuildActionPayload.LEAVE -> leave(player);
            case GuildActionPayload.KICK -> kick(player, target);
            case GuildActionPayload.PROMOTE -> promote(player, target);
            case GuildActionPayload.DEMOTE -> demote(player, target);
            case GuildActionPayload.TRANSFER -> transfer(player, target);
            case GuildActionPayload.DISBAND -> disband(player);
            case GuildActionPayload.DEPOSIT -> deposit(player, a.getLong("amount"));
            case GuildActionPayload.WITHDRAW -> withdraw(player, a.getLong("amount"));
            case GuildActionPayload.HIRE -> hire(player, target);
            case GuildActionPayload.DISMISS -> dismiss(player, target);
            case GuildActionPayload.PERMIT -> {
                PlayerRank rank = PlayerRank.byName(a.getString("rank"));
                yield rank == null ? Outcome.no("permit.level", "?", 0) : permit(player, rank);
            }
            default -> null;
        };
        if (out == null) return;
        PacketDistributor.sendToPlayer(player, new GuildEventPayload(GuildEventPayload.OUTCOME, out.ok() ? 1 : 0, out.message()));
    }

    // ------------------------------------------------------------------ helpers

    @Nullable
    private static Invite take(ServerPlayer player, @Nullable UUID guild) {
        LinkedHashMap<UUID, Invite> map = INVITES.get(player.getUUID());
        if (map == null) return null;
        long now = player.server.getTickCount();
        map.values().removeIf(i -> i.expires <= now);
        Invite inv = null;
        if (guild != null && !guild.equals(new UUID(0, 0))) {
            inv = map.remove(guild);
        } else {
            for (Invite i : map.values()) inv = i;
            if (inv != null) map.remove(inv.guild);
        }
        if (map.isEmpty()) INVITES.remove(player.getUUID());
        return inv;
    }

    /** The guild of an open invitation by its name or tag (any case), or null. */
    @Nullable
    public static UUID invitingGuild(ServerPlayer player, String nameOrTag) {
        Map<UUID, Invite> map = INVITES.get(player.getUUID());
        if (map == null) return null;
        GuildData data = data(player.server);
        for (Invite i : map.values()) {
            Guild g = data.get(i.guild);
            if (g != null && (g.tag().equalsIgnoreCase(nameOrTag) || g.name().equalsIgnoreCase(nameOrTag))) return g.id();
        }
        return null;
    }

    /** The tags of the guilds that invited a hunter (open invitations). */
    public static List<String> invitingTags(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        GuildData data = data(player.server);
        for (UUID id : invitesFor(player)) {
            Guild g = data.get(id);
            if (g != null) out.add(g.tag());
        }
        return out;
    }

    /** Who takes over from a leaving master: the first officer, else the member who has been in longest. */
    private static UUID successor(Guild g, UUID leaving) {
        for (UUID m : g.members()) if (!m.equals(leaving) && g.role(m) == GuildRole.OFFICER) return m;
        for (UUID m : g.members()) if (!m.equals(leaving)) return m;
        return leaving;
    }

    private static void dissolve(MinecraftServer server, Guild g) {
        List<ServerPlayer> left = online(server, g);
        com.beyondsolo.raid.Raids.guildGone(server, g.id());
        data(server).disband(g);
        for (ServerPlayer m : left) {
            refreshName(m);
            sync(m);
        }
    }

    private static void announceMaster(MinecraftServer server, Guild g) {
        for (ServerPlayer m : online(server, g)) {
            inform(m, m.getUUID().equals(g.master()) ? Component.translatable(KEY + "master.you", g.name())
                    : Component.translatable(KEY + "master.other", g.name(g.master())), false);
        }
    }

    /** "[Guild] " in front of a chat line. */
    public static MutableComponent tag() {
        return Component.translatable(KEY + "tag").withStyle(ChatFormatting.GOLD).append(" ");
    }

    /** The guild's name with its tag, in its colour. */
    public static MutableComponent styled(Guild g) {
        return Component.literal(g.name() + " [" + g.tag() + "]").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(GuildColors.color(g.color()))));
    }

    static Component name(ServerPlayer p) {
        return Component.literal(p.getGameProfile().getName()).withStyle(ChatFormatting.AQUA);
    }

    static MutableComponent button(String key, ChatFormatting color, String command) {
        return Component.literal("[").append(Component.translatable(KEY + "button." + key)).append("]")
                .withStyle(Style.EMPTY.withColor(color).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command).withStyle(ChatFormatting.GRAY))));
    }

    /** News about the guild: a System pop-up for those who have it, a chat line for those who don't. */
    public static void inform(ServerPlayer p, Component text, boolean alert) {
        if (SystemAPI.hasSystem(p)) {
            Notifications.send(p, SystemNotification.of(alert ? SystemNotification.Kind.ALERT : SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.guild.title"), text));
        } else {
            p.sendSystemMessage(tag().append(text.copy().withStyle(ChatFormatting.GRAY)));
        }
    }

    // ------------------------------------------------------------------ tags over heads

    /** The tag in front of a member's name ("[TAG] "), in the guild's colour; empty outside a guild. */
    public static Component prefix(Guild g) {
        return Component.literal("[" + g.tag() + "] ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(GuildColors.color(g.color()))));
    }

    public static void refreshName(ServerPlayer p) {
        p.refreshDisplayName();
        p.refreshTabListName();
    }

    @SubscribeEvent
    static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Guild g = of(p);
        if (g != null) event.setDisplayname(prefix(g).copy().append(event.getDisplayname()));
    }

    @SubscribeEvent
    static void onTabName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Guild g = of(p);
        if (g == null) return;
        Component base = event.getDisplayName() != null ? event.getDisplayName() : Component.literal(p.getGameProfile().getName());
        event.setDisplayName(prefix(g).copy().append(base));
    }

    // ------------------------------------------------------------------ keeping clients up to date

    /** Sends a hunter their guild (or none) and their invitations. */
    public static void sync(ServerPlayer player) {
        if (player.connection == null) return;
        PacketDistributor.sendToPlayer(player, new GuildSyncPayload(syncTag(player)));
    }

    public static void syncGuild(MinecraftServer server, Guild g) {
        for (ServerPlayer m : online(server, g)) sync(m);
    }

    /** Everything the GUILD page shows, for this hunter. */
    public static CompoundTag syncTag(ServerPlayer player) {
        MinecraftServer server = player.server;
        CompoundTag t = new CompoundTag();
        t.putLong("foundCost", GuildConfig.FOUNDING_COST.get());
        t.putLong("day", day(server));
        t.putInt("baseMembers", GuildPerks.memberCap(1));
        // invitations
        ListTag invites = new ListTag();
        Map<UUID, Invite> map = INVITES.get(player.getUUID());
        if (map != null) {
            long now = server.getTickCount();
            for (Invite i : map.values()) {
                Guild g = data(server).get(i.guild);
                if (i.expires <= now || g == null) continue;
                CompoundTag inv = new CompoundTag();
                inv.putUUID("id", g.id());
                inv.putString("name", g.name());
                inv.putString("tag", g.tag());
                inv.putInt("crest", g.crest());
                inv.putInt("color", g.color());
                inv.putInt("level", g.level());
                inv.putInt("size", g.size());
                inv.putString("from", i.from);
                inv.putInt("ticks", (int) (i.expires - now));
                invites.add(inv);
            }
        }
        t.put("invites", invites);
        Guild g = of(server, player.getUUID());
        t.putBoolean("in", g != null);
        if (g == null) return t;

        t.putUUID("id", g.id());
        t.putString("name", g.name());
        t.putString("tag", g.tag());
        t.putInt("crest", g.crest());
        t.putInt("color", g.color());
        t.putInt("level", g.level());
        t.putLong("xp", g.xp());
        t.putLong("xpNext", GuildPerks.xpToNext(g.level()));
        t.putLong("treasury", g.treasury());
        t.putLong("founded", g.foundedDay());
        t.putInt("raids", g.raidsCleared());
        t.putInt("gates", g.gatesCleared());
        t.putUUID("master", g.master());
        t.putString("role", g.role(player.getUUID()).id());
        t.putInt("memberCap", GuildPerks.memberCap(g.level()));
        t.putInt("rosterCap", GuildPerks.rosterCap(g.level()));
        t.putDouble("goldBonus", GuildPerks.goldBonus(g.level()));
        t.putDouble("xpBonus", GuildPerks.xpBonus(g.level()));
        t.putString("permitRank", GuildPerks.permitRank(g.level()).name());
        t.putDouble("discount", GuildPerks.permitDiscount(g.level()));
        ListTag permits = new ListTag();
        for (PlayerRank r : PlayerRank.values()) {
            CompoundTag pr = new CompoundTag();
            pr.putString("rank", r.name());
            pr.putLong("price", GuildPerks.permitPrice(g.level(), r));
            pr.putInt("needs", GuildPerks.permitLevel(r));
            permits.add(pr);
        }
        t.put("permits", permits);

        ListTag members = new ListTag();
        for (UUID id : g.members()) {
            Guild.Member m = g.member(id);
            if (m == null) continue;
            CompoundTag mt = new CompoundTag();
            mt.putUUID("id", id);
            mt.putString("name", m.name());
            mt.putString("role", m.role().id());
            mt.putLong("given", m.contribution());
            mt.putLong("joined", m.joinedDay());
            ServerPlayer on = OnlinePlayers.get(server, id);
            mt.putBoolean("online", on != null);
            if (on != null) {
                SystemData d = SystemAPI.get(on);
                mt.putInt("rank", d.isAwakened() && d.rank() != null ? d.rank().ordinal() : -1);
                mt.putInt("cls", d.isAwakened() && d.playerClass() != null ? d.playerClass().ordinal() : -1);
                mt.putInt("lvl", d.hasSystem() ? d.level() : 0);
            } else {
                mt.putInt("rank", -1);
                mt.putInt("cls", -1);
                mt.putInt("lvl", 0);
            }
            members.add(mt);
        }
        t.put("members", members);

        long now = server.overworld().getGameTime();
        ListTag hunters = new ListTag();
        for (GuildHunter h : g.hunters()) {
            CompoundTag ht = h.save();
            ht.putLong("injured", Math.max(0, h.injuredUntil() - now));
            ht.putBoolean("busy", com.beyondsolo.raid.Raids.hunterBusy(server, h.id()));
            hunters.add(ht);
        }
        t.put("hunters", hunters);
        ListTag pool = new ListTag();
        for (GuildHunter h : recruits(server, g)) pool.add(h.save());
        t.put("recruits", pool);

        ListTag log = new ListTag();
        for (Guild.LogLine l : g.log()) {
            CompoundTag lt = new CompoundTag();
            lt.putLong("day", l.day());
            lt.putString("key", l.key());
            ListTag args = new ListTag();
            for (String s : l.args()) args.add(StringTag.valueOf(s));
            lt.put("args", args);
            log.add(lt);
        }
        t.put("log", log);
        CompoundTag raid = com.beyondsolo.raid.Raids.guildRaidTag(server, g);
        if (raid != null) t.put("raid", raid);
        return t;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        long now = server.getTickCount();
        for (Map.Entry<UUID, LinkedHashMap<UUID, Invite>> e : INVITES.entrySet()) {
            if (e.getValue().values().removeIf(i -> i.expires <= now)) DIRTY.add(e.getKey());
        }
        INVITES.values().removeIf(Map::isEmpty);
        if (!DIRTY.isEmpty()) {
            for (UUID id : List.copyOf(DIRTY)) {
                ServerPlayer p = OnlinePlayers.get(server, id);
                if (p != null) sync(p);
            }
            DIRTY.clear();
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.server;
        GuildData data = data(server);
        Guild g = data.of(player.getUUID());
        if (g != null) {
            data.rename(g, player.getUUID(), player.getGameProfile().getName());
            for (ServerPlayer m : online(server, g)) {
                if (m != player) inform(m, Component.translatable(KEY + "online", name(player)), false);
            }
            refreshName(player);
            syncGuild(server, g);
        } else {
            sync(player);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.server;
        if (of(server, player.getUUID()) == null) return;
        ServerScheduler.schedule(1, () -> {
            Guild now = of(server, player.getUUID());
            if (now != null) syncGuild(server, now);
        });
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        INVITES.clear();
        DIRTY.clear();
    }

    /** Forgets every open invitation (tests). */
    public static void clearInvites() {
        INVITES.clear();
    }

    /**
     * Takes a hunter out of their guild for {@code /solo wipe}: as if they left (a master hands the guild on, the last
     * member ends it).
     */
    public static void wipe(ServerPlayer player) {
        INVITES.remove(player.getUUID());
        if (of(player) != null) leave(player);
    }

    private Guilds() {}
}
