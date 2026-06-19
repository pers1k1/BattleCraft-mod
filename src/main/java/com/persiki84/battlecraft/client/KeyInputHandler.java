package com.persiki84.battlecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public class KeyInputHandler {

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (event.getKey() == GLFW.GLFW_KEY_F7) {
                if (ClientGameData.hasActiveVote()) {
                    mc.player.connection.sendCommand("battlecraft surrender yes");
                }
            } else if (event.getKey() == GLFW.GLFW_KEY_F8) {
                if (ClientGameData.hasActiveVote()) {
                    mc.player.connection.sendCommand("battlecraft surrender no");
                }
            }
        }
    }
}
