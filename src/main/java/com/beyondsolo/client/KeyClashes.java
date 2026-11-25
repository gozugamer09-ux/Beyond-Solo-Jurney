package com.beyondsolo.client;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.notify.SystemNotification;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Other mods in the pack come with keys of their own on keys the System uses: Xaero's Minimap on Y, U, B and Z (the
 * shadow steed, recall, hiding pop-ups, the first skill), Xaero's Head Tracker on H (raising a shadow), Curios on G
 * (the fourth skill). Minecraft fires every key bound to what is pressed, so one press meant for the System also opens
 * another mod's screen. The first time each game that the player stands in a world with no screen open, another mod's
 * key that still sits on its own default, fires in game and shares a System key moves to a free one, and the player is
 * told where it went.
 *
 * <p>Minecraft's own keys and the System's never move. A key moves only once ({@link ClientConfig#KEYS_MOVED}), so one
 * the player binds back stays where they put it, and a key the player chose themselves (not its default) is left
 * alone; a mod added to the pack later is sorted out the first game it is there.</p>
 */
public final class KeyClashes {
    /** Free keys to move a clashing key to, in order (each is used only if no key at all is on it). */
    private static final int[] FREE = {GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE,
            GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET,
            GLFW.GLFW_KEY_BACKSLASH, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END, GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN,
            GLFW.GLFW_KEY_INSERT};
    private static final String MINE = "key.beyondsolo.";

    private static boolean checked;

    /** Looks once a game, the first tick the player is in a world with no screen open, and moves the clashing keys. */
    public static void sortOnce(Minecraft mc) {
        if (checked || mc.player == null || mc.screen != null || mc.options == null) return;
        checked = true;
        Set<KeyMapping> minecrafts = minecrafts(mc.options);
        List<String> done = new ArrayList<>(ClientConfig.KEYS_MOVED.get());
        List<Component> lines = new ArrayList<>();
        for (KeyMapping other : mc.options.keyMappings) {
            if (minecrafts.contains(other) || other.getName().startsWith(MINE) || other.isUnbound() || !other.isDefault()
                    || done.contains(other.getName()) || !firesInGame(other) || !clashes(mc, other)) {
                continue;
            }
            InputConstants.Key to = free(mc);
            if (to == InputConstants.UNKNOWN) break;
            Component was = other.getTranslatedKeyMessage();
            other.setKeyModifierAndCode(KeyModifier.NONE, to);
            done.add(other.getName());
            lines.add(Component.translatable("message.beyondsolo.key_moved", Component.translatable(other.getName()),
                    was.copy().withStyle(ChatFormatting.GRAY), other.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.AQUA)));
            BeyondSolo.LOGGER.info("Moved {} off a key the System uses: {} -> {}", other.getName(), was.getString(), to.getName());
        }
        if (lines.isEmpty()) return;
        ClientConfig.KEYS_MOVED.set(List.copyOf(done));
        ClientConfig.KEYS_MOVED.save();
        KeyMapping.resetMapping();
        mc.options.save();
        if (ClientSystemData.get().hasSystem()) {
            NotificationManager.push(SystemNotification.of(SystemNotification.Kind.INFO, Component.translatable("popup.beyondsolo.keys_moved"),
                    Component.translatable("popup.beyondsolo.keys_moved.body", lines.size())));
        }
        // one line a key, so a key like ; or , never runs into the punctuation around it
        mc.player.sendSystemMessage(Component.translatable("message.beyondsolo.keys_moved"));
        for (Component line : lines) mc.player.sendSystemMessage(Component.literal("  ").append(line));
    }

    /** Minecraft's own keys, which are never moved. */
    private static Set<KeyMapping> minecrafts(Options o) {
        Set<KeyMapping> own = Collections.newSetFromMap(new IdentityHashMap<>());
        own.addAll(List.of(o.keyUp, o.keyLeft, o.keyDown, o.keyRight, o.keyJump, o.keyShift, o.keySprint, o.keyInventory,
                o.keySwapOffhand, o.keyDrop, o.keyUse, o.keyAttack, o.keyPickItem, o.keyChat, o.keyPlayerList, o.keyCommand,
                o.keySocialInteractions, o.keyScreenshot, o.keyTogglePerspective, o.keySmoothCamera, o.keyFullscreen,
                o.keySpectatorOutlines, o.keyAdvancements, o.keySaveHotbarActivator, o.keyLoadHotbarActivator));
        own.addAll(Arrays.asList(o.keyHotbarSlots));
        return own;
    }

    /**
     * Whether the key is live while the player walks around (checked with no screen open): a key of a screen only, like
     * JEI's keys over an item list, never fires with the System's.
     */
    private static boolean firesInGame(KeyMapping key) {
        return key.getKeyConflictContext().isActive();
    }

    /** Whether one of the System's keys is on the same key, with the same modifier, as this one. */
    private static boolean clashes(Minecraft mc, KeyMapping other) {
        for (KeyMapping mine : mc.options.keyMappings) {
            if (mine.getName().startsWith(MINE) && !mine.isUnbound() && mine.getKey().equals(other.getKey())
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
