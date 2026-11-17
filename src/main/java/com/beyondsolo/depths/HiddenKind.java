package com.beyondsolo.depths;

import com.beyondsolo.system.PlayerRank;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What waits behind a hidden wall ({@link HiddenRooms}). A vault holds a cache a rank above the gate's; a shrine
 * blesses whoever steps in for the rest of the run and holds a cache of the gate's rank; a lair is guarded by
 * creatures a rank above the gate's, and its cache (a rank above too) comes down when the last of them falls.
 */
public enum HiddenKind {
    VAULT(0xFFD36A, 40),
    SHRINE(0x9FE8FF, 25),
    LAIR(0xFF5A6A, 35);

    public final int color;
    final int weight;

    HiddenKind(int color, int weight) {
        this.color = color;
        this.weight = weight;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component title() {
        return Component.translatable("depths.beyondsolo.hidden." + id());
    }

    public Component description() {
        return Component.translatable("depths.beyondsolo.hidden." + id() + ".desc");
    }

    public static HiddenKind byIndex(int i) {
        HiddenKind[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    @Nullable
    public static HiddenKind byId(String id) {
        for (HiddenKind k : values()) if (k.id().equals(id)) return k;
        return null;
    }

    /** Chance a gate hides a chamber, by rank: 30% at E, up to 55% at S. */
    public static float chance(PlayerRank rank) {
        return 0.30f + 0.05f * rank.tier();
    }

    /** A gate's hidden chamber, from its seed, or null. */
    @Nullable
    public static HiddenKind roll(long seed, PlayerRank rank) {
        RandomSource r = new LegacyRandomSource(seed ^ 0x4849444445524E21L);
        if (r.nextFloat() >= chance(rank)) return null;
        int total = 0;
        for (HiddenKind k : values()) total += k.weight;
        int pick = r.nextInt(total);
        for (HiddenKind k : values()) {
            pick -= k.weight;
            if (pick < 0) return k;
        }
        return VAULT;
    }
}
