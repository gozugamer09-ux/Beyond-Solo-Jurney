package com.beyondsolo.home;

import com.beyondsolo.worldboss.WorldBossKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What a trophy pedestal shows: which great threat it is for, and how many times the home's owner has slain it (0: not
 * yet, an empty pedestal). Set by {@link Homes#refreshTrophies} from the owner's record; sent to everyone who sees it.
 */
public class TrophyPedestalBlockEntity extends BlockEntity {
    @Nullable private WorldBossKind kind;
    private int slain;

    public TrophyPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(HomeModule.PEDESTAL_ENTITY.get(), pos, state);
    }

    public @Nullable WorldBossKind kind() {
        return kind;
    }

    public int slain() {
        return slain;
    }

    void setKind(WorldBossKind kind) {
        if (this.kind == kind) return;
        this.kind = kind;
        changed();
    }

    void setSlain(int slain) {
        if (this.slain == slain) return;
        this.slain = slain;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (kind != null) tag.putString("kind", kind.name());
        tag.putInt("slain", slain);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        kind = tag.contains("kind") ? WorldBossKind.byId(tag.getString("kind")) : null;
        slain = tag.getInt("slain");
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
}
