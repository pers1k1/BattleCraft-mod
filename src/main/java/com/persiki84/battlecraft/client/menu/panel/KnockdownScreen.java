package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.PanelScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;

public class KnockdownScreen extends PanelScreen {
    private static final String COMMAND = "knockdown config";
    private static final int MAX_SECONDS = 3600;

    public KnockdownScreen() {
        super(Component.translatable("knockdown.menu.title"));
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.KNOCKDOWN;
    }

    @Override
    protected List<Page> pages() {
        return List.of(new Page(Component.translatable("knockdown.menu.tab.times"), this::rows));
    }

    private List<AbstractWidget> rows() {
        return List.of(
                seconds("knockdown.menu.bleed_time", "bleedTime", "bleed_time", 5),
                seconds("knockdown.menu.revive_time", "reviveTime", "revive_time", 1),
                seconds("knockdown.menu.injector_time", "injectorTime", "injector_time", 1),
                seconds("knockdown.menu.cooldown_time", "cooldownTime", "cooldown_time", 0));
    }

    private AbstractWidget seconds(String label, String key, String argument, int minimum) {
        return number(label, () -> MenuData.state(menuId()).getInt(key),
                value -> send(COMMAND + " " + argument + " " + value), minimum, MAX_SECONDS, 5);
    }
}
