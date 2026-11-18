package com.beyondsolo.client.hud;

import com.beyondsolo.client.gfx.Anim;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import snownee.jade.api.Accessor;
import snownee.jade.api.ui.IBoxElement;
import snownee.jade.api.ui.TooltipRect;

/**
 * Keeps Jade's tooltip (what the crosshair is on, top centre) under our bars at the top of the screen, the way Jade
 * keeps it under vanilla's boss bars: Jade only knows vanilla's, and ours are the System HUD's (see {@link HudSpace}).
 * It glides down when a bar comes and back up when the bars are gone.
 */
final class JadeRoom {
    private static final float GAP = 3;
    private static float shift;

    /** Jade asks this each frame before it draws its tooltip; the tooltip's rect is in GUI pixels. */
    static boolean beforeRender(IBoxElement root, TooltipRect rect, GuiGraphics g, Accessor<?> accessor) {
        Rect2i r = rect.rect;
        // where Jade means it to be this frame (under vanilla's boss bars already, when there are any)
        int base = rect.expectedRect.getY();
        float claimed = HudSpace.topClaimed(r.getX(), r.getX() + r.getWidth());
        shift = Anim.approach(shift, claimed > base ? claimed + GAP - base : 0, 10f);
        // Jade glides its rect toward where it means it to be each frame: holding it down here keeps it under the bars
        if (shift > 0.5f) r.setY(Math.max(r.getY(), base + Math.round(shift)));
        return false;
    }

    private JadeRoom() {}
}
