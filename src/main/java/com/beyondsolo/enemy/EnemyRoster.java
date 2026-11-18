package com.beyondsolo.enemy;

import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Which creatures live behind a gate of each rank, and how a single gate (or a dungeon break) picks its own mix.
 *
 * <p>Each rank has a weighted pool of the gate's own creatures ({@link MonsterKind}); no vanilla monster lives in a
 * gate. A gate draws three to five kinds from its rank's pool (always at least one that fights up close) and one
 * ruling affinity, both from its seed, so two gates of the same rank feel different: a frostbound hound den, a
 * venomous hive, a hall of storm-wreathed sentinels and acolytes. Most of its creatures carry its affinity; elites and
 * champions always carry one.</p>
 */
public final class EnemyRoster {
    public record Entry(Supplier<EntityType<?>> type, int weight, boolean own) {}

    /** A gate's (or a break's) creatures: the kinds it draws from and its ruling affinity. */
    public record Mix(List<Entry> entries, Affinity affinity) {
        public EntityType<?> pick(RandomSource r) {
            return weighted(entries, r).type.get();
        }

        /** The affinity of one creature of this mix. */
        public Affinity affinityFor(EntityType<?> type, RandomSource r, boolean special) {
            boolean own = false;
            for (Entry e : entries) if (e.type.get() == type) own = e.own;
            float ruling = own ? 0.7f : 0.25f;
            if (r.nextFloat() < ruling && affinity != Affinity.NONE) return affinity;
            if (special || (own && r.nextFloat() < 0.4f)) {
                Affinity[] all = Affinity.values();
                return all[1 + r.nextInt(all.length - 1)];
            }
            return affinity == Affinity.NONE && special ? Affinity.STORM : Affinity.NONE;
        }

        /** The kinds in it, for read-outs and tests. */
        public List<EntityType<?>> types() {
            List<EntityType<?>> out = new ArrayList<>();
            for (Entry e : entries) out.add(e.type.get());
            return out;
        }
    }

    private static final List<List<Entry>> POOLS = new ArrayList<>();
    private static final int[][] AFFINITY_WEIGHTS = {
            // NONE, FLAME, FROST, VENOM, STORM, SHADE, BLOOD
            {3, 0, 1, 1, 0, 0, 2},
            {2, 1, 1, 3, 0, 0, 0},
            {1, 0, 3, 1, 2, 0, 0},
            {0, 0, 1, 2, 2, 3, 0},
            {0, 3, 0, 0, 2, 1, 1},
            {0, 2, 0, 0, 1, 2, 3},
    };

