package com.beyondsolo.gear;

import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/** Tooltip lines for grades' extras: enhancement, bonus stats, infusion, and set bonuses with the pieces worn. */
public final class GearTooltip {
    /** The local player (set by the client), to count the set pieces they wear. */
    public static Supplier<@Nullable LivingEntity> clientWearer = () -> null;

    public static final int ENHANCE_COLOR = 0xFFE9A0;
    public static final int STAT_COLOR = 0x9EE6FF;

    /** "+7 " in front of a refined item's name. */
    public static Component name(Component name, ItemStack stack) {
        int level = Enhancement.level(stack);
        if (level <= 0) return name;
        return Component.literal("+" + level + " ").withStyle(s -> s.withColor(TextColor.fromRgb(ENHANCE_COLOR)).withBold(true)).append(name);
    }

    /** Enhancement, bonus stats and infusion. */
    public static void extras(ItemStack stack, List<Component> lines) {
        int level = Enhancement.level(stack);
        GearKind kind = GearKind.of(stack);
        if (level > 0 && kind != null) {
            MutableComponent l = Component.translatable("gear.beyondsolo.refined", level, Enhancement.MAX);
            Component what = kind == GearKind.ARMOR
                    ? Component.translatable("gear.beyondsolo.refined.armor", BonusStat.fmt(level * Enhancement.armorPerLevel()))
                    : Component.translatable("gear.beyondsolo.refined.damage", Math.round(Enhancement.damageBonus(level) * 100));
            lines.add(l.append("  ").append(what).withStyle(s -> s.withColor(TextColor.fromRgb(ENHANCE_COLOR))));
        }
        BonusStats stats = stack.get(GearModule.BONUS_STATS.get());
        if (stats != null && !stats.isEmpty()) {
            for (BonusStats.Roll r : stats.rolls()) {
                lines.add(Component.literal(" ✧ ").append(r.stat().describe(r.value())).withStyle(s -> s.withColor(TextColor.fromRgb(STAT_COLOR))));
            }
        }
        SkillElement infusion = Infusions.of(stack);
        if (infusion != null) {
            lines.add(Component.translatable("gear.beyondsolo.infused", infusion.displayName())
                    .withStyle(s -> s.withColor(TextColor.fromRgb(infusion.color())).withBold(true)));
        }
    }

    /** The set name with the pieces worn, its two- and four-piece bonuses (lit when active) and where it drops. */
    public static void set(ArmorSets.Set set, List<Component> lines) {
        LivingEntity wearer = clientWearer.get();
        int worn = wearer == null ? 0 : ArmorSets.pieces(wearer, set);
        lines.add(Component.translatable("tooltip.beyondsolo.set." + set.name, Component.translatable("set.beyondsolo." + set.name))
                .append(Component.literal(" (" + worn + "/4)")).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("set.beyondsolo." + set.name + ".two").withStyle(worn >= 2 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable("set.beyondsolo." + set.name + ".bonus").withStyle(worn >= 4 ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        PlayerRank boss = BossSets.bossRank(set);
        if (boss != null) {
            lines.add(Component.translatable("gear.beyondsolo.signature_of",
                            com.beyondsolo.gate.GateTheme.values()[boss.tier()].bossName(), boss.displayName())
                    .withStyle(s -> s.withColor(TextColor.fromRgb(ItemGrade.MYTHIC.accent())).withItalic(true)));
        }
    }

    private GearTooltip() {}
}
