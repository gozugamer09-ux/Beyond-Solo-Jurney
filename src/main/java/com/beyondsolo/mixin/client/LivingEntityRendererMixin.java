package com.beyondsolo.mixin.client;

import com.beyondsolo.client.skill.SkillImpacts;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A creature struck by a skill is drawn flashing white (vanilla and player-model renderers). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "getOverlayCoords", at = @At("RETURN"), cancellable = true)
    private static void beyondsolo$skillFlash(LivingEntity entity, float whiteOverlay, CallbackInfoReturnable<Integer> cir) {
        if (!SkillImpacts.active()) return;
        int overlay = cir.getReturnValueI();
        int flashed = SkillImpacts.overlay(entity, overlay);
        if (flashed != overlay) cir.setReturnValue(flashed);
    }
}
