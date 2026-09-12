package com.persiki84.shared.client.menu;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MenuSearch {

    private MenuSearch() {}

    public static String needle(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean matches(AbstractWidget row, String needle) {
        if (row instanceof HeadingRow) return false;
        if (holds(row.getMessage(), needle)) return true;
        return row instanceof MenuRow menuRow && holds(menuRow.note(), needle);
    }

    // WHY: заголовок группы остаётся только над своими найденными строками: без него отобранные
    // WHY: настройки теряют раздел, а сам по себе он объясняет пустое место
    public static List<AbstractWidget> filter(List<AbstractWidget> rows, String needle) {
        List<AbstractWidget> kept = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            AbstractWidget row = rows.get(index);
            if (row instanceof HeadingRow) {
                if (groupHolds(rows, index, needle)) kept.add(row);
            } else if (matches(row, needle)) {
                kept.add(row);
            }
        }
        return kept;
    }

    private static boolean groupHolds(List<AbstractWidget> rows, int heading, String needle) {
        for (int index = heading + 1; index < rows.size(); index++) {
            AbstractWidget row = rows.get(index);
            if (row instanceof HeadingRow) return false;
            if (matches(row, needle)) return true;
        }
        return false;
    }

    private static boolean holds(Component text, String needle) {
        return text != null && text.getString().toLowerCase(Locale.ROOT).contains(needle);
    }
}
