package com.beyondsolo.client.monarch;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientEvents;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.skill.SkillEmblem;
import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.network.MonarchActionPayload;
import com.beyondsolo.skill.MonarchPowers;
import com.beyondsolo.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The throne's question: which crown the hunter will wear. Four crowns side by side, each with what its domain does,
 * its authority and a line of what it is; the one the hunter's class leans to is marked and picked to begin with. A
 * click (or 1 to 4) picks one, the button (or Enter) claims it. The world stays in view behind it: the hunter is at the
 * foot of the throne. Esc looks away; the throne waits (the System key brings the question back) until the System
 * picks for them.
 */
public class ThroneScreen extends Screen {
    private static final float W = 404, H = 248;
    private static final float CARD_W = 90, CARD_H = 162, CARD_GAP = 7.3f;
    private static final int VIOLET = 0xFFB57BFF;

    private static @Nullable Dominion leaning;
    /** Game time the System picks for them. */
    private static long until;
    private static boolean pending;

    private record Hit(float x, float y, float w, float h, Runnable run) {}

    private final List<Hit> hits = new ArrayList<>();
    private final double openedAt = Anim.now();
    private final float[] lift = new float[4];
    private Dominion selected;
    private double selectedAt = -10;
    private float scale = 1, ox, oy;
    private int hovered = -1;

    private ThroneScreen(Dominion start) {
        super(Component.translatable("ui.beyondsolo.monarch.throne.header"));
        this.selected = start;
    }

    // ================================================================ the question

    /** The throne asks: which crown? ({@code ticks}: how long it waits.) */
    static void open(Dominion lean, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        leaning = lean;
        until = (mc.level == null ? 0 : mc.level.getGameTime()) + ticks;
        pending = true;
        mc.setScreen(new ThroneScreen(lean));
        play(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 1f);
        play(SoundEvents.BEACON_AMBIENT, 0.7f, 1f);
    }

    /** Whether the throne still waits for an answer. */
    public static boolean pending() {
        return pending && ticksLeft() > 0;
    }

