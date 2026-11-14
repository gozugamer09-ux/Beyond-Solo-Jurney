package com.beyondsolo.client.skill;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.evaluation.EvaluationOverlay;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.client.quest.QuestOverlay;
import com.beyondsolo.network.SkillMomentPayload;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.skill.Skill;
import com.beyondsolo.skill.Skills;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Skill moments on screen.
 * <ul>
 *   <li><b>Skill acquired</b>: the screen dims, the skill's emblem bursts in with rays and shock rings, a
 *   "[ SKILL ACQUIRED ]" header draws its lines out, the name types itself, then the details and the key it went to.
 *   Waits for the evaluation, quest and gate moments, holds new pop-ups while it plays, and the hide key skips it.</li>
 *   <li><b>Skill level up</b>: a banner drops in at the top with the new level ticking over.</li>
 *   <li><b>Refused</b>: the slot shakes and a short line says why.</li>
 * </ul>
 */
public final class SkillOverlay {
    private record Learned(Skill skill, int level) {}
    private record Banner(Skill skill, int level) {}

    private static final Deque<Learned> LEARNED = new ArrayDeque<>();
    private static final Deque<Banner> BANNERS = new ArrayDeque<>();
    /** How long a Skill Acquired moment plays; shorter when more are waiting behind it. */
    private static float learnTime = 4.0f;
    private static final float BANNER_TIME = 2.6f;
    private static final float EXIT = 0.5f;

    private static Learned current;
    private static double startedAt;
    private static Banner banner;
    private static double bannerAt;

    public static void handle(SkillMomentPayload p) {
        Skill s = Skills.get(p.skill());
        if (s == null) return;
        switch (p.moment()) {
            case SkillMomentPayload.LEARNED -> {
                LEARNED.addLast(new Learned(s, p.level()));
                NotificationManager.log(SystemNotification.of(SystemNotification.Kind.REWARD,
                        Component.translatable("skills.beyondsolo.acquired"), s.displayName()));
            }
            case SkillMomentPayload.LEVEL_UP -> {
                BANNERS.addLast(new Banner(s, p.level()));
                NotificationManager.log(SystemNotification.of(SystemNotification.Kind.LEVEL_UP,
                        Component.translatable("skills.beyondsolo.level_up"),
                        Component.translatable("skills.beyondsolo.level_up.body", s.displayName(), p.level())));
            }
            case SkillMomentPayload.REFUSED -> refuse(p.skill(), p.level());
            default -> {}
        }
    }

