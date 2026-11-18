package com.beyondsolo.client.chronicle;

import com.beyondsolo.chronicle.ChronicleData;
import com.beyondsolo.chronicle.journal.Journal;
import com.beyondsolo.chronicle.journal.JournalEntry;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.network.ChronicleActionPayload;
import com.beyondsolo.network.ChronicleSyncPayload;
import com.beyondsolo.network.RankingsPayload;
import com.beyondsolo.network.TitleEarnedPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The client's copy of the hunter's chronicle (titles, feats, journal pages), how far along each title still to earn
 * is, and the rankings boards last sent. The CHRONICLE page and the status window read it.
 */
public final class ChronicleClient {
    private static ChronicleData data = new ChronicleData();
    private static final Map<String, Long> PROGRESS = new HashMap<>();
    private static @Nullable CompoundTag boards;
    private static double boardsAt = -1;
    private static boolean synced;
    /** Pages and titles the hunter has looked at this session (the server is told too). */
    private static final Set<String> READ = new HashSet<>();

    public static ChronicleData data() {
        return data;
    }

    public static long progress(TitleDef def) {
        return PROGRESS.getOrDefault(def.key(), 0L);
    }

    public static @Nullable CompoundTag boards() {
        return boards;
    }

    public static void onSync(ChronicleSyncPayload p) {
        CompoundTag tag = p.data();
        ChronicleData next = new ChronicleData();
        next.deserializeNBT(null, tag);
        if (synced) {
            // a page filled in since the last sync: the little journal card
            for (String id : next.journal().keySet()) {
                if (!data.knows(id)) {
                    JournalEntry e = Journal.get(id);
                    if (e != null) TitleOverlay.page(e);
                }
            }
        }
        data = next;
        PROGRESS.clear();
        CompoundTag progress = tag.getCompound("progress");
        for (String k : progress.getAllKeys()) PROGRESS.put(k, progress.getLong(k));
        synced = true;
    }

    public static void onEarned(TitleEarnedPayload p) {
        TitleOverlay.earned(p.key(), p.worn());
    }

    public static void onRankings(RankingsPayload p) {
        boards = p.boards();
        boardsAt = com.beyondsolo.client.gfx.Anim.now();
    }

    /** Asks for the boards (when the rankings are opened, at most every few seconds). */
    public static void requestRankings(boolean force) {
        double now = com.beyondsolo.client.gfx.Anim.now();
        if (!force && boards != null && now - boardsAt < 5) return;
        boardsAt = now;
        PacketDistributor.sendToServer(new ChronicleActionPayload(ChronicleActionPayload.RANKINGS, ""));
    }

    public static void equip(String key) {
        PacketDistributor.sendToServer(new ChronicleActionPayload(key.isEmpty() ? ChronicleActionPayload.UNEQUIP : ChronicleActionPayload.EQUIP, key));
    }

    /** A title or page looked at: no longer new. */
    public static void read(String id) {
        if (!data.isFresh(id) || !READ.add(id)) return;
        data.markRead(id);
        PacketDistributor.sendToServer(new ChronicleActionPayload(ChronicleActionPayload.READ, id));
    }

    public static boolean isFresh(String id) {
        return data.isFresh(id) && !READ.contains(id);
    }

    /** The worn title's colour (its tier's), for the status window. */
    public static int titleColor(String key) {
        if (key == null || key.isEmpty()) return SystemTheme.TEXT_DIM;
        return Titles.tierOf(key).color();
    }

    public static TitleTier tier(String key) {
        return Titles.tierOf(key);
    }

    /** The worn title, or null. */
    public static @Nullable String worn() {
        String t = ClientSystemData.get().title();
        return t.isEmpty() ? null : t;
    }

    public static Component titleName(String key) {
        return Component.translatable(key);
    }

    public static void clear() {
        data = new ChronicleData();
        PROGRESS.clear();
        boards = null;
        boardsAt = -1;
        synced = false;
        READ.clear();
    }

    private ChronicleClient() {}
}
