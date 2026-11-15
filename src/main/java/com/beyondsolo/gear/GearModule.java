package com.beyondsolo.gear;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.network.ForgeActionPayload;
import com.beyondsolo.network.ForgeResultPayload;
import com.beyondsolo.registry.ModComponents;
import com.beyondsolo.registry.ModItems;
import com.beyondsolo.skill.SkillElement;
import com.mojang.serialization.Codec;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Item grades, bonus stats, set bonuses, enhancement and infusions: the data components on gear, the forge's
 * materials, the six boss signature armor sets, and the forge's network messages. Called once from the mod
 * constructor.
 */
public final class GearModule {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BeyondSolo.MOD_ID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, BeyondSolo.MOD_ID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, BeyondSolo.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BeyondSolo.MOD_ID);

    // ------------------------------------------------------------------ components

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BonusStats>> BONUS_STATS = COMPONENTS.registerComponentType("bonus_stats",
            b -> b.persistent(BonusStats.CODEC).networkSynchronized(BonusStats.STREAM_CODEC));
    /** Enhancement level, 1..10 (missing = +0). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENHANCE = COMPONENTS.registerComponentType("enhance",
            b -> b.persistent(Codec.intRange(0, Enhancement.MAX)).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** The element a weapon is infused with. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SkillElement>> INFUSION = COMPONENTS.registerComponentType("infusion",
            b -> b.persistent(Codec.STRING.xmap(SkillElement::valueOf, SkillElement::name))
                    .networkSynchronized(ByteBufCodecs.idMapper(i -> SkillElement.values()[i], Enum::ordinal)));

    // ------------------------------------------------------------------ forge materials

    public static final DeferredItem<ForgeMaterialItem> REFINING_STONE = ITEMS.register("refining_stone",
            () -> new ForgeMaterialItem(null, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<ForgeMaterialItem> WARD_SEAL = ITEMS.register("ward_seal",
            () -> new ForgeMaterialItem(null, new Item.Properties().rarity(Rarity.RARE).stacksTo(16)));
    public static final Map<SkillElement, DeferredItem<ForgeMaterialItem>> CRYSTALS = new EnumMap<>(SkillElement.class);

    static {
        for (SkillElement e : SkillElement.values()) {
            if (e == SkillElement.PHYSICAL) continue;
            CRYSTALS.put(e, ITEMS.register(e.name().toLowerCase() + "_infusion_crystal",
                    () -> new ForgeMaterialItem(e, new Item.Properties().rarity(Rarity.RARE).stacksTo(16))));
        }
    }

    // ------------------------------------------------------------------ boss signature armor

    private static final Map<String, DeferredHolder<ArmorMaterial, ArmorMaterial>> MATERIAL = new LinkedHashMap<>();
    /** Every boss set piece, by set name, in helmet, chestplate, leggings, boots order. */
    public static final Map<String, List<DeferredItem<SystemArmorItem>>> SET_PIECES = new LinkedHashMap<>();

    static {
        BossSets.init();
        material("stonehide", 2, 5, 6, 2, 1f, 0.05f, SoundEvents.ARMOR_EQUIP_IRON);
        material("mirefang", 2, 5, 6, 2, 1f, 0f, SoundEvents.ARMOR_EQUIP_LEATHER);
        material("rimeguard", 3, 6, 8, 3, 2f, 0.05f, SoundEvents.ARMOR_EQUIP_DIAMOND);
        material("gloomreaver", 3, 6, 7, 3, 2f, 0f, SoundEvents.ARMOR_EQUIP_NETHERITE);
        material("emberwrought", 3, 6, 8, 3, 3f, 0.1f, SoundEvents.ARMOR_EQUIP_NETHERITE);
        material("sovereign", 3, 7, 9, 4, 3.5f, 0.1f, SoundEvents.ARMOR_EQUIP_NETHERITE);
        for (ArmorSets.Set set : BossSets.ALL) {
            List<DeferredItem<SystemArmorItem>> pieces = new ArrayList<>();
            int durability = 22 + 5 * set.grade.tier();
            for (ArmorItem.Type type : new ArmorItem.Type[]{ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS}) {
                String id = set.name + "_" + switch (type) {
                    case HELMET -> "helm";
                    case CHESTPLATE -> "chestplate";
                    case LEGGINGS -> "leggings";
                    default -> "boots";
                };
                Item.Properties props = new Item.Properties().durability(type.getDurability(durability))
                        .rarity(set.grade.atLeast(ItemGrade.LEGENDARY) ? Rarity.EPIC : Rarity.RARE);
                if (set.grade.atLeast(ItemGrade.LEGENDARY)) props.fireResistant();
                pieces.add(ITEMS.register(id, () -> new SystemArmorItem(MATERIAL.get(set.name), type, set, Map.of(), props)));
            }
            SET_PIECES.put(set.name, pieces);
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("gear", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.beyondsolo.gear"))
            .icon(() -> new ItemStack(REFINING_STONE.get()))
            .withTabsBefore(BeyondSolo.id("arsenal"))
            .displayItems((params, out) -> {
                out.accept(REFINING_STONE.get());
                out.accept(WARD_SEAL.get());
                CRYSTALS.values().forEach(c -> out.accept(c.get()));
                SET_PIECES.values().forEach(list -> list.forEach(p -> out.accept(p.get())));
            })
            .build());

    private static void material(String name, int boots, int legs, int chest, int helmet, float toughness, float knockback, Holder<SoundEvent> sound) {
        MATERIAL.put(name, MATERIALS.register(name, () -> new ArmorMaterial(Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
            map.put(ArmorItem.Type.BOOTS, boots);
            map.put(ArmorItem.Type.LEGGINGS, legs);
            map.put(ArmorItem.Type.CHESTPLATE, chest);
            map.put(ArmorItem.Type.HELMET, helmet);
            map.put(ArmorItem.Type.BODY, chest);
        }), 15, sound, () -> Ingredient.of(Items.NETHERITE_SCRAP), List.of(new ArmorMaterial.Layer(BeyondSolo.id(name))), toughness, knockback)));
    }

    /** All pieces of every boss set. */
    public static List<DeferredItem<SystemArmorItem>> bossArmor() {
        List<DeferredItem<SystemArmorItem>> out = new ArrayList<>();
        SET_PIECES.values().forEach(out::addAll);
        return out;
    }

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        MATERIALS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(GearModule::payloads);
        modBus.addListener(GearModule::defaultGrades);
    }

    private static void defaultGrades(ModifyDefaultComponentsEvent event) {
        for (var item : bossArmor()) {
            SystemArmorItem armor = item.get();
            event.modify(armor, b -> b.set(ModComponents.GRADE.get(), armor.grade()));
        }
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(ForgeResultPayload.TYPE, ForgeResultPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.gear.GearClient.onResult(p)));
        r.playToServer(ForgeActionPayload.TYPE, ForgeActionPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    if (!(ctx.player() instanceof ServerPlayer player)) return;
                    switch (p.action()) {
                        case ForgeActionPayload.REFINE -> ForgeService.refine(player, p.slot(), p.arg() != 0);
                        case ForgeActionPayload.INFUSE -> ForgeService.infuse(player, p.slot(), p.arg());
                        default -> {}
                    }
                }));
    }

    private GearModule() {}
}
