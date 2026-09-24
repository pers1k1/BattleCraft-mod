package com.persiki84.zones.client.menu;

import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.ColorRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.ManagerScreen;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuField;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PaletteWindow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.battlecraft.rules.MarkerRange;
import com.persiki84.shared.zone.ZoneShape;
import com.persiki84.zones.mark.MapMark;
import com.persiki84.zones.mark.MarkHideZone;
import com.persiki84.zones.mark.MarkKind;
import com.persiki84.zones.mark.MarkMenuState;
import com.persiki84.shared.menu.MenuKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class MarkManagerScreen extends ManagerScreen {
    private static final String COMMAND = "battlecraft mark";
    private static final int LIST_WIDTH = 138;
    private static final int COLUMN_GAP = 8;
    private static final int MAX_COORDINATE = 30000000;
    private static final int MIN_HEIGHT = -64;
    private static final int MAX_HEIGHT = 320;
    private static final int CREATE_ROWS = 4 + MenuField.ROW_EQUIVALENT * 2;
    private static final int LABEL_LIMIT = 48;
    private static final int RANGE_STEP = 50;
    private static final int HIDE_RADIUS_STEP = 2;
    private static final int HIDE_HEIGHT_STEP = 1;
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_.+-]+");

    private int tab;
    private String markId;
    private FieldRow labelRow;
    private String labelShownFor;
    private int shownMarks = -1;
    private MenuField idField;
    private MenuField labelField;
    private String newId = "";
    private String newLabel = "";
    private String newLine = "";
    private boolean placed;
    private int newX;
    private int newY;
    private int newZ;

    public MarkManagerScreen() {
        super(Component.translatable("zones.mark.menu.title"));
    }

    @Override
    public MenuKind presence() {
        return MenuKind.ADMIN;
    }

    @Override
    protected List<Component> tabs() {
        return List.of(Component.translatable("zones.mark.menu.tab.mark"),
                Component.translatable("zones.mark.menu.tab.teams"),
                Component.translatable("zones.mark.menu.tab.create"));
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
    protected int desiredRows() {
        return Math.max(shownMarks, shownRows);
    }

    @Override
    protected void buildBody() {
        List<CompoundTag> marks = marks();
        if (markId == null && !marks.isEmpty()) markId = marks.get(0).getString("id");

        shownMarks = marks.size();
        if (tab == 0) place(markRows());
        if (tab == 1) place(teamRows());
        if (tab == 2) addCreateRows();
        addMarkList(marks);
    }

    private static List<CompoundTag> marks() {
        ListTag stored = MenuData.state(MarkMenuState.MENU_ID).getList(MarkMenuState.MARKS, Tag.TAG_COMPOUND);
        List<CompoundTag> marks = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            marks.add(stored.getCompound(index));
        }
        return marks;
    }

    private CompoundTag current() {
        for (CompoundTag mark : marks()) {
            if (mark.getString("id").equals(markId)) return mark;
        }
        return null;
    }

    private void addMarkList(List<CompoundTag> marks) {
        int capacity = rowCapacity(contentHeight() - PANEL_PAD * 2, marks.size());
        listScroll = clampList(listScroll, marks.size(), capacity);

        int x = listLeft();
        int y = listWindowTop();
        for (CompoundTag mark : listWindow(marks)) {
            String id = mark.getString("id");
            UiButton button = new UiButton(x, y, LIST_WIDTH, ROW_HEIGHT,
                    Component.literal(mark.getString("label")), pressed -> {
                markId = id;
                rowScroll = 0;
                rebuild();
            });
            button.active = !id.equals(markId);
            button.anchor(y, GlidingRow.Lane.LIST);
            addRenderableWidget(button);
            y += ROW_HEIGHT + ROW_GAP;
        }
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

    private List<AbstractWidget> markRows() {
        CompoundTag mark = current();
        if (mark == null) return List.of();

        String id = mark.getString("id");
        followMark(mark);
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(reading("zones.mark.menu.position", () -> position(id)));
        rows.add(labelRow());
        rows.add(action("zones.mark.menu.rename", "zones.mark.menu.action.apply", () -> applyLabel(id)));
        rows.add(kindRow(id));
        rows.add(scaleRow(id));
        rows.add(markerRangeRow(id));
        addHideZoneRows(rows, id);
        addLineRows(rows, id);
        rows.add(colorRow(id));
        rows.add(new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.everyone"),
                () -> live(id).getBoolean("everyone"), value -> showEveryone(id, value)));
        rows.add(worldRow(id));
        rows.add(action("zones.mark.menu.teleport", "zones.mark.menu.action.go", () -> send("tp " + id)));
        rows.add(action("zones.mark.menu.move", "zones.mark.menu.action.here", () -> send("edit " + id + " here")));
        rows.add(deleteRow(id));
        return rows;
    }

    private NumberRow scaleRow(String id) {
        NumberRow row = new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.scale"),
                () -> live(id).contains("scale") ? live(id).getInt("scale") : MapMark.SCALE_FULL,
                value -> send("edit " + id + " scale " + value),
                MapMark.SCALE_MIN, MapMark.SCALE_MAX, 5);
        row.hint("zones.mark.menu.scale" + HINT_SUFFIX);
        return row;
    }

    // WHY: ноль значит «как у вида»: у метки нет своего предела, и она берёт общий из настроек
    private NumberRow markerRangeRow(String id) {
        NumberRow row = new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.marker_range"),
                () -> live(id).getInt("markerRange"),
                value -> send("edit " + id + " markerrange " + value),
                MapMark.KIND_RANGE, MarkerRange.MAX_BLOCKS, RANGE_STEP);
        row.floorLabel(Component.translatable("battlecraft.markers.kind"));
        row.hint("zones.mark.menu.marker_range" + HINT_SUFFIX);
        return row;
    }

    // WHY: форма и высота без радиуса ничего не значат, поэтому появляются вместе с зоной;
    // WHY: подпись снимка держит признак «зона есть», и экран пересобирается на его смене
    private void addHideZoneRows(List<AbstractWidget> rows, String id) {
        rows.add(hideRadiusRow(id));
        if (live(id).getInt("hideRadius") <= MarkHideZone.OFF) return;

        rows.add(hideShapeRow(id));
        rows.add(hideHeightRow(id));
        rows.add(hideShownRow(id));
        if (live(id).getBoolean("hideShown")) rows.add(hideColorRow(id));
    }

    private ToggleRow hideShownRow(String id) {
        ToggleRow row = new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.hide_shown"),
                () -> live(id).getBoolean("hideShown"),
                value -> send("edit " + id + " hidezone show " + value));
        row.hint("zones.mark.menu.hide_shown" + HINT_SUFFIX);
        return row;
    }

    // WHY: сброс возвращает зону к цвету метки: пока свой не выбран, она перекрашивается вместе с ней
    private ColorRow hideColorRow(String id) {
        Component label = Component.translatable("zones.mark.menu.hide_color");
        ColorRow row = new ColorRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, label,
                () -> hideColor(live(id)),
                () -> live(id).getInt("hideColor") != MarkHideZone.MARK_COLOR,
                () -> palette("mark:" + id + ":hide", PaletteWindow.Kind.SERVER, label, hideColor(live(id)),
                        argb -> send("edit " + id + " hidezone color " + argb),
                        () -> send("edit " + id + " hidezone color mark")));
        row.hint("zones.mark.menu.hide_color" + HINT_SUFFIX);
        return row;
    }

    private static int hideColor(CompoundTag mark) {
        int own = mark.getInt("hideColor");
        return own == MarkHideZone.MARK_COLOR ? mark.getInt("color") : own;
    }

    private NumberRow hideRadiusRow(String id) {
        NumberRow row = new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.hide_radius"),
                () -> live(id).getInt("hideRadius"),
                value -> send("edit " + id + " hidezone radius " + value),
                MarkHideZone.OFF, MarkHideZone.MAX_RADIUS, HIDE_RADIUS_STEP);
        row.floorLabel(Component.translatable("zones.mark.menu.hide_radius.off"));
        row.hint("zones.mark.menu.hide_radius" + HINT_SUFFIX);
        return row;
    }

    private PickRow hideShapeRow(String id) {
        List<Component> options = new ArrayList<>();
        for (ZoneShape shape : ZoneShape.values()) {
            options.add(Component.translatable("zones.shape." + shape.id()));
        }
        PickRow row = new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.hide_shape"), options,
                () -> hideShape(id).ordinal(),
                picked -> send("edit " + id + " hidezone shape " + ZoneShape.values()[picked].id()));
        row.hint("zones.mark.menu.hide_shape" + HINT_SUFFIX);
        return row;
    }

    private ZoneShape hideShape(String id) {
        ZoneShape shape = ZoneShape.byId(live(id).getString("hideShape"));
        return shape == null ? ZoneShape.CIRCLE : shape;
    }

    private NumberRow hideHeightRow(String id) {
        NumberRow row = new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.hide_height"),
                () -> live(id).getInt("hideHeight"),
                value -> send("edit " + id + " hidezone height " + value),
                MarkHideZone.WHOLE_COLUMN, MarkHideZone.MAX_HEIGHT, HIDE_HEIGHT_STEP);
        row.floorLabel(Component.translatable("zones.mark.menu.hide_height.all"));
        row.hint("zones.mark.menu.hide_height" + HINT_SUFFIX);
        return row;
    }

    private PickRow kindRow(String id) {
        List<Component> options = new ArrayList<>();
        for (MarkKind kind : MarkKind.values()) {
            options.add(Component.translatable(kind.label()));
        }
        PickRow row = new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.kind"), options,
                () -> MarkKind.byId(live(id).getString("kind")).ordinal(),
                picked -> send("edit " + id + " kind " + MarkKind.values()[picked].id()));
        row.hint("zones.mark.menu.kind" + HINT_SUFFIX);
        return row;
    }

    // WHY: первая строка это подпись метки, и она правится полем выше: здесь только продолжение
    // WHY: надписи, поэтому снять можно любую строку кроме первой
    private void addLineRows(List<AbstractWidget> rows, String id) {
        ListTag lines = live(id).getList("lines", Tag.TAG_STRING);
        for (int index = 1; index < lines.size(); index++) {
            int line = index;
            rows.add(action("zones.mark.menu.line", "zones.mark.menu.action.remove_line",
                    () -> send("edit " + id + " line remove " + line))
                    .note(Component.literal(lines.getString(line))));
        }
        if (lines.size() < MapMark.MAX_LINES) {
            rows.add(new FieldRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                    Component.translatable("zones.mark.menu.add_line"),
                    Component.translatable("zones.mark.menu.add_line.hint"), "", LABEL_LIMIT,
                    value -> newLine = value));
            rows.add(action("zones.mark.menu.add_line", "zones.mark.menu.action.add",
                    () -> applyNewLine(id)));
        }
    }

    private void applyNewLine(String id) {
        String value = newLine.trim();
        if (value.isEmpty()) return;

        send("edit " + id + " line add " + value);
        newLine = "";
        rebuild();
    }

    // WHY: метка, снятая с мира, остаётся на миникарте и полной карте: это ориентир для карты,
    // WHY: а не табличка над местностью
    private ToggleRow worldRow(String id) {
        ToggleRow row = new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.in_world"),
                () -> live(id).getBoolean("inWorld"),
                value -> send("edit " + id + " world " + value));
        row.hint("zones.mark.menu.in_world" + HINT_SUFFIX);
        return row;
    }

    private FieldRow labelRow() {
        if (labelRow == null) {
            labelRow = new FieldRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                    Component.translatable("zones.mark.menu.label"),
                    Component.translatable("zones.mark.menu.new.label"), "", LABEL_LIMIT, value -> { });
        }
        labelRow.setX(rowsLeft());
        labelRow.setWidth(rowsWidth());
        return labelRow;
    }

    private void followMark(CompoundTag mark) {
        String id = mark.getString("id") + '\n' + mark.getString("label");
        if (id.equals(labelShownFor)) return;

        labelShownFor = id;
        labelRow().box().setValue(mark.getString("label"));
    }

    private void applyLabel(String id) {
        String label = labelRow().value().trim();
        if (label.isEmpty()) {
            MenuFeedback.show(Component.translatable("zones.mark.menu.error.no_label"), true);
            return;
        }
        send("edit " + id + " label " + label);
    }

    private static CompoundTag live(String id) {
        for (CompoundTag mark : marks()) {
            if (mark.getString("id").equals(id)) return mark;
        }
        return new CompoundTag();
    }

    private static Component position(String id) {
        CompoundTag mark = live(id);
        return Component.literal(mark.getInt("x") + " " + mark.getInt("y") + " " + mark.getInt("z"));
    }

    private void showEveryone(String id, boolean everyone) {
        if (everyone) {
            send("edit " + id + " everyone");
            return;
        }

        String team = firstTeam();
        if (team == null) {
            MenuFeedback.show(Component.translatable("zones.mark.menu.no_teams"), true);
            return;
        }
        send("edit " + id + " show " + team);
    }

    private static String firstTeam() {
        List<String> teams = teamNames();
        return teams.isEmpty() ? null : teams.get(0);
    }

    private ColorRow colorRow(String id) {
        Component label = Component.translatable("zones.mark.menu.color");
        return new ColorRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, label,
                () -> live(id).getInt("color"),
                () -> live(id).getInt("color") != MapMark.DEFAULT_COLOR,
                () -> palette("mark:" + id + ":color", PaletteWindow.Kind.SERVER, label, live(id).getInt("color"),
                        argb -> send("edit " + id + " color " + argb),
                        () -> send("edit " + id + " color " + MapMark.DEFAULT_COLOR)));
    }

    private List<AbstractWidget> teamRows() {
        CompoundTag mark = current();
        if (mark == null) return List.of();

        String id = mark.getString("id");
        List<AbstractWidget> rows = new ArrayList<>();

        for (String team : teamNames()) {
            rows.add(new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.literal(team),
                    () -> allowedTeams(live(id)).contains(team),
                    value -> send("edit " + id + (value ? " show " : " hide ") + team)));
        }
        if (rows.isEmpty()) rows.add(reading("zones.mark.menu.no_teams", Component::empty));
        return rows;
    }

    private static List<String> allowedTeams(CompoundTag mark) {
        ListTag stored = mark.getList("teams", Tag.TAG_STRING);
        List<String> teams = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            teams.add(stored.getString(index));
        }
        return teams;
    }

    private ActionRow deleteRow(String id) {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.delete"),
                () -> Component.translatable("zones.mark.menu.action.delete"), () -> {
            send("delete " + id);
            markId = neighbourMark(id);
            rebuild();
        }).alerting();
    }

    // WHY: удаляют подряд, и возврат к первой метке заставляет каждый раз листать заново
    private static String neighbourMark(String removed) {
        String previous = null;
        for (CompoundTag mark : marks()) {
            String id = mark.getString("id");
            if (id.equals(removed)) return previous;
            previous = id;
        }
        return null;
    }

    private void addCreateRows() {
        int x = rowsLeft();
        int y = contentTop() + PANEL_PAD;

        idField = field(x, y, newId, Component.translatable("zones.mark.menu.new.id"), value -> newId = value);
        y += MenuField.BLOCK_HEIGHT + ROW_GAP;

        labelField = field(x, y, newLabel, Component.translatable("zones.mark.menu.new.label"),
                value -> newLabel = value);
        y += MenuField.BLOCK_HEIGHT + ROW_GAP;

        y = addCoordinateRows(x, y);
        addRenderableWidget(new UiButton(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.new.create"), pressed -> create()));
        shownRows = CREATE_ROWS;
    }

    private int addCoordinateRows(int x, int y) {
        pickPlacement();
        addRenderableWidget(new NumberRow(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.new.x"), () -> newX, value -> newX = value,
                -MAX_COORDINATE, MAX_COORDINATE, 1));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new NumberRow(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.new.y"), () -> newY, value -> newY = value,
                MIN_HEIGHT, MAX_HEIGHT, 1));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new NumberRow(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.new.z"), () -> newZ, value -> newZ = value,
                -MAX_COORDINATE, MAX_COORDINATE, 1));
        return y + ROW_HEIGHT + ROW_GAP;
    }

    private void pickPlacement() {
        if (placed) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        placed = true;
        newX = minecraft.player.getBlockX();
        newY = minecraft.player.getBlockY();
        newZ = minecraft.player.getBlockZ();
    }

    private MenuField field(int x, int y, String value, Component label, java.util.function.Consumer<String> apply) {
        MenuField created = new MenuField(x, y, rowsWidth(), label, value, apply);
        addRenderableWidget(created.box());
        return created;
    }

    private void create() {
        String id = newId.trim();
        if (id.isEmpty()) {
            MenuFeedback.show(Component.translatable("zones.mark.menu.error.no_id"), true);
            return;
        }
        if (!ID_PATTERN.matcher(id).matches()) {
            MenuFeedback.show(Component.translatable("zones.mark.menu.error.bad_id"), true);
            return;
        }
        if (!live(id).isEmpty()) {
            MenuFeedback.show(Component.translatable("zones.mark.error.exists", id), true);
            return;
        }

        String label = newLabel.trim();
        send("create " + id + " at " + newX + " " + newY + " " + newZ + (label.isEmpty() ? "" : " " + label));
        markId = id;
        tab = 0;
        rebuild();
    }

    private void send(String tail) {
        MenuCommands.run(COMMAND + " " + tail, MarkMenuState.MENU_ID);
    }

    @Override
    protected void renderBody(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderListWell(graphics);

        renderMissing(graphics);
        if (tab == 2) renderFields(graphics);
    }

    private void renderMissing(GuiGraphics graphics) {
        Component hint = missing();
        if (hint == null) return;

        renderHint(graphics, hint, contentTop() + contentHeight() / 2.0f);
    }

    private Component missing() {
        if (tab == 2) return null;
        if (marks().isEmpty()) return Component.translatable("zones.mark.menu.empty");
        return current() == null ? Component.translatable("zones.mark.menu.pick_mark") : null;
    }

    private void renderFields(GuiGraphics graphics) {
        for (MenuField field : new MenuField[]{idField, labelField}) {
            if (field != null) field.render(graphics);
        }
    }

    @Override
    public void tick() {
        MenuData.request(MarkMenuState.MENU_ID);
        if (stale(signature()) || marks().size() != shownMarks) rebuild();
    }

    private static String signature() {
        StringBuilder mark = new StringBuilder();
        for (CompoundTag entry : marks()) {
            mark.append(entry.getString("id")).append(entry.getString("label")).append(entry.getInt("color"))
                    .append(entry.getBoolean("everyone")).append(entry.getBoolean("inWorld"))
                    .append(entry.getString("kind")).append(entry.getList("lines", Tag.TAG_STRING))
                    .append(entry.getInt("hideRadius") > MarkHideZone.OFF).append(entry.getBoolean("hideShown"))
                    .append(entry.getList("teams", Tag.TAG_STRING)).append('\n');
        }
        return mark.toString();
    }

}
