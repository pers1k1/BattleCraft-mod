package com.persiki84.battlecraft.client.menu.panel.mode;

import com.persiki84.battlecraft.mode.ModeId;
import com.persiki84.battlecraft.mode.ModeLimits;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.gunsmith.GunSmith;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

// WHY: строки создаются один раз и только переставляются: в них живут анимации смены значения.
// WHY: Числа уходят отложенной командой (не на каждый щелчок +/-), переключатели сразу
final class ModeRows {
    interface Host {
        ModeId mode();

        ModeView view();

        KitTile chosen();

        void later(String key, String command);

        void now(String command);

        void openShelf();

        void clearKit();

        boolean clearArmed();

        void resetMode();

        boolean resetArmed();

        void removeChosen();

        void openGuns();

        boolean pending(String key);
    }

    private static final int ROW = 22;
    private static final int SECTION_GAP = 6;
    private static final Component SURE = Component.translatable("studio.menu.sure");

    private final Host host;
    private final HeadingRow modeHeading = heading("studio.modes.group.choice");
    private final HeadingRow matchHeading = heading("studio.modes.group.match");
    private final HeadingRow scoreHeading = heading("studio.modes.group.score");
    private final HeadingRow startHeading = heading("studio.modes.group.start");
    private final HeadingRow kitHeading = heading("studio.modes.group.kit");
    private ToggleRow defaultRow;
    private ToggleRow lockRow;
    private ToggleRow quarriesRow;
    private NumberRow targetRow;
    private NumberRow capRow;
    private NumberRow suddenMarginRow;
    private NumberRow suddenMinutesRow;
    private NumberRow pointsRow;
    private NumberRow killsRow;
    private NumberRow vehiclesRow;
    private NumberRow transportRow;
    private NumberRow moneyRow;
    private NumberRow countRow;
    private ActionRow addRow;
    private ActionRow clearRow;
    private ActionRow resetRow;
    private ActionRow removeRow;
    private ActionRow gunsRow;
    private static final String DESCRIPTION = "mode:description";
    private FieldRow descriptionRow;

    ModeRows(Host host) {
        this.host = host;
        createChoiceRows();
        createMatchRows();
        createStartRows();
        createTextRows();
    }

    // WHY: описание читают игроки в правилах матча; уходит отложенной командой, как числа: на паузе в
    // WHY: наборе и при закрытии студии, а не на каждую букву
    private void createTextRows() {
        descriptionRow = new FieldRow(0, 0, 10, ROW, Component.translatable("studio.modes.description"),
                Component.translatable("studio.modes.description.placeholder"), "", ModeLimits.DESCRIPTION_MAX,
                this::describe);
        descriptionRow.hint("studio.modes.description.hint");
    }

    private void describe(String value) {
        if (value.strip().equals(settings().description())) return;
        host.later(DESCRIPTION, ModeOrders.describe(host.mode(), value));
    }

    // WHY: поле показывает сохранённое описание выбранного режима, но не перебивает ни набираемый, ни
    // WHY: ещё не ушедший на сервер текст
    private void syncDescription() {
        String saved = settings().description();
        if (descriptionRow.capturing() || host.pending(DESCRIPTION) || descriptionRow.value().strip().equals(saved)) return;
        descriptionRow.box().setValue(saved);
    }

    private void createChoiceRows() {
        defaultRow = toggle("studio.modes.default", () -> host.view().active() == host.mode(), this::makeDefault);
        lockRow = toggle("studio.modes.lock", () -> host.view().locked(), value -> host.now(ModeOrders.lock(value)));
        resetRow = action("studio.modes.reset", () -> host.resetArmed() ? SURE
                : Component.translatable("studio.modes.reset.value"), host::resetMode).alerting();
    }

    private void createMatchRows() {
        quarriesRow = toggle("studio.modes.quarries", () -> settings().quarries(),
                value -> host.now(ModeOrders.quarries(host.mode(), value)));
        targetRow = number("studio.modes.target", "target", ModeView.Settings::scoreTarget, ModeLimits.TARGET_MAX, 10);
        capRow = number("studio.modes.cap", "cap", ModeView.Settings::capMinutes, ModeLimits.CAP_MINUTES_MAX, 5);
        suddenMarginRow = number("studio.modes.sudden_margin", "sudden_margin", ModeView.Settings::suddenMargin,
                ModeLimits.SUDDEN_MARGIN_MAX, 1);
        suddenMinutesRow = number("studio.modes.sudden_minutes", "sudden_minutes", ModeView.Settings::suddenMinutes,
                ModeLimits.SUDDEN_MINUTES_MAX, 1);
        pointsRow = number("studio.modes.points", "points", ModeView.Settings::pointScore, ModeLimits.SCORE_MAX, 1);
        killsRow = number("studio.modes.kills", "kills", ModeView.Settings::killScore, ModeLimits.SCORE_MAX, 1);
        vehiclesRow = number("studio.modes.vehicles", "vehicles", ModeView.Settings::vehicleScore,
                ModeLimits.SCORE_MAX, 1);
        transportRow = number("studio.modes.transport", "transport", ModeView.Settings::transportScore,
                ModeLimits.SCORE_MAX, 1);
    }

