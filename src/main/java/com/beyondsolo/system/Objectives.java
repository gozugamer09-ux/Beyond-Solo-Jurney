package com.beyondsolo.system;

import net.minecraft.network.chat.Component;

/**
 * The single "current objective" line the core shows before the quest engine exists.
 * The quest engine can keep using it for story steps, or replace it with real quests.
 */
public final class Objectives {
    public static final String NONE = "";
    public static final String FIND_VILLAGE = "find_village";
    public static final String FIND_EVALUATION_ZONE = "find_evaluation_zone";

    public static Component title(String id) {
        return Component.translatable("objective.beyondsolo." + id);
    }

    public static Component description(String id) {
        return Component.translatable("objective.beyondsolo." + id + ".desc");
    }

    private Objectives() {}
}
