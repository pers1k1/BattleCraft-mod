package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.ServerLocks;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    // WHY: один вход гасит весь ванильный glint сразу - рука, инвентарь, дроп, броня и элитра
    // WHY: спрашивают именно hasFoil, поэтому перехватывать каждый слой рендера не нужно
    @Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
    private void battlecraft$hideGlint(CallbackInfoReturnable<Boolean> callback) {
        if (ServerLocks.glintHidden()) callback.setReturnValue(false);
    }
}
