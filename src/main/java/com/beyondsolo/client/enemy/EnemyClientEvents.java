package com.beyondsolo.client.enemy;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.enemy.EnemyModule;
import com.beyondsolo.enemy.MonsterKind;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

/** Client wiring of the enemy roster: renderers, world effects, names over heads. */
public final class EnemyClientEvents {
    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            for (MonsterKind kind : MonsterKind.values()) event.registerEntityRenderer(EnemyModule.type(kind), ctx -> new MonsterRenderer(ctx, kind));
            event.registerEntityRenderer(EnemyModule.ORB.get(), OrbRenderer::new);
        }

        /** Names belong to the world, like Minecraft's own name tags: under the HUD and the System's pop-ups. */
        @SubscribeEvent
        static void layers(RegisterGuiLayersEvent event) {
            event.registerBelowAll(BeyondSolo.id("enemy_plates"), EnemyHud::render);
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        static void renderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            EnemyWorldFx.render(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            if (net.minecraft.client.Minecraft.getInstance().level != null && net.minecraft.client.Minecraft.getInstance().level.getGameTime() % 40 == 0) EnemyClient.prune();
        }

        @SubscribeEvent
        static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            EnemyClient.clear();
        }

        /** A creature whose name the System shows over its head doesn't get Minecraft's own name tag as well. */
        @SubscribeEvent
        static void nameTag(RenderNameTagEvent event) {
            if (EnemyClient.level(event.getEntity().getId()) >= 0 && EnemyHud.showsNames()) event.setCanRender(TriState.FALSE);
        }
    }

    private EnemyClientEvents() {}
}
