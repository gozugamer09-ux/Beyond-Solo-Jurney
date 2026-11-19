package com.beyondsolo.command.solo;

import com.beyondsolo.isle.IsleAdmin;
import com.beyondsolo.isle.IsleRaids;
import com.beyondsolo.isle.IsleRecord;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * The Halcyon raids ({@code isle.IsleRaids}):
 * <pre>
 * /solo isle call [players]                       the Association calls a raid (named players: any rank)
 * /solo isle land [players]                       called if need be, answered, and landed on the island now
 * /solo isle launch                               the muster ends now: whoever answered lands
 * /solo isle phase beach|nests|hive|queen|king    the raid that has landed moves on (queen, king: you go down)
 * /solo isle win | fail | stop                    retaken now (all paid) | lost now | over at once (no pay)
 * /solo isle status                               how it stands, the next call, the island
 * /solo isle next &lt;minutes&gt;                       the Association's next call
 * /solo isle build                                the island is built again from nothing
 * /solo isle visit [player]                       to the island's camp to look around (/isle leave goes back)
 * /solo isle record [player] | reset [players]    a hunter's record of the raids, or a fresh one
 * </pre>
 */
final class SoloIsle {
    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("isle")
                .then(Commands.literal("call")
                        .executes(ctx -> say(ctx, IsleAdmin.call(ctx.getSource().getServer(), null)))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> say(ctx, IsleAdmin.call(ctx.getSource().getServer(), EntityArgument.getPlayers(ctx, "players"))))))
                .then(Commands.literal("land")
                        .executes(ctx -> land(ctx, java.util.List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> land(ctx, EntityArgument.getPlayers(ctx, "players")))))
                .then(Commands.literal("launch").executes(ctx -> say(ctx, IsleAdmin.launch(ctx.getSource().getServer()))))
                .then(Commands.literal("phase").then(Solo.word("phase", IsleAdmin.PHASES::stream).executes(ctx -> say(ctx,
                        IsleAdmin.phase(ctx.getSource().getServer(), Solo.lower(StringArgumentType.getString(ctx, "phase")), ctx.getSource().getPlayer())))))
                .then(Commands.literal("win").executes(ctx -> say(ctx, IsleAdmin.win(ctx.getSource().getServer()))))
                .then(Commands.literal("fail").executes(ctx -> say(ctx, IsleAdmin.fail(ctx.getSource().getServer()))))
                .then(Commands.literal("stop").executes(ctx -> {
                    if (!IsleAdmin.stop(ctx.getSource().getServer())) throw Solo.error("no_isle_raid");
                    ctx.getSource().sendSuccess(() -> Solo.t("isle.stopped"), true);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    for (Component line : IsleAdmin.status(ctx.getSource().getServer(), p)) ctx.getSource().sendSuccess(() -> line, false);
                    return 1;
                }))
                .then(Commands.literal("next").then(Commands.argument("minutes", IntegerArgumentType.integer(0, 100000)).executes(ctx -> {
                    int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                    IsleAdmin.next(ctx.getSource().getServer(), minutes);
                    ctx.getSource().sendSuccess(() -> Solo.t("isle.next", minutes), true);
                    return 1;
                })))
                .then(Commands.literal("build").executes(ctx -> say(ctx, IsleAdmin.build(ctx.getSource().getServer()))))
                .then(Solo.target(Commands.literal("visit"), (ctx, p) -> {
                    IsleRaids.Outcome out = IsleAdmin.visit(p);
                    if (!out.ok()) throw failure(out);
                    return Solo.t("isle.visit", p.getDisplayName());
                }))
                .then(Solo.target(Commands.literal("record"), (ctx, p) -> {
                    IsleRecord r = IsleAdmin.record(p);
                    return Solo.t("isle.record", p.getDisplayName(), r.raids(), r.cleared(), r.nests(), r.queens(), r.kings(),
                            r.best() > 0 ? (r.best() / 20 / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", r.best() / 20 % 60) : "-");
                }))
                .then(Solo.targets(Commands.literal("reset"), (ctx, p) -> {
                    IsleAdmin.resetRecord(p);
                    return Solo.t("isle.reset", p.getDisplayName());
                })));
    }

    private static int land(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> who) throws CommandSyntaxException {
        for (ServerPlayer p : who) Solo.needEvaluated(p);
        return say(ctx, IsleAdmin.land(ctx.getSource().getServer(), who));
    }

    /** Says what the raid did, or fails with why it would not. */
    private static int say(CommandContext<CommandSourceStack> ctx, IsleRaids.Outcome out) throws CommandSyntaxException {
        if (!out.ok()) throw failure(out);
        ctx.getSource().sendSuccess(out::text, true);
        return 1;
    }

    private static CommandSyntaxException failure(IsleRaids.Outcome out) {
        return new SimpleCommandExceptionType(out.text()).create();
    }

    private SoloIsle() {}
}
