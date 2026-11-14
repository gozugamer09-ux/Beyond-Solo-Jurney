package com.beyondsolo.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.registry.ModAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Buildup framework. Register a {@link BuildupType}, then call {@link #add} when a hit applies it.
 * Works the same on players and mobs, so it hits both ways.
 *
 * <p>{@link #BLOOD}: at 100 buildup the target loses 10% of max health. {@link Statuses} adds burn (deep burn),
 * venom stacks and crimson the same way. The target's resistance to the element (see {@link ModAttributes})
 * shrinks every amount added.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class Buildups {
    private static final Map<ResourceLocation, BuildupType> TYPES = new LinkedHashMap<>();

    public static final BuildupType BLOOD = register(new BuildupType(BeyondSolo.id("blood"), DamageElement.BLOOD,
            100f, 4f, true, Statuses::bloodPop));

    public static synchronized BuildupType register(BuildupType type) {
        TYPES.put(type.id(), type);
        return type;
    }

    public static BuildupType get(ResourceLocation id) {
        return TYPES.get(id);
    }

    public static Collection<BuildupType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    public static float get(LivingEntity entity, BuildupType type) {
        return entity.hasData(ModAttachments.BUILDUP) ? entity.getData(ModAttachments.BUILDUP).get(type.id()) : 0f;
    }

    /** Adds buildup and triggers the effect if the threshold is reached. Server side only. */
    public static void add(LivingEntity target, BuildupType type, float amount, @Nullable Entity source) {
        if (target.level().isClientSide || !target.isAlive()) return;
        amount *= 1 - ModAttributes.resistance(target, type.element());
        if (amount <= 0) return;
        BuildupData data = target.getData(ModAttachments.BUILDUP);
        float value = data.get(type.id()) + amount;
        CombatSync.markDirty(target);
        if (value >= type.threshold()) {
            data.set(type.id(), type.resetOnTrigger() ? 0 : type.threshold());
            type.onTrigger().trigger(target, source);
        } else {
            data.set(type.id(), value);
        }
    }

    public static void clear(LivingEntity target, BuildupType type) {
        if (target.hasData(ModAttachments.BUILDUP)) {
            target.getData(ModAttachments.BUILDUP).set(type.id(), 0);
            CombatSync.markDirty(target);
        }
    }

    @SubscribeEvent
    static void decay(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide || entity.tickCount % 20 != 0 || !entity.hasData(ModAttachments.BUILDUP)) return;
        BuildupData data = entity.getData(ModAttachments.BUILDUP);
        if (data.isEmpty()) return;
        if (entity instanceof LivingEntity living) CombatSync.markDirty(living);
        data.view().forEach((id, value) -> {
            BuildupType type = TYPES.get(id);
            data.set(id, type == null ? 0 : value - type.decayPerSecond());
        });
    }

    private Buildups() {}
}
