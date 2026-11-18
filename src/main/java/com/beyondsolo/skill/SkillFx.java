package com.beyondsolo.skill;

import com.beyondsolo.network.SkillCastPayload;
import com.beyondsolo.network.SkillFxPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends skill effects to everyone who can see the entity (and to the entity itself if it is a player). */
public final class SkillFx {
    public static void play(Entity entity, SkillFxPayload.Kind kind, int color, Vec3 at, float size, int duration) {
        if (entity.level().isClientSide) return;
        note(kind, duration);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new SkillFxPayload(entity.getId(), kind, color & 0xFFFFFF,
                (float) at.x, (float) at.y, (float) at.z, size, duration));
    }

    public static void play(Entity entity, SkillFxPayload.Kind kind, int color, float size) {
        play(entity, kind, color, entity.position(), size, 0);
    }

    // ---- the caster's body: while a skill's action runs, the first effect it shows is what the caster acts out ----

    private static boolean casting;
    private static String look = "";
    private static int variant;

    /** A skill's action is about to run (server thread). */
    static void beginCast() {
        casting = true;
        look = "";
        variant = 0;
    }

    /** It has run, whether it went off or not. */
    static void endCast() {
        casting = false;
    }

    /** An effect is going out. The first one of a running cast is the one the caster's body acts out. */
    public static void note(SkillFxPayload.Kind kind, int duration) {
        // the element a struck creature now carries, and the reactions it sets off, are the blow's doing, not the cast's
        if (kind == SkillFxPayload.Kind.ELEMENT || kind == SkillFxPayload.Kind.REACTION) return;
        note(kind.name(), duration);
    }

    /** {@link #note(SkillFxPayload.Kind, int)} for the rune skills' effects ({@code rune:} and the kind's name). */
    public static void note(String name, int duration) {
        if (!casting || !look.isEmpty()) return;
        look = name;
        variant = duration;
    }

    /** The cast went off: everyone who sees the caster sees them act it out. */
    static void gesture(ServerPlayer player, Skill skill) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SkillCastPayload(player.getId(), skill.path(), look, variant));
        look = "";
    }

    private SkillFx() {}
}
