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
        com.beyondsolo.registry.ModComponents.COMPONENTS.register(modBus);
        com.beyondsolo.item.arsenal.Arsenal.init();
        ModItems.ITEMS.register(modBus);
        com.beyondsolo.skill.Skills.init();
        ModItems.TABS.register(modBus);
        Statuses.init();
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        com.beyondsolo.registry.ModEntities.ENTITY_TYPES.register(modBus);
        com.beyondsolo.gate.GateFeature.FEATURES.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        com.beyondsolo.job.JobModule.init(modBus, container);
        com.beyondsolo.shadow.ShadowModule.init(modBus);
        com.beyondsolo.economy.EconomyModule.init(modBus);
        com.beyondsolo.gear.GearModule.init(modBus);
        com.beyondsolo.enemy.EnemyModule.init(modBus);
        com.beyondsolo.crafting.CraftingModule.init(modBus);
        com.beyondsolo.penalty.PenaltyModule.init(modBus, container);
        com.beyondsolo.tower.TowerModule.init(modBus, container);
        com.beyondsolo.rune.RuneModule.init(modBus);
        com.beyondsolo.key.KeyModule.init(modBus);
        com.beyondsolo.armory.ArmoryModule.init(modBus);
        com.beyondsolo.depths.DepthsModule.init(modBus);
        com.beyondsolo.breaks.BreakModule.init(modBus);
        com.beyondsolo.redgate.RedGateModule.init(modBus, container);
        com.beyondsolo.worldboss.WorldBossModule.init(modBus, container);
        com.beyondsolo.party.PartyModule.init(modBus, container);
        com.beyondsolo.guild.GuildModule.init(modBus, container);
        com.beyondsolo.raid.RaidModule.init(modBus, container);
        com.beyondsolo.monarch.MonarchModule.init(modBus, container);
        com.beyondsolo.ruler.RulerModule.init(modBus, container);
        com.beyondsolo.chronicle.ChronicleModule.init(modBus);
        container.registerConfig(ModConfig.Type.COMMON, com.beyondsolo.quest.situation.SituationConfig.SPEC, "beyondsolo-situations.toml");
        com.beyondsolo.quest.situation.Secrets.registerTitles();
        container.registerConfig(ModConfig.Type.CLIENT, com.beyondsolo.client.ClientConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
