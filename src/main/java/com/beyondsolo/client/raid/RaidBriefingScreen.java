package com.beyondsolo.client.raid;

import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.network.RaidActionPayload;
import com.beyondsolo.raid.RaidRole;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The raid briefing, shown when a hunter walks into a raid gate before it starts: what waits inside (the boss, the
 * lieutenants sealing its chamber, the barrier and the cataclysm), what it pays, who has mustered and in which role.
 * Each picks their role here; the leader decides whether their guild's hunters come and starts the countdown. It
 * closes on its own when the raid goes through.
 */
public class RaidBriefingScreen extends Screen {
    private static final int W = 344, H = 214;
    private static final int RAID = 0xFFB98BFF;
    private static final int GOLD = 0xFFFFC247;

    private record Hit(float x, float y, float w, float h, Runnable run) {}

    private final List<Hit> hits = new ArrayList<>();
    private Component hint;
    private final double openedAt = Anim.now();

    public RaidBriefingScreen() {
        super(Component.translatable("ui.beyondsolo.raid.brief.title"));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        // through the gate, or off the roster: nothing left to brief
        if (!RaidClient.mustering()) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        hits.clear();
        hint = null;
        float open = Anim.outCubic(Anim.progress(openedAt, 0.25f));
        float old = Gfx.setAlpha(open);
        Shapes.vignette(0, 0, width, height, 0.15f, 1.5f).fill(0xC8040912).draw(g);
        float x = (width - W) / 2f, y = (height - H) / 2f + (1 - open) * 8;
        CompoundTag t = RaidClient.data();
        PlayerRank rank = PlayerRank.values()[Mth.clamp(t.getInt("rank"), 0, PlayerRank.values().length - 1)];
        Ui.panel(g, x, y, W, H, 11, RAID, 1f);

        // header: the rank, the title, the cave's name, and the countdown or who leads
        Ui.rankEmblem(g, rank, x + 20, y + 17, 9, 0.25f + 0.15f * Anim.pulse(2.4f));
        SysFont.spaced(g, Component.translatable("ui.beyondsolo.raid.brief.header", rank.displayName()).getString(), x + 35, y + 7, 9f,
                SystemTheme.TEXT, true, 2.2f, 0f);
        SysFont.text(g, SysFont.fit(RaidClient.dungeon().getString(), 5.4f, false, 180), x + 35, y + 19, 5.4f, SystemTheme.TEXT_SOFT);
        long countdown = RaidClient.ticks("countdown");
        if (countdown >= 0) {
            float pulse = Anim.pulse(0.5f);
            String c = Component.translatable("ui.beyondsolo.raid.brief.countdown", (countdown + 19) / 20).getString();
            float cw = SysFont.width(c, 6.4f, true) + 16;
            Shapes.pill(x + W - 12 - cw, y + 9, cw, 15).fill(Gfx.alpha(GOLD, 0.25f + 0.15f * pulse)).border(0.8f, GOLD).glow(4 + 3 * pulse, Gfx.alpha(GOLD, 0.4f)).draw(g);
            SysFont.draw(g, c, x + W - 12 - cw / 2f, SysFont.centerY(y + 9, 15, 6.4f), 6.4f, SystemTheme.TEXT, true, 0.5f);
        } else {
            String lead = RaidClient.leading() ? Component.translatable("ui.beyondsolo.raid.brief.you_lead").getString()
                    : Component.translatable("ui.beyondsolo.raid.brief.led_by", t.getString("leaderName")).getString();
            SysFont.draw(g, SysFont.fit(lead, 5.4f, true, 120), x + W - 14, y + 13, 5.4f, RaidClient.leading() ? SystemTheme.GOLD : SystemTheme.TEXT_SOFT, true, 1f);
        }
        Ui.divider(g, x + 12, y + 32, W - 24, Gfx.alpha(RAID, 0.5f));

        float by = y + 38, bh = H - 38 - 50;
        threat(g, t, x + 12, by, 186, bh);
        roster(g, t, x + 204, by, W - 216, bh, mouseX, mouseY);
        controls(g, t, x + 12, y + H - 46, W - 24, mouseX, mouseY);
        toast(g, x, y + H + 3, W);
        Gfx.setAlpha(old);
    }

