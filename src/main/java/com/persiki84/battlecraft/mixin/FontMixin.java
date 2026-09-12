package com.persiki84.battlecraft.mixin;

import com.persiki84.shared.client.ui.UiFont;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Font.class)
public abstract class FontMixin {

    @ModifyVariable(method = "drawInternal(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I",
            at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private boolean battlecraft$plainString(boolean dropShadow) {
        return dropShadow && !UiFont.plain();
    }

    @ModifyVariable(method = "drawInternal(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
            at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private boolean battlecraft$plainSequence(boolean dropShadow) {
        return dropShadow && !UiFont.plain();
    }
}
