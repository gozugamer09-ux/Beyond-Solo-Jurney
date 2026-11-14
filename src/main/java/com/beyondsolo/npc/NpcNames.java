package com.beyondsolo.npc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;

import java.util.UUID;

/**
 * Villagers have no names, so the System gives each one a stable name from its UUID ("Mira the Farmer").
 * A name tag wins over it.
 */
public final class NpcNames {
    private static final String[] NAMES = {
            "Alden", "Mira", "Tobin", "Ysolde", "Bram", "Corra", "Edric", "Fenna", "Garret", "Hesper",
            "Ivo", "Jessa", "Kael", "Lise", "Marten", "Nell", "Osric", "Petra", "Quill", "Rowan",
            "Sabine", "Tamsin", "Ulric", "Vesna", "Wren", "Yara", "Ansel", "Brisa", "Cael", "Dagny",
            "Elric", "Fae", "Hollis", "Ilsa", "Jory", "Kestra", "Lorne", "Maren", "Niall", "Odette",
            "Perrin", "Rhea", "Soren", "Thea", "Varo", "Willa", "Arlo", "Beck", "Celyn", "Dov"
    };

    public static String firstName(UUID id) {
        return NAMES[Math.floorMod(id.hashCode() * 31 + (int) (id.getLeastSignificantBits() >>> 7), NAMES.length)];
    }

    /** "Mira" or the custom name. */
    public static Component name(Entity e) {
        if (e.hasCustomName() && e.getCustomName() != null) return e.getCustomName();
        return Component.literal(firstName(e.getUUID()));
    }

    /** The job as the UI shows it: "Farmer", "Traveling Merchant"... */
    public static Component job(Entity e) {
        if (e instanceof Villager v) {
            VillagerProfession p = v.getVillagerData().getProfession();
            String key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(p).getPath();
            return Component.translatable("entity.minecraft.villager." + key);
        }
        if (e instanceof WanderingTrader) return Component.translatable("entity.minecraft.wandering_trader");
        return e.getType().getDescription();
    }

    /** "Mira the Farmer". */
    public static Component full(Entity e) {
        return Component.translatable("npc.beyondsolo.giver", name(e), job(e));
    }

    private NpcNames() {}
}