    /** What waits inside, and what it pays. */
    private void threat(GuiGraphics g, CompoundTag t, float x, float y, float w, float h) {
        Ui.card(g, x, y, w, h, 8, 0xFFFF5D6C, 0.08f);
        Shapes.icon(Icons.SKULL, x + 10, y + 8, 8).fill(0xFFFF8A94).draw(g);
        SysFont.spaced(g, Component.translatable("ui.beyondsolo.raid.brief.boss").getString(), x + 18, y + 4.5f, 5.2f, 0xFFFFB0B8, true, 1.3f, 0f);
        SysFont.text(g, SysFont.fit(RaidClient.boss().getString(), 6.6f, true, w - 16), x + 8, y + 13, 6.6f, SystemTheme.TEXT);
        String[] keys = {"brief.lieutenants", "brief.barrier", "brief.cataclysm", "brief.grown"};
        ResourceLocation[] icons = {Icons.LOCK, Icons.HEXAGON, Icons.EXCLAIM, Icons.VILLAGERS};
        int[] colors = {RAID, 0xFFB98BFF, 0xFFFF5D6C, SystemTheme.ACCENT};
        int raiders = t.getList("raiders", Tag.TAG_COMPOUND).size(), hunters = t.getList("hunters", Tag.TAG_COMPOUND).size();
        Object[][] args = {{t.getInt("lieutenants")}, {}, {}, {raiders, hunters}};
        float ly = y + 23;
        for (int i = 0; i < keys.length; i++) {
            Shapes.icon(icons[i], x + 11, ly + 3, 6).fill(Gfx.mix(colors[i], 0xFFFFFFFF, 0.3f)).draw(g);
            float ty = ly;
            for (SysFont.Line l : SysFont.wrap(Component.translatable("ui.beyondsolo.raid." + keys[i], args[i]), 4.7f, false, w - 26)) {
                SysFont.draw(g, l, x + 18, ty, SystemTheme.TEXT_SOFT, 0f);
                ty += 4.7f * SysFont.LINE;
            }
            ly = Math.max(ly + 9, ty + 2.5f);
        }
        // the spoils
        float sy = y + h - 21;
        Ui.divider(g, x + 8, sy - 3, w - 16, Gfx.alpha(GOLD, 0.3f));
        Shapes.icon(Icons.PACKAGE, x + 11, sy + 4, 6.5f).fill(GOLD).draw(g);
        SysFont.spaced(g, Component.translatable("ui.beyondsolo.raid.brief.spoils").getString(), x + 18, sy, 4.8f, GOLD, true, 1.2f, 0f);
        String pay = Component.translatable("ui.beyondsolo.raid.brief.spoils_line", scale(t.getFloat("reward")),
                NumberFormat.getIntegerInstance(Locale.ROOT).format(t.getLong("guildXp"))).getString();
        SysFont.text(g, SysFont.fit(pay, 4.5f, false, w - 26), x + 18, sy + 8, 4.5f, SystemTheme.TEXT_SOFT);
    }

    private static String scale(float s) {
        return Math.abs(s - Math.round(s)) < 0.05f ? Integer.toString(Math.round(s)) : String.format(Locale.ROOT, "%.1f", s);
    }

