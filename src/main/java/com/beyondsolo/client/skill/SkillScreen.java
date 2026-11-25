package com.beyondsolo.client.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.skill.SkillFeel;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen's answer to a skill's heavy blows and ultimates, drawn over the world (and the hunter's hands) before
 * any of the HUD: a shock ripple running out from a heavy blow, colour fringes at the edges, a zoom blur toward a
 * massive one, the ink frame (the picture gone to stark black and white for a few frames, as an anime cuts to an
 * impact), the focus lines of a manhwa panel round an ultimate's call, and a dark vignette closing in on it.
 *
 * <p>One full-screen pass of {@code shaders/core/skill_screen.fsh} over a copy of what the world drew, and only while
 * something is showing; nothing runs otherwise. Off with the {@code combat.screenEffects} option.</p>
 */
public final class SkillScreen {
    private static ShaderInstance shader;
    private static TextureTarget copy;

    private static final int RIPPLES = 4;

    private static final class Ripple {
        final Vec3 at;
        final double start;
        final float life, reach, strength;

        Ripple(Vec3 at, double start, float life, float reach, float strength) {
            this.at = at;
            this.start = start;
            this.life = life;
            this.reach = reach;
            this.strength = strength;
        }
    }

    private static final List<Ripple> ripples = new ArrayList<>();
    private static double fringeAt = -10;
    private static float fringe;
    private static double inkAt = -10;
    private static int inkTint = 0xFFFFFF;
    private static double linesAt = -10;
    private static float linesLife, lines;
    private static double zoomAt = -10;
    private static float zoomLife, zoom;
    private static Vec3 zoomTo;
    private static double vignetteAt = -10;
    private static float vignetteLife, vignette;
    private static int vignetteColor;

    // ================================================================ moments

    /** A skill's blow landed at {@code at} with weight {@code w} ({@link SkillFeel}). */
    static void impact(Vec3 at, int w, boolean ultimate, boolean killed, SkillElement element, boolean self) {
        if (w < SkillFeel.HEAVY && !ultimate) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dist = mc.player.position().distanceTo(at);
        if (!self && dist > 24) return;
        float near = self ? 1 : 1 - (float) dist / 24f;
        double now = Anim.now();
        boolean massive = w == SkillFeel.MASSIVE;
        ripple(at, now, massive ? 0.55f : 0.38f, massive ? 0.42f : 0.24f, (massive ? 0.022f : 0.012f) * near);
        fringe(now, (massive ? 0.009f : 0.004f) * near);
        if (massive) {
            zoom(now, at, 0.2f, 0.11f * near);
            vignette(now, 0.55f, 0.35f * near, 0x050208);
        }
        // the ink frame: an ultimate's blow, or a massive one that fells (a boss, an elite), at most once a beat
        if ((ultimate || (killed && massive)) && now - inkAt > 1.2) {
            inkAt = now;
            inkTint = com.beyondsolo.client.gfx.Gfx.mix(0xFFFFFFFF, 0xFF000000 | element.color(), 0.25f) & 0xFFFFFF;
            if (self) lines(now, 0.35f, 0.8f);
        }
    }

    /** An ultimate is called by {@code player}: focus lines close in on the screen, the view rushes and darkens. */
    static void ultimate(AbstractClientPlayer player, Skill skill, boolean self) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double dist = mc.player.distanceTo(player);
        if (!self && dist > 24) return;
        float near = self ? 1 : 0.6f * (1 - (float) dist / 24f);
        double now = Anim.now();
        lines(now, 1.15f, near);
        zoom(now, null, 0.32f, 0.08f * near);
        vignette(now, 1.1f, 0.42f * near, 0xFF000000 | Mth.color(0.03f, 0.01f, 0.05f));
        fringe(now, 0.006f * near);
    }

    private static void ripple(Vec3 at, double now, float life, float reach, float strength) {
        if (ripples.size() >= RIPPLES) ripples.remove(0);
        ripples.add(new Ripple(at, now, life, reach, strength));
    }

    private static void fringe(double now, float amount) {
        if (amount > fringeNow(now)) {
            fringe = amount;
            fringeAt = now;
        }
    }

    private static void lines(double now, float life, float amount) {
        if (lines * fade(now, linesAt, linesLife) < amount) {
            linesAt = now;
            linesLife = life;
            lines = amount;
        }
    }

    private static void zoom(double now, Vec3 to, float life, float amount) {
        zoomAt = now;
        zoomLife = life;
        zoom = amount;
        zoomTo = to;
    }

    private static void vignette(double now, float life, float amount, int color) {
        if (amount > vignette * fade(now, vignetteAt, vignetteLife)) {
            vignetteAt = now;
            vignetteLife = life;
            vignette = amount;
            vignetteColor = color;
        }
    }

    private static float fringeNow(double now) {
        float t = (float) (now - fringeAt) / 0.3f;
        return t < 0 || t >= 1 ? 0 : fringe * (1 - t) * (1 - t);
    }

    /** 0..1: in quickly, held, out over the last third. */
    private static float fade(double now, double at, float life) {
        float t = (float) (now - at);
        if (t < 0 || t >= life) return 0;
        float in = Math.min(1, t / 0.06f);
        float out = Math.min(1, (life - t) / (life * 0.35f));
        return in * out;
    }

    private static boolean showing(double now) {
        if (!ripples.isEmpty()) return true;
        return fringeNow(now) > 0 || now - inkAt < INK || fade(now, linesAt, linesLife) > 0 || fade(now, zoomAt, zoomLife) > 0
                || fade(now, vignetteAt, vignetteLife) > 0;
    }

    /** How long the ink frame lasts: inverted, then stark, then gone. */
    private static final float INK = 0.14f;

    // ================================================================ the pass

    static void render(GuiGraphics g, DeltaTracker delta) {
        double now = Anim.now();
        ripples.removeIf(r -> now - r.start >= r.life);
        if (shader == null || !ClientConfig.SCREEN_EFFECTS.get() || !showing(now)) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width, h = main.height;
        if (copy == null) {
            copy = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            copy.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
        } else if (copy.width != w || copy.height != h) {
            copy.resize(w, h, Minecraft.ON_OSX);
        }
        Shapes.flush(g);
        g.flush();
        // what the world (and the hands) drew this frame
        GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, w, h, 0, 0, w, h, org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT, org.lwjgl.opengl.GL11.GL_NEAREST);
        main.bindWrite(true);

        float gw = g.guiWidth(), gh = g.guiHeight();
        for (int i = 0; i < RIPPLES; i++) {
            float[] r = {0, 0, 0, 0};
            if (i < ripples.size()) {
                Ripple rp = ripples.get(i);
                float[] p = WorldProjector.project(rp.at);
                if (p != null) {
                    float t = (float) (now - rp.start) / rp.life;
                    float grow = 1 - (1 - t) * (1 - t);
                    r = new float[]{p[0] / gw, 1 - p[1] / gh, 0.02f + rp.reach * grow, rp.strength * (1 - t)};
                }
            }
            shader.safeGetUniform("Ripple" + i).set(r[0], r[1], r[2], r[3]);
        }
        shader.safeGetUniform("Fringe").set(fringeNow(now));
        float z = zoom * fade(now, zoomAt, zoomLife);
        float zx = 0.5f, zy = 0.5f;
        if (z > 0 && zoomTo != null) {
            float[] p = WorldProjector.project(zoomTo);
            if (p != null) {
                zx = p[0] / gw;
                zy = 1 - p[1] / gh;
            }
        }
        shader.safeGetUniform("Zoom").set(zx, zy, z);
        float ink = (float) (now - inkAt);
        float inkAmount = ink < 0 || ink >= INK ? 0 : ink < INK * 0.75f ? 1 : 1 - (ink - INK * 0.75f) / (INK * 0.25f);
        shader.safeGetUniform("Ink").set(inkAmount, ink < INK * 0.4f ? 1f : 0f);
        shader.safeGetUniform("InkTint").set(((inkTint >> 16) & 255) / 255f, ((inkTint >> 8) & 255) / 255f, (inkTint & 255) / 255f);
        shader.safeGetUniform("Lines").set(lines * fade(now, linesAt, linesLife), (float) (now % 1000), 0.32f);
        float v = vignette * fade(now, vignetteAt, vignetteLife);
        shader.safeGetUniform("Vignette").set(v, ((vignetteColor >> 16) & 255) / 255f, ((vignetteColor >> 8) & 255) / 255f, (vignetteColor & 255) / 255f);

        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, copy.getColorTextureId());
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, 0, gh, 0).setUv(0, 0);
        b.addVertex(m, gw, gh, 0).setUv(1, 0);
        b.addVertex(m, gw, 0, 0).setUv(1, 1);
        b.addVertex(m, 0, 0, 0).setUv(0, 1);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void shaders(RegisterShadersEvent event) {
            shader = null;
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), BeyondSolo.id("skill_screen"), DefaultVertexFormat.POSITION_TEX),
                        loaded -> shader = loaded);
            } catch (Exception e) {
                BeyondSolo.LOGGER.error("Could not load the skill screen shader. Skills land without the screen's ripples and ink frames.", e);
            }
        }

        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            // under every layer: it reshapes the world and the hands, never the HUD
            event.registerBelowAll(BeyondSolo.id("skill_screen"), SkillScreen::render);
            event.registerAboveAll(BeyondSolo.id("skill_cut_in"), SkillCutIn::render);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void leave(ClientPlayerNetworkEvent.LoggingOut event) {
            ripples.clear();
            fringe = 0;
            inkAt = linesAt = zoomAt = vignetteAt = -10;
        }
    }

    private SkillScreen() {}
}
