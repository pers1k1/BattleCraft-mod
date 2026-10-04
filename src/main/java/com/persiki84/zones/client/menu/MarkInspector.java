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
import com.persiki84.zones.mark.MapMark;
import com.persiki84.zones.mark.MarkHideZone;
import com.persiki84.zones.mark.MarkKind;
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

// WHY: подпись, вид, кто видит и зона скрытия метки лежат одной колонкой: прежние вкладки «Метка»
// WHY: и «Кто видит» разводили по разным местам то, что админ правит за один заход
final class MarkInspector {
    private static final int ROW = 22;
    private static final int LABEL_LIMIT = 48;
    private static final int ID_LIMIT = 32;
    private static final int RANGE_STEP = 50;
    private static final int HIDE_RADIUS_STEP = 2;
    private static final String LABEL = "label";
    private static final String DROP = "drop-mark";
    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9_.+-]+");

    private final MarkStudioScreen screen;
    private final Map<String, MenuRow> made = new HashMap<>();
    private final Map<String, HeadingRow> headings = new HashMap<>();
    private final Map<Integer, ActionRow> lineRows = new HashMap<>();
    private final FieldRow labelRow;
    private final FieldRow lineField;
    private final FieldRow newLabelRow;
    private final FieldRow newIdRow;
    private String newLabel = "";
    private String newId = "";
    private int newKind;
    private boolean focusLabel;
    private boolean filling;
    private String filledFor;

    MarkInspector(MarkStudioScreen screen) {
        this.screen = screen;
        labelRow = new FieldRow(0, 0, 10, ROW, Component.translatable("zones.mark.menu.label"),
                Component.translatable("zones.mark.menu.new.label"), "", LABEL_LIMIT, this::labelTyped);
        lineField = new FieldRow(0, 0, 10, ROW, Component.translatable("zones.mark.menu.add_line"),
                Component.translatable("zones.mark.menu.add_line.hint"), "", MapMark.LINE_LIMIT, value -> { });
        newLabelRow = new FieldRow(0, 0, 10, ROW, Component.translatable("zones.mark.menu.new.label"),
                Component.translatable("studio.marks.new_label"), "", LABEL_LIMIT, value -> newLabel = value);
        newIdRow = new FieldRow(0, 0, 10, ROW, Component.translatable("zones.mark.menu.new.id"),
                Component.translatable("zones.mark.menu.new.id"), "", ID_LIMIT, value -> newId = value);
    }

    String typedLabel() {
        return newLabel;
    }

    void beginCreate(String id) {
        newIdRow.box().setValue(id);
        newId = id;
        newLabelRow.box().setValue("");
        newLabel = "";
        focusLabel = true;
    }

    boolean submit() {
        if (screen.creating() && (newLabelRow.capturing() || newIdRow.capturing())) {
            create();
            return true;
        }
        if (!lineField.capturing()) return false;
        addLine();
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
        MarkEntry entry = screen.entry();
        if (entry == null) return;
        fill();
        buildText(stack, x, width, entry);
        buildLook(stack, x, width);
        buildViewers(stack, x, width, entry);
        buildHideZone(stack, x, width, entry);
        buildActions(stack, x, width);
    }

    private void buildText(StudioStack stack, int x, int width, MarkEntry entry) {
        heading(stack, x, width, "studio.marks.group.text");
        add(stack, x, width, labelRow);
        List<String> lines = entry.lines();
        for (int index = 1; index < lines.size(); index++) {
            ActionRow line = lineRow(index);
            line.note(Component.literal(lines.get(index)));
            add(stack, x, width, line);
        }
        if (lines.size() >= MapMark.MAX_LINES) return;
        add(stack, x, width, lineField);
        add(stack, x, width, row("lineadd", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.add_line"),
                () -> Component.translatable("zones.mark.menu.action.add"), this::addLine)));
    }

    // WHY: первая строка это подпись метки, и она правится полем выше: здесь только продолжение
    // WHY: надписи, поэтому снять можно любую строку кроме первой
    private ActionRow lineRow(int index) {
        return lineRows.computeIfAbsent(index, unused -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.line"),
                () -> Component.translatable("zones.mark.menu.action.remove_line"),
                () -> edit("line remove " + index)));
    }

    private void addLine() {
        String value = lineField.value().trim();
        if (value.isEmpty()) return;
        edit("line add " + value);
        lineField.box().setValue("");
    }

    private void labelTyped(String text) {
        if (filling || text.isBlank()) return;
        MarkEntry entry = screen.entry();
        if (entry != null) screen.later(LABEL, MarkStudioScreen.COMMAND + "edit " + entry.id() + " label " + text.trim());
    }

    private void buildLook(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.marks.group.look");
        add(stack, x, width, row("kind", () -> new PickRow(0, 0, 10, ROW, Component.translatable("zones.mark.menu.kind"),
                kindLabels(), () -> read(entry -> entry.kind().ordinal()),
                picked -> edit("kind " + MarkKind.values()[picked].id())).hint("zones.mark.menu.kind.hint")));
        add(stack, x, width, row("color", () -> new ColorRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.color"), () -> read(MarkEntry::color),
                () -> read(entry -> entry.color() != MapMark.DEFAULT_COLOR ? 1 : 0) == 1,
                () -> openColor(screen.entry()))));
        add(stack, x, width, row("scale", () -> new NumberRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.scale"), () -> read(MarkEntry::scale),
                value -> later("scale", "scale " + value), MapMark.SCALE_MIN, MapMark.SCALE_MAX, 5)
                .hint("zones.mark.menu.scale.hint")));
        add(stack, x, width, row("world", () -> new ToggleRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.in_world"), () -> read(entry -> entry.flag("inWorld") ? 1 : 0) == 1,
                value -> edit("world " + value)).hint("zones.mark.menu.in_world.hint")));
        add(stack, x, width, row("range", () -> new NumberRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.marker_range"), () -> read(entry -> entry.number("markerRange")),
                value -> later("range", "markerrange " + value), MapMark.KIND_RANGE, MarkerRange.MAX_BLOCKS, RANGE_STEP)
                .floorLabel(Component.translatable("battlecraft.markers.kind")).hint("zones.mark.menu.marker_range.hint")));
    }

    void openColor(MarkEntry entry) {
        if (entry == null) return;
        String id = entry.id();
        screen.openPalette("mark:" + id + ":color", Component.translatable("zones.mark.menu.color"), entry.color(),
                argb -> screen.now(MarkStudioScreen.COMMAND + "edit " + id + " color " + argb),
                () -> screen.now(MarkStudioScreen.COMMAND + "edit " + id + " color " + MapMark.DEFAULT_COLOR));
    }

    private static List<Component> kindLabels() {
        List<Component> options = new ArrayList<>();
        for (MarkKind kind : MarkKind.values()) {
            options.add(Component.translatable(kind.label()));
        }
        return options;
    }

    private void buildViewers(StudioStack stack, int x, int width, MarkEntry entry) {
        heading(stack, x, width, "studio.marks.group.viewers");
        add(stack, x, width, row("everyone", () -> new ToggleRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.everyone"), () -> read(mark -> mark.flag("everyone") ? 1 : 0) == 1,
                this::showEveryone)));
        if (entry.flag("everyone")) return;
        for (String team : teamNames()) {
            add(stack, x, width, row("team:" + team, () -> new ToggleRow(0, 0, 10, ROW, Component.literal(team),
                    () -> screen.entry() != null && screen.entry().teams().contains(team),
                    value -> edit((value ? "show " : "hide ") + team))));
        }
    }

    private void showEveryone(boolean everyone) {
        if (everyone) {
            edit("everyone");
            return;
        }
        List<String> teams = teamNames();
        if (teams.isEmpty()) {
            MenuFeedback.show(Component.translatable("zones.mark.menu.no_teams"), true);
            return;
        }
        edit("show " + teams.get(0));
    }

    private static List<String> teamNames() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();
        return new ArrayList<>(minecraft.level.getScoreboard().getTeamNames());
    }

    // WHY: форма и высота без радиуса ничего не значат, поэтому появляются вместе с зоной
    private void buildHideZone(StudioStack stack, int x, int width, MarkEntry entry) {
        heading(stack, x, width, "studio.marks.group.hide");
        add(stack, x, width, row("hideradius", () -> new NumberRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.hide_radius"), () -> read(mark -> mark.number("hideRadius")),
                value -> later("hideradius", "hidezone radius " + value), MarkHideZone.OFF, MarkHideZone.MAX_RADIUS,
                HIDE_RADIUS_STEP).floorLabel(Component.translatable("zones.mark.menu.hide_radius.off"))
                .hint("zones.mark.menu.hide_radius.hint")));
        if (entry.number("hideRadius") <= MarkHideZone.OFF) return;
        add(stack, x, width, row("hideshape", () -> new PickRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.hide_shape"), shapeLabels(),
                () -> read(mark -> mark.hideShape().ordinal()),
                picked -> edit("hidezone shape " + ZoneShape.values()[picked].id())).hint("zones.mark.menu.hide_shape.hint")));
        add(stack, x, width, row("hideheight", () -> new NumberRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.hide_height"), () -> read(mark -> mark.number("hideHeight")),
                value -> later("hideheight", "hidezone height " + value), MarkHideZone.WHOLE_COLUMN,
                MarkHideZone.MAX_HEIGHT, 1).floorLabel(Component.translatable("zones.mark.menu.hide_height.all"))
                .hint("zones.mark.menu.hide_height.hint")));
        add(stack, x, width, row("hideshown", () -> new ToggleRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.hide_shown"), () -> read(mark -> mark.flag("hideShown") ? 1 : 0) == 1,
                value -> edit("hidezone show " + value)).hint("zones.mark.menu.hide_shown.hint")));
        if (entry.flag("hideShown")) add(stack, x, width, hideColorRow());
    }

    private MenuRow hideColorRow() {
        return row("hidecolor", () -> new ColorRow(0, 0, 10, ROW, Component.translatable("zones.mark.menu.hide_color"),
                () -> read(MarkEntry::hideColor),
                () -> read(mark -> mark.number("hideColor") != MarkHideZone.MARK_COLOR ? 1 : 0) == 1,
                this::openHideColor).hint("zones.mark.menu.hide_color.hint"));
    }

    private void openHideColor() {
        MarkEntry entry = screen.entry();
        if (entry == null) return;
        String id = entry.id();
        screen.openPalette("mark:" + id + ":hide", Component.translatable("zones.mark.menu.hide_color"),
                entry.hideColor(), argb -> screen.now(MarkStudioScreen.COMMAND + "edit " + id + " hidezone color " + argb),
                () -> screen.now(MarkStudioScreen.COMMAND + "edit " + id + " hidezone color mark"));
    }

    private static List<Component> shapeLabels() {
        List<Component> options = new ArrayList<>();
        for (ZoneShape shape : ZoneShape.values()) {
            options.add(Component.translatable("zones.shape." + shape.id()));
        }
        return options;
    }

    private void buildActions(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.points.group.actions");
        add(stack, x, width, row("tp", () -> new ActionRow(0, 0, 10, ROW, Component.translatable("studio.menu.tp"),
                () -> Component.translatable("studio.points.action.go"), () -> plain("tp "))));
        add(stack, x, width, row("here", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("studio.menu.move_here"), () -> Component.translatable("studio.points.action.here"),
                () -> edit("here"))));
        add(stack, x, width, row("delete", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.delete"),
                () -> Component.translatable(screen.isArmed(DROP) ? "studio.sure" : "studio.delete"),
                this::pressDelete).alerting()));
    }

    private void pressDelete() {
        MarkEntry entry = screen.entry();
        if (entry != null && screen.confirmed(DROP)) screen.remove(entry.id());
    }

    private void buildCreate(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.marks.group.new");
        add(stack, x, width, newLabelRow);
        add(stack, x, width, newIdRow);
        add(stack, x, width, row("newkind", () -> new PickRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.kind"), kindLabels(), () -> newKind, picked -> newKind = picked)));
        add(stack, x, width, row("place", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("studio.points.place_row"),
                () -> Component.literal(screen.newX() + "  " + screen.newZ()), this::placeAtPlayer)
                .hint("studio.points.place_row.hint")));
        stack.add(sized(row("create", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("zones.mark.menu.new.create"), () -> Component.translatable("studio.create"),
                this::create)), width), x, ROW / 3);
        if (focusLabel) {
            focusLabel = false;
            screen.setFocused(newLabelRow);
        }
    }

    private void placeAtPlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) screen.placeGhostAt(player.getBlockX(), player.getBlockZ());
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
        screen.create(id, newLabel.trim(), MarkKind.values()[newKind]);
    }

    // WHY: подпись заполняется из снимка только при смене метки или когда по ней нет отложенной
    // WHY: правки: иначе пришедший до отправки снимок вернул бы под пальцы старый текст
    void fill() {
        MarkEntry entry = screen.entry();
        String subject = entry == null ? "" : entry.id();
        boolean fresh = !subject.equals(filledFor);
        filledFor = subject;
        if (entry == null || labelRow.capturing() || !fresh && screen.pending(LABEL)) return;
        filling = true;
        try {
            if (!labelRow.value().equals(entry.label())) labelRow.box().setValue(entry.label());
        } finally {
            filling = false;
        }
    }

    private int read(ToIntFunction<MarkEntry> reader) {
        MarkEntry entry = screen.entry();
        return entry == null ? 0 : reader.applyAsInt(entry);
    }

    private void edit(String tail) {
        MarkEntry entry = screen.entry();
        if (entry != null) screen.now(MarkStudioScreen.COMMAND + "edit " + entry.id() + " " + tail);
    }

    private void plain(String verb) {
        MarkEntry entry = screen.entry();
        if (entry != null) screen.now(MarkStudioScreen.COMMAND + verb + entry.id());
    }

    private void later(String key, String tail) {
        MarkEntry entry = screen.entry();
        if (entry != null) screen.later(key, MarkStudioScreen.COMMAND + "edit " + entry.id() + " " + tail);
    }
}
