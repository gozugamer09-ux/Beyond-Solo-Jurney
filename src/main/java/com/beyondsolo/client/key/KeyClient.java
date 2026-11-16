package com.beyondsolo.client.key;

import com.beyondsolo.network.KeyFxPayload;
import com.beyondsolo.network.KeyRunPayload;
import com.beyondsolo.network.KeySyncPayload;

/** Where key messages from the server go (stub, filled in below). */
public final class KeyClient {
    public static void onFx(KeyFxPayload p) {}

    public static void onRun(KeyRunPayload p) {}

    public static void onSync(KeySyncPayload p) {}

    private KeyClient() {}
}
