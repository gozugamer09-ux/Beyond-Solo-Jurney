package com.beyondsolo.client.combat;

import com.beyondsolo.BeyondSolo;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IActualAnimation;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.SpeedModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Full-body animations for players, played through playerAnimator (the library Better Combat already ships).
 * Animations live in {@code assets/beyondsolo/player_animations}. Each player gets one layer of ours, above Better
 * Combat's attack layer, so an art's pose wins over a swing that happens to be running.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class BodyAnimator {
    private static final ResourceLocation LAYER = BeyondSolo.id("body");
    /** Better Combat plays attacks at 1000 or so; ours sits above it. */
    private static final int PRIORITY = 2000;

    /** Each player's layer runs through a speed modifier, so a strike can hang (see {@link #hold}). */
    private static final java.util.Map<AbstractClientPlayer, SpeedModifier> SPEED = new java.util.WeakHashMap<>();
    /** Players whose body hangs on a strike, and until when. */
    private static final java.util.Map<AbstractClientPlayer, Double> HELD = new java.util.WeakHashMap<>();

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> {
            SpeedModifier speed = new SpeedModifier(1f);
            SPEED.put(player, speed);
            return new ModifierLayer<>(null, speed);
        });
    }

    /**
     * The player's animation hangs for {@code seconds} where it is, as a blade hangs in what it struck (an art's
     * strike frame), then carries on where it stopped.
     */
    public static void hold(AbstractClientPlayer player, float seconds) {
        SpeedModifier speed = SPEED.get(player);
        if (speed == null || seconds <= 0) return;
        HELD.merge(player, com.beyondsolo.client.gfx.Anim.now() + seconds, Math::max);
        speed.speed = 0.02f;
    }

    /** Lets go of bodies whose hold is over. */
    @SubscribeEvent
    static void release(RenderFrameEvent.Pre event) {
        if (HELD.isEmpty()) return;
        double now = com.beyondsolo.client.gfx.Anim.now();
        var it = HELD.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getValue() > now) continue;
            SpeedModifier speed = SPEED.get(e.getKey());
            if (speed != null) speed.speed = 1f;
            it.remove();
        }
    }

    /** Plays {@code assets/beyondsolo/player_animations/<name>.json} on the player, blending in over a few ticks. */
    public static boolean play(AbstractClientPlayer player, String name) {
        IAnimation stored = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (!(stored instanceof ModifierLayer<?> raw)) return false;
        var playable = PlayerAnimationRegistry.getAnimation(BeyondSolo.id(name));
        if (playable == null) {
            BeyondSolo.LOGGER.warn("Missing player animation {}", name);
            return false;
        }
        IActualAnimation<?> anim = playable.playAnimation();
        // first person: the arms and the held item follow the animation, so the wielder sees the art too
        anim.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
        anim.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, false));
        @SuppressWarnings("unchecked")
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) raw;
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(3, Ease.OUTQUAD), anim);
        return true;
    }

    private BodyAnimator() {}
}
