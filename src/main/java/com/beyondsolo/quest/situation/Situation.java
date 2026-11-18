package com.beyondsolo.quest.situation;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestBuilder;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestEngine;
import com.beyondsolo.quest.QuestEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * One kind of situation the System reads and answers with a quest: a creature far stronger than the player comes for
 * them, they drop to the edge of death, they cut down a whole pack without a scratch, a villager is attacked in front of
 * them... Nothing is scripted ahead: the situation is noticed as it happens ({@link #detect} once a second, or one of the
 * event hooks), and the quest it starts keeps watching. When the situation changes, the System rewrites the quest
 * ({@link Situations#branch}: "Survive" becomes "Annihilate"); when it ends, the way it ended decides what follows
 * ({@link #ended}): rewards, penalties, and things that come back later ({@link FollowUps}).
 *
 * <p>A situation keeps its running state on the quest itself ({@link Run}, in {@link Quest#extra()}), so it survives
 * saving and logging out. Hooks it doesn't need stay empty.</p>
 */
public abstract class Situation {
    private final String id;
    private final String path;

    protected Situation(String path) {
        this.path = path;
        this.id = BeyondSolo.id(path).toString();
    }

    /** {@code beyondsolo:<path>}: the cooldown key and what a quest's {@link Run} points back to. */
    public final String id() {
        return id;
    }

    public final String path() {
        return path;
    }

    /** The template id of one of its stages' quests: {@code beyondsolo:situation/<path>/<stage>}. */
    public final String template(String stage) {
        return BeyondSolo.id("situation/" + path + "/" + stage).toString();
    }

    /** {@code situation.beyondsolo.<path>.<part>}: where its lines live in the language files. */
    public final String key(String part) {
        return "situation.beyondsolo." + path + "." + part;
    }

    /** One of its own lines. */
    public final Component text(String part, Object... args) {
        return Component.translatable(key(part), args);
    }

    /** How a quest ended, in the System's words: {@code situation.beyondsolo.outcome.<id>}. Shared by every situation. */
    public static Component outcome(String id, Object... args) {
        return Component.translatable("situation.beyondsolo.outcome." + id, args);
    }

    /**
     * A builder for one of its stages: the template, the stage's title and description ({@link #key}{@code (stage)} and
     * {@code .desc}) and the run state are filled in.
     */
    protected final QuestBuilder quest(ServerPlayer player, String stage, QuestCategory category, CompoundTag run, Object... args) {
        return QuestBuilder.of(template(stage), category, player)
                .title(text(stage))
                .description(text(stage + ".desc", args))
                .extra(run);
    }

    /** How long after starting it stays quiet for that player. */
    public abstract int cooldownTicks();

    /**
     * Whether its quest is offered (the player accepts it with the accept key, or lets it go) rather than started on
     * the spot. Danger never waits for an answer; an invitation to explore does.
     */
    public boolean offered() {
        return false;
    }

    /** Checked once a second while it may start (see {@link Situations#tick}). Return the first stage's quest. */
    @Nullable
    public Quest detect(ServerPlayer player, Watch watch) {
        return null;
    }

    /** Once a second while one of its quests is active. */
    public void update(ServerPlayer player, Quest quest, Run run) {}

    /**
     * The engine is about to fail its quest (its clock ran out, the player left its area). Return true to take over:
     * complete it (holding out was the point), change it, or fail it in its own words.
     */
    public boolean expiring(ServerPlayer player, Quest quest, Run run, QuestEvent.FailReason reason) {
        return false;
    }

    /** One of its quests ended. {@code reason} is null when it was completed. */
    public void ended(ServerPlayer player, Quest quest, Run run, @Nullable QuestEvent.FailReason reason) {}

    /** The creature (or person) its quest is about died: {@link Run#target()} or one listed in its run. */
    public void runEntityDied(ServerPlayer player, Quest quest, Run run, LivingEntity dead, DamageSource source) {}

    /**
     * The player died while one of its quests was running. By default the quest fails (an emergency would anyway): the
     * situation was theirs to come through alive.
     */
    public void playerDied(ServerPlayer player, Quest quest, Run run, DamageSource source) {
        QuestEngine.fail(player, quest, QuestEvent.FailReason.DIED);
    }

    // ------------------------------------------------------------------ events, for every awakened player

    /** The player took damage (after armour). */
    public void playerHurt(ServerPlayer player, DamageSource source, float amount, Watch watch) {}

    /** The player hurt something (after armour). */
    public void playerHit(ServerPlayer player, LivingEntity target, float amount, Watch watch) {}

    /** The player (or one of their summons) killed something. */
    public void killed(ServerPlayer player, LivingEntity victim, Watch watch) {}

    @Override
    public String toString() {
        return id;
    }
}
