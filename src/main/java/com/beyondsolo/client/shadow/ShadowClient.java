package com.beyondsolo.client.shadow;

import com.beyondsolo.network.ShadowFxPayload;
import com.beyondsolo.network.ShadowSyncPayload;
import com.beyondsolo.shadow.ShadowRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The client's copy of the player's shadow army, and where shadow packets land. */
public final class ShadowClient {
    private static final List<ShadowRecord> SHADOWS = new ArrayList<>();
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static int cap;
    private static int limit;
    private static boolean canRaise;

    public static List<ShadowRecord> shadows() { return SHADOWS; }
    public static boolean isActive(UUID id) { return ACTIVE.contains(id); }
    public static int activeCount() { return ACTIVE.size(); }
    public static int capacity() { return cap; }
    public static int limit() { return limit; }
    public static boolean canRaise() { return canRaise; }

    public static long gameTime() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    public static void onSync(ShadowSyncPayload p) {
        CompoundTag t = p.data();
        SHADOWS.clear();
        ListTag list = t.getList("shadows", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) SHADOWS.add(ShadowRecord.load(list.getCompound(i)));
        ACTIVE.clear();
        ListTag act = t.getList("active", Tag.TAG_STRING);
        for (int i = 0; i < act.size(); i++) {
            try {
                ACTIVE.add(UUID.fromString(act.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        cap = t.getInt("cap");
        limit = t.getInt("limit");
        canRaise = t.getBoolean("canRaise");
    }

    public static void onFx(ShadowFxPayload p) {
        ShadowWorldFx.handle(p);
        ShadowHud.handle(p);
    }

    public static void clear() {
        SHADOWS.clear();
        ACTIVE.clear();
        cap = 0;
        limit = 0;
        canRaise = false;
        ShadowWorldFx.clear();
        ShadowHud.reset();
    }

    private ShadowClient() {}
}
