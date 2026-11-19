package com.beyondsolo.association;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The association keeps a post at every Evaluation Zone: an officer beside the crystal, and a gate broker hanging
 * about outside. They come when a player is near a crystal that has none (so villages from worlds made before the
 * association had them get theirs too), and come back if one is ever gone. Only in the overworld, never on the
 * game-test server (whose test plots are Evaluation Zones).
 */
public final class AssociationPosts {
    /** A crystal looks for its people this often (ticks), each crystal at its own moment. */
    public static final int CHECK = 100;
    /** Someone must be this near (blocks) for a post to be manned. */
    public static final int NEAR = 48;
    /** A post's people are found this far (blocks) from its crystal. */
    public static final int REACH = 24;

    /** Checks in a row a crystal found nobody at its post, by crystal: someone comes on the second (entities load late). */
    private static final Map<Long, int[]> MISSING = new HashMap<>();

    /** From the Evaluation Crystal's server ticker. */
    public static void crystalTick(Level world, BlockPos pos) {
        if (!(world instanceof ServerLevel level) || (level.getGameTime() + Math.floorMod(pos.asLong(), CHECK)) % CHECK != 0) return;
        if (!level.dimension().equals(Level.OVERWORLD) || !AssociationConfig.posts()) return;
        if (level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer) return;
        if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, NEAR, p -> !p.isSpectator()) == null) return;
        int[] missing = MISSING.computeIfAbsent(pos.asLong(), k -> new int[2]);
        man(level, pos, AssociationNpc.Role.OFFICER, missing, 0);
        man(level, pos, AssociationNpc.Role.BROKER, missing, 1);
        if (missing[0] == 0 && missing[1] == 0) MISSING.remove(pos.asLong());
    }

    private static void man(ServerLevel level, BlockPos crystal, AssociationNpc.Role role, int[] missing, int i) {
        if (at(level, crystal, role) != null) {
            missing[i] = 0;
            return;
        }
        if (++missing[i] < 2) return;
        missing[i] = 0;
        spawn(level, crystal, role);
    }

    /** The one keeping that post at the crystal, or null. */
    @Nullable
    public static AssociationNpc at(ServerLevel level, BlockPos crystal, AssociationNpc.Role role) {
        for (AssociationNpc n : level.getEntitiesOfClass(AssociationNpc.class, new AABB(crystal).inflate(REACH),
                e -> e.isAlive() && e.role() == role && crystal.equals(e.post()))) {
            return n;
        }
        return null;
    }

    /** Puts someone at the post now (an officer beside the crystal, a broker outside). Null if nowhere would hold them. */
    @Nullable
    public static AssociationNpc spawn(ServerLevel level, BlockPos crystal, AssociationNpc.Role role) {
        BlockPos spot = spot(level, crystal, role);
        if (spot == null) return null;
        EntityType<AssociationNpc> type = role == AssociationNpc.Role.BROKER ? AssociationModule.BROKER.get() : AssociationModule.OFFICER.get();
        AssociationNpc npc = type.create(level);
        if (npc == null) return null;
        npc.setup(crystal, level.random);
        // the officer faces out, the way visitors come in; the broker faces the crystal
        Vec3 face = role == AssociationNpc.Role.OFFICER
                ? Vec3.atBottomCenterOf(spot).add(Vec3.atLowerCornerOf(front(level, crystal).getNormal()))
                : Vec3.atBottomCenterOf(crystal);
        float yaw = (float) (Mth.atan2(face.z - (spot.getZ() + 0.5), face.x - (spot.getX() + 0.5)) * Mth.RAD_TO_DEG) - 90f;
        npc.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        level.addFreshEntity(npc);
        return npc;
    }

    /**
     * The open side of the zone round a crystal: the way with no wall three blocks out (the zone's front), or north when
     * none is open (a crystal someone set down on their own).
     */
    public static Direction front(ServerLevel level, BlockPos crystal) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos edge = crystal.relative(d, 3);
            if (free(level, edge) && free(level, crystal.relative(d, 2))) return d;
        }
        return Direction.NORTH;
    }

    /**
     * Where someone of that role stands at a post: the officer inside the zone two blocks from the crystal, on the side
     * towards its front; the broker on open ground six to ten blocks out. Null if no spot would hold them.
     */
    @Nullable
    public static BlockPos spot(ServerLevel level, BlockPos crystal, AssociationNpc.Role role) {
        if (role == AssociationNpc.Role.OFFICER) {
            Direction front = front(level, crystal);
            List<BlockPos> ring = new ArrayList<>();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) continue;
                    ring.add(crystal.offset(dx, 0, dz));
                }
            }
            // nearest the front first, beside the way in rather than on it
            ring.sort((a, b) -> Double.compare(score(a, crystal, front), score(b, crystal, front)));
            for (BlockPos p : ring) {
                for (int dy = 0; dy >= -1; dy--) {
                    if (stand(level, p.above(dy))) return p.above(dy);
                }
            }
            return null;
        }
        RandomSource r = level.random;
        for (int attempt = 0; attempt < 40; attempt++) {
            float a = r.nextFloat() * Mth.TWO_PI;
            int d = 6 + r.nextInt(5);
            BlockPos column = BlockPos.containing(crystal.getX() + 0.5 + Mth.cos(a) * d, 0, crystal.getZ() + 0.5 + Mth.sin(a) * d);
            if (!level.isLoaded(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (Math.abs(top.getY() - crystal.getY()) > 6) continue;
            if (stand(level, top)) return top;
        }
        return null;
    }

    private static double score(BlockPos p, BlockPos crystal, Direction front) {
        int dx = p.getX() - crystal.getX(), dz = p.getZ() - crystal.getZ();
        double along = dx * front.getStepX() + dz * front.getStepZ();
        double across = Math.abs(dx * front.getStepZ()) + Math.abs(dz * front.getStepX());
        // two blocks towards the front, one to the side: beside the way in
        return Math.abs(along - 2) * 2 + Math.abs(across - 1);
    }

    private static boolean free(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /** Solid footing, two free blocks above it, no water. */
    static boolean stand(ServerLevel level, BlockPos p) {
        BlockState below = level.getBlockState(p.below());
        return below.isFaceSturdy(level, p.below(), Direction.UP) && free(level, p) && free(level, p.above())
                && level.getFluidState(p).isEmpty() && level.getFluidState(p.below()).isEmpty();
    }

    /** Server stopped: forget the counts. */
    static void clear() {
        MISSING.clear();
    }

    private AssociationPosts() {}
}
