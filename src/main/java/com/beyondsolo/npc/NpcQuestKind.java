package com.beyondsolo.npc;

import com.beyondsolo.BeyondSolo;

/** The five kinds of villager request. */
public enum NpcQuestKind {
    /** Bring items; they are handed over at the end. */
    GATHER(true),
    /** Defeat a kind of creature anywhere, then report back. */
    HUNT(true),
    /** A nest of monsters gathers at a marked spot near the village; clear it and report back. */
    CLEAR(true),
    /** Walk a traveller safely to a marked spot. Done on arrival. */
    ESCORT(false),
    /** Carry a sealed parcel to a marked spot (another village when there is one). Done on delivery. */
    DELIVER(false);

    private final boolean turnIn;

    NpcQuestKind(boolean turnIn) {
        this.turnIn = turnIn;
    }

    /** True if the quest is brought back to the villager to finish. */
    public boolean turnIn() {
        return turnIn;
    }

    public String template() {
        return BeyondSolo.id("npc/" + name().toLowerCase()).toString();
    }

    public static NpcQuestKind byTemplate(String template) {
        for (NpcQuestKind k : values()) if (k.template().equals(template)) return k;
        return null;
    }
}
