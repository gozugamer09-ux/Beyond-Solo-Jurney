package com.beyondsolo.client.home;

import com.beyondsolo.BeyondSolo;
import com.beyondsolo.client.gate.GateFx;
import com.beyondsolo.client.render.ModRenderTypes;
import com.beyondsolo.home.TrophyPedestalBlock;
import com.beyondsolo.home.TrophyPedestalBlockEntity;
import com.beyondsolo.worldboss.WorldBossKind;
import com.beyondsolo.worldboss.WorldBossModule;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.EnumMap;
import java.util.Map;

/**
 * A pedestal of the trophy hall: once its great threat has fallen to the home's owner, a likeness of it stands on top,
 * small enough to fit, in the pose it takes asleep (kneeling, crouched, curled up), on a soft glow of its colour; until
 * then nothing stands there. The name and the count over it, or the question mark, are drawn by {@link HomePlates}.
 */
public class TrophyPedestalRenderer implements BlockEntityRenderer<TrophyPedestalBlockEntity> {
    private static final ResourceLocation GLOW = BeyondSolo.id("textures/effect/glow.png");
    /** How tall a likeness stands on its pedestal (blocks). */
    static final float STATUE = 0.9f;
    /** The top of a pedestal, above its block's foot. */
    static final float TOP = 15f / 16f;

    /** One likeness of each great threat for this level, made the first time one is drawn. */
    private static final Map<WorldBossKind, Entity> STATUES = new EnumMap<>(WorldBossKind.class);
    private static ClientLevel statuesFor;

    public TrophyPedestalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TrophyPedestalBlockEntity pedestal, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(pedestal.getLevel() instanceof ClientLevel level)) return;
        WorldBossKind kind = pedestal.kind();
        BlockPos pos = pedestal.getBlockPos();
        HomePlates.pedestal(pos, kind, pedestal.slain());
        if (kind == null || pedestal.slain() <= 0) return;
        Entity statue = statue(level, kind);
        if (statue == null) return;
        Direction facing = pedestal.getBlockState().hasProperty(TrophyPedestalBlock.FACING)
                ? pedestal.getBlockState().getValue(TrophyPedestalBlock.FACING) : Direction.NORTH;
        float time = (level.getGameTime() % 24000L) + partial;

        // a soft glow of its colour on the pedestal's top
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        int rgb = kind.color & 0xFFFFFF;
        pose.pushPose();
        pose.translate(0.5, TOP, 0.5);
        GateFx.flat(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, 0.01f, 0.62f, 0, rgb, 0.45f + 0.1f * Mth.sin(time * 0.08f));
        GateFx.dot(buffers.getBuffer(ModRenderTypes.lightTexture(GLOW)), pose, camera, 0, STATUE * 0.5f, 0, 0.8f, rgb, 0.08f);

        // the likeness, turned the way the pedestal faces, alive in its sleep
        float h = statue.getBbHeight(), w = statue.getBbWidth();
        float scale = STATUE / Math.max(0.5f, Math.max(h, w * 0.9f));
        float yaw = facing.toYRot();
        statue.tickCount = (int) (level.getGameTime() % 1_000_000L);
        statue.setYRot(yaw);
        statue.setYHeadRot(yaw);
        statue.setXRot(0);
        if (statue instanceof LivingEntity le) {
            le.yBodyRot = le.yBodyRotO = yaw;
            le.yHeadRotO = yaw;
        }
        pose.scale(scale, scale, scale);
        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(statue);
        try {
            renderer.render(statue, yaw, partial, pose, buffers, light);
        } catch (RuntimeException e) {
            // a likeness that can't be drawn is left off rather than taking the hall down with it
            BeyondSolo.LOGGER.debug("Trophy likeness of {} failed to draw", kind.id, e);
            STATUES.remove(kind);
        }
        pose.popPose();
    }

    private static Entity statue(ClientLevel level, WorldBossKind kind) {
        if (statuesFor != level) {
            STATUES.clear();
            statuesFor = level;
        }
        return STATUES.computeIfAbsent(kind, k -> {
            Entity e = WorldBossModule.type(k).create(level);
            if (e != null) e.setSilent(true);
            return e;
        });
    }

    /** Forgets the likenesses (logged out). */
    static void clear() {
        STATUES.clear();
        statuesFor = null;
    }

    @Override
    public AABB getRenderBoundingBox(TrophyPedestalBlockEntity pedestal) {
        BlockPos p = pedestal.getBlockPos();
        return new AABB(p.getX() - 0.5, p.getY(), p.getZ() - 0.5, p.getX() + 1.5, p.getY() + 2.5, p.getZ() + 1.5);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
