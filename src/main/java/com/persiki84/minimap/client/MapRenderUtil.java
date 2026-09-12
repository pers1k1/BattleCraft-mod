package com.persiki84.minimap.client;

import com.persiki84.shared.client.ui.UiAccent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.PlayerTeam;

public class MapRenderUtil {

    public static int getPlayerTeamColor(String playerName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            PlayerTeam team = mc.level.getScoreboard().getPlayersTeam(playerName);
            if (team != null && team.getColor().getColor() != null) {
                return 0xFF000000 | team.getColor().getColor();
            }
        }
        return UiAccent.dim();
    }

    public static int getTeamColor(String teamName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && teamName != null) {
            PlayerTeam team = mc.level.getScoreboard().getPlayerTeam(teamName);
            if (team != null && team.getColor().getColor() != null) {
                return 0xFF000000 | team.getColor().getColor();
            }
        }
        return UiAccent.faint();
    }
}
