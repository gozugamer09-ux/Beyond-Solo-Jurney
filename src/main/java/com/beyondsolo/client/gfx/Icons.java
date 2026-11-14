package com.beyondsolo.client.gfx;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import net.minecraft.resources.ResourceLocation;

/**
 * The System UI icon set. Each texture is a signed distance field built by tools/gen_ui_assets.py
 * and drawn with {@link Shapes#icon}, so one texture stays sharp at every size.
 */
public final class Icons {
    // Keep in sync with tools/gen_ui_assets.py.
    public static final float TEXTURE_SIZE = 128f;
    public static final float CONTENT_SIZE = 96f;
    public static final float SPREAD = 16f;

    public static final ResourceLocation PIN = icon("pin");
    public static final ResourceLocation COMPASS = icon("compass");
    public static final ResourceLocation LOCK = icon("lock");
    public static final ResourceLocation CHECK = icon("check");
    public static final ResourceLocation DROP = icon("drop");
    public static final ResourceLocation MOON = icon("moon");
    public static final ResourceLocation STAR = icon("star");
    public static final ResourceLocation DIAMOND = icon("diamond");
    public static final ResourceLocation EYE_OFF = icon("eye_off");
    public static final ResourceLocation SKULL = icon("skull");
    public static final ResourceLocation HOURGLASS = icon("hourglass");
    public static final ResourceLocation CROWN = icon("crown");
    public static final ResourceLocation MEDAL = icon("medal");
    public static final ResourceLocation BELL = icon("bell");
    public static final ResourceLocation SPARKLE = icon("sparkle");
    public static final ResourceLocation SEAL = icon("seal");
    public static final ResourceLocation HEXAGON = icon("hexagon");
    public static final ResourceLocation HEART = icon("stat_vit");
    public static final ResourceLocation FLAME = icon("flame");
    public static final ResourceLocation FLASK = icon("flask");
    public static final ResourceLocation SPIRAL = icon("spiral");
    public static final ResourceLocation HEART_BREAK = icon("heart_break");
    public static final ResourceLocation SNOWFLAKE = icon("snowflake");
    public static final ResourceLocation RUN = icon("run");
    public static final ResourceLocation SHIELD = icon("shield");
    public static final ResourceLocation WEIGHT = icon("weight");
    public static final ResourceLocation PACKAGE = icon("package");
    public static final ResourceLocation HANDSHAKE = icon("handshake");
    public static final ResourceLocation FLAG = icon("flag");
    public static final ResourceLocation PATH = icon("path");
    public static final ResourceLocation EXCLAIM = icon("exclaim");
    public static final ResourceLocation QUESTION = icon("question");
    public static final ResourceLocation CHAT = icon("chat");
    public static final ResourceLocation VILLAGERS = icon("villagers");

    private static final ResourceLocation[] STATS = new ResourceLocation[Stat.values().length];
    private static final ResourceLocation[] CLASSES = new ResourceLocation[PlayerClass.values().length];
    private static final ResourceLocation[] RANKS = new ResourceLocation[PlayerRank.values().length];
    private static final ResourceLocation[] KINDS = new ResourceLocation[SystemNotification.Kind.values().length];

    static {
        for (Stat s : Stat.values()) STATS[s.ordinal()] = icon("stat_" + s.key());
        for (PlayerClass c : PlayerClass.values()) CLASSES[c.ordinal()] = icon("class_" + c.name().toLowerCase());
        for (PlayerRank r : PlayerRank.values()) RANKS[r.ordinal()] = icon("rank_" + r.name().toLowerCase());
        for (SystemNotification.Kind k : SystemNotification.Kind.values()) KINDS[k.ordinal()] = icon("kind_" + k.name().toLowerCase());
    }

    public static ResourceLocation of(Stat stat) {
        return STATS[stat.ordinal()];
    }

    public static ResourceLocation of(PlayerClass playerClass) {
        return CLASSES[playerClass.ordinal()];
    }

    /** The rank letter as a shape, for big emblems. */
    public static ResourceLocation of(PlayerRank rank) {
        return RANKS[rank.ordinal()];
    }

    public static ResourceLocation of(SystemNotification.Kind kind) {
        return KINDS[kind.ordinal()];
    }

    private static ResourceLocation icon(String name) {
        return BeyondSolo.id("textures/gui/icon/" + name + ".png");
    }

    private Icons() {}
}
