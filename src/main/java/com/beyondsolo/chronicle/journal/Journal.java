package com.beyondsolo.chronicle.journal;

import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateRealm;
import com.beyondsolo.item.arsenal.Arsenal;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every page the journal has, in the order it shows them: the gates' creatures by family, the bosses, the realms (every
 * gate realm, then the places that are no gate's cave), and the arsenal's weapons. Built the first time it is asked
 * for (the items are registered by then). Another feature adds its own pages with {@link #register}.
 */
public final class Journal {
    public static final String ARBITER = "arbiter";
    public static final String CITADEL_LORD = "citadel_lord";
    public static final String RED_WORLD = "red_world";
    public static final String CITADEL = "crimson_citadel";
    public static final String WASTES = "penalty_wastes";

    private static final List<JournalEntry> EXTRA = new ArrayList<>();
    private static List<JournalEntry> entries;
    private static Map<String, JournalEntry> byId;

    private static synchronized void build() {
        if (entries != null) return;
        List<JournalEntry> out = new ArrayList<>();
        for (CreatureFamily f : CreatureFamily.values()) {
            for (MonsterKind k : MonsterKind.values()) if (CreatureFamily.of(k) == f) out.add(JournalEntry.creature(k));
        }
        for (BossKind b : BossKind.values()) out.add(JournalEntry.boss(b));
        out.add(JournalEntry.boss(ARBITER));
        out.add(JournalEntry.boss(CITADEL_LORD));
        for (GateRealm r : GateRealm.values()) out.add(JournalEntry.realm(r));
        out.add(JournalEntry.place(RED_WORLD));
        out.add(JournalEntry.place(CITADEL));
        out.add(JournalEntry.place(WASTES));
        for (DeferredItem<? extends Item> item : Arsenal.everything()) out.add(JournalEntry.weapon(item.getId()));
        out.addAll(EXTRA);
        Map<String, JournalEntry> map = new LinkedHashMap<>();
        for (JournalEntry e : out) map.putIfAbsent(e.id(), e);
        entries = List.copyOf(map.values());
        byId = map;
    }

    /** Adds a page (before or after the journal is first built). */
    public static synchronized void register(JournalEntry entry) {
        EXTRA.add(entry);
        if (entries != null) {
            entries = null;
            byId = null;
        }
    }

    public static List<JournalEntry> all() {
        build();
        return entries;
    }

    public static List<JournalEntry> of(JournalCategory category) {
        List<JournalEntry> out = new ArrayList<>();
        for (JournalEntry e : all()) if (e.category() == category) out.add(e);
        return Collections.unmodifiableList(out);
    }

    public static @Nullable JournalEntry get(String id) {
        build();
        return byId.get(id);
    }

    public static int size() {
        return all().size();
    }

    public static int size(JournalCategory category) {
        int n = 0;
        for (JournalEntry e : all()) if (e.category() == category) n++;
        return n;
    }

    private Journal() {}
}
