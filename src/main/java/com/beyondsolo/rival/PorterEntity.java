package com.beyondsolo.rival;

import com.beyondsolo.guild.GuildHunterEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * A porter a hunter hired ({@link Hires}): someone of rank E or D with a great pack on their back, who keeps a few
 * steps behind their hunter, gets out of the way of creatures, and picks up what falls around them. Their hunter opens
 * the pack by talking to them; once the job is over they hand it all over. They never fight. Never saved: they step out
 * beside their hunter again (the pack is kept with the hire).
 */
public class PorterEntity extends GuildHunterEntity {
    /** How far a porter goes for something that fell (blocks), and how far from their hunter it may lie. */
    static final double REACH = 10, LEASH = 18;

    /** Server: who hired them, and which hire they are. */
    @Nullable private UUID owner, hire;

    public PorterEntity(EntityType<? extends PorterEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Mob.class, 6f, 1.1, 1.4,
                e -> e instanceof Enemy && e.isAlive() && !e.isInvulnerable()));
        goalSelector.addGoal(2, new GatherGoal());
        goalSelector.addGoal(3, new KeepUpGoal());
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /** Makes this {@code owner}'s porter for {@code h}. */
    void setup(ServerPlayer owner, Hire h) {
        this.owner = owner.getUUID();
        this.hire = h.id;
        double[] s = strength(h.cls, h.level);
        enlist(Hires.CAUSE, h.cls, h.level, s[0], 1f);
        setLook(h.skin, Hires.COLOR);
        setCustomName(Component.literal("[").append(Component.translatable("rival.beyondsolo.porter.tag")).append("] ")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(Hires.COLOR)))
                .append(Component.literal(h.name).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF)))));
        setCustomNameVisible(true);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    @Nullable
    public UUID hire() {
        return hire;
    }

    /** Their hire, while their hunter is on the server. */
    @Nullable
    Hire job() {
        if (!(level() instanceof ServerLevel level) || owner == null || hire == null) return null;
        ServerPlayer p = com.beyondsolo.util.OnlinePlayers.get(level.getServer(), owner);
        return p == null ? null : Hires.record(p).hire(hire);
    }

    // ------------------------------------------------------------------ never a fighter

    @Override
    public boolean canFight(@Nullable LivingEntity e) {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        return false;
    }

    @Override
    public boolean ranged() {
        return false;
    }

    // ------------------------------------------------------------------ the pack

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer p) {
            Hire h = job();
            if (h != null && p.getUUID().equals(owner)) openPack(p, h);
            else p.displayClientMessage(Component.translatable("rival.beyondsolo.porter.not_yours", getDisplayName()), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Opens the pack for their hunter: a chest's window over what they carry. */
    void openPack(ServerPlayer p, Hire h) {
        Pack pack = new Pack(this, h.pack);
        int rows = Math.max(1, Math.min(6, h.pack.size() / 9));
        MenuType<ChestMenu> type = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
        p.openMenu(new SimpleMenuProvider((id, inv, who) -> new ChestMenu(type, id, inv, pack, rows),
                Component.translatable("rival.beyondsolo.porter.pack", h.name)));
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.NEUTRAL, 0.7f, 1.1f);
    }

    /** Puts as much of a stack as fits into the pack: onto stacks of the same first, then into empty slots. */
    static void stow(NonNullList<ItemStack> pack, ItemStack stack) {
        for (int i = 0; i < pack.size() && !stack.isEmpty(); i++) {
            ItemStack s = pack.get(i);
            if (!ItemStack.isSameItemSameComponents(s, stack)) continue;
            int n = Math.min(s.getMaxStackSize() - s.getCount(), stack.getCount());
            if (n <= 0) continue;
            s.grow(n);
            stack.shrink(n);
        }
        for (int i = 0; i < pack.size() && !stack.isEmpty(); i++) {
            if (!pack.get(i).isEmpty()) continue;
            pack.set(i, stack.copy());
            stack.setCount(0);
        }
    }

    /** True if some of the stack would fit. */
    static boolean room(NonNullList<ItemStack> pack, ItemStack stack) {
        for (ItemStack s : pack) {
            if (s.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(s, stack) && s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    /** What lies on the ground that a porter takes: not what a hunter threw down on purpose. */
    boolean wanted(ItemEntity item, NonNullList<ItemStack> pack) {
        if (!item.isAlive() || item.hasPickUpDelay() || item.getItem().isEmpty()) return false;
        if (item.getOwner() instanceof Player) return false;
        return room(pack, item.getItem());
    }

    /** Takes what fell into the pack (all of it, or what fits). */
    void take(ItemEntity item, NonNullList<ItemStack> pack) {
        ItemStack stack = item.getItem().copy();
        int before = stack.getCount();
        stow(pack, stack);
        int taken = before - stack.getCount();
        if (taken <= 0) return;
        take(item, taken);
        if (stack.isEmpty()) item.discard();
        else item.setItem(stack);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3f, 0.8f + random.nextFloat() * 0.3f);
    }

    /** The pack as a chest's contents for the window; it stays open while the porter is there and near. */
    private record Pack(PorterEntity porter, NonNullList<ItemStack> items) implements Container {
        @Override
        public int getContainerSize() {
            return items.size();
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack s : items) if (!s.isEmpty()) return false;
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return ContainerHelper.removeItem(items, slot, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(items, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot >= 0 && slot < items.size()) items.set(slot, stack);
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return porter.isAlive() && !porter.isRemoved() && porter.level() == player.level() && porter.distanceToSqr(player) < 8 * 8;
        }

        @Override
        public void clearContent() {
            items.replaceAll(s -> ItemStack.EMPTY);
        }
    }

    // ------------------------------------------------------------------ goals

    /** Goes for what fell near them (and near their hunter) and puts it in the pack. */
    private final class GatherGoal extends Goal {
        @Nullable private ItemEntity item;
        private int scan;

        GatherGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (--scan > 0) return false;
            scan = 10;
            Hire h = job();
            if (h == null) return false;
            ServerPlayer p = Hires.CAUSE.leader(PorterEntity.this);
            if (p == null) return false;
            item = null;
            double best = REACH * REACH;
            for (ItemEntity e : level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(REACH, 4, REACH), e -> wanted(e, h.pack))) {
                if (e.distanceToSqr(p) > LEASH * LEASH) continue;
                double d = e.distanceToSqr(PorterEntity.this);
                if (d < best) {
                    best = d;
                    item = e;
                }
            }
            return item != null;
        }

        @Override
        public boolean canContinueToUse() {
            Hire h = job();
            return item != null && h != null && item.isAlive() && wanted(item, h.pack) && distanceToSqr(item) < (REACH + 4) * (REACH + 4);
        }

        @Override
        public void stop() {
            item = null;
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Hire h = job();
            if (item == null || h == null) return;
            getLookControl().setLookAt(item, 30, 30);
            if (distanceToSqr(item) < 1.8 * 1.8) {
                take(item, h.pack);
                item = null;
                scan = 2;
                return;
            }
            if (getNavigation().isDone() || tickCount % 10 == 0) getNavigation().moveTo(item, 1.25);
        }
    }

    /** Keeps a few steps behind their hunter; steps out beside them when left far behind. */
    private final class KeepUpGoal extends Goal {
        private int recompute;

        KeepUpGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            ServerPlayer p = Hires.CAUSE.leader(PorterEntity.this);
            if (p == null) return false;
            if (distanceToSqr(p) > 34 * 34) {
                Hires.CAUSE.catchUp(PorterEntity.this, p);
                return false;
            }
            return distanceToSqr(p) > 4.5 * 4.5;
        }

        @Override
        public boolean canContinueToUse() {
            ServerPlayer p = Hires.CAUSE.leader(PorterEntity.this);
            return p != null && !getNavigation().isDone() && distanceToSqr(p) > 3 * 3;
        }

        @Override
        public void start() {
            recompute = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            ServerPlayer p = Hires.CAUSE.leader(PorterEntity.this);
            if (p == null) return;
            getLookControl().setLookAt(p, 10, getMaxHeadXRot());
            if (--recompute > 0) return;
            recompute = 10;
            getNavigation().moveTo(p, p.isSprinting() ? 1.45 : 1.2);
        }
    }
}
