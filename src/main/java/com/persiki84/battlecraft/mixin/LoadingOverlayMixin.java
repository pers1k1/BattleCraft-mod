package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.loading.BootOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// WHY: перезагрузка ресурсов (смена языка, правка пака) рисует текущий экран сама, минуя
// WHY: ForgeHooksClient: события кадра не приходят, экран не одевается, и кнопки на время
// WHY: загрузки становятся ванильными, а после неё возвращаются в наш вид
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    @Redirect(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;render"
                            + "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void battlecraft$dressScreen(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                                         float partialTick) {
        if (BootOverlay.driving()) {
            screen.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        ForgeHooksClient.drawScreen(screen, graphics, mouseX, mouseY, partialTick);
    }
}
