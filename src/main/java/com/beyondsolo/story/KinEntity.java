package com.beyondsolo.story;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The hunter's mother ({@link Kin}). She lies in the Long Sleep in a bed of the hunter's village until the Elixir of
 * Waking reaches her ({@link LongSleep}); then she sits up in it, and from the next day on she is up and about beside
 * it. One per hunter: she knows whose mother she is, and to everyone else she is that hunter's mother. Nothing in the
 * world harms her, and nothing moves her.
 */
public class KinEntity extends PathfinderMob {
    /** What she is doing: asleep, asleep with her eyes open (the moment she wakes), sitting up in bed, up and about. */
    public static final byte SLEEPING = 0, STIRRING = 1, SITTING = 2, STANDING = 3;
    /** Her looks (skins from {@code tools/gen_story_assets.py}). */
    public static final int LOOKS = 3;

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(KinEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(KinEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(KinEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(KinEntity.class, EntityDataSerializers.STRING);
    /** Where she is sitting while she sits up (the facing of the bed, packed into the block position's direction). */
    private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(KinEntity.class, EntityDataSerializers.INT);

    /** The head of her bed. */
    @Nullable private BlockPos bed;

    public KinEntity(EntityType<? extends KinEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 12);
    }

    // ------------------------------------------------------------------ who she is

    public byte state() { return entityData.get(STATE); }
    public int look() { return entityData.get(LOOK); }
    public @Nullable UUID owner() { return entityData.get(OWNER).orElse(null); }
    public String ownerName() { return entityData.get(OWNER_NAME); }
    public @Nullable BlockPos bed() { return bed; }

    /** The way her bed points (from its foot to its head). */
    public Direction bedFacing() {
        return Direction.from2DDataValue(entityData.get(FACING));
    }

    public boolean isOwnedBy(Player p) {
        return p.getUUID().equals(owner());
    }

    void setup(ServerPlayer owner, int look, BlockPos bed) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(OWNER_NAME, owner.getGameProfile().getName());
        entityData.set(LOOK, look);
        this.bed = bed.immutable();
        BlockState s = level().getBlockState(bed);
        if (s.getBlock() instanceof BedBlock) entityData.set(FACING, s.getValue(BedBlock.FACING).get2DDataValue());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, SLEEPING);
        builder.define(LOOK, 0);
        builder.define(OWNER, Optional.empty());
        builder.define(OWNER_NAME, "");
        builder.define(FACING, Direction.NORTH.get2DDataValue());
    }

    // ------------------------------------------------------------------ where she is

    /**
     * Puts her in a state: in her bed (asleep, or the moment her eyes open), sitting up in it, or standing beside it.
     * Anywhere she can't be (no bed any more) she stands.
     */
    public void setState(byte state) {
        byte was = state();
        entityData.set(STATE, state);
        // getting up (out of bed, or off it): she steps beside it; already up, she is only fetched back if far off
        place(was != STANDING);
    }

    /** Puts her where her state says, now ({@code settle}: standing, she goes to her spot beside the bed however near). */
    void place(boolean settle) {
        byte state = state();
        BlockState s = bed == null ? null : level().getBlockState(bed);
        boolean hasBed = s != null && s.getBlock() instanceof BedBlock;
        if (hasBed) entityData.set(FACING, s.getValue(BedBlock.FACING).get2DDataValue());
        if ((state == SLEEPING || state == STIRRING) && hasBed) {
            if (!isSleeping() || !bed.equals(getSleepingPos().orElse(null))) startSleeping(bed);
            setNoGravity(false);
            return;
        }
        if (isSleeping()) {
            // not the vanilla stand-up: it would move her off somewhere
            clearSleepingPos();
            setPose(Pose.STANDING);
        }
        if (state == SITTING && hasBed) {
            Direction f = bedFacing();
            // her back against the head of the bed, her legs along it, sitting on the mattress (the sitting model's
            // seat is 10 pixels over her feet, the mattress 9 over the floor)
            Vec3 at = Vec3.atBottomCenterOf(bed).add(f.getStepX() * 0.2, -0.0625, f.getStepZ() * 0.2);
            moveTo(at.x, at.y, at.z, f.getOpposite().toYRot(), 0);
            setYHeadRot(getYRot());
            yBodyRot = getYRot();
            setNoGravity(true);
            setDeltaMovement(Vec3.ZERO);
            occupy(s, true);
            return;
        }
        if (hasBed) occupy(s, false);
        setNoGravity(false);
        BlockPos spot = hasBed ? Kin.besideBed(level(), bed) : null;
        if (spot != null && (settle || distanceToSqr(Vec3.atBottomCenterOf(spot)) > 1.5 * 1.5)) {
            Vec3 bedCenter = Vec3.atCenterOf(bed);
            Vec3 at = Vec3.atBottomCenterOf(spot);
            float yaw = (float) Math.toDegrees(Math.atan2(-(bedCenter.x - at.x), bedCenter.z - at.z));
            moveTo(at.x, at.y, at.z, yaw, 0);
            setYHeadRot(yaw);
            yBodyRot = yaw;
        }
    }

