package com.beyondsolo.skill;

import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.light.LightAPI;
import com.beyondsolo.light.LightConfig;
import com.beyondsolo.light.LightConstruct;
import com.beyondsolo.light.LightSanctum;
import com.beyondsolo.light.Mantle;
import com.beyondsolo.network.SkillFxPayload.Kind;
import com.beyondsolo.quest.situation.Foes;
import com.beyondsolo.system.Balance;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.worldboss.WorldBossFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.beyondsolo.skill.SkillActions.f;
import static com.beyondsolo.skill.SkillActions.mult;
import static com.beyondsolo.skill.SubclassKit.a;

/**
 * A ruler of light's powers ({@link LightAPI}): the light's answer to the monarchs' ({@link MonarchPowers}). They come
 * only from the light (never by level, tome or rune stone), only a hunter sworn to it can use them, and they sit on the
 * crown keys, not the skill bar:
 * <ol>
 *   <li><b>Sanctum</b>: a dome of light laid down round the ruler that goes where they go. Inside it they and their
 *   allies hit harder, take less and mend, foes burn, the mantle adds its own law, and the domains of the monarchs from
 *   beyond the gates are held back ({@link LightSanctum}). Comes with the Investiture.</li>
 *   <li><b>Judgement</b>: a sigil over every foe round them and a lance of light down onto each; the judged take more
 *   from every blow for a while. A hunter sworn to the light has it as they wake.</li>
 *   <li><b>Seraph Form</b>: wings of light and a halo for a while; their blows carry the light, they take less, move
 *   faster and fall like a feather. Never untouchable. Learned on the road ({@code light.seraphLevel}).</li>
 *   <li><b>Authority</b>: Blade of Heaven (the Verdict), Choir of Wings (Wings), Hand of Heaven (the Unseen Hand),
 *   Daybreak (Dawn). Comes with the Investiture.</li>
 * </ol>
 * The light's greater shapes (the blade, the hand, the sun, the lances and feathers, the choir) are real models
 * ({@link LightConstruct}); the rest is drawn by the skill effects. Numbers are first guesses for the final balancing
 * pass; every hit goes through the hunter's power like any skill's, and the light never makes anyone untouchable.
 */
public final class RulerPowers {
    /** The light's own colour, for a hunter sworn but not yet invested. */
    public static final int DAWN_GOLD = 0xFFE9A8;

    // ================================================================ numbers

    public static int sanctumSeconds(int l) { return 14 + l; }
    /** How much harder the ruler and their allies hit inside the sanctum (percent). */
    public static float sanctumMight(int l) { return 12 + l; }
    /** How much less the ruler and their allies take inside the sanctum (percent). */
    public static final float SANCTUM_GUARD = 12;
    /** What the sanctum mends of its allies' health each second (a fraction; twice as much under Dawn). */
    public static final float SANCTUM_MEND = 0.01f;
    /** What the sanctum's light does to each foe inside it every second. */
    public static float sanctumPulse(int l, int t) { return 2 * mult(l, t); }
    /** How much more a foe inside a Verdict's sanctum takes from every blow (percent). */
    public static final float VERDICT_SANCTUM = 10;

    public static float judgementRadius(int l) { return 12 + l / 2f; }
    public static float judgementHit(int l, int t) { return 6 * mult(l, t); }
    /** How much more the judged take from every blow (percent; half for bosses). */
    public static float judgedBonus(int l) { return 15 + l; }
    public static float judgementStun(int l) { return 1 + 0.1f * l; }
    public static final int JUDGED_TICKS = 160;
    /** Ticks from the sigils to the lances landing. */
    public static final int JUDGEMENT_FALL = 14;

    public static int seraphSeconds(int l) { return 18 + l; }
    /** How much harder a ruler in seraph form hits (percent). */
    public static float seraphMight(int l) { return 20 + 2 * l; }
    /** How much less a ruler in seraph form takes (percent). */
    public static float seraphGuard(int l) { return 15 + l; }

