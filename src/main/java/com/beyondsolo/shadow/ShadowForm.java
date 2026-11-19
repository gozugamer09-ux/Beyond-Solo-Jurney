package com.beyondsolo.shadow;

import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.DungeonBoss;
import com.beyondsolo.gate.GateBossEntity;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * What a shadow is shaped like, taken from the creature it was, whatever creature that was: ours, vanilla's or any
 * other mod's. How it gets about ({@link Movement}: walks, flies, or swims through water and air alike), its size
 * (the creature's own, with its size roll; anything bigger than {@link #MAX_WIDTH} x {@link #MAX_HEIGHT} rises smaller,
 * in proportion, see {@link #fit}), and its look: the creature's own saved data less what only mattered while it lived
 * ({@link #lookOf}), so a skeleton's shadow holds its bow, a boss's shows its kind and a mod's creature keeps its
 * variant. The client builds the stand-in it draws in ink from that ({@link ShadowLook}).
 *
 * <p>Tags {@code shadow/fliers}, {@code shadow/swimmers} and {@code shadow/walkers} settle how a kind moves when the
 * guess from its navigation is wrong; {@code shadow/mounts}, {@code shadow/casters}, {@code shadow/warriors} and
 * {@code shadow/bosses} its role, before the guesses in {@link #roleOf} and {@link #isBoss}.</p>
 *
 * @param move   how it gets about
 * @param width  the creature's width (blocks), its size roll included
 * @param height the creature's height
 * @param eye    the creature's eye height
 * @param scale  the creature's scale (its size roll), given to the stand-in the client draws
 * @param look   the creature's saved data that shapes its look (may be empty: the kind's own look)
 */
public record ShadowForm(Movement move, float width, float height, float eye, float scale, CompoundTag look) {
    /** How a shadow gets about. */
    public enum Movement {
        /** On its feet, like most creatures. */
        WALK,
        /** In the air: it hovers over its master's head and flies at what they fight. */
        FLY,
        /** A creature of the water: it swims through water and air alike, low over the ground. */
        SWIM;

        public String key() { return name().toLowerCase(); }

        public Component displayName() {
            return Component.translatable("shadow_move.beyondsolo." + key());
        }

        /** In the air or the water: no gravity, flies to where it goes. */
        public boolean airborne() { return this != WALK; }

        public static Movement byOrdinal(int i) {
            Movement[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, i))];
        }
    }

    /** The widest a shadow stands, in blocks; a wider creature rises smaller, in proportion (a dragon). */
    public static final float MAX_WIDTH = 4f;
    /** The tallest a shadow stands; a taller creature rises smaller (a titan, the giant). */
    public static final float MAX_HEIGHT = 6f;
    /** A look bigger than this (NBT bytes) keeps only the creature's gear; one bigger still, nothing. */
    public static final int MAX_LOOK_BYTES = 4096;

    /**
     * What a creature saved that has nothing to do with how it looks, or must not come back on a stand-in: where it
     * was and who it was, its health and wounds, its mind, its loot, its owner, whom it trusted or hated, the System's
     * marks, a pack of states only the living have (a zombie turning, a hoglin shaking, a creature knocked down).
     */
    private static final String[] DROP = {
            // every entity
            "Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround", "Invulnerable", "PortalCooldown", "UUID",
            "CustomName", "CustomNameVisible", "Silent", "NoGravity", "Glowing", "TicksFrozen", "HasVisualFire", "Tags",
            "Passengers", "NeoForgeData", "neoforge:attachments", "ForgeData", "ForgeCaps",
            // living things
            "Health", "HurtTime", "HurtByTimestamp", "DeathTime", "AbsorptionAmount", "attributes", "Attributes",
            "active_effects", "ActiveEffects", "FallFlying", "SleepingX", "SleepingY", "SleepingZ", "Brain", "Team",
            // creatures
            "CanPickUpLoot", "PersistenceRequired", "ArmorDropChances", "HandDropChances", "body_armor_drop_chance",
            "leash", "Leash", "NoAI", "DeathLootTable", "DeathLootTableSeed",
            // what some kinds carry about with them
            "Inventory", "Items", "Offers", "Gossips", "Xp", "LastRestock", "LastGossipDecay", "RestocksToday", "Owner",
            "Trusted", "HivePos", "FlowerPos", "listener", "anger", "AngerTime", "AngryAt", "HurtBy", "ConversionTime",
            "DrownedConversionTime", "StrayConversionTime", "InWaterTime", "TimeInOverworld", "CannotHunt",
            // a moment it died in, not what it looks like: asleep, sitting, hanging upside down, curled on a wall,
            // lit to blow, a dragon's last breath
            "Sitting", "Sleeping", "Crouching", "BatFlags", "LastPoseTick", "AttachFace", "ignited", "DragonDeathTime",
            "DragonPhase",
            // ours: knocked down, the hunters who hurt a world boss, its event, home and gate, its raid and ruler
            "Downed", "Dealt", "Event", "Engaged", "Home", "Foreign", "Gate", "Raid", "Ruler", "MoonCalled"};
    /** What a look too big to keep whole keeps: what the creature wore and held. */
    private static final String[] GEAR = {"ArmorItems", "HandItems", "body_armor_item", "SaddleItem", "DecorItem", "LeftHanded"};

    /** Every kind's form as a fresh one of it has it (server side; forgotten when the server stops). */
    private static final Map<EntityType<?>, ShadowForm> PROBED = new HashMap<>();
    /** And what a fresh one of each kind would do as a shadow. */
    private static final Map<EntityType<?>, ShadowRecord.Role> PROBED_ROLE = new HashMap<>();
    /** And the rank its shadow would be counted at as a boss (-1: not a boss). */
    private static final Map<EntityType<?>, Integer> PROBED_BOSS = new HashMap<>();
    /** The form of a shadow whose kind is gone (a mod taken out of the pack): a body of a man's size, on its feet. */
    public static final ShadowForm UNKNOWN = new ShadowForm(Movement.WALK, 0.6f, 1.95f, 1.74f, 1f, new CompoundTag());

    public ShadowForm {
        if (!Float.isFinite(width) || width <= 0) width = 0.6f;
        if (!Float.isFinite(height) || height <= 0) height = 1.8f;
        if (!Float.isFinite(eye) || eye <= 0) eye = height * 0.85f;
        if (!Float.isFinite(scale) || scale <= 0) scale = 1f;
        if (look == null) look = new CompoundTag();
    }

    /** The form of this creature, look and all (at its death). */
    public static ShadowForm of(LivingEntity creature) {
        return of(creature, true);
    }

    private static ShadowForm of(LivingEntity creature, boolean withLook) {
        EntityDimensions d;
        try {
            d = creature.getDimensions(Pose.STANDING);
        } catch (RuntimeException | LinkageError e) {
            d = creature.getType().getDimensions();
        }
        return new ShadowForm(movementOf(creature), d.width(), d.height(), d.eyeHeight(), creature.getScale(),
                withLook ? lookOf(creature) : new CompoundTag());
    }

    /**
     * A kind's form without a creature of it to go by (a shadow given by a command, a shadow raised before forms were
     * kept): a fresh one's, made once and forgotten, with the kind's own look. {@link #UNKNOWN} for a kind that is gone.
     */
    public static ShadowForm probe(ServerLevel level, @Nullable EntityType<?> type) {
        if (type == null) return UNKNOWN;
        ShadowForm known = PROBED.get(type);
        if (known != null) return known;
        ShadowForm form = null;
        ShadowRecord.Role role = null;
        int boss = type.is(ShadowModule.BOSSES) || type.is(Tags.EntityTypes.BOSSES) ? 3 : -1;
        Entity probe = null;
        try {
            probe = type.create(level);
            if (probe instanceof LivingEntity le) {
                form = of(le, false);
                role = roleOf(type, le);
                boss = bossRank(le);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // a kind that can't be made on its own: its registered size will do
        } finally {
            if (probe != null) probe.discard();
        }
        if (form == null) form = ofKind(type);
        PROBED.put(type, form);
        PROBED_ROLE.put(type, role != null ? role : roleOf(type, null));
        PROBED_BOSS.put(type, boss);
        return form;
    }

    /** A kind's registered size, on its feet, with its own look: for a shadow that has no form to go by. */
    public static ShadowForm ofKind(EntityType<?> type) {
        EntityDimensions d = type.getDimensions();
        return new ShadowForm(Movement.WALK, d.width(), d.height(), d.eyeHeight(), 1f, new CompoundTag());
    }

    /** What a shadow of this kind does, going by a fresh one of it ({@link #probe}). */
    public static ShadowRecord.Role probeRole(ServerLevel level, @Nullable EntityType<?> type) {
        if (type == null) return ShadowRecord.Role.WARRIOR;
        probe(level, type);
        return PROBED_ROLE.getOrDefault(type, ShadowRecord.Role.WARRIOR);
    }

    /** The rank a shadow of this kind is counted at as a boss, going by a fresh one of it; -1 for a kind that isn't one. */
    public static int probeBossRank(ServerLevel level, @Nullable EntityType<?> type) {
        if (type == null) return -1;
        probe(level, type);
        return PROBED_BOSS.getOrDefault(type, -1);
    }

    static void forget() {
        PROBED.clear();
        PROBED_ROLE.clear();
        PROBED_BOSS.clear();
    }

    // ================================================================ what kind of creature it was

    /**
     * How a creature gets about: the tags first, then what its body and its navigation say. Whatever it was doing as it
     * died (held up in the air by a skill, a leap) doesn't count: only what it is.
     */
    public static Movement movementOf(LivingEntity e) {
        EntityType<?> t = e.getType();
        if (t.is(ShadowModule.WALKERS)) return Movement.WALK;
        if (t.is(ShadowModule.FLIERS)) return Movement.FLY;
        if (t.is(ShadowModule.SWIMMERS)) return Movement.SWIM;
        // fish, squid, dolphins and every mod's creature built on them
        if (e instanceof WaterAnimal) return Movement.SWIM;
        if (e instanceof Mob m) {
            if (m.getNavigation() instanceof WaterBoundPathNavigation) return Movement.SWIM;
            if (m.getNavigation() instanceof FlyingPathNavigation || m.getMoveControl() instanceof FlyingMoveControl) return Movement.FLY;
        }
        if (e instanceof FlyingMob || e instanceof FlyingAnimal || e instanceof Bat || e instanceof EnderDragon
                || e instanceof WitherBoss || e instanceof Vex) return Movement.FLY;
        // the magus floats over its circle
        if (e instanceof GateBossEntity boss && boss.kind() == BossKind.MAGUS) return Movement.FLY;
        return Movement.WALK;
    }

    /**
     * What a shadow of this kind does: the tags first, then steeds carry, archers and throwers cast (one holding a blade
     * instead, a piglin with its sword, fights up close), the rest fight.
     */
    public static ShadowRecord.Role roleOf(EntityType<?> type, @Nullable LivingEntity creature) {
        if (type.is(ShadowModule.MOUNTS)) return ShadowRecord.Role.MOUNT;
        if (type.is(ShadowModule.CASTERS)) return ShadowRecord.Role.CASTER;
        if (type.is(ShadowModule.WARRIORS)) return ShadowRecord.Role.WARRIOR;
        if (creature instanceof AbstractHorse) return ShadowRecord.Role.MOUNT;
        if (creature instanceof RangedAttackMob) {
            Item held = creature.getMainHandItem().getItem();
            return held instanceof SwordItem || held instanceof AxeItem ? ShadowRecord.Role.WARRIOR : ShadowRecord.Role.CASTER;
        }
        return ShadowRecord.Role.WARRIOR;
    }

    /** A boss: a dungeon's, a world threat (titans, monarchs, rulers, the hive), tag {@code shadow/bosses} or {@code c:bosses}. */
    public static boolean isBoss(LivingEntity e) {
        EntityType<?> t = e.getType();
        return e instanceof DungeonBoss || e instanceof ColossalBoss || t.is(ShadowModule.BOSSES) || t.is(Tags.EntityTypes.BOSSES);
    }

    /**
     * The rank a boss's shadow is counted at (PlayerRank ordinal, -1 for none): a dungeon boss its gate's; the world's
     * great threats S (the hive's queen B: her king is the swarm's greatest child); any other boss B.
     */
    public static int bossRank(LivingEntity e) {
        if (e instanceof DungeonBoss db) return db.rank().ordinal();
        if (e instanceof ColossalBoss c) return c.kind() == WorldBossKind.QUEEN ? 3 : 5;
        return isBoss(e) ? 3 : -1;
    }

    /**
     * The look a creature leaves its shadow: everything it saved but what is listed in {@link #DROP}. A look bigger
     * than {@link #MAX_LOOK_BYTES} keeps only its gear.
     */
    public static CompoundTag lookOf(LivingEntity e) {
        CompoundTag all;
        try {
            all = e.saveWithoutId(new CompoundTag());
        } catch (RuntimeException | LinkageError ex) {
            return new CompoundTag();
        }
        for (String key : DROP) all.remove(key);
        if (all.sizeInBytes() <= MAX_LOOK_BYTES) return all;
        CompoundTag gear = new CompoundTag();
        for (String key : GEAR) {
            if (all.contains(key)) gear.put(key, all.get(key));
        }
        return gear.sizeInBytes() <= MAX_LOOK_BYTES ? gear : new CompoundTag();
    }

    // ================================================================ size

    /** How much smaller than the creature its shadow stands: 1, or less for one bigger than the largest a shadow stands. */
    public float fit() {
        return Math.min(1f, Math.min(MAX_WIDTH / width, MAX_HEIGHT / height));
    }

    /** The shadow's own size (before its rank's growth): the creature's, fitted. */
    public EntityDimensions dimensions() {
        float k = fit();
        return EntityDimensions.scalable(width * k, height * k).withEyeHeight(Mth.clamp(eye, 0.05f, height) * k);
    }

    /** The same form getting about another way (a steed walks, whatever it was). */
    public ShadowForm withMove(Movement m) {
        return m == move ? this : new ShadowForm(m, width, height, eye, scale, look);
    }

    // ================================================================ save

    /** Saved with its record; without the look for what the client is sent with the army (the look can be large). */
    public CompoundTag save(boolean withLook) {
        CompoundTag t = new CompoundTag();
        t.putInt("move", move.ordinal());
        t.putFloat("w", width);
        t.putFloat("h", height);
        t.putFloat("eye", eye);
        t.putFloat("scale", scale);
        if (withLook && !look.isEmpty()) t.put("look", look);
        return t;
    }

    public static ShadowForm load(CompoundTag t) {
        return new ShadowForm(Movement.byOrdinal(t.getInt("move")), t.getFloat("w"), t.getFloat("h"), t.getFloat("eye"),
                t.contains("scale") ? t.getFloat("scale") : 1f, t.getCompound("look"));
    }
}
