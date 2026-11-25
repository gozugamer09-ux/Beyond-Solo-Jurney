package com.beyondsolo.item.arsenal;

import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.system.PlayerClass;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/** Tooltip lines shared by every System weapon: grade, class, buildup, traits, signature, weight and a line of lore. */
public final class ArsenalTooltip {
    /** The local player's class, set by the client (null on a server or before evaluation). */
    public static Supplier<@Nullable PlayerClass> clientClass = () -> null;

    public static ItemGrade gradeOf(ItemStack stack, ItemGrade fallback) {
        ItemGrade g = ItemGrade.of(stack);
        return g != null ? g : fallback;
    }

    public static Component gradedName(Component name, ItemStack stack, ItemGrade fallback) {
        ItemGrade g = gradeOf(stack, fallback);
        return com.beyondsolo.gear.GearTooltip.name(name.copy().withStyle(s -> s.withColor(TextColor.fromRgb(g.color()))), stack);
    }

    public static void append(ItemStack stack, ArsenalWeapon w, String weightClass, List<Component> lines) {
        ItemGrade grade = gradeOf(stack, w.baseGrade());
        // "Rare Dagger" / "Daga de grado Raro": the language file orders grade and kind
        MutableComponent head = Component.literal("◆ ").append(w.kind() == null ? grade.displayName()
                : Component.translatable("tooltip.beyondsolo.grade_kind", grade.displayName(), w.kind().displayName()));
        lines.add(head.withStyle(s -> s.withColor(TextColor.fromRgb(grade.color()))));

        com.beyondsolo.gear.GearTooltip.extras(stack, lines);

        PlayerClass cls = w.favoredClass();
        if (cls != null) {
            if (w.boundToClass()) {
                PlayerClass mine = clientClass.get();
                boolean ok = mine == cls;
                lines.add(Component.translatable("tooltip.beyondsolo.bound", cls.displayName())
                        .withStyle(ok ? ChatFormatting.GOLD : ChatFormatting.RED));
                if (!ok) lines.add(Component.translatable("tooltip.beyondsolo.bound.refused").withStyle(ChatFormatting.DARK_RED));
            } else {
                lines.add(Component.translatable("tooltip.beyondsolo.favored", cls.displayName())
                        .withStyle(s -> s.withColor(TextColor.fromRgb(cls.color()))));
            }
        }

        if (w.buildup() > 0) {
            lines.add(Component.translatable("tooltip.beyondsolo.buildup", w.element().displayName(), Math.round(w.buildup()))
                    .withStyle(s -> s.withColor(TextColor.fromRgb(w.element().color()))));
        }
        for (Trait t : w.traits()) {
            lines.add(Component.literal(" ✦ ").append(t.describe()).withStyle(s -> s.withColor(TextColor.fromRgb(0xD8E4F0))));
        }
        Signature sig = w.signature();
        if (sig != null) {
            lines.add(Component.translatable("tooltip.beyondsolo.signature", sig.displayName())
                    .withStyle(s -> s.withColor(TextColor.fromRgb(sig.color())).withBold(true)));
            lines.add(Component.literal("   ").append(sig.description()).withStyle(ChatFormatting.GRAY));
            if (sig.active()) {
                lines.add(Component.literal("   ").append(Component.translatable("tooltip.beyondsolo.signature.use", sig.cooldownSeconds()))
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else if (sig.drawn()) {
                String hold = String.valueOf(sig.drawTicks() / 20f).replaceAll("\\.0$", "");
                lines.add(Component.literal("   ").append(Component.translatable("tooltip.beyondsolo.signature.draw", hold, sig.cooldownSeconds()))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        com.beyondsolo.armory.DivinePassives.tooltip(w, lines);
        lines.add(Component.translatable("tooltip.beyondsolo.weight." + weightClass).withStyle(ChatFormatting.GRAY));

        String lore = stack.getDescriptionId() + ".lore";
        if (Language.getInstance().has(lore)) {
            lines.add(Component.translatable(lore).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    private ArsenalTooltip() {}
}
