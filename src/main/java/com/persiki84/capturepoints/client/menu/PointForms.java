package com.persiki84.capturepoints.client.menu;

import com.persiki84.capturepoints.capture.IncomePayout;
import com.persiki84.capturepoints.menu.CapturePointMenuState;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.zone.ZoneShape;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// WHY: общие правила точек и создание новой - не свойства одной точки, поэтому у них свои строки:
// WHY: новая точка ставится щелчком по карте, а форма справа держит всё остальное о ней
final class PointForms {
    private static final int ROW = 22;
    private static final int NAME_LIMIT = 32;
    private static final int MAX_RADIUS = 100;
    private static final int MAX_SECONDS = 3600;
    private static final int DEFAULT_RADIUS = 10;
    private static final int DEFAULT_CAPTURE = 30;
    private static final int DEFAULT_COOLDOWN = 60;
    private static final String RESET_ALL = "reset-all";
    private static final String POINT = "capturepoint ";
    private static final String FINAL = "finalpoint ";

    private final PointStudioScreen screen;
    private final HeadingRow commonHeading = heading("studio.points.group.common");
    private final HeadingRow createHeading = heading("studio.points.group.new");
    private final ToggleRow protectionRow;
    private final ToggleRow breakPlacedRow;
    private final ToggleRow denyPlaceRow;
    private final ToggleRow pointMarkersRow;
    private final ToggleRow finalMarkersRow;
    private final ToggleRow openerOnlyRow;
    private final PickRow payoutRow;
    private final ActionRow resetAllRow;
    private final FieldRow nameRow;
    private final PickRow kindRow;
    private final PickRow shapeRow;
    private final NumberRow radiusRow;
    private final NumberRow timeRow;
    private final NumberRow cooldownRow;
    private final ActionRow placeRow;
    private final ActionRow createRow;
    private String typed = "";
    private int kind;
    private int shape;
    private int radius = DEFAULT_RADIUS;
    private int seconds = DEFAULT_CAPTURE;
    private int cooldown = DEFAULT_COOLDOWN;
    private boolean focusName;

    PointForms(PointStudioScreen screen) {
        this.screen = screen;
        protectionRow = toggle("capturepoints.menu.protection", CapturePointMenuState.PROTECTION,
                value -> screen.now(POINT + "protection " + (value ? "enable" : "disable")));
        breakPlacedRow = toggle("capturepoints.menu.break_placed", CapturePointMenuState.BREAK_PLACED,
                value -> screen.now(POINT + "protection breakplaced " + value));
        denyPlaceRow = toggle("capturepoints.menu.deny_place", CapturePointMenuState.DENY_PLACE,
                value -> screen.now(POINT + "protection denyplace " + value));
        pointMarkersRow = toggle("capturepoints.menu.server_markers", CapturePointMenuState.CAPTURE_MARKERS,
                value -> screen.now(POINT + "serverviewmarkers " + value));
        finalMarkersRow = toggle("capturepoints.menu.final_markers", CapturePointMenuState.FINAL_MARKERS,
                value -> screen.now(FINAL + "serverviewmarkers " + value));
        openerOnlyRow = toggle("capturepoints.menu.final_opener_only", CapturePointMenuState.FINAL_OPENER_ONLY,
                value -> screen.now(FINAL + "openeronly " + value));
        openerOnlyRow.hint("capturepoints.menu.final_opener_only.hint");
        payoutRow = payoutRow();
        resetAllRow = new ActionRow(0, 0, 10, ROW, Component.translatable("capturepoints.menu.reset_all"),
                () -> Component.translatable(screen.isArmed(RESET_ALL) ? "studio.sure"
                        : "capturepoints.menu.action.reset"), this::pressResetAll).alerting();
        nameRow = new FieldRow(0, 0, 10, ROW, Component.translatable("capturepoints.menu.new.name"),
                Component.translatable("studio.points.new_name"), "", NAME_LIMIT, value -> typed = value);
        kindRow = new PickRow(0, 0, 10, ROW, Component.translatable("capturepoints.menu.kind"),
                List.of(Component.translatable("capturepoints.menu.kind.point"),
                        Component.translatable("capturepoints.menu.kind.final")), () -> kind, picked -> kind = picked);
        shapeRow = new PickRow(0, 0, 10, ROW, Component.translatable("capturepoints.menu.shape"), shapeLabels(),
                () -> shape, picked -> shape = picked);
        radiusRow = new NumberRow(0, 0, 10, ROW, Component.translatable("capturepoints.menu.radius"), () -> radius,
                value -> radius = value, 1, MAX_RADIUS, 1);
        timeRow = new NumberRow(0, 0, 10, ROW, Component.translatable("capturepoints.menu.capture_time"),
                () -> seconds, value -> seconds = value, 1, MAX_SECONDS, 5);
        cooldownRow = new NumberRow(0, 0, 10, ROW, Component.translatable("capturepoints.menu.cooldown"),
                () -> cooldown, value -> cooldown = value, 0, MAX_SECONDS, 5);
        placeRow = new ActionRow(0, 0, 10, ROW, Component.translatable("studio.points.place_row"),
                () -> Component.literal(screen.newX() + "  " + screen.newZ()), this::placeAtPlayer);
        placeRow.hint("studio.points.place_row.hint");
        createRow = new ActionRow(0, 0, 10, ROW, Component.translatable("studio.points.create"),
                () -> Component.translatable("studio.create"), this::create);
    }

