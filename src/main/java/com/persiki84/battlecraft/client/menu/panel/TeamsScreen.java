package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.client.menu.TeamSelectScreen;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.PanelScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TeamsScreen extends PanelScreen {
    private static final String COMMAND = "battlecraft team";
    private static final String TEAMS_KEY = "teams";
    private static final String[] SUGGESTED = {"red", "blue", "green", "yellow"};
    private static final int MAX_NAME = 32;

    // WHY: команда ждёт brigadier word(): пробел или кириллица в имени уронят разбор ещё до сервера
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_.+-]+");

    private FieldRow nameRow;

    public TeamsScreen() {
        super(Component.translatable("battlecraft.teams.title"));
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.TEAMS;
    }

    @Override
    protected List<Page> pages() {
        return List.of(new Page(Component.translatable("battlecraft.teams.tab.list"), this::rows));
    }

    private List<AbstractWidget> rows() {
        List<AbstractWidget> rows = new ArrayList<>();
        List<CompoundTag> teams = teams();

        for (CompoundTag team : teams) {
            rows.add(teamRow(team));
        }
        if (teams.isEmpty()) {
            rows.add(reading("battlecraft.teams.empty", Component::empty));
        }

        rows.add(nameRow());
        rows.add(action("battlecraft.teams.new", "battlecraft.teams.action.create", this::createTyped));
        addCreateRows(rows, teams);
        rows.add(action("battlecraft.teams.pick", "battlecraft.menu.action.choose", TeamSelectScreen::open));
        return rows;
    }

    // WHY: строка поля переживает пересборку: новая на каждый кадр стирала бы набранное имя
    // WHY: и уводила бы курсор из поля на первой же смене состава команд
    private FieldRow nameRow() {
        if (nameRow == null) {
            nameRow = new FieldRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                    Component.translatable("battlecraft.teams.name"),
                    Component.translatable("battlecraft.teams.name.placeholder"),
                    "", MAX_NAME, value -> { });
            nameRow.hint("battlecraft.teams.name" + HINT_SUFFIX);
        }
        nameRow.setX(rowsLeft());
        nameRow.setWidth(rowsWidth());
        return nameRow;
    }

    private void createTyped() {
        if (create(nameRow().value().trim())) nameRow.box().setValue("");
    }

    private ActionRow teamRow(CompoundTag team) {
        String name = team.getString("name");
        int players = team.getInt("players");

        return action(Component.literal(name),
                Component.translatable("battlecraft.teams.players", players),
                () -> send(COMMAND + " remove " + name));
    }

    private void addCreateRows(List<AbstractWidget> rows, List<CompoundTag> teams) {
        for (String name : SUGGESTED) {
            if (taken(teams, name)) continue;

            rows.add(action(Component.translatable("battlecraft.teams.create", name),
                    Component.translatable("battlecraft.teams.action.create"),
                    () -> create(name)));
        }
    }

    private boolean create(String name) {
        if (name.isEmpty()) {
            MenuFeedback.show(Component.translatable("battlecraft.teams.error.name"), true);
            return false;
        }
        if (!WORD.matcher(name).matches()) {
            MenuFeedback.show(Component.translatable("battlecraft.teams.error.chars"), true);
            return false;
        }

        send(COMMAND + " add " + name);
        return true;
    }

    private static boolean taken(List<CompoundTag> teams, String name) {
        for (CompoundTag team : teams) {
            if (team.getString("name").equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private List<CompoundTag> teams() {
        List<CompoundTag> teams = new ArrayList<>();
        for (Tag tag : MenuData.state(menuId()).getList(TEAMS_KEY, Tag.TAG_COMPOUND)) {
            teams.add((CompoundTag) tag);
        }
        return teams;
    }
}
