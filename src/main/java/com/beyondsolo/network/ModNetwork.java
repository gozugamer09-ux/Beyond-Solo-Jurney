package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientPayloadHandler;
import com.beyondsolo.evaluation.EvaluationManager;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ModNetwork {
    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(SyncSystemDataPayload.TYPE, SyncSystemDataPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.onSync(payload)));
        registrar.playToClient(NotificationPayload.TYPE, NotificationPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.onNotification(payload)));
        registrar.playToClient(EvaluationPayload.TYPE, EvaluationPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.onEvaluation(payload)));

        registrar.playToClient(CombatStatePayload.TYPE, CombatStatePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.onCombatState(payload)));
        registrar.playToClient(CombatFxPayload.TYPE, CombatFxPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.onCombatFx(payload)));
        registrar.playToClient(DamageNumberPayload.TYPE, DamageNumberPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.onDamageNumber(payload)));

        registrar.playToClient(QuestSyncPayload.TYPE, QuestSyncPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.onQuestSync(payload)));
        registrar.playToClient(QuestEventPayload.TYPE, QuestEventPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.onQuestEvent(payload)));
        registrar.playToServer(QuestActionPayload.TYPE, QuestActionPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player)) return;
                    switch (payload.action()) {
                        case QuestActionPayload.ACCEPT -> QuestEngine.accept(player, payload.quest());
                        case QuestActionPayload.DECLINE -> QuestEngine.decline(player, payload.quest());
                        case QuestActionPayload.ABANDON -> QuestEngine.abandon(player, payload.quest());
                        case QuestActionPayload.ACCEPT_LATEST -> QuestEngine.acceptLatest(player);
                        default -> {}
                    }
                }));

        registrar.playToClient(NpcBoardPayload.TYPE, NpcBoardPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.onNpcBoard(payload)));
        registrar.playToClient(NpcMarkersPayload.TYPE, NpcMarkersPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandler.onNpcMarkers(payload)));
        registrar.playToServer(NpcActionPayload.TYPE, NpcActionPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) {
                        com.beyondsolo.npc.NpcService.handle(player, payload.action(), payload.entity(), payload.slot(), payload.quest());
                    }
                }));

        registrar.playToServer(AllocateStatPayload.TYPE, AllocateStatPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    Stat stat = Stat.byIndex(payload.stat());
                    if (stat != null && ctx.player() instanceof ServerPlayer player) {
                        SystemAPI.allocateStat(player, stat);
                    }
                }));
        registrar.playToServer(SkipEvaluationPayload.TYPE, SkipEvaluationPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) EvaluationManager.skip(player);
                }));
    }

    private ModNetwork() {}
}
