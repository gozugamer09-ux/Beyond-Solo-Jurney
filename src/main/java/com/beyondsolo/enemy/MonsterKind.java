package com.beyondsolo.enemy;

import com.beyondsolo.BeyondSolo;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * The gate's own creatures (GeckoLib, made by tools/gen_creatures.py; the gloomfang by tools/gen_enemy_assets.py).
 * Each is its own entity type ({@code beyondsolo:<id>}) but they share one class, {@link GateMonsterEntity}, which
 * fights the way its {@link Style} says. Every one of them can carry any {@link Affinity}: it picks the matching
 * texture.
 *
 * <p>The first six are the originals: every later creature names one of them as its {@link #kin}, whose drops it
 * shares (its crafting material, its rune art and its armour sets), so a new creature never needs a loot table of its
 * own.</p>
 */
public enum MonsterKind {
    /** A lean cave hound that hunts in packs and leaps at its prey. */
    GLOOMFANG("gloomfang", Style.HOUND, 0.8f, 0.95f, 18, 4, 0.34, 2, "bite", null, 20, null, 0x3A3A44, 0x9FD8FF),
    /** A spider the size of a dog, in swarms. A broodmother's young. */
    BROODLING("broodling", Style.SWARM, 0.7f, 0.5f, 8, 2.5f, 0.33, 0, "bite", null, 20, null, 0x2E2430, 0xB06AD0),
    /** A squat beetle with a back of crystal: slow, armoured, and it charges. */
    SHARDBACK("shardback", Style.CHARGER, 1.3f, 1.05f, 36, 6, 0.21, 9, "bite", null, 30, null, 0x4A4640, 0x7FE0C8),
    /** A robed caster in a bone mask that keeps its distance and throws bolts of its element. */
    HOLLOW_ACOLYTE("hollow_acolyte", Style.CASTER, 0.6f, 1.95f, 22, 5, 0.25, 2, "cast", null, 24, null, 0x34303F, 0xD9C27A),
    /** An armoured soldier with a tower shield: hits from the front glance off it. */
    ASHEN_SENTINEL("ashen_sentinel", Style.GUARD, 0.7f, 2.05f, 30, 6, 0.24, 10, "attack", "block", 28, null, 0x3A3A40, 0xC9A35A),
    /** A floating, burning skull that flies at you and bursts. */
    EMBERWISP("emberwisp", Style.BOMBER, 0.6f, 0.65f, 10, 7, 0.3, 0, "attack", null, 10, null, 0x46403C, 0xFF8030),

    /** A goblin-like raider: darts in with a cleaver, cuts, and darts back out. */
    GUTTERKIN("gutterkin", Style.SKIRMISHER, 0.6f, 1.3f, 12, 3, 0.33, 1, "slash", "dash", 22, BROODLING, 0x4E5A2E, 0xFFC43A),
    /** A rotting corpse in rusted mail: slow, and it gets back up once unless it is finished while it is down. */
    GRAVE_THRALL("grave_thrall", Style.SHAMBLER, 0.6f, 1.95f, 20, 4, 0.21, 3, "attack", "revive", 28, ASHEN_SENTINEL, 0x3E4438, 0x96FFE1),
    /** A hooded skeleton with a longbow that keeps its distance. */
    BONE_ARCHER("bone_archer", Style.ARCHER, 0.6f, 1.95f, 16, 4, 0.26, 1, "shoot", null, 24, GLOOMFANG, 0x2E3038, 0x82BEFF),
    /** A gaunt shadow armoured in ice with blades for hands: it closes the distance in a blink. */
    RIME_STALKER("rime_stalker", Style.SKIRMISHER, 0.6f, 2.2f, 24, 6, 0.32, 3, "slash", "dash", 24, GLOOMFANG, 0x1A1E2A, 0x8CDCFF),
    /** A hulking tusked brute with a great axe: heavy blows, and a slam that shakes the ground. */
    TUSKBRUTE("tuskbrute", Style.BRUTE, 1.1f, 2.2f, 48, 9, 0.24, 6, "smash", "slam", 32, SHARDBACK, 0x5A4636, 0xFF6030),
    /** A tusked shaman with a skull staff: throws bolts and mends the creatures around it. */
    TUSK_SHAMAN("tusk_shaman", Style.CASTER, 0.7f, 2.1f, 24, 5, 0.25, 2, "cast", "mend", 28, HOLLOW_ACOLYTE, 0x4A3A2E, 0x5AF0C8, Quirk.MENDS),
    /** A fish-man from flooded gates with a barbed spear: it lunges from out of reach. */
    DEEPKIN("deepkin", Style.LUNGER, 0.7f, 1.9f, 22, 5, 0.28, 3, "thrust", null, 24, ASHEN_SENTINEL, 0x2A4048, 0x5AFFE6),
    /** A great bat: circles overhead and swoops to bite. */
    DUSKWING("duskwing", Style.SWOOPER, 0.9f, 0.8f, 12, 3.5f, 0.32, 0, "bite", null, 20, EMBERWISP, 0x30262C, 0xFF3838),
    /** A wasp as big as a cat, in swarms: dives in to sting. */
    HIVE_DRONE("hive_drone", Style.SWOOPER, 0.7f, 0.6f, 8, 3, 0.34, 0, "sting", null, 20, BROODLING, 0x3A3018, 0xFF961E),
    /** A winged imp that throws fire and blinks away when you close in. */
    BRIMLING("brimling", Style.CASTER, 0.6f, 1.25f, 14, 5, 0.3, 1, "cast", "blink", 20, EMBERWISP, 0x601A1A, 0xFF9628, Quirk.BLINKS),
    /** A drifting ghost with long claws whose wail saps strength from everyone near. */
    WAILING_WRAITH("wailing_wraith", Style.HAUNT, 0.7f, 2.0f, 26, 6, 0.24, 2, "claw", "wail", 24, HOLLOW_ACOLYTE, 0x2E3038, 0xC8FFE1),
    /** A floating bell of light trailing stinging threads: it zaps what it touches and discharges in bursts. */
    STATIC_DRIFTER("static_drifter", Style.HAUNT, 0.8f, 1.4f, 18, 5, 0.2, 0, "zap", "pulse", 24, EMBERWISP, 0x283A4A, 0x78FFFF),
    /** A great bear grown over with ice: raking swipes, and it rears to slam the ground. */
    RIMECLAW("rimeclaw", Style.BRUTE, 1.3f, 1.5f, 60, 10, 0.26, 6, "swipe", "slam", 40, GLOOMFANG, 0x5A6678, 0x82DCFF),
    /** A horned demon bull from the pits: it scrapes the ground and charges. */
    PIT_RAVAGER("pit_ravager", Style.CHARGER, 1.5f, 1.7f, 56, 9, 0.27, 7, "gore", "charge", 40, SHARDBACK, 0x581E1C, 0xFF6E1E),
    /** A great hooded serpent that strikes from further away than you think. */
    COILFANG("coilfang", Style.LUNGER, 1.0f, 1.2f, 30, 7, 0.27, 3, "strike", null, 24, BROODLING, 0x363A28, 0xFFCE3C),
    /** A crab with a shell of rock: its claws ward off blows from the front. */
    CRAGCRAWLER("cragcrawler", Style.GUARD, 1.5f, 1.0f, 40, 6, 0.22, 12, "pinch", "block", 28, SHARDBACK, 0x625C56, 0xFF963C),
    /** The hive's warrior: a man-sized mantis that leaps in with both scythes. */
    HIVE_REAVER("hive_reaver", Style.LUNGER, 0.9f, 1.8f, 34, 9, 0.3, 4, "slash", "lunge", 28, BROODLING, 0x22381E, 0xFF3C6E),
    /** A colossus of rune-cut stone: slow, enormous, and its slam knocks everything flat. */
    RUNE_COLOSSUS("rune_colossus", Style.BRUTE, 1.9f, 2.8f, 110, 14, 0.2, 12, "smash", "slam", 44, ASHEN_SENTINEL, 0x403C3A, 0xFFC450),
    /** A rooted plant with a mouth of thorns that spits poisoned barbs and never moves. */
    THORN_SPITTER("thorn_spitter", Style.TURRET, 0.8f, 1.3f, 20, 5, 0.0, 2, "spit", null, 32, BROODLING, 0x425C2E, 0xC8FF46);

    /**
     * How a kind fights. HOUND and SWARM bite and leap, CHARGER rushes, CASTER keeps away and throws bolts, GUARD
     * shields its front, BOMBER flies in and bursts; BRUTE hits hard and slams the ground around it, ARCHER shoots
     * from range, SKIRMISHER dashes in and out, SWOOPER circles overhead and dives, LUNGER strikes from a few blocks
     * off, HAUNT drifts in the air and sends out pulses, SHAMBLER plods and rises again, TURRET is rooted and spits.
     */
    public enum Style { HOUND, SWARM, CHARGER, CASTER, GUARD, BOMBER, BRUTE, ARCHER, SKIRMISHER, SWOOPER, LUNGER, HAUNT, SHAMBLER, TURRET }

    /** Extra habits on top of a style. */
    public enum Quirk {
        /** Heals the creatures around it now and then. */
        MENDS,
        /** Blinks away when something gets close. */
        BLINKS
    }

    public final String id;
    public final Style style;
    public final float width;
    public final float height;
    public final float health;
    public final float damage;
    public final double speed;
    public final double armor;
    public final String attackAnim;
    /** Its second move's animation (a slam, a dash, a block, a wail ...), or null. */
    @Nullable public final String specialAnim;
    /** How long its death animation lasts before it is gone. */
    public final int deathTicks;
    @Nullable private final MonsterKind kin;
    public final int eggBase;
    public final int eggSpots;
    private final Set<Quirk> quirks;

    MonsterKind(String id, Style style, float width, float height, float health, float damage, double speed, double armor, String attackAnim,
                @Nullable String specialAnim, int deathTicks, @Nullable MonsterKind kin, int eggBase, int eggSpots, Quirk... quirks) {
        this.id = id;
        this.style = style;
        this.width = width;
        this.height = height;
        this.health = health;
        this.damage = damage;
        this.speed = speed;
        this.armor = armor;
        this.attackAnim = attackAnim;
        this.specialAnim = specialAnim;
        this.deathTicks = deathTicks;
        this.kin = kin;
        this.eggBase = eggBase;
        this.eggSpots = eggSpots;
        this.quirks = quirks.length == 0 ? EnumSet.noneOf(Quirk.class) : EnumSet.of(quirks[0], quirks);
    }

    public ResourceLocation location() {
        return BeyondSolo.id(id);
    }

    /** One of the six originals whose drops (material, rune art, armour sets) this kind shares: itself for those. */
    public MonsterKind kin() {
        return kin == null ? this : kin;
    }

    public boolean has(Quirk quirk) {
        return quirks.contains(quirk);
    }

    public boolean flies() {
        return style == Style.BOMBER || style == Style.SWOOPER || style == Style.HAUNT;
    }

    /** Never moves from where it took root. */
    public boolean rooted() {
        return style == Style.TURRET;
    }
}
