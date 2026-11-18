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
    /**
     * How the line shows its progress. Most count ("3/10"); a situation the System is watching shows other things: a
     * line with no counter ("Stay alive"), a share ("35%"), the seconds left of a hold ("0:42"), or a creature's health,
     * which is only there to be watched and never counts towards finishing the quest.
     */
    public enum Display {
        /** "3/10". */
        COUNT,
        /** Just the label: done or not. */
        NONE,
        /** "35%": progress out of required, as a share. */
        PERCENT,
        /** Required is seconds; shows the time left ("0:42"). */
        CLOCK,
        /** Someone's health (progress out of required): a bar to watch, never counted towards finishing. */
        HEALTH,
        /**
         * A rule the quest is played under ("Stay alive", "Take no hits"): no counter, never counted towards finishing.
         * Breaking it is up to the situation that set it.
         */
        RULE;

        public static Display byName(String name) {
            for (Display d : values()) if (d.name().equalsIgnoreCase(name)) return d;
            return COUNT;
        }
    }

    private final ObjectiveType type;
    private final String target;
    private final int required;
    private final boolean areaOnly;
    private final Component label;
    private Display display = Display.COUNT;
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
    public Display display() { return display; }

    QuestObjective consuming() {
        this.consume = true;
        return this;
    }

    QuestObjective shown(Display display) {
        this.display = display == null ? Display.COUNT : display;
        return this;
    }

    /** False for a line that is only there to be watched or obeyed (a creature's health, a rule): it never holds a quest back. */
    public boolean counts() { return display != Display.HEALTH && display != Display.RULE; }
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
        if (display != Display.COUNT) tag.putString("display", display.name());
        tag.putString("label", Component.Serializer.toJson(label, provider));
        return tag;
    }

    static QuestObjective load(CompoundTag tag, HolderLookup.Provider provider) {
        Component label = Quest.readComponent(tag.getString("label"), provider);
        QuestObjective o = new QuestObjective(ObjectiveType.byName(tag.getString("type")), tag.getString("target"),
                tag.getInt("required"), tag.getBoolean("areaOnly"), label);
        o.progress = Math.min(o.required, tag.getInt("progress"));
        o.consume = tag.getBoolean("consume");
        o.display = Display.byName(tag.getString("display"));
        return o;
    }
}
