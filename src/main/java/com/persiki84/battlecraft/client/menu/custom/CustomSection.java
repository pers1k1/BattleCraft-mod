package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.PaletteKey;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;

// WHY: группа настроек это узел навигатора студии: строки группы стоят справа, её образец в центре,
// WHY: а сброс группы и её цвета достаются из меню по правой кнопке. Плоский список вкладки для поиска
// WHY: собирается из тех же групп, поэтому строка, найденная поиском, это та же самая строка
public record CustomSection(String key, CustomTab tab, String titleKey, Component title, boolean headed,
                            List<AbstractWidget> rows, Runnable reset, PreviewFocus focus,
                            List<PaletteKey> colors) {

    public static CustomSection of(String key, CustomTab tab, String titleKey, boolean headed,
                                   List<AbstractWidget> rows, Runnable reset, PreviewFocus focus,
                                   List<PaletteKey> colors) {
        return new CustomSection(key, tab, titleKey, Component.translatable(titleKey), headed, List.copyOf(rows),
                reset, focus, List.copyOf(colors));
    }

    public boolean resettable() {
        return reset != null;
    }
}
