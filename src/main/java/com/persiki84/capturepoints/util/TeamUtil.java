package com.persiki84.capturepoints.util;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;

public class TeamUtil {
    public static ChatFormatting getTeamColor(MinecraftServer server, String teamName) {
        if (server != null) {
            var team = server.getScoreboard().getPlayerTeam(teamName);
            if (team != null) {
                return team.getColor();
            }
        }
        return ChatFormatting.WHITE;
    }
}
