package com.beyondsolo.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * One skill's definition. Skills are defined in code ({@link Skills}) and are the same on both sides, so the
 * client can draw descriptions and numbers without the server sending them. What a player has (level,
 * proficiency, cooldowns, the skill bar) lives in {@link SkillData}.
 *
 * <p>A skill belongs to a class ({@code owner}) or to everyone ({@code owner == null}). Class skills unlock at a
 * player level, earlier the higher the player's rank. {@code advanced} skills never unlock by level or from books:
 * they are for the job change (see {@link SkillAPI#grantAdvanced}).</p>
 */
public final class Skill {
    public enum Kind { ACTIVE, PASSIVE }

    /** Highest skill level. */
    public static final int MAX_LEVEL = 10;

    /** What an active skill does. Return false (no target, nothing to do) to cancel: no mana or cooldown is spent. */
    @FunctionalInterface
    public interface Action {
        boolean cast(ServerPlayer player, Skill skill, int level);
    }

    /** The numbers filled into the skill's description, for a level and the player's rank tier (0 = E .. 5 = S). */
    @FunctionalInterface
    public interface Args {
        Object[] of(int level, int rankTier);
    }

    private final String path;
    private final Kind kind;
    private final @Nullable PlayerClass owner;
    private final PlayerRank grade;
    private final int unlockLevel;
    private final boolean advanced;
    private final int color;
    private final float mana;
    private final int cooldown;
    private final int cooldownAtMax;
    private final @Nullable Action action;
    private final Args args;
    private SkillElement element = SkillElement.PHYSICAL;

    Skill(String path, Kind kind, @Nullable PlayerClass owner, PlayerRank grade, int unlockLevel, boolean advanced, int color,
          float mana, int cooldown, int cooldownAtMax, @Nullable Action action, Args args) {
        this.path = path;
        this.kind = kind;
        this.owner = owner;
        this.grade = grade;
        this.unlockLevel = unlockLevel;
        this.advanced = advanced;
        this.color = color;
        this.mana = mana;
        this.cooldown = cooldown;
        this.cooldownAtMax = cooldownAtMax;
        this.action = action;
        this.args = args;
    }

    public String path() { return path; }
    public ResourceLocation id() { return BeyondSolo.id(path); }
    public Kind kind() { return kind; }
    public boolean isActive() { return kind == Kind.ACTIVE; }
    /** The class that learns it, or null for skills anyone can learn. */
    public @Nullable PlayerClass owner() { return owner; }
    /** How rare and strong the skill is (E..S). Shown as a chip; skill tomes roll better grades in higher gates. */
    public PlayerRank grade() { return grade; }
    /** Only unlocked by the job change. */
    public boolean advanced() { return advanced; }
    public int color() { return color; }
    @Nullable Action action() { return action; }
    public SkillElement element() { return element; }

    Skill element(SkillElement element) {
        this.element = element;
        return this;
    }

    /** True if a player of this class may hold the skill at all. */
    public boolean fits(PlayerClass playerClass) {
        return owner == null || owner == playerClass;
    }

    /** The player level the skill unlocks at for a rank (higher ranks unlock earlier), or -1 if it never unlocks by level. */
    public int unlockLevel(PlayerRank rank) {
        if (unlockLevel < 0 || advanced) return -1;
        // Higher ranks learn faster: 1 level earlier per rank tier above E.
        return Math.max(1, unlockLevel - rank.tier());
    }

    /** The base unlock level (E rank). */
    public int baseUnlockLevel() {
        return unlockLevel;
    }

    public float manaCost(int level) {
        return isActive() ? Math.round(mana * (1f + 0.05f * (level - 1))) : 0;
    }

    /** Cooldown in ticks: shrinks from the level-1 value to the max-level value. */
    public int cooldown(int level) {
        if (!isActive()) return 0;
        float t = (Mth.clamp(level, 1, MAX_LEVEL) - 1) / (float) (MAX_LEVEL - 1);
        return Math.round(Mth.lerp(t, cooldown, cooldownAtMax));
    }

    /** Proficiency needed to go from {@code level} to the next one. Actives gain 1 per cast. */
    public static int proficiencyToNext(int level) {
        return 6 + 4 * level;
    }

    // ---- text ----

    public String translationKey() {
        return "skill.beyondsolo." + path;
    }

    public Component displayName() {
        return Component.translatable(translationKey());
    }

    /** One line of flavour, the same at every level. */
    public Component flavour() {
        return Component.translatable(translationKey() + ".flavour");
    }

    /** What it does at this level, with the numbers filled in. */
    public Component description(int level, int rankTier) {
        return Component.translatable(translationKey() + ".desc", args.of(Mth.clamp(level, 1, MAX_LEVEL), rankTier));
    }
}