    /** Who has mustered: the raiders (their role, class and level) and the guild's hunters who would come. */
    private void roster(GuiGraphics g, CompoundTag t, float x, float y, float w, float h, int mouseX, int mouseY) {
        Ui.card(g, x, y, w, h, 8, RAID, 0.08f);
        Shapes.icon(Icons.VILLAGERS, x + 10, y + 8, 8).fill(Gfx.mix(RAID, 0xFFFFFFFF, 0.4f)).draw(g);
        SysFont.spaced(g, Component.translatable("ui.beyondsolo.raid.brief.roster").getString(), x + 18, y + 4.5f, 5.2f, SystemTheme.TEXT, true, 1.3f, 0f);
        ListTag raiders = t.getList("raiders", Tag.TAG_COMPOUND), hunters = t.getList("hunters", Tag.TAG_COMPOUND);
        SysFont.draw(g, raiders.size() + " / " + t.getInt("maxSize"), x + w - 8, y + 5, 5.4f, Gfx.mix(RAID, 0xFFFFFFFF, 0.4f), true, 1f);
        float ry = y + 15, rowH = 12;
        int room = (int) ((h - 17) / rowH);
        int shown = 0;
        java.util.UUID leader = t.hasUUID("leader") ? t.getUUID("leader") : null;
        for (int i = 0; i < raiders.size() && shown < room; i++, shown++) {
            CompoundTag r = raiders.getCompound(i);
            boolean lead = r.getUUID("id").equals(leader);
            boolean me = r.getUUID("id").equals(RaidClient.me());
            row(g, x + 4, ry, w - 8, rowH - 1, r.getString("name"), r.getInt("cls"), r.getInt("level"), RaidRole.byId(r.getString("role")), lead, me, false);
            ry += rowH;
        }
        if (hunters.size() > 0 && shown < room) {
            SysFont.spaced(g, Component.translatable("ui.beyondsolo.raid.brief.hunters", hunters.size()).getString(), x + 8, ry + 1.5f, 4.4f,
                    Gfx.alpha(GOLD, 0.85f), true, 1.1f, 0f);
            ry += 9;
            shown++;
        }
        for (int i = 0; i < hunters.size() && shown < room; i++, shown++) {
            CompoundTag r = hunters.getCompound(i);
            row(g, x + 4, ry, w - 8, rowH - 1, r.getString("name"), r.getInt("cls"), r.getInt("level"), RaidRole.byId(r.getString("role")), false, false, true);
            ry += rowH;
        }
        int left = raiders.size() + hunters.size() + (hunters.isEmpty() ? 0 : 1) - shown;
        if (left > 0) SysFont.draw(g, Component.translatable("ui.beyondsolo.raid.hud.more", left).getString(), x + w - 8, y + h - 7, 4.4f, SystemTheme.TEXT_DIM, false, 1f);
        if (raiders.size() + hunters.size() <= 1 && room > 3) {
            float ty = ry + 4;
            for (SysFont.Line l : SysFont.wrap(Component.translatable("ui.beyondsolo.raid.brief.alone"), 4.5f, false, w - 16)) {
                SysFont.draw(g, l, x + 8, ty, SystemTheme.TEXT_DIM, 0f);
                ty += 4.5f * SysFont.LINE;
            }
        }
    }

    private void row(GuiGraphics g, float x, float y, float w, float h, String name, int cls, int level, RaidRole role, boolean leader, boolean me, boolean hunter) {
        RaidRole r = role == null ? RaidRole.STRIKER : role;
        int cc = cls >= 0 && cls < PlayerClass.values().length ? SystemTheme.playerClass(PlayerClass.values()[cls]) : SystemTheme.TEXT_SOFT;
        Shapes.box(x, y, w, h).radius(4).fill(Gfx.alpha(cc, me ? 0.14f : 0.05f)).border(0.4f, Gfx.alpha(me ? SystemTheme.ACCENT : cc, me ? 0.6f : 0.2f)).draw(g);
        Shapes.box(x + 1, y + 1.5f, 1.4f, h - 3).radius(0.7f).fill(r.color).draw(g);
        ResourceLocation icon = cls >= 0 && cls < PlayerClass.values().length ? Icons.of(PlayerClass.values()[cls]) : Icons.QUESTION;
        Shapes.icon(icon, x + 8, y + h / 2f, 6.5f).fill(Gfx.mix(cc, 0xFFFFFFFF, 0.5f)).draw(g);
        String roleName = r.displayName().getString();
        float rw = SysFont.width(roleName, 4f, true) + 6;
        String lv = level > 0 ? Component.translatable("ui.beyondsolo.party.level", level).getString() : "";
        float lvw = SysFont.width(lv, 4.2f, false);
        String nm = SysFont.fit(name, 4.8f, true, w - 18 - rw - lvw - 6 - (leader ? 7 : 0));
        SysFont.text(g, nm, x + 14, SysFont.centerY(y, h, 4.8f), 4.8f, hunter ? SystemTheme.TEXT_SOFT : SystemTheme.TEXT);
        if (leader) Shapes.icon(Icons.CROWN, x + 14 + SysFont.width(nm, 4.8f, true) + 4, y + h / 2f, 5f).fill(SystemTheme.GOLD).draw(g);
        Ui.chip(g, x + w - rw - 2, y + (h - 6.4f) / 2f, 6.4f, roleName, 4f, r.color, false);
        if (!lv.isEmpty()) SysFont.draw(g, lv, x + w - rw - 5, SysFont.centerY(y, h, 4.2f), 4.2f, SystemTheme.TEXT_DIM, false, 1f);
    }

