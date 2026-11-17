package com.beyondsolo.rune;

import com.beyondsolo.gate.BossKind;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.PlayerRank;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Locale;

/**
 * /system rune ... testing commands (permission level 2):
 * <pre>
 * rune give &lt;skill&gt; [n]           a rune stone inscribed with a skill
 * rune sealed &lt;grade&gt; [n]          sealed rune stones
 * rune engraving &lt;kind&gt; [n]        engraving runes (haste, thrift, might)
 * rune kit                         every rune art, a sealed stone of each grade and the engraving runes
 * rune absorb                      the held rune stone shatters at once (no hold)
 * rune engrave &lt;skill&gt; &lt;kind&gt;     carve an engraving without a rune
 * rune clear                       takes every engraving off
 * rune bossloot &lt;boss&gt; &lt;rank&gt;     what runes a boss adds, rolled once (ascended)
 * </pre>
 */
public final class RuneCommand {
    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("rune")
                        .then(Commands.literal("give").then(skill()
                                .executes(ctx -> giveSkill(ctx, 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 16)).executes(ctx -> giveSkill(ctx, IntegerArgumentType.getInteger(ctx, "n"))))))
                        .then(Commands.literal("sealed").then(rank()
                                .executes(ctx -> giveSealed(ctx, 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 16)).executes(ctx -> giveSealed(ctx, IntegerArgumentType.getInteger(ctx, "n"))))))
                        .then(Commands.literal("engraving").then(kind()
                                .executes(ctx -> giveEngraving(ctx, 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 16)).executes(ctx -> giveEngraving(ctx, IntegerArgumentType.getInteger(ctx, "n"))))))
                        .then(Commands.literal("kit").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            for (Skill s : Skills.runeArts()) give(p, Runes.of(s));
                            for (PlayerRank r : PlayerRank.values()) give(p, Runes.sealed(r));
                            for (Engraving e : Engraving.values()) give(p, new ItemStack(e.item(), 2));
                            return 1;
                        }))
                        .then(Commands.literal("absorb").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
                            if (!Runes.isRune(held)) {
                                ctx.getSource().sendFailure(Component.literal("hold a rune stone"));
                                return 0;
                            }
                            if (!Runes.absorb(p, held)) return 0;
                            held.shrink(1);
                            return 1;
                        }))
                        .then(Commands.literal("engrave").then(skill().then(kind().executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Skill s = Skills.get(StringArgumentType.getString(ctx, "skill"));
                            Engraving e = Engraving.byId(StringArgumentType.getString(ctx, "kind"));
                            if (s == null || e == null) {
                                ctx.getSource().sendFailure(Component.literal("no such skill or engraving"));
                                return 0;
                            }
                            ItemStack had = p.getItemInHand(InteractionHand.MAIN_HAND);
                            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(e.item()));
                            boolean ok = Runes.engrave(p, 0, s.path());
                            p.setItemInHand(InteractionHand.MAIN_HAND, had);
                            return ok ? 1 : 0;
                        }))))
                        .then(Commands.literal("clear").executes(ctx -> {
                            Runes.reset(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("bossloot").then(Commands.argument("boss", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(BossKind.values()).map(k -> k.name().toLowerCase(Locale.ROOT)), b))
                                .then(rank().executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    BossKind boss = BossKind.valueOf(StringArgumentType.getString(ctx, "boss").toUpperCase(Locale.ROOT));
                                    for (ItemStack s : RuneDrops.bossRunes(boss, rankArg(ctx), true, p.getRandom())) give(p, s);
                                    return 1;
                                }))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> skill() {
        return Commands.argument("skill", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Skills.all().stream().map(Skill::path), b));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> rank() {
        return Commands.argument("rank", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(PlayerRank.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), b));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> kind() {
        return Commands.argument("kind", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Engraving.values()).map(Engraving::id), b));
    }

    private static PlayerRank rankArg(CommandContext<CommandSourceStack> ctx) {
        PlayerRank r = PlayerRank.byName(StringArgumentType.getString(ctx, "rank"));
        return r == null ? PlayerRank.E : r;
    }

    private static int giveSkill(CommandContext<CommandSourceStack> ctx, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Skill s = Skills.get(StringArgumentType.getString(ctx, "skill"));
        if (s == null) {
            ctx.getSource().sendFailure(Component.literal("no such skill"));
            return 0;
        }
        ItemStack stack = Runes.of(s);
        stack.setCount(n);
        give(p, stack);
        return n;
    }

    private static int giveSealed(CommandContext<CommandSourceStack> ctx, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ItemStack stack = Runes.sealed(rankArg(ctx));
        stack.setCount(n);
        give(ctx.getSource().getPlayerOrException(), stack);
        return n;
    }

    private static int giveEngraving(CommandContext<CommandSourceStack> ctx, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Engraving e = Engraving.byId(StringArgumentType.getString(ctx, "kind"));
        if (e == null) {
            ctx.getSource().sendFailure(Component.literal("no such engraving"));
            return 0;
        }
        give(ctx.getSource().getPlayerOrException(), new ItemStack(e.item(), n));
        return n;
    }

    static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
    }

    private RuneCommand() {}
}
