package com.beyondsolo.isle;

import com.beyondsolo.combat.BuildupType;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.guild.GuildHunterEntity;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.WorldBosses;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The queen and the king of the hive ({@link com.beyondsolo.worldboss.WorldBossKind.Tier#HIVE}): great threats that
 * live deep under Halcyon Isle and only ever fight there, in the queen's chamber, against the raid's hunters and the
 * hunters of the rankings fighting beside them ({@link #foe}). They walk the chamber's floor (no climbing out of the
 * earth, no leaving: they fight until they fall), and every dash and leap stops short of its walls ({@link #clearRun}).
 * Beatable like every great threat: no second life, no immunity but to their own venom, and what heals them is only what
 * the king devours.
 */
public abstract class HiveBoss extends ColossalBoss {
    private int walled;

    protected HiveBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    /** The distance it likes to fight from (from its edge). */
    protected abstract double preferred();

    @Override
    protected void approach(LivingEntity target, double dist) {
        if (dist > preferred()) getNavigation().moveTo(target, dist > 12 ? 1.3 : 1.0);
        else getNavigation().stop();
    }

    @Override
    protected boolean earthbound() {
        return false;
    }

    /**
     * The rankings' hunters who came with the raid fight it too, but their blows land at a fraction
     * ({@link IsleConfig#allyRoyalDamage}): they keep the swarm off the hunters, and bringing down the royalty is the
     * hunters' own work (and their share of it, which every blow of another thins).
     */
    @Override
    protected float allyShare() {
        return IsleConfig.allyRoyalDamage();
    }

    /** Whether a buildup takes hold on it: never venom (the swarm's poison is its own), nor what an ally's blow carries. */
    @Override
    public boolean takesBuildup(BuildupType type, @Nullable Entity source) {
        return type != Statuses.VENOM && super.takesBuildup(type, source);
    }

    /** The swarm's venom is its own: no poison takes hold on it, its own kind's or a potion's. */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.is(ModEffects.VENOM) || effect.is(ModEffects.HEAVY_VENOM) || effect.is(MobEffects.POISON)) return false;
        return super.canBeAffected(effect);
    }

    /** The hunters of the rankings who came with the raid are its foes too. */
    @Override
    protected boolean foe(LivingEntity e) {
        return e instanceof GuildHunterEntity;
    }

    @Override
    protected List<String> extraAnims() {
        return List.of("intro", "phase2", "phase3");
    }

    /**
     * The chamber has a roof: the ground under a point is looked for from the point down (from above, a leap at a hunter
     * on the dais found the roof where the dome comes down low, and the king landed in the rock).
     */
    @Override
    public Vec3 ground(Vec3 at) {
        if (!(level() instanceof ServerLevel server)) return at;
        return new Vec3(at.x, WorldBosses.groundBelow(server, at.x, at.z, at.y), at.z);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) walled = isAlive() && isInWall() ? walled + 1 : 0;
    }

    /** How long it has been shut in the rock, in ticks (0: it isn't). */
    public int walled() {
        return walled;
    }

    /** Moves it to a point at once (the end of a dash, a leap's landing). */
    protected void blinkTo(Vec3 at) {
        teleportTo(at.x, at.y, at.z);
        getNavigation().stop();
    }

    /** Holds it at a point for a tick of a move (in the air): no physics, no fall. */
    protected void hold(Vec3 at) {
        noPhysics = true;
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
    }

    /**
     * How far it can go from where it stands along a flat direction before its body would meet a wall (at most
     * {@code max}): its dashes and slides stop there.
     */
    protected double clearRun(Vec3 dir, double max) {
        Vec3 d = dir.multiply(1, 0, 1);
        if (d.lengthSqr() < 1e-6) return 0;
        d = d.normalize();
        double half = getBbWidth() / 2 + 0.3;
        int height = (int) Math.ceil(getBbHeight());
        for (double s = 0.5; s <= max; s += 0.5) {
            Vec3 at = position().add(d.scale(s + half));
            for (int y = 0; y < height; y++) {
                BlockPos p = BlockPos.containing(at.x, getY() + 0.5 + y, at.z);
                if (!level().getBlockState(p).getCollisionShape(level(), p).isEmpty()) return Math.max(0, s - 0.5);
            }
        }
        return max;
    }

    /** Where a dash along {@code dir} of up to {@code max} blocks ends, short of any wall. */
    protected Vec3 runEnd(Vec3 dir, double max) {
        return ground(position().add(dir.multiply(1, 0, 1).normalize().scale(clearRun(dir, max))));
    }

    @Override
    protected void deathTick(ServerLevel level, int t) {
        if (t % 6 == 0) dust(position(), getBbWidth(), 14);
    }
}
