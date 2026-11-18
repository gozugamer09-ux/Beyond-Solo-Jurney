package com.beyondsolo.client.quest;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.situation.Run;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * The System's mark on what a situation quest is about, drawn around the creature itself: four brackets that lock on
 * around something to bring down (in the quest's colour), a soft green ward around someone to keep alive, and the
 * target's health as an arc around either. Found by the network id the run keeps current ({@link Run#TARGET_ID}).
 */
public final class SituationMarkers {
    private record Mark(int color, boolean ward) {}

    private static final int WARD = 0xFF6FE38A;
    private static final Map<Integer, Float> SHOWN = new HashMap<>();
    private static final Map<Integer, Mark> LAST = new HashMap<>();
    private static final Map<Integer, Double> SINCE = new HashMap<>();

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !ClientConfig.SHOW_HUD.get()) return;
        Map<Integer, Mark> wanted = new HashMap<>();
        for (Quest q : ClientQuests.active()) {
            if (q.status() != Quest.Status.ACTIVE || !q.extra().contains(Run.TARGET_ID)) continue;
            boolean ward = q.extra().getBoolean(Run.WARD);
            wanted.putIfAbsent(q.extra().getInt(Run.TARGET_ID), new Mark(ward ? WARD : q.category().color(), ward));
        }
        for (var e : wanted.entrySet()) {
            if (!SHOWN.containsKey(e.getKey())) {
                SHOWN.put(e.getKey(), 0f);
                SINCE.put(e.getKey(), Anim.now());
            }
            LAST.put(e.getKey(), e.getValue());
        }
        if (SHOWN.isEmpty()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(partial);
        var it = SHOWN.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int id = e.getKey();
            Entity entity = mc.level.getEntity(id);
            boolean on = wanted.containsKey(id) && entity != null && entity.isAlive();
            float v = Anim.approach(e.getValue(), on ? 1 : 0, on ? 5f : 3f);
            if (!on && (v <= 0.01f || entity == null)) {
                it.remove();
                LAST.remove(id);
                SINCE.remove(id);
                continue;
            }
            e.setValue(v);
            draw(g, entity, LAST.get(id), v, SINCE.getOrDefault(id, 0.0), partial, eye);
        }
    }

    private static void draw(GuiGraphics g, Entity e, Mark mark, float a, double since, float partial, Vec3 eye) {
        Vec3 feet = e.getPosition(partial);
        float h = e.getBbHeight();
        float[] mid = WorldProjector.project(feet.add(0, h * 0.5, 0));
        if (mid == null) return;
        float[] top = WorldProjector.project(feet.add(0, h, 0));
        float[] bottom = WorldProjector.project(feet);
        float size = top != null && bottom != null ? Math.abs(bottom[1] - top[1]) : 24;
        float r = Mth.clamp(size * 0.62f, 10, 70);
        double dist = Math.sqrt(e.distanceToSqr(eye));
        a *= 1 - Mth.clamp((float) (dist - 48) / 16f, 0, 1);
        if (a <= 0.01f) return;
        // it locks on: closes in from wide and settles with a small overshoot
        float lock = Anim.outBackStrong(Anim.progress(since, 0.5f));
        float rr = r * (1.7f - 0.7f * lock);
        float x = mid[0], y = mid[1];
        int color = mark.color();
        float old = Gfx.fade(a);
        if (mark.ward()) {
            float pulse = Anim.pulse(1.4f);
            Shapes.ring(x, y, rr, 1.0f).fill(Gfx.alpha(color, 0.75f)).glow(3, Gfx.alpha(color, 0.35f)).draw(g);
            Shapes.ring(x, y, rr * (1.06f + 0.14f * pulse), 0.7f).fill(Gfx.alpha(color, 0.5f * (1 - pulse))).draw(g);
            Shapes.ring(x, y, rr + 3.5f, 0.6f).fill(Gfx.alpha(color, 0.45f)).dashes(16, 0.45f).rotation(-Anim.loop(14f)).draw(g);
            Shapes.icon(Icons.SHIELD, x, y - rr - 8, 9).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.7f), color).glow(2, Gfx.alpha(color, 0.6f)).draw(g);
        } else {
            Shapes.ring(x, y, rr, 1.4f).fill(Gfx.alpha(color, 0.9f)).dashes(4, 0.3f).rotation(0.0875f + Anim.loop(10f))
                    .glow(3, Gfx.alpha(color, 0.4f)).draw(g);
            Shapes.ring(x, y, rr * 0.55f, 0.6f).fill(Gfx.alpha(color, 0.3f)).dashes(12, 0.4f).rotation(-Anim.loop(7f)).draw(g);
            float bob = (float) Math.sin(Anim.now() * 3) * 1.5f;
            Shapes.icon(Icons.DIAMOND, x, y - rr - 7 + bob, 7).fillV(Gfx.mix(color, 0xFFFFFFFF, 0.6f), color).glow(2, Gfx.alpha(color, 0.7f)).draw(g);
        }
        if (e instanceof LivingEntity living && living.getMaxHealth() > 0) {
            float hp = Mth.clamp(living.getHealth() / living.getMaxHealth(), 0, 1);
            float hr = rr + 6;
            Shapes.ring(x, y, hr, 1.6f).fill(Gfx.alpha(0xFF050A16, 0.5f)).draw(g);
            Shapes.ring(x, y, hr, 1.6f).fill(QuestText.healthColor(hp)).progress(hp).rotation(0.5f - hp / 2f).draw(g);
        }
        Gfx.setAlpha(old);
    }

    private SituationMarkers() {}
}
