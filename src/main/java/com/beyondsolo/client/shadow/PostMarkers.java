package com.beyondsolo.client.shadow;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.skill.ClientSkills;
import com.beyondsolo.shadow.ShadowRecord;
import com.beyondsolo.skill.Skills;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where the necromancer's guards stand: a small violet shield over each post in this world, with how far away it is.
 * Close by the guard itself is in plain sight, so its mark goes. With Umbral Exchange known, the post the hunter looks
 * toward (the one the skill would trade places with) is marked brighter and says so.
 */
public final class PostMarkers {
    /** Closer than this the guard is in plain sight: no mark. */
    private static final double NEAR = 12;
    /** Same as {@code UmbralExchange.CONE}: how far off the line of sight a post can lie and still be the one meant. */
    private static final double CONE = 22;
    private static final int VIOLET = 0xFF9B7BE0;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !ClientConfig.SHOW_HUD.get() || mc.screen != null) return;
        if (!ShadowClient.canRaise()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(partial);
        ShadowRecord pick = ClientSkills.known().contains(Skills.UMBRAL_EXCHANGE) ? aimed(eye, mc.player.getViewVector(partial)) : null;
        for (ShadowRecord r : ShadowClient.shadows()) {
            if (!r.postedIn(mc.level.dimension())) continue;
            Vec3 post = r.post();
            double dist = post.distanceTo(eye);
            boolean picked = r == pick;
            if (dist < NEAR && !picked) continue;
            float[] p = WorldProjector.project(post.add(0, 2.6, 0));
            if (p == null) continue;
            marker(g, p[0], p[1], r, dist, picked, ShadowClient.isActive(r.id()));
        }
    }

    /** The guard Umbral Exchange would trade places with, looking this way (posts only: one following along is seen). */
    private static @Nullable ShadowRecord aimed(Vec3 eye, Vec3 look) {
        Minecraft mc = Minecraft.getInstance();
        ShadowRecord best = null;
        double bestDot = Math.cos(Math.toRadians(CONE));
        long now = ShadowClient.gameTime();
        for (ShadowRecord r : ShadowClient.shadows()) {
            if (mc.level == null || !r.postedIn(mc.level.dimension()) || r.recovering(now)) continue;
            Vec3 to = r.post().add(0, 1, 0).subtract(eye);
            double d = to.length();
            if (d < 1.5) continue;
            double dot = to.scale(1 / d).dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = r;
            }
        }
        return best;
    }

    private static void marker(GuiGraphics g, float x, float y, ShadowRecord r, double dist, boolean picked, boolean standing) {
        float s = picked ? 1.15f : Mth.clamp(1.05f - (float) dist / 400f, 0.7f, 1f);
        float a = picked ? 1f : standing ? 0.85f : 0.6f;
        float old = Gfx.fade(a);
        int color = picked ? 0xFFC9B4FF : VIOLET;
        float r0 = 6.5f * s;
        float pulse = Anim.pulse(picked ? 3f : 1.4f);
        y += (float) Math.sin(Anim.now() * 1.7 + r.id().hashCode()) * 1.2f * s;
        // a diamond of shadow with the shield in it, a thin line down to where the guard stands
        Shapes.box(x - 0.5f * s, y + r0, 1f * s, 5 * s).fillV(Gfx.alpha(color, 0.7f), Gfx.alpha(color, 0f)).draw(g);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        Shapes.box(-r0 * 0.78f, -r0 * 0.78f, r0 * 1.56f, r0 * 1.56f).radius(r0 * 0.3f).fillV(0xF0180E2C, 0xF0080412)
                .border(1f * s, Gfx.mix(color, 0xFFFFFFFF, 0.25f), Gfx.alpha(color, 0.5f))
                .glow(r0 * (picked ? 0.9f + 0.5f * pulse : 0.5f + 0.2f * pulse), Gfx.alpha(color, picked ? 0.6f : 0.35f)).draw(g);
        g.pose().popPose();
        Shapes.icon(Icons.SHIELD, x, y, r0 * 1.15f).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.8f), Gfx.mix(color, 0xFFFFFFFF, 0.3f)).draw(g);
        // how far, and whose post it is when it is the one the exchange would take
        String far = dist < 1000 ? Math.round(dist) + "m" : String.format(java.util.Locale.ROOT, "%.1fkm", dist / 1000);
        float fw = SysFont.width(far, 5.6f, true) + 8;
        Shapes.pill(x - fw / 2f, y + r0 + 4, fw, 8).fill(Gfx.alpha(0xFF0A0614, 0.75f)).border(0.5f, Gfx.alpha(color, 0.45f)).draw(g);
        SysFont.draw(g, far, x, SysFont.centerY(y + r0 + 4, 8, 5.6f), 5.6f, Gfx.mix(color, 0xFFFFFFFF, 0.55f), true, 0.5f);
        if (picked) {
            String name = r.displayName().getString();
            String hint = Component.translatable("shadows.beyondsolo.post.exchange").getString();
            float nw = Math.max(SysFont.width(name, 6.6f, true), SysFont.width(hint, 5.4f, false)) + 12;
            float ny = y - r0 - 22;
            Shapes.box(x - nw / 2f, ny, nw, 17).radius(4).fillV(Gfx.alpha(0xFF1A1030, 0.9f), Gfx.alpha(0xFF0A0614, 0.9f))
                    .border(0.7f, Gfx.alpha(color, 0.7f)).draw(g);
            SysFont.draw(g, name, x, ny + 2.5f, 6.6f, SystemTheme.TEXT, true, 0.5f);
            SysFont.draw(g, hint, x, ny + 10.5f, 5.4f, Gfx.mix(color, 0xFFFFFFFF, 0.4f), false, 0.5f);
        }
        Gfx.setAlpha(old);
    }

    private PostMarkers() {}
}
