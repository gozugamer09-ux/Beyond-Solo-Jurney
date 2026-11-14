package com.beyondsolo.quest;

import net.minecraft.server.level.ServerPlayer;

/** Hooks for commands and game tests that need to reach past the normal flow. Not for gameplay code. */
public final class QuestTestAccess {
    /** Adds progress to the quest's objectives in order (the first unfinished one first), then completes it if done. */
    public static void addProgress(ServerPlayer player, Quest quest, int amount) {
        int left = amount;
        for (QuestObjective o : quest.objectives()) {
            if (left <= 0) break;
            int need = o.required() - o.progress();
            if (need <= 0) continue;
            int add = Math.min(need, left);
            o.setProgress(o.progress() + add);
            left -= add;
        }
        QuestEngine.sync(player);
        if (quest.isComplete()) QuestEngine.complete(player, quest);
    }

    /** Moves a quest's clock so it runs out on the next check. */
    public static void expire(ServerPlayer player, Quest quest) {
        quest.setDeadline(QuestEngine.gameTime(player) - 1);
    }

    /** Marks the quest line as handed out, so ticking doesn't start it (tests that set up their own quests). */
    public static void markStarted(ServerPlayer player) {
        QuestData data = QuestEngine.data(player);
        data.setStarted(true);
        data.setDailyDay(QuestEngine.day(player));
    }

    private QuestTestAccess() {}
}
