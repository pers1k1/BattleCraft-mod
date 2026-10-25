package com.persiki84.battlecraft.rules;

import com.persiki84.battlecraft.mode.ModeMenuState;
import com.persiki84.shared.menu.MenuStates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

// WHY: правила матча читает любой игрок, поэтому меню без прав. Режимы идут в формате студии
// WHY: оператора, без второго формата; запретов крафта в снимке нет: их список у клиента уже есть
public final class MatchRulesMenuState {
    public static final String MENU_ID = "match_rules";
    public static final String MODE = "mode";
    private static final int ANYONE = 0;

    private MatchRulesMenuState() {}

    public static void register() {
        MenuStates.register(MENU_ID, ANYONE, MatchRulesMenuState::snapshot);
    }

    private static CompoundTag snapshot(ServerPlayer viewer) {
        CompoundTag state = new CompoundTag();
        state.put(MODE, ModeMenuState.livePlayerView(viewer));
        return state;
    }
}
