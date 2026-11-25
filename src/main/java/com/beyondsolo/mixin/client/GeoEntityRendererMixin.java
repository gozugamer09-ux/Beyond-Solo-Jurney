package com.beyondsolo.mixin.client;

import com.beyondsolo.client.skill.SkillImpacts;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * A creature drawn by GeckoLib flashes white when a skill strikes it, as the vanilla ones do. The renderers that
 * draw their own overlay (the great threats, the training dummy) ask {@link SkillImpacts#overlay} themselves.
 */
@Mixin(value = GeoEntityRenderer.class, remap = false)
public abstract class GeoEntityRendererMixin {
    @Inject(method = "getPackedOverlay(Lnet/minecraft/world/entity/Entity;FF)I", at = @At("RETURN"), cancellable = true, require = 0)
    private void beyondsolo$skillFlash(Entity animatable, float u, float partialTick, CallbackInfoReturnable<Integer> cir) {
        if (!SkillImpacts.active()) return;
        int overlay = cir.getReturnValueI();
        int flashed = SkillImpacts.overlay(animatable, overlay);
        if (flashed != overlay) cir.setReturnValue(flashed);
    }
}
