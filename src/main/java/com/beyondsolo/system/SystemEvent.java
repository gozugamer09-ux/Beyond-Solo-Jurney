package com.beyondsolo.system;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Events fired on {@code NeoForge.EVENT_BUS} when the System changes a player.
 * Quests, achievements, gates and combat listen to these instead of patching the core.
 */
public abstract class SystemEvent extends Event {
    private final ServerPlayer player;

    protected SystemEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public SystemData getData() {
        return SystemAPI.get(player);
    }

    /** The player finished an evaluation and the System is now active for them. */
    public static final class Awakened extends SystemEvent {
        private final PlayerRank rank;
        private final PlayerClass playerClass;

        public Awakened(ServerPlayer player, PlayerRank rank, PlayerClass playerClass) {
            super(player);
            this.rank = rank;
            this.playerClass = playerClass;
        }

        public PlayerRank getRank() { return rank; }
        public PlayerClass getPlayerClass() { return playerClass; }
    }

    /** Fired before XP is added. Change the amount or cancel it. */
    public static final class XpGain extends SystemEvent implements ICancellableEvent {
        private final ResourceLocation source;
        private long amount;

        public XpGain(ServerPlayer player, long amount, ResourceLocation source) {
            super(player);
            this.amount = amount;
            this.source = source;
        }

        /** Where the XP came from, e.g. {@code beyondsolo:kill} or a quest id. */
        public ResourceLocation getSource() { return source; }
        public long getAmount() { return amount; }
        public void setAmount(long amount) { this.amount = Math.max(0, amount); }
    }

    public static final class LevelUp extends SystemEvent {
        private final int oldLevel;
        private final int newLevel;

        public LevelUp(ServerPlayer player, int oldLevel, int newLevel) {
            super(player);
            this.oldLevel = oldLevel;
            this.newLevel = newLevel;
        }

        public int getOldLevel() { return oldLevel; }
        public int getNewLevel() { return newLevel; }
    }

    public static final class RankChanged extends SystemEvent {
        private final PlayerRank oldRank;
        private final PlayerRank newRank;

        public RankChanged(ServerPlayer player, PlayerRank oldRank, PlayerRank newRank) {
            super(player);
            this.oldRank = oldRank;
            this.newRank = newRank;
        }

        public PlayerRank getOldRank() { return oldRank; }
        public PlayerRank getNewRank() { return newRank; }
    }

    public static final class StatAllocated extends SystemEvent {
        private final Stat stat;
        private final int newValue;

        public StatAllocated(ServerPlayer player, Stat stat, int newValue) {
            super(player);
            this.stat = stat;
            this.newValue = newValue;
        }

        public Stat getStat() { return stat; }
        public int getNewValue() { return newValue; }
    }

    /** The player hit 100% exhaustion and collapsed. */
    public static final class Collapsed extends SystemEvent {
        private final int durationTicks;

        public Collapsed(ServerPlayer player, int durationTicks) {
            super(player);
            this.durationTicks = durationTicks;
        }

        public int getDurationTicks() { return durationTicks; }
    }

    /** The objective line changed (e.g. "find a village" was completed). */
    public static final class ObjectiveChanged extends SystemEvent {
        private final String oldObjective;
        private final String newObjective;

        public ObjectiveChanged(ServerPlayer player, String oldObjective, String newObjective) {
            super(player);
            this.oldObjective = oldObjective;
            this.newObjective = newObjective;
        }

        public String getOldObjective() { return oldObjective; }
        public String getNewObjective() { return newObjective; }
    }
}
