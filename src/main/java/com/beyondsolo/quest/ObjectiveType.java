package com.beyondsolo.quest;

/**
 * What an objective counts. The target string of an objective is matched against what happened:
 * an id ({@code minecraft:zombie}), a tag ({@code #minecraft:skeletons}), empty for anything, or for KILL
 * the word {@code hostile} for any monster.
 */
public enum ObjectiveType {
    /** Kill entities. Target: entity type or tag, or "hostile". */
    KILL,
    /** Break blocks. Target: block or block tag. */
    BREAK,
    /** Have items in the inventory (counted, not taken). Target: item or item tag. */
    COLLECT,
    /** Travel on foot, in blocks. No target. */
    TRAVEL,
    /** Reach a System level. Required = the level. */
    LEVEL,
    /** Anything else. Other modules advance it with {@link QuestEngine#progress} and their own key as target. */
    CUSTOM;

    public static ObjectiveType byName(String name) {
        for (ObjectiveType t : values()) if (t.name().equalsIgnoreCase(name)) return t;
        return CUSTOM;
    }
}
