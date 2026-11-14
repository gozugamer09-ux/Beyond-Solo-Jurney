package com.beyondsolo.registry;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.npc.TravelerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);

    /** The traveller of an escort request. */
    public static final DeferredHolder<EntityType<?>, EntityType<TravelerEntity>> TRAVELER = ENTITY_TYPES.register("traveler",
            () -> EntityType.Builder.<TravelerEntity>of(TravelerEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10).build("traveler"));

    @SubscribeEvent
    static void attributes(EntityAttributeCreationEvent event) {
        event.put(TRAVELER.get(), TravelerEntity.attributes().build());
    }

    private ModEntities() {}
}
