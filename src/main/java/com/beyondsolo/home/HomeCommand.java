package com.beyondsolo.home;

import com.beyondsolo.BeyondSolo;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /home}, for everyone: the bare command goes home (standing still a few seconds, like the HOME page's button),
 * {@code leave} goes back out into the world, {@code visit <player>} goes to a party member's home, {@code buy} buys the
 * next part of the home, {@code stay} stops going, and {@code status} says how the home stands. Giving homes away is an
 * operator's: {@code /solo home}.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class HomeCommand {
    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("home")
                .executes(ctx -> run(ctx, Homes.goHome(me(ctx))))
                .then(Commands.literal("leave").executes(ctx -> run(ctx, Homes.leave(me(ctx)))))
                .then(Commands.literal("visit").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> run(ctx, Homes.visit(me(ctx), EntityArgument.getPlayer(ctx, "player").getUUID())))))
                .then(Commands.literal("buy").executes(ctx -> run(ctx, Homes.buy(me(ctx)))))
                .then(Commands.literal("stay").executes(ctx -> {
                    ServerPlayer p = me(ctx);
                    if (!Homes.channeling(p)) return run(ctx, new Homes.Outcome(false, Component.translatable(Homes.KEY + "outcome.not_going")));
                    Homes.cancel(p, Component.translatable(Homes.KEY + "channel.stopped"));
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    for (Component line : HomeAdmin.info(me(ctx))) ctx.getSource().sendSystemMessage(tag().append(line));
                    return 1;
                })));
    }

    private static ServerPlayer me(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    /** "[Home] " in the night's blue, before every line. */
    static MutableComponent tag() {
        return Component.literal("[").append(Component.translatable(Homes.KEY + "tag")).append("] ").withStyle(ChatFormatting.AQUA);
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Homes.Outcome out) {
        // always said (not command feedback, which a server can turn off)
        if (out.ok()) ctx.getSource().sendSystemMessage(tag().append(out.text().copy().withStyle(ChatFormatting.GRAY)));
        else ctx.getSource().sendFailure(out.text());
        return out.ok() ? 1 : 0;
    }

    private HomeCommand() {}
}
