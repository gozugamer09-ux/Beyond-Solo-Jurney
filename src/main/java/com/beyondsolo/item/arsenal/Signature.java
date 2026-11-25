package com.beyondsolo.item.arsenal;

import com.beyondsolo.item.ItemGrade;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The signature art of a legendary, mythic or divine weapon. Most are unleashed with a right-click; the bows' arts are
 * a held draw ({@link Trigger#DRAW}): hold the string at full draw until the art gathers, then loose. The higher the
 * grade, the bigger the art: a legendary art is one heavy blow, a mythic one comes in beats with its name over the
 * screen, a divine one is a whole scene (the screen closes in, the world answers, the name is stamped across it).
 * The effects live in {@link com.beyondsolo.combat.ArsenalAbilities} and the three art classes beside it; the looks
 * in the client's {@code ArtFx}.
 */
public enum Signature {
    // mythic
    INFERNAL_REND(ItemGrade.MYTHIC, Trigger.USE, 26, 0xFF5A1E, 8),
    ABSOLUTE_ZERO(ItemGrade.MYTHIC, Trigger.USE, 35, 0x9ED8FF, 12),
    HEAVENS_VERDICT(ItemGrade.MYTHIC, Trigger.USE, 40, 0xB8E2FF, 14),
    // divine
    CATACLYSM_CLEAVE(ItemGrade.DIVINE, Trigger.USE, 25, 0xFF7A2E, 14),
    DRAGONS_WRATH(ItemGrade.DIVINE, Trigger.USE, 18, 0xE0306E, 6),
    DEVOURING_STAR(ItemGrade.DIVINE, Trigger.USE, 16, 0x9B7BFF, 12),
    COLOSSUS_STANCE(ItemGrade.DIVINE, Trigger.USE, 45, 0xFFC85A, 12),
    VERDANT_BLESSING(ItemGrade.DIVINE, Trigger.USE, 50, 0x8CE05A, 16),
    STARFALL(ItemGrade.DIVINE, Trigger.DRAW, 30, 0xFFF0A8, 40),
    // legendary
    STORMBREAK(ItemGrade.LEGENDARY, Trigger.USE, 20, 0x9CC8FF, 9),
    TITANS_BARRAGE(ItemGrade.LEGENDARY, Trigger.USE, 18, 0xF2D27A, 4),
    THREE_HEADED_MAW(ItemGrade.LEGENDARY, Trigger.USE, 22, 0xFF6A2A, 10),
    CONSTELLATION(ItemGrade.LEGENDARY, Trigger.USE, 20, 0x9FC4FF, 10),
    SAINTS_DAWN(ItemGrade.LEGENDARY, Trigger.USE, 35, 0xFFE7A0, 12),
    GALE_LANCE(ItemGrade.LEGENDARY, Trigger.DRAW, 16, 0xA8F0E0, 30),
    VEILSTEP(ItemGrade.LEGENDARY, Trigger.USE, 16, 0x8A6CFF, 5),
    BLOOD_PRICE(ItemGrade.LEGENDARY, Trigger.USE, 20, 0xD0244E, 10),
    SUNDERING_VERDICT(ItemGrade.LEGENDARY, Trigger.USE, 22, 0xF4E2B0, 12),
    PYRE_OF_OATHS(ItemGrade.LEGENDARY, Trigger.USE, 22, 0xFF8A3A, 10),
    NIGHTGLASS_FAN(ItemGrade.LEGENDARY, Trigger.USE, 16, 0x7CFF6A, 6),
    REQUIEM_OF_THORNS(ItemGrade.LEGENDARY, Trigger.USE, 22, 0xC2185B, 12);

    /** How an art is set off. */
    public enum Trigger {
        /** Right-click with the weapon in the main hand. */
        USE,
        /** Hold a bow at full draw for {@link #drawTicks()}, then loose. */
        DRAW
    }

    private final ItemGrade grade;
    private final Trigger trigger;
    private final int cooldownSeconds;
    private final int color;
    private final int ticks;

    Signature(ItemGrade grade, Trigger trigger, int cooldownSeconds, int color, int ticks) {
        this.grade = grade;
        this.trigger = trigger;
        this.cooldownSeconds = cooldownSeconds;
        this.color = color;
        this.ticks = ticks;
    }

    /** The grade the art belongs to: how big it plays (a legendary blow, a mythic's beats, a divine scene). */
    public ItemGrade grade() {
        return grade;
    }

    public Trigger trigger() {
        return trigger;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public int color() {
        return color;
    }

    /**
     * Ticks from activation to the strike frame of the wielder's body animation: the first blow lands then. Matches
     * the animations made by {@code tools/gen_signature_anims.py}. A drawn art has none (it lands where its arrow does).
     */
    public int impactTicks() {
        return trigger == Trigger.USE ? ticks : 0;
    }

    /** Ticks a bow must be held at full draw before a drawn art is ready to loose (0 for the others). */
    public int drawTicks() {
        return trigger == Trigger.DRAW ? ticks : 0;
    }

    /** True for the right-click arts. */
    public boolean active() {
        return trigger == Trigger.USE;
    }

    public boolean drawn() {
        return trigger == Trigger.DRAW;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("signature.beyondsolo." + key());
    }

    public Component description() {
        return Component.translatable("signature.beyondsolo." + key() + ".desc");
    }
}
