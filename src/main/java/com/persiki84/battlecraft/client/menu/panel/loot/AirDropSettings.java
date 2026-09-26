package com.persiki84.battlecraft.client.menu.panel.loot;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

import static com.persiki84.airdrop.config.AirDropLimits.*;

// WHY: настройки сброса живут строками в центре студии: видимого объекта у них нет, а держать их
// WHY: отдельным экраном значит снова гонять админа между окнами одного раздела
final class AirDropSettings {
    private static final int ROW = 22;
    private static final int GAP = 4;
    private static final String CONFIG = "airdrop config ";
    private static final String TOGGLE = "airdrop toggle ";

    private static final int ART = 44;
    private static final float LINE_SCALE = 0.72f;
    private static final String KILL = "kill-all";
    private static final Component SURE = Component.translatable("studio.sure");
    private static final Component REMOVE = Component.translatable("airdrop.menu.action.remove");

    private final AirDropStudioScreen screen;
    private final List<AbstractWidget> rows = new ArrayList<>();
    private final HeadingRow lootHeading = heading("studio.drop.group.loot");
    private final HeadingRow dangerHeading = heading("studio.drop.group.danger");
    private final ActionRow openRow;
    private final ActionRow killRow;
    private List<String> pickedNames = List.of();
    private PickRow tableRow;

    AirDropSettings(AirDropStudioScreen screen) {
        this.screen = screen;
        openRow = hinted(new ActionRow(0, 0, 10, ROW, Component.translatable("airdrop.menu.open_editor"),
                () -> Component.translatable("airdrop.menu.action.open"),
                () -> screen.openTable(state().getString("lootTable"))), "airdrop.menu.open_editor");
        killRow = new ActionRow(0, 0, 10, ROW, Component.translatable("airdrop.menu.kill_all"),
                () -> screen.isArmed(KILL) ? SURE : REMOVE, this::killAll).alerting();
        killRow.hint("airdrop.menu.kill_all.hint");
        addStateRows();
        addAreaRows();
        addTimingRows();
    }

    private static CompoundTag state() {
        return MenuData.state(ModuleMenuStates.AIRDROP);
    }

    private static int value(String key) {
        return state().getInt(key);
    }

    private void addStateRows() {
        rows.add(heading("airdrop.menu.group.state"));
        rows.add(flag("airdrop.menu.enabled", "modEnabled", "mod"));
        rows.add(flag("airdrop.menu.auto_spawn", "autoSpawnEnabled", "spawn"));
        rows.add(flag("airdrop.menu.match_only", "matchOnly", "match_only"));
        rows.add(flag("airdrop.menu.clear_on_end", "clearOnMatchEnd", "clear_on_end"));
        rows.add(flag("airdrop.menu.announce", "announceCoords", "announce"));
    }

    private void addAreaRows() {
        rows.add(heading("airdrop.menu.group.area"));
        rows.add(flag("airdrop.menu.world_center", "centerAtWorldSpawn", "world_center"));
        rows.add(hinted(new ActionRow(0, 0, 10, ROW, Component.translatable("airdrop.menu.center_here"),
                () -> Component.translatable("airdrop.menu.action.here"), () -> screen.now(CONFIG + "center here")),
                "airdrop.menu.center_here"));
        rows.add(number("airdrop.menu.center_x", "centerX",
                set -> CONFIG + "center " + set + " " + value("centerZ"), -COORDINATE, COORDINATE, 1));
        rows.add(number("airdrop.menu.center_z", "centerZ",
                set -> CONFIG + "center " + value("centerX") + " " + set, -COORDINATE, COORDINATE, 1));
        rows.add(number("airdrop.menu.radius", "spawnRadius", set -> CONFIG + "radius " + set, RADIUS_MIN, RADIUS_MAX, 50));
        rows.add(number("airdrop.menu.height", "height", set -> CONFIG + "height " + set, HEIGHT_MIN, HEIGHT_MAX, 10));
    }

    private void addTimingRows() {
        rows.add(heading("airdrop.menu.tab.timing"));
        rows.add(number("airdrop.menu.interval", "intervalSeconds", set -> CONFIG + "interval " + set,
                INTERVAL_MIN, INTERVAL_MAX, 30));
        rows.add(number("airdrop.menu.chance", "chancePercent", set -> CONFIG + "chance " + set, 0, PERCENT, 5));
        rows.add(number("airdrop.menu.flight", "flightSeconds", set -> CONFIG + "flight_time " + set,
                FLIGHT_MIN, FLIGHT_MAX, 5));
        rows.add(number("airdrop.menu.open_delay", "openDelaySeconds", set -> CONFIG + "open_delay " + set,
                0, OPEN_DELAY_MAX, 1));
        rows.add(number("airdrop.menu.despawn_empty", "despawnEmptySeconds", set -> CONFIG + "despawn_empty " + set,
                DESPAWN_MIN, DESPAWN_MAX, 30));
        rows.add(number("airdrop.menu.despawn_filled", "despawnFilledSeconds", set -> CONFIG + "despawn_filled " + set,
                DESPAWN_MIN, DESPAWN_MAX, 30));
        rows.add(number("airdrop.menu.warn", "warnSeconds", set -> CONFIG + "warn_time " + set, 0, WARN_MAX, 10));
    }

