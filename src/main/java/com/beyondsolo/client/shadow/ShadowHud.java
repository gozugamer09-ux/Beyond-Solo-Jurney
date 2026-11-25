package com.beyondsolo.client.shadow;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.hud.HudSpace;
import com.beyondsolo.network.ShadowFxPayload;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.shadow.ShadowRank;
import com.beyondsolo.shadow.ShadowRecord;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.shadow.SoulAim;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The shadow army on screen: the "extract" prompt over a soul in reach, the ritual's progress ring, the banners
 * (extraction, refusal, fading soul, rank-up; a boss shadow gets a cinematic one with letterbox bars) and a small
 * army counter next to the hotbar. Drawn with the System toolkit.
 */
public final class ShadowHud {
    /** A banner; {@code boss} ones are cinematic (bars, a title card and {@code note} under it in gold). */
    private record Moment(ShadowFxPayload.Kind kind, String title, String sub, int color, boolean boss, String note) {
        Moment(ShadowFxPayload.Kind kind, String title, String sub, int color, boolean boss) {
            this(kind, title, sub, color, boss, boss ? tr("shadow.beyondsolo.hud.elite") : "");
        }

        float duration() {
            return boss ? 4.2f : kind == ShadowFxPayload.Kind.RANK_UP ? 3.2f : 2.4f;
        }
    }

    /** Essence that just came in (shown over the army counter), and when. */
    private static int essenceGain;
    private static double essenceAt = -10;

    private static final Deque<Moment> QUEUE = new ArrayDeque<>();
    private static Moment current;
    private static double startedAt;

    static void handle(ShadowFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ShadowClient.canRaise()) return;
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        boolean near = mc.player.position().distanceToSqr(at) < 14 * 14;
        switch (p.kindEnum()) {
            case EXTRACTED -> {
                if (!near) return;
                boolean boss = p.extra() == 1;
                push(new Moment(p.kindEnum(), tr(boss ? "shadow.beyondsolo.hud.extracted_boss" : "shadow.beyondsolo.hud.extracted"),
                        ShadowRecord.displayName(p.text()).getString(), p.color(), boss));
            }
            case FAILED -> {
                if (!near) return;
                push(new Moment(p.kindEnum(), tr("shadow.beyondsolo.hud.failed"),
                        Component.translatable("shadow.beyondsolo.hud.tries", p.extra()).getString(), 0xFF5D6C, false));
            }
            case CRUMBLE -> {
                if (!near) return;
                push(new Moment(p.kindEnum(), tr("shadow.beyondsolo.hud.crumble"), tr("shadow.beyondsolo.hud.crumble.sub"), 0x8C86A8, false));
            }
            case RANK_UP -> {
                Entity e = p.id() >= 0 && mc.level != null ? mc.level.getEntity(p.id()) : null;
                if (e instanceof ShadowEntity s && !mc.player.getUUID().equals(s.getOwnerUUID())) return;
                ShadowRank r = ShadowRank.byOrdinal(p.extra());
                push(new Moment(p.kindEnum(), tr("shadow.beyondsolo.hud.rank_up"),
                        ShadowRecord.displayName(p.text()).getString() + "  →  " + r.displayName().getString().toUpperCase(), r.color(), false));
            }
            case PROMOTED -> {
                Entity e = p.id() >= 0 && mc.level != null ? mc.level.getEntity(p.id()) : null;
                if (e instanceof ShadowEntity s && !mc.player.getUUID().equals(s.getOwnerUUID())) return;
                ShadowRank r = ShadowRank.byOrdinal(p.extra());
                String note = Component.translatable(r.ordinal() >= ShadowRank.COMMANDER.ordinal() ? "shadow.beyondsolo.hud.promoted.leads"
                        : "shadow.beyondsolo.hud.promoted.power", String.format(java.util.Locale.ROOT, "%.2f", r.power())).getString();
                push(new Moment(p.kindEnum(), tr("shadow.beyondsolo.hud.promoted"),
                        ShadowRecord.displayName(p.text()).getString() + "  →  " + r.displayName().getString().toUpperCase(), r.color(), true, note));
            }
            case NAMED -> {
                Entity e = mc.level != null ? mc.level.getEntity(p.id()) : null;
                if (!(e instanceof ShadowEntity s) || !mc.player.getUUID().equals(s.getOwnerUUID())) return;
                push(new Moment(p.kindEnum(), tr("shadow.beyondsolo.hud.named"), ShadowRecord.displayName(p.text()).getString(), p.color(), false));
            }
            case ESSENCE -> {
                if (p.extra() != mc.player.getId()) return;
                int n;
                try {
                    n = Integer.parseInt(p.text());
                } catch (NumberFormatException ex) {
                    return;
                }
                essenceGain = Anim.since(essenceAt) < 2f ? essenceGain + n : n;
                essenceAt = Anim.now();
            }
            default -> {
            }
        }
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static void push(Moment m) {
        if (current == null) {
            current = m;
            startedAt = Anim.now();
        } else if (QUEUE.size() < 6) QUEUE.add(m);
    }

