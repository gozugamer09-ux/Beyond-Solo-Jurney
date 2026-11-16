package com.beyondsolo.armory;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gear.GearModule;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.registry.ModComponents;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The armory's items: four pieces for each set of {@link ArmorCatalog}, their armor materials (one per set, for the
 * worn texture) and the ARMORY creative tab. Called once from the mod constructor.
 */
public final class ArmoryModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, BeyondSolo.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BeyondSolo.MOD_ID);

    /** Every armory piece, by set name, in helmet, chestplate, leggings, boots order. */
    public static final Map<String, List<DeferredItem<SystemArmorItem>>> PIECES = new LinkedHashMap<>();

    /**
     * Armor points (boots, leggings, chestplate, helmet), toughness and knockback resistance by rank (E..S, then
     * divine) and build (heavy, medium, light). Netherite is 3, 6, 8, 3 with 3 toughness.
     */
    static final float[][][] VALUES = {
            {{2, 5, 6, 2, 1, 0}, {2, 4, 5, 2, 0.5f, 0}, {1, 3, 4, 1, 0, 0}},
            {{2, 5, 7, 2, 1.5f, 0}, {2, 5, 6, 2, 1, 0}, {1, 4, 5, 2, 0.5f, 0}},
            {{3, 6, 8, 3, 2, 0.05f}, {2, 5, 7, 2, 1.5f, 0}, {2, 4, 6, 2, 1, 0}},
            {{3, 6, 8, 3, 2.5f, 0.05f}, {3, 6, 7, 3, 2, 0}, {2, 5, 6, 2, 1, 0}},
            {{3, 7, 9, 3, 3, 0.1f}, {3, 6, 8, 3, 2.5f, 0.05f}, {2, 5, 7, 2, 1.5f, 0}},
            {{4, 7, 9, 4, 3.5f, 0.1f}, {3, 7, 8, 3, 3, 0.05f}, {3, 5, 7, 3, 2, 0}},
            {{4, 8, 10, 4, 4, 0.15f}, {4, 7, 9, 3, 3.5f, 0.1f}, {3, 6, 8, 3, 2.5f, 0.05f}},
    };

    private static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    static {
        ArmorCatalog.init();
        for (ArmorCatalog.Def def : ArmorCatalog.ALL) register(def);
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("armory", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.beyondsolo.armory"))
            .icon(() -> new ItemStack(PIECES.get("worldbreaker").get(0).get()))
            .withTabsBefore(BeyondSolo.id("runes"))
            .displayItems((params, out) -> {
                for (ArmorSets.Set set : ArmorCatalog.classSets()) {
                    List<DeferredItem<SystemArmorItem>> pieces = piecesOf(set);
                    if (pieces != null) pieces.forEach(p -> out.accept(p.get()));
                }
            })
            .build());

    private static void register(ArmorCatalog.Def def) {
        ArmorSets.Set set = def.set();
        float[] v = VALUES[Math.min(set.tier, VALUES.length - 1)][def.build().ordinal()];
        DeferredHolder<ArmorMaterial, ArmorMaterial> material = MATERIALS.register(set.name, () -> new ArmorMaterial(
                Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                    map.put(ArmorItem.Type.BOOTS, (int) v[0]);
                    map.put(ArmorItem.Type.LEGGINGS, (int) v[1]);
                    map.put(ArmorItem.Type.CHESTPLATE, (int) v[2]);
                    map.put(ArmorItem.Type.HELMET, (int) v[3]);
                    map.put(ArmorItem.Type.BODY, (int) v[2]);
                }), 15, sound(set, def.build()), () -> Ingredient.of(repair(set, def.build())),
                List.of(new ArmorMaterial.Layer(BeyondSolo.id(set.name))), v[4], v[5]));
        List<DeferredItem<SystemArmorItem>> pieces = new ArrayList<>();
        int durability = 18 + 5 * Math.min(set.tier, 5) + (def.build() == ArmorCatalog.Build.HEAVY ? 4 : 0);
        for (ArmorItem.Type type : TYPES) {
            String id = set.name + "_" + switch (type) {
                case HELMET -> "helm";
                case CHESTPLATE -> "chestplate";
                case LEGGINGS -> "leggings";
                default -> "boots";
            };
            Item.Properties props = new Item.Properties().rarity(rarity(set.grade));
            if (set.divine()) props.stacksTo(1).component(DataComponents.UNBREAKABLE, new Unbreakable(false));
            else props.durability(type.getDurability(durability));
            if (set.grade.atLeast(ItemGrade.LEGENDARY)) props.fireResistant();
            pieces.add(ITEMS.register(id, () -> new SystemArmorItem(material, type, set, Map.of(), props)));
        }
        PIECES.put(set.name, pieces);
    }

    private static Holder<SoundEvent> sound(ArmorSets.Set set, ArmorCatalog.Build build) {
        if (set.divine()) return SoundEvents.ARMOR_EQUIP_NETHERITE;
        return switch (build) {
            case HEAVY -> set.tier >= 3 ? SoundEvents.ARMOR_EQUIP_NETHERITE : SoundEvents.ARMOR_EQUIP_IRON;
            case MEDIUM -> SoundEvents.ARMOR_EQUIP_CHAIN;
            case LIGHT -> SoundEvents.ARMOR_EQUIP_LEATHER;
        };
    }

    private static Item repair(ArmorSets.Set set, ArmorCatalog.Build build) {
        if (set.tier >= 4) return Items.NETHERITE_SCRAP;
        if (set.tier >= 2) return Items.DIAMOND;
        return build == ArmorCatalog.Build.LIGHT ? Items.LEATHER : Items.IRON_INGOT;
    }

    private static Rarity rarity(ItemGrade grade) {
        return switch (grade) {
            case COMMON -> Rarity.COMMON;
            case RARE -> Rarity.UNCOMMON;
            case EPIC -> Rarity.RARE;
            default -> Rarity.EPIC;
        };
    }

    /** The four pieces of any set that has them: the armory's, the brute's and the shop's. */
    public static @Nullable List<DeferredItem<SystemArmorItem>> piecesOf(ArmorSets.Set set) {
        List<DeferredItem<SystemArmorItem>> pieces = PIECES.get(set.name);
        if (pieces != null) return pieces;
        pieces = GearModule.SET_PIECES.get(set.name);
        if (pieces != null) return pieces;
        List<DeferredItem<SystemArmorItem>> shop = new ArrayList<>();
        for (var item : com.beyondsolo.registry.ModItems.ARMOR) if (item.get().set() == set) shop.add(item);
        return shop.size() == 4 ? shop : null;
    }

    /** Every armory piece. */
    public static List<DeferredItem<SystemArmorItem>> all() {
        List<DeferredItem<SystemArmorItem>> out = new ArrayList<>();
        PIECES.values().forEach(out::addAll);
        return out;
    }

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        MATERIALS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(ArmoryModule::defaultGrades);
    }

    private static void defaultGrades(ModifyDefaultComponentsEvent event) {
        for (var item : all()) {
            SystemArmorItem armor = item.get();
            event.modify(armor, b -> b.set(ModComponents.GRADE.get(), armor.grade()));
        }
    }

    private ArmoryModule() {}
}
