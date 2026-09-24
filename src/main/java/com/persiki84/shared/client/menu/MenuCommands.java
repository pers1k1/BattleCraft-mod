package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiSound;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class MenuCommands {
    private static final long QUIET_MS = 1400L;

    private static long quietUntil;

    private MenuCommands() {}

    public static boolean run(String command) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (command.length() > SharedConstants.MAX_CHAT_LENGTH) {
            MenuFeedback.show(Component.translatable("battlecraft.menu.command_too_long",
                    SharedConstants.MAX_CHAT_LENGTH), true);
            return false;
        }

        quietUntil = System.currentTimeMillis() + QUIET_MS;
        player.connection.sendCommand(command);
        return true;
    }

    public static void run(String command, String menuId) {
        if (!run(command)) return;

        MenuData.invalidate(menuId);
        MenuData.request(menuId);
        UiSound.slot();
    }

    public static boolean quiet() {
        return System.currentTimeMillis() < quietUntil;
    }
}
