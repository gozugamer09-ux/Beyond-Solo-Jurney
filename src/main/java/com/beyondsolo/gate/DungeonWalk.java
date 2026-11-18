package com.beyondsolo.gate;

import com.beyondsolo.depths.HiddenSealBlock;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * Getting about a dungeon on foot, the way a player does: walking, stepping or jumping up one block where there's
 * head room, dropping off an edge no more than three blocks (a fall that doesn't hurt), never swimming. The builder
 * checks its work by it ({@link DungeonMend}); the game tests walk a dungeon by the same rules. Positions are where the
 * feet are, as {@link BlockPos#asLong()}.
 */
public final class DungeonWalk {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** What a block is to someone on foot (0: not looked at yet). */
    private static final byte OPEN = 1, FLAT = 2, LOW = 3, SOLID = 4, LIQUID = 5, WET = 6;

    private final BlockGetter level;
    private final boolean wallDown;
    private final Long2ByteOpenHashMap kinds = new Long2ByteOpenHashMap();
    private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
    private LongPredicate barred = p -> false;

    /**
     * @param wallDown the hidden chamber's wall counts as open, as it is once it has come down
     */
    public DungeonWalk(BlockGetter level, boolean wallDown) {
        this.level = level;
        this.wallDown = wallDown;
    }

    /** Never steps where {@code barred} says (the builder keeps out of the hidden chamber this way). */
    DungeonWalk barring(LongPredicate barred) {
        this.barred = barred;
        return this;
    }

    private byte kind(long p) {
        byte k = kinds.get(p);
        if (k != 0) return k;
        at.set(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p));
        BlockState s = level.getBlockState(at);
        if (wallDown && s.getBlock() instanceof HiddenSealBlock) {
            k = OPEN;
        } else {
            VoxelShape shape = s.getCollisionShape(level, at);
            boolean wet = !s.getFluidState().isEmpty();
            if (shape.isEmpty()) {
                k = wet ? LIQUID : OPEN;
            } else if (wet) {
                k = WET;
            } else {
                double top = shape.max(Direction.Axis.Y);
                k = top <= 0.0625 ? FLAT : top <= 0.5 ? LOW : SOLID;
            }
        }
        kinds.put(p, k);
        return k;
    }

    /** Forgets what a block was (it has just been changed). */
    void forget(long p) {
        kinds.remove(p);
    }

    /** Nothing to bump into (air, a light, a cobweb, hanging roots...) and no water or lava. */
    public boolean open(long p) {
        return kind(p) == OPEN;
    }

    /** Something to stand on: anything solid at all, down to a carpet (or a waterlogged slab). */
    public boolean floor(long p) {
        byte k = kind(p);
        return k == FLAT || k == LOW || k == SOLID || k == WET;
    }

    /** A liquid, alone or in a block. */
    public boolean liquid(long p) {
        byte k = kind(p);
        return k == LIQUID || k == WET;
    }

    /** Something you stand in, not against: nothing, or something low (a carpet, a slab, a step, snow). */
    public boolean low(long p) {
        byte k = kind(p);
        return k == OPEN || k == FLAT || k == LOW;
    }

    /** Room for a player with their feet in {@code p}: standing on a slab or a step, the head reaches a block higher. */
    public boolean stand(long p) {
        byte k = kind(p);
        if (k != OPEN && k != FLAT && k != LOW) return false;
        if (!open(BlockPos.offset(p, 0, 1, 0))) return false;
        return k != LOW || open(BlockPos.offset(p, 0, 2, 0));
    }

    /** Somewhere a player can stand: room for them, and something under their feet (or a slab, a step, snow in the block). */
    public boolean footing(long p) {
        if (!stand(p)) return false;
        return kind(p) != OPEN || floor(BlockPos.offset(p, 0, -1, 0));
    }

    /** Where one step from {@code c} towards {@code dir} lands: on, off an edge, or up a block. */
    void step(long c, Direction dir, LongConsumer to) {
        LongConsumer out = q -> {
            if (!barred.test(q)) to.accept(q);
        };
        long h = BlockPos.offset(c, dir);
        if (footing(h)) {
            out.accept(h);
        } else if (open(h) && open(BlockPos.offset(h, 0, 1, 0))) {
            for (int k = 1; k <= 3; k++) {
                long q = BlockPos.offset(h, 0, -k, 0);
                if (footing(q)) {
                    out.accept(q);
                    break;
                }
                if (!open(q)) break;
            }
        }
        long up = BlockPos.offset(h, 0, 1, 0);
        if (open(BlockPos.offset(c, 0, 2, 0)) && footing(up)) out.accept(up);
    }

    /** Every step from {@code c}. */
    void steps(long c, LongConsumer out) {
        for (Direction dir : HORIZONTAL) step(c, dir, out);
    }

    /** True if one step takes a player from {@code c} to {@code n}. */
    boolean steps(long c, long n) {
        boolean[] found = {false};
        steps(c, q -> found[0] |= q == n);
        return found[0];
    }

    /** Every spot a player gets to on foot from {@code from} (at most {@code limit}). */
    public LongOpenHashSet reach(BlockPos from, int limit) {
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue todo = new LongArrayFIFOQueue();
        seen.add(from.asLong());
        todo.enqueue(from.asLong());
        while (!todo.isEmpty() && seen.size() < limit) {
            steps(todo.dequeueLong(), q -> {
                if (seen.add(q)) todo.enqueue(q);
            });
        }
        return seen;
    }

    /**
     * The shortest way on foot from {@code from} to a spot {@code goal} takes, in order (both ends included), or null
     * if there is none within {@code limit} spots.
     */
    @Nullable
    public LongArrayList way(BlockPos from, LongPredicate goal, int limit) {
        Long2LongOpenHashMap came = new Long2LongOpenHashMap();
        LongArrayFIFOQueue todo = new LongArrayFIFOQueue();
        long start = from.asLong();
        came.put(start, start);
        todo.enqueue(start);
        long end = Long.MIN_VALUE;
        if (goal.test(start)) end = start;
        while (end == Long.MIN_VALUE && !todo.isEmpty() && came.size() < limit) {
            long c = todo.dequeueLong();
            long[] hit = {Long.MIN_VALUE};
            steps(c, q -> {
                if (came.containsKey(q)) return;
                came.put(q, c);
                todo.enqueue(q);
                if (hit[0] == Long.MIN_VALUE && goal.test(q)) hit[0] = q;
            });
            end = hit[0];
        }
        if (end == Long.MIN_VALUE) return null;
        LongArrayList out = new LongArrayList();
        for (long p = end; ; p = came.get(p)) {
            out.add(0, p);
            if (p == start) break;
        }
        return out;
    }
}
