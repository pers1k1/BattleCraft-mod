package com.persiki84.battlecraft.client.setup;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public interface SetupPage {
    Component title();

    Component note();

    int contentHeight();

    void build(SetupScreen screen, int left, int top, int width);

    void paint(GuiGraphics graphics, Font font, int left, int top, int width, float leaving);


    default boolean framed() {
        return true;
    }

    default boolean keyPressed(int key, int scan) {
        return false;
    }

    default boolean mouseClicked(int button) {
        return false;
    }

    default void release() {}
}
