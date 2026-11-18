package com.beyondsolo.enemy;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.util.ParticleGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Spawning the roster's creatures, inside a gate or anywhere else (dungeon breaks, events): one call makes a
 * creature of a rank, with its affinity, variant and affixes, stronger by the rank, and marks it.
 *
 * <pre>
 * EnemyRoster.Mix mix = EnemyRoster.random(rank, random);          // or EnemyRoster.forGate(gate)
 * for (...) Enemies.spawn(level, rank, mix.pick(random), pos, Enemies.roll(rank, mix, random, false), -1);
 * </pre>
 */
public final class Enemies {
    /** Persistent-data key that marks a creature as one of the roster's (worth a gate quest's kill, etc.). */
    public static final String RANK_KEY = "beyondsolo_enemy_rank";

    /** Rolls a creature's traits: an affinity from the mix, a variant (a pack leader rolls higher), its affixes. */
    public static EnemyTraits roll(PlayerRank rank, EnemyRoster.Mix mix, EntityType<?> type, RandomSource r, boolean leader, boolean lateRoom) {
        Variant v = leader ? Variant.rollLeader(rank, r, lateRoom) : Variant.rollMember(rank, r);
        Affinity a = mix.affinityFor(type, r, v.special());
        return new EnemyTraits(a, v, Affix.mask(Affix.roll(v.affixes, r)));
    }

