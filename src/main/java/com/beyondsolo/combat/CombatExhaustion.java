package com.beyondsolo.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.SystemWeaponItem;
import com.beyondsolo.network.CombatFxPayload;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Combat's share of the exhaustion meter (the meter and the collapse live in system/Exhaustion):
 * heavy weapons tire you faster per swing, light ones slower; heavy armor makes sprinting more tiring;
 * every combat roll costs a little. Exhaustion resistance (Hunter set, gear) shrinks all of it.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class CombatExhaustion {
    private static final Map<ServerPlayer, Integer> SPRINT_TICKS = new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOW)
    static void swing(AttackEntityEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getMainHandItem().getItem() instanceof SystemWeaponItem weapon && weapon.weight() != 1f) {
            // The System already charged a normal swing; add (or refund) the difference.
            SystemAPI.addExhaustion(player, Config.EXHAUSTION_PER_ATTACK.get().floatValue() * (weapon.weight() - 1f));
        }
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSprinting()) SPRINT_TICKS.merge(player, 1, Integer::sum);
        if (player.tickCount % 20 != 0) return;
        int sprinted = SPRINT_TICKS.getOrDefault(player, 0);
        SPRINT_TICKS.remove(player);
        float weight = ArmorSets.weight(player);
        if (sprinted >= 10 && weight > 0) {
            SystemAPI.addExhaustion(player, Config.EXHAUSTION_PER_SPRINT_SECOND.get().floatValue() * weight);
        }
    }

    /** Called by the Combat Roll hook when a player starts a roll. */
    public static void rolled(ServerPlayer player) {
        SystemAPI.addExhaustion(player, Config.EXHAUSTION_PER_ROLL.get().floatValue());
        CombatFx.play(player, CombatFxPayload.Kind.ROLL, 0);
    }

    private CombatExhaustion() {}
}
