package com.persiki84.shared.client.ui;

import net.minecraft.client.Minecraft;

public final class UiBoot {
    private UiBoot() {}

    // WHY: последнюю секунду мозанговский оверлей рисует наш экран сам, под своей тающей плашкой,
    // WHY: и вступление успевает пройти наполовину раньше, чем игрок вообще увидит экран
    public static boolean loading() {
        return Minecraft.getInstance().getOverlay() != null;
    }

    // WHY: горение закрытого экрана рисуется поверх открытого, поэтому новый экран обязан
    // WHY: придержать содержимое: иначе меню проступает сквозь ещё не догоревший мастер
    public static boolean busy() {
        return loading() || UiFarewell.burning();
    }
}
