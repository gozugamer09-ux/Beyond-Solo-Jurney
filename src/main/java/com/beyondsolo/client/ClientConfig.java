package com.beyondsolo.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player display options. Lives in config/beyondsolo-client.toml. */
public final class ClientConfig {
    public enum NotificationMode {
        /** Windows float in the world beside you and drift as you turn. */
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

    public static final ModConfigSpec SPEC = B.build();

    public static NotificationMode cycleMode() {
        NotificationMode[] all = NotificationMode.values();
        NotificationMode next = all[(NOTIFICATION_MODE.get().ordinal() + 1) % all.length];
        NOTIFICATION_MODE.set(next);
        NOTIFICATION_MODE.save();
        return next;
    }

    private ClientConfig() {}
}
