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
        if (player == null || !fits(command)) return false;

        quietUntil = System.currentTimeMillis() + QUIET_MS;
        player.connection.sendCommand(command);
        return true;
    }

    // WHY: экран, который двигает счётчики и выбор до ответа сервера, обязан знать заранее, уйдёт ли
    // WHY: команда: иначе отказ по длине оставлял бы на экране правку, которой на сервере нет
    public static boolean fits(String command) {
        if (command.length() <= SharedConstants.MAX_CHAT_LENGTH) return true;
        MenuFeedback.show(Component.translatable("battlecraft.menu.command_too_long",
                SharedConstants.MAX_CHAT_LENGTH), true);
        return false;
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
