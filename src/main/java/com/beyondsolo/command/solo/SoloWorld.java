package com.beyondsolo.command.solo;

import com.beyondsolo.Config;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.awakening.ArbiterEntity;
import com.beyondsolo.breaks.DungeonBreaks;
import com.beyondsolo.depths.DepthsCommand;
import com.beyondsolo.depths.HiddenKind;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Affix;
import com.beyondsolo.enemy.Enemies;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.DungeonShape;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateRealm;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.gate.GateWiper;
import com.beyondsolo.gate.RewardCacheBlock;
import com.beyondsolo.gate.RewardCacheBlockEntity;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.JobTrial;
import com.beyondsolo.penalty.PenaltyZone;
import com.beyondsolo.redgate.RedGateData;
import com.beyondsolo.redgate.RedGates;
import com.beyondsolo.redgate.RedWorld;
import com.beyondsolo.registry.ModBlocks;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.tower.RushTier;
import com.beyondsolo.tower.TowerAPI;
import com.beyondsolo.tower.TowerData;
import com.beyondsolo.tower.TowerFloors;
import com.beyondsolo.tower.TowerRun;
import com.beyondsolo.util.ServerScheduler;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The world: /solo spawn (creatures, bosses, gates, caches, a crystal) where the sender stands, /solo gate (the gates
 * out in the world), /solo dungeon (into, through and out of them), /solo trial and /solo tower.
 */
