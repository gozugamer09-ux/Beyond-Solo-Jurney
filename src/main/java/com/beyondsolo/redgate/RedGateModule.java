package com.beyondsolo.redgate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.RedGatePayload;
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
 * Registers red gates: the hunter's record, the network message and the config. The red gates themselves are
 * {@link RedGates}; the frozen world inside is data ({@code data/beyondsolo/dimension/red_gate.json} and its type,
 * biome, terrain and features) shaped by {@link RedWorld}.
 */
public final class RedGateModule {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    public static final Supplier<AttachmentType<RedRecord>> RECORD = ATTACHMENTS.register("red_gates",
            () -> AttachmentType.serializable(RedRecord::new).copyOnDeath().build());

    public static void init(IEventBus modBus, ModContainer container) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(RedGateModule::payloads);
        container.registerConfig(ModConfig.Type.COMMON, RedGateConfig.SPEC, "beyondsolo-redgates.toml");
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(RedGatePayload.TYPE, RedGatePayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.redgate.RedGateClient.onPayload(p)));
    }

    private RedGateModule() {}
}
