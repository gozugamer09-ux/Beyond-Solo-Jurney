package com.beyondsolo.armory;

import com.beyondsolo.combat.ModAttributes;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gear.BossSets;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.system.PlayerClass;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.beyondsolo.system.PlayerClass.ASSASSIN;
import static com.beyondsolo.system.PlayerClass.GUARDIAN;
import static com.beyondsolo.system.PlayerClass.HEALER;
import static com.beyondsolo.system.PlayerClass.MAGE;
import static com.beyondsolo.system.PlayerClass.RANGER;
import static com.beyondsolo.system.PlayerClass.WARRIOR;

/**
 * The armory: 39 armor sets on top of the brute's six ({@link BossSets}), every one made for a single class. Every
 * gate creature and every boss has sets of its own (one to three, across the ranks it lives in), the villages'
 * craftsmen hand out a plain starter set per class for their requests, and each class has one divine set.
 *
 * <p>Buffs are small on purpose: two pieces give a stat or two, four pieces a little more and the set's perk
 * ({@link SetPerks}); only the class the set is made for draws them out. The gate rank a set belongs to decides the
 * level it needs (E 1, D 10, C 20, B 30, A 40, S 50, divine 60) and how rarely it drops ({@link ArmorDrops}).
 *
 * <pre>
 * rank  Warrior          Assassin        Mage             Guardian        Healer          Ranger
 * E     Forgehand (v)    Nightstitch (v) Inkweave (v)     Hearthguard (v) Chapelward (v)  Fletchwood (v)
 *                        Broodsilk (c)                    Stonehide (b)
 * D     Fangcrest (b)    Mirefang (b)    Cinderwisp (c)   Shardshell (c)  Silken Cradle (b) Gloomstalker (c)
 * C     Hollow Vanguard (b) Duskfang (c) Sigilweaver (b)  Rimeguard (b)   Bonechant (c)   Silkstrider (c)
 * B     Prismhorn (c)    Gloomreaver (b) Pyre Wraith (c)  Ashen Watch (c) Rimebloom (b)   Frostmane (b)
 * A     Emberwrought (b) Brood Empress (b) Hollow Hierarch (c) Oathbound (b) Starbinder (b) Ashwing (b)
 * S     Sovereign (b)    Hollowblade (b) Archmagus (b)    Ashen Bastion (c) Hollow Saint (c) Moonmaw (c)
 *       Wyrmlord (b)
 * div   Worldbreaker     Veilwalker      Starweave        Eternal Bastion Lifebinder      Skywarden
 * </pre>
 * (v) village requests, (c) a gate creature, (b) a boss.
 */
public final class ArmorCatalog {
    /** How heavy a set is: its armor values and how much more tiring sprinting gets. */
    public enum Build {
        HEAVY(0.12f), MEDIUM(0.05f), LIGHT(0.015f);

        public final float weight;

        Build(float weight) {
            this.weight = weight;
        }
    }

    /** A set of the armory and how it is built. */
    public record Def(ArmorSets.Set set, Build build) {}

    private static final List<Def> DEFS = new ArrayList<>();
    /** Every armory set, in catalogue order. */
    public static final List<Def> ALL = Collections.unmodifiableList(DEFS);

    // ------------------------------------------------------------------ village requests (E, common, stats only)

