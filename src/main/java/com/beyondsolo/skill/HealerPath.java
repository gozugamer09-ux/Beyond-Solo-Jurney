package com.beyondsolo.skill;

import com.beyondsolo.combat.Buildups;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.ModEffects;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.network.SkillFxPayload.Kind;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

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
import static com.beyondsolo.skill.SkillActions.aimPoint;
import static com.beyondsolo.skill.SkillActions.canHit;
import static com.beyondsolo.skill.SkillActions.f;
import static com.beyondsolo.skill.SubclassActions.stun;
import static com.beyondsolo.skill.SubclassKit.*;

/**
 * The Healer subclasses' own skill sets, on top of the starters in {@link SubclassSkills}: ten more each for the
 * Lightbinder, Grovekeeper, Spring Oracle, Hemomancer and the rare Dawn Saint. Same shape as {@link WarriorPath}.
 *
 * <p>Healers are casters, so their skills may draw circles, but each has a look of its own: shafts of sunlight,
 * flowers and trees that really stand there for a while (block marker particles, nothing is planted), bubbles,
 * fountains, threads of blood, halos and wings.</p>
 */
public final class HealerPath {
    public static final String BUFF_AFTERGLOW = "afterglow";
    public static final String BUFF_LAST_LIGHT = "last_light_spent";
    public static final String BUFF_BLOOD_PRICE = "blood_price";
    public static final String BUFF_COAGULATE = "coagulate";
    public static final String BUFF_WINGS = "seraph_wings";
    public static final String BUFF_AVATAR = "avatar_of_dawn";
    /** Gifts a healer leaves on others (see {@link #gift}): the mist that makes blows miss, the halo's protection. */
    public static final String GIFT_MIST = "morning_mist";
    public static final String GIFT_BENISON = "benison";

    /** Motes of Mercy: player -> {motes left, end game time, health each mote gives}. Server thread only. */
    static final Map<UUID, double[]> MOTES = new HashMap<>();
    /** Premonition: oracle -> {drops left, end game time, health each drop gives}. */
    static final Map<UUID, double[]> DROPLETS = new HashMap<>();
    /** Fated Blow: creature -> {end game time, damage its oracle dealt to it meanwhile, oracle entity id}. */
    static final Map<LivingEntity, double[]> FATED = new WeakHashMap<>();
    /** Open Wounds: creature -> {end game time, hemomancer entity id, skill level}. */
    static final Map<LivingEntity, long[]> WOUNDS = new WeakHashMap<>();
    /** Foretold: creature -> the oracles who have already taken its first blow. */
    static final Map<LivingEntity, Set<UUID>> FORESEEN = new WeakHashMap<>();
    /** What healers have left on other players: player -> gift -> {end game time, value}. */
    private static final Map<UUID, Map<String, double[]>> GIFTS = new HashMap<>();

    private static final Subclass LB = Subclass.LIGHTBINDER, GK = Subclass.GROVEKEEPER, SO = Subclass.SPRING_ORACLE,
            HM = Subclass.HEMOMANCER, DS = Subclass.DAWN_SAINT;
    private static final PlayerRank A = PlayerRank.A, S = PlayerRank.S;

