package com.beyondsolo.quest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Matches an objective's target string against what happened, and names it for the quest text. */
public final class Targets {
    public static final String HOSTILE = "hostile";

    public static boolean entity(String target, Entity entity) {
        if (target.isEmpty()) return true;
        if (target.equals(HOSTILE)) return entity instanceof Enemy;
        if (target.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(target.substring(1));
            return id != null && entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, id));
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(target);
    }

    public static boolean block(String target, BlockState state) {
        if (target.isEmpty()) return true;
        if (target.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(target.substring(1));
            return id != null && state.is(TagKey.create(Registries.BLOCK, id));
        }
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(target);
    }

    public static boolean item(String target, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (target.isEmpty()) return true;
        if (target.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(target.substring(1));
            return id != null && stack.is(TagKey.create(Registries.ITEM, id));
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(target);
    }

    /** A readable name for a target: the entity, block or item name, or a lang key for tags. */
    public static Component name(ObjectiveType type, String target) {
        if (target.isEmpty() || target.equals(HOSTILE)) return Component.translatable("quest_target.beyondsolo.hostile");
        if (target.startsWith("#")) {
            return Component.translatable("quest_target.beyondsolo." + target.substring(1).replace(':', '.').replace('/', '.'));
        }
        ResourceLocation id = ResourceLocation.tryParse(target);
        if (id == null) return Component.literal(target);
        return switch (type) {
            case KILL -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(t -> t.getDescription()).orElse(Component.literal(target));
            case BREAK -> BuiltInRegistries.BLOCK.getOptional(id).map(b -> (Component) b.getName()).orElse(Component.literal(target));
            case COLLECT -> BuiltInRegistries.ITEM.getOptional(id).map(i -> i.getDescription()).orElse(Component.literal(target));
            default -> Component.literal(target);
        };
    }

    private Targets() {}
}