    public static final ArmorSets.Set FORGEHAND = set("forgehand", WARRIOR, 0, Build.MEDIUM, "villager:forgehand", null,
            () -> Map.of(Attributes.ATTACK_DAMAGE, 0.5),
            () -> Map.of(Attributes.ATTACK_DAMAGE, 0.5, Attributes.KNOCKBACK_RESISTANCE, 0.05));
    public static final ArmorSets.Set HEARTHGUARD = set("hearthguard", GUARDIAN, 0, Build.HEAVY, "villager:hearthguard", null,
            () -> Map.of(Attributes.ARMOR, 1.0),
            () -> Map.of(Attributes.MAX_HEALTH, 0.05, Attributes.KNOCKBACK_RESISTANCE, 0.1));
    public static final ArmorSets.Set NIGHTSTITCH = set("nightstitch", ASSASSIN, 0, Build.LIGHT, "villager:nightstitch", null,
            () -> Map.of(Attributes.MOVEMENT_SPEED, 0.02),
            () -> Map.of(ALObjects.Attributes.CRIT_CHANCE, 0.03, ALObjects.Attributes.DODGE_CHANCE, 0.02));
    public static final ArmorSets.Set INKWEAVE = set("inkweave", MAGE, 0, Build.LIGHT, "villager:inkweave", null,
            () -> Map.of(ModAttributes.SPELL_POWER, 0.04),
            () -> Map.of(ModAttributes.MANA_EFFICIENCY, 0.05, ModAttributes.SPELL_POWER, 0.03));
    public static final ArmorSets.Set CHAPELWARD = set("chapelward", HEALER, 0, Build.LIGHT, "villager:chapelward", null,
            () -> Map.of(ALObjects.Attributes.HEALING_RECEIVED, 0.05),
            () -> Map.of(Attributes.MAX_HEALTH, 0.04, ModAttributes.SKILL_HASTE, 0.03));
    public static final ArmorSets.Set FLETCHWOOD = set("fletchwood", RANGER, 0, Build.LIGHT, "villager:fletchwood", null,
            () -> Map.of(ALObjects.Attributes.ARROW_DAMAGE, 0.05),
            () -> Map.of(ALObjects.Attributes.DRAW_SPEED, 0.05, Attributes.MOVEMENT_SPEED, 0.02));

    // ------------------------------------------------------------------ gate creatures

