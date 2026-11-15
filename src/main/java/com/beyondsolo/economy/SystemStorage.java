package com.beyondsolo.economy;

import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The System storage: extra space only its hunter can reach, from anywhere, through the System window. It grows
 * with rank (27 slots at E, 9 more per rank) and with expansions bought in the shop (9 each, up to
 * {@link #MAX_EXPANSIONS}), and it is kept through death.
 */
public final class SystemStorage {
    public static final int BASE = 27;
    public static final int PER_RANK = 9;
    public static final int PER_EXPANSION = 9;
    public static final int MAX_EXPANSIONS = 4;

    /** How many slots this player can use. */
    public static int capacity(Player player) {
        return capacity(SystemAPI.get(player).rank().tier(), Economy.data(player).expansions);
    }

    public static int capacity(int rankTier, int expansions) {
        return Math.min(HunterData.MAX_SLOTS, BASE + PER_RANK * rankTier + PER_EXPANSION * Math.min(expansions, MAX_EXPANSIONS));
    }

    /** Opens the storage window. Only for players who have the System. */
    public static boolean open(ServerPlayer player) {
        if (!SystemAPI.hasSystem(player)) return false;
        Economy.sync(player);
        HunterData data = Economy.data(player);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new StorageMenu(id, inv, data, capacity(player)),
                Component.translatable("screen.beyondsolo.storage")));
        return true;
    }

    /** Moves items from the player's main inventory (not the hotbar) onto matching stacks already in storage. */
    public static int depositMatching(Player player, NonNullList<ItemStack> storage, int capacity) {
        int moved = 0;
        var inv = player.getInventory();
        for (int i = 9; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !contains(storage, capacity, stack)) continue;
            int before = stack.getCount();
            insert(storage, capacity, stack);
            moved += before - stack.getCount();
            inv.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        return moved;
    }

    private static boolean contains(NonNullList<ItemStack> storage, int capacity, ItemStack stack) {
        for (int i = 0; i < capacity; i++) if (ItemStack.isSameItemSameComponents(storage.get(i), stack)) return true;
        return false;
    }

    /** Puts as much of the stack as fits into storage: onto matching stacks first, then empty slots. */
    public static void insert(NonNullList<ItemStack> storage, int capacity, ItemStack stack) {
        for (int i = 0; i < capacity && !stack.isEmpty(); i++) {
            ItemStack s = storage.get(i);
            if (!ItemStack.isSameItemSameComponents(s, stack)) continue;
            int room = s.getMaxStackSize() - s.getCount();
            int n = Math.min(room, stack.getCount());
            if (n <= 0) continue;
            s.grow(n);
            stack.shrink(n);
        }
        for (int i = 0; i < capacity && !stack.isEmpty(); i++) {
            if (!storage.get(i).isEmpty()) continue;
            storage.set(i, stack.copy());
            stack.setCount(0);
        }
    }

    /** Merges stacks and sorts the usable slots: gear by grade first, then by item name. */
    public static void sort(NonNullList<ItemStack> storage, int capacity) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            ItemStack s = storage.get(i);
            if (!s.isEmpty()) items.add(s.copy());
            storage.set(i, ItemStack.EMPTY);
        }
        items.sort(Comparator.<ItemStack>comparingInt(s -> {
                    var grade = s.get(com.beyondsolo.registry.ModComponents.GRADE.get());
                    return grade == null ? 99 : -grade.ordinal();
                })
                .thenComparing(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString())
                .thenComparingInt(s -> -s.getCount()));
        for (ItemStack s : items) insert(storage, capacity, s);
    }

    private SystemStorage() {}
}
