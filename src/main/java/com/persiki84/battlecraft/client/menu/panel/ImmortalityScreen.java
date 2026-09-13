package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.PanelScreen;
import com.persiki84.shared.client.menu.ToggleRow;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ImmortalityScreen extends PanelScreen {
    private static final String COMMAND = "immortality";
    private static final int MAX_SECONDS = 3600;

    public ImmortalityScreen() {
        super(Component.translatable("immortality.menu.title"));
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.IMMORTALITY;
    }

    @Override
    protected List<Page> pages() {
        return List.of(
                new Page(Component.translatable("immortality.menu.tab.main"), this::rows),
                new Page(Component.translatable("immortality.menu.tab.players"), this::playerRows));
    }

    private List<AbstractWidget> rows() {
        return List.of(
                toggle("immortality.menu.enabled", () -> MenuData.state(menuId()).getBoolean("enabled"),
                        value -> send(COMMAND + (value ? " enable" : " disable"))),
                number("immortality.menu.duration", () -> MenuData.state(menuId()).getInt("duration"),
                        value -> send(COMMAND + " setduration " + value), 1, MAX_SECONDS, 5),
                action("immortality.menu.clear_all", "immortality.menu.action.clear",
                        () -> send(COMMAND + " clearall")));
    }

    // WHY: выдача отказывается работать при выключенном модуле, а выключение чистит список
    // WHY: неуязвимых, поэтому строки игроков там заперты, а не молча ничего не делают
    private List<AbstractWidget> playerRows() {
        boolean enabled = MenuData.state(menuId()).getBoolean("enabled");
        List<AbstractWidget> rows = new ArrayList<>();
        if (!enabled) rows.add(reading("immortality.menu.disabled", Component::empty));

        for (CompoundTag player : players()) {
            rows.add(playerRow(player, enabled));
        }
        if (rows.isEmpty()) rows.add(reading("immortality.menu.no_players", Component::empty));
        return rows;
    }

    private AbstractWidget playerRow(CompoundTag player, boolean enabled) {
        String name = player.getString("name");
        ToggleRow row = new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.literal(name),
                () -> player.getBoolean("immortal"), value -> grant(name, value));
        if (player.getBoolean("immortal")) {
            row.note(Component.translatable("immortality.menu.remaining", player.getInt("remaining")));
        }
        row.active = enabled;
        return row;
    }

    private void grant(String name, boolean wanted) {
        send(COMMAND + (wanted ? " give " : " remove ") + name);
    }

    private List<CompoundTag> players() {
        List<CompoundTag> players = new ArrayList<>();
        for (Tag stored : MenuData.state(menuId()).getList(ModuleMenuStates.IMMORTAL_PLAYERS, Tag.TAG_COMPOUND)) {
            players.add((CompoundTag) stored);
        }
        return players;
    }
}
