package com.beyondsolo.client.crafting;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.combat.CombatWorldFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.crafting.CraftingModule;
import com.beyondsolo.crafting.MonsterCoreItem;
import com.beyondsolo.client.screen.SystemTabs;
import com.beyondsolo.enemy.Affinity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

/**
 * Client wiring for crafting: the CRAFT tab, and the light around dropped cores and mana stones (a pulsing pool and
 * halo in the rank's or affinity's colour; boss cores also get a short column of light).
 */
@EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
public final class CraftClientEvents {
    public static final ResourceLocation CRAFT_TAB = BeyondSolo.id("craft");

    @SubscribeEvent
    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SystemTabs.register(CRAFT_TAB, 127, CraftTab::new));
        NeoForge.EVENT_BUS.addListener(CraftClientEvents::renderLevel);
        NeoForge.EVENT_BUS.addListener(CraftClientEvents::tick);
    }

    /** Colour of the glow around a dropped item, or -1 for none. */
    public static int glowColor(ItemStack s) {
        if (s.getItem() instanceof MonsterCoreItem core) {
            Affinity a = MonsterCoreItem.affinity(s);
            return a != Affinity.NONE ? a.color : core.rank().color();
        }
        if (s.getItem() instanceof com.beyondsolo.crafting.ManaStoneItem) return com.beyondsolo.crafting.ManaStoneItem.COLOR;
        return -1;
    }

    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || mc.player == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity item) || e.distanceToSqr(mc.player) > 32 * 32) continue;
            if (!(item.getItem().getItem() instanceof MonsterCoreItem core) || mc.level.random.nextInt(core.boss() ? 2 : 8) != 0) continue;
            mc.level.addParticle(ParticleTypes.GLOW, e.getX() + (mc.level.random.nextDouble() - 0.5) * 0.5, e.getY() + 0.2,
                    e.getZ() + (mc.level.random.nextDouble() - 0.5) * 0.5, 0, 0.04, 0);
        }
    }

    static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        PoseStack pose = event.getPoseStack();
        Camera camera = event.getCamera();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float time = mc.level.getGameTime() + partial;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        boolean any = false;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity item) || e.distanceToSqr(cam) > 48 * 48) continue;
            int c = glowColor(item.getItem());
            if (c < 0) continue;
            any = true;
            boolean boss = item.getItem().getItem() instanceof MonsterCoreItem core && core.boss();
            Vec3 at = e.getPosition(partial);
            float pulse = 0.7f + 0.3f * Mth.sin(time * 0.18f + e.getId());
            float bob = Mth.sin((item.getAge() + partial) / 10f + item.bobOffs) * 0.1f + 0.1f;
            VertexConsumer glow = buffers.getBuffer(ModRenderTypes.lightTexture(CombatWorldFx.GLOW));
            // a halo around the item, facing the camera
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y + bob + 0.25, at.z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            quad(glow, pose.last().pose(), (boss ? 0.7f : 0.5f) * (0.9f + 0.15f * pulse), c, 0.9f * pulse);
            quad(glow, pose.last().pose(), (boss ? 0.3f : 0.2f), 0xFFFFFF, 0.5f * pulse);
            if (boss) {
                pose.mulPose(Axis.XP.rotationDegrees(-camera.getXRot()));
                column(buffers.getBuffer(ModRenderTypes.lightTexture(com.beyondsolo.client.combat.GradeVisuals.BEAM)), pose.last().pose(),
                        0.16f, 2.4f, c, 0.6f * pulse);
            }
            pose.popPose();
            // a pool of light on the ground
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y + 0.02, at.z - cam.z);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            quad(buffers.getBuffer(ModRenderTypes.lightTexture(CombatWorldFx.GLOW)), pose.last().pose(), boss ? 0.9f : 0.6f, c, 0.7f * pulse);
            pose.popPose();
        }
        if (!any) return;
        buffers.endBatch(ModRenderTypes.lightTexture(CombatWorldFx.GLOW));
        buffers.endBatch(ModRenderTypes.lightTexture(com.beyondsolo.client.combat.GradeVisuals.BEAM));
    }

    private static void quad(VertexConsumer out, Matrix4f m, float half, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        out.addVertex(m, -half, -half, 0).setUv(0, 1).setColor(r, g, b, a);
        out.addVertex(m, half, -half, 0).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, half, half, 0).setUv(1, 0).setColor(r, g, b, a);
        out.addVertex(m, -half, half, 0).setUv(0, 0).setColor(r, g, b, a);
    }

    private static void column(VertexConsumer out, Matrix4f m, float half, float height, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        out.addVertex(m, -half, -0.25f, 0).setUv(0, 1).setColor(r, g, b, a);
        out.addVertex(m, half, -0.25f, 0).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, half, height, 0).setUv(1, 0).setColor(r, g, b, a);
        out.addVertex(m, -half, height, 0).setUv(0, 0).setColor(r, g, b, a);
    }

    private CraftClientEvents() {}
}
