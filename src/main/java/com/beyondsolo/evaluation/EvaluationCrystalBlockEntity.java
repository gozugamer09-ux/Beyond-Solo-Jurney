package com.beyondsolo.evaluation;

import com.beyondsolo.registry.ModBlockEntities;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds the crystal's animation state. Nothing is saved: the server announces state changes with
 * block events and each client animates on its own.
 */
public class EvaluationCrystalBlockEntity extends BlockEntity {
    /** Floating quietly. */
    public static final int IDLE = 0;
    /** Scanning a player: spinning up, beam on. */
    public static final int SCANNING = 1;
    /** Deciding the rank: spinning at full speed. */
    public static final int REVEALING = 2;
    /** Rank decided: the crystal and its beam take the rank's colour. The event parameter is the rank. */
    public static final int LOCKED = 3;

    public static final int IDLE_COLOR = 0x5FD0FF;
    /** How high the middle of the floating crystal sits above the bottom of its block. */
    public static final float GEM_HEIGHT = 0.90f;

    private int state = IDLE;
    private int rank;

    // client animation, advanced once per tick and interpolated by the renderer
    private float spin, lastSpin;
    private float spinSpeed = 1.2f;
    private float activity, lastActivity;
    private float flash, lastFlash;
    private float tint, lastTint;

    public EvaluationCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EVALUATION_CRYSTAL.get(), pos, state);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id < IDLE || id > LOCKED) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) {
            if (id == LOCKED) {
                rank = Mth.clamp(param, 0, PlayerRank.values().length - 1);
                flash = 1f;
            }
            state = id;
        }
        return true;
    }

    public static void clientTick(Level level, BlockPos pos, BlockState blockState, EvaluationCrystalBlockEntity be) {
        be.lastSpin = be.spin;
        be.lastActivity = be.activity;
        be.lastFlash = be.flash;
        be.lastTint = be.tint;
        float targetSpeed = switch (be.state) {
            case SCANNING -> 9f;
            case REVEALING -> 22f;
            case LOCKED -> 4f;
            default -> 1.2f;
        };
        be.spinSpeed += (targetSpeed - be.spinSpeed) * (be.state == LOCKED ? 0.12f : 0.035f);
        be.spin += be.spinSpeed;
        be.activity += ((be.state == IDLE ? 0f : 1f) - be.activity) * 0.09f;
        be.tint += ((be.state == LOCKED ? 1f : 0f) - be.tint) * 0.2f;
        be.flash *= 0.9f;
    }

    /** Rotation in degrees. */
    public float spin(float partialTick) {
        return Mth.lerp(partialTick, lastSpin, spin);
    }

    /** 0 while idle, 1 while an evaluation is running. */
    public float activity(float partialTick) {
        return Mth.lerp(partialTick, lastActivity, activity);
    }

    /** Jumps to 1 when the rank is decided and fades out. */
    public float flash(float partialTick) {
        return Mth.lerp(partialTick, lastFlash, flash);
    }

    /** The crystal's colour right now (0xRRGGBB): cyan, or the decided rank's colour. */
    public int color(float partialTick) {
        float t = Mth.lerp(partialTick, lastTint, tint);
        int to = PlayerRank.values()[rank].color();
        int r = Math.round(Mth.lerp(t, (IDLE_COLOR >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (IDLE_COLOR >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(t, IDLE_COLOR & 0xFF, to & 0xFF));
        return (r << 16) | (g << 8) | b;
    }
}
