package com.beyondsolo.raid;

import com.beyondsolo.system.PlayerClass;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What a raider does in a raid. Each one changes a little while the raid lasts (numbers in {@link RaidConfig}):
 * the vanguard takes less from every blow and draws the boss's eye, the striker hits harder, the support's heals are
 * stronger and they pulse a little health into the raiders around them.
 */
public enum RaidRole {
    VANGUARD(0xFFE0B04A),
    STRIKER(0xFFE0675A),
    SUPPORT(0xFF7CE09B);

    public final int color;

    RaidRole(int color) {
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("ui.beyondsolo.raid.role." + id());
    }

    /** The role a class takes unless its hunter picks another: guardians hold the line, healers support. */
    public static RaidRole forClass(@Nullable PlayerClass cls) {
        if (cls == null) return STRIKER;
        return switch (cls) {
            case GUARDIAN -> VANGUARD;
            case HEALER -> SUPPORT;
            default -> STRIKER;
        };
    }

    public static RaidRole byIndex(int i) {
        RaidRole[] all = values();
        return all[Math.floorMod(i, all.length)];
    }

    @Nullable
    public static RaidRole byId(String id) {
        for (RaidRole r : values()) if (r.id().equalsIgnoreCase(id)) return r;
        return null;
    }
}
