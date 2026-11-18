package com.beyondsolo.util;

import com.beyondsolo.BeyondSolo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The players on the server, looked up by id. Game tests stand in fake players, which are never in the server's player
 * list: a test hands them in with {@link #addTestPlayer} so the systems that look players up by id (parties, the shared
 * awakening trial) find them too. Nothing else ever adds to that list.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class OnlinePlayers {
    private static final Map<UUID, WeakReference<ServerPlayer>> TEST_PLAYERS = new LinkedHashMap<>();

    /** A game test's fake player, to be found by id like a real one (held weakly; gone when the test is). */
    public static void addTestPlayer(ServerPlayer player) {
        TEST_PLAYERS.put(player.getUUID(), new WeakReference<>(player));
    }

    /** The player with this id if they are on the server, else null. */
    @Nullable
    public static ServerPlayer get(MinecraftServer server, @Nullable UUID id) {
        if (id == null) return null;
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p;
        WeakReference<ServerPlayer> ref = TEST_PLAYERS.get(id);
        p = ref == null ? null : ref.get();
        return p == null || p.isRemoved() && !(p instanceof net.neoforged.neoforge.common.util.FakePlayer) ? null : p;
    }

    /** Everyone on the server (and the test players still alive). */
    public static List<ServerPlayer> all(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>(server.getPlayerList().getPlayers());
        Iterator<Map.Entry<UUID, WeakReference<ServerPlayer>>> it = TEST_PLAYERS.entrySet().iterator();
        while (it.hasNext()) {
            ServerPlayer p = it.next().getValue().get();
            if (p == null) {
                it.remove();
                continue;
            }
            if (p.getServer() == server && !out.contains(p)) out.add(p);
        }
        return out;
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        TEST_PLAYERS.clear();
    }

    private OnlinePlayers() {}
}
