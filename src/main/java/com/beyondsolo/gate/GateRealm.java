package com.beyondsolo.gate;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Habitat;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where a gate leads: the world on the other side, before its shape ({@link DungeonShape}) is laid out. A realm is
 * the look of the place (its rock, floors, trims, lights, liquids, its decorations), what lives there (the creature
 * {@link Habitat}s it asks for, a ruling {@link Affinity}, the bosses it favours), its element, and the shapes it
 * can take. Each rank can lead to several realms and a gate's seed picks one ({@link #forSeed}), so an E-rank gate
 * can open on stone burrows, a beast den, a mossy cave or an overgrown ruin.
 *
 * <p>The first six are the original rank looks ({@link #classic}); caves saved before realms existed keep them.
 * {@link #THRESHOLD} is the awakening trial's and is never rolled.</p>
 */
public enum GateRealm {
    // ------------------------------------------------------------------ the original six, one per rank
    HOLLOWS(spec(PlayerRank.E, PlayerRank.D, 4).cave()
            .walls(Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE).floors(Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.STONE)
            .accents(Blocks.MOSSY_COBBLESTONE, Blocks.TUFF).light(Blocks.OCHRE_FROGLIGHT).scatter(Blocks.GLOW_LICHEN).spike(Blocks.STONE)
            .trimmings(Blocks.COBBLESTONE_STAIRS, Blocks.COBBLESTONE_SLAB, Blocks.COBBLESTONE_WALL, Blocks.COBBLESTONE)
            .shapes(DungeonShape.CAVERNS, 4, DungeonShape.LABYRINTH, 2, DungeonShape.HOLLOW, 1, DungeonShape.HIVE, 1)
            .life(null, DamageElement.BLOOD, Habitat.BEAST, Habitat.INSECT, Habitat.UNDEAD).bosses(BossKind.BRUTE, BossKind.ALPHA, BossKind.BROODMOTHER)
            .decor(Decor.STALAGMITES, Decor.BONES).fog(0x2A2620, 44).ambience(ParticleTypes.WHITE_ASH)),
    MIREWOOD(spec(PlayerRank.E, PlayerRank.C, 4).cave()
            .walls(Blocks.MOSSY_COBBLESTONE, Blocks.STONE, Blocks.MOSS_BLOCK).floors(Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT, Blocks.MUD)
            .accents(Blocks.MOSS_BLOCK, Blocks.MOSSY_STONE_BRICKS).light(Blocks.VERDANT_FROGLIGHT).scatter(Blocks.MOSS_CARPET).spike(Blocks.MOSSY_COBBLESTONE)
            .trimmings(Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.MOSSY_COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE)
            .shapes(DungeonShape.CAVERNS, 3, DungeonShape.HOLLOW, 2, DungeonShape.LABYRINTH, 1)
            .life(Affinity.VENOM, DamageElement.POISON, Habitat.PLANT, Habitat.INSECT, Habitat.BEAST).bosses(BossKind.BROODMOTHER, BossKind.ALPHA)
            .decor(Decor.STALAGMITES, Decor.ROOTS, Decor.AZALEA, Decor.POOLS, Decor.GLOW_BERRIES).fog(0x1C2A18, 40).ambience(ParticleTypes.SPORE_BLOSSOM_AIR)),
    AZURE_GROTTO(spec(PlayerRank.C, PlayerRank.B, 4).cave()
            .walls(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.TUFF).floors(Blocks.SMOOTH_BASALT, Blocks.DEEPSLATE, Blocks.PACKED_ICE)
            .accents(Blocks.BLUE_ICE, Blocks.PRISMARINE).light(Blocks.SEA_LANTERN).scatter(Blocks.GLOW_LICHEN).spike(Blocks.COBBLED_DEEPSLATE)
            .trimmings(Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.COBBLED_DEEPSLATE_WALL, Blocks.COBBLED_DEEPSLATE)
            .shapes(DungeonShape.CAVERNS, 3, DungeonShape.HOLLOW, 2, DungeonShape.CHASM, 2)
            .life(Affinity.FROST, DamageElement.BLOOD, Habitat.AQUATIC, Habitat.FROST, Habitat.BEAST).bosses(BossKind.ALPHA, BossKind.MAGUS, BossKind.KNIGHT)
            .decor(Decor.STALAGMITES, Decor.POOLS, Decor.ICE).fog(0x10263A, 44).ambience(ParticleTypes.FALLING_WATER)),
    GLOOMSHADE(spec(PlayerRank.B, PlayerRank.A, 4).cave()
            .walls(Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.DEEPSLATE).floors(Blocks.SOUL_SOIL, Blocks.BLACKSTONE, Blocks.SOUL_SAND)
            .accents(Blocks.AMETHYST_BLOCK, Blocks.CRYING_OBSIDIAN).light(Blocks.PEARLESCENT_FROGLIGHT).scatter(Blocks.AMETHYST_CLUSTER).spike(Blocks.BLACKSTONE)
            .trimmings(Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.BLACKSTONE_WALL, Blocks.POLISHED_BLACKSTONE)
            .shapes(DungeonShape.CAVERNS, 3, DungeonShape.DESCENT, 2, DungeonShape.HOLLOW, 1, DungeonShape.CHASM, 1)
            .life(Affinity.SHADE, DamageElement.POISON, Habitat.CULT, Habitat.UNDEAD, Habitat.DEMON).bosses(BossKind.KNIGHT, BossKind.MAGUS)
            .decor(Decor.STALAGMITES, Decor.SOUL_FIRE, Decor.SCULK, Decor.CRYSTALS).fog(0x1A1028, 40).ambience(ParticleTypes.SOUL)),
    EMBERFORGE(spec(PlayerRank.A, PlayerRank.S, 4).cave()
            .walls(Blocks.BASALT, Blocks.BLACKSTONE, Blocks.NETHERRACK).floors(Blocks.NETHERRACK, Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK)
            .accents(Blocks.GILDED_BLACKSTONE, Blocks.MAGMA_BLOCK).light(Blocks.SHROOMLIGHT).scatter(Blocks.FIRE).spike(Blocks.BASALT)
            .trimmings(Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.BLACKSTONE_WALL, Blocks.POLISHED_BLACKSTONE).liquid(Blocks.LAVA)
            .shapes(DungeonShape.CAVERNS, 2, DungeonShape.CHASM, 3, DungeonShape.DESCENT, 2)
            .life(Affinity.FLAME, DamageElement.FIRE, Habitat.FLAME, Habitat.KNIGHT, Habitat.DEMON).bosses(BossKind.WYRM, BossKind.BRUTE, BossKind.KNIGHT)
            .decor(Decor.STALAGMITES, Decor.LAVA, Decor.BASALT).fog(0x3A1408, 40).ambience(ParticleTypes.ASH)),
    CRIMSON_ABYSS(spec(PlayerRank.S, PlayerRank.S, 4).cave()
            .walls(Blocks.OBSIDIAN, Blocks.RED_NETHER_BRICKS, Blocks.BLACKSTONE).floors(Blocks.CRIMSON_NYLIUM, Blocks.BLACKSTONE, Blocks.NETHER_WART_BLOCK)
            .accents(Blocks.CRYING_OBSIDIAN, Blocks.NETHER_WART_BLOCK).light(Blocks.SHROOMLIGHT).scatter(Blocks.CRIMSON_ROOTS).spike(Blocks.OBSIDIAN)
            .trimmings(Blocks.RED_NETHER_BRICK_STAIRS, Blocks.RED_NETHER_BRICK_SLAB, Blocks.RED_NETHER_BRICK_WALL, Blocks.RED_NETHER_BRICKS).liquid(Blocks.LAVA)
            .shapes(DungeonShape.CAVERNS, 2, DungeonShape.CHASM, 2, DungeonShape.HOLLOW, 2, DungeonShape.DESCENT, 1)
            .life(Affinity.BLOOD, DamageElement.CRIMSON, Habitat.DEMON, Habitat.UNDEAD, Habitat.BEAST).bosses(BossKind.WYRM, BossKind.KNIGHT, BossKind.MAGUS)
            .decor(Decor.STALAGMITES, Decor.NETHER_PLANTS, Decor.LAVA).fog(0x2E0610, 40).ambience(ParticleTypes.CRIMSON_SPORE)),

    // ------------------------------------------------------------------ new realms
    /** A beast's lair: packed earth, roots through the ceiling, bones of what it caught, glow berries in the dark. */
    ROOTBOUND_DEN(spec(PlayerRank.E, PlayerRank.C, 3).cave()
            .walls(Blocks.ROOTED_DIRT, Blocks.PACKED_MUD, Blocks.DIRT, Blocks.COARSE_DIRT).floors(Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.ROOTED_DIRT)
            .accents(Blocks.BONE_BLOCK, Blocks.MUDDY_MANGROVE_ROOTS).light(Blocks.OCHRE_FROGLIGHT).scatter(Blocks.DEAD_BUSH).spike(Blocks.PACKED_MUD)
            .trimmings(Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_WALL, Blocks.MUD_BRICKS)
            .shapes(DungeonShape.CAVERNS, 3, DungeonShape.HIVE, 2, DungeonShape.LABYRINTH, 1)
            .life(Affinity.BLOOD, DamageElement.BLOOD, Habitat.BEAST, Habitat.PLANT).bosses(BossKind.ALPHA, BossKind.BRUTE)
            .decor(Decor.ROOTS, Decor.GLOW_BERRIES, Decor.BONES).fog(0x2A1E14, 40)),
    /** An old temple the moss took back: cracked stone halls, green light, azaleas through the floor. */
    MOSSWEPT_RUINS(spec(PlayerRank.E, PlayerRank.C, 3).built()
            .walls(Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS)
            .floors(Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSS_BLOCK).accents(Blocks.CHISELED_STONE_BRICKS, Blocks.MOSS_BLOCK)
            .trim(Blocks.CHISELED_STONE_BRICKS).ceiling(Blocks.MOSSY_STONE_BRICKS).pillar(Blocks.MOSSY_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS)
            .light(Blocks.VERDANT_FROGLIGHT).scatter(Blocks.MOSS_CARPET).spike(Blocks.MOSSY_COBBLESTONE)
            .trimmings(Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_WALL, Blocks.MOSSY_STONE_BRICKS)
            .shapes(DungeonShape.GAUNTLET, 3, DungeonShape.LABYRINTH, 2, DungeonShape.CAVERNS, 2)
            .life(null, null, Habitat.PLANT, Habitat.UNDEAD, Habitat.BEAST).bosses(BossKind.BRUTE, BossKind.KNIGHT, BossKind.ALPHA)
            .decor(Decor.PILLARS, Decor.MOSS, Decor.AZALEA, Decor.ROOTS, Decor.CHAINS).fog(0x1C2618, 40).glow(0xC8F0B4).ambience(ParticleTypes.SPORE_BLOSSOM_AIR)),
    /** A buried tomb of sandstone: pillared halls, sarcophagi, urns, dust in the air. */
    SUNSCORCHED_TOMB(spec(PlayerRank.D, PlayerRank.B, 3).built()
            .walls(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE).floors(Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE)
            .accents(Blocks.CHISELED_SANDSTONE, Blocks.ORANGE_TERRACOTTA).trim(Blocks.CHISELED_SANDSTONE).ceiling(Blocks.SMOOTH_SANDSTONE)
            .pillar(Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE).light(Blocks.OCHRE_FROGLIGHT).scatter(Blocks.DEAD_BUSH).spike(Blocks.SANDSTONE)
            .trimmings(Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.SANDSTONE_WALL, Blocks.SMOOTH_SANDSTONE).carpet(Blocks.ORANGE_CARPET)
            .shapes(DungeonShape.LABYRINTH, 4, DungeonShape.GAUNTLET, 2, DungeonShape.CAVERNS, 1, DungeonShape.DESCENT, 1)
            .life(Affinity.SHADE, DamageElement.CRIMSON, Habitat.UNDEAD, Habitat.INSECT, Habitat.CULT).bosses(BossKind.KNIGHT, BossKind.MAGUS, BossKind.BROODMOTHER)
            .decor(Decor.PILLARS, Decor.COFFINS, Decor.URNS, Decor.COBWEBS, Decor.BRAZIERS, Decor.THRONE)
            .fog(0x3A2C16, 40).ambience(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()))),
    /** Catacombs of dark brick: bones in the walls, candles on the floor, chains and cold soul light. */
    BONE_CATACOMBS(spec(PlayerRank.D, PlayerRank.A, 3).built()
            .walls(Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_BRICKS)
            .floors(Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE, Blocks.CRACKED_DEEPSLATE_TILES).accents(Blocks.BONE_BLOCK, Blocks.CHISELED_DEEPSLATE)
            .trim(Blocks.POLISHED_DEEPSLATE).ceiling(Blocks.DEEPSLATE_BRICKS).pillar(Blocks.POLISHED_DEEPSLATE, Blocks.CHISELED_DEEPSLATE)
            .light(Blocks.PEARLESCENT_FROGLIGHT).scatter(Blocks.WHITE_CANDLE).spike(Blocks.COBBLED_DEEPSLATE).lantern(Blocks.SOUL_LANTERN)
            .trimmings(Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.POLISHED_DEEPSLATE_SLAB, Blocks.DEEPSLATE_BRICK_WALL, Blocks.POLISHED_DEEPSLATE)
            .shapes(DungeonShape.LABYRINTH, 4, DungeonShape.CAVERNS, 2, DungeonShape.DESCENT, 1, DungeonShape.GAUNTLET, 1)
            .life(Affinity.SHADE, DamageElement.CRIMSON, Habitat.UNDEAD, Habitat.CULT).bosses(BossKind.KNIGHT, BossKind.MAGUS, BossKind.BRUTE)
            .decor(Decor.PILLARS, Decor.COFFINS, Decor.COBWEBS, Decor.BONES, Decor.CHAINS, Decor.SOUL_FIRE).fog(0x121218, 36).glow(0xB8D0FF).ambience(ParticleTypes.WHITE_ASH)),
    /** A nest of round cells in resin and mud, clusters of glowing eggs, webs, honey underfoot. */
    CHITIN_HIVE(spec(PlayerRank.D, PlayerRank.A, 3).hive()
            .walls(Blocks.PACKED_MUD, Blocks.BROWN_TERRACOTTA, Blocks.TERRACOTTA, Blocks.PACKED_MUD).floors(Blocks.MUD, Blocks.PACKED_MUD, Blocks.COARSE_DIRT)
            .accents(Blocks.HONEYCOMB_BLOCK, Blocks.DRIPSTONE_BLOCK).ceiling(Blocks.BROWN_TERRACOTTA).light(Blocks.OCHRE_FROGLIGHT)
            .scatter(Blocks.BROWN_MUSHROOM).spike(Blocks.DRIPSTONE_BLOCK)
            .trimmings(Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_WALL, Blocks.MUD_BRICKS)
            .shapes(DungeonShape.HIVE, 5, DungeonShape.LABYRINTH, 1)
            .life(Affinity.VENOM, DamageElement.POISON, Habitat.INSECT).bosses(BossKind.BROODMOTHER, BossKind.BROODMOTHER, BossKind.ALPHA)
            .decor(Decor.EGGS, Decor.COBWEBS, Decor.STALAGMITES).fog(0x2C2008, 36).ambience(ParticleTypes.FALLING_HONEY)),
    /** A drowned temple of prismarine: water channels down the halls, sea light, dead coral. */
    SUNKEN_SANCTUM(spec(PlayerRank.C, PlayerRank.A, 3).built()
            .walls(Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS).floors(Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS)
            .accents(Blocks.DARK_PRISMARINE, Blocks.PRISMARINE).trim(Blocks.DARK_PRISMARINE).ceiling(Blocks.PRISMARINE)
            .pillar(Blocks.PRISMARINE_BRICKS, Blocks.SEA_LANTERN).light(Blocks.SEA_LANTERN).scatter(Blocks.DEAD_BRAIN_CORAL).spike(Blocks.PRISMARINE)
            .trimmings(Blocks.DARK_PRISMARINE_STAIRS, Blocks.DARK_PRISMARINE_SLAB, Blocks.PRISMARINE_WALL, Blocks.DARK_PRISMARINE).carpet(Blocks.CYAN_CARPET)
            .shapes(DungeonShape.GAUNTLET, 3, DungeonShape.CAVERNS, 2, DungeonShape.LABYRINTH, 1, DungeonShape.CHASM, 1)
            .life(Affinity.FROST, null, Habitat.AQUATIC, Habitat.CULT, Habitat.KNIGHT).bosses(BossKind.MAGUS, BossKind.KNIGHT)
            .decor(Decor.PILLARS, Decor.CHANNELS, Decor.CORAL, Decor.CHAINS).fog(0x0C2A32, 40).glow(0xA0F0E4).ambience(ParticleTypes.UNDERWATER)),
    /** Caverns of blue ice and snow, spires of packed ice, cold light, snow drifting down. */
    RIMEFROST(spec(PlayerRank.C, PlayerRank.A, 3).cave()
            .walls(Blocks.PACKED_ICE, Blocks.PACKED_ICE, Blocks.SNOW_BLOCK, Blocks.CALCITE).floors(Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.CALCITE)
            .accents(Blocks.BLUE_ICE, Blocks.BLUE_ICE).ceiling(Blocks.PACKED_ICE).light(Blocks.SEA_LANTERN).scatter(Blocks.SNOW).spike(Blocks.PACKED_ICE)
            .trimmings(Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_DIORITE_SLAB, Blocks.DIORITE_WALL, Blocks.POLISHED_DIORITE).lantern(Blocks.SOUL_LANTERN)
            .shapes(DungeonShape.HOLLOW, 3, DungeonShape.CHASM, 2, DungeonShape.CAVERNS, 2, DungeonShape.DESCENT, 1)
            .life(Affinity.FROST, null, Habitat.FROST, Habitat.BEAST, Habitat.UNDEAD).bosses(BossKind.ALPHA, BossKind.ALPHA, BossKind.KNIGHT)
            .decor(Decor.STALAGMITES, Decor.ICE).fog(0x9CC4E4, 34).glow(0xBCDCFF).ambience(ParticleTypes.SNOWFLAKE)),
    /** A crystal mine: timber-propped tunnels through tuff and calcite, seams of amethyst glowing in the walls. */
    CRYSTAL_SEAM(spec(PlayerRank.C, PlayerRank.A, 3).cave().corridors()
            .walls(Blocks.TUFF, Blocks.DEEPSLATE, Blocks.CALCITE, Blocks.TUFF).floors(Blocks.TUFF, Blocks.COBBLED_DEEPSLATE, Blocks.GRAVEL)
            .accents(Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST).ceiling(Blocks.DEEPSLATE).light(Blocks.PEARLESCENT_FROGLIGHT)
            .scatter(Blocks.AMETHYST_CLUSTER).spike(Blocks.CALCITE)
            .trimmings(Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_PLANKS)
            .shapes(DungeonShape.DESCENT, 3, DungeonShape.LABYRINTH, 2, DungeonShape.CAVERNS, 2)
            .life(Affinity.STORM, null, Habitat.CRYSTAL, Habitat.INSECT, Habitat.KNIGHT).bosses(BossKind.MAGUS, BossKind.BRUTE, BossKind.ALPHA)
            .decor(Decor.CRYSTALS, Decor.SUPPORTS, Decor.STALAGMITES).fog(0x1C1430, 40).glow(0xE4CCFF).ambience(ParticleTypes.GLOW)),
    /** A cavern of giant mushrooms on mycelium, spores hanging in a violet haze. */
    SPOREFALL_DEEP(spec(PlayerRank.C, PlayerRank.B, 3).cave()
            .walls(Blocks.TUFF, Blocks.PACKED_MUD, Blocks.DEEPSLATE).floors(Blocks.MYCELIUM, Blocks.MOSS_BLOCK, Blocks.PODZOL)
            .accents(Blocks.BROWN_MUSHROOM_BLOCK, Blocks.MUSHROOM_STEM).ceiling(Blocks.TUFF).light(Blocks.SHROOMLIGHT).scatter(Blocks.RED_MUSHROOM)
            .spike(Blocks.MUSHROOM_STEM).trimmings(Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_WALL, Blocks.MUD_BRICKS)
            .shapes(DungeonShape.HOLLOW, 4, DungeonShape.CAVERNS, 2, DungeonShape.HIVE, 1)
            .life(Affinity.VENOM, DamageElement.POISON, Habitat.PLANT, Habitat.INSECT).bosses(BossKind.BROODMOTHER, BossKind.BRUTE, BossKind.MAGUS)
            .decor(Decor.MUSHROOMS, Decor.MOSS, Decor.ROOTS).fog(0x241A2E, 38).glow(0xDCC4FF).ambience(ParticleTypes.SPORE_BLOSSOM_AIR)),
    /** A fortress keep: stone halls with timber ceilings, red carpets and banners, chandeliers, a throne. */
    IRONBOUND_KEEP(spec(PlayerRank.B, PlayerRank.S, 3).built()
            .walls(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE)
            .floors(Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE).accents(Blocks.CHISELED_STONE_BRICKS, Blocks.DARK_OAK_PLANKS)
            .trim(Blocks.POLISHED_ANDESITE).ceiling(Blocks.DARK_OAK_PLANKS).pillar(Blocks.STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS)
            .light(Blocks.GLOWSTONE).spike(Blocks.STONE_BRICKS).carpet(Blocks.RED_CARPET).banner(Blocks.RED_WALL_BANNER)
            .trimmings(Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL, Blocks.STONE_BRICKS)
            .shapes(DungeonShape.GAUNTLET, 3, DungeonShape.SPIRE, 3, DungeonShape.LABYRINTH, 1)
            .life(null, null, Habitat.KNIGHT, Habitat.CULT, Habitat.BEAST).bosses(BossKind.KNIGHT, BossKind.KNIGHT, BossKind.BRUTE, BossKind.MAGUS)
            .decor(Decor.PILLARS, Decor.CARPET, Decor.BANNERS, Decor.CHAINS, Decor.BRAZIERS, Decor.THRONE).fog(0x1A1A20, 44)),
    /** A tower of tuff and green copper that hums with storm: lightning rods, copper lamps, sparks in the air. */
    STORMSPIRE(spec(PlayerRank.B, PlayerRank.S, 3).built()
            .walls(Blocks.TUFF_BRICKS, Blocks.POLISHED_TUFF, Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.TUFF_BRICKS)
            .floors(Blocks.POLISHED_TUFF, Blocks.WAXED_OXIDIZED_CUT_COPPER).accents(Blocks.CHISELED_TUFF_BRICKS, Blocks.WAXED_WEATHERED_CUT_COPPER)
            .trim(Blocks.WAXED_WEATHERED_CUT_COPPER).ceiling(Blocks.TUFF_BRICKS).pillar(Blocks.POLISHED_TUFF, Blocks.CHISELED_TUFF)
            .light(Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(CopperBulbBlock.LIT, true)).scatter(Blocks.LIGHTNING_ROD).spike(Blocks.TUFF)
            .trimmings(Blocks.POLISHED_TUFF_STAIRS, Blocks.POLISHED_TUFF_SLAB, Blocks.TUFF_BRICK_WALL, Blocks.POLISHED_TUFF).carpet(Blocks.CYAN_CARPET)
            .shapes(DungeonShape.SPIRE, 5, DungeonShape.GAUNTLET, 1, DungeonShape.CHASM, 1)
            .life(Affinity.STORM, null, Habitat.STORM, Habitat.CULT, Habitat.KNIGHT).bosses(BossKind.MAGUS, BossKind.MAGUS, BossKind.KNIGHT, BossKind.WYRM)
            .decor(Decor.PILLARS, Decor.COPPER, Decor.END_RODS, Decor.CHAINS).fog(0x161C2E, 44).glow(0xC4DCFF).ambience(ParticleTypes.ELECTRIC_SPARK)),
    /** Islands of pale stone adrift in a lightless void, obsidian spires, motes rising out of the dark. */
    STARLESS_EXPANSE(spec(PlayerRank.A, PlayerRank.S, 3).cave()
            .walls(Blocks.OBSIDIAN, Blocks.BLACKSTONE, Blocks.OBSIDIAN).floors(Blocks.END_STONE, Blocks.END_STONE_BRICKS)
            .accents(Blocks.CRYING_OBSIDIAN, Blocks.PURPUR_BLOCK).ceiling(Blocks.OBSIDIAN).light(Blocks.PEARLESCENT_FROGLIGHT).scatter(Blocks.END_ROD)
            .spike(Blocks.OBSIDIAN).trimmings(Blocks.END_STONE_BRICK_STAIRS, Blocks.END_STONE_BRICK_SLAB, Blocks.END_STONE_BRICK_WALL, Blocks.END_STONE_BRICKS)
            .shapes(DungeonShape.CHASM, 5, DungeonShape.HOLLOW, 1, DungeonShape.DESCENT, 1)
            .life(Affinity.SHADE, DamageElement.CRIMSON, Habitat.DEMON, Habitat.CULT, Habitat.STORM).bosses(BossKind.MAGUS, BossKind.KNIGHT, BossKind.WYRM)
            .decor(Decor.END_RODS, Decor.STALAGMITES).fog(0x08060E, 64).glow(0xB8B8FF).ambience(ParticleTypes.REVERSE_PORTAL)),
    /** A fortress of the burning dark: red brick and blackstone, lava in channels, soul fire in braziers. */
    INFERNAL_CITADEL(spec(PlayerRank.A, PlayerRank.S, 3).built()
            .walls(Blocks.RED_NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.RED_NETHER_BRICKS)
            .floors(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.NETHER_BRICKS).accents(Blocks.GILDED_BLACKSTONE, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)
            .trim(Blocks.CHISELED_POLISHED_BLACKSTONE).ceiling(Blocks.NETHER_BRICKS).pillar(Blocks.POLISHED_BLACKSTONE, Blocks.CHISELED_POLISHED_BLACKSTONE)
            .light(Blocks.SHROOMLIGHT).spike(Blocks.BLACKSTONE).banner(Blocks.BLACK_WALL_BANNER).carpet(Blocks.BLACK_CARPET)
            .trimmings(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB, Blocks.RED_NETHER_BRICK_WALL, Blocks.POLISHED_BLACKSTONE_BRICKS).liquid(Blocks.LAVA)
            .shapes(DungeonShape.SPIRE, 3, DungeonShape.GAUNTLET, 3, DungeonShape.CHASM, 2)
            .life(Affinity.FLAME, DamageElement.FIRE, Habitat.DEMON, Habitat.FLAME, Habitat.KNIGHT).bosses(BossKind.KNIGHT, BossKind.WYRM, BossKind.MAGUS)
            .decor(Decor.PILLARS, Decor.CHANNELS, Decor.SOUL_FIRE, Decor.CHAINS, Decor.BANNERS, Decor.THRONE).fog(0x2C0A06, 40).glow(0xFFB48C).ambience(ParticleTypes.ASH)),

    // ------------------------------------------------------------------ never rolled
    /** The awakening trial's: bone-white stone with gold seams over dark tiled floors, cold soul light. */
    THRESHOLD(spec(PlayerRank.D, PlayerRank.D, 0).cave()
            .walls(Blocks.CALCITE, Blocks.POLISHED_DIORITE, Blocks.CALCITE).floors(Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES)
            .accents(Blocks.GILDED_BLACKSTONE, Blocks.RAW_GOLD_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK).light(Blocks.GLOWSTONE).scatter(Blocks.SOUL_LANTERN)
            .spike(Blocks.BONE_BLOCK).trimmings(Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_DIORITE_SLAB, Blocks.DIORITE_WALL, Blocks.POLISHED_DIORITE)
            .shapes(DungeonShape.CAVERNS, 1).life(null, DamageElement.CRIMSON).decor(Decor.STALAGMITES).fog(0x16161C, 44));

    /** How rooms and passages are cut. */
    public enum Build {
        /** Natural caves: uneven caverns joined by winding tunnels. */
        CAVE,
        /** Built halls: straight walls, flat floors, vaulted ceilings, square corridors with steps. */
        BUILT,
        /** Round cells joined by short tubes. */
        HIVE
    }

    /** Dressing a realm's rooms can have ({@link DungeonDecor}). */
    public enum Decor {
        STALAGMITES, ROOTS, GLOW_BERRIES, MOSS, AZALEA, POOLS, ICE, CRYSTALS, MUSHROOMS, BONES, COBWEBS, CHAINS, PILLARS,
        CARPET, BANNERS, BRAZIERS, SOUL_FIRE, COFFINS, URNS, LAVA, BASALT, SCULK, EGGS, SUPPORTS, CORAL, COPPER,
        END_RODS, NETHER_PLANTS, THRONE, CHANNELS
    }

    public final PlayerRank minRank;
    public final PlayerRank maxRank;
    /** How often it comes up among the realms of a rank (0: never rolled). */
    public final int weight;
    public final Build build;
    /** Corridors instead of tubes between natural caves (a mine's square tunnels). */
    public final boolean corridors;
    private final Map<DungeonShape, Integer> shapes;
    /** The affinity most of its creatures carry; null leaves it to the rank. */
    @Nullable public final Affinity affinity;
    /** The element its creatures' hits build up; null uses the rank's ({@link GateTheme#element}). */
    @Nullable public final DamageElement element;
    public final Set<Habitat> habitats;
    private final List<BossKind> bosses;
    private final BlockState[] walls;
    private final BlockState[] floors;
    private final BlockState[] accents;
    @Nullable private final BlockState ceiling;
    @Nullable private final BlockState trim;
    public final BlockState pillar;
    public final BlockState pillarCap;
    /** The block that lights the place, set into walls (always a full block). */
    public final BlockState light;
    /** A lantern that can hang from a chain. */
    public final BlockState lantern;
    /** Small things on the floor: lichen, moss, crystals, candles, fire. */
    @Nullable public final BlockState scatter;
    /** Stalagmites, spires and posts. */
    public final BlockState spike;
    public final Block stairs;
    /** Half steps on bridges and winding stairs. */
    public final Block slab;
    /** Railings on bridges and ramps (a wall or a fence). */
    public final Block railing;
    /** The walkway of bridges, ramps and steps. */
    public final BlockState deck;
    /** What fills the bottom of a chasm and the pools. */
    public final Block liquid;
    @Nullable public final Block carpet;
    @Nullable public final Block banner;
    /** The fog's colour on the other side (blended in on the client) and how far you can see. */
    public final int fogColor;
    public final float fogFar;
    /**
     * The colour of the light down there (on the client, the light of lamps and fire takes it on: cold in the ice,
     * violet in the haze); 0 leaves it the usual warm torchlight.
     */
    public final int glow;
    /** Drifting in the air around you, now and then; null for nothing. */
    @Nullable public final ParticleOptions ambience;
    public final Set<Decor> decor;

    GateRealm(Spec s) {
        this.minRank = s.min;
        this.maxRank = s.max;
        this.weight = s.weight;
        this.build = s.build;
        this.corridors = s.corridors;
        this.shapes = s.shapes;
        this.affinity = s.affinity;
        this.element = s.element;
        this.habitats = s.habitats;
        this.bosses = s.bosses;
        this.walls = s.walls;
        this.floors = s.floors;
        this.accents = s.accents;
        this.ceiling = s.ceiling;
        this.trim = s.trim;
        this.pillar = s.pillar != null ? s.pillar : s.walls[0];
        this.pillarCap = s.pillarCap != null ? s.pillarCap : this.pillar;
        this.light = s.light;
        this.lantern = s.lantern;
        this.scatter = s.scatter;
        this.spike = s.spike;
        this.stairs = s.stairs;
        this.slab = s.slab;
        this.railing = s.railing;
        this.deck = s.deck;
        this.liquid = s.liquid;
        this.carpet = s.carpet;
        this.banner = s.banner;
        this.fogColor = s.fog;
        this.fogFar = s.fogFar;
        this.glow = s.glow;
        this.ambience = s.ambience;
        this.decor = s.decor;
    }

    // ------------------------------------------------------------------ choosing

    /** The realm a gate of this rank leads to, the same every time for the same seed. */
    public static GateRealm forSeed(PlayerRank rank, long seed) {
        return roll(rank, RandomSource.create(seed * 0x2545F491L + 0x7EA1L));
    }

    /** A realm for a rank. */
    public static GateRealm roll(PlayerRank rank, RandomSource r) {
        List<GateRealm> fits = forRank(rank);
        int total = 0;
        for (GateRealm g : fits) total += g.weight;
        int roll = r.nextInt(Math.max(1, total));
        for (GateRealm g : fits) {
            roll -= g.weight;
            if (roll < 0) return g;
        }
        return classic(rank);
    }

    /** Every realm a gate of this rank can lead to. */
    public static List<GateRealm> forRank(PlayerRank rank) {
        List<GateRealm> out = new ArrayList<>();
        for (GateRealm g : values()) if (g.weight > 0 && g.livesAt(rank)) out.add(g);
        return out;
    }

    public boolean livesAt(PlayerRank rank) {
        return rank.ordinal() >= minRank.ordinal() && rank.ordinal() <= maxRank.ordinal();
    }

    /** The look each rank had before realms: caves saved back then keep it. */
    public static GateRealm classic(PlayerRank rank) {
        return values()[rank.ordinal()];
    }

    /** The shape a gate of this realm takes, the same every time for the same seed. */
    public DungeonShape shapeFor(long seed) {
        RandomSource r = RandomSource.create(seed * 0x5851F42DL + 0x5A9EL);
        int total = 0;
        for (int w : shapes.values()) total += w;
        int roll = r.nextInt(Math.max(1, total));
        for (Map.Entry<DungeonShape, Integer> e : shapes.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) return e.getKey();
        }
        return DungeonShape.CAVERNS;
    }

    public Set<DungeonShape> shapes() {
        return shapes.keySet();
    }

    /** True if this realm favours that boss (it is then four times as likely). */
    public boolean favours(BossKind kind) {
        return bosses.contains(kind);
    }

    /** How strongly it favours a boss (a boss named twice counts twice). */
    public int bossWeight(BossKind kind) {
        int n = 0;
        for (BossKind k : bosses) if (k == kind) n++;
        return n;
    }

    @Nullable
    public static GateRealm byId(String id) {
        for (GateRealm g : values()) if (g.name().equalsIgnoreCase(id)) return g;
        return null;
    }

    public static GateRealm byIndex(int i) {
        GateRealm[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, i))];
    }

    public String id() {
        return name().toLowerCase();
    }

    public Component displayName() {
        return Component.translatable("gate.beyondsolo.realm." + id());
    }

    // ------------------------------------------------------------------ the look

    public boolean built() {
        return build == Build.BUILT;
    }

    public BlockState wall(RandomSource r) {
        return r.nextInt(9) == 0 ? accents[r.nextInt(accents.length)] : walls[r.nextInt(walls.length)];
    }

    /**
     * A wall block at a height: built realms run a band of trim along their walls at knee and lintel height (every
     * 6 blocks from the base line), natural ones just their rock.
     */
    public BlockState wallAt(int y, RandomSource r) {
        if (trim != null && Math.floorMod(y - DungeonLayout.BASE_Y, 6) == 0) return trim;
        return wall(r);
    }

    /** A floor block: built realms lay theirs in a check of their first two floors, now and then another. */
    public BlockState floorAt(int x, int z, RandomSource r) {
        if (build == Build.BUILT && floors.length >= 2) {
            if (floors.length > 2 && r.nextInt(7) == 0) return floors[2 + r.nextInt(floors.length - 2)];
            return floors[(Math.floorDiv(x, 2) + Math.floorDiv(z, 2)) & 1];
        }
        return floors[r.nextInt(floors.length)];
    }

    public BlockState floor(RandomSource r) {
        return floors[r.nextInt(floors.length)];
    }

    public BlockState ceiling(RandomSource r) {
        if (ceiling == null || r.nextInt(8) == 0) return wall(r);
        return ceiling;
    }

    public BlockState accent(RandomSource r) {
        return accents[r.nextInt(accents.length)];
    }

    public BlockState mainWall() {
        return walls[0];
    }

    public boolean has(Decor d) {
        return decor.contains(d);
    }

    // ------------------------------------------------------------------ the spec

    private static Spec spec(PlayerRank min, PlayerRank max, int weight) {
        return new Spec(min, max, weight);
    }

    /** Fluent description of a realm (only used while the realms are made). */
    private static final class Spec {
        final PlayerRank min, max;
        final int weight;
        Build build = Build.CAVE;
        boolean corridors;
        final Map<DungeonShape, Integer> shapes = new EnumMap<>(DungeonShape.class);
        Affinity affinity;
        DamageElement element;
        Set<Habitat> habitats = EnumSet.noneOf(Habitat.class);
        List<BossKind> bosses = List.of();
        BlockState[] walls, floors, accents;
        BlockState ceiling, trim, pillar, pillarCap, light, scatter, spike;
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        Block stairs = Blocks.COBBLESTONE_STAIRS, slab = Blocks.COBBLESTONE_SLAB, railing = Blocks.COBBLESTONE_WALL;
        BlockState deck = Blocks.COBBLESTONE.defaultBlockState();
        Block liquid = Blocks.WATER;
        Block carpet, banner;
        int fog = 0x101018;
        float fogFar = 44;
        int glow;
        ParticleOptions ambience;
        Set<Decor> decor = EnumSet.noneOf(Decor.class);

        Spec(PlayerRank min, PlayerRank max, int weight) {
            this.min = min;
            this.max = max;
            this.weight = weight;
        }

        Spec cave() { build = Build.CAVE; return this; }
        Spec built() { build = Build.BUILT; return this; }
        Spec hive() { build = Build.HIVE; return this; }
        Spec corridors() { corridors = true; return this; }
        Spec walls(Block... b) { walls = states(b); return this; }
        Spec floors(Block... b) { floors = states(b); return this; }
        Spec accents(Block... b) { accents = states(b); return this; }
        Spec ceiling(Block b) { ceiling = b.defaultBlockState(); return this; }
        Spec trim(Block b) { trim = b.defaultBlockState(); return this; }
        Spec pillar(Block body, Block cap) { pillar = body.defaultBlockState(); pillarCap = cap.defaultBlockState(); return this; }
        Spec light(Block b) { return light(b.defaultBlockState()); }
        Spec light(BlockState s) { light = s; return this; }
        Spec lantern(Block b) { lantern = b.defaultBlockState(); return this; }
        Spec scatter(Block b) {
            BlockState s = b.defaultBlockState();
            if (b instanceof CandleBlock) s = s.setValue(CandleBlock.LIT, true);
            scatter = s;
            return this;
        }
        Spec spike(Block b) { spike = b.defaultBlockState(); return this; }
        Spec trimmings(Block stairs, Block slab, Block railing, Block deck) {
            this.stairs = stairs;
            this.slab = slab;
            this.railing = railing;
            this.deck = deck.defaultBlockState();
            return this;
        }
        Spec liquid(Block b) { liquid = b; return this; }
        Spec carpet(Block b) { carpet = b; return this; }
        Spec banner(Block b) { banner = b; return this; }

        Spec shapes(Object... pairs) {
            for (int i = 0; i + 1 < pairs.length; i += 2) shapes.put((DungeonShape) pairs[i], (Integer) pairs[i + 1]);
            return this;
        }

        Spec life(@Nullable Affinity affinity, @Nullable DamageElement element, Habitat... habitats) {
            this.affinity = affinity;
            this.element = element;
            this.habitats = habitats.length == 0 ? EnumSet.noneOf(Habitat.class) : EnumSet.copyOf(List.of(habitats));
            return this;
        }

        Spec bosses(BossKind... kinds) { bosses = List.of(kinds); return this; }
        Spec decor(Decor... d) { decor = EnumSet.copyOf(List.of(d)); return this; }
        Spec fog(int color, float far) { fog = color; fogFar = far; return this; }
        Spec glow(int color) { glow = color; return this; }
        Spec ambience(ParticleOptions p) { ambience = p; return this; }

        private static BlockState[] states(Block... blocks) {
            BlockState[] out = new BlockState[blocks.length];
            for (int i = 0; i < blocks.length; i++) out[i] = blocks[i].defaultBlockState();
            return out;
        }
    }
}
