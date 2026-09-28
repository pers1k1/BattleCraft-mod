package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.Locale;

// WHY: календарь под часами строки меню: стеклянная панель с месяцем, стрелками и сеткой дней.
// WHY: Смена месяца едет сеткой в сторону листания с наплывом, а щелчок по заголовку возвращает
// WHY: к текущему месяцу. Названия и первый день недели берутся из языка игры, не системы
final class DesktopCalendar {
    static final float WIDTH = 168.0f;

    private static final float PAD = 10.0f;
    private static final float RADIUS = 16.0f;
    private static final float HEADER_TOP = 9.0f;
    private static final float HEADER_HEIGHT = 14.0f;
    private static final float TITLE_SCALE = 0.9f;
    private static final float WEEK_TOP = 29.0f;
    private static final float WEEK_HEIGHT = 10.0f;
    private static final float WEEK_SCALE = 0.58f;
    private static final float GRID_TOP = 42.0f;
    private static final float GRID_HEIGHT = 108.0f;
    private static final float HEIGHT = GRID_TOP + GRID_HEIGHT + 8.0f;
    private static final float ARROW = 8.0f;
    private static final float ARROW_STEP = 17.0f;
    private static final float ARROW_HIT = 8.0f;
    private static final float ARROW_HALO = 7.5f;
    private static final float GRID_TRAVEL = 26.0f;
    private static final float TITLE_TRAVEL = 10.0f;
    private static final long TODAY_CHECK_MS = 1000L;

    private final DesktopPopover popover = new DesktopPopover();
    private final CalendarGrid grid = new CalendarGrid();
    private final Spring slide = new Spring(0.4f, 1.0f, 1.0f);
    private final Smooth backHover = new Smooth(0.0f, 16.0f);
    private final Smooth forwardHover = new Smooth(0.0f, 16.0f);
    private CalendarPage page;
    private CalendarPage leaving;
    private Component[] weekdays = new Component[0];
    private Locale locale = Locale.ROOT;
    private DayOfWeek firstDay = DayOfWeek.MONDAY;
    private String language = "";
    private LocalDate today = LocalDate.now();
    private long todayCheckedAt;
    private int todayCell = -1;
    private int leavingTodayCell = -1;
    private int direction = 1;
    private int hoveredArrow;
    private float left;
    private float top;

    boolean open() {
        return popover.open();
    }

    void toggle() {
        if (!popover.open()) jumpHome();
        popover.toggle();
    }

    void close() {
        popover.hide();
    }

    void dismiss() {
        popover.dismiss();
    }

    private void jumpHome() {
        refreshToday(true);
        refreshLanguage();
        turnTo(YearMonth.from(today), false);
        leaving = null;
        slide.snap(1.0f);
    }

