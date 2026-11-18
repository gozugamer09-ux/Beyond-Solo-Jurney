package com.beyondsolo.network;

import com.beyondsolo.BeyondSolo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: where the player is in a Crimson Citadel run, for its HUD (the floor plaque, title cards, the
 * stamps, the rush clock) and its light in the world (the seal on the stair door, the stair's light, the exit sigil,
 * the blood moon).
 *
 * @param phase    one of the phase constants ({@link #NONE} hides everything)
 * @param mode     {@link #CASTLE}, {@link #ENDLESS} or {@link #RUSH}
 * @param floor    the floor number (castle and spire)
 * @param kind     the floor's kind ({@code TowerFloors.Kind} ordinal)
 * @param wing     the floor's wing ({@code TowerFloors.Wing} ordinal)
 * @param tick     ticks the phase has run
 * @param length   the phase's length in ticks (0: until something happens)
 * @param wave     the wave being fought (from 1)
 * @param waves    waves on the floor
 * @param foes     creatures still standing
 * @param event    a moment to show once (the {@code EV_} constants)
 * @param tier     the boss rush's tier ({@code RushTier} ordinal), -1 outside a rush
 * @param bout     the rush's bout (from 1)
 * @param bouts    bouts in the rush
 * @param clock    the rush's time so far, or a siege's time left (ticks)
 * @param best     the record to beat: the spire's best floor, or the rush tier's best time (ticks, -1 none)
 * @param hold     ticks the player has stood on the exit sigil
 * @param flags    the {@code F_} bits
 * @param door     the stair door (the middle of its foot)
 * @param exit     the exit sigil (on the floor)
 * @param stair    the top of the stair (where the climb starts)
 * @param title    the big line (the floor's wing, the gatekeeper's name, the rush's tier)
 * @param subtitle the small line
 */
public record TowerPayload(int phase, int mode, int floor, int kind, int wing, int tick, int length, int wave, int waves,
                           int foes, int event, int tier, int bout, int bouts, int clock, int best, int hold, int flags,
                           BlockPos door, BlockPos exit, BlockPos stair, Component title, Component subtitle) implements CustomPacketPayload {
    public static final int NONE = 0;
    /** The System pulls the player in. */
    public static final int PULL = 1;
    /** The floor's title card. */
    public static final int ARRIVING = 2;
    public static final int FIGHT = 3;
    /** The floor is won: the stair door opens, the exit sigil shows. */
    public static final int CLEARED = 4;
    /** Up the stair (the screen goes dark and comes back on the next floor). */
    public static final int CLIMBING = 5;
    /** The lord has fallen. */
    public static final int VICTORY = 6;
    /** A boss rush's breather between bouts. */
    public static final int BREATHER = 7;
    /** A blow that would have killed them: the run is over. */
    public static final int FALLEN = 8;
    /** Out through the exit sigil (or the System window). */
    public static final int LEAVING = 9;
    /** The last boss of a rush has fallen. */
    public static final int RUSH_DONE = 10;

    public static final int CASTLE = 0;
    public static final int ENDLESS = 1;
    public static final int RUSH = 2;

    public static final int EV_NONE = 0;
    /** A new wave has come. */
    public static final int EV_WAVE = 1;
    /** A gatekeeper was beaten: a new checkpoint. */
    public static final int EV_CHECKPOINT = 2;
    /** A new best floor of the spire, or a rush's best time. */
    public static final int EV_RECORD = 3;
    /** The Sigil of Dominion fell. */
    public static final int EV_SIGIL = 4;
    /** The lord calls the blood moon. */
    public static final int EV_MOON = 5;
    /** A siege's last ten seconds. */
    public static final int EV_SIEGE_END = 6;
    /** A champion has come onto an elite floor. */
    public static final int EV_CHAMPION = 7;
    /** They reached the stair with the floor's reward cache still sealed. */
    public static final int EV_CACHE = 8;

    /** The stair door is open. */
    public static final int F_DOOR_OPEN = 1;
    /** The blood moon is up (the lord's last stand). */
    public static final int F_MOON = 2;
    /** This run has set a new record. */
    public static final int F_RECORD = 4;
    /** The exit sigil is lit. */
    public static final int F_EXIT = 8;
    /** The floor has a stair onwards. */
    public static final int F_STAIR = 16;
    /** The lord's floor has been won before (the throne challenged again). */
    public static final int F_CONQUERED = 32;

    public static final Type<TowerPayload> TYPE = new Type<>(BeyondSolo.id("tower"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TowerPayload> STREAM_CODEC = StreamCodec.of(TowerPayload::write, TowerPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, TowerPayload p) {
        for (int v : new int[]{p.phase, p.mode, p.floor, p.kind, p.wing, p.tick, p.length, p.wave, p.waves, p.foes, p.event, p.tier,
                p.bout, p.bouts, p.clock, p.best, p.hold, p.flags}) {
            ByteBufCodecs.VAR_INT.encode(buf, v);
        }
        BlockPos.STREAM_CODEC.encode(buf, p.door);
        BlockPos.STREAM_CODEC.encode(buf, p.exit);
        BlockPos.STREAM_CODEC.encode(buf, p.stair);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.title);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.subtitle);
    }

    private static TowerPayload read(RegistryFriendlyByteBuf buf) {
        int[] a = new int[18];
        for (int i = 0; i < a.length; i++) a[i] = ByteBufCodecs.VAR_INT.decode(buf);
        BlockPos door = BlockPos.STREAM_CODEC.decode(buf);
        BlockPos exit = BlockPos.STREAM_CODEC.decode(buf);
        BlockPos stair = BlockPos.STREAM_CODEC.decode(buf);
        Component title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        Component subtitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        return new TowerPayload(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14], a[15],
                a[16], a[17], door, exit, stair, title, subtitle);
    }

    public static TowerPayload none() {
        return new TowerPayload(NONE, CASTLE, 0, 0, 0, 0, 0, 0, 0, 0, EV_NONE, -1, 0, 0, 0, -1, 0, 0,
                BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO, Component.empty(), Component.empty());
    }

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
