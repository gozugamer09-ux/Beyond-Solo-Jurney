package com.beyondsolo.guild;

import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.raid.RaidPylonEntity;
import com.beyondsolo.raid.RaidRole;
import com.beyondsolo.raid.RaidState;
import com.beyondsolo.raid.Raids;
import com.beyondsolo.shadow.ShadowEntity;
import com.beyondsolo.skill.SkillFx;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerClass;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * A guild's hired hunter in a raid: a hunter like any other, in their class's armour with their class's weapon, who
 * comes through the gate with the raiders and fights beside them. Vanguards (guardians) hold the creatures on
 * themselves, strikers cut them down (rangers and mages from a distance), supports (healers) keep the raiders on their
 * feet. They break the boss's pylons with everyone else and run for the light when it calls a cataclysm down.
 *
 * <p>Never hurt by a raider and never hurting one. Never saved with the world: {@link Raids} brings them back next to
 * a raider if the cave was unloaded. One who falls is out of the raid, and hurt for a while ({@link Guilds#hunterFell}).</p>
 */
public class GuildHunterEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> CLASS = SynchedEntityData.defineId(GuildHunterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(GuildHunterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LEVEL = SynchedEntityData.defineId(GuildHunterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(GuildHunterEntity.class, EntityDataSerializers.INT);

    /** Server: whose hunter it is and which raid it fights in. */
    @Nullable private UUID gateId, guildId, hunterId;
    /** Server: what one of its blows deals, and its timers. */
    private float power = 5;
    private int shotCooldown = 20, healCooldown = 40, tauntCooldown = 60, pylonCooldown;
    /** Server: where the raid wants it to stand (the light, during a cataclysm), and until when. */
    @Nullable private Vec3 refuge;
    private long refugeUntil;
    /** Server: what it fights for when it isn't a guild's raid (the Halcyon raid's allies); null: a raid's hunter. */
    @Nullable private Cause cause;

    /**
     * What a hunter fights for besides a guild's raid in a gate ({@link Raids}): who it keeps near, whether it is still
     * wanted, and what happens when it falls. The Halcyon raid's allies from the national rankings fight for one.
     */
    public interface Cause {
        /** Whether it is still wanted in the fight (false: it heads home). */
        boolean keeps(GuildHunterEntity hunter);

        /** The hunter it keeps near, or null. */
        @Nullable
        ServerPlayer leader(GuildHunterEntity hunter);

        /** It fell. */
        void fell(ServerLevel level, GuildHunterEntity hunter);

        /** Left far behind: it steps out of the air beside its leader. */
        default void catchUp(GuildHunterEntity hunter, ServerPlayer leader) {
            Raids.slipTo(hunter, leader);
        }
    }

    public GuildHunterEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.ATTACK_DAMAGE, 1)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(CLASS, 0);
        b.define(SKIN, 0);
        b.define(LEVEL, 1);
        b.define(COLOR, 0x4FA8FF);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new RefugeGoal());
        goalSelector.addGoal(2, new MendGoal());
        goalSelector.addGoal(3, new PylonGoal());
        goalSelector.addGoal(4, new ShootGoal());
        goalSelector.addGoal(5, new MeleeGoal());
        goalSelector.addGoal(6, new FollowRaiderGoal());
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new RaidTargetGoal());
    }

    // ------------------------------------------------------------------ setup

    /** Makes this the body of a guild's hunter in a raid: its looks, gear and strength. */
    public void configure(RaidState.Hired h, UUID gate, int guildColor, String guildTag) {
        this.gateId = gate;
        this.guildId = h.guild;
        this.hunterId = h.hunter;
        entityData.set(CLASS, h.cls.ordinal());
        entityData.set(SKIN, Math.floorMod(h.skin, GuildHunter.SKINS.length));
        entityData.set(LEVEL, h.level);
        entityData.set(COLOR, guildColor & 0xFFFFFF);
        double[] k = roleScale(h.cls);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(20, 20 * Balance.damageAt(h.level) * k[0] * GuildConfig.HUNTER_HEALTH.get()));
        power = (float) Math.max(1, 5 * Balance.healthAt(h.level) * k[1] * GuildConfig.HUNTER_DAMAGE.get());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(h.cls == PlayerClass.ASSASSIN ? 0.36 : h.cls == PlayerClass.GUARDIAN ? 0.3 : 0.33);
        setHealth(getMaxHealth());
        gear(h.cls, Balance.rankFor(h.level));
        setCustomName(Component.literal("[" + guildTag + "] ").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(guildColor & 0xFFFFFF)))
                .append(Component.literal(h.name).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)))));
        setCustomNameVisible(true);
    }

    /** Makes this the body of a hunter fighting for {@code cause} (no gate, no guild raid): its looks, gear and strength. */
    public void serve(Cause cause, RaidState.Hired h, int color, String tag) {
        configure(h, h.hunter, color, tag);
        this.gateId = null;
        this.cause = cause;
    }

    @Nullable
    public Cause cause() {
        return cause;
    }

    /**
     * Makes this one of a story's own hunters, fighting for {@code cause}: no gate, no guild and no gear given here (it
     * brings its own). {@code health} and {@code power} are its health and what one of its blows deals.
     */
    protected void enlist(Cause cause, PlayerClass cls, int level, double health, float power) {
        this.gateId = null;
        this.guildId = null;
        this.hunterId = null;
        this.cause = cause;
        entityData.set(CLASS, cls.ordinal());
        entityData.set(LEVEL, Math.max(1, level));
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(Math.max(20, health));
        this.power = Math.max(1, power);
        setHealth(getMaxHealth());
    }

    /** Its face ({@link GuildHunter#SKINS}) and the colour of its name, for a hunter set up by {@link #enlist}. */
    protected void setLook(int skin, int color) {
        entityData.set(SKIN, Math.floorMod(skin, GuildHunter.SKINS.length));
        entityData.set(COLOR, color & 0xFFFFFF);
    }

    /** What a hunter of {@code cls} at {@code level} has: {health, one blow's damage} (the guilds' own scale). */
    public static double[] strength(PlayerClass cls, int level) {
        double[] k = roleScale(cls);
        return new double[]{Math.max(20, 20 * Balance.damageAt(level) * k[0] * GuildConfig.HUNTER_HEALTH.get()),
                Math.max(1, 5 * Balance.healthAt(level) * k[1] * GuildConfig.HUNTER_DAMAGE.get())};
    }

    /** Health and damage for each class: {health, damage} times a hunter's of the level. */
    static double[] roleScale(PlayerClass cls) {
        return switch (cls) {
            case GUARDIAN -> new double[]{2.4, 0.8};
            case WARRIOR -> new double[]{1.6, 1.25};
            case ASSASSIN -> new double[]{1.2, 1.45};
            case RANGER -> new double[]{1.2, 1.15};
            case MAGE -> new double[]{1.1, 1.3};
            case HEALER -> new double[]{1.4, 0.5};
        };
    }

    /** Its class's armour of the best rank it may wear, and its class's weapon. */
    private void gear(PlayerClass cls, PlayerRank rank) {
        com.beyondsolo.item.ArmorSets.Set best = null;
        for (com.beyondsolo.armory.ArmorCatalog.Def d : com.beyondsolo.armory.ArmorCatalog.ALL) {
            com.beyondsolo.item.ArmorSets.Set s = d.set();
            if (s.playerClass != cls || s.divine() || s.tier < 0 || s.tier > rank.tier()) continue;
            if (best == null || s.tier > best.tier) best = s;
        }
        if (best != null) {
            var pieces = com.beyondsolo.armory.ArmoryModule.piecesOf(best);
            if (pieces != null) {
                for (var piece : pieces) {
                    ItemStack stack = new ItemStack(piece.get());
                    EquipmentSlot slot = getEquipmentSlotForItem(stack);
                    setItemSlot(slot, stack);
                    setDropChance(slot, 0f);
                }
            }
        }
        Item weapon = weapon(cls, rank);
        if (weapon != null) setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
        setDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    @Nullable
    private static Item weapon(PlayerClass cls, PlayerRank rank) {
        int t = rank.tier();
        int i = t <= 0 ? 0 : t <= 2 ? 1 : t <= 3 ? 2 : 3;
        var list = switch (cls) {
            case WARRIOR -> List.of(com.beyondsolo.item.arsenal.Arsenal.WARCHIEF_CLEAVER, com.beyondsolo.item.arsenal.Arsenal.WARCHIEF_CLEAVER,
                    com.beyondsolo.item.arsenal.Arsenal.THUNDERFANG_LONGSWORD, com.beyondsolo.item.arsenal.Arsenal.STORMLORD_LONGSWORD);
            case ASSASSIN -> List.of(com.beyondsolo.item.arsenal.Arsenal.GOBLIN_SHIV, com.beyondsolo.item.arsenal.Arsenal.FROSTWARDEN_DAGGER,
                    com.beyondsolo.item.arsenal.Arsenal.SWARM_KING_TALONS, com.beyondsolo.item.arsenal.Arsenal.NIGHTVEIL_KRIS);
            case MAGE -> List.of(com.beyondsolo.item.arsenal.Arsenal.APPRENTICE_STAFF, com.beyondsolo.item.arsenal.Arsenal.WINTER_BREATH_STAFF,
                    com.beyondsolo.item.arsenal.Arsenal.EMBER_SAGE_STAFF, com.beyondsolo.item.arsenal.Arsenal.STARSEER_STAFF);
            case GUARDIAN -> List.of(com.beyondsolo.item.arsenal.Arsenal.IRONCLAD_WARHAMMER, com.beyondsolo.item.arsenal.Arsenal.IRONCLAD_WARHAMMER,
                    com.beyondsolo.item.arsenal.Arsenal.IRONCLAD_WARHAMMER, com.beyondsolo.item.arsenal.Arsenal.IRONCLAD_WARHAMMER);
            case HEALER -> List.of(com.beyondsolo.item.arsenal.Arsenal.ACOLYTE_MACE, com.beyondsolo.item.arsenal.Arsenal.MENDING_SCEPTER,
                    com.beyondsolo.item.arsenal.Arsenal.LIFEBLOOM_STAFF, com.beyondsolo.item.arsenal.Arsenal.SAINTS_SUNSTAFF);
            case RANGER -> List.of(com.beyondsolo.item.arsenal.Arsenal.HUNTERS_SHORTBOW, com.beyondsolo.item.arsenal.Arsenal.WINDRUNNER_LONGBOW,
                    com.beyondsolo.item.arsenal.Arsenal.SERPENTSTING_BOW, com.beyondsolo.item.arsenal.Arsenal.GALEBREAKER_WARBOW);
        };
        return list.get(Math.min(i, list.size() - 1)).get();
    }

    // ------------------------------------------------------------------ state

    public PlayerClass playerClass() {
        return PlayerClass.values()[Mth.clamp(entityData.get(CLASS), 0, PlayerClass.values().length - 1)];
    }

    public RaidRole role() {
        return RaidRole.forClass(playerClass());
    }

    public int skin() {
        return entityData.get(SKIN);
    }

    public int hunterLevel() {
        return entityData.get(LEVEL);
    }

    public int guildColor() {
        return entityData.get(COLOR);
    }

    /** Fights from a distance (rangers and mages, and healers with nobody to mend). */
    public boolean ranged() {
        PlayerClass c = playerClass();
        return c == PlayerClass.RANGER || c == PlayerClass.MAGE || c == PlayerClass.HEALER;
    }

    @Nullable public UUID gateId() { return gateId; }
    @Nullable public UUID guildId() { return guildId; }
    @Nullable public UUID hunterId() { return hunterId; }

    public float power() {
        return power;
    }

    /** Sends it to stand somewhere until a time (the light during a cataclysm); null lets it go back to the fight. */
    public void seekRefuge(@Nullable Vec3 at, long until) {
        refuge = at;
        refugeUntil = until;
    }

    private boolean inRefuge() {
        return refuge != null && level().getGameTime() < refugeUntil;
    }

    // ------------------------------------------------------------------ fighting

    /** What it may fight: a creature, never a player, another hunter or anyone's pet or shadow. */
    public boolean canFight(@Nullable LivingEntity e) {
        if (e == null || !e.isAlive() || e == this) return false;
        if (e instanceof Player || e instanceof GuildHunterEntity || e instanceof ShadowEntity) return false;
        if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) return false;
        if (e instanceof GateBossEntity boss && (!boss.isAwake() || boss.introTicks() > 0)) return false;
        if (e instanceof com.beyondsolo.worldboss.ColossalBoss boss && (!boss.isAwake() || boss.introTicks() > 0 || boss.leaving())) return false;
        // hired hunters keep out of the light's war
        if (com.beyondsolo.ruler.Rulers.light(e)) return false;
        return e instanceof Enemy && !e.isInvulnerable();
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return canFight(target) && super.canAttack(target);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof Player || other instanceof GuildHunterEntity) return true;
        return super.isAlliedTo(other);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity victim)) return false;
        swing(InteractionHand.MAIN_HAND);
        return strike(victim, power, true);
    }

    /** One of its blows (or bolts): counted for the raid's tally. */
    protected boolean strike(LivingEntity victim, float dmg, boolean knock) {
        boolean hit = victim.hurt(damageSources().mobAttack(this), dmg);
        if (hit && knock) {
            double dx = victim.getX() - getX(), dz = victim.getZ() - getZ();
            double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
            victim.knockback(0.2, -dx / len, -dz / len);
        }
        if (hit) setLastHurtMob(victim);
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity by = source.getEntity();
        // a raider's blow, a fellow hunter's, a pet's or a shadow's: they fight side by side
        if (by instanceof Player || by instanceof GuildHunterEntity || by instanceof ShadowEntity) return false;
        if (by instanceof OwnableEntity o && o.getOwnerUUID() != null) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel server)) return;
        if (cause != null) cause.fell(server, this);
        else Raids.hunterDown(server, this);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (shotCooldown > 0) shotCooldown--;
        if (healCooldown > 0) healCooldown--;
        if (tauntCooldown > 0) tauntCooldown--;
        if (pylonCooldown > 0) pylonCooldown--;
        // no raid to fight in (the cave was cleared and left, the raid failed, the server was restarted): it goes home
        if (tickCount % 20 == 0 && (cause != null ? !cause.keeps(this) : gateId == null || !Raids.keeps(this))) {
            leave();
            return;
        }
        if (role() == RaidRole.VANGUARD && tauntCooldown <= 0 && getTarget() != null) taunt();
    }

    /** It heads home: a puff of light and it is gone. */
    public void leave() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 14, 0.3, 0.6, 0.3, 0.04);
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.5f, 1.4f);
        }
        discard();
    }

    /** A vanguard's war cry: the creatures around it turn on it. */
    private void taunt() {
        tauntCooldown = 160;
        int pulled = 0;
        for (Mob m : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(7, 3, 7), e -> e instanceof Enemy && !(e instanceof GateBossEntity))) {
            if (m.getTarget() != this && canFight(m)) {
                m.setTarget(this);
                pulled++;
            }
        }
        if (pulled > 0) {
            swing(InteractionHand.OFF_HAND);
            SkillFx.play(this, SkillFxPayload.Kind.ROAR, RaidRole.VANGUARD.color, position().add(0, 1.2, 0), 2.6f, 0);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.NEUTRAL, 0.6f, 1.3f);
        }
    }

    /** The raider it keeps close to: the raid's leader if they are in here, else the nearest raider. */
    @Nullable
    ServerPlayer raider() {
        if (!(level() instanceof ServerLevel server)) return null;
        if (cause != null) return cause.leader(this);
        if (gateId == null) return null;
        return Raids.followTarget(server, gateId, this);
    }

    // ------------------------------------------------------------------ saving and sounds

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        // a hunter keeps their gear
    }

    @Override
    protected int getBaseExperienceReward() {
        return 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Class", entityData.get(CLASS));
        tag.putInt("Skin", skin());
        tag.putInt("HunterLevel", hunterLevel());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(CLASS, tag.getInt("Class"));
        entityData.set(SKIN, tag.getInt("Skin"));
        entityData.set(LEVEL, Math.max(1, tag.getInt("HunterLevel")));
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    // ------------------------------------------------------------------ goals

    /** During a cataclysm: run for the light. Nothing else matters. */
    private final class RefugeGoal extends Goal {
        RefugeGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return inRefuge() && refuge.distanceToSqr(position()) > 1.2 * 1.2;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (refuge == null) return;
            if (getNavigation().isDone() || tickCount % 10 == 0) getNavigation().moveTo(refuge.x, refuge.y, refuge.z, 1.6);
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }
    }

    /** A healer mends whoever is hurt worst near it. */
    private final class MendGoal extends Goal {
        @Nullable private LivingEntity patient;

        MendGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (role() != RaidRole.SUPPORT || healCooldown > 0 || inRefuge()) return false;
            patient = hurtWorst();
            return patient != null;
        }

        @Override
        public boolean canContinueToUse() {
            return patient != null && patient.isAlive() && healCooldown <= 0 && !inRefuge();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (patient == null) return;
            getLookControl().setLookAt(patient, 30, 30);
            if (distanceToSqr(patient) > 7 * 7) {
                getNavigation().moveTo(patient, 1.3);
                return;
            }
            getNavigation().stop();
            healCooldown = 60;
            swing(InteractionHand.MAIN_HAND);
            Balance.healRaw(patient, patient.getMaxHealth() * 0.08f);
            SkillFx.play(patient, SkillFxPayload.Kind.HEAL, RaidRole.SUPPORT.color, patient.position(), 1.2f, 0);
            level().playSound(null, patient.getX(), patient.getY(), patient.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.9f, 1.3f);
            patient = null;
        }

        @Nullable
        private LivingEntity hurtWorst() {
            LivingEntity worst = null;
            float lowest = 0.8f;
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(14, 5, 14),
                    e -> e.isAlive() && (e instanceof ServerPlayer p && !p.isSpectator() || e instanceof GuildHunterEntity))) {
                float share = e.getHealth() / Math.max(1, e.getMaxHealth());
                if (share < lowest) {
                    lowest = share;
                    worst = e;
                }
            }
            return worst;
        }
    }

    /** While the boss's barrier stands: go and break the nearest pylon. */
    private final class PylonGoal extends Goal {
        @Nullable private RaidPylonEntity pylon;

        PylonGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (role() == RaidRole.SUPPORT || inRefuge()) return false;
            pylon = nearestPylon();
            return pylon != null;
        }

        @Override
        public boolean canContinueToUse() {
            return pylon != null && pylon.isAlive() && !inRefuge();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (pylon == null) return;
            getLookControl().setLookAt(pylon, 30, 30);
            double d = distanceToSqr(pylon);
            if (ranged() && d < 14 * 14 && getSensing().hasLineOfSight(pylon)) {
                getNavigation().stop();
                if (pylonCooldown <= 0) {
                    pylonCooldown = 25;
                    swing(InteractionHand.MAIN_HAND);
                    SkillFx.play(GuildHunterEntity.this, playerClass() == PlayerClass.RANGER ? SkillFxPayload.Kind.SHOT : SkillFxPayload.Kind.BOLT,
                            0xFFFFFF, pylon.position().add(0, 1, 0), 0.25f, 0);
                    pylon.hurt(damageSources().mobAttack(GuildHunterEntity.this), power);
                }
                return;
            }
            if (d > 2.6 * 2.6) {
                if (getNavigation().isDone() || tickCount % 10 == 0) getNavigation().moveTo(pylon, 1.35);
                return;
            }
            getNavigation().stop();
            if (pylonCooldown <= 0) {
                pylonCooldown = 20;
                swing(InteractionHand.MAIN_HAND);
                pylon.hurt(damageSources().mobAttack(GuildHunterEntity.this), power);
            }
        }

        @Nullable
        private RaidPylonEntity nearestPylon() {
            RaidPylonEntity best = null;
            double bestD = 40 * 40;
            for (RaidPylonEntity p : level().getEntitiesOfClass(RaidPylonEntity.class, getBoundingBox().inflate(40, 12, 40), Entity::isAlive)) {
                double d = distanceToSqr(p);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            return best;
        }
    }

    /** Rangers and mages (and healers between mends) keep their distance and shoot. */
    private final class ShootGoal extends Goal {
        ShootGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return ranged() && !inRefuge() && getTarget() != null && getTarget().isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30, 30);
            double d = distanceToSqr(t);
            boolean sees = getSensing().hasLineOfSight(t);
            double far = 11 + t.getBbWidth() / 2;
            if (d > far * far || !sees) getNavigation().moveTo(t, 1.15);
            else if (d < 4.5 * 4.5) {
                Vec3 away = position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(4);
                getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.25);
            } else getNavigation().stop();
            if (shotCooldown <= 0 && sees && d < 16 * 16) {
                shotCooldown = playerClass() == PlayerClass.HEALER ? 35 : 25;
                swing(InteractionHand.MAIN_HAND);
                boolean ranger = playerClass() == PlayerClass.RANGER;
                int color = ranger ? 0xE8F2FF : playerClass() == PlayerClass.MAGE ? 0x8FB8FF : 0x9CF0B4;
                SkillFx.play(GuildHunterEntity.this, ranger ? SkillFxPayload.Kind.SHOT : SkillFxPayload.Kind.BOLT, color,
                        t.position().add(0, t.getBbHeight() * 0.55, 0), 0.25f, 0);
                level().playSound(null, getX(), getY(), getZ(), ranger ? SoundEvents.ARROW_SHOOT : SoundEvents.EVOKER_CAST_SPELL,
                        SoundSource.NEUTRAL, 0.7f, ranger ? 1.1f : 1.5f);
                strike(t, power * 0.9f, false);
            }
        }
    }

    private final class MeleeGoal extends MeleeAttackGoal {
        MeleeGoal() {
            super(GuildHunterEntity.this, 1.25, true);
        }

        @Override
        public boolean canUse() {
            return !ranged() && !inRefuge() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !inRefuge() && super.canContinueToUse();
        }
    }

    /** Stays with the raiders; slips through to them when left far behind. */
    private final class FollowRaiderGoal extends Goal {
        private int recompute;

        FollowRaiderGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            ServerPlayer p = raider();
            if (p == null || inRefuge()) return false;
            if (distanceToSqr(p) > 34 * 34) {
                if (cause != null) cause.catchUp(GuildHunterEntity.this, p);
                else Raids.slipTo(GuildHunterEntity.this, p);
                return false;
            }
            if (getTarget() != null && distanceToSqr(p) < 18 * 18) return false;
            return distanceToSqr(p) > 5 * 5;
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer p = raider();
            return p != null && !inRefuge() && !getNavigation().isDone() && distanceToSqr(p) > 3 * 3
                    && (getTarget() == null || distanceToSqr(p) > 18 * 18);
        }

        @Override
        public void start() {
            recompute = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            ServerPlayer p = raider();
            if (p == null) return;
            getLookControl().setLookAt(p, 10, getMaxHeadXRot());
            if (--recompute > 0) return;
            recompute = 10;
            getNavigation().moveTo(p, p.isSprinting() ? 1.5 : 1.25);
        }
    }

    /**
     * What to fight: whatever is hurting a raider or a hunter near it, then whatever a raider near it fights, then the
     * creatures around the raiders.
     */
    private final class RaidTargetGoal extends TargetGoal {
        private int scan;

        RaidTargetGoal() {
            super(GuildHunterEntity.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity current = getTarget();
            if (current != null && current.isAlive() && canFight(current)) return false;
            if (--scan > 0) return false;
            scan = 10;
            LivingEntity pick = pick();
            if (pick == null) return false;
            targetMob = pick;
            return true;
        }

        @Nullable
        private LivingEntity pick() {
            LivingEntity by = getLastHurtByMob();
            if (canFight(by)) return by;
            ServerPlayer p = raider();
            if (p != null) {
                LivingEntity hurtThem = p.getLastHurtByMob();
                if (canFight(hurtThem) && p.tickCount - p.getLastHurtByMobTimestamp() < 200) return hurtThem;
                LivingEntity theirs = p.getLastHurtMob();
                if (canFight(theirs) && p.tickCount - p.getLastHurtMobTimestamp() < 200) return theirs;
            }
            LivingEntity best = null;
            double bestD = 16 * 16;
            for (Mob m : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(16, 6, 16), GuildHunterEntity.this::canFight)) {
                double d = distanceToSqr(m);
                // a creature already at someone's throat comes first
                if (m.getTarget() instanceof Player || m.getTarget() instanceof GuildHunterEntity) d *= 0.5;
                if (d < bestD) {
                    bestD = d;
                    best = m;
                }
            }
            return best;
        }

        @Override
        public void start() {
            setTarget(targetMob);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && canFight(t) && distanceToSqr(t) < 32 * 32;
        }
    }
}
