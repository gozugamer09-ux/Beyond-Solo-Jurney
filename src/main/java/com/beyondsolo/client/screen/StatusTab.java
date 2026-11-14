package com.beyondsolo.client.screen;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.network.AllocateStatPayload;
import com.beyondsolo.system.Objectives;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** The STATUS page: your character, rank and class on the left; level, bars and the five stats on the right. */
public class StatusTab implements SystemTab {
    private static final float CARD_W = 108;
    private static final float CELL_H = 23;

    private final float[][] plusButtons = new float[Stat.values().length][];
    private final float[] hoverAmount = new float[Stat.values().length];
    private final float[] plusHover = new float[Stat.values().length];
    private final int[] lastValue = new int[Stat.values().length];
    private final double[] bumpedAt = new double[Stat.values().length];
    private float shownHp = -1, shownMp = -1, shownXp = -1, shownFatigue = -1;
    private int lastLevel = -1;
    private boolean first = true;

    @Override
    public Component title() {
        return Component.translatable("tab.beyondsolo.status");
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        SystemData d = ClientSystemData.get();
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!d.isAwakened()) {
            renderLocked(g, d, x, y, w, h);
            return;
        }
        renderProfile(g, d, player, x, y, h, mouseX, mouseY);

        float x0 = x + CARD_W + 10;
        float rw = w - CARD_W - 10;

        // smooth the bars so changes glide instead of jumping
        float hp = player.getMaxHealth() <= 0 ? 0 : player.getHealth() / player.getMaxHealth();
        float mp = d.maxMana() <= 0 ? 0 : d.mana() / d.maxMana();
        float xp = d.xpToNextLevel() <= 0 ? 0 : (float) d.xp() / d.xpToNextLevel();
        float ft = d.exhaustion() / 100f;
        if (first || d.level() != lastLevel) {
            shownXp = first ? 0 : 0;
            lastLevel = d.level();
        }
        if (first) {
            shownHp = shownMp = shownFatigue = 0;
            for (Stat s : Stat.values()) {
                lastValue[s.ordinal()] = d.stat(s);
                bumpedAt[s.ordinal()] = -10;
            }
            first = false;
        }
        shownHp = Anim.approach(shownHp, hp, 9f);
        shownMp = Anim.approach(shownMp, mp, 9f);
        shownXp = Anim.approach(shownXp, xp, 7f);
        shownFatigue = Anim.approach(shownFatigue, ft, 9f);

