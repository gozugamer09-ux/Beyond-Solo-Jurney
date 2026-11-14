package com.beyondsolo.client.skill;

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
import com.beyondsolo.network.SkillActionPayload;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillActions;
import com.beyondsolo.skill.SkillData;
import com.beyondsolo.skill.Skills;
import com.beyondsolo.system.SystemData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The SKILLS page of the System window. Left: the class's skills as gems (actives are hexes, passives round,
 * locked ones grey), then the skill bar, where a click puts the selected active skill into a slot. Right: the
 * selected skill: level and proficiency, what it does at this level, mana and cooldown, and how to unlock it.
 */
public class SkillsTab implements SystemTab {
    private static final float TILE = 30;
    private static final float GAP = 4;
    private static final float GRID_W = TILE * 4 + GAP * 3;

    private static String selected = "";
    private String shown = "";
    private double selectedAt = -10;
    private final List<Object[]> tiles = new ArrayList<>();
    private final float[][] slotRects = new float[SkillData.SLOTS][];
    private double flashSlotAt = -10;
    private int flashSlot = -1;

    @Override
    public Component title() {
        return Component.translatable("tab.beyondsolo.skills");
    }

    @Override
    public boolean isVisible() {
        return ClientSystemData.get().isAwakened();
    }

    private static List<Skill> skills() {
        SystemData sys = ClientSystemData.get();
        List<Skill> list = new ArrayList<>(Skills.forClass(sys.playerClass()));
        // skills from another class (e.g. before a job change) still show while known
        for (Skill s : ClientSkills.known()) if (!list.contains(s)) list.add(s);
        return list;
    }

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        tiles.clear();
        SkillData data = ClientSkills.data();
        List<Skill> all = skills();
        Skill sel = Skills.get(selected);
        if (sel == null || !all.contains(sel)) {
            sel = ClientSkills.known().isEmpty() ? all.get(0) : ClientSkills.known().get(0);
            selected = sel.path();
        }
        if (!selected.equals(shown)) {
            shown = selected;
            selectedAt = Anim.now();
        }

        // ---- the gems
        float gy = y;
        gy = grid(g, x, gy, all, true, mouseX, mouseY, data);
        gy = grid(g, x, gy + 2, all, false, mouseX, mouseY, data);

        // ---- the skill bar
        float by = y + h - 36;
        SysFont.spaced(g, Component.translatable("skills.beyondsolo.bar").getString(), x + 2, by, 6.2f, SystemTheme.ACCENT, true, 1.3f, 0f);
        float sw = (GRID_W - GAP * 3) / 4f;
        boolean canAssign = sel.isActive() && data.knows(sel.path());
        for (int i = 0; i < SkillData.SLOTS; i++) {
            float sx = x + i * (sw + GAP);
            float sy = by + 9;
            float sh = 24;
            slotRects[i] = new float[]{sx, sy, sw, sh};
            boolean hover = Ui.inside(mouseX, mouseY, sx, sy, sw, sh);
            Skill in = ClientSkills.slot(i);
            boolean mine = in != null && in == sel;
            float flash = i == flashSlot ? 1 - Anim.progress(flashSlotAt, 0.5f) : 0;
            int c = in != null ? Gfx.opaque(in.color()) : SystemTheme.TEXT_DIM;
            Shapes.box(sx, sy, sw, sh).radius(6).fill(in != null ? 0xD0101C30 : 0x50081020)
                    .border(0.75f, Gfx.alpha(mine ? 0xFFFFFFFF : c, hover && canAssign ? 0.95f : 0.5f))
                    .glow(2 + 6 * flash + (hover && canAssign ? 3 : 0), Gfx.alpha(c, 0.15f + 0.6f * flash)).draw(g);
            if (in != null) SkillEmblem.draw(g, in, sx + sw / 2f, sy + sh / 2f + 1, 7.5f, flash, false);
            else SysFont.draw(g, "+", sx + sw / 2f, SysFont.centerY(sy, sh, 9), 9, SystemTheme.TEXT_DIM, true, 0.5f);
            String key = SkillClientEvents.SLOT_KEYS[i].getTranslatedKeyMessage().getString();
            if (key.length() > 3) key = key.substring(0, 3);
            Ui.keycap(g, sx - 2, sy - 3, 7, key, c);
        }
        String hint = Component.translatable(canAssign ? "skills.beyondsolo.bar.hint" : "skills.beyondsolo.bar.hint_none").getString();
        SysFont.text(g, SysFont.fit(hint, 6f, false, GRID_W), x + 2, by + 36, 6f, SystemTheme.TEXT_DIM);

