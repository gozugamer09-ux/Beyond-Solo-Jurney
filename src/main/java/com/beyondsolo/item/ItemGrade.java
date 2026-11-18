package com.beyondsolo.item;

import com.beyondsolo.registry.ModComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * How rare and strong a piece of gear is, from starter gear to the one-of-a-kind divine weapons, and above them the
 * monarch armor that only a slain monarch leaves ({@code worldboss.MonarchArmor}). Every System item
 * carries its grade as the {@link ModComponents#GRADE} component, so later steps (enhancement, boss loot) can change
 * it per stack. The client colours the name, the tooltip frame and dropped items by it.
 *
 * @param color  main colour (name, label, loot beam)
 * @param accent second colour, for the shimmer on legendary and up
 */
public enum ItemGrade implements StringRepresentable {
    COMMON(0xC8CDD4, 0x8A929C),
    RARE(0x4FA8FF, 0x9ED8FF),
    EPIC(0xB46CFF, 0xE0B8FF),
    LEGENDARY(0xFFB02E, 0xFFE9A0),
    MYTHIC(0xFF3D5A, 0xFF9A5A),
    DIVINE(0xFFF4C8, 0x7FE8FF),
    /** Above divine: armor formed from its wearer's own aura, left only by slain monarchs. No weapon is of this grade. */
    MONARCH(0xC77DFF, 0xF3E6FF);

    public static final com.mojang.serialization.Codec<ItemGrade> CODEC = StringRepresentable.fromEnum(ItemGrade::values);

    private final int color;
    private final int accent;

    ItemGrade(int color, int accent) {
        this.color = color;
        this.accent = accent;
    }

    public int color() {
        return color;
    }

    public int accent() {
        return accent;
    }

    /** 0 = common ... 5 = divine, 6 = monarch. */
    public int tier() {
        return ordinal();
    }

    /** Legendary and up shimmer, glint and get a light beam with sparks when dropped. */
    public boolean shimmers() {
        return this.ordinal() >= LEGENDARY.ordinal();
    }

    public boolean atLeast(ItemGrade other) {
        return ordinal() >= other.ordinal();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("grade.beyondsolo." + getSerializedName());
    }

    /** The stack's grade, or null for items without one (vanilla items etc.). */
    public static @Nullable ItemGrade of(ItemStack stack) {
        return stack.isEmpty() ? null : stack.get(ModComponents.GRADE.get());
    }

    public static @Nullable ItemGrade byName(String name) {
        for (ItemGrade g : values()) if (g.getSerializedName().equalsIgnoreCase(name)) return g;
        return null;
    }
}
