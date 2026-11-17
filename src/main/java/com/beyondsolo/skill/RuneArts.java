package com.beyondsolo.skill;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.enemy.MonsterKind;
import com.beyondsolo.gate.BossKind;
import com.beyondsolo.gate.DungeonBoss;
import com.beyondsolo.network.RuneFxPayload.Kind;
import com.beyondsolo.network.SkillFxPayload;
import com.beyondsolo.rune.RuneFx;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.Stat;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static com.beyondsolo.skill.SkillActions.aim;
import static com.beyondsolo.skill.SkillActions.aimPoint;
import static com.beyondsolo.skill.SkillActions.canHit;
import static com.beyondsolo.skill.SkillActions.f;
import static com.beyondsolo.skill.SubclassKit.*;

/**
 * The rune arts: skills that only rune stones teach. Each is the essence of a gate creature or boss, crystallised
 * when it died (the Gloomfang's howl, the Brute's fall, the Wyrm's roar...), plus a few that only the deepest gates
 * give up. Any class can learn them; they never unlock by level, no tome rolls them and a class change keeps them.
 *
 * <p>Numbers sit next to the skill they belong to, and both the description and the effect read them. Looks are
 * drawn by {@code client.rune.RuneWorldFx} from {@link RuneFx} events: no cast circles except for the one spell
 * (Sigil Cascade), light particles, a shape of its own for each.</p>
 */
public final class RuneArts {
    /** Ashen Bulwark's shield is up. */
    public static final String BUFF_BULWARK = "ashen_bulwark";

    private static final PlayerRank E = PlayerRank.E, D = PlayerRank.D, C = PlayerRank.C, B = PlayerRank.B, A = PlayerRank.A, S = PlayerRank.S;
    private static final Map<MonsterKind, Skill> BY_MONSTER = new EnumMap<>(MonsterKind.class);
    private static final Map<BossKind, Skill> BY_BOSS = new EnumMap<>(BossKind.class);
    private static final List<Skill> DEEP = new ArrayList<>();
    /** Crystal Carapace: player -> game time of the last shard, so a swarm can't make it fire every tick. */
    private static final Map<Player, Long> SHARDED = new WeakHashMap<>();

    // ================================================================ numbers

    public static float howlSeconds(int l) { return 6 + 0.5f * l; }
    public static int carapaceChance(int l) { return 10 + 2 * l; }
    public static float lanceMana(int l) { return 3 + l / 2f; }
    public static float bulwarkSeconds(int l) { return 4 + 0.3f * l; }
    public static int bulwarkBlock(int l) { return 50 + l; }
    public static int bulwarkReturn(int l) { return 20 + 2 * l; }
    public static float snareSeconds(int l) { return 4 + 0.3f * l; }
    public static float dreadSeconds(int l) { return 4 + 0.4f * l; }
    public static int flurryCuts(int l) { return 6 + l / 3; }
    public static int sigils(int l) { return 3 + l / 3; }
    public static float resolveBlock(int l) { return 15 + 2.5f * l; }
    /** Below this share of their health, Iron Resolve holds. */
    public static final float RESOLVE_BELOW = 0.3f;

    // ================================================================ the creatures' runes

    public static final Skill HUNTERS_HOWL = active("hunters_howl", D, SkillElement.AIR, 0x9FD8E8, 14, 600, 360,
            RuneArts::huntersHowl, (l, t) -> a(f(howlSeconds(l))));
    public static final Skill SKITTERING_BROOD = active("skittering_brood", E, SkillElement.NATURE, 0x8CCB5E, 10, 160, 90,
            RuneArts::skitteringBrood, (l, t) -> a(f(4.5f * m(l, t)), f(0.8f * m(l, t))));
    public static final Skill CRYSTAL_CARAPACE = passive("crystal_carapace", D, SkillElement.EARTH, 0x7FD7C8,
            (l, t) -> a(f(0.6f * l), f(carapaceChance(l)), f(3 * m(l, t))));
    public static final Skill HOLLOW_LANCE = active("hollow_lance", C, SkillElement.SHADOW, 0x9B6BE0, 18, 220, 130,
            RuneArts::hollowLance, (l, t) -> a(f(8 * m(l, t)), f(lanceMana(l))));
    public static final Skill ASHEN_BULWARK = active("ashen_bulwark", C, SkillElement.EARTH, 0xD4A47A, 16, 400, 240,
            RuneArts::ashenBulwark, (l, t) -> a(f(bulwarkSeconds(l)), f(bulwarkBlock(l)), f(bulwarkReturn(l))));
    public static final Skill CINDER_WISP = active("cinder_wisp", D, SkillElement.FIRE, 0xFF8A3D, 12, 140, 80,
            RuneArts::cinderWisp, (l, t) -> a(f(6 * m(l, t))));

