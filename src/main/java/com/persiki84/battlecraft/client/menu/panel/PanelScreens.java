package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.AnnounceMenuState;
import com.persiki84.battlecraft.menu.ConfigMenuState;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.MenuScreens;

public final class PanelScreens {

    private PanelScreens() {}

    public static void register() {
        MenuScreens.register(ModuleMenuStates.AIRDROP, AirDropScreen::new);
        MenuScreens.register(ModuleMenuStates.LOOT, () -> new com.persiki84.battlecraft.client.menu.panel.loot.LootEditorScreen(null, null));
        MenuScreens.register(ModuleMenuStates.QUARRY, QuarryScreen::new);
        MenuScreens.register(ModuleMenuStates.KILL_REWARD, KillRewardScreen::new);
        MenuScreens.register(ModuleMenuStates.IMMORTALITY, ImmortalityScreen::new);
        MenuScreens.register(ModuleMenuStates.KNOCKDOWN, KnockdownScreen::new);
        MenuScreens.register(ModuleMenuStates.COMBAT, CombatScreen::new);
        MenuScreens.register(ModuleMenuStates.SELL, SellScreen::new);
        MenuScreens.register(ModuleMenuStates.MODIFIERS, ModifierScreen::new);
        MenuScreens.register(ModuleMenuStates.GAME_RULES, GameRulesScreen::new);
        MenuScreens.register(ModuleMenuStates.TEAMS, TeamsScreen::new);
        MenuScreens.register(ModuleMenuStates.MAP, MapShareScreen::new);
        MenuScreens.register(AnnounceMenuState.MENU_ID, AnnounceScreen::new);
        MenuScreens.register(ConfigMenuState.MENU_ID, ConfigGuardScreen::new);
    }
}
