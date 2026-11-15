package com.beyondsolo.economy;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.quest.QuestEvent;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemEvent;
import com.beyondsolo.util.ServerScheduler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Where gold comes from (monsters, quests, gates), and when the hunter license is issued and renewed.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class EconomyHooks {
    /** Gold for clearing a gate, by its rank E..S. */
    public static final long[] GATE_GOLD = {120, 250, 500, 1000, 2000, 4000};
    /** Ticks after the evaluation before the license arrives (the reveal plays first). */
    public static final int LICENSE_DELAY = 260;

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Economy.sync(p);
        // worlds from before licenses existed: evaluated hunters get theirs now
        if (SystemAPI.isAwakened(p) && !Economy.data(p).licenseIssued()) issueLicense(p);
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Economy.sync(p);
    }

    @SubscribeEvent
    static void onAwakened(SystemEvent.Awakened event) {
        ServerPlayer p = event.getPlayer();
        Economy.sync(p);
        if (Economy.data(p).licenseIssued()) return;
        ServerScheduler.schedule(LICENSE_DELAY, () -> {
            if (!p.isRemoved() && SystemAPI.isAwakened(p) && !Economy.data(p).licenseIssued()) issueLicense(p);
        });
    }

    @SubscribeEvent
    static void onRank(SystemEvent.RankChanged event) {
        ServerPlayer p = event.getPlayer();
        Economy.sync(p);
        if (!Economy.data(p).licenseIssued() || event.getNewRank() == event.getOldRank()) return;
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                Component.translatable("license.beyondsolo.renewed"),
                Component.translatable("license.beyondsolo.renewed.body", event.getNewRank().displayName(), SystemStorage.capacity(p))));
    }

    /** Hands out the license (into the inventory, or at the player's feet) and says so. */
    public static void issueLicense(ServerPlayer p) {
        HunterData d = Economy.data(p);
        d.licenseIssued = true;
        if (d.licenseIssuedDay < 0) d.licenseIssuedDay = ShopService.day(p);
        ItemStack card = new ItemStack(EconomyModule.HUNTER_LICENSE.get());
        HunterLicenseItem.refresh(p, card);
        if (!p.getInventory().add(card) && !card.isEmpty()) p.drop(card, false);
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD,
                Component.translatable("license.beyondsolo.issued"),
                Component.translatable("license.beyondsolo.issued.body", SystemAPI.get(p).rank().displayName())));
        Economy.sync(p);
    }

    /** Monsters drop gold for hunters with the System; gate monsters drop more, by the gate's rank. */
    @SubscribeEvent
    static void onKill(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || dead instanceof Player || !(dead instanceof Enemy)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || !SystemAPI.hasSystem(p)) return;
        long gold = killGold(dead);
        if (gold > 0) Economy.add(p, gold, Economy.Reason.KILL);
    }

    public static long killGold(LivingEntity dead) {
        float base = Math.max(1f, dead.getMaxHealth() / 5f);
        var tag = dead.getPersistentData();
        if (tag.contains(GateService.MOB_TAG)) {
            int tier = Math.min(tag.getInt(GateService.MOB_TAG), PlayerRank.S.tier());
            base *= 1.5f + 0.6f * tier;
        }
        float roll = 0.8f + dead.getRandom().nextFloat() * 0.4f;
        return Math.max(1, Math.round(base * roll));
    }

    /** Quests pay a quarter of their XP in gold. */
    @SubscribeEvent
    static void onQuest(QuestEvent.Completed event) {
        ServerPlayer p = event.getPlayer();
        long gold = Math.max(5, event.getQuest().rewards().xp() / 4);
        Economy.add(p, gold, Economy.Reason.QUEST);
    }

    /** Called by the gate when its boss falls, for everyone inside. */
    public static void gateCleared(ServerPlayer p, PlayerRank rank) {
        Economy.countClear(p, rank);
        if (SystemAPI.hasSystem(p)) Economy.add(p, GATE_GOLD[rank.tier()], Economy.Reason.GATE);
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        EconomyCommand.register(event.getDispatcher());
    }

    private EconomyHooks() {}
}
