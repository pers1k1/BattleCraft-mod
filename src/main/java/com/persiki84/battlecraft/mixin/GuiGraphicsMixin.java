package com.persiki84.battlecraft.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.battlecraft.client.hud.ForeignHud;
import com.persiki84.battlecraft.client.menu.ForeignPanel;
import net.minecraft.client.renderer.RenderType;
import com.persiki84.shared.client.ui.UiFont;
import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("HEAD"))
    private void battlecraft$sharpenCount(Font font, ItemStack stack, int x, int y, String text, CallbackInfo callback) {
        GuiGraphics graphics = (GuiGraphics) (Object) this;
        UiFont.push(UiRender.boldFaceFor(UiRender.pixels(graphics)));
    }

    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("RETURN"))
    private void battlecraft$releaseCount(Font font, ItemStack stack, int x, int y, String text, CallbackInfo callback) {
        UiFont.pop();
    }

    @Inject(method = "blit(Lnet/minecraft/resources/ResourceLocation;IIIIIIIFFII)V",
            at = @At("HEAD"), cancellable = true)
    private void battlecraft$foreignPanel(ResourceLocation texture, int left, int right, int top, int bottom,
                                          int blitOffset, int uWidth, int vHeight, float u, float v,
                                          int textureWidth, int textureHeight, CallbackInfo callback) {
        GuiGraphics graphics = (GuiGraphics) (Object) this;
        if (ForeignHud.watching() && RenderSystem.getShaderColor()[3] > 0.02f) {
            ForeignHud.record(battlecraft$pose(), left, top, right, bottom);
        }
        if (ForeignPanel.replace(graphics, texture, left, top, right - left, bottom - top)) {
            callback.cancel();
        }
    }

    @Inject(method = "fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V", at = @At("HEAD"))
    private void battlecraft$measureFill(RenderType layer, int left, int top, int right, int bottom,
                                         int blitOffset, int color, CallbackInfo callback) {
        if (!ForeignHud.watching() || (color >>> 24) == 0) return;
        ForeignHud.record(battlecraft$pose(), Math.min(left, right), Math.min(top, bottom),
                Math.max(left, right), Math.max(top, bottom));
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I", at = @At("HEAD"))
    private void battlecraft$measureText(Font font, String text, int x, int y, int color, boolean shadow,
                                         CallbackInfoReturnable<Integer> callback) {
        if (!ForeignHud.watching() || text == null) return;
        ForeignHud.record(battlecraft$pose(), x, y, x + font.width(text), y + font.lineHeight);
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I",
            at = @At("HEAD"))
    private void battlecraft$measureSequence(Font font, FormattedCharSequence text, int x, int y, int color,
                                             boolean shadow, CallbackInfoReturnable<Integer> callback) {
        if (!ForeignHud.watching() || text == null) return;
        ForeignHud.record(battlecraft$pose(), x, y, x + font.width(text), y + font.lineHeight);
    }

    private Matrix4f battlecraft$pose() {
        return ((GuiGraphics) (Object) this).pose().last().pose();
    }
}
