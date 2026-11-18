package com.beyondsolo.client.gfx;

import com.beyondsolo.client.SystemTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.StringUtil;
import org.lwjgl.glfw.GLFW;

import java.util.function.UnaryOperator;

/**
 * A one-line text field in the System's look, for the pages of the System window: click to type in it, Enter or a
 * click elsewhere to stop, Escape to stop as well. Typing goes at the end (Backspace takes the last character, with
 * Ctrl the last word); Ctrl+V pastes. {@code filter} shapes what is typed (upper case, allowed characters).
 */
public final class TextField {
    private String value = "";
    private final int max;
    private final UnaryOperator<String> filter;
    private boolean focused;
    private double focusedAt;
    private float x, y, w, h;

    public TextField(int max, UnaryOperator<String> filter) {
        this.max = max;
        this.filter = filter;
    }

    public String value() {
        return value;
    }

    public void set(String text) {
        value = clip(filter.apply(text == null ? "" : text));
    }

    public boolean focused() {
        return focused;
    }

    public void focus(boolean on) {
        if (on && !focused) focusedAt = Anim.now();
        focused = on;
    }

    private String clip(String s) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Draws the field; {@code placeholder} shows while it is empty. */
    public void render(GuiGraphics g, float x, float y, float w, float h, String placeholder, int accent, int mouseX, int mouseY) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, h);
        int border = focused ? accent : hover ? Gfx.mix(accent, SystemTheme.TEXT_SOFT, 0.4f) : Gfx.alpha(accent, 0.45f);
        Shapes.box(x, y, w, h).radius(4).fill(Gfx.alpha(0xFF040812, focused ? 0.85f : 0.65f)).border(0.7f, border)
                .glow(focused ? 4 : 0, Gfx.alpha(accent, 0.3f)).draw(g);
        float size = Math.min(6.2f, h * 0.55f);
        float tx = x + 4, ty = SysFont.centerY(y, h, size);
        if (value.isEmpty() && !focused) {
            SysFont.text(g, SysFont.fit(placeholder, size, false, w - 8), tx, ty, size, SystemTheme.TEXT_DIM);
            return;
        }
        // the end of the text stays in view
        String shown = value;
        while (shown.length() > 1 && SysFont.width(shown, size, true) > w - 10) shown = shown.substring(1);
        SysFont.text(g, shown, tx, ty, size, SystemTheme.TEXT);
        if (focused && ((int) (Anim.since(focusedAt) * 2.2f)) % 2 == 0) {
            float cx = tx + SysFont.width(shown, size, true) + 0.8f;
            Shapes.box(cx, y + h * 0.22f, 0.8f, h * 0.56f).fill(Gfx.mix(accent, 0xFFFFFFFF, 0.5f)).draw(g);
        }
    }

    /** A click: focuses the field if it is on it, else lets it go. True if it was on the field. */
    public boolean click(double mouseX, double mouseY) {
        boolean in = w > 0 && Ui.inside(mouseX, mouseY, x, y, w, h);
        focus(in);
        return in;
    }

    public boolean keyPressed(int key, int modifiers) {
        if (!focused) return false;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!value.isEmpty()) {
                    if (Screen.hasControlDown()) {
                        String t = value.stripTrailing();
                        int cut = t.lastIndexOf(' ');
                        value = cut < 0 ? "" : t.substring(0, cut + 1);
                    } else {
                        value = value.substring(0, value.length() - 1);
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> {
                focused = false;
                return true;
            }
            case GLFW.GLFW_KEY_V -> {
                if (Screen.hasControlDown()) {
                    String paste = StringUtil.filterText(Minecraft.getInstance().keyboardHandler.getClipboard());
                    set(value + paste);
                    return true;
                }
            }
            default -> {}
        }
        // every other key is the field's while it is typed in (letters arrive as characters)
        return true;
    }

    public boolean charTyped(char c) {
        if (!focused) return false;
        if (StringUtil.isAllowedChatCharacter(c)) set(value + c);
        return true;
    }
}
