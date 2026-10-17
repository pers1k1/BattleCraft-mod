package com.persiki84.battlecraft.client.menu.lobby;

import com.persiki84.shared.client.ui.UiRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class LobbyFigures {
    public static final float CENTER = 0.5f;
    public static final float RIGHT = 1.0f;

    private LobbyFigures() {}

    // WHY: все строки экрана с меняющимся числом (счётчики мест, отсчёты, готовность) идут через
    // WHY: эти два метода, по одному вызову на строку: переход цифр на морф правится здесь, а не по экрану
    public static void line(GuiGraphics graphics, Font font, Component value, float anchorX, float y,
                            float scale, int color, float align) {
        if (align >= RIGHT) {
            UiRender.textRight(graphics, font, value, anchorX, y, scale, color, false);
            return;
        }
        UiRender.textCentered(graphics, font, value, anchorX, y, scale, color, false);
    }

    // WHY: строка, которая сама держит своё число (LobbyText), меняется по цифрам, как таймер острова
    public static void line(GuiGraphics graphics, Font font, LobbyText value, float anchorX, float y,
                            float scale, int color, float align) {
        value.ticker(align >= RIGHT ? RIGHT : CENTER).at(graphics, font, value.current(), anchorX, y, scale, color);
    }

    public static void boxed(GuiGraphics graphics, Font font, LobbyText value, float x, float y,
                             float width, float height, float scale, int color, float align) {
        value.ticker(align).box(graphics, font, value.current(), x, y, height, width, scale, color);
    }

    public static void boxed(GuiGraphics graphics, Font font, Component value, float x, float y,
                             float width, float height, float scale, int color, float align) {
        UiRender.textTrackedBox(graphics, font, value, x, y, height, width, scale, 0.0f, color, false, align);
    }
}
