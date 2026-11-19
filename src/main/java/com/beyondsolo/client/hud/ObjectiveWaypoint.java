package com.beyondsolo.client.hud;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.system.SystemData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Where the objective leads: the Evaluation Zone the System marked for a newcomer ({@code spawn.VillageGuide}).
 * <ul>
 *   <li>In the world, a column of gold light over the crystal, seen through hills and from past the fog (drawn nearer,
 *   on the same line and at the same size on screen, like the gates Gate Sense finds).</li>
 *   <li>On screen, a label where it stands: what it is and how far.</li>
 *   <li>On the objective chip ({@link StatusHud}), an arrow that turns as the player does, and the distance.</li>
 *   <li>On Xaero's minimap and world map when they are installed ({@link XaeroWaypoints}).</li>
 * </ul>
 * All of it fades out as the player arrives.
 */
public final class ObjectiveWaypoint {
    private static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/beam.png");
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    /** Gold, like the objective chip. */
    public static final int COLOR = SystemTheme.GOLD;
    /** Closer than this (blocks, on the map) the player is there: the column and the label are gone. */
    static final float ARRIVED = 9;
    /** The column and the label fade in over this many blocks past {@link #ARRIVED}. */
    private static final float FADE = 14;

    /** The mark in the world the player is in (the middle of the crystal's block, at its foot), or null. */
    @Nullable
    public static Vec3 target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        SystemData d = ClientSystemData.get();
        GlobalPos at = d.objectiveTarget();
        if (at == null || d.objective().isEmpty() || !at.dimension().equals(mc.level.dimension())) return null;
        return Vec3.atBottomCenterOf(at.pos());
    }

    /** Blocks on the map from the player to the mark; NaN when there is none here. */
    public static double distance(float partial) {
        Vec3 at = target();
        if (at == null) return Double.NaN;
        Vec3 me = Minecraft.getInstance().player.getPosition(partial);
        return Math.sqrt((at.x - me.x) * (at.x - me.x) + (at.z - me.z) * (at.z - me.z));
    }

    /** Which way the mark is: degrees clockwise from straight ahead (0 ahead, 90 to the right); NaN when there is none here. */
    public static float bearing(float partial) {
        Vec3 at = target();
        if (at == null) return Float.NaN;
        Player p = Minecraft.getInstance().player;
        Vec3 me = p.getPosition(partial);
        float compass = (float) (Mth.atan2(at.z - me.z, at.x - me.x) * Mth.RAD_TO_DEG) - 90f;
        return Mth.wrapDegrees(compass - p.getViewYRot(partial));
    }

    /** How much of the column and label shows at this distance: none once the player is there. */
    static float shown(double distance) {
        return Double.isNaN(distance) ? 0 : Mth.clamp((float) (distance - ARRIVED) / FADE, 0, 1);
    }

    /** The distance as the HUD writes it ("412 m"). */
    public static String distanceText(double distance) {
        return Component.translatable("hud.beyondsolo.objective.distance", Math.round(distance)).getString();
    }

    // ------------------------------------------------------------------ on screen: the label where it stands

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientConfig.SHOW_HUD.get() || mc.getDebugOverlay().showDebugScreen()) return;
        Vec3 at = target();
        if (at == null) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        double dist = distance(partial);
        float a = shown(dist);
        if (a <= 0.01f) return;
        float[] p = WorldProjector.project(at.add(0, 1.5, 0));
        if (p == null) return;
        float old = Gfx.fade(a);
        float x = p[0];
        float bob = (float) Math.sin(Anim.now() * 2.2) * 1.2f;
        // a short stem down to where it stands, and the label over it
        float y = p[1] - 26 + bob;
        Shapes.box(x - 0.5f, y + 7, 1f, p[1] - y - 9).fillV(Gfx.alpha(COLOR, 0.75f), Gfx.alpha(COLOR, 0f)).draw(g);
        Shapes.circle(x, p[1], 2.2f).fill(Gfx.alpha(COLOR, 0.9f)).glow(4, Gfx.alpha(COLOR, 0.5f)).draw(g);
        String name = Component.translatable("hud.beyondsolo.objective.waypoint").getString();
        String far = distanceText(dist);
        float nw = SysFont.width(name, 6.5f, true);
        float fw = SysFont.width(far, 6f, false);
        float w = 13 + nw + 6 + fw + 7;
        float h = 13;
        float left = x - w / 2f;
        Shapes.pill(left, y - h / 2f, w, h).fillV(Gfx.alpha(0xFF0E1830, 0.86f), Gfx.alpha(0xFF070C1A, 0.86f))
                .border(0.8f, Gfx.alpha(COLOR, 0.85f), Gfx.alpha(COLOR, 0.35f)).glow(5, Gfx.alpha(COLOR, 0.3f + 0.15f * Anim.pulse(1.6f))).draw(g);
        Shapes.icon(Icons.PIN, left + 7.5f, y, 7.5f).fill(COLOR).glow(1.5f, Gfx.alpha(COLOR, 0.6f)).draw(g);
        SysFont.draw(g, name, left + 13, SysFont.centerY(y - h / 2f, h, 6.5f), 6.5f, Gfx.mix(COLOR, 0xFFFFFFFF, 0.55f), true, 0f);
        SysFont.draw(g, far, left + 13 + nw + 6, SysFont.centerY(y - h / 2f, h, 6f), 6f, SystemTheme.TEXT_SOFT, false, 0f);
        Gfx.setAlpha(old);
    }

    // ------------------------------------------------------------------ in the world: the column of light

    public static void renderWorld(PoseStack pose, Camera camera, float partial) {
        Vec3 at = target();
        if (at == null) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = camera.getPosition();
        Vec3 rel = at.subtract(cam);
        double flat = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
        float a = shown(flat);
        if (a <= 0.01f) return;
        // past the fog: nearer along the same line, everything scaled down with it, so it looks the same
        double far = Math.max(48, RenderSystem.getShaderFogEnd() * 0.7);
        float scale = (float) (rel.length() > far ? far / rel.length() : 1);
        rel = rel.scale(scale);
        // the same width on screen however far: about six degrees of soft glow round a bright core
        float width = (float) Math.max(1.0, flat * 0.02) * scale;
        float height = (float) Math.max(110, flat * 0.45) * scale;
        float t = mc.level.getGameTime() + partial;
        float beat = 0.85f + 0.15f * Mth.sin(t * 0.09f);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(rel.x, rel.y, rel.z);
        GateFx.column(buffers.getBuffer(ModRenderTypes.lightThroughWalls(BEAM)), pose, camera, at.x, at.z, width * 2.6f, height, COLOR, 0.55f * a * beat);
        GateFx.column(buffers.getBuffer(ModRenderTypes.lightThroughWalls(BEAM)), pose, camera, at.x, at.z, width * 0.8f, height * 0.9f,
                GateFx.mix(COLOR, 0xFFFFFF, 0.5f), 0.9f * a);
        // a soft glow at its foot
        float glow = (float) Math.max(1.2, flat * 0.02) * scale;
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightThroughWalls(GLOW)), pose, camera, 0, 0.6f * scale, 0, glow * (0.9f + 0.2f * beat), COLOR, 0.6f * a);
        pose.popPose();
        buffers.endBatch(ModRenderTypes.lightThroughWalls(BEAM));
        buffers.endBatch(ModRenderTypes.lightThroughWalls(GLOW));
    }

    // ------------------------------------------------------------------ wiring

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void renderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            renderWorld(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.tickCount % 10 != 0) return;
            Vec3 at = target();
            XaeroWaypoints.show(at == null ? null : BlockPos.containing(at),
                    Component.translatable("hud.beyondsolo.objective.waypoint").getString());
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            XaeroWaypoints.reset();
        }
    }

    private ObjectiveWaypoint() {}
}
