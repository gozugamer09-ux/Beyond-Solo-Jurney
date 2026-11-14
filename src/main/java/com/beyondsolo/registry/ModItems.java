package com.beyondsolo.registry;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.Buildups;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ElementCures;
import com.beyondsolo.combat.ModAttributes;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ElixirItem;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.item.SystemWeaponItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BeyondSolo.MOD_ID);

    public static final DeferredItem<BlockItem> EVALUATION_CRYSTAL = ITEMS.registerSimpleBlockItem(ModBlocks.EVALUATION_CRYSTAL);

    /** The sealed parcel of a villager's delivery request. */
    public static final DeferredItem<com.beyondsolo.item.ParcelItem> SEALED_PARCEL = ITEMS.register("sealed_parcel",
            () -> new com.beyondsolo.item.ParcelItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // ---- weapon tiers ----
    /** Hunter steel: iron-grade gear for E and D rank hunters. */
    public static final Tier HUNTER_STEEL = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 450, 6.5f, 2.0f, 14, () -> Ingredient.of(Items.IRON_INGOT));
    /** Duskforged: diamond-grade elemental weapons. */
    public static final Tier DUSKFORGED = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1400, 8f, 3.0f, 15, () -> Ingredient.of(Items.DIAMOND));

    // ---- weapons (moveset from data/beyondsolo/weapon_attributes) ----
    public static final DeferredItem<SystemWeaponItem> HUNTER_DAGGER = weapon("hunter_dagger", HUNTER_STEEL, 0, -1.6f, DamageElement.BLOOD, 14, 0.6f, Rarity.COMMON);
    public static final DeferredItem<SystemWeaponItem> KNIGHT_SWORD = weapon("knight_sword", HUNTER_STEEL, 3, -2.4f, DamageElement.BLOOD, 7, 1.0f, Rarity.COMMON);
    public static final DeferredItem<SystemWeaponItem> STEEL_GREATSWORD = weapon("steel_greatsword", HUNTER_STEEL, 6, -3.0f, DamageElement.BLOOD, 12, 1.8f, Rarity.COMMON);
    public static final DeferredItem<SystemWeaponItem> HUNTING_SPEAR = weapon("hunting_spear", HUNTER_STEEL, 4, -2.7f, DamageElement.BLOOD, 10, 1.2f, Rarity.COMMON);
    public static final DeferredItem<SystemWeaponItem> EMBERBRAND = weapon("emberbrand", DUSKFORGED, 3, -2.4f, DamageElement.FIRE, 24, 1.0f, Rarity.RARE);
    public static final DeferredItem<SystemWeaponItem> VENOM_FANG = weapon("venom_fang", DUSKFORGED, 0, -1.6f, DamageElement.POISON, 14, 0.6f, Rarity.RARE);
    public static final DeferredItem<SystemWeaponItem> CRIMSON_REAPER = weapon("crimson_reaper", DUSKFORGED, 5, -2.9f, DamageElement.CRIMSON, 22, 1.6f, Rarity.EPIC);
    public static final DeferredItem<SystemWeaponItem> BLOODLETTER = weapon("bloodletter", DUSKFORGED, 3, -2.3f, DamageElement.BLOOD, 22, 1.0f, Rarity.EPIC);

    public static final List<DeferredItem<SystemWeaponItem>> WEAPONS = List.of(HUNTER_DAGGER, KNIGHT_SWORD, STEEL_GREATSWORD,
            HUNTING_SPEAR, EMBERBRAND, VENOM_FANG, CRIMSON_REAPER, BLOODLETTER);

    // ---- armor ----
    public static final DeferredItem<SystemArmorItem> HUNTER_HOOD = armor("hunter_hood", ModArmorMaterials.HUNTER, ArmorItem.Type.HELMET, ArmorSets.HUNTER, 15, Map.of(ModAttributes.EXHAUSTION_RESISTANCE, 0.05));
    public static final DeferredItem<SystemArmorItem> HUNTER_COAT = armor("hunter_coat", ModArmorMaterials.HUNTER, ArmorItem.Type.CHESTPLATE, ArmorSets.HUNTER, 15, Map.of(ModAttributes.EXHAUSTION_RESISTANCE, 0.05));
    public static final DeferredItem<SystemArmorItem> HUNTER_LEGGINGS = armor("hunter_leggings", ModArmorMaterials.HUNTER, ArmorItem.Type.LEGGINGS, ArmorSets.HUNTER, 15, Map.of(ModAttributes.EXHAUSTION_RESISTANCE, 0.05));
    public static final DeferredItem<SystemArmorItem> HUNTER_BOOTS = armor("hunter_boots", ModArmorMaterials.HUNTER, ArmorItem.Type.BOOTS, ArmorSets.HUNTER, 15, Map.of(ModAttributes.EXHAUSTION_RESISTANCE, 0.05));

    public static final DeferredItem<SystemArmorItem> KNIGHT_HELM = armor("knight_helm", ModArmorMaterials.KNIGHT, ArmorItem.Type.HELMET, ArmorSets.KNIGHT, 22, Map.of(ModAttributes.BLOOD_RESISTANCE, 0.08));
    public static final DeferredItem<SystemArmorItem> KNIGHT_CUIRASS = armor("knight_cuirass", ModArmorMaterials.KNIGHT, ArmorItem.Type.CHESTPLATE, ArmorSets.KNIGHT, 22, Map.of(ModAttributes.BLOOD_RESISTANCE, 0.08));
    public static final DeferredItem<SystemArmorItem> KNIGHT_GREAVES = armor("knight_greaves", ModArmorMaterials.KNIGHT, ArmorItem.Type.LEGGINGS, ArmorSets.KNIGHT, 22, Map.of(ModAttributes.BLOOD_RESISTANCE, 0.08));
    public static final DeferredItem<SystemArmorItem> KNIGHT_SABATONS = armor("knight_sabatons", ModArmorMaterials.KNIGHT, ArmorItem.Type.BOOTS, ArmorSets.KNIGHT, 22, Map.of(ModAttributes.BLOOD_RESISTANCE, 0.08));

    private static final Map<Holder<Attribute>, Double> DUSK_PIECE = Map.of(ModAttributes.FIRE_RESISTANCE, 0.06,
            ModAttributes.POISON_RESISTANCE, 0.06, ModAttributes.CRIMSON_RESISTANCE, 0.06);
    public static final DeferredItem<SystemArmorItem> DUSK_HELM = armor("dusk_helm", ModArmorMaterials.DUSK, ArmorItem.Type.HELMET, ArmorSets.DUSK, 33, DUSK_PIECE);
    public static final DeferredItem<SystemArmorItem> DUSK_CHESTPLATE = armor("dusk_chestplate", ModArmorMaterials.DUSK, ArmorItem.Type.CHESTPLATE, ArmorSets.DUSK, 33, DUSK_PIECE);
    public static final DeferredItem<SystemArmorItem> DUSK_LEGGINGS = armor("dusk_leggings", ModArmorMaterials.DUSK, ArmorItem.Type.LEGGINGS, ArmorSets.DUSK, 33, DUSK_PIECE);
    public static final DeferredItem<SystemArmorItem> DUSK_BOOTS = armor("dusk_boots", ModArmorMaterials.DUSK, ArmorItem.Type.BOOTS, ArmorSets.DUSK, 33, DUSK_PIECE);

    public static final List<DeferredItem<SystemArmorItem>> ARMOR = List.of(HUNTER_HOOD, HUNTER_COAT, HUNTER_LEGGINGS, HUNTER_BOOTS,
            KNIGHT_HELM, KNIGHT_CUIRASS, KNIGHT_GREAVES, KNIGHT_SABATONS, DUSK_HELM, DUSK_CHESTPLATE, DUSK_LEGGINGS, DUSK_BOOTS);

    // ---- remedies ----
    /** The only cure for deep burn. */
    public static final DeferredItem<ElixirItem> QUENCHING_ELIXIR = elixir("quenching_elixir", DamageElement.FIRE, Rarity.UNCOMMON, e -> {
        e.removeEffectsCuredBy(ElementCures.ELIXIR);
        Buildups.clear(e, Statuses.BURN);
        e.clearFire();
        e.addEffect(new MobEffectInstance(ModEffects.FIRE_WARD, 60 * 20));
    });
    public static final DeferredItem<ElixirItem> ANTIDOTE = elixir("antidote", DamageElement.POISON, Rarity.COMMON, e -> {
        e.removeEffectsCuredBy(ElementCures.ANTIDOTE);
        e.removeEffect(MobEffects.POISON);
        Buildups.clear(e, Statuses.VENOM);
        e.addEffect(new MobEffectInstance(ModEffects.POISON_WARD, 20 * 20));
    });
    /** Cures heavy venom too. */
    public static final DeferredItem<ElixirItem> GREATER_ANTIDOTE = elixir("greater_antidote", DamageElement.POISON, Rarity.UNCOMMON, e -> {
        e.removeEffectsCuredBy(ElementCures.GREATER_ANTIDOTE);
        e.removeEffect(MobEffects.POISON);
        e.removeEffect(MobEffects.WITHER);
        Buildups.clear(e, Statuses.VENOM);
        e.addEffect(new MobEffectInstance(ModEffects.POISON_WARD, 90 * 20));
    });
    public static final DeferredItem<ElixirItem> CLOTTING_TONIC = elixir("clotting_tonic", DamageElement.BLOOD, Rarity.COMMON, e -> {
        e.removeEffectsCuredBy(ElementCures.TONIC);
        Buildups.clear(e, Buildups.BLOOD);
        Buildups.clear(e, Statuses.CRIMSON);
        e.addEffect(new MobEffectInstance(ModEffects.BLOOD_WARD, 60 * 20));
    });

    public static final List<DeferredItem<ElixirItem>> REMEDIES = List.of(QUENCHING_ELIXIR, ANTIDOTE, GREATER_ANTIDOTE, CLOTTING_TONIC);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("combat", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.beyondsolo.combat"))
            .icon(() -> new ItemStack(BLOODLETTER.get()))
            .displayItems((params, out) -> {
                WEAPONS.forEach(w -> out.accept(w.get()));
                ARMOR.forEach(a -> out.accept(a.get()));
                REMEDIES.forEach(r -> out.accept(r.get()));
            })
            .build());

    private static DeferredItem<SystemWeaponItem> weapon(String name, Tier tier, int damage, float speed, DamageElement element,
                                                         float buildup, float weight, Rarity rarity) {
        return ITEMS.register(name, () -> new SystemWeaponItem(tier, damage, speed, element, buildup, weight, new Item.Properties().rarity(rarity)));
    }

    private static DeferredItem<SystemArmorItem> armor(String name, Holder<ArmorMaterial> material, ArmorItem.Type type, ArmorSets.Set set,
                                                       int durability, Map<Holder<Attribute>, Double> extras) {
        return ITEMS.register(name, () -> new SystemArmorItem(material, type, set, extras,
                new Item.Properties().durability(type.getDurability(durability))));
    }

    private static DeferredItem<ElixirItem> elixir(String name, DamageElement element, Rarity rarity, Consumer<LivingEntity> effect) {
        return ITEMS.register(name, () -> new ElixirItem(element, effect, new Item.Properties().stacksTo(16).rarity(rarity)));
    }

    @SubscribeEvent
    static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS && event.hasPermissions()) {
            event.accept(EVALUATION_CRYSTAL);
        }
    }

    private ModItems() {}
}
