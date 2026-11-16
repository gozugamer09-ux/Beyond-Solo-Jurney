package com.beyondsolo.client.rune;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.rune.Runes;
import dev.kosmx.playerAnim.api.layered.IActualAnimation;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * The body while a rune is absorbed, through playerAnimator on a layer of its own: the stone is raised to the chest
 * in both hands and the body tenses as it cracks ({@code rune_channel}); when it shatters the arms fly open and the
 * head goes back as the light pours in ({@code rune_release}). Let go early and the pose eases away. Third person and
 * other players only: in first person the hands are drawn by {@link RuneClientEvents}' hand transform.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class RuneAnimator {
    private static final ResourceLocation LAYER = BeyondSolo.id("rune");
    /** Above Better Combat's attacks (about 1000) and our own body layer (2000). */
    private static final int PRIORITY = 2100;
    /** Players in the channel pose: entity id -> ticks they had held the stone when last seen. */
    private static final Map<Integer, Integer> CHANNELING = new HashMap<>();

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> new ModifierLayer<>());
    }

    /** Starts and stops the channel pose as players start and stop holding a rune stone. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            boolean using = p.isUsingItem() && Runes.isRune(p.getUseItem());
            Integer held = CHANNELING.get(p.getId());
            if (using) {
                if (held == null) play(p, "rune_channel", 3);
                CHANNELING.put(p.getId(), p.getTicksUsingItem());
            } else if (held != null) {
                CHANNELING.remove(p.getId());
                // a stone held to the end is about to shatter: the release takes over from the pose
                if (held < Runes.USE_TICKS - 3) stop(p);
            }
        }
        CHANNELING.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    /** The stone shattered in this player's hands. */
    static void release(Entity e) {
        if (!(e instanceof AbstractClientPlayer p)) return;
        CHANNELING.remove(p.getId());
        play(p, "rune_release", 2);
    }

    private static void play(AbstractClientPlayer player, String name, int fade) {
        ModifierLayer<IAnimation> layer = layer(player);
        if (layer == null) return;
        var playable = PlayerAnimationRegistry.getAnimation(BeyondSolo.id(name));
        if (playable == null) {
            BeyondSolo.LOGGER.warn("Missing player animation {}", name);
            return;
        }
        IActualAnimation<?> anim = playable.playAnimation();
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fade, Ease.OUTQUAD), anim);
    }

    private static void stop(AbstractClientPlayer player) {
        ModifierLayer<IAnimation> layer = layer(player);
        if (layer != null && layer.getAnimation() != null) layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(5, Ease.INOUTSINE), null);
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layer(AbstractClientPlayer player) {
        IAnimation stored = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        return stored instanceof ModifierLayer<?> raw ? (ModifierLayer<IAnimation>) raw : null;
    }

    static void clear() {
        CHANNELING.clear();
    }

    private RuneAnimator() {}
}
