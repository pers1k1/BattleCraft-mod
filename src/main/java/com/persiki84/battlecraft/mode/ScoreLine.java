package com.persiki84.battlecraft.mode;

import com.persiki84.battlecraft.LobbyRoster;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// WHY: счёт снимается строкой до сброса матча и уходит в баннер готовым: клиент к приходу баннера
// WHY: уже в лобби и своего счёта не держит. Двух команд пишется «Red 143 : 120 Blue», больше - списком
final class ScoreLine {
    private static final int SHOWN_MAX = 4;
    private static final String PAIR = " : ";
    private static final String LIST = "  ·  ";

    private ScoreLine() {}

    static Component of(MinecraftServer server) {
        if (MatchScore.mode() == null) return Component.empty();
        List<String> teams = new ArrayList<>(LobbyRoster.pool(server));
        if (teams.isEmpty()) return Component.empty();
        teams.sort(Comparator.<String>comparingInt(MatchScore::scoreOf).reversed());
        if (teams.size() == 2) return pair(server, teams.get(0), teams.get(1));

        MutableComponent line = Component.empty();
        for (int index = 0; index < Math.min(SHOWN_MAX, teams.size()); index++) {
            if (index > 0) line.append(LIST);
            String team = teams.get(index);
            line.append(name(server, team)).append(" " + MatchScore.scoreOf(team));
        }
        return line;
    }

    private static Component pair(MinecraftServer server, String first, String second) {
        return Component.empty().append(name(server, first))
                .append(" " + MatchScore.scoreOf(first) + PAIR + MatchScore.scoreOf(second) + " ")
                .append(name(server, second));
    }

    private static Component name(MinecraftServer server, String team) {
        PlayerTeam found = server.getScoreboard().getPlayerTeam(team);
        ChatFormatting color = found == null ? ChatFormatting.WHITE : found.getColor();
        return Component.literal(team).withStyle(color == ChatFormatting.RESET ? ChatFormatting.WHITE : color);
    }
}
