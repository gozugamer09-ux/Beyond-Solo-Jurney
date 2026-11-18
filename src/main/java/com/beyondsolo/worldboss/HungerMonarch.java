package com.beyondsolo.worldboss;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.combat.DamageElement;
import com.beyondsolo.combat.Statuses;
import com.beyondsolo.enemy.Habitat;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Khorvane, Monarch of Hunger: a feral king in a wolf's skull with blades for claws. The longer he hunts, the faster he
 * gets: every blow he lands feeds his frenzy (faster on his feet, up to half again), and it ebbs when he goes hungry.
 * He never heals.
 * <ul>
 *   <li><b>Domain, the Red Hunt</b>: hunters inside starve (their food drains) and their wounds bleed.</li>
 *   <li><b>I. The Hunt</b>: quick claws and a double rend, a pounce onto his prey, and a howl that shakes and bleeds.</li>
 *   <li><b>II. Blood Frenzy</b>: he tears through his prey one after another in straight dashes, and the ground under
 *   them opens in a red maw.</li>
 *   <li><b>III. The Feast</b>, his authority: he marks every hunter near, then dashes through each in turn.</li>
 * </ul>
 */
public class HungerMonarch extends MonarchBoss {
    static final int CLAW = 1, HOWL = 2, POUNCE = 3, MAW = 4, DASH = 5;
    private static final ResourceLocation FRENZY_ID = BeyondSolo.id("monarch_frenzy");
    public static final int FRENZY_MAX = 15;

    private int frenzy;
    private int hungry;

