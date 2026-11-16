package com.beyondsolo.client.key;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.key.DungeonKeys;
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
 * The body while a key turns, through playerAnimator on a layer of its own ({@code key_turn}): the arm reaches out
 * level with the shoulder, the wrist turns the key in two notches and a last hard twist, the body leaning into it.
 * Played for a held turn and for a turn from the menu alike (the server's TURN moment starts it for those). Let go
 * early or refused and it eases away. Third person and other players only: in first person the key is drawn by
 * {@link KeyClientEvents}' hand transform.
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class KeyAnimator {
    private static final ResourceLocation LAYER = BeyondSolo.id("key");
    /** Above Better Combat's attacks (about 1000), our own body layer (2000) and the rune pose (2100). */
    private static final int PRIORITY = 2110;
    /** Players in the turn pose: entity id -> ticks they had held the key when last seen (-1 for a menu turn). */
    private static final Map<Integer, Integer> TURNING = new HashMap<>();

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, player -> new ModifierLayer<>());
    }

    /** Starts and stops the pose as players start and stop holding a key out. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            boolean using = p.isUsingItem() && DungeonKeys.isKey(p.getUseItem());
            Integer held = TURNING.get(p.getId());
            if (using) {
                if (held == null || held < 0) play(p, "key_turn", 3);
                TURNING.put(p.getId(), p.getTicksUsingItem());
            } else if (held != null && held >= 0) {
                TURNING.remove(p.getId());
                // a key held to the end has turned: the pose plays out by itself
                if (held < DungeonKeys.USE_TICKS - 3) stop(p);
            }
        }
        TURNING.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    /** A turn began (the server's TURN moment): a menu turn has no hold to start the pose, so start it here. */
    static void turn(Entity e) {
        if (!(e instanceof AbstractClientPlayer p) || p.isUsingItem()) return;
        TURNING.put(p.getId(), -1);
        play(p, "key_turn", 3);
    }

    /** The lock gave: the pose finishes on its own. */
    static void release(Entity e) {
        if (e instanceof AbstractClientPlayer p && TURNING.get(p.getId()) != null && TURNING.get(p.getId()) < 0) TURNING.remove(p.getId());
    }

    /** The turn stopped short (let go, refused, walked off). */
    static void stop(Entity e) {
        if (!(e instanceof AbstractClientPlayer p)) return;
        TURNING.remove(p.getId());
        stop(p);
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
        TURNING.clear();
    }

    private KeyAnimator() {}
}
