package com.beyondsolo.client.reward;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.network.RewardBoxPickPayload;
import com.beyondsolo.reward.BoxKind;
import com.beyondsolo.reward.BoxReward;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * A blessed box's pick of three: three cards rise out of the box's light, each one of the things it could give (what
 * kind of thing, drawn big, its name and the rest of what comes with it; hover the big one to read it). Click a card
 * or press 1, 2 or 3 to take it; Esc keeps the box sealed, with the same three inside.
 */
public class BlessedBoxScreen extends Screen {
    private static final int GOLD = BoxKind.BLESSED.color();

    private final int hand;
    private final long seed;
    private final PlayerRank rank;
    private final List<BoxReward> offers;
    private final double openedAt = Anim.now();
    private final double[] hoverAt = new double[3];
    private int hovered = -1;
    private int picked = -1;
    private double pickedAt;

    BlessedBoxScreen(int hand, long seed, PlayerRank rank, List<BoxReward> offers) {
        super(Component.translatable("box.beyondsolo.pick.title"));
        this.hand = hand;
        this.seed = seed;
        this.rank = rank;
        this.offers = offers;
    }

    @Override
    protected void init() {
        super.init();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3f, 0.6f));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.35f));
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
        // the card taken flashes, then the window goes and the box's moment plays
        if (picked >= 0 && Anim.since(pickedAt) > 0.45f) onClose();
    }

    private float cardW() {
        return Math.min(124, (width - 48 - 20) / 3f);
    }

    /** As tall as the fullest card needs, never taller than the window allows. */
    private float cardH() {
        float cw = cardW();
        float need = 0;
        for (BoxReward r : offers) need = Math.max(need, needed(r, cw));
        return Math.min(Math.max(132, need), Math.min(190, height - 96));
    }

    /** The height one offer's card needs: the chip, the big item, its name, what it is for, the rest, the key. */
    private static float needed(BoxReward r, float cw) {
        float h = 78;
        h += Math.min(2, SysFont.wrap(Component.literal(BoxOverlay.name(r)), 7.5f, true, cw - 14).size()) * 7.5f * SysFont.LINE + 9;
        h += Math.min(3, SysFont.wrap(r.category().hint(), 6f, false, cw - 22).size()) * 6f * SysFont.LINE + 4;
        h += Math.min(3, Math.max(0, r.items().size() - 1)) * 9;
        if (!r.items().isEmpty() && r.gold() > 0) h += 9;
        return h + 26;
    }

    private float cardX(int i) {
        float cw = cardW();
        return Math.round(width / 2f - (cw * 3 + 20) / 2f + i * (cw + 10));
    }

    private float cardY() {
        return Math.round(height / 2f - cardH() / 2f + 8);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        float t = Anim.since(openedAt);
        ItemStack tooltip = ItemStack.EMPTY;
        try (var batch = Shapes.batch(g)) {
            float old = Gfx.setAlpha(Math.min(1f, t / 0.15f));
            Shapes.vignette(0, 0, width, height, 0.15f, 1.5f).fill(0xC0040912).draw(g);
            Shapes.vignette(0, 0, width, height, 0.3f, 1.4f).fill(Gfx.alpha(GOLD, 0.12f + 0.04f * Anim.pulse(2.5f))).draw(g);
            Gfx.setAlpha(old);
            header(g, t);
            hovered = -1;
            for (int i = 0; i < offers.size() && i < 3; i++) {
                ItemStack over = card(g, i, t, mouseX, mouseY);
                if (!over.isEmpty()) tooltip = over;
            }
            footer(g, t);
        }
        if (!tooltip.isEmpty() && picked < 0) g.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private void header(GuiGraphics g, float t) {
        float k = Anim.outCubic(Anim.window(t, 0f, 0.35f));
        float cx = width / 2f;
        float y = cardY() - 40 - (1 - k) * 6;
        float old = Gfx.fade(k);
        String head = Component.translatable("box.beyondsolo.header.blessed").getString();
        SysFont.spaced(g, head, cx, y, 8f, Gfx.mix(GOLD, 0xFFFFFFFF, 0.45f), true, 3f, 0.5f);
        float hw = SysFont.spacedWidth(head, 8f, true, 3f);
        float line = 50 * k;
        Ui.divider(g, cx - hw / 2f - 8 - line, y + 3f, line, Gfx.alpha(GOLD, 0.9f));
        Ui.divider(g, cx + hw / 2f + 8, y + 3f, line, Gfx.alpha(GOLD, 0.9f));
        String rankText = Component.translatable("box.beyondsolo.rank", rank.displayName()).getString().toUpperCase(Locale.ROOT);
        String sub = Component.translatable("box.beyondsolo.pick.sub").getString();
        float rw = SysFont.width(rankText, 5.6f, true) + 9;
        float sw = SysFont.width(sub, 7f, false);
        float sx = cx - (rw + 6 + sw) / 2f;
        Ui.chip(g, sx, y + 13, 10, rankText, 5.6f, GOLD, true);
        SysFont.text(g, sub, sx + rw + 6, y + 14.5f, 7f, SystemTheme.TEXT_SOFT);
        Gfx.setAlpha(old);
    }

    /** One offer. Returns the item under the mouse, for its tooltip. */
    private ItemStack card(GuiGraphics g, int i, float t, int mouseX, int mouseY) {
        BoxReward r = offers.get(i);
        float in = Anim.outBackStrong(Anim.window(t, 0.15f + i * 0.12f, 0.6f + i * 0.12f));
        if (in <= 0.01f) return ItemStack.EMPTY;
        float cw = cardW(), ch = cardH();
        float x = cardX(i), y = cardY() + (1 - in) * 24;
        boolean over = picked < 0 && Ui.inside(mouseX, mouseY, x, y, cw, ch);
        if (over) {
            if (hovered != i && hoverAt[i] <= 0) hoverAt[i] = Anim.now();
            hovered = i;
        } else {
            hoverAt[i] = 0;
        }
        float lift = over ? Anim.outCubic(Anim.progress(hoverAt[i], 0.18f)) : 0;
        float fade = 1f;
        if (picked >= 0) fade = picked == i ? 1f : 1 - Anim.clamp01(Anim.since(pickedAt) / 0.2f);
        float old = Gfx.fade(Math.min(1f, in) * fade);
        // items are drawn as items, deaf to the fade: they shrink in and out with their card instead
        float shown = Math.min(1f, in) * fade;
        y -= lift * 4;
        int accent = picked == i ? 0xFFFFFFFF : GOLD;
        Ui.panel(g, x, y, cw, ch, 9, GOLD, 0.35f + 0.5f * lift);
        Shapes.box(x, y, cw, ch).radius(9).fill(0).border(0.9f + lift * 0.5f, Gfx.alpha(accent, 0.45f + 0.45f * lift), Gfx.alpha(GOLD, 0.15f))
                .glow(lift * 8, Gfx.alpha(GOLD, 0.35f)).draw(g);
        if (picked == i) {
            float f = 1 - Anim.clamp01(Anim.since(pickedAt) / 0.4f);
            Shapes.box(x, y, cw, ch).radius(9).fill(Gfx.alpha(0xFFFFFFFF, 0.35f * f)).additive().draw(g);
        }
        float cx = x + cw / 2f;
        // what kind of thing it is
        String cat = r.category().displayName().getString().toUpperCase(Locale.ROOT);
        float catW = SysFont.width(cat, 5.6f, true) + 9;
        Ui.chip(g, cx - catW / 2f, y + 8, 10, cat, 5.6f, Gfx.mix(GOLD, SystemTheme.TEXT_SOFT, 0.4f), false);
        // drawn big, in the light
        float iy = y + 50;
        Shapes.circle(cx, iy, 22).fill(0).glow(14, Gfx.alpha(GOLD, 0.22f + 0.2f * lift)).additive().draw(g);
        Shapes.ring(cx, iy, 21, 0.6f).fill(Gfx.alpha(GOLD, 0.5f)).dashes(16, 0.5f).rotation(Anim.loop(14f) + i * 0.3f).draw(g);
        ItemStack lead = r.lead();
        ItemStack hover = ItemStack.EMPTY;
        if (!lead.isEmpty()) {
            float s = (2.4f + 0.2f * lift) * shown;
            if (s > 0.1f) BoxOverlay.item(g, lead, cx, iy, s);
            if (over && Ui.inside(mouseX, mouseY, cx - 8 * s, iy - 8 * s, 16 * s, 16 * s)) hover = lead;
        } else {
            boolean stats = r.statPoints() > 0;
            int c = stats ? SystemTheme.ACCENT_BRIGHT : SystemTheme.GOLD;
            Shapes.icon(stats ? com.beyondsolo.client.gfx.Icons.STAR : com.beyondsolo.client.gfx.Icons.COINS, cx, iy, 26)
                    .fillV(0xFFFFFFFF, c).glow(4, Gfx.alpha(c, 0.7f)).draw(g);
        }
        // its name, and the rest of what comes with it
        float ty = iy + 28;
        List<SysFont.Line> name = SysFont.wrap(Component.literal(BoxOverlay.name(r)), 7.5f, true, cw - 14);
        int nameColor = BoxOverlay.nameColor(r);
        for (int n = 0; n < name.size() && n < 2; n++) {
            SysFont.draw(g, name.get(n), cx, ty, nameColor, 0.5f);
            ty += 7.5f * SysFont.LINE;
        }
        ty += 4;
        Ui.divider(g, x + 14, ty, cw - 28, Gfx.alpha(GOLD, 0.3f));
        ty += 5;
        // what this kind of thing is for
        List<SysFont.Line> hint = SysFont.wrap(r.category().hint(), 6f, false, cw - 22);
        for (int n = 0; n < hint.size() && n < 3; n++) {
            SysFont.draw(g, hint.get(n), cx, ty, SystemTheme.TEXT_DIM, 0.5f);
            ty += 6f * SysFont.LINE;
        }
        ty += 4;
        List<ItemStack> items = r.items();
        for (int n = 1; n < items.size() && n <= 3; n++) {
            ItemStack s = items.get(n);
            String txt = SysFont.fit(s.getCount() + "× " + s.getHoverName().getString(), 6f, false, cw - 30);
            if (shown > 0.2f) BoxOverlay.item(g, s, x + 15, ty + 3, 0.55f * shown);
            SysFont.text(g, txt, x + 23, ty, 6f, SystemTheme.TEXT_SOFT);
            if (over && Ui.inside(mouseX, mouseY, x + 10, ty - 2, cw - 20, 9)) hover = s;
            ty += 9;
        }
        if (!items.isEmpty() && r.gold() > 0) {
            SysFont.text(g, "+" + BoxOverlay.gold(r.gold()), x + 12, ty, 6f, SystemTheme.GOLD);
        }
        // how to take it
        String key = String.valueOf(i + 1);
        String take = Component.translatable("box.beyondsolo.pick.take").getString();
        float kw = Ui.keycapWidth(10, key);
        float tw = SysFont.width(take, 6f, true);
        float bx = cx - (kw + 4 + tw) / 2f;
        float by = y + ch - 17;
        Ui.keycap(g, bx, by, 10, key, over ? 0xFFFFFFFF : GOLD);
        SysFont.draw(g, take, bx + kw + 4, by + 2.2f, 6f, over ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, 0f);
        Gfx.setAlpha(old);
        return hover;
    }

    private void footer(GuiGraphics g, float t) {
        float k = Anim.outCubic(Anim.window(t, 0.6f, 0.9f));
        if (k <= 0) return;
        float old = Gfx.fade(k * (picked >= 0 ? 0 : 1));
        String hint = Component.translatable("box.beyondsolo.pick.keep").getString();
        String key = Component.translatable("key.keyboard.escape").getString();
        float kw = Ui.keycapWidth(10, key);
        float tw = SysFont.width(hint, 6.5f, false);
        float x = width / 2f - (kw + 5 + tw) / 2f;
        float y = cardY() + cardH() + 10;
        Ui.keycap(g, x, y, 10, key, SystemTheme.TEXT_DIM);
        SysFont.text(g, hint, x + kw + 5, y + 2, 6.5f, SystemTheme.TEXT_DIM);
        Gfx.setAlpha(old);
    }

    private void take(int i) {
        if (picked >= 0 || i < 0 || i >= offers.size()) return;
        picked = i;
        pickedAt = Anim.now();
        PacketDistributor.sendToServer(new RewardBoxPickPayload(hand, seed, i));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.4f, 0.8f));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && picked < 0 && Anim.since(openedAt) > 0.4f) {
            for (int i = 0; i < offers.size() && i < 3; i++) {
                if (Ui.inside(mouseX, mouseY, cardX(i), cardY() - 4, cardW(), cardH() + 4)) {
                    take(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (picked < 0 && Anim.since(openedAt) > 0.4f && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_3) {
            take(keyCode - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
