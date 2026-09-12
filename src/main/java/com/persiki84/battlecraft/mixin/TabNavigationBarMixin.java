package com.persiki84.battlecraft.mixin;

import com.google.common.collect.ImmutableList;
import com.persiki84.battlecraft.client.menu.ScreenTabs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TabNavigationBar.class)
public abstract class TabNavigationBarMixin {
    @Shadow @Final private ImmutableList<TabButton> tabButtons;
    @Shadow private int width;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void battlecraft$glassBar(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                      CallbackInfo callback) {
        if (!ScreenTabs.dressed()) return;

        ScreenTabs.paintBar(graphics, width);
        for (TabButton button : tabButtons) {
            button.render(graphics, mouseX, mouseY, partialTick);
        }
        callback.cancel();
    }
}
