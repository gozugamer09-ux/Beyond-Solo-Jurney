package com.beyondsolo.light;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobEvent;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.skill.RulerPowers;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillCombat;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.SystemEvent;
import com.beyondsolo.worldboss.MonarchBoss;
import net.minecraft.network.chat.Component;
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

import java.util.List;

/**
 * The light's hooks: the powers it owes a hunter as they grow, the call to the Investiture, the client's copy of their
 * road, what the blows of a hunter sworn to it and the marks of its judgement are worth ({@link LightSanctum}), and
 * ending what their powers left running (and their vigil) when they leave, fall or change worlds.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class LightHooks {
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        // a hunter sworn to the light keeps its powers (one added since is given now; a mantle's lost one comes back)
        if (LightAPI.sworn(p)) RulerPowers.grant(p);
        LightAPI.sync(p);
        LightAPI.callIfReady(p);
    }

    @SubscribeEvent
    static void onLevelUp(SystemEvent.LevelUp event) {
        ServerPlayer p = event.getPlayer();
        if (LightAPI.sworn(p)) {
            List<Skill> given = RulerPowers.grant(p);
            for (Skill s : given) {
                Notifications.send(p, new SystemNotification(SystemNotification.Kind.REWARD,
                        Component.translatable("notify.beyondsolo.light_power.title"),
                        Component.translatable("notify.beyondsolo.light_power.body", s.displayName()), 240));
            }
        }
        LightAPI.callIfReady(p);
    }

    @SubscribeEvent
    static void onJobChanged(JobEvent.Changed event) {
        LightAPI.callIfReady(event.getPlayer());
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) LightAPI.sync(p);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        LightSanctum.end(p);
        Vigil.cancel(p);
        Chapel.forget(p.getUUID());
    }

    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) LightSanctum.end(p);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer p)) return;
        LightSanctum.end(p);
        Vigil.died(p);
    }

    /** Someone came into sight of a ruler: their sanctum and seraph form are drawn for them too. */
    @SubscribeEvent
    static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer ruler && LightAPI.sworn(ruler)) {
            LightSanctum.showTo(viewer, ruler);
        }
    }

    /**
     * What a blow is worth in the light: a sworn hunter's against the monarchs from beyond the gates, a hunter's inside
     * a sanctum or in seraph form (whose blows carry the light), a judged creature's, and a sheltered or winged one's taken.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        float amount = event.getAmount();
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer p && p != target) {
            if (LightAPI.sworn(p)) {
                if (target instanceof MonarchBoss) amount *= (float) LightConfig.versusMonarch();
                if (LightSanctum.inSeraph(p) && event.getSource().is(DamageTypes.PLAYER_ATTACK)) {
                    amount = SkillCombat.strike(p, target, SkillElement.LIGHT, amount);
                }
            }
            amount *= LightSanctum.might(p);
        }
        amount *= LightSanctum.judgedBonus(target);
        if (target instanceof Player || target instanceof com.beyondsolo.shadow.ShadowEntity) amount *= LightSanctum.guard(target);
        if (amount != event.getAmount()) event.setAmount(amount);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        LightSanctum.tick(event.getServer());
        Vigil.tick(event.getServer());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        LightSanctum.clear();
        Vigil.clear();
        Chapel.clear();
    }

    private LightHooks() {}
}
