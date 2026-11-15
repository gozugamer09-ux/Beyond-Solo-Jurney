package com.beyondsolo.client.economy;

import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.LicenseInfo;
import com.beyondsolo.economy.ShopCatalog;
import com.beyondsolo.network.EconomyActionPayload;
import com.beyondsolo.network.EconomySyncPayload;
import com.beyondsolo.network.GoldPayload;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/** The client's copy of the player's gold, storage size, gate clears and shop purchases. */
public final class EconomyClient {
    private static long gold;
    private static long earned;
    private static int expansions;
    private static int capacity = 27;
    private static int[] clears = new int[PlayerRank.values().length];
    private static long shopDay = -1;
    private static final Map<String, Integer> bought = new HashMap<>();
    private static String number = "";
    private static boolean synced;

    /** When gold last changed (Anim time) and by how much, for screens that react to it. */
    private static double lastChangeAt = -10;
    private static long lastDelta;
    private static Economy.Reason lastReason = Economy.Reason.COMMAND;

    public static void onSync(EconomySyncPayload p) {
        CompoundTag t = p.data();
        gold = t.getLong("gold");
        earned = t.getLong("earned");
        expansions = t.getInt("expansions");
        capacity = t.getInt("capacity");
        int[] c = t.getIntArray("clears");
        clears = c.length == PlayerRank.values().length ? c : new int[PlayerRank.values().length];
        shopDay = t.getLong("shopDay");
        bought.clear();
        CompoundTag b = t.getCompound("bought");
        for (String k : b.getAllKeys()) bought.put(k, b.getInt(k));
        number = t.getString("number");
        if (!synced) GoldHud.snap(gold);
        synced = true;
    }

    public static void onGold(GoldPayload p) {
        gold = p.total();
        if (p.delta() > 0) earned += p.delta();
        lastChangeAt = com.beyondsolo.client.gfx.Anim.now();
        lastDelta = p.delta();
        lastReason = Economy.Reason.values()[Math.max(0, Math.min(p.reason(), Economy.Reason.values().length - 1))];
        GoldHud.push(p.delta(), p.total(), lastReason);
    }

    public static void clear() {
        gold = 0;
        earned = 0;
        expansions = 0;
        capacity = 27;
        clears = new int[PlayerRank.values().length];
        bought.clear();
        synced = false;
        GoldHud.snap(0);
    }

    public static long gold() { return gold; }
    public static long earned() { return earned; }
    public static int expansions() { return expansions; }
    public static int capacity() { return capacity; }
    public static int clears(PlayerRank r) { return clears[r.ordinal()]; }
    public static String number() { return number; }
    public static double lastChangeAt() { return lastChangeAt; }
    public static long lastDelta() { return lastDelta; }
    public static Economy.Reason lastReason() { return lastReason; }

    public static int totalClears() {
        int n = 0;
        for (int c : clears) n += c;
        return n;
    }

    /** The shop's day as the client sees it. */
    public static long day() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getDayTime() / 24000L;
    }

    public static int bought(String entry) {
        return shopDay == day() ? bought.getOrDefault(entry, 0) : 0;
    }

    public static long price(ShopCatalog.Entry e) {
        return ShopCatalog.price(e, day(), expansions);
    }

    private static double storageRequestedAt = -10;

    /** When the System window last asked for the storage (so it opens without a second opening animation). */
    public static double storageRequestedAt() {
        return storageRequestedAt;
    }

    public static void send(int action, String entry, int amount) {
        if (action == EconomyActionPayload.OPEN_STORAGE) storageRequestedAt = com.beyondsolo.client.gfx.Anim.now();
        PacketDistributor.sendToServer(new EconomyActionPayload(action, entry, amount));
    }

    public static void openLicense(LicenseInfo info) {
        Minecraft.getInstance().setScreen(new LicenseScreen(info));
    }

    /** "12,480" */
    public static String format(long n) {
        return String.format(java.util.Locale.ROOT, "%,d", n);
    }

    private EconomyClient() {}
}
