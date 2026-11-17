package com.beyondsolo.key;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.KeyRunPayload;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Runs through keyed gates ({@link DungeonKeys}). The gate in the world is only a door: it waits for its holder, seals
 * {@link DungeonKeys#SEAL_TICKS} after the first one through, and gives up (the key comes back) if nobody goes in. The
 * run's clock starts at the first arrival and stops when the boss falls; the time goes into each hunter's records
 * ({@link KeyRunData}). A run everyone inside has fallen out of (died) is over: its cave closes for the wiper.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class KeyedRuns {

    // ------------------------------------------------------------------ from the gate

    /** Called by the gate when a player arrives in a keyed cave. */
    public static void arrived(ServerPlayer p, GateInstance g) {
        GateInstance.Keyed k = g.keyed();
        if (!k.clockStarted()) k.runStartedAt = p.server.overworld().getGameTime();
        k.inside.add(p.getUUID());
        GateData.get(p.server).setDirty();
        start(p, g);
    }

    /** A player who logged out in a keyed run is back in it. */
    public static void rejoined(ServerPlayer p, GateInstance g) {
        g.keyed().inside.add(p.getUUID());
        GateData.get(p.server).setDirty();
        if (g.state() == GateInstance.State.OPEN) start(p, g);
    }

    /** A player is out of a keyed run (through the way out, or fallen). */
    public static void left(ServerPlayer p, GateInstance g) {
        if (g.keyed().inside.remove(p.getUUID())) GateData.get(p.server).setDirty();
        PacketDistributor.sendToPlayer(p, KeyRunPayload.end());
    }

    /** The run's clock, on the player's screen. */
    private static void start(ServerPlayer p, GateInstance g) {
        GateInstance.Keyed k = g.keyed();
        PacketDistributor.sendToPlayer(p, new KeyRunPayload(KeyRunPayload.START, g.rank().ordinal(), k.chain, k.runStartedAt, 0, -1, 0,
                g.theme().dungeonName()));
    }

    /**
     * The boss of a keyed run fell: the clock stops (on everyone's screen too, with the best time for the rank or NEW
     * BEST), and the time goes into the records of everyone there.
     */
    public static void cleared(ServerLevel level, GateInstance g, List<ServerPlayer> players) {
        GateInstance.Keyed k = g.keyed();
        long now = level.getServer().overworld().getGameTime();
        k.clearTicks = k.clockStarted() ? Math.max(0, now - k.runStartedAt) : 0;
        GateData.get(level.getServer()).setDirty();
        for (ServerPlayer p : players) {
            KeyRunData d = DungeonKeys.data(p);
            boolean newBest = d.clear(g.rank(), k.clearTicks, k.chain);
            DungeonKeys.sync(p);
            PacketDistributor.sendToPlayer(p, new KeyRunPayload(KeyRunPayload.CLEARED, g.rank().ordinal(), k.chain, k.runStartedAt,
                    k.clearTicks, d.best[g.rank().tier()], newBest ? KeyRunPayload.NEW_BEST : 0, g.theme().dungeonName()));
        }
    }

    /** The XP and gold multiplier of a cleared gate: the chain bonus for a keyed run, else 1. */
    public static float bonus(GateInstance g) {
        return g.isKeyed() ? DungeonKeys.bonusFor(g.keyed().chain) : 1f;
    }

    /**
     * Once the cleared banner is up: if the player carries a key of the cave's rank, the prompt to turn it for the next
     * run right here (it shows when the banner is done).
     */
    public static void afterClear(ServerPlayer p, GateInstance g) {
        offerRerun(p, g);
    }

    /** Shows the re-run prompt if the player has a key of this cave's rank. */
    public static void offerRerun(ServerPlayer p, GateInstance g) {
        if (g.isTrial() || !SystemAPI.isAwakened(p)) return;
        int slot = DungeonKeys.rerunSlot(p, g);
        if (slot < 0) return;
        ItemStack key = p.getInventory().getItem(slot);
        int chain = g.isKeyed() ? g.keyed().chain + 1 : 1;
        PacketDistributor.sendToPlayer(p, new KeyRunPayload(KeyRunPayload.RERUN, g.rank().ordinal(), chain, 0, 0, -1,
                DungeonKeys.style(key), key.getHoverName()));
    }

    // ------------------------------------------------------------------ the doors

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        tick(server, 20);
    }

    /** Every second: count unused gates' time, seal doors behind parties, end runs nobody is left in. */
    public static void tick(MinecraftServer server, int dt) {
        GateData data = GateData.get(server);
        long now = server.overworld().getGameTime();
        for (GateInstance g : List.copyOf(data.all())) {
            if (!g.isKeyed() || g.slotFreed() || g.state() == GateInstance.State.CLOSED) continue;
            GateInstance.Keyed k = g.keyed();
            if (!k.entered()) {
                if (k.doorClosed) continue;
                ServerPlayer owner = server.getPlayerList().getPlayer(k.owner);
                if (owner == null) continue;
                k.waited += dt;
                data.setDirty();
                if (k.waited >= DungeonKeys.UNUSED_TICKS || doorGone(server, g)) giveUp(server, g, owner);
                continue;
            }
            if (!k.doorClosed && now - k.firstEntryAt >= DungeonKeys.SEAL_TICKS) seal(server, g);
            if (k.doorClosed && g.state() == GateInstance.State.OPEN && k.inside.isEmpty() && !GateService.inTransitTo(g.id())) {
                // everyone who went in has fallen: the run is over, and the cave goes back to the rock
                g.setState(GateInstance.State.CLOSED);
                data.setDirty();
            }
        }
    }

    /** The door in the world is gone (wiped with the cleared cave it stood in): no point waiting for it. */
    private static boolean doorGone(MinecraftServer server, GateInstance g) {
        ServerLevel level = server.getLevel(g.gateDimension());
        if (level == null) return true;
        if (!level.isLoaded(g.gatePos())) return false;
        return !(level.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate) || !g.id().equals(gate.gateId());
    }

    /** The door shuts behind the party: nobody else gets in, and the gate in the world closes. */
    public static void seal(MinecraftServer server, GateInstance g) {
        GateInstance.Keyed k = g.keyed();
        if (k.doorClosed) return;
        k.doorClosed = true;
        GateData.get(server).setDirty();
        closeDoor(server, g);
        ServerLevel dungeon = GateService.dungeon(server);
        if (dungeon == null) return;
        for (ServerPlayer p : GateService.playersIn(dungeon, g)) {
            p.displayClientMessage(Component.translatable("key.beyondsolo.sealed"), true);
        }
    }

    /** Nobody went through: the door closes and the key goes back to its holder. */
    public static void giveUp(MinecraftServer server, GateInstance g, ServerPlayer owner) {
        GateInstance.Keyed k = g.keyed();
        k.doorClosed = true;
        k.refunded = true;
        g.setState(GateInstance.State.CLOSED);
        GateData.get(server).setDirty();
        closeDoor(server, g);
        DungeonKeys.refund(owner, g);
    }

    private static void closeDoor(MinecraftServer server, GateInstance g) {
        ServerLevel level = server.getLevel(g.gateDimension());
        if (level == null && GateService.isDungeon(server.overworld())) level = server.overworld();
        if (level != null && level.isLoaded(g.gatePos()) && level.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate
                && g.id().equals(gate.gateId())) {
            GateService.close(level, gate);
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) DungeonKeys.sync(p);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) DungeonKeys.sync(p);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DungeonKeys.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        DungeonKeys.clear();
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        KeyCommand.register(event.getDispatcher());
    }

    private KeyedRuns() {}
}
