package com.beyondsolo.monarch;

import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.PlayerClass;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What a monarch rules: the hunter picks one at the throne, and it is theirs for good. It colours their aura and
 * their domain, decides what their domain does to the foes inside it, and gives them their authority, the one power
 * no other monarch has.
 * <ul>
 *   <li><b>Night</b>: the dark answers. The monarch raises shadows whatever their class; shadows in the domain hit
 *   harder and mend; the authority, Eclipse of Kings, binds every foe round them in the dark and calls the whole army
 *   back at once, stronger.</li>
 *   <li><b>Winter</b>: everything slows. The domain chills and freezes; The Long Winter freezes everything round them
 *   solid, then shatters it.</li>
 *   <li><b>Cinders</b>: everything burns. The domain sets foes alight; Ashen Heaven drops a burning star where they
 *   look.</li>
 *   <li><b>Thunder</b>: the sky obeys. The domain strikes foes with lightning; Thousand Thunders calls it down on
 *   everything near, one bolt after another.</li>
 * </ul>
 * Names are this mod's own.
 */
public enum Dominion {
    NIGHT(0x8A5CFF, SkillElement.SHADOW, "monarch_eclipse"),
    WINTER(0x9BE7FF, SkillElement.WATER, "monarch_long_winter"),
    CINDERS(0xFF7A2E, SkillElement.FIRE, "monarch_ashen_heaven"),
    THUNDER(0xB8C8FF, SkillElement.LIGHTNING, "monarch_thousand_thunders");

    private final int color;
    private final SkillElement element;
    private final String authority;

    Dominion(int color, SkillElement element, String authority) {
        this.color = color;
        this.element = element;
        this.authority = authority;
    }

    /** RGB: the monarch's aura, domain and crown. */
    public int color() { return color; }

    /** The element of the monarch's domain and authority. */
    public SkillElement element() { return element; }

    /** The skill path of its authority (see {@code MonarchPowers}). */
    public String authority() { return authority; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    /** "Night". */
    public Component displayName() {
        return Component.translatable("monarch.beyondsolo.dominion." + id());
    }

    /** "Monarch of Night". */
    public Component title() {
        return Component.translatable("monarch.beyondsolo.dominion." + id() + ".title");
    }

    /** One line: what the domain does. */
    public Component domainText() {
        return Component.translatable("monarch.beyondsolo.dominion." + id() + ".domain");
    }

    /** One line of flavour for the throne. */
    public Component lore() {
        return Component.translatable("monarch.beyondsolo.dominion." + id() + ".lore");
    }

    /** The title the System gives with this crown ("title.beyondsolo.monarch_of_night"). */
    public String titleKey() {
        return "title.beyondsolo.monarch_of_" + id();
    }

    /** The crown the throne leans to for a class, when the hunter lets it choose. */
    public static Dominion leaning(@Nullable PlayerClass c) {
        if (c == null) return NIGHT;
        return switch (c) {
            case MAGE -> CINDERS;
            case RANGER -> THUNDER;
            case GUARDIAN, HEALER -> WINTER;
            default -> NIGHT;
        };
    }

    public static @Nullable Dominion byId(String id) {
        for (Dominion d : values()) if (d.id().equalsIgnoreCase(id) || d.name().equalsIgnoreCase(id)) return d;
        return null;
    }

    public static Dominion byIndex(int i) {
        Dominion[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }
}
