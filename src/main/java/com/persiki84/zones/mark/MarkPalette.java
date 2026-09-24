package com.persiki84.zones.mark;

import java.util.ArrayList;
import java.util.List;

// WHY: цвет выбирается окном палитры и команда принимает любой, а этот набор остался подсказками
// WHY: аргумента цвета, чтобы оператор в чате не набирал число вслепую
public final class MarkPalette {
    public static final int[] COLORS = {
            0xFFE7E9F4, 0xFFCE2A22, 0xFF3F7BD8, 0xFF3FA75A,
            0xFFE0B33C, 0xFFD9772E, 0xFF9B59B6, 0xFF34C6C6,
            0xFF000000
    };

    private MarkPalette() {}

    public static List<String> suggestions() {
        List<String> values = new ArrayList<>(COLORS.length);
        for (int color : COLORS) {
            values.add(Integer.toString(color));
        }
        return values;
    }
}
