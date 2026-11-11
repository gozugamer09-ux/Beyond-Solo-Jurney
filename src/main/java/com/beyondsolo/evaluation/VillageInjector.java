package com.beyondsolo.evaluation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.Config;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the Evaluation Zone to the building pools of every vanilla village type.
 * Structure mods that reuse vanilla village pools pick it up too.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class VillageInjector {
    public static final String EVALUATION_ZONE = BeyondSolo.MOD_ID + ":evaluation_zone";

    private static final List<ResourceLocation> HOUSE_POOLS = List.of(
            ResourceLocation.withDefaultNamespace("village/plains/houses"),
            ResourceLocation.withDefaultNamespace("village/desert/houses"),
            ResourceLocation.withDefaultNamespace("village/savanna/houses"),
            ResourceLocation.withDefaultNamespace("village/snowy/houses"),
            ResourceLocation.withDefaultNamespace("village/taiga/houses"));

    @SubscribeEvent
    static void onServerAboutToStart(ServerAboutToStartEvent event) {
        int weight = Config.EVALUATION_ZONE_WEIGHT.get();
        if (weight <= 0) return;
        Registry<StructureTemplatePool> pools = event.getServer().registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        for (ResourceLocation id : HOUSE_POOLS) {
            StructureTemplatePool pool = pools.get(id);
            if (pool == null) {
                BeyondSolo.LOGGER.warn("Village pool {} not found, skipping Evaluation Zone", id);
                continue;
            }
            addToPool(pool, EVALUATION_ZONE, weight);
            BeyondSolo.LOGGER.info("Added Evaluation Zone to {} (weight {}, pool size now {})", id, weight, pool.templates.size());
        }
        BeyondSolo.LOGGER.info("Evaluation Zone added to {} village building pools", HOUSE_POOLS.size());
    }

    public static void addToPool(StructureTemplatePool pool, String structure, int weight) {
        StructurePoolElement element = StructurePoolElement.single(structure).apply(StructureTemplatePool.Projection.RIGID);
        for (int i = 0; i < weight; i++) pool.templates.add(element);
        List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(pool.rawTemplates);
        raw.add(Pair.of(element, weight));
        pool.rawTemplates = raw;
    }

    private VillageInjector() {}
}
