package com.beyondsolo.association;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * A hunter's standing with the association, earned with merit ({@code association.standings}). Each brings something:
 * a contract more at once, bigger bounties, a permit more a day, the broker's goodwill, and at the top a title.
 */
public enum Standing {
    REGISTERED(0xFF9FB2C8),
    RECOGNIZED(0xFF8BE28B),
    TRUSTED(0xFF5FD0FF),
    DISTINGUISHED(0xFFB98BFF),
    ELITE(0xFFFFB347),
    NATIONAL(0xFFFFD166);

    public final int color;

    Standing(int color) {
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("association.beyondsolo.standing." + id());
    }

    /** What reaching it brings (empty for Registered). */
    public Component perk() {
        return Component.translatable("association.beyondsolo.standing." + id() + ".perk");
    }

    /** The merit it takes. */
    public long merit() {
        return AssociationConfig.standing(ordinal());
    }

    /** The standing {@code merit} earns. */
    public static Standing of(long merit) {
        Standing best = REGISTERED;
        for (Standing s : values()) if (merit >= s.merit()) best = s;
        return best;
    }

    public static Standing byIndex(int i) {
        Standing[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    /** The next one up, or null at the top. */
    public Standing next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }

    public boolean atLeast(Standing other) {
        return ordinal() >= other.ordinal();
    }

    // what each brings

    /** Contracts held at once. */
    public int contractSlots() {
        return AssociationConfig.maxContracts() + (atLeast(RECOGNIZED) ? 1 : 0);
    }

    /** Bounties paid times this. */
    public double bountyBonus() {
        return atLeast(NATIONAL) ? 1.3 : atLeast(ELITE) ? 1.2 : atLeast(TRUSTED) ? 1.1 : 1.0;
    }

    /** Gate permits a day. */
    public int permitsPerDay() {
        return AssociationConfig.permitsPerDay() + (atLeast(DISTINGUISHED) ? 1 : 0);
    }

    /** The broker's prices times this. */
    public double brokerPrice() {
        return atLeast(ELITE) ? 0.8 : 1.0;
    }
}
