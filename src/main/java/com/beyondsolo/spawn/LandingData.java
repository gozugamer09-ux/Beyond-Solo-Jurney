package com.beyondsolo.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Where the world's first player landed on their random first spawn. With {@code spawn.together} on, everyone who
 * joins after them lands a few blocks from it, so friends start the game side by side instead of each somewhere else
 * in the world. Kept with the overworld's data.
 */
public final class LandingData extends SavedData {
    private static final String NAME = "beyondsolo_landing";

    @Nullable
    private BlockPos landing;

    public static LandingData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(LandingData::new, LandingData::load, null), NAME);
    }

    @Nullable
    public BlockPos landing() {
        return landing;
    }

    public void set(@Nullable BlockPos pos) {
        landing = pos == null ? null : pos.immutable();
        setDirty();
    }

    /** A dry spot 3 to 8 blocks from the landing (the landing itself if none is found), or null with no landing yet. */
    @Nullable
    public BlockPos near(ServerLevel level, RandomSource random) {
        if (landing == null) return null;
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int dist = 3 + random.nextInt(6);
            int x = landing.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = landing.getZ() + (int) Math.round(Math.sin(angle) * dist);
            BlockPos pos = dry(level, x, z);
            if (pos != null && Math.abs(pos.getY() - landing.getY()) <= 6) return pos;
        }
        BlockPos at = dry(level, landing.getX(), landing.getZ());
        return at != null ? at : landing;
    }

    /** The surface at x, z if it is solid and dry, else null. */
    @Nullable
    static BlockPos dry(ServerLevel level, int x, int z) {
        if (!level.getWorldBorder().isWithinBounds(x, z)) return null;
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (y <= level.getMinBuildHeight() + 1) return null;
        if (!level.getFluidState(pos.below()).isEmpty() || !level.getFluidState(pos).isEmpty()) return null;
        if (!level.getBlockState(pos.below()).isSolid()) return null;
        return pos;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (landing != null) tag.put("landing", NbtUtils.writeBlockPos(landing));
        return tag;
    }

    static LandingData load(CompoundTag tag, HolderLookup.Provider registries) {
        LandingData d = new LandingData();
        d.landing = NbtUtils.readBlockPos(tag, "landing").orElse(null);
        return d;
    }
}
