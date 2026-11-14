package com.beyondsolo.gate;

import com.beyondsolo.notify.Notifications;
import com.beyondsolo.notify.SystemNotification;
import com.beyondsolo.registry.ModBlockEntities;
import com.beyondsolo.system.PlayerRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * The boss's reward: comes down out of a beam of light (the renderer plays the fall from {@link #droppedAt}), lands
 * with a ring of light, and waits. Opening it plays the lid animation, then bursts the loot out in a fountain and
 * lists it in a pop-up. It fades away a little while later.
 */
public class RewardCacheBlockEntity extends BlockEntity implements GeoBlockEntity {
    public static final int DROP_TICKS = 34;
    public static final int BURST_DELAY = 16;
    public static final int LINGER_TICKS = 20 * 12;

    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.beyondsolo.reward_cache.idle");
    public static final RawAnimation OPEN = RawAnimation.begin().thenPlayAndHold("animation.beyondsolo.reward_cache.open");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private PlayerRank rank = PlayerRank.E;
    private long droppedAt = -1;
    private long openedAt = -1;
    private boolean burst;

    public RewardCacheBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REWARD_CACHE.get(), pos, state);
    }

    public PlayerRank rank() { return rank; }
    public long droppedAt() { return droppedAt; }
    public long openedAt() { return openedAt; }
    public boolean isOpen() { return openedAt >= 0; }

    public void setup(PlayerRank rank, long droppedAt) {
        this.rank = rank;
        this.droppedAt = droppedAt;
        changed();
    }

    /** 0 while falling from the sky, 1 once it has landed. */
    public float landed(float partialTick) {
        if (level == null || droppedAt < 0) return 1f;
        return Mth.clamp((level.getGameTime() + partialTick - droppedAt) / DROP_TICKS, 0f, 1f);
    }

    public void open(ServerPlayer player) {
        if (openedAt >= 0 || level == null || landed(0) < 1f) return;
        openedAt = level.getGameTime();
        changed();
        level.playSound(null, worldPosition, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.2f, 0.8f);
        level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.5f, 1.2f);
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RewardCacheBlockEntity cache) {
        if (!(level instanceof ServerLevel server)) return;
        long now = level.getGameTime();
        if (cache.droppedAt >= 0 && now - cache.droppedAt == DROP_TICKS) {
            server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 30, 0.8, 0.05, 0.8, 0.12);
            level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.7f, 0.6f);
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1f, 1.4f);
        }
        if (cache.openedAt < 0) return;
        if (!cache.burst && now - cache.openedAt >= BURST_DELAY) {
            cache.burst = true;
            cache.burst(server);
        }
        if (now - cache.openedAt >= LINGER_TICKS) {
            server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 25, 0.4, 0.4, 0.4, 0.06);
            level.removeBlock(pos, false);
        }
    }

    private void burst(ServerLevel server) {
        List<ItemStack> loot = GateLoot.roll(rank, server.random);
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 1.0, z = worldPosition.getZ() + 0.5;
        for (int i = 0; i < loot.size(); i++) {
            double a = i * Math.PI * 2 / loot.size() + server.random.nextDouble() * 0.4;
            ItemEntity item = new ItemEntity(server, x, y, z, loot.get(i).copy());
            item.setDeltaMovement(Math.cos(a) * 0.17, 0.42 + server.random.nextDouble() * 0.12, Math.sin(a) * 0.17);
            item.setPickUpDelay(18);
            item.setGlowingTag(true);
            server.addFreshEntity(item);
        }
        server.sendParticles(ParticleTypes.FIREWORK, x, y, z, 40, 0.3, 0.3, 0.3, 0.25);
        server.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1.5f);

        MutableComponent list = Component.empty();
        for (int i = 0; i < loot.size(); i++) {
            if (i > 0) list.append(", ");
            list.append(Component.literal(loot.get(i).getCount() + "x ")).append(loot.get(i).getHoverName());
        }
        for (ServerPlayer p : server.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(32))) {
            Notifications.send(p, SystemNotification.of(SystemNotification.Kind.REWARD,
                    Component.translatable("notify.beyondsolo.gate.cache.title"), list));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("rank", rank.name());
        tag.putLong("droppedAt", droppedAt);
        tag.putLong("openedAt", openedAt);
        tag.putBoolean("burst", burst);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        PlayerRank r = PlayerRank.byName(tag.getString("rank"));
        rank = r == null ? PlayerRank.E : r;
        droppedAt = tag.contains("droppedAt") ? tag.getLong("droppedAt") : -1;
        openedAt = tag.contains("openedAt") ? tag.getLong("openedAt") : -1;
        burst = tag.getBoolean("burst");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "cache", 0, state -> state.setAndContinue(openedAt >= 0 ? OPEN : IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
