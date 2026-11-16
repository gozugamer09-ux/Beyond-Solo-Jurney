package com.beyondsolo.gear;

import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
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

    /**
     * The set name with the pieces worn, the class it is made for and the level it needs, its two- and four-piece
     * bonuses and perk (lit when active) and where it drops.
     */
    public static void set(ArmorSets.Set set, List<Component> lines) {
        LivingEntity wearer = clientWearer.get();
        int worn = wearer == null ? 0 : ArmorSets.pieces(wearer, set);
        boolean attuned = wearer == null || ArmorSets.attuned(wearer, set);
        Component setName = Component.translatable("set.beyondsolo." + set.name);
        String own = "tooltip.beyondsolo.set." + set.name;
        MutableComponent head = has(own) ? Component.translatable(own, setName) : Component.translatable("tooltip.beyondsolo.set_of", setName);
        lines.add(head.append(Component.literal(" (" + worn + "/4)")).withStyle(ChatFormatting.GOLD));

        // who it is made for, and the level it needs
        if (set.playerClass != null || set.requiredLevel > 1) {
            MutableComponent req = Component.literal(" ");
            if (set.playerClass != null) {
                int color = attuned ? set.playerClass.color() : 0xE05A5A;
                req.append(Component.translatable("tooltip.beyondsolo.set_class", set.playerClass.displayName()).withStyle(s -> s.withColor(TextColor.fromRgb(color))));
            }
            if (set.requiredLevel > 1) {
                boolean ready = !(wearer instanceof Player p) || p.isCreative() || SystemAPI.get(p).level() >= set.requiredLevel;
                if (set.playerClass != null) req.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY));
                req.append(Component.translatable("tooltip.beyondsolo.set_level", set.requiredLevel).withStyle(ready ? ChatFormatting.GRAY : ChatFormatting.RED));
            }
            lines.add(req);
            if (!attuned && set.playerClass != null) {
                lines.add(Component.literal(" ").append(Component.translatable(set.divine() ? "tooltip.beyondsolo.set_bound" : "tooltip.beyondsolo.set_unattuned",
                        set.playerClass.displayName())).withStyle(ChatFormatting.DARK_RED));
            }
        }

        boolean twoOn = worn >= 2 && attuned;
        boolean fourOn = worn == 4 && attuned;
        String two = "set.beyondsolo." + set.name + ".two";
        String bonus = "set.beyondsolo." + set.name + ".bonus";
        lines.add((has(two) ? Component.translatable(two) : Component.translatable("tooltip.beyondsolo.set_two", stats(set.two())))
                .withStyle(twoOn ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        if (has(bonus)) {
            lines.add(Component.translatable(bonus).withStyle(fourOn ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        } else {
            lines.add(Component.translatable("tooltip.beyondsolo.set_four", stats(set.bonus())).withStyle(fourOn ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
            if (set.perk != null) {
                int perkColor = set.divine() ? ItemGrade.DIVINE.color() : set.playerClass != null ? set.playerClass.color() : 0xFFE9A0;
                lines.add(Component.literal(" ✦ ").append(Component.translatable("perk.beyondsolo." + set.perk).withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(": ")).append(Component.translatable("perk.beyondsolo." + set.perk + ".desc"))
                        .withStyle(s -> fourOn ? s.withColor(TextColor.fromRgb(perkColor)) : s.withColor(ChatFormatting.DARK_GRAY)));
            }
        }

        // where it comes from
        PlayerRank boss = BossSets.bossRank(set);
        if (boss != null) {
            lines.add(Component.translatable("gear.beyondsolo.signature_of",
                            com.beyondsolo.gate.GateTheme.values()[boss.tier()].bossName(), boss.displayName())
                    .withStyle(s -> s.withColor(TextColor.fromRgb(ItemGrade.MYTHIC.accent())).withItalic(true)));
        } else if (set.source != null) {
            Component where = source(set);
            if (where != null) lines.add(where.copy().withStyle(s -> s.withColor(TextColor.fromRgb(0xB8A6D8)).withItalic(true)));
        }
    }

    /** "Signature loot of the Oathless Paladin (A-rank gate)", "Rare drop from Gloomfangs in D-rank gates and up"... */
    public static @Nullable Component source(ArmorSets.Set set) {
        String src = set.source;
        if (src == null) return null;
        PlayerRank rank = set.tier >= 0 && set.tier < PlayerRank.values().length ? PlayerRank.values()[set.tier] : PlayerRank.S;
        if (src.startsWith("boss:")) {
            com.beyondsolo.gate.BossKind kind = com.beyondsolo.gate.BossKind.byId(src.substring(5));
            if (kind == null) return null;
            return Component.translatable("gear.beyondsolo.signature_of", kind.name(rank), rank.displayName());
        }
        if (src.startsWith("creature:")) {
            return Component.translatable("tooltip.beyondsolo.set_source.creature",
                    Component.translatable("entity.beyondsolo." + src.substring(9)), rank.displayName());
        }
        if (src.startsWith("villager:")) return Component.translatable("tooltip.beyondsolo.set_source.villager." + src.substring(9));
        if (src.equals("divine")) return Component.translatable("tooltip.beyondsolo.set_source.divine");
        return null;
    }

    /** Attributes a set bonus does not show as a percentage. */
    private static final java.util.Set<String> FLAT = java.util.Set.of("generic.armor", "generic.armor_toughness", "generic.attack_damage", "armor_pierce");

    /** "+3% Movement speed, +1 Armor", sorted so it reads the same every time. */
    public static MutableComponent stats(Map<Holder<Attribute>, Double> map) {
        MutableComponent out = Component.empty();
        List<Map.Entry<Holder<Attribute>, Double>> entries = new java.util.ArrayList<>(map.entrySet());
        entries.sort(java.util.Comparator.comparing(e -> path(e.getKey())));
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) out.append(", ");
            String path = path(entries.get(i).getKey());
            float v = entries.get(i).getValue().floatValue();
            String num = FLAT.contains(path) ? BonusStat.fmt(v) : BonusStat.fmt(v * 100) + "%";
            out.append(Component.translatable("gear.beyondsolo.stat_line", "+" + num, Component.translatable("armory.beyondsolo.stat." + path)));
        }
        return out;
    }

    private static String path(Holder<Attribute> attribute) {
        return attribute.unwrapKey().orElseThrow().location().getPath();
    }

    private static boolean has(String key) {
        return Language.getInstance().has(key);
    }

    private GearTooltip() {}
}
