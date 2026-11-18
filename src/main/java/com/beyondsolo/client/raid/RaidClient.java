package com.beyondsolo.client.raid;

import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.network.RaidActionPayload;
import com.beyondsolo.network.RaidEventPayload;
import com.beyondsolo.network.RaidSyncPayload;
import com.beyondsolo.raid.RaidRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the client knows of the raid it is on: the last {@link RaidSyncPayload} (as {@code raid.Raids.syncTag} built it:
 * the muster and its roster, then inside every raider's and hunter's health, what is left of the lieutenants and the
 * barrier, the boss and the next cataclysm), the banners across the screen, the cataclysm being channelled, the results
 * board and the last answer to a briefing button.
 */
public final class RaidClient {
    private static RaidSyncPayload state = RaidSyncPayload.none();
    /** Client game time the state came in (the clocks in it count from then). */
    private static long syncedAt;

    /** How long a banner stays across the screen, and the results board up, in seconds. */
    static final float BANNER_SECONDS = 3.6f;
    static final float RESULTS_SECONDS = 14f;

    /** The banner across the screen. */
    private static Component bannerTitle = Component.empty(), bannerText = Component.empty();
    private static int bannerColor = 0xFFC247;
    private static String bannerIcon = "";
    private static double bannerAt = -10;
    /** A banner waiting for the middle of the screen (a quest or title moment, or a gate's own card, is up), and since when. */
    private static boolean bannerWaiting;
    private static double bannerQueuedAt = -10;
    /**
     * When something else last held the middle of the screen (a gate's own card such as the boss DEFEATED or DUNGEON
     * CLEARED, a quest or title moment already under way): the results board comes in after it.
     */
    private static double heldAt = -10;

    /** The cataclysm the boss is channelling: since when (Anim time) and how long until it lands. */
    private static double cataclysmAt = -10;
    private static int cataclysmTicks;

    /** The results board, while it shows. */
    @Nullable private static CompoundTag results;
    private static Component resultsTitle = Component.empty(), resultsDungeon = Component.empty();
    private static double resultsAt = -10;

    private static Component outcome = Component.empty();
    private static boolean outcomeOk;
    private static double outcomeAt = -10;

    public static void onSync(RaidSyncPayload p) {
        state = p;
        syncedAt = gameTime();
    }