    public static final float BLADE_RADIUS = 8;
    public static final int BLADE_FALL = 26;
    public static final int BLADE_STAND_SECONDS = 5;
    public static float bladeHit(int l, int t) { return 50 * mult(l, t); }
    public static float bladeBurn(int l, int t) { return 3 * mult(l, t); }

    public static final float CHOIR_RANGE = 20;
    public static final int CHOIR_SECONDS = 6;
    public static int choirFeathers(int l) { return 12 + l; }
    public static float featherHit(int l, int t) { return 7 * mult(l, t); }

    public static final float HAND_RADIUS = 7;
    /** Ticks the hand takes to come down and close. */
    public static final int HAND_GRAB = 30;
    /** Ticks it holds what it closed on before it slams it down. */
    public static final int HAND_HOLD = 30;
    public static float handCrush(int l, int t) { return 20 * mult(l, t); }
    public static float handSlam(int l, int t) { return 30 * mult(l, t); }

    public static final float DAYBREAK_RADIUS = 16;
    public static final int DAYBREAK_SECONDS = 8;
    public static float daybreakBurn(int l, int t) { return 4 * mult(l, t); }
    public static float daybreakFlare(int l, int t) { return 15 * mult(l, t); }
    /** What Daybreak mends of each ally's health every second (a fraction). */
    public static final float DAYBREAK_MEND = 0.03f;

    /** The sanctum's radius (the setting; its default before the settings are read, as on a fresh client). */
    public static int radius() {
        return LightConfig.sanctumRadius();
    }

    // ================================================================ the powers

    public static final Skill SANCTUM = power("light_sanctum", 0xFFE08A, 60, 1800, 1200, RulerPowers::sanctum,
            (l, t) -> a(f(sanctumSeconds(l)), f(radius()), f(sanctumMight(l)), f(SANCTUM_GUARD), f(SANCTUM_MEND * 100), f(sanctumPulse(l, t))));
    public static final Skill JUDGEMENT = power("light_judgement", 0xFFF3C2, 40, 900, 600, RulerPowers::judgement,
            (l, t) -> a(f(judgementRadius(l)), f(judgementHit(l, t)), f(judgementStun(l)), f(judgedBonus(l)), f(JUDGED_TICKS / 20f))).circle(false);
    public static final Skill SERAPH = power("light_seraph", 0xFFF6D8, 80, 3600, 2400, RulerPowers::seraph,
            (l, t) -> a(f(seraphSeconds(l)), f(seraphMight(l)), f(seraphGuard(l))));

    public static final Skill BLADE_OF_HEAVEN = power(Mantle.VERDICT.authority(), Mantle.VERDICT.color(), 100, 2400, 1600,
            RulerPowers::bladeOfHeaven, (l, t) -> a(f(bladeHit(l, t)), f(BLADE_RADIUS), f(BLADE_STAND_SECONDS), f(bladeBurn(l, t))));
    public static final Skill CHOIR_OF_WINGS = power(Mantle.WINGS.authority(), Mantle.WINGS.color(), 100, 2400, 1600,
            RulerPowers::choirOfWings, (l, t) -> a(f(choirFeathers(l)), f(CHOIR_RANGE), f(featherHit(l, t)), f(CHOIR_SECONDS)));
    public static final Skill HAND_OF_HEAVEN = power(Mantle.HAND.authority(), Mantle.HAND.color(), 100, 2400, 1600,
            RulerPowers::handOfHeaven, (l, t) -> a(f(HAND_RADIUS), f(handCrush(l, t)), f(HAND_HOLD / 20f), f(handSlam(l, t))));
    public static final Skill DAYBREAK = power(Mantle.DAWN.authority(), Mantle.DAWN.color(), 100, 2400, 1600,
            RulerPowers::daybreak, (l, t) -> a(f(DAYBREAK_SECONDS), f(DAYBREAK_RADIUS), f(daybreakBurn(l, t)), f(DAYBREAK_MEND * 100), f(daybreakFlare(l, t))));