        // ---- level + experience
        SysFont.spaced(g, Component.translatable("status.beyondsolo.level").getString(), x0, y + 1, 6.5f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        SysFont.title(g, String.valueOf(d.level()), x0, y + 10, 18, SystemTheme.TEXT);
        float xpX = x0 + 50;
        float xpW = rw - 50;
        SysFont.spaced(g, Component.translatable("status.beyondsolo.experience").getString(), xpX, y + 1, 6.5f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        SysFont.draw(g, d.xp() + " / " + d.xpToNextLevel(), xpX + xpW, y + 0.5f, 7.5f, SystemTheme.TEXT_SOFT, false, 1f);
        Ui.bar(g, xpX, y + 12, xpW, 7, shownXp, SystemTheme.XP_A, SystemTheme.XP_B);
        SysFont.draw(g, Component.translatable("status.beyondsolo.next_level", Math.max(0, d.xpToNextLevel() - d.xp())).getString(),
                xpX + xpW, y + 22.5f, 6.5f, SystemTheme.TEXT_DIM, false, 1f);

        // ---- health, mana, fatigue
        float vy = y + 34;
        float half = (rw - 8) / 2f;
        vital(g, Icons.HEART, "HP", Math.round(player.getHealth()) + " / " + Math.round(player.getMaxHealth()), x0, vy, half, shownHp, SystemTheme.HP_A, SystemTheme.HP_B);
        vital(g, Icons.DROP, "MP", Math.round(d.mana()) + " / " + Math.round(d.maxMana()), x0 + half + 8, vy, half, shownMp, SystemTheme.MP_A, SystemTheme.MP_B);

        float fy = y + 58;
        boolean critical = d.exhaustion() >= 80;
        int ftA = critical ? SystemTheme.HP_A : SystemTheme.FATIGUE_A;
        int ftB = critical ? Gfx.mix(SystemTheme.HP_B, 0xFFFFFFFF, Anim.pulse(0.7f) * 0.4f) : SystemTheme.FATIGUE_B;
        SysFont.spaced(g, Component.translatable("status.beyondsolo.fatigue").getString(), x0, fy, 6.5f, critical ? SystemTheme.DANGER : SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        SysFont.draw(g, Math.round(d.exhaustion()) + "%", x0 + rw, fy - 0.5f, 7.5f, critical ? SystemTheme.DANGER : SystemTheme.TEXT_SOFT, false, 1f);
        Ui.bar(g, x0, fy + 9, rw, 4, shownFatigue, ftA, ftB);

        Ui.divider(g, x0, y + 79.5f, rw, Gfx.alpha(SystemTheme.ACCENT, 0.35f));

        // ---- attributes
        SysFont.spaced(g, Component.translatable("status.beyondsolo.attributes").getString(), x0, y + 86, 6.5f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        float gridY = y + 97;
        float cellW = (rw - 6) / 2f;
        Stat[] stats = Stat.values();
        Stat hovered = null;
        boolean canSpend = d.statPoints() > 0;
        for (int i = 0; i < stats.length; i++) {
            Stat s = stats[i];
            float cx = x0 + (i % 2) * (cellW + 6);
            float cy = gridY + (i / 2) * (CELL_H + 3);
            boolean hover = Ui.inside(mouseX, mouseY, cx, cy, cellW, CELL_H);
            if (hover) hovered = s;
            hoverAmount[i] = Anim.approach(hoverAmount[i], hover ? 1 : 0, 16f);
            int value = d.stat(s);
            if (value != lastValue[i]) {
                lastValue[i] = value;
                bumpedAt[i] = Anim.now();
            }
            float bump = 1 - Anim.outCubic(Anim.progress(bumpedAt[i], 0.45f));

            Ui.card(g, cx, cy, cellW, CELL_H, 7, SystemTheme.ACCENT, hoverAmount[i] * 0.35f + bump * 0.5f);
            Ui.iconBadge(g, Icons.of(s), cx + 13, cy + CELL_H / 2f, 8, Gfx.mix(SystemTheme.ACCENT, 0xFFFFFFFF, bump * 0.5f));
            SysFont.title(g, s.displayName(), cx + 26, SysFont.centerY(cy, CELL_H, 9), 9, Gfx.mix(SystemTheme.TEXT_SOFT, SystemTheme.TEXT, hoverAmount[i]));

            float valueRight = cx + cellW - (canSpend ? 26 : 9);
            float vs = 12 + bump * 3;
            SysFont.draw(g, String.valueOf(value), valueRight, SysFont.centerY(cy, CELL_H, vs), vs, Gfx.mix(SystemTheme.TEXT, SystemTheme.ACCENT_BRIGHT, bump), true, 1f);

            if (canSpend) {
                float bx = cx + cellW - 13;
                float by = cy + CELL_H / 2f;
                boolean over = Math.hypot(mouseX - bx, mouseY - by) <= 8.5;
                plusHover[i] = Anim.approach(plusHover[i], over ? 1 : 0, 18f);
                float r = 7 + plusHover[i] * 1.2f;
                float idle = Anim.pulse(1.6f);
                Shapes.circle(bx, by, r)
                        .fillV(Gfx.alpha(SystemTheme.ACCENT, 0.3f + 0.35f * plusHover[i]), Gfx.alpha(SystemTheme.ACCENT_DEEP, 0.2f + 0.3f * plusHover[i]))
                        .border(0.9f, Gfx.alpha(SystemTheme.ACCENT_BRIGHT, 0.85f), Gfx.alpha(SystemTheme.ACCENT, 0.5f))
                        .glow(3 + idle * 2 + plusHover[i] * 3, Gfx.alpha(SystemTheme.ACCENT, 0.25f + 0.2f * idle + 0.3f * plusHover[i])).draw(g);
                Shapes.pill(bx - 3.2f, by - 0.6f, 6.4f, 1.2f).fill(SystemTheme.TEXT).draw(g);
                Shapes.pill(bx - 0.6f, by - 3.2f, 1.2f, 6.4f).fill(SystemTheme.TEXT).draw(g);
                plusButtons[i] = new float[]{bx, by};
            } else {
                plusButtons[i] = null;
                plusHover[i] = 0;
            }
        }

        // the sixth cell shows the points you can still spend
        float px = x0 + cellW + 6;
        float py = gridY + 2 * (CELL_H + 3);
        float glow = canSpend ? Anim.pulse(1.6f) : 0;
        int pointColor = canSpend ? SystemTheme.GOLD : SystemTheme.TEXT_DIM;
        Ui.card(g, px, py, cellW, CELL_H, 7, pointColor, canSpend ? 0.45f + 0.25f * glow : 0f);
        Ui.iconBadge(g, Icons.STAR, px + 13, py + CELL_H / 2f, 8, pointColor);
        SysFont.title(g, Component.translatable("status.beyondsolo.points"), px + 26, SysFont.centerY(py, CELL_H, 9), 9, canSpend ? SystemTheme.GOLD : SystemTheme.TEXT_DIM);
        SysFont.draw(g, String.valueOf(d.statPoints()), px + cellW - 9, SysFont.centerY(py, CELL_H, 12), 12, canSpend ? SystemTheme.GOLD : SystemTheme.TEXT_DIM, true, 1f);

        if (hovered != null) tooltip(g, hovered, mouseX, mouseY);
    }

    private void renderProfile(GuiGraphics g, SystemData d, Player player, int x, int y, int h, int mouseX, int mouseY) {
        int rankColor = SystemTheme.rank(d.rank());
        int classColor = SystemTheme.playerClass(d.playerClass());
        float mid = x + CARD_W / 2f;
        Ui.card(g, x, y, CARD_W, h, 9);
        // light behind and under the character
        Shapes.circle(mid, y + 60, 3).fill(0x00FFFFFF).glow(56, Gfx.alpha(rankColor, 0.20f)).draw(g);
        float ring = Anim.loop(14f);
        Shapes.box(mid - 30, y + 106, 60, 8).radius(4).fill(Gfx.alpha(SystemTheme.ACCENT, 0.10f)).border(0.75f, Gfx.alpha(SystemTheme.ACCENT, 0.5f)).glow(6, Gfx.alpha(SystemTheme.ACCENT, 0.22f)).draw(g);
        Shapes.box(mid - 20, y + 108.5f, 40, 3).radius(1.5f).fill(Gfx.alpha(SystemTheme.ACCENT_BRIGHT, 0.25f + 0.15f * Anim.pulse(2.4f))).draw(g);

        if (Gfx.alpha() > 0.6f) {
            float ax = (float) Math.atan((mid - mouseX) / 70f) + (float) Math.sin(Anim.now() * 0.7) * 0.18f;
            float ay = (float) Math.atan((y + 40 - mouseY) / 90f) * 0.6f;
            InventoryScreen.renderEntityInInventoryFollowsAngle(g, x + 3, y + 6, Math.round(x + CARD_W - 3), y + 118, 46, 0.0625f, ax, ay, player);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        Ui.rankEmblem(g, d.rank(), x + 18, y + 18, 10.5f, 0.15f + 0.1f * Anim.pulse(3f));

        String name = SysFont.fit(player.getName().getString(), 11, true, CARD_W - 12);
        SysFont.draw(g, name, mid, y + 123, 11, SystemTheme.TEXT, true, 0.5f);

        // class line: icon + name
        // after the job change the subclass takes the class's place
        com.beyondsolo.job.Subclass sub = com.beyondsolo.client.job.JobClient.subclass();
        if (sub != null) classColor = Gfx.opaque(sub.color());
        String className = (sub != null ? sub.displayName() : d.playerClass().displayName()).getString();
        float cw = SysFont.width(className, 8.5f, true) + 12;
        float cx = mid - cw / 2f;
        Shapes.icon(sub != null ? sub.icon() : Icons.of(d.playerClass()), cx + 4, y + 139.5f, 9).fill(Gfx.mix(classColor, 0xFFFFFFFF, 0.25f)).glow(2.5f, Gfx.alpha(classColor, 0.6f)).draw(g);
        SysFont.title(g, className, cx + 12, y + 136.5f, 8.5f, Gfx.mix(classColor, 0xFFFFFFFF, 0.2f));

        Component titleText = d.title().isEmpty() ? Component.translatable("status.beyondsolo.title.none") : Component.translatable(d.title());
        String title = SysFont.fit(titleText.getString(), 7.5f, false, CARD_W - 14);
        Ui.divider(g, x + 12, y + 150, CARD_W - 24, Gfx.alpha(SystemTheme.ACCENT, 0.3f));
        SysFont.spaced(g, Component.translatable("status.beyondsolo.title").getString(), mid, y + 155, 6f, SystemTheme.TEXT_DIM, true, 1.2f, 0.5f);
        SysFont.draw(g, title, mid, y + 164, 7.5f, d.title().isEmpty() ? SystemTheme.TEXT_DIM : SystemTheme.GOLD, false, 0.5f);
        g.pose().popPose();
    }

    private static void vital(GuiGraphics g, ResourceLocation icon, String label, String value, float x, float y, float w, float fraction, int a, int b) {
        Shapes.icon(icon, x + 4, y + 3.5f, 8).fill(b).glow(2, Gfx.alpha(a, 0.6f)).draw(g);
        SysFont.title(g, label, x + 11, y + 0.5f, 7.5f, SystemTheme.TEXT_SOFT);
        SysFont.draw(g, value, x + w, y, 7.5f, SystemTheme.TEXT_SOFT, false, 1f);
        Ui.bar(g, x, y + 11, w, 7, fraction, a, b);
    }

    private static void renderLocked(GuiGraphics g, SystemData d, int x, int y, int w, int h) {
        float cx = x + w / 2f;
        float cy = y + 46;
        float pulse = Anim.pulse(2.2f);
        Shapes.circle(cx, cy, 24).fillV(0x60183052, 0x600D1C36).border(1f, Gfx.alpha(SystemTheme.FATIGUE_B, 0.8f), Gfx.alpha(SystemTheme.FATIGUE_A, 0.35f))
                .glow(10 + pulse * 6, Gfx.alpha(SystemTheme.FATIGUE_A, 0.22f + 0.12f * pulse)).draw(g);
        Shapes.ring(cx, cy, 30, 1f).fill(Gfx.alpha(SystemTheme.FATIGUE_B, 0.7f)).dashes(28, 0.4f).rotation(Anim.loop(22f)).draw(g);
        Shapes.ring(cx, cy, 35, 0.75f).fill(Gfx.alpha(SystemTheme.FATIGUE_B, 0.35f)).dashes(8, 0.7f).rotation(-Anim.loop(30f)).draw(g);
        Shapes.icon(Icons.LOCK, cx, cy, 24).fillV(0xFFFFE9B8, SystemTheme.FATIGUE_B).glow(4, Gfx.alpha(SystemTheme.FATIGUE_A, 0.6f)).draw(g);

        SysFont.spaced(g, Component.translatable("status.beyondsolo.unevaluated").getString(), cx, y + 92, 11, SystemTheme.FATIGUE_B, true, 2.2f, 0.5f);
        float ly = y + 108;
        for (SysFont.Line line : SysFont.wrap(Component.translatable("status.beyondsolo.unevaluated.desc"), 8.5f, false, Math.min(w - 60, 250))) {
            SysFont.draw(g, line, cx, ly, SystemTheme.TEXT_SOFT, 0.5f);
            ly += 8.5f * SysFont.LINE;
        }
        if (!d.objective().isEmpty()) {
            String objective = Objectives.title(d.objective()).getString();
            float tw = SysFont.width(objective, 8.5f, true);
            float cw = tw + 30;
            float ox = cx - cw / 2f;
            float oy = ly + 8;
            Shapes.pill(ox, oy, cw, 17).fill(Gfx.alpha(SystemTheme.GOLD, 0.14f)).border(0.75f, Gfx.alpha(SystemTheme.GOLD, 0.7f)).glow(5, Gfx.alpha(SystemTheme.GOLD, 0.18f)).draw(g);
            Shapes.icon(Icons.PIN, ox + 11, oy + 8.5f, 9).fill(SystemTheme.GOLD).draw(g);
            SysFont.title(g, objective, ox + 20, SysFont.centerY(oy, 17, 8.5f), 8.5f, SystemTheme.GOLD);
        }
    }

    private static void tooltip(GuiGraphics g, Stat stat, int mouseX, int mouseY) {
        List<SysFont.Line> lines = SysFont.wrap(stat.description(), 8f, false, 150);
        float w = 164;
        float h = 22 + lines.size() * 8f * SysFont.LINE;
        float x = mouseX + 10;
        float y = mouseY - h - 6;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        if (x + w > screenW - 4) x = mouseX - w - 10;
        if (y < 4) y = mouseY + 14;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        Shapes.box(x, y, w, h).radius(7).fillV(0xF8102240, 0xF8081428).border(0.75f, Gfx.alpha(SystemTheme.ACCENT, 0.9f), Gfx.alpha(SystemTheme.ACCENT, 0.35f))
                .glow(8, 0x90000000).draw(g);
        Shapes.icon(Icons.of(stat), x + 11, y + 10.5f, 9).fill(SystemTheme.ACCENT_BRIGHT).draw(g);
        SysFont.title(g, stat.displayName(), x + 19, y + 7.5f, 9, SystemTheme.TEXT);
        float ly = y + 20;
        for (SysFont.Line line : lines) {
            SysFont.draw(g, line, x + 7, ly, SystemTheme.TEXT_SOFT, 0f);
            ly += 8f * SysFont.LINE;
        }
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < plusButtons.length; i++) {
            float[] b = plusButtons[i];
            if (b != null && Math.hypot(mouseX - b[0], mouseY - b[1]) <= 9) {
                PacketDistributor.sendToServer(new AllocateStatPayload(i));
                SystemScreen.playClick();
                return true;
            }
        }
        return false;
    }
}
