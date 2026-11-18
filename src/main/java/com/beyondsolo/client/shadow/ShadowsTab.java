package com.beyondsolo.client.shadow;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTab;
import com.beyondsolo.network.ShadowActionPayload;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.shadow.ShadowModule;
import com.beyondsolo.shadow.ShadowRecord;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.skill.SkillActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The SHADOWS page of the System window (necromancers only). Left: the army, strongest first, with each shadow's
 * rank, level and whether it is out, resting or recovering. Right: the selected shadow turning slowly in ink, its
 * numbers, and buttons to call it, send it back, ride it, or let it go (click twice).
 */
public class ShadowsTab implements SystemTab {
    private static final float LIST_W = 168;
    private static final float ROW_H = 20;

    private static @Nullable UUID selected;
    private UUID shown;
    private double selectedAt = -10;
    private float scroll;
    private final List<Object[]> rows = new ArrayList<>();
    private final List<Object[]> buttons = new ArrayList<>();
    private @Nullable UUID releaseArmed;
    private double releaseArmedAt;
    private @Nullable ShadowEntity preview;

    @Override
    public Component title() {
        return Component.translatable("tab.beyondsolo.shadows");
    }

    @Override
    public boolean isVisible() {
        return ClientSystemData.get().isAwakened() && ShadowClient.canRaise();
    }

