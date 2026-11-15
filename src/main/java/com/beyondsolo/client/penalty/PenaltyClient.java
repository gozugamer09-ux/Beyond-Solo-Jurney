package com.beyondsolo.client.penalty;

import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.network.PenaltyPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** The client's copy of the player's Penalty Zone sentence, fed by {@link PenaltyPayload}s. */
public final class PenaltyClient {
    static int phase = PenaltyPayload.NONE;
    /** The phase the player was in before the sentence ended (for the fade back home). */
    static int endedFrom = PenaltyPayload.NONE;
    static double phaseStart;
    static double endedAt = -100;
    static int length;
    static int tickAtReceive;
    static int remaining;
    static long receivedAt;
    static int kills;
    static int shownKills;
    static double killPop = -100;
    static int cx, cz, radius;
    static Component reason = Component.empty();
    static int event;
    static double eventAt = -100;

    public static void onPayload(PenaltyPayload p) {
        if (p.phase() == PenaltyPayload.NONE) {
            if (phase == PenaltyPayload.SURVIVED || phase == PenaltyPayload.FAILED) {
                endedFrom = phase;
                endedAt = Anim.now();
            }
            phase = PenaltyPayload.NONE;
            com.beyondsolo.client.notify.NotificationManager.setHeld(false);
            return;
        }
        if (p.phase() != phase) {
            // the server's tick in the phase tells how far in it already is (a late join, a resent packet)
            phaseStart = Anim.now() - (p.phase() == PenaltyPayload.SURVIVE ? 0 : p.tick() / 20.0);
            phase = p.phase();
            // other pop-ups wait while the warning and the cards play
            com.beyondsolo.client.notify.NotificationManager.setHeld(phase != PenaltyPayload.SURVIVE);
        }
        length = p.length();
        tickAtReceive = p.tick();
        remaining = p.remaining();
        receivedAt = gameTime();
        if (p.kills() > kills) killPop = Anim.now();
        kills = p.kills();
        cx = p.cx();
        cz = p.cz();
        radius = p.radius();
        reason = p.reason();
        if (p.event() != PenaltyPayload.EV_NONE) {
            event = p.event();
            eventAt = Anim.now();
        }
    }

    private static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** Ticks left to survive, counted on between packets. */
    static float remainingNow(float partial) {
        if (phase != PenaltyPayload.SURVIVE) return remaining;
        float passed = Math.max(0, gameTime() - receivedAt + partial);
        return Math.max(0, remaining - passed);
    }

    static float phaseAge() {
        return Anim.since(phaseStart);
    }

    /** Inside the zone (the edge and the sky apply). */
    public static boolean inside() {
        return phase == PenaltyPayload.ARRIVAL || phase == PenaltyPayload.SURVIVE || phase == PenaltyPayload.SURVIVED || phase == PenaltyPayload.FAILED;
    }

    public static void clear() {
        if (phase != PenaltyPayload.NONE) com.beyondsolo.client.notify.NotificationManager.setHeld(false);
        phase = PenaltyPayload.NONE;
        endedFrom = PenaltyPayload.NONE;
        endedAt = -100;
        kills = 0;
        event = PenaltyPayload.EV_NONE;
    }

    private PenaltyClient() {}
}
