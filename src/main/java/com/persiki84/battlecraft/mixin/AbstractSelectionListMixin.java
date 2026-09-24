package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.menu.ListSelection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {
    private static final int FOCUSED_BORDER = -1;
    private static final int ROW_LEFT_INSET = 2;

    @Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true)
    private void battlecraft$glidingSelection(GuiGraphics graphics, int top, int rowWidth, int rowHeight,
                                              int borderColor, int fillColor, CallbackInfo callback) {
        if (!ListSelection.dressed()) return;

        AbstractSelectionList<?> list = (AbstractSelectionList<?>) (Object) this;
        int left = list.getRowLeft() - ROW_LEFT_INSET;
        int right = left + rowWidth;
        ListSelection.paint(graphics, list, list.getSelected(), left, right, top, rowHeight,
                borderColor == FOCUSED_BORDER);
        callback.cancel();
    }
}
