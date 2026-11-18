package com.beyondsolo.client.combat;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.ClientSystemData;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.item.ItemGrade;
import com.beyondsolo.item.SystemBowItem;
import com.beyondsolo.item.arsenal.Arsenal;
import com.beyondsolo.item.arsenal.ArsenalTooltip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * How a grade shows on the client: coloured names (a moving gradient for mythic and divine), a tooltip frame in the
 * grade's colours, a soft glow behind graded items in inventory slots, and a beam of light over dropped gear (rare and
 * up; legendary and up also shed sparks). Also wires the System bows' pull animation.
 */
public final class GradeVisuals {
    public static final ResourceLocation BEAM = BeyondSolo.id("textures/effect/loot_beam.png");

    private static float time() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? (System.currentTimeMillis() % 100000) / 50f : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    /** The grade's colour at this moment: steady below legendary, a slow shimmer between its two colours above. */
    public static int shimmer(ItemGrade g, float offset) {
        if (!g.shimmers()) return g.color();
        float speed = g == ItemGrade.DIVINE ? 0.09f : g == ItemGrade.MYTHIC ? 0.07f : 0.05f;
        float t = 0.5f + 0.5f * Mth.sin(time() * speed + offset);
        return mix(g.color(), g.accent(), t);
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        static void setup(FMLClientSetupEvent event) {
            ArsenalTooltip.clientClass = () -> {
                var d = ClientSystemData.get();
                return d.isAwakened() ? d.playerClass() : null;
            };
            event.enqueueWork(() -> {
                for (var item : Arsenal.all()) {
                    if (!(item.get() instanceof SystemBowItem bow)) continue;
                    ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) ->
                            entity == null || entity.getUseItem() != stack ? 0f : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20f);
                    ItemProperties.register(bow, ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1f : 0f);
                }
            });
        }
    }

    @EventBusSubscriber(modid = BeyondSolo.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        /** Drawing one of our bows narrows the view as the vanilla bow does: the game only does it for its own bow. */
        @SubscribeEvent
        static void bowZoom(ComputeFovModifierEvent event) {
            var player = event.getPlayer();
            if (!player.isUsingItem() || !(player.getUseItem().getItem() instanceof SystemBowItem)) return;
            float pull = Math.min(1f, player.getTicksUsingItem() / 20f);
            float zoom = 1f - pull * pull * 0.15f;
            float scale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
            event.setNewFovModifier(event.getNewFovModifier() * Mth.lerp(scale, 1f, zoom));
        }

        /** Mythic and divine names ripple with their two colours. */
        @SubscribeEvent
        static void tooltip(ItemTooltipEvent event) {
            ItemGrade g = ItemGrade.of(event.getItemStack());
            List<Component> lines = event.getToolTip();
            if (g == null || lines.isEmpty() || !g.atLeast(ItemGrade.MYTHIC)) return;
            Component first = lines.get(0);
            String text = first.getString();
            MutableComponent out = Component.empty();
            Style base = first.getStyle().withBold(true);
            for (int i = 0; i < text.length(); i++) {
                int c = shimmer(g, -i * 0.35f);
                out.append(Component.literal(String.valueOf(text.charAt(i))).withStyle(base.withColor(TextColor.fromRgb(c))));
            }
            lines.set(0, out);
        }

        /** The tooltip frame takes the grade's colours. */
        @SubscribeEvent
        static void tooltipColor(RenderTooltipEvent.Color event) {
            ItemGrade g = ItemGrade.of(event.getItemStack());
            if (g == null || g == ItemGrade.COMMON) return;
            int top = shimmer(g, 0);
            int bottom = g.shimmers() ? shimmer(g, 2.2f) : mix(g.color(), 0x000000, 0.45f);
            event.setBorderStart(0xFF000000 | top);
            event.setBorderEnd(0xC0000000 | bottom);
            int bg = mix(0x100A18, g.color(), g.shimmers() ? 0.14f : 0.08f);
            event.setBackgroundStart(0xF0000000 | bg);
            event.setBackgroundEnd(0xF0000000 | mix(0x100A18, 0x000000, 0.3f));
        }

        /** A soft glow behind graded items in every inventory. */
        @SubscribeEvent
        static void slots(ContainerScreenEvent.Render.Background event) {
            AbstractContainerScreen<?> screen = event.getContainerScreen();
            GuiGraphics g = event.getGuiGraphics();
            for (Slot slot : screen.getMenu().slots) {
                if (!slot.isActive()) continue;
                ItemGrade grade = ItemGrade.of(slot.getItem());
                if (grade == null || grade == ItemGrade.COMMON) continue;
                int x = screen.getGuiLeft() + slot.x;
                int y = screen.getGuiTop() + slot.y;
                int c = shimmer(grade, slot.index * 0.6f);
                float pulse = grade.shimmers() ? 0.75f + 0.25f * Mth.sin(time() * 0.12f + slot.index) : 0.8f;
                int a = Math.round((0x50 + grade.tier() * 0x10) * pulse);
                g.fillGradient(x, y, x + 16, y + 16, (a / 3) << 24 | c, a << 24 | c);
                g.fill(x, y + 15, x + 16, y + 16, Math.min(255, a * 2) << 24 | c);
            }
        }

        @SubscribeEvent
        static void renderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
            beams(event.getPoseStack(), event.getCamera(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.isPaused() || mc.player == null) return;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof ItemEntity item) || e.distanceToSqr(mc.player) > 48 * 48) continue;
                ItemGrade g = ItemGrade.of(item.getItem());
                if (g == null || !g.shimmers() || mc.level.random.nextInt(g == ItemGrade.LEGENDARY ? 6 : 3) != 0) continue;
                double r = 0.35;
                mc.level.addParticle(g == ItemGrade.DIVINE ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK,
                        e.getX() + (mc.level.random.nextDouble() - 0.5) * r * 2, e.getY() + 0.2 + mc.level.random.nextDouble() * 1.5,
                        e.getZ() + (mc.level.random.nextDouble() - 0.5) * r * 2, 0, 0.03, 0);
            }
        }
    }

    /** Light columns over dropped graded gear. */
    static void beams(PoseStack pose, Camera camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        float time = time();
        boolean any = false;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity item) || e.distanceToSqr(cam) > 64 * 64) continue;
            ItemGrade g = ItemGrade.of(item.getItem());
            if (g == null || g == ItemGrade.COMMON) continue;
            any = true;
            Vec3 at = e.getPosition(partial);
            float height = 1.2f + g.tier() * 0.9f;
            float width = 0.18f + g.tier() * 0.05f;
            float pulse = 0.8f + 0.2f * Mth.sin(time * 0.15f + e.getId());
            int c = shimmer(g, e.getId());
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            // turn the column towards the camera around the vertical axis
            pose.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
            VertexConsumer out = buffers.getBuffer(ModRenderTypes.lightTexture(BEAM));
            column(out, pose.last().pose(), width, height, c, 0.75f * pulse);
            column(out, pose.last().pose(), width * 0.4f, height * 0.9f, mix(c, 0xFFFFFF, 0.6f), 0.8f * pulse);
            pose.popPose();
            // a glowing pool under the item
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y + 0.02, at.z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(time * 2 % 360));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            disc(buffers.getBuffer(ModRenderTypes.lightTexture(CombatWorldFx.GLOW)), pose.last().pose(), 0.45f + g.tier() * 0.08f, c, 0.6f * pulse);
            if (g.shimmers()) {
                disc(buffers.getBuffer(ModRenderTypes.lightTexture(CombatWorldFx.SIGIL)), pose.last().pose(), 0.55f + g.tier() * 0.06f, c, 0.55f * pulse);
            }
            pose.popPose();
        }
        if (!any) return;
        buffers.endBatch(ModRenderTypes.lightTexture(BEAM));
        buffers.endBatch(ModRenderTypes.lightTexture(CombatWorldFx.GLOW));
        buffers.endBatch(ModRenderTypes.lightTexture(CombatWorldFx.SIGIL));
    }

    private static void column(VertexConsumer out, Matrix4f m, float half, float height, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        out.addVertex(m, -half, 0, 0).setUv(0, 1).setColor(r, g, b, a);
        out.addVertex(m, half, 0, 0).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, half, height, 0).setUv(1, 0).setColor(r, g, b, a);
        out.addVertex(m, -half, height, 0).setUv(0, 0).setColor(r, g, b, a);
    }

    private static void disc(VertexConsumer out, Matrix4f m, float half, int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255), 0, 255);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        out.addVertex(m, -half, -half, 0).setUv(0, 1).setColor(r, g, b, a);
        out.addVertex(m, half, -half, 0).setUv(1, 1).setColor(r, g, b, a);
        out.addVertex(m, half, half, 0).setUv(1, 0).setColor(r, g, b, a);
        out.addVertex(m, -half, half, 0).setUv(0, 0).setColor(r, g, b, a);
    }

    private GradeVisuals() {}
}
