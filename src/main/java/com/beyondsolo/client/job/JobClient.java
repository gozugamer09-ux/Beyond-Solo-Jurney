package com.beyondsolo.client.job;

import com.beyondsolo.job.Subclass;
import com.beyondsolo.network.CutscenePayload;
import com.beyondsolo.network.JobRevealPayload;
import com.beyondsolo.network.JobSyncPayload;
import com.beyondsolo.network.JobTrialPayload;
import org.jetbrains.annotations.Nullable;

/** The client's copy of the player's job change state, and where job change packets land. */
public final class JobClient {
    private static @Nullable Subclass subclass;
    private static boolean unlocked;

    public static @Nullable Subclass subclass() {
        return subclass;
    }

    public static boolean unlocked() {
        return unlocked;
    }

    public static void onSync(JobSyncPayload p) {
        Subclass[] all = Subclass.values();
        subclass = p.subclass() >= 0 && p.subclass() < all.length ? all[p.subclass()] : null;
        unlocked = p.unlocked();
    }

    public static void onTrial(JobTrialPayload p) {
        TrialHud.handle(p);
    }

    public static void onReveal(JobRevealPayload p) {
        JobRevealOverlay.start(p);
    }

    public static void onCutscene(CutscenePayload p) {
        CutsceneCamera.play(p);
    }

    public static void clear() {
        subclass = null;
        unlocked = false;
        CutsceneCamera.stop();
        TrialHud.reset();
        JobRevealOverlay.reset();
    }

    private JobClient() {}
}
