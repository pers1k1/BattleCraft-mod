package com.persiki84.battlecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public class KeyInputHandler {
    public static final String CATEGORY = "key.categories." + BattleCraftMod.MOD_ID;

    public static final KeyMapping VOTE_YES = new KeyMapping(
            "key." + BattleCraftMod.MOD_ID + ".vote_yes",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, CATEGORY);

    public static final KeyMapping VOTE_NO = new KeyMapping(
            "key." + BattleCraftMod.MOD_ID + ".vote_no",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY);

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (VOTE_YES.consumeClick()) vote(true);
        if (VOTE_NO.consumeClick()) vote(false);
    }

    private static void vote(boolean approve) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !ClientGameData.hasActiveVote()) return;

        minecraft.player.connection.sendCommand("battlecraft surrender " + (approve ? "yes" : "no"));
    }
}
