package com.beyondsolo.light;

import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.network.LightActionPayload;
import com.beyondsolo.network.LightFxPayload;
import com.beyondsolo.skill.RulerPowers;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Envoy of Dawn's covenant: what it offers a hunter who finds its chapel ({@link Chapel}) before the Arbiter takes
 * them. Spoken to, it says who it is and what the light asks; the hunter answers on their screen
 * ({@code client.light.CovenantScreen}):
 * <ul>
 *   <li><b>Swear to the light</b>: the Envoy lays its hand on them and the System binds to them in the light instead of
 *   the Arbiter's dark ({@code awakening.AwakeningService#beginLight}): they wake sworn ({@link LightAPI#awoken}).</li>
 *   <li><b>Turn away</b>: the Envoy lets them go; the Arbiter waits. They can still come back to it while the temple
 *   stands and they haven't woken.</li>
 * </ul>
 */
public final class Covenant {
    /** How near the Envoy a hunter has to stand for their answer to count. */
    static final double REACH = 10;

    /** A hunter spoke to the Envoy in its chapel. */
    public static void speak(ServerPlayer p, EnvoyEntity envoy) {
        if (AwakeningService.inSequence(p)) return;
        if (SystemAPI.hasSystem(p)) {
            // the System is already theirs: the light has nothing more to offer them here
            p.displayClientMessage(Component.translatable(LightAPI.sworn(p) ? "light.beyondsolo.envoy.sworn" : "light.beyondsolo.envoy.bound")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            envoy.speak();
            return;
        }
        envoy.speak();
        p.serverLevel().playSound(null, envoy.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 1f, 1.2f);
        PacketDistributor.sendToPlayer(p, new LightFxPayload(LightFxPayload.COVENANT, envoy.getId(), RulerPowers.DAWN_GOLD,
                LightAPI.get(p).refused() ? 1 : 0, 0));
    }

    /** The hunter answered ({@link LightActionPayload#COVENANT}): 1 sworn, 0 turned away, 2 they only walked off. */
    public static void answer(ServerPlayer p, int value) {
        if (value == 2) return;
        if (AwakeningService.inSequence(p) || SystemAPI.hasSystem(p)) return;
        GateInstance g = AwakeningService.trialAt(p);
        EnvoyEntity envoy = g == null ? null : near(p, g);
        if (envoy == null) return;
        if (value == 1) swear(p, g, envoy);
        else turnAway(p, envoy);
    }

    /** The covenant taken: the Envoy's hand, and the System binds to them in the light. */
    static void swear(ServerPlayer p, GateInstance g, EnvoyEntity envoy) {
        LightAPI.swear(p);
        envoy.bless();
        var level = p.serverLevel();
        level.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.6f, 1.2f);
        level.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 2f, 0.7f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 50, 0.5, 1, 0.5, 0.08);
        AwakeningService.beginLight(p, g);
    }

    /** Turned away: the Envoy lowers its hand. The way back to it stays open while the temple stands. */
    static void turnAway(ServerPlayer p, EnvoyEntity envoy) {
        LightAPI.refused(p);
        envoy.speak();
        p.displayClientMessage(Component.translatable("light.beyondsolo.envoy.turned_away").withStyle(net.minecraft.ChatFormatting.GOLD), true);
        p.serverLevel().playSound(null, envoy.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1f, 0.6f);
    }

    /** The Envoy of this temple's chapel, if the hunter stands near it. */
    static @Nullable EnvoyEntity near(ServerPlayer p, GateInstance g) {
        List<EnvoyEntity> found = p.serverLevel().getEntitiesOfClass(EnvoyEntity.class, p.getBoundingBox().inflate(REACH),
                e -> e.isAlive() && !e.leaving() && e.mode() == EnvoyEntity.CHAPEL && g.id().equals(e.gate()));
        return found.isEmpty() ? null : found.get(0);
    }

    private Covenant() {}
}
