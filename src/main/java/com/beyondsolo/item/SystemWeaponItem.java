package com.beyondsolo.item;

import com.beyondsolo.combat.ArsenalAbilities;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.item.arsenal.ArsenalTooltip;
import com.beyondsolo.item.arsenal.ArsenalWeapon;
import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.item.arsenal.Trait;
import com.beyondsolo.item.arsenal.WeaponKind;
import com.beyondsolo.system.PlayerClass;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A System weapon. Better Combat gives it its moveset through {@code data/beyondsolo/weapon_attributes/<id>.json}
 * (dagger, sword, claymore, spear, scythe, katana...). Each hit adds element buildup to the target, and each swing
 * costs exhaustion scaled by the weapon's weight. Arsenal weapons add a grade, passive traits, a favoured (or, for
 * divine weapons, bound) class and a right-click signature art.
 */
public class SystemWeaponItem extends SwordItem implements ArsenalWeapon {
    private final DamageElement element;
    private final float buildup;
    private final float weight;
    private final ItemGrade grade;
    private final WeaponKind kind;
    private final List<Trait> traits;
    private final @Nullable PlayerClass favored;
    private final @Nullable Signature signature;

    /** The original combat weapons. */
    public SystemWeaponItem(Tier tier, int damage, float speed, DamageElement element, float buildup, float weight, Properties props) {
        this(tier, SwordItem.createAttributes(tier, damage, speed), element, buildup, weight, ItemGrade.COMMON, null, List.of(), null, null, props);
    }

    public SystemWeaponItem(Tier tier, ItemAttributeModifiers attributes, DamageElement element, float buildup, float weight,
                            ItemGrade grade, @Nullable WeaponKind kind, List<Trait> traits, @Nullable PlayerClass favored,
                            @Nullable Signature signature, Properties props) {
        super(tier, props.attributes(attributes));
        this.element = element;
        this.buildup = buildup;
        this.weight = weight;
        this.grade = grade;
        this.kind = kind;
        this.traits = List.copyOf(traits);
        this.favored = favored;
        this.signature = signature;
    }

    @Override
    public DamageElement element() {
        return element;
    }

    /** Buildup added per hit, before the attacker's elemental power and the target's resistance. */
    @Override
    public float buildup() {
        return buildup;
    }

    /** 1 = a sword. Swing exhaustion is multiplied by this. */
    public float weight() {
        return weight;
    }

    @Override
    public ItemGrade baseGrade() {
        return grade;
    }

    @Override
    public WeaponKind kind() {
        return kind;
    }

    @Override
    public List<Trait> traits() {
        return traits;
    }

    @Override
    public @Nullable PlayerClass favoredClass() {
        return favored;
    }

    @Override
    public @Nullable Signature signature() {
        return signature;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (signature != null && signature.active() && hand == InteractionHand.MAIN_HAND) {
            if (!level.isClientSide) ArsenalAbilities.unleash(player, stack, this);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public Component getName(ItemStack stack) {
        return ArsenalTooltip.gradedName(super.getName(stack), stack, grade);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        ItemGrade g = ItemGrade.of(stack);
        return (g != null ? g : grade).atLeast(ItemGrade.MYTHIC) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        ArsenalTooltip.append(stack, this, weightClass(), lines);
    }

    private String weightClass() {
        return weight < 0.8f ? "light" : weight > 1.4f ? "heavy" : "medium";
    }
}
