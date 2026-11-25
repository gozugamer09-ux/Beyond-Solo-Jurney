package com.beyondsolo.duel;

import com.beyondsolo.chronicle.ranking.Rival;
import com.beyondsolo.chronicle.ranking.Rivals;
import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.system.PlayerClass;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The national-level hunters: the four S-ranks at the top of the national rankings ({@code chronicle.ranking.Rivals}),
 * each a fighter of their own ({@link Style}). They are the hunters a hunter duels: they come to test a new S-rank, to
 * take back the place a hunter climbed past them on the rankings, to settle it with one who walked out on their guild,
 * or because the hunter asked for it at the association.
 */
public enum NationalHunter {
    /** Azure Tide's master: a mage of the tides, who keeps her distance and drowns the ground under you. */
    HAN_SEORI("han_seori", Style.TIDE, 0x4FA8FF, true),
    /** Iron Bastion's ace: a warrior who grows into an iron giant when pressed. */
    VIKTOR_KAEL("viktor_kael", Style.TITAN, 0xC9D3DD, false),
    /** Sunfall's archer: arrows of sunlight, and at the end a sky that rains them. */
    AMARA_OKAFOR("amara_okafor", Style.SUN, 0xFFB347, true),
    /** The independent: an assassin you mostly see once he has already struck. */
    REN_TAKASHIRO("ren_takashiro", Style.SHADE, 0xE0465A, false);

    /** How each of them fights. */
    public enum Style {
        /** Heavy blows, a charge, a leaping slam; pressed, a giant of iron that stomps the ground. */
        TITAN,
        /** Bolts of water, geysers under the feet, a step through mist; pressed, a maelstrom and its lightning. */
        TIDE,
        /** Arrows of light, a fan of them, a shot from the sky; pressed, wings of light, rain of arrows, a lance of sun. */
        SUN,
        /** Quick cuts, a step through the shadows to the back, knives; pressed, unseen, and a thousand cuts. */
        SHADE;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public final String id;
    public final Style style;
    /** Their colour: their ring, their bar, their light. */
    public final int color;
    /** Slim arms (their skin's model). */
    public final boolean slim;

    NationalHunter(String id, Style style, int color, boolean slim) {
        this.id = id;
        this.style = style;
        this.color = color;
        this.slim = slim;
    }

    /** Their line on the rankings. */
    public Rival rival() {
        for (Rival r : Rivals.ALL) if (r.id().equals(id)) return r;
        throw new IllegalStateException("no rival " + id);
    }

    public String key() {
        return id;
    }

    public PlayerClass playerClass() {
        return rival().playerClass();
    }

    /** Their great guild (or the independents). */
    public RivalGuild guild() {
        return RivalGuild.of(rival().guild());
    }

    public Component displayName() {
        return Component.literal(rival().name());
    }

    /** What the country calls them ("the Iron Titan"). */
    public Component epithet() {
        return Component.translatable("duel.beyondsolo.epithet." + id);
    }

    /** The name of the form they take when pressed ("Titan Form"). */
    public Component unleashed() {
        return Component.translatable("duel.beyondsolo.unleashed." + style.key());
    }

    public static NationalHunter byIndex(int i) {
        NationalHunter[] all = values();
        return all[Math.floorMod(i, all.length)];
    }

    @Nullable
    public static NationalHunter byId(String id) {
        for (NationalHunter h : values()) if (h.id.equals(id)) return h;
        return null;
    }

    /** The national-level hunter of a great guild (Red Lantern has none). */
    @Nullable
    public static NationalHunter of(RivalGuild guild) {
        for (NationalHunter h : values()) if (h.guild() == guild && guild != RivalGuild.INDEPENDENT) return h;
        return null;
    }
}
