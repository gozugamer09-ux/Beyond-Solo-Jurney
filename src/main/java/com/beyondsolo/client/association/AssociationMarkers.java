package com.beyondsolo.client.association;

import com.beyondsolo.association.AssociationNpc;
import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * The marks over the association's people: the officer's seal in steel blue and the broker's coins in violet, with
 * their name and what they do once the hunter is close. Drawn like the villagers' request markers.
 */
public final class AssociationMarkers {
    private static final double REACH = 26;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !ClientConfig.SHOW_HUD.get() || mc.screen instanceof AssociationWindow) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(partial);
        for (AssociationNpc npc : mc.level.getEntitiesOfClass(AssociationNpc.class, mc.player.getBoundingBox().inflate(REACH))) {
            if (npc.isInvisible()) continue;
            Vec3 head = npc.getPosition(partial).add(0, npc.getBbHeight() + 0.55, 0);
            double dist = head.distanceTo(eye);
            float a = 1 - Mth.clamp((float) (dist - 16) / 8f, 0, 1);
            if (a <= 0.01f) continue;
            float[] p = WorldProjector.project(head);
            if (p == null) continue;
            float s = Mth.clamp(1.25f - (float) dist / 28f, 0.55f, 1.15f);
            marker(g, p[0], p[1], s, a, npc, dist);
        }
    }

    private static void marker(GuiGraphics g, float x, float y, float s, float a, AssociationNpc npc, double dist) {
        float old = Gfx.fade(a);
        boolean broker = npc.role() == AssociationNpc.Role.BROKER;
        int color = broker ? AssociationWindow.VIOLET : AssociationWindow.STEEL;
        ResourceLocation icon = broker ? Icons.COINS : Icons.SEAL;
        y += (float) Math.sin(Anim.now() * 2.1 + npc.getId()) * 1.8f * s;
        float r = 7.5f * s;
        float pulse = Anim.pulse(1.8f);
        Shapes.box(x - 0.6f * s, y + r, 1.2f * s, 4 * s).fillV(Gfx.alpha(color, 0.8f), Gfx.alpha(color, 0f)).draw(g);
        if (broker) {
            // a broker's mark is a diamond, the association's a disc
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(45));
            Shapes.box(-r * 0.78f, -r * 0.78f, r * 1.56f, r * 1.56f).radius(r * 0.3f).fillV(0xF0201436, 0xF00C0818)
                    .border(1.1f * s, Gfx.mix(color, 0xFFFFFFFF, 0.25f), Gfx.alpha(color, 0.5f)).glow(r * (0.6f + 0.3f * pulse), Gfx.alpha(color, 0.4f)).draw(g);
            g.pose().popPose();
        } else {
            Shapes.circle(x, y, r).fillV(0xF0162440, 0xF0080F1E).border(1.1f * s, Gfx.mix(color, 0xFFFFFFFF, 0.25f), Gfx.alpha(color, 0.5f))
                    .glow(r * (0.6f + 0.3f * pulse), Gfx.alpha(color, 0.4f)).draw(g);
            Shapes.ring(x, y, r * 1.35f, 0.6f).fill(Gfx.alpha(color, 0.5f)).dashes(12, 0.5f).rotation(Anim.loop(10f)).draw(g);
        }
        Shapes.icon(icon, x, y, r * 1.3f).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.8f), Gfx.mix(color, 0xFFFFFFFF, 0.3f)).glow(r * 0.3f, Gfx.alpha(color, 0.7f)).draw(g);
        // name and trade when close
        float near = 1 - Mth.clamp((float) (dist - 7) / 4f, 0, 1);
        if (near > 0.01f) {
            float o2 = Gfx.fade(near);
            String name = npc.getDisplayName().getString();
            float nw = SysFont.width(name, 7f, true) + 12;
            float ny = y - r - 26;
            Shapes.pill(x - nw / 2f, ny, nw, 11).fillV(Gfx.alpha(0xFF0C1830, 0.85f), Gfx.alpha(0xFF060C1A, 0.85f))
                    .border(0.7f, Gfx.alpha(color, 0.6f)).draw(g);
            SysFont.draw(g, name, x, SysFont.centerY(ny, 11, 7f), 7f, Gfx.mix(color, 0xFFFFFFFF, 0.7f), true, 0.5f);
            // the role on a dark tag of its own, so it reads against a bright sky too
            String role = Component.translatable(broker ? "ui.beyondsolo.association.broker.role" : "ui.beyondsolo.association.officer")
                    .getString().toUpperCase(Locale.ROOT);
            float rw = SysFont.spacedWidth(role, 4.6f, true, 1f) + 10;
            Shapes.pill(x - rw / 2f, ny + 12.5f, rw, 8).fillV(Gfx.alpha(Gfx.mix(0xFF0C1830, color, 0.25f), 0.88f), Gfx.alpha(0xFF060C1A, 0.88f))
                    .border(0.6f, Gfx.alpha(color, 0.75f)).draw(g);
            SysFont.spaced(g, role, x, SysFont.centerY(ny + 12.5f, 8, 4.6f), 4.6f, Gfx.mix(color, 0xFFFFFFFF, 0.6f), true, 1f, 0.5f);
            String hint = Component.translatable(broker ? "npc.beyondsolo.association.hint.broker" : "npc.beyondsolo.association.hint.officer").getString();
            float hw = SysFont.width(hint, 6f, false) + 10;
            Shapes.pill(x - hw / 2f, y + r + 5, hw, 9).fill(Gfx.alpha(0xFF060C1A, 0.7f)).draw(g);
            SysFont.draw(g, hint, x, SysFont.centerY(y + r + 5, 9, 6f), 6f, Gfx.mix(color, 0xFFFFFFFF, 0.5f), false, 0.5f);
            Gfx.setAlpha(o2);
        }
        Gfx.setAlpha(old);
    }

    private AssociationMarkers() {}
}
