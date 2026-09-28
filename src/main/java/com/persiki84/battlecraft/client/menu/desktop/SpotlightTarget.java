package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.shared.client.menu.SearchBeacon;
import com.persiki84.shared.client.ui.UiMetrics;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

interface SpotlightTarget {
    // WHY: высоты строк и границы списков сняты с исходников 1.20.1: OptionsList везде строится
    // WHY: как (32, height - 32, 25), KeyBindsList как (20, height - 32, 20)
    int OPTION_ROW = 25;
    int OPTION_TOP = 32;
    int OPTION_BOTTOM = 32;
    int KEY_ROW = 20;
    int KEY_TOP = 20;
    int KEY_BOTTOM = 32;
    int KEY_NAME_GAP = 15;
    int KEY_NAME_PAD = 4;

    Spot locate(Screen screen);

    record Spot(AbstractWidget from, AbstractWidget to, int reach, AbstractSelectionList<?> list) {
        void paint(GuiGraphics graphics, float strength) {
            float left = from.getX() - reach;
            float top = from.getY();
            float width = to.getX() + to.getWidth() - left;
            float height = from.getHeight();
            if (!inside(left + width / 2.0f, top, top + height)) return;

            SearchBeacon.paint(graphics, left, top, width, height, UiMetrics.radius(height), strength);
        }

        private boolean inside(float centreX, float top, float bottom) {
            return list == null || (list.isMouseOver(centreX, top) && list.isMouseOver(centreX, bottom));
        }
    }

    static SpotlightTarget option(String captionKey, Function<Options, OptionInstance<?>> option) {
        return screen -> {
            Spot listed = option == null ? null : inLists(screen, option.apply(Minecraft.getInstance().options));
            return listed != null ? listed : amongChildren(screen, captionKey);
        };
    }

    static SpotlightTarget key(KeyMapping mapping) {
        return screen -> {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof KeyBindsList list) return inKeyList(screen, list, mapping);
            }
            return null;
        };
    }

    private static Spot inLists(Screen screen, OptionInstance<?> option) {
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof OptionsList list)) continue;

            AbstractWidget widget = list.findOption(option);
            if (widget == null) continue;

            centre(list, rowHolding(list, widget), OPTION_ROW, screen.height - OPTION_TOP - OPTION_BOTTOM);
            return new Spot(widget, widget, 0, list);
        }
        return null;
    }

    private static int rowHolding(OptionsList list, AbstractWidget widget) {
        int index = 0;
        for (GuiEventListener row : list.children()) {
            if (row instanceof ContainerEventHandler holder && holder.children().contains(widget)) return index;
            index++;
        }
        return -1;
    }

    // WHY: та же формула, что у защищённого centerScrollOn: середина строки встаёт в середину окна
    // WHY: списка, а за края прокрутку отводит сам setScrollAmount
    private static void centre(AbstractSelectionList<?> list, int row, int rowHeight, int viewport) {
        if (row < 0) return;
        list.setScrollAmount(row * rowHeight + rowHeight / 2.0 - viewport / 2.0);
    }

    private static Spot amongChildren(Screen screen, String captionKey) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && captioned(widget.getMessage(), captionKey)) {
                return new Spot(widget, widget, 0, null);
            }
            Spot listed = child instanceof OptionsList list ? captionedInList(screen, list, captionKey) : null;
            if (listed != null) return listed;
        }
        return null;
    }

    // WHY: разрешение полноэкранного режима экран видео создаёт сам, в Options такой опции нет, и
    // WHY: findOption её не находит: строка ищется в списке по подписи
    private static Spot captionedInList(Screen screen, OptionsList list, String captionKey) {
        for (GuiEventListener row : list.children()) {
            if (!(row instanceof ContainerEventHandler holder)) continue;

            for (GuiEventListener part : holder.children()) {
                if (part instanceof AbstractWidget widget && captioned(widget.getMessage(), captionKey)) {
                    centre(list, rowHolding(list, widget), OPTION_ROW, screen.height - OPTION_TOP - OPTION_BOTTOM);
                    return new Spot(widget, widget, 0, list);
                }
            }
        }
        return null;
    }

    // WHY: кнопка опции подписана как options.generic_value(название, значение): название лежит
    // WHY: первым аргументом, а у простой кнопки перехода ключ стоит на самой подписи
    private static boolean captioned(Component message, String captionKey) {
        if (!(message.getContents() instanceof TranslatableContents contents)) return false;
        if (captionKey.equals(contents.getKey())) return true;

        Object[] args = contents.getArgs();
        return args.length > 0 && args[0] instanceof Component caption
                && caption.getContents() instanceof TranslatableContents inner && captionKey.equals(inner.getKey());
    }

    private static Spot inKeyList(Screen screen, KeyBindsList list, KeyMapping mapping) {
        KeyMapping[] sorted = Minecraft.getInstance().options.keyMappings.clone();
        Arrays.sort(sorted);
        int row = rowOf(sorted, mapping);
        List<KeyBindsList.Entry> rows = list.children();
        if (row < 0 || row >= rows.size() || !(rows.get(row) instanceof KeyBindsList.KeyEntry entry)) return null;

        List<? extends GuiEventListener> parts = entry.children();
        if (parts.size() < 2 || !(parts.get(0) instanceof AbstractWidget change)
                || !(parts.get(1) instanceof AbstractWidget reset)) {
            return null;
        }
        centre(list, row, KEY_ROW, screen.height - KEY_TOP - KEY_BOTTOM);
        return new Spot(change, reset, KEY_NAME_GAP + widestName(sorted) + KEY_NAME_PAD, list);
    }

    // WHY: повтор раскладки KeyBindsList: те же отсортированные клавиши, и перед каждой новой
    // WHY: категорией своя строка-заголовок, поэтому номер строки считается вместе с ними
    private static int rowOf(KeyMapping[] sorted, KeyMapping wanted) {
        String category = null;
        int row = 0;
        for (KeyMapping mapping : sorted) {
            if (!mapping.getCategory().equals(category)) {
                category = mapping.getCategory();
                row++;
            }
            if (mapping == wanted) return row;
            row++;
        }
        return -1;
    }

    // WHY: имя клавиши рисуется от left + 90 - maxNameWidth, кнопка от left + 105: подсветка
    // WHY: захватывает имя, поэтому отступает от кнопки на ту же самую ширину
    private static int widestName(KeyMapping[] mappings) {
        Font font = Minecraft.getInstance().font;
        int widest = 0;
        for (KeyMapping mapping : mappings) {
            widest = Math.max(widest, font.width(Component.translatable(mapping.getName())));
        }
        return widest;
    }
}
