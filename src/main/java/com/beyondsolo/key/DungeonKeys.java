package com.beyondsolo.key;

import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateFeature;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.KeyFxPayload;
import com.beyondsolo.network.KeySyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Dungeon keys: one per rank, each opens a gate of its rank wherever its holder stands, so a hunter can run a gate
 * whenever they like instead of waiting for one to tear open nearby.
 *
 * <p>Turning a key takes {@link #USE_TICKS} of holding it out (or the TURN button in the GATES tab): a lock of light
 * forms in the air a few steps ahead and cracks, then gives, and a gate opens there. The key is used up. The gate is a
 * door into a fresh cave of the key's rank for its holder and anyone with them ({@link KeyedRuns}): it seals itself
 * {@link #SEAL_TICKS} after the first one goes through, and the way out at the end leads back to where the key was
 * turned. A gate nobody enters in {@link #UNUSED_TICKS} (counted while its holder is online) closes and the key comes
 * back.</p>
 *
 * <p>A key can also be turned in a cleared cave, before going home: the next gate opens right there (the re-run
 * prompt turns one of the same rank with a single press). Runs opened one straight after another form a chain; each
 * run in a chain pays {@link #CHAIN_BONUS} more XP and gold, up to {@link #CHAIN_MAX} steps, and the whole chain
 * still goes home to where its first key was turned.</p>
 *
 * <p>A key can carry a seal ({@link KeySeal}): the boss it was taken from, so its gate ends with that boss again, and
 * an ascendant seal brings that boss ascended. Numbers are first guesses; the balancing pass tunes them.</p>
 */
public final class DungeonKeys {
    /** Ticks of holding a key out before it turns. */
    public static final int USE_TICKS = 30;
    /** A key can be at most this many ranks above its holder's. */
    public static final int RANK_REACH = 1;
    /** A keyed gate nobody has entered closes after this long (while its holder is online) and the key comes back. */
    public static final int UNUSED_TICKS = 20 * 120;
    /** A keyed gate seals this long after the first one goes through (time for the party to follow). */
    public static final int SEAL_TICKS = 20 * 10;
    /** Extra XP and gold for each run in a chain after the first. */
    public static final float CHAIN_BONUS = 0.10f;
    /** The chain bonus stops growing after this many steps. */
    public static final int CHAIN_MAX = 5;
    /** What the System shop pays for a plain key, by rank E..S. A sealed key is worth more, an ascendant one more again. */
    public static final long[] SELL = {60, 160, 420, 1100, 2400, 4500};
    public static final float SEALED_SELL = 1.5f;
    public static final float ASCENDANT_SELL = 2f;
    /** How far ahead of its holder a key's gate opens (it tries a little nearer and further if that spot is taken). */
    public static final int AHEAD = 3;

    /** Why a key won't turn. */
    public enum Refusal {
        /** The player has not awakened. */
        ASLEEP,
        /** The key is too far above the player's rank. */
        RANK,
        /** Somewhere a gate can't open: the Wastes, the job change trial, the gate depths outside a cave. */
        PLACE,
        /** In a cave whose boss still stands. */
        UNCLEARED,
        /** The player's last keyed gate is still standing open, unentered. */
        WAITING,
        /** No room ahead for a gate (it needs solid ground and a clear space three wide and five tall). */
        ROOM,
        /** Already turning a key. */
        BUSY;

        public String key() {
            return "key.beyondsolo.refuse." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Where a key's gate opens: its block, and the way it faces (towards the key's holder). */
    public record Spot(ServerLevel level, BlockPos pos, Direction facing) {
        public Vec3 center() {
            return new Vec3(pos.getX() + 0.5, pos.getY() + GateBlockEntity.CENTER_Y, pos.getZ() + 0.5);
        }
    }

    /**
     * Set by the client at setup: the same refusal worked out from what the client knows, so the hold doesn't start
     * when the server will refuse. The server always decides.
     */
    public static BiFunction<Player, ItemStack, Refusal> clientCheck = (p, s) -> null;

    /** Players turning a key: where its gate will open. */
    private static final Map<UUID, Spot> PENDING = new HashMap<>();
    /** Players turning a key from the GATES tab or the re-run prompt (no hold): when it started. */
    private static final Map<UUID, Long> TURNING = new HashMap<>();

    // ================================================================ stacks

    public static ItemStack key(PlayerRank rank) {
        return new ItemStack(KeyModule.KEYS.get(rank.tier()).get());
    }

    /** A key bound to a boss (null for a plain key), maybe ascendant. */
    public static ItemStack sealed(PlayerRank rank, @Nullable BossKind boss, boolean ascendant) {
        ItemStack stack = key(rank);
        if (boss != null || ascendant) stack.set(KeyModule.SEAL.get(), KeySeal.of(boss, ascendant));
        return stack;
    }

    public static boolean isKey(ItemStack stack) {
        return stack.getItem() instanceof DungeonKeyItem;
    }

    public static PlayerRank rankOf(ItemStack stack) {
        return stack.getItem() instanceof DungeonKeyItem key ? key.rank() : PlayerRank.E;
    }

    public static KeySeal sealOf(ItemStack stack) {
        KeySeal seal = stack.get(KeyModule.SEAL.get());
        return seal == null ? KeySeal.of(null, false) : seal;
    }

    /** 0 plain, 1 sealed to a boss, 2 ascendant. */
    public static int style(ItemStack stack) {
        return sealOf(stack).style();
    }

    /** The colour of a key's light: its rank's, warmed to gold when ascendant. */
    public static int color(PlayerRank rank, int style) {
        return style == 2 ? 0xFFD36B : rank.color() & 0xFFFFFF;
    }

    /** What the System shop pays for one key, or -1 if this is not a key. */
    public static long sellPrice(ItemStack stack) {
        if (!isKey(stack)) return -1;
        long base = SELL[rankOf(stack).tier()];
        return switch (style(stack)) {
            case 2 -> Math.round(base * (double) ASCENDANT_SELL);
            case 1 -> Math.round(base * (double) SEALED_SELL);
            default -> base;
        };
    }

    // ================================================================ refusals

    /** What the client can tell on its own: awakened, and the key not too far above. */
    public static @Nullable Refusal refusal(SystemData sys, ItemStack key) {
        if (!sys.isAwakened()) return Refusal.ASLEEP;
        if (rankOf(key).tier() > sys.rank().tier() + RANK_REACH) return Refusal.RANK;
        return null;
    }

    public static @Nullable Refusal refusal(ServerPlayer p, ItemStack key) {
        Refusal r = refusal(SystemAPI.get(p), key);
        if (r != null) return r;
        ServerLevel level = p.serverLevel();
        if (level.dimension().equals(com.beyondsolo.penalty.PenaltyZone.DIMENSION) || com.beyondsolo.job.JobTrial.inTrial(p)
                || com.beyondsolo.tower.TowerAPI.inCitadel(p)) return Refusal.PLACE;
        GateInstance host = host(p);
        if (host != null) {
            if (host.isTrial()) return Refusal.PLACE;
            if (host.state() == GateInstance.State.OPEN) return Refusal.UNCLEARED;
        } else if (level.dimension().equals(GateService.DIMENSION)) {
            return Refusal.PLACE;
        }
        if (waiting(p) != null) return Refusal.WAITING;
        return null;
    }

    /** The cave the player is standing in, if any. */
    public static @Nullable GateInstance host(ServerPlayer p) {
        return GateService.isDungeon(p.level()) ? GateData.get(p.server).bySlotPos(p.blockPosition()) : null;
    }

    /** The player's keyed gate that stands open with nobody through it yet, if any. */
    public static @Nullable GateInstance waiting(ServerPlayer p) {
        for (GateInstance g : GateData.get(p.server).all()) {
            if (!g.isKeyed() || g.slotFreed() || g.state() != GateInstance.State.OPEN) continue;
            GateInstance.Keyed k = g.keyed();
            if (k.owner.equals(p.getUUID()) && !k.entered() && !k.doorClosed) return g;
        }
        return null;
    }

    /** Tells the player the key won't turn: a dull clunk and a line on the action bar; the hold stops. */
    public static void refuse(ServerPlayer p, ItemStack key, Refusal r) {
        PacketDistributor.sendToPlayer(p, new KeyFxPayload(KeyFxPayload.REFUSE, p.getId(), p.getX(), p.getY(), p.getZ(), 0,
                rankOf(key).ordinal(), style(key), 0));
        p.displayClientMessage(Component.translatable(r.key(), rankOf(key).displayName(),
                PlayerRank.values()[Math.max(0, rankOf(key).tier() - RANK_REACH)].displayName()), true);
        sound(p.serverLevel(), p.position(), SoundEvents.VAULT_INSERT_ITEM_FAIL, 1f, 0.8f);
    }

    // ================================================================ where the gate opens

    /** A spot a few steps ahead with solid ground and room for a gate, facing back at the player; null if none. */
    public static @Nullable Spot findSpot(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Direction ahead = p.getDirection();
        BlockPos feet = p.blockPosition();
        int[] dists = {AHEAD, AHEAD + 1, AHEAD - 1, AHEAD + 2};
        int[] sides = {0, 1, -1};
        int[] heights = {0, 1, -1, 2, -2, 3, -3};
        for (int d : dists) {
            for (int s : sides) {
                for (int dy : heights) {
                    BlockPos pos = feet.relative(ahead, d).relative(ahead.getClockWise(), s).above(dy);
                    if (good(level, pos)) return new Spot(level, pos, ahead.getOpposite());
                }
            }
        }
        return null;
    }

    private static boolean good(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos) || !GateFeature.goodSpot(level, pos)) return false;
        // not on top of another gate still standing (an exit gate in a cleared cave, a gate already here); one that has
        // closed and gone doesn't count, though its record lingers until its cave is wiped
        for (GateInstance g : GateData.get(level.getServer()).all()) {
            if (g.slotFreed()) continue;
            if (g.gateDimension().equals(level.dimension()) && g.gatePos().distSqr(pos) < 36 && standing(level, g.gatePos(), g)) return false;
            if (g.exitPos() != null && GateService.isDungeon(level) && g.exitPos().distSqr(pos) < 36 && standing(level, g.exitPos(), g)) return false;
        }
        return true;
    }

    /** A gate of this record stands at the spot (open or still closing). */
    private static boolean standing(ServerLevel level, BlockPos at, GateInstance g) {
        return !level.isLoaded(at) || level.getBlockEntity(at) instanceof GateBlockEntity door && g.id().equals(door.gateId());
    }

    // ================================================================ turning

    /** Starting to turn a key (the hold, or a turn from the menu): true if it may go on, else the player is told why. */
    public static boolean begin(ServerPlayer p, ItemStack key) {
        Refusal r = PENDING.containsKey(p.getUUID()) ? Refusal.BUSY : refusal(p, key);
        Spot spot = r == null ? findSpot(p) : null;
        if (r == null && spot == null) r = Refusal.ROOM;
        if (r != null) {
            refuse(p, key, r);
            return false;
        }
        PENDING.put(p.getUUID(), spot);
        Vec3 c = spot.center();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new KeyFxPayload(KeyFxPayload.TURN, p.getId(), c.x, c.y, c.z,
                spot.facing().toYRot(), rankOf(key).ordinal(), style(key), USE_TICKS));
        sound(p.serverLevel(), c, SoundEvents.VAULT_INSERT_ITEM, 1.2f, 0.7f);
        sound(p.serverLevel(), c, SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.5f);
        return true;
    }

    /** The turn stopped short (let go, swapped items, walked off). */
    public static void cancel(ServerPlayer p) {
        Spot spot = PENDING.remove(p.getUUID());
        if (spot == null) return;
        Vec3 c = spot.center();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new KeyFxPayload(KeyFxPayload.CANCEL, p.getId(), c.x, c.y, c.z,
                spot.facing().toYRot(), 0, 0, 0));
        sound(p.serverLevel(), c, SoundEvents.VAULT_CLOSE_SHUTTER, 0.8f, 0.6f);
    }

    /** The lock cracks as the key turns (a third and two thirds through). */
    static void crack(ServerPlayer p, int held) {
        Spot spot = PENDING.get(p.getUUID());
        if (spot == null || (held != USE_TICKS / 3 && held != USE_TICKS * 2 / 3)) return;
        float pitch = held < USE_TICKS / 2 ? 0.8f : 1.05f;
        sound(p.serverLevel(), spot.center(), SoundEvents.CHAIN_BREAK, 1.2f, pitch);
        sound(p.serverLevel(), spot.center(), SoundEvents.AMETHYST_BLOCK_CHIME, 1f, pitch);
    }

    /**
     * The key has turned: its gate opens (where the lock formed, or the nearest good spot if that one is taken now).
     * Returns false (and the player is told why) if it can't; the caller uses the key up on true.
     */
    public static boolean finish(ServerPlayer p, ItemStack key) {
        Spot spot = PENDING.remove(p.getUUID());
        Refusal r = refusal(p, key);
        if (r == null) {
            if (spot == null || spot.level() != p.serverLevel() || !good(spot.level(), spot.pos())) spot = findSpot(p);
            if (spot == null) r = Refusal.ROOM;
        }
        if (r != null) {
            refuse(p, key, r);
            return false;
        }
        open(p, key, spot);
        return true;
    }

    /** Opens a key's gate at a spot, for the player (commands and tests use it directly). */
    public static GateBlockEntity open(ServerPlayer p, ItemStack key, Spot spot) {
        MinecraftServer server = p.server;
        PlayerRank rank = rankOf(key);
        KeySeal seal = sealOf(key);
        // where the run goes home to: here, or from a cleared cave, wherever that cave's own way home leads
        GateInstance host = host(p);
        int chain = host != null && host.isKeyed() ? host.keyed().chain + 1 : 1;
        GateService.Home home = host != null ? GateService.home(server, host, p.getYRot())
                : new GateService.Home(p.level().dimension(), p.position(), p.getYRot());
        GateInstance.Keyed keyed = new GateInstance.Keyed(p.getUUID(), chain, home.dimension(), home.pos(), home.yaw(),
                seal.boss(), seal.ascendant(), server.overworld().getGameTime());
        GateBlockEntity gate = GateService.spawnKeyedGate(spot.level(), spot.pos(), spot.facing(), rank, keyed);
        Vec3 c = spot.center();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new KeyFxPayload(KeyFxPayload.OPEN, p.getId(), c.x, c.y, c.z,
                spot.facing().toYRot(), rank.ordinal(), seal.style(), 0));
        sound(spot.level(), c, SoundEvents.VAULT_OPEN_SHUTTER, 1.4f, 0.6f);
        sound(spot.level(), c, SoundEvents.VAULT_ACTIVATE, 1.2f, 0.8f);
        KeyRunData d = data(p);
        d.turned[rank.tier()]++;
        sync(p);
        if (chain > 1) {
            p.displayClientMessage(Component.translatable("key.beyondsolo.chain", chain, Math.round(bonusFor(chain) * 100 - 100)), true);
        }
        return gate;
    }

    /**
     * Turns the key in an inventory slot without the hold (the GATES tab's TURN, the re-run prompt): the lock forms
     * and cracks just the same, and the gate opens when the turn is done unless the player has moved off or the key
     * is gone from the slot.
     */
    public static void turnFromInventory(ServerPlayer p, int slot) {
        Inventory inv = p.getInventory();
        if (slot < 0 || slot >= inv.getContainerSize() || p.isUsingItem() || TURNING.containsKey(p.getUUID())) return;
        ItemStack key = inv.getItem(slot);
        if (!isKey(key) || !begin(p, key)) return;
        ItemStack want = key.copyWithCount(1);
        Vec3 from = p.position();
        UUID id = p.getUUID();
        TURNING.put(id, p.server.overworld().getGameTime());
        for (int t = USE_TICKS / 3; t < USE_TICKS; t += USE_TICKS / 3) {
            int held = t;
            ServerScheduler.schedule(held, () -> {
                if (TURNING.containsKey(id) && !p.isRemoved()) crack(p, held);
            });
        }
        ServerScheduler.schedule(USE_TICKS, () -> {
            TURNING.remove(id);
            if (p.isRemoved() || !p.isAlive() || p.position().distanceToSqr(from) > 2.5 * 2.5) {
                cancel(p);
                return;
            }
            ItemStack now = p.getInventory().getItem(slot);
            if (!ItemStack.isSameItemSameComponents(now, want)) {
                cancel(p);
                return;
            }
            if (finish(p, now)) now.consume(1, p);
        });
    }

    /** True while the player is turning a key from the menu (the client animates it like a hold). */
    public static boolean turning(Player p) {
        return TURNING.containsKey(p.getUUID());
    }

    /** The re-run prompt: in a cleared cave, turn a key of the cave's rank for the next run, right here. */
    public static void rerun(ServerPlayer p) {
        GateInstance host = host(p);
        if (host == null || host.isTrial() || host.state() == GateInstance.State.OPEN) return;
        int slot = rerunSlot(p, host);
        if (slot < 0) {
            p.displayClientMessage(Component.translatable("key.beyondsolo.no_key", host.rank().displayName()), true);
            return;
        }
        turnFromInventory(p, slot);
    }

    /**
     * The inventory slot of the key the re-run prompt turns, -1 for none: one of the cave's rank, the same seal as the
     * run just cleared first (the same boss again), then a plain one, then any.
     */
    public static int rerunSlot(Player p, GateInstance host) {
        KeySeal same = host.isKeyed() ? KeySeal.of(host.keyed().sigil, host.keyed().ascendant) : KeySeal.of(null, false);
        Inventory inv = p.getInventory();
        int best = -1, bestScore = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!isKey(s) || rankOf(s) != host.rank()) continue;
            KeySeal seal = sealOf(s);
            int score = seal.equals(same) ? 3 : seal.style() == 0 ? 2 : 1;
            if (score > bestScore) {
                best = i;
                bestScore = score;
            }
        }
        return best;
    }

    /** The XP and gold multiplier for a run at this place in its chain. */
    public static float bonusFor(int chain) {
        return 1f + CHAIN_BONUS * Math.min(Math.max(0, chain - 1), CHAIN_MAX);
    }

    // ================================================================ refunds, records

    /** A keyed gate closed with nobody through it: its key goes back to its holder. */
    static void refund(ServerPlayer owner, GateInstance g) {
        GateInstance.Keyed k = g.keyed();
        ItemStack key = sealed(g.rank(), k.sigil, k.ascendant);
        Notifications.send(owner, SystemNotification.of(SystemNotification.Kind.INFO,
                Component.translatable("notify.beyondsolo.key.refund.title"),
                Component.translatable("notify.beyondsolo.key.refund.body", key.getHoverName())));
        if (!owner.getInventory().add(key) && !key.isEmpty()) owner.drop(key, false);
        PacketDistributor.sendToPlayer(owner, new KeyFxPayload(KeyFxPayload.REFUND, owner.getId(), owner.getX(), owner.getY(), owner.getZ(), 0,
                g.rank().ordinal(), KeySeal.of(k.sigil, k.ascendant).style(), 0));
        sound(owner.serverLevel(), owner.position(), SoundEvents.VAULT_EJECT_ITEM, 1f, 0.9f);
    }

    public static KeyRunData data(Player p) {
        return p.getData(KeyModule.RUNS);
    }

    public static void sync(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new KeySyncPayload(data(p).serializeNBT(p.registryAccess())));
    }

    static void forget(UUID player) {
        PENDING.remove(player);
        TURNING.remove(player);
    }

    static void clear() {
        PENDING.clear();
        TURNING.clear();
    }

    static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private DungeonKeys() {}
}
