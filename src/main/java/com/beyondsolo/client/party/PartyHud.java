package com.beyondsolo.client.party;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.economy.GoldHud;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.hud.EffectsHud;
import com.beyondsolo.network.PartySyncPayload;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The party on the HUD, for everyone in one (with or without the System: finding each other is what it is for):
 * a frame for each other member on the right (class, name, health, how far and which way, or where they went), a
 * marker over each one out in the same world (an arrow at the edge of the screen when they are behind), and the
 * regroup: the light gathering while the player stands still, then where they arrived.
 */
public final class PartyHud {
    private static final float FRAME_W = 104;
    private static final float FRAME_H = 19;
    private static final int PARTY = 0xFF5FD0FF;
    private static final int REGROUP = 0xFFB98BFF;

    private static float shown;
    /** The frames' top edge as drawn (it glides when something above them comes or goes). */
    private static float top = Float.NaN;
    /** Per member: health as drawn (it slides). */
    private static final Map<UUID, Float> HEALTH = new HashMap<>();

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        float partial = delta.getGameTimeDeltaPartialTick(false);
        List<PartySyncPayload.Member> others = PartyClient.others();
        shown = Anim.approach(shown, PartyClient.inParty() && !others.isEmpty() ? 1 : 0, 6f);
        int w = g.guiWidth(), h = g.guiHeight();
        if (shown > 0.02f && !others.isEmpty()) {
            float old = Gfx.fade(shown);
            markers(g, mc, others, w, h, partial);
            // in a raid the raid frames list everyone (the party among them), so the party's own would double them
            if (!com.beyondsolo.client.raid.RaidHud.framesShown()) frames(g, mc, others, w, h, partial);
            Gfx.setAlpha(old);
        }
        regroup(g, w, h);
    }

    // ------------------------------------------------------------------ frames

    private static void frames(GuiGraphics g, Minecraft mc, List<PartySyncPayload.Member> others, int w, int h, float partial) {
        float x = w - 8 - FRAME_W + (1 - Anim.outCubic(shown)) * 18;
        // on the right edge, under the effect icons, stepping down while the gold capsule shows a change
        float want = Math.max(Math.max(64, h * 0.42f), Math.max(EffectsHud.bottom() + 9, GoldHud.bottom() + 6));
        top = Float.isNaN(top) ? want : Anim.approach(top, want, 8f);
        float y = top;
        UUID leader = PartyClient.state().leader();
        for (PartySyncPayload.Member m : others) {
            if (y + FRAME_H > h - 40) break;
            frame(g, mc, m, m.id().equals(leader), x, y, partial);
            y += FRAME_H + 3;
        }
    }

    private static void frame(GuiGraphics g, Minecraft mc, PartySyncPayload.Member m, boolean leader, float x, float y, float partial) {
        int cc = m.cls() >= 0 ? SystemTheme.playerClass(PlayerClass.values()[m.cls()]) : PARTY;
        int tint = m.online() ? cc : SystemTheme.TEXT_DIM;
        float hp = m.maxHealth() <= 0 ? 0 : Mth.clamp(m.health() / m.maxHealth(), 0, 1);
        float drawn = HEALTH.containsKey(m.id()) ? Anim.approach(HEALTH.get(m.id()), hp, 8f) : hp;
        HEALTH.put(m.id(), drawn);
        boolean low = m.online() && hp < 0.3f;
        float danger = low ? Anim.pulse(0.8f) : 0;

        Shapes.box(x + 1, y + 2, FRAME_W - 2, FRAME_H - 2).radius(7).fill(0).glow(6, 0x40000000).draw(g);
        Shapes.box(x, y, FRAME_W, FRAME_H).radius(7).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.84f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.84f))
                .border(0.7f, Gfx.alpha(low ? SystemTheme.DANGER : tint, 0.55f + 0.35f * danger), Gfx.alpha(tint, 0.15f))
                .glow(3 + 4 * danger, Gfx.alpha(low ? SystemTheme.DANGER : tint, 0.12f + 0.25f * danger)).draw(g);
        // the badge
        float bx = x + 9.5f, by = y + FRAME_H / 2f;
        Shapes.circle(bx, by, 6.3f).fillV(Gfx.alpha(tint, 0.3f), Gfx.alpha(Gfx.shade(tint, -0.5f), 0.45f)).border(0.6f, Gfx.alpha(tint, 0.9f)).draw(g);
        ResourceLocation icon = m.cls() >= 0 ? Icons.of(PlayerClass.values()[m.cls()]) : Icons.QUESTION;
        Shapes.icon(icon, bx, by, 7.5f).fill(m.online() ? Gfx.mix(tint, 0xFFFFFFFF, 0.55f) : SystemTheme.TEXT_DIM).draw(g);

        // where: how far and which way when they are where the player is (out in the world, or in the same cave), else
        // the place they went (a gate, the Citadel...) or the world they are in
        String where;
        int wc = PartyTab.placeColor(m);
        float arrow = Float.NaN;
        if (!PartyClient.samePlace(m) && (!m.online() || m.place() != PartySyncPayload.WORLD)) {
            where = m.place() == PartySyncPayload.GATE && m.placeRank() >= 0
                    ? Component.translatable("ui.beyondsolo.party.short.gate_rank", PlayerRank.values()[m.placeRank()].displayName()).getString()
                    : Component.translatable("ui.beyondsolo.party.short." + PartyTab.placeKey(m.online() ? m.place() : PartySyncPayload.OFFLINE)).getString();
        } else if (!PartyClient.samePlace(m)) {
            where = PartyTab.dimName(m.dim().toString());
        } else {
            Vec3 at = position(mc, m, partial);
            double dx = at.x - mc.player.getX(), dz = at.z - mc.player.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            where = d < 1000 ? Component.translatable("ui.beyondsolo.party.meters", Math.round(d)).getString()
                    : String.format(Locale.ROOT, "%.1fk", d / 1000.0);
            float bearing = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            arrow = Mth.wrapDegrees(bearing - mc.player.getViewYRot(partial));
            if (d < 6) arrow = Float.NaN;
        }
        float rx = x + FRAME_W - 5;
        if (!Float.isNaN(arrow)) {
            g.pose().pushPose();
            g.pose().translate(rx - 3, y + 6.2f, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(arrow));
            Shapes.icon(Icons.ARROW, 0, 0, 6.5f).fill(Gfx.mix(PARTY, 0xFFFFFFFF, 0.5f)).glow(1.5f, Gfx.alpha(PARTY, 0.6f)).draw(g);
            g.pose().popPose();
            rx -= 8;
        }
        where = SysFont.fit(where, 4.8f, true, 44);
        SysFont.draw(g, where, rx, y + 4f, 4.8f, wc, true, 1f);
        float whereW = SysFont.width(where, 4.8f, true) + (Float.isNaN(arrow) ? 0 : 8);

        // name (and the crown), health
        float tx = x + 18.5f;
        String name = SysFont.fit(m.name(), 5.8f, true, FRAME_W - 26 - whereW - (leader ? 8 : 0));
        SysFont.text(g, name, tx, y + 3.2f, 5.8f, m.online() ? SystemTheme.TEXT : SystemTheme.TEXT_DIM);
        if (leader) Shapes.icon(Icons.CROWN, tx + SysFont.width(name, 5.8f, true) + 4.5f, y + 6f, 6f).fill(SystemTheme.GOLD).draw(g);
        if (m.online()) {
            Ui.bar(g, tx, y + 12.2f, FRAME_W - 18.5f - 6, 2.6f, drawn, low ? SystemTheme.DANGER : SystemTheme.HP_A,
                    low ? Gfx.mix(SystemTheme.HP_B, 0xFFFFFFFF, 0.5f * danger) : SystemTheme.HP_B);
        } else {
            Shapes.pill(tx, y + 12.2f, FRAME_W - 18.5f - 6, 2.6f).fill(SystemTheme.TRACK).draw(g);
        }
    }

    /** Where a member is: their player if this client sees them (smooth), else the last position the server sent. */
    private static Vec3 position(Minecraft mc, PartySyncPayload.Member m, float partial) {
        Player p = mc.level == null ? null : mc.level.getPlayerByUUID(m.id());
        if (p != null) return p.getPosition(partial);
        return new Vec3(m.x(), m.y(), m.z());
    }

    // ------------------------------------------------------------------ markers in the world

    private static void markers(GuiGraphics g, Minecraft mc, List<PartySyncPayload.Member> others, int w, int h, float partial) {
        if (mc.level == null) return;
        int edgeRow = 0;
        for (PartySyncPayload.Member m : others) {
            // (someone in another gate's cave is in this dimension too, thousands of blocks off: no marker for them)
            if (!PartyClient.samePlace(m)) continue;
            Player p = mc.level.getPlayerByUUID(m.id());
            Vec3 feet = position(mc, m, partial);
            double d = feet.distanceTo(mc.player.getPosition(partial));
            if (d < 10) continue;
            Vec3 head = feet.add(0, (p != null ? p.getBbHeight() : 1.8) + 0.7, 0);
            int cc = m.cls() >= 0 ? SystemTheme.playerClass(PlayerClass.values()[m.cls()]) : PARTY;
            float[] at = WorldProjector.project(head);
            boolean onScreen = at != null && at[0] > 12 && at[0] < w - 12 && at[1] > 12 && at[1] < h - 30;
            String dist = d < 1000 ? Component.translatable("ui.beyondsolo.party.meters", Math.round(d)).getString() : String.format(Locale.ROOT, "%.1fk", d / 1000.0);
            if (onScreen) {
                marker(g, m, cc, at[0], at[1], dist);
            } else {
                float bearing = (float) (Mth.atan2(feet.z - mc.player.getZ(), feet.x - mc.player.getX()) * Mth.RAD_TO_DEG) - 90f;
                float rel = Mth.wrapDegrees(bearing - mc.player.getViewYRot(partial));
                edge(g, m, cc, rel, w, h, edgeRow++, dist);
            }
        }
    }

    private static void marker(GuiGraphics g, PartySyncPayload.Member m, int c, float x, float y, String dist) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().pushPose();
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        Shapes.box(-4f, -4f, 8f, 8f).radius(1.6f).fill(Gfx.alpha(0xFF060A14, 0.9f)).border(0.8f, c).glow(3, Gfx.alpha(c, 0.5f)).draw(g);
        g.pose().popPose();
        ResourceLocation icon = m.cls() >= 0 ? Icons.of(PlayerClass.values()[m.cls()]) : Icons.STAR;
        Shapes.icon(icon, 0, 0, 6.2f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.6f)).draw(g);
        g.pose().popPose();
        String name = SysFont.fit(m.name(), 5f, true, 80);
        float nw = SysFont.width(name, 5f, true), dw = SysFont.width(dist, 4.4f, true);
        float lw = nw + dw + 5;
        float lx = x - lw / 2f, ly = y + 7.5f;
        Shapes.pill(lx - 3, ly - 1.2f, lw + 6, 7.6f).fill(Gfx.alpha(0xFF060A14, 0.6f)).draw(g);
        SysFont.draw(g, name, lx, ly, 5f, SystemTheme.TEXT, true, 0f);
        SysFont.draw(g, dist, lx + nw + 5, ly + 0.4f, 4.4f, SystemTheme.TEXT_SOFT, true, 0f);
    }

    /**
     * Someone behind or off to the side: an arrow at that edge, with their name and how far. On the right it stands
     * just left of the party frames, which keep that edge.
     */
    private static void edge(GuiGraphics g, PartySyncPayload.Member m, int c, float rel, int w, int h, int row, String dist) {
        boolean right = rel > 0;
        float y = h * 0.5f - 10 + row * 16;
        float x = right ? w - 8 - FRAME_W - 12 : 14;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(right ? 90 : -90));
        float pulse = Anim.pulse(1.2f);
        Shapes.icon(Icons.ARROW, 0, 0, 9).fill(Gfx.mix(c, 0xFFFFFFFF, 0.45f)).glow(2 + 2 * pulse, Gfx.alpha(c, 0.55f)).draw(g);
        g.pose().popPose();
        String name = SysFont.fit(m.name(), 4.8f, true, 70);
        String line = name + "  " + dist;
        SysFont.draw(g, line, right ? x - 8 : x + 8, y - 2.4f, 4.8f, Gfx.alpha(SystemTheme.TEXT, 0.85f), true, right ? 1f : 0f);
    }

    // ------------------------------------------------------------------ regrouping

    private static void regroup(GuiGraphics g, int w, int h) {
        float cx = w / 2f, y = h * 0.64f;
        if (PartyClient.channeling()) {
            float k = PartyClient.channel();
            float bw = 150, bh = 26;
            float x = cx - bw / 2f;
            Shapes.box(x, y, bw, bh).radius(8).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.88f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.88f))
                    .border(0.8f, Gfx.alpha(REGROUP, 0.8f), Gfx.alpha(REGROUP, 0.25f)).glow(6 + 4 * k, Gfx.alpha(REGROUP, 0.25f + 0.2f * k)).draw(g);
            Shapes.ring(x + 13, y + bh / 2f, 7, 0.9f).fill(Gfx.alpha(REGROUP, 0.25f)).draw(g);
            Shapes.ring(x + 13, y + bh / 2f, 7, 0.9f).fill(Gfx.mix(REGROUP, 0xFFFFFFFF, 0.3f)).progress(k).glow(2, Gfx.alpha(REGROUP, 0.6f)).draw(g);
            Shapes.icon(Icons.SPIRAL, x + 13, y + bh / 2f, 7).fill(Gfx.mix(REGROUP, 0xFFFFFFFF, 0.5f)).rotation(Anim.loop(1.6f)).draw(g);
            String title = Component.translatable("ui.beyondsolo.party.regrouping", PartyClient.channelTo()).getString().toUpperCase(Locale.ROOT);
            SysFont.spaced(g, SysFont.fit(title, 5.8f, true, bw - 34), x + 25, y + 5, 5.8f, SystemTheme.TEXT, true, 1.1f, 0f);
            SysFont.text(g, Component.translatable("ui.beyondsolo.party.stand_still").getString(), x + 25, y + 14.5f, 4.8f, SystemTheme.TEXT_SOFT);
            Ui.bar(g, x + 25, y + bh - 4.5f, bw - 33, 1.8f, k, Gfx.shade(REGROUP, -0.2f), Gfx.mix(REGROUP, 0xFFFFFFFF, 0.4f));
            return;
        }
        int kind = PartyClient.flashKind();
        float t = PartyClient.sinceFlash();
        if (kind == 0 || t > 2.6f) return;
        float k = (1 - Anim.window(t, 2.0f, 2.6f)) * Anim.outCubic(Anim.window(t, 0f, 0.2f));
        int c = kind == 1 ? SystemTheme.DANGER : REGROUP;
        String text = switch (kind) {
            case 1 -> Component.translatable("ui.beyondsolo.party.regroup_broken", PartyClient.flashText()).getString();
            case 2 -> Component.translatable("ui.beyondsolo.party.regroup_done", PartyClient.flashText()).getString();
            default -> Component.translatable("ui.beyondsolo.party.regroup_joined", PartyClient.flashText()).getString();
        };
        text = SysFont.fit(text, 6f, true, w - 60);
        float tw = SysFont.width(text, 6f, true) + 26;
        float old = Gfx.fade(k);
        Shapes.pill(cx - tw / 2f, y, tw, 14).fill(Gfx.alpha(0xFF060A14, 0.85f)).border(0.8f, Gfx.alpha(c, 0.85f)).glow(6, Gfx.alpha(c, 0.35f)).draw(g);
        Shapes.icon(kind == 1 ? Icons.X : Icons.SPIRAL, cx - tw / 2f + 9, y + 7, 7).fill(Gfx.mix(c, 0xFFFFFFFF, 0.4f)).draw(g);
        SysFont.draw(g, text, cx + 5, SysFont.centerY(y, 14, 6f), 6f, SystemTheme.TEXT, true, 0.5f);
        Gfx.setAlpha(old);
    }

    private PartyHud() {}
}
