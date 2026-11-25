package com.beyondsolo.client.association;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.duel.DuelRecord;
import com.beyondsolo.duel.NationalHunter;
import com.beyondsolo.network.AssociationActionPayload;
import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.system.PlayerClass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A national-level hunter's challenge: the challenger on the left over their guild's crest, what the country calls
 * them and the hunter's record against them; on the right why they came, how the two of them stand on the national
 * rankings, what a win pays, the rules of the ring, and the three answers: accept (the ring is marked out where the
 * hunter stands and the count begins), not now (they come back later, twice at most), decline.
 */
public class DuelScreen extends AssociationWindow {
    DuelScreen() {
        super(Component.translatable("ui.beyondsolo.duel.title"), 440, 258, 132);
    }

    @Override
    CompoundTag data() {
        return AssociationClient.duel();
    }

    NationalHunter who() {
        return NationalHunter.byIndex(data().getInt("who"));
    }

    @Override
    int accent() {
        return 0xFF000000 | who().color;
    }

    @Override
    Component role() {
        return Component.translatable("duel.beyondsolo.national");
    }

    static Component ui(String key, Object... args) {
        return Component.translatable("ui.beyondsolo.duel." + key, args);
    }

    /** Why a challenge was made, as the window and the office tell it. */
    static Component why(NationalHunter who, DuelRecord.Reason why) {
        return ui("why." + why.key(), who.displayName(), who.guild().displayName());
    }

    /** The mark for why a challenge was made. */
    static ResourceLocation whyIcon(DuelRecord.Reason why) {
        return switch (why) {
            case TEST -> Icons.SEAL;
            case PASSED -> Icons.MEDAL;
            case GRUDGE -> Icons.LEAVE;
            case ASKED -> Icons.ID_CARD;
        };
    }

    /** "2 won · 1 lost · 0 drawn", or that they never met. */
    static String record(CompoundTag r) {
        int w = r.getInt("wins"), l = r.getInt("losses"), d = r.getInt("draws");
        return w + l + d == 0 ? ui("record.none").getString() : ui("record.line", w, l, d).getString();
    }

    // ------------------------------------------------------------------ left: who they are

    @Override
    void under(GuiGraphics g, float x, float y, float lw, float bottom, float t) {
        NationalHunter who = who();
        int c = accent();
        RivalGuild guild = who.guild();
        Ui.divider(g, x, y, lw, Gfx.alpha(c, 0.4f));
        // what the country calls them
        SysFont.text(g, SysFont.fit(who.epithet().getString(), 5.4f, true, lw), x, y + 5, 5.4f, Gfx.mix(c, 0xFFFFFFFF, 0.45f));
        // their guild, and their class
        float gy = y + 16;
        Shapes.circle(x + 6, gy + 5, 5.5f).fillV(Gfx.alpha(guild.color, 0.3f), Gfx.alpha(guild.color, 0.06f)).border(0.6f, Gfx.alpha(guild.color, 0.9f)).draw(g);
        Shapes.icon(Icons.GUILDS[guild.ordinal()], x + 6, gy + 5, 7).fill(Gfx.mix(guild.color, 0xFFFFFFFF, 0.6f)).draw(g);
        SysFont.text(g, SysFont.fit(guild.displayName().getString(), 4.8f, true, lw - 16), x + 15, gy + 2.6f, 4.8f, Gfx.mix(guild.color, 0xFFFFFFFF, 0.35f));
        PlayerClass pc = who.playerClass();
        float cy = gy + 14;
        Shapes.icon(Icons.of(pc), x + 6, cy + 5, 8).fill(Gfx.opaque(pc.color())).draw(g);
        SysFont.text(g, SysFont.fit(pc.displayName().getString(), 4.8f, true, lw - 16), x + 15, cy + 2.6f, 4.8f, SystemTheme.TEXT_SOFT);
        // the hunter's record against them
        CompoundTag r = data().getCompound("record");
        float ry = Math.max(cy + 16, bottom - 22);
        SysFont.spaced(g, ui("your_record").getString(), x, ry, 4.2f, SystemTheme.TEXT_DIM, true, 1.1f, 0f);
        SysFont.text(g, SysFont.fit(record(r), 4.8f, true, lw), x, ry + 8, 4.8f, SystemTheme.TEXT);
    }

    // ------------------------------------------------------------------ right: the challenge