    private void refreshLanguage() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        if (code.equals(language) && page != null) return;
        language = code;
        locale = DesktopClock.locale();
        firstDay = WeekFields.of(locale).getFirstDayOfWeek();
        weekdays = CalendarPage.weekdays(locale, firstDay);
        YearMonth shown = page == null ? YearMonth.from(today) : page.month();
        page = CalendarPage.of(shown, locale, firstDay);
        leaving = null;
        todayCell = page.cellOf(today);
    }

    private void refreshToday(boolean now) {
        long millis = System.currentTimeMillis();
        if (!now && millis - todayCheckedAt < TODAY_CHECK_MS) return;
        todayCheckedAt = millis;
        LocalDate fresh = LocalDate.now();
        if (fresh.equals(today) && page != null) return;
        today = fresh;
        if (page != null) todayCell = page.cellOf(today);
    }

    private void turnTo(YearMonth month, boolean animated) {
        if (page != null && page.month().equals(month)) return;
        if (animated && page != null) {
            direction = month.isAfter(page.month()) ? 1 : -1;
            leaving = page;
            leavingTodayCell = todayCell;
            slide.snap(0.0f);
        } else {
            leaving = null;
            slide.snap(1.0f);
        }
        page = CalendarPage.of(month, locale, firstDay);
        todayCell = page.cellOf(today);
    }

    void render(GuiGraphics graphics, Font font, float anchorRight, float anchorTop, float pivotX,
                int mouseX, int mouseY) {
        float delta = UiFrame.delta();
        popover.advance(delta);
        if (!popover.visible()) return;
        refreshLanguage();
        refreshToday(false);
        left = anchorRight - WIDTH;
        top = anchorTop;
        advance(mouseX, mouseY, delta);
        popover.begin(graphics, Math.max(left, Math.min(anchorRight, pivotX)), top);
        try {
            paint(graphics, font, popover.alpha());
        } finally {
            popover.end(graphics);
        }
    }

    private void advance(int mouseX, int mouseY, float delta) {
        slide.to(1.0f, delta);
        if (slide.get() >= 0.999f) leaving = null;
        grid.place(left + PAD, top + GRID_TOP, WIDTH - PAD * 2.0f, GRID_HEIGHT);
        grid.advance(mouseX, mouseY, popover.open(), delta);
        int arrow = popover.open() ? arrowAt(mouseX, mouseY) : 0;
        if (arrow != 0 && arrow != hoveredArrow) UiSound.hover();
        hoveredArrow = arrow;
        backHover.to(arrow < 0 ? 1.0f : 0.0f, delta);
        forwardHover.to(arrow > 0 ? 1.0f : 0.0f, delta);
    }

    private void paint(GuiGraphics graphics, Font font, float alpha) {
        UiGlass.above(graphics, left, top, WIDTH, HEIGHT);
        UiGlass.hush(graphics, left, top, WIDTH, HEIGHT, RADIUS, alpha);
        UiGlass.window(graphics, left, top, WIDTH, HEIGHT, RADIUS, alpha);
        UiGlass.layer(graphics);
        paintHeader(graphics, font, alpha * popover.row(0));
        paintWeekdays(graphics, font, alpha * popover.row(1));
        UiRestFrame.shift(graphics, 0.0f, popover.drift(2));
        try {
            paintPages(graphics, font, alpha * popover.row(2));
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private void paintHeader(GuiGraphics graphics, Font font, float alpha) {
        float titleY = UiRender.centerY(top + HEADER_TOP, HEADER_HEIGHT, TITLE_SCALE);
        float eased = slide.get();
        UiRender.clip(graphics, left + PAD - 2.0f, top + HEADER_TOP - 2.0f, titleRoom() + 4.0f, HEADER_HEIGHT + 4.0f);
        try {
            if (leaving != null) {
                paintTitle(graphics, font, leaving, titleY, -direction * eased * TITLE_TRAVEL, alpha * (1.0f - eased));
            }
            paintTitle(graphics, font, page, titleY, direction * (1.0f - eased) * TITLE_TRAVEL, alpha * eased);
        } finally {
            graphics.disableScissor();
        }
        paintArrow(graphics, UiIcon.Kind.CHEVRON_LEFT, arrowX(-1), backHover.get(), alpha);
        paintArrow(graphics, UiIcon.Kind.CHEVRON_RIGHT, arrowX(1), forwardHover.get(), alpha);
    }

    private void paintTitle(GuiGraphics graphics, Font font, CalendarPage shown, float y, float shiftX, float alpha) {
        if (alpha <= 0.01f) return;
        UiRestFrame.shift(graphics, shiftX, 0.0f);
        try {
            UiRender.labelScaled(graphics, font, shown.title(), left + PAD, y, TITLE_SCALE,
                    UiTheme.alpha(UiAccent.text(), alpha));
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private void paintArrow(GuiGraphics graphics, UiIcon.Kind glyph, float x, float lit, float alpha) {
        float y = top + HEADER_TOP + HEADER_HEIGHT / 2.0f;
        if (lit > 0.01f) {
            UiRender.dot(graphics, x, y, ARROW_HALO, UiTheme.withAlpha(UiTheme.WHITE, 0.12f * lit * alpha));
        }
        UiIcon.draw(graphics, glyph, x, y, ARROW, UiTheme.alpha(UiAccent.text(), alpha * (0.75f + 0.25f * lit)));
    }

    private void paintWeekdays(GuiGraphics graphics, Font font, float alpha) {
        float cell = (WIDTH - PAD * 2.0f) / CalendarPage.COLUMNS;
        float y = UiRender.centerY(top + WEEK_TOP, WEEK_HEIGHT, WEEK_SCALE);
        int ink = UiTheme.alpha(UiAccent.textDim(), alpha);
        for (int column = 0; column < weekdays.length; column++) {
            UiRender.labelCentered(graphics, font, weekdays[column], left + PAD + (column + 0.5f) * cell, y, WEEK_SCALE, ink);
        }
    }

    private void paintPages(GuiGraphics graphics, Font font, float alpha) {
        float eased = slide.get();
        UiRender.clip(graphics, left + 2.0f, top + GRID_TOP - 1.0f, WIDTH - 4.0f, GRID_HEIGHT + 2.0f);
        try {
            if (leaving != null) {
                grid.paint(graphics, font, leaving, leavingTodayCell, -direction * eased * GRID_TRAVEL,
                        alpha * (1.0f - eased), false);
            }
            grid.paint(graphics, font, page, todayCell, direction * (1.0f - eased) * GRID_TRAVEL, alpha * eased, true);
        } finally {
            graphics.disableScissor();
        }
    }

    private float titleRoom() {
        return arrowX(-1) - ARROW_HALO - (left + PAD);
    }

    private float arrowX(int side) {
        float forward = left + WIDTH - PAD - ARROW_HALO + 2.0f;
        return side > 0 ? forward : forward - ARROW_STEP;
    }

    private int arrowAt(double mouseX, double mouseY) {
        double y = top + HEADER_TOP + HEADER_HEIGHT / 2.0f;
        for (int side = -1; side <= 1; side += 2) {
            double dx = mouseX - arrowX(side);
            double dy = mouseY - y;
            if (dx * dx + dy * dy <= ARROW_HIT * ARROW_HIT) return side;
        }
        return 0;
    }

    boolean contains(double mouseX, double mouseY) {
        return popover.open() && mouseX >= left && mouseX <= left + WIDTH && mouseY >= top && mouseY <= top + HEIGHT;
    }

    boolean click(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return false;
        int arrow = arrowAt(mouseX, mouseY);
        if (arrow != 0) {
            UiSound.press();
            turnTo(page.month().plusMonths(arrow), true);
            return true;
        }
        if (onTitle(mouseX, mouseY) && !page.month().equals(YearMonth.from(today))) {
            UiSound.press();
            turnTo(YearMonth.from(today), true);
        }
        return true;
    }

    private boolean onTitle(double mouseX, double mouseY) {
        return mouseX >= left + PAD && mouseX <= left + PAD + titleRoom()
                && mouseY >= top + HEADER_TOP - 2.0f && mouseY <= top + HEADER_TOP + HEADER_HEIGHT + 2.0f;
    }
}