    public static void reset() {
        QUEUE.clear();
        current = null;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ShadowClient.canRaise()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        soulPrompt(g, mc, partial);
        armyChip(g);
        banner(g);
    }

    // ---------------------------------------------------------------- the soul in reach

    private static void soulPrompt(GuiGraphics g, Minecraft mc, float partial) {
        if (mc.screen != null) return;
        float now = ShadowWorldFx.now(partial);
        // the ritual running; else the soul the key reaches (the one looked at, else the nearest), as the server picks
        // it; else the one meant but too far
        ShadowWorldFx.Soul s = ShadowWorldFx.inRitual(mc.player.getPosition(partial), now);
        boolean far = false;
        if (s == null) {
            SoulAim.Pick<ShadowWorldFx.Soul> pick = ShadowWorldFx.aim(mc.player, partial, now);
            s = pick.soul();
            if (s == null) {
                s = pick.far();
                far = true;
            }
        }
        if (s == null) return;
        float[] p = WorldProjector.project(s.pos.add(0, 1.7, 0));
        if (p == null) return;
        float in = Mth.clamp((now - s.born) / 8f, 0, 1);
        float old = Gfx.fade(in);
        int c = Gfx.opaque(s.color);
        float cx = p[0], cy = p[1];
        // over the soul, but under whatever holds the top of the screen (a boss's bars, the report of a great kill)
        float under = HudSpace.topClaimedLately(cx - 60, cx + 60);
        if (under > 0 && cy - 18 < under + 4) cy = under + 22;
        if (s.inRitual(now)) {
            float k = s.ritualProgress(now);
            Shapes.circle(cx, cy, 13).fill(0xC0080612).border(0.75f, Gfx.alpha(c, 0.6f)).glow(6 + 6 * k, Gfx.alpha(c, 0.4f)).draw(g);
            Shapes.ring(cx, cy, 13, 2.2f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.3f)).progress(k).glow(4, Gfx.alpha(c, 0.8f)).draw(g);
            Shapes.ring(cx, cy, 17, 0.8f).fill(Gfx.alpha(c, 0.6f)).dashes(14, 0.5f).rotation(Anim.loop(2f)).draw(g);
            Shapes.icon(Icons.SKULL, cx, cy, 13).fill(Gfx.mix(c, 0xFFFFFFFF, 0.5f)).glow(3, Gfx.alpha(c, 0.7f)).draw(g);
            SysFont.spaced(g, tr("shadow.beyondsolo.hud.extracting"), cx, cy + 20, 6.5f, Gfx.mix(c, 0xFFFFFFFF, 0.4f), true, 1.6f, 0.5f);
        } else if (far) {
            // out of reach: a dimmer card saying to come closer, no key on it
            String name = typeName(s.type);
            String action = tr("shadow.beyondsolo.hud.closer");
            float tw = Math.max(SysFont.width(action, 7f, true), SysFont.width(name, 6.5f, false));
            float w = tw + 16;
            float h = 28;
            float x = cx - w / 2, y = cy - h / 2;
            float dim = Gfx.fade(0.8f);
            Shapes.box(x, y, w, h).radius(7).fillV(0xC0120A22, 0xC0070410).border(0.75f, Gfx.alpha(c, 0.45f), Gfx.alpha(c, 0.15f)).draw(g);
            SysFont.title(g, action, x + 8, y + 6, 7f, SystemTheme.TEXT_SOFT);
            SysFont.text(g, name, x + 8, y + 17, 6.5f, Gfx.mix(c, 0xFFFFFFFF, 0.3f));
            Gfx.setAlpha(dim);
        } else {
            String name = typeName(s.type);
            String key = ShadowClientEvents.RISE.getTranslatedKeyMessage().getString().toUpperCase();
            String action = tr("shadow.beyondsolo.hud.extract");
            float tw = Math.max(SysFont.width(action, 7.5f, true) + Ui.keycapWidth(11, key) + 6, SysFont.width(name, 6.5f, false));
            float w = tw + 22;
            float h = 30;
            float x = cx - w / 2, y = cy - h / 2;
            float bob = Mth.sin((float) Anim.now() * 2.5f) * 1.2f;
            y += bob;
            Shapes.box(x, y, w, h).radius(7).fillV(0xE0120A22, 0xE0070410).border(0.75f, Gfx.alpha(c, 0.8f), Gfx.alpha(c, 0.3f))
                    .glow(6 + 2 * Anim.pulse(1.6f), Gfx.alpha(c, 0.35f)).draw(g);
            float kx = x + 8;
            kx += Ui.keycap(g, kx, y + 5, 11, key, c) + 5;
            SysFont.title(g, action, kx, y + 6.5f, 7.5f, SystemTheme.TEXT);
            SysFont.text(g, name, x + 8, y + 19, 6.5f, Gfx.mix(c, 0xFFFFFFFF, 0.4f));
            // tries left as small flames
            for (int i = 0; i < ShadowService.TRIES; i++) {
                boolean left = i < s.tries;
                Shapes.circle(x + w - 8 - i * 6, y + 21.5f, 1.8f).fill(left ? Gfx.mix(c, 0xFFFFFFFF, 0.3f) : 0x40FFFFFF)
                        .glow(left ? 2 : 0, Gfx.alpha(c, 0.8f)).draw(g);
            }
            // its last seconds: a thread along the bottom burning down as the soul fades
            float time = s.timeLeft(now);
            if (time < 1f) {
                Shapes.pill(x + 8, y + h - 3.5f, w - 16, 1.2f).fill(0x30FFFFFF).draw(g);
                Shapes.pill(x + 8, y + h - 3.5f, Math.max(1.2f, (w - 16) * time), 1.2f).fill(Gfx.alpha(0xFF5060, 0.95f))
                        .glow(2, Gfx.alpha(0xFF5060, 0.6f)).draw(g);
            }
        }
        Gfx.setAlpha(old);
    }

    static String typeName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        EntityType<?> t = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
        return t == null ? id : t.getDescription().getString();
    }

    // ---------------------------------------------------------------- the army counter

    private static void armyChip(GuiGraphics g) {
        int out = ShadowClient.activeCount();
        String text = out + "/" + ShadowClient.limit();
        float w = SysFont.width(text, 7f, true) + 22;
        float h = 14;
        float x = com.beyondsolo.client.hud.VitalsHud.hotbarLeft(g.guiWidth()) - 6 - w;
        float y = g.guiHeight() - h - 4;
        int c = 0xFF9B7BE0;
        if (!ShadowClient.shadows().isEmpty()) {
            Shapes.pill(x, y, w, h).fill(0xB00A0614).border(0.75f, Gfx.alpha(c, out > 0 ? 0.8f : 0.35f)).glow(out > 0 ? 4 : 0, Gfx.alpha(c, 0.3f)).draw(g);
            Shapes.icon(Icons.SKULL, x + 8, y + h / 2, 8).fill(out > 0 ? Gfx.mix(c, 0xFFFFFFFF, 0.4f) : SystemTheme.TEXT_DIM).draw(g);
            SysFont.draw(g, text, x + 15, SysFont.centerY(y, h, 7f), 7f, out > 0 ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, 0f);
        }
        // essence drifting in from the souls left to fade: "+12" rising over the counter
        float t = Anim.since(essenceAt);
        if (t < 2f && essenceGain > 0) {
            float a = Anim.clamp01(t / 0.15f) * Anim.clamp01((2f - t) / 0.6f);
            float old = Gfx.fade(a);
            String gain = "+" + essenceGain;
            float gy = y - 9 - Anim.outCubic(Anim.clamp01(t / 0.8f)) * 5;
            float gw = SysFont.width(gain, 6.6f, true);
            Shapes.icon(Icons.DIAMOND, x + w / 2 - gw / 2 - 4, gy + 3.5f, 7).fill(0xFFC9A6FF).glow(3, 0x90B07CFF).draw(g);
            SysFont.draw(g, gain, x + w / 2 + 3, gy, 6.6f, 0xFFE8DCFF, true, 0.5f);
            Gfx.setAlpha(old);
        }
    }

    // ---------------------------------------------------------------- banners

    private static void banner(GuiGraphics g) {
        if (current == null) return;
        float t = Anim.since(startedAt);
        float dur = current.duration();
        if (t > dur) {
            current = QUEUE.poll();
            startedAt = Anim.now();
            if (current == null) return;
            t = 0;
            dur = current.duration();
        }
        float in = Anim.outBack(Anim.clamp01(t / 0.35f));
        float out = Anim.clamp01((dur - t) / 0.4f);
        float alpha = Anim.clamp01(t / 0.15f) * out;
        int c = Gfx.opaque(current.color);
        float cx = g.guiWidth() / 2f;
        float old = Gfx.fade(alpha);
        if (current.boss) {
            // cinema bars and a centred title card
            float bars = Anim.outCubic(Anim.clamp01(t / 0.5f)) * out;
            float bh = g.guiHeight() * 0.11f * bars;
            Shapes.box(0, 0, g.guiWidth(), bh).fill(0xF0000000).draw(g);
            Shapes.box(0, g.guiHeight() - bh, g.guiWidth(), bh).fill(0xF0000000).draw(g);
            float cy = g.guiHeight() * 0.70f;
            Shapes.rays(cx, cy, 120 * in, 18).fill(Gfx.alpha(c, 0.12f)).rotation(Anim.loop(30f)).draw(g);
            Shapes.circle(cx, cy - 26, 15).fillV(0xF0140A24, 0xF0060310).border(1, c).glow(10 + 4 * Anim.pulse(1.2f), Gfx.alpha(c, 0.5f)).draw(g);
            Shapes.icon(current.kind == ShadowFxPayload.Kind.PROMOTED ? Icons.CROWN : Icons.SKULL, cx, cy - 26, 17)
                    .fill(Gfx.mix(c, 0xFFFFFFFF, 0.5f)).glow(4, Gfx.alpha(c, 0.8f)).draw(g);
            float s = 14 * (0.85f + 0.15f * in);
            SysFont.spaced(g, current.title, cx, cy - 4, s, SystemTheme.TEXT, true, 2.4f, 0.5f);
            Ui.divider(g, cx - 110, cy + 16, 220, Gfx.alpha(c, 0.9f));
            float subIn = Anim.outCubic(Anim.clamp01((t - 0.5f) / 0.5f));
            float o2 = Gfx.fade(subIn);
            SysFont.draw(g, current.sub, cx, cy + 22 + (1 - subIn) * 6, 10, Gfx.mix(c, 0xFFFFFFFF, 0.55f), true, 0.5f);
            if (!current.note.isEmpty()) SysFont.spaced(g, current.note, cx, cy + 38, 6.5f, SystemTheme.GOLD, true, 2f, 0.5f);
            Gfx.setAlpha(o2);
        } else {
            float y = 46;
            float tw = Math.max(SysFont.spacedWidth(current.title, 10, true, 2f), SysFont.width(current.sub, 7.5f, true)) + 44;
            float w = tw * (0.9f + 0.1f * in);
            float h = 34;
            float x = cx - w / 2;
            Ui.panel(g, x, y, w, h, 8, c, 0.8f);
            Shapes.icon(Icons.SKULL, x + 14, y + h / 2, 12).fill(Gfx.mix(c, 0xFFFFFFFF, 0.4f)).glow(3, Gfx.alpha(c, 0.7f)).draw(g);
            SysFont.spaced(g, current.title, cx + 8, y + 7, 10, Gfx.mix(c, 0xFFFFFFFF, 0.6f), true, 2f, 0.5f);
            SysFont.draw(g, current.sub, cx + 8, y + 21, 7.5f, SystemTheme.TEXT_SOFT, true, 0.5f);
        }
        Gfx.setAlpha(old);
    }

    private ShadowHud() {}
}
