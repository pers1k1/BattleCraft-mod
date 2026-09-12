package com.persiki84.battlecraft;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LobbyRoster {

    private LobbyRoster() {}

    public static List<String> pool(MinecraftServer server) {
        List<String> pool = new ArrayList<>(server.getScoreboard().getTeamNames());
        Collections.sort(pool);
        return pool;
    }

    public static Map<String, List<ServerPlayer>> byTeam(MinecraftServer server, List<String> teamNames) {
        Map<String, List<ServerPlayer>> roster = new LinkedHashMap<>();
        for (String teamName : teamNames) {
            roster.put(teamName, new ArrayList<>());
        }

        Scoreboard scoreboard = server.getScoreboard();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
            if (team == null) continue;

            List<ServerPlayer> members = roster.get(team.getName());
            if (members != null) members.add(player);
        }
        return roster;
    }

    public static List<ServerPlayer> withoutTeam(MinecraftServer server, List<String> teamNames) {
        List<ServerPlayer> result = new ArrayList<>();
        Scoreboard scoreboard = server.getScoreboard();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
            if (team == null || !teamNames.contains(team.getName())) result.add(player);
        }
        return result;
    }

    public static int slotsPerTeam(MinecraftServer server, List<String> teamNames) {
        if (teamNames.isEmpty()) return 0;
        int players = Math.max(server.getPlayerList().getPlayerCount(), teamNames.size());
        return (players + teamNames.size() - 1) / teamNames.size();
    }

    public static String neediestTeam(MinecraftServer server, List<String> teamNames) {
        Map<String, List<ServerPlayer>> roster = byTeam(server, teamNames);
        String neediest = null;
        int smallest = Integer.MAX_VALUE;

        for (Map.Entry<String, List<ServerPlayer>> entry : roster.entrySet()) {
            int size = entry.getValue().size();
            if (size < smallest) {
                smallest = size;
                neediest = entry.getKey();
            }
        }
        return neediest;
    }

    public static boolean hasRoom(MinecraftServer server, List<String> teamNames, String teamName) {
        Map<String, List<ServerPlayer>> roster = byTeam(server, teamNames);
        List<ServerPlayer> members = roster.get(teamName);
        if (members == null) return false;

        int slots = slotsPerTeam(server, teamNames);
        if (members.size() < slots) return true;

        for (List<ServerPlayer> other : roster.values()) {
            if (other.size() < members.size()) return false;
        }
        return true;
    }
}
