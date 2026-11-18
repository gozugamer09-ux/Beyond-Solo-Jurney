package com.beyondsolo.skill;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.monarch.Dominion;
import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.monarch.MonarchConfig;
import com.beyondsolo.monarch.MonarchDomain;
import com.beyondsolo.network.SkillFxPayload.Kind;
import com.beyondsolo.quest.situation.Foes;
import com.beyondsolo.shadow.ShadowService;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.worldboss.WorldBossFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.beyondsolo.skill.SkillActions.f;
import static com.beyondsolo.skill.SkillActions.mult;
import static com.beyondsolo.skill.SubclassKit.a;

/**
 * A monarch's powers ({@link MonarchAPI}): the three every crown gives, and each crown's authority, the one power no
 * other monarch has. They come only with a crown (never by level, tome or rune stone), only a monarch can use them, and
 * they sit on the crown keys, not the skill bar:
 * <ol>
 *   <li><b>Domain</b>: the monarch's own rule laid down round them. Inside it they and their allies hit harder and take
 *   less, foes are worn down by the crown's element, and the domains of the monarchs from beyond the gates are held
 *   back ({@link MonarchDomain}).</li>
 *   <li><b>Dread</b>: the monarch's killing intent let loose. Everything round them freezes in terror, weakened, and
 *   takes more from every blow for a while; bosses only flinch.</li>
 *   <li><b>Crown form</b>: their true form for a while: their blows are far heavier and carry their crown's element,
 *   they take less and move faster. Never untouchable.</li>
 *   <li><b>Authority</b>: Eclipse of Kings (Night), The Long Winter (Winter), Ashen Heaven (Cinders), Thousand
 *   Thunders (Thunder).</li>
 * </ol>
 * Numbers are first guesses for the final balancing pass; every hit goes through the hunter's power like any skill's.
 */
public final class MonarchPowers {
    // ================================================================ numbers

    public static int domainSeconds(int l) { return 14 + l; }
    /** How much harder the monarch and their allies hit inside the domain (percent). */
    public static float domainMight(int l) { return 15 + l; }
    /** How much less the monarch and their allies take inside the domain (percent). */
    public static final float DOMAIN_GUARD = 10;
    /** What the domain does to each foe inside it every second. */
    public static float domainPulse(int l, int t) { return 2.5f * mult(l, t); }

    public static float dreadRadius(int l) { return 12 + l / 2f; }
    public static float dreadSeconds(int l) { return 2.5f + 0.15f * l; }
    /** How much more the dreaded take from every blow (percent; half for bosses). */
    public static float dreadBonus(int l) { return 20 + l; }
    public static final int DREAD_TICKS = 160;

    public static int crownSeconds(int l) { return 18 + l; }
    /** How much harder a monarch in crown form hits (percent). */
    public static float crownMight(int l) { return 25 + 2 * l; }
    /** How much less a monarch in crown form takes (percent). */
    public static float crownGuard(int l) { return 15 + l; }

    public static final float ECLIPSE_RADIUS = 18;
    public static float eclipseHit(int l, int t) { return 10 * mult(l, t); }
    public static float eclipseBind(int l) { return 2 + 0.1f * l; }
    /** How long the army fights harder after an eclipse, and by how much (percent). */
    public static final int ECLIPSE_TICKS = 300;
    public static final float ECLIPSE_MIGHT = 40;

    public static final float WINTER_RADIUS = 16;
    public static final int WINTER_SECONDS = 6;
    public static float winterHit(int l, int t) { return 6 * mult(l, t); }
    public static float winterShatter(int l, int t) { return 14 * mult(l, t); }

    public static final float HEAVEN_RADIUS = 9;
    public static final int HEAVEN_FALL = 30;
    public static final int HEAVEN_BURN_SECONDS = 5;
    public static float heavenHit(int l, int t) { return 55 * mult(l, t); }
    public static float heavenBurn(int l, int t) { return 3 * mult(l, t); }

    public static final float THUNDER_RANGE = 22;
    public static int thunderBolts(int l) { return 8 + l / 2; }
    public static float thunderHit(int l, int t) { return 14 * mult(l, t); }

