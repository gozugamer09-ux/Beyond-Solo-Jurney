package com.beyondsolo.enemy;

import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.skill.SkillElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * How a creature takes each element: what it is weak to, what it shrugs off, what cannot touch it. Every living
 * thing has one, vanilla monsters included: undead fear light and fire, spiders burn, blazes drown, witches laugh at
 * arcane. On top of what a creature is comes what it is steeped in ({@link Affinity}: a frostbound hound fears fire
 * and shatters under earth) and, for a gate boss, what kind of boss it is.
 *
 * <p>Each source moves an element one step up (weaker) or down (tougher). Steps add up: a creature both weak and
 * resistant to an element takes it normally, and two weaknesses stack into a severe one. An affinity always resists
 * its own element. The result is a {@link Rating} per element, and its multiplier applies to every element hit: skills
 * ({@code SkillCombat.strike}), weapon infusions, element procs (burns, venom, crimson and blood bursts) and buildup.
 * Physical is never rated, so plain weapon swings stay as they were.</p>
 *
 * <p>Works on both sides: the client knows a creature's affinity from {@code EnemyTraitsPayload} and a boss's kind
 * from its synced data, so read-outs (Appraising Gaze, the combat meters) show the same profile the server uses.</p>
 */
public final class ElementalProfile {
    /** How one element lands on a creature, from immune to severely weak. */
    public enum Rating {
        IMMUNE(0f), RESIST_STRONG(0.25f), RESIST(0.5f), NEUTRAL(1f), WEAK(1.5f), WEAK_SEVERE(2f);

        public final float multiplier;

        Rating(float multiplier) {
            this.multiplier = multiplier;
        }

        public boolean weak() {
            return this == WEAK || this == WEAK_SEVERE;
        }

        public boolean resists() {
            return this == RESIST || this == RESIST_STRONG || this == IMMUNE;
        }

        /** The label a hit of this rating carries over the damage number. */
        public Component label() {
            return Component.translatable("element_rating.beyondsolo." + name().toLowerCase(Locale.ROOT));
        }

