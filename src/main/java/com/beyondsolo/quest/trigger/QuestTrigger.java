package com.beyondsolo.quest.trigger;

import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestData;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Watches for a situation and starts a surprise quest when it happens. Checked every couple of seconds for each
 * evaluated player. Return a quest to start it: emergencies start on the spot, other quests are offered.
 */
public interface QuestTrigger {
    /** Used as the cooldown key. */
    String id();

    /** How long after firing this trigger stays quiet for that player. */
    int cooldownTicks();

    @Nullable
    Quest test(ServerPlayer player, QuestData data);
}
