package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.loading.BootOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// WHY: подмена ловится на постановке оверлея, а не на тике отрисовки: пока грузятся моды, нашего
// WHY: подписчика ещё нет, и заставка загрузчика занимала бы весь этот отрезок
@Mixin(Minecraft.class)
public abstract class MinecraftOverlayMixin {
    @ModifyVariable(method = "setOverlay", at = @At("HEAD"), argsOnly = true)
    private Overlay battlecraft$wrapBoot(Overlay overlay) {
        return BootOverlay.claim((Minecraft) (Object) this, overlay);
    }
}
