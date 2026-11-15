package com.beyondsolo.economy;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.EconomyActionPayload;
import com.beyondsolo.network.EconomySyncPayload;
import com.beyondsolo.network.GoldPayload;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Registers the hunter's economy: gold and the System storage (player data), the storage window, the hunter
 * license item and its card data, and the network messages. Called once from the mod constructor.
 */
public final class EconomyModule {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, BeyondSolo.MOD_ID);

    /** Gold, storage, clears and license state. Survives death. */
    public static final Supplier<AttachmentType<HunterData>> HUNTER = ATTACHMENTS.register("hunter",
            () -> AttachmentType.serializable(HunterData::new).copyOnDeath().build());

    /** What a hunter license shows. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LicenseInfo>> LICENSE = COMPONENTS.registerComponentType("license",
            b -> b.persistent(LicenseInfo.CODEC).networkSynchronized(LicenseInfo.STREAM_CODEC));

    public static final DeferredItem<HunterLicenseItem> HUNTER_LICENSE = ITEMS.register("hunter_license",
            () -> new HunterLicenseItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant()));

    public static final DeferredHolder<MenuType<?>, MenuType<StorageMenu>> STORAGE_MENU = MENUS.register("system_storage",
            () -> new MenuType<>(StorageMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(EconomyModule::payloads);
        modBus.addListener(EconomyModule::creativeTab);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(EconomySyncPayload.TYPE, EconomySyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.economy.EconomyClient.onSync(p)));
        r.playToClient(GoldPayload.TYPE, GoldPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.economy.EconomyClient.onGold(p)));
        r.playToServer(EconomyActionPayload.TYPE, EconomyActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player)) return;
                    switch (p.action()) {
                        case EconomyActionPayload.OPEN_STORAGE -> SystemStorage.open(player);
                        case EconomyActionPayload.BUY -> ShopService.buy(player, p.entry(), p.amount());
                        case EconomyActionPayload.SELL -> ShopService.sell(player, p.amount(), false);
                        case EconomyActionPayload.SELL_ALL -> ShopService.sell(player, p.amount(), true);
                        default -> {}
                    }
                }));
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(BeyondSolo.id("combat"))) event.accept(HUNTER_LICENSE);
    }

    private EconomyModule() {}
}
