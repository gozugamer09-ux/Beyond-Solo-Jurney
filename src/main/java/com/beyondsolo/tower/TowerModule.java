package com.beyondsolo.tower;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.TowerActionPayload;
import com.beyondsolo.network.TowerPayload;
import com.beyondsolo.network.TowerSyncPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The Crimson Citadel: a castle of a hundred floors in its own dimension, with a gatekeeper every tenth floor and its
 * lord on the last, the Endless Spire above it, and the boss rush in its colosseum. Registers the hunter's records,
 * the key, the Sigil of Dominion, the lord, and the network messages. The climb itself is {@link TowerRun}, the halls
 * {@link CitadelHalls}, what each floor holds {@link TowerFloors}. The dimension, its type and biome are data
 * ({@code data/beyondsolo/dimension/crimson_citadel.json}). Lang lives in {@code assets/beyondsolo_tower/lang}; the lord's
 * model and the item textures come from {@code tools/gen_tower_assets.py}.
 */
public final class TowerModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);

    /** A hunter's Citadel records. Survive death. */
    public static final Supplier<AttachmentType<TowerData>> CITADEL = ATTACHMENTS.register("citadel",
            () -> AttachmentType.serializable(TowerData::new).copyOnDeath().build());

    /** Bought at the System shop: bound on use, the Citadel opens to its holder for good. */
    public static final DeferredItem<CitadelKeyItem> CITADEL_KEY = ITEMS.register("citadel_key",
            () -> new CitadelKeyItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** The lord's rare drop: proof of dominion over the Citadel, and the key to what lies past it. */
    public static final DeferredItem<DominionSigilItem> SIGIL_OF_DOMINION = ITEMS.register("sigil_of_dominion",
            () -> new DominionSigilItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** Ozrath, Lord of the Crimson Citadel. */
    public static final DeferredHolder<EntityType<?>, EntityType<CitadelLordEntity>> CITADEL_LORD = ENTITY_TYPES.register("citadel_lord",
            () -> EntityType.Builder.<CitadelLordEntity>of(CitadelLordEntity::new, MobCategory.MONSTER)
                    .sized(1.4f, 3.4f).eyeHeight(3.0f).fireImmune().clientTrackingRange(12).build("citadel_lord"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("citadel", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.beyondsolo.citadel"))
            .icon(() -> new ItemStack(CITADEL_KEY.get()))
            .withTabsBefore(BeyondSolo.id("keys"))
            .displayItems((params, out) -> {
                out.accept(CITADEL_KEY.get());
                out.accept(SIGIL_OF_DOMINION.get());
            })
            .build());

    public static void init(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        TABS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(TowerModule::payloads);
        modBus.addListener(TowerModule::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(CITADEL_LORD.get(), CitadelLordEntity.attributes().build());
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(TowerPayload.TYPE, TowerPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.tower.TowerClient.onRun(p)));
        r.playToClient(TowerSyncPayload.TYPE, TowerSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.tower.TowerClient.onSync(p)));
        r.playToServer(TowerActionPayload.TYPE, TowerActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) TowerRun.action(player, p.action(), p.arg());
                }));
    }

    private TowerModule() {}
}