    private static HeadingRow heading(String key) {
        return new HeadingRow(0, 0, 10, ROW, Component.translatable(key));
    }

    private static <T extends MenuRow> T hinted(T row, String key) {
        row.hint(key + ".hint");
        return row;
    }

    private ToggleRow flag(String label, String key, String command) {
        return hinted(new ToggleRow(0, 0, 10, ROW, Component.translatable(label),
                () -> state().getBoolean(key), on -> screen.now(TOGGLE + command + " " + on)), label);
    }

    private NumberRow number(String label, String key, java.util.function.IntFunction<String> command,
                             int minimum, int maximum, int step) {
        return hinted(new NumberRow(0, 0, 10, ROW, Component.translatable(label), () -> value(key),
                set -> screen.later(key, command.apply(set)), minimum, maximum, step), label);
    }

    private void killAll() {
        if (screen.confirmed(KILL)) screen.now("airdrop kill_all");
    }

    // WHY: список таблиц меняется, а варианты строки выбора неизменны: строка пересоздаётся только
    // WHY: при смене состава таблиц, иначе её анимация переключения обрывалась бы каждым снимком
    private PickRow tableRow() {
        List<String> names = LootSnapshot.names();
        if (tableRow != null && names.equals(pickedNames)) return tableRow;
        pickedNames = names;
        List<Component> labels = new ArrayList<>();
        for (String name : names) {
            labels.add(Component.literal(name));
        }
        tableRow = hinted(new PickRow(0, 0, 10, ROW, Component.translatable("airdrop.menu.loot_table"), labels,
                () -> Math.max(0, pickedNames.indexOf(state().getString("lootTable"))),
                picked -> screen.now(CONFIG + "table " + pickedNames.get(picked))), "airdrop.menu.loot_table");
        return tableRow;
    }

    void inspector(StudioStack stack, int x, int width) {
        stack.add(sized(lootHeading, width), x);
        if (!pickedNamesEmpty()) stack.add(sized(tableRow(), width), x);
        stack.add(sized(openRow, width), x);
        stack.add(sized(dangerHeading, width), x, GAP);
        stack.add(sized(killRow, width), x);
    }

    private static boolean pickedNamesEmpty() {
        return LootSnapshot.names().isEmpty();
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    Component stats() {
        CompoundTag state = state();
        if (state.isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        if (!state.getBoolean("modEnabled")) return Component.translatable("studio.drop.stats.off");
        if (!state.getBoolean("autoSpawnEnabled")) return Component.translatable("studio.drop.stats.manual");
        return Component.translatable("studio.drop.stats", state.getInt("intervalSeconds"), state.getInt("chancePercent"));
    }

    int artHeight() {
        return ART;
    }

    void renderArt(GuiGraphics graphics, float left, float top, float width, float appear) {
        CompoundTag state = state();
        if (state.isEmpty()) return;
        UiRender.textTrackedFit(graphics, Minecraft.getInstance().font, stats(), left + width / 2.0f, top, 14.0f,
                width, 0.9f, 0.0f, UiTheme.alpha(UiAccent.text(), appear), false);
        line(graphics, areaLine(state), left, width, top + 16.0f, appear);
        line(graphics, Component.translatable("studio.drop.art.table", state.getString("lootTable")),
                left, width, top + 27.0f, appear);
    }

    private static Component areaLine(CompoundTag state) {
        if (state.getBoolean("centerAtWorldSpawn")) {
            return Component.translatable("studio.drop.art.spawn", state.getInt("spawnRadius"));
        }
        return Component.translatable("studio.drop.art.area", state.getInt("centerX"), state.getInt("centerZ"),
                state.getInt("spawnRadius"));
    }

    private static void line(GuiGraphics graphics, Component text, float left, float width, float y, float appear) {
        UiRender.textTrackedFit(graphics, Minecraft.getInstance().font, text, left + width / 2.0f, y, 10.0f, width,
                LINE_SCALE, 0.0f, UiTheme.alpha(UiAccent.textDim(), appear), false);
    }

    void place(StudioStack stack, int x, int width) {
        for (AbstractWidget row : rows) {
            row.setWidth(width);
            stack.add(row, x, row instanceof HeadingRow && row != rows.get(0) ? GAP : 0);
        }
    }
}
