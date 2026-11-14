package com.beyondsolo.combat;

import com.beyondsolo.network.CombatFxPayload;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends one-off combat effects to everyone who can see the entity (and the entity itself, if it is a player). */
public final class CombatFx {
    public static void play(LivingEntity entity, CombatFxPayload.Kind kind, int param) {
        if (entity.level().isClientSide) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new CombatFxPayload(entity.getId(), kind, param));
    }

    private CombatFx() {}
}
