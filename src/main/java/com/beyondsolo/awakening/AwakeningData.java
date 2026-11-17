package com.beyondsolo.awakening;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.UUID;

/**
 * Where one player is on the road to the System (kept through death):
 * {@code DORMANT} (no System; their next gate of rank D or higher hides the sealed door), {@code CALLED} (the door's
 * gate was cleared: the door has opened, or is about to, and the cave won't let them leave), {@code TRIAL} (through
 * it), {@code AWAKENING} (the binding is playing), {@code DONE}.
 */
public final class AwakeningData implements INBTSerializable<CompoundTag> {
    public enum Stage { DORMANT, CALLED, TRIAL, AWAKENING, DONE }

    private Stage stage = Stage.DORMANT;
    @Nullable private UUID trial;
    @Nullable private BlockPos sealedPos;
    @Nullable private ResourceKey<Level> sealedDim;
    private boolean arbiterSlain;
    private boolean hinted;

    public Stage stage() { return stage; }
    public void setStage(Stage stage) { this.stage = stage; }
    @Nullable public UUID trial() { return trial; }
    @Nullable public BlockPos sealedPos() { return sealedPos; }
    @Nullable public ResourceKey<Level> sealedDim() { return sealedDim; }
    public boolean arbiterSlain() { return arbiterSlain; }
    public void setArbiterSlain(boolean slain) { this.arbiterSlain = slain; }
    public boolean hinted() { return hinted; }
    public void setHinted(boolean hinted) { this.hinted = hinted; }

    public void setSealedGate(@Nullable UUID trial, @Nullable ResourceKey<Level> dim, @Nullable BlockPos pos) {
        this.trial = trial;
        this.sealedDim = dim;
        this.sealedPos = pos == null ? null : pos.immutable();
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("stage", stage.name());
        if (trial != null) tag.putUUID("trial", trial);
        if (sealedPos != null) tag.put("sealedPos", NbtUtils.writeBlockPos(sealedPos));
        if (sealedDim != null) tag.putString("sealedDim", sealedDim.location().toString());
        tag.putBoolean("arbiterSlain", arbiterSlain);
        tag.putBoolean("hinted", hinted);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        try {
            stage = Stage.valueOf(tag.getString("stage"));
        } catch (IllegalArgumentException e) {
            stage = Stage.DORMANT;
        }
        trial = tag.hasUUID("trial") ? tag.getUUID("trial") : null;
        sealedPos = NbtUtils.readBlockPos(tag, "sealedPos").orElse(null);
        sealedDim = tag.contains("sealedDim") ? ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("sealedDim"))) : null;
        arbiterSlain = tag.getBoolean("arbiterSlain");
        hinted = tag.getBoolean("hinted");
    }
}
