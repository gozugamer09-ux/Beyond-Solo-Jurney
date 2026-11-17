package com.beyondsolo.depths;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.HitTags;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.DepthsPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the depths. Every half second: each player's screen is told when they come into a cave (its weather and rank)
 * or leave it, and every cave with a party in it gets its weather, hidden chamber and traps ticked. A crimson haze
 * makes the cave's creatures hit harder and heal off what they hit. Logging in sends the weakness log.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class DepthsHooks {
    /** In a crimson haze: the cave's creatures hit this much harder... */
    public static final float CRIMSON_DAMAGE = 1.15f;
    /** ...and heal this share of what they deal. */
    public static final float CRIMSON_LEECH = 0.10f;

    /** The cave each player's screen was last told about (absent: none). */
    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    private record Shown(UUID gate, int weather) {}

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) return;
        tick(server, server.getTickCount());
    }

    /** One half-second beat. Public for tests. */
    public static void tick(MinecraftServer server, long tick) {
        GateData data = GateData.get(server);
        ServerLevel dungeon = GateService.dungeon(server);
        Map<GateInstance, List<ServerPlayer>> parties = new LinkedHashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            GateInstance g = dungeon != null && p.level() == dungeon ? caveOf(data, p) : null;
            show(p, g);
            if (g != null && p.isAlive() && !p.isSpectator()) parties.computeIfAbsent(g, k -> new ArrayList<>()).add(p);
        }
        if (dungeon == null) return;
        for (Map.Entry<GateInstance, List<ServerPlayer>> e : parties.entrySet()) {
            GateInstance g = e.getKey();
            List<ServerPlayer> players = e.getValue();
            // the weather dies down with the boss
            if (!g.bossDefeated()) WeatherService.tick(dungeon, g, players, tick);
            HiddenRooms.tick(dungeon, g, players);
            TrapRooms.tick(dungeon, g, players, tick);
        }
    }

    /** The built, open cave a player in the gate dimension is standing in, or null. */
    private static GateInstance caveOf(GateData data, ServerPlayer p) {
        if (GateData.slotAt(p.blockPosition()) < 1) return null;
        GateInstance g = data.bySlotPos(p.blockPosition());
        if (g == null || !g.built() || g.state() == GateInstance.State.CLOSED) return null;
        return g;
    }

    /** Tells a player's screen about the cave they are in, when it changes (or its weather does). */
    private static void show(ServerPlayer p, GateInstance g) {
        Shown was = SHOWN.get(p.getUUID());
        if (g == null) {
            if (was != null) {
                SHOWN.remove(p.getUUID());
                PacketDistributor.sendToPlayer(p, DepthsPayload.state(-1, 0));
            }
            return;
        }
        int weather = g.depths().weather(g).ordinal();
        if (was != null && was.gate().equals(g.id()) && was.weather() == weather) return;
        SHOWN.put(p.getUUID(), new Shown(g.id(), weather));
        PacketDistributor.sendToPlayer(p, DepthsPayload.state(weather, g.rank().ordinal()));
    }

    /** Forgets what a player's screen was told (it is sent again on the next beat). */
    public static void resend(ServerPlayer p) {
        SHOWN.remove(p.getUUID());
    }

    // ------------------------------------------------------------------ the crimson haze

    /** True if {@code e} is one of a cave's own creatures (or its boss). */
    static boolean caveCreature(Entity e) {
        return e instanceof GateBossEntity || e.getPersistentData().contains(GateService.MOB_TAG);
    }

    @SubscribeEvent
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player) || event.getEntity().level().isClientSide) return;
        Entity src = event.getSource().getEntity();
        if (src == null || !caveCreature(src)) return;
        if (WeatherService.weatherAt(src.level(), src.blockPosition()) == GateWeather.CRIMSON) event.setAmount(event.getAmount() * CRIMSON_DAMAGE);
    }

    @SubscribeEvent
    static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Player) || event.getNewDamage() <= 0) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || !attacker.isAlive() || !caveCreature(attacker)) return;
        if (WeatherService.weatherAt(attacker.level(), attacker.blockPosition()) != GateWeather.CRIMSON) return;
        attacker.heal(event.getNewDamage() * CRIMSON_LEECH);
    }

    // ------------------------------------------------------------------ players

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        SHOWN.remove(p.getUUID());
        WeaknessLog.sync(p);
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        // the screen forgets the cave on the way through; tell it again on arrival
        if (event.getEntity() instanceof ServerPlayer p) SHOWN.remove(p.getUUID());
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        SHOWN.remove(id);
        HiddenRooms.forget(id);
        TrapRooms.forget(id);
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        SHOWN.clear();
        HitTags.clear();
        HiddenRooms.clear();
        TrapRooms.clear();
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        DepthsCommand.register(event.getDispatcher());
    }

    private DepthsHooks() {}
}
