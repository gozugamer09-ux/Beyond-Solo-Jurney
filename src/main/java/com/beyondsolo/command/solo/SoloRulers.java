package com.beyondsolo.command.solo;

import com.beyondsolo.ruler.RulerBoss;
import com.beyondsolo.ruler.RulerConfig;
import com.beyondsolo.ruler.RulerModule;
import com.beyondsolo.ruler.RulerRecord;
import com.beyondsolo.ruler.Rulers;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.MonarchBoss;
import com.beyondsolo.worldboss.WorldBossData;
import com.beyondsolo.worldboss.WorldBossKind;
import com.beyondsolo.worldboss.WorldBosses;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * The rulers of light: /solo ruler call (one comes down where you look), war (one comes to make war on the monarch
 * abroad), sentence (one comes to judge a hunter), land (the one coming down lands now), stop, status, next (the next
 * Sentence of Light in that many minutes), bless (the Blessing of Light now), mote (Motes of Radiance), record, and for
 * the ruler that has landed: phase (its phase now), move (one of its moves, aimed at the sender) and kill.
 */
final class SoloRulers {
    /** The rulers, by name ("verdict"), and "random". */
    static final List<String> KINDS = java.util.stream.Stream.concat(Arrays.stream(WorldBossKind.rulers()).map(k -> k.id),
            java.util.stream.Stream.of("random")).toList();

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("ruler")
                .then(Commands.literal("call")
                        .executes(ctx -> call(ctx, "random"))
                        .then(Solo.word("ruler", KINDS::stream).executes(ctx -> call(ctx, StringArgumentType.getString(ctx, "ruler")))))
                .then(Commands.literal("war")
                        .executes(ctx -> war(ctx, "random"))
                        .then(Solo.word("ruler", KINDS::stream).executes(ctx -> war(ctx, StringArgumentType.getString(ctx, "ruler")))))
                .then(Commands.literal("sentence")
                        .then(Solo.word("ruler", KINDS::stream).then(Solo.target(Commands.literal("on"), (ctx, p) -> {
                            WorldBossKind kind = kind(StringArgumentType.getString(ctx, "ruler"));
                            return sentence(ctx, p, kind);
                        })))
                        .then(Solo.target(Commands.literal("on"), (ctx, p) -> sentence(ctx, p, null))))
                .then(Commands.literal("land").executes(ctx -> {
                    ServerLevel level = ctx.getSource().getServer().overworld();
                    WorldBossData.Event ev = WorldBossData.get(level.getServer()).ruler();
                    if (ev == null || !ev.descending()) throw Solo.error("no_ruler");
                    ev.setLandAt(level.getGameTime());
                    ctx.getSource().sendSuccess(() -> Solo.t("ruler.landing", ev.kind.fullName()), false);
                    return 1;
                }))
                .then(Commands.literal("stop").executes(ctx -> {
                    if (!Rulers.stop(ctx.getSource().getServer().overworld())) throw Solo.error("no_ruler");
                    ctx.getSource().sendSuccess(() -> Solo.t("ruler.stopped"), true);
                    return 1;
                }))
                .then(Commands.literal("status").executes(SoloRulers::status))
                .then(Commands.literal("phase").then(Commands.argument("n", IntegerArgumentType.integer(1, 3)).executes(ctx -> {
                    RulerBoss boss = landed(ctx);
                    int n = IntegerArgumentType.getInteger(ctx, "n");
                    boss.forcePhase((ServerLevel) boss.level(), n);
                    ctx.getSource().sendSuccess(() -> Solo.t("ruler.phase", boss.kind().fullName(), n), true);
                    return 1;
                })))
                .then(Commands.literal("move").then(Commands.argument("move", StringArgumentType.word())
                        .suggests((c, b) -> {
                            RulerBoss boss = Rulers.current(c.getSource().getServer().overworld());
                            return SharedSuggestionProvider.suggest(boss == null ? List.of() : boss.moveNames(), b);
                        })
                        .executes(ctx -> {
                            RulerBoss boss = landed(ctx);
                            String name = StringArgumentType.getString(ctx, "move");
                            ColossalBoss.Move m = boss.moveNamed(Solo.lower(name));
                            if (m == null) throw Solo.error("unknown_ruler_move", name, String.join(", ", boss.moveNames()));
                            // aimed at whoever sent it: its blows still land only on its foes
                            LivingEntity at = ctx.getSource().getEntity() instanceof LivingEntity e ? e : boss.getTarget();
                            if (at != null) boss.setTarget(at);
                            boss.start(m, at);
                            ctx.getSource().sendSuccess(() -> Solo.t("ruler.move", boss.kind().fullName(), m.anim()), false);
                            return 1;
                        })))
                .then(Commands.literal("kill").executes(ctx -> {
                    RulerBoss boss = landed(ctx);
                    boss.kill();
                    ctx.getSource().sendSuccess(() -> Solo.t("ruler.killed", boss.kind().fullName()), true);
                    return 1;
                }))
                .then(Commands.literal("next").then(Commands.argument("minutes", IntegerArgumentType.integer(0, 100000)).executes(ctx -> {
                    ServerLevel level = ctx.getSource().getServer().overworld();
                    int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                    WorldBossData.get(level.getServer()).setNextSentenceAt(level.getGameTime() + minutes * 1200L);
                    ctx.getSource().sendSuccess(() -> Solo.t("ruler.next", minutes), true);
                    return 1;
                })))
                .then(Solo.targets(Commands.literal("bless"), (ctx, p) -> {
                    if (!Rulers.bless(p, RulerConfig.blessingTicks(), true)) throw Solo.error("crowned_unblessed", p.getDisplayName());
                    return Solo.t("ruler.blessed", p.getDisplayName(), RulerConfig.blessingTicks() / 1200);
                }))
                .then(Commands.literal("mote").executes(ctx -> mote(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(ctx -> mote(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Solo.target(Commands.literal("record"), (ctx, p) -> record(p))));
    }

    /** A ruler (by name, or one of them for "random"). */
    @Nullable
    static WorldBossKind kind(String name) throws CommandSyntaxException {
        String n = Solo.lower(name);
        if (n.equals("random")) return null;
        WorldBossKind k = WorldBossKind.byId(n);
        if (k == null || !k.ruler()) throw Solo.error("unknown_ruler", name);
        return k;
    }

    /** The ruler abroad, landed and alive (for phase, move and kill). */
    private static RulerBoss landed(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        RulerBoss boss = Rulers.current(ctx.getSource().getServer().overworld());
        if (boss == null || !boss.isAlive() || !boss.landed()) throw Solo.error("no_ruler_landed");
        return boss;
    }

    private static WorldBossKind orRandom(@Nullable WorldBossKind kind, ServerLevel level) {
        return kind != null ? kind : Rulers.pick(level.random);
    }

    /** A ruler called down 16 blocks ahead of the sender, for no one: it fights whoever stands against it. */
    private static int call(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (p.level().dimension() != Level.OVERWORLD) throw Solo.error("not_overworld");
        ServerLevel level = (ServerLevel) p.level();
        if (WorldBossData.get(level.getServer()).ruler() != null) throw Solo.error("ruler_abroad");
        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(16));
        int x = Mth.floor(at.x), z = Mth.floor(at.z);
        BlockPos spot = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        WorldBossKind kind = orRandom(kind(name), level);
        if (Rulers.begin(level, kind, spot, Rulers.CALLED, null) == null) throw Solo.error("ruler_abroad");
        ctx.getSource().sendSuccess(() -> Solo.t("ruler.called", kind.fullName(), spot.toShortString()), true);
        return 1;
    }

    /** A ruler comes to make war on the monarch abroad. */
    private static int war(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getServer().overworld();
        ColossalBoss boss = WorldBosses.currentBoss(level);
        if (!(boss instanceof MonarchBoss monarch) || !monarch.isAlive()) throw Solo.error("no_monarch_abroad");
        if (WorldBossData.get(level.getServer()).ruler() != null) throw Solo.error("ruler_abroad");
        WorldBossKind kind = orRandom(kind(name), level);
        if (Rulers.war(level, monarch, kind) == null) throw Solo.error("ruler_abroad");
        ctx.getSource().sendSuccess(() -> Solo.t("ruler.war", kind.fullName(), monarch.kind().fullName()), true);
        return 1;
    }

    /** A ruler comes to judge a hunter (the sentence is carried out if they die before it falls). */
    private static Component sentence(CommandContext<CommandSourceStack> ctx, ServerPlayer p, @Nullable WorldBossKind kind) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        if (p.level().dimension() != Level.OVERWORLD) throw Solo.error("not_overworld");
        ServerLevel level = (ServerLevel) p.level();
        if (WorldBossData.get(level.getServer()).ruler() != null) throw Solo.error("ruler_abroad");
        WorldBossKind k = orRandom(kind, level);
        if (Rulers.sentence(level, p, k) == null) throw Solo.error("ruler_abroad");
        return Solo.t("ruler.sentenced", k.fullName(), p.getDisplayName());
    }

