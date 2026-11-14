package com.beyondsolo.client.combat;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.network.CombatFxPayload;
import com.beyondsolo.network.CombatStatePayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Combat on screen:
 * <ul>
 *   <li>the player's own buildup gauges and statuses, in a rail next to the status capsule;</li>
 *   <li>buildup meters and status icons over creatures in a fight;</li>
 *   <li>damage numbers ({@link FloatingNumbers});</li>
 *   <li>the screen-edge tint of a deep burn, heavy venom or a brand, and the flash of a pop or a cure.</li>
 * </ul>
 */
public final class CombatHud {
    public static final int[] COLORS = {0xFFFF7A2E, 0xFF7CCF3A, 0xFFE0306E, 0xFFD3172A};
    public static final ResourceLocation[] ICONS = {Icons.FLAME, Icons.FLASK, Icons.SPIRAL, Icons.DROP};

    /** One slot in the player's rail. */
    private static final class Slot {
        final String key;
        float shown;
        float visible;
        double since = Anim.now();
        boolean live;
        /** Label kept from while the status was active, so it doesn't change while fading out. */
        String label;

        Slot(String key) {
            this.key = key;
        }
    }

    private static final List<Slot> RAIL = new ArrayList<>();
    private static float burnTint, venomTint, brandTint;
    private static double flashAt = -10;
    private static int flashColor;
    private static float flashStrength;
    private static double ringAt = -10;
    private static int ringColor;

    // ---- events ----

    /** A one-off effect landed on this client's player: flash the screen to match. */
    public static void selfFx(CombatFxPayload.Kind kind, int param) {
        switch (kind) {
            case BLOOD_POP -> flash(0xFFB0101E, 0.85f);
            case IGNITE -> flash(0xFFFF6A1A, 0.7f);
            case HEAVY_VENOM -> flash(0xFF3E7A12, 0.7f);
            case CRIMSON_BURST -> flash(0xFFC2185B, 0.8f);
            case VENOM_STACK -> flash(0xFF7CCF3A, 0.25f);
            case CURE -> {
                ringAt = Anim.now();
                ringColor = 0xFF000000 | DamageElement.byIndex(param).color();
                flash(Gfx.mix(ringColor, 0xFFFFFFFF, 0.5f), 0.35f);
            }
            default -> {}
        }
    }

    private static void flash(int color, float strength) {
        flashAt = Anim.now();
        flashColor = color;
        flashStrength = strength;
    }