    /** A ruler's power: S grade, never by level or book ({@code advanced}), anyone's class ({@code owner} null), light. */
    private static Skill power(String path, int color, float mana, int cooldown, int cooldownAtMax, Skill.Action action, Skill.Args args) {
        Skill s = new Skill(path, Skill.Kind.ACTIVE, null, PlayerRank.S, -1, true, color, mana, cooldown, cooldownAtMax, action, args);
        return Skills.add(s.element(SkillElement.LIGHT).ruler(true));
    }

    /** Touch the class so every power is registered (from {@link Skills#init}). */
    static void init() {}

    // ================================================================ what the light gives

    /** The power on a crown key: 0 the sanctum, 1 judgement, 2 the seraph form, 3 the mantle's authority. */
    public static @Nullable Skill onKey(@Nullable Mantle m, int key) {
        return switch (key) {
            case 0 -> SANCTUM;
            case 1 -> JUDGEMENT;
            case 2 -> SERAPH;
            case 3 -> m == null ? null : authority(m);
            default -> null;
        };
    }

    public static Skill authority(Mantle m) {
        return switch (m) {
            case VERDICT -> BLADE_OF_HEAVEN;
            case WINGS -> CHOIR_OF_WINGS;
            case HAND -> HAND_OF_HEAVEN;
            case DAWN -> DAYBREAK;
        };
    }

    /**
     * Whether the light has given a hunter this power yet: Judgement and Seraph Form on the road
     * ({@code light.judgementLevel}, {@code light.seraphLevel}, or the Investiture), the sanctum and the mantle's
     * authority at the Investiture.
     */
    public static boolean due(Player p, Skill s) {
        if (!LightAPI.sworn(p)) return false;
        Mantle m = LightAPI.mantle(p);
        if (s == SANCTUM) return m != null;
        if (s == JUDGEMENT) return m != null || SystemAPI.get(p).level() >= LightConfig.judgementLevel();
        if (s == SERAPH) return m != null || SystemAPI.get(p).level() >= LightConfig.seraphLevel();
        return m != null && s == authority(m);
    }

    /**
     * Gives the hunter every power the light owes them now and takes back any they shouldn't hold (another mantle's
     * authority, everything once they're no longer sworn). Returns the powers newly given.
     */
    public static List<Skill> grant(ServerPlayer p) {
        List<Skill> given = new ArrayList<>();
        boolean changed = false;
        for (Skill s : Skills.rulerPowers()) {
            boolean due = due(p, s);
            boolean knows = SkillAPI.knows(p, s);
            if (due && !knows) {
                SkillAPI.learn(p, s, 1, SkillEvent.Source.OTHER);
                given.add(s);
            } else if (!due && knows) {
                SkillAPI.get(p).forget(s.path());
                changed = true;
            }
        }
        if (changed) {
            SkillPassives.apply(p);
            SkillAPI.sync(p);
        }
        return given;
    }

    /** The light taken back: every power forgotten. */
    public static void forgetAll(ServerPlayer p) {
        for (Skill s : Skills.rulerPowers()) {
            if (SkillAPI.knows(p, s)) SkillAPI.get(p).forget(s.path());
        }
        SkillPassives.apply(p);
        SkillAPI.sync(p);
    }

    /** A crown key was pressed by a hunter sworn to the light. Returns true if the power went off. */
    public static boolean castKey(ServerPlayer p, int key) {
        if (!LightAPI.sworn(p)) return false;
        Skill s = onKey(LightAPI.mantle(p), key);
        return s != null && SkillAPI.knows(p, s) && SkillAPI.cast(p, s);
    }

    /** The colour of a hunter's light: their mantle's, or the dawn's gold before they take one up. */
    public static int color(Player p) {
        Mantle m = LightAPI.mantle(p);
        return m == null ? DAWN_GOLD : m.color();
    }

