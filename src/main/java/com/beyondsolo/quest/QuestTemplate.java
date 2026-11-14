package com.beyondsolo.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Makes a quest for a player. Templates are registered by id in {@link QuestTemplates}; the NPC module can
 * register its random villager quests the same way and pick one by rank.
 */
@FunctionalInterface
public interface QuestTemplate {
    /** Where and about what the quest was started (a spawner position and its mob, a gate...). */
    record Context(@Nullable BlockPos pos, String target) {
        public static final Context NONE = new Context(null, "");

        public static Context at(BlockPos pos) {
            return new Context(pos, "");
        }
    }

    Quest create(ServerPlayer player, Context context);
}
