package com.beyondsolo.client.key;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.key.DungeonKeys;
import com.beyondsolo.key.KeyRunData;
import com.beyondsolo.network.KeyActionPayload;
import com.beyondsolo.network.KeyFxPayload;
import com.beyondsolo.network.KeyRunPayload;
import com.beyondsolo.network.KeySyncPayload;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What the client knows about dungeon keys: the local player's keyed-run records, the run under way around them (for
 * the run clock), the last clear (its time under the cleared banner) and the re-run prompt; and where key messages
 * from the server go.
 */
public final class KeyClient {
    private static KeyRunData data = new KeyRunData();

    // ---- the keyed run around the local player
    private static boolean inRun;
    private static int runRank;
    private static int runChain = 1;
    /** Game time the run's clock started (the first arrival). */
    private static long runStarted;
    private static Component dungeon = Component.empty();
    private static double runShownAt = -100;
    private static boolean cleared;
    private static long clearTicks;
    private static long best = -1;
    private static boolean newBest;
    private static double clearedAt = -100;

    // ---- the re-run prompt
    private static boolean rerun;
    private static int rerunRank;
    private static int rerunChain = 1;
    private static int rerunStyle;
    private static Component rerunKey = Component.empty();
    private static double rerunAt = -100;
    /** Where the prompt was offered: it goes away once the player is out of that cave. */
    private static String rerunDimension = "";

    /** When the local player last started turning a key from the menu or the prompt (no hold). */
    private static double menuTurnAt = -100;
    /** Ticks outside the gate depths, to drop a run whose end was missed. */
    private static int awayTicks;

    /**
     * Hooks the shared key rules up to what the client knows (rank, awakening, and a keyed run whose boss still
     * stands). Called once at client setup. The server has the last word.
     */
    static void setup() {
        DungeonKeys.clientCheck = (player, stack) -> {
            DungeonKeys.Refusal r = DungeonKeys.refusal(ClientSystemData.get(), stack);
            if (r == null && inRun && !cleared && player.level().dimension().equals(GateService.DIMENSION)) r = DungeonKeys.Refusal.UNCLEARED;
            return r;
        };
    }

    // ================================================================ from the server

    public static void onSync(KeySyncPayload p) {
        KeyRunData fresh = new KeyRunData();
        fresh.deserializeNBT(null, p.data());
        data = fresh;
    }

    public static void onRun(KeyRunPayload p) {
        switch (p.kind()) {
            case KeyRunPayload.START -> {
                boolean fresh = !inRun || runStarted != p.started();
                inRun = true;
                runRank = p.rank();
                runChain = Math.max(1, p.chain());
                runStarted = p.started();
                dungeon = p.label();
                if (fresh) {
                    runShownAt = Anim.now();
                    cleared = false;
                }
                rerun = false;
                awayTicks = 0;
            }
            case KeyRunPayload.CLEARED -> {
                if (!inRun) runShownAt = Anim.now();
                inRun = true;
                runRank = p.rank();
                runChain = Math.max(1, p.chain());
                runStarted = p.started();
                dungeon = p.label();
                cleared = true;
                clearTicks = p.ticks();
                best = p.best();
                newBest = (p.flags() & KeyRunPayload.NEW_BEST) != 0;
                clearedAt = Anim.now();
            }
            case KeyRunPayload.END -> {
                inRun = false;
                cleared = false;
                rerun = false;
            }
            case KeyRunPayload.RERUN -> {
                rerun = true;
                rerunRank = p.rank();
                rerunChain = Math.max(1, p.chain());
                rerunStyle = p.flags();
                rerunKey = p.label();
                rerunAt = Anim.now();
                Minecraft mc = Minecraft.getInstance();
                rerunDimension = mc.level == null ? "" : mc.level.dimension().location().toString();
            }
            default -> {
            }
        }
    }

    public static void onFx(KeyFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = p.entity() >= 0 ? mc.level.getEntity(p.entity()) : null;
        boolean self = e != null && e == mc.player;
        switch (p.kind()) {
            case KeyFxPayload.TURN -> {
                if (self) {
                    rerun = false;
                    if (!mc.player.isUsingItem()) menuTurnAt = Anim.now();
                }
                KeyAnimator.turn(e);
            }
            case KeyFxPayload.CANCEL -> {
                if (self) menuTurnAt = -100;
                KeyAnimator.stop(e);
            }
            case KeyFxPayload.REFUSE -> {
                // the server said no: stop the hold the client started
                if (self) {
                    menuTurnAt = -100;
                    if (mc.player.isUsingItem() && DungeonKeys.isKey(mc.player.getUseItem())) mc.player.stopUsingItem();
                }
                KeyAnimator.stop(e);
            }
            case KeyFxPayload.OPEN -> {
                if (self) {
                    menuTurnAt = -100;
                    rerun = false;
                }
                KeyAnimator.release(e);
            }
            default -> {
            }
        }
        KeyWorldFx.play(p);
    }

