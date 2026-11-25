package com.beyondsolo.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a caster's body does for a skill, on both sides: which gesture it acts out, the tick of that gesture at which
 * its blow lands, and how long the body winds up before the skill goes off ({@link SkillWindup}).
 *
 * <p>A gesture is picked from the first effect the skill shows (its "look") and the skill's class. The server learns
 * each skill's look the first time it is cast; before that it reads it from {@code beyondsolo/skill_looks.txt}, which
 * {@code SkillWindupGameTests} keeps true to the skills (it prints the whole table when one has changed).</p>
 */
public final class Gestures {
    /** The look each skill showed when it was last cast, or the table's: path -> {look, variant, delay}. */
    private static final Map<String, Object[]> LOOKS = new ConcurrentHashMap<>();
    private static volatile boolean seeded;

    // ---- wind-up lengths, in ticks, by how big the skill is ----
    static final int LIGHT = 4, MEDIUM = 6, HEAVY = 9, ULTIMATE = 16;
    /**
     * The longest a skill may take after its cast before its first blow (a stance, a held breath) and still have the
     * body hold its wind-up until then. Later blows come as a gesture of their own.
     */
    public static final int MAX_DELAY = 30;

    /** The gesture for a skill that showed {@code look} first ({@code variant}: that effect's duration). */
    public static String pick(Skill skill, String look, int variant) {
        PlayerClass owner = skill.owner();
        boolean melee = owner == PlayerClass.WARRIOR || owner == PlayerClass.ASSASSIN || owner == PlayerClass.GUARDIAN;
        boolean heavy = owner == PlayerClass.WARRIOR || owner == PlayerClass.GUARDIAN;
        if (look.startsWith("rune:")) {
            return switch (look.substring(5)) {
                case "BREATH", "DREAD", "HOWL" -> "roar";
                case "FLURRY" -> "slash";
                case "IMPACT" -> "slam";
                case "SIGIL" -> "point";
                case "WEB" -> "place";
                case "WALL" -> "guard";
                case "SWARM" -> "summon";
                case "SHARDS" -> "burst";
                default -> "cast";
            };
        }
        SkillFxPayload.Kind kind = kind(look);
        if (kind == null) return byClass(owner);
        return switch (kind) {
            case SLASH -> variant == 1 ? "slash_back" : "slash";
            case DASH -> "lunge";
            case BLINK, VEIL, EXCHANGE -> "vanish";
            // the hand nobody sees: reached out to take, thrown with the arm, pushed out with both palms
            case GRASP -> "point";
            case HURL -> "throw";
            case FORCE -> "cast_both";
            case SLAM, CRACK -> melee ? "slam" : "ground";
            case QUAKE -> "stomp";
            case ROAR -> "roar";
            case AURA -> melee || owner == PlayerClass.RANGER ? "power" : "channel";
            case ZONE, VORTEX, MIST -> "channel";
            case HEAL, HALO, WINGS, BUBBLE, ORBIT -> "pray";
            case BOLT, ORB, BALL, STREAM -> "cast";
            case SPLASH -> owner == PlayerClass.GUARDIAN ? "lunge" : melee ? "slash" : owner == PlayerClass.RANGER ? "bow" : "cast";
            case BEAM -> "cast_both";
            case SHOT -> owner == PlayerClass.RANGER ? "bow" : melee ? "throw" : "cast";
            case ARROW, GALE, SEEKER -> "bow";
            case SKYSHOT -> "bow_sky";
            case DOME, SHIELD -> "guard";
            case NOVA, PULSE -> heavy ? "stomp" : "burst";
            case BLOSSOM, SPIN -> "spin";
            case MARK, SIGHT, BIND -> "point";
            // fire rolls out from a swept hand, rock spikes are called up from the ground, a charge leaves a wake, wind is cut loose
            case WAVE -> switch (variant) {
                case 1 -> melee ? "slam" : "ground";
                case 2 -> "lunge";
                case 3 -> "slash";
                default -> melee ? "slash" : "sweep";
            };
            case BEAST, SWARM -> "summon";
            case FAN, DISCUS -> "throw_side";
            case PILLAR, METEOR, RAIN, THUNDER, CLOUD -> "raise";
            case CLEAVE -> melee ? "chop" : "raise";
            case HAMMER, CHAIN, NET -> "throw";
            case TRAP, WIRE, ROPE -> "place";
            case UPDRAFT -> "leap";
            case STATIC, QUIVER, BLADES -> "power";
            case WINDWALL -> "sweep";
            case CLAW -> "claw";
            case GEYSER -> owner == PlayerClass.ASSASSIN ? "slash" : melee ? "slam" : "ground";
            case BANNER -> "slam";
            // the casting circle and element marks say nothing about the body; a kind added later falls back the same way
            default -> byClass(owner);
        };
    }

    static String byClass(PlayerClass owner) {
        if (owner == null) return "cast";
        return switch (owner) {
            case WARRIOR, ASSASSIN -> "slash";
            case GUARDIAN -> "guard";
            case HEALER -> "pray";
            case RANGER -> "bow";
            case MAGE -> "cast";
        };
    }

    private static SkillFxPayload.Kind kind(String look) {
        for (SkillFxPayload.Kind k : SkillFxPayload.Kind.values()) if (k.name().equals(look)) return k;
        return null;
    }

