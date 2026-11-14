package com.beyondsolo.quest;

import net.minecraft.nbt.CompoundTag;

/**
 * What failing a quest costs: exhaustion (100 = collapse) and a few seconds of weakness and slowness.
 * Higher ranks get harsher penalties (see {@link RankScaling#penalty}).
 */
public record QuestPenalty(float exhaustion, int debuffSeconds) {
    public static final QuestPenalty NONE = new QuestPenalty(0, 0);

    public boolean isNone() {
        return exhaustion <= 0 && debuffSeconds <= 0;
    }

    /** The same penalty, scaled (abandoning a quest costs half). */
    public QuestPenalty scaled(float factor) {
        return new QuestPenalty(exhaustion * factor, Math.round(debuffSeconds * factor));
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("exhaustion", exhaustion);
        tag.putInt("debuff", debuffSeconds);
        return tag;
    }

    static QuestPenalty load(CompoundTag tag) {
        return new QuestPenalty(tag.getFloat("exhaustion"), tag.getInt("debuff"));
    }
}
