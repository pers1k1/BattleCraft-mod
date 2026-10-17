package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.CustomPreset;
import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.GlassKey;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.ManagerScreen;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.ToggleRow;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// WHY: все строки кастомизации собираются один раз на экран и живут до его закрытия: виджет хранит
// WHY: свою анимацию, и строка, показанная в группе, в обзоре вкладки и в поиске, обязана быть одной и
// WHY: той же, иначе правка двойника уходила бы мимо показанного значения
final class CustomCatalog {
    static final String RESET_ALL = "battlecraft.custom.reset.all";

    private final RowKit kit;
    private final Map<String, CustomSection> sections = new LinkedHashMap<>();
    private final Map<CustomTab, List<CustomSection>> byTab = new EnumMap<>(CustomTab.class);
    private final Map<String, HeadingRow> headings = new HashMap<>();
    private final Map<CustomTab, HeadingRow> resetHeadings = new EnumMap<>(CustomTab.class);
    private final Map<CustomTab, List<AbstractWidget>> resets;

    CustomCatalog(CustomHost host, int width) {
        kit = new RowKit(host, width);
        add(CustomTab.GLASS, GlassSections.build(kit));
        add(CustomTab.COLORS, ColorSections.build(kit));
        add(CustomTab.INTERFACE, InterfaceSections.build(kit));
        byTab.put(CustomTab.PRESETS, List.of());
        resets = TabResets.build(kit);
        for (CustomTab tab : resets.keySet()) {
            resetHeadings.put(tab, kit.heading("battlecraft.custom.group.reset"));
        }
    }

    private void add(CustomTab tab, List<CustomSection> built) {
        byTab.put(tab, built);
        for (CustomSection section : built) {
            sections.put(section.key(), section);
            headings.put(section.key(), kit.heading(section.titleKey()));
        }
    }

    RowKit kit() {
        return kit;
    }

    List<CustomSection> of(CustomTab tab) {
        return byTab.get(tab);
    }

    CustomSection section(String key) {
        return key == null ? null : sections.get(key);
    }

    HeadingRow heading(CustomSection section) {
        return headings.get(section.key());
    }

    HeadingRow resetHeading(CustomTab tab) {
        return resetHeadings.get(tab);
    }

    List<AbstractWidget> resets(CustomTab tab) {
        return resets.getOrDefault(tab, List.of());
    }

    // WHY: причина блокировки пишется в строку при каждой раскладке: жидкое стекло и акцент текста
    // WHY: меняются и строками, и пресетом, и мастером, а строка без причины выглядела бы сломанной
    void holdLocks() {
        boolean liquid = Customization.liquid();
        for (Map.Entry<MenuRow, GlassKey> entry : kit.glassRows().entrySet()) {
            String reason = entry.getValue().reasonKey(liquid);
            if (reason == null) {
                entry.getKey().unblock();
            } else {
                entry.getKey().block(Component.translatable(reason));
            }
        }
        holdHudAccent(kit.hudAccentRow());
    }

    // WHY: под акцентом уже весь текст, и худ вместе с ним, поэтому отдельная ручка худа не решает
    // WHY: ничего: она гаснет и говорит почему, а не молчит включённой без действия
    private static void holdHudAccent(ToggleRow row) {
        if (row == null) return;
        if (Customization.accentInk()) {
            row.block(Component.translatable("battlecraft.custom.blocked.accent_ink"));
        } else {
            row.unblock();
        }
    }

    CustomSection holding(String key) {
        for (CustomSection section : sections.values()) {
            if (key.equals(section.titleKey())) return section;
            for (AbstractWidget row : section.rows()) {
                if (key.equals(keyOf(row.getMessage()))) return section;
            }
        }
        return null;
    }

    AbstractWidget row(CustomSection section, String key) {
        for (AbstractWidget row : section.rows()) {
            if (key.equals(keyOf(row.getMessage()))) return row;
        }
        return null;
    }

    static CustomTab tabTitled(String key) {
        for (CustomTab tab : CustomTab.values()) {
            if (tab.titleKey().equals(key)) return tab;
        }
        return null;
    }

    static CustomPreset presetTitled(String key) {
        for (CustomPreset preset : CustomPreset.values()) {
            if (preset.translationKey().equals(key)) return preset;
        }
        return null;
    }

    // WHY: сброс вкладки живёт в конце её обзора, поэтому поиск ведёт на узел вкладки, а не группы
    CustomTab resetTab(String key) {
        for (Map.Entry<CustomTab, List<AbstractWidget>> entry : resets.entrySet()) {
            for (AbstractWidget row : entry.getValue()) {
                if (key.equals(keyOf(row.getMessage()))) return entry.getKey();
            }
        }
        return null;
    }

    AbstractWidget resetRow(CustomTab tab, String key) {
        for (AbstractWidget row : resets(tab)) {
            if (key.equals(keyOf(row.getMessage()))) return row;
        }
        return null;
    }

    // WHY: ориентиры для поиска рабочего стола собираются теми же сборщиками, что и строки: отдельный
    // WHY: список названий разошёлся бы с экраном после первой же новой ручки. Пресеты и общий сброс
    // WHY: живут не в строках вкладок, а в галерее пресетов, и добавляются её же ключами
    List<ManagerScreen.Landmark> landmarks() {
        List<ManagerScreen.Landmark> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CustomTab tab : CustomTab.values()) {
            mark(found, seen, tab.titleKey());
            if (tab == CustomTab.PRESETS) markPresets(found, seen);
            for (CustomSection section : byTab.get(tab)) {
                mark(found, seen, section.titleKey());
                for (AbstractWidget row : section.rows()) {
                    mark(found, seen, keyOf(row.getMessage()));
                }
            }
            for (AbstractWidget row : resets(tab)) {
                mark(found, seen, keyOf(row.getMessage()));
            }
        }
        return found;
    }

    private static void markPresets(List<ManagerScreen.Landmark> found, Set<String> seen) {
        for (CustomPreset preset : CustomPreset.values()) {
            mark(found, seen, preset.translationKey());
        }
        mark(found, seen, RESET_ALL);
    }

    private static void mark(List<ManagerScreen.Landmark> found, Set<String> seen, String key) {
        if (key != null && seen.add(key)) found.add(new ManagerScreen.Landmark(Component.translatable(key), key));
    }

    static String keyOf(Component text) {
        if (!(text.getContents() instanceof TranslatableContents contents)) return null;
        return contents.getArgs().length == 0 ? contents.getKey() : null;
    }
}
