package com.beyondsolo;

import com.beyondsolo.combat.ModAttributes;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.registry.ModArmorMaterials;
import com.beyondsolo.registry.ModAttachments;
import com.beyondsolo.registry.ModBlockEntities;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(BeyondSolo.MOD_ID)
public final class BeyondSolo {
    public static final String MOD_ID = "beyondsolo";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BeyondSolo(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModAttributes.ATTRIBUTES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModArmorMaterials.MATERIALS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        Statuses.init();
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        com.beyondsolo.registry.ModEntities.ENTITY_TYPES.register(modBus);
        com.beyondsolo.gate.GateFeature.FEATURES.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, com.beyondsolo.client.ClientConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
