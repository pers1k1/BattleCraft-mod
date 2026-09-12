package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.ui.UiSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class MenuCommands {
    private static final long QUIET_MS = 1400L;

    private static long quietUntil;

    private MenuCommands() {}

    public static void run(String command) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        quietUntil = System.currentTimeMillis() + QUIET_MS;
        player.connection.sendCommand(command);
    }

    public static void run(String command, String menuId) {
        run(command);
        MenuData.invalidate(menuId);
        MenuData.request(menuId);
        UiSound.slot();
    }

    public static boolean quiet() {
        return System.currentTimeMillis() < quietUntil;
    }
}
