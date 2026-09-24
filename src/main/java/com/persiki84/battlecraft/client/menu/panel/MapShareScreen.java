package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.minimap.client.MapUploader;
import com.persiki84.minimap.server.MapScanner;
import com.persiki84.minimap.server.MapShareScope;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.PanelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class MapShareScreen extends PanelScreen {
    private static final String TEAMS_KEY = "teams";
    private static final String SHARED_KEY = "shared";
    private static final String SCAN_LEFT_KEY = "scanLeft";
    private static final String SCAN_TOTAL_KEY = "scanTotal";

    private static int radius = MapScanner.DEFAULT_RADIUS;

    public MapShareScreen() {
        super(Component.translatable("battlecraft.map.title"));
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.MAP;
    }

    @Override
    protected List<Page> pages() {
        return List.of(
                new Page(Component.translatable("battlecraft.map.tab.share"), this::shareRows),
                new Page(Component.translatable("battlecraft.map.tab.players"), this::playerRows),
                new Page(Component.translatable("battlecraft.map.tab.load"), this::loadRows));
    }

    private List<AbstractWidget> loadRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(heading("battlecraft.map.heading.load"));
        rows.add(number("battlecraft.map.radius", () -> radius, value -> radius = value,
                MapScanner.MIN_RADIUS, MapScanner.MAX_RADIUS, MapScanner.RADIUS_STEP));
        rows.add(action(Component.translatable("battlecraft.map.do_load"),
                Component.translatable("battlecraft.map.start"),
                () -> send("bc map load " + radius)));

        rows.add(heading("battlecraft.map.heading.progress"));
        rows.add(reading("battlecraft.map.left", this::progress));
        rows.add(action(Component.translatable("battlecraft.map.do_cancel"),
                Component.translatable("battlecraft.map.stop"),
                () -> send("bc map cancel")));
        return rows;
    }

    private Component progress() {
        CompoundTag state = MenuData.state(menuId());
        int total = state.getInt(SCAN_TOTAL_KEY);
        if (total == 0) return Component.translatable("battlecraft.map.idle");

        return Component.translatable("battlecraft.map.chunks_left", total - state.getInt(SCAN_LEFT_KEY), total);
    }

    private List<AbstractWidget> shareRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(heading("battlecraft.map.heading.source"));
        rows.add(reading("battlecraft.map.known",
                () -> Component.translatable("battlecraft.map.chunks", MapUploader.known())));
        rows.add(reading("battlecraft.map.stored",
                () -> Component.translatable("battlecraft.map.chunks", stored())));

        rows.add(heading("battlecraft.map.heading.targets"));
        rows.add(uploadRow("battlecraft.map.everyone", MapShareScope.EVERYONE, ""));
        for (String team : teams()) {
            rows.add(uploadRow(Component.translatable("battlecraft.map.team", team),
                    MapShareScope.TEAM, team));
        }
        return rows;
    }

    private List<AbstractWidget> playerRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return rows;

        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().getName();
            rows.add(uploadRow(Component.literal(name), MapShareScope.PLAYER, name));
        }
        if (rows.isEmpty()) rows.add(reading("battlecraft.map.no_players", Component::empty));
        return rows;
    }

    private ActionRow uploadRow(String label, MapShareScope scope, String target) {
        return uploadRow(Component.translatable(label), scope, target);
    }

    private ActionRow uploadRow(Component label, MapShareScope scope, String target) {
        return action(label, Component.translatable("battlecraft.map.do_share"),
                () -> upload(scope, target));
    }

    private void upload(MapShareScope scope, String target) {
        int sent = MapUploader.upload(scope, target);
        if (sent == 0) {
            MenuFeedback.show(Component.translatable("battlecraft.map.empty"), true);
            return;
        }
        MenuFeedback.show(Component.translatable("battlecraft.map.sent", sent), false);
    }

    private HeadingRow heading(String key) {
        return new HeadingRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(key));
    }

    private int stored() {
        return MenuData.state(menuId()).getInt(SHARED_KEY);
    }

    private List<String> teams() {
        List<String> names = new ArrayList<>();
        ListTag list = MenuData.state(menuId()).getList(TEAMS_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            names.add(entry.getString("name"));
        }
        return names;
    }
}