    /**
     * Makes one creature: {@code gateTheme} is the theme ordinal to store under the gate's mob tag (-1 for none).
     * Returns null if the type can't be made.
     */
    @Nullable
    public static Mob spawn(ServerLevel level, PlayerRank rank, EntityType<?> type, BlockPos p, EnemyTraits traits, int gateTheme) {
        if (!(type.create(level) instanceof Mob mob)) return null;
        mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        if (gateTheme >= 0) mob.getPersistentData().putInt(com.beyondsolo.gate.GateService.MOB_TAG, gateTheme);
        apply(mob, rank, traits);
        level.addFreshEntity(mob);
        // the puff it rises from, for everyone not standing right on top of it
        ParticleGuard.send(level, ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.3, mob.getZ(), 12, 0.3, 0.1, 0.3, 0.02);
        ParticleGuard.send(level, ParticleTypes.SCULK_SOUL, mob.getX(), mob.getY() + 0.5, mob.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
        if (traits.variant() == Variant.CHAMPION) EnemyFx.burst(level, mob.position().add(0, 1, 0), traits.affinity().color, 2.2f);
        return mob;
    }

    /**
     * Gives a creature its rank's strength and its traits (attributes, affinity, effects) and remembers them. Its health
     * and damage grow by the rank's {@link #rankHealth} and {@link #rankDamage}: a rank's typical creature is an even
     * match for a hunter at the rank's home level.
     */
    public static void apply(Mob mob, PlayerRank rank, EnemyTraits traits) {
        Variant v = traits.variant();
        strength(mob, rankHealth(rank), rankDamage(rank));
        mod(mob, Attributes.MAX_HEALTH, "variant_health", v.health - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        mod(mob, Attributes.ATTACK_DAMAGE, "variant_damage", v.damage - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        mod(mob, Attributes.SCALE, "variant_scale", v.scale - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (v.special()) mod(mob, Attributes.KNOCKBACK_RESISTANCE, "variant_steady", 0.3, AttributeModifier.Operation.ADD_VALUE);
        EnumSet<Affix> affixes = traits.affixes();
        if (affixes.contains(Affix.SWIFT)) {
            mod(mob, Attributes.MOVEMENT_SPEED, "affix_swift", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            mod(mob, Attributes.FLYING_SPEED, "affix_swift", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
        if (affixes.contains(Affix.ARMORED)) {
            mod(mob, Attributes.ARMOR, "affix_armored", 8, AttributeModifier.Operation.ADD_VALUE);
            mod(mob, Attributes.KNOCKBACK_RESISTANCE, "affix_armored", 0.5, AttributeModifier.Operation.ADD_VALUE);
        }
        if (mob.getAttribute(Attributes.SCALE) != null && mob.getAttribute(Attributes.SCALE).getModifier(BeyondSolo.id("size_roll")) == null) {
            applySize(mob, rollSize(mob.getRandom(), false));
        }
        mob.setHealth(mob.getMaxHealth());
        if (mob instanceof GateMonsterEntity m) m.setAffinity(traits.affinity());
        if (traits.affinity() == Affinity.FLAME) mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        mob.getPersistentData().putInt(RANK_KEY, rank.ordinal());
        traits.save(mob);
    }

    /**
     * Sets how much stronger than its kind a creature is: its health and damage multipliers (what its rank or level
     * gives, before its variant and size). Keeps its share of health.
     */
    public static void strength(Mob mob, double health, double damage) {
        float share = mob.getMaxHealth() <= 0 ? 1f : mob.getHealth() / mob.getMaxHealth();
        mod(mob, Attributes.MAX_HEALTH, "gate_health", health - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(mob, Attributes.ATTACK_DAMAGE, "gate_damage", damage - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mob.setHealth(Math.max(1f, mob.getMaxHealth() * Math.min(1f, share)));
        HiddenLevel.forget(mob);
    }

    /**
     * How much a rank's numbers are evened out by how heavy its pool is: the higher ranks' pools hold heavier kinds, so
     * their typical creature would stand above the power curve. E 1, down to about 0.37 for S. Every creature of the
     * rank gets it, so its kinds keep their spread around the typical one.
     */
    public static double poolScale(@Nullable PlayerRank rank) {
        if (rank == null) return 1;
        return EnemyRoster.basePower(PlayerRank.E) / EnemyRoster.basePower(rank);
    }

    /** The health multiplier a gate rank gives its creatures: {@link Balance#health} evened out by {@link #poolScale}. */
    public static double rankHealth(PlayerRank rank) {
        return Balance.health(rank) * poolScale(rank);
    }

    /** The damage multiplier a gate rank gives its creatures: {@link Balance#damage} evened out by {@link #poolScale}. */
    public static double rankDamage(PlayerRank rank) {
        return Balance.damage(rank) * poolScale(rank);
    }

    /**
     * The health a kill is worth in System XP: its max health, counted as if its rank's pool had not been evened out
     * ({@link #poolScale}), so the heavier ranks pay what their creatures' size says they should.
     */
    public static float killWorth(LivingEntity victim) {
        float health = victim.getMaxHealth();
        return victim instanceof Mob mob ? (float) (health / poolScale(rankOf(mob))) : health;
    }

    /** Gives {@code to} the same strength multipliers {@code from} has (when it has any). */
    public static void copyStrength(Mob from, Mob to) {
        double health = multiplier(from, Attributes.MAX_HEALTH, "gate_health");
        double damage = multiplier(from, Attributes.ATTACK_DAMAGE, "gate_damage");
        if (health > 0 || damage > 0) strength(to, health > 0 ? health : 1, damage > 0 ? damage : 1);
    }

    /** How many times its kind's health the System's strength gave a creature (its rank or level; 1 for none). */
    public static double healthGrowth(Mob mob) {
        double m = multiplier(mob, Attributes.MAX_HEALTH, "gate_health");
        return m > 0 ? m : 1;
    }

    /** A strength multiplier a creature carries, or 0 when it carries none. */
    private static double multiplier(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, String id) {
        AttributeInstance inst = mob.getAttribute(attr);
        var m = inst == null ? null : inst.getModifier(BeyondSolo.id(id));
        return m == null ? 0 : 1 + m.amount();
    }

    /**
     * Makes a creature as strong as content for a hunter of {@code level} is (the penalty zone, nests):
     * {@link Balance#healthAt} and {@link Balance#damageAt} instead of its rank's numbers, evened out by the pool it
     * came from ({@link #poolScale} of the rank it was made for).
     */
    public static void strengthForLevel(Mob mob, double level) {
        double scale = poolScale(rankOf(mob));
        strength(mob, Balance.healthAt(level) * scale, Balance.damageAt(level) * scale);
    }

    /**
     * The creatures of the cave that hides a sealed door are fought before the System: whatever the gate's rank, they
     * stay at an old D-rank gate's strength ({@link Balance#DOOR_HEALTH}).
     */
    public static void doorStrength(Mob mob) {
        strength(mob, Balance.DOOR_HEALTH, Balance.DOOR_DAMAGE);
    }

    /**
     * A creature's size, as a multiple of its kind's: most are near their usual size, about one in eight is small
     * (down to half), and the unlucky meet a giant (up to double). Bosses stray less often.
     */
    public static float rollSize(RandomSource r, boolean boss) {
        float roll = r.nextFloat();
        float giant = boss ? 0.015f : 0.025f;
        float big = boss ? 0.07f : 0.09f;
        float small = boss ? 0.07f : 0.12f;
        if (roll < giant) return 1.6f + 0.4f * r.nextFloat();
        if (roll < giant + big) return 1.25f + 0.35f * r.nextFloat();
        if (roll < giant + big + small) return 0.5f + 0.3f * r.nextFloat();
        return 0.85f + 0.3f * r.nextFloat();
    }

    /**
     * Sets a creature's size roll: its scale, and its health, damage and pace with it. Size 1 puts it back to its
     * kind's own size. The scale modifier stays on even then: it is also the mark that this creature's size has been
     * decided, so {@link #apply} does not roll another one.
     */
    public static void applySize(Mob mob, float size) {
        set(mob, Attributes.SCALE, "size_roll", size - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
        mod(mob, Attributes.MAX_HEALTH, "size_health", (size - 1) * 0.8, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        mod(mob, Attributes.ATTACK_DAMAGE, "size_damage", (size - 1) * 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        mod(mob, Attributes.MOVEMENT_SPEED, "size_pace", (1 - size) * 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        mob.refreshDimensions();
    }

    /** The size roll a creature got (1 when it has none). */
    public static float sizeOf(net.minecraft.world.entity.LivingEntity e) {
        AttributeInstance inst = e.getAttribute(Attributes.SCALE);
        var m = inst == null ? null : inst.getModifier(BeyondSolo.id("size_roll"));
        return m == null ? 1f : 1f + (float) m.amount();
    }

    /** Sets the modifier {@code id} to {@code amount}. An amount of zero takes an earlier one off and adds nothing. */
    private static void mod(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, String id, double amount,
                            AttributeModifier.Operation op) {
        set(mob, attr, id, amount, op, false);
    }

    private static void set(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, String id, double amount,
                            AttributeModifier.Operation op, boolean keepZero) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst == null) return;
        // the old one goes first, whatever the new amount is: setting a value to nothing has to undo the earlier one
        inst.removeModifier(BeyondSolo.id(id));
        if (keepZero || Math.abs(amount) >= 1e-4) inst.addPermanentModifier(new AttributeModifier(BeyondSolo.id(id), amount, op));
    }

    /** The rank a roster creature was made for, or null. */
    @Nullable
    public static PlayerRank rankOf(Mob mob) {
        if (!mob.getPersistentData().contains(RANK_KEY)) return null;
        return PlayerRank.values()[Math.max(0, Math.min(PlayerRank.values().length - 1, mob.getPersistentData().getInt(RANK_KEY)))];
    }

    private Enemies() {}
}
