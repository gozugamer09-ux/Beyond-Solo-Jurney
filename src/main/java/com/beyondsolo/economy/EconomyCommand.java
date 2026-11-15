package com.beyondsolo.economy;

import com.beyondsolo.system.PlayerRank;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * /system gold|storage|shop|license ... testing commands (permission level 2):
 * <pre>
 * gold add|set &lt;n&gt;, gold info
 * storage open|expand [n]|fill|clear
 * shop buy &lt;entry&gt; [n]|sell &lt;slot&gt;|reset
 * license give|issue|clear &lt;rank&gt; [n]|reset
 * </pre>
 */
public final class EconomyCommand {
    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("gold")
                        .then(Commands.literal("info").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            HunterData h = Economy.data(p);
                            ctx.getSource().sendSuccess(() -> Component.literal("Gold " + h.gold() + " (earned " + h.earned() + "), storage "
                                    + SystemStorage.capacity(p) + ", expansions " + h.expansions() + ", clears " + h.totalClears()), false);
                            return 1;
                        }))
                        .then(Commands.literal("add").then(Commands.argument("n", LongArgumentType.longArg()).executes(ctx -> {
                            Economy.add(ctx.getSource().getPlayerOrException(), LongArgumentType.getLong(ctx, "n"), Economy.Reason.COMMAND);
                            return 1;
                        })))
                        .then(Commands.literal("set").then(Commands.argument("n", LongArgumentType.longArg(0)).executes(ctx -> {
                            Economy.set(ctx.getSource().getPlayerOrException(), LongArgumentType.getLong(ctx, "n"));
                            return 1;
                        }))))
                .then(Commands.literal("storage")
                        .then(Commands.literal("open").executes(ctx -> SystemStorage.open(ctx.getSource().getPlayerOrException()) ? 1 : 0))
                        .then(Commands.literal("expand")
                                .executes(ctx -> expand(ctx.getSource().getPlayerOrException(), 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(-8, 8))
                                        .executes(ctx -> expand(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "n")))))
                        .then(Commands.literal("fill").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            HunterData h = Economy.data(p);
                            var r = p.getRandom();
                            var all = ShopCatalog.entries().values().stream().filter(e -> e.special() == ShopCatalog.Special.NONE).toList();
                            int cap = SystemStorage.capacity(p);
                            for (int i = 0; i < cap; i++) {
                                if (r.nextFloat() < 0.45f) continue;
                                h.storage.set(i, all.get(r.nextInt(all.size())).display());
                            }
                            return 1;
                        }))
                        .then(Commands.literal("clear").executes(ctx -> {
                            Economy.data(ctx.getSource().getPlayerOrException()).storage.replaceAll(s -> net.minecraft.world.item.ItemStack.EMPTY);
                            return 1;
                        })))
                .then(Commands.literal("shop")
                        .then(Commands.literal("buy").then(Commands.argument("entry", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ShopCatalog.entries().keySet(), b))
                                .executes(ctx -> buy(ctx.getSource(), StringArgumentType.getString(ctx, "entry"), 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> buy(ctx.getSource(), StringArgumentType.getString(ctx, "entry"), IntegerArgumentType.getInteger(ctx, "n"))))))
                        .then(Commands.literal("sell").then(Commands.argument("slot", IntegerArgumentType.integer(0, 35)).executes(ctx -> {
                            var r = ShopService.sell(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot"), false);
                            ctx.getSource().sendSuccess(() -> Component.literal("Sell: " + r), false);
                            return 1;
                        })))
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Economy.data(p).purchases.clear();
                            Economy.sync(p);
                            return 1;
                        })))
                .then(Commands.literal("license")
                        .then(Commands.literal("give").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            var card = new net.minecraft.world.item.ItemStack(EconomyModule.HUNTER_LICENSE.get());
                            HunterLicenseItem.refresh(p, card);
                            p.getInventory().add(card);
                            return 1;
                        }))
                        .then(Commands.literal("issue").executes(ctx -> {
                            EconomyHooks.issueLicense(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("clear").then(Commands.argument("rank", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"e", "d", "c", "b", "a", "s"}, b))
                                .executes(ctx -> clear(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "rank"), 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 999))
                                        .executes(ctx -> clear(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "rank"), IntegerArgumentType.getInteger(ctx, "n"))))))
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Economy.data(p).reset();
                            Economy.sync(p);
                            return 1;
                        }))));
    }

    private static int expand(ServerPlayer p, int n) {
        HunterData h = Economy.data(p);
        h.expansions = Math.max(0, Math.min(SystemStorage.MAX_EXPANSIONS, h.expansions + n));
        Economy.sync(p);
        return 1;
    }

    private static int buy(CommandSourceStack src, String entry, int n) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var r = ShopService.buy(src.getPlayerOrException(), entry, n);
        src.sendSuccess(() -> Component.literal("Buy " + entry + ": " + r), false);
        return r == ShopService.Result.OK ? 1 : 0;
    }

    private static int clear(ServerPlayer p, String rank, int n) {
        PlayerRank r = PlayerRank.valueOf(rank.toUpperCase(Locale.ROOT));
        Economy.data(p).clears[r.ordinal()] += n;
        Economy.sync(p);
        return 1;
    }

    private EconomyCommand() {}
}
