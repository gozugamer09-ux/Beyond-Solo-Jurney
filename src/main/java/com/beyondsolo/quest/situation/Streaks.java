package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.quest.QuestObjective;
import com.beyondsolo.quest.QuestRewards;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

/**
 * Streaks the System notices and dares the player to keep going: each finished tier is paid on the spot and the quest
 * moves up to the next, harder one (the System rewriting it as they go). Breaking the streak only ends it: what was
 * banked stays banked.
 */
public final class Streaks {
    /** Stage name of a tier (0-based index). */
    static String tier(int i) {
        return "tier" + (i + 1);
    }

    static int tierOf(Run run) {
        String s = run.stage();
        try {
            return Integer.parseInt(s.substring(4)) - 1;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Counts one for the streak's quest, moving it up a tier when this one is done. */
    static void count(ServerPlayer player, Streak s) {
        Quest q = Situations.running(player, s);
        Run run = q == null ? null : Run.of(q);
        if (run == null) return;
        int i = Situations.objective(q, s.key());
        if (i < 0) return;
        QuestObjective o = q.objectives().get(i);
        int tier = tierOf(run);
        if (o.progress() + 1 < o.required()) {
            Situations.add(player, q, s.key(), 1);
            return;
        }
        if (tier + 1 < s.tiers().length) {
            CompoundTag next = run.next(tier(tier + 1));
            next.putInt(Run.LOOPS, run.loops() + 1);
            Situations.branch(player, q, s.quest(player, tier + 1, next), true);
        } else {
            q.setOutcome(Situation.outcome(s.finale()));
            Situations.add(player, q, s.key(), 1);
        }
    }

    /** The two streaks share their shape: tiers, a key, a last tier that is a secret. */
    abstract static class Streak extends Situation {
        Streak(String path) {
            super(path);
        }

        abstract int[] tiers();

        abstract String key();

        /** The outcome line of the last tier. */
        abstract String finale();

        /** The secret the last tier reveals. */
        abstract String secret();

        abstract Quest quest(ServerPlayer player, int tier, CompoundTag run);

        Quest first(ServerPlayer player) {
            return quest(player, 0, Run.start(this, tier(0), QuestEngine.gameTime(player)));
        }

        @Override
        public void killed(ServerPlayer player, LivingEntity victim, Watch watch) {
            if (victim instanceof Enemy) count(player, this);
        }

        @Override
        public void ended(ServerPlayer player, Quest q, Run run, @Nullable QuestEvent.FailReason reason) {
            if (reason == null && tierOf(run) == tiers().length - 1) Secrets.reveal(player, secret());
        }
    }

    /** "Untouchable": kill after kill without taking a hit. A hit ends it. */
    public static final class Untouchable extends Streak {
        public static final int[] TIERS = {6, 10, 15};
        /** Clean kills in a row before the System notices. */
        public static final int START = 3;
        static final String CLEAN = BeyondSolo.id("streak_clean").toString();

        public Untouchable() {
            super("untouchable");
        }

        @Override
        public int cooldownTicks() {
            return 20 * 60 * 4;
        }

        @Override int[] tiers() { return TIERS; }
        @Override String key() { return CLEAN; }
        @Override String finale() { return "flawless"; }
        @Override String secret() { return Secrets.FLAWLESS; }

        @Override
        @Nullable
        public Quest detect(ServerPlayer player, Watch watch) {
            long now = QuestEngine.gameTime(player);
            if (watch.cleanKills < START || now - watch.lastCleanKill > 20 * 20) return null;
            return first(player);
        }

        @Override
        Quest quest(ServerPlayer player, int tier, CompoundTag run) {
            int n = TIERS[tier];
            return quest(player, tier(tier), QuestCategory.SURPRISE, run, n)
                    .custom(CLEAN, n, text("obj.clean", n))
                    .rule(text("obj.no_hits"))
                    .xp(90 + 70L * tier)
                    .mana(20 + 10 * tier)
                    .build();
        }

        @Override
        public void ended(ServerPlayer player, Quest q, Run run, @Nullable QuestEvent.FailReason reason) {
            super.ended(player, q, run, reason);
            Situations.watch(player).cleanKills = 0;
        }

        @Override
        public void playerHurt(ServerPlayer player, DamageSource source, float amount, Watch watch) {
            if (source.getEntity() == null || source.getEntity() == player) return;
            Quest q = Situations.running(player, this);
            Run run = q == null ? null : Run.of(q);
            if (run == null) return;
            QuestEngine.fail(player, q, QuestEvent.FailReason.BROKEN, outcome(run.loops() > 0 ? "streak_banked" : "streak_broken", run.loops()));
        }
    }

    /** "Frenzy": kills come fast. Keep the pace up, tier after tier, before each clock runs out. */
    public static final class Frenzy extends Streak {
        public static final int[] TIERS = {8, 12, 16};
        public static final int SECONDS = 30;
        /** Kills within {@link #WINDOW} ticks before the System notices. */
        public static final int START = 4;
        public static final long WINDOW = 20 * 15;
        static final String KILLS = BeyondSolo.id("streak_kills").toString();

        public Frenzy() {
            super("frenzy");
        }

        @Override
        public int cooldownTicks() {
            return 20 * 60 * 4;
        }

        @Override int[] tiers() { return TIERS; }
        @Override String key() { return KILLS; }
        @Override String finale() { return "unstoppable"; }
        @Override String secret() { return Secrets.UNSTOPPABLE; }

        @Override
        @Nullable
        public Quest detect(ServerPlayer player, Watch watch) {
            if (watch.killsWithin(QuestEngine.gameTime(player), WINDOW) < START) return null;
            return first(player);
        }

        @Override
        Quest quest(ServerPlayer player, int tier, CompoundTag run) {
            int n = TIERS[tier];
            return quest(player, tier(tier), QuestCategory.SURPRISE, run, n, SECONDS)
                    .custom(KILLS, n, text("obj.kills", n))
                    .timeLimit(SECONDS * 20)
                    .xp(80 + 60L * tier)
                    .mana(15 + 10 * tier)
                    .build();
        }

        @Override
        public void ended(ServerPlayer player, Quest q, Run run, @Nullable QuestEvent.FailReason reason) {
            super.ended(player, q, run, reason);
            Situations.watch(player).recentKills.clear();
        }

        @Override
        public boolean expiring(ServerPlayer player, Quest q, Run run, QuestEvent.FailReason reason) {
            if (run.loops() > 0) QuestEngine.complete(player, q, outcome("frenzy_faded", run.loops()), QuestRewards.NONE);
            else QuestEngine.fail(player, q, QuestEvent.FailReason.BROKEN, outcome("too_slow"));
            return true;
        }
    }

    private Streaks() {}
}
