package com.beyondsolo.client.screen;

import com.beyondsolo.BeyondSolo;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/** Registry of System window pages. Call {@link #register} during client setup. */
public final class SystemTabs {
    public record Entry(ResourceLocation id, int order, Supplier<SystemTab> factory) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static final ResourceLocation STATUS = BeyondSolo.id("status");
    public static final ResourceLocation QUESTS = BeyondSolo.id("quests");
    public static final ResourceLocation LOG = BeyondSolo.id("log");

    /**
     * Adds or replaces a page. Lower {@code order} goes further left.
     * Core uses 0 (Status), 100 (Quests) and 1000 (Log). The quest engine can replace {@link #QUESTS}.
     */
    public static synchronized void register(ResourceLocation id, int order, Supplier<SystemTab> factory) {
        ENTRIES.removeIf(e -> e.id().equals(id));
        ENTRIES.add(new Entry(id, order, factory));
        ENTRIES.sort(Comparator.comparingInt(Entry::order));
    }

    public static synchronized List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    static void registerDefaults() {
        register(STATUS, 0, StatusTab::new);
        register(QUESTS, 100, QuestsTab::new);
        register(LOG, 1000, LogTab::new);
    }

    private SystemTabs() {}
}
