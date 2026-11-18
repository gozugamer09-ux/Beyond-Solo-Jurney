package com.beyondsolo.client.party;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTab;
import com.beyondsolo.network.PartyActionPayload;
import com.beyondsolo.network.PartySyncPayload;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The PARTY page of the System window. On the left the party: every member with their class, rank and level, their
 * health, where they are (out in the world and how far, or inside a gate, a trial, the Citadel...), with buttons to
 * travel to them (regroup) and, for the leader, to hand over the lead or remove them. Alone, the page says what a party
 * is for. On the right the invitations waiting, the hunters online to invite, the regroup clock, and leaving.
 */
public class PartyTab implements SystemTab {
    private static final float LEFT_W = 214;
    private static final float ROW_H = 21;
    private static final float ROW_GAP = 2;
    private static final int PARTY = 0xFF5FD0FF;
    private static final int INVITE = 0xFF8BE28B;
    private static final int REGROUP = 0xFFB98BFF;

    private static int pressAction = -1;
    private static UUID pressTarget = new UUID(0, 0);

    private final List<Object[]> hits = new ArrayList<>();
    private Component hint;
    private int scroll;

    @Override
    public Component title() {
        return Component.translatable("ui.beyondsolo.party.tab");
    }

    @Override
    public boolean isVisible() {
        return ClientSystemData.get().hasSystem();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        hint = null;
        PartySyncPayload s = PartyClient.state();
        if (s.inParty()) members(g, s, x, y, LEFT_W, h, mouseX, mouseY);
        else alone(g, x, y, LEFT_W, h);
        side(g, s, x + LEFT_W + 6, y, w - LEFT_W - 6, h, mouseX, mouseY);
        toast(g, x, y, w, h);
        if (pressAction >= 0) {
            int a = pressAction;
            pressAction = -1;
            act(a, pressTarget);
        }
    }

    /** No party yet: what one is for. */
    private void alone(GuiGraphics g, float x, float y, float w, float h) {
        Ui.card(g, x, y, w, h, 9, PARTY, 0.1f);
        float cx = x + w / 2f, cy = y + 40;
        float spin = Anim.loop(22f);
        Shapes.circle(cx, cy, 24).fill(0).glow(14, Gfx.alpha(PARTY, 0.16f + 0.06f * Anim.pulse(3f))).draw(g);
        Shapes.ring(cx, cy, 22, 0.7f).fill(Gfx.alpha(PARTY, 0.5f)).dashes(18, 0.5f).rotation(spin).draw(g);
        Shapes.circle(cx, cy, 15).fillV(Gfx.alpha(PARTY, 0.25f), Gfx.alpha(SystemTheme.ACCENT_DEEP, 0.3f)).border(0.7f, Gfx.alpha(PARTY, 0.7f)).draw(g);
        Shapes.icon(Icons.VILLAGERS, cx, cy, 17).fill(SystemTheme.ACCENT_BRIGHT).glow(3, Gfx.alpha(PARTY, 0.5f)).draw(g);
        SysFont.spaced(g, Component.translatable("ui.beyondsolo.party.alone").getString(), cx, cy + 30, 8f, SystemTheme.TEXT, true, 2.2f, 0.5f);
        float ty = cy + 44;
        for (SysFont.Line l : SysFont.wrap(Component.translatable("ui.beyondsolo.party.alone_hint"), 5.6f, false, w - 30)) {
            SysFont.draw(g, l, cx, ty, SystemTheme.TEXT_SOFT, 0.5f);
            ty += 5.6f * SysFont.LINE;
        }
        // what a party does, as four little lines
        ty += 5;
        ResourceLocation[] icons = {Icons.SPIRAL, Icons.COINS, Icons.SHIELD, Icons.PATH};
        for (int i = 0; i < icons.length; i++) {
            float ly = ty + i * 9.5f;
            Shapes.icon(icons[i], x + 22, ly + 2.5f, 6.5f).fill(Gfx.mix(PARTY, 0xFFFFFFFF, 0.3f)).draw(g);
            String line = Component.translatable("ui.beyondsolo.party.perk." + i).getString();
            SysFont.text(g, SysFont.fit(line, 5.2f, false, w - 44), x + 30, ly, 5.2f, SystemTheme.TEXT_SOFT);
        }
    }

