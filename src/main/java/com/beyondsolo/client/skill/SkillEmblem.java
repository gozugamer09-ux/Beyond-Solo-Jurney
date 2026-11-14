package com.beyondsolo.client.skill;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.skill.Skill;
import net.minecraft.client.gui.GuiGraphics;

/** How a skill looks everywhere: a dark gem with the skill's icon, a hex frame and a turning ring in its colour. */
public final class SkillEmblem {
    /**
     * @param energy 0..1, extra glow (just learned, ready, hovered)
     * @param locked draws it grey with a lock
     */
    public static void draw(GuiGraphics g, Skill s, float cx, float cy, float radius, float energy, boolean locked) {
        int color = locked ? 0xFF56677A : Gfx.opaque(s.color());
        int light = Gfx.mix(color, 0xFFFFFFFF, 0.45f);
        boolean passive = !s.isActive();
        // frame: actives are hexes, passives round
        if (passive) {
            Shapes.circle(cx, cy, radius).fillV(0xF0142238, 0xF0070D1A).border(Math.max(0.75f, radius * 0.07f), color, Gfx.fade(color, 0.4f))
                    .glow(radius * (0.35f + 0.6f * energy), Gfx.alpha(color, 0.25f + 0.45f * energy)).draw(g);
        } else {
            Shapes.icon(Icons.HEXAGON, cx, cy, radius * 2.55f).fillV(0xF0162640, 0xF0060C18)
                    .border(Math.max(0.6f, radius * 0.06f), color).glow(radius * (0.35f + 0.6f * energy), Gfx.alpha(color, 0.3f + 0.45f * energy)).draw(g);
        }
        if (!locked) {
            Shapes.ring(cx, cy, radius * 1.18f, Math.max(0.5f, radius * 0.05f)).fill(Gfx.alpha(color, 0.45f + 0.4f * energy))
                    .dashes(passive ? 24 : 12, 0.4f).rotation(Anim.loop(passive ? 22f : 14f) * (passive ? -1 : 1)).draw(g);
        }
        Shapes.icon(SkillIcons.of(s), cx, cy, radius * 1.25f)
                .fillV(locked ? 0xFF7D8DA0 : Gfx.mix(color, 0xFFFFFFFF, 0.8f), locked ? 0xFF50606F : light)
                .glow(radius * 0.2f + radius * 0.25f * energy, Gfx.alpha(color, locked ? 0.1f : 0.6f)).draw(g);
        if (locked) {
            Shapes.circle(cx + radius * 0.62f, cy + radius * 0.62f, radius * 0.36f).fill(0xF0101826).border(0.6f, 0xFF56677A).draw(g);
            Shapes.icon(Icons.LOCK, cx + radius * 0.62f, cy + radius * 0.62f, radius * 0.5f).fill(SystemTheme.TEXT_SOFT).draw(g);
        }
    }

    private SkillEmblem() {}
}
