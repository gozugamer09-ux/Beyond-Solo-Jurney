package com.beyondsolo.command.solo;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.armory.ArmoryModule;
import com.beyondsolo.economy.EconomyModule;
import com.beyondsolo.economy.HunterLicenseItem;
import com.beyondsolo.gear.GearKind;
import com.beyondsolo.gear.GearRolls;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.key.DungeonKeys;
import com.beyondsolo.rune.Runes;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillTomeItem;
import com.beyondsolo.skill.Skills;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /solo give: the mod's items, rolled the way a gate rolls them (every piece of gear gets its own grade and bonus
 * stats). What doesn't fit in the inventory drops at the player's feet.
 */
final class SoloGive {
    /** At most this many of one thing per command. */
    static final int MAX = 256;

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("give")
                .then(Commands.literal("item").then(item()))
                .then(Commands.literal("armor").then(armor()))
                .then(Commands.literal("key").then(counted(Solo.rankArg(), (ctx, p, n) -> copies(DungeonKeys.key(Solo.rank(ctx)), n, null, p.getRandom()))))
                .then(Commands.literal("rune")
                        .then(Commands.literal("sealed").then(counted(Solo.rankArg(), (ctx, p, n) -> copies(Runes.sealed(Solo.rank(ctx)), n, null, p.getRandom()))))
                        .then(counted(skillArg(), (ctx, p, n) -> copies(Runes.of(SoloSkills.skill(ctx)), n, null, p.getRandom()))))
                .then(Commands.literal("tome").then(counted(skillArg(), (ctx, p, n) -> copies(SkillTomeItem.of(SoloSkills.skill(ctx)), n, null, p.getRandom()))))
                .then(Solo.targets(Commands.literal("license"), (ctx, p) -> {
                    ItemStack card = new ItemStack(EconomyModule.HUNTER_LICENSE.get());
                    HunterLicenseItem.refresh(p, card);
                    return given(p, List.of(card));
                })));
    }

    /** Makes what a command gives one player. */
    @FunctionalInterface
    private interface Gift {
        List<ItemStack> make(CommandContext<CommandSourceStack> ctx, ServerPlayer p, int count) throws CommandSyntaxException;
    }

    /** {@code <node> [count] [players]}: the count goes in before the players, so a number is never read as a name. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T counted(T node, Gift gift) {
        node.then(Solo.targets(Commands.argument("count", IntegerArgumentType.integer(1, MAX)),
                (ctx, p) -> given(p, gift.make(ctx, p, IntegerArgumentType.getInteger(ctx, "count")))));
        return Solo.targets(node, (ctx, p) -> given(p, gift.make(ctx, p, 1)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> skillArg() {
        return Solo.word("skill", () -> Skills.all().stream().map(Skill::path));
    }

    // ---------------------------------------------------------------- any item

    /** {@code <item> [count] [grade] [players]}; the grades are words of their own, so they never take a player's place. */
    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> item() {
        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> item = Commands.argument("item", ResourceLocationArgument.id())
                .suggests((c, b) -> Solo.suggestIds(BuiltInRegistries.ITEM.keySet().stream().filter(id -> id.getNamespace().equals(BeyondSolo.MOD_ID)), b));
        RequiredArgumentBuilder<CommandSourceStack, Integer> count = Commands.argument("count", IntegerArgumentType.integer(1, MAX));
        for (ItemGrade g : ItemGrade.values()) {
            item.then(Solo.targets(Commands.literal(g.getSerializedName()), (ctx, p) -> given(p, items(ctx, p, 1, g))));
            count.then(Solo.targets(Commands.literal(g.getSerializedName()), (ctx, p) -> given(p, items(ctx, p, IntegerArgumentType.getInteger(ctx, "count"), g))));
        }
        item.then(Solo.targets(count, (ctx, p) -> given(p, items(ctx, p, IntegerArgumentType.getInteger(ctx, "count"), null))));
        return Solo.targets(item, (ctx, p) -> given(p, items(ctx, p, 1, null)));
    }

    private static List<ItemStack> items(CommandContext<CommandSourceStack> ctx, ServerPlayer p, int count, @Nullable ItemGrade grade)
            throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "item");
        Item item = Solo.lookup(BuiltInRegistries.ITEM, id).filter(i -> i != Items.AIR).orElseThrow(() -> Solo.error("unknown_item", id.toString()));
        ItemStack one = new ItemStack(item);
        if (grade != null && GearKind.of(one) == null) throw Solo.error("no_grade", one.getHoverName());
        return copies(one, count, grade, p.getRandom());
    }

    /**
     * {@code n} of a thing in as few stacks as it takes. Every piece of gear is rolled on its own, at the grade asked
     * for (never below the item's own) or at its own.
     */
    static List<ItemStack> copies(ItemStack one, int n, @Nullable ItemGrade grade, RandomSource r) {
        List<ItemStack> out = new ArrayList<>();
        if (GearKind.of(one) != null) {
            for (int i = 0; i < n; i++) {
                ItemStack piece = one.copyWithCount(1);
                if (grade != null) GearRolls.roll(piece, grade, r);
                else GearRolls.roll(piece, r);
                out.add(piece);
            }
            return out;
        }
        int max = Math.max(1, one.getMaxStackSize());
        for (int left = n; left > 0; left -= max) out.add(one.copyWithCount(Math.min(max, left)));
        return out;
    }

    // ---------------------------------------------------------------- armor sets

    /** {@code <set> [grade] [players]}: all four pieces of a set. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> armor() {
        RequiredArgumentBuilder<CommandSourceStack, String> set = Solo.word("set", () -> ArmorSets.ALL.stream()
                .filter(s -> ArmoryModule.piecesOf(s) != null).map(s -> s.name));
        for (ItemGrade g : ItemGrade.values()) set.then(Solo.targets(Commands.literal(g.getSerializedName()), (ctx, p) -> armor(ctx, p, g)));
        return Solo.targets(set, (ctx, p) -> armor(ctx, p, null));
    }

    private static Component armor(CommandContext<CommandSourceStack> ctx, ServerPlayer p, @Nullable ItemGrade grade) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "set");
        ArmorSets.Set set = ArmorSets.byName(name);
        var pieces = set == null ? null : ArmoryModule.piecesOf(set);
        if (pieces == null) throw Solo.error("unknown_set", name);
        List<ItemStack> out = new ArrayList<>();
        for (var piece : pieces) out.addAll(copies(new ItemStack(piece.get()), 1, grade, p.getRandom()));
        hand(p, out);
        return Solo.t("give.set", p.getDisplayName(), Component.translatable("set.beyondsolo." + set.name));
    }

    // ---------------------------------------------------------------- handing over

    /** Hands the stacks over and says what the player got ("3 × Rune Stone"). */
    private static Component given(ServerPlayer p, List<ItemStack> stacks) {
        if (stacks.isEmpty()) return Solo.t("give.done", p.getDisplayName(), 0, Component.empty());
        Component what = stacks.get(0).getDisplayName();
        int total = stacks.stream().mapToInt(ItemStack::getCount).sum();
        hand(p, stacks);
        return Solo.t("give.done", p.getDisplayName(), total, what);
    }

    /** Into the inventory; what doesn't fit drops at the player's feet, theirs to pick up at once. */
    static void hand(ServerPlayer p, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            p.getInventory().add(stack);
            if (stack.isEmpty()) continue;
            ItemEntity drop = p.drop(stack, false);
            if (drop != null) {
                drop.setNoPickUpDelay();
                drop.setTarget(p.getUUID());
            }
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f,
                ((p.getRandom().nextFloat() - p.getRandom().nextFloat()) * 0.7f + 1f) * 2f);
        p.containerMenu.broadcastChanges();
    }

    private SoloGive() {}
}
