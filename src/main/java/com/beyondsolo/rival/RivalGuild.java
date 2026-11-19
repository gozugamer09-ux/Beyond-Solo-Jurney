package com.beyondsolo.rival;

import com.beyondsolo.chronicle.ranking.Rivals;
import net.minecraft.network.chat.Component;

/**
 * Who a rival party hunts for: one of the four great guilds of the national rankings ({@link Rivals}), or nobody (a
 * band of independents). Each has its colour (names over their heads, its emblem in the windows and on the HUD) and its
 * nerve: how often a party of theirs that finds a hunter already in a gate goes in after them anyway
 * ({@code rivals.followInScale} scales it).
 */
public enum RivalGuild {
    AZURE_TIDE(Rivals.AZURE_TIDE, 0xFF4FA8FF, 0.15f),
    IRON_BASTION(Rivals.IRON_BASTION, 0xFFB8C2CC, 0.08f),
    SUNFALL(Rivals.SUNFALL, 0xFFFFB347, 0.15f),
    /** The hungriest of them: a gate somebody else walked into first is still a gate. */
    RED_LANTERN(Rivals.RED_LANTERN, 0xFFFF5A4F, 0.6f),
    /** No guild: hunters who banded together for the day. */
    INDEPENDENT(Rivals.NONE, 0xFFD9C7A3, 0.3f);

    /** The guild's id on the rankings ({@code chronicle.beyondsolo.guild.<id>}); "" for independents. */
    public final String id;
    /** Opaque ARGB. */
    public final int color;
    /** The chance a party of theirs follows a hunter in rather than standing down. */
    public final float nerve;

    RivalGuild(String id, int color, float nerve) {
        this.id = id;
        this.color = color;
        this.nerve = nerve;
    }

    /** The guild's name, or "Independent". */
    public Component displayName() {
        return Rivals.guildName(id);
    }

    /** The guild of a rankings id ("" and anything unknown: independents). */
    public static RivalGuild of(String id) {
        for (RivalGuild g : values()) if (g.id.equals(id)) return g;
        return INDEPENDENT;
    }

    public static RivalGuild byIndex(int i) {
        RivalGuild[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    /** The name a command takes ("red_lantern", "independent"). */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
