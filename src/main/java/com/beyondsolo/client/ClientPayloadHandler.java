package com.beyondsolo.client;

import com.beyondsolo.client.combat.ClientCombatState;
import com.beyondsolo.client.combat.CombatWorldFx;
import com.beyondsolo.client.combat.FloatingNumbers;
import com.beyondsolo.client.evaluation.EvaluationOverlay;
import com.beyondsolo.network.CombatFxPayload;
import com.beyondsolo.network.CombatStatePayload;
import com.beyondsolo.network.DamageNumberPayload;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.network.EvaluationPayload;
import com.beyondsolo.network.NotificationPayload;
import com.beyondsolo.network.SyncSystemDataPayload;
import com.beyondsolo.network.QuestEventPayload;
import com.beyondsolo.network.QuestSyncPayload;
import com.beyondsolo.client.quest.ClientQuests;

/** Client-side packet handling. Only ever called on the client. */
public final class ClientPayloadHandler {
    public static void onSync(SyncSystemDataPayload payload) {
        ClientSystemData.update(payload.data());
    }

    public static void onNotification(NotificationPayload payload) {
        NotificationManager.push(payload.notification());
    }

    public static void onEvaluation(EvaluationPayload payload) {
        EvaluationOverlay.handle(payload);
    }

    public static void onCombatState(CombatStatePayload payload) {
        ClientCombatState.update(payload);
    }

    public static void onCombatFx(CombatFxPayload payload) {
        CombatWorldFx.play(payload);
    }

    public static void onDamageNumber(DamageNumberPayload payload) {
        FloatingNumbers.add(payload);
    }

    public static void onQuestSync(QuestSyncPayload payload) {
        ClientQuests.update(payload);
    }

    public static void onQuestEvent(QuestEventPayload payload) {
        ClientQuests.onEvent(payload);
    }

    public static void onNpcBoard(com.beyondsolo.network.NpcBoardPayload payload) {
        com.beyondsolo.client.npc.NpcBoardScreen.receive(payload.data());
    }

    public static void onNpcMarkers(com.beyondsolo.network.NpcMarkersPayload payload) {
        com.beyondsolo.client.npc.NpcMarkers.update(payload);
    }

    public static void onGate(com.beyondsolo.network.GatePayload payload) {
        com.beyondsolo.client.gate.GateOverlay.handle(payload);
    }

    public static void onSkillSync(com.beyondsolo.network.SkillSyncPayload payload) {
        com.beyondsolo.client.skill.ClientSkills.update(payload.data());
    }

    public static void onSkillMoment(com.beyondsolo.network.SkillMomentPayload payload) {
        com.beyondsolo.client.skill.SkillOverlay.handle(payload);
    }

    public static void onSkillFx(com.beyondsolo.network.SkillFxPayload payload) {
        com.beyondsolo.client.skill.SkillWorldFx.play(payload);
    }

    private ClientPayloadHandler() {}
}
