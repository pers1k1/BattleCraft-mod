package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: окно «Об этом Mac» в малом виде: значок, название, версия и сборка. Вырастает из-под
// WHY: значка бренда той же анимацией, что центр управления и календарь, строки догоняют каскадом
final class DesktopAbout {
    private static final float WIDTH = 150.0f;
    private static final float HEIGHT = 94.0f;
    private static final float RADIUS = 16.0f;
    private static final float ICON = 22.0f;
    private static final float ICON_MIDDLE = 23.0f;
    private static final float NAME_TOP = 42.0f;
    private static final float VERSION_TOP = 60.0f;
    private static final float BUILD_TOP = 72.0f;
    private static final float NAME_SCALE = 1.1f;
    private static final float VERSION_SCALE = 0.7f;
    private static final float BUILD_SCALE = 0.62f;
    private static final Component NAME = Component.translatable("battlecraft.desktop.about.name");

    private final DesktopPopover popover = new DesktopPopover();
    private final Component build = DesktopWatermark.build();
    private float left;
    private float top;

    boolean open() {
        return popover.open();
    }

    void show() {
        popover.show();
    }

    void close() {
        popover.hide();
    }

    void dismiss() {
        popover.dismiss();
    }

    boolean contains(double mouseX, double mouseY) {
        return popover.open() && mouseX >= left && mouseX <= left + WIDTH && mouseY >= top && mouseY <= top + HEIGHT;
    }

    void render(GuiGraphics graphics, Font font, float anchorLeft, float anchorTop, float pivotX) {
        popover.advance(UiFrame.delta());
        if (!popover.visible()) return;

        left = anchorLeft;
        top = anchorTop;
        popover.begin(graphics, Math.max(left, Math.min(left + WIDTH, pivotX)), top);
        try {
            paint(graphics, font, popover.alpha());
        } finally {
            popover.end(graphics);
        }
    }

    private void paint(GuiGraphics graphics, Font font, float alpha) {
        UiGlass.hush(graphics, left, top, WIDTH, HEIGHT, RADIUS, alpha);
        UiGlass.window(graphics, left, top, WIDTH, HEIGHT, RADIUS, alpha);
        UiGlass.layer(graphics);
        float middle = left + WIDTH / 2.0f;
        UiRestFrame.shift(graphics, 0.0f, popover.drift(0));
        try {
            UiIcon.draw(graphics, UiIcon.Kind.SPARKLE, middle, top + ICON_MIDDLE, ICON,
                    UiTheme.alpha(UiAccent.color(), alpha * popover.row(0)));
        } finally {
            UiRestFrame.pop(graphics);
        }
        line(graphics, font, 1, NAME, NAME_TOP, NAME_SCALE, UiTheme.alpha(UiAccent.text(), alpha));
        line(graphics, font, 2, DesktopWatermark.NAME, VERSION_TOP, VERSION_SCALE,
                UiTheme.alpha(UiAccent.textDim(), alpha));
        line(graphics, font, 3, build, BUILD_TOP, BUILD_SCALE, UiTheme.alpha(UiAccent.textFaint(), alpha));
    }

    private void line(GuiGraphics graphics, Font font, int row, Component text, float offset, float scale,
                      int color) {
        UiRestFrame.shift(graphics, 0.0f, popover.drift(row));
        try {
            UiRender.textCentered(graphics, font, text, left + WIDTH / 2.0f, top + offset, scale,
                    UiTheme.alpha(color, popover.row(row)), false);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }
}
