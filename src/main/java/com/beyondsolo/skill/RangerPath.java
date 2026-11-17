package com.beyondsolo.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.Buildups;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.job.Subclass;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.network.SkillFxPayload.Kind;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
import java.util.function.Supplier;

import static com.beyondsolo.skill.SkillActions.aim;
import static com.beyondsolo.skill.SkillActions.aimPoint;
import static com.beyondsolo.skill.SkillActions.canHit;
import static com.beyondsolo.skill.SkillActions.f;
import static com.beyondsolo.skill.SubclassActions.stun;
import static com.beyondsolo.skill.SubclassKit.*;

/**
 * The Ranger subclasses' own skill sets, on top of the starters in {@link SubclassSkills}: ten more each for the
 * Sharpshooter, Wildcaller, Snare Artisan, Storm Archer and the rare Heavenpiercer. Same shape as {@link WarriorPath}.
 *
 * <p>Rangers are not casters: nothing here draws a circle but the five ultimates. What is seen is the thing itself:
 * an arrow that flies and stays stuck where it lands, a sight line, beasts made of starlight, iron jaws on the
 * ground, a wire between two stakes, a net, rocks that really fall, a cloud with lightning in it.</p>
 */
public final class RangerPath {
    public static final String BUFF_TAKE_AIM = "take_aim";
    public static final String BUFF_LIVE_WIRE = "live_wire";
    /** Carried by the wind: set while a Heavenpiercer rises and hangs in the air. */
    public static final String BUFF_SKYBORNE = "skyborne";

    private static final int HAWK = SkillFxPayload.BEAST_HAWK, WOLF = SkillFxPayload.BEAST_WOLF, BOAR = SkillFxPayload.BEAST_BOAR,
            BEAR = SkillFxPayload.BEAST_BEAR, HOWL = SkillFxPayload.BEAST_HOWL;

    /** Patient Hunter: player -> {seconds stood still (at most 5), x, y, z a second ago}. Server thread only. */
    static final Map<UUID, double[]> FOCUS = new HashMap<>();
    /** Pack Tactics: creature -> {end game time, entity id of the hunter whose beast bit it}. */
    static final Map<LivingEntity, long[]> BITTEN = new WeakHashMap<>();
    /** What a trap, snare, net or pin is holding: creature -> {end game time, entity id of whoever set it}. */
    static final Map<LivingEntity, long[]> HELD = new WeakHashMap<>();
    /** Jaw traps lying armed, by who set them. */
    private static final Map<UUID, List<Trap>> TRAPS = new HashMap<>();
    /** Static left by a storm archer's lightning: creature -> {end game time, entity id of the archer}. */
    static final Map<LivingEntity, long[]> CHARGED = new WeakHashMap<>();
    /** Storm Battery and the sparks of Live Wire come at most so often: player -> game time of the last one. */
    private static final Map<UUID, Long> LAST_BATTERY = new HashMap<>(), LAST_SPARK = new HashMap<>();
    /** Arrow Halo: player -> game time their newest halo was raised (an older one stops firing). */
    private static final Map<UUID, Long> HALO = new HashMap<>();
    /** Killing Wind: player -> {kills counted (at most 5), end game time}. */
    static final Map<UUID, long[]> WIND = new HashMap<>();

    private static final Subclass SS = Subclass.SHARPSHOOTER, WC = Subclass.WILDCALLER, SA = Subclass.SNARE_ARTISAN,
            ST = Subclass.STORM_ARCHER, HP = Subclass.HEAVENPIERCER;
    private static final PlayerRank A = PlayerRank.A, S = PlayerRank.S;

