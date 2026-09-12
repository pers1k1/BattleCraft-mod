package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.announce.AnnounceStyle;
import com.persiki84.battlecraft.announce.Announcements;
import com.persiki84.battlecraft.menu.AnnounceMenuState;
import com.persiki84.battlecraft.network.C2SAnnouncePacket;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.PanelScreen;
import com.persiki84.shared.client.menu.ToggleRow;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AnnounceScreen extends PanelScreen {
    private final Set<String> pickedTeams = new LinkedHashSet<>();
    private final Set<String> pickedNames = new LinkedHashSet<>();

    private FieldRow messageRow;
    private AnnounceStyle style = AnnounceStyle.DEFAULT;
    private int seconds = Announcements.DEFAULT_SECONDS;
    private boolean everyone = true;

    public AnnounceScreen() {
        super(Component.translatable("battlecraft.announce.title"));
    }

    @Override
    protected String menuId() {
        return AnnounceMenuState.MENU_ID;
    }

    @Override
    protected List<Page> pages() {
        return List.of(
                new Page(Component.translatable("battlecraft.announce.tab.message"), this::messageRows),
                new Page(Component.translatable("battlecraft.announce.tab.targets"), this::targetRows));
    }

    private List<AbstractWidget> messageRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(messageRow());
        rows.add(pick("battlecraft.announce.style", styleLabels(),
                () -> style.ordinal(), picked -> style = AnnounceStyle.byOrdinal(picked)));
        rows.add(number("battlecraft.announce.seconds", () -> seconds, value -> seconds = value,
                Announcements.MIN_SECONDS, Announcements.MAX_SECONDS, 1));
        rows.add(reading("battlecraft.announce.aim", this::aim));
        rows.add(action(Component.translatable("battlecraft.announce.send"),
                Component.translatable("battlecraft.announce.action.send"), this::send));
        return rows;
    }

    // WHY: строка поля переживает пересборку: новая на каждый кадр стирала бы набранный текст
    // WHY: и уводила бы курсор из поля на первой же смене состояния меню
    private FieldRow messageRow() {
        if (messageRow == null) {
            messageRow = new FieldRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                    Component.translatable("battlecraft.announce.text"),
                    Component.translatable("battlecraft.announce.text.placeholder"),
                    "", Announcements.MAX_LENGTH, value -> { });
            messageRow.hint("battlecraft.announce.text" + HINT_SUFFIX);
        }
        messageRow.setX(rowsLeft());
        messageRow.setWidth(rowsWidth());
        return messageRow;
    }

    private static List<Component> styleLabels() {
        List<Component> labels = new ArrayList<>();
        for (AnnounceStyle value : AnnounceStyle.values()) {
            labels.add(Component.translatable(value.translationKey()));
        }
        return labels;
    }

    private List<AbstractWidget> targetRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("battlecraft.announce.everyone"),
                () -> everyone, value -> pickEveryone(value)).hint("battlecraft.announce.everyone.hint"));

        addTeamRows(rows);
        addPlayerRows(rows);
        return rows;
    }

    private void addTeamRows(List<AbstractWidget> rows) {
        List<CompoundTag> teams = listOf(AnnounceMenuState.TEAMS);
        if (teams.isEmpty()) return;

        rows.add(heading(Component.translatable("battlecraft.announce.group.teams")));
        for (CompoundTag team : teams) {
            String name = team.getString(AnnounceMenuState.NAME);
            ToggleRow row = new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                    Component.literal(name), () -> pickedTeams.contains(name),
                    value -> mark(pickedTeams, name, value));
            row.note(Component.translatable("battlecraft.teams.players",
                    team.getInt(AnnounceMenuState.PLAYERS)));
            row.active = !everyone;
            rows.add(row);
        }
    }

    private void addPlayerRows(List<AbstractWidget> rows) {
        List<CompoundTag> players = listOf(AnnounceMenuState.PLAYERS);
        if (players.isEmpty()) return;

        rows.add(heading(Component.translatable("battlecraft.announce.group.players")));
        for (CompoundTag player : players) {
            String name = player.getString(AnnounceMenuState.NAME);
            String team = player.getString(AnnounceMenuState.TEAM);
            ToggleRow row = new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                    Component.literal(name), () -> pickedNames.contains(name),
                    value -> mark(pickedNames, name, value));
            if (!team.isEmpty()) row.note(Component.literal(team));
            row.active = !everyone;
            rows.add(row);
        }
    }

    private void pickEveryone(boolean value) {
        everyone = value;
        rebuild();
    }

    private void mark(Set<String> picked, String name, boolean wanted) {
        if (wanted) {
            picked.add(name);
        } else {
            picked.remove(name);
        }
    }

    private Component aim() {
        if (everyone) return Component.translatable("battlecraft.announce.aim.everyone");

        int count = pickedTeams.size() + pickedNames.size();
        if (count == 0) return Component.translatable("battlecraft.announce.aim.none");
        return Component.translatable("battlecraft.announce.aim.picked", pickedTeams.size(),
                pickedNames.size());
    }

    private void send() {
        String text = Announcements.clip(messageRow().value());
        if (text.isEmpty()) {
            MenuFeedback.show(Component.translatable("battlecraft.announce.error.empty"), true);
            return;
        }
        if (!everyone && pickedTeams.isEmpty() && pickedNames.isEmpty()) {
            MenuFeedback.show(Component.translatable("battlecraft.announce.error.no_target"), true);
            return;
        }

        PacketHandler.INSTANCE.sendToServer(new C2SAnnouncePacket(everyone,
                new ArrayList<>(pickedTeams), new ArrayList<>(pickedNames), text, style, seconds));
        MenuFeedback.show(Component.translatable("battlecraft.announce.queued"), false);
    }

    private List<CompoundTag> listOf(String key) {
        List<CompoundTag> entries = new ArrayList<>();
        for (Tag tag : MenuData.state(menuId()).getList(key, Tag.TAG_COMPOUND)) {
            entries.add((CompoundTag) tag);
        }
        return entries;
    }

}
