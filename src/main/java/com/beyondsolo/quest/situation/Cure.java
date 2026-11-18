package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.npc.NpcNames;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The player comes across a zombie villager. The System points out what it was ("A Cursed Villager: cure it"), an
 * offer they can take or leave. Once the cure has started the quest becomes "Keep it safe until the cure takes hold",
 * with the villager's health to watch; a villager walking out of the zombie completes it.
 */
public final class Cure extends Situation {
    public static final String CURE = "cure";
    public static final String WAIT = "wait";
    static final String CURED = BeyondSolo.id("cure_done").toString();
    static final String HEALTH = BeyondSolo.id("cure_health").toString();
    static final String LOST = "lost";

    public static final double RANGE = 16;
    public static final int CURE_MINUTES = 10;

    public Cure() {
        super("cure");
    }

    @Override
    public int cooldownTicks() {
        return 20 * 60 * 15;
    }

    @Override
    public boolean offered() {
        return true;
    }

    @Override
    @Nullable
    public Quest detect(ServerPlayer player, Watch watch) {
        // looked for every few seconds: the world around the player only changes so fast
        if ((QuestEngine.gameTime(player) / 20) % 4 != 0) return null;
        List<ZombieVillager> near = player.level().getEntitiesOfClass(ZombieVillager.class, player.getBoundingBox().inflate(RANGE),
                z -> z.isAlive() && !z.isConverting() && player.hasLineOfSight(z));
        return near.isEmpty() ? null : cure(player, near.get(0));
    }

    public Quest cure(ServerPlayer player, ZombieVillager zombie) {
        CompoundTag run = Run.start(this, CURE, QuestEngine.gameTime(player));
        Run.setTarget(run, zombie);
        Run.setWard(run, true);
        Run.setName(run, zombie.getType().getDescription(), player.registryAccess());
        return quest(player, CURE, QuestCategory.SURPRISE, run)
                .line(CURED, text("obj.cure"))
                .timeLimit(CURE_MINUTES * 60 * 20)
                .xp(200)
                .build();
    }

    @Override
    public void update(ServerPlayer player, Quest q, Run run) {
        LivingEntity z = Foes.find(player, run.target());
        int lost = z == null ? run.count(LOST) + 1 : 0;
        run.setCount(LOST, lost);
        if (z == null) {
            if (lost >= 10) QuestEngine.fail(player, q, QuestEvent.FailReason.BROKEN, outcome("cure_lost"));
            return;
        }
        if (run.refreshTargetId(z)) QuestEngine.sync(player);
        if (run.is(CURE) && z instanceof ZombieVillager zv && zv.isConverting()) {
            Component name = run.name(player.registryAccess());
            Quest wait = quest(player, WAIT, QuestCategory.SURPRISE, run.next(WAIT))
                    .watch(HEALTH, name, Foes.healthPercent(z))
                    .line(CURED, text("obj.wait"))
                    .rule(text("obj.keep_safe"))
                    .xp(200)
                    .build();
            Situations.branch(player, q, wait, false);
            return;
        }
        if (run.is(WAIT)) Situations.set(player, q, HEALTH, Foes.healthPercent(z));
    }

    /** A creature turned into another: a cured zombie villager completes the quest about it. */
    static void converted(LivingEntity from, LivingEntity to) {
        if (!(from instanceof ZombieVillager) || !(from.level() instanceof ServerLevel level)) return;
        for (ServerPlayer p : level.players()) {
            Quest q = Situations.running(p, Situations.CURE);
            Run run = q == null ? null : Run.of(q);
            if (run == null || !from.getUUID().equals(run.target())) continue;
            q.setOutcome(outcome("cured", NpcNames.name(to)));
            Situations.set(p, q, CURED, 1);
        }
    }

    @Override
    public void runEntityDied(ServerPlayer player, Quest q, Run run, LivingEntity dead, DamageSource source) {
        if (dead.getUUID().equals(run.target())) QuestEngine.fail(player, q, QuestEvent.FailReason.BROKEN, outcome("cure_died"));
    }

    @Override
    public void playerDied(ServerPlayer player, Quest q, Run run, DamageSource source) {
        // a cure takes minutes: dying in the meantime does not undo it
    }
}
