package com.persiki84.battlecraft.client.menu.hub;

import com.persiki84.battlecraft.menu.BattleCraftMenuState;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// WHY: у чисел матча нет видимой вещи, поэтому они остаются строками, но в центре студии и по
// WHY: одной группе: лобби, команды, сдача, стамина и место, каждая своим узлом слева
final class HubSettings {
    static final int GROUPS = 5;

    private static final int ROW = 22;
    private static final int MAX_COMMAND = 180;
    private static final int TEAMS_GROUP = 1;
    private static final int PLACE_GROUP = 4;

    private static final Setting[] SETTINGS = {
            new Setting("lobbyTimeLimit", 1, 3600, 5, 0),
            new Setting("lobbyFastStartTime", 1, 600, 1, 0),
            new Setting("minPlayersToStart", 1, 64, 1, 0),
            new Setting("readyPercentToStart", 1, 100, 5, 0),
            new Setting("readyToggleCooldownSeconds", 0, 120, 1, 0),
            new Setting("joinGraceSeconds", 0, 600, 5, 1),
            new Setting("autoAssignSeconds", 5, 600, 5, 1),
            new Setting("matchJoinChoiceSeconds", 0, 300, 5, 1),
            new Setting("teamSwitchCooldownSeconds", 0, 600, 5, 1),
            new Setting("teamsNeeded", 1, 16, 1, 1),
            new Setting("surrenderVoteTimeout", 1, 600, 5, 2),
            new Setting("voteCooldown", 1, 1800, 10, 2),
            new Setting("surrenderMinTime", 1, 3600, 10, 2),
            new Setting("sprintStaminaDashPercent", 0, 300, 5, 3),
            new Setting("armedSprintStaminaDashPercent", 0, 300, 5, 3),
            new Setting("jumpStaminaPermille", 0, 500, 1, 3, 10),
            new Setting("armedJumpStaminaPermille", 0, 500, 1, 3, 10),
            new Setting("lobbyX", -30000000, 30000000, 1, 4),
            new Setting("lobbyY", -64, 320, 1, 4),
            new Setting("lobbyZ", -30000000, 30000000, 1, 4)
    };

    private static final String[] GROUP_KEYS = {"lobby", "teams", "surrender", "stamina", "place"};

    private final HubStudioScreen screen;
    private final Map<String, MenuRow> made = new HashMap<>();
    private final Map<CommandList, FieldRow> fields = new EnumMap<>(CommandList.class);

    private record Setting(String key, int minimum, int maximum, int step, int group, int divisor) {
        Setting(String key, int minimum, int maximum, int step, int group) {
            this(key, minimum, maximum, step, group, 1);
        }
    }

    enum CommandList {
        START("force start", BattleCraftMenuState.START_COMMANDS, "battlecraft.menu.group.start_commands"),
        STOP("force stop", BattleCraftMenuState.STOP_COMMANDS, "battlecraft.menu.group.stop_commands"),
        SURRENDER("surrender", BattleCraftMenuState.SURRENDER_COMMANDS, "battlecraft.menu.group.surrender_commands");

        private final String branch;
        private final String key;
        private final String group;

        CommandList(String branch, String key, String group) {
            this.branch = branch;
            this.key = key;
            this.group = group;
        }

        String group() {
            return group;
        }
    }

    HubSettings(HubStudioScreen screen) {
        this.screen = screen;
    }

    static String groupKey(int group) {
        return GROUP_KEYS[group];
    }

    static String groupLabel(int group) {
        return "battlecraft.menu.group." + GROUP_KEYS[group];
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    void buildGroup(StudioStack stack, int x, int width, int group) {
        for (Setting setting : SETTINGS) {
            if (setting.group() == group) stack.add(sized(settingRow(setting), width), x);
        }
        if (group == TEAMS_GROUP) stack.add(sized(requireTeamsRow(), width), x);
        if (group == PLACE_GROUP) stack.add(sized(lobbyHereRow(), width), x, ROW / 3);
    }

    private MenuRow settingRow(Setting setting) {
        String label = "battlecraft.menu.setting." + setting.key();
        return made.computeIfAbsent(setting.key(), unused -> new NumberRow(0, 0, 10, ROW, Component.translatable(label),
                () -> HubStudioScreen.state().getInt(setting.key()),
                value -> screen.later(setting.key(), "config set " + setting.key() + " " + value),
                setting.minimum(), setting.maximum(), setting.step()).scaledBy(setting.divisor()).hint(label + ".hint"));
    }

    private MenuRow requireTeamsRow() {
        return made.computeIfAbsent("requireTeams", unused -> new ToggleRow(0, 0, 10, ROW,
                Component.translatable("battlecraft.menu.require_teams"),
                () -> HubStudioScreen.state().getBoolean("requireTeams"),
                value -> screen.now("config set requireTeams " + value)).hint("battlecraft.menu.require_teams.hint"));
    }

    private MenuRow lobbyHereRow() {
        return made.computeIfAbsent("lobbyhere", unused -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("battlecraft.menu.lobby_here"),
                () -> Component.translatable("battlecraft.menu.action.place"), () -> screen.now("config lobbyhere")));
    }

    // WHY: команды старта и остановки видны целиком и снимаются каждая своей строкой, а поле ниже
    // WHY: только добавляет новую; Enter в поле тоже добавляет
    void buildCommands(StudioStack stack, int x, int width, CommandList list) {
        List<String> entries = BattleCraftMenuState.commandsOf(HubStudioScreen.state(), list.key);
        for (String entry : entries) {
            stack.add(sized(commandRow(list, entry), width), x);
        }
        stack.add(sized(field(list), width), x, entries.isEmpty() ? 0 : ROW / 3);
        stack.add(sized(made.computeIfAbsent("add:" + list.name(), unused -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("battlecraft.menu.commands.new"),
                () -> Component.translatable("battlecraft.menu.action.add"), () -> addCommand(list))), width), x);
    }

    private MenuRow commandRow(CommandList list, String entry) {
        return made.computeIfAbsent("cmd:" + list.name() + ":" + entry, unused -> new ActionRow(0, 0, 10, ROW,
                Component.literal(entry), () -> Component.translatable("battlecraft.menu.commands.remove"),
                () -> screen.now(list.branch + " removecommand " + entry)).alerting());
    }

    private FieldRow field(CommandList list) {
        return fields.computeIfAbsent(list, unused -> {
            FieldRow created = new FieldRow(0, 0, 10, ROW, Component.translatable("battlecraft.menu.commands.text"),
                    Component.translatable("battlecraft.menu.commands.text.placeholder"), "", MAX_COMMAND,
                    value -> { });
            created.hint("battlecraft.menu.commands.text.hint");
            return created;
        });
    }

    boolean submit(CommandList list) {
        FieldRow field = list == null ? null : fields.get(list);
        if (field == null || !field.capturing()) return false;
        addCommand(list);
        return true;
    }

    private void addCommand(CommandList list) {
        FieldRow field = fields.get(list);
        String text = field == null ? "" : field.value().trim();
        if (text.isEmpty()) {
            MenuFeedback.show(Component.translatable("battlecraft.menu.commands.error.empty"), true);
            return;
        }
        screen.now(list.branch + " addcommand " + text);
        field.box().setValue("");
    }
}