        // ---- detail
        float dx = x + GRID_W + 10;
        float dw = w - GRID_W - 10;
        float k = Anim.outCubic(Anim.progress(selectedAt, 0.28f));
        float old = Gfx.fade(k);
        g.pose().pushPose();
        g.pose().translate((1 - k) * 10, 0, 0);
        detail(g, sel, data, dx, y, dw, h);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    private float grid(GuiGraphics g, float x, float y, List<Skill> all, boolean actives, int mouseX, int mouseY, SkillData data) {
        List<Skill> list = new ArrayList<>();
        for (Skill s : all) if (s.isActive() == actives) list.add(s);
        int known = 0;
        for (Skill s : list) if (data.knows(s.path())) known++;
        String head = Component.translatable(actives ? "skills.beyondsolo.actives" : "skills.beyondsolo.passives", known, list.size()).getString();
        SysFont.spaced(g, head, x + 2, y + 1, 6.2f, actives ? SystemTheme.ACCENT : SystemTheme.GOLD, true, 1.3f, 0f);
        y += 11;
        long now = ClientSkills.gameTime();
        for (int i = 0; i < list.size(); i++) {
            Skill s = list.get(i);
            float tx = x + (i % 4) * (TILE + GAP);
            float ty = y + (i / 4) * (TILE + GAP);
            boolean isSel = s.path().equals(selected);
            boolean hover = Ui.inside(mouseX, mouseY, tx, ty, TILE, TILE);
            boolean have = data.knows(s.path());
            int c = have ? Gfx.opaque(s.color()) : 0xFF3A4A5E;
            Ui.card(g, tx, ty, TILE, TILE, 7, c, isSel ? 0.55f : hover ? 0.3f : 0.1f);
            if (isSel) Shapes.box(tx, ty, TILE, TILE).radius(7).fill(0).border(0.9f, Gfx.alpha(c, 0.9f)).glow(4, Gfx.alpha(c, 0.3f)).draw(g);
            SkillEmblem.draw(g, s, tx + TILE / 2f, ty + TILE / 2f, 9.5f, isSel ? 0.5f : hover ? 0.3f : 0f, !have);
            if (have) {
                SysFont.draw(g, String.valueOf(data.level(s.path())), tx + TILE - 3, ty + TILE - 8, 6f, Gfx.mix(c, 0xFFFFFFFF, 0.6f), true, 1f);
                var kn = data.get(s.path());
                if (kn != null && now - kn.learnedAt() < 20 * 60 * 3) {
                    Shapes.circle(tx + TILE - 4, ty + 4, 2.2f).fill(SystemTheme.GOLD).glow(2 + 2 * Anim.pulse(1f), Gfx.alpha(SystemTheme.GOLD, 0.7f)).draw(g);
                }
            }
            tiles.add(new Object[]{s.path(), tx, ty});
        }
        int rows = (list.size() + 3) / 4;
        return y + rows * (TILE + GAP);
    }

