package com.beyondsolo.registry;

import com.beyondsolo.BeyondSolo;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;

/** Armor materials. Their model textures are textures/models/armor/<name>_layer_1/2.png (made by tools/gen_combat_assets.py). */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, BeyondSolo.MOD_ID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HUNTER = register("hunter", 1, 4, 5, 2, 15,
            SoundEvents.ARMOR_EQUIP_LEATHER, Items.LEATHER, 0f, 0f);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> KNIGHT = register("knight", 2, 6, 7, 2, 10,
            SoundEvents.ARMOR_EQUIP_IRON, Items.IRON_INGOT, 1f, 0.05f);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> DUSK = register("dusk", 3, 6, 8, 3, 15,
            SoundEvents.ARMOR_EQUIP_NETHERITE, Items.DIAMOND, 2f, 0.05f);

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> register(String name, int boots, int legs, int chest, int helmet,
                                                                         int enchant, Holder<SoundEvent> sound, ItemLike repair,
                                                                         float toughness, float knockback) {
        return MATERIALS.register(name, () -> new ArmorMaterial(Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
            map.put(ArmorItem.Type.BOOTS, boots);
            map.put(ArmorItem.Type.LEGGINGS, legs);
            map.put(ArmorItem.Type.CHESTPLATE, chest);
            map.put(ArmorItem.Type.HELMET, helmet);
            map.put(ArmorItem.Type.BODY, chest);
        }), enchant, sound, () -> Ingredient.of(repair), List.of(new ArmorMaterial.Layer(BeyondSolo.id(name))), toughness, knockback));
    }

    private ModArmorMaterials() {}
}