    // ================================================================ the bosses' runes

    public static final Skill TITANS_FALL = active("titans_fall", B, SkillElement.EARTH, 0xD8B070, 28, 400, 260,
            RuneArts::titansFall, (l, t) -> a(f(11 * m(l, t))));
    public static final Skill WIDOWS_SNARE = active("widows_snare", C, SkillElement.NATURE, 0xCFF0B4, 18, 360, 220,
            RuneArts::widowsSnare, (l, t) -> a(f(snareSeconds(l)), f(1.5f * m(l, t))));
    public static final Skill DREAD_PRESENCE = active("dread_presence", A, SkillElement.SHADOW, 0xC0283E, 24, 860, 560,
            RuneArts::dreadPresence, (l, t) -> a(f(dreadSeconds(l))));
    public static final Skill PHANTOM_FLURRY = active("phantom_flurry", B, SkillElement.PHYSICAL, 0xD8E4FF, 22, 300, 180,
            RuneArts::phantomFlurry, (l, t) -> a(f(flurryCuts(l)), f(1.7f * m(l, t))));
    public static final Skill SIGIL_CASCADE = active("sigil_cascade", A, SkillElement.ARCANE, 0x8FA0FF, 26, 360, 220,
            RuneArts::sigilCascade, (l, t) -> a(f(sigils(l)), f(10 * m(l, t))));
    public static final Skill WYRMFIRE_ROAR = active("wyrmfire_roar", S, SkillElement.FIRE, 0xFF5A28, 40, 1200, 800,
            RuneArts::wyrmfireRoar, (l, t) -> a(f(16 * m(l, t))));

    // ================================================================ the deep runes (high gates only)

    public static final Skill QUICKENED_PULSE = passive("quickened_pulse", B, SkillElement.LIGHTNING, 0xB8E2FF,
            (l, t) -> a(f(1.5f * l), f(1.5f * l)));
    public static final Skill IRON_RESOLVE = passive("iron_resolve", A, SkillElement.EARTH, 0xD9BE8A,
            (l, t) -> a(f(resolveBlock(l))));

    static {
        BY_MONSTER.put(MonsterKind.GLOOMFANG, HUNTERS_HOWL);
        BY_MONSTER.put(MonsterKind.BROODLING, SKITTERING_BROOD);
        BY_MONSTER.put(MonsterKind.SHARDBACK, CRYSTAL_CARAPACE);
        BY_MONSTER.put(MonsterKind.HOLLOW_ACOLYTE, HOLLOW_LANCE);
        BY_MONSTER.put(MonsterKind.ASHEN_SENTINEL, ASHEN_BULWARK);
        BY_MONSTER.put(MonsterKind.EMBERWISP, CINDER_WISP);
        BY_BOSS.put(BossKind.BRUTE, TITANS_FALL);
        BY_BOSS.put(BossKind.BROODMOTHER, WIDOWS_SNARE);
        BY_BOSS.put(BossKind.ALPHA, DREAD_PRESENCE);
        BY_BOSS.put(BossKind.KNIGHT, PHANTOM_FLURRY);
        BY_BOSS.put(BossKind.MAGUS, SIGIL_CASCADE);
        BY_BOSS.put(BossKind.WYRM, WYRMFIRE_ROAR);
        DEEP.add(QUICKENED_PULSE);
        DEEP.add(IRON_RESOLVE);

        bonus(CRYSTAL_CARAPACE, Attributes.ARMOR, 0.6f, ADD, "armor");
        bonus(CRYSTAL_CARAPACE, Attributes.KNOCKBACK_RESISTANCE, 0.02f, ADD, "knockback");
        bonus(QUICKENED_PULSE, Attributes.MOVEMENT_SPEED, 0.015f, MUL, "speed");
        bonus(QUICKENED_PULSE, Attributes.ATTACK_SPEED, 0.015f, MUL, "attack_speed");
        // a spell: the only rune art that draws a circle; the roar is strong but no spell
        SIGIL_CASCADE.circle(true);
        WYRMFIRE_ROAR.circle(false);
    }