        public static Rating byIndex(int i) {
            Rating[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, i))];
        }

        static Rating ofSteps(int steps) {
            if (steps <= IMMUNE_STEP) return IMMUNE;
            if (steps <= -2) return RESIST_STRONG;
            if (steps == -1) return RESIST;
            if (steps == 0) return NEUTRAL;
            if (steps == 1) return WEAK;
            return WEAK_SEVERE;
        }
    }

    private static final int IMMUNE_STEP = -9;
    private static final int N = SkillElement.values().length;
    public static final ElementalProfile NONE = new ElementalProfile(new byte[N], "none");

    /** Set by the client: a creature's affinity by entity id (from {@code EnemyTraitsPayload}). */
    public static IntFunction<Affinity> clientAffinity = id -> Affinity.NONE;

    private final byte[] steps;
    private final String key;

    private ElementalProfile(byte[] steps, String key) {
        this.steps = steps;
        this.key = key;
    }

    public Rating rating(@Nullable SkillElement element) {
        if (element == null || element == SkillElement.PHYSICAL) return Rating.NEUTRAL;
        return Rating.ofSteps(steps[element.ordinal()]);
    }

    public float multiplier(@Nullable SkillElement element) {
        return rating(element).multiplier;
    }

    /** Elements this creature is weak to, most severe first. */
    public List<SkillElement> weaknesses() {
        List<SkillElement> out = new ArrayList<>();
        for (int pass = 2; pass >= 1; pass--) {
            for (SkillElement e : SkillElement.values()) if (e != SkillElement.PHYSICAL && steps[e.ordinal()] == pass) out.add(e);
        }
        return out;
    }

    /** Elements this creature resists or is immune to, immune first. */
    public List<SkillElement> resistances() {
        List<SkillElement> out = new ArrayList<>();
        for (SkillElement e : SkillElement.values()) if (e != SkillElement.PHYSICAL && steps[e.ordinal()] <= IMMUNE_STEP) out.add(e);
        for (int pass = -2; pass <= -1; pass++) {
            for (SkillElement e : SkillElement.values()) if (e != SkillElement.PHYSICAL && steps[e.ordinal()] == pass) out.add(e);
        }
        return out;
    }

    /** True if nothing about this creature is out of the ordinary. */
    public boolean plain() {
        for (byte s : steps) if (s != 0) return false;
        return true;
    }

    /**
     * What this profile belongs to ("minecraft:zombie", "beyondsolo:gloomfang#frost", "beyondsolo:gate_boss@wyrm"): the
     * same key for every creature of that kind, so a weakness found on one is known for all of them.
     */
    public String key() {
        return key;
    }

    // ================================================================ lookup

    private static final Map<String, ElementalProfile> CACHE = new HashMap<>();

    /** The profile of a creature as it is now (its kind, its affinity, a boss's kind). Either side. */
    public static ElementalProfile of(LivingEntity e) {
        Affinity affinity = e.level().isClientSide ? clientAffinity.apply(e.getId()) : EnemyTraits.of(e).affinity();
        BossKind boss = e instanceof GateBossEntity b ? b.kind() : null;
        return of(e.getType(), affinity == null ? Affinity.NONE : affinity, boss);
    }

    public static synchronized ElementalProfile of(EntityType<?> type, Affinity affinity, @Nullable BossKind boss) {
        String key = BuiltInRegistries.ENTITY_TYPE.getKey(type) + (boss != null ? "@" + boss.id : "") + (affinity != Affinity.NONE ? "#" + affinity.name().toLowerCase(Locale.ROOT) : "");
        ElementalProfile cached = CACHE.get(key);
        if (cached != null) return cached;
        int[] sum = new int[N];
        boolean[] immune = new boolean[N];
        add(sum, immune, base(type));
        if (boss != null) add(sum, immune, BOSSES.get(boss));
        add(sum, immune, AFFINITIES.get(affinity));
        if (affinity.element != null && !immune[affinity.element.ordinal()]) {
            // whatever else it is, a creature steeped in an element never fears that element
            sum[affinity.element.ordinal()] = Math.min(-1, sum[affinity.element.ordinal()]);
        }
        byte[] steps = new byte[N];
        for (int i = 0; i < N; i++) steps[i] = (byte) (immune[i] ? IMMUNE_STEP : Math.max(-2, Math.min(2, sum[i])));
        steps[SkillElement.PHYSICAL.ordinal()] = 0;
        ElementalProfile p = new ElementalProfile(steps, key);
        CACHE.put(key, p);
        return p;
    }

    private static void add(int[] sum, boolean[] immune, @Nullable int[] row) {
        if (row == null) return;
        for (int i = 0; i < N; i++) {
            if (row[i] <= IMMUNE_STEP) immune[i] = true;
            else sum[i] += row[i];
        }
    }

    // ================================================================ the tables

    private static final Map<EntityType<?>, int[]> TYPES = new IdentityHashMap<>();
    private static final Map<Affinity, int[]> AFFINITIES = new HashMap<>();
    private static final Map<BossKind, int[]> BOSSES = new HashMap<>();
    private static boolean ready;

    private static final int[] UNDEAD = row("light+1 fire+1 shadow-1 nature-1");
    private static final int[] ARTHROPOD = row("fire+1 nature-1");
    private static final int[] FIRE_IMMUNE = row("fire! water+1");
    private static final int[] AQUATIC = row("water-1 lightning+1");
    private static final int[] ILLAGER = row("arcane+1");

    static {
        AFFINITIES.put(Affinity.FLAME, row("water+1 earth+1 fire-1 nature-1"));
        AFFINITIES.put(Affinity.FROST, row("fire+1 earth+1 water-1 air-1"));
        AFFINITIES.put(Affinity.VENOM, row("fire+1 light+1 nature-1 shadow-1"));
        AFFINITIES.put(Affinity.STORM, row("earth+1 water+1 lightning-1 air-1"));
        AFFINITIES.put(Affinity.SHADE, row("light+1 arcane+1 shadow-1 nature-1"));
        AFFINITIES.put(Affinity.BLOOD, row("light+1 fire+1 shadow-1 water-1"));

        BOSSES.put(BossKind.BRUTE, row("water+1 earth-1"));
        BOSSES.put(BossKind.BROODMOTHER, row("fire+1 nature-1"));
        BOSSES.put(BossKind.ALPHA, row("earth+1 air-1"));
        BOSSES.put(BossKind.KNIGHT, row("lightning+1 shadow-1"));
        BOSSES.put(BossKind.MAGUS, row("earth+1 arcane-1"));
        BOSSES.put(BossKind.WYRM, row("air+1 earth-1"));
    }

    private static synchronized void fill() {
        if (ready) return;
        ready = true;
        // vanilla monsters
        TYPES.put(EntityType.HUSK, row("light+1 water+1 shadow-1 nature-1"));
        TYPES.put(EntityType.DROWNED, row("light+1 lightning+1 water-1 nature-1"));
        TYPES.put(EntityType.SKELETON, row("light+1 earth+1 shadow-1 nature-1"));
        TYPES.put(EntityType.STRAY, row("light+1 fire+1 water-1 nature-1"));
        TYPES.put(EntityType.BOGGED, row("light+1 fire+1 nature-2"));
        TYPES.put(EntityType.WITHER_SKELETON, row("fire! light+1 earth+1 shadow-1 nature-1"));
        TYPES.put(EntityType.ZOMBIFIED_PIGLIN, row("fire! light+1 water+1 nature-1"));
        TYPES.put(EntityType.PHANTOM, row("light+1 lightning+1 air-1 shadow-1"));
        TYPES.put(EntityType.CAVE_SPIDER, row("fire+1 nature-2"));
        TYPES.put(EntityType.SILVERFISH, row("fire+1 earth-1"));
        TYPES.put(EntityType.ENDERMITE, row("water+1 arcane-1"));
        TYPES.put(EntityType.BLAZE, row("fire! water+2 air-1"));
        TYPES.put(EntityType.MAGMA_CUBE, row("fire! water+1 earth-1"));
        TYPES.put(EntityType.GHAST, row("fire! water+1 lightning+1 air-1"));
        TYPES.put(EntityType.WITCH, row("arcane-2 nature-1 lightning+1"));
        TYPES.put(EntityType.EVOKER, row("arcane-1 light+1"));
        TYPES.put(EntityType.ILLUSIONER, row("arcane-1 light+1"));
        TYPES.put(EntityType.RAVAGER, row("earth-1 arcane+1"));
        TYPES.put(EntityType.VEX, row("arcane-1 air-1 light+1"));
        TYPES.put(EntityType.CREEPER, row("lightning-2 fire+1"));
        TYPES.put(EntityType.ENDERMAN, row("water+2 arcane-1 shadow-1"));
        TYPES.put(EntityType.SLIME, row("lightning+1 earth-1"));
        TYPES.put(EntityType.ELDER_GUARDIAN, row("water-2 lightning+1"));
        TYPES.put(EntityType.SHULKER, row("arcane-1 earth+1"));
        TYPES.put(EntityType.WARDEN, row("shadow-2 nature-1 light+1"));
        TYPES.put(EntityType.BREEZE, row("air! earth+1"));
        // the gate's own creatures
        TYPES.put(EnemyModule.type(MonsterKind.GLOOMFANG), row("light+1 shadow-1"));
        TYPES.put(EnemyModule.type(MonsterKind.BROODLING), row("fire+1 nature-1"));
        TYPES.put(EnemyModule.type(MonsterKind.SHARDBACK), row("earth+1 arcane-1"));
        TYPES.put(EnemyModule.type(MonsterKind.HOLLOW_ACOLYTE), row("light+1 arcane-1 shadow-1"));
        TYPES.put(EnemyModule.type(MonsterKind.ASHEN_SENTINEL), row("lightning+1 water+1 fire-1 earth-1"));
        TYPES.put(EnemyModule.type(MonsterKind.EMBERWISP), row("fire! water+1 air+1"));
    }

    /** What a kind of creature is, before any affinity: its own row, else what its tags say. */
    private static int[] base(EntityType<?> type) {
        fill();
        int[] own = TYPES.get(type);
        if (own != null) return own;
        int[] sum = new int[N];
        boolean[] immune = new boolean[N];
        if (type.is(EntityTypeTags.UNDEAD)) add(sum, immune, UNDEAD);
        if (type.is(EntityTypeTags.ARTHROPOD)) add(sum, immune, ARTHROPOD);
        if (type.is(EntityTypeTags.AQUATIC)) add(sum, immune, AQUATIC);
        if (type.is(EntityTypeTags.ILLAGER)) add(sum, immune, ILLAGER);
        if (type.fireImmune()) add(sum, immune, FIRE_IMMUNE);
        int[] out = new int[N];
        for (int i = 0; i < N; i++) out[i] = immune[i] ? IMMUNE_STEP : sum[i];
        return out;
    }

    /** "light+1 fire! shadow-1": +n weaker, -n tougher, ! immune. */
    private static int[] row(String spec) {
        int[] out = new int[N];
        for (String part : spec.trim().split("\\s+")) {
            if (part.isEmpty()) continue;
            int split = part.indexOf('+') >= 0 ? part.indexOf('+') : part.indexOf('-') >= 0 ? part.indexOf('-') : part.indexOf('!');
            SkillElement e = SkillElement.valueOf(part.substring(0, split).toUpperCase(Locale.ROOT));
            String n = part.substring(split);
            out[e.ordinal()] = n.equals("!") ? IMMUNE_STEP : Integer.parseInt(n);
        }
        return out;
    }

    /** The elements worth listing (all but physical). */
    public static List<SkillElement> rated() {
        List<SkillElement> out = new ArrayList<>();
        for (SkillElement e : SkillElement.values()) if (e != SkillElement.PHYSICAL) out.add(e);
        return out;
    }

    /** Readable summary for commands: "weak: fire, light / resists: shadow, nature (immune: -)". */
    public String describe() {
        StringBuilder b = new StringBuilder(key).append(": weak ");
        List<SkillElement> weak = weaknesses();
        b.append(weak.isEmpty() ? "-" : String.join(", ", weak.stream().map(e -> e.name().toLowerCase(Locale.ROOT) + (rating(e) == Rating.WEAK_SEVERE ? "!!" : "")).toList()));
        b.append(" / resists ");
        List<SkillElement> res = resistances();
        b.append(res.isEmpty() ? "-" : String.join(", ", res.stream().map(e -> e.name().toLowerCase(Locale.ROOT)
                + (rating(e) == Rating.IMMUNE ? " (immune)" : rating(e) == Rating.RESIST_STRONG ? "!!" : "")).toList()));
        return b.toString();
    }
}
