package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuSearch;
import com.persiki84.shared.client.menu.studio.StudioStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// WHY: колонка справа раскладывается из тех же строк в трёх видах: одна группа, вся вкладка обзором
// WHY: и найденное поиском по всем вкладкам. Строки не пересоздаются, поэтому правка в любом виде
// WHY: это правка той же ручки, а её анимация переживает смену вида
final class CustomInspector {
    private static final int GROUP_GAP = RowKit.ROW_HEIGHT / 3;

    private final CustomCatalog catalog;
    private final HeadingRow resetHeading;
    private final HeadingRow nothing;
    private final Map<String, ActionRow> groupResets = new HashMap<>();

    CustomInspector(CustomCatalog catalog) {
        this.catalog = catalog;
        RowKit kit = catalog.kit();
        resetHeading = kit.heading("battlecraft.custom.group.reset");
        nothing = kit.heading("battlecraft.menu.search.empty");
        for (CustomTab tab : CustomTab.values()) {
            for (CustomSection section : catalog.of(tab)) {
                if (section.resettable()) groupResets.put(section.key(), resetRow(kit, section));
            }
        }
    }

    private static ActionRow resetRow(RowKit kit, CustomSection section) {
        ActionRow row = new ActionRow(0, 0, kit.width(), RowKit.ROW_HEIGHT,
                Component.translatable("battlecraft.custom.reset.section", section.title()),
                () -> Component.translatable("battlecraft.custom.reset"),
                () -> TabResets.done(kit, section.reset()));
        row.hint("battlecraft.custom.reset.section" + RowKit.HINT);
        return row;
    }

    void section(StudioStack stack, int x, int width, CustomSection section) {
        boolean first = true;
        if (section.headed()) {
            put(stack, catalog.heading(section), x, width, false);
            first = false;
        }
        for (AbstractWidget row : section.rows()) {
            put(stack, row, x, width, !first && row instanceof HeadingRow);
            first = false;
        }
        ActionRow reset = groupResets.get(section.key());
        if (reset == null) return;
        put(stack, resetHeading, x, width, true);
        put(stack, reset, x, width, false);
    }

    void tab(StudioStack stack, int x, int width, CustomTab tab) {
        boolean first = true;
        for (CustomSection section : catalog.of(tab)) {
            if (section.headed()) {
                put(stack, catalog.heading(section), x, width, !first);
                first = false;
            }
            for (AbstractWidget row : section.rows()) {
                put(stack, row, x, width, !first && row instanceof HeadingRow);
                first = false;
            }
        }
        List<AbstractWidget> resets = catalog.resets(tab);
        if (resets.isEmpty()) return;
        put(stack, catalog.resetHeading(tab), x, width, true);
        for (AbstractWidget row : resets) {
            put(stack, row, x, width, false);
        }
    }

    // WHY: найденное собирается со всех вкладок под заголовками своих групп: без заголовка строка
    // WHY: «Цвет» или «Размер» не говорит, к чему она относится
    void search(StudioStack stack, int x, int width, String needle) {
        boolean found = false;
        for (CustomTab tab : CustomTab.values()) {
            for (CustomSection section : catalog.of(tab)) {
                found |= searchGroup(stack, x, width, catalog.heading(section), section.rows(), needle, found);
            }
            found |= searchGroup(stack, x, width, catalog.resetHeading(tab), catalog.resets(tab), needle, found);
        }
        if (!found) put(stack, nothing, x, width, false);
    }

    private static boolean searchGroup(StudioStack stack, int x, int width, HeadingRow heading,
                                       List<AbstractWidget> rows, String needle, boolean gap) {
        boolean headed = false;
        for (AbstractWidget row : rows) {
            if (!MenuSearch.matches(row, needle)) continue;
            if (!headed && heading != null) put(stack, heading, x, width, gap);
            headed = true;
            put(stack, row, x, width, false);
        }
        return headed;
    }

    private static void put(StudioStack stack, AbstractWidget row, int x, int width, boolean gap) {
        row.setWidth(width);
        stack.add(row, x, gap ? GROUP_GAP : 0);
    }
}
