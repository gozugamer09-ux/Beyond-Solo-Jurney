package com.beyondsolo.monarch;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobEvent;
import com.beyondsolo.skill.MonarchPowers;
import com.beyondsolo.skill.SkillCombat;
import com.beyondsolo.system.SystemEvent;
import com.beyondsolo.worldboss.MonarchBoss;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The monarch path's hooks: the call to the throne when a hunter becomes ready for it, the client's copy of their
 * crown, what a monarch's blows and their powers' marks are worth ({@link MonarchDomain}), and ending what their powers
 * left running when they leave, fall or change worlds.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class MonarchHooks {
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Dominion d = MonarchAPI.dominion(p);
        // a monarch keeps their powers (one added since they were crowned is given now)
        if (d != null) MonarchPowers.grant(p, d);
        MonarchAPI.sync(p);
        MonarchAPI.callIfReady(p);
    }

    @SubscribeEvent
    static void onLevelUp(SystemEvent.LevelUp event) {
        MonarchAPI.callIfReady(event.getPlayer());
    }

    @SubscribeEvent
    static void onJobChanged(JobEvent.Changed event) {
        MonarchAPI.callIfReady(event.getPlayer());
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) MonarchAPI.sync(p);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) MonarchDomain.end(p);
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) MonarchDomain.end(p);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer p) MonarchDomain.end(p);
    }

    /** Someone came into sight of a monarch: their domain and crown form are drawn for them too. */
    @SubscribeEvent
    static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer monarch && MonarchAPI.crowned(monarch)) {
            MonarchDomain.showTo(viewer, monarch);
        }
    }

    /**
     * What a blow is worth among monarchs: a crowned hunter's against the monarchs from beyond the gates, a hunter's
     * inside a domain or in crown form (whose blows carry the crown's element), a dreaded creature's, and a sheltered
     * or crowned one's taken.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        float amount = event.getAmount();
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer p && p != target) {
            if (MonarchAPI.crowned(p)) {
                if (target instanceof MonarchBoss) amount *= MonarchConfig.VERSUS_MONARCH.get().floatValue();
                Dominion form = MonarchDomain.formOf(p);
                if (form != null && event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
                    amount = SkillCombat.strike(p, target, form.element(), amount);
                }
            }
            amount *= MonarchDomain.might(p);
        }
        amount *= MonarchDomain.dreadBonus(target);
        if (target instanceof Player || target instanceof com.beyondsolo.shadow.ShadowEntity) amount *= com.beyondsolo.system.BalanceHooks.keep(target, MonarchDomain.guard(target));
        if (amount != event.getAmount()) event.setAmount(amount);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MonarchDomain.tick(event.getServer());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        MonarchDomain.clear();
    }

    private MonarchHooks() {}
}
