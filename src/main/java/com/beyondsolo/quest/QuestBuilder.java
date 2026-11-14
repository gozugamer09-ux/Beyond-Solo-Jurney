package com.beyondsolo.quest;

import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds a quest for one player. Counts given to {@link #kill}, {@link #breakBlocks} and {@link #collect} are
 * base values for an E-rank and are scaled by the player's rank (use {@link #fixed()} to turn that off), and so
 * are {@link #xp} and {@link #statPoints}.
 *
 * <pre>
 * Quest q = QuestBuilder.of("mymod:wolves", QuestCategory.NPC, player)
 *         .title(Component.literal("Wolf trouble"))
 *         .kill("minecraft:wolf", 4)
 *         .xp(80).timeLimit(20 * 60 * 5)
 *         .build();
 * QuestEngine.offer(player, q, 20 * 30);
 * </pre>
 */
public final class QuestBuilder {
    private final String template;
    private final QuestCategory category;
    private final int tier;
    private final int level;
    private boolean scale = true;
    private Component title = Component.empty();
    private Component description = Component.empty();
    private final List<QuestObjective> objectives = new ArrayList<>();
    private long xp;
    private int statPoints;
    private float mana;
    private final List<ItemStack> items = new ArrayList<>();
    private String rewardTitle = "";
    private QuestPenalty penalty = QuestPenalty.NONE;
    private int timeLimit;
    private QuestArea area;

    private QuestBuilder(String template, QuestCategory category, int tier, int level) {
        this.template = template;
        this.category = category;
        this.tier = tier;
        this.level = level;
    }

    public static QuestBuilder of(String template, QuestCategory category, ServerPlayer player) {
        SystemData d = SystemAPI.get(player);
        return new QuestBuilder(template, category, d.rank().tier(), d.level());
    }

    /** For tests or quests without a player yet. */
    public static QuestBuilder of(String template, QuestCategory category, int tier, int level) {
        return new QuestBuilder(template, category, tier, level);
    }

    public int tier() { return tier; }

    /** Turns rank scaling off for the counts and rewards added after this call. */
    public QuestBuilder fixed() {
        scale = false;
        return this;
    }

    public QuestBuilder title(Component title) {
        this.title = title;
        return this;
    }

    public QuestBuilder description(Component description) {
        this.description = description;
        return this;
    }

    /** Uses {@code quest.beyondsolo.<key>} and {@code quest.beyondsolo.<key>.desc}. */
    public QuestBuilder text(String key, Object... args) {
        this.title = Component.translatable("quest.beyondsolo." + key);
        this.description = Component.translatable("quest.beyondsolo." + key + ".desc", args);
        return this;
    }

    private int n(int base) {
        return scale ? RankScaling.count(base, tier) : base;
    }

    public QuestBuilder kill(String target, int count) {
        return objective(ObjectiveType.KILL, target, n(count), false, null);
    }

    public QuestBuilder killInArea(String target, int count) {
        return objective(ObjectiveType.KILL, target, n(count), true, null);
    }

    public QuestBuilder breakBlocks(String target, int count) {
        return objective(ObjectiveType.BREAK, target, n(count), false, null);
    }

    public QuestBuilder breakInArea(String target, int count) {
        return objective(ObjectiveType.BREAK, target, count, true, null);
    }

    public QuestBuilder collect(String target, int count) {
        return objective(ObjectiveType.COLLECT, target, n(count), false, null);
    }

    public QuestBuilder travel(int blocks) {
        return objective(ObjectiveType.TRAVEL, "", n(blocks), false, null);
    }

    public QuestBuilder reachLevel(int level) {
        return objective(ObjectiveType.LEVEL, "", level, false, Component.translatable("quest_objective.beyondsolo.level", level));
    }

    /** An objective another module advances with {@link QuestEngine#progress(ServerPlayer, String, int)}. */
    public QuestBuilder custom(String key, int count, Component label) {
        return objective(ObjectiveType.CUSTOM, key, count, false, label);
    }

    public QuestBuilder objective(ObjectiveType type, String target, int count, boolean areaOnly, Component label) {
        if (label == null) {
            label = type == ObjectiveType.TRAVEL
                    ? Component.translatable("quest_objective.beyondsolo.travel")
                    : Component.translatable("quest_objective.beyondsolo." + type.name().toLowerCase(), Targets.name(type, target));
        }
        objectives.add(new QuestObjective(type, target, count, areaOnly, label));
        return this;
    }

    public QuestBuilder xp(long base) {
        this.xp = scale ? RankScaling.xp(base, tier, level) : base;
        return this;
    }

    public QuestBuilder statPoints(int base) {
        this.statPoints = scale ? RankScaling.statPoints(base, tier) : base;
        return this;
    }

    public QuestBuilder mana(float amount) {
        this.mana = amount;
        return this;
    }

    public QuestBuilder item(ItemStack stack) {
        if (!stack.isEmpty()) items.add(stack.copy());
        return this;
    }

    public QuestBuilder rewardTitle(String title) {
        this.rewardTitle = title;
        return this;
    }

    /** Base penalty for an E-rank, scaled by rank. */
    public QuestBuilder penalty(float exhaustion, int debuffSeconds) {
        this.penalty = RankScaling.penalty(exhaustion, debuffSeconds, tier);
        return this;
    }

    public QuestBuilder timeLimit(int ticks) {
        this.timeLimit = Math.max(0, ticks);
        return this;
    }

    public QuestBuilder area(ServerPlayer player, BlockPos center, int radius, int leaveRadius) {
        this.area = new QuestArea(player.level().dimension(), center.immutable(), radius, leaveRadius);
        return this;
    }

    public Quest build() {
        if (objectives.isEmpty()) throw new IllegalStateException("A quest needs at least one objective: " + template);
        return new Quest(UUID.randomUUID(), template, category, title, description, objectives,
                new QuestRewards(xp, statPoints, mana, items, rewardTitle), penalty, timeLimit, area, tier, Quest.Status.ACTIVE);
    }
}
