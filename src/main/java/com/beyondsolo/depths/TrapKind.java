package com.beyondsolo.depths;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A trap room ({@link TrapRooms}). Lockdown seals the room's ways out with walls of light and sends two waves; the
 * others work until their trap core is broken: spikes burst up under whoever stands still, vents blast the gate's
 * element, and the ceiling comes down in marked spots.
 */
public enum TrapKind {
    LOCKDOWN(0xFF5A4A, 30),
    SPIKES(0xE8E0D0, 25),
    VENTS(0xFF9A3A, 25),
    ROCKFALL(0xC9A35A, 20);

    public final int color;
    final int weight;

    TrapKind(int color, int weight) {
        this.color = color;
        this.weight = weight;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component title() {
        return Component.translatable("depths.beyondsolo.trap." + id());
    }

    public Component hint() {
        return Component.translatable("depths.beyondsolo.trap." + id() + ".hint");
    }

    /** False for the lockdown, which is beaten by fighting, not by a core. */
    public boolean hasCore() {
        return this != LOCKDOWN;
    }

    public static TrapKind byIndex(int i) {
        TrapKind[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    @Nullable
    public static TrapKind byId(String id) {
        for (TrapKind k : values()) if (k.id().equals(id)) return k;
        return null;
    }

    static TrapKind pick(int roll) {
        int total = 0;
        for (TrapKind k : values()) total += k.weight;
        int pick = Math.floorMod(roll, total);
        for (TrapKind k : values()) {
            pick -= k.weight;
            if (pick < 0) return k;
        }
        return SPIKES;
    }

    /**
     * What a room a lockdown can't shut gets instead ({@code DungeonLayout.enclosed}: a camp in a hollow, an island, a
     * ledge over a shaft): one of the traps with a core, by weight, from the same roll.
     */
    static TrapKind pickOpen(int roll) {
        int total = 0;
        for (TrapKind k : values()) if (k.hasCore()) total += k.weight;
        int pick = Math.floorMod(roll * 7 / 3, total);
        for (TrapKind k : values()) {
            if (!k.hasCore()) continue;
            pick -= k.weight;
            if (pick < 0) return k;
        }
        return SPIKES;
    }
}
