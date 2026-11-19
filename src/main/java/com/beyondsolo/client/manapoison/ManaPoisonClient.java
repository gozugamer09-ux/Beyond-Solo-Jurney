package com.beyondsolo.client.manapoison;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gfx.Anim;
import com.beyondsolo.client.gfx.Gfx;
import com.beyondsolo.client.gfx.Shapes;
import com.beyondsolo.manapoison.ManaPoisoning;
import com.beyondsolo.network.ManaPoisonPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Mana poisoning on screen: what the server says of it ({@link ManaPoisonPayload}), the meter the vitals HUD draws
 * ({@code client.hud.VitalsHud}) and the haze at the screen's edges: violet that thickens with each stage and beats
 * at the worst, a flash when the mana bursts, a teal glow at the edges while a purifying crystal draws it out.
 */
public final class ManaPoisonClient {
    public static final int VIOLET = 0xFF8E4BFF, VIOLET_DEEP = 0xFF4A1C9C, PALE = 0xFF8FF5E6, TEAL = 0xFF1FB89F;

    private static float level;
    private static float shown;
    private static int flags;
    private static double burstAt = -10;
    private static double stageAt = -10;

    public static void onSync(ManaPoisonPayload p) {
        int was = ManaPoisoning.stage(level);
        level = p.level();
        flags = p.flags();
        int now = ManaPoisoning.stage(level);
        if ((flags & ManaPoisoning.BURST) != 0) {
            burstAt = Anim.now();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.7f, 1f));
        }
        if (now > was) stageAt = Anim.now();
    }

    /** How poisoned the hunter is, 0..100. */
    public static float level() {
        return level;
    }

    /** The level eased towards what the server said, for bars. */
    public static float shown() {
        return shown;
    }

    public static int stage() {
        return ManaPoisoning.stage(level);
    }

    /** In a gate whose mana is poisoning the hunter. */
    public static boolean dense() {
        return (flags & ManaPoisoning.DENSE) != 0;
    }

    /** A purifying crystal is drawing it out. */
    public static boolean purifying() {
        return (flags & ManaPoisoning.PURIFYING) != 0;
    }

    public static boolean clearBreath() {
        return (flags & ManaPoisoning.CLEAR) != 0;
    }

    /** Whether the meter should be up: poisoned at all, or breathing poisoning mana. */
    public static boolean meter() {
        return level > 0.05f || dense();
    }

    /** Seconds since a stage was last reached (for a flash on the meter). */
    public static float sinceStage() {
        return Anim.since(stageAt);
    }

    static void reset() {
        level = shown = 0;
        flags = 0;
        burstAt = stageAt = -10;
    }

    /** The haze at the screen's edges. */
    static void renderHaze(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        shown = Anim.approach(shown, level, 6f);
        if (mc.player == null || mc.options.hideGui) return;
        float w = g.guiWidth(), h = g.guiHeight();
        int stage = stage();
        if (shown > 0.05f) {
            // a violet tint creeping in from the edges, stronger with each stage (it reaches full strength at the edge)
            float base = switch (stage) {
                case 0 -> 0.22f * shown / ManaPoisoning.SICK;
                case 1 -> 0.30f;
                case 2 -> 0.42f;
                default -> 0.52f;
            };
            // the worst of it beats like a heart
            float beat = stage >= 3 ? Anim.pulse(0.9f) : stage == 2 ? Anim.pulse(1.6f) * 0.5f : 0;
            float a = base + beat * 0.15f;
            Shapes.vignette(0, 0, w, h, 0.5f - stage * 0.06f, 1.08f).fill(Gfx.alpha(Gfx.mix(VIOLET_DEEP, VIOLET, 0.45f + 0.25f * beat), a)).draw(g);
            if (stage >= 2) {
                // and the edges darken, as if the eyes were failing
                Shapes.vignette(0, 0, w, h, 0.62f, 1.1f).fill(Gfx.alpha(0xFF14051F, 0.22f + 0.16f * beat)).draw(g);
            }
        }
        float flash = 1 - Anim.progress(burstAt, 0.9f);
        if (flash > 0) {
            Shapes.box(0, 0, w, h).fill(Gfx.alpha(0xFFE9D6FF, 0.28f * flash * flash)).draw(g);
            Shapes.vignette(0, 0, w, h, 0.1f, 1.3f).fill(Gfx.alpha(VIOLET, 0.5f * flash)).draw(g);
        }
        if (purifying()) {
            // a cool teal at the edges, breathing slowly: added light, so a deep teal (a pale one washes the view white)
            float glow = 0.22f + 0.1f * Anim.pulse(2.2f);
            Shapes.vignette(0, 0, w, h, 0.6f, 1.1f).fill(Gfx.alpha(TEAL, glow)).additive().draw(g);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerBelowAll(BeyondSolo.id("mana_haze"), ManaPoisonClient::renderHaze);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            reset();
        }
    }

    private ManaPoisonClient() {}
}
