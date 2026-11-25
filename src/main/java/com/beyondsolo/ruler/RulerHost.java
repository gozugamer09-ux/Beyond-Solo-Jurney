package com.beyondsolo.ruler;

import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.worldboss.WorldBossFx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A ruler's host: its heralds of light ({@link HeraldEntity}), called down round it as it lands and again as it angers,
 * each in a pillar of its light. They fade when it falls or leaves. Heralds in land that isn't loaded when that happens
 * fade as soon as it loads ({@link Rulers#onJoin}).
 */
public final class RulerHost {
    /** The heralds each ruler's event has called, by the event's id. */
    private static final Map<UUID, List<UUID>> HERALDS = new HashMap<>();

    /** Calls {@code count} heralds round {@code at} for the ruler; they go for {@code target} first if it is the ruler's foe. */
    public static List<HeraldEntity> call(ServerLevel level, RulerBoss ruler, Vec3 at, int count, @Nullable LivingEntity target) {
        List<HeraldEntity> out = new ArrayList<>();
        UUID event = ruler.eventId();
        RandomSource r = level.random;
        for (int i = 0; i < count; i++) {
            double a = (i + r.nextDouble() * 0.6) / count * Math.PI * 2, d = 4 + r.nextDouble() * 5;
            int x = Mth.floor(at.x + Math.cos(a) * d), z = Mth.floor(at.z + Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.getFluidState(p.below()).isEmpty()) continue;
            HeraldEntity h = RulerModule.HERALD.get().create(level);
            if (h == null) continue;
            h.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, ruler.getYRot() + (float) Math.toDegrees(a), 0f);
            h.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
            h.setPersistenceRequired();
            // as strong as an S-rank gate's creatures, and keeping pace with its ruler as it is sized past S
            double[] grow = ruler.hostScale();
            Enemies.strength(h, Enemies.rankHealth(PlayerRank.S) * grow[0], Enemies.rankDamage(PlayerRank.S) * grow[1]);
            h.setHealth(h.getMaxHealth());
            h.setRuler(ruler);
            if (event != null) {
                h.getPersistentData().putUUID(Rulers.HOST_TAG, event);
                HERALDS.computeIfAbsent(event, k -> new ArrayList<>()).add(h.getUUID());
            }
            if (ruler.home() != null) h.restrictTo(ruler.home(), 48);
            if (target != null && h.foe(target)) h.setTarget(target);
            level.addFreshEntity(h);
            WorldBossFx.pillar(level, h.position(), 0.9f, 9f, ruler.fxColor());
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, h.getX(), h.getY() + 1.2, h.getZ(), 24, 0.3, 0.9, 0.3, 0.05);
            out.add(h);
        }
        return out;
    }

    /** How many of an event's heralds still stand (in loaded land). */
    public static int standing(ServerLevel level, @Nullable UUID event) {
        if (event == null) return 0;
        int n = 0;
        for (UUID id : HERALDS.getOrDefault(event, List.of())) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) n++;
        }
        return n;
    }

    /** Every herald of an event fades. */
    public static void disband(ServerLevel level, UUID event) {
        List<UUID> ids = HERALDS.remove(event);
        if (ids == null) return;
        for (UUID id : ids) {
            if (level.getEntity(id) instanceof HeraldEntity h && h.isAlive()) h.fade(level);
        }
    }

    static void clear() {
        HERALDS.clear();
    }

    private RulerHost() {}
}