    public static void refuse(String path, int reason) {
        SkillBar.refused(path);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String key = switch (reason) {
            case SkillMomentPayload.NO_MANA -> "skills.beyondsolo.refused.mana";
            case SkillMomentPayload.COOLDOWN -> "skills.beyondsolo.refused.cooldown";
            case SkillMomentPayload.NO_TARGET -> "skills.beyondsolo.refused.target";
            default -> "skills.beyondsolo.refused.blocked";
        };
        mc.player.displayClientMessage(Component.translatable(key), true);
        if (reason == SkillMomentPayload.NO_MANA || reason == SkillMomentPayload.NO_TARGET) sound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.5f);
    }

    public static boolean active() {
        return current != null;
    }

    public static void frame() {
        if (current != null && Anim.since(startedAt) >= learnTime) {
            current = null;
            NotificationManager.setHeld(false);
        }
        if (current == null && !LEARNED.isEmpty() && !EvaluationOverlay.active() && !QuestOverlay.active()
                && !com.beyondsolo.client.gate.GateOverlay.active()) {
            current = LEARNED.pollFirst();
            startedAt = Anim.now();
            learnTime = LEARNED.isEmpty() ? 4.0f : 2.7f;
            NotificationManager.setHeld(true);
            sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.7f);
            sound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.2f, 0.6f);
        }
        if (banner != null && Anim.since(bannerAt) >= BANNER_TIME) banner = null;
        if (banner == null && !BANNERS.isEmpty() && current == null && !EvaluationOverlay.active()) {
            banner = BANNERS.pollFirst();
            bannerAt = Anim.now();
            sound(SoundEvents.PLAYER_LEVELUP, 1.6f, 0.35f);
        }
    }

    /** The hide key: jumps to the end of the acquired moment. */
    public static boolean skip() {
        if (current == null) return false;
        float t = Anim.since(startedAt);
        float end = learnTime - EXIT;
        if (t < end) startedAt -= end - t;
        return true;
    }

    public static void reset() {
        LEARNED.clear();
        BANNERS.clear();
        if (current != null) NotificationManager.setHeld(false);
        current = null;
        banner = null;
    }

    private static void sound(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ------------------------------------------------------------------ drawing

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (banner != null) drawBanner(g, banner, Anim.since(bannerAt));
        if (current != null) drawLearned(g, current, Anim.since(startedAt));
    }

    private static void drawLearned(GuiGraphics g, Learned m, float t) {
        Skill s = m.skill;
        int color = Gfx.opaque(s.color());
        int light = Gfx.mix(color, 0xFFFFFFFF, 0.55f);
        float w = g.guiWidth();
        float h = g.guiHeight();
        float cx = w / 2f;
        float cy = h / 2f - 22;
        float in = Anim.clamp01(t / 0.35f);
        float out = Anim.clamp01((t - (learnTime - EXIT)) / EXIT);
        float live = in * (1 - out);
        float old = Gfx.fade(live);

        // dim the world, tint the edges
        Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFF02060E, 0.45f)).draw(g);
        float flash = 1 - Anim.clamp01((t - 0.25f) / 0.6f);
        Shapes.vignette(0, 0, w, h, 0.2f, 1.35f).fill(Gfx.alpha(color, 0.18f + 0.35f * flash)).draw(g);

        // emblem bursts in
        float pop = Anim.outBackStrong(Anim.window(t, 0.12f, 0.62f));
        float rays = Anim.window(t, 0.2f, 0.7f) * (1 - out);
        if (rays > 0) {
            Shapes.rays(cx, cy, 140, 16).fillV(Gfx.alpha(light, 0.3f * rays), Gfx.alpha(color, 0.12f * rays)).rotation(t * 0.03f).additive().draw(g);
        }
        for (int i = 0; i < 2; i++) {
            float k = Anim.window(t, 0.28f + i * 0.14f, 1.1f + i * 0.14f);
            if (k > 0 && k < 1) Shapes.ring(cx, cy, 20 + 90 * Anim.outCubic(k), 2.2f * (1 - k)).fill(Gfx.alpha(light, 1 - k)).draw(g);
        }
        float r = 22 * pop * (1 + 0.08f * out);
        if (r > 0.5f) {
            Shapes.circle(cx, cy, r * 1.9f).fill(0x00000000).glow(r * 1.1f, Gfx.alpha(color, 0.35f + 0.4f * flash)).additive().draw(g);
            SkillEmblem.draw(g, s, cx, cy, r, 0.6f + 0.4f * flash, false);
        }

        // header with lines drawing out
        float head = Anim.outCubic(Anim.window(t, 0.45f, 0.85f));
        float hy = cy - 50 - (1 - head) * 8;
        String header = Component.translatable("skills.beyondsolo.acquired.header").getString();
        float oldH = Gfx.fade(head);
        SysFont.spaced(g, header, cx, hy, 9, light, true, 3.2f, 0.5f);
        float hw = SysFont.spacedWidth(header, 9, true, 3.2f);
        float line = 70 * head;
        Ui.divider(g, cx - hw / 2f - 8 - line, hy + 3.5f, line, Gfx.alpha(color, 0.9f));
        Ui.divider(g, cx + hw / 2f + 8, hy + 3.5f, line, Gfx.alpha(color, 0.9f));
        Gfx.setAlpha(oldH);

        // the name types out
        float ny = cy + 34;
        SysFont.Line name = SysFont.line(s.displayName().getString(), 17, true);
        int total = SysFont.length(name);
        int shown = Math.round(total * Anim.clamp01((t - 0.7f) / 0.5f));
        if (shown > 0) {
            float nx = cx - name.width() / 2f;
            SysFont.draw(g, shown >= total ? name : SysFont.truncate(name, shown), nx, ny, SystemTheme.TEXT, 0f);
            if (shown < total) {
                float cw = SysFont.truncate(name, shown).width();
                Shapes.box(nx + cw + 1, ny, 1.5f, 13).fill(Gfx.alpha(light, Anim.pulse(0.25f))).draw(g);
            }
        }

        // details
        float d = Anim.outCubic(Anim.window(t, 1.2f, 1.55f));
        if (d > 0) {
            float oldD = Gfx.fade(d);
            float dy = ny + 22 + (1 - d) * 5;
            String kind = Component.translatable(s.isActive() ? "skills.beyondsolo.active" : "skills.beyondsolo.passive").getString();
            String owner = s.owner() == null ? Component.translatable("skills.beyondsolo.common").getString() : s.owner().displayName().getString();
            String lv = Component.translatable("skills.beyondsolo.level", m.level).getString();
            String grade = Component.translatable("skills.beyondsolo.grade", s.grade().name()).getString();
            float cw = 0;
            for (String c : new String[]{kind, owner, lv, grade}) cw += SysFont.width(c, 6f, true) + 9 + 4;
            float chipX = cx - (cw - 4) / 2f;
            chipX += Ui.chip(g, chipX, dy, 10, kind, 6f, color, true) + 4;
            chipX += Ui.chip(g, chipX, dy, 10, owner, 6f, SystemTheme.TEXT_SOFT, false) + 4;
            chipX += Ui.chip(g, chipX, dy, 10, lv, 6f, SystemTheme.ACCENT, false) + 4;
            Ui.chip(g, chipX, dy, 10, grade, 6f, SystemTheme.GOLD, false);
            List<SysFont.Line> flav = SysFont.wrap(s.flavour(), 7.5f, false, 230);
            float fy = dy + 15;
            for (int i = 0; i < Math.min(2, flav.size()); i++) {
                SysFont.Line l = flav.get(i);
                SysFont.draw(g, l, cx - l.width() / 2f, fy, SystemTheme.TEXT_SOFT, 0f);
                fy += 7.5f * SysFont.LINE;
            }
            Gfx.setAlpha(oldD);

            // where it went
            float e = Anim.outCubic(Anim.window(t, 1.7f, 2.0f));
            if (e > 0) {
                float oldE = Gfx.fade(e);
                int slot = ClientSkills.data().slotOf(s.path());
                String hint;
                String key;
                if (slot >= 0) {
                    hint = Component.translatable("skills.beyondsolo.acquired.slot").getString();
                    key = SkillClientEvents.SLOT_KEYS[slot].getTranslatedKeyMessage().getString();
                } else {
                    hint = Component.translatable(s.isActive() ? "skills.beyondsolo.acquired.equip" : "skills.beyondsolo.acquired.passive").getString();
                    key = com.beyondsolo.client.ClientEvents.OPEN_SYSTEM.getTranslatedKeyMessage().getString();
                }
                float hy2 = fy + 5;
                float tw = SysFont.width(hint, 7f, false);
                float kw = Ui.keycapWidth(10, key);
                float hx = cx - (tw + 5 + kw) / 2f;
                SysFont.text(g, hint, hx, hy2 + 2, 7f, SystemTheme.TEXT_SOFT);
                Ui.keycap(g, hx + tw + 5, hy2, 10, key, color);
                Gfx.setAlpha(oldE);
            }
        }
        Gfx.setAlpha(old);
    }

    private static void drawBanner(GuiGraphics g, Banner b, float t) {
        Skill s = b.skill;
        int color = Gfx.opaque(s.color());
        float in = Anim.outBack(Anim.clamp01(t / 0.4f));
        float out = Anim.clamp01((t - (BANNER_TIME - 0.35f)) / 0.35f);
        float live = Anim.clamp01(t / 0.2f) * (1 - out);
        String head = Component.translatable("skills.beyondsolo.level_up.header").getString();
        String name = s.displayName().getString();
        float w = Math.max(SysFont.spacedWidth(head, 6f, true, 1.6f), SysFont.width(name, 9.5f, true) + 40) + 52;
        float h = 30;
        float x = g.guiWidth() / 2f - w / 2f;
        float y = 22 - (1 - in) * 30 - out * 10;
        float old = Gfx.fade(live);
        Ui.panel(g, x, y, w, h, 10, color, 0.8f);
        float pop = Anim.window(t, 0.25f, 0.9f);
        SkillEmblem.draw(g, s, x + 17, y + h / 2f, 9, 1 - pop, false);
        if (pop > 0 && pop < 1) Shapes.ring(x + 17, y + h / 2f, 9 + 16 * Anim.outCubic(pop), 1.4f * (1 - pop)).fill(Gfx.alpha(color, 1 - pop)).draw(g);
        SysFont.spaced(g, head, x + 32, y + 6, 6f, Gfx.mix(color, 0xFFFFFFFF, 0.3f), true, 1.6f, 0f);
        SysFont.title(g, name, x + 32, y + 15, 9.5f, SystemTheme.TEXT);
        // the level ticks over
        float tick = Anim.outBackStrong(Anim.window(t, 0.45f, 0.85f));
        float lx = x + w - 12;
        String from = "Lv." + (b.level - 1);
        String to = "Lv." + b.level;
        float oldA = Gfx.fade(1 - tick);
        SysFont.draw(g, from, lx, y + 10 - 6 * tick, 9f, SystemTheme.TEXT_SOFT, true, 1f);
        Gfx.setAlpha(oldA);
        float oldB = Gfx.fade(tick);
        SysFont.draw(g, to, lx, y + 16 - 6 * tick, 9f, Gfx.mix(color, 0xFFFFFFFF, 0.5f), true, 1f);
        Gfx.setAlpha(oldB);
        Gfx.setAlpha(old);
    }

    private SkillOverlay() {}

    static boolean awakened() {
        return ClientSystemData.get().isAwakened();
    }
}
