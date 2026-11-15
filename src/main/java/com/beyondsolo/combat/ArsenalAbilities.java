package com.beyondsolo.combat;

import com.beyondsolo.item.arsenal.ArsenalWeapon;
import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.network.SignatureFxPayload;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Signature arts of the mythic and divine weapons (right-click). Each art has two beats: the wind-up starts the
 * wielder's own body animation and gathering effects, and the impact lands on the animation's strike frame
 * ({@link Signature#impactTicks()}) with that art's own visuals, sounds and screen effect (drawn by the client's
 * SignatureFx). The cooldown starts at once, so an art can't be queued twice.
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
        player.getCooldowns().addCooldown(stack.getItem(), sig.cooldownSeconds() * 20);
        windup(level, player, sig);
        send(player, sig, SignatureFxPayload.WINDUP, List.of());
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(sig.displayName().copy().withStyle(s -> s.withColor(sig.color()).withBold(true)), true);
        }
        ServerScheduler.schedule(sig.impactTicks(), () -> {
            if (!player.isAlive() || player.isRemoved() || player.level() != level) return;
            float power = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            List<Vec3> points = switch (sig) {
                case INFERNAL_REND -> infernalRend(level, player);
                case ABSOLUTE_ZERO -> absoluteZero(level, player);
                case HEAVENS_VERDICT -> heavensVerdict(level, player, power);
                case CATACLYSM_CLEAVE -> cataclysmCleave(level, player, power);
                case DRAGONS_WRATH -> dragonsWrath(level, player, power);
                case DEVOURING_STAR -> devouringStar(level, player);
                case COLOSSUS_STANCE -> colossusStance(level, player);
                case VERDANT_BLESSING -> verdantBlessing(level, player);
                case STARFALL -> List.of();
            };
            send(player, sig, SignatureFxPayload.IMPACT, points);
        });
    }

    /** Sounds of the wind-up, while the body animation gathers itself. */
    private static void windup(ServerLevel level, Player p, Signature sig) {
        switch (sig) {
            case INFERNAL_REND -> sound(level, p, SoundEvents.FIRECHARGE_USE, 0.8f, 0.5f);
            case ABSOLUTE_ZERO -> sound(level, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.6f);
            case HEAVENS_VERDICT -> {
                sound(level, p, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.6f);
                sound(level, p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
            }
            case CATACLYSM_CLEAVE -> sound(level, p, SoundEvents.BLAZE_AMBIENT, 1f, 0.5f);
            case DRAGONS_WRATH -> sound(level, p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1f, 1.6f);
            case DEVOURING_STAR -> sound(level, p, SoundEvents.BEACON_AMBIENT, 1.5f, 1.8f);
            case COLOSSUS_STANCE -> sound(level, p, SoundEvents.IRON_GOLEM_ATTACK, 1f, 0.6f);
            case VERDANT_BLESSING -> sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.6f);
            default -> {}
        }
    }

    static void send(LivingEntity p, Signature sig, int phase, List<Vec3> points) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new SignatureFxPayload(p.getId(), sig, phase, points));
    }

    // ---------------------------------------------------------------- mythic

    /** Hellsovereign's Fang: six seconds of hellfire on every strike. */
    static List<Vec3> infernalRend(ServerLevel level, Player p) {
        p.addEffect(new MobEffectInstance(ModEffects.INFERNAL_REND, 6 * 20, 0, false, true, true));
        sound(level, p, SoundEvents.BLAZE_SHOOT, 1.2f, 0.6f);
        sound(level, p, SoundEvents.GHAST_SHOOT, 0.7f, 0.5f);
        return List.of();
    }

    /** Winterborne Requiem: freeze everything nearby. Points: the frozen foes. */
    static List<Vec3> absoluteZero(ServerLevel level, Player p) {
        List<Vec3> frozen = new ArrayList<>();
        for (LivingEntity e : ArsenalHits.foesAround(p, p.position(), 7)) {
            ArsenalHits.freeze(e, p, 4 * 20);
            ArsenalHits.magicHit(p, e, 6f);
            if (frozen.size() < 16) frozen.add(e.position());
        }
        sound(level, p, SoundEvents.GLASS_BREAK, 1.4f, 0.5f);
        sound(level, p, SoundEvents.POWDER_SNOW_BREAK, 1.4f, 0.6f);
        sound(level, p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2f, 0.5f);
        return frozen;
    }

    /** Stormcaller's Edict: lightning falls on up to five foes. Points: where it struck. */
    static List<Vec3> heavensVerdict(ServerLevel level, Player p, float power) {
        List<LivingEntity> foes = ArsenalHits.foesAround(p, p.position(), 10).stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).limit(5).toList();
        List<Vec3> struck = new ArrayList<>();
        for (LivingEntity e : foes) {
            ArsenalHits.magicHit(p, e, 8f + power * 0.6f);
            struck.add(e.position());
        }
        if (struck.isEmpty()) struck.add(p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(4)));
        sound(level, p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.6f, 0.9f);
        for (Vec3 at : struck) level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.4f, 0.8f);
        return struck;
    }

    // ---------------------------------------------------------------- divine

    /** Ruinbringer (warrior): a burning cleave that tears a wedge of ground in front of the wielder. Points: origin, tip. */
    static List<Vec3> cataclysmCleave(ServerLevel level, Player p, float power) {
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
        sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.7f);
        sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
        sound(level, p, SoundEvents.BLAZE_SHOOT, 1f, 0.5f);
        return List.of(origin, origin.add(look.scale(7.5)));
    }

    /** Calamity Fang (assassin): dash through everything in a line, leaving crimson wounds behind. Points: from, to, then the cut foes. */
    static List<Vec3> dragonsWrath(ServerLevel level, Player p, float power) {
        Vec3 from = p.position();
        Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 want = from.add(look.scale(9));
        BlockHitResult wall = level.clip(new ClipContext(from.add(0, 0.6, 0), want.add(0, 0.6, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 to = wall.getType() == HitResult.Type.MISS ? want : wall.getLocation().subtract(look.scale(0.6)).add(0, -0.6, 0);
        List<Vec3> points = new ArrayList<>(List.of(from, to));
        AABB path = new AABB(from, to).inflate(1.4, 1.2, 1.4);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, path, e -> ArsenalHits.isFoe(p, e))) {
            ArsenalHits.magicHit(p, e, 8f + power * 0.8f);
            Statuses.apply(e, DamageElement.CRIMSON, 50, p);
            if (points.size() < 16) points.add(e.position());
        }
        p.teleportTo(to.x, to.y, to.z);
        p.resetFallDistance();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 10, 4, false, false, false));
        sound(level, p, SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 1.4f);
        sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.3f);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.6f);
        return points;
    }

    /** Eye of the Insatiable (mage): a star that devours whatever it hits, up to 28 blocks away. Points: muzzle, impact. */
    static List<Vec3> devouringStar(ServerLevel level, Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(28));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, p, eye, stop, new AABB(eye, stop).inflate(1),
                e -> e instanceof LivingEntity l && ArsenalHits.isFoe(p, l), 0.3f);
        Vec3 at = entity != null ? entity.getLocation() : stop;
        float spell = 1f + (float) p.getAttributeValue(ModAttributes.SPELL_POWER);
        for (LivingEntity e : ArsenalHits.foesAround(p, at, 4)) ArsenalHits.magicHit(p, e, 12f * spell);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 1.3f);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
        sound(level, p, SoundEvents.EVOKER_CAST_SPELL, 1.2f, 0.7f);
        return List.of(eye.add(p.getLookAngle().scale(1.2)), at);
    }

    /** Colossus Aegis (guardian): plant the hammer, draw every foe's eye and shrug off their blows. Points: knocked-back foes. */
    static List<Vec3> colossusStance(ServerLevel level, Player p) {
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 8 * 20, 1, false, true, true));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 8 * 20, 2, false, true, true));
        List<Vec3> knocked = new ArrayList<>();
        for (LivingEntity e : ArsenalHits.foesAround(p, p.position(), 14)) {
            if (e instanceof Mob m) m.setTarget(p);
            if (e.distanceTo(p) < 4.5) {
                Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1).normalize();
                e.push(away.x * 1.1, 0.35, away.z * 1.1);
                e.hurtMarked = true;
                ArsenalHits.magicHit(p, e, 6f);
                if (knocked.size() < 16) knocked.add(e.position());
            }
        }
        sound(level, p, SoundEvents.ANVIL_LAND, 1f, 0.5f);
        sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.6f);
        sound(level, p, SoundEvents.IRON_GOLEM_REPAIR, 1.2f, 0.6f);
        return knocked;
    }

    /** Branch of the World Tree (healer): a great heal for the whole party, and most ailments wash away. Points: the allies. */
    static List<Vec3> verdantBlessing(ServerLevel level, Player p) {
        List<Vec3> allies = new ArrayList<>();
        for (Player ally : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(10), Player::isAlive)) {
            ally.heal(ally.getMaxHealth() * 0.4f);
            ally.removeEffect(ModEffects.VENOM);
            ally.removeEffect(ModEffects.CRIMSON_BRAND);
            ally.removeEffect(ModEffects.FROSTBITE);
            ally.removeEffect(ModEffects.FROZEN);
            ally.removeEffect(MobEffects.POISON);
            ally.removeEffect(MobEffects.WITHER);
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 8 * 20, 1, false, true, true));
            if (ally != p && allies.size() < 16) allies.add(ally.position());
        }
        sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.8f);
        sound(level, p, SoundEvents.BEACON_POWER_SELECT, 1f, 1.4f);
        sound(level, p, SoundEvents.CHERRY_LEAVES_BREAK, 1.2f, 0.7f);
        return allies;
    }

    // ---------------------------------------------------------------- helpers

    private static void sound(ServerLevel level, Player p, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private ArsenalAbilities() {}
}