    static {
        for (int i = 0; i < PlayerRank.values().length; i++) POOLS.add(new ArrayList<>());
        add(PlayerRank.E, own(MonsterKind.GLOOMFANG, 4), own(MonsterKind.BROODLING, 4), own(MonsterKind.GUTTERKIN, 4), own(MonsterKind.GRAVE_THRALL, 3),
                own(MonsterKind.BONE_ARCHER, 3), own(MonsterKind.HIVE_DRONE, 3), own(MonsterKind.DUSKWING, 2), own(MonsterKind.THORN_SPITTER, 2));
        add(PlayerRank.D, own(MonsterKind.GLOOMFANG, 3), own(MonsterKind.BROODLING, 3), own(MonsterKind.GUTTERKIN, 3), own(MonsterKind.SHARDBACK, 3),
                own(MonsterKind.GRAVE_THRALL, 3), own(MonsterKind.BONE_ARCHER, 3), own(MonsterKind.DEEPKIN, 3), own(MonsterKind.EMBERWISP, 2),
                own(MonsterKind.DUSKWING, 2), own(MonsterKind.HIVE_DRONE, 2), own(MonsterKind.COILFANG, 2), own(MonsterKind.THORN_SPITTER, 2),
                own(MonsterKind.BRIMLING, 2));
        add(PlayerRank.C, own(MonsterKind.SHARDBACK, 3), own(MonsterKind.HOLLOW_ACOLYTE, 3), own(MonsterKind.RIME_STALKER, 3), own(MonsterKind.COILFANG, 3),
                own(MonsterKind.GLOOMFANG, 2), own(MonsterKind.ASHEN_SENTINEL, 2), own(MonsterKind.BROODLING, 2), own(MonsterKind.TUSK_SHAMAN, 2),
                own(MonsterKind.TUSKBRUTE, 2), own(MonsterKind.DEEPKIN, 2), own(MonsterKind.CRAGCRAWLER, 2), own(MonsterKind.STATIC_DRIFTER, 2),
                own(MonsterKind.BRIMLING, 2), own(MonsterKind.BONE_ARCHER, 2), own(MonsterKind.GRAVE_THRALL, 2), own(MonsterKind.THORN_SPITTER, 1));
        add(PlayerRank.B, own(MonsterKind.HOLLOW_ACOLYTE, 3), own(MonsterKind.ASHEN_SENTINEL, 3), own(MonsterKind.EMBERWISP, 3), own(MonsterKind.TUSKBRUTE, 3),
                own(MonsterKind.HIVE_REAVER, 3), own(MonsterKind.WAILING_WRAITH, 3), own(MonsterKind.SHARDBACK, 2), own(MonsterKind.TUSK_SHAMAN, 2),
                own(MonsterKind.RIME_STALKER, 2), own(MonsterKind.CRAGCRAWLER, 2), own(MonsterKind.STATIC_DRIFTER, 2), own(MonsterKind.PIT_RAVAGER, 2),
                own(MonsterKind.RIMECLAW, 2), own(MonsterKind.BRIMLING, 2));
        add(PlayerRank.A, own(MonsterKind.ASHEN_SENTINEL, 3), own(MonsterKind.RIME_STALKER, 3), own(MonsterKind.HIVE_REAVER, 3), own(MonsterKind.WAILING_WRAITH, 3),
                own(MonsterKind.PIT_RAVAGER, 3), own(MonsterKind.RIMECLAW, 3), own(MonsterKind.HOLLOW_ACOLYTE, 2), own(MonsterKind.EMBERWISP, 2),
                own(MonsterKind.GLOOMFANG, 2), own(MonsterKind.TUSKBRUTE, 2), own(MonsterKind.RUNE_COLOSSUS, 2), own(MonsterKind.STATIC_DRIFTER, 2),
                own(MonsterKind.TUSK_SHAMAN, 2), own(MonsterKind.CRAGCRAWLER, 2));
        add(PlayerRank.S, own(MonsterKind.ASHEN_SENTINEL, 3), own(MonsterKind.RIME_STALKER, 3), own(MonsterKind.HIVE_REAVER, 3), own(MonsterKind.WAILING_WRAITH, 3),
                own(MonsterKind.PIT_RAVAGER, 3), own(MonsterKind.RIMECLAW, 3), own(MonsterKind.RUNE_COLOSSUS, 3), own(MonsterKind.HOLLOW_ACOLYTE, 2),
                own(MonsterKind.EMBERWISP, 2), own(MonsterKind.SHARDBACK, 2), own(MonsterKind.TUSKBRUTE, 2), own(MonsterKind.BRIMLING, 2),
                own(MonsterKind.STATIC_DRIFTER, 2));
    }

    /** The awakening trial's dead: thralls, archers and gutterkin, plain (its own light is the only element there). */
    private static final Mix TRIAL = new Mix(List.of(own(MonsterKind.GRAVE_THRALL, 4), own(MonsterKind.BONE_ARCHER, 4), own(MonsterKind.GUTTERKIN, 2)),
            Affinity.NONE);

    private static Entry own(MonsterKind kind, int weight) {
        return new Entry(() -> EnemyModule.type(kind), weight, true);
    }

    private static void add(PlayerRank rank, Entry... entries) {
        POOLS.get(rank.ordinal()).addAll(List.of(entries));
    }

    public static List<Entry> pool(PlayerRank rank) {
        return POOLS.get(rank.ordinal());
    }

    /** Each rank's {@link #basePower}, worked out the first time it is needed (the kinds' types must be registered). */
    private static volatile double[] basePowers;

    /**
     * How strong a rank's typical creature is at its kinds' own numbers, before its rank grows it: the weighted average
     * of its pool's {@link HiddenLevel#power}. The higher ranks' pools hold heavier kinds (an S pool's average is about
     * two and a half times an E pool's); {@link Enemies#poolScale} evens that out.
     */
    public static double basePower(PlayerRank rank) {
        double[] powers = basePowers;
        if (powers == null) {
            powers = new double[PlayerRank.values().length];
            for (PlayerRank r : PlayerRank.values()) {
                double sum = 0;
                int weight = 0;
                for (Entry e : pool(r)) {
                    MonsterKind kind = kindOf(e.type.get());
                    if (kind == null) continue;
                    sum += HiddenLevel.power(kind.health, kind.damage, kind.armor, 0) * e.weight;
                    weight += e.weight;
                }
                powers[r.ordinal()] = weight == 0 ? 1 : sum / weight;
            }
            basePowers = powers;
        }
        return powers[rank.ordinal()];
    }

    /** A gate's mix, the same every time for the same seed. */
    public static Mix forSeed(PlayerRank rank, long seed) {
        return random(rank, RandomSource.create(seed ^ 0x5EED_BEA5_7L));
    }