    public static final ArmorSets.Set BROODSILK = set("broodsilk", ASSASSIN, 0, Build.LIGHT, "creature:broodling", "venom_fangs",
            () -> Map.of(ModAttributes.POISON_RESISTANCE, 0.15),
            () -> Map.of(ALObjects.Attributes.DODGE_CHANCE, 0.02, Attributes.MOVEMENT_SPEED, 0.02));
    public static final ArmorSets.Set GLOOMSTALKER = set("gloomstalker", RANGER, 1, Build.MEDIUM, "creature:gloomfang", "hunters_mark",
            () -> Map.of(Attributes.MOVEMENT_SPEED, 0.03),
            () -> Map.of(ALObjects.Attributes.ARROW_DAMAGE, 0.08));
    public static final ArmorSets.Set SHARDSHELL = set("shardshell", GUARDIAN, 1, Build.HEAVY, "creature:shardback", "crystal_spines",
            () -> Map.of(Attributes.ARMOR, 1.0, Attributes.KNOCKBACK_RESISTANCE, 0.1),
            () -> Map.of(Attributes.ARMOR_TOUGHNESS, 1.0));
    public static final ArmorSets.Set CINDERWISP = set("cinderwisp", MAGE, 1, Build.LIGHT, "creature:emberwisp", "cinder_burst",
            () -> Map.of(ModAttributes.FIRE_RESISTANCE, 0.15),
            () -> Map.of(ModAttributes.SPELL_POWER, 0.05, ModAttributes.ELEMENTAL_POWER, 0.05));
    public static final ArmorSets.Set DUSKFANG = set("duskfang", ASSASSIN, 2, Build.LIGHT, "creature:gloomfang", "pounce",
            () -> Map.of(ALObjects.Attributes.CRIT_CHANCE, 0.04),
            () -> Map.of(Attributes.MOVEMENT_SPEED, 0.03, ALObjects.Attributes.CRIT_DAMAGE, 0.1));
    public static final ArmorSets.Set SILKSTRIDER = set("silkstrider", RANGER, 2, Build.LIGHT, "creature:broodling", "webshot",
            () -> Map.of(ModAttributes.POISON_RESISTANCE, 0.15, Attributes.MOVEMENT_SPEED, 0.02),
            () -> Map.of(ALObjects.Attributes.ARROW_DAMAGE, 0.08));
    public static final ArmorSets.Set BONECHANT = set("bonechant", HEALER, 2, Build.LIGHT, "creature:hollow_acolyte", "bone_ward",
            () -> Map.of(ALObjects.Attributes.HEALING_RECEIVED, 0.08),
            () -> Map.of(ModAttributes.SKILL_HASTE, 0.04, Attributes.MAX_HEALTH, 0.05));
    public static final ArmorSets.Set PRISMHORN = set("prismhorn", WARRIOR, 3, Build.HEAVY, "creature:shardback", "prism_charge",
            () -> Map.of(Attributes.ATTACK_DAMAGE, 1.0, Attributes.KNOCKBACK_RESISTANCE, 0.1),
            () -> Map.of(Attributes.ARMOR_TOUGHNESS, 1.0, ALObjects.Attributes.CRIT_DAMAGE, 0.1));
    public static final ArmorSets.Set ASHEN_WATCH = set("ashen_watch", GUARDIAN, 3, Build.HEAVY, "creature:ashen_sentinel", "tower_guard",
            () -> Map.of(Attributes.ARMOR, 2.0),
            () -> Map.of(Attributes.KNOCKBACK_RESISTANCE, 0.15, Attributes.MAX_HEALTH, 0.06));
    public static final ArmorSets.Set PYRE_WRAITH = set("pyre_wraith", MAGE, 3, Build.LIGHT, "creature:emberwisp", "pyre_burst",
            () -> Map.of(ModAttributes.FIRE_RESISTANCE, 0.2, ModAttributes.SPELL_POWER, 0.05),
            () -> Map.of(ModAttributes.ELEMENTAL_POWER, 0.1, ModAttributes.MANA_EFFICIENCY, 0.05));
    public static final ArmorSets.Set HOLLOW_HIERARCH = set("hollow_hierarch", MAGE, 4, Build.LIGHT, "creature:hollow_acolyte", "hollow_echo",
            () -> Map.of(ModAttributes.SPELL_POWER, 0.08),
            () -> Map.of(ModAttributes.MANA_EFFICIENCY, 0.08, ModAttributes.ELEMENTAL_POWER, 0.1));
    public static final ArmorSets.Set MOONMAW = set("moonmaw", RANGER, 5, Build.MEDIUM, "creature:gloomfang", "moonlit_hunt",
            () -> Map.of(ALObjects.Attributes.ARROW_DAMAGE, 0.1, ALObjects.Attributes.DRAW_SPEED, 0.05),
            () -> Map.of(ALObjects.Attributes.CRIT_CHANCE, 0.05, Attributes.MOVEMENT_SPEED, 0.04));
    public static final ArmorSets.Set HOLLOW_SAINT = set("hollow_saint", HEALER, 5, Build.MEDIUM, "creature:hollow_acolyte", "saints_grace",
            () -> Map.of(ALObjects.Attributes.HEALING_RECEIVED, 0.1, Attributes.MAX_HEALTH, 0.05),
            () -> Map.of(ModAttributes.SKILL_HASTE, 0.06, ModAttributes.MANA_EFFICIENCY, 0.06));
    public static final ArmorSets.Set ASHEN_BASTION = set("ashen_bastion", GUARDIAN, 5, Build.HEAVY, "creature:ashen_sentinel", "ashen_bulwark",
            () -> Map.of(Attributes.ARMOR, 2.0, Attributes.ARMOR_TOUGHNESS, 1.0),
            () -> Map.of(Attributes.MAX_HEALTH, 0.1, Attributes.KNOCKBACK_RESISTANCE, 0.2));

    // ------------------------------------------------------------------ bosses (besides the brute's six)

