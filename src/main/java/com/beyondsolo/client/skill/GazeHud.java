package com.beyondsolo.client.skill;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.enemy.ElementalProfile;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.skill.SkillActions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Appraising Gaze: while it lasts, every creature in sight gets a read-out over its head (its level, health, the
 * elements it is weak to and those it shrugs off, from its {@link ElementalProfile}) and a turning reticle on its weak
 * spot, where hits deal more.
 */
public final class GazeHud {
    private static float shown;

    /** A rough level from how tough a creature is, so the read-out can compare it with the player. */
    public static int levelOf(LivingEntity e) {
        double hp = e.getMaxHealth();
        double armor = e.getAttributes().hasAttribute(Attributes.ARMOR) ? e.getAttributeValue(Attributes.ARMOR) : 0;
        double attack = e.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? e.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
        return Math.max(1, (int) Math.round((hp + armor * 2 + attack * 3) / 4));
    }

    /** The elements {@code e} is weak to, worst first (empty for a creature with none). */
    public static List<SkillElement> weaknesses(LivingEntity e) {
        return ElementalProfile.of(e).weaknesses();
    }

    /** Where the weak spot is: the head of upright creatures, the middle of the rest. */
    private static Vec3 weakSpot(LivingEntity e, float partial) {
        boolean upright = e.getBbHeight() > e.getBbWidth() * 1.4f;
        return e.getPosition(partial).add(0, upright ? e.getEyeHeight() : e.getBbHeight() * 0.5, 0);
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        shown = Anim.approach(shown, ClientSkills.hasBuff(SkillActions.BUFF_GAZE) ? 1 : 0, 6f);
        if (shown < 0.02f) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float old = Gfx.fade(shown);
        int gold = 0xFFFFE27A;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof LivingEntity e) || e == mc.player || !e.isAlive() || e instanceof ArmorStand || e.isInvisible()) continue;
            double dist = e.distanceTo(mc.player);
            if (dist > 22 || !mc.player.hasLineOfSight(e)) continue;
            float near = (float) Math.min(1, (22 - dist) / 4);
            float oldE = Gfx.fade(near);

            // reticle on the weak spot
            float[] spot = WorldProjector.project(weakSpot(e, partial));
            if (spot != null) {
                float r = (float) Math.max(3, 12 / Math.max(1, dist * 0.35));
                Shapes.ring(spot[0], spot[1], r, 0.9f).fill(Gfx.alpha(gold, 0.85f)).dashes(4, 0.55f).rotation(Anim.loop(3f)).draw(g);
                Shapes.circle(spot[0], spot[1], 1.2f).fill(gold).glow(3, Gfx.alpha(gold, 0.6f)).draw(g);
            }

            // read-out over the head
            float[] top = WorldProjector.project(e.getPosition(partial).add(0, e.getBbHeight() + 0.45, 0));
            if (top != null) {
                int lvl = levelOf(e);
                int mine = com.beyondsolo.client.ClientSystemData.get().level();
                int lc = lvl > mine + 5 ? SystemTheme.DANGER : lvl > mine ? SystemTheme.FATIGUE_B : SystemTheme.GOOD;
                ElementalProfile prof = ElementalProfile.of(e);
                List<SkillElement> weak = prof.weaknesses();
                List<SkillElement> tough = prof.resistances();
                String name = SysFont.fit(e.getDisplayName().getString(), 6.5f, true, 70);
                String lv = Component.translatable("hud.beyondsolo.level", lvl).getString();
                String wk = Component.translatable("depths.beyondsolo.gaze.weak").getString();
                String rs = Component.translatable("depths.beyondsolo.gaze.resists").getString();
                float iconRow = 8;
                float wkW = weak.isEmpty() ? 0 : SysFont.width(wk, 5.5f, false) + 3 + iconRow * weak.size();
                float rsW = tough.isEmpty() ? 0 : SysFont.width(rs, 5.5f, false) + 3 + iconRow * tough.size();
                float elementsW = wkW + (wkW > 0 && rsW > 0 ? 7 : 0) + rsW;
                float w = Math.max(SysFont.width(lv, 6.5f, true) + 4 + SysFont.width(name, 6.5f, true), elementsW) + 12;
                float h = elementsW > 0 ? 24 : 16;
                float x = top[0] - w / 2f;
                float y = top[1] - h;
                Shapes.box(x, y, w, h).radius(5).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.85f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.85f))
                        .border(0.6f, Gfx.alpha(gold, 0.7f), Gfx.alpha(gold, 0.2f)).draw(g);
                SysFont.draw(g, lv, x + 6, y + 3, 6.5f, lc, true, 0f);
                SysFont.draw(g, name, x + 6 + SysFont.width(lv, 6.5f, true) + 4, y + 3, 6.5f, SystemTheme.TEXT, true, 0f);
                Ui.bar(g, x + 6, y + 11, w - 12, 2f, e.getHealth() / e.getMaxHealth(), SystemTheme.HP_A, SystemTheme.HP_B);
                float ex = x + 6;
                if (!weak.isEmpty()) {
                    SysFont.draw(g, wk, ex, y + 15.5f, 5.5f, 0xFFFFC857, false, 0f);
                    ex += SysFont.width(wk, 5.5f, false) + 3;
                    for (SkillElement el : weak) {
                        int c = Gfx.opaque(el.color());
                        Shapes.icon(com.beyondsolo.client.depths.DepthsIcons.element(el), ex + 3.5f, y + 18.5f, 7f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.2f))
                                .glow(prof.rating(el) == ElementalProfile.Rating.WEAK_SEVERE ? 3 : 1.5f, Gfx.alpha(c, 0.7f)).draw(g);
                        ex += iconRow;
                    }
                    ex += 7;
                }
                if (!tough.isEmpty()) {
                    SysFont.draw(g, rs, ex, y + 15.5f, 5.5f, SystemTheme.TEXT_DIM, false, 0f);
                    ex += SysFont.width(rs, 5.5f, false) + 3;
                    for (SkillElement el : tough) {
                        int c = Gfx.mix(Gfx.opaque(el.color()), 0xFF6F859C, prof.rating(el) == ElementalProfile.Rating.IMMUNE ? 0.7f : 0.45f);
                        Shapes.icon(com.beyondsolo.client.depths.DepthsIcons.element(el), ex + 3.5f, y + 18.5f, 7f).fill(c).draw(g);
                        ex += iconRow;
                    }
                }
            }
            Gfx.setAlpha(oldE);
        }
        Gfx.setAlpha(old);
    }

    private GazeHud() {}
}
