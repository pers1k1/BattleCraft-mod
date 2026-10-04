package com.persiki84.zones.client.menu;

import com.persiki84.battlecraft.rules.MarkerRange;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.ColorRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.zone.ZoneShape;
import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneRule;
import com.persiki84.zones.ZoneType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

// WHY: всё о зоне в одной колонке: вид, область, метка, спавн и все одиннадцать правил. Строки
// WHY: живут между снимками и только переставляются, поэтому переходы значений не обрываются
final class ZoneInspector {
    private static final int ROW = 22;
    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 512;
    private static final int MAX_HEIGHT = 256;
    private static final int RANGE_STEP = 50;
    private static final int ID_LIMIT = 32;
    private static final int DEFAULT_SIZE = 24;
    private static final String DROP = "drop-zone";
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_.+-]+");

    private final ZoneStudioScreen screen;
    private final Map<String, MenuRow> made = new HashMap<>();
    private final Map<String, HeadingRow> headings = new HashMap<>();
    private final List<ZoneType> types = creatableTypes();
    private final FieldRow idRow;
    private PickRow ownerRow;
    private List<String> ownerTeams = List.of();
    private String typed = "";
    private int newType;
    private int newShape;
    private int newSize = DEFAULT_SIZE;
    private boolean focusId;

    ZoneInspector(ZoneStudioScreen screen) {
        this.screen = screen;
        idRow = new FieldRow(0, 0, 10, ROW, Component.translatable("zones.menu.new.id"),
                Component.translatable("zones.menu.new.id"), "", ID_LIMIT, value -> typed = value);
    }

    private static List<ZoneType> creatableTypes() {
        List<ZoneType> list = new ArrayList<>();
        for (ZoneType type : ZoneType.values()) {
            if (type.isCreatableByCommand()) list.add(type);
        }
        return list;
    }

    String typedId() {
        return typed;
    }

    ZoneShape newShape() {
        return ZoneShape.values()[newShape];
    }

    int newSize() {
        return newSize;
    }

    void beginCreate(String id) {
        idRow.box().setValue(id);
        typed = id;
        focusId = true;
    }

    boolean submit() {
        if (!screen.creating() || !idRow.capturing()) return false;
        create();
        return true;
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    private void add(StudioStack stack, int x, int width, AbstractWidget widget) {
        stack.add(sized(widget, width), x);
    }

    private void heading(StudioStack stack, int x, int width, String key) {
        HeadingRow heading = headings.computeIfAbsent(key,
                unused -> new HeadingRow(0, 0, 10, ROW, Component.translatable(key)));
        stack.add(sized(heading, width), x, ROW / 3);
    }

    private MenuRow row(String key, Supplier<MenuRow> maker) {
        return made.computeIfAbsent(key, unused -> maker.get());
    }

    void build(StudioStack stack, int x, int width) {
        if (screen.creating()) {
            buildCreate(stack, x, width);
            return;
        }
        Zone zone = screen.zone();
        if (zone == null) return;
        buildKind(stack, x, width);
        buildArea(stack, x, width);
        buildMarker(stack, x, width);
        buildRules(stack, x, width, zone);
        buildActions(stack, x, width);
    }

    private void buildKind(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.zones.group.kind");
        add(stack, x, width, row("type", () -> new PickRow(0, 0, 10, ROW, Component.translatable("zones.menu.type"),
                typeLabels(), () -> read(zone -> Math.max(0, types.indexOf(zone.type()))),
                picked -> edit("type " + types.get(picked).id()))));
        add(stack, x, width, ownerRow());
        add(stack, x, width, row("color", () -> new ColorRow(0, 0, 10, ROW, Component.translatable("zones.menu.color"),
                () -> read(ZoneStudioScreen::drawnColor), () -> read(zone -> zone.hasCustomColor() ? 1 : 0) == 1,
                () -> openColor(screen.zone()))));
    }

    void openColor(Zone zone) {
        if (zone == null) return;
        String id = zone.id();
        screen.openPalette("zone:" + id, Component.translatable("zones.menu.color"), ZoneStudioScreen.drawnColor(zone),
                argb -> screen.now(ZoneStudioScreen.COMMAND + "edit " + id + " color " + argb),
                () -> screen.now(ZoneStudioScreen.COMMAND + "edit " + id + " clearcolor"));
    }

    private List<Component> typeLabels() {
        List<Component> options = new ArrayList<>();
        for (ZoneType type : types) {
            options.add(Component.translatable("zones.type." + type.id()));
        }
        return options;
    }

    // WHY: команды скорборда меняются, пока экран открыт: строка пересоздаётся только на смене их
    // WHY: списка, иначе её переход обрывался бы на каждом снимке
    private PickRow ownerRow() {
        List<String> teams = teamNames();
        if (ownerRow != null && teams.equals(ownerTeams)) return ownerRow;
        ownerTeams = teams;
        List<Component> options = new ArrayList<>();
        options.add(Component.translatable("zones.menu.owner.none"));
        for (String team : teams) {
            options.add(Component.literal(team));
        }
        ownerRow = new PickRow(0, 0, 10, ROW, Component.translatable("zones.menu.owner"), options,
                () -> read(zone -> zone.ownerTeam() == null ? 0 : ownerTeams.indexOf(zone.ownerTeam()) + 1),
                picked -> edit(picked == 0 ? "clearowner" : "owner " + ownerTeams.get(picked - 1)));
        return ownerRow;
    }

    private static List<String> teamNames() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();
        return new ArrayList<>(minecraft.level.getScoreboard().getTeamNames());
    }

    private void buildArea(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.zones.group.area");
        add(stack, x, width, row("shape", () -> new PickRow(0, 0, 10, ROW, Component.translatable("zones.menu.shape"),
                shapeLabels(), () -> read(zone -> zone.area().shape().ordinal()),
                picked -> edit("shape " + ZoneShape.values()[picked].id()))));
        add(stack, x, width, row("size", () -> new NumberRow(0, 0, 10, ROW, Component.translatable("zones.menu.size"),
                () -> read(zone -> (int) Math.round(zone.area().size())), value -> later("size", "size " + value),
                MIN_SIZE, MAX_SIZE, 1)));
        add(stack, x, width, height(true));
        add(stack, x, width, height(false));
    }

    private MenuRow height(boolean up) {
        return row(up ? "up" : "down", () -> new NumberRow(0, 0, 10, ROW,
                Component.translatable(up ? "zones.menu.height_up" : "zones.menu.height_down"),
                () -> read(zone -> (int) Math.round(up ? zone.area().heightUp() : zone.area().heightDown())),
                value -> applyHeight(up, value), 0, MAX_HEIGHT, 1));
    }

    private void applyHeight(boolean up, int value) {
        int other = read(zone -> (int) Math.round(up ? zone.area().heightDown() : zone.area().heightUp()));
        later("height", "height " + (up ? value : other) + " " + (up ? other : value));
    }

    private static List<Component> shapeLabels() {
        List<Component> options = new ArrayList<>();
        for (ZoneShape shape : ZoneShape.values()) {
            options.add(Component.translatable("zones.shape." + shape.id()));
        }
        return options;
    }

    private void buildMarker(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.zones.group.marker");
        add(stack, x, width, row("range", () -> new NumberRow(0, 0, 10, ROW,
                Component.translatable("zones.menu.marker_range"), () -> read(Zone::markerRange),
                value -> later("range", "markerrange " + value), Zone.KIND_RANGE, MarkerRange.MAX_BLOCKS, RANGE_STEP)
                .floorLabel(Component.translatable("battlecraft.markers.kind")).hint("zones.menu.marker_range.hint")));
        add(stack, x, width, row("hide", () -> new ToggleRow(0, 0, 10, ROW,
                Component.translatable("zones.menu.hide_inside"), () -> read(zone -> zone.hiddenInside() ? 1 : 0) == 1,
                value -> edit("hideinside " + value)).hint("zones.menu.hide_inside.hint")));
        add(stack, x, width, row("spawn", () -> new PickRow(0, 0, 10, ROW, Component.translatable("zones.menu.spawn"),
                List.of(Component.translatable("zones.menu.spawn.random"),
                        Component.translatable("zones.menu.spawn.anchor")),
                () -> read(zone -> zone.spawnsAtAnchor() ? 1 : 0), this::applySpawn).hint("studio.zones.spawn.hint")));
    }

    // WHY: якорь встаёт туда, где стоит игрок, и снаружи зоны сервер откажет: без проверки строка
    // WHY: молча вернулась бы в прежний режим, не сказав почему
    private void applySpawn(int picked) {
        Zone zone = screen.zone();
        if (zone == null) return;
        if (picked == 0) {
            edit("spawn random");
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !zone.area().contains(player.getX(), player.getY(), player.getZ())) {
            MenuFeedback.show(Component.translatable("zones.error.anchor_outside", zone.id()), true);
            return;
        }
        edit("spawn here");
    }

    private void buildRules(StudioStack stack, int x, int width, Zone zone) {
        heading(stack, x, width, "studio.zones.group.rules");
        for (ZoneRule rule : ZoneRule.values()) {
            add(stack, x, width, ruleRow(rule, zone.type()));
        }
    }

    // WHY: подпись «по умолчанию» зависит от вида зоны, поэтому строка правила своя на каждый вид
    private MenuRow ruleRow(ZoneRule rule, ZoneType type) {
        return row("rule:" + rule.id() + ":" + type.id(), () -> new PickRow(0, 0, 10, ROW,
                Component.translatable("zones.rule." + rule.id()), ruleOptions(rule, type),
                () -> read(zone -> ruleChoice(zone, rule)),
                picked -> edit("rule " + rule.id() + " " + ruleWord(picked))));
    }

    private static List<Component> ruleOptions(ZoneRule rule, ZoneType type) {
        Component fallback = Component.translatable(rule.allowedByDefaultIn(type) ? "zones.menu.rule.allow"
                : "zones.menu.rule.deny");
        return List.of(Component.translatable("zones.menu.rule.default", fallback),
                Component.translatable("zones.menu.rule.allow"), Component.translatable("zones.menu.rule.deny"));
    }

    private static int ruleChoice(Zone zone, ZoneRule rule) {
        if (!zone.rules().isOverridden(rule)) return 0;
        return zone.allows(rule) ? 1 : 2;
    }

    private static String ruleWord(int choice) {
        if (choice == 1) return "allow";
        return choice == 2 ? "deny" : "default";
    }

    private void buildActions(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.points.group.actions");
        add(stack, x, width, row("tp", () -> new ActionRow(0, 0, 10, ROW, Component.translatable("studio.menu.tp"),
                () -> Component.translatable("studio.points.action.go"), () -> plain("tp "))));
        add(stack, x, width, row("here", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("studio.menu.move_here"), () -> Component.translatable("studio.points.action.here"),
                () -> edit("here"))));
        add(stack, x, width, row("delete", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("zones.menu.delete"),
                () -> Component.translatable(screen.isArmed(DROP) ? "studio.sure" : "studio.delete"),
                this::pressDelete).alerting()));
    }

    private void pressDelete() {
        Zone zone = screen.zone();
        if (zone != null && screen.confirmed(DROP)) screen.remove(zone.id());
    }

    private void buildCreate(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.zones.group.new");
        add(stack, x, width, idRow);
        add(stack, x, width, row("newtype", () -> new PickRow(0, 0, 10, ROW, Component.translatable("zones.menu.type"),
                typeLabels(), () -> newType, picked -> newType = picked)));
        add(stack, x, width, row("newshape", () -> new PickRow(0, 0, 10, ROW,
                Component.translatable("zones.menu.shape"), shapeLabels(), () -> newShape, picked -> newShape = picked)));
        add(stack, x, width, row("newsize", () -> new NumberRow(0, 0, 10, ROW, Component.translatable("zones.menu.size"),
                () -> newSize, value -> newSize = value, MIN_SIZE, MAX_SIZE, 1)));
        add(stack, x, width, row("place", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("studio.points.place_row"),
                () -> Component.literal(screen.newX() + "  " + screen.newZ()), this::placeAtPlayer)
                .hint("studio.points.place_row.hint")));
        stack.add(sized(row("create", () -> new ActionRow(0, 0, 10, ROW, Component.translatable("zones.menu.new.create"),
                () -> Component.translatable("studio.create"), this::create)), width), x, ROW / 3);
        if (focusId) takeIdFocus();
    }

    private void takeIdFocus() {
        focusId = false;
        screen.setFocused(idRow);
        idRow.box().moveCursorToEnd();
        idRow.box().setHighlightPos(0);
    }

    private void placeAtPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) screen.placeGhostAt(player.getBlockX(), player.getBlockZ());
    }

    private void create() {
        String id = typed.trim();
        if (id.isEmpty()) {
            MenuFeedback.show(Component.translatable("zones.menu.error.no_id"), true);
            return;
        }
        if (!ID_PATTERN.matcher(id).matches()) {
            MenuFeedback.show(Component.translatable("zones.menu.error.bad_id"), true);
            return;
        }
        screen.create(id, newShape(), newSize, types.get(newType));
    }

    private int read(ToIntFunction<Zone> reader) {
        Zone zone = screen.zone();
        return zone == null ? 0 : reader.applyAsInt(zone);
    }

    private void edit(String tail) {
        Zone zone = screen.zone();
        if (zone != null) screen.now(ZoneStudioScreen.COMMAND + "edit " + zone.id() + " " + tail);
    }

    private void plain(String verb) {
        Zone zone = screen.zone();
        if (zone != null) screen.now(ZoneStudioScreen.COMMAND + verb + zone.id());
    }

    private void later(String key, String tail) {
        Zone zone = screen.zone();
        if (zone != null) screen.later(key, ZoneStudioScreen.COMMAND + "edit " + zone.id() + " " + tail);
    }
}
