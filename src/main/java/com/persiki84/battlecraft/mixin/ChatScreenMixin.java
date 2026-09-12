package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.hud.ChatSkin;
import com.persiki84.battlecraft.client.hudedit.HudEditLayer;
import com.persiki84.battlecraft.client.hudedit.HudEditSession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"))
    private void battlecraft$raiseInput(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                        CallbackInfo callback) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, ChatSkin.inputLift(graphics), 0.0f);
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("RETURN"))
    private void battlecraft$settleInput(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                         CallbackInfo callback) {
        graphics.pose().popPose();
        HudEditLayer.render(graphics);
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void battlecraft$editPress(double mouseX, double mouseY, int button,
                                       CallbackInfoReturnable<Boolean> callback) {
        if (!HudEditSession.press(mouseX, mouseY, button)) return;
        callback.setReturnValue(true);
    }

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void battlecraft$editKey(int key, int scanCode, int modifiers,
                                     CallbackInfoReturnable<Boolean> callback) {
        if (key != GLFW.GLFW_KEY_ESCAPE || !HudEditSession.escape()) return;
        callback.setReturnValue(true);
    }
}
