package com.beyondsolo.client.worldboss;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.quest.QuestOverlay;
import com.beyondsolo.network.WorldBossPayload;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * What this player knows of the great threat abroad ({@link WorldBossPayload}): which boss, where, its phase and health,
 * how long until a monarch lands, this player's share of the damage, and the banners waiting to be shown. The HUD
 * ({@link WorldBossHud}) and the light in the world ({@link WorldBossWorldFx}) read it.
 */
public final class WorldBossClient {
    /** The boss abroad as last heard. */
    public static final class Tracked {
        public final long event;
        public final WorldBossKind kind;
        public int entity = -1;
        public BlockPos pos;
        /** Where its gate is: where a monarch comes down (or rises from as it leaves). */
        public BlockPos anchor;
        public int phase = 1;
        /** Health 0..1 from the last status (the entity's own health is used when it is in sight). */
        public float health = 1f;
        /** Ticks until a monarch lands, as of {@link #landStamp} (game time). */
        int landTicks;
        long landStamp;
        /** This player's share of the damage (0..1, -1: none yet), and their place among the hunters. */
        public float share = -1f;
        public int place;
        /** A monarch's domain radius once laid down (0: none). */
        public int domain;
        public boolean leaving;
        public boolean slain;
        /** When it was first heard of (for the sky gate's opening), {@link Anim#now()}. */
        public final double since;
        /** When it fell or left ({@link Anim#now()}), -1 while it is abroad. */
        public double endedAt = -1;

        Tracked(long event, WorldBossKind kind, BlockPos pos) {
            this.event = event;
            this.kind = kind;
            this.pos = pos;
            this.anchor = pos;
            this.since = Anim.now();
        }

        /** Ticks until a monarch lands (0: it has, or it is not a monarch coming down). */
        public int landIn() {
            if (landTicks <= 0) return 0;
            return (int) Math.max(0, landTicks - (gameTime() - landStamp));
        }

        public boolean descending() {
            return entity < 0 && landTicks > 0;
        }

