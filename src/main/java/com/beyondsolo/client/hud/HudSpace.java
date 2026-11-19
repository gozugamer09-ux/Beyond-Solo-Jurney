package com.beyondsolo.client.hud;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.notify.PopupPanel;
import com.beyondsolo.client.quest.QuestTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Room on the screen that the System's HUD shares with other mods' HUD pieces.
 *
 * <p><b>Xaero's minimap.</b> Where it sits this frame is read back from Xaero's own HUD, by reflection: nothing here
 * needs Xaero, and if Xaero changes, the HUD just stops making room. Our pieces in the corner it takes start under it:
 * the status capsule and the quest cards on the left; the unread badge, effect icons, pop-ups, gold and party frames
 * on the right. The pack puts the minimap top right ({@code modpack/overrides/config/xaerohud.txt}).</p>
 *
 * <p><b>The top centre.</b> Our bars there (boss bars, the run clock, the break timer, ...) claim the room they drew
 * each frame, so another mod's overlay that sits there can move under them: Jade's tooltip does, see
 * {@link JadeRoom}.</p>
 */
public final class HudSpace {
    /** Our left column: the status capsule and the quest cards, with a margin. */
    private static final float LEFT_COLUMN = 8 + QuestTracker.WIDTH + 8;
    /** Our right column: the floating pop-ups (the effect icons, badge, gold and party frames sit inside it). */
    private static final float RIGHT_COLUMN = PopupPanel.WIDTH + 16 + 8;
    /** The minimap's info line under it (its coordinates, by default). */
    private static final float INFO_LINE = 12;
    private static final float GAP = 4;

    private static final List<float[]> CLAIMS = new ArrayList<>();
    /** Last frame's claims, for pieces that may draw before the ones they must keep clear of. */
    private static final List<float[]> LAST = new ArrayList<>();
    private static long frame;
    private static long readFrame = -1;
    /** Xaero's minimap this frame as {left, top, right, bottom}, or null. */
    private static float[] minimap;

    private static boolean xaeroGone;
    private static Object xaeroModule;
    private static Method xaeroSession;
    private static Method xaeroActive;
    private static Method xaeroState;
    private static Field stateX;
    private static Field stateY;
    private static Field stateW;
    private static Field stateH;

    /** A new frame of the HUD: the last frame's claims go. */
    static void newFrame() {
        frame++;
        LAST.clear();
        LAST.addAll(CLAIMS);
        CLAIMS.clear();
    }

    /**
     * One of our pieces at the top centre of the screen drew in this box this frame (GUI pixels). Returns the claim (null
     * when the box is empty or off the screen), for a piece that also keeps clear of the others: see
     * {@link #topClaimedLately(float, float, float[])}.
     */
    public static float[] claimTop(float x, float y, float w, float h) {
        if (!(w > 0 && h > 0 && y + h > 0)) return null;
        float[] claim = {x, y, x + w, y + h};
        CLAIMS.add(claim);
        return claim;
    }

    /** The lowest edge of what our pieces at the top drew this frame across [left, right]: 0 when none did. */
    public static float topClaimed(float left, float right) {
        float bottom = 0;
        for (float[] c : CLAIMS) if (c[2] > left && c[0] < right) bottom = Math.max(bottom, c[3]);
        return bottom;
    }

    /**
     * Like {@link #topClaimed}, counting last frame's claims too: for a piece that may draw before the bars it has to
     * keep clear of (the layers' order among mods' classes is not fixed).
     */
    public static float topClaimedLately(float left, float right) {
        return topClaimedLately(left, right, null);
    }

    /**
     * Like {@link #topClaimedLately(float, float)}, leaving out one claim: a piece that keeps clear of the others and
     * claims its own room too passes the claim it made last frame, or it would keep clear of itself and slide further
     * down every frame.
     */
    public static float topClaimedLately(float left, float right, float[] mine) {
        float bottom = 0;
        for (float[] c : CLAIMS) if (c != mine && c[2] > left && c[0] < right) bottom = Math.max(bottom, c[3]);
        for (float[] c : LAST) if (c != mine && c[2] > left && c[0] < right) bottom = Math.max(bottom, c[3]);
        return bottom;
    }