    /** The domain's radius (the setting; its default before the settings are read, as on a fresh client). */
    public static int radius() {
        try {
            return MonarchConfig.DOMAIN_RADIUS.get();
        } catch (IllegalStateException e) {
            return 16;
        }
    }

    // ================================================================ the powers

    public static final Skill DOMAIN = power("monarch_domain", 0xB57BFF, SkillElement.ARCANE, 60, 1800, 1200, MonarchPowers::domain,
            (l, t) -> a(f(domainSeconds(l)), f(radius()), f(domainMight(l)), f(DOMAIN_GUARD), f(domainPulse(l, t))));
    public static final Skill DREAD = power("monarch_dread", 0xE0304A, SkillElement.SHADOW, 40, 900, 600, MonarchPowers::dread,
            (l, t) -> a(f(dreadRadius(l)), f(dreadSeconds(l)), f(dreadBonus(l)), f(DREAD_TICKS / 20f))).circle(false);
    public static final Skill CROWN = power("monarch_crown", 0xFFD06A, SkillElement.ARCANE, 80, 3600, 2400, MonarchPowers::crown,
            (l, t) -> a(f(crownSeconds(l)), f(crownMight(l)), f(crownGuard(l))));

    public static final Skill ECLIPSE = power(Dominion.NIGHT.authority(), Dominion.NIGHT.color(), Dominion.NIGHT.element(), 100, 2400, 1600,
            MonarchPowers::eclipse, (l, t) -> a(f(ECLIPSE_RADIUS), f(eclipseBind(l)), f(eclipseHit(l, t)), f(ECLIPSE_MIGHT), f(ECLIPSE_TICKS / 20f)));
    public static final Skill LONG_WINTER = power(Dominion.WINTER.authority(), Dominion.WINTER.color(), Dominion.WINTER.element(), 100, 2400, 1600,
            MonarchPowers::longWinter, (l, t) -> a(f(WINTER_RADIUS), f(WINTER_SECONDS), f(winterHit(l, t)), f(winterShatter(l, t))));
    public static final Skill ASHEN_HEAVEN = power(Dominion.CINDERS.authority(), Dominion.CINDERS.color(), Dominion.CINDERS.element(), 100, 2400, 1600,
            MonarchPowers::ashenHeaven, (l, t) -> a(f(heavenHit(l, t)), f(HEAVEN_RADIUS), f(HEAVEN_BURN_SECONDS), f(heavenBurn(l, t))));
    public static final Skill THOUSAND_THUNDERS = power(Dominion.THUNDER.authority(), Dominion.THUNDER.color(), Dominion.THUNDER.element(), 100, 2400, 1600,
            MonarchPowers::thousandThunders, (l, t) -> a(f(thunderBolts(l)), f(THUNDER_RANGE), f(thunderHit(l, t))));

    /** A monarch power: S grade, never by level or book ({@code advanced}), anyone's class ({@code owner} null). */
    private static Skill power(String path, int color, SkillElement element, float mana, int cooldown, int cooldownAtMax,
                               Skill.Action action, Skill.Args args) {
        Skill s = new Skill(path, Skill.Kind.ACTIVE, null, PlayerRank.S, -1, true, color, mana, cooldown, cooldownAtMax, action, args);
        return Skills.add(s.element(element).monarch(true));
    }

    /** Touch the class so every power is registered (from {@link Skills#init}). */
    static void init() {}

    // ================================================================ what a crown gives

    /** The power on a crown key: 0 the domain, 1 dread, 2 the crown form, 3 the crown's authority. */
    public static @Nullable Skill onKey(@Nullable Dominion d, int key) {
        return switch (key) {
            case 0 -> DOMAIN;
            case 1 -> DREAD;
            case 2 -> CROWN;
            case 3 -> d == null ? null : authority(d);
            default -> null;
        };
    }

    public static Skill authority(Dominion d) {
        return switch (d) {
            case NIGHT -> ECLIPSE;
            case WINTER -> LONG_WINTER;
            case CINDERS -> ASHEN_HEAVEN;
            case THUNDER -> THOUSAND_THUNDERS;
        };
    }

