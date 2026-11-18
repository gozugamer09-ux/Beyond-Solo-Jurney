package com.beyondsolo.guild;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Where a hunter stands in their guild. The master leads (everything, and only they can hand the guild on or end it);
 * officers invite, hire hunters, request raid permits and remove plain members; members fight, pay in and leave.
 */
public enum GuildRole {
    MEMBER,
    OFFICER,
    MASTER;

    /** True for officers and the master. */
    public boolean manages() {
        return this != MEMBER;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("ui.beyondsolo.guild.role." + id());
    }

    public static GuildRole byId(String id) {
        for (GuildRole r : values()) if (r.id().equalsIgnoreCase(id)) return r;
        return MEMBER;
    }

    public static GuildRole byIndex(int i) {
        GuildRole[] all = values();
        return all[Math.floorMod(i, all.length)];
    }
}
