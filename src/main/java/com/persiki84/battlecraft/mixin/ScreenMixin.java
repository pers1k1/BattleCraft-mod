package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.menu.ScreenDress;
import com.persiki84.battlecraft.client.menu.ScreenSkin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    private static final int PLAIN_VEIL = 2;
    private static final int PLAIN_INVENTORY = 4;

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"), cancellable = true)
    private void battlecraft$ownBackground(GuiGraphics graphics, CallbackInfo callback) {
        if (battlecraft$paint(graphics)) callback.cancel();
    }

    @Inject(method = "renderDirtBackground(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"), cancellable = true)
    private void battlecraft$ownDirtBackground(GuiGraphics graphics, CallbackInfo callback) {
        if (battlecraft$paint(graphics)) callback.cancel();
    }

    @Inject(method = "rebuildWidgets", at = @At("HEAD"))
    private void battlecraft$measureWithScreenFont(CallbackInfo callback) {
        ScreenDress.build((Screen) (Object) this);
    }

    @Inject(method = "rebuildWidgets", at = @At("RETURN"))
    private void battlecraft$releaseScreenFont(CallbackInfo callback) {
        ScreenDress.built((Screen) (Object) this);
    }

    private boolean battlecraft$paint(GuiGraphics graphics) {
        if ((HudConfig.plainScreens() & PLAIN_VEIL) != 0) return false;

        Screen screen = (Screen) (Object) this;
        if (screen instanceof InventoryScreen && (HudConfig.plainScreens() & PLAIN_INVENTORY) != 0) return false;
        return ScreenSkin.paint(screen, graphics);
    }
}
