package com.persiki84.battlecraft.client.roster;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// WHY: состав списка опрашивается в клиентском тике и только пока список виден или догорает:
// WHY: в кадре рисуется готовое, а пересборка компонентов и сортировка 80 игроков в отрисовке
// WHY: это аллокации на каждом кадре
public final class RosterBook {
    // WHY: ванильный список режет состав на 80 после сортировки, больше в экран не помещается
    private static final int LIMIT = 80;
    private static final int WATCHER_TINT = 0xFF8E93A6;
    private static final Component LONERS = Component.translatable("battlecraft.roster.loners");
    private static final Component PLAYERS = Component.translatable("battlecraft.roster.players");
    private static final Component WATCHERS = Component.translatable("battlecraft.roster.watchers");

    private static final Comparator<PlayerInfo> VANILLA_ORDER = Comparator
            .<PlayerInfo>comparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
            .thenComparing(info -> info.getTeam() == null ? "" : info.getTeam().getName())
            .thenComparing(info -> info.getProfile().getName(), String::compareToIgnoreCase);
    private static final Comparator<RosterRow> ROW_ORDER =
            Comparator.comparing(RosterRow::sortName, String::compareToIgnoreCase);
    private static final Comparator<RosterGroup> GROUP_ORDER = Comparator
            .<RosterGroup>comparingInt(group -> group.kind.ordinal())
            .thenComparing(group -> group.key, String::compareToIgnoreCase);

    private static final List<PlayerInfo> listed = new ArrayList<>();
    private static final Map<UUID, RosterRow> rowIndex = new HashMap<>();
    private static final List<RosterRow> rows = new ArrayList<>();
    private static final Map<String, RosterGroup> teams = new HashMap<>();
    private static final List<RosterGroup> groups = new ArrayList<>();
    private static RosterGroup loners;
    private static RosterGroup watchers;
    private static boolean scored;
    private static int online;
    private static boolean seeded;
    private static int version;

    private RosterBook() {}

    public static void tick(Minecraft minecraft) {
        if (RosterOverlay.live()) refresh(minecraft);
    }

    static void refresh(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || minecraft.level == null) {
            forget();
            return;
        }
        Scoreboard board = minecraft.level.getScoreboard();
        Objective objective = board.getDisplayObjective(Scoreboard.DISPLAY_SLOT_LIST);
        scored = objective != null;
        unlistAll();
        gather(connection);
        for (PlayerInfo info : listed) place(info, board, objective);
        listed.clear();
        order();
        seeded = true;
        version++;
    }

    private static void unlistAll() {
        for (RosterRow row : rows) row.unlist();
        for (RosterGroup group : groups) group.unlist();
    }

    private static void gather(ClientPacketListener connection) {
        listed.clear();
        listed.addAll(connection.getListedOnlinePlayers());
        online = listed.size();
        listed.sort(VANILLA_ORDER);
        if (listed.size() > LIMIT) listed.subList(LIMIT, listed.size()).clear();
    }

    private static void place(PlayerInfo info, Scoreboard board, Objective objective) {
        UUID uuid = info.getProfile().getId();
        RosterRow row = rowIndex.get(uuid);
        if (row == null) {
            row = new RosterRow(uuid, seeded);
            rowIndex.put(uuid, row);
            rows.add(row);
        }
        row.refresh(info, board, objective);
        RosterGroup group = groupOf(info);
        group.members.add(row);
        row.join(group, group.color());
    }

    private static RosterGroup groupOf(PlayerInfo info) {
        if (info.getGameMode() == GameType.SPECTATOR) return opened(watchers(), WATCHERS, WATCHER_TINT);
        PlayerTeam team = info.getTeam();
        if (team == null) return opened(loners(), LONERS, RosterGroup.ACCENT);

        RosterGroup group = teams.get(team.getName());
        if (group == null) {
            group = adopt(new RosterGroup(RosterGroup.Kind.TEAM, team.getName(), seeded));
            teams.put(team.getName(), group);
        }
        Integer color = team.getColor().getColor();
        return opened(group, team.getDisplayName(), color == null ? RosterGroup.ACCENT : 0xFF000000 | color);
    }

    private static RosterGroup opened(RosterGroup group, Component title, int color) {
        if (!group.listed()) group.open(title, color);
        return group;
    }

    private static RosterGroup loners() {
        if (loners == null) loners = adopt(new RosterGroup(RosterGroup.Kind.LONERS, "", seeded));
        return loners;
    }

    private static RosterGroup watchers() {
        if (watchers == null) watchers = adopt(new RosterGroup(RosterGroup.Kind.WATCHERS, "", seeded));
        return watchers;
    }

    private static RosterGroup adopt(RosterGroup group) {
        groups.add(group);
        return group;
    }

    private static void order() {
        groups.sort(GROUP_ORDER);
        for (RosterGroup group : groups) {
            if (group.listed()) group.members.sort(ROW_ORDER);
        }
    }

    // WHY: если команд на сервере нет вовсе, группа «без команды» это просто все игроки
    static Component titleOf(RosterGroup group) {
        if (group.kind != RosterGroup.Kind.LONERS) return group.title();
        for (RosterGroup other : groups) {
            if (other.kind == RosterGroup.Kind.TEAM && other.listed()) return LONERS;
        }
        return PLAYERS;
    }

    // WHY: ушедшая строка и опустевшая группа гаснут присутствием, а не пропадают кадром;
    // WHY: из книги их убирает только доигранное угасание
    static void advance(float delta) {
        for (int at = rows.size() - 1; at >= 0; at--) {
            RosterRow row = rows.get(at);
            if (row.advance(delta)) continue;
            rowIndex.remove(row.uuid);
            rows.remove(at);
        }
        for (int at = groups.size() - 1; at >= 0; at--) {
            RosterGroup group = groups.get(at);
            if (group.advance(delta)) continue;
            dropGroup(group);
            groups.remove(at);
        }
    }

    private static void dropGroup(RosterGroup group) {
        if (group == loners) loners = null;
        else if (group == watchers) watchers = null;
        else teams.remove(group.key);
    }

    static void settle() {
        for (RosterRow row : rows) row.settle();
        for (RosterGroup group : groups) group.settle();
    }

    public static void forget() {
        listed.clear();
        rowIndex.clear();
        rows.clear();
        teams.clear();
        groups.clear();
        loners = null;
        watchers = null;
        seeded = false;
        online = 0;
        version++;
    }

    static List<RosterRow> rows() {
        return rows;
    }

    static List<RosterGroup> groups() {
        return groups;
    }

    static boolean scored() {
        return scored;
    }

    static int version() {
        return version;
    }

    static int online() {
        return online;
    }
}
