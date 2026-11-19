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
import com.beyondsolo.shadow.ShadowPromotions;
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
            if (s.monarch() || !s.fits(d.playerClass(), JobAPI.subclass(p)) || !s.allows(p)) continue;
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

    /**
     * {@code /solo shadow give <creature> [rank] [players]}: a shadow straight into the army. {@code post} leaves every
     * shadow out on guard where it stands, {@code unpost} takes every guard off its post, {@code posts} lists them.
     * {@code list} shows the army with each shadow's number, {@code promote <number>} promotes one at once (no essence,
     * no level needed, with its moment), {@code name <number> <name>} names one (quotes round a name with spaces; "" takes
     * the name back), {@code essence set|add <amount>} sets the essence for promotions.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> shadow() {
        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> creature = Commands.argument("creature", ResourceLocationArgument.id())
                .suggests((c, b) -> Solo.suggestIds(BuiltInRegistries.ENTITY_TYPE.keySet().stream(), b));
        Solo.targets(creature, (ctx, p) -> giveShadow(ctx, p, null));
        for (ShadowRank rank : ShadowRank.values()) {
            creature.then(Solo.targets(Commands.literal(rank.key()), (ctx, p) -> giveShadow(ctx, p, rank)));
        }
        return Commands.literal("shadow").then(Commands.literal("give").then(creature))
                .then(Solo.targets(Commands.literal("post"), (ctx, p) -> postAll(p)))
                .then(Solo.targets(Commands.literal("unpost"), (ctx, p) -> unpostAll(p)))
                .then(Solo.target(Commands.literal("posts"), (ctx, p) -> posts(p)))
                .then(Solo.target(Commands.literal("list"), (ctx, p) -> shadows(p)))
                .then(Commands.literal("promote").then(Solo.target(Commands.argument("number", IntegerArgumentType.integer(0)),
                        (ctx, p) -> promote(p, IntegerArgumentType.getInteger(ctx, "number")))))
                .then(Commands.literal("name").then(Commands.argument("number", IntegerArgumentType.integer(0))
                        .then(Solo.target(Commands.argument("name", StringArgumentType.string()),
                                (ctx, p) -> name(p, IntegerArgumentType.getInteger(ctx, "number"), StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("essence")
                        .then(Commands.literal("set").then(Solo.targets(Commands.argument("amount", IntegerArgumentType.integer(0)),
                                (ctx, p) -> essence(p, IntegerArgumentType.getInteger(ctx, "amount"), false))))
                        .then(Commands.literal("add").then(Solo.targets(Commands.argument("amount", IntegerArgumentType.integer()),
                                (ctx, p) -> essence(p, IntegerArgumentType.getInteger(ctx, "amount"), true)))));
    }

    private static ShadowRecord numbered(ServerPlayer p, int number) throws CommandSyntaxException {
        ShadowRecord r = ShadowService.army(p).byIndex(number);
        if (r == null) throw Solo.error("no_such_shadow", p.getDisplayName(), number, ShadowService.army(p).size());
        return r;
    }

    private static Component shadows(ServerPlayer p) throws CommandSyntaxException {
        var army = ShadowService.army(p);
        if (army.size() == 0) throw Solo.error("no_shadows", p.getDisplayName());
        MutableComponent out = Solo.t("shadow.list", p.getDisplayName(), army.size(), ShadowService.capacity(p), army.essence(),
                army.bonded(), ShadowPromotions.nameSlots(p));
        ShadowRank top = ShadowService.top(p);
        for (int i = 0; i < army.size(); i++) {
            ShadowRecord r = army.byIndex(i);
            out.append(Component.literal("\n  " + i + ". ")).append(Solo.t("shadow.list.line", r.displayName(), r.rank().displayName(), r.level(),
                    r.kills()).withStyle(r.ready(top) ? ChatFormatting.GOLD : army.isActive(r.id()) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            if (r.ready(top)) out.append(" ").append(Solo.t("shadow.list.ready").withStyle(ChatFormatting.GOLD));
            if (r.bonded()) out.append(" ").append(Solo.t("shadow.list.named").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return out;
    }

    private static Component promote(ServerPlayer p, int number) throws CommandSyntaxException {
        ShadowRecord r = numbered(p, number);
        ShadowRank before = r.rank();
        if (!ShadowPromotions.promoteFree(p, r)) throw Solo.error("shadow_top", r.displayName(), before.displayName());
        return Solo.t("shadow.promoted", p.getDisplayName(), r.displayName(), before.displayName(), r.rank().displayName());
    }

    private static Component name(ServerPlayer p, int number, String name) throws CommandSyntaxException {
        ShadowRecord r = numbered(p, number);
        Component before = r.displayName();
        String why = ShadowPromotions.nameRefusal(p, r, name);
        if (why != null) throw Solo.error("shadow_name", Component.translatable("shadow.beyondsolo.name." + why,
                com.beyondsolo.shadow.ShadowNames.MIN_GIVEN, com.beyondsolo.shadow.ShadowNames.MAX_GIVEN, ShadowPromotions.nameSlots(p)));
        ShadowPromotions.name(p, r.id(), name);
        return Solo.t(r.bonded() ? "shadow.named" : "shadow.unnamed", p.getDisplayName(), before, r.displayName());
    }

    private static Component essence(ServerPlayer p, int amount, boolean add) {
        if (add) ShadowPromotions.addEssence(p, amount);
        else ShadowPromotions.setEssence(p, amount);
        return Solo.t("shadow.essence", p.getDisplayName(), ShadowService.army(p).essence());
    }

    private static Component postAll(ServerPlayer p) throws CommandSyntaxException {
        if (!ShadowService.canRaise(p)) throw Solo.error("not_necromancer", p.getDisplayName());
        if (!ShadowService.canGuardIn(p.serverLevel())) throw Solo.error("no_guard_here", p.getDisplayName());
        int n = 0;
        for (java.util.UUID rid : List.copyOf(ShadowService.army(p).active().keySet())) {
            ShadowRecord r = ShadowService.army(p).get(rid);
            if (r != null && !r.posted() && ShadowService.post(p, rid)) n++;
        }
        if (n == 0) throw Solo.error("no_shadows_out", p.getDisplayName());
        return Solo.t("shadow.posted", p.getDisplayName(), n);
    }

    private static Component unpostAll(ServerPlayer p) throws CommandSyntaxException {
        int n = 0;
        for (ShadowRecord r : List.copyOf(ShadowService.army(p).shadows())) if (ShadowService.unpost(p, r.id())) n++;
        if (n == 0) throw Solo.error("no_guards", p.getDisplayName());
        return Solo.t("shadow.unposted", p.getDisplayName(), n);
    }

    private static Component posts(ServerPlayer p) throws CommandSyntaxException {
        List<ShadowRecord> guards = new ArrayList<>();
        for (ShadowRecord r : ShadowService.army(p).shadows()) if (r.posted()) guards.add(r);
        if (guards.isEmpty()) throw Solo.error("no_guards", p.getDisplayName());
        MutableComponent out = Solo.t("shadow.posts", p.getDisplayName(), guards.size());
        for (ShadowRecord r : guards) {
            net.minecraft.world.phys.Vec3 at = r.post();
            boolean here = r.postedIn(p.level().dimension());
            boolean standing = ShadowService.army(p).isActive(r.id());
            out.append(Component.literal("\n  "))
                    .append(Solo.t(standing ? "shadow.post.standing" : "shadow.post.away", r.displayName(),
                            Math.round(at.x) + " " + Math.round(at.y) + " " + Math.round(at.z), String.valueOf(r.postDimension()),
                            here ? Math.round(Math.sqrt(p.distanceToSqr(at))) : "-"));
        }
        return out;
    }

    private static Component giveShadow(CommandContext<CommandSourceStack> ctx, ServerPlayer p, @Nullable ShadowRank rank) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "creature");
        EntityType<?> type = Solo.lookup(BuiltInRegistries.ENTITY_TYPE, id).orElseThrow(() -> Solo.error("unknown_creature", id.toString()));
        // a boss (ours, the world's threats, the dragon, the wither, another mod's) rises as a named elite
        boolean boss = type == ModEntities.GATE_BOSS.get() || type == TowerModule.CITADEL_LORD.get()
                || com.beyondsolo.shadow.ShadowForm.probeBossRank(p.serverLevel(), type) >= 0;
        ShadowRecord r = ShadowService.give(p, type, rank, boss);
        return Solo.t("shadow.given", p.getDisplayName(), r.displayName(), r.rank().displayName());
    }

    private SoloSkills() {}
}