    private static long ticksLeft() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : until - mc.level.getGameTime();
    }

    /** The System key while the throne waits: the question again. Returns true if it was used. */
    public static boolean reopen() {
        Minecraft mc = Minecraft.getInstance();
        if (!pending() || leaning == null || mc.screen != null) return false;
        mc.setScreen(new ThroneScreen(leaning));
        return true;
    }

    /** The crown was taken (or picked by the System): the question goes. */
    static void closeIfOpen() {
        pending = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ThroneScreen) mc.setScreen(null);
    }

    static void reset() {
        pending = false;
        leaning = null;
    }

    private void claim() {
        pending = false;
        PacketDistributor.sendToServer(new MonarchActionPayload(MonarchActionPayload.CHOOSE, selected.ordinal()));
        play(SoundEvents.BEACON_POWER_SELECT, 0.6f, 1f);
        play(SoundEvents.END_PORTAL_FRAME_FILL, 0.8f, 0.8f);
        Minecraft.getInstance().setScreen(null);
    }

    private void select(Dominion d) {
        if (d == selected) return;
        selected = d;
        selectedAt = Anim.now();
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f + 0.15f * d.ordinal(), 0.8f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!pending()) onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the hall stays in view: only the edges darken, and the floor under the cards
        Shapes.vignette(0, 0, width, height, 0.35f, 1.4f).fill(0xB0030208).draw(g);
        Shapes.box(0, height * 0.45f, width, height * 0.55f).fillV(0x00000000, 0xA0020106).draw(g);
    }

    // ================================================================ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        hits.clear();
        float open = Anim.outCubic(Anim.progress(openedAt, 0.45f));
        scale = Math.min(1f, Math.min((width - 12) / W, (height - 12) / H));
        ox = (width - W * scale) / 2f;
        oy = (height - H * scale) / 2f + (1 - open) * 10;
        float mx = (mouseX - ox) / scale, my = (mouseY - oy) / scale;
        float old = Gfx.fade(open);
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(scale, scale, 1);
        float oldScale = SysFont.setContentScale(scale);

        header(g);
        hovered = -1;
        Dominion[] all = Dominion.values();
        float total = all.length * CARD_W + (all.length - 1) * CARD_GAP;
        float cx0 = (W - total) / 2f;
        for (int i = 0; i < all.length; i++) {
            float x = cx0 + i * (CARD_W + CARD_GAP);
            float y = 40;
            if (Ui.inside(mx, my, x, y, CARD_W, CARD_H)) hovered = i;
        }
        for (int i = 0; i < all.length; i++) {
            Dominion d = all[i];
            float appear = Anim.outBack(Anim.window(Anim.since(openedAt), 0.1f + i * 0.08f, 0.5f + i * 0.08f));
            if (appear <= 0) continue;
            float target = d == selected ? 1 : hovered == i ? 0.45f : 0;
            lift[i] = Anim.approach(lift[i], target, 10f);
            float x = cx0 + i * (CARD_W + CARD_GAP);
            float y = 40 + (1 - appear) * 14 - 5 * lift[i];
            float oa = Gfx.fade(Math.min(1, appear));
            card(g, d, x, y, i);
            Gfx.setAlpha(oa);
            final Dominion pick = d;
            hits.add(new Hit(x, 40, CARD_W, CARD_H, () -> select(pick)));
        }
        footer(g, mx, my);

        SysFont.setContentScale(oldScale);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    private void header(GuiGraphics g) {
        String head = Component.translatable("ui.beyondsolo.monarch.throne.header").getString().toUpperCase(Locale.ROOT);
        SysFont.spaced(g, head, W / 2f, 2, 12, 0xFFFFFFFF, true, 3.4f, 0.5f);
        float hw = SysFont.spacedWidth(head, 12, true, 3.4f);
        float line = 46 * Anim.outCubic(Anim.window(Anim.since(openedAt), 0.2f, 0.7f));
        Ui.divider(g, W / 2f - hw / 2f - 8 - line, 7, line, Gfx.alpha(VIOLET, 0.9f));
        Ui.divider(g, W / 2f + hw / 2f + 8, 7, line, Gfx.alpha(VIOLET, 0.9f));
        String sub = Component.translatable("ui.beyondsolo.monarch.throne.sub").getString();
        SysFont.draw(g, SysFont.fit(sub, 6f, false, W - 20), W / 2f, 19, 6f, SystemTheme.TEXT_SOFT, false, 0.5f);
        // how long the throne waits
        long left = Math.max(0, ticksLeft());
        int secs = (int) ((left + 19) / 20);
        String c = Component.translatable("ui.beyondsolo.monarch.throne.countdown", String.format(Locale.ROOT, "%d:%02d", secs / 60, secs % 60)).getString();
        float urgent = secs <= 10 ? Anim.pulse(0.5f) : 0;
        int cc = secs <= 10 ? SystemTheme.DANGER : SystemTheme.TEXT_DIM;
        SysFont.draw(g, c, W / 2f, 28.5f, 5f, Gfx.mix(cc, 0xFFFFFFFF, 0.2f * urgent), false, 0.5f);
    }

    /** One crown: its emblem, its name and title, what its domain does, its authority and a line of what it is. */
    private void card(GuiGraphics g, Dominion d, float x, float y, int i) {
        int c = Gfx.opaque(d.color());
        int light = Gfx.mix(c, 0xFFFFFFFF, 0.55f);
        boolean sel = d == selected;
        float k = lift[i];
        float justPicked = sel ? 1 - Anim.progress(selectedAt, 0.5f) : 0;
        float dim = sel ? 1 : 0.62f + 0.38f * k;
        float oldDim = Gfx.fade(dim);
        Shapes.box(x, y, CARD_W, CARD_H).radius(9)
                .fillV(Gfx.mix(0xF0120C22, Gfx.alpha(c, 0.94f), 0.12f + 0.12f * k), 0xF005040C)
                .border(sel ? 1.2f : 0.8f, Gfx.alpha(c, 0.45f + 0.5f * k), Gfx.alpha(c, 0.15f + 0.2f * k))
                .glow(4 + 9 * k + 8 * justPicked, Gfx.alpha(c, 0.12f + 0.3f * k + 0.3f * justPicked)).draw(g);
        if (sel) {
            Shapes.box(x, y, CARD_W, CARD_H).radius(9).fill(0x00000000).sheen(Anim.loop(2.6f) * 1.6f - 0.3f, 0.12f).draw(g);
        }
        float cx = x + CARD_W / 2f;
        // the crown's emblem
        float ey = y + 22;
        float er = 13;
        Shapes.circle(cx, ey, er * 1.9f).fill(0x00000000).glow(er * (0.6f + 0.6f * k), Gfx.alpha(c, 0.15f + 0.3f * k)).additive().draw(g);
        Shapes.circle(cx, ey, er).fillV(0xF0181030, 0xF0080512).border(1f, c, Gfx.fade(c, 0.4f)).draw(g);
        Shapes.ring(cx, ey, er * 1.22f, 0.7f).fill(Gfx.alpha(c, 0.4f + 0.45f * k)).dashes(16, 0.45f)
                .rotation(Anim.loop(16f) * (i % 2 == 0 ? 1 : -1)).draw(g);
        Shapes.icon(crownIcon(d), cx, ey, er * 1.3f).fillV(Gfx.mix(c, 0xFFFFFFFF, 0.8f), light)
                .glow(2 + 3 * k, Gfx.alpha(c, 0.7f)).draw(g);
        // the class leans here
        if (d == leaning) {
            String lean = Component.translatable("ui.beyondsolo.monarch.throne.leaning").getString().toUpperCase(Locale.ROOT);
            float lw = SysFont.width(lean, 4.2f, true) + 7 * 0.9f;
            Ui.chip(g, cx - lw / 2f, y - 3.5f, 7, lean, 4.2f, SystemTheme.GOLD, true);
        }
        // name and title
        String name = d.displayName().getString().toUpperCase(Locale.ROOT);
        SysFont.spaced(g, SysFont.fit(name, 8.5f, true, CARD_W - 8), cx, y + 41, 8.5f, light, true, 1.6f, 0.5f);
        SysFont.draw(g, SysFont.fit(d.title().getString(), 5f, false, CARD_W - 8), cx, y + 52, 5f, SystemTheme.TEXT_SOFT, false, 0.5f);
        Ui.divider(g, x + 8, y + 60, CARD_W - 16, Gfx.alpha(c, 0.5f));
        // the domain
        float ty = y + 64;
        SysFont.spaced(g, Component.translatable("ui.beyondsolo.monarch.throne.domain").getString().toUpperCase(Locale.ROOT), x + 7, ty, 4.2f,
                SystemTheme.TEXT_DIM, true, 1f, 0f);
        ty += 7;
        ty = lines(g, d.domainText(), x + 7, ty, 4.6f, SystemTheme.TEXT, CARD_W - 14, 3);
        // the authority
        ty = Math.max(ty + 3, y + 93);
        SysFont.spaced(g, Component.translatable("ui.beyondsolo.monarch.throne.authority").getString().toUpperCase(Locale.ROOT), x + 7, ty, 4.2f,
                SystemTheme.TEXT_DIM, true, 1f, 0f);
        ty += 7;
        Skill auth = MonarchPowers.authority(d);
        SkillEmblem.draw(g, auth, x + 11, ty + 2.6f, 3.6f, 0.4f * k, false);
        SysFont.draw(g, SysFont.fit(auth.displayName().getString(), 5.4f, true, CARD_W - 24), x + 17, ty, 5.4f, light, true, 0f);
        ty += 8;
        ty = lines(g, auth.flavour(), x + 7, ty, 4.4f, SystemTheme.TEXT_SOFT, CARD_W - 14, 3);
        // what it is
        float ly = y + CARD_H - 27;
        Ui.divider(g, x + 14, ly - 3, CARD_W - 28, Gfx.alpha(c, 0.25f));
        lines(g, d.lore(), x + 7, ly, 4.3f, Gfx.mix(c, SystemTheme.TEXT_SOFT, 0.55f), CARD_W - 14, 3);
        // its number key
        Ui.keycap(g, x + CARD_W - 13, y + 5, 8, String.valueOf(i + 1), sel ? c : SystemTheme.TEXT_DIM);
        Gfx.setAlpha(oldDim);
    }

    /** Wrapped text, at most {@code max} lines (the last cut with an ellipsis). Returns where the next line would go. */
    private static float lines(GuiGraphics g, Component text, float x, float y, float size, int color, float width, int max) {
        List<SysFont.Line> wrapped = SysFont.wrap(text, size, false, width);
        for (int i = 0; i < Math.min(max, wrapped.size()); i++) {
            SysFont.Line l = wrapped.get(i);
            if (i == max - 1 && wrapped.size() > max) {
                // the last line shown, cut short with "..."
                SysFont.draw(g, SysFont.fit(asString(l) + " " + asString(wrapped.get(i + 1)), size, false, width), x, y, size, color, false, 0f);
            } else {
                SysFont.draw(g, l, x, y, color, 0f);
            }
            y += size * SysFont.LINE;
        }
        return y;
    }

    private static String asString(SysFont.Line l) {
        StringBuilder sb = new StringBuilder();
        l.text().accept((index, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        return sb.toString();
    }

    /** The claim button, and how to answer from the keyboard. */
    private void footer(GuiGraphics g, float mx, float my) {
        int c = Gfx.opaque(selected.color());
        String every = Component.translatable("ui.beyondsolo.monarch.throne.every").getString();
        SysFont.draw(g, SysFont.fit(every, 5f, false, W - 20), W / 2f, 40 + CARD_H + 6, 5f, SystemTheme.TEXT_SOFT, false, 0.5f);
        String label = Component.translatable("ui.beyondsolo.monarch.throne.claim", selected.displayName()).getString().toUpperCase(Locale.ROOT);
        float size = 6.6f;
        float bw = SysFont.spacedWidth(label, size, true, 1.4f) + 34;
        float bh = 17;
        float bx = W / 2f - bw / 2f, by = 40 + CARD_H + 16;
        boolean hover = Ui.inside(mx, my, bx, by, bw, bh);
        float pulse = Anim.pulse(1.6f);
        Shapes.pill(bx, by, bw, bh).fillV(Gfx.alpha(c, hover ? 0.42f : 0.28f), Gfx.alpha(c, hover ? 0.2f : 0.1f))
                .border(1f, Gfx.mix(c, 0xFFFFFFFF, 0.3f), Gfx.alpha(c, 0.5f)).glow(5 + 4 * pulse + (hover ? 4 : 0), Gfx.alpha(c, 0.35f + 0.15f * pulse))
                .sheen(Anim.loop(2.2f) * 1.6f - 0.3f, 0.18f).draw(g);
        Shapes.icon(Icons.CROWN, bx + 13, by + bh / 2f, 9).fillV(0xFFFFFFFF, Gfx.mix(c, 0xFFFFFFFF, 0.5f)).glow(2, Gfx.alpha(c, 0.7f)).draw(g);
        SysFont.spaced(g, label, bx + 21, SysFont.centerY(by, bh, size), size, 0xFFFFFFFF, true, 1.4f, 0f);
        hits.add(new Hit(bx, by, bw, bh, this::claim));
        String keys = Component.translatable("ui.beyondsolo.monarch.throne.keys").getString();
        SysFont.draw(g, SysFont.fit(keys, 4.6f, false, W - 20), W / 2f, by + bh + 5, 4.6f, SystemTheme.TEXT_DIM, false, 0.5f);
    }

    private static ResourceLocation crownIcon(Dominion d) {
        return BeyondSolo.id("textures/gui/icon/monarch_" + d.id() + ".png");
    }

    // ================================================================ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float mx = (float) ((mouseX - ox) / scale), my = (float) ((mouseY - oy) / scale);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!Ui.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            hit.run().run();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4) {
            select(Dominion.byIndex(key - GLFW.GLFW_KEY_1));
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            int n = Dominion.values().length;
            select(Dominion.byIndex((selected.ordinal() + (key == GLFW.GLFW_KEY_LEFT ? n - 1 : 1)) % n));
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            claim();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        super.onClose();
        if (pending()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("ui.beyondsolo.monarch.throne.looked_away",
                        ClientEvents.OPEN_SYSTEM.getTranslatedKeyMessage()), true);
            }
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ================================================================ the reminder

    /** Over the HUD while the throne waits and its question is closed: how to bring it back. */
    static void renderPrompt(GuiGraphics g, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!pending() || mc.screen instanceof ThroneScreen) return;
        String text = Component.translatable("ui.beyondsolo.monarch.throne.waiting").getString();
        String key = ClientEvents.OPEN_SYSTEM.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        float size = 6.4f;
        float tw = SysFont.width(text, size, true);
        float kw = Ui.keycapWidth(11, key);
        float pw = 14 + 10 + tw + 6 + kw + 10;
        float px = w / 2f - pw / 2f, py = h * 0.68f;
        float pulse = Anim.pulse(1.4f);
        Shapes.pill(px, py, pw, 17).fill(Gfx.alpha(0xFF0A0614, 0.88f)).border(0.8f, Gfx.alpha(VIOLET, 0.6f + 0.3f * pulse))
                .glow(5 + 3 * pulse, Gfx.alpha(VIOLET, 0.3f)).draw(g);
        Shapes.icon(Icons.CROWN, px + 12, py + 8.5f, 9).fill(Gfx.mix(VIOLET, 0xFFFFFFFF, 0.4f)).glow(2, Gfx.alpha(VIOLET, 0.6f)).draw(g);
        SysFont.draw(g, text, px + 22, SysFont.centerY(py, 17, size), size, SystemTheme.TEXT, true, 0f);
        Ui.keycap(g, px + 22 + tw + 6, py + 3, 11, key, VIOLET);
    }
}
