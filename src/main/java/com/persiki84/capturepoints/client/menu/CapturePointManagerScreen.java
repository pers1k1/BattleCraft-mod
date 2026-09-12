package com.persiki84.capturepoints.client.menu;

import com.persiki84.capturepoints.capture.CaptureMode;
import com.persiki84.capturepoints.capture.CooldownScope;
import com.persiki84.capturepoints.capture.RewardSplit;
import com.persiki84.capturepoints.menu.CapturePointMenuState;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.ManagerScreen;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuField;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.zone.ZoneShape;
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
    private static final int DEFAULT_RADIUS = 10;
    private static final int DEFAULT_CAPTURE = 30;
    private static final int DEFAULT_COOLDOWN = 60;

    private int tab;
    private String pointName;
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
    protected List<Component> tabs() {
        return List.of(Component.translatable("capturepoints.menu.tab.points"),
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
        if (tab == 1) place(commonRows());
        if (tab == 2) addCreateRows();
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
        addRewardRows(rows, point);
        addTuningRows(rows, point);
        addPointActions(rows, point);
        place(rows);
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

    private void addRewardRows(List<AbstractWidget> rows, CompoundTag point) {
        String reward = point.getString("rewardItem");
        if (!reward.isEmpty()) {
            rows.add(number("capturepoints.menu.reward", point, "rewardAmount", 1, MAX_AMOUNT, 1,
                    value -> send(point, "setreward " + quoted(point) + " " + reward + " " + value)));
        }

        String income = point.getString("incomeItem");
        if (!income.isEmpty()) {
            rows.add(number("capturepoints.menu.income", point, "incomeAmount", 0, MAX_AMOUNT, 1,
                    value -> send(point, "setincome " + quoted(point) + " " + income + " " + value)));
        }
        rows.add(number("capturepoints.menu.income_interval", point, "incomeInterval", 1, MAX_SECONDS, 10,
                value -> send(point, "setincomeinterval " + quoted(point) + " " + value)));
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
            pointName = null;
            rebuild();
        }).alerting());
    }

    private NumberRow number(String label, CompoundTag point, String key, int minimum, int maximum, int step,
                             java.util.function.IntConsumer apply) {
        String name = point.getString("name");
        return new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(label),
                () -> valueOf(name, key), apply, minimum, maximum, step);
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
        for (String team : teams) {
            options.add(Component.literal(team));
        }
        String name = point.getString("name");
        return new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("capturepoints.menu.owner"), options,
                () -> Math.max(0, teams.indexOf(live(name).getString("owner"))),
                picked -> applyOwner(point, teams, picked));
    }

    private void applyOwner(CompoundTag point, List<String> teams, int picked) {
        if (teams.isEmpty()) {
            MenuFeedback.show(Component.translatable("capturepoints.menu.no_teams"), true);
            return;
        }
        send(point, "setowner " + quoted(point) + " " + teams.get(picked));
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
        if (tab == 2 && nameField != null) nameField.render(graphics);
    }

    private void renderMissing(GuiGraphics graphics) {
        Component hint = missing();
        if (hint == null) return;

        renderHint(graphics, hint, contentTop() + contentHeight() / 2.0f);
    }

    private Component missing() {
        if (tab == 2) return null;
        if (points().isEmpty()) return Component.translatable("capturepoints.menu.empty");
        return current() == null ? Component.translatable("capturepoints.menu.pick_point") : null;
    }

    @Override
    public void tick() {
        MenuData.request(MENU_ID);
        if (points().size() != shownPoints) rebuild();
    }

}
