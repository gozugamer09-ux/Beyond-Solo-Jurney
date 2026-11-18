package com.beyondsolo.client.hud;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.combat.CombatHud;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.notify.NotificationRenderer;
import com.google.common.collect.Ordering;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Status effect icons for the System HUD, in vanilla's corner and rows (good effects on top, bad ones under them),
 * as glass discs with a ring that runs down with the time left, the time under them and the level beside them.
 * New effects pop in, the rest glide over when one ends, and an effect about to run out blinks like vanilla's.
 *
 * <p>Pieces that share the top-right corner ask {@link #bottom()} and {@link #left()} where the icons end; those
 * follow vanilla's icons while the System HUD is off.</p>
 */
public final class EffectsHud {
    private static final int CELL = 24;
    private static final int STEP = 25;
    private static final int ROW = 26;

    private static final class Slot {
        float x = Float.NaN;
        float y;
        double since = Anim.now();
        int full;
        int lastDuration;
    }

    private static final Map<Holder<MobEffect>, Slot> SLOTS = new HashMap<>();
    private static float bottom;
    private static float left = Float.MAX_VALUE;

    /** Where the effect icons end, in GUI pixels from the top (0 when there are none). */
    public static float bottom() {
        return bottom;
    }

    /** The left edge of the effect icons (the screen's width or more when there are none). */
    public static float left() {
        return left;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        bottom = 0;
        left = Float.MAX_VALUE;
        if (mc.player == null) return;
        boolean system = VitalsHud.active();
        if (mc.screen instanceof EffectRenderingInventoryScreen<?> s && s.canSeeEffects()) {
            if (system) SLOTS.clear();
            return;
        }
        List<MobEffectInstance> effects = Ordering.natural().<MobEffectInstance>reverse().sortedCopy(mc.player.getActiveEffects());
        int w = g.guiWidth();
        // the unread badge of hidden pop-ups sits in this corner too: the icons go under it
        float top = (mc.isDemo() ? 15 : 0) + (system ? NotificationRenderer.badgeBottom() : 0);
        int good = 0;
        int bad = 0;
        Set<Holder<MobEffect>> seen = new HashSet<>();
        for (MobEffectInstance e : effects) {
            if (!e.showIcon() || !IClientMobEffectExtensions.of(e).isVisibleInGui(e)) continue;
            // burn, venom and the crimson brand already have their meters on the combat rail
            if (system && CombatHud.onRail(e)) continue;
            boolean beneficial = e.getEffect().value().isBeneficial();
            int index = beneficial ? ++good : ++bad;
            float x = w - STEP * index;
            float y = top + 1 + (beneficial ? 0 : ROW);
            bottom = Math.max(bottom, y + CELL);
            left = Math.min(left, x);
            if (!system || mc.options.hideGui) continue;
            seen.add(e.getEffect());
            Slot slot = SLOTS.computeIfAbsent(e.getEffect(), k -> new Slot());
            draw(g, mc, e, slot, x, y);
        }
        SLOTS.keySet().retainAll(seen);
    }

    private static void draw(GuiGraphics g, Minecraft mc, MobEffectInstance e, Slot slot, float x, float y) {
        // glide to the slot (a new one starts there)
        if (Float.isNaN(slot.x)) {
            slot.x = x;
            slot.y = y;
        }
        slot.x = Anim.approach(slot.x, x, 14f);
        slot.y = Anim.approach(slot.y, y, 14f);
        int duration = e.getDuration();
        boolean infinite = e.isInfiniteDuration();
        // the ring runs from the longest time this effect had since it last went up
        if (duration > slot.lastDuration + 20 || slot.full <= 0) slot.full = Math.max(1, duration);
        slot.lastDuration = duration;

        Holder<MobEffect> effect = e.getEffect();
        boolean beneficial = effect.value().isBeneficial();
        int color = Gfx.opaque(effect.value().getColor());
        int rim = Gfx.mix(color, 0xFFFFFFFF, 0.25f);
        boolean ambient = e.isAmbient();
        float cx = slot.x + CELL / 2f;
        float cy = slot.y + CELL / 2f;

        float pop = Anim.progress(slot.since, 0.45f);
        float scale = 0.55f + 0.45f * Anim.outBack(pop);
        float fresh = 1 - pop;
        // vanilla's blink in the last ten seconds
        float alpha = 1f;
        if (!ambient && !infinite && e.endsWithin(200)) {
            int l = 10 - duration / 20;
            alpha = Math.clamp(duration / 10f / 5f * 0.5f, 0f, 0.5f)
                    + (float) Math.cos(duration * Math.PI / 5.0) * Math.clamp(l / 10f * 0.25f, 0f, 0.25f);
            alpha = 0.35f + 0.65f * Math.clamp(alpha * 2f, 0f, 1f);
        }

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.pose().translate(-cx, -cy, 0);
        float old = Gfx.fade(Math.min(1, pop * 2.5f));

        float r = 10.6f;
        int glassTop = beneficial ? 0xE8142642 : 0xE8341420;
        int glassBottom = beneficial ? 0xEC08101E : 0xEC12060C;
        Shapes.circle(cx, cy + 1, r).fill(0).glow(5, 0x60000000).draw(g);
        Shapes.circle(cx, cy, r).fillV(glassTop, glassBottom)
                .border(0.6f, Gfx.alpha(rim, ambient ? 0.35f : 0.55f), Gfx.alpha(color, 0.2f))
                .glow(2.5f + 5 * fresh, Gfx.alpha(color, 0.18f + 0.5f * fresh)).draw(g);
        float left = infinite || ambient ? 1f : Math.clamp(duration / (float) slot.full, 0f, 1f);
        Shapes.ring(cx, cy, r - 0.2f, 1.5f).fill(0x26FFFFFF).draw(g);
        if (ambient) {
            // beacons and conduits: a calm dotted ring, no clock
            Shapes.ring(cx, cy, r - 0.2f, 1.2f).fill(Gfx.alpha(rim, 0.6f)).dashes(16, 0.5f).rotation(Anim.loop(18f)).draw(g);
        } else {
            Shapes.ring(cx, cy, r - 0.2f, 1.5f).fillAround(Gfx.mix(color, 0xFFFFFFFF, 0.55f), color).progress(left)
                    .glow(2f, Gfx.alpha(color, 0.55f * alpha)).draw(g);
        }

        // the effect's own icon, as vanilla draws it (or the effect's mod does)
        int ix = Math.round(cx) - 9;
        int iy = Math.round(cy) - 9;
        float a = alpha * Gfx.alpha();
        IClientMobEffectExtensions ext = IClientMobEffectExtensions.of(e);
        RenderSystem.enableBlend();
        if (!ext.renderGuiIcon(e, mc.gui, g, ix - 3, iy - 3, 0, a)) {
            TextureAtlasSprite sprite = mc.getMobEffectTextures().get(effect);
            g.setColor(1f, 1f, 1f, a);
            g.blit(ix + 1, iy + 1, 0, 16, 16, sprite);
            g.setColor(1f, 1f, 1f, 1f);
        }
        RenderSystem.disableBlend();

        // level, as a small roman numeral at the top right
        if (e.getAmplifier() > 0) {
            int level = e.getAmplifier() + 1;
            String text = level <= 10 ? Component.translatable("enchantment.level." + level).getString() : String.valueOf(level);
            float tw = SysFont.width(text, 5f, true);
            float bw = Math.max(7.5f, tw + 3.5f);
            float bx = cx + r - bw + 3.5f;
            float by = slot.y - 1.5f;
            Shapes.pill(bx, by, bw, 7).fillV(0xF0182A44, 0xF0060C18).border(0.5f, Gfx.alpha(rim, 0.8f)).draw(g);
            SysFont.draw(g, text, bx + bw / 2f, SysFont.centerY(by, 7, 5f), 5f, Gfx.mix(color, 0xFFFFFFFF, 0.7f), true, 0.5f);
        }
        // time left, on a little plate under the disc
        if (!infinite && !ambient) {
            String text = time(duration);
            float tw = SysFont.width(text, 4.8f, true);
            float pw = tw + 4.5f;
            float px = cx - pw / 2f;
            float py = slot.y + CELL - 5.6f;
            boolean ending = e.endsWithin(200);
            Shapes.pill(px, py, pw, 6.4f).fillV(0xF0101C30, 0xF0060A14)
                    .border(0.5f, ending ? Gfx.alpha(SystemTheme.DANGER, 0.5f + 0.5f * Anim.pulse(0.5f)) : Gfx.alpha(rim, 0.45f)).draw(g);
            SysFont.draw(g, text, cx, SysFont.centerY(py, 6.4f, 4.8f), 4.8f, ending ? 0xFFFFD0D0 : SystemTheme.TEXT, true, 0.5f);
        }
        Gfx.setAlpha(old);
        g.pose().popPose();
    }

    private static String time(int ticks) {
        int s = (ticks + 19) / 20;
        if (s >= 3600) return s / 3600 + "h";
        if (s >= 60) return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
        return s + "s";
    }

    public static void reset() {
        SLOTS.clear();
        bottom = 0;
        left = Float.MAX_VALUE;
    }

    private EffectsHud() {}
}
