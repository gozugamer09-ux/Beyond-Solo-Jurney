package com.beyondsolo.story;

import com.beyondsolo.guild.GuildHunterEntity;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.skill.SkillFx;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.util.OnlinePlayers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The hunter's father ({@link MainStory}): a hunter who walked into a gate ten years ago and came back as the light's
 * vessel. He comes down in a pillar of light when the monarchs come for his child, and fights at their side with his
 * fists, every blow a burst of gold light and every fourth one a wave of it. The light holds him up: he staggers but
 * never falls, until the story's end. One per hunter, and only while the story wants him ({@link MainFights}); to
 * everyone else he is that hunter's father. Never saved with the world.
 */
public class FatherEntity extends GuildHunterEntity {
    /** What he is doing: fighting at their side, standing still to talk, down on one knee, rising into the light. */
    public static final byte FIGHTING = 0, STILL = 1, KNEELING = 2, RISING = 3;
    /** His looks (skins from {@code tools/gen_story_assets.py}). */
    public static final int LOOKS = 3;
    /** The light he is made of (his blows, his name plate). */
    public static final int LIGHT = 0xFFE7A0;
    /** How fast he rises into the sky (blocks a tick). */
    static final double RISE = 0.035;
    /** The share of his health below which the light holds him up. */
    static final float FLOOR = 0.3f;
    /** How much stronger than a hunter of the same level he is (a first guess for the balancing pass). */
    static final double HEALTH_SCALE = 3.0, POWER_SCALE = 1.5;

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(FatherEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> LOOK = SynchedEntityData.defineId(FatherEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(FatherEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> OWNER_NAME = SynchedEntityData.defineId(FatherEntity.class, EntityDataSerializers.STRING);

    private int blows;
    private int quiet;

    public FatherEntity(EntityType<? extends FatherEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STATE, FIGHTING);
        b.define(LOOK, 0);
        b.define(OWNER, Optional.empty());
        b.define(OWNER_NAME, "");
    }

    // ------------------------------------------------------------------ who he is

    public byte state() { return entityData.get(STATE); }
    public int look() { return entityData.get(LOOK); }
    public @Nullable UUID owner() { return entityData.get(OWNER).orElse(null); }
    public String ownerName() { return entityData.get(OWNER_NAME); }

    public boolean isOwnedBy(Player p) {
        return p.getUUID().equals(owner());
    }

    /** Makes him {@code owner}'s father: his looks and a strength to match theirs. */
    void setup(ServerPlayer owner, int look) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(OWNER_NAME, owner.getGameProfile().getName());
        entityData.set(LOOK, Math.floorMod(look, LOOKS));
        int level = Math.max(1, com.beyondsolo.system.SystemAPI.get(owner).level());
        double[] s = strength(PlayerClass.WARRIOR, level);
        enlist(CAUSE, PlayerClass.WARRIOR, level, s[0] * HEALTH_SCALE, (float) (s[1] * POWER_SCALE));
    }

    /** Puts him in a state (standing still and kneeling stop him where he is). */
    public void setState(byte state) {
        entityData.set(STATE, state);
        setPose(state == KNEELING ? Pose.CROUCHING : Pose.STANDING);
        setNoGravity(state == RISING);
        // still, kneeling or rising: he stays where the scene puts him (his goals rest), facing his child
        setNoAi(state != FIGHTING);
        if (state != FIGHTING) {
            getNavigation().stop();
            setTarget(null);
            setDeltaMovement(Vec3.ZERO);
        }
    }

    /** Turns him (head and body) to look at {@code at}. */
    public void face(Vec3 at) {
        Vec3 d = at.subtract(getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        yRotO = yaw;
        yHeadRotO = yaw;
        yBodyRotO = yaw;
        setXRot(pitch);
    }

    /** What he fights for: his child, while the story wants him at their side. */
    static final Cause CAUSE = new Cause() {
        @Override
        public boolean keeps(GuildHunterEntity hunter) {
            return hunter instanceof FatherEntity f && MainFights.keeps(f);
        }

        @Override
        public @Nullable ServerPlayer leader(GuildHunterEntity hunter) {
            if (!(hunter instanceof FatherEntity f) || !(f.level() instanceof ServerLevel level) || f.owner() == null) return null;
            ServerPlayer p = OnlinePlayers.get(level.getServer(), f.owner());
            return p != null && p.level() == level && p.isAlive() && !p.isSpectator() ? p : null;
        }

        @Override
        public void fell(ServerLevel level, GuildHunterEntity hunter) {
            // only what nothing survives gets here (the void, a command): the story sends him again
        }
    };

    // ------------------------------------------------------------------ fighting

    @Override
    public boolean doHurtTarget(Entity target) {
        if (state() != FIGHTING) return false;
        boolean hit = super.doHurtTarget(target);
        if (!hit || !(target instanceof LivingEntity victim) || !(level() instanceof ServerLevel server)) return hit;
        blows++;
        Vec3 at = victim.position().add(0, Math.min(victim.getBbHeight() * 0.5, 2.5), 0);
        server.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 6, 0.25, 0.25, 0.25, 0.08);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.NEUTRAL, 0.8f, 0.7f + random.nextFloat() * 0.2f);
        if (blows % 4 == 0) wave(server, victim);
        return true;
    }

    /** Every fourth blow: a wave of light out from the one he struck, through every foe near it. */
    private void wave(ServerLevel server, LivingEntity victim) {
        SkillFx.play(this, SkillFxPayload.Kind.NOVA, LIGHT, victim.position().add(0, 0.2, 0), 3.2f, 3);
        server.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.9f, 1.4f);
        for (Mob m : server.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(4.0, 2.0, 4.0), e -> e != victim && canFight(e))) {
            strike(m, power() * 0.6f, true);
        }
    }

    @Override
    public boolean isPushable() {
        return state() == FIGHTING && super.isPushable();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (state() == RISING && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        // the light keeps his body: he takes half of what a hunter would
        return super.hurt(source, source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? amount : amount * 0.5f);
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        quiet = 0;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        float floor = getMaxHealth() * FLOOR;
        if (getHealth() < floor) {
            // he staggers; the light holds him up
            setHealth(floor);
            if (level() instanceof ServerLevel server && tickCount % 10 == 0) {
                server.sendParticles(ParticleTypes.WAX_OFF, getX(), getY() + 1, getZ(), 8, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved()) return;
        // the light mends him while nothing is hurting him
        if (++quiet > 60 && tickCount % 20 == 0 && getHealth() < getMaxHealth()) Balance.healRaw(this, getMaxHealth() * 0.03f);
        if (state() == STILL || state() == KNEELING) {
            ServerPlayer p = CAUSE.leader(this);
            if (p != null) face(p.getEyePosition());
        }
        if (state() == RISING && level() instanceof ServerLevel server) {
            // up into the light (moved by hand: resting, nothing moves him, not even falling)
            setPos(getX(), getY() + RISE, getZ());
            if (tickCount % 2 == 0) server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 3, 0.3, 0.7, 0.3, 0.02);
        }
    }

    /** He goes back into the light: a pillar of it, and he is gone. */
    @Override
    public void leave() {
        if (level() instanceof ServerLevel server) pillar(server, position());
        discard();
    }

    /** A pillar of light where he comes and goes. */
    static void pillar(ServerLevel server, Vec3 at) {
        for (int i = 0; i < 12; i++) {
            server.sendParticles(ParticleTypes.END_ROD, at.x, at.y + i * 0.9, at.z, 3, 0.18, 0.4, 0.18, 0.01);
        }
        server.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 1.2f, 1.3f);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Look", look());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(LOOK, tag.getInt("Look"));
    }
}
