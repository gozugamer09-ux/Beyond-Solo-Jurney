package com.beyondsolo.client;

import com.beyondsolo.registry.ModAttachments;
import com.beyondsolo.system.SystemData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;

/** The local player's System state as last sent by the server. */
public final class ClientSystemData {
    private static SystemData data = new SystemData();
    private static CompoundTag lastTag = new CompoundTag();
    private static LocalPlayer appliedTo;

    public static SystemData get() {
        // The local player entity is recreated on respawn / dimension change; copy the data across
        // so SystemAPI.get(player) also works on the client.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player != appliedTo) {
            player.getData(ModAttachments.SYSTEM).deserializeNBT(player.registryAccess(), lastTag);
            appliedTo = player;
        }
        return data;
    }

    static void update(CompoundTag tag) {
        lastTag = tag;
        SystemData fresh = new SystemData();
        fresh.deserializeNBT(null, tag);
        data = fresh;
        appliedTo = null;
        get();
    }

    static void clear() {
        data = new SystemData();
        lastTag = new CompoundTag();
        appliedTo = null;
    }

    private ClientSystemData() {}
}
