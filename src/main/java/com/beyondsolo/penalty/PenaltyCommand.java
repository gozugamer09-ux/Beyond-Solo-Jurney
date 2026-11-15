package com.beyondsolo.penalty;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /system penalty ... testing commands (permission level 2):
 * sentence (as if the daily was failed: the warning follows when the player is free), now (straight in),
 * jump &lt;arrive|fight|time|survive|fail|surge|erupt|champion&gt; [n], cancel, status, reset.
 */
@EventBusSubscriber(modid = com.beyondsolo.BeyondSolo.MOD_ID)
public final class PenaltyCommand {
    private static final SimpleCommandExceptionType NOT_RUNNING = new SimpleCommandExceptionType(Component.literal("No penalty running (or not in that phase)"));
    private static final SimpleCommandExceptionType OFF = new SimpleCommandExceptionType(Component.literal("The Penalty Zone is off, or the player has no System"));

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("penalty")
                        .then(Commands.literal("sentence").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            if (!PenaltyZone.sentence(p, Component.translatable("quest.beyondsolo.daily"))) throw OFF.create();
                            return 1;
                        }))
                        .then(Commands.literal("now").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            PenaltyZone.data(p).sentence(Component.translatable("quest.beyondsolo.daily"));
                            return PenaltyZone.begin(p, true) ? 1 : 0;
                        }))
                        .then(Commands.literal("jump").then(Commands.argument("to", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"arrive", "fight", "time", "survive", "fail", "surge", "erupt", "champion"}, b))
                                .executes(ctx -> jump(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "to"), 0))
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, 3600))
                                        .executes(ctx -> jump(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "to"),
                                                IntegerArgumentType.getInteger(ctx, "n"))))))
                        .then(Commands.literal("cancel").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            PenaltyZone.cancel(p);
                            PenaltyZone.data(p).clearSentence();
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            PenaltyZone.cancel(p);
                            PenaltyZone.data(p).reset();
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            PenaltyData data = PenaltyZone.data(p);
                            PenaltyZone.Phase phase = PenaltyZone.phase(p);
                            int left = PenaltyZone.remaining(p);
                            ctx.getSource().sendSuccess(() -> Component.literal("Penalty: phase " + (phase == null ? "none" : phase.name().toLowerCase())
                                    + (left >= 0 ? ", " + left / 20 + "s left" : "") + ", pending " + data.pending() + ", in zone " + data.inZone()
                                    + ", served " + data.served() + ", failed " + data.failed()
                                    + ", foes " + PenaltyZone.foes(p).size()), false);
                            return 1;
                        }))));
    }

    private static int jump(ServerPlayer p, String to, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!PenaltyZone.jump(p, to, n)) throw NOT_RUNNING.create();
        return 1;
    }

    private PenaltyCommand() {}
}