    /**
     * A gate's mix for a realm ({@code gate.GateRealm}): the same every time for the same seed, with the creatures
     * that live where the realm is ({@link Habitat}) four times as likely, and the realm's ruling affinity if it has one.
     */
    public static Mix forSeed(PlayerRank rank, long seed, Collection<Habitat> habitats, @Nullable Affinity ruling) {
        return random(rank, RandomSource.create(seed ^ 0x5EED_BEA5_7L), habitats, ruling);
    }

    /** The awakening trial's creatures. */
    public static Mix trial() {
        return TRIAL;
    }

    /** A fresh mix for a rank (dungeon breaks, events). */
    public static Mix random(PlayerRank rank, RandomSource r) {
        return random(rank, r, List.of(), null);
    }

    /**
     * A fresh mix for a rank in a place: three to five kinds, always one that fights up close, with creatures carrying
     * one of {@code habitats} four times as likely and at least two of the chosen kinds carrying one (when the pool
     * has two). {@code ruling} replaces the rank's affinity roll; null keeps it.
     */
    public static Mix random(PlayerRank rank, RandomSource r, Collection<Habitat> habitats, @Nullable Affinity ruling) {
        List<Entry> pool = new ArrayList<>();
        for (Entry e : pool(rank)) {
            pool.add(Habitat.fits(e.type.get(), habitats) ? new Entry(e.type, e.weight * 4, e.own) : e);
        }
        int kinds = Math.min(pool.size(), 3 + (rank.tier() >= 2 ? 1 : 0) + (r.nextInt(4) == 0 ? 1 : 0));
        List<Entry> chosen = new ArrayList<>();
        // always one that fights up close (one that lives here, if any does): a cave of archers and spitters alone
        // would be a turret gallery
        List<Entry> front = pool.stream().filter(e -> isFront(e.type.get())).toList();
        List<Entry> frontHere = front.stream().filter(e -> Habitat.fits(e.type.get(), habitats)).toList();
        if (!frontHere.isEmpty()) chosen.add(weighted(frontHere, r));
        else if (!front.isEmpty()) chosen.add(weighted(front, r));
        // at least two that belong here
        List<Entry> fitting = pool.stream().filter(e -> Habitat.fits(e.type.get(), habitats)).toList();
        while (countFitting(chosen, habitats) < 2 && countFitting(chosen, habitats) < fitting.size()) {
            Entry e = weighted(fitting, r);
            if (!contains(chosen, e)) chosen.add(e);
        }
        while (chosen.size() < kinds) {
            Entry e = weighted(pool, r);
            if (!contains(chosen, e)) chosen.add(e);
        }
        if (ruling != null) return new Mix(List.copyOf(chosen), ruling);
        int[] w = AFFINITY_WEIGHTS[rank.ordinal()];
        int total = 0;
        for (int x : w) total += x;
        int roll = r.nextInt(total);
        Affinity affinity = Affinity.NONE;
        for (int i = 0; i < w.length; i++) {
            roll -= w[i];
            if (roll < 0) {
                affinity = Affinity.values()[i];
                break;
            }
        }
        return new Mix(List.copyOf(chosen), affinity);
    }

    private static long countFitting(List<Entry> chosen, Collection<Habitat> habitats) {
        return chosen.stream().filter(e -> Habitat.fits(e.type.get(), habitats)).count();
    }

    /** Entries are compared by creature: a weighted copy is the same kind as the pool's own entry. */
    private static boolean contains(List<Entry> chosen, Entry e) {
        for (Entry c : chosen) if (c.type.get() == e.type.get()) return true;
        return false;
    }

    /** Whether a creature of this type closes in and fights hand to hand. */
    public static boolean isFront(EntityType<?> type) {
        MonsterKind kind = kindOf(type);
        if (kind == null) return true;
        return switch (kind.style) {
            case ARCHER, CASTER, TURRET, BOMBER, SWOOPER -> false;
            default -> true;
        };
    }

    /** The gate creature kind of an entity type, or null for anything else. */
    @Nullable
    public static MonsterKind kindOf(EntityType<?> type) {
        for (MonsterKind k : MonsterKind.values()) if (EnemyModule.type(k) == type) return k;
        return null;
    }

    static Entry weighted(List<Entry> entries, RandomSource r) {
        int total = 0;
        for (Entry e : entries) total += e.weight;
        int roll = r.nextInt(Math.max(1, total));
        for (Entry e : entries) {
            roll -= e.weight;
            if (roll < 0) return e;
        }
        return entries.get(0);
    }

    /** "gloomfang, zombie, broodling (frost)" for commands. */
    public static String describe(Mix mix) {
        StringBuilder b = new StringBuilder();
        for (EntityType<?> t : mix.types()) {
            if (!b.isEmpty()) b.append(", ");
            b.append(BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath());
        }
        return b + " (" + mix.affinity().name().toLowerCase() + ")";
    }

    private EnemyRoster() {}
}
