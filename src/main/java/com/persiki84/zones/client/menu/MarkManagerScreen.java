package com.persiki84.zones.client.menu;

import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
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
import com.persiki84.zones.mark.MapMark;
import com.persiki84.zones.mark.MarkKind;
import com.persiki84.zones.mark.MarkMenuState;
import com.persiki84.zones.mark.MarkPalette;
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

    private PickRow colorRow(String id) {
        List<Component> options = new ArrayList<>();
        for (int index = 0; index < MarkPalette.COLORS.length; index++) {
            options.add(MarkPalette.name(index));
        }
        return new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.mark.menu.color"), options,
                () -> MarkPalette.indexOf(live(id).getInt("color")),
                picked -> send("edit " + id + " color " + MarkPalette.COLORS[picked]));
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
            markId = null;
            rebuild();
        }).alerting();
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
                    .append(entry.getList("teams", Tag.TAG_STRING)).append('\n');
        }
        return mark.toString();
    }

}
