package com.beyondsolo.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Something that builds up on an entity until it pops: blood loss, poison stacks, deep burn...
 *
 * @param id              unique id, e.g. {@code beyondsolo:blood}
 * @param element         element it belongs to (for colours and resistances)
 * @param threshold       amount at which {@link #onTrigger} runs
 * @param decayPerSecond  how fast it fades when not refreshed
 * @param resetOnTrigger  set back to 0 after triggering (otherwise stays at the threshold)
 * @param onTrigger       what happens at the threshold
 */
public record BuildupType(ResourceLocation id, DamageElement element, float threshold, float decayPerSecond,
                          boolean resetOnTrigger, Trigger onTrigger) {

    @FunctionalInterface
    public interface Trigger {
        void trigger(LivingEntity target, @Nullable Entity source);
    }
}