    /** The role picker, then the leader's switches, starting, leaving. */
    private void controls(GuiGraphics g, CompoundTag t, float x, float y, float w, int mouseX, int mouseY) {
        RaidRole mine = RaidClient.role();
        SysFont.spaced(g, Component.translatable("ui.beyondsolo.raid.brief.your_role").getString(), x, y, 4.8f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        float rw = (w - 8 - 130) / 3f;
        ResourceLocation[] icons = {Icons.SHIELD, Icons.FLAME, Icons.HEART};
        for (RaidRole role : RaidRole.values()) {
            float bx = x + role.ordinal() * (rw + 4), by = y + 7;
            boolean sel = role == mine;
            boolean hover = Ui.inside(mouseX, mouseY, bx, by, rw, 30);
            int c = role.color;
            Shapes.box(bx, by, rw, 30).radius(7).fillV(Gfx.alpha(c, sel ? 0.32f : hover ? 0.18f : 0.08f), Gfx.alpha(Gfx.shade(c, -0.4f), sel ? 0.25f : 0.08f))
                    .border(sel ? 1f : 0.6f, Gfx.alpha(c, sel ? 1f : 0.45f)).glow(sel ? 5 : 0, Gfx.alpha(c, 0.35f)).draw(g);
            Shapes.icon(icons[role.ordinal()], bx + 9, by + 8, 8).fill(Gfx.mix(c, 0xFFFFFFFF, sel ? 0.6f : 0.3f)).draw(g);
            SysFont.text(g, SysFont.fit(role.displayName().getString(), 5.4f, true, rw - 20), bx + 17, by + 5, 5.4f, sel ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT);
            float ty = by + 15;
            for (SysFont.Line l : SysFont.wrap(Component.translatable("ui.beyondsolo.raid.role." + role.id() + ".short"), 3.9f, false, rw - 8)) {
                SysFont.draw(g, l, bx + 4, ty, SystemTheme.TEXT_DIM, 0f);
                ty += 3.9f * SysFont.LINE;
            }
            if (hover) hint = Component.translatable("ui.beyondsolo.raid.role." + role.id() + ".desc");
            if (!sel) {
                RaidRole pick = role;
                hits.add(new Hit(bx, by, rw, 30, () -> {
                    CompoundTag a = new CompoundTag();
                    a.putString("role", pick.id());
                    RaidClient.send(RaidActionPayload.ROLE, a);
                }));
            }
        }
        // the leader's side
        float cx = x + w - 126, cw = 126;
        boolean lead = RaidClient.leading();
        boolean counting = RaidClient.ticks("countdown") >= 0;
        if (lead) {
            boolean bring = t.getBoolean("bring");
            int guildHunters = t.getInt("guildHunters");
            String label = Component.translatable(bring ? "ui.beyondsolo.raid.brief.bring_on" : "ui.beyondsolo.raid.brief.bring_off", guildHunters).getString();
            boolean any = guildHunters > 0;
            toggle(g, cx, y + 1, cw, 11, label, bring && any, any, mouseX, mouseY,
                    Component.translatable(any ? "ui.beyondsolo.raid.brief.hint.bring" : "ui.beyondsolo.raid.brief.hint.no_hunters"),
                    any ? () -> {
                        CompoundTag a = new CompoundTag();
                        a.putBoolean("on", !bring);
                        RaidClient.send(RaidActionPayload.BRING, a);
                    } : null);
            button(g, cx, y + 14, cw - 40, 17, Component.translatable(counting ? "ui.beyondsolo.raid.brief.starting" : "ui.beyondsolo.raid.brief.start").getString(),
                    Icons.FLAG, counting ? SystemTheme.TEXT_DIM : GOLD, mouseX, mouseY, Component.translatable("ui.beyondsolo.raid.brief.hint.start"),
                    counting ? null : () -> RaidClient.send(RaidActionPayload.START));
        } else {
            String wait = counting ? Component.translatable("ui.beyondsolo.raid.brief.starting").getString()
                    : Component.translatable("ui.beyondsolo.raid.brief.waiting", t.getString("leaderName")).getString();
            float ty = y + 4;
            for (SysFont.Line l : SysFont.wrap(Component.literal(wait), 4.8f, false, cw - 44)) {
                SysFont.draw(g, l, cx, ty, SystemTheme.TEXT_SOFT, 0f);
                ty += 4.8f * SysFont.LINE;
            }
        }
        button(g, cx + cw - 37, y + 14, 37, 17, Component.translatable("ui.beyondsolo.raid.brief.leave").getString(), Icons.LEAVE, SystemTheme.DANGER,
                mouseX, mouseY, Component.translatable("ui.beyondsolo.raid.brief.hint.leave"), () -> RaidClient.send(RaidActionPayload.LEAVE));
    }

    private void toggle(GuiGraphics g, float x, float y, float w, float h, String label, boolean on, boolean enabled, int mouseX, int mouseY, Component tip, Runnable run) {
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, h);
        int c = on ? GOLD : SystemTheme.TEXT_DIM;
        Shapes.pill(x, y, w, h).fill(Gfx.alpha(c, hover && enabled ? 0.22f : 0.1f)).border(0.6f, Gfx.alpha(c, 0.7f)).draw(g);
        float kx = x + 4, ky = y + h / 2f;
        Shapes.pill(kx, ky - 2.5f, 10, 5).fill(Gfx.alpha(on ? GOLD : 0xFF3A4556, 0.8f)).draw(g);
        Shapes.circle(kx + (on ? 7.5f : 2.5f), ky, 2.4f).fill(0xFFF4F7FF).draw(g);
        SysFont.text(g, SysFont.fit(label, 4.6f, false, w - 20), x + 17, SysFont.centerY(y, h, 4.6f), 4.6f, enabled ? SystemTheme.TEXT : SystemTheme.TEXT_DIM);
        if (hover) hint = tip;
        if (enabled && run != null) hits.add(new Hit(x, y, w, h, run));
    }

