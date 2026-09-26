package com.persiki84.zones.client.menu.studio;

import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.zones.shop.ShopAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

// WHY: видимость правится переключателем на каждую команду прямо под вещью, а не выбором цели
// WHY: в отдельной вкладке: строки одни на весь экран и только меняют, чей доступ показывают
final class ShopAccessRows {
    private static final ShopAccess OPEN = new ShopAccess();

    private final Map<String, ToggleRow> teamRows = new HashMap<>();
    private final HeadingRow heading;
    private final ToggleRow everyone;
    private final ActionRow noTeams;
    private final Consumer<String> send;
    private Supplier<ShopAccess> source = () -> OPEN;
    private String command = "";

    ShopAccessRows(int width, int height, Consumer<String> send) {
        this.send = send;
        heading = new HeadingRow(0, 0, width, height, Component.translatable("studio.shop.group.access"));
        everyone = new ToggleRow(0, 0, width, height, Component.translatable("zones.shopadmin.access.everyone"),
                () -> access().everyone(), this::openToEveryone);
        everyone.hint("zones.shopadmin.access.everyone.hint");
        noTeams = new ActionRow(0, 0, width, height, Component.translatable("zones.shopadmin.access.no_teams"),
                Component::empty, () -> {});
        noTeams.active = false;
    }

    void target(String commandPrefix, Supplier<ShopAccess> access) {
        command = commandPrefix;
        source = access;
    }

    private ShopAccess access() {
        ShopAccess access = source.get();
        return access == null ? OPEN : access;
    }

    private void openToEveryone(boolean open) {
        if (open) {
            send.accept(command + " everyone");
            return;
        }
        List<String> teams = teams();
        if (teams.isEmpty()) {
            MenuFeedback.show(Component.translatable("zones.shopadmin.access.no_teams"), true);
            return;
        }
        send.accept(command + " show " + teams.get(0));
    }

    void place(StudioStack stack, int x, int width) {
        stack.add(resized(heading, width), x, 4);
        stack.add(resized(everyone, width), x);
        List<String> teams = teams();
        if (teams.isEmpty()) {
            stack.add(resized(noTeams, width), x);
            return;
        }
        for (String team : teams) {
            stack.add(resized(teamRow(team), width), x);
        }
    }

    private static <T extends net.minecraft.client.gui.components.AbstractWidget> T resized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    private ToggleRow teamRow(String team) {
        return teamRows.computeIfAbsent(team, name -> {
            ToggleRow row = new ToggleRow(0, 0, 10, everyone.getHeight(), Component.literal(name),
                    () -> access().allows(name), shown -> send.accept(command + (shown ? " show " : " hide ") + name));
            row.hint("studio.shop.access.team.hint");
            return row;
        });
    }

    static List<String> teams() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();
        List<String> names = new ArrayList<>(minecraft.level.getScoreboard().getTeamNames());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }
}
