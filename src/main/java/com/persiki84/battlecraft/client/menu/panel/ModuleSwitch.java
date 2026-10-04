package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.BattleCraftMenuState;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import net.minecraft.network.chat.Component;

// WHY: модуль включается там же, где настраивается: тот же переключатель, что на плитке модуля в
// WHY: хабе, читает и шлёт то же состояние, поэтому два входа не расходятся
final class ModuleSwitch {
    private static final int ROW = 22;
    private static final int REQUEST_TICKS = 20;

    private final ModuleId module;
    private ToggleRow row;

    ModuleSwitch(ModuleId module) {
        this.module = module;
    }

    void place(StudioStack stack, int x, int width) {
        if (row == null) {
            row = new ToggleRow(0, 0, 10, ROW, Component.translatable("studio.hub.module.on"),
                    () -> MenuData.state(BattleCraftMenuState.MENU_ID).getBoolean(BattleCraftMenuState.moduleKey(module)),
                    value -> MenuCommands.run("battlecraft module " + module.id() + " " + value,
                            BattleCraftMenuState.MENU_ID));
            row.hint(module.label() + ".hint");
        }
        row.setWidth(width);
        stack.add(row, x);
    }

    void request(int ticks) {
        if (ticks % REQUEST_TICKS == 0) MenuData.request(BattleCraftMenuState.MENU_ID);
    }
}
