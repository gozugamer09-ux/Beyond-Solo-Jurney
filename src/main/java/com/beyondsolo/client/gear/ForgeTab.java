package com.beyondsolo.client.gear;

import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.SystemTheme;
import com.beyondsolo.client.economy.EconomyClient;
import com.beyondsolo.client.economy.GoldHud;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Icons;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.client.gfx.SysFont;
import com.beyondsolo.client.gfx.Ui;
import com.beyondsolo.client.screen.SystemScreen;
import com.beyondsolo.client.screen.SystemTab;
import com.beyondsolo.gear.BonusStats;
import com.beyondsolo.gear.Enhancement;
import com.beyondsolo.gear.ForgeService;
import com.beyondsolo.gear.GearKind;
import com.beyondsolo.gear.GearModule;
import com.beyondsolo.gear.GearRolls;
import com.beyondsolo.gear.Infusions;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.network.ForgeActionPayload;
import com.beyondsolo.network.ForgeResultPayload;
import com.beyondsolo.skill.SkillElement;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The FORGE page of the System window. Your gear on the left (inventory, armor worn and off hand); the chosen piece
 * turns over a forge ring on the right, with ten notches for its refinement. REFINE: chance, what it costs, a ward
 * seal toggle once failing would cost a level, and the try itself: three hammer strikes of light, then a burst and
 * a "+7" stamp, or cracks, a shake and the level falling. INFUSE: nine element crystals; the chosen one's light
 * spirals into the weapon.
 */
public class ForgeTab implements SystemTab {
    private static final float LIST_W = 136;
    private static final float ROW_H = 22;
    /** The hammer strikes play at least this long before the outcome shows. */
    private static final float CHARGE = 0.75f;

    private static int chosenSlot = -1;
    private static boolean infusing;
    private static boolean useWard = true;
    private static SkillElement element = SkillElement.FIRE;

    private float scroll;
    private double chosenAt = -10;
    private double modeAt = -10;
    private double seenResult;
    private double revealAt = -10;
    private @Nullable ForgeResultPayload shown;
    private final List<Object[]> hits = new ArrayList<>();

    @Override
    public Component title() {
        return Component.translatable("tab.beyondsolo.forge");
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
        seenResult = GearClient.lastAt();
        modeAt = Anim.now();
        // materials in System storage count too: get the server's copy of it
        com.beyondsolo.client.crafting.CraftClient.requestStock();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (GearClient.lastAt() > seenResult) {
            seenResult = GearClient.lastAt();
            shown = GearClient.last();
            // the hammer always gets its strikes in before the outcome
            revealAt = Math.max(GearClient.lastAt(), GearClient.sentAt() + (shown != null && shown.outcome() == Enhancement.Outcome.INVALID.ordinal() ? 0 : CHARGE));
        }

        List<Integer> slots = gearSlots(player.getInventory());
        if (!slots.contains(chosenSlot)) chosenSlot = slots.isEmpty() ? -1 : slots.get(0);
        list(g, player.getInventory(), slots, x, y, h, mouseX, mouseY);
        float dx = x + LIST_W + 8;
        float dw = x + w - dx;
        if (chosenSlot < 0) {
            Ui.card(g, dx, y, dw, h, 9, SystemTheme.ACCENT, 0.12f);
            Shapes.icon(Icons.FLAME, dx + dw / 2f, y + h / 2f - 14, 22).fill(0x405FD0FF).draw(g);
            List<SysFont.Line> lines = SysFont.wrap(Component.translatable("forge.beyondsolo.empty"), 6.5f, false, dw - 30);
            for (int i = 0; i < lines.size(); i++) SysFont.draw(g, lines.get(i), dx + dw / 2f, y + h / 2f + 4 + i * 8.5f, SystemTheme.TEXT_SOFT, 0.5f);
            return;
        }
        detail(g, player.getInventory(), player.getInventory().getItem(chosenSlot), dx, y, dw, h, mouseX, mouseY);
    }

    /** Inventory slots holding System gear: worn armor and the hand first, then the rest. */
    static List<Integer> gearSlots(Inventory inv) {
        List<Integer> out = new ArrayList<>();
        int[] first = {inv.selected, 39, 38, 37, 36, 40};
        for (int s : first) if (GearKind.of(inv.getItem(s)) != null && !out.contains(s)) out.add(s);
        for (int i = 0; i < 36; i++) if (GearKind.of(inv.getItem(i)) != null && !out.contains(i)) out.add(i);
        return out;
    }