    /** The party: a row for each member. */
    private void members(GuiGraphics g, PartySyncPayload s, float x, float y, float w, float h, int mouseX, int mouseY) {
        Ui.card(g, x, y, w, h, 9, PARTY, 0.1f);
        boolean lead = PartyClient.leading();
        Shapes.icon(Icons.VILLAGERS, x + 14, y + 11, 11).fill(SystemTheme.ACCENT_BRIGHT).glow(2, Gfx.alpha(PARTY, 0.5f)).draw(g);
        SysFont.spaced(g, Component.translatable("ui.beyondsolo.party.title").getString(), x + 25, y + 5.5f, 7f, SystemTheme.TEXT, true, 1.8f, 0f);
        String count = s.members().size() + " / " + s.maxSize();
        SysFont.draw(g, count, x + w - 9, y + 6, 6.6f, SystemTheme.ACCENT_BRIGHT, true, 1f);
        String who = lead ? Component.translatable("ui.beyondsolo.party.you_lead").getString()
                : Component.translatable("ui.beyondsolo.party.led_by", leaderName(s)).getString();
        SysFont.text(g, SysFont.fit(who, 5f, false, w - 40), x + 25, y + 14.5f, 5f, lead ? SystemTheme.GOLD : SystemTheme.TEXT_DIM);
        Ui.divider(g, x + 8, y + 23, w - 16, Gfx.alpha(PARTY, 0.35f));

        float ry = y + 27;
        for (PartySyncPayload.Member m : s.members()) {
            row(g, s, m, x + 6, ry, w - 12, lead, mouseX, mouseY);
            ry += ROW_H + ROW_GAP;
        }
    }

    private static String leaderName(PartySyncPayload s) {
        for (PartySyncPayload.Member m : s.members()) if (m.id().equals(s.leader())) return m.name();
        return "?";
    }

    private void row(GuiGraphics g, PartySyncPayload s, PartySyncPayload.Member m, float x, float y, float w, boolean lead, int mouseX, int mouseY) {
        boolean me = PartyClient.isMe(m.id());
        boolean leader = m.id().equals(s.leader());
        int cc = m.cls() >= 0 ? SystemTheme.playerClass(PlayerClass.values()[m.cls()]) : SystemTheme.TEXT_SOFT;
        int tint = m.online() ? cc : SystemTheme.TEXT_DIM;
        Shapes.box(x, y, w, ROW_H).radius(6).fill(Gfx.alpha(tint, me ? 0.12f : 0.06f)).border(0.5f, Gfx.alpha(tint, me ? 0.55f : 0.3f)).draw(g);

        // the badge: their class, or a question mark before they are evaluated
        float bx = x + 11, by = y + ROW_H / 2f;
        Shapes.circle(bx, by, 7.5f).fillV(Gfx.alpha(tint, 0.3f), Gfx.alpha(Gfx.shade(tint, -0.5f), 0.4f)).border(0.6f, Gfx.alpha(tint, 0.9f)).draw(g);
        ResourceLocation icon = m.cls() >= 0 ? Icons.of(PlayerClass.values()[m.cls()]) : Icons.QUESTION;
        Shapes.icon(icon, bx, by, 9).fill(m.online() ? Gfx.mix(tint, 0xFFFFFFFF, 0.55f) : SystemTheme.TEXT_DIM).draw(g);

        // buttons on the right
        float btn = 13, bgap = 2;
        List<Object[]> buttons = new ArrayList<>();
        if (!me && m.online() && m.place() == PartySyncPayload.WORLD) buttons.add(new Object[]{PartyActionPayload.REGROUP, Icons.SPIRAL, REGROUP, "regroup"});
        if (!me && lead) {
            buttons.add(new Object[]{PartyActionPayload.PROMOTE, Icons.CROWN, SystemTheme.GOLD, "promote"});
            buttons.add(new Object[]{PartyActionPayload.KICK, Icons.X, SystemTheme.DANGER, "kick"});
        }
        float right = x + w - 4;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Object[] b = buttons.get(i);
            float bx0 = right - btn;
            int action = (int) b[0];
            boolean enabled = action != PartyActionPayload.REGROUP || PartyClient.regroupIn() == 0;
            iconButton(g, bx0, y + (ROW_H - btn) / 2f, btn, (ResourceLocation) b[1], (int) b[2], enabled, mouseX, mouseY, action, m.id(),
                    hintFor((String) b[3], m), enabled);
            right = bx0 - bgap;
        }

