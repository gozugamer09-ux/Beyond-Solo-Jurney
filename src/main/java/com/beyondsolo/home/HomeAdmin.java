package com.beyondsolo.home;

import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.story.Kin;
import com.beyondsolo.worldboss.WorldBossKind;
import com.beyondsolo.worldboss.WorldBossModule;
import com.beyondsolo.worldboss.WorldBossRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What operators do to homes ({@code /solo home}) and what {@code /home status} says: a home given or taken for free,
 * a hunter taken home or out at once, builds finished or started again, a night's rest, the wait cleared, the trophy
 * hall filled for a look.
 */
public final class HomeAdmin {
    /** The tiers as commands name them. */
    public static final List<String> TIERS = Arrays.stream(HomeTier.values()).map(HomeTier::id).toList();

    /** The hunter owns {@code tier} now, for free ({@link HomeTier#NONE}: their home is taken away). */
    public static void give(ServerPlayer p, HomeTier tier) {
        Homes.own(p, tier);
    }

    /** Home at once, no gathering and no wait: false if they own none or there is no world for homes. */
    public static boolean go(ServerPlayer p) {
        Homes.cancel(p, null);
        return Homes.travel(p, null);
    }

    /** Into {@code host}'s home at once, party or not: false if the host owns none. */
    public static boolean visit(ServerPlayer p, UUID host) {
        Homes.cancel(p, null);
        return Homes.travel(p, host.equals(p.getUUID()) ? null : host);
    }

    /** Back out into the world: false if they are in no home. */
    public static boolean leave(ServerPlayer p) {
        if (!Homes.inAnyHome(p)) return false;
        Homes.goBack(p, true);
        return true;
    }

    /** Builds whatever is going up, now: false if there is no world for homes. */
    public static boolean finishBuilds(MinecraftServer server) {
        ServerLevel level = HomeWorld.level(server);
        if (level == null) return false;
        Homes.finishAll(level);
        return true;
    }

    /** The hunter's home (and the city round it) is built again from nothing: false if they own none. */
    public static boolean rebuild(ServerPlayer p) {
        ServerLevel level = HomeWorld.level(p.server);
        int plot = HomeData.get(p.server).plot(p.getUUID());
        HomeRecord rec = Homes.record(p);
        if (level == null || plot < 0 || !rec.owns()) return false;
        Homes.rebuild(level, plot, rec.tier(), true);
        return true;
    }

    /** A night's rest now, as if they slept in their bed, Well Rested again even if they had it today. */
    public static void rest(ServerPlayer p) {
        Homes.record(p).restDay = HomeRecord.NEVER;
        Homes.rest(p);
    }

    /** No wait before they can go home again. */
    public static void ready(ServerPlayer p) {
        Homes.record(p).readyAt = 0;
        Homes.sync(p);
    }

    /** Every great threat of the trophy hall slain {@code count} times (0: none), and the hall shown so. */
    public static void trophies(ServerPlayer p, int count) {
        WorldBossRecord r = p.getData(WorldBossModule.RECORD.get());
        for (WorldBossKind k : HomeWorld.TROPHIES) r.setSlain(k, count);
        ServerLevel level = HomeWorld.level(p.server);
        int plot = HomeData.get(p.server).plot(p.getUUID());
        if (level != null && plot >= 0) Homes.refreshTrophies(level, HomeWorld.plan(level, plot), p);
        Homes.sync(p);
    }

    /** How a hunter's home stands, in two lines. */
    public static List<Component> info(ServerPlayer p) {
        MinecraftServer server = p.server;
        HomeRecord rec = Homes.record(p);
        String k = Homes.KEY + "status.";
        List<Component> out = new ArrayList<>();
        if (!rec.owns()) {
            HomeTier next = HomeTier.STUDIO;
            out.add(Component.translatable(k + "none", next.displayName(), HomeConfig.level(next), HomeConfig.rank(next).displayName(),
                    String.format(Locale.ROOT, "%,d", HomeConfig.gold(next))).withStyle(ChatFormatting.GRAY));
        } else {
            Component where;
            UUID visiting = rec.visiting();
            if (Homes.inOwnHome(p)) where = Component.translatable(k + "where.home");
            else if (visiting != null && Homes.inAnyHome(p)) where = Component.translatable(k + "where.guest", Homes.hostName(server, visiting));
            else where = Component.translatable(k + "where.out");
            out.add(Component.translatable(k + "owner", p.getDisplayName(), rec.tier().displayName(), where).withStyle(ChatFormatting.GRAY));
            HomeTier next = rec.tier().next();
            if (next != null) {
                out.add(Component.translatable(k + "next", next.displayName(), HomeConfig.level(next), HomeConfig.rank(next).displayName(),
                        String.format(Locale.ROOT, "%,d", HomeConfig.gold(next))).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        long wait = Math.max(0, rec.readyAt - Homes.now(server));
        int trophies = 0;
        WorldBossRecord kills = p.getData(WorldBossModule.RECORD.get());
        for (WorldBossKind kind : HomeWorld.TROPHIES) if (kills.slain(kind) > 0) trophies++;
        Component yes = Component.translatable(k + "yes"), no = Component.translatable(k + "no");
        out.add(Component.translatable(k + "facts",
                rec.restDay() == QuestEngine.day(p) ? yes : no,
                rec.motherHere ? yes : Kin.record(p).awake() ? Component.translatable(k + "mother_waiting") : no,
                rec.bestDps > 0 ? String.format(Locale.ROOT, "%,.0f", rec.bestDps) : "-",
                trophies, HomeWorld.TROPHIES.length,
                wait > 0 ? Homes.clock(wait) : Component.translatable(k + "now")).withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    private HomeAdmin() {}
}
