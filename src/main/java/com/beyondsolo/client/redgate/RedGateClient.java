package com.beyondsolo.client.redgate;

import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.network.RedGatePayload;
import com.beyondsolo.redgate.RedWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The client's copy of the red gate the player is in (or standing near), fed by {@link RedGatePayload}s: its clock
 * (counted on between packets at its rate), its weather, the player's cold, the way to its master, and the moments for
 * {@link RedGateOverlay}. Inside, it also sets the world's sky to the inside clock and its snowfall
 * ({@link #applyWorld}): the server keeps one clock for every dimension, so the red gate's own only lives here.
 */
public final class RedGateClient {
    /** How long the System's red warning stays up (seconds). */
    static final float WARNING_SECONDS = 9f;
    /** How long the card back home stays up (seconds). */
    static final float SUMMARY_SECONDS = 8f;

    /** A one-off moment waiting its turn under the HUD. */
    static final class Banner {
        final int event;
        /** Numbers for its words: the day (dawn), camps taken and how many there are (a camp). */
        final int a, b;
        double start = -1;

        Banner(int event, int a, int b) {
            this.event = event;
            this.a = a;
            this.b = b;
        }

        float age() {
            return start < 0 ? 0 : Anim.since(start);
        }

        float duration() {
            return switch (event) {
                case RedGatePayload.EV_EDGE, RedGatePayload.EV_CALM, RedGatePayload.EV_CLEARED -> 2.4f;
                case RedGatePayload.EV_FREEZING -> 3.2f;
                default -> 3.8f;
            };
        }
    }

    private static final Deque<Banner> BANNERS = new ArrayDeque<>();
    private static Banner banner;

    static int mode = RedGatePayload.NONE;
    static int phase;
    static int rank, insideRank;
    static int day = 1, time;
    static int rate = 1;
    static boolean blizzard;
    static int chill;
    static int party, kills;
    static int releaseDay;
    static int targetX, targetZ;
    static int flags;
    static int cx, cz, radius;
    static int camps, campsTotal;
    static int sealLeft;
    /** Seconds the way out was still open when this hunter arrived (the warning's ring counts them down). */
    static float sealTotal = 8;
    static Component boss = Component.empty();
    static long receivedAt;

    /** The last flash on the screen's edges (a raid, the edge, the gate turning...) and when. */
    static int flash = RedGatePayload.EV_NONE;
    static double flashAt = -100;
    static double warningAt = -100;
    static double sealedAt = -100;
    static double turnedAt = -100;
    static double nightAt = -100;
    static double killPop = -100;

    /** The card back home: how it went. {@link #summaryAt} stays -1 until the card can go up (no screen, no gate moment). */
    static int summary = RedGatePayload.EV_NONE;
    static double summaryAt = -1;
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
                summaryAt = -1;
                sDay = Math.max(1, p.get(RedGatePayload.I_DAY));
                sKills = p.get(RedGatePayload.I_KILLS);
                sCamps = p.get(RedGatePayload.I_CAMPS);
                sCampsTotal = p.get(RedGatePayload.I_CAMPS_TOTAL);
                sXp = p.get(RedGatePayload.I_XP);
                sRank = p.get(RedGatePayload.I_RANK);
                sInside = p.get(RedGatePayload.I_INSIDE_RANK);
                sBoss = p.boss();
            }
            mode = RedGatePayload.NONE;
            BANNERS.clear();
            banner = null;
            hold(false);
            return;
        }
        boolean wasInside = mode == RedGatePayload.INSIDE;
        int oldKills = kills;
        mode = p.mode();
        phase = p.get(RedGatePayload.I_PHASE);
        rank = p.get(RedGatePayload.I_RANK);
        insideRank = p.get(RedGatePayload.I_INSIDE_RANK);
        day = Math.max(1, p.get(RedGatePayload.I_DAY));
        time = p.get(RedGatePayload.I_TIME);
        rate = Math.max(1, p.get(RedGatePayload.I_RATE));
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
        if (wasInside && mode == RedGatePayload.INSIDE && kills > oldKills) killPop = Anim.now();
        switch (ev) {
            case RedGatePayload.EV_NONE -> {}
            case RedGatePayload.EV_WARNING -> {
                warningAt = Anim.now();
                sealTotal = Math.max(1, sealLeft) / 20f;
                summary = RedGatePayload.EV_NONE;
                BANNERS.clear();
                banner = null;
                flash(ev);
                // the black after going through lifts on the warning, not on a name card
                GateOverlay.lift();
                // other pop-ups wait while the System's warning is up
                hold(true);
                sound(SoundEvents.BEACON_DEACTIVATE, 0.5f, 0.9f);
                sound(SoundEvents.WARDEN_HEARTBEAT, 0.8f, 1f);
            }
            case RedGatePayload.EV_SEALED -> {
                sealedAt = Anim.now();
                flash(ev);
                // the warning shows its lock snapping shut; later on it is a banner of its own
                if (!warningUp()) queue(ev, 0, 0);
            }
            case RedGatePayload.EV_TURNED -> {
                turnedAt = Anim.now();
                flash(ev);
                queue(ev, 0, 0);
            }
            case RedGatePayload.EV_NIGHT -> {
                nightAt = Anim.now();
                queue(ev, day, 0);
            }
            case RedGatePayload.EV_DAWN -> queue(ev, day, 0);
            case RedGatePayload.EV_CAMP -> queue(ev, camps, campsTotal);
            case RedGatePayload.EV_RAID, RedGatePayload.EV_EDGE, RedGatePayload.EV_BLIZZARD, RedGatePayload.EV_RELEASED -> {
                flash(ev);
                queue(ev, 0, 0);
            }
            default -> queue(ev, 0, 0);
        }
    }

    private static void flash(int ev) {
        flash = ev;
        flashAt = Anim.now();
    }

    private static void queue(int ev, int a, int b) {
        // the same kind already waiting: the newer one stands for both
        BANNERS.removeIf(x -> x.event == ev);
        if (BANNERS.size() >= 4) BANNERS.pollFirst();
        BANNERS.addLast(new Banner(ev, a, b));
    }

    /** The banner showing now; the next goes up once it is done (none while the warning is up). */
    @Nullable
    static Banner banner() {
        if (banner != null && banner.age() > banner.duration()) banner = null;
        if (banner == null && !BANNERS.isEmpty() && !warningUp()) {
            banner = BANNERS.pollFirst();
            banner.start = Anim.now();
        }
        return banner;
    }

    static boolean warningUp() {
        return Anim.since(warningAt) < WARNING_SECONDS;
    }

    private static void hold(boolean on) {
        held = on;
        NotificationManager.setHeld(on);
    }

    private static void sound(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** Once a client tick: holds the pop-ups while the warning is up, and eases the weather and the cold. */
    static void tick() {
        if (warningUp()) {
            // a gate moment ending under the warning lets the pop-ups go; the warning holds them again
            NotificationManager.setHeld(true);
            held = true;
        } else if (held) {
            hold(false);
        }
        storm = Anim.approach(storm, inside() && blizzard ? 1 : 0, blizzard ? 0.35f : 0.5f);
        shownChill = Anim.approach(shownChill, inside() ? chill / 100f : 0, 3f);
    }

    static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** Real ticks since the last packet (capped, so a stalled server doesn't run the clock away). */
    private static double passed(float partial) {
        return Math.max(0, Math.min(200, gameTime() - receivedAt + partial));
    }

    /** The inside clock now, counted on from the last packet (ticks since the first dawn). */
    static double insideTime(float partial) {
        return (day - 1) * 24000.0 + time + passed(partial) * rate;
    }

    static int dayNow(float partial) {
        return (int) (insideTime(partial) / 24000.0) + 1;
    }

    /** Ticks into the inside day now. */
    static int timeOfDayNow(float partial) {
        return (int) (insideTime(partial) % 24000.0);
    }

    static boolean nightNow(float partial) {
        int t = timeOfDayNow(partial);
        return t >= RedWorld.NIGHT_START && t < RedWorld.NIGHT_END;
    }

    /** How far into the night the inside clock is: 0 by day, 1 in the dead of night, eased through dusk and dawn. */
    static float night(float partial) {
        int t = timeOfDayNow(partial);
        if (t < 12000) return 0;
        if (t < 13500) return (t - 12000) / 1500f;
        if (t < 22500) return 1;
        return 1 - (t - 22500) / 1500f;
    }

    /** Real seconds until the gate seals (while it is still open). */
    static float sealSeconds(float partial) {
        return (float) Math.max(0, (sealLeft - passed(partial)) / 20.0);
    }

    /** Real seconds until the dawn that breaks its hold, or -1 (no release day, or it has let go). */
    static float releaseSeconds(float partial) {
        if (releaseDay <= 0 || phase != RedGatePayload.SEALED) return -1;
        double at = (releaseDay - 2) * 24000.0 + RedWorld.NIGHT_END;
        return (float) Math.max(0, (at - insideTime(partial)) / rate / 20.0);
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
        summaryAt = -1;
        BANNERS.clear();
        banner = null;
        flash = RedGatePayload.EV_NONE;
        flashAt = -100;
        warningAt = -100;
        sealedAt = -100;
        turnedAt = -100;
        nightAt = -100;
        storm = 0;
        shownChill = 0;
        if (held) hold(false);
    }

    private RedGateClient() {}
}
