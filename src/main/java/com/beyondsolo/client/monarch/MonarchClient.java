package com.beyondsolo.client.monarch;

import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.network.MonarchFxPayload;
import com.beyondsolo.network.MonarchSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The client's side of the monarch path: the player's own crown (from {@link MonarchSyncPayload}), and what monarchs
 * nearby have running (from {@link MonarchFxPayload}): their domains and crown forms, drawn in the world
 * ({@code WorldBossWorldFx}), and their moments on screen ({@link MonarchHud}, {@link ThroneScreen},
 * {@link CoronationOverlay}).
 */
public final class MonarchClient {
    private static @Nullable Dominion dominion;
    private static boolean called;
    private static int minLevel = 80;

    /** A domain or a crown form round a monarch, in game time. */
    public static final class Aura {
        public final int entity;
        public final int color;
        /** A domain's radius; a crown form's dominion. */
        public final int size;
        public final long since;
        public final long until;
        /** When it was ended early (game time), or -1. */
        public long endedAt = -1;

        Aura(int entity, int color, int size, long since, long until) {
            this.entity = entity;
            this.color = color;
            this.size = size;
            this.since = since;
            this.until = until;
        }

        /** 0..1: grows in over half a second, fades over half a second when it ends. */
        public float strength(double now) {
            float in = (float) Math.min(1, (now - since) / 10.0);
            long end = endedAt >= 0 ? endedAt : until;
            float out = (float) Math.min(1, Math.max(0, (end + 10 - now) / 10.0));
            return Math.max(0, Math.min(in, out));
        }

        public boolean over(double now) {
            return now > (endedAt >= 0 ? endedAt : until) + 10;
        }

        public Dominion dominion() {
            return Dominion.byIndex(size);
        }
    }

    private static final Map<Integer, Aura> DOMAINS = new HashMap<>();
    private static final Map<Integer, Aura> FORMS = new HashMap<>();

    // ================================================================ the player's own crown

    public static @Nullable Dominion dominion() {
        return dominion;
    }

    public static boolean crowned() {
        return dominion != null;
    }

    /** The System has called them to the throne. */
    public static boolean called() {
        return called;
    }

    public static int minLevel() {
        return minLevel;
    }

    public static void onSync(MonarchSyncPayload p) {
        dominion = p.dominion() < 0 ? null : Dominion.byIndex(p.dominion());
        called = p.called();
        minLevel = p.minLevel();
    }

    // ================================================================ monarchs' moments

    public static void onFx(MonarchFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        switch (p.kind()) {
            case MonarchFxPayload.DOMAIN -> aura(DOMAINS, p, now);
            case MonarchFxPayload.CROWN -> {
                aura(FORMS, p, now);
                if (p.ticks() > 0 && mc.player != null && p.entity() == mc.player.getId()) MonarchHud.crowned(p.color());
            }
            case MonarchFxPayload.DREAD -> MonarchHud.dread(p);
            case MonarchFxPayload.AUTHORITY -> MonarchHud.authority(p);
            case MonarchFxPayload.CLASH -> MonarchHud.clash(p);
            case MonarchFxPayload.CHOOSE -> ThroneScreen.open(Dominion.byIndex(p.size()), p.ticks());
            case MonarchFxPayload.CORONATION -> {
                ThroneScreen.closeIfOpen();
                CoronationOverlay.start(Dominion.byIndex(p.size()));
            }
            default -> {
            }
        }
    }

    private static void aura(Map<Integer, Aura> map, MonarchFxPayload p, long now) {
        if (p.ticks() <= 0) {
            Aura a = map.get(p.entity());
            if (a != null && a.endedAt < 0) a.endedAt = Math.min(now, a.until);
            return;
        }
        map.put(p.entity(), new Aura(p.entity(), p.color(), p.size(), now, now + p.ticks()));
    }

    /** Domains laid down round monarchs nearby (some may be fading out). */
    public static Collection<Aura> domains() {
        return DOMAINS.values();
    }

    /** Crown forms taken by monarchs nearby (some may be fading out). */
    public static Collection<Aura> forms() {
        return FORMS.values();
    }

    /** The crown form a monarch is in, or null. */
    public static @Nullable Aura formOf(int entity) {
        return FORMS.get(entity);
    }

    /** How deep the local player stands in a domain (0 = in none), and the colour of the strongest one. */
    public static int domainColorAt(Vec3 at, double now, float[] strength) {
        Minecraft mc = Minecraft.getInstance();
        int color = 0;
        float best = 0;
        for (Aura a : DOMAINS.values()) {
            Entity owner = mc.level == null ? null : mc.level.getEntity(a.entity);
            if (owner == null) continue;
            if (owner.position().distanceToSqr(at) > (double) a.size * a.size) continue;
            float s = a.strength(now);
            if (s > best) {
                best = s;
                color = a.color;
            }
        }
        strength[0] = best;
        return color;
    }

    /** Forgets what has ended (once a second). */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() % 20 != 0) return;
        double now = now(0);
        DOMAINS.values().removeIf(a -> a.over(now) || mc.level.getEntity(a.entity) == null && now - a.since > 40);
        FORMS.values().removeIf(a -> a.over(now) || mc.level.getEntity(a.entity) == null && now - a.since > 40);
    }

    /** Game time, with the frame's part of a tick. */
    public static double now(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() + (double) partial;
    }

    /** Another world: what was drawn there is gone. */
    static void clearWorld() {
        DOMAINS.clear();
        FORMS.clear();
    }

    /** Logging out. */
    static void clear() {
        dominion = null;
        called = false;
        clearWorld();
        ThroneScreen.reset();
        CoronationOverlay.reset();
        MonarchHud.reset();
        CrownBar.reset();
    }

    private MonarchClient() {}
}
