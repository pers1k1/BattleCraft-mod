package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.PanelScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ImmortalityScreen extends PanelScreen {
    private static final String COMMAND = "immortality";
    private static final int MAX_SECONDS = 3600;

    public ImmortalityScreen() {
        super(Component.translatable("immortality.menu.title"));
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.IMMORTALITY;
    }

    @Override
    protected List<Page> pages() {
        return List.of(new Page(Component.translatable("immortality.menu.tab.main"), this::rows));
    }

    private List<AbstractWidget> rows() {
        return List.of(
                toggle("immortality.menu.enabled", () -> MenuData.state(menuId()).getBoolean("enabled"),
                        value -> send(COMMAND + (value ? " enable" : " disable"))),
                number("immortality.menu.duration", () -> MenuData.state(menuId()).getInt("duration"),
                        value -> send(COMMAND + " setduration " + value), 1, MAX_SECONDS, 5),
                action("immortality.menu.clear_all", "immortality.menu.action.clear",
                        () -> send(COMMAND + " clearall")));
    }
}
