package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.menu.ScreenTabs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// WHY: экран создания мира сам блитит шов над нижней полосой, мимо renderBackground: под нашим
// WHY: фоном он висел ванильной полоской земли поперёк экрана
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"))
    private void battlecraft$dropFooterSeam(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                                            float u, float v, int width, int height,
                                            int textureWidth, int textureHeight) {
        if (ScreenTabs.dressed()) return;

        graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }
}
