package com.beyondsolo.client.economy;

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
import com.beyondsolo.economy.Economy;
import com.beyondsolo.economy.SellPrices;
import com.beyondsolo.economy.ShopCatalog;
import com.beyondsolo.economy.SystemStorage;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.network.EconomyActionPayload;
import com.beyondsolo.registry.ModComponents;
import com.beyondsolo.system.PlayerClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The SHOP page of the System window. BUY: categories on the left (today's deals first), the wares in the
 * middle (locked ones show the rank they need), the chosen one on the right turning on a lit pedestal with its
 * price, how many to buy and today's stock. A purchase sends coins from the wallet into the item, which lands
 * with a ring of light and an ACQUIRED stamp. SELL: what the System will pay for the things in your inventory;
 * selling sends the coins the other way.
 */
public class ShopTab implements SystemTab {
    private static final float RAIL_W = 66;
    private static final float LIST_W = 164;
    private static final float ROW_H = 22;

    private static boolean selling;
    private static ShopCatalog.Category category = ShopCatalog.Category.FEATURED;
    private static String chosen = "";
    private static int chosenSlot = -1;

    private float scroll;
    private int lots = 1;
    private double chosenAt = -10;
    private double modeAt = -10;
    private double doneAt = -10;
    private long doneAmount;
    private boolean doneWasSale;
    private double seenChange;
    private float railIndicator = Float.NaN;
    private float[] walletChip = {0, 0, 0, 0};
    private float[] pedestal = {0, 0};
    private final List<Object[]> hits = new ArrayList<>();

    @Override
    public Component title() {
        return Component.translatable("tab.beyondsolo.shop");
    }

    @Override
    public boolean isVisible() {
        return ClientSystemData.get().hasSystem();
    }

    @Override
    public void onOpened() {
        seenChange = EconomyClient.lastChangeAt();
        modeAt = Anim.now();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        // a purchase or sale just came back from the server
        if (EconomyClient.lastChangeAt() > seenChange) {
            seenChange = EconomyClient.lastChangeAt();
            Economy.Reason r = EconomyClient.lastReason();
            if (r == Economy.Reason.PURCHASE || r == Economy.Reason.SALE) {
                doneAt = Anim.now();
                doneAmount = Math.abs(EconomyClient.lastDelta());
                doneWasSale = r == Economy.Reason.SALE;
            }
        }

        topBar(g, x, y, w, mouseX, mouseY);
        float top = y + 20;
        float bodyH = h - 20;
        float k = Anim.outCubic(Anim.progress(modeAt, 0.25f));
        float old = Gfx.fade(k);
        if (selling) sellView(g, x, top, w, bodyH, mouseX, mouseY);
        else buyView(g, x, top, w, bodyH, mouseX, mouseY);
        Gfx.setAlpha(old);
        coinsFlight(g);
    }

    private void topBar(GuiGraphics g, float x, float y, float w, int mouseX, int mouseY) {
        float px = x;
        for (int i = 0; i < 2; i++) {
            boolean on = (i == 1) == selling;
            String label = Component.translatable(i == 0 ? "shop.beyondsolo.buy" : "shop.beyondsolo.sell").getString();
            float pw = SysFont.spacedWidth(label, 6.8f, true, 1f) + 26;
            boolean hover = !on && Ui.inside(mouseX, mouseY, px, y, pw, 14);
            int c = i == 0 ? SystemTheme.ACCENT : GoldHud.GOLD;
            Shapes.pill(px, y, pw, 14).fill(Gfx.alpha(c, on ? 0.28f : hover ? 0.12f : 0.05f))
                    .border(0.75f, Gfx.alpha(c, on ? 0.95f : 0.35f)).glow(on ? 4 : 0, Gfx.alpha(c, 0.3f)).draw(g);
            Shapes.icon(i == 0 ? Icons.CART : Icons.TAG, px + 9, y + 7, 8).fill(on ? Gfx.mix(c, 0xFFFFFFFF, 0.4f) : SystemTheme.TEXT_SOFT).draw(g);
            SysFont.spaced(g, label, px + 16, SysFont.centerY(y, 14, 6.8f), 6.8f, on ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, 1f, 0f);
            hits.add(new Object[]{"mode", px, y, pw, 14f, i == 1});
            px += pw + 4;
        }
        // today's day, small, between
        String day = Component.translatable("shop.beyondsolo.day", EconomyClient.day() + 1).getString();
        SysFont.text(g, day, px + 6, y + 4, 6f, SystemTheme.TEXT_DIM);

        // the wallet
        String gold = EconomyClient.format(EconomyClient.gold());
        float gw = SysFont.width(gold, 8f, true) + 30;
        float gx = x + w - gw;
        float flash = 1 - Anim.clamp01(Anim.since(EconomyClient.lastChangeAt()) / 0.7f);
        Shapes.pill(gx, y - 1, gw, 16).fillV(Gfx.alpha(GoldHud.GOLD, 0.16f + 0.2f * flash), Gfx.alpha(GoldHud.GOLD_DEEP, 0.08f))
                .border(0.75f, Gfx.alpha(GoldHud.GOLD, 0.6f + 0.4f * flash)).glow(3 + 6 * flash, Gfx.alpha(GoldHud.GOLD, 0.2f + 0.4f * flash)).draw(g);
        Shapes.circle(gx + 8, y + 7, 5.5f).fillV(GoldHud.GOLD, GoldHud.GOLD_DEEP).border(0.6f, 0xFFFFF0C0, 0xFFB86A10).draw(g);
        Shapes.icon(Icons.COINS, gx + 8, y + 7, 7).fill(0xFF5A3200).draw(g);
        SysFont.title(g, gold, gx + 16, SysFont.centerY(y - 1, 16, 8f), 8f, 0xFFFFF4D6);
        SysFont.title(g, "G", gx + 17 + SysFont.width(gold, 8f, true), SysFont.centerY(y - 1, 16, 6f) + 0.5f, 6f, GoldHud.GOLD);
        walletChip = new float[]{gx + 8, y + 7};
    }

    // ------------------------------------------------------------------ BUY

    private void buyView(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        rail(g, x, y, h, mouseX, mouseY);
        List<ShopCatalog.Entry> entries = ShopCatalog.of(category, EconomyClient.day());
        ShopCatalog.Entry sel = null;
        for (ShopCatalog.Entry e : entries) if (e.id().equals(chosen)) sel = e;
        if (sel == null && !entries.isEmpty()) {
            sel = entries.get(0);
            choose(sel.id());
        }
        float lx = x + RAIL_W + 6;
        list(g, lx, y, h, entries, mouseX, mouseY);
        float dx = lx + LIST_W + 8;
        if (sel != null) buyDetail(g, sel, dx, y, x + w - dx, h, mouseX, mouseY);
    }

    private void rail(GuiGraphics g, float x, float y, float h, int mouseX, int mouseY) {
        ShopCatalog.Category[] cats = ShopCatalog.Category.values();
        float rowH = 18;
        int sel = category.ordinal();
        float target = y + sel * (rowH + 2);
        if (Float.isNaN(railIndicator)) railIndicator = target;
        railIndicator = Anim.approach(railIndicator, target, 18f);
        Shapes.box(x, railIndicator, RAIL_W, rowH).radius(5)
                .fillH(Gfx.alpha(SystemTheme.ACCENT, 0.3f), Gfx.alpha(SystemTheme.ACCENT, 0.05f))
                .border(0.75f, Gfx.alpha(SystemTheme.ACCENT, 0.7f), Gfx.alpha(SystemTheme.ACCENT, 0.2f)).draw(g);
        Shapes.pill(x + 1.5f, railIndicator + 4, 2, rowH - 8).fill(SystemTheme.ACCENT_BRIGHT).glow(3, Gfx.alpha(SystemTheme.ACCENT, 0.8f)).draw(g);
        for (int i = 0; i < cats.length; i++) {
            float ry = y + i * (rowH + 2);
            boolean on = i == sel;
            boolean hover = !on && Ui.inside(mouseX, mouseY, x, ry, RAIL_W, rowH);
            if (hover) Shapes.box(x, ry, RAIL_W, rowH).radius(5).fill(0x14FFFFFF).draw(g);
            int c = cats[i] == ShopCatalog.Category.FEATURED ? GoldHud.GOLD : on ? SystemTheme.ACCENT_BRIGHT : SystemTheme.TEXT_SOFT;
            Shapes.icon(categoryIcon(cats[i]), x + 11, ry + rowH / 2f, 9).fill(c).glow(on ? 2 : 0, Gfx.alpha(c, 0.6f)).draw(g);
            SysFont.draw(g, Component.translatable("shop.beyondsolo.cat." + cats[i].name().toLowerCase()).getString(), x + 20, SysFont.centerY(ry, rowH, 6.8f),
                    6.8f, on ? SystemTheme.TEXT : c, on, 0f);
            hits.add(new Object[]{"cat", x, ry, RAIL_W, rowH, cats[i]});
        }
        // the storage expansion line under the rail
        float sy = y + cats.length * (rowH + 2) + 6;
        Ui.divider(g, x + 4, sy, RAIL_W - 8, Gfx.alpha(SystemTheme.ACCENT, 0.3f));
        SysFont.text(g, Component.translatable("shop.beyondsolo.storage_line").getString(), x + 4, sy + 5, 5.6f, SystemTheme.TEXT_DIM);
        SysFont.title(g, EconomyClient.capacity() + " / " + com.beyondsolo.economy.HunterData.MAX_SLOTS, x + 4, sy + 13, 7f, SystemTheme.TEXT_SOFT);
    }

    private static ResourceLocation categoryIcon(ShopCatalog.Category c) {
        return switch (c) {
            case FEATURED -> Icons.SPARKLE;
            case SUPPLIES -> Icons.PACKAGE;
            case REMEDIES -> Icons.FLASK;
            case WEAPONS -> Icons.of(PlayerClass.WARRIOR);
            case ARMOR -> Icons.SHIELD;
            case SPECIAL -> Icons.DIAMOND;
        };
    }

    private void list(GuiGraphics g, float x, float y, float h, List<ShopCatalog.Entry> entries, int mouseX, int mouseY) {
        int visible = (int) (h / ROW_H);
        int max = Math.max(0, entries.size() - visible);
        scroll = Mth.clamp(scroll, 0, max);
        long day = EconomyClient.day();
        g.enableScissor((int) x - 2, (int) y - 1, (int) (x + LIST_W + 4), (int) (y + h));
        for (int i = 0; i < entries.size(); i++) {
            float ry = y + (i - scroll) * ROW_H;
            if (ry < y - ROW_H || ry > y + h) continue;
            ShopCatalog.Entry e = entries.get(i);
            ItemStack stack = e.display();
            boolean locked = locked(e);
            boolean on = e.id().equals(chosen);
            boolean hover = Ui.inside(mouseX, mouseY, x, ry, LIST_W, ROW_H - 2) && mouseY < y + h;
            int gc = gradeColor(stack);
            Ui.card(g, x, ry, LIST_W, ROW_H - 2, 5, gc, on ? 0.6f : hover ? 0.3f : 0.08f);
            if (on) Shapes.pill(x + 1.5f, ry + 4, 2, ROW_H - 10).fill(gc).glow(2, Gfx.alpha(gc, 0.7f)).draw(g);
            // icon
            Shapes.box(x + 5, ry + 1.5f, 17, 17).radius(4).fill(Gfx.alpha(gc, 0.14f)).border(0.5f, Gfx.alpha(gc, 0.4f)).draw(g);
            g.renderItem(stack, (int) x + 5, (int) ry + 2);
            if (stack.getCount() > 1) g.renderItemDecorations(Minecraft.getInstance().font, stack, (int) x + 5, (int) ry + 2);
            float nameW = LIST_W - 74;
            String name = SysFont.fit(e.special() == ShopCatalog.Special.EXPANSION ? Component.translatable("shop.beyondsolo.expansion").getString()
                    : stack.getHoverName().getString(), 6.8f, true, nameW);
            SysFont.title(g, name, x + 26, ry + 3, 6.8f, locked ? SystemTheme.TEXT_DIM : Gfx.mix(gc, 0xFFFFFFFF, 0.45f));
            String sub = locked ? Component.translatable("shop.beyondsolo.needs_rank", e.minRank().displayName()).getString()
                    : e.dailyLimit() > 0 ? Component.translatable("shop.beyondsolo.stock", e.dailyLimit() - EconomyClient.bought(e.id()), e.dailyLimit()).getString()
                    : Component.translatable("shop.beyondsolo.cat." + e.category().name().toLowerCase()).getString();
            SysFont.text(g, sub, x + 26, ry + 11.5f, 5.6f, locked ? Gfx.alpha(SystemTheme.DANGER, 0.85f) : SystemTheme.TEXT_DIM);
            // price
            long price = EconomyClient.price(e);
            boolean deal = ShopCatalog.isFeatured(e, day) && e.special() == ShopCatalog.Special.NONE;
            boolean maxed = e.special() == ShopCatalog.Special.EXPANSION && EconomyClient.expansions() >= SystemStorage.MAX_EXPANSIONS;
            String ps = maxed ? "MAX" : EconomyClient.format(price);
            float pw = SysFont.width(ps, 6.8f, true);
            int pc = locked ? SystemTheme.TEXT_DIM : EconomyClient.gold() >= price ? 0xFFFFE7A6 : SystemTheme.DANGER;
            SysFont.title(g, ps, x + LIST_W - 6 - pw, ry + (deal ? 2.5f : 6), 6.8f, pc);
            if (!maxed) Shapes.circle(x + LIST_W - 10 - pw, ry + (deal ? 5 : 8.5f), 2.4f).fill(GoldHud.GOLD).draw(g);
            if (deal) {
                String was = EconomyClient.format(e.price());
                float ww = SysFont.width(was, 5.6f, false);
                SysFont.text(g, was, x + LIST_W - 6 - ww, ry + 11, 5.6f, SystemTheme.TEXT_DIM);
                Shapes.box(x + LIST_W - 6 - ww - 0.5f, ry + 13.6f, ww + 1, 0.6f).fill(SystemTheme.TEXT_DIM).draw(g);
                float tagX = x + LIST_W - 10 - ww - 22;
                Shapes.pill(tagX, ry + 10.5f, 19, 7).fill(Gfx.alpha(GoldHud.GOLD, 0.25f)).border(0.5f, GoldHud.GOLD).draw(g);
                SysFont.draw(g, "-" + Math.round((1 - ShopCatalog.DEAL) * 100) + "%", tagX + 9.5f, ry + 11.3f, 5f, 0xFFFFE7A6, true, 0.5f);
            }
            if (locked) Shapes.icon(Icons.LOCK, x + 19, ry + 15, 6).fill(SystemTheme.DANGER).draw(g);
            hits.add(new Object[]{"entry", x, ry, LIST_W, ROW_H - 2, e.id()});
        }
        g.disableScissor();
        if (max > 0) {
            float knob = Math.max(12, h * visible / entries.size());
            float ky = y + (h - knob) * scroll / max;
            Shapes.pill(x + LIST_W + 2, ky, 2, knob).fill(Gfx.alpha(SystemTheme.ACCENT, 0.5f)).draw(g);
        }
        hits.add(new Object[]{"list", x, y, LIST_W, h, null});
    }

    private void buyDetail(GuiGraphics g, ShopCatalog.Entry e, float x, float y, float w, float h, int mouseX, int mouseY) {
        ItemStack stack = e.display();
        int gc = gradeColor(stack);
        float k = Anim.outCubic(Anim.progress(chosenAt, 0.3f));
        float old = Gfx.fade(k);
        g.pose().pushPose();
        g.pose().translate((1 - k) * 8, 0, 0);
        Ui.card(g, x, y, w, h, 9, gc, 0.22f);

        float cx = x + w / 2f;
        float py = y + 30;
        pedestal(g, stack, cx, py, gc, w);

        // name and kind
        float ty = py + 28;
        String name = e.special() == ShopCatalog.Special.EXPANSION ? Component.translatable("shop.beyondsolo.expansion").getString() : stack.getHoverName().getString();
        List<SysFont.Line> lines = SysFont.wrap(Component.literal(name), 7.5f, true, w - 12);
        for (int i = 0; i < Math.min(2, lines.size()); i++) SysFont.draw(g, lines.get(i), cx, ty + i * 9, Gfx.mix(gc, 0xFFFFFFFF, 0.5f), 0.5f);
        ty += Math.min(2, lines.size()) * 9 + 1;
        String kind = describe(e, stack);
        SysFont.draw(g, kind, cx, ty, 5.6f, SystemTheme.TEXT_DIM, false, 0.5f);
        ty += 9;

        // price x lots
        boolean locked = locked(e);
        boolean maxed = e.special() == ShopCatalog.Special.EXPANSION && EconomyClient.expansions() >= SystemStorage.MAX_EXPANSIONS;
        int left = e.dailyLimit() > 0 ? e.dailyLimit() - EconomyClient.bought(e.id()) : Integer.MAX_VALUE;
        int maxLots = e.stackable() ? Math.min(64, left) : Math.min(1, left);
        lots = Mth.clamp(lots, 1, Math.max(1, maxLots));
        long total = EconomyClient.price(e) * lots;
        float qy = y + h - 42;
        if (e.stackable() && !locked) {
            float qx = cx - 26;
            stepper(g, qx, qy, "−", mouseX, mouseY, -1, lots > 1);
            SysFont.draw(g, "×" + lots, cx, qy + 1.5f, 7f, SystemTheme.TEXT, true, 0.5f);
            stepper(g, qx + 42, qy, "+", mouseX, mouseY, 1, lots < maxLots);
        } else if (e.dailyLimit() > 0) {
            SysFont.draw(g, Component.translatable("shop.beyondsolo.stock", Math.max(0, left), e.dailyLimit()).getString(), cx, qy + 2, 6f, SystemTheme.TEXT_SOFT, false, 0.5f);
        }
        String ts = maxed ? Component.translatable("shop.beyondsolo.maxed").getString() : EconomyClient.format(total) + " G";
        boolean afford = EconomyClient.gold() >= total;
        SysFont.draw(g, ts, cx, qy - 11, 8f, maxed || locked ? SystemTheme.TEXT_DIM : afford ? 0xFFFFE7A6 : SystemTheme.DANGER, true, 0.5f);

        // the buy button
        String why = locked ? Component.translatable("shop.beyondsolo.needs_rank", e.minRank().displayName()).getString()
                : maxed ? Component.translatable("shop.beyondsolo.maxed").getString()
                : left <= 0 ? Component.translatable("shop.beyondsolo.sold_out").getString()
                : !afford ? Component.translatable("shop.beyondsolo.short", EconomyClient.format(total - EconomyClient.gold())).getString() : null;
        bigButton(g, x + 8, y + h - 24, w - 16, Component.translatable("shop.beyondsolo.buy_button").getString(), why, SystemTheme.ACCENT, mouseX, mouseY, "buy");

        stamp(g, cx, py, false);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    private String describe(ShopCatalog.Entry e, ItemStack stack) {
        if (e.special() == ShopCatalog.Special.EXPANSION) {
            return Component.translatable("shop.beyondsolo.expansion.desc", SystemStorage.PER_EXPANSION, EconomyClient.expansions(), SystemStorage.MAX_EXPANSIONS).getString();
        }
        if (e.special() == ShopCatalog.Special.LICENSE) return Component.translatable("shop.beyondsolo.license.desc").getString();
        ItemGrade grade = stack.get(ModComponents.GRADE.get());
        String cat = Component.translatable("shop.beyondsolo.cat." + e.category().name().toLowerCase()).getString();
        if (grade != null) return grade.displayName().getString() + "  ·  " + cat;
        return stack.getCount() > 1 ? Component.translatable("shop.beyondsolo.lot", stack.getCount()).getString() + "  ·  " + cat : cat;
    }

    // ------------------------------------------------------------------ SELL

    private void sellView(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        List<Integer> slots = new ArrayList<>();
        long all = 0;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack s = player.getInventory().items.get(i);
            long v = SellPrices.of(s);
            if (v > 0) {
                slots.add(i);
                all += v;
            }
        }
        // left: what it's all worth
        Ui.card(g, x, y, RAIL_W, 62, 7, GoldHud.GOLD, 0.2f);
        Shapes.icon(Icons.TAG, x + RAIL_W / 2f, y + 13, 12).fill(GoldHud.GOLD).glow(3, Gfx.alpha(GoldHud.GOLD, 0.5f)).draw(g);
        SysFont.draw(g, Component.translatable("shop.beyondsolo.sell_worth").getString(), x + RAIL_W / 2f, y + 24, 5.8f, SystemTheme.TEXT_SOFT, false, 0.5f);
        SysFont.draw(g, EconomyClient.format(all) + " G", x + RAIL_W / 2f, y + 33, 8f, 0xFFFFE7A6, true, 0.5f);
        SysFont.draw(g, Component.translatable("shop.beyondsolo.sell_count", slots.size()).getString(), x + RAIL_W / 2f, y + 45, 5.8f, SystemTheme.TEXT_DIM, false, 0.5f);
        List<SysFont.Line> note = SysFont.wrap(Component.translatable("shop.beyondsolo.sell_note"), 5.6f, false, RAIL_W - 4);
        for (int i = 0; i < note.size(); i++) SysFont.draw(g, note.get(i), x + 2, y + 70 + i * 7.5f, SystemTheme.TEXT_DIM, 0f);

        float lx = x + RAIL_W + 6;
        if (!slots.contains(chosenSlot)) chosenSlot = slots.isEmpty() ? -1 : slots.get(0);
        int visible = (int) (h / ROW_H);
        int max = Math.max(0, slots.size() - visible);
        scroll = Mth.clamp(scroll, 0, max);
        if (slots.isEmpty()) {
            Shapes.icon(Icons.PACKAGE, lx + LIST_W / 2f, y + h / 2f - 12, 22).fill(0x405FD0FF).draw(g);
            List<SysFont.Line> lines = SysFont.wrap(Component.translatable("shop.beyondsolo.sell_empty"), 6.5f, false, LIST_W - 20);
            for (int i = 0; i < lines.size(); i++) SysFont.draw(g, lines.get(i), lx + LIST_W / 2f, y + h / 2f + 6 + i * 8.5f, SystemTheme.TEXT_SOFT, 0.5f);
        }
        g.enableScissor((int) lx - 2, (int) y - 1, (int) (lx + LIST_W + 4), (int) (y + h));
        for (int i = 0; i < slots.size(); i++) {
            float ry = y + (i - scroll) * ROW_H;
            if (ry < y - ROW_H || ry > y + h) continue;
            int slot = slots.get(i);
            ItemStack s = player.getInventory().items.get(slot);
            int gc = gradeColor(s);
            boolean on = slot == chosenSlot;
            boolean hover = Ui.inside(mouseX, mouseY, lx, ry, LIST_W, ROW_H - 2) && mouseY < y + h;
            Ui.card(g, lx, ry, LIST_W, ROW_H - 2, 5, gc, on ? 0.6f : hover ? 0.3f : 0.08f);
            if (on) Shapes.pill(lx + 1.5f, ry + 4, 2, ROW_H - 10).fill(gc).glow(2, Gfx.alpha(gc, 0.7f)).draw(g);
            Shapes.box(lx + 5, ry + 1.5f, 17, 17).radius(4).fill(Gfx.alpha(gc, 0.14f)).border(0.5f, Gfx.alpha(gc, 0.4f)).draw(g);
            g.renderItem(s, (int) lx + 5, (int) ry + 2);
            g.renderItemDecorations(Minecraft.getInstance().font, s, (int) lx + 5, (int) ry + 2);
            SysFont.title(g, SysFont.fit(s.getHoverName().getString(), 6.8f, true, LIST_W - 74), lx + 26, ry + 3, 6.8f, Gfx.mix(gc, 0xFFFFFFFF, 0.45f));
            SysFont.text(g, Component.translatable("shop.beyondsolo.each", EconomyClient.format(SellPrices.each(s))).getString(), lx + 26, ry + 11.5f, 5.6f, SystemTheme.TEXT_DIM);
            String ps = "+" + EconomyClient.format(SellPrices.of(s));
            float pw = SysFont.width(ps, 6.8f, true);
            SysFont.title(g, ps, lx + LIST_W - 6 - pw, ry + 6, 6.8f, 0xFFFFE7A6);
            hits.add(new Object[]{"slot", lx, ry, LIST_W, ROW_H - 2, slot});
        }
        g.disableScissor();
        if (max > 0) {
            float knob = Math.max(12, h * visible / slots.size());
            float ky = y + (h - knob) * scroll / max;
            Shapes.pill(lx + LIST_W + 2, ky, 2, knob).fill(Gfx.alpha(GoldHud.GOLD, 0.5f)).draw(g);
        }
        hits.add(new Object[]{"list", lx, y, LIST_W, h, null});

        float dx = lx + LIST_W + 8;
        float dw = x + w - dx;
        if (chosenSlot >= 0) {
            ItemStack s = player.getInventory().items.get(chosenSlot);
            int gc = gradeColor(s);
            Ui.card(g, dx, y, dw, h, 9, GoldHud.GOLD, 0.18f);
            float cx = dx + dw / 2f;
            float py = y + 30;
            pedestal(g, s, cx, py, gc, dw);
            float ty = py + 28;
            List<SysFont.Line> lines = SysFont.wrap(s.getHoverName(), 7.5f, true, dw - 12);
            for (int i = 0; i < Math.min(2, lines.size()); i++) SysFont.draw(g, lines.get(i), cx, ty + i * 9, Gfx.mix(gc, 0xFFFFFFFF, 0.5f), 0.5f);
            ty += Math.min(2, lines.size()) * 9 + 3;
            long same = 0;
            for (ItemStack o : player.getInventory().items) if (!o.isEmpty() && ItemStack.isSameItem(o, s)) same += SellPrices.of(o);
            SysFont.draw(g, "+" + EconomyClient.format(SellPrices.of(s)) + " G", cx, y + h - 64, 8f, 0xFFFFE7A6, true, 0.5f);
            bigButton(g, dx + 8, y + h - 50, dw - 16, Component.translatable("shop.beyondsolo.sell_button").getString(), null, GoldHud.GOLD, mouseX, mouseY, "sell");
            String allLabel = Component.translatable("shop.beyondsolo.sell_all", EconomyClient.format(same)).getString();
            bigButton(g, dx + 8, y + h - 24, dw - 16, allLabel, null, GoldHud.GOLD_DEEP, mouseX, mouseY, "sell_all");
            stamp(g, cx, py, true);
        }
    }

    // ------------------------------------------------------------------ pieces

    /** The chosen item on a lit pedestal, with a slow turning ring under it. */
    private void pedestal(GuiGraphics g, ItemStack stack, float cx, float cy, int gc, float w) {
        float t = Anim.loop(9f);
        float bob = (float) Math.sin(Anim.loop(2.6f) * Math.PI * 2) * 1.5f;
        float land = Anim.outBack(Anim.clamp01(Anim.since(doneAt) / 0.4f));
        boolean justDone = Anim.since(doneAt) < 0.9f;
        Shapes.circle(cx, cy + 12, 22).fill(0).glow(14, Gfx.alpha(gc, 0.22f)).draw(g);
        Shapes.ring(cx, cy + 12, 17, 0.8f).fill(Gfx.alpha(gc, 0.5f)).dashes(14, 0.5f).rotation(t).draw(g);
        Shapes.ring(cx, cy + 12, 11, 0.6f).fill(Gfx.alpha(gc, 0.35f)).dashes(8, 0.4f).rotation(-t * 1.6f).draw(g);
        Shapes.box(cx - 0.75f, cy - 22, 1.5f, 34).fillV(Gfx.alpha(gc, 0f), Gfx.alpha(gc, 0.35f)).draw(g);
        float scale = 2f * (justDone ? 0.8f + 0.2f * land : 1f);
        g.pose().pushPose();
        g.pose().translate(cx, cy + bob, 150);
        g.pose().scale(scale, scale, 1);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
        pedestal = new float[]{cx, cy};
    }

    /** ACQUIRED / SOLD stamp and a ring of light, right after the server confirms. */
    private void stamp(GuiGraphics g, float cx, float cy, boolean sale) {
        float t = Anim.since(doneAt);
        if (t > 1.8f || doneWasSale != sale) return;
        // the ring
        float rk = Anim.clamp01((t - 0.35f) / 0.5f);
        if (rk > 0 && rk < 1) {
            int c = sale ? GoldHud.GOLD : SystemTheme.ACCENT;
            Shapes.ring(cx, cy, 10 + 30 * Anim.outCubic(rk), 1.5f * (1 - rk) + 0.3f).fill(Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.3f), 1 - rk)).glow(4, Gfx.alpha(c, 0.6f * (1 - rk))).draw(g);
        }
        float sk = Anim.clamp01((t - 0.4f) / 0.25f);
        if (sk <= 0) return;
        float out = 1 - Anim.clamp01((t - 1.4f) / 0.4f);
        float s = 1.6f - 0.6f * Anim.outBackStrong(sk);
        String label = Component.translatable(sale ? "shop.beyondsolo.sold" : "shop.beyondsolo.acquired").getString();
        int c = sale ? GoldHud.GOLD : SystemTheme.GOOD;
        float old = Gfx.fade(sk * out);
        g.pose().pushPose();
        g.pose().translate(cx, cy + 4, 200);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-8));
        g.pose().scale(s, s, 1);
        float tw = SysFont.spacedWidth(label, 8f, true, 1.8f) + 14;
        Shapes.box(-tw / 2f, -8, tw, 16).radius(3).fill(Gfx.alpha(c, 0.18f)).border(1.2f, c).glow(5, Gfx.alpha(c, 0.5f)).draw(g);
        SysFont.spaced(g, label, 0, SysFont.centerY(-8, 16, 8f), 8f, Gfx.mix(c, 0xFFFFFFFF, 0.3f), true, 1.8f, 0.5f);
        String amount = (sale ? "+" : "−") + EconomyClient.format(doneAmount) + " G";
        SysFont.draw(g, amount, 0, 10, 6f, sale ? 0xFFFFE7A6 : SystemTheme.TEXT_SOFT, true, 0.5f);
        g.pose().popPose();
        Gfx.setAlpha(old);
    }

    /** Coins flying between the wallet and the item after a purchase (wallet -> item) or a sale (item -> wallet). */
    private void coinsFlight(GuiGraphics g) {
        float t = Anim.since(doneAt);
        if (t > 0.9f) return;
        float fromX = doneWasSale ? pedestal[0] : walletChip[0];
        float fromY = doneWasSale ? pedestal[1] : walletChip[1];
        float toX = doneWasSale ? walletChip[0] : pedestal[0];
        float toY = doneWasSale ? walletChip[1] : pedestal[1];
        for (int i = 0; i < 9; i++) {
            float k = Anim.clamp01((t - i * 0.035f) / 0.5f);
            if (k <= 0 || k >= 1) continue;
            float e = Anim.inOutCubic(k);
            float arc = (float) Math.sin(k * Math.PI) * (18 + (i % 3) * 8) * (i % 2 == 0 ? 1 : -1);
            float px = Mth.lerp(e, fromX, toX) + arc * 0.4f;
            float py = Mth.lerp(e, fromY, toY) - Math.abs(arc);
            Shapes.circle(px, py, 2.6f).fillV(0xFFFFE7A6, GoldHud.GOLD_DEEP).border(0.4f, 0xFFFFF4D6).glow(3, Gfx.alpha(GoldHud.GOLD, 0.7f)).draw(g);
        }
    }

    private void stepper(GuiGraphics g, float x, float y, String label, int mouseX, int mouseY, int delta, boolean enabled) {
        boolean hover = enabled && Ui.inside(mouseX, mouseY, x, y, 10, 10);
        int c = enabled ? SystemTheme.ACCENT : SystemTheme.TEXT_DIM;
        Shapes.circle(x + 5, y + 5, 5).fill(Gfx.alpha(c, hover ? 0.32f : 0.12f)).border(0.6f, Gfx.alpha(c, 0.8f)).draw(g);
        SysFont.draw(g, label, x + 5, y + 1.2f, 7.5f, Gfx.mix(c, 0xFFFFFFFF, 0.4f), true, 0.5f);
        if (enabled) hits.add(new Object[]{"lots", x, y, 10f, 10f, delta});
    }

    private void bigButton(GuiGraphics g, float x, float y, float w, String label, @Nullable String why, int color, int mouseX, int mouseY, String action) {
        float h = 17;
        boolean enabled = why == null;
        boolean hover = enabled && Ui.inside(mouseX, mouseY, x, y, w, h);
        float press = enabled ? 1 - Anim.clamp01(Anim.since(doneAt) / 0.5f) : 0;
        int c = enabled ? color : SystemTheme.TEXT_DIM;
        float sheen = Anim.loop(2.8f) * 1.6f - 0.3f;
        Shapes.pill(x, y, w, h).fillV(Gfx.alpha(c, hover ? 0.42f : 0.26f), Gfx.alpha(Gfx.shade(c, -0.4f), hover ? 0.3f : 0.16f))
                .border(0.9f, Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.25f), enabled ? 0.95f : 0.4f), Gfx.alpha(c, 0.3f))
                .glow(enabled ? 4 + 6 * press + (hover ? 3 : 0) : 0, Gfx.alpha(c, 0.25f + 0.4f * press))
                .sheen(enabled ? sheen : -2, 0.18f).draw(g);
        String text = enabled ? label : why;
        float size = enabled ? 7f : 6f;
        SysFont.spaced(g, SysFont.fit(text, size, true, w - 8), x + w / 2f, SysFont.centerY(y, h, size), size, enabled ? SystemTheme.TEXT : SystemTheme.TEXT_SOFT, true, enabled ? 1.4f : 0.3f, 0.5f);
        if (enabled) hits.add(new Object[]{action, x, y, w, h, null});
    }

    private static boolean locked(ShopCatalog.Entry e) {
        return ClientSystemData.get().rank().tier() < e.minRank().tier();
    }

    private static int gradeColor(ItemStack stack) {
        ItemGrade grade = stack.get(ModComponents.GRADE.get());
        if (grade != null) return Gfx.opaque(grade.color());
        return switch (stack.getRarity()) {
            case UNCOMMON -> 0xFFFFE07A;
            case RARE -> 0xFF6FE3FF;
            case EPIC -> 0xFFD58CFF;
            default -> SystemTheme.ACCENT;
        };
    }

    private void choose(String id) {
        if (!id.equals(chosen)) {
            chosen = id;
            chosenAt = Anim.now();
            lots = 1;
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Object[] hit = hits.get(i);
            if (!Ui.inside(mouseX, mouseY, (float) hit[1], (float) hit[2], (float) hit[3], (float) hit[4])) continue;
            String kind = (String) hit[0];
            switch (kind) {
                case "mode" -> {
                    boolean sell = (boolean) hit[5];
                    if (sell != selling) {
                        selling = sell;
                        modeAt = Anim.now();
                        scroll = 0;
                        SystemScreen.playClick();
                    }
                }
                case "cat" -> {
                    ShopCatalog.Category c = (ShopCatalog.Category) hit[5];
                    if (c != category) {
                        category = c;
                        scroll = 0;
                        chosen = "";
                        SystemScreen.playTick(1.3f);
                    }
                }
                case "entry" -> {
                    choose((String) hit[5]);
                    SystemScreen.playTick(1.6f);
                }
                case "slot" -> {
                    chosenSlot = (int) hit[5];
                    chosenAt = Anim.now();
                    SystemScreen.playTick(1.6f);
                }
                case "lots" -> {
                    int d = (int) hit[5];
                    lots = Math.max(1, lots + d * (net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 8 : 1));
                    SystemScreen.playTick(1.8f);
                }
                case "buy" -> {
                    EconomyClient.send(EconomyActionPayload.BUY, chosen, lots);
                    SystemScreen.playClick();
                }
                case "sell" -> {
                    EconomyClient.send(EconomyActionPayload.SELL, "", chosenSlot);
                    SystemScreen.playClick();
                }
                case "sell_all" -> {
                    EconomyClient.send(EconomyActionPayload.SELL_ALL, "", chosenSlot);
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        for (Object[] hit : hits) {
            if (!"list".equals(hit[0])) continue;
            if (Ui.inside(mouseX, mouseY, (float) hit[1], (float) hit[2], (float) hit[3], (float) hit[4])) {
                scroll -= (float) scrollY;
                return true;
            }
        }
        return false;
    }

    /** For the UI test driver. */
    public static void show(boolean sell, ShopCatalog.Category cat, String entry) {
        selling = sell;
        category = cat;
        chosen = entry;
    }
}