    private static final BlockState SAPLING = Blocks.OAK_SAPLING.defaultBlockState();
    private static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState LEAVES = Blocks.AZALEA_LEAVES.defaultBlockState();
    private static final BlockState BLOOMS = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState();
    private static final BlockState[] FLOWERS = {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(),
            Blocks.AZURE_BLUET.defaultBlockState(), Blocks.ALLIUM.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(),
            Blocks.OXEYE_DAISY.defaultBlockState()};
    private static final BlockState[] MUSHROOMS = {Blocks.RED_MUSHROOM.defaultBlockState(), Blocks.BROWN_MUSHROOM.defaultBlockState()};
    private static final BlockParticleOption LEAF = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AZALEA_LEAVES.defaultBlockState());
    private static final BlockParticleOption GORE = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
    private static final DustParticleOptions SPORE = new DustParticleOptions(new Vector3f(0.72f, 0.80f, 0.36f), 2.2f);
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.75f, 0.08f, 0.14f), 1.1f);

    // ================================================================ shared

    private static float wit(ServerPlayer p, float perPoint) {
        return stat(p, Stat.INT, perPoint);
    }

    private static Vec3 mid(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    private static boolean undead(LivingEntity e) {
        return e.getType().is(EntityTypeTags.UNDEAD);
    }

    private static long now(ServerPlayer p) {
        return p.level().getGameTime();
    }

    /** Living players within {@code radius} of a point. */
    private static List<Player> near(ServerLevel level, Vec3 at, double radius) {
        return level.getEntitiesOfClass(Player.class, new AABB(at, at).inflate(radius), x -> x.isAlive() && x.position().distanceTo(at) <= radius + 0.5);
    }

    private static Player mostWounded(List<Player> players) {
        Player worst = null;
        for (Player ally : players) {
            if (worst == null || ally.getHealth() / ally.getMaxHealth() < worst.getHealth() / worst.getMaxHealth()) worst = ally;
        }
        return worst;
    }

    /** How much stronger this healer's skills heal: Benediction, the subclass's own gifts and the avatar's light. */
    private static float healPower(ServerPlayer p) {
        SkillData data = SkillAPI.get(p);
        float bonus = 0.04f * data.level(Skills.BENEDICTION.path()) + 0.03f * data.level(INNER_RADIANCE.path()) + 0.04f * data.level(SANCTITY.path());
        if (data.hasBuff(BUFF_AVATAR, now(p))) bonus += 0.5f;
        return 1 + bonus;
    }

    /**
     * What a healer's skill really heals for. Every heal a healer's skill gives goes through here, the base class's
     * too, so this is also where the Afterglow starts.
     */
    static float healing(ServerPlayer p, float amount) {
        SkillData data = SkillAPI.get(p);
        if (data.level(AFTERGLOW.path()) > 0) data.setBuff(BUFF_AFTERGLOW, now(p) + 100);
        return amount * healPower(p);
    }

    static void mend(ServerPlayer p, LivingEntity ally, float amount) {
        ally.heal(healing(p, amount));
    }

    /** Leaves something on a player for a while that works whatever their own class is. */
    private static void gift(Player ally, String key, long until, double value) {
        GIFTS.computeIfAbsent(ally.getUUID(), k -> new HashMap<>()).put(key, new double[]{until, value});
    }

    private static double gift(Player ally, String key, long now) {
        Map<String, double[]> mine = GIFTS.get(ally.getUUID());
        if (mine == null) return 0;
        double[] g = mine.get(key);
        if (g == null) return 0;
        if (g[0] <= now) {
            mine.remove(key);
            return 0;
        }
        return g[1];
    }

    /** Pays health for a blood skill, never the last point. Returns what was paid. */
    private static float pay(ServerPlayer p, float amount) {
        float paid = Math.min(amount, p.getHealth() - 1);
        if (paid <= 0) return 0;
        p.setHealth(p.getHealth() - paid);
        SkillData data = SkillAPI.get(p);
        if (data.level(COAGULATE.path()) > 0) data.setBuff(BUFF_COAGULATE, now(p) + 100);
        p.serverLevel().sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.getX(), p.getY() + 1.1, p.getZ(), 2, 0.25, 0.25, 0.25, 0.05);
        return paid;
    }

    /** Moves the caster in a blink, also when they are a test player that does not really move. */
    private static void step(ServerPlayer p, Vec3 dest) {
        p.teleportTo(p.serverLevel(), dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
        if (p.position().distanceToSqr(dest) > 0.01) p.moveTo(dest.x, dest.y, dest.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
    }

    // ================================================================ lightbinder

    static float radianceMana(int l) { return 4 * l; }
    static float bindSeconds(int l) { return 3 + 0.2f * l; }
    static float lanternHeal(int l) { return 1 + 0.2f * l; }
    static float afterglowBonus(int l) { return 6 + 2 * l; }
    static float lastLight(int l) { return 12 + 2 * l; }
    static float solsticeHeal(int l) { return 2 + 0.4f * l; }

    public static final Skill SUNBEAM = active("sunbeam", LB, A, 22, 0xFFF0A8, 12, 100, 60,
            HealerPath::sunbeam, (l, t) -> a(f(8 * m(l, t)), f(2 * m(l, t))));
    public static final Skill BINDING_LIGHT = active("binding_light", LB, A, 24, 0xFFE9A0, 16, 320, 200,
            HealerPath::bindingLight, (l, t) -> a(f(bindSeconds(l)), f(2 * m(l, t))));
    public static final Skill INNER_RADIANCE = passive("inner_radiance", LB, A, 26, 0xFFF6C8, (l, t) -> a(f(radianceMana(l)), f(3 * l)));
    public static final Skill MOTES_OF_MERCY = active("motes_of_mercy", LB, A, 29, 0xFFF8D0, 18, 500, 320,
            HealerPath::motesOfMercy, (l, t) -> a(f(3 * m(l, t))));
    public static final Skill LANTERN_OF_MERCY = active("lantern_of_mercy", LB, A, 32, 0xFFE890, 20, 500, 320,
            HealerPath::lanternOfMercy, (l, t) -> a(f(lanternHeal(l)), f(3 * m(l, t))));
    public static final Skill AFTERGLOW = passive("afterglow", LB, A, 35, 0xFFD890, (l, t) -> a(f(afterglowBonus(l))));
    public static final Skill SOLAR_FLARE = active("solar_flare", LB, A, 38, 0xFFFAE0, 22, 360, 220,
            HealerPath::solarFlare, (l, t) -> a(f(9 * m(l, t)), f(3 * m(l, t))));
    public static final Skill LIGHTSTEP = active("lightstep", LB, A, 42, 0xFFF4B8, 16, 240, 140,
            HealerPath::lightstep, (l, t) -> a(f(7 * m(l, t)), f(4 * m(l, t))));
    public static final Skill LAST_LIGHT = passive("last_light", LB, A, 46, 0xFFE0A0, (l, t) -> a(f(lastLight(l))));
    public static final Skill SOLSTICE = active("solstice", LB, S, 50, 0xFFE070, 45, 1800, 1200,
            HealerPath::solstice, (l, t) -> a(f(5 * m(l, t)), f(solsticeHeal(l)), f(30 * m(l, t))));

    static boolean sunbeam(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 20, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.8f);
        // the sky takes a moment to answer
        fxAt(level, Kind.PILLAR, s.color(), target.position(), 1.2f, 6);
        later(p, 6, () -> {
            if (!target.isAlive()) return;
            float dmg = 8 * m(l, tier(p)) + wit(p, 0.25f);
            magic(p, s, target, undead(target) ? dmg * 1.5f : dmg);
            sound(target, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.4f);
            for (Player ally : allies(p, 8)) mend(p, ally, 2 * m(l, tier(p)));
        });
        return true;
    }

    static boolean bindingLight(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 16, x -> canHit(p, x));
        if (target == null) return false;
        int ticks = Math.round(bindSeconds(l) * 20);
        stun(target, ticks);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
        sound(target, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.8f);
        sound(target, SoundEvents.CHAIN_PLACE, 0.8f, 1.5f);
        fx(target, Kind.BIND, s.color(), target.position(), 1.1f, ticks);
        repeat(p, ticks, 10, () -> {
            if (!target.isAlive()) return;
            float dmg = 2 * m(l, tier(p)) + wit(p, 0.06f);
            magic(p, s, target, undead(target) ? dmg * 1.5f : dmg);
            // held where it stands
            target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
            target.hurtMarked = true;
        });
        return true;
    }

    static boolean motesOfMercy(ServerPlayer p, Skill s, int l) {
        long end = now(p) + 240;
        // what a mote will give is settled now; nobody has been healed yet, so no Afterglow
        float each = (3 * m(l, tier(p)) + wit(p, 0.08f)) * healPower(p);
        sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.6f);
        sound(p, SoundEvents.ALLAY_ITEM_GIVEN, 0.7f, 1.3f);
        for (Player ally : allies(p, 8)) {
            MOTES.put(ally.getUUID(), new double[]{3, end, each});
            fx(ally, Kind.ORBIT, s.color(), ally.position(), 3, 240);
        }
        return true;
    }

    static boolean lanternOfMercy(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 12).add(0, 2.6, 0);
        sound(level, at, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.5f, 1.8f);
        fxAt(level, Kind.ORB, s.color(), at, 0.45f, 160);
        repeat(p, 160, 20, () -> {
            for (Player ally : near(level, at, 6)) mend(p, ally, lanternHeal(l) + wit(p, 0.03f));
            LivingEntity foe = foes(p, at, 8).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(at))).orElse(null);
            if (foe == null) return;
            float dmg = 3 * m(l, tier(p)) + wit(p, 0.1f);
            magic(p, s, foe, undead(foe) ? dmg * 1.5f : dmg);
            // a ray between the lantern and what it burns
            fx(foe, Kind.SHOT, s.color(), at, 0.4f, 0);
            sound(level, at, SoundEvents.AMETHYST_BLOCK_HIT, 0.7f, 1.8f);
        });
        return true;
    }

    static boolean solarFlare(ServerPlayer p, Skill s, int l) {
        Vec3 at = p.position();
        float dmg = 9 * m(l, tier(p)) + wit(p, 0.25f);
        for (LivingEntity e : foes(p, at, 7)) {
            magic(p, s, e, undead(e) ? dmg * 1.5f : dmg);
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
        }
        for (Player ally : allies(p, 7)) {
            ally.removeEffect(MobEffects.BLINDNESS);
            ally.removeEffect(MobEffects.DARKNESS);
            mend(p, ally, 3 * m(l, tier(p)) + wit(p, 0.08f));
        }
        sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, 1f, 1.5f);
        sound(p, SoundEvents.BEACON_ACTIVATE, 0.7f, 1.9f);
        fx(p, Kind.ROAR, 0xFFFFFF, at, 7, 0);
        fx(p, Kind.PULSE, s.color(), at, 7, 0);
        return true;
    }

    static boolean lightstep(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 from = p.position();
        Vec3 eye = p.getEyePosition();
        Vec3 aimed = aimPoint(p, 10);
        Vec3 look = aimed.subtract(eye);
        if (look.lengthSqr() < 1) return false;
        Vec3 back = look.normalize().scale(0.6);
        // to the spot in sight, or the nearest place short of it with room to stand
        Vec3 dest = null;
        for (int i = 1; i <= 6 && dest == null; i++) {
            Vec3 spot = ground(level, aimed.subtract(back.scale(i)));
            if (level.noCollision(p, p.getBoundingBox().move(spot.subtract(from)))) dest = spot;
        }
        if (dest == null || dest.distanceToSqr(from) < 1) return false;
        step(p, dest);
        float dmg = 7 * m(l, tier(p)) + wit(p, 0.2f);
        for (LivingEntity e : foes(p, from, 3.5)) {
            magic(p, s, e, undead(e) ? dmg * 1.5f : dmg);
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
        }
        for (Player ally : allies(p, 5)) mend(p, ally, 4 * m(l, tier(p)) + wit(p, 0.1f));
        sound(level, from, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.7f);
        sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.9f);
        fx(p, Kind.BLINK, s.color(), from, 1, 1);
        return true;
    }

    static boolean solstice(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        sound(p, SoundEvents.BEACON_ACTIVATE, 1f, 0.7f);
        sound(p, SoundEvents.FIRECHARGE_USE, 0.5f, 0.6f);
        // a small sun hangs over the field, a little ahead, where the one who raised it can see it
        fxAt(level, Kind.ORB, s.color(), center.add(facing(p).scale(4)).add(0, 5, 0), 1.5f, 200);
        fxAt(level, Kind.PULSE, s.color(), center, 12, 0);
        repeat(p, 200, 40, () -> fxAt(level, Kind.PULSE, s.color(), center, 12, 1));
        repeat(p, 200, 20, () -> {
            for (Player ally : near(level, center, 12)) mend(p, ally, solsticeHeal(l) + wit(p, 0.03f));
            for (LivingEntity e : foes(p, center, 12)) {
                float dmg = 5 * m(l, tier(p)) + wit(p, 0.1f);
                magic(p, s, e, undead(e) ? dmg * 2 : dmg);
            }
        });
        later(p, 200, () -> {
            for (LivingEntity e : foes(p, center, 12)) {
                float dmg = 30 * m(l, tier(p)) + wit(p, 0.5f);
                magic(p, s, e, undead(e) ? dmg * 2 : dmg);
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
            }
            for (Player ally : near(level, center, 12)) mend(p, ally, ally.getMaxHealth() * 0.3f);
            sound(level, center, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, 0.6f);
            sound(level, center, SoundEvents.BEACON_DEACTIVATE, 1f, 0.8f);
            // the sun comes down as a shower of light
            fxAt(level, Kind.RAIN, s.color(), center, 12, 16);
            fxAt(level, Kind.ROAR, 0xFFFFFF, center, 12, 0);
        });
        return true;
    }

    // ================================================================ grovekeeper

    static float sproutHeal(int l) { return 1.5f + 0.3f * l; }
    static float briar(int l) { return 1 + 0.4f * l; }
    static float bloomBonus(int l) { return 4 + 2 * l; }
    static float elderHeal(int l) { return 3 + 0.5f * l; }

    public static final Skill THORN_VOLLEY = active("thorn_volley", GK, A, 22, 0x8FE05A, 10, 90, 50,
            HealerPath::thornVolley, (l, t) -> a(f(6 * m(l, t))));
    public static final Skill HEALING_SPROUT = active("healing_sprout", GK, A, 24, 0x7CE05A, 16, 400, 260,
            HealerPath::healingSprout, (l, t) -> a(f(sproutHeal(l)), f(2 * m(l, t))));
    public static final Skill BARKSKIN = passive("barkskin", GK, A, 26, 0xA08050, (l, t) -> a(f(0.6f * l), f(0.8f * l)));
    public static final Skill BRAMBLE_RUSH = active("bramble_rush", GK, A, 29, 0x6CB848, 18, 300, 180,
            HealerPath::brambleRush, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill SECOND_SPRING = active("second_spring", GK, A, 32, 0xB0F080, 20, 520, 340,
            HealerPath::secondSpring, (l, t) -> a(f(3 * m(l, t)), f(8)));
    public static final Skill BRIAR_COAT = passive("briar_coat", GK, A, 35, 0x5CA040, (l, t) -> a(f(briar(l))));
    public static final Skill SPORE_CLOUD = active("spore_cloud", GK, A, 38, 0xA8D860, 20, 420, 280,
            HealerPath::sporeCloud, (l, t) -> a(f(1.5f * m(l, t))));
    public static final Skill GRASPING_VINES = active("grasping_vines", GK, A, 42, 0x58B040, 22, 480, 300,
            HealerPath::graspingVines, (l, t) -> a(f(6 * m(l, t))));
    public static final Skill TOXIC_BLOOM = passive("toxic_bloom", GK, A, 46, 0x90E060, (l, t) -> a(f(bloomBonus(l))));
    public static final Skill ELDERWOOD = active("elderwood", GK, S, 50, 0x7CE05A, 45, 1800, 1200,
            HealerPath::elderwood, (l, t) -> a(f(20 * m(l, t)), f(elderHeal(l)), f(4 * m(l, t))));

    private static void leaves(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(LEAF, at.x, at.y, at.z, count, spread, 0.3, spread, 0.05);
    }

    static boolean thornVolley(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 dir = facing(p);
        for (LivingEntity e : cone(p, 9, 0.6)) {
            hit(p, s, e, 6 * m(l, tier(p)) + wit(p, 0.2f));
            Statuses.apply(e, DamageElement.POISON, 6 + l, p);
            leaves(level, mid(e), 4, 0.2);
        }
        sound(p, SoundEvents.SWEET_BERRY_BUSH_BREAK, 1f, 1.3f);
        sound(p, SoundEvents.ARROW_SHOOT, 0.8f, 1.5f);
        fx(p, Kind.FAN, s.color(), p.position().add(dir.scale(9)), 1, 0);
        return true;
    }

    static boolean healingSprout(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 12);
        sound(level, at, SoundEvents.BIG_DRIPLEAF_PLACE, 1f, 1.2f);
        sound(level, at, SoundEvents.BONE_MEAL_USE, 1f, 1f);
        // a marker stands four seconds, so the sprout is set three times over its ten
        repeat(p, 180, 60, () -> marker(level, SAPLING, at));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.5, at.z, 6, 0.3, 0.3, 0.3, 0);
        repeat(p, 200, 20, () -> {
            Player hurt = mostWounded(near(level, at, 7));
            if (hurt != null && hurt.getHealth() < hurt.getMaxHealth()) {
                mend(p, hurt, sproutHeal(l) + wit(p, 0.03f));
                fx(hurt, Kind.STREAM, s.color(), at.add(0, 0.6, 0), 0.8f, 10);
            }
            for (LivingEntity e : foes(p, at, 2.5)) hit(p, s, e, 2 * m(l, tier(p)) + wit(p, 0.05f));
        });
        return true;
    }

    static boolean brambleRush(ServerPlayer p, Skill s, int l) {
        Vec3 dir = facing(p);
        Vec3 from = p.position().add(dir), to = p.position().add(dir.scale(11));
        for (LivingEntity e : along(p, from.add(0, 0.8, 0), to.add(0, 0.8, 0), 1.3)) {
            hit(p, s, e, 8 * m(l, tier(p)) + wit(p, 0.25f));
            launch(e, 0.55);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            Statuses.apply(e, DamageElement.POISON, 8 + l, p);
        }
        sound(p, SoundEvents.SWEET_BERRY_BUSH_BREAK, 1f, 0.6f);
        sound(p, SoundEvents.ROOTS_BREAK, 1f, 0.7f);
        fx(p, Kind.WAVE, s.color(), to, 1.2f, 1);
        return true;
    }

    static boolean secondSpring(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        for (Player ally : allies(p, 8)) {
            mend(p, ally, 3 * m(l, tier(p)) + wit(p, 0.1f));
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 1 + l / 5, false, false, true));
            ally.removeEffect(MobEffects.POISON);
            ally.removeEffect(MobEffects.WITHER);
            ally.removeEffect(ModEffects.VENOM);
            Buildups.clear(ally, Statuses.VENOM);
            // a ring of flowers opens around them, one after the other
            Vec3 c = ground(level, ally.position());
            for (int i = 0; i < 7; i++) {
                double ang = i * Math.PI * 2 / 7 + ally.getId();
                Vec3 at = ground(level, c.add(Math.cos(ang) * 1.7, 0.5, Math.sin(ang) * 1.7));
                BlockState flower = FLOWERS[Math.floorMod(i + ally.getId(), FLOWERS.length)];
                later(p, i * 2, () -> marker(level, flower, at));
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 0.4, c.z, 8, 1.2, 0.2, 1.2, 0);
        }
        sound(p, SoundEvents.BONE_MEAL_USE, 1f, 0.8f);
        sound(p, SoundEvents.FLOWERING_AZALEA_PLACE, 1f, 1.2f);
        return true;
    }

    static boolean sporeCloud(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 14);
        sound(level, at, SoundEvents.FUNGUS_BREAK, 1f, 0.7f);
        sound(level, at, SoundEvents.PUFFER_FISH_BLOW_UP, 0.8f, 0.7f);
        // a ring of mushrooms marks the cloud
        for (int i = 0; i < 5; i++) {
            double ang = i * Math.PI * 2 / 5 + 0.3;
            Vec3 spot = ground(level, at.add(Math.cos(ang) * 3.2, 0.5, Math.sin(ang) * 3.2));
            BlockState cap = MUSHROOMS[i % 2];
            repeat(p, 80, 40, () -> marker(level, cap, spot));
        }
        repeat(p, 120, 10, () -> {
            level.sendParticles(SPORE, at.x, at.y + 0.8, at.z, 12, 1.9, 0.5, 1.9, 0);
            level.sendParticles(ParticleTypes.MYCELIUM, at.x, at.y + 1, at.z, 8, 2, 0.6, 2, 0);
            for (LivingEntity e : foes(p, at, 4)) {
                magic(p, s, e, 1.5f * m(l, tier(p)) + wit(p, 0.04f));
                Statuses.apply(e, DamageElement.POISON, 4 + l * 0.5f, p);
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
            }
        });
        repeat(p, 120, 20, () -> {
            for (Player ally : near(level, at, 4)) mend(p, ally, 0.5f + 0.1f * l);
        });
        return true;
    }

    static boolean graspingVines(ServerPlayer p, Skill s, int l) {
        List<LivingEntity> seized = new ArrayList<>(foes(p, p.position(), 10));
        if (seized.isEmpty()) return false;
        seized.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        ServerLevel level = p.serverLevel();
        for (LivingEntity e : seized.subList(0, Math.min(5, seized.size()))) {
            hit(p, s, e, 6 * m(l, tier(p)) + wit(p, 0.2f));
            // dragged to the keeper's feet, not through them
            pull(e, p.position().add(flat(e.position().subtract(p.position()), p).scale(1.6)), 2.0);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 5));
            Statuses.apply(e, DamageElement.POISON, 8 + l, p);
            leaves(level, mid(e), 8, 0.3);
            // the vine follows what it drags
            fx(p, Kind.CHAIN, s.color(), mid(e), 1.3f, 8);
            later(p, 6, () -> {
                if (e.isAlive()) fx(p, Kind.CHAIN, s.color(), mid(e), 1.3f, 18);
            });
        }
        sound(p, SoundEvents.VINE_BREAK, 1.2f, 0.6f);
        sound(p, SoundEvents.ROOTS_PLACE, 1f, 0.8f);
        return true;
    }

    static boolean elderwood(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        // it grows a little ahead, so the keeper is not standing inside the trunk
        Vec3 root = ground(level, center.add(facing(p).scale(2.5)));
        sound(level, root, SoundEvents.WOOD_PLACE, 1.4f, 0.5f);
        sound(level, root, SoundEvents.AZALEA_LEAVES_PLACE, 1.2f, 0.7f);
        sound(level, root, SoundEvents.ROOTS_PLACE, 1.2f, 0.5f);
        // trunk and crown stand twelve seconds; a marker lasts four, so the tree is raised three times
        for (int lay = 0; lay < 3; lay++) {
            boolean grow = lay == 0;
            int t0 = lay * 80;
            for (int h = 0; h < 6; h++) {
                Vec3 at = root.add(0, h, 0);
                later(p, t0 + (grow ? h * 2 : 0), () -> marker(level, LOG, at));
            }
            for (int y = 4; y <= 6; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        boolean inner = dx == 0 && dz == 0;
                        // two rings around the trunk, then a cap over it
                        if (y < 6 ? inner : dx != 0 && dz != 0) continue;
                        Vec3 at = root.add(dx, y, dz);
                        BlockState leaf = Math.floorMod(dx * 3 + dz + y, 4) == 0 ? BLOOMS : LEAVES;
                        later(p, t0 + (grow ? 10 + y : 0), () -> marker(level, leaf, at));
                    }
                }
            }
        }
        fxAt(level, Kind.CRACK, s.color(), root, 5, 0);
        fxAt(level, Kind.BIND, s.color(), center, 10, 100);
        for (LivingEntity e : foes(p, center, 10)) {
            hit(p, s, e, 20 * m(l, tier(p)) + wit(p, 0.4f));
            Statuses.apply(e, DamageElement.POISON, 10 + l, p);
        }
        repeat(p, 240, 20, () -> {
            leaves(level, root.add(0, 4.5, 0), 6, 1.4);
            for (Player ally : near(level, center, 10)) {
                mend(p, ally, elderHeal(l) + wit(p, 0.04f));
                ally.removeEffect(MobEffects.POISON);
                ally.removeEffect(ModEffects.VENOM);
            }
            for (LivingEntity e : foes(p, center, 10)) magic(p, s, e, 4 * m(l, tier(p)) + wit(p, 0.1f));
        });
        // the roots hold for the first five seconds
        repeat(p, 100, 10, () -> {
            for (LivingEntity e : foes(p, center, 10)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 5));
        });
        return true;
    }

    // ================================================================ spring oracle

    static float mindRegen(int l) { return 0.12f * l; }
    static int droplets(int l) { return 2 + l / 4; }
    static float sightBonus(int l) { return 8 + 3 * l; }
    static float mistDodge(int l) { return 25 + 2 * l; }
    static float gazeBonus(int l) { return 12 + 2 * l; }
    static float foretold(int l) { return 20 + 4 * l; }
    static float springHeal(int l) { return 3 + 0.5f * l; }

    public static final Skill SPRING_BOLT = active("spring_bolt", SO, A, 22, 0x7FE0E0, 10, 80, 50,
            HealerPath::springBolt, (l, t) -> a(f(7 * m(l, t)), f(2 * m(l, t))));
    public static final Skill BUBBLE_WARD = active("bubble_ward", SO, A, 24, 0x9FF0F0, 16, 440, 280,
            HealerPath::bubbleWard, (l, t) -> a(f(4 + 4 * (l / 3)), f(3 * m(l, t))));
    public static final Skill CLEAR_MIND = passive("clear_mind", SO, A, 26, 0xB0F4F4, (l, t) -> a(f(mindRegen(l))));
    public static final Skill FATED_BLOW = active("fated_blow", SO, A, 29, 0x60C8E0, 18, 300, 180,
            HealerPath::fatedBlow, (l, t) -> a(f(12 * m(l, t)), f(30)));
    public static final Skill PREMONITION = active("premonition", SO, A, 32, 0xA0E8FF, 18, 600, 400,
            HealerPath::premonition, (l, t) -> a(f(droplets(l)), f(2 * m(l, t))));
    public static final Skill SECOND_SIGHT = passive("second_sight", SO, A, 35, 0x70D0F0, (l, t) -> a(f(sightBonus(l))));
    public static final Skill MORNING_MIST = active("morning_mist", SO, A, 38, 0xD0F8F8, 20, 520, 340,
            HealerPath::morningMist, (l, t) -> a(f(mistDodge(l)), f(2 * m(l, t))));
    public static final Skill GAZE_OF_FATE = active("gaze_of_fate", SO, A, 42, 0x58B8E8, 22, 600, 400,
            HealerPath::gazeOfFate, (l, t) -> a(f(gazeBonus(l)), f(4 * m(l, t))));
    public static final Skill FORETOLD = passive("foretold", SO, A, 46, 0x90D8F8, (l, t) -> a(f(foretold(l))));
    public static final Skill ETERNAL_SPRING = active("eternal_spring", SO, S, 50, 0x7FE0E0, 45, 1800, 1200,
            HealerPath::eternalSpring, (l, t) -> a(f(springHeal(l)), f(10 * m(l, t))));

    static boolean springBolt(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 18, x -> canHit(p, x));
        if (target == null) return false;
        magic(p, s, target, 7 * m(l, tier(p)) + wit(p, 0.25f));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
        Player hurt = mostWounded(allies(p, 8));
        if (hurt != null) mend(p, hurt, 2 * m(l, tier(p)) + wit(p, 0.05f));
        sound(p, SoundEvents.BOTTLE_EMPTY, 1f, 1.3f);
        sound(target, SoundEvents.PLAYER_SPLASH, 0.6f, 1.5f);
        fx(p, Kind.SPLASH, s.color(), mid(target), 1.4f, 0);
        return true;
    }

    static boolean bubbleWard(ServerPlayer p, Skill s, int l) {
        LivingEntity seen = aim(p, 12, x -> x instanceof Player && x != p && x.isAlive());
        Player ally = seen instanceof Player other ? other : p;
        ServerLevel level = p.serverLevel();
        ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120, l / 3, false, false, true));
        ally.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 120, 0, false, false, true));
        ally.clearFire();
        mend(p, ally, 3 * m(l, tier(p)) + wit(p, 0.08f));
        sound(ally, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.8f, 1.4f);
        fx(ally, Kind.BUBBLE, s.color(), ally.position(), 1.25f, 120);
        later(p, 120, () -> {
            if (!ally.isAlive()) return;
            Vec3 at = ally.position();
            for (Player wet : near(level, at, 3.5)) mend(p, wet, 2 * m(l, tier(p)));
            for (LivingEntity e : foes(p, at, 3.5)) {
                magic(p, s, e, 3 * m(l, tier(p)) + wit(p, 0.1f));
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            }
            sound(level, at, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1f, 0.8f);
            sound(level, at, SoundEvents.GENERIC_SPLASH, 0.7f, 1.3f);
            fxAt(level, Kind.SPLASH, s.color(), at.add(0, 1, 0), 2.2f, 0);
        });
        return true;
    }

    static boolean fatedBlow(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 18, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        FATED.put(target, new double[]{now(p) + 60, 0, p.getId()});
        sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.6f);
        sound(target, SoundEvents.CONDUIT_ACTIVATE, 0.5f, 1.8f);
        fx(target, Kind.MARK, s.color(), target.position(), target.getBbHeight(), 60);
        later(p, 60, () -> {
            double[] fate = FATED.remove(target);
            if (fate == null || !target.isAlive()) return;
            Vec3 at = target.position();
            magic(p, s, target, 12 * m(l, tier(p)) + wit(p, 0.4f) + (float) fate[1] * 0.3f);
            sound(level, at, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1f, 0.8f);
            fxAt(level, Kind.GEYSER, s.color(), at, 4, 3);
        });
        return true;
    }

    static boolean premonition(ServerPlayer p, Skill s, int l) {
        int n = droplets(l);
        DROPLETS.put(p.getUUID(), new double[]{n, now(p) + 240, 2 * m(l, tier(p)) * healPower(p)});
        sound(p, SoundEvents.BOTTLE_FILL, 0.8f, 1.4f);
        sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.9f);
        fx(p, Kind.ORBIT, s.color(), p.position(), n, 240);
        return true;
    }

    static boolean morningMist(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        sound(p, SoundEvents.FIRE_EXTINGUISH, 0.4f, 0.6f);
        sound(p, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.8f, 1.2f);
        // the rim first, so the reach of the mist is seen at once
        for (int i = 0; i < 16; i++) {
            double ang = i * Math.PI / 8;
            level.sendParticles(ParticleTypes.CLOUD, center.x + Math.cos(ang) * 5, center.y + 0.3, center.z + Math.sin(ang) * 5, 1, 0.1, 0.1, 0.1, 0.01);
        }
        repeat(p, 120, 5, () -> level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.5, center.z, 4, 2.4, 0.3, 2.4, 0.005));
        repeat(p, 120, 10, () -> {
            long until = level.getGameTime() + 15;
            for (Player ally : near(level, center, 5)) gift(ally, GIFT_MIST, until, mistDodge(l));
            for (LivingEntity e : foes(p, center, 5)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
        });
        repeat(p, 120, 20, () -> {
            for (Player ally : near(level, center, 5)) mend(p, ally, 0.5f + 0.1f * l);
            for (LivingEntity e : foes(p, center, 5)) magic(p, s, e, 2 * m(l, tier(p)) + wit(p, 0.05f));
        });
        return true;
    }

    static boolean gazeOfFate(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 16);
        long end = now(p) + 160;
        sound(level, at, SoundEvents.CONDUIT_ACTIVATE, 0.8f, 1.5f);
        sound(level, at, SoundEvents.ENDER_EYE_DEATH, 0.8f, 0.7f);
        fxAt(level, Kind.ORB, s.color(), at.add(0, 4.5, 0), 0.7f, 160);
        for (LivingEntity e : foes(p, at, 8)) {
            magic(p, s, e, 4 * m(l, tier(p)) + wit(p, 0.1f));
            // the same curse a necromancer lays: everyone hurts it more
            MagePath.FRAIL.put(e, new long[]{end, Math.round(gazeBonus(l))});
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0, false, false));
        }
        return true;
    }

    static boolean eternalSpring(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        // a little ahead, so the oracle is not standing in the jet
        Vec3 center = ground(level, p.position().add(facing(p).scale(2)));
        sound(level, center, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1f, 0.6f);
        sound(level, center, SoundEvents.CONDUIT_ACTIVATE, 1f, 0.8f);
        repeat(p, 240, 20, () -> {
            fxAt(level, Kind.GEYSER, s.color(), center, 5, 3);
            for (Player ally : near(level, center, 10)) {
                mend(p, ally, springHeal(l) + wit(p, 0.04f));
                SubclassActions.cleanse(ally);
                ally.clearFire();
                if (ally instanceof ServerPlayer sp) SystemAPI.restoreMana(sp, 2);
            }
        });
        repeat(p, 240, 60, () -> {
            for (LivingEntity e : foes(p, center, 10)) {
                magic(p, s, e, 10 * m(l, tier(p)) + wit(p, 0.25f));
                push(e, center, 1.0, 0.35);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
            sound(level, center, SoundEvents.GENERIC_SPLASH, 1f, 0.8f);
            fxAt(level, Kind.PULSE, s.color(), center, 10, 1);
        });
        return true;
    }

    // ================================================================ hemomancer

    static float priceBonus(int l) { return 10 + 2 * l; }
    static float pactSteal(int l) { return 2 + 0.6f * l; }
    static float thirstBonus(int l) { return 5 + 2 * l; }
    static float coagulate(int l) { return 6 + 2 * l; }

    public static final Skill CRIMSON_LANCE = active("crimson_lance", HM, A, 22, 0xD0304A, 8, 70, 40,
            HealerPath::crimsonLance, (l, t) -> a(f(8 * m(l, t)), f(1)));
    public static final Skill TRANSFUSION = active("transfusion", HM, A, 24, 0xE04A5A, 12, 300, 180,
            HealerPath::transfusion, (l, t) -> a(f(15), f(priceBonus(l))));
    public static final Skill SANGUINE_PACT = passive("sanguine_pact", HM, A, 26, 0xB02840, (l, t) -> a(f(pactSteal(l))));
    public static final Skill OPEN_WOUNDS = active("open_wounds", HM, A, 29, 0xC02038, 16, 300, 180,
            HealerPath::openWounds, (l, t) -> a(f(1.5f * m(l, t)), f(8 * m(l, t))));
    public static final Skill EXSANGUINATE = active("exsanguinate", HM, A, 32, 0xD83850, 20, 400, 260,
            HealerPath::exsanguinate, (l, t) -> a(f(2.5f * m(l, t)), f(40)));
    public static final Skill RED_THIRST = passive("red_thirst", HM, A, 35, 0xA01830, (l, t) -> a(f(thirstBonus(l))));
    public static final Skill CRIMSON_THORNS = active("crimson_thorns", HM, A, 38, 0xC8283C, 18, 340, 220,
            HealerPath::crimsonThorns, (l, t) -> a(f(10 * m(l, t)), f(2)));
    public static final Skill BLOOD_RITE = active("blood_rite", HM, A, 42, 0xB81C34, 22, 600, 400,
            HealerPath::bloodRite, (l, t) -> a(f(25), f(8 * m(l, t))));
    public static final Skill COAGULATE = passive("coagulate", HM, A, 46, 0x902038, (l, t) -> a(f(coagulate(l))));
    public static final Skill BLOOD_TEMPEST = active("blood_tempest", HM, S, 50, 0xC0304A, 45, 1800, 1200,
            HealerPath::bloodTempest, (l, t) -> a(f(4 * m(l, t)), f(25 * m(l, t))));

    private static void bleed(ServerLevel level, LivingEntity e, int drops) {
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), drops, 0.2, 0.3, 0.2, 0.05);
    }

    static boolean crimsonLance(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        pay(p, 1);
        Vec3 eye = p.getEyePosition();
        Vec3 end = aimPoint(p, 16);
        for (LivingEntity e : along(p, eye, end, 0.5)) {
            magic(p, s, e, 8 * m(l, tier(p)) + wit(p, 0.3f));
            Buildups.add(e, Buildups.BLOOD, 10 + 2 * l, p);
            bleed(level, e, 3);
        }
        sound(p, SoundEvents.TRIDENT_THROW.value(), 1f, 1.5f);
        sound(p, SoundEvents.SLIME_ATTACK, 0.7f, 0.7f);
        fx(p, Kind.SHOT, s.color(), end, 0.6f, 0);
        return true;
    }

    static boolean transfusion(ServerPlayer p, Skill s, int l) {
        float paid = pay(p, p.getHealth() * 0.15f);
        float given = paid * 2.5f + 2 * m(l, tier(p)) + wit(p, 0.08f);
        for (Player ally : allies(p, 8)) {
            if (ally == p) continue;
            mend(p, ally, given);
            fx(ally, Kind.STREAM, s.color(), p.position().add(0, 1.1, 0), 0.9f, 12);
        }
        SkillAPI.setBuff(p, BUFF_BLOOD_PRICE, 160);
        sound(p, SoundEvents.WARDEN_HEARTBEAT, 1f, 1.5f);
        sound(p, SoundEvents.HONEY_DRINK, 0.8f, 0.6f);
        // the price is seen on the one who paid it
        fx(p, Kind.ORBIT, s.color(), p.position(), 2, 160);
        return true;
    }

    static boolean openWounds(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 16, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        WOUNDS.put(target, new long[]{now(p) + 100, p.getId(), l});
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        sound(target, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.6f);
        sound(target, SoundEvents.SLIME_SQUISH, 0.8f, 0.6f);
        fx(target, Kind.SPLASH, s.color(), mid(target), 1.2f, 1);
        repeat(p, 100, 10, () -> {
            if (!target.isAlive()) return;
            magic(p, s, target, 1.5f * m(l, tier(p)) + wit(p, 0.05f));
            Buildups.add(target, Buildups.BLOOD, 8, p);
            bleed(level, target, 2);
        });
        return true;
    }

    static boolean exsanguinate(ServerPlayer p, Skill s, int l) {
        if (foes(p, p.position(), 7).isEmpty()) return false;
        sound(p, SoundEvents.WARDEN_HEARTBEAT, 1f, 0.8f);
        repeat(p, 60, 10, () -> {
            float drawn = 0;
            for (LivingEntity e : foes(p, p.position(), 7)) {
                float before = e.getHealth();
                magic(p, s, e, 2.5f * m(l, tier(p)) + wit(p, 0.08f));
                drawn += Math.max(0, before - e.getHealth());
                Buildups.add(e, Buildups.BLOOD, 5, p);
                // a thread of blood from each of them
                fx(p, Kind.STREAM, s.color(), mid(e), 0.9f, 10);
            }
            if (drawn > 0) {
                mend(p, p, drawn * 0.4f);
                sound(p, SoundEvents.HONEY_DRINK, 0.5f, 0.8f);
            }
        });
        return true;
    }

    static boolean crimsonThorns(ServerPlayer p, Skill s, int l) {
        pay(p, 2);
        Vec3 dir = facing(p);
        Vec3 from = p.position().add(dir), to = p.position().add(dir.scale(11));
        for (LivingEntity e : along(p, from.add(0, 0.8, 0), to.add(0, 0.8, 0), 1.3)) {
            hit(p, s, e, 10 * m(l, tier(p)) + wit(p, 0.3f));
            launch(e, 0.5);
            Buildups.add(e, Buildups.BLOOD, 20 + 2 * l, p);
        }
        sound(p, SoundEvents.TRIDENT_HIT_GROUND, 1f, 0.6f);
        sound(p, SoundEvents.SLIME_BLOCK_BREAK, 1f, 0.6f);
        fx(p, Kind.WAVE, s.color(), to, 1.2f, 1);
        return true;
    }

    static boolean bloodRite(ServerPlayer p, Skill s, int l) {
        Vec3 at = p.position();
        float paid = pay(p, p.getHealth() * 0.25f);
        float dmg = 8 * m(l, tier(p)) + wit(p, 0.3f) + paid * 2;
        boolean bled = false;
        for (LivingEntity e : foes(p, at, 8)) {
            magic(p, s, e, dmg);
            Buildups.add(e, Buildups.BLOOD, 25, p);
            bled = true;
        }
        // what was paid comes back, if anything bled for it
        if (bled && paid > 0) repeat(p, 100, 20, () -> p.heal(paid / 5));
        sound(p, SoundEvents.WARDEN_HEARTBEAT, 1f, 0.6f);
        sound(p, SoundEvents.WITHER_HURT, 0.5f, 0.6f);
        fx(p, Kind.CRACK, s.color(), at, 8, 0);
        fx(p, Kind.ROAR, s.color(), at, 8, 0);
        return true;
    }

    static boolean bloodTempest(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        pay(p, p.getHealth() * 0.3f);
        sound(p, SoundEvents.WITHER_SPAWN, 0.35f, 1.6f);
        sound(p, SoundEvents.WARDEN_HEARTBEAT, 1f, 0.6f);
        fxAt(level, Kind.CRACK, s.color(), center, 10, 0);
        fxAt(level, Kind.RAIN, s.color(), center, 10, 160);
        repeat(p, 160, 10, () -> {
            float drawn = 0;
            for (LivingEntity e : foes(p, center, 10)) {
                float before = e.getHealth();
                magic(p, s, e, 4 * m(l, tier(p)) + wit(p, 0.12f));
                drawn += Math.max(0, before - e.getHealth());
                Buildups.add(e, Buildups.BLOOD, 10, p);
            }
            if (drawn > 0) mend(p, p, drawn * 0.3f);
        });
        repeat(p, 160, 40, () -> sound(level, center, SoundEvents.WEATHER_RAIN, 0.8f, 0.6f));
        later(p, 160, () -> {
            for (LivingEntity e : foes(p, center, 10)) magic(p, s, e, 25 * m(l, tier(p)) + wit(p, 0.5f));
            for (Player ally : near(level, center, 10)) mend(p, ally, ally.getMaxHealth() * 0.25f);
            sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.6f);
            fxAt(level, Kind.ROAR, s.color(), center, 10, 0);
            fxAt(level, Kind.SPLASH, s.color(), center.add(0, 1, 0), 6, 1);
            level.sendParticles(GORE, center.x, center.y + 1, center.z, 40, 3, 0.6, 3, 0.2);
        });
        return true;
    }

    // ================================================================ dawn saint

    static float gilded(int l) { return 2 + 2 * l; }
    static float soulRegen(int l) { return 0.15f * l; }
    static float avatarHeal(int l) { return 3 + 0.5f * l; }

    public static final Skill FIRST_LIGHT = active("first_light", DS, S, 22, 0xFFD27A, 14, 90, 50,
            HealerPath::firstLight, (l, t) -> a(f(12 * m(l, t)), f(4 * m(l, t))));
    public static final Skill BENISON = active("benison", DS, S, 24, 0xFFE8A8, 20, 360, 220,
            HealerPath::benison, (l, t) -> a(f(8 * m(l, t)), f(15)));
    public static final Skill SANCTITY = passive("sanctity", DS, S, 26, 0xFFF0C0, (l, t) -> a(f(4 * l), f(2 * l)));
    public static final Skill SERAPH_WINGS = active("seraph_wings", DS, S, 29, 0xFFF4D8, 22, 500, 320,
            HealerPath::seraphWings, (l, t) -> a(f(8 * m(l, t)), f(10)));
    public static final Skill CHAKRAM_OF_DAWN = active("chakram_of_dawn", DS, S, 32, 0xFFC860, 22, 300, 180,
            HealerPath::chakramOfDawn, (l, t) -> a(f(9 * m(l, t)), f(4 * m(l, t))));
    public static final Skill GILDED_SOUL = passive("gilded_soul", DS, S, 35, 0xF0C060, (l, t) -> a(f(gilded(l))));
    public static final Skill RAPTURE = active("rapture", DS, S, 38, 0xFFE090, 26, 520, 340,
            HealerPath::rapture, (l, t) -> a(f(16 * m(l, t))));
    public static final Skill SANCTUM = active("sanctum", DS, S, 42, 0xFFDC88, 28, 900, 600,
            HealerPath::sanctum, (l, t) -> a(f(80), f(4 * m(l, t))));
    public static final Skill RADIANT_SOUL = passive("radiant_soul", DS, S, 46, 0xFFE8B0, (l, t) -> a(f(6 * l), f(soulRegen(l))));
    public static final Skill AVATAR_OF_DAWN = active("avatar_of_dawn", DS, S, 50, 0xFFD27A, 45, 2400, 1600,
            HealerPath::avatarOfDawn, (l, t) -> a(f(15), f(14 * m(l, t)), f(avatarHeal(l))));

    static boolean firstLight(ServerPlayer p, Skill s, int l) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = aimPoint(p, 20);
        float dmg = 12 * m(l, tier(p)) + wit(p, 0.4f);
        for (LivingEntity e : along(p, eye, end, 0.8)) magic(p, s, e, undead(e) ? dmg * 2 : dmg);
        for (Player ally : p.level().getEntitiesOfClass(Player.class, new AABB(eye, end).inflate(1.5),
                x -> x != p && x.isAlive() && x.getBoundingBox().inflate(1).clip(eye, end).isPresent())) {
            mend(p, ally, 4 * m(l, tier(p)) + wit(p, 0.1f));
        }
        mend(p, p, 2 * m(l, tier(p)));
        sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 2f);
        sound(p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 1.7f);
        fx(p, Kind.BEAM, s.color(), end, 0.5f, 8);
        return true;
    }

    static boolean benison(ServerPlayer p, Skill s, int l) {
        long until = now(p) + 120;
        for (Player ally : allies(p, 12)) {
            mend(p, ally, 8 * m(l, tier(p)) + wit(p, 0.2f));
            ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1, false, false, true));
            gift(ally, GIFT_BENISON, until, 15);
            fx(ally, Kind.HALO, s.color(), ally.position(), 1, 120);
        }
        sound(p, SoundEvents.BELL_RESONATE, 0.4f, 1.8f);
        sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        return true;
    }

    static boolean seraphWings(ServerPlayer p, Skill s, int l) {
        Vec3 at = p.position();
        SkillAPI.setBuff(p, BUFF_WINGS, 200);
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, 200, 2, false, false, true));
        for (LivingEntity e : foes(p, at, 6)) {
            magic(p, s, e, 8 * m(l, tier(p)) + wit(p, 0.25f));
            push(e, at, 1.2, 0.4);
        }
        sound(p, SoundEvents.ENDER_DRAGON_FLAP, 0.9f, 1.4f);
        sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.5f);
        fx(p, Kind.WINGS, s.color(), at, 1, 200);
        fx(p, Kind.PULSE, s.color(), at, 6, 0);
        return true;
    }

    static boolean chakramOfDawn(ServerPlayer p, Skill s, int l) {
        LivingEntity first = aim(p, 18, x -> canHit(p, x));
        if (first == null) return false;
        sound(p, SoundEvents.TRIDENT_THROW.value(), 1f, 1.4f);
        chakram(p, s, l, p, first, new HashSet<>(), 0);
        return true;
    }

    /** One hop of the chakram, from one creature to the next, and home after the last. */
    private static void chakram(ServerPlayer p, Skill s, int l, LivingEntity from, LivingEntity to, Set<Integer> struck, int n) {
        if (!to.isAlive()) return;
        struck.add(to.getId());
        fx(from, Kind.DISCUS, s.color(), mid(to), 1.2f, 4);
        later(p, 4, () -> {
            if (!to.isAlive()) return;
            float dmg = 9 * m(l, tier(p)) + wit(p, 0.3f);
            magic(p, s, to, undead(to) ? dmg * 2 : dmg);
            mend(p, p, 1.5f * m(l, tier(p)));
            sound(to, SoundEvents.AMETHYST_BLOCK_HIT, 1f, 1.2f + 0.12f * n);
            LivingEntity next = n >= 5 ? null : foes(p, to.position(), 12).stream().filter(e -> !struck.contains(e.getId()))
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(to))).orElse(null);
            if (next != null) {
                chakram(p, s, l, to, next, struck, n + 1);
                return;
            }
            // it comes back, and brings the dawn with it
            fx(to, Kind.DISCUS, s.color(), mid(p), 1.2f, 6);
            later(p, 6, () -> {
                for (Player ally : allies(p, 6)) mend(p, ally, 4 * m(l, tier(p)) + wit(p, 0.1f));
                sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.5f);
            });
        });
    }

    static boolean rapture(ServerPlayer p, Skill s, int l) {
        List<LivingEntity> taken = new ArrayList<>(foes(p, p.position(), 12));
        if (taken.isEmpty()) return false;
        taken.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.7f);
        for (LivingEntity e : taken.subList(0, Math.min(6, taken.size()))) {
            // taken up in a shaft of light, then put down hard
            launch(e, 0.9);
            fxAt(level, Kind.PILLAR, s.color(), e.position(), 1.3f, 12);
            later(p, 12, () -> {
                if (!e.isAlive()) return;
                float dmg = 16 * m(l, tier(p)) + wit(p, 0.4f);
                magic(p, s, e, undead(e) ? dmg * 2 : dmg);
                stun(e, 40);
                e.setDeltaMovement(0, -1.2, 0);
                e.hurtMarked = true;
                sound(e, SoundEvents.TRIDENT_THUNDER.value(), 0.3f, 1.9f);
            });
        }
        return true;
    }

    static boolean sanctum(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        sound(p, SoundEvents.BEACON_ACTIVATE, 1f, 1.1f);
        sound(p, SoundEvents.BELL_RESONATE, 0.6f, 1.2f);
        fxAt(level, Kind.DOME, s.color(), center, 5, 100);
        repeat(p, 100, 10, () -> {
            for (Player ally : near(level, center, 5)) ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 15, 3, false, false, true));
            for (LivingEntity e : foes(p, center, 5)) push(e, center, 0.8, 0.25);
        });
        repeat(p, 100, 20, () -> {
            for (Player ally : near(level, center, 5)) mend(p, ally, 2 + 0.2f * l);
            for (LivingEntity e : foes(p, center, 5.5)) magic(p, s, e, 4 * m(l, tier(p)) + wit(p, 0.1f));
        });
        return true;
    }

    static boolean avatarOfDawn(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        SkillAPI.setBuff(p, BUFF_AVATAR, 300);
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 1, false, false, true));
        sound(p, SoundEvents.BEACON_ACTIVATE, 1f, 0.8f);
        sound(p, SoundEvents.ENDER_DRAGON_FLAP, 1f, 0.8f);
        sound(p, SoundEvents.TOTEM_USE, 0.4f, 1.4f);
        fx(p, Kind.WINGS, s.color(), p.position(), 1.6f, 300);
        fx(p, Kind.HALO, 0xFFFFFF, p.position(), 1.3f, 300);
        fx(p, Kind.ROAR, s.color(), p.position(), 8, 0);
        repeat(p, 300, 20, () -> {
            for (Player ally : allies(p, 12)) mend(p, ally, avatarHeal(l) + wit(p, 0.04f));
        });
        // the pillars walk outward: the nearest foe first, then the next nearest, and round again
        Set<LivingEntity> struck = new HashSet<>();
        repeat(p, 300, 40, () -> {
            List<LivingEntity> foes = new ArrayList<>(foes(p, p.position(), 14));
            if (foes.isEmpty()) return;
            foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
            if (struck.containsAll(foes)) struck.clear();
            LivingEntity e = foes.stream().filter(f -> !struck.contains(f)).findFirst().orElseThrow();
            struck.add(e);
            fxAt(level, Kind.PILLAR, s.color(), e.position(), 1.4f, 8);
            later(p, 8, () -> {
                if (!e.isAlive()) return;
                float dmg = 14 * m(l, tier(p)) + wit(p, 0.3f);
                magic(p, s, e, undead(e) ? dmg * 2 : dmg);
                sound(e, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 0.8f);
            });
        });
        return true;
    }

    // ================================================================ passives

    static {
        bonus(BARKSKIN, Attributes.ARMOR, 0.6f, ADD, "armor");
        bonus(BARKSKIN, Attributes.MAX_HEALTH, 0.8f, ADD, "health");
    }

    /** More mana from the Lightbinder's radiance and the Saint's soul. */
    static float manaBonus(SkillData data) {
        return radianceMana(data.level(INNER_RADIANCE.path())) + 6 * data.level(RADIANT_SOUL.path());
    }

    /** Extra damage, in percent, when a healer subclass player strikes. */
    static float strikeBonus(ServerPlayer p, SkillData data, LivingEntity target, long now) {
        float bonus = 0;
        if (data.hasBuff(BUFF_AFTERGLOW, now)) bonus += afterglowBonus(Math.max(1, data.level(AFTERGLOW.path())));
        int bloom = data.level(TOXIC_BLOOM.path());
        if (bloom > 0 && (poisoned(target) || Buildups.get(target, Statuses.VENOM) > 0)) bonus += bloomBonus(bloom);
        int sight = data.level(SECOND_SIGHT.path());
        if (sight > 0 && target.getHealth() < target.getMaxHealth() * 0.35f) bonus += sightBonus(sight);
        int thirst = data.level(RED_THIRST.path());
        if (thirst > 0 && (target.getHealth() < target.getMaxHealth() * 0.5f || Buildups.get(target, Buildups.BLOOD) > 0)) bonus += thirstBonus(thirst);
        if (data.hasBuff(BUFF_BLOOD_PRICE, now)) bonus += priceBonus(Math.max(1, data.level(TRANSFUSION.path())));
        bonus += 2 * data.level(SANCTITY.path());
        if (data.hasBuff(BUFF_AVATAR, now)) bonus += 40;
        return bonus;
    }

    static void afterStrike(ServerPlayer p, SkillData data, LivingEntity target, float amount, long now) {
        int pact = data.level(SANGUINE_PACT.path());
        if (pact > 0 && amount > 0) p.heal(amount * pactSteal(pact) / 100f);
        if (!FATED.isEmpty()) {
            double[] fate = FATED.get(target);
            if (fate != null && fate[2] == p.getId() && fate[0] > now) fate[1] += amount;
        }
    }

    /**
     * A player is struck: a healer's own passives, and what healers have left on anyone. Returns the damage to take,
     * below zero if the blow misses. Public for the game tests: test players cannot be hurt, so they ask directly.
     */
    public static float whenStruck(ServerPlayer p, SkillData data, LivingEntity attacker, boolean melee, DamageSource source, float amount, long now) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        UUID id = p.getUUID();
        if (attacker != null) {
            // Premonition: a drop swallows the blow whole
            double[] drops = DROPLETS.get(id);
            if (drops != null) {
                if (drops[1] <= now || drops[0] < 1) {
                    DROPLETS.remove(id);
                } else {
                    drops[0]--;
                    int left = (int) drops[0];
                    p.heal((float) drops[2]);
                    fx(p, Kind.ORBIT, PREMONITION.color(), p.position(), left, (int) Math.max(1, drops[1] - now));
                    p.serverLevel().sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1, p.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
                    sound(p, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 1f, 1.2f);
                    if (left < 1) DROPLETS.remove(id);
                    return -1;
                }
            }
            double mist = gift(p, GIFT_MIST, now);
            if (mist > 0 && p.getRandom().nextFloat() * 100 < mist) {
                sound(p, SoundEvents.FIRE_EXTINGUISH, 0.3f, 1.8f);
                return -1;
            }
        }
        double blessed = gift(p, GIFT_BENISON, now);
        if (blessed > 0) amount *= 1 - (float) blessed / 100f;
        int foretold = data.level(FORETOLD.path());
        if (foretold > 0 && attacker != null && FORESEEN.computeIfAbsent(attacker, k -> new HashSet<>()).add(id)) amount *= 1 - foretold(foretold) / 100f;
        int gilded = data.level(GILDED_SOUL.path());
        if (gilded > 0) amount *= 1 - gilded(gilded) / 100f;
        if (data.hasBuff(BUFF_COAGULATE, now)) amount *= 1 - coagulate(Math.max(1, data.level(COAGULATE.path()))) / 100f;
        int briar = data.level(BRIAR_COAT.path());
        if (briar > 0 && melee && attacker != null && attacker.isAlive()) {
            later(p, 1, () -> {
                if (!attacker.isAlive()) return;
                attacker.hurt(p.damageSources().thorns(p), briar(briar));
                Statuses.apply(attacker, DamageElement.POISON, 4 + briar, p);
            });
        }
        // Motes of Mercy: a mote is spent to mend whoever was struck
        double[] motes = MOTES.get(id);
        if (motes != null) {
            if (motes[1] <= now || motes[0] < 1) {
                MOTES.remove(id);
            } else if (attacker != null) {
                motes[0]--;
                int left = (int) motes[0];
                float heal = (float) motes[2];
                later(p, 1, () -> p.heal(heal));
                fx(p, Kind.ORBIT, MOTES_OF_MERCY.color(), p.position(), left, (int) Math.max(1, motes[1] - now));
                sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.9f);
                if (left < 1) MOTES.remove(id);
            }
        }
        // the avatar cannot be killed
        if (data.hasBuff(BUFF_AVATAR, now) && amount >= p.getHealth()) amount = Math.max(0, p.getHealth() - 1);
        int last = data.level(LAST_LIGHT.path());
        if (last > 0 && !data.hasBuff(BUFF_LAST_LIGHT, now) && amount < p.getHealth() && p.getHealth() - amount < p.getMaxHealth() * 0.3f) {
            data.setBuff(BUFF_LAST_LIGHT, now + 1200);
            later(p, 1, () -> {
                mend(p, p, p.getMaxHealth() * lastLight(last) / 100f);
                sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
                fx(p, Kind.HEAL, LAST_LIGHT.color(), p.position(), 1.2f, 0);
            });
        }
        return amount;
    }

    static void tickSecond(ServerPlayer p, SkillData data) {
        float mana = mindRegen(data.level(CLEAR_MIND.path())) + soulRegen(data.level(RADIANT_SOUL.path()));
        if (mana > 0) SystemAPI.restoreMana(p, mana);
        if (data.hasBuff(BUFF_WINGS, now(p))) {
            for (Player ally : allies(p, 8)) if (ally.getHealth() < ally.getMaxHealth()) mend(p, ally, 1);
        }
    }

    static void onKill(ServerPlayer p, SkillData data, LivingEntity dead) {
        FATED.remove(dead);
        FORESEEN.remove(dead);
        long[] wound = WOUNDS.remove(dead);
        if (wound == null || wound[0] <= now(p) || wound[1] != p.getId()) return;
        // Open Wounds: it died bleeding, and bursts. Next tick, so that the burst cannot feed itself.
        int l = (int) wound[2];
        ServerLevel level = p.serverLevel();
        Vec3 at = dead.position();
        later(p, 1, () -> {
            for (LivingEntity e : foes(p, at, 3.5)) {
                magic(p, OPEN_WOUNDS, e, 8 * m(l, tier(p)) + wit(p, 0.2f));
                Buildups.add(e, Buildups.BLOOD, 15, p);
            }
            mend(p, p, 3 + 0.5f * l);
            sound(level, at, SoundEvents.SLIME_BLOCK_BREAK, 1.2f, 0.5f);
            fxAt(level, Kind.SPLASH, OPEN_WOUNDS.color(), at.add(0, 0.8, 0), 3.5f, 1);
            level.sendParticles(GORE, at.x, at.y + 0.8, at.z, 24, 0.6, 0.5, 0.6, 0.2);
            level.sendParticles(BLOOD, at.x, at.y + 0.8, at.z, 10, 0.8, 0.5, 0.8, 0);
        });
    }

    /** Touch the class so its skills are registered. */
    static void init() {}

    private HealerPath() {}
}
