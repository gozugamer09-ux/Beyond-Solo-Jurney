package com.beyondsolo.client.hud;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * The System's mark on Xaero's minimap and world map: one waypoint among the waypoints Xaero's minimap keeps for other
 * mods (its "third-party" waypoints: never saved, never in the player's own sets, gone with the session). Reached by
 * reflection, like {@link HudSpace} reads the minimap's place: nothing here needs Xaero, and if Xaero changes, the mark
 * is just not on its maps (the column of light and the objective chip still show the way).
 */
public final class XaeroWaypoints {
    /** Whose waypoints these are, in Xaero's list of other mods' waypoints. */
    private static final ResourceLocation ORIGIN = BeyondSolo.id("system");
    private static final String ID = "objective";
    /** What the waypoint's square shows: the objective's mark. */
    private static final String INITIALS = "!";

    private static boolean gone;
    private static Object module;
    private static Method session;
    private static Method worldManager;
    private static Method currentWorld;
    private static Method container;
    private static Method thirdParty;
    private static Method origin;
    private static Method add;
    private static Method remove;
    private static Method get;
    private static Constructor<?> waypoint;
    private static Method setVisibility;
    private static Object color;
    private static Object purpose;
    private static Object visibility;

    /** Xaero's waypoints of ours the mark is in now (the world it was added in), the waypoint, and what it shows. */
    private static Object shownIn;
    private static Object shown;
    private static String shownKey;

    /**
     * Shows the mark at {@code pos} named {@code name} on Xaero's maps of the world the player is in, or takes it off
     * (null). Cheap enough to call every few ticks: it only changes Xaero's list when something changed.
     */
    public static void show(@Nullable BlockPos pos, String name) {
        if (gone || !bind()) return;
        try {
            Object points = pos == null ? null : currentPoints();
            String key = pos == null ? null : pos.asLong() + "|" + name;
            if (points != null && points == shownIn && key.equals(shownKey) && get.invoke(points, ID) == shown) return;
            takeOff();
            if (points == null) return;
            Object mark = waypoint.newInstance(pos.getX(), pos.getY(), pos.getZ(), name, INITIALS, color, purpose, false, true);
            setVisibility.invoke(mark, visibility);
            add.invoke(points, ID, mark);
            shownIn = points;
            shown = mark;
            shownKey = key;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            giveUp(e);
        }
    }

    /** The world is left: Xaero's session goes with it, and with it the mark. */
    public static void reset() {
        shownIn = null;
        shown = null;
        shownKey = null;
    }

    /** Takes the mark off while it is on (the waypoint stays a waypoint until removed). */
    private static void takeOff() throws ReflectiveOperationException {
        if (shownIn != null) remove.invoke(shownIn, ID);
        reset();
    }

    /** Our waypoints in Xaero's current world (the dimension the player is in), or null while it has none. */
    @Nullable
    private static Object currentPoints() throws ReflectiveOperationException {
        Object s = session.invoke(module);
        if (s == null) return null;
        Object manager = worldManager.invoke(s);
        if (manager == null) return null;
        Object world = currentWorld.invoke(manager);
        if (world == null) return null;
        Object c = container.invoke(world);
        if (c == null) return null;
        Object others = thirdParty.invoke(c);
        return others == null ? null : origin.invoke(others, ORIGIN);
    }

    private static boolean bind() {
        if (module != null) return true;
        try {
            Object minimap = Class.forName("xaero.hud.minimap.BuiltInHudModules").getField("MINIMAP").get(null);
            // not set up yet: look again next time
            if (minimap == null) return false;
            session = Class.forName("xaero.hud.module.HudModule").getMethod("getCurrentSession");
            worldManager = Class.forName("xaero.hud.minimap.module.MinimapSession").getMethod("getWorldManager");
            currentWorld = Class.forName("xaero.hud.minimap.world.MinimapWorldManager").getMethod("getCurrentWorld");
            container = Class.forName("xaero.hud.minimap.world.MinimapWorld").getMethod("getContainer");
            thirdParty = Class.forName("xaero.hud.minimap.world.container.MinimapWorldContainer").getMethod("getThirdPartyWaypointManager");
            origin = Class.forName("xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypointManager").getMethod("get", ResourceLocation.class);
            Class<?> points = Class.forName("xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints");
            Class<?> point = Class.forName("xaero.common.minimap.waypoints.Waypoint");
            add = points.getMethod("add", String.class, point);
            remove = points.getMethod("remove", String.class);
            get = points.getMethod("get", String.class);
            Class<?> colors = Class.forName("xaero.hud.minimap.waypoint.WaypointColor");
            Class<?> purposes = Class.forName("xaero.hud.minimap.waypoint.WaypointPurpose");
            Class<?> visibilities = Class.forName("xaero.common.minimap.waypoints.WaypointVisibilityType");
            waypoint = point.getConstructor(int.class, int.class, int.class, String.class, String.class, colors, purposes, boolean.class, boolean.class);
            setVisibility = point.getMethod("setVisibility", visibilities);
            // gold like the objective chip; an ordinary waypoint; seen however far it is
            color = colors.getField("GOLD").get(null);
            purpose = purposes.getField("NORMAL").get(null);
            visibility = visibilities.getField("GLOBAL").get(null);
            module = minimap;
            return true;
        } catch (ClassNotFoundException e) {
            // no Xaero's minimap in this pack
            gone = true;
            return false;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            giveUp(e);
            return false;
        }
    }

    private static void giveUp(Throwable e) {
        gone = true;
        module = null;
        reset();
        BeyondSolo.LOGGER.warn("Can't put the System's mark on Xaero's maps", e);
    }

    private XaeroWaypoints() {}
}
