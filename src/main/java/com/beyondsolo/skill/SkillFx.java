package com.beyondsolo.skill;

import com.beyondsolo.network.SkillFxPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends skill effects to everyone who can see the entity (and to the entity itself if it is a player). */
public final class SkillFx {
    public static void play(Entity entity, SkillFxPayload.Kind kind, int color, Vec3 at, float size, int duration) {
        if (entity.level().isClientSide) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new SkillFxPayload(entity.getId(), kind, color & 0xFFFFFF,
                (float) at.x, (float) at.y, (float) at.z, size, duration));
    }

    public static void play(Entity entity, SkillFxPayload.Kind kind, int color, float size) {
        play(entity, kind, color, entity.position(), size, 0);
    }

    private SkillFx() {}
}
