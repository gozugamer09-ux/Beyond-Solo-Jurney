package com.beyondsolo.client.gate;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.gate.GateRealm;
import com.beyondsolo.gate.GateService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The air of the realm behind the gate the player is in: the colour of its fog (it fades in after arriving), how far
 * it lets you see, and what drifts in it now and then (ash, snow, spores, motes). The server says which realm with a
 * {@code GatePayload.REALM}; outside the gate dimension there is none.
 *
 * <p>The fog colour is set at normal priority, so anything tinting it at low priority (the gate's weather) works on
 * top of the realm's. The light takes on the realm's colour too ({@link #tintLight}).</p>
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class GateAtmosphere {
    /** How far you can see in a gate whose realm is not known. */
    public static final float DEFAULT_FAR = 44f;

    @Nullable private static GateRealm realm;
    /** 0..1: how far the realm's fog colour has come in. */
    private static float blend;

    private GateAtmosphere() {}

    /** The realm the player is in now (its ordinal; -1: none). */
    public static void set(int ordinal) {
        GateRealm next = ordinal < 0 ? null : GateRealm.byIndex(ordinal);
        if (next != realm) blend = 0;
        realm = next;
    }

    @Nullable
    public static GateRealm realm() {
        return inGate() ? realm : null;
    }

    /** How far the fog lets you see in the gate dimension now. */
    public static float fogFar() {
        GateRealm r = realm();
        return r == null ? DEFAULT_FAR : r.fogFar;
    }

    private static boolean inGate() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension().equals(GateService.DIMENSION);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    static void fogColor(ViewportEvent.ComputeFogColor event) {
        GateRealm r = realm();
        if (r == null) return;
        int c = r.fogColor;
        float k = blend * blend * (3 - 2 * blend);
        event.setRed(Mth.lerp(k, event.getRed(), ((c >> 16) & 255) / 255f));
        event.setGreen(Mth.lerp(k, event.getGreen(), ((c >> 8) & 255) / 255f));
        event.setBlue(Mth.lerp(k, event.getBlue(), (c & 255) / 255f));
    }

    /**
     * The light of the realm (the gate dimension's lightmap, see {@code GateClientEvents}): lamps and fire take on its
     * {@link GateRealm#glow} instead of the usual warm torchlight, as bright as before. Fades in with the fog.
     */
    public static void tintLight(Vector3f colors) {
        GateRealm r = realm();
        if (r == null || r.glow == 0) return;
        float l = Math.max(colors.x, Math.max(colors.y, colors.z));
        if (l <= 0) return;
        int c = r.glow;
        float cr = ((c >> 16) & 255) / 255f, cg = ((c >> 8) & 255) / 255f, cb = (c & 255) / 255f;
        float m = Math.max(cr, Math.max(cg, cb));
        float k = 0.85f * blend * blend * (3 - 2 * blend);
        colors.set(Mth.lerp(k, colors.x, l * cr / m), Mth.lerp(k, colors.y, l * cg / m), Mth.lerp(k, colors.z, l * cb / m));
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if (!inGate()) {
            // died or left some other way: whatever realm it was is behind us
            realm = null;
            return;
        }
        if (realm == null) return;
        blend = Math.min(1f, blend + 0.02f);
        ParticleOptions air = realm.ambience;
        if (air == null) return;
        RandomSource r = mc.level.random;
        for (int i = 0; i < 2; i++) {
            if (r.nextBoolean()) continue;
            double x = mc.player.getX() + (r.nextDouble() - 0.5) * 28;
            double y = mc.player.getY() + (r.nextDouble() - 0.3) * 12;
            double z = mc.player.getZ() + (r.nextDouble() - 0.5) * 28;
            if (!mc.level.getBlockState(BlockPos.containing(x, y, z)).isAir()) continue;
            double vx = (r.nextDouble() - 0.5) * 0.02, vy = 0, vz = (r.nextDouble() - 0.5) * 0.02;
            if (air == ParticleTypes.SNOWFLAKE) vy = -0.04;
            else if (air == ParticleTypes.ASH || air == ParticleTypes.WHITE_ASH) vy = -0.01;
            else if (air == ParticleTypes.SOUL || air == ParticleTypes.GLOW) vy = 0.015;
            mc.level.addParticle(air, x, y, z, vx, vy, vz);
        }
    }

    @SubscribeEvent
    static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        realm = null;
        blend = 0;
    }
}
