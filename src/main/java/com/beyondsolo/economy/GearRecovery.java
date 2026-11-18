package com.beyondsolo.economy;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.GateService;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.redgate.RedWorld;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Collection;

/**
 * Falling inside a gate doesn't cost a hunter their gear (playtest 0.1.6). A gate's cave is closed and wiped once it
 * is cleared and empty, and a red gate ends, so whatever a hunter dropped there (or a gravestone holding it) would be
 * gone for good. Instead the System carries what they were holding out of the gate, and hands it back when they get
 * up again, for {@link #FEE} gold (or all they have, if less). Deaths out in the world are left to the world (and to a
 * gravestone mod, if the pack has one).
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID)
public final class GearRecovery {
    /** What the System charges to carry a fallen hunter's gear out of a gate. */
    public static final long FEE = 100;
    /** Key under the player's persisted data (kept through death) holding the carried items. */
    public static final String KEY = "beyondsolo_recovered_gear";

    /** Places that don't last: a gate's cave and a red gate. */
    public static boolean fleeting(Level level) {
        return level.dimension().equals(GateService.DIMENSION) || level.dimension().equals(RedWorld.DIMENSION);
    }

    /** Before anything else (a gravestone mod included) can leave the drops in the cave, the System takes them. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !fleeting(p.level()) || event.getDrops().isEmpty()) return;
        carry(p, event.getDrops());
        event.setCanceled(true);
    }

    /** The System takes these drops into its keeping, kept through death (the collection is emptied). */
    public static void carry(ServerPlayer p, Collection<ItemEntity> drops) {
        CompoundTag kept = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ListTag list = kept.getList(KEY, Tag.TAG_COMPOUND);
        for (ItemEntity e : drops) {
            ItemStack s = e.getItem();
            if (!s.isEmpty()) list.add(s.save(p.registryAccess()));
        }
        if (!list.isEmpty()) kept.put(KEY, list);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
        drops.clear();
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) handBack(p);
    }

    /** Someone who logged out with gear still held (a crash before they got up) gets it back when they are alive. */
    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && p.isAlive()) handBack(p);
    }

    /** True if the System is holding gear for this player. */
    public static boolean holding(Player p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).contains(KEY);
    }

    /**
     * Gives back whatever the System carried out: armour onto empty armour slots, the rest into the inventory, then
     * System storage, then at their feet. Charges the fee. Returns how many items came back.
     */
    public static int handBack(ServerPlayer p) {
        CompoundTag kept = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!kept.contains(KEY)) return 0;
        ListTag list = kept.getList(KEY, Tag.TAG_COMPOUND);
        kept.remove(KEY);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
        int count = 0;
        boolean stored = false;
        for (Tag t : list) {
            ItemStack s = ItemStack.parseOptional(p.registryAccess(), (CompoundTag) t);
            if (s.isEmpty()) continue;
            count += s.getCount();
            EquipmentSlot slot = p.getEquipmentSlotForItem(s);
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && p.getItemBySlot(slot).isEmpty()) {
                p.setItemSlot(slot, s);
                continue;
            }
            p.getInventory().add(s);
            if (!s.isEmpty() && SystemAPI.hasSystem(p)) {
                SystemStorage.insert(Economy.data(p).storage(), SystemStorage.capacity(p), s);
                stored = true;
            }
            if (!s.isEmpty()) p.drop(s, false);
        }
        if (count == 0) return 0;
        long fee = Math.min(FEE, Economy.gold(p));
        if (fee > 0) Economy.spend(p, fee, Economy.Reason.RECOVERY);
        p.getInventory().setChanged();
        p.containerMenu.broadcastChanges();
        if (stored) Economy.sync(p);
        Notifications.send(p, SystemNotification.of(SystemNotification.Kind.INFO,
                Component.translatable("notify.beyondsolo.gear_recovered.title"),
                Component.translatable("notify.beyondsolo.gear_recovered.body", fee)));
        return count;
    }

    private GearRecovery() {}
}
