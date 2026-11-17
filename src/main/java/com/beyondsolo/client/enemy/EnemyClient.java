package com.beyondsolo.client.enemy;

import com.beyondsolo.enemy.EnemyTraits;
import com.beyondsolo.enemy.Affinity;
import com.beyondsolo.enemy.Variant;
import com.beyondsolo.network.EnemyFxPayload;
import com.beyondsolo.network.EnemyTraitsPayload;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/** The client's view of the enemy roster: traits and hidden levels of the creatures in sight, and incoming world effects. */
public final class EnemyClient {
    private static final Int2ObjectMap<EnemyTraits> TRAITS = new Int2ObjectOpenHashMap<>();
    private static final Int2IntMap LEVELS = new Int2IntOpenHashMap();

    static {
        LEVELS.defaultReturnValue(-1);
    }

    public static void onFx(EnemyFxPayload p) {
        EnemyWorldFx.add(p);
    }

    public static void onTraits(EnemyTraitsPayload p) {
        TRAITS.put(p.entity(), new EnemyTraits(Affinity.byIndex(p.affinity()), Variant.byIndex(p.variant()), p.affixes()));
        LEVELS.put(p.entity(), p.level());
    }

    @Nullable
    public static EnemyTraits traits(int entityId) {
        return TRAITS.get(entityId);
    }

    /** A creature's hidden level as the server sent it, or -1 if it has none (not one of the System's creatures). */
    public static int level(int entityId) {
        return LEVELS.get(entityId);
    }

    static Int2ObjectMap<EnemyTraits> all() {
        return TRAITS;
    }

    /** Forgets creatures that are gone. */
    static void prune() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRAITS.clear();
            LEVELS.clear();
            return;
        }
        TRAITS.int2ObjectEntrySet().removeIf(e -> {
            var ent = mc.level.getEntity(e.getIntKey());
            return ent == null || ent.isRemoved();
        });
        LEVELS.int2IntEntrySet().removeIf(e -> !TRAITS.containsKey(e.getIntKey()));
    }

    static void clear() {
        TRAITS.clear();
        LEVELS.clear();
        EnemyWorldFx.clear();
    }

    private EnemyClient() {}
}
