package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.menu.AnnounceMenuState;
import com.persiki84.battlecraft.menu.BattleCraftMenuState;
import com.persiki84.battlecraft.menu.ConfigMenuState;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.ManagerScreen;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuScreens;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.ToggleRow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class BattleCraftMenuScreen extends ManagerScreen {
    private static final String MENU_ID = BattleCraftMenuState.MENU_ID;
    private static final String COMMAND = "battlecraft";
    private static final int ROW_MARGIN = 24;
    private static final int MAX_COMMAND = 180;
    private static final String LOBBY_PHASE = "LOBBY";

    private static final String[] SETTING_GROUPS = {
            "battlecraft.menu.group.lobby",
            "battlecraft.menu.group.teams",
            "battlecraft.menu.group.surrender",
            "battlecraft.menu.group.stamina",
            "battlecraft.menu.group.place"
    };

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
            new Setting("surrenderVoteTimeout", 1, 600, 5, 2),
            new Setting("voteCooldown", 1, 1800, 10, 2),
            new Setting("surrenderMinTime", 1, 3600, 10, 2),
            new Setting("sprintStaminaDashPercent", 0, 300, 5, 3),
            new Setting("armedSprintStaminaDashPercent", 0, 300, 5, 3),
            new Setting("jumpStaminaPermille", 0, 500, 1, 3, 10),
            new Setting("armedJumpStaminaPermille", 0, 500, 1, 3, 10),
            new Setting("teamsNeeded", 1, 16, 1, 1),
            new Setting("lobbyX", -30000000, 30000000, 1, 4),
            new Setting("lobbyY", -64, 320, 1, 4),
            new Setting("lobbyZ", -30000000, 30000000, 1, 4)
    };

    private static final String[][] MANAGED = {
            {"battlecraft.menu.rules", ModuleMenuStates.GAME_RULES},
            {"battlecraft.menu.teams", ModuleMenuStates.TEAMS},
            {"battlecraft.menu.map", ModuleMenuStates.MAP},
            {"battlecraft.menu.zones", "zones"},
            {"battlecraft.menu.points", "capturepoints"},
            {"battlecraft.menu.marks", "marks"},
            {"battlecraft.menu.shop", ModuleMenuStates.SHOP},
            {"battlecraft.menu.airdrop", ModuleMenuStates.AIRDROP},
            {"battlecraft.menu.quarry", ModuleMenuStates.QUARRY},
            {"battlecraft.menu.killreward", ModuleMenuStates.KILL_REWARD},
            {"battlecraft.menu.immortality", ModuleMenuStates.IMMORTALITY},
            {"battlecraft.menu.knockdown", ModuleMenuStates.KNOCKDOWN},
            {"battlecraft.menu.combat", ModuleMenuStates.COMBAT},
            {"battlecraft.menu.modifiers", ModuleMenuStates.MODIFIERS},
            {"battlecraft.menu.sell", ModuleMenuStates.SELL},
            {"battlecraft.menu.announce", AnnounceMenuState.MENU_ID},
            {"battlecraft.menu.configs", ConfigMenuState.MENU_ID}
    };

    private final Map<CommandList, FieldRow> commandFields = new EnumMap<>(CommandList.class);
    private int tab;

    public BattleCraftMenuScreen() {
        super(Component.translatable("battlecraft.menu.hub.title"));
    }

    private static boolean admin() {
        return MenuData.admin(MENU_ID);
    }

    private static CompoundTag state() {
        return MenuData.state(MENU_ID);
    }

    @Override
    protected List<Component> tabs() {
        if (!admin()) return List.of(Component.translatable("battlecraft.menu.tab.match"));
        return List.of(Component.translatable("battlecraft.menu.tab.match"),
                Component.translatable("battlecraft.menu.tab.settings"),
                Component.translatable("battlecraft.menu.tab.modules"),
                Component.translatable("battlecraft.menu.tab.commands"),
                Component.translatable("battlecraft.menu.tab.manage"));
    }

    @Override
    protected int activeTab() {
        return tab;
    }

    @Override
    protected void pickTab(int index) {
        tab = index;
        rowScroll = 0;
    }

    @Override
    protected void buildBody() {
        if (tab > 0 && !admin()) tab = 0;

        List<AbstractWidget> rows = rowsOf(tab);
        if (rows != null) place(rows);
    }

    @Override
    protected List<AbstractWidget> rowsOf(int page) {
        if (page > 0 && !admin()) return null;

        return switch (page) {
            case 0 -> matchRows();
            case 1 -> settingRows();
            case 2 -> moduleRows();
            case 3 -> commandRows();
            case 4 -> manageRows();
            default -> null;
        };
    }

    @Override
    protected int rowsLeft() {
        return contentLeft() + ROW_MARGIN;
    }

    @Override
    protected int rowsWidth() {
        return CONTENT_WIDTH - ROW_MARGIN * 2;
    }

    private List<AbstractWidget> matchRows() {
        List<AbstractWidget> rows = new ArrayList<>(statusRows());
        boolean locked = playing();

        rows.add(locked
                ? status("battlecraft.menu.team", Component.translatable("battlecraft.menu.action.locked"), false)
                : action("battlecraft.menu.team", "battlecraft.menu.action.choose", TeamSelectScreen::open));
        rows.add(locked
                ? status("battlecraft.menu.ready", Component.translatable("battlecraft.menu.action.locked"), false)
                : action("battlecraft.menu.ready", "battlecraft.menu.action.toggle", () -> send("ready")));
        rows.add(action("battlecraft.menu.surrender", "battlecraft.menu.action.vote",
                () -> send("surrender start")));
        if (admin()) addMatchAdminRows(rows);
        return rows;
    }

    private static boolean playing() {
        CompoundTag state = state();
        if (state.isEmpty()) return false;
        return state.getBoolean(BattleCraftMenuState.IN_TEAM) && !LOBBY_PHASE.equals(state.getString("phase"));
    }

    private List<AbstractWidget> statusRows() {
        CompoundTag state = state();
        List<AbstractWidget> rows = new ArrayList<>();
        if (state.isEmpty()) return rows;

        rows.add(status("battlecraft.menu.status.phase", phaseLabel(state.getString("phase")), false));
        if (state.getBoolean(BattleCraftMenuState.SOFT_DISABLED)) {
            rows.add(status("battlecraft.menu.status.mode", Component.translatable("battlecraft.menu.off"), true));
        }
        addLobbyStatus(rows, state);
        return rows;
    }

    private void addLobbyStatus(List<AbstractWidget> rows, CompoundTag state) {
        if (!LOBBY_PHASE.equals(state.getString("phase"))) return;

        addChecklist(rows, state);

        int undecided = state.getInt("undecided");
        if (undecided > 0) {
            rows.add(status("battlecraft.menu.status.undecided",
                    Component.literal(String.valueOf(undecided)), true));
        }
    }

    private void addChecklist(List<AbstractWidget> rows, CompoundTag state) {
        List<CompoundTag> checks = BattleCraftMenuState.requirementsOf(state);
        if (checks.isEmpty()) return;

        rows.add(heading("battlecraft.menu.status.checklist"));

        List<CompoundTag> blocking = unmet(checks);
        if (blocking.isEmpty()) {
            rows.add(status("battlecraft.menu.status.ready_to_start",
                    Component.translatable("battlecraft.menu.requirement.met"), false));
            return;
        }

        for (CompoundTag check : blocking) {
            rows.add(requirementRow(check));
        }
    }

    private static List<CompoundTag> unmet(List<CompoundTag> checks) {
        List<CompoundTag> blocking = new ArrayList<>();
        for (CompoundTag check : checks) {
            if (!check.getBoolean(BattleCraftMenuState.RULE_MET)) blocking.add(check);
        }
        return blocking;
    }

    private ActionRow requirementRow(CompoundTag check) {
        boolean met = check.getBoolean(BattleCraftMenuState.RULE_MET);
        String rule = check.getString(BattleCraftMenuState.RULE_ID);
        Component value = requirementValue(check, met);

        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("battlecraft.requirement." + rule), () -> value, () -> solve(rule));
        row.hint("battlecraft.requirement." + rule + HINT_SUFFIX);
        if (!met) row.alerting();
        row.active = admin() && solvable(rule);
        return row;
    }

    private static boolean solvable(String rule) {
        return switch (rule) {
            case "mod_enabled", "capture_points", "final_point", "teams",
                 "min_players", "ready_share", "join_grace" -> true;
            default -> false;
        };
    }

    private void solve(String rule) {
        switch (rule) {
            case "mod_enabled" -> send("toggle");
            case "capture_points", "final_point" -> MenuScreens.open("capturepoints", this);
            case "teams" -> MenuScreens.open(ModuleMenuStates.TEAMS, this);
            case "min_players", "ready_share" -> send("ready");
            case "join_grace" -> send("force grace");
            default -> {}
        }
    }

    private static Component requirementValue(CompoundTag check, boolean met) {
        if (met) return Component.translatable("battlecraft.menu.requirement.met");

        int need = check.getInt(BattleCraftMenuState.RULE_NEED);
        if (need <= 0) return Component.translatable("battlecraft.menu.requirement.unmet");
        return ratio(check.getInt(BattleCraftMenuState.RULE_HAVE), need);
    }

    private static Component ratio(int value, int target) {
        return Component.translatable("battlecraft.menu.status.ratio", value, target);
    }

    private static Component phaseLabel(String phase) {
        if (phase == null || phase.isEmpty()) return Component.empty();
        return Component.translatable("battlecraft.menu.phase." + phase.toLowerCase(java.util.Locale.ROOT));
    }

    private ActionRow status(String label, Component value, boolean blocking) {
        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(label), () -> value, () -> {});
        row.hint(label + HINT_SUFFIX);
        if (blocking) row.alerting();
        row.active = false;
        return row;
    }

    private void addMatchAdminRows(List<AbstractWidget> rows) {
        rows.add(action("battlecraft.menu.force_start", "battlecraft.menu.action.start",
                () -> send("force start")));
        rows.add(action("battlecraft.menu.force_stop", "battlecraft.menu.action.stop",
                () -> send("force stop")));
        rows.add(action("battlecraft.menu.force_grace", "battlecraft.menu.action.clear",
                () -> send("force grace")));
        rows.add(new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("battlecraft.menu.enabled"),
                () -> !state().getBoolean(BattleCraftMenuState.SOFT_DISABLED),
                value -> send("toggle")).hint("battlecraft.menu.enabled" + HINT_SUFFIX));
    }

    private List<AbstractWidget> settingRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        int group = -1;

        for (Setting setting : SETTINGS) {
            if (setting.group() != group) {
                group = setting.group();
                rows.add(heading(SETTING_GROUPS[group]));
            }
            rows.add(settingRow(setting));
        }

        rows.add(new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("battlecraft.menu.require_teams"),
                () -> state().getBoolean("requireTeams"),
                value -> send("config set requireTeams " + value))
                .hint("battlecraft.menu.require_teams" + HINT_SUFFIX));
        rows.add(action("battlecraft.menu.lobby_here", "battlecraft.menu.action.place",
                () -> send("config lobbyhere")));
        return rows;
    }

    private HeadingRow heading(String label) {
        return new HeadingRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(label));
    }

    private NumberRow settingRow(Setting setting) {
        String label = "battlecraft.menu.setting." + setting.key();
        NumberRow row = new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(label),
                () -> state().getInt(setting.key()),
                value -> send("config set " + setting.key() + " " + value),
                setting.minimum(), setting.maximum(), setting.step()).scaledBy(setting.divisor());
        row.hint(label + HINT_SUFFIX);
        return row;
    }

    private List<AbstractWidget> moduleRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(heading("battlecraft.menu.group.modules"));
        for (ModuleId module : ModuleId.values()) {
            rows.add(moduleRow(module));
        }
        return rows;
    }

    private ToggleRow moduleRow(ModuleId module) {
        String key = BattleCraftMenuState.moduleKey(module);
        ToggleRow row = new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(module.label()),
                () -> state().getBoolean(key),
                value -> send("module " + module.id() + " " + value));
        row.hint(module.label() + HINT_SUFFIX);
        return row;
    }

    private List<AbstractWidget> commandRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        for (CommandList list : CommandList.values()) {
            addCommandRows(rows, list);
        }
        return rows;
    }

    private void addCommandRows(List<AbstractWidget> rows, CommandList list) {
        rows.add(heading(list.group()));

        List<String> entries = BattleCraftMenuState.commandsOf(state(), list.key());
        for (String entry : entries) {
            rows.add(commandRow(list, entry));
        }
        if (entries.isEmpty()) rows.add(reading("battlecraft.menu.commands.empty", Component::empty));

        rows.add(commandField(list));
        rows.add(action("battlecraft.menu.commands.new", "battlecraft.menu.action.add",
                () -> addCommand(list)));
    }

    private ActionRow commandRow(CommandList list, String entry) {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.literal(entry),
                () -> Component.translatable("battlecraft.menu.commands.remove"),
                () -> send(list.branch() + " removecommand " + entry)).alerting();
    }

    // WHY: строка поля переживает пересборку: новая на каждый кадр стирала бы набранный текст
    // WHY: и уводила бы курсор из поля на первой же смене состояния меню
    private FieldRow commandField(CommandList list) {
        FieldRow field = commandFields.computeIfAbsent(list, kind -> newCommandField());
        field.setX(rowsLeft());
        field.setWidth(rowsWidth());
        return field;
    }

    private FieldRow newCommandField() {
        FieldRow field = new FieldRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("battlecraft.menu.commands.text"),
                Component.translatable("battlecraft.menu.commands.text.placeholder"),
                "", MAX_COMMAND, value -> {});
        field.hint("battlecraft.menu.commands.text" + HINT_SUFFIX);
        return field;
    }

    private void addCommand(CommandList list) {
        FieldRow field = commandFields.get(list);
        String text = field == null ? "" : field.value().trim();
        if (text.isEmpty()) {
            MenuFeedback.show(Component.translatable("battlecraft.menu.commands.error.empty"), true);
            return;
        }

        send(list.branch() + " addcommand " + text);
        field.box().setValue("");
    }

    private List<AbstractWidget> manageRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        for (String[] entry : MANAGED) {
            String menuId = entry[1];
            rows.add(action(entry[0], "battlecraft.menu.action.open", () -> MenuScreens.open(menuId, this)));
        }
        return rows;
    }

    private void send(String tail) {
        MenuCommands.run(COMMAND + " " + tail, MENU_ID);
    }

    @Override
    protected void renderBody(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (state().isEmpty()) {
            renderHint(graphics, Component.translatable("battlecraft.menu.waiting"),
                    contentTop() + contentHeight() / 2.0f);
        }
    }

    // WHY: хаб раскладывается один раз на вход, поэтому добавленную или убранную строку списка
    // WHY: видно только по смене их числа: значения в самих строках и так читаются живьём
    @Override
    public void tick() {
        MenuData.request(MENU_ID);

        List<AbstractWidget> rows = rowsOf(tab);
        if (stale(signature()) || (rows != null && rows.size() != sourceRows)) rebuild();
    }

    private static String signature() {
        StringBuilder mark = new StringBuilder();
        for (CommandList list : CommandList.values()) {
            mark.append(BattleCraftMenuState.commandsOf(state(), list.key())).append('\n');
        }
        return mark.toString();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (leaving()) return true;

        return scrollRows(amount > 0 ? -1 : 1) || true;
    }

    private record Setting(String key, int minimum, int maximum, int step, int group, int divisor) {
        Setting(String key, int minimum, int maximum, int step, int group) {
            this(key, minimum, maximum, step, group, 1);
        }
    }

    private enum CommandList {
        START("force start", BattleCraftMenuState.START_COMMANDS, "battlecraft.menu.group.start_commands"),
        STOP("force stop", BattleCraftMenuState.STOP_COMMANDS, "battlecraft.menu.group.stop_commands"),
        SURRENDER("surrender", BattleCraftMenuState.SURRENDER_COMMANDS,
                "battlecraft.menu.group.surrender_commands");

        private final String branch;
        private final String key;
        private final String group;

        CommandList(String branch, String key, String group) {
            this.branch = branch;
            this.key = key;
            this.group = group;
        }

        String branch() {
            return branch;
        }

        String key() {
            return key;
        }

        String group() {
            return group;
        }
    }
}