    // ---- drawing ----

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        screenEffects(g, mc);
        if (ClientConfig.ENEMY_METERS.get()) enemyMeters(g, mc, delta.getGameTimeDeltaPartialTick(true));
        FloatingNumbers.render(g);
        if (ClientConfig.SHOW_HUD.get() && !mc.getDebugOverlay().showDebugScreen()) rail(g, mc);
    }

    private static void screenEffects(GuiGraphics g, Minecraft mc) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        CombatStatePayload me = ClientCombatState.data(mc.player);
        boolean effects = ClientConfig.SCREEN_EFFECTS.get();
        burnTint = Anim.approach(burnTint, effects && me != null && me.has(CombatStatePayload.DEEP_BURN) ? 1 : 0, 3f);
        venomTint = Anim.approach(venomTint, effects && me != null && me.has(CombatStatePayload.HEAVY_VENOM) ? 1 : 0, 3f);
        brandTint = Anim.approach(brandTint, effects && me != null && me.has(CombatStatePayload.CRIMSON_BRAND) ? 1 : 0, 4f);
        if (burnTint > 0.01f) {
            float flicker = 0.75f + 0.25f * Anim.pulse(1.3f) + 0.08f * (float) Math.sin(Anim.now() * 17.0);
            Shapes.vignette(0, 0, w, h, 0.55f, 1.3f).fill(Gfx.alpha(0xFFFF5A12, 0.42f * burnTint * flicker)).draw(g);
        }
        if (venomTint > 0.01f) {
            Shapes.vignette(0, 0, w, h, 0.45f, 1.25f).fill(Gfx.alpha(0xFF2E6A10, 0.5f * venomTint * (0.8f + 0.2f * Anim.pulse(2.4f)))).draw(g);
        }
        if (brandTint > 0.01f) {
            Shapes.vignette(0, 0, w, h, 0.65f, 1.35f).fill(Gfx.alpha(0xFFC2185B, 0.38f * brandTint * (0.7f + 0.3f * Anim.pulse(0.9f)))).draw(g);
        }
        float f = 1 - Anim.progress(flashAt, 0.7f);
        if (f > 0 && effects) {
            float a = Anim.inCubic(f) * flashStrength;
            Shapes.vignette(0, 0, w, h, 0.2f, 1.1f).fill(Gfx.alpha(flashColor, 0.85f * a)).draw(g);
            Shapes.box(0, 0, w, h).fill(Gfx.alpha(flashColor, 0.18f * a)).draw(g);
        }
        float r = Anim.progress(ringAt, 0.9f);
        if (r < 1) {
            float e = Anim.outCubic(r);
            float old = Gfx.fade(1 - r);
            Shapes.ring(w / 2f, h / 2f, 20 + e * Math.max(w, h) * 0.45f, 2.5f + 6 * (1 - e)).fill(Gfx.alpha(ringColor, 0.7f))
                    .glow(10, Gfx.alpha(ringColor, 0.6f)).additive().draw(g);
            Gfx.setAlpha(old);
        }
    }

    /** Buildup meters and status icons floating over creatures that are in a fight. */
    private static void enemyMeters(GuiGraphics g, Minecraft mc, float partial) {
        for (var entry : ClientCombatState.all().int2ObjectEntrySet()) {
            ClientCombatState.State s = entry.getValue();
            Entity e = mc.level.getEntity(entry.getIntKey());
            if (!(e instanceof LivingEntity living) || e == mc.player || !living.isAlive()) continue;
            boolean any = !s.data.isEmpty();
            for (int i = 0; i < 4; i++) s.shown[i] = Anim.approach(s.shown[i], s.target(i), 10f);
            s.visible = Anim.approach(s.visible, any ? 1 : 0, any ? 8f : 3f);
            if (s.visible < 0.02f) continue;
            double dist = mc.player.distanceTo(e);
            if (dist > 28) continue;
            Vec3 pos = e.getPosition(partial).add(0, e.getBbHeight() + 0.55, 0);
            float[] p = WorldProjector.project(pos);
            if (p == null) continue;
            float scale = (float) Math.max(0.75, Math.min(1.3, 9.0 / Math.max(1, dist)));
            float old = Gfx.fade(s.visible * (dist > 22 ? (float) (28 - dist) / 6f : 1f));
            drawMeters(g, s, p[0], p[1], scale);
            Gfx.setAlpha(old);
        }
    }

    private static void drawMeters(GuiGraphics g, ClientCombatState.State s, float cx, float bottom, float scale) {
        CombatStatePayload d = s.data;
        float barW = 38 * scale;
        float barH = 3.6f * scale;
        float gap = 2.2f * scale;
        float y = bottom;
        float fresh = 1 - Anim.outCubic(Anim.progress(s.lastRise, 0.5f));
        // bars, bottom-up
        for (int i = 3; i >= 0; i--) {
            float v = s.shown[i];
            if (v < 0.01f) continue;
            y -= barH + gap;
            float near = Math.max(0, (v - 0.7f) / 0.3f);
            float bx = cx - barW / 2f + 5 * scale;
            Shapes.pill(bx - 1, y - 1, barW + 2, barH + 2).fill(0x00000000).glow(4 * scale, Gfx.alpha(COLORS[i], 0.2f + 0.5f * near * Anim.pulse(0.5f) + fresh * 0.4f)).draw(g);
            Ui.bar(g, bx, y, barW, barH, v, Gfx.shade(COLORS[i], -0.25f), Gfx.mix(COLORS[i], 0xFFFFFFFF, 0.25f + 0.3f * near));
            Shapes.icon(ICONS[i], bx - 5.5f * scale, y + barH / 2f, 6.5f * scale).fill(Gfx.mix(COLORS[i], 0xFFFFFFFF, 0.3f))
                    .glow(1.5f * scale, Gfx.alpha(COLORS[i], 0.7f)).draw(g);
        }
        // statuses above the bars
        List<int[]> chips = new ArrayList<>();
        if (d.has(CombatStatePayload.DEEP_BURN)) chips.add(new int[]{0, 0});
        if (d.has(CombatStatePayload.HEAVY_VENOM)) chips.add(new int[]{1, 99});
        else if (d.venomStacks() > 0) chips.add(new int[]{1, d.venomStacks()});
        if (d.has(CombatStatePayload.CRIMSON_BRAND)) chips.add(new int[]{2, 0});
        if (chips.isEmpty()) return;
        float r = 6.5f * scale;
        float step = r * 2 + 3 * scale;
        float x = cx - (chips.size() - 1) * step / 2f;
        float cy = y - r - 3 * scale;
        for (int[] c : chips) {
            int color = c[1] == 99 ? 0xFF4E9A1E : COLORS[c[0]];
            float pulse = Anim.pulse(c[0] == 0 ? 0.8f : 1.4f);
            Shapes.circle(x, cy, r).fillV(Gfx.alpha(color, 0.55f), Gfx.alpha(Gfx.shade(color, -0.5f), 0.7f))
                    .border(0.8f * scale, Gfx.mix(color, 0xFFFFFFFF, 0.4f)).glow(3 * scale + 2 * pulse * scale, Gfx.alpha(color, 0.55f)).draw(g);
            ResourceLocation icon = c[1] == 99 ? Icons.SKULL : ICONS[c[0]];
            Shapes.icon(icon, x, cy, r * 1.2f).fill(0xFFFFFFFF).draw(g);
            if (c[0] == 1 && c[1] != 99) {
                // venom stacks as pips under the chip
                for (int k = 0; k < c[1]; k++) {
                    float px = x - (c[1] - 1) * 2.4f * scale + k * 4.8f * scale;
                    Shapes.circle(px, cy + r + 2.4f * scale, 1.3f * scale).fill(0xFFB8F57A).glow(1.5f * scale, 0x907CCF3A).draw(g);
                }
            }
            x += step;
        }
    }

    // ---- the player's own rail ----

    private static Slot slot(String key) {
        for (Slot s : RAIL) if (s.key.equals(key)) return s;
        Slot s = new Slot(key);
        RAIL.add(s);
        return s;
    }

    private static void rail(GuiGraphics g, Minecraft mc) {
        CombatStatePayload me = ClientCombatState.data(mc.player);
        for (Slot s : RAIL) s.live = false;
        if (me != null) {
            float[] v = {me.burn(), me.venom(), me.crimson(), me.blood()};
            for (int i = 0; i < 4; i++) {
                if (v[i] > 0.01f) {
                    Slot s = slot("b" + i);
                    s.live = true;
                    s.shown = Anim.approach(s.shown, v[i], 10f);
                }
            }
        }
        // statuses come from the player's own effect list, which also has their time left
        if (mc.player.hasEffect(ModEffects.DEEP_BURN)) slot("deep_burn").live = true;
        if (mc.player.hasEffect(ModEffects.VENOM)) slot("venom").live = true;
        if (mc.player.hasEffect(ModEffects.HEAVY_VENOM)) slot("heavy_venom").live = true;
        if (mc.player.hasEffect(ModEffects.CRIMSON_BRAND)) slot("crimson_brand").live = true;

        float x = ClientSystemData.get().isAwakened() ? 8 + 118 + 8 : 8;
        float cy = 8 + 15;
        RAIL.removeIf(s -> !s.live && s.visible < 0.02f);
        for (Slot s : RAIL) {
            s.visible = Anim.approach(s.visible, s.live ? 1 : 0, s.live ? 9f : 5f);
            if (s.live && s.visible < 0.05f) s.since = Anim.now();
            float in = Anim.outBack(s.visible);
            float old = Gfx.fade(s.visible);
            float width = drawSlot(g, mc, s, x, cy, in);
            Gfx.setAlpha(old);
            x += width * s.visible + 5 * s.visible;
        }
    }

    /** Draws one rail slot with its left edge at x. Returns the width it took. */
    private static float drawSlot(GuiGraphics g, Minecraft mc, Slot s, float x, float cy, float in) {
        float r = 11 * (0.6f + 0.4f * in);
        float cx = x + 11;
        if (s.key.startsWith("b")) {
            int i = s.key.charAt(1) - '0';
            int c = COLORS[i];
            float near = Math.max(0, (s.shown - 0.7f) / 0.3f);
            float shake = near > 0 ? (float) Math.sin(Anim.now() * 40) * 0.6f * near : 0;
            Shapes.circle(cx + shake, cy, r).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.88f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.88f))
                    .glow(5 + 6 * near * Anim.pulse(0.45f), Gfx.alpha(c, 0.25f + 0.5f * near)).draw(g);
            Shapes.ring(cx + shake, cy, r - 1.4f, 2.2f).fill(0x30FFFFFF).draw(g);
            Shapes.ring(cx + shake, cy, r - 1.4f, 2.2f).fillAround(Gfx.shade(c, -0.2f), Gfx.mix(c, 0xFFFFFFFF, 0.35f)).progress(s.shown)
                    .glow(3, Gfx.alpha(c, 0.7f)).draw(g);
            Shapes.icon(ICONS[i], cx + shake, cy, r * 0.95f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.3f)).glow(1.5f, Gfx.alpha(c, 0.6f)).draw(g);
            return 22;
        }
        int color;
        ResourceLocation icon;
        String label;
        String sub = null;
        MobEffectInstance inst;
        switch (s.key) {
            case "deep_burn" -> {
                color = COLORS[0];
                icon = Icons.FLAME;
                label = Component.translatable("effect.beyondsolo.deep_burn").getString();
                sub = Component.translatable("hud.beyondsolo.needs_elixir").getString();
                inst = null;
            }
            case "venom" -> {
                color = COLORS[1];
                icon = Icons.FLASK;
                inst = mc.player.getEffect(ModEffects.VENOM);
                int stacks = inst == null ? 1 : inst.getAmplifier() + 1;
                label = Component.translatable("effect.beyondsolo.venom").getString() + " " + new String[]{"I", "II", "III", "IV"}[Math.min(3, stacks - 1)];
            }
            case "heavy_venom" -> {
                color = 0xFF4E9A1E;
                icon = Icons.SKULL;
                label = Component.translatable("effect.beyondsolo.heavy_venom").getString();
                sub = Component.translatable("hud.beyondsolo.needs_greater").getString();
                inst = mc.player.getEffect(ModEffects.HEAVY_VENOM);
            }
            default -> {
                color = COLORS[2];
                icon = Icons.SPIRAL;
                label = Component.translatable("effect.beyondsolo.crimson_brand").getString();
                inst = mc.player.getEffect(ModEffects.CRIMSON_BRAND);
            }
        }
        if (s.live || s.label == null) s.label = label;
        else label = s.label;
        float pulse = Anim.pulse(s.key.equals("deep_burn") ? 0.7f : 1.2f);
        float labelW = Math.max(SysFont.width(label, 7.5f, true), sub == null ? 0 : SysFont.width(sub, 6f, false));
        float w = 2 * r + 8 + labelW + 8;
        // capsule
        Shapes.pill(x, cy - r, w, 2 * r).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.9f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.9f))
                .border(0.75f, Gfx.alpha(color, 0.85f), Gfx.alpha(color, 0.3f)).glow(5 + 4 * pulse, Gfx.alpha(color, 0.18f + 0.15f * pulse)).draw(g);
        Shapes.circle(cx, cy, r - 1.5f).fillV(Gfx.alpha(color, 0.75f), Gfx.alpha(Gfx.shade(color, -0.5f), 0.85f)).glow(3 + 3 * pulse, Gfx.alpha(color, 0.6f)).draw(g);
        if (inst != null && !inst.isInfiniteDuration()) {
            float left = Math.min(1f, inst.getDuration() / (float) Math.max(1, maxDuration(s.key)));
            Shapes.ring(cx, cy, r - 1.5f, 1.4f).fill(Gfx.mix(color, 0xFFFFFFFF, 0.6f)).progress(left).draw(g);
        }
        Shapes.icon(icon, cx, cy, r * 1.05f).fill(0xFFFFFFFF).draw(g);
        float tx = x + 2 * r + 6;
        if (sub == null) {
            SysFont.title(g, label, tx, SysFont.centerY(cy - r, 2 * r, 7.5f), 7.5f, Gfx.mix(color, 0xFFFFFFFF, 0.55f));
        } else {
            SysFont.title(g, label, tx, cy - 6.5f, 7.5f, Gfx.mix(color, 0xFFFFFFFF, 0.55f));
            SysFont.text(g, sub, tx, cy + 1.5f, 6f, SystemTheme.TEXT_SOFT);
        }
        return w;
    }

    private static int maxDuration(String key) {
        return switch (key) {
            case "venom" -> com.beyondsolo.combat.Statuses.VENOM_TICKS;
            case "heavy_venom" -> com.beyondsolo.combat.Statuses.HEAVY_VENOM_TICKS;
            default -> com.beyondsolo.combat.Statuses.BRAND_TICKS;
        };
    }

    public static void reset() {
        RAIL.clear();
        burnTint = venomTint = brandTint = 0;
        flashAt = ringAt = -10;
    }

    private CombatHud() {}
}
