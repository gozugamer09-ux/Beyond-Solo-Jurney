package com.beyondsolo.gear;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.PlayerRank;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * /system gear ... testing commands (permission level 2), on the held item unless said otherwise:
 * <pre>
 * gear info                      what the held item rolled
 * gear roll &lt;grade&gt;              new grade and stats
 * gear enhance &lt;0-10&gt;            set the level
 * gear refine [ward]             one real try (costs like the forge)
 * gear infuse &lt;element|none&gt;
 * gear bossloot &lt;rank&gt; [n]       a boss's signature loot into the inventory
 * gear set &lt;name&gt;               wear a whole set (rolled)
 * gear materials                 forge materials
 * </pre>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class GearCommand {
    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("gear")
                        .then(Commands.literal("info").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ItemStack s = p.getMainHandItem();
                            ctx.getSource().sendSuccess(() -> Component.literal(s.getHoverName().getString() + " grade " + ItemGrade.of(s)
                                    + " +" + Enhancement.level(s) + " stats " + GearRolls.stats(s).rolls() + " infusion " + Infusions.of(s)), false);
                            return 1;
                        }))
                        .then(Commands.literal("roll").then(Commands.argument("grade", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(ItemGrade.values()).map(ItemGrade::getSerializedName), b))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    ItemGrade g = ItemGrade.byName(StringArgumentType.getString(ctx, "grade"));
                                    if (g == null) return 0;
                                    GearRolls.roll(p.getMainHandItem(), g, p.getRandom());
                                    return 1;
                                })))
                        .then(Commands.literal("enhance").then(Commands.argument("level", IntegerArgumentType.integer(0, Enhancement.MAX)).executes(ctx -> {
                            Enhancement.set(ctx.getSource().getPlayerOrException().getMainHandItem(), IntegerArgumentType.getInteger(ctx, "level"));
                            return 1;
                        })))
                        .then(Commands.literal("refine")
                                .executes(ctx -> refine(ctx.getSource().getPlayerOrException(), false))
                                .then(Commands.literal("ward").executes(ctx -> refine(ctx.getSource().getPlayerOrException(), true))))
                        .then(Commands.literal("infuse").then(Commands.argument("element", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(SkillElement.values()).map(e -> e.name().toLowerCase(Locale.ROOT)), b))
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "element").toUpperCase(Locale.ROOT);
                                    ItemStack s = ctx.getSource().getPlayerOrException().getMainHandItem();
                                    if (name.equals("NONE")) Infusions.set(s, null);
                                    else Infusions.set(s, SkillElement.valueOf(name));
                                    return 1;
                                })))
                        .then(Commands.literal("bossloot").then(Commands.argument("rank", StringArgumentType.word())
                                .executes(ctx -> bossLoot(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "rank"), 1))
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 20))
                                        .executes(ctx -> bossLoot(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "rank"),
                                                IntegerArgumentType.getInteger(ctx, "n"))))))
                        .then(Commands.literal("set").then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ArmorSets.ALL.stream().map(s -> s.name), b))
                                .executes(ctx -> wearSet(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("materials").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            p.getInventory().add(new ItemStack(GearModule.REFINING_STONE.get(), 32));
                            p.getInventory().add(new ItemStack(GearModule.WARD_SEAL.get(), 4));
                            GearModule.CRYSTALS.values().forEach(c -> p.getInventory().add(new ItemStack(c.get(), 2)));
                            return 1;
                        }))));
    }

    private static int refine(ServerPlayer p, boolean ward) {
        Enhancement.Outcome out = ForgeService.refine(p, p.getInventory().selected, ward);
        p.sendSystemMessage(Component.literal("Refine: " + out + " -> +" + Enhancement.level(p.getMainHandItem())));
        return 1;
    }

    private static int bossLoot(ServerPlayer p, String rankName, int n) {
        PlayerRank rank = PlayerRank.byName(rankName);
        if (rank == null) return 0;
        for (int i = 0; i < n; i++) {
            List<ItemStack> loot = new java.util.ArrayList<>(com.beyondsolo.armory.ArmorDrops.bossPieces(
                    com.beyondsolo.gate.BossKind.BRUTE, rank, true, p.getRandom()));
            loot.addAll(BossLoot.roll(rank, p.getRandom()));
            for (ItemStack s : loot) if (!p.getInventory().add(s)) p.drop(s, false);
        }
        return 1;
    }

    private static int wearSet(ServerPlayer p, String name) {
        ArmorSets.Set set = ArmorSets.byName(name);
        var pieces = set == null ? null : com.beyondsolo.armory.ArmoryModule.piecesOf(set);
        if (pieces == null) return 0;
        for (var item : pieces) equip(p, new ItemStack(item.get()));
        return 1;
    }

    private static void equip(ServerPlayer p, ItemStack stack) {
        GearRolls.roll(stack, p.getRandom());
        EquipmentSlot slot = ((SystemArmorItem) stack.getItem()).getEquipmentSlot();
        p.setItemSlot(slot, stack);
    }

    private GearCommand() {}
}
