package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

// WHY: главное меню показано уменьшенным экраном: рабочий стол со строкой меню и доком или прежняя
// WHY: колонка кнопок, обои точками или гладкие, цветом темы или белые. Три выключателя группы видны
// WHY: здесь сразу, а переход между видами идёт долями, а не подменой картинки
final class PreviewDesk {
    private static final float ASPECT = 0.62f;
    private static final float SPEED = 9.0f;
    private static final float DOT_STEP = 9.0f;
    private static final float DOT_SIZE = 1.3f;
    private static final float BAR_HEIGHT = 7.0f;
    private static final float DOCK_HEIGHT = 15.0f;
    private static final float ICON_SIZE = 9.0f;
    private static final float ICON_STEP = 14.0f;
    private static final float BUTTON_HEIGHT = 9.0f;
    private static final UiIcon.Kind[] DOCK = {
            UiIcon.Kind.GLOBE, UiIcon.Kind.PERSON, UiIcon.Kind.SLIDERS, UiIcon.Kind.PHOTO, UiIcon.Kind.GEAR};

    private final Smooth desktop = new Smooth(HudConfig.menuDesktop() ? 1.0f : 0.0f, SPEED);
    private final Smooth dots = new Smooth(HudConfig.wallpaperDots() ? 1.0f : 0.0f, SPEED);
    private final Smooth themed = new Smooth(HudConfig.wallpaperThemed() ? 1.0f : 0.0f, SPEED);

    float paint(GuiGraphics graphics, float x, float y, float width) {
        float delta = UiFrame.delta();
        float desk = desktop.to(HudConfig.menuDesktop() ? 1.0f : 0.0f, delta);
        float dotted = dots.to(HudConfig.wallpaperDots() ? 1.0f : 0.0f, delta);
        float tinted = themed.to(HudConfig.wallpaperThemed() ? 1.0f : 0.0f, delta);
        float height = width * ASPECT;
        float radius = UiMetrics.radius(height * 0.18f);
        UiGlass.sunken(graphics, x, y, width, height, radius, 1.0f);
        int ink = UiTheme.mix(UiTheme.WHITE, UiAccent.color(), tinted);
        paintWallpaper(graphics, x, y, width, height, ink, dotted);
        if (desk > 0.01f) paintDesktop(graphics, x, y, width, height, desk);
        if (desk < 0.99f) paintClassic(graphics, x, y, width, height, 1.0f - desk);
        return y + height;
    }

    private static void paintWallpaper(GuiGraphics graphics, float x, float y, float width, float height, int ink,
                                       float dotted) {
        float planet = Math.min(width, height) * 0.3f;
        UiRender.dot(graphics, x + width * 0.68f, y + height * 0.58f, planet,
                UiTheme.alpha(ink, 0.32f * (1.0f - dotted)));
        if (dotted <= 0.01f) return;
        int columns = (int) ((width - DOT_STEP) / DOT_STEP);
        int rows = (int) ((height - DOT_STEP) / DOT_STEP);
        float originX = x + (width - (columns - 1) * DOT_STEP) / 2.0f;
        float originY = y + (height - (rows - 1) * DOT_STEP) / 2.0f;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                float shade = lit(column / (float) columns, row / (float) rows);
                UiRender.rect(graphics, originX + column * DOT_STEP - DOT_SIZE / 2.0f,
                        originY + row * DOT_STEP - DOT_SIZE / 2.0f, DOT_SIZE, DOT_SIZE,
                        UiTheme.alpha(ink, dotted * (0.08f + 0.6f * shade)));
            }
        }
    }

    // WHY: освещённая сторона планеты ярче: доля клетки от центра диска справа внизу
    private static float lit(float u, float v) {
        float dx = (u - 0.68f) / 0.42f;
        float dy = (v - 0.58f) / 0.62f;
        float inside = 1.0f - (dx * dx + dy * dy);
        return Math.max(0.0f, Math.min(1.0f, inside * (0.6f - dx * 0.5f - dy * 0.3f)));
    }

    private static void paintDesktop(GuiGraphics graphics, float x, float y, float width, float height, float share) {
        UiRender.rect(graphics, x + 4.0f, y + 3.0f, width - 8.0f, BAR_HEIGHT,
                UiTheme.withAlpha(UiTheme.WHITE, 0.06f * share));
        UiRender.dot(graphics, x + 10.0f, y + 3.0f + BAR_HEIGHT / 2.0f, 1.6f, UiTheme.alpha(UiAccent.text(), share));
        float dockWidth = DOCK.length * ICON_STEP + 6.0f;
        float dockX = x + (width - dockWidth) / 2.0f;
        float dockY = y + height - DOCK_HEIGHT - 4.0f + (1.0f - share) * 6.0f;
        UiGlass.panel(graphics, dockX, dockY, dockWidth, DOCK_HEIGHT, UiMetrics.radius(DOCK_HEIGHT), share, 0.3f);
        for (int index = 0; index < DOCK.length; index++) {
            UiIcon.draw(graphics, DOCK[index], dockX + 3.0f + ICON_STEP * (index + 0.5f), dockY + DOCK_HEIGHT / 2.0f,
                    ICON_SIZE, UiTheme.alpha(UiAccent.text(), share));
        }
    }

    private static void paintClassic(GuiGraphics graphics, float x, float y, float width, float height, float share) {
        float buttonWidth = width * 0.42f;
        float left = x + (width - buttonWidth) / 2.0f;
        float top = y + height * 0.36f;
        for (int index = 0; index < 3; index++) {
            float rowY = top + index * (BUTTON_HEIGHT + 3.0f) + (1.0f - share) * 4.0f;
            UiGlass.panel(graphics, left, rowY, buttonWidth, BUTTON_HEIGHT, UiMetrics.radius(BUTTON_HEIGHT), share,
                    0.2f);
            UiRender.rect(graphics, left + buttonWidth * 0.3f, rowY + BUTTON_HEIGHT / 2.0f - 0.6f, buttonWidth * 0.4f,
                    1.2f, UiTheme.alpha(UiAccent.textDim(), share));
        }
    }
}
