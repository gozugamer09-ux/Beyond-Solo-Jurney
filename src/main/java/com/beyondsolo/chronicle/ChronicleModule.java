package com.beyondsolo.chronicle;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.ranking.Rankings;
import com.beyondsolo.chronicle.title.TitleService;
import com.beyondsolo.network.ChronicleActionPayload;
import com.beyondsolo.network.ChronicleSyncPayload;
import com.beyondsolo.network.RankingsPayload;
import com.beyondsolo.network.TitleEarnedPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The hunter's chronicle: earnable titles (worn one at a time, each with a small perk, announced by the System), the
 * rankings (the hunter among the rival hunters of the world, the Citadel and the boss rush), and the journal that fills
 * in as creatures, bosses, realms and weapons are found. One page of the System window, CHRONICLE, shows all three.
 * Titles are {@link com.beyondsolo.chronicle.title.TitleService}, the journal
 * {@link com.beyondsolo.chronicle.journal.JournalService}, the rankings {@link Rankings}. Lang lives in
 * {@code assets/beyondsolo_chronicle/lang}.
 */
public final class ChronicleModule {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** Titles earned, feats counted, journal pages. Survives death. */
    public static final Supplier<AttachmentType<ChronicleData>> CHRONICLE = ATTACHMENTS.register("chronicle",
            () -> AttachmentType.serializable(ChronicleData::new).copyOnDeath().build());

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(ChronicleModule::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(ChronicleSyncPayload.TYPE, ChronicleSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.chronicle.ChronicleClient.onSync(p)));
        r.playToClient(TitleEarnedPayload.TYPE, TitleEarnedPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.chronicle.ChronicleClient.onEarned(p)));
        r.playToClient(RankingsPayload.TYPE, RankingsPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.chronicle.ChronicleClient.onRankings(p)));
        r.playToServer(ChronicleActionPayload.TYPE, ChronicleActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player)) return;
                    switch (p.action()) {
                        case ChronicleActionPayload.EQUIP -> TitleService.equip(player, p.arg(), true);
                        case ChronicleActionPayload.UNEQUIP -> TitleService.equip(player, "", true);
                        case ChronicleActionPayload.READ -> TitleService.read(player, p.arg());
                        case ChronicleActionPayload.RANKINGS -> Rankings.send(player);
                        default -> {}
                    }
                }));
    }

    public static ChronicleData data(net.minecraft.world.entity.player.Player player) {
        return player.getData(CHRONICLE);
    }

    private ChronicleModule() {}
}
