package com.beyondsolo.quest.trigger;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.quest.QuestTemplates;
import com.beyondsolo.quest.Targets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * "Purge the nest": the player is next to a monster spawner and its mobs are out. Emergency: kill the mobs around
 * it and break the spawner within five minutes, without running off. Each spawner only does this once a day.
 */
public final class SpawnerNestTrigger implements QuestTrigger {
    public static final int SEARCH_RADIUS = 10;
    public static final int MOB_RADIUS = 12;
    public static final int MIN_MOBS = 2;

    @Override
    public String id() {
        return BeyondSolo.id("spawner_nest").toString();
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60 * 2;
    }

    @Override
    @Nullable
    public Quest test(ServerPlayer player, QuestData data) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        BlockPos spawner = findSpawner(level, player.blockPosition());
        if (spawner == null) return null;
        String key = id() + "@" + spawner.toShortString();
        long now = QuestEngine.gameTime(player);
        if (now < data.cooldownUntil(key)) return null;
        int mobs = level.getEntitiesOfClass(Mob.class, new AABB(spawner).inflate(MOB_RADIUS), m -> m instanceof Enemy && m.isAlive()).size();
        if (mobs < MIN_MOBS) return null;
        data.setCooldown(key, now + 24000L);
        return QuestTemplates.create(QuestTemplates.SPAWNER_NEST, player, new QuestTemplate.Context(spawner, mobType(level, spawner)));
    }

    @Nullable
    static BlockPos findSpawner(ServerLevel level, BlockPos around) {
        BlockPos best = null;
        ChunkPos center = new ChunkPos(around);
        for (int cx = center.x - 1; cx <= center.x + 1; cx++) {
            for (int cz = center.z - 1; cz <= center.z + 1; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof SpawnerBlockEntity)) continue;
                    BlockPos pos = be.getBlockPos();
                    if (pos.distSqr(around) <= SEARCH_RADIUS * SEARCH_RADIUS && (best == null || pos.distSqr(around) < best.distSqr(around))) best = pos;
                }
            }
        }
        return best;
    }

    /** The spawner's mob, as an objective target, or "hostile" if it can't be told. */
    static String mobType(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            Entity shown = spawner.getSpawner().getOrCreateDisplayEntity(level, pos);
            if (shown instanceof Enemy) return BuiltInRegistries.ENTITY_TYPE.getKey(shown.getType()).toString();
        }
        return Targets.HOSTILE;
    }
}
