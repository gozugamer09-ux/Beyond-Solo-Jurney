package com.beyondsolo.command.solo;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.job.JobAPI;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.QuestTemplate;
import com.beyondsolo.quest.QuestTemplates;
import com.beyondsolo.quest.QuestTestAccess;
import com.beyondsolo.registry.ModEntities;
import com.beyondsolo.shadow.ShadowRank;
import com.beyondsolo.shadow.ShadowRecord;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillAPI;
import com.beyondsolo.skill.SkillEvent;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import com.beyondsolo.tower.TowerModule;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** /solo skill, quest and shadow: what the player knows, what they are doing, and who follows them. */
final class SoloSkills {
    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(skill());
        root.then(quest());
        root.then(shadow());
    }

    // ---------------------------------------------------------------- skills

    private static LiteralArgumentBuilder<CommandSourceStack> skill() {
        return Commands.literal("skill")
                .then(Commands.literal("learn")
                        .then(withLevel(Commands.literal("all"), true))
                        .then(withLevel(skillArg(), false)))
                .then(Commands.literal("forget")
                        .then(Solo.targets(Commands.literal("all"), (ctx, p) -> {
                            int n = Skills.known(SkillAPI.get(p)).size();
                            SkillAPI.reset(p);
                            return Solo.t("skill.forgot_all", p.getDisplayName(), n);
                        }))
                        .then(Solo.targets(skillArg(), (ctx, p) -> {
                            Skill s = skill(ctx);
                            if (!SkillAPI.knows(p, s)) throw Solo.error("skill_unknown_to", p.getDisplayName(), s.displayName());
                            SkillAPI.forget(p, s);
                            return Solo.t("skill.forgot", p.getDisplayName(), s.displayName());
                        })))
                .then(Solo.targets(Commands.literal("max"), (ctx, p) -> {
                    int n = 0;
                    for (Skill s : Skills.known(SkillAPI.get(p))) if (SkillAPI.addLevels(p, s, Skill.MAX_LEVEL) > 0) n++;
                    return Solo.t("skill.maxed", p.getDisplayName(), n, Skill.MAX_LEVEL);
                }));
    }

    /**
     * {@code <node> [level] [players]}: learns the skill (or every skill that fits) at a level, or raises it to it.
     * The level goes in before the players, so a number is read as a level, never as a player's name.
     */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withLevel(T node, boolean all) {
        node.then(Solo.targets(Commands.argument("level", IntegerArgumentType.integer(1, Skill.MAX_LEVEL)),
                (ctx, p) -> learn(ctx, p, all, IntegerArgumentType.getInteger(ctx, "level"))));
        return Solo.targets(node, (ctx, p) -> learn(ctx, p, all, 1));
    }

    private static Component learn(CommandContext<CommandSourceStack> ctx, ServerPlayer p, boolean all, int level) throws CommandSyntaxException {
        if (!all) {
            Skill s = skill(ctx);
            teach(p, s, level);
            return Solo.t("skill.learned", p.getDisplayName(), s.displayName(), SkillAPI.level(p, s));
        }
        // every skill the player's class and subclass may hold: the class's, the subclass's and everyone's
        SystemData d = SystemAPI.get(p);
        int n = 0;
        for (Skill s : Skills.all()) {
            // the monarch powers come with a crown (/solo monarch crown), not with "every skill"
            if (s.monarch() || !s.fits(d.playerClass(), JobAPI.subclass(p))) continue;
            teach(p, s, level);
            n++;
        }
        return Solo.t("skill.learned_all", p.getDisplayName(), n, level);
    }

    private static void teach(ServerPlayer p, Skill s, int level) {
        if (!SkillAPI.learn(p, s, level, SkillEvent.Source.COMMAND) && SkillAPI.level(p, s) < level) {
            SkillAPI.addLevels(p, s, level - SkillAPI.level(p, s));
        }
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> skillArg() {
        return Solo.word("skill", () -> Skills.all().stream().map(Skill::path));
    }

    static Skill skill(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String path = StringArgumentType.getString(ctx, "skill");
        Skill s = Skills.get(path);
        if (s == null) throw Solo.error("unknown_skill", path);
        return s;
    }

    // ---------------------------------------------------------------- quests

    private static LiteralArgumentBuilder<CommandSourceStack> quest() {
        return Commands.literal("quest")
                .then(Solo.target(Commands.literal("list"), (ctx, p) -> list(p)))
                .then(Commands.literal("start").then(Solo.targets(questArg(), (ctx, p) -> {
                    String id = questId(ctx);
                    Quest q = QuestTemplates.create(id, p, QuestTemplate.Context.at(p.blockPosition()));
                    if (q == null) throw Solo.error("quest_cannot_start", id);
                    QuestEngine.start(p, q);
                    return Solo.t("quest.started", p.getDisplayName(), q.title());
                })))
                .then(Commands.literal("complete")
                        .then(Solo.targets(Commands.literal("all"), (ctx, p) -> complete(p, null)))
                        .then(Solo.targets(questArg(), (ctx, p) -> complete(p, questId(ctx)))))
                .then(Commands.literal("fail")
                        .then(Solo.targets(Commands.literal("all"), (ctx, p) -> fail(p, null)))
                        .then(Solo.targets(questArg(), (ctx, p) -> fail(p, questId(ctx)))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> questArg() {
        return Commands.argument("quest", ResourceLocationArgument.id())
                .suggests((c, b) -> Solo.suggestIds(QuestTemplates.ids().stream().map(ResourceLocation::tryParse).filter(java.util.Objects::nonNull), b));
    }

    /** The quest named, with the mod's namespace when none was given ("set/provisions"). */
    private static String questId(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "quest");
        String full = id.toString();
        if (QuestTemplates.get(full) != null) return full;
        String ours = BeyondSolo.id(id.getPath()).toString();
        if (QuestTemplates.get(ours) != null) return ours;
        throw Solo.error("unknown_quest", full);
    }

    private static Component list(ServerPlayer p) {
        QuestData data = QuestEngine.data(p);
        MutableComponent out = Solo.t("quest.list", p.getDisplayName(), data.all().size(), data.completedCount(), data.failedCount())
                .withStyle(ChatFormatting.GOLD);
        for (Quest q : data.all()) {
            MutableComponent line = Component.literal("\n" + Solo.lower(q.status().name()) + " ").withStyle(ChatFormatting.GRAY)
                    .append(q.title().copy().withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" " + q.template()).withStyle(ChatFormatting.DARK_GRAY));
            for (QuestObjective o : q.objectives()) line.append(Component.literal("  " + o.progress() + "/" + o.required()).withStyle(ChatFormatting.GRAY));
            out.append(line);
        }
        return out;
    }

    /**
     * Finishes the quest named (null: every quest the player has, offers included) with its rewards, as if its
     * objectives were done and it was handed in. A quest named that the player doesn't have is started first.
     */
    private static Component complete(ServerPlayer p, @Nullable String template) throws CommandSyntaxException {
        List<Quest> quests = matching(p, template);
        if (quests.isEmpty() && template != null) {
            Quest q = QuestTemplates.create(template, p, QuestTemplate.Context.at(p.blockPosition()));
            if (q == null) throw Solo.error("quest_cannot_start", template);
            QuestEngine.start(p, q);
            if (QuestEngine.data(p).all().contains(q)) quests = List.of(q);
        }
        if (quests.isEmpty() && template == null) throw Solo.error("no_quests", p.getDisplayName());
        int n = 0;
        for (Quest q : quests) {
            if (q.status() == Quest.Status.OFFERED) QuestEngine.accept(p, q.id());
            if (q.status() == Quest.Status.ACTIVE) QuestTestAccess.addProgress(p, q, Integer.MAX_VALUE / 2);
            if (q.status() == Quest.Status.READY) QuestEngine.complete(p, q);
            n++;
        }
        return Solo.t("quest.completed", p.getDisplayName(), n);
    }

    /** Fails the quest named (null: every quest the player has taken), with what failing it costs. */
    private static Component fail(ServerPlayer p, @Nullable String template) throws CommandSyntaxException {
        List<Quest> quests = matching(p, template);
        quests.removeIf(q -> !q.isTaken());
        if (quests.isEmpty()) throw template == null ? Solo.error("no_quests", p.getDisplayName()) : Solo.error("quest_not_taken", p.getDisplayName(), template);
        for (Quest q : quests) QuestEngine.fail(p, q, QuestEvent.FailReason.TIME);
        return Solo.t("quest.failed", p.getDisplayName(), quests.size());
    }

    private static List<Quest> matching(ServerPlayer p, @Nullable String template) {
        List<Quest> out = new ArrayList<>();
        for (Quest q : QuestEngine.data(p).all()) if (template == null || q.template().equals(template)) out.add(q);
        return out;
    }

    // ---------------------------------------------------------------- shadows

    /** {@code /solo shadow give <creature> [rank] [players]}: a shadow straight into the army. */
    private static LiteralArgumentBuilder<CommandSourceStack> shadow() {
        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> creature = Commands.argument("creature", ResourceLocationArgument.id())
                .suggests((c, b) -> Solo.suggestIds(BuiltInRegistries.ENTITY_TYPE.keySet().stream(), b));
        Solo.targets(creature, (ctx, p) -> giveShadow(ctx, p, null));
        for (ShadowRank rank : ShadowRank.values()) {
            creature.then(Solo.targets(Commands.literal(rank.key()), (ctx, p) -> giveShadow(ctx, p, rank)));
        }
        return Commands.literal("shadow").then(Commands.literal("give").then(creature));
    }

    private static Component giveShadow(CommandContext<CommandSourceStack> ctx, ServerPlayer p, @Nullable ShadowRank rank) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "creature");
        EntityType<?> type = Solo.lookup(BuiltInRegistries.ENTITY_TYPE, id).orElseThrow(() -> Solo.error("unknown_creature", id.toString()));
        boolean boss = type == ModEntities.GATE_BOSS.get() || type == TowerModule.CITADEL_LORD.get();
        ShadowRecord r = ShadowService.give(p, type, rank, boss);
        return Solo.t("shadow.given", p.getDisplayName(), r.displayName(), r.rank().displayName());
    }

    private SoloSkills() {}
}
