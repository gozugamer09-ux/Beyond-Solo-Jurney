package com.beyondsolo.shadow;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobEvent;
import com.beyondsolo.system.SystemEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Wires the shadow army into the game's events. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ShadowHooks {
    /** After other modules (the trial cancels some deaths): only real deaths leave a soul. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        ShadowService.creatureDied(event.getEntity(), event.getSource().getEntity());
    }

    /** A master can't hurt their own shadows, and shadows never hurt players. */
    @SubscribeEvent
    static void onDamage(LivingIncomingDamageEvent event) {
        Entity by = event.getSource().getEntity();
        if (by instanceof ShadowEntity && event.getEntity() instanceof net.minecraft.world.entity.player.Player) event.setCanceled(true);
        if (event.getEntity() instanceof ShadowEntity s && by != null && s.isAlliedTo(by)) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        ShadowService.tick(event.getServer());
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) ShadowService.login(p);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) ShadowService.logout(p);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ShadowService.discardAll(p);
            ShadowService.sync(p);
        }
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) ShadowService.changedDimension(p);
    }

    @SubscribeEvent
    static void onJob(JobEvent.Changed event) {
        ServerPlayer p = event.getPlayer();
        if (event.getSubclass().canRaiseShadows()) ShadowService.becameNecromancer(p);
        else {
            ShadowService.discardAll(p);
            ShadowService.sync(p);
        }
    }

    /** Level and intelligence change how big the army can be. */
    @SubscribeEvent
    static void onLevel(SystemEvent.LevelUp event) {
        ShadowService.sync(event.getPlayer());
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        ShadowCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        ShadowService.clearAll();
    }

    private ShadowHooks() {}
}
