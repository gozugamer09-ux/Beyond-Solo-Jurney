package com.beyondsolo.client.crafting;

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
import com.beyondsolo.crafting.CraftingService;
import com.beyondsolo.crafting.SystemPotionItem;
import com.beyondsolo.crafting.SystemRecipes;
import com.beyondsolo.item.ElixirItem;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.network.CraftResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The CRAFT page of the System window. Categories and recipes on the left; the chosen recipe on the right: what it
 * makes, floating in a crucible of light, and what it takes (counted from the inventory and the System storage).
 * Crafting plays as a short fusion: every ingredient flies into the crucible along a curve, the crucible draws in
 * and flares, and the result springs out with a "+N" and a burst of rays. A refusal shakes the panel and says why.
 */
public class CraftTab implements SystemTab {
    private static final float LIST_W = 146;
    private static final float ROW_H = 21;
    /** How long the ingredients fly before the flare. */
    private static final float FLY = 0.55f;

    private static SystemRecipes.Category category = SystemRecipes.Category.POTIONS;
    private static String chosen = "";
    private static int amount = 1;

    private float scroll;
    private double chosenAt = -10;
    private double seenResult;
    private double revealAt = -10;
    private @Nullable CraftResultPayload shown;
    /** Where each ingredient was drawn last frame, for the flight. */
    private final List<float[]> needSpots = new ArrayList<>();
    private final List<ItemStack> flying = new ArrayList<>();
    private final List<Object[]> hits = new ArrayList<>();

    @Override
    public Component title() {
        return Component.translatable("tab.beyondsolo.craft");
    }

    @Override
    public boolean isVisible() {
        return ClientSystemData.get().hasSystem();
    }

    @Override
    public void onOpened() {
        seenResult = CraftClient.lastAt();
        CraftClient.requestStock();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        if (CraftClient.lastAt() > seenResult) {
            seenResult = CraftClient.lastAt();
            shown = CraftClient.last();
            boolean ok = shown != null && shown.made() > 0;
            // the ingredients always finish their flight before the result shows
            revealAt = ok ? Math.max(CraftClient.lastAt(), CraftClient.sentAt() + FLY) : CraftClient.lastAt();
        }
        List<SystemRecipes.Recipe> recipes = SystemRecipes.of(category);
        if (SystemRecipes.get(chosen) == null || SystemRecipes.get(chosen).category() != category) {
            chosen = recipes.isEmpty() ? "" : recipes.get(0).id();
        }
        categories(g, x, y, mouseX, mouseY);
        list(g, recipes, player.getInventory().items, x, y + 18, h - 18, mouseX, mouseY);
        float dx = x + LIST_W + 8;
        SystemRecipes.Recipe r = SystemRecipes.get(chosen);
        if (r != null) detail(g, r, player.getInventory().items, dx, y, x + w - dx, h, mouseX, mouseY);
    }

    private void categories(GuiGraphics g, float x, float y, int mouseX, int mouseY) {
        float px = x;
        SystemRecipes.Category[] all = SystemRecipes.Category.values();
        float pw = (LIST_W - 3 * (all.length - 1)) / all.length;
        for (SystemRecipes.Category c : all) {
            boolean on = c == category;
            boolean hover = !on && Ui.inside(mouseX, mouseY, px, y, pw, 13);
            int col = catColor(c);
            Shapes.pill(px, y, pw, 13).fill(Gfx.alpha(col, on ? 0.28f : hover ? 0.12f : 0.05f))
                    .border(0.75f, Gfx.alpha(col, on ? 0.95f : 0.35f)).glow(on ? 4 : 0, Gfx.alpha(col, 0.3f)).draw(g);
            String label = Component.translatable("craft.beyondsolo.cat." + c.name().toLowerCase()).getString();
            SysFont.spaced(g, SysFont.fit(label, 5.6f, true, pw - 4), px + pw / 2f, SysFont.centerY(y, 13, 5.6f), 5.6f,
                    on ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, 0.6f, 0.5f);
            hits.add(new Object[]{"cat", px, y, pw, 13f, c});
            px += pw + 3;
        }
    }

