package com.beyondsolo.light;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.chronicle.title.PerkLine;
import com.beyondsolo.chronicle.title.TitleCategory;
import com.beyondsolo.chronicle.title.TitleDef;
import com.beyondsolo.chronicle.title.TitlePerk;
import com.beyondsolo.chronicle.title.TitleTier;
import com.beyondsolo.chronicle.title.Titles;
import com.beyondsolo.network.LightActionPayload;
import com.beyondsolo.network.LightFxPayload;
import com.beyondsolo.network.LightSyncPayload;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestTemplates;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

/**
 * Registers the light ({@link LightAPI}): the hunter's road in it ({@code beyondsolo:light}), the Envoy of Dawn, the
 * light's shapes ({@link LightConstruct}), the Envoy's feather, the network messages, the settings
 * ({@code beyondsolo-light.toml}), the Investiture's quest and the titles a ruler of light earns. Lang lives in
 * {@code assets/beyondsolo_light/lang}; models, animations, skins and icons come from {@code tools/gen_light_assets.py}.
 * Called once from the mod constructor.
 */
public final class LightModule {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);

    /** Per-player road in the light. Survives death. */
    public static final Supplier<AttachmentType<LightData>> LIGHT = ATTACHMENTS.register("light",
            () -> AttachmentType.serializable(LightData::new).copyOnDeath().build());

    /** The Envoy of Dawn: the light's messenger, in its chapel and at the vigil. */
    public static final DeferredHolder<EntityType<?>, EntityType<EnvoyEntity>> ENVOY = ENTITY_TYPES.register("light_envoy",
            () -> EntityType.Builder.<EnvoyEntity>of(EnvoyEntity::new, MobCategory.MISC)
                    .sized(1.0f, 3.2f).eyeHeight(2.8f).fireImmune().clientTrackingRange(10).build("light_envoy"));

    /** One of the light's greater shapes, for a moment (see {@link LightConstruct}). */
    public static final DeferredHolder<EntityType<?>, EntityType<LightConstruct>> CONSTRUCT = ENTITY_TYPES.register("light_construct",
            () -> EntityType.Builder.<LightConstruct>of(LightConstruct::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f).fireImmune().noSave().clientTrackingRange(10).updateInterval(4).build("light_construct"));

    /** The Envoy's feather: the light's call to the Investiture, and the key to its vigil. */
    public static final DeferredItem<EnvoyFeatherItem> FEATHER = ITEMS.register("envoy_feather",
            () -> new EnvoyFeatherItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** The Investiture's quest: keep the vigil and take up a mantle. */
    public static final String QUEST_TEMPLATE = BeyondSolo.id("light/investiture").toString();
    /** Quest progress key the Investiture advances when a mantle is taken up. */
    public static final String INVESTITURE_KEY = BeyondSolo.id("light_investiture").toString();

    /** Titles: waking sworn, one per mantle, and two a ruler of light earns. */
    public static final String VESSEL = TitleDef.PREFIX + "vessel_of_light";
    public static final String DAWN_OVER_CROWNS = TitleDef.PREFIX + "dawn_over_crowns";
    public static final String UNBROKEN_LIGHT = TitleDef.PREFIX + "unbroken_light";

    public static void init(IEventBus modBus, ModContainer container) {
        ATTACHMENTS.register(modBus);
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(LightModule::payloads);
        modBus.addListener(LightModule::attributes);
        container.registerConfig(ModConfig.Type.COMMON, LightConfig.SPEC, "beyondsolo-light.toml");
        QuestTemplates.register(QUEST_TEMPLATE, (p, c) -> QuestBuilder.of(QUEST_TEMPLATE, QuestCategory.SET, p)
                .fixed()
                .text("light_investiture")
                .custom(INVESTITURE_KEY, 1, Component.translatable("quest_objective.beyondsolo.light_investiture"))
                .xp(20000).statPoints(10)
                .build());
        titles();
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(ENVOY.get(), EnvoyEntity.attributes().build());
    }

    /** First guesses for the balancing pass: a ruler's titles weigh what a monarch's do. */
    private static void titles() {
        Titles.register(new TitleDef("vessel_of_light", TitleCategory.SYSTEM, TitleTier.LEGENDARY, null, 0,
                List.of(new PerkLine(TitlePerk.ELEMENTAL, 0.06f), new PerkLine(TitlePerk.VITALITY, 0.04f)), true));
        for (Mantle m : Mantle.values()) {
            Titles.register(new TitleDef("ruler_of_" + m.id(), TitleCategory.SYSTEM, TitleTier.MYTHIC, null, 0,
                    List.of(new PerkLine(TitlePerk.MIGHT, 0.10f), new PerkLine(TitlePerk.VITALITY, 0.10f), new PerkLine(TitlePerk.ELEMENTAL, 0.10f)), false));
        }
        Titles.register(new TitleDef("dawn_over_crowns", TitleCategory.HUNT, TitleTier.MYTHIC, null, 0,
                List.of(new PerkLine(TitlePerk.BOSS_DAMAGE, 0.12f), new PerkLine(TitlePerk.GIANT_DAMAGE, 0.10f), new PerkLine(TitlePerk.MIGHT, 0.06f)), false));
        Titles.register(new TitleDef("unbroken_light", TitleCategory.HUNT, TitleTier.MYTHIC, null, 0,
                List.of(new PerkLine(TitlePerk.GUARD, 0.10f), new PerkLine(TitlePerk.VITALITY, 0.08f)), true));
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(LightSyncPayload.TYPE, LightSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.light.LightClient.onSync(p)));
        r.playToClient(LightFxPayload.TYPE, LightFxPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.light.LightClient.onFx(p)));
        r.playToServer(LightActionPayload.TYPE, LightActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer player) LightAPI.handle(player, p);
                }));
    }

    private LightModule() {}
}
