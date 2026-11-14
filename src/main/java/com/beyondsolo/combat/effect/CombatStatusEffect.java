package com.beyondsolo.combat.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;

import java.util.Set;

/**
 * A combat status (deep burn, venom, crimson brand...). It is cured only by the cures given here,
 * so leaving out milk makes it ignore milk, and leaving out the totem cure makes it survive a totem.
 */
public class CombatStatusEffect extends MobEffect {
    @FunctionalInterface
    public interface Tick {
        void tick(LivingEntity entity, int amplifier);
    }

    private final Set<EffectCure> cures;
    private final int interval;
    private final Tick onTick;

    public CombatStatusEffect(MobEffectCategory category, int color, Set<EffectCure> cures, int interval, Tick onTick) {
        super(category, color);
        this.cures = Set.copyOf(cures);
        this.interval = interval;
        this.onTick = onTick;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int remaining, int amplifier) {
        return interval > 0 && tickClock() % interval == 0;
    }

    // Infinite effects have no countdown to pace ticks with, so pace them all with the server clock.
    private static int tickClock() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? 0 : server.getTickCount();
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) onTick.tick(entity, amplifier);
        return true;
    }

    @Override
    public void fillEffectCures(Set<EffectCure> set, MobEffectInstance instance) {
        set.addAll(cures);
    }
}
