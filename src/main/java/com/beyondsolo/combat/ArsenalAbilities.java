package com.beyondsolo.combat;

import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.item.arsenal.ArsenalWeapon;
import com.beyondsolo.item.arsenal.Signature;
import com.beyondsolo.network.SignatureFxPayload;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Signature arts of the legendary, mythic and divine weapons. A right-click art ({@link #unleash}) has a wind-up that
 * starts the wielder's own body animation and gathering effects, its first blow on the animation's strike frame
 * ({@link Signature#impactTicks()}), and for the bigger arts later beats of its own. The cooldown starts at once, so an
 * art can't be queued twice. The bows' arts are held draws: {@link #charged} when the string has been held long enough,
 * {@link #loosing} as the arrow leaves. The arts themselves are in {@link LegendaryArts}, {@link MythicArts} and
 * {@link DivineArts}; the client draws each one ({@code ArtFx}) from the payloads sent here.
 *
 * <p>Every art deals magic damage through {@link ArsenalHits#magicHit} sized with {@code Balance.flatDamage} plus a
 * share of the wielder's attack, inside the final balance pass's ceilings: heals go through {@code Balance.healRaw},
 * nothing makes anyone untouchable.</p>
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
        ServerScheduler.schedule(sig.impactTicks(), () -> {
            if (!alive(player, level)) return;
            List<Vec3> points = impact(level, player, sig, power(player));
            send(player, sig, SignatureFxPayload.IMPACT, points);
        });
    }

    /** The art's first blow, on the strike frame. Returns the points its look needs. */
    private static List<Vec3> impact(ServerLevel level, Player p, Signature sig, float power) {
        return switch (sig) {
            case INFERNAL_REND -> MythicArts.infernalRend(level, p, power);
            case ABSOLUTE_ZERO -> MythicArts.absoluteZero(level, p, power);
            case HEAVENS_VERDICT -> MythicArts.heavensVerdict(level, p, power);
            case CATACLYSM_CLEAVE -> DivineArts.cataclysmCleave(level, p, power);
            case DRAGONS_WRATH -> DivineArts.dragonsWrath(level, p, power);
            case DEVOURING_STAR -> DivineArts.devouringStar(level, p);
            case COLOSSUS_STANCE -> DivineArts.colossusStance(level, p, power);
            case VERDANT_BLESSING -> DivineArts.verdantBlessing(level, p);
            case STORMBREAK -> LegendaryArts.stormbreak(level, p, power);
            case TITANS_BARRAGE -> LegendaryArts.titansBarrage(level, p, power);
            case THREE_HEADED_MAW -> LegendaryArts.threeHeadedMaw(level, p, power);
            case CONSTELLATION -> LegendaryArts.constellation(level, p);
            case SAINTS_DAWN -> LegendaryArts.saintsDawn(level, p);
            case VEILSTEP -> LegendaryArts.veilstep(level, p, power);
            case BLOOD_PRICE -> LegendaryArts.bloodPrice(level, p, power);
            case SUNDERING_VERDICT -> LegendaryArts.sunderingVerdict(level, p, power);
            case PYRE_OF_OATHS -> LegendaryArts.pyreOfOaths(level, p, power);
            case NIGHTGLASS_FAN -> LegendaryArts.nightglassFan(level, p, power);
            case REQUIEM_OF_THORNS -> LegendaryArts.requiemOfThorns(level, p, power);
            case STARFALL, GALE_LANCE -> List.of();
        };
    }

    /** Sounds of the wind-up, while the body animation gathers itself. */
    private static void windup(ServerLevel level, Player p, Signature sig) {
        switch (sig) {
            case INFERNAL_REND -> {
                sound(level, p, SoundEvents.FIRECHARGE_USE, 0.9f, 0.5f);
                sound(level, p, SoundEvents.BLAZE_AMBIENT, 0.7f, 0.5f);
            }
            case ABSOLUTE_ZERO -> {
                sound(level, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3f, 0.6f);
                sound(level, p, SoundEvents.POWDER_SNOW_STEP, 1.2f, 0.5f);
            }
            case HEAVENS_VERDICT -> {
                sound(level, p, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.6f);
                sound(level, p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.8f);
            }
            case CATACLYSM_CLEAVE -> {
                sound(level, p, SoundEvents.BLAZE_AMBIENT, 1.2f, 0.45f);
                sound(level, p, SoundEvents.FIRECHARGE_USE, 1f, 0.6f);
                sound(level, p, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1f, 0.6f);
            }
            case DRAGONS_WRATH -> {
                sound(level, p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1f, 1.6f);
                sound(level, p, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 1.4f);
            }
            case DEVOURING_STAR -> {
                sound(level, p, SoundEvents.BEACON_AMBIENT, 1.6f, 1.8f);
                sound(level, p, SoundEvents.PORTAL_TRIGGER, 0.5f, 1.9f);
            }
            case COLOSSUS_STANCE -> {
                sound(level, p, SoundEvents.IRON_GOLEM_ATTACK, 1.1f, 0.6f);
                sound(level, p, SoundEvents.BEACON_POWER_SELECT, 1f, 0.6f);
            }
            case VERDANT_BLESSING -> {
                sound(level, p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.3f, 0.6f);
                sound(level, p, SoundEvents.ROOTED_DIRT_BREAK, 1.2f, 0.6f);
            }
            case STORMBREAK -> sound(level, p, SoundEvents.TRIDENT_RIPTIDE_1.value(), 1f, 1.3f);
            case TITANS_BARRAGE -> sound(level, p, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.2f, 0.7f);
            case THREE_HEADED_MAW -> {
                sound(level, p, SoundEvents.WOLF_GROWL, 1.2f, 0.5f);
                sound(level, p, SoundEvents.FIRECHARGE_USE, 0.8f, 0.7f);
            }
            case CONSTELLATION -> sound(level, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.5f);
            case SAINTS_DAWN -> sound(level, p, SoundEvents.BEACON_ACTIVATE, 1f, 1.4f);
            case VEILSTEP -> sound(level, p, SoundEvents.ENDERMAN_AMBIENT, 0.6f, 1.6f);
            case BLOOD_PRICE -> sound(level, p, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.6f);
            case SUNDERING_VERDICT -> sound(level, p, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.8f);
            case PYRE_OF_OATHS -> sound(level, p, SoundEvents.BLAZE_AMBIENT, 1f, 0.7f);
            case NIGHTGLASS_FAN -> sound(level, p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.2f, 0.8f);
            case REQUIEM_OF_THORNS -> sound(level, p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.5f);
            default -> {}
        }
    }

    // ---------------------------------------------------------------- the bows' drawn arts

    private static final String DRAW_READY = "beyondsolo.art_ready.";
    private static final String LOOSING = "beyondsolo.art_loosing";

    /** Whether the wielder's drawn art is off its cooldown (kept apart from the bow's own: that would stop it shooting). */
    public static boolean drawReady(Player p, Signature sig) {
        return p.level().getGameTime() >= p.getPersistentData().getLong(DRAW_READY + sig.key());
    }

    /** Seconds until the drawn art can be loosed again (0 when it can). */
    public static int drawCooldownLeft(Player p, Signature sig) {
        long left = p.getPersistentData().getLong(DRAW_READY + sig.key()) - p.level().getGameTime();
        return left <= 0 ? 0 : (int) ((left + 19) / 20);
    }

    /**
     * Every weapon art the player has used is ready again: the right-click arts' weapon cooldowns and the bows' drawn
     * arts (/solo heal, /solo reset cooldowns).
     */
    public static void resetCooldowns(ServerPlayer p) {
        for (var item : Arsenal.everything()) {
            if (item.get() instanceof ArsenalWeapon w && w.signature() != null) p.getCooldowns().removeCooldown(item.get());
        }
        for (Signature sig : Signature.values()) {
            if (!sig.drawn() || !p.getPersistentData().contains(DRAW_READY + sig.key())) continue;
            p.getPersistentData().remove(DRAW_READY + sig.key());
            PacketDistributor.sendToPlayer(p, new SignatureFxPayload(p.getId(), sig, SignatureFxPayload.READY, List.of()));
        }
    }

    /** What the Colossus Stance's bulwark has taken so far, or -1 when none stands. */
    public static float bulwarkTaken(Player p) {
        return DivineArts.bastionTaken(p);
    }

    /** The string has been held long enough: the art gathers on the arrow, for the archer and everyone watching. */
    public static void charged(Player p, Signature sig) {
        if (!(p.level() instanceof ServerLevel level)) return;
        send(p, sig, SignatureFxPayload.WINDUP, List.of());
        switch (sig) {
            case STARFALL -> {
                sound(level, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.8f);
                sound(level, p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.9f);
            }
            case GALE_LANCE -> {
                sound(level, p, SoundEvents.BREEZE_CHARGE, 1.2f, 0.8f);
                sound(level, p, SoundEvents.ELYTRA_FLYING, 0.4f, 1.6f);
            }
            default -> {}
        }
    }

    /** A charged draw is being loosed: the next arrow that leaves this archer this tick carries the art. */
    public static void loosing(Player p, Signature sig) {
        p.getPersistentData().putString(LOOSING, sig.key() + "@" + p.level().getGameTime());
    }

    /** Called once the bow has loosed: the art goes on cooldown if an arrow carried it, and the mark is cleared. */
    public static void loosed(Player p, Signature sig) {
        p.getPersistentData().remove(LOOSING);
    }

    /** The arrow just loosed by {@code owner}, if it carries a charged art. */
    static void arrowLoosed(LivingEntity owner, AbstractArrow arrow) {
        if (!(owner instanceof Player p) || !(p.level() instanceof ServerLevel level)) return;
        String mark = p.getPersistentData().getString(LOOSING);
        if (mark.isEmpty()) return;
        p.getPersistentData().remove(LOOSING);
        int at = mark.indexOf('@');
        if (at < 0 || !mark.substring(at + 1).equals(Long.toString(level.getGameTime()))) return;
        Signature sig;
        try {
            sig = Signature.valueOf(mark.substring(0, at).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return;
        }
        p.getPersistentData().putLong(DRAW_READY + sig.key(), level.getGameTime() + sig.cooldownSeconds() * 20L);
        switch (sig) {
            case STARFALL -> DivineArts.starArrow(level, p, arrow);
            case GALE_LANCE -> LegendaryArts.galeLance(level, p, arrow);
            default -> {}
        }
    }

    // ---------------------------------------------------------------- helpers shared by the arts

    static void send(LivingEntity p, Signature sig, int phase, List<Vec3> points) {
        List<Vec3> pts = points.size() > 32 ? points.subList(0, 32) : points;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new SignatureFxPayload(p.getId(), sig, phase, pts));
    }

    static void sound(ServerLevel level, LivingEntity p, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    static void soundAt(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    static boolean alive(Player p, ServerLevel level) {
        return p.isAlive() && !p.isRemoved() && p.level() == level;
    }

    /** Runs a later beat of an art, if the wielder is still there to see it. */
    static void later(Player p, ServerLevel level, int ticks, Runnable beat) {
        if (ticks <= 0) {
            if (alive(p, level)) beat.run();
            return;
        }
        ServerScheduler.schedule(ticks, () -> {
            if (alive(p, level)) beat.run();
        });
    }

    static float power(Player p) {
        return (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    /** Where the wielder faces, on the ground's plane. */
    static Vec3 flatLook(LivingEntity p) {
        Vec3 look = p.getLookAngle().multiply(1, 0, 1);
        return look.lengthSqr() < 1.0e-6 ? Vec3.directionFromRotation(0, p.getYRot()) : look.normalize();
    }

    /** Foes within {@code range} of {@code origin} and within {@code halfAngle} degrees of {@code dir} (on the ground's plane). */
    static List<LivingEntity> cone(LivingEntity p, Vec3 origin, Vec3 dir, double range, double halfAngle) {
        double cos = Math.cos(Math.toRadians(halfAngle));
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : ArsenalHits.foesAround(p, origin, range)) {
            Vec3 to = e.position().subtract(origin).multiply(1, 0, 1);
            if (to.lengthSqr() < 0.36 || to.normalize().dot(dir) >= cos) out.add(e);
        }
        return out;
    }

    /** Foes whose bodies come within {@code radius} of the segment from a to b. */
    static List<LivingEntity> line(LivingEntity p, Vec3 a, Vec3 b, double radius) {
        AABB box = new AABB(a, b).inflate(radius + 1.5);
        Vec3 ab = b.subtract(a);
        double len2 = Math.max(1.0e-6, ab.lengthSqr());
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, e -> ArsenalHits.isFoe(p, e))) {
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double k = Math.max(0, Math.min(1, c.subtract(a).dot(ab) / len2));
            Vec3 near = a.add(ab.scale(k));
            if (c.distanceTo(near) <= radius + e.getBbWidth() * 0.5 + e.getBbHeight() * 0.35) out.add(e);
        }
        return out;
    }

    /** A point at the middle of a body, for hit marks. */
    static Vec3 chest(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    /** Pushes a foe, less the harder it is to move (bosses barely budge). */
    static void knock(LivingEntity e, double x, double y, double z) {
        double keep = 1 - net.minecraft.util.Mth.clamp(e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0, 1) * 0.8;
        e.push(x * keep, y * keep, z * keep);
        e.hurtMarked = true;
    }

    /** The ground under (or just over) a point, so a blow lands on it and not in the air. */
    static Vec3 ground(ServerLevel level, Vec3 at) {
        net.minecraft.world.phys.BlockHitResult down = level.clip(new net.minecraft.world.level.ClipContext(at.add(0, 2.5, 0), at.add(0, -4, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return down.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? at : down.getLocation();
    }

    /** The point a straight line from a towards b stops at: the first wall, or b. */
    static Vec3 reach(LivingEntity p, Vec3 a, Vec3 b) {
        net.minecraft.world.phys.BlockHitResult hit = p.level().clip(new net.minecraft.world.level.ClipContext(a, b,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? b : hit.getLocation();
    }

    /** Heals the wielder by what a blow took from its target (life drawn back through the blade). */
    static float hitAndMeasure(Signature art, Player p, LivingEntity e, float amount) {
        float before = e.getHealth() + e.getAbsorptionAmount();
        ArsenalHits.artHit(art, p, e, amount);
        return Math.max(0, before - e.getHealth() - e.getAbsorptionAmount());
    }

    private ArsenalAbilities() {}
}
