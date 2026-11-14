package com.beyondsolo.command;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.awakening.ArbiterEntity;
import com.beyondsolo.awakening.AwakeningData;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.spawn.FirstSpawnHandler;
import com.beyondsolo.system.SystemAPI;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /system trial ... testing commands for the awakening (permission level 2):
 * status, reset (back to the very start: no System, not called), call (the sealed gate opens for you now), enter
 * (into the trial), arbiter (to the door of its hall), wake, fall (as if the Arbiter had struck you down), slay (kill
 * the Arbiter), grant (the System binds to you without the trial).
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class AwakeningCommand {
    private static final SimpleCommandExceptionType NOT_NOW = new SimpleCommandExceptionType(Component.translatable("command.beyondsolo.trial.not_now"));

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("trial")
                        .then(Commands.literal("status").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            AwakeningData d = AwakeningService.data(p);
                            ctx.getSource().sendSuccess(() -> Component.literal("System: " + SystemAPI.hasSystem(p) + ", stage: " + d.stage()
                                    + (d.sealedPos() != null ? ", sealed gate at " + d.sealedPos().toShortString() : "")), false);
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            AwakeningService.reset(p);
                            SystemAPI.reset(p);
                            com.beyondsolo.quest.QuestEngine.reset(p);
                            return 1;
                        }))
                        .then(Commands.literal("call").executes(ctx -> {
                            if (!AwakeningService.call(ctx.getSource().getPlayerOrException())) throw NOT_NOW.create();
                            return 1;
                        }))
                        .then(Commands.literal("enter").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = trial(p, true);
                            GateService.arrive(p, g);
                            return 1;
                        }))
                        .then(Commands.literal("arbiter").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = trial(p, false);
                            BlockPos c = g.bossRoomCenter();
                            BlockPos from = g.entrance();
                            for (var t : g.layout().tunnels()) if (g.layout().rooms().get(t.to()).kind() == com.beyondsolo.gate.DungeonLayout.Kind.BOSS) from = g.world(t.start());
                            double dx = from.getX() - c.getX(), dz = from.getZ() - c.getZ();
                            double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
                            double reach = g.layout().boss().reach() - 5;
                            float yaw = (float) (Math.atan2(-dz, -dx) * 180 / Math.PI) - 90f;
                            p.teleportTo(p.serverLevel(), c.getX() + 0.5 + dx / len * reach, c.getY(), c.getZ() + 0.5 + dz / len * reach, yaw, 0);
                            return 1;
                        }))
                        .then(Commands.literal("wake").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateService.wakeBossNow(p.serverLevel(), trial(p, false));
                            return 1;
                        }))
                        .then(Commands.literal("fall").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            AwakeningService.begin(p, trial(p, false), false);
                            return 1;
                        }))
                        .then(Commands.literal("slay").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = trial(p, false);
                            ArbiterEntity a = AwakeningService.arbiter(p.serverLevel(), g);
                            if (a == null) throw NOT_NOW.create();
                            if (!a.isAwake()) GateService.wakeBossNow(p.serverLevel(), g);
                            a.setHealth(1f);
                            a.kill();
                            return 1;
                        }))
                        .then(Commands.literal("grant").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            AwakeningService.reset(p);
                            AwakeningService.data(p).setStage(AwakeningData.Stage.DONE);
                            SystemAPI.grantSystem(p);
                            FirstSpawnHandler.systemIntro(p, 20);
                            return 1;
                        }))));
    }

    /** The player's trial: the one they stand in, or (with {@code orCalled}) the one behind their sealed gate. */
    private static GateInstance trial(ServerPlayer p, boolean orCalled) throws CommandSyntaxException {
        GateInstance g = AwakeningService.trialAt(p);
        if (g == null && orCalled) {
            AwakeningData d = AwakeningService.data(p);
            if (d.trial() == null && !AwakeningService.call(p)) throw NOT_NOW.create();
            g = d.trial() == null ? null : GateData.get(p.server).get(d.trial());
        }
        if (g == null) throw NOT_NOW.create();
        return g;
    }

    private AwakeningCommand() {}
}
