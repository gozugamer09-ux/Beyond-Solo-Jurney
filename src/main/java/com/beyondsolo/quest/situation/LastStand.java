package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.trigger.QuestTriggers;
import com.beyondsolo.util.ParticleGuard;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The player is at the edge of death with creatures still on them. "Last Stand: survive." Holding out earns a second
 * wind: the System pours strength back into them and the quest turns into "Turn the tables": bring down the ones who
 * nearly killed them before the second wind fades. Running out the clock on the second wind is still a life saved.
 */
public final class LastStand extends Situation {
    public static final String STAND = "stand";
    public static final String SECOND_WIND = "second_wind";
    static final String HOLD = BeyondSolo.id("stand_hold").toString();
    static final String KILLS = BeyondSolo.id("stand_kills").toString();

    /** At or under this share of their health the player is at the edge. */
    public static final float EDGE = 0.25f;
    public static final int STAND_SECONDS = 20;
    public static final int WIND_SECONDS = 30;
    public static final int WIND_KILLS = 3;
    public static final double RANGE = 16;

    public LastStand() {
        super("last_stand");
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60 * 6;
    }

    @Override
    @Nullable
    public Quest detect(ServerPlayer player, Watch watch) {
        if (QuestTriggers.inEmergency(QuestEngine.data(player))) return null;
        long now = QuestEngine.gameTime(player);
        if (player.getHealth() > player.getMaxHealth() * EDGE || now - watch.lastHurtByCreature > 100) return null;
        if (Foes.huntersOf(player, RANGE).isEmpty()) return null;
        return stand(player);
    }

    public Quest stand(ServerPlayer player) {
        CompoundTag run = Run.start(this, STAND, QuestEngine.gameTime(player));
        return quest(player, STAND, QuestCategory.EMERGENCY, run)
                .hold(HOLD, STAND_SECONDS, text("obj.stand"))
                .rule(text("obj.alive"))
                .xp(140).mana(30)
                .penalty(20, 10)
                .build();
    }

    @Override
    public void update(ServerPlayer player, Quest q, Run run) {
        if (!run.is(STAND)) return;
        List<Mob> hunters = Foes.huntersOf(player, 24);
        long now = QuestEngine.gameTime(player);
        if (hunters.isEmpty() && now - Situations.watch(player).lastHurtByCreature > 100) {
            // they are gone (dead, or lost the trail): the danger has passed
            QuestEngine.complete(player, q, outcome("danger_passed"));
            return;
        }
        QuestObjective o = q.objectives().get(Situations.objective(q, HOLD));
        if (o.progress() + 1 < o.required()) {
            Situations.add(player, q, HOLD, 1);
            return;
        }
        // held out: a second wind
        Secrets.reveal(player, Secrets.UNBROKEN);
        if (hunters.isEmpty()) {
            QuestEngine.complete(player, q, outcome("held_on"));
            return;
        }
        breathe(player);
        int kills = Math.max(1, Math.min(WIND_KILLS, hunters.size()));
        CompoundTag next = run.next(SECOND_WIND);
        Quest wind = quest(player, SECOND_WIND, QuestCategory.EMERGENCY, next, kills)
                .custom(KILLS, kills, text("obj.wind", kills))
                .timeLimit(WIND_SECONDS * 20)
                .xp(180)
                .build();
        Situations.branch(player, q, wind, true);
    }

    /** The System's second wind: some health back, strength and speed for as long as it lasts. */
    static void breathe(ServerPlayer player) {
        player.heal(player.getMaxHealth() * 0.25f);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 8, 1));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * WIND_SECONDS, 0));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * WIND_SECONDS, 0));
        if (player.level() instanceof ServerLevel level) {
            ParticleGuard.send(level, ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.8, 0.5, 0.3);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9f, 1.4f);
        }
    }

    @Override
    public void killed(ServerPlayer player, LivingEntity victim, Watch watch) {
        if (!(victim instanceof Enemy)) return;
        Quest q = Situations.running(player, this);
        Run run = q == null ? null : Run.of(q);
        if (run == null || !run.is(SECOND_WIND)) return;
        QuestObjective o = q.objectives().get(Situations.objective(q, KILLS));
        if (o.progress() + 1 >= o.required()) q.setOutcome(outcome("turned_tables"));
        Situations.add(player, q, KILLS, 1);
    }

    @Override
    public boolean expiring(ServerPlayer player, Quest q, Run run, QuestEvent.FailReason reason) {
        if (!run.is(SECOND_WIND)) return false;
        // the second wind fades, but they are alive: that was the point
        QuestEngine.complete(player, q, outcome("lived"), q.rewards().partial(q.fraction()));
        return true;
    }
}
