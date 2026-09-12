package com.persiki84.battlecraft.mixin;

import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityRenderer.class)
public abstract class NameTagMixin {
    private static final float GLOW_SPREAD = 0.6f;
    private static final float GLOW_ALPHA = 0.22f;
    private static final int NO_BACKDROP = 0;

    @Redirect(method = "renderNameTag",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;"
                            + "FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;"
                            + "Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))
    private int battlecraft$softNameTag(Font font, Component text, float x, float y, int color, boolean dropShadow,
                                        Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode mode,
                                        int backdrop, int light) {
        if (mode != Font.DisplayMode.NORMAL) {
            return font.drawInBatch(text, x, y, color, false, matrix, buffer, mode, NO_BACKDROP, light);
        }

        int halo = UiTheme.withAlpha(color, GLOW_ALPHA);
        font.drawInBatch(text, x - GLOW_SPREAD, y, halo, false, matrix, buffer, mode, NO_BACKDROP, light);
        font.drawInBatch(text, x + GLOW_SPREAD, y, halo, false, matrix, buffer, mode, NO_BACKDROP, light);
        font.drawInBatch(text, x, y - GLOW_SPREAD, halo, false, matrix, buffer, mode, NO_BACKDROP, light);
        font.drawInBatch(text, x, y + GLOW_SPREAD, halo, false, matrix, buffer, mode, NO_BACKDROP, light);

        return font.drawInBatch(text, x, y, color, false, matrix, buffer, mode, NO_BACKDROP, light);
    }
}