    private static List<ShadowRecord> sorted() {
        List<ShadowRecord> list = new ArrayList<>(ShadowClient.shadows());
        list.sort((a, b) -> {
            if (a.boss() != b.boss()) return a.boss() ? -1 : 1;
            if (a.rank() != b.rank()) return b.rank().ordinal() - a.rank().ordinal();
            return b.level() - a.level();
        });
        return list;
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        rows.clear();
        buttons.clear();
        List<ShadowRecord> all = sorted();
        long now = ShadowClient.gameTime();

        // ---- header: army size and how many are out
        float hx = x + 2;
        SysFont.spaced(g, Component.translatable("shadows.beyondsolo.army").getString(), hx, y + 1, 6.2f, 0xFFB79CFF, true, 1.3f, 0f);
        String counts = Component.translatable("shadows.beyondsolo.counts", all.size(), ShadowClient.capacity(),
                ShadowClient.activeCount(), ShadowClient.limit()).getString();
        SysFont.draw(g, counts, x + LIST_W, y + 1, 6.2f, SystemTheme.TEXT_SOFT, false, 1f);

        if (all.isEmpty()) {
            float cy = y + h / 2f - 20;
            Shapes.icon(Icons.SKULL, x + LIST_W / 2, cy, 26).fill(0x60B79CFF).glow(4, 0x309B7BE0).draw(g);
            List<SysFont.Line> lines = SysFont.wrap(Component.translatable("shadows.beyondsolo.empty"), 7f, false, LIST_W - 10);
            for (int i = 0; i < lines.size(); i++) SysFont.draw(g, lines.get(i), x + LIST_W / 2, cy + 20 + i * 9, SystemTheme.TEXT_SOFT, 0.5f);
            keysHint(g, x, y + h - 12);
            return;
        }

        ShadowRecord sel = null;
        for (ShadowRecord r : all) if (r.id().equals(selected)) sel = r;
        if (sel == null) {
            sel = all.get(0);
            selected = sel.id();
        }
        if (!sel.id().equals(shown)) {
            shown = sel.id();
            selectedAt = Anim.now();
        }

        // ---- the list
        float ly = y + 11;
        float lh = h - 11 - 14;
        int visible = (int) (lh / ROW_H);
        int maxScroll = Math.max(0, all.size() - visible);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(x, (int) ly, (int) (x + LIST_W + 2), (int) (ly + lh));
        for (int i = 0; i < all.size(); i++) {
            float ry = ly + (i - scroll) * ROW_H;
            if (ry < ly - ROW_H || ry > ly + lh) continue;
            ShadowRecord r = all.get(i);
            boolean isSel = r.id().equals(selected);
            boolean hover = Ui.inside(mouseX, mouseY, x, ry, LIST_W, ROW_H - 2) && mouseY >= ly && mouseY <= ly + lh;
            int c = Gfx.opaque(r.boss() ? 0xFF4A6A : r.rank().color());
            Ui.card(g, x, ry, LIST_W, ROW_H - 2, 5, c, isSel ? 0.55f : hover ? 0.3f : 0.1f);
            Shapes.pill(x + 2, ry + 3, 2, ROW_H - 8).fill(c).glow(2, Gfx.alpha(c, 0.6f)).draw(g);
            Shapes.icon(r.isMount() ? Icons.RUN : r.role() == ShadowRecord.Role.CASTER ? Icons.SPARKLE : r.boss() ? Icons.CROWN : Icons.SKULL,
                    x + 12, ry + (ROW_H - 2) / 2, 9).fill(Gfx.mix(c, 0xFFFFFFFF, 0.4f)).draw(g);
            String name = SysFont.fit(r.displayName().getString(), 7f, true, LIST_W - 80);
            SysFont.title(g, name, x + 20, ry + 2.5f, 7f, SystemTheme.TEXT);
            String sub = r.rank().displayName().getString() + "  ·  " + Component.translatable("shadows.beyondsolo.lv", r.level()).getString();
            SysFont.text(g, sub, x + 20, ry + 10.5f, 5.8f, Gfx.mix(c, 0xFFFFFFFF, 0.3f));
            // status
            String status;
            int sc;
            if (ShadowClient.isActive(r.id())) {
                status = Component.translatable("shadows.beyondsolo.out").getString();
                sc = SystemTheme.GOOD;
            } else if (r.recovering(now)) {
                status = Component.translatable("shadows.beyondsolo.recovering", (r.recoverUntil() - now) / 20 + 1).getString();
                sc = SystemTheme.DANGER;
            } else {
                status = Component.translatable("shadows.beyondsolo.resting").getString();
                sc = SystemTheme.TEXT_DIM;
            }
            SysFont.draw(g, status, x + LIST_W - 5, ry + 6, 6f, sc, true, 1f);
            if (ShadowClient.isActive(r.id())) Shapes.circle(x + LIST_W - 7 - SysFont.width(status, 6f, true) - 2, ry + 8.5f, 1.6f)
                    .fill(sc).glow(2 + 2 * Anim.pulse(1.4f), Gfx.alpha(sc, 0.7f)).draw(g);
            rows.add(new Object[]{r.id(), ry, (float) x});
        }
        g.disableScissor();
        if (maxScroll > 0) {
            float track = lh;
            float knob = Math.max(12, track * visible / all.size());
            float ky = ly + (track - knob) * scroll / maxScroll;
            Shapes.pill(x + LIST_W + 3, ky, 2, knob).fill(0x809B7BE0).draw(g);
        }
        keysHint(g, x, y + h - 12);

        // ---- detail
        float dx = x + LIST_W + 10;
        float dw = w - LIST_W - 10;
        float k = Anim.outCubic(Anim.progress(selectedAt, 0.28f));
        float old = Gfx.fade(k);
        g.pose().pushPose();
        g.pose().translate((1 - k) * 10, 0, 0);
        detail(g, sel, dx, y, dw, h, now, mouseX, mouseY);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    private void keysHint(GuiGraphics g, float x, float y) {
        float kx = x + 2;
        kx += Ui.keycap(g, kx, y, 9, key(ShadowClientEvents.RISE), 0xFF9B7BE0) + 3;
        String a = Component.translatable("shadows.beyondsolo.key.rise").getString();
        SysFont.text(g, a, kx, y + 1.5f, 6f, SystemTheme.TEXT_SOFT);
        kx += SysFont.width(a, 6f, false) + 7;
        kx += Ui.keycap(g, kx, y, 9, key(ShadowClientEvents.RECALL), 0xFF9B7BE0) + 3;
        String b = Component.translatable("shadows.beyondsolo.key.recall").getString();
        SysFont.text(g, b, kx, y + 1.5f, 6f, SystemTheme.TEXT_SOFT);
        kx += SysFont.width(b, 6f, false) + 7;
        kx += Ui.keycap(g, kx, y, 9, key(ShadowClientEvents.MOUNT), 0xFF9B7BE0) + 3;
        SysFont.text(g, Component.translatable("shadows.beyondsolo.key.mount").getString(), kx, y + 1.5f, 6f, SystemTheme.TEXT_SOFT);
    }

    private static String key(net.minecraft.client.KeyMapping k) {
        String s = k.getTranslatedKeyMessage().getString().toUpperCase();
        return s.length() > 3 ? s.substring(0, 3) : s;
    }

    private void detail(GuiGraphics g, ShadowRecord r, float x, float y, float w, float h, long now, int mouseX, int mouseY) {
        int c = Gfx.opaque(r.boss() ? 0xFF4A6A : r.rank().color());
        Ui.card(g, x, y, w, h, 9, c, 0.22f);
        float px = x + 10;
        float pw = w - 20;

        // the shadow itself, turning slowly
        float boxW = 70, boxH = 84;
        float bx = px - 2, by = y + 8;
        Shapes.box(bx, by, boxW, boxH).radius(7).fillV(0xC0100820, 0xE0040208).border(0.75f, Gfx.alpha(c, 0.5f)).draw(g);
        Shapes.circle(bx + boxW / 2, by + boxH - 12, 24).fill(0).glow(16, Gfx.alpha(c, 0.25f)).draw(g);
        drawPreview(g, r, (int) bx, (int) by, (int) (bx + boxW), (int) (by + boxH));

        float tx = bx + boxW + 8;
        float tw = x + w - 10 - tx;
        float cy = y + 9;
        float chipX = tx;
        chipX += Ui.chip(g, chipX, cy, 10, r.rank().displayName().getString().toUpperCase(), 6f, Gfx.opaque(r.rank().color()), true) + 4;
        chipX += Ui.chip(g, chipX, cy, 10, r.role().displayName().getString(), 6f, SystemTheme.TEXT_SOFT, false) + 4;
        if (r.boss()) Ui.chip(g, chipX, cy, 10, Component.translatable("shadows.beyondsolo.elite").getString(), 6f, SystemTheme.GOLD, true);
        cy += 14;
        // a long name steps down a size rather than lose its end ("Gralric, la Novena Campana")
        float nameSize = 10.5f;
        List<SysFont.Line> nameLines = SysFont.wrap(r.displayName(), nameSize, true, tw);
        if (nameLines.size() > 2) {
            nameSize = 8.5f;
            nameLines = SysFont.wrap(r.displayName(), nameSize, true, tw);
        }
        for (int i = 0; i < Math.min(3, nameLines.size()); i++) {
            SysFont.draw(g, nameLines.get(i), tx, cy, SystemTheme.TEXT, 0f);
            cy += nameSize * SysFont.LINE;
        }
        cy += 2;
        // level and xp
        boolean max = r.maxed();
        SysFont.title(g, Component.translatable("shadows.beyondsolo.level", r.level(), com.beyondsolo.shadow.ShadowRank.LEVEL_CAP).getString(), tx, cy, 7.5f,
                Gfx.mix(c, 0xFFFFFFFF, 0.4f));
        String xp = max ? Component.translatable("shadows.beyondsolo.max").getString() : r.xp() + " / " + r.xpToNext();
        SysFont.draw(g, xp, tx + tw, cy + 0.5f, 6.2f, SystemTheme.TEXT_SOFT, false, 1f);
        Ui.bar(g, tx, cy + 10, tw, 2.6f, max ? 1 : r.xp() / (float) r.xpToNext(), Gfx.mix(c, 0xFF000000, 0.2f), c);
        cy += 17;
        var master = ClientSystemData.get();
        float sx = tx;
        sx += stat(g, sx, cy, Icons.HEART, SkillActions.f(ShadowService.maxHealth(r, master)), SystemTheme.HP_B) + 4;
        sx += stat(g, sx, cy, Icons.FLAME, SkillActions.f(ShadowService.damage(r, master)), 0xFFFFB070) + 4;
        stat(g, sx, cy, Icons.MEDAL, Component.translatable("shadows.beyondsolo.kills", r.kills()).getString(), SystemTheme.GOLD);
        cy += 15;

        // what it does
        float dy = Math.max(cy, by + boxH + 6);
        String what = r.boss() ? "shadows.beyondsolo.does.boss" : "shadows.beyondsolo.does." + r.role().key();
        List<SysFont.Line> lines = SysFont.wrap(Component.translatable(what), 6.6f, false, pw);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            SysFont.draw(g, lines.get(i), px, dy, SystemTheme.TEXT_SOFT, 0f);
            dy += 6.6f * SysFont.LINE;
        }

        // buttons
        float bY = y + h - 20;
        float bx2 = px;
        boolean out = ShadowClient.isActive(r.id());
        boolean recovering = r.recovering(now);
        if (out) bx2 += button(g, bx2, bY, "shadows.beyondsolo.btn.dismiss", ShadowActionPayload.DISMISS, r, 0xFF9B7BE0, true, mouseX, mouseY) + 4;
        else bx2 += button(g, bx2, bY, "shadows.beyondsolo.btn.summon", ShadowActionPayload.SUMMON, r, 0xFFB79CFF, !recovering, mouseX, mouseY) + 4;
        if (r.isMount()) bx2 += button(g, bx2, bY, "shadows.beyondsolo.btn.ride", ShadowActionPayload.RIDE, r, 0xFF6FA0FF, !recovering, mouseX, mouseY) + 4;
        boolean armed = r.id().equals(releaseArmed) && Anim.since(releaseArmedAt) < 3;
        float rw = buttonWidth(armed ? "shadows.beyondsolo.btn.confirm" : "shadows.beyondsolo.btn.release");
        button(g, x + w - 10 - rw, bY, armed ? "shadows.beyondsolo.btn.confirm" : "shadows.beyondsolo.btn.release",
                ShadowActionPayload.RELEASE, r, SystemTheme.DANGER, true, mouseX, mouseY);
    }

