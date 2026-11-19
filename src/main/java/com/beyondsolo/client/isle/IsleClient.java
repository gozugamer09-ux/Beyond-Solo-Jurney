package com.beyondsolo.client.isle;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.network.IslePayload;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * What the client knows of the Halcyon raid ({@code isle.IsleRaids}): the raid as this hunter sees it (sent about once
 * a second while they are called or on it), the moments for the middle of the screen, and what the raid paid them.
 * {@link IsleHud} draws it.
 */
public final class IsleClient {
    /** How long a raid is kept without word from the server before the HUD lets it go. */
    private static final float STALE = 4f;

    /** The raid as last heard. Countdowns are in ticks as they were when it came ({@link #ticksSince}). */
    public static final class State {
        UUID raid;
        String phase = "muster";
        int you;
        int muster;
        int called;
        int joined;
        PlayerRank rank = PlayerRank.C;
        int left = -1;
        int wave;
        int waves;
        BlockPos[] nests = new BlockPos[0];
        boolean[] burned = new boolean[0];
        int[] blows = new int[0];
        int needed = 1;
        BlockPos descent = BlockPos.ZERO;
        BlockPos mouth = BlockPos.ZERO;
        BlockPos arrival = BlockPos.ZERO;
        BlockPos circle = BlockPos.ZERO;
        int fighting;
        int allies;
        int alliesTotal;
        int swarm;
        int slain;
        int kills;
        int kingIn = -1;
        int home = -1;
        String lost = "none";
        /** When it came (the HUD's clock, and the level's). */
        double at;
        long gameTime;

        public boolean landed() {
            return !phase.equals("muster") && !over();
        }

        public boolean over() {
            return phase.equals("cleared") || phase.equals("failed");
        }

        /** Whether the hive is open (its pit leads down to the queen's chamber). */
        public boolean hiveOpen() {
            return phase.equals("hive") || phase.equals("queen") || phase.equals("king");
        }

        public int burnedCount() {
            int n = 0;
            for (boolean b : burned) if (b) n++;
            return n;
        }

        /** A countdown sent with it, as it stands now (0 at the least; -1 stays -1). */
        int now(int ticks) {
            return ticks < 0 ? -1 : Math.max(0, ticks - (int) ticksSince(this));
        }
    }

    /** A moment in the middle of the screen. */
    public static final class Banner {
        final String id;
        final int a, b;
        final float duration;
        @Nullable final Result result;
        double start = -1;

        Banner(String id, int a, int b, float duration, @Nullable Result result) {
            this.id = id;
            this.a = a;
            this.b = b;
            this.duration = duration;
            this.result = result;
        }

        float age() {
            return start < 0 ? 0 : Anim.since(start);
        }
    }

    /** What the raid paid this hunter, and the tally. */
    public record Result(boolean won, boolean fallen, long time, int kills, int slain, int nests, int alliesFell, int allies, long xp, long gold,
                         long merit, boolean best, String lost) {
        static Result of(CompoundTag t) {
            return new Result(t.getBoolean("won"), t.getBoolean("fallen"), t.getLong("time"), t.getInt("kills"), t.getInt("slain"), t.getInt("nests"),
                    t.getInt("alliesFell"), t.getInt("allies"), t.getLong("xp"), t.getLong("gold"), t.getLong("merit"), t.getBoolean("best"),
                    t.getString("lost"));
        }
    }

    @Nullable private static State state;
    private static final Deque<Banner> BANNERS = new ArrayDeque<>();
    @Nullable private static Banner current;

    public static void onPayload(IslePayload p) {
        CompoundTag t = p.data();
        switch (p.kind()) {
            case "state" -> state = parse(t);
            case "banner" -> banner(t.getString("id"), t.getInt("a"), t.getInt("b"));
            case "result" -> {
                Result r = Result.of(t);
                push(new Banner("result", 0, 0, 9.5f, r), false);
            }
            case "clear" -> state = null;
            default -> {
            }
        }
    }

