package com.beyondsolo.client.association;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.network.AssociationActionPayload;
import com.beyondsolo.rival.RivalGuild;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A great guild's scout putting their offer: the scout on the left over their guild's crest and what the guild is
 * known for; on the right the signing bonus (it climbs when the hunter holds out and the guild raises), the terms, the
 * rest of the table (the other guilds' offers, the best of them lit), and the three answers: sign, hold out for more,
 * turn it down.
 */
public class ScoutScreen extends AssociationWindow {
    /** The bonus as shown: it runs up to a new figure. */
    private double shown = -1, from, to;
    private double changedAt = -10;
    /** When the window first showed the hunter signed with this scout's guild. */
    private double signedAt = -1;

    ScoutScreen() {
        super(Component.translatable("ui.beyondsolo.scouting.title"), 440, 258, 132);
    }

    @Override
    CompoundTag data() {
        return AssociationClient.scout();
    }

    RivalGuild guild() {
        return RivalGuild.byIndex(data().getInt("guild"));
    }

    @Override
    int accent() {
        return guild().color;
    }

    @Override
    Component role() {
        return ui("role", guild().displayName());
    }

    static Component ui(String key, Object... args) {
        return Component.translatable("ui.beyondsolo.scouting." + key, args);
    }

    /** The scout's own offer, or null (answered, or gone). */
    @Nullable
    CompoundTag mine() {
        CompoundTag d = data();
        if (!d.hasUUID("mine")) return null;
        UUID id = d.getUUID("mine");
        ListTag offers = d.getList("offers", Tag.TAG_COMPOUND);
        for (int i = 0; i < offers.size(); i++) if (offers.getCompound(i).getUUID("id").equals(id)) return offers.getCompound(i);
        return null;
    }

    // ------------------------------------------------------------------ left: the guild

    @Override
    void under(GuiGraphics g, float x, float y, float lw, float bottom, float t) {
        RivalGuild guild = guild();
        int gc = guild.color;
        Ui.divider(g, x, y, lw, Gfx.alpha(gc, 0.4f));
        Shapes.circle(x + 9, y + 15, 8).fillV(Gfx.alpha(gc, 0.3f), Gfx.alpha(gc, 0.06f)).border(0.7f, Gfx.alpha(gc, 0.9f)).glow(3, Gfx.alpha(gc, 0.35f)).draw(g);
        Shapes.icon(Icons.GUILDS[guild.ordinal()], x + 9, y + 15, 10).fill(Gfx.mix(gc, 0xFFFFFFFF, 0.6f)).draw(g);
        SysFont.text(g, SysFont.fit(guild.displayName().getString(), 6f, true, lw - 22), x + 21, y + 9, 6f, Gfx.mix(gc, 0xFFFFFFFF, 0.35f));
        SysFont.text(g, SysFont.fit(ui("great_guild").getString(), 4.2f, false, lw - 22), x + 21, y + 17, 4.2f, SystemTheme.TEXT_DIM);
        float my = para(g, ui("motto." + guild.key()), x, y + 28, lw, 4.4f, SystemTheme.TEXT_SOFT) + 4;
        // the hunter's own standing in the bidding: how many have come, and their contract
        CompoundTag d = data();
        CompoundTag c = d.getCompound("contract");
        float ry = Math.max(my, bottom - 22);
        if (c.contains("guild")) {
            RivalGuild cg = RivalGuild.byIndex(c.getInt("guild"));
            SysFont.spaced(g, ui("under_contract").getString(), x, ry, 4.2f, SystemTheme.TEXT_DIM, true, 1.1f, 0f);
            SysFont.text(g, SysFont.fit(ui("contract_line", cg.displayName(), c.getLong("daysLeft")).getString(), 4.6f, false, lw),
                    x, ry + 8, 4.6f, cg.color);
        } else {
            SysFont.spaced(g, ui("free_agent").getString(), x, ry, 4.2f, SystemTheme.TEXT_DIM, true, 1.1f, 0f);
            SysFont.text(g, SysFont.fit(ui("free_agent.line").getString(), 4.6f, false, lw), x, ry + 8, 4.6f, SystemTheme.TEXT_SOFT);
        }
    }

    // ------------------------------------------------------------------ right: the offer

    @Override
    void content(GuiGraphics g, float x, float y, float cw, float ch, int mouseX, int mouseY, float t) {
        CompoundTag o = mine();
        RivalGuild guild = guild();
        int gc = guild.color;
        float k = Anim.outCubic(Anim.window(t, 0.2f, 0.55f));
        float old = Gfx.fade(k);
        if (o == null && c(data()).contains("guild") && c(data()).getInt("guild") == guild.ordinal()) {
            signed(g, x, y, cw, ch, guild, c(data()));
            Gfx.setAlpha(old);
            return;
        }
        if (o == null) {
            Ui.card(g, x, y + 30, cw, 50, 9, gc, 0.08f);
            Shapes.icon(Icons.HANDSHAKE, x + 18, y + 55, 14).fill(Gfx.alpha(gc, 0.8f)).draw(g);
            paraMid(g, ui("done"), x + 34, y + 55, cw - 44, 5f, SystemTheme.TEXT_SOFT);
            Gfx.setAlpha(old);
            return;
        }
        long bonus = o.getLong("bonus");
        if (shown < 0) {
            shown = to = from = bonus;
        } else if (bonus != to) {
            from = shown;
            to = bonus;
            changedAt = Anim.now();
        }
        float since = Anim.since(changedAt);
        float run = Anim.outCubic(Anim.clamp01(since / 0.9f));
        shown = from + (to - from) * run;
        boolean last = o.getBoolean("last");

        // the head: what it is, and the clock on it
        String head = ui(o.getBoolean("renewal") ? "head.renewal" : "head").getString();
        SysFont.spaced(g, head, x, y, 5f, SystemTheme.TEXT, true, 1.3f, 0f);
        String lapse = ui("lapses", clock(o.getLong("left"))).getString();
        SysFont.draw(g, lapse, x + cw, y, 4.6f, o.getLong("left") < 20 * 120 ? AMBER : SystemTheme.TEXT_SOFT, true, 1f);

        // the signing bonus
        float by = y + 11;
        Ui.card(g, x, by, cw, 44, 9, gc, 0.12f);
        float flash = 1 - Anim.clamp01(since / 1.4f);
        if (flash > 0) Shapes.box(x, by, cw, 44).radius(9).fill(0).border(1f, Gfx.alpha(GOLD, flash)).glow(10 * flash, Gfx.alpha(GOLD, 0.45f * flash)).draw(g);
        SysFont.spaced(g, ui("bonus").getString(), x + 10, by + 6, 4.4f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        String big = gold(Math.round(shown));
        float bw = SysFont.width(big, 13f, true);
        Shapes.icon(Icons.COINS, x + 16, by + 26, 11).fill(GOLD).glow(3, Gfx.alpha(GOLD, 0.5f)).draw(g);
        SysFont.draw(g, big, x + 26, by + 18.5f, 13f, Gfx.mix(GOLD, 0xFFFFFFFF, 0.25f + 0.4f * flash), true, 0f);
        long first = o.getLong("first");
        if (bonus > first) {
            String up = "+" + Math.round(100.0 * (bonus - first) / Math.max(1, first)) + "%";
            Ui.chip(g, x + 30 + bw, by + 21, 8, up, 4.6f, SystemTheme.GOOD, true);
        }
        if (last) Ui.chip(g, x + cw - 10 - SysFont.width(ui("final").getString(), 4.2f, true) - 8, by + 6, 7.5f, ui("final").getString(), 4.2f, AMBER, true);
        SysFont.text(g, SysFont.fit(ui("for_days", o.getInt("days")).getString(), 4.6f, false, cw - 20), x + 10, by + 34, 4.6f, SystemTheme.TEXT_SOFT);

        // the terms
        float ty = by + 50;
        term(g, x, ty, cw / 2f - 3, Icons.TAG, ui("term.cut").getString(), Math.round(o.getFloat("cut") * 100) + "%", gc);
        term(g, x + cw / 2f + 3, ty, cw / 2f - 3, Icons.STAR, ui("term.perk").getString(), AssociationClient.text(o, "perk").getString(), gc);
        float ay = ty + 22;
        ally(g, x, ay, cw, guild);

        // the rest of the table
        float ry = ay + 14;
        SysFont.spaced(g, ui("table").getString(), x, ry, 4.4f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        ListTag offers = data().getList("offers", Tag.TAG_COMPOUND);
        long best = 0;
        for (int i = 0; i < offers.size(); i++) best = Math.max(best, offers.getCompound(i).getLong("bonus"));
        float rx = x, rowY = ry + 8;
        int shownRows = 0;
        for (int i = 0; i < offers.size(); i++) {
            CompoundTag x2 = offers.getCompound(i);
            if (x2.getUUID("id").equals(o.getUUID("id"))) continue;
            float w = (cw - 6) / 3f;
            rival(g, x2, rx, rowY, w, x2.getLong("bonus") == best);
            rx += w + 3;
            if (++shownRows >= 3) break;
        }
        if (shownRows == 0) {
            int coming = data().getInt("coming");
            Component none = coming > 0 ? ui("table.coming", coming) : ui("table.none");
            SysFont.text(g, SysFont.fit(none.getString(), 4.6f, false, cw), x, rowY + 2, 4.6f, SystemTheme.TEXT_SOFT);
        }

        // the answers, and what each one does (the row of the button under the mouse lights up)
        float btnY = y + ch - 18;
        float bw3 = (cw - 8) / 3f;
        boolean underContract = c(data()).contains("guild");
        Component signWhat = underContract ? ui("hint.sign_buyout", gold(bonus), RivalGuild.byIndex(c(data()).getInt("guild")).displayName())
                : ui("hint.sign", gold(bonus), o.getInt("days"));
        Component holdWhat = last ? ui("hint.final") : ui("hint.hold_out");
        float wy = rowY + 28;
        if (btnY - 4 - wy >= 8 + 3 * 21 + 6) {
            SysFont.spaced(g, ui("answers").getString(), x, wy, 4.4f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
            answer(g, x, wy + 8, cw, Icons.HANDSHAKE, GOLD, ui("sign").getString(), signWhat, Ui.inside(mouseX, mouseY, x, btnY, bw3, 16));
            answer(g, x, wy + 32, cw, Icons.COINS, STEEL, ui("hold_out").getString(), holdWhat, Ui.inside(mouseX, mouseY, x + bw3 + 4, btnY, bw3, 16));
            answer(g, x, wy + 56, cw, Icons.X, SystemTheme.DANGER, ui("decline").getString(), ui("hint.decline"),
                    Ui.inside(mouseX, mouseY, x + 2 * (bw3 + 4), btnY, bw3, 16));
        }
        UUID id = o.getUUID("id");
        CompoundTag args = new CompoundTag();
        args.putUUID("offer", id);
        button(g, x, btnY, bw3, 16, ui("sign").getString(), Icons.HANDSHAKE, GOLD, mouseX, mouseY, signWhat,
                () -> AssociationClient.send(AssociationActionPayload.SIGN, args));
        button(g, x + bw3 + 4, btnY, bw3, 16, ui("hold_out").getString(), Icons.COINS, STEEL, mouseX, mouseY, holdWhat,
                last ? null : () -> AssociationClient.send(AssociationActionPayload.HOLD_OUT, args));
        button(g, x + 2 * (bw3 + 4), btnY, bw3, 16, ui("decline").getString(), Icons.X, SystemTheme.DANGER, mouseX, mouseY,
                ui("hint.decline"), () -> AssociationClient.send(AssociationActionPayload.DECLINE, args));
        Gfx.setAlpha(old);
    }

    /** Signed with this scout's guild: the welcome, what was paid, and the terms that run now. */
    private void signed(GuiGraphics g, float x, float y, float cw, float ch, RivalGuild guild, CompoundTag c) {
        if (signedAt < 0) signedAt = Anim.now();
        float s = Anim.since(signedAt);
        float k = Anim.outCubic(Anim.clamp01(s / 0.5f));
        float flash = 1 - Anim.clamp01(s / 1.4f);
        int gc = guild.color;
        float old = Gfx.fade(k);
        SysFont.spaced(g, ui("signed.head").getString(), x, y, 5f, SystemTheme.TEXT, true, 1.3f, 0f);
        float by = y + 11 + (1 - k) * 6;
        Ui.card(g, x, by, cw, 54, 9, gc, 0.16f);
        if (flash > 0) Shapes.box(x, by, cw, 54).radius(9).fill(0).border(1f, Gfx.alpha(gc, flash)).glow(12 * flash, Gfx.alpha(gc, 0.5f * flash)).draw(g);
        // the guild's crest, and the welcome
        Shapes.circle(x + 26, by + 27, 15).fillV(Gfx.alpha(gc, 0.35f), Gfx.alpha(gc, 0.08f)).border(0.8f, Gfx.alpha(gc, 0.9f))
                .glow(4 + 6 * flash, Gfx.alpha(gc, 0.4f)).draw(g);
        Shapes.icon(Icons.GUILDS[guild.ordinal()], x + 26, by + 27, 18).fill(Gfx.mix(gc, 0xFFFFFFFF, 0.6f)).draw(g);
        SysFont.text(g, SysFont.fit(ui("signed.title", guild.displayName()).getString(), 7f, true, cw - 58), x + 50, by + 12, 7f, Gfx.mix(gc, 0xFFFFFFFF, 0.5f));
        Shapes.icon(Icons.COINS, x + 54, by + 34.5f, 8).fill(GOLD).glow(2, Gfx.alpha(GOLD, 0.5f)).draw(g);
        SysFont.text(g, SysFont.fit(ui("signed.paid", gold(c.getLong("bonus"))).getString(), 5.2f, true, cw - 68), x + 61, by + 31.5f, 5.2f, GOLD);
        // the terms that run now
        float ty = by + 60;
        term(g, x, ty, cw / 2f - 3, Icons.TAG, ui("term.cut").getString(), Math.round(c.getFloat("cut") * 100) + "%", gc);
        term(g, x + cw / 2f + 3, ty, cw / 2f - 3, Icons.STAR, ui("term.perk").getString(), AssociationClient.text(c, "perk").getString(), gc);
        ally(g, x, ty + 22, cw, guild);
        para(g, ui("signed.days", c.getLong("daysLeft")), x, ty + 36, cw, 4.6f, SystemTheme.TEXT_SOFT);
        // at the foot, where the answers were: what leaving early costs
        float fy = y + ch - 18;
        Ui.divider(g, x, fy - 6, cw, Gfx.alpha(gc, 0.3f));
        Shapes.icon(Icons.LEAVE, x + 5, fy + 8, 7).fill(SystemTheme.TEXT_DIM).draw(g);
        paraMid(g, ui("signed.walk_out"), x + 13, fy + 8, cw - 16, 4.4f, SystemTheme.TEXT_DIM);
        Gfx.setAlpha(old);
    }

    private static CompoundTag c(CompoundTag d) {
        return d.getCompound("contract");
    }

    /** One of the terms: an icon, what it is, the figure. */
    private static void term(GuiGraphics g, float x, float y, float w, net.minecraft.resources.ResourceLocation icon, String label, String value, int gc) {
        Shapes.box(x, y, w, 18).radius(6).fill(Gfx.alpha(gc, 0.07f)).border(0.6f, Gfx.alpha(gc, 0.3f)).draw(g);
        Shapes.icon(icon, x + 8, y + 9, 7).fill(Gfx.mix(gc, 0xFFFFFFFF, 0.4f)).draw(g);
        SysFont.text(g, SysFont.fit(label, 4f, false, w - 20), x + 15, y + 3.2f, 4f, SystemTheme.TEXT_DIM);
        SysFont.text(g, SysFont.fit(value, 4.8f, true, w - 20), x + 15, y + 9.6f, 4.8f, SystemTheme.TEXT);
    }

    /** One of the answers: its mark and name, and what it does. */
    private static void answer(GuiGraphics g, float x, float y, float w, net.minecraft.resources.ResourceLocation icon, int c, String label,
                               Component what, boolean lit) {
        Shapes.box(x, y, w, 21).radius(6).fill(Gfx.alpha(c, lit ? 0.16f : 0.06f)).border(lit ? 0.8f : 0.5f, Gfx.alpha(c, lit ? 0.75f : 0.28f)).draw(g);
        Shapes.icon(icon, x + 9, y + 10.5f, 7).fill(Gfx.mix(c, 0xFFFFFFFF, 0.35f)).draw(g);
        SysFont.text(g, SysFont.fit(label, 4.8f, true, 40), x + 17, SysFont.centerY(y, 21, 4.8f), 4.8f, Gfx.mix(c, 0xFFFFFFFF, lit ? 0.6f : 0.35f));
        paraMid(g, what, x + 60, y + 10.5f, w - 66, 4.2f, lit ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT);
    }

    /** What being one of the guild's own means out there: its parties stand down for you. */
    private static void ally(GuiGraphics g, float x, float y, float w, RivalGuild guild) {
        Shapes.icon(Icons.FLAG, x + 3, y + 3, 6).fill(Gfx.alpha(guild.color, 0.9f)).draw(g);
        SysFont.text(g, SysFont.fit(ui("allies", guild.displayName()).getString(), 4.4f, false, w - 10), x + 9, y + 0.8f, 4.4f, SystemTheme.TEXT_SOFT);
    }

    /** Another guild's offer on the table. */
    private static void rival(GuiGraphics g, CompoundTag o, float x, float y, float w, boolean best) {
        RivalGuild guild = RivalGuild.byIndex(o.getInt("guild"));
        int gc = guild.color;
        Shapes.box(x, y, w, 22).radius(6).fillV(Gfx.alpha(gc, best ? 0.2f : 0.09f), Gfx.alpha(Gfx.shade(gc, -0.5f), 0.06f))
                .border(best ? 0.9f : 0.6f, Gfx.alpha(best ? GOLD : gc, best ? 0.8f : 0.35f)).draw(g);
        Shapes.icon(Icons.GUILDS[guild.ordinal()], x + 8, y + 11, 8).fill(Gfx.mix(gc, 0xFFFFFFFF, 0.5f)).draw(g);
        SysFont.text(g, SysFont.fit(guild.displayName().getString(), 4.2f, true, w - 20), x + 15, y + 3.5f, 4.2f, Gfx.mix(gc, 0xFFFFFFFF, 0.4f));
        String amount = gold(o.getLong("bonus"));
        SysFont.text(g, amount, x + 15, y + 11, 5f, best ? GOLD : SystemTheme.TEXT);
        if (o.getBoolean("last")) {
            String fin = ui("final").getString();
            SysFont.draw(g, fin, x + w - 4, y + 12, 3.6f, AMBER, true, 1f);
        }
    }
}