final class SoloWorld {
    /** The kinds of gate /solo spawn gate opens. */
    static final List<String> GATE_TYPES = List.of("normal", "red", "vault", "shrine", "lair", "breaking");

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(spawn());
        root.then(gate());
        root.then(dungeon());
        root.then(trial());
        root.then(tower());
    }

    // ================================================================ spawn

    private static LiteralArgumentBuilder<CommandSourceStack> spawn() {
        return Commands.literal("spawn")
                .then(Commands.literal("creature").then(creatureArgs()))
                .then(Commands.literal("boss").then(bossArgs()))
                .then(Commands.literal("gate").then(gateArgs()).executes(ctx -> spawnGate(ctx.getSource(), "random", "normal", null, null)))
                .then(Commands.literal("cache")
                        .executes(ctx -> cache(ctx.getSource(), PlayerRank.C))
                        .then(Solo.rankArg().executes(ctx -> cache(ctx.getSource(), Solo.rank(ctx)))))
                .then(Commands.literal("crystal").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    BlockPos at = Solo.ahead(src, 2);
                    src.getLevel().setBlock(at, ModBlocks.EVALUATION_CRYSTAL.get().defaultBlockState(), 3);
                    src.sendSuccess(() -> Solo.t("spawn.crystal", at.toShortString()), false);
                    return 1;
                }));
    }

    /** {@code <kind|random> [count] [rank] [variant] [affinity]}. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> creatureArgs() {
        return Solo.word("kind", () -> Stream.concat(Stream.of("random"), Arrays.stream(MonsterKind.values()).map(k -> k.id)))
                .executes(ctx -> creatures(ctx, 1, "c", null, null))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                        .executes(ctx -> creatures(ctx, count(ctx), "c", null, null))
                        .then(Solo.rankArg()
                                .executes(ctx -> creatures(ctx, count(ctx), str(ctx, "rank"), null, null))
                                .then(Solo.word("variant", () -> Stream.concat(Arrays.stream(Variant.values()).map(v -> Solo.lower(v.name())), Stream.of("random")))
                                        .executes(ctx -> creatures(ctx, count(ctx), str(ctx, "rank"), str(ctx, "variant"), null))
                                        .then(Solo.word("affinity", () -> Stream.concat(Arrays.stream(Affinity.values()).map(a -> Solo.lower(a.name())), Stream.of("random")))
                                                .executes(ctx -> creatures(ctx, count(ctx), str(ctx, "rank"), str(ctx, "variant"), str(ctx, "affinity")))))));
    }

    private static int count(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "count");
    }

    private static String str(CommandContext<CommandSourceStack> ctx, String name) {
        return StringArgumentType.getString(ctx, name);
    }

    /** Some of the gates' own creatures in front of the sender, spread a little when there are several. */
    private static int creatures(CommandContext<CommandSourceStack> ctx, int count, String rankName, @Nullable String variant, @Nullable String affinity)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        RandomSource r = level.random;
        String kindName = str(ctx, "kind");
        MonsterKind named = kindName.equalsIgnoreCase("random") ? null
                : Arrays.stream(MonsterKind.values()).filter(k -> k.id.equalsIgnoreCase(kindName)).findFirst().orElseThrow(() -> Solo.error("unknown_creature", kindName));
        BlockPos base = Solo.ahead(src, 4);
        Mob last = null;
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            MonsterKind kind = named != null ? named : MonsterKind.values()[r.nextInt(MonsterKind.values().length)];
            PlayerRank rank = Solo.rank(rankName, src);
            Variant v = pick(Variant.values(), variant, Variant.NORMAL, r);
            Affinity a = pick(Affinity.values(), affinity, Affinity.NONE, r);
            int affixes = Affix.mask(v.affixes > 0 ? Affix.roll(v.affixes, r) : EnumSet.noneOf(Affix.class));
            BlockPos at = i == 0 ? base : base.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
            Mob mob = Enemies.spawn(level, rank, EnemyModule.type(kind), free(level, at), new EnemyTraits(a, v, affixes), -1);
            if (mob != null) {
                last = mob;
                spawned++;
            }
        }
        if (spawned == 0) throw Solo.error("spawn_failed");
        Mob one = last;
        int n = spawned;
        src.sendSuccess(() -> n == 1 ? Solo.t("spawn.creature", one.getDisplayName()) : Solo.t("spawn.creatures", n), false);
        return spawned;
    }

    /** The value named (any of them for "random", the default for none). */
    private static <E extends Enum<E>> E pick(E[] values, @Nullable String name, E fallback, RandomSource r) throws CommandSyntaxException {
        if (name == null) return fallback;
        if (name.equalsIgnoreCase("random")) return values[r.nextInt(values.length)];
        for (E e : values) if (e.name().equalsIgnoreCase(name)) return e;
        throw Solo.error("unknown_value", name);
    }

    /** The first spot at or above {@code at} with room to stand. */
    private static BlockPos free(ServerLevel level, BlockPos at) {
        BlockPos p = at;
        for (int i = 0; i < 6 && !level.getBlockState(p).getCollisionShape(level, p).isEmpty(); i++) p = p.above();
        return p;
    }

    /** {@code <kind|random> [rank] [ascended]}. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> bossArgs() {
        return Solo.word("kind", () -> Stream.concat(Stream.of("random"), Arrays.stream(BossKind.values()).map(k -> k.id)))
                .executes(ctx -> boss(ctx, "c", false))
                .then(Solo.rankArg()
                        .executes(ctx -> boss(ctx, str(ctx, "rank"), false))
                        .then(Commands.literal("ascended").executes(ctx -> boss(ctx, str(ctx, "rank"), true))));
    }

    /** A gate boss in front of the sender, awake. Nothing opens when it falls: it belongs to no gate. */
    private static int boss(CommandContext<CommandSourceStack> ctx, String rankName, boolean ascended) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        PlayerRank rank = Solo.rank(rankName, src);
        String kindName = str(ctx, "kind");
        BossKind kind = kindName.equalsIgnoreCase("random") ? BossKind.roll(rank, level.random) : BossKind.byId(kindName);
        if (kind == null) throw Solo.error("unknown_boss", kindName);
        GateBossEntity boss = ModEntities.GATE_BOSS.get().create(level);
        if (boss == null) throw Solo.error("spawn_failed");
        BlockPos at = free(level, Solo.ahead(src, 6));
        boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, src.getRotation().y + 180, 0);
        boss.configure(rank, kind, ascended);
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.COMMAND, null);
        level.addFreshEntity(boss);
        boss.wake();
        src.sendSuccess(() -> Solo.t("spawn.boss", kind.name(rank), rank.displayName()), false);
        return 1;
    }

    /** {@code [rank] [type] [realm] [shape]}. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> gateArgs() {
        return Solo.rankArg()
                .executes(ctx -> spawnGate(ctx.getSource(), str(ctx, "rank"), "normal", null, null))
                .then(Solo.word("type", GATE_TYPES::stream)
                        .executes(ctx -> spawnGate(ctx.getSource(), str(ctx, "rank"), str(ctx, "type"), null, null))
                        .then(Solo.word("realm", () -> Stream.concat(Stream.of("any"), Arrays.stream(GateRealm.values()).filter(r -> r != GateRealm.THRESHOLD).map(GateRealm::id)))
                                .executes(ctx -> spawnGate(ctx.getSource(), str(ctx, "rank"), str(ctx, "type"), str(ctx, "realm"), null))
                                .then(Solo.word("shape", () -> Arrays.stream(DungeonShape.values()).map(DungeonShape::id))
                                        .executes(ctx -> spawnGate(ctx.getSource(), str(ctx, "rank"), str(ctx, "type"), str(ctx, "realm"), str(ctx, "shape"))))));
    }

    /**
     * A gate tears open 5 blocks in front of the sender: an ordinary one, a red one (it turns red when an evaluated
     * hunter is the first through), one with a hidden vault, shrine or lair, or one a minute from breaking.
     */
    private static int spawnGate(CommandSourceStack src, String rankName, String type, @Nullable String realmName, @Nullable String shapeName)
            throws CommandSyntaxException {
        ServerLevel level = src.getLevel();
        MinecraftServer server = src.getServer();
        // the caves' own world (not GateService.isDungeon: on the game test server the overworld stands in for it)
        if (level.dimension().equals(GateService.DIMENSION)) throw Solo.error("gate_here");
        if (!GATE_TYPES.contains(Solo.lower(type))) throw Solo.error("unknown_gate_type", type);
        String kind = Solo.lower(type);
        PlayerRank rank = Solo.rank(rankName, src);
        GateRealm realm = null;
        if (realmName != null && !realmName.equalsIgnoreCase("any") && !realmName.equalsIgnoreCase("random")) {
            realm = GateRealm.byId(realmName);
            if (realm == null) throw Solo.error("unknown_realm", realmName);
        }
        DungeonShape shape = null;
        if (shapeName != null) {
            shape = DungeonShape.byId(shapeName);
            if (shape == null) throw Solo.error("unknown_shape", shapeName);
        }
        if (kind.equals("red") && RedWorld.level(server) == null) throw Solo.error("no_red_world");
        if (kind.equals("breaking") && Config.GATE_BREAK_MINUTES.get() <= 0) throw Solo.error("breaks_off");
        Direction look = Solo.facing(src);
        BlockPos at = Solo.ahead(src, 5);
        GateBlockEntity gate = GateService.spawnGate(level, at, rank, look.getOpposite(), true);
        GateInstance g = gate.gateId() == null ? null : GateData.get(server).get(gate.gateId());
        if (g == null) throw Solo.error("spawn_failed");
        if (realm != null || shape != null) g.setDungeon(realm, shape);
        Component said;
        switch (kind) {
            case "red" -> {
                RedGateData.get(server).force(g.id(), true);
                said = Solo.t("spawn.gate.red", rank.displayName());
            }
            case "vault", "shrine", "lair" -> {
                DepthsCommand.force(g, "seed", kind, "seed");
                HiddenKind hidden = HiddenKind.byId(kind);
                said = Solo.t("spawn.gate.hidden", rank.displayName(), hidden == null ? Component.literal(kind) : hidden.title());
            }
            case "breaking" -> {
                g.breakState().start(level.getGameTime(), DungeonBreaks.IMMINENT_TICKS);
                said = Solo.t("spawn.gate.breaking", rank.displayName(), DungeonBreaks.IMMINENT_TICKS / 20);
            }
            default -> said = Solo.t("spawn.gate", rank.displayName(), g.dungeonName(), g.shape().displayName());
        }
        GateData.get(server).setDirty();
        src.sendSuccess(() -> said, false);
        return 1;
    }

    private static int cache(CommandSourceStack src, PlayerRank rank) {
        ServerLevel level = src.getLevel();
        Direction look = Solo.facing(src);
        BlockPos at = Solo.ahead(src, 3);
        level.setBlock(at, ModBlocks.REWARD_CACHE.get().defaultBlockState().setValue(RewardCacheBlock.FACING, look.getOpposite()), 3);
        if (level.getBlockEntity(at) instanceof RewardCacheBlockEntity cache) cache.setup(rank, level.getGameTime());
        src.sendSuccess(() -> Solo.t("spawn.cache", rank.displayName()), false);
        return 1;
    }

    // ================================================================ gates in the world

    private static LiteralArgumentBuilder<CommandSourceStack> gate() {
        return Commands.literal("gate")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("close")
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            GateInstance g = nearest(src, x -> closable(src.getServer(), x));
                            close(src.getServer(), g);
                            src.sendSuccess(() -> Solo.t("gate.closed", g.rank().displayName(), g.gatePos().toShortString()), false);
                            return 1;
                        })
                        .then(Commands.literal("all").executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            int n = 0;
                            for (GateInstance g : List.copyOf(GateData.get(src.getServer()).all())) {
                                if (!closable(src.getServer(), g)) continue;
                                close(src.getServer(), g);
                                n++;
                            }
                            int closed = n;
                            src.sendSuccess(() -> Solo.t("gate.closed_all", closed), false);
                            return n;
                        })))
                .then(Commands.literal("break").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    GateInstance g = nearest(src, DungeonBreaks::canBreak);
                    ServerLevel world = src.getServer().getLevel(g.gateDimension());
                    if (world == null) throw Solo.error("no_gate", Solo.RANGE);
                    DungeonBreaks.breakOpen(world, GateData.get(src.getServer()), g, world.getGameTime());
                    src.sendSuccess(() -> Solo.t("gate.broke", g.rank().displayName(), g.gatePos().toShortString()), false);
                    return 1;
                }));
    }

    /** Every gate still open in the world, nearest first. */
    private static int list(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        Vec3 from = src.getPosition();
        List<GateInstance> open = new ArrayList<>();
        for (GateInstance g : GateData.get(server).all()) {
            if (g.state() != GateInstance.State.CLOSED && !g.gateDimension().equals(GateService.DIMENSION)) open.add(g);
        }
        open.sort(Comparator.comparing((GateInstance g) -> !g.gateDimension().equals(src.getLevel().dimension()))
                .thenComparingDouble(g -> g.gatePos().distToCenterSqr(from)));
        MutableComponent out = Solo.t("gate.list", open.size()).withStyle(ChatFormatting.GOLD);
        for (GateInstance g : open.subList(0, Math.min(20, open.size()))) {
            boolean same = g.gateDimension().equals(src.getLevel().dimension());
            MutableComponent line = Component.literal("\n").append(Solo.t("gate.list.line", g.rank().displayName(), g.dungeonName(), g.gatePos().toShortString(),
                    same ? Solo.t("gate.list.blocks", Solo.blocks(from, g.gatePos())) : Component.literal(g.gateDimension().location().toString())));
            for (Component tag : tags(server, g)) line.append(" ").append(tag);
            out.append(line);
        }
        src.sendSuccess(() -> out, false);
        return open.size();
    }

    private static List<Component> tags(MinecraftServer server, GateInstance g) {
        List<Component> tags = new ArrayList<>();
        if (RedGateData.get(server).get(g.id()) != null || RedGateData.get(server).forced(g.id())) tags.add(tag("red", ChatFormatting.RED));
        if (g.breakState().broken()) tags.add(tag("broken", ChatFormatting.DARK_RED));
        else if (g.breakState().clockStarted() && g.breakState().left(server.overworld().getGameTime()) <= DungeonBreaks.IMMINENT_TICKS) tags.add(tag("breaking", ChatFormatting.GOLD));
        if (g.state() == GateInstance.State.CLEARED) tags.add(tag("cleared", ChatFormatting.GREEN));
        if (g.isKeyed()) tags.add(tag("keyed", ChatFormatting.AQUA));
        if (g.isTrial()) tags.add(tag("sealed", ChatFormatting.YELLOW));
        return tags;
    }

    private static Component tag(String key, ChatFormatting color) {
        return Component.literal("[").append(Solo.t("gate.tag." + key)).append("]").withStyle(color);
    }

    /** A gate a command may close: open, not an awakening trial, and no red gate holding hunters in it. */
    private static boolean closable(MinecraftServer server, GateInstance g) {
        return g.state() != GateInstance.State.CLOSED && !g.isTrial() && !RedGates.holding(g) && !g.gateDimension().equals(GateService.DIMENSION);
    }

    /** Closes a gate for good, the way a cleared one closes: anyone still in its cave is sent home. */
    private static void close(MinecraftServer server, GateInstance g) {
        g.setState(GateInstance.State.CLOSED);
        if (g.isKeyed()) g.keyed().inside.clear();
        GateData.get(server).setDirty();
        ServerLevel world = server.getLevel(g.gateDimension());
        if (world != null && world.isLoaded(g.gatePos()) && world.getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate) {
            GateService.close(world, gate);
        }
    }

    /** The nearest gate to the sender (same world, within {@link Solo#RANGE}) that {@code ok} accepts. */
    private static GateInstance nearest(CommandSourceStack src, java.util.function.Predicate<GateInstance> ok) throws CommandSyntaxException {
        return nearest(src.getServer(), src.getLevel(), src.getPosition(), ok);
    }

    private static GateInstance nearest(MinecraftServer server, ServerLevel level, Vec3 from, java.util.function.Predicate<GateInstance> ok)
            throws CommandSyntaxException {
        GateInstance best = null;
        double bestD = (double) Solo.RANGE * Solo.RANGE;
        for (GateInstance g : GateData.get(server).all()) {
            if (!g.gateDimension().equals(level.dimension()) || !ok.test(g)) continue;
            double d = g.gatePos().distToCenterSqr(from);
            if (d <= bestD) {
                best = g;
                bestD = d;
            }
        }
        if (best == null) throw Solo.error("no_gate", Solo.RANGE);
        return best;
    }

    // ================================================================ dungeons

    private static LiteralArgumentBuilder<CommandSourceStack> dungeon() {
        return Commands.literal("dungeon")
                .then(Solo.target(Commands.literal("enter"), (ctx, p) -> enter(p)))
                .then(Solo.target(Commands.literal("boss"), (ctx, p) -> {
                    GateInstance g = inside(p);
                    toBossDoor(p, g);
                    return Solo.t("dungeon.boss", p.getDisplayName());
                }))
                .then(Solo.target(Commands.literal("clear"), (ctx, p) -> clear(p)))
                .then(Solo.targets(Commands.literal("leave"), (ctx, p) -> leave(p)))
                .then(Solo.target(Commands.literal("info"), (ctx, p) -> {
                    GateInstance g = inside(p);
                    DungeonLayout layout = g.layout();
                    return Component.translatable("command.beyondsolo.gate.info", g.dungeonName(), g.realm().id(), g.shape().displayName(),
                            g.rank().name(), layout.rooms().size(), layout.tunnels().size(), layout.spaces().size(), g.bossKind().name(g.rank()),
                            g.element().displayName(), Long.toString(g.seed()));
                }));
    }

    /** Into the cave of the gate nearest the player (straight in; a red gate in waiting turns red as it would). */
    private static Component enter(ServerPlayer p) throws CommandSyntaxException {
        if (GateService.isDungeon(p.level())) throw Solo.error("already_inside", p.getDisplayName());
        GateInstance g = nearest(p.server, p.serverLevel(), p.position(), x -> x.state() == GateInstance.State.OPEN && !x.breakState().broken()
                && (!x.isTrial() || p.getUUID().equals(x.owner())) && !(x.isKeyed() && x.keyed().doorClosed));
        if (!(p.serverLevel().getBlockEntity(g.gatePos()) instanceof GateBlockEntity gate)) throw Solo.error("no_gate", Solo.RANGE);
        if (RedGates.enter(p, gate, g)) {
            if (!RedGates.occupied(p)) throw Solo.error("red_sealed");
            return Solo.t("dungeon.enter_red", p.getDisplayName());
        }
        if (!GateService.arrive(p, g)) throw Solo.error("no_dungeon_world");
        return Solo.t("dungeon.enter", p.getDisplayName(), g.dungeonName());
    }

    private static GateInstance inside(ServerPlayer p) throws CommandSyntaxException {
        GateInstance g = GateService.isDungeon(p.level()) ? GateData.get(p.server).bySlotPos(p.blockPosition()) : null;
        if (g == null || g.state() == GateInstance.State.CLOSED) throw Solo.error("not_in_dungeon", p.getDisplayName());
        return g;
    }

    /** Just inside the door of the boss's room, looking in. */
    private static void toBossDoor(ServerPlayer p, GateInstance g) {
        BlockPos c = g.bossRoomCenter();
        DungeonLayout.Room room = g.layout().boss();
        Vec3 way = GateService.doorward(g, Vec3.atBottomCenterOf(c));
        double k = Math.max(3, room.reach() - 3);
        float yaw = (float) (Mth.atan2(-way.z, -way.x) * Mth.RAD_TO_DEG) - 90f;
        p.teleportTo(p.serverLevel(), c.getX() + 0.5 + way.x * k, c.getY(), c.getZ() + 0.5 + way.z * k, yaw, 0);
    }

    /**
     * Every creature of the cave around is gone and its boss falls (to the player, so the cave counts as theirs):
     * the way out opens. A boss too far off to be loaded is reached first.
     */
    private static Component clear(ServerPlayer p) throws CommandSyntaxException {
        GateInstance g = inside(p);
        ServerLevel dungeon = p.serverLevel();
        List<Entity> foes = new ArrayList<>();
        for (Entity e : dungeon.getAllEntities()) {
            if (e instanceof Mob && !(e instanceof GateBossEntity) && !(e instanceof ArbiterEntity)
                    && e.getPersistentData().hasUUID(GateWiper.GATE_ID_TAG) && g.id().equals(e.getPersistentData().getUUID(GateWiper.GATE_ID_TAG))) {
                foes.add(e);
            }
        }
        foes.forEach(Entity::discard);
        if (g.bossDefeated()) return Solo.t("dungeon.cleared", p.getDisplayName(), foes.size());
        if (slay(p, g)) return Solo.t("dungeon.cleared_boss", p.getDisplayName(), foes.size());
        // the boss's room is out of reach: take the player there and let it fall once it has loaded
        toBossDoor(p, g);
        retrySlay(p, g.id(), 10);
        return Solo.t("dungeon.clearing", p.getDisplayName(), foes.size());
    }

    private static void retrySlay(ServerPlayer p, UUID gate, int tries) {
        ServerScheduler.schedule(10, () -> {
            GateInstance g = GateData.get(p.server).get(gate);
            if (g == null || g.bossDefeated() || p.hasDisconnected() || !GateService.isDungeon(p.level())) return;
            if (!slay(p, g) && tries > 1) retrySlay(p, gate, tries - 1);
        });
    }

    /** The cave's boss (or the Arbiter of an awakening trial) falls now, if it is loaded. */
    private static boolean slay(ServerPlayer p, GateInstance g) {
        ServerLevel dungeon = GateService.dungeon(p.server);
        if (dungeon == null) return false;
        if (g.isTrial()) {
            ArbiterEntity a = AwakeningService.arbiter(dungeon, g);
            if (a == null) return false;
            if (!a.isAwake()) GateService.wakeBossNow(dungeon, g);
            a.setHealth(1f);
            a.kill();
            return true;
        }
        Entity e = g.boss() == null ? null : dungeon.getEntity(g.boss());
        if (!(e instanceof GateBossEntity boss) || !boss.isAlive()) return false;
        if (!boss.isAwake()) GateService.wakeBossNow(dungeon, g);
        boss.setHealth(1f);
        boss.hurt(dungeon.damageSources().playerAttack(p), 1000f);
        if (boss.isAlive()) boss.kill();
        return true;
    }

    /** Out of wherever the player is held: a dungeon, a red gate, the job change trial, the Citadel or the Penalty Zone. */
    private static Component leave(ServerPlayer p) throws CommandSyntaxException {
        if (JobTrial.cancel(p)) return Solo.t("dungeon.left", p.getDisplayName());
        if (TowerRun.inRun(p) || TowerAPI.data(p).inside()) {
            TowerRun.cancel(p);
            return Solo.t("dungeon.left", p.getDisplayName());
        }
        if (PenaltyZone.inZone(p) || PenaltyZone.data(p).pending()) {
            PenaltyZone.pardon(p);
            return Solo.t("dungeon.left", p.getDisplayName());
        }
        if (RedGates.leaveNow(p)) return Solo.t("dungeon.left", p.getDisplayName());
        GateInstance g = GateService.isDungeon(p.level()) ? GateData.get(p.server).bySlotPos(p.blockPosition()) : null;
        if (g == null) throw Solo.error("not_held", p.getDisplayName());
        GateService.leave(p, g);
        return Solo.t("dungeon.left", p.getDisplayName());
    }

    // ================================================================ trials

    private static LiteralArgumentBuilder<CommandSourceStack> trial() {
        return Commands.literal("trial")
                .then(Solo.targets(Commands.literal("awakening"), (ctx, p) -> {
                    if (SystemAPI.hasSystem(p)) throw Solo.error("has_system", p.getDisplayName());
                    AwakeningService.reset(p);
                    if (!AwakeningService.call(p)) throw Solo.error("trial_not_here");
                    return Solo.t("trial.awakening", p.getDisplayName());
                }))
                .then(Solo.target(Commands.literal("job"), (ctx, p) -> {
                    Solo.needEvaluated(p);
                    if (JobTrial.inTrial(p)) throw Solo.error("busy", p.getDisplayName());
                    JobAPI.setRetryAt(p, 0);
                    if (!JobAPI.get(p).unlocked()) JobAPI.unlock(p);
                    if (!JobTrial.begin(p, true)) throw Solo.error("busy", p.getDisplayName());
                    return Solo.t("trial.job", p.getDisplayName());
                }))
                .then(Solo.target(Commands.literal("penalty"), (ctx, p) -> {
                    if (!SystemAPI.hasSystem(p)) throw Solo.error("no_system", p.getDisplayName());
                    if (!PenaltyZone.sendNow(p, Component.translatable("quest.beyondsolo.daily"))) throw Solo.error("busy", p.getDisplayName());
                    return Solo.t("trial.penalty", p.getDisplayName());
                }));
    }

    // ================================================================ the Crimson Citadel

    private static LiteralArgumentBuilder<CommandSourceStack> tower() {
        // the floor goes in before the player, so a number is read as a floor, never as a player's name
        LiteralArgumentBuilder<CommandSourceStack> climb = Commands.literal("climb");
        climb.then(Solo.target(Commands.argument("floor", IntegerArgumentType.integer(1, TowerFloors.TOP)),
                (ctx, p) -> climb(p, TowerRun.Mode.CASTLE, IntegerArgumentType.getInteger(ctx, "floor"), null)));
        Solo.target(climb, (ctx, p) -> climb(p, TowerRun.Mode.CASTLE, -1, null));
        return Commands.literal("tower")
                .then(climb)
                .then(Commands.literal("rush").then(Solo.target(Solo.word("tier", () -> Arrays.stream(RushTier.values()).map(RushTier::id)), (ctx, p) -> {
                    String id = str(ctx, "tier");
                    RushTier tier = RushTier.byId(id);
                    if (tier == null) throw Solo.error("unknown_value", id);
                    return climb(p, TowerRun.Mode.RUSH, 0, tier);
                })))
                .then(Solo.target(Commands.literal("endless"), (ctx, p) -> climb(p, TowerRun.Mode.ENDLESS, TowerFloors.SPIRE, null)));
    }

    /** Into the Citadel at once, its key bound first if it isn't (floor -1: where the player would start). */
    private static Component climb(ServerPlayer p, TowerRun.Mode mode, int floor, @Nullable RushTier tier) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        if (TowerRun.inRun(p)) throw Solo.error("busy", p.getDisplayName());
        TowerData d = TowerAPI.data(p);
        if (!d.bound) {
            d.bound = true;
            d.told = true;
            TowerAPI.sync(p);
        }
        int start = floor < 0 ? d.startFloor() : floor;
        if (!TowerRun.begin(p, mode, start, tier)) throw Solo.error("no_citadel");
        return switch (mode) {
            case RUSH -> Solo.t("tower.rush", p.getDisplayName(), tier.title());
            case ENDLESS -> Solo.t("tower.endless", p.getDisplayName());
            default -> Solo.t("tower.climb", p.getDisplayName(), Math.max(1, start));
        };
    }

    private SoloWorld() {}
}
