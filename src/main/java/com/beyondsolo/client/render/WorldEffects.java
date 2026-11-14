package com.beyondsolo.client.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Effects that exist only on this client: nothing here touches the world or other players. */
public final class WorldEffects {
    // The server never hands out negative entity ids, so ours cannot collide with a real entity.
    private static int nextLocalId = -1000;

    /**
     * A lightning bolt for show: the bolt, the flash in the sky and the thunder, and nothing else.
     * A bolt spawned on the server (even a "visual only" one) would count as a real strike: it hands out the
     * vanilla lightning advancement to everyone nearby, charges lightning rods and cleans copper.
     */
    public static void lightning(Level level, Vec3 at) {
        if (!(level instanceof ClientLevel client)) return;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(client);
        if (bolt == null) return;
        bolt.setId(nextLocalId--);
        bolt.moveTo(at);
        bolt.setVisualOnly(true);
        client.addEntity(bolt);
    }

    private WorldEffects() {}
}
