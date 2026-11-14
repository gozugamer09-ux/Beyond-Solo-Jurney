package com.beyondsolo.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.CombatStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Keeps clients up to date with each entity's buildups and statuses, so they can draw the meters over its head
 * and the burn / venom / brand visuals. Changes are collected during the tick and sent once at its end, and only
 * when something visible changed.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class CombatSync {
    private static final Set<LivingEntity> DIRTY = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<LivingEntity, CombatStatePayload> SENT = new WeakHashMap<>();

    public static void markDirty(LivingEntity entity) {
        if (!entity.level().isClientSide) DIRTY.add(entity);
    }

    public static CombatStatePayload snapshot(LivingEntity e) {
        int flags = 0;
        if (e.hasEffect(ModEffects.DEEP_BURN)) flags |= CombatStatePayload.DEEP_BURN;
        if (e.hasEffect(ModEffects.HEAVY_VENOM)) flags |= CombatStatePayload.HEAVY_VENOM;
        if (e.hasEffect(ModEffects.CRIMSON_BRAND)) flags |= CombatStatePayload.CRIMSON_BRAND;
        var venom = e.getEffect(ModEffects.VENOM);
        return new CombatStatePayload(e.getId(), fraction(e, Statuses.BURN), fraction(e, Statuses.VENOM),
                fraction(e, Statuses.CRIMSON), fraction(e, Buildups.BLOOD), flags, venom == null ? 0 : venom.getAmplifier() + 1);
    }

    private static float fraction(LivingEntity e, BuildupType type) {
        return Math.min(1f, Buildups.get(e, type) / type.threshold());
    }

    @SubscribeEvent
    static void flush(ServerTickEvent.Post event) {
        if (DIRTY.isEmpty()) return;
        for (LivingEntity e : DIRTY.toArray(new LivingEntity[0])) {
            if (e.isRemoved()) continue;
            CombatStatePayload now = snapshot(e);
            CombatStatePayload before = SENT.get(e);
            if (now.equals(before) || (before == null && now.isEmpty())) continue;
            SENT.put(e, now);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, now);
        }
        DIRTY.clear();
    }

    @SubscribeEvent
    static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity e && event.getEntity() instanceof ServerPlayer player) {
            CombatStatePayload now = snapshot(e);
            if (!now.isEmpty()) PacketDistributor.sendToPlayer(player, now);
        }
    }

    @SubscribeEvent
    static void effectAdded(MobEffectEvent.Added event) {
        markDirty(event.getEntity());
    }

    @SubscribeEvent
    static void effectRemoved(MobEffectEvent.Remove event) {
        markDirty(event.getEntity());
    }

    @SubscribeEvent
    static void effectExpired(MobEffectEvent.Expired event) {
        markDirty(event.getEntity());
    }

    private CombatSync() {}
}
