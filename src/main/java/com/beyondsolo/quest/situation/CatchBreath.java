package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.jetbrains.annotations.Nullable;

/**
 * A hard fight is over and the player came out of it badly hurt. The System tells them to catch their breath: a few
 * quiet seconds (no blows taken or dealt) and it helps them back on their feet (regeneration, some mana). If the fight
 * finds them again first, the rest is over.
 */
public final class CatchBreath extends Situation {
    public static final String REST = "rest";
    static final String HOLD = BeyondSolo.id("rest_hold").toString();
    public static final int SECONDS = 15;
    /** Hurt at least this badly. */
    public static final float HURT = 0.4f;

    public CatchBreath() {
        super("catch_breath");
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60 * 8;
    }

    @Override
    @Nullable
    public Quest detect(ServerPlayer player, Watch watch) {
        long now = QuestEngine.gameTime(player);
        if (player.getHealth() > player.getMaxHealth() * HURT) return null;
        // fought just now, but nothing has touched them for a few seconds and nothing hunts them
        if (now - watch.lastHurtByCreature > 20 * 30 || now - watch.lastHurt < 20 * 5 || now - watch.lastHit < 20 * 3) return null;
        if (!Foes.huntersOf(player, 24).isEmpty()) return null;
        return rest(player);
    }

    public Quest rest(ServerPlayer player) {
        CompoundTag run = Run.start(this, REST, QuestEngine.gameTime(player));
        return quest(player, REST, QuestCategory.SURPRISE, run, SECONDS)
                .hold(HOLD, SECONDS, text("obj.rest"))
                .rule(text("obj.calm"))
                .xp(60)
                .build();
    }

    @Override
    public void update(ServerPlayer player, Quest q, Run run) {
        long now = QuestEngine.gameTime(player);
        Watch w = Situations.watch(player);
        if (now - w.lastHurtByCreature < 20 || now - w.lastHit < 20) {
            QuestEngine.fail(player, q, QuestEvent.FailReason.BROKEN, outcome("rest_broken"));
            return;
        }
        QuestObjective o = q.objectives().get(Situations.objective(q, HOLD));
        if (o.progress() + 1 >= o.required()) q.setOutcome(outcome("rested"));
        Situations.add(player, q, HOLD, 1);
    }

    @Override
    public void ended(ServerPlayer player, Quest q, Run run, @Nullable QuestEvent.FailReason reason) {
        if (reason != null) return;
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 10, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 60, 0));
        SystemAPI.restoreMana(player, 40);
    }
}
