package com.beyondsolo.quest;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Counts the daily training's drills: jumps, squats (crouching down on the ground) and strikes (melee hits that hurt
 * something alive). The run is the engine's own TRAVEL objective. Finishing the training restores the player fully.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class TrainingHooks {
    private static final Set<UUID> CROUCHED = new HashSet<>();

    private static boolean training(ServerPlayer p) {
        return SystemAPI.isAwakened(p) && QuestEngine.data(p).has(QuestTemplates.DAILY);
    }

    @SubscribeEvent
    static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && training(p)) QuestEngine.progress(p, QuestTemplates.TRAIN_JUMP, 1);
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        boolean down = p.isCrouching() && p.onGround() && !p.isPassenger();
        boolean was = CROUCHED.contains(p.getUUID());
        if (down == was) return;
        if (down) {
            CROUCHED.add(p.getUUID());
            if (training(p)) QuestEngine.progress(p, QuestTemplates.TRAIN_SQUAT, 1);
        } else if (!p.isCrouching()) {
            CROUCHED.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    static void onStrike(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0 || event.getEntity() instanceof ServerPlayer) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getSource().getDirectEntity() != p) return;
        LivingEntity hit = event.getEntity();
        if (hit.isAlive() || hit.isDeadOrDying()) {
            if (training(p)) QuestEngine.progress(p, QuestTemplates.TRAIN_STRIKE, 1);
        }
    }

    /** Training done: the System restores the body (health, mana, fatigue). */
    @SubscribeEvent
    static void onCompleted(QuestEvent.Completed event) {
        if (!event.getQuest().template().equals(QuestTemplates.DAILY)) return;
        ServerPlayer p = event.getPlayer();
        p.setHealth(p.getMaxHealth());
        SystemAPI.restoreMana(p, 99999);
        if (!SystemAPI.get(p).isCollapsed()) SystemAPI.addExhaustion(p, -100);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CROUCHED.remove(event.getEntity().getUUID());
    }

    private TrainingHooks() {}
}
