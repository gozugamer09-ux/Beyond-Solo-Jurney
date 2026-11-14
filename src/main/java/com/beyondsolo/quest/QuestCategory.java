package com.beyondsolo.quest;

import net.minecraft.network.chat.Component;

/**
 * Where a quest came from. Picks its colour, its label and how it behaves when it is offered.
 * NPC and GATE are reserved for the villager and gate modules.
 */
public enum QuestCategory {
    /** The System's own quest line, handed out after the evaluation. */
    SET(0xFF5FD0FF),
    /** Resets every in-game day. Unfinished at the end of the day = failed. */
    DAILY(0xFF8BE28B),
    /** Started by something the player ran into. Offered: the player can accept or let it go. */
    SURPRISE(0xFFFFD166),
    /** Started by danger. Forced: accepted on the spot, and fleeing or dying fails it. */
    EMERGENCY(0xFFFF5D6C),
    /** Given by a villager or other NPC (NPC module). */
    NPC(0xFFC79BFF),
    /** Part of a gate run (gate module). */
    GATE(0xFFB9EDFF);

    private final int color;

    QuestCategory(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("quest_category.beyondsolo." + name().toLowerCase());
    }

    public static QuestCategory byName(String name) {
        for (QuestCategory c : values()) if (c.name().equalsIgnoreCase(name)) return c;
        return SET;
    }
}
