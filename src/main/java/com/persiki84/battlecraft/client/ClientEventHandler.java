package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientEventHandler {


    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            
            if (!ClientGameData.isSoftDisabled()) {
                int centerX = event.getScreen().width / 2;
                int centerY = event.getScreen().height / 2;
                int buttonWidth = 150;
                int buttonHeight = 20;

                if (ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.LOBBY) {
                    if (mc.player.getTeam() == null) {
                        java.util.List<net.minecraft.world.scores.PlayerTeam> teams = new java.util.ArrayList<>(mc.level.getScoreboard().getPlayerTeams());
                        int count = Math.min(4, teams.size());
                        int startX = centerX + 110;
                        int startY = centerY - (count * (buttonHeight + 5)) / 2;
                        
                        for (int i = 0; i < count; i++) {
                            net.minecraft.world.scores.PlayerTeam team = teams.get(i);
                            int y = startY + i * (buttonHeight + 5);
                            event.addListener(Button.builder(Component.translatable("battlecraft.button.join_team", team.getDisplayName()), button -> {
                                mc.player.connection.sendCommand("battlecraft select_team " + team.getName());
                                event.getScreen().onClose();
                            }).bounds(startX, y, buttonWidth, buttonHeight).build());
                        }
                    } else {
                        Component readyText = ClientGameData.isReady()
                            ? Component.translatable("battlecraft.status.not_ready").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD)
                            : Component.translatable("battlecraft.status.ready").withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.BOLD);
                            
                        event.addListener(Button.builder(readyText, button -> {
                            mc.player.connection.sendCommand("battlecraft ready");
                            event.getScreen().onClose();
                        }).bounds(centerX + 110, centerY - 10, buttonWidth, buttonHeight).build());
                    }
                } else if (ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.ACTIVE && mc.player.getTeam() != null) {
                    event.addListener(Button.builder(Component.translatable("battlecraft.button.surrender"), button -> {
                        mc.player.connection.sendCommand("battlecraft surrender start");
                        event.getScreen().onClose();
                    }).bounds(centerX + 110, centerY - 10, buttonWidth, buttonHeight).build());
                }
            }
        }
    }
}
