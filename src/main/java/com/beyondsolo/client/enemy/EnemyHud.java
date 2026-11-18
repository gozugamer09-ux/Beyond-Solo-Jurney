package com.beyondsolo.client.enemy;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Affix;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.HiddenLevel;
import com.beyondsolo.enemy.HiddenLevel.Threat;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.gate.DungeonBoss;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Names over the System's creatures. A hunter with the System sees every creature's name over its head, in the colour
 * of how it stands against them ({@link Threat}, from its hidden level): white when it is weaker, orange when it is
 * about their level or a little above, red when it is far stronger. The level itself is never shown.
 *
 * <p>Elites and champions wear a bigger plate: a tag ("ELITE", "CHAMPION") in the variant's colour over the name,
 * their affixes under it. A boss fighting under its bar at the top of the screen has its name there instead (in the
 * same colour), and so does a world boss or monarch ({@code WorldBossHud}). Without the System only elites and champions get a plate, their name in their affinity's colour.
 * Everything is drawn with the System's own font and shapes.</p>
 */
public final class EnemyHud {
    /** How far names are read (the bigger plates of elites and champions a little further). */
    private static final double NAME_RANGE = 20, PLATE_RANGE = 24;

    /** Where the name over one creature sits this frame: its centre and bottom on screen, its size and its fade. */
    private record Spot(float x, float bottom, float scale, float alpha, boolean plate) {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        // a gate's own moments (going through, the boss's entrance, the clear) play on a clean screen
        if (mc.level == null || mc.player == null || mc.options.hideGui || GateOverlay.active()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        boolean names = showsNames();
        float hunter = HiddenLevel.hunterLevel(ClientSystemData.get());
        for (var entry : EnemyClient.all().int2ObjectEntrySet()) {
            Entity e = mc.level.getEntity(entry.getIntKey());
            if (!(e instanceof LivingEntity le)) continue;
            EnemyTraits t = entry.getValue();
            Spot s = spot(mc, le, t, partial, names);
            if (s == null) continue;
            int level = EnemyClient.level(le.getId());
            Threat threat = names && level >= 0 ? Threat.of(level, hunter) : null;
            float old = Gfx.fade(s.alpha);
            if (s.plate) plate(g, le, t, s, threat);
            else tag(g, le, t, s, threat);
            Gfx.setAlpha(old);
        }
    }

    /** True when this player reads creatures' names: they have the System, and names are on in the client config. */
    public static boolean showsNames() {
        return ClientConfig.ENEMY_NAMES.get() && ClientSystemData.get().hasSystem();
    }

    /**
     * The colour this player sees a creature's name in: its threat's (white, orange, red), or {@code fallback} when it
     * has no hidden level or the player reads no names. ARGB.
     */
    public static int nameColor(LivingEntity e, int fallback) {
        int level = EnemyClient.level(e.getId());
        if (level < 0 || !showsNames()) return fallback;
        return 0xFF000000 | Threat.of(level, HiddenLevel.hunterLevel(ClientSystemData.get())).color();
    }

    /**
     * The point just over the name drawn above a creature this frame ({x, y} on screen), for what stacks on top of it
     * (the buildup meters of a fight), or null if it has none.
     */
    @Nullable
    public static float[] above(LivingEntity e, float partial) {
        Minecraft mc = Minecraft.getInstance();
        EnemyTraits t = EnemyClient.traits(e.getId());
        if (mc.player == null || t == null || mc.options.hideGui || GateOverlay.active()) return null;
        Spot s = spot(mc, e, t, partial, showsNames());
        if (s == null) return null;
        return new float[]{s.x, s.bottom - height(s) - 2 * s.scale};
    }

    // ------------------------------------------------------------------ placing

    @Nullable
    private static Spot spot(Minecraft mc, LivingEntity e, EnemyTraits t, float partial, boolean names) {
        if (!e.isAlive() || e.isInvisible() || e == mc.getCameraEntity()) return null;
        boolean plate = t.variant().special();
        boolean named = names && EnemyClient.level(e.getId()) >= 0;
        if (!plate && !named) return null;
        // a boss fighting under its bar has its name up there; a great threat always has its own bar or marker
        if (named && e instanceof DungeonBoss && e.getId() == GateOverlay.barBossId()) return null;
        if (e instanceof com.beyondsolo.worldboss.ColossalBoss) return null;
        double range = plate ? PLATE_RANGE : NAME_RANGE;
        double dist = mc.player.distanceTo(e);
        if (dist > range || !mc.player.hasLineOfSight(e)) return null;
        Vec3 pos = e.getPosition(partial).add(0, e.getBbHeight() + (plate ? 1.05 : 0.4), 0);
        float[] p = WorldProjector.project(pos);
        if (p == null) return null;
        float scale = (float) Math.max(0.7, Math.min(1.15, 8.0 / Math.max(1, dist)));
        float fade = dist > range - 6 ? (float) (range - dist) / 6f : 1f;
        return new Spot(p[0], p[1], scale, fade, plate);
    }

    /** How tall the name (and a plate's tag over it) stands above its spot. */
    private static float height(Spot s) {
        if (!s.plate) return TAG_SIZE * 1.6f * s.scale;
        float boxH = PLATE_SIZE * s.scale * 1.7f;
        float tagH = 5f * s.scale * 1.6f;
        return boxH + tagH + 1.5f * s.scale;
    }

    // ------------------------------------------------------------------ drawing

    private static final float TAG_SIZE = 6.6f;
    private static final float BOSS_TAG_SIZE = 7.6f;
    private static final float PLATE_SIZE = 7.5f;

