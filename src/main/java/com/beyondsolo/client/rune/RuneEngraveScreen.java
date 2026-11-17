package com.beyondsolo.client.rune;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.skill.ClientSkills;
import com.beyondsolo.client.skill.SkillEmblem;
import com.beyondsolo.network.RuneActionPayload;
import com.beyondsolo.rune.Engraving;
import com.beyondsolo.rune.Runes;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.SkillActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The engraving screen: opened by using an engraving rune. Lists the active skills the player knows; pick one and
 * press CARVE, and the rune carves its engraving into that skill for good (replacing any engraving it had). Shows
 * the skill's numbers before and after. The server checks the choice again and answers with the carving's light.
 */
public class RuneEngraveScreen extends Screen {
    private static final float W = 300;
    private static final float H = 214;
    private static final float TILE = 32;
    private static final float GAP = 6;
    private static final int COLS = 7;
    private static final int ROWS = 2;

    private final InteractionHand hand;
    private final Engraving engraving;
    private final double openedAt;
    private final List<Skill> skills = new ArrayList<>();
    private String selected = "";
    private int scroll;
    private double sentAt = -100;
    private double engravedAt = -100;
    private double refusedAt = -100;
    private final List<Object[]> hits = new ArrayList<>();

    /** The open engraving screen, for the UI test driver. */
    private static RuneEngraveScreen open;