    public static final ArmorSets.Set SILKEN_CRADLE = set("silken_cradle", HEALER, 1, Build.LIGHT, "boss:broodmother", "cradle_silk",
            () -> Map.of(ModAttributes.POISON_RESISTANCE, 0.2),
            () -> Map.of(ALObjects.Attributes.HEALING_RECEIVED, 0.06, Attributes.MAX_HEALTH, 0.04));
    public static final ArmorSets.Set FANGCREST = set("fangcrest", WARRIOR, 1, Build.MEDIUM, "boss:alpha", "alphas_howl",
            () -> Map.of(Attributes.ATTACK_DAMAGE, 0.5, Attributes.MOVEMENT_SPEED, 0.02),
            () -> Map.of(ALObjects.Attributes.CRIT_CHANCE, 0.03, Attributes.ATTACK_DAMAGE, 0.5));
    public static final ArmorSets.Set HOLLOW_VANGUARD = set("hollow_vanguard", WARRIOR, 2, Build.HEAVY, "boss:knight", "vanguard_cleave",
            () -> Map.of(Attributes.ATTACK_DAMAGE, 0.5, Attributes.ARMOR, 1.0),
            () -> Map.of(Attributes.KNOCKBACK_RESISTANCE, 0.1, ALObjects.Attributes.CRIT_DAMAGE, 0.1));
    public static final ArmorSets.Set SIGILWEAVER = set("sigilweaver", MAGE, 2, Build.LIGHT, "boss:magus", "storm_sigil",
            () -> Map.of(ModAttributes.SPELL_POWER, 0.05),
            () -> Map.of(ModAttributes.SKILL_HASTE, 0.05, ModAttributes.MANA_EFFICIENCY, 0.05));
    public static final ArmorSets.Set FROSTMANE = set("frostmane", RANGER, 3, Build.MEDIUM, "boss:alpha", "frostbite_arrows",
            () -> Map.of(ALObjects.Attributes.ARROW_DAMAGE, 0.06, Attributes.MOVEMENT_SPEED, 0.02),
            () -> Map.of(ALObjects.Attributes.DRAW_SPEED, 0.08, ALObjects.Attributes.CRIT_CHANCE, 0.04));
    public static final ArmorSets.Set RIMEBLOOM = set("rimebloom", HEALER, 3, Build.LIGHT, "boss:alpha", "rime_bloom",
            () -> Map.of(ALObjects.Attributes.HEALING_RECEIVED, 0.08, ModAttributes.FIRE_RESISTANCE, 0.1),
            () -> Map.of(ModAttributes.SKILL_HASTE, 0.05, Attributes.MAX_HEALTH, 0.05));
    public static final ArmorSets.Set BROOD_EMPRESS = set("brood_empress", ASSASSIN, 4, Build.LIGHT, "boss:broodmother", "empress_venom",
            () -> Map.of(ModAttributes.POISON_RESISTANCE, 0.25, ALObjects.Attributes.CRIT_CHANCE, 0.04),
            () -> Map.of(ALObjects.Attributes.CRIT_DAMAGE, 0.15, Attributes.MOVEMENT_SPEED, 0.03));
    public static final ArmorSets.Set OATHBOUND = set("oathbound", GUARDIAN, 4, Build.HEAVY, "boss:knight", "oath_of_iron",
            () -> Map.of(Attributes.ARMOR, 2.0, Attributes.MAX_HEALTH, 0.05),
            () -> Map.of(Attributes.ARMOR_TOUGHNESS, 2.0, Attributes.KNOCKBACK_RESISTANCE, 0.15));
    public static final ArmorSets.Set STARBINDER = set("starbinder", HEALER, 4, Build.MEDIUM, "boss:magus", "starbinding",
            () -> Map.of(ALObjects.Attributes.HEALING_RECEIVED, 0.1, Attributes.MAX_HEALTH, 0.05),
            () -> Map.of(ModAttributes.SKILL_HASTE, 0.06, ModAttributes.SPELL_POWER, 0.06));
    public static final ArmorSets.Set ASHWING = set("ashwing", RANGER, 4, Build.MEDIUM, "boss:wyrm", "ashfall",
            () -> Map.of(ALObjects.Attributes.ARROW_DAMAGE, 0.08, ModAttributes.FIRE_RESISTANCE, 0.15),
            () -> Map.of(Attributes.MOVEMENT_SPEED, 0.04, ALObjects.Attributes.DRAW_SPEED, 0.08));
    public static final ArmorSets.Set HOLLOWBLADE = set("hollowblade", ASSASSIN, 5, Build.LIGHT, "boss:knight", "phantom_blade",
            () -> Map.of(ALObjects.Attributes.CRIT_CHANCE, 0.05, Attributes.MOVEMENT_SPEED, 0.03),
            () -> Map.of(ALObjects.Attributes.CRIT_DAMAGE, 0.2, ALObjects.Attributes.DODGE_CHANCE, 0.04));
    public static final ArmorSets.Set ARCHMAGUS = set("archmagus", MAGE, 5, Build.LIGHT, "boss:magus", "arcane_cascade",
            () -> Map.of(ModAttributes.SPELL_POWER, 0.08, ModAttributes.MANA_EFFICIENCY, 0.05),
            () -> Map.of(ModAttributes.SKILL_HASTE, 0.08, ModAttributes.ELEMENTAL_POWER, 0.15));
    public static final ArmorSets.Set WYRMLORD = set("wyrmlord", WARRIOR, 5, Build.HEAVY, "boss:wyrm", "dragons_blood",
            () -> Map.of(Attributes.ATTACK_DAMAGE, 1.0, ModAttributes.FIRE_RESISTANCE, 0.25),
            () -> Map.of(Attributes.ARMOR_TOUGHNESS, 2.0, Attributes.MAX_HEALTH, 0.08));

