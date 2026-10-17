package com.persiki84.battlecraft.client.menu.panel.craft;

import com.persiki84.battlecraft.craft.CraftPhase;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

// WHY: строки инспектора создаются один раз и только переставляются: в них живут анимации смены
// WHY: значения. Тексты чтений собираются на раскладке, а не в кадре, потому что раскладка и есть
// WHY: момент, когда меняется выбранное или приходит снимок
final class CraftRows {
    interface Host {
        CraftTile chosen();

        CraftPhase phase();

        void rephaseChosen(CraftPhase phase);

        void unbanChosen();

        void choosePhase(CraftPhase phase);

        void openShelf();

        void clearPhase();

        boolean clearArmed();
    }

    private static final int ROW = 22;
    private static final int SECTION_GAP = 6;
    private static final Component SURE = Component.translatable("studio.menu.sure");
    private static final Component REMOVE = Component.translatable("studio.craft.remove.value");
    private static final Component ADD = Component.translatable("studio.craft.add.value");
    private static final Component CLEAR = Component.translatable("studio.craft.clear.value");

    private final Host host;
    private final CraftRecipeCount recipes = new CraftRecipeCount();
    private final HeadingRow banHeading = heading("studio.craft.group.ban");
    private final HeadingRow nodeHeading = heading("studio.craft.group.node");
    private final HeadingRow shelfHeading = heading("studio.craft.group.new");
    private final HeadingRow bulkHeading = heading("studio.bulk.group");
    private final PickRow phaseRow;
    private final PickRow newPhaseRow;
    private final ActionRow recipesRow;
    private final ActionRow nowRow;
    private final ActionRow removeRow;
    private final ActionRow countRow;
    private final ActionRow nodeNowRow;
    private final ActionRow addRow;
    private final ActionRow clearRow;
    private Component recipesText = Component.empty();
    private Component nowText = Component.empty();
    private Component countText = Component.empty();
    private Component nodeNowText = Component.empty();

    CraftRows(Host host) {
        this.host = host;
        List<Component> phases = phaseLabels();
        phaseRow = new PickRow(0, 0, 10, ROW, Component.translatable("studio.craft.phase"), phases,
                () -> host.chosen() == null ? 0 : host.chosen().phase().ordinal(),
                index -> host.rephaseChosen(CraftPhase.byOrdinal(index)));
        phaseRow.hint("studio.craft.phase.hint");
        newPhaseRow = new PickRow(0, 0, 10, ROW, Component.translatable("studio.craft.new_phase"), phases,
                () -> host.phase().ordinal(), index -> host.choosePhase(CraftPhase.byOrdinal(index)));
        newPhaseRow.hint("studio.craft.new_phase.hint");
        recipesRow = reading("studio.craft.recipes", () -> recipesText);
        nowRow = reading("studio.craft.now", () -> nowText);
        countRow = reading("studio.craft.node.count", () -> countText);
        nodeNowRow = reading("studio.craft.node.now", () -> nodeNowText);
        removeRow = action("studio.craft.remove", () -> REMOVE, host::unbanChosen).alerting();
        addRow = action("studio.craft.add", () -> ADD, host::openShelf);
        clearRow = action("studio.craft.clear", () -> host.clearArmed() ? SURE : CLEAR, host::clearPhase).alerting();
    }

    private static List<Component> phaseLabels() {
        return List.of(Component.translatable(CraftPhase.ALWAYS.label()),
                Component.translatable(CraftPhase.MATCH.label()), Component.translatable(CraftPhase.LOBBY.label()));
    }

    private static HeadingRow heading(String key) {
        return new HeadingRow(0, 0, 10, ROW, Component.translatable(key));
    }

    private static ActionRow reading(String key, Supplier<Component> value) {
        ActionRow row = new ActionRow(0, 0, 10, ROW, Component.translatable(key), value, () -> { });
        row.hint(key + ".hint");
        row.active = false;
        return row;
    }

    private static ActionRow action(String key, Supplier<Component> value, Runnable run) {
        ActionRow row = new ActionRow(0, 0, 10, ROW, Component.translatable(key), value, run);
        row.hint(key + ".hint");
        return row;
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    void buildBan(StudioStack stack, int x, int width, CraftTile tile) {
        int count = recipes.of(tile);
        recipesText = count == 0 ? Component.translatable("studio.craft.recipes.none")
                : Component.literal(String.valueOf(count));
        nowText = CraftTile.stateOf(tile.phase(), tile.active());
        stack.add(sized(banHeading, width), x);
        stack.add(sized(phaseRow, width), x);
        stack.add(sized(nowRow, width), x);
        stack.add(sized(recipesRow, width), x);
        stack.add(sized(removeRow, width), x, SECTION_GAP);
    }

    void buildNode(StudioStack stack, int x, int width, int count, boolean active) {
        CraftPhase phase = host.phase();
        nodeHeading.setMessage(Component.translatable("studio.craft.node." + phase.id()));
        countText = Component.literal(String.valueOf(count));
        nodeNowText = Component.translatable(active ? "studio.craft.node.now.active" : "studio.craft.node.now.idle");
        stack.add(sized(nodeHeading, width), x);
        stack.add(sized(countRow, width), x);
        stack.add(sized(nodeNowRow, width), x);
        stack.add(sized(addRow, width), x, SECTION_GAP);
        clearRow.active = count > 0;
        stack.add(sized(clearRow, width), x);
    }

    void buildShelf(StudioStack stack, int x, int width) {
        stack.add(sized(shelfHeading, width), x);
        stack.add(sized(newPhaseRow, width), x);
        stack.skip(4);
    }

    void buildBulk(StudioStack stack, int x, int width) {
        stack.add(sized(bulkHeading, width), x);
    }
}
