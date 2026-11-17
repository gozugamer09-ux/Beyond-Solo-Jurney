package com.beyondsolo.combat;

import com.beyondsolo.enemy.ElementalProfile;
import com.beyondsolo.skill.SkillElement;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What the next damage number over a creature should say about its element: a skill hit (or an infused weapon's
 * element) is tagged with its element and how it landed ({@link ElementalProfile.Rating}: WEAK, RESIST, IMMUNE) right
 * before the damage goes in, and {@link DamageNumbers} picks the tag up when it reports that damage the same tick.
 */
public final class HitTags {
    public record Tag(SkillElement element, ElementalProfile.Rating rating, long time) {}

    private static final Map<LivingEntity, Tag> TAGS = new WeakHashMap<>();

    public static void tag(LivingEntity target, SkillElement element, ElementalProfile.Rating rating) {
        if (target.level().isClientSide || element == SkillElement.PHYSICAL) return;
        TAGS.put(target, new Tag(element, rating, target.level().getGameTime()));
    }

    /** The tag left on {@code target} this tick, if any (taken: one number carries it). */
    @Nullable
    public static Tag take(LivingEntity target) {
        Tag t = TAGS.remove(target);
        if (t == null || t.time() != target.level().getGameTime()) return null;
        return t;
    }

    public static void clear() {
        TAGS.clear();
    }

    private HitTags() {}
}
