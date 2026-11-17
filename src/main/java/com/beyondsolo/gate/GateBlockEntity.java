package com.beyondsolo.gate;

import com.beyondsolo.registry.ModBlockEntities;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * A gate. Knows which {@link GateInstance} it leads into (or, for an exit gate, out of), its rank, and when it
 * opened or started to close, so every client can play the same opening and closing. Walking into the vortex
 * starts the trip through ({@link GateService}). It also carries what clients see of its dungeon break (its phase,
 * when it started to come apart, when it breaks and when it broke), set by {@code breaks.DungeonBreaks}.
 */
public class GateBlockEntity extends BlockEntity implements GeoBlockEntity {
    /** Opening animation length in ticks (the model's "open" plus the vortex growing). */
    public static final int OPEN_TICKS = 44;
    public static final int CLOSE_TICKS = 36;
    /** Height of the middle of the portal above the block. */
    public static final float CENTER_Y = 2.5f;
    public static final float HALF_WIDTH = 1.45f;
    public static final float HALF_HEIGHT = 2.0f;

    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.beyondsolo.gate.idle");
    public static final RawAnimation OPEN = RawAnimation.begin().thenPlay("animation.beyondsolo.gate.open").thenLoop("animation.beyondsolo.gate.idle");
    public static final RawAnimation CLOSE = RawAnimation.begin().thenPlayAndHold("animation.beyondsolo.gate.close");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    @Nullable private UUID gateId;
    private PlayerRank rank = PlayerRank.E;
    private boolean exit;
    /** Game time the gate opened, or -1 if it was already open when the world was made. */
    private long openedAt = -1;
    private long closingAt = -1;
    /** The sealed gate of an awakening trial: pale gold, chained, and it won't let its player walk away. */
    private boolean sealed;
    /** A gate opened with a dungeon key: a lock of light at its heart, and no beam (see key.DungeonKeys). */
    private boolean keyed;
    /** Its dungeon break as clients see it: {@link #HOLDING}, {@link #UNSTABLE}, {@link #IMMINENT} or {@link #BROKEN}. */
    private int breakPhase;
    /** Game time it started to come apart and when it breaks (-1 while it holds), and when it broke (-1 until it has). */
    private long unstableAt = -1, breakAt = -1, brokeAt = -1;

    public static final int HOLDING = 0, UNSTABLE = 1, IMMINENT = 2, BROKEN = 3;

