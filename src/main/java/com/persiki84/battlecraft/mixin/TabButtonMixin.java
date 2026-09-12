package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.menu.ScreenTabs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.TabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TabButton.class)
public abstract class TabButtonMixin {

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void battlecraft$glassTab(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                      CallbackInfo callback) {
        if (!ScreenTabs.dressed()) return;

        ScreenTabs.paintTab(graphics, (TabButton) (Object) this);
        callback.cancel();
    }
}
