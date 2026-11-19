package com.beyondsolo.worldboss;

import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.Habitat;
import com.beyondsolo.enemy.Affix;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A monarch's army: S-rank gate creatures of its own affinity, called up round it as it lands and again as it grows
 * angrier. They fight for it, and crumble when it falls or leaves. Soldiers in land that isn't loaded when that happens
 * crumble as soon as it loads ({@link WorldBosses#onJoin}).
 */
public final class MonarchArmy {
    /** The soldiers each event has called, by the event's id. */
    private static final Map<UUID, List<UUID>> SOLDIERS = new HashMap<>();

    /**
     * Calls {@code count} soldiers round {@code at} for the monarch (none if its kind has no army): its affinity, from
     * the creatures that fit its {@code habitats}, veterans and elites, an elite or a champion leading them.
     */
    public static List<Mob> call(ServerLevel level, ColossalBoss monarch, Vec3 at, int count, Affinity affinity, List<Habitat> habitats,
                                 @Nullable LivingEntity target) {
        List<Mob> out = new ArrayList<>();
        UUID event = monarch.eventId();
        RandomSource r = level.random;
        EnemyRoster.Mix mix = EnemyRoster.random(PlayerRank.S, r, habitats, affinity);
        for (int i = 0; i < count; i++) {
            double a = (i + r.nextDouble() * 0.6) / count * Math.PI * 2, d = 5 + r.nextDouble() * 6;
            int x = Mth.floor(at.x + Math.cos(a) * d), z = Mth.floor(at.z + Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.getFluidState(p.below()).isEmpty()) continue;
            EntityType<?> type = mix.pick(r);
            Variant v = i == 0 ? (r.nextInt(3) == 0 ? Variant.CHAMPION : Variant.ELITE) : r.nextInt(3) == 0 ? Variant.ELITE : Variant.VETERAN;
            EnemyTraits traits = new EnemyTraits(affinity, v, Affix.mask(Affix.roll(v.affixes, r)));
            Mob mob = Enemies.spawn(level, PlayerRank.S, type, p, traits, -1);
            if (mob == null) continue;
            if (event != null) {
                mob.getPersistentData().putUUID(WorldBosses.ARMY_TAG, event);
                SOLDIERS.computeIfAbsent(event, k -> new ArrayList<>()).add(mob.getUUID());
            }
            if (monarch.home() != null) mob.restrictTo(monarch.home(), 48);
            if (target != null) mob.setTarget(target);
            WorldBossFx.pillar(level, mob.position(), 0.9f, 4.5f, monarch.fxColor());
            level.sendParticles(MonarchBoss.ink(monarch.fxColor()), mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.4, 0.8, 0.4, 0.15);
            out.add(mob);
        }
        return out;
    }

    /** How many of an event's soldiers still stand (in loaded land). */
    public static int standing(ServerLevel level, @Nullable UUID event) {
        if (event == null) return 0;
        int n = 0;
        for (UUID id : SOLDIERS.getOrDefault(event, List.of())) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) n++;
        }
        return n;
    }

    /** Every soldier of an event crumbles. */
    public static void disband(ServerLevel level, UUID event) {
        List<UUID> ids = SOLDIERS.remove(event);
        if (ids == null) return;
        for (UUID id : ids) {
            Entity e = level.getEntity(id);
            if (e == null || !e.isAlive()) continue;
            level.sendParticles(MonarchBoss.ink(0x2A2440), e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 16, 0.3, 0.5, 0.3, 0.02);
            level.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
            e.discard();
        }
    }

    static void clear() {
        SOLDIERS.clear();
    }

    private MonarchArmy() {}
}