        // name, crown, you
        float tx = x + 22;
        float nameMax = right - tx - 34;
        String name = SysFont.fit(m.name(), 6.2f, true, nameMax);
        SysFont.text(g, name, tx, y + 3, 6.2f, m.online() ? SystemTheme.TEXT : SystemTheme.TEXT_DIM);
        float after = tx + SysFont.width(name, 6.2f, true) + 3;
        if (leader) {
            Shapes.icon(Icons.CROWN, after + 3, y + 5.5f, 6.5f).fill(SystemTheme.GOLD).glow(2, Gfx.alpha(SystemTheme.GOLD, 0.5f)).draw(g);
            after += 9;
        }
        if (me) Ui.chip(g, after, y + 2, 7, Component.translatable("ui.beyondsolo.party.you").getString(), 4.4f, PARTY, false);
        // rank and level, right of the name
        String lv = m.online() && m.system() ? Component.translatable("ui.beyondsolo.party.level", m.level()).getString() : "";
        if (m.online() && m.rank() >= 0) {
            PlayerRank rank = PlayerRank.values()[m.rank()];
            String r = rank.displayName().getString();
            SysFont.draw(g, r + (lv.isEmpty() ? "" : " · " + lv), right - 3, y + 3.5f, 5f, Gfx.mix(SystemTheme.rank(rank), 0xFFFFFFFF, 0.3f), true, 1f);
        } else if (!lv.isEmpty()) {
            SysFont.draw(g, lv, right - 3, y + 3.5f, 5f, SystemTheme.TEXT_SOFT, true, 1f);
        }

