package com.beyondsolo.client.association;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.network.AssociationActionPayload;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the office's and the broker's windows share: the frame that opens out of a line of light, the one at the desk
 * on the left (their portrait over a turning ring, their name), buttons and chips, and the answer to the last button
 * under the window. The window closes when the hunter walks away from the desk.
 */
abstract class AssociationWindow extends Screen {
    static final int STEEL = 0xFF8FB4FF;
    static final int GOLD = 0xFFFFC247;
    static final int VIOLET = 0xFFB98BFF;
    static final int AMBER = 0xFFFF9F43;

    record Hit(float x, float y, float w, float h, Runnable run) {}

    protected final float w, h, leftW;
    protected final List<Hit> hits = new ArrayList<>();
    protected Component hint;
    protected final double openedAt = Anim.now();

    AssociationWindow(Component title, float w, float h, float leftW) {
        super(title);
        this.w = w;
        this.h = h;
        this.leftW = leftW;
    }

    /** What the server last sent for this window. */
    abstract CompoundTag data();

    abstract int accent();

    /** The right side: tabs and pages. */
    abstract void content(GuiGraphics g, float x, float y, float cw, float ch, int mouseX, int mouseY, float t);

    /** Under the portrait: what this desk shows of the hunter. */
    abstract void under(GuiGraphics g, float x, float y, float lw, float bottom, float t);

    /** The small line under the name ("Association officer"). */
    abstract Component role();

