package com.beyondsolo.command.solo;

import com.beyondsolo.duel.Duel;
import com.beyondsolo.duel.DuelRecord;
import com.beyondsolo.duel.DuelistEntity;
import com.beyondsolo.duel.Duels;
import com.beyondsolo.duel.NationalHunter;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Duels with the national-level hunters ({@code duel.Duels}):
 * <pre>
 * /solo duel challenge &lt;hunter&gt; [test|passed|grudge|asked] [players]   a challenge from them, opening now
 * /solo duel send [players]                 the challenger sets out for the player now
 * /solo duel talk [player]                  the challenge's window, from the challenger near the player
 * /solo duel lapse [players]                the challenge runs out now
 * /solo duel start &lt;hunter&gt; [players]       a duel with them begins now where the player stands
 * /solo duel bell [players]                 the count is skipped: they fight now
 * /solo duel move &lt;move&gt; [players]          the challenger begins that move now
 * /solo duel unleash [players]              the challenger shows what they really are now
 * /solo duel win|lose|draw|call [players]   the duel under way ends so
 * /solo duel info [player]                  the record against each of them and the challenge standing
 * /solo duel reset [players]                the record forgotten, the challenge gone, the challengers home
 * </pre>
 */
final class SoloDuels {
    static final List<String> HUNTERS = Arrays.stream(NationalHunter.values()).map(h -> h.id).toList();

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        LiteralArgumentBuilder<CommandSourceStack> challenge = Commands.literal("challenge");
        LiteralArgumentBuilder<CommandSourceStack> start = Commands.literal("start");
        for (NationalHunter h : NationalHunter.values()) {
            LiteralArgumentBuilder<CommandSourceStack> who = Commands.literal(h.id);
            Solo.targets(who, (ctx, p) -> challenge(p, h, DuelRecord.Reason.ASKED));
            for (DuelRecord.Reason why : DuelRecord.Reason.values()) {
                who.then(Solo.targets(Commands.literal(why.key()), (ctx, p) -> challenge(p, h, why)));
            }
            challenge.then(who);
            start.then(Solo.targets(Commands.literal(h.id), (ctx, p) -> start(p, h)));
        }
        LiteralArgumentBuilder<CommandSourceStack> move = Commands.literal("move");
        for (DuelistEntity.Move m : DuelistEntity.Move.values()) {
            move.then(Solo.targets(Commands.literal(m.key()), (ctx, p) -> {
                if (!Duels.moveNow(p, m)) throw Solo.error("duel_move", p.getDisplayName(), m.key());
                return Solo.t("duel.moved", p.getDisplayName(), m.key());
            }));
        }
        LiteralArgumentBuilder<CommandSourceStack> duel = Commands.literal("duel")
                .then(challenge)
                .then(Solo.targets(Commands.literal("send"), (ctx, p) -> {
                    if (Duels.record(p).challenge() == null) throw Solo.error("no_duel_challenge", p.getDisplayName());
                    if (!Duels.sendNow(p)) throw Solo.error("no_duel_spot", p.getDisplayName());
                    return Solo.t("duel.sent", Duels.record(p).challenge().who.displayName(), p.getDisplayName());
                }))
                .then(Solo.target(Commands.literal("talk"), (ctx, p) -> {
                    DuelistEntity e = Duels.challenger(p);
                    if (e == null) throw Solo.error("no_duel_challenger", p.getDisplayName());
                    Duels.talk(p, e);
                    return null;
                }))
                .then(Solo.targets(Commands.literal("lapse"), (ctx, p) -> {
                    if (!Duels.lapseNow(p)) throw Solo.error("no_duel_challenge", p.getDisplayName());
                    return Solo.t("duel.lapsed", p.getDisplayName());
                }))
                .then(start)
                .then(Solo.targets(Commands.literal("bell"), (ctx, p) -> {
                    Duel d = running(p);
                    Duels.bell(p.server, d);
                    return Solo.t("duel.bell", p.getDisplayName());
                }))
                .then(move)
                .then(Solo.targets(Commands.literal("unleash"), (ctx, p) -> {
                    if (!Duels.unleashNow(p)) throw Solo.error("duel_unleash", p.getDisplayName());
                    return Solo.t("duel.unleashed", p.getDisplayName());
                }))
                .then(Solo.targets(Commands.literal("win"), (ctx, p) -> end(p, Duel.Result.WON)))
                .then(Solo.targets(Commands.literal("lose"), (ctx, p) -> end(p, Duel.Result.LOST)))
                .then(Solo.targets(Commands.literal("draw"), (ctx, p) -> end(p, Duel.Result.DRAWN)))
                .then(Solo.targets(Commands.literal("call"), (ctx, p) -> end(p, Duel.Result.CALLED_OFF)))
                .then(Solo.target(Commands.literal("info"), SoloDuels::info))
                .then(Solo.targets(Commands.literal("reset"), (ctx, p) -> {
                    Duels.reset(p);
                    return Solo.t("duel.reset", p.getDisplayName());
                }));
        root.then(duel);
    }

    private static Component challenge(ServerPlayer p, NationalHunter who, DuelRecord.Reason why) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        if (Duels.running(p.getUUID()) != null) throw Solo.error("dueling", p.getDisplayName());
        Duels.challenge(p, who, why, p.server.overworld().getGameTime());
        Duels.tick(p);
        return Solo.t("duel.challenged", who.displayName(), p.getDisplayName(), why.key());
    }

    private static Component start(ServerPlayer p, NationalHunter who) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        if (Duels.running(p.getUUID()) != null) throw Solo.error("dueling", p.getDisplayName());
        DuelRecord.Challenge c = Duels.record(p).challenge();
        DuelRecord.Reason why = c != null && c.who == who ? c.why : DuelRecord.Reason.ASKED;
        if (Duels.startNow(p, who, why) == null) throw Solo.error("no_duel_spot", p.getDisplayName());
        return Solo.t("duel.started", p.getDisplayName(), who.displayName());
    }

    private static Duel running(ServerPlayer p) throws CommandSyntaxException {
        Duel d = Duels.running(p.getUUID());
        if (d == null || d.phase == Duel.Phase.OVER) throw Solo.error("no_duel", p.getDisplayName());
        return d;
    }

    private static Component end(ServerPlayer p, Duel.Result result) throws CommandSyntaxException {
        Duel d = running(p);
        Duels.finish(p.server, d, result);
        return Solo.t("duel.ended", p.getDisplayName(), result.name().toLowerCase(Locale.ROOT));
    }

    private static Component info(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
        DuelRecord r = Duels.record(p);
        for (NationalHunter h : NationalHunter.values()) {
            ctx.getSource().sendSuccess(() -> Solo.t("duel.line", h.displayName(), r.wins(h), r.losses(h), r.draws(h),
                    r.passed(h) ? Solo.t("duel.passed") : Component.empty()), false);
        }
        DuelRecord.Challenge c = r.challenge();
        if (c != null) {
            long now = p.server.overworld().getGameTime();
            ctx.getSource().sendSuccess(() -> Solo.t("duel.standing", c.who.displayName(), c.why.key(),
                    c.open(now) ? Solo.t("duel.open") : Solo.t("duel.opens", (c.openAt - now) / 20), c.later), false);
        }
        return Solo.t("duel.record", p.getDisplayName(), r.totalWins(), r.totalLosses(), r.totalDraws(), r.declined(), r.forfeits(),
                r.tested() ? Solo.t("duel.tested") : Component.empty(), r.national() ? Solo.t("duel.national") : Component.empty());
    }

    private SoloDuels() {}
}