    private void detail(GuiGraphics g, Skill s, SkillData data, float x, float y, float w, float h) {
        SystemData sys = ClientSystemData.get();
        boolean have = data.knows(s.path());
        int level = Math.max(1, data.level(s.path()));
        int color = have ? Gfx.opaque(s.color()) : 0xFF6A7C92;
        Ui.card(g, x, y, w, h, 9, color, 0.22f);
        float px = x + 10;
        float pw = w - 20;

        SkillEmblem.draw(g, s, px + 17, y + 22, 15, have ? 0.4f + 0.2f * Anim.pulse(2.4f) : 0, !have);
        float tx = px + 40;
        float cy = y + 9;
        float chipX = tx;
        chipX += Ui.chip(g, chipX, cy, 10, Component.translatable(s.isActive() ? "skills.beyondsolo.active" : "skills.beyondsolo.passive").getString(), 6f, color, true) + 4;
        String owner = s.owner() == null ? Component.translatable("skills.beyondsolo.common").getString() : s.owner().displayName().getString();
        chipX += Ui.chip(g, chipX, cy, 10, owner, 6f, SystemTheme.TEXT_SOFT, false) + 4;
        chipX += Ui.chip(g, chipX, cy, 10, Component.translatable("skills.beyondsolo.grade", s.grade().name()).getString(), 6f, SystemTheme.GOLD, false) + 4;
        Ui.chip(g, chipX, cy, 10, s.element().displayName().getString(), 6f, Gfx.opaque(s.element().color()), false);
        cy += 14;
        SysFont.title(g, SysFont.fit(s.displayName().getString(), 12f, true, pw - 40), tx, cy, 12f, have ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT);
        cy += 18;

        // level and proficiency
        if (have) {
            var kn = data.get(s.path());
            boolean max = level >= Skill.MAX_LEVEL;
            String lv = Component.translatable("skills.beyondsolo.level_of", level, Skill.MAX_LEVEL).getString();
            SysFont.title(g, lv, px, cy, 8f, Gfx.mix(color, 0xFFFFFFFF, 0.4f));
            String prof = max ? Component.translatable("skills.beyondsolo.mastered").getString()
                    : Component.translatable("skills.beyondsolo.proficiency", kn.proficiency(), Skill.proficiencyToNext(level)).getString();
            SysFont.draw(g, prof, px + pw, cy + 0.5f, 6.5f, SystemTheme.TEXT_SOFT, false, 1f);
            Ui.bar(g, px, cy + 10, pw, 2.6f, max ? 1 : kn.proficiency() / (float) Skill.proficiencyToNext(level), Gfx.mix(color, 0xFF000000, 0.2f), color);
            cy += 18;
        } else {
            String req;
            int at = s.unlockLevel(sys.rank());
            if (s.advanced()) req = Component.translatable("skills.beyondsolo.locked.job").getString();
            else if (at > 0) req = Component.translatable("skills.beyondsolo.locked.level", at).getString();
            else req = Component.translatable("skills.beyondsolo.locked.tome").getString();
            Shapes.icon(Icons.LOCK, px + 4, cy + 3.5f, 8).fill(SystemTheme.GOLD).draw(g);
            SysFont.text(g, SysFont.fit(req, 7.5f, true, pw - 14), px + 12, cy, 7.5f, SystemTheme.GOLD);
            cy += 10;
            if (!s.advanced()) {
                SysFont.text(g, Component.translatable("skills.beyondsolo.locked.or_tome").getString(), px + 12, cy, 6.5f, SystemTheme.TEXT_DIM);
                cy += 9;
            } else {
                cy += 0;
            }
            cy += 3;
        }

        // what it does
        List<SysFont.Line> desc = SysFont.wrap(s.description(level, sys.rank().tier()), 7.5f, false, pw);
        for (int i = 0; i < Math.min(4, desc.size()); i++) {
            SysFont.draw(g, desc.get(i), px, cy, SystemTheme.TEXT, 0f);
            cy += 7.5f * SysFont.LINE;
        }
        cy += 3;
        List<SysFont.Line> flav = SysFont.wrap(s.flavour(), 6.5f, false, pw);
        for (int i = 0; i < Math.min(2, flav.size()); i++) {
            SysFont.draw(g, flav.get(i), px, cy, SystemTheme.TEXT_DIM, 0f);
            cy += 6.5f * SysFont.LINE;
        }

        // costs
        if (s.isActive()) {
            float sy = y + h - 18;
            float sx = px;
            sx += stat(g, sx, sy, Icons.DROP, Component.translatable("skills.beyondsolo.mana", SkillActions.f(s.manaCost(level))).getString(), 0xFF62D4FF) + 6;
            sx += stat(g, sx, sy, Icons.HOURGLASS, Component.translatable("skills.beyondsolo.cooldown", SkillActions.f(s.cooldown(level) / 20f)).getString(), SystemTheme.TEXT_SOFT) + 6;
            if (have) {
                float left = ClientSkills.cooldownSeconds(s, 0);
                if (left > 0) stat(g, sx, sy, Icons.MOON, Component.translatable("skills.beyondsolo.recharging", SkillActions.f(left)).getString(), SystemTheme.FATIGUE_B);
            }
        } else {
            SysFont.text(g, Component.translatable("skills.beyondsolo.always_on").getString(), px, y + h - 15, 7f, SystemTheme.GOOD);
        }
    }

    private static float stat(GuiGraphics g, float x, float y, net.minecraft.resources.ResourceLocation icon, String text, int color) {
        float tw = SysFont.width(text, 7f, true);
        float w = tw + 20;
        Shapes.pill(x, y, w, 11).fill(Gfx.alpha(color, 0.1f)).border(0.6f, Gfx.alpha(color, 0.5f)).draw(g);
        Shapes.icon(icon, x + 7, y + 5.5f, 7.5f).fill(color).draw(g);
        SysFont.draw(g, text, x + 13, SysFont.centerY(y, 11, 7f), 7f, Gfx.mix(color, 0xFFFFFFFF, 0.3f), true, 0f);
        return w;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Object[] t : tiles) {
            if (Ui.inside(mouseX, mouseY, (float) t[1], (float) t[2], TILE, TILE)) {
                if (!t[0].equals(selected)) {
                    selected = (String) t[0];
                    SystemScreen.playClick();
                }
                return true;
            }
        }
        Skill sel = Skills.get(selected);
        for (int i = 0; i < SkillData.SLOTS; i++) {
            float[] r = slotRects[i];
            if (r == null || !Ui.inside(mouseX, mouseY, r[0], r[1], r[2], r[3])) continue;
            Skill in = ClientSkills.slot(i);
            String put;
            if (button == 1 || (in != null && sel != null && in == sel)) put = ""; // right click or same skill clears it
            else if (sel != null && sel.isActive() && ClientSkills.data().knows(sel.path())) put = sel.path();
            else return true;
            PacketDistributor.sendToServer(new SkillActionPayload(SkillActionPayload.ASSIGN, i, put));
            flashSlot = i;
            flashSlotAt = Anim.now();
            SystemScreen.playClick();
            return true;
        }
        return false;
    }
}
