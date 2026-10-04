package com.persiki84.battlecraft.client.menu.hub;

import com.persiki84.battlecraft.client.menu.TeamSelectScreen;
import com.persiki84.battlecraft.menu.BattleCraftMenuState;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

// WHY: справа то, что делают с выбранным: в матче - свои действия игрока и рычаги оператора, у
// WHY: раздела - вход в него, у модуля - его переключатель. Строки созданы один раз
final class HubInspector {
    private static final int ROW = 22;
    private static final String LOBBY = "LOBBY";
    private static final String STOP = "force-stop";

    private final HubStudioScreen screen;
    private final Map<String, MenuRow> made = new HashMap<>();
    private final Map<String, HeadingRow> headings = new HashMap<>();

    HubInspector(HubStudioScreen screen) {
        this.screen = screen;
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    private void heading(StudioStack stack, int x, int width, String key) {
        HeadingRow heading = headings.computeIfAbsent(key,
                unused -> new HeadingRow(0, 0, 10, ROW, Component.translatable(key)));
        stack.add(sized(heading, width), x, ROW / 3);
    }

    private MenuRow row(String key, Supplier<MenuRow> maker) {
        return made.computeIfAbsent(key, unused -> maker.get());
    }

    private void add(StudioStack stack, int x, int width, AbstractWidget widget) {
        stack.add(sized(widget, width), x);
    }

    void buildMatch(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.hub.group.me");
        add(stack, x, width, row("phase", () -> reading("battlecraft.menu.status.phase", HubInspector::phase)));
        boolean locked = playing();
        add(stack, x, width, locked ? row("team.locked", () -> reading("battlecraft.menu.team",
                () -> Component.translatable("battlecraft.menu.action.locked")))
                : row("team", () -> action("battlecraft.menu.team", "battlecraft.menu.action.choose",
                TeamSelectScreen::open)));
        if (!locked) add(stack, x, width, row("ready", () -> action("battlecraft.menu.ready",
                "battlecraft.menu.action.toggle", () -> screen.now("ready"))));
        add(stack, x, width, row("surrender", () -> action("battlecraft.menu.surrender",
                "battlecraft.menu.action.vote", () -> screen.now("surrender start"))));
        if (screen.admin()) buildOperator(stack, x, width);
    }

    private void buildOperator(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.hub.group.operator");
        add(stack, x, width, row("start", () -> action("battlecraft.menu.force_start", "battlecraft.menu.action.start",
                () -> screen.now("force start"))));
        add(stack, x, width, row("stop", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("battlecraft.menu.force_stop"),
                () -> Component.translatable(screen.isArmed(STOP) ? "studio.sure" : "battlecraft.menu.action.stop"),
                this::pressStop).alerting()));
        add(stack, x, width, row("grace", () -> action("battlecraft.menu.force_grace", "battlecraft.menu.action.clear",
                () -> screen.now("force grace"))));
        add(stack, x, width, row("enabled", () -> new ToggleRow(0, 0, 10, ROW,
                Component.translatable("battlecraft.menu.enabled"),
                () -> !HubStudioScreen.state().getBoolean(BattleCraftMenuState.SOFT_DISABLED),
                value -> screen.now("toggle")).hint("battlecraft.menu.enabled.hint")));
    }

    // WHY: остановка матча снимает всех с точек и раздаёт итог: вторым нажатием, а не случайным
    void pressStop() {
        if (screen.confirmed(STOP)) screen.now("force stop");
    }

    void buildRequirement(StudioStack stack, int x, int width, CompoundTag check) {
        String rule = check.getString(BattleCraftMenuState.RULE_ID);
        heading(stack, x, width, "battlecraft.requirement." + rule);
        add(stack, x, width, row("state:" + rule, () -> reading("studio.hub.requirement.state",
                () -> HubStudioScreen.requirementValue(screen.requirement(rule)))));
        if (screen.admin() && HubStudioScreen.solvable(rule)) {
            add(stack, x, width, row("solve:" + rule, () -> action("studio.hub.requirement.solve",
                    "studio.hub.requirement.do", () -> screen.solve(rule)).hint("battlecraft.requirement." + rule + ".hint")));
        }
    }

    void buildSection(StudioStack stack, int x, int width, HubSection section) {
        add(stack, x, width, row("open:" + section.name(), () -> action("studio.hub.open",
                "battlecraft.menu.action.open", () -> screen.open(section))));
        ModuleId module = moduleOf(section);
        if (module != null) add(stack, x, width, moduleToggle(module));
    }

    void buildModule(StudioStack stack, int x, int width, ModuleId module) {
        add(stack, x, width, moduleToggle(module));
        HubSection section = HubSection.of(module);
        if (section != null) add(stack, x, width, row("open:" + section.name(), () -> action("studio.hub.open",
                "battlecraft.menu.action.open", () -> screen.open(section))));
    }

    private MenuRow moduleToggle(ModuleId module) {
        return row("module:" + module.id(), () -> new ToggleRow(0, 0, 10, ROW,
                Component.translatable("studio.hub.module.on"), () -> HubStudioScreen.moduleOn(module),
                value -> screen.now("module " + module.id() + " " + value)).hint(module.label() + ".hint"));
    }

    private static ModuleId moduleOf(HubSection section) {
        for (ModuleId module : ModuleId.values()) {
            if (HubSection.of(module) == section) return module;
        }
        return null;
    }

    private static boolean playing() {
        CompoundTag state = HubStudioScreen.state();
        return !state.isEmpty() && state.getBoolean(BattleCraftMenuState.IN_TEAM)
                && !LOBBY.equals(state.getString("phase"));
    }

    static Component phase() {
        String phase = HubStudioScreen.state().getString("phase");
        if (phase.isEmpty()) return Component.empty();
        return Component.translatable("battlecraft.menu.phase." + phase.toLowerCase(Locale.ROOT));
    }

    private static MenuRow reading(String label, Supplier<Component> value) {
        ActionRow row = new ActionRow(0, 0, 10, ROW, Component.translatable(label), value, () -> { });
        row.active = false;
        return row;
    }

    private static ActionRow action(String label, String value, Runnable run) {
        return new ActionRow(0, 0, 10, ROW, Component.translatable(label), () -> Component.translatable(value), run);
    }
}