    private static int catColor(SystemRecipes.Category c) {
        return switch (c) {
            case POTIONS -> 0xFFFF6E8A;
            case FORGE -> 0xFFFFB04A;
            case CORES -> 0xFF5FD0FF;
            case GEAR -> 0xFFB57CF7;
        };
    }

    private void list(GuiGraphics g, List<SystemRecipes.Recipe> recipes, List<ItemStack> inv, float x, float y, float h, int mouseX, int mouseY) {
        int visible = (int) (h / ROW_H);
        int max = Math.max(0, recipes.size() - visible);
        scroll = Mth.clamp(scroll, 0, max);
        int rank = ClientSystemData.get().rank().tier();
        g.enableScissor((int) x - 2, (int) y - 1, (int) (x + LIST_W + 4), (int) (y + h));
        for (int i = 0; i < recipes.size(); i++) {
            float ry = y + (i - scroll) * ROW_H;
            if (ry < y - ROW_H || ry > y + h) continue;
            SystemRecipes.Recipe r = recipes.get(i);
            ItemStack out = r.display();
            int c = outputColor(out);
            boolean on = r.id().equals(chosen);
            boolean hover = Ui.inside(mouseX, mouseY, x, ry, LIST_W, ROW_H - 2) && mouseY < y + h;
            Ui.card(g, x, ry, LIST_W, ROW_H - 2, 5, c, on ? 0.6f : hover ? 0.3f : 0.08f);
            if (on) Shapes.pill(x + 1.5f, ry + 4, 2, ROW_H - 10).fill(c).glow(2, Gfx.alpha(c, 0.7f)).draw(g);
            g.renderItem(out, (int) x + 5, (int) ry + 1);
            String name = SysFont.fit(out.getHoverName().getString(), 6.4f, true, LIST_W - 56);
            SysFont.title(g, name, x + 25, ry + 3, 6.4f, Gfx.mix(c, 0xFFFFFFFF, 0.45f));
            boolean locked = rank < r.minRank().tier();
            int can = CraftingService.canMake(inv, CraftClient.storage(), r);
            String sub;
            int subColor;
            if (locked) {
                sub = Component.translatable("craft.beyondsolo.needs_rank", r.minRank().displayName()).getString();
                subColor = SystemTheme.DANGER;
            } else if (can > 0) {
                sub = Component.translatable("craft.beyondsolo.can_make", can >= CraftingService.MAX_TIMES ? "64+" : String.valueOf(can)).getString();
                subColor = SystemTheme.GOOD;
            } else {
                sub = Component.translatable("craft.beyondsolo.missing").getString();
                subColor = SystemTheme.TEXT_DIM;
            }
            SysFont.text(g, sub, x + 25, ry + 11, 5.4f, subColor);
            if (out.getCount() > 1) {
                String n = "x" + out.getCount();
                SysFont.title(g, n, x + LIST_W - 6 - SysFont.width(n, 6.4f, true), ry + 6, 6.4f, SystemTheme.TEXT_SOFT);
            } else if (locked) {
                Shapes.icon(Icons.LOCK, x + LIST_W - 10, ry + 9.5f, 7).fill(SystemTheme.TEXT_DIM).draw(g);
            } else if (can > 0) {
                Shapes.circle(x + LIST_W - 9, ry + 9.5f, 2.2f).fill(SystemTheme.GOOD).glow(2.5f, Gfx.alpha(SystemTheme.GOOD, 0.7f)).draw(g);
            }
            hits.add(new Object[]{"recipe", x, ry, LIST_W, ROW_H - 2, r.id()});
        }
        g.disableScissor();
        if (max > 0) {
            float knob = Math.max(12, h * visible / recipes.size());
            float ky = y + (h - knob) * scroll / max;
            Shapes.pill(x + LIST_W + 2, ky, 2, knob).fill(Gfx.alpha(SystemTheme.ACCENT, 0.5f)).draw(g);
        }
        hits.add(new Object[]{"list", x, y, LIST_W, h, null});
    }

