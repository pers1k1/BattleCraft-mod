package com.persiki84.battlecraft.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class FramerateMixin {

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void battlecraft$liftMenuCap(CallbackInfoReturnable<Integer> callback) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) return;

        callback.setReturnValue(client.getWindow().getFramerateLimit());
    }
}
