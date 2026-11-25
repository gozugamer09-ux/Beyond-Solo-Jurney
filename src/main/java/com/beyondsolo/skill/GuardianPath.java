package com.beyondsolo.skill;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.network.SkillFxPayload.Kind;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import static com.beyondsolo.skill.SkillActions.aim;
import static com.beyondsolo.skill.SkillActions.canHit;
import static com.beyondsolo.skill.SkillActions.f;
import static com.beyondsolo.skill.SubclassActions.stun;
import static com.beyondsolo.skill.SubclassKit.*;

/**
 * The Guardian subclasses' own skill sets, on top of the starters in {@link SubclassSkills}: ten more each for the
 * Paladin, Ironwall Sentinel, Spellward Warden, Vengeance Knight and the rare Everlasting Colossus. Same shape as
 * {@link WarriorPath}. These are shield and hammer skills: no casting circles, each with a shape of its own.
 */
public final class GuardianPath {
    public static final String BUFF_BRACE = "brace";
    public static final String BUFF_MIRROR_WARD = "mirror_ward";
    public static final String BUFF_CITADEL = "citadel";
    public static final String BUFF_ARCANUM = "arcanum";
    public static final String BUFF_WRATH = "wrath";
    public static final String BUFF_EYE = "eye_for_an_eye";
    public static final String BUFF_GRUDGE = "grudge";

    /** Searing Brand: creature -> {end game time, bonus percent, caster entity id}. Server thread only. */
    static final Map<LivingEntity, long[]> BRANDS = new WeakHashMap<>();
    /** Eye for an Eye: the damage each knight has swallowed while it lasts. */
    static final Map<UUID, float[]> SWALLOWED = new HashMap<>();

