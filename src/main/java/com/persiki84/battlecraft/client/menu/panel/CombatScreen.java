package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.PanelScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CombatScreen extends PanelScreen {
    private static final String COMMAND = "kt";
    private static final int MIN_SECONDS = 5;
    private static final int MAX_SECONDS = 300;

    public CombatScreen() {
        super(Component.translatable("combattimer.menu.title"));
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.COMBAT;
    }

    @Override
    protected List<Page> pages() {
        return List.of(new Page(Component.translatable("combattimer.menu.tab.combat"), this::rows));
    }

    private List<AbstractWidget> rows() {
        return List.of(
                number("combattimer.menu.duration", () -> MenuData.state(menuId()).getInt("duration"),
                        value -> send(COMMAND + " settime " + value), MIN_SECONDS, MAX_SECONDS, 5),
                toggle("combattimer.menu.kill_logout", () -> MenuData.state(menuId()).getBoolean("killOnLogout"),
                        value -> send(COMMAND + " killlogout " + value)));
    }
}
