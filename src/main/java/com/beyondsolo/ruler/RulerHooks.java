package com.beyondsolo.ruler;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.monarch.MonarchAPI;
import com.beyondsolo.monarch.MonarchConfig;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.util.OnlinePlayers;
import com.beyondsolo.worldboss.ColossalBoss;
import com.beyondsolo.worldboss.MonarchBoss;
import com.beyondsolo.worldboss.WorldBosses;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.UUID;

/**
 * The light's hooks:
 * <ul>
 *   <li><b>Choosing a side</b>: a hunter the light doesn't stand against has their first blow on a ruler or a herald
 *   stayed, with a warning; a second blow soon after makes them its foe, and that one lands. (Area skills, shadows and
 *   hired hunters never strike the light for a hunter who isn't its foe: {@link Rulers#spares}.)</li>
 *   <li><b>A crown against the light</b>: a crowned hunter's blows on a ruler count as much more as on a monarch.</li>
 *   <li><b>The Blessing of Light</b>: a blessed hunter's blows on monarchs and their soldiers are harder
 *   ({@code rulers.blessingMight}).</li>
 *   <li>Heralds cut down go in the hunter's record; the hunter a ruler came to judge dying carries the sentence out.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class RulerHooks {
    /** How long a warning stands: a second blow within it is a choice. */
    static final int WARN_TICKS = 200;

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) return;
        DamageSource source = event.getSource();
        if (Rulers.light(target)) {
            UUID who = ColossalBoss.hunterBehind(source);
            RulerBoss ruler = Rulers.rulerOf(target);
            if (who == null || ruler == null) return;
            ServerPlayer p = OnlinePlayers.get(level.getServer(), who);
            if (ruler.holdsAgainst(who, p)) {
                // one crown knows another: a crowned hunter's blows count against the light as against a monarch
                if (p != null && target instanceof RulerBoss && MonarchAPI.crowned(p)) {
                    event.setAmount(event.getAmount() * MonarchConfig.VERSUS_MONARCH.get().floatValue());
                }
                return;
            }
            long now = level.getGameTime();
            long warned = ruler.warnedAt(who);
            if (warned >= 0 && now - warned <= WARN_TICKS && now - warned > 5) {
                // a second blow: they stand against the light now, and this one lands
                ruler.aggressor(who);
                ruler.unwarn(who);
                if (p != null) {
                    Notifications.send(p, new SystemNotification(SystemNotification.Kind.ALERT, Component.translatable("ruler.beyondsolo.notify.ruler"),
                            Component.translatable("ruler.beyondsolo.notify.turned", ruler.kind().fullName()), 140));
                }
                return;
            }
            event.setCanceled(true);
            if (warned < 0 || now - warned > WARN_TICKS) {
                ruler.warn(who, now);
                if (p != null) {
                    Notifications.send(p, new SystemNotification(SystemNotification.Kind.INFO, Component.translatable("ruler.beyondsolo.notify.ruler"),
                            Component.translatable("ruler.beyondsolo.notify.stayed", ruler.kind().fullName()), 160));
                }
            }
            return;
        }
        // the light's favour: harder blows on monarchs and their soldiers
        Entity src = source.getEntity();
        if (src instanceof ServerPlayer p && Rulers.blessed(p)
                && (target instanceof MonarchBoss || target.getPersistentData().hasUUID(WorldBosses.ARMY_TAG))) {
            event.setAmount(event.getAmount() * (1 + RulerConfig.blessingMight()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        if (dead instanceof HeraldEntity) {
            ServerPlayer p = OnlinePlayers.get(level.getServer(), ColossalBoss.hunterBehind(event.getSource()));
            if (p != null) {
                RulerRecord record = p.getData(RulerModule.RECORD.get());
                record.addHerald();
                RulerJournal.heraldSlain(p, record);
            }
            return;
        }
        if (dead instanceof ServerPlayer p && level.dimension() == Level.OVERWORLD) Rulers.carriedOut(level, p);
    }

    private RulerHooks() {}
}
