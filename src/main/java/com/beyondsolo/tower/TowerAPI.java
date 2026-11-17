package com.beyondsolo.tower;

import com.beyondsolo.network.TowerSyncPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.PlayerRank;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What other features may ask of the Crimson Citadel (read-only, except binding the key).
 *
 * <p>The Sigil of Dominion is the Citadel's rare prize: {@link #hasSigil} stays true once a hunter has held it, even
 * if the item itself is lost, so whatever it opens later (the monarch path) can ask here.</p>
 */
@EventBusSubscriber(modid = com.beyondsolo.BeyondSolo.MOD_ID)
public final class TowerAPI {
    /** The lowest rank the shop sells the key to (and the System mentions it to). */
    public static final PlayerRank KEY_RANK = PlayerRank.D;

    public static TowerData data(Player player) {
        return player.getData(TowerModule.CITADEL);
    }

    /** The hunter has held the Sigil of Dominion. */
    public static boolean hasSigil(Player player) {
        return data(player).sigil;
    }

    /** The lord of the Citadel has fallen to them at least once. */
    public static boolean conquered(Player player) {
        return data(player).conquered();
    }

    /** The highest floor of the Endless Spire they have cleared (0 for none). */
    public static int spireBest(Player player) {
        return data(player).spireBest;
    }

    /** In a run, or anywhere in the Citadel's dimension. */
    public static boolean inCitadel(Player player) {
        return TowerRun.inRun(player) || player.level().dimension().equals(TowerRun.DIMENSION);
    }

    /** In a run (being pulled in, climbing, or being let out): other features leave them be. */
    public static boolean busy(Player player) {
        return TowerRun.inRun(player);
    }

    /** Binds the Key to the Crimson Citadel to a hunter. False (and why, in a notice) if it can't be. */
    public static boolean bind(ServerPlayer p) {
        if (!SystemAPI.hasSystem(p) || !SystemAPI.isAwakened(p)) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.ALERT,
                    Component.translatable("notify.beyondsolo.tower.key_cold.title"), Component.translatable("notify.beyondsolo.tower.key_cold.body")));
            return false;
        }
        TowerData d = data(p);
        if (d.bound) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.tower.key_bound.title"), Component.translatable("notify.beyondsolo.tower.key_already.body")));
            return false;
        }
        d.bound = true;
        d.told = true;
        p.level().playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.2f, 0.6f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 0.5f);
        com.beyondsolo.enemy.EnemyFx.pillar(p.serverLevel(), p.position(), 0xFF2E3F, 1.1f, 10f);
        Notifications.send(p, new SystemNotification(SystemNotification.Kind.REWARD,
                Component.translatable("notify.beyondsolo.tower.key_bound.title"), Component.translatable("notify.beyondsolo.tower.key_bound.body"), 220));
        sync(p);
        return true;
    }

    public static void sync(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new TowerSyncPayload(data(p).serializeNBT(p.registryAccess())));
    }

    /** Once a hunter reaches the key's rank, the System tells them where it is sold (once). */
    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 1200 != 600) return;
        TowerData d = data(p);
        if (d.told || d.bound || !SystemAPI.isAwakened(p) || SystemAPI.get(p).rank().tier() < KEY_RANK.tier()) return;
        d.told = true;
        Notifications.send(p, new SystemNotification(SystemNotification.Kind.QUEST,
                Component.translatable("notify.beyondsolo.tower.told.title"), Component.translatable("notify.beyondsolo.tower.told.body"), 260));
        sync(p);
    }

    private TowerAPI() {}
}
