package com.persiki84.shared.client.ui;

import net.minecraft.client.Minecraft;

// WHY: список игроков открывается клавишей из настроек игрока, а не Tab: захардкоженный код
// WHY: клавиши расходится с перебиндом и оставляет метки поверх таблицы
public final class UiHud {

    private UiHud() {}

    public static boolean rosterOpen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options != null && minecraft.options.keyPlayerList.isDown();
    }

    public static boolean hidden() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options == null || minecraft.options.hideGui || rosterOpen();
    }
}
