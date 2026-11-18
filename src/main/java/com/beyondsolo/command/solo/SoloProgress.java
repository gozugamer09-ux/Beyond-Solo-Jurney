package com.beyondsolo.command.solo;

import com.beyondsolo.awakening.AwakeningData;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.combat.Buildups;
import com.beyondsolo.depths.WeaknessLog;
import com.beyondsolo.economy.Economy;
import com.beyondsolo.evaluation.EvaluationManager;
import com.beyondsolo.evaluation.Reevaluation;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.job.JobTrial;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.key.DungeonKeys;
import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.monarch.MonarchData;
import com.beyondsolo.penalty.PenaltyZone;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.rune.Runes;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.spawn.FirstSpawnHandler;
import com.beyondsolo.system.Exhaustion;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Progression;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.tower.TowerAPI;
import com.beyondsolo.tower.TowerData;
import com.beyondsolo.tower.TowerRun;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The player's System: /solo info, awaken, evaluate, bind, set, add, heal and reset. Each takes optional players at
 * the end (the sender when left out).
 */
final class SoloProgress {
    /** What /solo reset can reset, in the order the help lists them. */
    static final List<String> PARTS = List.of("quests", "skills", "cooldowns", "stats", "job", "shadows", "gold", "economy", "keys",
            "runes", "tower", "penalty", "reeval", "weaknesses");

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Solo.target(Commands.literal("info"), (ctx, p) -> info(p)));
        root.then(rankAndClass(Commands.literal("awaken"), false));
        root.then(rankAndClass(Commands.literal("evaluate"), true));
        root.then(Solo.targets(Commands.literal("bind"), (ctx, p) -> bind(p)));
        root.then(set());
        root.then(add());
        root.then(Solo.targets(Commands.literal("heal"), (ctx, p) -> heal(p)));
        LiteralArgumentBuilder<CommandSourceStack> reset = Commands.literal("reset");
        for (String part : PARTS) reset.then(Solo.targets(Commands.literal(part), (ctx, p) -> reset(p, part)));
        root.then(reset);
    }

    /**
     * {@code <node> [rank] [class] [players]}: rank and class are words of their own (literals), so a player's name in
     * their place is never read as a rank. {@code evaluate} plays the crystal's whole ceremony for one player.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> rankAndClass(LiteralArgumentBuilder<CommandSourceStack> node, boolean ceremony) {
        addRankAndClass(node, ceremony, null, null);
        for (String r : Solo.RANKS) {
            LiteralArgumentBuilder<CommandSourceStack> rank = Commands.literal(r);
            addRankAndClass(rank, ceremony, r, null);
            for (String c : Solo.CLASSES) {
                LiteralArgumentBuilder<CommandSourceStack> cls = Commands.literal(c);
                addRankAndClass(cls, ceremony, r, c);
                rank.then(cls);
            }
            node.then(rank);
        }
        return node;
    }

    private static void addRankAndClass(LiteralArgumentBuilder<CommandSourceStack> node, boolean ceremony, @Nullable String rank, @Nullable String cls) {
        Solo.Action action = (ctx, p) -> {
            PlayerRank r = rank == null || ceremony && rank.equals("random") ? null : Solo.rank(rank, ctx.getSource());
            PlayerClass c = cls == null || ceremony && cls.equals("random") ? null : Solo.playerClass(cls, ctx.getSource());
            return ceremony ? evaluate(p, r, c) : awaken(p, r == null ? Solo.rank("random", ctx.getSource()) : r,
                    c == null ? Solo.playerClass("random", ctx.getSource()) : c);
        };
        if (ceremony) Solo.target(node, action);
        else Solo.targets(node, action);
    }

    // ---------------------------------------------------------------- info

    private static Component info(ServerPlayer p) {
        SystemData d = SystemAPI.get(p);
        MutableComponent out = Solo.t("info.header", p.getDisplayName()).withStyle(ChatFormatting.GOLD);
        if (!d.hasSystem()) {
            return out.append("\n").append(Solo.t("info.no_system", Solo.lower(AwakeningService.stage(p).name())));
        }
        if (!d.isAwakened()) return out.append("\n").append(Solo.t("info.unevaluated"));
        Subclass sub = JobAPI.subclass(p);
        out.append("\n").append(Solo.t("info.level", d.level(), d.xp(), d.xpToNextLevel(), d.rank().displayName(), d.playerClass().displayName(),
                sub == null ? Solo.t("info.none") : sub.displayName()));
        MutableComponent stats = Component.empty();
        for (Stat s : Stat.values()) {
            if (s.ordinal() > 0) stats.append(" · ");
            stats.append(s.displayName()).append(" " + d.stat(s));
        }
        out.append("\n").append(stats).append(" · ").append(Solo.t("info.points", d.statPoints()));
        out.append("\n").append(Solo.t("info.state", Math.round(d.mana()), Math.round(d.maxMana()), Math.round(d.exhaustion()), Economy.gold(p)));
        QuestData q = QuestEngine.data(p);
        TowerData t = TowerAPI.data(p);
        out.append("\n").append(Solo.t("info.counts", Skills.known(SkillAPI.get(p)).size(), ShadowService.army(p).size(),
                q.active().size(), q.completedCount(), t.highest));
        MonarchData m = MonarchAPI.get(p);
        if (m.dominion() != null) {
            out.append("\n").append(Solo.t("info.monarch", m.dominion().title(), m.monarchsSlain(), m.clashes(), m.authorities()));
        } else if (m.called()) {
            out.append("\n").append(Solo.t("info.monarch_called", m.attempts()));
        }
        return out;
    }

    // ---------------------------------------------------------------- the System itself

    private static Component awaken(ServerPlayer p, PlayerRank rank, PlayerClass cls) throws CommandSyntaxException {
        if (EvaluationManager.isBeingEvaluated(p)) throw Solo.error("evaluating", p.getDisplayName());
        markBound(p);
        Subclass sub = JobAPI.subclass(p);
        if (sub != null && sub.base() != cls) JobAPI.reset(p);
        SystemAPI.awaken(p, rank, cls);
        return Solo.t("awaken.done", p.getDisplayName(), rank.displayName(), cls.displayName());
    }

    /** The whole evaluation at the nearest crystal (or a spot in front of the player), with a forced result if given. */
    private static Component evaluate(ServerPlayer p, @Nullable PlayerRank rank, @Nullable PlayerClass cls) throws CommandSyntaxException {
        if (EvaluationManager.isBeingEvaluated(p)) throw Solo.error("evaluating", p.getDisplayName());
        markBound(p);
        SystemAPI.reset(p);
        QuestEngine.reset(p);
        // the reset took the System with it: the crystal only answers those it is bound to
        SystemAPI.grantSystem(p);
        EvaluationManager.begin(p, Solo.nearestCrystal(p), rank, cls);
        return Solo.t("evaluate.done", p.getDisplayName());
    }

    private static Component bind(ServerPlayer p) throws CommandSyntaxException {
        if (SystemAPI.hasSystem(p)) throw Solo.error("has_system", p.getDisplayName());
        AwakeningService.reset(p);
        AwakeningService.data(p).setStage(AwakeningData.Stage.DONE);
        SystemAPI.grantSystem(p);
        FirstSpawnHandler.systemIntro(p, 20);
        return Solo.t("bind.done", p.getDisplayName());
    }

    /** The awakening trial counts as done (the System is bound for good), without its sealed gate left standing. */
    private static void markBound(ServerPlayer p) {
        if (AwakeningService.stage(p) == AwakeningData.Stage.DONE) return;
        AwakeningService.reset(p);
        AwakeningService.data(p).setStage(AwakeningData.Stage.DONE);
    }

    // ---------------------------------------------------------------- set and add

    private static LiteralArgumentBuilder<CommandSourceStack> set() {
        return Commands.literal("set")
                .then(Commands.literal("level").then(Solo.targets(Commands.argument("level", IntegerArgumentType.integer(1, Progression.MAX_LEVEL)),
                        (ctx, p) -> level(p, IntegerArgumentType.getInteger(ctx, "level")))))
                .then(Commands.literal("rank").then(Solo.targets(Solo.rankArg(), (ctx, p) -> {
                    Solo.needEvaluated(p);
                    PlayerRank r = Solo.rank(ctx);
                    SystemAPI.setRank(p, r);
                    return Solo.t("set.rank", p.getDisplayName(), r.displayName());
                })))
                .then(Commands.literal("class").then(Solo.targets(Solo.classArg(), (ctx, p) ->
                        setClass(p, Solo.playerClass(StringArgumentType.getString(ctx, "class"), ctx.getSource())))))
                .then(Commands.literal("subclass").then(Solo.targets(Solo.word("subclass",
                                () -> Stream.concat(Stream.of("none"), Arrays.stream(Subclass.values()).map(Subclass::key))),
                        (ctx, p) -> setSubclass(p, StringArgumentType.getString(ctx, "subclass")))))
                .then(Commands.literal("stat").then(Solo.statArg().then(Solo.targets(Commands.argument("value", IntegerArgumentType.integer(Stat.BASE, 100_000)),
                        (ctx, p) -> stat(p, Solo.stat(ctx), IntegerArgumentType.getInteger(ctx, "value"))))))
                .then(Commands.literal("points").then(Solo.targets(Commands.argument("points", IntegerArgumentType.integer(0, 100_000)),
                        (ctx, p) -> points(p, IntegerArgumentType.getInteger(ctx, "points") - SystemAPI.get(p).statPoints()))))
                .then(Commands.literal("gold").then(Solo.targets(Commands.argument("gold", LongArgumentType.longArg(0)), (ctx, p) -> {
                    Economy.set(p, LongArgumentType.getLong(ctx, "gold"));
                    Economy.sync(p);
                    return Solo.t("set.gold", p.getDisplayName(), Economy.gold(p));
                })))
                .then(Commands.literal("exhaustion").then(Solo.targets(Commands.argument("percent", FloatArgumentType.floatArg(0, 100)), (ctx, p) -> {
                    Solo.needEvaluated(p);
                    float target = FloatArgumentType.getFloat(ctx, "percent");
                    Exhaustion.recover(p, Math.min(target, 99.9f));
                    if (target >= 100) Exhaustion.collapse(p);
                    return Solo.t("set.exhaustion", p.getDisplayName(), Math.round(target));
                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> add() {
        return Commands.literal("add")
                .then(Commands.literal("xp").then(Solo.targets(Commands.argument("xp", LongArgumentType.longArg(1)), (ctx, p) -> {
                    Solo.needEvaluated(p);
                    long xp = LongArgumentType.getLong(ctx, "xp");
                    SystemAPI.grantXp(p, xp, SystemAPI.XP_SOURCE_COMMAND);
                    return Solo.t("add.xp", p.getDisplayName(), xp, SystemAPI.get(p).level());
                })))
                .then(Commands.literal("levels").then(Solo.targets(Commands.argument("levels", IntegerArgumentType.integer(-Progression.MAX_LEVEL, Progression.MAX_LEVEL)),
                        (ctx, p) -> level(p, SystemAPI.get(p).level() + IntegerArgumentType.getInteger(ctx, "levels")))))
                .then(Commands.literal("points").then(Solo.targets(Commands.argument("points", IntegerArgumentType.integer(-100_000, 100_000)),
                        (ctx, p) -> points(p, IntegerArgumentType.getInteger(ctx, "points")))))
                .then(Commands.literal("gold").then(Solo.targets(Commands.argument("gold", LongArgumentType.longArg()), (ctx, p) -> {
                    Economy.add(p, LongArgumentType.getLong(ctx, "gold"), Economy.Reason.COMMAND);
                    Economy.sync(p);
                    return Solo.t("set.gold", p.getDisplayName(), Economy.gold(p));
                })))
                .then(Commands.literal("stat").then(Solo.statArg().then(Solo.targets(Commands.argument("amount", IntegerArgumentType.integer(-100_000, 100_000)),
                        (ctx, p) -> {
                            Stat s = Solo.stat(ctx);
                            return stat(p, s, SystemAPI.get(p).stat(s) + IntegerArgumentType.getInteger(ctx, "amount"));
                        }))));
    }

    private static Component level(ServerPlayer p, int level) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        SystemAPI.setLevel(p, level);
        return Solo.t("set.level", p.getDisplayName(), SystemAPI.get(p).level());
    }

    private static Component stat(ServerPlayer p, Stat stat, int value) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        SystemAPI.setStat(p, stat, Math.max(Stat.BASE, value));
        return Solo.t("set.stat", p.getDisplayName(), stat.displayName(), SystemAPI.get(p).stat(stat));
    }

    private static Component points(ServerPlayer p, int change) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        SystemAPI.addStatPoints(p, change);
        return Solo.t("set.points", p.getDisplayName(), SystemAPI.get(p).statPoints());
    }

    /** A new class: a subclass of the old one goes, and the skills follow the class. Stats stay. */
    private static Component setClass(ServerPlayer p, PlayerClass cls) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        switchClass(p, cls);
        return Solo.t("set.class", p.getDisplayName(), cls.displayName());
    }

    private static void switchClass(ServerPlayer p, PlayerClass cls) {
        Subclass sub = JobAPI.subclass(p);
        if (sub != null && sub.base() != cls) JobAPI.reset(p);
        SystemAPI.setClass(p, cls);
        SkillAPI.refreshForClass(p);
    }

    /** The job change straight to a subclass (its base class too, if the player is another class), or back to none. */
    private static Component setSubclass(ServerPlayer p, String name) throws CommandSyntaxException {
        Solo.needEvaluated(p);
        if (name.equalsIgnoreCase("none")) {
            JobTrial.cancel(p);
            JobAPI.reset(p);
            return Solo.t("set.subclass.none", p.getDisplayName());
        }
        Subclass sub = Subclass.byName(name);
        if (sub == null) throw Solo.error("unknown_subclass", name);
        if (JobTrial.inTrial(p)) JobTrial.cancel(p);
        if (SystemAPI.get(p).playerClass() != sub.base()) switchClass(p, sub.base());
        JobAPI.changeJob(p, sub);
        return Solo.t("set.subclass", p.getDisplayName(), sub.displayName(), sub.base().displayName());
    }

    // ---------------------------------------------------------------- heal

    /** Everything full and nothing wearing them down: health, food, air, mana, exhaustion, cooldowns, ailments. */
    private static Component heal(ServerPlayer p) {
        for (MobEffectInstance e : List.copyOf(p.getActiveEffects())) {
            if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) p.removeEffect(e.getEffect());
        }
        for (var type : Buildups.all()) Buildups.clear(p, type);
        p.clearFire();
        p.setTicksFrozen(0);
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(20f);
        p.setAirSupply(p.getMaxAirSupply());
        SystemData d = SystemAPI.get(p);
        if (d.hasSystem()) {
            if (d.isCollapsed() || d.exhaustion() > 0) Exhaustion.recover(p, 0);
            SystemAPI.restoreMana(p, d.maxMana());
            SkillAPI.resetCooldowns(p);
        }
        return Solo.t("heal.done", p.getDisplayName());
    }

    // ---------------------------------------------------------------- reset

    private static Component reset(ServerPlayer p, String part) throws CommandSyntaxException {
        switch (part) {
            case "quests" -> QuestEngine.reset(p);
            case "skills" -> {
                SkillAPI.reset(p);
                int back = SkillAPI.grantUnlocked(p).size();
                if (JobAPI.subclass(p) != null) back += SkillAPI.grantAdvanced(p).size() + SkillAPI.grantSubclass(p).size();
                return Solo.t("reset.skills", p.getDisplayName(), back);
            }
            case "cooldowns" -> SkillAPI.resetCooldowns(p);
            case "stats" -> {
                Solo.needEvaluated(p);
                int back = SystemAPI.refundStats(p);
                return Solo.t("reset.stats", p.getDisplayName(), Stat.BASE, back);
            }
            case "job" -> {
                JobTrial.cancel(p);
                JobAPI.reset(p);
            }
            case "shadows" -> ShadowService.reset(p);
            case "gold" -> {
                Economy.set(p, 0);
                Economy.sync(p);
            }
            case "economy" -> Economy.reset(p);
            case "keys" -> {
                DungeonKeys.data(p).reset();
                DungeonKeys.sync(p);
            }
            case "runes" -> Runes.reset(p);
            case "tower" -> {
                if (TowerRun.inRun(p)) TowerRun.cancel(p);
                TowerAPI.data(p).reset();
                TowerAPI.sync(p);
            }
            case "penalty" -> PenaltyZone.reset(p);
            case "reeval" -> Reevaluation.reset(p);
            case "weaknesses" -> {
                WeaknessLog.of(p).clear();
                WeaknessLog.sync(p);
            }
            default -> throw Solo.error("unknown_part", part);
        }
        return Solo.t("reset.done", p.getDisplayName(), Solo.t("part." + part.toLowerCase(Locale.ROOT)));
    }

    private SoloProgress() {}
}