    private static State parse(CompoundTag t) {
        State s = new State();
        s.raid = t.hasUUID("raid") ? t.getUUID("raid") : new UUID(0, 0);
        s.phase = t.getString("phase");
        s.you = t.getInt("you");
        s.muster = t.getInt("muster");
        s.called = t.getInt("called");
        s.joined = t.getInt("joined");
        PlayerRank[] ranks = PlayerRank.values();
        s.rank = ranks[Math.max(0, Math.min(ranks.length - 1, t.getInt("rank")))];
        s.left = t.contains("left") ? t.getInt("left") : -1;
        s.wave = t.getInt("wave");
        s.waves = t.getInt("waves");
        int[] xs = t.getIntArray("nx"), ys = t.getIntArray("ny"), zs = t.getIntArray("nz");
        int n = Math.min(xs.length, Math.min(ys.length, zs.length));
        s.nests = new BlockPos[n];
        for (int i = 0; i < n; i++) s.nests[i] = new BlockPos(xs[i], ys[i], zs[i]);
        byte[] b = t.getByteArray("burned");
        s.burned = new boolean[n];
        for (int i = 0; i < n && i < b.length; i++) s.burned[i] = b[i] != 0;
        s.blows = t.getIntArray("blows");
        s.needed = Math.max(1, t.getInt("needed"));
        s.descent = BlockPos.of(t.getLong("descent"));
        s.mouth = BlockPos.of(t.getLong("mouth"));
        s.arrival = BlockPos.of(t.getLong("arrival"));
        s.circle = BlockPos.of(t.getLong("circle"));
        s.fighting = t.getInt("fighting");
        s.allies = t.getInt("allies");
        s.alliesTotal = t.getInt("alliesTotal");
        s.swarm = t.getInt("swarm");
        s.slain = t.getInt("slain");
        s.kills = t.getInt("kills");
        s.kingIn = t.contains("kingIn") ? t.getInt("kingIn") : -1;
        s.home = t.contains("home") ? t.getInt("home") : -1;
        s.lost = t.getString("lost");
        s.at = Anim.now();
        Minecraft mc = Minecraft.getInstance();
        s.gameTime = mc.level == null ? -1 : mc.level.getGameTime();
        return s;
    }

    /** Ticks since a state came: by the level's clock (which stops with the game), else the HUD's. */
    static long ticksSince(State s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && s.gameTime >= 0) {
            long d = mc.level.getGameTime() - s.gameTime;
            if (d >= 0 && d < 20 * 60) return d;
        }
        return (long) (Anim.since(s.at) * 20);
    }

    private static void banner(String id, int a, int b) {
        switch (id) {
            // the call's horn, the landing's light and the waves' horn are the server's own, heard in the world
            case "call" -> push(new Banner(id, a, b, 7.0f, null), true);
            case "landing" -> push(new Banner(id, a, b, 4.8f, null), true);
            case "wave" -> push(new Banner(id, a, b, 3.4f, null), false);
            case "push" -> push(new Banner(id, a, b, 3.8f, null), false);
            case "nest" -> {
                drop("nest");
                push(new Banner(id, a, b, 3.2f, null), false);
                sound(SoundEvents.FIRECHARGE_USE, 0.6f, 0.6f);
            }
            case "hive" -> {
                drop("nest");
                push(new Banner(id, a, b, 5.0f, null), true);
                sound(SoundEvents.ENDER_DRAGON_GROWL, 0.4f, 0.5f);
            }
            case "stirs" -> push(new Banner(id, a, b, 4.0f, null), true);
            case "retaken", "lost" -> {
                drop("wave");
                drop("nest");
                drop("push");
                push(new Banner(id, a, b, 5.2f, null), true);
                sound(id.equals("retaken") ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.WITHER_DEATH, id.equals("retaken") ? 0.7f : 0.3f,
                        id.equals("retaken") ? 0.9f : 0.6f);
            }
            default -> {
            }
        }
    }

    /** Queues a moment (an urgent one goes first and takes the place of a lesser one on screen). */
    private static void push(Banner b, boolean urgent) {
        if (!ClientSystemData.get().hasSystem()) return;
        if (!urgent) {
            BANNERS.addLast(b);
            return;
        }
        BANNERS.addFirst(b);
        if (current != null && !current.id.equals("call") && !current.id.equals("result")) current = null;
    }

    private static void drop(String id) {
        BANNERS.removeIf(b -> b.id.equals(id));
        if (current != null && current.id.equals(id)) current = null;
    }

    /** The banner on screen now, or null. {@code free} is false while another moment holds the middle of the screen. */
    @Nullable
    static Banner banner(boolean free) {
        if (current != null && current.age() >= current.duration) current = null;
        if (current == null && free && !BANNERS.isEmpty() && !afterTheKill(BANNERS.peekFirst())) {
            current = BANNERS.pollFirst();
            current.start = Anim.now();
        }
        return current;
    }

    /** The island retaken and the raid's report wait for the king's fall to be told: the kill first, then what it won. */
    private static boolean afterTheKill(Banner next) {
        return (next.id.equals("retaken") || next.id.equals("result")) && com.beyondsolo.client.worldboss.WorldBossClient.slainPending();
    }

    /** A banner of the raid is on screen now (the others wait for it). */
    public static boolean bannerShowing() {
        return current != null && current.start >= 0 && current.age() < current.duration;
    }

    /** A banner of the raid (or its report) is on screen or waiting for it: pop-ups wait, they would cover it. */
    public static boolean bannerPending() {
        return bannerShowing() || !BANNERS.isEmpty();
    }

    /** The raid as last heard, while it is fresh. */
    @Nullable
    public static State state() {
        if (state != null && Anim.since(state.at) > STALE) state = null;
        return state;
    }

    static void tick() {
        state();
    }

    static void clear() {
        state = null;
        BANNERS.clear();
        current = null;
    }

    static void sound(SoundEvent s, float volume, float pitch) {
        if (!ClientSystemData.get().hasSystem()) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    private IsleClient() {}
}
