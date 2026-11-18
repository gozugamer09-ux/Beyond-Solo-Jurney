package com.beyondsolo.util;

import com.beyondsolo.BeyondSolo;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
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
 * awakening trial) find them too, for as long as that test runs. Nothing else ever adds to that list.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class OnlinePlayers {
    private static final Map<UUID, TestPlayer> TEST_PLAYERS = new LinkedHashMap<>();

    /**
     * A game test's fake player, to be found by id like a real one until its test is over. NeoForge keeps every fake
     * player for the whole run, so one a finished test left standing in a cave would otherwise still be "online" there
     * for the tests after it (and in a cave slot that has since gone to another test's gate).
     */
    public static void addTestPlayer(GameTestHelper helper, ServerPlayer player) {
        TEST_PLAYERS.put(player.getUUID(), new TestPlayer(new WeakReference<>(player), new WeakReference<>(helper.testInfo)));
    }

    /** The player with this id if they are on the server, else null. */
    @Nullable
    public static ServerPlayer get(MinecraftServer server, @Nullable UUID id) {
        if (id == null) return null;
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p;
        TestPlayer stand = TEST_PLAYERS.get(id);
        p = stand == null ? null : stand.live();
        return p == null || p.isRemoved() && !(p instanceof net.neoforged.neoforge.common.util.FakePlayer) ? null : p;
    }

    /** Everyone on the server (and the test players whose tests are still running). */
    public static List<ServerPlayer> all(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>(server.getPlayerList().getPlayers());
        Iterator<Map.Entry<UUID, TestPlayer>> it = TEST_PLAYERS.entrySet().iterator();
        while (it.hasNext()) {
            ServerPlayer p = it.next().getValue().live();
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

    /** A test's stand-in, held weakly together with its test. */
    private record TestPlayer(WeakReference<ServerPlayer> player, WeakReference<GameTestInfo> test) {
        /** The stand-in while its test runs, else null. */
        @Nullable
        ServerPlayer live() {
            GameTestInfo t = test.get();
            return t == null || t.isDone() ? null : player.get();
        }
    }

    private OnlinePlayers() {}
}
