package com.beyondsolo.gate;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * What a gate of each rank looks like and holds: the dungeon's rock and lights, its element, its monsters,
 * how big it is and how strong its boss is. One theme per rank, so a gate's rank can be read from the colour of its
 * light before the System says it.
 */
public enum GateTheme {
    E(PlayerRank.E, DamageElement.BLOOD, 5, 2,
            blocks(Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE), blocks(Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.STONE),
            blocks(Blocks.MOSSY_COBBLESTONE, Blocks.TUFF), Blocks.GLOW_LICHEN, Blocks.OCHRE_FROGLIGHT, Blocks.POINTED_DRIPSTONE,
            List.of(EntityType.ZOMBIE, EntityType.SPIDER, EntityType.SKELETON)),
    D(PlayerRank.D, DamageElement.POISON, 6, 3,
            blocks(Blocks.MOSSY_COBBLESTONE, Blocks.STONE, Blocks.MOSS_BLOCK), blocks(Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT, Blocks.MUD),
            blocks(Blocks.MOSS_BLOCK, Blocks.MOSSY_STONE_BRICKS), Blocks.MOSS_CARPET, Blocks.VERDANT_FROGLIGHT, Blocks.POINTED_DRIPSTONE,
            List.of(EntityType.HUSK, EntityType.CAVE_SPIDER, EntityType.SKELETON)),
    C(PlayerRank.C, DamageElement.BLOOD, 7, 3,
            blocks(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.TUFF), blocks(Blocks.SMOOTH_BASALT, Blocks.DEEPSLATE, Blocks.PACKED_ICE),
            blocks(Blocks.BLUE_ICE, Blocks.PRISMARINE), Blocks.GLOW_LICHEN, Blocks.SEA_LANTERN, Blocks.POINTED_DRIPSTONE,
            List.of(EntityType.STRAY, EntityType.ZOMBIE, EntityType.SPIDER, EntityType.VINDICATOR)),
    B(PlayerRank.B, DamageElement.POISON, 8, 4,
            blocks(Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.DEEPSLATE), blocks(Blocks.SOUL_SOIL, Blocks.BLACKSTONE, Blocks.SOUL_SAND),
            blocks(Blocks.AMETHYST_BLOCK, Blocks.CRYING_OBSIDIAN), Blocks.AMETHYST_CLUSTER, Blocks.PEARLESCENT_FROGLIGHT, Blocks.AMETHYST_CLUSTER,
            List.of(EntityType.VINDICATOR, EntityType.STRAY, EntityType.CAVE_SPIDER, EntityType.WITCH)),
    A(PlayerRank.A, DamageElement.FIRE, 9, 4,
            blocks(Blocks.BASALT, Blocks.BLACKSTONE, Blocks.NETHERRACK), blocks(Blocks.NETHERRACK, Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK),
            blocks(Blocks.GILDED_BLACKSTONE, Blocks.MAGMA_BLOCK), Blocks.FIRE, Blocks.SHROOMLIGHT, Blocks.POINTED_DRIPSTONE,
            List.of(EntityType.WITHER_SKELETON, EntityType.BLAZE, EntityType.VINDICATOR)),
    S(PlayerRank.S, DamageElement.CRIMSON, 10, 5,
            blocks(Blocks.OBSIDIAN, Blocks.RED_NETHER_BRICKS, Blocks.BLACKSTONE), blocks(Blocks.CRIMSON_NYLIUM, Blocks.BLACKSTONE, Blocks.NETHER_WART_BLOCK),
            blocks(Blocks.CRYING_OBSIDIAN, Blocks.NETHER_WART_BLOCK), Blocks.CRIMSON_ROOTS, Blocks.SHROOMLIGHT, Blocks.POINTED_DRIPSTONE,
            List.of(EntityType.WITHER_SKELETON, EntityType.EVOKER, EntityType.BLAZE, EntityType.VINDICATOR));

    public final PlayerRank rank;
    public final DamageElement element;
    /** Rooms on the way to the boss's room (the boss room is extra). */
    public final int rooms;
    /** Monsters in each room, at least. */
    public final int packSize;
    private final BlockState[] walls;
    private final BlockState[] floors;
    private final BlockState[] accents;
    /** Small things on the floor: lichen, moss, crystals, fire. */
    public final Block scatter;
    /** The block that lights the cave, set into walls and floors. */
    public final Block light;
    public final Block spike;
    public final List<EntityType<?>> monsters;

    GateTheme(PlayerRank rank, DamageElement element, int rooms, int packSize, BlockState[] walls, BlockState[] floors, BlockState[] accents,
              Block scatter, Block light, Block spike, List<EntityType<?>> monsters) {
        this.rank = rank;
        this.element = element;
        this.rooms = rooms;
        this.packSize = packSize;
        this.walls = walls;
        this.floors = floors;
        this.accents = accents;
        this.scatter = scatter;
        this.light = light;
        this.spike = spike;
        this.monsters = monsters;
    }

    public static GateTheme of(PlayerRank rank) {
        return values()[rank.ordinal()];
    }

    public BlockState wall(RandomSource r) {
        return r.nextInt(9) == 0 ? accents[r.nextInt(accents.length)] : walls[r.nextInt(walls.length)];
    }

    public BlockState floor(RandomSource r) {
        return floors[r.nextInt(floors.length)];
    }

    public BlockState accent(RandomSource r) {
        return accents[r.nextInt(accents.length)];
    }

    public BlockState mainWall() {
        return walls[0];
    }

    /** The colour of the gate's light (the rank's colour). */
    public int color() {
        return rank.color();
    }

    public Component dungeonName() {
        return Component.translatable("gate.beyondsolo.dungeon." + name().toLowerCase());
    }

    public Component bossName() {
        return Component.translatable("gate.beyondsolo.boss." + name().toLowerCase());
    }

    /** Boss health: 120 for an E-rank boss, up to 660 for S. */
    public float bossHealth() {
        return 120f * (1f + 0.9f * rank.tier());
    }

    public float bossDamage() {
        return 7f + 3.5f * rank.tier();
    }

    /** Monsters get this much more health (as a multiplier on top of their own: E +0%, S +200%). */
    public double mobHealthBonus() {
        return 0.4 * rank.tier();
    }

    public double mobDamageBonus() {
        return 0.25 * rank.tier();
    }

    /** Element buildup per hit from the gate's monsters (the boss hits much harder). */
    public float mobBuildup() {
        return 12f + 3f * rank.tier();
    }

    /** System XP for clearing the gate, before rank scaling of quests. */
    public long clearXp() {
        return 300L * (1L + rank.tier()) * (1L + rank.tier());
    }

    private static BlockState[] blocks(Block... blocks) {
        BlockState[] out = new BlockState[blocks.length];
        for (int i = 0; i < blocks.length; i++) out[i] = blocks[i].defaultBlockState();
        return out;
    }
}