        // health, and where they are
        float barW = right - tx - 3;
        if (m.online()) {
            float hp = m.maxHealth() <= 0 ? 0 : Mth.clamp(m.health() / m.maxHealth(), 0, 1);
            boolean low = hp < 0.3f;
            Ui.bar(g, tx, y + 11, barW, 2.6f, hp, low ? SystemTheme.DANGER : SystemTheme.HP_A, low ? Gfx.mix(SystemTheme.HP_B, 0xFFFFFFFF, 0.4f * Anim.pulse(0.8f)) : SystemTheme.HP_B);
        }
        String where = SysFont.fit(where(m), 4.6f, false, barW);
        SysFont.text(g, where, tx, y + 14.8f, 4.6f, placeColor(m));
    }

    private Component hintFor(String what, PartySyncPayload.Member m) {
        if (what.equals("regroup")) {
            long wait = PartyClient.regroupIn();
            if (wait < 0) return Component.translatable("ui.beyondsolo.party.hint.regroup_off");
            if (wait > 0) return Component.translatable("ui.beyondsolo.party.hint.regroup_wait", clock(wait));
        }
        return Component.translatable("ui.beyondsolo.party.hint." + what, m.name());
    }

    /** "Overworld · 340 m NE", "In a D-Rank Gate · Ashen Hollow" (and how far, in the same cave), "Offline". */
    static String where(PartySyncPayload.Member m) {
        if (!m.online()) return Component.translatable("ui.beyondsolo.party.place.offline").getString();
        String line;
        if (m.place() != PartySyncPayload.WORLD) {
            String place = m.place() == PartySyncPayload.GATE && m.placeRank() >= 0
                    ? Component.translatable("ui.beyondsolo.party.place.gate_rank", PlayerRank.values()[m.placeRank()].displayName()).getString()
                    : Component.translatable("ui.beyondsolo.party.place." + placeKey(m.place())).getString();
            String name = m.placeName().getString();
            line = name.isEmpty() ? place : place + " · " + name;
        } else {
            line = dimName(m.dim().toString());
        }
        if (PartyClient.isMe(m.id()) || !PartyClient.samePlace(m)) return line;
        Minecraft mc = Minecraft.getInstance();
        double dx = m.x() - mc.player.getX(), dz = m.z() - mc.player.getZ();
        int d = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        if (d < 16) return line + " · " + Component.translatable("ui.beyondsolo.party.near").getString();
        return line + " · " + Component.translatable("ui.beyondsolo.party.away", d,
                Component.translatable("ui.beyondsolo.party.dir." + compass(dx, dz))).getString();
    }

    static int placeColor(PartySyncPayload.Member m) {
        if (!m.online()) return SystemTheme.TEXT_DIM;
        return switch (m.place()) {
            case PartySyncPayload.GATE -> m.placeRank() >= 0 ? Gfx.mix(SystemTheme.rank(PlayerRank.values()[m.placeRank()]), 0xFFFFFFFF, 0.35f) : SystemTheme.ACCENT_BRIGHT;
            case PartySyncPayload.RED_GATE, PartySyncPayload.PENALTY -> 0xFFFF7A7A;
            case PartySyncPayload.CITADEL -> 0xFFFF8A94;
            case PartySyncPayload.TRIAL, PartySyncPayload.JOB_TRIAL -> SystemTheme.GOLD;
            default -> SystemTheme.TEXT_SOFT;
        };
    }

    static String placeKey(int kind) {
        return switch (kind) {
            case PartySyncPayload.GATE -> "gate";
            case PartySyncPayload.RED_GATE -> "red_gate";
            case PartySyncPayload.CITADEL -> "citadel";
            case PartySyncPayload.PENALTY -> "penalty";
            case PartySyncPayload.TRIAL -> "trial";
            case PartySyncPayload.JOB_TRIAL -> "job_trial";
            case PartySyncPayload.OFFLINE -> "offline";
            default -> "world";
        };
    }

    static String dimName(String id) {
        return switch (id) {
            case "minecraft:overworld" -> Component.translatable("ui.beyondsolo.party.dim.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("ui.beyondsolo.party.dim.nether").getString();
            case "minecraft:the_end" -> Component.translatable("ui.beyondsolo.party.dim.end").getString();
            default -> id;
        };
    }

    /** n, ne, e, se, s, sw, w, nw: which way a point lies (north is -z). */
    static String compass(double dx, double dz) {
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        int i = (int) Math.floorMod(Math.round(a / 45.0), 8);
        return new String[] {"n", "ne", "e", "se", "s", "sw", "w", "nw"}[i];
    }

    static String clock(long ticks) {
        long sec = (ticks + 19) / 20;
        return sec / 60 + ":" + String.format(Locale.ROOT, "%02d", sec % 60);
    }

    /** The right side: invitations, hunters to invite, the regroup clock, leaving. */
    private void side(GuiGraphics g, PartySyncPayload s, float x, float y, float w, float h, int mouseX, int mouseY) {
        float cy = y;
        // invitations waiting
        if (!s.invites().isEmpty()) {
            int n = Math.min(2, s.invites().size());
            float ch = 15 + n * 17;
            Ui.card(g, x, cy, w, ch, 8, INVITE, 0.16f);
            float pulse = Anim.pulse(1.6f);
            Shapes.icon(Icons.INVITE, x + 10, cy + 7.5f, 7.5f).fill(Gfx.mix(INVITE, 0xFFFFFFFF, 0.3f)).glow(2 + 2 * pulse, Gfx.alpha(INVITE, 0.5f)).draw(g);
            SysFont.spaced(g, Component.translatable("ui.beyondsolo.party.invites").getString(), x + 18, cy + 4.5f, 5.6f, Gfx.mix(INVITE, 0xFFFFFFFF, 0.4f), true, 1.3f, 0f);
            for (int i = 0; i < n; i++) {
                PartySyncPayload.Invite inv = s.invites().get(s.invites().size() - 1 - i);
                float iy = cy + 14 + i * 17;
                Shapes.box(x + 4, iy, w - 8, 15).radius(5).fill(Gfx.alpha(INVITE, 0.07f)).border(0.5f, Gfx.alpha(INVITE, 0.3f)).draw(g);
                SysFont.text(g, SysFont.fit(inv.name(), 5.6f, true, w - 50), x + 8, iy + 2.2f, 5.6f, SystemTheme.TEXT);
                String sub = Component.translatable("ui.beyondsolo.party.invite_line", inv.size(), clock(PartyClient.ticksLeft(inv))).getString();
                SysFont.text(g, SysFont.fit(sub, 4.4f, false, w - 50), x + 8, iy + 9.2f, 4.4f, SystemTheme.TEXT_DIM);
                iconButton(g, x + w - 8 - 12 - 14, iy + 1.5f, 12, Icons.CHECK, INVITE, true, mouseX, mouseY, PartyActionPayload.ACCEPT, inv.from(),
                        Component.translatable("ui.beyondsolo.party.hint.accept", inv.name()), true);
                iconButton(g, x + w - 8 - 12, iy + 1.5f, 12, Icons.X, SystemTheme.DANGER, true, mouseX, mouseY, PartyActionPayload.DECLINE, inv.from(),
                        Component.translatable("ui.beyondsolo.party.hint.decline", inv.name()), true);
            }
            cy += ch + 5;
        }

        // the footer: regroup clock, leave and disband
        float footH = s.inParty() ? 31 : 12;
        float listH = h - (cy - y) - footH - 4;
        hunters(g, s, x, cy, w, listH, mouseX, mouseY);
        float fy = y + h - footH;
        long wait = PartyClient.regroupIn();
        String regroup = wait < 0 ? Component.translatable("ui.beyondsolo.party.regroup_off").getString()
                : wait == 0 ? Component.translatable("ui.beyondsolo.party.regroup_ready").getString()
                : Component.translatable("ui.beyondsolo.party.regroup_in", clock(wait)).getString();
        Shapes.icon(Icons.SPIRAL, x + 6, fy + 4, 6).fill(wait == 0 ? REGROUP : SystemTheme.TEXT_DIM).draw(g);
        SysFont.text(g, SysFont.fit(regroup, 5f, true, w - 14), x + 12, fy + 1.2f, 5f, wait == 0 ? Gfx.mix(REGROUP, 0xFFFFFFFF, 0.35f) : SystemTheme.TEXT_DIM);
        if (s.inParty()) {
            boolean lead = PartyClient.leading();
            float bw = lead ? (w - 4) / 2f : w;
            button(g, x, fy + 12, bw, 16, Component.translatable("ui.beyondsolo.party.leave").getString(), Icons.LEAVE, SystemTheme.DANGER,
                    mouseX, mouseY, PartyActionPayload.LEAVE, Component.translatable("ui.beyondsolo.party.hint.leave"));
            if (lead) button(g, x + bw + 4, fy + 12, bw, 16, Component.translatable("ui.beyondsolo.party.disband").getString(), Icons.X, 0xFFB03A48,
                    mouseX, mouseY, PartyActionPayload.DISBAND, Component.translatable("ui.beyondsolo.party.hint.disband"));
        }
    }

    /** The hunters online who are not in the party yet, with a button to invite each. */
    private void hunters(GuiGraphics g, PartySyncPayload s, float x, float y, float w, float h, int mouseX, int mouseY) {
        Ui.card(g, x, y, w, h, 8, PARTY, 0.07f);
        SysFont.spaced(g, Component.translatable("ui.beyondsolo.party.hunters").getString(), x + 8, y + 5, 5.6f, SystemTheme.ACCENT_BRIGHT, true, 1.3f, 0f);
        boolean canInvite = !s.inParty() || PartyClient.leading();
        boolean full = s.inParty() && s.members().size() >= s.maxSize();
        List<PlayerInfo> list = candidates(s);
        float ly = y + 15;
        if (!canInvite || full || list.isEmpty()) {
            String why = !canInvite ? Component.translatable("ui.beyondsolo.party.only_leader").getString()
                    : full ? Component.translatable("ui.beyondsolo.party.full").getString()
                    : Component.translatable("ui.beyondsolo.party.nobody").getString();
            float ty = ly + 2;
            for (SysFont.Line l : SysFont.wrap(Component.literal(why), 5f, false, w - 16)) {
                SysFont.draw(g, l, x + 8, ty, SystemTheme.TEXT_DIM, 0f);
                ty += 5f * SysFont.LINE;
            }
            return;
        }
        float rowH = 14;
        int fit = Math.max(1, (int) ((h - 17) / rowH));
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - fit));
        Minecraft mc = Minecraft.getInstance();
        for (int i = scroll; i < Math.min(list.size(), scroll + fit); i++) {
            PlayerInfo p = list.get(i);
            float ry = ly + (i - scroll) * rowH;
            boolean hover = Ui.inside(mouseX, mouseY, x + 4, ry, w - 8, rowH - 1);
            Shapes.box(x + 4, ry, w - 8, rowH - 1).radius(4).fill(Gfx.alpha(PARTY, hover ? 0.12f : 0.05f)).draw(g);
            String name = SysFont.fit(p.getProfile().getName(), 5.4f, true, w - 50);
            SysFont.text(g, name, x + 8, SysFont.centerY(ry, rowH - 1, 5.4f), 5.4f, SystemTheme.TEXT);
            Player near = mc.level == null ? null : mc.level.getPlayerByUUID(p.getProfile().getId());
            if (near != null && mc.player != null) {
                String d = Component.translatable("ui.beyondsolo.party.meters", Math.round(near.distanceTo(mc.player))).getString();
                SysFont.draw(g, d, x + w - 24, SysFont.centerY(ry, rowH - 1, 4.4f), 4.4f, SystemTheme.TEXT_DIM, false, 1f);
            }
            iconButton(g, x + w - 8 - 11, ry + 1, 11, Icons.INVITE, INVITE, true, mouseX, mouseY, PartyActionPayload.INVITE, p.getProfile().getId(),
                    Component.translatable("ui.beyondsolo.party.hint.invite", p.getProfile().getName()), true);
        }
        if (list.size() > fit) {
            String more = (scroll + fit) + " / " + list.size();
            SysFont.draw(g, more, x + w - 8, y + 5.5f, 4.4f, SystemTheme.TEXT_DIM, false, 1f);
        }
    }

    /** Everyone on the server's player list but the player and their party, by name. */
    private static List<PlayerInfo> candidates(PartySyncPayload s) {
        Minecraft mc = Minecraft.getInstance();
        List<PlayerInfo> out = new ArrayList<>();
        if (mc.getConnection() == null) return out;
        for (PlayerInfo p : mc.getConnection().getOnlinePlayers()) {
            UUID id = p.getProfile().getId();
            if (PartyClient.isMe(id)) continue;
            boolean member = false;
            for (PartySyncPayload.Member m : s.members()) if (m.id().equals(id)) member = true;
            if (!member) out.add(p);
        }
        out.sort(Comparator.comparing(p -> p.getProfile().getName().toLowerCase(Locale.ROOT)));
        return out;
    }

    private void iconButton(GuiGraphics g, float x, float y, float size, ResourceLocation icon, int color, boolean enabled, int mouseX, int mouseY,
                            int action, UUID target, Component tip, boolean clickable) {
        boolean hover = Ui.inside(mouseX, mouseY, x, y, size, size);
        int c = enabled ? color : SystemTheme.TEXT_DIM;
        Shapes.circle(x + size / 2f, y + size / 2f, size / 2f).fillV(Gfx.alpha(c, hover ? 0.4f : 0.18f), Gfx.alpha(Gfx.shade(c, -0.4f), hover ? 0.3f : 0.12f))
                .border(0.6f, Gfx.alpha(c, hover ? 1f : 0.6f)).glow(hover && enabled ? 4 : 0, Gfx.alpha(c, 0.4f)).draw(g);
        Shapes.icon(icon, x + size / 2f, y + size / 2f, size * 0.62f).fill(enabled ? Gfx.mix(c, 0xFFFFFFFF, hover ? 0.7f : 0.45f) : SystemTheme.TEXT_DIM).draw(g);
        if (hover) hint = tip;
        if (enabled && clickable) hits.add(new Object[]{x, y, size, size, action, target});
    }

    private void button(GuiGraphics g, float x, float y, float w, float h, String label, ResourceLocation icon, int color, int mouseX, int mouseY,
                        int action, Component tip) {
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, h);
        Shapes.pill(x, y, w, h).fillV(Gfx.alpha(color, hover ? 0.38f : 0.2f), Gfx.alpha(Gfx.shade(color, -0.4f), hover ? 0.28f : 0.14f))
                .border(0.8f, Gfx.alpha(Gfx.mix(color, 0xFFFFFFFF, 0.25f), hover ? 1f : 0.7f), Gfx.alpha(color, 0.3f))
                .glow(hover ? 5 : 2, Gfx.alpha(color, 0.3f)).draw(g);
        float size = 5.6f;
        String text = SysFont.fit(label, size, true, w - 20);
        float lw = SysFont.spacedWidth(text, size, true, 1f);
        float lx = x + (w - lw - 9) / 2f;
        Shapes.icon(icon, lx + 3, y + h / 2f, 6.5f).fill(SystemTheme.TEXT).draw(g);
        SysFont.spaced(g, text, lx + 9, SysFont.centerY(y, h, size), size, SystemTheme.TEXT, true, 1f, 0f);
        if (hover) hint = tip;
        hits.add(new Object[]{x, y, w, h, action, new UUID(0, 0)});
    }

    /** The last answer to a button, or what the button under the mouse does. */
    private void toast(GuiGraphics g, float x, float y, float w, float h) {
        float since = PartyClient.sinceOutcome();
        Component text = null;
        int color = SystemTheme.TEXT_SOFT;
        float k = 1;
        if (since < 4f && !PartyClient.outcome().getString().isEmpty()) {
            text = PartyClient.outcome();
            color = PartyClient.outcomeOk() ? SystemTheme.GOOD : SystemTheme.DANGER;
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
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] hit = hits.get(i);
            if (!Ui.inside(mouseX, mouseY, (float) hit[0], (float) hit[1], (float) hit[2], (float) hit[3])) continue;
            act((int) hit[4], (UUID) hit[5]);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        scroll -= (int) Math.signum(scrollY);
        return true;
    }

    private static void act(int action, UUID target) {
        PartyClient.send(action, target);
        SystemScreen.playClick();
        // (once a regroup starts the window gets out of the way: PartyClient; a refusal stays here, in the toast)
    }

    // ------------------------------------------------------------------ UI test driver

    /** Presses a button of the page on the next frame (driver: {@code partygo <action> [name]}). */
    public static void press(int action, UUID target) {
        pressAction = action;
        pressTarget = target;
    }
}
