package com.beyondsolo.registry;

import com.beyondsolo.BeyondSolo;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components. */
public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE, BeyondSolo.MOD_ID);

    /** The skill a skill tome teaches (path, e.g. "rending_arc"). Missing = an unread tome that rolls one. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SKILL = COMPONENTS.registerComponentType("skill",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private ModComponents() {}
}
