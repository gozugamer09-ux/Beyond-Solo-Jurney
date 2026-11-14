package com.beyondsolo.combat;

import net.neoforged.neoforge.common.EffectCure;

/** Cures for the combat statuses. Milk still cures the light ones; the heavy ones each need their own remedy. */
public final class ElementCures {
    /** Quenching Elixir: the only thing that puts out a deep burn. */
    public static final EffectCure ELIXIR = EffectCure.get("beyondsolo_elixir");
    /** Antidote: light venom. */
    public static final EffectCure ANTIDOTE = EffectCure.get("beyondsolo_antidote");
    /** Greater Antidote: any venom, including heavy venom. */
    public static final EffectCure GREATER_ANTIDOTE = EffectCure.get("beyondsolo_greater_antidote");
    /** Clotting Tonic: bleeding and the crimson brand. */
    public static final EffectCure TONIC = EffectCure.get("beyondsolo_tonic");

    private ElementCures() {}
}
