package com.beyondsolo.breaks;

import com.beyondsolo.network.BreakPayload;
import com.beyondsolo.network.GateSensePayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Dungeon breaks ({@link DungeonBreaks}) and gate sensing ({@link GateSense}). Registers the network messages; the
 * rest listens on the game bus by itself. Called once from the mod constructor. Lang lives in
 * {@code assets/beyondsolo_breaks/lang}.
 */
public final class BreakModule {
    public static void init(IEventBus modBus) {
        modBus.addListener(BreakModule::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(BreakPayload.TYPE, BreakPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.breaks.BreakClient.on(p)));
        r.playToClient(GateSensePayload.TYPE, GateSensePayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.breaks.BreakClient.onSense(p)));
    }

    private BreakModule() {}
}
