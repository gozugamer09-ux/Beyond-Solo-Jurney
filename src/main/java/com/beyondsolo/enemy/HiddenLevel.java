package com.beyondsolo.enemy;

import com.beyondsolo.gate.DungeonBoss;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Every System creature's hidden level: the level of the hunter it is an even match for. Nobody sees the number. A
 * hunter with the System sees the creature's name over its head in the colour of its {@link Threat}: white when it is
 * weaker than them, orange when it is about their level or a little above, red when it is far stronger.
 *
 * <p>The level comes from what the creature really is once everything is on it (its kind, gate rank, variant, size,
 * affixes, a red gate's fury, a boss's kind and ascension): its staying power (health, stretched by its armour) times
 * the weight of its blows, the product an even fight between two sides comes down to ({@link #power}). {@link #powerAt}
 * is the same measure for a hunter of each level: an E gate's typical creature ({@link EnemyRoster#basePower}) grown
 * along the power curve in {@link Balance} ({@link Balance#strengthAt}). A gate's typical plain creature stands at its
 * rank's home level (E 3, D 13, C 26, B 41, A 56, S 74; each rank's pool is evened out to that, see
 * {@link Enemies#poolScale}), and the curve goes on past S and the level cap. A hunter's own side of the comparison is
 * their level plus every stat point they have beyond what their levels gave, {@link #POINTS_PER_LEVEL} to a level
 * ({@link #hunterLevel}).</p>
 *
 * <p>A creature's level is worked out the first time it is needed (when a player first sees it) and kept in its
 * persistent data; code that makes a creature stronger or weaker after it appeared calls {@link #refresh}. All numbers
 * are first guesses for the balancing pass.</p>
 */
public final class HiddenLevel {
    /** Persistent-data key of a creature's hidden level. */
    public static final String KEY = "beyondsolo_level";
    /** Stat points beyond what a hunter's levels gave them that count as one more level ({@link Balance#POINTS_PER_LEVEL}). */
    public static final float POINTS_PER_LEVEL = Balance.POINTS_PER_LEVEL;
    public static final int MAX = 9999;

    /** How a creature stands against the hunter looking at it, and the colour its name is shown in. */
    public enum Threat {
        /** Weaker than you: a white name. */
        WEAKER(0xF2F5FA),
        /** About your level, or a little above: an orange name. */
        EVEN(0xFFA43A),
        /** Far stronger than you: a red name. */
        FAR_STRONGER(0xFF4242);

        /** Below this share of the hunter's power a creature is weaker (two or three levels below, in the middle levels). */
        public static final float WEAKER_BELOW = 0.85f;
        /** From this many times the hunter's power a creature is far stronger. */
        public static final float FAR_FROM = 1.5f;

        private final int color;

        Threat(int color) {
            this.color = color;
        }

        /** RGB. */
        public int color() {
            return color;
        }

        public String key() {
            return name().toLowerCase();
        }

        /** "white", "orange", "red" in the player's language, in its own colour. */
        public Component word() {
            return Component.translatable("enemy.beyondsolo.threat." + key()).withColor(color);
        }

        /** A creature of {@code creatureLevel} against a hunter of {@code hunterLevel} ({@link #hunterLevel}). */
        public static Threat of(int creatureLevel, float hunterLevel) {
            double ratio = powerAt(creatureLevel) / powerAt(hunterLevel);
            if (ratio < WEAKER_BELOW) return WEAKER;
            if (ratio < FAR_FROM) return EVEN;
            return FAR_STRONGER;
        }
    }

    /** Has a hidden level at all: one of the roster's creatures (it carries traits or is a gate creature) or a dungeon's boss. */
    public static boolean has(LivingEntity e) {
        return !(e instanceof Player) && (EnemyTraits.has(e) || e instanceof GateMonsterEntity || e instanceof DungeonBoss);
    }

    /** A creature's hidden level. Worked out and kept the first time it is asked for. */
    public static int of(LivingEntity e) {
        CompoundTag data = e.getPersistentData();
        if (data.contains(KEY)) return data.getInt(KEY);
        int level = compute(e);
        if (!e.level().isClientSide) data.putInt(KEY, level);
        return level;
    }

    /** Drops a creature's kept level, so it is worked out again the next time it is needed (before anyone has seen it). */
    public static void forget(LivingEntity e) {
        e.getPersistentData().remove(KEY);
    }

    /** Works the level out again (after something made the creature stronger or weaker) and tells everyone who sees it. */
    public static int refresh(LivingEntity e) {
        int level = compute(e);
        if (e.level().isClientSide) return level;
        e.getPersistentData().putInt(KEY, level);
        EnemyHooks.broadcast(e);
        return level;
    }

    /** The level a creature is as it stands now. */
    public static int compute(LivingEntity e) {
        return levelFor(power(e));
    }

    /** How much a creature is worth in a fight: see {@link #power(double, double, double, double)}. */
    public static double power(LivingEntity e) {
        return power(e.getMaxHealth(), value(e, Attributes.ATTACK_DAMAGE, 1), value(e, Attributes.ARMOR, 0), value(e, Attributes.ARMOR_TOUGHNESS, 0));
    }

    /**
     * Staying power (health, stretched by armour) times the weight of a blow, as one number on the scale of a blow: two
     * sides with the same power wear each other down at the same pace.
     */
    public static double power(double health, double damage, double armor, double toughness) {
        double stretch = 1 + 0.04 * Math.min(30, Math.max(0, armor)) + 0.01 * Math.max(0, toughness);
        return Math.sqrt(Math.max(1, health) * stretch * Math.max(0.5, damage));
    }

    /** The power of a hunter of this level (fractions allowed): the even match for a creature of it. */
    public static double powerAt(double level) {
        return typical() * Balance.strengthAt(level);
    }

    /** The level whose hunter has this power (at least 1). */
    public static int levelFor(double power) {
        double level = Balance.levelForStrength(power / typical());
        return (int) Math.max(1, Math.min(MAX, Math.round(level)));
    }

    /** The power of an E gate's typical creature: the even match for a hunter at E's home level. */
    private static double typical() {
        return Math.max(0.01, EnemyRoster.basePower(com.beyondsolo.system.PlayerRank.E));
    }

    /**
     * Where a hunter stands on the same scale: their level, plus whatever stat points they have beyond what their
     * levels gave (rank bonus, rewards, the job change), {@link #POINTS_PER_LEVEL} to a level, and never below E's
     * home level ({@link Balance#powerLevel}). Unspent points count: they are strength the hunter has, just not placed
     * yet. Works on both sides (the client has its own System data).
     */
    public static float hunterLevel(SystemData d) {
        return Balance.powerLevel(d);
    }

    public static Threat threat(LivingEntity creature, Player hunter) {
        return Threat.of(of(creature), hunterLevel(SystemAPI.get(hunter)));
    }

    // ------------------------------------------------------------------ the first time

    private static final String TOLD = "beyondsolo_creature_names_told";

    /** The first time a hunter with the System sees a creature, the System tells them what the colours of names mean. */
    static void introduce(ServerPlayer player) {
        if (!SystemAPI.hasSystem(player)) return;
        CompoundTag root = player.getPersistentData();
        CompoundTag kept = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (kept.getBoolean(TOLD)) return;
        kept.putBoolean(TOLD, true);
        root.put(Player.PERSISTED_NBT_TAG, kept);
        Notifications.send(player, SystemNotification.of(SystemNotification.Kind.INFO,
                Component.translatable("notify.beyondsolo.creature_names.title"),
                Component.translatable("notify.beyondsolo.creature_names.body", Threat.WEAKER.word(), Threat.EVEN.word(), Threat.FAR_STRONGER.word())));
    }

    private static double value(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double fallback) {
        AttributeInstance inst = e.getAttribute(attribute);
        return inst == null ? fallback : inst.getValue();
    }

    private HiddenLevel() {}
}
