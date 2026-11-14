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

    private ClientPayloadHandler() {}
}
