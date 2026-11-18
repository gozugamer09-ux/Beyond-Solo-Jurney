package com.beyondsolo.skill;

import com.beyondsolo.network.SkillCastPayload;
import com.beyondsolo.network.SkillFxPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/** Sends skill effects to everyone who can see the entity (and to the entity itself if it is a player). */
public final class SkillFx {
    public static void play(Entity entity, SkillFxPayload.Kind kind, int color, Vec3 at, float size, int duration) {
        if (entity.level().isClientSide) return;
        note(kind, duration);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new SkillFxPayload(entity.getId(), kind, color & 0xFFFFFF,
                (float) at.x, (float) at.y, (float) at.z, size, duration, elementOf(entity)));
    }

    public static void play(Entity entity, SkillFxPayload.Kind kind, int color, float size) {
        play(entity, kind, color, entity.position(), size, 0);
    }

    // ---- the caster's body: while a skill's action runs, the first effect it shows is what the caster acts out ----

    private static boolean casting;
    private static String look = "";
    private static int variant;
    /** The running cast's element ({@link SkillElement} ordinal), which picks the skin of everything it shows. */
    private static int element = -1;
    /** What each caster cast last, and when: effects that come after the cast (a landing, a burst) take its element. */
    private static final Map<Integer, long[]> RECENT = new HashMap<>();
    private static final int RECENT_TICKS = 200;

    /** A skill's action is about to run (server thread). */
    static void beginCast(ServerPlayer player, Skill skill) {
        casting = true;
        look = "";
        variant = 0;
        element = skill.element().ordinal();
        long now = player.level().getGameTime();
        if (RECENT.size() > 256) RECENT.values().removeIf(e -> now - e[1] > RECENT_TICKS || now < e[1]);
        RECENT.put(player.getId(), new long[]{element, now});
    }

    /** The element of the running cast, or -1 between casts. */
    public static int castElement() {
        return casting ? element : -1;
    }

    /** The element of an effect the entity shows: the running cast's, or that of what it cast a moment ago. */
    static int elementOf(Entity entity) {
        if (casting) return element;
        long[] e = RECENT.get(entity.getId());
        long now = entity.level().getGameTime();
        return e != null && now - e[1] <= RECENT_TICKS && now >= e[1] ? (int) e[0] : -1;
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
