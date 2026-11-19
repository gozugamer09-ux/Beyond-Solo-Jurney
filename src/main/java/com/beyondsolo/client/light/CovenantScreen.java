package com.beyondsolo.client.light;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.network.LightActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Envoy of Dawn speaks: its words, one line after another, low on the screen while the chapel stays in view, and
 * the hunter's answer. <b>Swear to the light</b> and the System will bind to them in the light instead of the Arbiter's
 * dark; <b>Turn away</b> and the Envoy lets them go (the way back stays open while the temple stands). What swearing
 * means is said plainly before they choose. Esc says nothing at all.
 */
public class CovenantScreen extends Screen {
    private static final int GOLD = LightHud.GOLD;
    private static final float W = 360;

    private record Hit(float x, float y, float w, float h, Runnable run) {}

    private final List<Hit> hits = new ArrayList<>();
    private final int envoy;
    private final boolean back;
    private final double openedAt = Anim.now();
    private final List<String> lines = new ArrayList<>();
    private float scale = 1, ox, oy;
    private boolean answered;

    private CovenantScreen(int envoy, boolean back) {
        super(Component.translatable("ui.beyondsolo.light.covenant.name"));
        this.envoy = envoy;
        this.back = back;
        if (back) lines.add(tr("back"));
        for (int i = 1; i <= 4; i++) lines.add(tr("line" + i));
    }

