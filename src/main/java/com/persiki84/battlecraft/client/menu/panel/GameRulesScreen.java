package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.battlecraft.rules.MarkerRange;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.PanelScreen;
import com.persiki84.shared.client.menu.ToggleRow;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class GameRulesScreen extends PanelScreen {
    private static final String COMMAND = "battlecraft rules";
    private static final String MARKERS_COMMAND = "battlecraft markers";
    private static final int RANGE_STEP = 50;

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
            GameRule.CAPTURE_GLOW,
            GameRule.IFF_DEVICE
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

    // WHY: без SuperbWarfare или Curios выдавать нечего, и строка обязана сказать почему,
    // WHY: а не молча включаться в положение, которое ничего не делает
    private static void block(GameRule rule, ToggleRow row) {
        if (rule != GameRule.IFF_DEVICE) return;
        if (MenuData.state(ModuleMenuStates.GAME_RULES).getBoolean(ModuleMenuStates.IFF_AVAILABLE)) return;

        row.block(Component.translatable("battlecraft.rule.iff_device.missing"));
    }

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
                new Page(Component.translatable("battlecraft.rules.tab.parkour"), () -> rows(PARKOUR_RULES)),
                new Page(Component.translatable("battlecraft.rules.tab.markers"), this::markerRows));
    }

    private List<AbstractWidget> markerRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        for (MarkerRange range : MarkerRange.values()) {
            rows.add(number(range.label(), () -> blocks(range),
                    value -> send(MARKERS_COMMAND + " range " + range.id() + " " + value),
                    MarkerRange.MIN_BLOCKS, MarkerRange.MAX_BLOCKS, RANGE_STEP));
        }
        rows.add(heading(Component.translatable("battlecraft.rules.group.reset")));
        rows.add(action("battlecraft.markers.reset_all", "battlecraft.markers.do_reset",
                () -> send(MARKERS_COMMAND + " reset")).alerting());
        return rows;
    }

    // WHY: снимок приходит от сервера, и до первого пакета вкладка обязана показывать заводское
    // WHY: число, а не ноль: ноль выглядел бы выставленной настройкой, которой никто не делал
    private int blocks(MarkerRange range) {
        CompoundTag state = MenuData.state(menuId());
        String key = ModuleMenuStates.MARKER_RANGE + range.id();
        return state.contains(key) ? state.getInt(key) : MarkerRange.DEFAULT_BLOCKS;
    }

    private List<AbstractWidget> rows(GameRule[] rules) {
        List<AbstractWidget> rows = new ArrayList<>();
        for (GameRule rule : rules) {
            ToggleRow row = toggle(rule.label(), () -> MenuData.state(menuId()).getBoolean(rule.id()),
                    on -> send(COMMAND + " set " + rule.id() + " " + on));
            row.hint((Component) null);
            block(rule, row);
            rows.add(row);
            rows.add(reading(rule.hint(), Component::empty));
        }
        rows.add(heading(Component.translatable("battlecraft.rules.group.reset")));
        rows.add(action("battlecraft.rules.reset_all", "battlecraft.rules.do_reset",
                () -> send(COMMAND + " reset")).alerting());
        return rows;
    }
}