    private PickRow payoutRow() {
        List<Component> labels = new ArrayList<>();
        for (IncomePayout payout : IncomePayout.values()) {
            labels.add(Component.translatable(payout.translationKey()));
        }
        PickRow row = new PickRow(0, 0, 10, ROW, Component.translatable("capturepoints.menu.payout"), labels,
                () -> IncomePayout.byId(PointStudioScreen.state().getString(CapturePointMenuState.INCOME_PAYOUT)).ordinal(),
                picked -> screen.now(POINT + "incomepayout " + IncomePayout.values()[picked].id()));
        row.hint("capturepoints.menu.payout.hint");
        return row;
    }

    private static HeadingRow heading(String key) {
        return new HeadingRow(0, 0, 10, ROW, Component.translatable(key));
    }

    private ToggleRow toggle(String label, String key, Consumer<Boolean> apply) {
        ToggleRow row = new ToggleRow(0, 0, 10, ROW, Component.translatable(label),
                () -> PointStudioScreen.state().getBoolean(key), apply);
        row.hint(label + ".hint");
        return row;
    }

    private static List<Component> shapeLabels() {
        List<Component> options = new ArrayList<>();
        for (ZoneShape value : ZoneShape.values()) {
            options.add(Component.translatable("zones.shape." + value.id()));
        }
        return options;
    }

    String typedName() {
        return typed;
    }

    ZoneShape newShape() {
        return ZoneShape.values()[shape];
    }

    int newRadius() {
        return radius;
    }

    void beginCreate() {
        nameRow.box().setValue("");
        typed = "";
        focusName = true;
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    void buildCommon(StudioStack stack, int x, int width) {
        stack.add(sized(commonHeading, width), x);
        stack.add(sized(protectionRow, width), x);
        blockAgainst(breakPlacedRow, CapturePointMenuState.DENY_PLACE, "capturepoints.menu.blocked.deny_place");
        blockAgainst(denyPlaceRow, CapturePointMenuState.BREAK_PLACED, "capturepoints.menu.blocked.break_placed");
        stack.add(sized(breakPlacedRow, width), x);
        stack.add(sized(denyPlaceRow, width), x);
        stack.add(sized(pointMarkersRow, width), x);
        stack.add(sized(finalMarkersRow, width), x);
        stack.add(sized(openerOnlyRow, width), x);
        stack.add(sized(payoutRow, width), x);
        stack.add(sized(resetAllRow, width), x, ROW / 3);
    }

    // WHY: слом блоков игроков и запрет установки противоречат друг другу: включённый первый
    // WHY: запирает второй с объяснением, а не молча отменяет его на сервере
    private static void blockAgainst(ToggleRow row, String rival, String reason) {
        if (PointStudioScreen.state().getBoolean(rival)) {
            row.block(Component.translatable(reason));
        } else {
            row.unblock();
        }
    }

    private void pressResetAll() {
        if (screen.confirmed(RESET_ALL)) screen.now(POINT + "resetall");
    }

    void buildCreate(StudioStack stack, int x, int width) {
        stack.add(sized(createHeading, width), x);
        stack.add(sized(nameRow, width), x);
        stack.add(sized(kindRow, width), x);
        stack.add(sized(shapeRow, width), x);
        stack.add(sized(radiusRow, width), x);
        stack.add(sized(timeRow, width), x);
        stack.add(sized(cooldownRow, width), x);
        stack.add(sized(placeRow, width), x);
        stack.add(sized(createRow, width), x, ROW / 3);
        if (focusName) takeNameFocus();
    }

    private void takeNameFocus() {
        focusName = false;
        screen.setFocused(nameRow);
        nameRow.box().moveCursorToEnd();
    }

    private void placeAtPlayer() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) screen.placeGhostAt(player.getBlockX(), player.getBlockZ());
    }

    boolean submitCreate() {
        if (!nameRow.capturing()) return false;
        create();
        return true;
    }

    // WHY: имя точки уходит в кавычках строкой Brigadier, поэтому кавычка и обратная косая в нём
    // WHY: сломали бы команду: они отклоняются здесь, а не ответом сервера
    private void create() {
        String name = typed.trim();
        if (name.isEmpty()) {
            MenuFeedback.show(Component.translatable("capturepoints.menu.error.no_name"), true);
            return;
        }
        if (name.contains("\"") || name.contains("\\")) {
            MenuFeedback.show(Component.translatable("studio.points.error.bad_name"), true);
            return;
        }
        screen.create(name, kind == 1, radius, seconds, cooldown, newShape());
    }
}
