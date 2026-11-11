package com.beyondsolo.registry;

import com.beyondsolo.BeyondSolo;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);

    public static final DeferredItem<BlockItem> EVALUATION_CRYSTAL = ITEMS.registerSimpleBlockItem(ModBlocks.EVALUATION_CRYSTAL);

    @SubscribeEvent
    static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS && event.hasPermissions()) {
            event.accept(EVALUATION_CRYSTAL);
        }
    }

    private ModItems() {}
}
