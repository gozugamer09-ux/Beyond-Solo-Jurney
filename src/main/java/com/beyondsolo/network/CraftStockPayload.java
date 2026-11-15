package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Server -> client: the non-empty stacks in the player's System storage, which crafting can draw from too. */
public record CraftStockPayload(List<ItemStack> storage) implements CustomPacketPayload {
    public static final Type<CraftStockPayload> TYPE = new Type<>(BeyondSolo.id("craft_stock"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftStockPayload> STREAM_CODEC =
            ItemStack.LIST_STREAM_CODEC.map(CraftStockPayload::new, CraftStockPayload::storage);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
