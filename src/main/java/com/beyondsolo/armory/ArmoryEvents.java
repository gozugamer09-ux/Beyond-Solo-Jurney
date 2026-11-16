package com.beyondsolo.armory;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.item.ArmorSets;
import com.beyondsolo.item.SystemArmorItem;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.system.SystemAPI;
import com.beyondsolo.system.SystemData;
import net.combat_roll.api.event.ServerSideRollEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps worn armor honest and tells the hunter what it does:
 * <ul>
 *   <li>a piece put on some other way (a dispenser, a command) that the hunter isn't ready for goes back into the
 *   inventory, with the reason;</li>
 *   <li>when the hunter's class changes (evaluation, job change), the set bonuses are worked out again;</li>
 *   <li>two pieces of a set: a line over the hotbar; all four: a System pop-up with the perk. A set made for
 *   another class says so instead.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class ArmoryEvents {
    /** The class (ordinal + 1, 0 = none) each player's set bonuses were last worked out for. */
    private static final Map<Player, Integer> ATTUNED_TO = new WeakHashMap<>();

    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("combat_roll")) RollHook.register();
        });
    }

    /** Kept in its own class so nothing touches Combat Roll classes when the mod is missing. */
    static final class RollHook {
        static void register() {
            ServerSideRollEvents.PLAYER_START_ROLLING.register((player, velocity) -> SetPerks.rolled(player));
        }
    }

    @SubscribeEvent
    static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || (p.tickCount + p.getId()) % 20 != 5) return;
        SystemData d = SystemAPI.get(p);
        int key = d.isAwakened() && d.playerClass() != null ? d.playerClass().ordinal() + 1 : 0;
        Integer last = ATTUNED_TO.put(p, key);
        if (last == null || last != key) ArmorSets.refresh(p);
        if (p.isCreative() || p.isSpectator()) return;
        for (EquipmentSlot slot : ArmorSets.ARMOR) {
            ItemStack worn = p.getItemBySlot(slot);
            if (!(worn.getItem() instanceof SystemArmorItem a)) continue;
            Component why = ArmorSets.refusal(p, a.set());
            if (why == null) continue;
            ItemStack off = worn.copy();
            p.setItemSlot(slot, ItemStack.EMPTY);
            if (!p.getInventory().add(off)) p.drop(off, false);
            p.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void equipped(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getSlot().isArmor() || p.tickCount < 40) return;
        if (!(event.getTo().getItem() instanceof SystemArmorItem a)) return;
        if (event.getFrom().getItem() instanceof SystemArmorItem was && was.set() == a.set()) return;
        ArmorSets.Set set = a.set();
        int worn = ArmorSets.pieces(p, set);
        if (worn < 2) return;
        Component name = Component.translatable("set.beyondsolo." + set.name);
        if (!ArmorSets.attuned(p, set)) {
            if (set.playerClass != null && worn == 2) {
                p.displayClientMessage(Component.translatable("message.beyondsolo.set_unattuned", name, set.playerClass.displayName())
                        .withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        if (worn == 2) {
            p.displayClientMessage(Component.translatable("message.beyondsolo.set_two", name)
                    .withStyle(s -> s.withColor(0x9EE6FF)), true);
            p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.6f);
        } else if (worn == 4) {
            MutableComponent body = Component.empty();
            if (set.tier < 0 || com.beyondsolo.gear.BossSets.bossRank(set) != null) {
                // the shop's and the brute's sets have their bonus written out
                body.append(Component.translatable("set.beyondsolo." + set.name + ".bonus"));
            } else if (set.perk != null) {
                body.append(Component.translatable("perk.beyondsolo." + set.perk).withStyle(ChatFormatting.BOLD))
                        .append(Component.literal(": ")).append(Component.translatable("perk.beyondsolo." + set.perk + ".desc"));
            } else {
                body.append(Component.translatable("tooltip.beyondsolo.set_four", com.beyondsolo.gear.GearTooltip.stats(set.bonus())));
            }
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                    Component.translatable("notify.beyondsolo.set.title", name), body));
            p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7f, 1.5f);
        }
    }

    private ArmoryEvents() {}
}
