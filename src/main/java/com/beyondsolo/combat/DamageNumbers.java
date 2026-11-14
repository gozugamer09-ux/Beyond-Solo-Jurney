package com.beyondsolo.combat;

import com.beyondsolo.network.DamageNumberPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Floating damage numbers. A player sees the numbers for their own hits. Element damage with no player behind it
 * (a deep burn ticking, a mob's bleed popping) is shown to the players close by, so a status is never silent.
 * Each client can turn the numbers off in its config.
 */
public final class DamageNumbers {
    private static final double RANGE = 24;

    public static void report(LivingEntity target, float amount, DamageElement element, boolean crit, @Nullable Entity attacker) {
        if (!(target.level() instanceof ServerLevel level)) return;
        DamageNumberPayload payload = new DamageNumberPayload(target.getId(), amount, element.index(), crit);
        if (attacker instanceof ServerPlayer player) {
            if (player != target) PacketDistributor.sendToPlayer(player, payload);
            if (element == DamageElement.PHYSICAL) return;
        }
        if (element == DamageElement.PHYSICAL) return;
        for (ServerPlayer p : level.players()) {
            if (p != target && p != attacker && p.distanceToSqr(target) < RANGE * RANGE) PacketDistributor.sendToPlayer(p, payload);
        }
    }

    private DamageNumbers() {}
}
