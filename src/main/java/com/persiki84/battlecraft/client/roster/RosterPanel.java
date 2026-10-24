package com.persiki84.battlecraft.client.roster;

import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

// WHY: окно списка: стекло, шапка сервера, строка состояния, карточки команд и подвал. Размер окна
// WHY: едет за составом, а не встаёт кадром: вошедший игрок раздвигает окно, как строка в меню
final class RosterPanel {
    private static final float GLIDE_SPEED = 9.0f;
    private static final float WRAP_SHARE = 0.94f;
    private static final float FIT_FLOOR = 0.5f;
    private static final int FIT_PASSES = 3;

    private final RosterLayout layout = new RosterLayout();
    private final RosterChrome chrome = new RosterChrome();
    private final RosterStatus status = new RosterStatus();
    private final Smooth left = new Smooth(0.0f, GLIDE_SPEED);
    private final Smooth top = new Smooth(0.0f, GLIDE_SPEED);
    private final Smooth width = new Smooth(0.0f, GLIDE_SPEED);
    private final Smooth height = new Smooth(0.0f, GLIDE_SPEED);
    private int laidVersion = Integer.MIN_VALUE;
    private float laidWidth;
    private float laidHeight;
    private float laidHeader;
    private float laidFooter;
    private float laidChrome;
    private float fit = 1.0f;
    private long advanced = -1L;

    public void prepare(GuiGraphics graphics, Font font, float screenWidth, float screenHeight) {
        chrome.measure(graphics, font, screenWidth * WRAP_SHARE - RosterLayout.PAD * 2.0f);
        float header = chrome.headerHeight(font);
        float footer = chrome.footerHeight(font);
        float widest = chrome.widest();
        if (laidVersion == RosterBook.version() && laidWidth == screenWidth && laidHeight == screenHeight
                && laidHeader == header && laidFooter == footer && laidChrome == widest) return;

        laidVersion = RosterBook.version();
        laidWidth = screenWidth;
        laidHeight = screenHeight;
        laidHeader = header;
        laidFooter = footer;
        laidChrome = widest;
        fit(graphics, font, screenWidth, screenHeight, header, footer, widest);
    }

    // WHY: окно, которое не влезает по высоте, ужимается целиком, а раскладка считается на экране,
    // WHY: увеличенном на ту же долю: в ужатом окне по ширине помещается больше карточек, и полок
    // WHY: становится меньше. Пара проходов сходится, пол держит текст читаемым
    private void fit(GuiGraphics graphics, Font font, float screenWidth, float screenHeight, float header,
                     float footer, float widest) {
        fit = 1.0f;
        layout.compute(graphics, font, screenWidth, screenHeight, header, footer, widest);
        for (int pass = 0; pass < FIT_PASSES; pass++) {
            float share = layout.fit(screenHeight / fit);
            if (share >= 1.0f || fit <= FIT_FLOOR) return;
            fit = Math.max(FIT_FLOOR, fit * share);
            layout.compute(graphics, font, screenWidth / fit, screenHeight / fit, header, footer, widest);
        }
    }

    public float scale() {
        return fit;
    }

    // WHY: окно рисуется дважды за кадр, пока догорает прошлое и проявляется новое; ход один на кадр
    public void advance() {
        long frame = UiFrame.frame();
        if (frame == advanced) return;
        advanced = frame;

        float delta = UiFrame.delta();
        RosterBook.advance(delta);
        left.to(layout.panelX, delta);
        top.to(layout.panelY, delta);
        width.to(layout.panelWidth, delta);
        height.to(layout.panelHeight, delta);
    }

    public void settle() {
        RosterBook.settle();
        left.snap(layout.panelX);
        top.snap(layout.panelY);
        width.snap(layout.panelWidth);
        height.snap(layout.panelHeight);
    }

    public void draw(GuiGraphics graphics, Font font, float alpha) {
        float x = left.get();
        float y = top.get();
        float wide = width.get();
        float tall = height.get();
        UiGlass.hush(graphics, x, y, wide, tall, RosterLayout.PANEL_RADIUS, alpha);
        UiGlass.window(graphics, x, y, wide, tall, RosterLayout.PANEL_RADIUS, alpha);

        float centerX = x + wide / 2.0f;
        int ink = UiTheme.alpha(HudInk.text(), alpha);
        float header = chrome.headerHeight(font);
        chrome.drawHeader(graphics, font, centerX, y + RosterLayout.PAD, ink);
        float statusY = y + RosterLayout.PAD + (header > 0.0f ? header + RosterLayout.SECTION_GAP : 0.0f);
        status.draw(graphics, font, x + RosterLayout.PAD, statusY, wide - RosterLayout.PAD * 2.0f, alpha);
        RosterCards.drawGroups(graphics, font, alpha);
        RosterCards.drawRows(graphics, font, layout, alpha);

        float footer = chrome.footerHeight(font);
        if (footer > 0.0f) chrome.drawFooter(graphics, font, centerX, y + tall - RosterLayout.PAD - footer, ink);
    }

    public float left() {
        return left.get();
    }

    public float top() {
        return top.get();
    }

    public float width() {
        return width.get();
    }

    public float height() {
        return height.get();
    }
}