    private void button(GuiGraphics g, float x, float y, float w, float h, String label, ResourceLocation icon, int color, int mouseX, int mouseY,
                        Component tip, Runnable run) {
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, h);
        boolean enabled = run != null;
        Shapes.pill(x, y, w, h).fillV(Gfx.alpha(color, hover && enabled ? 0.4f : 0.22f), Gfx.alpha(Gfx.shade(color, -0.4f), hover && enabled ? 0.3f : 0.14f))
                .border(0.8f, Gfx.alpha(Gfx.mix(color, 0xFFFFFFFF, 0.25f), hover ? 1f : 0.7f), Gfx.alpha(color, 0.3f))
                .glow(hover && enabled ? 6 : enabled ? 3 : 0, Gfx.alpha(color, 0.35f)).draw(g);
        float size = 5.6f;
        String text = SysFont.fit(label, size, true, w - 16);
        float lw = SysFont.spacedWidth(text, size, true, 1f);
        float lx = x + (w - lw - 9) / 2f;
        int tc = enabled ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT;
        Shapes.icon(icon, lx + 3, y + h / 2f, 6.5f).fill(tc).draw(g);
        SysFont.spaced(g, text, lx + 9, SysFont.centerY(y, h, size), size, tc, true, 1f, 0f);
        if (hover) hint = tip;
        if (enabled) hits.add(new Hit(x, y, w, h, run));
    }

    /** The last answer to a button, or what the thing under the mouse does. */
    private void toast(GuiGraphics g, float x, float y, float w) {
        float since = RaidClient.sinceOutcome();
        Component text = null;
        int color = SystemTheme.TEXT_SOFT;
        float k = 1;
        if (since < 4f && !RaidClient.outcome().getString().isEmpty()) {
            text = RaidClient.outcome();
            color = RaidClient.outcomeOk() ? SystemTheme.GOOD : SystemTheme.DANGER;
            k = 1 - Anim.window(since, 3.4f, 4f);
        } else if (hint != null) {
            text = hint;
        }
        if (text == null || k <= 0.01f) return;
        String line = SysFont.fit(text.getString(), 5.4f, false, w - 30);
        float lw = SysFont.width(line, 5.4f, false) + 16;
        float tx = x + (w - lw) / 2f;
        float old = Gfx.fade(k);
        Shapes.pill(tx, y, lw, 11).fill(Gfx.alpha(0xFF060A14, 0.9f)).border(0.6f, Gfx.alpha(color, 0.7f)).glow(4, Gfx.alpha(color, 0.25f)).draw(g);
        SysFont.draw(g, line, tx + lw / 2f, SysFont.centerY(y, 11, 5.4f), 5.4f, Gfx.mix(color, 0xFFFFFFFF, 0.3f), false, 0.5f);
        Gfx.setAlpha(old);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!Ui.inside(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            hit.run().run();
            SystemScreen.playClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 0.4f));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