    private static final ResourceLocation HOVER = BeyondSolo.id("skill/ranger/hover");
    private static final BlockState[] ROCKS = {Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.ANDESITE.defaultBlockState()};
    private static final BlockParticleOption GRIT = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState());

    // ================================================================ shared

    private static float agi(ServerPlayer p, float perPoint) {
        return stat(p, Stat.AGI, perPoint);
    }

    private static float sense(ServerPlayer p, float perPoint) {
        return stat(p, Stat.SENSE, perPoint);
    }

    private static Vec3 mid(LivingEntity e) {
        return e.position().add(0, e.getBbHeight() * 0.55, 0);
    }

    private static long now(ServerPlayer p) {
        return p.level().getGameTime();
    }

    private static boolean shooting;
    private static boolean sparking;

    /**
     * True while a ranger's skill shot lands. An arrow loosed by a skill is as much a projectile as one from the
     * bow, so the passives that speak of projectiles count it (see {@link SubclassPassives}).
     */
    static boolean shooting() {
        return shooting;
    }

    /** Runs a hit as a shot. Also for the starters' shots in {@link SubclassActions}. */
    static void asShot(Runnable hit) {
        boolean before = shooting;
        shooting = true;
        try {
            hit.run();
        } finally {
            shooting = before;
        }
    }

    private static void shot(ServerPlayer p, Skill s, LivingEntity e, float damage) {
        asShot(() -> hit(p, s, e, damage));
    }

    /** Foes the player could shoot: in sight and in front of them, the ones nearest their aim first. */
    private static List<LivingEntity> inSight(ServerPlayer p, double range, double minDot) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        List<LivingEntity> list = new ArrayList<>(around(p, p.position(), range,
                x -> canHit(p, x) && p.hasLineOfSight(x) && mid(x).subtract(eye).normalize().dot(look) >= minDot));
        list.sort(Comparator.comparingDouble(e -> -mid(e).subtract(eye).normalize().dot(look)));
        return list;
    }

    /** Foes standing on, or flying over, a round patch of ground. */
    private static List<LivingEntity> over(ServerPlayer p, Vec3 at, double radius, double height) {
        return around(p, at, radius + height, x -> {
            if (!canHit(p, x)) return false;
            double dx = x.getX() - at.x, dz = x.getZ() - at.z, dy = x.getY() - at.y;
            return dx * dx + dz * dz <= (radius + x.getBbWidth() / 2) * (radius + x.getBbWidth() / 2) && dy > -2 && dy < height;
        });
    }

    /** Holds a creature where it stands, and remembers who holds it. */
    private static void hold(ServerPlayer p, LivingEntity e, int ticks) {
        stun(e, ticks);
        snared(p, e, ticks);
        // at once, too: the blow that came with it must not knock the creature out of what holds it
        e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
        e.hurtMarked = true;
        repeat(p, ticks, 4, () -> {
            if (!e.isAlive()) return;
            e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
            e.hurtMarked = true;
        });
    }

    /** Marks a creature as caught in one of this hunter's traps or snares for a while. */
    static void snared(ServerPlayer p, LivingEntity e, int ticks) {
        HELD.put(e, new long[]{now(p) + ticks, p.getId()});
    }

    /** A blow that does not shove. What stings or strikes many times over must not drive its victim out of reach. */
    private static void unmoved(LivingEntity e, Runnable blow) {
        Vec3 before = e.getDeltaMovement();
        blow.run();
        e.setDeltaMovement(before);
    }

    /** Whether a trap, snare, net or pin is holding the creature. Public for the game tests. */
    public static boolean caught(LivingEntity e, long now) {
        long[] held = HELD.get(e);
        return held != null && held[0] > now;
    }

    /** The summoned beasts of a hunter that are near them. */
    private static List<TamableAnimal> beasts(ServerPlayer p, double radius) {
        return p.level().getEntitiesOfClass(TamableAnimal.class, p.getBoundingBox().inflate(radius),
                t -> t.isAlive() && t.getTags().contains(SubclassActions.SUMMON_TAG) && p.getUUID().equals(t.getOwnerUUID()));
    }

    /** Bolts that walk through the foes around a point: the nearest one first, then the next, and round again. */
    private static void storm(ServerPlayer p, Skill s, Vec3 center, double radius, int from, int until, int period, Supplier<Float> damage) {
        ServerLevel level = p.serverLevel();
        Set<LivingEntity> struck = new HashSet<>();
        for (int t = from; t < until; t += period) {
            later(p, t, () -> {
                List<LivingEntity> foes = new ArrayList<>(foes(p, center, radius));
                if (foes.isEmpty()) return;
                foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
                if (struck.containsAll(foes)) struck.clear();
                LivingEntity e = foes.stream().filter(x -> !struck.contains(x)).findFirst().orElseThrow();
                struck.add(e);
                zap(p, s, e, damage.get());
                fxAt(level, Kind.THUNDER, s.color(), e.position(), 2.2f, 0);
                sound(e, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.5f);
            });
        }
    }

    /** Lifts the player off the ground and, once they are up, lets them hang there. */
    private static void rise(ServerPlayer p, double up, int hangTicks) {
        move(p, new Vec3(p.getDeltaMovement().x * 0.3, up, p.getDeltaMovement().z * 0.3));
        SkillAPI.setBuff(p, BUFF_SKYBORNE, 12 + hangTicks + 2);
        later(p, 12, () -> hover(p, true));
        later(p, 12 + hangTicks, () -> {
            hover(p, false);
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false, true));
            p.fallDistance = 0;
        });
    }

    /** No weight at all while it is on: the wind carries the player. The buff's end takes it off again (see {@link #tickSecond}). */
    private static void hover(ServerPlayer p, boolean on) {
        AttributeInstance gravity = p.getAttribute(Attributes.GRAVITY);
        if (gravity == null) return;
        if (on) {
            gravity.addOrUpdateTransientModifier(new AttributeModifier(HOVER, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            move(p, new Vec3(p.getDeltaMovement().x, 0, p.getDeltaMovement().z));
        } else {
            gravity.removeModifier(HOVER);
        }
    }

    // ================================================================ sharpshooter

    static final int RAPID_SHOTS = 6, VERDICT_ARROWS = 8;

    static int bounces(int l) { return 3 + l / 4; }
    static float pinSeconds(int l) { return 2.5f + 0.2f * l; }
    static float eyeChance(int l) { return 1.5f * l; }
    static float eyeDamage(int l) { return 3 * l; }
    static float aimBonus(int l) { return 40 + 6 * l; }
    static float patience(int l) { return 1 + 0.4f * l; }
    static float cleanMana(int l) { return 3 + l; }

    public static final Skill RICOCHET = active("ricochet", SS, A, 22, 0xD2F27A, 12, 120, 70,
            RangerPath::ricochet, (l, t) -> a(f(8 * m(l, t)), f(bounces(l))));
    public static final Skill PINNING_SHOT = active("pinning_shot", SS, A, 24, 0xB8E060, 14, 280, 170,
            RangerPath::pinningShot, (l, t) -> a(f(7 * m(l, t)), f(pinSeconds(l))));
    public static final Skill MARKSMANS_EYE = passive("marksmans_eye", SS, A, 26, 0xE0F5A0, (l, t) -> a(f(eyeChance(l)), f(eyeDamage(l))));
    public static final Skill GALE_ARROW = active("gale_arrow", SS, A, 29, 0xD8FFC0, 16, 240, 140,
            RangerPath::galeArrow, (l, t) -> a(f(9 * m(l, t))));
    public static final Skill TAKE_AIM = active("take_aim", SS, A, 32, 0xA8E84A, 10, 300, 200,
            RangerPath::takeAimCast, (l, t) -> a(f(aimBonus(l))));
    public static final Skill PATIENT_HUNTER = passive("patient_hunter", SS, A, 35, 0xCFE88A, (l, t) -> a(f(patience(l)), f(5 * patience(l))));
    public static final Skill KILLSHOT = active("killshot", SS, A, 38, 0xF2FF8A, 20, 360, 220,
            RangerPath::killshot, (l, t) -> a(f(14 * m(l, t)), f(35 * m(l, t))));
    public static final Skill RAPID_FIRE = active("rapid_fire", SS, A, 42, 0xC6F06A, 22, 400, 260,
            RangerPath::rapidFire, (l, t) -> a(f(RAPID_SHOTS), f(4 * m(l, t))));
    public static final Skill CLEAN_KILL = passive("clean_kill", SS, A, 46, 0xB0D860, (l, t) -> a(f(cleanMana(l))));
    public static final Skill HUNTERS_VERDICT = active("hunters_verdict", SS, S, 50, 0xE6FF70, 45, 1800, 1200,
            RangerPath::huntersVerdict, (l, t) -> a(f(VERDICT_ARROWS), f(28 * m(l, t))));

    /** Take Aim is spent by the next sharpshooter skill: how much harder that skill hits. */
    private static float takeAim(ServerPlayer p) {
        SkillData data = SkillAPI.get(p);
        if (!data.hasBuff(BUFF_TAKE_AIM, now(p))) return 1;
        SkillAPI.clearBuff(p, BUFF_TAKE_AIM);
        // the sight goes out
        fx(p, Kind.SIGHT, TAKE_AIM.color(), p.position(), -1, 0);
        return 1 + aimBonus(Math.max(1, data.level(TAKE_AIM.path()))) / 100f;
    }

    static boolean ricochet(ServerPlayer p, Skill s, int l) {
        LivingEntity first = aim(p, 32, x -> canHit(p, x));
        if (first == null) return false;
        float dmg = (8 * m(l, tier(p)) + agi(p, 0.2f) + sense(p, 0.1f)) * takeAim(p);
        // where it will go is settled as it leaves the string
        List<LivingEntity> path = new ArrayList<>();
        path.add(first);
        for (int i = 0; i < bounces(l); i++) {
            LivingEntity from = path.get(path.size() - 1);
            LivingEntity next = foes(p, from.position(), 9).stream().filter(e -> !path.contains(e))
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
            if (next == null) break;
            path.add(next);
        }
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 1.2f);
        fx(p, Kind.ARROW, s.color(), mid(first), 0.8f, 0);
        shot(p, s, first, dmg);
        for (int i = 1; i < path.size(); i++) {
            LivingEntity from = path.get(i - 1), e = path.get(i);
            float d = dmg * (float) Math.pow(0.85, i);
            float pitch = 1.3f + 0.12f * i;
            later(p, i * 3, () -> {
                if (!e.isAlive()) return;
                fx(from, Kind.ARROW, s.color(), mid(e), 0.8f, 0);
                shot(p, s, e, d);
                sound(e, SoundEvents.ARROW_HIT, 0.8f, pitch);
            });
        }
        return true;
    }

    static boolean pinningShot(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 32, x -> canHit(p, x));
        if (target == null) return false;
        int ticks = Math.round(pinSeconds(l) * 20);
        shot(p, s, target, (7 * m(l, tier(p)) + agi(p, 0.2f)) * takeAim(p));
        hold(p, target, ticks);
        // the bolt stays in the ground through its foot for as long as it holds
        fx(p, Kind.ARROW, s.color(), target.position().add(0, 0.05, 0), 1.2f, ticks);
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.8f);
        sound(target, SoundEvents.TRIDENT_HIT_GROUND, 1f, 1.3f);
        return true;
    }

    static boolean galeArrow(ServerPlayer p, Skill s, int l) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = aimPoint(p, 24);
        Vec3 dir = flat(end.subtract(eye), p);
        float dmg = (9 * m(l, tier(p)) + agi(p, 0.2f)) * takeAim(p);
        for (LivingEntity e : along(p, eye, end, 0.9)) {
            shot(p, s, e, dmg);
            // the wind it drags along carries them off
            e.setDeltaMovement(dir.x * 1.3, 0.35, dir.z * 1.3);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        }
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.6f);
        sound(p, SoundEvents.BREEZE_SHOOT, 0.8f, 1.2f);
        fx(p, Kind.GALE, s.color(), end, 1.3f, 0);
        return true;
    }

    static boolean takeAimCast(ServerPlayer p, Skill s, int l) {
        SkillAPI.setBuff(p, BUFF_TAKE_AIM, 160);
        sound(p, SoundEvents.SPYGLASS_USE, 1f, 1f);
        fx(p, Kind.SIGHT, s.color(), p.position(), 0, 160);
        return true;
    }

    static boolean killshot(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 40, x -> canHit(p, x));
        if (target == null) return false;
        float aimed = takeAim(p);
        // a breath to line it up, then the shot
        fx(p, Kind.SIGHT, s.color(), mid(target), 1, 6);
        sound(p, SoundEvents.SPYGLASS_USE, 0.8f, 1.4f);
        later(p, 6, () -> {
            if (!target.isAlive()) return;
            float dmg = (14 * m(l, tier(p)) + agi(p, 0.3f) + sense(p, 0.2f)) * aimed;
            boolean ending = target.getHealth() < target.getMaxHealth() * 0.35f;
            fx(p, Kind.ARROW, s.color(), mid(target), 1.6f, 0);
            shot(p, s, target, ending ? dmg * 2.5f : dmg);
            sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.5f);
            sound(target, SoundEvents.TRIDENT_HIT, 1f, ending ? 0.6f : 0.9f);
            if (ending) fxAt(p.serverLevel(), Kind.SPLASH, s.color(), mid(target), 1.6f, 1);
        });
        return true;
    }

    static boolean rapidFire(ServerPlayer p, Skill s, int l) {
        if (aim(p, 32, x -> canHit(p, x)) == null) return false;
        float aimed = takeAim(p);
        for (int i = 0; i < RAPID_SHOTS; i++) {
            float pitch = 1.2f + 0.08f * i;
            later(p, i * 3, () -> {
                // each arrow goes where the hunter is looking by then
                LivingEntity target = aim(p, 32, x -> canHit(p, x));
                fx(p, Kind.ARROW, s.color(), target != null ? mid(target) : aimPoint(p, 32), 0.85f, 0);
                sound(p, SoundEvents.ARROW_SHOOT, 0.8f, pitch);
                if (target != null) shot(p, s, target, (4 * m(l, tier(p)) + agi(p, 0.08f)) * aimed);
            });
        }
        return true;
    }

    static boolean huntersVerdict(ServerPlayer p, Skill s, int l) {
        List<LivingEntity> seen = inSight(p, 40, 0);
        if (seen.isEmpty()) return false;
        List<LivingEntity> marks = seen.size() > VERDICT_ARROWS ? seen.subList(0, VERDICT_ARROWS) : seen;
        float aimed = takeAim(p);
        for (LivingEntity e : marks) {
            fx(p, Kind.SIGHT, s.color(), mid(e), 1, 16);
            fx(e, Kind.MARK, s.color(), e.position(), e.getBbHeight(), 22);
        }
        sound(p, SoundEvents.SPYGLASS_USE, 1f, 0.7f);
        sound(p, SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f, 0.6f);
        later(p, 16, () -> {
            sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.5f);
            sound(p, SoundEvents.TRIDENT_THROW.value(), 1f, 0.8f);
        });
        // eight arrows: one for every mark, and the ones left over find the same marks again
        Set<LivingEntity> struck = new HashSet<>();
        for (int i = 0; i < VERDICT_ARROWS; i++) {
            LivingEntity e = marks.get(i % marks.size());
            later(p, 16 + i, () -> {
                if (!e.isAlive()) return;
                float dmg = (28 * m(l, tier(p)) + agi(p, 0.4f) + sense(p, 0.3f)) * aimed;
                fx(p, Kind.ARROW, s.color(), mid(e), 1.3f, 0);
                shot(p, s, e, struck.add(e) ? dmg : dmg * 0.4f);
                sound(e, SoundEvents.TRIDENT_HIT, 0.9f, 1.1f);
            });
        }
        return true;
    }

    // ================================================================ wildcaller

    static float packBonus(int l) { return 5 + 3 * l; }
    static float packMark(int l) { return 3 + 1.2f * l; }
    static float alpha(int l) { return 1 + 0.4f * l; }
    static float heartRegen(int l) { return 0.15f * l; }
    static float heartBeasts(int l) { return 0.3f * l; }

    public static final Skill HAWK_STRIKE = active("hawk_strike", WC, A, 22, 0xB8E080, 12, 140, 80,
            RangerPath::hawkStrike, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill POUNCE = active("pounce", WC, A, 24, 0x8CCB5A, 14, 220, 130,
            RangerPath::pounce, (l, t) -> a(f(9 * m(l, t))));
    public static final Skill PACK_TACTICS = passive("pack_tactics", WC, A, 26, 0xA8D878, (l, t) -> a(f(packBonus(l)), f(packMark(l))));
    public static final Skill TUSKER = active("tusker", WC, A, 29, 0xB4C86A, 16, 260, 160,
            RangerPath::tusker, (l, t) -> a(f(9 * m(l, t))));
    public static final Skill RALLYING_HOWL = active("rallying_howl", WC, A, 32, 0xC8E8A0, 18, 520, 340,
            RangerPath::rallyingHowl, (l, t) -> a(f(8)));
    public static final Skill ALPHA = passive("alpha", WC, A, 35, 0x9BD06A, (l, t) -> a(f(alpha(l)), f(4 * alpha(l))));
    public static final Skill HORNET_SWARM = active("hornet_swarm", WC, A, 38, 0xD8D24A, 18, 380, 240,
            RangerPath::hornetSwarm, (l, t) -> a(f(2 * m(l, t))));
    public static final Skill BEARS_WRATH = active("bears_wrath", WC, A, 42, 0xA6D46E, 24, 520, 340,
            RangerPath::bearsWrath, (l, t) -> a(f(16 * m(l, t))));
    public static final Skill WILD_HEART = passive("wild_heart", WC, A, 46, 0x88C860, (l, t) -> a(f(heartRegen(l)), f(heartBeasts(l))));
    public static final Skill WILD_HUNT = active("wild_hunt", WC, S, 50, 0xB6F07A, 45, 1800, 1200,
            RangerPath::wildHunt, (l, t) -> a(f(9 * m(l, t)), f(6 * m(l, t))));

    static boolean hawkStrike(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 30, x -> canHit(p, x));
        if (target == null) return false;
        sound(p, SoundEvents.PHANTOM_SWOOP, 0.9f, 1.5f);
        fx(p, Kind.BEAST, s.color(), mid(target), 1.2f, 10 * 10 + HAWK);
        later(p, 10, () -> {
            if (!target.isAlive()) return;
            hit(p, s, target, 8 * m(l, tier(p)) + agi(p, 0.2f) + sense(p, 0.1f));
            // it cannot shake the bird off: harried, it moves and hits like something half blind
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
            sound(target, SoundEvents.PHANTOM_BITE, 1f, 1.4f);
            Vec3 at = mid(target);
            p.serverLevel().sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.2);
        });
        return true;
    }

    static boolean pounce(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 14, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.WOLF_GROWL, 1f, 0.8f);
        Vec3 short_ = flat(p.position().subtract(target.position()), p).scale(target.getBbWidth() / 2 + 0.8);
        leap(p, target.position().add(short_), 0.55, land -> {
            float dmg = 9 * m(l, tier(p)) + agi(p, 0.25f);
            for (LivingEntity e : foes(p, land, 3)) {
                hit(p, s, e, dmg);
                Buildups.add(e, Buildups.BLOOD, 20, p);
                fxAt(level, Kind.CLAW, s.color(), mid(e), 1.1f, 0);
            }
            sound(level, land, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.7f);
            sound(level, land, SoundEvents.WOLF_GROWL, 0.8f, 1.3f);
        });
        return true;
    }

    static boolean tusker(ServerPlayer p, Skill s, int l) {
        Vec3 dir = facing(p);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 from = p.position();
        Vec3 to = from.add(dir.scale(14));
        sound(p, SoundEvents.HOGLIN_ANGRY, 1f, 0.9f);
        fx(p, Kind.BEAST, s.color(), to, 1.3f, 16 * 10 + BOAR);
        for (LivingEntity e : along(p, from.add(0, 0.6, 0), to.add(0, 0.6, 0), 1.4)) {
            Vec3 rel = e.position().subtract(from);
            double aside = rel.dot(side);
            // each is struck as the boar reaches it
            later(p, (int) Math.round(Mth.clamp(rel.dot(dir), 0, 14) / 14 * 16), () -> {
                if (!e.isAlive()) return;
                hit(p, s, e, 9 * m(l, tier(p)) + agi(p, 0.2f));
                Vec3 out = side.scale(aside >= 0 ? 1 : -1);
                e.setDeltaMovement(out.x * 0.8 + dir.x * 0.35, 0.45, out.z * 0.8 + dir.z * 0.35);
                e.hurtMarked = true;
                stun(e, 20);
                sound(e, SoundEvents.HOGLIN_ATTACK, 0.8f, 1.1f);
            });
        }
        return true;
    }

    static boolean rallyingHowl(ServerPlayer p, Skill s, int l) {
        int ticks = 160;
        for (Player ally : allies(p, 10)) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0));
        }
        for (TamableAnimal beast : beasts(p, 24)) {
            beast.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 1));
            beast.addEffect(new MobEffectInstance(MobEffects.REGENERATION, ticks, 1));
            // the pack stays ten seconds longer
            CompoundTag data = beast.getPersistentData();
            data.putLong(SubclassActions.SUMMON_EXPIRES, data.getLong(SubclassActions.SUMMON_EXPIRES) + 200);
        }
        for (LivingEntity e : foes(p, p.position(), 8)) {
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
        }
        sound(p, SoundEvents.WOLF_HOWL, 1.2f, 0.9f);
        fx(p, Kind.BEAST, s.color(), p.position(), 1.2f, 30 * 10 + HOWL);
        return true;
    }

    static boolean hornetSwarm(ServerPlayer p, Skill s, int l) {
        LivingEntity first = aim(p, 24, x -> canHit(p, x));
        if (first == null) return false;
        LivingEntity[] host = {first};
        sound(p, SoundEvents.BEEHIVE_EXIT, 1f, 1.2f);
        sound(first, SoundEvents.BEE_LOOP_AGGRESSIVE, 0.9f, 1.3f);
        fx(first, Kind.SWARM, s.color(), first.position(), 1.3f, 100);
        for (int t = 5; t < 100; t += 10) {
            int left = 100 - t;
            later(p, t, () -> {
                if (!host[0].isAlive()) {
                    // its victim is dead: the swarm looks for someone else
                    Vec3 at = host[0].position();
                    LivingEntity next = foes(p, at, 8).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(at))).orElse(null);
                    if (next == null) return;
                    host[0] = next;
                    fx(next, Kind.SWARM, s.color(), next.position(), 1.3f, left);
                }
                float dmg = 2 * m(l, tier(p)) + sense(p, 0.05f);
                for (LivingEntity e : foes(p, host[0].position(), 3)) {
                    unmoved(e, () -> hit(p, s, e, dmg));
                    Statuses.apply(e, DamageElement.POISON, 4 + l, p);
                }
                sound(host[0], SoundEvents.BEE_STING, 0.6f, 1.3f);
            });
        }
        return true;
    }

    static boolean bearsWrath(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 16);
        sound(level, at, SoundEvents.POLAR_BEAR_WARNING, 1.4f, 0.6f);
        fx(p, Kind.BEAST, s.color(), at, 1.6f, 12 * 10 + BEAR);
        // it rears, and then it comes down
        later(p, 12, () -> {
            for (LivingEntity e : foes(p, at, 5)) {
                hit(p, s, e, 16 * m(l, tier(p)) + agi(p, 0.2f) + sense(p, 0.2f));
                stun(e, 40);
                launch(e, 0.55);
            }
            sound(level, at, SoundEvents.RAVAGER_ROAR, 0.8f, 0.7f);
            sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 0.7f);
            fxAt(level, Kind.CRACK, s.color(), at, 5, 0);
        });
        return true;
    }

    static boolean wildHunt(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 dir = facing(p);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 center = ground(level, p.position().add(dir.scale(9)));
        sound(p, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(2).value(), 1.2f, 0.9f);
        sound(p, SoundEvents.WOLF_HOWL, 1.2f, 0.7f);
        for (TamableAnimal beast : beasts(p, 24)) {
            beast.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1));
            beast.heal(10);
        }
        for (int w = 0; w < 10; w++) {
            int wave = w;
            // four abreast, wolves and boars turn about, from behind the hunter to the far side of the field
            later(p, 4 + w * 20, () -> {
                for (int i = 0; i < 4; i++) {
                    Vec3 to = center.add(dir.scale(10)).add(side.scale(-6 + 4 * i + (level.random.nextDouble() - 0.5) * 1.5));
                    fx(p, Kind.BEAST, s.color(), to, 1.1f, 18 * 10 + ((wave + i) % 2 == 0 ? WOLF : BOAR));
                }
                sound(level, center, SoundEvents.RAVAGER_STEP, 1f, 1.2f);
            });
            later(p, 4 + w * 20 + 9, () -> {
                float dmg = 9 * m(l, tier(p)) + agi(p, 0.15f) + sense(p, 0.15f);
                List<LivingEntity> run = foes(p, center, 9);
                for (LivingEntity e : run) {
                    hit(p, s, e, dmg);
                    e.setDeltaMovement(dir.x * 0.5, 0.3, dir.z * 0.5);
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2));
                }
                // and every third wave a hawk comes down on whoever stands nearest the middle
                if (wave % 3 != 2 || run.isEmpty()) return;
                LivingEntity prey = run.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(center))).orElseThrow();
                fx(p, Kind.BEAST, s.color(), mid(prey), 1.4f, 8 * 10 + HAWK);
                sound(prey, SoundEvents.PHANTOM_SWOOP, 0.9f, 1.4f);
                later(p, 8, () -> {
                    if (prey.isAlive()) hit(p, s, prey, 6 * m(l, tier(p)) + sense(p, 0.15f));
                });
            });
        }
        return true;
    }

    // ================================================================ snare artisan

    static final int MAX_TRAPS = 3;
    static final float TRAP_SIZE = 0.75f, WIRE_LENGTH = 9;
    /** How near a jaw trap a creature must come: wide enough that one laid under a walking creature still has it when it arms. */
    static final double TRAP_REACH = 1.4;

    static int trips(int l) { return 4 + l / 3; }
    static float netSeconds(int l) { return 3 + 0.15f * l; }
    static float trapper(int l) { return 4 * l; }
    static float scavenge(int l) { return 2 + l; }
    static float killingGround(int l) { return 2 * l; }

    public static final Skill JAW_TRAP = active("jaw_trap", SA, A, 22, 0xD0B070, 10, 160, 90,
            RangerPath::jawTrap, (l, t) -> a(f(10 * m(l, t))));
    public static final Skill TRIPWIRE = active("tripwire", SA, A, 24, 0xE0C890, 12, 300, 190,
            RangerPath::tripwire, (l, t) -> a(f(trips(l)), f(6 * m(l, t))));
    public static final Skill PATIENT_TRAPPER = passive("patient_trapper", SA, A, 26, 0xC9A35A, (l, t) -> a(f(trapper(l))));
    public static final Skill WEIGHTED_NET = active("weighted_net", SA, A, 29, 0xD8C08A, 16, 340, 220,
            RangerPath::weightedNet, (l, t) -> a(f(netSeconds(l)), f(4 * m(l, t))));
    public static final Skill SPRING_SNARE = active("spring_snare", SA, A, 32, 0xC8A860, 18, 420, 280,
            RangerPath::springSnare, (l, t) -> a(f(6 * m(l, t)), f(8 * m(l, t))));
    public static final Skill SCAVENGER = passive("scavenger", SA, A, 35, 0xB89850, (l, t) -> a(f(scavenge(l))));
    public static final Skill SHRAPNEL_CHARGE = active("shrapnel_charge", SA, A, 38, 0xE0A050, 20, 360, 230,
            RangerPath::shrapnelCharge, (l, t) -> a(f(13 * m(l, t))));
    public static final Skill DEADFALL = active("deadfall", SA, A, 42, 0xB8A078, 24, 520, 340,
            RangerPath::deadfall, (l, t) -> a(f(17 * m(l, t))));
    public static final Skill KILLING_GROUND = passive("killing_ground", SA, A, 46, 0xA89060, (l, t) -> a(f(killingGround(l))));
    public static final Skill NO_WAY_OUT = active("no_way_out", SA, S, 50, 0xE0B860, 45, 1800, 1200,
            RangerPath::noWayOut, (l, t) -> a(f(7 * m(l, t)), f(30 * m(l, t))));

    private static final class Trap {
        final Vec3 at;
        final long armedAt, until;
        final int level;
        boolean gone;

        Trap(Vec3 at, long armedAt, long until, int level) {
            this.at = at;
            this.armedAt = armedAt;
            this.until = until;
            this.level = level;
        }
    }

    /** How many jaw traps the hunter has lying armed. Public for the game tests. */
    public static int trapsSet(ServerPlayer p) {
        long now = now(p);
        List<Trap> mine = TRAPS.get(p.getUUID());
        return mine == null ? 0 : (int) mine.stream().filter(t -> !t.gone && t.until > now).count();
    }

    /** How much worse the Patient Trapper's traps and snares are. Also for the Snare Field in {@link SubclassActions}. */
    static float trapPower(ServerPlayer p) {
        return 1 + trapper(SkillAPI.level(p, PATIENT_TRAPPER)) / 100f;
    }

    /** What a trap or snare does to what it catches. */
    private static void snare(ServerPlayer p, Skill s, LivingEntity e, float damage) {
        hit(p, s, e, damage * trapPower(p));
    }

    /** Rocks that fall from above onto a spot and are gone when they land: nothing is left in the world. */
    private static void rockfall(ServerLevel level, Vec3 at, int count, double spread) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            double ang = level.random.nextDouble() * Math.PI * 2, rad = i == 0 ? 0 : Math.sqrt(level.random.nextDouble()) * spread;
            double x = at.x + Math.cos(ang) * rad, z = at.z + Math.sin(ang) * rad;
            // from as high as there is room for, up to nine blocks; under a low roof there is no rock to fall
            int room = 0;
            while (room < 9 && level.getBlockState(pos.set(x, at.y + 1 + room, z)).isAir()) room++;
            if (room < 3) continue;
            // only ever started in air, so the block it "falls from" is air before and after
            FallingBlockEntity rock = FallingBlockEntity.fall(level, BlockPos.containing(x, at.y + room, z), ROCKS[level.random.nextInt(ROCKS.length)]);
            rock.dropItem = false;
            rock.disableDrop();
            rock.setDeltaMovement(0, -0.7 - level.random.nextDouble() * 0.1, 0);
            // it was shown to those watching before it had this speed: tell them, or they see it drift down from rest
            rock.hurtMarked = true;
        }
    }

    static boolean jawTrap(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 16);
        long now = now(p);
        List<Trap> mine = TRAPS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        mine.removeIf(t -> t.gone || t.until <= now);
        // a hunter keeps three set: a fourth takes the oldest one up
        while (mine.size() >= MAX_TRAPS) {
            Trap old = mine.remove(0);
            old.gone = true;
            fxAt(level, Kind.TRAP, s.color(), old.at, 0, 0);
        }
        Trap trap = new Trap(at, now + 10, now + 600, l);
        mine.add(trap);
        sound(level, at, SoundEvents.IRON_TRAPDOOR_OPEN, 0.8f, 1.4f);
        sound(level, at, SoundEvents.CHAIN_PLACE, 0.8f, 0.8f);
        fxAt(level, Kind.TRAP, s.color(), at, TRAP_SIZE, 600);
        watch(p, s, trap);
        return true;
    }

    /** Looks every other tick for something stepping into the trap, until it springs or its half minute is over. */
    private static void watch(ServerPlayer p, Skill s, Trap trap) {
        later(p, 2, () -> {
            if (trap.gone) return;
            long now = now(p);
            if (now >= trap.until) {
                trap.gone = true;
                return;
            }
            if (now >= trap.armedAt) {
                LivingEntity victim = foes(p, trap.at, TRAP_REACH).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(trap.at))).orElse(null);
                if (victim != null) {
                    trap.gone = true;
                    ServerLevel level = p.serverLevel();
                    snare(p, s, victim, 10 * m(trap.level, tier(p)) + sense(p, 0.3f));
                    hold(p, victim, 60);
                    Buildups.add(victim, Buildups.BLOOD, 25, p);
                    sound(level, trap.at, SoundEvents.IRON_TRAPDOOR_CLOSE, 1.2f, 0.7f);
                    sound(level, trap.at, SoundEvents.CHAIN_BREAK, 1f, 0.8f);
                    // duration 0: the open jaws are gone from where they lay, and snap shut on the creature's legs
                    fxAt(level, Kind.TRAP, s.color(), trap.at, 0, 0);
                    fxAt(level, Kind.TRAP, s.color(), victim.position(), TRAP_SIZE, 0);
                    return;
                }
            }
            watch(p, s, trap);
        });
    }

    static boolean tripwire(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 dir = facing(p);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 center = aimGround(p, 10);
        Vec3 a = center.add(side.scale(-WIRE_LENGTH / 2)).add(0, 0.35, 0), b = center.add(side.scale(WIRE_LENGTH / 2)).add(0, 0.35, 0);
        sound(level, center, SoundEvents.TRIPWIRE_ATTACH, 1f, 1f);
        // strung across the way the hunter faces
        fx(p, Kind.WIRE, s.color(), center, WIRE_LENGTH, 400);
        watchWire(p, s, l, center, a, b, new int[]{trips(l)}, new HashSet<>(), now(p) + 6, now(p) + 400);
        return true;
    }

    private static void watchWire(ServerPlayer p, Skill s, int l, Vec3 center, Vec3 a, Vec3 b, int[] left, Set<Integer> tripped, long armedAt, long until) {
        later(p, 2, () -> {
            long now = now(p);
            if (now >= until) return;
            ServerLevel level = p.serverLevel();
            if (now >= armedAt) {
                for (LivingEntity e : along(p, a, b, 0.4)) {
                    if (!tripped.add(e.getId())) continue;
                    snare(p, s, e, 6 * m(l, tier(p)) + sense(p, 0.2f));
                    stun(e, 40);
                    snared(p, e, 40);
                    // off its feet
                    e.setDeltaMovement(0, 0.32, 0);
                    e.hurtMarked = true;
                    sound(e, SoundEvents.TRIPWIRE_CLICK_ON, 1f, 0.8f);
                    fxAt(level, Kind.SPLASH, s.color(), e.position().add(0, 0.3, 0), 1.2f, 1);
                    if (--left[0] <= 0) {
                        // it has done its work and snaps
                        sound(level, center, SoundEvents.CHAIN_BREAK, 0.8f, 1.6f);
                        fxAt(level, Kind.WIRE, s.color(), center, 0, 0);
                        return;
                    }
                }
            }
            watchWire(p, s, l, center, a, b, left, tripped, armedAt, until);
        });
    }

    static boolean weightedNet(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 18);
        int ticks = Math.round(netSeconds(l) * 20);
        sound(p, SoundEvents.FISHING_BOBBER_THROW, 1f, 0.6f);
        fx(p, Kind.NET, s.color(), at, 3.5f, ticks);
        later(p, 5, () -> {
            for (LivingEntity e : over(p, at, 3.5, 8)) {
                snare(p, s, e, 4 * m(l, tier(p)) + sense(p, 0.15f));
                hold(p, e, ticks);
                // what flies is dragged down
                if (!e.onGround()) {
                    e.setDeltaMovement(e.getDeltaMovement().x * 0.2, -0.9, e.getDeltaMovement().z * 0.2);
                    e.hurtMarked = true;
                }
            }
            sound(level, at, SoundEvents.LEASH_KNOT_PLACE, 1.2f, 0.6f);
        });
        return true;
    }

    static boolean springSnare(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 18, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        snare(p, s, target, 6 * m(l, tier(p)) + sense(p, 0.2f));
        sound(target, SoundEvents.LEASH_KNOT_PLACE, 1f, 0.7f);
        sound(target, SoundEvents.FISHING_BOBBER_RETRIEVE, 1f, 0.6f);
        if (target.getBbWidth() > 2.2f || target.getBbHeight() > 4f) {
            // too heavy to lift: the noose only hobbles it
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 3));
            snared(p, target, 80);
            return true;
        }
        Vec3 dir = facing(p);
        Vec3 foot = ground(level, target.position().add(-dir.z * 1.8, 0, dir.x * 1.8));
        stun(target, 70);
        snared(p, target, 70);
        target.setDeltaMovement(0, 0.85, 0);
        target.hurtMarked = true;
        fx(target, Kind.ROPE, s.color(), foot, 1, 62);
        // it hangs for as long as nothing lets it move
        for (int t = 9; t < 60; t++) {
            later(p, t, () -> {
                if (!target.isAlive()) return;
                target.setDeltaMovement(0, 0, 0);
                target.hurtMarked = true;
                target.fallDistance = 0;
            });
        }
        later(p, 60, () -> {
            if (!target.isAlive()) return;
            target.setDeltaMovement(0, -1.4, 0);
            target.hurtMarked = true;
        });
        later(p, 66, () -> {
            if (!target.isAlive()) return;
            snare(p, s, target, 8 * m(l, tier(p)) + sense(p, 0.2f));
            stun(target, 20);
            sound(target, SoundEvents.PLAYER_BIG_FALL, 1f, 0.8f);
            level.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + 0.1, target.getZ(), 8, 0.4, 0.1, 0.4, 0.02);
        });
        return true;
    }

    static boolean shrapnelCharge(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 16);
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.9f);
        sound(level, at, SoundEvents.TNT_PRIMED, 1f, 1.3f);
        // the charge rides an arrow into the ground and sits there, lit
        fx(p, Kind.ARROW, s.color(), at, 0.9f, 16);
        fxAt(level, Kind.ORB, s.color(), at.add(0, 0.25, 0), 0.16f, 16);
        later(p, 16, () -> {
            for (LivingEntity e : foes(p, at, 4.5)) {
                snare(p, s, e, 13 * m(l, tier(p)) + sense(p, 0.3f));
                push(e, at, 0.9, 0.45);
                Buildups.add(e, Buildups.BLOOD, 20, p);
            }
            sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 1.2f);
            fxAt(level, Kind.GEYSER, s.color(), at, 3.5f, 1);
            fxAt(level, Kind.SPLASH, s.color(), at.add(0, 0.5, 0), 4.5f, 1);
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.6, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(GRIT, at.x, at.y + 0.4, at.z, 30, 0.5, 0.3, 0.5, 0.6);
        });
        return true;
    }

    static boolean deadfall(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 20);
        sound(level, at, SoundEvents.TRIPWIRE_CLICK_ON, 1f, 0.6f);
        sound(level, at, SoundEvents.CHAIN_BREAK, 1f, 0.6f);
        rockfall(level, at, 6, 2.6);
        later(p, 11, () -> {
            for (LivingEntity e : foes(p, at, 4)) {
                snare(p, s, e, 17 * m(l, tier(p)) + sense(p, 0.3f));
                stun(e, 50);
                snared(p, e, 50);
            }
            sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 0.6f);
            sound(level, at, SoundEvents.DEEPSLATE_BREAK, 1.5f, 0.6f);
            fxAt(level, Kind.QUAKE, s.color(), at, 4, 16);
            level.sendParticles(GRIT, at.x, at.y + 0.3, at.z, 40, 1.6, 0.3, 1.6, 0.3);
            level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.3, at.z, 14, 1.6, 0.2, 1.6, 0.03);
        });
        return true;
    }

    static boolean noWayOut(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = aimGround(p, 20);
        sound(level, center, SoundEvents.IRON_TRAPDOOR_OPEN, 1.2f, 0.7f);
        sound(level, center, SoundEvents.CHAIN_PLACE, 1.2f, 0.6f);
        // the ground is seeded with jaws: some of them are seen
        for (int i = 0; i < 9; i++) {
            double ang = i * Math.PI * 2 / 9 + level.random.nextDouble() * 0.5, rad = 2.5 + level.random.nextDouble() * 5.5;
            fxAt(level, Kind.TRAP, s.color(), ground(level, center.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad)), TRAP_SIZE, 200);
        }
        repeat(p, 200, 40, () -> fxAt(level, Kind.PULSE, s.color(), center, 9, 1));
        repeat(p, 200, 20, () -> {
            boolean any = false;
            for (LivingEntity e : foes(p, center, 9)) {
                any = true;
                snare(p, s, e, 7 * m(l, tier(p)) + sense(p, 0.15f));
                hold(p, e, 25);
                Buildups.add(e, Buildups.BLOOD, 8, p);
                fxAt(level, Kind.TRAP, s.color(), e.position(), TRAP_SIZE * 0.9f, 0);
            }
            if (any) sound(level, center, SoundEvents.IRON_TRAPDOOR_CLOSE, 1f, 0.8f);
        });
        // and when everything in it is caught, the roof comes down
        later(p, 189, () -> rockfall(level, center, 14, 8));
        later(p, 200, () -> {
            for (LivingEntity e : foes(p, center, 9)) {
                snare(p, s, e, 30 * m(l, tier(p)) + sense(p, 0.5f));
                stun(e, 60);
                snared(p, e, 60);
            }
            sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.5f);
            sound(level, center, SoundEvents.DEEPSLATE_BREAK, 1.5f, 0.5f);
            fxAt(level, Kind.QUAKE, s.color(), center, 9, 20);
            fxAt(level, Kind.CRACK, s.color(), center, 9, 0);
            level.sendParticles(GRIT, center.x, center.y + 0.3, center.z, 80, 4, 0.3, 4, 0.3);
        });
        return true;
    }

    // ================================================================ storm archer

    static float conductive(int l) { return 4 + 1.6f * l; }
    static float battery(int l) { return 0.5f + 0.15f * l; }

    public static final Skill LIGHTNING_ROD = active("lightning_rod", ST, A, 22, 0xA8D8FF, 12, 240, 150,
            RangerPath::lightningRod, (l, t) -> a(f(3 * m(l, t))));
    public static final Skill FLASH_STEP = active("flash_step", ST, A, 24, 0xD0ECFF, 14, 220, 130,
            RangerPath::flashStep, (l, t) -> a(f(8 * m(l, t))));
    public static final Skill CONDUCTIVE = passive("conductive", ST, A, 26, 0xB8E2FF, (l, t) -> a(f(conductive(l))));
    public static final Skill WANDERING_SPARK = active("wandering_spark", ST, A, 29, 0x98D0FF, 18, 320, 200,
            RangerPath::wanderingSpark, (l, t) -> a(f(2 * m(l, t)), f(8 * m(l, t))));
    public static final Skill LIVE_WIRE = active("live_wire", ST, A, 32, 0xC8E8FF, 20, 600, 400,
            RangerPath::liveWire, (l, t) -> a(f(10), f(3 * m(l, t))));
    public static final Skill STORM_BATTERY = passive("storm_battery", ST, A, 35, 0xA0D8F0, (l, t) -> a(f(battery(l))));
    public static final Skill THUNDERHEAD = active("thunderhead", ST, A, 38, 0x90C4F0, 22, 480, 320,
            RangerPath::thunderhead, (l, t) -> a(f(7 * m(l, t))));
    public static final Skill THUNDERBOLT_ARROW = active("thunderbolt_arrow", ST, A, 42, 0xE0F2FF, 24, 420, 270,
            RangerPath::thunderboltArrow, (l, t) -> a(f(18 * m(l, t)), f(9 * m(l, t))));
    public static final Skill CHAIN_REACTION = passive("chain_reaction", ST, A, 46, 0xB0DCFF, (l, t) -> a(f(5 * m(l, t))));
    public static final Skill STORMFALL = active("stormfall", ST, S, 50, 0xA8D4FF, 45, 1800, 1200,
            RangerPath::stormfall, (l, t) -> a(f(9 * m(l, t)), f(22 * m(l, t))));

    /**
     * Lightning from a storm archer's skill: it goes through armour and leaves static on what it strikes (see
     * {@link #CONDUCTIVE}), and the Storm Battery fills a little.
     */
    private static void zap(ServerPlayer p, Skill s, LivingEntity e, float damage) {
        // a jolt, not a shove: a storm that struck its victims away would strike each of them once
        unmoved(e, () -> asShot(() -> magic(p, s, e, damage)));
        long now = now(p);
        CHARGED.put(e, new long[]{now + 120, p.getId()});
        int battery = SkillAPI.level(p, STORM_BATTERY);
        if (battery > 0 && now - LAST_BATTERY.getOrDefault(p.getUUID(), 0L) >= 10) {
            LAST_BATTERY.put(p.getUUID(), now);
            SystemAPI.restoreMana(p, battery(battery));
        }
    }

    static boolean lightningRod(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 20);
        Vec3 top = at.add(0, 1.0, 0);
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 1f);
        sound(level, at, SoundEvents.TRIDENT_HIT_GROUND, 1f, 1.2f);
        // an arrow planted in the ground, humming
        fx(p, Kind.ARROW, s.color(), at, 1.1f, 124);
        fxAt(level, Kind.STATIC, s.color(), at, 0.8f, 124);
        for (int t = 4; t < 124; t += 20) {
            later(p, t, () -> {
                boolean any = false;
                for (LivingEntity e : foes(p, at, 6)) {
                    any = true;
                    zap(p, s, e, 3 * m(l, tier(p)) + agi(p, 0.06f));
                    // a bolt is the same from either end
                    fx(e, Kind.BOLT, s.color(), top, 0.2f, 0);
                }
                if (any) sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4f, 1.8f);
            });
        }
        return true;
    }

    static boolean flashStep(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 from = blinkForward(p, 10);
        Vec3 to = p.position();
        if (to.distanceToSqr(from) < 1) return false;
        for (LivingEntity e : along(p, from.add(0, 1, 0), to.add(0, 1, 0), 1.5)) {
            zap(p, s, e, 8 * m(l, tier(p)) + agi(p, 0.2f));
            stun(e, 20);
        }
        sound(level, from, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 1.7f);
        sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.3f, 2f);
        // the bolt they were, from where they stood to where they stand
        fx(p, Kind.BOLT, s.color(), from.add(0, 1, 0), 0.3f, 0);
        fxAt(level, Kind.NOVA, s.color(), from, 2, 1);
        fx(p, Kind.NOVA, s.color(), to, 2.5f, 1);
        return true;
    }

    static boolean wanderingSpark(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 start = p.position().add(0, 1.2, 0);
        Vec3 dir = facing(p);
        // it stops at the first wall
        var wall = level.clip(new net.minecraft.world.level.ClipContext(start, start.add(dir.scale(16)), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, p));
        Vec3 end = wall.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? start.add(dir.scale(16)) : wall.getLocation().subtract(dir.scale(0.5));
        int ticks = Math.max(10, (int) Math.round(end.distanceTo(start) / 16 * 60));
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.7f);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 0.5f, 1.9f);
        fx(p, Kind.BALL, s.color(), end, 0.55f, ticks);
        for (int t = 2; t < ticks; t += 5) {
            Vec3 at = start.lerp(end, t / (double) ticks);
            later(p, t, () -> {
                for (LivingEntity e : foes(p, at, 3.5)) {
                    zap(p, s, e, 2 * m(l, tier(p)) + agi(p, 0.04f));
                    fx(e, Kind.BOLT, s.color(), at, 0.15f, 0);
                }
            });
        }
        later(p, ticks, () -> {
            for (LivingEntity e : foes(p, end, 4)) zap(p, s, e, 8 * m(l, tier(p)) + agi(p, 0.15f));
            sound(level, end, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.4f);
            fxAt(level, Kind.NOVA, s.color(), end.add(0, -1, 0), 4, 1);
            fxAt(level, Kind.SPLASH, s.color(), end, 2.5f, 1);
        });
        return true;
    }

    static boolean liveWire(ServerPlayer p, Skill s, int l) {
        SkillAPI.setBuff(p, BUFF_LIVE_WIRE, 200);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0, false, false, true));
        sound(p, SoundEvents.TRIDENT_THUNDER.value(), 0.35f, 1.8f);
        sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.6f);
        fx(p, Kind.STATIC, s.color(), p.position(), 0.9f, 200);
        return true;
    }

    static boolean thunderhead(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = aimGround(p, 24);
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.7f);
        sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.4f);
        // an arrow into the sky above the spot, and the sky takes it badly
        fx(p, Kind.ARROW, s.color(), center.add(0, 7, 0), 0.9f, 0);
        fxAt(level, Kind.CLOUD, s.color(), center, 6, 160);
        storm(p, s, center, 6, 10, 160, 12, () -> 7 * m(l, tier(p)) + agi(p, 0.12f));
        return true;
    }

    static boolean thunderboltArrow(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 32, x -> canHit(p, x));
        if (target == null) return false;
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.6f);
        fx(p, Kind.ARROW, s.color(), target.position().add(0, 24, 0), 1.1f, 0);
        // up it goes, and what comes back down is not an arrow
        later(p, 12, () -> {
            Vec3 at = target.position();
            float dmg = 18 * m(l, tier(p)) + agi(p, 0.3f) + sense(p, 0.2f);
            if (target.isAlive()) {
                zap(p, s, target, dmg);
                stun(target, 50);
            }
            for (LivingEntity e : foes(p, at, 5)) if (e != target) zap(p, s, e, dmg * 0.5f);
            fxAt(level, Kind.THUNDER, s.color(), at, 5, 0);
            sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.9f, 1.1f);
            sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1f, 0.9f);
        });
        return true;
    }

    static boolean stormfall(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 center = p.position();
        SkillAPI.setBuff(p, BUFF_LIVE_WIRE, 240);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, 0, false, false, true));
        sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, 0.7f);
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.5f);
        fx(p, Kind.ARROW, s.color(), center.add(0, 9, 0), 1.3f, 0);
        fx(p, Kind.STATIC, s.color(), center, 0.9f, 240);
        fxAt(level, Kind.CLOUD, s.color(), center, 12, 240);
        storm(p, s, center, 12, 6, 236, 10, () -> 9 * m(l, tier(p)) + agi(p, 0.15f));
        // it ends with every bolt it has left, all at once
        later(p, 240, () -> {
            int shown = 0;
            for (LivingEntity e : foes(p, center, 12)) {
                zap(p, s, e, 22 * m(l, tier(p)) + agi(p, 0.4f));
                stun(e, 40);
                if (shown++ < 12) fxAt(level, Kind.THUNDER, s.color(), e.position(), 3, 0);
            }
            sound(level, center, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2f, 0.8f);
        });
        return true;
    }

    // ================================================================ heavenpiercer

    static final int SEEKERS = 5;

    static float ascendBonus(int l) { return 25 + 3 * l; }
    static float skyGrace(int l) { return 2 * l; }
    static int haloArrows(int l) { return 6 + l / 3; }
    static float unerring(int l) { return 2 * l; }
    static float killingWind(int l) { return 1 + 0.4f * l; }

    public static final Skill STARSEEKERS = active("starseekers", HP, S, 22, 0xDFFFEA, 14, 140, 80,
            RangerPath::starseekers, (l, t) -> a(f(SEEKERS), f(6 * m(l, t))));
    public static final Skill ASCEND = active("ascend", HP, S, 24, 0xE8FFF0, 14, 400, 260,
            RangerPath::ascend, (l, t) -> a(f(ascendBonus(l))));
    public static final Skill SKYS_GRACE = passive("skys_grace", HP, S, 26, 0xD8F8E8, (l, t) -> a(f(skyGrace(l))));
    public static final Skill FALLING_STAR = active("falling_star", HP, S, 29, 0xF0FFE0, 18, 200, 120,
            RangerPath::fallingStar, (l, t) -> a(f(13 * m(l, t))));
    public static final Skill ARROW_HALO = active("arrow_halo", HP, S, 32, 0xE0FFF4, 20, 520, 340,
            RangerPath::arrowHalo, (l, t) -> a(f(haloArrows(l)), f(7 * m(l, t))));
    public static final Skill UNERRING = passive("unerring", HP, S, 35, 0xD0F0E0, (l, t) -> a(f(unerring(l)), f(l)));
    public static final Skill WINDCUTTER = active("windcutter", HP, S, 38, 0xD8FFF0, 20, 260, 160,
            RangerPath::windcutter, (l, t) -> a(f(14 * m(l, t))));
    public static final Skill GALE_BARRIER = active("gale_barrier", HP, S, 42, 0xC8F4E8, 22, 600, 400,
            RangerPath::galeBarrier, (l, t) -> a(f(5 * m(l, t))));
    public static final Skill KILLING_WIND = passive("killing_wind", HP, S, 46, 0xE0FFE8, (l, t) -> a(f(killingWind(l)), f(5 * killingWind(l))));
    public static final Skill FALL_OF_HEAVEN = active("fall_of_heaven", HP, S, 50, 0xF4FFF8, 45, 1800, 1200,
            RangerPath::fallOfHeaven, (l, t) -> a(f(9 * m(l, t)), f(40 * m(l, t))));

    static boolean starseekers(ServerPlayer p, Skill s, int l) {
        List<LivingEntity> seen = inSight(p, 30, 0.2);
        if (seen.isEmpty()) return false;
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 1.3f);
        sound(p, SoundEvents.BREEZE_SHOOT, 0.6f, 1.6f);
        // five arrows, one for each of the five nearest the hunter's aim; with fewer foes the rest double up
        Set<LivingEntity> struck = new HashSet<>();
        for (int i = 0; i < SEEKERS; i++) {
            LivingEntity e = seen.get(i % seen.size());
            later(p, i * 2, () -> {
                if (!e.isAlive()) return;
                fx(p, Kind.SEEKER, s.color(), mid(e), 1, 8);
                later(p, 8, () -> {
                    if (!e.isAlive()) return;
                    float dmg = 6 * m(l, tier(p)) + agi(p, 0.15f) + sense(p, 0.1f);
                    shot(p, s, e, struck.add(e) ? dmg : dmg * 0.5f);
                    sound(e, SoundEvents.ARROW_HIT, 0.8f, 1.6f);
                });
            });
        }
        return true;
    }

    static boolean ascend(ServerPlayer p, Skill s, int l) {
        sound(p, SoundEvents.BREEZE_JUMP, 1f, 0.8f);
        sound(p, SoundEvents.ELYTRA_FLYING, 0.3f, 1.6f);
        fx(p, Kind.UPDRAFT, s.color(), p.position(), 1.1f, 36);
        rise(p, 1.15, 80);
        return true;
    }

    static boolean fallingStar(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 32);
        sound(p, SoundEvents.CROSSBOW_SHOOT, 1f, 0.9f);
        fxAt(level, Kind.SKYSHOT, s.color(), at, 1.6f, 10);
        later(p, 10, () -> {
            for (LivingEntity e : foes(p, at, 3.5)) {
                shot(p, s, e, 13 * m(l, tier(p)) + agi(p, 0.3f) + sense(p, 0.15f));
                launch(e, 0.55);
            }
            sound(level, at, SoundEvents.TRIDENT_HIT_GROUND, 1.2f, 0.7f);
            sound(level, at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 0.9f);
        });
        return true;
    }

    static boolean arrowHalo(ServerPlayer p, Skill s, int l) {
        int[] left = {haloArrows(l)};
        long cast = now(p);
        // a new halo takes the place of the old one
        HALO.put(p.getUUID(), cast);
        sound(p, SoundEvents.ARROW_SHOOT, 0.8f, 0.6f);
        sound(p, SoundEvents.BREEZE_IDLE_AIR, 0.8f, 1.4f);
        fx(p, Kind.QUIVER, s.color(), p.position(), left[0], 240);
        for (int t = 4; t < 240; t += 8) {
            int remaining = 240 - t;
            later(p, t, () -> {
                if (left[0] <= 0 || HALO.getOrDefault(p.getUUID(), 0L) != cast) return;
                LivingEntity e = foes(p, p.position(), 10).stream().filter(p::hasLineOfSight)
                        .min(Comparator.comparingDouble(x -> x.distanceToSqr(p))).orElse(null);
                if (e == null) return;
                left[0]--;
                fx(p, Kind.QUIVER, s.color(), p.position(), left[0], remaining);
                fx(p, Kind.SEEKER, s.color(), mid(e), 0.8f, 5);
                sound(p, SoundEvents.ARROW_SHOOT, 0.7f, 1.6f);
                later(p, 5, () -> {
                    if (e.isAlive()) shot(p, s, e, 7 * m(l, tier(p)) + agi(p, 0.15f));
                });
            });
        }
        return true;
    }

    static boolean windcutter(ServerPlayer p, Skill s, int l) {
        Vec3 dir = facing(p);
        Vec3 from = p.position().add(0, 1, 0);
        Vec3 to = from.add(dir.scale(16));
        float dmg = 14 * m(l, tier(p)) + agi(p, 0.3f) + sense(p, 0.1f);
        for (LivingEntity e : along(p, from, to, 2.5)) {
            // struck as the blade of wind reaches them
            later(p, (int) Math.round(Mth.clamp(e.position().subtract(from).dot(dir), 0, 16) * 0.4), () -> {
                if (!e.isAlive()) return;
                shot(p, s, e, dmg);
                e.setDeltaMovement(dir.x, 0.3, dir.z);
                e.hurtMarked = true;
            });
        }
        sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f, 0.6f);
        sound(p, SoundEvents.BREEZE_SHOOT, 1f, 0.7f);
        fx(p, Kind.WAVE, s.color(), p.position().add(dir.scale(16)), 2.5f, 3);
        return true;
    }

    static boolean galeBarrier(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 dir = facing(p);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 center = p.position().add(dir.scale(2.5));
        double half = 4.5;
        Vec3 a = center.add(side.scale(-half)).add(0, 1, 0), b = center.add(side.scale(half)).add(0, 1, 0);
        Map<Integer, Long> stung = new HashMap<>();
        sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1f, 0.6f);
        sound(p, SoundEvents.ELYTRA_FLYING, 0.4f, 1.3f);
        fx(p, Kind.WINDWALL, s.color(), center, (float) (half * 2), 160);
        repeat(p, 160, 4, () -> {
            long now = now(p);
            for (LivingEntity e : along(p, a, b, 1.2)) {
                // thrown back to the far side, whichever side it came from
                e.setDeltaMovement(dir.x * 1.1, 0.3, dir.z * 1.1);
                e.hurtMarked = true;
                if (stung.getOrDefault(e.getId(), 0L) > now) continue;
                stung.put(e.getId(), now + 20);
                shot(p, s, e, 5 * m(l, tier(p)) + agi(p, 0.1f));
            }
            // and nothing thrown or shot at the hunter comes through it
            for (Projectile shotAt : level.getEntitiesOfClass(Projectile.class, new AABB(a, b).inflate(1.5, 2.5, 1.5), x -> !(x.getOwner() instanceof Player))) {
                Vec3 rel = shotAt.position().subtract(center);
                if (Math.abs(rel.dot(dir)) > 1.4 || Math.abs(rel.dot(side)) > half) continue;
                level.sendParticles(ParticleTypes.CLOUD, shotAt.getX(), shotAt.getY(), shotAt.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
                shotAt.discard();
            }
        });
        return true;
    }

    static boolean fallOfHeaven(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        sound(p, SoundEvents.BREEZE_JUMP, 1f, 0.6f);
        sound(p, SoundEvents.ENDER_DRAGON_FLAP, 0.8f, 1.3f);
        fx(p, Kind.UPDRAFT, s.color(), p.position(), 1.3f, 40);
        fx(p, Kind.QUIVER, 0xFFFFFF, p.position(), 9, 156);
        rise(p, 1.1, 144);
        // from up there: an arrow every quarter second, the nearest foe first, then the next, and round again
        Set<LivingEntity> struck = new HashSet<>();
        Vec3[] last = {null};
        for (int t = 14; t < 150; t += 5) {
            later(p, t, () -> {
                List<LivingEntity> foes = new ArrayList<>(foes(p, p.position(), 32));
                if (foes.isEmpty()) return;
                foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
                if (struck.containsAll(foes)) struck.clear();
                LivingEntity e = foes.stream().filter(x -> !struck.contains(x)).findFirst().orElseThrow();
                struck.add(e);
                last[0] = e.position();
                float dmg = 9 * m(l, tier(p)) + agi(p, 0.2f) + sense(p, 0.1f);
                fx(p, Kind.ARROW, s.color(), e.position().add(0, 0.1, 0), 1.3f, 8);
                // straight down: they nail what they hit to the spot, so the last arrow finds everyone still there
                unmoved(e, () -> shot(p, s, e, dmg));
                for (LivingEntity near : foes(p, e.position(), 2)) if (near != e) unmoved(near, () -> shot(p, s, near, dmg * 0.5f));
                sound(p, SoundEvents.ARROW_SHOOT, 0.8f, 0.8f);
                sound(e, SoundEvents.TRIDENT_HIT_GROUND, 0.8f, 1.2f);
            });
        }
        // and the last one is the size of a tower
        later(p, 146, () -> {
            Vec3 at = last[0] != null ? last[0] : aimGround(p, 40);
            fxAt(level, Kind.SKYSHOT, s.color(), at, 3.4f, 8);
            sound(p, SoundEvents.TRIDENT_THROW.value(), 1.2f, 0.5f);
            later(p, 8, () -> {
                for (LivingEntity e : foes(p, at, 7)) {
                    shot(p, s, e, 40 * m(l, tier(p)) + agi(p, 0.6f) + sense(p, 0.4f));
                    launch(e, 0.8);
                }
                sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.6f);
                sound(level, at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.2f, 0.6f);
                fxAt(level, Kind.CRACK, s.color(), at, 7, 0);
            });
        });
        return true;
    }

    // ================================================================ passives

    static {
        bonus(MARKSMANS_EYE, ALObjects.Attributes.CRIT_CHANCE, 0.015f, ADD, "crit_chance");
        bonus(MARKSMANS_EYE, ALObjects.Attributes.CRIT_DAMAGE, 0.03f, ADD, "crit_damage");
        bonus(UNERRING, ALObjects.Attributes.CRIT_CHANCE, 0.01f, ADD, "crit_chance");
    }

    /** A wildcaller's beast bites. */
    static float beastStrike(TamableAnimal beast, LivingEntity target, float amount, long now) {
        if (!beast.getTags().contains(SubclassActions.SUMMON_TAG) || !(beast.getOwner() instanceof ServerPlayer owner)) return amount;
        return packBite(owner, target, amount, now);
    }

    /**
     * Pack Tactics: a beast of this hunter's pack bites harder, and what it bit is marked for the hunter. Returns
     * the damage of the bite. Public for the game tests: a wolf does not find a test player to call its owner.
     */
    public static float packBite(ServerPlayer owner, LivingEntity target, float amount, long now) {
        int pack = SkillAPI.level(owner, PACK_TACTICS);
        if (pack <= 0) return amount;
        BITTEN.put(target, new long[]{now + 100, owner.getId()});
        return amount * (1 + packBonus(pack) / 100f);
    }

    /** Extra damage, in percent, when a ranger subclass player strikes. */
    static float strikeBonus(ServerPlayer p, SkillData data, LivingEntity target, long now) {
        float bonus = 0;
        int patient = data.level(PATIENT_HUNTER.path());
        if (patient > 0) {
            double[] focus = FOCUS.get(p.getUUID());
            if (focus != null) bonus += (float) focus[0] * patience(patient);
        }
        int pack = data.level(PACK_TACTICS.path());
        if (pack > 0) {
            long[] bitten = BITTEN.get(target);
            if (bitten != null && bitten[0] > now && bitten[1] == p.getId()) bonus += packMark(pack);
        }
        int alpha = data.level(ALPHA.path());
        if (alpha > 0) bonus += Math.min(4, beasts(p, 16).size()) * alpha(alpha);
        int conductive = data.level(CONDUCTIVE.path());
        if (conductive > 0) {
            long[] charged = CHARGED.get(target);
            if (charged != null && charged[0] > now && charged[1] == p.getId()) bonus += conductive(conductive);
        }
        if (data.hasBuff(BUFF_SKYBORNE, now)) bonus += ascendBonus(Math.max(1, data.level(ASCEND.path())));
        int grace = data.level(SKYS_GRACE.path());
        if (grace > 0 && !p.onGround()) bonus += skyGrace(grace);
        bonus += unerring(data.level(UNERRING.path()));
        int wind = data.level(KILLING_WIND.path());
        if (wind > 0) {
            long[] kills = WIND.get(p.getUUID());
            if (kills != null && kills[1] > now) bonus += kills[0] * killingWind(wind);
        }
        return bonus;
    }

    static void afterStrike(ServerPlayer p, SkillData data, LivingEntity target, long now) {
        // Live Wire: whatever the archer hits, a spark follows it
        if (sparking || !data.hasBuff(BUFF_LIVE_WIRE, now) || now - LAST_SPARK.getOrDefault(p.getUUID(), 0L) < 5) return;
        LAST_SPARK.put(p.getUUID(), now);
        int l = Math.max(1, data.level(LIVE_WIRE.path()));
        later(p, 1, () -> {
            if (!target.isAlive()) return;
            sparking = true;
            try {
                zap(p, LIVE_WIRE, target, 3 * m(l, tier(p)));
            } finally {
                sparking = false;
            }
            fx(p, Kind.BOLT, LIVE_WIRE.color(), mid(target), 0.15f, 0);
        });
    }

    /**
     * A ranger subclass player is struck. Returns the damage to take, below zero if nothing lands. Public for the
     * game tests: test players cannot be hurt, so they ask directly.
     */
    public static float whenStruck(ServerPlayer p, SkillData data, LivingEntity attacker, DamageSource source, float amount, long now) {
        // the sky looks after its own: a Heavenpiercer never falls hard
        if (source.is(DamageTypeTags.IS_FALL) && (data.level(SKYS_GRACE.path()) > 0 || data.hasBuff(BUFF_SKYBORNE, now))) return -1;
        int ground = data.level(KILLING_GROUND.path());
        if (ground > 0 && attacker != null && (caught(attacker, now) || attacker.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))) {
            amount *= 1 - killingGround(ground) / 100f;
        }
        return amount;
    }

    /** Once a second. Public for the game tests, whose players do not tick. */
    public static void tickSecond(ServerPlayer p, SkillData data) {
        long now = now(p);
        int patient = data.level(PATIENT_HUNTER.path());
        if (patient > 0) {
            double[] focus = FOCUS.computeIfAbsent(p.getUUID(), k -> new double[]{-1, p.getX(), p.getY(), p.getZ()});
            boolean still = p.position().distanceToSqr(focus[1], focus[2], focus[3]) < 0.09;
            focus[0] = still ? Math.min(5, focus[0] + 1) : 0;
            focus[1] = p.getX();
            focus[2] = p.getY();
            focus[3] = p.getZ();
        } else if (!FOCUS.isEmpty()) {
            FOCUS.remove(p.getUUID());
        }
        int heart = data.level(WILD_HEART.path());
        if (heart > 0) {
            if (p.getHealth() < p.getMaxHealth()) p.heal(heartRegen(heart));
            for (TamableAnimal beast : beasts(p, 24)) if (beast.getHealth() < beast.getMaxHealth()) beast.heal(heartBeasts(heart));
        }
        // the wind lets go when its time is up, whatever became of the skill that called it
        AttributeInstance gravity = p.getAttribute(Attributes.GRAVITY);
        if (gravity != null && gravity.getModifier(HOVER) != null && !data.hasBuff(BUFF_SKYBORNE, now)) gravity.removeModifier(HOVER);
    }

    static void onKill(ServerPlayer p, SkillData data, LivingEntity dead) {
        long now = now(p);
        BITTEN.remove(dead);
        int clean = data.level(CLEAN_KILL.path());
        if (clean > 0) {
            SystemAPI.restoreMana(p, cleanMana(clean));
            if (data.cooldownLeft(SubclassSkills.DEADEYE_SHOT.path(), now) > 0) {
                data.startCooldown(SubclassSkills.DEADEYE_SHOT.path(), now, 0);
                SkillAPI.sync(p);
            }
        }
        long[] held = HELD.remove(dead);
        int scavenger = data.level(SCAVENGER.path());
        if (scavenger > 0 && held != null && held[0] > now && held[1] == p.getId()) {
            SystemAPI.restoreMana(p, scavenge(scavenger));
            if (data.cooldownLeft(JAW_TRAP.path(), now) > 0) {
                data.startCooldown(JAW_TRAP.path(), now, 0);
                SkillAPI.sync(p);
            }
        }
        long[] charged = CHARGED.remove(dead);
        int chain = data.level(CHAIN_REACTION.path());
        if (chain > 0 && charged != null && charged[0] > now && charged[1] == p.getId()) {
            // the charge it carried leaps to whoever stands nearest. Next tick, so that it cannot feed itself.
            Vec3 at = dead.position().add(0, dead.getBbHeight() * 0.55, 0);
            later(p, 1, () -> {
                LivingEntity next = foes(p, at, 7).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(at))).orElse(null);
                if (next == null) return;
                zap(p, CHAIN_REACTION, next, 5 * m(chain, tier(p)));
                fx(next, Kind.BOLT, CHAIN_REACTION.color(), at, 0.25f, 0);
                sound(next, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 1.8f);
            });
        }
        int wind = data.level(KILLING_WIND.path());
        if (wind > 0) {
            long[] kills = WIND.computeIfAbsent(p.getUUID(), k -> new long[2]);
            kills[0] = kills[1] > now ? Math.min(5, kills[0] + 1) : 1;
            kills[1] = now + 200;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0, false, false, true));
        }
    }

    /** Touch the class so its skills are registered. */
    static void init() {}

    private RangerPath() {}
}