    // ================================================================ the three the light gives

    static boolean sanctum(ServerPlayer p, Skill s, int l) {
        Mantle m = LightAPI.mantle(p);
        if (m == null) return false;
        int r = radius();
        LightSanctum.open(p, m, sanctumSeconds(l) * 20, l);
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        int c = m.color();
        WorldBossFx.ring(level, at, 0.5f, r, 14, c);
        WorldBossFx.circle(level, at, r, 24, c);
        SkillFx.play(p, Kind.NOVA, c, at, r, 3);
        SkillFx.play(p, Kind.HEAL, c, at, 3, 0);
        SkillFx.play(p, Kind.HALO, c, at, 1.4f, 40);
        SkillParticles.send(level, ParticleTypes.END_ROD, at.x, at.y + 2, at.z, 50, r * 0.3, 1.5, r * 0.3, 0.02);
        SubclassKit.sound(p, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.2f);
        SubclassKit.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 0.6f);
        SubclassKit.sound(p, SoundEvents.BELL_RESONATE, 1.2f, 0.8f);
        return true;
    }

    /** Once a second, from the sanctum: what its light does to one foe inside it, by the ruler's mantle. */
    public static void sanctumPulse(ServerPlayer p, Mantle m, int l, LivingEntity e, int second) {
        int t = SubclassKit.tier(p);
        strike(p, SANCTUM, e, sanctumPulse(l, t));
        switch (m) {
            case VERDICT -> {
                // the sanctum's law: everyone inside it is already judged a little
                LightSanctum.judge(e, e.level().getGameTime() + 30, VERDICT_SANCTUM);
                if (second % 3 == 0) SubclassKit.fxAt(p.serverLevel(), Kind.MARK, m.color(), e.position(), e.getBbHeight(), 30);
            }
            case WINGS -> {
                // the air itself is the light's: nothing that flies stays up in it
                if (!e.onGround()) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), p);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), p);
            }
            case HAND -> {
                // the unseen hand presses everything inside it down
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2), p);
                if (!Foes.isBoss(e) && e.getDeltaMovement().y > -0.4) e.setDeltaMovement(e.getDeltaMovement().multiply(0.6, 1, 0.6).add(0, -0.25, 0));
                e.hurtMarked = true;
            }
            case DAWN -> {
                Statuses.apply(e, DamageElement.FIRE, 6, p);
                if (e.getRemainingFireTicks() < 20) e.igniteForSeconds(1);
            }
        }
    }

    static boolean judgement(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        int c = color(p);
        int t = SubclassKit.tier(p);
        float radius = judgementRadius(l);
        List<LivingEntity> foes = foes(p, at, radius);
        if (foes.isEmpty()) return false;
        int stun = Math.round(judgementStun(l) * 20);
        float bonus = judgedBonus(l);
        // a sigil over each, then a lance of light down onto it
        for (LivingEntity e : foes) {
            SkillFx.play(e, Kind.MARK, c, e.position(), e.getBbHeight(), JUDGEMENT_FALL + 20);
            LightConstruct.drop(level, LightConstruct.Form.SPEAR, e.position(), c, 1f, JUDGEMENT_FALL, 26);
        }
        SubclassKit.later(p, JUDGEMENT_FALL, () -> {
            long until = level.getGameTime() + JUDGED_TICKS;
            int judged = 0;
            for (LivingEntity e : foes) {
                if (!e.isAlive() || e.level() != level) continue;
                boolean boss = Foes.isBoss(e);
                strike(p, s, e, judgementHit(l, t));
                SkillCombat.stun(e, boss ? 20 : stun);
                LightSanctum.judge(e, until, (boss ? 0.5f : 1f) * bonus);
                SubclassKit.fxAt(level, Kind.PILLAR, c, e.position(), 0.9f, 0);
                SkillParticles.send(level, ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.2, 0.6, 0.2, 0.08);
                judged++;
            }
            LightAPI.judged(p, judged);
            SubclassKit.sound(level, at, SoundEvents.TRIDENT_THUNDER.value(), 0.9f, 1.4f);
            SubclassKit.sound(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 1.4f, 0.6f);
        });
        WorldBossFx.wave(level, at, radius, 2f, 10, c);
        SkillFx.play(p, Kind.HALO, c, at, 1.2f, 30);
        LightSanctum.fx(p, com.beyondsolo.network.LightFxPayload.JUDGEMENT, c, Math.round(radius), 0);
        SubclassKit.sound(p, SoundEvents.BELL_BLOCK, 1.6f, 0.5f);
        SubclassKit.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.4f, 1.5f);
        return true;
    }

    static boolean seraph(ServerPlayer p, Skill s, int l) {
        if (!LightAPI.sworn(p)) return false;
        int c = color(p);
        LightSanctum.seraph(p, c, seraphSeconds(l) * 20, l);
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        SkillFx.play(p, Kind.PILLAR, c, at, 2.2f, 0);
        WorldBossFx.burst(level, at.add(0, 1, 0), 4f, c);
        SkillFx.play(p, Kind.NOVA, c, at, 6, 3);
        SkillParticles.send(level, ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 50, 0.6, 1, 0.6, 0.18);
        SkillParticles.send(level, ParticleTypes.WHITE_ASH, at.x, at.y + 1.5, at.z, 40, 1.2, 1, 1.2, 0.04);
        SubclassKit.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.4f, 1.2f);
        SubclassKit.sound(p, SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 1.4f);
        SubclassKit.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 2f, 0.8f);
        return true;
    }

    // ================================================================ the authorities

    /** The Verdict: a sword of light the size of a tower comes down where they look, and stands there burning. */
    static boolean bladeOfHeaven(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 target = SubclassKit.aimGround(p, 32);
        int c = Mantle.VERDICT.color();
        int t = SubclassKit.tier(p);
        authority(p, Mantle.VERDICT, BLADE_FALL + BLADE_STAND_SECONDS * 20);
        LightConstruct.drop(level, LightConstruct.Form.BLADE, target, c, 1f, BLADE_FALL, BLADE_FALL + BLADE_STAND_SECONDS * 20 + 16);
        WorldBossFx.circle(level, target, BLADE_RADIUS, BLADE_FALL, c);
        SubclassKit.fxAt(level, Kind.ZONE, c, target, BLADE_RADIUS, BLADE_FALL);
        SubclassKit.sound(p, SoundEvents.BELL_RESONATE, 1.6f, 0.5f);
        SubclassKit.sound(level, target, SoundEvents.BEACON_ACTIVATE, 2f, 0.5f);
        SubclassKit.later(p, BLADE_FALL, () -> {
            for (LivingEntity e : foes(p, target, BLADE_RADIUS)) {
                float k = 1 - 0.4f * (float) Math.min(1, e.position().distanceTo(target) / BLADE_RADIUS);
                strike(p, s, e, bladeHit(l, t) * k);
                if (!Foes.isBoss(e)) {
                    SkillCombat.stun(e, 30);
                    SubclassKit.push(e, target, 1.1, 0.8);
                }
            }
            WorldBossFx.burst(level, target.add(0, 1, 0), 7f, c);
            WorldBossFx.quake(level, target, 14f, c);
            SubclassKit.fxAt(level, Kind.CRACK, c, target, BLADE_RADIUS + 2, 0);
            SubclassKit.fxAt(level, Kind.NOVA, c, target, BLADE_RADIUS + 3, 3);
            SubclassKit.fxAt(level, Kind.GEYSER, c, target, 10, 2);
            SkillParticles.send(level, ParticleTypes.FLASH, target.x, target.y + 1, target.z, 1, 0, 0, 0, 0);
            SkillParticles.send(level, ParticleTypes.END_ROD, target.x, target.y + 1, target.z, 80, 2.5, 3, 2.5, 0.3);
            SubclassKit.sound(level, target, SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.6f);
            SubclassKit.sound(level, target, SoundEvents.ANVIL_LAND, 2f, 0.5f);
            SubclassKit.sound(level, target, SoundEvents.TRIDENT_THUNDER.value(), 2f, 0.6f);
            // the blade stands, and the ground round it burns with its verdict
            SubclassKit.repeat(p, BLADE_STAND_SECONDS * 20, 20, () -> {
                for (LivingEntity e : foes(p, target, BLADE_RADIUS * 0.7f)) {
                    strike(p, s, e, bladeBurn(l, t));
                    LightSanctum.judge(e, level.getGameTime() + 30, VERDICT_SANCTUM);
                }
                SkillParticles.send(level, ParticleTypes.END_ROD, target.x, target.y + 0.4, target.z, 16, BLADE_RADIUS * 0.3, 0.2, BLADE_RADIUS * 0.3, 0.02);
                SubclassKit.fxAt(level, Kind.PULSE, c, target, BLADE_RADIUS * 0.7f, 1);
            });
        });
        return true;
    }

    /** Wings: they rise on a choir of wings, and feathers like spears rain on everything round them. */
    static boolean choirOfWings(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        List<LivingEntity> foes = new ArrayList<>(foes(p, at, CHOIR_RANGE));
        if (foes.isEmpty()) return false;
        foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        int c = Mantle.WINGS.color();
        int t = SubclassKit.tier(p);
        int feathers = choirFeathers(l);
        authority(p, Mantle.WINGS, CHOIR_SECONDS * 20);
        // lifted on the wings, then held up by them
        p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 22, 2, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, CHOIR_SECONDS * 20 + 40, 0, false, false));
        LightConstruct.follow(level, LightConstruct.Form.CHOIR, p, c, 1f, CHOIR_SECONDS * 20);
        SkillFx.play(p, Kind.UPDRAFT, c, at, 2.5f, 30);
        SubclassKit.sound(p, SoundEvents.ENDER_DRAGON_FLAP, 2f, 0.8f);
        SubclassKit.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.4f, 1.6f);
        int span = Math.max(1, (CHOIR_SECONDS * 20 - 40) / feathers);
        for (int i = 0; i < feathers; i++) {
            final int k = i;
            SubclassKit.later(p, 20 + i * span, () -> {
                List<LivingEntity> alive = new ArrayList<>();
                for (LivingEntity e : foes) if (e.isAlive() && e.level() == level) alive.add(e);
                if (alive.isEmpty()) return;
                LivingEntity e = alive.get(k % alive.size());
                Vec3 spot = e.position();
                LightConstruct.drop(level, LightConstruct.Form.FEATHER, spot, c, 1f, 8, 40);
                SubclassKit.later(p, 8, () -> {
                    for (LivingEntity o : foes(p, spot, 1.8)) {
                        strike(p, s, o, featherHit(l, t));
                        if (!Foes.isBoss(o)) o.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3), p);
                    }
                    SubclassKit.fxAt(level, Kind.SPLASH, c, spot.add(0, 0.3, 0), 1.4f, 1);
                    SkillParticles.send(level, ParticleTypes.END_ROD, spot.x, spot.y + 0.5, spot.z, 6, 0.3, 0.3, 0.3, 0.08);
                    SubclassKit.sound(level, spot, SoundEvents.TRIDENT_HIT, 1f, 1.3f + 0.02f * k);
                });
            });
        }
        return true;
    }

    /** The Unseen Hand: a hand of light comes down out of the sky, closes on everything under it, and slams it down. */
    static boolean handOfHeaven(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 target = SubclassKit.aimGround(p, 28);
        int c = Mantle.HAND.color();
        int t = SubclassKit.tier(p);
        authority(p, Mantle.HAND, HAND_GRAB + HAND_HOLD + 20);
        LightConstruct.drop(level, LightConstruct.Form.HAND, target, c, 1f, HAND_GRAB, HAND_GRAB + HAND_HOLD + 36);
        WorldBossFx.circle(level, target, HAND_RADIUS, HAND_GRAB, c);
        SubclassKit.fxAt(level, Kind.ZONE, c, target, HAND_RADIUS, HAND_GRAB + HAND_HOLD);
        SubclassKit.sound(p, SoundEvents.ELDER_GUARDIAN_AMBIENT, 1.4f, 0.6f);
        SubclassKit.sound(level, target, SoundEvents.BEACON_ACTIVATE, 2f, 0.6f);
        List<LivingEntity> held = new ArrayList<>();
        SubclassKit.later(p, HAND_GRAB, () -> {
            for (LivingEntity e : foes(p, target, HAND_RADIUS)) {
                held.add(e);
                strike(p, s, e, handCrush(l, t));
                SkillCombat.stun(e, Foes.isBoss(e) ? 20 : HAND_HOLD + 10);
                if (!Foes.isBoss(e)) SubclassKit.launch(e, 0.9);
                SubclassKit.fxAt(level, Kind.BIND, c, e.position(), 1.2f, HAND_HOLD);
            }
            SubclassKit.fxAt(level, Kind.QUAKE, c, target, HAND_RADIUS, 10);
            SubclassKit.sound(level, target, SoundEvents.IRON_GOLEM_DAMAGE, 2f, 0.5f);
            SubclassKit.sound(level, target, SoundEvents.AMETHYST_BLOCK_BREAK, 2f, 0.5f);
        });
        // held up in the fist, then thrown down
        SubclassKit.repeat(p, HAND_HOLD - 6, 2, () -> {
            for (LivingEntity e : held) {
                if (e.isAlive() && !Foes.isBoss(e) && e.getY() < target.y + 4) {
                    e.setDeltaMovement(e.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 0.12, 0));
                    e.hurtMarked = true;
                    e.fallDistance = 0;
                }
            }
        });
        SubclassKit.later(p, HAND_GRAB + HAND_HOLD, () -> {
            for (LivingEntity e : foes(p, target, HAND_RADIUS + 1.5)) {
                strike(p, s, e, handSlam(l, t));
                if (!Foes.isBoss(e)) {
                    e.setDeltaMovement(0, -2.2, 0);
                    e.hurtMarked = true;
                }
            }
            WorldBossFx.quake(level, target, 12f, c);
            WorldBossFx.burst(level, target.add(0, 1, 0), 5f, c);
            SubclassKit.fxAt(level, Kind.SLAM, c, target, HAND_RADIUS, 0);
            SubclassKit.fxAt(level, Kind.CRACK, c, target, HAND_RADIUS + 2, 0);
            SkillParticles.send(level, ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 0.5, target.z, 1, 0, 0, 0, 0);
            SubclassKit.sound(level, target, SoundEvents.GENERIC_EXPLODE.value(), 2.5f, 0.5f);
            SubclassKit.sound(level, target, SoundEvents.ANVIL_LAND, 1.6f, 0.4f);
        });
        return true;
    }

    /** Dawn: a sun rises over the fight that burns their foes and mends whoever stands with them, then flares out. */
    static boolean daybreak(ServerPlayer p, Skill s, int l) {
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        Vec3 sun = at.add(0, 9, 0);
        int c = Mantle.DAWN.color();
        int t = SubclassKit.tier(p);
        authority(p, Mantle.DAWN, DAYBREAK_SECONDS * 20 + 20);
        LightConstruct.hold(level, LightConstruct.Form.SUN, at, c, 1f, DAYBREAK_SECONDS * 20 + 24);
        WorldBossFx.circle(level, at, DAYBREAK_RADIUS, DAYBREAK_SECONDS * 20, c);
        SubclassKit.fxAt(level, Kind.ZONE, c, at, DAYBREAK_RADIUS, DAYBREAK_SECONDS * 20);
        SubclassKit.sound(p, SoundEvents.BEACON_ACTIVATE, 2f, 0.8f);
        SubclassKit.sound(p, SoundEvents.FIRECHARGE_USE, 1.6f, 0.5f);
        for (int i = 1; i <= DAYBREAK_SECONDS; i++) {
            SubclassKit.later(p, 10 + i * 20, () -> {
                for (LivingEntity e : foes(p, at, DAYBREAK_RADIUS)) {
                    strike(p, s, e, daybreakBurn(l, t));
                    if (e.getRemainingFireTicks() < 20) e.igniteForSeconds(1);
                    SubclassKit.fxAt(level, Kind.BEAM, c, e.position().add(0, e.getBbHeight() * 0.5, 0), 0.4f, 8);
                }
                for (Player ally : allies(p, at, DAYBREAK_RADIUS)) {
                    Balance.regen(ally, ally.getMaxHealth() * DAYBREAK_MEND);
                    SkillParticles.send(level, ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getY() + 1, ally.getZ(), 3, 0.3, 0.5, 0.3, 0);
                }
                SkillParticles.send(level, ParticleTypes.END_ROD, sun.x, sun.y, sun.z, 12, 1.2, 1.2, 1.2, 0.12);
                SubclassKit.fxAt(level, Kind.PULSE, c, at, DAYBREAK_RADIUS, 1);
                SubclassKit.sound(level, sun, SoundEvents.FIRE_AMBIENT, 1.4f, 0.6f);
            });
        }
        SubclassKit.later(p, 20 + DAYBREAK_SECONDS * 20, () -> {
            for (LivingEntity e : foes(p, at, DAYBREAK_RADIUS)) {
                strike(p, s, e, daybreakFlare(l, t));
                Statuses.apply(e, DamageElement.FIRE, 30, p);
            }
            WorldBossFx.burst(level, sun, 8f, c);
            WorldBossFx.wave(level, at, DAYBREAK_RADIUS, 2.5f, 12, c);
            SubclassKit.fxAt(level, Kind.NOVA, c, at, DAYBREAK_RADIUS, 0);
            SkillParticles.send(level, ParticleTypes.FLASH, sun.x, sun.y, sun.z, 1, 0, 0, 0, 0);
            SubclassKit.sound(level, sun, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.8f);
            SubclassKit.sound(level, sun, SoundEvents.BEACON_DEACTIVATE, 2f, 0.6f);
        });
        return true;
    }

    // ================================================================ helpers

    /** The foes round a point the light reaches: never a shadow, the ruler's own or a friend's. */
    static List<LivingEntity> foes(ServerPlayer p, Vec3 at, double radius) {
        return MonarchPowers.foes(p, at, radius);
    }

    /** The hunters round a point (the ruler among them): the light's skills never strike a hunter, so all of them stand with it. */
    static List<Player> allies(ServerPlayer p, Vec3 at, double radius) {
        double r2 = radius * radius;
        return new ArrayList<>(p.level().getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius),
                o -> o.isAlive() && !o.isSpectator() && o.position().distanceToSqr(at) <= r2));
    }

    /** A ruler power's hit: the hunter's power, the combo chain, then the light. */
    static void strike(ServerPlayer p, Skill s, LivingEntity e, float damage) {
        MonarchPowers.strike(p, s, SkillElement.LIGHT, e, damage);
    }

    /** An authority is used: counted, and named on the screens of everyone near ({@code ticks} = how long it lasts). */
    private static void authority(ServerPlayer p, Mantle m, int ticks) {
        LightAPI.usedAuthority(p);
        LightSanctum.announce(p, m, ticks);
    }

    private RulerPowers() {}
}
