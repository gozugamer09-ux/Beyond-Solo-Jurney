package com.beyondsolo.penalty;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.PenaltyPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Registers the Penalty Zone: its player data, network message and config. The zone itself is
 * {@link PenaltyZone}; the dimension, its type and biome are data ({@code data/beyondsolo/dimension/penalty_zone.json}).
 */
public final class PenaltyModule {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** Per-player sentence state. Survives death. */
    public static final Supplier<AttachmentType<PenaltyData>> PENALTY = ATTACHMENTS.register("penalty",
            () -> AttachmentType.serializable(PenaltyData::new).copyOnDeath().build());

    public static void init(IEventBus modBus, ModContainer container) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(PenaltyModule::payloads);
        container.registerConfig(ModConfig.Type.COMMON, PenaltyConfig.SPEC, "beyondsolo-penalty.toml");
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(PenaltyPayload.TYPE, PenaltyPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.penalty.PenaltyClient.onPayload(p)));
    }

    private PenaltyModule() {}
}
