package com.persiki84.zones.mark;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class MarkTeamWatch {
    private static final String NO_TEAM = "";

    private static final Map<UUID, String> seenTeams = new HashMap<>();

    private MarkTeamWatch() {}

    public static void remember(ServerPlayer player) {
        seenTeams.put(player.getUUID(), teamOf(player));
    }

    public static void forget(ServerPlayer player) {
        seenTeams.remove(player.getUUID());
    }

    public static void reset() {
        seenTeams.clear();
    }

    // WHY: видимость меток считается по команде в момент рассылки, а команду меняют лобби, баланс
    // WHY: и ванильная /team мимо нас; без сверки перешедший видел метки прежней команды до перезахода
    public static void resyncChanged(MinecraftServer server, Consumer<ServerPlayer> resync) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String team = teamOf(player);
            String seen = seenTeams.put(player.getUUID(), team);
            if (seen != null && !seen.equals(team)) resync.accept(player);
        }
    }

    private static String teamOf(ServerPlayer player) {
        Team team = player.getTeam();
        return team == null ? NO_TEAM : team.getName();
    }
}
