package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.spawn.VillageGuide;
import com.beyondsolo.system.Objectives;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The mark on the way to the first village ({@link VillageGuide}). The test server makes no villages (structures are
 * off), so the zone is put down by hand the way a village builds it, and the mark's life is played through by hand.
 */
@GameTestHolder(BeyondSolo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageGuideGameTests {
    private static final String ARENA = "evaluation_zone";
    /** The arena's own crystal (test structures sit on a one-block plate). */
    private static final BlockPos CRYSTAL = new BlockPos(3, 2, 3);

    /**
     * Wherever a village put its Evaluation Zone, turned any of the four ways, the mark is on its crystal: the zone is
     * built with the village's own sum (the template turned about its corner), and the crystal is found where it stands.
     * A village without a zone has no crystal to mark.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theMarkIsOnTheZonesCrystalWhicheverWayItTurns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureTemplate zone = level.getStructureManager().getOrCreate(VillageGuide.ZONE);
        Vec3i size = zone.getSize();
        helper.assertTrue(size.getX() > 0 && size.getZ() > 0, "the zone's template is there: " + size);
        int step = Math.max(size.getX(), size.getZ()) * 2 + 8;
        BlockPos origin = far(helper, 30000, -30000);
        List<ChunkPos> chunks = claim(level, origin, -step, Rotation.values().length * step + step);
        try {
            int y = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, origin).getY() + 2;
            StructurePoolElement element = StructurePoolElement.single(VillageGuide.ZONE.toString()).apply(StructureTemplatePool.Projection.RIGID);
            StructurePoolElement house = StructurePoolElement.single("minecraft:village/plains/houses/plains_small_house_1")
                    .apply(StructureTemplatePool.Projection.RIGID);
            helper.assertTrue(VillageGuide.isZone(element) && !VillageGuide.isZone(house), "only the zone's own pieces count as zones");
            var village = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.VILLAGE_PLAINS);
            for (int i = 0; i < Rotation.values().length; i++) {
                Rotation turn = Rotation.values()[i];
                BlockPos at = new BlockPos(origin.getX() + i * step, y, origin.getZ());
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(turn);
                zone.placeInWorld(level, at, at, settings, level.getRandom(), 2);
                BoundingBox box = zone.getBoundingBox(settings, at);
                BlockPos placed = crystalIn(level, box);
                helper.assertTrue(placed != null, "the zone turned " + turn + " stands with its crystal");

                List<StructurePiece> pieces = new ArrayList<>();
                pieces.add(new PoolElementStructurePiece(level.getStructureManager(), house, at.offset(0, 0, step), 0, turn,
                        new BoundingBox(at.getX(), y, at.getZ() + step, at.getX() + 6, y + 6, at.getZ() + step + 6), LiquidSettings.IGNORE_WATERLOGGING));
                StructureStart without = new StructureStart(village, new ChunkPos(at), 0, new PiecesContainer(List.copyOf(pieces)));
                helper.assertTrue(VillageGuide.crystalIn(level, without) == null, "a village of houses alone has no crystal to mark");
                pieces.add(new PoolElementStructurePiece(level.getStructureManager(), element, at, 0, turn, box, LiquidSettings.IGNORE_WATERLOGGING));
                StructureStart with = new StructureStart(village, new ChunkPos(at), 0, new PiecesContainer(pieces));
                BlockPos marked = VillageGuide.crystalIn(level, with);
                helper.assertTrue(placed.equals(marked), "turned " + turn + ": the mark " + marked + " is on the crystal at " + placed);
            }
        } finally {
            release(level, chunks);
        }
        helper.succeed();
    }

    /**
     * The mark's life: only a newcomer on the way to an evaluation gets one; it is saved and sent with the System; close
     * by it settles onto the crystal a few blocks off; a mark with no crystal near is let go; walking into the village
     * keeps it; the evaluation takes it away with the objective.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void theMarkComesWithTheWayAndGoesWithTheEvaluation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = player(helper);
        BlockPos crystal = helper.absolutePos(CRYSTAL);
        helper.assertBlockPresent(ModBlocks.EVALUATION_CRYSTAL.get(), CRYSTAL);
        helper.assertTrue(!VillageGuide.wantsMark(p), "no System, no mark");

        SystemAPI.grantSystem(p);
        SystemAPI.setObjective(p, Objectives.FIND_VILLAGE);
        helper.assertTrue(VillageGuide.wantsMark(p), "the System sends them to a village: they want the way");
        // the test world makes no villages: nothing to walk, nothing marked
        helper.assertTrue(!VillageGuide.searching(p) && VillageGuide.mark(p) == null, "no villages, no search and no mark");

        VillageGuide.point(p, crystal, false);
        GlobalPos mark = VillageGuide.mark(p);
        helper.assertTrue(mark != null && mark.pos().equals(crystal) && mark.dimension().equals(level.dimension()), "marked: " + mark);
        CompoundTag saved = SystemAPI.get(p).serializeNBT(level.registryAccess());
        SystemData loaded = new SystemData();
        loaded.deserializeNBT(level.registryAccess(), saved);
        helper.assertTrue(mark.equals(loaded.objectiveTarget()), "saved and sent with the System: " + loaded.objectiveTarget());

        // a mark a few blocks off the crystal settles onto it once its chunk is loaded
        SystemAPI.setObjectiveTarget(p, GlobalPos.of(level.dimension(), crystal.offset(3, -1, 2)));
        helper.assertTrue(VillageGuide.check(p), "the crystal is found near the mark");
        helper.assertTrue(crystal.equals(VillageGuide.mark(p).pos()), "and the mark is on it: " + VillageGuide.mark(p));

        // in the village, looking for the zone: the mark stays
        SystemAPI.setObjective(p, Objectives.FIND_EVALUATION_ZONE);
        helper.assertTrue(VillageGuide.mark(p) != null && VillageGuide.wantsMark(p), "the mark stays while they look for the zone");

        // the evaluation ends the objective, and the mark with it
        SystemAPI.awaken(p, PlayerRank.E, PlayerClass.WARRIOR);
        helper.assertTrue(VillageGuide.mark(p) == null && !VillageGuide.wantsMark(p), "evaluated: no mark");
        CompoundTag after = SystemAPI.get(p).serializeNBT(level.registryAccess());
        helper.assertTrue(!after.contains("objectiveTarget"), "and none saved");
        helper.succeed();
    }

    /** A mark with no crystal anywhere near (taken away, a template changed) is let go instead of leading nowhere. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void aMarkWithNoCrystalNearIsLetGo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = far(helper, 34000, -30000);
        List<ChunkPos> chunks = claim(level, origin, -16, 16);
        try {
            ServerPlayer p = player(helper);
            SystemAPI.grantSystem(p);
            SystemAPI.setObjective(p, Objectives.FIND_VILLAGE);
            BlockPos empty = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, origin);
            p.moveTo(empty.getX() + 0.5, empty.getY(), empty.getZ() + 4.5);
            VillageGuide.point(p, empty, false);
            helper.assertTrue(!VillageGuide.check(p), "no crystal there");
            helper.assertTrue(VillageGuide.mark(p) == null, "so the mark is let go: " + VillageGuide.mark(p));
        } finally {
            release(level, chunks);
        }
        helper.succeed();
    }

    /** The village grid is walked a ring at a time: every cell of a ring once, and only that ring's cells. */
    @GameTest(template = ARENA)
    public static void theWalkGoesRoundEachRingOnce(GameTestHelper helper) {
        for (int ring = 0; ring <= 6; ring++) {
            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < VillageGuide.cells(ring); i++) {
                int[] c = VillageGuide.cell(ring, i);
                helper.assertTrue(Math.max(Math.abs(c[0]), Math.abs(c[1])) == ring, "ring " + ring + " cell " + i + " is on the ring: " + c[0] + "," + c[1]);
                helper.assertTrue(seen.add(ChunkPos.asLong(c[0], c[1])), "ring " + ring + " cell " + i + " comes once");
            }
            int expected = ring == 0 ? 1 : (2 * ring + 1) * (2 * ring + 1) - (2 * ring - 1) * (2 * ring - 1);
            helper.assertTrue(seen.size() == expected, "ring " + ring + " has all its cells: " + seen.size());
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "newcomer"));
        SystemAPI.reset(p);
        p.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(1, 2, 1));
        p.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        return p;
    }

    /** A spot far from every other test. */
    private static BlockPos far(GameTestHelper helper, int dx, int dz) {
        return helper.absolutePos(BlockPos.ZERO).offset(dx, 0, dz);
    }

    /** Loads and holds the chunks from {@code from} to {@code to} blocks along x (and as far each way along z) of a spot. */
    private static List<ChunkPos> claim(ServerLevel level, BlockPos at, int from, int to) {
        List<ChunkPos> chunks = new ArrayList<>();
        int reach = Math.max(Math.abs(from), Math.abs(to));
        for (int x = (at.getX() + from) >> 4; x <= (at.getX() + to) >> 4; x++) {
            for (int z = (at.getZ() - reach) >> 4; z <= (at.getZ() + reach) >> 4; z++) chunks.add(new ChunkPos(x, z));
        }
        for (ChunkPos c : chunks) level.setChunkForced(c.x, c.z, true);
        for (ChunkPos c : chunks) level.getChunk(c.x, c.z);
        TestChunks.settle(level, () -> chunks.stream().allMatch(c -> level.getChunkSource().hasChunk(c.x, c.z)));
        return chunks;
    }

    private static void release(ServerLevel level, List<ChunkPos> chunks) {
        for (ChunkPos c : chunks) level.setChunkForced(c.x, c.z, false);
    }

    private static BlockPos crystalIn(ServerLevel level, BoundingBox box) {
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(pos).is(ModBlocks.EVALUATION_CRYSTAL.get())) return pos.immutable();
        }
        return null;
    }

    private VillageGuideGameTests() {}
}
