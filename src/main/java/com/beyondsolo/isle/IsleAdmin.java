package com.beyondsolo.isle;

import com.beyondsolo.network.WorldBossPayload;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * What an operator ({@code /solo isle}) and the game tests do to the Halcyon raids: call one, land hunters on the island
 * at once, move a raid on to any phase, win or lose it, build the island again, and look around it. Refusals are the
 * raid's own ({@code isle.beyondsolo.refuse.*}).
 */
public final class IsleAdmin {
    /** The phases a landed raid can be moved on to. */
    public static final List<String> PHASES = List.of("beach", "nests", "hive", "queen", "king");

    /** Calls a raid for these hunters (any rank; everyone the Association would call when null). */
    public static IsleRaids.Outcome call(MinecraftServer server, @Nullable Collection<ServerPlayer> only) {
        return IsleRaids.call(server, true, only);
    }

    /**
     * Lands these hunters on the island now: a raid is called for them if none is on, they all answer it, and the boats
     * leave at once (with whoever else had answered).
     */
    public static IsleRaids.Outcome land(MinecraftServer server, Collection<ServerPlayer> who) {
        ServerLevel isle = IsleWorld.level(server);
        if (isle == null) return IsleRaids.Outcome.no("no_world");
        IsleData data = IsleData.get(server);
        if (data.raid == null) {
            IsleRaids.Outcome called = IsleRaids.call(server, true, who);
            if (!called.ok()) return called;
        }
        IsleRaid r = data.raid;
        if (r == null || r.phase != IsleRaid.Phase.MUSTER) return IsleRaids.Outcome.no("under_way");
        for (ServerPlayer p : who) {
            if (r.joined.contains(p.getUUID())) continue;
            r.called.add(p.getUUID());
            IsleRaids.join(p);
        }
        if (r.joined.isEmpty()) return IsleRaids.Outcome.no("nobody");
        IsleRaids.launch(server, isle, data, r, IsleRaids.now(server));
        return r.phase.landed() ? IsleRaids.Outcome.yes("landed", r.joined.size() - r.left.size()) : IsleRaids.Outcome.no("nobody");
    }

    /** The muster ends now: whoever answered lands. */
    public static IsleRaids.Outcome launch(MinecraftServer server) {
        ServerLevel isle = IsleWorld.level(server);
        if (isle == null) return IsleRaids.Outcome.no("no_world");
        IsleData data = IsleData.get(server);
        IsleRaid r = data.raid;
        if (r == null) return IsleRaids.Outcome.no("no_call");
        if (r.phase != IsleRaid.Phase.MUSTER) return IsleRaids.Outcome.no("too_late");
        if (r.joined.isEmpty()) return IsleRaids.Outcome.no("nobody");
        IsleRaids.launch(server, isle, data, r, IsleRaids.now(server));
        return r.phase.landed() ? IsleRaids.Outcome.yes("landed", r.joined.size() - r.left.size()) : IsleRaids.Outcome.no("nobody");
    }

    /**
     * Moves the raid that has landed on to a phase: {@code beach} (the waves come again), {@code nests} (the push
     * inland), {@code hive} (every nest burns: the hive opens), {@code queen} (as {@code hive}, and {@code to} goes down
     * to her, which wakes her) and {@code king} (the queen is gone and the king is born at once; {@code to} goes down).
     */
    public static IsleRaids.Outcome phase(MinecraftServer server, String phase, @Nullable ServerPlayer to) {
        ServerLevel isle = IsleWorld.level(server);
        if (isle == null) return IsleRaids.Outcome.no("no_world");
        IsleData data = IsleData.get(server);
        IsleRaid r = data.raid;
        if (r == null || !r.phase.landed()) return IsleRaids.Outcome.no("not_landed");
        long now = IsleRaids.now(server);
        IsleWorld.Layout l = IsleRaids.layout(isle, r);
        switch (phase) {
            case "beach" -> {
                if (r.phase.ordinal() > IsleRaid.Phase.NESTS.ordinal()) return IsleRaids.Outcome.no("past", phase);
                r.phase = IsleRaid.Phase.BEACH;
                r.phaseAt = now;
                r.wave = 0;
                r.waveAt = now;
            }
            case "nests" -> {
                if (r.phase.ordinal() > IsleRaid.Phase.NESTS.ordinal()) return IsleRaids.Outcome.no("past", phase);
                r.phase = IsleRaid.Phase.NESTS;
                r.phaseAt = now;
            }
            case "hive", "queen" -> {
                if (r.phase.ordinal() > IsleRaid.Phase.QUEEN.ordinal()) return IsleRaids.Outcome.no("past", phase);
                burnAll(server, isle, r);
                if (phase.equals("queen")) {
                    down(isle, l, r, to);
                    if (r.phase == IsleRaid.Phase.HIVE) {
                        r.phase = IsleRaid.Phase.QUEEN;
                        r.phaseAt = now;
                    }
                }
            }
            case "king" -> {
                burnAll(server, isle, r);
                if (r.phase != IsleRaid.Phase.KING) {
                    if (r.queen != null && isle.getEntity(r.queen) instanceof ColossalBoss q) q.discard();
                    r.phase = IsleRaid.Phase.KING;
                    r.phaseAt = now;
                    r.queenFellAt = now - IsleRaids.KING_DELAY;
                    r.king = null;
                }
                down(isle, l, r, to);
            }
            default -> {
                return IsleRaids.Outcome.no("unknown_phase", phase, String.join(", ", PHASES));
            }
        }
        data.setDirty();
        return IsleRaids.Outcome.yes("phase", Component.translatable(IsleRaids.KEY + "phase." + r.phase.id()));
    }