    private void list(GuiGraphics g, Inventory inv, List<Integer> slots, float x, float y, float h, int mouseX, int mouseY) {
        SysFont.spaced(g, Component.translatable("forge.beyondsolo.your_gear").getString(), x + 2, y + 1, 6f, SystemTheme.TEXT_DIM, true, 1.2f, 0f);
        float ly = y + 11;
        float lh = h - 11;
        int visible = (int) (lh / ROW_H);
        int max = Math.max(0, slots.size() - visible);
        scroll = Mth.clamp(scroll, 0, max);
        Shapes.flush(g);
        g.enableScissor((int) x - 2, (int) ly - 1, (int) (x + LIST_W + 4), (int) (ly + lh));
        for (int i = 0; i < slots.size(); i++) {
            float ry = ly + (i - scroll) * ROW_H;
            if (ry < ly - ROW_H || ry > ly + lh) continue;
            int slot = slots.get(i);
            ItemStack s = inv.getItem(slot);
            int gc = gradeColor(s);
            boolean on = slot == chosenSlot;
            boolean hover = Ui.inside(mouseX, mouseY, x, ry, LIST_W, ROW_H - 2) && mouseY < ly + lh;
            Ui.card(g, x, ry, LIST_W, ROW_H - 2, 5, gc, on ? 0.6f : hover ? 0.3f : 0.08f);
            if (on) Shapes.pill(x + 1.5f, ry + 4, 2, ROW_H - 10).fill(gc).glow(2, Gfx.alpha(gc, 0.7f)).draw(g);
            Shapes.box(x + 5, ry + 1.5f, 17, 17).radius(4).fill(Gfx.alpha(gc, 0.14f)).border(0.5f, Gfx.alpha(gc, 0.4f)).draw(g);
            g.renderItem(s, (int) x + 5, (int) ry + 2);
            String name = SysFont.fit(s.getItem().getName(s).getString().replaceFirst("^\\+\\d+ ", ""), 6.6f, true, LIST_W - 52);
            SysFont.title(g, name, x + 26, ry + 3, 6.6f, Gfx.mix(gc, 0xFFFFFFFF, 0.45f));
            ItemGrade grade = ItemGrade.of(s);
            String where = slot >= 36 && slot <= 39 ? Component.translatable("forge.beyondsolo.worn").getString()
                    : slot == inv.selected ? Component.translatable("forge.beyondsolo.held").getString()
                    : slot == 40 ? Component.translatable("forge.beyondsolo.offhand").getString() : "";
            String sub = (grade == null ? "" : grade.displayName().getString()) + (where.isEmpty() ? "" : "  ·  " + where);
            SysFont.text(g, sub, x + 26, ry + 11.5f, 5.4f, SystemTheme.TEXT_DIM);
            int level = Enhancement.level(s);
            if (level > 0) {
                String ls = "+" + level;
                float lw = SysFont.width(ls, 7f, true);
                SysFont.title(g, ls, x + LIST_W - 6 - lw, ry + 6, 7f, Gfx.opaque(com.beyondsolo.gear.GearTooltip.ENHANCE_COLOR));
            }
            SkillElement inf = Infusions.of(s);
            if (inf != null) Shapes.circle(x + 20, ry + 16, 2.6f).fill(Gfx.opaque(inf.color())).glow(2.5f, Gfx.alpha(Gfx.opaque(inf.color()), 0.8f)).draw(g);
            hits.add(new Object[]{"slot", x, ry, LIST_W, ROW_H - 2, slot});
        }
        Shapes.flush(g);
        g.disableScissor();
        if (max > 0) {
            float knob = Math.max(12, lh * visible / slots.size());
            float ky = ly + (lh - knob) * scroll / max;
            Shapes.pill(x + LIST_W + 2, ky, 2, knob).fill(Gfx.alpha(SystemTheme.ACCENT, 0.5f)).draw(g);
        }
        hits.add(new Object[]{"list", x, ly, LIST_W, lh, null});
    }

