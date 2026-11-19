package com.beyondsolo.command.solo;

import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.manapoison.ManaCrystals;
import com.beyondsolo.manapoison.ManaPoisonModule;
import com.beyondsolo.manapoison.ManaPoisoning;
import com.beyondsolo.reward.BoxCurse;
import com.beyondsolo.reward.BoxKind;
import com.beyondsolo.reward.BoxReward;
import com.beyondsolo.reward.BoxRolls;
import com.beyondsolo.reward.RewardBoxItem;
import com.beyondsolo.reward.RewardBoxes;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Random boxes and mana poisoning:
 * <pre>
 * /solo box give plain|blessed|cursed [rank] [count] [players]    boxes (the player's own rank if left out)
 * /solo box curse wardens|frailty|backlash|greed [rank] [players]  a cursed box opened on the spot, with that curse
 * /solo mana set|add &lt;amount&gt; [players]                          mana poisoning (100 bursts)
 * /solo mana clear [players]
 * /solo mana crystal                                             a purifying crystal where you stand
 * /solo mana crystals [player]                                   the crystals grown in the gate the player is in
 * /solo mana info [player]
 * </pre>
 */
final class SoloBoxes {
    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("box")
                .then(Commands.literal("give").then(Solo.word("kind", () -> Arrays.stream(BoxKind.values()).map(SoloBoxes::kindName))
                        .executes(ctx -> Solo.each(ctx, java.util.List.of(ctx.getSource().getPlayerOrException()), (c, p) -> give(c, p, null, 1)))
                        .then(Solo.rankArg()
                                .executes(ctx -> Solo.each(ctx, java.util.List.of(ctx.getSource().getPlayerOrException()), (c, p) -> give(c, p, Solo.rank(c), 1)))
                                .then(Solo.targets(Commands.argument("count", IntegerArgumentType.integer(1, 64)),
                                        (ctx, p) -> give(ctx, p, Solo.rank(ctx), IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(Commands.literal("curse").then(Solo.word("curse", () -> Arrays.stream(BoxCurse.values()).map(BoxCurse::id))
                        .executes(ctx -> Solo.each(ctx, java.util.List.of(ctx.getSource().getPlayerOrException()), (c, p) -> curse(c, p, null)))
                        .then(Solo.targets(Solo.rankArg(), (ctx, p) -> curse(ctx, p, Solo.rank(ctx)))))));
        root.then(Commands.literal("mana")
                .then(Commands.literal("set").then(Solo.targets(Commands.argument("amount", FloatArgumentType.floatArg(0, ManaPoisoning.MAX)), (ctx, p) -> {
                    ManaPoisoning.set(p, FloatArgumentType.getFloat(ctx, "amount"));
                    return level(p);
                })))
                .then(Commands.literal("add").then(Solo.targets(Commands.argument("amount", FloatArgumentType.floatArg(-ManaPoisoning.MAX, ManaPoisoning.MAX)), (ctx, p) -> {
                    ManaPoisoning.add(p, FloatArgumentType.getFloat(ctx, "amount"));
                    return level(p);
                })))
                .then(Solo.targets(Commands.literal("clear"), (ctx, p) -> {
                    ManaPoisoning.clear(p);
                    return level(p);
                }))
                .then(Commands.literal("crystal").executes(SoloBoxes::crystal))
                .then(Solo.target(Commands.literal("crystals"), (ctx, p) -> crystals(p)))
                .then(Solo.target(Commands.literal("info"), (ctx, p) -> info(p))));
    }

    static String kindName(BoxKind kind) {
        return kind == BoxKind.PLAIN ? "plain" : Solo.lower(kind.name());
    }

    private static BoxKind kind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = Solo.lower(StringArgumentType.getString(ctx, "kind"));
        for (BoxKind k : BoxKind.values()) if (kindName(k).equals(name) || k.id().equals(name)) return k;
        throw Solo.error("unknown_box", name);
    }

    private static Component give(CommandContext<CommandSourceStack> ctx, ServerPlayer p, PlayerRank rank, int count) throws CommandSyntaxException {
        BoxKind kind = kind(ctx);
        PlayerRank r = rank != null ? rank : SystemAPI.isAwakened(p) ? SystemAPI.get(p).rank() : PlayerRank.E;
        ItemStack stack = RewardBoxItem.of(kind, r, count);
        Component name = stack.getHoverName();
        if (!p.getInventory().add(stack) && !stack.isEmpty()) p.drop(stack, false);
        return Solo.t("box.given", p.getDisplayName(), count, name);
    }

    private static Component curse(CommandContext<CommandSourceStack> ctx, ServerPlayer p, PlayerRank rank) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        String name = Solo.lower(StringArgumentType.getString(ctx, "curse"));
        BoxCurse curse = null;
        for (BoxCurse c : BoxCurse.values()) if (c.id().equals(name)) curse = c;
        if (curse == null) {
            throw Solo.error("unknown_curse", name, String.join(", ", Arrays.stream(BoxCurse.values()).map(BoxCurse::id).toList()));
        }
        PlayerRank r = rank != null ? rank : SystemAPI.get(p).rank();
        BoxReward reward = BoxRolls.roll(BoxKind.CURSED, r, SystemAPI.get(p).playerClass(), p.getRandom());
        RewardBoxes.curse(p, r, reward, curse);
        return Solo.t("box.cursed", p.getDisplayName(), r.displayName(), curse.displayName());
    }

    private static Component level(ServerPlayer p) {
        float level = ManaPoisoning.level(p);
        return Solo.t("mana.level", p.getDisplayName(), String.format(Locale.ROOT, "%.1f", level),
                Solo.t("mana.stage." + ManaPoisoning.stage(level)));
    }

    private static Component info(ServerPlayer p) {
        GateInstance g = ManaPoisoning.denseGate(p);
        String rate = g == null ? "0" : String.format(Locale.ROOT, "%.2f", ManaPoisoning.rate(p, g));
        String shield = String.format(Locale.ROOT, "%.0f", ManaPoisoning.shield(p) * 100);
        BlockPos crystal = ManaPoisoning.nearestCrystal(p);
        return Solo.t("mana.info", level(p), rate, shield,
                crystal == null ? Solo.t("mana.no_crystal") : Component.literal(crystal.toShortString()));
    }

    /** The purifying crystals grown in the gate the player is in, each a click away (a teleport beside it to suggest). */
    private static Component crystals(ServerPlayer p) throws CommandSyntaxException {
        GateInstance g = SoloWorld.inside(p);
        List<BlockPos> grown = new ArrayList<>();
        for (BlockPos at : ManaCrystals.spots(p.serverLevel(), g)) {
            if (p.serverLevel().getBlockState(at).is(ManaPoisonModule.CRYSTAL.get())) grown.add(at);
        }
        if (grown.isEmpty()) return Solo.t("mana.crystals.none", g.rank().displayName());
        MutableComponent list = Component.empty();
        for (int i = 0; i < grown.size(); i++) {
            BlockPos at = grown.get(i);
            String tp = "/tp @s " + (at.getX() + 1) + " " + at.getY() + " " + at.getZ();
            if (i > 0) list.append(", ");
            list.append(Component.literal(at.toShortString()).withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tp))));
        }
        return Solo.t("mana.crystals", grown.size(), list);
    }

    private static int crystal(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        BlockPos at = p.blockPosition().relative(p.getDirection(), 2);
        p.serverLevel().setBlock(at, ManaPoisonModule.CRYSTAL.get().defaultBlockState(), Block.UPDATE_ALL);
        ctx.getSource().sendSuccess(() -> Solo.t("mana.crystal", at.toShortString()), true);
        return 1;
    }

    private SoloBoxes() {}
}
