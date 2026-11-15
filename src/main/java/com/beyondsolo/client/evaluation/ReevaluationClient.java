package com.beyondsolo.client.evaluation;

import com.beyondsolo.network.ReevaluationSyncPayload;

/** The client's copy of "can I be re-evaluated, and when next". The hidden rank itself never reaches the client. */
public final class ReevaluationClient {
    private static boolean available;
    private static int nextLevel;

    public static void onSync(ReevaluationSyncPayload p) {
        available = p.available();
        nextLevel = p.nextLevel();
    }

    public static boolean available() { return available; }
    public static int nextLevel() { return nextLevel; }

    public static void reset() {
        available = false;
        nextLevel = 0;
    }

    private ReevaluationClient() {}
}
