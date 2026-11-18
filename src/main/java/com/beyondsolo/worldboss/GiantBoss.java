package com.beyondsolo.worldboss;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A world boss: a giant that walks the land as it is. Too big for paths, it strides over the ground ({@link #stride}):
 * through trees and over rocks and hills, its feet on the ground under it, never into deep water. It roams the land
 * round where it rose ({@link #home()}) until a hunter comes, follows them a long way, and goes back when they flee
 * too far.
 */
public abstract class GiantBoss extends ColossalBoss {
    /** How far from where it rose it roams. */
    public static final int ROAM = 40;
    /** How far from where it rose it follows a hunter before it turns back. */
    public static final int LEASH = 110;

    @Nullable private Vec3 waypoint;
    private int waitTicks = 60;
    private boolean returning;

    protected GiantBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected boolean walksThrough() {
        return true;
    }

    /** Its walking pace (blocks a tick). */
    protected abstract double walkSpeed();

    /** How close it likes to be to its target (from its edge) before it stops to strike. */
    protected double reach() {
        return 3.0;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server) || !isAlive() || leaving() || introTicks() > 0) return;
        // while it doesn't walk, it still stands on whatever is under it
        if (getDeltaMovement().horizontalDistanceSqr() < 1e-4) settle(server);
    }

    /** Keeps its feet on the ground where it stands. */
    protected void settle(ServerLevel level) {
        double y = footY(level, getX(), getZ());
        double dy = Mth.clamp(y - getY(), -0.5, 0.5);
        if (Math.abs(dy) > 0.01) setPos(getX(), getY() + dy, getZ());
        setDeltaMovement(0, 0, 0);
    }

    /** The ground its feet are on at a point: the highest of five spots under its body, so it never sinks into a hill. */
    protected double footY(ServerLevel level, double x, double z) {
        double r = getBbWidth() * 0.35, near = getY();
        double best = WorldBosses.groundY(level, x, z, near);
        double sum = 0;
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.toRadians(getYRot());
            sum += WorldBosses.groundY(level, x + Math.cos(a) * r, z + Math.sin(a) * r, near);
        }
        return Math.max(best, sum / 4);
    }

    /**
     * Takes one step towards {@code dest} at {@code speed}, turning to face where it goes (at most {@code turn} degrees a
     * tick). Returns false when it can't go on (deep water ahead, or its land isn't loaded) or is there.
     */
    public boolean stride(Vec3 dest, double speed, float turn) {
        if (!(level() instanceof ServerLevel server)) return false;
        Vec3 to = dest.subtract(position()).multiply(1, 0, 1);
        double d = to.length();
        if (d < 0.3) {
            settle(server);
            return false;
        }
        Vec3 dir = to.scale(1 / d);
        turnTowards(dir, turn);
        // it walks the way it faces: a giant turns before it goes
        Vec3 facing = Vec3.directionFromRotation(0, getYRot());
        double along = Math.max(0, facing.dot(dir));
        double step = Math.min(d, speed * (0.25 + 0.75 * along));
        double nx = getX() + facing.x * step, nz = getZ() + facing.z * step;
        if (!server.hasChunk(Mth.floor(nx) >> 4, Mth.floor(nz) >> 4) || WorldBosses.waterDepth(server, nx, nz) > 3) {
            settle(server);
            return false;
        }
        double y = footY(server, nx, nz);
        double dy = Mth.clamp(y - getY(), -0.6, 0.6);
        setDeltaMovement(nx - getX(), dy, nz - getZ());
        return true;
    }

    /** Turns its body and head towards a flat direction, at most {@code max} degrees. */
    public void turnTowards(Vec3 dir, float max) {
        float want = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90f;
        float yaw = Mth.approachDegrees(getYRot(), want, max);
        setYRot(yaw);
        setYHeadRot(yaw);
        yBodyRot = yaw;
    }

    @Override
    protected void approach(LivingEntity target, double dist) {
        returning = false;
        BlockPos home = home();
        if (home != null && target.position().distanceTo(Vec3.atBottomCenterOf(home)) > LEASH) {
            // it won't follow a hunter that far: back to its land
            setTarget(null);
            returning = true;
            waypoint = Vec3.atBottomCenterOf(home);
            return;
        }
        if (move != null) return;
        if (dist > reach()) stride(target.position(), walkSpeed() * (dist > 18 ? 1.25 : 1), 6f);
        else {
            turnTowards(target.position().subtract(position()).multiply(1, 0, 1).normalize(), 5f);
            settle((ServerLevel) level());
        }
    }

    /** With no hunter to fight: it roams its land, a waypoint at a time, resting between them. */
    @Override
    protected void idle() {
        if (!(level() instanceof ServerLevel server)) return;
        BlockPos home = home();
        if (home == null) {
            settle(server);
            return;
        }
        if (waypoint == null) {
            if (--waitTicks > 0) {
                settle(server);
                return;
            }
            waypoint = pickWaypoint(server, home);
            waitTicks = 80 + random.nextInt(120);
            if (waypoint == null) return;
        }
        double speed = returning ? walkSpeed() : walkSpeed() * 0.6;
        if (!stride(waypoint, speed, 3f)) {
            waypoint = null;
            returning = false;
        }
    }

    @Nullable
    private Vec3 pickWaypoint(ServerLevel level, BlockPos home) {
        for (int i = 0; i < 8; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = 8 + random.nextDouble() * (ROAM - 8);
            int x = Mth.floor(home.getX() + Math.cos(a) * d), z = Mth.floor(home.getZ() + Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4) || WorldBosses.waterDepth(level, x, z) > 1) continue;
            BlockPos p = new BlockPos(x, Mth.floor(WorldBosses.groundY(level, x, z, getY())), z);
            if (WorldBosses.settled(level, p, 48, 24)) continue;
            return Vec3.atBottomCenterOf(p);
        }
        return null;
    }

    @Override
    protected String baseAnim() {
        return moved() > 0.0004 && isAwake() ? "walk" : "idle";
    }
}
