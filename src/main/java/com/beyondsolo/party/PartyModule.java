package com.beyondsolo.party;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.PartyActionPayload;
import com.beyondsolo.network.PartyEventPayload;
import com.beyondsolo.network.PartySyncPayload;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
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
 * Registers parties: the config ({@code config/beyondsolo-party.toml}), the hunter's regroup clock and the network
 * messages. The parties themselves are {@link Parties} (kept in {@link PartyData}), travelling to a member is
 * {@link Regroup}, the command {@link PartyCommand}. Lang lives in {@code assets/beyondsolo_party/lang}.
 */
public final class PartyModule {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** Game time (the overworld's) from which a hunter can regroup again. Survives death. */
    public static final Supplier<AttachmentType<Long>> REGROUP_READY = ATTACHMENTS.register("regroup_ready",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).copyOnDeath().build());

    public static void init(IEventBus modBus, ModContainer container) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(PartyModule::payloads);
        container.registerConfig(ModConfig.Type.COMMON, PartyConfig.SPEC, "beyondsolo-party.toml");
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(PartySyncPayload.TYPE, PartySyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.party.PartyClient.onSync(p)));
        r.playToClient(PartyEventPayload.TYPE, PartyEventPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.party.PartyClient.onEvent(p)));
        r.playToServer(PartyActionPayload.TYPE, PartyActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) Parties.handle(player, p);
                }));
    }

    private PartyModule() {}
}
