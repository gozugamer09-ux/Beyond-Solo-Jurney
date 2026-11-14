package com.beyondsolo.npc;

import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * The traveller of an escort request. Follows the player who took the request, keeps a few blocks back, catches up
 * (teleports, like a pet) when left far behind, and is hunted by monsters. When it reaches the marked spot with the
 * player close by, the request is done; if it dies, the request fails.
 */
public class TravelerEntity extends PathfinderMob {
    private static final int ARRIVE_RADIUS = 6;
    @Nullable private UUID owner;
    private String token = "";
    private BlockPos destination = BlockPos.ZERO;
    /** Ticks since arriving; it leaves (fades out) after a short goodbye. -1 = still travelling. */
    private int arrived = -1;

    public TravelerEntity(EntityType<? extends TravelerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.FOLLOW_RANGE, 48);
    }

    public void setup(ServerPlayer owner, String token, BlockPos destination, String name) {
        this.owner = owner.getUUID();
        this.token = token;
        this.destination = destination;
        setCustomName(Component.literal(name));
    }

    @Nullable public UUID owner() { return owner; }
    public String token() { return token; }
    public BlockPos destination() { return destination; }
    public boolean hasArrived() { return arrived >= 0; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new PanicGoal(this, 1.3));
        goalSelector.addGoal(2, new FollowOwnerGoal());
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    @Nullable
    private ServerPlayer ownerPlayer() {
        if (owner == null || !(level() instanceof ServerLevel level)) return null;
        return NpcService.player(level.getServer(), owner);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (arrived >= 0) {
            arrived++;
            getNavigation().stop();
            if (arrived == 60) ((ServerLevel) level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.2, getZ(), 10, 0.4, 0.5, 0.4, 0);
            if (arrived > 100) {
                ((ServerLevel) level()).sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.03);
                discard();
            }
            return;
        }
        if (tickCount % 10 != 0) return;
        ServerPlayer p = ownerPlayer();
        if (p == null) return; // owner offline: wait here
        Quest quest = quest(p);
        if (quest == null) {
            // the request ended some other way (abandoned, out of time)
            ((ServerLevel) level()).sendParticles(ParticleTypes.POOF, getX(), getY() + 0.8, getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            discard();
            return;
        }
        boolean here = position().closerThan(Vec3.atBottomCenterOf(destination), ARRIVE_RADIUS)
                && p.position().closerThan(position(), 16);
        if (here) {
            arrived = 0;
            playSound(SoundEvents.VILLAGER_CELEBRATE, 1f, 1f);
            QuestEngine.progress(p, NpcQuests.escortKey(quest.extra()), 1);
        }
    }

    /** The owner's escort quest this traveller belongs to, or null. */
    @Nullable
    public Quest quest(ServerPlayer p) {
        for (Quest q : QuestEngine.data(p).active()) {
            if (q.template().equals(NpcQuestKind.ESCORT.template()) && token.equals(q.extra().getString("token"))) return q;
        }
        return null;
    }

    /** Only real danger counts: getting stuck in a wall, falling, drowning or being crowded just moves them to safety. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING)
                || source.is(net.minecraft.world.damagesource.DamageTypes.DROWN) || source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            if (!level().isClientSide) moveToSafety(blockPosition());
            return false;
        }
        return super.hurt(source, amount);
    }

    /** Puts the traveller on a free spot (solid ground, two blocks of air) near {@code around}. Returns true if it found one. */
    public boolean moveToSafety(BlockPos around) {
        for (int i = 0; i < 24; i++) {
            int r = 1 + i / 6;
            BlockPos t = around.offset(random.nextInt(2 * r + 1) - r, random.nextInt(5) - 2, random.nextInt(2 * r + 1) - r);
            if (level().getBlockState(t.below()).isSolid() && level().getBlockState(t).getCollisionShape(level(), t).isEmpty()
                    && level().getBlockState(t.above()).getCollisionShape(level(), t.above()).isEmpty() && level().getFluidState(t).isEmpty()) {
                moveTo(t.getX() + 0.5, t.getY(), t.getZ() + 0.5, getYRot(), getXRot());
                getNavigation().stop();
                return true;
            }
        }
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level().isClientSide || arrived >= 0) return;
        ServerPlayer p = ownerPlayer();
        if (p == null) return;
        Quest q = quest(p);
        if (q != null) QuestEngine.fail(p, q, QuestEvent.FailReason.LOST);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("Token", token);
        tag.put("Destination", NbtUtils.writeBlockPos(destination));
        tag.putInt("Arrived", arrived);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        token = tag.getString("Token");
        destination = NbtUtils.readBlockPos(tag, "Destination").orElse(BlockPos.ZERO);
        arrived = tag.contains("Arrived") ? tag.getInt("Arrived") : -1;
    }

    /** Walks after the owner, keeping 3 to 4 blocks back; jumps to them when more than 24 blocks behind. */
    private final class FollowOwnerGoal extends Goal {
        private int repath;

        FollowOwnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            ServerPlayer p = ownerPlayer();
            return arrived < 0 && p != null && p.level() == level() && !p.isSpectator() && distanceToSqr(p) > 3.5 * 3.5;
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer p = ownerPlayer();
            return arrived < 0 && p != null && p.level() == level() && distanceToSqr(p) > 2.5 * 2.5;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            ServerPlayer p = ownerPlayer();
            if (p == null) return;
            getLookControl().setLookAt(p, 10f, getMaxHeadXRot());
            if (--repath > 0) return;
            repath = 10;
            double d = distanceToSqr(p);
            if (d > 24 * 24) {
                catchUp(p);
                return;
            }
            getNavigation().moveTo(p, d > 10 * 10 ? 1.25 : 1.0);
        }

        private void catchUp(ServerPlayer p) {
            if (moveToSafety(p.blockPosition().offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2))) {
                ((ServerLevel) level()).sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            }
        }
    }
}
