package com.persiki84.battlecraft.menu;

import com.persiki84.shared.menu.MenuStates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

public final class AnnounceMenuState {
    public static final String MENU_ID = "announce";
    public static final String PLAYERS = "players";
    public static final String TEAMS = "teams";
    public static final String NAME = "name";
    public static final String TEAM = "team";

    private AnnounceMenuState() {}

    public static void register() {
        MenuStates.register(MENU_ID, 2, AnnounceMenuState::snapshot);
    }

    private static CompoundTag snapshot(ServerPlayer viewer) {
        CompoundTag tag = new CompoundTag();
        tag.put(PLAYERS, players(viewer));
        tag.put(TEAMS, teams(viewer));
        return tag;
    }

    private static ListTag players(ServerPlayer viewer) {
        ListTag list = new ListTag();
        for (ServerPlayer player : viewer.server.getPlayerList().getPlayers()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(NAME, player.getGameProfile().getName());
            entry.putString(TEAM, player.getTeam() == null ? "" : player.getTeam().getName());
            list.add(entry);
        }
        return list;
    }

    private static ListTag teams(ServerPlayer viewer) {
        ListTag list = new ListTag();
        for (PlayerTeam team : viewer.server.getScoreboard().getPlayerTeams()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(NAME, team.getName());
            entry.putInt(PLAYERS, team.getPlayers().size());
            list.add(entry);
        }
        return list;
    }
}
