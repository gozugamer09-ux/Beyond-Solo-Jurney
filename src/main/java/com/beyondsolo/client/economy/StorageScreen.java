package com.beyondsolo.client.economy;

import com.beyondsolo.client.ClientEvents;
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
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.economy.StorageMenu;
import com.beyondsolo.economy.SystemStorage;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/**
 * The System storage, drawn as the STORAGE page of the System window (same glass, header and tabs; picking another
 * tab goes back to the System window). Left: a page of 54 storage slots, locked ones past the hunter's capacity
 * shown with a lock, the page switch and the capacity bar. Right: the inventory, the wallet and two buttons:
 * stack matching items into storage, and sort.
 */
public class StorageScreen extends AbstractContainerScreen<StorageMenu> {
    private static final int W = SystemScreen.WIDTH;
    private static final int H = SystemScreen.HEIGHT;

    private final List<ResourceLocation> tabIds = new ArrayList<>();
    private final List<String> tabTitles = new ArrayList<>();
    private List<float[]> tabRects = List.of();
    private int selected;
    private double openedAt = -1;
    private double closingAt = -1;
    private double pageAt = -10;
    private int pageDir = 1;
    private int lastPage;
    private double sortedAt = -10;
    private double depositedAt = -10;
    private final List<float[]> buttons = new ArrayList<>();

    public StorageScreen(StorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        tabIds.clear();
        tabTitles.clear();
        for (SystemTabs.Entry e : SystemTabs.entries()) {
            SystemTab tab = e.factory().get();
            if (!tab.isVisible()) continue;
            if (e.id().equals(EconomyClientEvents.STORAGE_TAB)) selected = tabIds.size();
            tabIds.add(e.id());
            tabTitles.add(tab.title().getString());
        }
        SystemScreen.openOn(EconomyClientEvents.STORAGE_TAB);
        // coming from the System window it is already open; from the key it opens like the window does
        if (openedAt < 0) openedAt = Anim.since(EconomyClient.storageRequestedAt()) < 3 ? Anim.now() - 1 : Anim.now();
        lastPage = menu.page();
    }