    /** The Envoy spoke to the hunter ({@code back}: they turned it away before). */
    static void open(int envoy, boolean back) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof CovenantScreen) return;
        mc.setScreen(new CovenantScreen(envoy, back));
        play(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, 1f);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("ui.beyondsolo.light.covenant." + key, args).getString();
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void answer(int value) {
        if (answered) return;
        answered = true;
        PacketDistributor.sendToServer(new LightActionPayload(LightActionPayload.COVENANT, value));
        if (value == 1) {
            play(SoundEvents.BEACON_POWER_SELECT, 1.2f, 1f);
            play(SoundEvents.BELL_RESONATE, 0.8f, 1f);
        } else {
            play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1f);
        }
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(envoy);
        // walked away from it, or it went back up into the light
        if (mc.player == null || e == null || !e.isAlive() || mc.player.distanceTo(e) > 11) onClose();
    }

    /** Seconds since it began to speak at which line {@code i} starts, typed at ~45 letters a second. */
    private float lineStart(int i) {
        float t = 0.5f;
        for (int k = 0; k < i; k++) t += lines.get(k).length() / 45f + 0.45f;
        return t;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the chapel stays in view: the edges darken warm, the foot of the screen more, where the words are
        Shapes.vignette(0, 0, width, height, 0.4f, 1.4f).fill(0x90060402).draw(g);
        Shapes.box(0, height * 0.5f, width, height * 0.5f).fillV(0x00000000, 0xC0060402).draw(g);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        hits.clear();
        float t = Anim.since(openedAt);
        float open = Anim.outCubic(Anim.window(t, 0, 0.5f));
        float H = 150;
        scale = Math.min(1f, Math.min((width - 12) / W, (height - 12) / H));
        ox = (width - W * scale) / 2f;
        oy = height - (H + 10) * scale + (1 - open) * 8;
        float mx = (mouseX - ox) / scale, my = (mouseY - oy) / scale;
        float old = Gfx.fade(open);
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(scale, scale, 1);
        float oldScale = SysFont.setContentScale(scale);

        // who speaks: its name between two lines of light, a small sun turning
        String name = getTitle().getString().toUpperCase(Locale.ROOT);
        float nw = SysFont.spacedWidth(name, 8.5f, true, 3f);
        SysFont.spaced(g, name, W / 2f, 0, 8.5f, 0xFFFFFFFF, true, 3f, 0.5f);
        float line = 70 * Anim.outCubic(Anim.window(t, 0.15f, 0.8f));
        Ui.divider(g, W / 2f - nw / 2f - 10 - line, 4.5f, line, Gfx.alpha(GOLD, 0.9f));
        Ui.divider(g, W / 2f + nw / 2f + 10, 4.5f, line, Gfx.alpha(GOLD, 0.9f));
        Shapes.icon(Icons.SPARKLE, W / 2f - nw / 2f - 6, 4.5f, 6).fill(GOLD).glow(3, Gfx.alpha(GOLD, 0.7f)).rotation(Anim.loop(6f)).draw(g);
        Shapes.icon(Icons.SPARKLE, W / 2f + nw / 2f + 6, 4.5f, 6).fill(GOLD).glow(3, Gfx.alpha(GOLD, 0.7f)).rotation(-Anim.loop(6f)).draw(g);

        // its words, typed one line after another
        float y = 16;
        for (int i = 0; i < lines.size(); i++) {
            float from = lineStart(i);
            if (t < from) break;
            boolean first = back && i == 0;
            float size = first ? 7.2f : 7f;
            int color = first ? Gfx.mix(GOLD, 0xFFFFFFFF, 0.3f) : 0xFFF4EEDF;
            List<SysFont.Line> wrapped = SysFont.wrap(Component.literal(lines.get(i)), size, false, W - 20);
            int total = 0;
            for (SysFont.Line l : wrapped) total += SysFont.length(l);
            int shown = Math.min(total, Math.round((t - from) * 45f));
            for (SysFont.Line l : wrapped) {
                int n = SysFont.length(l);
                int here = Math.max(0, Math.min(n, shown));
                shown -= n;
                if (here <= 0) break;
                SysFont.Line part = here >= n ? l : SysFont.truncate(l, here);
                SysFont.draw(g, part, W / 2f - l.width() / 2f, y, color, 0f);
                y += size * SysFont.LINE;
                if (here < n) break;
            }
            y += 2;
        }
        float done = lineStart(lines.size());
        float choices = Anim.outCubic(Anim.window(t, done - 0.2f, done + 0.4f));
        if (choices > 0.01f) {
            float oc = Gfx.fade(choices);
            float my2 = H - 44;
            String means = tr("means");
            SysFont.draw(g, SysFont.fit(means, 5.6f, false, W - 20), W / 2f, my2, 5.6f, SystemTheme.TEXT_SOFT, false, 0.5f);
            buttons(g, mx, my, my2 + 10);
            String keys = tr("keys");
            SysFont.draw(g, SysFont.fit(keys, 4.8f, false, W - 20), W / 2f, my2 + 33, 4.8f, SystemTheme.TEXT_DIM, false, 0.5f);
            Gfx.setAlpha(oc);
        }
        SysFont.setContentScale(oldScale);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    private void buttons(GuiGraphics g, float mx, float my, float by) {
        String swear = tr("swear").toUpperCase(Locale.ROOT);
        String away = tr("away").toUpperCase(Locale.ROOT);
        float size = 6.8f;
        float sw = SysFont.spacedWidth(swear, size, true, 1.4f) + 36;
        float aw = SysFont.spacedWidth(away, size, true, 1.4f) + 30;
        float bh = 18, gap = 12;
        float x0 = W / 2f - (sw + aw + gap) / 2f;
        // the oath, in gold
        boolean hs = Ui.inside(mx, my, x0, by, sw, bh);
        float pulse = Anim.pulse(1.6f);
        Shapes.pill(x0, by, sw, bh).fillV(Gfx.alpha(GOLD, hs ? 0.45f : 0.3f), Gfx.alpha(GOLD, hs ? 0.22f : 0.12f))
                .border(1f, Gfx.mix(GOLD, 0xFFFFFFFF, 0.3f), Gfx.alpha(GOLD, 0.5f)).glow(5 + 4 * pulse + (hs ? 4 : 0), Gfx.alpha(GOLD, 0.35f + 0.15f * pulse))
                .sheen(Anim.loop(2.2f) * 1.6f - 0.3f, 0.2f).draw(g);
        Shapes.icon(Icons.SPARKLE, x0 + 13, by + bh / 2f, 9).fillV(0xFFFFFFFF, GOLD).glow(2, Gfx.alpha(GOLD, 0.8f)).rotation(Anim.loop(5f)).draw(g);
        SysFont.spaced(g, swear, x0 + 23, SysFont.centerY(by, bh, size), size, 0xFFFFFFFF, true, 1.4f, 0f);
        hits.add(new Hit(x0, by, sw, bh, () -> answer(1)));
        // turning away, quiet
        float ax = x0 + sw + gap;
        boolean ha = Ui.inside(mx, my, ax, by, aw, bh);
        Shapes.pill(ax, by, aw, bh).fill(Gfx.alpha(0xFF14100A, ha ? 0.95f : 0.8f)).border(0.8f, Gfx.alpha(SystemTheme.TEXT_SOFT, ha ? 0.8f : 0.45f)).draw(g);
        SysFont.spaced(g, away, ax + aw / 2f, SysFont.centerY(by, bh, size), size, ha ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, 1.4f, 0.5f);
        hits.add(new Hit(ax, by, aw, bh, () -> answer(0)));
    }

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
        boolean ready = Anim.since(openedAt) > lineStart(lines.size()) - 0.2f;
        if (ready && (key == GLFW.GLFW_KEY_1 || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            answer(1);
            return true;
        }
        if (ready && key == GLFW.GLFW_KEY_2) {
            answer(0);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
