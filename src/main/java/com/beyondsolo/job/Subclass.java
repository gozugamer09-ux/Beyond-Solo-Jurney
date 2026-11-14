package com.beyondsolo.job;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.Stat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The 30 subclasses a job change can give: five per base class. In each class one of them is {@link #rare()}:
 * extremely rare to roll and far stronger than the other four.
 *
 * <p>Stat weights decide where the job change's bonus points go (order STR, AGI, VIT, INT, SENSE).
 * Skills of a subclass are in {@code com.beyondsolo.skill.SubclassSkills}.</p>
 */
public enum Subclass {
    // ---- warrior
    BLOODRAGER(PlayerClass.WARRIOR, false, 0xE0393E, SkillElement.FIRE, "drop", 5, 2, 3, 0, 0),
    WARLORD(PlayerClass.WARRIOR, false, 0xF0A040, SkillElement.FIRE, "flag-banner", 3, 1, 4, 0, 2),
    BLADEMASTER(PlayerClass.WARRIOR, false, 0xFF8A70, SkillElement.AIR, "sword", 4, 4, 1, 0, 1),
    JUGGERNAUT(PlayerClass.WARRIOR, false, 0xB07850, SkillElement.EARTH, "shield-chevron", 4, 0, 6, 0, 0),
    WORLDBREAKER(PlayerClass.WARRIOR, true, 0xFF5A1E, SkillElement.EARTH, "planet", 6, 2, 4, 0, 2),
    // ---- assassin
    NIGHTBLADE(PlayerClass.ASSASSIN, false, 0x8060E0, SkillElement.SHADOW, "knife", 3, 5, 0, 0, 2),
    TOXIN_WEAVER(PlayerClass.ASSASSIN, false, 0x8FE04A, SkillElement.NATURE, "flask", 1, 4, 1, 2, 2),
    MIRAGE_DANCER(PlayerClass.ASSASSIN, false, 0xB8A0FF, SkillElement.AIR, "wind", 1, 6, 1, 0, 2),
    BOUNTY_REAPER(PlayerClass.ASSASSIN, false, 0xD04060, SkillElement.PHYSICAL, "crosshair", 3, 3, 1, 0, 3),
    ECLIPSE_SOVEREIGN(PlayerClass.ASSASSIN, true, 0x5A2FD0, SkillElement.SHADOW, "moon-stars", 4, 7, 1, 1, 3),
    // ---- mage
    PYROMANCER(PlayerClass.MAGE, false, 0xFF7A2E, SkillElement.FIRE, "fire", 0, 1, 1, 6, 2),
    STORMCALLER(PlayerClass.MAGE, false, 0x9CD8FF, SkillElement.LIGHTNING, "lightning", 0, 2, 1, 5, 2),
    TIDECALLER(PlayerClass.MAGE, false, 0x4FA8FF, SkillElement.WATER, "waves", 0, 1, 2, 5, 2),
    /** The only subclass that will be able to raise a shadow army. */
    NECROMANCER(PlayerClass.MAGE, false, 0x7B5CC8, SkillElement.SHADOW, "skull", 0, 1, 2, 5, 2),
    STARBORN_ARCHON(PlayerClass.MAGE, true, 0x7F9CFF, SkillElement.ARCANE, "shooting-star", 0, 2, 2, 8, 2),
    // ---- guardian
    PALADIN(PlayerClass.GUARDIAN, false, 0xFFE38A, SkillElement.LIGHT, "sun", 2, 0, 4, 2, 2),
    IRONWALL_SENTINEL(PlayerClass.GUARDIAN, false, 0xA8A8B8, SkillElement.EARTH, "castle-turret", 2, 0, 7, 0, 1),
    SPELLWARD_WARDEN(PlayerClass.GUARDIAN, false, 0x6FA8FF, SkillElement.ARCANE, "shield-star", 1, 0, 4, 3, 2),
    VENGEANCE_KNIGHT(PlayerClass.GUARDIAN, false, 0xFF6A3A, SkillElement.FIRE, "shield-warning", 4, 0, 4, 0, 2),
    EVERLASTING_COLOSSUS(PlayerClass.GUARDIAN, true, 0xE0B050, SkillElement.EARTH, "mountains", 4, 0, 10, 0, 2),
    // ---- healer
    LIGHTBINDER(PlayerClass.HEALER, false, 0xFFF0A8, SkillElement.LIGHT, "sun-horizon", 0, 1, 3, 4, 2),
    GROVEKEEPER(PlayerClass.HEALER, false, 0x7CE05A, SkillElement.NATURE, "tree", 0, 1, 4, 3, 2),
    SPRING_ORACLE(PlayerClass.HEALER, false, 0x7FE0E0, SkillElement.WATER, "drop-half", 0, 1, 2, 4, 3),
    HEMOMANCER(PlayerClass.HEALER, false, 0xC0304A, SkillElement.SHADOW, "heartbeat", 1, 1, 3, 4, 1),
    DAWN_SAINT(PlayerClass.HEALER, true, 0xFFD27A, SkillElement.LIGHT, "sun-dim", 0, 2, 4, 6, 3),
    // ---- ranger
    SHARPSHOOTER(PlayerClass.RANGER, false, 0xC6F06A, SkillElement.AIR, "target", 1, 3, 0, 0, 6),
    WILDCALLER(PlayerClass.RANGER, false, 0x9BD06A, SkillElement.NATURE, "paw-print", 2, 3, 2, 0, 3),
    SNARE_ARTISAN(PlayerClass.RANGER, false, 0xC9A35A, SkillElement.EARTH, "graph", 1, 4, 1, 0, 4),
    STORM_ARCHER(PlayerClass.RANGER, false, 0xB8E2FF, SkillElement.LIGHTNING, "cloud-lightning", 1, 3, 0, 2, 4),
    HEAVENPIERCER(PlayerClass.RANGER, true, 0xE8FFF0, SkillElement.AIR, "arrow-up", 2, 5, 1, 1, 7);

    /** Bonus stat points the job change gives (rare subclasses get far more). */
    public static final int BONUS_POINTS = 10;
    public static final int RARE_BONUS_POINTS = 30;

    private final PlayerClass base;
    private final boolean rare;
    private final int color;
    private final SkillElement element;
    private final String iconSource;
    private final int[] statWeights;

    Subclass(PlayerClass base, boolean rare, int color, SkillElement element, String iconSource, int... statWeights) {
        this.base = base;
        this.rare = rare;
        this.color = color;
        this.element = element;
        this.iconSource = iconSource;
        this.statWeights = statWeights;
    }

    public PlayerClass base() { return base; }
    /** Extremely rare and extremely strong: one per base class. */
    public boolean rare() { return rare; }
    public int color() { return color; }
    public SkillElement element() { return element; }
    /** The Phosphor icon its emblem is made from (see tools/gen_job_assets.py). */
    public String iconSource() { return iconSource; }
    public String key() { return name().toLowerCase(); }
    public ResourceLocation icon() { return BeyondSolo.id("textures/gui/icon/subclass_" + key() + ".png"); }

    public int statWeight(Stat stat) {
        return statWeights[stat.ordinal()];
    }

    public int bonusPoints() {
        return rare ? RARE_BONUS_POINTS : BONUS_POINTS;
    }

    /** Only necromancers will be able to raise a shadow army (the shadow army thread checks this). */
    public boolean canRaiseShadows() {
        return this == NECROMANCER;
    }

    public Component displayName() {
        return Component.translatable("subclass.beyondsolo." + key());
    }

    /** One line on how it plays. */
    public Component description() {
        return Component.translatable("subclass.beyondsolo." + key() + ".desc");
    }

    // ---------------------------------------------------------------- lookups

    /** The five subclasses of a base class, the rare one last. */
    public static List<Subclass> of(PlayerClass base) {
        List<Subclass> out = new ArrayList<>(5);
        for (Subclass s : values()) if (s.base == base && !s.rare) out.add(s);
        for (Subclass s : values()) if (s.base == base && s.rare) out.add(s);
        return out;
    }

    public static Subclass rareOf(PlayerClass base) {
        for (Subclass s : values()) if (s.base == base && s.rare) return s;
        throw new IllegalStateException("no rare subclass for " + base);
    }

    /**
     * Rolls a subclass for a base class: the rare one with {@code rareChance} (0..1), otherwise one of the four
     * others, all equally likely.
     */
    public static Subclass roll(PlayerClass base, RandomSource random, double rareChance) {
        if (random.nextDouble() < rareChance) return rareOf(base);
        List<Subclass> common = of(base).subList(0, 4);
        return common.get(random.nextInt(common.size()));
    }

    @Nullable
    public static Subclass byName(String name) {
        if (name == null) return null;
        for (Subclass s : values()) if (s.name().equalsIgnoreCase(name)) return s;
        return null;
    }
}
