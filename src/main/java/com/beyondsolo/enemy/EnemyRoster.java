package com.beyondsolo.enemy;

import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Which creatures live behind a gate of each rank, and how a single gate (or a dungeon break) picks its own mix.
 *
 * <p>Each rank has a weighted pool of vanilla monsters and the roster's own ({@link MonsterKind}). A gate draws
 * three or four kinds from its rank's pool (at least two of the roster's own) and one ruling affinity, both from its
 * seed, so two gates of the same rank feel different: a frostbound hound den, a venomous brood nest, a hall of
 * storm-wreathed sentinels and acolytes. Most of the gate's own creatures carry its affinity; vanilla monsters
 * sometimes do; elites and champions always carry one.
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
        add(PlayerRank.E, v(EntityType.ZOMBIE, 4), v(EntityType.SPIDER, 3), v(EntityType.SKELETON, 3), own(MonsterKind.GLOOMFANG, 4), own(MonsterKind.BROODLING, 4));
        add(PlayerRank.D, v(EntityType.HUSK, 3), v(EntityType.CAVE_SPIDER, 3), v(EntityType.SKELETON, 2), own(MonsterKind.GLOOMFANG, 4),
                own(MonsterKind.BROODLING, 3), own(MonsterKind.SHARDBACK, 3), own(MonsterKind.EMBERWISP, 2));
        add(PlayerRank.C, v(EntityType.STRAY, 3), v(EntityType.ZOMBIE, 2), v(EntityType.VINDICATOR, 2), own(MonsterKind.GLOOMFANG, 3),
                own(MonsterKind.SHARDBACK, 3), own(MonsterKind.HOLLOW_ACOLYTE, 3), own(MonsterKind.ASHEN_SENTINEL, 2), own(MonsterKind.BROODLING, 2));
        add(PlayerRank.B, v(EntityType.VINDICATOR, 3), v(EntityType.WITCH, 2), v(EntityType.CAVE_SPIDER, 2), own(MonsterKind.HOLLOW_ACOLYTE, 3),
                own(MonsterKind.ASHEN_SENTINEL, 3), own(MonsterKind.SHARDBACK, 3), own(MonsterKind.EMBERWISP, 3), own(MonsterKind.BROODLING, 2));
        add(PlayerRank.A, v(EntityType.WITHER_SKELETON, 3), v(EntityType.BLAZE, 2), v(EntityType.VINDICATOR, 2), own(MonsterKind.ASHEN_SENTINEL, 3),
                own(MonsterKind.HOLLOW_ACOLYTE, 3), own(MonsterKind.EMBERWISP, 3), own(MonsterKind.GLOOMFANG, 3), own(MonsterKind.SHARDBACK, 2));
        add(PlayerRank.S, v(EntityType.WITHER_SKELETON, 3), v(EntityType.EVOKER, 2), v(EntityType.BLAZE, 2), own(MonsterKind.ASHEN_SENTINEL, 4),
                own(MonsterKind.HOLLOW_ACOLYTE, 3), own(MonsterKind.EMBERWISP, 3), own(MonsterKind.SHARDBACK, 3), own(MonsterKind.GLOOMFANG, 2));
    }

    private static Entry v(EntityType<?> type, int weight) {
        return new Entry(() -> type, weight, false);
    }

    private static Entry own(MonsterKind kind, int weight) {
        return new Entry(() -> EnemyModule.type(kind), weight, true);
    }

    private static void add(PlayerRank rank, Entry... entries) {
        POOLS.get(rank.ordinal()).addAll(List.of(entries));
    }

    public static List<Entry> pool(PlayerRank rank) {
        return POOLS.get(rank.ordinal());
    }

    /** A gate's mix, the same every time for the same seed. */
    public static Mix forSeed(PlayerRank rank, long seed) {
        return random(rank, RandomSource.create(seed ^ 0x5EED_BEA5_7L));
    }

    /** A fresh mix for a rank (dungeon breaks, events). */
    public static Mix random(PlayerRank rank, RandomSource r) {
        List<Entry> pool = new ArrayList<>(pool(rank));
        int kinds = rank.tier() >= 3 ? 4 : 3;
        List<Entry> chosen = new ArrayList<>();
        // at least two of the roster's own creatures
        List<Entry> own = pool.stream().filter(Entry::own).toList();
        while (chosen.stream().filter(Entry::own).count() < 2 && chosen.size() < own.size()) {
            Entry e = weighted(own, r);
            if (!chosen.contains(e)) chosen.add(e);
        }
        while (chosen.size() < kinds && chosen.size() < pool.size()) {
            Entry e = weighted(pool, r);
            if (!chosen.contains(e)) chosen.add(e);
        }
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
