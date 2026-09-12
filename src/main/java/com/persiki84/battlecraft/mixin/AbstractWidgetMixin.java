package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.menu.WidgetRestyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {
    @Shadow
    protected abstract void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Redirect(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/AbstractWidget;renderWidget"
                            + "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void battlecraft$skipRepainted(AbstractWidget widget, GuiGraphics graphics,
                                           int mouseX, int mouseY, float partialTick) {
        if (WidgetRestyle.repainted(widget)) return;
        if (com.persiki84.shared.client.ui.UiFarewell.burning() && widget.visible) {
            WidgetRestyle.noteStray(widget);
        }
        renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
