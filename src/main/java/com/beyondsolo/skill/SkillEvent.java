package com.beyondsolo.skill;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Skill events on {@code NeoForge.EVENT_BUS}. The job change, quests and achievements can listen to these. */
public abstract class SkillEvent extends Event {
    private final ServerPlayer player;
    private final Skill skill;

    protected SkillEvent(ServerPlayer player, Skill skill) {
        this.player = player;
        this.skill = skill;
    }

    public ServerPlayer getPlayer() { return player; }
    public Skill getSkill() { return skill; }

    /** Where a skill came from. */
    public enum Source { LEVEL, BOOK, QUEST, JOB_CHANGE, COMMAND, OTHER }

    /** The player learned a new skill. */
    public static final class Learned extends SkillEvent {
        private final Source source;

        public Learned(ServerPlayer player, Skill skill, Source source) {
            super(player, skill);
            this.source = source;
        }

        public Source getSource() { return source; }
    }

    /** A skill went up a level. */
    public static final class LevelUp extends SkillEvent {
        private final int newLevel;

        public LevelUp(ServerPlayer player, Skill skill, int newLevel) {
            super(player, skill);
            this.newLevel = newLevel;
        }

        public int getNewLevel() { return newLevel; }
    }

    /** Fired before an active skill runs. Cancel it to stop the cast (nothing is spent). */
    public static final class Cast extends SkillEvent implements ICancellableEvent {
        private final int level;

        public Cast(ServerPlayer player, Skill skill, int level) {
            super(player, skill);
            this.level = level;
        }

        public int getLevel() { return level; }
    }
}
