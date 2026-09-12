package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.PanelScreen;
import com.persiki84.shared.client.menu.ToggleRow;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GameRulesScreen extends PanelScreen {
    private static final String COMMAND = "battlecraft rules";

    private static final GameRule[] CLIENT_RULES = {
            GameRule.FIRST_PERSON_ONLY,
            GameRule.BLOCK_DEBUG_KEYS,
            GameRule.BLOCK_ADVANCEMENTS,
            GameRule.BLOCK_MOD_LIST,
            GameRule.BLOCK_RESOURCE_PACKS,
            GameRule.BLOCK_VOICE_GROUPS,
            GameRule.SPECTATOR_LIGHT,
            GameRule.BLOCK_SUBTITLES,
            GameRule.NO_ENCHANT_GLINT,
            GameRule.CHECK_CONFIGS
    };

    private static final GameRule[] COMBAT_RULES = {
            GameRule.AIM_WALKS,
            GameRule.FIRE_WALKS,
            GameRule.RELOAD_WALKS,
            GameRule.ZOOM_LOCKED_WHILE_AIMING,
            GameRule.NO_JUMP_WHILE_AIMING,
            GameRule.NO_DODGE_WHILE_AIMING,
            GameRule.ESCORT_PROJECTILES,
            GameRule.NO_REFIT_WHEN_HURT,
            GameRule.NO_REFIT_IN_COMBAT,
            GameRule.CAPTURE_GLOW
    };

    private static final GameRule[] PARKOUR_RULES = {
            GameRule.PARKOUR_HIDES_WEAPON,
            GameRule.DODGE_BLOCKS_FIRE,
            GameRule.NO_SLIDE_WITH_GUN,
            GameRule.NO_CLIMB_WITH_GUN,
            GameRule.CRAWL_SPAM_PENALTY,
            GameRule.SPRINT_COSTS_STAMINA,
            GameRule.STAMINA_LIMITS_MOVES,
            GameRule.NO_SPRINT_BOOST_WITH_GUN
    };

    public GameRulesScreen() {
        super(Component.translatable("battlecraft.rules.title"));
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.GAME_RULES;
    }

    @Override
    protected List<Page> pages() {
        return List.of(
                new Page(Component.translatable("battlecraft.rules.tab.client"), () -> rows(CLIENT_RULES)),
                new Page(Component.translatable("battlecraft.rules.tab.combat"), () -> rows(COMBAT_RULES)),
                new Page(Component.translatable("battlecraft.rules.tab.parkour"), () -> rows(PARKOUR_RULES)));
    }

    private List<AbstractWidget> rows(GameRule[] rules) {
        List<AbstractWidget> rows = new ArrayList<>();
        for (GameRule rule : rules) {
            ToggleRow row = toggle(rule.label(), () -> MenuData.state(menuId()).getBoolean(rule.id()),
                    on -> send(COMMAND + " set " + rule.id() + " " + on));
            row.hint((Component) null);
            rows.add(row);
            rows.add(reading(rule.hint(), Component::empty));
        }
        return rows;
    }
}
