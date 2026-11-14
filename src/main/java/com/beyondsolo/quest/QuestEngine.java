package com.beyondsolo.quest;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.evaluation.EvaluationManager;
import com.beyondsolo.network.QuestEventPayload;
import com.beyondsolo.network.QuestSyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.trigger.QuestTriggers;
import com.beyondsolo.registry.ModAttachments;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Runs a player's quests: offering, starting, counting progress, completing (rewards) and failing (penalties).
 * Server side only. Every change is synced to the player's client, which animates the big moments.
 *
 * <p>Other modules: build a quest with {@link QuestBuilder} or {@link QuestTemplates}, then {@link #offer} it
 * (the player can accept or let it go) or {@link #start} it (it is theirs right away). Advance your own
 * objectives with {@link #progress(ServerPlayer, String, int)}. Listen to {@link QuestEvent}s.</p>
 */
public final class QuestEngine {
    /** How long a surprise quest offer stays open. */
    public static final int OFFER_TICKS = 20 * 45;
    public static final int MAX_OFFERS = 3;
    private static final long DAY = 24000L;

    public static QuestData data(Player player) {
        return player.getData(ModAttachments.QUESTS);
    }

    public static long gameTime(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    public static long day(ServerPlayer player) {
        return player.server.overworld().getDayTime() / DAY;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Offers a quest: a pop-up asks the player to accept it. It disappears after {@code expireTicks}. */
    public static void offer(ServerPlayer player, Quest quest, int expireTicks) {
        if (quest.isForced()) {
            start(player, quest);
            return;
        }
        QuestData data = data(player);
        List<Quest> offers = data.offers();
        if (offers.size() >= MAX_OFFERS) data.remove(offers.get(0));
        quest.offer(gameTime(player) + expireTicks);
        data.add(quest);
        Notifications.send(player, new SystemNotification(SystemNotification.Kind.QUEST,
                Component.translatable("quest.beyondsolo.offer.title", quest.title()),
                Component.translatable("quest.beyondsolo.offer.body", quest.description(), Component.keybind("key.beyondsolo.accept_quest")),
                Math.min(expireTicks, 20 * 12)));
        sync(player);
    }

    /** Gives the player a quest right away, with the "quest accepted" moment. */
    public static void start(ServerPlayer player, Quest quest) {
        QuestData data = data(player);
        if (!data.all().contains(quest)) data.add(quest);
        quest.activate(gameTime(player));
        if (quest.category() == QuestCategory.DAILY) quest.setDeadline(gameTime(player) + DAY - player.server.overworld().getDayTime() % DAY);
        refresh(player, quest);
        sync(player);
        event(player, QuestEventPayload.ACCEPTED, quest, 0);
        if (quest.isForced()) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.35f, 1.6f);
        }
        NeoForge.EVENT_BUS.post(new QuestEvent.Started(player, quest));
        if (quest.isComplete()) complete(player, quest);
    }

    /** Starts a template by id. Returns the quest, or null if there is no such template. */
    @Nullable
    public static Quest start(ServerPlayer player, String template, QuestTemplate.Context context) {
        Quest q = QuestTemplates.create(template, player, context);
        if (q != null) start(player, q);
        return q;
    }

    public static boolean accept(ServerPlayer player, UUID id) {
        Quest q = data(player).find(id);
        if (q == null || q.status() != Quest.Status.OFFERED) return false;
        start(player, q);
        return true;
    }

    /** The accept key: takes the newest open offer. */
    public static boolean acceptLatest(ServerPlayer player) {
        List<Quest> offers = data(player).offers();
        return !offers.isEmpty() && accept(player, offers.get(offers.size() - 1).id());
    }

    public static void decline(ServerPlayer player, UUID id) {
        QuestData data = data(player);
        Quest q = data.find(id);
        if (q == null || q.status() != Quest.Status.OFFERED) return;
        data.remove(q);
        sync(player);
    }

    /** Gives up an active quest. Story quests can't be abandoned; the rest fail with half their penalty. */
    public static boolean abandon(ServerPlayer player, UUID id) {
        Quest q = data(player).find(id);
        if (q == null || q.status() != Quest.Status.ACTIVE || q.category() == QuestCategory.SET) return false;
        fail(player, q, QuestEvent.FailReason.ABANDONED);
        return true;
    }

    public static void complete(ServerPlayer player, Quest quest) {
        QuestData data = data(player);
        if (!data.all().remove(quest)) return;
        data.markFinished(quest.template());
        data.countCompleted();

        QuestRewards r = quest.rewards();
        if (r.xp() > 0) {
            ResourceLocation source = ResourceLocation.tryParse(quest.template());
            SystemAPI.grantXp(player, r.xp(), source == null ? BeyondSolo.id("quest") : source);
        }
        if (r.statPoints() > 0) SystemAPI.addStatPoints(player, r.statPoints());
        if (r.mana() > 0) SystemAPI.restoreMana(player, r.mana());
        if (!r.title().isEmpty()) SystemAPI.setTitle(player, r.title());
        for (ItemStack stack : r.items()) {
            ItemStack give = stack.copy();
            if (!player.getInventory().add(give) && !give.isEmpty()) player.drop(give, false);
        }

        // a little light for everyone else around; the player gets the big moment on screen
        if (player.level() instanceof ServerLevel level) {
            Vec3 p = player.position();
            for (int i = 0; i < 24; i++) {
                double a = i / 24.0 * Math.PI * 2;
                level.sendParticles(ParticleTypes.END_ROD, p.x + Math.cos(a) * 1.2, p.y + 0.15, p.z + Math.sin(a) * 1.2, 1, 0, 0.12, 0, 0.02);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.5f);
        }

        sync(player);
        event(player, QuestEventPayload.COMPLETED, quest, 0);
        NeoForge.EVENT_BUS.post(new QuestEvent.Completed(player, quest));

        String next = QuestTemplates.nextInLine(quest.template());
        if (next != null && !data.hasFinished(next)) {
            ServerScheduler.schedule(20 * 5, () -> {
                if (!player.hasDisconnected() && !data(player).has(next)) start(player, next, QuestTemplate.Context.NONE);
            });
        }
    }

    public static void fail(ServerPlayer player, Quest quest, QuestEvent.FailReason reason) {
        QuestData data = data(player);
        if (!data.all().remove(quest)) return;
        data.countFailed();
        QuestPenalty penalty = reason == QuestEvent.FailReason.ABANDONED ? quest.penalty().scaled(0.5f) : quest.penalty();
        if (!penalty.isNone()) {
            if (penalty.exhaustion() > 0) SystemAPI.addExhaustion(player, penalty.exhaustion());
            if (penalty.debuffSeconds() > 0) {
                int ticks = penalty.debuffSeconds() * 20;
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0));
            }
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 0.7f);
        sync(player);
        event(player, QuestEventPayload.FAILED, quest, reason.ordinal());
        NeoForge.EVENT_BUS.post(new QuestEvent.Failed(player, quest, reason));
    }

    /** Wipes every quest and the quest line (used by /system reset). */
    public static void reset(ServerPlayer player) {
        data(player).reset();
        sync(player);
    }

    // ------------------------------------------------------------------ progress

    /** Advances a module's own CUSTOM objectives, e.g. {@code progress(player, "mymod:talk_to_elder", 1)}. */
    public static void progress(ServerPlayer player, String key, int amount) {
        progress(player, ObjectiveType.CUSTOM, o -> o.target().equals(key), amount, null);
    }

    /**
     * Adds {@code amount} to every active objective of {@code type} that {@code matches}.
     * {@code where} is checked against the quest's area for area-only objectives (null = not in any area).
     */
    public static void progress(ServerPlayer player, ObjectiveType type, Predicate<QuestObjective> matches, int amount, @Nullable Vec3 where) {
        if (amount <= 0) return;
        QuestData data = data(player);
        if (data.all().isEmpty()) return;
        boolean changed = false;
        List<Quest> done = new ArrayList<>();
        for (Quest q : data.active()) {
            for (int i = 0; i < q.objectives().size(); i++) {
                QuestObjective o = q.objectives().get(i);
                if (o.type() != type || o.done() || !matches.test(o)) continue;
                if (o.areaOnly() && q.area() != null && (where == null || !q.area().contains(player.level(), where))) continue;
                if (o.setProgress(o.progress() + amount)) {
                    changed = true;
                    if (o.done() && !q.isComplete()) event(player, QuestEventPayload.OBJECTIVE, q, i);
                }
            }
            if (q.isComplete()) done.add(q);
        }
        if (changed) sync(player);
        for (Quest q : done) complete(player, q);
    }

    /** Re-counts objectives that look at the player's state (items held, level). */
    static boolean refresh(ServerPlayer player, Quest q) {
        boolean changed = false;
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestObjective o = q.objectives().get(i);
            int value;
            if (o.type() == ObjectiveType.COLLECT) {
                value = 0;
                for (ItemStack stack : player.getInventory().items) if (Targets.item(o.target(), stack)) value += stack.getCount();
                for (ItemStack stack : player.getInventory().offhand) if (Targets.item(o.target(), stack)) value += stack.getCount();
            } else if (o.type() == ObjectiveType.LEVEL) {
                value = SystemAPI.get(player).level();
            } else {
                continue;
            }
            boolean wasDone = o.done();
            if (o.setProgress(value)) {
                changed = true;
                if (!wasDone && o.done() && q.status() == Quest.Status.ACTIVE && !q.isComplete()) event(player, QuestEventPayload.OBJECTIVE, q, i);
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------ ticking

    /** Once a second per player: hands out the first quests, the daily, offers and deadlines, triggers. */
    public static void tick(ServerPlayer player) {
        if (!SystemAPI.isAwakened(player)) return;
        QuestData data = data(player);
        boolean changed = false;
        long now = gameTime(player);

        if (!data.started() && !EvaluationManager.isBeingEvaluated(player)) {
            data.setStarted(true);
            data.setDailyDay(day(player));
            start(player, QuestTemplates.SET_LINE.get(0), QuestTemplate.Context.NONE);
            ServerScheduler.schedule(20 * 4, () -> {
                if (!player.hasDisconnected() && !data(player).has(QuestTemplates.DAILY)) start(player, QuestTemplates.DAILY, QuestTemplate.Context.NONE);
            });
            return;
        }
        if (!data.started()) return;

        // a new day: an unfinished daily fails, and a fresh one starts
        long day = day(player);
        if (day != data.dailyDay()) {
            data.setDailyDay(day);
            for (Quest q : List.copyOf(data.active())) {
                if (q.category() == QuestCategory.DAILY) fail(player, q, QuestEvent.FailReason.DAY_ENDED);
            }
            start(player, QuestTemplates.DAILY, QuestTemplate.Context.NONE);
        }

        List<Quest> completed = new ArrayList<>();
        for (Quest q : List.copyOf(data.all())) {
            if (q.status() == Quest.Status.OFFERED) {
                if (q.offerExpires() >= 0 && now >= q.offerExpires()) {
                    data.remove(q);
                    changed = true;
                }
                continue;
            }
            if (q.category() != QuestCategory.DAILY && q.deadline() >= 0 && now >= q.deadline()) {
                fail(player, q, QuestEvent.FailReason.TIME);
                continue;
            }
            if (q.area() != null && q.area().left(player.level(), player.position())) {
                fail(player, q, QuestEvent.FailReason.FLED);
                continue;
            }
            if (refresh(player, q)) changed = true;
            if (q.isComplete()) completed.add(q);
        }
        if (changed) sync(player);
        for (Quest q : completed) complete(player, q);

        QuestTriggers.check(player, data);
    }

    /** The player died: emergencies they were in fail. */
    public static void onDeath(ServerPlayer player) {
        for (Quest q : List.copyOf(data(player).active())) {
            if (q.isForced()) fail(player, q, QuestEvent.FailReason.DIED);
        }
    }

    // ------------------------------------------------------------------ sync

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new QuestSyncPayload(data(player).serializeNBT(player.registryAccess()), gameTime(player)));
    }

    private static void event(ServerPlayer player, int kind, Quest quest, int detail) {
        PacketDistributor.sendToPlayer(player, new QuestEventPayload(kind, quest.save(player.registryAccess()), detail));
    }

    private QuestEngine() {}
}
