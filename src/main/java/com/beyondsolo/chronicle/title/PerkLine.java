package com.beyondsolo.chronicle.title;

import com.beyondsolo.chronicle.journal.CreatureFamily;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** One effect of a title: the perk, how strong, and for {@link TitlePerk#FAMILY_DAMAGE} which family. */
public record PerkLine(TitlePerk perk, float value, @Nullable CreatureFamily family) {
    public PerkLine(TitlePerk perk, float value) {
        this(perk, value, null);
    }

    /** "+5% System XP", "+10% damage to beasts". */
    public Component describe() {
        String key = "chronicle.beyondsolo.perk." + perk.id();
        if (perk == TitlePerk.FAMILY_DAMAGE && family != null) return Component.translatable(key, perk.number(value), family.plural());
        return Component.translatable(key, perk.number(value));
    }
}