    /** The rune a gate creature of this kind can leave. */
    public static @Nullable Skill of(MonsterKind kind) {
        // the later creatures carry the rune of the original they are kin to
        return BY_MONSTER.get(kind.kin());
    }

    /** The rune a boss of this kind can leave. */
    public static @Nullable Skill of(BossKind kind) {
        return BY_BOSS.get(kind);
    }

    /** Rune arts no creature carries: they only turn up in the caches of high gates. */
    public static List<Skill> deep() {
        return List.copyOf(DEEP);
    }

    /** Touch the class so every rune art is registered (called from {@link Skills#init}). */
    static void init() {}

    // ================================================================ builders

    private static Skill active(String path, PlayerRank grade, SkillElement element, int color, float mana, int cooldown, int cooldownAtMax,
                                Skill.Action action, Skill.Args args) {
        Skill s = new Skill(path, Skill.Kind.ACTIVE, null, grade, -1, false, color, mana, cooldown, cooldownAtMax, action, args);
        return Skills.add(s.element(element).rune(true));
    }

    private static Skill passive(String path, PlayerRank grade, SkillElement element, int color, Skill.Args args) {
        Skill s = new Skill(path, Skill.Kind.PASSIVE, null, grade, -1, false, color, 0, 0, 0, null, args);
        return Skills.add(s.element(element).rune(true));
    }

    // ================================================================ actions