    private void drawPreview(GuiGraphics g, ShadowRecord r, int x0, int y0, int x1, int y1) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (preview == null || preview.level() != mc.level) {
            preview = ShadowModule.SHADOW.get().create(mc.level);
            if (preview == null) return;
            preview.preview = true;
        }
        preview.setLook(r.type().toString(), r.rank(), r.boss() ? r.bossRank() : -1, r.role());
        preview.tickCount = (int) (Anim.now() * 20);
        float big = Math.max(preview.getBbHeight(), preview.getBbWidth() * 0.9f);
        int scale = (int) Mth.clamp((y1 - y0) * 0.62f / Math.max(0.5f, big), 6, 60);
        float turn = (float) Math.sin(Anim.now() * 0.6) * 0.9f;
        try {
            InventoryScreen.renderEntityInInventoryFollowsAngle(g, x0, y0, x1, y1 - 4, scale, 0.0625f, turn, -0.15f, preview);
        } catch (RuntimeException ignored) {
            // some creatures can't be drawn outside a world; the card still shows its numbers
        }
    }

    private static float stat(GuiGraphics g, float x, float y, net.minecraft.resources.ResourceLocation icon, String text, int color) {
        float tw = SysFont.width(text, 6.5f, true);
        float w = tw + 17;
        Shapes.pill(x, y, w, 10).fill(Gfx.alpha(color, 0.1f)).border(0.6f, Gfx.alpha(color, 0.5f)).draw(g);
        Shapes.icon(icon, x + 6, y + 5, 7).fill(color).draw(g);
        SysFont.draw(g, text, x + 11.5f, SysFont.centerY(y, 10, 6.5f), 6.5f, Gfx.mix(color, 0xFFFFFFFF, 0.3f), true, 0f);
        return w;
    }

    private static float buttonWidth(String key) {
        return SysFont.width(Component.translatable(key).getString(), 6.8f, true) + 16;
    }

    private float button(GuiGraphics g, float x, float y, String key, int action, ShadowRecord r, int color, boolean enabled, int mx, int my) {
        String text = Component.translatable(key).getString();
        float w = buttonWidth(key);
        float h = 13;
        boolean hover = enabled && Ui.inside(mx, my, x, y, w, h);
        int c = enabled ? color : 0xFF4A5468;
        Shapes.box(x, y, w, h).radius(4).fillV(Gfx.alpha(c, hover ? 0.4f : 0.22f), Gfx.alpha(c, hover ? 0.2f : 0.08f))
                .border(0.75f, Gfx.alpha(c, hover ? 1f : 0.65f)).glow(hover ? 5 : 0, Gfx.alpha(c, 0.4f)).draw(g);
        SysFont.draw(g, text, x + w / 2, SysFont.centerY(y, h, 6.8f), 6.8f, enabled ? Gfx.mix(c, 0xFFFFFFFF, 0.5f) : SystemTheme.TEXT_DIM, true, 0.5f);
        if (enabled) buttons.add(new Object[]{action, r.id(), x, y, w, h});
        return w;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Object[] b : buttons) {
            if (!Ui.inside(mouseX, mouseY, (float) b[2], (float) b[3], (float) b[4], (float) b[5])) continue;
            int action = (int) b[0];
            UUID id = (UUID) b[1];
            if (action == ShadowActionPayload.RELEASE && !(id.equals(releaseArmed) && Anim.since(releaseArmedAt) < 3)) {
                releaseArmed = id;
                releaseArmedAt = Anim.now();
                SystemScreen.playClick();
                return true;
            }
            releaseArmed = null;
            PacketDistributor.sendToServer(new ShadowActionPayload(action, id.toString()));
            SystemScreen.playClick();
            if (action == ShadowActionPayload.RIDE) Minecraft.getInstance().setScreen(null);
            return true;
        }
        for (Object[] r : rows) {
            float ry = (float) r[1];
            if (mouseY >= ry && mouseY < ry + ROW_H - 2 && mouseX >= (float) r[2] && mouseX <= (float) r[2] + LIST_W) {
                UUID id = (UUID) r[0];
                if (!id.equals(selected)) {
                    selected = id;
                    SystemScreen.playClick();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        scroll -= (float) scrollY;
        return true;
    }
}
