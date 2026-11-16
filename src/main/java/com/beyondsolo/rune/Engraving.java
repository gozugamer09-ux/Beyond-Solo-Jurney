package com.beyondsolo.rune;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What an engraving rune carves into one of the player's skills, for good: one engraving per skill, a new one
 * replaces the old. Factors multiply the skill's own numbers.
 */
public enum Engraving {
    /** The skill comes back sooner. */
    HASTE(0x7FE3FF, 0.8f, 1f, 1f),
    /** The skill costs less mana. */
    THRIFT(0x8CF0A8, 1f, 0.7f, 1f),
    /** The skill hits harder. */
    MIGHT(0xFF9A5A, 1f, 1f, 1.2f);

    public final int color;
    public final float cooldown;
    public final float mana;
    public final float damage;

    Engraving(int color, float cooldown, float mana, float damage) {
        this.color = color;
        this.cooldown = cooldown;
        this.mana = mana;
        this.damage = damage;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("rune.beyondsolo.engraving." + id());
    }

    /** One line on what it does, with its number. */
    public Component effect() {
        int pct = Math.round(Math.abs(1 - (this == HASTE ? cooldown : this == THRIFT ? mana : damage)) * 100);
        return Component.translatable("rune.beyondsolo.engraving." + id() + ".effect", pct);
    }

    /** The engraving rune that carves it. */
    public Item item() {
        return RuneModule.ENGRAVINGS.get(this).get();
    }

    public static @Nullable Engraving byIndex(int i) {
        Engraving[] all = values();
        return i >= 0 && i < all.length ? all[i] : null;
    }

    public static @Nullable Engraving byId(String id) {
        for (Engraving e : values()) if (e.id().equals(id)) return e;
        return null;
    }
}
