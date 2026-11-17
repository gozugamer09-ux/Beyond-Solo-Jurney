package com.beyondsolo.depths;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * A hidden wall: a plug of rock that looks like the cave's own ({@link Look}) across the tunnel into a hidden chamber.
 * It can't be mined; knock on it (hit it) and it sounds hollow, and the third knock brings it down
 * ({@link HiddenRooms#knock}). Pulse Scan shows it from afar. Now and then a faint mote of light seeps out of it.
 */
public class HiddenSealBlock extends Block {
    public static final MapCodec<HiddenSealBlock> CODEC = simpleCodec(HiddenSealBlock::new);

    /** The rock it passes for (each look's model uses that block's own textures). */
    public enum Look implements StringRepresentable {
        STONE(Blocks.STONE), ANDESITE(Blocks.ANDESITE), COBBLESTONE(Blocks.COBBLESTONE), MOSSY_COBBLESTONE(Blocks.MOSSY_COBBLESTONE),
        MOSS_BLOCK(Blocks.MOSS_BLOCK), DEEPSLATE(Blocks.DEEPSLATE), COBBLED_DEEPSLATE(Blocks.COBBLED_DEEPSLATE), TUFF(Blocks.TUFF),
        BLACKSTONE(Blocks.BLACKSTONE), POLISHED_BLACKSTONE(Blocks.POLISHED_BLACKSTONE), BASALT(Blocks.BASALT), NETHERRACK(Blocks.NETHERRACK),
        OBSIDIAN(Blocks.OBSIDIAN), RED_NETHER_BRICKS(Blocks.RED_NETHER_BRICKS), CALCITE(Blocks.CALCITE), POLISHED_DIORITE(Blocks.POLISHED_DIORITE);

        public final Block mimic;

        Look(Block mimic) {
            this.mimic = mimic;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The look for a block of the cave's wall, or null if no look passes for it. */
        public static Look of(BlockState state) {
            for (Look l : values()) if (state.is(l.mimic)) return l;
            return null;
        }
    }

    public static final EnumProperty<Look> LOOK = EnumProperty.create("look", Look.class);

    public HiddenSealBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LOOK, Look.STONE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOOK);
    }

    /** A hit: a knock. */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) HiddenRooms.knock(sp, pos);
    }

    /** An arrow or a thrown thing knocks too. */
    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide && projectile.getOwner() instanceof ServerPlayer sp) HiddenRooms.knock(sp, hit.getBlockPos());
    }

    /** Broken outright (creative): the whole wall comes down. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer sp) HiddenRooms.openAt(sp.serverLevel(), pos, sp);
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** A faint violet mote now and then, out of a face that opens onto the cave. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(14) != 0) return;
        Direction d = Direction.getRandom(random);
        BlockPos out = pos.relative(d);
        if (!level.getBlockState(out).isAir()) return;
        double x = pos.getX() + 0.5 + d.getStepX() * 0.55 + (d.getStepX() == 0 ? random.nextDouble() - 0.5 : 0) * 0.9;
        double y = pos.getY() + 0.5 + d.getStepY() * 0.55 + (d.getStepY() == 0 ? random.nextDouble() - 0.5 : 0) * 0.9;
        double z = pos.getZ() + 0.5 + d.getStepZ() * 0.55 + (d.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0) * 0.9;
        level.addParticle(new DustParticleOptions(new Vector3f(0.72f, 0.55f, 1f), 0.55f), x, y, z,
                d.getStepX() * 0.01, 0.012, d.getStepZ() * 0.01);
    }
}
