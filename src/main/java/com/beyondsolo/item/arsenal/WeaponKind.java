package com.beyondsolo.item.arsenal;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The shape of a weapon: its base damage and speed on top of the grade, how heavy it is to swing, and the
 * Better Combat moveset it uses ({@code data/beyondsolo/weapon_attributes/<id>.json} names it as the parent).
 */
public enum WeaponKind {
    DAGGER(0, -1.6f, 0.6f, "dagger"),
    SWORD(3, -2.4f, 1.0f, "sword"),
    RAPIER(2, -2.1f, 0.7f, "rapier"),
    KATANA(3, -2.3f, 0.9f, "katana"),
    CLAYMORE(6, -3.0f, 1.8f, "claymore"),
    SPEAR(4, -2.7f, 1.2f, "spear"),
    GLAIVE(5, -2.9f, 1.4f, "glaive"),
    HALBERD(6, -3.0f, 1.6f, "halberd"),
    HEAVY_AXE(7, -3.2f, 1.9f, "heavy_axe"),
    MACE(4, -2.6f, 1.3f, "mace"),
    HAMMER(7, -3.3f, 2.0f, "hammer"),
    FIST(1, -1.8f, 0.5f, "fist"),
    CLAW(1, -1.6f, 0.5f, "claw"),
    STAFF(2, -2.6f, 0.8f, "battlestaff"),
    BOW(0, 0, 0.4f, "bow");

    private final int damage;
    private final float speed;
    private final float weight;
    private final String moveset;

    WeaponKind(int damage, float speed, float weight, String moveset) {
        this.damage = damage;
        this.speed = speed;
        this.weight = weight;
        this.moveset = moveset;
    }

    public int damage() {
        return damage;
    }

    public float speed() {
        return speed;
    }

    public float weight() {
        return weight;
    }

    /** Better Combat preset, e.g. "bettercombat:claymore". */
    public String moveset() {
        return "bettercombat:" + moveset;
    }

    public Component displayName() {
        return Component.translatable("weapon_kind.beyondsolo." + name().toLowerCase(Locale.ROOT));
    }
}