    /** The colour a recipe's result glows in. */
    static int outputColor(ItemStack out) {
        ItemGrade grade = ItemGrade.of(out);
        if (grade != null) return Gfx.opaque(grade.color());
        int glow = CraftClientEvents.glowColor(out);
        if (glow >= 0) return Gfx.opaque(glow);
        if (out.getItem() instanceof SystemPotionItem p) return Gfx.opaque(p.color());
        if (out.getItem() instanceof ElixirItem) return 0xFF8BE28B;
        if (out.getItem() instanceof com.beyondsolo.gear.ForgeMaterialItem f && f.element() != null) return Gfx.opaque(f.element().color());
        return SystemTheme.ACCENT;
    }

    private void detail(GuiGraphics g, SystemRecipes.Recipe r, List<ItemStack> inv, float x, float y, float w, float h, int mouseX, int mouseY) {
        ItemStack out = r.display();
        int c = outputColor(out);
        float k = Anim.outCubic(Anim.progress(chosenAt, 0.3f));
        float old = Gfx.fade(k);
        g.pose().pushPose();
        g.pose().translate((1 - k) * 8, 0, 0);
        boolean ours = shown != null && shown.recipe().equals(r.id());
        float sinceReveal = Anim.since(revealAt);
        if (ours && shown.made() == 0 && sinceReveal >= 0 && sinceReveal < 0.45f) {
            float amp = 2.2f * (1 - sinceReveal / 0.45f);
            g.pose().translate(Math.sin(sinceReveal * 90) * amp, Math.cos(sinceReveal * 70) * amp * 0.5f, 0);
        }
        Ui.card(g, x, y, w, h, 9, c, 0.2f);
        // header: category label and the wallet
        SysFont.spaced(g, Component.translatable("craft.beyondsolo.cat." + r.category().name().toLowerCase()).getString(), x + 9, y + 8,
                5.6f, Gfx.alpha(catColor(r.category()), 0.9f), true, 1.2f, 0f);
        wallet(g, x + w - 7, y + 6);

        float cx = x + w / 2f;
        float cy = y + 48;
        crucible(g, r, out, cx, cy, c);

        // name and what it is
        String name = out.getHoverName().getString() + (out.getCount() > 1 ? "  x" + out.getCount() : "");
        SysFont.draw(g, SysFont.fit(name, 7.4f, true, w - 16), cx, cy + 29, 7.4f, Gfx.mix(c, 0xFFFFFFFF, 0.5f), true, 0.5f);
        String sub = r.gear() ? Component.translatable("craft.beyondsolo.gear_rolls").getString()
                : Component.translatable("craft.beyondsolo.rank_line", r.minRank().displayName()).getString();
        SysFont.draw(g, sub, cx, cy + 38.5f, 5.6f, SystemTheme.TEXT_DIM, false, 0.5f);

        // ingredients
        needs(g, r, inv, x, cy + 49, w);

        // amount, cost and the button
        float by = y + h - 25;
        int can = CraftingService.canMake(inv, CraftClient.storage(), r);
        boolean locked = ClientSystemData.get().rank().tier() < r.minRank().tier();
        amount = Mth.clamp(amount, 1, CraftingService.MAX_TIMES);
        stepper(g, x + 9, by, mouseX, mouseY);
        long gold = r.gold() * amount;
        boolean rich = EconomyClient.gold() >= gold;
        float gx = x + 9 + 58;
        if (gold > 0) goldChip(g, gx, by + 1.5f, gold, rich);
        String why = locked ? Component.translatable("craft.beyondsolo.needs_rank", r.minRank().displayName()).getString()
                : can < amount ? Component.translatable("craft.beyondsolo.missing").getString()
                : !rich ? Component.translatable("craft.beyondsolo.refuse.gold").getString() : null;
        float bw = 86;
        bigButton(g, x + w - 9 - bw, by, bw, Component.translatable("craft.beyondsolo.craft").getString(), why, c, mouseX, mouseY);

        // a refusal from the server, under the crucible
        if (ours && shown.made() == 0 && !shown.reason().isEmpty() && sinceReveal < 2.5f) {
            float a = 1 - Anim.clamp01((sinceReveal - 2f) / 0.5f);
            SysFont.draw(g, Component.translatable(shown.reason()).getString(), cx, y + h - 36, 6f, Gfx.alpha(SystemTheme.DANGER, a), true, 0.5f);
        }
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    /** The result floating in a ring of light; the fusion plays here. */
    private void crucible(GuiGraphics g, SystemRecipes.Recipe r, ItemStack out, float cx, float cy, int c) {
        boolean ours = shown != null && shown.recipe().equals(r.id());
        float sinceSent = Anim.since(CraftClient.sentAt());
        boolean waiting = sinceSent >= 0 && sinceSent < FLY + 0.3f && (revealAt < CraftClient.sentAt() || Anim.since(revealAt) < 0);
        boolean inFlight = waiting && sinceSent < FLY;
        float since = Anim.since(revealAt);
        boolean success = ours && shown.made() > 0 && since >= 0 && since < 1.4f;
        float t = Anim.loop(waiting ? 2.5f : 8f);

        // the crucible itself
        float draw = waiting ? Anim.clamp01(sinceSent / FLY) : 0;
        Shapes.circle(cx, cy, 30).fill(0).glow(16 + 10 * draw, Gfx.alpha(c, 0.16f + 0.3f * draw)).draw(g);
        Shapes.ring(cx, cy, 24 - 4 * draw, 0.8f).fill(Gfx.alpha(c, 0.5f)).dashes(18, 0.45f).rotation(t).draw(g);
        Shapes.ring(cx, cy, 17 - 3 * draw, 0.6f).fill(Gfx.alpha(c, 0.3f)).dashes(6, 0.6f).rotation(-t * 1.4f).draw(g);
        Shapes.circle(cx, cy, 12).fillV(Gfx.alpha(c, 0.18f + 0.3f * draw), Gfx.alpha(Gfx.shade(c, -0.5f), 0.1f))
                .border(0.6f, Gfx.alpha(c, 0.5f)).draw(g);

        // ingredients flying in along curves
        if (inFlight && !flying.isEmpty()) {
            for (int i = 0; i < flying.size() && i < needSpots.size(); i++) {
                float[] from = needSpots.get(i);
                float delay = i * 0.06f;
                float p = Anim.clamp01((sinceSent - delay) / (FLY - 0.12f));
                if (p <= 0 || p >= 1) continue;
                float e = Anim.inOutCubic(p);
                float side = (i % 2 == 0 ? -1 : 1) * 26;
                float mx = (from[0] + cx) / 2f + side, my = Math.min(from[1], cy) - 14;
                float bx = (1 - e) * (1 - e) * from[0] + 2 * (1 - e) * e * mx + e * e * cx;
                float bys = (1 - e) * (1 - e) * from[1] + 2 * (1 - e) * e * my + e * e * cy;
                float s = 1f - 0.55f * e;
                Shapes.circle(bx, bys, 6 * s).fill(0).glow(6, Gfx.alpha(c, 0.5f * (1 - e * 0.5f))).draw(g);
                g.pose().pushPose();
                g.pose().translate(bx, bys, 160);
                g.pose().scale(s, s, 1);
                g.renderItem(flying.get(i), -8, -8);
                g.pose().popPose();
                // a short trail
                for (int k = 1; k <= 4; k++) {
                    float pe = Anim.inOutCubic(Anim.clamp01(p - k * 0.04f));
                    float tx = (1 - pe) * (1 - pe) * from[0] + 2 * (1 - pe) * pe * mx + pe * pe * cx;
                    float ty = (1 - pe) * (1 - pe) * from[1] + 2 * (1 - pe) * pe * my + pe * pe * cy;
                    Shapes.circle(tx, ty, 1.6f - k * 0.25f).fill(Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.5f), 0.7f - k * 0.15f)).draw(g);
                }
            }
        }

