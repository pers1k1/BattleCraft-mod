package com.persiki84.capturepoints.client.menu;

import com.persiki84.capturepoints.capture.CaptureCommandRunner;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CaptureMode;
import com.persiki84.capturepoints.capture.CooldownScope;
import com.persiki84.capturepoints.capture.RewardSplit;
import com.persiki84.capturepoints.menu.CapturePointMenuState;
import com.persiki84.shared.Names;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.ManagerScreen;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuField;
import com.persiki84.battlecraft.rules.MarkerRange;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.pick.ItemPickerScreen;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.zone.ZoneShape;
import com.persiki84.shared.menu.MenuKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CapturePointManagerScreen extends ManagerScreen {
    private static final String MENU_ID = CapturePointMenuState.MENU_ID;
    private static final String POINT_COMMAND = "capturepoint";
    private static final String FINAL_COMMAND = "finalpoint";
    private static final int LIST_WIDTH = 138;
    private static final int COLUMN_GAP = 8;
    private static final int CREATE_ROWS = 5 + MenuField.ROW_EQUIVALENT;
    private static final int MAX_RADIUS = 100;
    private static final int MAX_HEIGHT = 256;
    private static final int MAX_SECONDS = 3600;
    private static final int MAX_AMOUNT = 64;
    private static final int MAX_PERCENT = 2000;
    private static final int RANGE_STEP = 50;
    private static final int MAX_LEVEL = 6;
    private static final int EFFECT_LIMIT = 64;
    private static final int DEFAULT_RADIUS = 10;
    private static final int DEFAULT_CAPTURE = 30;
    private static final int DEFAULT_COOLDOWN = 60;

    private int tab;
    private String pointName;
    private FieldRow commandRow;
    private FieldRow buffRow;
    private String fieldsFor;
    private int buffLevel = 1;
    private MenuField nameField;
    private String newName = "";
    private int newRadius = DEFAULT_RADIUS;
    private int newCapture = DEFAULT_CAPTURE;
    private int newCooldown = DEFAULT_COOLDOWN;
    private int newKind;
    private int shownPoints = -1;

    public CapturePointManagerScreen() {
        super(Component.translatable("capturepoints.menu.title"));
    }

    @Override
    public MenuKind presence() {
        return MenuKind.ADMIN;
    }

    @Override
    protected List<Component> tabs() {
        return List.of(Component.translatable("capturepoints.menu.tab.points"),
                Component.translatable("capturepoints.menu.tab.bonus"),
                Component.translatable("capturepoints.menu.tab.common"),
                Component.translatable("capturepoints.menu.tab.create"));
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
        List<CompoundTag> points = points();
        if (pointName == null && !points.isEmpty()) pointName = points.get(0).getString("name");

        shownPoints = points.size();
        if (tab == 0) addPointRows();
        if (tab == 1) place(bonusRows());
        if (tab == 2) place(commonRows());
        if (tab == 3) addCreateRows();
        addPointList(points);
    }

    @Override
    protected int desiredRows() {
        return Math.max(shownPoints, shownRows);
    }

    private static List<CompoundTag> points() {
        ListTag stored = MenuData.state(MENU_ID).getList(CapturePointMenuState.POINTS, Tag.TAG_COMPOUND);
        List<CompoundTag> points = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            points.add(stored.getCompound(index));
        }
        return points;
    }

    private CompoundTag current() {
        for (CompoundTag point : points()) {
            if (point.getString("name").equals(pointName)) return point;
        }
        return null;
    }

    private void addPointList(List<CompoundTag> points) {
        int capacity = rowCapacity(contentHeight() - PANEL_PAD * 2, points.size());
        listScroll = clampList(listScroll, points.size(), capacity);

        int x = listLeft();
        int y = listWindowTop();
        for (CompoundTag point : listWindow(points)) {
            String name = point.getString("name");
            UiButton button = new UiButton(x, y, LIST_WIDTH, ROW_HEIGHT, listLabel(point), pressed -> {
                pointName = name;
                rowScroll = 0;
                rebuild();
            });
            button.active = !name.equals(pointName);
            button.anchor(y, GlidingRow.Lane.LIST);
            addRenderableWidget(button);
            y += ROW_HEIGHT + ROW_GAP;
        }
    }

    private static Component listLabel(CompoundTag point) {
        String name = point.getString("name");
        if (!point.getBoolean(CapturePointMenuState.FINAL_FLAG)) return Component.literal(name);
        return Component.translatable("capturepoints.menu.final_name", name);
    }

    @Override
    protected int rowsLeft() {
        return contentLeft() + PANEL_PAD + LIST_WIDTH + COLUMN_GAP;
    }

    @Override
    protected int rowsWidth() {
        return CONTENT_WIDTH - PANEL_PAD * 2 - LIST_WIDTH - COLUMN_GAP;
    }

    @Override
    protected int listWidth() {
        return LIST_WIDTH;
    }

    private void addPointRows() {
        CompoundTag point = current();
        if (point == null) return;

        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(number("capturepoints.menu.radius", point, "size", 1, MAX_RADIUS, 1,
                value -> send(point, "setradius " + quoted(point) + " " + value)));
        rows.add(shapeRow(point));
        rows.add(heightRow(point, true));
        rows.add(heightRow(point, false));
        rows.add(number("capturepoints.menu.capture_time", point, "captureTime", 1, MAX_SECONDS, 5,
                value -> send(point, "setcapturetime " + quoted(point) + " " + value)));
        rows.add(number("capturepoints.menu.cooldown", point, "cooldown", 0, MAX_SECONDS, 5,
                value -> send(point, "setcooldown " + quoted(point) + " " + value)));
        rows.add(ownerRow(point));
        addVisibilityRows(rows, point);
        addTuningRows(rows, point);
        addPointActions(rows, point);
        place(rows);
    }

    // WHY: обязательность есть только у обычной точки: финальная сама открывается ими, и строка
    // WHY: у неё означала бы условие для самой себя
    private void addVisibilityRows(List<AbstractWidget> rows, CompoundTag point) {
        String name = point.getString("name");
        if (!point.getBoolean(CapturePointMenuState.FINAL_FLAG)) {
            rows.add(pointToggle("capturepoints.menu.required", name, "required",
                    value -> send(point, "setrequired " + quoted(point) + " " + value)));
        }
        rows.add(pointToggle("capturepoints.menu.shown_in_hud", name, "shownInHud",
                value -> send(point, "sethud " + quoted(point) + " " + value)));
        rows.add(pointToggle("capturepoints.menu.hidden_inside", name, "hiddenInside",
                value -> send(point, "sethideinside " + quoted(point) + " " + value)));
        rows.add(markerRangeRow(point));
    }

    // WHY: ноль значит «как у вида»: у точки нет своего предела, и она берёт общий из настроек
    private NumberRow markerRangeRow(CompoundTag point) {
        NumberRow row = number("capturepoints.menu.marker_range", point, "markerRange",
                CapturePoint.KIND_RANGE, MarkerRange.MAX_BLOCKS, RANGE_STEP,
                value -> send(point, "setmarkerrange " + quoted(point) + " " + value));
        row.floorLabel(Component.translatable("battlecraft.markers.kind"));
        return row;
    }

    private ToggleRow pointToggle(String label, String name, String key,
                                  java.util.function.Consumer<Boolean> apply) {
        ToggleRow row = new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(label), () -> live(name).getBoolean(key), apply);
        row.hint(label + ".hint");
        return row;
    }

    private void addTuningRows(List<AbstractWidget> rows, CompoundTag point) {
        String name = point.getString("name");
        rows.add(pick("capturepoints.menu.mode", modeLabels(),
                () -> CaptureMode.byId(live(name).getString("mode")).ordinal(),
                picked -> send(point, "setmode " + quoted(point) + " " + CaptureMode.values()[picked].id())));
        rows.add(pick("capturepoints.menu.split", splitLabels(),
                () -> RewardSplit.byId(live(name).getString("split")).ordinal(),
                picked -> send(point, "setrewardsplit " + quoted(point) + " " + RewardSplit.values()[picked].id())));
        rows.add(number("capturepoints.menu.capture_speed", point, "captureSpeed", 1, MAX_PERCENT, 10,
                value -> send(point, "setcapturespeed " + quoted(point) + " " + value)));
        rows.add(number("capturepoints.menu.rollback_speed", point, "rollbackSpeed", 1, MAX_PERCENT, 10,
                value -> send(point, "setrollback " + quoted(point) + " " + value)));
        rows.add(number("capturepoints.menu.pressured_rollback", point, "pressuredRollbackSpeed", 1, MAX_PERCENT, 10,
                value -> send(point, "setpressuredrollback " + quoted(point) + " " + value)));
        rows.add(number("capturepoints.menu.owned_rollback", point, "ownedRollbackSpeed", 1, MAX_PERCENT, 10,
                value -> send(point, "setownedrollback " + quoted(point) + " " + value)));
        rows.add(number("capturepoints.menu.team_cooldown", point, "teamCooldown", 0, MAX_SECONDS, 5,
                value -> send(point, "setteamcooldown " + quoted(point) + " " + value)));
        rows.add(pick("capturepoints.menu.cooldown_scope", scopeLabels(),
                () -> CooldownScope.byId(live(name).getString("cooldownScope")).ordinal(),
                picked -> send(point, "setteamcooldownscope " + quoted(point) + " "
                        + CooldownScope.values()[picked].id())));
    }

    private PickRow pick(String label, List<Component> options, java.util.function.IntSupplier current,
                         java.util.function.IntConsumer apply) {
        return new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(label),
                options, current, apply);
    }

    private static List<Component> modeLabels() {
        List<Component> options = new ArrayList<>();
        for (CaptureMode mode : CaptureMode.values()) {
            options.add(Component.translatable(mode.translationKey()));
        }
        return options;
    }

    private static List<Component> splitLabels() {
        List<Component> options = new ArrayList<>();
        for (RewardSplit split : RewardSplit.values()) {
            options.add(Component.translatable(split.translationKey()));
        }
        return options;
    }

    private static List<Component> scopeLabels() {
        List<Component> options = new ArrayList<>();
        for (CooldownScope scope : CooldownScope.values()) {
            options.add(Component.translatable(scope.translationKey()));
        }
        return options;
    }

    private List<AbstractWidget> bonusRows() {
        CompoundTag point = current();
        if (point == null) return List.of();

        followPoint(point);
        List<AbstractWidget> rows = new ArrayList<>();
        addRewardRows(rows, point);
        if (!point.getBoolean(CapturePointMenuState.FINAL_FLAG)) {
            addIncomeRows(rows, point);
            addBuffRows(rows, point);
        }
        addCommandRows(rows, point);
        return rows;
    }

    private void addRewardRows(List<AbstractWidget> rows, CompoundTag point) {
        String name = point.getString("name");
        rows.add(heading(Component.translatable("capturepoints.menu.group.reward")));
        rows.add(itemPickRow("capturepoints.menu.reward_item", () -> itemLabel(live(name).getString("rewardItem")),
                () -> pickReward(point)));
        rows.add(number("capturepoints.menu.reward", point, "rewardAmount", 0, MAX_AMOUNT, 1,
                value -> applyReward(point, value)));
    }

    private void addIncomeRows(List<AbstractWidget> rows, CompoundTag point) {
        String name = point.getString("name");
        rows.add(heading(Component.translatable("capturepoints.menu.group.income")));
        rows.add(itemPickRow("capturepoints.menu.income_item", () -> itemLabel(live(name).getString("incomeItem")),
                () -> pickIncome(point)));
        rows.add(number("capturepoints.menu.income", point, "incomeAmount", 0, MAX_AMOUNT, 1,
                value -> applyIncome(point, value)));
        rows.add(number("capturepoints.menu.income_interval", point, "incomeInterval", 1, MAX_SECONDS, 10,
                value -> send(point, "setincomeinterval " + quoted(point) + " " + value)));
    }

    private void addBuffRows(List<AbstractWidget> rows, CompoundTag point) {
        String name = point.getString("name");
        rows.add(heading(Component.translatable("capturepoints.menu.group.buff")));
        rows.add(reading("capturepoints.menu.buff", () -> effectLabel(live(name).getString("buff"))));
        rows.add(buffRow());
        rows.add(new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("capturepoints.menu.buff_level"), () -> buffLevel,
                value -> buffLevel = value, 1, MAX_LEVEL, 1));
        rows.add(action("capturepoints.menu.buff_apply", "capturepoints.menu.action.apply",
                () -> applyBuff(point)));
        rows.add(action("capturepoints.menu.buff_clear", "capturepoints.menu.action.clear",
                () -> send(point, "clearbuff " + quoted(point))));
    }

    // WHY: команд у точки несколько, и настраивать их вслепую нельзя: список показывает каждую
    // WHY: целиком и снимает её отдельной кнопкой, а поле ниже только добавляет новую
    private void addCommandRows(List<AbstractWidget> rows, CompoundTag point) {
        rows.add(heading(Component.translatable("capturepoints.menu.group.command")));

        ListTag commands = point.getList("commands", Tag.TAG_STRING);
        for (int index = 0; index < commands.size(); index++) {
            rows.add(commandEntry(point, index, commands.getString(index)));
        }
        if (commands.isEmpty()) rows.add(reading("capturepoints.menu.command_none", Component::empty));

        if (commands.size() < CapturePoint.MAX_COMMANDS) {
            rows.add(commandRow());
            rows.add(action("capturepoints.menu.command_apply", "capturepoints.menu.action.add",
                    () -> applyCommand(point)));
        }
        if (!commands.isEmpty()) {
            rows.add(action("capturepoints.menu.command_clear", "capturepoints.menu.action.clear",
                    () -> send(point, "clearcommand " + quoted(point))).alerting());
        }
    }

    private ActionRow commandEntry(CompoundTag point, int index, String command) {
        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("capturepoints.menu.command_line", index + 1),
                () -> Component.translatable("capturepoints.menu.action.remove"),
                () -> send(point, "removecommand " + quoted(point) + " " + (index + 1)));
        row.note(Component.literal(command));
        return row;
    }

    // WHY: строки полей переживают пересборку экрана, поэтому их содержимое наводится на точку
    // WHY: только при смене выбранной: иначе набранная команда стиралась бы каждым обновлением
    private void followPoint(CompoundTag point) {
        String name = point.getString("name") + '\n' + point.getString("command") + '\n' + point.getString("buff");
        if (name.equals(fieldsFor)) return;

        fieldsFor = name;
        commandRow().box().setValue(point.getString("command"));
        buffRow().box().setValue(point.getString("buff"));
        buffLevel = Math.max(1, point.getInt("buffAmplifier") + 1);
    }

    private FieldRow commandRow() {
        if (commandRow == null) {
            commandRow = new FieldRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                    Component.translatable("capturepoints.menu.command"),
                    Component.translatable("capturepoints.menu.command.placeholder"),
                    "", CaptureCommandRunner.MAX_LENGTH, value -> { });
            commandRow.hint("capturepoints.menu.command" + HINT_SUFFIX);
        }
        commandRow.setX(rowsLeft());
        commandRow.setWidth(rowsWidth());
        return commandRow;
    }

    private FieldRow buffRow() {
        if (buffRow == null) {
            buffRow = new FieldRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                    Component.translatable("capturepoints.menu.buff_id"),
                    Component.translatable("capturepoints.menu.buff_id.placeholder"),
                    "", EFFECT_LIMIT, value -> { });
        }
        buffRow.setX(rowsLeft());
        buffRow.setWidth(rowsWidth());
        return buffRow;
    }

    private void applyReward(CompoundTag point, int amount) {
        if (amount <= 0) {
            send(point, "removereward " + quoted(point));
            return;
        }

        String item = live(point.getString("name")).getString("rewardItem");
        if (item.isEmpty()) {
            MenuFeedback.show(Component.translatable("capturepoints.menu.error.no_item"), true);
            return;
        }
        send(point, "setreward " + quoted(point) + " " + item + " " + amount);
    }

    private void applyIncome(CompoundTag point, int amount) {
        String item = live(point.getString("name")).getString("incomeItem");
        if (item.isEmpty()) {
            MenuFeedback.show(Component.translatable("capturepoints.menu.error.no_item"), true);
            return;
        }
        send(point, "setincome " + quoted(point) + " " + item + " " + amount);
    }

    // WHY: предмет награды и дохода выбирается глазами, из инвентаря или всего реестра, вместе с
    // WHY: количеством: взятие из руки требовало держать нужную вещь перед нажатием строки
    private ActionRow itemPickRow(String label, java.util.function.Supplier<Component> value, Runnable run) {
        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(label), value, run);
        row.hint(label + HINT_SUFFIX);
        return row;
    }

    private void pickReward(CompoundTag point) {
        int amount = Math.max(1, valueOf(point.getString("name"), "rewardAmount"));
        ItemPickerScreen.open(Component.translatable("capturepoints.menu.pick.reward"), this,
                ItemPickerScreen.Options.counted(Component.translatable("capturepoints.menu.reward"), 1, MAX_AMOUNT, amount),
                choice -> send(point, "setreward " + quoted(point) + " " + choice.itemId() + " " + choice.amount()));
    }

    private void pickIncome(CompoundTag point) {
        int amount = Math.max(1, valueOf(point.getString("name"), "incomeAmount"));
        ItemPickerScreen.open(Component.translatable("capturepoints.menu.pick.income"), this,
                ItemPickerScreen.Options.counted(Component.translatable("capturepoints.menu.income"), 1, MAX_AMOUNT, amount),
                choice -> send(point, "setincome " + quoted(point) + " " + choice.itemId() + " " + choice.amount()));
    }

    private void applyBuff(CompoundTag point) {
        String effect = buffRow().value().trim();
        if (effect.isEmpty()) {
            MenuFeedback.show(Component.translatable("capturepoints.menu.error.no_effect"), true);
            return;
        }
        send(point, "setbuff " + quoted(point) + " \"" + effect + "\" " + (buffLevel - 1));
    }

    private void applyCommand(CompoundTag point) {
        String command = commandRow().value().trim();
        if (command.isEmpty()) return;

        send(point, "addcommand " + quoted(point) + " " + command);
        commandRow().box().setValue("");
    }

    private static Component itemLabel(String id) {
        return id.isEmpty() ? Component.translatable("capturepoints.menu.none") : Names.item(id);
    }

    private static Component effectLabel(String id) {
        return id.isEmpty() ? Component.translatable("capturepoints.menu.none") : Names.effect(id);
    }

    private void addPointActions(List<AbstractWidget> rows, CompoundTag point) {
        rows.add(action("capturepoints.menu.reset_height", "capturepoints.menu.action.reset",
                () -> send(point, "resetheight " + quoted(point))));

        if (!point.getBoolean(CapturePointMenuState.FINAL_FLAG)) {
            rows.add(action("capturepoints.menu.reset_cooldown", "capturepoints.menu.action.reset",
                    () -> raw("resetcapturecooldown " + quoted(point))));
        }

        rows.add(new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("capturepoints.menu.remove"),
                () -> Component.translatable("capturepoints.menu.action.delete"), () -> {
            send(point, "remove " + quoted(point));
            pointName = neighbourPoint(point.getString("name"));
            rebuild();
        }).alerting());
    }

    // WHY: точки удаляют подряд, и прыжок к первой означает листать список заново
    private static String neighbourPoint(String removed) {
        String previous = null;
        for (CompoundTag point : points()) {
            String name = point.getString("name");
            if (name.equals(removed)) return previous;
            previous = name;
        }
        return null;
    }

    private NumberRow number(String label, CompoundTag point, String key, int minimum, int maximum, int step,
                             java.util.function.IntConsumer apply) {
        String name = point.getString("name");
        NumberRow row = new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(label),
                () -> valueOf(name, key), apply, minimum, maximum, step);
        row.hint(label + HINT_SUFFIX);
        return row;
    }

    private static int valueOf(String name, String key) {
        return live(name).getInt(key);
    }

    private static CompoundTag live(String name) {
        for (CompoundTag point : points()) {
            if (point.getString("name").equals(name)) return point;
        }
        return new CompoundTag();
    }

    private PickRow shapeRow(CompoundTag point) {
        List<Component> options = new ArrayList<>();
        for (ZoneShape shape : ZoneShape.values()) {
            options.add(Component.translatable("zones.shape." + shape.id()));
        }
        String name = point.getString("name");
        return new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("capturepoints.menu.shape"), options,
                () -> shapeIndex(live(name).getString("shape")),
                picked -> send(point, "setshape " + quoted(point) + " " + ZoneShape.values()[picked].id()));
    }

    private static int shapeIndex(String id) {
        ZoneShape shape = ZoneShape.byId(id);
        return shape == null ? 0 : shape.ordinal();
    }

    private NumberRow heightRow(CompoundTag point, boolean up) {
        return number(up ? "capturepoints.menu.height_up" : "capturepoints.menu.height_down",
                point, up ? "heightUp" : "heightDown", 0, MAX_HEIGHT, 1,
                value -> applyHeight(point, up, value));
    }

    private void applyHeight(CompoundTag point, boolean up, int value) {
        String name = point.getString("name");
        int other = valueOf(name, up ? "heightDown" : "heightUp");
        send(point, "setheight " + quoted(point) + " " + (up ? value : other) + " " + (up ? other : value));
    }

    private PickRow ownerRow(CompoundTag point) {
        List<String> teams = teamNames();
        List<Component> options = new ArrayList<>();
        options.add(Component.translatable("capturepoints.menu.owner_none"));
        for (String team : teams) {
            options.add(Component.literal(team));
        }
        String name = point.getString("name");
        PickRow row = new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("capturepoints.menu.owner"), options,
                () -> teams.indexOf(live(name).getString("owner")) + 1,
                picked -> applyOwner(point, teams, picked));
        row.hint("capturepoints.menu.owner" + HINT_SUFFIX);
        return row;
    }

    private void applyOwner(CompoundTag point, List<String> teams, int picked) {
        if (picked == 0) {
            send(point, "clearowner " + quoted(point));
            return;
        }
        if (teams.isEmpty()) {
            MenuFeedback.show(Component.translatable("capturepoints.menu.no_teams"), true);
            return;
        }
        send(point, "setowner " + quoted(point) + " " + teams.get(picked - 1));
    }

    private List<AbstractWidget> commonRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(toggle("capturepoints.menu.protection", CapturePointMenuState.PROTECTION,
                value -> raw(POINT_COMMAND + " protection " + (value ? "enable" : "disable"))));
        rows.add(blockRuleRow("capturepoints.menu.break_placed", CapturePointMenuState.BREAK_PLACED,
                CapturePointMenuState.DENY_PLACE, "capturepoints.menu.blocked.deny_place",
                value -> raw(POINT_COMMAND + " protection breakplaced " + value)));
        rows.add(blockRuleRow("capturepoints.menu.deny_place", CapturePointMenuState.DENY_PLACE,
                CapturePointMenuState.BREAK_PLACED, "capturepoints.menu.blocked.break_placed",
                value -> raw(POINT_COMMAND + " protection denyplace " + value)));
        rows.add(toggle("capturepoints.menu.server_markers", CapturePointMenuState.CAPTURE_MARKERS,
                value -> raw(POINT_COMMAND + " serverviewmarkers " + value)));
        rows.add(toggle("capturepoints.menu.final_markers", CapturePointMenuState.FINAL_MARKERS,
                value -> raw(FINAL_COMMAND + " serverviewmarkers " + value)));
        rows.add(toggle("capturepoints.menu.final_opener_only", CapturePointMenuState.FINAL_OPENER_ONLY,
                value -> raw(FINAL_COMMAND + " openeronly " + value))
                .hint("capturepoints.menu.final_opener_only.hint"));
        rows.add(action("capturepoints.menu.reset_all", "capturepoints.menu.action.reset",
                () -> raw(POINT_COMMAND + " resetall")));
        return rows;
    }

    private ToggleRow blockRuleRow(String label, String key, String rival, String reason,
                                   java.util.function.Consumer<Boolean> apply) {
        ToggleRow row = toggle(label, key, apply);
        if (MenuData.state(MENU_ID).getBoolean(rival)) row.block(Component.translatable(reason));
        return row;
    }

    private ToggleRow toggle(String label, String key, java.util.function.Consumer<Boolean> apply) {
        return new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(label),
                () -> MenuData.state(MENU_ID).getBoolean(key), apply);
    }

    private void addCreateRows() {
        int x = rowsLeft();
        int y = contentTop() + PANEL_PAD;

        shownRows = CREATE_ROWS;
        nameField = new MenuField(x, y, rowsWidth(),
                Component.translatable("capturepoints.menu.new.name"), newName, value -> newName = value);
        addRenderableWidget(nameField.box());

        addCreateTail(x, y + MenuField.BLOCK_HEIGHT + ROW_GAP);
    }

    private void addCreateTail(int x, int y) {
        addRenderableWidget(new PickRow(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("capturepoints.menu.kind"),
                List.of(Component.translatable("capturepoints.menu.kind.point"),
                        Component.translatable("capturepoints.menu.kind.final")),
                () -> newKind, picked -> newKind = picked));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new NumberRow(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("capturepoints.menu.radius"), () -> newRadius,
                value -> newRadius = value, 1, MAX_RADIUS, 1));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new NumberRow(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("capturepoints.menu.capture_time"), () -> newCapture,
                value -> newCapture = value, 1, MAX_SECONDS, 5));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new NumberRow(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("capturepoints.menu.cooldown"), () -> newCooldown,
                value -> newCooldown = value, 0, MAX_SECONDS, 5));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new UiButton(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("capturepoints.menu.new.create"), pressed -> create()));
    }

    private void create() {
        String name = newName.trim();
        if (name.isEmpty()) {
            MenuFeedback.show(Component.translatable("capturepoints.menu.error.no_name"), true);
            return;
        }
        if (!live(name).isEmpty()) {
            MenuFeedback.show(Component.translatable("capturepoints.menu.error.exists", name), true);
            return;
        }

        String root = newKind == 1 ? FINAL_COMMAND : POINT_COMMAND;
        raw(root + " create \"" + name + "\" ~ ~ ~ " + newRadius + " " + newCapture + " " + newCooldown);
        pointName = name;
        tab = 0;
        rebuild();
    }

    private static String quoted(CompoundTag point) {
        return "\"" + point.getString("name") + "\"";
    }

    private void send(CompoundTag point, String tail) {
        raw((point.getBoolean(CapturePointMenuState.FINAL_FLAG) ? FINAL_COMMAND : POINT_COMMAND) + " " + tail);
    }

    private void raw(String command) {
        MenuCommands.run(command, MENU_ID);
    }

    @Override
    protected void renderBody(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderListWell(graphics);

        renderMissing(graphics);
        if (tab == 3 && nameField != null) nameField.render(graphics);
    }

    private void renderMissing(GuiGraphics graphics) {
        Component hint = missing();
        if (hint == null) return;

        renderHint(graphics, hint, contentTop() + contentHeight() / 2.0f);
    }

    private Component missing() {
        if (tab == 3) return null;
        if (points().isEmpty()) return Component.translatable("capturepoints.menu.empty");
        return current() == null ? Component.translatable("capturepoints.menu.pick_point") : null;
    }

    @Override
    public void tick() {
        MenuData.request(MENU_ID);
        if (stale(signature()) || points().size() != shownPoints) rebuild();
    }

    private static String signature() {
        StringBuilder mark = new StringBuilder();
        for (CompoundTag point : points()) {
            mark.append(point.getString("name")).append(point.getBoolean(CapturePointMenuState.FINAL_FLAG))
                    .append(point.getString("owner")).append(point.getString("rewardItem"))
                    .append(point.getString("incomeItem")).append(point.getString("buff"))
                    .append(point.getList("commands", Tag.TAG_STRING)).append(point.getBoolean("required"))
                    .append(point.getBoolean("shownInHud")).append(point.getBoolean("hiddenInside"))
                    .append('\n');
        }
        return mark.toString();
    }

}
