package com.beyondsolo.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player display options. Lives in config/beyondsolo-client.toml. */
public final class ClientConfig {
    public enum NotificationMode {
        /** Windows float at the side of your view and drift as you turn and move. */
        FLOATING,
        /** Windows stay fixed at the top of the screen. */
        PINNED,
        /** No pop-ups; a small badge counts unread messages (read them in the System log). */
        HIDDEN
    }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.EnumValue<NotificationMode> NOTIFICATION_MODE = B
            .comment("How System pop-ups are shown. Cycle in game with the notification key (default N).")
            .defineEnum("notificationMode", NotificationMode.FLOATING);
    public static final ModConfigSpec.BooleanValue SHOW_HUD = B
            .comment("Show the small status HUD (level, mana, fatigue, objective) in the top-left corner.")
            .define("showHud", true);
    public static final ModConfigSpec.BooleanValue NOTIFICATION_SOUNDS = B
            .define("notificationSounds", true);
    public static final ModConfigSpec.BooleanValue KEEP_POPUPS = B
            .comment("Keep pop-ups on screen until you hide them (default key B) instead of letting them fade out on their own.")
            .define("keepPopups", false);
    public static final ModConfigSpec.BooleanValue SYSTEM_FONT = B
            .comment("Draw System text in the smooth System typeface. Turn off to use the vanilla Minecraft font everywhere.")
            .define("systemFont", true);

    public static final ModConfigSpec.BooleanValue DAMAGE_NUMBERS = B
            .comment("Show floating damage numbers for your hits and for element damage nearby.")
            .define("combat.damageNumbers", true);
    public static final ModConfigSpec.BooleanValue ENEMY_METERS = B
            .comment("Show element buildup meters and statuses over the heads of creatures in a fight.")
            .define("combat.enemyMeters", true);
    public static final ModConfigSpec.BooleanValue SCREEN_EFFECTS = B
            .comment("Tint the screen edges while you burn, bleed, carry heavy venom or a crimson brand.")
            .define("combat.screenEffects", true);

    public static final ModConfigSpec SPEC = B.build();

    public static NotificationMode cycleMode() {
        NotificationMode[] all = NotificationMode.values();
        NotificationMode next = all[(NOTIFICATION_MODE.get().ordinal() + 1) % all.length];
        setMode(next);
        return next;
    }

    public static void setMode(NotificationMode mode) {
        NOTIFICATION_MODE.set(mode);
        NOTIFICATION_MODE.save();
    }

    public static boolean toggleKeepPopups() {
        boolean next = !KEEP_POPUPS.get();
        KEEP_POPUPS.set(next);
        KEEP_POPUPS.save();
        return next;
    }

    private ClientConfig() {}
}
