package com.beyondsolo.key;

import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateWiper;
import com.beyondsolo.system.PlayerRank;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /system key ... testing commands (permission level 2):
 * <pre>
 * key give &lt;rank&gt; [n]                 plain dungeon keys
 * key sealed &lt;rank&gt; &lt;boss&gt; [ascendant] a key sealed with a boss's sigil (ascendant: 1)
 * key kit                            a key of each rank and a sealed one
 * key turn                           the held key turns at once (no hold)
 * key runs                           the keyed-run records
 * key reset                          forgets the records
 * key expire                         unused keyed gates give up now (their keys come back)
 * key seal                           keyed gates seal now
 * key wipe                           closed caves are wiped back to rock at once, their slots freed
 * </pre>
 */
public final class KeyCommand {
    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("key")
                        .then(Commands.literal("give").then(rank()
                                .executes(ctx -> give(ctx, DungeonKeys.key(rankArg(ctx)), 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> give(ctx, DungeonKeys.key(rankArg(ctx)), IntegerArgumentType.getInteger(ctx, "n"))))))
                        .then(Commands.literal("sealed").then(rank().then(Commands.argument("boss", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(BossKind.values()).map(k -> k.id), b))
                                .executes(ctx -> sealed(ctx, false))
                                .then(Commands.argument("ascendant", IntegerArgumentType.integer(0, 1))
                                        .executes(ctx -> sealed(ctx, IntegerArgumentType.getInteger(ctx, "ascendant") == 1))))))
                        .then(Commands.literal("kit").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            for (PlayerRank r : PlayerRank.values()) give(p, DungeonKeys.key(r));
                            give(p, DungeonKeys.sealed(PlayerRank.D, BossKind.KNIGHT, false));
                            give(p, DungeonKeys.sealed(PlayerRank.C, BossKind.MAGUS, true));
                            return 1;
                        }))
                        .then(Commands.literal("turn").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ItemStack held = p.getItemInHand(InteractionHand.MAIN_HAND);
                            if (!DungeonKeys.isKey(held)) {
                                ctx.getSource().sendFailure(Component.literal("hold a dungeon key"));
                                return 0;
                            }
                            if (!DungeonKeys.begin(p, held) || !DungeonKeys.finish(p, held)) return 0;
                            held.consume(1, p);
                            return 1;
                        }))
                        .then(Commands.literal("runs").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            KeyRunData r = DungeonKeys.data(p);
                            StringBuilder out = new StringBuilder("keyed runs: turned " + r.totalTurned() + ", cleared " + r.totalCleared()
                                    + ", longest chain " + r.longestChain);
                            for (PlayerRank rank : PlayerRank.values()) {
                                int t = rank.tier();
                                if (r.turned[t] == 0 && r.cleared[t] == 0) continue;
                                out.append("\n  ").append(rank.name()).append(": ").append(r.turned[t]).append(" turned, ").append(r.cleared[t])
                                        .append(" cleared, best ").append(r.best[t] < 0 ? "-" : String.format(Locale.ROOT, "%.1fs", r.best[t] / 20.0));
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            DungeonKeys.data(p).reset();
                            DungeonKeys.sync(p);
                            return 1;
                        }))
                        .then(Commands.literal("expire").executes(ctx -> {
                            int n = 0;
                            var server = ctx.getSource().getServer();
                            for (GateInstance g : List.copyOf(GateData.get(server).all())) {
                                if (!g.isKeyed() || g.keyed().entered() || g.keyed().doorClosed || g.state() == GateInstance.State.CLOSED) continue;
                                ServerPlayer owner = server.getPlayerList().getPlayer(g.keyed().owner);
                                if (owner == null) continue;
                                KeyedRuns.giveUp(server, g, owner);
                                n++;
                            }
                            int count = n;
                            ctx.getSource().sendSuccess(() -> Component.literal(count + " keyed gate(s) gave up"), false);
                            return n;
                        }))
                        .then(Commands.literal("seal").executes(ctx -> {
                            int n = 0;
                            var server = ctx.getSource().getServer();
                            for (GateInstance g : List.copyOf(GateData.get(server).all())) {
                                if (!g.isKeyed() || g.keyed().doorClosed || !g.keyed().entered()) continue;
                                KeyedRuns.seal(server, g);
                                n++;
                            }
                            int count = n;
                            ctx.getSource().sendSuccess(() -> Component.literal(count + " keyed gate(s) sealed"), false);
                            return n;
                        }))
                        .then(Commands.literal("wipe").executes(ctx -> {
                            int n = 0;
                            var server = ctx.getSource().getServer();
                            for (GateInstance g : List.copyOf(GateData.get(server).all())) {
                                if (GateWiper.wipeNow(server, g)) n++;
                            }
                            int count = n;
                            ctx.getSource().sendSuccess(() -> Component.literal(count + " cave(s) wiped, free slots "
                                    + GateData.get(server).freeSlots()), false);
                            return n;
                        }))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> rank() {
        return Commands.argument("rank", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(PlayerRank.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), b));
    }

    private static PlayerRank rankArg(CommandContext<CommandSourceStack> ctx) {
        PlayerRank r = PlayerRank.byName(StringArgumentType.getString(ctx, "rank"));
        return r == null ? PlayerRank.E : r;
    }

    private static int sealed(CommandContext<CommandSourceStack> ctx, boolean ascendant) throws CommandSyntaxException {
        BossKind boss = BossKind.byId(StringArgumentType.getString(ctx, "boss"));
        if (boss == null) {
            ctx.getSource().sendFailure(Component.literal("no such boss"));
            return 0;
        }
        return give(ctx, DungeonKeys.sealed(rankArg(ctx), boss, ascendant), 1);
    }

    private static int give(CommandContext<CommandSourceStack> ctx, ItemStack stack, int n) throws CommandSyntaxException {
        stack.setCount(n);
        give(ctx.getSource().getPlayerOrException(), stack);
        return n;
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack) && !stack.isEmpty()) p.drop(stack, false);
    }

    private KeyCommand() {}
}