    public RuneEngraveScreen(InteractionHand hand, Engraving engraving) {
        super(Component.translatable("rune.beyondsolo.engrave.title"));
        this.hand = hand;
        this.engraving = engraving;
        this.openedAt = Anim.now();
        for (Skill s : ClientSkills.known()) if (s.isActive()) skills.add(s);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.7f));
        open = this;
    }

    static ResourceLocation icon(Engraving e) {
        return BeyondSolo.id("textures/gui/icon/engrave_" + e.id() + ".png");
    }

    // ------------------------------------------------------------------ the UI test driver

    /** Picks a skill on the open screen. */
    public static void select(String path) {
        if (open != null && Minecraft.getInstance().screen == open) open.selected = path;
    }

    /** Presses CARVE on the open screen. */
    public static void pressCarve() {
        if (open != null && Minecraft.getInstance().screen == open) open.carve();
    }

    // ------------------------------------------------------------------ server answers

    void engraved() {
        engravedAt = Anim.now();
    }

    void refused() {
        refusedAt = Anim.now();
        sentAt = -100;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBlurredBackground(partialTick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        hits.clear();
        float t = Anim.since(openedAt);
        int c = Gfx.opaque(engraving.color);
        int light = Gfx.mix(c, 0xFFFFFFFF, 0.5f);
        float in = Anim.outCubic(Anim.clamp01(t / 0.3f));
        float dim = Gfx.setAlpha(in);
        Shapes.vignette(0, 0, width, height, 0.1f, 1.4f).fill(0xC0040912).draw(g);
        Gfx.setAlpha(dim);

        float x = (width - W) / 2f;
        float y = (height - H) / 2f;
        float shake = Anim.since(refusedAt) < 0.35f ? Mth.sin(Anim.since(refusedAt) * 70) * 4 * (1 - Anim.since(refusedAt) / 0.35f) : 0;
        g.pose().pushPose();
        g.pose().translate(width / 2f + shake, height / 2f + (1 - in) * 10, 0);
        float s = 0.95f + 0.05f * in;
        g.pose().scale(s, s, 1);
        g.pose().translate(-width / 2f, -height / 2f, 0);
        float old = Gfx.setAlpha(in);

        Ui.panel(g, x, y, W, H, 12, c, 0.8f + 0.2f * Anim.pulse(2.6f));
        header(g, x, y, c, light, t);
        grid(g, x + 12, y + 70, mouseX, mouseY, c);
        preview(g, x + 12, y + H - 62, W - 24, c, light);
        buttons(g, x, y, mouseX, mouseY, c, light);

        Gfx.setAlpha(old);
        g.pose().popPose();
    }

    private void header(GuiGraphics g, float x, float y, int c, int light, float t) {
        float cx = x + 30, cy = y + 28;
        Shapes.circle(cx, cy, 16).fill(0).glow(14, Gfx.alpha(c, 0.35f + 0.15f * Anim.pulse(2f))).additive().draw(g);
        Shapes.circle(cx, cy, 15).fillV(Gfx.alpha(c, 0.32f), Gfx.alpha(c, 0.1f)).border(0.9f, Gfx.alpha(light, 0.9f), Gfx.alpha(c, 0.4f)).draw(g);
        Shapes.ring(cx, cy, 19, 0.6f).fill(Gfx.alpha(c, 0.55f)).dashes(14, 0.45f).rotation(Anim.loop(14f)).draw(g);
        Shapes.icon(icon(engraving), cx, cy, 17).fill(light).glow(3, Gfx.alpha(c, 0.9f)).draw(g);
        float tx = x + 54;
        SysFont.spaced(g, Component.translatable("rune.beyondsolo.engrave.title").getString(), tx, y + 12, 5.6f, Gfx.alpha(SystemTheme.TEXT_SOFT, 0.85f), true, 1.8f, 0f);
        SysFont.title(g, engraving.displayName().getString(), tx, y + 20, 12f, SystemTheme.TEXT);
        SysFont.text(g, engraving.effect().getString(), tx, y + 36, 7f, light);
        Ui.divider(g, x + 12, y + 50, W - 24, Gfx.alpha(c, 0.45f));
        SysFont.text(g, Component.translatable("rune.beyondsolo.engrave.hint").getString(), x + 12, y + 56, 6f, SystemTheme.TEXT_DIM);
    }

    private void grid(GuiGraphics g, float x, float y, int mouseX, int mouseY, int c) {
        if (skills.isEmpty()) {
            SysFont.draw(g, Component.translatable("rune.beyondsolo.engrave.none").getString(), x + (W - 24) / 2f, y + 26, 7f, SystemTheme.TEXT_SOFT, false, 0.5f);
            return;
        }
        int rows = (skills.size() + COLS - 1) / COLS;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows - ROWS));
        float gridW = COLS * TILE + (COLS - 1) * GAP;
        float ox = x + ((W - 24) - gridW) / 2f;
        for (int i = scroll * COLS; i < Math.min(skills.size(), (scroll + ROWS) * COLS); i++) {
            Skill s = skills.get(i);
            int col = i % COLS, row = i / COLS - scroll;
            float tx = ox + col * (TILE + GAP), ty = y + row * (TILE + GAP);
            boolean sel = s.path().equals(selected);
            boolean hover = Ui.inside(mouseX, mouseY, tx, ty, TILE, TILE);
            int sc = Gfx.opaque(s.color());
            float carved = sel ? Anim.clamp01(1 - Anim.since(engravedAt) / 0.9f) : 0;
            Ui.card(g, tx, ty, TILE, TILE, 7, sel ? c : sc, sel ? 0.6f : hover ? 0.3f : 0.12f);
            if (sel) {
                Shapes.box(tx, ty, TILE, TILE).radius(7).fill(0).border(1f, Gfx.alpha(c, 0.95f)).glow(5 + 6 * carved, Gfx.alpha(c, 0.4f + 0.5f * carved)).draw(g);
            }
            SkillEmblem.draw(g, s, tx + TILE / 2f, ty + TILE / 2f, TILE * 0.3f, sel ? 0.55f + carved : hover ? 0.3f : 0f, false);
            Engraving has = RuneClient.data().engraving(s.path());
            if (has != null) {
                int hc = 0xFF000000 | has.color;
                Shapes.icon(Icons.DIAMOND, tx + 5, ty + TILE - 5, 6).fill(hc).glow(2, Gfx.alpha(hc, 0.7f)).draw(g);
            }
            if (carved > 0) {
                float k = 1 - carved;
                Shapes.ring(tx + TILE / 2f, ty + TILE / 2f, TILE * 0.5f + 26 * Anim.outCubic(k), 1.6f * carved + 0.2f).fill(Gfx.alpha(0xFFFFFFFF, carved)).glow(4, Gfx.alpha(c, carved)).draw(g);
            }
            hits.add(new Object[]{"skill", tx, ty, TILE, TILE, s.path()});
        }
        if (rows > ROWS) {
            // a slim scroll bar on the right
            float bh = ROWS * TILE + (ROWS - 1) * GAP;
            float frac = (float) ROWS / rows;
            float pos = scroll / (float) Math.max(1, rows - ROWS);
            Shapes.pill(x + W - 28, y, 2, bh).fill(SystemTheme.TRACK).draw(g);
            Shapes.pill(x + W - 28, y + (bh - bh * frac) * pos, 2, bh * frac).fill(Gfx.alpha(c, 0.8f)).draw(g);
        }
    }

    private void preview(GuiGraphics g, float x, float y, float w, int c, int light) {
        Skill s = selectedSkill();
        Ui.card(g, x, y, w, 30, 7, c, s == null ? 0.05f : 0.18f);
        if (s == null) {
            SysFont.draw(g, Component.translatable("rune.beyondsolo.engrave.pick").getString(), x + w / 2f, y + 11.5f, 6.5f, SystemTheme.TEXT_DIM, false, 0.5f);
            return;
        }
        int level = Math.max(1, ClientSkills.data().level(s.path()));
        var runes = RuneClient.data();
        Engraving old = runes.engraving(s.path());
        SysFont.title(g, SysFont.fit(s.displayName().getString(), 8f, true, 110), x + 8, y + 5, 8f, SystemTheme.TEXT);
        SysFont.text(g, Component.translatable("skills.beyondsolo.level", level).getString(), x + 8, y + 17, 6f, SystemTheme.TEXT_SOFT);
        float sx = x + 122;
        String before, after;
        switch (engraving) {
            case HASTE -> {
                before = SkillActions.f(Runes.cooldown(runes, s, level) / 20f) + "s";
                after = SkillActions.f(Math.max(1, Math.round(s.cooldown(level) * engraving.cooldown)) / 20f) + "s";
                stat(g, sx, y + 9, Icons.HOURGLASS, before, after, light);
            }
            case THRIFT -> {
                before = SkillActions.f(Runes.manaCost(runes, s, level));
                after = SkillActions.f(s.manaCost(level) * engraving.mana);
                stat(g, sx, y + 9, Icons.DROP, before, after, light);
            }
            case MIGHT -> {
                before = old == Engraving.MIGHT ? "×" + SkillActions.f(Engraving.MIGHT.damage) : "×1";
                after = "×" + SkillActions.f(engraving.damage);
                stat(g, sx, y + 9, Icons.FLAME, before, after, light);
            }
        }
        if (old != null && old != engraving) {
            String warn = Component.translatable("rune.beyondsolo.engrave.replaces", old.displayName()).getString();
            SysFont.draw(g, warn, x + w - 8, y + 18.5f, 5.6f, SystemTheme.GOLD, false, 1f);
        } else if (old == engraving) {
            SysFont.draw(g, Component.translatable("rune.beyondsolo.engrave.same").getString(), x + w - 8, y + 18.5f, 5.6f, SystemTheme.DANGER, false, 1f);
        }
    }

    private static void stat(GuiGraphics g, float x, float y, ResourceLocation icon, String before, String after, int color) {
        Shapes.icon(icon, x + 4, y + 3.5f, 8).fill(color).draw(g);
        float bx = x + 11;
        SysFont.draw(g, before, bx, y, 7f, SystemTheme.TEXT_SOFT, true, 0f);
        float aw = SysFont.width(before, 7f, true);
        SysFont.draw(g, "›", bx + aw + 4, y, 7f, SystemTheme.TEXT_DIM, true, 0f);
        SysFont.draw(g, after, bx + aw + 11, y, 7f, color, true, 0f);
    }

    private void buttons(GuiGraphics g, float x, float y, int mouseX, int mouseY, int c, int light) {
        Skill s = selectedSkill();
        boolean done = Anim.since(engravedAt) < 2;
        boolean waiting = !done && Anim.since(sentAt) < 1.5f;
        boolean can = s != null && !done && !waiting && Runes.engraveRefusal(Minecraft.getInstance().player, s, engraving) == null;
        float by = y + H - 25;
        float bw = 86, bh = 15;
        float carveX = x + W - 12 - bw;
        boolean hoverCarve = Ui.inside(mouseX, mouseY, carveX, by, bw, bh);
        String label = Component.translatable(done ? "rune.beyondsolo.engrave.carved" : "rune.beyondsolo.engrave.carve").getString();
        Shapes.pill(carveX, by, bw, bh)
                .fillV(Gfx.alpha(c, can ? (hoverCarve ? 0.75f : 0.55f) : done ? 0.6f : 0.12f), Gfx.alpha(c, can ? 0.35f : done ? 0.35f : 0.06f))
                .border(0.9f, Gfx.alpha(light, can || done ? 0.95f : 0.3f)).glow(can && hoverCarve ? 7 : 0, Gfx.alpha(c, 0.5f)).draw(g);
        SysFont.spaced(g, label, carveX + bw / 2f, SysFont.centerY(by, bh, 6.5f), 6.5f, can || done ? SystemTheme.TEXT : SystemTheme.TEXT_DIM, true, 1.6f, 0.5f);
        hits.add(new Object[]{"carve", carveX, by, bw, bh, ""});

        float cancelX = carveX - 6 - 64;
        boolean hoverCancel = Ui.inside(mouseX, mouseY, cancelX, by, 64, bh);
        Shapes.pill(cancelX, by, 64, bh).fill(Gfx.alpha(SystemTheme.TEXT_DIM, hoverCancel ? 0.2f : 0.08f)).border(0.75f, Gfx.alpha(SystemTheme.TEXT_SOFT, 0.45f)).draw(g);
        SysFont.spaced(g, Component.translatable("rune.beyondsolo.engrave.cancel").getString(), cancelX + 32, SysFont.centerY(by, bh, 6.5f), 6.5f,
                SystemTheme.TEXT_SOFT, true, 1.4f, 0.5f);
        hits.add(new Object[]{"cancel", cancelX, by, 64f, bh, ""});

        // how many of this rune are in the hand
        int count = Minecraft.getInstance().player.getItemInHand(hand).getCount();
        SysFont.text(g, Component.translatable("rune.beyondsolo.engrave.held", count).getString(), x + 14, by + 4, 6f, SystemTheme.TEXT_DIM);
    }

    private Skill selectedSkill() {
        for (Skill s : skills) if (s.path().equals(selected)) return s;
        return null;
    }

    // ------------------------------------------------------------------ input

    private void carve() {
        Skill s = selectedSkill();
        if (s == null || Anim.since(sentAt) < 1.5f || Anim.since(engravedAt) < 2) return;
        if (Runes.engraveRefusal(Minecraft.getInstance().player, s, engraving) != null) {
            refused();
            return;
        }
        sentAt = Anim.now();
        PacketDistributor.sendToServer(new RuneActionPayload(hand == InteractionHand.OFF_HAND ? 1 : 0, s.path()));
        SystemScreen.playClick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Object[] h : hits) {
            if (!Ui.inside(mouseX, mouseY, (float) h[1], (float) h[2], (float) h[3], (float) h[4])) continue;
            switch ((String) h[0]) {
                case "skill" -> {
                    if (!h[5].equals(selected) && Anim.since(engravedAt) >= 2) {
                        selected = (String) h[5];
                        SystemScreen.playClick();
                    }
                }
                case "carve" -> carve();
                case "cancel" -> onClose();
                default -> {
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            carve();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        // the carving took: let its light play, then close
        if (engravedAt > 0 && Anim.since(engravedAt) > 1.0f) onClose();
        if (!ClientSystemData.get().isAwakened()) onClose();
    }

    @Override
    public void onClose() {
        if (open == this) open = null;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
