package com.beyondsolo.client;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.notify.SystemNotification;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * The pack's map mod (Xaero's Minimap) comes with keys of its own on Y, U, B and Z, which the System uses too (the shadow
 * steed, recall, hiding pop-ups, the first skill). Minecraft fires every key bound to what is pressed, so one press meant
 * for the System also opens a map screen. Once per game (remembered in the client config, {@link ClientConfig#KEYS_SORTED})
 * the map's keys that clash with the System's move to free ones, and the player is told where they went. Only Xaero's
 * keys are moved, never Minecraft's own or the System's; a key the player binds back afterwards stays where they put it.
 */
public final class KeyClashes {
    /** Free keys to move a clashing key to, in order (each is used only if nothing else has it). */
    private static final int[] FREE = {GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE,
            GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET,
            GLFW.GLFW_KEY_BACKSLASH, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END, GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN,
            GLFW.GLFW_KEY_INSERT};

    /** Checks once (the first tick in a world) and moves the map's clashing keys. */
    public static void sortOnce(Minecraft mc) {
        if (ClientConfig.KEYS_SORTED.get() || mc.options == null) return;
        ClientConfig.KEYS_SORTED.set(true);
        ClientConfig.KEYS_SORTED.save();
        MutableComponent moved = null;
        for (KeyMapping other : mc.options.keyMappings) {
            if (!other.getName().startsWith("gui.xaero") || other.isUnbound() || !clashes(mc, other)) continue;
            InputConstants.Key to = free(mc);
            Component was = other.getTranslatedKeyMessage();
            other.setKeyModifierAndCode(KeyModifier.NONE, to);
            Component line = Component.translatable("message.beyondsolo.key_moved", Component.translatable(other.getName()), was,
                    other.getTranslatedKeyMessage());
            moved = moved == null ? line.copy() : moved.append(", ").append(line);
            BeyondSolo.LOGGER.info("Moved {} off a key the System uses: {} -> {}", other.getName(), was.getString(), to.getName());
        }
        if (moved == null) return;
        KeyMapping.resetMapping();
        mc.options.save();
        Component body = Component.translatable("message.beyondsolo.keys_moved", moved);
        if (ClientSystemData.get().hasSystem()) {
            NotificationManager.push(SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable("popup.beyondsolo.keys_moved"), body));
        }
        if (mc.player != null) mc.player.sendSystemMessage(body);
    }

    /** Whether one of the System's keys is on the same key as this one. */
    private static boolean clashes(Minecraft mc, KeyMapping other) {
        for (KeyMapping mine : mc.options.keyMappings) {
            if (mine.getName().startsWith("key.beyondsolo.") && !mine.isUnbound() && mine.getKey().equals(other.getKey())
                    && mine.getKeyModifier() == other.getKeyModifier()) {
                return true;
            }
        }
        return false;
    }

    /** The first key in {@link #FREE} that nothing is bound to, or none. */
    private static InputConstants.Key free(Minecraft mc) {
        for (int code : FREE) {
            InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(code);
            boolean used = false;
            for (KeyMapping m : mc.options.keyMappings) used |= m.getKey().equals(key);
            if (!used) return key;
        }
        return InputConstants.UNKNOWN;
    }

    private KeyClashes() {}
}
