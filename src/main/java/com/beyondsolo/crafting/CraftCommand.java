package com.beyondsolo.crafting;

import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.system.PlayerRank;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;

/**
 * /system craft ... testing commands (permission level 2):
 * <pre>
 * craft make &lt;recipe&gt; [n]          craft like the tab does
 * craft cores &lt;rank&gt; [n] [affinity]  monster cores
 * craft bosscore &lt;rank&gt; [n]
 * craft kit                          a bit of everything (cores, materials, stones, bottles)
 * craft bossloot &lt;rank&gt;              what a boss adds to the cache
 * craft drainmana                    empty the mana bar (to try mana stones and draughts)
 * </pre>
 */
public final class CraftCommand {
    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("craft")
                        .then(Commands.literal("make").then(Commands.argument("recipe", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(SystemRecipes.all().keySet(), b))
                                .executes(ctx -> make(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "recipe"), 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, CraftingService.MAX_TIMES))
                                        .executes(ctx -> make(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "recipe"),
                                                IntegerArgumentType.getInteger(ctx, "n"))))))
                        .then(Commands.literal("cores").then(rank()
                                .executes(ctx -> cores(ctx.getSource().getPlayerOrException(), rankArg(ctx), 4, "none", false))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> cores(ctx.getSource().getPlayerOrException(), rankArg(ctx), IntegerArgumentType.getInteger(ctx, "n"), "none", false))
                                        .then(Commands.argument("affinity", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Affinity.values()).map(a -> a.name().toLowerCase()), b))
                                                .executes(ctx -> cores(ctx.getSource().getPlayerOrException(), rankArg(ctx), IntegerArgumentType.getInteger(ctx, "n"),
                                                        StringArgumentType.getString(ctx, "affinity"), false))))))
                        .then(Commands.literal("bosscore").then(rank()
                                .executes(ctx -> cores(ctx.getSource().getPlayerOrException(), rankArg(ctx), 1, "none", true))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 16))
                                        .executes(ctx -> cores(ctx.getSource().getPlayerOrException(), rankArg(ctx), IntegerArgumentType.getInteger(ctx, "n"), "none", true)))))
                        .then(Commands.literal("bossloot").then(rank().executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            for (ItemStack s : CraftDrops.bossExtras(rankArg(ctx), false, p.getRandom())) give(p, s);
                            return 1;
                        })))
                        .then(Commands.literal("drainmana").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            com.beyondsolo.system.SystemAPI.consumeMana(p, com.beyondsolo.system.SystemAPI.get(p).mana());
                            return 1;
                        }))
                        .then(Commands.literal("kit").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            give(p, new ItemStack(Items.GLASS_BOTTLE, 16));
                            for (PlayerRank r : new PlayerRank[]{PlayerRank.E, PlayerRank.D, PlayerRank.C}) give(p, MonsterCoreItem.of(r, false, Affinity.NONE, 8));
                            give(p, MonsterCoreItem.of(PlayerRank.C, true, Affinity.NONE, 2));
                            give(p, MonsterCoreItem.of(PlayerRank.D, false, Affinity.STORM, 2));
                            give(p, MonsterCoreItem.of(PlayerRank.D, false, Affinity.FLAME, 2));
                            CraftingModule.MANA_STONES.forEach(m -> give(p, new ItemStack(m.get(), 6)));
                            CraftingModule.MATERIALS.values().forEach(m -> give(p, new ItemStack(m.get(), 6)));
                            return 1;
                        }))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> rank() {
        return Commands.argument("rank", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(PlayerRank.values()).map(r -> r.name().toLowerCase()), b));
    }

    private static PlayerRank rankArg(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        PlayerRank r = PlayerRank.byName(StringArgumentType.getString(ctx, "rank"));
        return r == null ? PlayerRank.E : r;
    }

    private static int make(ServerPlayer p, String id, int n) {
        int made = CraftingService.craft(p, id, n);
        p.sendSystemMessage(Component.literal("made " + made + " x " + id));
        return made;
    }

    private static int cores(ServerPlayer p, PlayerRank rank, int n, String affinity, boolean boss) {
        Affinity a = Affinity.NONE;
        for (Affinity x : Affinity.values()) if (x.name().equalsIgnoreCase(affinity)) a = x;
        give(p, MonsterCoreItem.of(rank, boss, a, n));
        return n;
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
    }

    private CraftCommand() {}
}
