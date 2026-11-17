package com.beyondsolo.client.breaks;

import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.quest.QuestOverlay;
import com.beyondsolo.network.BreakPayload;
import com.beyondsolo.network.GateSensePayload;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The client's view of dungeon breaks: the gates near the player that are coming apart or broken (for the collapse
 * timer at the top of the screen), the System's banners waiting their turn (the break, its boss coming through, the
 * break suppressed), and the gates the last Pulse Scan sensed.
 */
public final class BreakClient {
    public enum BannerKind { BREAK, BOSS_OUT, SUPPRESSED }

    public static final class Banner {
        public final BannerKind kind;
        public final PlayerRank rank;
        public final Component title;
        public final Component subtitle;
        /** BREAK: how hard it was felt (0..1); SUPPRESSED: the XP it paid. */
        public final float value;
        public final float duration;
        double start = -1;

        Banner(BannerKind kind, PlayerRank rank, Component title, Component subtitle, float value, float duration) {
            this.kind = kind;
            this.rank = rank;
            this.title = title;
            this.subtitle = subtitle;
            this.value = value;
            this.duration = duration;
        }

        /** Seconds since it went up. */
        public float age() {
            return start < 0 ? 0 : Anim.since(start);
        }
    }

    /** A gate near the player that is coming apart or broken, as last heard (about once a second). */
    public static final class Status {
        public final BlockPos pos;
        public PlayerRank rank = PlayerRank.E;
        /** 1 unstable, 2 about to break, 3 broken. */
        public int phase;
        /** Ticks left when last heard (unstable, about to break); monsters out (broken). */
        public int value;
        public boolean bossOut;
        /** Client game time it was last heard. */
        public long heardAt;
        /** Seconds left last time the heartbeat was played. */
        int lastBeat = -1;

        Status(BlockPos pos) {
            this.pos = pos;
        }

        /** Its clock did not move between the last two times it was heard: someone is inside, holding it. */
        public boolean held;

        /** Seconds left before it breaks, counted down since it was last heard (unless it is held). */
        public float secondsLeft() {
            if (held) return value / 20f;
            long now = gameTime();
            return Math.max(0, (value - (now - heardAt)) / 20f);
        }

        public Vec3 center() {
            return new Vec3(pos.getX() + 0.5, pos.getY() + 2.5, pos.getZ() + 0.5);
        }
    }

    /** The gates sensed by the last pulse, until when, and from where. */
    public record Sensed(List<GateSensePayload.Entry> gates, int range, double start, float seconds, long heardAt) {
        public float age() {
            return Anim.since(start);
        }

        public boolean live() {
            return age() < seconds;
        }
    }

    private static final Map<BlockPos, Status> STATUS = new HashMap<>();
    private static final LinkedList<Banner> BANNERS = new LinkedList<>();
    @Nullable private static Banner current;
    @Nullable private static Sensed sensed;
    /** When the last break was felt (for the red flash), and how hard. */
    private static double brokeAt = -100;
    private static float brokeFelt;
    private static float dread;

    // ------------------------------------------------------------------ messages

