package com.beyondsolo.gate;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;

/**
 * What a gate of each rank looks like and holds: the dungeon's rock and lights, its element, how big it is and how
 * strong its boss is. Its creatures come from {@link com.beyondsolo.enemy.EnemyRoster}. One theme per rank, so a
 * gate's rank can be read from the colour of its light before the System says it.
 */
public enum GateTheme {
    E(PlayerRank.E, DamageElement.BLOOD, 5, 2,
            blocks(Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE), blocks(Blocks.GRAVEL, Blocks.COARSE_DIRT, Blocks.STONE),
            blocks(Blocks.MOSSY_COBBLESTONE, Blocks.TUFF), Blocks.GLOW_LICHEN, Blocks.OCHRE_FROGLIGHT, Blocks.POINTED_DRIPSTONE),
    D(PlayerRank.D, DamageElement.POISON, 6, 3,
            blocks(Blocks.MOSSY_COBBLESTONE, Blocks.STONE, Blocks.MOSS_BLOCK), blocks(Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT, Blocks.MUD),
            blocks(Blocks.MOSS_BLOCK, Blocks.MOSSY_STONE_BRICKS), Blocks.MOSS_CARPET, Blocks.VERDANT_FROGLIGHT, Blocks.POINTED_DRIPSTONE),
    C(PlayerRank.C, DamageElement.BLOOD, 7, 3,
            blocks(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.TUFF), blocks(Blocks.SMOOTH_BASALT, Blocks.DEEPSLATE, Blocks.PACKED_ICE),
            blocks(Blocks.BLUE_ICE, Blocks.PRISMARINE), Blocks.GLOW_LICHEN, Blocks.SEA_LANTERN, Blocks.POINTED_DRIPSTONE),
    B(PlayerRank.B, DamageElement.POISON, 8, 4,
            blocks(Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.DEEPSLATE), blocks(Blocks.SOUL_SOIL, Blocks.BLACKSTONE, Blocks.SOUL_SAND),
            blocks(Blocks.AMETHYST_BLOCK, Blocks.CRYING_OBSIDIAN), Blocks.AMETHYST_CLUSTER, Blocks.PEARLESCENT_FROGLIGHT, Blocks.AMETHYST_CLUSTER),
    A(PlayerRank.A, DamageElement.FIRE, 9, 4,
            blocks(Blocks.BASALT, Blocks.BLACKSTONE, Blocks.NETHERRACK), blocks(Blocks.NETHERRACK, Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK),
            blocks(Blocks.GILDED_BLACKSTONE, Blocks.MAGMA_BLOCK), Blocks.FIRE, Blocks.SHROOMLIGHT, Blocks.POINTED_DRIPSTONE),
    S(PlayerRank.S, DamageElement.CRIMSON, 10, 5,
            blocks(Blocks.OBSIDIAN, Blocks.RED_NETHER_BRICKS, Blocks.BLACKSTONE), blocks(Blocks.CRIMSON_NYLIUM, Blocks.BLACKSTONE, Blocks.NETHER_WART_BLOCK),
            blocks(Blocks.CRYING_OBSIDIAN, Blocks.NETHER_WART_BLOCK), Blocks.CRIMSON_ROOTS, Blocks.SHROOMLIGHT, Blocks.POINTED_DRIPSTONE),
    /**
     * The awakening trial behind a sealed gate (not a rank of its own). Bone-white stone with gold seams over dark tiled
     * floors, cold soul light, and the way down to the Hollow Arbiter: since 0.1.1 a short temple
     * ({@link DungeonLayout#temple}) with nothing else alive in it. {@link #rooms} is the old long descent, kept for trials
     * carved before then.
     */
    TRIAL(PlayerRank.D, DamageElement.CRIMSON, 11, 2,
            blocks(Blocks.CALCITE, Blocks.POLISHED_DIORITE, Blocks.CALCITE), blocks(Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES),
            blocks(Blocks.GILDED_BLACKSTONE, Blocks.RAW_GOLD_BLOCK, Blocks.CHISELED_QUARTZ_BLOCK), Blocks.SOUL_LANTERN, Blocks.GLOWSTONE, Blocks.BONE_BLOCK);

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

    GateTheme(PlayerRank rank, DamageElement element, int rooms, int packSize, BlockState[] walls, BlockState[] floors, BlockState[] accents,
              Block scatter, Block light, Block spike) {
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
    }

    public static GateTheme of(PlayerRank rank) {
        return values()[rank.ordinal()];
    }

    /** A theme by its ordinal (as stored on gate monsters), clamped. */
    public static GateTheme byIndex(int i) {
        return values()[Math.max(0, Math.min(values().length - 1, i))];
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

    /** The colour of the gate's light (the rank's colour; the trial's pale gold). */
    public int color() {
        return this == TRIAL ? 0xF2D58A : rank.color();
    }

    public Component dungeonName() {
        return Component.translatable("gate.beyondsolo.dungeon." + name().toLowerCase());
    }

    public Component bossName() {
        return Component.translatable("gate.beyondsolo.boss." + name().toLowerCase());
    }

    /** Boss health: 300 for an E-rank boss, about 16,500 for S (see {@link com.beyondsolo.system.Balance}). */
    public float bossHealth() {
        return com.beyondsolo.system.Balance.bossHealth(rank);
    }

    /** Boss damage: 7 for an E-rank boss, about 490 for S. */
    public float bossDamage() {
        return com.beyondsolo.system.Balance.bossDamage(rank);
    }

    /** Monsters get this much more health (on top of their own: E +0%, S +5400%). */
    public double mobHealthBonus() {
        return com.beyondsolo.system.Balance.health(rank) - 1;
    }

    public double mobDamageBonus() {
        return com.beyondsolo.system.Balance.damage(rank) - 1;
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
