package com.beyondsolo.quest.trigger;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.quest.QuestTemplates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** "Rich veins": the player went deep underground. Offered (not forced): mine ore for a reward. */
public final class DeepVeinsTrigger implements QuestTrigger {
    public static final int DEPTH = 0;

    @Override
    public String id() {
        return BeyondSolo.id("deep_veins").toString();
    }

    @Override
    public int cooldownTicks() {
        return 24000 * 3;
    }

    @Override
    @Nullable
    public Quest test(ServerPlayer player, QuestData data) {
        if (!player.level().dimension().equals(Level.OVERWORLD) || player.getY() >= DEPTH) return null;
        if (player.level().canSeeSky(player.blockPosition())) return null;
        return QuestTemplates.create(QuestTemplates.DEEP_VEINS, player, QuestTemplate.Context.NONE);
    }
}
