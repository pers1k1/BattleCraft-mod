package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.combat.CombatRules;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LocalPlayer.class, priority = 1500)
public abstract class LocalPlayerMixin {

    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void battlecraft$holdSprint(CallbackInfoReturnable<Boolean> callback) {
        if (CombatRules.grounded((LocalPlayer) (Object) this)) callback.setReturnValue(false);
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void battlecraft$dropSprint(CallbackInfo callback) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (self.isSprinting() && CombatRules.grounded(self)) self.setSprinting(false);
    }
}
