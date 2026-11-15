package com.beyondsolo.penalty;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Penalty Zone events, on {@code NeoForge.EVENT_BUS}. */
public abstract class PenaltyEvent extends Event {
    private final ServerPlayer player;

    protected PenaltyEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** A sentence is over: the player survived it (or fell), after killing {@code kills} creatures. */
    public static final class Ended extends PenaltyEvent {
        private final boolean survived;
        private final int kills;

        public Ended(ServerPlayer player, boolean survived, int kills) {
            super(player);
            this.survived = survived;
            this.kills = kills;
        }

        public boolean survived() { return survived; }
        public int kills() { return kills; }
    }
}
