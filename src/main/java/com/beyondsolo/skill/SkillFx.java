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

    /**
     * Shows a passive's effect (a proc: a spark, a dodge, a pact paid). It can go off in the middle of a cast, on a
     * blow the cast dealt, but it is not that cast's own look: the caster acts out the skill, not the proc (a random
     * spark first made Flash Step's look a heal now and then).
     */
    public static void aside(Runnable show) {
        aside++;
        try {
            show.run();
        } finally {
            aside--;
        }
    }

    // ---- the caster's body: while a skill's action runs, the first effect it shows is what the caster acts out ----

    private static boolean casting;
    private static String look = "";
    private static int variant;
    /** How deep in a passive's own effect ({@link #aside}) the server is: those are never a cast's look. */
    private static int aside;
    /** The running cast's element ({@link SkillElement} ordinal), which picks the skin of everything it shows. */
    private static int element = -1;
    /** What each caster cast last, and when: effects that come after the cast (a landing, a burst) take its element. */
    private static final Map<Integer, long[]> RECENT = new HashMap<>();
    private static final int RECENT_TICKS = 200;

    // ---- a skill whose first blow comes later (a stance, a held breath): what it schedules carries the cast along ----

    /** A cast that went off: its caster and skill, the tick, and whether its body still waits for its first blow. */
    private static final class Cast {
        final ServerPlayer player;
        final Skill skill;
        final long at;
        boolean awaiting;

        Cast(ServerPlayer player, Skill skill, long at) {
            this.player = player;
            this.skill = skill;
            this.at = at;
        }
    }

    /** The cast whose action runs now. */
    private static Cast current;
    /** The cast whose scheduled part runs now, or null. */
    private static Cast late;

    /**
     * A task scheduled now ({@code ServerScheduler}) runs as part of the cast that scheduled it, so the first effect
     * a skill shows after its cast is still known as that skill's (see {@link #note(String, int)}).
     */
    static Runnable carry(Runnable task) {
        Cast c = casting ? current : late;
        if (c == null) return task;
        return () -> {
            Cast was = late;
            late = c;
            try {
                task.run();
            } finally {
                late = was;
            }
        };
    }

    /** A skill's action is about to run (server thread). */
    static void beginCast(ServerPlayer player, Skill skill) {
        SkillSounds.enter();
        casting = true;
        current = new Cast(player, skill, player.level().getGameTime());
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
        SkillSounds.exit();
    }

    /** An effect is going out. The first one of a running cast is the one the caster's body acts out. */
    public static void note(SkillFxPayload.Kind kind, int duration) {
        // the element a struck creature now carries, and the reactions it sets off, are the blow's doing, not the cast's
        if (kind == SkillFxPayload.Kind.ELEMENT || kind == SkillFxPayload.Kind.REACTION) return;
        note(kind.name(), duration);
    }

    /** {@link #note(SkillFxPayload.Kind, int)} for the rune skills' effects ({@code rune:} and the kind's name). */
    public static void note(String name, int duration) {
        if (aside > 0) return;
        if (casting) {
            if (!look.isEmpty()) return;
            look = name;
            variant = duration;
            return;
        }
        // a cast that showed nothing shows its first blow now: the caster's body acts that out, now
        Cast c = late;
        if (c == null || !c.awaiting || c.player.isRemoved()) return;
        c.awaiting = false;
        int delay = (int) Math.max(1, c.player.level().getGameTime() - c.at);
        Gestures.remember(c.skill, name, duration, delay);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(c.player, new SkillCastPayload(c.player.getId(), c.skill.path(), name, duration));
    }

    /**
     * The cast went off: everyone who sees the caster sees them act it out. A cast that showed nothing yet waits for
     * its first blow; when that one is known to come soon, the body is only told then.
     */
    static void gesture(ServerPlayer player, Skill skill) {
        Cast c = current;
        current = null;
        if (look.isEmpty() && c != null) {
            c.awaiting = true;
            int delay = Gestures.delay(skill);
            if (delay > 0 && delay <= Gestures.MAX_DELAY) return;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SkillCastPayload(player.getId(), skill.path(), look, variant));
        if (!look.isEmpty()) Gestures.remember(skill, look, variant, 0);
        look = "";
    }

    private SkillFx() {}
}
