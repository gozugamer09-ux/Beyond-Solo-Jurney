package com.beyondsolo.scouting;

import com.beyondsolo.rival.RivalGuild;
import com.beyondsolo.util.OnlinePlayers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A great guild's scout, come to put an offer to a hunter ({@link Scouting}): a person in a sharp suit with their guild's
 * band on the arm. They walk up to the hunter they came for and keep near them while the offer is on the table, then
 * walk off: once it is answered, once they have waited long enough ({@code scouting.scoutWaitSeconds}), or when the
 * hunter goes too far. Right-clicked, they put the offer (their window). Nothing harms them, and they are never saved:
 * the offer is the hunter's, kept with them.
 */
public class ScoutEntity extends PathfinderMob {
    /** Their looks (skins). */
    public static final int LOOKS = 3;
    /** How near they come to the hunter, and how far off they let them get before following. */
    static final double NEAR = 2.6, FOLLOW = 3.6;
    /** A hunter further off than this is let go (the offer stays on the table). */
    static final double LOSE = 56;

    private static final EntityDataAccessor<Integer> GUILD = SynchedEntityData.defineId(ScoutEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(ScoutEntity.class, EntityDataSerializers.INT);

    @Nullable private UUID hunter;
    @Nullable private UUID offer;
    private String scoutName = "";
    /** Ticks spent by the hunter; when they set off to leave (-1: not leaving); whether they have said hello. */
    private int waited;
    private int leftAt = -1;
    private boolean greeted;

    public ScoutEntity(EntityType<? extends ScoutEntity> type, Level level) {
        super(type, level);
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    /** Sent for {@code hunter} with offer {@code o}: their guild, look and name over their head. */
    void setup(ServerPlayer hunter, ScoutRecord.Offer o) {
        this.hunter = hunter.getUUID();
        this.offer = o.id;
        this.scoutName = o.scout;
        entityData.set(GUILD, o.guild.ordinal());
        entityData.set(LOOK, o.look);
        setCustomName(Component.literal("[").append(o.guild.displayName()).append("] ")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(o.guild.color & 0xFFFFFF)))
                .append(Component.literal(o.scout).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)))));
        setCustomNameVisible(true);
    }

    public RivalGuild guild() {
        return RivalGuild.byIndex(entityData.get(GUILD));
    }

    public int look() {
        return entityData.get(LOOK);
    }

    @Nullable
    public UUID hunter() {
        return hunter;
    }

    @Nullable
    public UUID offer() {
        return offer;
    }

    public String scoutName() {
        return scoutName;
    }

    public boolean leaving() {
        return leftAt >= 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(GUILD, RivalGuild.IRON_BASTION.ordinal());
        builder.define(LOOK, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 10f, 0.9f));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer p) {
            getLookControl().setLookAt(p, 30f, 30f);
            Scouting.talk(p, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel level)) return;
        if (tickCount % 5 != 0) return;
        if (leftAt >= 0) {
            // walking off: gone once out of sight, or after a while
            if (tickCount - leftAt > 20 * 15 || level.getNearestPlayer(this, 28) == null) discard();
            return;
        }
        ServerPlayer p = OnlinePlayers.get(level.getServer(), hunter);
        if (p == null || !p.isAlive() || p.level() != level || distanceToSqr(p) > LOSE * LOSE) {
            leave(p);
            return;
        }
        ScoutRecord.Offer o = offer == null ? null : Scouting.record(p).offer(offer);
        if (o == null) {
            leave(p);
            return;
        }
        double d = distanceTo(p);
        if (d > FOLLOW) {
            if (getNavigation().isDone() || tickCount % 20 == 0) getNavigation().moveTo(p, d > 12 ? 1.15 : 1.0);
        } else {
            if (d < NEAR) getNavigation().stop();
            getLookControl().setLookAt(p, 30f, 30f);
            if (!greeted) {
                greeted = true;
                Scouting.greet(p, this, o);
            }
            waited += 5;
            // the hunter's window open on them: they wait as long as it takes
            if (waited > ScoutingConfig.scoutWaitTicks() && !Scouting.talking(p, this)) {
                Scouting.gaveUp(p, this, o);
                leave(p);
            }
        }
    }

    /** Off they go, away from the hunter. */
    void leave(@Nullable ServerPlayer from) {
        if (leftAt >= 0) return;
        leftAt = tickCount;
        Vec3 away = from == null ? getLookAngle().scale(-1) : position().subtract(from.position());
        Vec3 flat = new Vec3(away.x, 0, away.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : flat.normalize();
        Vec3 to = position().add(flat.scale(24));
        getNavigation().moveTo(to.x, to.y, to.z, 1.0);
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
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
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
}