    private void createStartRows() {
        moneyRow = numberRow("studio.modes.money", () -> settings().startMoney(),
                value -> host.later("mode:money", ModeOrders.money(host.mode(), value)), 0, (int) ModeLimits.MONEY_MAX, 100);
        countRow = numberRow("studio.modes.kit.count", () -> host.chosen() == null ? 1 : host.chosen().count(),
                this::countChosen, 1, ModeLimits.KIT_COUNT_MAX, 1);
        addRow = action("studio.modes.kit.add", () -> Component.translatable("studio.modes.kit.add.value"), host::openShelf);
        clearRow = action("studio.modes.kit.clear", () -> host.clearArmed() ? SURE
                : Component.translatable("studio.modes.kit.clear.value"), host::clearKit).alerting();
        removeRow = action("studio.modes.kit.remove", () -> Component.translatable("studio.modes.kit.remove.value"),
                host::removeChosen).alerting();
        gunsRow = action("studio.modes.kit.guns", () -> Component.translatable("studio.modes.kit.guns.value"),
                host::openGuns);
    }

    // WHY: «по умолчанию» снимается только выбором другого режима: выключить его значит оставить лобби без режима
    private void makeDefault(boolean value) {
        if (value) host.now(ModeOrders.active(host.mode()));
    }

    private ModeView.Settings settings() {
        return host.view().settings(host.mode());
    }

    private void countChosen(int value) {
        KitTile tile = host.chosen();
        if (tile != null) host.later("mode:count:" + tile.index(), ModeOrders.kitCount(host.mode(), tile.index(), value));
    }

    private static HeadingRow heading(String key) {
        return new HeadingRow(0, 0, 10, ROW, Component.translatable(key));
    }

    private static ToggleRow toggle(String key, BooleanSupplier value, Consumer<Boolean> apply) {
        ToggleRow row = new ToggleRow(0, 0, 10, ROW, Component.translatable(key), value, apply);
        row.hint(key + ".hint");
        return row;
    }

    private NumberRow number(String key, String field, ToIntFunction<ModeView.Settings> read, int maximum, int step) {
        return numberRow(key, () -> read.applyAsInt(settings()),
                value -> host.later("mode:" + field, ModeOrders.number(host.mode(), field, value)), 0, maximum, step);
    }

    // WHY: у нулевого значения своя подпись («выкл», «без потолка»): ноль здесь значит «правило снято»
    private static NumberRow numberRow(String key, IntSupplier value, IntConsumer apply, int minimum, int maximum,
                                       int step) {
        NumberRow row = new NumberRow(0, 0, 10, ROW, Component.translatable(key), value, apply, minimum, maximum, step);
        if (minimum == 0) row.floorLabel(Component.translatable(key + ".off"));
        row.hint(key + ".hint");
        return row;
    }

    private static ActionRow action(String key, Supplier<Component> value, Runnable run) {
        ActionRow row = new ActionRow(0, 0, 10, ROW, Component.translatable(key), value, run);
        row.hint(key + ".hint");
        return row;
    }

    private static <T extends AbstractWidget> T sized(T row, int width) {
        row.setWidth(width);
        return row;
    }

    void buildMode(StudioStack stack, int x, int width) {
        modeHeading.setMessage(Component.translatable(host.mode().translationKey()));
        stack.add(sized(modeHeading, width), x);
        stack.add(sized(defaultRow, width), x);
        stack.add(sized(lockRow, width), x);
        stack.add(sized(matchHeading, width), x, SECTION_GAP);
        stack.add(sized(quarriesRow, width), x);
        stack.add(sized(targetRow, width), x);
        stack.add(sized(capRow, width), x);
        stack.add(sized(suddenMarginRow, width), x);
        stack.add(sized(suddenMinutesRow, width), x);
        stack.add(sized(scoreHeading, width), x, SECTION_GAP);
        stack.add(sized(pointsRow, width), x);
        stack.add(sized(killsRow, width), x);
        stack.add(sized(vehiclesRow, width), x);
        stack.add(sized(transportRow, width), x);
        stack.add(sized(startHeading, width), x, SECTION_GAP);
        stack.add(sized(moneyRow, width), x);
        syncDescription();
        stack.add(sized(descriptionRow, width), x);
        stack.add(sized(addRow, width), x);
        clearRow.active = !settings().kit().isEmpty();
        stack.add(sized(clearRow, width), x);
        stack.add(sized(resetRow, width), x, SECTION_GAP);
    }

    void buildKit(StudioStack stack, int x, int width, KitTile tile) {
        kitHeading.setMessage(tile.name());
        stack.add(sized(kitHeading, width), x);
        stack.add(sized(countRow, width), x);
        if (GunSmith.isGun(tile.icon())) stack.add(sized(gunsRow, width), x);
        stack.add(sized(removeRow, width), x, SECTION_GAP);
    }

    void buildShelf(StudioStack stack, int x, int width) {
        kitHeading.setMessage(Component.translatable("studio.modes.group.new"));
        stack.add(sized(kitHeading, width), x);
        stack.skip(4);
    }
}