    private boolean closing() {
        return closingAt >= 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float open = Anim.outCubic(Anim.progress(openedAt, 0.22f));
        if (closing()) open *= 1 - Anim.inCubic(Anim.progress(closingAt, 0.13f));
        if (menu.page() != lastPage) {
            pageDir = menu.page() > lastPage ? 1 : -1;
            lastPage = menu.page();
            pageAt = Anim.now();
        }
        float oldAlpha = Gfx.setAlpha(open);
        renderBlurredBackground(partialTick);
        Shapes.vignette(0, 0, width, height, 0.15f, 1.5f).fill(0xC8040912).draw(g);
        Shapes.box(0, 0, width, height).fill(0x30040912).draw(g);
        g.pose().pushPose();
        float scale = 0.95f + 0.05f * open;
        g.pose().translate(width / 2f, height / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-width / 2f, -height / 2f, 0);
        super.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
        Gfx.setAlpha(oldAlpha);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBg(g, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        float x = leftPos;
        float y = topPos;
        Ui.panel(g, x, y, W, H, 11, SystemTheme.ACCENT, 1f);

        // header, as in the System window
        float headerMid = y + SystemScreen.headerHeight() / 2f + 1;
        float pulse = Anim.pulse(2.6f);
        Shapes.icon(Icons.DIAMOND, x + 17, headerMid, 9).fill(SystemTheme.ACCENT_BRIGHT).glow(3 + pulse * 1.5f, Gfx.alpha(SystemTheme.ACCENT, 0.5f + 0.3f * pulse)).draw(g);
        tabRects = SystemScreen.layoutTabs(tabTitles, x, y);
        float tabsLeft = tabRects.isEmpty() ? x + W : tabRects.get(0)[0];
        String title = Component.translatable("screen.beyondsolo.system.header").getString();
        if (x + 27 + SysFont.spacedWidth(title, 10.5f, true, 2.6f) < tabsLeft - 10) {
            SysFont.spaced(g, title, x + 27, SysFont.centerY(headerMid - 6, 12, 10.5f), 10.5f, SystemTheme.TEXT, true, 2.6f, 0f);
        }
        if (!tabRects.isEmpty()) {
            float[] r = tabRects.get(selected);
            SystemScreen.drawTabs(g, tabTitles, tabRects, selected, r[0], r[2], mouseX, mouseY);
        }
        Ui.divider(g, x + 12, y + SystemScreen.headerHeight(), W - 24, Gfx.alpha(SystemTheme.ACCENT, 0.55f));

        storageSide(g, x, y, mouseX, mouseY);
        inventorySide(g, x, y, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ left: the storage

    private void storageSide(GuiGraphics g, float x, float y, int mouseX, int mouseY) {
        int cap = menu.capacity();
        int pages = menu.pages();
        float gx = x + StorageMenu.STORAGE_X;
        float gw = StorageMenu.COLS * 18;

        Shapes.icon(Icons.VAULT, gx + 4, y + 45, 9).fill(SystemTheme.ACCENT_BRIGHT).glow(2, Gfx.alpha(SystemTheme.ACCENT, 0.5f)).draw(g);
        SysFont.spaced(g, Component.translatable("storage.beyondsolo.title").getString(), gx + 12, y + 42, 6.5f, SystemTheme.ACCENT_BRIGHT, true, 1.4f, 0f);

        buttons.clear();
        // page switch
        if (pages > 1) {
            float px = gx + gw;
            String label = (menu.page() + 1) + " / " + pages;
            float lw = SysFont.width(label, 6.5f, true);
            float bx = px - 9;
            pageButton(g, bx, y + 40, false, menu.page() < pages - 1, mouseX, mouseY, StorageMenu.NEXT);
            SysFont.draw(g, label, bx - 4 - lw / 2f, y + 42, 6.5f, SystemTheme.TEXT, true, 0.5f);
            pageButton(g, bx - 8 - lw - 9, y + 40, true, menu.page() > 0, mouseX, mouseY, StorageMenu.PREV);
        } else {
            String counts = used() + " / " + cap;
            SysFont.draw(g, counts, gx + gw, y + 42, 6.5f, SystemTheme.TEXT_SOFT, true, 1f);
        }

        // the grid well
        float wy = y + StorageMenu.STORAGE_Y;
        Shapes.box(gx - 4, wy - 4, gw + 8, StorageMenu.ROWS * 18 + 8).radius(7)
                .fillV(0x60050B18, 0x90050B18).border(0.75f, Gfx.alpha(SystemTheme.ACCENT, 0.35f), Gfx.alpha(SystemTheme.ACCENT, 0.1f)).draw(g);
        float k = Anim.outCubic(Anim.progress(pageAt, 0.3f));
        for (int i = 0; i < StorageMenu.PAGE; i++) {
            Slot s = menu.slots.get(i);
            boolean unlocked = menu.unlocked(i);
            float sx = x + s.x - 1;
            float sy = y + s.y - 1;
            // the cells sweep in after a page change, column by column
            float col = (i % StorageMenu.COLS) / (float) StorageMenu.COLS;
            float cell = Anim.clamp01((k - (pageDir > 0 ? col : 1 - col) * 0.5f) * 2f);
            float a = 0.35f + 0.65f * cell;
            if (unlocked) {
                Shapes.box(sx, sy, 18, 18).radius(3.5f).fillV(Gfx.alpha(0xFF183052, 0.75f * a), Gfx.alpha(0xFF0D1C36, 0.75f * a))
                        .border(0.6f, Gfx.alpha(SystemTheme.ACCENT, 0.32f * a), Gfx.alpha(SystemTheme.ACCENT, 0.12f * a)).draw(g);
            } else {
                Shapes.box(sx + 1, sy + 1, 16, 16).radius(3f).fill(Gfx.alpha(0xFF02050C, 0.6f)).border(0.5f, 0x18FFFFFF).draw(g);
                Shapes.icon(Icons.LOCK, sx + 9, sy + 9, 7).fill(Gfx.alpha(SystemTheme.TEXT_DIM, 0.45f)).draw(g);
            }
        }

        // capacity
        float by = wy + StorageMenu.ROWS * 18 + 10;
        float frac = cap / (float) com.beyondsolo.economy.HunterData.MAX_SLOTS;
        Ui.bar(g, gx, by, gw, 4, frac, SystemTheme.ACCENT_DEEP, SystemTheme.ACCENT);
        PlayerRank rank = ClientSystemData.get().rank();
        String left = Component.translatable("storage.beyondsolo.capacity", cap).getString();
        SysFont.text(g, left, gx, by + 8, 6f, SystemTheme.TEXT_SOFT);
        String right;
        if (EconomyClient.expansions() < SystemStorage.MAX_EXPANSIONS) right = Component.translatable("storage.beyondsolo.expand_hint").getString();
        else right = Component.translatable("storage.beyondsolo.maxed").getString();
        SysFont.draw(g, right, gx + gw, by + 8, 6f, SystemTheme.TEXT_DIM, false, 1f);
        Shapes.circle(gx + SysFont.width(left, 6f, false) + 5, by + 10.5f, 1.4f).fill(SystemTheme.rank(rank)).glow(2, Gfx.alpha(SystemTheme.rank(rank), 0.6f)).draw(g);
        SysFont.text(g, Component.translatable("storage.beyondsolo.rank_line", rank.displayName()).getString(), gx + SysFont.width(left, 6f, false) + 9, by + 8, 6f, SystemTheme.rank(rank));
    }

    private int used() {
        int n = 0;
        for (int i = 0; i < StorageMenu.PAGE; i++) if (!menu.slots.get(i).getItem().isEmpty()) n++;
        return n;
    }

    private void pageButton(GuiGraphics g, float x, float y, boolean left, boolean enabled, int mouseX, int mouseY, int id) {
        boolean hover = enabled && Ui.inside(mouseX, mouseY, x, y, 9, 9);
        int c = enabled ? (hover ? SystemTheme.ACCENT_BRIGHT : SystemTheme.ACCENT) : SystemTheme.TEXT_DIM;
        Shapes.circle(x + 4.5f, y + 4.5f, 4.5f).fill(Gfx.alpha(c, hover ? 0.3f : 0.12f)).border(0.6f, Gfx.alpha(c, 0.7f)).draw(g);
        SysFont.draw(g, left ? "‹" : "›", x + 4.5f, y + 0.6f, 7.5f, c, true, 0.5f);
        if (enabled) buttons.add(new float[]{x, y, 9, 9, id});
    }

    // ------------------------------------------------------------------ right: the inventory and wallet

    private void inventorySide(GuiGraphics g, float x, float y, int mouseX, int mouseY) {
        float ix = x + StorageMenu.INV_X;
        float iw = 9 * 18;
        Shapes.icon(Icons.PACKAGE, ix + 4, y + 45, 9).fill(SystemTheme.TEXT_SOFT).draw(g);
        SysFont.spaced(g, Component.translatable("storage.beyondsolo.inventory").getString(), ix + 12, y + 42, 6.5f, SystemTheme.TEXT_SOFT, true, 1.4f, 0f);
        Shapes.box(ix - 4, y + StorageMenu.INV_Y - 4, iw + 8, StorageMenu.HOTBAR_Y - StorageMenu.INV_Y + 18 + 8).radius(7)
                .fillV(0x50050B18, 0x80050B18).border(0.75f, 0x30A8DCFF, 0x10A8DCFF).draw(g);
        for (int i = StorageMenu.PAGE; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            float sx = x + s.x - 1;
            float sy = y + s.y - 1;
            boolean hotbar = i >= StorageMenu.PAGE + 27;
            Shapes.box(sx, sy, 18, 18).radius(3.5f).fillV(hotbar ? 0x80203A60 : 0x70183052, 0x700D1C36)
                    .border(0.6f, hotbar ? 0x50A8DCFF : 0x30A8DCFF, 0x10A8DCFF).draw(g);
        }

        // wallet
        float wy = y + StorageMenu.HOTBAR_Y + 26;
        float flash = 1 - Anim.clamp01(Anim.since(EconomyClient.lastChangeAt()) / 0.6f);
        Ui.card(g, ix - 4, wy, iw + 8, 24, 7, GoldHud.GOLD, 0.25f + 0.4f * flash);
        Shapes.circle(ix + 8, wy + 12, 7).fillV(GoldHud.GOLD, GoldHud.GOLD_DEEP).border(0.75f, 0xFFFFF0C0, 0xFFB86A10).glow(3 + 3 * flash, Gfx.alpha(GoldHud.GOLD, 0.5f)).draw(g);
        Shapes.icon(Icons.COINS, ix + 8, wy + 12, 9).fill(0xFF5A3200).draw(g);
        String gold = EconomyClient.format(EconomyClient.gold());
        SysFont.title(g, gold, ix + 19, SysFont.centerY(wy, 24, 9f), 9f, 0xFFFFF4D6);
        SysFont.title(g, "G", ix + 21 + SysFont.width(gold, 9f, true), SysFont.centerY(wy, 24, 6.5f) + 0.5f, 6.5f, GoldHud.GOLD);
        SysFont.draw(g, Component.translatable("storage.beyondsolo.wallet").getString(), ix + iw, wy + 6, 5.8f, SystemTheme.TEXT_DIM, false, 1f);
        SysFont.draw(g, Component.translatable("storage.beyondsolo.earned", EconomyClient.format(EconomyClient.earned())).getString(), ix + iw, wy + 13.5f, 5.8f, SystemTheme.TEXT_SOFT, false, 1f);

        // buttons
        float by = wy + 30;
        float bw = (iw + 8 - 4) / 2f;
        actionButton(g, ix - 4, by, bw, Component.translatable("storage.beyondsolo.deposit").getString(), Icons.DEPOSIT, depositedAt, mouseX, mouseY, StorageMenu.DEPOSIT);
        actionButton(g, ix - 4 + bw + 4, by, bw, Component.translatable("storage.beyondsolo.sort").getString(), Icons.SPARKLE, sortedAt, mouseX, mouseY, StorageMenu.SORT);

        // key hints along the bottom of the window
        float hy = y + H - 22;
        Ui.divider(g, x + 16, hy - 6, W - 32, Gfx.alpha(SystemTheme.ACCENT, 0.25f));
        float kx = x + StorageMenu.STORAGE_X;
        kx += Ui.keycap(g, kx, hy, 9, Component.translatable("storage.beyondsolo.key.shift").getString(), SystemTheme.ACCENT) + 3;
        String a = Component.translatable("storage.beyondsolo.hint.move").getString();
        SysFont.text(g, a, kx, hy + 1.5f, 6f, SystemTheme.TEXT_SOFT);
        kx += SysFont.width(a, 6f, false) + 10;
        kx += Ui.keycap(g, kx, hy, 9, keyName(EconomyClientEvents.OPEN_STORAGE), SystemTheme.ACCENT) + 3;
        String b = Component.translatable("storage.beyondsolo.hint.key").getString();
        SysFont.text(g, b, kx, hy + 1.5f, 6f, SystemTheme.TEXT_SOFT);
        kx += SysFont.width(b, 6f, false) + 10;
        if (menu.pages() > 1) {
            kx += Ui.keycap(g, kx, hy, 9, "\u2195", SystemTheme.ACCENT) + 3;
            SysFont.text(g, Component.translatable("storage.beyondsolo.hint.scroll").getString(), kx, hy + 1.5f, 6f, SystemTheme.TEXT_SOFT);
        }
    }

    private static String keyName(net.minecraft.client.KeyMapping k) {
        String s = k.getTranslatedKeyMessage().getString().toUpperCase();
        return s.length() > 5 ? s.substring(0, 5) : s;
    }

    private void actionButton(GuiGraphics g, float x, float y, float w, String label, ResourceLocation icon, double pressedAt, int mouseX, int mouseY, int id) {
        float h = 15;
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, h);
        float press = 1 - Anim.clamp01(Anim.since(pressedAt) / 0.45f);
        Shapes.pill(x, y, w, h).fillV(Gfx.alpha(SystemTheme.ACCENT, hover ? 0.3f : 0.16f), Gfx.alpha(SystemTheme.ACCENT_DEEP, hover ? 0.22f : 0.1f))
                .border(0.75f, Gfx.alpha(SystemTheme.ACCENT, hover ? 0.95f : 0.55f), Gfx.alpha(SystemTheme.ACCENT, 0.25f))
                .glow(3 + 6 * press, Gfx.alpha(SystemTheme.ACCENT, 0.15f + 0.5f * press)).draw(g);
        float tw = SysFont.spacedWidth(label, 6.2f, true, 0.8f);
        float cx = x + w / 2f + 5;
        Shapes.icon(icon, cx - tw / 2f - 6, y + h / 2f, 8).fill(SystemTheme.ACCENT_BRIGHT).draw(g);
        SysFont.spaced(g, label, cx, SysFont.centerY(y, h, 6.2f), 6.2f, hover ? SystemTheme.TEXT : SystemTheme.ACCENT_BRIGHT, true, 0.8f, 0.5f);
        buttons.add(new float[]{x, y, w, h, id});
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // the page sweep: a sheet of light crosses the grid after a page change (drawn over the items)
        float t = Anim.progress(pageAt, 0.35f);
        if (t < 1f) {
            float gx = StorageMenu.STORAGE_X - 3;
            float gw = StorageMenu.COLS * 18 + 6;
            float pos = pageDir > 0 ? Anim.outCubic(t) : 1 - Anim.outCubic(t);
            float lx = gx + gw * pos;
            float a = 1 - t;
            Shapes.box(lx - 10, StorageMenu.STORAGE_Y - 3, 20, StorageMenu.ROWS * 18 + 6).fillH(Gfx.alpha(SystemTheme.ACCENT, 0), Gfx.alpha(SystemTheme.ACCENT_BRIGHT, 0)).glow(10, Gfx.alpha(SystemTheme.ACCENT, 0.35f * a)).draw(g);
            Shapes.box(lx - 0.75f, StorageMenu.STORAGE_Y - 3, 1.5f, StorageMenu.ROWS * 18 + 6).fill(Gfx.alpha(SystemTheme.ACCENT_BRIGHT, 0.9f * a)).draw(g);
        }
        float s = Anim.progress(sortedAt, 0.5f);
        if (s < 1f) {
            float a = (1 - s);
            Shapes.box(StorageMenu.STORAGE_X - 3, StorageMenu.STORAGE_Y - 3, StorageMenu.COLS * 18 + 6, StorageMenu.ROWS * 18 + 6).radius(6)
                    .fill(Gfx.alpha(SystemTheme.ACCENT, 0.12f * a)).border(1f, Gfx.alpha(SystemTheme.ACCENT_BRIGHT, 0.8f * a)).glow(6, Gfx.alpha(SystemTheme.ACCENT, 0.4f * a)).draw(g);
        }
    }

    @Override
    protected void renderSlotHighlight(GuiGraphics g, Slot slot, int mouseX, int mouseY, float partialTick) {
        if (!slot.isHighlightable()) return;
        boolean locked = slot.index < StorageMenu.PAGE && !menu.unlocked(slot.index);
        int c = locked ? SystemTheme.DANGER : SystemTheme.ACCENT;
        Shapes.box(slot.x - 1, slot.y - 1, 18, 18).radius(3.5f).fill(Gfx.alpha(c, 0.18f)).border(0.9f, Gfx.alpha(Gfx.mix(c, 0xFFFFFFFF, 0.3f), 0.9f))
                .glow(4, Gfx.alpha(c, 0.45f)).draw(g);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing()) return true;
        for (int i = 0; i < tabRects.size(); i++) {
            float[] r = tabRects.get(i);
            if (Ui.inside(mouseX, mouseY, r[0], r[1], r[2], SystemScreen.tabHeight())) {
                if (i != selected) goToTab(tabIds.get(i));
                return true;
            }
        }
        for (float[] b : buttons) {
            if (!Ui.inside(mouseX, mouseY, b[0], b[1], b[2], b[3])) continue;
            int id = (int) b[4];
            Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, id);
            if (id == StorageMenu.SORT) sortedAt = Anim.now();
            if (id == StorageMenu.DEPOSIT) depositedAt = Anim.now();
            // the client's copy of the page follows at once, the items with the server's next update
            if (id == StorageMenu.PREV || id == StorageMenu.NEXT) menu.clickMenuButton(Minecraft.getInstance().player, id);
            SystemScreen.playClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Presses a button by id (for the UI test driver). */
    public void press(int id) {
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, id);
        if (id == StorageMenu.SORT) sortedAt = Anim.now();
        if (id == StorageMenu.DEPOSIT) depositedAt = Anim.now();
        if (id == StorageMenu.PREV || id == StorageMenu.NEXT) menu.clickMenuButton(Minecraft.getInstance().player, id);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.pages() > 1 && Ui.inside(mouseX, mouseY, leftPos + StorageMenu.STORAGE_X - 4, topPos + StorageMenu.STORAGE_Y - 4, StorageMenu.COLS * 18 + 8, StorageMenu.ROWS * 18 + 8)) {
            int id = scrollY < 0 ? StorageMenu.NEXT : StorageMenu.PREV;
            if ((id == StorageMenu.NEXT && menu.page() < menu.pages() - 1) || (id == StorageMenu.PREV && menu.page() > 0)) {
                Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, id);
                menu.clickMenuButton(Minecraft.getInstance().player, id);
                SystemScreen.playTick(1.4f);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        return mouseX < left || mouseY < top || mouseX >= left + imageWidth || mouseY >= top + imageHeight;
    }

    private void goToTab(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        SystemScreen.playClick();
        if (mc.player != null) mc.player.closeContainer();
        mc.setScreen(SystemScreen.openInstant(id));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientEvents.OPEN_SYSTEM.matches(keyCode, scanCode) || EconomyClientEvents.OPEN_STORAGE.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && !tabIds.isEmpty()) {
            int step = hasShiftDown() ? -1 : 1;
            goToTab(tabIds.get(Math.floorMod(selected + step, tabIds.size())));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (closing() && Anim.progress(closingAt, 0.13f) >= 1f && minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
    }

    @Override
    public void onClose() {
        if (!closing()) {
            closingAt = Anim.now();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.9f, 0.2f));
        }
    }
}
