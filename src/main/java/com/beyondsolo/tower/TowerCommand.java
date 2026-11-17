package com.beyondsolo.tower;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/**
 * /system tower ... testing commands (permission level 2):
 * <ul>
 *   <li>bind (the key, without the checks), climb (as the CITADEL page does, with them), enter &lt;floor&gt;, lord,
 *   endless, rush &lt;tier&gt;</li>
 *   <li>jump &lt;arrive|clear|climb|wake|kill|enrage|moon|fall|breather|leave|siege&gt;, leave, cancel</li>
 *   <li>checkpoint &lt;floor&gt;, sigil (the item), sigil dry &lt;kills&gt; (stages the pity), info, reset</li>
 * </ul>
 */
@EventBusSubscriber(modid = com.beyondsolo.BeyondSolo.MOD_ID)
public final class TowerCommand {
    private static final SimpleCommandExceptionType NOT_RUNNING = new SimpleCommandExceptionType(Component.literal("Not in the Citadel (or not in that phase)"));
    private static final SimpleCommandExceptionType CANT = new SimpleCommandExceptionType(Component.literal("Can't start a run now (already in one, or no Citadel)"));
    private static final SimpleCommandExceptionType NO_TIER = new SimpleCommandExceptionType(Component.literal("No such rush tier"));

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        String[] tiers = Arrays.stream(RushTier.values()).map(RushTier::id).toArray(String[]::new);
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("tower")
                        .then(Commands.literal("bind").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            TowerAPI.data(p).bound = true;
                            TowerAPI.data(p).told = true;
                            TowerAPI.sync(p);
                            return 1;
                        }))
                        .then(Commands.literal("climb").executes(ctx -> TowerRun.enter(ctx.getSource().getPlayerOrException(), TowerRun.Mode.CASTLE, null) ? 1 : 0))
                        .then(Commands.literal("enter").then(Commands.argument("floor", IntegerArgumentType.integer(1, 9999))
                                .executes(ctx -> start(ctx.getSource().getPlayerOrException(), TowerRun.Mode.CASTLE, IntegerArgumentType.getInteger(ctx, "floor"), null))))
                        .then(Commands.literal("lord").executes(ctx -> start(ctx.getSource().getPlayerOrException(), TowerRun.Mode.CASTLE, TowerFloors.TOP, null)))
                        .then(Commands.literal("endless").executes(ctx -> start(ctx.getSource().getPlayerOrException(), TowerRun.Mode.ENDLESS, TowerFloors.SPIRE, null)))
                        .then(Commands.literal("rush").then(Commands.argument("tier", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(tiers, b))
                                .executes(ctx -> {
                                    RushTier tier = RushTier.byId(StringArgumentType.getString(ctx, "tier"));
                                    if (tier == null) throw NO_TIER.create();
                                    return start(ctx.getSource().getPlayerOrException(), TowerRun.Mode.RUSH, 0, tier);
                                })))
                        .then(Commands.literal("jump").then(Commands.argument("to", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"arrive", "clear", "climb", "wake", "kill", "enrage", "moon",
                                        "fall", "breather", "leave", "siege"}, b))
                                .executes(ctx -> {
                                    if (!TowerRun.jump(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "to"))) throw NOT_RUNNING.create();
                                    return 1;
                                })))
                        .then(Commands.literal("leave").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            if (!TowerRun.inRun(p)) throw NOT_RUNNING.create();
                            TowerRun.action(p, com.beyondsolo.network.TowerActionPayload.LEAVE, 0);
                            return 1;
                        }))
                        .then(Commands.literal("cancel").executes(ctx -> {
                            TowerRun.cancel(ctx.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("checkpoint").then(Commands.argument("floor", IntegerArgumentType.integer(0, TowerFloors.TOP))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    int f = TowerFloors.checkpointOf(IntegerArgumentType.getInteger(ctx, "floor"));
                                    TowerData data = TowerAPI.data(p);
                                    data.checkpoint = f;
                                    data.highest = Math.max(data.highest, f);
                                    TowerAPI.sync(p);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Checkpoint: floor " + f), false);
                                    return 1;
                                })))
                        .then(Commands.literal("sigil").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ItemStack sigil = new ItemStack(TowerModule.SIGIL_OF_DOMINION.get());
                            if (!p.getInventory().add(sigil)) p.drop(sigil, false);
                            return 1;
                        }).then(Commands.literal("dry").then(Commands.argument("kills", IntegerArgumentType.integer(0, TowerRun.SIGIL_PITY - 1))
                                .executes(ctx -> {
                                    // stages the pity: the Sigil not yet earned, and this many lord kills already gone by without it
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    TowerData data = TowerAPI.data(p);
                                    data.sigil = false;
                                    data.sigilDry = IntegerArgumentType.getInteger(ctx, "kills");
                                    TowerAPI.sync(p);
                                    return 1;
                                }))))
                        .then(Commands.literal("info").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            TowerData data = TowerAPI.data(p);
                            StringBuilder rush = new StringBuilder();
                            for (RushTier t : RushTier.values()) {
                                rush.append(' ').append(t.id()).append('=').append(data.rushClears[t.ordinal()])
                                        .append(data.rushBest[t.ordinal()] >= 0 ? "/" + TowerRun.time(data.rushBest[t.ordinal()]) : "");
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Citadel: " + TowerRun.describe(p)
                                    + "\nbound " + data.bound + ", checkpoint " + data.checkpoint + ", highest " + data.highest + ", lord kills " + data.lordKills
                                    + ", sigil " + data.sigil + " (dry " + data.sigilDry + "), spire best " + data.spireBest + " (" + data.spireRuns + " runs)"
                                    + ", floors " + data.floorsCleared + ", falls " + data.falls + "\nrush:" + rush), false);
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            if (TowerRun.inRun(p)) TowerRun.cancel(p);
                            TowerAPI.data(p).reset();
                            TowerAPI.sync(p);
                            return 1;
                        }))));
    }

    private static int start(ServerPlayer p, TowerRun.Mode mode, int floor, RushTier tier) throws CommandSyntaxException {
        if (!TowerAPI.data(p).bound) {
            TowerAPI.data(p).bound = true;
            TowerAPI.data(p).told = true;
            TowerAPI.sync(p);
        }
        if (!TowerRun.begin(p, mode, floor, tier)) throw CANT.create();
        return 1;
    }

    private TowerCommand() {}
}
