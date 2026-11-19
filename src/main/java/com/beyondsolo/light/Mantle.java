package com.beyondsolo.light;

import com.beyondsolo.system.PlayerClass;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The mantle a ruler of light takes up at the Investiture ({@link Vigil}): the light's answer to a monarch's crown. Each
 * is the way of one of the rulers who came before (and a fourth, the Envoy's own), colours their light, and gives
 * them its authority, the one power no other ruler has.
 * <ul>
 *   <li><b>The Verdict</b> (Aurenthe's way): judgement. Blade of Heaven drops a sword of light the size of a tower
 *   where they look.</li>
 *   <li><b>Wings</b> (Caelistor's way): the open sky. Choir of Wings lifts them on wings of light and rains feathers
 *   like spears on everything round them.</li>
 *   <li><b>The Unseen Hand</b> (Ithrael's way): what no one can lift. Hand of Heaven brings a hand of light down out of
 *   the sky that closes on everything under it.</li>
 *   <li><b>Dawn</b> (the Envoy's way): the light that heals. Daybreak raises a sun over the fight that burns their foes
 *   and mends whoever stands with them.</li>
 * </ul>
 * Names are this mod's own.
 */
public enum Mantle {
    VERDICT(0xFFC94A, "light_blade_of_heaven"),
    WINGS(0xBFE8FF, "light_choir_of_wings"),
    HAND(0xF6E2A8, "light_hand_of_heaven"),
    DAWN(0xFF9E52, "light_daybreak");

    private final int color;
    private final String authority;

    Mantle(int color, String authority) {
        this.color = color;
        this.authority = authority;
    }

    /** RGB: the ruler's light, their sanctum and their wings. */
    public int color() { return color; }

    /** The skill path of its authority (see {@code RulerPowers}). */
    public String authority() { return authority; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** "The Verdict". */
    public Component displayName() {
        return Component.translatable("light.beyondsolo.mantle." + id());
    }

    /** "Ruler of the Verdict". */
    public Component title() {
        return Component.translatable("light.beyondsolo.mantle." + id() + ".title");
    }

    /** One line: what the mantle does to their sanctum. */
    public Component sanctumText() {
        return Component.translatable("light.beyondsolo.mantle." + id() + ".sanctum");
    }

    /** One line of flavour for the Investiture. */
    public Component lore() {
        return Component.translatable("light.beyondsolo.mantle." + id() + ".lore");
    }

    /** The title the System gives with this mantle ("title.beyondsolo.ruler_of_verdict"). */
    public String titleKey() {
        return "title.beyondsolo.ruler_of_" + id();
    }

    /** The mantle the light leans to for a class, when the hunter lets it choose. */
    public static Mantle leaning(@Nullable PlayerClass c) {
        if (c == null) return VERDICT;
        return switch (c) {
            case HEALER -> DAWN;
            case RANGER, ASSASSIN -> WINGS;
            case MAGE, GUARDIAN -> HAND;
            default -> VERDICT;
        };
    }

    public static @Nullable Mantle byId(String id) {
        for (Mantle m : values()) if (m.id().equalsIgnoreCase(id) || m.name().equalsIgnoreCase(id)) return m;
        return null;
    }

    public static Mantle byIndex(int i) {
        Mantle[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }
}
