package com.beyondsolo.client.rival;

import com.beyondsolo.client.ClientConfig;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.breaks.BreakHud;
import com.beyondsolo.client.combat.WorldProjector;
import com.beyondsolo.client.depths.DepthsHud;
import com.beyondsolo.client.gate.GateOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.hud.HudSpace;
import com.beyondsolo.client.isle.IsleHud;
import com.beyondsolo.client.key.RunHud;
import com.beyondsolo.gate.GateBlockEntity;
import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.rival.RivalParties;
import com.beyondsolo.rival.RivalParty;
import com.beyondsolo.system.PlayerRank;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Rival parties on screen ({@link RivalClient}).
 *
 * <p>At the top, under the boss bar and the other chips there: the chip of the party that matters most. On its way:
 * whose party, the gate's rank, which way and how far, and when they get there; at the gate: when they go in; inside:
 * how far through they are; in a race: the split of the boss's damage, you against them, or how far they are on the
 * way to the boss before anyone has struck it; after: how it ended.</p>
 *
 * <p>Over a gate a party is heading for, gathering at or holding: the guild's mark with what they are doing.</p>
 */
public final class RivalHud {
    private static final RivalParty.State[] STATES = RivalParty.State.values();
    private static final RivalParty.Outcome[] OUTCOMES = RivalParty.Outcome.values();
    private static final int TRESPASS = 0xFFFF5D6C;

