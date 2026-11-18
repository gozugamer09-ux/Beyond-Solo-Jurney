package com.beyondsolo.client.guild;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.economy.EconomyClient;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.TextField;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTab;
import com.beyondsolo.guild.GuildColors;
import com.beyondsolo.guild.GuildRole;
import com.beyondsolo.network.GuildActionPayload;
import com.beyondsolo.raid.RaidRole;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The GUILD page of the System window. Without a guild: the founding form (name, tag, crest and colour, with the
 * banner taking shape as it is filled in), the invitations waiting and what a guild gives. In a guild, four pages:
 * the HALL (the crest, level and its perks, the treasury, what happened lately), MEMBERS (ranks, who is online, inviting
 * and managing them), HUNTERS (the hired hunters and today's recruits) and RAIDS (the raid underway, the roles, and
 * the raid permits officers can request).
 */
public class GuildTab implements SystemTab {
    public enum Page { HALL, MEMBERS, HUNTERS, RAIDS }

    private static final int GUILD = 0xFFE0B04A;
    private static final int GOOD = SystemTheme.GOOD;
    private static final int INVITE = 0xFF8BE28B;
    private static final int RAID = 0xFFB98BFF;
    private static final float ROW_H = 19;

    private static Page page = Page.HALL;
    /** The founding form's choices (kept while the window is closed and opened again). */
    private static int crest, color;
    private static final TextField NAME = new TextField(24, s -> s.replaceAll("[^\\p{L}\\p{N} '&.\\-]", ""));
    private static final TextField TAG = new TextField(4, s -> s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", ""));

    private record Hit(float x, float y, float w, float h, Runnable run) {}

    private final List<Hit> hits = new ArrayList<>();
    private Component hint;
    private int scrollLeft, scrollRight;

    @Override
    public Component title() {
        return Component.translatable("ui.beyondsolo.guild.tab");
    }

    @Override
    public boolean batched() {
        return true;
    }

    @Override
    public boolean isVisible() {
        return ClientSystemData.get().hasSystem();
    }

    @Override
    public void onOpened() {
        scrollLeft = scrollRight = 0;
    }

    /** Opens on a page (UI test driver). */
    public static void show(Page p) {
        page = p;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        hint = null;
        if (!GuildClient.inGuild()) {
            founding(g, x, y, 206, h, mouseX, mouseY);
            outside(g, x + 212, y, w - 212, h, mouseX, mouseY);
        } else {
            header(g, x, y, w, mouseX, mouseY);
            float by = y + 17, bh = h - 17;
            switch (page) {
                case HALL -> hall(g, x, by, w, bh, mouseX, mouseY);
                case MEMBERS -> members(g, x, by, w, bh, mouseX, mouseY);
                case HUNTERS -> hunters(g, x, by, w, bh, mouseX, mouseY);
                case RAIDS -> raids(g, x, by, w, bh, mouseX, mouseY);
            }
        }
        moment(g, x, y, w, h);
        toast(g, x, y, w, h);
    }

    // ---------------------------------------------------------------- no guild yet

    /** The founding form: the banner as it will look, its name and tag, crest and colour, and what it costs. */
    private void founding(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        int c = GuildArt.color(color);
        Ui.card(g, x, y, w, h, 9, c, 0.12f);
        Shapes.icon(Icons.FLAG, x + 13, y + 10.5f, 10).fill(Gfx.mix(c, 0xFFFFFFFF, 0.5f)).glow(2, Gfx.alpha(c, 0.5f)).draw(g);
        SysFont.spaced(g, tr("found.title"), x + 23, y + 5.5f, 7f, SystemTheme.TEXT, true, 1.8f, 0f);
        Ui.divider(g, x + 8, y + 17, w - 16, Gfx.alpha(c, 0.35f));

        // the banner taking shape
        float ex = x + 32, ey = y + 43;
        GuildArt.emblem(g, crest, color, ex, ey, 16.5f, 0.15f + 0.1f * Anim.pulse(3f));
        String tag = TAG.value().isEmpty() ? "TAG" : TAG.value();
        float tw = SysFont.width(tag, 5f, true) + 8;
        Shapes.pill(ex - tw / 2f, ey + 21.5f, tw, 8).fill(Gfx.alpha(0xFF060A14, 0.85f)).border(0.6f, Gfx.alpha(c, 0.9f)).draw(g);
        SysFont.draw(g, tag, ex, SysFont.centerY(ey + 21.5f, 8, 5f), 5f, Gfx.mix(c, 0xFFFFFFFF, 0.6f), true, 0.5f);

        // name and tag
        float fx = x + 62, fw = w - 70;
        SysFont.spaced(g, tr("found.name"), fx, y + 22, 4.6f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        NAME.render(g, fx, y + 28, fw, 13, tr("found.name_hint"), c, mouseX, mouseY);
        SysFont.spaced(g, tr("found.tag"), fx, y + 45, 4.6f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        TAG.render(g, fx, y + 51, 36, 13, tr("found.tag_hint"), c, mouseX, mouseY);
        String rules = Component.translatable("ui.beyondsolo.guild.found.rules", 3, 24).getString();
        float ry = y + 52;
        for (SysFont.Line l : SysFont.wrap(Component.literal(rules), 4.3f, false, fw - 42)) {
            SysFont.draw(g, l, fx + 41, ry, SystemTheme.TEXT_DIM, 0f);
            ry += 4.3f * SysFont.LINE;
        }

        // crest and colour
        SysFont.spaced(g, tr("found.crest"), x + 8, y + 78, 4.6f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        float step = (w - 16) / GuildColors.CRESTS;
        for (int i = 0; i < GuildColors.CRESTS; i++) {
            float cx = x + 8 + step * i + step / 2f, cy = y + 90.5f;
            boolean sel = i == crest;
            boolean hover = Ui.inside(mouseX, mouseY, cx - 6.5f, cy - 6.5f, 13, 13);
            Shapes.circle(cx, cy, 6.5f).fill(Gfx.alpha(c, sel ? 0.35f : hover ? 0.2f : 0.08f))
                    .border(sel ? 0.9f : 0.5f, Gfx.alpha(c, sel ? 1f : 0.4f)).glow(sel ? 4 : 0, Gfx.alpha(c, 0.4f)).draw(g);
            Shapes.icon(GuildArt.crest(i), cx, cy, 8.5f).fill(sel ? Gfx.mix(c, 0xFFFFFFFF, 0.7f) : SystemTheme.TEXT_SOFT).draw(g);
            int pick = i;
            hits.add(new Hit(cx - 6.5f, cy - 6.5f, 13, 13, () -> {
                crest = pick;
                SystemScreen.playTick(1.2f + pick * 0.04f);
            }));
        }
        SysFont.spaced(g, tr("found.color"), x + 8, y + 100.5f, 4.6f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        float cstep = (w - 16) / GuildColors.PALETTE.length;
        for (int i = 0; i < GuildColors.PALETTE.length; i++) {
            float cx = x + 8 + cstep * i + cstep / 2f, cy = y + 112.5f;
            int sw = GuildArt.color(i);
            boolean sel = i == color;
            Shapes.circle(cx, cy, sel ? 5.5f : 4.5f).fillV(Gfx.mix(sw, 0xFFFFFFFF, 0.25f), Gfx.shade(sw, -0.25f))
                    .border(sel ? 1f : 0.5f, sel ? 0xFFFFFFFF : Gfx.alpha(sw, 0.6f)).glow(sel ? 4 : 0, Gfx.alpha(sw, 0.6f)).draw(g);
            int pick = i;
            hits.add(new Hit(cx - 6, cy - 6, 12, 12, () -> {
                color = pick;
                SystemScreen.playTick(1.0f + pick * 0.05f);
            }));
        }

        // what it costs, and the button
        long cost = GuildClient.foundingCost(), have = EconomyClient.gold();
        boolean evaluated = ClientSystemData.get().isAwakened();
        String costLine = Component.translatable("ui.beyondsolo.guild.found.cost", fmt(cost), fmt(have)).getString();
        Shapes.icon(Icons.COINS, x + 12, y + h - 33.5f, 7).fill(SystemTheme.GOLD).draw(g);
        SysFont.text(g, SysFont.fit(costLine, 5f, false, w - 26), x + 19, y + h - 35.8f, 5f, have >= cost ? SystemTheme.TEXT_SOFT : 0xFFFF9A8A);
        String nameNow = NAME.value().trim();
        boolean ready = evaluated && nameNow.length() >= 3 && TAG.value().length() >= 2 && have >= cost;
        Component why = !evaluated ? Component.translatable("ui.beyondsolo.guild.found.need_eval")
                : nameNow.length() < 3 || TAG.value().length() < 2 ? Component.translatable("ui.beyondsolo.guild.found.fill")
                : have < cost ? Component.translatable("ui.beyondsolo.guild.found.need_gold", fmt(cost - have))
                : Component.translatable("ui.beyondsolo.guild.found.hint", nameNow, TAG.value());
        button(g, x + 8, y + h - 24, w - 16, 17, tr("found.button"), Icons.FLAG, ready ? GUILD : SystemTheme.TEXT_DIM, mouseX, mouseY, why,
                ready ? () -> {
                    CompoundTag a = new CompoundTag();
                    a.putString("name", nameNow);
                    a.putString("tag", TAG.value());
                    a.putInt("crest", crest);
                    a.putInt("color", color);
                    GuildClient.send(GuildActionPayload.FOUND, a);
                } : null);
    }

    /** Without a guild, on the right: the invitations waiting, then what a guild is for. */
    private void outside(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        List<CompoundTag> invites = GuildClient.invites();
        float cy = y;
        if (!invites.isEmpty()) {
            int n = Math.min(3, invites.size());
            float ch = 15 + n * 19;
            Ui.card(g, x, cy, w, ch, 8, INVITE, 0.16f);
            float pulse = Anim.pulse(1.6f);
            Shapes.icon(Icons.INVITE, x + 10, cy + 7.5f, 7.5f).fill(Gfx.mix(INVITE, 0xFFFFFFFF, 0.3f)).glow(2 + 2 * pulse, Gfx.alpha(INVITE, 0.5f)).draw(g);
            SysFont.spaced(g, tr("invites"), x + 18, cy + 4.5f, 5.6f, Gfx.mix(INVITE, 0xFFFFFFFF, 0.4f), true, 1.3f, 0f);
            for (int i = 0; i < n; i++) {
                CompoundTag inv = invites.get(invites.size() - 1 - i);
                float iy = cy + 14 + i * 19;
                Shapes.box(x + 4, iy, w - 8, 17).radius(5).fill(Gfx.alpha(INVITE, 0.07f)).border(0.5f, Gfx.alpha(INVITE, 0.3f)).draw(g);
                GuildArt.badge(g, inv.getInt("crest"), inv.getInt("color"), x + 12, iy + 8.5f, 5.5f);
                String name = SysFont.fit(inv.getString("name") + " [" + inv.getString("tag") + "]", 5.4f, true, w - 62);
                SysFont.text(g, name, x + 20, iy + 2.4f, 5.4f, GuildArt.text(inv.getInt("color")));
                String sub = Component.translatable("ui.beyondsolo.guild.invite_line", inv.getInt("level"), inv.getInt("size"),
                        clock(GuildClient.ticksLeft(inv.getInt("ticks")))).getString();
                SysFont.text(g, SysFont.fit(sub, 4.3f, false, w - 62), x + 20, iy + 10, 4.3f, SystemTheme.TEXT_DIM);
                UUID id = inv.getUUID("id");
                iconButton(g, x + w - 8 - 12 - 14, iy + 2.5f, 12, Icons.CHECK, INVITE, true, mouseX, mouseY,
                        Component.translatable("ui.beyondsolo.guild.hint.accept", inv.getString("name")), () -> GuildClient.send(GuildActionPayload.ACCEPT, id));
                iconButton(g, x + w - 8 - 12, iy + 2.5f, 12, Icons.X, SystemTheme.DANGER, true, mouseX, mouseY,
                        Component.translatable("ui.beyondsolo.guild.hint.decline", inv.getString("name")), () -> GuildClient.send(GuildActionPayload.DECLINE, id));
            }
            cy += ch + 5;
        }
        float ch = y + h - cy;
        Ui.card(g, x, cy, w, ch, 8, GUILD, 0.07f);
        SysFont.spaced(g, tr("gives.title"), x + 8, cy + 5, 5.6f, Gfx.mix(GUILD, 0xFFFFFFFF, 0.35f), true, 1.3f, 0f);
        ResourceLocation[] icons = {Icons.VILLAGERS, Icons.HANDSHAKE, Icons.SEAL, Icons.COINS, Icons.CHAT};
        float ly = cy + 16;
        for (int i = 0; i < icons.length && ly < y + h - 8; i++) {
            Shapes.icon(icons[i], x + 11, ly + 3, 6.5f).fill(Gfx.mix(GUILD, 0xFFFFFFFF, 0.35f)).draw(g);
            float ty = ly;
            for (SysFont.Line l : SysFont.wrap(Component.translatable("ui.beyondsolo.guild.gives." + i), 4.8f, false, w - 26)) {
                SysFont.draw(g, l, x + 19, ty, SystemTheme.TEXT_SOFT, 0f);
                ty += 4.8f * SysFont.LINE;
            }
            ly = Math.max(ly + 10, ty + 3);
        }
    }

    // ---------------------------------------------------------------- in a guild

    /** The guild's name and the four pages across the top. */
    private void header(GuiGraphics g, float x, float y, float w, int mouseX, int mouseY) {
        float px = x;
        for (Page p : Page.values()) {
            String label = tr("page." + p.name().toLowerCase(Locale.ROOT));
            float lw = SysFont.spacedWidth(label, 5.4f, true, 1f) + 14;
            boolean sel = p == page;
            boolean hover = !sel && Ui.inside(mouseX, mouseY, px, y, lw, 12);
            int c = GuildArt.color(GuildClient.color());
            Shapes.pill(px, y, lw, 12).fill(sel ? Gfx.alpha(c, 0.3f) : hover ? 0x18FFFFFF : 0x0CFFFFFF)
                    .border(0.6f, sel ? Gfx.alpha(c, 0.95f) : Gfx.alpha(c, 0.3f)).glow(sel ? 3 : 0, Gfx.alpha(c, 0.3f)).draw(g);
            SysFont.spaced(g, label, px + lw / 2f, SysFont.centerY(y, 12, 5.4f), 5.4f, sel ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, 1f, 0.5f);
            Page to = p;
            hits.add(new Hit(px, y, lw, 12, () -> {
                if (page != to) {
                    page = to;
                    scrollLeft = scrollRight = 0;
                    SystemScreen.playClick();
                }
            }));
            px += lw + 3;
        }
        // the guild on the right
        GuildArt.badge(g, GuildClient.crest(), GuildClient.color(), x + w - 6, y + 6, 5.5f);
        String name = GuildClient.name() + " [" + GuildClient.tag() + "]";
        name = SysFont.fit(name, 5.8f, true, x + w - 14 - px - 4);
        SysFont.draw(g, name, x + w - 14, SysFont.centerY(y, 12, 5.8f), 5.8f, GuildArt.text(GuildClient.color()), true, 1f);
    }

    // ---- HALL

    private void hall(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        CompoundTag s = GuildClient.state();
        int c = GuildArt.color(GuildClient.color());
        float lw = 178;
        Ui.card(g, x, y, lw, h, 9, c, 0.1f);
        float energy = GuildClient.momentKind() >= 0 ? Math.max(0, 1 - GuildClient.sinceMoment() / 3f) : 0;
        GuildArt.emblem(g, GuildClient.crest(), GuildClient.color(), x + 26, y + 26, 16, 0.12f + energy);
        float tx = x + 50;
        SysFont.text(g, SysFont.fit(GuildClient.name(), 7.4f, true, lw - 58), tx, y + 8, 7.4f, SystemTheme.TEXT);
        int level = GuildClient.level();
        boolean max = level >= com.beyondsolo.guild.GuildConfig.MAX_LEVEL;
        String lv = max ? Component.translatable("ui.beyondsolo.guild.level_max", level).getString()
                : Component.translatable("ui.beyondsolo.guild.level", level).getString();
        float cw = Ui.chip(g, tx, y + 18, 8, lv, 4.8f, c, true);
        Ui.chip(g, tx + cw + 3, y + 18, 8, "[" + GuildClient.tag() + "]", 4.8f, c, false);
        long xp = s.getLong("xp"), next = Math.max(1, s.getLong("xpNext"));
        Ui.bar(g, tx, y + 30, lw - 58, 2.6f, max ? 1f : Mth.clamp(xp / (float) next, 0, 1), Gfx.shade(c, -0.25f), Gfx.mix(c, 0xFFFFFFFF, 0.35f));
        String xpLine = max ? Component.translatable("ui.beyondsolo.guild.xp_max").getString()
                : Component.translatable("ui.beyondsolo.guild.xp", fmt(xp), fmt(next)).getString();
        SysFont.text(g, SysFont.fit(xpLine, 4.4f, false, lw - 58), tx, y + 34.5f, 4.4f, SystemTheme.TEXT_DIM);
        String founded = Component.translatable("ui.beyondsolo.guild.founded", s.getLong("founded") + 1).getString() + "  ·  "
                + Component.translatable("ui.beyondsolo.guild.you_are", GuildClient.role().displayName()).getString();
        SysFont.text(g, SysFont.fit(founded, 4.4f, false, lw - 16), x + 8, y + 47, 4.4f, SystemTheme.TEXT_SOFT);

        // four numbers
        float sy = y + 56, sw = (lw - 16) / 4f;
        String[][] stats = {
                {GuildClient.members().size() + "/" + s.getInt("memberCap"), "members"},
                {GuildClient.hunters().size() + "/" + s.getInt("rosterCap"), "hunters"},
                {Integer.toString(s.getInt("gates")), "gates"},
                {Integer.toString(s.getInt("raids")), "raids"}};
        ResourceLocation[] icons = {Icons.VILLAGERS, Icons.HANDSHAKE, Icons.SEAL, Icons.CROWN};
        for (int i = 0; i < 4; i++) {
            float bx = x + 8 + sw * i;
            Shapes.box(bx + 1, sy, sw - 2, 22).radius(5).fill(Gfx.alpha(c, 0.07f)).border(0.5f, Gfx.alpha(c, 0.25f)).draw(g);
            Shapes.icon(icons[i], bx + sw / 2f, sy + 5.5f, 6).fill(Gfx.mix(c, 0xFFFFFFFF, 0.4f)).draw(g);
            SysFont.draw(g, stats[i][0], bx + sw / 2f, sy + 9.5f, 5.6f, SystemTheme.TEXT, true, 0.5f);
            SysFont.draw(g, SysFont.fit(tr("stat." + stats[i][1]), 3.9f, false, sw - 4), bx + sw / 2f, sy + 16.3f, 3.9f, SystemTheme.TEXT_DIM, false, 0.5f);
        }

        // what this level gives
        float py = sy + 27;
        SysFont.spaced(g, tr("perks"), x + 8, py, 4.8f, Gfx.mix(c, 0xFFFFFFFF, 0.35f), true, 1.2f, 0f);
        PlayerRank permit = PlayerRank.byName(s.getString("permitRank"));
        String[] perks = {
                Component.translatable("ui.beyondsolo.guild.perk.gold", pct(s.getDouble("goldBonus"))).getString(),
                Component.translatable("ui.beyondsolo.guild.perk.xp", pct(s.getDouble("xpBonus"))).getString(),
                Component.translatable("ui.beyondsolo.guild.perk.permit", permit == null ? "?" : permit.displayName().getString()).getString(),
                Component.translatable("ui.beyondsolo.guild.perk.discount", pct(s.getDouble("discount"))).getString()};
        ResourceLocation[] perkIcons = {Icons.COINS, Icons.SPARKLE, Icons.SEAL, Icons.TAG};
        for (int i = 0; i < perks.length; i++) {
            float lx = x + 8 + (i % 2) * ((lw - 16) / 2f), ly = py + 8 + (i / 2) * 9;
            Shapes.icon(perkIcons[i], lx + 3, ly + 2.4f, 5.5f).fill(GOOD).draw(g);
            SysFont.text(g, SysFont.fit(perks[i], 4.6f, false, (lw - 16) / 2f - 9), lx + 8, ly, 4.6f, SystemTheme.TEXT_SOFT);
        }

        // the treasury and the log
        float rx = x + lw + 6, rw = w - lw - 6;
        float th = 46;
        Ui.card(g, rx, y, rw, th, 9, SystemTheme.GOLD, 0.12f);
        Shapes.icon(Icons.VAULT, rx + 11, y + 10, 9).fill(SystemTheme.GOLD).glow(2, Gfx.alpha(SystemTheme.GOLD, 0.5f)).draw(g);
        SysFont.spaced(g, tr("treasury"), rx + 19, y + 5.5f, 5.6f, Gfx.mix(SystemTheme.GOLD, 0xFFFFFFFF, 0.3f), true, 1.3f, 0f);
        SysFont.draw(g, fmt(GuildClient.treasury()), rx + rw - 8, y + 4.5f, 8f, SystemTheme.GOLD, true, 1f);
        long[] amounts = {100, 1_000, 10_000};
        float bw = (rw - 16 - 6) / 3f;
        long have = EconomyClient.gold();
        for (int i = 0; i < 3; i++) {
            long a = amounts[i];
            boolean can = have >= a;
            button(g, rx + 8 + i * (bw + 3), y + 17, bw, 12, "+" + fmt(a), Icons.DEPOSIT, can ? SystemTheme.GOLD : SystemTheme.TEXT_DIM, mouseX, mouseY,
                    Component.translatable("ui.beyondsolo.guild.hint.deposit", fmt(a)), can ? () -> {
                        CompoundTag t = new CompoundTag();
                        t.putLong("amount", a);
                        GuildClient.send(GuildActionPayload.DEPOSIT, t);
                    } : null);
        }
        if (GuildClient.role() == GuildRole.MASTER) {
            long out = Math.min(GuildClient.treasury(), 1_000);
            boolean can = out > 0;
            button(g, rx + 8, y + 31.5f, rw - 16, 11, Component.translatable("ui.beyondsolo.guild.withdraw", fmt(Math.max(out, 0))).getString(), Icons.COINS,
                    can ? 0xFFC9A35A : SystemTheme.TEXT_DIM, mouseX, mouseY, Component.translatable("ui.beyondsolo.guild.hint.withdraw", fmt(out)),
                    can ? () -> {
                        CompoundTag t = new CompoundTag();
                        t.putLong("amount", out);
                        GuildClient.send(GuildActionPayload.WITHDRAW, t);
                    } : null);
        } else {
            String note = Component.translatable("ui.beyondsolo.guild.treasury_note").getString();
            SysFont.text(g, SysFont.fit(note, 4.3f, false, rw - 16), rx + 8, y + 34, 4.3f, SystemTheme.TEXT_DIM);
        }

        float ly = y + th + 5, lh = h - th - 5;
        Ui.card(g, rx, ly, rw, lh, 8, c, 0.06f);
        SysFont.spaced(g, tr("log"), rx + 8, ly + 5, 5.2f, Gfx.mix(c, 0xFFFFFFFF, 0.35f), true, 1.2f, 0f);
        List<CompoundTag> log = GuildClient.log();
        if (log.isEmpty()) {
            SysFont.text(g, tr("log.empty"), rx + 8, ly + 16, 4.6f, SystemTheme.TEXT_DIM);
            return;
        }
        float row = 4.6f * SysFont.LINE + 1.6f;
        int fit = Math.max(1, (int) ((lh - 16) / row));
        float yy = ly + 15;
        for (int i = log.size() - 1, shown = 0; i >= 0 && shown < fit; i--, shown++) {
            CompoundTag l = log.get(i);
            String day = Component.translatable("ui.beyondsolo.guild.log.day", l.getLong("day") + 1).getString();
            SysFont.text(g, day, rx + 8, yy, 4.2f, SystemTheme.TEXT_DIM);
            String text = logLine(l).getString();
            SysFont.text(g, SysFont.fit(text, 4.6f, false, rw - 40), rx + 32, yy - 0.2f, 4.6f, SystemTheme.TEXT_SOFT);
            yy += row;
        }
    }

    /** A line of the guild's history: "Kael joined", "Hired Mira Voss", "Reached level 4". */
    static Component logLine(CompoundTag l) {
        ListTag args = l.getList("args", Tag.TAG_STRING);
        Object[] a = new Object[args.size()];
        String key = l.getString("key");
        for (int i = 0; i < args.size(); i++) {
            String v = args.getString(i);
            // ranks and gold travel as plain words: shown the way the page shows them
            if ((key.equals("permit") && i == 1) || (key.equals("raid") && i == 0)) {
                PlayerRank r = PlayerRank.byName(v);
                a[i] = r == null ? v : r.displayName();
            } else if ((key.equals("deposit") || key.equals("withdraw")) && i == 1) {
                try {
                    a[i] = fmt(Long.parseLong(v));
                } catch (NumberFormatException e) {
                    a[i] = v;
                }
            } else {
                a[i] = v;
            }
        }
        return Component.translatable("ui.beyondsolo.guild.log." + key, a);
    }

    // ---- MEMBERS

    private void members(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        CompoundTag s = GuildClient.state();
        int c = GuildArt.color(GuildClient.color());
        float lw = 218;
        Ui.card(g, x, y, lw, h, 9, c, 0.08f);
        Shapes.icon(Icons.VILLAGERS, x + 12, y + 8.5f, 9).fill(Gfx.mix(c, 0xFFFFFFFF, 0.5f)).draw(g);
        SysFont.spaced(g, tr("members.title"), x + 20, y + 4.5f, 5.8f, SystemTheme.TEXT, true, 1.4f, 0f);
        List<CompoundTag> members = new ArrayList<>(GuildClient.members());
        // the master first, then officers, then the rest; online before offline
        members.sort(Comparator.comparingInt((CompoundTag m) -> -GuildRole.byId(m.getString("role")).ordinal())
                .thenComparing(m -> !m.getBoolean("online")));
        SysFont.draw(g, members.size() + " / " + s.getInt("memberCap"), x + lw - 8, y + 5, 5.6f, Gfx.mix(c, 0xFFFFFFFF, 0.4f), true, 1f);
        GuildRole mine = GuildClient.role();
        float ly = y + 15;
        int fit = Math.max(1, (int) ((h - 17) / (ROW_H + 2)));
        scrollLeft = Mth.clamp(scrollLeft, 0, Math.max(0, members.size() - fit));
        for (int i = scrollLeft; i < Math.min(members.size(), scrollLeft + fit); i++) {
            memberRow(g, members.get(i), mine, x + 5, ly, lw - 10, mouseX, mouseY);
            ly += ROW_H + 2;
        }
        if (members.size() > fit) SysFont.draw(g, (scrollLeft + fit) + "/" + members.size(), x + lw - 8, y + h - 7, 4.2f, SystemTheme.TEXT_DIM, false, 1f);

        // inviting, and leaving
        float rx = x + lw + 6, rw = w - lw - 6;
        float footH = mine == GuildRole.MASTER ? 34 : 18;
        invitePanel(g, rx, y, rw, h - footH - 4, mine, members.size() >= s.getInt("memberCap"), mouseX, mouseY);
        float fy = y + h - footH;
        button(g, rx, fy, rw, 15, tr("leave"), Icons.LEAVE, SystemTheme.DANGER, mouseX, mouseY, Component.translatable("ui.beyondsolo.guild.hint.leave"),
                () -> GuildClient.send(GuildActionPayload.LEAVE));
        if (mine == GuildRole.MASTER) {
            button(g, rx, fy + 18, rw, 15, tr("disband"), Icons.X, 0xFFB03A48, mouseX, mouseY, Component.translatable("ui.beyondsolo.guild.hint.disband"),
                    () -> GuildClient.send(GuildActionPayload.DISBAND));
        }
    }

    private void memberRow(GuiGraphics g, CompoundTag m, GuildRole mine, float x, float y, float w, int mouseX, int mouseY) {
        UUID id = m.getUUID("id");
        boolean me = id.equals(GuildClient.me());
        boolean online = m.getBoolean("online");
        GuildRole role = GuildRole.byId(m.getString("role"));
        int cls = m.getInt("cls");
        int cc = cls >= 0 && cls < PlayerClass.values().length ? SystemTheme.playerClass(PlayerClass.values()[cls]) : SystemTheme.TEXT_SOFT;
        int tint = online ? cc : SystemTheme.TEXT_DIM;
        Shapes.box(x, y, w, ROW_H).radius(6).fill(Gfx.alpha(tint, me ? 0.12f : 0.05f)).border(0.5f, Gfx.alpha(tint, me ? 0.5f : 0.25f)).draw(g);
        float bx = x + 10, by = y + ROW_H / 2f;
        Shapes.circle(bx, by, 7f).fillV(Gfx.alpha(tint, 0.3f), Gfx.alpha(Gfx.shade(tint, -0.5f), 0.4f)).border(0.6f, Gfx.alpha(tint, 0.9f)).draw(g);
        ResourceLocation icon = cls >= 0 && cls < PlayerClass.values().length ? Icons.of(PlayerClass.values()[cls]) : Icons.QUESTION;
        Shapes.icon(icon, bx, by, 8.5f).fill(online ? Gfx.mix(tint, 0xFFFFFFFF, 0.55f) : SystemTheme.TEXT_DIM).draw(g);
        // the online light
        Shapes.circle(bx + 5.5f, by + 5.5f, 1.8f).fill(online ? GOOD : 0xFF4A5566).border(0.4f, 0xFF060A14).draw(g);

        // buttons: what this member may do to that one
        List<Object[]> buttons = new ArrayList<>();
        if (!me && mine == GuildRole.MASTER) {
            buttons.add(new Object[]{GuildActionPayload.TRANSFER, Icons.CROWN, SystemTheme.GOLD, "transfer"});
            if (role == GuildRole.MEMBER) buttons.add(new Object[]{GuildActionPayload.PROMOTE, Icons.MEDAL, GUILD, "promote"});
            else buttons.add(new Object[]{GuildActionPayload.DEMOTE, Icons.MEDAL, SystemTheme.TEXT_SOFT, "demote"});
        }
        if (!me && mine.manages() && (mine == GuildRole.MASTER || role == GuildRole.MEMBER)) {
            buttons.add(new Object[]{GuildActionPayload.KICK, Icons.X, SystemTheme.DANGER, "kick"});
        }
        float right = x + w - 3;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Object[] b = buttons.get(i);
            float size = 12;
            int action = (int) b[0];
            iconButton(g, right - size, y + (ROW_H - size) / 2f, size, (ResourceLocation) b[1], (int) b[2], true, mouseX, mouseY,
                    Component.translatable("ui.beyondsolo.guild.hint." + b[3], m.getString("name")), () -> GuildClient.send(action, id));
            right -= size + 2;
        }

        float tx = x + 20;
        String name = SysFont.fit(m.getString("name"), 5.8f, true, right - tx - 50);
        SysFont.text(g, name, tx, y + 3, 5.8f, online ? SystemTheme.TEXT : SystemTheme.TEXT_DIM);
        float after = tx + SysFont.width(name, 5.8f, true) + 3;
        if (role == GuildRole.MASTER) {
            Shapes.icon(Icons.CROWN, after + 3, y + 5.5f, 6.5f).fill(SystemTheme.GOLD).glow(2, Gfx.alpha(SystemTheme.GOLD, 0.5f)).draw(g);
            after += 9;
        } else if (role == GuildRole.OFFICER) {
            Shapes.icon(Icons.MEDAL, after + 3, y + 5.5f, 6.2f).fill(GUILD).draw(g);
            after += 9;
        }
        if (me) Ui.chip(g, after, y + 2, 7, Component.translatable("ui.beyondsolo.party.you").getString(), 4.4f, SystemTheme.ACCENT, false);
        String line = role.displayName().getString();
        int rank = m.getInt("rank");
        if (online && rank >= 0 && cls >= 0) {
            line += " · " + Component.translatable("ui.beyondsolo.guild.hunter_line", PlayerRank.values()[rank].displayName(),
                    m.getInt("lvl"), PlayerClass.values()[cls].displayName()).getString();
        } else if (!online) {
            line += " · " + Component.translatable("ui.beyondsolo.party.place.offline").getString();
        }
        SysFont.text(g, SysFont.fit(line, 4.5f, false, right - tx - 2), tx, y + 11.3f, 4.5f, SystemTheme.TEXT_DIM);
        String given = Component.translatable("ui.beyondsolo.guild.given", fmt(m.getLong("given"))).getString();
        SysFont.draw(g, SysFont.fit(given, 4.4f, false, 60), right - 2, y + 3.4f, 4.4f, Gfx.alpha(SystemTheme.GOLD, 0.85f), false, 1f);
    }

    private void invitePanel(GuiGraphics g, float x, float y, float w, float h, GuildRole mine, boolean full, int mouseX, int mouseY) {
        Ui.card(g, x, y, w, h, 8, INVITE, 0.07f);
        SysFont.spaced(g, tr("invite.title"), x + 8, y + 5, 5.4f, Gfx.mix(INVITE, 0xFFFFFFFF, 0.4f), true, 1.2f, 0f);
        List<PlayerInfo> list = candidates();
        float ly = y + 15;
        if (!mine.manages() || full || list.isEmpty()) {
            String why = !mine.manages() ? tr("invite.only_officers") : full ? tr("invite.full") : tr("invite.nobody");
            float ty = ly + 2;
            for (SysFont.Line l : SysFont.wrap(Component.literal(why), 4.8f, false, w - 16)) {
                SysFont.draw(g, l, x + 8, ty, SystemTheme.TEXT_DIM, 0f);
                ty += 4.8f * SysFont.LINE;
            }
            return;
        }
        float rowH = 13;
        int fit = Math.max(1, (int) ((h - 17) / rowH));
        scrollRight = Mth.clamp(scrollRight, 0, Math.max(0, list.size() - fit));
        for (int i = scrollRight; i < Math.min(list.size(), scrollRight + fit); i++) {
            PlayerInfo p = list.get(i);
            float ry = ly + (i - scrollRight) * rowH;
            boolean hover = Ui.inside(mouseX, mouseY, x + 4, ry, w - 8, rowH - 1);
            Shapes.box(x + 4, ry, w - 8, rowH - 1).radius(4).fill(Gfx.alpha(INVITE, hover ? 0.12f : 0.05f)).draw(g);
            SysFont.text(g, SysFont.fit(p.getProfile().getName(), 5.2f, true, w - 30), x + 8, SysFont.centerY(ry, rowH - 1, 5.2f), 5.2f, SystemTheme.TEXT);
            UUID id = p.getProfile().getId();
            iconButton(g, x + w - 8 - 10.5f, ry + 0.75f, 10.5f, Icons.INVITE, INVITE, true, mouseX, mouseY,
                    Component.translatable("ui.beyondsolo.guild.hint.invite", p.getProfile().getName()), () -> GuildClient.send(GuildActionPayload.INVITE, id));
        }
    }

    /** Everyone on the player list who is not in the guild yet, by name. */
    private static List<PlayerInfo> candidates() {
        Minecraft mc = Minecraft.getInstance();
        List<PlayerInfo> out = new ArrayList<>();
        if (mc.getConnection() == null) return out;
        Set<UUID> in = new HashSet<>();
        for (CompoundTag m : GuildClient.members()) in.add(m.getUUID("id"));
        for (PlayerInfo p : mc.getConnection().getOnlinePlayers()) {
            if (!in.contains(p.getProfile().getId())) out.add(p);
        }
        out.sort(Comparator.comparing(p -> p.getProfile().getName().toLowerCase(Locale.ROOT)));
        return out;
    }

    // ---- HUNTERS

    private void hunters(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        CompoundTag s = GuildClient.state();
        int c = GuildArt.color(GuildClient.color());
        boolean manage = GuildClient.role().manages();
        float lw = 196;
        Ui.card(g, x, y, lw, h, 9, c, 0.08f);
        Shapes.icon(Icons.HANDSHAKE, x + 12, y + 8.5f, 9).fill(Gfx.mix(c, 0xFFFFFFFF, 0.5f)).draw(g);
        SysFont.spaced(g, tr("hunters.title"), x + 20, y + 4.5f, 5.8f, SystemTheme.TEXT, true, 1.4f, 0f);
        List<CompoundTag> hired = GuildClient.hunters();
        int cap = s.getInt("rosterCap");
        SysFont.draw(g, hired.size() + " / " + cap, x + lw - 8, y + 5, 5.6f, Gfx.mix(c, 0xFFFFFFFF, 0.4f), true, 1f);
        float ly = y + 15;
        if (hired.isEmpty()) {
            float ty = ly + 4;
            for (SysFont.Line l : SysFont.wrap(Component.translatable("ui.beyondsolo.guild.hunters.none"), 5f, false, lw - 20)) {
                SysFont.draw(g, l, x + 10, ty, SystemTheme.TEXT_DIM, 0f);
                ty += 5f * SysFont.LINE;
            }
        } else {
            int fit = Math.max(1, (int) ((h - 17) / (ROW_H + 2)));
            scrollLeft = Mth.clamp(scrollLeft, 0, Math.max(0, hired.size() - fit));
            for (int i = scrollLeft; i < Math.min(hired.size(), scrollLeft + fit); i++) {
                hunterRow(g, hired.get(i), x + 5, ly, lw - 10, true, manage, false, mouseX, mouseY);
                ly += ROW_H + 2;
            }
        }

        float rx = x + lw + 6, rw = w - lw - 6;
        Ui.card(g, rx, y, rw, h, 9, GUILD, 0.08f);
        SysFont.spaced(g, tr("recruits.title"), rx + 8, y + 5, 5.4f, Gfx.mix(GUILD, 0xFFFFFFFF, 0.35f), true, 1.2f, 0f);
        List<CompoundTag> pool = GuildClient.recruits();
        boolean full = hired.size() >= cap;
        float py = y + 15;
        int fit = Math.max(1, (int) ((h - 27) / (ROW_H + 2)));
        scrollRight = Mth.clamp(scrollRight, 0, Math.max(0, pool.size() - fit));
        for (int i = scrollRight; i < Math.min(pool.size(), scrollRight + fit); i++) {
            hunterRow(g, pool.get(i), rx + 4, py, rw - 8, false, manage, full, mouseX, mouseY);
            py += ROW_H + 2;
        }
        if (pool.isEmpty()) SysFont.text(g, tr("recruits.none"), rx + 8, py + 2, 4.8f, SystemTheme.TEXT_DIM);
        String note = !manage ? tr("recruits.only_officers") : full ? tr("recruits.full")
                : Component.translatable("ui.beyondsolo.guild.recruits.note", fmt(GuildClient.treasury())).getString();
        SysFont.text(g, SysFont.fit(note, 4.3f, false, rw - 16), rx + 8, y + h - 8.5f, 4.3f, SystemTheme.TEXT_DIM);
    }

    /** A hired hunter (with their record and whether they are fit) or a recruit (with their price). */
    private void hunterRow(GuiGraphics g, CompoundTag t, float x, float y, float w, boolean hired, boolean manage, boolean full, int mouseX, int mouseY) {
        PlayerClass cls;
        try {
            cls = PlayerClass.valueOf(t.getString("class"));
        } catch (IllegalArgumentException e) {
            cls = PlayerClass.WARRIOR;
        }
        int level = t.getInt("level");
        PlayerRank rank = com.beyondsolo.system.Balance.rankFor(level);
        int cc = SystemTheme.playerClass(cls);
        long injured = hired ? GuildClient.ticksLeft(t.getLong("injured")) : 0;
        boolean busy = hired && t.getBoolean("busy");
        int tint = injured > 0 ? SystemTheme.TEXT_DIM : cc;
        Shapes.box(x, y, w, ROW_H).radius(6).fill(Gfx.alpha(tint, 0.06f)).border(0.5f, Gfx.alpha(tint, 0.28f)).draw(g);
        float bx = x + 10, by = y + ROW_H / 2f;
        Shapes.circle(bx, by, 7f).fillV(Gfx.alpha(cc, 0.3f), Gfx.alpha(Gfx.shade(cc, -0.5f), 0.4f)).border(0.6f, Gfx.alpha(cc, 0.9f)).draw(g);
        Shapes.icon(Icons.of(cls), bx, by, 8.5f).fill(Gfx.mix(cc, 0xFFFFFFFF, 0.55f)).draw(g);
        UUID id = t.getUUID("id");
        String name = t.getString("name");
        float right = x + w - 3;
        if (manage) {
            if (hired) {
                iconButton(g, right - 11, y + (ROW_H - 11) / 2f, 11, Icons.X, SystemTheme.DANGER, !busy, mouseX, mouseY,
                        Component.translatable(busy ? "ui.beyondsolo.guild.hint.dismiss_busy" : "ui.beyondsolo.guild.hint.dismiss", name),
                        busy ? null : () -> GuildClient.send(GuildActionPayload.DISMISS, id));
            } else {
                long cost = t.getLong("cost");
                boolean can = !full && GuildClient.treasury() >= cost;
                iconButton(g, right - 11, y + (ROW_H - 11) / 2f, 11, Icons.CHECK, GOOD, can, mouseX, mouseY,
                        full ? Component.translatable("ui.beyondsolo.guild.recruits.full")
                                : Component.translatable("ui.beyondsolo.guild.hint.hire", name, fmt(cost)),
                        can ? () -> GuildClient.send(GuildActionPayload.HIRE, id) : null);
            }
            right -= 14;
        }
        float tx = x + 20;
        SysFont.text(g, SysFont.fit(name, 5.6f, true, right - tx - 30), tx, y + 3, 5.6f, SystemTheme.TEXT);
        String rankLine = Component.translatable("ui.beyondsolo.guild.hunter_rank", rank.displayName(), level).getString();
        SysFont.draw(g, rankLine, right - 1, y + 3.4f, 4.6f, Gfx.mix(SystemTheme.rank(rank), 0xFFFFFFFF, 0.3f), true, 1f);
        String sub;
        int subColor = SystemTheme.TEXT_DIM;
        RaidRole role = RaidRole.forClass(cls);
        if (hired) {
            if (busy) {
                sub = tr("status.raiding");
                subColor = RAID;
            } else if (injured > 0) {
                sub = Component.translatable("ui.beyondsolo.guild.status.injured", clock(injured)).getString();
                subColor = 0xFFFF8A8A;
            } else {
                sub = tr("status.ready") + " · " + Component.translatable("ui.beyondsolo.guild.hunters.record", t.getInt("raids"), t.getInt("kills")).getString();
                subColor = SystemTheme.TEXT_SOFT;
            }
        } else {
            sub = Component.translatable("ui.beyondsolo.guild.recruits.price", fmt(t.getLong("cost"))).getString();
            subColor = GuildClient.treasury() >= t.getLong("cost") ? Gfx.alpha(SystemTheme.GOLD, 0.9f) : 0xFFB08A5A;
        }
        String roleName = role.displayName().getString();
        float rw = SysFont.width(roleName, 4.2f, true) + 6;
        Ui.chip(g, tx, y + 10.6f, 6.4f, roleName, 4.2f, role.color, false);
        SysFont.text(g, SysFont.fit(cls.displayName().getString() + " · " + sub, 4.4f, false, right - tx - rw - 4), tx + rw + 3, y + 11.4f, 4.4f, subColor);
    }

    // ---- RAIDS

    private void raids(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        CompoundTag s = GuildClient.state();
        float lw = 196;
        CompoundTag raid = GuildClient.raid();
        float th = raid == null ? 44 : 58;
        Ui.card(g, x, y, lw, th, 9, RAID, raid == null ? 0.06f : 0.14f);
        if (raid == null) {
            Shapes.icon(Icons.SEAL, x + 12, y + 9, 9).fill(Gfx.mix(RAID, 0xFFFFFFFF, 0.4f)).draw(g);
            SysFont.spaced(g, tr("raids.none"), x + 20, y + 5, 5.6f, SystemTheme.TEXT, true, 1.2f, 0f);
            float ty = y + 16;
            for (SysFont.Line l : SysFont.wrap(Component.translatable("ui.beyondsolo.guild.raids.none_hint"), 4.6f, false, lw - 16)) {
                SysFont.draw(g, l, x + 8, ty, SystemTheme.TEXT_SOFT, 0f);
                ty += 4.6f * SysFont.LINE;
            }
        } else {
            PlayerRank rank = PlayerRank.values()[Mth.clamp(raid.getInt("rank"), 0, PlayerRank.values().length - 1)];
            Ui.rankEmblem(g, rank, x + 20, y + 22, 11, 0.2f + 0.2f * Anim.pulse(2f));
            String phase = raid.getString("phase").toLowerCase(Locale.ROOT);
            SysFont.spaced(g, Component.translatable("ui.beyondsolo.guild.raids.current", rank.displayName()).getString(), x + 38, y + 6, 6.2f,
                    SystemTheme.TEXT, true, 1.3f, 0f);
            Ui.chip(g, x + 38, y + 15, 7.5f, Component.translatable("raid.beyondsolo.phase." + phase).getString(), 4.6f, RAID, true);
            if (raid.getBoolean("permit")) Ui.chip(g, x + lw - 8 - 40, y + 15, 7.5f, tr("raids.permit"), 4.4f, GUILD, false);
            String[] lines = {
                    Component.translatable("ui.beyondsolo.guild.raids.leader", raid.getString("leader")).getString(),
                    Component.translatable("ui.beyondsolo.guild.raids.count", raid.getInt("raiders"), raid.getInt("hunters")).getString(),
                    Component.translatable("ui.beyondsolo.guild.raids.where", raid.getInt("x"), raid.getInt("z")).getString()};
            float ty = y + 26;
            for (String l : lines) {
                SysFont.text(g, SysFont.fit(l, 4.7f, false, lw - 46), x + 38, ty, 4.7f, SystemTheme.TEXT_SOFT);
                ty += 7.6f;
            }
            String dir = direction(raid.getInt("x"), raid.getInt("z"));
            if (!dir.isEmpty()) SysFont.draw(g, dir, x + lw - 8, y + th - 8.5f, 4.6f, Gfx.mix(RAID, 0xFFFFFFFF, 0.4f), true, 1f);
        }

        // the roles
        float ry = y + th + 5, rh = h - th - 5;
        Ui.card(g, x, ry, lw, rh, 8, RAID, 0.05f);
        SysFont.spaced(g, tr("roles"), x + 8, ry + 5, 5.2f, Gfx.mix(RAID, 0xFFFFFFFF, 0.4f), true, 1.2f, 0f);
        float yy = ry + 15;
        ResourceLocation[] roleIcons = {Icons.SHIELD, Icons.FLAME, Icons.HEART};
        for (RaidRole role : RaidRole.values()) {
            if (yy > ry + rh - 10) break;
            Shapes.circle(x + 13, yy + 4, 5.5f).fill(Gfx.alpha(role.color, 0.25f)).border(0.6f, role.color).draw(g);
            Shapes.icon(roleIcons[role.ordinal()], x + 13, yy + 4, 6.5f).fill(Gfx.mix(role.color, 0xFFFFFFFF, 0.5f)).draw(g);
            SysFont.text(g, role.displayName().getString(), x + 22, yy - 0.5f, 5.2f, Gfx.mix(role.color, 0xFFFFFFFF, 0.3f));
            float ty = yy + 6;
            for (SysFont.Line l : SysFont.wrap(Component.translatable("ui.beyondsolo.raid.role." + role.id() + ".desc"), 4.3f, false, lw - 30)) {
                SysFont.draw(g, l, x + 22, ty, SystemTheme.TEXT_DIM, 0f);
                ty += 4.3f * SysFont.LINE;
            }
            yy = ty + 3;
        }

        // the permits
        float rx = x + lw + 6, rw = w - lw - 6;
        Ui.card(g, rx, y, rw, h, 9, GUILD, 0.08f);
        Shapes.icon(Icons.SEAL, rx + 11, y + 9, 8.5f).fill(Gfx.mix(GUILD, 0xFFFFFFFF, 0.4f)).draw(g);
        SysFont.spaced(g, tr("permits.title"), rx + 19, y + 5, 5.4f, Gfx.mix(GUILD, 0xFFFFFFFF, 0.35f), true, 1.2f, 0f);
        boolean manage = GuildClient.role().manages();
        int level = GuildClient.level();
        ListTag permits = s.getList("permits", Tag.TAG_COMPOUND);
        float py = y + 16;
        float rowH = Math.min(19, (h - 28) / Math.max(1, permits.size()));
        for (int i = 0; i < permits.size(); i++) {
            CompoundTag p = permits.getCompound(i);
            PlayerRank rank = PlayerRank.byName(p.getString("rank"));
            if (rank == null) continue;
            long price = p.getLong("price");
            int needs = p.getInt("needs");
            boolean unlocked = level >= needs;
            boolean can = manage && unlocked && raid == null && GuildClient.treasury() >= price;
            float ry0 = py + i * rowH;
            int rc = SystemTheme.rank(rank);
            Shapes.box(rx + 4, ry0, rw - 8, rowH - 2).radius(5).fill(Gfx.alpha(unlocked ? rc : SystemTheme.TEXT_DIM, 0.06f))
                    .border(0.5f, Gfx.alpha(unlocked ? rc : SystemTheme.TEXT_DIM, 0.3f)).draw(g);
            Ui.rankEmblem(g, rank, rx + 13, ry0 + (rowH - 2) / 2f, 5.2f, 0);
            String label = Component.translatable("ui.beyondsolo.guild.permit.rank", rank.displayName()).getString();
            SysFont.text(g, label, rx + 22, ry0 + 1.6f, 5f, unlocked ? SystemTheme.TEXT : SystemTheme.TEXT_DIM);
            String sub = unlocked ? Component.translatable("ui.beyondsolo.guild.permit.price", fmt(price)).getString()
                    : Component.translatable("ui.beyondsolo.guild.permit.needs", needs).getString();
            SysFont.text(g, SysFont.fit(sub, 4.3f, false, rw - 50), rx + 22, ry0 + 8.4f, 4.3f, unlocked ? Gfx.alpha(SystemTheme.GOLD, 0.85f) : SystemTheme.TEXT_DIM);
            if (manage && unlocked) {
                Component tip = raid != null ? Component.translatable("ui.beyondsolo.guild.hint.permit_busy")
                        : GuildClient.treasury() < price ? Component.translatable("ui.beyondsolo.guild.hint.permit_short", fmt(price))
                        : Component.translatable("ui.beyondsolo.guild.hint.permit", rank.displayName(), fmt(price));
                iconButton(g, rx + rw - 8 - 11, ry0 + (rowH - 2 - 11) / 2f, 11, Icons.FLAG, RAID, can, mouseX, mouseY, tip,
                        can ? () -> {
                            CompoundTag a = new CompoundTag();
                            a.putString("rank", rank.name());
                            GuildClient.send(GuildActionPayload.PERMIT, a);
                        } : null);
            } else if (!unlocked) {
                Shapes.icon(Icons.LOCK, rx + rw - 8 - 5.5f, ry0 + (rowH - 2) / 2f, 6.5f).fill(SystemTheme.TEXT_DIM).draw(g);
            }
        }
        String note = manage ? tr("permits.note") : tr("permits.only_officers");
        SysFont.text(g, SysFont.fit(note, 4.2f, false, rw - 16), rx + 8, y + h - 8.5f, 4.2f, SystemTheme.TEXT_DIM);
    }

    /** "1.2 km NE" to a point, from the player. */
    private static String direction(int x, int z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "";
        double dx = x + 0.5 - mc.player.getX(), dz = z + 0.5 - mc.player.getZ();
        int d = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        String dir = new String[] {"n", "ne", "e", "se", "s", "sw", "w", "nw"}[(int) Math.floorMod(Math.round(a / 45.0), 8)];
        return Component.translatable("ui.beyondsolo.party.away", d, Component.translatable("ui.beyondsolo.party.dir." + dir)).getString();
    }

    // ---------------------------------------------------------------- moments, toasts, widgets

    /** A guild just founded, or a level just gained: a banner over the page for a moment. */
    private void moment(GuiGraphics g, float x, float y, float w, float h) {
        int kind = GuildClient.momentKind();
        float t = GuildClient.sinceMoment();
        if (kind < 0 || t > 3.2f || !GuildClient.inGuild()) return;
        float k = (1 - Anim.window(t, 2.6f, 3.2f)) * Anim.outBack(Anim.window(t, 0f, 0.35f));
        String title = kind == com.beyondsolo.network.GuildEventPayload.FOUNDED ? tr("moment.founded")
                : Component.translatable("ui.beyondsolo.guild.moment.level", GuildClient.momentValue()).getString();
        int c = GuildArt.color(GuildClient.color());
        float bw = 200, bh = 34;
        float bx = x + (w - bw) / 2f, by = y + h / 2f - bh / 2f;
        float old = Gfx.fade(Mth.clamp(k, 0, 1));
        Shapes.box(bx, by, bw, bh).radius(9).fillV(Gfx.fade(SystemTheme.GLASS_TOP, 0.95f), Gfx.fade(SystemTheme.GLASS_BOTTOM, 0.95f))
                .border(1f, c, Gfx.alpha(c, 0.4f)).glow(10, Gfx.alpha(c, 0.4f)).draw(g);
        GuildArt.emblem(g, GuildClient.crest(), GuildClient.color(), bx + 18, by + bh / 2f, 10, 1f - t / 3.2f);
        SysFont.spaced(g, title, bx + 34, by + 7, 7f, SystemTheme.TEXT, true, 1.6f, 0f);
        SysFont.text(g, SysFont.fit(GuildClient.momentText().getString(), 5.2f, false, bw - 42), bx + 34, by + 19, 5.2f, GuildArt.text(GuildClient.color()));
        Gfx.setAlpha(old);
    }

    private void iconButton(GuiGraphics g, float x, float y, float size, ResourceLocation icon, int color, boolean enabled, int mouseX, int mouseY,
                            Component tip, Runnable run) {
        boolean hover = Ui.inside(mouseX, mouseY, x, y, size, size);
        int c = enabled ? color : SystemTheme.TEXT_DIM;
        Shapes.circle(x + size / 2f, y + size / 2f, size / 2f).fillV(Gfx.alpha(c, hover ? 0.4f : 0.18f), Gfx.alpha(Gfx.shade(c, -0.4f), hover ? 0.3f : 0.12f))
                .border(0.6f, Gfx.alpha(c, hover ? 1f : 0.6f)).glow(hover && enabled ? 4 : 0, Gfx.alpha(c, 0.4f)).draw(g);
        Shapes.icon(icon, x + size / 2f, y + size / 2f, size * 0.62f).fill(enabled ? Gfx.mix(c, 0xFFFFFFFF, hover ? 0.7f : 0.45f) : SystemTheme.TEXT_DIM).draw(g);
        if (hover) hint = tip;
        if (enabled && run != null) hits.add(new Hit(x, y, size, size, run));
    }

    private void button(GuiGraphics g, float x, float y, float w, float h, String label, ResourceLocation icon, int color, int mouseX, int mouseY,
                        Component tip, Runnable run) {
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, h);
        boolean enabled = run != null;
        Shapes.pill(x, y, w, h).fillV(Gfx.alpha(color, hover && enabled ? 0.38f : 0.2f), Gfx.alpha(Gfx.shade(color, -0.4f), hover && enabled ? 0.28f : 0.14f))
                .border(0.8f, Gfx.alpha(Gfx.mix(color, 0xFFFFFFFF, 0.25f), hover ? 1f : 0.7f), Gfx.alpha(color, 0.3f))
                .glow(hover && enabled ? 5 : enabled ? 2 : 0, Gfx.alpha(color, 0.3f)).draw(g);
        float size = Math.min(5.6f, h * 0.42f);
        String text = SysFont.fit(label, size, true, w - 18);
        float lw = SysFont.spacedWidth(text, size, true, 1f);
        float lx = x + (w - lw - 9) / 2f;
        int tc = enabled ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT;
        Shapes.icon(icon, lx + 3, y + h / 2f, Math.min(6.5f, h * 0.55f)).fill(tc).draw(g);
        SysFont.spaced(g, text, lx + 9, SysFont.centerY(y, h, size), size, tc, true, 1f, 0f);
        if (hover) hint = tip;
        if (enabled) hits.add(new Hit(x, y, w, h, run));
    }

    /** The last answer to a button, or what the button under the mouse does. */
    private void toast(GuiGraphics g, float x, float y, float w, float h) {
        float since = GuildClient.sinceOutcome();
        Component text = null;
        int color = SystemTheme.TEXT_SOFT;
        float k = 1;
        if (since < 4f && !GuildClient.outcome().getString().isEmpty()) {
            text = GuildClient.outcome();
            color = GuildClient.outcomeOk() ? SystemTheme.GOOD : SystemTheme.DANGER;
            k = 1 - Anim.window(since, 3.4f, 4f);
        } else if (hint != null) {
            text = hint;
        }
        if (text == null || k <= 0.01f) return;
        String line = SysFont.fit(text.getString(), 5.4f, false, w - 40);
        float lw = SysFont.width(line, 5.4f, false) + 16;
        float tx = x + (w - lw) / 2f, ty = y + h + 1;
        float old = Gfx.fade(k);
        Shapes.pill(tx, ty, lw, 10).fill(Gfx.alpha(0xFF060A14, 0.9f)).border(0.6f, Gfx.alpha(color, 0.7f)).glow(4, Gfx.alpha(color, 0.25f)).draw(g);
        SysFont.draw(g, line, tx + lw / 2f, SysFont.centerY(ty, 10, 5.4f), 5.4f, Gfx.mix(color, 0xFFFFFFFF, 0.3f), false, 0.5f);
        Gfx.setAlpha(old);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!GuildClient.inGuild()) {
            boolean onName = NAME.click(mouseX, mouseY);
            boolean onTag = TAG.click(mouseX, mouseY);
            if (onName || onTag) return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!Ui.inside(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            hit.run().run();
            SystemScreen.playClick();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int step = -(int) Math.signum(scrollY);
        if (mouseX < (Minecraft.getInstance().getWindow().getGuiScaledWidth()) / 2f) scrollLeft += step;
        else scrollRight += step;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (GuildClient.inGuild()) return false;
        if (keyCode == GLFW.GLFW_KEY_TAB && (NAME.focused() || TAG.focused())) {
            boolean toTag = NAME.focused();
            NAME.focus(!toTag);
            TAG.focus(toTag);
            return true;
        }
        return NAME.keyPressed(keyCode, modifiers) || TAG.keyPressed(keyCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (GuildClient.inGuild()) return false;
        return NAME.charTyped(c) || TAG.charTyped(c);
    }

    /** Fills in the founding form (UI test driver). */
    public static void fill(String name, String tag, int crestIndex, int colorIndex) {
        NAME.set(name);
        TAG.set(tag);
        crest = Math.floorMod(crestIndex, GuildColors.CRESTS);
        color = Math.floorMod(colorIndex, GuildColors.PALETTE.length);
    }

    // ------------------------------------------------------------------ text

    private static String tr(String key) {
        return Component.translatable("ui.beyondsolo.guild." + key).getString();
    }

    static String fmt(long n) {
        return NumberFormat.getIntegerInstance(Locale.ROOT).format(n);
    }

    private static String pct(double v) {
        double p = v * 100;
        return Math.abs(p - Math.round(p)) < 0.05 ? Long.toString(Math.round(p)) : String.format(Locale.ROOT, "%.1f", p);
    }

    static String clock(long ticks) {
        long sec = (ticks + 19) / 20;
        if (sec >= 3600) return sec / 3600 + ":" + String.format(Locale.ROOT, "%02d:%02d", (sec / 60) % 60, sec % 60);
        return sec / 60 + ":" + String.format(Locale.ROOT, "%02d", sec % 60);
    }
}
