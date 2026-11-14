package com.beyondsolo.system;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Exhaustion meter (0-100%). Sprinting and fighting fill it, resting drains it.
 * At 100% the player collapses: they can't move, attack, break or use anything until the
 * timer ends, they sleep, or they die. The combat module can add more sources through
 * {@link SystemAPI#addExhaustion}.
 */
public final class Exhaustion {
    public static final float WARNING_LEVEL = 80f;
    /** Exhaustion left after a collapse wears off on its own. */
    public static final float AFTER_COLLAPSE = 60f;

    private static final ResourceLocation COLLAPSE_ID = BeyondSolo.id("collapse");

    static void add(ServerPlayer player, float amount) {
        SystemData data = SystemAPI.get(player);
        if (!Config.EXHAUSTION_ENABLED.get() || !data.isAwakened() || data.isCollapsed() || player.isCreative() || player.isSpectator()) {
            return;
        }
        if (amount > 0) {
            // Gear and VIT can make the player tire more slowly.
            amount *= 1f - (float) Math.min(0.8, player.getAttributeValue(com.beyondsolo.combat.ModAttributes.EXHAUSTION_RESISTANCE));
        }
        float before = data.exhaustion();
        data.setExhaustion(before + amount);
        if (before < WARNING_LEVEL && data.exhaustion() >= WARNING_LEVEL && data.exhaustion() < 100) {
            Notifications.send(player, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.exhaustion_warning.title"),
                    Component.translatable("notify.beyondsolo.exhaustion_warning.body")));
        }
        if (data.exhaustion() >= 100) collapse(player);
    }

    public static void collapse(ServerPlayer player) {
        SystemData data = SystemAPI.get(player);
        int min = Config.COLLAPSE_MIN_SECONDS.get();
        int max = Math.max(min, Config.COLLAPSE_MAX_SECONDS.get());
        int ticks = (min + player.getRandom().nextInt(max - min + 1)) * 20;
        data.setExhaustion(100);
        data.setCollapseTicks(ticks);
        applyCollapseModifiers(player, true);
        player.setSprinting(false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 1f, 0.6f);
        Notifications.send(player, SystemNotification.of(SystemNotification.Kind.ALERT,
                Component.translatable("notify.beyondsolo.collapse.title"),
                Component.translatable("notify.beyondsolo.collapse.body", ticks / 20)));
        SystemAPI.sync(player);
        NeoForge.EVENT_BUS.post(new SystemEvent.Collapsed(player, ticks));
    }

    /** Ends a collapse early (sleep, death) or when the timer runs out. */
    public static void recover(ServerPlayer player, float exhaustionAfter) {
        SystemData data = SystemAPI.get(player);
        boolean wasCollapsed = data.isCollapsed();
        data.setCollapseTicks(0);
        data.setExhaustion(exhaustionAfter);
        applyCollapseModifiers(player, false);
        if (wasCollapsed) {
            Notifications.send(player, SystemNotification.of(SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.recovered.title"),
                    Component.translatable("notify.beyondsolo.recovered.body")));
        }
        SystemAPI.sync(player);
    }

    /** Called once per second from the player tick. */
    static void tickSecond(ServerPlayer player, boolean sprinted, boolean fought) {
        SystemData data = SystemAPI.get(player);
        if (!data.isAwakened()) return;
        if (data.isCollapsed()) {
            // Re-apply in case something (respawn, other mods) removed the modifiers.
            applyCollapseModifiers(player, true);
            return;
        }
        if (!Config.EXHAUSTION_ENABLED.get()) {
            data.setExhaustion(0);
            return;
        }
        if (sprinted) add(player, Config.EXHAUSTION_PER_SPRINT_SECOND.get().floatValue());
        if (!sprinted && !fought) {
            float vitBonus = 1f + (data.stat(Stat.VIT) - Stat.BASE) * 0.02f;
            float rate = Config.EXHAUSTION_RECOVERY_PER_SECOND.get().floatValue() * vitBonus;
            if (player.isSleeping()) rate *= 10;
            data.setExhaustion(data.exhaustion() - rate);
        }
    }

    /** Called every tick while collapsed. */
    static void tickCollapse(ServerPlayer player) {
        SystemData data = SystemAPI.get(player);
        if (!data.isCollapsed()) return;
        data.setCollapseTicks(data.collapseTicks() - 1);
        if (!data.isCollapsed()) recover(player, AFTER_COLLAPSE);
    }

    static void applyCollapseModifiers(ServerPlayer player, boolean collapsed) {
        setModifier(player, Attributes.MOVEMENT_SPEED, collapsed);
        setModifier(player, Attributes.JUMP_STRENGTH, collapsed);
        setModifier(player, Attributes.BLOCK_BREAK_SPEED, collapsed);
    }

    private static void setModifier(ServerPlayer player, Holder<Attribute> attribute, boolean on) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;
        if (on) {
            inst.addOrUpdateTransientModifier(new AttributeModifier(COLLAPSE_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            inst.removeModifier(COLLAPSE_ID);
        }
    }

    private Exhaustion() {}
}
