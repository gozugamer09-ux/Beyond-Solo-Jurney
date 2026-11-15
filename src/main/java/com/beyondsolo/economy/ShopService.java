package com.beyondsolo.economy;

import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Buying from and selling to the System shop. */
public final class ShopService {
    public enum Result { OK, NO_SYSTEM, UNKNOWN, RANK, LIMIT, GOLD, MAXED, NOTHING }

    /** The shop's day (in-game days since the world began). */
    public static long day(Player player) {
        return player.level().getDayTime() / 24000L;
    }

    /** What one more lot of this entry would cost this player right now. */
    public static long price(ServerPlayer player, ShopCatalog.Entry e) {
        return ShopCatalog.price(e, day(player), Economy.data(player).expansions);
    }

    public static Result buy(ServerPlayer player, String id, int lots) {
        if (!SystemAPI.hasSystem(player)) return Result.NO_SYSTEM;
        ShopCatalog.Entry e = ShopCatalog.get(id);
        if (e == null) return Result.UNKNOWN;
        lots = Math.max(1, Math.min(lots, e.stackable() ? 64 : 1));
        if (SystemAPI.get(player).rank().tier() < e.minRank().tier()) return refuse(player, Result.RANK);
        HunterData d = Economy.data(player);
        long day = day(player);
        if (e.dailyLimit() > 0) lots = Math.min(lots, e.dailyLimit() - d.bought(id, day));
        if (lots <= 0) return refuse(player, Result.LIMIT);
        if (e.special() == ShopCatalog.Special.EXPANSION && d.expansions >= SystemStorage.MAX_EXPANSIONS) return refuse(player, Result.MAXED);
        long cost = price(player, e) * lots;
        if (!Economy.spend(player, cost, Economy.Reason.PURCHASE)) return refuse(player, Result.GOLD);

        switch (e.special()) {
            case EXPANSION -> {
                d.expansions++;
                Notifications.send(player, SystemNotification.of(SystemNotification.Kind.REWARD,
                        Component.translatable("shop.beyondsolo.expanded"),
                        Component.translatable("shop.beyondsolo.expanded.body", SystemStorage.capacity(player))));
            }
            case LICENSE -> {
                ItemStack card = new ItemStack(EconomyModule.HUNTER_LICENSE.get());
                HunterLicenseItem.refresh(player, card);
                give(player, card);
            }
            case NONE -> {
                for (int i = 0; i < lots; i++) give(player, e.display());
            }
        }
        if (e.dailyLimit() > 0) d.addBought(id, day, lots);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.6f, 1.6f);
        Economy.sync(player);
        return Result.OK;
    }

    /** Sells one inventory slot, or (all = true) every stack of the same item. */
    public static Result sell(ServerPlayer player, int slot, boolean all) {
        if (!SystemAPI.hasSystem(player)) return Result.NO_SYSTEM;
        var inv = player.getInventory();
        if (slot < 0 || slot >= inv.items.size()) return Result.UNKNOWN;
        ItemStack target = inv.items.get(slot);
        if (SellPrices.each(target) <= 0) return Result.NOTHING;
        long total = 0;
        if (all) {
            ItemStack like = target.copy();
            for (int i = 0; i < inv.items.size(); i++) {
                ItemStack s = inv.items.get(i);
                if (!s.isEmpty() && ItemStack.isSameItem(s, like) && SellPrices.each(s) > 0) {
                    total += SellPrices.of(s);
                    inv.items.set(i, ItemStack.EMPTY);
                }
            }
        } else {
            total = SellPrices.of(target);
            inv.items.set(slot, ItemStack.EMPTY);
        }
        if (total <= 0) return Result.NOTHING;
        Economy.add(player, total, Economy.Reason.SALE);
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.6f, 1.8f);
        player.containerMenu.broadcastChanges();
        return Result.OK;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
    }

    private static Result refuse(ServerPlayer player, Result why) {
        player.displayClientMessage(Component.translatable("shop.beyondsolo.refused." + why.name().toLowerCase()), true);
        return why;
    }

    private ShopService() {}
}
