package com.beyondsolo.client.redgate;

import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.network.RedGatePayload;
import com.beyondsolo.redgate.RedWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The client's copy of the red gate the player is in (or standing near), fed by {@link RedGatePayload}s: its clock
 * (counted on between packets at its rate), its weather, the player's cold, the way to its master, and the moments for
 * {@link RedGateOverlay}. Inside, it also sets the world's sky to the inside clock and its snowfall
 * ({@link #applyWorld}): the server keeps one clock for every dimension, so the red gate's own only lives here.
 */
public final class RedGateClient {
    /** How long the System's red warning stays up (seconds). */
    static final float WARNING_SECONDS = 8.5f;

    static int mode = RedGatePayload.NONE;
    static double modeAt = -100;
    static int phase;
    static int rank, insideRank;
    static int day = 1, time;
    static int rate;
    static boolean blizzard;
    static int chill;
    static int party, kills;
    static int releaseDay;
    static int targetX, targetZ;
    static int flags;
    static int cx, cz, radius;
    static int camps, campsTotal;
    static int sealLeft;
    static Component boss = Component.empty();
    static long receivedAt;

    /** The latest one-off moment (banners) and the warning, each with when it came. */
    static int event = RedGatePayload.EV_NONE;
    static double eventAt = -100;
    static double warningAt = -100;
    static double sealedAt = -100;
    static double turnedAt = -100;

    /** The card back home: how it went. */
    static int summary = RedGatePayload.EV_NONE;
    static double summaryAt = -100;
    static int sDay, sKills, sCamps, sCampsTotal, sXp, sRank, sInside;
    static Component sBoss = Component.empty();

    /** Smoothed for the world: the blizzard's strength and how cold the player is. */
    private static float storm;
    private static float shownChill;
    private static boolean held;

    public static void onPayload(RedGatePayload p) {
        int ev = p.event();
        if (p.mode() == RedGatePayload.NONE) {
            if (ev == RedGatePayload.EV_SUMMARY_CLEARED || ev == RedGatePayload.EV_SUMMARY_ESCAPED || ev == RedGatePayload.EV_SUMMARY_FAILED) {
                summary = ev;
                summaryAt = Anim.now();
                sDay = p.get(RedGatePayload.I_DAY);
                sKills = p.get(RedGatePayload.I_KILLS);
                sCamps = p.get(RedGatePayload.I_CAMPS);
                sCampsTotal = p.get(RedGatePayload.I_CAMPS_TOTAL);
                sXp = p.get(RedGatePayload.I_XP);
                sRank = p.get(RedGatePayload.I_RANK);
                sInside = p.get(RedGatePayload.I_INSIDE_RANK);
                sBoss = p.boss();
            }
            if (mode != RedGatePayload.NONE) modeAt = Anim.now();
            mode = RedGatePayload.NONE;
            hold(false);
            return;
        }
        if (p.mode() != mode) modeAt = Anim.now();
        mode = p.mode();
        int newPhase = p.get(RedGatePayload.I_PHASE);
        phase = newPhase;
        rank = p.get(RedGatePayload.I_RANK);
        insideRank = p.get(RedGatePayload.I_INSIDE_RANK);
        day = Math.max(1, p.get(RedGatePayload.I_DAY));
        time = p.get(RedGatePayload.I_TIME);
        rate = p.get(RedGatePayload.I_RATE);
        blizzard = p.get(RedGatePayload.I_BLIZZARD) != 0;
        chill = p.get(RedGatePayload.I_CHILL);
        party = p.get(RedGatePayload.I_PARTY);
        kills = p.get(RedGatePayload.I_KILLS);
        releaseDay = p.get(RedGatePayload.I_RELEASE_DAY);
        targetX = p.get(RedGatePayload.I_TARGET_X);
        targetZ = p.get(RedGatePayload.I_TARGET_Z);
        flags = p.get(RedGatePayload.I_FLAGS);
        cx = p.get(RedGatePayload.I_CX);
        cz = p.get(RedGatePayload.I_CZ);
        radius = p.get(RedGatePayload.I_RADIUS);
        camps = p.get(RedGatePayload.I_CAMPS);
        campsTotal = p.get(RedGatePayload.I_CAMPS_TOTAL);
        sealLeft = p.get(RedGatePayload.I_SEAL_LEFT);
        boss = p.boss();
        receivedAt = gameTime();
        switch (ev) {
            case RedGatePayload.EV_NONE -> {}
            case RedGatePayload.EV_WARNING -> {
                warningAt = Anim.now();
                // other pop-ups wait while the System's warning is up
                hold(true);
            }
            case RedGatePayload.EV_SEALED -> {
                sealedAt = Anim.now();
                event = ev;
                eventAt = Anim.now();
            }
            case RedGatePayload.EV_TURNED -> turnedAt = Anim.now();
            default -> {
                event = ev;
                eventAt = Anim.now();
            }
        }
    }

    private static void hold(boolean on) {
        if (held == on) return;
        held = on;
        com.beyondsolo.client.notify.NotificationManager.setHeld(on);
    }

    /** Once a client tick: lets the pop-ups go once the warning is over, and eases the weather. */
    static void tick() {
        if (held && Anim.since(warningAt) > WARNING_SECONDS) hold(false);
        storm = Anim.approach(storm, inside() && blizzard ? 1 : 0, blizzard ? 0.35f : 0.5f);
        shownChill = Anim.approach(shownChill, inside() ? chill / 100f : 0, 3f);
    }

    static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** The inside clock now, counted on from the last packet (ticks since the first dawn). */
    static double insideTime(float partial) {
        double passed = Math.max(0, Math.min(200, gameTime() - receivedAt + partial));
        return (day - 1) * 24000.0 + time + passed * rate;
    }

    static int dayNow(float partial) {
        return (int) (insideTime(partial) / 24000.0) + 1;
    }

    /** Ticks into the inside day now. */
    static int timeOfDayNow(float partial) {
        return (int) (insideTime(partial) % 24000.0);
    }

    /** In a red gate's world, with its numbers. */
    public static boolean inside() {
        Minecraft mc = Minecraft.getInstance();
        return mode == RedGatePayload.INSIDE && mc.level != null && (mc.level.dimension().equals(RedWorld.DIMENSION) || mc.isLocalServer() && testWorld());
    }

    /** The game-test fallback runs red gates in the overworld: there the numbers are enough. */
    private static boolean testWorld() {
        return Math.abs(cz) > 100_000;
    }

    public static boolean outside() {
        return mode == RedGatePayload.OUTSIDE;
    }

    public static float storm() {
        return storm;
    }

    public static float chill() {
        return shownChill;
    }

    static boolean flag(int bit) {
        return (flags & bit) != 0;
    }

    /** Inside: the sky follows the inside clock, and snow falls (thick in a blizzard). */
    static void applyWorld(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !inside() || !mc.level.dimension().equals(RedWorld.DIMENSION)) return;
        mc.level.setDayTime((long) insideTime(partial));
        mc.level.setRainLevel(Mth.lerp(storm, 0.35f, 1f));
        mc.level.setThunderLevel(0);
    }

    public static void clear() {
        mode = RedGatePayload.NONE;
        summary = RedGatePayload.EV_NONE;
        event = RedGatePayload.EV_NONE;
        warningAt = -100;
        sealedAt = -100;
        turnedAt = -100;
        storm = 0;
        shownChill = 0;
        hold(false);
    }

    private RedGateClient() {}
}