    private static final Subclass PA = Subclass.PALADIN, IS = Subclass.IRONWALL_SENTINEL, SW = Subclass.SPELLWARD_WARDEN,
            VK = Subclass.VENGEANCE_KNIGHT, EC = Subclass.EVERLASTING_COLOSSUS;
    private static final PlayerRank A = PlayerRank.A, S = PlayerRank.S;
    private static final BlockParticleOption STONE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
    private static final BlockParticleOption DIRT = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState());
    private static final BlockState MASONRY = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState ROCK = Blocks.DRIPSTONE_BLOCK.defaultBlockState();

    private static float vit(ServerPlayer p, float perPoint) {
        return stat(p, Stat.VIT, perPoint);
    }

    private static Vec3 mid(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    private static boolean undead(LivingEntity e) {
        return e.getType().is(EntityTypeTags.UNDEAD);
    }

    private static void taunt(ServerPlayer p, LivingEntity e) {
        if (e instanceof Mob mob && !p.isCreative()) mob.setTarget(p);
    }

    private static void debris(ServerLevel level, Vec3 at, int count, double spread) {
        SkillParticles.send(level, STONE, at.x, at.y + 0.3, at.z, count, spread, 0.3, spread, 0.15);
    }

    /**
     * A slab of stone that stands for four seconds. It is a block marker particle, so nothing is really built and
     * nothing is left behind.
     */
    private static void slab(ServerLevel level, BlockState stone, Vec3 at) {
        marker(level, stone, at);
    }

    /** A column of slabs rising out of the ground, one a tick. */
    private static void column(ServerPlayer p, ServerLevel level, BlockState stone, Vec3 base, int height) {
        for (int i = 0; i < height; i++) {
            final int k = i;
            later(p, i, () -> slab(level, stone, base.add(0, k, 0)));
        }
    }

    /** Throws a creature sideways, out of the way of a charge. */
    private static void shove(ServerPlayer p, Vec3 dir, LivingEntity e, double strength, double up) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        double lr = e.position().subtract(p.position()).dot(side);
        if (Math.abs(lr) < 0.15) lr = (e.getId() & 1) == 0 ? 1 : -1;
        Vec3 out = side.scale(Math.signum(lr)).add(dir.scale(0.35)).normalize();
        e.setDeltaMovement(out.x * strength, up, out.z * strength);
        e.hurtMarked = true;
    }

    private static boolean spell(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.WITCH_RESISTANT_TO) || source.is(DamageTypeTags.IS_PROJECTILE);
    }

    // ================================================================ paladin

    static float zealHeal(int l) { return 0.15f * l; }
    static float resolveHeal(int l) { return 0.1f * l; }
    static float handsHeal(int l) { return 30 + 3 * l; }

    public static final Skill HAMMER_OF_DAWN = active("hammer_of_dawn", PA, A, 22, 0xFFE38A, 14, 140, 80,
            GuardianPath::hammerOfDawn, (l, t) -> a(f(10 * m(l, t))));
    public static final Skill CONSECRATE = active("consecrate", PA, A, 24, 0xFFF0B0, 18, 400, 260,
            GuardianPath::consecrate, (l, t) -> a(f(2.5f * m(l, t))));
    public static final Skill ZEAL = passive("zeal", PA, A, 26, 0xFFD870, (l, t) -> a(f(zealHeal(l))));
    public static final Skill SHIELD_OF_FAITH = active("shield_of_faith", PA, A, 29, 0xFFEFC0, 16, 500, 320,
            GuardianPath::shieldOfFaith, (l, t) -> a(f(4 + 4 * (l / 3))));
    public static final Skill RADIANT_CHARGE = active("radiant_charge", PA, A, 32, 0xFFE8A0, 16, 240, 140,
            GuardianPath::radiantCharge, (l, t) -> a(f(9 * m(l, t))));
    public static final Skill AURA_OF_RESOLVE = passive("aura_of_resolve", PA, A, 35, 0xFFF4C8, (l, t) -> a(f(resolveHeal(l))));
    public static final Skill LAY_ON_HANDS = active("lay_on_hands", PA, A, 38, 0xFFFAE0, 24, 880, 600,
            GuardianPath::layOnHands, (l, t) -> a(f(handsHeal(l))));
    public static final Skill DIVINE_VERDICT = active("divine_verdict", PA, A, 42, 0xFFD860, 22, 400, 260,
            GuardianPath::divineVerdict, (l, t) -> a(f(16 * m(l, t))));
    public static final Skill MARTYR = passive("martyr", PA, A, 46, 0xE8C870, (l, t) -> a(f(2 * l)));
    public static final Skill DAWNBREAKER = active("dawnbreaker", PA, S, 50, 0xFFE070, 45, 1500, 1000,
            GuardianPath::dawnbreaker, (l, t) -> a(f(45 * m(l, t))));

    static boolean hammerOfDawn(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 18, x -> canHit(p, x));
        if (target == null) return false;
        float dmg = 10 * m(l, tier(p)) + vit(p, 0.2f);
        sound(p, SoundEvents.TRIDENT_THROW.value(), 1f, 0.6f);
        fx(p, Kind.HAMMER, s.color(), mid(target), 1.8f, 8);
        later(p, 8, () -> {
            if (!target.isAlive()) return;
            hit(p, s, target, undead(target) ? dmg * 1.5f : dmg);
            stun(target, 20);
            sound(target, SoundEvents.ANVIL_LAND, 0.5f, 1.5f);
            fx(target, Kind.SPLASH, s.color(), mid(target), 1.8f, 1);
        });
        return true;
    }

    static boolean consecrate(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        sound(p, SoundEvents.BELL_RESONATE, 0.6f, 1.4f);
        // blessed ground: ripples of light and motes, no rune circle
        repeat(p, 120, 20, () -> fxAt(level, Kind.PULSE, s.color(), center, 5, 0));
        repeat(p, 120, 10, () -> {
            for (LivingEntity e : foes(p, center, 5)) magic(p, s, e, (2.5f * m(l, tier(p)) + vit(p, 0.05f)) * (undead(e) ? 2 : 1));
        });
        repeat(p, 120, 20, () -> {
            for (Player ally : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(center, center).inflate(5), Player::isAlive)) ally.heal(0.5f + 0.1f * l);
            SkillParticles.send(level, ParticleTypes.END_ROD, center.x, center.y + 0.2, center.z, 8, 2.5, 0.1, 2.5, 0.01);
        });
        return true;
    }

    static boolean shieldOfFaith(ServerPlayer p, Skill s, int l) {
        sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.5f);
        for (Player ally : allies(p, 8)) {
            ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, l / 3, false, false, true));
            fx(ally, Kind.SHIELD, s.color(), ally.position(), 1.1f, 30);
        }
        return true;
    }

    static boolean radiantCharge(ServerPlayer p, Skill s, int l) {
        Vec3 start = p.position();
        Vec3 dir = facing(p);
        sound(p, SoundEvents.ARMOR_EQUIP_GOLD.value(), 1f, 0.7f);
        fx(p, Kind.SHIELD, s.color(), start, 1.2f, 12);
        fx(p, Kind.DASH, s.color(), start, 1, 8);
        dash(p, dir, 8, 6, 2.0, e -> {
            hit(p, s, e, 9 * m(l, tier(p)) + vit(p, 0.2f));
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            shove(p, dir, e, 0.9, 0.35);
            fx(e, Kind.SPLASH, s.color(), mid(e), 1.6f, 1);
            sound(e, SoundEvents.SHIELD_BLOCK, 0.9f, 0.8f);
        }, null);
        return true;
    }

    static boolean layOnHands(ServerPlayer p, Skill s, int l) {
        LivingEntity seen = aim(p, 10, x -> x instanceof Player && x != p && x.isAlive());
        Player ally = seen instanceof Player other ? other : p;
        com.beyondsolo.system.Balance.healRaw(ally, ally.getMaxHealth() * handsHeal(l) / 100f);
        SubclassActions.cleanse(ally);
        sound(ally, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.5f, 1.8f);
        fx(ally, Kind.HEAL, s.color(), ally.position(), 1.4f, 0);
        fxAt(p.serverLevel(), Kind.GEYSER, s.color(), ally.position(), 3, 2);
        return true;
    }

    static boolean divineVerdict(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 20, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        Vec3 at = target.position();
        float dmg = 16 * m(l, tier(p)) + vit(p, 0.3f);
        sound(level, at, SoundEvents.BELL_BLOCK, 1f, 0.6f);
        fxAt(level, Kind.THUNDER, s.color(), at, 4.5f, 0);
        hit(p, s, target, undead(target) ? dmg * 1.5f : dmg);
        stun(target, 30);
        for (LivingEntity e : foes(p, at, 3)) if (e != target) magic(p, s, e, dmg * 0.4f);
        // the verdict stands where it fell: a column of light over the judged, the ground scorched round them
        fxAt(level, Kind.CRACK, s.color(), at, 4.5f, 0);
        fxAt(level, Kind.HEAL, s.color(), at, 1.8f, 0);
        fxAt(level, Kind.NOVA, s.color(), at, 5, 3);
        return true;
    }

    static boolean dawnbreaker(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position().add(facing(p).scale(3));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 3, false, false, false));
        sound(p, SoundEvents.BELL_RESONATE, 1f, 0.6f);
        fx(p, Kind.HAMMER, s.color(), ground(level, center), 3, 30);
        later(p, 30, () -> {
            Vec3 at = ground(level, center);
            for (LivingEntity e : foes(p, at, 10)) {
                float dmg = 45 * m(l, tier(p)) + vit(p, 0.7f);
                hit(p, s, e, undead(e) ? dmg * 1.5f : dmg);
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                push(e, at, 1.1, 0.5);
            }
            for (Player ally : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(at, at).inflate(10), Player::isAlive)) {
                com.beyondsolo.system.Balance.healRaw(ally, ally.getMaxHealth() * 0.3f);
                fx(ally, Kind.HEAL, s.color(), ally.position(), 1, 0);
            }
            sound(level, at, SoundEvents.ANVIL_LAND, 1.2f, 0.5f);
            sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.2f);
            fxAt(level, Kind.PULSE, s.color(), at, 10, 0);
            fxAt(level, Kind.ROAR, 0xFFFFFF, at, 10, 0);
            fxAt(level, Kind.CRACK, s.color(), at, 10, 0);
            fxAt(level, Kind.GEYSER, s.color(), at, 6, 2);
            SkillParticles.send(level, ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 60, 3, 1, 3, 0.1);
        });
        return true;
    }

    // ================================================================ ironwall sentinel

    static float thorns(int l) { return 1 + 0.5f * l; }
    static int braceTicks(int l) { return 60 + 4 * l; }

    public static final Skill SHIELD_SLAM = active("shield_slam", IS, A, 22, 0xB8B8C8, 12, 120, 70,
            GuardianPath::shieldSlam, (l, t) -> a(f(9 * m(l, t))));
    public static final Skill RAMPART = active("rampart", IS, A, 24, 0x9A9AA8, 18, 500, 320,
            GuardianPath::rampart, (l, t) -> a(f(6 * m(l, t))));
    public static final Skill HEAVY_PLATING = passive("heavy_plating", IS, A, 26, 0xC8C8D8, (l, t) -> a(f(0.8f * l), f(5 * l)));
    public static final Skill IRON_TETHER = active("iron_tether", IS, A, 29, 0xA0A0B0, 14, 300, 180,
            GuardianPath::ironTether, (l, t) -> a(f(6 * m(l, t))));
    public static final Skill BRACE = active("brace", IS, A, 32, 0xD0D0E0, 16, 400, 260,
            GuardianPath::brace, (l, t) -> a(f(braceTicks(l) / 20f), f(5 * m(l, t))));
    public static final Skill GARRISON = passive("garrison", IS, A, 35, 0x8888A0, (l, t) -> a(f(1.5f * l)));
    public static final Skill TREMOR_MARCH = active("tremor_march", IS, A, 38, 0xB0A890, 22, 420, 260,
            GuardianPath::tremorMarch, (l, t) -> a(f(5 * m(l, t))));
    public static final Skill SHIELD_TOSS = active("shield_toss", IS, A, 42, 0xC0C0D0, 20, 320, 200,
            GuardianPath::shieldToss, (l, t) -> a(f(9 * m(l, t))));
    public static final Skill SPIKED_BULWARK = passive("spiked_bulwark", IS, A, 46, 0xA8A8C0, (l, t) -> a(f(thorns(l))));
    public static final Skill CITADEL = active("citadel", IS, S, 50, 0xD8D8F0, 45, 1800, 1200,
            GuardianPath::citadel, (l, t) -> a(f(6 * m(l, t))));

    static boolean shieldSlam(ServerPlayer p, Skill s, int l) {
        Vec3 at = p.position();
        for (LivingEntity e : cone(p, 4.5, 0.4)) {
            hit(p, s, e, 9 * m(l, tier(p)) + vit(p, 0.25f));
            stun(e, 15);
            push(e, at, 1.1, 0.35);
            taunt(p, e);
        }
        sound(p, SoundEvents.SHIELD_BLOCK, 1.2f, 0.6f);
        sound(p, SoundEvents.ANVIL_LAND, 0.4f, 1.2f);
        fx(p, Kind.SHIELD, s.color(), at, 1.4f, 7);
        debris(p.serverLevel(), at.add(facing(p).scale(2)), 14, 0.6);
        // the shield meets them like a wall
        fxAt(p.serverLevel(), Kind.SLAM, s.color(), at.add(facing(p).scale(2)), 3.2f, 0);
        for (LivingEntity e : cone(p, 5.5, 0.4)) fx(e, Kind.SPLASH, s.color(), mid(e), 1.6f, 1);
        return true;
    }

    static boolean rampart(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 dir = facing(p);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 center = ground(level, p.position().add(dir.scale(3.5)));
        Vec3 a = center.add(side.scale(-3.5)), b = center.add(side.scale(3.5));
        sound(level, center, SoundEvents.STONE_PLACE, 1.4f, 0.5f);
        sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 1.5f);
        fxAt(level, Kind.CRACK, s.color(), center, 4, 0);
        for (int i = -3; i <= 3; i++) {
            Vec3 pt = ground(level, center.add(side.scale(i)));
            // three courses of masonry, rising one after the other; a slab stands four seconds, so each is laid twice
            for (int row = 0; row < 3; row++) {
                Vec3 at = pt.add(0, row, 0);
                later(p, row * 2, () -> slab(level, MASONRY, at));
                later(p, 80 + row * 2, () -> slab(level, MASONRY, at));
            }
            debris(level, pt, 12, 0.4);
        }
        for (LivingEntity e : along(p, a.add(0, 1, 0), b.add(0, 1, 0), 1.6)) {
            hit(p, s, e, 6 * m(l, tier(p)) + vit(p, 0.15f));
            launch(e, 0.6);
        }
        // nothing walks through the wall
        repeat(p, 160, 4, () -> {
            for (LivingEntity e : along(p, a.add(0, 1, 0), b.add(0, 1, 0), 1.0)) {
                Vec3 away = flat(e.position().subtract(p.position()), e);
                e.setDeltaMovement(away.x * 0.6, 0.15, away.z * 0.6);
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
            }
        });
        return true;
    }

    static boolean ironTether(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 16, x -> canHit(p, x));
        if (target == null) return false;
        hit(p, s, target, 6 * m(l, tier(p)) + vit(p, 0.15f));
        pull(target, p.position().add(facing(p).scale(1.5)), 1.5);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
        taunt(p, target);
        sound(p, SoundEvents.CHAIN_PLACE, 1.4f, 0.6f);
        sound(target, SoundEvents.CHAIN_BREAK, 1.2f, 0.7f);
        fx(p, Kind.CHAIN, s.color(), mid(target), 1.8f, 12);
        return true;
    }

    static boolean brace(ServerPlayer p, Skill s, int l) {
        int ticks = braceTicks(l);
        SkillAPI.setBuff(p, BUFF_BRACE, ticks);
        sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.2f, 0.7f);
        fx(p, Kind.SHIELD, s.color(), p.position(), 1.5f, ticks);
        return true;
    }

    static boolean tremorMarch(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        for (int i = 0; i < 4; i++) {
            later(p, i * 10, () -> {
                Vec3 at = p.position();
                for (LivingEntity e : foes(p, at, 4.5)) {
                    hit(p, s, e, 5 * m(l, tier(p)) + vit(p, 0.12f));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
                    taunt(p, e);
                }
                sound(p, SoundEvents.RAVAGER_STEP, 1.4f, 0.6f);
                fxAt(level, Kind.QUAKE, s.color(), at, 4.5f, 8);
                debris(level, at, 16, 1.6);
            });
        }
        return true;
    }

    static boolean shieldToss(ServerPlayer p, Skill s, int l) {
        LivingEntity first = aim(p, 18, x -> canHit(p, x));
        if (first == null) return false;
        Set<Integer> struck = new HashSet<>();
        sound(p, SoundEvents.TRIDENT_THROW.value(), 1f, 0.8f);
        bounce(p, s, l, p, first, struck, 0);
        return true;
    }

    private static void bounce(ServerPlayer p, Skill s, int l, LivingEntity from, LivingEntity to, Set<Integer> struck, int n) {
        if (!to.isAlive()) return;
        struck.add(to.getId());
        fx(from, Kind.DISCUS, s.color(), mid(to), 1.5f, 4);
        later(p, 4, () -> {
            if (!to.isAlive()) return;
            hit(p, s, to, 9 * m(l, tier(p)) + vit(p, 0.2f));
            stun(to, 10);
            fx(to, Kind.SPLASH, s.color(), mid(to), 1.4f, 1);
            sound(to, SoundEvents.SHIELD_BLOCK, 1f, 1.1f + 0.1f * n);
            LivingEntity next = n >= 4 ? null : foes(p, to.position(), 7).stream().filter(e -> !struck.contains(e.getId()))
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(to))).orElse(null);
            if (next != null) bounce(p, s, l, to, next, struck, n + 1);
            // it comes back
            else fx(to, Kind.DISCUS, s.color(), mid(p), 1.5f, 6);
        });
    }

    static boolean citadel(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        SkillAPI.setBuff(p, BUFF_CITADEL, 200);
        sound(p, SoundEvents.ANVIL_PLACE, 1.2f, 0.4f);
        sound(p, SoundEvents.IRON_GOLEM_REPAIR, 1.2f, 0.5f);
        fx(p, Kind.SHIELD, s.color(), center, 1.8f, 200);
        for (int i = 0; i < 12; i++) {
            double ang = i * Math.PI / 6;
            Vec3 at = ground(level, center.add(Math.cos(ang) * 2.6, 0, Math.sin(ang) * 2.6));
            // a slab stands four seconds
            repeat(p, 180, 60, () -> slab(level, MASONRY, at));
        }
        fxAt(level, Kind.CRACK, s.color(), center, 8, 0);
        debris(level, center, 40, 3);
        repeat(p, 200, 20, () -> {
            for (Player ally : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(center, center).inflate(8), Player::isAlive)) {
                if (ally != p) ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 2, false, false, true));
            }
            for (LivingEntity e : foes(p, center, 8)) {
                hit(p, s, e, 6 * m(l, tier(p)) + vit(p, 0.1f));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                taunt(p, e);
            }
            fxAt(level, Kind.QUAKE, s.color(), center, 8, 10);
        });
        return true;
    }

    // ================================================================ spellward warden

    static float weaveMana(int l) { return 4 * l; }
    static float runicMana(int l) { return 1 + 0.3f * l; }
    static int mirrorTicks(int l) { return 80 + 4 * l; }

    public static final Skill RUNE_STRIKE = active("rune_strike", SW, A, 22, 0x7FB0FF, 12, 120, 70,
            GuardianPath::runeStrike, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill NULL_FIELD = active("null_field", SW, A, 24, 0x5A88E0, 20, 500, 320,
            GuardianPath::nullField, (l, t) -> a(f(2 * m(l, t))));
    public static final Skill MANA_WEAVE = passive("mana_weave", SW, A, 26, 0x90B8FF, (l, t) -> a(f(weaveMana(l))));
    public static final Skill MIRROR_WARD = active("mirror_ward", SW, A, 29, 0xA0C8FF, 18, 500, 320,
            GuardianPath::mirrorWard, (l, t) -> a(f(mirrorTicks(l) / 20f), f(6 * m(l, t))));
    public static final Skill WARDING_BOLT = active("warding_bolt", SW, A, 32, 0x6FA8FF, 14, 160, 90,
            GuardianPath::wardingBolt, (l, t) -> a(f(10 * m(l, t))));
    public static final Skill RUNIC_ARMOR = passive("runic_armor", SW, A, 35, 0x80A0E8, (l, t) -> a(f(runicMana(l))));
    public static final Skill BINDING_GLYPH = active("binding_glyph", SW, A, 38, 0x88A0FF, 20, 420, 260,
            GuardianPath::bindingGlyph, (l, t) -> a(f(12 * m(l, t))));
    public static final Skill PURGE = active("purge", SW, A, 42, 0xB0D0FF, 22, 500, 320,
            GuardianPath::purge, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill SPELL_EATER = passive("spell_eater", SW, A, 46, 0x6080D0, (l, t) -> a(f(3 * l)));
    public static final Skill ARCANUM = active("arcanum", SW, S, 50, 0x8FB8FF, 45, 1800, 1200,
            GuardianPath::arcanum, (l, t) -> a(f(8 * m(l, t)), f(30 * m(l, t))));

    static boolean runeStrike(ServerPlayer p, Skill s, int l) {
        List<LivingEntity> hits = cone(p, 4.5, 0.4);
        for (LivingEntity e : hits) {
            magic(p, s, e, 8 * m(l, tier(p)) + vit(p, 0.2f));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
            fx(e, Kind.BIND, s.color(), e.position(), 1, 16);
        }
        if (!hits.isEmpty()) SystemAPI.restoreMana(p, 4);
        sound(p, SoundEvents.AMETHYST_BLOCK_HIT, 1.6f, 0.7f);
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        fx(p, Kind.SLASH, s.color(), p.position(), 3.6f, 0);
        // the rune the blade carries is cut into what it hits, and into the ground before them
        for (LivingEntity e : hits) fx(e, Kind.SPLASH, s.color(), mid(e), 1.6f, 1);
        fxAt(p.serverLevel(), Kind.CRACK, s.color(), p.position().add(facing(p).scale(2.5)), 2.6f, 0);
        return true;
    }

    static boolean nullField(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        sound(p, SoundEvents.BEACON_DEACTIVATE, 1f, 1.4f);
        fxAt(level, Kind.DOME, s.color(), center.add(0, 2, 0), 5, 120);
        repeat(p, 120, 10, () -> {
            for (LivingEntity e : foes(p, center, 5)) {
                magic(p, s, e, 2 * m(l, tier(p)));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
            }
        });
        return true;
    }

    static boolean mirrorWard(ServerPlayer p, Skill s, int l) {
        int ticks = mirrorTicks(l);
        SkillAPI.setBuff(p, BUFF_MIRROR_WARD, ticks);
        sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, 1.2f);
        fx(p, Kind.SHIELD, s.color(), p.position(), 1.5f, ticks);
        fx(p, Kind.BLADES, s.color(), p.position(), 1.3f, ticks);
        return true;
    }

    static boolean wardingBolt(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 18, x -> canHit(p, x));
        if (target == null) return false;
        magic(p, s, target, 10 * m(l, tier(p)) + vit(p, 0.2f));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120, 0, false, false, true));
        sound(p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.6f, 1.2f);
        fx(p, Kind.BEAM, s.color(), mid(target), 0.45f, 6);
        fx(target, Kind.SPLASH, s.color(), mid(target), 2.1f, 1);
        fx(target, Kind.NOVA, s.color(), target.position(), 2.6f, 3);
        return true;
    }

    static boolean bindingGlyph(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 18);
        sound(level, at, SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 0.7f);
        fxAt(level, Kind.ZONE, s.color(), at, 3, 200);
        boolean[] sprung = {false};
        repeat(p, 200, 5, () -> {
            if (sprung[0] || foes(p, at, 3).isEmpty()) return;
            sprung[0] = true;
            for (LivingEntity e : foes(p, at, 4)) {
                magic(p, s, e, 12 * m(l, tier(p)) + vit(p, 0.25f));
                stun(e, 50);
                fx(e, Kind.BIND, s.color(), e.position(), 1.2f, 50);
            }
            sound(level, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 2f, 0.6f);
            fxAt(level, Kind.GEYSER, s.color(), at, 4, 2);
            fxAt(level, Kind.PULSE, s.color(), at, 4, 0);
        });
        return true;
    }

    static boolean purge(ServerPlayer p, Skill s, int l) {
        Vec3 at = p.position();
        for (LivingEntity e : foes(p, at, 8)) {
            magic(p, s, e, 8 * m(l, tier(p)) + vit(p, 0.15f));
            for (MobEffectInstance fx : new ArrayList<>(e.getActiveEffects())) {
                if (fx.getEffect().value().isBeneficial()) e.removeEffect(fx.getEffect());
            }
        }
        for (Player ally : allies(p, 8)) {
            SubclassActions.cleanse(ally);
            fx(ally, Kind.HEAL, s.color(), ally.position(), 0.8f, 0);
        }
        sound(p, SoundEvents.BEACON_DEACTIVATE, 1f, 1.8f);
        fx(p, Kind.PULSE, s.color(), at, 8, 0);
        fx(p, Kind.ROAR, 0xFFFFFF, at, 8, 0);
        return true;
    }

    static boolean arcanum(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        SkillAPI.setBuff(p, BUFF_ARCANUM, 160);
        sound(p, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.2f);
        fx(p, Kind.DOME, s.color(), p.position(), 2.2f, 160);
        fx(p, Kind.BLADES, s.color(), p.position(), 2.4f, 160);
        repeat(p, 160, 20, () -> {
            for (LivingEntity e : foes(p, p.position(), 6)) {
                magic(p, s, e, 8 * m(l, tier(p)) + vit(p, 0.12f));
                fx(p, Kind.BOLT, s.color(), mid(e), 0.3f, 0);
                taunt(p, e);
            }
            for (Player ally : allies(p, 8)) if (ally != p) ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 1, false, false, true));
        });
        later(p, 160, () -> {
            Vec3 at = p.position();
            for (LivingEntity e : foes(p, at, 9)) {
                magic(p, s, e, 30 * m(l, tier(p)) + vit(p, 0.5f));
                push(e, at, 1.2, 0.5);
            }
            sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 1.5f);
            sound(p, SoundEvents.AMETHYST_CLUSTER_BREAK, 2f, 0.5f);
            fx(p, Kind.PULSE, s.color(), at, 9, 0);
            fx(p, Kind.ROAR, 0xFFFFFF, at, 9, 0);
        });
        return true;
    }

    // ================================================================ vengeance knight

    static float brandBonus(int l) { return 10 + 2 * l; }
    static float wrathBonus(int l) { return 20 + 3 * l; }

    public static final Skill SEARING_BRAND = active("searing_brand", VK, A, 22, 0xFF7A3A, 12, 140, 80,
            GuardianPath::searingBrand, (l, t) -> a(f(9 * m(l, t)), f(brandBonus(l))));
    public static final Skill VENGEFUL_LEAP = active("vengeful_leap", VK, A, 24, 0xFF8A40, 16, 260, 160,
            GuardianPath::vengefulLeap, (l, t) -> a(f(10 * m(l, t))));
    public static final Skill GRUDGE = passive("grudge", VK, A, 26, 0xE05020, (l, t) -> a(f(4 * l)));
    public static final Skill CINDER_CHAIN = active("cinder_chain", VK, A, 29, 0xFF6020, 14, 280, 170,
            GuardianPath::cinderChain, (l, t) -> a(f(7 * m(l, t))));
    public static final Skill WRATH = active("wrath", VK, A, 32, 0xFF4A1A, 18, 500, 320,
            GuardianPath::wrath, (l, t) -> a(f(wrathBonus(l)), f(3 * m(l, t))));
    public static final Skill ASHEN_HIDE = passive("ashen_hide", VK, A, 35, 0xA07060, (l, t) -> a(f(5 * l), f(0.4f * l)));
    public static final Skill EYE_FOR_AN_EYE = active("eye_for_an_eye", VK, A, 38, 0xFF9050, 20, 400, 260,
            GuardianPath::eyeForAnEye, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill FUNERAL_PYRE = active("funeral_pyre", VK, A, 42, 0xFF6A2A, 22, 420, 260,
            GuardianPath::funeralPyre, (l, t) -> a(f(4 * m(l, t))));
    public static final Skill BLOOD_DEBT = passive("blood_debt", VK, A, 46, 0xD03020, (l, t) -> a(f(3 * l)));
    public static final Skill DAY_OF_RECKONING = active("day_of_reckoning", VK, S, 50, 0xFF5A20, 45, 1500, 1000,
            GuardianPath::dayOfReckoning, (l, t) -> a(f(14 * m(l, t)), f(28 * m(l, t))));

    private static void ignite(ServerPlayer p, LivingEntity e, float amount) {
        Statuses.apply(e, DamageElement.FIRE, amount, p);
        e.igniteForSeconds(4);
    }

    static boolean searingBrand(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 5, x -> canHit(p, x));
        if (target == null) return false;
        hit(p, s, target, 9 * m(l, tier(p)) + vit(p, 0.2f));
        ignite(p, target, 30 + 4 * l);
        BRANDS.put(target, new long[]{p.level().getGameTime() + 120, Math.round(brandBonus(l)), p.getId()});
        taunt(p, target);
        sound(p, SoundEvents.FIRECHARGE_USE, 0.8f, 1.3f);
        sound(target, SoundEvents.LAVA_EXTINGUISH, 0.6f, 1.4f);
        fx(p, Kind.SLASH, s.color(), p.position(), 2.6f, 0);
        fx(target, Kind.MARK, s.color(), target.position(), target.getBbHeight(), 120);
        return true;
    }

    static boolean vengefulLeap(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 to = aimGround(p, 12);
        sound(p, SoundEvents.RAVAGER_ATTACK, 0.8f, 1.2f);
        leap(p, to, 0.75, land -> {
            for (LivingEntity e : foes(p, land, 4)) {
                hit(p, s, e, 10 * m(l, tier(p)) + vit(p, 0.25f));
                ignite(p, e, 30 + 3 * l);
                taunt(p, e);
            }
            sound(level, land, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.2f);
            fxAt(level, Kind.CRACK, s.color(), land, 4, 0);
            SkillParticles.send(level, ParticleTypes.FLAME, land.x, land.y + 0.3, land.z, 24, 1.4, 0.2, 1.4, 0.04);
            SkillParticles.send(level, DIRT, land.x, land.y + 0.3, land.z, 16, 1.2, 0.2, 1.2, 0.15);
        });
        return true;
    }

    static boolean cinderChain(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 14, x -> canHit(p, x));
        if (target == null) return false;
        hit(p, s, target, 7 * m(l, tier(p)) + vit(p, 0.15f));
        ignite(p, target, 30 + 3 * l);
        pull(target, p.position().add(facing(p).scale(1.5)), 1.5);
        taunt(p, target);
        sound(p, SoundEvents.CHAIN_PLACE, 1.4f, 0.8f);
        sound(target, SoundEvents.BLAZE_SHOOT, 0.6f, 1.4f);
        fx(p, Kind.CHAIN, s.color(), mid(target), 1.8f, 12);
        return true;
    }

    static boolean wrath(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        SkillAPI.setBuff(p, BUFF_WRATH, 160);
        sound(p, SoundEvents.RAVAGER_ROAR, 0.8f, 1.3f);
        fx(p, Kind.ROAR, s.color(), p.position(), 3, 0);
        repeat(p, 160, 8, () -> SkillParticles.send(level, ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 3, 0.3, 0.5, 0.3, 0.01));
        return true;
    }

    static boolean eyeForAnEye(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        SkillAPI.setBuff(p, BUFF_EYE, 100);
        SWALLOWED.put(p.getUUID(), new float[]{0});
        sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.2f, 0.5f);
        fx(p, Kind.SHIELD, s.color(), p.position(), 1.3f, 100);
        later(p, 100, () -> {
            float[] kept = SWALLOWED.remove(p.getUUID());
            // what was swallowed is already in today's sizes (its cap grows with the hunter's health pool)
            float stored = kept == null ? 0 : com.beyondsolo.system.Balance.keepSize(p,
                    Math.min(60 * (float) com.beyondsolo.system.Balance.legacyScale(p), kept[0] * 1.5f));
            Vec3 at = p.position();
            for (LivingEntity e : foes(p, at, 6)) {
                hit(p, s, e, 8 * m(l, tier(p)) + vit(p, 0.2f) + stored);
                ignite(p, e, 40);
                push(e, at, 1.0, 0.4);
            }
            sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1f, 1.1f);
            fx(p, Kind.NOVA, s.color(), at, 6, 0);
            SkillParticles.send(level, ParticleTypes.FLAME, at.x, at.y + 0.5, at.z, 40, 2, 0.4, 2, 0.06);
        });
        return true;
    }

    static boolean funeralPyre(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 18);
        sound(level, at, SoundEvents.FIRECHARGE_USE, 1.2f, 0.5f);
        repeat(p, 80, 16, () -> fxAt(level, Kind.GEYSER, s.color(), at, 5, 0));
        repeat(p, 80, 4, () -> SkillParticles.send(level, ParticleTypes.FLAME, at.x, at.y + 1.5, at.z, 10, 0.8, 1.5, 0.8, 0.02));
        repeat(p, 80, 10, () -> {
            for (LivingEntity e : foes(p, at, 3)) {
                hit(p, s, e, 4 * m(l, tier(p)) + vit(p, 0.08f));
                ignite(p, e, 25 + 2 * l);
            }
        });
        return true;
    }

    static boolean dayOfReckoning(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        List<LivingEntity> near = new ArrayList<>(foes(p, center, 12));
        if (near.isEmpty()) return false;
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        List<LivingEntity> judged = near.subList(0, Math.min(6, near.size()));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 2, false, false, false));
        SkillAPI.setBuff(p, BUFF_WRATH, 200);
        sound(p, SoundEvents.RAVAGER_ROAR, 1.2f, 0.7f);
        fx(p, Kind.ROAR, s.color(), center, 6, 0);
        for (int i = 0; i < judged.size(); i++) {
            LivingEntity e = judged.get(i);
            fx(e, Kind.MARK, s.color(), e.position(), e.getBbHeight(), 20 + i * 6);
            later(p, 14 + i * 6, () -> {
                if (!e.isAlive()) return;
                Vec3 at = e.position();
                fxAt(level, Kind.CLEAVE, s.color(), at, 2.6f, 6);
                later(p, 6, () -> {
                    for (LivingEntity x : foes(p, at, 2.6)) {
                        hit(p, s, x, 14 * m(l, tier(p)) + vit(p, 0.25f));
                        ignite(p, x, 50);
                    }
                    sound(level, at, SoundEvents.ANVIL_LAND, 0.8f, 0.6f);
                    fxAt(level, Kind.GEYSER, s.color(), at, 3.5f, 0);
                });
            });
        }
        later(p, 26 + judged.size() * 6, () -> {
            for (LivingEntity e : foes(p, center, 10)) {
                hit(p, s, e, 28 * m(l, tier(p)) + vit(p, 0.5f));
                ignite(p, e, 80);
                launch(e, 0.8);
            }
            sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.4f, 0.7f);
            fxAt(level, Kind.NOVA, s.color(), center, 10, 0);
            fxAt(level, Kind.CRACK, s.color(), center, 10, 0);
            SkillParticles.send(level, ParticleTypes.LAVA, center.x, center.y + 0.5, center.z, 20, 3, 0.3, 3, 0);
        });
        return true;
    }

    // ================================================================ everlasting colossus

    static float rootsRegen(int l) { return 0.15f * l; }

    public static final Skill STONE_FIST = active("stone_fist", EC, S, 22, 0xD0A860, 14, 120, 70,
            GuardianPath::stoneFist, (l, t) -> a(f(12 * m(l, t))));
    public static final Skill BEDROCK_SKIN = active("bedrock_skin", EC, S, 24, 0xB09060, 18, 500, 320,
            GuardianPath::bedrockSkin, (l, t) -> a(f(4 + 4 * (1 + l / 3))));
    public static final Skill TITANIC_FRAME = passive("titanic_frame", EC, S, 26, 0xE0C080, (l, t) -> a(f(5 * l), f(10 * l)));
    public static final Skill LANDSLIDE = active("landslide", EC, S, 29, 0xC09850, 18, 280, 170,
            GuardianPath::landslide, (l, t) -> a(f(12 * m(l, t))));
    public static final Skill MONOLITH = active("monolith", EC, S, 32, 0xD8B870, 20, 420, 260,
            GuardianPath::monolith, (l, t) -> a(f(10 * m(l, t))));
    public static final Skill DEEP_ROOTS = passive("deep_roots", EC, S, 35, 0xA8C070, (l, t) -> a(f(rootsRegen(l))));
    public static final Skill GRASP_OF_THE_EARTH = active("grasp_of_the_earth", EC, S, 38, 0xB8A060, 22, 460, 300,
            GuardianPath::graspOfTheEarth, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill STONEWAKE = active("stonewake", EC, S, 42, 0xC8A858, 22, 380, 240,
            GuardianPath::stonewake, (l, t) -> a(f(12 * m(l, t))));
    public static final Skill MOUNTAIN_HEART = passive("mountain_heart", EC, S, 46, 0xE8D090, (l, t) -> a(f(2.5f * l)));
    public static final Skill PILLARS_OF_CREATION = active("pillars_of_creation", EC, S, 50, 0xE0B050, 45, 1800, 1200,
            GuardianPath::pillarsOfCreation, (l, t) -> a(f(16 * m(l, t)), f(45 * m(l, t))));

    static boolean stoneFist(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        Vec3 front = ground(level, at.add(facing(p).scale(3)));
        for (LivingEntity e : cone(p, 6, 0.45)) {
            hit(p, s, e, 12 * m(l, tier(p)) + vit(p, 0.3f));
            push(e, at, 1.6, 0.45);
        }
        sound(p, SoundEvents.IRON_GOLEM_ATTACK, 1.2f, 0.6f);
        fxAt(level, Kind.CRACK, s.color(), front, 3.5f, 0);
        debris(level, front, 30, 1.2);
        return true;
    }

    static boolean bedrockSkin(ServerPlayer p, Skill s, int l) {
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 160, 1 + l / 3, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 160, 0, false, false, true));
        sound(p, SoundEvents.STONE_PLACE, 1.4f, 0.4f);
        sound(p, SoundEvents.IRON_GOLEM_REPAIR, 1f, 0.6f);
        debris(p.serverLevel(), p.position().add(0, 0.8, 0), 40, 0.5);
        fx(p, Kind.SHIELD, s.color(), p.position(), 1.3f, 20);
        return true;
    }

    static boolean landslide(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 start = p.position();
        Vec3 dir = facing(p);
        Set<Integer> struck = new HashSet<>();
        sound(p, SoundEvents.RAVAGER_STEP, 1.4f, 0.5f);
        for (int i = 0; i < 8; i++) {
            final int k = i;
            later(p, i * 2, () -> {
                Vec3 at = ground(level, start.add(dir.scale(1.5 * k + 1.5)));
                for (LivingEntity e : foes(p, at, 1.9)) {
                    if (!struck.add(e.getId())) continue;
                    hit(p, s, e, 12 * m(l, tier(p)) + vit(p, 0.25f));
                    launch(e, 0.7);
                }
                fxAt(level, Kind.GEYSER, k % 2 == 0 ? s.color() : 0x8A7A60, at, 2.2f + 0.2f * k, 1);
                debris(level, at, 14, 0.7);
                if (k % 2 == 0) sound(level, at, SoundEvents.STONE_BREAK, 1.4f, 0.5f);
            });
        }
        return true;
    }

    static boolean monolith(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 18);
        sound(level, at, SoundEvents.STONE_PLACE, 1.6f, 0.4f);
        fxAt(level, Kind.CRACK, s.color(), at, 3, 0);
        later(p, 5, () -> {
            for (LivingEntity e : foes(p, at, 3)) {
                hit(p, s, e, 10 * m(l, tier(p)) + vit(p, 0.25f));
                launch(e, 1.2);
                stun(e, 40);
            }
            sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 0.7f);
            column(p, level, ROCK, at, 5);
            fxAt(level, Kind.GEYSER, s.color(), at, 6, 1);
            debris(level, at.add(0, 1.5, 0), 50, 0.8);
        });
        return true;
    }

    static boolean graspOfTheEarth(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        for (LivingEntity e : foes(p, at, 8)) {
            hit(p, s, e, 8 * m(l, tier(p)) + vit(p, 0.2f));
            stun(e, 60);
            taunt(p, e);
            fx(e, Kind.BIND, s.color(), e.position(), 1.2f, 60);
            debris(level, e.position(), 12, 0.4);
        }
        sound(p, SoundEvents.ROOTED_DIRT_BREAK, 1.6f, 0.5f);
        fxAt(level, Kind.CRACK, s.color(), at, 8, 0);
        return true;
    }

    static boolean stonewake(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 dir = facing(p);
        sound(p, SoundEvents.RAVAGER_ROAR, 0.7f, 0.8f);
        repeat(p, 8, 2, () -> {
            fxAt(level, Kind.QUAKE, s.color(), p.position(), 2.5f, 8);
            debris(level, p.position(), 14, 0.8);
        });
        dash(p, dir, 10, 8, 2.3, e -> {
            hit(p, s, e, 12 * m(l, tier(p)) + vit(p, 0.25f));
            shove(p, dir, e, 1.3, 0.5);
            sound(e, SoundEvents.IRON_GOLEM_ATTACK, 0.9f, 0.7f);
        }, () -> fxAt(level, Kind.CRACK, s.color(), p.position(), 4, 0));
        return true;
    }

    static boolean pillarsOfCreation(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 70, 3, false, false, false));
        sound(p, SoundEvents.WARDEN_ROAR, 0.8f, 0.6f);
        fxAt(level, Kind.QUAKE, s.color(), center, 12, 64);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            Vec3 at = ground(level, center.add(Math.cos(ang) * 6.5, 0, Math.sin(ang) * 6.5));
            later(p, 6 + i * 5, () -> {
                for (LivingEntity e : foes(p, at, 3)) {
                    hit(p, s, e, 16 * m(l, tier(p)) + vit(p, 0.25f));
                    launch(e, 0.9);
                }
                sound(level, at, SoundEvents.STONE_BREAK, 1.6f, 0.4f);
                column(p, level, ROCK, at, 6);
                fxAt(level, Kind.GEYSER, s.color(), at, 7, 1);
                fxAt(level, Kind.CRACK, s.color(), at, 3, 0);
                debris(level, at.add(0, 2, 0), 30, 0.7);
            });
        }
        later(p, 56, () -> {
            for (LivingEntity e : foes(p, center, 10)) {
                hit(p, s, e, 45 * m(l, tier(p)) + vit(p, 0.8f));
                launch(e, 1.3);
                stun(e, 60);
            }
            sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.6f, 0.5f);
            fxAt(level, Kind.GEYSER, s.color(), center, 12, 1);
            fxAt(level, Kind.ROAR, s.color(), center, 10, 0);
            fxAt(level, Kind.CRACK, s.color(), center, 12, 0);
            debris(level, center.add(0, 2, 0), 120, 3);
        });
        return true;
    }

    // ================================================================ passives

    static {
        bonus(HEAVY_PLATING, Attributes.ARMOR, 0.8f, ADD, "armor");
        bonus(HEAVY_PLATING, Attributes.KNOCKBACK_RESISTANCE, 0.05f, ADD, "knockback");
        bonus(ASHEN_HIDE, Attributes.ARMOR, 0.4f, ADD, "armor");
        bonus(TITANIC_FRAME, Attributes.MAX_HEALTH, 0.05f, TOTAL, "health");
        bonus(TITANIC_FRAME, Attributes.KNOCKBACK_RESISTANCE, 0.1f, ADD, "knockback");
    }

    /** More mana from the Spellward Warden's weave. */
    static float manaBonus(SkillData data) {
        return weaveMana(data.level(MANA_WEAVE.path()));
    }

    /** Extra damage, in percent, when a guardian subclass player strikes. */
    static float strikeBonus(ServerPlayer p, SkillData data, LivingEntity target, long now) {
        float bonus = 0;
        if (data.hasBuff(BUFF_GRUDGE, now)) {
            bonus += 4 * Math.max(1, data.level(GRUDGE.path()));
            data.clearBuff(BUFF_GRUDGE);
        }
        if (data.hasBuff(BUFF_WRATH, now)) bonus += wrathBonus(Math.max(1, data.level(WRATH.path())));
        int debt = data.level(BLOOD_DEBT.path());
        if (debt > 0 && p.getHealth() < p.getMaxHealth() * 0.4f) bonus += 3 * debt;
        if (!BRANDS.isEmpty()) {
            long[] brand = BRANDS.get(target);
            if (brand != null) {
                if (brand[0] <= now) BRANDS.remove(target);
                else if (brand[2] == p.getId()) bonus += brand[1];
            }
        }
        return bonus;
    }

    static void afterStrike(ServerPlayer p, SkillData data, LivingEntity target, boolean melee, long now) {
        int zeal = data.level(ZEAL.path());
        if (zeal > 0 && melee) p.heal(zealHeal(zeal));
        if (melee && data.hasBuff(BUFF_WRATH, now)) {
            int wrath = Math.max(1, data.level(WRATH.path()));
            ServerLevel level = p.serverLevel();
            Vec3 at = target.position();
            // the splash goes out next tick, so it cannot feed itself
            later(p, 1, () -> {
                for (LivingEntity e : foes(p, at, 2.5)) {
                    if (e == target) continue;
                    e.hurt(p.damageSources().onFire(), com.beyondsolo.system.Balance.flatDamage(p, 3 * m(wrath, tier(p))));
                    e.igniteForSeconds(3);
                }
                SkillParticles.send(level, ParticleTypes.FLAME, at.x, at.y + 0.8, at.z, 10, 0.8, 0.4, 0.8, 0.03);
            });
        }
    }

    /** A guardian subclass player is struck. Returns the damage to take. */
    static float whenStruck(ServerPlayer p, SkillData data, LivingEntity attacker, boolean melee, DamageSource source, float amount, long now) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        boolean spell = spell(source);
        if (data.level(GRUDGE.path()) > 0 && attacker != null) data.setBuff(BUFF_GRUDGE, now + 100);
        int martyr = data.level(MARTYR.path());
        if (martyr > 0 && p.getHealth() < p.getMaxHealth() * 0.5f) amount *= 1 - 0.02f * martyr;
        int garrison = data.level(GARRISON.path());
        if (garrison > 0 && !p.isSprinting()) amount *= 1 - 0.015f * garrison;
        int eater = data.level(SPELL_EATER.path());
        if (eater > 0 && spell) amount *= 1 - 0.03f * eater;
        int ashen = data.level(ASHEN_HIDE.path());
        if (ashen > 0 && source.is(DamageTypeTags.IS_FIRE)) amount *= 1 - 0.05f * ashen;
        int heart = data.level(MOUNTAIN_HEART.path());
        if (heart > 0) amount *= 1 - 0.025f * heart;
        int runic = data.level(RUNIC_ARMOR.path());
        if (runic > 0 && attacker != null) SystemAPI.restoreMana(p, runicMana(runic));
        int spikes = data.level(SPIKED_BULWARK.path());
        if (spikes > 0 && melee && attacker != null && attacker.isAlive()) {
            float back = com.beyondsolo.system.Balance.flatDamage(p, thorns(spikes));
            later(p, 1, () -> {
                if (attacker.isAlive()) attacker.hurt(p.damageSources().thorns(p), back);
            });
        }
        if (data.hasBuff(BUFF_CITADEL, now)) amount *= 0.4f;
        if (data.hasBuff(BUFF_ARCANUM, now)) amount *= spell ? 0.1f : 0.5f;
        if (data.hasBuff(BUFF_BRACE, now)) {
            amount *= 0.3f;
            if (melee && attacker != null && attacker.isAlive()) {
                int brace = Math.max(1, data.level(BRACE.path()));
                Vec3 from = p.position();
                later(p, 1, () -> {
                    if (!attacker.isAlive()) return;
                    attacker.hurt(p.damageSources().thorns(p), com.beyondsolo.system.Balance.flatDamage(p, 5 * m(brace, tier(p))));
                    push(attacker, from, 1.1, 0.35);
                });
                sound(p, SoundEvents.SHIELD_BLOCK, 1f, 0.7f);
            }
        }
        if (data.hasBuff(BUFF_MIRROR_WARD, now) && spell) {
            amount *= 0.3f;
            if (attacker != null && attacker.isAlive() && attacker != p) {
                int ward = Math.max(1, data.level(MIRROR_WARD.path()));
                later(p, 1, () -> {
                    if (!attacker.isAlive()) return;
                    attacker.hurt(p.damageSources().indirectMagic(p, p), com.beyondsolo.system.Balance.flatDamage(p, 6 * m(ward, tier(p))));
                    fx(p, Kind.BOLT, MIRROR_WARD.color(), mid(attacker), 0.3f, 0);
                });
            }
        }
        if (data.hasBuff(BUFF_EYE, now)) {
            float[] kept = SWALLOWED.get(p.getUUID());
            if (kept != null) kept[0] += amount * 0.5f;
            amount *= 0.5f;
        }
        return amount;
    }

    static void tickSecond(ServerPlayer p, SkillData data) {
        int roots = data.level(DEEP_ROOTS.path());
        if (roots > 0) com.beyondsolo.system.Balance.regenFlat(p, rootsRegen(roots));
        int resolve = data.level(AURA_OF_RESOLVE.path());
        if (resolve > 0) {
            for (Player ally : allies(p, 8)) com.beyondsolo.system.Balance.regenFlat(ally, resolveHeal(resolve));
        }
    }

    static void onKill(ServerPlayer p, SkillData data, LivingEntity dead) {
        BRANDS.remove(dead);
    }

    /** Touch the class so its skills are registered. */
    static void init() {}

    private GuardianPath() {}
}