    /** The right edge of our left column (the status capsule and the quest cards, with a margin). */
    public static float leftColumn() {
        return LEFT_COLUMN;
    }

    /** Where the top-left corner is free from: under Xaero's minimap while it sits there, else 0. */
    public static float topLeft() {
        float[] m = minimap();
        return m != null && m[0] < LEFT_COLUMN ? m[3] + GAP : 0;
    }

    /** Where the top-right corner is free from: under Xaero's minimap while it sits there, else 0. */
    public static float topRight() {
        float[] m = inTopRight();
        return m != null ? m[3] + GAP : 0;
    }

    /** The left edge of what takes the top-right corner ({@link Float#MAX_VALUE} while nothing does). */
    public static float topRightLeft() {
        float[] m = inTopRight();
        return m != null ? m[0] : Float.MAX_VALUE;
    }

    private static float[] inTopRight() {
        float[] m = minimap();
        return m != null && m[2] > Minecraft.getInstance().getWindow().getGuiScaledWidth() - RIGHT_COLUMN ? m : null;
    }

    /** Xaero's minimap on screen this frame, its info line included, while it is near the top of the screen. */
    private static float[] minimap() {
        if (readFrame == frame) return minimap;
        readFrame = frame;
        minimap = null;
        if (!bindXaero()) return null;
        try {
            Object session = xaeroSession.invoke(xaeroModule);
            if (session == null || !Boolean.TRUE.equals(xaeroActive.invoke(session))) return null;
            // where Xaero put it last, after its own nudges (out of vanilla's effect icons and boss bars)
            Object state = xaeroState.invoke(xaeroModule);
            int x = stateX.getInt(state), y = stateY.getInt(state), w = stateW.getInt(state), h = stateH.getInt(state);
            if (w <= 0 || h <= 0 || y > Minecraft.getInstance().getWindow().getGuiScaledHeight() / 3) return null;
            minimap = new float[]{x, y, x + w, y + h + INFO_LINE};
        } catch (ReflectiveOperationException | RuntimeException e) {
            giveUp(e);
        }
        return minimap;
    }

    private static boolean bindXaero() {
        if (xaeroModule != null) return true;
        if (xaeroGone) return false;
        try {
            Class<?> mod = Class.forName("xaero.common.HudMod");
            Object instance = mod.getField("INSTANCE").get(null);
            // not set up yet: look again next frame
            if (instance == null) return false;
            Object hud = mod.getMethod("getHud").invoke(instance);
            if (hud == null) return false;
            Object modules = Class.forName("xaero.hud.Hud").getMethod("getModuleManager").invoke(hud);
            Object module = Class.forName("xaero.hud.module.ModuleManager").getMethod("get", ResourceLocation.class)
                    .invoke(modules, ResourceLocation.fromNamespaceAndPath("xaerominimap", "minimap"));
            if (module == null) {
                xaeroGone = true;
                return false;
            }
            Class<?> moduleClass = Class.forName("xaero.hud.module.HudModule");
            xaeroSession = moduleClass.getMethod("getCurrentSession");
            xaeroState = moduleClass.getMethod("getPushState");
            xaeroActive = Class.forName("xaero.hud.module.ModuleSession").getMethod("isActive");
            Class<?> state = Class.forName("xaero.hud.pushbox.PushboxHandler$State");
            stateX = state.getField("x");
            stateY = state.getField("y");
            stateW = state.getField("w");
            stateH = state.getField("h");
            xaeroModule = module;
            return true;
        } catch (ClassNotFoundException e) {
            // no Xaero's minimap in this pack
            xaeroGone = true;
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            giveUp(e);
            return false;
        }
    }

    private static void giveUp(Throwable e) {
        xaeroGone = true;
        xaeroModule = null;
        minimap = null;
        BeyondSolo.LOGGER.warn("Can't read where Xaero's minimap is, the System's HUD won't make room for it", e);
    }

    private HudSpace() {}
}
