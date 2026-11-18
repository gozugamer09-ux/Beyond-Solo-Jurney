package com.beyondsolo.chronicle.title;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One title the System can give. Its name is the lang line {@code title.beyondsolo.<id>} (what {@code SystemData} keeps
 * for the title worn), and {@code .desc} says what earns it. A title with a {@link Feat} is earned when that number
 * reaches {@link #target}; one without is given by the feature it belongs to ({@code SystemAPI.setTitle}). A secret one
 * shows only as a hint until it is earned.
 */
public record TitleDef(String id, TitleCategory category, TitleTier tier, @Nullable Feat feat, long target, List<PerkLine> perks, boolean secret) {
    public static final String PREFIX = "title.beyondsolo.";

    public String key() {
        return PREFIX + id;
    }

    public Component displayName() {
        return Component.translatable(key());
    }

    /** What earns it. */
    public Component description() {
        return Component.translatable(key() + ".desc");
    }

    /** The goal of its feat (the journal's grows with the journal). */
    public long goal() {
        return feat == null ? 0 : feat.target(target);
    }
}
