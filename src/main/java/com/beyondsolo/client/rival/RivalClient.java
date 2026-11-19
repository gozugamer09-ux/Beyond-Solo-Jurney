package com.beyondsolo.client.rival;

import com.beyondsolo.network.RivalSyncPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/** The client's picture of the rival parties it knows of ({@code rival.RivalParties#view}), for the HUD and the gate marks. */
public final class RivalClient {
    private static List<CompoundTag> parties = List.of();
    private static long at;

    public static void onSync(RivalSyncPayload p) {
        ListTag list = p.data().getList("parties", Tag.TAG_COMPOUND);
        List<CompoundTag> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) out.add(list.getCompound(i));
        parties = out;
        at = System.currentTimeMillis();
    }

    /** The parties, the one in the hunter's gate first, then the nearest (none once the server stops saying). */
    public static List<CompoundTag> parties() {
        return System.currentTimeMillis() - at > 5000 ? List.of() : parties;
    }

    /** Seconds since the last word from the server (the clocks count down between words). */
    public static float since() {
        return (System.currentTimeMillis() - at) / 1000f;
    }

    public static void clear() {
        parties = List.of();
    }

    private RivalClient() {}
}
