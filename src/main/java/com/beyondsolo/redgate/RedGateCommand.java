package com.beyondsolo.redgate;

import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.system.PlayerRank;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Locale;

/**
 * /system redgate ... testing commands (permission level 2):
 * <pre>
 * redgate force             the nearest gate (within 96 blocks) turns red when an awakened hunter goes through first
 * redgate gate [rank]       a gate that will turn red opens a few steps in front of you
 * redgate now [rank]        a red gate opens in front of you and takes you straight in
 * redgate build             the whole patch behind your red gate is built now
 * redgate jump &lt;to&gt; [n]     moves your red gate on: night, dawn, day n, time n, raid, blizzard [s], calm, seal,
 *                           release, clear, chill n, or takes you to the breach, the keep, a camp, its master
 * redgate leave             out at once, nothing paid
 * redgate end               your red gate ends, everyone in it sent home
 * redgate status            one line about your red gate (or the nearest)
 * </pre>
 */
@EventBusSubscriber(modid = com.beyondsolo.BeyondSolo.MOD_ID)
public final class RedGateCommand {
    private static final SimpleCommandExceptionType NO_GATE = new SimpleCommandExceptionType(Component.literal("no gate within 96 blocks"));
    private static final SimpleCommandExceptionType NOT_IN = new SimpleCommandExceptionType(Component.literal("you are not in a red gate (or it can't do that now)"));
    private static final SimpleCommandExceptionType NO_WORLD = new SimpleCommandExceptionType(Component.literal("the red gate dimension is missing, or that gate is already red"));
    private static final String[] JUMPS = {"night", "dawn", "day", "time", "raid", "blizzard", "calm", "seal", "release", "build", "clear", "chill",
            "breach", "keep", "camp", "boss"};

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("redgate")
                        .then(Commands.literal("force").executes(ctx -> {
                            GateInstance g = nearest(ctx);
                            RedGateData.get(ctx.getSource().getServer()).force(g.id(), true);
                            ctx.getSource().sendSuccess(() -> Component.literal(g.rank() + "-rank gate at " + g.gatePos().toShortString()
                                    + " turns red when an awakened hunter is the first through it"), false);
                            return 1;
                        }))
                        .then(Commands.literal("gate")
                                .executes(ctx -> gate(ctx, null, false))
                                .then(rank().executes(ctx -> gate(ctx, StringArgumentType.getString(ctx, "rank"), false))))
                        .then(Commands.literal("now")
                                .executes(ctx -> gate(ctx, null, true))
                                .then(rank().executes(ctx -> gate(ctx, StringArgumentType.getString(ctx, "rank"), true))))
                        .then(Commands.literal("build").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            RedGate run = RedGates.runOf(p.server, p);
                            if (run == null) throw NOT_IN.create();
                            RedGates.buildNow(p.server, run);
                            return 1;
                        }))
                        .then(Commands.literal("jump").then(Commands.argument("to", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(JUMPS, b))
                                .executes(ctx -> jump(ctx, 0))
                                .then(Commands.argument("n", IntegerArgumentType.integer(0, 24000))
                                        .executes(ctx -> jump(ctx, IntegerArgumentType.getInteger(ctx, "n"))))))
                        .then(Commands.literal("leave").executes(ctx -> {
                            if (!RedGates.leaveNow(ctx.getSource().getPlayerOrException())) throw NOT_IN.create();
                            return 1;
                        }))
                        .then(Commands.literal("end").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            RedGate run = RedGates.runOf(p.server, p);
                            if (run == null || run.state == RedGate.State.ENDED) throw NOT_IN.create();
                            RedGates.abort(p.server, run);
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            RedGate run = RedGates.runOf(p.server, p);
                            if (run == null) {
                                GateBlockEntity gate = GateService.nearestGate(p, 96);
                                if (gate != null && gate.gateId() != null) run = RedGateData.get(p.server).get(gate.gateId());
                                if (run == null && gate != null && gate.gateId() != null) {
                                    GateInstance g = GateData.get(p.server).get(gate.gateId());
                                    boolean latent = g != null && RedGates.latent(p.server, g);
                                    ctx.getSource().sendSuccess(() -> Component.literal("nearest gate: " + (latent ? "a red gate in waiting" : "an ordinary gate")
                                            + "; red gate chance " + RedGateConfig.chance()), false);
                                    return 1;
                                }
                            }
                            if (run == null) throw NOT_IN.create();
                            RedGate r = run;
                            ctx.getSource().sendSuccess(() -> Component.literal(RedGates.status(p.server, r)), false);
                            return 1;
                        }))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> rank() {
        return Commands.argument("rank", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(PlayerRank.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), b));
    }

    /** A gate opens in front of the player, one that turns red; with {@code now}, it turns red and takes them in at once. */
    private static int gate(CommandContext<CommandSourceStack> ctx, String rankName, boolean now) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerRank rank = rankName == null ? null : PlayerRank.byName(rankName);
        if (rank == null) rank = PlayerRank.C;
        Direction look = p.getDirection();
        BlockPos at = p.serverLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.blockPosition().relative(look, 7));
        GateBlockEntity gate = GateService.spawnGate(p.serverLevel(), at, rank, look.getOpposite(), !now);
        if (gate == null || gate.gateId() == null) throw NO_GATE.create();
        GateInstance g = GateData.get(p.server).get(gate.gateId());
        if (g == null) throw NO_GATE.create();
        RedGateData.get(p.server).force(g.id(), true);
        if (now && RedGates.startNow(p, gate, g) == null) throw NO_WORLD.create();
        return 1;
    }

    private static int jump(CommandContext<CommandSourceStack> ctx, int n) throws CommandSyntaxException {
        if (!RedGates.jump(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "to"), n)) throw NOT_IN.create();
        return 1;
    }

    private static GateInstance nearest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        GateBlockEntity gate = GateService.nearestGate(p, 96);
        GateInstance g = gate == null || gate.gateId() == null ? null : GateData.get(p.server).get(gate.gateId());
        if (g == null) throw NO_GATE.create();
        return g;
    }

    private RedGateCommand() {}
}
