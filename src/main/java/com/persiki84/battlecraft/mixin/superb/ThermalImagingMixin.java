package com.persiki84.battlecraft.mixin.superb;

import com.persiki84.battlecraft.client.goggles.GogglesClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// WHY: handleThermalImaging зовёт turnOffThermalImaging каждый тик, пока тепловизор выключен,
// WHY: а тот гасит gameRenderer.postEffect целиком и стирает чужие постэффекты
@Mixin(targets = "com.atsuishio.superbwarfare.event.ClientEventHandler", remap = false)
public class ThermalImagingMixin {

    @Redirect(
            method = "turnOffThermalImaging",
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/client/renderer/GameRenderer;m_109086_()V")
    )
    private static void battlecraft$keepForeignEffect(GameRenderer renderer) {
        if (GogglesClient.ownsEffect()) return;
        renderer.shutdownEffect();
    }
}