    /**
     * The tick of a gesture at which its blow lands: the cut crosses, the fists meet the ground, the arrow leaves the
     * string, the palms open. Its wind-up pose is the one it starts from (tick 0). Read off
     * {@code tools/gen_skill_anims.py}.
     */
    public static int strike(String gesture) {
        return switch (gesture) {
            case "claw", "lunge", "throw_side", "cast", "cast_both", "burst", "guard", "point" -> 2;
            case "slash", "slash_back", "spin", "stomp", "ground", "roar", "power", "leap", "vanish", "place", "throw",
                 "raise" -> 3;
            case "chop", "slam", "sweep" -> 4;
            case "channel", "summon" -> 5;
            case "bow", "bow_sky" -> 6;
            case "pray" -> 8;
            default -> 3;
        };
    }

    /**
     * How many ticks the body winds up before the skill goes off: nothing for what has to be instant (a dash, a
     * vanish, a guard thrown up, a leap, the hand that grabs) or isn't a class's skill, otherwise by how big the skill
     * is, never shorter than the gesture takes to land its blow (a bow is drawn before it is loosed).
     */
    public static int windup(Skill skill, String look, int variant) {
        return windup(skill, look, variant, 0);
    }

    /**
     * {@link #windup(Skill, String, int)} for a skill whose first blow comes {@code delay} ticks after it goes off: it
     * takes that time itself, so the body winds up only for the rest, and holds until the blow.
     */
    public static int windup(Skill skill, String look, int variant, int delay) {
        if (skill.owner() == null || skill.rune() || skill.sovereign() || skill.recast() != null || !skill.isActive()) return 0;
        SkillFxPayload.Kind kind = kind(look);
        if (kind != null) {
            switch (kind) {
                case DASH, BLINK, VEIL, EXCHANGE, UPDRAFT, DOME, SHIELD, GRASP, HURL -> {
                    return 0;
                }
                default -> {}
            }
        }
        int base = switch (heft(skill)) {
            case 3 -> ULTIMATE;
            case 2 -> HEAVY;
            case 1 -> MEDIUM;
            default -> LIGHT;
        };
        int strike = strike(pick(skill, look, variant));
        if (delay > 0 && delay <= MAX_DELAY) return Math.max(strike, base - delay);
        return Math.max(base, strike);
    }

    /**
     * How big a skill feels to cast, for its wind-up and the power it gathers: 0 a quick one, 1 a mid one (A grade
     * or a wait of 20 seconds), 2 a big one (S grade, or a wait of 25 seconds or more), 3 an ultimate. Nothing about
     * what its blows do: that is {@link SkillFeel#stature}.
     */
    public static int heft(Skill skill) {
        if (SkillFeel.ultimate(skill)) return 3;
        if (skill.grade().tier() >= PlayerRank.S.tier() || skill.cooldown(1) >= 500) return 2;
        return SkillFeel.stature(skill) >= SkillFeel.MEDIUM ? 1 : 0;
    }

    // ================================================================ what each skill shows first

    /** The look the skill shows first, as far as the server knows: {look, variant, delay}. */
    public static Object[] look(Skill skill) {
        seed();
        Object[] l = LOOKS.get(skill.path());
        return l != null ? l : new Object[]{"", 0, 0};
    }

    /** How many ticks after its cast the skill shows its first blow, as far as the server knows (0: at once). */
    public static int delay(Skill skill) {
        return (int) look(skill)[2];
    }

    /**
     * A skill showed {@code look} first, {@code delay} ticks after it went off (0: in its cast): casts after this one
     * wind up for it.
     */
    static void remember(Skill skill, String look, int variant, int delay) {
        seed();
        LOOKS.put(skill.path(), new Object[]{look, variant, delay});
    }

    /** Back to the table, forgetting what casts showed (game tests). */
    static void forget() {
        LOOKS.clear();
        seeded = false;
    }

    private static void seed() {
        if (seeded) return;
        synchronized (LOOKS) {
            if (seeded) return;
            readTable().forEach(LOOKS::putIfAbsent);
            seeded = true;
        }
    }

    /**
     * {@code beyondsolo/skill_looks.txt}: one skill a line, its path, the look it shows first (- for none), the variant,
     * and how many ticks after its cast it shows it, when not at once.
     */
    static Map<String, Object[]> readTable() {
        Map<String, Object[]> out = new java.util.LinkedHashMap<>();
        try (InputStream in = Gestures.class.getResourceAsStream("/beyondsolo/skill_looks.txt")) {
            if (in == null) return out;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                String look = parts.length > 1 && !parts[1].equals("-") ? parts[1] : "";
                int variant = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                int delay = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
                out.put(parts[0], new Object[]{look, variant, delay});
            }
        } catch (Exception e) {
            BeyondSolo.LOGGER.warn("Couldn't read the skill looks table", e);
        }
        return out;
    }

    /** Forgets what one skill's casts showed (game tests). */
    static void clear(Skill skill) {
        seed();
        LOOKS.remove(skill.path());
    }

    /** What the server knows the skill shows first, or null (game tests). */
    static Object[] known(Skill skill) {
        return LOOKS.get(skill.path());
    }

    private Gestures() {}
}
