package com.persiki84.battlecraft.mixin.extrasounds;

import com.persiki84.battlecraft.compat.extrasounds.SoundOverlap;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.arbor.extrasoundsnext.sounds.SoundManager", remap = false)
public class ExtraSoundsManagerMixin {

    // WHY: смена слота играет звук предмета, а не свой образец, и по имени звука её не отличить
    // WHY: от клика в инвентаре, поэтому глушится сама точка входа
    @Inject(method = "hotbar(I)V", at = @At("HEAD"), cancellable = true)
    private static void battlecraft$yieldHotbar(int slot, CallbackInfo callback) {
        if (SoundOverlap.hotbarSwitch()) callback.cancel();
    }

    @Inject(method = "playSound(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
            at = @At("HEAD"), cancellable = true)
    private static void battlecraft$yieldSound(SoundInstance sound, CallbackInfo callback) {
        if (SoundOverlap.covers(sound.getLocation())) callback.cancel();
    }
}
