package com.beyondsolo.client.depths;

import com.beyondsolo.client.enemy.EnemyClient;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.quest.QuestOverlay;
import com.beyondsolo.depths.GateWeather;
import com.beyondsolo.depths.HiddenKind;
import com.beyondsolo.depths.TrapKind;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.ElementalProfile;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.network.DepthsPayload;
import com.beyondsolo.network.WeaknessPayload;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * The client's view of the depths of the cave it is in: the weather (for the fog, the air and the chip), a trap
 * sprung around the player (for its chip and the wave count), the System's banners waiting to be shown, and the
 * weaknesses this player has found.
 */
public final class DepthsClient {
    /** A System banner: what kind, its words, its colour and when it went up. */
    public enum BannerKind { WEATHER, HIDDEN, HIDDEN_ENTER, TRAP, WAVE, TRAP_DONE }

    public static final class Banner {
        public final BannerKind kind;
        public final Component title;
        public final Component subtitle;
        public final int color;
        /** Which weather, trap or chamber (ordinal), for its icon. */
        public final int which;
        public final float duration;
        double start = -1;

        Banner(BannerKind kind, Component title, Component subtitle, int color, int which, float duration) {
            this.kind = kind;
            this.title = title;
            this.subtitle = subtitle;
            this.color = color;
            this.which = which;
            this.duration = duration;
        }

        /** Seconds since it went up. */
        public float age() {
            return start < 0 ? 0 : Anim.since(start);
        }
    }

    @Nullable private static GateWeather weather;
    private static PlayerRank rank = PlayerRank.E;
    private static double weatherSince = -100;

    @Nullable private static TrapKind trap;
    @Nullable private static Vec3 trapAt;
    private static float trapReach;
    private static double trapSince = -100;
    private static int wave, waves;

    private static final LinkedList<Banner> BANNERS = new LinkedList<>();
    @Nullable private static Banner current;

    private static final Set<String> KNOWN = new HashSet<>();

    // ------------------------------------------------------------------ messages

    public static void on(DepthsPayload p) {
        switch (p.kind()) {
            case DepthsPayload.STATE -> state(p.a(), p.b());
            case DepthsPayload.TRAP -> {
                trap = TrapKind.byIndex(p.a());
                trapAt = new Vec3(p.x(), p.y(), p.z());
                trapReach = p.b();
                trapSince = Anim.now();
                wave = 0;
                waves = 0;
                // a trap cuts in front of whatever else is waiting
                push(new Banner(BannerKind.TRAP, p.title(), p.subtitle(), trap.color, trap.ordinal(), 3.4f), true);
            }
            case DepthsPayload.TRAP_ACTIVE -> {
                // already going off when this player came in: its chip and marker, without the banner
                trap = TrapKind.byIndex(p.a() & 15);
                trapAt = new Vec3(p.x(), p.y(), p.z());
                trapReach = p.b();
                trapSince = Anim.now();
                wave = p.a() >> 4;
                waves = wave > 0 ? 2 : 0;
            }
            case DepthsPayload.WAVE -> {
                wave = p.a();
                waves = p.b();
                push(new Banner(BannerKind.WAVE, p.title(), Component.empty(), TrapKind.LOCKDOWN.color, TrapKind.LOCKDOWN.ordinal(), 2.0f), true);
            }
            case DepthsPayload.TRAP_DONE -> {
                TrapKind kind = TrapKind.byIndex(p.a());
                if (trap == kind) {
                    trap = null;
                    trapAt = null;
                }
                drop(BannerKind.TRAP);
                drop(BannerKind.WAVE);
                push(new Banner(BannerKind.TRAP_DONE, p.title(), p.subtitle(), 0x8BE28B, kind.ordinal(), 2.8f), true);
            }
            case DepthsPayload.HIDDEN -> {
                HiddenKind kind = HiddenKind.byIndex(p.a());
                push(new Banner(BannerKind.HIDDEN, p.title(), p.subtitle(), 0xB58CFF, kind.ordinal(), 4.6f), true);
            }
            case DepthsPayload.HIDDEN_ENTER -> {
                HiddenKind kind = HiddenKind.byIndex(p.a());
                push(new Banner(BannerKind.HIDDEN_ENTER, p.title(), p.subtitle(), kind.color, kind.ordinal(), 3.4f), false);
            }
            default -> {
            }
        }
    }

    private static void state(int weatherIndex, int rankIndex) {
        if (weatherIndex < 0) {
            weather = null;
            trap = null;
            trapAt = null;
            BANNERS.clear();
            current = null;
            return;
        }
        GateWeather w = GateWeather.byIndex(weatherIndex);
        PlayerRank r = PlayerRank.values()[Mth.clamp(rankIndex, 0, PlayerRank.values().length - 1)];
        if (w == weather && r == rank) return;
        weather = w;
        rank = r;
        weatherSince = Anim.now();
        drop(BannerKind.WEATHER);
        if (w != GateWeather.CLEAR) {
            push(new Banner(BannerKind.WEATHER, w.title(), w.description(), w.color, w.ordinal(), 4.4f), false);
        }
    }

