package com.beyondsolo.test;

import com.beyondsolo.BeyondSolo;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Waiting for chunks in a game test. A game-test server runs its turns back to back without pausing, while chunks are
 * generated, loaded and handed over to the world by other threads in their own time: a second of that can be a
 * thousand turns, more than a test's whole timeout. Until a chunk is handed over, a creature added to it is in the world
 * but can't be found there yet. So a test that needs chunks ready (to find the boss a dungeon was just built with, or a
 * wave that just rose) waits for them here, on the server thread and by the clock, running the level's chunk work
 * meanwhile.
 */
final class TestChunks {
    /** How long to wait at most (nanoseconds). */
    private static final long PATIENCE = 15_000_000_000L;

    private TestChunks() {}

    /** Runs the level's chunk work until {@code ready} holds (true) or a while has gone by (false). */
    static boolean settle(ServerLevel level, BooleanSupplier ready) {
        if (ready.getAsBoolean()) return true;
        long start = System.nanoTime();
        while (!ready.getAsBoolean()) {
            if (System.nanoTime() - start > PATIENCE) return false;
            if (!level.getChunkSource().pollTask()) LockSupport.parkNanos(100_000L);
        }
        BeyondSolo.LOGGER.info("waited {} ms for chunks to be ready", (System.nanoTime() - start) / 1_000_000L);
        return true;
    }

    /** Waits until these chunks tick, creatures and all (see {@link #settle}); false if they still don't. */
    static boolean ticking(ServerLevel level, List<ChunkPos> chunks) {
        boolean ok = settle(level, () -> chunks.stream().allMatch(c -> level.isPositionEntityTicking(c.getWorldPosition())));
        if (!ok) BeyondSolo.LOGGER.warn("chunks still not ticking after {} s: {}", PATIENCE / 1_000_000_000L, chunks);
        return ok;
    }
}
