package com.beyondsolo.worldboss;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.network.WorldBossFxPayload;
import com.beyondsolo.network.WorldBossPayload;
import com.beyondsolo.registry.ModComponents;
import com.mojang.serialization.Codec;
import net.minecraft.Util;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Registers the world's great threats: the two world bosses and three monarchs, the blows they throw, the Monarch's
 * Seal, the monarch armor (its pieces, materials and the aura component), the hunter's record, the network messages
 * and the config. What they do is {@link WorldBosses} (the schedule, the call, the rewards), {@link ColossalBoss} (the
 * fight) and {@link MonarchArmor}. Lang lives in {@code assets/beyondsolo_worldboss/lang}; the models and textures come
 * from {@code tools/gen_worldboss_assets.py} and {@code tools/gen_monarch_armor.py}.
 */
public final class WorldBossModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BeyondSolo.MOD_ID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BeyondSolo.MOD_ID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, BeyondSolo.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BeyondSolo.MOD_ID);

    /** A hunter's record against world bosses and monarchs. Survives death. */
    public static final Supplier<AttachmentType<WorldBossRecord>> RECORD = ATTACHMENTS.register("world_bosses",
            () -> AttachmentType.serializable(WorldBossRecord::new).copyOnDeath().build());

    /** The colour of the aura a piece of monarch armor carries (its wearer's). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> AURA = COMPONENTS.registerComponentType("monarch_aura",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT));

    public static final DeferredHolder<EntityType<?>, EntityType<TitanBoss>> TITAN = ENTITY_TYPES.register("titan",
            () -> EntityType.Builder.<TitanBoss>of(TitanBoss::new, MobCategory.MONSTER)
                    .sized(4.6f, 11.5f).eyeHeight(9.8f).fireImmune().clientTrackingRange(16).updateInterval(2).build("titan"));
    public static final DeferredHolder<EntityType<?>, EntityType<BehemothBoss>> BEHEMOTH = ENTITY_TYPES.register("behemoth",
            () -> EntityType.Builder.<BehemothBoss>of(BehemothBoss::new, MobCategory.MONSTER)
                    .sized(5.0f, 5.4f).eyeHeight(4.0f).clientTrackingRange(16).updateInterval(2).build("behemoth"));
    public static final DeferredHolder<EntityType<?>, EntityType<StillnessMonarch>> STILLNESS = ENTITY_TYPES.register("monarch_stillness",
            () -> EntityType.Builder.<StillnessMonarch>of(StillnessMonarch::new, MobCategory.MONSTER)
                    .sized(1.3f, 3.9f).eyeHeight(3.45f).fireImmune().clientTrackingRange(16).updateInterval(2).build("monarch_stillness"));
    public static final DeferredHolder<EntityType<?>, EntityType<HungerMonarch>> HUNGER = ENTITY_TYPES.register("monarch_hunger",
            () -> EntityType.Builder.<HungerMonarch>of(HungerMonarch::new, MobCategory.MONSTER)
                    .sized(1.6f, 3.4f).eyeHeight(3.0f).fireImmune().clientTrackingRange(16).updateInterval(2).build("monarch_hunger"));
    public static final DeferredHolder<EntityType<?>, EntityType<StormMonarch>> STORMS = ENTITY_TYPES.register("monarch_storms",
            () -> EntityType.Builder.<StormMonarch>of(StormMonarch::new, MobCategory.MONSTER)
                    .sized(1.5f, 4.0f).eyeHeight(3.5f).fireImmune().clientTrackingRange(16).updateInterval(2).build("monarch_storms"));
    /** What great threats throw and shoot: boulders, ice spears, storm orbs, blood shards. */
    public static final DeferredHolder<EntityType<?>, EntityType<BossProjectile>> PROJECTILE = ENTITY_TYPES.register("boss_projectile",
            () -> EntityType.Builder.<BossProjectile>of(BossProjectile::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f).fireImmune().clientTrackingRange(10).updateInterval(1).build("boss_projectile"));

    /** Break it and a monarch comes down through the gate it held shut. */
    public static final DeferredItem<MonarchSealItem> MONARCH_SEAL = ITEMS.register("monarch_seal",
            () -> new MonarchSealItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** The monarch armor pieces, by set name, in helmet, chestplate, leggings, boots order. */
    public static final Map<String, List<DeferredItem<SystemArmorItem>>> PIECES = new LinkedHashMap<>();

    private static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    static {
        for (ArmorSets.Set set : MonarchArmor.sets()) registerSet(set);
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("world_bosses", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.beyondsolo.world_bosses"))
            .icon(() -> new ItemStack(PIECES.get(MonarchArmor.STILLNESS.name).get(0).get()))
            .withTabsBefore(BeyondSolo.id("armory"))
            .displayItems((params, out) -> {
                out.accept(MONARCH_SEAL.get());
                for (List<DeferredItem<SystemArmorItem>> pieces : PIECES.values()) pieces.forEach(p -> out.accept(p.get()));
            })
            .build());

    /** Four pieces and a material for a monarch set: above divine (armor, toughness, steadiness), unbreakable. */
    private static void registerSet(ArmorSets.Set set) {
        DeferredHolder<ArmorMaterial, ArmorMaterial> material = MATERIALS.register(set.name, () -> new ArmorMaterial(
                Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                    map.put(ArmorItem.Type.BOOTS, 4);
                    map.put(ArmorItem.Type.LEGGINGS, 8);
                    map.put(ArmorItem.Type.CHESTPLATE, 11);
                    map.put(ArmorItem.Type.HELMET, 4);
                    map.put(ArmorItem.Type.BODY, 11);
                }), 20, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(Items.NETHERITE_INGOT),
                List.of(new ArmorMaterial.Layer(BeyondSolo.id(set.name))), 5f, 0.2f));
        List<DeferredItem<SystemArmorItem>> pieces = new ArrayList<>();
        for (ArmorItem.Type type : TYPES) {
            String id = set.name + "_" + switch (type) {
                case HELMET -> "helm";
                case CHESTPLATE -> "chestplate";
                case LEGGINGS -> "leggings";
                default -> "boots";
            };
            Item.Properties props = new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).fireResistant()
                    .component(DataComponents.UNBREAKABLE, new Unbreakable(false));
            pieces.add(ITEMS.register(id, () -> new SystemArmorItem(material, type, set, Map.of(), props)));
        }
        PIECES.put(set.name, pieces);
    }

    /** Every monarch armor piece. */
    public static List<DeferredItem<SystemArmorItem>> armor() {
        List<DeferredItem<SystemArmorItem>> out = new ArrayList<>();
        PIECES.values().forEach(out::addAll);
        return out;
    }

    public static EntityType<? extends ColossalBoss> type(WorldBossKind kind) {
        return switch (kind) {
            case TITAN -> TITAN.get();
            case BEHEMOTH -> BEHEMOTH.get();
            case STILLNESS -> STILLNESS.get();
            case HUNGER -> HUNGER.get();
            case STORMS -> STORMS.get();
        };
    }

    @Nullable
    public static ColossalBoss create(ServerLevel level, WorldBossKind kind) {
        return type(kind).create(level);
    }

    public static void init(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        TABS.register(modBus);
        ATTACHMENTS.register(modBus);
        MATERIALS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(WorldBossModule::payloads);
        modBus.addListener(WorldBossModule::attributes);
        modBus.addListener(WorldBossModule::defaultComponents);
        container.registerConfig(ModConfig.Type.COMMON, WorldBossConfig.SPEC, "beyondsolo-worldbosses.toml");
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(TITAN.get(), TitanBoss.attributes().build());
        event.put(BEHEMOTH.get(), BehemothBoss.attributes().build());
        event.put(STILLNESS.get(), MonarchBoss.attributes().build());
        event.put(HUNGER.get(), MonarchBoss.attributes().build());
        event.put(STORMS.get(), MonarchBoss.attributes().build());
    }

    private static void defaultComponents(ModifyDefaultComponentsEvent event) {
        for (var item : armor()) event.modify(item.get(), b -> b.set(ModComponents.GRADE.get(), ItemGrade.MONARCH));
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(WorldBossPayload.TYPE, WorldBossPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.worldboss.WorldBossClient.onPayload(p)));
        r.playToClient(WorldBossFxPayload.TYPE, WorldBossFxPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.worldboss.WorldBossWorldFx.onFx(p)));
    }

    private WorldBossModule() {}
}
