package com.beyondsolo.item;

import com.beyondsolo.combat.DamageElement;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A System weapon. Better Combat gives it its moveset through {@code data/beyondsolo/weapon_attributes/<id>.json}
 * (dagger, sword, claymore, spear, scythe, katana...). Each hit adds element buildup to the target, and each swing
 * costs exhaustion scaled by the weapon's weight.
 */
public class SystemWeaponItem extends SwordItem {
    private final DamageElement element;
    private final float buildup;
    private final float weight;

    public SystemWeaponItem(Tier tier, int damage, float speed, DamageElement element, float buildup, float weight, Properties props) {
        super(tier, props.attributes(SwordItem.createAttributes(tier, damage, speed)));
        this.element = element;
        this.buildup = buildup;
        this.weight = weight;
    }

    public DamageElement element() {
        return element;
    }

    /** Buildup added per hit, before the attacker's elemental power and the target's resistance. */
    public float buildup() {
        return buildup;
    }

    /** 1 = a sword. Swing exhaustion is multiplied by this. */
    public float weight() {
        return weight;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (buildup > 0) {
            lines.add(Component.translatable("tooltip.beyondsolo.buildup", element.displayName(), Math.round(buildup))
                    .withStyle(s -> s.withColor(TextColor.fromRgb(element.color()))));
        }
        lines.add(Component.translatable("tooltip.beyondsolo.weight." + weightClass()).withStyle(ChatFormatting.GRAY));
    }

    private String weightClass() {
        return weight < 0.8f ? "light" : weight > 1.4f ? "heavy" : "medium";
    }
}