    /** Gives her another bed (the old one is gone) and puts her in or beside it. */
    void moveToBed(BlockPos bed) {
        if (isSleeping()) {
            clearSleepingPos();
            setPose(Pose.STANDING);
        }
        this.bed = bed.immutable();
        place(true);
    }

    private void occupy(BlockState s, boolean occupied) {
        if (bed != null && s.getValue(BedBlock.OCCUPIED) != occupied) level().setBlock(bed, s.setValue(BedBlock.OCCUPIED, occupied), 3);
    }

    /** Gone for good (not just unloaded with her chunk): her bed is free again. */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && bed != null) {
            BlockState s = level().getBlockState(bed);
            if (s.getBlock() instanceof BedBlock) occupy(s, false);
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ what she does

    /** Awake enough to look about (not lying in bed). */
    public boolean up() {
        byte s = state();
        return s == SITTING || s == STANDING;
    }

    @Override
    protected void registerGoals() {
        // no looking about in her sleep: the head stays on the pillow
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 6f, 0.7f) {
            @Override
            public boolean canUse() {
                return up() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return up() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(2, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return up() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return up() && super.canContinueToUse();
            }
        });
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!up()) {
            setYHeadRot(yBodyRot);
            yHeadRotO = yBodyRot;
            setXRot(0);
        }
        if (state() == SITTING) {
            // she sits facing the foot of the bed; only her head turns to look at someone
            float yaw = bedFacing().getOpposite().toYRot();
            setYRot(yaw);
            yBodyRot = yaw;
            yBodyRotO = yaw;
            setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer p) Kin.interact(p, this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || tickCount % 40 != 17) return;
        ServerLevel level = (ServerLevel) level();
        UUID owner = owner();
        if (owner == null) {
            discard();
            return;
        }
        // two of her (a chunk loading late, a respawn, a wiped story): the one her hunter's story names stays
        ServerPlayer hunter = level.getServer().getPlayerList().getPlayer(owner);
        if (hunter != null && !getUUID().equals(Kin.record(hunter).kin())) {
            discard();
            return;
        }
        // keep to her bed
        byte state = state();
        boolean inBed = (state == SLEEPING || state == STIRRING) && isSleeping();
        boolean sitting = state == SITTING && bed != null && distanceToSqr(Vec3.atBottomCenterOf(bed)) < 1.2;
        if (!inBed && !sitting && state != STANDING) place(true);
        if (state == STANDING && bed != null && distanceToSqr(Vec3.atCenterOf(bed)) > 6 * 6) place(true);
    }

    // ------------------------------------------------------------------ nothing harms her

    /** Nothing in the world harms her (commands that kill outright still do). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public void push(double x, double y, double z) {}

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (bed != null) tag.put("Bed", NbtUtils.writeBlockPos(bed));
        tag.putByte("State", state());
        tag.putInt("Look", look());
        UUID owner = owner();
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName());
        tag.putInt("Facing", entityData.get(FACING));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        bed = NbtUtils.readBlockPos(tag, "Bed").orElse(null);
        entityData.set(STATE, tag.getByte("State"));
        entityData.set(LOOK, tag.getInt("Look"));
        entityData.set(OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        entityData.set(OWNER_NAME, tag.getString("OwnerName"));
        entityData.set(FACING, tag.getInt("Facing"));
        setInvulnerable(true);
    }
}
