package com.beyondsolo.enemy;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Where a creature belongs: the tags a gate's realm ({@code gate.GateRealm}) asks for when it fills its caves. An
 * insect hive asks for {@link #INSECT}, a crypt for {@link #UNDEAD} and {@link #CULT}, a frost cavern for
 * {@link #FROST} and {@link #BEAST}; {@link EnemyRoster#random(com.beyondsolo.system.PlayerRank,
 * net.minecraft.util.RandomSource, Collection, Affinity)} then weights the creatures that carry one of those tags four
 * times higher and makes at least two of a gate's kinds fit.
 *
 * <p><b>Adding a creature:</b> give it a line in the table below (its entity id and its tags). A creature without
 * a line fits nowhere in particular: it can still turn up anywhere its rank's pool allows, just without the boost.</p>
 */
public enum Habitat {
    /** Hounds, bats, bears, raiders: things with fur, fangs and claws. */
    BEAST,
    /** Spiders, beetles, ants, centipedes. */
    INSECT,
    /** The dead that walk: thralls, skeletons, wraiths. */
    UNDEAD,
    /** Robed casters, shamans, zealots. */
    CULT,
    /** Armoured soldiers, guards, golems: things that hold a line. */
    KNIGHT,
    /** Things that burn. */
    FLAME,
    /** Things of ice and snow. */
    FROST,
    /** Things of deep or flooded water. */
    AQUATIC,
    /** Roots, spores, bog plants, the things that live among them. */
    PLANT,
    /** Fiends, imps, the horned and winged. */
    DEMON,
    /** Things of crystal and stone that live in the seams. */
    CRYSTAL,
    /** Things that crackle with lightning. */
    STORM;

    private static final Map<ResourceLocation, EnumSet<Habitat>> TABLE = new HashMap<>();

    static {
        // the roster's own creatures
        own("gloomfang", BEAST, FROST);
        own("broodling", INSECT);
        own("shardback", INSECT, CRYSTAL);
        own("hollow_acolyte", CULT, UNDEAD);
        own("ashen_sentinel", KNIGHT, UNDEAD);
        own("emberwisp", FLAME, DEMON);
        // the creatures the "gate creatures" work adds (inert until they exist)
        own("grave_thrall", UNDEAD);
        own("bone_archer", UNDEAD, KNIGHT);
        own("gutterkin", BEAST, PLANT);
        own("duskwing", BEAST, STORM);
        own("rimeclaw", FROST, BEAST);
        own("cragcrawler", INSECT, CRYSTAL);
        own("coilfang", AQUATIC, PLANT, BEAST);
        own("rime_stalker", FROST);
        own("tuskbrute", KNIGHT, FLAME);
        own("tusk_shaman", CULT, FLAME);
        own("hive_drone", INSECT);
        own("hive_reaver", INSECT);
        own("rune_colossus", CRYSTAL, KNIGHT);
        own("brimling", DEMON, FLAME);
        own("pit_ravager", DEMON, FLAME);
        own("wailing_wraith", UNDEAD, CULT);
        own("deepkin", AQUATIC);
        own("static_drifter", STORM, AQUATIC);
        own("thorn_spitter", PLANT);
        // vanilla monsters still in some pools
        vanilla("zombie", UNDEAD);
        vanilla("husk", UNDEAD);
        vanilla("drowned", UNDEAD, AQUATIC);
        vanilla("skeleton", UNDEAD);
        vanilla("stray", UNDEAD, FROST);
        vanilla("wither_skeleton", UNDEAD, FLAME, DEMON);
        vanilla("spider", INSECT, BEAST);
        vanilla("cave_spider", INSECT);
        vanilla("vindicator", CULT, KNIGHT);
        vanilla("evoker", CULT);
        vanilla("witch", CULT, PLANT);
        vanilla("blaze", FLAME, DEMON);
    }

    private static void own(String id, Habitat... tags) {
        TABLE.put(BeyondSolo.id(id), EnumSet.copyOf(java.util.List.of(tags)));
    }

    private static void vanilla(String id, Habitat... tags) {
        TABLE.put(ResourceLocation.withDefaultNamespace(id), EnumSet.copyOf(java.util.List.of(tags)));
    }

    /** A creature's tags (empty when it has none). */
    public static Set<Habitat> of(EntityType<?> type) {
        EnumSet<Habitat> tags = TABLE.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
        return tags == null ? EnumSet.noneOf(Habitat.class) : tags;
    }

    /** True if the creature carries any of {@code wanted}. */
    public static boolean fits(EntityType<?> type, Collection<Habitat> wanted) {
        if (wanted.isEmpty()) return false;
        for (Habitat h : of(type)) if (wanted.contains(h)) return true;
        return false;
    }
}
