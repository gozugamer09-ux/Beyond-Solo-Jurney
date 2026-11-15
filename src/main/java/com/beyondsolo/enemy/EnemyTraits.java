package com.beyondsolo.enemy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

import java.util.EnumSet;

/**
 * What makes one creature of the roster different from the next: its affinity, variant and affixes. Kept in the
 * entity's persistent data (so it works on vanilla mobs too) under {@link #KEY}, and sent to clients that see it
 * ({@code EnemyTraitsPayload}) for its aura and name plate.
 */
public record EnemyTraits(Affinity affinity, Variant variant, int affixMask) {
    public static final String KEY = "beyondsolo_enemy";
    public static final EnemyTraits NONE = new EnemyTraits(Affinity.NONE, Variant.NORMAL, 0);

    public static EnemyTraits of(LivingEntity e) {
        CompoundTag data = e.getPersistentData();
        if (!data.contains(KEY)) return NONE;
        CompoundTag t = data.getCompound(KEY);
        return new EnemyTraits(Affinity.byIndex(t.getInt("affinity")), Variant.byIndex(t.getInt("variant")), t.getInt("affixes"));
    }

    public static boolean has(LivingEntity e) {
        return e.getPersistentData().contains(KEY);
    }

    public void save(LivingEntity e) {
        CompoundTag t = new CompoundTag();
        t.putInt("affinity", affinity.ordinal());
        t.putInt("variant", variant.ordinal());
        t.putInt("affixes", affixMask);
        e.getPersistentData().put(KEY, t);
    }

    public boolean has(Affix a) {
        return (affixMask & (1 << a.ordinal())) != 0;
    }

    public EnumSet<Affix> affixes() {
        return Affix.fromMask(affixMask);
    }

    /** Worth showing a name plate and aura for. */
    public boolean notable() {
        return variant.special() || affinity != Affinity.NONE;
    }
}
