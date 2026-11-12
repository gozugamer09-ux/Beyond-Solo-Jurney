package com.beyondsolo.evaluation;

import com.beyondsolo.client.render.WorldEffects;
import com.beyondsolo.registry.ModBlockEntities;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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
    /** Deciding the class: trails of the class's colour wind around the crystal. The event parameter is the class. */
    public static final int CHANNELING = 4;

    public static final int IDLE_COLOR = 0x5FD0FF;
    /** How high the middle of the floating crystal sits above the bottom of its block. */
    public static final float GEM_HEIGHT = 0.90f;

    private int state = IDLE;
    private int rank;
    private int playerClass;

    // client animation, advanced once per tick and interpolated by the renderer
    private float spin, lastSpin;
    private float spinSpeed = 1.2f;
    private float activity, lastActivity;
    private float flash, lastFlash;
    private float tint, lastTint;
    private float accent, lastAccent;
    private float trail, lastTrail;
    private float gather, lastGather;

    public EvaluationCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EVALUATION_CRYSTAL.get(), pos, state);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id < IDLE || id > CHANNELING) return super.triggerEvent(id, param);
        if (level != null && level.isClientSide) {
            if (id == LOCKED) {
                // sent twice in an evaluation: when the rank is decided and again when the class is
                boolean rankDecided = state != CHANNELING;
                rank = Mth.clamp(param, 0, PlayerRank.values().length - 1);
                flash = 1f;
                if (rankDecided && PlayerRank.values()[rank].tier() >= 5) {
                    // the highest rank: the sky answers
                    WorldEffects.lightning(level, Vec3.atBottomCenterOf(worldPosition.above()));
                }
            } else if (id == CHANNELING) {
                playerClass = Mth.clamp(param, 0, PlayerClass.values().length - 1);
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
        be.lastAccent = be.accent;
        be.lastTrail = be.trail;
        be.lastGather = be.gather;
        float targetSpeed = switch (be.state) {
            case SCANNING -> 9f;
            case REVEALING -> 22f;
            case LOCKED -> 4f;
            case CHANNELING -> 11f;
            default -> 1.2f;
        };
        be.spinSpeed += (targetSpeed - be.spinSpeed) * (be.state == LOCKED ? 0.12f : 0.035f);
        be.spin += be.spinSpeed;
        be.activity += ((be.state == IDLE ? 0f : 1f) - be.activity) * 0.09f;
        be.tint += ((be.state == LOCKED || be.state == CHANNELING ? 1f : 0f) - be.tint) * 0.2f;
        be.accent += ((be.state == CHANNELING ? 1f : 0f) - be.accent) * 0.15f;
        be.trail += ((be.state == SCANNING || be.state == CHANNELING ? 1f : 0f) - be.trail) * 0.15f;
        be.gather += ((be.state == REVEALING ? 1f : 0f) - be.gather) * 0.15f;
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

    /** 1 while trails of light wind around the crystal (scanning a player, deciding their class), otherwise 0. */
    public float trails(float partialTick) {
        return Mth.lerp(partialTick, lastTrail, trail);
    }

    /** 1 while the crystal draws light in from all around (deciding the rank), otherwise 0. */
    public float gather(float partialTick) {
        return Mth.lerp(partialTick, lastGather, gather);
    }

    /** The colour of those trails (0xRRGGBB): the crystal's own colour, or the class's while it is being decided. */
    public int trailColor(float partialTick) {
        float t = Mth.lerp(partialTick, lastAccent, accent);
        return blend(color(partialTick), PlayerClass.values()[playerClass].color(), t);
    }

    /** The crystal's colour right now (0xRRGGBB): cyan, or the decided rank's colour. */
    public int color(float partialTick) {
        return blend(IDLE_COLOR, PlayerRank.values()[rank].color(), Mth.lerp(partialTick, lastTint, tint));
    }

    private static int blend(int from, int to, float t) {
        int r = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return (r << 16) | (g << 8) | b;
    }
}
