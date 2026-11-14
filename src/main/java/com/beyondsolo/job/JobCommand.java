package com.beyondsolo.job;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * /system job ... testing commands (permission level 2):
 * unlock, trial [now], jump &lt;1|2|3|boss|verdict&gt; [subclass], set &lt;subclass&gt;, reveal &lt;subclass&gt; (plays the
 * reveal on its own), odds [rolls], info, reset.
 */
public final class JobCommand {
    private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(Component.literal("Unknown subclass"));
    private static final SimpleCommandExceptionType NO_TRIAL = new SimpleCommandExceptionType(Component.literal("No trial running"));

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("job")
                        .then(Commands.literal("unlock").executes(ctx -> {
                            JobAPI.unlock(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("trial")
                                .executes(ctx -> trial(ctx.getSource().getPlayerOrException(), false))
                                .then(Commands.literal("now").executes(ctx -> trial(ctx.getSource().getPlayerOrException(), true))))
                        .then(Commands.literal("jump").then(Commands.argument("to", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"1", "2", "3", "boss", "verdict"}, b))
                                .executes(ctx -> jump(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "to"), null))
                                .then(subclassArg().executes(ctx -> jump(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "to"), subclass(StringArgumentType.getString(ctx, "subclass")))))))
                        .then(Commands.literal("set").then(subclassArg().executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Subclass s = subclass(StringArgumentType.getString(ctx, "subclass"));
                            JobAPI.changeJob(p, s);
                            ctx.getSource().sendSuccess(() -> Component.literal("Subclass: ").append(s.displayName()), false);
                            return 1;
                        })))
                        .then(Commands.literal("reveal").then(subclassArg().executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Subclass s = subclass(StringArgumentType.getString(ctx, "subclass"));
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new com.beyondsolo.network.JobRevealPayload(s.ordinal(), false));
                            return 1;
                        })))
                        .then(Commands.literal("odds")
                                .executes(ctx -> odds(ctx.getSource(), 100_000))
                                .then(Commands.argument("rolls", IntegerArgumentType.integer(1, 10_000_000))
                                        .executes(ctx -> odds(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "rolls")))))
                        .then(Commands.literal("info").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            JobData data = JobAPI.get(p);
                            ctx.getSource().sendSuccess(() -> Component.literal("subclass=" + data.subclass() + " unlocked=" + data.unlocked()
                                    + " inTrial=" + data.inTrial() + " attempts=" + data.attempts() + " phase=" + JobTrial.phase(p)), false);
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            JobAPI.reset(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> subclassArg() {
        return Commands.argument("subclass", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Subclass.values()).map(Subclass::key), b));
    }

    private static Subclass subclass(String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Subclass s = Subclass.byName(name);
        if (s == null) throw UNKNOWN.create();
        return s;
    }

    private static int trial(ServerPlayer p, boolean now) {
        JobAPI.get(p).setRetryAt(0);
        if (!JobAPI.get(p).unlocked()) JobAPI.unlock(p);
        return JobTrial.begin(p, now) ? 1 : 0;
    }

    private static int jump(ServerPlayer p, String to, Subclass result) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!JobTrial.jump(p, to, result)) throw NO_TRIAL.create();
        return 1;
    }

    /** Rolls many subclasses for the player's class and prints how often each came up. */
    private static int odds(CommandSourceStack src, int rolls) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Map<Subclass, Integer> count = new EnumMap<>(Subclass.class);
        RandomSource r = RandomSource.create();
        var base = com.beyondsolo.system.SystemAPI.get(p).playerClass();
        for (int i = 0; i < rolls; i++) count.merge(Subclass.roll(base, r, JobConfig.RARE_CHANCE.get()), 1, Integer::sum);
        StringBuilder b = new StringBuilder();
        for (var e : count.entrySet()) b.append(e.getKey().key()).append(String.format(" %.2f%%  ", 100.0 * e.getValue() / rolls));
        src.sendSuccess(() -> Component.literal(b.toString()), false);
        return 1;
    }

    private JobCommand() {}
}