    @Override
    void content(GuiGraphics g, float x, float y, float cw, float ch, int mouseX, int mouseY, float t) {
        CompoundTag d = data();
        NationalHunter who = who();
        int c = accent();
        float k = Anim.outCubic(Anim.window(t, 0.2f, 0.55f));
        float old = Gfx.fade(k);
        CompoundTag ch2 = d.getCompound("challenge");
        if (!ch2.contains("who")) {
            Ui.card(g, x, y + 30, cw, 50, 9, c, 0.08f);
            Shapes.icon(Icons.DUEL, x + 18, y + 55, 14).fill(Gfx.alpha(c, 0.8f)).draw(g);
            paraMid(g, ui("done", who.displayName()), x + 34, y + 55, cw - 44, 5f, SystemTheme.TEXT_SOFT);
            Gfx.setAlpha(old);
            return;
        }
        DuelRecord.Reason why = DuelRecord.Reason.byIndex(ch2.getInt("why"));
        boolean open = ch2.getBoolean("open");

        // the head, and the clock on it
        SysFont.spaced(g, ui("head").getString(), x, y, 5f, SystemTheme.TEXT, true, 1.3f, 0f);
        long left = ch2.getLong("left");
        String clock = ui(open ? "lapses" : "coming", clock(left)).getString();
        SysFont.draw(g, clock, x + cw, y, 4.6f, open && left < 20 * 120 ? AMBER : SystemTheme.TEXT_SOFT, true, 1f);

        // why they came, and the level they fight at
        float by = y + 11;
        Ui.card(g, x, by, cw, 38, 9, c, 0.12f);
        Shapes.circle(x + 17, by + 19, 11).fillV(Gfx.alpha(c, 0.32f), Gfx.alpha(c, 0.06f)).border(0.7f, Gfx.alpha(c, 0.9f)).glow(3, Gfx.alpha(c, 0.35f)).draw(g);
        Shapes.icon(whyIcon(why), x + 17, by + 19, 12).fill(Gfx.mix(c, 0xFFFFFFFF, 0.6f)).draw(g);
        float ty = para(g, why(who, why), x + 34, by + 6, cw - 42, 5f, SystemTheme.TEXT);
        String level = ui(why == DuelRecord.Reason.TEST ? "level.yours" : "level", Math.round(ch2.getFloat("level"))).getString();
        SysFont.text(g, SysFont.fit(level, 4.6f, false, cw - 42), x + 34, Math.max(ty + 1, by + 26), 4.6f, Gfx.mix(c, SystemTheme.TEXT_SOFT, 0.5f));

        // how the two stand on the national rankings, and how it went before
        float sy = by + 44;
        float third = (cw - 8) / 3f;
        CompoundTag r = d.getCompound("record");
        long theirs = r.getLong("power"), mine = d.getLong("power");
        term(g, x, sy, third, Icons.CROWN, ui("their_power").getString(), gold(theirs), c, theirs > mine ? AMBER : SystemTheme.TEXT);
        term(g, x + third + 4, sy, third, Icons.ID_CARD, ui("your_power").getString(), gold(mine), c, mine >= theirs ? SystemTheme.GOOD : SystemTheme.TEXT);
        String last = switch (r.getInt("last")) {
            case DuelRecord.WON -> ui("last.won").getString();
            case DuelRecord.LOST -> ui("last.lost").getString();
            case DuelRecord.DRAWN -> ui("last.drawn").getString();
            default -> ui("last.none").getString();
        };
        term(g, x + 2 * (third + 4), sy, third, Icons.DUEL, ui("last").getString(), last, c, SystemTheme.TEXT);

        // what a win pays
        float wy = sy + 24;
        SysFont.spaced(g, ui("stakes").getString(), x, wy, 4.4f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        float py = wy + 8;
        stake(g, x, py, third, Icons.COINS, GOLD, ui("stake.purse").getString(), gold(d.getLong("purse")));
        stake(g, x + third + 4, py, third, Icons.MEDAL, STEEL, ui("stake.merit").getString(), "+" + gold(d.getInt("merit")));
        stake(g, x + 2 * (third + 4), py, third, Icons.STAR, SystemTheme.GOOD, ui("stake.xp").getString(), "+" + gold(d.getLong("xp")));
        float ly = para(g, ui("stake.loss"), x, py + 22, cw, 4.2f, SystemTheme.TEXT_SOFT);

        // the rules of the ring
        float ry = ly + 3;
        SysFont.spaced(g, ui("rules").getString(), x, ry, 4.4f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        float rule = ry + 8;
        rule = rule(g, x, rule, cw, Icons.DUEL, ui("rule.ring", d.getInt("radius") * 2, d.getInt("outsideSeconds")), c);
        rule = rule(g, x, rule, cw, Icons.HOURGLASS, ui("rule.time", clock(d.getInt("seconds") * 20L)), c);
        rule = rule(g, x, rule, cw, Icons.SHIELD, ui("rule.alone"), c);

        // the answers
        float btnY = y + ch - 18;

        // between the rules and the answers: what they said when they came, in their own words
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && btnY - 6 - (rule + 5) > 20) {
            Component said = Component.translatable(com.beyondsolo.duel.Duels.KEY + "greet." + why.key() + "." + who.id,
                    player.getGameProfile().getName(), who.guild().displayName());
            quote(g, x, rule + 5, cw, btnY - 6 - (rule + 5), said, who, c);
        }
        float bw3 = (cw - 8) / 3f;
        int later = ch2.getInt("later"), maxLater = ch2.getInt("maxLater");
        boolean canLater = later < maxLater;
        Component acceptWhat = open ? ui("hint.accept") : ui("hint.not_yet", who.displayName());
        Component laterWhat = canLater ? ui("hint.later", maxLater - later) : ui("hint.no_later", who.displayName());
        Component declineWhat = ui(why == DuelRecord.Reason.ASKED ? "hint.decline_asked" : "hint.decline");
        button(g, x, btnY, bw3, 16, ui("accept").getString(), Icons.DUEL, c, mouseX, mouseY, acceptWhat,
                open ? () -> AssociationClient.send(AssociationActionPayload.DUEL_ACCEPT) : null);
        button(g, x + bw3 + 4, btnY, bw3, 16, ui("later").getString(), Icons.HOURGLASS, STEEL, mouseX, mouseY, laterWhat,
                canLater ? () -> AssociationClient.send(AssociationActionPayload.DUEL_LATER) : null);
        button(g, x + 2 * (bw3 + 4), btnY, bw3, 16, ui("decline").getString(), Icons.X, SystemTheme.DANGER, mouseX, mouseY, declineWhat,
                () -> AssociationClient.send(AssociationActionPayload.DUEL_DECLINE));
        Gfx.setAlpha(old);
    }

    /** One figure: an icon, what it is, the figure. */
    static void term(GuiGraphics g, float x, float y, float w, ResourceLocation icon, String label, String value, int c, int valueColor) {
        Shapes.box(x, y, w, 19).radius(6).fill(Gfx.alpha(c, 0.07f)).border(0.6f, Gfx.alpha(c, 0.3f)).draw(g);
        Shapes.icon(icon, x + 8, y + 9.5f, 7).fill(Gfx.mix(c, 0xFFFFFFFF, 0.4f)).draw(g);
        SysFont.text(g, SysFont.fit(label, 4f, false, w - 20), x + 15, y + 3.4f, 4f, SystemTheme.TEXT_DIM);
        SysFont.text(g, SysFont.fit(value, 5f, true, w - 20), x + 15, y + 10, 5f, valueColor);
    }

    /** One of the stakes: a lit figure. */
    private static void stake(GuiGraphics g, float x, float y, float w, ResourceLocation icon, int c, String label, String value) {
        Shapes.box(x, y, w, 19).radius(6).fillV(Gfx.alpha(c, 0.14f), Gfx.alpha(c, 0.04f)).border(0.6f, Gfx.alpha(c, 0.45f)).draw(g);
        Shapes.icon(icon, x + 9, y + 9.5f, 8).fill(c).glow(2, Gfx.alpha(c, 0.45f)).draw(g);
        SysFont.text(g, SysFont.fit(label, 4f, false, w - 22), x + 17, y + 3.4f, 4f, SystemTheme.TEXT_DIM);
        SysFont.text(g, SysFont.fit(value, 5.4f, true, w - 22), x + 17, y + 9.6f, 5.4f, Gfx.mix(c, 0xFFFFFFFF, 0.3f));
    }

    /** What they said: their words in a speech card in their colour, their name under them. */
    private static void quote(GuiGraphics g, float x, float y, float w, float maxH, Component said, NationalHunter who, int c) {
        float size = 4.8f;
        java.util.List<SysFont.Line> lines = SysFont.wrap(said, size, false, w - 28);
        float textH = (lines.size() - 1) * size * SysFont.LINE + SysFont.CAP * size;
        float h = Math.min(maxH, textH + 22);
        // in the middle of the room it has
        y += (maxH - h) / 2f;
        Shapes.box(x, y, w, h).radius(8).fillV(Gfx.alpha(c, 0.11f), Gfx.alpha(c, 0.03f)).border(0.6f, Gfx.alpha(c, 0.35f)).draw(g);
        Shapes.box(x + 3, y + 5, 1.6f, h - 10).radius(0.8f).fill(Gfx.alpha(c, 0.85f)).glow(3, Gfx.alpha(c, 0.4f)).draw(g);
        Shapes.icon(Icons.CHAT, x + 13, y + 9.5f, 9).fill(Gfx.mix(c, 0xFFFFFFFF, 0.35f)).glow(2, Gfx.alpha(c, 0.4f)).draw(g);
        float ty = y + 6.5f;
        for (SysFont.Line l : lines) {
            if (ty + SysFont.CAP * size > y + h - 9) break;
            SysFont.draw(g, l, x + 22, ty, SystemTheme.TEXT, 0f);
            ty += size * SysFont.LINE;
        }
        SysFont.spaced(g, who.displayName().getString(), x + w - 7, y + h - 8.5f, 4.2f, Gfx.mix(c, 0xFFFFFFFF, 0.45f), true, 1.1f, 1f);
    }

    /** One rule of the ring; returns the y under it. */
    private static float rule(GuiGraphics g, float x, float y, float w, ResourceLocation icon, Component text, int c) {
        Shapes.icon(icon, x + 3, y + 2.4f, 5.5f).fill(Gfx.alpha(c, 0.85f)).draw(g);
        return para(g, text, x + 9, y, w - 10, 4.3f, SystemTheme.TEXT_SOFT) + 1;
    }
}
