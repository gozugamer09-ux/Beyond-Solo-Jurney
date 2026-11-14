package com.beyondsolo.system;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Keeps System state alive across login, death and dimension changes, and runs the per-tick logic. */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class SystemEventHandler {
    /** Tick of each player's last attack, for "in combat" checks. Server thread only. */
    private static final Map<UUID, Integer> LAST_ATTACK = new java.util.HashMap<>();
    private static final Map<ServerPlayer, Integer> SPRINT_TICKS = new WeakHashMap<>();

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!event.isEndConquered()) {
                // Death clears exhaustion and ends a collapse.
                Exhaustion.recover(player, 0);
                SystemData data = SystemAPI.get(player);
                data.setMana(data.maxMana());
            }
            refresh(player);
        }
    }

    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player);
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_ATTACK.remove(event.getEntity().getUUID());
    }

    private static void refresh(ServerPlayer player) {
        StatEffects.apply(player);
        Exhaustion.applyCollapseModifiers(player, SystemAPI.get(player).isCollapsed());
        SystemAPI.sync(player);
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SystemData data = SystemAPI.get(player);
        if (!data.isAwakened()) return;

        Exhaustion.tickCollapse(player);
        if (player.isSprinting()) SPRINT_TICKS.merge(player, 1, Integer::sum);

        if (player.tickCount % 20 == 0) {
            boolean sprinted = SPRINT_TICKS.getOrDefault(player, 0) >= 10;
            SPRINT_TICKS.remove(player);
            int lastAttack = LAST_ATTACK.getOrDefault(player.getUUID(), Integer.MIN_VALUE / 2);
            boolean fought = player.tickCount - lastAttack < 60;
            Exhaustion.tickSecond(player, sprinted, fought);

            // Mana regen: 1/s plus a bit per INT.
            data.setMana(data.mana() + 1f + (data.stat(Stat.INT) - Stat.BASE) * 0.05f);
            SystemAPI.sync(player);
        }
    }

    // ---- XP from kills ----

    @SubscribeEvent
    static void onKill(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || victim.level().isClientSide) return;
        if (event.getSource().getEntity() instanceof ServerPlayer killer && SystemAPI.isAwakened(killer)) {
            long xp = Math.max(1, Math.round(victim.getMaxHealth() * Config.KILL_XP_MULTIPLIER.get()));
            SystemAPI.grantXp(killer, xp, SystemAPI.XP_SOURCE_KILL);
        }
    }

    // ---- exhaustion from fighting, and the collapse lock ----

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            if (isCollapsed(event.getEntity())) event.setCanceled(true);
            return;
        }
        if (cancelIfCollapsed(player, event)) return;
        LAST_ATTACK.put(player.getUUID(), player.tickCount);
        Exhaustion.add(player, Config.EXHAUSTION_PER_ATTACK.get().floatValue());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Beds still work, so a collapsed player next to one can sleep it off.
        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BedBlock) return;
        cancelIfCollapsed(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfCollapsed(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfCollapsed(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfCollapsed(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onBreak(BlockEvent.BreakEvent event) {
        cancelIfCollapsed(event.getPlayer(), event);
    }

    @SubscribeEvent
    static void onWakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SystemAPI.isAwakened(player)) {
            Exhaustion.recover(player, 0);
        }
    }

    private static boolean isCollapsed(Player player) {
        return SystemAPI.get(player).isCollapsed();
    }

    private static boolean cancelIfCollapsed(Player player, ICancellableEvent event) {
        if (player != null && isCollapsed(player)) {
            event.setCanceled(true);
            return true;
        }
        return false;
    }

    private SystemEventHandler() {}
}
