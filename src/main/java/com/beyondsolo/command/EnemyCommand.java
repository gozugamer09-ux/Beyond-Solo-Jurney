package com.beyondsolo.command;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Affix;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.EnemyRoster;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.HiddenLevel;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Testing commands for the enemy roster (permission level 2):
 * <ul>
 *   <li>/system enemy spawn &lt;kind&gt; [rank] [variant] [affinity] — one of the gate's creatures in front of you</li>
 *   <li>/system enemy boss &lt;kind&gt; [rank] [ascended] — a boss in front of you, awake (it opens nothing when it dies)</li>
 *   <li>/system enemy mix [rank] — what a random gate of that rank would hold</li>
 *   <li>/system enemy levels — the hidden levels of the creatures around you, against your own, and the colour each
 *   name shows in</li>
 *   <li>/system gate spawnboss &lt;rank&gt; &lt;boss&gt; [ascended] — a gate whose boss is that kind</li>
 * </ul>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class EnemyCommand {
    private static final SimpleCommandExceptionType UNKNOWN = new SimpleCommandExceptionType(Component.literal("Unknown kind"));

    @SubscribeEvent
    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("system").requires(src -> src.hasPermission(2))
                .then(Commands.literal("enemy")
                        .then(Commands.literal("spawn").then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(MonsterKind.values()).map(k -> k.id), b))
                                .executes(ctx -> spawn(ctx, PlayerRank.C, null, null))
                                .then(rank().executes(ctx -> spawn(ctx, rankArg(ctx), null, null))
                                        .then(Commands.argument("variant", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Variant.values()).map(v -> v.name().toLowerCase()), b))
                                                .executes(ctx -> spawn(ctx, rankArg(ctx), StringArgumentType.getString(ctx, "variant"), null))
                                                .then(Commands.argument("affinity", StringArgumentType.word())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Affinity.values()).map(a -> a.name().toLowerCase()), b))
                                                        .executes(ctx -> spawn(ctx, rankArg(ctx), StringArgumentType.getString(ctx, "variant"),
                                                                StringArgumentType.getString(ctx, "affinity"))))))))
                        .then(Commands.literal("boss").then(bossArg()
                                .executes(ctx -> boss(ctx, PlayerRank.C, false))
                                .then(rank().executes(ctx -> boss(ctx, rankArg(ctx), false))
                                        .then(Commands.argument("ascended", BoolArgumentType.bool())
                                                .executes(ctx -> boss(ctx, rankArg(ctx), BoolArgumentType.getBool(ctx, "ascended")))))))
                        .then(Commands.literal("mix")
                                .executes(ctx -> mix(ctx, PlayerRank.C))
                                .then(rank().executes(ctx -> mix(ctx, rankArg(ctx)))))
                        .then(Commands.literal("levels").executes(EnemyCommand::levels)))
                .then(Commands.literal("gate").then(Commands.literal("spawnboss").then(rank().then(bossArg()
                        .executes(ctx -> gate(ctx, false))
                        .then(Commands.argument("ascended", BoolArgumentType.bool())
                                .executes(ctx -> gate(ctx, BoolArgumentType.getBool(ctx, "ascended")))))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> rank() {
        return Commands.argument("rank", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(PlayerRank.values()).map(r -> r.name().toLowerCase()), b));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> bossArg() {
        return Commands.argument("boss", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(BossKind.values()).map(k -> k.id), b));
    }

    private static PlayerRank rankArg(CommandContext<CommandSourceStack> ctx) {
        return PlayerRank.byName(StringArgumentType.getString(ctx, "rank"));
    }

    private static BossKind bossKind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BossKind k = BossKind.byId(StringArgumentType.getString(ctx, "boss"));
        if (k == null) throw UNKNOWN.create();
        return k;
    }

    private static BlockPos ahead(ServerPlayer p, int blocks) {
        return p.blockPosition().relative(p.getDirection(), blocks);
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, PlayerRank rank, String variant, String affinity) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "kind");
        MonsterKind kind = Arrays.stream(MonsterKind.values()).filter(k -> k.id.equalsIgnoreCase(id)).findFirst().orElseThrow(UNKNOWN::create);
        Variant v = variant == null ? Variant.NORMAL : Arrays.stream(Variant.values()).filter(x -> x.name().equalsIgnoreCase(variant)).findFirst().orElse(Variant.NORMAL);
        Affinity a = affinity == null ? Affinity.NONE : Arrays.stream(Affinity.values()).filter(x -> x.name().equalsIgnoreCase(affinity)).findFirst().orElse(Affinity.NONE);
        int affixes = Affix.mask(v.affixes > 0 ? Affix.roll(v.affixes, p.getRandom()) : EnumSet.noneOf(Affix.class));
        Enemies.spawn(p.serverLevel(), rank, EnemyModule.type(kind), ahead(p, 4), new EnemyTraits(a, v, affixes), -1);
        return 1;
    }

    private static int boss(CommandContext<CommandSourceStack> ctx, PlayerRank rank, boolean ascended) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        BossKind kind = bossKind(ctx);
        GateBossEntity boss = ModEntities.GATE_BOSS.get().create(level);
        if (boss == null) return 0;
        BlockPos at = ahead(p, 6);
        boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot() + 180, 0);
        boss.configure(rank, kind, ascended);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.COMMAND, null);
        level.addFreshEntity(boss);
        boss.wake();
        return 1;
    }

    /** The hidden levels of the System's creatures within 24 blocks, nearest first, against your own (for the balancing pass). */
    private static int levels(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        float hunter = HiddenLevel.hunterLevel(SystemAPI.get(p));
        List<LivingEntity> near = new ArrayList<>(p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24), HiddenLevel::has));
        near.sort(Comparator.comparingDouble(p::distanceToSqr));
        StringBuilder b = new StringBuilder(String.format(java.util.Locale.ROOT, "You: hunter level %.1f (level %d, rank %s)",
                hunter, SystemAPI.get(p).level(), SystemAPI.get(p).rank()));
        for (LivingEntity e : near.subList(0, Math.min(10, near.size()))) {
            int level = HiddenLevel.of(e);
            b.append(String.format(java.util.Locale.ROOT, "%n%s: level %d, power %.1f -> %s", e.getDisplayName().getString(), level,
                    HiddenLevel.power(e), HiddenLevel.Threat.of(level, hunter).word().getString()));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(b.toString()), false);
        return near.size();
    }

    private static int mix(CommandContext<CommandSourceStack> ctx, PlayerRank rank) {
        var mix = EnemyRoster.random(rank, ctx.getSource().getLevel().random);
        ctx.getSource().sendSuccess(() -> Component.literal(rank + ": " + EnemyRoster.describe(mix)
                + " | boss " + BossKind.roll(rank, ctx.getSource().getLevel().random).id), false);
        return 1;
    }

    private static int gate(CommandContext<CommandSourceStack> ctx, boolean ascended) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        BossKind kind = bossKind(ctx);
        Direction look = p.getDirection();
        GateBlockEntity gate = GateService.spawnGate(level, ahead(p, 5), rankArg(ctx), look.getOpposite(), true);
        GateInstance g = gate.gateId() == null ? null : GateData.get(p.server).get(gate.gateId());
        if (g != null) {
            g.setBossKind(kind);
            g.setBossAscended(ascended);
            GateData.get(p.server).setDirty();
        }
        return 1;
    }

    private EnemyCommand() {}
}