    public GateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GATE.get(), pos, state);
    }

    // ------------------------------------------------------------------ state

    @Nullable
    public UUID gateId() { return gateId; }
    public PlayerRank rank() { return rank; }
    public boolean isExit() { return exit; }
    public long openedAt() { return openedAt; }
    public long closingAt() { return closingAt; }
    public boolean isClosing() { return closingAt >= 0; }
    public boolean isSealed() { return sealed; }
    public boolean isKeyed() { return keyed; }
    public int breakPhase() { return breakPhase; }
    /** Coming apart, its collapse timer running. */
    public boolean isUnstable() { return breakPhase == UNSTABLE || breakPhase == IMMINENT; }
    public boolean isBroken() { return breakPhase == BROKEN; }
    public long breakAt() { return breakAt; }
    public long brokeAt() { return brokeAt; }

    /** 0..1 how far it has come apart: 0 while it holds, 1 at the break (and after). */
    public float instability(float partialTick) {
        if (breakPhase == BROKEN) return 1f;
        if (!isUnstable() || level == null || breakAt <= unstableAt) return 0f;
        float t = level.getGameTime() + partialTick;
        return Mth.clamp((t - unstableAt) / (float) (breakAt - unstableAt), 0f, 1f);
    }

    /**
     * What clients see of its break. Sends an update only when something they would see changed: the phase, the
     * moment it broke, or its time to break by more than a few seconds (it is pushed back a second at a time while
     * someone is inside its cave).
     */
    public void setBreak(int phase, long unstableAt, long breakAt, long brokeAt) {
        boolean show = phase != breakPhase || brokeAt != this.brokeAt || unstableAt != this.unstableAt || Math.abs(breakAt - this.breakAt) > 100;
        boolean save = show || breakAt != this.breakAt;
        this.breakPhase = phase;
        this.unstableAt = unstableAt;
        this.breakAt = breakAt;
        this.brokeAt = brokeAt;
        if (show) changed();
        else if (save) setChanged();
    }

    public void setSealed(boolean sealed) {
        this.sealed = sealed;
        changed();
    }

    public void setKeyed(boolean keyed) {
        this.keyed = keyed;
        changed();
    }

    /** Sets everything at once (world-gen, spawning, the exit gate). */
    public void setup(@Nullable UUID gateId, PlayerRank rank, boolean exit, long openedAt) {
        this.gateId = gateId;
        this.rank = rank;
        this.exit = exit;
        this.openedAt = openedAt;
        this.closingAt = -1;
        changed();
    }

    public void setGateId(UUID id) {
        this.gateId = id;
        changed();
    }

    public void setRank(PlayerRank rank) {
        this.rank = rank;
        changed();
    }

    public void startClosing(long now) {
        if (closingAt >= 0) return;
        closingAt = now;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public Direction facing() {
        return getBlockState().hasProperty(GateBlock.FACING) ? getBlockState().getValue(GateBlock.FACING) : Direction.NORTH;
    }

    /** The middle of the vortex. */
    public Vec3 center() {
        return new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + CENTER_Y, worldPosition.getZ() + 0.5);
    }

    /** The space you have to step into to go through. */
    public AABB portal() {
        Vec3 c = center();
        boolean alongX = facing().getAxis() == Direction.Axis.Z;
        double hx = alongX ? HALF_WIDTH : 0.45, hz = alongX ? 0.45 : HALF_WIDTH;
        return new AABB(c.x - hx, c.y - HALF_HEIGHT, c.z - hz, c.x + hx, c.y + HALF_HEIGHT, c.z + hz);
    }

    /** Where someone stepping out of this gate lands: on the ground two blocks in front of it. */
    public Vec3 front() {
        Direction f = facing();
        return new Vec3(worldPosition.getX() + 0.5 + f.getStepX() * 2.2, worldPosition.getY(), worldPosition.getZ() + 0.5 + f.getStepZ() * 2.2);
    }

    /** 0..1 how far the gate has opened. */
    public float openness(float partialTick) {
        if (level == null) return 1f;
        float t = level.getGameTime() + partialTick;
        float open = openedAt < 0 ? 1f : Mth.clamp((t - openedAt) / OPEN_TICKS, 0f, 1f);
        if (closingAt >= 0) open *= 1f - Mth.clamp((t - closingAt) / CLOSE_TICKS, 0f, 1f);
        return open;
    }

    // ------------------------------------------------------------------ ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, GateBlockEntity gate) {
        if (!(level instanceof ServerLevel server)) return;
        long now = level.getGameTime();
        if (gate.closingAt >= 0) {
            if (now - gate.closingAt >= CLOSE_TICKS) level.removeBlock(pos, false);
            return;
        }
        if (!gate.exit && gate.gateId == null) GateService.register(server, gate);
        if (gate.openedAt >= 0 && now - gate.openedAt < OPEN_TICKS * 0.7f) return;
        if ((now + pos.asLong()) % 2 != 0) return;

        if (!gate.exit && !GateService.stillOpen(server, gate)) return;
        AABB portal = gate.portal();
        for (ServerPlayer p : server.getEntitiesOfClass(ServerPlayer.class, portal.inflate(0.2))) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (gate.exit) GateService.beginExit(p, gate);
            else GateService.beginEnter(p, gate);
        }
        if (!gate.exit && now % 20 == 0) GateService.noticeNearby(server, gate);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, GateBlockEntity gate) {
        // a few motes drawn in towards the vortex; the light itself is drawn by the renderer
        RandomSource r = level.random;
        float open = gate.openness(0);
        if (open > 0.05f && (gate.isUnstable() || gate.isBroken())) leak(level, gate, r);
        if (open <= 0.05f || r.nextInt(3) != 0) return;
        Vec3 c = gate.center();
        boolean alongX = gate.facing().getAxis() == Direction.Axis.Z;
        double a = r.nextDouble() * Math.PI * 2;
        double rad = 2.6 + r.nextDouble() * 1.5;
        double ox = Math.cos(a) * rad * 0.8, oy = Math.sin(a) * rad;
        double side = (r.nextDouble() - 0.5) * 2.5;
        double x = c.x + (alongX ? ox : side), y = c.y + oy, z = c.z + (alongX ? side : ox);
        if (gate.keyed) {
            // a keyed gate draws in runic letters, as a lock does its key (they fly to the given point)
            level.addParticle(ParticleTypes.ENCHANT, c.x, c.y, c.z, x - c.x, y - c.y, z - c.z);
            return;
        }
        level.addParticle(gate.sealed ? ParticleTypes.END_ROD : gate.rank.tier() >= 4 ? ParticleTypes.SMALL_FLAME : ParticleTypes.REVERSE_PORTAL,
                x, y, z, (c.x - x) * 0.05, (c.y - y) * 0.05, (c.z - z) * 0.05);
    }

    /**
     * A gate coming apart leaks: red motes thrown out of the rim instead of drawn in, more of them as it nears the
     * break, and once broken a steady spill and ash rising off it. None of them come near a first-person camera.
     */
    private static void leak(Level level, GateBlockEntity gate, RandomSource r) {
        if (!(level instanceof net.minecraft.client.multiplayer.ClientLevel client)) return;
        float k = gate.instability(0);
        float chance = gate.isBroken() ? 0.7f : 0.12f + 0.5f * k;
        if (r.nextFloat() > chance) return;
        Vec3 c = gate.center();
        boolean alongX = gate.facing().getAxis() == Direction.Axis.Z;
        double a = r.nextDouble() * Math.PI * 2;
        double ox = Math.cos(a) * HALF_WIDTH * 0.95, oy = Math.sin(a) * HALF_HEIGHT * 0.95;
        double x = c.x + (alongX ? ox : 0), y = c.y + oy, z = c.z + (alongX ? 0 : ox);
        double sp = 0.05 + 0.08 * k + (gate.isBroken() ? 0.08 : 0);
        double vx = (alongX ? Math.cos(a) : (r.nextDouble() - 0.5)) * sp, vz = (alongX ? (r.nextDouble() - 0.5) : Math.cos(a)) * sp;
        org.joml.Vector3f red = new org.joml.Vector3f(1f, 0.16f + 0.2f * (1 - k), 0.12f);
        com.beyondsolo.client.render.CameraParticles.add(client, new net.minecraft.core.particles.DustParticleOptions(red, 1.1f + 0.6f * k),
                x, y, z, vx, Math.sin(a) * sp, vz);
        if (r.nextFloat() < 0.25f + 0.4f * k) {
            com.beyondsolo.client.render.CameraParticles.add(client, ParticleTypes.ELECTRIC_SPARK, x, y, z, vx * 2, Math.sin(a) * sp * 2, vz * 2);
        }
        if (gate.isBroken() && r.nextFloat() < 0.35f) {
            com.beyondsolo.client.render.CameraParticles.add(client, ParticleTypes.LARGE_SMOKE, c.x + (r.nextDouble() - 0.5) * 2, c.y + HALF_HEIGHT,
                    c.z + (r.nextDouble() - 0.5) * 2, 0, 0.05, 0);
        }
    }

    // ------------------------------------------------------------------ saving and sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (gateId != null) tag.putUUID("gate", gateId);
        tag.putString("rank", rank.name());
        tag.putBoolean("exit", exit);
        tag.putLong("openedAt", openedAt);
        tag.putLong("closingAt", closingAt);
        tag.putBoolean("sealed", sealed);
        tag.putBoolean("keyed", keyed);
        tag.putInt("breakPhase", breakPhase);
        tag.putLong("unstableAt", unstableAt);
        tag.putLong("breakAt", breakAt);
        tag.putLong("brokeAt", brokeAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gateId = tag.hasUUID("gate") ? tag.getUUID("gate") : null;
        PlayerRank r = PlayerRank.byName(tag.getString("rank"));
        rank = r == null ? PlayerRank.E : r;
        exit = tag.getBoolean("exit");
        openedAt = tag.contains("openedAt") ? tag.getLong("openedAt") : -1;
        closingAt = tag.contains("closingAt") ? tag.getLong("closingAt") : -1;
        sealed = tag.getBoolean("sealed");
        keyed = tag.getBoolean("keyed");
        breakPhase = tag.getInt("breakPhase");
        unstableAt = tag.contains("unstableAt") ? tag.getLong("unstableAt") : -1;
        breakAt = tag.contains("breakAt") ? tag.getLong("breakAt") : -1;
        brokeAt = tag.contains("brokeAt") ? tag.getLong("brokeAt") : -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "gate", 0, state -> {
            if (closingAt >= 0) return state.setAndContinue(CLOSE);
            if (openedAt >= 0 && level != null && level.getGameTime() - openedAt < OPEN_TICKS) return state.setAndContinue(OPEN);
            // keep playing the opening once it has started; it hands over to the idle loop by itself
            if (state.getController().getCurrentRawAnimation() == OPEN) return PlayState.CONTINUE;
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
