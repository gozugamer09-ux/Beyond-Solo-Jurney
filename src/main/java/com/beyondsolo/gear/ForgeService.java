package com.beyondsolo.gear;

import com.beyondsolo.economy.Economy;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.network.ForgeActionPayload;
import com.beyondsolo.network.ForgeResultPayload;
import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.skill.SkillElement;
import com.beyondsolo.system.SystemAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The System forge, server side: refining gear (+1..+10) and infusing weapons with an element. Every check is made
 * here; the client only asks. The result goes back as a {@link ForgeResultPayload} for the forge's animation.
 */
public final class ForgeService {
    /** Gold for an infusion, by the weapon's grade. */
    public static long infusionGold(ItemGrade grade) {
        return 60L + 60L * grade.tier() * grade.tier();
    }

    public static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** How many of {@code item} a stack list holds (System storage). */
    public static int count(java.util.List<ItemStack> stacks, Item item) {
        int n = 0;
        for (ItemStack s : stacks) if (!s.isEmpty() && s.is(item)) n += s.getCount();
        return n;
    }

    /**
     * What the hunter has of a forge material: carried and in System storage together, the way crafting counts. On
     * the client, {@code storage} is the copy the server sent ({@code CraftClient.storage()}).
     */
    public static int have(Inventory inv, java.util.List<ItemStack> storage, Item item) {
        return count(inv, item) + count(storage, item);
    }

    public static int have(ServerPlayer p, Item item) {
        return have(p.getInventory(), Economy.data(p).storage(), item);
    }

    /** Takes {@code amount}: from the inventory first, the rest from System storage. */
    static void take(ServerPlayer p, Item item, int amount) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(item)) continue;
            int t = Math.min(amount, s.getCount());
            s.shrink(t);
            amount -= t;
        }
        for (ItemStack s : Economy.data(p).storage()) {
            if (amount <= 0) break;
            if (s.isEmpty() || !s.is(item)) continue;
            int t = Math.min(amount, s.getCount());
            s.shrink(t);
            amount -= t;
        }
    }

    private static boolean validSlot(ServerPlayer p, int slot) {
        return slot >= 0 && slot < p.getInventory().getContainerSize();
    }

    /** Why a refine can't go ahead, as a lang key, or null if it can. */
    public static String refuseRefine(ServerPlayer p, ItemStack stack) {
        if (!SystemAPI.hasSystem(p)) return "forge.beyondsolo.no_system";
        if (GearKind.of(stack) == null) return "forge.beyondsolo.not_gear";
        int level = Enhancement.level(stack);
        if (level >= Enhancement.MAX) return "forge.beyondsolo.maxed";
        ItemGrade grade = ItemGrade.of(stack);
        if (grade == null) return "forge.beyondsolo.not_gear";
        if (!p.getAbilities().instabuild) {
            if (have(p, GearModule.REFINING_STONE.get()) < Enhancement.stones(level)) return "forge.beyondsolo.no_stones";
            if (Economy.gold(p) < Enhancement.gold(grade, level)) return "forge.beyondsolo.no_gold";
        }
        return null;
    }

    public static Enhancement.Outcome refine(ServerPlayer p, int slot, boolean useWard) {
        if (!validSlot(p, slot)) return Enhancement.Outcome.INVALID;
        ItemStack stack = p.getInventory().getItem(slot);
        String why = refuseRefine(p, stack);
        if (why != null) {
            send(p, ForgeActionPayload.REFINE, Enhancement.Outcome.INVALID, slot, 0, 0, why);
            return Enhancement.Outcome.INVALID;
        }
        int before = Enhancement.level(stack);
        ItemGrade grade = ItemGrade.of(stack);
        boolean ward = useWard && (p.getAbilities().instabuild || have(p, GearModule.WARD_SEAL.get()) > 0);
        if (!p.getAbilities().instabuild) {
            take(p, GearModule.REFINING_STONE.get(), Enhancement.stones(before));
            Economy.spend(p, Enhancement.gold(grade, before), Economy.Reason.FORGE);
        }
        Enhancement.Outcome out = Enhancement.attempt(stack, p.getRandom(), ward);
        if (out == Enhancement.Outcome.WARDED && !p.getAbilities().instabuild) take(p, GearModule.WARD_SEAL.get(), 1);
        int after = Enhancement.level(stack);
        p.getInventory().setChanged();
        p.containerMenu.broadcastChanges();

        switch (out) {
            case SUCCESS -> {
                p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7f, 1.3f);
                p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f + after * 0.04f);
                if (after >= 7) {
                    Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD,
                            Component.translatable("notify.beyondsolo.forge.great.title", after),
                            Component.translatable("notify.beyondsolo.forge.great.body", stack.getHoverName())));
                }
            }
            case FAILED, WARDED -> p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 0.8f);
            case DOWNGRADED -> p.level().playSound(null, p.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 0.7f);
            default -> {}
        }
        send(p, ForgeActionPayload.REFINE, out, slot, before, after, "");
        return out;
    }

    public static String refuseInfuse(ServerPlayer p, ItemStack stack, SkillElement element) {
        if (!SystemAPI.hasSystem(p)) return "forge.beyondsolo.no_system";
        GearKind kind = GearKind.of(stack);
        if (kind == null || !kind.weapon()) return "forge.beyondsolo.not_weapon";
        if (element == SkillElement.PHYSICAL || !GearModule.CRYSTALS.containsKey(element)) return "forge.beyondsolo.not_gear";
        if (Infusions.of(stack) == element) return "forge.beyondsolo.same_element";
        if (!p.getAbilities().instabuild) {
            if (have(p, GearModule.CRYSTALS.get(element).get()) < 1) return "forge.beyondsolo.no_crystal";
            ItemGrade grade = ItemGrade.of(stack);
            if (Economy.gold(p) < infusionGold(grade == null ? ItemGrade.COMMON : grade)) return "forge.beyondsolo.no_gold";
        }
        return null;
    }

    public static boolean infuse(ServerPlayer p, int slot, int elementOrdinal) {
        if (!validSlot(p, slot) || elementOrdinal < 0 || elementOrdinal >= SkillElement.values().length) return false;
        SkillElement element = SkillElement.values()[elementOrdinal];
        ItemStack stack = p.getInventory().getItem(slot);
        String why = refuseInfuse(p, stack, element);
        if (why != null) {
            send(p, ForgeActionPayload.INFUSE, Enhancement.Outcome.INVALID, slot, -1, elementOrdinal, why);
            return false;
        }
        SkillElement old = Infusions.of(stack);
        if (!p.getAbilities().instabuild) {
            take(p, GearModule.CRYSTALS.get(element).get(), 1);
            ItemGrade grade = ItemGrade.of(stack);
            Economy.spend(p, infusionGold(grade == null ? ItemGrade.COMMON : grade), Economy.Reason.FORGE);
        }
        Infusions.set(stack, element);
        p.getInventory().setChanged();
        p.containerMenu.broadcastChanges();
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1f, 1.2f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.5f);
        send(p, ForgeActionPayload.INFUSE, Enhancement.Outcome.SUCCESS, slot, old == null ? -1 : old.ordinal(), elementOrdinal, "");
        return true;
    }

    private static void send(ServerPlayer p, int action, Enhancement.Outcome out, int slot, int before, int after, String reason) {
        PacketDistributor.sendToPlayer(p, new ForgeResultPayload(action, out.ordinal(), slot, before, after, reason));
        // what is left in storage, for the forge's counts
        com.beyondsolo.crafting.CraftingService.sendStock(p);
    }

    private ForgeService() {}
}
