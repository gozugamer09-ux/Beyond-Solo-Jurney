package com.beyondsolo.isle;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.system.Balance;
import com.beyondsolo.worldboss.WorldBossFx;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Azhrak, the Hive King: born out of the cocoon behind the queen's dais the moment she dies, the swarm's last and
 * deadliest child. Lean, winged, armoured in black chitin, with claws like scythes, and fast: he is on a hunter before
 * they see him move.
 * <ul>
 *   <li><b>I. The Newborn</b>: claws, a dash straight through his prey (drawn on the ground first), a dive from the
 *   air, a spray of acid.</li>
 *   <li><b>II. The Hunger</b>: a roar that leaves hunters shaking, and he devours his own swarm to mend himself (a
 *   little for each, a few at most).</li>
 *   <li><b>III. The King</b>: faster still, and a frenzy of blows that turns as he does.</li>
 * </ul>
 */
public class KingBoss extends HiveBoss {
    static final int CLAW = 1, RUSH = 2, DIVE = 3, SPRAY = 4, ROAR = 5;
    /** What each of his swarm he devours mends (a share of his health), and how many at most a time. */
    static final float DEVOUR_HEAL = 0.025f;
    static final int DEVOUR_MAX = 3;

    public KingBoss(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Override
    public WorldBossKind kind() {
        return WorldBossKind.KING;
    }

    @Override
    protected double preferred() {
        return 2.2;
    }

    @Override
    protected int introLength() {
        return 90;
    }

    @Override
    public int fxColor() {
        return phase() >= 3 ? 0x9CFF2E : kind().color;
    }

    // ================================================================ moves

    @Override
    protected List<Move> buildMoves() {
        List<Move> m = new ArrayList<>();
        m.add(new Move("claws", 26, 0, 4.5f, 6, 1, 0, false, this::claws));
        m.add(new Move("rush", 30, 4, 24, 4, 1, 70, true, this::rush));
        m.add(new Move("dive", 40, 6, 30, 3, 1, 150, true, this::dive));
        m.add(new Move("spray", 34, 0, 9, 3, 1, 120, false, this::spray));
        m.add(new Move("roar", 40, 0, 22, 2, 2, 320, false, this::roar));
        m.add(new Move("devour", 50, 0, 40, 2, 2, 520, true, this::devour));
        m.add(new Move("frenzy", 70, 0, 6, 3, 3, 220, false, this::frenzy));
        return m;
    }

    /** Two cuts of his claws, one after the other. */
    private void claws(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0 || t == 12) {
            s.dir = face(target);
            strike(Shape.CONE, ground(position()), s.dir, 60, 5.5f, 0, 8, 0.9f, CLAW, fxColor());
        }
        if (t == 7 || t == 19) sound(SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.5f);
    }