    @Override
    protected void init() {
        super.init();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.1f, 0.8f));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(partialTick);
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        int id = data().getInt("entity");
        if (id == 0 || mc.level == null || mc.player == null) return;
        Entity e = mc.level.getEntity(id);
        if (e == null || !e.isAlive() || e.distanceToSqr(mc.player) > 10 * 10) onClose();
    }

    @Override
    public void removed() {
        AssociationClient.send(AssociationActionPayload.CLOSE);
        super.removed();
    }

    protected float left() {
        return Math.round((width - w) / 2f);
    }

    protected float top() {
        return Math.round((height - h) / 2f);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = Anim.since(openedAt);
        float open = Anim.outCubic(Anim.window(t, 0.05f, 0.4f));
        renderBackground(g, mouseX, mouseY, partialTick);
        hits.clear();
        hint = null;
        // the window is drawn in a few draw calls (see Shapes.batch): flush before clipping or drawing a mob
        try (var batch = Shapes.batch(g)) {
            frame(g, mouseX, mouseY, t, open);
        }
    }

    private void frame(GuiGraphics g, int mouseX, int mouseY, float t, float open) {
        float old = Gfx.setAlpha(Math.min(1f, t / 0.12f));
        Shapes.vignette(0, 0, width, height, 0.15f, 1.5f).fill(0xC0040912).draw(g);
        Gfx.setAlpha(old);
        float x = left(), y = top();
        float cx = x + w / 2f, cy = y + h / 2f;
        int accent = accent();
        // the line of light the window opens out of
        float line = Anim.outCubic(Anim.window(t, 0f, 0.18f));
        if (open < 0.98f && line > 0) {
            float lw = w * line;
            float fade = 1 - Anim.window(t, 0.25f, 0.45f);
            Shapes.pill(cx - lw / 2f, cy - 0.9f, lw, 1.8f).fill(Gfx.alpha(Gfx.mix(accent, 0xFFFFFFFF, 0.6f), fade))
                    .glow(8, Gfx.alpha(accent, 0.8f * fade)).additive().draw(g);
        }
        if (open <= 0.01f) return;
        float shown = h * open;
        Shapes.flush(g);
        g.enableScissor(0, Math.max(0, Math.round(cy - shown / 2f) - 16), width, Math.min(height, Math.round(cy + shown / 2f) + 16));
        Ui.panel(g, x, y, w, h, 12, accent, 1f);
        Shapes.box(x, y, w, h).radius(12).fill(0x00000000).sheen(Anim.window(t, 0.2f, 1.0f) * 1.6f - 0.3f, t < 1.1f ? 0.14f : 0f).draw(g);
        portrait(g, x, y, t, mouseX, mouseY);
        content(g, x + leftW + 10, y + 10, w - leftW - 20, h - 20, mouseX, mouseY, t);
        Shapes.flush(g);
        g.disableScissor();
        toast(g, x, y + h + 4, w);
    }

    /** The one at the desk: portrait over a turning ring, name, role, then what {@link #under} draws. */
    private void portrait(GuiGraphics g, float x, float y, float t, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        int accent = accent();
        float px = x + 4 + leftW / 2f;
        float ringY = y + 88;
        Ui.card(g, x + 8, y + 8, leftW - 8, h - 16, 9, accent, 0.25f);
        float rk = Anim.outCubic(Anim.window(t, 0.15f, 0.7f));
        float pulse = Anim.pulse(2.4f);
        Shapes.circle(px, ringY - 26, 40).fill(0x00000000).glow(28, Gfx.alpha(accent, (0.16f + 0.08f * pulse) * rk)).draw(g);
        Shapes.ring(px, ringY, 34 * rk, 0.9f).fill(Gfx.alpha(accent, 0.7f)).progress(rk).rotation(-0.25f).glow(3, Gfx.alpha(accent, 0.5f)).draw(g);
        Shapes.ring(px, ringY, 40, 0.6f).fill(Gfx.alpha(accent, 0.45f * rk)).dashes(32, 0.35f).rotation(Anim.loop(22f)).draw(g);
        Shapes.ring(px, ringY, 28, 0.6f).fill(Gfx.alpha(GOLD, 0.3f * rk)).dashes(12, 0.6f).rotation(-Anim.loop(14f)).draw(g);
        Entity e = mc.level == null ? null : mc.level.getEntity(data().getInt("entity"));
        if (e instanceof LivingEntity living && rk > 0.2f) {
            int x1 = Math.round(px - 36), y1 = Math.round(y + 20);
            Shapes.flush(g);
            float o = Gfx.fade(Math.min(1f, (rk - 0.2f) * 2));
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x1, y1, x1 + 72, Math.round(ringY + 8), 34, 0.0625f, mouseX, mouseY, living);
            Gfx.setAlpha(o);
        } else if (rk > 0.2f) {
            // opened by a command: the association's seal stands in
            Shapes.icon(com.beyondsolo.client.gfx.Icons.SEAL, px, ringY - 22, 34).fillV(Gfx.mix(accent, 0xFFFFFFFF, 0.7f), accent)
                    .glow(6, Gfx.alpha(accent, 0.5f)).draw(g);
        }
        float k = Anim.outCubic(Anim.window(t, 0.3f, 0.65f));
        float o = Gfx.fade(k);
        String name = SysFont.fit(AssociationClient.text(data(), "name").getString(), 8.5f, true, leftW - 18);
        SysFont.draw(g, name, px, y + 112, 8.5f, SystemTheme.TEXT, true, 0.5f);
        String role = role().getString();
        float rw = SysFont.spacedWidth(role, 4.6f, true, 1.1f) + 12;
        Shapes.pill(px - rw / 2f, y + 124, rw, 10).fill(Gfx.alpha(accent, 0.18f)).border(0.6f, Gfx.alpha(accent, 0.6f)).draw(g);
        SysFont.spaced(g, role, px, SysFont.centerY(y + 124, 10, 4.6f), 4.6f, Gfx.mix(accent, 0xFFFFFFFF, 0.55f), true, 1.1f, 0.5f);
        under(g, x + 14, y + 140, leftW - 20, y + h - 14, t);
        Gfx.setAlpha(o);
    }

    // ------------------------------------------------------------------ pieces

    /** A button; {@code run} null draws it disabled. */
    protected void button(GuiGraphics g, float x, float y, float bw, float bh, String label, ResourceLocation icon, int color, int mouseX, int mouseY,
                          Component tip, Runnable run) {
        boolean hover = Ui.inside(mouseX, mouseY, x, y, bw, bh);
        boolean enabled = run != null;
        int c = enabled ? color : 0xFF56657A;
        Shapes.pill(x, y, bw, bh).fillV(Gfx.alpha(c, hover && enabled ? 0.42f : 0.22f), Gfx.alpha(Gfx.shade(c, -0.4f), hover && enabled ? 0.32f : 0.14f))
                .border(0.8f, Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.25f), hover ? 1f : 0.7f), Gfx.alpha(c, 0.3f))
                .glow(hover && enabled ? 6 : enabled ? 3 : 0, Gfx.alpha(c, 0.35f)).draw(g);
        float size = Math.min(5.4f, bh * 0.42f);
        String text = SysFont.fit(label, size, true, bw - (icon != null ? 16 : 8));
        float lw = SysFont.spacedWidth(text, size, true, 0.8f);
        float iconW = icon != null ? 8 : 0;
        float lx = x + (bw - lw - iconW) / 2f;
        int tc = enabled ? SystemTheme.TEXT : SystemTheme.TEXT_DIM;
        if (icon != null) Shapes.icon(icon, lx + 3, y + bh / 2f, size + 1.2f).fill(tc).draw(g);
        SysFont.spaced(g, text, lx + iconW, SysFont.centerY(y, bh, size), size, tc, true, 0.8f, 0f);
        if (hover && tip != null) hint = tip;
        if (enabled) hits.add(new Hit(x, y, bw, bh, run));
    }

    /** A small coloured tag; returns its width. */
    protected static float tag(GuiGraphics g, float x, float y, String text, int color, boolean filled) {
        return Ui.chip(g, x, y, 7.5f, text, 4.1f, color, filled);
    }

    protected static String gold(long amount) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(amount);
    }

    protected static PlayerRank rank(int i) {
        return PlayerRank.values()[Mth.clamp(i, 0, PlayerRank.values().length - 1)];
    }

    /** "12:30" for a clock in ticks. */
    protected static String clock(long ticks) {
        long s = Math.max(0, ticks) / 20;
        return s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** Wrapped text; returns the y under it. */
    protected static float para(GuiGraphics g, Component text, float x, float y, float maxW, float size, int color) {
        for (SysFont.Line l : SysFont.wrap(text, size, false, maxW)) {
            SysFont.draw(g, l, x, y, color, 0f);
            y += size * SysFont.LINE;
        }
        return y;
    }

    /** A paragraph centred on {@code midY} (one line or several). */
    protected static void paraMid(GuiGraphics g, Component text, float x, float midY, float maxW, float size, int color) {
        List<SysFont.Line> lines = SysFont.wrap(text, size, false, maxW);
        float block = (lines.size() - 1) * size * SysFont.LINE + SysFont.CAP * size;
        float y = midY - block / 2f;
        for (SysFont.Line l : lines) {
            SysFont.draw(g, l, x, y, color, 0f);
            y += size * SysFont.LINE;
        }
    }

    /** The answer to the last button, or what the thing under the mouse does. */
    private void toast(GuiGraphics g, float x, float y, float tw) {
        float since = AssociationClient.sinceOutcome();
        Component text = null;
        int color = SystemTheme.TEXT_SOFT;
        float k = 1;
        if (since < 4.5f && !AssociationClient.outcome().getString().isEmpty()) {
            text = AssociationClient.outcome();
            color = AssociationClient.outcomeOk() ? SystemTheme.GOOD : SystemTheme.DANGER;
            k = 1 - Anim.window(since, 3.9f, 4.5f);
        } else if (hint != null) {
            text = hint;
        }
        if (text == null || k <= 0.01f) return;
        String line = SysFont.fit(text.getString(), 5.4f, false, tw - 30);
        float lw = SysFont.width(line, 5.4f, false) + 16;
        float tx = x + (tw - lw) / 2f;
        float o = Gfx.fade(k);
        Shapes.pill(tx, y, lw, 11).fill(Gfx.alpha(0xFF060A14, 0.9f)).border(0.6f, Gfx.alpha(color, 0.7f)).glow(4, Gfx.alpha(color, 0.25f)).draw(g);
        SysFont.draw(g, line, tx + lw / 2f, SysFont.centerY(y, 11, 5.4f), 5.4f, Gfx.mix(color, 0xFFFFFFFF, 0.3f), false, 0.5f);
        Gfx.setAlpha(o);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!Ui.inside(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            hit.run().run();
            SystemScreen.playClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
