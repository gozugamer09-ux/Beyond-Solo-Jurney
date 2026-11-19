package com.beyondsolo.party;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.PartyActionPayload;
import com.beyondsolo.network.PartyEventPayload;
import com.beyondsolo.network.PartySyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Parties: a few hunters who go together. One leads (invites, removes, hands the lead on, disbands); anyone can leave.
 * A party is formed when someone accepts an invitation (a party of one never exists) and lasts until it is disbanded or
 * only one member is left, through logouts and restarts ({@link PartyData}).
 *
 * <p>What a party changes: its members share kills ({@link #sharers}: the quest progress in full, a part of the XP and
 * gold, see {@link PartyConfig#KILL_SHARE}), can't hurt each other or each other's shadows and pets unless
 * {@code party.friendlyFire} is on, see each other's health and whereabouts (the HUD, the PARTY page, markers in the
 * world), can travel to each other ({@link Regroup}), keep a keyed gate or a red gate from sealing while one of them is
 * still on the way ({@link #followers}), and go through the awakening trial together when the System calls them in the
 * same cave ({@code awakening.AwakeningService}).</p>
 *
 * <p>Invitations are kept in memory only and run out after {@code party.inviteSeconds}. Every change goes through here;
 * the client hears of it through {@link PartySyncPayload}, sent when something changes and every
 * {@link #SYNC_TICKS} ticks to everyone in a party with someone else online.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Parties {
    /** XP a party member gets for a kill they shared. */
    public static final ResourceLocation XP_SOURCE = BeyondSolo.id("party_kill");
    /** Ticks between two syncs of a party's health and whereabouts. */
    public static final int SYNC_TICKS = 10;

    /** What an action came to: done, or why not. Shown to whoever asked (a chat line, or the PARTY page's status). */
    public record Outcome(boolean ok, Component message) {
        static Outcome ok(String key, Object... args) {
            return new Outcome(true, Component.translatable("party.beyondsolo." + key, args));
        }

        static Outcome no(String key, Object... args) {
            return new Outcome(false, Component.translatable("party.beyondsolo." + key, args));
        }
    }

    private record Invite(UUID from, String name, long expires) {}

    /** Open invitations by invited hunter, oldest first. */
    private static final Map<UUID, LinkedHashMap<UUID, Invite>> INVITES = new HashMap<>();
    /** Hunters whose PARTY page has an invitation to forget (it ran out): synced once more. */
    private static final Set<UUID> DIRTY = new HashSet<>();

    // ------------------------------------------------------------------ reads

    public static PartyData data(MinecraftServer server) {
        return PartyData.get(server);
    }

    /** The party a hunter is in, or null. */
    @Nullable
    public static Party of(MinecraftServer server, UUID player) {
        return data(server).of(player);
    }

    @Nullable
    public static Party of(Player player) {
        MinecraftServer server = player.getServer();
        return server == null ? null : of(server, player.getUUID());
    }

    /** True if two different hunters are in the same party. */
    public static boolean together(MinecraftServer server, @Nullable UUID a, @Nullable UUID b) {
        if (a == null || b == null || a.equals(b)) return false;
        Party p = of(server, a);
        return p != null && p.has(b);
    }

    public static boolean together(Player a, Player b) {
        MinecraftServer server = a.getServer();
        return server != null && together(server, a.getUUID(), b.getUUID());
    }

    /** The party's members who are on the server. */
    public static List<ServerPlayer> online(MinecraftServer server, Party party) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID m : party.members()) {
            ServerPlayer p = OnlinePlayers.get(server, m);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** The other members of a hunter's party who are on the server (none outside a party). */
    public static List<ServerPlayer> mates(ServerPlayer player) {
        Party party = of(player);
        if (party == null) return List.of();
        List<ServerPlayer> out = online(player.server, party);
        out.remove(player);
        return out;
    }

    /** Who invited a hunter, oldest first (open invitations only). */
    public static List<UUID> invitesFor(ServerPlayer player) {
        Map<UUID, Invite> map = INVITES.get(player.getUUID());
        if (map == null) return List.of();
        long now = player.server.getTickCount();
        List<UUID> out = new ArrayList<>();
        for (Invite i : map.values()) if (i.expires > now) out.add(i.from);
        return out;
    }

    /** The names of those who invited a hunter (open invitations), oldest first. */
    public static List<String> inviterNames(ServerPlayer player) {
        Map<UUID, Invite> map = INVITES.get(player.getUUID());
        if (map == null) return List.of();
        long now = player.server.getTickCount();
        List<String> out = new ArrayList<>();
        for (Invite i : map.values()) if (i.expires > now) out.add(i.name);
        return out;
    }

    /** Who sent a hunter an open invitation under this name (any case), or null. */
    @Nullable
    public static UUID inviterNamed(ServerPlayer player, String name) {
        Map<UUID, Invite> map = INVITES.get(player.getUUID());
        if (map == null) return null;
        for (Invite i : map.values()) if (i.name.equalsIgnoreCase(name)) return i.from;
        return null;
    }

    // ------------------------------------------------------------------ actions

    /** {@code from} invites {@code to} into their party (made when the first invitation is accepted). */
    public static Outcome invite(ServerPlayer from, ServerPlayer to) {
        MinecraftServer server = from.server;
        Party party = of(from);
        if (to == from) return Outcome.no("invite.self");
        if (party != null && !party.isLeader(from.getUUID())) return Outcome.no("not_leader", Component.literal(party.name(party.leader())));
        if (party != null && party.has(to.getUUID())) return Outcome.no("invite.member", to.getDisplayName());
        if (party != null && party.size() >= PartyConfig.MAX_SIZE.get()) return Outcome.no("full", PartyConfig.MAX_SIZE.get());
        int size = party == null ? 1 : party.size();
        long expires = server.getTickCount() + PartyConfig.INVITE_SECONDS.get() * 20L;
        LinkedHashMap<UUID, Invite> map = INVITES.computeIfAbsent(to.getUUID(), k -> new LinkedHashMap<>());
        boolean again = map.remove(from.getUUID()) != null;
        map.put(from.getUUID(), new Invite(from.getUUID(), from.getGameProfile().getName(), expires));

        // the invitation: a line in chat they can click (it works with or without the System), and a pop-up
        Component name = Component.literal(from.getGameProfile().getName()).withStyle(ChatFormatting.AQUA);
        MutableComponent line = Component.translatable("party.beyondsolo.invited", name, size, PartyConfig.MAX_SIZE.get())
                .withStyle(ChatFormatting.GRAY);
        Party theirs = of(to);
        if (theirs != null) line.append(" ").append(Component.translatable("party.beyondsolo.invited.leaves").withStyle(ChatFormatting.DARK_GRAY));
        line.append(" ").append(button("accept", ChatFormatting.GREEN, "/party accept " + from.getGameProfile().getName()))
                .append(" ").append(button("decline", ChatFormatting.RED, "/party decline " + from.getGameProfile().getName()));
        to.sendSystemMessage(tag().append(line));
        Notifications.send(to, SystemNotification.of(SystemNotification.Kind.QUEST,
                Component.translatable("notify.beyondsolo.party.invite.title"),
                Component.translatable("notify.beyondsolo.party.invite.body", from.getGameProfile().getName())));
        sync(to);
        return Outcome.ok(again ? "invite.again" : "invite.sent", to.getDisplayName());
    }

    /** Accepts the invitation from {@code from} (null: the newest one). */
    public static Outcome accept(ServerPlayer player, @Nullable UUID from) {
        MinecraftServer server = player.server;
        Invite inv = take(player, from);
        if (inv == null) return Outcome.no(from == null ? "accept.none" : "accept.expired");
        PartyData data = data(server);
        Party party = data.of(inv.from);
        if (party != null && party.has(player.getUUID())) {
            sync(player);
            return Outcome.no("accept.already");
        }
        if (party != null && party.size() >= PartyConfig.MAX_SIZE.get()) {
            sync(player);
            return Outcome.no("accept.full", PartyConfig.MAX_SIZE.get());
        }
        Party old = data.of(player.getUUID());
        if (old != null) leave(player);
        if (party == null) {
            ServerPlayer leader = OnlinePlayers.get(server, inv.from);
            party = data.create(inv.from, leader != null ? leader.getGameProfile().getName() : inv.name);
        }
        data.add(party, player.getUUID(), player.getGameProfile().getName());
        // one party: whatever else they were asked into is moot now
        INVITES.remove(player.getUUID());
        for (ServerPlayer m : online(server, party)) {
            if (m != player) inform(m, Component.translatable("party.beyondsolo.joined.other", name(player)), false);
        }
        syncParty(server, party);
        return Outcome.ok("joined", Component.literal(party.name(party.leader())));
    }

    /** Turns down the invitation from {@code from} (null: the newest one). */
    public static Outcome decline(ServerPlayer player, @Nullable UUID from) {
        Invite inv = take(player, from);
        if (inv == null) return Outcome.no(from == null ? "accept.none" : "accept.expired");
        ServerPlayer inviter = OnlinePlayers.get(player.server, inv.from);
        if (inviter != null) inform(inviter, Component.translatable("party.beyondsolo.declined.other", name(player)), false);
        sync(player);
        return Outcome.ok("declined", Component.literal(inv.name));
    }

    /** Leaves the party. A party left with one member is no more; a leader who leaves hands the lead on. */
    public static Outcome leave(ServerPlayer player) {
        MinecraftServer server = player.server;
        PartyData data = data(server);
        Party party = data.of(player.getUUID());
        if (party == null) return Outcome.no("none");
        boolean led = party.isLeader(player.getUUID());
        data.remove(party, player.getUUID());
        Regroup.cancel(player, false);
        sync(player);
        if (party.size() <= 1) {
            for (ServerPlayer m : online(server, party)) inform(m, Component.translatable("party.beyondsolo.ended.left", name(player)), true);
            dissolve(server, party);
            return Outcome.ok("left");
        }
        if (led) handOn(server, party);
        for (ServerPlayer m : online(server, party)) inform(m, Component.translatable("party.beyondsolo.left.other", name(player)), false);
        if (led) announceLeader(server, party);
        syncParty(server, party);
        return Outcome.ok("left");
    }

    /** The leader removes a member (online or not). */
    public static Outcome kick(ServerPlayer leader, UUID member) {
        MinecraftServer server = leader.server;
        PartyData data = data(server);
        Party party = data.of(leader.getUUID());
        if (party == null) return Outcome.no("none");
        if (!party.isLeader(leader.getUUID())) return Outcome.no("not_leader", Component.literal(party.name(party.leader())));
        if (member.equals(leader.getUUID())) return Outcome.no("kick.self");
        if (!party.has(member)) return Outcome.no("not_member");
        String name = party.name(member);
        data.remove(party, member);
        ServerPlayer gone = OnlinePlayers.get(server, member);
        if (gone != null) {
            Regroup.cancel(gone, false);
            inform(gone, Component.translatable("party.beyondsolo.kicked.you", Component.literal(party.name(party.leader()))), true);
            sync(gone);
        }
        if (party.size() <= 1) {
            dissolve(server, party);
            return Outcome.ok("kicked.ended", Component.literal(name));
        }
        for (ServerPlayer m : online(server, party)) {
            if (m != leader) inform(m, Component.translatable("party.beyondsolo.kicked.other", Component.literal(name)), false);
        }
        syncParty(server, party);
        return Outcome.ok("kicked", Component.literal(name));
    }

    /** The leader hands the lead to another member. */
    public static Outcome promote(ServerPlayer leader, UUID member) {
        MinecraftServer server = leader.server;
        PartyData data = data(server);
        Party party = data.of(leader.getUUID());
        if (party == null) return Outcome.no("none");
        if (!party.isLeader(leader.getUUID())) return Outcome.no("not_leader", Component.literal(party.name(party.leader())));
        if (!party.has(member)) return Outcome.no("not_member");
        if (member.equals(leader.getUUID())) return Outcome.no("promote.self");
        data.setLeader(party, member);
        announceLeader(server, party);
        syncParty(server, party);
        return Outcome.ok("promoted", Component.literal(party.name(member)));
    }

    /** The leader breaks the party up. */
    public static Outcome disband(ServerPlayer leader) {
        MinecraftServer server = leader.server;
        Party party = of(server, leader.getUUID());
        if (party == null) return Outcome.no("none");
        if (!party.isLeader(leader.getUUID())) return Outcome.no("not_leader", Component.literal(party.name(party.leader())));
        for (ServerPlayer m : online(server, party)) {
            if (m != leader) inform(m, Component.translatable("party.beyondsolo.disbanded.other", name(leader)), true);
        }
        dissolve(server, party);
        return Outcome.ok("disbanded");
    }

    /** A button on the PARTY page: does it and tells the client how it went. */
    public static void handle(ServerPlayer player, PartyActionPayload p) {
        MinecraftServer server = player.server;
        UUID target = p.target();
        Outcome out = switch (p.action()) {
            case PartyActionPayload.INVITE -> {
                ServerPlayer to = OnlinePlayers.get(server, target);
                yield to == null ? Outcome.no("invite.offline") : invite(player, to);
            }
            case PartyActionPayload.ACCEPT -> accept(player, target);
            case PartyActionPayload.DECLINE -> decline(player, target);
            case PartyActionPayload.LEAVE -> leave(player);
            case PartyActionPayload.KICK -> kick(player, target);
            case PartyActionPayload.PROMOTE -> promote(player, target);
            case PartyActionPayload.DISBAND -> disband(player);
            case PartyActionPayload.REGROUP -> Regroup.start(player, target);
            default -> null;
        };
        if (out == null) return;
        PacketDistributor.sendToPlayer(player, new PartyEventPayload(PartyEventPayload.OUTCOME, out.ok() ? 1 : 0, out.message()));
    }

    /** Takes an open invitation off the list (null {@code from}: the newest). */
    @Nullable
    private static Invite take(ServerPlayer player, @Nullable UUID from) {
        LinkedHashMap<UUID, Invite> map = INVITES.get(player.getUUID());
        if (map == null) return null;
        long now = player.server.getTickCount();
        map.values().removeIf(i -> i.expires <= now);
        Invite inv = null;
        if (from != null && !from.equals(new UUID(0, 0))) {
            inv = map.remove(from);
        } else {
            for (Invite i : map.values()) inv = i;
            if (inv != null) map.remove(inv.from);
        }
        if (map.isEmpty()) INVITES.remove(player.getUUID());
        return inv;
    }

    private static void dissolve(MinecraftServer server, Party party) {
        List<ServerPlayer> left = online(server, party);
        data(server).disband(party);
        for (ServerPlayer m : left) {
            Regroup.cancel(m, false);
            sync(m);
        }
    }

    /** The lead goes to the first member online (else the first who joined). */
    private static void handOn(MinecraftServer server, Party party) {
        UUID next = null;
        for (UUID m : party.members()) {
            if (OnlinePlayers.get(server, m) != null) {
                next = m;
                break;
            }
        }
        if (next == null && party.size() > 0) next = party.members().get(0);
        if (next != null) data(server).setLeader(party, next);
    }

    private static void announceLeader(MinecraftServer server, Party party) {
        Component who = Component.literal(party.name(party.leader()));
        for (ServerPlayer m : online(server, party)) {
            inform(m, m.getUUID().equals(party.leader()) ? Component.translatable("party.beyondsolo.leader.you")
                    : Component.translatable("party.beyondsolo.leader.other", who), false);
        }
    }

    // ------------------------------------------------------------------ telling people

    /** "[Party] " in front of a chat line. */
    static MutableComponent tag() {
        return Component.translatable("party.beyondsolo.tag").withStyle(ChatFormatting.DARK_AQUA).append(" ");
    }

    static Component name(ServerPlayer p) {
        return Component.literal(p.getGameProfile().getName()).withStyle(ChatFormatting.AQUA);
    }

    /** A word in brackets that runs a command when clicked. */
    static MutableComponent button(String key, ChatFormatting color, String command) {
        return Component.literal("[").append(Component.translatable("party.beyondsolo.button." + key)).append("]")
                .withStyle(Style.EMPTY.withColor(color).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command).withStyle(ChatFormatting.GRAY))));
    }

    /** News about the party: a System pop-up for those who have it, a chat line for those who don't. */
    public static void inform(ServerPlayer p, Component text, boolean alert) {
        if (SystemAPI.hasSystem(p)) {
            Notifications.send(p, SystemNotification.of(alert ? SystemNotification.Kind.ALERT : SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.party.title"), text));
        } else {
            p.sendSystemMessage(tag().append(text.copy().withStyle(ChatFormatting.GRAY)));
        }
    }

    // ------------------------------------------------------------------ sharing

    /**
     * The party members of {@code killer} who share a kill of {@code victim}: alive, and in the same gate's cave as the
     * creature, or within {@code party.shareRange} blocks of it anywhere else. Not the killer.
     */
    public static List<ServerPlayer> sharers(ServerPlayer killer, LivingEntity victim) {
        Party party = of(killer);
        if (party == null) return List.of();
        double range = PartyConfig.SHARE_RANGE.get();
        boolean cave = GateService.isDungeon(victim.level());
        int slot = cave ? GateData.slotAt(victim.blockPosition()) : 0;
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer m : online(killer.server, party)) {
            if (m == killer || !m.isAlive() || m.isSpectator() || m.level() != victim.level()) continue;
            boolean near = cave && slot >= 1 ? GateData.slotAt(m.blockPosition()) == slot : m.distanceToSqr(victim) <= range * range;
            if (near) out.add(m);
        }
        return out;
    }

    /** The part of a kill's XP or gold a sharer gets (rounded, at least 1 if anything is shared at all). */
    public static long share(long amount) {
        double part = PartyConfig.KILL_SHARE.get();
        if (part <= 0 || amount <= 0) return 0;
        return Math.max(1, Math.round(amount * part));
    }

    // ------------------------------------------------------------------ friendly fire

    /** The player behind an attack: the player, or the owner of the shadow, pet or arrow. */
    @Nullable
    public static UUID culprit(@Nullable Entity e) {
        for (int i = 0; e != null && i < 4; i++) {
            if (e instanceof Player p) return p.getUUID();
            if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) return o.getOwnerUUID();
            if (e instanceof Projectile pr) {
                e = pr.getOwner();
                continue;
            }
            return null;
        }
        return null;
    }

    /** Party members (and their shadows, pets and arrows) don't hurt each other unless friendly fire is on. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || PartyConfig.FRIENDLY_FIRE.get()) return;
        MinecraftServer server = victim.getServer();
        if (server == null) return;
        UUID by = culprit(event.getSource().getEntity());
        if (by == null) return;
        UUID hurt = victim instanceof Player p ? p.getUUID() : victim instanceof OwnableEntity o ? o.getOwnerUUID() : null;
        if (together(server, by, hurt)) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ gates and the party

    /**
     * Party members of the hunters {@code inside} who are still on their way to a gate standing at {@code pos}: on the
     * server, in that dimension, within {@link PartyConfig#FOLLOW_REACH} blocks of it, not inside it already. A keyed
     * gate's door or a red gate holds open for them a little longer ({@code party.followSeconds}).
     */
    public static List<ServerPlayer> followers(MinecraftServer server, ResourceKey<Level> dim, BlockPos pos, Collection<UUID> inside) {
        if (PartyConfig.followTicks() <= 0 || inside.isEmpty()) return List.of();
        Set<UUID> seen = new HashSet<>(inside);
        List<ServerPlayer> out = new ArrayList<>();
        double reach = PartyConfig.FOLLOW_REACH;
        for (UUID in : inside) {
            Party party = of(server, in);
            if (party == null) continue;
            for (ServerPlayer m : online(server, party)) {
                if (!seen.add(m.getUUID()) || !m.isAlive() || m.isSpectator() || !m.level().dimension().equals(dim)) continue;
                if (m.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= reach * reach) out.add(m);
            }
        }
        return out;
    }

    /** Gates whose hunters were told they hold for the party (once a hold). */
    private static final Set<UUID> HELD = new HashSet<>();

    /**
     * True while a gate about to seal ({@code overdue} ticks past its time) holds for party members of the hunters
     * {@code inside} who are still on the way ({@link #followers}), at most {@code party.followSeconds}. The first time,
     * those inside hear who they are waiting for.
     */
    public static boolean holdFor(MinecraftServer server, UUID gate, ResourceKey<Level> dim, BlockPos pos, Collection<UUID> inside, long overdue) {
        List<ServerPlayer> coming = overdue < PartyConfig.followTicks() ? followers(server, dim, pos, inside) : List.of();
        if (coming.isEmpty()) {
            HELD.remove(gate);
            return false;
        }
        if (HELD.add(gate)) {
            Component who = name(coming.get(0));
            for (UUID id : inside) {
                ServerPlayer p = OnlinePlayers.get(server, id);
                if (p != null) p.displayClientMessage(Component.translatable("party.beyondsolo.hold", who), true);
            }
            for (ServerPlayer m : coming) m.displayClientMessage(Component.translatable("party.beyondsolo.hold.you"), true);
        }
        return true;
    }

    /**
     * A party member went into a gate's cave: those of the party out in the world near it hear of it (the HUD shows
     * where everyone is; this is for the ones right there).
     */
    public static void wentIn(ServerPlayer player, GateInstance g) {
        if (g.isTrial()) return;
        for (ServerPlayer m : mates(player)) {
            if (GateService.isDungeon(m.level())) continue;
            if (!m.level().dimension().equals(g.gateDimension()) || m.distanceToSqr(g.gatePos().getX(), g.gatePos().getY(), g.gatePos().getZ()) > 96 * 96) continue;
            m.displayClientMessage(Component.translatable("party.beyondsolo.went_in", name(player), GateService.gateLabel(g)), true);
        }
    }

    // ------------------------------------------------------------------ keeping clients up to date

    /** Sends a hunter their party (or none), their invitations and their regroup clock. */
    public static void sync(ServerPlayer player) {
        MinecraftServer server = player.server;
        Party party = of(server, player.getUUID());
        if (party == null) {
            PacketDistributor.sendToPlayer(player, PartySyncPayload.none(inviteList(player), PartyConfig.MAX_SIZE.get(), Regroup.readyIn(player)));
            return;
        }
        List<PartySyncPayload.Member> members = members(server, party);
        send(player, party, members);
    }

    /** Sends every online member their party as it stands. */
    public static void syncParty(MinecraftServer server, Party party) {
        List<PartySyncPayload.Member> members = members(server, party);
        for (ServerPlayer m : online(server, party)) send(m, party, members);
    }

    private static void send(ServerPlayer player, Party party, List<PartySyncPayload.Member> members) {
        if (player.connection == null) return;
        PacketDistributor.sendToPlayer(player, new PartySyncPayload(true, party.leader(), members, inviteList(player),
                PartyConfig.MAX_SIZE.get(), Regroup.readyIn(player)));
    }

    private static List<PartySyncPayload.Invite> inviteList(ServerPlayer player) {
        Map<UUID, Invite> map = INVITES.get(player.getUUID());
        if (map == null || map.isEmpty()) return List.of();
        MinecraftServer server = player.server;
        long now = server.getTickCount();
        List<PartySyncPayload.Invite> out = new ArrayList<>();
        for (Invite i : map.values()) {
            if (i.expires <= now) continue;
            Party theirs = of(server, i.from);
            out.add(new PartySyncPayload.Invite(i.from, i.name, theirs == null ? 1 : theirs.size(), (int) (i.expires - now)));
        }
        return out;
    }

    static List<PartySyncPayload.Member> members(MinecraftServer server, Party party) {
        List<PartySyncPayload.Member> out = new ArrayList<>();
        for (UUID id : party.members()) {
            ServerPlayer p = OnlinePlayers.get(server, id);
            if (p == null) {
                out.add(new PartySyncPayload.Member(id, party.name(id), false, false, -1, -1, -1, 0, 0, 0,
                        PartySyncPayload.OFFLINE, -1, Component.empty(), Level.OVERWORLD.location(), 0, 0, 0));
                continue;
            }
            SystemData d = SystemAPI.get(p);
            boolean awake = d.isAwakened();
            var sub = com.beyondsolo.job.JobAPI.subclass(p);
            Place where = place(p);
            out.add(new PartySyncPayload.Member(id, p.getGameProfile().getName(), true, d.hasSystem(),
                    awake && d.rank() != null ? d.rank().ordinal() : -1, awake && d.playerClass() != null ? d.playerClass().ordinal() : -1,
                    sub == null ? -1 : sub.ordinal(), d.level(), p.getHealth(), p.getMaxHealth(),
                    where.kind, where.rank, where.name, p.level().dimension().location(), p.getX(), p.getY(), p.getZ()));
        }
        return out;
    }

    /** Where a hunter is, for their party: out in a world, or in one of the places only some can go. */
    public record Place(int kind, int rank, Component name) {}

    public static Place place(ServerPlayer p) {
        if (com.beyondsolo.job.JobTrial.inTrial(p)) return new Place(PartySyncPayload.JOB_TRIAL, -1, Component.empty());
        if (com.beyondsolo.monarch.ThroneTrial.inTrial(p)) return new Place(PartySyncPayload.THRONE, -1, Component.empty());
        if (com.beyondsolo.penalty.PenaltyZone.inZone(p)) return new Place(PartySyncPayload.PENALTY, -1, Component.empty());
        // at home, or a guest in a party member's (their name)
        if (com.beyondsolo.home.Homes.inAnyHome(p)) return new Place(PartySyncPayload.HOME, -1, com.beyondsolo.home.Homes.hostOf(p));
        ResourceKey<Level> dim = p.level().dimension();
        if (dim.equals(com.beyondsolo.tower.TowerRun.DIMENSION)) return new Place(PartySyncPayload.CITADEL, -1, Component.empty());
        if (dim.equals(com.beyondsolo.redgate.RedWorld.DIMENSION)) {
            return new Place(PartySyncPayload.RED_GATE, -1, com.beyondsolo.redgate.RedGates.dungeonName());
        }
        if (GateService.isDungeon(p.level())) {
            GateInstance g = GateData.get(p.server).bySlotPos(p.blockPosition());
            if (g != null && g.isTrial()) return new Place(PartySyncPayload.TRIAL, -1, Component.empty());
            if (g != null) return new Place(PartySyncPayload.GATE, g.rank().ordinal(), g.dungeonName());
        }
        return new Place(PartySyncPayload.WORLD, -1, Component.empty());
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        Regroup.tick(server);
        if (tick % SYNC_TICKS != 0) return;
        // invitations that ran out: off the page
        long now = tick;
        for (Map.Entry<UUID, LinkedHashMap<UUID, Invite>> e : INVITES.entrySet()) {
            if (e.getValue().values().removeIf(i -> i.expires <= now)) DIRTY.add(e.getKey());
        }
        INVITES.values().removeIf(Map::isEmpty);
        Set<UUID> synced = new HashSet<>();
        for (Party party : List.copyOf(data(server).all())) {
            List<ServerPlayer> on = online(server, party);
            if (on.isEmpty()) continue;
            List<PartySyncPayload.Member> members = members(server, party);
            for (ServerPlayer m : on) {
                send(m, party, members);
                synced.add(m.getUUID());
            }
        }
        if (!DIRTY.isEmpty()) {
            for (UUID id : List.copyOf(DIRTY)) {
                ServerPlayer p = OnlinePlayers.get(server, id);
                if (p != null && !synced.contains(id)) sync(p);
            }
            DIRTY.clear();
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.server;
        PartyData data = data(server);
        Party party = data.of(player.getUUID());
        if (party != null) {
            data.rename(party, player.getUUID(), player.getGameProfile().getName());
            for (ServerPlayer m : online(server, party)) {
                if (m != player) inform(m, Component.translatable("party.beyondsolo.online", name(player)), false);
            }
            syncParty(server, party);
        } else {
            sync(player);
        }
        // the first time on a server with someone else on it: how to team up
        if (!player.getPersistentData().getBoolean(HINT_TAG) && server.getPlayerCount() > 1) {
            player.getPersistentData().putBoolean(HINT_TAG, true);
            ServerScheduler.schedule(200, () -> {
                if (!player.hasDisconnected()) player.sendSystemMessage(tag().append(Component.translatable("party.beyondsolo.hint").withStyle(ChatFormatting.GRAY)));
            });
        }
    }

    private static final String HINT_TAG = "beyondsolo_party_hint";

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.server;
        Regroup.cancel(player, false);
        Party party = of(server, player.getUUID());
        if (party == null) return;
        // a leader who goes leaves the lead with someone still here
        ServerScheduler.schedule(1, () -> {
            Party now = of(server, player.getUUID());
            if (now == null) return;
            if (now.isLeader(player.getUUID()) && OnlinePlayers.get(server, player.getUUID()) == null) {
                UUID before = now.leader();
                handOn(server, now);
                if (!before.equals(now.leader())) announceLeader(server, now);
            }
            for (ServerPlayer m : online(server, now)) inform(m, Component.translatable("party.beyondsolo.offline", Component.literal(now.name(player.getUUID()))), false);
            syncParty(server, now);
        });
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        INVITES.clear();
        DIRTY.clear();
        HELD.clear();
        Regroup.clear();
    }

    /** Forgets every open invitation (tests). */
    public static void clearInvites() {
        INVITES.clear();
    }

    private Parties() {}
}