    public HungerMonarch(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    public WorldBossKind kind() {
        return WorldBossKind.HUNGER;
    }

    @Override
    protected List<Habitat> armyHabitats() {
        return List.of(Habitat.BEAST, Habitat.DEMON);
    }

    @Override
    protected String authority() {
        return "the_feast";
    }

    @Override
    protected double preferred() {
        return 1.5;
    }

    public int frenzy() {
        return frenzy;
    }

    @Override
    protected void domainTick(ServerLevel level, LivingEntity e) {
        e.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 2, true, false), this);
        Statuses.apply(e, DamageElement.BLOOD, phase() >= 3 ? 9f : 5f, this);
    }

    @Override
    public boolean hit(LivingEntity e, float scale, Vec3 away, double push, double lift) {
        boolean landed = super.hit(e, scale, away, push, lift);
        if (landed) {
            frenzy = Math.min(FRENZY_MAX, frenzy + 1);
            hungry = 0;
            applyFrenzy();
        }
        return landed;
    }

    private void applyFrenzy() {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(FRENZY_ID);
        if (frenzy > 0) speed.addTransientModifier(new AttributeModifier(FRENZY_ID, frenzy * 0.035, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel server) || !isAlive()) return;
        if (frenzy > 0 && ++hungry >= 40) {
            hungry = 0;
            frenzy--;
            applyFrenzy();
        }
        if (frenzy >= 6 && tickCount % 4 == 0) {
            server.sendParticles(new DustParticleOptions(new Vector3f(0.9f, 0.1f, 0.15f), 1.2f), getX(), getY() + getBbHeight() * 0.6, getZ(),
                    1 + frenzy / 5, 0.4, 0.6, 0.4, 0.01);
        }
    }

    // ================================================================ moves

    @Override
    protected List<Move> buildMoves() {
        List<Move> m = new ArrayList<>();
        m.add(new Move("claw", 18, 0, 4.5f, 5, 1, 0, false, this::claw));
        m.add(new Move("rend", 32, 0, 4.5f, 4, 1, 0, false, this::rend));
        m.add(new Move("pounce", 36, 4, 24, 4, 1, 60, true, this::pounce));
        m.add(new Move("howl", 40, 0, 12, 2, 1, 200, false, this::howl));
        m.add(new Move("blood_frenzy", 76, 0, 26, 3, 2, 140, true, (t, tick, s) -> dashes(t, tick, s, 4, false)));
        m.add(new Move("maw", 44, 0, 24, 3, 2, 120, false, this::maw));
        m.add(new Move("the_feast", 150, 0, 64, 1, 3, 900, true, (t, tick, s) -> dashes(t, tick, s, 6, true)));
        return m;
    }

    private void claw(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.CONE, ground(position()), s.dir, 70, 4.8f, 0, 8, 0.8f, CLAW, fxColor());
        }
        if (t == 6) sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 1.3f);
    }

    private void rend(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0 || t == 12) {
            s.dir = face(target);
            Vec3 d = s.dir.yRot((t == 0 ? 20 : -20) * Mth.DEG_TO_RAD);
            strike(Shape.CONE, ground(position()), d, 60, 5f, 0, 9, 0.8f, CLAW, fxColor());
        }
        if (t == 7 || t == 19) sound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 1.1f);
    }

    private void pounce(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            s.spot = ground(target != null ? target.position() : ahead(s.dir, 8));
            s.spots.add(position());
            circleStrike(s.spot, 3.6f, 18, 1.1f, POUNCE);
            sound(SoundEvents.WOLF_GROWL, 3f, 0.5f);
        }
        if (t >= 4 && t <= 18) {
            float k = (t - 4) / 14f;
            Vec3 at = s.spots.get(0).lerp(s.spot, k).add(0, 4 * 4 * k * (1 - k), 0);
            noPhysics = true;
            setPos(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
            if (t == 18) noPhysics = false;
        }
    }

    private void howl(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            strike(Shape.RING, ground(position()), Vec3.ZERO, 11, 0, 2, 18, 0.5f, HOWL, fxColor());
        }
        if (t == 14) sound(SoundEvents.WOLF_HOWL, 5f, 0.4f);
        if (t == 20 && phase() >= 2 && level() instanceof ServerLevel server) {
            MonarchArmy.call(server, this, position(), 2, kind().affinity, List.of(Habitat.BEAST), target);
        }
    }

    private void maw(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            s.spot = ground(target != null ? target.position() : position());
            circleStrike(s.spot, 6f, 26, 1.6f, MAW);
            sound(SoundEvents.EVOKER_PREPARE_ATTACK, 3f, 0.5f);
        }
    }

    /**
     * Straight dashes through his prey, one after another, each drawn on the ground first. {@code feast}: his authority,
     * through every hunter near (marked first).
     */
    private void dashes(@Nullable LivingEntity target, int t, MoveState s, int count, boolean feast) {
        if (!(level() instanceof ServerLevel server)) return;
        int lead = feast ? 40 : 6;
        if (t == 0) {
            List<LivingEntity> prey = fighters(feast ? 34 : 26);
            if (prey.isEmpty() && target != null) prey = List.of(target);
            for (int i = 0; i < count && !prey.isEmpty(); i++) s.spots.add(prey.get(i % prey.size()).position());
            if (feast) {
                announceAuthority(server, lead + 10);
                sound(SoundEvents.WOLF_HOWL, 5f, 0.3f);
                for (LivingEntity e : prey) {
                    server.sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + e.getBbHeight() + 0.5, e.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
        int each = 16;
        if (t < lead || s.spots.isEmpty()) return;
        int k = (t - lead) % each, n = (t - lead) / each;
        if (n >= s.spots.size()) return;
        if (k == 0) {
            // re-aim at where that hunter is now, and draw the line through them
            LivingEntity now = null;
            for (LivingEntity e : fighters(40)) if (e.position().distanceTo(s.spots.get(n)) < 6) now = e;
            Vec3 at = now != null ? now.position() : s.spots.get(n);
            s.dir = at.subtract(position()).multiply(1, 0, 1);
            if (s.dir.lengthSqr() < 1e-4) s.dir = Vec3.directionFromRotation(0, getYRot());
            s.dir = s.dir.normalize();
            double length = Mth.clamp(position().distanceTo(at) + 6, 8, 30);
            s.spot = position().add(s.dir.scale(length));
            faceDir(s.dir);
            strike(Shape.LINE, ground(position()), s.dir, 2.6f, (float) length, 0, 10, feast ? 1.4f : 1.0f, DASH, fxColor());
        }
        if (k == 10) {
            // he is through: at the end of the line before you can blink
            Vec3 end = ground(s.spot);
            blinkTo(end);
            server.sendParticles(new DustParticleOptions(new Vector3f(0.9f, 0.1f, 0.15f), 1.6f), getX(), getY() + 1.5, getZ(), 30, 0.5, 0.8, 0.5,
                    0.05);
            sound(SoundEvents.PLAYER_ATTACK_STRONG, 2f, 0.6f);
        }
    }

    // ================================================================ when blows land

    @Override
    protected void landed(ServerLevel level, Strike s, List<LivingEntity> caught) {
        switch (s.tag()) {
            case HOWL -> {
                for (LivingEntity e : caught) {
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1), this);
                    Statuses.apply(e, DamageElement.BLOOD, 25f, this);
                }
            }
            case POUNCE -> {
                dust(s.at(), 3f, 30);
                shake(0.5f, 32);
            }
            case MAW -> {
                WorldBossFx.burst(level, s.at().add(0, 0.5, 0), 5f, fxColor());
                WorldBossFx.quake(level, s.at(), 6f, fxColor());
                for (LivingEntity e : caught) Statuses.apply(e, DamageElement.BLOOD, 40f, this);
                soundAt(s.at(), SoundEvents.EVOKER_FANGS_ATTACK, 3f, 0.5f);
            }
            case DASH -> {
                for (LivingEntity e : caught) Statuses.apply(e, DamageElement.BLOOD, s.scale() > 1.2f ? 45f : 25f, this);
            }
            default -> {
            }
        }
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Frenzy", frenzy);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        frenzy = Mth.clamp(tag.getInt("Frenzy"), 0, FRENZY_MAX);
        applyFrenzy();
    }
}
