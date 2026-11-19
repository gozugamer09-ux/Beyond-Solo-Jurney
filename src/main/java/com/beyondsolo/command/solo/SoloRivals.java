package com.beyondsolo.command.solo;

import com.beyondsolo.chronicle.ranking.Rival;
import com.beyondsolo.chronicle.ranking.Rivals;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateSpawner;
import com.beyondsolo.rival.Hire;
import com.beyondsolo.rival.Hires;
import com.beyondsolo.rival.RivalData;
import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.rival.RivalParties;
import com.beyondsolo.rival.RivalParty;
import com.beyondsolo.rival.RivalRecord;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The world's other hunters ({@code rival.RivalParties}) and the hands for hire ({@code rival.Hires}):
 * <pre>
 * /solo rival send [guild|random] [ranked]       a party for the nearest free gate (a new gate if none is free)
 * /solo rival arrive                             the nearest party reaches its gate now
 * /solo rival enter                              the nearest party goes into its gate now
 * /solo rival finish clear|retreat               the nearest party inside clears its gate, or pulls out
 * /solo rival list                               every party, where it is and how it is doing
 * /solo rival forget                             every party is forgotten, its hunters gone
 * /solo rival record [player]                    the player's races and trespasses
 * /solo hire porter [e|d] [players]              a porter for nothing, now
 * /solo hire hunter [rank] [class] [players]     a freelance hunter for nothing, now (their own rank if left out)
 * /solo hire dismiss [players]                   every hand let go (a porter hands over the pack)
 * /solo hire list [player]                       the hands on the job
 * </pre>
 */
final class SoloRivals {
    static final List<String> GUILDS = Stream.concat(Arrays.stream(RivalGuild.values()).map(RivalGuild::key), Stream.of("random")).toList();

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> send = Commands.literal("send").executes(ctx -> send(ctx, null, false));
        send.then(Commands.literal("ranked").executes(ctx -> send(ctx, null, true)));
        for (String guild : GUILDS) {
            send.then(Commands.literal(guild).executes(ctx -> send(ctx, guild, false))
                    .then(Commands.literal("ranked").executes(ctx -> send(ctx, guild, true))));
        }
        root.then(Commands.literal("rival")
                .then(send)
                .then(Commands.literal("arrive").executes(ctx -> step(ctx, false)))
                .then(Commands.literal("enter").executes(ctx -> step(ctx, true)))
                .then(Commands.literal("finish")
                        .then(Commands.literal("clear").executes(ctx -> finish(ctx, true)))
                        .then(Commands.literal("retreat").executes(ctx -> finish(ctx, false))))
                .then(Commands.literal("list").executes(SoloRivals::list))
                .then(Commands.literal("forget").executes(ctx -> {
                    RivalParties.forget(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Solo.t("rival.forgot"), true);
                    return 1;
                }))
                .then(Solo.target(Commands.literal("record"), (ctx, p) -> {
                    RivalRecord r = Hires.record(p);
                    return Solo.t("rival.record", p.getDisplayName(), r.raceWins(), r.raceLosses(), r.trespasses());
                })));

