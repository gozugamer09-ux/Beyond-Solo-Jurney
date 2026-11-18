package com.beyondsolo.client.worldboss;

import com.beyondsolo.worldboss.WorldBosses;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The lie of the land under a warning: the top of the ground in each column near it, found the way the server finds
 * where its blows land ({@link WorldBosses#groundY}: leaves, logs, plants and water skipped) and kept, so a warning on a
 * hillside lies on the hill.
 */
final class Terrain {
    private final Long2FloatOpenHashMap tops = new Long2FloatOpenHashMap();
    private float nearY;

    Terrain(float nearY) {
        this.nearY = nearY;
        tops.defaultReturnValue(Float.NaN);
    }

    /** Looks from another height (a domain that follows its monarch); forgets what it knew when it grows large. */
    void around(float y) {
        if (Math.abs(y - nearY) > 3 || tops.size() > 30_000) {
            tops.clear();
            nearY = y;
        }
    }

    /** The top of the ground in one column. */
    float column(int bx, int bz) {
        long key = ((long) bx << 32) ^ (bz & 0xFFFFFFFFL);
        float v = tops.get(key);
        if (!Float.isNaN(v)) return v;
        v = scan(bx, bz);
        tops.put(key, v);
        return v;
    }

    private float scan(int bx, int bz) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.hasChunk(bx >> 4, bz >> 4)) return nearY;
        int top = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(nearY) + 12);
        int bottom = Math.max(level.getMinBuildHeight(), Mth.floor(nearY) - 24);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(bx, top, bz);
        for (int y = top; y >= bottom; y--) {
            m.setY(y);
            BlockState s = level.getBlockState(m);
            if (WorldBosses.isGround(s)) {
                double h = s.getCollisionShape(level, m).max(Direction.Axis.Y);
                return (float) (y + (Double.isFinite(h) && h > 0 ? h : 1));
            }
        }
        return nearY;
    }

    /** The height at a point: the highest of the four columns round it, so what lies on the ground is never buried in it. */
    float at(double x, double z) {
        int x0 = Mth.floor(x - 0.5), z0 = Mth.floor(z - 0.5);
        return Math.max(Math.max(column(x0, z0), column(x0 + 1, z0)), Math.max(column(x0, z0 + 1), column(x0 + 1, z0 + 1)));
    }
}
