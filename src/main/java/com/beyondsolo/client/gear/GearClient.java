package com.beyondsolo.client.gear;

import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.network.ForgeActionPayload;
import com.beyondsolo.network.ForgeResultPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Client side of the forge: sends requests and keeps the last answer for the forge's animation. */
public final class GearClient {
    private static @Nullable ForgeResultPayload last;
    private static double lastAt = -10;
    private static double sentAt = -10;

    public static void onResult(ForgeResultPayload p) {
        last = p;
        lastAt = Anim.now();
    }

    public static void send(int action, int slot, int arg) {
        sentAt = Anim.now();
        PacketDistributor.sendToServer(new ForgeActionPayload(action, slot, arg));
    }

    public static @Nullable ForgeResultPayload last() {
        return last;
    }

    public static double lastAt() {
        return lastAt;
    }

    public static double sentAt() {
        return sentAt;
    }

    private GearClient() {}
}
