package com.beyondsolo.client.duel;

import com.beyondsolo.duel.Duel;
import com.beyondsolo.duel.NationalHunter;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.network.DuelPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The client's side of the duels: every duel the server has told this player about (their own, and any whose ring
 * they stand near), as last sent. The HUD ({@link DuelHud}) and the ring in the world ({@link DuelRing}) read it.
 */
public final class DuelClient {
    /** A duel as the server last sent it, and when that was (client seconds). */
    public static final class View {
        public CompoundTag tag;
        public double at;
        /** When it was first seen over (the result card's clock). */
        public double overAt = -1;
        /** When the challenger was first seen unleashed (the banner's clock). */
        public double unleashedAt = -1;
        /** When the bell was first seen (FIGHT!). */
        public double bellAt = -1;
        /** When this duel was first heard of (the ring is marked out from then). */
        public double seenAt = -1;

        public boolean mine() { return tag.getBoolean("mine"); }
        public NationalHunter who() { return NationalHunter.byIndex(tag.getInt("who")); }
        public Duel.Phase phase() { return Duel.Phase.values()[Math.max(0, Math.min(2, tag.getInt("phase")))]; }
        public Vec3 center() { return new Vec3(tag.getDouble("cx"), tag.getDouble("cy"), tag.getDouble("cz")); }
        public double radius() { return tag.getDouble("r"); }

        @Nullable
        public Duel.Result result() {
            return tag.contains("result") ? Duel.Result.values()[Math.max(0, Math.min(Duel.Result.values().length - 1, tag.getInt("result")))] : null;
        }

        @Nullable
        public Entity entity() {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null ? null : mc.level.getEntity(tag.getInt("entity"));
        }

        /** Game ticks to the bell (the count), and left on the clock. */
        public long toBell() {
            return tag.getLong("fightAt") - gameTime();
        }

        public long left() {
            return tag.getLong("endsAt") - gameTime();
        }

        public UUID id() {
            return tag.getUUID("id");
        }

        /** 0..1: the ring being marked out as it begins, and fading a few seconds after it ends. */
        public float presence() {
            double now = Anim.now();
            float in = seenAt < 0 ? 1 : (float) Math.min(1, (now - seenAt) / 0.7);
            float out = overAt < 0 ? 1 : 1 - (float) Math.max(0, Math.min(1, (now - overAt - 2.5) / 1.5));
            return Math.max(0, Math.min(in, out));
        }
    }

    private static final Map<UUID, View> DUELS = new HashMap<>();

    public static void onSync(DuelPayload p) {
        CompoundTag t = p.data();
        if (!t.hasUUID("id")) return;
        UUID id = t.getUUID("id");
        View v = DUELS.computeIfAbsent(id, k -> new View());
        v.tag = t;
        v.at = Anim.now();
        if (v.seenAt < 0) v.seenAt = Anim.now();
        if (v.phase() == Duel.Phase.OVER && v.overAt < 0) v.overAt = Anim.now();
        if (v.phase() == Duel.Phase.FIGHT && v.bellAt < 0) v.bellAt = Anim.now();
    }

    static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** The duels to show now: stale ones (no word for a while, or over and done) are dropped. */
    public static Map<UUID, View> duels() {
        double now = Anim.now();
        for (Iterator<View> it = DUELS.values().iterator(); it.hasNext(); ) {
            View v = it.next();
            boolean over = v.overAt >= 0 && now - v.overAt > 6.5;
            boolean stale = v.overAt < 0 && now - v.at > 3.5;
            if (over || stale) it.remove();
        }
        return DUELS;
    }

    /** The duel to put on the HUD: the player's own, else the nearest one being watched. */
    @Nullable
    public static View focus() {
        Minecraft mc = Minecraft.getInstance();
        View best = null;
        double bestD = Double.MAX_VALUE;
        for (View v : duels().values()) {
            if (v.mine()) return v;
            if (mc.player == null) continue;
            double d = mc.player.position().distanceToSqr(v.center());
            if (d < bestD) {
                bestD = d;
                best = v;
            }
        }
        return best;
    }

    public static void reset() {
        DUELS.clear();
    }

    private DuelClient() {}
}
