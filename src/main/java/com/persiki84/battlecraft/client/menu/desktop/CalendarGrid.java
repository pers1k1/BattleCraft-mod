package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// WHY: сетка дней как в календаре macOS: сегодняшний день в кружке цвета акцента, дни соседних
// WHY: месяцев приглушены, наведение мягко подсвечивает день кружком, который проявляется и гаснет
final class CalendarGrid {
    private static final float DAY_SCALE = 0.72f;
    private static final float MARK_RADIUS = 7.5f;
    private static final float HOVER_ALPHA = 0.12f;
    private static final float HOVER_SPEED = 16.0f;
    private static final Component[] NUMBERS = numbers();

    private final Smooth[] hover = new Smooth[CalendarPage.CELLS];
    private float left;
    private float top;
    private float cellWidth;
    private float cellHeight;

    CalendarGrid() {
        for (int cell = 0; cell < CalendarPage.CELLS; cell++) hover[cell] = new Smooth(0.0f, HOVER_SPEED);
    }

    private static Component[] numbers() {
        Component[] made = new Component[31];
        for (int day = 1; day <= made.length; day++) made[day - 1] = Component.literal(Integer.toString(day));
        return made;
    }

    void place(float x, float y, float width, float height) {
        left = x;
        top = y;
        cellWidth = width / CalendarPage.COLUMNS;
        cellHeight = height / CalendarPage.ROWS;
    }

    void advance(double mouseX, double mouseY, boolean hovering, float delta) {
        int over = hovering ? cellAt(mouseX, mouseY) : -1;
        for (int cell = 0; cell < CalendarPage.CELLS; cell++) hover[cell].to(cell == over ? 1.0f : 0.0f, delta);
    }

    private int cellAt(double mouseX, double mouseY) {
        int column = (int) Math.floor((mouseX - left) / cellWidth);
        int row = (int) Math.floor((mouseY - top) / cellHeight);
        if (column < 0 || column >= CalendarPage.COLUMNS || row < 0 || row >= CalendarPage.ROWS) return -1;
        return row * CalendarPage.COLUMNS + column;
    }

    void paint(GuiGraphics graphics, Font font, CalendarPage page, int todayCell, float shiftX, float alpha,
               boolean live) {
        if (alpha <= 0.01f) return;
        UiRestFrame.shift(graphics, shiftX, 0.0f);
        try {
            for (int cell = 0; cell < CalendarPage.CELLS; cell++) {
                paintCell(graphics, font, page, cell, cell == todayCell, live ? hover[cell].get() : 0.0f, alpha);
            }
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private void paintCell(GuiGraphics graphics, Font font, CalendarPage page, int cell, boolean today, float lit,
                           float alpha) {
        float centerX = left + (cell % CalendarPage.COLUMNS + 0.5f) * cellWidth;
        float cellTop = top + (float) (cell / CalendarPage.COLUMNS) * cellHeight;
        float centerY = cellTop + cellHeight / 2.0f;
        if (today) {
            UiRender.dot(graphics, centerX, centerY, MARK_RADIUS, UiTheme.alpha(UiAccent.color(), alpha));
        } else if (lit > 0.01f) {
            UiRender.dot(graphics, centerX, centerY, MARK_RADIUS, UiTheme.withAlpha(UiTheme.WHITE, HOVER_ALPHA * lit * alpha));
        }
        int ink = today ? UiTheme.WHITE : page.inside(cell) ? UiAccent.text() : UiAccent.textFaint();
        UiRender.labelCentered(graphics, font, NUMBERS[page.dayAt(cell) - 1], centerX,
                UiRender.centerY(cellTop, cellHeight, DAY_SCALE), DAY_SCALE, UiTheme.alpha(ink, alpha));
    }
}
