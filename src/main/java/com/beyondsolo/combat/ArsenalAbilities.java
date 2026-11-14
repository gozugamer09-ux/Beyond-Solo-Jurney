package com.beyondsolo.combat;

import com.beyondsolo.item.arsenal.ArsenalWeapon;
import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.network.CombatFxPayload;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;

/**
 * Signature arts of the mythic and divine weapons (right-click). Each one has a cooldown shown on the hotbar, a
 * screen flash for the wielder and its own burst of light and particles for everyone around.
 */
public final class ArsenalAbilities {
    public static void unleash(Player player, ItemStack stack, ArsenalWeapon w) {
        Signature sig = w.signature();
        if (sig == null || !sig.active() || !(player.level() instanceof ServerLevel level)) return;
        if (player.getCooldowns().isOnCooldown(stack.getItem())) return;
        if (ArsenalHits.refuses(player, w)) {
            ArsenalHits.warnRefused(player, w);
            return;
        }
        float power = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        switch (sig) {
            case INFERNAL_REND -> infernalRend(level, player);
            case ABSOLUTE_ZERO -> absoluteZero(level, player);
            case HEAVENS_VERDICT -> heavensVerdict(level, player, power);
            case CATACLYSM_CLEAVE -> cataclysmCleave(level, player, power);
            case DRAGONS_WRATH -> dragonsWrath(level, player, power);
            case DEVOURING_STAR -> devouringStar(level, player);
            case COLOSSUS_STANCE -> colossusStance(level, player);
            case VERDANT_BLESSING -> verdantBlessing(level, player);
            default -> {
                return;
            }
        }
        player.getCooldowns().addCooldown(stack.getItem(), sig.cooldownSeconds() * 20);
        CombatFx.play(player, CombatFxPayload.Kind.SIGNATURE, sig.color());
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(sig.displayName().copy().withStyle(s -> s.withColor(sig.color()).withBold(true)), true);
        }
    }

    // ---------------------------------------------------------------- mythic

    /** Hellsovereign's Fang: six seconds of hellfire on every strike. */
    static void infernalRend(ServerLevel level, Player p) {
        p.addEffect(new MobEffectInstance(ModEffects.INFERNAL_REND, 6 * 20, 0, false, true, true));
        sound(level, p, SoundEvents.BLAZE_SHOOT, 1.2f, 0.6f);
        sound(level, p, SoundEvents.FIRECHARGE_USE, 1f, 0.7f);
        ring(level, p.position(), 2.5, ParticleTypes.FLAME, 40);
        level.sendParticles(ParticleTypes.LAVA, p.getX(), p.getY(1), p.getZ(), 12, 0.4, 0.5, 0.4, 0.1);
        CombatFx.play(p, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0xFF5A1E, 3));
    }

    /** Winterborne Requiem: freeze everything nearby. */
    static void absoluteZero(ServerLevel level, Player p) {
        for (LivingEntity e : ArsenalHits.foesAround(p, p.position(), 7)) {
            ArsenalHits.freeze(e, p, 4 * 20);
            ArsenalHits.magicHit(p, e, 6f);
        }
        sound(level, p, SoundEvents.GLASS_BREAK, 1.4f, 0.5f);
        sound(level, p, SoundEvents.POWDER_SNOW_BREAK, 1.4f, 0.6f);
        for (int r = 1; r <= 7; r += 2) ring(level, p.position().add(0, 0.2, 0), r, ParticleTypes.SNOWFLAKE, 14 * r);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, p.getX(), p.getY(0.5), p.getZ(), 50, 3, 0.5, 3, 0.05);
        CombatFx.play(p, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0x9ED8FF, 7));
    }

    /** Stormcaller's Edict: lightning falls on up to five foes. */
    static void heavensVerdict(ServerLevel level, Player p, float power) {
        List<LivingEntity> foes = ArsenalHits.foesAround(p, p.position(), 10).stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).limit(5).toList();
        for (LivingEntity e : foes) {
            bolt(level, e.position());
            ArsenalHits.magicHit(p, e, 8f + power * 0.6f);
        }
        if (foes.isEmpty()) bolt(level, p.position().add(p.getLookAngle().scale(4)));
        sound(level, p, SoundEvents.TRIDENT_THUNDER.value(), 1.5f, 0.8f);
        CombatFx.play(p, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0xB8E2FF, 5));
    }

    // ---------------------------------------------------------------- divine

    /** Ruinbringer (warrior): a burning cleave that tears a wedge of ground in front of the wielder. */
    static void cataclysmCleave(ServerLevel level, Player p, float power) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 origin = p.position();
        for (LivingEntity e : ArsenalHits.foesAround(p, origin, 7.5)) {
            Vec3 to = e.position().subtract(origin).multiply(1, 0, 1);
            if (to.lengthSqr() > 0.01 && to.normalize().dot(look) < Math.cos(Math.toRadians(40))) continue;
            ArsenalHits.magicHit(p, e, 10f + power);
            Statuses.apply(e, DamageElement.FIRE, 60, p);
            e.push(look.x * 0.6, 0.55, look.z * 0.6);
            e.hurtMarked = true;
        }
        for (int d = 1; d <= 7; d++) {
            Vec3 at = origin.add(look.scale(d));
            double spread = d * 0.35;
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.2, at.z, 8 + d * 2, spread, 0.15, spread, 0.04);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 2, spread * 0.5, 0.1, spread * 0.5, 0.01);
            if (d % 2 == 1) level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + look.x * 1.5, p.getY(0.6), p.getZ() + look.z * 1.5, 3, 0.4, 0.1, 0.4, 0);
        sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.7f);
        sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
        CombatFx.play(p, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0xFF7A2E, 7));
    }

    /** Calamity Fang (assassin): dash through everything in a line, leaving crimson wounds behind. */
    static void dragonsWrath(ServerLevel level, Player p, float power) {
        Vec3 from = p.position();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 want = from.add(look.scale(9));
        BlockHitResult wall = level.clip(new ClipContext(from.add(0, 0.6, 0), want.add(0, 0.6, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 to = wall.getType() == HitResult.Type.MISS ? want : wall.getLocation().subtract(look.scale(0.6)).add(0, -0.6, 0);
        AABB path = new AABB(from, to).inflate(1.4, 1.2, 1.4);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, path, e -> ArsenalHits.isFoe(p, e))) {
            ArsenalHits.magicHit(p, e, 8f + power * 0.8f);
            Statuses.apply(e, DamageElement.CRIMSON, 50, p);
        }
        for (int i = 0; i <= 18; i++) {
            Vec3 at = from.lerp(to, i / 18.0);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.88f, 0.19f, 0.43f), 1.6f), at.x, at.y + 0.9, at.z, 4, 0.2, 0.4, 0.2, 0);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, at.x, at.y + 0.9, at.z, 3, 0.3, 0.4, 0.3, 0.02);
        }
        p.teleportTo(to.x, to.y, to.z);
        p.resetFallDistance();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 4, false, false, false));
        sound(level, p, SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 1.4f);
        sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.3f);
        CombatFx.play(p, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0xE0306E, 3));
    }

    /** Eye of the Insatiable (mage): a star that devours whatever it hits, up to 28 blocks away. */
    static void devouringStar(ServerLevel level, Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(28));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, p, eye, stop, new AABB(eye, stop).inflate(1),
                e -> e instanceof LivingEntity l && ArsenalHits.isFoe(p, l), 0.3f);
        Vec3 at = entity != null ? entity.getLocation() : stop;
        float spell = 1f + (float) p.getAttributeValue(ModAttributes.SPELL_POWER);
        for (LivingEntity e : ArsenalHits.foesAround(p, at, 4)) ArsenalHits.magicHit(p, e, 12f * spell);
        int steps = (int) Math.max(4, eye.distanceTo(at) * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 t = eye.lerp(at, i / (double) steps);
            level.sendParticles(ParticleTypes.END_ROD, t.x, t.y, t.z, 1, 0.02, 0.02, 0.02, 0);
            if (i % 3 == 0) level.sendParticles(new DustParticleOptions(new Vector3f(0.6f, 0.48f, 1f), 1.2f), t.x, t.y, t.z, 1, 0.05, 0.05, 0.05, 0);
        }
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 1.2, 0.6, 1.2, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 80, 1.5, 1.5, 1.5, 0.3);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, at.x, at.y, at.z, 40, 1.2, 0.6, 1.2, 0.02);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 1.3f);
        sound(level, p, SoundEvents.EVOKER_CAST_SPELL, 1.2f, 0.7f);
        if (entity != null && entity.getEntity() instanceof LivingEntity hit) CombatFx.play(hit, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0x9B7BFF, 4));
    }

    /** Colossus Aegis (guardian): plant the hammer, draw every foe's eye and shrug off their blows. */
    static void colossusStance(ServerLevel level, Player p) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 8 * 20, 1, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 8 * 20, 2, false, true, true));
        for (LivingEntity e : ArsenalHits.foesAround(p, p.position(), 14)) {
            if (e instanceof Mob m) m.setTarget(p);
            if (e.distanceTo(p) < 4.5) {
                Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                e.push(away.x * 1.1, 0.35, away.z * 1.1);
                e.hurtMarked = true;
                ArsenalHits.magicHit(p, e, 6f);
            }
        }
        ring(level, p.position().add(0, 0.1, 0), 4.5, ParticleTypes.CLOUD, 60);
        level.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.78f, 0.35f), 2f), p.getX(), p.getY(1), p.getZ(), 40, 0.6, 1, 0.6, 0);
        sound(level, p, SoundEvents.ANVIL_LAND, 1f, 0.5f);
        sound(level, p, SoundEvents.IRON_GOLEM_REPAIR, 1.2f, 0.6f);
        CombatFx.play(p, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0xFFC85A, 5));
    }

    /** Branch of the World Tree (healer): a great heal for the whole party, and most ailments wash away. */
    static void verdantBlessing(ServerLevel level, Player p) {
        for (Player ally : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(10), Player::isAlive)) {
            ally.heal(ally.getMaxHealth() * 0.4f);
            ally.removeEffect(ModEffects.VENOM);
            ally.removeEffect(ModEffects.CRIMSON_BRAND);
            ally.removeEffect(ModEffects.FROSTBITE);
            ally.removeEffect(ModEffects.FROZEN);
            ally.removeEffect(MobEffects.POISON);
            ally.removeEffect(MobEffects.WITHER);
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 8 * 20, 1, false, true, true));
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getY(1), ally.getZ(), 20, 0.5, 0.8, 0.5, 0);
            if (ally != p) CombatFx.play(ally, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0x8CE05A, 2));
        }
        ring(level, p.position().add(0, 0.1, 0), 3, ParticleTypes.SPORE_BLOSSOM_AIR, 50);
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY(2), p.getZ(), 60, 3, 1, 3, 0);
        sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.8f);
        sound(level, p, SoundEvents.BEACON_POWER_SELECT, 1f, 1.4f);
        CombatFx.play(p, CombatFxPayload.Kind.NOVA, ArsenalHits.packNova(0x8CE05A, 10));
    }

    // ---------------------------------------------------------------- helpers

    private static void bolt(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(at.x, at.y, at.z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    private static void ring(ServerLevel level, Vec3 at, double radius, ParticleOptions type, int count) {
        for (int i = 0; i < count; i++) {
            double a = i / (double) count * Math.PI * 2;
            level.sendParticles(type, at.x + Math.cos(a) * radius, at.y, at.z + Math.sin(a) * radius, 1, 0, 0.05, 0, 0.01);
        }
    }

    private static void sound(ServerLevel level, Player p, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private ArsenalAbilities() {}
}
