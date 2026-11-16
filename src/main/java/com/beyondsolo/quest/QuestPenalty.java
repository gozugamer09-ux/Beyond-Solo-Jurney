package com.beyondsolo.quest;

import net.minecraft.nbt.CompoundTag;

/**
 * What failing a quest costs: exhaustion (100 = collapse) and a few seconds of weakness and slowness.
 * Higher ranks get harsher penalties (see {@link RankScaling#penalty}).
 *
 * <p>With {@code zone} set, failing sends the player to the Penalty Zone instead (see
 * {@link com.beyondsolo.penalty.PenaltyZone}); the exhaustion and debuffs then only apply when the zone is turned
 * off in the config.</p>
 */
public record QuestPenalty(float exhaustion, int debuffSeconds, boolean zone) {
    public static final QuestPenalty NONE = new QuestPenalty(0, 0, false);

    public QuestPenalty(float exhaustion, int debuffSeconds) {
        this(exhaustion, debuffSeconds, false);
    }

    public boolean isNone() {
        return exhaustion <= 0 && debuffSeconds <= 0 && !zone;
    }

    /** The same penalty, scaled (abandoning a quest costs half). The Penalty Zone is not halved. */
    public QuestPenalty scaled(float factor) {
        return new QuestPenalty(exhaustion * factor, Math.round(debuffSeconds * factor), zone);
    }

    /** The same penalty, ending in the Penalty Zone. */
    public QuestPenalty withZone() {
        return new QuestPenalty(exhaustion, debuffSeconds, true);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("exhaustion", exhaustion);
        tag.putInt("debuff", debuffSeconds);
        if (zone) tag.putBoolean("zone", true);
        return tag;
    }

    static QuestPenalty load(CompoundTag tag) {
        return new QuestPenalty(tag.getFloat("exhaustion"), tag.getInt("debuff"), tag.getBoolean("zone"));
    }
}
