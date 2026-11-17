package com.beyondsolo.breaks;

import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateBossEntity;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Locale;

/**
 * /system break ... testing commands (permission level 2), on the nearest gate within 96 blocks:
 * <pre>
 * break gate &lt;rank&gt;        a gate of that rank opens a few steps in front of you
 * break status              its clock: phase, time left, waves, its boss
 * break unstable            it starts coming apart now (its collapse timer shows)
 * break imminent            its last minute starts now (it counts down while someone watches)
 * break clock &lt;seconds&gt;     it breaks in that many seconds (counted like any clock)
 * break now                 it breaks now
 * break boss                its boss comes through now (a broken gate)
 * break suppress            the break ends as if its boss fell
 * break sense [level]       a gate-sensing pulse from where you stand
 * </pre>
 */
@EventBusSubscriber(modid = com.beyondsolo.BeyondSolo.MOD_ID)
public final class BreakCommand {
    private static final SimpleCommandExceptionType NO_GATE = new SimpleCommandExceptionType(Component.literal("no gate within 96 blocks"));
    private static final SimpleCommandExceptionType NOT_BROKEN = new SimpleCommandExceptionType(Component.literal("that gate has not broken"));

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("break")
                        .then(Commands.literal("gate").then(Commands.argument("rank", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(PlayerRank.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), b))
                                .executes(BreakCommand::gate)))
                        .then(Commands.literal("status").executes(ctx -> {
                            GateInstance g = nearest(ctx);
                            ServerLevel level = ctx.getSource().getPlayerOrException().serverLevel();
                            ctx.getSource().sendSuccess(() -> Component.literal(describe(g, level.getGameTime())), false);
                            return 1;
                        }))
                        .then(Commands.literal("unstable").executes(ctx -> clock(ctx, -1)))
                        .then(Commands.literal("imminent").executes(ctx -> clock(ctx, DungeonBreaks.IMMINENT_TICKS / 20)))
                        .then(Commands.literal("clock").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 1_000_000))
                                .executes(ctx -> clock(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))
                        .then(Commands.literal("now").executes(ctx -> {
                            GateInstance g = nearest(ctx);
                            ServerLevel level = ctx.getSource().getPlayerOrException().serverLevel();
                            DungeonBreaks.breakOpen(level, GateData.get(level.getServer()), g, level.getGameTime());
                            return 1;
                        }))
                        .then(Commands.literal("boss").executes(ctx -> {
                            GateInstance g = nearest(ctx);
                            if (!g.breakState().broken()) throw NOT_BROKEN.create();
                            ServerLevel level = ctx.getSource().getPlayerOrException().serverLevel();
                            Entity old = g.breakState().boss() == null ? null : level.getEntity(g.breakState().boss());
                            if (old != null) old.discard();
                            DungeonBreaks.bossOut(level, GateData.get(level.getServer()), g);
                            return 1;
                        }))
                        .then(Commands.literal("suppress").executes(ctx -> {
                            GateInstance g = nearest(ctx);
                            if (!g.breakState().broken()) throw NOT_BROKEN.create();
                            ServerLevel level = ctx.getSource().getPlayerOrException().serverLevel();
                            Entity boss = g.breakState().boss() == null ? null : level.getEntity(g.breakState().boss());
                            if (boss instanceof GateBossEntity b) b.kill();
                            else DungeonBreaks.suppress(level, GateData.get(level.getServer()), g, null);
                            return 1;
                        }))
                        .then(Commands.literal("sense")
                                .executes(ctx -> sense(ctx, 10))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 20))
                                        .executes(ctx -> sense(ctx, IntegerArgumentType.getInteger(ctx, "level")))))));
    }

    private static int gate(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PlayerRank rank = PlayerRank.byName(StringArgumentType.getString(ctx, "rank"));
        if (rank == null) rank = PlayerRank.D;
        Direction look = p.getDirection();
        BlockPos at = p.blockPosition().relative(look, 7);
        at = p.serverLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at);
        GateService.spawnGate(p.serverLevel(), at, rank, look.getOpposite(), true);
        return 1;
    }

    /** Sets the nearest gate's clock: {@code seconds} until it breaks (-1: to the start of its last third). */
    private static int clock(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        GateInstance g = nearest(ctx);
        ServerLevel level = ctx.getSource().getPlayerOrException().serverLevel();
        BreakState b = g.breakState();
        if (b.hasBroken()) throw new SimpleCommandExceptionType(Component.literal("that gate has already broken")).create();
        long now = level.getGameTime();
        if (!b.clockStarted()) b.start(now, DungeonBreaks.clockTicks(level.random));
        long left = seconds < 0 ? b.length() / 3 : seconds * 20L;
        b.setBreakAt(now + left);
        if (seconds >= 0 && left > DungeonBreaks.IMMINENT_TICKS && b.phase() == BreakState.Phase.IMMINENT) b.setPhase(BreakState.Phase.UNSTABLE);
        GateData.get(level.getServer()).setDirty();
        ctx.getSource().sendSuccess(() -> Component.literal("breaks in " + left / 20 + "s: " + g.rank() + "-rank gate at " + g.gatePos().toShortString()), false);
        return 1;
    }

    private static int sense(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        int n = GateSense.pulse(p, level, 12);
        ctx.getSource().sendSuccess(() -> Component.literal(n + " gate(s) within " + GateSense.range(level) + " blocks"), false);
        return n;
    }

    private static GateInstance nearest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        GateBlockEntity gate = GateService.nearestGate(p, 96);
        GateInstance g = gate == null || gate.gateId() == null ? null : GateData.get(p.server).get(gate.gateId());
        if (g == null) throw NO_GATE.create();
        return g;
    }

    static String describe(GateInstance g, long now) {
        BreakState b = g.breakState();
        StringBuilder s = new StringBuilder(g.rank() + "-rank gate at " + g.gatePos().toShortString() + ": " + b.phase().name().toLowerCase(Locale.ROOT));
        if (!DungeonBreaks.canBreak(g) && !b.hasBroken()) s.append(" (never breaks: ").append(g.isTrial() ? "trial" : g.isKeyed() ? "keyed" : g.state().name().toLowerCase(Locale.ROOT)).append(')');
        else if (!b.clockStarted()) s.append(", clock not started");
        else if (!b.hasBroken()) s.append(", breaks in ").append(b.left(now) / 20).append("s of ").append(b.length() / 20).append('s');
        if (b.hasBroken()) s.append(", waves ").append(b.waves()).append(", killed ").append(b.killed()).append(b.bossOut() ? ", boss out" : "");
        return s.toString();
    }

    private BreakCommand() {}
}
