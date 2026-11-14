package com.beyondsolo.client.npc;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.quest.ClientQuests;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.network.NpcMarkersPayload;
import com.beyondsolo.npc.NpcNames;
import com.beyondsolo.npc.NpcQuestKind;
import com.beyondsolo.npc.NpcService;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestArea;
import com.beyondsolo.quest.QuestCategory;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the player sees of villager requests out in the world:
 * <ul>
 *   <li>Markers over villagers' heads, drawn with the UI toolkit and projected from the world: a violet "!" that
 *   bobs when a villager has requests for you, a gold "?" with turning rays when you can hand one in, a quiet
 *   speech bubble while one of theirs is in progress, and a cyan marker with the name over your traveller.</li>
 *   <li>Waypoints for requests with a place (a nest, an escort's destination, a delivery): a column of light and a
 *   turning circle on the ground, plus a label with the distance on screen.</li>
 * </ul>
 */
public final class NpcMarkers {
    public static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    public static final ResourceLocation CIRCLE = BeyondSolo.id("textures/effect/magic_circle.png");
    public static final ResourceLocation RING = BeyondSolo.id("textures/effect/ring.png");

    private static final Map<Integer, Integer> STATES = new HashMap<>();
    private static final Map<Integer, Float> SHOWN = new HashMap<>();
    private static final Map<Integer, Integer> LAST_STATE = new HashMap<>();
    private static final Map<Integer, Double> CHANGED_AT = new HashMap<>();

    public static void update(NpcMarkersPayload payload) {
        Map<Integer, Integer> fresh = new HashMap<>();
        for (int i = 0; i < payload.entities().size(); i++) fresh.put(payload.entities().get(i), payload.states().get(i));
        for (var e : fresh.entrySet()) {
            Integer old = STATES.get(e.getKey());
            if (old == null || !old.equals(e.getValue())) CHANGED_AT.put(e.getKey(), Anim.now());
        }
        STATES.clear();
        STATES.putAll(fresh);
    }

    public static int state(int entityId) {
        return STATES.getOrDefault(entityId, NpcService.MARK_NONE);
    }

    public static void clear() {
        STATES.clear();
        SHOWN.clear();
        LAST_STATE.clear();
        CHANGED_AT.clear();
    }

    // ------------------------------------------------------------------ HUD: markers and waypoint labels

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !ClientConfig.SHOW_HUD.get() || NpcBoardScreen.isOpen()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(partial);

        // fade markers in and out as their state comes and goes
        for (Integer id : STATES.keySet()) SHOWN.putIfAbsent(id, 0f);
        var it = SHOWN.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int state = STATES.getOrDefault(e.getKey(), NpcService.MARK_NONE);
            if (state != NpcService.MARK_NONE) LAST_STATE.put(e.getKey(), state);
            float v = Anim.approach(e.getValue(), state == NpcService.MARK_NONE ? 0 : 1, 6f);
            if (v <= 0.001f && state == NpcService.MARK_NONE) {
                it.remove();
                LAST_STATE.remove(e.getKey());
                continue;
            }
            e.setValue(v);
        }
        for (var e : SHOWN.entrySet()) {
            Entity entity = mc.level.getEntity(e.getKey());
            if (entity == null) continue;
            int state = LAST_STATE.getOrDefault(e.getKey(), NpcService.MARK_NONE);
            Vec3 head = entity.getPosition(partial).add(0, entity.getBbHeight() + 0.5, 0);
            double dist = head.distanceTo(eye);
            float far = 1 - Mth.clamp((float) (dist - 22) / 16f, 0, 1);
            float a = e.getValue() * far;
            if (a <= 0.01f) continue;
            float[] p = WorldProjector.project(head);
            if (p == null) continue;
            float s = Mth.clamp(1.25f - (float) dist / 28f, 0.55f, 1.15f);
            marker(g, p[0], p[1], s, state, a, e.getKey(), entity, dist);
        }

        // waypoint labels
        for (Quest q : ClientQuests.active()) {
            Vec3 at = waypoint(q);
            if (at == null) continue;
            Vec3 label = at.add(0, 3.2, 0);
            float[] p = WorldProjector.project(label);
            if (p == null) continue;
            double dist = Math.sqrt((at.x - eye.x) * (at.x - eye.x) + (at.z - eye.z) * (at.z - eye.z));
            if (dist < 5) continue;
            int color = waypointColor(q);
            float a = Mth.clamp((float) (dist - 5) / 6f, 0, 1);
            float old = Gfx.fade(a);
            String text = Component.translatable("npc.beyondsolo.waypoint." + kindKey(q), Math.round(dist)).getString();
            float tw = SysFont.width(text, 6.5f, true) + 18;
            float bob = (float) Math.sin(Anim.now() * 2.2) * 1.2f;
            Shapes.pill(p[0] - tw / 2f, p[1] - 6 + bob, tw, 12).fillV(Gfx.alpha(0xFF0A1428, 0.82f), Gfx.alpha(0xFF050A16, 0.82f))
                    .border(0.8f, Gfx.alpha(color, 0.8f)).glow(4, Gfx.alpha(color, 0.3f)).draw(g);
            Shapes.icon(NpcBoardScreen.kindIcon(NpcQuestKind.byTemplate(q.template())), p[0] - tw / 2f + 7, p[1] + bob, 7).fill(Gfx.mix(color, 0xFFFFFFFF, 0.4f)).draw(g);
            SysFont.draw(g, text, p[0] - tw / 2f + 13, SysFont.centerY(p[1] - 6 + bob, 12, 6.5f), 6.5f, SystemTheme.TEXT, true, 0f);
            Gfx.setAlpha(old);
        }
    }

    private static void marker(GuiGraphics g, float x, float y, float s, int state, float a, int id, Entity entity, double dist) {
        float old = Gfx.fade(a);
        double changed = CHANGED_AT.getOrDefault(id, -10.0);
        float pop = Anim.outBackStrong(Math.min(1f, Anim.progress(changed, 0.45f)));
        float sc = s * (0.6f + 0.4f * pop);
        float bob = (float) Math.sin(Anim.now() * 2.6 + id) * 2.2f * s;
        y += bob;
        int color;
        ResourceLocation icon;
        float r = 8.5f * sc;
        switch (state) {
            case NpcService.MARK_READY -> {
                color = SystemTheme.GOLD;
                icon = Icons.QUESTION;
                float pulse = Anim.pulse(1.1f);
                Shapes.rays(x, y, 22 * sc, 10).fillV(Gfx.alpha(0xFFFFF4D0, 0.35f), Gfx.alpha(color, 0f)).rotation(Anim.loop(9f)).additive().draw(g);
                Shapes.ring(x, y, r * (1.25f + 0.35f * pulse), 0.8f).fill(Gfx.alpha(color, 0.6f * (1 - pulse))).draw(g);
            }
            case NpcService.MARK_WAITING -> {
                color = SystemTheme.TEXT_SOFT;
                icon = Icons.CHAT;
                r *= 0.8f;
            }
            case NpcService.MARK_TRAVELLER -> {
                color = SystemTheme.ACCENT;
                icon = Icons.PATH;
                r *= 0.85f;
            }
            default -> {
                color = QuestCategory.NPC.color();
                icon = Icons.EXCLAIM;
            }
        }
        float pulse = Anim.pulse(1.6f);
        // a small stem pointing down at the head
        Shapes.box(x - 0.6f * sc, y + r, 1.2f * sc, 4 * sc).fillV(Gfx.alpha(color, 0.8f), Gfx.alpha(color, 0f)).draw(g);
        Shapes.circle(x, y, r).fillV(0xF0162440, 0xF0080F1E).border(1.1f * sc, Gfx.mix(color, 0xFFFFFFFF, 0.25f), Gfx.alpha(color, 0.5f))
                .glow(r * (0.7f + 0.3f * pulse), Gfx.alpha(color, 0.45f)).draw(g);
        Shapes.ring(x, y, r * 1.35f, 0.6f).fill(Gfx.alpha(color, 0.55f)).dashes(10, 0.5f).rotation(Anim.loop(8f)).draw(g);
        Shapes.icon(icon, x, y, r * 1.35f).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.8f), Gfx.mix(color, 0xFFFFFFFF, 0.3f)).glow(r * 0.3f, Gfx.alpha(color, 0.8f)).draw(g);
        // a name plate when close
        float near = 1 - Mth.clamp((float) (dist - 7) / 4f, 0, 1);
        if (near > 0.01f && state != NpcService.MARK_WAITING) {
            float o2 = Gfx.fade(near);
            String name = state == NpcService.MARK_TRAVELLER && entity.hasCustomName() ? entity.getCustomName().getString()
                    : NpcNames.full(entity).getString();
            float nw = SysFont.width(name, 7f, true) + 12;
            float ny = y - r - 15;
            Shapes.pill(x - nw / 2f, ny, nw, 11).fillV(Gfx.alpha(0xFF0C1830, 0.85f), Gfx.alpha(0xFF060C1A, 0.85f))
                    .border(0.7f, Gfx.alpha(color, 0.6f)).draw(g);
            SysFont.draw(g, name, x, SysFont.centerY(ny, 11, 7f), 7f, Gfx.mix(color, 0xFFFFFFFF, 0.7f), true, 0.5f);
            if (state == NpcService.MARK_OFFER || state == NpcService.MARK_READY) {
                String hint = Component.translatable(state == NpcService.MARK_READY ? "npc.beyondsolo.hint.turn_in" : "npc.beyondsolo.hint.talk").getString();
                float hw = SysFont.width(hint, 6f, false) + 10;
                Shapes.pill(x - hw / 2f, y + r + 5, hw, 9).fill(Gfx.alpha(0xFF060C1A, 0.7f)).draw(g);
                SysFont.draw(g, hint, x, SysFont.centerY(y + r + 5, 9, 6f), 6f, Gfx.mix(color, 0xFFFFFFFF, 0.5f), false, 0.5f);
            }
            Gfx.setAlpha(o2);
        }
        Gfx.setAlpha(old);
    }

    /** The place an active request points at (bottom centre), or null. */
    public static Vec3 waypoint(Quest q) {
        Minecraft mc = Minecraft.getInstance();
        if (q.category() != QuestCategory.NPC || q.status() != Quest.Status.ACTIVE || mc.level == null) return null;
        QuestArea area = q.area();
        if (area == null || !area.dimension().equals(mc.level.dimension())) return null;
        NpcQuestKind kind = NpcQuestKind.byTemplate(q.template());
        if (kind == NpcQuestKind.CLEAR && q.isComplete()) return null;
        return Vec3.atBottomCenterOf(area.center());
    }

    private static String kindKey(Quest q) {
        NpcQuestKind k = NpcQuestKind.byTemplate(q.template());
        return k == null ? "place" : k.name().toLowerCase();
    }

    private static int waypointColor(Quest q) {
        NpcQuestKind k = NpcQuestKind.byTemplate(q.template());
        if (k == NpcQuestKind.CLEAR) return 0xFFFF5D6C;
        if (k == NpcQuestKind.DELIVER) return SystemTheme.GOLD;
        return SystemTheme.ACCENT;
    }

    // ------------------------------------------------------------------ world: columns of light and ground circles

    public static void renderWorld(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        List<Quest> active = ClientQuests.active();
        if (active.isEmpty()) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = mc.level.getGameTime() + partial;
        boolean any = false;
        for (Quest q : active) {
            Vec3 at = waypoint(q);
            if (at == null) continue;
            any = true;
            int rgb = waypointColor(q) & 0xFFFFFF;
            float radius = q.area().radius();
            double dist = Math.sqrt(at.distanceToSqr(cam));
            float near = Mth.clamp((float) (dist - 4) / 10f, 0.25f, 1f);
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            // ground: a turning circle the size of the area, and a soft glow
            disc(pose, buffers, CIRCLE, 0.06f, Math.min(radius, 16), time * 0.6f, rgb, 0.55f);
            disc(pose, buffers, GLOW, 0.04f, Math.min(radius, 16) * 1.3f, 0, rgb, 0.35f);
            float pulse = (time % 40) / 40f;
            disc(pose, buffers, RING, 0.08f, 1 + pulse * Math.min(radius, 16), 0, rgb, 0.7f * (1 - pulse));
            // a column of light, turned to face the camera, taller and wider far away so it reads as a waypoint
            float width = 0.9f + (float) Math.min(dist, 160) / 50f;
            float breathe = 0.85f + 0.15f * Mth.sin(time * 0.12f);
            column(pose, buffers, camera, at, cam, width, 56, rgb, 0.7f * near * breathe);
            column(pose, buffers, camera, at, cam, width * 0.3f, 48, 0xFFFFFF, 0.6f * near);
            pose.popPose();
        }
        if (!any) return;
        buffers.endBatch(ModRenderTypes.lightTexture(GLOW));
        buffers.endBatch(ModRenderTypes.lightTexture(CIRCLE));
        buffers.endBatch(ModRenderTypes.lightTexture(RING));
    }

    private static void column(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 at, Vec3 cam, float half, float height, int rgb, float alpha) {
        pose.pushPose();
        float yaw = (float) Math.toDegrees(Math.atan2(cam.x - at.x, cam.z - at.z));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(GLOW));
        Matrix4f m = pose.last().pose();
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        // the glow texture's middle column, stretched up: bright at the bottom, gone at the top
        out.addVertex(m, -half, 0, 0).setUv(0, 0.5f).setColor(r, g, b, a);
        out.addVertex(m, half, 0, 0).setUv(1, 0.5f).setColor(r, g, b, a);
        out.addVertex(m, half, height, 0).setUv(1, 0f).setColor(r, g, b, 0);
        out.addVertex(m, -half, height, 0).setUv(0, 0f).setColor(r, g, b, 0);
        pose.popPose();
    }

    private static void disc(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, float y, float radius, float degrees, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.mulPose(Axis.YP.rotationDegrees(degrees));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(texture));
        Matrix4f m = pose.last().pose();
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        out.addVertex(m, -radius, -radius, 0).setUv(0, 1).setColor(r, g, b, a);
        out.addVertex(m, radius, -radius, 0).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, radius, radius, 0).setUv(1, 0).setColor(r, g, b, a);
        out.addVertex(m, -radius, radius, 0).setUv(0, 0).setColor(r, g, b, a);
        pose.popPose();
    }

    private NpcMarkers() {}
}
