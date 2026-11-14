package com.beyondsolo.quest;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One quest a player has: everything it needs is stored on the instance (texts, objectives already scaled to the
 * player's rank, rewards, penalty), so a quest keeps working even if the template that made it changes or goes
 * away. NPC and gate quests can be built the same way with {@link QuestBuilder}.
 */
public final class Quest {
    /** OFFERED: waiting to be accepted. ACTIVE: in progress. READY: every objective is done, but it has to be handed in to its giver. */
    public enum Status { OFFERED, ACTIVE, READY }

    private final UUID id;
    private final String template;
    private final QuestCategory category;
    private final Component title;
    private final Component description;
    private final List<QuestObjective> objectives;
    private final QuestRewards rewards;
    private final QuestPenalty penalty;
    private final int timeLimit;
    @Nullable private final QuestArea area;
    private final int rankTier;
    @Nullable private QuestGiver giver;
    /** Data the module that made the quest keeps on it (an escort's traveller, whether a nest was spawned...). */
    private final CompoundTag extra = new CompoundTag();
    private Status status;
    /** Game time the quest runs out, or -1. Set when it becomes active. */
    private long deadline = -1;
    /** Game time an offer disappears, or -1. */
    private long offerExpires = -1;

    Quest(UUID id, String template, QuestCategory category, Component title, Component description, List<QuestObjective> objectives,
          QuestRewards rewards, QuestPenalty penalty, int timeLimit, @Nullable QuestArea area, int rankTier, Status status) {
        this.id = id;
        this.template = template;
        this.category = category;
        this.title = title;
        this.description = description;
        this.objectives = List.copyOf(objectives);
        this.rewards = rewards;
        this.penalty = penalty;
        this.timeLimit = timeLimit;
        this.area = area;
        this.rankTier = rankTier;
        this.status = status;
    }

    public UUID id() { return id; }
    /** The template id, e.g. {@code beyondsolo:spawner_nest}. Used for chains and cooldowns. */
    public String template() { return template; }
    public QuestCategory category() { return category; }
    public Component title() { return title; }
    public Component description() { return description; }
    public List<QuestObjective> objectives() { return objectives; }
    public QuestRewards rewards() { return rewards; }
    public QuestPenalty penalty() { return penalty; }
    /** Ticks the player has once the quest starts, or 0 for no limit. */
    public int timeLimit() { return timeLimit; }
    @Nullable public QuestArea area() { return area; }
    public int rankTier() { return rankTier; }
    public Status status() { return status; }
    /** Who gave the quest (NPC quests), or null. */
    @Nullable public QuestGiver giver() { return giver; }
    /** True if finishing the objectives isn't enough: the quest has to be handed in to its giver. */
    public boolean needsTurnIn() { return giver != null && giver.turnIn(); }
    public CompoundTag extra() { return extra; }
    /** ACTIVE or READY: the player has it. */
    public boolean isTaken() { return status != Status.OFFERED; }
    public long deadline() { return deadline; }
    public long offerExpires() { return offerExpires; }
    public boolean isForced() { return category == QuestCategory.EMERGENCY; }

    public boolean isComplete() {
        for (QuestObjective o : objectives) if (!o.done()) return false;
        return true;
    }

    public float fraction() {
        float sum = 0;
        for (QuestObjective o : objectives) sum += o.fraction();
        return objectives.isEmpty() ? 1 : sum / objectives.size();
    }

    void activate(long gameTime) {
        status = Status.ACTIVE;
        offerExpires = -1;
        deadline = timeLimit > 0 ? gameTime + timeLimit : -1;
    }

    void setGiver(@Nullable QuestGiver giver) {
        this.giver = giver;
    }

    void markReady() {
        status = Status.READY;
    }

    void unready() {
        if (status == Status.READY) status = Status.ACTIVE;
    }

    void offer(long expires) {
        status = Status.OFFERED;
        offerExpires = expires;
    }

    void setDeadline(long deadline) {
        this.deadline = deadline;
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("template", template);
        tag.putString("category", category.name());
        tag.putString("title", Component.Serializer.toJson(title, provider));
        tag.putString("desc", Component.Serializer.toJson(description, provider));
        ListTag list = new ListTag();
        for (QuestObjective o : objectives) list.add(o.save(provider));
        tag.put("objectives", list);
        tag.put("rewards", rewards.save(provider));
        tag.put("penalty", penalty.save());
        tag.putInt("timeLimit", timeLimit);
        if (area != null) tag.put("area", area.save());
        tag.putInt("rank", rankTier);
        tag.putString("status", status.name());
        tag.putLong("deadline", deadline);
        tag.putLong("offerExpires", offerExpires);
        if (giver != null) tag.put("giver", giver.save(provider));
        if (!extra.isEmpty()) tag.put("extra", extra.copy());
        return tag;
    }

    public static Quest load(CompoundTag tag, HolderLookup.Provider provider) {
        List<QuestObjective> objectives = new ArrayList<>();
        for (Tag t : tag.getList("objectives", Tag.TAG_COMPOUND)) objectives.add(QuestObjective.load((CompoundTag) t, provider));
        Status status = switch (tag.getString("status")) {
            case "OFFERED" -> Status.OFFERED;
            case "READY" -> Status.READY;
            default -> Status.ACTIVE;
        };
        Quest q = new Quest(tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(), tag.getString("template"),
                QuestCategory.byName(tag.getString("category")),
                readComponent(tag.getString("title"), provider), readComponent(tag.getString("desc"), provider), objectives,
                QuestRewards.load(tag.getCompound("rewards"), provider), QuestPenalty.load(tag.getCompound("penalty")),
                tag.getInt("timeLimit"), tag.contains("area") ? QuestArea.load(tag.getCompound("area")) : null,
                tag.getInt("rank"), status);
        q.deadline = tag.getLong("deadline");
        q.offerExpires = tag.getLong("offerExpires");
        if (tag.contains("giver")) q.giver = QuestGiver.load(tag.getCompound("giver"), provider);
        q.extra.merge(tag.getCompound("extra"));
        return q;
    }

    static Component readComponent(String json, HolderLookup.Provider provider) {
        if (json == null || json.isEmpty()) return Component.empty();
        try {
            Component c = Component.Serializer.fromJson(json, provider);
            return c == null ? Component.empty() : c;
        } catch (Exception e) {
            BeyondSolo.LOGGER.warn("Bad quest text {}", json);
            return Component.literal(json);
        }
    }
}
