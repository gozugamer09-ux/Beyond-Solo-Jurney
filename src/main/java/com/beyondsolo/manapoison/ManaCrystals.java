package com.beyondsolo.manapoison;

import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Grows the purifying crystals into a freshly carved high-rank cave (a step of {@code gate.DungeonBuilder}, after the
 * depths): two in an A gate, three in an S gate ({@link ManaPoisonConfig#crystals}), each in a different cavern, side
 * room or passage, never the entrance, the boss's hall, the hidden chamber or a trap room. Which rooms follows from
 * the seed alone; where in a room, from the rock there.
 */
public final class ManaCrystals {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    public static void build(ServerLevel level, GateInstance g) {
        if (!ManaPoisonConfig.enabled() || !ManaPoisoning.dense(g)) return;
        for (BlockPos at : spots(level, g)) level.setBlock(at, ManaPoisonModule.CRYSTAL.get().defaultBlockState(), FLAGS);
    }

    /** The rooms a gate's crystals grow in. */
    public static List<DungeonLayout.Room> rooms(GateInstance g) {
        DungeonLayout layout = g.layout();
        Map<Integer, ?> traps = g.depths().traps(g);
        List<DungeonLayout.Room> pool = new ArrayList<>();
        for (DungeonLayout.Room room : layout.rooms()) {
            DungeonLayout.Kind kind = room.kind();
            if (kind != DungeonLayout.Kind.CAVERN && kind != DungeonLayout.Kind.SIDE && kind != DungeonLayout.Kind.PASSAGE) continue;
            if (traps.containsKey(room.index())) continue;
            pool.add(room);
        }
        RandomSource r = new LegacyRandomSource(g.seed() ^ 0x50555249465931L);
        // side rooms first (a quiet corner to rest in), then the rest; shuffled within each
        List<DungeonLayout.Room> shuffled = new ArrayList<>(pool);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            DungeonLayout.Room t = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, t);
        }
        shuffled.sort(Comparator.comparingInt(room -> room.kind() == DungeonLayout.Kind.SIDE ? 0 : 1));
        int want = Math.min(ManaPoisonConfig.crystals(g.rank()), shuffled.size());
        return new ArrayList<>(shuffled.subList(0, want));
    }

    /** Where a gate's crystals stand (world positions), as {@link #build} grows them (the same before and after). */
    public static List<BlockPos> spots(ServerLevel level, GateInstance g) {
        List<BlockPos> out = new ArrayList<>();
        for (DungeonLayout.Room room : rooms(g)) {
            BlockPos at = spot(level, g, room);
            if (at != null) out.add(at);
        }
        return out;
    }

    /** A spot on the floor towards the room's edge, with headroom: walked round from a seeded angle. */
    static BlockPos spot(ServerLevel level, GateInstance g, DungeonLayout.Room room) {
        RandomSource r = new LegacyRandomSource(g.seed() * 31 + room.index() * 977L + 5);
        BlockPos c = g.world(room.center());
        int floor = g.world(new BlockPos(0, room.floorY(), 0)).getY();
        float start = r.nextFloat() * Mth.TWO_PI;
        for (float k = 0.6f; k > 0.15f; k -= 0.09f) {
            for (int turn = 0; turn < 8; turn++) {
                float a = start + turn * Mth.TWO_PI / 8;
                BlockPos at = floorAt(level, c.getX() + Math.round(Mth.cos(a) * room.rx() * k), c.getZ() + Math.round(Mth.sin(a) * room.rz() * k), floor);
                if (at != null && open(level.getBlockState(at)) && open(level.getBlockState(at.above()))) return at;
            }
        }
        BlockPos mid = floorAt(level, c.getX(), c.getZ(), floor);
        return mid != null && open(level.getBlockState(mid)) ? mid : null;
    }

    private static BlockPos floorAt(ServerLevel level, int x, int z, int y) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos q = new BlockPos(x, y + dy, z);
            if (open(level.getBlockState(q)) && level.getBlockState(q.below()).isFaceSturdy(level, q.below(), net.minecraft.core.Direction.UP)) return q;
        }
        return null;
    }

    /** Room to stand a crystal in (a crystal already there counts, so the spots read the same once it has grown). */
    private static boolean open(BlockState s) {
        return s.isAir() || s.is(ManaPoisonModule.CRYSTAL.get()) || s.canBeReplaced() && s.getFluidState().isEmpty();
    }

    private ManaCrystals() {}
}
