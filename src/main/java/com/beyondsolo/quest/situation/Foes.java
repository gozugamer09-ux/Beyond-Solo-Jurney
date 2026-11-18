package com.beyondsolo.quest.situation;

import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.HiddenLevel;
import com.beyondsolo.gate.DungeonBoss;
import com.beyondsolo.party.Parties;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.worldboss.ColossalBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** How the System sizes up the creatures in a situation: how strong, what they are called, where they are. */
public final class Foes {
    /**
     * How a creature stands against a hunter: its power over theirs, the same measure as the colour of its name
     * ({@link HiddenLevel.Threat}). System creatures use their kept hidden level; any other creature (a vanilla monster)
     * is measured on the spot, so the System judges every creature the same way.
     */
    public static double ratio(LivingEntity creature, Player hunter) {
        int level = HiddenLevel.has(creature) ? HiddenLevel.of(creature) : HiddenLevel.compute(creature);
        return HiddenLevel.powerAt(level) / HiddenLevel.powerAt(HiddenLevel.hunterLevel(SystemAPI.get(hunter)));
    }

    /**
     * A boss: a gate's, a world boss or monarch, or anything tagged {@code c:bosses}. Its fight has its own rules, so a
     * situation never heals it, enrages it or brings it back.
     */
    public static boolean isBoss(Entity e) {
        return e instanceof DungeonBoss || e instanceof ColossalBoss || e.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** A red name: far stronger than the hunter. */
    public static boolean farStronger(LivingEntity creature, Player hunter) {
        return ratio(creature, hunter) >= HiddenLevel.Threat.FAR_FROM;
    }

    /** The creature's name as the player reads it ("Frostbound Rimeclaw", a boss's name, a custom name). */
    public static Component name(LivingEntity e) {
        if (e.hasCustomName() && e.getCustomName() != null) return e.getCustomName().copy();
        if (e instanceof DungeonBoss) return e.getDisplayName().copy();
        EnemyTraits t = EnemyTraits.of(e);
        if (t.affinity() != Affinity.NONE) return Component.translatable("enemy.beyondsolo.named", t.affinity().adjective(), e.getType().getDescription());
        return e.getType().getDescription().copy();
    }

    /** Health as a whole percentage, at least 1 while alive. */
    public static int healthPercent(LivingEntity e) {
        if (!e.isAlive()) return 0;
        return Math.max(1, Math.round(100f * e.getHealth() / Math.max(1f, e.getMaxHealth())));
    }

    /** The living entity with this id in the player's world, or null if it isn't there (unloaded, gone, dead). */
    @Nullable
    public static LivingEntity find(ServerPlayer player, @Nullable UUID id) {
        if (id == null || !(player.level() instanceof ServerLevel level)) return null;
        Entity e = level.getEntity(id);
        return e instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    /** The living entity with this id anywhere on the server (any dimension), or null. */
    @Nullable
    public static LivingEntity findAnywhere(ServerPlayer player, @Nullable UUID id) {
        if (id == null) return null;
        for (ServerLevel level : player.server.getAllLevels()) {
            Entity e = level.getEntity(id);
            if (e instanceof LivingEntity le && le.isAlive()) return le;
        }
        return null;
    }

    /** A monster hunting this player right now. */
    public static boolean hunting(Mob mob, Player player) {
        return mob.isAlive() && mob.getTarget() == player && (mob instanceof Enemy || mob.isAggressive());
    }

    /** Monsters within {@code radius} hunting the player. */
    public static List<Mob> huntersOf(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius), m -> hunting(m, player) && !ownedBy(m, player));
    }

    /** One of the player's own (a shadow, a tamed wolf): never a foe. */
    public static boolean ownedBy(Entity e, Player player) {
        if (e instanceof OwnableEntity own && player.getUUID().equals(own.getOwnerUUID())) return true;
        return e instanceof TamableAnimal t && t.isTame();
    }

    /**
     * A kill the player can call their own: by them, by a summon of theirs, or by a party member fighting beside them
     * (the rule shared quest kills go by, {@link Parties#sharers}).
     */
    public static boolean killedBy(ServerPlayer player, DamageSource source, LivingEntity dead) {
        ServerPlayer killer = playerBehind(source.getEntity());
        if (killer == null) return false;
        return killer == player || Parties.sharers(killer, dead).contains(player);
    }

    /** Who really dealt a blow or a kill: the player, or the player a summon of theirs fights for. */
    @Nullable
    public static ServerPlayer playerBehind(@Nullable Entity attacker) {
        if (attacker instanceof ServerPlayer p) return p;
        if (attacker instanceof OwnableEntity own && own.getOwner() instanceof ServerPlayer p) return p;
        return null;
    }

    /**
     * A spot between {@code min} and {@code max} blocks from {@code center} where a creature can stand (solid ground, two
     * blocks of air, no water), in a loaded chunk; null if none turns up.
     */
    @Nullable
    public static BlockPos spotAround(ServerLevel level, BlockPos center, int min, int max) {
        RandomSource r = level.random;
        for (int i = 0; i < 24; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            int d = min + r.nextInt(Math.max(1, max - min + 1));
            int x = center.getX() + (int) Math.round(Math.cos(a) * d), z = center.getZ() + (int) Math.round(Math.sin(a) * d);
            if (!level.isLoaded(new BlockPos(x, center.getY(), z))) continue;
            for (int dy = 6; dy >= -8; dy--) {
                BlockPos p = new BlockPos(x, center.getY() + dy, z);
                if (standable(level, p)) return p;
            }
        }
        return null;
    }

    /** Solid ground under it, room for a body above, no water. */
    public static boolean standable(ServerLevel level, BlockPos p) {
        BlockPos below = p.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getFluidState(p).isEmpty() && level.getFluidState(p.above()).isEmpty();
    }

    private Foes() {}
}
