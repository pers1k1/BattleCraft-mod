package com.persiki84.zones.client.menu;

import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.ManagerScreen;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuField;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.zone.ZoneShape;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneRule;
import com.persiki84.zones.ZoneType;
import com.persiki84.zones.client.ClientZoneData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

public class ZoneManagerScreen extends ManagerScreen {
    private static final String COMMAND = "battlecraft zone";
    private static final int LIST_WIDTH = 138;
    private static final int COLUMN_GAP = 8;
    private static final int CREATE_ROWS = 5 + MenuField.ROW_EQUIVALENT;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 512;
    private static final int MAX_HEIGHT = 256;
    private static final int DEFAULT_SIZE = 24;
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_.+-]+");

    private static final int[] COLORS = {
            0xE7E9F4, 0xCE2A22, 0x3F7BD8, 0x3FA75A,
            0xE0B33C, 0xD9772E, 0x9B59B6, 0x34C6C6
    };

    private int tab;
    private String zoneId;
    private MenuField nameField;
    private String newId = "";
    private int newShape;
    private int newType;
    private int newSize = DEFAULT_SIZE;
    private int shownZones = -1;

    public ZoneManagerScreen() {
        super(Component.translatable("zones.menu.title"));
    }

    @Override
    protected List<Component> tabs() {
        return List.of(Component.translatable("zones.menu.tab.zones"),
                Component.translatable("zones.menu.tab.rules"),
                Component.translatable("zones.menu.tab.create"));
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
        List<Zone> all = zones();
        if (zoneId == null && !all.isEmpty()) zoneId = all.get(0).id();

        shownZones = all.size();
        if (tab == 0) addZoneRows();
        if (tab == 1) addRuleRows();
        if (tab == 2) addCreateRows();
        addZoneList(all);
    }

    @Override
    protected int desiredRows() {
        return Math.max(shownZones, shownRows);
    }

    private List<Zone> zones() {
        return new ArrayList<>(ClientZoneData.all());
    }

    private Zone current() {
        return zoneId == null ? null : ClientZoneData.byId(zoneId);
    }

    private void addZoneList(List<Zone> all) {
        int capacity = rowCapacity(contentHeight() - PANEL_PAD * 2, all.size());
        listScroll = clampList(listScroll, all.size(), capacity);

        int x = listLeft();
        int y = listWindowTop();
        for (Zone zone : listWindow(all)) {
            String id = zone.id();
            UiButton button = new UiButton(x, y, LIST_WIDTH, ROW_HEIGHT, Component.literal(id), pressed -> {
                zoneId = id;
                rowScroll = 0;
                rebuild();
            });
            button.active = !id.equals(zoneId);
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

    private void addZoneRows() {
        Zone zone = current();
        if (zone == null) return;

        String id = zone.id();
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(typeRow(id));
        rows.add(shapeRow(id));
        rows.add(sizeRow(id));
        rows.add(heightRow(id, true));
        rows.add(heightRow(id, false));
        rows.add(ownerRow(id));
        rows.add(colorRow(id));
        rows.add(spawnRow(id));
        rows.add(actionRow("zones.menu.teleport", "zones.menu.action.go", () -> send("tp " + id)));
        rows.add(actionRow("zones.menu.move", "zones.menu.action.here", () -> edit(id, "here")));
        rows.add(deleteRow(id));
        place(rows);
    }

    private static int read(String id, ToIntFunction<Zone> reader) {
        Zone zone = ClientZoneData.byId(id);
        return zone == null ? 0 : reader.applyAsInt(zone);
    }

    private PickRow typeRow(String id) {
        List<ZoneType> types = creatableTypes();
        return new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.menu.type"), typeOptions(types),
                () -> read(id, zone -> Math.max(0, types.indexOf(zone.type()))),
                picked -> edit(id, "type " + types.get(picked).id()));
    }

    private static List<ZoneType> creatableTypes() {
        List<ZoneType> types = new ArrayList<>();
        for (ZoneType type : ZoneType.values()) {
            if (type.isCreatableByCommand()) types.add(type);
        }
        return types;
    }

    private static List<Component> typeOptions(List<ZoneType> types) {
        List<Component> options = new ArrayList<>();
        for (ZoneType type : types) {
            options.add(Component.translatable("zones.type." + type.id()));
        }
        return options;
    }

    private PickRow shapeRow(String id) {
        return new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.menu.shape"), shapeOptions(),
                () -> read(id, zone -> zone.area().shape().ordinal()),
                picked -> edit(id, "shape " + ZoneShape.values()[picked].id()));
    }

    private static List<Component> shapeOptions() {
        List<Component> options = new ArrayList<>();
        for (ZoneShape shape : ZoneShape.values()) {
            options.add(Component.translatable("zones.shape." + shape.id()));
        }
        return options;
    }

    private NumberRow sizeRow(String id) {
        return new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.menu.size"),
                () -> read(id, zone -> (int) Math.round(zone.area().size())),
                value -> edit(id, "size " + value), MIN_SIZE, MAX_SIZE, 1);
    }

    private NumberRow heightRow(String id, boolean up) {
        return new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(up ? "zones.menu.height_up" : "zones.menu.height_down"),
                () -> read(id, zone -> (int) Math.round(up ? zone.area().heightUp() : zone.area().heightDown())),
                value -> applyHeight(id, up, value), 0, MAX_HEIGHT, 1);
    }

    private void applyHeight(String id, boolean up, int value) {
        int other = read(id, zone -> (int) Math.round(up ? zone.area().heightDown() : zone.area().heightUp()));
        edit(id, "height " + (up ? value : other) + " " + (up ? other : value));
    }

    private PickRow ownerRow(String id) {
        List<String> teams = teamNames();
        List<Component> options = new ArrayList<>();
        options.add(Component.translatable("zones.menu.owner.none"));
        for (String team : teams) {
            options.add(Component.literal(team));
        }
        return new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.menu.owner"), options,
                () -> read(id, zone -> zone.ownerTeam() == null ? 0 : teams.indexOf(zone.ownerTeam()) + 1),
                picked -> applyOwner(id, teams, picked));
    }

    private void applyOwner(String id, List<String> teams, int picked) {
        if (picked > 0) {
            edit(id, "owner " + teams.get(picked - 1));
            return;
        }
        if (teams.isEmpty()) {
            MenuFeedback.show(Component.translatable("zones.menu.error.no_teams"), true);
        }
        edit(id, "clearowner");
    }

    private PickRow colorRow(String id) {
        int shown = read(id, Zone::color);
        boolean own = shown != Zone.TEAM_COLOR && presetIndex(shown) < 0;
        return new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.menu.color"), colorOptions(own, shown),
                () -> colorChoice(read(id, Zone::color), own),
                picked -> applyColor(id, picked, shown));
    }

    private static List<Component> colorOptions(boolean own, int shown) {
        List<Component> options = new ArrayList<>();
        options.add(Component.translatable("zones.menu.color.team"));
        for (int index = 0; index < COLORS.length; index++) {
            options.add(Component.translatable("zones.menu.color." + index));
        }
        if (own) options.add(Component.translatable("zones.menu.color.own", hex(shown)));
        return options;
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    // WHY: свой цвет зоны отличается от первого пресета: без отдельного пункта строка показывала бы
    // WHY: белый на любом заданном командой цвете и стирала бы его первым же щелчком
    private static int colorChoice(int color, boolean own) {
        if (color == Zone.TEAM_COLOR) return 0;

        int preset = presetIndex(color);
        if (preset >= 0) return preset + 1;
        return own ? COLORS.length + 1 : 0;
    }

    private static int presetIndex(int color) {
        for (int index = 0; index < COLORS.length; index++) {
            if (COLORS[index] == color) return index;
        }
        return -1;
    }

    private void applyColor(String id, int picked, int shown) {
        if (picked == 0) {
            edit(id, "clearcolor");
            return;
        }
        edit(id, "color " + (picked <= COLORS.length ? COLORS[picked - 1] : shown));
    }

    private PickRow spawnRow(String id) {
        List<Component> options = List.of(Component.translatable("zones.menu.spawn.random"),
                Component.translatable("zones.menu.spawn.anchor"));
        return new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.menu.spawn"), options,
                () -> read(id, zone -> zone.spawnsAtAnchor() ? 1 : 0),
                picked -> applySpawn(id, picked));
    }

    // WHY: якорь встаёт туда, где стоит игрок, и снаружи зоны сервер откажет: без проверки строка
    // WHY: молча возвращалась бы в прежний режим, не сказав почему
    private void applySpawn(String id, int picked) {
        if (picked == 0) {
            edit(id, "spawn random");
            return;
        }
        if (!standingInside(id)) {
            MenuFeedback.show(Component.translatable("zones.error.anchor_outside", id), true);
            return;
        }
        edit(id, "spawn here");
    }

    private static boolean standingInside(String id) {
        Zone zone = ClientZoneData.byId(id);
        LocalPlayer player = Minecraft.getInstance().player;
        return zone != null && player != null
                && zone.area().contains(player.getX(), player.getY(), player.getZ());
    }

    private ActionRow actionRow(String label, String value, Runnable action) {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(label), () -> Component.translatable(value), action);
    }

    private ActionRow deleteRow(String id) {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.menu.delete"),
                () -> Component.translatable("zones.menu.action.delete"), () -> {
            send("delete " + id);
            zoneId = null;
            rebuild();
        }).alerting();
    }

    private void addRuleRows() {
        Zone zone = current();
        if (zone == null) return;

        String id = zone.id();
        List<AbstractWidget> rows = new ArrayList<>();
        for (ZoneRule rule : ZoneRule.values()) {
            rows.add(ruleRow(id, rule));
        }
        place(rows);
    }

    private PickRow ruleRow(String id, ZoneRule rule) {
        return new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.rule." + rule.id()), ruleOptions(id, rule),
                () -> read(id, zone -> ruleChoice(zone, rule)),
                picked -> edit(id, "rule " + rule.id() + " " + ruleWord(picked)));
    }

    private static List<Component> ruleOptions(String id, ZoneRule rule) {
        Zone zone = ClientZoneData.byId(id);
        boolean allowed = zone != null && rule.allowedByDefaultIn(zone.type());
        return List.of(Component.translatable("zones.menu.rule.default", stateLabel(allowed)),
                Component.translatable("zones.menu.rule.allow"),
                Component.translatable("zones.menu.rule.deny"));
    }

    private static Component stateLabel(boolean allowed) {
        return Component.translatable(allowed ? "zones.menu.rule.allow" : "zones.menu.rule.deny");
    }

    private static int ruleChoice(Zone zone, ZoneRule rule) {
        if (!zone.rules().isOverridden(rule)) return 0;
        return zone.allows(rule) ? 1 : 2;
    }

    private static String ruleWord(int choice) {
        if (choice == 1) return "allow";
        return choice == 2 ? "deny" : "default";
    }

    private void addCreateRows() {
        int x = rowsLeft();
        int y = contentTop() + PANEL_PAD;

        shownRows = CREATE_ROWS;
        nameField = new MenuField(x, y, rowsWidth(),
                Component.translatable("zones.menu.new.id"), newId, value -> newId = value);
        addRenderableWidget(nameField.box());

        addCreateTail(x, y + MenuField.BLOCK_HEIGHT + ROW_GAP);
    }

    private void addCreateTail(int x, int y) {
        addRenderableWidget(new PickRow(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.menu.shape"), shapeOptions(),
                () -> newShape, picked -> newShape = picked));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new NumberRow(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.menu.size"), () -> newSize, value -> newSize = value,
                MIN_SIZE, MAX_SIZE, 1));
        y += ROW_HEIGHT + ROW_GAP;

        List<ZoneType> types = creatableTypes();
        addRenderableWidget(new PickRow(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.menu.type"), typeOptions(types),
                () -> newType, picked -> newType = picked));
        y += ROW_HEIGHT + ROW_GAP;

        addRenderableWidget(new UiButton(x, y, rowsWidth(), ROW_HEIGHT,
                Component.translatable("zones.menu.new.create"), pressed -> create(types)));
    }

    private void create(List<ZoneType> types) {
        String id = newId.trim();
        if (id.isEmpty()) {
            MenuFeedback.show(Component.translatable("zones.menu.error.no_id"), true);
            return;
        }
        if (!ID_PATTERN.matcher(id).matches()) {
            MenuFeedback.show(Component.translatable("zones.menu.error.bad_id"), true);
            return;
        }
        if (ClientZoneData.byId(id) != null) {
            MenuFeedback.show(Component.translatable("zones.error.zone_exists", id), true);
            return;
        }

        send("create " + id + " " + ZoneShape.values()[newShape].id() + " " + newSize + " " + types.get(newType).id());
        zoneId = id;
        tab = 0;
        rebuild();
    }

    private void edit(String id, String tail) {
        send("edit " + id + " " + tail);
    }

    private void send(String tail) {
        MenuCommands.run(COMMAND + " " + tail);
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
        if (ClientZoneData.all().isEmpty()) return Component.translatable("zones.menu.empty");
        return current() == null ? Component.translatable("zones.menu.pick_zone") : null;
    }

    @Override
    public void tick() {
        if (stale(signature()) || ClientZoneData.all().size() != shownZones) rebuild();
    }

    private static String signature() {
        StringBuilder mark = new StringBuilder();
        for (Zone zone : ClientZoneData.all()) {
            mark.append(zone.id()).append(zone.type().id()).append(zone.color()).append('\n');
        }
        return mark.toString();
    }

}
