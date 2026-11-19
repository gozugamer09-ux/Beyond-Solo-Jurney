package com.beyondsolo.reward;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * What a cursed box does to whoever opens it, picked when it opens:
 * <ul>
 *   <li><b>Wardens:</b> the things that guarded it climb out. What it held stays sealed until they are all down; die or
 *   run, or let the time run out, and it crumbles.</li>
 *   <li><b>Frailty:</b> a hex that takes a share of the hunter's health for a while.</li>
 *   <li><b>Backlash:</b> the box's own mana floods into them: mana poisoning, wherever they stand.</li>
 *   <li><b>Greed:</b> it swallows a share of their gold.</li>
 * </ul>
 */
public enum BoxCurse {
    WARDENS(40), FRAILTY(25), BACKLASH(20), GREED(15);

    private final int weight;

    BoxCurse(int weight) {
        this.weight = weight;
    }

    public String id() {
        return name().toLowerCase();
    }

    public Component displayName() {
        return Component.translatable("box.beyondsolo.curse." + id());
    }

    public static BoxCurse roll(RandomSource r) {
        int total = 0;
        for (BoxCurse c : values()) total += c.weight;
        int roll = r.nextInt(total);
        for (BoxCurse c : values()) {
            roll -= c.weight;
            if (roll < 0) return c;
        }
        return WARDENS;
    }

    public static BoxCurse byIndex(int i) {
        BoxCurse[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }
}
