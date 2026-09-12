package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ClientEventHandler {
    private boolean choiceOffered;

    @SubscribeEvent
    public void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientGameData.isSoftDisabled()) return;

        boolean joining = ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.ACTIVE
                && mc.player.getTeam() == null && ClientGameData.getAutoAssignSeconds() > 0;
        if (!joining) {
            choiceOffered = false;
            return;
        }
        if (choiceOffered || mc.screen != null) return;

        choiceOffered = true;
        com.persiki84.battlecraft.client.menu.TeamSelectScreen.open();
    }

}
