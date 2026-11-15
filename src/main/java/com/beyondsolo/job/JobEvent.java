package com.beyondsolo.job;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/** Job change events on {@code NeoForge.EVENT_BUS}. The shadow army, titles and quests can listen to these. */
public abstract class JobEvent extends Event {
    private final ServerPlayer player;

    protected JobEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() { return player; }

    /** The player reached the level milestone and the job change trial opened to them. */
    public static final class TrialUnlocked extends JobEvent {
        public TrialUnlocked(ServerPlayer player) {
            super(player);
        }
    }

    /** The player cleared or failed the trial. */
    public static final class TrialEnded extends JobEvent {
        private final boolean cleared;

        public TrialEnded(ServerPlayer player, boolean cleared) {
            super(player);
            this.cleared = cleared;
        }

        public boolean cleared() { return cleared; }
    }

    /** The player's subclass changed (null old = their first job change). */
    public static final class Changed extends JobEvent {
        private final @Nullable Subclass old;
        private final Subclass subclass;

        public Changed(ServerPlayer player, @Nullable Subclass old, Subclass subclass) {
            super(player);
            this.old = old;
            this.subclass = subclass;
        }

        public @Nullable Subclass getOld() { return old; }
        public Subclass getSubclass() { return subclass; }
    }
}
