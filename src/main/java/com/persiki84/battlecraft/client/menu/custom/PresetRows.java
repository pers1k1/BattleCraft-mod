package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.ConfigSection;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

// WHY: строки колонки справа у пресета создаются один раз и только переставляются: выбор другой
// WHY: плитки меняет набор строк, а не их экземпляры, поэтому переходы строк не обрываются
final class PresetRows {
    private static final int NAME_LIMIT = 40;
    private static final Component SURE = Component.translatable("studio.menu.sure");
    private static final Component DO_DELETE = Component.translatable("battlecraft.custom.mine.do_delete");

    final Set<ConfigSection> chosen = EnumSet.allOf(ConfigSection.class);
    final FieldRow newName;
    final FieldRow rename;
    final ActionRow apply;
    final ActionRow resetAll;
    final ActionRow renameDone;
    final ActionRow overwrite;
    final ActionRow duplicate;
    final ActionRow delete;
    final ActionRow save;
    final ActionRow folder;
    final HeadingRow resetHeading;
    final HeadingRow sectionsHeading;
    final List<ToggleRow> sections = new ArrayList<>();

    PresetRows(RowKit kit, PresetDesk desk, Consumer<String> typedNew, Consumer<String> typedRename) {
        newName = field(kit, typedNew);
        rename = field(kit, typedRename);
        apply = kit.action("battlecraft.custom.mine.apply", "battlecraft.custom.mine.do_apply", desk::applySelected);
        resetAll = kit.action(CustomCatalog.RESET_ALL, "battlecraft.custom.reset", desk::resetAll).alerting();
        renameDone = kit.action("battlecraft.custom.mine.rename", "battlecraft.custom.mine.do_rename", desk::renameSelected);
        overwrite = kit.action("battlecraft.custom.mine.overwrite", "battlecraft.custom.mine.do_overwrite",
                desk::overwriteSelected);
        duplicate = kit.action("battlecraft.custom.mine.duplicate", "battlecraft.custom.mine.do_duplicate",
                desk::duplicateSelected);
        delete = kit.action("battlecraft.custom.mine.delete",
                () -> kit.host().asking(desk.deleteKey()) ? SURE : DO_DELETE, desk::deleteSelected).alerting();
        save = kit.action("battlecraft.custom.mine.save", "battlecraft.custom.mine.do_save", desk::saveFresh);
        folder = kit.action("battlecraft.custom.mine.folder", "battlecraft.custom.mine.do_open", PresetDesk::openFolder);
        resetHeading = kit.heading("battlecraft.custom.group.reset");
        sectionsHeading = kit.heading("battlecraft.custom.mine.sections");
        for (ConfigSection section : ConfigSection.values()) {
            sections.add(sectionRow(kit, section));
        }
    }

    private static FieldRow field(RowKit kit, Consumer<String> typed) {
        FieldRow row = new FieldRow(0, 0, kit.width(), RowKit.ROW_HEIGHT,
                Component.translatable("battlecraft.custom.mine.name"),
                Component.translatable("battlecraft.custom.mine.name.placeholder"), "", NAME_LIMIT, typed);
        row.hint("battlecraft.custom.mine.name" + RowKit.HINT);
        return row;
    }

    private ToggleRow sectionRow(RowKit kit, ConfigSection section) {
        ToggleRow row = new ToggleRow(0, 0, kit.width(), RowKit.ROW_HEIGHT,
                Component.translatable(section.translationKey()), () -> chosen.contains(section), value -> {
                    if (value) {
                        chosen.add(section);
                    } else {
                        chosen.remove(section);
                    }
                });
        row.hint("battlecraft.custom.mine.section" + RowKit.HINT);
        return row;
    }

    void builtin(StudioStack stack, int x, boolean current) {
        if (current) {
            apply.block(Component.translatable("battlecraft.custom.preset.chosen"));
        } else {
            apply.unblock();
        }
        stack.add(apply, x);
        stack.add(resetHeading, x, RowKit.ROW_HEIGHT / 3);
        stack.add(resetAll, x);
    }

    void mine(StudioStack stack, int x) {
        stack.add(apply, x);
        apply.unblock();
        stack.add(rename, x, RowKit.ROW_HEIGHT / 3);
        stack.add(renameDone, x);
        stack.add(overwrite, x);
        stack.add(duplicate, x);
        stack.add(delete, x);
        stack.add(folder, x, RowKit.ROW_HEIGHT / 3);
    }

    void fresh(StudioStack stack, int x) {
        stack.add(newName, x);
        stack.add(sectionsHeading, x, RowKit.ROW_HEIGHT / 3);
        for (ToggleRow row : sections) {
            stack.add(row, x);
        }
        stack.add(save, x, RowKit.ROW_HEIGHT / 3);
        stack.add(folder, x);
    }
}