    /** Straight through his prey, short of any wall. */
    private void rush(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            double want = target != null ? horizontalTo(target) + 5 : 14;
            double run = clearRun(s.dir, Math.min(24, want));
            s.spot = runEnd(s.dir, run);
            strike(Shape.LINE, ground(position()), s.dir, 2.8f, (float) run, 0, 10, 1.1f, RUSH, fxColor());
            sound(SoundEvents.PHANTOM_FLAP, 3f, 0.5f);
        }
        if (t == 10) {
            blinkTo(s.spot);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 1.5, getZ(), 3, 0.5, 0.5, 0.5, 0);
                server.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 1.2, getZ(), 16, 0.6, 0.8, 0.6, 0.1);
            }
            sound(SoundEvents.PLAYER_ATTACK_STRONG, 2.5f, 0.5f);
        }
    }

    /** Up on his wings, and down on his prey. */
    private void dive(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            s.spot = ground(target != null ? target.position() : ahead(s.dir, 10));
            s.spots.add(position());
            circleStrike(s.spot, 4f, 26, 1.3f, DIVE);
            sound(SoundEvents.PHANTOM_FLAP, 4f, 0.4f);
        }
        if (t >= 4 && t <= 26 && !s.spots.isEmpty()) {
            float k = (t - 4) / 22f;
            Vec3 at = s.spots.get(0).lerp(s.spot, k).add(0, 5 * 4 * k * (1 - k), 0);
            hold(at);
            if (t == 26) noPhysics = false;
        }
    }

    /** A spray of acid ahead of him. */
    private void spray(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            s.dir = face(target);
            strike(Shape.CONE, ground(position()), s.dir, 35, 9.5f, 0, 18, 0.8f, SPRAY, fxColor());
            sound(SoundEvents.LLAMA_SPIT, 3f, 0.4f);
        }
        if (t >= 10 && t <= 18 && level() instanceof ServerLevel server) {
            Vec3 from = position().add(s.dir.scale(1.2)).add(0, 2.6, 0);
            for (int i = 0; i < 3; i++) {
                Vec3 v = s.dir.yRot((random.nextFloat() - 0.5f) * 1.2f).scale(0.6 + random.nextDouble() * 0.5);
                server.sendParticles(ParticleTypes.ITEM_SLIME, from.x, from.y, from.z, 0, v.x, -0.1, v.z, 1.2);
            }
        }
    }

    /** A roar that leaves whoever it reaches shaking. */
    private void roar(@Nullable LivingEntity target, int t, MoveState s) {
        if (t == 0) {
            face(target);
            strike(Shape.RING, ground(position()), Vec3.ZERO, 13, 0, 2.5f, 18, 0.5f, ROAR, fxColor());
        }
        if (t == 16) {
            sound(SoundEvents.RAVAGER_ROAR, 5f, 0.9f);
            sound(SoundEvents.ENDER_DRAGON_GROWL, 4f, 1.6f);
            shake(0.8f, 48);
        }
    }

    /** He leaps on his own swarm and devours it: a little health back for each, a few at most. */
    private void devour(@Nullable LivingEntity target, int t, MoveState s) {
        if (!(level() instanceof ServerLevel server)) return;
        if (t == 0 || t == 16 || t == 32) {
            if (s.n >= DEVOUR_MAX) return;
            Mob meal = IsleRaids.nearestSwarm(server, position(), 30);
            if (meal == null) {
                if (t == 0) sound(SoundEvents.RAVAGER_ROAR, 4f, 1.1f);
                return;
            }
            Vec3 by = meal.position();
            faceDir(by.subtract(position()));
            blinkTo(ground(by.add(position().subtract(by).normalize().scale(1.2))));
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SLIME_BLOCK.defaultBlockState()), by.x, by.y + 0.6, by.z, 30,
                    0.4, 0.4, 0.4, 0.2);
            meal.discard();
            Balance.healRaw(this, getMaxHealth() * DEVOUR_HEAL);
            s.n++;
            sound(SoundEvents.GENERIC_EAT, 3f, 0.4f);
            sound(SoundEvents.PLAYER_BURP, 2f, 0.4f);
        }
    }

    /** Five blows in a row, each turned towards wherever his prey has got to. */
    private void frenzy(@Nullable LivingEntity target, int t, MoveState s) {
        if (t % 9 == 0 && t <= 36) {
            s.dir = face(target);
            float swing = (t / 9 % 2 == 0 ? 1 : -1) * 20;
            strike(Shape.CONE, ground(position()), s.dir.yRot(swing * net.minecraft.util.Mth.DEG_TO_RAD), 55, 6f, 0, 7, 0.8f, CLAW, fxColor());
        }
        if (t % 9 == 6 && t <= 42) sound(SoundEvents.PLAYER_ATTACK_SWEEP, 2f, 0.6f + 0.05f * (t / 9));
    }

    // ================================================================ when blows land

    @Override
    protected void landed(ServerLevel level, Strike s, List<LivingEntity> caught) {
        switch (s.tag()) {
            case DIVE -> {
                dust(s.at(), 4f, 40);
                WorldBossFx.quake(level, s.at(), 6f, fxColor());
                shake(0.8f, 40);
                soundAt(s.at(), SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.7f);
            }
            case SPRAY -> {
                for (LivingEntity e : caught) e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1), this);
            }
            case ROAR -> {
                for (LivingEntity e : caught) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), this);
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), this);
                }
                WorldBossFx.wave(level, s.at(), 13, 2.2f, 10, fxColor());
            }
            default -> {
            }
        }
    }

    // ================================================================ birth, phases, death

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t == 0) {
            sound(SoundEvents.TURTLE_EGG_CRACK, 6f, 0.4f);
            sound(SoundEvents.ENDER_DRAGON_GROWL, 5f, 1.5f);
        }
        if (t == 30) sound(SoundEvents.PHANTOM_FLAP, 5f, 0.4f);
        if (t == 55) {
            sound(SoundEvents.RAVAGER_ROAR, 6f, 0.8f);
            shake(1.0f, 60);
            WorldBossFx.quake(level, ground(position()), 8f, fxColor());
        }
    }

    @Override
    protected void onPhase(ServerLevel level, int phase) {
        sound(SoundEvents.RAVAGER_ROAR, 6f, phase == 3 ? 0.7f : 0.85f);
        level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + getBbHeight() * 0.7, getZ(), 40, 1, 1, 1, 0.2);
        if (phase == 3) {
            var speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && speed.getModifier(BeyondSolo.id("king_frenzy")) == null) {
                speed.addPermanentModifier(new AttributeModifier(BeyondSolo.id("king_frenzy"), 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            WorldBossFx.quake(level, ground(position()), 10f, fxColor());
        }
    }

    @Override
    protected void deathTick(ServerLevel level, int t) {
        super.deathTick(level, t);
        if (t == 1) sound(SoundEvents.ENDER_DRAGON_GROWL, 6f, 1.0f);
        if (t == 50) sound(SoundEvents.RAVAGER_DEATH, 5f, 0.8f);
    }
}
