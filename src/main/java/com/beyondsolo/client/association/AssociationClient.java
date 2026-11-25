package com.beyondsolo.client.association;

import com.beyondsolo.association.Association;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.network.AssociationActionPayload;
import com.beyondsolo.network.AssociationSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The client's side of the association: the office, the broker's and a great guild's scout's windows as the server
 * last sent them, and the answer to the last button pressed.
 */
public final class AssociationClient {
    private static CompoundTag office = new CompoundTag();
    private static CompoundTag broker = new CompoundTag();
    private static CompoundTag scout = new CompoundTag();
    private static Component outcome = Component.empty();
    private static boolean outcomeOk;
    private static double outcomeAt = -100;

    /** A window arrived from the server: opens it, or refreshes it if it is open. */
    public static void onSync(AssociationSyncPayload p) {
        Minecraft mc = Minecraft.getInstance();
        CompoundTag t = p.data();
        if (t.contains("outcome")) {
            outcome = text(t, "outcome");
            outcomeOk = t.getBoolean("ok");
            outcomeAt = Anim.now();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(outcomeOk ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO, outcomeOk ? 1.4f : 1f, 0.5f));
        }
        if (p.screen() == Association.BROKER) {
            broker = t;
            if (!(mc.screen instanceof BrokerScreen)) mc.setScreen(new BrokerScreen());
        } else if (p.screen() == Association.SCOUT) {
            scout = t;
            if (!(mc.screen instanceof ScoutScreen)) mc.setScreen(new ScoutScreen());
        } else {
            office = t;
            int tab = t.getInt("tab");
            if (mc.screen instanceof AssociationScreen s) {
                if (tab >= 0) s.setTab(tab);
            } else {
                mc.setScreen(new AssociationScreen(Math.max(0, tab)));
            }
        }
    }

    public static CompoundTag office() {
        return office;
    }

    public static CompoundTag broker() {
        return broker;
    }

    public static CompoundTag scout() {
        return scout;
    }

    public static Component outcome() {
        return outcome;
    }

    public static boolean outcomeOk() {
        return outcomeOk;
    }

    /** Seconds since the last answer came. */
    public static float sinceOutcome() {
        return Anim.since(outcomeAt);
    }

    public static void send(int action, CompoundTag args) {
        PacketDistributor.sendToServer(new AssociationActionPayload(action, args));
    }

    public static void send(int action) {
        send(action, new CompoundTag());
    }

    /** A text the server sent as JSON. */
    public static Component text(CompoundTag t, String key) {
        String s = t.getString(key);
        if (s.isEmpty()) return Component.empty();
        Minecraft mc = Minecraft.getInstance();
        try {
            Component c = mc.level == null ? null : Component.Serializer.fromJson(s, mc.level.registryAccess());
            return c == null ? Component.literal(s) : c;
        } catch (Exception e) {
            return Component.literal(s);
        }
    }

    public static void reset() {
        office = new CompoundTag();
        broker = new CompoundTag();
        scout = new CompoundTag();
        outcome = Component.empty();
        outcomeAt = -100;
    }

    private AssociationClient() {}
}
