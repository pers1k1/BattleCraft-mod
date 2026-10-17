package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.PaletteKey;
import net.minecraft.client.gui.components.AbstractWidget;

// WHY: строки групп и пресеты собираются отдельно от экрана, но их отклик меняет то, что видит экран:
// WHY: блокировки соседних строк, окно палитры, выбор картинки острова, второе нажатие необратимого
interface CustomHost {
    void refresh();

    void openPalette(PaletteKey key);

    void pickAvatarPicture();

    boolean sure(String key);

    boolean asking(String key);

    void focus(AbstractWidget widget);
}
