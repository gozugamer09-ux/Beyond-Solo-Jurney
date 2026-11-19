package com.beyondsolo.association;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Someone the association keeps at an Evaluation Zone ({@link AssociationPosts}): its officer, who runs the office
 * (contracts, permits, the rank exam, standing), or the gate broker who hangs about it (gates for sale, scouting).
 * Shaped like a person, in their own clothes; they stand at their post, turn to whoever comes near and can't be hurt.
 * Which of the two it is comes from its entity type.
 */
public class AssociationNpc extends PathfinderMob {
    public enum Role { OFFICER, BROKER }

    /** Officers' surnames ("Officer Halden"). */
    static final List<String> OFFICERS = List.of("Halden", "Seo", "Varga", "Lindqvist", "Park", "Esposito", "Castell", "Ibarra",
            "Noor", "Takeda", "Brennan", "Kowal", "Ahn", "Moreau", "Yoon", "Adeyemi", "Sato", "Reyes", "Novak", "Hale");
    /** Brokers' names ("Silas Venn, gate broker"). */
    static final List<String> BROKERS = List.of("Silas Venn", "Mags Wren", "Old Teodor", "Quill", "Ash Corran", "Dace Morrow",
            "Hollis Crane", "Ruo Jin", "Pell Ardent", "Vesna Kroll", "Tamsin Rook", "Jory Flint");
    /** Looks: officers and brokers each have a few. */
    public static final int OFFICER_LOOKS = 3, BROKER_LOOKS = 2;

    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(AssociationNpc.class, EntityDataSerializers.INT);
    /** The crystal it keeps a post at, or null (one placed by a command). */
    @Nullable private BlockPos post;

    public AssociationNpc(EntityType<? extends AssociationNpc> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 16);
    }

    public Role role() {
        return getType() == AssociationModule.BROKER.get() ? Role.BROKER : Role.OFFICER;
    }

    public int look() {
        return entityData.get(LOOK);
    }

    public void setLook(int look) {
        entityData.set(LOOK, look);
    }

    @Nullable
    public BlockPos post() {
        return post;
    }

    /** Gives it a post at {@code crystal} (null: none), a name and a look of its own. */
    public void setup(@Nullable BlockPos crystal, RandomSource r) {
        this.post = crystal == null ? null : crystal.immutable();
        boolean broker = role() == Role.BROKER;
        setLook(r.nextInt(broker ? BROKER_LOOKS : OFFICER_LOOKS));
        List<String> names = broker ? BROKERS : OFFICERS;
        String name = names.get(r.nextInt(names.size()));
        setCustomName(Component.translatable(broker ? "npc.beyondsolo.association.broker_name" : "npc.beyondsolo.association.officer_name", name));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LOOK, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8f, 0.6f));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer p) {
            getLookControl().setLookAt(p, 30f, 30f);
            if (role() == Role.BROKER) Broker.talk(p, this);
            else Association.talk(p, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || tickCount % 100 != 37) return;
        ServerLevel level = (ServerLevel) level();
        if (post == null) return;
        // two at one post (chunks loading late): the one that came last goes
        for (AssociationNpc other : level.getEntitiesOfClass(AssociationNpc.class, new AABB(post).inflate(32),
                e -> e != this && e.isAlive() && e.getType() == getType() && post.equals(e.post()))) {
            if (other.getUUID().compareTo(getUUID()) < 0) {
                discard();
                return;
            }
        }
        // blown away from it: back to the post
        if (distanceToSqr(Vec3.atBottomCenterOf(post)) > 14 * 14) {
            BlockPos spot = AssociationPosts.spot(level, post, role());
            if (spot != null) moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, getYRot(), getXRot());
        }
    }

    /** Nothing in the world harms them (commands that kill outright still do). */
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
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (post != null) tag.put("Post", NbtUtils.writeBlockPos(post));
        tag.putInt("Look", look());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        post = NbtUtils.readBlockPos(tag, "Post").orElse(null);
        setLook(tag.getInt("Look"));
        setInvulnerable(true);
    }
}
