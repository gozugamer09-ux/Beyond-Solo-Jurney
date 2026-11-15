package com.beyondsolo.client.enemy;

import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.network.EnemyFxPayload;
import com.beyondsolo.network.EnemyTraitsPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/** The client's view of the enemy roster: traits of the creatures in sight, and incoming world effects. */
public final class EnemyClient {
    private static final Int2ObjectMap<EnemyTraits> TRAITS = new Int2ObjectOpenHashMap<>();

    public static void onFx(EnemyFxPayload p) {
        EnemyWorldFx.add(p);
    }

    public static void onTraits(EnemyTraitsPayload p) {
        TRAITS.put(p.entity(), new EnemyTraits(Affinity.byIndex(p.affinity()), Variant.byIndex(p.variant()), p.affixes()));
    }

    @Nullable
    public static EnemyTraits traits(int entityId) {
        return TRAITS.get(entityId);
    }

    static Int2ObjectMap<EnemyTraits> all() {
        return TRAITS;
    }

    /** Forgets creatures that are gone. */
    static void prune() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRAITS.clear();
            return;
        }
        TRAITS.int2ObjectEntrySet().removeIf(e -> {
            var ent = mc.level.getEntity(e.getIntKey());
            return ent == null || ent.isRemoved();
        });
    }

    static void clear() {
        TRAITS.clear();
        EnemyWorldFx.clear();
    }

    private EnemyClient() {}
}
