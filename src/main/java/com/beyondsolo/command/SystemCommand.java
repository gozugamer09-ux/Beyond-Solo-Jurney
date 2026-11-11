package com.beyondsolo.command;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.Exhaustion;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/** /system ... admin and testing commands (permission level 2). They act on the player running them. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class SystemCommand {
    private static final SimpleCommandExceptionType BAD_VALUE =
            new SimpleCommandExceptionType(Component.translatable("command.beyondsolo.bad_value"));

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("system")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("awaken")
                        .executes(ctx -> awaken(ctx, PlayerRank.roll(ctx.getSource().getPlayerOrException().getRandom()),
                                PlayerClass.roll(ctx.getSource().getPlayerOrException().getRandom())))
                        .then(Commands.argument("rank", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(PlayerRank.values()), b))
                                .then(Commands.argument("class", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(names(PlayerClass.values()), b))
                                        .executes(ctx -> awaken(ctx, rank(ctx), playerClass(ctx))))))
                .then(Commands.literal("reset").executes(ctx -> {
                    SystemAPI.reset(ctx.getSource().getPlayerOrException());
                    return ok(ctx, "reset");
                }))
                .then(Commands.literal("xp").then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
                    SystemAPI.grantXp(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "amount"), SystemAPI.XP_SOURCE_COMMAND);
                    return ok(ctx, "xp");
                })))
                .then(Commands.literal("points").then(Commands.argument("amount", IntegerArgumentType.integer()).executes(ctx -> {
                    SystemAPI.addStatPoints(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "amount"));
                    return ok(ctx, "points");
                })))
                .then(Commands.literal("rank").then(Commands.argument("rank", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(names(PlayerRank.values()), b)).executes(ctx -> {
                            SystemAPI.setRank(ctx.getSource().getPlayerOrException(), rank(ctx));
                            return ok(ctx, "rank");
                        })))
                .then(Commands.literal("stat").then(Commands.argument("stat", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(names(Stat.values()), b))
                        .then(Commands.argument("value", IntegerArgumentType.integer(Stat.BASE)).executes(ctx -> {
                            Stat stat = Arrays.stream(Stat.values()).filter(s -> s.name().equalsIgnoreCase(StringArgumentType.getString(ctx, "stat")))
                                    .findFirst().orElseThrow(BAD_VALUE::create);
                            SystemAPI.setStat(ctx.getSource().getPlayerOrException(), stat, IntegerArgumentType.getInteger(ctx, "value"));
                            return ok(ctx, "stat");
                        }))))
                .then(Commands.literal("exhaustion").then(Commands.argument("percent", FloatArgumentType.floatArg(0, 100)).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    float target = FloatArgumentType.getFloat(ctx, "percent");
                    Exhaustion.recover(player, Math.min(target, 99.9f));
                    if (target >= 100) Exhaustion.collapse(player);
                    return ok(ctx, "exhaustion");
                })))
                .then(Commands.literal("notify").then(Commands.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
                    Notifications.send(ctx.getSource().getPlayerOrException(), SystemNotification.of(SystemNotification.Kind.QUEST,
                            Component.literal("Test"), Component.literal(StringArgumentType.getString(ctx, "text"))));
                    return 1;
                }))));
    }

    private static int awaken(CommandContext<CommandSourceStack> ctx, PlayerRank rank, PlayerClass playerClass) throws CommandSyntaxException {
        SystemAPI.awaken(ctx.getSource().getPlayerOrException(), rank, playerClass);
        return ok(ctx, "awaken");
    }

    private static PlayerRank rank(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerRank r = PlayerRank.byName(StringArgumentType.getString(ctx, "rank"));
        if (r == null) throw BAD_VALUE.create();
        return r;
    }

    private static PlayerClass playerClass(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        PlayerClass c = PlayerClass.byName(StringArgumentType.getString(ctx, "class"));
        if (c == null) throw BAD_VALUE.create();
        return c;
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(v -> v.name().toLowerCase()).toArray(String[]::new);
    }

    private static int ok(CommandContext<CommandSourceStack> ctx, String what) {
        ctx.getSource().sendSuccess(() -> Component.translatable("command.beyondsolo." + what), false);
        return 1;
    }

    private SystemCommand() {}
}