    /** The crown's powers: the three every crown gives and its authority (another crown's authority is forgotten). */
    public static void grant(ServerPlayer p, Dominion d) {
        for (Dominion other : Dominion.values()) {
            if (other != d && SkillAPI.knows(p, authority(other))) SkillAPI.forget(p, authority(other));
        }
        for (int key = 0; key < 4; key++) {
            Skill s = onKey(d, key);
            if (s != null && !SkillAPI.knows(p, s)) SkillAPI.learn(p, s, 1, SkillEvent.Source.OTHER);
        }
    }

    /** The crown taken back: every power forgotten. */
    public static void forgetAll(ServerPlayer p) {
        for (Skill s : Skills.monarchPowers()) {
            if (SkillAPI.knows(p, s)) SkillAPI.get(p).forget(s.path());
        }
        SkillPassives.apply(p);
        SkillAPI.sync(p);
    }

    /** A crown key was pressed. Returns true if the power went off. */
    public static boolean castCrown(ServerPlayer p, int key) {
        Dominion d = MonarchAPI.dominion(p);
        if (d == null) return false;
        Skill s = onKey(d, key);
        return s != null && SkillAPI.knows(p, s) && SkillAPI.cast(p, s);
    }

    // ================================================================ the three every crown gives

    static boolean domain(ServerPlayer p, Skill s, int l) {
        Dominion d = MonarchAPI.dominion(p);
        if (d == null) return false;
        int r = radius();
        MonarchDomain.open(p, d, domainSeconds(l) * 20, l);
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        int c = d.color();
        WorldBossFx.quake(level, at, r, c);
        WorldBossFx.ring(level, at, 0.5f, r, 14, c);
        SkillFx.play(p, Kind.NOVA, c, at, r, nova(d));
        SkillFx.play(p, Kind.ROAR, c, at, 7, 0);
        SubclassKit.sound(p, SoundEvents.END_PORTAL_SPAWN, 0.7f, 1.3f);
        SubclassKit.sound(p, SoundEvents.BEACON_ACTIVATE, 1.4f, 0.5f);
        SubclassKit.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.5f);
        return true;
    }

    /** Once a second, from the domain: what it does to one foe inside it. */
    public static void domainPulse(ServerPlayer p, Dominion d, int l, LivingEntity e, int second) {
        int t = SubclassKit.tier(p);
        switch (d) {
            case NIGHT -> {
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0), p);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), p);
                strike(p, DOMAIN, d.element(), e, domainPulse(l, t));
            }
            case WINTER -> {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), p);
                e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 40, e.getTicksFrozen() + 30));
                strike(p, DOMAIN, d.element(), e, domainPulse(l, t));
            }
            case CINDERS -> {
                Statuses.apply(e, DamageElement.FIRE, 12, p);
                if (e.getRemainingFireTicks() < 40) e.igniteForSeconds(2);
                strike(p, DOMAIN, d.element(), e, domainPulse(l, t));
            }
            case THUNDER -> {
                // the sky strikes on its own clock: one foe in three each second, harder
                if ((e.getId() + second) % 3 == 0) {
                    strike(p, DOMAIN, d.element(), e, domainPulse(l, t) * 3);
                    SkillCombat.stun(e, 10);
                    SubclassKit.fxAt(p.serverLevel(), Kind.THUNDER, d.color(), e.position(), 1.6f, 0);
                    SubclassKit.sound(e, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.3f);
                }
            }
        }
    }

    static boolean dread(ServerPlayer p, Skill s, int l) {
        Dominion d = MonarchAPI.dominion(p);
        if (d == null) return false;
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        int c = d.color();
        float radius = dreadRadius(l);
        int stun = Math.round(dreadSeconds(l) * 20);
        long until = level.getGameTime() + DREAD_TICKS;
        for (LivingEntity e : foes(p, at, radius)) {
            boolean boss = Foes.isBoss(e);
            SkillCombat.stun(e, boss ? 20 : stun);
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DREAD_TICKS, boss ? 0 : 1), p);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DREAD_TICKS, 0), p);
            MonarchDomain.dread(e, until, (boss ? 0.5f : 1f) * dreadBonus(l));
            SkillFx.play(e, Kind.MARK, c, e.position(), e.getBbHeight(), Math.max(stun, 40));
            level.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getY() + e.getBbHeight() * 0.7, e.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
        }
        WorldBossFx.wave(level, at, radius, 2.5f, 12, c);
        SkillFx.play(p, Kind.ROAR, c, at, radius, 0);
        MonarchDomain.fx(p, com.beyondsolo.network.MonarchFxPayload.DREAD, c, Math.round(radius), 0);
        SubclassKit.sound(p, SoundEvents.WARDEN_ROAR, 1.6f, 0.6f);
        SubclassKit.sound(p, SoundEvents.ENDER_DRAGON_GROWL, 0.8f, 1.3f);
        return true;
    }

    static boolean crown(ServerPlayer p, Skill s, int l) {
        Dominion d = MonarchAPI.dominion(p);
        if (d == null) return false;
        MonarchDomain.crown(p, d, crownSeconds(l) * 20, l);
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        int c = d.color();
        SkillFx.play(p, Kind.PILLAR, c, at, 2.2f, 0);
        WorldBossFx.burst(level, at.add(0, 1, 0), 4f, c);
        SkillFx.play(p, Kind.NOVA, c, at, 6, nova(d));
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 40, 0.4, 0.9, 0.4, 0.15);
        SubclassKit.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.4f, 0.6f);
        SubclassKit.sound(p, SoundEvents.END_PORTAL_FRAME_FILL, 1.2f, 0.6f);
        SubclassKit.sound(p, SoundEvents.WITHER_AMBIENT, 0.5f, 0.6f);
        return true;
    }

    // ================================================================ the authorities

    /** Night: the sun goes out. Every foe round them is bound in the dark, and the whole army rises, mended and stronger. */
    static boolean eclipse(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        int c = Dominion.NIGHT.color();
        int t = SubclassKit.tier(p);
        int bind = Math.round(eclipseBind(l) * 20);
        authority(p, Dominion.NIGHT, 70);
        for (LivingEntity e : foes(p, at, ECLIPSE_RADIUS)) {
            SkillCombat.stun(e, bind);
            SkillFx.play(e, Kind.BIND, c, e.position(), 1.4f, bind);
        }
        // the dark closes on them three times
        for (int i = 0; i < 3; i++) {
            final int k = i;
            SubclassKit.later(p, 10 + i * 20, () -> {
                for (LivingEntity e : foes(p, at, ECLIPSE_RADIUS)) {
                    strike(p, s, s.element(), e, eclipseHit(l, t));
                    level.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
                }
                SubclassKit.fxAt(level, Kind.NOVA, c, at, ECLIPSE_RADIUS, 4);
                SubclassKit.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 0.5f + 0.1f * k);
            });
        }
        // the army rises
        ShadowService.eclipse(p);
        MonarchDomain.eclipse(p, ECLIPSE_TICKS);
        WorldBossFx.circle(level, at, ECLIPSE_RADIUS, 20, c);
        WorldBossFx.quake(level, at, ECLIPSE_RADIUS, c);
        SubclassKit.fxAt(level, Kind.MIST, c, at, ECLIPSE_RADIUS, 80);
        SubclassKit.fxAt(level, Kind.GEYSER, c, at, 6, 4);
        SubclassKit.sound(p, SoundEvents.WITHER_SPAWN, 0.9f, 0.5f);
        SubclassKit.sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.6f, 0.5f);
        return true;
    }

    /** Winter: the cold of an age. Everything round them freezes solid for a while, then shatters. */
    static boolean longWinter(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        int c = Dominion.WINTER.color();
        int t = SubclassKit.tier(p);
        authority(p, Dominion.WINTER, WINTER_SECONDS * 20 + 30);
        WorldBossFx.circle(level, at, WINTER_RADIUS, 20, c);
        SubclassKit.fxAt(level, Kind.MIST, c, at, WINTER_RADIUS, WINTER_SECONDS * 20 + 30);
        SubclassKit.fxAt(level, Kind.CLOUD, c, at.add(0, 9, 0), WINTER_RADIUS * 0.8f, WINTER_SECONDS * 20 + 20);
        SubclassKit.sound(p, SoundEvents.POWDER_SNOW_STEP, 2f, 0.5f);
        SubclassKit.sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, 0.8f, 0.6f);
        for (int i = 0; i < WINTER_SECONDS; i++) {
            SubclassKit.later(p, 20 + i * 20, () -> {
                for (LivingEntity e : foes(p, at, WINTER_RADIUS)) {
                    strike(p, s, s.element(), e, winterHit(l, t));
                    e.setTicksFrozen(e.getTicksRequiredToFreeze() + 60);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4), p);
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 5, at.z, 160, WINTER_RADIUS * 0.5, 2.5, WINTER_RADIUS * 0.5, 0.03);
                SubclassKit.fxAt(level, Kind.PULSE, c, at, WINTER_RADIUS, 1);
                SubclassKit.sound(level, at, SoundEvents.GLASS_BREAK, 0.4f, 0.5f);
            });
        }
        // the thaw that never comes: everything frozen shatters
        SubclassKit.later(p, 30 + WINTER_SECONDS * 20, () -> {
            for (LivingEntity e : foes(p, at, WINTER_RADIUS)) {
                strike(p, s, s.element(), e, winterShatter(l, t));
                SubclassKit.fxAt(level, Kind.SPLASH, c, e.position().add(0, e.getBbHeight() * 0.5, 0), 2f, 1);
                level.sendParticles(ParticleTypes.ITEM_SNOWBALL, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 16, 0.4, 0.5, 0.4, 0.2);
            }
            WorldBossFx.burst(level, at.add(0, 1, 0), 6f, c);
            WorldBossFx.quake(level, at, WINTER_RADIUS, c);
            SubclassKit.sound(level, at, SoundEvents.GLASS_BREAK, 2f, 0.6f);
            SubclassKit.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.3f);
        });
        return true;
    }

    /** Cinders: a star torn burning out of the sky onto where they look, and the land keeps burning. */
    static boolean ashenHeaven(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 target = SubclassKit.aimGround(p, 32);
        int c = Dominion.CINDERS.color();
        int t = SubclassKit.tier(p);
        authority(p, Dominion.CINDERS, HEAVEN_FALL + 40);
        WorldBossFx.meteor(level, target, 4.5f, HEAVEN_FALL, c);
        WorldBossFx.circle(level, target, HEAVEN_RADIUS, HEAVEN_FALL, c);
        SubclassKit.sound(p, SoundEvents.BLAZE_SHOOT, 1.6f, 0.4f);
        SubclassKit.sound(level, target, SoundEvents.FIRECHARGE_USE, 2f, 0.5f);
        SubclassKit.later(p, HEAVEN_FALL, () -> {
            for (LivingEntity e : foes(p, target, HEAVEN_RADIUS)) {
                float k = 1 - 0.4f * (float) Math.min(1, e.position().distanceTo(target) / HEAVEN_RADIUS);
                strike(p, s, s.element(), e, heavenHit(l, t) * k);
                Statuses.apply(e, DamageElement.FIRE, 60, p);
                if (!Foes.isBoss(e)) SubclassKit.push(e, target, 1.2, 0.7);
            }
            WorldBossFx.burst(level, target.add(0, 1, 0), 7f, c);
            WorldBossFx.quake(level, target, 13f, c);
            SubclassKit.fxAt(level, Kind.NOVA, c, target, HEAVEN_RADIUS + 3, 0);
            SubclassKit.fxAt(level, Kind.GEYSER, c, target, 9, 0);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 1, target.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.LAVA, target.x, target.y + 0.5, target.z, 40, 3, 0.5, 3, 0.1);
            SubclassKit.sound(level, target, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.5f);
            SubclassKit.sound(level, target, SoundEvents.DRAGON_FIREBALL_EXPLODE, 2f, 0.6f);
            // the land keeps burning
            SubclassKit.fxAt(level, Kind.ZONE, c, target, HEAVEN_RADIUS * 0.7f, HEAVEN_BURN_SECONDS * 20);
            SubclassKit.repeat(p, HEAVEN_BURN_SECONDS * 20, 20, () -> {
                for (LivingEntity e : foes(p, target, HEAVEN_RADIUS * 0.7f)) {
                    strike(p, s, s.element(), e, heavenBurn(l, t));
                    Statuses.apply(e, DamageElement.FIRE, 10, p);
                }
                level.sendParticles(ParticleTypes.FLAME, target.x, target.y + 0.3, target.z, 30, HEAVEN_RADIUS * 0.35, 0.2, HEAVEN_RADIUS * 0.35, 0.02);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, target.x, target.y + 0.6, target.z, 8, HEAVEN_RADIUS * 0.3, 0.3, HEAVEN_RADIUS * 0.3, 0.02);
            });
        });
        return true;
    }

    /** Thunder: the sky answers its monarch, one bolt after another on everything near, each leaping on to one more. */
    static boolean thousandThunders(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        List<LivingEntity> foes = new ArrayList<>(foes(p, at, THUNDER_RANGE));
        if (foes.isEmpty()) return false;
        foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        int c = Dominion.THUNDER.color();
        int t = SubclassKit.tier(p);
        int bolts = thunderBolts(l);
        authority(p, Dominion.THUNDER, bolts * 4 + 30);
        SubclassKit.fxAt(level, Kind.CLOUD, c, at.add(0, 12, 0), THUNDER_RANGE * 0.7f, bolts * 4 + 30);
        SubclassKit.fxAt(level, Kind.STATIC, c, at, 3, bolts * 4 + 10);
        SubclassKit.sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.6f, 0.6f);
        for (int i = 0; i < bolts; i++) {
            final int k = i;
            SubclassKit.later(p, 8 + i * 4, () -> {
                List<LivingEntity> alive = new ArrayList<>();
                for (LivingEntity e : foes) if (e.isAlive() && e.level() == level) alive.add(e);
                if (alive.isEmpty()) return;
                LivingEntity e = alive.get(k % alive.size());
                strike(p, s, s.element(), e, thunderHit(l, t));
                SkillCombat.stun(e, 16);
                WorldBossFx.bolt(level, e.position(), 1.5f, c);
                SubclassKit.fxAt(level, Kind.THUNDER, c, e.position(), 2.4f, 0);
                SubclassKit.sound(e, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4f, 0.8f + 0.05f * k);
                // it leaps on to one more
                LivingEntity next = null;
                double best = 36;
                for (LivingEntity o : foes(p, e.position(), 6)) {
                    double dd = o.distanceToSqr(e);
                    if (o != e && dd < best) {
                        best = dd;
                        next = o;
                    }
                }
                if (next != null) {
                    strike(p, s, s.element(), next, thunderHit(l, t) * 0.5f);
                    SubclassKit.fxAt(level, Kind.STATIC, c, next.position().add(0, next.getBbHeight() * 0.5, 0), 1.5f, 10);
                }
            });
        }
        return true;
    }

    // ================================================================ helpers

    /** The foes round a point a monarch's power reaches: never a shadow, the monarch's own or a friend's. */
    static List<LivingEntity> foes(ServerPlayer p, Vec3 at, double radius) {
        List<LivingEntity> out = new ArrayList<>(SubclassKit.foes(p, at, radius));
        out.removeIf(e -> e instanceof com.beyondsolo.shadow.ShadowEntity);
        return out;
    }

    /** A monarch power's hit, in an element of the crown's choosing: the hunter's power, the combo chain, then the element. */
    static void strike(ServerPlayer p, Skill s, SkillElement element, LivingEntity e, float damage) {
        damage *= (float) Balance.power(p);
        damage *= SkillCombat.comboMultiplier(p) * com.beyondsolo.rune.Runes.damageMultiplier(p, s);
        damage = SkillCombat.strike(p, e, element, damage);
        e.invulnerableTime = 0;
        e.hurt(p.damageSources().indirectMagic(p, p), damage);
    }

    /** An authority is used: counted, and named on the screens of everyone near ({@code ticks} = how long it lasts). */
    private static void authority(ServerPlayer p, Dominion d, int ticks) {
        MonarchAPI.usedAuthority(p);
        MonarchDomain.announce(p, d, ticks);
    }

    /** What a crown's novas throw: soul fire, spray, flames, sparks. */
    static int nova(Dominion d) {
        return switch (d) {
            case NIGHT -> 4;
            case WINTER -> 2;
            case CINDERS -> 0;
            case THUNDER -> 1;
        };
    }

    private MonarchPowers() {}
}
