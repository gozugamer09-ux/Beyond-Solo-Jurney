package com.beyondsolo.economy;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The storage window: one page of 54 storage slots (the server shows the page the client asked for) and the
 * player's inventory. Slots past the player's capacity are locked: things can be taken out (if a rank was lost)
 * but nothing goes in.
 *
 * <p>Buttons ({@link #clickMenuButton}): {@link #PREV}, {@link #NEXT}, {@link #DEPOSIT} (move inventory items onto
 * matching stacks in storage), {@link #SORT}.</p>
 */
public class StorageMenu extends AbstractContainerMenu {
    public static final int COLS = 9;
    public static final int ROWS = 6;
    public static final int PAGE = COLS * ROWS;
    public static final int PAGES = HunterData.MAX_SLOTS / PAGE;

    public static final int PREV = 0;
    public static final int NEXT = 1;
    public static final int DEPOSIT = 2;
    public static final int SORT = 3;

    /** Where the slots sit inside the window (it is the size of the System window, 380 x 222). */
    public static final int STORAGE_X = 20;
    public static final int STORAGE_Y = 56;
    public static final int INV_X = 198;
    public static final int INV_Y = 56;
    public static final int HOTBAR_Y = INV_Y + 3 * 18 + 4;

    private final Container view;
    private final ContainerData data;
    private final PageView server;

    /** Client side. */
    public StorageMenu(int id, Inventory inv) {
        this(id, inv, new SimpleContainer(PAGE), new SimpleContainerData(2), null);
    }

    /** Server side. */
    public StorageMenu(int id, Inventory inv, HunterData hunter, int capacity) {
        this(id, inv, new PageView(hunter.storage), new SimpleContainerData(2), null);
        data.set(0, capacity);
    }

    private StorageMenu(int id, Inventory inv, Container view, ContainerData data, Void unused) {
        super(EconomyModule.STORAGE_MENU.get(), id);
        this.view = view;
        this.data = data;
        this.server = view instanceof PageView p ? p : null;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                addSlot(new StorageSlot(view, c + r * COLS, STORAGE_X + c * 18, STORAGE_Y + r * 18));
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, 9 + c + r * 9, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, HOTBAR_Y));
        addDataSlots(data);
    }

    public int capacity() {
        return data.get(0);
    }

    public int page() {
        return data.get(1);
    }

    public int pages() {
        return Math.max(1, (capacity() + PAGE - 1) / PAGE);
    }

    /** True if storage slot {@code index} (0..53 on this page) can take items. */
    public boolean unlocked(int index) {
        return page() * PAGE + index < capacity();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case PREV, NEXT -> {
                int page = Math.max(0, Math.min(pages() - 1, page() + (id == NEXT ? 1 : -1)));
                data.set(1, page);
                if (server != null) server.page = page;
            }
            case DEPOSIT -> {
                if (server != null) SystemStorage.depositMatching(player, server.storage, capacity());
            }
            case SORT -> {
                if (server != null) SystemStorage.sort(server.storage, capacity());
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < PAGE) {
            if (!moveItemStackTo(stack, PAGE, PAGE + 36, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, PAGE, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp) Economy.sync(sp);
    }

    private class StorageSlot extends Slot {
        StorageSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return unlocked(getContainerSlot());
        }
    }

    /** The server's view of one page of the hunter's storage. */
    private static final class PageView implements Container {
        final NonNullList<ItemStack> storage;
        int page;

        PageView(NonNullList<ItemStack> storage) {
            this.storage = storage;
        }

        private int at(int slot) {
            return page * PAGE + slot;
        }

        @Override public int getContainerSize() { return PAGE; }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < PAGE; i++) if (!getItem(i).isEmpty()) return false;
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            int i = at(slot);
            return i < storage.size() ? storage.get(i) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack s = getItem(slot);
            if (s.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            ItemStack out = s.split(amount);
            if (s.isEmpty()) setItem(slot, ItemStack.EMPTY);
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack s = getItem(slot);
            setItem(slot, ItemStack.EMPTY);
            return s;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            int i = at(slot);
            if (i < storage.size()) storage.set(i, stack);
        }

        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return true; }

        @Override
        public void clearContent() {
            for (int i = 0; i < PAGE; i++) setItem(i, ItemStack.EMPTY);
        }
    }
}
