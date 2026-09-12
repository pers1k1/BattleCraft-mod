package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.client.menu.TeamSelectScreen;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.PanelScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TeamsScreen extends PanelScreen {
    private static final String COMMAND = "battlecraft team";
    private static final String TEAMS_KEY = "teams";
    private static final String[] SUGGESTED = {"red", "blue", "green", "yellow"};

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

        addCreateRows(rows, teams);
        rows.add(action("battlecraft.teams.pick", "battlecraft.menu.action.choose", TeamSelectScreen::open));
        return rows;
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

    private void create(String name) {
        if (name.isEmpty()) {
            MenuFeedback.show(Component.translatable("battlecraft.teams.error.name"), true);
            return;
        }
        send(COMMAND + " add " + name.toLowerCase(Locale.ROOT));
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
