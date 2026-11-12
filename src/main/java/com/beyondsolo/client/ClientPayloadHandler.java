package com.beyondsolo.client;

import com.beyondsolo.client.evaluation.EvaluationOverlay;
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

    private ClientPayloadHandler() {}
}
