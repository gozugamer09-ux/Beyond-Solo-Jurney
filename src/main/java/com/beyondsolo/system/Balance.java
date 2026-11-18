package com.beyondsolo.system;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The power curve of the whole game, in one place.
 *
 * <p><b>The hunter.</b> Levels go from 1 to {@link #MAX_LEVEL}. Each level adds {@link #HEALTH_PER_LEVEL} max health
 * and {@link #LEVEL_DAMAGE} (3%) to every blow; each stat point is a big step of its own: a VIT point is +5% max
 * health, a STR point +10% melee damage, a SENSE point +10% bow damage, and every point a hunter's class leans on
 * raises the power of their skills, procs and summons ({@link #power}). A fresh hunter has the vanilla 20 health;
 * a level 75 hunter has about 1,500 (a VIT-heavy guardian twice that), and hits about sixty times harder.</p>
 *
 * <p><b>The creatures.</b> They keep pace: a gate rank's plain creature is an even match for a hunter at that rank's
 * {@link #homeLevel} (E 3, D 13, C 26, B 41, A 56, S 74), so its health is multiplied by {@link #health} (as much as
 * a hunter's blows grew by then) and its damage by {@link #damage} (as much as a hunter's staying power grew).
 * {@link #healthAt} and {@link #damageAt} are the same curve by level, for content made for one hunter (job trials,
 * the penalty zone, nests, summons).</p>
 *
 * <p><b>Old flat numbers.</b> Flat heals (food, potions, most healing skills), absorption shields, hazards and the
 * blows of creatures that are not the System's were sized for health pools of 20 to 60. They are multiplied by
 * {@link #legacyScale} (how much bigger the hunter's pool is than it would have been before), so each keeps the share
 * of a hunter's health it always had. Armour is worked out on the blow's size next to the body it lands on
 * ({@link #armorScale}), so it protects as much against a 400-damage blow on a 1,500-health hunter as against a
 * 6-damage blow on a 20-health one. All of it lives in {@link BalanceHooks}.</p>
 *
 * <p>The numbers come from a model of a hunter who spends their points the way their class leans (3.5 points a level
 * counting rewards) and wears their rank's armour. This is the interim pass; the final balancing pass tunes it.</p>
 */
public final class Balance {
    public static final int MAX_LEVEL = 100;

    // ------------------------------------------------------------------ the hunter

    /** Max health each level adds on top of the vanilla 20. */
    public static final double HEALTH_PER_LEVEL = 4;
    /** Each VIT point: +5% max health (of the base, the levels and flat gear health). */
    public static final double VIT_HEALTH = 0.05;
    public static final double VIT_ARMOR = 0.05;
    public static final double VIT_TOUGHNESS = 0.05;
    /** Each STR point: +10% melee damage. */
    public static final double STR_DAMAGE = 0.10;
    /** Each SENSE point: +10% damage with bows, crossbows and thrown weapons. */
    public static final double SENSE_RANGED = 0.10;
    /** Each level: +3% to every blow, arrow, skill and summon. */
    public static final double LEVEL_DAMAGE = 0.03;
    /** Each point of a stat the hunter's class leans on: +3% skill, proc and summon power (scaled by how much it leans). */
    public static final double POWER_PER_POINT = 0.03;
    /** Stat points one level is worth on the creatures' level scale: 3 a level, plus what rewards add. */
    public static final float POINTS_PER_LEVEL = 3.5f;
    /** Max health a VIT point gave before this curve. Only used to keep old flat numbers at their old share. */
    private static final double LEGACY_VIT_HEALTH = 0.5;

    // ------------------------------------------------------------------ creatures, by rank E D C B A S

    private static final int[] HOME_LEVELS = {3, 13, 26, 41, 56, 74};
    private static final double[] HEALTH = {1, 3.39, 8.06, 17.06, 30.48, 55.03};
    private static final double[] DAMAGE = {1, 3.78, 9.79, 22.76, 39.87, 69.71};
    /** The same curve by level: the home levels, the first levels, and on past S to the level cap. */
    private static final double[] CURVE_LEVELS = {1, 3, 13, 26, 41, 56, 74, 80, 90, 100};
    private static final double[] CURVE_HEALTH = {0.705, 1, 3.39, 8.06, 17.06, 30.48, 55.03, 62.56, 76.15, 91.05};
    private static final double[] CURVE_DAMAGE = {0.654, 1, 3.78, 9.79, 22.76, 39.87, 69.71, 80.60, 100.77, 123.52};

    /** An E-rank dungeon boss; every rank multiplies it like its creatures. */
    public static final float BOSS_HEALTH = 300f;
    public static final float BOSS_DAMAGE = 7f;

    /**
     * The cave that hides a sealed door is fought by someone without the System yet, at the vanilla 20 health, whatever
     * the gate's rank: its creatures and boss stay at an old D-rank gate's strength.
     */
    public static final double DOOR_HEALTH = 1.4;
    public static final double DOOR_DAMAGE = 1.25;
    public static final float DOOR_BOSS_HEALTH = 228f;
    public static final float DOOR_BOSS_DAMAGE = 10.5f;

    /** Creature health multiplier for a gate rank (E 1 up to S 55). */
    public static double health(PlayerRank rank) {
        return HEALTH[rank.ordinal()];
    }

    /** Creature damage multiplier for a gate rank (E 1 up to S 70). */
    public static double damage(PlayerRank rank) {
        return DAMAGE[rank.ordinal()];
    }

    /** The level of the hunter a rank's plain creatures are an even match for. */
    public static int homeLevel(PlayerRank rank) {
        return HOME_LEVELS[rank.ordinal()];
    }

    /** Creature health multiplier for content made for a hunter of this level (fractions allowed, past 100 too). */
    public static double healthAt(double level) {
        return curve(CURVE_HEALTH, level);
    }

    /** Creature damage multiplier for content made for a hunter of this level. */
    public static double damageAt(double level) {
        return curve(CURVE_DAMAGE, level);
    }

    /** The gate rank made for a hunter of this level: the highest whose home level they are near (E below D's). */
    public static PlayerRank rankFor(double level) {
        PlayerRank best = PlayerRank.E;
        for (PlayerRank r : PlayerRank.values()) if (level >= homeLevel(r) - 2) best = r;
        return best;
    }

    public static float bossHealth(PlayerRank rank) {
        return (float) (BOSS_HEALTH * health(rank));
    }

    public static float bossDamage(PlayerRank rank) {
        return (float) (BOSS_DAMAGE * damage(rank));
    }

    /** Smooth between the anchor levels (a constant growth rate on each stretch), and on along the last stretch. */
    private static double curve(double[] values, double level) {
        if (level <= CURVE_LEVELS[0]) return values[0];
        int last = CURVE_LEVELS.length - 1;
        int i = 1;
        while (i < last && level > CURVE_LEVELS[i]) i++;
        double t = (level - CURVE_LEVELS[i - 1]) / (CURVE_LEVELS[i] - CURVE_LEVELS[i - 1]);
        return values[i - 1] * Math.pow(values[i] / values[i - 1], t);
    }

    // ------------------------------------------------------------------ the hunter's numbers

    /** Max health a hunter's levels add. */
    public static double levelHealth(int level) {
        return HEALTH_PER_LEVEL * (Math.max(1, level) - 1);
    }

    /** Everything a hunter deals is multiplied by this: +3% a level. */
    public static double levelDamage(int level) {
        return 1 + LEVEL_DAMAGE * (Math.max(1, level) - 1);
    }

    /** XP needed to go from {@code level} to the next. Level 1 needs 102; about 102,000 at level 74. */
    public static long xpToNext(int level) {
        int l = Math.max(1, level);
        return Math.round(100 * Math.pow(l, 1.4) * xpGrowth(l));
    }

    /** How much more a level costs than the old curve did: fixed rewards that grow with level grow by it too. */
    public static double xpGrowth(int level) {
        return 1 + Math.max(1, level) / 50.0;
    }

    /**
     * Where a hunter stands on the creatures' level scale: every stat point they have, rank bonus, rewards and unspent
     * points included, {@link #POINTS_PER_LEVEL} to a level. Works on both sides.
     */
    public static float powerLevel(SystemData d) {
        int points = d.statPoints();
        for (Stat s : Stat.values()) points += d.stat(s) - Stat.BASE;
        return Math.max(1f, 1f + points / POINTS_PER_LEVEL);
    }

    /**
     * A hunter's power: skills, procs and the bonus blows of gear grow with it. Each level adds 3%, and each stat point
     * adds {@link #POWER_PER_POINT} weighted by how much their class leans on the stat (its main stat counts fully, a
     * stat it ignores not at all). A hunter who builds the way their class leans ends up near the same power whatever
     * the class. 1 for anyone without the System.
     */
    public static double power(SystemData d) {
        if (!d.isAwakened()) return 1;
        PlayerClass c = d.playerClass();
        int max = 1;
        for (Stat s : Stat.values()) max = Math.max(max, c.statWeight(s));
        double weighted = 0;
        for (Stat s : Stat.values()) weighted += (double) c.statWeight(s) / max * Math.max(0, d.stat(s) - Stat.BASE);
        return levelDamage(d.level()) * (1 + POWER_PER_POINT * weighted);
    }

    public static double power(Player p) {
        return power(SystemAPI.get(p));
    }

    /** What one stat's points add to {@link #power} (0.3 = +30%), before the level bonus. 0 if the class ignores it. */
    public static double powerFrom(SystemData d, Stat stat) {
        if (!d.isAwakened()) return 0;
        PlayerClass c = d.playerClass();
        int max = 1;
        for (Stat s : Stat.values()) max = Math.max(max, c.statWeight(s));
        return POWER_PER_POINT * c.statWeight(stat) / max * Math.max(0, d.stat(stat) - Stat.BASE);
    }

    /** Bows, crossbows and thrown weapons: +10% a SENSE point and the level bonus. */
    public static double rangedPower(SystemData d) {
        if (!d.isAwakened()) return 1;
        return levelDamage(d.level()) * (1 + SENSE_RANGED * Math.max(0, d.stat(Stat.SENSE) - Stat.BASE));
    }

    /**
     * How many times bigger a hunter's health pool is than it was before this curve (20 health and half a point a VIT
     * point). Flat heals, absorption shields, hazards and the blows of creatures that are not the System's are
     * multiplied by it, so each keeps its old share of the hunter's health. 1 for anyone without the System.
     */
    public static double legacyScale(SystemData d) {
        if (!d.isAwakened()) return 1;
        double vit = Math.max(0, d.stat(Stat.VIT) - Stat.BASE);
        double now = (20 + levelHealth(d.level())) * (1 + VIT_HEALTH * vit);
        double before = 20 + LEGACY_VIT_HEALTH * vit;
        return Math.max(1, now / before);
    }

    public static double legacyScale(LivingEntity e) {
        return e instanceof Player p ? legacyScale(SystemAPI.get(p)) : 1;
    }

    /**
     * A summon or a shadow keeps pace with the creatures of its master's level: its blows grow as their health does,
     * its health as their blows do. At least what it was made with.
     */
    public static double summonDamage(SystemData d) {
        return d.isAwakened() ? Math.max(1, healthAt(powerLevel(d))) : 1;
    }

    public static double summonHealth(SystemData d) {
        return d.isAwakened() ? Math.max(1, damageAt(powerLevel(d))) : 1;
    }

    /** A flat bonus blow (a gear proc, a burst) by this attacker, grown the way its other blows grow. */
    public static float flatDamage(@Nullable Entity attacker, float amount) {
        return attacker instanceof Player p ? (float) (amount * power(p)) : amount;
    }

    /**
     * A part of a skill's damage that is already in today's sizes (health paid, blows taken and thrown back): divided
     * by the hunter's power, so the skill hit, which multiplies everything by it, leaves this part as it is.
     */
    public static float keepSize(Player p, float amount) {
        return (float) (amount / power(p));
    }

    /**
     * Armour works on a blow's size next to the body it lands on: a body of {@code n} times the vanilla 20 health takes
     * a blow as if it were {@code n} times smaller, so armour keeps its share against big numbers.
     */
    public static float armorScale(LivingEntity e) {
        return Math.max(1f, e.getMaxHealth() / 20f);
    }

    // ------------------------------------------------------------------ heals

    private static int rawHeals;

    /**
     * Heals by an amount already sized for today's health pools (a share of max health, a share of the damage dealt),
     * so it is not grown by {@link #legacyScale} like flat heals are.
     */
    public static void healRaw(LivingEntity e, float amount) {
        rawHeals++;
        try {
            e.heal(amount);
        } finally {
            rawHeals--;
        }
    }

    static boolean healingRaw() {
        return rawHeals > 0;
    }

    private Balance() {}
}
