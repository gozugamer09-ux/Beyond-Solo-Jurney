package com.beyondsolo.tower;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Hooks for game tests that drive a run or the lord by hand. Not for gameplay code. Test players load no chunks, so a
 * test holds its run's halls loaded itself ({@link #hold}); and the lord's moves are started and played out on demand.
 */
public final class TowerTestAccess {
    /**
     * Holds the halls of a run loaded and ticking (both hall pads of its slot, or the colosseum for a rush) and returns
     * the chunks held, for {@link #release}. Empty outside a run.
     */
    public static List<ChunkPos> hold(ServerPlayer p) {
        int slot = TowerRun.slot(p);
        ServerLevel level = TowerRun.citadel(p.server);
        if (slot < 0 || level == null) return List.of();
        boolean rush = TowerRun.mode(p) == TowerRun.Mode.RUSH;
        List<ChunkPos> out = new ArrayList<>();
        for (int pad : rush ? new int[]{CitadelHalls.COLOSSEUM_PAD} : new int[]{0, 1}) {
            BlockPos o = CitadelHalls.origin(slot, pad);
            AABB box = CitadelHalls.layout(rush ? CitadelHalls.Shape.COLOSSEUM : CitadelHalls.Shape.NAVE, o).box();
            for (int cx = Mth.floor(box.minX) >> 4; cx <= (Mth.floor(box.maxX) - 1) >> 4; cx++) {
                for (int cz = Mth.floor(box.minZ) >> 4; cz <= (Mth.floor(box.maxZ) - 1) >> 4; cz++) out.add(new ChunkPos(cx, cz));
            }
        }
        for (ChunkPos c : out) level.setChunkForced(c.x, c.z, true);
        return out;
    }

    public static void release(ServerLevel level, List<ChunkPos> chunks) {
        for (ChunkPos c : chunks) level.setChunkForced(c.x, c.z, false);
    }

    /** Starts one of the lord's moves against a target at once (waking him and skipping his intro if need be). */
    public static void forceMove(CitadelLordEntity lord, CitadelLordEntity.Move move, LivingEntity target) {
        lord.forceMove(move, target);
    }

    /** Plays the lord's current move out to its end in one go (blows due later stay pending: see {@link #pendingStrikes}). */
    public static void finishMove(CitadelLordEntity lord) {
        lord.finishMove();
    }

    /** Blows of the lord's still waiting to land (a lance's further spears, a decree's marks). */
    public static int pendingStrikes(CitadelLordEntity lord) {
        return lord.pendingStrikes();
    }

    private TowerTestAccess() {}
}