    // ================================================================ asking the server

    /** Turns the key in an inventory slot without the hold (the GATES tab's TURN). */
    public static void turn(int slot) {
        PacketDistributor.sendToServer(new KeyActionPayload(KeyActionPayload.TURN, slot));
        menuTurnAt = Anim.now();
    }

    /** The re-run prompt's key was pressed: true if a prompt was there to take it. */
    public static boolean pressRerun() {
        if (!rerunShowing() || !RunHud.promptShown()) return false;
        PacketDistributor.sendToServer(new KeyActionPayload(KeyActionPayload.RERUN, -1));
        rerun = false;
        menuTurnAt = Anim.now();
        return true;
    }

    // ================================================================ state

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        boolean depths = mc.level.dimension().equals(GateService.DIMENSION);
        awayTicks = depths ? 0 : awayTicks + 1;
        // a run whose end never came (the player was moved out some other way)
        if (inRun && awayTicks > 60) {
            inRun = false;
            cleared = false;
        }
        if (rerun && (!mc.level.dimension().location().toString().equals(rerunDimension) || slotOf(rerunRank) < 0)) rerun = false;
    }

    public static KeyRunData data() {
        return data;
    }

    public static boolean inRun() {
        return inRun;
    }

    public static PlayerRank runRank() {
        return PlayerRank.values()[Mth.clamp(runRank, 0, PlayerRank.values().length - 1)];
    }

    public static int runChain() {
        return runChain;
    }

    public static Component dungeon() {
        return dungeon;
    }

    public static float sinceRunShown() {
        return Anim.since(runShownAt);
    }

    /** Ticks on the run's clock (frozen once cleared). */
    public static float runTicks(float partial) {
        if (cleared) return clearTicks;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        return Math.max(0, mc.level.getGameTime() - runStarted + partial);
    }

    public static boolean cleared() {
        return inRun && cleared;
    }

    public static long clearTicks() {
        return clearTicks;
    }

    public static long best() {
        return best;
    }

    public static boolean newBest() {
        return newBest;
    }

    public static float sinceCleared() {
        return Anim.since(clearedAt);
    }

    /** True for a few seconds after a keyed run's boss fell (the cleared banner that follows shows its time). */
    public static boolean justCleared() {
        return cleared && Anim.since(clearedAt) < 12f;
    }

    public static boolean rerunShowing() {
        return rerun;
    }

    public static PlayerRank rerunRank() {
        return PlayerRank.values()[Mth.clamp(rerunRank, 0, PlayerRank.values().length - 1)];
    }

    public static int rerunChain() {
        return rerunChain;
    }

    public static int rerunStyle() {
        return rerunStyle;
    }

    public static Component rerunKey() {
        return rerunKey;
    }

    public static float sinceRerun() {
        return Anim.since(rerunAt);
    }

    /** When the local player's last turn from the menu or the prompt started (UI time). */
    public static double menuTurnStarted() {
        return menuTurnAt;
    }

    /** True while the local player is turning a key from the menu or the prompt. */
    public static boolean menuTurning() {
        return Anim.since(menuTurnAt) < DungeonKeys.USE_TICKS / 20f + 0.4f;
    }

    /** Why the local player can't turn this key right now, as far as the client can tell (null: it should turn). */
    static @org.jetbrains.annotations.Nullable DungeonKeys.Refusal refusal(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? DungeonKeys.Refusal.ASLEEP : DungeonKeys.clientCheck.apply(mc.player, stack);
    }

    /** The inventory slot of a key of this rank, or -1. */
    static int slotOf(int rank) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (DungeonKeys.isKey(s) && DungeonKeys.rankOf(s).ordinal() == rank) return i;
        }
        return -1;
    }

    /** A time on the run clock: m:ss.t (h:mm:ss past an hour). */
    public static String time(double ticks) {
        double seconds = Math.max(0, ticks) / 20.0;
        int whole = (int) Math.floor(seconds);
        int tenths = (int) Math.floor((seconds - whole) * 10);
        int h = whole / 3600, m = (whole / 60) % 60, s = whole % 60;
        if (h > 0) return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s);
        return String.format(java.util.Locale.ROOT, "%d:%02d.%d", m, s, tenths);
    }

    static void clear() {
        RunHud.reset();
        data = new KeyRunData();
        inRun = false;
        cleared = false;
        rerun = false;
        menuTurnAt = -100;
        awayTicks = 0;
    }

    private KeyClient() {}
}
