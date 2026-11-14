package com.beyondsolo.command;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.quest.QuestTemplates;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.quest.trigger.QuestTrigger;
import com.beyondsolo.quest.trigger.QuestTriggers;
import com.mojang.brigadier.CommandDispatcher;
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

import java.util.List;

/** /system quest ... testing commands for the quest engine (permission level 2). */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class QuestCommand {
    private static final SimpleCommandExceptionType UNKNOWN =
            new SimpleCommandExceptionType(Component.translatable("command.beyondsolo.quest.unknown"));
    private static final SimpleCommandExceptionType NONE =
            new SimpleCommandExceptionType(Component.translatable("command.beyondsolo.quest.none"));

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("quest")
                        .then(Commands.literal("list").executes(QuestCommand::list))
                        .then(Commands.literal("start").then(Commands.argument("template", StringArgumentType.greedyString())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(QuestTemplates.ids(), b))
                                .executes(ctx -> give(ctx, false))))
                        .then(Commands.literal("offer").then(Commands.argument("template", StringArgumentType.greedyString())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(QuestTemplates.ids(), b))
                                .executes(ctx -> give(ctx, true))))
                        .then(Commands.literal("trigger").then(Commands.argument("trigger", StringArgumentType.greedyString())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(QuestTriggers.all().stream().map(QuestTrigger::id), b))
                                .executes(QuestCommand::trigger)))
                        .then(Commands.literal("accept").executes(ctx -> {
                            if (!QuestEngine.acceptLatest(ctx.getSource().getPlayerOrException())) throw NONE.create();
                            return 1;
                        }))
                        .then(Commands.literal("progress").then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(QuestCommand::progress)))
                        .then(Commands.literal("complete").executes(QuestCommand::complete))
                        .then(Commands.literal("fail").executes(QuestCommand::fail))
                        .then(Commands.literal("reset").executes(ctx -> {
                            QuestEngine.reset(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        QuestData data = QuestEngine.data(player);
        for (Quest q : data.all()) {
            StringBuilder s = new StringBuilder(q.status() + " " + q.template() + " [" + q.category() + "]");
            for (QuestObjective o : q.objectives()) s.append("  ").append(o.type()).append(' ').append(o.target()).append(' ').append(o.progress()).append('/').append(o.required());
            ctx.getSource().sendSuccess(() -> Component.literal(s.toString()), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("finished " + data.completedCount() + ", failed " + data.failedCount()), false);
        return data.all().size();
    }

    private static int give(CommandContext<CommandSourceStack> ctx, boolean offer) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "template");
        if (!id.contains(":")) id = BeyondSolo.MOD_ID + ":" + id;
        Quest q = QuestTemplates.create(id, player, QuestTemplate.Context.at(player.blockPosition()));
        if (q == null) throw UNKNOWN.create();
        if (offer) QuestEngine.offer(player, q, QuestEngine.OFFER_TICKS);
        else QuestEngine.start(player, q);
        return 1;
    }

    private static int trigger(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        QuestTrigger t = QuestTriggers.byId(StringArgumentType.getString(ctx, "trigger"));
        if (t == null) throw UNKNOWN.create();
        if (!QuestTriggers.fire(player, QuestEngine.data(player), t, false)) throw NONE.create();
        return 1;
    }

    private static Quest first(ServerPlayer player) throws CommandSyntaxException {
        List<Quest> active = QuestEngine.data(player).active();
        if (active.isEmpty()) throw NONE.create();
        return active.get(0);
    }

    private static int progress(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Quest q = first(player);
        QuestTestAccess.addProgress(player, q, IntegerArgumentType.getInteger(ctx, "amount"));
        return 1;
    }

    private static int complete(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Quest q = first(player);
        QuestTestAccess.addProgress(player, q, Integer.MAX_VALUE / 2);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        QuestEngine.fail(player, first(player), QuestEvent.FailReason.TIME);
        return 1;
    }

    private QuestCommand() {}
}