    /** Every nest still standing burns, their hearts with them (the last opens the hive). */
    private static void burnAll(MinecraftServer server, ServerLevel isle, IsleRaid r) {
        IsleWorld.Layout l = IsleRaids.layout(isle, r);
        for (int i = 0; i < r.nests(); i++) {
            if (r.burned(i)) continue;
            IsleRaids.nestBurned(isle, r.id, i, null);
        }
        for (Entity e : isle.getEntities((Entity) null, IsleRaids.bounds(l), e -> e instanceof NestHeartEntity)) e.discard();
        if (r.phase.ordinal() < IsleRaid.Phase.HIVE.ordinal()) IsleRaids.openHive(server, isle, l, r, IsleRaids.now(server));
    }

    /** A hunter of the raid on the island goes down to the queen's chamber. */
    private static void down(ServerLevel isle, IsleWorld.Layout l, IsleRaid r, @Nullable ServerPlayer p) {
        if (p == null || !r.fighting(p.getUUID())) return;
        BlockPos a = l.arrival();
        IsleRaids.place(p, isle, new Vec3(a.getX() + 0.5, a.getY() + 1, a.getZ() + 0.5), 180f);
    }

    /** The island is retaken now: everyone who stayed is paid, as if the king had fallen. */
    public static IsleRaids.Outcome win(MinecraftServer server) {
        ServerLevel isle = IsleWorld.level(server);
        if (isle == null) return IsleRaids.Outcome.no("no_world");
        IsleData data = IsleData.get(server);
        IsleRaid r = data.raid;
        if (r == null || !r.phase.landed()) return IsleRaids.Outcome.no("not_landed");
        long now = IsleRaids.now(server);
        if (r.kingFellAt < 0) r.kingFellAt = now;
        IsleRaids.clear(server, isle, data, r, now);
        // the royalty still standing go with the swarm
        for (UUID id : new UUID[]{r.queen, r.king}) {
            if (id != null && isle.getEntity(id) instanceof ColossalBoss b && b.isAlive()) b.discard();
        }
        WorldBossPayload gone = new WorldBossPayload(WorldBossPayload.GONE, WorldBossKind.KING.ordinal(), IsleRaids.eventKey(r), -1, BlockPos.ZERO, 1, 0, 0);
        for (ServerPlayer p : OnlinePlayers.all(server)) PacketDistributor.sendToPlayer(p, gone);
        return IsleRaids.Outcome.yes("won");
    }

    /** The raid is lost now (called off): its quests fail and the hunters are taken home. */
    public static IsleRaids.Outcome fail(MinecraftServer server) {
        ServerLevel isle = IsleWorld.level(server);
        if (isle == null) return IsleRaids.Outcome.no("no_world");
        IsleData data = IsleData.get(server);
        IsleRaid r = data.raid;
        if (r == null || !r.phase.landed()) return IsleRaids.Outcome.no("not_landed");
        IsleRaids.fail(server, isle, data, r, IsleRaid.Lost.CALLED_OFF);
        return IsleRaids.Outcome.yes("failed");
    }

