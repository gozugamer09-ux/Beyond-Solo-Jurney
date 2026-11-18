package com.beyondsolo.quest;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Fired on {@code NeoForge.EVENT_BUS} as quests change. NPC and gate modules listen to these. */
public abstract class QuestEvent extends Event {
    private final ServerPlayer player;
    private final Quest quest;

    protected QuestEvent(ServerPlayer player, Quest quest) {
        this.player = player;
        this.quest = quest;
    }

    public ServerPlayer getPlayer() { return player; }
    public Quest getQuest() { return quest; }

    /** The quest became active (accepted, forced or handed out). */
    public static final class Started extends QuestEvent {
        public Started(ServerPlayer player, Quest quest) { super(player, quest); }
    }

    /** Every objective is done, but the quest still has to be handed in to its giver. */
    public static final class Ready extends QuestEvent {
        public Ready(ServerPlayer player, Quest quest) { super(player, quest); }
    }

    /**
     * The quest turned into another one mid-way: a situation changed and the System rewrote it ("Survive" became
     * "Annihilate"). {@link #getQuest()} is the new quest; {@link #getPrevious()} the one it replaced, which neither
     * completed nor failed.
     */
    public static final class Branched extends QuestEvent {
        private final Quest previous;

        public Branched(ServerPlayer player, Quest previous, Quest quest) {
            super(player, quest);
            this.previous = previous;
        }

        public Quest getPrevious() { return previous; }
    }

    /**
     * The engine is about to fail the quest on its own (its clock ran out, the player left its area). Cancel it to take
     * over: a situation that counts the end of the clock as surviving completes the quest instead, or changes it.
     */
    public static final class Expiring extends QuestEvent implements net.neoforged.bus.api.ICancellableEvent {
        private final FailReason reason;

        public Expiring(ServerPlayer player, Quest quest, FailReason reason) {
            super(player, quest);
            this.reason = reason;
        }

        public FailReason getReason() { return reason; }
    }

    /** Every objective is done. Rewards were already given. */
    public static final class Completed extends QuestEvent {
        public Completed(ServerPlayer player, Quest quest) { super(player, quest); }
    }

    /** The quest failed (time ran out, the player fled, died, gave up, or the day ended). */
    public static final class Failed extends QuestEvent {
        private final FailReason reason;

        public Failed(ServerPlayer player, Quest quest, FailReason reason) {
            super(player, quest);
            this.reason = reason;
        }

        public FailReason getReason() { return reason; }
    }

    public enum FailReason {
        TIME, FLED, DIED, ABANDONED, DAY_ENDED,
        /** Someone the quest was about (an escorted traveller) died. */
        LOST,
        /** A condition of the quest was broken (taking a hit during a flawless streak, collapsing while resting...). */
        BROKEN;

        public net.minecraft.network.chat.Component describe() {
            return net.minecraft.network.chat.Component.translatable("quest_fail.beyondsolo." + name().toLowerCase());
        }
    }
}
