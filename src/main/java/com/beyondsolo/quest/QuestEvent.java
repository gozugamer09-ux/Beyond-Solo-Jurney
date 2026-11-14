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
        TIME, FLED, DIED, ABANDONED, DAY_ENDED;

        public net.minecraft.network.chat.Component describe() {
            return net.minecraft.network.chat.Component.translatable("quest_fail.beyondsolo." + name().toLowerCase());
        }
    }
}
