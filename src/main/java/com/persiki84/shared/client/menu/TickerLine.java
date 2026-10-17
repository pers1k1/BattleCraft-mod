package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTicker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Optional;

// WHY: подпись меню, в которой живёт число (счёт, цена, остаток, отсчёт), меняется по цифрам, как
// WHY: таймер острова: стоящие знаки не трогаются, сменившиеся уходят вверх, новые приходят снизу.
// WHY: Ходом идёт только смена, где цифры есть и до, и после: смена одного слова на другое встаёт
// WHY: сразу, как и раньше. Строка снимается с компонента только при смене его содержимого: та же
// WHY: ссылка или равный компонент, собранный заново, в строку не разворачиваются. Раскрашенный
// WHY: компонент и строка шире коробки идут прежним пером: тикер несёт один цвет и не умеет
// WHY: бегущей строки
public final class TickerLine {
    private final UiTicker ticker;
    private final float align;
    private final float tracking;

    private Component source;
    private String text = "";
    private boolean plain;
    private boolean counted;
    long seen;

    public TickerLine(float align) {
        this(align, 0.0f);
    }

    public TickerLine(float align, float tracking) {
        this.align = align;
        this.tracking = tracking;
        this.ticker = new UiTicker().align(align).tracking(tracking).rising();
    }

    public void box(GuiGraphics graphics, Font font, Component value, float boxX, float boxY, float boxHeight,
                    float boxWidth, float scale, int color) {
        take(value);
        if (text.isEmpty()) return;

        boolean overflowing = plain && ticker.width(graphics, font, scale) > boxWidth;
        if (overflowing && ticker.moving()) ticker.snap(text);
        if (!plain || overflowing) {
            UiRender.textTrackedBox(graphics, font, value, boxX, boxY, boxHeight, boxWidth, scale, tracking,
                    color, false, align);
            return;
        }
        float y = UiRender.centerY(boxY, boxHeight, UiRender.crisp(graphics, scale));
        ticker.draw(graphics, font, boxX + boxWidth * align, y, scale, color);
    }

    // WHY: строка без коробки: anchorX - левый край, середина или правый край по align, y как у labelScaled
    public void at(GuiGraphics graphics, Font font, Component value, float anchorX, float y, float scale, int color) {
        take(value);
        if (text.isEmpty()) return;

        if (plain) {
            ticker.draw(graphics, font, anchorX, y, scale, color);
        } else if (align >= 1.0f) {
            UiRender.labelRight(graphics, font, value, anchorX, y, scale, color);
        } else if (align > 0.0f) {
            UiRender.labelCentered(graphics, font, value, anchorX, y, scale, color);
        } else {
            UiRender.labelScaled(graphics, font, value, anchorX, y, scale, color);
        }
    }

    // WHY: подпись, которую в этом кадре рисует другой путь (смена слова целиком), всё равно ведётся
    // WHY: тикером: иначе первая смена числа после неё встала бы без хода, тикер не знал бы прошлого
    public void follow(Component value) {
        take(value);
        ticker.set(text);
    }

    public void forget() {
        source = null;
        text = "";
        counted = false;
        ticker.reset();
    }

    private void take(Component value) {
        if (value == source || value.equals(source)) return;

        source = value;
        String next = value.getString();
        if (next.equals(text) && !text.isEmpty()) return;

        boolean digits = hasDigit(next);
        plain = uncoloured(value) && next.indexOf(ChatFormatting.PREFIX_CODE) < 0;
        if (digits && counted && plain) {
            ticker.set(next);
        } else {
            ticker.snap(next);
        }
        counted = digits;
        text = next;
    }

    private static boolean hasDigit(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isDigit(value.charAt(index))) return true;
        }
        return false;
    }

    private static boolean uncoloured(Component value) {
        return value.visit((style, part) -> style.getColor() == null
                ? Optional.<Boolean>empty() : Optional.of(Boolean.TRUE), Style.EMPTY).isEmpty();
    }
}