    // ------------------------------------------------------------------ divine (one per class, bound to it)

    public static final ArmorSets.Set WORLDBREAKER = set("worldbreaker", WARRIOR, ArmorSets.DIVINE_TIER, Build.HEAVY, "divine", "warlords_fury",
            () -> Map.of(Attributes.ATTACK_DAMAGE, 2.0, Attributes.MAX_HEALTH, 0.08),
            () -> Map.of(ALObjects.Attributes.CRIT_DAMAGE, 0.25, Attributes.ARMOR_TOUGHNESS, 2.0));
    public static final ArmorSets.Set VEILWALKER = set("veilwalker", ASSASSIN, ArmorSets.DIVINE_TIER, Build.LIGHT, "divine", "shadow_veil",
            () -> Map.of(ALObjects.Attributes.CRIT_CHANCE, 0.08, Attributes.MOVEMENT_SPEED, 0.05),
            () -> Map.of(ALObjects.Attributes.CRIT_DAMAGE, 0.3, ALObjects.Attributes.DODGE_CHANCE, 0.05));
    public static final ArmorSets.Set STARWEAVE = set("starweave", MAGE, ArmorSets.DIVINE_TIER, Build.LIGHT, "divine", "arcane_overflow",
            () -> Map.of(ModAttributes.SPELL_POWER, 0.15, ModAttributes.MANA_EFFICIENCY, 0.08),
            () -> Map.of(ModAttributes.SKILL_HASTE, 0.1, ModAttributes.ELEMENTAL_POWER, 0.2));
    public static final ArmorSets.Set ETERNAL_BASTION = set("eternal_bastion", GUARDIAN, ArmorSets.DIVINE_TIER, Build.HEAVY, "divine", "sanctuary",
            () -> Map.of(Attributes.ARMOR, 3.0, Attributes.MAX_HEALTH, 0.1),
            () -> Map.of(Attributes.ARMOR_TOUGHNESS, 3.0, Attributes.KNOCKBACK_RESISTANCE, 0.3));
    public static final ArmorSets.Set LIFEBINDER = set("lifebinder", HEALER, ArmorSets.DIVINE_TIER, Build.MEDIUM, "divine", "overflowing_grace",
            () -> Map.of(ALObjects.Attributes.HEALING_RECEIVED, 0.15, Attributes.MAX_HEALTH, 0.08),
            () -> Map.of(ModAttributes.SKILL_HASTE, 0.1, ModAttributes.MANA_EFFICIENCY, 0.1));
    public static final ArmorSets.Set SKYWARDEN = set("skywarden", RANGER, ArmorSets.DIVINE_TIER, Build.MEDIUM, "divine", "windwalker",
            () -> Map.of(ALObjects.Attributes.ARROW_DAMAGE, 0.15, ALObjects.Attributes.DRAW_SPEED, 0.1),
            () -> Map.of(ALObjects.Attributes.CRIT_CHANCE, 0.08, Attributes.MOVEMENT_SPEED, 0.06));

