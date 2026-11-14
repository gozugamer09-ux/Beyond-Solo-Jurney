package com.beyondsolo.command;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.skill.SkillTomeItem;
import com.beyondsolo.skill.Skills;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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

/**
 * /system skill ... testing commands (permission level 2): list, learn &lt;skill&gt; [level], levelup &lt;skill&gt; [n],
 * forget &lt;skill&gt;, cast &lt;skill&gt;, slot &lt;n&gt; &lt;skill&gt;, cooldowns (clears them), unlocked (grants what level
 * and rank have unlocked), advanced (the job change grant), tome [skill], reset.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class SkillCommand {
    private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(Component.translatable("command.beyondsolo.skill.unknown"));

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("skill")
                        .then(Commands.literal("list").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            StringBuilder b = new StringBuilder();
                            for (Skill s : Skills.known(SkillAPI.get(p))) b.append(s.path()).append(" Lv.").append(SkillAPI.level(p, s)).append("  ");
                            ctx.getSource().sendSuccess(() -> Component.literal(b.isEmpty() ? "-" : b.toString()), false);
                            return 1;
                        }))
                        .then(Commands.literal("learn").then(skillArg()
                                .executes(ctx -> learn(ctx, 1))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, Skill.MAX_LEVEL))
                                        .executes(ctx -> learn(ctx, IntegerArgumentType.getInteger(ctx, "level"))))))
                        .then(Commands.literal("levelup").then(skillArg()
                                .executes(ctx -> levelUp(ctx, 1))
                                .then(Commands.argument("levels", IntegerArgumentType.integer(1, Skill.MAX_LEVEL))
                                        .executes(ctx -> levelUp(ctx, IntegerArgumentType.getInteger(ctx, "levels"))))))
                        .then(Commands.literal("forget").then(skillArg().executes(ctx -> {
                            SkillAPI.forget(ctx.getSource().getPlayerOrException(), skill(ctx));
                            return ok(ctx);
                        })))
                        .then(Commands.literal("cast").then(skillArg().executes(ctx ->
                                SkillAPI.cast(ctx.getSource().getPlayerOrException(), skill(ctx)) ? 1 : 0)))
                        .then(Commands.literal("slot").then(Commands.argument("slot", IntegerArgumentType.integer(1, 4)).then(skillArg().executes(ctx -> {
                            SkillAPI.assign(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot") - 1, skill(ctx).path());
                            return ok(ctx);
                        }))))
                        .then(Commands.literal("cooldowns").executes(ctx -> {
                            SkillAPI.resetCooldowns(ctx.getSource().getPlayerOrException());
                            return ok(ctx);
                        }))
                        .then(Commands.literal("unlocked").executes(ctx -> SkillAPI.grantUnlocked(ctx.getSource().getPlayerOrException()).size()))
                        .then(Commands.literal("advanced").executes(ctx -> SkillAPI.grantAdvanced(ctx.getSource().getPlayerOrException()).size()))
                        .then(Commands.literal("tome")
                                .executes(ctx -> {
                                    ctx.getSource().getPlayerOrException().getInventory().add(SkillTomeItem.unread());
                                    return ok(ctx);
                                })
                                .then(skillArg().executes(ctx -> {
                                    ctx.getSource().getPlayerOrException().getInventory().add(SkillTomeItem.of(skill(ctx)));
                                    return ok(ctx);
                                })))
                        .then(Commands.literal("reset").executes(ctx -> {
                            SkillAPI.reset(ctx.getSource().getPlayerOrException());
                            return ok(ctx);
                        }))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> skillArg() {
        return Commands.argument("skill", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Skills.all().stream().map(Skill::path), b));
    }

    private static Skill skill(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Skill s = Skills.get(StringArgumentType.getString(ctx, "skill"));
        if (s == null) throw UNKNOWN.create();
        return s;
    }

    private static int learn(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Skill s = skill(ctx);
        if (!SkillAPI.learn(p, s, level, SkillEvent.Source.COMMAND)) SkillAPI.addLevels(p, s, level - SkillAPI.level(p, s));
        return ok(ctx);
    }

    private static int levelUp(CommandContext<CommandSourceStack> ctx, int levels) throws CommandSyntaxException {
        return SkillAPI.addLevels(ctx.getSource().getPlayerOrException(), skill(ctx), levels);
    }

    private static int ok(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable("command.beyondsolo.gate.done"), false);
        return 1;
    }

    private SkillCommand() {}
}
