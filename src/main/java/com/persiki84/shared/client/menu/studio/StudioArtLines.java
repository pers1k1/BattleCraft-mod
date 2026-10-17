package com.persiki84.shared.client.menu.studio;

import com.persiki84.shared.client.menu.TickerLine;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Objects;

// WHY: строки под артом инспектора (шанс, остаток, число записей) перекатываются по цифрам, пока
// WHY: выбрана та же вещь. Смена выбранного сбрасывает все строки: число другой вещи встаёт сразу,
// WHY: вместе с проездом арта, а не катится от числа соседки. Вещь задаётся ключом и номером, чтобы
// WHY: сверка в кадре не упаковывала число и не собирала составной ключ
public final class StudioArtLines {
    public static final int NO_INDEX = -1;

    private final TickerLine[] lines;
    private Object subject;
    private int index = NO_INDEX;
    private boolean known;

    public StudioArtLines(int count) {
        lines = new TickerLine[count];
        for (int slot = 0; slot < count; slot++) lines[slot] = new TickerLine(0.5f);
    }

    public StudioArtLines about(Object next) {
        return about(next, NO_INDEX);
    }

    public StudioArtLines about(Object next, int nextIndex) {
        if (known && nextIndex == index && Objects.equals(next, subject)) return this;

        known = true;
        subject = next;
        index = nextIndex;
        for (TickerLine line : lines) line.forget();
        return this;
    }

    public void line(int slot, GuiGraphics graphics, Font font, Component text, float left, float top,
                     float boxHeight, float width, float scale, int color) {
        lines[slot].box(graphics, font, text, left, top, boxHeight, width, scale, color);
    }
}
