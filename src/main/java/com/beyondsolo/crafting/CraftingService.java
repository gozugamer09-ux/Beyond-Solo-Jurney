package com.beyondsolo.crafting;

import com.beyondsolo.economy.Economy;
import com.beyondsolo.gear.GearRolls;
import com.beyondsolo.network.CraftResultPayload;
import com.beyondsolo.network.CraftStockPayload;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Crafting a System recipe: checks rank, ingredients (inventory first, then System storage) and gold, takes them,
 * and hands over the result. Plain cores are used before cores with an affinity, so the special ones are kept for
 * recipes that ask for them.
 */
public final class CraftingService {
    public static final int MAX_TIMES = 64;

    /** How many of a need the player has, inventory and storage together. */
    public static int have(Player player, SystemRecipes.Need need) {
        return count(player.getInventory().items, need) + count(Economy.data(player).storage(), need);
    }

    public static int count(List<ItemStack> stacks, SystemRecipes.Need need) {
        int n = 0;
        for (ItemStack s : stacks) if (!s.isEmpty() && need.match().test(s)) n += s.getCount();
        return n;
    }

    /** How many times the player could make it with what they carry and store (gold aside). */
    public static int canMake(List<ItemStack> inventory, List<ItemStack> storage, SystemRecipes.Recipe recipe) {
        int max = MAX_TIMES;
        for (SystemRecipes.Need need : recipe.needs()) max = Math.min(max, (count(inventory, need) + count(storage, need)) / need.count());
        return max;
    }

    /** Lang key for why it can't be made {@code times} times right now, or null if it can. */
    public static String refusal(ServerPlayer player, SystemRecipes.Recipe recipe, int times) {
        if (!SystemAPI.hasSystem(player)) return "craft.beyondsolo.refuse.system";
        if (SystemAPI.get(player).rank().tier() < recipe.minRank().tier()) return "craft.beyondsolo.refuse.rank";
        if (canMake(player.getInventory().items, Economy.data(player).storage(), recipe) < times) return "craft.beyondsolo.refuse.materials";
        if (Economy.gold(player) < recipe.gold() * times) return "craft.beyondsolo.refuse.gold";
        return null;
    }

    /** Makes a recipe up to {@code times} times. Returns how many were made (0 if refused). */
    public static int craft(ServerPlayer player, String id, int times) {
        SystemRecipes.Recipe recipe = SystemRecipes.get(id);
        times = Math.max(1, Math.min(MAX_TIMES, times));
        if (recipe == null) {
            reply(player, id, 0, "craft.beyondsolo.refuse.unknown");
            return 0;
        }
        String why = refusal(player, recipe, times);
        if (why != null) {
            reply(player, id, 0, why);
            return 0;
        }
        for (SystemRecipes.Need need : recipe.needs()) take(player, need, need.count() * times);
        if (recipe.gold() > 0) Economy.spend(player, recipe.gold() * times, Economy.Reason.CRAFT);
        for (int i = 0; i < times; i++) {
            ItemStack out = recipe.output().get();
            if (recipe.gear()) out = GearRolls.roll(out, player.getRandom());
            give(player, out);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9f, 1.2f);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.5f, 1.4f);
        reply(player, id, times, "");
        sendStock(player);
        return times;
    }

    private static void give(ServerPlayer player, ItemStack out) {
        // stackables can go up to the max; gear one at a time
        while (!out.isEmpty()) {
            ItemStack part = out.split(out.getMaxStackSize());
            if (!player.getInventory().add(part) && !part.isEmpty()) player.drop(part, false);
        }
    }

    /** Takes {@code amount} matching items: inventory before storage, plain stacks before ones with an affinity. */
    static void take(Player player, SystemRecipes.Need need, int amount) {
        List<List<ItemStack>> sources = List.of(player.getInventory().items, Economy.data(player).storage());
        for (int pass = 0; pass < 2 && amount > 0; pass++) {
            for (List<ItemStack> list : sources) {
                for (ItemStack s : new ArrayList<>(list)) {
                    if (amount <= 0) break;
                    if (s.isEmpty() || !need.match().test(s)) continue;
                    boolean special = MonsterCoreItem.affinity(s) != com.beyondsolo.enemy.Affinity.NONE;
                    if (special != (pass == 1)) continue;
                    int n = Math.min(amount, s.getCount());
                    s.shrink(n);
                    amount -= n;
                }
            }
        }
    }

    public static void sendStock(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : Economy.data(player).storage()) if (!s.isEmpty()) out.add(s.copy());
        PacketDistributor.sendToPlayer(player, new CraftStockPayload(out));
    }

    private static void reply(ServerPlayer player, String id, int made, String reason) {
        PacketDistributor.sendToPlayer(player, new CraftResultPayload(id, made, reason));
    }

    private CraftingService() {}
}
