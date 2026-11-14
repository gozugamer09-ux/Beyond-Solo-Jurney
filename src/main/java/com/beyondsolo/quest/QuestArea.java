package com.beyondsolo.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A place a quest is tied to (a spawner nest, a gate). Objectives with {@code areaOnly} only count inside
 * {@code radius}; going further than {@code leaveRadius} fails the quest (0 = never).
 */
public record QuestArea(ResourceKey<Level> dimension, BlockPos center, int radius, int leaveRadius) {
    public boolean contains(Level level, Vec3 pos) {
        return level.dimension().equals(dimension) && pos.closerThan(Vec3.atCenterOf(center), radius);
    }

    public boolean left(Level level, Vec3 pos) {
        if (leaveRadius <= 0) return false;
        return !level.dimension().equals(dimension) || !pos.closerThan(Vec3.atCenterOf(center), leaveRadius);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", dimension.location().toString());
        tag.put("center", NbtUtils.writeBlockPos(center));
        tag.putInt("radius", radius);
        tag.putInt("leave", leaveRadius);
        return tag;
    }

    static QuestArea load(CompoundTag tag) {
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dim"));
        BlockPos center = NbtUtils.readBlockPos(tag, "center").orElse(BlockPos.ZERO);
        return new QuestArea(ResourceKey.create(Registries.DIMENSION, dim == null ? Level.OVERWORLD.location() : dim),
                center, tag.getInt("radius"), tag.getInt("leave"));
    }
}
