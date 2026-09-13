package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ConfigMenuState;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.PanelScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ConfigGuardScreen extends PanelScreen {
    private static final String COMMAND = "battlecraft configs";

    public ConfigGuardScreen() {
        super(Component.translatable("battlecraft.configs.title"));
    }

    @Override
    protected String menuId() {
        return ConfigMenuState.MENU_ID;
    }

    @Override
    protected List<Page> pages() {
        return List.of(
                new Page(Component.translatable("battlecraft.configs.tab.guard"), this::guardRows),
                new Page(Component.translatable("battlecraft.configs.tab.files"), this::fileRows));
    }

    private List<AbstractWidget> guardRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(reading("battlecraft.configs.watched", this::watched));
        rows.add(reading("battlecraft.configs.count",
                () -> Component.literal(String.valueOf(paths().size()))));
        rows.add(action("battlecraft.configs.do_snapshot", "battlecraft.configs.take",
                () -> send(COMMAND + " snapshot")));
        rows.add(action("battlecraft.configs.do_recheck", "battlecraft.configs.again",
                () -> send(COMMAND + " recheck")));
        rows.add(action("battlecraft.configs.do_clear", "battlecraft.configs.wipe",
                () -> send(COMMAND + " clear")).alerting());
        return rows;
    }

    private Component watched() {
        return Component.translatable(MenuData.state(menuId()).getBoolean(ConfigMenuState.WATCHED)
                ? "battlecraft.menu.on"
                : "battlecraft.menu.off");
    }

    private List<AbstractWidget> fileRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        for (String path : paths()) {
            rows.add(fileRow(path));
        }
        if (rows.isEmpty()) rows.add(reading("battlecraft.configs.no_files", Component::empty));
        return rows;
    }

    private ActionRow fileRow(String path) {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.literal(path),
                () -> Component.translatable("battlecraft.configs.forget"),
                () -> send(COMMAND + " forget " + path)).alerting();
    }

    private List<String> paths() {
        return ConfigMenuState.pathsOf(MenuData.state(menuId()));
    }
}