        // the flare and the result springing out
        float scale = 2f;
        float bob = (float) Math.sin(Anim.loop(2.6f) * Math.PI * 2) * 1.2f;
        if (waiting) scale *= 1 - 0.35f * draw;
        if (success) {
            float f = Anim.clamp01(since / 0.5f);
            Shapes.rays(cx, cy, 34 + 12 * Anim.outCubic(f), 14).fill(Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.4f), 0.6f * (1 - Anim.clamp01(since / 1.1f))))
                    .rotation(since * 0.15f).draw(g);
            Shapes.ring(cx, cy, 10 + 34 * Anim.outCubic(f), 1.6f * (1 - f) + 0.2f).fill(Gfx.alpha(0xFFFFFFFF, 1 - f)).glow(5, Gfx.alpha(c, 0.7f * (1 - f))).draw(g);
            Shapes.circle(cx, cy, 14).fill(0).glow(22 * (1 - f), Gfx.alpha(0xFFFFFFFF, 0.7f * (1 - f))).draw(g);
            scale *= 0.65f + 0.35f * Anim.outBackStrong(Anim.clamp01(since / 0.45f));
            // +N rising
            float rise = Anim.outCubic(Anim.clamp01(since / 0.9f));
            float a = 1 - Anim.clamp01((since - 0.9f) / 0.5f);
            int total = shown.made() * out.getCount();
            SysFont.draw(g, "+" + total, cx + 22, cy - 14 - 10 * rise, 9f, Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.55f), a), true, 0f);
        }
        g.pose().pushPose();
        g.pose().translate(cx, cy + bob, 150);
        g.pose().scale(scale, scale, 1);
        g.renderItem(out, -8, -8);
        g.pose().popPose();
    }

    private void needs(GuiGraphics g, SystemRecipes.Recipe r, List<ItemStack> inv, float x, float y, float w) {
        List<SystemRecipes.Need> needs = r.needs();
        float slotW = Math.min(64, (w - 16) / needs.size());
        float total = slotW * needs.size();
        float sx = x + (w - total) / 2f;
        boolean keepSpots = Anim.since(CraftClient.sentAt()) < FLY;
        if (!keepSpots) {
            needSpots.clear();
            flying.clear();
        }
        for (int i = 0; i < needs.size(); i++) {
            SystemRecipes.Need n = needs.get(i);
            float nx = sx + i * slotW + slotW / 2f;
            int have = CraftingService.count(inv, n) + CraftingService.count(CraftClient.storage(), n);
            int want = n.count() * amount;
            boolean ok = have >= want;
            int col = ok ? SystemTheme.GOOD : SystemTheme.DANGER;
            ItemStack icon = n.icon().get();
            int ic = outputColor(icon);
            Shapes.box(nx - 11, y, 22, 22).radius(6).fill(Gfx.alpha(ic, 0.12f)).border(0.75f, Gfx.alpha(ok ? ic : SystemTheme.DANGER, 0.55f)).draw(g);
            g.renderItem(icon, (int) (nx - 8), (int) y + 3);
            String cnt = have + "/" + want;
            SysFont.draw(g, cnt, nx, y + 25, 6f, col, true, 0.5f);
            String label = SysFont.fit(n.name().getString(), 5.2f, false, slotW - 4);
            SysFont.draw(g, label, nx, y + 33, 5.2f, SystemTheme.TEXT_DIM, false, 0.5f);
            if (!keepSpots) {
                needSpots.add(new float[]{nx, y + 11});
                flying.add(icon);
            }
        }
    }

    private void stepper(GuiGraphics g, float x, float y, int mouseX, int mouseY) {
        float h = 17;
        Shapes.pill(x, y, 52, h).fill(Gfx.alpha(SystemTheme.ACCENT, 0.08f)).border(0.75f, Gfx.alpha(SystemTheme.ACCENT, 0.4f)).draw(g);
        for (int i = 0; i < 2; i++) {
            float bx = i == 0 ? x : x + 52 - 15;
            boolean hover = Ui.inside(mouseX, mouseY, bx, y, 15, h);
            Shapes.circle(bx + 7.5f, y + h / 2f, 5.5f).fill(Gfx.alpha(SystemTheme.ACCENT, hover ? 0.35f : 0.12f)).draw(g);
            SysFont.draw(g, i == 0 ? "-" : "+", bx + 7.5f, SysFont.centerY(y, h, 8f), 8f, SystemTheme.TEXT, true, 0.5f);
            hits.add(new Object[]{i == 0 ? "less" : "more", bx, y, 15f, h, null});
        }
        SysFont.draw(g, "x" + amount, x + 26, SysFont.centerY(y, h, 7f), 7f, SystemTheme.TEXT, true, 0.5f);
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

    private void goldChip(GuiGraphics g, float x, float y, long gold, boolean ok) {
        String s = EconomyClient.format(gold) + " G";
        float w = SysFont.width(s, 6.2f, true) + 18;
        int c = ok ? GoldHud.GOLD : SystemTheme.DANGER;
        Shapes.pill(x, y, w, 14).fill(Gfx.alpha(c, 0.1f)).border(0.75f, Gfx.alpha(c, 0.5f)).draw(g);
        Shapes.circle(x + 7, y + 7, 3.6f).fillV(GoldHud.GOLD, GoldHud.GOLD_DEEP).draw(g);
        SysFont.title(g, s, x + 13, SysFont.centerY(y, 14, 6.2f), 6.2f, ok ? 0xFFFFE7A6 : SystemTheme.DANGER);
    }

    private void bigButton(GuiGraphics g, float x, float y, float w, String label, @Nullable String why, int color, int mouseX, int mouseY) {
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
        SysFont.spaced(g, SysFont.fit(text, size, true, w - 8), x + w / 2f, SysFont.centerY(y, h, size), size,
                enabled ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, enabled ? 1.4f : 0.3f, 0.5f);
        if (enabled) hits.add(new Object[]{"craft", x, y, w, h, null});
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] hit = hits.get(i);
            if (!Ui.inside(mouseX, mouseY, (float) hit[1], (float) hit[2], (float) hit[3], (float) hit[4])) continue;
            switch ((String) hit[0]) {
                case "cat" -> {
                    SystemRecipes.Category c = (SystemRecipes.Category) hit[5];
                    if (c != category) {
                        category = c;
                        scroll = 0;
                        chosenAt = Anim.now();
                        SystemScreen.playClick();
                    }
                }
                case "recipe" -> choose((String) hit[5]);
                case "less" -> {
                    amount = Math.max(1, amount - (net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 10 : 1));
                    SystemScreen.playTick(1.4f);
                }
                case "more" -> {
                    amount = Math.min(CraftingService.MAX_TIMES, amount + (net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 10 : 1));
                    SystemScreen.playTick(1.7f);
                }
                case "craft" -> press();
                default -> {
                    continue;
                }
            }
            return true;
        }
        return false;
    }

    private void choose(String id) {
        if (!id.equals(chosen)) {
            chosen = id;
            chosenAt = Anim.now();
            amount = 1;
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

    /** Chooses a recipe (driver: {@code craft <recipe> [amount]}). */
    public static void show(String id, int times) {
        SystemRecipes.Recipe r = SystemRecipes.get(id);
        if (r == null) return;
        category = r.category();
        chosen = id;
        amount = Math.max(1, times);
    }

    /** Presses CRAFT (driver: {@code craftgo}). */
    public static void press() {
        if (chosen.isEmpty()) return;
        CraftClient.craft(chosen, amount);
        SystemScreen.playClick();
    }

}
