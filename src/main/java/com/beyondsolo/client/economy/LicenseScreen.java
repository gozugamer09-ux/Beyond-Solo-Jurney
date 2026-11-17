package com.beyondsolo.client.economy;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.economy.LicenseInfo;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The hunter license, held up to the screen: an ID card in the holder's rank colour with their face, name,
 * class, level, rank emblem, gates cleared, highest gate, issue day and license number. It flips up into view,
 * the photo is scanned in, the rank emblem stamps down with a shockwave, the clear count rolls up, and a
 * holographic sheen keeps crossing the card (it follows the mouse when the mouse is over it).
 */
public class LicenseScreen extends Screen {
    private static final float CW = 252;
    private static final float CH = 159;

    private final LicenseInfo info;
    private final double openedAt;
    private double closingAt = -1;
    private boolean stamped;

    public LicenseScreen(LicenseInfo info) {
        super(Component.translatable("screen.beyondsolo.license"));
        this.info = info;
        this.openedAt = Anim.now();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.3f, 0.8f));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(partialTick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        float t = Anim.since(openedAt);
        float close = closingAt < 0 ? 0 : Anim.inCubic(Anim.progress(closingAt, 0.2f));
        float dim = Anim.clamp01(t / 0.3f) * (1 - close);
        float oldA = Gfx.setAlpha(dim);
        Shapes.vignette(0, 0, width, height, 0.1f, 1.4f).fill(0xD0040912).draw(g);
        Gfx.setAlpha(oldA);

        PlayerRank rank = info.playerRank();
        int rc = SystemTheme.rank(rank);
        float x = (width - CW) / 2f;
        float y = (height - CH) / 2f - 6;

        // the card flips up into view
        float rise = Anim.outCubic(Anim.clamp01(t / 0.5f));
        float flip = Anim.outBack(Anim.clamp01((t - 0.05f) / 0.5f));
        g.pose().pushPose();
        g.pose().translate(width / 2f, height / 2f + (1 - rise) * 70 + close * 50, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((1 - rise) * -9));
        g.pose().scale(Math.max(0.02f, flip), 0.9f + 0.1f * rise, 1);
        g.pose().translate(-width / 2f, -height / 2f, 0);
        float old = Gfx.setAlpha(Anim.clamp01(t / 0.2f) * (1 - close));

        card(g, x, y, rc, mouseX, mouseY, t);

        g.pose().popPose();
        Gfx.setAlpha(old);

        // the stamp sound at the right moment
        if (!stamped && t > 0.85f) {
            stamped = true;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.8f, 0.25f));
        }
        float hint = Anim.clamp01((t - 1.6f) / 0.4f) * (1 - close);
        if (hint > 0) {
            float o = Gfx.setAlpha(hint);
            SysFont.draw(g, Component.translatable("license.beyondsolo.hint").getString(), width / 2f, y + CH + 16, 6.2f, SystemTheme.TEXT_DIM, false, 0.5f);
            Gfx.setAlpha(o);
        }
    }

    private void card(GuiGraphics g, float x, float y, int rc, int mouseX, int mouseY, float t) {
        // body
        Shapes.box(x + 2, y + 5, CW - 4, CH - 4).radius(11).fill(0).glow(16, 0x90000000).draw(g);
        float sheenPos;
        boolean over = Ui.inside(mouseX, mouseY, x, y, CW, CH);
        if (over) sheenPos = (mouseX - x) / CW * 1.4f - 0.2f;
        else sheenPos = Anim.loop(3.6f) * 2.6f - 0.6f;
        Shapes.box(x, y, CW, CH).radius(11)
                .fillV(Gfx.mix(0xFF101D36, rc, 0.16f), Gfx.mix(0xFF070C18, rc, 0.06f))
                .border(1.2f, Gfx.mix(rc, 0xFFFFFFFF, 0.25f), Gfx.alpha(rc, 0.4f))
                .glow(10, Gfx.alpha(rc, 0.35f))
                .sheen(sheenPos, 0.16f).draw(g);
        // guilloche-like rings in the background
        float ringT = Anim.loop(40f);
        for (int i = 0; i < 4; i++) {
            Shapes.ring(x + CW - 34, y + CH / 2f + 2, 30 + i * 13, 0.5f).fill(Gfx.alpha(rc, 0.10f - i * 0.018f)).dashes(36 + i * 8, 0.55f)
                    .rotation(ringT * (i % 2 == 0 ? 1 : -1)).draw(g);
        }
        // the top strip
        Shapes.box(x + 1.2f, y + 1.2f, CW - 2.4f, 22).radius(10).fillH(Gfx.alpha(rc, 0.55f), Gfx.alpha(rc, 0.08f)).draw(g);
        Shapes.icon(Icons.ID_CARD, x + 14, y + 12.5f, 10).fill(0xFFFFFFFF).glow(2, Gfx.alpha(rc, 0.8f)).draw(g);
        SysFont.spaced(g, Component.translatable("license.beyondsolo.title").getString(), x + 23, y + 8, 8f, SystemTheme.TEXT, true, 2.2f, 0f);
        SysFont.draw(g, Component.translatable("license.beyondsolo.registry").getString(), x + CW - 10, y + 6.5f, 5.2f, Gfx.alpha(SystemTheme.TEXT, 0.7f), true, 1f);
        SysFont.draw(g, Component.translatable("tooltip.beyondsolo.license.number", info.number()).getString(), x + CW - 10, y + 13.5f, 6f, SystemTheme.TEXT, true, 1f);

        float content = Anim.clamp01((t - 0.35f) / 0.35f);
        float co = Gfx.fade(content);

        // photo
        float px = x + 12, py = y + 31, pw = 56, ph = 66;
        Shapes.box(px, py, pw, ph).radius(5).fillV(0xFF0B1426, 0xFF050A14).border(0.9f, Gfx.alpha(rc, 0.8f), Gfx.alpha(rc, 0.3f)).draw(g);
        float scan = Anim.clamp01((t - 0.4f) / 0.6f);
        PlayerSkin skin = skin();
        int face = 44;
        int fx = Math.round(px + (pw - face) / 2f);
        int fy = Math.round(py + 8);
        if (scan > 0) {
            g.enableScissor(fx, fy, fx + face, fy + Math.round(face * scan));
            PlayerFaceRenderer.draw(g, skin, fx, fy, face);
            g.disableScissor();
        }
        if (scan > 0 && scan < 1) {
            float ly = fy + face * scan;
            Shapes.box(px + 3, ly - 1, pw - 6, 2).fill(Gfx.alpha(rc, 0.9f)).glow(4, Gfx.alpha(rc, 0.8f)).draw(g);
        }
        // scanlines over the photo
        for (int i = 0; i < 11; i++) Shapes.box(fx, fy + i * 4 + 1, face, 0.5f).fill(0x14FFFFFF).draw(g);
        corners(g, px, py, pw, ph, rc);
        SysFont.draw(g, Component.translatable("license.beyondsolo.holder").getString(), px + pw / 2f, py + ph - 10, 5f, SystemTheme.TEXT_DIM, true, 0.5f);

        // name, class, level
        float tx = px + pw + 10;
        SysFont.title(g, SysFont.fit(info.name(), 11f, true, 110), tx, y + 32, 11f, SystemTheme.TEXT);
        PlayerClass cls = info.playerClass();
        Subclass sub = info.sub();
        float ly = y + 47;
        if (cls != null) {
            int cc = SystemTheme.playerClass(cls);
            Shapes.icon(Icons.of(cls), tx + 4, ly + 3.5f, 9).fill(cc).draw(g);
            String cn = cls.displayName().getString();
            SysFont.title(g, cn, tx + 11, ly, 7f, cc);
            if (sub != null) {
                float sx = tx + 11 + SysFont.width(cn, 7f, true) + 4;
                SysFont.text(g, "›", sx, ly, 7f, SystemTheme.TEXT_DIM);
                SysFont.title(g, sub.displayName().getString(), sx + 6, ly, 7f, Gfx.opaque(sub.color()));
            }
        } else {
            SysFont.text(g, Component.translatable("license.beyondsolo.unevaluated").getString(), tx, ly, 7f, SystemTheme.TEXT_DIM);
        }
        SysFont.text(g, Component.translatable("license.beyondsolo.level", info.level()).getString(), tx, ly + 11, 6.5f, SystemTheme.TEXT_SOFT);
        Ui.divider(g, tx, ly + 22, 100, Gfx.alpha(rc, 0.5f));

        // stats row
        float sy = y + 79;
        int shown = Math.round(info.clears() * Anim.outCubic(Anim.clamp01((t - 0.9f) / 0.7f)));
        stat(g, tx, sy, Component.translatable("license.beyondsolo.clears").getString(), String.valueOf(shown), SystemTheme.TEXT);
        PlayerRank best = info.bestRank();
        stat(g, tx + 38, sy, Component.translatable("license.beyondsolo.best").getString(), best == null ? "—" : best.name(),
                best == null ? SystemTheme.TEXT_DIM : SystemTheme.rank(best));
        stat(g, tx + 76, sy, Component.translatable("license.beyondsolo.issued_on").getString(),
                Component.translatable("license.beyondsolo.day", info.issued() + 1).getString(), SystemTheme.TEXT_SOFT);

        // barcode along the bottom
        float bx = x + 12, by = y + CH - 24;
        long seed = info.number().hashCode() * 0x9E3779B97F4A7C15L;
        float cx = bx;
        java.util.Random r = new java.util.Random(seed);
        while (cx < bx + 120) {
            float bw = 0.6f + r.nextInt(3) * 0.6f;
            Shapes.box(cx, by, bw, 12).fill(Gfx.alpha(SystemTheme.TEXT, 0.65f)).draw(g);
            cx += bw + 0.6f + r.nextInt(3) * 0.6f;
        }
        SysFont.text(g, info.number().replace("-", "  "), bx, by + 14, 5f, SystemTheme.TEXT_DIM);
        Gfx.setAlpha(co);

        // the rank emblem stamps down
        float ex = x + CW - 34, ey = y + CH / 2f + 2;
        float st = Anim.clamp01((t - 0.75f) / 0.22f);
        if (st > 0) {
            float s = 1.8f - 0.8f * Anim.outBackStrong(st);
            float energy = 1 - Anim.clamp01((t - 0.97f) / 1.2f);
            float o = Gfx.fade(Anim.clamp01(st * 2));
            g.pose().pushPose();
            g.pose().translate(ex, ey, 0);
            g.pose().scale(s, s, 1);
            Ui.rankEmblem(g, info.playerRank(), 0, 0, 18, Math.max(energy, 0.15f + 0.1f * Anim.pulse(2.4f)));
            g.pose().popPose();
            Gfx.setAlpha(o);
            SysFont.spaced(g, Component.translatable("license.beyondsolo.rank").getString(), ex, ey + 26, 5.6f, Gfx.mix(rc, 0xFFFFFFFF, 0.3f), true, 1.6f, 0.5f);
            // the shockwave of the stamp
            float wk = Anim.clamp01((t - 0.97f) / 0.6f);
            if (wk > 0 && wk < 1) {
                Shapes.ring(ex, ey, 24 + 50 * Anim.outCubic(wk), 2.2f * (1 - wk) + 0.3f).fill(Gfx.alpha(Gfx.mix(rc, 0xFFFFFFFF, 0.4f), 1 - wk))
                        .glow(6, Gfx.alpha(rc, 0.6f * (1 - wk))).draw(g);
            }
        }
        // a holographic seal in the corner, turning
        float sealX = x + CW - 20, sealY = y + CH - 18;
        Shapes.circle(sealX, sealY, 9).fillV(Gfx.alpha(0xFF9FE8FF, 0.25f), Gfx.alpha(0xFFE4A8FF, 0.2f)).border(0.6f, Gfx.alpha(0xFFFFFFFF, 0.5f)).sheen(sheenPos, 0.5f).draw(g);
        Shapes.ring(sealX, sealY, 11, 0.6f).fill(Gfx.alpha(0xFFFFFFFF, 0.45f)).dashes(12, 0.5f).rotation(Anim.loop(10f)).draw(g);
        Shapes.icon(Icons.SEAL, sealX, sealY, 11).fill(Gfx.alpha(0xFFFFFFFF, 0.75f)).draw(g);
    }

    private void stat(GuiGraphics g, float x, float y, String label, String value, int color) {
        SysFont.text(g, label, x, y, 5f, SystemTheme.TEXT_DIM);
        SysFont.title(g, value, x, y + 7, 9f, color);
    }

    private static void corners(GuiGraphics g, float x, float y, float w, float h, int c) {
        float l = 7;
        int col = Gfx.mix(c, 0xFFFFFFFF, 0.3f);
        Shapes.box(x - 1, y - 1, l, 1.2f).fill(col).draw(g);
        Shapes.box(x - 1, y - 1, 1.2f, l).fill(col).draw(g);
        Shapes.box(x + w - l + 1, y - 1, l, 1.2f).fill(col).draw(g);
        Shapes.box(x + w - 0.2f, y - 1, 1.2f, l).fill(col).draw(g);
        Shapes.box(x - 1, y + h - 0.2f, l, 1.2f).fill(col).draw(g);
        Shapes.box(x - 1, y + h - l + 1, 1.2f, l).fill(col).draw(g);
        Shapes.box(x + w - l + 1, y + h - 0.2f, l, 1.2f).fill(col).draw(g);
        Shapes.box(x + w - 0.2f, y + h - l + 1, 1.2f, l).fill(col).draw(g);
    }

    private PlayerSkin skin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo pi = mc.getConnection().getPlayerInfo(info.owner());
            if (pi != null) return pi.getSkin();
        }
        if (mc.player != null && mc.player.getUUID().equals(info.owner())) return mc.player.getSkin();
        return DefaultPlayerSkin.get(info.owner());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        onClose();
        return true;
    }

    @Override
    public void onClose() {
        if (closingAt < 0) {
            closingAt = Anim.now();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.6f, 0.5f));
        }
    }

    @Override
    public void tick() {
        if (closingAt >= 0 && Anim.progress(closingAt, 0.2f) >= 1f) Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