    private void detail(GuiGraphics g, Inventory inv, ItemStack stack, float x, float y, float w, float h, int mouseX, int mouseY) {
        int gc = gradeColor(stack);
        GearKind kind = GearKind.of(stack);
        if (kind == null) return;
        if (!kind.weapon()) infusing = false;
        float k = Anim.outCubic(Anim.progress(chosenAt, 0.3f));
        float old = Gfx.fade(k);
        g.pose().pushPose();
        g.pose().translate((1 - k) * 8, 0, 0);

        // shake on a failure
        float sinceReveal = Anim.since(revealAt);
        boolean failedNow = shown != null && shown.slot() == chosenSlot && sinceReveal >= 0 && sinceReveal < 0.45f
                && (shown.outcome() == Enhancement.Outcome.FAILED.ordinal() || shown.outcome() == Enhancement.Outcome.DOWNGRADED.ordinal());
        if (failedNow) {
            float amp = 2.2f * (1 - sinceReveal / 0.45f);
            g.pose().translate(Math.sin(sinceReveal * 90) * amp, Math.cos(sinceReveal * 70) * amp * 0.5f, 0);
        }
        Ui.card(g, x, y, w, h, 9, gc, 0.2f);

        // mode pills and the wallet
        modePills(g, x + 7, y + 6, kind, mouseX, mouseY);
        wallet(g, x + w - 7, y + 6);

        float cx = x + w / 2f;
        float cy = y + 50;
        forgeRing(g, stack, cx, cy, gc);

        // name
        String name = stack.getHoverName().getString();
        SysFont.draw(g, SysFont.fit(name, 7.4f, true, w - 16), cx, cy + 30, 7.4f, Gfx.mix(gc, 0xFFFFFFFF, 0.5f), true, 0.5f);
        BonusStats stats = GearRolls.stats(stack);
        ItemGrade grade = ItemGrade.of(stack);
        String sub = (grade == null ? "" : grade.displayName().getString()) + "  ·  "
                + Component.translatable("forge.beyondsolo.stats_count", stats.rolls().size()).getString();
        SysFont.draw(g, sub, cx, cy + 39.5f, 5.6f, SystemTheme.TEXT_DIM, false, 0.5f);

        float py = cy + 49;
        if (infusing) infusePanel(g, inv, stack, x, py, w, y + h - py, mouseX, mouseY);
        else refinePanel(g, inv, stack, kind, x, py, w, y + h - py, mouseX, mouseY);

        outcome(g, stack, cx, cy);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    private void modePills(GuiGraphics g, float x, float y, GearKind kind, int mouseX, int mouseY) {
        float px = x;
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == infusing;
            boolean enabled = i == 0 || kind.weapon();
            String label = Component.translatable(i == 0 ? "forge.beyondsolo.refine" : "forge.beyondsolo.infuse").getString();
            float pw = SysFont.spacedWidth(label, 6.4f, true, 1f) + 24;
            boolean hover = enabled && !on && Ui.inside(mouseX, mouseY, px, y, pw, 13);
            int c = !enabled ? SystemTheme.TEXT_DIM : i == 0 ? Gfx.opaque(com.beyondsolo.gear.GearTooltip.ENHANCE_COLOR) : Gfx.opaque(element.color());
            Shapes.pill(px, y, pw, 13).fill(Gfx.alpha(c, on ? 0.28f : hover ? 0.12f : 0.05f))
                    .border(0.75f, Gfx.alpha(c, on ? 0.95f : 0.35f)).glow(on ? 4 : 0, Gfx.alpha(c, 0.3f)).draw(g);
            Shapes.icon(i == 0 ? Icons.FLAME : Icons.SPARKLE, px + 8.5f, y + 6.5f, 7.5f).fill(on ? Gfx.mix(c, 0xFFFFFFFF, 0.4f) : SystemTheme.TEXT_SOFT).draw(g);
            SysFont.spaced(g, label, px + 15, SysFont.centerY(y, 13, 6.4f), 6.4f, on ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, 1f, 0f);
            if (enabled) hits.add(new Object[]{"mode", px, y, pw, 13f, i == 1});
            px += pw + 4;
        }
    }

    private void wallet(GuiGraphics g, float right, float y) {
        String gold = EconomyClient.format(EconomyClient.gold());
        float gw = SysFont.width(gold, 7f, true) + 24;
        float gx = right - gw;
        float flash = 1 - Anim.clamp01(Anim.since(EconomyClient.lastChangeAt()) / 0.7f);
        Shapes.pill(gx, y, gw, 13).fillV(Gfx.alpha(GoldHud.GOLD, 0.14f + 0.2f * flash), Gfx.alpha(GoldHud.GOLD_DEEP, 0.08f))
                .border(0.75f, Gfx.alpha(GoldHud.GOLD, 0.55f + 0.4f * flash)).glow(2 + 5 * flash, Gfx.alpha(GoldHud.GOLD, 0.2f + 0.4f * flash)).draw(g);
        Shapes.circle(gx + 7, y + 6.5f, 4.5f).fillV(GoldHud.GOLD, GoldHud.GOLD_DEEP).border(0.5f, 0xFFFFF0C0, 0xFFB86A10).draw(g);
        SysFont.title(g, gold, gx + 14, SysFont.centerY(y, 13, 7f), 7f, 0xFFFFF4D6);
    }

    /** The item turning over the forge ring, the ten refinement notches around it, and the hammer strikes. */
    private void forgeRing(GuiGraphics g, ItemStack stack, float cx, float cy, int gc) {
        int level = Enhancement.level(stack);
        float charge = Anim.since(GearClient.sentAt());
        boolean charging = charge >= 0 && charge < CHARGE && (revealAt < GearClient.sentAt() || Anim.since(revealAt) < 0);
        float speed = charging ? 4f : 1f;
        float t = Anim.loop(9f / speed);
        int ec = Gfx.opaque(com.beyondsolo.gear.GearTooltip.ENHANCE_COLOR);
        int ringColor = infusing ? Gfx.opaque(element.color()) : gc;

        Shapes.circle(cx, cy, 30).fill(0).glow(16, Gfx.alpha(ringColor, 0.18f + (charging ? 0.2f : 0))).draw(g);
        Shapes.ring(cx, cy, 23, 0.8f).fill(Gfx.alpha(ringColor, 0.5f)).dashes(16, 0.5f).rotation(t).draw(g);
        Shapes.ring(cx, cy, 15, 0.6f).fill(Gfx.alpha(ringColor, 0.32f)).dashes(8, 0.4f).rotation(-t * 1.6f).draw(g);

        // the ten notches of refinement
        if (!infusing) {
            float pulse = Anim.pulse(1.4f);
            for (int i = 0; i < Enhancement.MAX; i++) {
                double a = -Math.PI / 2 + (i + 0.5) * Math.PI * 2 / Enhancement.MAX;
                float px = cx + (float) Math.cos(a) * 29;
                float py = cy + (float) Math.sin(a) * 29;
                boolean lit = i < level;
                boolean next = i == level;
                int c = lit ? ec : next ? Gfx.alpha(ec, 0.35f + 0.35f * pulse) : 0x40FFFFFF;
                Shapes.circle(px, py, lit ? 2.4f : 1.9f).fill(c).glow(lit ? 3 : next ? 2 : 0, Gfx.alpha(ec, lit ? 0.7f : 0.4f)).draw(g);
            }
        }

        // hammer strikes: three flashes and sparks pulled into the item
        if (charging) {
            for (int s = 0; s < 3; s++) {
                float at = 0.12f + s * 0.2f;
                float sk = Anim.clamp01((charge - at) / 0.16f);
                if (sk <= 0 || sk >= 1) continue;
                Shapes.ring(cx, cy, 8 + 18 * Anim.outCubic(sk), 1.4f * (1 - sk) + 0.2f).fill(Gfx.alpha(0xFFFFFFFF, 1 - sk)).glow(4, Gfx.alpha(ringColor, 0.6f * (1 - sk))).draw(g);
                Shapes.circle(cx, cy, 10).fill(0).glow(14 * (1 - sk), Gfx.alpha(0xFFFFFFFF, 0.5f * (1 - sk))).draw(g);
            }
            for (int i = 0; i < 12; i++) {
                float sk = (charge * 1.8f + i / 12f) % 1f;
                double a = i * 2.4 + charge * 3;
                float r = 34 * (1 - Anim.inCubic(sk));
                Shapes.circle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 1.2f)
                        .fill(Gfx.alpha(Gfx.mix(ringColor, 0xFFFFFFFF, 0.5f), sk)).glow(2, Gfx.alpha(ringColor, 0.6f * sk)).draw(g);
            }
        }

