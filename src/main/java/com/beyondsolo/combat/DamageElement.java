package com.beyondsolo.combat;

import com.beyondsolo.BeyondSolo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Damage elements (fire, poison, crimson, blood, ...). This is only the list of names and colours;
 * the combat module decides what each one does (status effects, which cures work, etc.).
 * Matching vanilla damage types live in {@code data/beyondsolo/damage_type/}.
 */
public record DamageElement(ResourceLocation id, int color) {
    private static final Map<ResourceLocation, DamageElement> REGISTRY = new LinkedHashMap<>();

    public static final DamageElement PHYSICAL = register("physical", 0xD0D0D0);
    public static final DamageElement FIRE = register("fire", 0xFF7A2E);
    public static final DamageElement POISON = register("poison", 0x7CCF3A);
    public static final DamageElement CRIMSON = register("crimson", 0xC2185B);
    public static final DamageElement BLOOD = register("blood", 0x8B0000);

    public static DamageElement register(String name, int color) {
        return register(new DamageElement(BeyondSolo.id(name), color));
    }

    public static synchronized DamageElement register(DamageElement element) {
        REGISTRY.put(element.id(), element);
        return element;
    }

    public static DamageElement get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Collection<DamageElement> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /** Position in {@link #all()}, for packets. */
    public int index() {
        int i = 0;
        for (DamageElement e : REGISTRY.values()) {
            if (e == this) return i;
            i++;
        }
        return 0;
    }

    public static DamageElement byIndex(int index) {
        int i = 0;
        for (DamageElement e : REGISTRY.values()) {
            if (i++ == index) return e;
        }
        return PHYSICAL;
    }

    public Component displayName() {
        return Component.translatable("element." + id.getNamespace() + "." + id.getPath());
    }
}
