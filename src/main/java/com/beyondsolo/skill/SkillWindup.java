package com.beyondsolo.skill;

import com.beyondsolo.network.SkillWindupPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The beat before a class skill goes off. A hunter who presses a skill on the bar first winds up for it: the body draws
 * back into the pose its gesture strikes from and the power gathers ({@code client.skill.SkillCharge}); then the skill
 * goes off ({@link SkillAPI#cast}) on the tick the gesture's blow lands. A light skill's wind-up is a fifth of a second,
 * a heavy one's half a second, an ultimate's eight tenths, with its cut-in ({@link Gestures#windup}). What has to be
 * instant stays instant: dashes, vanishes, leaps, guards, the shared skills and the sovereigns' powers.
 *
 * <p>Nothing is spent until the skill goes off, and a skill that finds nothing to strike then costs nothing, as before.
 * One press made during a wind-up waits and goes off right after. Commands, crown keys and code still cast at once.</p>
 */
public final class SkillWindup {
    private static final class Pending {
        final ServerPlayer player;
        final Skill skill;
        final long release;
        /** A skill pressed during the wind-up, to go off right after, or null. */
        Skill queued;

        Pending(ServerPlayer player, Skill skill, long release) {
            this.player = player;
            this.skill = skill;
            this.release = release;
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    public static void init(IEventBus modBus) {
        modBus.addListener(SkillWindup::payloads);
        NeoForge.EVENT_BUS.addListener(SkillWindup::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> PENDING.clear());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> PENDING.remove(e.getEntity().getUUID()));
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(SkillWindupPayload.TYPE, SkillWindupPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> com.beyondsolo.client.skill.SkillGestures.windup(p)));
    }

    /** A skill key was pressed: winds up for the skill in that bar slot, or casts it at once if it has no wind-up. */
    public static boolean pressSlot(ServerPlayer p, int slot) {
        String path = SkillAPI.get(p).slot(slot);
        Skill s = path.isEmpty() ? null : Skills.get(path);
        return s != null && press(p, s);
    }

    /**
     * Winds up for {@code s} (as if its key was pressed), or casts it at once if it has none. While a wind-up is under
     * way nothing new starts: another skill waits its turn and goes off right after (the last one pressed), and the
     * same skill pressed again is not a second cast.
     */
    public static boolean press(ServerPlayer p, Skill s) {
        Pending pending = PENDING.get(p.getUUID());
        if (pending != null) {
            if (pending.skill != s) pending.queued = s;
            return false;
        }
        Object[] look = Gestures.look(s);
        int delay = (int) look[2] <= Gestures.MAX_DELAY ? (int) look[2] : 0;
        int ticks = Gestures.windup(s, (String) look[0], (int) look[1], delay);
        if (ticks <= 0 || !SkillAPI.knows(p, s)) return SkillAPI.cast(p, s);
        if (!SkillAPI.ready(p, s)) return false;
        PENDING.put(p.getUUID(), new Pending(p, s, p.level().getGameTime() + ticks));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new SkillWindupPayload(p.getId(), s.path(), (String) look[0], (int) look[1], ticks, delay));
        return true;
    }

    /** True while the hunter is winding up for a skill. */
    public static boolean winding(ServerPlayer p) {
        return PENDING.containsKey(p.getUUID());
    }

    private static void tick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        for (Pending pend : new ArrayList<>(PENDING.values())) {
            ServerPlayer p = pend.player;
            if (p.isRemoved() || p.hasDisconnected()) {
                PENDING.remove(p.getUUID(), pend);
                continue;
            }
            if (p.level().getGameTime() < pend.release) continue;
            PENDING.remove(p.getUUID(), pend);
            SkillAPI.cast(p, pend.skill);
            if (pend.queued != null) press(p, pend.queued);
        }
    }

    private SkillWindup() {}
}