    private static int mote(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        WorldBosses.give(p, new ItemStack(RulerModule.MOTE.get(), count));
        ctx.getSource().sendSuccess(() -> Solo.t("ruler.motes", count, p.getDisplayName()), false);
        return 1;
    }

    private static Component record(ServerPlayer p) {
        RulerRecord r = p.getData(RulerModule.RECORD.get());
        Component felled = Component.literal(Arrays.stream(WorldBossKind.rulers()).map(k -> k.id + " " + r.slain(k)).reduce((a, b) -> a + ", " + b).orElse(""));
        return Solo.t("ruler.record", p.getDisplayName(), r.rulers(), felled, r.top(), r.alliedWars(), r.sentences(), r.overturned(), r.heralds(),
                r.blessings(), Rulers.blessed(p) ? Solo.t("ruler.record.blessed") : Component.empty());
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        WorldBossData data = WorldBossData.get(level.getServer());
        WorldBossData.Event ev = data.ruler();
        long now = level.getGameTime();
        Component next = data.nextSentenceAt() < 0 ? Solo.t("ruler.status.unplanned")
                : Solo.t("ruler.status.in", Math.max(0, (data.nextSentenceAt() - now) / 20));
        if (ev == null) {
            ctx.getSource().sendSuccess(() -> Solo.t("ruler.status.none", next, data.rulerKills()), false);
            return 1;
        }
        String why = switch (ev.reason()) {
            case Rulers.WAR -> "war";
            case Rulers.SENTENCE -> "sentence";
            default -> "called";
        };
        RulerBoss boss = Rulers.current(level);
        Component state = ev.descending() ? Solo.t("ruler.status.landing", Math.max(0, (ev.landAt() - now) / 20))
                : boss != null ? Solo.t("ruler.status.health", Math.round(boss.getHealth()), Math.round(boss.getMaxHealth()), boss.phase())
                : Solo.t("ruler.status.unloaded");
        ctx.getSource().sendSuccess(() -> Solo.t("ruler.status", ev.kind.fullName(), Solo.t("ruler.reason." + why), ev.last().toShortString(), state, next),
                false);
        return 1;
    }

    private SoloRulers() {}
}
