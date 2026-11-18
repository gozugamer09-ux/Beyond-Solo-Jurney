package com.beyondsolo.item;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.item.arsenal.ArsenalTooltip;
import com.beyondsolo.item.arsenal.ArsenalWeapon;
import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.item.arsenal.Trait;
import com.beyondsolo.item.arsenal.WeaponKind;
import com.beyondsolo.system.PlayerClass;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A System bow. Its arrows hit {@link #damageMultiplier} times harder, carry the bow's element buildup and traits
 * (see {@link com.beyondsolo.combat.ArsenalHits}), and the higher grades split full draws into a volley.
 */
public class SystemBowItem extends BowItem implements ArsenalWeapon {
    /** Ticks into a draw at which the string is heard: a tap on the button stays silent. */
    private static final int DRAW_SOUND_TICK = 3;

    private final ItemGrade grade;
    private final float damageMultiplier;
    private final DamageElement element;
    private final float buildup;
    private final List<Trait> traits;
    private final @Nullable PlayerClass favored;
    private final @Nullable Signature signature;

    public SystemBowItem(ItemGrade grade, float damageMultiplier, DamageElement element, float buildup, List<Trait> traits,
                         @Nullable PlayerClass favored, @Nullable Signature signature, Properties props) {
        super(props);
        this.grade = grade;
        this.damageMultiplier = damageMultiplier;
        this.element = element;
        this.buildup = buildup;
        this.traits = List.copyOf(traits);
        this.favored = favored;
        this.signature = signature;
    }

    public float damageMultiplier() {
        return damageMultiplier;
    }

    @Override
    public ItemGrade baseGrade() {
        return grade;
    }

    @Override
    public WeaponKind kind() {
        return WeaponKind.BOW;
    }

    @Override
    public List<Trait> traits() {
        return traits;
    }

    @Override
    public DamageElement element() {
        return element;
    }

    @Override
    public float buildup() {
        return buildup;
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
    public Component getName(ItemStack stack) {
        return ArsenalTooltip.gradedName(super.getName(stack), stack, grade);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return ArsenalTooltip.gradeOf(stack, grade).atLeast(ItemGrade.MYTHIC) || super.isFoil(stack);
    }

    @Override
    public int getEnchantmentValue() {
        return 10 + grade.tier() * 3;
    }

    /** The string creaks softly as the draw begins (the game's own bow is silent until it looses). */
    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        super.onUseTick(level, user, stack, remaining);
        if (!level.isClientSide && getUseDuration(stack, user) - remaining == DRAW_SOUND_TICK) {
            level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.CROSSBOW_LOADING_START.value(),
                    SoundSource.PLAYERS, 0.35f, 1.15f + level.random.nextFloat() * 0.15f);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.beyondsolo.bow_damage", Math.round(damageMultiplier * 100))
                .withStyle(net.minecraft.ChatFormatting.BLUE));
        ArsenalTooltip.append(stack, this, "light", lines);
    }
}
