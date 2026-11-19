package com.beyondsolo.client.home;

import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.home.HomeTier;
import com.beyondsolo.network.HomeActionPayload;
import com.beyondsolo.network.HomeEventPayload;
import com.beyondsolo.network.HomeSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the client knows of the hunter's home: the last {@link HomeSyncPayload} (what they own and what the next part
 * asks, their rest, their mother, their trophies and best at the dummies, the party homes they may visit), the
 * gathering in progress, the last moment for the HUD (arrived, gathering broken) and the last answer to a button.
 */
public final class HomeClient {
    /** A party member whose home the hunter may visit. */
    public record Host(UUID id, String name, HomeTier tier, boolean online) {}

    /** The last word from the server. */
    public static final class State {
        public boolean enabled = true, world = true, visits = true;
        public HomeTier tier = HomeTier.NONE;
        public int plot = -1;
        public int[] levels = new int[HomeTier.values().length];
        public int[] ranks = new int[HomeTier.values().length];
        public long[] gold = new long[HomeTier.values().length];
        /** Ticks until they may go home again, as of {@link #syncedAt}. */
        public int ready;
        public int channel;
        /** In their own home; in any home. */
        public boolean home, away;
        /** Whose home they are a guest in (empty: none). */
        public String guestOf = "";
        public boolean mother, motherAwake, story, rested;
        public float best;
        public int[] trophies = new int[0];
        public List<String> kinds = List.of();
        /** How far their home's build is (0..1), or -1 if nothing is going up. */
        public float building = -1;
        public List<Host> hosts = List.of();

        public boolean owns() {
            return tier.atLeast(HomeTier.STUDIO);
        }
    }

    public static final int FLASH_BROKEN = 1, FLASH_ARRIVED = 2;

    private static State state = new State();
    private static long syncedAt;

    private static boolean channeling;
    private static double channelAt = -10;
    private static int channelTicks = 1;
    private static Component channelTo = Component.empty();

    private static int flashKind;
    private static int flashTier;
    private static Component flashText = Component.empty();
    private static double flashAt = -10;

    private static Component outcome = Component.empty();
    private static boolean outcomeOk;
    private static double outcomeAt = -10;

    public static void onSync(HomeSyncPayload p) {
        state = parse(p.data());
        syncedAt = gameTime();
    }

    static State parse(CompoundTag t) {
        State s = new State();
        s.enabled = t.getBoolean("enabled");
        s.world = t.getBoolean("world");
        s.visits = t.getBoolean("visits");
        s.tier = HomeTier.of(t.getInt("tier"));
        s.plot = t.getInt("plot");
        int n = HomeTier.values().length;
        int[] levels = t.getIntArray("levels"), ranks = t.getIntArray("ranks");
        long[] gold = t.getLongArray("gold");
        if (levels.length == n) s.levels = levels;
        if (ranks.length == n) s.ranks = ranks;
        if (gold.length == n) s.gold = gold;
        s.ready = t.getInt("ready");
        s.channel = t.getInt("channel");
        s.home = t.getBoolean("home");
        s.away = t.getBoolean("away");
        s.guestOf = t.getString("guestOf");
        s.mother = t.getBoolean("mother");
        s.motherAwake = t.getBoolean("motherAwake");
        s.story = t.getBoolean("story");
        s.rested = t.getBoolean("rested");
        s.best = t.getFloat("best");
        s.trophies = t.getIntArray("trophies");
        List<String> kinds = new ArrayList<>();
        for (Tag k : t.getList("kinds", Tag.TAG_STRING)) kinds.add(k.getAsString());
        s.kinds = kinds;
        s.building = t.getFloat("building");
        List<Host> hosts = new ArrayList<>();
        ListTag list = t.getList("hosts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            if (!c.hasUUID("id")) continue;
            hosts.add(new Host(c.getUUID("id"), c.getString("name"), HomeTier.of(c.getInt("tier")), c.getBoolean("online")));
        }
        s.hosts = hosts;
        return s;
    }

    public static void onEvent(HomeEventPayload p) {
        switch (p.kind()) {
            case HomeEventPayload.CHANNEL_START -> {
                channeling = true;
                channelAt = Anim.now();
                channelTicks = Math.max(1, p.ticks());
                channelTo = p.text();
                sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 0.7f);
                // going home means standing still: the System window gets out of the way
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof com.beyondsolo.client.screen.SystemScreen) mc.screen.onClose();
            }
            case HomeEventPayload.CHANNEL_CANCEL -> {
                channeling = false;
                flash(FLASH_BROKEN, 0, p.text());
                sound(SoundEvents.AMETHYST_BLOCK_BREAK, 0.9f, 0.6f);
            }
            case HomeEventPayload.ARRIVED -> {
                channeling = false;
                flash(FLASH_ARRIVED, p.ticks(), p.text());
            }
            case HomeEventPayload.LEFT -> {
                channeling = false;
                if (flashKind == FLASH_ARRIVED) flashKind = 0;
            }
            case HomeEventPayload.OUTCOME -> {
                outcome = p.text();
                outcomeOk = p.ticks() == 1;
                outcomeAt = Anim.now();
                if (!outcomeOk) sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.5f);
            }
            default -> {}
        }
    }

    private static void flash(int kind, int tier, Component text) {
        flashKind = kind;
        flashTier = tier;
        flashText = text;
        flashAt = Anim.now();
    }

    private static void sound(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** Forgets everything (logged out). */
    public static void reset() {
        state = new State();
        channeling = false;
        flashKind = 0;
        outcome = Component.empty();
        outcomeAt = -10;
    }

    // ------------------------------------------------------------------ reads

    public static State state() {
        return state;
    }

    /** Ticks until the hunter may go home again (counted down since the last word). */
    public static long readyIn() {
        return Math.max(0, state.ready - (gameTime() - syncedAt));
    }

    public static boolean channeling() {
        return channeling;
    }

    /** How far along the gathering is, 0..1. */
    public static float channel() {
        return Math.min(1f, Anim.since(channelAt) * 20f / channelTicks);
    }

    public static Component channelTo() {
        return channelTo;
    }

    public static int flashKind() {
        return flashKind;
    }

    public static HomeTier flashTier() {
        return HomeTier.of(flashTier);
    }

    public static Component flashText() {
        return flashText;
    }

    public static float sinceFlash() {
        return Anim.since(flashAt);
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

    private static long gameTime() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    // ------------------------------------------------------------------ actions

    public static void send(int action) {
        PacketDistributor.sendToServer(new HomeActionPayload(action, new CompoundTag()));
    }

    public static void visit(UUID host) {
        CompoundTag t = new CompoundTag();
        t.putUUID("host", host);
        PacketDistributor.sendToServer(new HomeActionPayload(HomeActionPayload.VISIT, t));
    }

    /** Stands in a home for the UI test driver (screenshots without a server's word). */
    public static void demo(CompoundTag fake) {
        onSync(new HomeSyncPayload(fake));
    }

    private HomeClient() {}
}
