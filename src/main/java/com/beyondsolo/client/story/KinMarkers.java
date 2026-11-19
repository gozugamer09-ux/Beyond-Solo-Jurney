package com.beyondsolo.client.story;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.story.FatherEntity;
import com.beyondsolo.story.KinEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The plates over the hunter's family: over their mother a small heart and "Mom" for her own hunter (and what they
 * can do with her when close), "<name>'s mother" for everyone else; over their father, while the light lends him to
 * them, a spark and "Dad" (or "<name>'s father"), in his gold. Drawn like the association's marks, only warmer, and
 * only when near.
 */
public final class KinMarkers {
    public static final int WARM = 0xFFFFB38A;
    /** The father's gold. */
    public static final int LIGHT = 0xFFFFE7A0;
    private static final double REACH = 14;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !ClientConfig.SHOW_HUD.get() || mc.screen != null) return;
        if (StoryOverlay.cinematic()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(partial);
        for (KinEntity k : mc.level.getEntitiesOfClass(KinEntity.class, mc.player.getBoundingBox().inflate(REACH))) {
            if (k.isInvisible()) continue;
            Vec3 at = k.getPosition(partial);
            double over = switch (k.state()) {
                case KinEntity.SLEEPING, KinEntity.STIRRING -> 0.95;
                case KinEntity.SITTING -> 2.05;
                default -> k.getBbHeight() + 0.45;
            };
            Vec3 head = at.add(0, over, 0);
            double dist = head.distanceTo(eye);
            float a = 1 - Mth.clamp((float) (dist - 9) / 5f, 0, 1);
            if (a <= 0.01f) continue;
            float[] p = WorldProjector.project(head);
            if (p == null) continue;
            boolean own = mc.player.getUUID().equals(k.owner());
            String name = own ? Component.translatable("story.beyondsolo.kin.name.own").getString()
                    : Component.translatable("story.beyondsolo.kin.name.other", k.ownerName()).getString();
            float old = Gfx.fade(a);
            float y = plate(g, p[0], p[1], s(dist), k.getId(), name, own ? Icons.HEART : null, WARM, 0xFF2A1A18, 0xFF140C0C);
            hint(g, p[0], y, k, own, dist);
            Gfx.setAlpha(old);
        }
        // their father, while the light lends him to them
        for (FatherEntity f : mc.level.getEntitiesOfClass(FatherEntity.class, mc.player.getBoundingBox().inflate(REACH))) {
            if (f.isInvisible() || f.state() == FatherEntity.RISING) continue;
            Vec3 head = f.getPosition(partial).add(0, f.state() == FatherEntity.KNEELING ? 1.5 : f.getBbHeight() + 0.45, 0);
            double dist = head.distanceTo(eye);
            float a = 1 - Mth.clamp((float) (dist - 9) / 5f, 0, 1);
            if (a <= 0.01f) continue;
            float[] p = WorldProjector.project(head);
            if (p == null) continue;
            boolean own = mc.player.getUUID().equals(f.owner());
            String name = own ? Component.translatable("story.beyondsolo.father.name.own").getString()
                    : Component.translatable("story.beyondsolo.father.name.other", f.ownerName()).getString();
            float old = Gfx.fade(a);
            plate(g, p[0], p[1], s(dist), f.getId(), name, own ? Icons.SPARKLE : null, LIGHT, 0xFF2A2412, 0xFF14100A);
            Gfx.setAlpha(old);
        }
    }

    private static float s(double dist) {
        return Mth.clamp(1.2f - (float) dist / 24f, 0.6f, 1.1f);
    }

    /** One name plate, bobbing a little over (x, y); returns where its bottom edge sits. */
    private static float plate(GuiGraphics g, float x, float y, float s, int seed, String name, @Nullable ResourceLocation icon, int color, int top, int bottom) {
        y += (float) Math.sin(Anim.now() * 1.6 + seed) * 1.2f * s;
        float size = 7f;
        float nw = SysFont.width(name, size, true) + (icon != null ? 22 : 12);
        float h = 11;
        Shapes.pill(x - nw / 2f, y - h, nw, h).fillV(Gfx.alpha(top, 0.86f), Gfx.alpha(bottom, 0.86f))
                .border(0.7f, Gfx.alpha(color, 0.65f)).glow(5, Gfx.alpha(color, 0.18f)).draw(g);
        float tx = x;
        if (icon != null) {
            float beat = 1 + 0.08f * (float) Math.max(0, Math.sin(Anim.now() * 4.2));
            Shapes.icon(icon, x - nw / 2f + 8, y - h / 2f, 7.5f * beat).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.45f), color).draw(g);
            tx += 5;
        }
        SysFont.draw(g, name, tx, SysFont.centerY(y - h, h, size), size, Gfx.mix(color, 0xFFFFFFFF, 0.65f), true, 0.5f);
        return y;
    }

    /** What the hunter can do with their mother, when close. */
    private static void hint(GuiGraphics g, float x, float y, KinEntity k, boolean own, double dist) {
        float near = 1 - Mth.clamp((float) (dist - 4.5) / 2.5f, 0, 1);
        if (own && near > 0.01f) {
            float o2 = Gfx.fade(near);
            String hint = Component.translatable(k.state() == KinEntity.SLEEPING ? "story.beyondsolo.kin.hint.asleep"
                    : "story.beyondsolo.kin.hint.awake").getString();
            float hw = SysFont.width(hint, 6f, false) + 10;
            Shapes.pill(x - hw / 2f, y + 3, hw, 9).fill(Gfx.alpha(0xFF140C0C, 0.7f)).draw(g);
            SysFont.draw(g, hint, x, SysFont.centerY(y + 3, 9, 6f), 6f, Gfx.mix(WARM, 0xFFFFFFFF, 0.45f), false, 0.5f);
            Gfx.setAlpha(o2);
        }
    }

    private KinMarkers() {}
}
