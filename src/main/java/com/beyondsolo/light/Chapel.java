package com.beyondsolo.light;

import com.beyondsolo.depths.DepthsBuild;
import com.beyondsolo.depths.HiddenKind;
import com.beyondsolo.depths.HiddenRooms;
import com.beyondsolo.gate.DungeonLayout;
import com.beyondsolo.gate.GateData;
import com.beyondsolo.gate.GateInstance;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.monarch.ThroneTrial;
import com.beyondsolo.network.LightFxPayload;
import com.beyondsolo.skill.RulerPowers;
import com.beyondsolo.skill.SkillParticles;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The chapel of the Envoy of Dawn: the light's secret way into the System. Every awakening temple opened since the
 * light came hides it off the antechamber, past the tenets, behind a wall that passes for the temple's rock
 * ({@code depths.HiddenKind#CHAPEL}). It is meant to be found: golden light seeps out of the wall, something hums
 * behind it when a hunter comes near, a sign by it says the light waits behind the stone, and the wall gives to three
 * knocks like any hidden wall. Behind it the Envoy waits over the altar with the covenant ({@link Covenant}).
 *
 * <p>Nothing here is saved but the gate's own record of its wall: the hunters already told about the hum or the
 * chapel are only remembered while the server runs.</p>
 */
public final class Chapel {
    /** How close to the wall a hunter has to come to hear the light behind it. */
    public static final double HUM_RANGE = 14;
    private static final Map<UUID, Set<UUID>> HEARD = new HashMap<>();
    private static final Map<UUID, Set<UUID>> ENTERED = new HashMap<>();

    /** Every half second for a temple with someone in it (from {@code depths.HiddenRooms#tick}). */
    public static void tick(ServerLevel level, GateInstance g, DungeonLayout.Room room, List<ServerPlayer> players) {
        if (!g.depths().hiddenOpen()) {
            seep(level, g, players);
            return;
        }
        EnvoyEntity envoy = envoyOf(level, g);
        if (envoy == null) envoy = summon(level, g);
        Set<UUID> entered = ENTERED.computeIfAbsent(g.id(), k -> new HashSet<>());
        for (ServerPlayer p : players) {
            if (!HiddenRooms.inside(g, room, p.position(), 1) || !entered.add(p.getUUID())) continue;
            // the first step in: the light's words, and the Envoy turns to them
            PacketDistributor.sendToPlayer(p, new LightFxPayload(LightFxPayload.ENTER, envoy == null ? -1 : envoy.getId(), RulerPowers.DAWN_GOLD, 0, 140));
            if (envoy != null) envoy.speak();
            level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5f, 1.2f);
        }
    }

    /** Before the wall is down: golden light seeps out of it, and the hunters who come near hear the light behind it. */
    private static void seep(ServerLevel level, GateInstance g, List<ServerPlayer> players) {
        DepthsBuild.Mouth m = DepthsBuild.sealMouth(g);
        if (m == null) return;
        Vec3 at = m.center();
        boolean near = false;
        Set<UUID> heard = HEARD.computeIfAbsent(g.id(), k -> new HashSet<>());
        for (ServerPlayer p : players) {
            if (p.position().distanceTo(at) > HUM_RANGE) continue;
            near = true;
            if (heard.add(p.getUUID()) && !SystemAPI.hasSystem(p)) {
                PacketDistributor.sendToPlayer(p, new LightFxPayload(LightFxPayload.HUM, -1, RulerPowers.DAWN_GOLD, 0, 120));
            }
        }
        if (!near) return;
        // the light behind the stone: motes through the cracks, now and then a far bell
        float r = m.radius() + 0.4f;
        SkillParticles.send(level, ThroneTrial.dust(RulerPowers.DAWN_GOLD, 1.1f), at.x, at.y, at.z, 6, r * 0.6, r * 0.6, r * 0.6, 0.002);
        Vec3 out = at.subtract(m.dir().scale(0.9));
        SkillParticles.send(level, ParticleTypes.END_ROD, out.x, out.y, out.z, 1, r * 0.5, r * 0.5, r * 0.5, 0.004);
        if (level.getGameTime() % 80 < 10) {
            level.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.5f, 0.7f);
            level.playSound(null, BlockPos.containing(at), SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.6f, 1.6f);
        }
    }

    /** The wall is down (from {@code depths.HiddenRooms#open}): the light pours out and the Envoy is there, over the altar. */
    public static void opened(ServerLevel level, GateInstance g, List<ServerPlayer> party) {
        EnvoyEntity envoy = envoyOf(level, g);
        if (envoy == null) envoy = summon(level, g);
        DepthsBuild.Mouth m = DepthsBuild.sealMouth(g);
        Vec3 at = m != null ? m.center() : envoy != null ? envoy.position() : Vec3.ZERO;
        SkillParticles.send(level, ParticleTypes.END_ROD, at.x, at.y, at.z, 60, 1.2, 1.2, 1.2, 0.12);
        SkillParticles.send(level, ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, BlockPos.containing(at), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2f, 0.8f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.6f, 1.3f);
        if (envoy != null) {
            for (ServerPlayer p : party) PacketDistributor.sendToPlayer(p, new LightFxPayload(LightFxPayload.HUM, envoy.getId(), RulerPowers.DAWN_GOLD, 1, 60));
        }
    }

    /** The Envoy over the altar, made now. Null if the chapel isn't built. */
    static @Nullable EnvoyEntity summon(ServerLevel level, GateInstance g) {
        if (g.layout().hidden() == null || !g.built()) return null;
        DepthsBuild.Door altar = DepthsBuild.chapelAltar(level, g);
        EnvoyEntity envoy = LightModule.ENVOY.get().create(level);
        if (envoy == null) return null;
        float yaw = altar.facing().toYRot();
        BlockPos p = altar.pos();
        envoy.moveTo(p.getX() + 0.5, p.getY() + 0.9, p.getZ() + 0.5, yaw, 0);
        envoy.setYHeadRot(yaw);
        envoy.setYBodyRot(yaw);
        envoy.setMode(EnvoyEntity.CHAPEL);
        envoy.setGate(g.id());
        level.addFreshEntity(envoy);
        return envoy;
    }

    /** The Envoy in a temple's chapel, if it is there. */
    public static @Nullable EnvoyEntity envoyOf(ServerLevel level, GateInstance g) {
        DungeonLayout.Room room = g.layout().hidden();
        if (room == null) return null;
        BlockPos c = g.world(room.center());
        AABB box = new AABB(c).inflate(room.rx() + 2, room.ry() + 4, room.rz() + 2);
        List<EnvoyEntity> found = level.getEntitiesOfClass(EnvoyEntity.class, box, e -> e.isAlive() && !e.leaving() && g.id().equals(e.gate()));
        return found.isEmpty() ? null : found.get(0);
    }

    /** Whether the temple an Envoy waits in still stands (it goes when its temple closes). */
    static boolean stillStands(ServerLevel level, EnvoyEntity envoy) {
        if (envoy.gate() == null) return false;
        GateInstance g = GateData.get(level.getServer()).get(envoy.gate());
        return g != null && g.state() == GateInstance.State.OPEN && GateService.isDungeon(level);
    }

    /** Whether a temple hides the chapel ({@code GateInstance#chapel}) and it is built into its rock. */
    public static boolean has(GateInstance g) {
        return g.isTrial() && g.depths().hidden(g) == HiddenKind.CHAPEL && g.layout().hidden() != null;
    }

    static void forget(UUID player) {
        for (Set<UUID> s : HEARD.values()) s.remove(player);
    }

    static void clear() {
        HEARD.clear();
        ENTERED.clear();
    }

    private Chapel() {}
}
