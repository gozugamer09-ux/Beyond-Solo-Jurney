package com.beyondsolo.quest.trigger;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.quest.QuestTemplates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

/** "Ambush": several monsters are hunting the player at once. Emergency: fight them off within two minutes. */
public final class AmbushTrigger implements QuestTrigger {
    public static final int RADIUS = 14;
    public static final int MIN_HUNTERS = 4;

    @Override
    public String id() {
        return BeyondSolo.id("ambush").toString();
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60 * 10;
    }

    @Override
    @Nullable
    public Quest test(ServerPlayer player, QuestData data) {
        int hunters = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RADIUS),
                m -> m instanceof Enemy && m.isAlive() && m.getTarget() == player).size();
        if (hunters < MIN_HUNTERS) return null;
        return QuestTemplates.create(QuestTemplates.AMBUSH, player, QuestTemplate.Context.at(player.blockPosition()));
    }
}