    private static float chipIn;
    private static CompoundTag shownTag;
    private static UUID shownId;
    private static int shownState = -1;
    private static double changedAt = -10;

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !ClientSystemData.get().hasSystem()) return;
        List<CompoundTag> parties = RivalClient.parties();
        int w = g.guiWidth();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        if (ClientConfig.SHOW_HUD.get()) marks(g, mc, parties, partial);
        CompoundTag p = parties.isEmpty() ? null : parties.get(0);
        if (p != null) {
            UUID id = p.getUUID("id");
            int key = p.getInt("state") * 8 + p.getInt("outcome") + (p.getBoolean("racing") ? 64 : 0) + (p.getBoolean("trespasser") ? 128 : 0);
            if (!id.equals(shownId) || key != shownState) changedAt = Anim.now();
            shownId = id;
            shownState = key;
            shownTag = p;
        }
        chipIn = Anim.approach(chipIn, p != null ? 1 : 0, p != null ? 4f : 3f);
        if (chipIn < 0.01f || shownTag == null) return;
        float y = 6 + 34 * Anim.outCubic(GateOverlay.bossBarIn()) + 26 * RunHud.clockShown() + BreakHud.shownHeight()
                + IsleHud.shownHeight() + DepthsHud.shownHeight();
        chip(g, mc, w, y, shownTag, parties.size() - 1);
    }

    static void reset() {
        chipIn = 0;
        shownTag = null;
        shownId = null;
        shownState = -1;
    }

    // ================================================================ the chip

    private static void chip(GuiGraphics g, Minecraft mc, int w, float y, CompoundTag p, int more) {
        RivalGuild guild = RivalGuild.byIndex(p.getInt("guild"));
        PlayerRank rank = PlayerRank.values()[Mth.clamp(p.getInt("rank"), 0, PlayerRank.values().length - 1)];
        RivalParty.State state = STATES[Mth.clamp(p.getInt("state"), 0, STATES.length - 1)];
        RivalParty.Outcome outcome = OUTCOMES[Mth.clamp(p.getInt("outcome"), 0, OUTCOMES.length - 1)];
        boolean here = p.getBoolean("here"), racing = p.getBoolean("racing"), trespasser = p.getBoolean("trespasser");
        // the hunter's own guild (under contract): no race, a gate worked together
        boolean ally = p.getBoolean("ally");
        float share = p.getFloat("share");
        // in the gate with them it is a race (a trespasser's too, once the boss has been struck)
        boolean race = state == RivalParty.State.INSIDE && here && racing && (!trespasser || share >= 0);
        float progress = p.getFloat("progress");
        int gc = guild.color;
        int rc = SystemTheme.rank(rank);
        Component party = party(p);

        String label;
        String line = null;
        String big = null;
        ResourceLocation icon = Icons.GUILDS[guild.ordinal()];
        int c = gc;
        boolean urgent = false;
        BlockPos toward = null;
        switch (state) {
            case EN_ROUTE -> {
                label = t(ally ? "allies" : "en_route");
                if (ally) icon = Icons.HANDSHAKE;
                line = Component.translatable("rival.beyondsolo.hud.en_route.line", party, rank.displayName()).getString();
                big = clock(left(p));
                toward = gate(mc, p);
                urgent = left(p) < 20 * 20;
            }
            case GATHERING -> {
                label = t(ally ? "allies" : "gathering");
                if (ally) icon = Icons.HANDSHAKE;
                line = Component.translatable("rival.beyondsolo.hud.gathering.line", party).getString();
                big = clock(left(p));
                toward = gate(mc, p);
                urgent = true;
            }
            case INSIDE -> {
                if (race) {
                    label = t(trespasser ? "trespass" : "race");
                    icon = Icons.RACE;
                    if (trespasser) {
                        c = TRESPASS;
                        urgent = true;
                    }
                } else if (trespasser) {
                    label = t("trespass");
                    line = Component.translatable("rival.beyondsolo.hud.trespass.line", party).getString();
                    c = TRESPASS;
                    urgent = true;
                    big = percent(progress);
                } else if (racing) {
                    // a race the hunter watches from outside
                    label = t("race");
                    icon = Icons.RACE;
                    line = Component.translatable("rival.beyondsolo.hud.racing.line", party).getString();
                    big = percent(progress);
                    if (!here) toward = gate(mc, p);
                } else if (ally) {
                    label = t("allies");
                    icon = Icons.HANDSHAKE;
                    line = Component.translatable("rival.beyondsolo.hud.allies.line", party).getString();
                    big = percent(progress);
                    if (!here) toward = gate(mc, p);
                } else {
                    label = t("holds");
                    line = Component.translatable("rival.beyondsolo.hud.holds.line", party).getString();
                    big = percent(progress);
                    if (!here) toward = gate(mc, p);
                }
            }
            default -> {
                label = t(switch (outcome) {
                    case CLEARED -> "cleared";
                    case RETREATED -> "retreated";
                    case STOOD_DOWN -> "stood_down";
                    case BEATEN -> "beaten";
                    default -> "disbanded";
                });
                line = Component.translatable("rival.beyondsolo.hud." + outcome.name().toLowerCase(Locale.ROOT) + ".line", party).getString();
                if (outcome == RivalParty.Outcome.BEATEN) {
                    c = SystemTheme.GOLD;
                    icon = Icons.CROWN;
                } else if (outcome == RivalParty.Outcome.CLEARED) {
                    icon = Icons.FLAG;
                }
            }
        }
        label = label.toUpperCase(Locale.ROOT);
        String far = toward == null ? null : Component.translatable("rival.beyondsolo.hud.distance",
                Math.round(Math.sqrt(flatSqr(mc.player.position(), toward)))).getString();
        String rankText = rank.displayName().getString();
        String moreText = more > 0 ? Component.translatable("rival.beyondsolo.hud.more", more).getString() : null;

        float labelSize = 5.6f, lineSize = 5.1f, bigSize = 9f;
        float labelW = SysFont.spacedWidth(label, labelSize, true, 1.3f);
        float rankW = SysFont.width(rankText, 5f, true) + 7;
        int size = Math.max(1, p.getInt("size"));
        float pipsW = size * 5f + 3;
        float head = labelW + 4 + rankW + 4 + pipsW;
        float lineW = race ? 150 : SysFont.width(line == null ? "" : line, lineSize, true) + (far != null ? 9 + SysFont.width(far, lineSize, true) + 4 : 0);
        float bigW = big == null ? 0 : SysFont.width(big, bigSize, true) + 12;
        float bh = race ? 26 : 22;
        float bw = 24 + Math.max(head, lineW) + 10 + bigW + 4;
        float in = Anim.outCubic(chipIn);
        float x = (w - bw) / 2f;
        y -= 14 * (1 - in);
        float old = Gfx.fade(in);
        HudSpace.claimTop(x, y, bw, bh + (moreText != null ? 10 : 0));
        float since = Anim.since(changedAt);
        float flash = 1 - Anim.clamp01(since / 1.1f);
        float blink = urgent ? 0.5f + 0.5f * Anim.pulse(0.9f) : 0.5f + 0.5f * Anim.pulse(3f);
        Shapes.box(x, y, bw, bh).radius(bh / 2f).fillV(Gfx.mix(0xC00A1222, Gfx.alpha(c, 0.75f), 0.14f), 0xC0050912)
                .border(0.8f, Gfx.alpha(c, 0.5f + 0.35f * blink + 0.15f * flash), Gfx.alpha(c, 0.25f))
                .glow(4 + 4 * blink + 8 * flash, Gfx.alpha(c, 0.15f + 0.1f * blink + 0.35f * flash)).draw(g);

        // the guild's emblem in a ring that turns while they are on the move
        float ix = x + 12, iy = y + bh / 2f;
        boolean moving = state != RivalParty.State.DONE;
        Shapes.circle(ix, iy, 7.5f).fillV(Gfx.alpha(gc, 0.32f), Gfx.alpha(gc, 0.08f)).border(0.6f, Gfx.alpha(c, 0.85f)).draw(g);
        Shapes.ring(ix, iy, 9.2f, 0.55f).fill(Gfx.alpha(c, 0.65f)).dashes(state == RivalParty.State.INSIDE ? 6 : 10, 0.5f)
                .rotation(moving ? Anim.loop(race ? 1.8f : 6f) : 0).draw(g);
        Shapes.icon(icon, ix, iy, 9).fill(Gfx.mix(c, 0xFFFFFFFF, 0.55f)).glow(2, Gfx.alpha(c, 0.6f)).draw(g);

        // the label, the gate's rank, and the party's people (standing, or down)
        float tx = x + 24;
        float ty = race ? y + 4 : y + 3.8f;
        SysFont.spaced(g, label, tx, ty, labelSize, Gfx.mix(c, 0xFFFFFFFF, 0.45f), true, 1.3f, 0f);
        float hx = tx + labelW + 4;
        hx += Ui.chip(g, hx, ty - 1.3f, 7.4f, rankText, 5f, rc, true) + 4;
        int standing = Mth.clamp(p.getInt("standing"), 0, size);
        for (int i = 0; i < size; i++) {
            float px = hx + i * 5f + 2, py = ty + 2.4f;
            if (i < standing) Shapes.circle(px, py, 1.6f).fill(Gfx.mix(gc, 0xFFFFFFFF, 0.25f)).glow(1.5f, Gfx.alpha(gc, 0.5f)).draw(g);
            else Shapes.circle(px, py, 1.5f).fill(0).border(0.5f, Gfx.alpha(SystemTheme.TEXT_DIM, 0.8f)).draw(g);
        }

        float ly = y + 12.6f;
        if (race) {
            raceLine(g, tx, y + 13.5f, 150, share, progress, gc);
        } else if (line != null) {
            float lx = tx;
            if (toward != null) {
                double dx = toward.getX() + 0.5 - mc.player.getX(), dz = toward.getZ() + 0.5 - mc.player.getZ();
                float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
                arrow(g, lx + 3, ly + 2, Mth.wrapDegrees(bearing - mc.player.getYRot()), Gfx.mix(c, 0xFFFFFFFF, 0.4f));
                SysFont.draw(g, far, lx + 8, ly, lineSize, SystemTheme.TEXT_SOFT, true, 0f);
                lx += 9 + SysFont.width(far, lineSize, true) + 4;
            }
            SysFont.draw(g, line, lx, ly, lineSize, state == RivalParty.State.DONE ? SystemTheme.TEXT_SOFT : SystemTheme.TEXT_DIM, true, 0f);
        }

        // the clock or how far through, on the right
        if (big != null) {
            float bx = x + bw - 10 - bigW + 8;
            Shapes.box(bx - 6, y + 4, 0.75f, bh - 8).fill(Gfx.alpha(c, 0.35f)).draw(g);
            int bc = urgent ? Gfx.mix(c, 0xFFFFFFFF, 0.35f + 0.25f * blink) : SystemTheme.TEXT;
            SysFont.draw(g, big, bx, SysFont.centerY(y, bh, bigSize), bigSize, bc, true, 0f);
        }
        if (moreText != null) {
            float mw = SysFont.width(moreText, 4.8f, true) + 10;
            Shapes.pill(x + (bw - mw) / 2f, y + bh + 1.5f, mw, 8).fill(Gfx.alpha(0xFF060C1A, 0.75f)).border(0.5f, Gfx.alpha(gc, 0.4f)).draw(g);
            SysFont.draw(g, moreText, x + bw / 2f, SysFont.centerY(y + bh + 1.5f, 8, 4.8f), 4.8f, SystemTheme.TEXT_SOFT, true, 0.5f);
        }
        Gfx.setAlpha(old);
    }

    /**
     * The race: once the boss has been struck, the split of its damage (you on the left in the System's blue, them on
     * the right in their guild's colour); before that, how far they are on their way to it.
     */
    private static void raceLine(GuiGraphics g, float x, float y, float width, float share, float progress, int gc) {
        float size = 5f;
        if (share >= 0) {
            String you = t("you") + " " + Math.round((1 - share) * 100) + "%";
            String them = Math.round(share * 100) + "% " + t("them");
            float yw = SysFont.width(you, size, true), tw = SysFont.width(them, size, true);
            float bx = x + yw + 4, bw = width - yw - tw - 8;
            float split = bx + bw * (1 - share);
            Shapes.pill(bx, y + 1, bw, 5).fill(SystemTheme.TRACK).border(0.5f, SystemTheme.TRACK_BORDER).draw(g);
            if (share < 1) Shapes.pill(bx, y + 1, Math.max(5, split - bx), 5).fillH(SystemTheme.ACCENT_DEEP, SystemTheme.ACCENT).glow(2, Gfx.alpha(SystemTheme.ACCENT, 0.5f)).draw(g);
            if (share > 0) Shapes.pill(Math.min(split, bx + bw - 5), y + 1, Math.max(5, bx + bw - split), 5)
                    .fillH(Gfx.mix(gc, 0xFFFFFFFF, 0.2f), Gfx.shade(gc, -0.35f)).glow(2, Gfx.alpha(gc, 0.5f)).draw(g);
            Shapes.box(split - 0.5f, y - 0.5f, 1, 8).fill(0xFFFFFFFF).glow(2, 0x80FFFFFF).draw(g);
            SysFont.draw(g, you, x, y + 0.5f, size, SystemTheme.ACCENT_BRIGHT, true, 0f);
            SysFont.draw(g, them, x + width, y + 0.5f, size, Gfx.mix(gc, 0xFFFFFFFF, 0.4f), true, 1f);
        } else {
            // their way to the boss's door: a trail with their mark on it
            String what = t("race.way");
            float ww = SysFont.width(what, size, true);
            SysFont.draw(g, what, x, y + 0.5f, size, SystemTheme.TEXT_DIM, true, 0f);
            float bx = x + ww + 6, bw = width - ww - 6 - 10;
            float k = Mth.clamp(progress / RivalParties.ROUTE_SHARE, 0, 1);
            for (int i = 0; i < 18; i++) Shapes.box(bx + bw * i / 18f, y + 3, bw / 18f * 0.55f, 0.8f).fill(Gfx.alpha(gc, 0.35f)).draw(g);
            Shapes.box(bx, y + 3, bw * k, 0.8f).fill(Gfx.alpha(gc, 0.9f)).glow(1.5f, Gfx.alpha(gc, 0.5f)).draw(g);
            Shapes.circle(bx + bw * k, y + 3.4f, 2.2f).fill(Gfx.mix(gc, 0xFFFFFFFF, 0.3f)).glow(2.5f, Gfx.alpha(gc, 0.7f)).draw(g);
            Shapes.icon(Icons.SKULL, bx + bw + 5, y + 3.4f, 7).fill(Gfx.alpha(SystemTheme.DANGER, 0.9f)).draw(g);
        }
    }

    // ================================================================ the marks over gates

    private static void marks(GuiGraphics g, Minecraft mc, List<CompoundTag> parties, float partial) {
        if (!mc.level.dimension().equals(Level.OVERWORLD)) return;
        Vec3 eye = mc.player.getEyePosition(partial);
        for (int i = parties.size() - 1; i >= 0; i--) {
            CompoundTag p = parties.get(i);
            RivalParty.State state = STATES[Mth.clamp(p.getInt("state"), 0, STATES.length - 1)];
            if (state == RivalParty.State.DONE || !p.getBoolean("overworld") || !p.contains("gate")) continue;
            BlockPos gate = BlockPos.of(p.getLong("gate"));
            Vec3 top = Vec3.atBottomCenterOf(gate).add(0, GateBlockEntity.CENTER_Y + GateBlockEntity.HALF_HEIGHT + 1.3, 0);
            double dist = top.distanceTo(eye);
            float a = 1 - Mth.clamp((float) (dist - 56) / 16f, 0, 1);
            if (a <= 0.01f || dist < 3) continue;
            float[] at = WorldProjector.project(top);
            if (at == null) continue;
            float s = Mth.clamp(1.3f - (float) dist / 60f, 0.6f, 1.15f);
            mark(g, at[0], at[1], s, a, p, state);
        }
    }

    private static void mark(GuiGraphics g, float x, float y, float s, float a, CompoundTag p, RivalParty.State state) {
        RivalGuild guild = RivalGuild.byIndex(p.getInt("guild"));
        int c = guild.color;
        float r = 7f * s;
        // what they are doing, on a dark tag of its own under the guild's name (measured first: see below)
        String what = switch (state) {
            case EN_ROUTE -> Component.translatable("rival.beyondsolo.hud.mark.coming", clock(left(p))).getString();
            case GATHERING -> Component.translatable("rival.beyondsolo.hud.mark.going_in", clock(left(p))).getString();
            default -> Component.translatable(p.getBoolean("racing") ? "rival.beyondsolo.hud.mark.racing" : "rival.beyondsolo.hud.mark.held",
                    percent(p.getFloat("progress"))).getString();
        };
        what = what.toUpperCase(Locale.ROOT);
        String name = guild.displayName().getString();
        float ts = 5.2f * Math.max(0.85f, s);
        float tw = SysFont.spacedWidth(what, ts * 0.9f, true, 1f) + 10;
        float nw = SysFont.width(name, ts, true) + 10;
        float above = r + 22 * Math.max(0.85f, s);
        y += (float) Math.sin(Anim.now() * 1.9 + p.getInt("guild")) * 1.5f * s;
        // a gate close by puts its mark high up the screen: it stays under the bars at the top centre (the party's own
        // chip among them), which drew last frame
        float half = Math.max(tw, nw) / 2f;
        float free = HudSpace.topClaimedLately(x - half, x + half);
        if (free > 0 && y - above < free + 3) y = free + 3 + above;
        float old = Gfx.fade(a);
        float pulse = Anim.pulse(1.6f);
        // a stem down to the gate, the guild's mark in a diamond
        Shapes.box(x - 0.6f * s, y + r, 1.2f * s, 6 * s).fillV(Gfx.alpha(c, 0.8f), Gfx.alpha(c, 0f)).draw(g);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(45));
        Shapes.box(-r * 0.8f, -r * 0.8f, r * 1.6f, r * 1.6f).radius(r * 0.28f).fillV(0xF0141C2E, 0xF0080C16)
                .border(1.1f * s, Gfx.mix(c, 0xFFFFFFFF, 0.25f), Gfx.alpha(c, 0.5f)).glow(r * (0.5f + 0.3f * pulse), Gfx.alpha(c, 0.4f)).draw(g);
        g.pose().popPose();
        if (state == RivalParty.State.INSIDE) {
            Shapes.ring(x, y, r * 1.45f, 1.1f * s).fill(Gfx.alpha(c, 0.85f)).progress(Mth.clamp(p.getFloat("progress"), 0, 1))
                    .glow(2 * s, Gfx.alpha(c, 0.5f)).draw(g);
        } else {
            Shapes.ring(x, y, r * 1.45f, 0.6f * s).fill(Gfx.alpha(c, 0.55f)).dashes(10, 0.5f).rotation(Anim.loop(state == RivalParty.State.GATHERING ? 2f : 6f)).draw(g);
        }
        Shapes.icon(Icons.GUILDS[guild.ordinal()], x, y, r * 1.35f).fillV(Gfx.mix(c, 0xFFFFFFFF, 0.8f), Gfx.mix(c, 0xFFFFFFFF, 0.3f))
                .glow(r * 0.3f, Gfx.alpha(c, 0.7f)).draw(g);
        float ty = y - above;
        Shapes.pill(x - nw / 2f, ty, nw, ts + 4.5f).fillV(Gfx.alpha(0xFF0C1830, 0.85f), Gfx.alpha(0xFF060C1A, 0.85f)).border(0.6f, Gfx.alpha(c, 0.55f)).draw(g);
        SysFont.draw(g, name, x, SysFont.centerY(ty, ts + 4.5f, ts), ts, Gfx.mix(c, 0xFFFFFFFF, 0.65f), true, 0.5f);
        float wy = ty + ts + 6;
        Shapes.pill(x - tw / 2f, wy, tw, ts + 3).fillV(Gfx.alpha(Gfx.mix(0xFF0C1830, c, 0.25f), 0.88f), Gfx.alpha(0xFF060C1A, 0.88f))
                .border(0.6f, Gfx.alpha(c, 0.75f)).draw(g);
        SysFont.spaced(g, what, x, SysFont.centerY(wy, ts + 3, ts * 0.9f), ts * 0.9f, Gfx.mix(c, 0xFFFFFFFF, 0.55f), true, 1f, 0.5f);
        Gfx.setAlpha(old);
    }

    // ================================================================ bits

        /** The party's name, as the System's words have it: "Kaelen Voss's party" (whoever leads it). */
    private static Component party(CompoundTag p) {
        return Component.translatable("rival.beyondsolo.hud.led", p.getString("leader"));
    }

    /** Ticks left on the party's clock, counted down between the server's words. */
    private static long left(CompoundTag p) {
        return Math.max(0, p.getLong("left") - (long) (RivalClient.since() * 20));
    }

    private static BlockPos gate(Minecraft mc, CompoundTag p) {
        if (!p.contains("gate") || !p.getBoolean("overworld") || !mc.level.dimension().equals(Level.OVERWORLD)) return null;
        return BlockPos.of(p.getLong("gate"));
    }

    private static double flatSqr(Vec3 a, BlockPos b) {
        double dx = b.getX() + 0.5 - a.x, dz = b.getZ() + 0.5 - a.z;
        return dx * dx + dz * dz;
    }

    static String clock(long ticks) {
        int s = (int) Math.max(0, (ticks + 19) / 20);
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    private static String percent(float f) {
        return Math.round(Mth.clamp(f, 0, 1) * 100) + "%";
    }

    private static String t(String key) {
        return Component.translatable("rival.beyondsolo.hud." + key).getString();
    }

    /** A small arrow, pointing up for ahead and turned {@code degrees} clockwise. */
    private static void arrow(GuiGraphics g, float cx, float cy, float degrees, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
        Shapes.icon(Icons.ARROW, 0, 0, 6.5f).fill(color).draw(g);
        g.pose().popPose();
    }

    private RivalHud() {}
}
