package com.beyondsolo.client.tower;

import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.network.TowerPayload;
import com.beyondsolo.network.TowerSyncPayload;
import com.beyondsolo.tower.TowerData;
import net.minecraft.client.Minecraft;

/**
 * The client's copy of the Crimson Citadel: the run the player is in ({@link TowerPayload}, for the HUD and the light
 * in the world) and their records ({@link TowerSyncPayload}, for the CITADEL page).
 */
public final class TowerClient {
    static TowerPayload run = TowerPayload.none();
    /** The phase the run was in before it ended (for the last fade). */
    static int endedFrom = TowerPayload.NONE;
    static double endedAt = -100;
    static double phaseStart;
    static long receivedAt;
    static int event = TowerPayload.EV_NONE;
    static double eventAt = -100;
    /** The floor (or bout) the title card and the plaque were last set for. */
    static int shownFloor = -1;
    static double floorAt = -100;
    static final TowerData DATA = new TowerData();
    /** How long a cleared rush's stamp keeps the pop-ups waiting (the phase itself lasts a minute). */
    static final float RUSH_CARD_SECONDS = 5.5f;
    private static double gateMomentAt = -100;

    public static void onRun(TowerPayload p) {
        int was = run.phase();
        if (p.phase() == TowerPayload.NONE) {
            if (was != TowerPayload.NONE) {
                endedFrom = was;
                endedAt = Anim.now();
            }
            run = p;
            com.beyondsolo.client.notify.NotificationManager.setHeld(false);
            return;
        }
        if (p.phase() != was) {
            // the server's tick in the phase says how far in it already is (a late join, a resent packet)
            phaseStart = Anim.now() - p.tick() / 20.0;
            com.beyondsolo.client.notify.NotificationManager.setHeld(holds(p.phase()));
            // pulled in: the Citadel takes the whole screen, and what was popping up out there waits in the Log
            if (p.phase() == TowerPayload.PULL) com.beyondsolo.client.notify.NotificationManager.dismissShown();
        }
        int floorKey = p.mode() == TowerPayload.RUSH ? 10_000 + p.bout() : p.floor();
        if (floorKey != shownFloor) {
            shownFloor = floorKey;
            floorAt = Anim.now();
        }
        if (p.event() != TowerPayload.EV_NONE) {
            event = p.event();
            eventAt = Anim.now();
        }
        run = p;
        receivedAt = gameTime();
    }

    public static void onSync(TowerSyncPayload p) {
        DATA.deserializeNBT(null, p.data());
    }

    /** Pop-ups wait while the screen belongs to the Citadel (pulled in, title cards, the climb, fallen, leaving). */
    private static boolean holds(int phase) {
        return phase == TowerPayload.PULL || phase == TowerPayload.ARRIVING || phase == TowerPayload.CLIMBING || phase == TowerPayload.FALLEN
                || phase == TowerPayload.LEAVING;
    }

    /**
     * The end cards (the throne taken, a rush cleared) keep the System's pop-ups waiting while they show, so the rewards'
     * pop-ups come in after them. The pop-ups ask this every frame (like a dungeon break's banner).
     */
    public static boolean holdsPopups() {
        int phase = run.phase();
        return phase == TowerPayload.VICTORY || phase == TowerPayload.RUSH_DONE && cardAge() < RUSH_CARD_SECONDS;
    }

    /** How long the phase's card has been up: a boss's fall plays its own DEFEATED moment first, and the card waits for it. */
    static float cardAge() {
        if (com.beyondsolo.client.gate.GateOverlay.active()) gateMomentAt = Anim.now();
        return Math.min(phaseAge(), Anim.since(gateMomentAt));
    }

    private static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** Ticks since the last packet (the HUD counts clocks on between them). */
    static float sinceReceive(float partial) {
        return Math.max(0, gameTime() - receivedAt + partial);
    }

    static float phaseAge() {
        return Anim.since(phaseStart);
    }

    public static boolean inRun() {
        return run.phase() != TowerPayload.NONE;
    }

    public static TowerPayload run() {
        return run;
    }

    /** The player's Citadel records (bound, checkpoint, best floor, rush times ...). */
    public static TowerData data() {
        return DATA;
    }

    /** The rush clock or a siege's time left, counted on between packets (ticks). */
    static float clockNow(float partial) {
        if (run.mode() == TowerPayload.RUSH) {
            // it runs from the first boss waking to the last one falling, breathers included
            boolean running = (run.phase() == TowerPayload.FIGHT || run.phase() == TowerPayload.BREATHER) && run.clock() > 0;
            return run.clock() + (running ? sinceReceive(partial) : 0);
        }
        if (siege() && run.phase() == TowerPayload.FIGHT) return Math.max(0, run.clock() - sinceReceive(partial));
        return run.clock();
    }

    static boolean siege() {
        return run.mode() != TowerPayload.RUSH && run.kind() == com.beyondsolo.tower.TowerFloors.Kind.SIEGE.ordinal();
    }

    /** The blood moon is up (the sky and the fog turn). */
    public static boolean bloodMoon() {
        return inRun() && run.has(TowerPayload.F_MOON);
    }

    public static void clear() {
        if (inRun()) com.beyondsolo.client.notify.NotificationManager.setHeld(false);
        run = TowerPayload.none();
        endedFrom = TowerPayload.NONE;
        endedAt = -100;
        event = TowerPayload.EV_NONE;
        shownFloor = -1;
        DATA.reset();
    }

    private TowerClient() {}
}