        float bob = (float) Math.sin(Anim.loop(2.6f) * Math.PI * 2) * 1.2f;
        float scale = 2f;
        if (charging) {
            float hit = 0;
            for (int s = 0; s < 3; s++) hit = Math.max(hit, 1 - Anim.clamp01(Math.abs(charge - (0.12f + s * 0.2f)) / 0.06f));
            scale *= 1 - 0.08f * hit;
        }
        g.pose().pushPose();
        g.pose().translate(cx, cy + bob, 150);
        g.pose().scale(scale, scale, 1);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
    }

    private void refinePanel(GuiGraphics g, Inventory inv, ItemStack stack, GearKind kind, float x, float y, float w, float h, int mouseX, int mouseY) {
        int level = Enhancement.level(stack);
        float cx = x + w / 2f;
        int ec = Gfx.opaque(com.beyondsolo.gear.GearTooltip.ENHANCE_COLOR);
        if (level >= Enhancement.MAX) {
            SysFont.draw(g, Component.translatable("forge.beyondsolo.maxed").getString(), cx, y + 8, 8f, ec, true, 0.5f);
            SysFont.draw(g, effect(kind, level), cx, y + 20, 6f, SystemTheme.TEXT_SOFT, false, 0.5f);
            return;
        }
        // "+6 -> +7   60%"
        int chance = Math.round(Enhancement.chance(level) * 100);
        int cc = chance >= 80 ? SystemTheme.GOOD : chance >= 50 ? SystemTheme.GOLD : SystemTheme.DANGER;
        String from = "+" + level, to = "+" + (level + 1);
        float fw = SysFont.width(from, 8f, true), tw = SysFont.width(to, 8f, true);
        float rowX = x + 12;
        SysFont.title(g, from, rowX, y + 1, 8f, SystemTheme.TEXT_SOFT);
        Shapes.icon(Icons.PATH, rowX + fw + 8, y + 5.5f, 7).fill(SystemTheme.TEXT_DIM).draw(g);
        SysFont.title(g, to, rowX + fw + 15, y + 1, 8f, ec);
        SysFont.text(g, effect(kind, level + 1), rowX + fw + tw + 22, y + 2.5f, 5.6f, SystemTheme.TEXT_SOFT);
        String cs = chance + "%";
        float cw = SysFont.width(cs, 9f, true);
        SysFont.title(g, cs, x + w - 12 - cw, y, 9f, cc);
        SysFont.text(g, Component.translatable("forge.beyondsolo.chance").getString(), x + w - 12 - cw - 30, y + 2.5f, 5.6f, SystemTheme.TEXT_DIM);

        // costs
        ItemGrade grade = ItemGrade.of(stack);
        long gold = Enhancement.gold(grade == null ? ItemGrade.COMMON : grade, level);
        int stones = Enhancement.stones(level);
        int haveStones = ForgeService.have(inv, com.beyondsolo.client.crafting.CraftClient.storage(), GearModule.REFINING_STONE.get());
        int haveSeals = ForgeService.have(inv, com.beyondsolo.client.crafting.CraftClient.storage(), GearModule.WARD_SEAL.get());
        boolean creative = inv.player.getAbilities().instabuild;
        float ry = y + 14;
        float costX = x + 12;
        costX += costChip(g, costX, ry, new ItemStack(GearModule.REFINING_STONE.get()), "×" + stones + (creative ? "" : "  (" + haveStones + ")"), creative || haveStones >= stones) + 5;
        costX += goldChip(g, costX, ry, gold, creative || EconomyClient.gold() >= gold) + 5;
        boolean risky = Enhancement.risky(level);
        if (risky) {
            boolean can = creative || haveSeals > 0;
            boolean on = useWard && can;
            float ww = x + w - 12 - costX;
            int sc = on ? 0xFF7FD8FF : SystemTheme.TEXT_DIM;
            Shapes.pill(costX, ry, ww, 14).fill(Gfx.alpha(sc, on ? 0.18f : 0.05f)).border(0.75f, Gfx.alpha(sc, on ? 0.9f : 0.35f)).draw(g);
            Shapes.box(costX + 4, ry + 3.5f, 7, 7).radius(2).fill(on ? sc : 0).border(0.75f, sc).draw(g);
            if (on) Shapes.icon(Icons.CHECK, costX + 7.5f, ry + 7, 6).fill(0xFF0A1A24).draw(g);
            g.pose().pushPose();
            g.pose().translate(costX + 13, ry - 1, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.renderItem(new ItemStack(GearModule.WARD_SEAL.get()), 0, 0);
            g.pose().popPose();
            SysFont.text(g, SysFont.fit(Component.translatable("forge.beyondsolo.ward", creative ? "∞" : String.valueOf(haveSeals)).getString(), 5.6f, false, ww - 30),
                    costX + 27, ry + 4.5f, 5.6f, on ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT);
            if (can) hits.add(new Object[]{"ward", costX, ry, ww, 14f, null});
        }
        String note = risky ? Component.translatable(useWard && (creative || haveSeals > 0) ? "forge.beyondsolo.risk_warded" : "forge.beyondsolo.risk", level - 1).getString()
                : Component.translatable("forge.beyondsolo.safe").getString();
        SysFont.draw(g, SysFont.fit(note, 5.6f, false, w - 20), cx, ry + 18, 5.6f, risky ? Gfx.alpha(SystemTheme.DANGER, 0.9f) : SystemTheme.TEXT_DIM, false, 0.5f);

        String why = null;
        if (!creative && haveStones < stones) why = Component.translatable("forge.beyondsolo.no_stones").getString();
        else if (!creative && EconomyClient.gold() < gold) why = Component.translatable("forge.beyondsolo.no_gold").getString();
        else if (busy()) why = Component.translatable("forge.beyondsolo.working").getString();
        bigButton(g, x + 8, y + h - 22, w - 16, Component.translatable("forge.beyondsolo.refine_button", level + 1).getString(), why, ec, mouseX, mouseY, "refine");
    }

    private static String effect(GearKind kind, int level) {
        if (kind == GearKind.ARMOR) {
            return Component.translatable("forge.beyondsolo.effect.armor", com.beyondsolo.gear.BonusStat.fmt(level * Enhancement.armorPerLevel())).getString();
        }
        return Component.translatable(kind == GearKind.BOW ? "forge.beyondsolo.effect.arrows" : "forge.beyondsolo.effect.damage",
                Math.round(Enhancement.damageBonus(level) * 100)).getString();
    }

    private void infusePanel(GuiGraphics g, Inventory inv, ItemStack stack, float x, float y, float w, float h, int mouseX, int mouseY) {
        SkillElement current = Infusions.of(stack);
        boolean creative = inv.player.getAbilities().instabuild;
        SkillElement[] els = SkillElement.values();
        int n = els.length - 1;
        float gap = 3;
        float gw = Math.min(20, (w - 16 - gap * (n - 1)) / n);
        float gx = x + (w - (gw * n + gap * (n - 1))) / 2f;
        for (int i = 1; i < els.length; i++) {
            SkillElement e = els[i];
            int c = Gfx.opaque(e.color());
            int have = ForgeService.have(inv, com.beyondsolo.client.crafting.CraftClient.storage(), GearModule.CRYSTALS.get(e).get());
            boolean on = e == element;
            boolean hover = Ui.inside(mouseX, mouseY, gx, y, gw, gw + 6);
            float bob = on ? (float) Math.sin(Anim.loop(1.6f) * Math.PI * 2) * 0.8f : 0;
            Shapes.box(gx, y + bob, gw, gw).radius(5).fillV(Gfx.alpha(c, on ? 0.35f : hover ? 0.2f : 0.08f), Gfx.alpha(c, 0.04f))
                    .border(on ? 1f : 0.6f, Gfx.alpha(c, on ? 1f : 0.45f)).glow(on ? 5 : 0, Gfx.alpha(c, 0.5f)).draw(g);
            g.renderItem(new ItemStack(GearModule.CRYSTALS.get(e).get()), (int) (gx + gw / 2f - 8), (int) (y + bob + gw / 2f - 8));
            String hs = creative ? "∞" : String.valueOf(have);
            SysFont.draw(g, hs, gx + gw / 2f, y + gw + 1.5f, 5f, have > 0 || creative ? SystemTheme.TEXT_SOFT : SystemTheme.TEXT_DIM, true, 0.5f);
            if (e == current) Shapes.circle(gx + gw - 2, y + 2, 2.4f).fill(c).border(0.5f, 0xFFFFFFFF).draw(g);
            hits.add(new Object[]{"element", gx, y, gw, gw + 6, e});
            gx += gw + gap;
        }
        float cx = x + w / 2f;
        int ec = Gfx.opaque(element.color());
        String now = current == null ? Component.translatable("forge.beyondsolo.infusion_none").getString()
                : Component.translatable("forge.beyondsolo.infusion_now", current.displayName()).getString();
        SysFont.draw(g, SysFont.fit(now, 5.8f, false, w - 20), cx, y + gw + 10, 5.8f, current == null ? SystemTheme.TEXT_DIM : Gfx.opaque(current.color()), false, 0.5f);
        ItemGrade grade = ItemGrade.of(stack);
        long gold = ForgeService.infusionGold(grade == null ? ItemGrade.COMMON : grade);
        float cw = goldWidth(gold) ;
        goldChip(g, cx - cw / 2f, y + gw + 19, gold, creative || EconomyClient.gold() >= gold);

        String why = null;
        int have = ForgeService.have(inv, com.beyondsolo.client.crafting.CraftClient.storage(), GearModule.CRYSTALS.get(element).get());
        if (element == current) why = Component.translatable("forge.beyondsolo.same_element").getString();
        else if (!creative && have <= 0) why = Component.translatable("forge.beyondsolo.no_crystal").getString();
        else if (!creative && EconomyClient.gold() < gold) why = Component.translatable("forge.beyondsolo.no_gold").getString();
        else if (busy()) why = Component.translatable("forge.beyondsolo.working").getString();
        bigButton(g, x + 8, y + h - 22, w - 16, Component.translatable("forge.beyondsolo.infuse_button", element.displayName()).getString(), why, ec, mouseX, mouseY, "infuse");
    }

    private boolean busy() {
        return Anim.since(GearClient.sentAt()) < CHARGE + 0.3f;
    }

    /** The outcome of the last try: burst and stamp, cracks, ward or an element spiral. */
    private void outcome(GuiGraphics g, ItemStack stack, float cx, float cy) {
        ForgeResultPayload r = shown;
        if (r == null || r.slot() != chosenSlot) return;
        float t = Anim.since(revealAt);
        if (t < 0 || t > 2.2f) return;
        Enhancement.Outcome out = Enhancement.Outcome.values()[Math.min(r.outcome(), Enhancement.Outcome.values().length - 1)];
        if (out == Enhancement.Outcome.INVALID) {
            float k = 1 - Anim.clamp01((t - 1.2f) / 0.4f);
            if (!r.reason().isEmpty()) stampLabel(g, cx, cy, Component.translatable(r.reason()).getString(), "", SystemTheme.DANGER, Math.min(1, t / 0.15f) * k, 0);
            return;
        }
        boolean infuse = r.action() == ForgeActionPayload.INFUSE;
        int ec = Gfx.opaque(com.beyondsolo.gear.GearTooltip.ENHANCE_COLOR);
        float fade = 1 - Anim.clamp01((t - 1.6f) / 0.6f);
        if (infuse) {
            SkillElement e = SkillElement.values()[Mth.clamp(r.after(), 0, SkillElement.values().length - 1)];
            int c = Gfx.opaque(e.color());
            // light spirals in, then a bloom
            for (int i = 0; i < 18; i++) {
                float sk = Anim.clamp01((t - i * 0.02f) / 0.6f);
                if (sk <= 0 || sk >= 1) continue;
                double a = i * (Math.PI * 2 / 18) + sk * 5;
                float rr = 44 * (1 - Anim.inOutCubic(sk));
                Shapes.circle(cx + (float) Math.cos(a) * rr, cy + (float) Math.sin(a) * rr, 1.8f).fill(Gfx.mix(c, 0xFFFFFFFF, 0.4f)).glow(3, Gfx.alpha(c, 0.8f)).draw(g);
            }
            float bk = Anim.clamp01((t - 0.55f) / 0.6f);
            if (bk > 0 && bk < 1) {
                Shapes.rays(cx, cy, 20 + 30 * Anim.outCubic(bk), 10).fill(Gfx.alpha(c, 0.6f * (1 - bk))).rotation(t * 0.2f).additive().draw(g);
                Shapes.ring(cx, cy, 10 + 34 * Anim.outCubic(bk), 1.6f * (1 - bk) + 0.3f).fill(Gfx.alpha(c, 1 - bk)).glow(5, Gfx.alpha(c, 0.6f * (1 - bk))).draw(g);
            }
            if (t > 0.55f) stampLabel(g, cx, cy, Component.translatable("forge.beyondsolo.infused").getString(), e.displayName().getString(), c,
                    Anim.clamp01((t - 0.55f) / 0.2f) * fade, Anim.clamp01((t - 0.55f) / 0.3f));
            return;
        }
        switch (out) {
            case SUCCESS -> {
                float bk = Anim.clamp01(t / 0.7f);
                Shapes.circle(cx, cy, 10).fill(0).glow(26 * (1 - bk), Gfx.alpha(0xFFFFFFFF, 0.8f * (1 - bk))).draw(g);
                Shapes.rays(cx, cy, 26 + 34 * Anim.outCubic(bk), 14).fill(Gfx.alpha(ec, 0.7f * (1 - bk))).rotation(t * 0.15f).additive().draw(g);
                Shapes.ring(cx, cy, 12 + 40 * Anim.outCubic(bk), 2f * (1 - bk) + 0.3f).fill(Gfx.alpha(Gfx.mix(ec, 0xFFFFFFFF, 0.4f), 1 - bk)).glow(6, Gfx.alpha(ec, 0.7f * (1 - bk))).draw(g);
                // the new notch flares
                double a = -Math.PI / 2 + (r.after() - 0.5) * Math.PI * 2 / Enhancement.MAX;
                float nk = Anim.clamp01(t / 0.5f);
                Shapes.circle(cx + (float) Math.cos(a) * 29, cy + (float) Math.sin(a) * 29, 2.4f + 6 * (1 - nk)).fill(Gfx.alpha(0xFFFFFFFF, 1 - nk)).glow(6, Gfx.alpha(ec, 1 - nk)).draw(g);
                stampLabel(g, cx, cy, "+" + r.after(), Component.translatable("forge.beyondsolo.success").getString(), ec, Anim.clamp01(t / 0.15f) * fade, Anim.clamp01(t / 0.3f));
            }
            case FAILED, DOWNGRADED -> {
                int c = SystemTheme.DANGER;
                float ck = Anim.clamp01(t / 0.25f);
                for (int i = 0; i < 7; i++) {
                    float ang = i * 51.4f + 13;
                    float len = (14 + (i * 7) % 12) * ck;
                    g.pose().pushPose();
                    g.pose().translate(cx, cy, 440);
                    g.pose().mulPose(Axis.ZP.rotationDegrees(ang));
                    Shapes.box(4, -0.6f, len, 1.2f).fill(Gfx.alpha(c, 0.9f * fade)).glow(2, Gfx.alpha(c, 0.5f * fade)).draw(g);
                    Shapes.box(4 + len * 0.6f, -0.5f, len * 0.4f, 1f).fill(Gfx.alpha(c, 0.7f * fade)).draw(g);
                    g.pose().popPose();
                }
                Shapes.circle(cx, cy, 30).fill(0).glow(14 * (1 - ck * 0.5f), Gfx.alpha(c, 0.35f * fade)).draw(g);
                String sub = out == Enhancement.Outcome.DOWNGRADED
                        ? Component.translatable("forge.beyondsolo.downgraded", r.before(), r.after()).getString()
                        : Component.translatable("forge.beyondsolo.kept", r.after()).getString();
                stampLabel(g, cx, cy, Component.translatable("forge.beyondsolo.failed").getString(), sub, c, Anim.clamp01(t / 0.15f) * fade, Anim.clamp01(t / 0.3f));
            }
            case WARDED -> {
                int c = 0xFF7FD8FF;
                float wk = Anim.clamp01(t / 0.5f);
                Shapes.icon(Icons.HEXAGON, cx, cy, 44 + 10 * wk).fill(Gfx.alpha(c, 0.5f * (1 - wk))).glow(6, Gfx.alpha(c, 0.6f * (1 - wk))).draw(g);
                Shapes.ring(cx, cy, 24 + 22 * Anim.outCubic(wk), 1.4f * (1 - wk) + 0.3f).fill(Gfx.alpha(c, 1 - wk)).dashes(6, 0.7f).rotation(t * 0.3f).draw(g);
                stampLabel(g, cx, cy, Component.translatable("forge.beyondsolo.failed").getString(),
                        Component.translatable("forge.beyondsolo.warded", r.after()).getString(), c, Anim.clamp01(t / 0.15f) * fade, Anim.clamp01(t / 0.3f));
            }
            default -> {}
        }
    }

    /** A tilted stamp over the ring: a big label and a line under it. */
    private void stampLabel(GuiGraphics g, float cx, float cy, String label, String sub, int c, float alpha, float pop) {
        if (alpha <= 0) return;
        float s = 1.5f - 0.5f * Anim.outBackStrong(pop);
        float old = Gfx.fade(alpha);
        g.pose().pushPose();
        g.pose().translate(cx, cy + 2, 450);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-7));
        g.pose().scale(s, s, 1);
        float size = label.startsWith("+") ? 13f : 8f;
        float tw = SysFont.spacedWidth(label, size, true, 1.6f) + 14;
        float bh = size + 8;
        Shapes.box(-tw / 2f, -bh / 2f, tw, bh).radius(3).fill(Gfx.alpha(c, 0.2f)).border(1.2f, c).glow(5, Gfx.alpha(c, 0.5f)).draw(g);
        SysFont.spaced(g, label, 0, SysFont.centerY(-bh / 2f, bh, size), size, Gfx.mix(c, 0xFFFFFFFF, 0.35f), true, 1.6f, 0.5f);
        if (!sub.isEmpty()) {
            float sw = SysFont.width(sub, 6f, true) + 10;
            Shapes.pill(-sw / 2f, bh / 2f + 2, sw, 10).fill(0xC0101820).border(0.5f, Gfx.alpha(c, 0.6f)).draw(g);
            SysFont.draw(g, sub, 0, bh / 2f + 3.5f, 6f, Gfx.mix(c, 0xFFFFFFFF, 0.5f), true, 0.5f);
        }
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    // ------------------------------------------------------------------ pieces

    private float costChip(GuiGraphics g, float x, float y, ItemStack icon, String text, boolean ok) {
        float tw = SysFont.width(text, 6.2f, true);
        float w = tw + 22;
        int c = ok ? SystemTheme.ACCENT : SystemTheme.DANGER;
        Shapes.pill(x, y, w, 14).fill(Gfx.alpha(c, 0.1f)).border(0.75f, Gfx.alpha(c, 0.5f)).draw(g);
        g.pose().pushPose();
        g.pose().translate(x + 2, y - 1, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();
        SysFont.title(g, text, x + 16, SysFont.centerY(y, 14, 6.2f), 6.2f, ok ? SystemTheme.TEXT : SystemTheme.DANGER);
        return w;
    }

    private static float goldWidth(long gold) {
        return SysFont.width(EconomyClient.format(gold) + " G", 6.2f, true) + 18;
    }

    private float goldChip(GuiGraphics g, float x, float y, long gold, boolean ok) {
        String s = EconomyClient.format(gold) + " G";
        float w = goldWidth(gold);
        int c = ok ? GoldHud.GOLD : SystemTheme.DANGER;
        Shapes.pill(x, y, w, 14).fill(Gfx.alpha(c, 0.1f)).border(0.75f, Gfx.alpha(c, 0.5f)).draw(g);
        Shapes.circle(x + 7, y + 7, 3.6f).fillV(GoldHud.GOLD, GoldHud.GOLD_DEEP).draw(g);
        SysFont.title(g, s, x + 13, SysFont.centerY(y, 14, 6.2f), 6.2f, ok ? 0xFFFFE7A6 : SystemTheme.DANGER);
        return w;
    }

    private void bigButton(GuiGraphics g, float x, float y, float w, String label, @Nullable String why, int color, int mouseX, int mouseY, String action) {
        float h = 17;
        boolean enabled = why == null;
        boolean hover = enabled && Ui.inside(mouseX, mouseY, x, y, w, h);
        int c = enabled ? color : SystemTheme.TEXT_DIM;
        float sheen = Anim.loop(2.8f) * 1.6f - 0.3f;
        Shapes.pill(x, y, w, h).fillV(Gfx.alpha(c, hover ? 0.42f : 0.26f), Gfx.alpha(Gfx.shade(c, -0.4f), hover ? 0.3f : 0.16f))
                .border(0.9f, Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.25f), enabled ? 0.95f : 0.4f), Gfx.alpha(c, 0.3f))
                .glow(enabled ? 4 + (hover ? 3 : 0) : 0, Gfx.alpha(c, 0.3f))
                .sheen(enabled ? sheen : -2, 0.18f).draw(g);
        String text = enabled ? label : why;
        float size = enabled ? 7f : 6f;
        SysFont.spaced(g, SysFont.fit(text, size, true, w - 8), x + w / 2f, SysFont.centerY(y, h, size), size, enabled ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, enabled ? 1.4f : 0.3f, 0.5f);
        if (enabled) hits.add(new Object[]{action, x, y, w, h, null});
    }

    private static int gradeColor(ItemStack stack) {
        ItemGrade grade = ItemGrade.of(stack);
        return grade != null ? Gfx.opaque(grade.color()) : SystemTheme.ACCENT;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] hit = hits.get(i);
            if (!Ui.inside(mouseX, mouseY, (float) hit[1], (float) hit[2], (float) hit[3], (float) hit[4])) continue;
            switch ((String) hit[0]) {
                case "slot" -> choose((int) hit[5]);
                case "mode" -> {
                    boolean inf = (boolean) hit[5];
                    if (inf != infusing) {
                        infusing = inf;
                        modeAt = Anim.now();
                        SystemScreen.playClick();
                    }
                }
                case "ward" -> {
                    useWard = !useWard;
                    SystemScreen.playTick(1.5f);
                }
                case "element" -> {
                    element = (SkillElement) hit[5];
                    SystemScreen.playTick(1.7f);
                }
                case "refine" -> {
                    GearClient.send(ForgeActionPayload.REFINE, chosenSlot, useWard ? 1 : 0);
                    SystemScreen.playClick();
                }
                case "infuse" -> {
                    GearClient.send(ForgeActionPayload.INFUSE, chosenSlot, element.ordinal());
                    SystemScreen.playClick();
                }
                default -> {
                    continue;
                }
            }
            return true;
        }
        return false;
    }

    private void choose(int slot) {
        if (slot != chosenSlot) {
            chosenSlot = slot;
            chosenAt = Anim.now();
            SystemScreen.playTick(1.6f);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        for (Object[] hit : hits) {
            if ("list".equals(hit[0]) && Ui.inside(mouseX, mouseY, (float) hit[1], (float) hit[2], (float) hit[3], (float) hit[4])) {
                scroll -= (float) scrollY;
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ UI test driver

    /** Chooses a slot and mode (driver: {@code forge <slot> [refine|infuse] [element]}). */
    public static void show(int slot, boolean infuse, @Nullable SkillElement e) {
        chosenSlot = slot;
        infusing = infuse;
        if (e != null) element = e;
    }

    /** Presses the forge's button as if clicked (driver: {@code forgego}). */
    public static void press() {
        if (infusing) GearClient.send(ForgeActionPayload.INFUSE, chosenSlot, element.ordinal());
        else GearClient.send(ForgeActionPayload.REFINE, chosenSlot, useWard ? 1 : 0);
    }

    public static void setWard(boolean on) {
        useWard = on;
    }
}
