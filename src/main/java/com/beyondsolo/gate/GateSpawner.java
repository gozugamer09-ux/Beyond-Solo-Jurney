package com.beyondsolo.gate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.awakening.AwakeningData;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.network.GatePayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.registry.ModAttachments;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gates tear open around players while they play (world generation places more, often out of sight). Once a minute
 * ({@link #tick}), for each player in the overworld ({@link #minute}):
 *
 * <ul>
 * <li><b>An evaluated hunter.</b> A gate standing within {@link #NEAR} blocks that they have not seen yet is pointed out
 * to them (a System alert: its rank, how far, which way). Otherwise, while fewer than {@code gates.maxOpenNearby} gates
 * are open around them, a new one may open 40 to 80 blocks away: a chance each minute ({@code gates.openChancePerMinute}),
 * and for sure once {@code gates.guaranteeMinutes} have gone by without one. Its rank is rolled at random, whoever the
 * player is ({@link GateService#randomRank}): an E-rank hunter can find an S-rank gate on their doorstep. The player
 * feels it open (a tremor) and is told how far away it is and which way.</li>
 * <li><b>A player without the System</b> (before the awakening): the same with D-rank gates only, the gates that can
 * call them ({@code awakening.gateChancePerMinute}, {@code awakening.gateGuaranteeMinutes}), one at a time. They are
 * told in a line of thought in the chat, not by the System, which they don't have yet.</li>
 * <li><b>Someone the System has bound but not evaluated.</b> Gates turn them away, so none open for them; once they have
 * waited as long as a gate would take, a System alert tells them why (once a session).</li>
 * </ul>
 *
 * <p>Anyone's very first gate opens in front of them, closer, within a minute and a half of playing, so they watch it
 * tear open. Until 0.1.2 any open gate within 160 blocks, seen or not, stopped new ones, and nothing ever opened for
 * sure: a player could wait ten minutes and more next to a world-gen gate behind a hill and never learn it was there.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class GateSpawner {
    /** Gates within this many blocks (on the map) of a player are around them. */
    public static final int NEAR = 160;
    /** No new gate opens closer than this to another one. */
    public static final int SPACING = 32;
    /** Ticks a player has to have been in the game (since they logged in or came back from death) before a gate opens. */
    public static final int SETTLE_TICKS = 20 * 30;
    /** A first gate opens this far away (blocks), in front of the player. */
    public static final int VIEW_MIN = 22, VIEW_MAX = 36;
    /** Other gates open this far away (blocks), any way round. */
    public static final int RING_MIN = 40, RING_MAX = 80;

    /** What a minute brought a player. */
    public enum Outcome {
        /** Nothing for them: elsewhere than the overworld, a spectator, going through a gate, or awakening. */
        SKIPPED,
        /** A gate opened near them. */
        OPENED,
        /** A gate around them they had not seen was pointed out. */
        POINTED,
        /** Not this minute. */
        WAITING,
        /** As many gates are open around them as may be. */
        FULL,
        /** A gate was due but no spot around them would hold one (open water, thick forest): next minute, then. */
        NO_SPOT,
        /** The System has them but has not evaluated them: no gate lets them in, so none opens. */
        LOCKED
    }

    /**
     * How gates come to one kind of player: the chance each minute, the quiet minutes after which one opens for sure (0:
     * only by chance), and how many may be open around them at once.
     */
    public record Rules(double chance, int guarantee, int maxNear) {
        /** Evaluated hunters, from the config; null when gates opening near players is turned off. */
        @Nullable
        public static Rules hunters() {
            double chance = Config.GATE_SPAWN_CHANCE.get();
            return chance <= 0 ? null : new Rules(chance, Config.GATE_GUARANTEE_MINUTES.get(), Config.GATE_MAX_NEARBY.get());
        }

        /** Players without the System and their D-rank gates, from the config; null when turned off. */
        @Nullable
        public static Rules unawakened() {
            double chance = Config.PRE_SYSTEM_GATE_CHANCE.get();
            return chance <= 0 ? null : new Rules(chance, Config.PRE_SYSTEM_GATE_GUARANTEE.get(), 1);
        }
    }

    /** Gates each player was told of (opened for them, or pointed out), so none is pointed out twice. */
    private static final Map<UUID, Set<UUID>> TOLD = new HashMap<>();
    /** Players told this session why no gate opens for them. */
    private static final Set<UUID> TOLD_LOCKED = new HashSet<>();

    /** Once a minute (from {@link GateService}'s server tick). */
    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Rules hunters = Rules.hunters(), unawakened = Rules.unawakened();
        for (ServerPlayer p : List.copyOf(level.players())) minute(level, p, hunters, unawakened, level.random);
    }

    /** One minute for one player, by the config's rules. */
    public static Outcome minute(ServerLevel level, ServerPlayer p) {
        return minute(level, p, Rules.hunters(), Rules.unawakened(), level.random);
    }

    /** One minute for one player, by the rules given (null: none open for that kind of player). */
    public static Outcome minute(ServerLevel level, ServerPlayer p, @Nullable Rules hunters, @Nullable Rules unawakened, RandomSource r) {
        if (p.isSpectator() || !p.isAlive() || p.level() != level || !level.dimension().equals(Level.OVERWORLD) || GateService.inTransit(p)) {
            return Outcome.SKIPPED;
        }
        if (!SystemAPI.hasSystem(p)) {
            // not yet awakened: D-rank gates keep opening near them until one has called them
            if (unawakened == null || AwakeningService.stage(p) != AwakeningData.Stage.DORMANT) return Outcome.SKIPPED;
            return step(level, p, unawakened, PlayerRank.D, r);
        }
        if (!SystemAPI.isAwakened(p)) return locked(p, hunters);
        return hunters == null ? Outcome.SKIPPED : step(level, p, hunters, null, r);
    }

    private static Outcome step(ServerLevel level, ServerPlayer p, Rules rules, @Nullable PlayerRank only, RandomSource r) {
        List<GateInstance> near = openGatesNear(level.getServer(), p.blockPosition(), NEAR, only);
        // a gate that is there already but out of their sight is pointed out first, nearest first
        GateInstance unseen = null;
        double best = Double.MAX_VALUE;
        for (GateInstance g : near) {
            double d = flatDistSqr(p.position(), g.gatePos());
            if (d < best && !knows(p, g)) {
                best = d;
                unseen = g;
            }
        }
        if (unseen != null) {
            pointOut(p, unseen);
            setQuiet(p, 0);
            return Outcome.POINTED;
        }
        if (near.size() >= rules.maxNear()) return Outcome.FULL;
        // nothing tears open while they are still loading in
        if (p.tickCount < SETTLE_TICKS) return Outcome.WAITING;
        int was = quiet(p);
        boolean first = was < 0;
        int quiet = Math.max(0, was) + 1;
        boolean due = first || (rules.guarantee() > 0 && quiet >= rules.guarantee()) || r.nextDouble() < rules.chance();
        if (!due) {
            setQuiet(p, quiet);
            return Outcome.WAITING;
        }
        if (trySpawnNear(level, p, only, first) == null) {
            // a first gate stays a first gate; any other stays due
            if (!first) setQuiet(p, Math.max(quiet, rules.guarantee()));
            return Outcome.NO_SPOT;
        }
        setQuiet(p, 0);
        return Outcome.OPENED;
    }

    /** The System has them but not their measure yet: gates won't let them in, and they are told so once. */
    private static Outcome locked(ServerPlayer p, @Nullable Rules hunters) {
        int quiet = Math.max(0, quiet(p)) + 1;
        setQuiet(p, quiet);
        int wait = hunters != null && hunters.guarantee() > 0 ? hunters.guarantee() : 6;
        if (quiet >= wait && TOLD_LOCKED.add(p.getUUID())) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.gate.locked.title"),
                    Component.translatable("notify.beyondsolo.gate.locked.body")));
        }
        return Outcome.LOCKED;
    }

    /** Tells the player where a gate they have not seen stands: by the System, or as a feeling if they have none. */
    private static void pointOut(ServerPlayer p, GateInstance g) {
        told(p, g.id());
        int blocks = Mth.floor(Math.sqrt(flatDistSqr(p.position(), g.gatePos())) + 0.5);
        Component dir = direction(p.position(), g.gatePos());
        if (SystemAPI.hasSystem(p)) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.gate.near.title", g.rank().displayName()),
                    Component.translatable("notify.beyondsolo.gate.near.body", blocks, dir)));
        } else {
            thought(p, Component.translatable("message.beyondsolo.presystem.gate_near", blocks, dir));
        }
    }

    /** A line of the player's own thoughts in the chat: for those the System has not bound yet. */
    private static void thought(ServerPlayer p, Component text) {
        p.sendSystemMessage(text.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    // ------------------------------------------------------------------ what is around

    /**
     * The gates open around {@code pos} in the overworld: not closed or cleared, not broken (a broken gate is closed by
     * its boss, out here), not a sealed trial or a keyed run (those are somebody's own). With {@code only}, just those of
     * that rank.
     */
    public static List<GateInstance> openGatesNear(MinecraftServer server, BlockPos pos, int range, @Nullable PlayerRank only) {
        List<GateInstance> out = new ArrayList<>();
        Vec3 at = Vec3.atCenterOf(pos);
        for (GateInstance g : GateData.get(server).all()) {
            if (g.state() != GateInstance.State.OPEN || g.breakState().hasBroken() || g.isTrial() || g.isKeyed()) continue;
            if (only != null && g.rank() != only) continue;
            if (g.gateDimension().equals(Level.OVERWORLD) && flatDistSqr(at, g.gatePos()) < (double) range * range) out.add(g);
        }
        return out;
    }

    /** True if a gate that can still be entered stands within {@code range}. */
    public static boolean openGateNear(MinecraftServer server, BlockPos pos, int range) {
        return !openGatesNear(server, pos, range, null).isEmpty();
    }

    /** True if the player has seen this gate: come near it, or been told of it (it opened for them, or was pointed out). */
    public static boolean knows(ServerPlayer p, GateInstance g) {
        Set<UUID> told = TOLD.get(p.getUUID());
        return told != null && told.contains(g.id()) || GateService.noticed(p, g.id());
    }

    private static void told(ServerPlayer p, UUID gate) {
        TOLD.computeIfAbsent(p.getUUID(), k -> new HashSet<>()).add(gate);
    }

    /** True if the player was told this session why no gate opens for them. */
    public static boolean toldLocked(ServerPlayer p) {
        return TOLD_LOCKED.contains(p.getUUID());
    }

    /** Minutes in the overworld since a gate last opened near the player or was pointed out to them; -1 before their first. */
    public static int quiet(ServerPlayer p) {
        return p.getData(ModAttachments.GATE_QUIET);
    }

    public static void setQuiet(ServerPlayer p, int minutes) {
        p.setData(ModAttachments.GATE_QUIET, minutes);
    }

    // ------------------------------------------------------------------ opening one

    /** Opens a gate of a random rank somewhere around the player. Returns null if no good spot was found. */
    @Nullable
    public static GateBlockEntity trySpawnNear(ServerLevel level, ServerPlayer p) {
        return trySpawnNear(level, p, null, false);
    }

    /**
     * Opens a gate around the player, of rank {@code fixed} (null: rolled at random). {@code inView}: in front of them
     * and closer if a spot is there ({@link #VIEW_MIN} to {@link #VIEW_MAX} blocks, within 35 degrees of where they
     * look), so they watch it tear open; otherwise {@link #RING_MIN} to {@link #RING_MAX} blocks any way round. Never
     * nearer than {@link #SPACING} to another gate, never where the chunks aren't loaded. Returns null if no spot would
     * hold it.
     */
    @Nullable
    public static GateBlockEntity trySpawnNear(ServerLevel level, ServerPlayer p, @Nullable PlayerRank fixed, boolean inView) {
        RandomSource r = level.random;
        // beyond the view distance the world isn't loaded around them (a server that keeps none, the game-test one, has no limit)
        int view = level.getServer().getPlayerList().getViewDistance();
        int reach = view > 0 ? Math.max(VIEW_MIN, view * 16 - 8) : RING_MAX;
        BlockPos top = null;
        for (int attempt = 0; attempt < 32 && top == null; attempt++) {
            boolean front = inView && attempt < 12;
            float a = front ? lookAngle(p) + (r.nextFloat() - 0.5f) * 70 * Mth.DEG_TO_RAD : r.nextFloat() * Mth.TWO_PI;
            int d = Math.min(reach, front ? VIEW_MIN + r.nextInt(VIEW_MAX - VIEW_MIN + 1) : RING_MIN + r.nextInt(RING_MAX - RING_MIN + 1));
            BlockPos column = BlockPos.containing(p.getX() + Mth.cos(a) * d, 0, p.getZ() + Mth.sin(a) * d);
            if (!level.isLoaded(column)) continue;
            BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (!GateFeature.goodSpot(level, spot) || gateWithin(level.getServer(), spot, SPACING)) continue;
            top = spot;
        }
        if (top == null) return null;
        PlayerRank rank = fixed != null ? fixed : GateService.randomRank(r);
        Direction facing = Direction.getNearest(p.getX() - top.getX(), 0, p.getZ() - top.getZ());
        if (facing.getAxis().isVertical()) facing = Direction.NORTH;
        GateBlockEntity gate = GateService.spawnGate(level, top, rank, facing, true);
        if (gate.gateId() != null) told(p, gate.gateId());
        // the world shudders as it tears, harder the higher the gate, and they hear it from where they stand
        PacketDistributor.sendToPlayer(p, new GatePayload(GatePayload.SHAKE, 0, -1, 0.18f + 0.05f * rank.tier(), Component.empty(), Component.empty()));
        p.playNotifySound(SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, 0.3f, 0.7f);
        int blocks = Mth.floor(Math.sqrt(flatDistSqr(p.position(), top)) + 0.5);
        Component dir = direction(p.position(), top);
        if (SystemAPI.hasSystem(p)) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.gate.opened.title", rank.displayName()),
                    Component.translatable("notify.beyondsolo.gate.opened.body", blocks, dir)));
        } else {
            thought(p, Component.translatable("message.beyondsolo.presystem.gate_opened", blocks, dir));
        }
        return gate;
    }

    /** True if a gate not yet closed stands within {@code range} blocks of {@code pos} in the overworld. */
    private static boolean gateWithin(MinecraftServer server, BlockPos pos, int range) {
        Vec3 at = Vec3.atCenterOf(pos);
        for (GateInstance g : GateData.get(server).all()) {
            if (g.state() == GateInstance.State.CLOSED || !g.gateDimension().equals(Level.OVERWORLD)) continue;
            if (flatDistSqr(at, g.gatePos()) < (double) range * range) return true;
        }
        return false;
    }

    /** The angle for {@code cos} (x) and {@code sin} (z) that points the way the player looks. */
    public static float lookAngle(ServerPlayer p) {
        return p.getYRot() * Mth.DEG_TO_RAD + Mth.HALF_PI;
    }

    /** Squared distance on the map (heights left out) from {@code from} to the middle of the block {@code to}. */
    public static double flatDistSqr(Vec3 from, BlockPos to) {
        double dx = to.getX() + 0.5 - from.x, dz = to.getZ() + 0.5 - from.z;
        return dx * dx + dz * dz;
    }

    /** The compass direction from {@code from} to {@code to}, as a word ("north-east"). */
    public static Component direction(Vec3 from, BlockPos to) {
        return com.beyondsolo.npc.NpcQuests.direction((float) Mth.atan2(to.getZ() + 0.5 - from.z, to.getX() + 0.5 - from.x));
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        TOLD.clear();
        TOLD_LOCKED.clear();
    }

    private GateSpawner() {}
}
