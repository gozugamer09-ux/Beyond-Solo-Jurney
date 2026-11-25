package com.beyondsolo.skill;

import com.beyondsolo.network.SkillHitPayload;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a skill's blows feel where they land. Every blow a skill deals ({@link SkillActions#hit}, a sovereign's
 * {@link MonarchPowers#strike}) is weighed here: how much of the creature it took, how big the skill is, whether it
 * felled it, whether it is a storm's later tick on a creature it already struck. The blows one hunter lands in a tick
 * go out together to everyone who sees that hunter ({@link SkillHitPayload}), and their screens answer it: the struck
 * creature flashes white and hangs for a moment, sparks of the skill's element fly off the side that was hit, the hit
 * is heard, and the hunter's own view kicks ({@code client.skill.SkillImpacts}).
 *
 * <p>Nothing here changes what a blow does: the weights only say how hard it should feel.</p>
 */
public final class SkillFeel {
    /** A scratch: a quick spark, no hitstop to speak of. */
    public static final byte LIGHT = 0;
    /** A solid blow: a short hitstop and a flash. */
    public static final byte MEDIUM = 1;
    /** A blow that tears: a longer hitstop, the hunter's view jolts, the screen ripples. */
    public static final byte HEAVY = 2;
    /** An ultimate's blow, a felled boss, half a creature's life in one hit: everything, and the ink frame. */
    public static final byte MASSIVE = 3;

    /** Flag: the blow felled the creature. */
    public static final byte KILL = 1;
    /** Flag: the same skill struck the same creature a moment ago (a storm's tick, a flurry's next cut). */
    public static final byte REPEAT = 2;

    /** A blow from the same skill on the same creature within this many ticks of the last is a repeat. */
    static final int REPEAT_TICKS = 12;
    /** At most this many blows go out in one payload: the heaviest. */
    static final int MAX_BLOWS = 24;

    /** This tick's blows, per hunter and element. */
    private static final Map<Long, Batch> PENDING = new LinkedHashMap<>();
    /** When each hunter last struck each creature, and with what: (hunter, creature) -> {skill, game time}. */
    private static final Map<Long, long[]> LAST = new HashMap<>();

    private static final class Batch {
        final ServerPlayer caster;
        final int element;
        boolean ultimate;
        final List<int[]> blows = new ArrayList<>();

        Batch(ServerPlayer caster, int element) {
            this.caster = caster;
            this.element = element;
        }
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(SkillFeel::payloads);
        // last in the tick, so the blows of what skills scheduled for this tick go out with it
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SkillFeel::flush);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            PENDING.clear();
            LAST.clear();
        });
        SkillSounds.init(modBus);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(SkillHitPayload.TYPE, SkillHitPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.skill.SkillImpacts.onHit(p)));
    }

    /**
     * A skill's blow landed on {@code e}: weigh it and queue it for this tick's payload. {@code healthBefore} is the
     * creature's health before the blow, so what it actually took (after armour and the rest) decides the weight.
     */
    public static void struck(ServerPlayer p, Skill s, SkillElement element, LivingEntity e, float healthBefore) {
        struck(p, s.path(), stature(s), ultimate(s), element, e, healthBefore);
    }

    /**
     * A blow from something other than a class skill (a weapon's signature art), weighed the same way: {@code source}
     * tells its blows apart for the repeats, {@code stature} is how big it is ({@link #stature(Skill)} for a skill).
     */
    public static void struck(ServerPlayer p, String source, byte stature, boolean ultimate, SkillElement element, LivingEntity e,
                              float healthBefore) {
        if (p.level().isClientSide) return;
        long now = p.level().getGameTime();
        long key = ((long) p.getId() << 32) | (e.getId() & 0xFFFFFFFFL);
        long[] last = LAST.get(key);
        int skill = source.hashCode();
        boolean repeat = last != null && last[0] == skill && now >= last[1] && now - last[1] <= REPEAT_TICKS;
        if (LAST.size() > 1024) LAST.values().removeIf(l -> now - l[1] > REPEAT_TICKS || now < l[1]);
        LAST.put(key, new long[]{skill, now});

        float max = Math.max(1, e.getMaxHealth());
        float dealt = Math.max(0, healthBefore - Math.max(0, e.getHealth()));
        boolean killed = !e.isAlive() || e.isDeadOrDying();
        byte weight = weigh(stature, dealt / max, killed, repeat, com.beyondsolo.combat.Statuses.tough(e), ultimate);
        byte flags = (byte) ((killed ? KILL : 0) | (repeat ? REPEAT : 0));

        long batchKey = ((long) p.getId() << 8) | element.ordinal();
        Batch b = PENDING.computeIfAbsent(batchKey, k -> new Batch(p, element.ordinal()));
        if (ultimate) b.ultimate = true;
        for (int[] blow : b.blows) {
            if (blow[0] == e.getId()) {
                // struck twice in one tick (a blast and its shrapnel): one blow, the heavier, felled if either felled it
                blow[1] = Math.max(blow[1], weight);
                int kill = (blow[2] | flags) & KILL;
                int again = blow[2] & flags & REPEAT;
                blow[2] = kill | again;
                return;
            }
        }
        b.blows.add(new int[]{e.getId(), weight, flags});
    }

    /**
     * How big the skill is, apart from what a blow of it did: its grade and how long it waits between casts. A
     * sovereign's power or a long-waiting S-grade skill is {@link #HEAVY}, a mid skill {@link #MEDIUM}, the rest
     * {@link #LIGHT}.
     */
    public static byte stature(Skill s) {
        if (s.sovereign()) return HEAVY;
        int cooldown = s.cooldown(1);
        int grade = s.grade().tier();
        if (cooldown >= 1200 || (grade >= PlayerRank.S.tier() && cooldown >= 600)) return HEAVY;
        if (cooldown >= 400 || grade >= PlayerRank.A.tier()) return MEDIUM;
        return LIGHT;
    }

    /**
     * An ultimate: a sovereign's power, or a skill that waits a minute or more, or an S-grade one that waits half a
     * minute. Its cast cuts in across the screen, its first blows land {@link #MASSIVE} and the screen answers with
     * an ink frame.
     */
    public static boolean ultimate(Skill s) {
        if (!s.isActive()) return false;
        if (s.sovereign()) return true;
        int cooldown = s.cooldown(1);
        return cooldown >= 1200 || (s.grade() == PlayerRank.S && cooldown >= 600);
    }

    /**
     * The weight of one blow: what it took of the creature ({@code fraction} of its most health), raised to the
     * skill's {@code stature}; an ultimate's blow is {@link #MASSIVE}; felling a creature is at least
     * {@link #MEDIUM} (a boss or elite: {@link #MASSIVE}); a repeat stays {@link #LIGHT} unless it bit deep.
     */
    static byte weigh(byte stature, float fraction, boolean killed, boolean repeat, boolean big, boolean ultimate) {
        byte w = fraction >= 0.5f ? MASSIVE : fraction >= 0.25f ? HEAVY : fraction >= 0.1f ? MEDIUM : LIGHT;
        w = (byte) Math.max(w, stature);
        if (ultimate) w = MASSIVE;
        if (killed) return (byte) Math.max(w, big ? MASSIVE : MEDIUM);
        if (repeat) w = (byte) Math.min(w, fraction >= 0.25f ? HEAVY : LIGHT);
        return w;
    }

    private static void flush(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        List<Batch> batches = new ArrayList<>(PENDING.values());
        PENDING.clear();
        for (Batch b : batches) {
            if (b.caster.isRemoved() || b.blows.isEmpty()) continue;
            // the heaviest blows first, so a crowd struck at once still sends what matters
            b.blows.sort((x, y) -> y[1] - x[1]);
            int n = Math.min(MAX_BLOWS, b.blows.size());
            int[] targets = new int[n];
            byte[] weights = new byte[n];
            byte[] flags = new byte[n];
            for (int i = 0; i < n; i++) {
                int[] blow = b.blows.get(i);
                targets[i] = blow[0];
                weights[i] = (byte) blow[1];
                flags[i] = (byte) blow[2];
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(b.caster,
                    new SkillHitPayload(b.caster.getId(), b.element, b.ultimate, targets, weights, flags));
            sent++;
        }
    }

    /**
     * Shows everyone near {@code p} a blow of this weight landing on {@code target}, as if a skill had dealt it, without
     * dealing anything ({@code /solo skill feel}).
     */
    public static void preview(ServerPlayer p, LivingEntity target, SkillElement element, byte weight, boolean killed, boolean ultimate) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new SkillHitPayload(p.getId(), element.ordinal(), ultimate,
                new int[]{target.getId()}, new byte[]{weight}, new byte[]{killed ? KILL : 0}));
    }

    /** Payloads sent since the server started (for the game tests). */
    static int sent;

    /** The weights queued this tick for a hunter's blows on a creature, or -1 (for the game tests). */
    static int pending(ServerPlayer p, LivingEntity e) {
        for (Batch b : PENDING.values()) {
            if (b.caster != p) continue;
            for (int[] blow : b.blows) if (blow[0] == e.getId()) return blow[1] | (blow[2] << 8);
        }
        return -1;
    }

    private SkillFeel() {}
}
