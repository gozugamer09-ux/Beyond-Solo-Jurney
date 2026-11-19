package com.beyondsolo.spawn;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.evaluation.VillageInjector;
import com.beyondsolo.gate.GateSpawner;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.system.Objectives;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.system.SystemEvent;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The way to the first village. The System, once bound, sends a newcomer to find a village and touch the crystal in its
 * Evaluation Zone; it also marks the nearest village that has one, so they don't have to wander: a column of light over
 * the crystal, an arrow and the distance on the objective chip, and a waypoint on Xaero's minimap and world map
 * ({@code client.hud.ObjectiveWaypoint}). The mark is the player's objective target ({@link SystemAPI#setObjectiveTarget})
 * and goes with the objective when they are evaluated.
 *
 * <p><b>Finding it.</b> The village grid (the placements of the {@code #minecraft:village} structures) is walked ring by
 * ring around the player, the way {@code /locate} does it, and every village met is planned as far as its pieces
 * ({@link ChunkStatus#STRUCTURE_STARTS}: no blocks yet) to see whether one of them is an Evaluation Zone. About seven
 * villages in eight have one. A walk takes at most a few milliseconds a tick, so a long one never stalls the server, and
 * the ring after the first find is walked too (a village one ring out can still be nearer). What each village holds is
 * remembered while the server runs, so friends who start together share the work.</p>
 *
 * <p><b>When.</b> As the objective turns to "find a village" (the welcome after the awakening), and for anyone on that
 * objective with no mark (saves from before; a search that found nothing tries again later or further on). Searches
 * look from the overworld only. Close by, the mark is checked against the world once the crystal's chunk is loaded.
 * A village walked into that has no zone is said to have none, and the mark stays on one that has.</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class VillageGuide {
    /** The Evaluation Zone's template, as the village pools name it. */
    public static final ResourceLocation ZONE = ResourceLocation.parse(VillageInjector.EVALUATION_ZONE);
    /** Rings of the village grid walked at most: 12 rings of 34 chunks is about 6,500 blocks in vanilla. */
    public static final int RINGS = 12;
    /** Time the searches may take each tick, all of them together (nanoseconds). */
    private static final long BUDGET = 4_000_000L;
    /** A search that found nothing is tried again this much later (ticks)... */
    static final int RETRY = 20 * 60 * 5;
    /** ...or as soon as the player is this far (blocks) from where it looked. */
    static final double RETRY_MOVED = 600;
    /** The mark is checked against the world when the player is this near (blocks) and the crystal's chunk is loaded. */
    static final int CHECK_NEAR = 64;
    /** How far (blocks) around a mark a crystal is looked for when the mark is not on one. */
    private static final int CHECK_REACH = 8;

    private static final Map<UUID, Search> SEARCHES = new LinkedHashMap<>();
    /** Players whose last search found nothing: where it looked from and when. */
    private static final Map<UUID, Miss> MISSES = new HashMap<>();
    /** Villages looked at, by the chunk they start in: their crystal, or empty with no Evaluation Zone (or no village). */
    private static final Long2ObjectMap<Optional<BlockPos>> SEEN = new Long2ObjectOpenHashMap<>();
    /** Villages without a zone a player has been told about, by player: the chunks they start in. */
    private static final Map<UUID, LongSet> TOLD_EMPTY = new HashMap<>();

    private record Miss(BlockPos from, long time) {}

    /** A structure placement on the village grid and the village kinds it places. */
    record Spread(RandomSpreadStructurePlacement placement, List<Holder<Structure>> structures) {}

    // ------------------------------------------------------------------ who gets a mark

    /** The player is on their way to be evaluated: they have the System, not yet a rank, and the objective says so. */
    public static boolean wantsMark(ServerPlayer p) {
        SystemData d = SystemAPI.get(p);
        return d.hasSystem() && !d.isAwakened()
                && (d.objective().equals(Objectives.FIND_VILLAGE) || d.objective().equals(Objectives.FIND_EVALUATION_ZONE));
    }

    /** Where the player's mark is, or null. */
    @Nullable
    public static GlobalPos mark(ServerPlayer p) {
        return SystemAPI.get(p).objectiveTarget();
    }

    /**
     * Marks that crystal for the player. {@code announce}: the System says so too, with how far and which way it is (once,
     * when a search finds their first mark).
     */
    public static void point(ServerPlayer p, BlockPos crystal, boolean announce) {
        SystemAPI.setObjectiveTarget(p, GlobalPos.of(Level.OVERWORLD, crystal));
        MISSES.remove(p.getUUID());
        if (!announce) return;
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.QUEST,
                Component.translatable("notify.beyondsolo.village_marked.title"),
                Component.translatable("notify.beyondsolo.village_marked.body", blocks(p, crystal), GateSpawner.direction(p.position(), crystal))));
    }

    /** Takes the player's mark away and stops their search. */
    public static void clear(ServerPlayer p) {
        SEARCHES.remove(p.getUUID());
        MISSES.remove(p.getUUID());
        SystemAPI.setObjectiveTarget(p, null);
    }

    /** A search is walking the grid for the player. */
    public static boolean searching(ServerPlayer p) {
        return SEARCHES.containsKey(p.getUUID());
    }

    /**
     * Starts walking the village grid around the player (replacing a walk already going). False when there is nothing to
     * walk: they are not in the overworld, or the world has no villages (structures off, none placed).
     */
    public static boolean start(ServerPlayer p, boolean announce) {
        if (!p.level().dimension().equals(Level.OVERWORLD)) return false;
        Search s = Search.around(p.serverLevel(), p.blockPosition(), p, announce);
        if (s == null) return false;
        SEARCHES.put(p.getUUID(), s);
        return true;
    }

    /**
     * The nearest Evaluation Zone crystal to {@code from} in the overworld, found at once (the command, tests). Empty
     * when none is within {@link #RINGS} rings.
     */
    public static Optional<BlockPos> findNow(ServerLevel overworld, BlockPos from) {
        Search s = Search.around(overworld, from, null, false);
        if (s == null) return Optional.empty();
        while (!s.done()) s.step(overworld);
        return Optional.ofNullable(s.best);
    }

    // ------------------------------------------------------------------ the walk

    /** One walk of the village grid, ring after ring, a cell at a time. */
    static final class Search {
        @Nullable
        final ServerPlayer player;
        final BlockPos from;
        final boolean announce;
        private final List<Spread> spreads;
        private final long seed;
        private final int cx;
        private final int cz;
        private int ring;
        private int cell;
        @Nullable
        BlockPos best;
        private double bestDist = Double.MAX_VALUE;
        private int foundRing = -1;

        private Search(@Nullable ServerPlayer player, BlockPos from, boolean announce, List<Spread> spreads, long seed) {
            this.player = player;
            this.from = from;
            this.announce = announce;
            this.spreads = spreads;
            this.seed = seed;
            this.cx = SectionPos.blockToSectionCoord(from.getX());
            this.cz = SectionPos.blockToSectionCoord(from.getZ());
        }

        @Nullable
        static Search around(ServerLevel level, BlockPos from, @Nullable ServerPlayer player, boolean announce) {
            if (!level.dimension().equals(Level.OVERWORLD) || !level.structureManager().shouldGenerateStructures()) return null;
            List<Spread> spreads = spreads(level);
            if (spreads.isEmpty()) return null;
            return new Search(player, from, announce, spreads, level.getChunkSource().getGeneratorState().getLevelSeed());
        }

        /** Walked far enough: past the rings, or the ring after the first find is done too. */
        boolean done() {
            return ring > RINGS || (foundRing >= 0 && ring > foundRing + 1);
        }

        /** Looks at the next cell of the grid: the village each placement may put there, if any. */
        void step(ServerLevel level) {
            int[] at = cell(ring, cell);
            for (Spread s : spreads) {
                int spacing = s.placement().spacing();
                ChunkPos chunk = s.placement().getPotentialStructureChunk(seed, cx + spacing * at[0], cz + spacing * at[1]);
                Optional<BlockPos> crystal = examine(level, s, chunk);
                if (crystal.isEmpty()) continue;
                double d = flatDistSqr(from, crystal.get());
                if (d < bestDist) {
                    bestDist = d;
                    best = crystal.get();
                }
                if (foundRing < 0) foundRing = ring;
            }
            if (++cell >= cells(ring)) {
                ring++;
                cell = 0;
            }
        }
    }

    /** The grid cells on a ring: one in the middle, then 8 a ring further out each time. */
    public static int cells(int ring) {
        return ring == 0 ? 1 : 8 * ring;
    }

    /** The {@code i}th cell on a ring as {x, z} steps from the middle: round the square, a side at a time. */
    public static int[] cell(int ring, int i) {
        if (ring == 0) return new int[]{0, 0};
        int side = i / (2 * ring), along = i % (2 * ring);
        return switch (side) {
            case 0 -> new int[]{-ring + along, -ring};
            case 1 -> new int[]{ring, -ring + along};
            case 2 -> new int[]{ring - along, ring};
            default -> new int[]{-ring, ring - along};
        };
    }

    /** The placements the world's villages are spread by, each with the village kinds it places. */
    static List<Spread> spreads(ServerLevel level) {
        Optional<HolderSet.Named<Structure>> villages = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getTag(StructureTags.VILLAGE);
        if (villages.isEmpty()) return List.of();
        Map<RandomSpreadStructurePlacement, List<Holder<Structure>>> by = new LinkedHashMap<>();
        for (Holder<Structure> village : villages.get()) {
            for (StructurePlacement placement : level.getChunkSource().getGeneratorState().getPlacementsForStructure(village)) {
                if (placement instanceof RandomSpreadStructurePlacement spread) by.computeIfAbsent(spread, k -> new ArrayList<>()).add(village);
            }
        }
        List<Spread> out = new ArrayList<>();
        by.forEach((placement, kinds) -> out.add(new Spread(placement, List.copyOf(kinds))));
        return out;
    }

    /**
     * The Evaluation Zone crystal of the village the placement may start in that chunk: empty when no village starts
     * there or it has no zone. Planned only as far as its pieces; remembered.
     */
    static Optional<BlockPos> examine(ServerLevel level, Spread spread, ChunkPos chunk) {
        Optional<BlockPos> known = SEEN.get(chunk.toLong());
        if (known != null) return known;
        Optional<BlockPos> found = Optional.empty();
        StructureManager structures = level.structureManager();
        for (Holder<Structure> kind : spread.structures()) {
            if (structures.checkStructurePresence(chunk, kind.value(), spread.placement(), false) == StructureCheckResult.START_NOT_PRESENT) continue;
            ChunkAccess planned = level.getChunk(chunk.x, chunk.z, ChunkStatus.STRUCTURE_STARTS);
            StructureStart start = structures.getStartForStructure(SectionPos.bottomOf(planned), kind.value(), planned);
            if (start == null || !start.isValid()) continue;
            found = Optional.ofNullable(crystalIn(level, start));
            break;
        }
        SEEN.put(chunk.toLong(), found);
        return found;
    }

    /**
     * The Evaluation Zone crystal among a village's pieces, or null when none of them is an Evaluation Zone. The crystal
     * is where the zone's template puts it, turned the way the piece was: the same sum the village is built with.
     */
    @Nullable
    public static BlockPos crystalIn(ServerLevel level, StructureStart village) {
        for (StructurePiece piece : village.getPieces()) {
            if (!(piece instanceof PoolElementStructurePiece pool) || !isZone(pool.getElement())) continue;
            StructureTemplate template = level.getStructureManager().getOrCreate(ZONE);
            List<StructureTemplate.StructureBlockInfo> crystals = template.filterBlocks(pool.getPosition(),
                    new StructurePlaceSettings().setRotation(pool.getRotation()), ModBlocks.EVALUATION_CRYSTAL.get());
            if (!crystals.isEmpty()) return crystals.get(0).pos();
            // a zone whose template lost its crystal: its middle, on its floor
            BoundingBox box = pool.getBoundingBox();
            return new BlockPos(box.getCenter().getX(), box.minY() + 1, box.getCenter().getZ());
        }
        return null;
    }

    /** A piece built from the Evaluation Zone's template. */
    public static boolean isZone(StructurePoolElement element) {
        return element instanceof SinglePoolElement single && single.template.left().filter(ZONE::equals).isPresent();
    }

    // ------------------------------------------------------------------ in the village

    /**
     * The player walked into a village while looking for one. True when it has an Evaluation Zone: the mark moves onto
     * its crystal. False when it has none: the System says so (once a village) and the mark stays on one that has.
     */
    public static boolean enteredVillage(ServerPlayer p, StructureStart village) {
        BlockPos crystal = crystalIn(p.serverLevel(), village);
        if (crystal != null) {
            SystemAPI.setObjectiveTarget(p, GlobalPos.of(p.level().dimension(), crystal));
            return true;
        }
        if (TOLD_EMPTY.computeIfAbsent(p.getUUID(), k -> new LongOpenHashSet()).add(village.getChunkPos().toLong())) {
            GlobalPos mark = mark(p);
            Component body = mark != null && mark.dimension().equals(p.level().dimension())
                    ? Component.translatable("notify.beyondsolo.village_no_zone.body.marked", blocks(p, mark.pos()), GateSpawner.direction(p.position(), mark.pos()))
                    : Component.translatable("notify.beyondsolo.village_no_zone.body");
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.village_no_zone.title"), body));
        }
        return false;
    }

    /**
     * Near the mark, with its chunk loaded: is the crystal there? If it stands a few blocks off the mark moves onto it;
     * if there is none at all (taken away, a template changed) that village is forgotten and the search starts again.
     * True when the mark is on a crystal.
     */
    public static boolean check(ServerPlayer p) {
        GlobalPos mark = mark(p);
        if (mark == null || !mark.dimension().equals(p.level().dimension())) return false;
        ServerLevel level = p.serverLevel();
        BlockPos at = mark.pos();
        if (!level.isLoaded(at)) return false;
        if (level.getBlockState(at).is(ModBlocks.EVALUATION_CRYSTAL.get())) return true;
        BlockPos near = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-CHECK_REACH, -CHECK_REACH, -CHECK_REACH), at.offset(CHECK_REACH, CHECK_REACH, CHECK_REACH))) {
            if (!level.isLoaded(pos) || !level.getBlockState(pos).is(ModBlocks.EVALUATION_CRYSTAL.get())) continue;
            double d = pos.distSqr(at);
            if (d < best) {
                best = d;
                near = pos.immutable();
            }
        }
        if (near != null) {
            SystemAPI.setObjectiveTarget(p, GlobalPos.of(mark.dimension(), near));
            return true;
        }
        for (Long2ObjectMap.Entry<Optional<BlockPos>> e : SEEN.long2ObjectEntrySet()) {
            if (e.getValue().filter(at::equals).isPresent()) e.setValue(Optional.empty());
        }
        SystemAPI.setObjectiveTarget(p, null);
        start(p, true);
        return false;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    static void objectiveChanged(SystemEvent.ObjectiveChanged event) {
        if (!Objectives.FIND_VILLAGE.equals(event.getNewObjective())) return;
        ServerPlayer p = event.getPlayer();
        if (!wantsMark(p)) return;
        MISSES.remove(p.getUUID());
        // the welcome has just set it: the System marks the way as it speaks (the objective is set before it syncs)
        if (event.getData().objectiveTarget() == null && !searching(p)) start(p, true);
    }

    @SubscribeEvent
    static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 40 != 20 || !wantsMark(p)) return;
        GlobalPos mark = mark(p);
        if (mark == null) {
            if (!searching(p) && mayLookAgain(p)) start(p, true);
            return;
        }
        if (mark.dimension().equals(p.level().dimension()) && flatDistSqr(p.blockPosition(), mark.pos()) < CHECK_NEAR * CHECK_NEAR) check(p);
    }

    /** No search found anything for them lately, from about here. */
    private static boolean mayLookAgain(ServerPlayer p) {
        Miss miss = MISSES.get(p.getUUID());
        if (miss == null) return true;
        return p.serverLevel().getGameTime() - miss.time() > RETRY || flatDistSqr(p.blockPosition(), miss.from()) > RETRY_MOVED * RETRY_MOVED;
    }

    @SubscribeEvent
    static void serverTick(ServerTickEvent.Post event) {
        if (SEARCHES.isEmpty()) return;
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        long end = System.nanoTime() + BUDGET;
        Iterator<Search> it = SEARCHES.values().iterator();
        while (it.hasNext() && System.nanoTime() < end) {
            Search s = it.next();
            while (!s.done() && System.nanoTime() < end) s.step(overworld);
            if (!s.done()) break;
            it.remove();
            finish(s);
        }
    }

    /** A walk is done: the player gets the nearest crystal it found, or is looked for again later. */
    private static void finish(Search s) {
        ServerPlayer p = s.player;
        if (p == null || p.isRemoved() || p.hasDisconnected() || !wantsMark(p) || mark(p) != null) return;
        if (s.best == null) {
            MISSES.put(p.getUUID(), new Miss(s.from, p.serverLevel().getGameTime()));
            BeyondSolo.LOGGER.info("No village with an Evaluation Zone within {} rings of {} for {}", RINGS, s.from.toShortString(), p.getGameProfile().getName());
            return;
        }
        point(p, s.best, s.announce);
    }

    @SubscribeEvent
    static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SEARCHES.remove(event.getEntity().getUUID());
        TOLD_EMPTY.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void stopped(ServerStoppedEvent event) {
        SEARCHES.clear();
        MISSES.clear();
        SEEN.clear();
        TOLD_EMPTY.clear();
    }

    // ------------------------------------------------------------------ helpers

    static double flatDistSqr(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** Blocks from the player to there, on the map. */
    static int blocks(ServerPlayer p, BlockPos to) {
        return Mth.floor(Math.sqrt(GateSpawner.flatDistSqr(p.position(), to)) + 0.5);
    }

    /** What the village chunks hold so far, for /solo village info: villages looked at, and how many of them have a zone. */
    public static int[] seen() {
        int zones = 0;
        for (Optional<BlockPos> v : SEEN.values()) if (v.isPresent()) zones++;
        return new int[]{SEEN.size(), zones};
    }

    /** Forgets what the village chunks hold (tests). */
    static void forget() {
        SEEN.clear();
    }

    private VillageGuide() {}
}
