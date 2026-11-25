package com.beyondsolo.mixin.client;

import com.beyondsolo.client.skill.SkillImpacts;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

/**
 * A creature that hangs on a skill's blow is drawn at the moment it was struck (its ticks are held, see
 * {@link ClientLevelMixin}), shuddering along the line of the blow.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @WrapMethod(method = "renderEntity")
    private void beyondsolo$skillHold(Entity entity, double camX, double camY, double camZ, float partialTick, PoseStack pose,
                                      MultiBufferSource buffers, Operation<Void> original) {
        if (!SkillImpacts.active()) {
            original.call(entity, camX, camY, camZ, partialTick, pose, buffers);
            return;
        }
        Vec3 shudder = SkillImpacts.shudder(entity);
        original.call(entity, camX - shudder.x, camY - shudder.y, camZ - shudder.z, SkillImpacts.partial(entity, partialTick), pose, buffers);
    }
}