    public static void onEvent(RaidEventPayload p) {
        Minecraft mc = Minecraft.getInstance();
        switch (p.kind()) {
            case RaidEventPayload.BRIEF -> {
                // the briefing over whatever else is open, unless the player is typing somewhere
                if (mc.screen == null || mc.screen instanceof RaidBriefingScreen || mc.screen instanceof com.beyondsolo.client.screen.SystemScreen) {
                    if (!(mc.screen instanceof RaidBriefingScreen)) mc.setScreen(new RaidBriefingScreen());
                }
                sound(SoundEvents.BEACON_POWER_SELECT, 0.7f, 0.6f);
            }
            case RaidEventPayload.BANNER -> {
                bannerTitle = p.title();
                bannerText = p.text();
                bannerColor = p.data().contains("color") ? p.data().getInt("color") : 0xFFC247;
                bannerIcon = p.data().getString("icon");
                // it comes in once the middle of the screen is free (see banner())
                bannerAt = -10;
                bannerWaiting = true;
                bannerQueuedAt = Anim.now();
            }
            case RaidEventPayload.CATACLYSM -> {
                cataclysmAt = Anim.now();
                cataclysmTicks = Math.max(1, p.data().getInt("ticks"));
                bannerTitle = p.title();
                bannerText = p.text();
                bannerColor = 0xFF3348;
                bannerIcon = "doom";
                bannerAt = Anim.now();
                bannerWaiting = false;
                // no time to wait: a quest or title window in the middle of the screen folds away to make room
                com.beyondsolo.client.quest.QuestOverlay.skip();
                com.beyondsolo.client.chronicle.TitleOverlay.skip();
                sound(SoundEvents.WARDEN_SONIC_CHARGE, 0.6f, 1.0f);
            }
            case RaidEventPayload.RESULTS -> {
                results = p.data();
                resultsTitle = p.title();
                resultsDungeon = p.text();
                resultsAt = Anim.now();
                cataclysmAt = -10;
                boolean ok = p.data().getBoolean("cleared");
                sound(ok ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.BEACON_DEACTIVATE, ok ? 1.0f : 0.6f, 0.8f);
            }
            case RaidEventPayload.OUTCOME -> {
                outcome = p.title();
                outcomeOk = p.data().getBoolean("ok");
                outcomeAt = Anim.now();
                if (!outcomeOk) sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.5f);
            }
            default -> {}
        }
    }

    private static void sound(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** Forgets everything (logged out). */
    public static void reset() {
        state = RaidSyncPayload.none();
        bannerAt = -10;
        bannerWaiting = false;
        cataclysmAt = -10;
        results = null;
        outcome = Component.empty();
        outcomeAt = -10;
    }

    private static long gameTime() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    // ------------------------------------------------------------------ reads

    public static CompoundTag data() {
        return state.data();
    }

    public static boolean active() {
        return state.data().getBoolean("active");
    }

    public static Component dungeon() {
        return state.dungeon();
    }

    public static Component boss() {
        return state.boss();
    }

    /** MUSTER, COUNTDOWN, RUNNING, CLEARED or FAILED ("" if none). */
    public static String phase() {
        return state.data().getString("phase");
    }

    public static boolean mustering() {
        String p = phase();
        return active() && (p.equals("MUSTER") || p.equals("COUNTDOWN"));
    }

    /** True once the raid has been taken through (running or over), and the player is on it. */
    public static boolean inside() {
        String p = phase();
        return active() && (p.equals("RUNNING") || p.equals("CLEARED") || p.equals("FAILED"));
    }

    @Nullable
    public static UUID me() {
        var p = Minecraft.getInstance().player;
        return p == null ? null : p.getUUID();
    }

    public static boolean leading() {
        CompoundTag t = state.data();
        return t.hasUUID("leader") && t.getUUID("leader").equals(me());
    }

    @Nullable
    public static RaidRole role() {
        return RaidRole.byId(state.data().getString("role"));
    }

    public static List<CompoundTag> raiders() {
        return list("raiders");
    }

    public static List<CompoundTag> hunters() {
        return list("hunters");
    }

    private static List<CompoundTag> list(String key) {
        List<CompoundTag> out = new ArrayList<>();
        ListTag l = state.data().getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) out.add(l.getCompound(i));
        return out;
    }

    /** Ticks a clock in the state still has, counted down since the sync (-1 stays -1). */
    public static long ticks(String key) {
        int v = state.data().getInt(key);
        if (!state.data().contains(key) || v < 0) return -1;
        return Math.max(0, v - (gameTime() - syncedAt));
    }

    /** How long the raid has run, in ticks (counting on between syncs while it runs). */
    public static long elapsed() {
        long e = state.data().getLong("elapsed");
        return phase().equals("RUNNING") ? e + (gameTime() - syncedAt) : e;
    }

    // ---- moments

    public static Component bannerTitle() {
        return bannerTitle;
    }

    public static Component bannerText() {
        return bannerText;
    }

    public static int bannerColor() {
        return bannerColor;
    }

    public static String bannerIcon() {
        return bannerIcon;
    }

    public static float sinceBanner() {
        return Anim.since(bannerAt);
    }

    /**
     * Once a frame, from the HUD: starts the banner waiting for the middle of the screen once no quest or title moment and
     * no gate card holds it (one that waited too long is dropped: it is old news). True while a banner is on screen.
     */
    static boolean bannerUp() {
        if (bannerWaiting) {
            if (Anim.since(bannerQueuedAt) > 8f) {
                bannerWaiting = false;
            } else if (!com.beyondsolo.client.quest.QuestOverlay.active() && !com.beyondsolo.client.chronicle.TitleOverlay.active()
                    && !com.beyondsolo.client.gate.GateOverlay.active()) {
                bannerWaiting = false;
                bannerAt = Anim.now();
                sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 0.9f);
            }
        }
        return bannerAt >= 0 && Anim.since(bannerAt) <= BANNER_SECONDS && !bannerTitle.getString().isEmpty();
    }

    /**
     * The raid holds the middle of the screen: a banner across it, a cataclysm being channelled, or the results board.
     * The quest and title moments wait for it, as they do for the tower's cards.
     */
    public static boolean holdsScreen() {
        return bannerAt >= 0 && Anim.since(bannerAt) <= BANNER_SECONDS || cataclysm() >= 0 || results != null;
    }

    /** 0..1 of the cataclysm gathered, or -1 if none is coming. */
    public static float cataclysm() {
        if (cataclysmAt < 0) return -1;
        float k = Anim.since(cataclysmAt) * 20f / cataclysmTicks;
        if (k > 1.15f) return -1;
        return Mth.clamp(k, 0f, 1f);
    }

    /** Seconds until the cataclysm lands. */
    public static float cataclysmSeconds() {
        return Math.max(0, cataclysmTicks / 20f - Anim.since(cataclysmAt));
    }

    @Nullable
    public static CompoundTag results() {
        return results;
    }

    public static Component resultsTitle() {
        return resultsTitle;
    }

    public static Component resultsDungeon() {
        return resultsDungeon;
    }

    /**
     * How long the results board has been up. It waits for the gate's own end cards (the boss DEFEATED, DUNGEON CLEARED)
     * and for a quest or title moment already on screen; none starts while it is up ({@link #holdsScreen}).
     */
    public static float sinceResults() {
        if (com.beyondsolo.client.gate.GateOverlay.active() || com.beyondsolo.client.quest.QuestOverlay.active()
                || com.beyondsolo.client.chronicle.TitleOverlay.active()) heldAt = Anim.now();
        return (float) Math.min(Anim.since(resultsAt), Anim.since(heldAt));
    }

    /** Puts the results board away. */
    public static void closeResults() {
        results = null;
    }

    /** The hide key: the results board folds away. True if one was up. */
    public static boolean skipResults() {
        if (results == null || sinceResults() <= 0.05f) return false;
        float t = sinceResults(), end = RESULTS_SECONDS - 0.6f;
        if (t < end) resultsAt -= end - t;
        return true;
    }

    public static Component outcome() {
        return outcome;
    }

    public static boolean outcomeOk() {
        return outcomeOk;
    }

    public static float sinceOutcome() {
        return Anim.since(outcomeAt);
    }

    // ------------------------------------------------------------------ actions

    public static void send(int action, CompoundTag args) {
        PacketDistributor.sendToServer(new RaidActionPayload(action, args));
    }

    public static void send(int action) {
        send(action, new CompoundTag());
    }

    /** Stands in a raid for the UI test driver (screenshots without other players). */
    public static void demo(RaidSyncPayload fake) {
        onSync(fake);
    }

    /** Shows a results board for the UI test driver. */
    public static void demoResults(CompoundTag fake, Component title, Component dungeon) {
        onEvent(new RaidEventPayload(RaidEventPayload.RESULTS, fake, title, dungeon));
    }

    private RaidClient() {}
}