    /** Village professions and the set their requests can pay out. Anyone else's: one of the six at random. */
    public static final Map<String, ArmorSets.Set> PROFESSIONS = Map.ofEntries(
            Map.entry("weaponsmith", FORGEHAND), Map.entry("toolsmith", FORGEHAND),
            Map.entry("armorer", HEARTHGUARD), Map.entry("mason", HEARTHGUARD),
            Map.entry("leatherworker", NIGHTSTITCH), Map.entry("butcher", NIGHTSTITCH),
            Map.entry("librarian", INKWEAVE), Map.entry("cartographer", INKWEAVE),
            Map.entry("cleric", CHAPELWARD), Map.entry("farmer", CHAPELWARD),
            Map.entry("fletcher", FLETCHWOOD), Map.entry("fisherman", FLETCHWOOD), Map.entry("shepherd", FLETCHWOOD));

    // ------------------------------------------------------------------ lookups

    /** Every class set: the armory's and the brute's, sorted by class, then rank. */
    public static List<ArmorSets.Set> classSets() {
        List<ArmorSets.Set> out = new ArrayList<>();
        for (ArmorSets.Set s : ArmorSets.ALL) if (s.playerClass != null && s.tier >= 0) out.add(s);
        out.sort(Comparator.comparingInt((ArmorSets.Set s) -> s.playerClass.ordinal()).thenComparingInt(s -> s.tier));
        return out;
    }

    /** The sets a gate creature can leave: its own kind's, or those of the original it is kin to. */
    public static List<ArmorSets.Set> forCreature(MonsterKind kind) {
        return from("creature:" + kind.kin().id);
    }

    /** The sets a boss can leave in its cache (the brute: its six, one per rank). */
    public static List<ArmorSets.Set> forBoss(BossKind kind) {
        if (kind == BossKind.BRUTE) return BossSets.ALL;
        return from("boss:" + kind.id);
    }

    public static List<ArmorSets.Set> villagerSets() {
        List<ArmorSets.Set> out = new ArrayList<>();
        for (Def d : DEFS) if (d.set().source != null && d.set().source.startsWith("villager:")) out.add(d.set());
        return out;
    }

    public static List<ArmorSets.Set> divineSets() {
        List<ArmorSets.Set> out = new ArrayList<>();
        for (Def d : DEFS) if (d.set().divine()) out.add(d.set());
        return out;
    }

    public static @Nullable ArmorSets.Set divine(PlayerClass cls) {
        for (Def d : DEFS) if (d.set().divine() && d.set().playerClass == cls) return d.set();
        return null;
    }

    public static @Nullable Def def(ArmorSets.Set set) {
        for (Def d : DEFS) if (d.set() == set) return d;
        return null;
    }

    private static List<ArmorSets.Set> from(String source) {
        List<ArmorSets.Set> out = new ArrayList<>();
        for (Def d : DEFS) if (source.equals(d.set().source)) out.add(d.set());
        return out;
    }

    // ------------------------------------------------------------------ building

    /**
     * A set's grade from where it comes from and its rank: village sets are common; creature sets go common, rare,
     * rare, epic, epic, legendary from E to S; boss sets rare, rare, epic, epic, legendary, mythic.
     */
    static ItemGrade gradeFor(String source, int tier) {
        if (tier >= ArmorSets.DIVINE_TIER) return ItemGrade.DIVINE;
        if (source.startsWith("villager:")) return ItemGrade.COMMON;
        if (source.startsWith("boss:")) {
            return switch (tier) {
                case 0, 1 -> ItemGrade.RARE;
                case 2, 3 -> ItemGrade.EPIC;
                case 4 -> ItemGrade.LEGENDARY;
                default -> ItemGrade.MYTHIC;
            };
        }
        return switch (tier) {
            case 0 -> ItemGrade.COMMON;
            case 1, 2 -> ItemGrade.RARE;
            case 3, 4 -> ItemGrade.EPIC;
            default -> ItemGrade.LEGENDARY;
        };
    }

    private static ArmorSets.Set set(String name, PlayerClass cls, int tier, Build build, String source, @Nullable String perk,
                                     Supplier<Map<Holder<Attribute>, Double>> two, Supplier<Map<Holder<Attribute>, Double>> four) {
        ArmorSets.Set set = ArmorSets.register(new ArmorSets.Set(name, build.weight, gradeFor(source, tier), two, four, perk, null))
                .forClass(cls).tier(tier).from(source);
        DEFS.add(new Def(set, build));
        return set;
    }

    static void init() {}

    private ArmorCatalog() {}
}
