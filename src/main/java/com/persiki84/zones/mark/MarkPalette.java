package com.persiki84.zones.mark;

import net.minecraft.network.chat.Component;

public final class MarkPalette {
    // WHY: палитра одна на меню меток и на карту: разъехавшиеся наборы дали бы цвет, который
    // WHY: выбран на карте, но не показывается в списке значений меню
    public static final int[] COLORS = {
            0xFFE7E9F4, 0xFFCE2A22, 0xFF3F7BD8, 0xFF3FA75A,
            0xFFE0B33C, 0xFFD9772E, 0xFF9B59B6, 0xFF34C6C6
    };

    private MarkPalette() {}

    public static int indexOf(int color) {
        for (int index = 0; index < COLORS.length; index++) {
            if (COLORS[index] == color) return index;
        }
        return 0;
    }

    public static Component name(int index) {
        return Component.translatable("zones.mark.menu.color." + index);
    }
}
