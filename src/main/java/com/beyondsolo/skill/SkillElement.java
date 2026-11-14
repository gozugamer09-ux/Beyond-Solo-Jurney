package com.beyondsolo.skill;

import net.minecraft.network.chat.Component;

/**
 * The element a skill carries. Shown on the skill and used by the coming combo and element reaction systems
 * (for example water then lightning bursts, air feeds fire).
 */
public enum SkillElement {
    PHYSICAL(0xD8D8D8),
    FIRE(0xFF7A2E),
    WATER(0x4FA8FF),
    LIGHTNING(0xB8E2FF),
    AIR(0xA8F0E0),
    EARTH(0xC9A35A),
    SHADOW(0x9B7BE0),
    LIGHT(0xFFF0A8),
    NATURE(0x8CE05A),
    ARCANE(0x7F9CFF);

    private final int color;

    SkillElement(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("skill_element.beyondsolo." + name().toLowerCase());
    }
}
