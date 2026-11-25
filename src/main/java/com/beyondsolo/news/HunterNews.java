package com.beyondsolo.news;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.association.Association;
import com.beyondsolo.chronicle.ranking.Rankings;
import com.beyondsolo.chronicle.ranking.Rival;
import com.beyondsolo.chronicle.ranking.Rivals;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.guild.Recruits;
import com.beyondsolo.network.NewsPayload;
import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.rival.RivalParty;
import com.beyondsolo.scouting.ScoutingConfig;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The hunter news: what the country's hunters hear about each other. A hunter's rank-up makes the news (an S-rank is
 * breaking news), and so do the great guilds fighting over them, a contract signed or every guild turned down, races
 * with rival parties, a dungeon break and its suppression, a world boss brought down; between them, word of the
 * world's other hunters and guilds now and then ({@code news.ambientMinutes}). Every headline is kept for the NEWS
 * page of the Chronicle ({@code news.keep} of them) and runs across everyone's ticker as it comes in.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class HunterNews {
    public static final String KEY = "news.beyondsolo.";
    /** What a headline is about (its mark on the page and the ticker). */
    public static final String RANK = "rank", GUILD = "guild", RACE = "race", BREAK = "break", BOSS = "boss", WORLD = "world";
    /** A duel with one of the national-level hunters ({@code duel.Duels}). */
    public static final String DUEL = "duel";
    /** The world's colour for the news itself (headlines of the world at large). */
    public static final int STEEL = 0xFF8FB4FF;
    public static final int BREAK_RED = 0xFFFF5A3C;
    public static final int GOLD = 0xFFFFC247;

    /** Runs a headline: kept, and across every hunter's ticker now. */
    public static Headline post(MinecraftServer server, String kind, Component text, int accent, boolean breaking) {
        NewsData data = NewsData.get(server);
        long time = server.overworld().getGameTime();
        long day = server.overworld().getDayTime() / 24000L;
        Headline h = data.add(time, day, kind, Component.Serializer.toJson(text, server.registryAccess()), 0xFF000000 | accent, breaking,
                ScoutingConfig.newsKeep());
        ListTag list = new ListTag();
        list.add(h.save());
        CompoundTag t = new CompoundTag();
        t.put("headlines", list);
        t.putBoolean("flash", true);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(p, new NewsPayload(t));
        return h;
    }

    /** Every headline kept, to one hunter (at login). */
    public static void sync(ServerPlayer p) {
        ListTag list = new ListTag();
        for (Headline h : NewsData.get(p.server).headlines()) list.add(h.save());
        CompoundTag t = new CompoundTag();
        t.put("headlines", list);
        t.putBoolean("reset", true);
        PacketDistributor.sendToPlayer(p, new NewsPayload(t));
    }

    /** Every headline forgotten, on every hunter's page too (/solo news clear). */
    public static void clear(MinecraftServer server) {
        NewsData.get(server).clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }

    static Component t(String key, Object... args) {
        return Component.translatable(KEY + key, args);
    }

    static Component name(ServerPlayer p) {
        return Component.literal(p.getGameProfile().getName());
    }

    // ================================================================ the hunters

    /** A hunter's new rank, as the association announces it (from C up: below that, nobody is watching yet). */
    public static void rankUp(ServerPlayer p, PlayerRank rank) {
        if (rank.tier() < PlayerRank.C.tier()) return;
        String which = rank.name().toLowerCase(java.util.Locale.ROOT);
        post(p.server, RANK, t("rank_up." + which, name(p)), rank.color(), rank == PlayerRank.S);
    }

    /** A hunter who awakened high (C and up). */
    public static void awakened(ServerPlayer p, PlayerRank rank) {
        if (rank.tier() < PlayerRank.C.tier()) return;
        post(p.server, RANK, t(rank == PlayerRank.S ? "awakened.s" : "awakened", name(p), rank.displayName()), rank.color(), rank == PlayerRank.S);
    }

    /** Two or more great guilds want the same hunter. */
    public static void biddingWar(ServerPlayer p, List<RivalGuild> guilds, PlayerRank rank) {
        if (guilds.size() < 2) return;
        Component text = guilds.size() == 2 ? t("bidding_war.two", name(p), guilds.get(0).displayName(), guilds.get(1).displayName())
                : t("bidding_war.many", name(p), guilds.size());
        post(p.server, GUILD, text, guilds.get(guilds.size() - 1).color, rank == PlayerRank.S);
    }

    /** A hunter signed with a guild (bought out of another's contract, or renewing one). */
    public static void signed(ServerPlayer p, RivalGuild guild, long bonus, PlayerRank rank, boolean renewal, @Nullable RivalGuild from) {
        String gold = String.format(java.util.Locale.ROOT, "%,d", bonus);
        Component text = from != null ? t("signed.buyout", guild.displayName(), name(p), from.displayName(), gold)
                : renewal ? t("signed.renewal", name(p), guild.displayName(), gold)
                : t("signed", name(p), guild.displayName(), gold);
        post(p.server, GUILD, text, guild.color, rank == PlayerRank.S && !renewal);
    }

    /** A hunter turned every great guild down. */
    public static void notForSale(ServerPlayer p) {
        post(p.server, GUILD, t("not_for_sale", name(p)), GOLD, true);
    }

    /** A hunter walked out on their contract. */
    public static void walkedOut(ServerPlayer p, RivalGuild guild) {
        post(p.server, GUILD, t("walked_out", name(p), guild.displayName()), guild.color, false);
    }

    // ================================================================ gates and the parties

    /** Hunters beat a rival party to a boss. */
    public static void raceWon(MinecraftServer server, Collection<ServerPlayer> winners, RivalParty party, PlayerRank rank) {
        if (winners.isEmpty()) return;
        post(server, RACE, t("race_won", names(winners), party.partyName(), party.guild.displayName(), rank.displayName()), party.guild.color, false);
    }

    /** A rival party beat hunters to a boss. */
    public static void raceLost(MinecraftServer server, Collection<ServerPlayer> losers, RivalParty party, PlayerRank rank) {
        if (losers.isEmpty()) return;
        post(server, RACE, t("race_lost", party.partyName(), party.guild.displayName(), names(losers), rank.displayName()), party.guild.color, false);
    }

    /** A party led by a hunter of the national rankings cleared its gate (other parties make no news). */
    public static void partyCleared(MinecraftServer server, RivalParty party, PlayerRank rank) {
        if (party.rival.isEmpty()) return;
        post(server, RACE, t("party_cleared", party.leaderName(), party.guild.displayName(), rank.displayName()), party.guild.color, false);
    }

    /** A gate broke open into the world. */
    public static void breakOpened(MinecraftServer server, GateInstance g) {
        post(server, BREAK, t("break_opened", g.rank().displayName(), g.dungeonName()), BREAK_RED, g.rank().tier() >= PlayerRank.A.tier());
    }

    /** A break was put down. */
    public static void breakSuppressed(MinecraftServer server, GateInstance g, Collection<ServerPlayer> paid) {
        Component text = paid.isEmpty() ? t("break_suppressed.nobody", g.rank().displayName())
                : t("break_suppressed", names(paid), g.rank().displayName());
        post(server, BREAK, text, GOLD, false);
    }

    /** A world boss (or a monarch) was brought down. */
    public static void bossSlain(MinecraftServer server, WorldBossKind kind, @Nullable ServerPlayer top) {
        Component text = top == null ? t(kind.monarch() ? "monarch_slain.nobody" : "boss_slain.nobody", kind.fullName())
                : t(kind.monarch() ? "monarch_slain" : "boss_slain", kind.fullName(), name(top));
        post(server, BOSS, text, GOLD, kind.monarch());
    }

    /** "Ana", "Ana and Bo", "Ana and 2 others". */
    static Component names(Collection<ServerPlayer> players) {
        List<ServerPlayer> list = new ArrayList<>(players);
        if (list.size() == 1) return name(list.get(0));
        if (list.size() == 2) return t("names.two", name(list.get(0)), name(list.get(1)));
        return t("names.many", name(list.get(0)), list.size() - 1);
    }

    // ================================================================ the world at large

    /** Word of the world's other hunters and guilds, now and then. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 200 != 37) return;
        int every = ScoutingConfig.newsAmbientTicks();
        if (every <= 0 || server.getPlayerList().getPlayers().isEmpty()) return;
        NewsData data = NewsData.get(server);
        long now = server.overworld().getGameTime();
        RandomSource r = server.overworld().random;
        if (data.nextAmbient <= 0) {
            data.nextAmbient = now + every / 2 + r.nextInt(Math.max(1, every));
            data.setDirty();
            return;
        }
        if (now < data.nextAmbient) return;
        data.nextAmbient = now + every / 2 + r.nextInt(Math.max(1, every));
        data.setDirty();
        ambient(server, r);
    }

    /** One headline about the world's other hunters (commands use it too). */
    public static Headline ambient(MinecraftServer server, RandomSource r) {
        List<Rival> ranked = new ArrayList<>();
        for (Rival v : Rivals.ALL) if (v.rank().tier() >= PlayerRank.C.tier()) ranked.add(v);
        Rival v = ranked.get(r.nextInt(ranked.size()));
        RivalGuild guild = RivalGuild.values()[r.nextInt(4)];
        RivalGuild other = RivalGuild.values()[(guild.ordinal() + 1 + r.nextInt(3)) % 4];
        return switch (r.nextInt(8)) {
            case 0 -> post(server, WORLD, t("ambient.clear", v.name(), Rivals.guildName(v.guild()), v.rank().displayName()), color(v), false);
            case 1 -> top(server);
            case 2 -> post(server, WORLD, t("ambient.recruit", guild.displayName(), PlayerRank.values()[2 + r.nextInt(3)].displayName()), guild.color, false);
            case 3 -> post(server, WORLD, t("ambient.joint_raid", guild.displayName(), other.displayName(), PlayerRank.values()[4 + r.nextInt(2)].displayName()),
                    guild.color, false);
            case 4 -> {
                List<Rival> solo = new ArrayList<>();
                for (Rival x : ranked) if (x.guild().isEmpty()) solo.add(x);
                Rival s = solo.isEmpty() ? v : solo.get(r.nextInt(solo.size()));
                yield post(server, WORLD, t("ambient.solo", s.name(), s.rank().displayName()), color(s), false);
            }
            case 5 -> post(server, WORLD, t("ambient.exam_season", 20 + r.nextInt(41)), STEEL, false);
            case 6 -> post(server, WORLD, t("ambient.injured", v.name(), Rivals.guildName(v.guild()), v.rank().displayName()), color(v), false);
            default -> post(server, WORLD, t("ambient.rookie", Recruits.name(r)), STEEL, false);
        };
    }

    private static int color(Rival v) {
        return RivalGuild.of(v.guild()).color;
    }

    /** Who stands first on the national merit board (a hunter of this server, if one does). */
    static Headline top(MinecraftServer server) {
        long day = Rankings.day(server);
        String best = null;
        long bestMerit = -1;
        boolean player = false;
        int color = STEEL;
        for (Rival r : Rivals.ALL) {
            long m = Association.rivalMerit(server, r, day);
            if (m > bestMerit) {
                bestMerit = m;
                best = r.name();
                color = RivalGuild.of(r.guild()).color;
            }
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!SystemAPI.isAwakened(p)) continue;
            long m = Association.record(p).merit();
            if (m > bestMerit) {
                bestMerit = m;
                best = p.getGameProfile().getName();
                player = true;
                color = GOLD;
            }
        }
        return post(server, WORLD, t(player ? "ambient.top.player" : "ambient.top", best), color, false);
    }

    // ================================================================ hunters coming in

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    private HunterNews() {}
}
