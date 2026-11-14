package com.beyondsolo.client.quest;

import com.beyondsolo.network.QuestActionPayload;
import com.beyondsolo.network.QuestEventPayload;
import com.beyondsolo.network.QuestSyncPayload;
import com.beyondsolo.client.notify.NotificationManager;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.Quest;
import com.beyondsolo.quest.QuestCategory;
import com.beyondsolo.quest.QuestData;
import com.beyondsolo.quest.QuestEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** The local player's quests as last sent by the server, plus helpers for the UI. */
public final class ClientQuests {
    private static QuestData data = new QuestData();
    private static long serverTime;
    private static long clientTimeAtSync;

    public static void update(QuestSyncPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        QuestData fresh = new QuestData();
        fresh.deserializeNBT(mc.level.registryAccess(), payload.data());
        data = fresh;
        serverTime = payload.gameTime();
        clientTimeAtSync = mc.level.getGameTime();
        QuestTracker.onSync(fresh);
    }

    public static void onEvent(QuestEventPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Quest quest = Quest.load(payload.quest(), mc.level.registryAccess());
        switch (payload.kind()) {
            case QuestEventPayload.ACCEPTED -> {
                QuestOverlay.push(QuestOverlay.Moment.ACCEPTED, quest, 0);
                NotificationManager.log(SystemNotification.of(quest.isForced() ? SystemNotification.Kind.ALERT : SystemNotification.Kind.QUEST,
                        Component.translatable(quest.isForced() ? "quest.beyondsolo.log.emergency" : "quest.beyondsolo.log.accepted", quest.title()),
                        quest.description()));
            }
            case QuestEventPayload.COMPLETED -> {
                QuestOverlay.push(QuestOverlay.Moment.COMPLETED, quest, 0);
                NotificationManager.log(SystemNotification.of(SystemNotification.Kind.REWARD,
                        Component.translatable("quest.beyondsolo.log.completed", quest.title()), QuestText.rewardsLine(quest)));
            }
            case QuestEventPayload.FAILED -> {
                QuestEvent.FailReason reason = QuestEvent.FailReason.values()[Math.floorMod(payload.detail(), QuestEvent.FailReason.values().length)];
                QuestOverlay.push(QuestOverlay.Moment.FAILED, quest, payload.detail());
                NotificationManager.log(SystemNotification.of(SystemNotification.Kind.ALERT,
                        Component.translatable("quest.beyondsolo.log.failed", quest.title()), reason.describe()));
            }
            case QuestEventPayload.OBJECTIVE -> QuestTracker.objectiveDone(quest.id(), payload.detail());
            case QuestEventPayload.READY -> {
                QuestTracker.ready(quest.id());
                NotificationManager.log(SystemNotification.of(SystemNotification.Kind.QUEST,
                        Component.translatable("quest.beyondsolo.ready.title", quest.title()),
                        Component.translatable("quest.beyondsolo.ready.body", quest.giver() == null ? Component.empty() : quest.giver().name())));
            }
            default -> {}
        }
    }

    public static QuestData data() {
        return data;
    }

    /** Active quests, most urgent first: emergencies, then the System line, daily, the rest. */
    public static List<Quest> active() {
        List<Quest> list = new ArrayList<>(data.active());
        list.sort(Comparator.comparingInt(ClientQuests::priority));
        return list;
    }

    public static List<Quest> offers() {
        return data.offers();
    }

    static int priority(Quest q) {
        return switch (q.category()) {
            case EMERGENCY -> 0;
            case GATE -> 1;
            case SET -> 2;
            case SURPRISE -> 3;
            case NPC -> 4;
            case DAILY -> 5;
        };
    }

    /** The server's game time right now, estimated from the last sync. */
    public static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return serverTime;
        return serverTime + (mc.level.getGameTime() - clientTimeAtSync);
    }

    /** Ticks left on a quest's clock (deadline, or offer expiry for offers), or -1 if it has none. */
    public static long ticksLeft(Quest q) {
        long end = q.status() == Quest.Status.OFFERED ? q.offerExpires() : q.deadline();
        if (end < 0) return -1;
        return Math.max(0, end - gameTime());
    }

    public static void accept(UUID id) {
        PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.ACCEPT, id));
    }

    public static void decline(UUID id) {
        PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.DECLINE, id));
    }

    public static void abandon(UUID id) {
        PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.ABANDON, id));
    }

    /** The accept key. Returns false if there is nothing to accept. */
    public static boolean acceptLatest() {
        if (data.offers().isEmpty()) return false;
        PacketDistributor.sendToServer(new QuestActionPayload(QuestActionPayload.ACCEPT_LATEST, QuestActionPayload.NONE));
        return true;
    }

    public static boolean canAbandon(Quest q) {
        return q.isTaken() && q.category() != QuestCategory.SET;
    }

    public static void clear() {
        data = new QuestData();
        QuestOverlay.reset();
        QuestTracker.reset();
    }

    private ClientQuests() {}
}
