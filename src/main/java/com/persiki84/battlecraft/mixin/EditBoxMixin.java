package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.menu.WidgetRestyle;
import com.persiki84.shared.client.ui.UiField;
import com.persiki84.shared.client.ui.UiFont;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiFunction;

@Mixin(EditBox.class)
public abstract class EditBoxMixin {
    @Shadow private int displayPos;
    @Shadow private int cursorPos;
    @Shadow private int highlightPos;
    @Shadow private int textColor;
    @Shadow private boolean isEditable;
    @Shadow private boolean bordered;
    @Shadow private String suggestion;
    @Shadow private Component hint;
    @Shadow private BiFunction<String, Integer, FormattedCharSequence> formatter;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void battlecraft$ownField(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                      CallbackInfo callback) {
        EditBox box = (EditBox) (Object) this;
        if (!WidgetRestyle.dressing(box)) return;

        UiField.render(graphics, box, displayPos, cursorPos, highlightPos, textColor, isEditable, bordered,
                suggestion, hint, formatter);
        callback.cancel();
    }

    // WHY: попадание курсора ваниль считает шириной строки, а рисуем мы своим пером: без подмены
    // WHY: щелчок ставил курсор мимо буквы тем сильнее, чем длиннее строка
    @Inject(method = "onClick", at = @At("HEAD"))
    private void battlecraft$measureWithScreenFont(double mouseX, double mouseY, CallbackInfo callback) {
        if (WidgetRestyle.known((EditBox) (Object) this)) UiFont.push();
    }

    @Inject(method = "onClick", at = @At("RETURN"))
    private void battlecraft$releaseScreenFont(double mouseX, double mouseY, CallbackInfo callback) {
        if (WidgetRestyle.known((EditBox) (Object) this)) UiFont.pop();
    }

    // WHY: сдвиг видимой части строки ваниль тоже считает шириной: без общего пера длинный адрес
    // WHY: уезжал не на столько, сколько нарисовано, и курсор вылезал за поле
    @Inject(method = "setHighlightPos", at = @At("HEAD"))
    private void battlecraft$scrollWithScreenFont(int position, CallbackInfo callback) {
        if (WidgetRestyle.known((EditBox) (Object) this)) UiFont.push();
    }

    @Inject(method = "setHighlightPos", at = @At("RETURN"))
    private void battlecraft$releaseScrollFont(int position, CallbackInfo callback) {
        if (WidgetRestyle.known((EditBox) (Object) this)) UiFont.pop();
    }
}
