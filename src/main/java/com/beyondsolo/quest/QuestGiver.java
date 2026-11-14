package com.beyondsolo.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * The villager or other NPC that gave a quest.
 *
 * @param entity the giver's entity UUID (to find them again for the hand-in)
 * @param name   what the UI calls them, e.g. "Mira the Farmer"
 * @param pos    where they were when they gave it (for the tracker's distance)
 * @param turnIn true if the quest has to be brought back to them; false if it completes on its own
 */
public record QuestGiver(UUID entity, Component name, BlockPos pos, ResourceKey<Level> dimension, boolean turnIn) {
    public boolean is(UUID id) {
        return entity.equals(id);
    }

    public QuestGiver withTurnIn(boolean value) {
        return new QuestGiver(entity, name, pos, dimension, value);
    }

    CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("entity", entity);
        tag.putString("name", Component.Serializer.toJson(name, provider));
        tag.put("pos", NbtUtils.writeBlockPos(pos));
        tag.putString("dim", dimension.location().toString());
        tag.putBoolean("turnIn", turnIn);
        return tag;
    }

    static QuestGiver load(CompoundTag tag, HolderLookup.Provider provider) {
        ResourceLocation dim = ResourceLocation.tryParse(tag.getString("dim"));
        return new QuestGiver(tag.hasUUID("entity") ? tag.getUUID("entity") : new UUID(0, 0),
                Quest.readComponent(tag.getString("name"), provider),
                NbtUtils.readBlockPos(tag, "pos").orElse(BlockPos.ZERO),
                ResourceKey.create(Registries.DIMENSION, dim == null ? Level.OVERWORLD.location() : dim),
                tag.getBoolean("turnIn"));
    }
}