    public static void on(BreakPayload p) {
        PlayerRank rank = PlayerRank.values()[Mth.clamp(p.rank(), 0, PlayerRank.values().length - 1)];
        switch (p.kind()) {
            case BreakPayload.STATUS -> {
                Status s = STATUS.computeIfAbsent(p.pos().immutable(), Status::new);
                long now = gameTime();
                // a clock that has not moved since it was last heard is held (hunters inside the gate hold it together)
                s.held = p.phase() < 3 && s.phase == p.phase() && s.heardAt > 0 && now - s.heardAt <= 40 && p.a() >= s.value - 4;
                s.rank = rank;
                s.phase = p.phase();
                s.value = p.a();
                s.bossOut = p.b() != 0;
                s.heardAt = now;
            }
            case BreakPayload.BREAK -> {
                float felt = Mth.clamp(p.a() / 100f, 0f, 1f);
                brokeAt = Anim.now();
                brokeFelt = felt;
                GateOverlay.addTrauma(0.35f + 0.6f * felt);
                sound(SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 0.4f + 0.6f * felt);
                BreakWorldFx.broke(p.pos(), rank);
                Status s = STATUS.computeIfAbsent(p.pos().immutable(), Status::new);
                s.rank = rank;
                s.phase = 3;
                s.value = 0;
                s.heardAt = gameTime();
                push(new Banner(BannerKind.BREAK, rank, p.title(), p.subtitle(), felt, 4.6f), true);
            }
            case BreakPayload.BOSS_OUT -> {
                // a roar from far off
                sound(SoundEvents.ENDER_DRAGON_GROWL, 0.55f, 0.45f);
                push(new Banner(BannerKind.BOSS_OUT, rank, p.title(), p.subtitle(), 0, 3.6f), true);
            }
            case BreakPayload.SPILL -> BreakWorldFx.spill(p.pos(), rank, p.a());
            case BreakPayload.CRUMBLE -> BreakWorldFx.crumble(p.a(), p.pos(), p.b() / 10f);
            case BreakPayload.SUPPRESSED -> {
                STATUS.remove(p.pos());
                drop(BannerKind.BREAK);
                drop(BannerKind.BOSS_OUT);
                if (com.beyondsolo.client.ClientSystemData.get().hasSystem()) sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f);
                push(new Banner(BannerKind.SUPPRESSED, rank, p.title(), p.subtitle(), p.a(), 4.4f), true);
            }
            default -> {
            }
        }
    }

    public static void onSense(GateSensePayload p) {
        sensed = new Sensed(List.copyOf(p.gates()), p.range(), Anim.now(), Math.max(4, p.seconds()), gameTime());
    }

    // ------------------------------------------------------------------ banners

    private static int priority(BannerKind kind) {
        return switch (kind) {
            case SUPPRESSED -> 1;
            case BOSS_OUT -> 2;
            case BREAK -> 3;
        };
    }

    private static void push(Banner b, boolean urgent) {
        // the System's banners: without it, a break is only felt
        if (!com.beyondsolo.client.ClientSystemData.get().hasSystem()) return;
        if (!urgent) {
            BANNERS.addLast(b);
            return;
        }
        // a quest window in the middle of the screen folds away early to make room
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
        if (current == null && free && !BANNERS.isEmpty()) {
            current = BANNERS.pollFirst();
            current.start = Anim.now();
        }
        return current;
    }

    /** A banner is on screen now. */
    public static boolean bannerShowing() {
        return current != null && current.start >= 0 && current.age() < current.duration;
    }

    /** A banner is on screen or waiting for it (the quest windows let the break's banners go first). */
    public static boolean bannerPending() {
        return bannerShowing() || !BANNERS.isEmpty();
    }

    // ------------------------------------------------------------------ state

    /** The gate whose timer shows at the top: broken first, then about to break, then the nearest coming apart. */
    @Nullable
    static Status urgent() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        Status best = null;
        double bestScore = Double.MAX_VALUE;
        for (Status s : STATUS.values()) {
            double d = mc.player.position().distanceTo(s.center());
            double score = d - s.phase * 10_000;
            if (score < bestScore) {
                best = s;
                bestScore = score;
            }
        }
        return best;
    }

    @Nullable
    static Sensed sensed() {
        return sensed != null && sensed.live() ? sensed : null;
    }

    /** Seconds since the last break was felt, and how hard (for the red flash). */
    static float sinceBreak() {
        return Anim.since(brokeAt);
    }

    static float breakFelt() {
        return brokeFelt;
    }

    /** How strongly the player stands in a break (0..1, eased): near a broken gate the air turns red. */
    static float dread() {
        return dread;
    }

    /** Once a client tick: forgets gates not heard of lately, beats the heart in a break's last seconds. */
    static void tick() {
        long now = gameTime();
        STATUS.values().removeIf(s -> now - s.heardAt > 60 || now < s.heardAt);
        Minecraft mc = Minecraft.getInstance();
        float target = 0;
        if (mc.player != null) {
            for (Status st : STATUS.values()) {
                if (st.phase != 3) continue;
                double d = mc.player.position().distanceTo(st.center());
                target = Math.max(target, Mth.clamp((float) (1 - (d - 24) / 56), 0f, 1f));
            }
        }
        dread += (target - dread) * 0.04f;
        if (dread < 0.002f && target == 0) dread = 0;
        Status s = urgent();
        if (s != null && s.phase == 2 && com.beyondsolo.client.ClientSystemData.get().hasSystem()) {
            int left = (int) Math.ceil(s.secondsLeft());
            if (left <= 10 && left >= 1 && left != s.lastBeat) {
                s.lastBeat = left;
                sound(SoundEvents.WARDEN_HEARTBEAT, 1f, 0.5f + 0.06f * (10 - left));
            }
        }
        if (sensed != null && !sensed.live()) sensed = null;
    }

    static void clear() {
        STATUS.clear();
        BANNERS.clear();
        current = null;
        sensed = null;
        brokeAt = -100;
        dread = 0;
    }

    static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    private static void sound(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** Every status heard of (for the world effects). */
    static List<Status> statuses() {
        return new ArrayList<>(STATUS.values());
    }

    private BreakClient() {}
}
