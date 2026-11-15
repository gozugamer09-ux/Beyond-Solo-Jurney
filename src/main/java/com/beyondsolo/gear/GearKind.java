package com.beyondsolo.gear;

import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.item.SystemBowItem;
import com.beyondsolo.item.arsenal.ArsenalWeapon;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** What a piece of System gear is, for rolls, enhancement and infusions. */
public enum GearKind {
    MELEE, BOW, ARMOR;

    /** The stack's kind, or null if it is not System gear. */
    public static @Nullable GearKind of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof SystemBowItem) return BOW;
        if (stack.getItem() instanceof ArsenalWeapon) return MELEE;
        if (stack.getItem() instanceof SystemArmorItem) return ARMOR;
        return null;
    }

    public boolean weapon() {
        return this != ARMOR;
    }
}
