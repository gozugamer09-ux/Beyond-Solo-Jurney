package com.beyondsolo.party;

import com.beyondsolo.awakening.AwakeningData;
import com.beyondsolo.awakening.AwakeningService;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.network.PartyEventPayload;
import com.beyondsolo.network.PartySyncPayload;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.util.ParticleGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regrouping: a party member travels to another member's side, wherever in the world they are. They stand still for
 * {@link #CHANNEL_TICKS} while threads of light gather round them; stepping away or being hurt breaks it. Both have to
 * be out in a world (the overworld, the Nether, the End): nobody regroups into or out of a gate, a trial, the Citadel or
 * the Penalty Zone. Then it waits {@code party.regroupCooldownMinutes} (kept on the hunter, through deaths and logouts).
 * This is what brings friends who started far apart together; a gate is then walked into side by side.
 */
public final class Regroup {
    /** Ticks a hunter stands still before they go. */
    public static final int CHANNEL_TICKS = 100;
    /** How far (blocks) they may drift while gathering before it breaks. */
    static final double DRIFT = 0.75;

    private static final class Channel {
        final UUID target;
        final ResourceKey<Level> dim;
        final Vec3 at;
        final long started;
        float health;

        Channel(UUID target, ResourceKey<Level> dim, Vec3 at, long started, float health) {
            this.target = target;
            this.dim = dim;
            this.at = at;
            this.started = started;
            this.health = health;
        }
    }

    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

    /** True while a hunter is gathering to go. */
    public static boolean channeling(Entity player) {
        return CHANNELS.containsKey(player.getUUID());
    }

    /** Ticks until a hunter can regroup again: 0 ready, -1 when regrouping is off. */
    public static long readyIn(ServerPlayer player) {
        if (PartyConfig.regroupCooldownTicks() < 0) return -1;
        long readyAt = player.getData(PartyModule.REGROUP_READY.get());
        return Math.max(0, readyAt - now(player.server));
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** Starts gathering a hunter to go to {@code target}'s side. */
    public static Parties.Outcome start(ServerPlayer player, UUID target) {
        MinecraftServer server = player.server;
        ServerPlayer to = OnlinePlayers.get(server, target);
        Parties.Outcome no = refusal(player, target, to, true);
        if (no != null) return no;
        CHANNELS.put(player.getUUID(), new Channel(target, player.level().dimension(), player.position(), now(server), player.getHealth()));
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.6f);
        PacketDistributor.sendToPlayer(player, new PartyEventPayload(PartyEventPayload.REGROUP_START, CHANNEL_TICKS, Component.literal(to.getGameProfile().getName())));
        return new Parties.Outcome(true, Component.translatable("party.beyondsolo.regroup.started", Component.literal(to.getGameProfile().getName())));
    }

    /**
     * Why a hunter can't go to {@code target} now, or null if they can. {@code fresh}: starting (the clock and the
     * distance count); otherwise finishing one already gathered.
     */
    @Nullable
    static Parties.Outcome refusal(ServerPlayer player, UUID targetId, @Nullable ServerPlayer target, boolean fresh) {
        MinecraftServer server = player.server;
        if (PartyConfig.regroupCooldownTicks() < 0) return Parties.Outcome.no("regroup.off");
        Party party = Parties.of(server, player.getUUID());
        if (party == null) return Parties.Outcome.no("none");
        if (player.getUUID().equals(targetId)) return Parties.Outcome.no("regroup.self");
        if (!party.has(targetId)) return Parties.Outcome.no("not_member");
        if (target == null) return Parties.Outcome.no("regroup.offline", Component.literal(party.name(targetId)));
        if (fresh && channeling(player)) return Parties.Outcome.no("regroup.already");
        long wait = readyIn(player);
        if (fresh && wait > 0) return Parties.Outcome.no("regroup.wait", clock(wait));
        if (!player.isAlive() || player.isSpectator()) return Parties.Outcome.no("regroup.busy");
        if (busy(player) || !PartyConfig.openWorld(player.level())) return Parties.Outcome.no("regroup.here");
        Component name = Component.literal(target.getGameProfile().getName());
        if (!target.isAlive() || target.isSpectator() || busy(target) || !PartyConfig.openWorld(target.level())) {
            Parties.Place where = Parties.place(target);
            Component place = Component.translatable("ui.beyondsolo.party.place." + placeKey(where.kind()));
            return Parties.Outcome.no("regroup.there", name, place);
        }
        if (fresh && player.level() == target.level()) {
            double min = PartyConfig.REGROUP_MIN_DISTANCE.get();
            if (player.distanceToSqr(target) < min * min) return Parties.Outcome.no("regroup.near", name);
        }
        return null;
    }

    /** Somewhere they can't be taken from: a gate crossing, a trial, the awakening, an evaluation, the Penalty Zone. */
    static boolean busy(ServerPlayer p) {
        if (GateService.inTransit(p) || AwakeningService.inSequence(p)) return true;
        AwakeningData.Stage stage = AwakeningService.stage(p);
        if (stage == AwakeningData.Stage.CALLED || stage == AwakeningData.Stage.TRIAL || stage == AwakeningData.Stage.AWAKENING) return true;
        if (com.beyondsolo.evaluation.EvaluationManager.isBeingEvaluated(p)) return true;
        if (com.beyondsolo.job.JobTrial.inTrial(p) || com.beyondsolo.monarch.ThroneTrial.inTrial(p) || com.beyondsolo.penalty.PenaltyZone.inZone(p)) return true;
        return com.beyondsolo.redgate.RedGates.occupied(p);
    }

    /** The lang key's last part for a place ({@link PartySyncPayload#WORLD} .. {@link PartySyncPayload#OFFLINE}). */
    public static String placeKey(int kind) {
        return switch (kind) {
            case PartySyncPayload.GATE -> "gate";
            case PartySyncPayload.RED_GATE -> "red_gate";
            case PartySyncPayload.CITADEL -> "citadel";
            case PartySyncPayload.PENALTY -> "penalty";
            case PartySyncPayload.TRIAL -> "trial";
            case PartySyncPayload.JOB_TRIAL -> "job_trial";
            case PartySyncPayload.THRONE -> "throne";
            case PartySyncPayload.OFFLINE -> "offline";
            default -> "world";
        };
    }

    /** "4:05" */
    static Component clock(long ticks) {
        long s = (ticks + 19) / 20;
        return Component.literal(s / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60));
    }

    /** Stops a hunter gathering ({@code tell}: and says why on their screen). */
    public static void cancel(ServerPlayer player, boolean tell) {
        cancel(player, tell ? Component.translatable("party.beyondsolo.regroup.broken") : null);
    }

    private static void cancel(ServerPlayer player, @Nullable Component why) {
        if (CHANNELS.remove(player.getUUID()) == null) return;
        if (why == null || player.connection == null) return;
        PacketDistributor.sendToPlayer(player, new PartyEventPayload(PartyEventPayload.REGROUP_CANCEL, 0, why));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.5f);
    }

    static void clear() {
        CHANNELS.clear();
    }

    /** Every tick: the gathering goes on, breaks, or ends with them at their party member's side. */
    static void tick(MinecraftServer server) {
        if (CHANNELS.isEmpty()) return;
        long now = now(server);
        for (Map.Entry<UUID, Channel> e : List.copyOf(CHANNELS.entrySet())) {
            ServerPlayer p = OnlinePlayers.get(server, e.getKey());
            Channel ch = e.getValue();
            if (p == null) {
                CHANNELS.remove(e.getKey());
                continue;
            }
            if (!p.level().dimension().equals(ch.dim) || horizontal(p.position(), ch.at) > DRIFT) {
                cancel(p, Component.translatable("party.beyondsolo.regroup.moved"));
                continue;
            }
            if (p.getHealth() < ch.health - 0.01f) {
                cancel(p, Component.translatable("party.beyondsolo.regroup.hurt"));
                continue;
            }
            ch.health = Math.max(ch.health, p.getHealth());
            ServerPlayer to = OnlinePlayers.get(server, ch.target);
            Parties.Outcome no = refusal(p, ch.target, to, false);
            if (no != null) {
                cancel(p, no.message());
                continue;
            }
            long t = now - ch.started;
            if (t % 10 == 0) {
                ServerLevel level = p.serverLevel();
                double a = t * 0.35;
                ParticleGuard.send(level, ParticleTypes.END_ROD, p.getX() + Math.cos(a) * 0.9, p.getY() + 0.2 + (t % 40) / 40.0 * 1.6,
                        p.getZ() + Math.sin(a) * 0.9, 2, 0.05, 0.1, 0.05, 0.01);
                if (t % 20 == 0) level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 0.8f + t / (float) CHANNEL_TICKS);
            }
            if (t >= CHANNEL_TICKS) {
                CHANNELS.remove(e.getKey());
                travel(p, to);
            }
        }
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Takes a hunter to their party member's side now (the end of the gathering; tests use it directly). */
    public static boolean travel(ServerPlayer player, ServerPlayer target) {
        if (refusal(player, target.getUUID(), target, false) != null) return false;
        ServerLevel from = player.serverLevel();
        ServerLevel level = target.serverLevel();
        Vec3 was = player.position();
        Vec3 to = spotNear(level, target, player);
        float yaw = (float) (Mth.atan2(target.getZ() - to.z, target.getX() - to.x) * Mth.RAD_TO_DEG) - 90f;
        if (player.isPassenger()) player.stopRiding();
        player.teleportTo(level, to.x, to.y, to.z, yaw, 0f);
        // fake players (tests) ignore same-level teleports
        if (player.level() == level && player.distanceToSqr(to) > 4) player.moveTo(to.x, to.y, to.z, yaw, 0f);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        long cooldown = PartyConfig.regroupCooldownTicks();
        player.setData(PartyModule.REGROUP_READY.get(), now(player.server) + Math.max(0, cooldown));

        ParticleGuard.send(from, ParticleTypes.REVERSE_PORTAL, was.x, was.y + 1, was.z, 30, 0.3, 0.6, 0.3, 0.02);
        from.playSound(null, BlockPos.containing(was), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.3f);
        ParticleGuard.send(level, ParticleTypes.END_ROD, to.x, to.y + 1, to.z, 24, 0.3, 0.6, 0.3, 0.03);
        level.playSound(null, BlockPos.containing(to), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.2f);
        level.playSound(null, BlockPos.containing(to), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 1.8f);

        PacketDistributor.sendToPlayer(player, new PartyEventPayload(PartyEventPayload.REGROUP_DONE, 0, Component.literal(target.getGameProfile().getName())));
        if (target.connection != null) {
            PacketDistributor.sendToPlayer(target, new PartyEventPayload(PartyEventPayload.REGROUP_JOINED, 0, Component.literal(player.getGameProfile().getName())));
        }
        Parties.sync(player);
        return true;
    }

    /** A spot to stand at, a couple of blocks from the target (to their side or behind), else their own spot. */
    static Vec3 spotNear(ServerLevel level, ServerPlayer target, ServerPlayer traveler) {
        BlockPos base = target.blockPosition();
        float yaw = target.getYRot();
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(yaw + 90 + i * 45);
            int dx = (int) Math.round(-Math.sin(a) * 2), dz = (int) Math.round(Math.cos(a) * 2);
            for (int dy : new int[] {0, 1, -1}) {
                BlockPos p = base.offset(dx, dy, dz);
                if (standable(level, p, traveler)) return Vec3.atBottomCenterOf(p);
            }
        }
        return target.position();
    }

    static boolean standable(ServerLevel level, BlockPos p, ServerPlayer e) {
        if (!level.getWorldBorder().isWithinBounds(p) || !level.isLoaded(p)) return false;
        BlockState below = level.getBlockState(p.below());
        if (!below.isFaceSturdy(level, p.below(), Direction.UP)) return false;
        if (!level.getFluidState(p).isEmpty() || !level.getFluidState(p.above()).isEmpty()) return false;
        if (below.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || below.is(net.minecraft.tags.BlockTags.CAMPFIRES)) return false;
        AABB box = e.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.atBottomCenterOf(p));
        return level.noCollision(e, box);
    }

    private Regroup() {}
}
