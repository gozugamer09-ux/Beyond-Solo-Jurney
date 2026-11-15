package com.beyondsolo.client.crafting;

import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.network.CraftActionPayload;
import com.beyondsolo.network.CraftResultPayload;
import com.beyondsolo.network.CraftStockPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Client side of crafting: sends requests, keeps the storage contents and the last answer for the animation. */
public final class CraftClient {
    private static @Nullable CraftResultPayload last;
    private static double lastAt = -10;
    private static double sentAt = -10;
    private static List<ItemStack> storage = List.of();

    public static void onResult(CraftResultPayload p) {
        last = p;
        lastAt = Anim.now();
    }

    public static void onStock(CraftStockPayload p) {
        storage = List.copyOf(p.storage());
    }

    public static void craft(String recipe, int times) {
        sentAt = Anim.now();
        PacketDistributor.sendToServer(new CraftActionPayload(recipe, times));
    }

    public static void requestStock() {
        PacketDistributor.sendToServer(new CraftActionPayload("", 0));
    }

    public static List<ItemStack> storage() {
        return storage;
    }

    public static @Nullable CraftResultPayload last() {
        return last;
    }

    public static double lastAt() {
        return lastAt;
    }

    public static double sentAt() {
        return sentAt;
    }

    private CraftClient() {}
}