    /** A howl that sharpens the pack and shakes the prey. */
    static boolean huntersHowl(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(howlSeconds(l) * 20);
        for (Player ally : allies(p, 10)) {
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, false, true));
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0, false, false, true));
        }
        for (LivingEntity e : foes(p, p.position(), 7)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
        }
        sound(p, SoundEvents.WOLF_HOWL, 1.3f, 0.8f);
        sound(p, SoundEvents.RAVAGER_ROAR, 0.35f, 1.6f);
        RuneFx.play(p, Kind.HOWL, s.color(), p.position(), facing(p), 10, 0, 0);
        return true;
    }

    /** Spectral spiderlings pour out over the ground and bite whatever they reach. */
    static boolean skitteringBrood(ServerPlayer p, Skill s, int l) {
        Vec3 dir = facing(p);
        List<LivingEntity> targets = cone(p, 7, 0.45);
        float dmg = 4.5f * m(l, tier(p)) + stat(p, Stat.AGI, 0.12f);
        float bite = 0.8f * m(l, tier(p));
        RuneFx.play(p, Kind.SWARM, s.color(), p.position().add(dir.scale(7)), dir, 3.5f, 0, targets.size());
        sound(p, SoundEvents.SPIDER_AMBIENT, 1f, 1.8f);
        sound(p, SoundEvents.SILVERFISH_AMBIENT, 1f, 0.7f);
        for (LivingEntity e : targets) {
            // the brood reaches the far ones a moment later
            int delay = (int) Math.min(8, e.distanceTo(p) * 1.1f);
            later(p, delay, () -> {
                if (!e.isAlive()) return;
                hit(p, s, e, dmg);
                Statuses.apply(e, DamageElement.POISON, 12 + 2 * l, p);
                for (int i = 1; i <= 3; i++) {
                    later(p, i * 20, () -> {
                        if (e.isAlive()) magic(p, s, e, bite);
                    });
                }
            });
        }
        return true;
    }

    /** A lance of hollow light through everything in a line; each foe it passes gives some mana back. */
    static boolean hollowLance(ServerPlayer p, Skill s, int l) {
        Vec3 from = p.getEyePosition().add(0, -0.3, 0);
        Vec3 to = aimPoint(p, 16);
        float dmg = 8 * m(l, tier(p)) + stat(p, Stat.INT, 0.25f);
        List<LivingEntity> struck = along(p, from, to, 0.8);
        RuneFx.play(p, Kind.LANCE, s.color(), to, to.subtract(from).normalize(), (float) from.distanceTo(to), 0, struck.size());
        sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 0.7f);
        sound(p, SoundEvents.TRIDENT_THROW.value(), 1f, 0.5f);
        for (LivingEntity e : struck) {
            magic(p, s, e, dmg);
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false));
            SystemAPI.restoreMana(p, lanceMana(l));
        }
        return true;
    }

    /** A tower shield of ash: blows from the front are blunted and partly thrown back (see {@link Hooks}). */
    static boolean ashenBulwark(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(bulwarkSeconds(l) * 20);
        SkillAPI.setBuff(p, BUFF_BULWARK, ticks);
        RuneFx.play(p, Kind.WALL, s.color(), p.position(), facing(p), 1.7f, ticks, 0);
        sound(p, SoundEvents.ANVIL_PLACE, 0.45f, 0.55f);
        sound(p, SoundEvents.BASALT_PLACE, 1f, 0.6f);
        return true;
    }

    /** A burning wisp thrown in an arc; it bursts where it lands. */
    static boolean cinderWisp(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 18);
        int flight = (int) Mth.clamp(6 + at.distanceTo(p.position()) / 2.2, 6, 14);
        float dmg = 6 * m(l, tier(p)) + stat(p, Stat.INT, 0.15f);
        RuneFx.play(p, Kind.WISP, s.color(), at, Vec3.ZERO, 3.2f, flight, 0);
        sound(p, SoundEvents.BLAZE_SHOOT, 0.7f, 1.4f);
        later(p, flight, () -> {
            sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.55f, 1.5f);
            sound(level, at, SoundEvents.FIRECHARGE_USE, 1f, 0.8f);
            for (LivingEntity e : foes(p, at, 3.2)) {
                hit(p, s, e, dmg);
                Statuses.apply(e, DamageElement.FIRE, 18 + 3 * l, p);
                e.igniteForSeconds(3);
            }
        });
        return true;
    }

    /** Leap at the ground and come down like the Brute: a heavy landing and ground that keeps quaking. */
    static boolean titansFall(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 target = aimGround(p, 12);
        float dmg = 11 * m(l, tier(p)) + stat(p, Stat.STR, 0.3f);
        sound(p, SoundEvents.RAVAGER_STEP, 1f, 0.6f);
        sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 0.6f, 0.6f);
        leap(p, target, 0.95, land -> {
            RuneFx.at(level, Kind.IMPACT, s.color(), land, 4.5f, 0);
            SubclassActions.fxAt(level, SkillFxPayload.Kind.QUAKE, s.color(), land, 4.5f, 40);
            sound(level, land, SoundEvents.GENERIC_EXPLODE.value(), 1f, 0.55f);
            sound(level, land, SoundEvents.ANVIL_LAND, 0.6f, 0.45f);
            for (LivingEntity e : foes(p, land, 4.5)) {
                hit(p, s, e, dmg);
                launch(e, 0.75);
            }
            repeat(p, 60, 10, () -> {
                for (LivingEntity e : foes(p, land, 4.5)) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 1, false, false));
            });
        });
        return true;
    }

    /** A web cast over the ground: whatever is caught in it is held fast, poisoned and bitten. */
    static boolean widowsSnare(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = aimGround(p, 16);
        int ticks = Math.round(snareSeconds(l) * 20);
        float bite = 1.5f * m(l, tier(p)) + stat(p, Stat.INT, 0.05f);
        RuneFx.at(level, Kind.WEB, s.color(), at, 4.5f, ticks);
        sound(level, at, SoundEvents.SPIDER_AMBIENT, 1f, 0.6f);
        sound(level, at, SoundEvents.SLIME_BLOCK_PLACE, 1f, 0.7f);
        for (int t = 0; t < ticks; t += 10) {
            final boolean bites = t > 0 && t % 20 == 0;
            later(p, t, () -> {
                for (LivingEntity e : foes(p, at, 4.5)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 14, 5, false, false));
                    e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 1, 0.2));
                    if (bites) {
                        magic(p, s, e, bite);
                        Statuses.apply(e, DamageElement.POISON, 6 + l, p);
                    }
                }
            });
        }
        return true;
    }

    /** Killing intent: everything near is gripped with dread; the lesser ones freeze in terror. */
    static boolean dreadPresence(ServerPlayer p, Skill s, int l) {
        int ticks = Math.round(dreadSeconds(l) * 20);
        float lesser = 40 + 20 * tier(p);
        RuneFx.play(p, Kind.DREAD, s.color(), p.position(), 12, 0);
        sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, 0.5f, 0.55f);
        sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.6f);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0, false, false, true));
        for (LivingEntity e : foes(p, p.position(), 12)) {
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1, false, false));
            if (!(e instanceof DungeonBoss) && e.getMaxHealth() <= lesser) {
                SubclassActions.stun(e, 30);
                push(e, p.position(), 0.45, 0.12);
                RuneFx.play(e, Kind.TERROR, s.color(), e.position(), e.getBbHeight(), 30);
            }
        }
        return true;
    }

    /** Blink to the creature in your sights and cut it many times in a heartbeat. */
    static boolean phantomFlurry(ServerPlayer p, Skill s, int l) {
        LivingEntity target = aim(p, 12, x -> canHit(p, x));
        if (target == null) return false;
        Vec3 from = p.position();
        blinkNear(p, target, 1.7);
        int cuts = flurryCuts(l);
        float dmg = 1.7f * m(l, tier(p)) + stat(p, Stat.STR, 0.06f);
        RuneFx.play(target, Kind.FLURRY, s.color(), from, Vec3.ZERO, target.getBbHeight(), 2 + cuts * 2, cuts);
        sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.5f, 1.7f);
        for (int i = 0; i < cuts; i++) {
            final int k = i;
            later(p, 2 + i * 2, () -> {
                if (!target.isAlive()) return;
                hit(p, s, target, dmg);
                sound(target, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.3f + 0.07f * k);
                if (k == cuts - 1) {
                    push(target, p.position(), 1.1, 0.35);
                    sound(target, SoundEvents.PLAYER_ATTACK_CRIT, 1f, 0.75f);
                }
            });
        }
        return true;
    }

    /**
     * Like {@code SubclassKit.blinkTo}, but stopping {@code gap} blocks short of the creature's side: the flurry's cuts
     * are phantoms, and the cutter's view stays clear of the creature. Falls back to the usual spot when blocked.
     */
    private static void blinkNear(ServerPlayer p, LivingEntity target, double gap) {
        Vec3 from = p.position();
        Vec3 dest = target.position().add(flat(from.subtract(target.position()), p).scale(target.getBbWidth() / 2 + gap));
        if (!p.level().noCollision(p, p.getBoundingBox().move(dest.subtract(from)))) {
            blinkTo(p, target, false);
            return;
        }
        SubclassActions.teleport(p, dest, target);
    }

    /** Sigils burn under the nearest foes and burst a moment later. */
    static boolean sigilCascade(ServerPlayer p, Skill s, int l) {
        List<LivingEntity> near = new ArrayList<>(foes(p, p.position(), 14));
        near.removeIf(e -> !p.hasLineOfSight(e));
        if (near.isEmpty()) return false;
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        ServerLevel level = p.serverLevel();
        int n = Math.min(sigils(l), near.size());
        float dmg = 10 * m(l, tier(p)) + stat(p, Stat.INT, 0.3f);
        sound(p, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1f, 1.2f);
        for (int i = 0; i < n; i++) {
            LivingEntity marked = near.get(i);
            int delay = 20 + i * 3;
            RuneFx.play(marked, Kind.SIGIL, s.color(), marked.position(), Vec3.ZERO, 2f, delay, i);
            later(p, delay, () -> {
                Vec3 at = marked.position();
                sound(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 1.2f, 0.6f);
                sound(level, at, SoundEvents.FIREWORK_ROCKET_BLAST, 0.8f, 0.7f);
                for (LivingEntity e : foes(p, at, 2)) magic(p, s, e, dmg);
            });
        }
        return true;
    }

    /** A roar of wyrmfire: a cone of flame, and terror on everything that hears it. */
    static boolean wyrmfireRoar(ServerPlayer p, Skill s, int l) {
        float dmg = 16 * m(l, tier(p)) + Math.max(stat(p, Stat.STR, 0.3f), stat(p, Stat.INT, 0.3f));
        RuneFx.play(p, Kind.BREATH, s.color(), p.getEyePosition(), p.getLookAngle(), 10, 16, 0);
        sound(p, SoundEvents.ENDER_DRAGON_GROWL, 1f, 1.25f);
        sound(p, SoundEvents.BLAZE_SHOOT, 1f, 0.5f);
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, false, false, true));
        p.clearFire();
        for (LivingEntity e : foes(p, p.position(), 16)) {
            boolean boss = e instanceof DungeonBoss;
            SubclassActions.stun(e, boss ? 10 : 30);
            if (!boss) RuneFx.play(e, Kind.TERROR, s.color(), e.position(), e.getBbHeight(), 30);
        }
        // the flames roll out over a few ticks
        Set<Integer> burnt = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            final double reach = 4 + i * 2;
            later(p, 2 + i * 3, () -> {
                for (LivingEntity e : cone(p, reach, 0.55)) {
                    if (!burnt.add(e.getId())) continue;
                    hit(p, s, e, dmg);
                    Statuses.apply(e, DamageElement.FIRE, 40 + 4 * l, p);
                    e.igniteForSeconds(5);
                }
            });
        }
        return true;
    }

    // ================================================================ passives and buffs

    private static boolean inFront(Player p, Vec3 from) {
        Vec3 to = from.subtract(p.position()).multiply(1, 0, 1);
        return to.lengthSqr() < 1.0e-3 || to.normalize().dot(flat(p.getLookAngle(), p)) > 0.15;
    }

    /** Ashen Bulwark, Iron Resolve and Crystal Carapace, on blows the player takes. */
    @EventBusSubscriber(modid = BeyondSolo.MOD_ID)
    public static final class Hooks {
        @SubscribeEvent(priority = EventPriority.LOW)
        static void onIncoming(LivingIncomingDamageEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer p) || !SystemAPI.isAwakened(p)) return;
            SkillData data = SkillAPI.get(p);
            long now = p.level().getGameTime();
            float amount = event.getAmount();
            Entity attacker = event.getSource().getEntity();
            Entity direct = event.getSource().getDirectEntity();

            // Ashen Bulwark: blows from the front are blunted and partly thrown back
            if (data.hasBuff(BUFF_BULWARK, now) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                Vec3 from = event.getSource().getSourcePosition();
                if (from != null && inFront(p, from)) {
                    int l = Math.max(1, data.level(ASHEN_BULWARK.path()));
                    float original = amount;
                    amount *= 1 - bulwarkBlock(l) / 100f;
                    if (attacker instanceof LivingEntity foe && foe != p && direct == attacker && !event.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
                        foe.hurt(p.damageSources().thorns(p), original * bulwarkReturn(l) / 100f);
                    }
                    RuneFx.play(p, Kind.WALL, ASHEN_BULWARK.color(), p.position(), flat(p.getLookAngle(), p), 1.7f, 0, 0);
                    sound(p, SoundEvents.SHIELD_BLOCK, 0.9f, 0.6f);
                }
            }

            // Iron Resolve: holds when the end is near
            int resolve = data.level(IRON_RESOLVE.path());
            if (resolve > 0 && p.getHealth() < p.getMaxHealth() * RESOLVE_BELOW) amount *= 1 - resolveBlock(resolve) / 100f;

            // Crystal Carapace: a shard back at whoever struck in melee
            int carapace = data.level(CRYSTAL_CARAPACE.path());
            if (carapace > 0 && attacker instanceof LivingEntity foe && direct == attacker && foe != p && foe.isAlive() && canHit(p, foe)) {
                long last = SHARDED.getOrDefault(p, -100L);
                if (now - last >= 10 && p.getRandom().nextFloat() < carapaceChance(carapace) / 100f) {
                    SHARDED.put(p, now);
                    SkillActions.hit(p, CRYSTAL_CARAPACE, foe, 3 * m(carapace, tier(p)), false);
                    RuneFx.play(p, Kind.SHARDS, CRYSTAL_CARAPACE.color(), foe.position().add(0, foe.getBbHeight() * 0.5, 0), 1, 0);
                    sound(p, SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 1.2f);
                }
            }
            if (amount != event.getAmount()) event.setAmount(amount);
        }

        /** Nothing knocks back a bulwark. */
        @SubscribeEvent
        static void onKnockback(LivingKnockBackEvent event) {
            if (event.getEntity() instanceof ServerPlayer p && SkillAPI.hasBuff(p, BUFF_BULWARK)) event.setCanceled(true);
        }
    }

    private RuneArts() {}
}
