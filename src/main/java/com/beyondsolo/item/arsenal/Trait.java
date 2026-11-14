package com.beyondsolo.item.arsenal;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * A passive weapon trait and its strength. What each one does lives in {@link com.beyondsolo.combat.ArsenalHits}.
 *
 * @param kind  which trait
 * @param value its strength (a fraction for most: 0.3 = 30%)
 */
public record Trait(Kind kind, float value) {
    public enum Kind {
        /** Hits stack Frostbite (I-III); the next hit freezes the target for {@code value} seconds. */
        FROSTBITE,
        /** {@code value} chance per hit to arc lightning into up to 3 more enemies. */
        THUNDERCLAP,
        /** +{@code value} damage per point of the target's armor (max +60%). */
        ARMOR_BREAKER,
        /** Heals the wielder for {@code value} of the damage dealt. */
        LIFESTEAL,
        /** +{@code value} damage to targets below 35% health. */
        EXECUTIONER,
        /** Heals the wielder and allies within 6 blocks for {@code value} of the damage dealt (at least 1). */
        MENDING,
        /** Every 4th hit in a row on the same target deals {@code value} times the damage. */
        RHYTHM,
        /** Takes {@code value} less damage while the weapon is in hand. */
        BULWARK,
        /** +{@code value} damage when striking from behind. */
        BACKSTAB,
        /** +{@code value} damage while the other hand holds a twin weapon. */
        TWIN,
        /** Fully drawn shots fire {@code value} extra arrows. */
        SPLIT_SHOT;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static Trait of(Kind kind, float value) {
        return new Trait(kind, value);
    }

    public Component describe() {
        String shown = switch (kind) {
            case FROSTBITE, SPLIT_SHOT -> trim(value);
            case RHYTHM -> trim(value) + "x";
            case ARMOR_BREAKER -> trim(value * 100) + "%";
            default -> Math.round(value * 100) + "%";
        };
        return Component.translatable("trait.beyondsolo." + kind.key(), shown);
    }

    private static String trim(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(Math.round(v * 10) / 10f);
    }
}