        /** Its entity, when it is in this player's world and loaded. */
        @Nullable
        public ColossalBoss boss() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || entity < 0) return null;
            Entity e = mc.level.getEntity(entity);
            return e instanceof ColossalBoss b ? b : null;
        }

        /** Health 0..1: its own when it is in sight, else as last heard. */
        public float healthNow() {
            ColossalBoss b = boss();
            if (b != null) return b.isAlive() ? Math.max(0, b.getHealth() / Math.max(1, b.getMaxHealth())) : 0f;
            return slain ? 0f : health;
        }

        public int phaseNow() {
            ColossalBoss b = boss();
            return b != null ? b.phase() : phase;
        }
    }

    public enum BannerKind { APPEAR, DESCENT, PHASE, DOMAIN, AUTHORITY, SLAIN, WITHDRAW }

    /** A moment in the middle of the screen. */
    public static final class Banner {
        public final BannerKind kind;
        public final WorldBossKind boss;
        public final int phase;
        public final int a;
        public final int b;
        public final BlockPos pos;
        /** Game time when it was heard (countdowns run from it). */
        public final long heard;
        public final float duration;
        /** This player's place among the hunters when it fell (0: unknown). */
        public final int place;
        double start = -1;

        Banner(BannerKind kind, WorldBossKind boss, int phase, int a, int b, BlockPos pos, float duration, int place) {
            this.kind = kind;
            this.boss = boss;
            this.phase = phase;
            this.a = a;
            this.b = b;
            this.pos = pos;
            this.heard = gameTime();
            this.duration = duration;
            this.place = place;
        }

        public float age() {
            return start < 0 ? 0 : Anim.since(start);
        }

        /** Ticks left of a countdown that was {@code ticks} when it was heard. */
        public int left(int ticks) {
            return (int) Math.max(0, ticks - (gameTime() - heard));
        }
    }

    @Nullable private static Tracked tracked;
    private static final Deque<Banner> BANNERS = new ArrayDeque<>();
    @Nullable private static Banner current;
    @Nullable private static ClientLevel seenLevel;

    // ================================================================ messages

    public static void onPayload(WorldBossPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        WorldBossKind kind = WorldBossKind.byIndex(p.boss());
        if (p.kind() == WorldBossPayload.GONE) {
            if (tracked != null && (tracked.event == p.event() || p.event() == 0)) tracked = null;
            return;
        }
        Tracked t = track(p, kind);
        switch (p.kind()) {
            case WorldBossPayload.STATUS -> {
                t.phase = Math.max(1, p.phase());
                t.health = p.a() / 1000f;
                setLand(t, p.b());
            }
            case WorldBossPayload.APPEAR -> {
                setLand(t, 0);
                t.anchor = p.pos();
                t.health = 1f;
                push(new Banner(BannerKind.APPEAR, kind, 1, 0, 0, p.pos(), kind.monarch() ? 5.2f : 5.6f, 0), false);
                sound(kind.monarch() ? SoundEvents.WITHER_SPAWN : SoundEvents.RAID_HORN.value(), 0.5f, 0.35f);
            }
            case WorldBossPayload.DESCENT -> {
                setLand(t, p.a());
                t.anchor = p.pos();
                push(new Banner(BannerKind.DESCENT, kind, 1, p.a(), 0, p.pos(), 6.0f, 0), false);
                sound(SoundEvents.END_PORTAL_SPAWN, 0.6f, 0.45f);
            }
            case WorldBossPayload.PHASE -> {
                t.phase = Math.max(t.phase, p.phase());
                push(new Banner(BannerKind.PHASE, kind, p.phase(), 0, 0, p.pos(), 3.4f, 0), true);
                sound(SoundEvents.ENDER_DRAGON_GROWL, 0.55f, 0.6f);
            }
            case WorldBossPayload.DOMAIN -> {
                t.domain = p.a();
                push(new Banner(BannerKind.DOMAIN, kind, t.phase, p.a(), 0, p.pos(), 3.6f, 0), false);
                sound(SoundEvents.BEACON_ACTIVATE, 0.5f, 0.8f);
            }
            case WorldBossPayload.AUTHORITY -> {
                float secs = Math.max(3.2f, p.a() / 20f + 0.4f);
                push(new Banner(BannerKind.AUTHORITY, kind, 3, p.a(), 0, p.pos(), secs, 0), true);
                sound(SoundEvents.WARDEN_SONIC_CHARGE, 0.7f, 0.9f);
            }
            case WorldBossPayload.SHARE -> {
                t.share = p.a() / 1000f;
                t.place = p.b();
            }
            case WorldBossPayload.SLAIN -> {
                t.slain = true;
                t.health = 0;
                t.endedAt = Anim.now();
                drop(BannerKind.AUTHORITY);
                drop(BannerKind.PHASE);
                push(new Banner(BannerKind.SLAIN, kind, t.phase, p.a(), p.b(), p.pos(), p.a() >= 0 ? 7.0f : 4.6f, t.place), true);
                sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 0.9f);
            }
            case WorldBossPayload.WITHDRAW -> {
                if (!t.leaving) {
                    t.leaving = true;
                    t.anchor = p.pos();
                    t.endedAt = Anim.now();
                    drop(BannerKind.AUTHORITY);
                    push(new Banner(BannerKind.WITHDRAW, kind, t.phase, 0, 0, p.pos(), 4.4f, 0), false);
                    sound(SoundEvents.BEACON_DEACTIVATE, 0.6f, 0.7f);
                }
            }
            default -> {
            }
        }
    }

    /** The tracked boss for a message, made anew when the message names another event. */
    private static Tracked track(WorldBossPayload p, WorldBossKind kind) {
        if (tracked == null || tracked.event != p.event() || tracked.kind != kind) tracked = new Tracked(p.event(), kind, p.pos());
        Tracked t = tracked;
        t.pos = p.pos();
        if (p.entity() >= 0) t.entity = p.entity();
        return t;
    }

    private static void setLand(Tracked t, int ticks) {
        t.landTicks = Math.max(0, ticks);
        t.landStamp = gameTime();
    }

    // ================================================================ banners

    private static int priority(BannerKind k) {
        return switch (k) {
            case SLAIN -> 5;
            case AUTHORITY -> 4;
            case PHASE -> 3;
            case DESCENT, APPEAR -> 2;
            default -> 1;
        };
    }

    private static void push(Banner b, boolean urgent) {
        // the System's moments: a hunter without it only sees the boss itself
        if (!ClientSystemData.get().hasSystem()) return;
        if (!urgent) {
            BANNERS.addLast(b);
            return;
        }
        QuestOverlay.skip();
        BANNERS.addFirst(b);
        if (current != null && priority(current.kind) < priority(b.kind)) current = null;
    }

    private static void drop(BannerKind kind) {
        BANNERS.removeIf(b -> b.kind == kind);
        if (current != null && current.kind == kind) current = null;
    }

    /** The banner on screen now, or null. {@code free} is false while another moment holds the middle of the screen. */
    @Nullable
    static Banner banner(boolean free) {
        if (current != null && current.age() >= current.duration) current = null;
        // a countdown that ran out while it waited is no use any more
        while (current == null && free && !BANNERS.isEmpty()) {
            Banner next = BANNERS.pollFirst();
            if (next.kind == BannerKind.AUTHORITY && next.left(next.a) < 20) continue;
            if (next.kind == BannerKind.PHASE && gameTime() - next.heard > 200) continue;
            current = next;
            current.start = Anim.now();
        }
        return current;
    }

    /** A world boss banner is on screen or waiting (pop-ups and quest windows let it go first). */
    public static boolean bannerPending() {
        return (current != null && current.start >= 0 && current.age() < current.duration) || !BANNERS.isEmpty();
    }

    // ================================================================ state

    @Nullable
    public static Tracked tracked() {
        return tracked;
    }

    /** Clears what is known when the player leaves the overworld or the server (the server tells it again on return). */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != seenLevel) {
            seenLevel = mc.level;
            if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) tracked = null;
        }
        // a boss that fell or left is let go a little after (the bar empties, the gate closes)
        if (tracked != null && tracked.endedAt >= 0 && Anim.since(tracked.endedAt) > 12f) tracked = null;
    }

    static void clear() {
        tracked = null;
        BANNERS.clear();
        current = null;
        seenLevel = null;
    }

    static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    /** Whether another moment holds the middle of the screen. */
    static boolean othersShowing() {
        return GateOverlay.active() || QuestOverlay.active() || com.beyondsolo.client.evaluation.EvaluationOverlay.active()
                || com.beyondsolo.client.skill.SkillOverlay.active() || com.beyondsolo.client.breaks.BreakClient.bannerShowing()
                || com.beyondsolo.client.depths.DepthsClient.bannerShowing();
    }

    private static void sound(SoundEvent s, float volume, float pitch) {
        if (!ClientSystemData.get().hasSystem()) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    private WorldBossClient() {}
}