        LiteralArgumentBuilder<CommandSourceStack> porter = Commands.literal("porter");
        Solo.targets(porter, (ctx, p) -> hire(p, Hire.Kind.PORTER, PlayerRank.E, null));
        for (PlayerRank r : List.of(PlayerRank.E, PlayerRank.D)) {
            porter.then(Solo.targets(Commands.literal(Solo.lower(r.name())), (ctx, p) -> hire(p, Hire.Kind.PORTER, r, null)));
        }
        LiteralArgumentBuilder<CommandSourceStack> hunter = Commands.literal("hunter");
        Solo.targets(hunter, (ctx, p) -> hire(p, Hire.Kind.HUNTER, null, null));
        for (String r : Solo.RANKS) {
            LiteralArgumentBuilder<CommandSourceStack> rank = Commands.literal(r);
            Solo.targets(rank, (ctx, p) -> hire(p, Hire.Kind.HUNTER, Solo.rank(r, ctx.getSource()), null));
            for (String c : Solo.CLASSES) {
                rank.then(Solo.targets(Commands.literal(c), (ctx, p) -> hire(p, Hire.Kind.HUNTER, Solo.rank(r, ctx.getSource()),
                        Solo.playerClass(c, ctx.getSource()))));
            }
            hunter.then(rank);
        }
        root.then(Commands.literal("hire")
                .then(porter)
                .then(hunter)
                .then(Solo.targets(Commands.literal("dismiss"), (ctx, p) -> {
                    int n = Hires.record(p).hires().size();
                    for (Hire h : List.copyOf(Hires.record(p).hires())) Hires.dismiss(p, h.id);
                    return Solo.t("hire.dismissed", p.getDisplayName(), n);
                }))
                .then(Solo.target(Commands.literal("list"), (ctx, p) -> {
                    List<Hire> hires = Hires.record(p).hires();
                    if (hires.isEmpty()) return Solo.t("hire.none", p.getDisplayName());
                    long now = RivalParties.now(p.server);
                    for (Hire h : hires) {
                        Component what = h.porter() ? Component.translatable("rival.beyondsolo.hire.porter")
                                : Component.translatable("rival.beyondsolo.hire.hunter_of", h.cls.displayName(), h.level);
                        ctx.getSource().sendSuccess(() -> Solo.t("hire.line", h.name, what, h.rank().displayName(),
                                RivalParties.clock(h.until - now), h.kills(), h.carried()), false);
                    }
                    return null;
                })));
    }

    // ---------------------------------------------------------------- parties

    private static int send(CommandContext<CommandSourceStack> ctx, @Nullable String guildName, boolean ranked) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        MinecraftServer server = p.server;
        if (!p.level().dimension().equals(Level.OVERWORLD)) throw Solo.error("not_overworld");
        RivalGuild guild = guildName == null || guildName.equals("random") ? null : RivalGuild.valueOf(guildName.toUpperCase(Locale.ROOT));
        GateInstance g = nearestFree(p);
        if (g == null) {
            GateBlockEntity block = GateSpawner.trySpawnNear(p.serverLevel(), p, null, false);
            g = block == null || block.gateId() == null ? null : GateData.get(server).get(block.gateId());
            if (g == null || !RivalParties.eligible(server, g)) throw Solo.error("no_gate_spot");
        }
        Rival leader = null;
        if (ranked) {
            List<Rival> free = new ArrayList<>();
            RivalData data = RivalData.get(server);
            for (Rival v : Rivals.ALL) {
                if (v.rank() != g.rank() || data.busy(v.id())) continue;
                if (guild != null && RivalGuild.of(v.guild()) != guild) continue;
                free.add(v);
            }
            if (free.isEmpty()) throw Solo.error("no_ranked_rival", g.rank().displayName(), guild == null ? "-" : guild.displayName());
            leader = free.get(p.getRandom().nextInt(free.size()));
        }
        RivalParty party = RivalParties.dispatch(server, g, guild, leader, p.getRandom());
        if (party == null) throw Solo.error("rival_gate_taken");
        GateInstance gate = g;
        long eta = party.arriveAt() - RivalParties.now(server);
        ctx.getSource().sendSuccess(() -> Solo.t("rival.sent", party.partyName(), gate.rank().displayName(), gate.gatePos().toShortString(),
                RivalParties.clock(eta)), true);
        return 1;
    }

    /** The open gate nearest the player that a party may go for, within reach of the commands. */
    @Nullable
    private static GateInstance nearestFree(ServerPlayer p) {
        GateInstance best = null;
        double bestD = (double) Solo.RANGE * Solo.RANGE;
        for (GateInstance g : GateData.get(p.server).all()) {
            if (!RivalParties.eligible(p.server, g)) continue;
            double d = GateSpawner.flatDistSqr(p.position(), g.gatePos());
            if (d < bestD) {
                bestD = d;
                best = g;
            }
        }
        return best;
    }

    private static RivalParty nearest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        RivalParty party = RivalParties.nearest(ctx.getSource().getServer(), ctx.getSource().getPosition());
        if (party == null) throw Solo.error("no_rival_party");
        return party;
    }

    private static int step(CommandContext<CommandSourceStack> ctx, boolean enter) throws CommandSyntaxException {
        RivalParty party = nearest(ctx);
        MinecraftServer server = ctx.getSource().getServer();
        if (enter) {
            if (party.state() == RivalParty.State.INSIDE) throw Solo.error("rival_inside_already");
            RivalParties.enterNow(server, party);
        } else {
            if (party.state() != RivalParty.State.EN_ROUTE) throw Solo.error("rival_not_en_route");
            RivalParties.arriveNow(server, party);
        }
        ctx.getSource().sendSuccess(() -> Solo.t(enter ? "rival.entered" : "rival.arrived", party.partyName(), state(party)), true);
        return 1;
    }

    private static int finish(CommandContext<CommandSourceStack> ctx, boolean cleared) throws CommandSyntaxException {
        RivalParty party = nearest(ctx);
        if (party.state() != RivalParty.State.INSIDE) throw Solo.error("rival_not_inside");
        RivalParties.finishNow(ctx.getSource().getServer(), party, cleared);
        ctx.getSource().sendSuccess(() -> Solo.t(cleared ? "rival.cleared" : "rival.retreated", party.partyName()), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        List<RivalParty> parties = new ArrayList<>(RivalData.get(server).parties());
        if (parties.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Solo.t("rival.none"), false);
            return 0;
        }
        GateData gates = GateData.get(server);
        for (RivalParty party : parties) {
            GateInstance g = gates.get(party.gate);
            String where = g == null ? "-" : g.gatePos().toShortString();
            ctx.getSource().sendSuccess(() -> Solo.t("rival.line", party.partyName(), party.rank.displayName(), where, state(party),
                    party.standing().size(), party.members.size()), false);
        }
        return parties.size();
    }

    /** "en route", "inside (holds, 43%)", "done: cleared". */
    private static Component state(RivalParty p) {
        String s = p.state().name().toLowerCase(Locale.ROOT);
        if (p.state() == RivalParty.State.INSIDE) {
            return Solo.t("rival.state.inside", Solo.t(p.racing() ? "rival.state.racing" : p.holds() ? "rival.state.holds" : "rival.state.behind"),
                    Math.round(p.progress() * 100));
        }
        if (p.state() == RivalParty.State.DONE) return Solo.t("rival.state.done", Solo.t("rival.outcome." + p.outcome().name().toLowerCase(Locale.ROOT)));
        return Solo.t("rival.state." + s);
    }

    // ---------------------------------------------------------------- hands for hire

    private static Component hire(ServerPlayer p, Hire.Kind kind, @Nullable PlayerRank rank, @Nullable PlayerClass cls) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        PlayerRank r = rank != null ? rank : kind == Hire.Kind.PORTER ? PlayerRank.E : SystemAPI.get(p).rank();
        Hire h = Hires.hireFree(p, kind, r, cls);
        if (h == null) throw Solo.error(kind == Hire.Kind.PORTER ? "hire_porter_already" : "hire_hunters_full", p.getDisplayName());
        Component what = h.porter() ? Component.translatable("rival.beyondsolo.hire.porter")
                : Component.translatable("rival.beyondsolo.hire.hunter_of", h.cls.displayName(), h.level);
        return Solo.t("hire.hired", p.getDisplayName(), h.name, what);
    }

    private SoloRivals() {}
}
