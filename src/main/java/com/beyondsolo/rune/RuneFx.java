package com.beyondsolo.rune;

import com.beyondsolo.network.RuneFxPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends rune effects to the clients that can see them (drawn by {@code client.rune.RuneWorldFx}). */
public final class RuneFx {
    /** On an entity: everyone tracking it, and the entity itself if it is a player. */
    public static void play(Entity on, RuneFxPayload.Kind kind, int color, Vec3 at, Vec3 dir, float size, int duration, int extra) {
        if (on.level().isClientSide) return;
        com.beyondsolo.skill.SkillFx.note("rune:" + kind.name(), duration);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(on, new RuneFxPayload(on.getId(), kind, color & 0xFFFFFF,
                (float) at.x, (float) at.y, (float) at.z, (float) dir.x, (float) dir.y, (float) dir.z, size, duration, extra));
    }

    public static void play(Entity on, RuneFxPayload.Kind kind, int color, Vec3 at, float size, int duration) {
        play(on, kind, color, at, Vec3.ZERO, size, duration, 0);
    }

    /** At a point: every player within 96 blocks. */
    public static void at(ServerLevel level, RuneFxPayload.Kind kind, int color, Vec3 at, float size, int duration) {
        at(level, kind, color, at, size, duration, 0);
    }

    public static void at(ServerLevel level, RuneFxPayload.Kind kind, int color, Vec3 at, float size, int duration, int extra) {
        com.beyondsolo.skill.SkillFx.note("rune:" + kind.name(), duration);
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 96,
                new RuneFxPayload(-1, kind, color & 0xFFFFFF, (float) at.x, (float) at.y, (float) at.z, 0, 0, 0, size, duration, extra));
    }

    /** Only to this player (refusals). */
    public static void self(ServerPlayer player, RuneFxPayload.Kind kind, int color, int extra) {
        PacketDistributor.sendToPlayer(player, new RuneFxPayload(player.getId(), kind, color & 0xFFFFFF,
                (float) player.getX(), (float) player.getY(), (float) player.getZ(), 0, 0, 0, 1, 0, extra));
    }

    private RuneFx() {}
}