    /** Ends whatever raid is on at once: no pay, everyone home. */
    public static boolean stop(MinecraftServer server) {
        return IsleRaids.stop(server);
    }

    /** The island is built again from nothing (over the next while), unless a raid is on it. */
    public static IsleRaids.Outcome build(MinecraftServer server) {
        ServerLevel isle = IsleWorld.level(server);
        if (isle == null) return IsleRaids.Outcome.no("no_world");
        IsleData data = IsleData.get(server);
        if (data.raid != null && data.raid.phase != IsleRaid.Phase.MUSTER) return IsleRaids.Outcome.no("under_way");
        data.forgetIsland();
        IsleRaids.rebuild(isle, data.raid != null ? data.raid.seed : IsleRaids.isleSeed(server), IsleConfig.nests());
        return IsleRaids.Outcome.yes("building");
    }

    /** Takes a hunter to the island's camp to look around (they go back with {@code /isle leave}). */
    public static IsleRaids.Outcome visit(ServerPlayer p) {
        ServerLevel isle = IsleWorld.level(p.server);
        if (isle == null) return IsleRaids.Outcome.no("no_world");
        IsleData data = IsleData.get(p.server);
        long seed = data.raid != null ? data.raid.seed : IsleRaids.isleSeed(p.server);
        int nests = data.raid != null ? data.raid.nests() : IsleConfig.nests();
        IsleWorld.Layout l = IsleRaids.layout(isle, seed, nests);
        if (!data.built(seed, nests)) IsleRaids.buildNow(isle, data, l);
        IsleRecord rec = IsleRaids.record(p);
        if (!IsleWorld.onIsle(p.level(), p.blockPosition())) rec.away(rec.raid != null ? rec.raid : new UUID(0, 0), p.level().dimension(), p.position(), p.getYRot());
        BlockPos c = l.circle();
        int y = IsleRaids.surface(isle, c.getX(), c.getZ(), c.getY() + 6);
        IsleRaids.place(p, isle, new Vec3(c.getX() + 0.5, y, c.getZ() + 0.5), l.campYaw());
        return IsleRaids.Outcome.yes("visit");
    }

    /** The Association's next call comes in that many minutes. */
    public static void next(MinecraftServer server, int minutes) {
        IsleData data = IsleData.get(server);
        data.nextCallAt = IsleRaids.now(server) + minutes * 1200L;
        data.setDirty();
    }

    /** A hunter's record of the raids starts again (where they would be sent home from the island is kept). */
    public static void resetRecord(ServerPlayer p) {
        IsleRecord rec = IsleRaids.record(p);
        UUID raid = rec.raid;
        var dim = rec.homeDim;
        Vec3 home = rec.home;
        float yaw = rec.homeYaw;
        rec.reset();
        if (raid != null && dim != null && home != null) rec.away(raid, dim, home, yaw);
    }

    /** A hunter's record. */
    public static IsleRecord record(ServerPlayer p) {
        return IsleRaids.record(p);
    }

    /** How the raid stands, as {@code /isle} says it to this hunter, and the operator's details after. */
    public static List<Component> status(MinecraftServer server, ServerPlayer p) {
        List<Component> out = new ArrayList<>(IsleCommand.lines(p));
        IsleData data = IsleData.get(server);
        IsleRaid r = data.raid;
        long now = IsleRaids.now(server);
        Component next = data.nextCallAt() < 0 ? Component.translatable(IsleRaids.KEY + "admin.unplanned")
                : Component.literal(IsleCommand.minutes(data.nextCallAt() - now) + " min");
        Component built = Component.translatable(IsleRaids.KEY + (IsleRaids.isBuilding() ? "admin.building" : data.builtVersion > 0 ? "admin.built" : "admin.unbuilt"));
        out.add(IsleCommand.tag().append(Component.translatable(IsleRaids.KEY + "admin.status", next, built, data.called(), data.won())));
        if (r != null && r.phase.landed()) {
            out.add(IsleCommand.tag().append(Component.translatable(IsleRaids.KEY + "admin.raid", r.swarm.size(), r.swarmSlain, r.allies.size(),
                    r.wave, IsleConfig.beachWaves(), r.blowsNeeded)));
        }
        return out;
    }

    private IsleAdmin() {}
}
