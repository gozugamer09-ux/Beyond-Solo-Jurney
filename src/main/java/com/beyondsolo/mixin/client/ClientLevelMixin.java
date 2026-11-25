package com.beyondsolo.mixin.client;

import com.beyondsolo.client.skill.SkillImpacts;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The hitstop: a creature that hangs on a skill's blow skips its client ticks (its pose, its steps toward where the
 * server has it, its animation clock) until the hold is over, then carries on where it was.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void beyondsolo$skillHold(Entity entity, CallbackInfo ci) {
        if (SkillImpacts.held(entity)) ci.cancel();
    }
}
