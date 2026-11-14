package com.beyondsolo.client.combat;

import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.network.CombatStatePayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** What this client knows about each entity's buildups and statuses (from {@link CombatStatePayload}). */
public final class ClientCombatState {
    public static final class State {
        public CombatStatePayload data;
        /** Eased meter values for drawing: burn, venom, crimson, blood. */
        public final float[] shown = new float[4];
        /** 0..1, fades the over-head meters in and out. */
        public float visible;
        /** Real time of the last change that made something go up. */
        public double lastRise = Anim.now();

        public float target(int i) {
            return switch (i) {
                case 0 -> data.burn();
                case 1 -> data.venom();
                case 2 -> data.crimson();
                default -> data.blood();
            };
        }
    }

    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();

    public static void update(CombatStatePayload payload) {
        State s = STATES.get(payload.entityId());
        if (s == null) {
            s = new State();
            STATES.put(payload.entityId(), s);
        } else if (payload.burn() > s.data.burn() || payload.venom() > s.data.venom() || payload.crimson() > s.data.crimson()
                || payload.blood() > s.data.blood() || payload.flags() != s.data.flags() || payload.venomStacks() != s.data.venomStacks()) {
            s.lastRise = Anim.now();
        }
        s.data = payload;
    }

    public static @Nullable State get(Entity entity) {
        return STATES.get(entity.getId());
    }

    public static @Nullable CombatStatePayload data(Entity entity) {
        State s = STATES.get(entity.getId());
        return s == null ? null : s.data;
    }

    public static Int2ObjectOpenHashMap<State> all() {
        return STATES;
    }

    /** Drops entities that are gone and states that have fully faded. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATES.clear();
            return;
        }
        STATES.int2ObjectEntrySet().removeIf(e -> {
            Entity entity = mc.level.getEntity(e.getIntKey());
            return entity == null || entity.isRemoved();
        });
    }

    public static void clear() {
        STATES.clear();
    }

    private ClientCombatState() {}
}
