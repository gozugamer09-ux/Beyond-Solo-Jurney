package com.beyondsolo.item.arsenal;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.system.PlayerClass;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** What every System weapon (melee or bow) shares: grade, traits, the class it favours or is bound to, and its signature. */
public interface ArsenalWeapon {
    ItemGrade baseGrade();

    WeaponKind kind();

    List<Trait> traits();

    DamageElement element();

    /** Buildup per hit (bows: per arrow). */
    float buildup();

    /** The class this weapon suits, shown in the tooltip. Divine weapons are bound to it. */
    @Nullable PlayerClass favoredClass();

    @Nullable Signature signature();

    /** Divine weapons only answer to their class: anyone else deals half damage and can't use the signature. */
    default boolean boundToClass() {
        return baseGrade() == ItemGrade.DIVINE && favoredClass() != null;
    }

    default float trait(Trait.Kind kind) {
        for (Trait t : traits()) if (t.kind() == kind) return t.value();
        return 0;
    }

    default boolean has(Trait.Kind kind) {
        for (Trait t : traits()) if (t.kind() == kind) return true;
        return false;
    }
}
