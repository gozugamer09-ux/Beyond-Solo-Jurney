package com.beyondsolo.gate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * The bosses a gate can hold. A gate picks one from its seed among those that live at its rank, so the same rank
 * does not always end with the same fight. Each has its own model and animations (intro, death and its attacks,
 * see {@code BossMoves}), its own hitbox, an affinity (its elemental strength and weakness) and a name per rank.
 *
 * <p>Loot hook: each kind names a loot table, {@code beyondsolo:gate_boss/<id>}, rolled into the reward cache on top
 * of the rank's loot ({@link GateLoot#signature}). Signature drops go there.
 */
public enum BossKind {
    /** The original: a hulking brute of rock and armour. Swipe, two-fisted slam. */
    BRUTE("brute", "gate_boss", PlayerRank.E, PlayerRank.S, 3, 1.7f, 3.3f, 1f, 1f, Affinity.NONE, "roar"),
    /** A bloated spider queen. Bite, venom spray, broods of young, leaps. */
    BROODMOTHER("broodmother", "boss_broodmother", PlayerRank.E, PlayerRank.A, 3, 3.0f, 2.1f, 0.9f, 0.9f, Affinity.VENOM, "intro"),
    /** A crystal-maned hound. Bites, pounces, howls its pack in. */
    ALPHA("alpha", "boss_alpha", PlayerRank.E, PlayerRank.B, 3, 2.0f, 2.3f, 0.85f, 0.95f, Affinity.FROST, "intro"),
    /** A towering hollow knight. A three-strike cleave, a charge, a line of blades out of the floor. */
    KNIGHT("knight", "boss_knight", PlayerRank.D, PlayerRank.S, 3, 1.5f, 3.6f, 1f, 1.1f, Affinity.SHADE, "intro"),
    /** A floating masked caster. Bolts, sigils under your feet, blinks away, raises acolytes. */
    MAGUS("magus", "boss_magus", PlayerRank.C, PlayerRank.S, 3, 1.4f, 3.7f, 0.8f, 1f, Affinity.STORM, "intro"),
    /** A drake of ash and embers. Fire breath, a tail sweep, a wing buffet that throws you back. */
    WYRM("wyrm", "boss_wyrm", PlayerRank.B, PlayerRank.S, 3, 4.2f, 3.8f, 1.25f, 1.05f, Affinity.FLAME, "intro");

    public final String id;
    /** The model, animation and texture name (textures/entity/&lt;model&gt;_&lt;rank&gt;.png). */
    public final String model;
    public final PlayerRank minRank;
    public final PlayerRank maxRank;
    public final int weight;
    public final float width;
    public final float height;
    public final float healthScale;
    public final float damageScale;
    public final Affinity affinity;
    /** Its intro animation (also played when it enrages). */
    public final String introAnim;

    BossKind(String id, String model, PlayerRank minRank, PlayerRank maxRank, int weight, float width, float height, float healthScale,
             float damageScale, Affinity affinity, String introAnim) {
        this.id = id;
        this.model = model;
        this.minRank = minRank;
        this.maxRank = maxRank;
        this.weight = weight;
        this.width = width;
        this.height = height;
        this.healthScale = healthScale;
        this.damageScale = damageScale;
        this.affinity = affinity;
        this.introAnim = introAnim;
    }

    public static BossKind byIndex(int i) {
        BossKind[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    @Nullable
    public static BossKind byId(String id) {
        for (BossKind k : values()) if (k.id.equalsIgnoreCase(id) || k.name().equalsIgnoreCase(id)) return k;
        return null;
    }

    public boolean livesAt(PlayerRank rank) {
        return rank.ordinal() >= minRank.ordinal() && rank.ordinal() <= maxRank.ordinal();
    }

    /** A boss for a rank (the gate's seed makes it the same every time for that gate). */
    public static BossKind roll(PlayerRank rank, RandomSource r) {
        int total = 0;
        for (BossKind k : values()) if (k.livesAt(rank)) total += k.weight;
        int roll = r.nextInt(Math.max(1, total));
        for (BossKind k : values()) {
            if (!k.livesAt(rank)) continue;
            roll -= k.weight;
            if (roll < 0) return k;
        }
        return BRUTE;
    }

    public static BossKind forSeed(PlayerRank rank, long seed) {
        return roll(rank, RandomSource.create(seed * 31 + 0xB055L));
    }

    /**
     * The boss of a gate of a realm: those the realm favours are four times as likely (seven times for one it names
     * twice), so a hive ends with its broodmother more often than not, but any boss of the rank can still wait there.
     */
    public static BossKind forSeed(PlayerRank rank, long seed, GateRealm realm) {
        RandomSource r = RandomSource.create(seed * 31 + 0xB055L);
        int total = 0;
        for (BossKind k : values()) if (k.livesAt(rank)) total += k.weight * (1 + 3 * realm.bossWeight(k));
        int roll = r.nextInt(Math.max(1, total));
        for (BossKind k : values()) {
            if (!k.livesAt(rank)) continue;
            roll -= k.weight * (1 + 3 * realm.bossWeight(k));
            if (roll < 0) return k;
        }
        return BRUTE;
    }

    /** About one gate in twelve holds an ascended boss: bigger, tougher, its title in gold. */
    public static boolean ascendedForSeed(long seed) {
        return RandomSource.create(seed * 17 + 0xA5CL).nextInt(12) == 0;
    }

    /** Its name at a rank: the brute keeps the gate's own boss names; the others have one per rank. */
    public Component name(PlayerRank rank) {
        if (this == BRUTE) return Component.translatable("gate.beyondsolo.boss." + rank.name().toLowerCase());
        return Component.translatable("gate.beyondsolo.boss." + id + "." + rank.name().toLowerCase());
    }

    public ResourceLocation lootTable() {
        return BeyondSolo.id("gate_boss/" + id);
    }
}