    public static void onWeaknesses(WeaknessPayload p) {
        if (p.reset()) KNOWN.clear();
        KNOWN.addAll(p.entries());
    }

    // ------------------------------------------------------------------ banners

    private static int priority(BannerKind kind) {
        return switch (kind) {
            case WEATHER -> 0;
            case HIDDEN_ENTER -> 1;
            case WAVE, TRAP_DONE -> 2;
            case TRAP, HIDDEN -> 3;
        };
    }

    private static void push(Banner b, boolean urgent) {
        if (!urgent) {
            BANNERS.addLast(b);
            return;
        }
        // a quest window in the middle of the screen folds away early to make room
        QuestOverlay.skip();
        // an urgent banner goes next, and cuts short a less urgent one that is up (a weather banner barely begun
        // goes back to wait its turn)
        BANNERS.addFirst(b);
        if (current != null && priority(current.kind) < priority(b.kind)) {
            if (current.kind == BannerKind.WEATHER && current.age() < 1.2f) {
                current.start = -1;
                BANNERS.add(1, current);
            }
            current = null;
        }
    }

    private static void drop(BannerKind kind) {
        BANNERS.removeIf(b -> b.kind == kind);
        if (current != null && current.kind == kind) current = null;
    }

    /**
     * The banner on screen now, or null. {@code free} is false while another moment holds the middle of the screen
     * (the gate's, a quest window, the evaluation, a new skill); while {@code questWaiting}, only the urgent banners
     * (a trap, its waves and its end, a hidden dungeon found) go before the quest window.
     */
    @Nullable
    static Banner banner(boolean free, boolean questWaiting) {
        if (current != null && current.age() >= current.duration) current = null;
        if (current == null && free && !BANNERS.isEmpty() && (!questWaiting || priority(BANNERS.peekFirst().kind) >= 2)) {
            current = BANNERS.pollFirst();
            current.start = Anim.now();
        }
        return current;
    }

    // ------------------------------------------------------------------ the cave

    /** The weather of the cave the player is in, or null outside a cave. */
    @Nullable
    public static GateWeather weather() {
        return weather;
    }

    public static PlayerRank rank() {
        return rank;
    }

    /** Seconds since the weather (or the cave) changed. */
    public static float sinceWeather() {
        return Anim.since(weatherSince);
    }

    /** A banner is on screen now (the quest windows wait for it to go). */
    public static boolean bannerShowing() {
        return current != null && current.start >= 0 && current.age() < current.duration;
    }

    /** The weather banner is still up (the chip waits for it). */
    static boolean weatherBannerUp() {
        if (current != null && current.kind == BannerKind.WEATHER) return true;
        for (Banner b : BANNERS) if (b.kind == BannerKind.WEATHER) return true;
        return false;
    }

    /** The trap sprung around the player, while they are still in its room. */
    @Nullable
    public static TrapKind trap() {
        if (trap == null || trapAt == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        double dx = mc.player.getX() - trapAt.x, dz = mc.player.getZ() - trapAt.z;
        if (Math.sqrt(dx * dx + dz * dz) > trapReach + 4 || Math.abs(mc.player.getY() - trapAt.y) > 16) return null;
        return trap;
    }

    public static float sinceTrap() {
        return Anim.since(trapSince);
    }

    public static int wave() {
        return wave;
    }

    public static int waves() {
        return waves;
    }

    // ------------------------------------------------------------------ weaknesses

    /** True if this player has found {@code element} to be a weakness of creatures like {@code e}. */
    public static boolean knows(LivingEntity e, SkillElement element) {
        return KNOWN.contains(com.beyondsolo.depths.WeaknessLog.entry(ElementalProfile.of(e).key(), element));
    }

    /** The weaknesses of {@code e} this player has found, most severe first. */
    public static List<SkillElement> knownWeaknesses(LivingEntity e) {
        List<SkillElement> out = new ArrayList<>();
        if (KNOWN.isEmpty()) return out;
        ElementalProfile p = ElementalProfile.of(e);
        for (SkillElement w : p.weaknesses()) if (KNOWN.contains(com.beyondsolo.depths.WeaknessLog.entry(p.key(), w))) out.add(w);
        return out;
    }

    public static int knownCount() {
        return KNOWN.size();
    }

    /** Lets {@link ElementalProfile} see a creature's affinity on this side. */
    static void setup() {
        ElementalProfile.clientAffinity = id -> {
            EnemyTraits t = EnemyClient.traits(id);
            return t == null ? Affinity.NONE : t.affinity();
        };
    }

    static void clear() {
        weather = null;
        trap = null;
        trapAt = null;
        BANNERS.clear();
        current = null;
        KNOWN.clear();
    }

    private DepthsClient() {}
}
