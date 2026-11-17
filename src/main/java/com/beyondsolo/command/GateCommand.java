package com.beyondsolo.command;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.DungeonShape;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateRealm;
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
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * /system gate ... testing commands (permission level 2):
 * spawn [rank] [realm|any] [shape] (a gate opens 4 blocks in front of you; its dungeon of that realm and shape),
 * near (open one nearby like the natural spawner), enter (go through the nearest gate now), boss (to the door of the
 * boss's room), wake, kill (defeat the boss), info (the dungeon you are in), cache [rank] (a reward cache drops in
 * front of you), list.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class GateCommand {
    private static final SimpleCommandExceptionType NONE = new SimpleCommandExceptionType(Component.translatable("command.beyondsolo.gate.none"));
    private static final SimpleCommandExceptionType NOT_INSIDE = new SimpleCommandExceptionType(Component.translatable("command.beyondsolo.gate.not_inside"));
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id -> Component.translatable("command.beyondsolo.gate.unknown", id));

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("gate")
                        .then(Commands.literal("spawn")
                                .executes(ctx -> spawn(ctx, null, null, null))
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(PlayerRank.values()).map(r -> r.name().toLowerCase()), b))
                                        .executes(ctx -> spawn(ctx, rank(ctx), null, null))
                                        .then(Commands.argument("realm", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.concat(Stream.of("any"),
                                                        Arrays.stream(GateRealm.values()).filter(r -> r != GateRealm.THRESHOLD).map(GateRealm::id)), b))
                                                .executes(ctx -> spawn(ctx, rank(ctx), realm(ctx), null))
                                                .then(Commands.argument("shape", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(DungeonShape.values()).map(DungeonShape::id), b))
                                                        .executes(ctx -> spawn(ctx, rank(ctx), realm(ctx), shape(ctx)))))))
                        .then(Commands.literal("near").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            if (GateSpawner.trySpawnNear(p.serverLevel(), p) == null) throw NONE.create();
                            return 1;
                        }))
                        // what the once-a-minute gate spawner sees around you; "now" runs its minute for you at once
                        .then(Commands.literal("spawner")
                                .executes(ctx -> spawner(ctx, false))
                                .then(Commands.literal("now").executes(ctx -> spawner(ctx, true))))
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
                            // just inside the door of the boss's room, looking in
                            BlockPos c = g.bossRoomCenter();
                            DungeonLayout.Room room = g.layout().boss();
                            Vec3 way = GateService.doorward(g, Vec3.atBottomCenterOf(c));
                            double k = Math.max(3, room.reach() - 3);
                            float yaw = (float) (Mth.atan2(-way.z, -way.x) * Mth.RAD_TO_DEG) - 90f;
                            p.teleportTo(p.serverLevel(), c.getX() + 0.5 + way.x * k, c.getY(), c.getZ() + 0.5 + way.z * k, yaw, 0);
                            return 1;
                        }))
                        .then(Commands.literal("info").executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            GateInstance g = inside(p);
                            DungeonLayout layout = g.layout();
                            Component text = Component.translatable("command.beyondsolo.gate.info", g.dungeonName(), g.realm().id(),
                                    g.shape().displayName(), g.rank().name(), layout.rooms().size(), layout.tunnels().size(), layout.spaces().size(),
                                    g.bossKind().name(g.rank()), g.element().displayName(), Long.toString(g.seed()));
                            ctx.getSource().sendSuccess(() -> text, false);
                            if (g.legacy()) ctx.getSource().sendSuccess(() -> Component.translatable("command.beyondsolo.gate.info_legacy"), false);
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
                                        + " slot " + g.slot() + " " + g.realm().id() + "/" + g.shape().id() + (g.built() ? " built" : "")), false);
                            }
                            return 1;
                        }))));
    }

    private static int spawner(CommandContext<CommandSourceStack> ctx, boolean now) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (now) {
            GateSpawner.Outcome outcome = GateSpawner.minute(p.server.overworld(), p);
            ctx.getSource().sendSuccess(() -> Component.literal("Gate spawner minute: " + outcome), false);
        }
        String who = !com.beyondsolo.system.SystemAPI.hasSystem(p) ? "no System (D-rank gates, " + com.beyondsolo.awakening.AwakeningService.stage(p) + ")"
                : !com.beyondsolo.system.SystemAPI.isAwakened(p) ? "not evaluated (no gates open)" : "hunter (any rank)";
        java.util.List<GateInstance> near = GateSpawner.openGatesNear(p.server, p.blockPosition(), GateSpawner.NEAR, null);
        StringBuilder gates = new StringBuilder();
        for (GateInstance g : near) {
            gates.append("\n  ").append(g.rank()).append(" at ").append(g.gatePos().toShortString())
                    .append(" (").append((int) Math.sqrt(GateSpawner.flatDistSqr(p.position(), g.gatePos()))).append(" blocks, ")
                    .append(GateSpawner.knows(p, g) ? "known" : "not seen yet").append(")");
        }
        GateSpawner.Rules hunters = GateSpawner.Rules.hunters(), unawakened = GateSpawner.Rules.unawakened();
        String rules = "hunters " + (hunters == null ? "off" : hunters) + ", no System " + (unawakened == null ? "off" : unawakened);
        int quiet = GateSpawner.quiet(p);
        String text = "You: " + who + ". Quiet minutes: " + (quiet < 0 ? "none yet (first gate due)" : quiet)
                + ". Open gates within " + GateSpawner.NEAR + ": " + near.size() + gates + "\nRules: " + rules;
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static GateInstance inside(ServerPlayer p) throws CommandSyntaxException {
        if (!GateService.isDungeon(p.level())) throw NOT_INSIDE.create();
        GateInstance g = GateData.get(p.server).bySlotPos(p.blockPosition());
        if (g == null) throw NOT_INSIDE.create();
        return g;
    }

    private static PlayerRank rank(CommandContext<CommandSourceStack> ctx) {
        return PlayerRank.byName(StringArgumentType.getString(ctx, "rank"));
    }

    /** The realm named (null: any, picked by the gate's seed). */
    private static GateRealm realm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "realm");
        if (id.equalsIgnoreCase("any") || id.equalsIgnoreCase("random")) return null;
        GateRealm realm = GateRealm.byId(id);
        if (realm == null) throw UNKNOWN.create(id);
        return realm;
    }

    private static DungeonShape shape(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "shape");
        DungeonShape shape = DungeonShape.byId(id);
        if (shape == null) throw UNKNOWN.create(id);
        return shape;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, PlayerRank rank, GateRealm realm, DungeonShape shape) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        Direction look = p.getDirection();
        BlockPos pos = p.blockPosition().relative(look, 5);
        if (rank == null) rank = PlayerRank.values()[level.random.nextInt(PlayerRank.values().length)];
        GateBlockEntity gate = GateService.spawnGate(level, pos, rank, look.getOpposite(), true);
        GateInstance g = gate.gateId() == null ? null : GateData.get(level.getServer()).get(gate.gateId());
        if (g != null && (realm != null || shape != null)) {
            g.setDungeon(realm, shape);
            GateData.get(level.getServer()).setDirty();
        }
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