    /**
     * "Frostbound Gloomfang": the language file decides the order ("Sabueso Umbrío de Escarcha"). A boss goes by the name
     * on its bar, a creature with a name of its own (an echo of the job trial) by that. Plain text: the threat colours it.
     */
    private static Component name(LivingEntity e, EnemyTraits t) {
        if (e instanceof DungeonBoss) return Component.literal(e.getDisplayName().getString());
        if (e.hasCustomName()) return Component.literal(e.getCustomName().getString());
        if (t.affinity() == Affinity.NONE) return e.getType().getDescription();
        return Component.translatable("enemy.beyondsolo.named", t.affinity().adjective(), e.getType().getDescription());
    }

    /** An ordinary creature's name: a slim dark capsule, the name in its threat's colour, a red one glowing. */
    private static void tag(GuiGraphics g, LivingEntity e, EnemyTraits t, Spot s, @Nullable Threat threat) {
        if (threat == null) return;
        int c = 0xFF000000 | threat.color();
        float size = (e instanceof DungeonBoss ? BOSS_TAG_SIZE : TAG_SIZE) * s.scale;
        Component name = name(e, t);
        float w = SysFont.width(name, size, true) + 9 * s.scale;
        float h = size * 1.6f;
        float x0 = s.x - w / 2f, y0 = s.bottom - h;
        var box = Shapes.pill(x0, y0, w, h).fillV(0xB810141E, 0xB806080E);
        switch (threat) {
            case WEAKER -> box.border(0.6f * s.scale, 0x40FFFFFF);
            case EVEN -> box.border(0.7f * s.scale, Gfx.alpha(c, 0.7f)).glow(3 * s.scale, Gfx.alpha(c, 0.22f));
            case FAR_STRONGER -> box.border(0.8f * s.scale, Gfx.alpha(c, 0.9f))
                    .glow((3.5f + 2f * Anim.pulse(1.1f)) * s.scale, Gfx.alpha(c, 0.3f + 0.2f * Anim.pulse(1.1f)));
        }
        box.draw(g);
        SysFont.draw(g, name, s.x, SysFont.centerY(y0, h, size), size, c, true, 0.5f);
    }

    /** An elite's or champion's plate: its variant's tag over its name, its affixes under. */
    private static void plate(GuiGraphics g, LivingEntity e, EnemyTraits t, Spot s, @Nullable Threat threat) {
        float scale = s.scale;
        int vc = 0xFF000000 | t.variant().color();
        int nc = threat != null ? 0xFF000000 | threat.color()
                : 0xFF000000 | (t.affinity() == Affinity.NONE ? 0xE8ECF5 : t.affinity().color);
        Component name = name(e, t);
        float nameSize = PLATE_SIZE * scale;
        float tagSize = 5f * scale;
        float nameW = SysFont.width(name, nameSize, true);
        String tag = t.variant().title().getString().toUpperCase();
        float pad = 6 * scale;
        float boxW = nameW + pad * 2;
        float boxH = nameSize * 1.7f;
        float y0 = s.bottom - boxH;
        float cx = s.x;
        boolean champion = t.variant() == Variant.CHAMPION;
        float glow = champion ? 4 + 2 * Anim.pulse(1.2f) : 3;
        Shapes.box(cx - boxW / 2, y0, boxW, boxH).radius(boxH / 2).fillV(0xC0101420, 0xC0060810)
                .border(0.8f * scale, Gfx.alpha(vc, 0.8f)).glow(glow * scale, Gfx.alpha(vc, champion ? 0.55f : 0.35f)).draw(g);
        if (threat == Threat.FAR_STRONGER) {
            // far stronger: a red edge inside the variant's frame
            Shapes.box(cx - boxW / 2 + 1.2f * scale, y0 + 1.2f * scale, boxW - 2.4f * scale, boxH - 2.4f * scale).radius(boxH / 2 - 1.2f * scale)
                    .fill(0).border(0.6f * scale, Gfx.alpha(nc, 0.45f + 0.3f * Anim.pulse(1.1f))).draw(g);
        }
        SysFont.draw(g, name, cx, y0 + (boxH - nameSize) / 2f, nameSize, nc, true, 0.5f);
        // the variant tag sits above the plate, clear of the name
        float tagH = tagSize * 1.6f;
        float tagW = SysFont.spacedWidth(tag, tagSize, true, 1.2f) + 8 * scale;
        float ty = y0 - tagH - 1.5f * scale;
        Shapes.pill(cx - tagW / 2, ty, tagW, tagH).fill(Gfx.alpha(Gfx.shade(vc, -0.6f), 0.95f)).border(0.6f * scale, vc).draw(g);
        SysFont.spaced(g, tag, cx, ty + (tagH - tagSize) / 2f, tagSize, vc, true, 1.2f, 0.5f);
        // affixes under the plate, on their own dark strip so they read against the sky
        var affixes = t.affixes();
        if (!affixes.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (Affix a : affixes) {
                if (!b.isEmpty()) b.append("  ·  ");
                b.append(a.title().getString());
            }
            String text = b.toString();
            float as = 6f * scale;
            float aw = SysFont.width(text, as, true) + 8 * scale;
            float ah = as * 1.6f;
            float ay = y0 + boxH + 1.5f * scale;
            Shapes.pill(cx - aw / 2, ay, aw, ah).fill(0xB0080A12).draw(g);
            SysFont.draw(g, text, cx, ay + (ah - as) / 2f, as, 0xFFE4E8F0, true, 0.5f);
        }
    }

    private EnemyHud() {}
}
