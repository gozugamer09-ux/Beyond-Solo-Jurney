package com.beyondsolo.quest;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * One line of a quest: "Defeat hostile creatures 3/10".
 *
 * @param areaOnly only counts inside the quest's area (surprise quests use this)
 */
public final class QuestObjective {
    private final ObjectiveType type;
    private final String target;
    private final int required;
    private final boolean areaOnly;
    private final Component label;
    /** COLLECT only: the items are taken when the quest is handed in. */
    private boolean consume;
    private int progress;

    public QuestObjective(ObjectiveType type, String target, int required, boolean areaOnly, Component label) {
        this.type = type;
        this.target = target == null ? "" : target;
        this.required = Math.max(1, required);
        this.areaOnly = areaOnly;
        this.label = label;
    }

    public ObjectiveType type() { return type; }
    public String target() { return target; }
    public int required() { return required; }
    public boolean areaOnly() { return areaOnly; }
    public Component label() { return label; }
    public int progress() { return progress; }
    public boolean consume() { return consume; }

    QuestObjective consuming() {
        this.consume = true;
        return this;
    }
    public boolean done() { return progress >= required; }
    public float fraction() { return Math.min(1f, progress / (float) required); }

    /** Returns true if the value changed. */
    boolean setProgress(int value) {
        int v = Math.max(0, Math.min(required, value));
        if (v == progress) return false;
        progress = v;
        return true;
    }

    CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putString("target", target);
        tag.putInt("required", required);
        tag.putInt("progress", progress);
        tag.putBoolean("areaOnly", areaOnly);
        if (consume) tag.putBoolean("consume", true);
        tag.putString("label", Component.Serializer.toJson(label, provider));
        return tag;
    }

    static QuestObjective load(CompoundTag tag, HolderLookup.Provider provider) {
        Component label = Quest.readComponent(tag.getString("label"), provider);
        QuestObjective o = new QuestObjective(ObjectiveType.byName(tag.getString("type")), tag.getString("target"),
                tag.getInt("required"), tag.getBoolean("areaOnly"), label);
        o.progress = Math.min(o.required, tag.getInt("progress"));
        o.consume = tag.getBoolean("consume");
        return o;
    }
}
