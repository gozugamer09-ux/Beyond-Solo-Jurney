package com.beyondsolo.enemy;

import com.beyondsolo.BeyondSolo;
import net.minecraft.resources.ResourceLocation;

/**
 * The gate's own creatures (GeckoLib, made by tools/gen_enemy_assets.py). Each is its own entity type
 * ({@code beyondsolo:<id>}) but they share one class, {@link GateMonsterEntity}, which fights the way its
 * {@link Style} says. Every one of them can carry any {@link Affinity}: it picks the matching texture.
 */
public enum MonsterKind {
    /** A lean cave hound that hunts in packs and leaps at its prey. */
    GLOOMFANG("gloomfang", Style.HOUND, 0.8f, 0.95f, 18, 4, 0.34, 2, "bite", 20),
    /** A spider the size of a dog, in swarms. A broodmother's young. */
    BROODLING("broodling", Style.SWARM, 0.7f, 0.5f, 8, 2.5f, 0.33, 0, "bite", 20),
    /** A squat beetle with a back of crystal: slow, armoured, and it charges. */
    SHARDBACK("shardback", Style.CHARGER, 1.3f, 1.05f, 36, 6, 0.21, 9, "bite", 30),
    /** A robed caster in a bone mask that keeps its distance and throws bolts of its element. */
    HOLLOW_ACOLYTE("hollow_acolyte", Style.CASTER, 0.6f, 1.95f, 22, 5, 0.25, 2, "cast", 24),
    /** An armoured soldier with a tower shield: hits from the front glance off it. */
    ASHEN_SENTINEL("ashen_sentinel", Style.GUARD, 0.7f, 2.05f, 30, 6, 0.24, 10, "attack", 24),
    /** A floating, burning skull that flies at you and bursts. */
    EMBERWISP("emberwisp", Style.BOMBER, 0.6f, 0.65f, 10, 7, 0.3, 0, "attack", 10);

    public enum Style { HOUND, SWARM, CHARGER, CASTER, GUARD, BOMBER }

    public final String id;
    public final Style style;
    public final float width;
    public final float height;
    public final float health;
    public final float damage;
    public final double speed;
    public final double armor;
    public final String attackAnim;
    /** How long its death animation lasts before it is gone. */
    public final int deathTicks;

    MonsterKind(String id, Style style, float width, float height, float health, float damage, double speed, double armor, String attackAnim, int deathTicks) {
        this.id = id;
        this.style = style;
        this.width = width;
        this.height = height;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.armor = armor;
        this.attackAnim = attackAnim;
        this.deathTicks = deathTicks;
    }

    public ResourceLocation location() {
        return BeyondSolo.id(id);
    }

    public boolean flies() {
        return style == Style.BOMBER;
    }
}
