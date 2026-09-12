package com.persiki84.shared.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import org.lwjgl.glfw.GLFW;

public final class UiInput {

    private UiInput() {}

    public static boolean mouseDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    // WHY: клик оставляет фокус на виджете, и isHoveredOrFocused держит его подсвеченным,
    // WHY: пока курсор уже в другом месте; ваниль отделяет наведение от фокуса тем же условием
    public static boolean pointed(AbstractWidget widget) {
        return widget.isHovered()
                || widget.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard();
    }
}
