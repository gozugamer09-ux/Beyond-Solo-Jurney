package com.beyondsolo.command;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateSpawner;
import com.beyondsolo.gate.RewardCacheBlock;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.system.PlayerRank;
import com.mojang.brigadier.CommandDispatcher;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;

/**
 * /system gate ... testing commands (permission level 2):
 * spawn [rank] (a gate opens 4 blocks in front of you), near (open one nearby like the natural spawner), enter
 * (go through the nearest gate now), boss (to the boss's arena), wake, kill (defeat the boss), cache [rank]
 * (a reward cache drops in front of you), list.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class GateCommand {
    private static final SimpleCommandExceptionType NONE = new SimpleCommandExceptionType(Component.translatable("command.beyondsolo.gate.none"));
    private static final SimpleCommandExceptionType NOT_INSIDE = new SimpleCommandExceptionType(Component.translatable("command.beyondsolo.gate.not_inside"));

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("gate")
                        .then(Commands.literal("spawn")
                                .executes(ctx -> spawn(ctx, null))
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(PlayerRank.values()).map(r -> r.name().toLowerCase()), b))
                                        .executes(ctx -> spawn(ctx, PlayerRank.byName(StringArgumentType.getString(ctx, "rank"))))))
                        .then(Commands.literal("near").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            if (GateSpawner.trySpawnNear(p.serverLevel(), p) == null) throw NONE.create();
                            return 1;
                        }))
                        .then(Commands.literal("enter").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateBlockEntity gate = GateService.nearestGate(p, 64);
                            if (gate == null || gate.gateId() == null) throw NONE.create();
                            GateInstance g = GateData.get(p.server).get(gate.gateId());
                            if (g == null) throw NONE.create();
                            GateService.arrive(p, g);
                            return 1;
                        }))
                        .then(Commands.literal("boss").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = inside(p);
                            BlockPos c = g.bossRoomCenter();
                            BlockPos door = c.offset(g.entrance().subtract(c).getX() > 0 ? 9 : -9, 0, 0);
                            p.teleportTo(p.serverLevel(), door.getX() + 0.5, c.getY(), c.getZ() + 0.5, p.getYRot(), 0);
                            return 1;
                        }))
                        .then(Commands.literal("wake").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateService.wakeBossNow(p.serverLevel(), inside(p));
                            return 1;
                        }))
                        .then(Commands.literal("kill").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = inside(p);
                            Entity e = g.boss() == null ? null : p.serverLevel().getEntity(g.boss());
                            if (!(e instanceof GateBossEntity boss)) throw NONE.create();
                            if (!boss.isAwake()) GateService.wakeBossNow(p.serverLevel(), g);
                            boss.setHealth(1f);
                            boss.hurt(p.serverLevel().damageSources().playerAttack(p), 1000f);
                            if (boss.isAlive()) boss.kill();
                            return 1;
                        }))
                        .then(Commands.literal("cache")
                                .executes(ctx -> cache(ctx, PlayerRank.C))
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .executes(ctx -> cache(ctx, PlayerRank.byName(StringArgumentType.getString(ctx, "rank"))))))
                        .then(Commands.literal("list").executes(ctx -> {
                            for (GateInstance g : GateData.get(ctx.getSource().getServer()).all()) {
                                ctx.getSource().sendSuccess(() -> Component.literal(g.rank() + " " + g.state() + " at " + g.gatePos().toShortString()
                                        + " slot " + g.slot() + (g.built() ? " built" : "")), false);
                            }
                            return 1;
                        }))));
    }

    private static GateInstance inside(ServerPlayer p) throws CommandSyntaxException {
        if (!GateService.isDungeon(p.level())) throw NOT_INSIDE.create();
        GateInstance g = GateData.get(p.server).bySlotPos(p.blockPosition());
        if (g == null) throw NOT_INSIDE.create();
        return g;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, PlayerRank rank) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        Direction look = p.getDirection();
        BlockPos pos = p.blockPosition().relative(look, 5);
        if (rank == null) rank = PlayerRank.values()[level.random.nextInt(PlayerRank.values().length)];
        GateService.spawnGate(level, pos, rank, look.getOpposite(), true);
        return 1;
    }

    private static int cache(CommandContext<CommandSourceStack> ctx, PlayerRank rank) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        Direction look = p.getDirection();
        BlockPos pos = p.blockPosition().relative(look, 3);
        level.setBlock(pos, ModBlocks.REWARD_CACHE.get().defaultBlockState().setValue(RewardCacheBlock.FACING, look.getOpposite()), 3);
        if (level.getBlockEntity(pos) instanceof RewardCacheBlockEntity cache) cache.setup(rank == null ? PlayerRank.C : rank, level.getGameTime());
        return 1;
    }

    private GateCommand() {}
}
