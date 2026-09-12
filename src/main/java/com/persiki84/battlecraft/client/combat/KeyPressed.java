package com.persiki84.battlecraft.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

final class KeyPressed {

    private KeyPressed() {}

    static boolean physically(KeyMapping key) {
        if (key == null) return false;

        InputConstants.Key bound = key.getKey();
        if (bound.getValue() == InputConstants.UNKNOWN.getValue()) return false;

        long window = Minecraft.getInstance().getWindow().getWindow();
        if (bound.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, bound.getValue()) == GLFW.GLFW_PRESS;
        }
        return bound.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, bound.getValue());
    }
}
