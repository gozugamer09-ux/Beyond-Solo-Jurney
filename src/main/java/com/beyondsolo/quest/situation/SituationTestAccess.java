package com.beyondsolo.quest.situation;

import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** Hooks for game tests that need to reach past the normal flow (a situation started on cue, follow-ups run now). Not for gameplay code. */
public final class SituationTestAccess {
    /** Starts a situation's quest as if it had just been noticed (the room for it is still checked). */
    public static boolean begin(ServerPlayer player, Situation s, Quest q) {
        return Situations.begin(player, s, q);
    }

    /** The first tier of a streak ({@link Situations#UNTOUCHABLE} or {@link Situations#FRENZY}). */
    public static Quest firstTier(ServerPlayer player, Situation streak) {
        return ((Streaks.Streak) streak).first(player);
    }

    /** Runs whatever follow-ups are due, as the once-a-second tick would. */
    public static void runFollowUps(ServerPlayer player) {
        FollowUps.tick(player, QuestEngine.gameTime(player));
    }

    /** What a creature would come back as later, or null if it can't come back (a boss, a summon...). */
    @Nullable
    public static CompoundTag snapshot(LivingEntity e) {
        return OverwhelmingFoe.snapshot(e);
    }

    private SituationTestAccess() {}
}
